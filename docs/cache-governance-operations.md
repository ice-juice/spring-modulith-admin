# P08 缓存运行说明

完整设计见[P08](proposals/P08-cache-governance-and-cluster.md)，交付与实际验证见[进度](cache-governance-progress.md)。本文件随阶段更新，以下配置不能等同于 Redis/多实例已经验收。

## 当前存储与边界

- `sma.cache.provider=local`（默认）：Caffeine 单进程缓存。`none`：直接回源，记录 bypass，不存储数据。
- `SMA_CACHE_PROVIDER=none` 可用于下次启动禁用普通数据缓存。恢复 local 需重启；目前不提供管理页热切换。
- `redis` 已接入显式运行配置；必须提供部署专属 namespace、可用连接和正数 Redis maxmemory。`deployment-mode=multi` 仍明确拒绝启动，直到完整应用集群门禁通过；Redis 数据共享不能替代 WS/文件/后台任务的集群改造。
- 接入区域 `config.parameters`、`config.dictionary-options`、`config.preferences`。认证/登录参数（`auth.*`、`login.*`）直接查询数据库；标记 secret 的参数不入缓存。字典按租户/类型/语言隔离，偏好只缓存当前人类用户的原始记录；调用者默认语言、时区和租户默认主题每次重新合成。跨用户内部查询保持直接查询。
- 缓存键从可信租户/portal/用户生成。`TENANT_USER` 等用户范围要求人类身份；键中的业务标识仅保存摘要。
- 原始值和调用 fallback 分离；null 负缓存默认 15 秒，普通条目最大 300 秒；默认抖动只缩短 TTL，命中不续期。
- 缓存 DTO 使用独立 JSON 快照，不与调用方共享可变对象。超过单条/区域/全局预算时返回回源结果并跳过存储。
- 有真实数据库事务时绕过读写缓存，避免事务旧快照或未提交内容污染；参数普通读取采用 SUPPORTS，不人为开启只读事务。
- 写事务同时更新 `common_cache_revision`，提交后失效，回滚不发布；清理变化代次，在途旧加载不能重新填入。S2c 已实现持久版本与逐节点补偿，版本校验失败绕过缓存；Redis 每次访问还核对已知数据库版本，避免旧 Redis 快照复活。
- Local 的节点总预算默认 64 MiB，区域默认10000条/16 MiB，单条载荷最大256 KiB；估算不等于精确 JVM heap。Redis 的同一租户/区域预算由所有客户端共享、原子执行，最大不超过注册策略和10000条/64MiB操作上限，`max-bytes` 同时作为该区域预算上限；Redis 实例总物理内存由服务端 maxmemory 限制（启动时检查，Cluster 检查所有发现的节点）。Local 的节点聚合约束比单租户约束更保守，不应把多租户 Redis 用量按单节点 Local 预算理解。
- 元数据作用域最多 10000 个，空闲 1 小时淘汰；统计窗口随元数据生命周期，重建代次不复用旧数据。快照不返回缓存内容。

IAM 已接入部门展示、岗位选项、用户展示、菜单及导航元数据（`iam.department-display`、`iam.post-options`、`iam.user-display`、`iam.menu-metadata`、`iam.navigation-metadata`）。部门缓存键包含当前可信数据范围摘要，仍由 DataScoped 回源；菜单、账号、凭据、角色授权的权威判断不走普通缓存。用户展示只存id/name，按租户/portal隔离；禁用账号在历史姓名显示中的既有语义保留。个人资料改名、bootstrap和菜单同步也登记失效。全部区域在现有缓存目录可见，IAM三种provider专项已通过，S3整体标准回归状态见进度。

部署覆盖示例（必须显式加载，不原地修改共享默认配置）：

```yaml
sma:
  cache:
    provider: local
    namespace: sma-prod
    deployment-mode: single
    max-bytes: 67108864
    max-scopes: 10000
    ttl-jitter: 0.1
    defaults:
      ttl: 120s
      negative-ttl: 10s
    regions:
      "[config.parameters]":
        ttl: 60s
        negative-ttl: 10s
        max-entries: 5000
        max-bytes: 8388608
        max-entry-bytes: 65536
```

带点区域 ID 用方括号保留为 Map key。策略顺序为代码声明 → defaults → 区域覆盖；区域覆盖可以高于较低的全局默认，但不能突破代码声明上限。全局默认和最终区域策略均须合法，未知区域或超上限拒绝启动；缩短 TTL 时也需保证 negative-ttl 不超过正 TTL。这些部署配置在启动时绑定。

现有 `/api/monitor/caches` 的形状与查看/清理权限保留，参数区域通过旧 SPI 适配，其他登记的 DATA 区域自动进入目录；清理仍仅当前租户（偏好区域清理覆盖该租户所有用户）。清理在服务层同时要求 `monitor:cache:clear` 和区域声明的 `managePermission`；旧批量清理仅处理登记、具备CLEAR能力且操作者获准的DATA区域。未登记旧SPI不能绕过治理执行清理。策略、持久任务和节点确认接口已交付；分组界面尚待S4d，不能通过旧入口管理保护状态。

## 治理目录接口（S4a已验证）

- `GET /api/monitor/caches/directory`：当前租户目录，要求 `monitor:cache:view`。可选 `owner`、`group`、`scenario`、`kind`、`provider`、`status`、`keyword` 按交集筛选；keyword匹配名称/区域ID，不搜索缓存内容。
- `GET /api/monitor/caches/directory/{id}`：区域详情，包含声明/当前允许操作、隔离维度、格式版本、失效来源、有效部署策略及逐字段来源。只有当前实际支持的操作才进入allowedOperations；最终执行时仍复验权限。
- 状态区分ACTIVE、PAUSED、DISABLED、UNAVAILABLE、PROTECTED与ADAPTER_REQUIRED；保护状态不统计内容、不提供清理。RUNTIME/RESOURCE已提供所属模块的只读观测；可执行适配的阶段验收见S5c，不能把缺适配器视为健康或可清空。
- observation中processId只标识一次进程生命周期，sampledAt为采样时刻；Redis条目按共享租户区域统计，Local按本进程租户区域统计，命中/未命中计数均属于本进程观察窗口，不能把共享条目跨节点求和。
- policy区分DECLARED、DEPLOYMENT_DEFAULT、DEPLOYMENT_REGION、TENANT_RUNTIME、DEPLOYMENT_CAP和DEPLOYMENT_PROVIDER_LIMIT；providerByteLimit和作用范围说明Redis区域有效预算与Local节点聚合预算。无法确认控制状态时来源UNAVAILABLE、effective及paused为未知；control.confirmed仅代表本进程观察，不能代表全节点。字节估算不是精确物理内存。接口不返回缓存值、原始key、namespace、端点或凭据。provider仍按部署配置重启切换。

当前前端仍为原缓存表格；上述详情和筛选已做后端HTTP与拒绝路径验证，四页签和真实浏览器验收另按S4d完成。

## 运行策略与审计接口（S4b已验证）

Common V5在原区域状态行增加controlVersion、statsEpoch、paused和运行策略，单次查询读取同一已提交快照。策略编辑/暂停/恢复使用独立管理版本防并发覆盖，并增加数据revision以隔离旧回填；统计重置只切换统计窗口，保留缓存数据、TTL和Redis Hash计数。原业务失效仍只增加数据revision，事务回滚不发布任何控制变化。

Local保留节点与区域聚合硬上限，同时执行当前租户区域的更低运行配额；Redis按共享租户区域执行运行配额，落后版本不能借新generation回填。各节点用独立计数窗口，旧在途请求不能污染重置后的窗口。已开始的业务回源允许完成，但暂停/策略切换会阻止旧数据写回；None始终不存储。权限/安全判定不受这些开关控制。

通过公开`CacheGovernance`或以下HTTP入口操作，不能直接修改内部表。路径前缀均为`/api/monitor/caches`：

| 方法与路径 | 权限 | 行为 |
| --- | --- | --- |
| GET `/{id}/policy` | monitor:cache:view | 当前持久版本、运行覆盖、部署上限与有效策略 |
| PUT `/{id}/policy`、POST `/{id}/policy/reset` | monitor:cache:policy | 收紧策略或恢复部署策略 |
| POST `/{id}/pause`、`/{id}/resume` | monitor:cache:pause | 禁读写或恢复，均推进代次 |
| POST `/{id}/statistics/reset` | monitor:cache:reset-stats | 只推进统计窗口 |
| GET `/{id}/history?limit=50` | monitor:cache:view＋区域管理权 | 当前租户区域审计，最多100条 |

写操作同时要求区域managePermission及对应能力。内置Config/IAM区域使用monitor:cache:view；二开区域保留自己声明的业务管理权。新增目录权限不自动授予已有角色。

写操作均要求`expectedVersion`（GET返回的version，Long按字符串传输）和非空`reason`（最多256字符）。PUT还要求ttlMillis、negativeTtlMillis、maxEntries、maxBytes、maxEntryBytes；数值只能收紧部署有效上限。示例：`{"expectedVersion":"0","reason":"降低过期展示时间","ttlMillis":30000,"negativeTtlMillis":5000,"maxEntries":100,"maxBytes":1048576,"maxEntryBytes":65536}`。版本过期返回真实HTTP409，缺失版本返回400；刷新状态后由操作者决定，不自动覆盖重试。

Common V6审计记录精确前后状态，与控制变更同事务；写审计失败会回滚控制状态。运行治理需要所有节点升级到支持该状态的版本；回退不理解控制状态的旧程序前，显式取消暂停/运行覆盖并换新namespace，不能假定旧程序理解暂停标记。区域统计重置不影响全局健康累计指标。上述接口已完成真实MySQL/HTTP及三后端分阶段验证；四页签仍待S4d。

## 任务受理基础（S4c1已验证）

`common :: cache`新增CacheTasks：preview/submit/get/recent/cancel。预览固定当前可信租户、发起人/portal、区域描述摘要与控制版本，最多128项目标，筛选为ID/owner/group/scenario交集；无权、保护或无能力区域单独列出跳过原因。预览默认5分钟，`sma.cache.tasks.preview-ttl`可设30秒～10分钟；`max-records-per-tenant`默认10000，上限100000。限流先于批量版本查询，且只退役过期未受理预览，不自动删除已受理任务或回执。

提交使用既有RequestExecution与原请求键；需要部署有效的`sma.security.requests.fingerprint-key`，沿用请求治理的密钥保留/轮换规则。目标、策略或部署后端/namespace变化要求重新预览；成功受理的同键重放仍返回原任务。查询只允许查看自己的租户/portal任务元数据；提交/取消另外检查操作和区域管理权。取消不撤销已完成的动作，不删除原回执。

S4c2a已验证后台执行与最新IAM授权：每项先核对租户安全代次/账号/权限/范围/控制版本，权威变更与APPLIED同事务。任务支持部分失败和取消，已完成项不反向撤销；崩溃租约到期接管，旧fence不能覆盖，单项最多3次认领尝试，耗尽明确失败。提交确认丢失先查持久结果，不能复制已完成动作。

`sma.cache.tasks.dispatch.enabled`默认true，默认每轮16租户、每租户4项，租约30秒；`tenants-per-tick`最大32、`items-per-tenant`最大8、`lease`可设30秒～5分钟。使用独立单worker、有界队列和持久租户分页；关闭调度会保留待执行任务。测试profile关闭自动调度并显式验证。QUEUED不是已清理，APPLIED也不代表所有节点确认或空间已回收。节点确认见S4c3a，注册预热见S4c3b，页面待S4d交付。

S4c2b已验证以下任务HTTP接口（统一前缀`/api/monitor/caches/tasks`）：

| 方法与路径 | 用法 |
| --- | --- |
| POST `/preview` | 传action、regions及可选owner/group/scenario和reason，返回固定目标及排除原因 |
| POST `/{id}/submit` | 发送原`Idempotency-Key`请求头，目标只取服务端预览，不接收客户端重写目标 |
| GET `/`、GET `/{id}` | 当前用户/portal的最近任务（默认20、最多100）和完整明细 |
| POST `/{id}/cancel` | 幂等请求取消，已提交效果不撤回 |

预览示例：`{"action":"CLEAR","regions":["config.dictionary-options"],"reason":"业务数据同步后刷新展示"}`。当前action支持CLEAR、PAUSE、RESUME、RESET_STATS及显式启用后的PREWARM（限制见注册预热一节）。所有接口要求view，预览/提交/取消由服务层再检查对应操作权和区域管理权。HTTP审计区分预览、提交、取消；执行结果在持久项中查询，控制变更另有同事务前后审计。

缺请求键/无效输入为400，预览过期或变化为409；对象不可访问沿用现有HTTP200＋非零业务错误码约定。响应丢失先GET已知预览ID/任务ID；若需重放提交必须保留原key，不生成新key猜测重试。原请求仍RUNNING/UNKNOWN时返回原回执状态，不重复执行。任务可为SUCCEEDED、PARTIAL、FAILED或CANCELLED；页面须检查状态和逐项结果，不能仅看HTTP200。

## 节点清单与任务快照（S4c3a1已验证）

节点来源是显式部署清单，不按访问过的租户或缓存推测全集。`sma.cache.nodes.cluster`为部署标识（默认sma），`id`为当前逻辑节点（默认local），`expected`为同一部署的完整逻辑节点列表，最多32个且必须包含自身；`version`为单调清单版本，默认1。每个应用进程生成独立UUID，检查与任务执行复用该标识；它不是登录凭据，也不是分布式主键的worker ID。

宿主内部租约以数据库时钟计30秒，存储在Common的两张全局框架元数据表中；不存业务数据，没有HTTP写入口。用户任务、节点快照仍归可信租户/用户/portal。相同节点的新进程不能抢占活租约，过期接管提升epoch；正常续租不变代次。清单变更必须提升version且等待旧租约全部退出，旧版本即使在全体离线后也不能重新夺回清单。回到以前的节点组合仍须使用更高version，不能把版本号调小。

`GET /api/monitor/caches/tasks/{id}/nodes`返回该任务预览时冻结的清单、进程/epoch及`stateAtCapture`，不是实时健康接口。租约续期不使预览过期；清单版本或进程代次变化会拒绝首次提交。预览没有登记信息时保持UNKNOWN，不在提交时偷偷绑定后来出现的进程。旧V7/V8任务无快照返回null，表示范围未知；旧预览须重新生成，已受理旧任务保留逻辑执行兼容。

每份任务最多512个区域/节点组合，超限须缩小区域范围；每个部署最多保留128个逻辑租约槽位，节点清单适合使用稳定别名。任务校验使用共享清单锁，正常续租同样使用共享路径，只有身份/清单变动取排他锁，避免租户任务阻塞其他实例心跳。

自动心跳和逐节点确认已在S4c3a2验证。不要通过手改框架表或调用internal方法作为运维入口。`deployment-mode=multi`仍被应用门禁拒绝；这部分测试含真实MySQL/Redis和后台线程，不是独立双实例/LB验收。

## 节点确认与在线观察（S4c3a2已验证）

`sma.cache.nodes.enabled`默认true，测试profile关闭。心跳每5秒触发，租约30秒；确认每1秒触发。两者使用独立有界线程和容量1的队列，慢确认不阻塞续租。每轮最多16租户、每租户16请求，租户之间检查2秒预算；失败5秒后重试。待确认积压会延迟报告，不能把数据失效核对窗口等同于确认完成时限。

- GET `/api/monitor/caches/nodes`：当前租约快照、本进程ID、观察器状态和最近心跳。ONLINE仅表示有效租约，不是瞬时网络可达证明；OWNED、CONFLICT、UNAVAILABLE、STALE、DISABLED、STOPPED区分本地观察器状态。
- GET `/api/monitor/caches/tasks/{id}/confirmation`：本人任务的逐区域/节点最低版本、已观察版本、历史确认和当前可用性。两个接口都要求`monitor:cache:view`；无HTTP注册/确认写接口。
- `allTargetsConfirmed`为历史生效证据，`allExpectedProcessesOnline`为当前租约状态。CONFIRMED之后离线或替换不抹去历史证明，但在线汇总为false；较新版本显式标CONFIRMED_NEWER。预览未知进程不能绑定后来进程。
- 旧任务无范围为LEGACY_UNKNOWN，历史APPLIED缺记录为UNRECORDED，证据绑定不符为EVIDENCE_MISMATCH；不能宣称全部成功。`reclamation=NOT_MEASURED`，确认不等于物理内存回收。

确认请求与APPLIED同事务提交；本节点应用状态在确认数据库事务外执行，报告时重新校验当前租约/进程代次和两个版本。Common V10已执行，此后仅追加迁移。

## 注册预热（S4c3b2已验证）

预热通过既有任务接口提交`action=PREWARM`，不另开脚本或SQL入口。`sma.cache.tasks.warmup.enabled`默认false；必须先停止旧任务消费者、升级所有读端/worker到认识PREWARM的同批版本，再显式开启。已有预热历史不能靠关闭开关就安全回退到旧枚举解码器；保留兼容读端和账本。V11追加检查点字段，V1～V10不改写。

- 当前内置加载器仅`config.dictionary-options`，固定枚举zh-CN和en-US。要求`monitor:cache:view`、`monitor:cache:prewarm`、区域管理权及`config:dictionary:view`；分组不授予业务读取权限。None、暂停、私有区域、未注册加载器和关闭开关返回明确拒绝原因。
- 预览冻结加载器版本/权限/预算；业务二开改变分页或键语义须提升加载器版本。无HTTP游标或任意键读取接口，游标只在租户内任务检查点保存。
- 全任务最多1000次已检查点读取、首次执行后120秒；区域声明只能更小。每页最多16项，内置字典每秒20项的平均速率、最多一页突发，页后通过持久下次可认领时间冷却。此速率按区域任务计算，不宣称集群所有任务合计20项/秒；实际回源仍受普通ManagedCache全局/区域并发预算限制。
- 字典预热的每次数据库回源使用3秒只读事务，同一快照最多512字典项；超过上限拒绝主动回源，未完成批次仍受最多3次尝试限制。普通字典options路径保持原语义。加载器必须提供有限I/O超时；宿主在调用之间校验截止时间，不能宣称强制中断任意二开Java代码。
- 每次加载和检查点重新验证权限、租户安全代次、租约/fence、取消及缓存版本。正常分页不消耗崩溃重试预算，成功页把attempts归零。暂停/停用预热不阻止拥有原操作权的发起者取消已有任务；关闭开关后待执行工作明确拒绝，不删除账本。
- `items[].warmup.checkpointedReads`仅统计已提交检查点的成功读取，不代表精确尝试次数、缓存驻留条目或全节点变热。失败/取消的未检查点页可能已产生部分缓存读取，重试可安全重复。`processId`是实际执行进程；同一区域中途换进程明确失败，禁止把多个Local实例累计当作一个已完整预热实例。
- 预热任务的节点确认报告为`NOT_APPLICABLE_PREWARM`；不发布全节点失效确认，也不推进失效版本。正常TTL/淘汰/新写入随时可能使已预热内容失效。

最终验证结果与未验证范围以[专项进度](cache-governance-progress.md)为准。

## 新版缓存管理页面（S4d已验证）

S5b补充运行对象与资源详情：规则运行对象、SQL统计、IP地理库、文件存储和会话/验证码/票据等保护状态统一登记。规则观察仍要求原维护查看权限和ALL数据范围；SQL观察要求原SQL查看权。RESTRICTED表示业务权限/范围不足，READ_ONLY表示只读资源，UNAVAILABLE表示观测失败；计数-1显示未知。文件是持久资产，IP库供在线查询使用，不提供普通清理。原生运行时操作交付状态见S5c进度，不因声明CLEAR/RESET_STATS就视为可执行。

### 原生运行对象操作

`sma.cache.tasks.resources.enabled`默认false，环境变量为`SMA_CACHE_TASKS_RESOURCES_ENABLED`。先升级全部任务读端/worker到理解原生绑定及UNKNOWN的版本，再显式开启；开关不是热刷新。首批为规则运行对象CLEAR与SQL统计RESET_STATS，仍要求各自原业务查看/操作权，规则还要求ALL范围。文件、IP资源和PROTECTED没有此入口。

预览显示目标node及process，五分钟路由截止从受理开始计算。请求到另一个实例可提交同一预览，但只能绑定进程执行；重启产生新进程，不接替旧进程的运行对象清理。截止前未开始则TARGET_NODE_UNAVAILABLE，开始后无法确认则UNKNOWN。UNKNOWN不自动重做，取消不能撤销既有效果；需先检查所属模块资源状态。

原生成功只代表适配器返回且结果持久化。移除/保留对象数量是执行时观测，不是GC内存回收；纯原生报告NOT_APPLICABLE_RESOURCE，混合任务DATA_TARGETS_ONLY只确认DATA。不会把某节点SQL统计清零显示成集群全节点统计清零。

关闭开关不能让旧程序读取新增历史载荷。回退要保留支持ResourceTarget/ResourceProgress/UNKNOWN的读端，核对在途及历史任务；不得删账本、清Redis或改写已执行的Common V12来绕过兼容要求。

### 共享WebSocket票据

M1a已将票据移到Message自有MySQL表，只有随机票据摘要入库。有效期由`sma.message.socket.ticket-ttl`控制，默认30秒、最长1分钟；过期以数据库时间判定。同一租户/portal/session只保留最新票据，消费独立提交，后续会话权威查询失败也不恢复票据。

`max-connections`同时约束每租户跨portal/节点的待消费票据数量，默认10000；活跃连接仍按每节点10000、每节点每会话8条限制，不能将其宣传为集群总连接限额。签发时最多清理200个过期票据，缓存管理无清除票据入口。旧内存票据不能迁移；升级要排空最长票据TTL并统一更新节点，混合新旧程序不能保证跨节点取票可用。共享票据验证不代表跨节点推送/无粘性LB已完成，见实施进度。

### 共享机器登录预算

`POST /open/token`在查询应用凭据前独立预占：同来源60次/5分钟，同来源＋AppId10次/5分钟。该预算覆盖全部尝试，成功不会清零，后续错误凭据、签名失败或事务回滚也不退还；与旧“仅失败后本机计数”的行为不同。正常已签发token的业务请求不重复消耗登录预算。来源必须由受信代理解析链提供，客户端不能自己指定限流身份。

计数使用MySQL固定窗口和行锁，平台最多10000项，每次预占清理最多200条过期项；满额时新主体拒绝，已有主体继续受原窗口限制。无需先假定租户，也不影响正常应用/令牌/nonce的租户隔离；此平台元数据不是业务表例外模板。共享guard串行化预算变更，机器登录属于低频操作，容量与排队延迟仍需按S6实际负载验证。

摘要密钥独立随机生成，经OpenAPI部署主密钥加密保存；同库节点必须使用同一主密钥。不要删除guard、计数或重新生成密钥来“恢复缓存”；密钥丢失但仍有计数、密文损坏或代次变化将拒绝登录。密钥材料和平台预算在缓存目录中标为PROTECTED，无重置/清理入口。切换Local/Redis/None不改变安全预算。新旧限流实现不能混跑宣称共享配额，升级需统一更新入口节点；仅关闭Redis不能回退此安全协议。

保留原“系统监控 → 缓存管理”及`/monitor/caches`路由，页面按四个页签组织：

| 页签 | 使用方式 | 结果口径 |
| --- | --- | --- |
| 缓存目录 | 模块、分组、场景、类型、后端、状态和关键词组合筛选；可选择具体区域 | 无勾选时操作当前筛选结果，勾选时操作所选区域；服务端预览再次逐项检查权限和能力 |
| 运行概览 | 查看已登记区域、后端分布、本地观察器与节点租约 | Redis条目共享、本进程统计不跨节点累加；在线仅代表租约有效 |
| 操作任务 | 本人最近100个预览与任务，逐项详情、取消、可选5秒刷新 | 逻辑完成、历史确认、当前在线分别展示；预热显示实际进程和已检查点读取数 |
| 缓存策略 | 查看代码/部署上限、当前有效值、来源与最近50条控制审计 | 只允许收紧；使用原expectedVersion保存，冲突或未知保留草稿并要求核对 |

清理、暂停、恢复、预热和统计重置先填写原因、预览确切目标/排除原因，再确认提交。响应丢失时页面查询原任务，显式重试也保留同一请求键；不要新建重复任务。关闭页面不会取消已受理任务。取消只影响未完成工作，已应用项不回滚。

刷新同一账号的语言、偏好或会话信息须保持页签/编辑状态；账号或门户变化、锁定及页面销毁会清理内存状态并拒绝迟到响应。任务和策略草稿不写浏览器持久存储。旧版继续使用原列表/清理API；新前端需要同批支持directory/tasks/policy接口的后端，不能把新静态包搭配未升级后端。

## Redis 配置与恢复

```yaml
sma:
  cache:
    provider: redis
    namespace: myapp-prod-v2
    deployment-mode: single
spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      database: 0
      username: ${REDIS_USERNAME:}
      password: ${REDIS_PASSWORD:}
      timeout: 200ms
      connect-timeout: 1s
      ssl:
        enabled: false
```

生产按网络配置启用 TLS，保留证书校验，不关闭 peer verification。支持显式 host/port/database/username/password/timeout/connect-timeout/ssl、sentinel 或 cluster 字段；不接受带凭据的 URL 或未知连接字段。Local/None 不创建 Redis 连接。连接失败、错误拓扑、默认 `sma` namespace、无限内存服务器会拒绝 Redis 模式启动，不静默切成本地。

Sentinel 使用 `spring.data.redis.sentinel.master`、`nodes: [host:26379, ...]` 和可选 Sentinel 自身 username/password；数据节点凭据仍在 redis 根级。Cluster 使用 `cluster.nodes: [host:6379, ...]`、`cluster.max-redirects: 3`，database 必须0；两种拓扑不能同时配置。当前 standalone 有真实连接、整应用与 TCP 断线恢复证据；Sentinel/Cluster 工厂已编译，但具体拓扑实测仍待 S6。

服务端需设置正数 maxmemory；建议专用可丢弃缓存实例及 allkeys-lfu，启动账户需要 PING、INFO memory 与被限 namespace 的 Hash/Lua/TIME/PEXPIRE/UNLINK 权限。应用不会执行 CONFIG SET、KEYS、FLUSHDB 或 FLUSHALL。Redis 配额按序列化字段及固定开销估算，不提供精确物理内存承诺。

协议 v2 把一个租户/区域的代次、权威版本、容量计数和字段放在一个有界 Hash 中。字段绝对 TTL 命中不续期；Hash 的空闲 TTL 为一天加一分钟，整个 Hash 被淘汰后冷加载；清理只 UNLINK 精确作用域。重启后仍先同步 MySQL 权威版本再使用 Redis，回退旧程序或改变格式/容量策略时使用新的部署 namespace；禁止复用旧 namespace 来绕过不兼容策略。

Redis 不可用时受限回源，不保留本地数据副本。容量满或写入失败返回真实回源结果，不改变业务事务结果；源查询异常不缓存。未完成权威核对或计数获取失败时，size/bytes 为 -1（未知），两套前端显示“暂时不可用”，不能当作0。

`governedCache` Health contributor 提供 provider、AVAILABLE/BYPASS/DISABLED、circuit、回源拒绝、Redis错误与未同步范围数，不暴露连接串/键/值。缓存是可降级依赖，BYPASS 时该 contributor 保持 UP；整体可用性仍由数据库与后续 readiness 门禁判断。Micrometer 指标为 `sma.cache.available`、`sma.cache.invalidation.failures`、`sma.cache.source.rejections`、`sma.cache.backend.errors`，只使用 provider 低基数标签。不要只看顶层 UP 而忽略 BYPASS 告警。

## 验证环境

### 回源保护（S2d 第一项）

`sma.cache.resilience`：`max-concurrent-loads` 默认 32，`max-concurrent-per-region` 默认 8，`lock-wait` 默认 100ms、上限 1s。Local/None 的回源、事务绕过、版本校验失败回源均经过同一并发预算；无无界排队。预算不足返回双语 `common.error.unavailable` 和 HTTP 503，不伪装成成功响应或把异常缓存为空值。正常命中不消耗回源许可。

版本查询使用独立 READ_COMMITTED 事务及 2 秒事务超时；同一范围正在校验时，其他请求有限等待后走受限回源。提交回调记录最低已提交版本，即使快速刷新因锁忙未执行，旧校验结果也不能隐藏该提交。None 不为纯读取轮询版本，但写入仍持久登记以通知其他节点。

Redis 熔断已接入（失败阈值默认3，重试间隔默认1s），仅一个半开恢复探测；旧在途成功不能关闭较新的熔断周期。真实 TCP 代理断线/恢复已验证；Redis HA 拓扑切换仍需 S6 专项。

S2 已接入三种 provider、持久版本、逐节点补偿、回源保护与 Redis 运行装配；具体标准回归结果见进度。代码实现不代表现有服务已部署，也不代表完整负载均衡验收通过。

P08 使用独立 `sma_test_p08_20261111_0650` MySQL 测试库和 `backend/target/p08-*/target` 输出。末段保持 `target/test-classes`，避免 ArchUnit 将测试类误判为生产模块。测试启动会清空所配置的专用测试库，禁止指向开发/生产库。

全量最终验证使用 `pwsh -NoProfile -File tools/dev/verify-backend.ps1 -Standard`（配置独立测试库/输出）；阶段专项命令与成功/失败记录保存在进度中。所有 Redis 拓扑、LB 双实例、浏览器与故障演练均需单独证据。

### M1d 双进程协议验收

入口：`node frontend-next/scripts/run-cluster-e2e.mjs`。先完成当前源码后端专项打包，并通过环境提供`SMA_E2E_BACKEND_JAR`、`SMA_CLUSTER_MYSQL_DEFAULTS`（受保护MySQL root客户端配置文件）、`JAVA21_HOME`。默认仅连接本任务MySQL 13306、Redis 16379，后端18101/18102和浏览器测试源5197；端口可分别用`SMA_CLUSTER_MYSQL_PORT`、`SMA_CLUSTER_REDIS_PORT`、`SMA_CLUSTER_PORT_A/B`、`SMA_CLUSTER_ORIGIN_PORT`覆盖。应使用专用验证服务，不能将此入口指向生产MySQL/Redis。

脚本创建带时间戳的独立库和账号，只删除自己创建的对象；A先迁移、B后启动。两个进程共用临时认证/OpenAPI密钥及请求指纹密钥；不同node ID、相同Redis namespace。浏览器只测试真实WebSocket握手，普通HTTP使用无自动重试的请求；所有写入使用独立幂等键，未知结果停止，不改键重发。验证码固定答案和安全参数仅在本轮独立库作为夹具设置；不降低应用原有限流门禁。

证据在`frontend-next/.e2e-runtime/cluster-<timestamp>/result.json`及对应进程日志。结果文件只保存测试名、进程/端口及计数，不保存令牌、密码或TOTP材料；清理临时密钥，记录数据库/进程清理是否完成。Redis只写本轮独立命名空间，数据按登记TTL退役，禁止全库清空。`deployment-mode=single`是本阶段互通测试的明确边界，成功不能视作已开放multi、跨节点事件广播、共享文件或无粘性LB。

### 跨节点消息提示（M2a专项通过，双JVM广播待M2d）

Message持久提示与Redis provider独立；MySQL保存每个收件人/portal/类型的最新提示，各节点查询自己的可信连接范围。提示仅使客户端重新读取原授权REST接口，不包含正文、令牌，也不证明客户端已收到或已读；连续变更可合并，重复提示允许发生。消息正文及未读仍以原持久数据为准，新连接补查和30秒轮询保留。

`sma.message.signals`默认`enabled=true`、`max-entries=50000`（每租户）、`retention=1h`、`users-per-poll=500`、`poll-millis=1000`。工作线程独立且无无界排队；每轮只处理一个tenant/portal的有界用户批次，多组按连接目录轮转，不能把1秒调度间隔承诺为任意规模下的全节点投递时限。`enabled=false`暂停分发及后台清理，但事件提示仍在容量内持久合并；重新开启或重连后补查。保留期最多1天，每次清理至多200条，停用租户也通过可信目录发现。

容量或数据库错误不回滚已提交的消息正文；失败发布保留既有框架记录，按原事件重投/运维协议处理，前端通过REST恢复。会话提示触发权威状态复验，原HTTP鉴权、推送前校验与30秒连接检查继续生效。普通“缓存管理”只能查看此受保护区域，清缓存和切provider不得删提示或放宽权限。旧版本监听器只推本节点，不承诺与新监听器混跑的实时广播；完整资格仍以M2/M3实际双节点验收为准。

### 后台安全状态维护（M2b）

Auth与OpenAPI分别使用`sma.auth.maintenance`、`sma.openapi.nonce-maintenance`，均默认`enabled=true`、`tenant-page-size=8`、`poll-millis=10000`。可信租户目录包含停用租户；不依赖本节点曾经登录或收到签名请求。两个维护器都有独立有界工作线程，租户之间检查2秒本轮预算；每个Auth/nonce事务分别受5秒/3秒期限约束，不能将2秒解释为任意故障下整轮的硬上限。

会话/TOTP待绑定状态按数据库时间判断，最多200条并跳过锁定行；密钥轮换每次最多100条，旧密钥必须按原协议保留。nonce每次物理删除至多200条且仅删除过期记录；不会清除仍可防重放的有效nonce。满负载时需结合租户数/签名速率调整分页与轮询并测量清理积压，不能只看一次扫过就宣称所有空间已经释放。测试profile关闭定时维护，集成用例显式驱动。

### 集群容量验收口径（S6待执行）

先记录MySQL/Redis/两应用的资源限制、实际进程数、连接池与缓存回源预算；预热后分别运行1/2/8并发的认证读取、基础数据读取、不同用户写入及同对象冲突写入。报告总请求、HTTP与稳定业务错误码分布、成功延迟p50/p95/p99、最大延迟，以及数据库查询/等待、Redis错误与回源拒绝。不能把503或409排除后只报告成功请求吞吐，也不能拿限流/冲突当业务成功。

M1d首轮观察到八并发偏好写入中HTTP503，第二轮两并发确认`common.request.unavailable`；租户级受理容量锁已改为原3秒短事务期限内等待，业务资源冲突仍NOWAIT。新可控争用及原协议42项专项已通过，真实八并发重跑和S6容量测量另行记录，暂不具备吞吐承诺。S6必须区分受理竞争、数据库不可用与缓存回源拒绝；短时容量拒绝不能驱动自动换键重发。M1c新增数据库时间查询的成本单独比较。故障测试还需验证Redis不可用时受限回源、恢复后新版本不被旧值覆盖、应用节点摘流后存活节点继续访问。

上述并发数是起始诊断矩阵，不代表生产容量保证；生产容量结论必须绑定对应部署资源、数据规模、实际测量与可接受错误预算。负载问题若影响正常两实例访问，应修复并重跑该项，不能用功能互通通过替代。
