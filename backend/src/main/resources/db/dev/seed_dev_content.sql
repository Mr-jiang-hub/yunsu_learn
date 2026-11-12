-- =============================================================================
-- 云枢智学 · 开发环境内容种子数据（仅开发/本地，勿用于生产）
--
-- 内容：1 个学科（Java 后端）+ 1 条学习路线 + 3 个阶段 + 6 门课程 + 12 道题
--       + 5 个面试能力维度
-- 目的：让管理后台内容管理与 C 端页面一打开就有数据可看，便于联调验收。
--
-- 用法：
--   docker exec -i yunshu-mysql mysql -uyunshu_app -p yunshu --default-character-set=utf8mb4 \
--     < backend/src/main/resources/db/dev/seed_dev_content.sql
--
-- 幂等性：全部按业务键（名称/标题/题干）判断存在性，可重复执行。
-- content_hash 与后端 AdminQuestionService 的 normalize 规则保持一致：
--   题干 trim + 连续空白折叠为单空格 + 转小写，再取 SHA-256 hex。
--   因此下方题干刻意不含首尾空格与连续空格。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 学科与学习路线（一一对应）
-- -----------------------------------------------------------------------------
INSERT INTO `subject` (`name`, `description`, `cover_url`, `sort_no`, `status`, `created_by`, `updated_by`)
VALUES ('Java 后端', 'Java 语言基础、Spring 生态与数据库，面向后端开发岗位的学习路线。', '', 1, 1, 0, 0)
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `sort_no` = VALUES(`sort_no`);

SET @subject_id = (SELECT `id` FROM `subject` WHERE `name` = 'Java 后端');

INSERT INTO `learning_path` (`subject_id`, `name`, `description`, `status`, `created_by`, `updated_by`)
VALUES (@subject_id, 'Java 后端成长路线', '从语言基础到 Spring 生态再到数据库与 SQL 优化，共三个阶段。', 1, 0, 0)
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

SET @path_id = (SELECT `id` FROM `learning_path` WHERE `subject_id` = @subject_id);

-- -----------------------------------------------------------------------------
-- 2. 阶段（stage 无业务唯一键，用 NOT EXISTS 保证幂等）
-- -----------------------------------------------------------------------------
INSERT INTO `stage` (`path_id`, `name`, `description`, `sort_no`, `status`, `created_by`, `updated_by`)
SELECT @path_id, t.name, t.description, t.sort_no, 1, 0, 0
FROM (
  SELECT 'Java 基础' AS name, '语法、集合、异常、IO 与 JVM 基础。' AS description, 1 AS sort_no
  UNION ALL SELECT 'Spring 生态', 'IoC、AOP、MVC 与事务管理。', 2
  UNION ALL SELECT '数据库与 SQL', 'MySQL 索引、事务与 SQL 优化。', 3
) t
WHERE NOT EXISTS (SELECT 1 FROM `stage` s WHERE s.`path_id` = @path_id AND s.`name` = t.name);

SET @stage1 = (SELECT `id` FROM `stage` WHERE `path_id` = @path_id AND `name` = 'Java 基础');
SET @stage2 = (SELECT `id` FROM `stage` WHERE `path_id` = @path_id AND `name` = 'Spring 生态');
SET @stage3 = (SELECT `id` FROM `stage` WHERE `path_id` = @path_id AND `name` = '数据库与 SQL');

-- -----------------------------------------------------------------------------
-- 3. 课程（course 无业务唯一键，用 NOT EXISTS(stage_id,title) 保证幂等）
-- -----------------------------------------------------------------------------
INSERT INTO `course` (`stage_id`, `title`, `bilibili_url`, `description`, `duration_seconds`, `cover_url`, `sort_no`, `status`, `created_by`, `updated_by`)
SELECT t.stage_id, t.title, t.url, t.description, t.duration, '', t.sort_no, 1, 0, 0
FROM (
  SELECT @stage1 AS stage_id, 'Java 语法快速入门' AS title,
         'https://www.bilibili.com/video/BV1fh411y7R8' AS url,
         '变量、运算符、流程控制与数组，零基础起步。' AS description, 5400 AS duration, 1 AS sort_no
  UNION ALL SELECT @stage1, '集合框架与泛型', 'https://www.bilibili.com/video/BV1y4411p74E',
         'List、Map、Set 的实现差异与泛型使用。', 6300, 2
  UNION ALL SELECT @stage2, 'Spring IoC 与 AOP', 'https://www.bilibili.com/video/BV1gW411W76m',
         'Bean 生命周期、依赖注入与代理机制。', 7200, 1
  UNION ALL SELECT @stage2, 'Spring MVC 与事务', 'https://www.bilibili.com/video/BV1mE411p7jg',
         '请求处理流程、拦截器与声明式事务。', 6600, 2
  UNION ALL SELECT @stage3, 'MySQL 索引原理', 'https://www.bilibili.com/video/BV1cW411T7Qd',
         'B+ 树结构、聚簇索引与覆盖索引。', 4800, 1
  UNION ALL SELECT @stage3, 'SQL 优化实战', 'https://www.bilibili.com/video/BV1u4411N7bq',
         '执行计划解读与慢查询排查。', 5100, 2
) t
WHERE NOT EXISTS (SELECT 1 FROM `course` c WHERE c.`stage_id` = t.stage_id AND c.`title` = t.title);

-- -----------------------------------------------------------------------------
-- 4. 题目（按同阶段 + 题干幂等）
-- -----------------------------------------------------------------------------
INSERT INTO `question` (`stage_id`, `type`, `content`, `content_hash`, `analysis`, `reference_answer`, `status`, `created_by`, `updated_by`)
SELECT t.stage_id, t.type, t.content, SHA2(LOWER(TRIM(t.content)), 256), t.analysis, t.reference_answer, 1, 0, 0
FROM (
  -- Java 基础
  SELECT @stage1 AS stage_id,
         1 AS type,
         'Java 中 String 是不可变类型。' AS content,
         'String 内部字符数组被 final 修饰且不对外暴露，任何修改都会产生新对象。' AS analysis,
         NULL AS reference_answer
  UNION ALL SELECT @stage1, 2, '下列哪个关键字用于继承一个类？',
         'Java 用 extends 表示类继承，implements 用于实现接口。', NULL
  UNION ALL SELECT @stage1, 3, '以下哪些属于 JVM 运行时数据区？',
         'JVM 规范定义的运行时数据区包括方法区、堆、虚拟机栈、本地方法栈与程序计数器。', NULL
  UNION ALL SELECT @stage1, 4, '简述 == 与 equals 的区别。',
         '考察引用比较与内容比较的区别，以及 equals 重写需同步重写 hashCode。',
         '== 比较基本类型的值或引用地址，equals 默认比较引用地址，可被重写为内容比较。'
  -- Spring 生态
  UNION ALL SELECT @stage2, 2, 'Spring 中用于标注业务层组件的注解是？',
         '@Service 语义化标注服务层；@Repository 标注数据访问层；@Controller 标注控制层。', NULL
  UNION ALL SELECT @stage2, 3, 'Spring 事务的传播行为包括以下哪些？',
         'REQUIRED、REQUIRES_NEW、NESTED 均为标准传播行为；COMPILED 不是事务概念。', NULL
  UNION ALL SELECT @stage2, 4, '简述 IOC 与 DI 的关系。',
         '考察控制反转的思想与依赖注入的实现手段。',
         'IOC 是控制反转的设计思想，DI 是实现该思想的手段，由容器负责创建对象并注入依赖。'
  UNION ALL SELECT @stage2, 1, 'Spring 中 @Transactional 默认只在运行时异常时回滚。',
         '默认回滚规则是 RuntimeException 与 Error，受检异常不回滚。', NULL
  -- 数据库与 SQL
  UNION ALL SELECT @stage3, 1, 'MySQL InnoDB 的默认事务隔离级别是可重复读。',
         'InnoDB 默认 REPEATABLE READ，并通过 MVCC 降低幻读影响。', NULL
  UNION ALL SELECT @stage3, 2, 'InnoDB 中最常用的索引数据结构是？',
         'InnoDB 使用 B+ 树作为索引结构，叶子节点存储数据并按序链接。', NULL
  UNION ALL SELECT @stage3, 3, '事务的 ACID 特性包括以下哪些？',
         'ACID 指原子性、一致性、隔离性、持久性。', NULL
  UNION ALL SELECT @stage3, 4, '简述联合索引的最左前缀原则。',
         '考察联合索引的可用条件与失效场景。',
         '查询条件必须从联合索引的最左列开始连续匹配才能命中索引，跳过最左列或使用范围查询后的列会失效。'
) t
WHERE NOT EXISTS (SELECT 1 FROM `question` q WHERE q.`stage_id` = t.stage_id AND q.`content` = t.content);

-- -----------------------------------------------------------------------------
-- 5. 选项（判断题固定 A=正确、B=错误；简答题无选项）
-- -----------------------------------------------------------------------------
INSERT INTO `question_option` (`question_id`, `option_key`, `content`, `is_correct`, `sort_no`)
SELECT q.`id`, t.option_key, t.content, t.is_correct, t.sort_no
FROM (
  SELECT 'Java 中 String 是不可变类型。' AS q_content, 'A' AS option_key, '正确' AS content, 1 AS is_correct, 0 AS sort_no
  UNION ALL SELECT 'Java 中 String 是不可变类型。', 'B', '错误', 0, 1

  UNION ALL SELECT '下列哪个关键字用于继承一个类？', 'A', 'implements', 0, 0
  UNION ALL SELECT '下列哪个关键字用于继承一个类？', 'B', 'extends', 1, 1
  UNION ALL SELECT '下列哪个关键字用于继承一个类？', 'C', 'import', 0, 2
  UNION ALL SELECT '下列哪个关键字用于继承一个类？', 'D', 'package', 0, 3

  UNION ALL SELECT '以下哪些属于 JVM 运行时数据区？', 'A', '方法区', 1, 0
  UNION ALL SELECT '以下哪些属于 JVM 运行时数据区？', 'B', '堆', 1, 1
  UNION ALL SELECT '以下哪些属于 JVM 运行时数据区？', 'C', '虚拟机栈', 1, 2
  UNION ALL SELECT '以下哪些属于 JVM 运行时数据区？', 'D', 'CPU 寄存器', 0, 3

  UNION ALL SELECT 'Spring 中用于标注业务层组件的注解是？', 'A', '@Repository', 0, 0
  UNION ALL SELECT 'Spring 中用于标注业务层组件的注解是？', 'B', '@Service', 1, 1
  UNION ALL SELECT 'Spring 中用于标注业务层组件的注解是？', 'C', '@Controller', 0, 2
  UNION ALL SELECT 'Spring 中用于标注业务层组件的注解是？', 'D', '@ComponentScan', 0, 3

  UNION ALL SELECT 'Spring 事务的传播行为包括以下哪些？', 'A', 'REQUIRED', 1, 0
  UNION ALL SELECT 'Spring 事务的传播行为包括以下哪些？', 'B', 'REQUIRES_NEW', 1, 1
  UNION ALL SELECT 'Spring 事务的传播行为包括以下哪些？', 'C', 'NESTED', 1, 2
  UNION ALL SELECT 'Spring 事务的传播行为包括以下哪些？', 'D', 'COMPILED', 0, 3

  UNION ALL SELECT 'Spring 中 @Transactional 默认只在运行时异常时回滚。', 'A', '正确', 1, 0
  UNION ALL SELECT 'Spring 中 @Transactional 默认只在运行时异常时回滚。', 'B', '错误', 0, 1

  UNION ALL SELECT 'MySQL InnoDB 的默认事务隔离级别是可重复读。', 'A', '正确', 1, 0
  UNION ALL SELECT 'MySQL InnoDB 的默认事务隔离级别是可重复读。', 'B', '错误', 0, 1

  UNION ALL SELECT 'InnoDB 中最常用的索引数据结构是？', 'A', 'B+ 树', 1, 0
  UNION ALL SELECT 'InnoDB 中最常用的索引数据结构是？', 'B', '哈希表', 0, 1
  UNION ALL SELECT 'InnoDB 中最常用的索引数据结构是？', 'C', '全文索引', 0, 2
  UNION ALL SELECT 'InnoDB 中最常用的索引数据结构是？', 'D', '位图', 0, 3

  UNION ALL SELECT '事务的 ACID 特性包括以下哪些？', 'A', '原子性', 1, 0
  UNION ALL SELECT '事务的 ACID 特性包括以下哪些？', 'B', '一致性', 1, 1
  UNION ALL SELECT '事务的 ACID 特性包括以下哪些？', 'C', '隔离性', 1, 2
  UNION ALL SELECT '事务的 ACID 特性包括以下哪些？', 'D', '持久性', 1, 3
) t
JOIN `question` q ON q.`content` = t.q_content
ON DUPLICATE KEY UPDATE `content` = VALUES(`content`), `is_correct` = VALUES(`is_correct`);

-- -----------------------------------------------------------------------------
-- 6. 评分点（简答题）
-- -----------------------------------------------------------------------------
INSERT INTO `question_scoring_point` (`question_id`, `point_text`, `sort_no`)
SELECT q.`id`, t.point_text, t.sort_no
FROM (
  SELECT '简述 == 与 equals 的区别。' AS q_content, '说明 == 比较值或引用地址' AS point_text, 0 AS sort_no
  UNION ALL SELECT '简述 == 与 equals 的区别。', '说明 equals 可被重写为内容比较', 1
  UNION ALL SELECT '简述 == 与 equals 的区别。', '提到重写 equals 需同步重写 hashCode', 2

  UNION ALL SELECT '简述 IOC 与 DI 的关系。', '说明 IOC 是控制反转的设计思想', 0
  UNION ALL SELECT '简述 IOC 与 DI 的关系。', '说明 DI 是 IOC 的实现手段', 1
  UNION ALL SELECT '简述 IOC 与 DI 的关系。', '提到由容器负责对象创建与依赖注入', 2

  UNION ALL SELECT '简述联合索引的最左前缀原则。', '说明必须从最左列开始连续匹配', 0
  UNION ALL SELECT '简述联合索引的最左前缀原则。', '说明跳过最左列会导致索引失效', 1
  UNION ALL SELECT '简述联合索引的最左前缀原则。', '提到范围查询之后的列无法继续使用索引', 2
) t
JOIN `question` q ON q.`content` = t.q_content
ON DUPLICATE KEY UPDATE `point_text` = VALUES(`point_text`);

-- -----------------------------------------------------------------------------
-- 7. 面试能力维度（按学科维护；报告评分按维度出分）
-- -----------------------------------------------------------------------------
INSERT INTO `dimension` (`subject_id`, `name`, `sort_no`, `status`, `created_by`, `updated_by`)
VALUES
  (@subject_id, '基础语法与语言特性', 10, 1, 0, 0),
  (@subject_id, '集合与并发', 20, 1, 0, 0),
  (@subject_id, 'JVM 与性能调优', 30, 1, 0, 0),
  (@subject_id, '框架与工程实践', 40, 1, 0, 0),
  (@subject_id, '数据库与 SQL 能力', 50, 1, 0, 0)
ON DUPLICATE KEY UPDATE `sort_no` = VALUES(`sort_no`);
