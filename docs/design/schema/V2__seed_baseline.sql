-- =============================================================================
-- 云枢智学 · 基线种子数据 V2
-- 工程初始化后整体移入 src/main/resources/db/migration
--
-- 内容：
--   1. 三类预置后台角色（PRD 2.2 / 6.8）
--   2. 角色默认模块权限（PRD 2.3）
--   3. 三个全局面试难度档位（PRD 6.5 / Q29 全局难度）
--
-- 幂等性：
--   使用 INSERT ... ON DUPLICATE KEY UPDATE，可在空库执行，也可重复执行。
--   V2 不创建任何后台账号、学员、学科、路线、课程或题目。
--
-- 回滚说明（仅空库/开发环境）：
--   1) DELETE FROM admin_role_module WHERE role_id IN
--        (SELECT id FROM admin_role WHERE code IN ('super_admin','content_ops','student_ops'));
--   2) DELETE FROM interview_difficulty WHERE code IN ('easy','medium','hard');
--   3) DELETE FROM admin_role WHERE code IN ('super_admin','content_ops','student_ops');
--   生产环境不要执行上述删除：角色可能已被 admin_user 引用，难度可能已被面试会话引用。
--   生产回滚应改为调整权限或停用难度，并先备份、走变更审批。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 预置角色
-- -----------------------------------------------------------------------------
INSERT INTO `admin_role` (`name`, `code`, `description`, `created_by`, `updated_by`)
VALUES
  ('超级管理员', 'super_admin', '系统负责人，固定拥有全部模块权限', 0, 0),
  ('内容运营', 'content_ops', '维护学科、课程、题库、路线与面试设置', 0, 0),
  ('学员运营', 'student_ops', '管理学员并查看数据看板', 0, 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`),
  `updated_by` = VALUES(`updated_by`);

-- -----------------------------------------------------------------------------
-- 2. 角色默认模块权限（PRD 2.3 权限矩阵）
--    超管：全部 10 个模块
--    内容运营：学科、课程、题库、面试设置、学习路线、数据看板
--    学员运营：学员管理、数据看板
-- -----------------------------------------------------------------------------
INSERT INTO `admin_role_module` (`role_id`, `module_code`, `created_by`, `updated_by`)
SELECT r.`id`, x.`module_code`, 0, 0
FROM `admin_role` r
JOIN (
  SELECT 'super_admin' AS `role_code`, 'subject' AS `module_code`
  UNION ALL SELECT 'super_admin', 'course'
  UNION ALL SELECT 'super_admin', 'question'
  UNION ALL SELECT 'super_admin', 'interview_settings'
  UNION ALL SELECT 'super_admin', 'learning_path'
  UNION ALL SELECT 'super_admin', 'student'
  UNION ALL SELECT 'super_admin', 'admin_user'
  UNION ALL SELECT 'super_admin', 'role'
  UNION ALL SELECT 'super_admin', 'operation_log'
  UNION ALL SELECT 'super_admin', 'dashboard'
  UNION ALL SELECT 'content_ops', 'subject'
  UNION ALL SELECT 'content_ops', 'course'
  UNION ALL SELECT 'content_ops', 'question'
  UNION ALL SELECT 'content_ops', 'interview_settings'
  UNION ALL SELECT 'content_ops', 'learning_path'
  UNION ALL SELECT 'content_ops', 'dashboard'
  UNION ALL SELECT 'student_ops', 'student'
  UNION ALL SELECT 'student_ops', 'dashboard'
) x ON x.`role_code` = r.`code`
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`);

-- -----------------------------------------------------------------------------
-- 3. 全局面试难度档位（Q29：全局统一，不加 subject_id）
-- -----------------------------------------------------------------------------
INSERT INTO `interview_difficulty`
  (`code`, `name`, `description`, `focus`, `sort_no`, `status`, `created_by`, `updated_by`)
VALUES
  ('easy', '简单', '基础概念与常见用法，问题直接、追问较浅', '基础概念、核心用法、能否说清基本流程', 1, 1, 0, 0),
  ('medium', '中等', '结合实际场景考察原理与取舍，追问逐步深入', '原理理解、场景应用、方案取舍', 2, 1, 0, 0),
  ('hard', '困难', '深挖底层机制、边界与复杂场景，追问强度高', '底层机制、边界条件、复杂问题拆解', 3, 1, 0, 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`),
  `focus` = VALUES(`focus`),
  `sort_no` = VALUES(`sort_no`),
  `status` = VALUES(`status`),
  `updated_by` = VALUES(`updated_by`);
