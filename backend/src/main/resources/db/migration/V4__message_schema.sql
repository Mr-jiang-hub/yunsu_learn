-- =============================================================================
-- 云枢智学 · 消息通知 V4
--
-- 背景：
--   公告（notice）是"全局广播、谁都能看、没有已读概念"的轻量内容；
--   消息通知是"要触达、要已读、可撤回、可删除"的定向能力，两者语义不同，
--   因此另立两张表，不在 notice 上加字段。
--
-- 设计取舍：
--   1. message 只存"稿件"，message_receive 存"每个学员一份的收件状态"；
--      已读/删除是收件侧状态，改收件不影响稿件本身，撤回也只是稿件改状态。
--   2. 全体推送不预生成收件行：发送时只写 target_count 快照，学员首次访问
--      消息中心时按"注册时间 <= 发送时间"补齐缺失的收件行（懒投递），
--      避免一次性向全量表插入百万行。注册前发的老消息不会推给新学员。
--   3. 指定学员推送在发送时直接落收件行，接收人已注销（status=3）的会被剔除。
--   4. 已发送的消息不可删除、不可编辑，只能撤回；撤回后 C 端立即不可见，
--      但收件行保留，便于后台统计"发出了多少"。草稿可编辑可删除。
--   5. 不维护 read_count 冗余列：已读人数由 message_receive 实时统计，
--      避免标记已读时双写不一致。
--
-- 回滚说明（仅开发环境）：
--   DELETE FROM admin_role_module WHERE module_code = 'message';
--   DROP TABLE message_receive;
--   DROP TABLE message;
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 消息稿件表
-- -----------------------------------------------------------------------------
CREATE TABLE `message` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` VARCHAR(100) NOT NULL COMMENT '消息标题',
  `content` VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '消息正文；纯文本，不含 HTML',
  `type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '类型：1系统通知 2学习提醒 3活动通知',
  `audience` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '受众：1全体学员 2指定学员',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1草稿 2已发送 3已撤回',
  `sent_at` DATETIME(3) NULL COMMENT '发送时间（UTC）；草稿/撤回后重发不覆盖首次时间',
  `target_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发送时接收人数快照；全体=当时正常学员数，指定=实际落库人数',
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人 admin_user.id；0=系统',
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后修改人 admin_user.id；0=系统',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_message_status_sent` (`status`, `sent_at`),
  KEY `idx_message_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息稿件；已发送只可撤回不可删除';

-- -----------------------------------------------------------------------------
-- 2. 收件表：每个学员每条消息一行
-- -----------------------------------------------------------------------------
CREATE TABLE `message_receive` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `message_id` BIGINT UNSIGNED NOT NULL COMMENT '消息 message.id',
  `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学员 student.id',
  `read_at` DATETIME(3) NULL COMMENT '已读时间（UTC）；NULL=未读',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '学员侧是否已删除：0否 1是；软删，不再补齐',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '投递时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_receive` (`message_id`, `student_id`),
  KEY `idx_message_receive_student` (`student_id`, `deleted`, `read_at`, `message_id`),
  KEY `idx_message_receive_message` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息收件；已读/删除是收件侧状态，撤回不影响历史收件';

-- -----------------------------------------------------------------------------
-- 3. 新模块 message 的角色授权
--    超管与学员运营可推送消息（学员运营负责触达学员）；内容运营不介入。
-- -----------------------------------------------------------------------------
INSERT INTO `admin_role_module` (`role_id`, `module_code`, `created_by`, `updated_by`)
SELECT r.`id`, 'message', 0, 0
FROM `admin_role` r
WHERE r.`code` IN ('super_admin', 'student_ops')
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`);
