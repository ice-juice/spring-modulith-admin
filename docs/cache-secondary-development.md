# 二开缓存接入契约

适用于同进程静态业务模块；交付状态以[P08进度](cache-governance-progress.md)为准。热插件与独立服务须通过各自受支持的宿主能力/外部协议接入，不能访问宿主Bean或数据库表来复用本契约。

## 模块与注册

业务模块位于`com.sma.business.<bizid>`，通过模块元数据声明`common :: cache`依赖。模块自己的`CacheCatalog`提供固定`CacheDescriptor`；`owner=<bizid>`、区域ID为`<bizid>.<region>`、nameKey以`<bizid>.`开头，中文/英文语言包同时提供。

生产启动会解开Spring代理，检查提供类所在模块及`ApplicationModule`元数据；注册区域与真实模块不一致则失败。业务区域的`managePermission`必须使用自身业务权限前缀，例如`shop:cache:manage`；管理操作还要求对应`monitor:cache:*`权限，分组不授予权限。不能由业务Catalog冒充config/iam/auth目录，也不能注册他模块的预热加载器。

新区域必须说明分组、场景、类型、隔离范围、DTO格式版本、有限TTL/负TTL、容量、单条字节限制、允许操作和所有失效来源。重复ID启动失败，未登记区域不能调用ManagedCache。启动配置中不认识的区域覆盖也应拒绝，不能靠拼写错误偷偷创建缓存。

这套校验约束可信业务源码的归属与接入方式，不构成恶意Java代码的安全沙箱。任意Map是否属于跨请求缓存仍须代码评审；临时集合、请求内memoization与业务权威状态不能仅凭类型判定为缓存。

## 读写和失效

独立业务MySQL使用`BusinessCacheCatalog`声明`Region(storageGroup, descriptor)`，并调用`BusinessCache.get`；不要把同一区域再登记到平台`CacheCatalog`。仅可重建DATA和事务内CLEAR声明可用，group必须属于实际业务模块。宿主打开固定业务会话，先读业务库提交代次，再在该会话只读事务调用loader；缓存键还包含MySQL物理源ID/修订。写操作用`BusinessLedger.Session.invalidate`同事务登记，平台`ManagedCache.invalidate`在业务路由内拒绝。首次读取/命中均先做业务授权，loader不写库；外层已有事务时业务缓存入口拒绝。共享客户端、最多8个活动源缓存、全局和租户并发有界；此业务缓存预算独立于平台缓存，配置容量评估需相加。单源本地内存/活跃范围按源槽分摊，Redis每区域/租户仍由原Lua配额与服务端maxmemory约束。业务缓存暂不进入平台运维清理/预热任务；专用Redis与在线改绑留X2b/S8。验收记录见[单实例基线](secondary-development-baseline-progress.md)。

- 只使用`ManagedCache`与公开cache契约。禁止直接使用Redis/Lettuce/Jedis/Redisson、Caffeine、Spring Cache或JSR Cache；CacheBoundaryTests覆盖两个包根。框架实现类只在common内部，业务不访问它们。
- 数据缓存只保存可重建、不可变且可安全序列化的DTO；不保存Mapper实体、运行时类、任意多态对象、凭据/令牌明文或权限有效性结论。`schemaVersion`发生不兼容变化时提升版本并记录升级/回退条件。
- 租户、portal、用户取可信调用上下文。业务键包含实际需要的对象ID、语言、查询条件及数据范围摘要；不能用HTTP参数授予租户/用户身份。缓存命中仍不替代对象授权。
- 创建、修改、删除、恢复、导入、初始化、翻译和授权范围变化均须审查失效。通过`ManagedCache.invalidate`加入同数据库资源事务；事务回滚不能发布失效，事务内读取不会回填共享缓存。跨库二开须声明资源事务边界，不能把独立数据库提交说成与common版本表原子一致。
- 回源在Local/Redis/None下都受并发预算；缓存失败允许有界回源，不能无限创建线程或无限重试。需要强一致鉴权的数据直接读取权威来源。

## 预热

所属模块可提供唯一`CacheWarmupLoader`，声明区域、递增版本、自己的业务读取权限及Limits。不得为用户私有区域提供预热。分页游标必须是稳定keyset、长度有限且由服务端维护；没有HTTP任意key/SQL/脚本/类名入口。

`enumerate`只读、按当前租户和操作者数据范围返回有限页；`load`通过正常ManagedCache读路径幂等读取，禁止业务写入和外部副作用。回填调用不能处于实际数据库事务中。每次I/O须有有限超时；宿主只在调用之间检查取消和截止时间，不能强制安全终止任意Java实现。

宿主上限为每页16项、每区域声明总量不超过1000项、速率不超过每秒20项、时长不超过120秒；任务整体还有1000项/120秒上限。实际速率允许最多一页突发，页后持久冷却；不承诺集群所有任务合计20项/秒。修改分页、游标或键语义须提升加载器版本，使旧预览/任务明确拒绝继续。

None、暂停、关闭部署预热开关、无业务读取权或无加载器均拒绝。成功页保存检查点并重置未完成批次的尝试；崩溃可能重读未确认页，因此加载器必须无业务副作用。换执行进程不能冒充原Local缓存已完整预热。

## 管理与升级

运行对象使用所属模块的`CacheResourceAdapter`。`inspect`只返回有限元数据并验证原业务读取权限/范围；未知计数为-1。`authorize`必须无副作用，验证原业务操作权和数据范围；`execute`只能执行有限的本进程缓存维护，不允许业务写入、文件删除或网络副作用。规则在途对象需要所属模块自身保护。旧适配器默认拒绝变更，不能因目录声明CLEAR就跳过业务授权。

首批仅RUNTIME支持CLEAR/RESET_STATS，由默认关闭的`sma.cache.tasks.resources.enabled`控制。预览冻结进程和`operationVersion`，操作语义不兼容时提升版本；五分钟内目标进程没有开始执行则明确失败，不改由别的进程代做。执行前后分别持久记录，副作用在任务事务之外；开始后无法确认结果显示UNKNOWN，禁止自动重做，也不能声称取消能撤销已经发生的效果。返回的移除/剩余数量只表示观测值，不代表物理内存回收。

登记区域自动进入现有缓存管理目录。DATA能力通过统一策略/任务接口治理；RUNTIME/RESOURCE须按S5的所属模块适配协议接入，保护状态不暴露普通变更操作。管理结果不能返回缓存正文、原始业务键、连接端点、秘密或内部路径。

部署provider/namespace/连接参数通过重启切换；租户运行策略只收紧部署上限。二开不能覆盖基座默认配置为单个项目改变通用行为。全局节点清单/租约表是框架例外，不允许业务表借此绕过租户拦截。

新增枚举、JSON字段、缓存DTO或加载器版本须验证旧载荷和接入方；PREWARM只能在所有读端/worker升级后显式开启。只关闭开关并不使旧程序能读取新增历史动作。旧预览、在途任务、未知结果和审计不能通过删表或清缓存绕过。

原生资源任务同样要求所有读取端/worker先升级到理解ResourceTarget/ResourceProgress/UNKNOWN的版本，再开启开关。关闭开关只阻止新执行，不能消除已有历史载荷；回退必须保留可读取这些历史记录的兼容版本。Common V12已经过真实数据库迁移验证，后续只追加迁移。

## 验证入口

接入前至少覆盖：实际模块装配与Mapper/Flyway发现，目录归属和权限交集，Local/None/真实Redis返回语义一致，跨租户/portal/用户/数据范围拒绝，全部写入口失效、事务回滚、在途旧回填，加载器分页/预算/撤权/取消和版本变化。

仓库门禁：`CacheRegistrationOwnershipTests`、`CacheBoundaryTests`、缓存契约/模块/架构检查及根标准reactor；应用共享会话、广播、文件与LB资格由M1～M3/S6单独验收。基座提供的测试通过不能替代实际业务自己的场景验证。

部署一致性扩展（P08 M3b2阶段）：可信静态业务模块可通过common::deployment的DeploymentEvidenceSource声明自己的固定域，域以实际业务模块ID开头；注册校验真实包和Modulith声明，不能冒充基座域或重复注册。收集只在无HTTP/身份/事务的宿主线程执行，读取已绑定的部署配置/运行对象，返回定长摘要或不可用，禁止返回密钥/原值、查询租户业务数据或执行网络/业务写入。域集合进入总摘要，两个实例缺少同一扩展不能静默视为一致。纯配置字段逐项白名单，不能反射序列化整个Environment或包含凭据的record。动态租户配置与业务资源仍走原公开契约及P11归属；这里不替代跨库失效和幂等。当前多实例资格尚未开放，协议限制与验收见[部署一致性设计](proposals/P08-deployment-evidence.md)和[P08进度](cache-governance-progress.md)。

## 后台工作与摘流契约（M3c2分阶段接入）

静态二开调度器通过公开`common :: deployment`的`DeploymentWork`，在执行线程、每次新领取持久工作前、事务开始前调用`tryStart()`。可信host或由持久归属恢复的SYSTEM_JOB可以申请；普通用户/HTTP即使切成job也不能申请。空结果表示不领取，原队列和请求键保留；不是取消、已失败或允许换键重做。许可不代替租户/对象/权限与原生fence校验。

拿到许可后覆盖该单位的实际执行和结果提交，真正收尾才`close()`；关闭可跨线程且幂等。不能在提交线程先占位再排队、仅包装tick、超时自动关闭仍在运行的子任务，或提前释放来让排空看似成功。新资格失败只阻止新准入，在途继续原业务协议；主动摘流不可恢复。`snapshot()`只给本机准入/摘流/活动工作数，0不证明持久队列、HTTP或WS均已处理完。

适用范围是同进程可信静态模块；热插件和独立服务仍按各自支持的SDK/协议，不依赖宿主internal。当前核心API及后续消费者/本机控制的交付状态查[P08进度](cache-governance-progress.md)，不能把接口存在当作基座全部worker已接线。接入验证至少覆盖排队后发生摘流、领取并发、在途异常/重复关闭、旧权限/fence及UNKNOWN保留。

持久事件消费使用`@ApplicationModuleListener`并显式加`@Async("deploymentEventExecutor")`。宿主自动在事务开始前准入，方法内不要再申请许可；只支持单参数void、REQUIRES_NEW且可代理的同步方法体。专用执行器4线程/128队列，不继承请求身份、不满载回退调用线程；按事件可信归属恢复租户和领域幂等仍是业务责任。许可覆盖领域事务和框架完成记录，拒绝留FAILED，资格恢复后按原记录显式重投，不能用返回成功或删记录绕过。Future返回或脱离式子任务需另设计生命周期，当前不支持以此宣称排空。接入运行DeploymentEventBoundaryTests并补业务自己的真实持久事件拒绝、回滚、重投与跨租户测试；框架升级须复验代理顺序。本机实际停机仍按c2c，工作数0不证明事件队列和技术收尾已经结束。

物理业务库的BusinessOutboxConsumer由宿主StorageOutboxWorker在部署准入后调用，消费者不要在已有许可内再申请新部署许可；deliver返回须表示本地消费执行已经结束，不可分离异步线程后提前返回。该协议至少一次，UNKNOWN租约到期可能以同一不可变事件/新fence重投，必须按原事件ID/稳定键幂等，不能把它当作通用请求账本UNKNOWN自动重跑许可。摘流停止新领取，已开始的执行/结果提交及池租约保持到结束；接入方须验证重复/未知结果/迟到执行与本地资源关闭，P08单机专项不替代业务跨库一致性验收。

热插件后台工作通过稳定宿主的BusinessExtensions.invokeJob与ExtensionJobPolicy接入，调用方必须为无HTTP/认证身份/外层事务的可信host；不要先切成tenant job再调用。宿主在解析业务对象前完成部署准入，策略负责按可信租户校验归属、权限及稳定workKey。许可由宿主持有至同步插件执行和结果记录结束，插件不得自建脱离线程；原UNKNOWN恢复时只查询/对账。控制同步暂停不延长租约，也不会中断已经进入execute的实例。具体规则和验证入口见[插件规则](agent-rules/extension-hot-reload.md#运行验收边界)，普通静态模块仍遵循上文DeploymentWork契约。

长连接适配器可实现公开DeploymentConnections只读观察SPI，为宿主提供握手、连接、真实I/O计数；snapshot禁止访问DB/网络，不泄露身份/消息。它不授予部署控制权，0仅表示本适配器已收尾，不能代表HTTP和所有后台任务。保持升级预留到交接、全部缓冲发送到实际线程退出、关闭到真实回调，不能超时清零；具体接入需提供自己的竞争/故障/关闭验收。基座消息部署关闭1001不撤销登录，客户端需新票据重连并从授权REST补查，提示不是送达回执。

静态业务新增定时/异步/线程/进程或隐式并发资源时，必须在业务自己的审查台账声明父工作、可信归属、拒绝/恢复、容量及真实完成条件，并在自身分支扩展deployment-activity-inventory.txt组合清单，保留基座原条目。运行DeploymentActivityInventoryTests和业务消费者的实际排空测试；不能为通过清单删除扫描或自动接受发现输出。方法超时/Future.cancel并不证明子任务退出；公共DeploymentWork与连接快照各有范围，完整资源及库线程审查见[活动清单](proposals/P08-deployment-activity-inventory.md)。

若已准入的业务需要将同步I/O交给受控子线程，可使用DeploymentTaskTracker包裹原执行器，并通过DeploymentActivity输出稳定模块id和queued/running快照。提交前父请求/工作必须已合法准入；该工具不传播租户或授权，不允许从客户端随意拼装系统身份。取消未启动任务可释放，已运行任务取消Future后仍须等run退出；组件关闭调用tracker.close并关闭执行器，不能遗漏被shutdownNow移出的队列任务。真正分离的孙任务/进程、HttpClient等库资源仍有自己的生命周期；不得因snapshot为空就声称整个请求已结束。消费者至少验证忽略中断的真实I/O、结果未知、拒绝及关闭竞争；当前接入范围见[活动清单](proposals/P08-deployment-activity-inventory.md)。

共享存储适配器的内层I/O属于技术协调子任务，不能用业务DeploymentWork拒绝续证，也不能因Future超时就释放真实线程/文件锁观察。基座FileClusterStorage已接入file.shared-witness观察，资格仍由原持久租约及双向见证决定；新增业务存储见证适配器须分别证明外层协调停止、内层I/O结束和资源释放，不能把取消请求当作成功关闭或自动重写未知探针。

组件拥有辅助传输库时，可实现DeploymentTransport供宿主受控关闭；它不适用于数据库或全局服务停止，不提供用户HTTP入口。关闭前宿主先停止新父请求/工作准入并等待已准入父流程完成，组件再通过tracker.stopAccepting拒绝新子任务但保留已接受任务，执行器真正终止后才关闭其库客户端。终止观察必须覆盖客户端自己的资源，不能只看子任务snapshot；不支持真实终止查询的库应保持未完成并明确适配缺口，不得返回猜测true。shutdownTransport拒绝HTTP、认证身份和外层事务，close仅做最终取消兜底。新增消费者须用真实本地传输验证成功收尾、取消后的连接结束和库终止，整机关闭顺序另验。

二开不得通过直接调用file.internal.inspection的解析器/子进程工具把独立检查搬进宿主；仅按公开上传协议或受支持独立worker协议接入。InspectionWorkerBoundaryTests扫描基座和业务包，协议DTO/帧例外不授予解析执行权。规则新增并行/异步节点或表达式函数需要新的生命周期设计和实际运行测试；当前RuleDeploymentExecutionTests只证明基座生成的单路径THEN/IF/SWITCH与原计划租约边界，不能据此为任意业务异步工作宣称排空。

P08 c2b9d已用固定快照组合验证公开观察契约的宿主发现：子任务活动与传输生命周期须对应同一组件归属，WS单独计数。二开新增线程、异步、传输或监听器仍要提交自己的精确入口及生命周期验证，不能继承基座6活动/4传输/1连接清单；基座组合通过不等于二开组合或整机排空通过。技术协调例外只限已审宿主组件，不为业务开放。验证入口为DeploymentActivityInventoryTests、DeploymentActivityAssemblyTests及自身消费者；c2c真实关闭与X5仍待验收，部署开关限制保持。

c2c1父HTTP跟踪不是二开异步执行授权：宿主为同一容器请求的REQUEST/ASYNC/ERROR/FORWARD/INCLUDE保留原准入，直到容器销毁与分派返回，不复制认证ThreadLocal。业务若新增Callable、流式响应、AsyncContext.start或其他子线程，仍须按自身归属定义可信身份、预算及实际完成观察，通过入口清单和安全验证；不得因HTTP计数归零推断子线程停止。当前JSON浏览器/租户上下文仍遵循原同步约束，执行器接线与有序关闭按c2c2后续验收。

框架时间事件也不是集群单例：Modulith Moments整点/每日Cron在各实例各自触发，并可发布周/月/季/年边界事件。当前基座没有这类业务消费者；二开若订阅，须先定义可信租户/操作者、周期业务键、持久唯一领取或幂等及摘流规则，不能用内存锁或“收到一次事件”推断集群只执行一次。它属于事件生产者，停止顺序必须早于消费执行器关闭；运行清单发现这两项不代表已交付通用集群调度产品。

traffic开启时，MVC异步观察复用原applicationTaskExecutor；WebAsyncTask可用默认执行器或显式名称deploymentMvcExecutor，指定其他executor会在Callable启动前拒绝。只增加生命周期观察，不授予异步tenant/user上下文或放宽同步JSON请求约束。业务自行创建的线程、Publisher、DeferredResult/CompletableFuture外部生产者以及解包原Servlet请求后提交的工作，仍须独立登记和验收，不能以响应完成或Future取消当作执行终止。适配方法和预算不会改变原业务UNKNOWN回执语义。
