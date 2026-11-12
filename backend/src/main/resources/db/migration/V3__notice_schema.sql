-- =============================================================================
-- 云枢智学 · C 端公告位 V3
--
-- 背景：
--   04 §6.2.1 的首页聚合响应中预留了 notices 字段，此前 P0 阶段没有公告表，
--   恒返回空数组。本迁移补齐最小可用的公告能力：后台可维护草稿、发布、下线，
--   C 端首页读取"已发布且未过期"的前三条。
--
-- 设计取舍：
--   1. 公告是全局内容，不绑定学科/阶段，因此没有外键列；不做已读/未读记录
--      （未读体系属于消息通知，另立模块时再建 notice_read 表）。
--   2. 状态用三态而非 enable/disable：1 草稿、2 已发布、3 已下线。
--      草稿不对外可见；下线保留历史，可再次发布。
--   3. expire_at 为空表示长期有效；C 端只返回 expire_at 为空或未到期的记录，
--      过期公告不会自动改状态，仍可在后台看到并手动下线。
--   4. pinned 仅影响 C 端排序（置顶在前），不限制条数，避免"置顶满天飞"时
--      首页被占满——前端只取 3 条，超出部分自然截断。
--
-- 回滚说明（仅开发环境）：
--   DELETE FROM admin_role_module WHERE module_code = 'notice';
--   DROP TABLE notice;
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 公告表
-- -----------------------------------------------------------------------------
CREATE TABLE `notice` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` VARCHAR(100) NOT NULL COMMENT '公告标题',
  `content` VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '公告正文；纯文本，不含 HTML',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1草稿 2已发布 3已下线',
  `pinned` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶：0否 1是；仅影响 C 端排序',
  `sort_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序号，升序；同一置顶分组内生效',
  `published_at` DATETIME(3) NULL COMMENT '首次发布时间（UTC）；下线后再次发布不覆盖',
  `expire_at` DATETIME(3) NULL COMMENT '失效时间（UTC）；NULL=长期有效',
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人 admin_user.id；0=系统',
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后修改人 admin_user.id；0=系统',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_notice_feed` (`status`, `pinned`, `sort_no`, `published_at`),
  KEY `idx_notice_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='C 端公告；只草稿可删除，已发布只下线';

-- -----------------------------------------------------------------------------
-- 2. 新模块 notice 的角色授权
--    超管与内容运营可维护公告；学员运营不涉及内容，不授权。
-- -----------------------------------------------------------------------------
INSERT INTO `admin_role_module` (`role_id`, `module_code`, `created_by`, `updated_by`)
SELECT r.`id`, 'notice', 0, 0
FROM `admin_role` r
WHERE r.`code` IN ('super_admin', 'content_ops')
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`);
