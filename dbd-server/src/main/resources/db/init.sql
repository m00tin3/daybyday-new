-- ============================================================
-- Day-BY-Day (DbD) 数据库初始化脚本
-- 库：dbd    字符集：utf8mb4
-- 约定：主键 BIGINT 由后端 Redis 全局 ID 生成器产生（不依赖 AUTO_INCREMENT）
--       sign / uv 数据由 Redis 承载，不建表
-- 执行：mysql -u root -p < init.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS dbd DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE dbd;

-- ==================== 用户 ====================
CREATE TABLE IF NOT EXISTS `user` (
  `id`          BIGINT       NOT NULL COMMENT '用户ID（全局ID生成器）',
  `phone`       VARCHAR(20)  NOT NULL COMMENT '手机号（登录账号）',
  `password`    VARCHAR(128) NOT NULL COMMENT '密码（BCrypt加密，验证码登录注册时存随机串）',
  `nickname`    VARCHAR(32)  NOT NULL COMMENT '昵称',
  `icon`        VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `sign_text`   VARCHAR(128) DEFAULT NULL COMMENT '个性签名',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB COMMENT='用户';

-- ==================== 吧 ====================
CREATE TABLE IF NOT EXISTS `bar` (
  `id`           BIGINT       NOT NULL COMMENT '吧ID',
  `name`         VARCHAR(32)  NOT NULL COMMENT '吧名称',
  `description`  VARCHAR(255) DEFAULT NULL COMMENT '吧简介',
  `cover`        VARCHAR(255) DEFAULT NULL COMMENT '封面图URL',
  `creator_id`   BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `member_count` INT          NOT NULL DEFAULT 0 COMMENT '关注人数（Redis 为准，异步落库）',
  `post_count`   INT          NOT NULL DEFAULT 0 COMMENT '帖子数（Redis 为准，异步落库）',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0封禁',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB COMMENT='吧';

-- ==================== 帖子 ====================
CREATE TABLE IF NOT EXISTS `post` (
  `id`           BIGINT       NOT NULL COMMENT '帖子ID',
  `bar_id`       BIGINT       NOT NULL COMMENT '所属吧ID',
  `user_id`      BIGINT       NOT NULL COMMENT '发帖人ID',
  `title`        VARCHAR(64)  NOT NULL COMMENT '标题',
  `content`      MEDIUMTEXT   NOT NULL COMMENT '正文',
  `images`       VARCHAR(1024) DEFAULT NULL COMMENT '图片URL列表（JSON数组）',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0删除 2精华',
  `is_top`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶 0否 1是',
  `like_count`   INT          NOT NULL DEFAULT 0 COMMENT '点赞数（Redis 为准）',
  `favorite_count` INT        NOT NULL DEFAULT 0 COMMENT '收藏数（Redis 为准）',
  `comment_count` INT         NOT NULL DEFAULT 0 COMMENT '楼层数（Redis 为准）',
  `view_count`   INT          NOT NULL DEFAULT 0 COMMENT '浏览量（Redis 为准）',
  `uv_count`     INT          NOT NULL DEFAULT 0 COMMENT '独立访客数（HyperLogLog，定时落库）',
  `score`        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '热度分（ZSet 排行用，定时重算）',
  `last_comment_time` DATETIME DEFAULT NULL COMMENT '最后回复时间（列表排序用）',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bar_status_created` (`bar_id`, `status`, `created_at`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_score` (`score`)
) ENGINE=InnoDB COMMENT='帖子';

-- ==================== 评论/楼层 ====================
CREATE TABLE IF NOT EXISTS `comment` (
  `id`         BIGINT       NOT NULL COMMENT '楼层ID（每帖内按 Redis 自增生成楼层号）',
  `post_id`    BIGINT       NOT NULL COMMENT '帖子ID',
  `user_id`    BIGINT       NOT NULL COMMENT '回复人ID',
  `floor_no`   INT          NOT NULL COMMENT '楼层号（1 起，本贴内递增）',
  `content`    VARCHAR(2048) NOT NULL COMMENT '内容',
  `images`     VARCHAR(1024) DEFAULT NULL COMMENT '图片URL列表（JSON数组）',
  `parent_id`  BIGINT       DEFAULT NULL COMMENT '楼中楼父楼层ID（NULL=直接回帖）',
  `like_count` INT          NOT NULL DEFAULT 0 COMMENT '点赞数（Redis 为准）',
  `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0删除',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post_floor` (`post_id`, `floor_no`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='评论/楼层';

-- ==================== 点赞 ====================
CREATE TABLE IF NOT EXISTS `post_like` (
  `id`         BIGINT   NOT NULL,
  `post_id`    BIGINT   NOT NULL,
  `user_id`    BIGINT   NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='帖子点赞（Redis Set 为主，本表供异步落库/对账）';

-- ==================== 收藏 ====================
CREATE TABLE IF NOT EXISTS `post_favorite` (
  `id`         BIGINT   NOT NULL,
  `post_id`    BIGINT   NOT NULL,
  `user_id`    BIGINT   NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='帖子收藏（Redis Set 为主，本表供异步落库/对账）';

-- ==================== 关注关系 ====================
CREATE TABLE IF NOT EXISTS `follow` (
  `id`             BIGINT   NOT NULL,
  `user_id`        BIGINT   NOT NULL COMMENT '粉丝ID',
  `follow_user_id` BIGINT   DEFAULT NULL COMMENT '关注的用户ID（关注类型为用户时）',
  `follow_bar_id`  BIGINT   DEFAULT NULL COMMENT '关注的吧ID（关注类型为吧时）',
  `follow_type`    TINYINT  NOT NULL COMMENT '类型 1关注用户 2关注吧',
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow` (`user_id`, `follow_type`, `follow_user_id`, `follow_bar_id`),
  KEY `idx_target` (`follow_user_id`, `follow_bar_id`)
) ENGINE=InnoDB COMMENT='关注关系（用户-用户 / 用户-吧）';

-- ==================== 秒杀活动（抢楼/限量徽章） ====================
CREATE TABLE IF NOT EXISTS `activity` (
  `id`          BIGINT      NOT NULL COMMENT '活动ID',
  `bar_id`      BIGINT      DEFAULT NULL COMMENT '关联吧ID（抢楼活动所在吧）',
  `title`       VARCHAR(64) NOT NULL COMMENT '活动标题',
  `type`        TINYINT     NOT NULL COMMENT '类型 1抢楼 2限量徽章',
  `stock`       INT         NOT NULL COMMENT '库存（徽章数量/楼层上限）',
  `award_desc`  VARCHAR(255) DEFAULT NULL COMMENT '奖励描述',
  `begin_time`  DATETIME    NOT NULL COMMENT '开始时间',
  `end_time`    DATETIME    NOT NULL COMMENT '结束时间',
  `status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '状态 0未开始 1进行中 2已结束',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_time` (`begin_time`, `end_time`)
) ENGINE=InnoDB COMMENT='秒杀活动';

-- ==================== 秒杀订单/领取记录 ====================
CREATE TABLE IF NOT EXISTS `activity_order` (
  `id`          BIGINT   NOT NULL COMMENT '订单ID',
  `activity_id` BIGINT   NOT NULL,
  `user_id`     BIGINT   NOT NULL,
  `status`      TINYINT  NOT NULL DEFAULT 0 COMMENT '状态 0待发货/待领取 1已领取 2已取消',
  `order_no`    VARCHAR(32) DEFAULT NULL COMMENT '业务单号',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`) COMMENT '一人一单约束',
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='秒杀订单/领取记录';

-- ============================================================
-- 种子数据（可选，演示/联调用；ID 为固定值，与全局 ID 生成器互不冲突）
-- ============================================================

-- 基础吧
INSERT INTO `bar` (`id`, `name`, `description`, `member_count`, `post_count`) VALUES
  (1, '猫咪吧',      '铲屎官交流乐园', 1280000, 32000),
  (2, '游戏攻略吧',  '全职业强度分析、配装攻略', 960000, 51000),
  (3, '考研上岸吧',  '打卡互助，一战成硕', 730000, 26000),
  (4, '汽车之家吧',  '选车用车，老司机带路', 520000, 18000),
  (5, '干饭魂',      '深夜放毒，美食探店', 410000, 15000);

-- 演示秒杀活动（徽章 100 个，进行中）
INSERT INTO `activity` (`id`, `bar_id`, `title`, `type`, `stock`, `award_desc`, `begin_time`, `end_time`, `status`) VALUES
  (1001, 1, '猫咪吧 3 周年限量徽章', 2, 100, '「猫奴认证」专属徽章', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), 1),
  (1002, 2, '新版本攻略抢楼活动',   1, 300, '抢到 8 楼/88 楼/888 楼送皮肤', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY), 1);

-- ============================================================
-- 索引与校验（可选执行）
-- SHOW TABLES;
-- SELECT COUNT(*) AS bar_count FROM bar;
-- ============================================================
