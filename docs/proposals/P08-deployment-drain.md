# P08 M3c2：受控摘流与排空

承接[接流验收](../cache-governance-progress.md#m3c1最终验收完成本机独立jvm)，属于已授权的基座多实例实施范围。完整multi仍受M3c3/M3d/S6/X5约束。本方案不新增用户停机HTTP接口，不改变认证、业务回执或任务的UNKNOWN语义。

## 行为与边界

实例启动后，业务工作仅在本机就绪且部署资格有效时准入。主动摘流是不可逆的本进程状态：先关闭新HTTP和后台工作准入，再正常关闭已有WS（1001，不发送session-ended或注销共享会话），等待在途HTTP及工作排空，最后结束服务。已有准入不因后续资格变化被自动撤销、重试或标记完成；原生任务继续使用自己的事务、租约/fence和回执。耗尽宽限时不能伪造排空成功，未知外部副作用仍留待原协议核对。

心跳、配置证据、共享存储见证及排空观察属于基础协调，未就绪/摘流等待时仍须运行；业务worker和持久事件消费不能借此例外继续领取。启动初始化是独立、幂等的技术步骤，不作为业务调度器处理。不同模块通过Common公开契约接入，不跨模块调用internal或覆盖Bean。

## 设计

1. Common维护同一份不可逆摘流状态。HTTP门禁查询它；公开`DeploymentWork`用于可信宿主/系统job在**执行线程、事务开始前、每个新领取单位前**申请许可，不在调度排队时预占。普通认证/匿名HTTP不能申请，许可不授予租户或业务权限。活动许可最多1024个，无排队；拒绝时尚未领取的工作留在原持久队列。许可只在当前工作真正收尾时关闭，重复关闭安全，跨线程关闭可用，不能超时清零隐藏在途工作。
2. `DeploymentWork.Snapshot`只观察本地准入、摘流及活动工作数，不代表整个HTTP/WS都已排空，不含任务/身份/连接/凭据。traffic关闭时仍须完成Spring启动才准入；traffic开启时依赖原evidence/storage/Gate，缺失或异常拒绝。资格失败可以恢复，主动摘流和ContextClosed不可恢复；关闭事件只接受自身上下文。
3. 后续逐一接入Cache任务/原生工作、Mail、消息投递及维护、Auth/Identity/OpenAPI维护和持久事件消费。每个领取/执行单位由许可覆盖，排队回调重新准入；事件拒绝须保留持久失败记录，不能被当成监听完成。HTTP同步触发的在途操作由HTTP跟踪覆盖，独立异步工作必须另有许可。先登记所有调度/异步来源及例外，再加架构清单门禁。
4. HTTP请求与异步生命周期另行计数，避免链返回就错误认为异步请求结束。WS收到进程内摘流通知后正常关闭，新握手/尚未建立的连接再检查摘流状态；不修改会话记录，客户端在健康节点重新取票并刷新REST状态。
5. 本机部署控制默认关闭，只在满足traffic和graceful前置条件时启用。使用运维预建、拒绝链接/重解析跳转的每实例控制目录；持有排他所有权锁，控制记录绑定node/process及请求编号，旧进程请求不能摘流新进程。文件大小、轮询和I/O预算有界，不接受URL/脚本/任意路径。部署请求先摘流，等待HTTP/WS/工作计数归零后关闭上下文；超时保留摘流及未完成状态，不能自动重放业务或报告成功。确切本机命令/收据格式在c子项实现时与测试一起固化。
6. ContextClosed是最后防线，不替代预先摘流；不能等到Spring已销毁调度器才开始等任务。排空期间保持节点租约/证据续报，之后关闭业务线程和HTTP服务，再释放协调资源。验证实际进程退出及另一实例原会话连续可用，不用强杀的进程退出冒充优雅停机。

## 顺序、拟改路径与验收

| 子项 | 范围 | 验收与完成条件 |
| --- | --- | --- |
| c2a | common公开deployment契约、内部协调状态及HTTP Gate接线；无外部控制入口 | 准入/摘流竞争、容量、重复与跨线程关闭、资格恢复、HTTP/身份/事务边界、自身关闭与代理、默认/开启装配；真实HTTP和模块门禁 |
| c2b | 各模块worker及消息WS；二开契约/调度清单 | 已摘流不领取、排队回调重新检查、在途记录保留、事件未被误标完成；WS正常断开且原会话保留；各真实消费者专项 |
| c2c | Common本机控制、HTTP异步跟踪、graceful/生命周期顺序与部署脚本 | 恶意/陈旧控制文件拒绝、超时不报成功、等待HTTP/工作、实际进程自然退出、另一实例继续认证和重连；隔离双JVM |
| c2d | 启动锁读索引、部署说明与兼容/回退 | 精确索引锁范围、默认单机兼容、所有入口清单覆盖及同制品完整矩阵；同步README/规则/总排期 |

状态记录在[P08进度](../cache-governance-progress.md)与[总排期](../development-schedule.md)，每项通过后再推进下一项。默认开关关闭、同机测试不代表生产LB/共享挂载或外部SMTP等环境已验收。

## 已定位的接线范围（c2b开工前复核）

| 来源 | 初步处理 |
| --- | --- |
| CacheNodeRuntime心跳/确认、DeploymentEvidenceRuntime、SharedStorageRuntime、DurableManagedCache版本同步 | 基础协调保持到排空结束，不能作为业务worker例外的泛化入口 |
| CacheTaskDispatcher、MailDispatcher/MailDispatchCycle | 按每个领取单位准入；排队回调执行时重新检查；已领取部分按原协议结束 |
| SessionMaintenance、IdentityLogin/EmailMaintenance、NonceMaintenance、SocketSignalProcessor、StoragePoolRegistry维护 | 逐项限定维护预算并接入资格/摘流，不能只有顶层tick检查而遗漏已排队回调 |
| Audit日志、UserSessionListener、SocketEventListener等ApplicationModuleListener | 许可必须先于监听事务；拒绝保留持久失败/待处理，禁止正常返回伪造完成；技术启动事件另列 |
| ExtensionConfiguration独立定时器与运行时同步 | 必须读取插件专项规则并核对宿主/worker在途租约；不能漏在Scheduled注解清单之外 |
| SMTP/Google/Console HTTP、Domain DNS/TLS、文件传输/隔离检查子进程、规则执行内部线程 | 核对父请求/工作与子任务完成关系；取消Future不必然代表实际I/O或子进程结束，不能过早释放许可/计数 |

待领取的持久队列不属于本机在途数；固定旧process的原生缓存任务继续原路由截止协议，不能因节点下线转投其他进程。UNKNOWN可留待核对，但本机执行/连接仍在活动时不能据方法超时返回伪造排空。该表是源码定位记录，不是已完成接线或完整架构门禁的证明；c2b需补完整清单与消费者测试。

## Mail执行单位与完成条件（c2b2）

MailDispatcher的目录发现单独申请许可，队列中只保留租户标识，不预留许可。回调开始时检查资格，MailDispatchCycle从严格host入口恢复系统任务身份：recover一个许可，随后每个claim→begin→有效性检查→send→finish共用一个许可。资格丢失/摘流后拒绝下一项；尚未领取的记录留在持久队列。目录游标是公平扫描位置而不是消费确认，回调被拒绝后会在下一次目录循环重新发现租户。默认调度关闭及原并发/队列上限不变。

MailDispatchAttempt将同一许可分成领域处理和传输完成两个条件，不额外申请第二个工作名额。领域处理在finish返回或抛出后结束；传输条件由SmtpMailSender持有，到实际执行线程的finally退出后才满足。FutureTask.done会在取消时提前运行，故不能直接用done释放已运行的传输；仅在尚未执行且已取消/被线程池拒绝时可立即释放。两个条件无论哪个先完成均只释放一次。发送开始前验证失败也必须归还传输分支。

超时状态与开始DATA使用同一个同步状态：超时先发生则禁止后来开始发送，返回RETRYABLE；开始发送先发生则超时保守返回UNKNOWN。取消仅关闭当前socket并请求线程中断，DNS或其他底层调用若暂未退出，许可仍计数；后续迟到返回不得覆写已持久化结果或重发。数据库提交失败继续保留原SENDING/租约恢复UNKNOWN规则，不能由排空跟踪器修改领域状态。邮件SMTP密码、地址、正文及供应商响应不进入部署状态或日志。

本机TLS夹具验证协议/断连，真实MySQL夹具使用受控传输替身验证入队、短事务、摘流与结果；两者分别记录，不能称为真实供应商送达证明。该接线也不替代c2c的优雅关闭：PreDestroy仍为最终销毁兜底，整机只有在HTTP、WS、所有消费者及子任务均完成后才能宣称排空成功。

## 安全与身份维护执行单位（c2b3）

SessionMaintenance和NonceMaintenance的调度、执行回调以及内部直接cleanTenant入口均要求host，无HTTP、无认证身份、无外层事务。目录发现单独准入，后续每次有界清理在开始事务前获得许可，到事务提交/回滚后释放；原事务超时、会话/nonce每批200行、SKIP LOCKED及过期判断保持。直接cleanTenant未获准时返回0，仅表示本次未清理，不能据此证明没有过期记录；轮询内部区分“拒绝”和“完成0条”，拒绝不跳过租户游标。已准入发布SessionEndedEvent的事务可提交，但其异步持久监听完成另由c2b4跟踪。

Auth会话/安全挑战过期处理与TOTP重加密属于两个执行单位，前者结束后必须再次申请，不能在摘流后继续启动重加密。Identity登录维护每租户一个单位；邮箱挑战维护和重新认证维护分别申请，在两项之间摘流时不继续后一项，恢复资格后重新访问该租户。每项继续原领域有限批次及可信SYSTEM_JOB范围，失败只记录固定诊断并归还许可，不能扩展删除范围、改变有效期或清除未知回执。基础协调续报不受这些业务维护器准入影响。

## 持久事件的事务与完成边界（c2b4）

当前Spring Modulith 2.1.2使用Async拦截器调度，CompletionRegisteringAdvisor（HIGHEST+10）在工作线程记录PROCESSING，包住领域事务，随后记录COMPLETED或FAILED。监听方法显式添加`@Async("deploymentEventExecutor")`，选用4线程/128队列、AbortPolicy、禁止继承请求ThreadLocal且不装饰ContextSnapshot的专用执行器；非默认候选，其他异步处理沿用原执行器。默认执行器会传播请求上下文，已由首轮真实宿主失败证明不能直接用于host消费；禁止通过放宽host或无条件清空调用者身份修补。Common自动匹配ApplicationModuleListener：外层生命周期Advisor（HIGHEST+5）在执行线程先尝试准入，内层准入Advisor（HIGHEST+20）在领域事务前拒绝未获准调用。两层之间包含框架结果记录，已准入许可到框架记录结束才归还；不会把业务已提交但结果记录仍在写的瞬间当作排空。

未获准时外层只保留拒绝状态，由内层抛固定EventDeferred，使框架持久化FAILED；不能正常返回让事件变COMPLETED，也不能在框架拦截器外提前抛错而使事件只能等待PROCESSING超时。无许可的这段框架技术收尾不执行领域事务，整体停机须另等事件执行器退出；c2c不能仅看DeploymentWork计数归零就关闭数据库。框架记录自身失败时，原staleness/持久结果核对继续负责恢复，禁止本地自动重发。

保持监听方法标识、事件record和持久表格式，旧失败记录可通过原管理权限/租户校验后的重投流程继续处理。资格恢复不会自动重投所有失败事件，仍使用既有显式重投；摘流不可逆，本机不恢复接流。新增静态业务ApplicationModuleListener自动使用相同准入，须显式选择deploymentEventExecutor，架构门禁检查漏配；方法体必须同步完成、单参数void、REQUIRES_NEW且可被Spring代理，领域恢复可信租户及幂等仍由所属模块负责。异步子任务、Future返回或其他事件类型需单独设计和验收，不能绕开生命周期跟踪。框架升级须复验实际代理顺序、FAILED/COMPLETED、事务提交及原记录ID重投，不能只凭顺序常量推断兼容。

实际重投验收发现原Monitor使用FailedEventPublications(batchSize=1)先对全局失败记录LIMIT，再按所选ID过滤，队列前存在其他FAILED时会静默不投递。改用框架公开IncompleteEventPublications谓词入口，在所有未完成候选上只匹配指定ID、FAILED与当前租户，框架仍通过原子markResubmitted竞争并调用原监听代理；不绕过HTTP权限/请求治理，不批量重投外租户。该入口读取未完成集合，当前管理查询本来也使用集合扫描；本子项不宣称实现新的数据库分页或全局并发限额。事件执行仍受专用线程池容量和DeploymentWork预算限制，队列满/进程退出留下的持久未完成记录由原staleness与显式重投核对。

## 消息信号执行单位（c2b5）

SocketSignalProcessor的tick与pollOnce只允许无HTTP/身份/外层事务的host调用，专用单worker有界队列不继承ThreadLocal，排队不预占许可。订阅发现、每个租户/portal有限收件人批次读取、每条未观察信号的push或revalidate分别申请许可；读取事务结束后再做授权复查和网络调用。当前同步推送已获准则允许返回，不因摘流撤销，下一信号必须重新申请。

读取后或两条信号之间拒绝时不推进批次offset，未开始信号不写observed；资格恢复重读该批，已经观察的版本继续去重。observed只是本进程见过该提示，不代表客户端已收到/已读，持久信号仍保留，各节点按自身连接独立读取，REST授权查询仍为事实来源。清理先许可内发现目录，再独立申请清理事务许可；拒绝不推进维护游标或推迟下一次维护，原过期范围/批次预算不变。

本子项跟踪SocketSignalProcessor发起的同步Hub调用，ConcurrentWebSocketSessionDecorator可能与连接初始化/ping/其他发送并发并暂存消息，故不能据该调用返回就声称底层连接已排空。SocketHub.validateConnections、所有连接生命周期与缓冲发送、摘流1001正常关闭及重新取票在后续WS子项统一处理；禁止复用session-ended/POLICY_VIOLATION来代替部署下线。c2c须综合HTTP/WS/执行器，而非仅看本机工作数。

## 存储后台消费与协调（c2b6）

StorageOutboxWorker使用独立单worker、有界队列和无继承ThreadLocal的线程，不在Spring公共调度线程调用业务消费者。tick仅host且就绪时排队，回调在执行时重新申请目录发现许可；每轮最多8个已有物理池，由StoragePoolRegistry在事务前逐个申请消费许可。许可覆盖原物理库claim、同步消费者、finish以及池租约释放；拒绝不推进当前资源游标，异常不在本轮立即重做。物理池未打开时不为扫描而创建池，仍按原节点/主机/租约预算运行。

BusinessOutboxConsumer原协议为至少一次：UNKNOWN租约到期可用同一不可变事件和新fence再次投递，消费者必须按稳定事件幂等；这与通用请求账本UNKNOWN不自动重跑不是同一种状态机。摘流仅停止当前实例新领取，不改状态、载荷、attempt或租约，不推断未知外部结果。消费者deliver返回必须表示本地执行已结束，不可启动脱离式异步后提前返回；此限制需业务接入测试，可信Java接口本身不是线程沙箱。

StoragePoolRegistry的定时evictIdle独立准入，拒绝时不回收；当前请求因容量需要的闲置池回收和已持有会话的release/close仍在父工作收尾内执行，不能再申请新许可阻断收尾。关闭保持有租约池，最后租约释放才真正关闭。StorageDrainWorker是明确的技术协调例外：摘流期间继续确认已有DRAINING绑定的本机/持久会话为零，保留节点/process/epoch原校验；仅扫描与确认，不调用复制/提交切库/业务消费者。入口严格host，单次扫描沿用最多20项并串行，不把例外泛化到Outbox。c2c必须在协调线程完成后才销毁平台库。

## 插件控制同步与后台入口（c2b7）

ExtensionConfiguration的独立30秒定时器也属于调度清单，线程不继承请求ThreadLocal。synchronize只允许无HTTP/身份/外层事务的host，在周期执行开始时申请许可；许可覆盖本周期信任读取、控制HTTPS、撤销与策略、先serving后desired的续租/准备/激活、回收和权限登记。资格失败/摘流时标DEFERRED并停止新周期，已开始的周期允许按原签名/租约协议收尾，不能只因取消Future或等待超过35秒就认定同步结束。控制I/O未返回时保持许可，完整超时/真实控制服务与c2c有序停机另验。

PluginRuntime当前execute同步运行在调用线程，入口检查租约并固定实例inFlight，执行期间不会因lease自然到期自动卸载实例或取消能力调用；因此部署摘流后暂停新的控制同步，不会把已进入execute的调用变成可重试失败。新调用仍按原租约校验，期限不因部署摘流延长。关闭runtime阻止再初始化，整机必须先等待已准入调用，再执行销毁，不能把PreDestroy兜底当作完整优雅关闭。

invokeJob仅由稳定宿主以原ExtensionJobPolicy和可信归属进入，先检查严格host，再于policy.resolve之前申请许可，覆盖journal接受/claim、同步插件调用和成功/UNKNOWN结果处理。拒绝不解析业务对象、不claim，不修改原工作账本或持久版本引用。已存在UNKNOWN仍只能读取/对账，恢复资格不重新执行插件。WorkCaller.job额外拒绝匿名HTTP与外层事务，避免绕过既有后台身份检查。HTTP人类/机器invoke及invokeWork保持原权限与同步请求边界，统一HTTP在途跟踪归c2c；宿主能力内部若开启分离子任务，必须另外跟踪真实生命周期，不能据插件方法返回宣称该子任务已结束。

## WebSocket握手、发送与技术关闭（c2b8）

Common新增只读DeploymentConnections SPI，为后续关闭协调提供各适配器的pending handshakes、connections和active operations快照；不暴露业务载荷/用户/连接ID，也不提供用户摘流入口。Message握手在消费一次票据前预留有界名额，失败释放，成功升级与afterConnectionEstablished原子交接为连接；摘流后的新握手拒绝且不消费票据，已经消费票据的晚到升级仍计数并1001关闭。升级成功却迟迟未收到回调不靠超时伪造完成，后续c2c据未完成数报告等待/超时。

全部连接发送入口（connected、业务hint、ping、终止提示）都跟踪实际方法执行。Spring ConcurrentWebSocketSessionDecorator保持5秒观察限额/64KiB有界缓冲；其缓冲由仍在执行的发送线程同步刷新，因此所有调用统一计数后，另一个入队调用提前返回不会把活动数归零。连接从快照消失须afterConnectionClosed；即使回调早于原send返回，全局operation仍保留。关闭请求先阻止新发送，已有发送返回后再发close，避免部署1001被并发缓冲限制替换；异常提示不保证送达，REST仍是事实来源。

独立单worker每250ms受控排队（至多一个待执行任务），回调重查状态，每轮最多128项。正常ping每连接至少间隔30秒，在事务外逐连接申请DeploymentWork，覆盖会话核验/同步发送；技术关闭不申请新业务许可，摘流期间继续运行。只读观察无I/O。部署关闭固定1001且不发送session-ended，不修改MySQL认证会话；关闭异常保留未完成连接供下一轮核对/重试，不能清空集合冒充排空。销毁是兜底，完整线程关闭顺序仍需c2c；不能据本项关闭API已返回宣称整机退出。

维护使用显式轮转队列，每轮最多检查128个连接，包括失败关闭项；失败项不会长期占据扫描起点。已有连接被关闭后从轮转队列移除，新连接加入队尾。10000连接在无阻塞I/O时至少需约20秒轮转一周，这不是总关闭时限；底层发送/close可能阻塞，c2c须按真实计数和线程状态等待并准确报告超时。销毁后仍晚到的既有升级在回调finally执行1001兜底，预留不因销毁自动丢失；未返回或未回调仍保持未完成。

## 调度及隐式异步审查（c2b9）

完整来源及未完成生命周期缺口维护于[活动清单](P08-deployment-activity-inventory.md)，由DeploymentActivityInventoryTests核对编译后精确基线，不能只搜索Scheduled注解。b9a先登记/验证清单，b9b补Future取消后子线程/异步传输观察，b9c核验独立检查进程与规则库隐式执行，b9d组合收口。Hikari/Lettuce/HttpClient、Spring/Modulith自动配置线程要在c2c实际宿主里验证关闭顺序；静态清单通过不表示整机已可关闭。
