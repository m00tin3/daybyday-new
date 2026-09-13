-- ============================================================
-- 增量迁移：官方公告（帖子类型 + 公告不挂吧）
--
-- 背景：管理后台需要发布"全站公告"。公告本身是一种帖子，复用帖子
--       详情/回复/点赞/缓存/Feed 的全部链路，只是：
--         · type = 1 与普通帖区分（前端渲染「公告」角标）
--         · bar_id = NULL —— 公告不属于任何吧，是全站级别的
--       配合已有的 ORDER BY p.is_top DESC，公告自动排在全站列表最前。
--
-- 适用场景：**已部署且已有数据**的库（全新部署直接用 init.sql 即可）
-- 执行：mysql -u root -p --default-character-set=utf8mb4 < migration_notice.sql
--
-- ⚠️ 非幂等：ADD COLUMN 重复执行会报错，忽略即可。
-- ⚠️ --default-character-set=utf8mb4 必须带上，否则中文会二次编码乱码。
-- ⚠️ MySQL 8.0 下 MODIFY COLUMN（NOT NULL → NULL）与带 AFTER 的 ADD COLUMN
--    都不是 INSTANT 算法，会触发表重建。当前数据量下无感知，若日后上生产
--    且帖子量很大，需安排在低峰期执行。
-- ============================================================

SET NAMES utf8mb4;
USE dbd;

-- 1) 公告不挂吧：放开 bar_id 的 NOT NULL
--    存量帖子的 bar_id 全部有值，放开约束不影响任何现有数据
ALTER TABLE `post`
  MODIFY COLUMN `bar_id` BIGINT DEFAULT NULL COMMENT '所属吧ID（公告为 NULL）';

-- 2) 帖子类型
ALTER TABLE `post`
  ADD COLUMN `type` TINYINT NOT NULL DEFAULT 0 COMMENT '类型 0普通帖 1公告' AFTER `bar_id`;

-- ============================================================
-- 说明：存量帖子的 type 一律为 0（默认值），无需回填。
-- ============================================================

-- 校验（可选）
-- SHOW COLUMNS FROM `post` LIKE 'bar_id';
-- SHOW COLUMNS FROM `post` LIKE 'type';
-- SELECT type, COUNT(*) FROM `post` GROUP BY type;
