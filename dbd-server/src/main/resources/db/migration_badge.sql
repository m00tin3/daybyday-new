-- ============================================================
-- 增量迁移：限量徽章抢夺活动
--
-- 背景：原有 activity 表只区分 type（1抢楼 / 2限量徽章），徽章没有独立的
--       "称号"字段，只能用 award_desc 写一句描述，抢到的用户也无处展示。
--       本次为 activity 增加 badge_name（徽章称号），并把发行入口交给管理员。
--
-- 适用场景：**已部署且已有数据**的库（全新部署直接用 init.sql 即可）
-- 执行：mysql -u root -p --default-character-set=utf8mb4 < migration_badge.sql
--
-- ⚠️ 非幂等：ADD COLUMN 重复执行会报错，忽略即可。
-- ⚠️ --default-character-set=utf8mb4 必须带上，否则中文会二次编码乱码。
-- ⚠️ 本脚本会**删除**旧的示例活动（1001/1002）及其领取记录，执行前请备份。
-- ============================================================

SET NAMES utf8mb4;
USE dbd;

-- ------------------------------------------------------------
-- 1) activity 增加徽章称号字段
-- ------------------------------------------------------------
ALTER TABLE `activity`
  ADD COLUMN `badge_name` VARCHAR(32) DEFAULT NULL COMMENT '限量徽章称号（type=2 时使用）' AFTER `type`;

-- 同一称号的活动不允许并发发布（一个称号对应一个活动）
ALTER TABLE `activity`
  ADD UNIQUE KEY `uk_badge_name` (`badge_name`);

-- ------------------------------------------------------------
-- 2) 清理旧的示例活动（猫咪吧 3 周年限量徽章 / 新版本攻略抢楼活动）
--    连带删除其领取记录，避免 activity_order 留下孤儿行。
-- ------------------------------------------------------------
DELETE FROM `activity_order` WHERE `activity_id` IN (1001, 1002);
DELETE FROM `activity`       WHERE `id`          IN (1001, 1002);

-- ------------------------------------------------------------
-- 3) 发布 4 个限量徽章活动
--    bar_id 为 NULL：徽章是平台级荣誉，不挂在某个吧下。
--    数量按稀缺度递进，时间窗口默认 7 天，管理员可在后台随时修改。
-- ------------------------------------------------------------
INSERT IGNORE INTO `activity`
  (`id`, `bar_id`, `title`, `type`, `badge_name`, `stock`, `award_desc`, `begin_time`, `end_time`, `status`)
VALUES
  (1003, NULL, '限量徽章：苦来兮苦宗主', 2, '苦来兮苦宗主',  1, '全站唯一，仅此一枚',   NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 1),
  (1004, NULL, '限量徽章：凤川祥',       2, '凤川祥',        3, '限量 3 枚，先到先得',   NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 1),
  (1005, NULL, '限量徽章：苏幽离',       2, '苏幽离',        5, '限量 5 枚，先到先得',   NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 1),
  (1006, NULL, '限量徽章：千早樱',       2, '千早樱',       10, '限量 10 枚，先到先得',  NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 1);

-- ============================================================
-- 校验（可选）
-- SELECT id, badge_name, stock, begin_time, end_time FROM `activity` WHERE type = 2;
-- ============================================================
