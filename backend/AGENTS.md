# 后端基座开发规则

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
