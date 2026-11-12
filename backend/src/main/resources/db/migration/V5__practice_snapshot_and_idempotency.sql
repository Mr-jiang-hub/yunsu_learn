-- =============================================================================
-- V5： practice_question（练习题目快照，04-Q35 / 05 G-03）
--      idempotency_record（幂等结果持久化，04-Q36 / 05 G-04）
--
-- 两张表都是 05 §11 "实现补充项"里已确认规则缺少的物理承载：
--   G-03 题库增删/停用会让续答的题目集合变化，练习结果不可复现；
--   G-04 api-rules §9.2 要求 Idempotency-Key 的结果保存 ≥24 小时，
--        原来只有"60 秒内复用进行中会话"兜底，不能宣称满足规则。
--
-- 版本号说明：
--   03/04/05 三份文档原把这两张表登记为 V3 前置；V3 已用于 notice（公告）、
--   V4 已用于 message/message_receive（消息），故顺延到 V5，结论不变。
--   迁移文件一旦应用就不得再改（validate-on-migrate=true 会 checksum mismatch），
--   版本顺延只体现在文档里。
--
-- 回滚说明（仅开发环境）：
--   DROP TABLE practice_question;
--   DROP TABLE idempotency_record;
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 练习题目快照
--    创建练习时一次写入，之后答题/续答/判分全部读快照，
--    不再回 question 表按 stage_id 推导，保证 7 天续答期内题目集合不变。
-- -----------------------------------------------------------------------------
CREATE TABLE `practice_question` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `practice_id` BIGINT UNSIGNED NOT NULL COMMENT '练习 practice.id',
  `question_id` BIGINT UNSIGNED NOT NULL COMMENT '题目 question.id（快照，题目停用仍保留）',
  `sort_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '题序，升序；决定一题一屏的展示顺序',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_practice_question` (`practice_id`, `question_id`),
  KEY `idx_practice_question_practice_sort` (`practice_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='练习题目快照；05 G-03，保证续答题目集合可复现';

-- -----------------------------------------------------------------------------
-- 2. 幂等结果记录
--    scope + idempotency_key 唯一；request_hash 用于发现"同 key 不同请求"的误用；
--    response_json 是首次成功响应的快照，重放时原样返回；
--    expire_at 为 24 小时，到期后可被清理，不长期占用。
-- -----------------------------------------------------------------------------
CREATE TABLE `idempotency_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scope` VARCHAR(40) NOT NULL COMMENT '幂等作用域；如 interview.create',
  `idempotency_key` CHAR(36) NOT NULL COMMENT '客户端 Idempotency-Key（UUID）',
  `subject_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '归属主体 id；如 student_id，便于按人清理',
  `request_hash` CHAR(64) NOT NULL COMMENT '规范化请求体 SHA-256（hex）；同 key 不同请求时拒绝',
  `response_json` JSON NULL COMMENT '首次成功响应快照；重放时原样返回',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1成功 2失败（失败不缓存响应，仅用于限并发）',
  `expire_at` DATETIME(3) NOT NULL COMMENT '过期时间（UTC）；创建+24 小时',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idempotency_scope_key` (`scope`, `idempotency_key`),
  KEY `idx_idempotency_expire` (`expire_at`),
  KEY `idx_idempotency_subject` (`subject_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='幂等结果记录；05 G-04，满足 api-rules §9.2 的 24 小时要求';
