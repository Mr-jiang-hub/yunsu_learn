-- =============================================================================
-- 云枢智学 · 数据库初始化脚本 V1
-- 工程初始化后整体移入 src/main/resources/db/migration
--
-- 依据：docs/PRD.md v1.0、docs/design/01.功能模块梳理.md v1.0、
--       docs/design/02.技术选型与整体架构.md v1.0、docs/rules/database-rules.md v2.1
-- 设计文档：docs/design/03.数据库设计.md
--
-- 兼容性说明（重要）：
--   1. 目标数据库为 MySQL 8.0；本脚本所有表与字段统一使用
--      utf8mb4 / utf8mb4_0900_ai_ci。
--   2. 已在 MySQL 8.0.46 空库执行验证：32 张表、全部 InnoDB、V2 种子 3/18/3。
--   3. 不再兼容 MySQL 5.7；旧的 5.7.26 实例不作为开发/测试基线。
--   4. 本脚本未使用窗口函数、CTE、CHECK、降序索引、JSON_TABLE 等非必要新语法。
--
-- 回滚说明（仅空库/开发环境，生产需备份并审批）：
--   按以下逆序 DROP TABLE；本脚本不包含 DROP 语句，避免误执行。
--   import_job_error, import_job, refresh_token, login_failure, sms_code,
--   sms_send_quota, operation_log, admin_role_module, admin_user, admin_role,
--   interview_quota, report_dimension, interview_report, interview_message,
--   interview_session, wrong_question, answer_scoring_point, answer_option,
--   answer, practice, course_progress, student_path, student, interview_difficulty,
--   dimension, question_scoring_point, question_option, question, course, stage,
--   learning_path, subject
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 一、后台角色与用户
-- -----------------------------------------------------------------------------

CREATE TABLE `admin_role` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(50) NOT NULL COMMENT '角色名称：超级管理员/内容运营/学员运营',
  `code` VARCHAR(30) NOT NULL COMMENT '角色编码：super_admin/content_ops/student_ops',
  `description` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '角色说明',
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人 admin_user.id；0=系统/种子',
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后修改人 admin_user.id；0=系统/种子',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_role_code` (`code`),
  UNIQUE KEY `uk_admin_role_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台角色（仅三类预置角色，不支持新建）';

CREATE TABLE `admin_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `account` VARCHAR(50) NOT NULL COMMENT '登录账号',
  `password_hash` VARCHAR(100) NOT NULL COMMENT '密码哈希（bcrypt/argon2），禁止明文',
  `display_name` VARCHAR(50) NOT NULL COMMENT '姓名/显示名',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色 admin_role.id',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1正常 2停用',
  `last_login_at` DATETIME(3) NULL COMMENT '最近登录时间（UTC）；活跃统计用',
  `password_reset_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否首次登录强制改密：0否 1是',
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人 admin_user.id；0=系统',
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后修改人 admin_user.id；0=系统',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_user_account` (`account`),
  KEY `idx_admin_user_role_id` (`role_id`),
  KEY `idx_admin_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台系统用户；只停用不删除';

CREATE TABLE `admin_role_module` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色 admin_role.id',
  `module_code` VARCHAR(50) NOT NULL COMMENT '模块编码：subject/course/question/interview_settings/learning_path/student/admin_user/role/operation_log/dashboard',
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人 admin_user.id；0=系统/种子',
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后修改人 admin_user.id；0=系统/种子',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_role_module` (`role_id`, `module_code`),
  KEY `idx_admin_role_module_code` (`module_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色可访问模块（模块级权限，不做按钮级/数据行级）';

-- -----------------------------------------------------------------------------
-- 二、内容域：学科 → 学习路线 → 阶段 → 课程/题目；能力维度与面试难度
-- -----------------------------------------------------------------------------

CREATE TABLE `subject` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(50) NOT NULL COMMENT '学科名称',
  `description` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '学科简介',
  `cover_url` VARCHAR(512) NOT NULL DEFAULT '' COMMENT '封面 OSS key/URL；空=占位图',
  `sort_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用 2停用',
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人 admin_user.id；0=系统',
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后修改人 admin_user.id；0=系统',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_name` (`name`),
  KEY `idx_subject_status_sort` (`status`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学科（=学习方向），与学习路线一一对应';

CREATE TABLE `learning_path` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `subject_id` BIGINT UNSIGNED NOT NULL COMMENT '所属学科 subject.id',
  `name` VARCHAR(100) NOT NULL COMMENT '路线名称',
  `description` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '路线简介',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用 2停用',
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人 admin_user.id；0=系统',
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后修改人 admin_user.id；0=系统',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_learning_path_subject` (`subject_id`),
  KEY `idx_learning_path_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习路线；一个学科仅一条';

CREATE TABLE `stage` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `path_id` BIGINT UNSIGNED NOT NULL COMMENT '所属路线 learning_path.id',
  `name` VARCHAR(100) NOT NULL COMMENT '阶段名称',
  `description` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '阶段简介/目标',
  `sort_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '阶段排序号，升序',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用 2停用；停用不物理删除，历史会话/作答保留',
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人 admin_user.id；0=系统',
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后修改人 admin_user.id；0=系统',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_stage_path_sort` (`path_id`, `sort_no`),
  KEY `idx_stage_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阶段；有序，删除仅停用';

CREATE TABLE `course` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `stage_id` BIGINT UNSIGNED NOT NULL COMMENT '所属阶段 stage.id',
  `title` VARCHAR(200) NOT NULL COMMENT '课程标题',
  `bilibili_url` VARCHAR(512) NOT NULL COMMENT 'B 站视频链接',
  `description` TEXT NOT NULL COMMENT '课程简介（长文本，列表不返回）',
  `duration_seconds` INT UNSIGNED NULL COMMENT '课程时长（秒，选填）；NULL=未填',
  `cover_url` VARCHAR(512) NOT NULL DEFAULT '' COMMENT '封面 OSS key/URL；空=占位图',
  `sort_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '课程排序号，升序',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用 2停用',
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人 admin_user.id；0=系统',
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后修改人 admin_user.id；0=系统',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_course_stage_sort` (`stage_id`, `sort_no`),
  KEY `idx_course_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程；一门=一个 B 站链接，不跨阶段复用';

CREATE TABLE `question` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `stage_id` BIGINT UNSIGNED NOT NULL COMMENT '所属阶段 stage.id；不跨阶段复用',
  `type` TINYINT UNSIGNED NOT NULL COMMENT '题型：1判断 2单选 3多选 4简答',
  `content` TEXT NOT NULL COMMENT '题干（长文本，列表不返回）',
  `content_hash` CHAR(64) NOT NULL COMMENT '规范化题干 SHA-256（hex），用于批量导入重复题判定；应用维护',
  `analysis` TEXT NOT NULL COMMENT '解析（长文本）',
  `reference_answer` TEXT NULL COMMENT '简答题参考答案；判断/单选/多选为 NULL',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用 2停用；只停用不硬删',
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人 admin_user.id；0=系统',
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后修改人 admin_user.id；0=系统',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_question_stage_status_type` (`stage_id`, `status`, `type`),
  KEY `idx_question_stage_content_hash` (`stage_id`, `content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目；选项/正确答案/评分点拆分子表，见 question_option/question_scoring_point';

CREATE TABLE `question_option` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `question_id` BIGINT UNSIGNED NOT NULL COMMENT '题目 question.id',
  `option_key` VARCHAR(8) NOT NULL COMMENT '选项标识：A/B/C/D...；判断题固定 A=正确、B=错误',
  `content` VARCHAR(1000) NOT NULL COMMENT '选项内容',
  `is_correct` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否正确答案：0否 1是；判断题/单选恰 1 个，多选至少 2 个（应用校验）',
  `sort_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '选项排序号，升序',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_option_key` (`question_id`, `option_key`),
  KEY `idx_question_option_sort` (`question_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目选项；判断/单选/多选使用，简答题无记录';

CREATE TABLE `question_scoring_point` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `question_id` BIGINT UNSIGNED NOT NULL COMMENT '题目 question.id',
  `point_text` VARCHAR(500) NOT NULL COMMENT '评分点内容',
  `sort_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评分点排序号，升序',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_scoring_point_sort` (`question_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简答题评分点；等权计分，得分率=命中数/总数';

CREATE TABLE `dimension` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `subject_id` BIGINT UNSIGNED NOT NULL COMMENT '所属学科 subject.id',
  `name` VARCHAR(50) NOT NULL COMMENT '能力维度名称',
  `sort_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用 2停用；被报告引用后只停用',
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人 admin_user.id；0=系统',
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后修改人 admin_user.id；0=系统',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dimension_subject_name` (`subject_id`, `name`),
  KEY `idx_dimension_subject_status_sort` (`subject_id`, `status`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学科能力维度；面试出题与报告评分使用';

CREATE TABLE `interview_difficulty` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` VARCHAR(20) NOT NULL COMMENT '难度编码：easy/medium/hard',
  `name` VARCHAR(20) NOT NULL COMMENT '难度名称：简单/中等/困难',
  `description` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '难度描述',
  `focus` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '考察重点',
  `sort_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序号，升序',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用 2停用；停用后 C 端不可选',
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人 admin_user.id；0=系统',
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后修改人 admin_user.id；0=系统',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interview_difficulty_code` (`code`),
  UNIQUE KEY `uk_interview_difficulty_name` (`name`),
  KEY `idx_interview_difficulty_status_sort` (`status`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试难度（全局配置，不按学科拆分；Q29 已确认）';

-- -----------------------------------------------------------------------------
-- 三、学员与学习进度
-- -----------------------------------------------------------------------------

CREATE TABLE `student` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键；注销后作为匿名行为关联 ID，不对外暴露',
  `phone_cipher` VARBINARY(255) NULL COMMENT '手机号 AES-GCM 密文（含 IV/tag）；注销置 NULL；Q18 已确认',
  `phone_hash` CHAR(64) NULL COMMENT '手机号 HMAC-SHA256（hex）；唯一；登录/查重按此列；注销置 NULL',
  `password_hash` VARCHAR(100) NOT NULL COMMENT '密码哈希（bcrypt/argon2）；注销时置为不可用随机哈希',
  `nickname` VARCHAR(50) NULL COMMENT '昵称；注销置 NULL',
  `avatar_url` VARCHAR(512) NULL COMMENT '头像 OSS key/URL；注销置 NULL',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1正常 2禁用 3已注销',
  `current_path_id` BIGINT UNSIGNED NULL COMMENT '当前主路线 learning_path.id；同一时间仅一条；未选路线为 NULL',
  `last_login_at` DATETIME(3) NULL COMMENT '最近登录时间（UTC）；活跃统计用',
  `password_reset_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否首次登录强制改密：0否 1是；后台重置密码后置 1',
  `cancelled_at` DATETIME(3) NULL COMMENT '注销时间（UTC）；status=3 时写入',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '注册时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_phone_hash` (`phone_hash`),
  KEY `idx_student_current_path_id` (`current_path_id`),
  KEY `idx_student_status_created_at` (`status`, `created_at`),
  KEY `idx_student_last_login_at` (`last_login_at`),
  KEY `idx_student_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员；手机号加密+盲索引；注销释放手机号但保留匿名行为';

CREATE TABLE `student_path` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学员 student.id',
  `path_id` BIGINT UNSIGNED NOT NULL COMMENT '学习路线 learning_path.id',
  `joined_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次加入时间（UTC）；换回路线不重置',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_path` (`student_id`, `path_id`),
  KEY `idx_student_path_path_id` (`path_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员加入路线历史；当前主路线另见 student.current_path_id';

CREATE TABLE `course_progress` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学员 student.id',
  `course_id` BIGINT UNSIGNED NOT NULL COMMENT '课程 course.id',
  `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始学习时间（UTC）；直接标记完成时等于完成时间',
  `completed_at` DATETIME(3) NULL COMMENT '完成时间（UTC）；NULL=未完成',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号；rules §3.4',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_progress` (`student_id`, `course_id`),
  KEY `idx_course_progress_course_id` (`course_id`),
  KEY `idx_course_progress_student_completed` (`student_id`, `completed_at`),
  KEY `idx_course_progress_completed_at` (`completed_at`),
  KEY `idx_course_progress_started_at` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程进度；完成标记幂等，重复完成只更新完成时间';

-- -----------------------------------------------------------------------------
-- 四、练习、作答与错题
-- -----------------------------------------------------------------------------

CREATE TABLE `practice` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学员 student.id',
  `stage_id` BIGINT UNSIGNED NULL COMMENT '阶段 practice.stage_id；阶段练习必填；错题重练跨阶段时为 NULL',
  `type` TINYINT UNSIGNED NOT NULL COMMENT '练习类型：1阶段练习 2错题重练',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1进行中 2已完成 3已作废',
  `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始时间（UTC）',
  `finished_at` DATETIME(3) NULL COMMENT '结束时间（UTC）；完成/作废时写入',
  `expire_at` DATETIME(3) NOT NULL COMMENT '续答截止时间（UTC）；创建+7天；超时作废',
  `question_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本次练习题目总数（创建时快照）',
  `correct_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '答对题数；完成时汇总',
  `correct_rate` DECIMAL(5,2) NULL COMMENT '正确率 0.00-100.00；完成时写入，未完成/作废为 NULL',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号；rules §3.4',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_practice_student_stage_type_status_finished` (`student_id`, `stage_id`, `type`, `status`, `finished_at`),
  KEY `idx_practice_student_created` (`student_id`, `created_at`),
  KEY `idx_practice_stage_type_status_finished` (`stage_id`, `type`, `status`, `finished_at`),
  KEY `idx_practice_status_expire` (`status`, `expire_at`),
  KEY `idx_practice_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='练习会话；阶段练习/错题重练；错题重练不计入掌握度与完成度';

CREATE TABLE `answer` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `practice_id` BIGINT UNSIGNED NOT NULL COMMENT '练习 practice.id',
  `question_id` BIGINT UNSIGNED NOT NULL COMMENT '题目 question.id',
  `answer_text` TEXT NULL COMMENT '简答题作答文本；客观题为 NULL，选择项见 answer_option',
  `is_correct` TINYINT(1) NULL COMMENT '是否正确：0否 1是；待判分/待自评为 NULL',
  `score` DECIMAL(5,2) NULL COMMENT '得分 0.00-100.00；客观题 0/100，简答为评分点得分率',
  `feedback` TEXT NULL COMMENT '判分反馈/简答逐点反馈；客观题可为空',
  `judge_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '判分状态：1待判分 2已判分 3待自评 4自评完成',
  `is_degraded` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否降级自评：0否 1是；简答 AI 失败时置 1',
  `judged_at` DATETIME(3) NULL COMMENT '判分/自评完成时间（UTC）',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_answer` (`practice_id`, `question_id`),
  KEY `idx_answer_question_id` (`question_id`),
  KEY `idx_answer_practice_correct` (`practice_id`, `is_correct`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作答；一次练习内每题一条；客观题选项拆 answer_option，简答评分点拆 answer_scoring_point';

CREATE TABLE `answer_option` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `answer_id` BIGINT UNSIGNED NOT NULL COMMENT '作答 answer.id',
  `option_id` BIGINT UNSIGNED NOT NULL COMMENT '被选中的题目选项 question_option.id',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_answer_option` (`answer_id`, `option_id`),
  KEY `idx_answer_option_option_id` (`option_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客观题作答选择项；行存在=被选中，多选题多行';

CREATE TABLE `answer_scoring_point` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `answer_id` BIGINT UNSIGNED NOT NULL COMMENT '作答 answer.id',
  `scoring_point_id` BIGINT UNSIGNED NOT NULL COMMENT '评分点 question_scoring_point.id',
  `is_hit` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否命中：0否 1是；由大模型逐点判定',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_answer_scoring_point` (`answer_id`, `scoring_point_id`),
  KEY `idx_answer_scoring_point_point_id` (`scoring_point_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简答题逐评分点命中结果；等权，得分率=命中数/总数';

CREATE TABLE `wrong_question` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学员 student.id',
  `question_id` BIGINT UNSIGNED NOT NULL COMMENT '题目 question.id',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1在册 2已移出；再次答错复用同一条并置回 1',
  `first_added_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次加入错题本时间（UTC）',
  `last_added_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近一次答错加入时间（UTC）',
  `removed_at` DATETIME(3) NULL COMMENT '最近一次答对移出时间（UTC）；在册为 NULL',
  `last_wrong_answer_id` BIGINT UNSIGNED NULL COMMENT '最近一次答错的作答 answer.id；用于错题本展示“我的答案”',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wrong_question` (`student_id`, `question_id`),
  KEY `idx_wrong_question_student_status` (`student_id`, `status`),
  KEY `idx_wrong_question_question_id` (`question_id`),
  KEY `idx_wrong_question_last_answer` (`last_wrong_answer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='错题本；唯一约束保证一人一题一条，移出后保留状态';

-- -----------------------------------------------------------------------------
-- 五、AI 面试
-- -----------------------------------------------------------------------------

CREATE TABLE `interview_session` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学员 student.id',
  `subject_id` BIGINT UNSIGNED NOT NULL COMMENT '所选学科 subject.id',
  `difficulty_id` BIGINT UNSIGNED NOT NULL COMMENT '所选难度 interview_difficulty.id',
  `stage_id` BIGINT UNSIGNED NOT NULL COMMENT '创建时快照的阶段 stage.id',
  `stage_name` VARCHAR(100) NOT NULL COMMENT '创建时快照的阶段名称；不随阶段改名/停用更新',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1创建中 2进行中 3正常完成 4未完成 5已过期 6已作废',
  `current_round` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已完成作答轮次 0-4；开场轮=1',
  `quota_date` DATE NOT NULL COMMENT '次数占用归属日（Asia/Shanghai）；跨日作废按此日返还',
  `started_at` DATETIME(3) NULL COMMENT '开场题生成成功、会话转进行中时间（UTC）',
  `finished_at` DATETIME(3) NULL COMMENT '完成/未完成/过期/作废时间（UTC）',
  `expire_at` DATETIME(3) NOT NULL COMMENT '过期时间（UTC）；创建中=创建+5分钟，进行中=开始+24小时',
  `void_reason` TINYINT UNSIGNED NULL COMMENT '作废原因：1开场题生成失败 2创建超时 3其他系统/AI故障；非作废为 NULL',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号；rules §3.4',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）；创建即计 1 次',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_interview_session_student_created` (`student_id`, `created_at`),
  KEY `idx_interview_session_status_expire` (`status`, `expire_at`),
  KEY `idx_interview_session_subject_difficulty` (`subject_id`, `difficulty_id`),
  KEY `idx_interview_session_created_at` (`created_at`),
  KEY `idx_interview_session_stage_id` (`stage_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 面试会话；阶段快照；每日次数在 interview_quota 原子占用';

CREATE TABLE `interview_message` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` BIGINT UNSIGNED NOT NULL COMMENT '面试会话 interview_session.id',
  `round_no` TINYINT UNSIGNED NOT NULL COMMENT '轮次：1开场 2-4追问',
  `role` TINYINT UNSIGNED NOT NULL COMMENT '角色：1 AI 提问 2学员回答',
  `content` TEXT NOT NULL COMMENT '转写/文本内容；不保存音频',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interview_message_round_role` (`session_id`, `round_no`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试消息/转写文本；固定4轮，每轮 AI+学员各一条；无音频字段';

CREATE TABLE `interview_report` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` BIGINT UNSIGNED NOT NULL COMMENT '面试会话 interview_session.id',
  `total_score` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '总分 0.00-100.00',
  `summary` TEXT NOT NULL COMMENT '总评文字（长文本）',
  `suggestions` TEXT NOT NULL COMMENT '改进建议 2-3 条，换行分隔（长文本）',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '报告状态：1正常 2未完成（提前结束）',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interview_report_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试报告；已过期/已作废会话不生成正式报告';

CREATE TABLE `report_dimension` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `report_id` BIGINT UNSIGNED NOT NULL COMMENT '面试报告 interview_report.id',
  `dimension_id` BIGINT UNSIGNED NOT NULL COMMENT '能力维度 dimension.id',
  `dimension_name` VARCHAR(50) NOT NULL COMMENT '报告生成时的维度名称快照；不随维度改名更新',
  `score` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '维度得分 0.00-100.00',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_dimension` (`report_id`, `dimension_id`),
  KEY `idx_report_dimension_dimension_id` (`dimension_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报告各能力维度得分；维度名称做快照保证历史可读';

CREATE TABLE `interview_quota` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学员 student.id',
  `quota_date` DATE NOT NULL COMMENT '配额归属日（Asia/Shanghai 自然日），不是 UTC 时间',
  `used_count` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已占用次数；上限 3，由原子条件更新保证',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号；rules §3.4',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interview_quota` (`student_id`, `quota_date`),
  KEY `idx_interview_quota_date` (`quota_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 面试每日次数配额；原子占用/返还，不按学科或难度拆分';

-- -----------------------------------------------------------------------------
-- 六、操作日志
-- -----------------------------------------------------------------------------

CREATE TABLE `operation_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operator_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '操作人 admin_user.id；0=系统/未知账号',
  `operator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '操作人姓名快照；避免账号改名后历史不可读',
  `role_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '操作人角色名称快照',
  `module_code` VARCHAR(50) NOT NULL COMMENT '模块编码，同 admin_role_module.module_code',
  `action` VARCHAR(50) NOT NULL COMMENT '动作：login/create/update/disable/enable/reset_password/import/...',
  `target_type` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '对象类型：subject/course/question/student/admin_user/role/...',
  `target_id` BIGINT UNSIGNED NULL COMMENT '对象主键；无具体对象为 NULL',
  `result` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '结果：1成功 2失败',
  `detail` VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '变更摘要；禁止写手机号/验证码/密码/token 明文',
  `ip` VARCHAR(45) NOT NULL DEFAULT '' COMMENT '请求 IP（IPv4/IPv6）',
  `trace_id` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '链路 traceId',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '规则要求字段；日志只追加，业务上永不更新',
  PRIMARY KEY (`id`),
  KEY `idx_operation_log_operator_created` (`operator_id`, `created_at`),
  KEY `idx_operation_log_module_created` (`module_code`, `created_at`),
  KEY `idx_operation_log_created_at` (`created_at`),
  KEY `idx_operation_log_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台操作日志；只追加，不更新不删除，不提供导出';

-- -----------------------------------------------------------------------------
-- 七、认证与短信支撑（P0 不用 Redis，全部落 MySQL 原子计数）
-- -----------------------------------------------------------------------------

CREATE TABLE `sms_send_quota` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone_hash` CHAR(64) NOT NULL COMMENT '手机号 HMAC-SHA256（hex）',
  `send_date` DATE NOT NULL COMMENT '发送归属日（Asia/Shanghai）',
  `send_count` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日已发送次数；上限 10',
  `last_sent_at` DATETIME(3) NULL COMMENT '最近一次发送时间（UTC）；用于 60 秒频控',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sms_send_quota` (`phone_hash`, `send_date`),
  KEY `idx_sms_send_quota_date` (`send_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信发送频控；60秒/次、单号单日10次，行锁原子判断';

CREATE TABLE `sms_code` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone_hash` CHAR(64) NOT NULL COMMENT '手机号 HMAC-SHA256（hex）',
  `purpose` TINYINT UNSIGNED NOT NULL COMMENT '用途：1注册 2验证码登录 3重置密码 4换绑手机号 5注销确认',
  `code_hash` CHAR(64) NOT NULL COMMENT '验证码 HMAC-SHA256（hex）；禁止存明文',
  `expire_at` DATETIME(3) NOT NULL COMMENT '过期时间（UTC）；发送+5分钟',
  `used_at` DATETIME(3) NULL COMMENT '使用时间（UTC）',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1未使用 2已使用 3已过期 4已作废',
  `request_ip` VARCHAR(45) NOT NULL DEFAULT '' COMMENT '请求 IP',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发送时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_sms_code_phone_purpose_status_expire` (`phone_hash`, `purpose`, `status`, `expire_at`),
  KEY `idx_sms_code_status_expire` (`status`, `expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信验证码；5分钟有效，校验时条件更新保证一次性';

CREATE TABLE `login_failure` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `account_type` TINYINT UNSIGNED NOT NULL COMMENT '账号类型：1学员 2后台用户',
  `account_key` VARCHAR(100) NOT NULL COMMENT '学员=phone_hash；后台=account；不存手机号明文',
  `failure_count` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '连续失败次数；成功登录/验证码通过后清零',
  `first_failed_at` DATETIME(3) NULL COMMENT '本轮首次失败时间（UTC）',
  `last_failed_at` DATETIME(3) NULL COMMENT '最近失败时间（UTC）',
  `locked_until` DATETIME(3) NULL COMMENT '锁定截止时间（UTC）；B端连续5次锁15分钟；学员端可为空但由 failure_count 强制验证码',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_login_failure` (`account_type`, `account_key`),
  KEY `idx_login_failure_last_failed` (`last_failed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录失败计数；C/B 端独立，重启不丢，不用本地缓存承载安全边界';

CREATE TABLE `refresh_token` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `subject_type` TINYINT UNSIGNED NOT NULL COMMENT '主体类型：1学员 2后台用户',
  `subject_id` BIGINT UNSIGNED NOT NULL COMMENT '主体 ID（student.id 或 admin_user.id）',
  `token_hash` CHAR(64) NOT NULL COMMENT 'Refresh Token SHA-256/HMAC（hex）；禁止存明文',
  `expires_at` DATETIME(3) NOT NULL COMMENT '过期时间（UTC）；TTL 待 Q27',
  `revoked_at` DATETIME(3) NULL COMMENT '撤销时间（UTC）；NULL=有效',
  `revoke_reason` TINYINT UNSIGNED NULL COMMENT '撤销原因：1登出 2改密 3禁用 4注销 5轮换 6其他',
  `replaced_by_id` BIGINT UNSIGNED NULL COMMENT '轮换后的新 refresh_token.id',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '签发时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
  KEY `idx_refresh_token_subject_revoked` (`subject_type`, `subject_id`, `revoked_at`),
  KEY `idx_refresh_token_expires` (`expires_at`),
  KEY `idx_refresh_token_replaced` (`replaced_by_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Refresh Token 持久化撤销表；只存哈希；登出/改密/禁用/注销批量撤销';

-- -----------------------------------------------------------------------------
-- 八、题库批量导入任务
-- -----------------------------------------------------------------------------

CREATE TABLE `import_job` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `admin_user_id` BIGINT UNSIGNED NOT NULL COMMENT '发起导入的后台用户 admin_user.id',
  `file_name` VARCHAR(255) NOT NULL COMMENT '上传文件名；不保存文件内容',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1处理中 2全部成功 3全部失败',
  `total_rows` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '总行数',
  `success_rows` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功行数；整批成功时才等于总行数',
  `failed_rows` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '失败行数；整批失败时等于总行数',
  `error_message` VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '整体错误摘要',
  `started_at` DATETIME(3) NULL COMMENT '开始处理时间（UTC）',
  `finished_at` DATETIME(3) NULL COMMENT '结束时间（UTC）',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_import_job_admin_created` (`admin_user_id`, `created_at`),
  KEY `idx_import_job_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题库批量导入任务；业务写入单事务整批回滚，任务状态独立提交';

CREATE TABLE `import_job_error` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_id` BIGINT UNSIGNED NOT NULL COMMENT '导入任务 import_job.id',
  `row_no` INT UNSIGNED NOT NULL COMMENT 'Excel 行号（含表头时从 2 开始）',
  `column_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '出错列名；整行错误可为空',
  `error_message` VARCHAR(500) NOT NULL COMMENT '错误原因',
  `raw_row` TEXT NULL COMMENT '原始行文本（脱敏后），便于定位；不保存整份文件',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_import_job_error_job_row` (`job_id`, `row_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批量导入失败行明细；整批失败时返回';
