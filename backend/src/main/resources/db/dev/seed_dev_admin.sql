-- =============================================================================
-- 云枢智学 · 开发环境初始化管理员账号（仅开发/本地，勿用于生产）
--
-- 原因：V2__seed_baseline.sql 只建角色与权限，刻意不建任何后台账号，
--       导致本地登录接口恒定返回 40036（账号或密码错误）。
--
-- 用法（MySQL 容器已启动时）：
--   docker exec -i yunshu-mysql mysql -uyunshu_app -p yunshu --default-character-set=utf8mb4 < seed_dev_admin.sql
--
-- 默认账号：admin / Admin@123456（role_id=1 超级管理员）
-- 修改密码：用 BCryptPasswordEncoder 生成新哈希后替换 password_hash。
-- =============================================================================

SET NAMES utf8mb4;

INSERT INTO `admin_user`
  (`account`, `password_hash`, `display_name`, `role_id`, `status`, `password_reset_required`, `created_by`, `updated_by`)
VALUES
  ('admin',
   '$2a$10$an8Ubx.fak9BB7CEBc4j5.h0vim5J0dYX60gcZKzLbhA8sCjAfQFi',
   '超级管理员',
   1, 1, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `password_hash` = VALUES(`password_hash`),
  `role_id` = VALUES(`role_id`),
  `status` = 1,
  `updated_by` = 0;
