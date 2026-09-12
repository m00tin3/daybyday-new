-- ============================================================
-- 增量迁移：发帖城市字段（替代经纬度手输）
--
-- 背景：GEO 同城需要地图 SDK 做地址→经纬度转换，否则要求用户手输
--       经纬度体验极差且无法校验。因此改为发帖时手动填写城市，
--       原经纬度字段与 GEO 相关代码保留（功能封存），便于日后恢复。
--
-- 适用场景：**已部署且已有数据**的库（全新部署直接用 init.sql 即可）
-- 执行：mysql -u root -p --default-character-set=utf8mb4 < migration_city.sql
--
-- ⚠️ 非幂等：ADD COLUMN / ADD INDEX 重复执行会报错，忽略即可。
-- ⚠️ --default-character-set=utf8mb4 必须带上，否则中文会二次编码乱码。
-- ============================================================

SET NAMES utf8mb4;
USE dbd;

-- 1) 帖子新增城市字段
ALTER TABLE `post`
  ADD COLUMN `city` VARCHAR(32) DEFAULT NULL COMMENT '城市（发帖时手动填写，用于按城市浏览）' AFTER `images`;

-- 2) 城市筛选索引
ALTER TABLE `post`
  ADD INDEX `idx_city` (`city`);

-- 3) 经纬度列注释更新为"已封存"（字段与数据保留，恢复 GEO 时可直接复用）
ALTER TABLE `post`
  MODIFY COLUMN `longitude` DECIMAL(10,6) DEFAULT NULL COMMENT '经度（GEO 同城，功能已封存）',
  MODIFY COLUMN `latitude`  DECIMAL(10,6) DEFAULT NULL COMMENT '纬度（GEO 同城，功能已封存）';

-- ============================================================
-- 存量帖子的城市补录（可选）
-- 历史帖子的 city 为 NULL，不会出现在"按城市浏览"里。
-- 如需按下面示例批量补：
--   UPDATE `post` SET `city` = '北京' WHERE `id` IN (2001,2002,2003,2004,2005);
-- ============================================================

-- 校验（可选）
-- SHOW COLUMNS FROM `post` LIKE 'city';
-- SELECT city, COUNT(*) FROM `post` GROUP BY city;
