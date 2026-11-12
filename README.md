# 云枢智学

AI 驱动的职业教育学习平台：学习路线 + 随堂练习 + 错题本 + 模拟面试，配套管理后台。

## 项目结构

```
├── backend/            Spring Boot 3 后端（模块化单体）
├── web/                pnpm monorepo 前端
│   ├── apps/student/   学生端 SPA（Vue 3 + Vant 4，端口 5173）
│   ├── apps/admin/     管理端 SPA（Vue 3 + Element Plus，端口 5174）
│   └── packages/       shared（类型/错误码/HTTP 封装）、config（TS 配置）
├── docs/               需求、设计文档、数据库规则、API 规则、OpenAPI 骨架
├── docker-compose.yml  本地依赖：MySQL 8.0 + MinIO（Redis 属 P1，--profile p1）
└── prototype/          页面原型
```

## 环境要求

- JDK 17、Maven 3.9+
- Node.js 20+、pnpm 10（`npm i -g pnpm@10` 或 `corepack enable`）
- Docker（运行本地 MySQL / MinIO）

## 快速启动

```bash
# 1. 启动依赖（首次先复制 .env.example 为 .env）
docker compose up -d

# 2. 启动后端（Flyway 自动建表 + 种子数据；AI/短信为 mock，存储走 MinIO）
cd backend && run-dev.cmd          # Windows：固定 JDK 17 + 端口，避免 JAVA_HOME 指向 JDK 8
#    或   mvn spring-boot:run
#    健康检查  http://127.0.0.1:18080/actuator/health
#    接口文档  http://127.0.0.1:18080/swagger-ui.html

# 3. 启动前端（另开终端，在 web/ 目录）
cd web
pnpm install
pnpm dev:student   # 学生端 http://localhost:5173
pnpm dev:admin     # 管理端 http://localhost:5174
```

> **端口说明**：文档约定后端 8080，但部分 Windows 机器的 8080 落在系统保留端口区间
> （`netsh interface ipv4 show excludedportrange protocol=tcp` 可查，如 8077–8176），
> 任何程序都无法绑定。`run-dev.cmd` 默认用 **18080**，前端通过
> `VITE_PROXY_TARGET` 指向它。若你的机器 8080 可用，把 `run-dev.cmd` 里的
> `APP_PORT` 和两个 `.env.development` 的 `VITE_PROXY_TARGET` 一起改回 8080 即可。

> **访问地址必须用 `localhost`**：Vite dev server 在 Windows 上只监听 IPv6 `::1`，
> 用 `127.0.0.1:5173` 会连接失败。

前端开发模式经 Vite 代理 `/api` 到后端，无需额外配置跨域。

## 开发数据

```bash
# 后台管理员 admin / Admin@123456（V2 种子只建角色，不建账号）
docker exec -i yunshu-mysql mysql -uyunshu_app -p yunshu --default-character-set=utf8mb4 \
  < backend/src/main/resources/db/dev/seed_dev_admin.sql

# 内容演示数据：1 学科 + 1 路线 + 3 阶段 + 6 课程 + 12 题
docker exec -i yunshu-mysql mysql -uyunshu_app -p yunshu --default-character-set=utf8mb4 \
  < backend/src/main/resources/db/dev/seed_dev_content.sql
```

| 端 | 账号 | 密码 |
| --- | --- | --- |
| 管理后台 | `admin` | `Admin@123456` |
| 学生端 | 自行注册（手机号 + 验证码） | 注册时设置，8–32 位含字母和数字 |

学生端注册需要短信验证码：dev 环境为 mock，**验证码打印在后端控制台**，
形如 `[dev mock 短信] phone=138****8000, scene=REGISTER, code=122064`。

## 常用命令

| 位置 | 命令 | 说明 |
| --- | --- | --- |
| `backend/` | `mvn test` | 运行后端测试 |
| `backend/` | `mvn -DskipTests package` | 打包 `target/yunshu-zhixue.jar` |
| `web/` | `pnpm typecheck` | 全部工作区类型检查 |
| `web/` | `pnpm build` | 两个应用生产构建 |
| `web/` | `pnpm lint` / `pnpm format` | ESLint / Prettier |

## 当前状态

**已完成：认证闭环（C 端 + B 端）**

- 统一响应与错误码、traceId 贯穿、C/B 双 Security 过滤链（JWT 双 audience 双密钥）
- 账号状态与模块权限校验（Caffeine 60s）、MyBatis-Plus + Flyway（V1/V2）
- 对象存储端口（dev=MinIO/S3，prod=OSS）、AI/短信端口 + dev mock、启动配置校验
- C 端：验证码、注册、密码登录、验证码登录、找回密码、刷新、登出、个人资料
- B 端：登录、刷新、登出、当前管理员与模块权限
- **Refresh Token 一律走 HttpOnly Cookie**（C 端 `ys_c_rt` Path=/api/v1/c，B 端 `ys_b_rt`
  Path=/api/v1/admin，7 天，轮换 + 重用检测），access token 只存内存
- 前端：双 SPA 路由与守卫、统一 HTTP 封装（token 注入、401 刷新重放、traceId）、
  Pinia 会话 store、登录/注册/个人中心页面已接真实接口

**已完成：后台内容底座（PRD 6.2–6.4）**

- 学科：列表/详情/新增/编辑/启停/全量排序（`/api/v1/admin/subjects`，6 个接口）
- 课程：列表/详情/新增/编辑/启停/全量排序（`/api/v1/admin/courses`，6 个接口），
  含 B 站链接白名单校验、阶段归属学科校验
- 题库：列表/详情/新增/编辑/启停（`/api/v1/admin/questions`，5 个接口），
  含四种题型的选项与评分点规则、同阶段题干 SHA-256 去重
- 学习路线：列表/详情/新增/编辑/启停（`/api/v1/admin/learning-paths`），
  阶段：新增/编辑/启停/全量排序/已挂载内容（`/api/v1/admin/stages`），共 13 个接口
- 管理端「内容管理」页已接真实数据：学科 / 课程 / 题库 三个标签页
- 管理端「学习路线」页已接真实数据：路线表格 + 阶段编排抽屉（增删改停用、上下移排序、阶段内容预览）
- 内容变更写 `operation_log` 审计；错误码 40001–40014/40034/40035 已按 04 文档分配

**已完成：后台运营模块（PRD 6.6–6.10）**

- 学员管理（`/api/v1/admin/students`，7 个接口）：列表（手机号盲索引精确、昵称模糊、路线/状态/注册区间）、
  详情（完成度 / 掌握度 / 最近动态 / 面试汇总）、重置密码（一次性明文 + 撤销全部 refresh）、
  调整当前路线（幂等，不删历史进度）、停用 / 启用 / 注销（二次确认、释放手机号、脱敏保留历史）
- 后台账号（`/api/v1/admin/admin-users`，7 个接口）：登录名唯一、首次登录强制改密、
  不可停用自己（40027）、重置密码撤销 refresh
- 角色权限（`/api/v1/admin/roles`，3 个接口）：固定三角色只读列表/详情，
  模块全量覆盖提交，`super_admin` 不可改（40031），模块编码白名单校验（40032）
- 操作日志（`/api/v1/admin/operation-logs`，只读）：游标分页不返回 total、
  模块/动作/对象/结果/时间区间过滤（跨度 ≤ 90 天）、不提供编辑与导出
- 数据看板（`/api/v1/admin/dashboard`）：TODAY/7D/30D，六个区块独立取数，
  单区块失败降级不整包失败，Caffeine 60s 缓存；口径按 03 §7.2（总数含注销、正确率按题量加权、
  面试完成率排除创建中与作废）
- 管理端页面全部接真实接口：学生管理、管理员与权限（账号 + 角色两个标签页）、操作日志、数据看板
- 错误码 40001–40036 已按 04 文档分配，管理动作统一写 `operation_log`

**已完成：面试设置（PRD 6.6）**

- 难度档位（`/api/v1/admin/interview-difficulties`，4 个接口）：全局固定三档只读列表、
  编辑文案与考察重点（`code` 不可改）、启停
- 能力维度（`/api/v1/admin/dimensions`，6 个接口）：按学科查询、新增（学科内名称唯一）、
  编辑（不跨学科移动）、启停、全量排序（必须覆盖该学科全部维度）
- 管理端「面试设置」页：难度档位 / 能力维度两个标签页，维度支持上下移排序
- 错误码 40015–40018 已分配；种子数据补 5 个 Java 后端能力维度

**已完成：C 端学习路线 / 课程进度 / 随堂练习 / 错题本（PRD 5.4–5.6）**

- 学习路线（`/api/v1/c/learning-paths`、`/c/students/me/current-path`，8 个接口）：
  路线列表带完成度、路线详情、阶段详情、切换主路线（幂等）、课程开始/完成（幂等）、
  停用内容返回 `30031`；掌握度取最近一次已完成阶段练习的正确率，完成度按启用课程实时计算
- 随堂练习（`/api/v1/c/stages/{id}/practices` 等 7 个接口）：创建/复用、断点续答、
  四种题型作答判分、简答降级转学员自评、交卷、结果页；幂等由 `uk_answer`、
  `uk_course_progress` 兜底，重复提交内容不同返回 `30025`
- 简答判分两个短事务 + 事务外调模型：AI 失败写 `judge_status=3`（待自评）+
  `is_degraded=1`，**绝不当 0 分**
- 错题本（`/api/v1/c/practices/wrong-questions` 等 3 个接口）：列表（折叠空白取 60 字预览）、
  详情（正确答案、评分点、我的上次作答）、错题重练；答对自动移出、答错重新入册
- 学生端页面接真实接口：学习路线、路线详情、阶段学习、随堂练习、错题本（含重练入口）
- 错误码 30020–30034 已按 04 文档分配

**已完成：C 端 AI 模拟面试（PRD 5.7）**

- 九个接口：`/c/interview-options`、`/c/interview-quota`、创建/列表/详情
  （`/c/interview-sessions`）、提交一轮作答、语音转写、提前结束、报告
- 固定 4 轮（开场 1 + 追问 3）；`current_round` 是"已完成作答轮次"，对外
  `currentRound` 为待答轮次（进行中 = +1，完成后固定 4）
- 每日 3 次按 `Asia/Shanghai` 自然日，`INSERT IGNORE` + 条件自增原子占用，
  并发只有一个成功；创建即扣次，扣减与建会话同事务
- 只有"开场题未生成、会话未进入可作答状态"才作废并返还次数（HTTP 502 + `50002`，
  `data.refunded=true` 并带最新余量）；正常完成/未完成/过期一律不返还
- 调模型全部在事务外：先落回答再出下一题；追问失败不回滚回答，按 `INCOMPLETE` 结束并出
  未完成报告；报告生成失败不建报告（报告接口 `30051`），无有效作答返回 `30052`
- 语音只转写不落库：音频 `webm/ogg/mp4/m4a/wav`、≤2MB，超过返回 `30060/30061/30062`；
  ASR 失败为 200 降级（`asrDegraded=true`、空转写），前端切文字作答
- 幂等：`(session_id, round_no, role)` 唯一键；同轮同内容返回 `replayed=true`、
  不同内容 `30047`；60 秒内同配置 `IN_PROGRESS` 会话复用（`reused=true`，不扣次）
- 权限：会话与报告归属失败统一 `403 + 30041`，与"不存在"完全一致
- dev mock 按 `metadata.task` 返回结构化 JSON（`interview.opening/followup/report`），
  需要联调降级分支时用 `INTEGRATION_AI_MOCK_DEGRADED_TASKS=interview.opening` 指定
- 学生端页面：面试入口（余量/难度/历史）、面试对话（文字 + 录音转写、提前结束）、
  面试报告（总分/维度条/建议，未完成标注）；错误码 30040–30062 已分配

**已完成：C 端首页聚合（PRD 5.3）**

- `GET /api/v1/c/home`：一次返回主路线进度、继续学习、随堂练习入口、面试余量、
  最近动态与公告位；整页 Caffeine 60 秒缓存（按 studentId），`meta.dataAsOf` 标记快照时间
- 四个区块各自取数、各自降级：任一区块异常只把该区块置 `null` 并写入
  `meta.degradedSections`，整体仍是 200；含降级的响应不写缓存，依赖恢复后立刻回到完整数据
- 无主路线（或路线/学科被停用）返回 `hasPath=false` + 其余区块 `null`，不是错误
- 最近动态由课程完成 / 阶段练习完成 / 面试完成派生（不建 `student_activity` 表，04-Q46）
- 学生端首页接真实数据：问候语、路线进度条与掌握度、继续学习、练习/面试双入口、动态列表、
  降级提示与"数据更新于"

**已完成：C 端学习记录与个人中心（PRD 5.9）**

- 学习记录（4 个接口）：已完成课程、练习记录（含是否计入掌握度）、阶段掌握度趋势
  （倒序取 N 条再转升序）、两次面试报告对比（维度按 ID 合并、缺失侧为 `null`）
- 个人中心（5 个接口）：`GET/PATCH /c/students/me`（只允许昵称/头像，未声明字段 10002）、
  改密（旧密码错 `30008`、策略 `30005`、新旧相同 `30005`）、换绑手机号（同号 `30010`、
  新号占用 `30009`、原号/新号双验证码）、注销（确认文本「注销」+ 验证码 `30011`，
  释放手机号、清空昵称头像、写随机密码、撤销全部 refresh）
- 补充接口 `POST /c/students/me/sms-codes`：只接受 `CHANGE_PHONE_OLD` / `CANCEL`，
  由服务端按 token 取号下发，前端不必回填完整手机号
- 图片上传 `POST /c/uploads/images`：魔数判定真实格式（jpg/png/webp）、≤2MB、≤4096×4096，
  校验顺序 空 → 大小 → 魔数 → 尺寸；库只存 `key`，返回 30 分钟签名 `url`
- 改密 / 换绑 / 注销后 `revokeAllFor` 撤销 refresh，并 **主动 evict 账号状态缓存**，
  注销后下一次请求立刻 403 + `20006`（后台停用/注销同样立即生效）
- 学生端「我的」页接真实接口：头像上传、改昵称、改密码、换绑手机号、注销账号四个弹层

**已完成：后台文件上传与题库批量导入（PRD 6.4 / B-12）**

- `POST /api/v1/admin/uploads/images`：purpose 决定归属模块与对象前缀
  （`SUBJECT_COVER`/`COURSE_COVER`），
  按调用方模块动态校验（无权限 `403 + 20004`），前缀由服务端固定，不接受客户端指定
- 题库批量导入三步：`GET /admin/questions/import-template` 下载 xlsx 模板（含 4 种题型示例行
  与填写说明页）、`POST /admin/questions/import` 上传并启动异步任务、`GET .../import/{taskId}`
  轮询结果（处理中 `40022`）
- 文件级校验：仅 `.xlsx`（`40019`）、≤5MB（`40020`）、≤1000 行（`40024`）、
  空文件/仅表头（`40023`）；任务归属错误 `40021`
- 行级校验复用手工新增的口径：题型、阶段归属学科、题干长度与同阶段 SHA-256 去重
  （含文件内重复）、选项数量与正确答案标记、简答评分点必填
- **任一行失败整批回滚**：`successRows=0`，逐行错误写 `import_job_error`，
  行号用 Excel 真实行号；状态与错误明细同事务提交，避免轮询读到"已失败但无明细"
- xlsx 读写走自带的 `common/excel/SimpleXlsx`（不引入 POI，离线构建可用）
- 管理端题库页新增「批量导入」弹层：下载模板、选文件、轮询进度、成功/失败与错误明细表

**已完成：面试每日次数可配置**

- `interview.quota.daily-limit`（环境变量 `INTERVIEW_DAILY_LIMIT`，默认 3）：
  改配置即可调整，不再写死在代码里；非法值回落 3

**已完成：C 端公告位与后台公告管理**

- `V3__notice_schema.sql` 新增 `notice` 表与第 11 个模块码 `notice`
  （授权给 super_admin 与 content_ops，学员运营不涉及内容不授权）
- 后台 7 个接口（`GET/POST /admin/notices`、`GET/PATCH /admin/notices/{id}`、
  `POST .../{id}/publish`、`POST .../{id}/offline`、`DELETE .../{id}`）
- 状态三态：草稿 / 已发布 / 已下线。发布与下线是独立动作接口（同 enable/disable 风格），
  删除只允许草稿 → 不存在"删掉线上内容"的路径；`published_at` 只在首次发布写入，
  下线后重新发布保留原始发布时间
- 置顶（`pinned`）与排序号只影响 C 端顺序；`expire_at` 为 `NULL` 表示长期有效，
  过期公告不自动改状态，仍可在后台看到并手动下线
- 编辑用部分更新语义，失效时间用 `expireAt` 覆盖、`clearExpire=true` 清空、都不传则不修改
- C 端首页 `notices` 取「已发布且未过期」的前 3 条，置顶优先；查询失败按 `notices`
  区块降级（置空数组并写 `meta.degradedSections`），无主路线的学员也能看到公告
- 时间统一 RFC 3339 入参、`UTC` 落库；管理端「公告管理」页与 C 端首页公告卡片已接真实接口

**已完成：消息通知（后台推送 + C 端消息中心）**

- `V4__message_schema.sql` 新增 `message`（稿件）与 `message_receive`（收件）两表，
  以及第 12 个模块码 `message`（授权给 super_admin 与 student_ops，内容运营不介入触达）
- 稿件与收件分离：已读、删除都是收件侧状态；撤回只改稿件状态（`status=3`），
  C 端立即不可见但收件行保留，后台仍能统计发出量
- 受众两种：`ALL` 全体学员走**懒投递**（发送只记人数快照，学员首次访问消息中心时
  补齐收件行，注册之前发的消息不会推给新学员）；`SPECIFIED` 指定学员在发送时落收件行，
  单次 ≤200 人，不存在/已注销的 ID 直接 `40041` 拒绝，不做静默丢弃
- 状态机 `DRAFT → SENT → REVOKED`（终态）：已发送不可编辑、不可删除，只能撤回；
  已撤回不可重发、不可删除（保留历史）
- 后台 7 个接口（`messages` 列表/详情/新建/编辑/发送/撤回/删除），
  C 端 5 个接口（列表、未读数、单条已读、全部已读、删除）
- 成员语义完全由 access token 决定，路径里没有学员 ID；消息不存在统一 `40039`，
  不区分"没这条消息"和"这条消息不是发给你的"
- 管理端「消息推送」页（定向推送可按昵称搜索学员）、C 端「消息中心」页
  （未读在前、单条/全部已读、删除）与首页铃铛未读红点已接真实接口

**已完成：孤儿文件巡检（04-Q47 P0 方案 A）**

- `GET /api/v1/admin/system/orphan-files`：只读巡检，绝不删除对象；按 `operation_log`
  模块鉴权（只有 super_admin 有该模块，受限账号 `403 + 20004`）
- 库里只有三列承接对象 key：`subject.cover_url`、`course.cover_url`、`student.avatar_url`。
  巡检按 `subject` / `course` / `avatar` 三个前缀列出对象，与库中被引用 key 求差集
- 结果是**当前快照**语义：刚上传还没来得及保存的 key 也会出现在候选里，
  所以只能人工确认后处理，不接自动清理（这也是不建 `file_asset` 表换来的代价）
- 上传用途同步收敛为 `SUBJECT_COVER` / `COURSE_COVER`（04 §6.10.12）：
  阶段与题目没有图片列，开放对应用途会让上传的 key 无处绑定、稳定产生孤儿
- `ObjectStorage` 端口新增 `list(prefix)`（S3 `listObjectsV2` / OSS `listObjects` 分页），
  巡检每日 03:00 定时执行、只打日志摘要，自身捕获异常不影响主营业务

**已完成：评审缺口 G-01～G-04 全部关闭**

- **G-01 课程视频链接**：`GET /c/stages/{id}` 的课程项与 `POST /c/courses/{id}/start`、`/complete`
  响应都补上 `bilibiliUrl`（只读 `course.bilibili_url` 已有字段，不新增表）。
  之前 C-04 拿不到视频地址，课程学习闭环是断的；现在开始学习后直接跳转，
  已开始的课程另有「观看视频」入口。未配置视频返回空串，URL 一律由服务端下发，前端不拼接
- **G-02 改密支持验证码**：PRD 5.10 要求"原密码或验证码二选一"。
  `ChangePasswordRequest` 增加可选 `smsCode`，服务端做"恰好一个"校验
  （都传或都不传 → `10001`，不静默偏向某一种）。新增 `SmsPurpose.CHANGE_PASSWORD`（purpose=6），
  **不与 `RESET_PASSWORD` 共用**——找回密码是未登录场景、改密是已登录场景，
  共用一个 purpose 会让两种验证码互相可复用。C 端个人中心改密弹层支持两种模式切换
- **G-03 练习题目快照**：`V5` 新增 `practice_question(practice_id, question_id, sort_no)`。
  创建练习时与练习行**同事务**落快照，之后答题/续答/判分全部读快照，
  题库增删/停用不再影响进行中的练习（7 天续答期内可复现）。
  快照为空时回退到按 `stage_id` 推导，兼容 V5 之前创建的历史练习
- **G-04 幂等 24 小时持久化**：`V5` 新增 `idempotency_record`（scope+key 唯一、
  `request_hash`、`response_json`、24 小时 `expire_at`），满足 api-rules §9.2。
  成功才缓存结果，失败只占位（`status=2`）**允许用同一个 key 重试**——失败不该被幂等锁死；
  同 key 换了请求体 → `40042`，不返回旧结果

**未实现：其余业务模块**

- **数据导出：不做**。PRD 第 12 章把「导出」排在 P2 本期范围外，且第 13 章默认值
  第 18/29 条明确"操作日志不提供导出"、01 文档对学习记录（不做导出）、
  学员管理（不导出学员清单）、数据看板（不做看板导出）也都有明确边界，
  因此本期不提供任何导出接口
- **题库图片题面：不做**。PRD 未定义图片题面，`question` 表也没有图片列，属无依据需求
- P1 项：文件元数据表 `file_asset`（触发条件：巡检月候选 >100 个）、
  精确的孤儿文件自动清理（依赖 `file_asset`）

## 文档索引

- 架构与技术选型：`docs/design/02.技术选型与整体架构.md`
- 数据库：`docs/rules/database-rules.md`、`docs/design/schema/`
- API 契约：`docs/rules/api-rules.md`、`docs/design/04.API接口设计.md`、`docs/design/openapi/`
- 对象存储：`docs/design/06.对象存储端口设计.md`
