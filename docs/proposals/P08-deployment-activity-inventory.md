# P08 M3c2b9：调度、异步及资源生命周期清单

承接[摘流方案](P08-deployment-drain.md)。这是当前源码的归属审查台账，**登记不等于排空验收完成**。状态和实际命令同时记入[P08进度](../cache-governance-progress.md)与[总排期](../development-schedule.md)。未解决的条目阻止c2b9整体完成及生产multi开放。

## 自动门禁

`DeploymentActivityInventoryTests`读取基座和静态业务的编译后字节码，比较`backend/src/test/resources/deployment-activity-inventory.txt`中的精确入口、目标、调用次数及调度参数。检测Spring直接/组合/重复Scheduled、类/方法Async、执行器创建和提交、Thread/虚拟线程、并行流、异步Future、HTTP发送、Servlet异步、规则引擎调用及显式Caffeine/HttpClient/Hikari/Lettuce资源创建；lambda调用归属仍来自实际字节码。新增/移除/改名或改变这些参数必须人工核对，标准构建不提供自动批准开关。失败时只在本次隔离输出生成`deployment-activity-actual.txt`，不得直接复制未审条目冒充验收。

门禁使用真实编译夹具验证替代API和组合注解，不执行夹具里的线程、进程或网络请求。它是可信源码的变更审查工具，不是线程沙箱：反射、native代码、第三方内部线程与自动配置不可能仅凭宿主调用点穷尽。库版本/自动配置变更必须复核下面的资源台账，c2c还须读取实际应用的调度器/执行器及运行线程，验证关闭顺序；最终X5/S6不能被这份静态清单替代。

业务二开新增来源须在自己的归属/生命周期清单声明，随独立业务构建验证；不能为通过基座门禁删掉扫描、忽略业务包或把业务冒充技术协调。初始默认基座清单只登记基座来源，业务组合构建须明确提供其审查后的组合清单并保留原基座条目。

## 已接入的业务及持久消费者

| 来源 | 执行归属、摘流行为 | 证据及剩余事项 |
| --- | --- | --- |
| CacheTaskDispatcher.tick / 执行器 | 执行线程逐claim准入；原路由、租约/fence和回执保持 | c2b1完成；执行器有序结束归c2c |
| MailDispatcher.poll / MailDispatchCycle / SmtpMailSender | 每领取单位准入；结果提交与SMTP真实线程均结束才归还同一许可 | c2b2完成，Future.cancel不视为结束；供应商送达另验 |
| SessionMaintenance.clean、NonceMaintenance.purge | 独立worker；目录及每个有界维护事务准入 | c2b3完成；排队回调重查 |
| IdentityLoginMaintenance.poll、IdentityEmailMaintenance.poll | Spring调度线程同步执行；逐租户/逐维护事务准入 | c2b3完成；公共调度器也须在DB销毁前结束 |
| LoginLogListener.on、OperationLogListener.on、UserSessionListener.on | 持久异步监听，专用deploymentEventExecutor | c2b4完成，许可覆盖领域事务和框架完成事务；拒绝留FAILED |
| SocketEventListener.notice/todo/privateMessage/ended | 同上，可信事件归属和领域幂等保持 | c2b4完成，不能直接调用监听器绕过框架 |
| SocketSignalProcessor.tick | 独立worker，发现/读取/逐信号/清理分别准入 | c2b5完成，observed不是客户端送达 |
| StorageOutboxWorker.tick、StoragePoolRegistry.evictIdle | 逐资源claim/同步消费/finish/池租约释放；闲置回收独立准入 | c2b6完成，至少一次Outbox与通用UNKNOWN语义不同 |
| ExtensionConfiguration.extensionControlScheduler / ExtensionRuntimeService.synchronize | 独立30秒调度，整个周期准入；在途I/O保持 | c2b7完成本地；实际HTTPS、HttpClient资源与关闭顺序仍需后续核对 |
| ExtensionRuntimeService.invokeJob | 非定时入口，由可信host调用；policy.resolve前准入 | c2b7完成，HTTP原同步调用归父请求；不得自行分离子任务 |
| SocketHub.tick / 内部维护worker | ping逐连接准入；1001技术关闭在摘流时继续；握手/连接/所有发送独立观察 | c2b8完成本地；原登录/票据保持，实际滚动重连归c2c |
| SecurityCollectionService.tick / SecurityCollectionWorker.cycle | 严格host；每磁盘/数据库I/O单元先准入；队列不预占，未知ACK保留 | 已有安全专项接线和测试；本清单仅核对源码，关闭后volatile队列不是持久证据；联合关闭待c2c |
| SecurityRetentionService.tick / SecurityRetentionCycle.cycle | 新增安全保留期维护；平台清理、目录发现及逐租户清理分别准入，单线程有界、不继承身份；不是技术协调例外 | 并行安全专项负责持久清理验收；P08仅审查/登记构造、带initialDelay调度和提交3条，不能据登记宣称已完成；实际关闭归c2c |

## 基础协调例外

| 来源 | 为什么摘流期间仍运行 | 关闭依赖和待验收点 |
| --- | --- | --- |
| CacheNodeRuntime.heartbeatTick / confirmationTick | 保持本进程租约及持久缓存控制确认，不领取新的业务任务 | 两个执行器及继承上下文需要最终核验；确认完成前不能关闭DB/Redis或清除租约 |
| DeploymentEvidenceRuntime.tick | 持续提交当前配置/密钥资格 | 独立零队列线程；关闭时等待真实收集/报告，不能只看busy标志 |
| SharedStorageRuntime.tick / FileClusterStorage | 持续证明共享文件资格 | 外层单worker与内层文件I/O线程是两层生命周期；内层观察已在b9b2-2完成本地验收，整体关闭顺序仍待c2c |
| DurableManagedCache.reconcile | 同步已登记版本/失效，帮助已准入请求收尾 | Spring定时线程同步访问DB/Redis；c2c在资源关闭前停止并等待 |
| StorageDrainWorker.tick | 仅确认已有DRAINING任务，不复制、不提交最终切库 | c2b6技术例外；协调结束后再关平台DB |
| Modulith StalenessMonitor | 框架自动配置的持久事件陈旧状态维护 | 宿主字节码不包含它的Scheduled声明；c2c实际调度清单须覆盖，不能据本清单没有条目认定不存在 |

技术例外不能推广成业务调用入口。初始化、Flyway、权限目录引导属于启动阶段的同步步骤；不替代运行时工作准入。

## 尚需补齐的子任务和传输

| 来源 | 当前真实行为 | 后续验收要求 |
| --- | --- | --- |
| GoogleHttpTransport.call | 4线程零队列使用tracker；已开始任务取消后保留计数，库客户端另查真实终止 | b9b3-1本地完成：34项契约、43项本地协议/生命周期及27项MySQL/HTTP回归分轮通过；停止提交后先完成已接受任务，再由执行器terminated钩子关闭JDK客户端。无外部Google账号验收 |
| LocalTransferStorage.execute | 写入/核对/冻结在独立零队列线程；已使用DeploymentTaskTracker，超时返回不清除running | b9b2-1本地完成：真实文件锁/迟到退出/关闭/身份隔离通过；旧符号链接权限夹具跳过1，Junction通过。完整父HTTP/进程关闭仍归c2c |
| FileClusterStorage.execute | 基础见证内层2线程零队列；已独立观察真实任务至finally，取消不清零运行计数 | b9b2-2本地完成：46项专项及4项默认宿主/真实MySQL互证通过；c2c仍须按协调依赖停止，生产多主机挂载未验收 |
| ConsoleProxyService.forward/send / LimitedBody | 按完整两阶段forward计数，固定1024在途无队列；关闭后拒绝新调用、已有流程完成再shutdown客户端 | b9b3-2本地完成：35项专项含真实HTTPS两阶段/未知命令不重发/证书与JDK终止，3项MySQL宿主/请求治理通过；实际发布服务和整机顺序另验 |
| NodeControlClient.post/download | 同步send后以InputStream读正文，完整方法由同步周期许可覆盖 | 阻塞正文仍保留父许可；客户端内部资源结束和关闭顺序待c2c，不假定请求header超时覆盖全部stream读取 |
| DomainTlsProbe.inspect / deadline callback | 父请求同步TLS；独立定时线程关闭socket；cancel(false)不能证明已启动回调结束 | b9b：登记技术回调实际退出，保留固定地址/TLS证书验证和总预算；默认HTTP身份不可传播 |
| RuleFlowRuntime / LiteFlow | 只生成THEN/IF/SWITCH的单路径表达式，调用execute2Resp；实例引用随返回释放 | b9c：实际阻塞dispatch、异常和调用线程测试，核对库版本的隐式执行器；不以API名同步推断完成；禁止二开引入WHEN/并行或异步节点绕过验收 |
| ExpressionEngine / QLExpress | 语法及函数白名单内同步求值，禁止任意Java方法/循环/线程 | b9c：保留现有沙箱/预算回归，新增异步函数必须另设计生命周期 |

## 独立检查进程与资源库

`ContentInspectionWorker.main`的90秒watchdog属于独立检查JVM，不是Spring Bean。`VideoContentInspection`仅从worker路径调用`BoundedInspectionProcess`创建ffprobe子进程；三路流线程分别读取stdout/stderr和写入stdin。父进程销毁及Future.cancel之后，原实现会保留仍活着的直接子进程槽位，但不据此宣称全部后代进程/流线程已退出。P09容器启动/隔离与完整杀毒仍依其专项验收；宿主不得为通过P08而直接在上传请求内调用这些解析器，也不新增本地检查后备路径。b9c要用架构边界防止worker-only入口被宿主调用，实际容器/后代退出证据归P09和联合验收。

资源库必须分清“业务活动”和“库维护线程”：

- Caffeine：LocalManagedCache.entries显式Runnable::run执行失效监听；states、RedisManagedCache.states、DurableManagedCache.observed可能用默认维护执行器，只含本地缓存状态，不执行领域事务/网络。新增loader/removal callback或refresh异步须重新审查。
- HttpClient：Google、Console、NodeControl各自创建客户端，内部selector/连接/响应任务不是宿主扫描可直接枚举的调用点；完成和资源关闭须用实际客户端生命周期验证。
- Hikari：平台自动配置池、StoragePoolRegistry业务池、StorageMigrationService临时迁移池都有库维护线程；必须晚于领域/事件/维护事务和业务会话结束。业务池当前租约保持由P10及c2b6验证，不能据线程daemon性质提前销毁。
- Lettuce/Netty：CacheRedisConfiguration显式连接工厂及Boot相关资源关闭晚于缓存读写/确认/见证；Redis不可用时的fail-closed与原PROTECTED状态不得被停机流程修改。
- Tomcat、Spring公共scheduler/默认taskExecutor、Modulith事件执行器由实际应用上下文核对；HTTP异步、WebSocket和排队技术收尾要先完成，不能只关闭监听端口。

当前b9a静态清单、b9b1公共原语、b9b2本地文件传输/共享见证与b9b3-1/2 Google及Console传输已分轮验证。包含并行新增SecurityRetentionService审查条目后基线为85条；安全维护的业务验收仍归其专项。全部未验证行必须保留状态，不能把静态门禁通过写成c2b9或整机排空完成。

## b9b1 子任务观察契约

公共DeploymentActivity只输出所属组件的稳定id与queued/running快照，snapshot无I/O。DeploymentTaskTracker不创建线程、传播身份或申请新的业务许可；父HTTP/工作/技术协调必须先满足自身准入，组件使用原受控执行器提交任务。提交时登记有界queued，实际run开始转running；未开始的取消/执行器拒绝立即释放，已开始的Future即使done/cancel也要等run finally才释放。重复run、重复close、取消/启动竞争不得重复减计数。

close仅关闭新提交并请求取消，既不等待也不报告已退出；shutdownNow移出执行器队列的任务必须配合tracker.close取消，避免永久queued。计数只覆盖包装的Callable本身，Callable不能自行分离子线程/进程后提前返回；HttpClient内部selector、连接和响应订阅仍须独立完成/资源关闭证据。正常Future.get已返回也可能早于wrapper finally，允许短暂仍有running；不得据业务结果已返回强行归零。b9b1公共原语验收与各消费者接线分开记录。

b9b2-2见证子线程接线：FileClusterStorage实现DeploymentActivity，id=file.shared-witness；原2线程零队列委托DeploymentTaskTracker，工厂禁止继承ThreadLocal。内部execute仍校验可信host；取消/关闭映射原unavailable，未开始取消不写探针，运行中的I/O计数到真正finally才归还。观察不生成或延长资格，不绕过共享资源声明/租约/摘要/锁。单元及独立JVM文件锁46项、真实MySQL互证/默认宿主4项均通过；底层挂载真实阻塞由受控I/O夹具持有真实文件锁模拟，不声称已测生产NFS/SMB故障。双runtime互证运行在同JVM，不替代完整独立双进程滚动停机。

## b9b3 辅助传输关闭契约

DeploymentTransport只适用于组件独占的辅助传输，不是数据库或全局停机API。shutdownTransport仅接受无HTTP/认证身份/外层事务的host，非阻塞、幂等、停止新提交；先完成已接受的任务，再关闭库客户端。transportTerminated只读且同时核验任务、执行器和库客户端真实终止，不能将snapshot为0或Future取消当作库资源结束。调用前宿主必须关闭新父请求/工作准入，**并等待已准入父请求/工作完成**，因为同一个父流程可能先兑换再获取公钥等多次调用传输；父方法超时后仍存活的子任务继续由传输观察/收尾。完整调用顺序由c2c实际协调验收；没有用户HTTP控制入口。

DeploymentTaskTracker.stopAccepting只关闭新提交，保留已有queued/running；close则请求取消，仍不提前清零运行任务。Google实现采用专属线程池terminated钩子发起HttpClient.shutdown，避免等待线程池期间提前关闭已接受任务仍要用的客户端；终止观察保留JDK HttpClient.isTerminated。PreDestroy只作强制取消兜底，底层不结束就保持未终止。Google生产端点仍固定，所有本轮协议测试仅使用本地回环服务器和受控客户端替身，真实供应商账号不在证据范围。

## b9b3-2 Console两阶段流程

ConsoleProxyService以extension.console-http观察整个forward，从机器凭据读取、发布会话交换直到原代理动作返回；同步调用在原请求线程运行，不另开执行器。最多1024个本机forward、无排队。容量或关闭拒绝发生在读取凭据/调用发布服务之前，仍先执行原权限/配置/路径/host Authorization合法性检查，不能用关闭状态绕过授权。

shutdownTransport只允许host：拒绝新forward，已接受流程允许从会话交换继续到原动作；活动forward全部结束后才HttpClient.shutdown。snapshot=0只描述父调用已经返回，异步HTTP内部资源仍须isTerminated核对。每次HTTP保持原20秒请求/25秒正文等待，两阶段并不是合计25秒预算。超时、取消和未知写只返回原CONSOLE_UNAVAILABLE，不自动重发命令、分片或complete。

PreDestroy强制关闭阻止尚未通过本机send入口检查的后续阶段；与关闭并发且已通过检查的提交不能视为已撤销，也不能证明远端未执行，仍用原协议只读核对。强制关闭不清活动计数，Future或客户端返回终止也必须等待forward finally。HTTPS、证书/主机校验、白名单、机器token ACL和host token不外传保持；本地HTTPS夹具使用合成命令计数，不连接用户发布服务。
