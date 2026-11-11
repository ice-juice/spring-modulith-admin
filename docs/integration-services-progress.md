# P09 应用身份、邮件与存储：实施进度

依据：[P09 v2 设计](proposals/P09-identity-mail-storage-and-secure-upload.md)。2026-11-11 用户授权开始实施，并要求最终交付面向二开技术的开发手册。仅实现通用基座；保持本地、OSS、AWS S3、R2 范围及 Google/邮件/存储配置的租户独立性。

## 验收约定

- 用户明确：暂未准备外部测试配置，先实现并完成本地验证。因此每阶段区分“本地实现验收”和“外部联调待办”。已通过本地门禁后可以继续下一项，缺少外部账号不伪记通过，也不因缺少账号跳过本地安全测试。
- 当前 `main` 存在 P06/P07/P08 和前端并行工作；本任务不回滚、重排或提交这些修改，不重启现有服务。共享文件使用最小补丁。
- 后端从根 reactor 构建。P09 当前独立输出为 `backend/target/p09-session/target`，历史阶段保留 `backend/target/p09/target`、`backend/target/p09-smtp/target` 与日志（保留 `/target/test-classes/` 后缀供 ArchUnit 排除测试类）；需要清库的集成测试使用 `sma_test_p09_services`，不得使用并行任务的默认测试库。
- 每个子项先实现、验证、记录，再进入依赖它的子项。最终运行标准后端检查、新版前端 verify 和适用端到端用例。真实 OAuth/SMTP/云测试保留明确资源及命令要求。

## 已确定的实施契约（C0）

1. 新增 `identity`、`mail` 静态 Modulith 模块，扩展现有 `file`。公共身份/租户/凭据边界放 `common :: security`；不创建业务工程，不通过反射或跨模块 Mapper 接入。
2. 后台身份保留 `SmaPrincipal`；应用用户采用独立主体。旧登录注解只允许后台人类会话，应用接口显式声明应用 audience；机器身份不获得人类权限。管理 API 均显式按模块权限和当前租户处理。
3. 公开配置引用包含租户、模块、配置 ID、配置版本、凭据版本。租户必须来源于已认证主体或明确建立的可信任务作用域，P09 不采用隐式租户 1 回退。配置记录和历史分别归 identity/mail/file。
4. Google 登录事务、OTP、会话及工作租约使用 MySQL 权威状态；公开 loginHandle 只供有限引导定位。缓存不保存可放行的过期安全状态。
5. 邮件采用 Jakarta Mail/SMTP；Google JWT 采用受维护的 JOSE 库并限制算法/issuer/aud/nonce；S3/R2 采用 AWS Java SDK v2，OSS 采用原生 SDK。依赖在对应阶段锁定和验证，不用手写签名协议代替供应商 SDK。
6. 上传的本地/代理/云直传遵守同一隔离和发布状态；完整安全检查成功才 READY。扫描器不可用时保持隔离。旧文件兼容只覆盖方案声明的迁移窗口。
7. 手册随实现更新，只把已存在的 API 写为可调用能力；最终覆盖租户配置、权限、Java/HTTP 调用、Google/H5/App、邮件验证码/通知、上传/续传/绑定/下载、错误处理、部署和迁移。

## 顺序与状态

| 阶段 | 子项/依赖 | 本地验收 | 状态 |
| --- | --- | --- | --- |
| C0 | 范围、契约、路径与验证隔离 | 当前规则/实现核对、无模块命名冲突、记录外部资源缺口 | 完成（文档核对） |
| C1a | 身份类型、显式租户、后台/应用/机器方法门禁；依赖 C0 | 真实 Spring 方法安全代理、伪身份/错误 audience/跨租户/线程作用域；架构门禁与后台认证回归 | 完成（本地） |
| C1b | 租户配置引用、凭据加密与不可跨租户解密；依赖 C1a | 缺配置/错作用域拒绝、AAD 绑定、轮换/篡改、无明文日志 | 完成（本地） |
| C2a | mail 租户配置、不可变版本、权限和凭据管理；依赖 C1 | 同名配置隔离、跨租户读写拒绝、版本与密文隔离、管理 HTTP/权限 | 完成（本地） |
| C2b1 | mail 模板草稿/发布版本、安全变量渲染；依赖 C2a | 双租户管理、不可变发布、HTML/链接/变量拒绝路径 | 完成（本地） |
| C2b2 | mail outbox、配额/租约；依赖 C2b1 | 事务/幂等/UNKNOWN/双租户/恢复 | 完成（本地） |
| C2b3a | SMTP TLS 发送器及出站边界；依赖 C2b2 | 本地 TLS/STARTTLS、证书/主机拒绝、断线及明确拒绝分类 | 完成（本地） |
| C2b3b | 租户自动调度；依赖 C2b3a | 重启恢复、租户停用、端到端任务领取/发送/结果 | 完成（本地） |
| C2b3c | 回执与抑制；依赖 C2b3b | 回执防重放/乱序/签名/退信及抑制 | 完成（本地桥接契约） |
| C3 | identity 配置、Google、邮箱注册/重置与应用会话；依赖 C2 | 本地 OIDC 协议夹具/签名、重放/绑定/OTP/刷新族、双租户 HTTP | 完成（本地，管理页面归C8） |
| C4 | file 配置/场景/本地/任务/扫描与旧契约兼容；依赖 C3 | 双租户、真实字节检测、隔离 worker、恶意/故障/旧附件回归 | C4a/b/c本地完成；C4d安全流水线实施中 |
| C5 | AWS S3 直传/分片；依赖 C4 | SDK 协议夹具、签名范围、断点/取消/发布不变性 | 未开始 |
| C6 | R2 独立能力适配；依赖 C5 | 不支持能力拒绝、region/endpoint/签名/校验差异 | 未开始 |
| C7 | OSS 原生适配；依赖 C6 | SDK 协议夹具、分片/回调验签/私有读取 | 未开始 |
| C8 | 管理页面、上传组件及二开教学样例；依赖 C7 | 前端 verify、租户切换/秘密脱敏、API/实际页面验收 | 未开始 |
| C9 | 兼容、容量/恢复验证、最终开发手册及规则同步；依赖 C8 | 标准后端/前端、文档样例编译与协议一致性、迁移/恢复证据 | 未开始 |

## 实际验证记录

### C3 后续拆分（C2 本地验收后按顺序进入）

1. C3a：租户 realm/client、Google 客户端类型/精确 callback/Origin 与加密 secret、不可变修订和停用；双租户管理 HTTP、版本冲突和泄密拒绝。
2. C3b：Google ID-token 验证器、公钥固定来源/有界刷新、授权码交换与 PKCE；本地签名/JWKS/协议夹具，不使用真实 Google 凭据。
3. C3c：一次性登录事务、最小 subject/external binding、应用会话/刷新族/租户安全代次、H5 Cookie/CSRF 与 App Bearer 链；跨 audience、重放、会话撤销并发专项。
4. C3d：邮箱 challenge/用途/猜测与发送预算、注册/重置/显式绑定、邮件在途有效性门禁；真实事务原子性、错误计数独立提交和旧令牌撤销。
5. C3e：公开二开 API/事件、H5/App 示例、管理能力与文档契约回归；管理员 TOTP/机器认证保持原安全边界，再进入 C4 文件服务。

C3a 路径：新增 `backend/src/main/java/com/sma/admin/identity/` 静态模块及 `db/migration/identity/V1__tenant_identity_configuration.sql`、identity 双语包与测试；根 `common` 身份契约保持不变，管理接口只允许后台明确权限。不新增匿名引导或应用会话端点；C3b/C3c 独立验收后再开放。

### C3c 依赖顺序与约束

C3c3 实施细化：以 URL 中公开的 `appClient=<loginHandle>` 选择客户端（也供 CORS 预检选择，不能用它认证），Host 继续决定可信租户。独立 `/app/**` 安全链，WEB 使用按客户端命名的 __Host- Cookie、Spring CSRF 和会话绑定校验，原生仅 Bearer；Origin/来源地址取服务端入口，不从 JSON 信任。追加客户端 SameSite 策略（默认 LAX，可显式 NONE；均 Secure），支持同站和受控跨站部署，第三方 Cookie 浏览器限制另记录。共享“本人会话”接口采用专门应用主体门禁并由精确客户端过滤器/对象归属约束；业务接口仍要求字面量 audience 的 PermitApplicationUser，旧后台注解不放宽。路由命名仅扩展 `/app/<所属模块>`，并增加应用路由授权约束测试。

C3c2 实施细化：在 identity V3 中增加一次性登录事务、租户预算和有界来源/客户端速率记录。状态 PENDING→VERIFYING→COMPLETED/FAILED，VERIFYING 崩溃/超时不重新兑换授权码；nonce 短期加密，state/发起方证明只存摘要，PKCE 始终 S256。固定事务中的客户端/回调/Origin/流程及配置安全代次，签发会话与完成事务同库原子提交。开始、领取、完成分别短事务，Google 网络/验签在事务之外；成功会话令牌不写入登录事务或幂等回执。CODE 和 ID_TOKEN 两种流程分别绑定，不能在完成时互换。部署和来源预算不是共享 Google 账号配置。

- C3c1：追加主体/外部身份、应用会话与刷新历史 schema，实现可信内部签发、访问令牌验证、刷新单次消费/重用撤销族、租户/realm/client/subject 安全代次。专用 MySQL 验证回滚、并发刷新、禁用后恢复不复活与跨租户/audience 拒绝；本子项不开放 HTTP 登录入口。
- C3c2：追加持久 Google 登录事务，绑定配置快照、tenant/realm/client、state/nonce、PKCE S256 和浏览器/设备证明；短事务消费后才访问 Google，完成后重新核验现场安全代次，异常/结果未知不能重复兑换。注册只按 issuer+sub，不按邮箱合并；注册策略与最小主体/业务事件分离。
- C3c3：接入独立 `/app/**` 应用安全链及 identity 登录/会话路由，保留 `/api/identity/**` 后台管理链。复用 P10 可信 Host 入口，loginHandle 必须与该租户入口匹配，不新增客户端 tenantId 切换或全局 Mapper 绕过。若引入 `/app/<module>/...` 路由，只精确扩展命名约束，不放宽后台身份门禁。
- Web 会话使用 Secure/HttpOnly Cookie，CSRF 与精确 Origin 检查必须配套；原生使用 Bearer，模式取不可变 client.platform，不能由请求选成“免 CSRF”。需支持同宿主多个 client 的 Cookie 区分，不能扫描/选择第一个可用会话。注册 Origin 的 CORS 响应需 Vary 与精确允许头，不使用任意 Origin 回显。
- 完整宿主 HTTP 验证在 C3c3：CSRF/登录重放/跨 client/跨租户、旧后台 TOTP/机器凭据拒绝、前端回调不在 URL 放正式 token。之后才进入邮箱验证码 C3d。

2026-11-11：核对 main、现有模块/安全注解、TenantScope/MyBatis、测试数据库清理保护、独立构建输出配置；确认现有 `@PermitLoggedIn` 只有 isAuthenticated，需显式身份类型门禁。

- C1a 专项：Java 21，根命令 `backend/mvnw.cmd -B -ntp -f pom.xml -pl backend -am -Dsma.verify.directory=target/p09/target -Dtest=ActorContextTests,ActorMethodSecurityTests,ModularityTests,ArchitectureRulesTests -Dsurefire.failIfNoSpecifiedTests=false test`：27 项通过（9 项新身份/真实方法代理测试＋18 项架构测试），日志 `backend/target/p09-c1a.log`。
- 首次使用 `target/p09-verify` 的基线失败：ArchUnit 默认测试目录排除未识别自定义路径，把测试支持包当作模块；调整到上述隔离路径后通过，未放宽规则。旧失败日志 `backend/target/p09-baseline.log` 保留。
- 已建立专用 `sma_test_p09_services` 数据库，仅给本机现有测试用户授予此库权限。`AuthFlowTests` 4、`SessionManagementTests` 1、`TotpAuthenticationTests` 11 项通过。日志 `backend/target/p09-c1a-auth.log` 中 OpenAPI 4 项有 3 项返回 403：原因是历史机器入口复用 `@PermitLoggedIn`。已增加精确的 `@PermitMachine` 并迁移 OpenController 的四个已认证方法，保留 token 入口及签名、nonce、扩展授权语义；执行真实机器/人类/应用方法代理与 OpenAPI 重验，完成前 C1a 不标记完成。

C1a 重验（2026-11-11）：相同根 reactor、隔离路径/测试库，`-Dtest=ActorContextTests,ActorMethodSecurityTests,OpenAuthenticationTests,ModularityTests,ArchitectureRulesTests`，31 项全部通过，日志 `backend/target/p09-c1a-recheck.log`。结合前次已通过的 16 项后台/TOTP/会话测试，C1a 本地门禁通过。未新增 Google/APP_USER HTTP 认证端点，相关端到端验证在 C3。

C1b（2026-11-11）：根 reactor 使用 `-Dtest=TenantCredentialCryptoTests,ActorContextTests,ActorMethodSecurityTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests`，36 项通过（新增密文隔离/篡改/轮换/秘密输出保护 6 项），日志 `backend/target/p09-c1b.log`。AES-256-GCM、HKDF-SHA256 租户/模块密钥派生及 AAD 配置/用途绑定完成；空密钥配置时拒绝秘密操作，不自动生成密钥。配置表及 REST 权限由对应模块下一阶段实现，此结果不代表三类配置已具备管理页面。

C2a 验收中遇到的问题（2026-11-11）：首次构建因并行修改中的 SqlTelemetry `Statement` 类型冲突失败；复读时工作区已改为 `java.sql.Statement`，本任务没有覆盖其改动。完整宿主启动随后暴露 TenantCredentialCrypto 的 final 类无法被 Modulith/CGLIB 代理，当前已去除 final；执行包含 6 项邮件配置测试（新增 HTTP 写入和保密响应）、加密及架构检查的重验。前两次失败日志 `backend/target/p09-c2a.log`、`backend/target/p09-c2a-recheck.log` 保留，当前日志 `backend/target/p09-c2a-final.log`；成功前 C2a 不标记完成。

C2a 最终结果（2026-11-11）：`p09-c2a-final.log` 的架构/语言包/加密/模块检查共 27 项通过，邮件 6 项中 5 项通过，剩余一项是测试误把业务 code=404 期待为 HTTP404；实际返回 HTTP200、code=404、data=null，未发生越权。保持现有协议并修正断言和手册后，以相同根 reactor/隔离库执行 `-Dtest=MailProfileTests -Dsurefire.failIfNoSpecifiedTests=false`，6 项全部通过，日志 `backend/target/p09-c2a-contract.log`。覆盖真实 MySQL 自动租户过滤、同名配置、密文/历史版本、停用时密钥不可用、过期版本拒绝、HTTP 创建/修改、权限与保密响应。mail V1 已本地验收，后续 schema 修改只追加迁移。

当前交付：C0/C1/C2/C3/C4a/b；当前为C4c上传任务与本地传输。Google/邮箱身份HTTP、本人管理、公开主体/联系查询、邮件服务及租户存储/场景配置发布均已通过本地专项；云传输/安全上传与管理界面尚未交付，二开手册随实现补齐。未执行全量标准发布检查、未部署当前服务、未进行真实外部联调。邮件自动调度已实现且默认关闭，持久队列入库不能当作已投递；身份状态维护默认启用，仅清理本模块状态。

C2b1（2026-11-11）：根 reactor 同一隔离输出执行 `-Dtest=MailTemplateRendererTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests`，27 项通过，日志 `backend/target/p09-c2b1-unit.log`。专用库执行 `-Dtest=MailTemplateTests,MailProfileTests`，9 项通过，日志 `backend/target/p09-c2b1-integration.log`。验证 HTML/链接域名/变量/头部注入拒绝、不可变发布、账号快照、跨租户 ID/同名编码、停用账号拒绝发布；mail V2 已本地验收，只追加后续迁移。模板/账号配置无外部发送副作用，投递闭环在 C2b2/C2b3。

C2b2（2026-11-11）：首次 `MailOutboxTests` 7 项通过，整组失败来自并行 common/request 模块尚未补齐的语言包键；复读当前源码已补齐，没有绕过语言包检查。追加 HMAC 请求指纹（终止后可擦除正文而保留幂等核验）和事务内队列预占（避免并发快照超额）后，从根 reactor 使用独立库/输出执行 `-Dtest=MailOutboxTests,TenantCredentialCryptoTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests -Dsurefire.failIfNoSpecifiedTests=false test`，37 项全通过，日志 `backend/target/p09-c2b2-final.log`。包含 9 项队列真实 MySQL 测试与 7 项加密/指纹测试；前次日志保留 `backend/target/p09-c2b2.log`。mail V3～V5 已本地验收，之后只追加迁移。

已验收边界：同资源业务事务回滚不留下任务；同键并发只入一任务；不同内容/租户/主体拒绝；固定模板/账号版本；CLAIMED 崩溃可重领、SENDING 崩溃 UNKNOWN 并拒绝迟到 worker；配额原子计数、限次退避、取消/过期清理；终止后正文擦除、HMAC 幂等重放。管理任务查询/取消控制器已通过架构检查，浏览器/HTTP 完整接受度随 C8 再测。SMTP 外部副作用和真实投递尚未测试，不据此宣称邮件服务闭环完成。

C2b3a（2026-11-11）：新增 Boot 管理的 Angus Mail 2.0.5、Jakarta Mail 2.1.5，单租户账号版本 Session、目的地主机/端口/IP 授权、固定已验证 IP 的 socket、TLS 证书/主机校验、并发与总时限。首轮 29 项通过；补充空信任库拒绝后，隔离输出 `target/p09-smtp/target` 从根 reactor 执行 `-Dtest=SmtpMailSenderTests,SmtpNetworkPolicyTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests -Dsurefire.failIfNoSpecifiedTests=false test`，30 项全通过，日志 `backend/target/p09-c2b3a-fresh.log`。本机临时证书/SMTP peer 实际验证 TLS/STARTTLS、UTF-8/固定 Message-ID、主机名/信任链拒绝、无 TLS 不发凭据、4xx/5xx、DATA 后断线 UNKNOWN、总时限断开及 DNS 固定。两次补测构建因并行缓存 Change 类型旧产物失败，换干净隔离输出后通过，未改缓存代码；旧日志保留。

C2b3c（2026-11-11）：根 reactor 专用库运行 MailOutboxTests、OpenAuthenticationTests，21 项通过，日志 `backend/target/p09-c2b3c-integration.log`。新增 4 项真实 MySQL 回执/抑制测试与 1 项真实机器安全链 HTTP 签名测试；域逻辑测试与安全链传输替身明确分开。最终源码执行 SignatureProtocolTests、MailDeliveryCompatibilityTests、MailDispatcherTests、ModularityTests、ArchitectureRulesTests、I18nBundlesTests，28 项通过，日志 `backend/target/p09-c2b3c-final-unit.log`。验证签名必选（包括编码路径）、nonce/正文/时间/跨租户拒绝、回执幂等乱序、发送中回执防重发、抑制双门禁和旧 8 参数 Java 消费者字节码链接。mail V6 已本地验收，只追加迁移。严格旧 JSON DTO 须更新；未声称旧 worker 可混合发送、原生供应商 webhook 或真实邮件送达已验收。

C2b3b（2026-11-11）：专用库执行 MailOutboxTests、TenantWorkDirectoryTests，12 项全部通过，日志 `backend/target/p09-c2b3b-integration-final.log`；最终源码执行 MailDispatcherTests、ModularityTests、ArchitectureRulesTests、I18nBundlesTests，25 项全部通过，日志 `backend/target/p09-c2b3b-final-unit.log`。验证租户目录隔离、停用检查、重启重新发现、有界调度、SYSTEM_JOB/事务外发送、结果提交失败不重复发送。首轮集成测试在发现测试替身应保留 TenantDirectoryService 的具体类型后主动终止，修正为 SpyBean 并重验；非超时重启，旧日志保留 `p09-c2b3b-integration.log`。运行集成测试耗时约 6 分半，不代表容量验收。

C2b3c 实施选择：通用 SMTP 没有统一供应商 webhook 签名协议，先提供签名 OpenAPI 回执桥接与默认拒绝的 MailReceiptPolicy。供应商网关负责核验原始厂商签名并映射本基座 Message-ID，基座再次验证机器签名/nonce/账号授权；不得把通用桥接声称为原生 SES/其他供应商 webhook 已集成。抑制限定当前租户/业务 application/邮件 profile，收件人使用独立随机密钥 HMAC 索引，不留正文或可枚举明文摘要。

C3a（2026-11-11）：根 reactor、隔离输出执行 IdentityClientRulesTests、ModularityTests、ArchitectureRulesTests、I18nBundlesTests，23 项通过，日志 `backend/target/p09-c3a-unit.log`。专用库执行 IdentityConfigurationTests，6 项全部通过，日志 `backend/target/p09-c3a-integration.log`；验证双租户同名/秘密独立、跨租户 realm/client 拒绝、不可变修订、空秘密保留、旧版本拒绝、密钥不可用时停用、原生不存 secret、Google ID 更换约束、真实管理 HTTP 权限/秘密不回传。根构建耗时 12 分 51 秒，主要用于全模块清库迁移；未重启测试或现有服务。identity V1 已验收，之后只追加迁移。此阶段未实现匿名入口、Google token 校验或 APP_USER 会话。

C3b（2026-11-11）：依赖树确定 Spring Security OAuth2 JOSE 7.1.2 / Nimbus JOSE JWT 10.9.1，日志 `backend/target/p09-c3b-dependencies.log`。首次协议 12 项通过但架构禁止 java.util.Date 转换，改用验证过的 NumericDate 转 Instant，未修改门禁。最终根 reactor 执行 GoogleIdTokenTests、GoogleProtocolTests、GoogleHttpTransportTests、ModularityTests、ArchitectureRulesTests、I18nBundlesTests，33 项通过，日志 `backend/target/p09-c3b-final-unit.log`；另将超时夹具改为“头已返回、正文延迟”，4 项 HTTP 专项再次通过，日志 `backend/target/p09-c3b-body-deadline.log`。验证签名/算法/issuer/aud/azp/nonce/时间拒绝、固定密钥来源、过期拒绝及限刷/单飞、配置快照与 PKCE RFC 向量、秘密短期副本擦除、表单绑定、重定向/超大体/超时/并发/事务拒绝。缓存登记受保护 NODE 区域，不缓存用户 claims。原失败日志保留 `p09-c3b-unit.log`；本地 HTTP 夹具不证明 Google TLS 真实联通，尚无匿名登录端点。下一阶段才做完整宿主登录链路验收。

C3c1（2026-11-11）：新增最小 subject/external、会话族与刷新历史，迁移 identity V2。内部会话签发按 issuer+sub/realm 识别，不使用 email 合并；令牌仅存 SHA-256 摘要，WEB 刷新还绑定对应代次的 CSRF 摘要。刷新重用在独立事务撤销族并提交；签发配额在主体锁下当前读，避免旧快照超额。阶段架构/i18n/模块 21 项通过，日志 `backend/target/p09-c3c1-unit.log`；最终独立输出的绑定及门禁 23 项通过，日志 `backend/target/p09-c3c1-binding.log`。专用库执行 IdentitySessionTests，8 项全部通过，日志 `backend/target/p09-c3c1-integration-final.log`（根构建 11 分 10 秒）。验证同邮箱不合并、realm/租户/client 隔离、令牌不落明文、回滚不留账号/会话、旧访问失效、刷新重放独立提交并抗外层回滚、并发刷新唯一后继及撤销、配置/租户/主体代次、期限/模式/注册开关、并发容量和本人退出。完整宿主已装配 Google 协议组件，但没有对外登录/会话端点。identity V2 已验收，后续只追加迁移。

C3c1 首轮受阻：`p09-c3c1-integration.log` 中 21 项架构/i18n/模块通过，会话 7 项均因宿主启动失败而未执行。原因是并行 RequestGovernanceProperties 新增重载构造器后，测试编译产物缺少明确 ConstructorBinding；复读源码已于测试编译之后补齐绑定及 RequestConfigurationTests。本任务保留该修正，切换独立输出 `target/p09-session/target`，先验配置绑定后重跑专用库；不以禁用请求治理或跳过宿主初始化绕过。补充外层事务回滚仍保留刷新重用撤销、应用自己退出会话的测试。此轮不能作为身份状态机失败或通过的证据。

C3c2 首轮（2026-11-11）：维护调度及架构/i18n/模块 23 项通过，日志 `backend/target/p09-c3c2-unit.log`。专用库 IdentityLoginTransactionTests 8 项中 7 项通过，配置停用用例发现只读 authority 校验异常将外层事务标记 rollback-only，导致本应提交的 FAILED/预占释放回滚；日志 `backend/target/p09-c3c2-integration.log`。已将 current/match 的业务拒绝交由事务所有者处理（读库故障仍回滚，签发方异常仍由外层写事务回滚），准备与 IdentitySessionTests 一起重验；通过前不进入 C3c3。

C3c2 最终结果（2026-11-11）：根 reactor、专用库和 `target/p09-session/target` 执行 IdentityLoginTransactionTests、IdentitySessionTests、IdentityLoginMaintenanceTests、ModularityTests、ArchitectureRulesTests、I18nBundlesTests，39 项全部通过，日志 `backend/target/p09-c3c2-final.log`，构建耗时 7 分 45 秒。包含 8 项真实 MySQL＋合成 RSA 签名登录事务测试、8 项既有会话回归、2 项维护调度及 21 项门禁。验证 CODE/ID_TOKEN、一次性消费、state/proof/PKCE/client/callback/Origin/流程隔离、真实 JWT nonce 验证、事务外兑换、并发完成唯一签发、失败回滚不留主体/会话、配置变更与到期迟到 worker 拒绝、失联后不重兑、预占/来源预算/清理及人类身份不能直接调用内部协调器的既有边界。identity V3 已本地验收，只追加迁移；HTTP Cookie/CSRF/Bearer、CORS 和浏览器验证仍在 C3c3/C8，真实 Google 联调未执行。

C3c3 验收中（2026-11-11）：实现独立 `/app/**` 链，可信 Host＋公开 appClient 选择租户内客户端，WEB 专属 Secure Cookie/头部 CSRF、原生 Bearer、精确 CORS 预检、请求体/并发边界及当前会话/退出；追加 identity V4 Cookie SameSite、V5 精确 Google authorized party，省略配置更新保留旧值。根 reactor 的 ActorContextTests、ActorMethodSecurityTests、IdentityClientRulesTests、GoogleIdTokenTests、GoogleProtocolTests、ModularityTests、ArchitectureRulesTests、I18nBundlesTests 共 43 项通过，日志 `backend/target/p09-c3c3-azp-unit.log`。单独输出 `target/p09-httpunit/target` 的 ApplicationRequestFilterTests 4 项通过，日志 `backend/target/p09-c3c3-filter-unit.log`，覆盖编码路径实际字节上限、重复 header/cookie/query、饱和拒绝与 finally 释放、无 Origin 的同站安全请求。专用库当前运行 IdentityApplicationHttpTests、IdentityConfigurationTests、AuthFlowTests、TotpAuthenticationTests、OpenAuthenticationTests；结果未出前不标记阶段完成，开发手册 8.4 为验收中契约。

C3c3 首轮宿主结果：`p09-c3c3-http.log` 的 AuthFlowTests 4、TotpAuthenticationTests 11、IdentityConfigurationTests 7、OpenAuthenticationTests 6 项通过，新增 V4/V5 迁移及 SameSite/azp 不可变修订与省略更新保留已验证。应用 HTTP 9 项全部停在测试夹具的 realm 创建：Users.principal 默认无权限，触发真实方法门禁。改为仅授予测试需要的 identity 管理权限；未修改生产权限门禁。静态复核同时修正 MockMvc 初始空 Cookie 数组（该框架禁止空数组）夹具，继续应用端重验，首轮不作为应用 HTTP 验收证据。

C3c3 第二轮：`p09-c3c3-http-recheck.log` 的 9 项中 5 项通过（原生登录/刷新/退出、租户客户端 audience 隔离、Google 混合 azp、真实后台/机器令牌拒绝、CORS/HTTPS/体积边界），4 项 H5 用例因该 JVM 编译时仍是旧空 Cookie 夹具而未执行。已确认修正源码，正在新一轮 `p09-c3c3-http-final.log` 重跑完整 9 项；第二轮没有生产实现断言失败，仍不标记 H5 已验收。保留原日志和独立清库过程。

C3c3 最终结果（2026-11-11）：根 reactor、专用库、隔离输出运行 IdentityApplicationHttpTests，9 项全部通过，日志 `backend/target/p09-c3c3-http-final.log`，构建 8 分 58 秒。包含真实 MySQL/HTTPS MockMvc 安全链/合成 RSA token；覆盖 WEB Cookie/CSRF/本人退出、刷新轮换与重放撤销、原生 Bearer/跨租户/客户端/audience 拒绝、原生刷新/退出、精确 azp、CORS/HTTPS/请求体/选择器歧义、HttpOnly proof 不接受 JSON 注入、真实后台及机器令牌拒绝、同 Host 双客户端 Cookie 隔离。结合本阶段 43 项协议/身份/架构、4 项请求边界、7 项配置及 21 项后台/TOTP/机器回归，C3c3 本地门禁通过。identity V4/V5 已验收，只追加迁移。MockMvc 不代表真实 Google、设备 SDK 或浏览器第三方 Cookie 策略通过；不部署/重启现有服务。已同步手册 8.4、方案接口映射、README 与后端规则，继续 C3d1。

### C3d 实施子项（C3d4b 门禁已通过，C3d4c 实施中）

1. C3d1：邮箱规范化/租户 realm 稳定 HMAC 索引、租户客户端邮件模板关联、统一密码策略契约；目标地址复用现有租户密文能力，在挑战/凭据落库时绑定用途。只追加 identity 迁移；验证规范化拒绝路径、密钥轮换后的索引稳定、双租户及配置版本。
2. C3d2：持久 challenge/grant 与创建/验证预算，六位 CSPRNG/短期 HMAC、用途和主体绑定；错误猜测独立提交，正确校验产生一次性 grant；验证超时、重复/并发、预算及事务回滚不返还猜测次数。
3. C3d3：通过 mail 公开接口同事务入队，新增通用发送有效性 SPI，发送前检查挑战仍有效；独立模块不得跨表，防止 challenge/outbox 锁顺序反转。验证取消/失效/发送租约、默认拒绝、失效验证码不能被消费；不声称已经发出的 SMTP 邮件可撤回。
4. C3d4：注册、密码登录/重置、已登录显式邮箱绑定与高风险再认证；原子消费 grant/凭据变更/会话撤销，不按 Google email 合并，不增加后台单因素恢复。验证用途/目标/realm/client 隔离、旧会话撤销与业务回滚。
5. C3d5：正式 HTTP/公开调用契约与 H5/App 样例，更新独立匿名入口清单和方法门禁；运行应用、后台/TOTP/机器回归后更新手册，继续 C3e/C4。

C3d 接口取舍：密码强度复用现有登录密码策略，通过 common 的最小公开校验契约接入，保留 IAM 原调用与错误码，不修改 P07 独立安全密码策略。邮箱地址先定义规范化，再使用每 realm 独立随机密钥的 HMAC 索引（随机密钥自身按租户加密），避免部署主密钥轮换改变身份查找键；不删除 Gmail 点号或 +tag。identity 通过 mail 新增的最小模板可用性查询校验当前租户绑定，不访问 mail 表。验证码走保留 application=identity 及固定用途 scene，客户端绑定本租户模板；发送授权检查真实持久 challenge，不能只匹配 application 字符串。发送有效性标记必须持久化且缺少对应 SPI 时拒绝；保留旧 MailSendApi.Command 构造契约。有效性查询不在持有邮件锁时反向锁 challenge，外部 SMTP 始终在数据库事务外；邮件失效门禁不能宣称可撤回已经放行的外部发送，最终挑战消费仍再次校验用途/期限/代次。

C3d1 实施中（2026-11-11）：新增 PasswordStrengthPolicy，只导出现有 IAM 登录密码判定，旧 validate 的调用/错误码保持；新增 MailTemplateQuery，管理调用仍需 mail:template:view、宿主任务需可信租户，返回已发布模板/账号最小元数据，不授予发信权限。identity 增加 V6 邮箱索引密钥与客户端按语言的模板绑定；保存启用配置校验发布版本与 code/minutes 变量，紧急停用不依赖模板可用。邮箱 local part 首版保留大小写和点号/+tag，域名 IDNA/小写，ASCII TLD；SMTPUTF8 本地部分及国际化 TLD 尚不支持，输入明确拒绝。根 reactor 的 IdentityEmailAddressTests、LoginPasswordPolicyTests、ModularityTests、ArchitectureRulesTests、I18nBundlesTests 28 项通过，日志 `backend/target/p09-c3d1-unit.log`。专用库开始 IdentityEmailFoundationTests、LoginPasswordWriteTests，日志 `backend/target/p09-c3d1-integration.log`；未通过前不进入挑战阶段。

C3d1 最终结果（2026-11-11）：根 reactor、专用库与隔离输出执行 IdentityEmailFoundationTests、LoginPasswordWriteTests，7 项通过，日志 `backend/target/p09-c3d1-integration-recheck.log`，构建 7 分 57 秒。首次 `p09-c3d1-integration.log` 只在测试编译遇到 AssertJ 重载/TransactionTemplate 泛型推断歧义，改为显式 String 后通过。结合前次 28 项单元/架构门禁，邮箱基础本地验收完成，identity V6 后仅追加迁移。覆盖真实 MySQL/HTTP 权限、双租户同码、配置版本、已发布模板与草稿隔离、变量/用途/语言/账号撤销、紧急停用、邮箱索引跨 realm/租户与密钥环轮换/篡改拒绝；原后台密码写路径/历史密码及会话撤销回归通过。没有验证码/注册端点，无外部邮件副作用；手册 8.5、README/后端规则已同步，继续 C3d2。

C3d2 契约：先交付 identity 内部持久状态，HTTP 与实际 mail 联动按 C3d3/C3d5 接入。PREPARED→PENDING→VERIFIED→CONSUMED，失败/过期终态擦除目标密文和短码验证值。六位随机码只存带部署键 ID 的 HMAC；proof/grant 均 256 bit 随机且只存摘要。创建与后续投递绑定在调用方同库事务中；验证码尝试使用独立事务并返回拒绝结果，由外层抛错，避免回滚返还猜测次数。正确验证码仅签发短期一次性 grant，grant 消费与凭据变更必须同事务；新用途/客户端/Origin/配置或主体安全代次不能互用。租户/客户端/来源/邮箱冷却及保留量均有界；自动维护使用可信租户目录、默认启用、测试显式关闭。PENDING 验证要求已绑定 deliveryId，单独创建 PREPARED 不能成为可用验证码。

C3d2 实施中（2026-11-11）：新增 identity V7 挑战/预算/速率表与内部状态存储，创建/投递绑定及 grant 消费要求调用方事务，verify 独立提交拒绝结果；同邮箱冷却 60 秒和日额度，租户/客户端/来源与总容量有界。默认维护覆盖停用租户，终态字段显式 ALWAYS 更新为 NULL，不能因 ORM 忽略 null 保留秘密。根 reactor 的 IdentityEmailMaintenanceTests、IdentityEmailAddressTests、ModularityTests、ArchitectureRulesTests、I18nBundlesTests 共 26 项通过，日志 `backend/target/p09-c3d2-unit.log`。专用库开始 IdentityEmailChallengeTests 的 8 项状态/原子性/并发/限流用例，日志 `backend/target/p09-c3d2-integration.log`；该阶段使用合成 deliveryId，只验身份状态，不能称 mail/SMTP 联动已完成。尚未新增公开验证码 HTTP 入口。

C3d2 最终结果（2026-11-11）：根 reactor、专用库、隔离输出执行 IdentityEmailChallengeTests，8 项全部通过，日志 `backend/target/p09-c3d2-integration.log`，构建 10 分 46 秒。覆盖创建/投递绑定回滚、PREPARED 不可验证、独立提交错误次数与第五次失效、grant 唯一签发及同事务业务回滚保留、用途/Origin/proof/client/tenant 拒绝、配置/realm/主体代次变化、重发冷却/邮箱日额度/容量/清理、并发验证与消费唯一成功、grant 到期及不存在挑战的来源预算。结合 26 项单元/架构门禁，C3d2 状态层本地验收完成，identity V7 之后只追加迁移。合成 deliveryId 不代表邮件已入队/投递；尚无匿名邮箱 HTTP 路由。手册 8.6 与后端规则已同步，继续 C3d3。

C3d3 实施中（2026-11-11）：新增 MailSendApi.Guard（保留原8参数 Command 构造、无 guard 的旧指纹/JSON）、MailDeliveryGate、mail V7 持久有效性标识。identity 发送策略核验可信任务/挑战/收件人/短码/模板/用途/到期时刻，不允许把 identity 当任意发送通道；创建 challenge、入队、绑定 deliveryId 同事务。发送准备与 SMTP 前均检查 guard，缺实现拒绝；检查加入 mail 自身的新短事务，使用非锁定读取，不反向锁 identity、不另占连接或访问网络。时间统一至 DATETIME(3) 精度，避免纳秒时刻与回读快照不相等。首轮既有调度/兼容/架构检查27项通过，日志 `backend/target/p09-c3d3-unit.log`；新增检查/旧 Command 字节码兼容测试正在 `p09-c3d3-final-unit-recheck.log` 验证。前次 `p09-c3d3-final-unit.log` 测试编译因 verify 助手遮蔽 Mockito 静态方法失败，已明确限定 Mockito 调用。尚未运行本阶段专用库联动测试，不标记完成。

C3d3 单元门禁（2026-11-11）：`p09-c3d3-final-unit-recheck.log` 的 MailDeliveryGuardsTests、MailDispatcherTests、MailDeliveryCompatibilityTests、ModularityTests、ArchitectureRulesTests、I18nBundlesTests 共31项通过，包括原8参数 Command 编译产物实际链接与旧有效 JSON 不新增空 guard 字段。专用库开始 IdentityEmailDeliveryTests、IdentityEmailChallengeTests、MailOutboxTests，日志 `backend/target/p09-c3d3-integration.log`；使用真实邮件队列/工作状态和本地发送替身，不访问真实 SMTP 供应商。

C3d3 最终结果（2026-11-11）：根 reactor、专用库与隔离输出执行 IdentityEmailDeliveryTests 6、IdentityEmailChallengeTests 8、MailOutboxTests 15，共29项通过，日志 `backend/target/p09-c3d3-integration.log`，构建10分51秒。覆盖纳秒输入与DATETIME(3)快照一致、真实入队/回写/调度及事务外发送替身、失败回滚不留任务、收件人/短码/缺guard拒绝、验证后及发送准备后失效、配置撤销/缺失handler/跨租户/到期拒绝、队列预占释放与正文擦除；既有15项邮件队列/回执回归通过。结合31项最终单元/架构门禁，C3d3本地验收完成，mail V7后仅追加迁移。真实供应商投递仍未验证，未开放邮箱账号HTTP入口；手册8.7和后端规则已同步，继续C3d4a。

### C3d4 账号操作的依赖顺序

1. C3d4a：identity邮箱凭据表、注册/重置挑战发起、原子grant消费与注册/改密/会话撤销，密码哈希使用宿主BCrypt且并发有界；不按Google邮箱合并，不为无密码账号创建恢复密码。先验真实数据库回滚/重复/用途/代次/撤销及Google会话回归。
2. C3d4b：密码登录的独立猜测预算、固定代价的不存在账号验证、有界哈希计算与提交时版本再检查；复用应用会话族，验证并发密码变更与旧证明拒绝。
3. C3d4c：本人高风险再认证与显式邮箱/Google关联；验证新邮箱不代表已证明原账号，拒绝跨主体绑定和移除最后可用认证方式。验证后进入C3d5公开HTTP与C3e二开契约。

C3d4a 实施中（2026-11-11）：新增 identity V8 邮箱凭据（realm独立邮箱索引、加密地址、验证时刻、密码哈希/版本），并给会话记录真实认证方式/时刻；旧行 UNKNOWN/null 保持普通会话兼容，刷新不能更新该证明。注册/重置通过grant消费回调原子修改凭据/主体/会话；Google同邮箱不合并，Google-only/不存在账号的恢复证明不能创建密码。发起注册/恢复采用相同挑战结构，收件人验证不等于存在密码账号；显式绑定暂不开放。PasswordStrengthPolicy复用旧登录规则，BCrypt通过宿主encoder且节点并发默认4、满额即时503，弱密码先拒绝不占hash资源。根reactor IdentityPasswordWorkTests、LoginPasswordPolicyTests、ModularityTests、ArchitectureRulesTests、I18nBundlesTests共28项通过，日志 `backend/target/p09-c3d4a-unit.log`。专用库开始IdentityEmailAccountTests、IdentitySessionTests、IdentityLoginTransactionTests、IdentityApplicationHttpTests，日志 `backend/target/p09-c3d4a-integration.log`；结果未出前不进入密码登录。

C3d4a 最终结果（2026-11-11）：根reactor、专用库和隔离输出运行 IdentityEmailAccountTests 9、IdentitySessionTests 8、IdentityLoginTransactionTests 8、IdentityApplicationHttpTests 9，共34项通过，日志 `backend/target/p09-c3d4a-integration.log`，构建10分30秒。验证密码哈希/邮箱密文、弱密码/缺钥/外层回滚不留半账号且保留grant、重复注册不覆盖、同Google邮箱不合并、未知/Google-only恢复不创建密码、用途/客户端/租户拒绝、重置原子撤销访问/刷新族、其他在途恢复因代次改变失效、旧UNKNOWN/null会话兼容且刷新不制造新认证。Google完整HTTP和原登录状态回归通过。结合28项单元/架构，C3d4a本地通过，identity V8后仅追加迁移；内部服务尚非公网邮箱登录入口，继续C3d4b。

C3d4b 实施中（2026-11-12）：新增密码登录 prepare/CPU验证/finish 三段。prepare独立提交共享租户/来源预算和每realm邮箱目标默认5次/分钟的预算；未知、停用或无密码主体使用启动时预热的同成本dummy hash，返回同一验证失败，不套用新密码写入规则拒绝历史密码。CPU工作不持数据库事务，finish重新验证配置/主体/邮箱/密码版本及30秒准备期限，再签发PASSWORD会话；拒绝外层事务包裹协调器。单元/架构日志 `backend/target/p09-c3d4b-unit.log` 共29项通过；随后加严finish拒绝空hash，已纳入本次专用库编译/验证。正在 `backend/target/p09-c3d4b-integration.log` 运行 IdentityPasswordLoginTests、IdentityEmailAccountTests，尚未标记完成或开放公网密码接口。

C3d4c 设计约束（在4b通过后实施）：敏感绑定必须取得显式一次性再认证grant，不能仅凭会话刚创建/刚刷新或新邮箱验证码放行。grant绑定原subject/session/client、安全代次、动作和目标；邮箱目标使用规范化索引，删除方法绑定具体标识。密码再认证复用有界密码计算与独立预算；Google再认证复用既有一次性Google事务，追加固定intent/原主体会话/目标绑定，普通LOGIN入口不得消费再认证或绑定事务。Google原账号证明必须匹配该主体现有issuer/sub，不按email比对。Google新增关联先消费原账号再认证grant，再验证新Google身份；冲突不合并/不转移他人主体。邮箱绑定先证明原账号、再验证新邮箱；无密码账号不通过普通重置隐式添加密码。方法变更推进主体安全代次并撤销旧会话，删除最后一种可登录方法拒绝；历史已撤销Google关联不能被匿名注册悄悄重新分配。所有复用改动继续回归原LOGIN/会话/HTTP链。

C3d4b 最终结果（2026-11-12）：根reactor、专用库、隔离输出运行 IdentityPasswordLoginTests 7、IdentityEmailAccountTests 9，共16项通过，日志 `backend/target/p09-c3d4b-integration.log`，构建15分11秒，迁移持续推进期间未重启。验证真实BCrypt在事务外、PASSWORD会话绑定、错误/未知/停用同一失败与dummy hash、独立预算抗外层回滚/换IP、30秒准备期限、密码/配置变更后旧验证拒绝、历史含符号密码验证、可信租户/realm/Origin/worker与禁止外层事务、共享realm不同客户端仍绑定audience。既有注册/重置9项回归通过。结合29项单元门禁，C3d4b本地通过；公网密码入口仍待C3d5，继续C3d4c。

C3d4c 子项顺序：c1先实现绑定原subject/session/动作/目标的一次性再认证grant及密码再认证；c2扩展现有Google事务的固定intent并实现Google再认证/新关联，普通登录路径继续隔离；c3接邮箱绑定、移除认证方式和历史关联保护，验证最后一种可登录方法不可删除。每项验收后再进入下一项。再认证存储按realm/subject有界，统一在realm锁后操作grant；不增加会与现有Google/邮箱预算倒序的第三套锁链。

C3d4c1 实施中（2026-11-12）：新增identity V9一次性再认证grant，绑定actor会话/主体代次/完整authority快照/动作/目标；邮箱目标只存HMAC索引。currentActor是纯权威读，业务拒绝由事务所有者处理，不能将此noRollbackFor约定扩大到写服务。密码再认证独立记猜测次数并与普通登录共享目标预算，事务外BCrypt，finish再次核验后签发；授权消费与后续同资源动作共事务。每realm/subject保留量有界，默认2分钟有效、10分钟保留；共用邮箱维护且两类清理独立容错。原维护/密码资源/架构门禁27项通过，日志 `backend/target/p09-c3d4c1-unit.log`。正在专用库执行IdentityReauthenticationTests、IdentityPasswordLoginTests、IdentitySessionTests，日志 `backend/target/p09-c3d4c1-integration.log`；Google再认证、实际绑定和删除尚未实施，未标记完成。

C3d4c1 最终结果（2026-11-12）：根reactor、专用库和隔离输出运行 IdentityReauthenticationTests 6、IdentityPasswordLoginTests 7、IdentitySessionTests 8，共21项通过，日志 `backend/target/p09-c3d4c1-integration.log`，构建13分44秒。验证动作/目标规范化、同事务回滚、唯一消费、跨subject/session/client/tenant拒绝、登录与再认证共享预算抗回滚、注销期间/之后拒绝、realm/subject容量、过期擦除/清理、并发消费唯一成功和配置变更失效；原密码登录与会话回归通过。结合27项单元/架构，c1本地通过，identity V9之后只追加迁移；继续c2 Google用途隔离与关联，不据此宣称完整绑定已交付。

C3d4c2 实施中（2026-11-12）：identity V10为Google事务增加固定LOGIN/REAUTH/LINK_GOOGLE用途及原actor/动作/目标快照。普通登录与操作入口不能互用；原账号再认证必须匹配其现有issuer/sub，不能以同邮箱替代。新增关联先原子消费原账号授权并创建新的Google事务，再在事务外验证新Google身份；完成时再次校验原会话/安全代次，关联修改与旧会话撤销同事务提交。逻辑删除的Google身份保留归属，只允许原主体显式恢复；匿名登录不重新分配历史关联。当前内部实现未开放新的HTTP入口。根reactor协议/架构/i18n共31项通过，日志 `backend/target/p09-c3d4c2-unit.log`；正在专用库执行IdentityGoogleOperationTests及原Google事务/会话/HTTP回归，日志 `backend/target/p09-c3d4c2-integration.log`，结果未出前不进入c3。

C3d4c2 首轮集成结果：原IdentityApplicationHttpTests 9、IdentityLoginTransactionTests 8、IdentitySessionTests 8，共25项回归全部通过；新IdentityGoogleOperationTests 8项中6通过、2错误。原因是回滚故障夹具对MANDATORY事务代理直接Mockito stubbing，先触发无外层事务异常并污染下一项stubbing；已改为对测试spy的目标注入，仍通过原代理执行实际调用，不改生产事务要求。补充同主体重复关联不撤销会话的断言。专用库/根reactor重新运行整个IdentityGoogleOperationTests，日志 `backend/target/p09-c3d4c2-recheck.log`；首轮日志保留，不将夹具错误记成功。

C3d4c2 最终结果（2026-11-12）：根reactor/专用库重新运行IdentityGoogleOperationTests，8项全部通过，日志 `backend/target/p09-c3d4c2-recheck.log`，构建13分42秒；结合首轮已通过的25项原登录/会话/HTTP回归及31项协议/架构/i18n，本子项本地验收完成。验证用途隔离、同邮箱不同sub拒绝、授权消费与创建失败回滚、关联与会话撤销回滚、他人活动/历史身份冲突、原主体显式恢复、重复关联不撤销会话、容量限制及提供方调用期间安全代次变化拒绝。identity V10之后仅追加迁移。没有新增关联HTTP入口，真实Google交互仍待联调，继续c3。

C3d4c3 验收计划：在identity追加邮箱绑定原会话快照字段及内部凭据操作服务，不新增公网入口。发起绑定先消费EMAIL_BINDING再认证授权并原子创建目标邮箱挑战；完成时同时验证邮箱grant与原actor/session/client/安全代次。Google-only可在此显式操作中为新验证邮箱添加密码，普通reset仍不能补密码；已有密码不得借绑定请求静默替换。移除密码保留已验证联系邮箱；移除Google保留历史归属；必须保留当前客户端可用的至少一种认证方式。测试覆盖回滚、换会话/跨主体/跨租户/目标不符、最后方式拒绝、邮箱独占与会话撤销，回归原邮箱账号/再认证服务后再进入C3d5。

C3d4c3 实施中（2026-11-12）：追加identity V11邮箱绑定原actor快照，新增内部IdentityCredentialOperations。原账号授权消费与目标邮箱挑战/邮件入队同事务；完成同时校验原会话与邮箱grant，不能换subject/client/session。已有密码不通过绑定静默覆盖；无密码账号可经明确新密码参数添加，普通reset仍拒绝。移除密码保留验证邮箱，移除Google保留历史；根据当前客户端Google开关/realm密码开关计算剩余登录方式。变化与grant消费、主体代次提升及会话撤销一起提交。单元/架构/i18n 27项通过，日志 `backend/target/p09-c3d4c3-unit-recheck.log`；初次测试辅助void lambda编译失败保留于 `p09-c3d4c3-unit.log`。正在专用库执行IdentityCredentialOperationTests、IdentityEmailAccountTests、IdentityReauthenticationTests，日志 `backend/target/p09-c3d4c3-integration.log`；未开放新HTTP或标记本子项完成。

C3d4c3 最终结果（2026-11-12）：根reactor/专用库运行IdentityCredentialOperationTests 9、IdentityEmailAccountTests 9、IdentityReauthenticationTests 6，共24项通过，日志 `backend/target/p09-c3d4c3-integration.log`，构建11分20秒。验证原账号/新邮箱双重证明、目标绑定、跨subject/session/client/tenant拒绝、邮箱独占/已有密码不可覆盖、Google-only显式加密码与普通reset拒绝、最后认证方式保护、联系邮箱保留、Google历史归属、失败回滚、会话与配置失效及并发移除唯一成功。结合27项单元门禁，本子项本地验收完成，identity V11之后只追加；继续C3d5正式HTTP与接口调用说明。

C3d5 接入顺序：a先开放邮箱注册/重置挑战、验证、完成及密码登录，复用现有应用安全链、H5 HttpOnly proof Cookie/CSRF与Native JSON凭据；审查精确公开方法白名单与请求治理清单，跑真实MockMvc和原Google/后台/TOTP/机器回归。b再接本人认证方式查询、密码/Google再认证、Google/邮箱绑定和移除，精确扩展平台Self白名单，保留业务字面audience要求；验证匿名/跨租户/后台身份/错client拒绝及凭据不泄露。每项通过后补齐对应调用示例，不提前宣称后续入口已可用。

C3d5a 实施中（2026-11-12）：新增5个精确POST邮箱入口，公开用途仅REGISTRATION/PASSWORD_RESET。WEB注册/重置proof分用途HttpOnly Cookie，拒绝JSON proof；Native只接JSON proof且拒绝本客户端的认证/挑战Cookie。验证码拒绝在独立事务提交后交给HTTP处理，不套后台账本；注册/密码登录复用应用会话Cookie写入，重置/退出清理当前客户端Cookie。首轮测试访问package-private摘要助手编译失败，改为测试自身计算已定义的SHA-256，没有扩大生产可见性。`p09-c3d5a-policy-review-recheck.log` 27项协议/模块/架构/i18n/端点审查通过；更新精确基线后关闭更新标志，`p09-c3d5a-policy-check.log` 5项普通检查通过。专用库执行IdentityEmailApplicationHttpTests、IdentityApplicationHttpTests、AuthFlowTests、TotpAuthenticationTests、OpenAuthenticationTests，日志 `backend/target/p09-c3d5a-integration.log`。手册8.13已给出H5/Native调用示例，仍标验收中，尚未进入b。

C3d5a 最终结果（2026-11-12）：根reactor/专用库运行IdentityEmailApplicationHttpTests 6、IdentityApplicationHttpTests 9、AuthFlowTests 4、TotpAuthenticationTests 11、OpenAuthenticationTests 6，共36项全部通过，日志 `backend/target/p09-c3d5a-integration.log`，构建17分35秒。验证H5 Cookie/CSRF与Native proof/Bearer、注册/重置/密码登录/会话撤销、错误验证码次数独立提交、未知账号重置不创建凭据、跨tenant/client/purpose及重放拒绝、Native挑战Cookie拒绝、proof/密码/会话令牌不泄漏；原后台/TOTP/Google/机器链路回归通过。结合27项审查门禁和5项关闭更新标志的普通门禁，a本地验收完成。手册8.13接口可用于本地接入，真实浏览器Cookie策略/邮件送达仍未联调；继续b。

C3d5b 实施中（2026-11-12）：增加11个精确本人账号端点及内部最小方式查询。主体只取安全链Access，GET不接subject选择；只返回本人验证邮箱、密码配置标志和Google关联ID/创建时刻，不返回hash/密文/provider sub。Google再认证/新关联和邮箱绑定分别使用独立HttpOnly proof Cookie；Native使用JSON proof并拒绝这些Cookie。邮箱绑定验证新增verifyBinding，在独立尝试事务内复查原actor，另一会话不能提前消费正确验证码；普通注册/重置verify保持原路径。平台Self架构白名单只扩展此控制器的11个方法，业务仍需字面audience。27项策略审查门禁通过 `p09-c3d5b-policy-review.log`，关闭更新标志后的普通门禁5项通过 `p09-c3d5b-policy-check.log`。集成首个命令误写forceConnectionTimeZoneSession，于清库阶段定位并只停止本次Maven/测试子进程（17964/32748），未动现有服务；该轮不算验收，日志保留 `p09-c3d5b-integration.log`。修正为forceConnectionTimeZoneToSession=true后重新执行6组数据库/HTTP专项，当前日志 `backend/target/p09-c3d5b-integration-recheck.log`，尚未标记完成。

C3d5b 首轮有效集成结果：47项中43通过、4个新H5账号连续操作失败，日志 `p09-c3d5b-integration-recheck.log`。真实原因是Spring的CsrfAuthenticationStrategy把每次恢复已签发应用身份当成新登录，在成功请求后清除csrf Cookie，下一写请求403；旧测试未把该响应Cookie应用到下一请求而未暴露。保留STATELESS、CsrfFilter双重提交及数据库会话CSRF检查，仅将/app链的CSRF认证事件策略改为框架NullAuthenticatedSessionStrategy，由现有登录/刷新显式轮换、退出/改密/改凭据显式清理。新增无数据库的真实安全链测试，验证连续GET/POST不清Cookie/不建HttpSession，缺头、错头和伪造匹配Cookie/头仍拒绝。正在 `p09-c3d5b-csrf-unit.log` 验证；此为生产链路修正，不将首轮失败归为夹具或标记完成。

C3d5b CSRF修正单元门禁：`p09-c3d5b-csrf-unit.log` 共28项通过，含真实ApplicationSecurityConfiguration过滤链专项，连续请求保持Cookie且三类伪造/缺失证明均403，无HttpSession创建。正在专用库从根reactor重跑IdentityAccountHttpTests、IdentityEmailApplicationHttpTests、IdentityApplicationHttpTests，日志 `p09-c3d5b-http-final.log`；同资源凭据/Google操作/邮箱状态的25项原专项已在前轮通过，当前改动只涉及应用链CSRF认证事件，不重复清库运行无关领域测试。最终结果未出前不标记完成。

C3d5b 最终结果（2026-11-12）：`p09-c3d5b-http-final.log` 中IdentityAccountHttpTests 7、IdentityApplicationHttpTests 9、IdentityEmailApplicationHttpTests 6，共22项全部通过，构建7分26秒。H5连续本人操作、Google再认证/关联/删除、Native邮箱/Google绑定、本人查询隔离、缺CSRF及跨session/client/tenant拒绝、凭据变化旧会话撤销均通过；修正CSRF恢复策略后旧Google/邮箱链路正常。结合28项最终单元/架构/端点普通基线及前轮已通过的25项领域回归，C3d5b本地验收完成。手册8.14已更新可调用接口；真实Google/邮件与浏览器策略仍单列，继续C3e。

C3e 实施计划：新增identity根包SubjectQuery、SubjectContactQuery及SubjectAccessPolicy，DTO不含内部实体/凭据。current只允许真实应用会话读取本人基本主体；按ID查询及联系邮箱读取必须匹配所属application的策略，默认拒绝、重复策略启动失败。策略检查可信actor/用途/业务对象归属，邮箱只来自经挑战验证的identity凭据，不采用Google claim作为业务联系邮箱。租户来自ActorContext；APP_USER还必须同audience/realm并复核会话，admin/machine/job无默认跨主体授权。查询与敏感读取不使用缓存。按双租户/多realm/不同actor/禁用配置/撤销会话/缺策略测试，并提供独立业务教学接入样例。提案中的EmailChallengeApi为草案名，最终凭据变更复用8.13/8.14已验证HTTP，避免让业务直接传入内部授权快照或签发会话；Java业务通知组合公开SubjectContactQuery和MailSendApi。此取舍同步方案与手册后再进入C4。

C3e 实施中（2026-11-12）：新增SubjectReadRequest/SubjectQuery/SubjectContactQuery/SubjectAccessPolicy及受控实现。current复查真实APP_USER会话；find与邮箱按用途/内容独立授权，策略接收原actor，所有读取仍绑定当前租户、realm及已启用application客户端。未验证Google邮箱不作为联系邮箱，停用主体不返回联系信息，查询不缓存。24项单元/模块/架构/i18n/端点普通基线通过，日志 `p09-c3e-unit.log`。专用库正在执行SubjectQueryTests，日志 `p09-c3e-integration.log`。独立教学目录4个Java文件已使用根reactor类路径和Java21 javac编译成功，输出仅在backend/target/p09-examples；另追加可复跑的样例编译测试，待当前数据库任务结束后执行，未把样例加入生产构建。手册8.15与方案公开面已同步，尚未标记C3e完成。

C3e 最终结果（2026-11-12）：`p09-c3e-integration.log` 的SubjectQueryTests 5项全部通过，构建11分20秒；验证真实会话/精确audience、用途/内容授权、原actor恢复、跨租户/realm/application拒绝、无超管/机器/job默认邮箱读取权、Google claim不作验证邮箱及停用状态不缓存。24项单元门禁已通过；`p09-c3e-example.log` 的独立教学源码编译测试1项通过，构建20.751秒，未把示例加入宿主构建。本子项与C3后端本地验收完成，真实外部联调和C8管理页面仍待办；继续C4。

### C4 文件服务实施顺序

1. C4a：租户存储profile/不可变revision、Local/OSS/S3/R2配置规则、加密凭据、权限和管理HTTP；保存不访问云。旧FileQuery/本地目录保持原契约，本子项不宣称云或安全上传已可用。验证双租户同名/ID/凭据隔离、版本、停用读/上传开关、路径/endpoint拒绝和HTTP秘密不回传。
2. C4b：公开文件场景Catalog/授权SPI、租户场景草稿/发布、平台硬限制与profile归属。业务场景显式登记，权限/类型/上传方式是交集，不能关闭扫描；验证跨应用/租户与缺策略拒绝。
3. C4c：新文件/上传任务/分片/配额/位置快照、Local隔离与冻结存储、流式传输和一次性并发状态；外部I/O与短事务分开。保留旧上传ACCEPTED/RUNNING/UNKNOWN与同键输入约束，不把未知结果当失败重传。
4. C4d：统一内容/魔数/容器/资源限制与隔离扫描协议，完整扫描通过才READY；文件名、恶意/截断/假MIME/超限/服务不可用等矩阵。图片重编码、视频受限探测、文档/压缩包按场景策略；没有完整扫描能力时保持隔离。
5. C4e：业务绑定/授权读取/Range/删除恢复/清理与旧FileQuery、头像、品牌/附件的兼容接入。旧入口须走相同安全门禁，避免形成旁路；真正外部存储/扫描不占数据库事务，只有确认回滚才能清理已写字节。回归FileUploadRequest/FileUploadHttp/FileRollbackCleanup与File/Profile/品牌专项，再进入S3/R2/OSS适配。

C4a 实施中（2026-11-12）：新增file V3租户存储profile/不可变revision，provider仅LOCAL/OSS/S3/R2。本地只选部署localRoots别名，不接受服务器路径；对象前缀拒绝路径穿越/ADS/Windows设备名。S3/OSS使用精确标准区域endpoint，R2仅32位account S3 endpoint及auto region；首版不接任意兼容endpoint/CDN/内网地址。保存是无网络配置操作，运行时DNS/IP/SDK能力仍待后续适配。云accessKeyId/secret/sessionToken一起做租户/版本绑定加密，只回传末4位hint；空输入保留并重新封装，替换推进凭据版本，临时凭据须有截止且禁止无新秘密延长。紧急stop分UPLOADS/READS/ALL且不解密。管理REST复用LOCAL请求治理/写入指纹；没有更改旧文件与上传路径。`p09-c4a-policy-review.log` 25项规则/架构/i18n/端点审查通过；正在普通基线和专用库验收，未标完成或宣称云传输已可用。

C4a 最终结果（2026-11-12）：普通端点基线 `p09-c4a-policy-check.log` 1项通过；专用库 `p09-c4a-integration.log` StorageProfileTests 6项全部通过，构建8分40秒。验证同名双租户/跨ID拒绝、加密绑定及不可变revision、凭据保留/替换、缺钥停用与版本冲突、Local无秘密配置、临时凭据到期/禁止虚假延期、HTTP权限/不回传原凭据与WRITE_ONLY变更指纹冲突。结合25项初始门禁，C4a本地完成，file V3后仅追加。保存无网络副作用，尚未接云SDK或新上传链路；继续C4b。

C4b 实施中（2026-11-12）：新增公开FileSceneCatalog/Definition、FileScenePolicy、FileSceneAccess、FileFormat/UploadMode与file V4场景草稿/不可变发布。真实模块包归属/Modulith元数据限制application命名空间，缺策略拒绝，重复注册启动失败。内置后台图片/附件仅提供Catalog和原后台权限策略，不自动发布或改变旧上传路径。配置只能缩小Catalog/部署类型/大小/方式，引用本租户启用且授予application的profile；发布固定具体配置/凭据版本，草稿编辑与profile更新不改变旧发布，实时停用/应用撤权仍阻止准入。运行选择取可信actor/租户当前安全代次，业务不能传Bucket/key或关闭扫描。首轮规则/模块/架构/i18n/端点审查25项通过 `p09-c4b-policy-review.log`；补租户安全检查后以普通基线重跑 `p09-c4b-final-unit.log`，随后进入专用库StorageSceneTests。配置类型/20GiB准入硬上限不代表处理器/大文件容量已经验收，尚未交付传输或READY。

C4b 最终结果（2026-11-12）：`p09-c4b-final-unit.log` 普通基线、模块/架构/i18n与Catalog策略共25项通过；`p09-c4b-integration.log` StorageSceneTests 6项全部通过，构建14分39秒。验证草稿与发布隔离、profile配置版本固定/重发采用新版本、同租户多空间偏好、跨租户/应用拒绝、Catalog/平台上限不可放宽、实时停用/撤权/租户停用拒绝、编辑/发布权限与管理HTTP。file V4已验收，之后仅追加迁移。没有执行传输/扫描，场景枚举不代表处理器能力；继续C4c。

C4c依赖拆分：c1先实现内部持久上传任务/上传者及tenant generation/场景配置快照、同键指纹、租户字节/任务/速率预占、任务状态与短事务操作，尚不写文件或签名；c2再实现Local受控目录、无跟随链接排他写、分片限流/摘要/幂等与冻结合并，网络/文件I/O在数据库事务外，未知提交先核对不可覆盖字节；c3接公开FileUploadApi与后台/应用上传HTTP及续传/取消，最后进入独立扫描门禁。每项验证再推进；C4c不允许返回READY，READY只由C4d完整检查产生。

C4c1 实施中（2026-11-12）：新增file V5内部持久upload task/part/budget。任务按可信ActorRef＋后台portal隔离，原请求指纹用租户任务引用HMAC，同键同输入复用、异输入拒绝；保存tenant generation、scene/空间版本、目标类型/大小/全文件声明SHA256。准入预占3倍声明字节覆盖临时副本，活动/保留任务与分钟/日额度有界；本子项只准Local SERVER/MULTIPART，云能力待各适配器验收。分片预先固定尺寸/摘要/claim；RECEIVING重放不给第二次执行权，STORED确认幂等，错误字节拒绝；完整片数/总量后仅VERIFYING，不产生READY。取消/过期保留配额直到真实清理确认，未知写结果不可按超时自动释放。所有方法独立短事务且无文件/网络I/O；尚无新公网上传端点。文件名校验拒绝路径/设备名/双危险后缀/隐形控制符，但不替代后续魔数/容器/恶意软件检查。当前 `p09-c4c1-unit.log` 执行门禁，下一步专用库UploadTaskStoreTests验证并发/回滚/配额/状态。

C4c1 最终结果（2026-11-12）：`p09-c4c1-unit.log` 26项单元/架构/i18n/端点普通基线通过；专用库 `p09-c4c1-integration.log` UploadTaskStoreTests 6和StorageSceneTests 6，共12项全部通过，构建13分15秒。覆盖同键/异内容、跨用户/portal/tenant、独立接受抗外层回滚、分片唯一执行/确认幂等/摘要与尺寸拒绝、并发初始化和领取唯一成功、配额/过期保留、取消后迟到确认拒绝及当前租户/空间安全校验。file V5已验收，后续只追加迁移；本子项没有写真实文件、没有公网上传端点、不会产生READY，继续c2本地字节流和冻结快照。

C4c2 实施中（2026-11-12）：新增LocalStorageLocation按任务固定revision解析部署根，LocalTransferStorage有界线程池/64KiB缓冲/总截止/磁盘水位、逐级目录/无跟随链接/排他创建/文件锁与大小SHA256核验；LocalUploadCoordinator禁止外层事务，文件I/O与任务提交分离。追加file V6分片尝试计数与冻结回执；只有检查已停止写入的MISMATCH片才允许一次新claim路径重试，旧片保留待清理，ABSENT/BUSY不给执行权，最多两次片文件＋一次冻结符合3倍预占。合并逐片重验并生成独立不可覆盖快照，成功仅SCANNING；重复/丢提交确认从原文件核对不重复写。`p09-c4c2-unit.log` 共29项，28通过、Windows符号链接创建权限不足跳过1项，无失败；已追加本机目录junction用例待补跑。专用库正在LocalUploadTests与UploadTaskStoreTests联动，日志 `p09-c4c2-integration.log`，未宣称完整隔离扫描/公网上传已交付。

C4c2 在验收期间补充：本机符号链接创建受限，追加无需该权限的Windows目录junction真实拒绝测试。另发现线程池在I/O前拒绝会留下无法重试的空claim，增加仅限LocalTransferStorage.NotStarted明确前置拒绝的RETRYABLE/releaseUnstartedFreeze路径；超时/普通异常/ABSENT或BUSY均不可调用该释放。追加数据库用例验证繁忙可重试、未知领取保持RECEIVING。为不覆盖正在执行的数据库测试class文件，最终单元/架构使用独立输出target/p09-local-final/target，日志 `p09-c4c2-final-unit.log`；当前数据库首轮完成后再补跑最新源码，不能用旧产物结果覆盖新增分支的验收。

C4c2最终单元门禁：独立输出 `p09-c4c2-final-unit.log` 共30项，29通过、1项符号链接fixture因Windows权限不足跳过，无失败；Windows目录junction真实创建/拒绝/未写外部目录用例通过。后续还补了inspect成功确认前force刷盘；最新源码将与LocalUploadTests/UploadTaskStoreTests/LocalTransferStorageTests一起补验，数据库首轮仍在进行。

C4c2首轮联动结果：`p09-c4c2-integration.log` LocalUploadTests 5＋UploadTaskStoreTests 6，共11项全部通过，构建15分02秒，使用真实临时文件与专用MySQL；验证分片/冻结字节、partial新claim一次恢复、丢失确认只核对不重复写、并发忙碌写者不覆盖、全文件/源片篡改不能SCANNING与外层事务拒绝。首轮编译之后新增的NotStarted恢复分支、Windows junction与inspect确认前刷盘仍需最新产物验证；正在 `p09-c4c2-integration-final.log` 重跑LocalUploadTests、UploadTaskStoreTests、LocalTransferStorageTests，仍不标本子项完成。

C4c2 最终结果（2026-11-12）：最新源码 `p09-c4c2-integration-final.log` LocalUploadTests 6＋UploadTaskStoreTests 6＋LocalTransferStorageTests 7，共19项，18通过、1项因Windows无符号链接创建权限跳过、无失败。Windows目录junction实测拒绝；原始文件写/锁/冻结、partial单次新claim恢复、已写丢确认核对、NotStarted明确前置拒绝可重试、ABSENT未知不重领、冻结全量摘要及前置刷盘均通过。前轮独立最终单元/架构30项中29通过1同类跳过。file V6已验收，后续只追加迁移。真实测试最大对象约2MiB+17字节，未据此宣称20GiB或杀毒扫描已验收；成功仅SCANNING，无读取/绑定，新公网上传API在c3接入。

C4c3接入约定：公开FileUploadApi与不可变DTO隐藏内部任务/配置实体。后台使用独立`/api/file/upload-tasks`，保留旧`/api/file/uploads`回执；APP_USER使用`/app/file/upload-tasks`且由共享应用链认证，文件平台本人任务入口按具体方法扩展受审Self白名单，业务自己的控制器仍要求字面audience。file层每次校验当前actor、scene.application精确等于APP_USER audience、任务owner中的realm/client，不能靠该注解访问他人任务。文件模块自有过滤器在认证后限制JSON/分片实际字节及节点并发，Cookie写入继续CSRF；仅将分片尺寸/摘要头加入精确CORS白名单。初始化/续传/完成/取消走原生任务状态，所有I/O在事务外，返回任务状态而非伪造READY。完成还需扫描阶段，取消物理清理在后续生命周期实现；验证实际管理/APP身份、拒绝路径、真实字节、跨租户/owner/client与旧上传/身份回归。

## 外部联调待办

Google Web/Android/iOS 真实客户端及回调；真实测试收件箱与域名送达；AWS S3、R2、OSS 专用 Bucket/凭据/区域。准备后按设计矩阵验收，结果不从模拟测试推导。暂缺资源不阻塞用户授权的本地实施。

C4c3实施中（2026-11-12）：新增公开FileUploadApi及不可变Command/Task/Part，后台/api/file/upload-tasks和应用/app/file/upload-tasks各6个方法；后台NATIVE保留file独立事务/字节I/O，应用逐方法受审Self白名单并由scene/owner/realm/client真实校验。UploadTaskRequestFilter在认证后限制节点16并发、JSON64KiB及实际分片字节/声明尺寸/重复头/压缩编码；应用CORS仅加x-upload-size/x-upload-sha256。p09-c4c3-unit-review.log 26项单元/架构/Modulith/真实应用链全部通过，已显式审查端点策略基线；新UploadTaskHttpTests真实文件/专用库身份隔离联动与普通基线复跑仍待验证，阶段不标完成。

C4c3教学与补充验证：examples/integration-services新增独立业务AttachmentScenes/AttachmentAuthorization/AttachmentUploadService，只通过公开FileUploadApi展示小文本初始化/片传输/完成与原键状态恢复；真实业务授权仍由二开实现，禁止直接把SCANNING写为可读附件。独立输出p09-c4c3-example.log中7个Java教学源文件编译校验1项＋UploadTaskRequestFilterTests 5项，共6项全部通过；未覆盖正在运行的数据库测试class输出。手册11.2已同步Java/HTTP/CSRF/分片/错误状态和边界，数据库联动未结束。

C4c3首轮联动未通过：p09-c4c3-integration.log的普通SecurityEndpointInventoryTests 1、旧FileUploadHttpTests 2、ApplicationSecurityChainTests 1通过；UploadTaskHttpTests 5项因测试配置minimum-write-interval=1ms低于既有100ms下限，Spring上下文启动失败，尚未执行新HTTP断言。已修正测试为100ms并为重复写显式间隔150ms，未放宽生产规则，下一轮只重跑失败的新HTTP专项。

C4c3最终结果（2026-11-12）：修正测试冷却配置后，p09-c4c3-http-recheck.log的UploadTaskHttpTests 5项全部通过（构建6分45秒）。真实本地约1MiB+17字节分片/重放/冻结、原键回执、跨租户/用户/portal拒绝、真实数据库应用会话与scene策略、同会员跨client任务拒绝、错误audience/旧client Bearer/后台路由拒绝、WEB CSRF与精确CORS、正文/重复头拒绝、ABORTING及迟到上传均验证。APP场景授权使用明确测试替身模拟业务策略，生产内置场景继续拒绝APP_USER；会话fixture直接调用内部可信签发，仅用于准备真实测试会话，不冒充Google外部登录联调。首轮普通端点基线、旧上传HTTP及应用安全链共4项通过；单元/架构26项、教学编译与过滤器6项通过。C4c本地验收完成，最大容量/安全扫描/READY/云适配仍未交付，继续C4d。

C4d实施顺序（依赖C4c3）：d1实现无复杂解析的完整流式尺寸/摘要/类型/UTF-8预检，不输出clean/READY；d2实现受限独立worker及图片重编码、PDF/Office/ZIP/文本/MP4结构检查，CPU/内存/输出/截止均有界并提供部署隔离入口；d3实现ClamAV/受控扫描完整冻结副本的协议、引擎版本/容量/超时与不可用拒绝；d4实现持久扫描领取/租约/fence/报告/重试和最终发布，只有完整内容门禁与恶意软件扫描共同通过才READY，派生图必须同样绑定摘要。每子项先验证记录后推进；旧接口兼容、绑定/读取、可靠清理与预算释放由C4e衔接。d1只预检不把魔数当木马检测；暂不修改已验收file V1～V6迁移。

C4d1实施：新增纯Java FileContentProbe，以64KiB固定缓冲遍历完整冻结流，校验期望字节/全文件SHA256、PNG/JPEG/GIF/WebP/MP4/PDF/ZIP初步签名及UTF-8 Unicode scalar/控制字符；Office仅识别ZIP候选，不冒充容器核验，JSON/CSV/文本候选也不代表结构或杀毒通过。拒绝常见可执行/活动标记前缀，尺寸/读取截止/中断有界；阻塞流读取须由外层传输/worker另行限时。它尚未接任务发布，不改变SCANNING。首轮p09-c4d1-unit.log因测试通配import中Signature同名编译失败；已改为精确MessageDigest导入，并补充阻塞EOF返回后再次核验截止时间的测试，p09-c4d1-unit-recheck.log正在验证。

C4d1最终结果（2026-11-12）：p09-c4d1-unit-recheck.log共29项（FileContentProbeTests 9、架构17、普通端点基线1、Modulith2）全部通过。验证完整流尺寸/摘要、格式伪装、WebP长度/MP4品牌、跨缓冲与逐字节UTF-8、非法Unicode/控制符、前缀拒绝、额外字节限读、流关闭/中断/截止与EOF后超时。专门验证JSON/CSV及标准杀毒测试标记仍可能只是文本候选，因此Candidate不是clean或READY；尚未接发布，也没有声称复杂解析或杀毒完成。继续C4d2隔离worker。

C4d2细分：d2a先实现独立worker的有界请求/报告协议、TEXT/JSON/CSV受限检查及真实子进程截止/输出/内存验证；d2b加入图片完整解码/重编码及受控输出；d2c加入PDF/Office/ZIP；d2d加入MP4探测与强制隔离容器执行器。生产只能通过受控隔离执行器运行复杂解析，缺运行时/镜像/处理器保持ERROR，不能回退宿主JVM解析。当前PATH没有docker/podman/ffprobe，标准Docker安装路径也不存在；本地独立JVM协议测试不冒充容器网络/OS隔离验收，相关部署验证另列边界。worker源码仍归backend的file内部包，不新增业务模块或生产业务源码。

C4d2a正在验收：InspectionProtocol严格绑定schema/引擎/原始格式/大小/摘要/派生预算，禁止任意输出路径；ContentInspectionWorker独立入口处理TEXT/JSON/CSV，未知处理器返回ERROR。BoundedInspectionProcess限制节点子进程并发、输入/输出各16KiB、总截止，清除继承Java/Docker选项环境，失败终止子进程并保留未知活进程的名额。初编译因Jackson3 JsonFactory已移至core.json包失败，已修正；p09-c4d2a-unit-recheck.log 38项中36通过，2项待修正：架构禁止stdout需要为唯一独立worker的有界管道协议登记精确例外；超时测试读到了fixture尚未写完的PID文件，改为写临时文件后rename发布。已补CSV Unicode空白/格式前缀公式拒绝、关闭后拒绝新进程与确认子进程死亡后的容量回收；继续复跑，不标完成。

C4d2a复跑p09-c4d2a-unit-final.log被并行config修改的瞬时编译错误阻断：BuiltinDictionaryCatalog调用seeds时，该方法当轮编译仍为非静态；随后只读核验当前BuiltinDictionaries.seeds已由并行工作改为static。未修改/回滚config文件，将基于当前源码重跑本专项。
