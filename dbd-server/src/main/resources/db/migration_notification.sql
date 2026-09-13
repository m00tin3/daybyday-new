-- ============================================================
-- 增量迁移：楼中楼 + 回复提醒
--
-- 背景：1) comment.parent_id 早已存在、addComment 也已能落库，但楼层接口一直
--          用 isNull(parent_id) 把子回复过滤掉，"嵌套组装"始终未实现。
--          本次补齐（固定 2 层：只能回复顶层楼层）。
--       2) 项目完全没有消息通知 —— 被回复的人无从得知。本次新增 notification
--          表，把「回复帖子 / 回复楼层 / 赞帖子」统一封装成一条消息，
--          前端用 type 区分究竟是回复还是点赞。
--
-- 适用场景：**已部署且已有数据**的库（全新部署直接用 init.sql 即可）
-- 执行：mysql -u root -p --default-character-set=utf8mb4 < migration_notification.sql
--
-- ⚠️ 非幂等：CREATE TABLE / ADD COLUMN / ADD INDEX 重复执行会报错，忽略即可。
-- ⚠️ --default-character-set=utf8mb4 必须带上，否则中文会二次编码乱码。
-- ⚠️ 存量 comment 行无需回填：reply_to_user_id 为 NULL 即"回复的是楼主层本身"；
--    floor_no 保持原值（子回复的楼层号自此不再分配、也不再展示，
--    故存量那批带楼层号的子回复不会影响前台的楼层连续显示）。
-- ============================================================

SET NAMES utf8mb4;
USE dbd;

-- 1) 消息通知表
--    接收者维度查询用 (user_id, created_at)；未读数用 (user_id, is_read)。
--    未读数刻意走 DB COUNT(*) 而不是 Redis 计数器：点赞计数刚出过
--    "Redis 与 DB 双写不一致导致前台恒为 0"的事故，未读数是低频读取，
--    不值得再引入一处双写状态。
CREATE TABLE IF NOT EXISTS `notification` (
  `id`           BIGINT   NOT NULL COMMENT '通知ID（Redis 全局ID）',
  `user_id`      BIGINT   NOT NULL COMMENT '接收者',
  `type`         TINYINT  NOT NULL COMMENT '类型 1回复我的帖子 2回复我的楼层 3赞了我的帖子',
  `from_user_id` BIGINT   NOT NULL COMMENT '触发者',
  `post_id`      BIGINT   NOT NULL COMMENT '相关帖子',
  `comment_id`   BIGINT   DEFAULT NULL COMMENT '相关楼层（点赞类通知为 NULL）',
  `is_read`      TINYINT  NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_user_read` (`user_id`, `is_read`)
) ENGINE=InnoDB COMMENT='消息通知';

-- 2) 楼中楼的"被回复者"
--    2 层结构下 parent_id 只指向顶层楼层。用户点某条**子回复**的「回复」时，
--    若只记 parent_id，通知会发给楼主而不是被点的那个人 —— 这一列就是为此存在。
--    NULL 表示"回复的就是楼主层本身"，等价于 parent_id 那一行的作者。
ALTER TABLE `comment`
  ADD COLUMN `reply_to_user_id` BIGINT DEFAULT NULL
  COMMENT '被回复者（NULL=回复楼主层本身）' AFTER `parent_id`;

-- 3) 批量查子回复的索引
--    现有索引 idx_post_floor(post_id, floor_no) 覆盖不到 parent_id 条件，
--    而楼层列表会按 parent_id IN (...) 一次性捞本页所有子回复。
ALTER TABLE `comment`
  ADD INDEX `idx_post_parent` (`post_id`, `parent_id`);

-- ============================================================
-- 校验（可选）
-- SHOW COLUMNS FROM `comment` LIKE 'reply_to_user_id';
-- SHOW INDEX FROM `comment` WHERE Key_name = 'idx_post_parent';
-- SHOW COLUMNS FROM `notification`;
-- ============================================================
