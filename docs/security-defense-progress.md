# 安全防御专项实施进度

S1c前端治理（C1～C4）与S1d真实请求治理已完成本地验收。S1d覆盖单宿主4场景、双JVM前后6场景，以及当前制品的操作者/撤权/上传原生协议/跨租户配置组合11场景；最后39项根reactor、6隔离原语、新版315项完整检查、旧版55项与独立浏览器4项通过。首轮编译窗口失败、历史503压力响应及各制品指纹边界见实际记录；安全前端早期关页锁时序和ResizeObserver告警见[前端进度](security-frontend-progress.md)。临时资源清理均已核验。下一S2事件采集；不替代跨设备/生产multi/LB、P09外部环境或X5验收。[入口清单](security-frontend-write-inventory.md)记录原生回执缺口。

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
| S1c | S1b | 前端统一输入防抖、提交门禁、慢响应与 UNKNOWN 恢复；按[前端拆分](security-frontend-progress.md)C1～C4逐项验证 | 完成（本地；C1～C4、独立旧版兼容，真实后端见S1d） |
| S1d | S1c | 慢网连点20次、超时断连、跨标签/双节点、无误合并正常操作 | 完成（本地；d1/d2/d3及清理通过，生产multi/LB和外部服务除外） |
| S2 | S1d | 受信来源、事件采集/脱敏/平台租户隔离、容量/故障与查询 | 实施中（[拆分计划](security-event-progress.md)，a/b/c/d1/d2a/d2b本地通过；元数据/旧包兼容/真实库分轮、最终11项聚焦通过；下一d3维护/完整闭环） |
| S3 | S2 | 诱捕、关联、站内＋Webhook持久投递、去重/未知/失败恢复；本地完整协议演练 | 待实施 |
| S4 | S3与租户平台品牌阶段 | 接入单一品牌实现，资产/URL/权限/版本回退/公开投影安全验收 | X4公开资产子项已前置本地验收；S4整体仍按后续阶段收尾 |
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


2026-11-12按开发调度继续：S1c已完成C3b与C3c工作台/品牌域名/缓存/规则的本地子项，详细证据见前端拆分；C3c插件验证中，上传/C3d/C4未完成。同步development-schedule状态。后续测试改为当前提交点＋记录差异直接执行，旧快照仅历史证据；不复用单线库进行X5。X3/X4仍待验收，本轮品牌交互测试不能替代X4公开资产安全矩阵。

P11 X3预算登记已本地验收（2026-11-12）：接口清单增14项受审预算目录与351条端点scope/模块/存储/N换算；BrandWorkBudget按实际node/4登记，持久预算明确自身事务语义。p11-x3-review.log 6项与关闭审查开关后的p11-x3-ordinary.log 25项通过，清单生成结果一致。详细命令与并行字典6条端点的策略审查边界见development-progress X3；本次不改限流实现，不替代安全S1c/S2或X5验收，开发调度已双写。

P11 X4实施计划（2026-11-12）：X3已完成，X4为独立可推进项，不依赖尚缺运行时的P09容器验收。先X4a审查并固定PublicImageEncoder的JDK PNG/JPEG/GIF读写器，避免ImageIO全局SPI扩展匿名素材解析范围；有界静态PNG输出，保留元数据/尾随字节剥离与GIF首帧语义，不宣称完整杀毒。验证真实SPI注入不调用、三类格式/伪装/尺寸/体积/输出预算及全部品牌URL字段。X4a通过后X4b使用独立可清空MySQL库和输出验证匿名GET/HEAD、公开字段精确集合、草稿/原件/管理入口不可公开、跨租户/portal/Host注入及停用/未知域名不回落，再回归发布/回退/权限/旧文件。路径限file既有公开图片编码器、tenant/file专项测试与进度/规则/手册，不创建品牌平行实现，不进入其他线程在建config或缓存代码。每子项验证后双写调度和本进度，X4整体须真实HTTP矩阵通过才完成。

X4a本地完成：根reactor使用Java21及`-Dsma.verify.directory=target/p11-x4/target`运行PublicImageEncoderTests、BrandValidationTests、LegacyBrandConverterTests、BrandWorkBudgetTests、BrandResolverTests、ArchitectureRulesTests、ModularityTests、SecurityEndpointInventoryTests、EndpointBudgetCatalogTests，`backend/target/p11-x4a.log`41项全部通过，无跳过，BUILD SUCCESS（2分04秒）。PublicImageEncoder只按魔数选择java.desktop模块内的固定JDK PNG/JPEG/GIF SPI，拒绝在白名单前探测或实例化其他插件；输入使用内存流及NOFOLLOW，PNG写出器固定，可寻址缓存和最终输出均≤2MiB。真实注册优先恶意SPI证明读/写回调零调用；三类源转PNG、伪格式/尺寸/文件上限、JPEG压缩源展开PNG超限与名额恢复、PNG元数据和尾随字节剥离通过。全部8个公开URL字段覆盖危险协议/凭据/控制符/反斜线/端口与合法地址。此边界只针对公开品牌快照编码器，不宣称旧FileService上传识别已统一P09扫描，也不代表杀毒/OS隔离。X4b开始，整体未完成。

X4b首轮`p11-x4b.log`仅测试编译失败，新增测试helper `content`遮蔽了MockMvc静态content断言，尚未执行数据库用例；已重命名为brandContent，并去掉URL测试一条重复合法断言，不改生产行为。独立新库`sma_test_p11_x4_20261112`已创建，仅授予既有本机测试账号该库权限，不使用任何单线正在验证的库或X5合流库。复跑日志`p11-x4b-recheck.log`，失败不标完成。

X4b首次执行结果：`p11-x4b-recheck.log`64项中63通过，唯一失败是MockMvc对HEAD返回的测试响应仍包含正文。当前无法用这个对象断言实际网络HEAD抑制；新增BrandPublicSecurityTests的回环随机端口Tomcat，在同用例用真实Socket发送HEAD并核验200/PNG/no-store/nosniff/Content-Length及实际零正文，保留MockMvc权限与原件/管理路径断言。未修改生产控制器或弱化HEAD语义。下一轮只复验BrandPublicSecurityTests、BrandEntryHttpTests、BrandHttpTests，原63项通过证据保留，X4仍进行中。

X4b与X4整体本地完成（2026-11-12）：根reactor `-pl backend -am -Dsma.verify.directory=target/p11-x4/target -Dsma.openapi.key-file=target/p11-x4/test-keys/openapi.key -Dtest=BrandPublicSecurityTests,BrandEntryHttpTests,BrandHttpTests -Dsurefire.failIfNoSpecifiedTests=false test`，环境SMA_TEST_DB_URL指向本专项`sma_test_p11_x4_20261112`，`p11-x4b-wire.log`10项全通过无跳过，BUILD SUCCESS（8分01秒）。新增5项矩阵实际覆盖公开字段精确集合及不泄漏内部ID/路径/草稿/操作者、只GET/HEAD可公开而原件/管理/非安全方法仍401、跨租户/portal/Host参数与未受信Forwarded注入无效、缺槽位/未知/停用404、查看权限只扩展到已引用素材、superAdmin不绕过租户/portal、文档/SVG源ID拒绝且不改草稿/发布。Socket在127.0.0.1随机端口60705的真实Tomcat核对HEAD正文为空且Content-Length与GET快照一致，证明确为MockMvc传输边界差异；未放宽生产接口。

证据组合为X4a41项＋首轮64项中63项通过＋最终10项HTTP通过，不声称首轮全绿或相加成互不重复用例数。原发布/回退/并发/预览、旧文件/头像和普通架构门禁已在首轮通过，最终只修改测试传输方式。复核60705已无监听、测试JVM45500已退出；专用测试库保留用于审阅，未触碰用户服务。X4不证明通用恶意软件扫描、P09安全文件READY、容器禁网、真实生产代理/WAF或多实例；X5仍待实施。已双写开发调度、开发进度，并同步README、品牌契约、运行说明及tenant规则；同批纠正tenant规则残留“共享预算全独立提交”的旧描述，对齐X3既有口径。


S1c插件恢复前置：发布上传状态只读接口与精确代理白名单、根验证输出隔离profile共45项根reactor通过（security-c3c-extension-isolated.log，3分25秒）。不增加业务写入口或密码变更，不扩大代理匹配；同步调度与前端专项。前端恢复仍实施中。

S1c/C3c3c插件恢复本地完成：原command到上传COMPLETE全程保留登记，刷新只查原任务，续传重验原文件及当前会话，拒绝未知/不可恢复任务重传。后端前置45项；前端定向33项、浏览器8项、完整verify 47文件171项通过，记录见security-frontend-progress及`.e2e-runtime/security-extension-upload-verify-final.log`。首轮并行配置页i18n失败已在其修正后完整复跑，不隐藏失败、不修改密码策略。继续C3c4通用上传；C3d/C4、S1d及监控诱捕、X4/X5尚未完成。

S1c/C3c4通用上传本地完成：useFileUpload持有原File与原键，ACCEPTED/resumable显式续传前再次查原任务；RUNNING/UNKNOWN不重传。文件库、头像当前选择、品牌槽位/草稿版本、公告表单实例分开绑定，过期响应不附到其他上下文。原File只在内存；刷新/锁屏后仅查询，失去原输入不重建multipart。复用B5已有/file/uploads协议，不改P09新upload-tasks流水线。最终verify 48文件178项通过；submission 20个场景与品牌管理5个场景分轮通过，失败及复验命令见前端专项。同步调度、README、前端规则和客户端契约；C3c关闭，下一项C3d，尚未声明S1c或整体完成。

C3d1认证竞态本地完成（2026-11-12）：同步登录/OTP门禁、仅内存的请求发送前身份绑定、旧退出和权限读取响应隔离、未知OTP清挑战、页面离开清密及迟到导航拒绝。定向22项、浏览器authentication/protocol-compat共5项和完整新版verify 48文件191项通过；首次旧端规则文档哈希失败在单文件审阅同步后复跑通过。详细命令、MFA交接与模拟API边界见前端专项。未改后端认证/密码规则或真实服务；C3d2/d3及C4仍待实施，不据此关闭S1c。

C3d2a账号安全本地完成（2026-11-12）：共享凭据门禁、发送前仅元数据标记、刷新和只读查询不清UNKNOWN、身份/锁屏及本地QR迟到隔离。33项专项、7项模拟API浏览器与完整verify198项全部通过，详见前端专项日志与失败边界；未改后端认证或密码规则。C3d2b本人改密/锁屏/退出前确认继续待验收，C3d整体/C4/S1d尚未完成；调度双写并同步规则/客户端契约。

C3d3a会话撤销已本地完成：按目标NATIVE意图与原身份发送，未知结果保留且禁止重发，列表缺失不能替代原操作回执；身份/权限/锁屏变化清列表并隔离迟到响应。定向34项、完整verify221项、会话浏览器2项及原锁屏/改密/撤销工作流1项通过。日志security-c3d3a-{unit,browser,workflow,verify}.log均在frontend-next/.e2e-runtime；不代表真实后端撤销或跨标签验收。下一d3b入口覆盖审查，与调度同步。
C3d3b1监控遗漏已补齐并本地验收：SQL重置/事件重投NATIVE原意图、未知跨刷新保持、确认前身份保护和私有列表清理。4项新浏览器＋1项原operations场景、完整verify221项通过，证据见前端进度security-c3d3b1日志。事件重投直接成功不宣称监听器完成；节点统计重置未新增多节点路由。继续d3b2消息票据/动态适配器及覆盖清单，C4/S1d不提前关闭。
C3d3b2消息会话隔离本地完成：summary/socket-ticket绑定原会话，同一用户换token、锁屏/权限变化使旧票据/推送失效；SESSION_INVALID迟到验证不停止新连接。33项定向、完整verify226项和2项原工作流通过，日志security-c3d3b2-{unit,browser,verify}.log。下一d3b3动态适配器/入口清单，C4/S1d仍待验收。
C3d3b3发送身份边界已本地验收：createApi默认固定调用时身份，rule精确JSON和cache submit适配到同一发送前核对，防止排队请求借用新登录token。33项定向、完整verify236项、认证5/缓存3/规则11浏览器分轮通过；原键/精确数值/节点头保持。证据security-c3d3b3各日志见前端进度，继续d3b4清单收尾。

C3d3b4完成：生产写入口清单和QUERY/原生协议例外已落文档，333源码指纹、137本地链接与定向diff检查通过；新增清单与README/规则/调度同步。C3整体只标本地完成，安全S1c仍等C4；S1d、后续安全阶段及X5不提前关闭。
C4a1浏览器资源锁原语完成本地验收：5项单元、2项真实双页面、类型与定向lint通过，日志security-c4a1见前端进度。关闭标签释放浏览器锁不等于历史命令完成；注册表生产接入、凭据协调与统一面板仍待后续，安全S1c不关闭。

C4a2本地完成：普通操作注册表已接资源原子锁、短存储准入与容量保护、storage刷新、原键核对/续传协调；未知关页不重发，不支持浏览器明确拒发。完整verify250项；coordination5/submission20分轮、工作台4/规则7/缓存2/插件2浏览器通过。初轮上传时序断言和新增peer恢复入口缺bind均保留失败记录并修复复验，详见前端进度。下一C4a3独立凭据/认证协调。

C4a3a本人凭据协调本地完成：共享摘要scope/原键UNKNOWN、Web Locks提交/回执核对、旧标记迁移与冲突保护、容量/能力/存储失败关闭；独立紧急锁屏保持。完整verify261项，账号安全/双页7场景、最终双页3项复验和原工作流2项通过，失败修复记录与日志见前端进度。下一a3b会话锁、a3c认证协调，不据此宣布全认证跨标签完成。

C4a3b锁屏协调本地完成：共享UNKNOWN/LOCKED/UNLOCKED元数据、同scope串行门禁、跨标签遮挡及确认解锁后当前授权读回；旧读回/缺字段/身份不符不能取消新锁。完整verify278项、账号安全/双页8场景及最终双页4项复验、原工作流2项和便签退出1项通过，日志与范围见前端进度。无真实后端锁/MFA集群证据，下一a3c认证协调。
C4a3c1令牌存储归属本地完成：私有会话退出/401不误删其他共享会话，明确退出后本标签不回落共享账号，旧预期token不能清新有效token。33定向、完整verify283项及存储双页/原认证6浏览器通过，证据security-c4a3c1日志见前端进度。下一c2认证准入/身份变更，再c3退出协调，S1c/C4尚未完成。
C4a3c2认证准入已本地完成：登录/MFA浏览器原子门禁、共享身份失效、按认证代次取消和原token保护；等待OTP不长占锁，不共享凭据/挑战。定向42项、最终完整verify292项、认证9/边界15浏览器分轮通过。首轮销毁时访问已销毁Pinia导致35项失败已修复并全量复验，详细日志见前端进度。下一c3原会话退出跨标签去重。
C4a3c3及C4a本地完成：原会话确认前浏览器锁、确认后LOCAL_EXIT摘要标记及单次注销发送；匹配标签停止使用旧会话，未知/刷新不重放，能力/存储故障仍保留确认和本地退出。标记不是后端注销回执。最终完整verify305项、退出3/原认证4/便签退出1浏览器分轮通过；初轮菜单已因自动退出消失的测试时序错误已修正，详细证据见前端进度。下一C4b操作面板，再C4c兼容，S1d仍未开工。

S1c最终本地证据：新版315项完整verify、C4b安全64场景分轮及原流程20/随手栏4、C4c旧版标准55项及独立浏览器4通过。未改旧版生产源码/基线哈希、未部署用户服务；C4b首轮时序及布局告警记录保留，S1d真实后端和X5仍待完成。

S1d实施拆分：d1先完善真实runner安全专项隔离（独立临时库/账号、显式空闲端口、制品指纹及终止/清理证据），根reactor当前制品门禁通过后验证真实单宿主的20次提交/慢响应、已提交丢响应、刷新原键核对及正常不同对象不误合并；d2再在独立双JVM上验证共享持久回执与跨标签/独立浏览器同键；d3完成身份/租户/撤权和原生协议代表场景及记录汇总。网络故障区分发送前阻断、服务端受理后丢响应，不用API夹具或延迟响应冒充服务器事务并发。只使用专项生成的临时数据库和测试账号，不碰开发库或其他线库/端口。每项验证后双写，不提前进入S2。

S1d1进行中：根reactor当前制品verify26项0失败/跳过（security-s1d-artifact.log）通过，独立输出backend/target/security-s1d/target；新runner隔离/路径/真实退出确认3项node测试及脚本/测试lint/type通过。SMA_E2E_SUITE=security使用独立临时库/账号与明确18261/5206端口，复制制品保存SHA256；只有确认自身子进程结束后才能清库，清理后查询核验库/账号确实不存在并删除本轮生成钥文件。真实单宿主测试已启动，runtime为frontend-next/.e2e-runtime/security-1794461309775，仍在迁移/就绪阶段，不能标记验收通过。最终测试包含延迟响应20触发、真实提交后丢响应、跨标签不同对象和15秒客户端超时/404后原请求迟到；这些是有明确边界的传输故障注入，不冒充服务端持锁并发。

S1d1真实单宿主4/4通过（security-s1d-real.log，runtime security-1794461309775）：真实UI20次交替按钮/表单触发仅一HTTP写及一条真实业务结果；服务端已提交而浏览器响应丢失后跨刷新按原键恢复，显式原键重放被common.request.completed拒绝且业务仍一条；两个真实登录标签的不同对象均提交且目标不混淆；实际15秒客户端超时后原键查询404仍保持未知与禁重发，传输故障注入随后放行原请求，真实提交后原键核对成功。所有业务响应来自真实Java/MySQL，不伪造业务结果；响应延迟与发送前阻断有明确边界，不宣称服务端事务长时间并行。

本轮制品SHA256为41abfc9670fd2fb44b927cd64f82fcb68ad4962dbec2269435da7420f6316d41；result.json的passed/cleanupComplete=true、credentialsPersisted=false。脚本核验自身宿主退出后清理专项库/用户，再查information_schema/mysql.user确认不存在；独立复查18261/5206无监听、auth-security.keys/openapi.key不存在。未重启或清理用户服务/其他专项库。当前新版完整前端门禁正在收尾；S1d1未在门禁完成前标结束，d2双JVM/d3范围与原生协议仍待完成。

S1d1已完成：security-s1d-frontend-verify.log最终完整verify61文件315项、应用/测试类型、eslint/i18n、baseline128/route30、构建及发布门禁通过（160KiB gzip/38 lazy modules/296 files）。与26项根reactor、3项隔离原语、真实Java/MySQL四场景及清理manifest构成完整本子项证据。README/开发手册/前端规则/调度同步，相关172个本地文档链接无缺失。下一S1d2双JVM；S1d整体/S2与P09/X5仍未完成。

S1d2开始：在已验证真实runner上增加仅security scope可用的显式第二宿主端口，两JVM使用同一复制制品/共享HMAC与认证钥、同一专项库；先单实例迁移/初始化，再以schema-mode=validate启动两服务。测试通过两个实际地址验证跨标签原键核对、独立浏览器并发同键仅一业务结果、同键异参拒绝；随后确认两JVM退出并重启，新的浏览器上下文仍通过权威接口核对原回执/拒绝重放。前后阶段只交换原键/业务ID/回执ID等测试元数据，不保存token或正文。保持single部署模式，此项不宣称P08生产multi/LB或多主机部署通过。独立第二端口必须空闲且与前端/主宿主不同；全部子进程退出确认后才清库。按源码当前状态重跑根reactor制品门禁与新增隔离单元/真实矩阵，逐项双写。

S1d2首轮before-restart 1/2：跨实例原键恢复通过；20原始HTTP并发出现503，原断言只允许202/409，因此本轮失败，after-restart未执行。runtime security-1794462959651的passed=false、cleanupComplete=true，临时进程/库/账号/生成钥已清理。现有服务与客户端契约明确允许有界503/Retry-After，不能据它判定原写回滚；本轮尚未记录其精确error key，不能凭状态猜测根因。

复验增强：新增每JVM一个请求的低并发正确性用例，仍严格要求唯一成功＋202/409原键回执；保留20请求压力，不减少数量，不调整生产限额。压力用例写出仅node/status/error-key/Retry-After/policy的脱敏证据，503只允许精确common.request.unavailable及Retry-After=1，并仍必须两个宿主都查到同一原键SUCCEEDED、业务仅一条、异参被拒绝；不接受任意5xx、不自动重试写入。后续重启再验证三类原键。该限定依据既有协议，不声称503的内部原因已定位或所有压力请求都被受理。d2待整轮通过，不推进d3。

S1d2复验继续使用已验证指纹35e56f25d1df7771d0806815337587df61c4403061d21b134d66c50d04446035定位上述协议响应；检查发现并行租户线在该制品之后增加V6存储目录、StorageDirectory/Pool/LedgerProvider等源码。未修改或覆盖这些文件；另以security-s1d2-current独立输出重跑当前源码门禁，后续真实验证记录各自制品边界，不把前一个JAR计作新增租户能力验收。

S1d2第二轮before-restart 2/3：跨实例恢复、每JVM一个请求的严格202/409正确性用例通过；20请求用例在准备登录时触发既有PASSWORD预算（每IP+账号5分钟5次，成功不清窗），未发起压力请求。截图显示“验证过于频繁”，根因不是请求去重失败；仍保留失败记录。已改为每个阶段每节点正常登录一次、测试worker仅内存复用合法token，各用例仍使用独立浏览器上下文并由后端重新校验；后重启阶段由新进程重新正常登录。没有修改登录规则/额度、删除预算或存储token。回执比较改为顺序授权读，避免对同一读取预算制造无关竞争。前轮passed=false/cleanupComplete=true，未进入after阶段。

当前源码（含并行新增租户存储目录）根reactor26项再次通过，日志security-s1d2-current-artifact.log。下一轮使用这个新制品重新运行完整前后阶段，不将前两轮旧制品证据代替当前运行。

S1d2当前制品完整运行通过：security-s1d2-real-current.log的before-restart 3/3、after-restart 3/3均通过，0跳过；runtime security-1794464773670，制品SHA256 33d378b41ace28b2780b71e425fe4df4b16cdede175ab543adfbcaa46926c341。启动日志确认新租户V6 business storage directory已迁移，随后两服务仅validate；先前旧制品两轮失败证据保留，不冒充本次运行。

六场景包括跨标签经另一JVM原键核对且不重发、每JVM一个请求的严格唯一成功/202或409、20请求压力唯一业务结果及异参拒绝、全部服务进程退出重启后以新会话核对三类旧键、重启后完成键重放/异参拒绝、不同对象独立更新。压力脱敏记录concurrent-outcomes.json为1成功、2处理中、11完成回执、6个503 common.request.unavailable/Retry-After=1/LOCAL；不宣称20个请求全部受理，也未自动重试写入。两节点均查到同一原键SUCCEEDED、业务只有一条；503内部来源未通过该黑盒结果进一步定位。

结果manifest的passed/cleanupComplete均true，前后阶段各3通过/0失败/0跳过且reportAvailable=true，5个宿主生命周期均有退出记录；18261/18262/5206独立复查无监听，auth-security.keys/openapi.key不存在，脚本已查询确认生成库/账号不存在。根reactor最新26项、隔离原语5项及新增测试类型/lint通过；security-s1d2-frontend-verify.log完整verify61文件315项、baseline128/route30、应用/测试类型、eslint/i18n/构建发布通过（160KiB gzip/38 lazy modules/296 files）。文档175个本地链接检查无缺失。

S1d2已完成本机隔离验收，README/开发手册/前端规则及调度同步。此为同机双独立JVM、同一专项MySQL、指定制品与single模式证据；不替代P08 multi/LB、多主机部署或P09外部服务。下一S1d3身份/租户/撤权与原生协议代表场景；S1d整体/S2及四线X5均未完成。

S1d3按依赖拆分：d3a真实同租户不同操作者/超级管理员的LOCAL与上传回执隔离、撤权/重新登录后的当前授权、上传已提交丢响应后的原生核对、删除后旧成功回执不得恢复文件；d3b用专项库内明确TEST目录/账号夹具验证真实Host的租户绑定、跨租户Bearer/业务对象/回执拒绝，以及Google/邮件/存储配置租户隔离（不调用外部提供方）；d3c完成当前制品/两前端必要回归与记录合流后才结束S1d。夹具只供本轮生成库，不增生产鉴权旁路、不修改密码策略/预算；TEST域名不是DNS/TLS验证证据。当前先d3a，前置通过后再d3b。

S1d3a完成（2026-11-12）：新增`request-governance-scope.spec.ts`四个真实Java/MySQL场景，均通过（2.4分钟，0失败/跳过）。角色和SELF成员由公开IAM接口创建，正常登录；会话只保留worker内存，写入间隔遵守生产500ms预算。验证LOCAL回执对其他成员及超管不可见、相同键按操作者独立；上传回执本人专属，普通SELF成员读取他人文件403，但超管按文件ACL可读；撤权后重新正常登录，旧LOCAL及上传回执均403；浏览器真实提交后丢响应，只上传一次、刷新后原生核对，删除后历史SUCCEEDED保留而详情/下载/原键重放不能恢复文件。异文件名同键明确409 conflict。

制品门禁根reactor `-Dsma.verify.directory=target/security-s1d3/target -Dtest=ModularityTests,ArchitectureRulesTests,SecurityEndpointInventoryTests,RequestConfigurationTests,RequestAdmissionTests,RequestFingerprintKeysTests -Dsurefire.failIfNoSpecifiedTests=false verify` 26项通过（security-s1d3-artifact.log）。真实命令为security scope、18261/5206、指定该JAR，`node scripts/run-real-e2e.mjs request-governance-scope.spec.ts`；日志`.e2e-runtime/security-s1d3a-real.log`，runtime `security-1794467289169`，SHA256 `f707d192529d8d28aab78d26fe338f435c29a4c44412c5f2a0f75796a66600da`。首次迁移约9分钟，继续观察活进程后正常就绪，未重启用户服务。

`result.json`的passed/cleanupComplete=true、credentialsPersisted=false；两个宿主生命周期均已退出，18261/5206无监听，生成auth/openapi钥文件不存在，runner查询确认专项库/账号不存在。初次类型检查发现randomUUID推导类型过窄，仅修正测试参数声明后类型/lint通过；完整`npm run verify`（security-s1d3a-frontend-verify.log）61文件315项及baseline128/route30、类型/lint/i18n/构建发布通过。文档178个本地链接无缺失。README、开发手册、前端规则与调度同步；d3b跨租户配置/d3c仍待完成，不据此关闭S1d、P09或X5。

S1d3b开始：runner增加显式`SMA_E2E_SECURITY_TENANTS=1`，只允许匹配本轮生成security库；首次宿主退出后建立tenant2/TEST域名、不同全局ID的测试管理员与完整复制权限目录，并在数据库内部复制本轮密码hash，不读出凭据。所有API仍经真实Host入口绑定和正常登录。每轮生成独立租户加密环，仅该子进程使用并纳入清理；不调用Google、SMTP或云提供方。先验证双向Bearer拒绝，再验证对象/原键隔离、同编码的Google/邮件/LOCAL/OSS/S3/R2各租户配置与跨租户读写拒绝；该夹具不代表DNS/TLS或真实租户开通验收。

S1d3b本地完成：`SMA_E2E_SECURITY_TENANTS=1 node scripts/run-real-e2e.mjs request-governance-tenant.spec.ts`七场景全部通过（1.5分钟，0失败/跳过）：真实Host绑定/双向Bearer 401、同编码业务及同键LOCAL/上传回执隔离；Google客户端/realm关联、邮箱、LOCAL/OSS/S3/R2分别同编码配置、跨租户列表/详情/更新拒绝与A修改不影响B。两方先确认相同管理权限，合法配置跨租户更新必须返回精确not-found，不能把参数错误/无权限当隔离成功。虚拟密钥不从API返回，留空更新保留credentialsConfigured；未访问提供方。

日志`.e2e-runtime/security-s1d3b-real.log`，runtime `security-1794468523026`，仍使用前项固定SHA256 `f707d192529d8d28aab78d26fe338f435c29a4c44412c5f2a0f75796a66600da`。manifest passed/cleanupComplete=true、credentialsPersisted=false；两个Java生命周期退出，端口18261/5206释放、生成三类钥文件清理，库/账号删除并查验。单实例重启日志出现Cache node membership CONFLICT告警，本项不验证multi资格；未据此忽略测试失败，七项接口结果均成功。

隔离原语6/6（含禁止普通/任意库与SQL注入范围）、测试类型与lint通过；首次仅测试编辑对象索引类型错误已修正。完整`npm run verify`（security-s1d3b-frontend-verify.log）61文件315项、baseline128/route30、类型/lint/i18n/构建发布通过，182个文档链接无缺失。二开手册与前端规则已同步。当前并行P08在固定JAR后更改CacheTaskDispatcher等源码，下一d3c用独立输出重建当前门禁制品并组合复跑d3a/d3b，保留前轮指纹边界；旧版必要回归与记录合流后才结束S1d，不改P08并行实现。

S1d3c首轮当前制品构建失败（security-s1d3-current-artifact.log）：在P08并行编辑窗口内，MailDispatchCycle已调用带Permit的send，而SmtpMailSender尚为旧签名，编译失败。未回退或改写并行文件；随后观察发送器和测试同步到新签名，开始独立输出复验（security-s1d3-current-artifact-recheck.log）。旧版标准入口`powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/dev/verify-frontend.ps1`已通过28文件55项、类型构建/lint/i18n；`npx playwright test --config playwright.security-legacy.config.ts`独立旧版四场景通过，日志security-s1d3c-legacy-verify.log及security-s1d3c-legacy-browser.log。d3c仍未结束。

S1d3c及S1d本地验收完成（2026-11-12）：当前源码根reactor `-Dsma.verify.directory=target/security-s1d3-current/target -Dtest=ModularityTests,ArchitectureRulesTests,SecurityEndpointInventoryTests,RequestConfigurationTests,RequestAdmissionTests,RequestFingerprintKeysTests,LoginPasswordPolicyTests,TenantCredentialCryptoTests,RequestInputFingerprintTests -Dsurefire.failIfNoSpecifiedTests=false verify` 39项通过（security-s1d3-current-artifact-recheck.log，1分13秒）。固定制品SHA256 `4d6bebde11924d018664febb8d41733f61096b0272c63d03c7b9ed4de115b6ab`；沿security/双租户开关/18261与5206，`node scripts/run-real-e2e.mjs request-governance-scope.spec.ts request-governance-tenant.spec.ts`组合11/11通过，0失败/跳过（2.8分钟）。日志security-s1d3c-real-current.log、runtime security-1794469336655；本项固定制品包含Mail许可新签名，但不代替P08邮件排空专项验收。

最终manifest passed/cleanupComplete=true、credentialsPersisted=false；两个Java生命周期17416/47932确认退出，18261/5206及旧版5197均无监听；auth-security.keys/openapi.key/tenant-credentials.keys全不存在，runner已查验库/账号删除。结合本批新版完整315项、隔离原语6项、旧版55项与四场景兼容，完成d3；189个相关文档链接无缺失，README/手册/前端规则/调度同步。S1d1/d2保留各自制品与4/6场景证据，不相加冒充单轮全量；20压力请求中503的原限定继续有效。S1d标本地完成，下一S2受信来源/安全事件采集；生产multi/LB、跨设备、真实Google/SMTP/云对象传输、P09容器与X5均不在本次通过范围。

S2a1/a2本地完成：受信来源采用严格IP/CIDR、从右向左的有界X-Forwarded-For及请求生命周期，只读观测不改Host/认证预算；固定事实信号从认证上下文捕获归属，匿名/未知/不匹配不落默认租户。来源35基础/37真实HTTP组合/10部署回归分轮通过，最终信号＋来源/ActorContext/部署/架构根reactor52项通过，详情/命令/制品见[采集进度](security-event-progress.md)。真实宿主45220已退出、54214无监听，使用专用可清空sma_test_security_source。README/后端规则/部署/运维/二开文档及调度同步。信号捕获没有持久化、告警或封禁效果；下一S2b独立平台/租户事件存储及授权，S2整体未完成。

S2b本地完成：新增defense模块，平台/租户事件物理分表、独立短事务、共享容量/去重/不可变正文、有界保留清理及平台运维SPI+权限。最终独立MySQL根reactor38/38通过（含8存储场景），部署装配/配置/架构/模块/i18n/普通端点通过，测试进程及临时约束已清理；失败历史、命令和指纹见[采集进度](security-event-progress.md)。二开查询通过SecurityEvents，不开放任意事件HTTP写入。下一S2c有界采集/持久缓冲/恢复与丢弃指标；生产采集与HTTP管理、升级联合门禁后续，S2及X5仍未完成。

S2c本地完成：只接Fact的有界内存准入/速率和优先预留、独立后台线程、节点范围绑定AES-GCM缓冲、部署准入、原ID失败恢复/去重与固定指标。最终根reactor49/49通过，包含3真实数据库、10采集组件、9文件场景及部署/结构门禁；测试进程/临时约束清理完成，指纹和失败历史见[采集进度](security-event-progress.md)。默认禁用，入队仅QUEUED_VOLATILE；二开调用和配置已入手册。下一S2d生产拒绝点/管理查询/保留调度，尚不宣称全站攻击事件自动采集、告警或S2整体完成。
