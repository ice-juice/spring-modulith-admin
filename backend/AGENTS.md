# 后端基座开发规则

安全Collection/Retention工作线程通过内部SecurityWorkerThreads固定宿主类加载器，同时禁止继承InheritableThreadLocal；不得为解决打包ClassNotFound改回继承调用者身份或TCCL。名称、单线程/有界队列、工作许可和关闭条件按原服务保持。变更回归SecurityWorkerClassLoaderTests、Collection/Retention/Scheduling、EncryptedSignalSpool及普通活动清单/结构门禁；需要打包证据时使用SecurityWorkerPackagedProbe经Boot PropertiesLauncher加载实际JAR，只复制测试探针class，不将测试回调打入生产制品。探针无Spring上下文/数据库，不能替代完整宿主或P08停机验收，命令和前后摘要见[S2进度](../docs/security-event-progress.md)。

安全事件整表容量夹具仅用于独占可清空测试库：SecurityPipelineHttpTests、SecurityFilePipelineHttpTests和SecurityRetentionMysqlTests必须声明JUnit Isolated，并经测试专用SecurityDatabaseFixture核对实际schema、拒绝外层事务，锁定原两条容量行后原子重置四张模块表。初始化失败时AfterEach不得调用存储清理；维护用例的实际prune/计数断言继续执行，不能以夹具重置冒充清理实现通过。生产容量/held/回滚语义不因测试污染放宽；原Store容量测试由同样受限的独立夹具负责。修改验证SecurityDatabaseFixtureTests及同一JVM的Query、Store、Collection/Metadata、两个Pipeline和Retention组合（alphabetical顺序），单类空库通过不能替代组合证据；清理前后仍须遵守原测试进程和密钥目录边界。

文件信号的真实TLS持久闭环使用SecurityFilePipelineHttpTests，独占可清空安全测试库；不得替换collector/store、spy生产场景授权或用转发头代替本轮TLS。测试场景只通过正式Catalog/Policy登记，客户端仅信任本轮证书并校验端点，不修改全局TLS信任。故障临时CHECK必须恢复；JVM实际退出后精确清理日志给出的UUID目录，含证书、租户/采集密钥与文件夹具。此证据不替代生产DNS/TLS、Google、云或P09隔离扫描，命令与范围见[安全运行手册](../docs/security-defense-operations.md#http采集闭环验证s2d3b)。

宿主事件线程在禁止继承InheritableThreadLocal时须显式设置宿主定义类的ClassLoader，不能继承请求/插件提交者的加载器。Java21默认退回系统加载器会在Boot嵌套JAR中导致Modulith异步审计反射找不到宿主类。保留原4线程/128队列/拒绝策略和DeploymentWork生命周期；验证DeploymentEventTests的隔离加载器/身份隔离/容量，以及打包JAR真实登录与操作审计，不能只用普通测试classpath证明可部署。

专用全局容量夹具SecurityEventStoreTests只在已核对的测试库运行，JUnit类隔离并在每项事务中重置本模块四张事件/容量表，不能把前序HTTP/采集留下的数据算进固定小容量，也不能修改生产限额。StorageOutboxDrainMysqlTests仅显式设置SMA_P08_BUSINESS_DB_URL才启用，继续检查loopback:13306和精确测试库前缀，允许任务独立后缀；缺环境的普通全量跳过须单列，不计为专项通过。邮件摘流先确认两个真实任务分别到达传输，再共同摘流；SKIP LOCKED单次未领取不能当成发送失败，也不能为测试加生产重试。

真实HTTP/WebSocket夹具须使用各自的Host与租户归属，不能覆盖或借用另一测试已登记的唯一域名。规则网络场景使用127.0.0.51，Socket摘流使用127.0.0.3；修改后同时跑SocketDeploymentMysqlTests与规则真实网络用例，再跑完整根reactor，不放宽生产域名唯一性。

S2d4a的common.security.ObservedRejection仅承载可信代码固定的拒绝Fact，继承BizException保持原错误码/参数；只有MVC原响应层捕获当前身份、实际决定状态并复用一次尝试，构造异常和worker不得直接上报或传任意身份/源对象。不能全量把INVALID_IMAGE/INVALID_UPLOAD或解析IOException当安全文件结论；文件事实仍NO_ATTACK_INFERENCE。传输异常可原路返回至父调用，超时/取消后不得补造原请求归属。后台扫描接线须等P09受控宿主及任务终态验证，不能绕过隔离门禁。变更回归RequestRejectionSignals/Response/Http、FileNameRules、PublicImageEncoder、LocalTransferStorage/Deployment、UploadTaskHttp及原File/上传回执；验证状态见[S2进度](../docs/security-event-progress.md)，业务调用见开发手册。

S2d3b完整采集闭环使用SecurityPipelineHttpTests：真实随机端口/采集器/加密磁盘/MySQL/维护调度，不替换collector/store；故障通过专用库临时CHECK构造，finally恢复，HTTP原响应必须保持。仅独占可清空测试库；本轮生成密钥和缓冲目录须在测试JVM实际退出后按精确UUID路径清理。异步入库等待按事件ID单次查询，正常请求同时检查HTTP与业务code。它验证本地故障恢复，不代替Google/SMTP/云、生产代理/容器或跨版本整机验收；复跑入口见[运行手册](../docs/security-defense-operations.md#http采集闭环验证s2d3b)，实际阶段与失败历史见S2进度。

S2d3保留维护仅宿主调度，平台/目录/每租户单元各自事务前取得DeploymentWork，排队不预占；摘流不启动新单元，关闭超时不能释放实际事务许可。每轮有界、含停用目录项、无跨模块表读取或新增租户豁免；held与未过期记录不能删除，删除与容量回收复用Store原子事务。拒绝保留游标，单租户失败下一轮重试并避免阻塞其他项。maintenance默认开启、测试profile关闭，配置进入部署摘要；不得将每节点预算称为集群单线程。新executor/schedule/submit须审查并同步DeploymentActivityInventory，不能自动批准清单差异。验证SecurityRetentionTests/Scheduling/Mysql/Drain、DefenseMaintenanceConfiguration、原Store与部署/结构门禁；范围见S2进度，不将组件通过等同完整HTTP采集闭环。

S2d2b的SecuritySignal.metadata只含部署组/节点/进程快照与有界HTTP事实；请求头/原URI/参数/正文不得透传。MVC模板必须与选定HandlerMethod的声明精确匹配，未知/畸形回落UNRESOLVED，不能猜测；NoResource仅实际资源handler、NoHandler仅确无handler可记UNMAPPED低级观察，普通业务404不推断攻击。显式传响应决定状态，MVC业务200不取ErrorCode状态冒充HTTP。默认查询投影不扩字段，includeMetadata=true才选入。六参数构造和无metadata JSON保留；新包schema2、旧包schema1可读且不能改历史摘要，禁止旧消费者静默忽略新版本或删除证据回退。验证SecuritySignalCompatibilityTests、RequestObservationTests、EncryptedSignalSpoolTests、SecurityMetadataMysqlTests、RequestRejectionHttpTests、SecurityEventQueryHttpTests及原采集/模块门禁；阶段见S2进度，组件测试不替代真实库或最终闭环。

S2d2安全事件HTTP仅有当前租户events和平台platform-events的GET列表/UUID详情；后台身份、显式权限及服务层权威授权同时保留，平台额外验证部署运维名单。禁止新增tenantId切换、跨租户通配、证据修改或payload透传；列表1～100、固定投影/no-store/QUERY预算。CurrentUser或超级管理员不替代显式defense权限。验证SecurityEventQueryHttpTests及模块/平台隔离/普通接口策略门禁；新增入口先审查再更新基线，不能在标准命令保留review标志。元数据/保留调度/真实采集闭环继续按S2进度验收。

S2d统一拒绝观察在LocalizedErrors中按已审错误key分类；Tenant/Browser可显式提供固定Fact，RequestSourceScope内最多尝试一次且在认证清理前捕获。不得按任意HTTP状态/错误正文归类，不得把普通404、对象不存在、版本冲突、幂等完成或5xx当成攻击。采集器缺失/异常/拒收保持原状态、正文与header，禁止在此同步写库/磁盘或重试。ErrorResponseWriter新增writeObserved保留default委托兼容；二开不调用common.internal映射器。验证RequestRejectionSignalsTests/ResponseTests/HttpTests及原Auth/OpenAPI/Identity/架构门禁；HTTP采集替身不等于持久闭环，阶段见S2进度。

S2采集只公开common.security.SecuritySignals.observe(Fact)，调用线程固定身份，无等待内存准入，禁止任意DTO/tenantId入队及HTTP磁盘/数据库IO。QUEUED_VOLATILE不是持久化回执；默认禁用，独立节点scope/目录/32字节密钥显式配置。worker每单元先DeploymentWork，再持久磁盘、同ID入库、确认后ACK；未知/容量/冲突保留，停止准入不提前释放实际IO许可。加密缓冲拒绝symlink/Junction、坏文件/错钥/版本不匹配，OS独占锁和条数/字节/优先预留不能去掉；关闭超时不清目录。验证EncryptedSignalSpoolTests、SecurityCollectionTests/Configuration/Mysql及原Store/架构/部署证据；阶段见S2进度，生产拒绝接线归S2d。

defense事件存储只允许内部宿主worker提交可信捕获包；匿名请求和已认证HTTP不得直接调用append/prune/hold，不公开任意SecuritySignal入库接口。PLATFORM与TENANT物理分表，平台查询同时要求tenant实现的DefensePlatformAccess部署身份和defense:platform:view；租户读取按当前tenant及defense:event:view，超管不跨界。REQUIRES_NEW使被拒业务回滚不删除证据；容量锁、幂等/冲突、保留期与持有记录不可被普通缓存操作清理。修改回归SecurityEventStoreTests、DefenseStorageConfigurationTests、DeploymentEvidenceAssembly/FieldInventory、平台隔离/模块/架构/i18n；只追加defense迁移，阶段以[安全采集进度](../docs/security-event-progress.md)为准。

S2来源观测使用common.security的RequestSourceQuery，只读DTO不构成身份。RequestSourceFilter在租户目录解析前绑定同步线程，内部绑定不可由业务设置；异常/结束必须清理，不继承到异步线程。source.trusted-proxies仅影响来源元数据，禁止扩张Host/Proto信任或以转发头绕过现有认证预算。CIDR/地址严格数值解析、链从右向左、有界且不存原始头；不确定回落直连证据不能当客户端归因。配置变更同步HostPolicyEvidence与字段清单；验证RequestSourceTests/Configuration/Http/Evidence、TenantRequestFilterTests、DeploymentFieldInventoryTests及模块/架构/普通端点门禁，状态见[采集进度](../docs/security-event-progress.md)。

S2信号捕获只接受固定Fact，从当前认证取得归属；匿名/未知/入口不匹配或HTTP身份转job归PLATFORM，禁止CurrentUser默认tenant/请求tenantId推断。DTO不可作为授权或持久化证明；后续存储不得暴露直接接受客户端SecuritySignal的入口。保持eventId独立、correlationId服务端请求级生成，凭据/用户名/原workId/自由正文不得加入信号；历史规则版本由存储保存，普通拒绝不推断攻击成功。验证SecuritySignalCaptureTests的主体/归属/脱敏/大小/绑定及ActorContext和架构，接线/持久化阶段见S2进度。

规则原生结果查询：模板GET /templates/{id}/publications/{operationKey}要求当前发布权限、当前资产范围与原操作者；批次GET /migration-batches/operations/{operationKey}要求batch:view及batch:migrate、原操作者与原批次范围。保留tenant/portal，超管不得读取他人原键回执；404不证明原写未执行，GET不得调用发布/迁移重放。回归RuleOperationLookupHttpTests、RulePlatformTests与普通端点基线。

Message公告收件人的纯详情GET使用NoticeLookupMapper加当前收件人/tenant/portal/未删除校验，不沿用管理详情的DataScope，也不因此赋予管理访问。GET不得改变readAt或发布事件；原PUT标记已读保留本地幂等契约。变更回归NoticeInboxLookupHttpTests、NoticeTests、MessageRequestGovernanceHttpTests及普通端点基线。

安全专项B6已按进度记录完成本地快照验收；后续新入口仍须普通策略基线检查，不能继承该完成状态。浏览器JSON包装只支持同步MVC读取，异步/流式消费必须单独设计生命周期与验收。含批量容量夹具的测试URL保留rewriteBatchedStatements=true；专用库安全检查、容量断言及生产持久性参数不能为提速而降低。

API策略基线为src/test/resources/security-endpoint-policies.txt；SecurityEndpointInventoryTests对编译后的方法/路径/访问/有效治理策略做比较，类级继承也不能让新入口未经审查静默进入。先审查副作用、事务归属、权限、原生回执与重试，再在专项命令中使用-Dsma.security.review-policy-baseline=true更新，并复跑普通门禁；禁止在标准构建脚本默认开启该标志。浏览器JSON字节与并发在MVC前有界，保留原始字节，响应JsonMapper不得因此公开WRITE_ONLY字段。回归BrowserRequestFilterTests、BrowserSecurityIntegrationTests、ConsoleRequestGovernanceHttpTests与实际受影响HTTP消费者；跨站/类型检查仍先于正文读取。

请求指纹使用独立RequestInputFingerprint序列化器纳入嵌套WRITE_ONLY输入，不能拿响应DTO序列化结果充当完整输入；不得修改全局响应JsonMapper来暴露密码/secret。摘要再经既有HMAC入账本，临时序列化字节清零。平台目录执行和重放继续调用tenant的PlatformOperators，不通过字符串权限替代部署身份；品牌发布/回退及Mail取消保持原生协议，/app应用身份不套后台人类GovernedApi。验证RequestInputFingerprintTests、TenantRequestGovernanceHttpTests、MailRequestGovernanceHttpTests、IdentityRequestGovernanceHttpTests及MailProfile/IdentityConfiguration/IdentityEmailFoundation原HTTP专项。

安全专项文件上传使用file自有任务；common本地Work只接受任务，不能写磁盘/网络。仅ACCEPTED可在原键及原输入下唯一领取，RUNNING/UNKNOWN禁止超时释放或自动重传；file结果查询须校验本人/tenant/portal/当前权限及安全代次。已删除元数据仅作完成证据，不恢复文件读取权。二进制清理只在STATUS_ROLLED_BACK执行，UNKNOWN保留供对账。后续存储扩展须保留这些边界，file V2之后仅追加迁移；回归FileUploadRequestTests、FileUploadHttpTests、FileRollbackCleanupTests和原File/Profile/品牌专项。集成文件目录使用sma.verify.directory下test-storage，不能共享并行专项的blob计数。

个人随手栏适用于 workspace 的标签、便签与显示设置：所有读写强制绑定可信 tenant/user/portal，超管不豁免个人归属；菜单通过 IAM 公开查询校验，不跨表访问。便签正文禁止进入日志/URL，更新必须验证版本，容量与列表写入在本人行锁下完成；客户端新建请求键用于去重，不能以最后写入覆盖冲突。只追加 workspace 迁移。验证 ShelfTests、ModularityTests、ArchitectureRulesTests 及根标准门禁；阶段状态见[随手栏进度](../docs/personal-shelf-progress.md)。

安全专项按[进度](../docs/security-defense-progress.md)逐项验收；密码策略保持 P07。浏览器写来源门禁与部署精确 Origin 见[运行说明](../docs/security-defense-operations.md)，不信任任意转发头，不把 Bearer 豁免扩展到未来 Cookie 认证。新增 Mapper 禁止 `${}` SQL 文本插值，动态结构需服务端固定映射；URL 语法校验不替代 SSRF 网络授权。验证 `BrowserRequestFilterTests`、`BrowserSecurityIntegrationTests`、`WebAddressesTests`、`SqlStructureSafetyTests` 与根 reactor。品牌归租户平台单一实现，勿重复建表或页面。

`common :: request` 的 `RequestExecution.executeLocal` 只允许宿主同库、同资源事务；调用入口不能已有活动事务，Work中禁止网络、独立提交或跨库写。授权回调在执行/重放前运行；结果重放只给自身最小回执，业务详情再走原对象授权。不同键业务重复仍由领域唯一性/状态/版本保证。UNKNOWN不能自动释放或重跑，普通缓存清理不能碰账本；HMAC旧键在关联回执退役前保留。验证 `RequestExecutionTests`、`RequestFingerprintKeysTests`、`RequestConfigurationTests` 及架构门禁；接口接入进度见请求治理子计划，不把基础API通过当全站已接入。

请求治理配置record若保留兼容重载，必须明确规范构造器的ConstructorBinding，并以真实配置Bean验证默认值和环境覆盖；测试用配置使用TestConfiguration隔离，不能进入应用组件扫描。GovernedApi的Advisor需注册为基础设施角色，在方法授权之后执行；验证RequestConfigurationTests、RequestGovernanceAdvisorTests与RequestGovernanceHttpTests，不能仅凭注解存在认定HTTP防护生效。

IAM请求治理保留原服务的租户/portal/数据范围与凭据锁顺序，不改变P07密码规则。角色编辑和授权使用同一iam.roles互斥组，个人资料按本人隔离；回执只含本人状态，不返回或缓存密码和资料。回执不能替代原详情接口的对象授权。修改这些入口回归IamRequestGovernanceHttpTests、LoginPasswordWriteTests、SecurityPasswordTests及原IAM专项。

消息、待办和随手栏的个人写入使用用户＋对象互斥范围；公告管理共享对象锁，已读入口用方法级GovernedApi覆盖为个人范围。不要把所有收件人的已读操作互相阻塞，也不要用个人锁替代共享管理对象锁。方法覆盖必须位于已标记控制器中；保留便签原requestKey/version与消息事务事件。回归RequestGovernanceAdvisorTests、MessageRequestGovernanceHttpTests及Notice/Todo/PrivateMessage/Shelf/Socket专项。

GovernedApi默认LOCAL；QUERY/NATIVE必须写明审查原因。QUERY仅允许无业务写入查询/模拟，NATIVE保留原生事务、工作账本或运行时效果，不生成通用完成回执。模板发布独立保存候选报告、规则批次分项提交、缓存任务自带幂等、事件框架至少一次重投，均不得再套本地事务。RequestAdmission只限本节点并发；同步套接同一服务复用名额，不能借它放宽授权、跨资源事务或自动重跑UNKNOWN。UUID路径按绑定值规范化。回归RequestAdmissionTests、RequestExecutionTests、RulePlatformTests、CacheTaskHttpTests、PublicationRequestGovernanceTests和既有HTTP治理专项。

本文件补充[总规则](../AGENTS.md)。适用于宿主、基座 API 和启动时集成的业务模块。宿主扩展相关代码还必须读[插件与零重启发布](../docs/agent-rules/extension-hot-reload.md)。

原生协议适配：readOnlyRequest/requestResources只用于NATIVE，必须按模块实际路由白名单验证，不能消费正文、修改参数或相信客户端safe/version标志。会话作用域只取已绑定的sma.session-id请求属性；代理分片按规范化真实目标区分，读写预算独立。频率维护仅可清理本租户已过期5分钟以上的计数，每次最多2条且跳过锁定行；严禁用于持久回执/互斥/UNKNOWN清理。回归AuthRequestGovernanceHttpTests、ConsoleRequestGovernanceHttpTests、ConsoleRequestScopeTests、RequestExecutionTests及认证/签名原协议。

- `com.sma.admin`（基座）和 `com.sma.business`（二开）的直接子包都是 Modulith 模块。模块 ID 使用单个小写英文单词，与包名末段相同，在两边全局唯一；`package-info.java` 声明 `@ApplicationModule` 的名称和显式 `allowedDependencies`。业务依赖基座模块公开面；基座通过自己定义的 SPI 获取业务扩展，不引用业务实现。模块之间无环。
- 两个包根共用宿主的组件、Mapper 和 Modulith 扫描。添加二开模块沿用现有启动类和扫描配置；变更包根或发现机制时，验证纯基座可构建，也用隔离业务样例验证模块发现、Mapper 注册以及非法路由和权限前缀会被拒绝。
- 根包只放公开接口、record DTO 和事件；不在签名暴露 `internal`、实体、Mapper、框架实现或敏感字段。内部按 `web → service → persistence` 分层，Controller 不碰 Mapper，写事务在 service。构造器注入；采用全限定 Bean 命名，显式 Bean 名仍需唯一。
- Controller 放 `internal.web`，类名以 `Controller` 结尾；后台使用 `/api/<module>/<复数资源>`，应用用户使用独立 `/app/<module>/...` 安全链。已有 `/open/**` 使用独立机器安全链，不作为新后台接口的例外。每个处理方法显式声明访问策略：后台 `@RequiresPermission`/`@PermitLoggedIn`、公开 `@PermitAnonymous`；P09 应用用户使用精确 audience 的 `@PermitApplicationUser`，机器接口使用 `@PermitMachine`，两者仍须具备对应安全链及对象授权。`@PermitApplicationSelf` 仅限架构逐方法白名单内identity当前会话/退出/本人认证方式及file本人上传任务操作；file还须检查scene audience与owner.realm/client，业务不能借它省略audience。新增方法必须单独审查架构白名单与端点策略。普通管理接口返回 `ApiResponse`，业务错误用 `BizException(ErrorCode, ...)`。Long ID 按字符串传输，调用方不得转成 JS Number。
- 权限 key 使用 `<module>:<resource>[:<action>]`，由所属模块声明。菜单、按钮及逻辑权限通过 `PermissionCatalog` 和现有 IAM 流程登记；只补缺项，保留已有 ID、授权和管理员设置。新增权限不等于给角色授权。插件入口权限遵循专项规则。
- 跨模块查询走公开 Query/API。提交后的联动用根包 record 事件和 `@ApplicationModuleListener`；携带可信 `tenantId`、稳定标识和必要快照，恢复正确租户并按事件业务键幂等。需要同事务强一致时走同步公开 API。版本升级时须考虑已持久化但未处理的事件。
- 访问本模块数据、迁移、配置时读[数据与配置](../docs/agent-rules/data-and-configuration.md)。从可信认证/任务上下文取得租户、portal 和操作者；禁止用客户端传入值授予身份，禁止绕过 MyBatis-Plus 租户和数据范围拦截。审计使用既有机制，记录动作与结果，避免记录凭据和正文。

## P09 应用身份、邮件与文件服务

按[专项进度](../docs/integration-services-progress.md)分阶段实施，调用说明见[二开手册](../docs/integration-services-developer-manual.md)。Google/邮件/存储配置必须属于可信租户，不能全局账号回退。`common :: security` 的 `ActorContext` 不允许匿名默认租户；`AppUserPrincipal` 与后台 `SmaPrincipal` 分离，旧 `@PermitLoggedIn`/`@RequiresPermission` 只允许后台人类身份。应用端使用显式 audience 的 `@PermitApplicationUser` 加对象/租户校验；不能因新增类型放宽旧数据范围或后台路由。受信工作线程用已持久任务的租户恢复 `ActorContext.withinJob`，禁止直接用 HTTP tenantId。验证入口为 ActorContextTests、ActorMethodSecurityTests、认证/TOTP/OpenAPI 回归与根 reactor；阶段测试不等于完整 P09 或真实外部服务验收。

P09 凭据基础：`sma.common.credentials.key-file` 仅保存部署加密环，不能作为跨租户外部账号配置。通过 `TenantSecretCodec` 加密并绑定 `TenantConfigurationRef` 和用途，调用前由所属模块检查配置权限/归属；匿名无默认租户，不能把 HTTP 中的引用直接交给 codec。密文、秘密和原始验证码不得返回或记录，密钥环轮换不自动迁移历史密文。验证 `TenantCredentialCryptoTests`（跨租户/配置/版本/用途、篡改、轮换、脱敏）。

P09 邮件配置：mail 拥有 profile/不可变连接 revision，管理接口和 service 同时验证管理权限及可信租户。`version` 用于管理并发，`configurationVersion/credentialVersion` 用于绑定连接快照/密文；停用必须在密钥不可用时仍有效并提升管理版本，防止旧页面重新启用。密码留空保留旧值、API 永不返回密码/密文，跨租户 ID 返回不可访问；沿用 HTTP200＋非零业务 code 的既有协议。验证 `MailProfileTests`，C2a 仅证明配置管理，不等于 SMTP/Google/云能力已交付；mail V1 之后只追加迁移。

P09 邮件模板：模板只支持已声明的 `{{name}}` 类型变量，拒绝脚本/表达式及活动 HTML；链接使用发布时固定的 HTTPS 主机白名单。draft 与 publishedVersion 分离，发布保存不可变正文及租户内账号版本，修改草稿不改变在途任务。管理/编辑/发布权限分离，预览不能记录变量或发邮件。验证 MailTemplateRendererTests、MailTemplateTests、MailProfileTests；mail V2 后只追加迁移。

P09 邮件队列：业务调用 MailSendApi 前必须有所属模块 MailSendPolicy 验证收件人/场景，对应模板与账号仍需租户/应用校验；默认拒绝。任务在同一数据库资源事务入库，跨库不能宣称原子提交。QUEUED/CLAIMED 才能取消，SENDING 租约丢失转 UNKNOWN，不自动重发。领取/发送开始/结果分别短事务，fence 防旧 worker 覆写；外部 SMTP 不占数据库事务。容量预占、日/分钟配额按账号锁内更新，终止原子释放预占。终止清理加密正文、保留独立用途 HKDF/HMAC 请求指纹核验幂等，禁止普通哈希暴露短码。回归 MailOutboxTests、TenantCredentialCryptoTests 与架构/i18n；mail V3～V5 后只追加迁移。投递、回执及定时调度交付状态见专项进度。

P09 SMTP：仅通过租户账号快照创建 Angus Session，部署 routes 精确限制主机/端口并检查全部 DNS 结果；TCP 固定到已批准地址，禁止 DNS 二次解析绕过。TLS/STARTTLS 必须校验证书及主机，不能降级明文或信任全部证书。总时限须小于任务租约、发送不能处于数据库事务中，并发有界；DATA 后不确定失败为 UNKNOWN。日志不得包含 SMTP 原始响应、地址、正文或凭据。验证 SmtpMailSenderTests、SmtpNetworkPolicyTests；本地协议通过不等于供应商投递/送达验收。

P09 identity 配置：realm/client 只能通过当前后台租户及显式 identity 权限管理；同名 code 允许跨租户复用，realm 关联禁止跨租户。client 的 realm/application/platform/code 不可改，loginHandle 只是公开随机入口标识。Google secret 只写入租户/配置版本绑定密文，API 不返回；原生客户端禁止配置服务端 secret。callback/Origin 使用精确白名单，不做前缀匹配。停用无需解密且提升安全版本，配置变更保存不可变修订；版本冲突不能覆盖。验证 IdentityClientRulesTests、IdentityConfigurationTests 及模块/架构/i18n；阶段状态见进度，配置验收不等于登录会话已完成。

P09 回执：OpenAPI 的 /open/mail/ 路由必须签名，即使机器应用原配置允许无签名；编码路径不能绕过，nonce/正文/时间继续校验。MailReceiptPolicy 按真实机器主体绑定租户/账号/应用/供应商，默认拒绝；供应商网关还需验证厂商原始回调，不能把桥接当原生 webhook。state 与 receiptState 分开保存，投诉/退信不可被送达覆盖；抑制键按租户/账号/应用隔离，入队和发送前检查。V6 后只追加迁移，旧发送器不能混合运行；MailOutboxTests、OpenAuthenticationTests、SignatureProtocolTests、MailDeliveryCompatibilityTests 为验证入口。

P09 邮件调度：仅经 TenantWorkQuery 分页发现租户，目录不向带认证的 HTTP 请求开放。后台在 ActorContext.withinJob 中恢复可信租户并检查最新安全状态，不用 HTTP tenantId 启动任务。调度默认关闭，开启后采用有界队列/并发；状态提交失败不能触发第二次 SMTP 发送。验证 MailDispatcherTests、MailOutboxTests、TenantWorkDirectoryTests；本地传输替身的数据库闭环不替代真实投递验收。

P09 Google 协议：RS256/官方 issuer/单一 audience/必要 azp/事务 nonce 和有限 NumericDate 同时验证，公钥不能来自 JWT header。固定官方 HTTPS 端点、禁跟随重定向、有限网络并发/响应体/总截止，禁止在数据库事务中访问提供方。identity.google-jwks 登记为受保护 NODE 区域，过期不降级、未知 kid 限刷，不缓存用户认证决定。GoogleProtocol/Verifier 为 internal，不构成可绕过持久一次性事务的二开登录 API；不得自动按同邮箱合并身份。验证 GoogleIdTokenTests、GoogleProtocolTests、GoogleHttpTransportTests 和架构/i18n；外部联调边界见进度。

P09 应用会话：主体按 tenant/realm/issuer/sub 唯一，不按邮箱隐式合并；只存访问/刷新/CSRF 摘要，WEB 与 NATIVE 不互换。锁序 realm→client→subject→session→refresh；配额和安全状态使用当前读，不依赖调用方旧快照。刷新重用独立提交撤销族，不因外层业务失败回滚；并发刷新只产生一个后继，重用会将其一起撤销。每次认证校验租户/域/客户端/主体代次，恢复开关不恢复旧令牌。公开主体创建事件不带凭据/邮箱，业务订阅按 subjectId 幂等并恢复可信租户。身份存储内部方法不能直接作为匿名登录 API。验证 IdentitySessionTests、配置/协议测试和根 reactor；identity V2 后只追加迁移。

P09 登录事务：入口元数据必须来自受信 Host/Origin/来源解析，不能直接把包含 origin/sourceAddress 的内部 Start/Completion 绑定为 HTTP body。state/proof 摘要、短期 nonce 密文、PKCE S256、客户端/回调/流程/配置快照一起绑定；VERIFYING 不能重新兑换，完成与会话签发同事务，正式 token 不入登录回执。登录修改先锁租户预算，再锁事务/authority/主体/会话；authority.current/match 只读校验的 BizException 由事务所有者决定是否提交失败状态，数据库异常仍回滚，不能扩展到写服务的通用 noRollbackFor。维护默认开启、分租户有界清理，测试关闭自动维护并显式驱动时间；禁止手改状态/删除预占。验证 IdentityLoginTransactionTests、IdentitySessionTests、IdentityLoginMaintenanceTests 及门禁；identity V3 后只追加迁移。

P09 应用 HTTP：可信 Host 先建立租户，appClient 查询参数只选择该租户客户端（含预检），禁止全局 handle 查询。WEB 使用每客户端独立 __Host Secure Cookie，写操作只能由 X-App-CSRF 提供证明；原生只用 Bearer，禁止 Origin/认证 Cookie 混用。identity 写入口实际 JSON 字节和并发有界；公开路径与方法显式列入白名单。Google azp 默认与 aud 一致，混合原生 SDK 仅允许单一显式 authorized party；Cookie SameSite/azp 使用不可变修订，旧客户端省略更新须保留配置。邮箱公开用途仅REGISTRATION/PASSWORD_RESET；WEB的注册/重置proof分Cookie保存且HttpOnly，拒绝JSON proof，Native拒绝同客户端挑战Cookie。verify业务拒绝在独立计数提交后抛出，不能把Controller包进通用回执/事务或缓存令牌响应。共享Cookie写入只负责传输，不绕过原签发服务。新增公开路由审查SecurityEndpointInventoryTests并复跑普通基线。验证ApplicationRequestFilterTests、IdentityEmailApplicationHttpTests、IdentityApplicationHttpTests、IdentityConfigurationTests、GoogleIdTokenTests、后台/TOTP/机器回归及架构门禁；完整宿主验收状态见专项进度，不能把MockMvc当真实Google/浏览器策略验收。

P09 邮箱基础：identity_email_realm_key 是身份索引安全状态，部署加密环轮换不能重新生成它；邮箱本地部分保留大小写/点/+tag，域名规范化规则固定，修改规则前需迁移/冲突方案。客户端语言模板绑定通过 MailTemplateQuery 的已发布契约校验，禁止跨 mail 表；元数据可用性不授予发送权。启用保存同时要求邮件模板查看权限，停用不依赖模板/秘密可用；绑定版本禁止旧写覆盖。PasswordStrengthPolicy 只供新登录密码写入，保留 IAM 旧 validate/错误码和历史密码验证，不用于后台独立安全密码。验证 IdentityEmailAddressTests、IdentityEmailFoundationTests、LoginPasswordPolicyTests、LoginPasswordWriteTests 及根架构/i18n；阶段状态见进度，基础配置不等于验证码/注册接口已交付。

应用安全链的身份恢复不是新登录事件：保留STATELESS、CsrfFilter的Cookie/Header校验和应用数据库会话CSRF绑定；通过CsrfConfigurer专用认证事件策略避免每次恢复主体时自动清除Cookie。真正登录/刷新由identity显式轮换CSRF，退出/密码重置/凭据变更显式清理，不能以禁用CSRF解决连续请求403。HTTP测试必须接受响应Set-Cookie后继续下一请求，不可只重放初始Cookie掩盖清理错误。验证ApplicationSecurityChainTests及IdentityAccountHttpTests、IdentityEmailApplicationHttpTests、IdentityApplicationHttpTests；不影响/api后台链。

P09 邮箱挑战：begin/attachDelivery 与真实邮件入队共用调用方事务；PREPARED 不是有效验证码。verify 使用独立事务并正常返回拒绝结果，不能在其事务内抛业务异常回滚猜测次数；缺钥/数据库故障拒绝且不得签发 grant。grant 的域、客户端、用途、Origin、目标与主体代次固定，消费回调只做同资源身份写入，与消费一起提交/回滚，禁止网络/独立提交/跨库。终态清除 targetEnvelope/codeFingerprint/grantHash，实体必须显式写 NULL；预算/速率/挑战均非可清理缓存。维护包含停用租户且不自动创建空预算。验证 IdentityEmailChallengeTests、IdentityEmailMaintenanceTests、模块/架构/i18n；状态层的合成 deliveryId 测试不替代实际 mail 联动，交付状态见进度。

P09 邮件有效性：Command 原8参数构造及无 guard 的旧指纹保留；新增 guard 必须持久化，找不到对应 MailDeliveryGate 时拒绝。Gate 在 mail 的新短工作事务内做非锁定只读查询，禁止领域行锁、独立事务、写入、回调 mail 或网络，避免锁顺序反转及连接池耗尽。identity 的保留发送策略必须核验真实 challenge/收件人/短码/模板/用途，不能只认 application；begin/入队/attachDelivery 同事务。发送准备与 SMTP 前复核，网络在事务外；旧 worker 不可消费 guarded 任务，回退不能删字段绕过。验证 IdentityEmailDeliveryTests、MailOutboxTests、MailDeliveryGuardsTests、MailDispatcherTests、MailDeliveryCompatibilityTests 与身份状态回归；本阶段状态见进度，发送替身不代表外部送达。

P09 邮箱凭据：新地址只由用途正确的已验证 grant 写入，密码恢复只能修改已有且已验证的密码凭据，不为 Google-only/不存在账号补密码；同邮箱 Google 身份不得合并。grant消费、主体/邮箱凭据/密码版本/会话撤销同资源原子提交，失败全部回滚。BCrypt只能在合法grant/账号校验后执行，CPU并发有界；不得改变后台独立安全密码规则。新会话记录真实认证方式/时刻，刷新不能更新它们；旧UNKNOWN/null会话可继续普通访问但不能冒充重新验证。验证 IdentityEmailAccountTests、IdentityPasswordWorkTests、IdentitySessionTests、IdentityLoginTransactionTests、IdentityApplicationHttpTests及根门禁；当前状态见专项进度，内部账号服务不可直接作为匿名路由。

P09 再认证：actor只从已认证应用上下文取得，不绑定为请求体。currentActor仅做权威读取/加锁，其noRollbackFor业务拒绝由外层状态机处理，写服务仍按失败回滚。一次性grant绑定原subject/session/client、代次、动作和目标，不能靠新会话时间、刷新或新邮箱验证码替代原账号证明；密码再认证与登录共享猜测预算，CPU计算在事务外。grant在realm锁后操作；消费EMAIL_BINDING/GOOGLE_BINDING时先取得对应原生预算，回调只能同资源修改，禁止引入倒序锁、网络或独立提交。保留量按realm/subject有界，终态擦除grantHash；共用邮箱维护且清理独立容错。验证IdentityReauthenticationTests、IdentityPasswordLoginTests、IdentitySessionTests、IdentityEmailMaintenanceTests及根门禁；实际绑定/Google流程以专项进度为准。

P09 Google操作：LOGIN、REAUTH、LINK_GOOGLE固定用途不可串用；再认证验证原主体当前issuer/sub，不能用email关联。新关联先消费原账号grant再创建新Google事务，两者同资源原子提交；网络与验签在事务外，finish复查actor后原子关联/撤销旧会话。历史外部关联保留归属，匿名登录不能恢复或转移；只有原主体显式绑定可以恢复，活动/历史容量都必须有界。旧实例不识别intent与历史保护，启用前须按手册8.11协调升级，不能混跑。验证IdentityGoogleOperationTests、IdentityLoginTransactionTests、IdentitySessionTests、IdentityApplicationHttpTests与根架构/i18n，阶段验收状态以进度为准。

P09 邮箱绑定/移除：EMAIL_BINDING必须先消费原账号再认证授权，再原子创建目标邮箱挑战；验证及完成HTTP均校验挑战保存的原actor快照，不能由另一会话消费验证码或改凭据。verifyBinding与普通verify一样独立提交拒绝计数，actor不符不消费正确证明；匿名用途白名单不包含绑定。已有密码不通过绑定覆盖，联系邮箱无密码不算认证方式，普通reset不补密码。移除前在realm锁内检查当前客户端可用的其他认证方式；删密码保留验证邮箱，删Google保留历史归属。凭据/主体代次/会话撤销/grant消费同资源原子提交。本人HTTP只从安全链取得actor，方式查询不接受subject参数；Google再认证/关联/邮箱绑定proof分Cookie，Native拒绝同客户端这些Cookie。验证IdentityAccountHttpTests、IdentityCredentialOperationTests、IdentityEmailChallengeTests、IdentityEmailAccountTests、IdentityReauthenticationTests及根门禁，当前状态见P09进度。

P09公开查询：业务只依赖identity根包SubjectQuery/SubjectContactQuery/SubjectReadRequest/SubjectAccessPolicy及已公布HTTP/事件，禁止调用内部签发或传“已验证”快照。current只验证当前APP_USER和精确audience；find/邮箱须所属application策略分别授权BASIC/VERIFIED_EMAIL，管理员/机器/job无默认豁免。策略接收原actor，检查真实业务归属/用途，禁止网络、递归query或业务写锁；缺策略拒绝、重复启动失败。tenant来自上下文，APP_USER还限制realm并复查会话；联系邮箱只取挑战验证的凭据，停用主体不返回联系地址，不能使用Google claim回填或缓存安全决定。教学目录examples/integration-services不进生产构建。验证SubjectQueryTests、SubjectAccessPoliciesTests、IntegrationServicesExampleCompilationTests及根门禁。

P09存储配置：file拥有租户profile与不可变revision，provider仅LOCAL/OSS/S3/R2。代码/provider不改，云三项凭据整体按tenant/profile/配置版本/凭据版本加密，API只回传配置状态/hint；留空保留并重新绑定密文，临时凭据不得只改截止时间续期。本地只选部署别名，不能让租户提交服务器路径；S3/OSS/R2各用已审查标准endpoint，运行时出站防护另由适配器验收，不把语法白名单当DNS/IP授权。stop分上传/读取且缺钥可用，version防旧写重新启用；管理保存无网络副作用，按LOCAL请求治理保护WRITE_ONLY指纹。阶段仅配置不代表云传输或扫描完成，验证StorageProfileRulesTests、StorageProfileTests、SecurityEndpointInventoryTests与根门禁；file V3验收状态见P09进度。

P09上传场景：Catalog与Policy按解代理后的真实静态模块包和ApplicationModule元数据核验归属，application对应模块ID；跨模块冒名/重复拒绝，缺Policy不准入。租户只发布登记范围内的格式/大小/方式及本租户授予该application的profile，不能关闭扫描。发布固定profile配置/凭据版本，草稿编辑不改变在途或旧发布；profile实时停用/应用撤权及租户停用仍拒绝新任务。准入锁序scene→按ID排序profile，后续任务预算在它们之前；policy不访问网络、不反向取得任务锁。内置file.image/file.attachment仅供后台，不给APP_USER/机器/job入口豁免。枚举/20GiB准入天花板不等于处理器或容量已验收。验证FileSceneRegistryTests、StorageSceneTests、SecurityEndpointInventoryTests与根门禁；file V4验收状态见进度。

P09上传任务/本地传输：新任务按ActorRef＋后台portal和tenant generation绑定，原生任务独立提交，预算→task→scene/profile→part锁序，不套common本地事务执行文件I/O。任务同键HMAC核验输入，Local当前每任务3倍声明字节预占；取消/过期不等于物理清理，不能自动返还预算。片RECEIVING不因超时重领；只有锁定检查到已关闭MISMATCH片，才允许一次新claim路径重试；ABSENT/BUSY保持未知/处理中。只有LocalTransferStorage.NotStarted代表线程池在I/O前拒绝，可显式释放未用claim，不能套用于普通异常/超时。根目录与父目录必须部署独占，使用逐级目录/链接检查、NOFOLLOW_LINKS/CREATE_NEW、文件锁、64KiB缓冲、并发/截止/磁盘水位；写入与冻结成功或对账匹配后刷盘，再提交数据库回执。冻结逐片及全文件摘要校验，仅进入SCANNING，绝不能直接READY。原FileUploadRequests回执行为继续保留。验证UploadTaskStoreTests、LocalTransferStorageTests、LocalUploadTests及原场景/根门禁，符号链接权限不足的跳过项必须注明；file V5/V6验收边界见进度。

## P08 缓存与多实例

缓存节点定向使用成对X-SMA-Cache-Node/Process，仅接受部署名单与当前进程；错路由503、旧进程409、缺字段/重复400，Controller及请求预算副作用前拒绝。选择仍需后台人类与缓存查看权，原动作/区域授权不能被替代；不得转发用户URL或把其他节点统计冒充目标。已有任务UNKNOWN仍查原回执，当前路由拒绝不能证明历史请求未执行。验证CacheNodeSelectionTests、CacheNodeRoutingHttpTests及原任务/Monitor回归；实际LB和multi资格仍看M3进度。

主键分配统一使用宿主`IdentifierAllocation`/已绑定的MyBatis assign_id和IdWorker；不得自行安装生成器或依赖ID排序代表时间。独立号段提交、升级停写和高位ID导入约束统一见[数据规则](../docs/agent-rules/data-and-configuration.md)。变更此基础设施回归IdentifierBoundaryTests、IdentifierConnectionTests、IdentifierBindingTests；破坏性GlobalIdMigrationIT只能在专用可清空库独立运行，不能与使用该库的应用或其他测试并行。

P08真实双进程发现的LOCAL受理容量竞争使用原3秒短事务内的有界等待；容量锁只覆盖回执预留，不跨业务执行，业务资源锁继续NOWAIT。禁止为消除503移除容量/并发预算、重跑UNKNOWN或延长为无界等待。回归RequestCapacityContentionTests、RequestExecutionTests、RequestAdmissionTests和真实双进程八并发写入；改动不等同集群总并发限额。

M2消息提示由Message保存可信收件人/portal/类型的有界合并版本，各节点分别读取本节点连接范围；不得改为全局抢占消费者、正文缓存或认证授权依据。数据库读取结束后再做WS I/O，提示失败依靠原授权REST补查；观察版本不代表客户端已收到或已读。会话提示须重新检查权威会话，不能无条件强退仍有效连接。Message V4已经执行，之后仅追加；验证SocketSignalProcessorTests、SocketSignalStoreTests、真实SocketTests及M2d双JVM广播。该状态登记PROTECTED，普通缓存管理不得删改。

Auth/OpenAPI维护通过TenantWorkQuery分页发现租户，包含停用租户；不得恢复seen集合或默认租户1兜底。维护使用可信job范围、数据库时间、有界批次和独立worker；只能清理过期安全状态，未过期nonce/预算不可退款。测试profile关闭自动维护并显式驱动。验证SessionMaintenanceDirectoryTests、SessionMaintenanceSweepTests、NonceMaintenanceDirectoryTests、NonceMaintenanceTests及原认证/TOTP/签名回归。

P10 租户入口、品牌、配置分层、业务数据源及 SQL 统计同时遵守[租户平台规则](../docs/agent-rules/tenant-platform.md)。数据源身份取实际 JDBC 连接，不能按当前路由猜测；平台/业务事务分离后须重新验证持久失效的事务归属。阶段交付见[专项进度](../docs/tenant-platform-progress.md)。

P10 S2入口与安全代次已通过本地专项：HTTP匿名不得回退租户1；后台任务的portal取可信持久会话，不能默认admin；租户恢复不能复活旧会话/验证码/MFA/机器bearer。核对TenantRequestFilterTests、TenantAuthenticationGuardTests、TenantAuthenticationTests、SocketTests及既有认证/P09回归；部署前读取auth V5/OpenAPI V3的重新登录和签发边界。S3a草稿schema/权限/版本已验证，保存不发布；后续不能让匿名入口直接读取draft_json。

P10 S3b素材与发布按[品牌契约](../docs/tenant-branding-api.md)和专项进度验收。File的ImagePublication必须在调用方事务内做源文件权限及行锁检查、受限栅格解码与PNG重编码；不能把原始文件content公开，不能声称重编码等同通用杀毒扫描。品牌引用保护不依赖进程缓存；发布/回退使用不可变修订和同事务回执，禁止修改旧修订或清缓存删除历史。

适用于基座和静态二开的跨请求缓存。新缓存通过 `common :: cache` 注册，声明归属、隔离、TTL、容量、失效来源及允许操作；安全状态不能暴露缓存清理。已有本地运行时缓存按 P08 逐项适配，不因过渡期忽略在途保护。公开接口不暴露 Redis/Caffeine 或本模块 internal；缓存不得替代对象/租户/权限校验。实施和验收顺序见[专项进度](../docs/cache-governance-progress.md)；契约验证入口 `CacheContractTests`、`ModularityTests`、`ArchitectureRulesTests`，最终仍执行根 reactor 标准验证与关闭粘性会话的双实例专项。

P08 失效必须通过 ManagedCache.invalidate 参与业务写事务；不得绕过 common_cache_revision 直接清 Redis/本地缓存。当前版本按租户/区域合并失效，不使用事件 ID 高水位跳过迟提交记录。背景核对用可信捕获范围及独立 READ_COMMITTED 事务；不能沿用业务旧快照。版本校验失败绕过缓存，不能放行旧权限/安全判定。回归 CacheRevisionIntegrationTests、ParameterCacheTransactionTests、DurableManagedCacheTests；Redis 整合使用专用端口显式运行 RedisDurableRevisionIntegrationTests。上述测试不是完整负载均衡验收。

P08 回源（含 None、事务绕过与版本校验失败）必须受统一并发预算约束；不通过无限等待或无限回源隐藏缓存故障。缓存锁使用有限等待；提交的最低权威版本不能被旧在途轮询覆盖。回归 CacheResilienceTests、DurableManagedCacheTests 与 I18nWebTests；回源预算不足使用统一 HTTP503，不能转成缓存空值或正常成功。

Redis 数据缓存只通过 common 的运行实现，字段/容量/代次在协议v2的同一租户区域Hash中原子治理；禁止模块直接操作客户端或复用默认namespace跨环境。切换需部署专属namespace、显式连接配置和正数服务端maxmemory，禁止自动执行CONFIG SET或全库清理。回归RedisCacheStoreTests、RedisManagedCacheTests、RedisProviderIntegrationTests及根reactor；standalone证明不能替代Sentinel/Cluster和LB专项。不可用统计用-1表达未知，前端不得显示为0。

Config字典缓存必须包含语言，所有类型/条目/翻译/初始化写入口在同事务失效；偏好只缓存原始当前用户记录，每次合成调用fallback及租户默认值。跨用户内部读取不复用当前用户槽位。更改这些语义或默认策略优先级时，回归LocalBasicDataCacheTests、NoneBasicDataCacheTests、RedisBasicDataCacheTests及CacheConfigurationTests；真实Redis专项需显式专用端口。

IAM展示缓存不得用于账号/凭据/角色权限有效性判断；部门结果键必须包含当前可信DataAccess范围，不能跨范围复用过滤后的结果。用户展示仅id/name，portal隔离；Profile更新/bootstrap与菜单sync属于必覆盖写入口，sync改变导航关联时同时失效导航区域。回归LocalIamDisplayCacheTests、NoneIamDisplayCacheTests、RedisIamDisplayCacheTests及IAM权限/登录专项；新增写路径必须补对应失效测试。

缓存治理目录走CacheRegions/CacheInspection，禁止Monitor依赖common internal或暴露原始key/值/连接参数。操作权限取操作权与区域managePermission交集，分组不能授予权限，旧清理接口同样受约束；未登记、保护或无能力区域不能清理。观察进程ID不充当集群租约；共享条目不跨节点重复求和。回归CacheDirectoryTests、CacheInspectionTests、MonitorTests与三种provider兼容。RedisProviderIntegrationTests使用默认逐方法生命周期，不能改成PER_CLASS使上下文在beforeTestClass清库之前启动。

P08运行治理状态由Common拥有：dataRevision/controlVersion/statsEpoch分别表达数据失效、并发编辑与统计窗口，必须一次权威查询取得一致快照，旧轮询不能覆盖已提交控制版本。统计重置不得清数据或延长TTL；Local保留聚合上限并执行租户配额，Redis读写必须拒绝落后版本借用新generation回填。控制内部存储不是二开API；公开入口按阶段门禁交付，不允许跨模块直接改表。回归CacheControlPersistenceTests、CacheRuntimeControlTests、CacheControlPropagationTests及原失效/回源契约；Common V5后仅追加迁移。

P08运行治理通过公开CacheGovernance，Monitor仅做协议适配；操作权和区域managePermission都须通过。必须携带expectedVersion和审计原因，冲突返回HTTP409；控制变更和不可变前后审计同事务，审计失败不能提交控制状态。Common V6后仅追加迁移。目录未确认时有效策略/暂停状态为未知，不能冒充全部节点已确认。回归CacheGovernanceTests、CacheGovernancePermissionTests、CacheControlInputTests及原运行层专项。

P08批量操作使用CacheTasks的服务端持久预览和原请求键；分组不授权，提交时再次验证目录摘要/控制版本。RequestExecution只负责同库受理事务，不能把Redis操作或预热放入Work。预览/任务必须按可信租户＋发起人＋portal隔离；不保存权限快照，不清除已受理账本来释放容量。Common V7后仅追加迁移。S4c1仅完成受理；未有执行器时不得开放让用户误认为已经执行的HTTP入口。回归CacheTaskPlannerTests、CacheTaskBoundaryTests、CacheTaskSubmissionTests、RequestExecutionTests与结构门禁；阶段边界见专项进度。

P08任务执行采用持久租约/fence和每项最多3次尝试，禁止旧worker覆盖接管结果；权威变更与APPLIED同事务。执行前通过IAM的CacheTaskActorResolver重建最新权限/范围，并核对租户安全代次，不能把SPI用于HTTP身份切换。拒绝结果必须在原事务回滚后另开事务记录；提交确认丢失先查项状态。调度使用独立有界线程，不占共享定时线程，不从请求历史推断租户；测试profile禁自动消费。回归CacheTaskExecutionTests、CacheTaskActorTests、CacheTaskDispatcherTests；V8后只追加迁移。APPLIED不是节点确认或物理回收证明。

P08任务HTTP使用原生CacheTasks协议，不能再加GovernedApi导致重复账本或嵌套本地事务；Controller的view只是入口检查，服务必须按持久动作检查操作权和区域管理权。提交不接受客户端覆盖预览目标，重放保留原Idempotency-Key，响应丢失先读原任务；操作日志区分预览/受理/取消，不能以HTTP200替代任务结果。回归CacheTaskHttpTests、原CacheGovernanceTests与MonitorTests；新增动作同时扩展能力、权限、迁移和拒绝路径。

P08节点的全局例外只包含Common拥有的CacheNodeGroupEntity/CacheNodeLeaseEntity（框架部署元数据，继承BaseEntity）；用户任务和确认数据仍须TenantEntity，不能借此给业务表免租户。节点全局写入仅接受无请求身份的宿主心跳，普通用户和tenant job均拒绝，无HTTP写入口。清单version只能前进、代次绑定进程，提交共享锁不能阻塞同进程续租；不得把UUID当Snowflake节点ID。旧任务无node_scope_json表示未知，不能用当前清单伪造历史覆盖；stateAtCapture仅为历史快照。回归CacheNodeMembershipTests、CacheNodeIdentityTests及原任务/结构门禁；Common V9后只追加迁移。自动确认与部署资格按专项进度验收。

P08节点确认请求与任务APPLIED同事务发布，发布失败同时回滚效果与证据；应用Local/Redis状态在确认数据库事务外完成，不能持租约行锁访问Redis。报告只接受精确cache-node进程job，并重查当前租约/epoch/清单和数据、控制双版本；旧确认保持不可变。历史确认与当前在线分开，未知快照不得绑定新进程，禁止宣称已回收物理内存。心跳/确认独立有界执行，宿主入口拒绝请求身份。回归CacheConfirmationTests、CacheNodeRuntimeTests及原任务门禁；Common V10后只追加迁移。

P08预热仅由CacheWarmupLoader注册所属模块的幂等只读实现；版本、业务读取权限、页/总条数/速率/时长必须有界。拒绝私有区域与重复加载器，枚举保留可信租户/数据范围；回填在实际事务外，经ManagedCache正常读取，不接收HTTP游标/SQL/脚本。加载前和检查点重验最新身份、fence、双版本、取消与截止；成功页重置尝试，换进程明确失败。字典主动回源用3秒/512项同快照上限，普通读取不改；不把检查点读取数当驻留数量，不发布预热的全节点失效确认。PREWARM须所有读端/worker升级后显式开启，关闭不能清账本或直接回退旧枚举解码器。验证CacheWarmupContractTests、DictionaryWarmupLoaderTests、CacheWarmupExecutionTests、RedisCacheWarmupExecutionTests及基础数据/结构门禁；Common V11后只追加迁移。

P08生产缓存注册必须由CacheRegistrationOwnership校验真实模块与Modulith元数据，不能仅信任owner字符串；业务管理权使用自己的命名空间，加载器必须属于区域同一模块。CacheConfiguration及Spring加载器生产装配不得省略校验。common/internal/cache以外禁止依赖Redis/Lettuce/Jedis/Redisson/Caffeine/Spring Cache/JSR Cache；通过公开契约或审核适配器接入。验证CacheRegistrationOwnershipTests、CacheBoundaryTests、配置绑定和真实宿主任务/预热回归；该约束不替代可信源码评审。

P08非DATA观察通过CacheResourceAdapter/CacheResources，提供者仍须真实归属校验；PROTECTED不接受适配器/普通变更。统计保留原业务权限、数据范围和租户隔离；拒绝/故障/缺样本为未知，不泄漏异常消息、值、路径或连接。规则观察委托原ALL范围检查，IP库不可close，文件持久资源不作为缓存删除。验证CacheResourceRegistryTests、CacheResourceDirectoryTests、Monitor/任务及结构门禁；原生操作按S5c专项另行验收。

P08运行时原生操作只允许登记的RUNTIME CLEAR/RESET_STATS，默认关闭部署开关；冻结进程与适配版本，开始/结果短事务分离，适配器副作用不得处于任务事务或执行业务/网络写入。所属模块保留原权限/范围/在途保护。已开始未获确认转UNKNOWN，接管不得重做；未开始到路由截止明确失败，不改派其他进程。旧SPI默认拒绝变更，全部读端/worker兼容后方可启用，关开关不消除历史载荷。Common V12后只追加迁移；回归CacheNativeResourceTests、RuleCacheAdapterTests、CacheResourceRegistryTests、CacheTaskPlannerTests及任务/预热/HTTP门禁。

P08共享Socket票据由Message自己的租户表管理，保留可信tenant/portal/session绑定、短TTL及仅摘要存储；消费原子删除并独立提交，之后再做Auth权威校验，异常不恢复票据。容量在租户guard下跨节点约束，业务缓存配置和清理不能影响该安全状态；不得跨Auth表或用本地Map降级。连接限额仍属节点，实际跨JVM与广播资格按P08进度。Message V3后仅追加迁移；验证SocketTicketTests、真实SocketTests、Auth/Message请求治理HTTP及架构门禁。

P08机器登录预算在凭据查找前预占共享来源及来源＋AppId窗口，独立提交且不因成功/失败退还；平台计数上限10000，单次清理最多200个过期项。HMAC密钥独立生成并由部署主密钥加密持久保存，密码学/密钥文件读取不跨预算事务；密钥缺失但已有计数、损坏或代次不符均拒绝，不能重建密钥绕过预算。精确平台表例外及禁止复用见数据规则，正常应用/令牌/nonce仍租户隔离。OpenAPI V4后仅追加迁移；验证OpenLoginBudgetTests、OpenAuthenticationTests、SignatureProtocolTests、PlatformPersistenceIsolationTests与普通SecurityEndpointInventoryTests。

P08会话活跃/锁屏更新只写必要字段：自动锁屏须比较观察到的lastSeen和数据库时间，活跃时间用GREATEST保持单调；更新后重读并校验会话，不能用旧实体覆盖锁屏/解锁/撤销。手动解锁按账户安全锁→会话行锁顺序，保留原密码/租户/安全代次验证，不更改认证证据。回归SessionConcurrencyTests、AuthFlow/SessionManagement/密码/TOTP/Captcha/Socket与普通接口清单；已通过鉴权的在途业务不因后续撤销自动回滚。

## P06 规则引擎



业务通过 `RuleExecutionApi` 传业务快照，绑定对象由业务归属模块实现 `RuleBindingPolicy`，没有授权适配器时拒绝。表达式、流程、决策表保持类型、只读和执行边界，禁止放开任意脚本、Java 类/Bean 调用或业务写副作用。草稿保存、模板升级和批量草稿迁移不自动改变生效指针；按试算/启用用例、发布和回滚接口处理，已发布内容和回执不可覆盖。改模型时回归旧 JSON、固定依赖、在途快照和绑定权限。详见[规则引擎运行规程](../docs/rule-engine/operations.md)。

演示绑定仅在`sma.rule.demo.enabled=true`显式开启时注册，保持默认关闭。`RuleDemoBindingPolicy`只允许固定`demo_rule_*`场景白名单、`RULE_DEMO`类型以及`demo:<可信tenantId>:<固定尾缀>`对象，并要求后台人类身份及规则权限；不能扩展为真实业务对象的兜底授权。示例数据创建须用户授权并走正常后台，不把测试身份或直接写表用作生产初始化。范围、清单与本地部署边界见[演示数据](../docs/rule-engine/demo-data.md)，验证`RuleDemoBindingPolicyTests`、`RuleDemoDataTests`、`RulePlatformTests`及架构/模块门禁。

规则菜单由 `RulePermissionCatalog` 声明为 `rule:center` 目录及既有5个查看菜单、17个操作权限。升级归组只移动仍在根级且目录元数据匹配的内置资源；保留旧ID、角色叶子授权、管理员开关/自定义父节点，补齐已授权资源的新目录祖先与原导航组关联。不得借菜单同步授予角色其他业务操作。验证入口 `RuleMenuTests`、`PermissionTests`、`AccountSecurityMenuTests`。

## 验证

权限图标为展示元数据：PermissionQuery.icons及auth/me.permissionIcons必须复用与keys相同的用户角色、portal、租户、禁用祖先及superAdminDefault过滤，不能为取图标要求授予资源管理权限或扩大授权。旧PermissionQuery实现可返回默认空映射。icon仍是≤64字符字符串；none表示管理员明确清除，内置目录同步不得将其当空值补回。回归PermissionIconTests、PermissionTests和AuthFlowTests；前端图标ID升级需配套发布，未知ID仅降级显示。

个人外观偏好：config 的 skin 仅允许 iris/celadon/amber，默认 iris，与 theme 独立；按当前用户/租户保存。旧请求省略 skin 必须保留已有值，reset 恢复默认；auth/me 返回同一偏好值。保持 PreferenceView 原构造器可调用，不修改已发布迁移。回归 PreferenceTests 与隔离真实前端 skin-theme.spec.ts，部署顺序为后端（config V4）先于新版前端。

账号安全菜单通过 AuthPermissionCatalog 声明 `auth:account-security:view`，挂在安全管理目录，新增时启用且超管默认授权；同步不得覆盖管理员后续的禁用、隐藏或默认授权设置。此权限控制导航菜单，个人账号安全 API 继续使用人类会话与自身归属/重新认证校验，不能赋予操作其他账号的能力。回归 AccountSecurityMenuTests。

P07 认证安全扩展按[实施进度](../docs/auth-security-progress.md)分阶段验证。修改认证时遵守：TOTP 未完成前只发用途受限挑战；正式会话统一校验凭据/安全版本及认证方式，锁屏解锁只恢复原有效会话、只验证登录密码且不续期。动态码时间步和挑战在同一事务原子消费，猜测预算单独提交不能随业务异常回滚。密钥不得写入数据库配置、日志或前端；部署格式见[认证安全运维](../docs/auth-security-operations.md)。新增登录或解锁入口必须回归 TotpAuthenticationTests 的拒绝路径，不得让旧入口或超管绕过。密码策略后续阶段以进度中的已验收范围为准。

P07 后端密码规则已验收：新登录密码 8～12 位字母数字＋弱密码拒绝，所有创建/初始化/修改/重置调用 IAM 统一策略；安全操作密码 6～8 位字母数字，不能用它替代 TOTP。旧登录密码验证不套新设长度。两种密码写入遵守 IAM 凭据锁 → auth 账号锁顺序，IAM 通过自己的 AccountPasswordPolicy SPI 同步检查，禁止反向依赖 auth 或异步补救同值写入。回归 LoginPasswordPolicyTests、LoginPasswordWriteTests、SecurityPasswordTests，特别保留并发同值及旧事务快照撤销用例；不为测试放宽生产密码规则或 OTP 窗口。

密钥轮换按租户限量加锁，不能改变绑定状态或降低已接受时间步，keyId 比较必须区分大小写；回归 AuthSecurityCryptoTests、AuthSecurityPersistenceTests。恢复脚本仅限隔离的恢复库，先核对备份之后的安全变更再撤销全部会话/挑战，禁止把旧备份中的未绑定状态当成密码单因素豁免。AuthSecurityMigrationIT 为独立清库专项，与其他数据库测试串行运行；运行方法及退役旧键条件见认证安全运维文档。

模块结构与自动规则见 [ModularityTests](src/test/java/com/sma/admin/ModularityTests.java) 和 [ArchitectureRulesTests](src/test/java/com/sma/admin/architecture/ArchitectureRulesTests.java)。修改 API、权限、事务、迁移或规则引擎时，同时验证受影响消费者、拒绝路径、跨租户和重试行为。在仓库根目录执行标准验证脚本；专项用例可用 `backend/mvnw.cmd -f pom.xml -pl backend -am -Dtest=ModularityTests,ArchitectureRulesTests -Dsurefire.failIfNoSpecifiedTests=false test`，该命令只验证指定用例。

P09公开上传：业务仅依赖file根包FileUploadApi，不能调用内部Store/Coordinator。后台/api/file/upload-tasks按NATIVE治理，应用/app/file/upload-tasks逐方法Self白名单；二者均由file场景策略和原任务actor/realm/client/portal复查。认证后UploadTaskRequestFilter限制16并发、64KiB JSON、实际二进制字节与唯一尺寸/摘要头，不扩大旧multipart上限；WEB继续CSRF，Native继续拒绝Origin/Cookie。流式putPart/complete禁止外层数据库事务，SCANNING不等于可读，取消不等于释放配额。旧/api/file/uploads回执继续保留。验证UploadTaskHttpTests、UploadTaskRequestFilterTests、FileUploadHttpTests、ApplicationSecurityChainTests、普通SecurityEndpointInventoryTests与根门禁；独立教学样例编译用IntegrationServicesExampleCompilationTests，阶段证据见P09进度。

P11 X3适用于接口预算清单与后续新增写入口：EndpointBudgetCatalog登记shared/node、真实所属模块/物理表或进程内存、隔离键/条件/事务语义和N实例换算；不能把shared称作全部独立提交，不能把BrandWorkBudget虚构成数据库表。SecurityEndpointInventoryTests把目录与编译端点绑定纳入受审基线；EndpointBudgetCatalogTests核对编译实体/默认节点名额/部署换算表，普通构建禁止静默更新。只在审查后临时使用sma.security.review-policy-baseline=true，随后关闭复跑；缺少目录预算须明确none-reviewed，不代表无限容量或WAF已部署。X3不合并预算、不迁移权威账本，双实例真实性由P08/X5验收。同步docs/development-schedule与所属进度；文档示例表修改与运行默认值需同批验证。

P09内容worker：FileContentProbe只提供完整字节候选，不能作安全/READY结论；复杂解析仅在独立ContentInspectionWorker执行，不注册Bean或作为业务API。唯一标准输出例外只用于16KiB有界协议，禁止输出客户内容/解析异常；源格式/大小/摘要/引擎与衍生预算必须绑定。图片固定解码器白名单，PNG/JPEG/GIF/WebP完整框架及逐帧检查，再像素复制为无原元数据PNG；GIF静态首帧衍生，APNG/动画WebP明确拒绝。输入/像素/累计帧/输出均有界，不能覆写衍生路径；输出资源故障是ERROR而非内容恶意判定。生产必须受控隔离执行器，当前独立JVM测试不是OS禁网证明，尚未接发布。验证FileContentProbeTests、ContentInspectionWorkerTests、ImageContentInspectionTests、BoundedInspectionProcessTests、架构/普通端点/模块门禁，状态见P09进度与开发调度。

P09档案/Office内部检查：ZIP在第三方解析器建目录前限制真实中央目录条目与字节边界；核对本地/中央头、数据描述符、实际Inflate尺寸/CRC/SHA256，禁路径/链接/加密/多卷/别名/重叠与未知扩展，不能只相信声明大小。OPC必须核验内容类型、根关系/主XML，禁外部关系、宏/ActiveX/嵌入对象、危险域/公式与执行动作，XML禁止DOCTYPE/外部实体/stylesheet并限制节点/深度/字符。Manifest/媒体清单只是内部结构结果，未验证叶子和完整杀毒不得连到PASS/READY；不能让业务引用内部类或以改后缀绕过。检查不在宿主请求线程解析，不提取用户命名路径；验证ArchiveInspectionTests、OfficePackageInspectionTests及架构/普通端点/模块门禁，实际完成范围以P09进度和开发调度为准。

P09包内内容与PDF门禁：根包/嵌套包共用累计展开/条目/深度预算并先向容器层传余量；所有叶子内容或Office媒体均验证才可内容PASS，缺处理器ERROR。worker临时提取只用随机路径与NOFOLLOW_LINKS，按Manifest大小/SHA复查，完成/拒绝删本人临时项，强杀残留由宿主清理；不得对条目名拼宿主路径。PDF固定strict parser、检查当前xref对象与解码流、禁加密/动作/脚本/附件/外部文件/表单，限制对象/字符串/流/页/token及图像尺寸，未知过滤器不可放行；库可能预分配，必须保留进程堆/截止和OS隔离。PdfContentInspectionTests验证转义键/不可达索引对象与ZIP同门禁，ArchiveContentInspectionTests验证全树额度和整包失败；配合内容/架构/普通端点回归，不能据内容PASS宣称病毒扫描或READY完成。

P09视频门禁：Mp4ContainerInspection在ffprobe前核验非分片容器边界、仅self-contained dref和sample引用，禁未受审fragment/DRM；VideoContentInspection命令仅从部署路径与冻结源生成，HTTP/租户不得传命令选项。固定mov/file、enable_drefs=0/use_absolute_path=0、线程/分配/probe/流数及30秒/16KiB输出上限，再验证编解码/时长/尺寸/帧率/音频/源大小。元数据探测不是逐帧解码或杀毒，单次分配上限不是总内存，生产还需OS隔离。真实NativeVideoInspectionTests须显式sma.file.test.ffprobe，未配置时记录跳过不能声称原生验收；缺执行器保持ERROR。验证VideoContentInspectionTests、NativeVideoInspectionTests、ArchiveContentInspectionTests、worker/架构/普通基线，部署状态见P09进度/调度。

P09 worker制品：file-inspection-worker profile仅按白名单打包inspection及纯Java helper/契约，独立依赖不带Spring宿主/配置/DB驱动/业务模块；Maven测试scope仅为选取WebP解析库，不可把全部test classpath复制到生产。新输出构建并运行tools/dev/verify-inspection-bundle.py，验证JAR/库归属、独立JVM与衍生摘要，记录verification.json；普通package或测试类路径启动不构成独立制品验收。二开不直接启动worker，镜像/禁网/OS限制另须真实验证，不把制品通过替代隔离。profile变更复验真实包和协议，不改变宿主默认构建入口。

P09输出握手：--hold-output仅接受有界单行请求/报告，报告后等待0x06 ACK；EOF/错误ACK不得报告成功取回，等待继续受watchdog与宿主总截止限制。ACK不授予READY。宿主必须在ACK前验证固定衍生文件的类型/大小/摘要，不能为简化复制改用无限制宿主输出挂载；未知容器生命周期不得仅杀CLI就释放容量。修改协议回归InspectionFramesTests、InspectionHoldOutputTests、原worker/进程测试及独立制品脚本；真实容器tmpfs与OS限制另行验收。

P09镜像上下文：只经tools/dev/prepare-inspection-image.py复制已验证worker＋精确17库，不能把仓库根/宿主JAR/配置作为构建上下文。依赖版本升级同步inspection_artifacts.py并复验；真实runtime pin由部署审查提供，不把测试合成digest写成生产默认值。工具检查摘要格式和文件证据不替代镜像来源/签名审查；Dockerfile低权限默认不等于运行时OS隔离。修改后运行test_inspection_image.py（实际包通过SMA_INSPECTION_CONTEXT_TEST_BUNDLE显式选择）及独立制品验证；真实Linux构建和禁网/限额/清理另验，状态双写P09进度与调度，方法见手册11.9。

P08受控schema入口由common::deployment的DeploymentSchemaCommand在宿主main启动业务上下文前执行，宿主传真实应用类型；Common不得引用宿主根类形成依赖环。MigrationApplication不得添加组件扫描、业务Import、Web或worker；沿用Modulith模块标识与历史表/基线0，禁止clean/repair及任意外部SQL路径。validate必须拒绝pending/missing/future/checksum异常和缺失模块，不因禁用Flyway跳过；默认单机migrate兼容保留。回归MigrationCommandTests、显式独占专用库的ControlledMigrationMysqlIT、模块/架构门禁及实际制品双实例SMA_CLUSTER_SCHEMA_MIGRATION=1（DDL权撤销后服务只校验）。完整multi资格仍按P08阶段；迁移命令不执行Java业务初始化。

P08部署证据仅供无请求身份、无tenant job且无活动数据库事务的宿主调用；common::deployment的DeploymentEvidenceSource返回固定域及定长摘要/不可用状态，禁止返回密钥、连接串或原配置。来源须真实模块归属；总摘要包含域集合及活动/保留键，缺域或异常不得当作一致。四类密钥适配必须复用实际运行对象，不重新读文件伪称已热换钥；OpenAPI预检不得建目录/生成钥，本进程自动生成的本地钥不授予集群资格。尚未接入持久租约/健康门禁时不能据Snapshot.ready宣称可接流。回归DeploymentEvidenceTests、DeploymentKeySourcesTests、DeploymentEvidenceAssemblyTests、原Auth/TenantCredential/RequestFingerprint密码学及受影响持久账本/模块门禁；阶段状态双写P08进度与总排期。

P11 X1业务账本接入只适用于静态业务模块与宿主存储运行时：业务通过`common :: request`的BusinessLedger在事务开始前按可信租户及已登记storageGroup打开会话，try-with-resources关闭；宿主实现BusinessLedgerProvider并用BusinessLedgerResourceFactory把业务Mapper与本地事务固定到同一DataSource。缺provider/绑定、跨租户会话、错误DataSource、缺租户/数据范围/全表/审计拦截器及非本源事务写入均拒绝。业务不得提供provider、直接调用Common内部Mapper或把业务执行权写进平台库。业务库框架表只从`db/business-ledger`独立Flyway位置迁移，不能把平台Common全套表复制进业务库；Outbox稳定键同内容可重试、异内容拒绝，权威代次事务外读取；汇聚年龄上限只报告stale，S6/S7另行装轮询/派发。X1a～X1c已本地验收，验证BusinessLedgerBoundaryTests、BusinessLedgerMysqlTests（专用双库且仅精确业务库可清空）、RequestExecutionTests、CacheRevisionIntegrationTests、ModularityTests及架构门禁；S6开工门禁以开发调度为准。

P10 S6a平台业务存储目录新增`StorageResourceEntity`和`StorageBindingEntity`两个受审全局元数据例外，仅保存资源修订/目标/secretRef和ownerTenantId＋storageGroup指针，禁止借此放宽业务表租户过滤或保存连接密码。Tenant V6只追加；内部登记需平台部署身份，目标必须命中部署白名单，绑定先保持PENDING，S7迁移完成前不可提供业务连接。验证StorageDirectoryMysqlTests（仅精确专用可清空库）、PlatformPersistenceIsolationTests、ArchitectureRulesTests和ModularityTests；状态见租户进度。

P10 S6b宿主池只接受目录ACTIVE＋v1绑定，按物理资源ID/修订复用并固定连接身份、框架Mapper和同源事务；节点/主机/单池预算及租约排空不得被租户数量放大。`tenant :: storage`的StorageCredentialResolver只供部署适配器从secretRef取凭据，缺适配器时拒绝，不把密码写目录/日志或前端。池修订描述漂移拒绝原地换库；旧池在租约关闭前不能释放。验证StoragePoolRegistryTests、StorageLedgerProviderMysqlTests（专用双库）、DataSourceIdentityTests、SqlDataSourceTelemetryTests及模块/架构门禁。

P10 S6c静态业务Mapper使用独立工厂与BusinessRoutingDataSource，只在BusinessLedger会话的executeLocal/query作用域取已固定物理源；会话外、异源事务及嵌套切源必须拒绝，不能回落平台库或借普通@Transactional宣称跨源原子。业务写与框架账本使用同一物理资源事务，SQL统计取实际连接的DataSourceIdentity。验证BusinessMapperRoutingMysqlTests（平台＋两个精确专用业务库）、RequestExecutionTests、CacheRevisionIntegrationTests、SqlDataSourceTelemetryTests和模块/架构门禁；正式资源激活与HTTP管理仍按S7/S6d验收。

P10 S6d资源目录HTTP只对平台部署运维身份开放，并分别要求tenant:storage:view/register/bind；写入只创建REGISTERED资源和PENDING绑定，资源响应不得回显secretRef或密码。LOCAL写入须保留Idempotency-Key回执协议；用户可见的资源运维页归S9，实际业务库迁移/激活归S7。验证StorageDirectoryHttpTests、StorageDirectoryMysqlTests、StoragePoolRegistryTests、StorageLedgerProviderMysqlTests、BusinessMapperRoutingMysqlTests、SecurityEndpointInventoryTests和架构/模块门禁；真实平台＋两个业务库仅使用精确专用库。

P10 S7a业务库迁移仅由平台运维任务在平台事务外触发，固定已登记目标/secretRef，临时连接和迁移并发有界；目标MySQL命名锁与独立`db/business-ledger` Flyway历史保护共享资源。已有业务表按版本0建立框架历史，不得清库、自动repair或导入平台全套迁移；迁移成功不能直接使PENDING绑定变ACTIVE。验证StorageMigrationMysqlTests（仅精确专用业务库）、架构/模块及后续空库/续跑专项，阶段状态看租户进度。

P10 S7b开通先由平台短事务在Tenant V7任务表领取原绑定ID/版本/资源/修订与fence，事务外执行物理迁移并核对四张框架表，最后原fence、PENDING和版本仍匹配才原子改ACTIVE/SUCCEEDED；失败记NEEDS_REVIEW，同键核对后续跑提升fence，旧执行者不能覆盖。任务表仅平台租户元数据，不得放业务行/密码；HTTP入口和换库各归S7d/S7c。验证StorageProvisionMysqlTests、StorageProvisionServiceTests、StorageMigrationMysqlTests及模块/架构；冻结专项证据和根全量测试编译边界见租户进度。

S7b任务的NEEDS_REVIEW/过期RUNNING不能通过普通provision自动续跑；先核对目标物理库及原意图，再由平台运维显式调用retryAfterReview提升fence。原键已完成只返回SUCCEEDED，不再次迁移。验证StorageProvisionMysqlTests、StorageProvisionServiceTests及根reactor专项，当前23项结果见租户进度。

P10 S7c-1在`StorageLedgerProvider`返回业务会话前按可信tenantId＋storageGroup登记本节点租约；本节点停写门禁拒绝新会话并释放已借池租约，最后一次关闭后计数归零。计数不代表集群排空，S7c-2持久停写代次及各节点证明未通过前禁止切库。验证StorageSessionTrackerTests、StorageLedgerProviderMysqlTests、架构/模块及后续真实多节点专项。

P10 S7c-2的Tenant V8会话租约与`DRAINING`门禁同绑定行锁提交；关闭失败保留租约，不能用计时过期替代核对。`common :: deployment`仅暴露宿主受控成员身份，离线/替换/未知节点均不得作为已排空；外部物理复制SPI必须实际核对备份、行数摘要及Outbox，缺实现拒绝。READY仍为停写状态，S7c-3验证版本化切换前不可改ACTIVE。验证StorageSwitchMysqlTests、StorageSessionTrackerTests、ArchitectureRulesTests和ModularityTests；本地通过范围及真实双进程缺口见租户进度。

P10 S7c-3切换须在平台短事务内复核READY后的新鲜物理证明、全节点租约/排空、持久会话为零及旧绑定版本；目标ACTIVE与SWITCHED同事务提交。切前可ABORTED恢复并提升绑定版本，切后回退只允许新反向迁移任务，不允许改回原资源。生产备份及全业务表复制核对须由受控部署实现`StorageMigrationEvidence`，本地双库探针不能替代；验证StorageSwitchMysqlTests及架构/模块，真实多进程归X5。

P10 S7d-1业务Outbox在同物理资源V2表以持久fence/租约领取，投递在事务外执行，成功原fence确认；UNKNOWN到期可重投，消费者必须按稳定键幂等。宿主只轮询已打开且有预算的业务池，缺消费者或歧义不得置DONE。领取可由Common受控资源内JDBC读取持久tenantId，不能借当前HTTP租户推断；代次按物理资源/修订/可信租户汇聚，失败stale。验证BusinessOutboxDeliveryMysqlTests、架构/模块；本地双库结果见租户进度。

P10 S7d-2开通和换库HTTP使用NATIVE原生任务协议；部署平台身份之外仍要求tenant:storage:provision/switch，GET回执要求view。失败后先GET核对，普通同键provision不重试NEEDS_REVIEW；显式审查后重试提升fence。换库各阶段必须按原任务状态执行，缺物理证据不得HTTP提交布尔结果。新增端点复跑普通SecurityEndpointInventoryTests、StorageDirectoryHttpTests与架构/模块。

P10 S7d-3首次非默认平台租户启动时，IAM管理员、权限与Config内置种子都应在公开PlatformTenantQuery给出的租户作用域执行；不得把无身份后台默认1视为平台ID。只在独立空平台测试库启用NonDefaultPlatformBootstrapTests，核对目录/域名/种子不落ID=1；已有平台库改ID需迁移，不准直接改部署配置。根reactor回归和隔离专项结果分别记录。

单实例二开B1要求所有手工MyBatis工厂（平台、静态业务、动态物理账本）显式注入同一宿主IdentifierGenerator；仅注册Spring生成器Bean不够，MyBatis工厂不能回退默认算法。动态建池后继续验证实体ASSIGN_ID、公开IdentifierAllocation和IdWorker仍来自平台持久号段，业务库不创建第二份号段。回归IdentifierBindingTests、IdentifierBoundaryTests与真实三库BusinessMapperRoutingMysqlTests；失败不能归为无关夹具。

单实例二开X2a业务缓存走BusinessCacheCatalog/BusinessCache；独立业务目录不混入平台CacheRegistry，PROTECTED拒绝，实际模块/权限/存储组归属必须校验。先按可信租户打开BusinessLedger固定资源，再取本业务库代次；loader只在该会话只读事务运行，禁止平台版本表替代。源ID/修订参与命名空间，新解析不串旧源；外层事务拒绝，写失效走Session.invalidate。平台ManagedCache.invalidate在业务路由中拒绝。回归BusinessCacheServiceTests、CacheRegistrationOwnershipTests、BusinessCacheMysqlTests（真实三库＋共享Redis高端口/精确键清理）及架构/部署证据；X2a不替代多Redis/X5。

业务schema目录通过common::persistence.BusinessSchemaCatalog按真实业务模块声明存储组，固定位置db/business/<module>及独立历史；禁止HTTP传入SQL/Flyway路径。正式StorageProvisionService从持久Work取storageGroup，完成框架＋业务schema才能激活，缺目录/脚本保留NEEDS_REVIEW。迁移失败不clean/repair，重入保留已提交DDL和业务行。StorageMigrationService两参数方法只用于内部框架迁移，不可代替正式开通。验证BusinessSchemaDirectoryTests、StorageProvisionServiceTests、真实Migration/ProvisionMysql及隔离教学样例；平台迁移位置不放客户业务脚本。

P08配置证据的12域集合必须齐备，新增影响跨实例授权/共享状态的配置由所属模块补白名单；DeploymentFieldInventoryTests要求已登记record新增字段显式审查，不能为通过测试默认序列化整个record。集合规范排序，null/空品牌值区分；实际有效缓存策略/格式/能力参与，私有路径及每节点Redis ACL值排除，物理存储仍须互证。Tenant运维名单、入口和逐字段品牌默认值不能只比revision名。回归DeploymentSettingsTests、TenantPolicyEvidenceTests、字段清单、DeploymentEvidenceAssemblyTests与真实消费者/模块门禁；Snapshot.ready仅代表本地集合齐备，持久租约及健康门禁按b2c/M3c验收。

P08持久部署证据由Common的DeploymentEvidenceStore独占写入既有group/lease的证据列；入口先拒绝HTTP/身份/外层事务，再在3秒READ_COMMITTED短事务按group→自身lease锁顺序核对。文件/密码学收集必须在事务外；基线只能指定首节点初建、同代次不可覆盖，清单换代依赖原租约退出规则。证据绑定nonce/process/epoch，最多15秒且不超过租约；心跳不能自动续证，源不可用只撤销自身资格。回归DeploymentEvidenceStoreTests、DeploymentEvidenceMigrationIT、原节点/确认与平台隔离/SQL/模块门禁；不得把内部MATCH称为流量就绪，c2/c3及M3后续仍须验收。

P08证据运行时仅在显式evidence.enabled开启时装配，要求真实节点租约、至少两节点及eager schema验证；一个独立无队列worker，收集/报告异常仅固定UNAVAILABLE。超过5秒收集不报告，本地MATCH以单调时间最多10秒并每次重查租约/epoch；不得在公共调度线程做I/O、积压队列或因为一次成功永久保留资格。deploymentEvidence健康项只含state，不包含摘要/缺失域/连接与原异常；默认不注册该健康项，最终readiness和直接接流策略按M3c另验。验证DeploymentEvidenceRuntimeTests、DeploymentEvidenceRuntimeHttpTests、原CacheNodeRuntime及模块/普通端点门禁。

P08 SharedStorageRegistry只在宿主首次探针时发现资源，校验真实模块/唯一归属/全部显式shared及最多32项；默认单机装配不能写探针。SharedStorageProbe按manifest/真实node/process/epoch固定每资源随机挑战，publish每资源最多一次；写结果未知只能verify，禁止自动重建失踪探针或覆盖。报价不含挑战原文/路径，peer读取要核对完整资源集合及预期身份；单向true不是双向资格。生产I/O禁止HTTP/job/数据库事务，调用预算有界；新增适配不能绕过原File路径/链接检查。回归SharedStorageProbeTests、FileClusterStorageTests、实际宿主和模块边界；持久互证/资格及生产挂载范围按M3b3后续阶段验收。

P08 SharedStorageStore只接受宿主无HTTP/身份/外层事务调用，group→预期节点有序锁，短事务内重查配置MATCH/租约/process/epoch；文件I/O禁止在事务内。锚点只由清单首节点初建且同清单不可替换；各进程报价不可变，故障或观察断档推进generation、旧reverse确认不得复用。初次/恢复需同锚点及活peer双向确认，健康节点连续自检证书不受peer离线拖累。JSON≤32KiB、资源≤32、peer确认≤31，不接HTTP载荷，不输出原异常/路径。验证SharedStorageStoreTests、SharedStorageMigrationIT及节点/配置存储/模块边界；M3b3c才装自动I/O worker和健康，不能据内部certified直接称流量就绪。

P08 SharedStorageRuntime独立单worker零队列，原节点心跳/配置证据线程不做文件I/O。每轮最多5秒，READY单调最多10秒且每次复查配置MATCH及lease epoch；自身或锚点失败报告本代次失效，迟到结果不可授证。新进程固定探针，状态恢复只能读原报价/代次，不重写未知探针。启用storage.enabled必须已有DeploymentEvidenceRuntime，sharedStorage健康仅state，默认单机不装配。回归SharedStorageRuntimeTests、SharedStorageRuntimeIntegrationTests、宿主/模块门禁及后续两JVM；同JVM两个runtime+真实文件/DB不代表已完成独立JVM/生产挂载验收。

P08 DeploymentTrafficGate只读取配置/存储运行时的有界本地观察与ApplicationAvailability；HTTP线程禁止主动探测DB/文件。默认traffic.enabled=false，开启须eager schema validate、真实节点清单及两类证据。Filter必须先于TenantRequestFilter/认证/正文读取，仅精确健康路径GET/HEAD豁免；健康readiness四项、liveness仅livenessState且失败503的固定部署契约见[部署说明](../docs/deployment.md)。已准入工作只执行一次，拒绝不改历史回执/UNKNOWN；自身ContextClosed后不可重新接流。新增影响接流的配置须纳入主机证据；禁止用旧基线覆写或共享状态清理绕过升级。回归DeploymentTrafficTests、DeploymentTrafficHttpTests及SMA_CLUSTER_TRAFFIC=1隔离矩阵，M3c2主动排空与M3d LB另验。

P08 FileClusterStorage的不可变verify必须使用共享读锁，不可让节点同读锚点互相撤销资格；首次写、publish核对和retire仍须排他。不能取消写权限/摘要/长度/force/路径检查或放宽超时，不支持shared锁的挂载不得以exclusive回退冒充通过。回归FileClusterStorageTests的独立JVM持锁场景、SharedStorageProbe/Runtime及真实矩阵；生产单节点单worker顺序核验不等于允许Java同JVM重叠文件锁。拒绝原因DEBUG只记固定状态，不写请求/token/路径/钥或摘要。

P08后台准入使用common::deployment的DeploymentWork：只在执行线程、事务外、每次新领取前由host/可信SYSTEM_JOB申请；HTTP和普通身份拒绝，不能排队时预占或用许可代替业务授权。许可覆盖本机实际工作，跨线程/重复关闭安全；资格失败不自动取消在途，摘流不可逆，不清零未完成数、不修改UNKNOWN。Snapshot只描述工作许可，不证明HTTP/WS全排空。Gate与许可共用同一摘流状态；基础协调续报例外、消费者接线及实际停机阶段见[方案](../docs/proposals/P08-deployment-drain.md)和P08进度。回归DeploymentWorkTests、DeploymentTrafficTests/HttpTests及各真实消费者；不得因底层API通过就宣称worker已全接入。

CacheTaskDispatcher的tick只接受无HTTP/身份/事务的host调用，执行线程不继承ThreadLocal；排队不能先拿许可，目录发现及每次claim＋executeOne分别准入。资格拒绝保留当前租户游标，已获准执行可按原回执/fence收尾，下一项必须重新申请；异常只释放本地许可，不标记业务完成或立即重发。回归CacheTaskDispatcherTests、CacheDispatcherDrainTests和CacheTaskExecution/CacheNativeResourceTests。PreDestroy仍是销毁兜底，不能用shutdownNow通过冒充受控排空；整机生命周期按c2c验收。

MailDispatcher/MailDispatchCycle仅host进入，目录发现、recover和每次claim在事务外分别申请DeploymentWork；排队不预占，执行回调重查，线程不继承请求ThreadLocal。每次claim的同一许可须同时等领域结果处理与实际SMTP线程finally结束；Future取消或方法超时不释放仍运行的传输，尚未执行的拒绝/取消则必须归还。超时与开始DATA互斥，可能已发出时UNKNOWN，迟到完成不能覆写结果或重发；原fence/短事务/租户凭据与TLS策略保持。此规则适用于mail内部发送器，二开仍只用公开MailSendApi，不能直接传假许可绕过；回归MailDispatcherTests、MailTransportLifecycleTests、SmtpMailSenderTests、MailDispatcherDrainTests、MailOutboxTests和IdentityEmailDeliveryTests。真实供应商和整机排空另验，禁止把本机协议/传输替身当送达证据。

Auth SessionMaintenance、OpenAPI NonceMaintenance与Identity Login/EmailMaintenance的宿主入口（含直接cleanTenant）必须拒绝HTTP/认证身份/外层事务。目录发现及每个有界领域事务分别准入；排队回调重查、不继承ThreadLocal，拒绝不推进未处理租户游标，许可覆盖提交或回滚。Auth过期与重加密、Identity邮箱与重新认证之间必须重新申请；禁止扩展清理范围/延长期限/删除未知回执来实现排空。direct cleanTenant拒绝时0不是“无待清理数据”的证明。回归四目录维护测试、SecurityMaintenanceDrainTests、SessionMaintenanceTests/SweepTests、NonceMaintenanceTests和IdentityLoginTransaction/EmailChallenge/ReauthenticationTests；持久事件发布不等于消费完成，监听器准入必须先于其事务，c2b4另验。

持久ApplicationModuleListener（基座/静态二开）须显式`@Async("deploymentEventExecutor")`，单参数void、可代理、REQUIRES_NEW同步方法体；禁止在方法内再申请事务外许可、返回Future/分离子任务后假装完成。Common两层Advisor必须保持Async→生命周期→Modulith完成跟踪→准入→事务顺序，许可到框架结果事务结束才关闭。未获准抛固定异常由框架保留FAILED，不能正常返回或清空未知记录；所属模块继续可信租户和幂等。专用4线程/128队列不传播请求身份、不CallerRuns、不替换默认执行器；其队列/技术收尾须c2c有序关闭。验证DeploymentEventBoundaryTests、DeploymentEventTests、DeploymentEventMysqlTests、DeploymentEventConsumersTests及Publication/Audit/Auth/Socket回归。新增监听/框架升级必须更新清单并实际验证顺序，不能仅改order常量。

Monitor指定事件重投必须同时校验所选ID、FAILED和当前租户，使用框架公开精确谓词入口及原子markResubmitted；禁止FailedEventPublications全局LIMIT1后过滤ID而静默跳过目标，也禁止直接调用监听器/批量重投外租户。当前未完成集合读取边界不代表数据库分页或全局互斥。回归PublicationTests的较早外租户FAILED、PublicationRequestGovernanceTests及DeploymentEventMysql/ConsumersTests；事件记录与监听ID/载荷兼容保持。

SocketSignalProcessor调度与执行入口严格host、无HTTP/身份/外层事务；单worker不继承ThreadLocal。订阅发现、批次读取、逐条push/revalidate、目录发现和清理分别事务外准入，读取提交后再做授权与I/O。拒绝不记observed、不推进未处理批次或维护游标/截止；已进入的同步Hub调用结束后才归还许可，异常不伪造客户端确认。observed只是本机提示观察，数据库信号保持各节点独立读取，PROTECTED和REST授权语义不变。验证SocketSignalProcessorTests、SocketSignalDrainTests、SocketSignalStoreTests及真实SocketTests；Hub缓冲发送/ping/连接关闭尚按WS子项验收，不得据调用返回宣称连接已排空。

存储后台部署准入适用于StorageOutboxWorker、StoragePoolRegistry定时回收与原生投递入口：目录发现/逐资源消费事务外准入，独立单worker有界队列不继承请求上下文，拒绝保留资源游标、每轮最多8资源。许可覆盖claim/同步消费者/finish及池租约释放，已有租约close/release和请求内容量回收不得被新准入阻断。StorageDrainWorker仅作为既有DRAINING任务的技术确认例外持续运行，严格host且不调用业务消费/复制/最终切库；c2c结束协调线程后再关DB。BusinessOutboxConsumer至少一次/UNKNOWN租约重投不同于通用请求账本UNKNOWN，禁止混同或让消费者启动脱离异步后提前返回。回归StorageOutboxWorkerTests、StorageDrainWorkerTests、StoragePoolRegistryTests、StorageSessionTrackerTests、StorageOutboxDrainMysqlTests（精确P08独立业务库）和ApplicationSmokeTests；不把单机任务准入当作X5跨库/多实例切换验收。

插件独立控制定时器和后台invokeJob的部署准入按[插件专项规则](../docs/agent-rules/extension-hot-reload.md#运行验收边界)执行：周期/任务在事务外申请许可，policy.resolve前拒绝新任务，在途I/O、同步execute及结果提交结束后才释放。租约过期不取消已进入execute，UNKNOWN不自动重做；HTTP同步调用和完整停机另按c2c。验证ExtensionDeploymentTests、ExtensionJobDrainMysqlTests、原ExtensionWorkJournal/WorkCaller、真实PluginRuntimeTests及模块门禁。

Message WebSocket排空需同时观察握手预留、真实连接和全部发送/关闭操作；通过common::deployment的只读DeploymentConnections供宿主观察，不含业务身份或控制入口。一次票据消费前检查准入，失败释放预留，101升级保留到连接回调；晚到升级重新检查资格。只有afterConnectionClosed移除连接，回调提前不能清除未返回的I/O计数；不得靠等待超时、close返回或清空集合伪造排空。独立sma-socket-lifecycle worker最多128项轮转，每连接ping间隔至少30秒且事务外准入；部署技术关闭继续运行，不申请新业务许可。停止新发送后等当前缓冲刷新线程返回，部署用1001且无session-ended、不注销共享会话；强退原有提示/1008继续保持。验证SocketHubDeploymentTests、SocketHandshakeDeploymentTests、SocketDeploymentMysqlTests、SocketTests、SocketTicketTests及前端messages.test.ts；实际整机停机顺序和双JVM滚动恢复归c2c。

部署活动入口采用DeploymentActivityInventoryTests的编译后精确清单门禁，覆盖基座/静态业务Scheduled、Async、执行器/线程/进程、并行/异步提交、HTTP及已审资源创建；基线为src/test/resources/deployment-activity-inventory.txt，人工归属台账见[活动清单](../docs/proposals/P08-deployment-activity-inventory.md)。新增/删除/修改入口或调度参数必须审查父请求、真实子线程/进程完成条件、摘流资格、预算及关闭依赖，再同步基线；禁止自动接受actual输出、忽略业务包或以Future取消冒充结束。库/自动配置内部线程另须运行时核对，清单存在不代表排空验收完成。标准入口运行DeploymentActivityInventoryTests、DeploymentEventBoundaryTests及相应消费者专项；二开组合在自身分支补充已审条目并保留基座原条目。

父请求/已准入工作分离到子线程时，使用common::deployment的DeploymentTaskTracker提交至原受控执行器，并由所属组件实现只读DeploymentActivity观察。它不创建线程、不传身份、不替代业务准入；queued在提交前登记，已开始工作要等run finally，不能在Future.done/cancel中提前释放。组件销毁配合tracker.close与执行器shutdown，移出队列的任务要取消，实际运行计数保持。Callable内部再分离异步必须另跟踪；库连接/子进程不能只靠此计数。LocalTransferStorage已接入，未知原路径/账本不删除，STORAGE与确证未开始的NotStarted继续区别。验证DeploymentTaskTrackerTests、LocalTransferDeploymentTests、LocalTransferStorageTests、DeploymentActivityInventoryTests及模块架构；全程停机和其他传输另按P08活动台账验收。

FileClusterStorage内层见证I/O通过DeploymentTaskTracker观察，组件id=file.shared-witness；属于技术协调，不能因业务摘流阻止当前见证续报，但c2c必须停止外层SharedStorageRuntime后等内层实际结束再释放资源。取消/关闭不能归零仍在运行的线程，也不能返回资格成功、重写未知探针或删除他节点证据。原2线程零队列/摘要/共享读锁/发布退役排他/路径和host限制保持；回归FileClusterDeploymentTests、FileClusterStorageTests、SharedStorageProbe/RuntimeTests、SharedStorageRuntimeIntegrationTests及ApplicationSmokeTests。受控阻塞夹具不替代生产挂载或多主机验收。

辅助传输关闭使用common::deployment的DeploymentTransport：shutdownTransport必须严格host且事务外、非阻塞、幂等；宿主先等已准入父请求/工作结束，再stopAccepting并等待已接受子任务，最后关闭库客户端。transportTerminated只读核对真实执行器/任务/客户端，不能以Future.cancel、任务数0或shutdown返回冒充完成。GoogleHttpTransport采用线程池terminated钩子关闭HttpClient，所有工作线程不继承上下文；PreDestroy仍只是强制取消兜底。维持固定端点、无跳转/Cookie、64KiB响应与4并发，不因停机重试兑换。验证GoogleTransportDeploymentTests、GoogleHttpTransportTests、GoogleIdTokenTests、DeploymentTaskTrackerTests/Inventory及原Identity登录/HTTP回归；实际整机协调与其余传输按P08活动台账验收。

ConsoleProxyService的两阶段代理流程按[插件专项规则](../docs/agent-rules/extension-hot-reload.md)纳入DeploymentActivity/DeploymentTransport：计数覆盖原forward全部阶段，关闭新入口后等既有流程，再关客户端。同步父调用结束不代表sendAsync底层资源结束；真实JDK终止另核验，未知发布写不得重发。适用范围、固定容量、TLS/权限/凭据约束和验证入口在专项规则集中维护。

DomainTlsProbe的探测与截止回调关闭约束集中在[租户平台规则](../docs/agent-rules/tenant-platform.md)：父调用/Socket归属与回调分别观察，保留在途截止保护；取消与定时器终止都不能代替实际Socket关闭。它不是新的域名验证授权入口，原NATIVE短事务/权限/网络边界继续生效。

NodeControlClient的同步流与客户端关闭遵循[插件规则](../docs/agent-rules/extension-hot-reload.md)：观察覆盖读取/close及缓存操作，先等外层周期，再关操作入口与客户端。请求头已返回、方法超时或活动0不能单独证明传输资源已结束；仍阻塞时保持未完成，不自动重发控制副作用。验证入口与有限等待边界在专项规则集中维护。

P08 b9c规则执行仅承诺当前THEN/IF/SWITCH单路径生成计划的同步回调与租约finally；引入WHEN/异步节点、异步表达式函数或升级LiteFlow必须复验RuleDeploymentExecutionTests及原RuleFlow/Engine/CacheAdapter/Compatibility，不能据execute2Resp名称猜测线程结束。独立file.internal.inspection解析/子进程类不得被宿主或业务直接依赖或注册Spring Bean；InspectionProtocol/Frames只允许协议DTO/帧用途。InspectionWorkerBoundaryTests验证该边界，禁止反射/包装绕过；这是可信源码门禁，不是native沙箱。BoundedInspectionProcess/ContentInspectionWorker本机测试不替代P09容器/所有后代退出和X5，整机关闭只能处理宿主自己拥有的资源。

P08技术协调的CacheNodeRuntime心跳/确认只允许严格host入口，含匿名HTTP、RequestContextHolder请求绑定、任何认证身份及外层事务均拒绝；两个执行器禁止继承ThreadLocal。不能为摘流套业务DeploymentWork导致停止续租/确认；实际执行器停止与DB/Redis销毁顺序归c2c。验证CacheNodeRuntimeTests、DeploymentActivityInventoryTests及真实DeploymentActivityAssemblyTests；后者检查组件发现与同一传输归属，不是整机关闭证明。

P08 HTTP父请求计数随traffic门禁启用：ServletRequestListener初始化内部生命周期，第一次业务分派与摘流共用Coordinator锁原子保留许可。REQUEST/ASYNC/ERROR/FORWARD/INCLUDE共用原请求许可，已准入分派不在摘流后重新申请；缺少监听器上下文拒绝。必须同时等requestDestroyed和已进入分派返回才归还，不能用链返回、isAsyncStarted=false、超时、断连或重复close清零。计数独立于业务worker许可，不授权身份传播；默认traffic=false和精确健康GET/HEAD豁免保持。验证DeploymentHttpRequestsTests、DeploymentHttpLifecycleTests（真实Tomcat）、DeploymentTrafficHttpTests（完整宿主）及原Traffic/Work/架构门禁；任意MVC/AsyncContext子线程、其他销毁回调和容器资源仍须c2c2跟踪，不以父计数0宣称完整排空。

P08 c2c2a运行资源清单使用真实宿主ScheduledTaskHolder及Executor/TaskScheduler/DataSource/RedisConnectionFactory公开API，须覆盖Modulith程序化陈旧事件维护，不以源码Scheduled清单代替。Spring任务诊断只接受已审方法/框架标签，未知描述仅摘要；不得反射框架私有Runnable包装器。nextExecution为空可能只是任务正在执行，不可据此断言停止。插件scheduler的默认候选及单次初始化约束集中在插件专项规则；验证DeploymentRuntimeInventoryTests（P08专用MySQL/Redis）、ExtensionSchedulingTests及原活动/模块门禁。运行装配通过不等于异步子任务或库资源终止。

P08 c2c2b异步观察随traffic开启：common.servlet-async包装受支持Servlet AsyncContext.start和监听回调上下文，common.mvc-async通过deploymentMvcExecutor借用applicationTaskExecutor；不新建线程池、不替换事件执行器、不授予身份。Runnable execute异常必须回到容器，不能用FutureTask吞掉原错误；关闭只撤销未开始void任务，已运行直到finally计数，submit取消也不能提前释放实际执行。MVC默认Callable/StreamingResponseBody/WebAsyncTask或显式deploymentMvcExecutor受观察，未观察的WebAsyncTask专用executor在执行前拒绝，保留原对象回调。两个组件需支持Modulith代理，不能final或关闭observability绕过。验证DeploymentTaskTrackerTests、DeploymentServletTasksTests、DeploymentHttpLifecycleTests、DeploymentMvcLifecycleTests及完整DeploymentTrafficHttpTests/默认ActivityAssembly、既有子任务消费者和结构清单。Servlet容器/原应用executor仍由所有者负责实际终止；脱离生产者的DeferredResult/CompletionStage和主动解包不能继承已观察结论，完整停机按c2c2c/d。
