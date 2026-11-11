# 安全防御专项实施进度

依据：[安全方案](proposals/P10-security-defense-and-branding.md)。2026-11-11 用户授权“密码策略不改当前策略。其他的按计划开始实施”。全部阶段持续保留，阶段通过不代表整体完成。

## 范围与协调

- 分支 main，仅通用基座。保留所有并行 P07/P08/P09/租户平台/前端修改，不重启用户服务；每次共享文件编辑前复读。
- 登录密码、安全操作密码、弱密码列表及相互不同规则全部保持现状；回归原密码策略测试。
- 品牌、域名、平台运维身份由正在实施的租户平台 P10 唯一拥有；本专项接入其契约并做安全验收，不重复实现。旧 public API 删除按该已确认方案，原安全草案的兼容桥接不再适用。
- auth 限流和 extension 工作账本保持原语义；common 提供通用治理契约，defense 不成为业务正确性的强制依赖。跨数据源写操作必须由业务事务资源保存幂等/Outbox，不能平台账本先提交后声称原子性。
- 后端隔离输出 `target/security-defense/target`（保留 test-classes 后缀）、独立 `sma_test_security_defense` 库；不使用开发库、默认测试库或其他专项库。前端使用独立测试配置/端口，不触碰现有会话。
- 默认同源；可信代理/外部通知均显式配置。站内＋受控 Webhook 为第一条投递链，邮件待 P09 正式契约接入。只向本地测试接收端演练，生产接收组由部署者配置。
- 新幂等能力按接口登记和执行语义逐项接入；不以 URL+正文短期哈希替代真实业务键，不把 UNKNOWN 当失败后自动重试。

## 顺序与验收

| 任务 | 依赖 | 范围与验证 | 状态 |
| --- | --- | --- | --- |
| S0 | 无 | 当前架构/并行决策、隔离输出、API 策略清单、密码不变、明确平台/租户边界 | 完成（本地） |
| S1.1 | S0 | Web 来源/内容类型防护、受控 URL 策略、SQL 动态结构门禁；正常/拒绝路径和架构验证 | 完成（本地） |
| S1.2 | S1.1 | 全站输出审查、页面 CSP/安全头、身份/对象/批量拒绝矩阵；隔离真实浏览器验证 | 完成（本地；生产CSP仍为观察策略） |
| S1a | S1.2 | 通用限流、互斥、持久幂等/回执/多实例；事务和故障矩阵 | 完成（本地数据库/双服务实例） |
| S1b | S1a | 各写接口业务唯一性、回执与兼容适配；按[接入子计划](security-request-rollout.md)B1～B6逐项验证，不跳过未覆盖项 | 完成（本地；快照与并行差异边界见记录） |
| S1c | S1b | 前端统一输入防抖、提交门禁、慢响应与 UNKNOWN 恢复；按[前端拆分](security-frontend-progress.md)C1～C4逐项验证 | 进行中（C1/C2/C3a/C3b完成，C3c进行中） |
| S1d | S1c | 慢网连点20次、超时断连、跨标签/双节点、无误合并正常操作 | 待实施 |
| S2 | S1d | 受信来源、事件采集/脱敏/平台租户隔离、容量/故障与查询 | 待实施 |
| S3 | S2 | 诱捕、关联、站内＋Webhook持久投递、去重/未知/失败恢复；本地完整协议演练 | 待实施 |
| S4 | S3与租户平台品牌阶段 | 接入单一品牌实现，资产/URL/权限/版本回退/公开投影安全验收 | 待实施 |
| S5 | S4 | 观察模式、短期封禁/解封、多节点与受控代理/WAF对接、管理网恢复 | 待实施 |
| S6 | 全部 | 标准根 reactor、前端 verify、隔离安全/容量/恢复、README/规则/部署与二开文档 | 待实施 |

## 实际记录

2026-11-11：开工核对 main、全量未提交状态、AGENTS 专项规则与 P09/租户平台最新进度。P09 邮件配置阶段进行中；租户平台数据源身份阶段进行中。未引用旧测试数作为本轮证据，未修改密码规则或运行服务。

S0：已创建专用 `sma_test_security_defense` 库，仅给既有本机测试账号授予该库权限。基于编译后注解生成[接口清单](security-endpoint-inventory.md)，其中待适配仍为未完成。根 reactor：`backend/mvnw.cmd -B -ntp -f pom.xml -pl backend -am -Dtest=SecurityEndpointInventoryTests,ModularityTests,ArchitectureRulesTests -Dsurefire.failIfNoSpecifiedTests=false -Dsma.verify.directory=target/security-defense/target -Dsma.security.inventory=target/security-defense/endpoint-inventory.md test`，19 项通过、BUILD SUCCESS，日志 `backend/target/security-s0.log`。首次 powershell.exe 标准脚本被当前进程执行策略阻止，改用同一标准 Maven Wrapper 根 reactor，无修改系统执行策略。

S1.1：根 reactor 隔离输出执行 `BrowserRequestFilterTests,WebAddressesTests,SqlStructureSafetyTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests,LoginPasswordPolicyTests`，32 项通过（`backend/target/security-s1-1.log`）；独立真实 MySQL 执行 `BrowserSecurityIntegrationTests,ParameterTests,AuthFlowTests,OpenAuthenticationTests`，13 项通过（`backend/target/security-s1-1-integration.log`）。包含真实注册过滤器、双语415、认证仍拒绝匿名、绑定SQL特殊字符按字面查询、跨租户、危险品牌URL拒绝及后台/机器协议回归。已补 dev 精确来源和隔离E2E来源参数（node --check 通过）；尚未声称浏览器或生产代理验收，S1.2继续。运行命令均为 S0 根 wrapper 加上述 `-Dtest`，集成时设置专用 `SMA_TEST_DB_URL`。

S1.2：根 reactor `ActorContextTests,ActorMethodSecurityTests,AccessAnnotationsTests,DataPermissionTests,UserTests,FileTests,NoticeTests,RolePermissionTests`，独立真实 MySQL，27/27通过（`backend/target/security-s1-2-authorization.log`）。覆盖后台/应用/机器身份隔离、数据范围、用户/角色/文件/公告拒绝路径及公告服务端XSS清洗；不是任意第三方业务接口的安全证明。

前端 `npm run verify`：32文件82项单元、类型/ESLint/双语/旧工程哈希/生产构建/发布预算通过，日志 `frontend-next/.e2e-runtime/security-s1-2-verify.log`。真实 Chromium `npx playwright test --config playwright.security.config.ts` 2/2，`--config playwright.security-production.config.ts` 1/1：脚本/媒体/危险链接被清除，正常富文本保留、无跟踪请求，生产登录品牌转义及 HTML 响应头成立。CSP为Report-Only，未声称已强制阻断或部署公网Nginx。

初次富文本单元测试在 happy-dom 下失败；独立复现并核对所安装 DOMPurify README 明确不支持该安全解析环境。完整恶意HTML断言迁至真实Chromium（不是将清洗函数mock为成功）；保留URL纯函数单元，补清洗器不可用时纯文本降级。首次类型检查还发现局部document变量遮蔽，已修正，完整verify通过。旧前端富文本依赖后端Jsoup，已记录审查边界，不将其直接v-html方式推广。

S1a 首轮：根 reactor `RequestExecutionTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests,SqlStructureSafetyTests`，30/30通过，日志 `backend/target/security-s1a.log`。7项真实MySQL用例证明本库提交/回滚、同键/异参、两服务实例并发在途回执、对象互斥、当前权限/租户、提交确认丢失、共享限流、容量与缺密钥拒绝。两实例是同一测试JVM内的独立服务对象和真实数据库连接，不等同负载均衡生产双节点。

S1a补充复验36/36通过（`backend/target/security-s1a-recheck.log`）；类型化配置及UNKNOWN状态最终复验38/38通过（`backend/target/security-s1a-final.log`，含11项真实MySQL治理用例及重新生成233个接口映射）；最后对实际application.yml环境变量占位绑定执行 `RequestConfigurationTests,RequestFingerprintKeysTests,RequestCommandTests,BrowserRequestFilterTests`，8/8通过（`backend/target/security-s1a-config.log`）。相同根reactor与隔离输出，数据库仍为专用库。新增表/键版本仅应用在隔离测试库，未部署用户服务。验证中观察到多个专项在同MySQL实例并行DDL导致耗时，无行锁等待；没有停止其他任务或调低数据库持久性配置。

HMAC轮换保留旧键、主体意图键唯一性、原键查回执、租户总预算、外层回滚不退还预算、容量和未决状态不自动重跑均有证据。通用账本不存正文/密码/令牌/完整业务响应；平台库本地事务能力不代表跨库原子性或真实LB双节点验收。开始B1，其他普通API仍按清单待接入。

## 接口接入验收与修复记录

B6完成（本地，2026-11-12）：余下根reactor verify为63/63通过、BUILD SUCCESS（`security-b6-current-remaining.log`，10分43秒）。与前轮已结束的148项去重合并，当前快照43类210项全部通过、无跳过；逐类XML汇总 `backend/target/security-b6-current-results.json`，不把中止的首轮称为整轮成功。配合之前完整根verify的840项/793通过/47条件跳过，B6所需标准验证、当前JSON/所有接入模块/域名/共享预算/应用操作差异已闭环。

快照外已核对并行差异：P09新5入口有36项HTTP回归和普通基线门禁成功；P08会话条件更新有60项行为/结构通过及独立普通基线复验成功（`p08-m1c.log`、`p08-m1c-policy-check.log`），其首轮唯一失败为当时新增5入口的基线差异。清单当前294条、22个精确例外。随后P08主键分配/列表时间排序及P09后续本人操作入口仍在实施，归其专项逐项验证及最终S6整合，不把这些进行中变化计为本快照通过。安全专项自己的JSON/指纹/治理代码未发生未验证变化；开始S1c/C1，密码策略保持现状。

B6整合执行方式修正：当前轮到OpenLoginBudgetTests容量夹具时，观察到约1万条预置数据因测试URL缺少rewriteBatchedStatements而逐条提交，运行数分钟仅约千条，可能超过夹具5分钟有效窗。核对JVM user.dir/test classpath确属本专项快照后仅停止该Surefire进程（41724），未停止运行服务/其他专项。原日志 `security-b6-current-integration.log` 整体为主动中止，不能写BUILD SUCCESS；此前已结束类的XML明细保存在 `security-b6-current-passed.json`。继续使用相同快照，给本次测试连接补 `rewriteBatchedStatements=true`，仅执行尚未完成的OpenAPI/规则/域名/租户及普通基线检查，日志 `security-b6-current-remaining.log`；保留重新清库/全部断言和生产数据库参数。

B6并行入口登记：P09新增5个精确POST邮箱入口，已读取控制器及应用安全链，确认仅注册/密码重置公开用途、WEB按用途HttpOnly proof与CSRF、Native JSON proof、原生挑战/预算/一次消费且无后台LOCAL套接。P09普通检查日志 `p09-c3d5a-policy-check.log` 实际BUILD SUCCESS，未开启更新标志；其生成清单已同步为294路由、22个精确原生/公开策略。该5入口HTTP专项仍在 `p09-c3d5a-integration.log` 执行，不能算入本专项289路由快照的HTTP通过数。

B6当前整合中间结果：延迟JSON实现的真实 `BrowserSecurityIntegrationTests` 5/5通过，包含配置绑定与缺失Content-Length的MVC包装异常仍返回413；`BrowserRequestFilterTests` 7/7、指纹2/2、治理配置2/2、Advisor3/3及Config HTTP4/4亦通过。证据均在 `security-b6-current-integration.log`，此时整轮尚在执行，不能用这些部分结果提前标记B6完成。

B6当前差异整合已启动：快照 `backend/target/security-b6-current-e95c542427b844c6abfadf5e1b84f266`（1270个输入文件哈希），根reactor `-pl backend -am verify`，日志 `backend/target/security-b6-current-integration.log`。包含延迟JSON读取/真实主体独立名额、289路由普通基线门禁、域名原生验证、共享Socket票据/OpenAPI登录预算、应用凭据操作及请求治理各模块回归；未设置baseline review标志。继续使用专用测试库、上下文缓存2及测试池minimumIdle=0。阶段尚未通过；快照后的并行应用Cookie抽取/会话变化需单独记录影响，不把旧快照当当前全部源码验收。

B6第二轮根完整verify通过（`security-b6-standard-v2.log`，55分59秒）：后端787项，0失败/0错误/46条件跳过；其他模块7＋8＋32＋6项，0失败/0错误，其中发布服务1项条件跳过。合计840项、793通过、47跳过，所有模块BUILD SUCCESS并完成打包。审计夹具与连接池问题已消除；未配置的真实Redis、发布MySQL/HTTPS及特定旧库升级不计通过。快照后仍有本专项延迟JSON读取/独立名额及并行域名、共享Socket票据/OpenAPI登录预算、应用凭据操作变化，已做哈希差异清单，必须补当前整合后才关闭B6。当前OpenLoginThrottle已由并行工作变为共享MySQL预占（来源60/5min＋来源/AppId10/5min），接口基线描述同步审查，不沿用旧节点失败计数说明。

B6当前接口差异：并行域名生命周期增加8个映射。普通基线检查确实拒绝未审查增加（`security-b6-new-routes-gate.log`），并未因为继承类级LOCAL而静默接受。逐项读取后确认4个GET、挑战/启停/主域名的LOCAL，以及DNS/TLS检查的NATIVE独立短事务/原生回执；保留PlatformOperators和各操作权限。显式审查更新基线通过（`security-b6-domain-policy-review.log`），清单为289路由、17个精确原生/公开例外；普通门禁与DomainLifecycleTests/DomainLifecycleHttpTests将纳入最后当前源码补验。

B6入口名额进一步收紧（第二轮快照之后的待集成差异）：JSON改为下游读取时才有界加载，认证拒绝不提前消耗正文/名额；根据读取时真实主体区分人类16与匿名4个名额，伪造Bearer头不授予人类名额。413/503在Jackson包装异常后仍保留，原始字节仅保存在请求内，结束清零。`BrowserRequestFilterTests,SecurityEndpointInventoryTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests` 30/30通过（`security-b6-json-admission.log`）；需在第二轮完整验证结束后补当前HTTP整合，不能将快照旧实现的测试计为该差异验收。

B6第二轮根完整verify已启动：快照 `backend/target/security-standard-v2-a3ddd470e3e240a5a0498b1bf97af401`（1241个输入文件哈希，含最新并行源码、审计夹具修正、JSON入口边界及281路由审查基线），日志 `backend/target/security-b6-standard-v2.log`。命令为根wrapper verify，加原隔离输出/接口清单参数及 `-Dspring.test.context.cache.maxSize=2`；进程环境 `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0`，不改生产池或MySQL全局参数。基线首次审查生成通过（`security-b6-policy-baseline.log`）；第二轮普通构建不带review标志，须真实匹配已审查基线。S1c仍未开始实现。

B6首轮根完整verify结束（`security-b6-standard.log`，37分27秒）：扩展各模块本地用例通过，可选MySQL/HTTPS发布联调跳过；后端744项中1项断言失败（旧OperationLogTests缺操作键，已修正）、47项因Too many connections而错误、46项按环境条件跳过。错误发生在测试上下文累计占用连接后，不能视为这些业务用例已执行通过。后续只收紧本次测试的上下文缓存及空闲池，不改运行服务/数据库全局配置。入口审查另补普通JSON在MVC之前的2 MiB实际字节/16并发上限，保留原字节及multipart、机器/应用协议。快速复验31/31通过（`security-b6-body-bounds-recheck.log`）；初次快速编译碰到并行DomainProbeTests尚未改完的var复合声明，复读时已由并行工作修正，未放宽门禁。当前源码补验与完整重跑仍未完成。

B6快照差异跟踪：完整验证期间并行P08新增CacheResourceAdapter/Registry及模块资源目录（请求账本/密钥环为PROTECTED，文件为不可清理RESOURCE），P09新增IdentityPasswordLogin服务及其测试；尚未新增对应HTTP映射。结束后需在当前工作区补跑结构/接口/i18n、CacheResourceRegistryTests/CacheResourceDirectoryTests/CacheBoundaryTests、IdentityPasswordWorkTests/IdentityPasswordLoginTests及审计夹具复验，并重新核对源码变化；不能把旧快照结果声明为全部最新源码通过。

B6完整验证进行中：根快照verify的接口门禁再次通过并生成281行清单，已同步 `docs/security-endpoint-inventory.md`。目前发现OperationLogTests旧成功写请求没有Idempotency-Key，收到预期的新400门禁而未到达原业务断言；已在工作区补测试密钥/显式操作键与100ms测试冷却间隔，保留成功/重复业务代码及正文不入审计的原断言。整套测试继续收集其余结果，完成后将定向复验夹具修正，尚未标B6通过。

B6进行中：编译映射覆盖门禁已检查281个处理方法/映射、17个精确原生/公开策略；新入口缺治理、方法覆盖无类标记、无限HTTP方法、原生原因缺失及失效例外均失败。补audit日志、workspace仪表盘、CacheConfirmation读预算，common回执保留自身合计120/min并加入节点/租户门禁。根reactor `SecurityEndpointInventoryTests,ModularityTests,ArchitectureRulesTests` 20/20通过（`backend/target/security-b6-inventory.log`）。为隔离各extension模块的默认target及测试文件目录，根完整verify在 `backend/target/security-standard-a13ac92ba6cb493bb14eac71de74a89a` 源码快照执行，含1532文件SHA256清单；专用库仍为sma_test_security_defense。日志 `backend/target/security-b6-standard.log`，尚未标完成。未设置可选SMA_RELEASE_IT_DB_URL，MySQL/HTTPS发布服务专项不在本轮真实外部验收范围。

B5c完成（本地）：根reactor `RequestInputFingerprintTests,RequestGovernanceAdvisorTests,RequestGovernanceHttpTests,IamRequestGovernanceHttpTests,MessageRequestGovernanceHttpTests,TenantRequestGovernanceHttpTests,IdentityRequestGovernanceHttpTests,MailRequestGovernanceHttpTests,MailProfileTests,MailTemplateTests,MailOutboxTests,IdentityConfigurationTests,IdentityEmailFoundationTests,IdentityApplicationHttpTests,ApplicationRequestFilterTests,BrandHttpTests,BrandDraftTests,BrandPublicationTests,TenantDirectoryTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests,SqlStructureSafetyTests` 107/107通过，BUILD SUCCESS，15分24秒（`backend/target/security-b5c-integration.log`）。证明仅持字符串权限无法成为平台运维、目录写重复不重复改变状态；品牌草稿本地回执与原生发布同operationId保持单修订，预览不保存草稿；Mail模板发布/预览与原生取消、异租户拒绝、队列状态回归；Google/mail WRITE_ONLY字段变化不再遗漏指纹，同时响应不暴露凭据。应用Cookie/CSRF/来源及realm安全版本、Config/IAM/Message既有HTTP回归通过，未真实发信。仅追加file回执Lookup.toString脱敏的非行为修正将在B6根验证一并编译。开始B6。

B5c进行中：租户/域名/品牌草稿、Mail配置/模板及Identity后台配置接入LOCAL；品牌发布/回退、Mail任务取消保留NATIVE，预览为QUERY。平台目录执行/重放回调复用PlatformOperators；不将/app应用身份或机器入口套后台人类协议。审查发现Jackson响应序列化会略去WRITE_ONLY字段，已增加只供指纹的独立序列化器，将嵌套密码/Google secret纳入摘要后再由既有HMAC保存，普通响应映射器不变且临时字节清零。普通请求摘要兼容及代理/结构快速验证24/24通过（`backend/target/security-b5c-fingerprint.log`）。当前代码中WRITE_ONLY仅出现在本批新接入的MailProfile和IdentityClient输入。正在根reactor `security-b5c-integration.log` 验证真实HTTP同键换秘密冲突、平台身份、品牌原生回执、Mail取消/预览、应用CSRF及原批次回归；未发送真实邮件。

B5b完成（本地）：修正故障注入后，根reactor `FileUploadRequestTests,FileUploadHttpTests,FileRollbackCleanupTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests,SqlStructureSafetyTests` 34/34通过，BUILD SUCCESS，7分18秒（`backend/target/security-b5b-files-final.log`）。真实事务afterCommit故障证明领取已提交后不重传、文件已提交但调用者异常可查回成功且二进制保留；结果更新失败和文件先删除后对账仍正确，未知任务拒绝重跑。包括并发同键仅一次写、异参冲突、独立文件、归属/租户/portal/撤权/安全代次、图片拒绝及真实HTTP回归。结合首轮其余通过证据，原File/Profile/PublicImage/BrandPublication/BrandEntry回归通过。未部署运行服务、未声称磁盘掉电或真实多节点验收。README、后端规则和运行协议已同步，开始B5c。

B5b验证中：file追加V2上传任务，不改V1；上传用NATIVE持久接受/唯一领取、原键或任务ID查询，只在ACCEPTED明确允许原键恢复，RUNNING/UNKNOWN不自动释放。FileService回滚清理改为仅STATUS_ROLLED_BACK删除二进制，UNKNOWN保留。测试存储目录改用隔离verify输出目录，避免并行专项共享blob计数。根reactor结构/SQL门禁21/21通过（`security-b5b-structure.log`），独立清理故障测试1/1通过（`security-b5b-cleanup.log`）。首轮数据库整合 `FileUploadRequestTests,FileUploadHttpTests,FileRollbackCleanupTests,FileTests,ProfileTests,PublicImageEncoderTests,BrandPublicationTests,BrandEntryHttpTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests,SqlStructureSafetyTests` 共45项，44通过、1失败（`security-b5b-files.log`）；失败是测试在start事务内部抛异常实际触发回滚，不能模拟提交后确认丢失。已改为真实事务afterCommit故障，并补文件提交后异常及删除先于对账用例；正在 `security-b5b-files-final.log` 复验，尚未标完成。

B5a完成：会话夹具修正后 `AuthRequestGovernanceHttpTests,RequestGovernanceAdvisorTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests`，26/26通过（`security-b5a-session-final.log`）。进一步分离同一RPC入口的读/写预算，避免上传挤占查询；每次成功预算预占仅清理最多2条当前租户、已过期超过5分钟的频率计数，使用索引和SKIP LOCKED，不触碰持久回执/互斥。最终根reactor `RequestExecutionTests,RequestGovernanceAdvisorTests,ConsoleRequestGovernanceHttpTests,AuthRequestGovernanceHttpTests,SqlStructureSafetyTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests`，42/42通过（`backend/target/security-b5a-budget-final.log`，8分42秒）。包含真实Bearer双会话互不误拦/伪造会话头无效、RPC查询/独立分片/重复分片和伪造只读拒绝、频率记录清理上限/租户隔离/有效预算不退款。结合前轮其余81项通过证据，B5a本地完成。代码审查明确插件invoke只能依赖适配器副作用契约，保留NATIVE分类，未声称所有插件能力都只读。开始B5b。

B5a整合 `security-b5a-auth-extension-recheck.log` 共82项，81通过、1错误；唯一错误为新增AuthRequestGovernanceHttpTests用MockMvc匿名处理器提前植入了非正式过滤链的匿名主体，SessionService解析时被严格主体校验拒绝。改用空SecurityContext，让真实Bearer过滤器建立身份，未放宽生产认证。TOTP 11项、安全密码7项、原登录4项、会话管理、登录密码策略/历史写入、插件账本/机器应用、控制台代理及分片作用域/HTTP、机器签名和真实Socket全部通过。正在 `security-b5a-session-final.log` 复验修正用例与相关门禁，B5a仍未标完成。

B5a进行中：Auth及SocketTicket保留原生协议，按可信绑定会话区分冷却；插件invoke及工作/对账保留NATIVE；stateful标记要求业务键，能力副作用仍由稳定适配器约束，不推导为纯查询。控制台代理用同一服务端路由白名单区分查询和写入，分片目标独立且数字索引规范化，原始字节不被治理层读取。首次整合启动命令误写forceConnectionTimeZoneSession，发现后在测试库准备阶段仅终止已核对的本轮Maven/Surefire进程树；日志 `security-b5a-auth-extension.log` 不计为通过。已改回forceConnectionTimeZoneToSession，正在 `security-b5a-auth-extension-recheck.log` 复验，未改运行服务和数据库全局配置。

B4完成：根reactor `RequestAdmissionTests,RequestExecutionTests,RequestGovernanceAdvisorTests,RequestConfigurationTests,RequestGovernanceHttpTests,IamRequestGovernanceHttpTests,MessageRequestGovernanceHttpTests,RulePlatformTests,OpenApiRequestGovernanceHttpTests,ApplicationTests,MonitorTests,CacheGovernanceTests,CacheTaskHttpTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests`，86/86通过（`backend/target/security-b4-integration.log`，15分47秒）。涵盖本节点并发拒绝及释放、原生嵌套本地协议单名额、原三批HTTP回归、密钥只首响应返回且仅轮换一次、真实规则网络创建/发布/执行/审计、模板原生发布回执及独立失败报告、缓存版本/权限及原生任务。规则网络夹具改为显式5199测试租户和127.0.0.3回环绑定，未放松生产Host规则。额外根reactor `PublicationRequestGovernanceTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests`，22/22通过（`backend/target/security-b4-publication.log`，9分58秒）：真实持久事件先注入失败再重投完成，外租户拒绝，UUID大小写不能绕过冷却。该日志中的两条异步异常是故意失败夹具，不是测试错误。运行时清理/框架重投保持原生结果核对及至少一次语义，不声称具备通用exactly-once。开始B5。

B4进行中：补LOCAL/QUERY/NATIVE显式模式和RequestAdmission，共享节点并发预算并支持同步原生协议套接；规则/监控/开放应用控制器已分类接入，新增缓存任务接口按自己的任务协议接入。首次快速验证testCompile失败，原因是既有测试work助手只允许String，嵌套协议用例需返回Outcome；改为泛型助手后根reactor `RequestAdmissionTests,RequestGovernanceAdvisorTests,RequestConfigurationTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests`，28/28通过（`backend/target/security-b4-admission-recheck.log`）。整合测试和真实事件重投验证仍在进行，未标B4完成。

B3：消息/随手栏控制器接入；便签原requestKey/version及消息提交后推送不变。首轮环境问题消除后，根reactor `MessageRequestGovernanceHttpTests,NoticeTests,PrivateMessageTests,TodoTests,ShelfTests,SocketTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests` 44/44通过（`backend/target/security-b3-message-recheck.log`，15分12秒），含真实WebSocket回归。审查补充个人对象互斥按用户＋规范化路径对象隔离，公告管理继续共享锁，个人已读用方法级策略覆盖；真实Spring代理及配置快速验证4/4通过（`security-b3-owner-scope.log`）。最终根reactor `RequestGovernanceHttpTests,IamRequestGovernanceHttpTests,MessageRequestGovernanceHttpTests,RequestExecutionTests,RequestGovernanceAdvisorTests,RequestConfigurationTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests`，48/48通过（`backend/target/security-b3-final.log`，7分1秒）。证明同键私信/公告仅一条记录/发布事件、混合归属批量回滚及FAILED_FINAL、便签版本与所有者保护、个人回执不含正文。B3本地完成，SocketTicket原生一次性协议的入口治理仍归B5。开始B4。

B3首轮 `security-b3-message.log`：44项中1项双语失败、23项因应用上下文未启动而错误；不是消息业务断言结果。测试复制资源/编译时并行identity登录开发尚未补齐三个错误文案及IdentityLoginProperties注册。复读当前源码：双语文案已齐，IdentityLoginStore已注册该配置Bean。独立输出根reactor `I18nBundlesTests`，3/3通过（`backend/target/security-b3-i18n.log`）；正在基于完整当前源码重验B3，不移除身份模块、不豁免语言门禁。

B2：IAM的8个控制器接入GovernedApi，角色编辑与授权共享iam.roles互斥组，个人资料使用本人资源；原服务授权、结构锁和密码规则不变。根reactor `IamRequestGovernanceHttpTests,DepartmentTests,PostTests,RoleTests,RolePermissionTests,NavigationTests,PermissionTests,UserTests,ProfileTests,LoginPasswordPolicyTests,LoginPasswordWriteTests,SecurityPasswordTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests`，55/55通过（`backend/target/security-b2-iam.log`，9分47秒）。4项新HTTP用例验证角色缺键/回执/不同对象独立/撤权、跨租户及越权授权、重放只提升一次凭据版本/数据范围拒绝/原密码格式拒绝、自身资料回执不含隐私且跨用户不可读。原部门HTTP成功用例补显式操作键及测试密钥，不豁免生产协议。B2本地完成，开始B3。

B1最终修复验收：根reactor `RequestGovernanceHttpTests,RequestGovernanceAdvisorTests,RequestConfigurationTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests`，28/28通过、BUILD SUCCESS（`backend/target/security-b1-binding-http.log`，17分27秒，专用库和原隔离输出）。4项真实HTTP用例包含缺键、同键/异参、删除后回执、跨用户/租户、撤权、快速换键429及FAILED_FINAL回执；配合S1a的在途/并发证据，B1标记本地完成。最终修复后的配置类也通过真实完整应用启动，不以单独Binder测试替代。开始B2，密码服务不变，仅接入IAM控制器及专项拒绝回归。

B1冷却参数最终复验 `security-b1-final.log` 未通过：27项中5项错误（配置绑定1项、HTTP上下文4项），根因是配置record新增兼容重载后缺少明确的构造器绑定，非业务断言失败。当前源码已为规范构造器声明ConstructorBinding；新增真实ApplicationContextRunner绑定回归，并用TestConfiguration隔离测试配置。独立输出 `target/security-binding/target` 执行 `RequestConfigurationTests,RequestGovernanceAdvisorTests`，3/3通过（`backend/target/security-b1-binding.log`）。正在原专用库复验HTTP及架构，不以快速绑定成功代替HTTP验收。

B1首轮真实HTTP验收失败（`backend/target/security-b1.log`）：缺键请求仍执行业务，实际根因为方法安全使用的基础设施自动代理器未选中普通角色的治理Advisor；已将Advisor明确注册为ROLE_INFRASTRUCTURE。补 `RequestGovernanceAdvisorTests` 用真实Spring方法安全代理证明缺键在业务执行前被拒绝，重验1/1通过（`backend/target/security-b1-advisor-recheck.log`）。HTTP测试还修正请求完成后安全上下文被清除的问题，使用现有Users.request真实测试域名夹具并恢复调用者上下文；没有放松生产租户/权限校验。B1保持进行中，等待修复后的真实HTTP和删除回执回归。

两套前端API协议单测通过（新版7项、旧版6项）；新版完整verify通过，旧版标准verify通过（48项单元/构建/ESLint/双语），日志分别 `.e2e-runtime/security-b1-client-verify.log` 和 `backend/target/security-b1-legacy-verify.log`。隔离浏览器新版3项、旧版1项通过；最初测试路由glob误拦截了/src/api源码模块，改为只匹配URL pathname以/api/开头，修复后真实Axios发出的操作头已核对。旧版仅更新已审阅HTTP客户端基线哈希。开发密钥助手已在backend/target独立夹具验证生成后再次读取一致、32字节、未启动服务。

实际公网代理/WAF、生产通道送达、真实收件人确认、生产负载、多节点网络及恢复演练均需独立证据；未配置项不伪记通过。本文件的“待实施”仍是未完成工作。
