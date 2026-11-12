# P05 插件与零重启发布规则

继承[总规则](../../AGENTS.md)。适用于 `extension-sdk/`、`extension-protocol/`、`extension-runtime/`、`extension-security/`、`extension-worker/`、业务宿主扩展适配和插件项目。具体接入以[后端接入手册](../extension/backend-guide.md)和[可构建示例](../../examples/extension-echo/README.md)为准。零重启是签名业务插件的版本切换；核心认证、宿主 Spring Bean、发布控制面、数据库结构及前端各用原有部署流程。

## 编写和打包

- 插件是 Java 21 普通 JAR，编译仅引用已支持的 `com.sma:extension-sdk`（Maven `provided`），不打包 SDK、宿主类或 Spring Boot fat JAR。源码放自有反向域名包；禁止抢占 `com.sma.*` 等保留命名空间、引用宿主内部包或依赖其类加载细节。
- 在 `META-INF/sma-plugin.json` 声明 `format`、`sdkMajor`（当前均为 `1`）、`pluginId`、`version`、`implementationClass`、`entries`、`dependencies`、`capabilities`、`metrics`。入口声明 ID、类型、契约、调用权限、身份和 `stateful`。类型/身份可写入清单不等于自动授权：OpenAPI应用通过现有 `/open` 认证链和业务 `ExtensionApplicationPolicy` 接入，缺少适配器即拒绝。当前没有通用事件/调度产品，使用稳定业务适配器。
- 依赖锁定坐标和 SHA-256，列出 `lib/*.jar`，走依赖登记和校验；无浮动版本、未声明依赖或宿主框架包。源码上传由 worker 受控编译后重新生成并验证产物，以实际成品哈希发布。同一 `pluginId + version` 只对应一份字节，任何变化都发新版本。
- 实现 `BusinessPlugin`。每版本有独立实例，`initialize` 可以预热，但不能写业务数据或触发外部动作；`execute` 尊重 deadline，按并发调用设计，状态放请求内或隔离到该版本；`close` 释放资源。不要自建线程/调度器，禁止反射宿主、直接文件/数据库/网络访问、native 和动态加载。用最小 `CapabilityInvoker` 契约调用宿主明确注册的 `ExtensionCapability`；宿主校验身份、租户、权限和副作用，不信任插件传来的身份值。
- 无状态调用使用 `invoke` / `/api/extension/invoke/{deployment}/{plugin}/{entry}`。有副作用或需恢复的调用使用 `invokeWork` / `/api/extension/work/{deployment}/{plugin}/{entry}`，以稳定业务键提供 `Idempotency-Key`。工作账本固定原 bundle 和输入字节摘要；同键异参拒绝，成功重放结果，RUNNING/UNKNOWN 先查回执和业务权威结果，不换键或切新版盲重试。业务端自行实现可对账的幂等提交或补偿，不假定 exactly-once。
- 后台 EVENT/TASK 由稳定宿主用 `ExtensionJobPolicy` 与可信租户上下文接入 `invokeJob`；不要在插件生命周期中注册后台任务，避免新旧版本重复执行。
- 异常工作对账只经稳定业务 `ExtensionWorkReconciler`。终态前必须以业务事务/fencing隔离迟到执行，返回 PENDING 时保持原状态；不得把超时当作无副作用证明。宿主只按原claim收敛结果并释放一次引用，无自动重跑入口。相关测试为 `ExtensionWorkJournalTests`、`ApplicationExtensionTests` 和 `WorkCallerTests`；真实业务适配器另验。

## 准备、切换与回退

1. 使用独立安全服务和 worker：权限、环境、分组、动作、预期修订与精确制品字节绑定。上传与发布分别走操作密码 + TOTP 强化授权；同一获授权人员可操作，业务超级管理员身份不替代发布身份。保持制品扫描、签名、不可变快照、信任和租约校验；缺失或失败时拒绝执行，不加目录监听或本地执行后备通道。
2. 先校验和预热，再切换：目标节点的 ready 必须对应本次 `preparationId`、发布摘要、制品集合和有效凭据。只有通过授权的激活和运行租约才能路由新请求；准备失败/超时保留有资格的稳定版本。新旧版本共存时按可信身份稳定分桶，不能让客户端通过请求头指定版本。
3. 用完整版本向量原子切换本节点路由，保持在途请求及持久工作固定到原向量。取消准备不影响稳定路由；节点同步不保证多机同一时刻切换。旧版本有在途调用、持久引用或有效租约时不得强制卸载；控制面中断可维持仍有效的旧租约，到期拒绝新调用。
4. 回退用更大 revision 重新发布经验证的历史稳定向量。验证旧入口能读切换期间的新数据与缓存格式，记录数据补偿/对账范围。不得降低修订、覆盖已发布 JAR、手改激活指针或把路由回退当成数据库回滚。
5. 新版灰度守护：初次权重上限10%，更高权重和PROMOTE须当前候选的完整健康分钟窗口；NODE报文不能来自插件自报指标，缺节点/窗口不足/有候选在途/样本不足时拒绝晋升。GUARD_CONFIGURE绑定当前修订、阈值、最长1小时授权及是否自动回退；无预授权不自动回退，不自动晋升。自动回退仍检查原操作者资格、旧稳定制品与所有节点准备。验证 `CanaryGuardTests` / `ReleaseWorkflowTests`，实际高并发与多机器另验。

## 验收边界

## 新版管理界面

菜单固定为“业务扩展”（`extension:management` 一级目录）→“扩展管理”（`extension:console:view`，`/extension/manage`）。一级目录不自动授予子菜单访问权或独立发布权限。IAM 目录同步只归组仍位于根节点、路由与 nameKey 匹配的内置菜单，保留其 ID、授权、显示/禁用/超管开关及自定义文案；补齐原有角色的目录祖先并迁移导航分组，不覆盖管理员已调整的父目录。无可见子项的目录不输出为导航。修改此行为须回归 `PermissionMenuGroupingTests`、`PermissionTests`、`RolePermissionTests`。

适用于 `frontend-next/src/features/extension/`，正常页面不得再要求第二套账号登录。宿主每次校验人类会话和 `extension:console:view`，浏览器只向 `/api/extension/consoles/requests` 发后台token，禁止转发该token到发布服务。CONSOLE机器只证明宿主来源，独立服务按机器/租户/portal/用户ID绑定发布权限并独立验证操作密码和TOTP，不接受“已验证”布尔字段。发布session留在服务器，绑定映射/机器/凭据修订；首次因素绑定使用票据+操作密码，恢复用操作密码+一次性恢复码。原独立登录API仅保留迁移兼容。所有代理路径显式白名单，证书验证、限额和未知写结果不重试的规则不可放宽；配置与兼容见 [统一入口部署](../extension/unified-console-deployment.md)。

验证入口：前端 `npm run verify`；专项 `npx playwright test --config playwright.extension.config.ts`（隔离测试输出，使用固定模拟账号/服务）；后端 `ReleaseWorkflowTests` 与 `SecurityHttpTests`。模拟浏览器测试不等于真实发布服务、TLS、Docker 或生产文件验收。生产后台代理需正确配置HTTPS与CONSOLE凭据；独立服务原Origin校验继续保留，前端不能提供关闭证书检查或修改信任根的捷径。统一入口回归 ConsoleIdentityTests、ConsoleProxyTests、SecurityMysqlHttpsTests 和 extension.spec.ts。

## 运行验收边界

宿主插件部署摘流（P08 c2b7）：`ExtensionRuntimeService.synchronize`整个控制同步周期与`invokeJob`分别在执行线程、事务外取得`DeploymentWork`许可，严格拒绝HTTP、已有认证身份和外层事务；后台job在`policy.resolve`前准入。同步调度线程禁止继承请求上下文，拒绝不得开始新控制调用/解析对象/领取工作。已准入许可持续到真实同步I/O、插件execute及结果事务结束；插件租约到期只拒绝新调用，不强制卸载在途实例。UNKNOWN保持原工作ID/版本引用，不因资格恢复自动重做。HTTP同步调用仍由原请求生命周期治理，不能借此创建脱离任务；能力适配器必须为真正异步子任务另行设计生命周期。销毁后不得重新初始化runtime，35秒scheduler等待不是实际排空证明。验证`ExtensionDeploymentTests`、`ExtensionJobDrainMysqlTests`、`ExtensionWorkJournalTests`、`WorkCallerTests`、`PluginRuntimeTests`及模块/架构；本机控制与完整关闭顺序另按[P08摘流方案](../proposals/P08-deployment-drain.md)验收。

审计镜像使用独立 AUDITOR 身份（security环境）和独立留存主机，不能复用WORKER/NODE凭据。`audit-mirror`逐批验证锚点/哈希链、只追加文件，落盘后ack。生产可开启 `release.audit.required`：确认过期/积压时冻结新敏感动作与激活，旧租约语义保持。文件只追加不是WORM，独立权限与不可变保留由部署验证。测试 `AuditMirrorTests`、`SecurityMysqlHttpsTests`，运维模板见 [deploy](../extension/deploy/README.md)。

扩展默认关闭。同 JVM 的 ClassLoader 隔离和字节码准入不构成恶意代码沙箱；宿主、发布服务和 worker 的权限边界、独立发布库与签名密钥归属必须保留。观测应能定位到发布修订、bundle、插件版本/哈希和调用结果；不要记录业务正文或凭据。

修改运行时或发布链时检查协议、真实 JAR 双版本同 JVM 切换、拒绝路径、准备/撤销、灰度/回退、在途排空、租约、工作重试与宿主重启恢复。可从 `PluginRuntimeTests`、`ReleaseWorkflowTests`、`ExtensionWorkJournalTests`、`WorkerBoundaryTests` 及根 reactor 验证起步；记录使用的真实制品、状态与测试范围。上线结论另需独立 MySQL/HTTPS、真实容器与扫描、多节点及压力和恢复演练；按[当前实施记录](../extension/backend-status-2026-11-11.md)核对仍待验证的能力。


安全专项上传查询：发布服务GET `/release/v1/uploads/{upload}`及宿主精确GET代理仅输出任务状态、到期、可恢复标记、原文件摘要与制品ID；不得返回session/令牌，重验原操作者、原会话、当前UPLOAD范围。查询不能调用completeUpload；RECEIVING且resumable才可显式原文件恢复，COMPLETE只核对制品。验证ReleaseWorkflowTests、SecurityHttpTests、ConsoleProxyTests、ConsoleRequestScopeTests与普通端点门禁。根sma.verify.directory同时隔离各extension输出，不能在并行测试中共用其默认target。

插件前端上传的注册表标识始终为原command key，ACCEPTED不能释放上传意图；只在查询到COMPLETE后完成。续传必须由用户明确触发，先读原命令和上传状态，再逐文件核对名称、字节数及SHA-256；完整任务不写分片/complete，不重新发命令或授权请求。文件与凭据只在内存，原发布会话失效后不得借新会话接管；查询与续传同页互斥，跨标签原子协调属安全C4。回归extension client/operation-registry单元、playwright.security-extension.config.ts八场景及npm run verify。

P08 Console传输关闭按整个forward登记（机器凭据→发布会话→原代理动作），不把两次HTTP拆成可提前关闭的空窗。shutdownTransport严格host/无事务，拒绝新forward，保留已接受流程；无在途后再shutdown JDK客户端，必须观察isTerminated，不能以Future取消或方法返回报告库已关闭。固定1024本机在途、无队列，原权限/白名单/HTTPS/凭据ACL和每次20秒请求/25秒等待保持；限额不替代请求治理。强制close只阻止尚未通过入口检查的后续阶段，不撤销并发已提交动作，未知不自动重试。验证ConsoleTransportDeploymentTests（真实本地HTTPS）、ConsoleProxy/RequestScopeTests、ConsoleRequestGovernanceHttpTests和ApplicationSmokeTests；外部发布服务/整机滚动停机另验，不能据合成命令夹具宣称真实发布完成。
