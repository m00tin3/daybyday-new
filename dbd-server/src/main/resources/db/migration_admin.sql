-- ============================================================
-- 增量迁移：管理员角色 + 个人资料
--
-- 适用场景：**已部署且已有数据**的库（全新部署直接用 init.sql 即可，无需本脚本）
-- 执行：mysql -u root -p --default-character-set=utf8mb4 < migration_admin.sql
--
-- ⚠️ 非幂等：ADD COLUMN 重复执行会报 "Duplicate column name"。
--    若已执行过，忽略该报错继续即可。
-- ⚠️ --default-character-set=utf8mb4 必须带上，否则中文会二次编码乱码。
-- ============================================================

SET NAMES utf8mb4;
USE dbd;

-- 1) 用户表新增角色字段：0 普通用户 / 1 管理员
ALTER TABLE `user`
  ADD COLUMN `role` TINYINT NOT NULL DEFAULT 0 COMMENT '角色 0普通用户 1管理员' AFTER `sign_text`;

-- 2) 管理员账号（role=1）
--    免验证码登录由 app.admin.free-login 控制（.env 的 ADMIN_FREE_LOGIN）
INSERT IGNORE INTO `user` (`id`, `phone`, `password`, `nickname`, `sign_text`, `role`) VALUES
  (9000, '2485617328', 'admin', '系统管理员', '社区管理员', 1);

-- 3) 帖子状态扩展：新增 3=隐藏（原 1正常 0删除 2精华 保留不变）
--    隐藏态在前台全链路不可见，仅在 /admin 管理后台可见并可恢复
ALTER TABLE `post`
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1正常 0删除 2精华 3隐藏';

-- ============================================================
-- 校验（可选）
-- SELECT id, phone, nickname, role FROM `user` WHERE role = 1;
-- SHOW COLUMNS FROM `user` LIKE 'role';
-- ============================================================
