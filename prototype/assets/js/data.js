/* =========================================================
   云枢智学 · 原型数据层
   所有数据仅用于前端演示，保存在 localStorage
   ========================================================= */
(function () {
  'use strict';

  /* ---------------- 本地存储 ---------------- */
  const mem = {};
  const Store = {
    get(key, fallback) {
      try {
        const raw = localStorage.getItem('ys_' + key);
        if (raw != null) return JSON.parse(raw);
      } catch (e) { /* ignore */ }
      return key in mem ? mem[key] : fallback;
    },
    set(key, val) {
      mem[key] = val;
      try { localStorage.setItem('ys_' + key, JSON.stringify(val)); } catch (e) { /* ignore */ }
      return val;
    },
    del(key) {
      delete mem[key];
      try { localStorage.removeItem('ys_' + key); } catch (e) { /* ignore */ }
    }
  };

  /* ---------------- 学科 ---------------- */
  const SUBJECTS = [
    {
      id: 'agent', name: 'Agent 开发',
      tagline: '从大模型原理到多智能体落地',
      desc: '适合想进入 AI 应用方向的开发者：先理解模型，再学会让模型稳定工作，最后完成一个可上线的多智能体应用。',
      color: '#4F46E5', color2: '#7C3AED', icon: 'bot', badge: '热门'
    },
    {
      id: 'ai-full', name: 'AI 全栈开发',
      tagline: 'Python 后端 + 大模型应用全链路',
      desc: '从 Python 后端到 RAG、流式输出、工程化上线，覆盖一个大模型应用从 0 到 1 的完整链路。',
      color: '#0EA5E9', color2: '#22D3EE', icon: 'stack', badge: '系统'
    },
    {
      id: 'java', name: 'Java 开发',
      tagline: '从语言基础到微服务架构',
      desc: '面向后端岗位的经典路径：语法与集合、Spring Boot 与数据层、微服务与可观测性，层层递进。',
      color: '#F59E0B', color2: '#FBBF24', icon: 'coffee', badge: '经典'
    }
  ];

  /* ---------------- 路线 / 阶段 / 课程 / 题目 ---------------- */
  const ROUTE_DEFS = {
    agent: {
      name: 'Agent 开发学习路线',
      desc: '4 个阶段，从理解大模型到做出一个多智能体应用。每个阶段都配有刷题与 AI 面试，学完即可自测。',
      stages: [
        {
          name: '阶段一 · 大模型基础', goal: '理解 LLM 的能力边界、成本结构与提示词的基本结构',
          courses: ['大模型是如何“思考”的', 'Token、上下文窗口与成本', '提示词的基本结构', '模型选型与 API 调用'],
          questions: [
            { type: 'judge', stem: '大模型的上下文窗口越大，单次调用的成本一定越低。', options: ['正确', '错误'], answer: [1], explanation: '上下文窗口是“能装下多少”的上限，不是成本的决定因素。实际费用取决于本次输入与输出的 token 数量，上下文越长通常意味着输入 token 越多，成本反而更高。' },
            { type: 'single', stem: '以下哪项最直接影响一次大模型调用的费用？', options: ['模型参数量', '输入与输出的 token 数', '提示词使用的语言', '调用发生的时段'], answer: [1], explanation: '主流模型按 token 计费，费用主要由本次调用的输入 token 与输出 token 数量决定。' },
            { type: 'multi', stem: '以下哪些做法有助于降低大模型调用成本？', options: ['精简系统提示词', '对重复请求做缓存', '提高 temperature', '控制输出长度'], answer: [0, 1, 3], explanation: '减少输入、复用结果、限制输出都能直接减少 token；temperature 影响随机性，与成本无关。' },
            {
              type: 'short', stem: '请简述 temperature 参数的作用，以及什么场景下应该把它调低。',
              points: [
                { text: '说明 temperature 控制输出的随机性 / 多样性', score: 35, keywords: ['随机', '多样', '创造', '发散', '确定性'] },
                { text: '指出值越低，输出越稳定、越可复现', score: 30, keywords: ['越低', '更低', '稳定', '确定', '可复现', '保守'] },
                { text: '说明事实性、结构化或需要稳定结果时应调低', score: 35, keywords: ['事实', '结构化', 'json', '抽取', '准确', '分类'] }
              ],
              reference: 'temperature 控制模型输出的随机性：值越高越发散、越有创造性，值越低越稳定、越确定。做事实问答、信息抽取、结构化输出（如 JSON）或需要可复现结果时，应把 temperature 调低。'
            }
          ]
        },
        {
          name: '阶段二 · Prompt 工程', goal: '让模型稳定、可控地完成业务任务',
          courses: ['结构化提示词：角色、任务、约束', 'Few-shot 与示例设计', '让模型稳定输出 JSON', '提示词的评测与迭代'],
          questions: [
            { type: 'judge', stem: 'Few-shot 示例给得越多，模型效果就一定越好。', options: ['正确', '错误'], answer: [1], explanation: '示例过多会占用上下文、增加成本，还可能引入噪声或让模型过度模仿。示例质量与代表性比数量更重要。' },
            { type: 'single', stem: '要让模型稳定输出 JSON，最有效的做法是？', options: ['提高 temperature', '给出 JSON schema 和示例', '在提示词里多说“请”', '把提示词写短'], answer: [1], explanation: '明确的结构约束 + 示例是最可靠的方式；部分模型还支持 JSON 模式或结构化输出参数。' },
            { type: 'multi', stem: '一个结构化的提示词通常包含哪些要素？', options: ['角色设定', '任务目标', '输出格式', '约束条件'], answer: [0, 1, 2, 3], explanation: '角色、目标、格式、约束共同构成可复用的提示词骨架，缺一都会让输出变得不稳定。' },
            {
              type: 'short', stem: '什么是 Few-shot？相比 Zero-shot 的优势和代价分别是什么？',
              points: [
                { text: '说明 Few-shot 是在提示词中提供少量示例', score: 35, keywords: ['示例', '例子', 'few-shot', 'few shot', '样例'] },
                { text: '说明优势：提升格式、风格与任务理解的一致性', score: 35, keywords: ['格式', '风格', '稳定', '准确', '引导', '一致'] },
                { text: '说明代价：增加 token 成本与示例维护成本', score: 30, keywords: ['token', '成本', '维护', '长度', '占用'] }
              ],
              reference: 'Few-shot 是在提示词里给出少量输入-输出示例，让模型照着做。优势是能显著提升输出格式、风格与任务理解的一致性；代价是占用上下文、增加 token 成本，并且示例需要持续维护。'
            }
          ]
        },
        {
          name: '阶段三 · Agent 框架', goal: '掌握工具调用、记忆与多步任务编排',
          courses: ['Agent 的核心循环：感知-决策-行动', '工具调用与函数签名设计', '记忆与状态管理', '用框架搭建第一个 Agent'],
          questions: [
            { type: 'judge', stem: 'Agent 与普通对话机器人最大的区别，是它能自主调用工具并多步执行任务。', options: ['正确', '错误'], answer: [0], explanation: 'Agent 的核心是“模型决策 + 工具执行 + 循环”，可以围绕目标多步推进，而不是只做单轮问答。' },
            { type: 'single', stem: '在 Agent 循环中，决定下一步调用哪个工具的是？', options: ['用户', '模型', '数据库', '前端页面'], answer: [1], explanation: 'Agent 由模型根据当前上下文与工具描述进行决策，框架负责执行与回填结果。' },
            { type: 'multi', stem: '设计一个供 Agent 调用的工具时，需要考虑哪些要素？', options: ['清晰的名称与描述', '参数 schema', '错误处理', '精简的返回结果'], answer: [0, 1, 2, 3], explanation: '名称与描述决定模型能否选对工具，schema 决定参数是否正确，错误处理与精简返回决定循环能否稳定推进。' },
            {
              type: 'short', stem: '为什么工具的描述（description）对 Agent 的表现很关键？',
              points: [
                { text: '说明模型是依据描述来选择是否调用、调用哪个工具', score: 40, keywords: ['选择', '调用', '判断', '决定', '依据'] },
                { text: '说明描述模糊会导致误调用、漏调用', score: 30, keywords: ['误调用', '错误', '不准', '选错', '漏', '混淆'] },
                { text: '说明描述应包含用途、参数、输入输出与适用边界', score: 30, keywords: ['用途', '参数', '输入', '输出', '边界', '场景', '约束'] }
              ],
              reference: '模型只能通过工具的名称与描述来判断“该不该用、用哪个”。描述模糊会让它误调用或漏调用；好的描述会说明用途、参数含义、输入输出格式以及不适用的边界。'
            }
          ]
        },
        {
          name: '阶段四 · 多智能体实战', goal: '完成一个可上线的多智能体应用',
          courses: ['多智能体协作模式', '任务拆解与角色分工', '失败重试与人工兜底', '上线一个多智能体应用'],
          questions: [
            { type: 'judge', stem: '多智能体系统一定比单智能体效果更好。', options: ['正确', '错误'], answer: [1], explanation: '多智能体会引入通信、状态同步与成本问题。只有任务确实可以拆分、需要多角色协作时，收益才明显。' },
            { type: 'single', stem: '多智能体协作中最常见的工程问题是？', options: ['模型不够聪明', '上下文与状态在 Agent 之间传递混乱', '用户数量太多', '前端渲染太慢'], answer: [1], explanation: '状态如何传递、谁负责最终决策、失败如何回滚，是多智能体落地时最容易失控的部分。' },
            { type: 'multi', stem: '以下哪些场景更适合用多智能体？', options: ['任务可以拆成相对独立的子任务', '需要多个角色互相评审', '简单的单轮问答', '需要并行处理多个子任务'], answer: [0, 1, 3], explanation: '可拆分、可并行、需要多角色视角的任务更适合多智能体；简单单轮问答用单 Agent 甚至直接调用模型更划算。' },
            {
              type: 'short', stem: '在 Agent 应用中，为什么需要设计“人工兜底”？',
              points: [
                { text: '说明模型会失败、产生幻觉或做出错误决策', score: 35, keywords: ['失败', '幻觉', '错误', '不稳定', '误判'] },
                { text: '说明高风险或不可逆动作需要人工确认', score: 35, keywords: ['高风险', '确认', '审核', '人工', '不可逆', '敏感'] },
                { text: '说明人工兜底能提升系统可靠性 / 用户体验', score: 30, keywords: ['可靠', '兜底', '安全', '体验', '可控'] }
              ],
              reference: 'Agent 的决策并不总是正确，高风险动作（下单、支付、删除、对外发送）一旦执行可能不可逆。人工兜底用于在关键节点确认、纠正或接管，既控制风险也提升用户信任。'
            }
          ]
        }
      ]
    },

    'ai-full': {
      name: 'AI 全栈开发学习路线',
      desc: '3 个阶段，覆盖 Python 后端、大模型应用与工程化上线，适合想独立做出 AI 产品的开发者。',
      stages: [
        {
          name: '阶段一 · Python 与后端基础', goal: '能独立写出可用的 API 服务',
          courses: ['Python 核心语法速通', '同步、异步与事件循环', 'FastAPI 快速上手', '接口设计与鉴权'],
          questions: [
            { type: 'judge', stem: '在 FastAPI 中，async def 定义的接口一定会比普通 def 更快。', options: ['正确', '错误'], answer: [1], explanation: '异步的优势在于 IO 等待时能切换任务；如果接口里是阻塞调用或 CPU 密集计算，async 并不会更快。' },
            { type: 'single', stem: '以下哪个最适合用来做接口的身份认证？', options: ['Cookie 明文存用户 ID', 'JWT Token', 'URL 参数传密码', '前端 localStorage 存密码'], answer: [1], explanation: 'JWT 自带签名与过期机制，适合无状态鉴权；明文存 ID、URL 传密码、前端存密码都不安全。' },
            { type: 'multi', stem: '设计一个 REST 接口时，通常需要考虑哪些？', options: ['资源命名', '状态码', '鉴权', '分页'], answer: [0, 1, 2, 3], explanation: '资源化的命名、正确的状态码、鉴权与分页，是接口可用与可维护的基础。' },
            {
              type: 'short', stem: '简述同步与异步接口的区别，以及异步适合什么场景。',
              points: [
                { text: '说明同步会阻塞等待，异步在等待时可切换任务', score: 40, keywords: ['阻塞', '非阻塞', '等待', '并发', '切换'] },
                { text: '说明异步适合 IO 密集、高并发场景', score: 35, keywords: ['io', 'i/o', '网络', '数据库', '高并发', '外部服务'] },
                { text: '说明同步实现更简单，适合 CPU 密集或逻辑简单场景', score: 25, keywords: ['cpu', '简单', '计算', '同步'] }
              ],
              reference: '同步接口在等待 IO 时会占住线程；异步在等待时让出执行权，可以同时处理更多请求。异步适合网络请求、数据库查询等 IO 密集场景；CPU 密集或逻辑简单时，同步反而更简单可靠。'
            }
          ]
        },
        {
          name: '阶段二 · 大模型应用开发', goal: '掌握 RAG 与对话应用的完整链路',
          courses: ['RAG 基础：检索增强生成', '向量数据库与 Embedding', '流式输出与前端体验', '对话状态与上下文管理'],
          questions: [
            { type: 'judge', stem: 'RAG 可以完全消除大模型的幻觉。', options: ['正确', '错误'], answer: [1], explanation: 'RAG 能提供事实依据、显著降低幻觉，但检索不到、检索错误或模型误读时仍可能出错。' },
            { type: 'single', stem: 'RAG 中向量数据库的主要作用是？', options: ['存储原始文档', '按语义相似度检索相关片段', '训练模型', '压缩模型体积'], answer: [1], explanation: '向量数据库存储文本的向量表示，用相似度检索召回与问题相关的片段。' },
            { type: 'multi', stem: '做 RAG 时，影响效果的关键环节包括？', options: ['文档切分策略', 'Embedding 模型选择', '召回数量与重排', '提示词组织'], answer: [0, 1, 2, 3], explanation: '切分决定检索粒度，Embedding 决定语义匹配质量，召回与重排决定送入模型的内容，提示词决定模型如何使用这些内容。' },
            {
              type: 'short', stem: '什么是 RAG？它相比直接微调模型有哪些优势？',
              points: [
                { text: '说明 RAG 是检索外部知识后交给模型生成', score: 35, keywords: ['检索', '外部知识', '知识库', '增强', '召回'] },
                { text: '说明优势：知识可实时更新、无需重新训练', score: 40, keywords: ['无需训练', '更新', '实时', '成本', '可控', '热更新'] },
                { text: '说明相比微调更轻、更易维护与追溯', score: 25, keywords: ['微调', '训练', '维护', '追溯', '来源'] }
              ],
              reference: 'RAG（检索增强生成）先从知识库检索相关片段，再把这些片段放进提示词让模型生成答案。相比微调，它能实时更新知识、无需重新训练、成本更低，并且可以追溯到具体来源。'
            }
          ]
        },
        {
          name: '阶段三 · 工程化与上线', goal: '让应用稳定、可观测、成本可控',
          courses: ['日志、监控与告警', '模型调用成本控制', '评测集与回归测试', '部署、扩缩容与灰度'],
          questions: [
            { type: 'judge', stem: '大模型应用的线上成本只需要在项目上线前评估一次。', options: ['正确', '错误'], answer: [1], explanation: '用户量、调用频率、提示词长度都会变化，成本需要持续监控与优化。' },
            { type: 'single', stem: '以下哪项最适合用来监控线上模型调用成本？', options: ['只记录总请求数', '按模型与 token 用量记录每次调用', '看服务器 CPU', '看用户数'], answer: [1], explanation: '按模型、token 用量与调用场景拆分记录，才能定位成本来源并优化。' },
            { type: 'multi', stem: '上线前，大模型应用的评测通常包括？', options: ['准确率 / 命中率', '响应时延', '成本', '失败与降级表现'], answer: [0, 1, 2, 3], explanation: '效果、时延、成本与异常表现共同决定线上体验，缺一不可。' },
            {
              type: 'short', stem: '为什么大模型应用需要做“评测集 + 回归测试”？',
              points: [
                { text: '说明评测集用于衡量效果与建立基线', score: 40, keywords: ['评测', '基准', '效果', '基线', '指标'] },
                { text: '说明回归测试防止改动导致效果退化', score: 35, keywords: ['退化', '回归', '稳定', '对比', '下降'] },
                { text: '说明它能支撑持续迭代与上线决策', score: 25, keywords: ['迭代', '上线', '优化', '决策', '发布'] }
              ],
              reference: '模型、提示词、检索策略经常调整，没有固定评测集就无法判断改动是变好还是变坏。回归测试用同一批样本对比新旧版本，防止效果退化，并为上线决策提供依据。'
            }
          ]
        }
      ]
    },

    java: {
      name: 'Java 开发学习路线',
      desc: '3 个阶段，从语言基础到 Spring Boot 数据层，再到微服务治理，适合后端方向系统学习。',
      stages: [
        {
          name: '阶段一 · Java 语言基础', goal: '打牢语法、集合与面向对象基础',
          courses: ['JDK 与开发环境', '面向对象核心：封装、继承、多态', '集合框架与泛型', '异常处理与 IO'],
          questions: [
            { type: 'judge', stem: 'Java 中 String 是不可变对象。', options: ['正确', '错误'], answer: [0], explanation: 'String 内部字符数组被 final 修饰且不对外暴露，任何“修改”都会产生新对象。' },
            { type: 'single', stem: '以下哪个集合允许重复元素且保持插入顺序？', options: ['HashSet', 'ArrayList', 'HashMap', 'TreeSet'], answer: [1], explanation: 'ArrayList 是有序列表，允许重复；HashSet / TreeSet 不允许重复，HashMap 存键值对。' },
            { type: 'multi', stem: '以下哪些属于 Java 面向对象的特征？', options: ['封装', '继承', '多态', '编译'], answer: [0, 1, 2], explanation: '封装、继承、多态是面向对象的三大特征；编译是语言执行过程，不是面向对象特征。' },
            {
              type: 'short', stem: '简述 Java 中 == 与 equals 的区别。',
              points: [
                { text: '说明 == 比较的是引用 / 内存地址（基本类型比较值）', score: 40, keywords: ['引用', '地址', '对象', '基本类型', '值'] },
                { text: '说明 equals 默认比较引用，通常被重写为比较内容', score: 40, keywords: ['内容', '值', 'equals', '重写'] },
                { text: '能举例说明 String 的 equals 比较内容', score: 20, keywords: ['字符串', 'string', '内容相同'] }
              ],
              reference: '== 对基本类型比较值，对对象比较引用地址；equals 是 Object 的方法，默认也是比较引用，但 String、Integer 等类重写后比较的是内容。'
            }
          ]
        },
        {
          name: '阶段二 · Spring Boot 与数据层', goal: '独立完成业务接口与数据访问',
          courses: ['Spring Boot 起步', 'REST 接口设计规范', 'MyBatis 与事务', 'Redis 缓存实战'],
          questions: [
            { type: 'judge', stem: '在 Spring Boot 中，@RestController 返回的对象默认会被序列化为 JSON。', options: ['正确', '错误'], answer: [0], explanation: '@RestController 组合了 @ResponseBody，返回值会经消息转换器序列化为 JSON。' },
            { type: 'single', stem: '以下哪个注解通常用于声明一个事务方法？', options: ['@Async', '@Transactional', '@Bean', '@Scheduled'], answer: [1], explanation: '@Transactional 用于声明事务边界，配合 Spring 的代理机制生效。' },
            { type: 'multi', stem: '使用 Redis 做缓存时，通常需要处理哪些问题？', options: ['缓存穿透', '缓存击穿', '缓存雪崩', '数据一致性'], answer: [0, 1, 2, 3], explanation: '穿透、击穿、雪崩是三类典型缓存问题，数据一致性则决定缓存与数据库如何同步。' },
            {
              type: 'short', stem: '简述数据库事务的 ACID 分别指什么。',
              points: [
                { text: '原子性：事务内操作要么全部成功，要么全部回滚', score: 25, keywords: ['原子', '全部', '回滚'] },
                { text: '一致性：事务前后数据满足约束与业务规则', score: 25, keywords: ['一致', '约束', '合法'] },
                { text: '隔离性：并发事务之间互不干扰', score: 25, keywords: ['隔离', '并发', '事务之间', '干扰'] },
                { text: '持久性：提交后的数据被永久保存', score: 25, keywords: ['持久', '提交', '保存', '永久'] }
              ],
              reference: 'ACID 指原子性（Atomicity）、一致性（Consistency）、隔离性（Isolation）、持久性（Durability）：事务要么全部成功要么全部回滚；前后数据满足约束；并发事务互不干扰；提交后永久保存。'
            }
          ]
        },
        {
          name: '阶段三 · 微服务与工程实践', goal: '理解服务拆分与分布式治理',
          courses: ['微服务拆分原则', '注册中心与配置中心', '消息队列与最终一致性', '链路追踪与可观测性'],
          questions: [
            { type: 'judge', stem: '服务拆分越细，系统的可维护性一定越好。', options: ['正确', '错误'], answer: [1], explanation: '拆得过细会带来分布式事务、网络调用、运维复杂度的上升。拆分应围绕业务边界，而不是越细越好。' },
            { type: 'single', stem: '微服务架构中，服务注册与发现通常由什么组件承担？', options: ['消息队列', '注册中心', '网关', '数据库'], answer: [1], explanation: '注册中心（如 Nacos、Eureka）负责服务实例的注册与发现；网关负责流量入口与路由。' },
            { type: 'multi', stem: '微服务架构带来的典型挑战包括？', options: ['分布式事务', '链路追踪', '服务治理', '单机性能'], answer: [0, 1, 2], explanation: '分布式事务、链路追踪与服务治理是微服务新增的复杂度；单机性能并不是其特有挑战。' },
            {
              type: 'short', stem: '简述消息队列在微服务中的典型用途。',
              points: [
                { text: '解耦：服务之间通过消息异步通信', score: 35, keywords: ['解耦', '异步', '削峰', '缓冲'] },
                { text: '可靠投递、重试与最终一致性', score: 35, keywords: ['可靠', '重试', '最终一致', '补偿', '投递'] },
                { text: '能举出典型场景，如订单、通知、日志', score: 30, keywords: ['订单', '通知', '日志', '削峰', '异步', '事件'] }
              ],
              reference: '消息队列常用于服务解耦、异步处理与流量削峰，并通过重试、补偿实现最终一致性。典型场景包括下单后异步通知、日志采集、事件驱动等。'
            }
          ]
        }
      ]
    }
  };

  const DIMENSIONS = {
    agent: ['大模型原理', 'Prompt 工程', '工具调用', '系统设计', '表达与逻辑'],
    'ai-full': ['Python 后端', 'RAG 与检索', '工程化', '系统设计', '表达与逻辑'],
    java: ['Java 基础', '框架应用', '数据库', '系统设计', '表达与逻辑']
  };

  const IV_DIFFS = [
    { id: 'easy', label: '简单', enabled: true, color: '#0B8A4B', bg: '#ECFDF3', desc: '围绕基础概念与课程要点提问，确认你是否真正理解。', tags: ['基础概念', '直接提问', '确认理解'] },
    { id: 'medium', label: '中等', enabled: true, color: '#B54708', bg: '#FFFAEB', desc: '结合课程场景追问做法与原因，考察设计与取舍。', tags: ['场景应用', '做法与原因', '设计取舍'] },
    { id: 'hard', label: '困难', enabled: true, color: '#B42318', bg: '#FEECEB', desc: '深入真实项目与边界条件，追问兜底与效果衡量。', tags: ['项目落地', '失败兜底', '效果衡量'] }
  ];

  const TOPICS = {
    agent: [
      {
        topic: '大模型基础与 Prompt 工程',
        opening: '请先简单介绍一下，你理解的“大模型是怎么工作的”？',
        followups: [
          '你提到模型会按概率生成内容。如果线上输出不稳定，你会从哪些方面排查？',
          '在真实业务里，你会怎么设计一套提示词，让输出稳定、可解析？',
          '如果让你评估一套提示词的好坏，你会看哪些指标？'
        ],
        dimensions: ['大模型原理', 'Prompt 工程']
      },
      {
        topic: 'Agent 工具调用设计',
        opening: '如果让你设计一个能查天气、订机票的 Agent，你会怎么组织它的工具？',
        followups: [
          '工具描述写得好不好，会怎样影响 Agent 的表现？',
          '如果某个工具调用失败了，你会怎么设计重试和兜底？',
          '多个工具返回的结果互相冲突时，你会怎么处理？'
        ],
        dimensions: ['工具调用', '系统设计']
      },
      {
        topic: '多智能体系统设计',
        opening: '多智能体协作中，你会如何避免状态在 Agent 之间变得混乱？',
        followups: [
          '你会怎么划分多个 Agent 的职责和边界？',
          '如果某个 Agent 一直失败，系统应该怎么处理？',
          '上线前，你会怎么评估这套多智能体系统？'
        ],
        dimensions: ['系统设计', '表达与逻辑']
      }
    ],
    'ai-full': [
      {
        topic: '后端接口与鉴权',
        opening: '请介绍一个你写过的后端接口，以及你是怎么设计鉴权的？',
        followups: [
          '如果接口需要支持高并发，你会从哪些层面优化？',
          '鉴权失败和权限不足，你会怎么设计返回与日志？',
          '你会怎么防止接口被恶意刷调用？'
        ],
        dimensions: ['Python 后端', '系统设计']
      },
      {
        topic: 'RAG 应用设计',
        opening: '如果要做一套企业知识库问答，你会怎么设计 RAG 链路？',
        followups: [
          '文档切分粒度会怎样影响最终效果？',
          '检索结果不相关时，你会怎么定位和优化？',
          '你会怎么评估这套问答系统的准确率？'
        ],
        dimensions: ['RAG 与检索', '系统设计']
      },
      {
        topic: '上线与成本',
        opening: '一个大模型应用上线后，你会重点关注哪些指标？',
        followups: [
          '如果发现成本突然上涨，你会怎么排查？',
          '模型服务不稳定时，你的降级方案是什么？',
          '你会怎么设计一次安全的上线或灰度？'
        ],
        dimensions: ['工程化', '表达与逻辑']
      }
    ],
    java: [
      {
        topic: 'Java 基础与集合',
        opening: '请说说你对 Java 集合框架的理解，以及你在项目里怎么选型？',
        followups: [
          'HashMap 在什么情况下会退化成链表？你会怎么避免？',
          'ArrayList 和 LinkedList 你会怎么选？',
          '你遇到过并发集合相关的问题吗？怎么解决的？'
        ],
        dimensions: ['Java 基础', '表达与逻辑']
      },
      {
        topic: 'Spring Boot 与事务',
        opening: '在 Spring Boot 项目里，你是如何设计一个业务接口的？',
        followups: [
          '@Transactional 在什么情况下会失效？',
          '事务回滚你会怎么设计？异常怎么处理？',
          '缓存和数据库不一致时，你会怎么保证最终一致？'
        ],
        dimensions: ['框架应用', '数据库']
      },
      {
        topic: '微服务治理',
        opening: '如果要把一个单体应用拆成微服务，你会怎么拆？',
        followups: [
          '拆分后分布式事务你会怎么处理？',
          '服务之间调用失败，你会怎么设计熔断和降级？',
          '线上排查问题，你会依赖哪些可观测性手段？'
        ],
        dimensions: ['系统设计', '数据库']
      }
    ]
  };

  /* ---------------- 构建内容 ---------------- */
  function buildRoutes() {
    return SUBJECTS.map(function (s) {
      const def = ROUTE_DEFS[s.id];
      const stages = def.stages.map(function (st, si) {
        const stageId = s.id + '-s' + (si + 1);
        return {
          id: stageId,
          name: st.name,
          goal: st.goal,
          courses: st.courses.map(function (title, ci) {
            return {
              id: stageId + '-c' + (ci + 1),
              title: title,
              minutes: 32 + ((ci * 9 + si * 7) % 28),
              url: 'https://www.bilibili.com/'
            };
          }),
          questions: st.questions.map(function (q, qi) {
            const item = Object.assign({}, q);
            item.id = stageId + '-q' + (qi + 1);
            item.stageId = stageId;
            return item;
          })
        };
      });
      return {
        id: 'route-' + s.id,
        subjectId: s.id,
        name: def.name,
        desc: def.desc,
        stages: stages
      };
    });
  }

  /* ---------------- B 端模拟数据 ---------------- */
  const MODULES = ['学科管理', '课程库', '刷题题库', '面试设置', '学习路线', '学员管理', '系统用户', '角色管理', '操作日志', '数据看板'];

  const ADMINS = [
    { id: 'a1', name: '周老师', account: 'zhou', role: '超级管理员', status: '启用', lastLogin: '2026-10-30 09:12' },
    { id: 'a2', name: '陈内容', account: 'chen', role: '内容运营', status: '启用', lastLogin: '2026-10-30 08:47' },
    { id: 'a3', name: '李运营', account: 'li', role: '学员运营', status: '启用', lastLogin: '2026-10-29 18:20' },
    { id: 'a4', name: '王老师', account: 'wang', role: '内容运营', status: '停用', lastLogin: '2026-10-11 15:06' }
  ];

  const ROLES = [
    { id: 'r1', name: '超级管理员', desc: '拥有全部模块权限', modules: MODULES.slice() },
    { id: 'r2', name: '内容运营', desc: '维护学科、课程、题库、路线与面试设置', modules: ['学科管理', '课程库', '刷题题库', '面试设置', '学习路线', '数据看板'] },
    { id: 'r3', name: '学员运营', desc: '管理学员、查看学习与面试数据', modules: ['学员管理', '数据看板'] }
  ];

  const STUDENT_NAMES = ['小林', '阿哲', '陈晨', '苏苏', '老周', '小满', '李想', '王可', '赵一鸣', '周舟', '孙悦', '吴迪', '郑好', '冯远', '蒋欣', '韩梅', '杨帆', '朱迪', '秦朗', '许知', '何一', '高远', '林深', '罗宾'];
  const ROUTE_KEYS = ['route-agent', 'route-ai-full', 'route-java'];

  function buildStudents() {
    return STUDENT_NAMES.map(function (name, i) {
      const routeId = ROUTE_KEYS[i % 3];
      const pct = [100, 82, 64, 48, 36, 20, 8, 0][i % 8];
      return {
        id: 'stu' + (i + 1),
        name: name,
        phone: '13' + String(800000000 + i * 173917).slice(0, 9),
        routeId: routeId,
        progress: pct,
        status: (i === 5 || i === 17) ? '禁用' : (i === 21 ? '注销' : '正常'),
        joinedAt: '2026-0' + (i % 9 + 1) + '-' + String(10 + (i % 18)).padStart(2, '0'),
        lastActive: i % 4 === 0 ? '今天' : ('10-' + String(20 + (i % 9)))
      };
    });
  }

  function buildLogs() {
    const acts = [
      ['内容运营', '刷题题库', '导入题目', 'Agent 开发 · 阶段三（42 条）'],
      ['内容运营', '课程库', '新增课程', '多智能体协作模式'],
      ['学员运营', '学员管理', '禁用学员', '阿哲（138****3317）'],
      ['超级管理员', '角色管理', '修改权限', '内容运营：新增「数据看板」'],
      ['内容运营', '面试设置', '编辑难度描述', '困难'],
      ['学员运营', '学员管理', '重置密码', '陈晨（138****0291）'],
      ['内容运营', '学习路线', '调整阶段顺序', 'Java 开发学习路线'],
      ['超级管理员', '系统用户', '新增用户', '王老师（内容运营）'],
      ['内容运营', '学科管理', '编辑学科', 'AI 全栈开发'],
      ['学员运营', '学员管理', '修改主路线', '苏苏 → Agent 开发'],
      ['内容运营', '刷题题库', '停用题目', 'Agent 开发 · 阶段二 · q3'],
      ['超级管理员', '操作日志', '导出日志', '2026-10 全量'],
      ['内容运营', '课程库', '编辑课程', '工具调用与函数签名设计'],
      ['学员运营', '学员管理', '注销账号', '罗宾（138****1186）'],
      ['内容运营', '面试设置', '调整考察维度', 'Agent 开发'],
      ['超级管理员', '角色管理', '查看权限', '学员运营'],
      ['内容运营', '刷题题库', '导入题目', 'Java 开发 · 阶段一（36 条）'],
      ['学员运营', '学员管理', '启用学员', '老周（138****2834）']
    ];
    return acts.map(function (a, i) {
      return {
        id: 'log' + (i + 1),
        who: a[0], module: a[1], action: a[2], target: a[3],
        time: '2026-10-' + String(30 - Math.floor(i / 2)).padStart(2, '0') + ' ' + String(9 + (i % 9)).padStart(2, '0') + ':' + String((i * 7) % 60).padStart(2, '0')
      };
    });
  }

  function defaultIvcfg() {
    return { rounds: 4, diffs: JSON.parse(JSON.stringify(IV_DIFFS)) };
  }

  function seedContent() {
    return {
      subjects: JSON.parse(JSON.stringify(SUBJECTS)),
      routes: buildRoutes(),
      dimensions: DIMENSIONS,
      topics: TOPICS,
      ivcfg: defaultIvcfg(),
      students: buildStudents(),
      admins: ADMINS,
      roles: ROLES,
      logs: buildLogs()
    };
  }

  let contentCache = null;

  function content() {
    if (contentCache) return contentCache;
    let c = Store.get('content', null);
    let dirty = false;
    if (!c || !c.routes) {
      c = seedContent();
      dirty = true;
    }
    // 兼容旧数据：面试题改为大模型生成后，原「面试题库」模块更名为「面试设置」
    (c.roles || []).forEach(function (r) {
      var i = r.modules ? r.modules.indexOf('面试题库') : -1;
      if (i >= 0) { r.modules[i] = '面试设置'; dirty = true; }
    });
    (c.logs || []).forEach(function (l) {
      if (l.module === '面试题库') { l.module = '面试设置'; dirty = true; }
    });
    if (!c.ivcfg || !c.ivcfg.diffs || !c.ivcfg.diffs.length) {
      c.ivcfg = defaultIvcfg();
      dirty = true;
    }
    if (dirty) Store.set('content', c);
    contentCache = c;
    return c;
  }

  function saveContent(c) {
    contentCache = c;
    Store.set('content', c);
  }

  /* ---------------- C 端演示状态 ---------------- */
  function seedUserState() {
    if (Store.get('seeded')) return;
    const c = content();
    const agentRoute = c.routes.find(function (r) { return r.subjectId === 'agent'; });
    const s1 = agentRoute.stages[0];
    const s2 = agentRoute.stages[1];

    const progress = {};
    s1.courses.forEach(function (co) {
      progress[co.id] = { startedAt: '2026-10-13 20:10', completedAt: '2026-10-16 21:30' };
    });
    s2.courses.slice(0, 2).forEach(function (co) {
      progress[co.id] = { startedAt: '2026-10-24 19:40', completedAt: '2026-10-26 21:05' };
    });

    const practices = [
      { id: 'p1', stageId: s1.id, type: 'stage', total: 4, correct: 4, accuracy: 100, at: '2026-10-17 20:12' },
      { id: 'p2', stageId: s1.id, type: 'stage', total: 4, correct: 3, accuracy: 75, at: '2026-10-22 21:03' },
      { id: 'p3', stageId: s2.id, type: 'stage', total: 4, correct: 2, accuracy: 50, at: '2026-10-26 20:18' },
      { id: 'p4', stageId: s2.id, type: 'stage', total: 4, correct: 3, accuracy: 75, at: '2026-10-28 21:40' }
    ];

    const wrong = {};
    [s2.questions[1].id, s2.questions[2].id, s1.questions[3].id].forEach(function (qid) {
      wrong[qid] = { addedAt: '2026-10-28 21:40', active: true };
    });

    const d1 = c.dimensions.agent;
    const iv1 = {
      id: 'iv-seed-1', subjectId: 'agent', topic: '大模型基础与 Prompt 工程',
      status: 'completed', startedAt: '2026-10-15 20:10', finishedAt: '2026-10-15 20:26',
      messages: [
        { role: 'ai', text: c.topics.agent[0].opening },
        { role: 'me', text: '大模型本质上是基于海量语料训练出来的概率模型，它根据上文预测下一个 token，所以能生成连贯的内容，但并不真正理解事实。' },
        { role: 'ai', text: c.topics.agent[0].followups[0] }
      ],
      report: {
        overall: 62, status: 'completed',
        dims: d1.map(function (n, i) { return { name: n, score: [58, 60, 55, 68, 69][i] }; }),
        summary: '整体基础不错，能够说清大模型的基本原理，但在提示词的结构化设计与工具调用场景上还需要更具体的实践。',
        suggestions: ['用结构化模板重写 3 个业务提示词，并对比输出稳定性', '补充 1 个工具调用的完整示例，说明参数设计与失败处理', '面试回答时多用“背景-做法-结果”的结构，减少铺垫']
      }
    };
    const iv2 = {
      id: 'iv-seed-2', subjectId: 'agent', topic: 'Agent 工具调用设计',
      status: 'completed', startedAt: '2026-10-24 19:30', finishedAt: '2026-10-24 19:52',
      messages: [
        { role: 'ai', text: c.topics.agent[1].opening },
        { role: 'me', text: '我会把工具按领域拆开：天气查询、航班查询、下单。每个工具只做一件事，参数尽量少，返回结构化结果，并在描述里写清楚适用边界。' },
        { role: 'ai', text: c.topics.agent[1].followups[0] }
      ],
      report: {
        overall: 74, status: 'completed',
        dims: d1.map(function (n, i) { return { name: n, score: [72, 74, 70, 76, 78][i] }; }),
        summary: '工具拆分的思路清晰，能考虑到参数设计与失败兜底；对多工具结果冲突的处理还可以再具体一些。',
        suggestions: ['补充工具失败后的重试与降级策略', '准备一个多工具结果冲突的处理案例', '继续练习用更短的结构化表达回答问题']
      }
    };

    Store.set('user', {
      phone: '13800138000', nickname: '小林', avatar: '',
      mainRoute: 'route-agent', joinedAt: '2026-10-12', status: 'normal'
    });
    Store.set('progress', progress);
    Store.set('practices', practices);
    Store.set('wrong', wrong);
    Store.set('interviews', [iv2, iv1]);
    Store.set('seeded', true);
  }

  /* ---------------- 查询辅助 ---------------- */
  function findRoute(routeId) {
    return content().routes.find(function (r) { return r.id === routeId; });
  }
  function findStage(stageId) {
    for (const r of content().routes) {
      const s = r.stages.find(function (x) { return x.id === stageId; });
      if (s) return { stage: s, route: r };
    }
    return null;
  }
  function findCourse(courseId) {
    for (const r of content().routes) {
      for (const s of r.stages) {
        const c = s.courses.find(function (x) { return x.id === courseId; });
        if (c) return { course: c, stage: s, route: r };
      }
    }
    return null;
  }
  function findQuestion(qid) {
    for (const r of content().routes) {
      for (const s of r.stages) {
        const q = s.questions.find(function (x) { return x.id === qid; });
        if (q) return { question: q, stage: s, route: r };
      }
    }
    return null;
  }
  function allCourses() {
    const out = [];
    content().routes.forEach(function (r) { r.stages.forEach(function (s) { s.courses.forEach(function (c) { out.push({ course: c, stage: s, route: r }); }); }); });
    return out;
  }
  function allQuestions() {
    const out = [];
    content().routes.forEach(function (r) { r.stages.forEach(function (s) { s.questions.forEach(function (q) { out.push({ question: q, stage: s, route: r }); }); }); });
    return out;
  }
  function subjectOf(routeId) {
    const r = findRoute(routeId);
    if (!r) return null;
    return content().subjects.find(function (s) { return s.id === r.subjectId; });
  }

  window.Store = Store;
  window.DATA = {
    content: content,
    saveContent: saveContent,
    seedContent: seedContent,
    seedUserState: seedUserState,
    findRoute: findRoute,
    findStage: findStage,
    findCourse: findCourse,
    findQuestion: findQuestion,
    allCourses: allCourses,
    allQuestions: allQuestions,
    subjectOf: subjectOf,
    MODULES: MODULES
  };
})();
