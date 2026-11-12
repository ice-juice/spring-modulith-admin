# P10 租户中心实施进度

跨线安全补记（2026-11-12）：P11 X4品牌公开资产本地验收已完成。file的公开快照编码器固定JDK PNG/JPEG/GIF及PNG writer，输入/可寻址缓存/输出有界；图片/URL/架构41项通过，专用`sma_test_p11_x4_20261112`库的公开字段、权限/租户/Host注入矩阵和真实Tomcat HEAD最终10项HTTP通过，发布/回退/预览/旧文件/头像分轮回归通过。未修改tenant表/迁移或本线在建S5代码，不改变S5～S10状态，不替代P09完整扫描及X5；完整失败和复验记录见[安全进度](security-defense-progress.md)及[开发调度](development-schedule.md)。

方案：[P10](proposals/P10-tenant-branding-and-storage.md)。2026-11-11 用户授权设计落盘并开始实施；删除旧公共参数接口，不做兼容代理；SQL 统计按实际配置/执行的数据源隔离。

## 顺序与门禁

每项通过后立即记录命令、结果及边界再进入下一项。计划中的测试名称不表示已经存在或通过。全部任务保留，不能因只完成基础契约就标记整体完成。

| 顺序 | 任务 | 依赖 | 验证方法 | 状态 |
| --- | --- | --- | --- | --- |
| S0 | 固化完整方案、影响范围、接口删除与验收矩阵 | 无 | 文档链接、范围审查 | 完成 |
| S1 | 稳定数据源身份、实际 JDBC 连接标记与 SQL 归属基础 | S0 | 连接/Statement 归属、相同 Mapper 多源/租户、原异常与资源关闭、架构测试 | 完成 |
| S2 | 租户/域名登记、可信匿名入口与认证一致性 | S1 | MySQL 迁移；Host/代理/未知/停用；跨租户登录、MFA、WS、P09 身份拒绝 | 完成（本地专项） |
| S3 | 品牌草稿/发布/回退、素材、公开白名单入口，删除旧 public API | S2 | 归属/权限/发布原子性/缓存；旧映射消失；两前端消费者迁移 | 完成（本地＋隔离联调） |
| S4 | login1/login2/login3、品牌与域名租户 UI | S3 | 前端 verify；明暗/双语/移动/键盘；真实隔离登录和发布回退 | 完成（本地＋隔离联调） |
| S5 | 平台共享参数/字典、租户覆盖和发布/缓存语义 | S4 | 旧数据迁移；继承/覆盖/隐藏/恢复/树约束；全租户失效与权限 | 完成（本地，S5a～S5f验收；外部部署另验） |
| S6 | 资源登记、业务双持久化边界与路由/连接池预算 | S5、P11 X1 | 独立业务夹具、真实两个 MySQL 库、事务固定、错库拒绝、池回收/耗尽 | 完成（本地，2026-11-12；生产开通归S7） |
| S7 | 业务库 Flyway、Outbox/失效、开通/迁移/切换任务 | S6 | 空库/旧库、共享库仅迁移一次、失败续跑、提交/回滚/重复/并发、换库恢复 | 进行中（S7a/S7b完成，下一S7c） |
| S8 | Redis 多资源与缓存归属、故障隔离 | S7 | 真共享/独立 Redis、切换/冷启动/失效、配额/熔断；P08 协同回归 | 待实施 |
| S9 | SQL 数据源目录/筛选/定向重置、资源运维 UI | S8 | 实际双库/旧连接修订/未知源/越权/重置竞争；前端真实操作与迟到响应 | 待实施 |
| S10 | 容量与故障评估、全量回归、部署/二开文档 | S9 | 根 reactor 标准 verify、双前端、浏览器、1/10/100租户冷热/故障矩阵、恢复演练 | 待实施 |

## 2026-11-11 开始记录

- 分支 main，属于通用基座。工作区存在 P08/P09、认证、规则、前端等未提交变更，保留全部现状；每次编辑前按具体路径复查。
- 已核对当前 SqlTelemetry 为 tenant＋Mapper 聚合，无资源身份；public 参数接口仍存在；平台/业务 Mapper 仍共用数据源。
- P08 当前 Redis S2 已完成、后续进行中；P09 ActorContext 已新增，入口设计不能覆盖其权限限制。
- 并行安全防御专项已约定品牌/域名/平台运维由本 P10 唯一实现；其 BrowserRequestFilter 已加入同源/内容类型保护。S2c 接入入口过滤时同时核对 Origin、原始 Host 和可信代理顺序，不覆盖安全防御改动，不重复创建品牌实现。
- 当前阶段不启动/重启业务服务，不清空默认测试库。后端使用独立 `target/p10-*/target` 输出和 `sma_test_p10_*` 数据库。
- 尚未执行本任务验证，所有能力按阶段状态认定，不引用旧测试数作为本次证据。

### S0 验收

- 已创建 P10 完整设计与此计划，README、方案索引、总进度已链接。
- PowerShell 遍历两份新文档相对 Markdown 链接：`P10 document links PASS`。范围复核包含品牌/域名/模板、共享配置、二开存储与事务、Redis、实际数据源 SQL、性能/迁移/双前端与集群边界。
- S0 完成，只证明方案与计划落盘，开始 S1。

### S1 当前验证

- 新增 DataSourceIdentity 与连接标记，平台真实池装饰、Statement 归属；SqlTelemetry 聚合包含 resourceId/revision，未知源显式 unknown，不读取 JDBC metadata/URL。原 Statement 构造器保留，统计数据只新增字段。
- 首次编译发现 DatabaseTelemetry.Statement 与 JDBC Statement 同名遮蔽，已使用全限定类型修正。单元测试对 MyBatis Invocation 的反射异常改为核对原始 root cause，JDBC 装饰器自身仍验证原 SQLException 不变。
- 标准根 reactor 专项：`tools/dev/verify-backend.ps1 -Standard -MavenArgs @('-pl','backend','-am','-Dtest=DataSourceIdentityTests,SqlDataSourceTelemetryTests,ModularityTests,ArchitectureRulesTests','-Dsurefire.failIfNoSpecifiedTests=false','-Dsma.verify.directory=target/p10-s1/target')`；24/24、BUILD SUCCESS，日志 `backend/target/p10-s1-unit-final.log`。
- 创建专用库 `sma_test_p10_20261111_1715`，仅授予现有测试账号该库权限；不使用其他任务/开发数据库。集成专项 `SqlTelemetryTests,MonitorTests,ApplicationSmokeTests` 首次在应用初始化失败：并行 P09 的 TenantCredentialCrypto final 类无法 CGLIB 代理；运行后复查源码已由并行工作修正（17:16:41），未覆盖该修改。重编译并重跑的成功结果见下。
- 首轮失败期间未跳到 S2。资源目录、定向重置及双物理库路由仍在后续阶段。
- 追加真实双库契约：独立创建 `sma_test_p10_jdbc_a_20261111`、`sma_test_p10_jdbc_b_20261111`；只在这两个库操作测试探针表，两个 Hikari 池最大各2连接，测试结束关闭。以 `SMA_TEST_DB_URL`/`SMA_P10_JDBC_DB_B` 指定两个库，标准 reactor `-Dtest=DataSourceIdentityMysqlTests -Dsma.verify.directory=target/p10-s1-dual/target`：1/1、0跳过、BUILD SUCCESS，日志 `backend/target/p10-s1-dual-mysql.log`。证明同一 MyBatis Mapper 实际写入两库且计数/错误/租户隔离正确，不表示已实现租户动态路由或资源管理 UI。
- 集成复验完成：`SqlTelemetryTests,MonitorTests,ApplicationSmokeTests` 共5/5、BUILD SUCCESS（17:22:44），真实 MySQL/迁移/应用装配/池 unwrap 正常，平台实际执行记录为 platform/revision 1。日志 `backend/target/p10-s1-integration-recheck.log`。S1 验收完成，单元/架构24项＋真实双库1项＋应用集成5项均通过；未验证完整分库路由、UI筛选或多实例统计。开始 S2。

### S2 内部验收顺序

| 子项 | 内容 | 状态 |
| --- | --- | --- |
| S2a | 域名规范化、匿名入口上下文与平台运维身份契约 | 完成 |
| S2b | tenant/domain 持久化、平台管理 API、开发入口显式登记与迁移 | 完成 |
| S2c | HTTP 入口过滤、会话/验证码/MFA/WS一致性、停用及重新启用不可复活会话 | 完成 |
| S2d | 真实多租户身份拒绝测试、既有认证/P09/架构回归 | 完成 |

S2a 验收：新增 tenant 模块及显式依赖、TenantHost/TenantEntry/TenantEntryQuery/TenantEntryScope、部署型平台运维名单。域名不做网络解析；拒绝 URL/用户信息/歧义端口/伪 IP/转发列表；匿名范围不创建身份、拒绝跨租户/portal嵌套、线程不继承、严格LIFO关闭。平台 superAdmin/自授权限/机器身份不能替代部署名单。标准 reactor 专项 `TenantEntryScopeTests,PlatformOperatorTests,ActorContextTests,ActorMethodSecurityTests,ModularityTests,ArchitectureRulesTests` 最终34/34、BUILD SUCCESS，日志 `backend/target/p10-s2a-final.log`。初轮33项通过后追加LIFO防护并复验。尚未接入 HTTP/数据库，不把纯契约测试当完整租户入口验收。

S2b 当前实现：新增 tenant V1 全局目录表（仅控制面元数据），租户登记/状态代次、域名全局唯一登记与查询、平台运维双重校验、显式 loopback 开发入口 bootstrap。普通域名注册保持 PENDING，不宣称已完成 DNS/TLS 激活；后续域名发布 UI 负责完整验证流程。tenant 内部 Mapper 仅供本模块，公开 TenantEntryQuery 只返回入口/安全状态。结构/语言/契约专项 `TenantEntryScopeTests,PlatformOperatorTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests` 28/28通过，日志 `backend/target/p10-s2b-contract.log`。真实 MySQL专项最终结果见下。

S2b 首轮集成4项中3项通过、1项测试断言失败：MockMvc完成HTTP调用后清理SecurityContext，测试随后调用服务得到“Human authentication required”，不是预期的平台自停用业务错误。HTTP权限断言本身通过。将服务断言放到请求之前，并新增并发状态更新同版本一胜一拒用例。未降低生产权限或修改通用认证行为来通过测试。

S2b 最终：标准 reactor `-Dtest=TenantDirectoryTests -Dsma.verify.directory=target/p10-s2b/target`，5/5、0跳过、BUILD SUCCESS，日志 `backend/target/p10-s2b-integration-final.log`。真实 MySQL验证新增迁移、域名规范化/全局唯一/仅登记不生效、TLS与租户状态门禁、暂停恢复安全代次递增、幂等bootstrap不复活停用域名、平台/租户/机器权限边界、并发状态更新一胜一拒。S2b完成；最终运行连接URL的forceConnectionTimeZoneSession拼写未对应驱动选项，下一次使用规范forceConnectionTimeZoneToSession；本专项不宣称验证了JDBC会话UTC（统计不以此失败为隐藏条件）。本任务运行服务未替换，所有测试进程已退出。

## 续作检查点

### S2c 本轮实现与验证（2026-11-11 续作）

- 当前权威源码确认上一轮为S2b完成，继续S2c，没有重做S1/S2a/b。新增TenantRequestFilter在BrowserRequestFilter/认证链之前解析原始Host；只对明确配置的代理IP读取单值X-Forwarded-Host/Proto，规范化后的scheme/host/port也提供给Origin检查。禁止框架/native再次处理任意转发头。未知/停用域名404、非法Host400、目录不可用503；不回退租户1，不由X-Tenant-ID授予范围。
- TenantEntryScope新增请求解析阶段标记，匿名HTTP缺入口时CurrentUser/TenantScope拒绝默认租户。全局目录读取的SQL统计跳过未知租户归属，不把未识别请求记入租户1，也不让观测阻断目录查找。
- 第一轮入口/浏览器/P09身份/结构专项40/40通过，日志 `backend/target/p10-s2c-entry.log`。安全版本守卫/验证码/入口/身份/结构后续专项39/39通过，日志 `backend/target/p10-s2c-security-final-unit.log`；随后仍有机器令牌和转发策略校验增量，最终需要复验。
- auth V5追加租户安全代次到会话、挑战、验证码，验证码同时记录portal；没有代次的旧会话/挑战要求重建，不能回填有效代次使旧材料复活。SecurityState最初采用独立READ_COMMITTED读，后为连接预算改为同连接当前读（见下）。WebSocket消费核对入口租户/portal后才原子取票，后台连接有效性仍校验会话。
- OpenAPI V3及签发/鉴权加入租户安全代次，机器接口仍走自己的签名身份链，不映射后台人类身份。停用期间不能新签发，恢复后旧机器bearer不能复活，可使用有效应用凭据重新签发。
- 测试夹具仅在test源码为Users.as合成身份登记真实测试租户/域名，并为无显式Host的MockMvc请求选择相应测试域名；不修改生产放行规则，显式Host始终优先。旧验证码夹具补齐portal/代次。
- 集成命令：专用 `sma_test_p10_20261111_1715`，规范URL参数 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`；标准reactor `-Dtest=TenantAuthenticationTests,TenantDirectoryTests,AuthFlowTests,TotpAuthenticationTests,SecurityPasswordTests,SessionManagementTests,SocketTests,OpenAuthenticationTests,BrowserSecurityIntegrationTests -Dsma.verify.directory=target/p10-s2c/target`。日志 `backend/target/p10-s2c-integration.log`，运行中，未把S2c标记完成。
- 后续单元/架构/双语专项46/46、BUILD SUCCESS，日志 `backend/target/p10-s2c-finalunit.log`。此后复查嵌套事务连接预算，将securityState从另借连接的REQUIRES_NEW改为同连接InnoDB FOR SHARE当前读，关闭该Mapper查询的一二级缓存；实际MySQL只读事务验证FOR SHARE可用。新加“4连接池全部被事务占用时仍能读取状态”与旧事务快照拒绝专项，需随最终集成再次通过。此变更避免把安全状态的新鲜度依赖无限新增连接；当前读持有的共享锁会让租户状态写等待已有事务结束，这是明确的在途边界。
- SocketTests使用第二个真实回环地址127.0.0.2登记租户252入口，首先验证向127.0.0.1错误租户入口提交票据被拒且不误消费，再验证正确入口连接/消息/重复票据/强退。测试夹具扩展到带test profile的随机端口测试；已存在目录不执行无意义UPDATE，避免夹具在业务事务中额外锁住租户行。
- 首轮真实MySQL回归40项：38通过；两个失败均已定位为测试夹具更新需求：随机端口SocketTests未使用IntegrationTest元注解，合成租户未登记；portal拒绝用例省略了Login record必需的remember字段，先被JSON400拒绝而未到业务断言。前者已扩展test profile夹具并增加真实跨域票据检查，后者补字段且明确断言invalid-credentials。没有放宽生产验证。普通登录4、密码安全7、会话1、TOTP11、浏览器来源2、机器接口5、目录5均通过；新租户认证其余3项通过。
- 最终复验使用同一专用库和完整9类专项，并纳入最新当前读/连接池满载/旧会话拒绝实现，日志 `backend/target/p10-s2c-integration-final.log`；运行中，结果未出不标记S2c完成。
- 当前读与异步匿名缺范围保护的单元/结构/语言专项47/47通过，日志 `backend/target/p10-s2c-current-read-unit.log`。复查后台WS有效性时补充非admin portal语义：无HTTP入口的可信后台按持久会话portal检查，不错误套用默认admin；有入口或人类身份时仍严格匹配portal，应用用户/机器身份不能借此签发人类会话。新增partner入口真实联调，以及带同源Origin的真实WS连接，待补充集成复验。
- portal补充后的专项48/48通过，日志 `backend/target/p10-s2c-portal-unit.log`。生产源码当前没有待运行的单元失败；真实MySQL第二轮仍在进行，不能由48项单元代替其结果。
- S7开通编排还须统一默认初始化租户：现有IAM/Config等ApplicationReady初始化沿用租户1，不能仅改变platform-tenant-id就宣称完成整个平台搬迁。S2验证使用默认平台租户1；平台ID迁移/非默认引导须有专门编排证据。
- S9补充范围：入口解析前的全局目录SQL目前不错误记入租户1；未来平台运维总览应显式区分控制面统计与租户统计，不借默认租户泄漏全平台流量。当前租户列表仍不是全库SQL审计。
- S2c第二轮集成42/42、0跳过、BUILD SUCCESS，日志 `backend/target/p10-s2c-integration-final.log`。真实池4连接全部被事务占用时安全代次查询完成、旧业务快照看不到新状态仍被当前读拒绝、无代次旧会话拒绝并可重新登录；同名账号/令牌/验证码跨租户、暂停恢复、MFA挑战、机器令牌、真实WS错误入口与重复票据均通过。既有普通登录/密码安全/锁屏/TOTP/浏览器来源门禁通过。S2c完成，进入S2d；新增partner门户和真实WS Origin增量由S2d最终验证，不把测试未覆盖的范围称为已验收。

### S2d 回归记录

- 首轮标准reactor57项，55通过、2失败，日志 `backend/target/p10-s2d-regression.log`。真实partner门户、带Origin的WS、密码策略及写入、认证持久化、SQL统计、应用启动、架构/语言/P09身份均通过。
- 两个失败在MailProfileTests：该测试通过`.with(authentication(...))`在Spring安全链中注入身份，但沿用localhost平台入口。真实生产边界应拒绝此不匹配，而非在Mapper租户检查中变成500。新增TokenAuthenticationFilter对后建立的身份再次核对入口；unknown/跨租户身份在执行Controller前403，公开登录仍忽略旧Bearer但不忽略身份范围。非公开管理路径同样解析入口；health/info/error和独立/open协议链保留明确例外。
- 测试增加Users.request，为显式认证请求登记/选择对应测试域名；调用方提供Host时不覆盖，以覆盖身份/Host不匹配拒绝。Mail测试保持原密码不可返回/只写/跨租户不可读等断言，并增加错误Host应403；未降低任何生产权限。
- TokenTenantBoundaryTests及入口/身份/架构专项39/39通过，日志 `backend/target/p10-s2d-boundary-unit.log`。相关真实HTTP/WS/机器接口/应用初始化6类复验中，日志 `backend/target/p10-s2d-boundary-integration.log`。S2d尚未完成，S3未开始改代码。
- 最终6类真实MySQL/HTTP/WS复验26/26、0跳过、BUILD SUCCESS，日志 `backend/target/p10-s2d-boundary-integration.log`。邮件6、独立门户/旧材料/池满载/旧快照等租户认证7、机器认证6、普通登录4、真实WS1、应用初始化2均通过。此前S2d通过的密码/持久化/身份/架构/SQL专项与本轮拒绝路径证据合并完成S2；没有修改或重启当前运行服务，没有宣称多实例/浏览器或外部DNS/TLS验收。

### S3 内部实施顺序

| 子项 | 内容 | 状态 |
| --- | --- | --- |
| S3a | 类型化品牌字段/模板注册与双语校验、草稿/不可变修订/发布指针及迁移 | 完成 |
| S3b | 素材公开契约、引用保护、发布与回退权限/并发/缓存语义 | 完成 |
| S3c | 新entry白名单API、删除public映射/白名单、旧参数迁移/唯一写入口 | 完成 |
| S3d | 双前端消费者和mock/e2e迁移、类型/构建/真实品牌接口验证 | 完成 |

S3a当前：新增BrandContent schemaVersion=1的身份/登录/法律/链接/外观/素材/继承模式字段；受控login1/2/3注册表，不接受任意组件URL；类型化字段和安全链接校验、法律主体禁止平台隐式继承。tenant V2增加草稿头/版本/发布指针与不可变发布修订表；BrandDraftService限定当前可信租户＋portal并核对view/edit权限，保存草稿不发布、旧expectedVersion冲突。权限/发布API与素材能力在S3b接入，此阶段不公开草稿。

S3a契约＋架构＋语言专项24/24、BUILD SUCCESS，日志 `backend/target/p10-s3a-contract.log`。真实MySQL的BrandDraftTests（租户/portal隔离、权限、保存不发布、非法内容无写入、并发编辑一胜一拒）在同一专用库执行，日志 `backend/target/p10-s3a-draft-integration.log`，结果待记录；尚未标记S3a完成。

S3/S4详细目标DTO、权限、发布/回退并发与幂等、素材授权和双前端替换清单已写入[品牌契约](tenant-branding-api.md)。该文档明确标注未验收路由，不表示管理端或公开API已存在。

S3a真实MySQL最终3/3、0跳过、BUILD SUCCESS，日志 `backend/target/p10-s3a-draft-integration.log`。验证新tenant V2迁移、草稿JSON往返、租户/portal隔离、无权限/非法输入不建记录、草稿保存不产生发布或改变指针、同版本并发编辑一胜一冲突。与24项契约/架构/双语检查共同完成S3a。当前没有本任务仍在运行的测试会话；未部署用户服务。

### 当前下一步：S4

#### S4内部顺序与范围

| 子项 | 内容 | 验证 | 状态 |
| --- | --- | --- | --- |
| S4a | 三种静态登录布局，共用认证/MFA状态机；已发布标识/欢迎文案/背景/法律链接展示；服务端模板退役安全回退 | 展示契约、两套前端类型/构建、三模板明暗/双语/窄屏/键盘及原认证回归 | 完成 |
| S4b | next品牌管理页：字段/继承模式、图片上传、模板缩略图/预览、草稿/发布/历史/回退、版本冲突与未知结果保护 | 表单单测、权限拒绝与并发浏览器、真实隔离发布/回退 | 完成 |
| S4c | 租户/域名管理UI与域名校验/启用流程 | 平台权限、未知/未验证域名不启用、校验/状态变更及UI联调 | 完成（本地＋隔离目录联调） |
| S4d | 全阶段回归、文档/路由清单/旧版基线同步 | 根后端相关门禁、双前端verify、隔离真实浏览器 | 完成 |

拟改范围沿用P10授权：frontend-next的components/tenant、features/tenant、views/LoginView、features/auth/TotpPage（旧端views/auth/TotpView）、branding store、路由/语言/管理壳；旧前端仅维护登录/品牌消费兼容，不复制next源码或新增旧版管理页。管理UI复用现有上传/表单/权限/未保存保护。S2当前域名API只能登记PENDING，不能把它误称为已提供自有域名启用；S4c补完整流程后再验收该界面。任何测试使用独立端口/库，不重启用户服务。

#### S3b 实施记录

- 已实现file的ImagePublication公开契约：必须在调用方事务中锁定同租户/portal文件并检查现有授权；只接受受支持的PNG/JPEG/GIF，解码像素后丢弃元数据/动画/尾随载荷并重编码静态PNG。单图原始/输出各≤2MiB、边长≤4096、≤400万像素，进程内最多4个并行解码。此机制不宣称等同杀毒扫描；WebP/ICO解码尚未提供，Favicon可使用PNG。
- tenant V3新增品牌引用和已发布图片快照表、发布操作回执字段。草稿保存校验文件后建立引用，引用中的私有文件只对具备品牌管理权限的当前租户人员授权；编辑人与发布人可不同。FileAccessPolicy保护草稿/历史引用不被删除，检查使用当前读；没有放开普通附件匿名下载。
- BrandPublicationService将发布/回退和素材快照、发布指针在同库事务内提交，锁定头版本，发布后快照不可改写，回退生成新修订。同操作ID仅相同操作者/相同意图重放原回执；变更意图/权限撤销/另一个用户拒绝。每portal最多100个历史修订、64MiB图片快照，达到上限明确拒绝，不自动删除回退历史。
- BrandResolver在服务端合成INHERIT/OVERRIDE/HIDE，隐藏字段从公开内容中移除；法律主体不得继承平台主体。部署默认 `sma.tenant.brand-defaults`带revision，禁止跨租户继承文件引用，发布保存合成结果及默认版本；调整默认不会偷偷修改已发布历史。实时公开读取仍直接查权威发布指针，无进程缓存，不存在提交前/旧草稿回填。
- BrandController已接入草稿、模板目录、发布、回退、历史管理API与分离权限，当前没有新增匿名HTTP文件入口；S3c再接entry/品牌图片白名单并删除旧public接口。
- 首轮架构失败为并行identity模块GoogleIdTokenVerifier使用java.util.Date；复查其源码已由并行工作改为epoch秒，没有覆盖或放宽架构规则。重跑BrandValidationTests/BrandResolverTests/PublicImageEncoderTests/ModularityTests/ArchitectureRulesTests/I18nBundlesTests：27/27、BUILD SUCCESS，日志 `backend/target/p10-s3b-contract-final.log`。
- 真实MySQL/HTTP/文件专项在新建独立库 `sma_test_p10_s3b_20261111` 运行，保留此前测试库；标准reactor `-Dtest=BrandDraftTests,BrandPublicationTests,BrandHttpTests,FileTests -Dsma.verify.directory=target/p10-s3b/target`，日志 `backend/target/p10-s3b-integration.log`，结果待记录。包含发布人非上传人、私有/跨租户文件拒绝、回退图像字节一致、引用删除保护、并发不同键一胜一拒/同键一份修订、授权回执重放和HTTP权限拆分。
- 补充BrandWorkBudget，最多4个持图事务，使用TransactionSynchronization在提交/回滚释放，不让不同租户的待提交图片快照绕过解码瞬时并发限制。与PublicImageEncoder独立4解码预算配合；没有图片的文本草稿不占该预算。当前真实专项启动早于此补充，最终需复验最新装配。
- 首轮真实集成11/11、BUILD SUCCESS（FileTests3、BrandDraftTests3、BrandHttpTests1、BrandPublicationTests4），日志 `backend/target/p10-s3b-integration.log`。预算专项中的BrandWorkBudgetTests通过，但同轮语言门禁因并行P08新增cache-task四类错误文案尚未复制到输出失败；复查当前源文件双语已齐全，不覆盖并行改动、不降低门禁。
- 最终合并单元/结构/语言/真实HTTP/图片/发布测试在新建专用库 `sma_test_p10_s3b_final_20261111` 执行，命令测试列表为 `BrandDraftTests,BrandPublicationTests,BrandHttpTests,FileTests,BrandWorkBudgetTests,BrandResolverTests,PublicImageEncoderTests,BrandValidationTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests`；日志 `backend/target/p10-s3b-final.log`。未出结果前S3b保持进行中。
- 旧库升级专项独立库 `sma_test_p10_brand_upgrade_20261111`：先以原tenant V1/V2迁移建库并插入带版本7的旧草稿，再正常迁移V3，验证JSON/版本/发布指针保留及再次migrate执行0条；无clean/repair。`BrandMigrationTests` 1/1、0跳过、BUILD SUCCESS，日志 `backend/target/p10-s3b-upgrade.log`；SMA_P10_BRAND_UPGRADE_DB为显式隔离库开关，无开关的跳过不能当升级通过。
- 最终组合运行39项：28项单元/结构/语言通过，11项集成因应用上下文未启动报错，日志 `backend/target/p10-s3b-final.log`。根因是并行请求治理的RequestGovernanceProperties增加重载构造器后未显式标注配置绑定构造器，Spring寻找无参构造器失败；不是品牌断言失败。保留新minimumWriteInterval和兼容构造器，只给规范构造器补ConstructorBinding，并在RequestConfigurationTests增加真实ApplicationContextRunner装配回归。先快速验证装配，再复跑11项集成；S3b保持进行中，不跳到S3c。
- 19:54最终复验完成：RequestConfigurationTests 2/2通过，日志 `backend/target/p10-s3b-binding.log`；真实MySQL/HTTP/文件/草稿/发布复跑11/11、0跳过、BUILD SUCCESS，日志 `backend/target/p10-s3b-integration-recheck.log`。结合本轮28项单元/结构/语言及旧库升级1项证据，S3b完成，开始S3c。未重启用户服务；尚未接入匿名图片路由和双前端。

- S3b已验收发布/回退/历史/幂等、公开快照合成与素材引用保护；S3整体仍未完成。新入口/删除旧public/双前端消费者属于S3c/d，login1/2/3实际渲染与管理页属于S4。图片重编码不代表通用杀毒扫描。

### 后续要求

#### S3c入口接入记录

- 新增BrandEntryView/BrandEntryService/BrandEntryController：GET /api/tenant/entry明确投影入口、已发布品牌与验证码能力；未发布只用本租户目录名称和空法律/素材，不读取草稿，不枚举publicReadable参数。GET /api/tenant/brand-images/{revisionId}/{slot}只返回本入口租户/portal的已发布PNG快照；缺图404，原文件仍需鉴权，响应no-store。
- Auth对白名单只开放上述路径的GET/HEAD，并与旧Bearer跳过逻辑共用一个精确匹配器；品牌管理路径和写请求不被放开，已建立身份/Host冲突仍由原边界拒绝。Tenant新增对Config公开ParameterQuery的依赖，仅投影login.captchaEnabled，未跨模块查表。
- 标准reactor `BrandEntryTests,TokenTenantBoundaryTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests` 27/27、BUILD SUCCESS，日志 `backend/target/p10-s3c-entry-contract.log`。新增真实HTTP入口3项与原管理HTTP1项、配置装配2项复验已开始，复用本任务已关闭会话的专用库sma_test_p10_s3b_final_20261111，输出target/p10-s3c/target，日志 `backend/target/p10-s3c-entry-http.log`。真实HTTP结果未出，不把纯契约验收当浏览器或完整S3已交付。
- S3c剩余旧参数迁移和唯一写入口、S3d双前端/旧public删除仍未完成；暂不部署这个中间组合。
- 旧数据转换已新增Config所属LegacyBrandSource类型化契约及Tenant纯LegacyBrandConverter；secret先脱敏，非公开值排除且只报告键名，非法/过长/不完整字段明确拒绝，不静默截断，不执行URL抓取。第一轮24项有1项失败：URI.normalize不消除开头的..；转换器补显式本地路径段校验（含编码形式），未改变共享WebAddresses。复验24/24、BUILD SUCCESS，日志 `backend/target/p10-s3c-converter-final.log`。当前仅完成契约与纯转换，Config读取/归档适配器、数据库迁移与启动编排尚未实现，不能标记旧数据迁移完成。
- 首轮HTTP组合6项，5通过1失败，日志 `backend/target/p10-s3c-entry-http.log`：未发布图片应404却500；原因是外层readOnly事务捕获内层BizException后，内层已标rollback-only，提交抛UnexpectedRollbackException。改为BrandPublicationService.findPublicImage直接返回Optional缺失，保留原publicImage抛业务错误的服务契约，匿名facade不再吞事务异常。重新执行真实入口/管理/发布及入口/转换器单元共14项目标，日志 `backend/target/p10-s3c-entry-http-final.log`；尚未出结果，不能把剩余图片/跨portal断言视为通过。
- 20:17复验14/14、0跳过、BUILD SUCCESS，日志 `backend/target/p10-s3c-entry-http-final.log`。真实入口3项证明公开DTO排除草稿/任意公开参数/secret、可信partner门户、旧Bearer不阻断读取、发布PNG可读、文件ID/跨租户/跨门户404、原附件仍401、未知/暂停入口拒绝；原管理HTTP1项、发布/回退4项及入口/转换器单元6项通过。没有浏览器或生产部署验收；S3c入口部分完成，旧参数持久迁移/唯一写入口尚未完成。

#### S3c旧品牌持久迁移验收

- 本轮已实现Config V5品牌归档标记及LegacyBrandParameters：所属模块锁定当前租户源行，FOR UPDATE且禁MyBatis查询缓存，SHA-256源版本核对后归档原行并同事务失效缓存。普通参数API/查询、初始化和重置拒绝7个品牌旧键，内置种子已移除；login.captchaEnabled保留。Tenant V4新增head/revision只读遗留背景及迁移回执，正常草稿输入不接受任意图片URL。
- LegacyBrandMigrationService由宿主启动按可信目录每批100租户执行，每租户一个平台事务；锁租户、转换所有已登记portal、插入首个发布/回执并归档，已有新草稿或发布直接冲突，不覆盖。无域名旧租户使用admin门户，单租户超过100门户明确拒绝。源数据无效/冲突使启动失败，匿名GET不触发迁移。只有已登记租户参与；已有不在目录中的历史租户须先完成目录接入，不能把此项称为历史租户ID自动发现/转换。
- 遗留背景只可由迁移初始化；上传替换或HIDE/INHERIT清除草稿引用，旧发布不变；回退复制历史引用，公开入口返回发布修订的legacyBackgroundUrl。并发幂等由租户/源写锁与持久回执保证，修订哈希涵盖该只读引用。
- 合同/模块/架构/语言检查27/27通过，日志 `backend/target/p10-s3c-migration-contract.log`。真实MySQL `LegacyBrandImportTests,ParameterTests,BrandEntryHttpTests,BrandPublicationTests,BrowserSecurityIntegrationTests` 在本任务专用库复验中，日志 `backend/target/p10-s3c-migration-integration.log`；新增目标包含多门户提交/中途冲突回滚、重复并发、非法/私有源、源变更检测、遗留背景发布/回退以及旧写入口封闭。未出结果不标记完成。
- 独立保留库sma_test_p10_brand_upgrade_20261111上运行LegacyBrandSchemaUpgradeTests：从已验证Tenant V3及补建的Config V4真实形状升级Tenant V4/Config V5，检查旧草稿版本/JSON/原参数不变及重复migrate为0；没有clean/repair。日志 `backend/target/p10-s3c-legacy-upgrade.log`，结果待记录。
- 实际旧库结构升级1/1、0跳过、BUILD SUCCESS（20:29），日志 `backend/target/p10-s3c-legacy-upgrade.log`。该保留库现为Tenant V4/Config V5，不能直接重跑要求初始V3的测试。后续补充启动分页/范围恢复/失败中止和SQL结构门禁，31/31通过，日志 `backend/target/p10-s3c-migration-contract-final.log`；品牌键按MySQL大小写不敏感规则拒绝别名写入/读取的专项1/1通过，日志 `backend/target/p10-s3c-brand-key-ownership.log`。
- 20:38真实迁移/参数/公开HTTP/发布/浏览器来源组合18/18、0跳过、BUILD SUCCESS，日志 `backend/target/p10-s3c-migration-integration.log`。当前数据迁移和归档主流程已获得真实库证据；另补失败字段名称诊断（不含值）及双语门禁10/10、BUILD SUCCESS，日志 `backend/target/p10-s3c-migration-diagnostics.log`。测试拒绝用例的遗留源夹具已增加按本方法创建ID清理，避免后续新测试上下文的启动迁移被故意非法源污染；该清理不改变产品断言。

#### S3c删除旧接口＋S3d双前端切换验收

- S3d现已删除ParameterController旧public映射、Auth两处匿名名单；双前端store及next mock/full/foundation/softlight夹具改typed entry。前端从服务端取得portal，拒绝旧字典响应和非法资产路径；失败清空旧品牌、禁用提交/验证码，代次丢弃迟到响应，确认入口失效/范围变化使旧身份失效。验证码同样丢弃旧入口/卸载后的响应，保留原MFA流程。
- 初轮两套品牌单测5＋6通过。类型检查发现空对象分支的推断与Record索引签名冲突，已补显式返回类型，双前端type-check及next测试类型检查通过。新增隔离Playwright双工程测试首轮6失败/2通过，原因是测试glob误拦/src/api模块加载；修正为URL pathname以/api/开头后8/8通过（绑定门户、失败禁用/显式恢复、拒绝旧字典、真实HTTP和WS代理保留Host/Origin）。5197/5198/5199专用端口，不复用或重启当前业务服务。
- 后端旧路径验证首轮36项中32通过、4项因测试注入RequestMappingHandlerMapping时Actuator同类型Bean造成歧义未启动；已限定requestMappingHandlerMapping并只复跑ParameterTests，日志 `backend/target/p10-s3d-retired-endpoint-final.log`，尚未出结果。原失败日志 `backend/target/p10-s3d-retired-endpoint.log` 保留。
- 旧版基线检查只命中本次6个有意修改文件：branding store、LoginView、login-ui test、双语common、Vite代理。已核对差异，保留既有P07流程；仅更新这6个哈希，并补登记新tenant-branding契约与branding单测2个文件。没有重建或覆盖其他旧版基线。接下来执行两套完整前端门禁。
- 完整前端门禁已通过：next的baseline/inventory/双类型检查/lint/i18n/99项单测/build/release通过，日志 `backend/target/p10-s3d-frontend-next-verify.log`；旧版标准tools/dev/verify-frontend.ps1的eslint/build/53项单测/i18n通过，日志 `backend/target/p10-s3d-legacy-frontend-verify.log`。新浏览器测试也纳入tsconfig.tests；没有跳过基线或发布预算。
- ParameterTests限定Bean后3项通过、旧路径断言1项失败：已认证GET实际为405而非404，原因是现有PUT/DELETE /{id}的MVC方法匹配；旧GET映射不存在和匿名401断言已通过。修正测试期望为405，不改生产路由去迎合错误期望；追加旧路径过期Bearer仍须校验的单测。仅复跑旧路径方法，日志 `backend/target/p10-s3d-retired-mapping.log`；独立输出打包/认证契约日志 `backend/target/p10-s3d-package.log`。
- 真实双前端联调准备新增e2e-real/tenant-entry.spec.ts：新建隔离库/账号，正常登录、图片上传、保存草稿/发布、读取实际公开PNG和两套登录页；不复用8080/5173等用户服务。隔离runner显式登记回环域名并等readiness，避免UP先于ApplicationReady迁移/初始化结束；未降低生产域名规则。真实结果未出，不把8项mock/代理浏览器测试当后端连通验收。
- 隔离后端打包首轮被并行新增PublicationRequestGovernanceTests缺少EventPublication导入阻断；复查该文件已由并行工作补齐导入，没有覆盖其改动，重跑4项认证边界/打包通过，日志 `backend/target/p10-s3d-package-recheck.log`。真实联调使用该独立输出JAR，8097后端＋5197/5198双前端，创建任务专属sma_next_e2e_1794402941111库，运行日志 `backend/target/p10-s3d-real-entry.log`，工作日志 `.e2e-runtime/1794402941111/backend.log`。原进程持续迁移中，不因观察超时重启；结束后由已有runner只清理其创建的进程/库/账号。
- 旧映射最终单项1/1、0跳过、BUILD SUCCESS（21:22），日志 `backend/target/p10-s3d-retired-mapping.log`：映射目录无旧GET、匿名401、已认证405（现有PUT/DELETE /{id}产生的方法不支持），不返回原公共参数。结合此前参数其他3项、入口/迁移/架构通过结果及新增认证4项，完成后端删除验收；无兼容别名。
- 真实双前端联调1/1通过（21:24），日志 `backend/target/p10-s3d-real-entry.log`、`frontend-next/test-results-real-tenant/results.json`。实际新建隔离MySQL与打包后端：未知Host拒绝；错误URL portal不能覆盖admin绑定；通过正常登录/图片上传/草稿保存/发布，两套登录页显示已发布名称/版权/备案并读取PNG，原附件匿名仍401；两端均无旧public调用。关闭浏览器上下文时Vite记录ECONNABORTED；品牌/登录断言全部通过，WS代理Host/Origin另有8项mock/探针浏览器证据，不把关闭日志当全消息协议验收。
- runner已退出0，`frontend-next/.e2e-runtime/1794402941111/result.json`为passed=true；任务创建的后端、数据库、账号及临时前端已清理，8097/5197/5198/5199无监听。本任务没有部署或重启用户服务。开发启动脚本补显式回环域名与readiness参数，PowerShell AST语法检查通过；相同Java参数已在隔离联调生效，没有执行会替换用户服务的开发启动脚本。
- S3完成，进入S4准备。模板注册数据和品牌API已有；login2/login3实际组件、品牌/域名管理页面、配置共享、业务MySQL/Redis资源、SQL目录筛选/定向重置以及S10完整门禁仍未完成。
- 文档同步已完成：README、品牌契约、运行说明、双前端规则与进度均标明S3实际交付/后续边界，跨端约束集中在tenant-platform规则。旧版AGENTS变更单独审核并更新第9个本任务基线路径，最终126个基线哈希匹配；Markdown链接和diff空白检查通过。开发启动脚本仅做AST验证，未执行或重启用户服务。

- 数据迁移、双前端切换和旧接口删除均已验收。后续不得加回旧HTTP别名、从URL推导可信portal、吞入口错误回退默认租户，或从普通参数重新生成品牌源。
- 当前本任务测试会话均已结束；可复用专用sma_test_p10_s3b_final_20261111（会清空）和独立target/p10-s3c-migrate/target。保留的升级专项库现为Tenant V4/Config V5，不再满足先前V2/V3空库测试的前提。不要重跑已通过整套检查，除非新变更/失败需要覆盖；不要重启用户服务或覆盖并行P08/P09源码。
- 两套auth store已有attempt/generation和reset，LoginView在入口加载失败/更新时须禁止提交/验证码并丢弃旧响应；portal取entry而非route.query。不要因同身份语言/主题切换清空表单。frontend-next的旧版哈希门禁会命中本次有意兼容维护，按旧前端规则逐文件审阅后更新对应哈希，不能跳过门禁。

S3c按依赖细分：先接类型化entry及已发布图片HTTP读取并验证匿名/跨租户/草稿隔离，再实现旧品牌参数迁移与唯一写入口，最后与S3d双前端同批删除旧public映射及白名单。中间工作区的新旧路由并存不表示保留兼容接口；未完成整体切换前不部署。旧背景URL不得由服务端任意抓取，非空自定义背景必须有显式素材转换和迁移核对记录，不能无提示丢弃。品牌入口的DTO不复用完整BrandContent，必须排除源文件ID、覆盖指令、发布者与操作回执。

- S3消费者已迁移，旧路径只可出现在删除验证/历史文档中，不得恢复生产调用。Vite的changeOrigin/rewriteWsOrigin已关闭并通过真实代理探针验证，部署代理同样须保留或可信重建外部Host与Origin。
- S3品牌素材已通过file的ImagePublication及租户发布PNG快照提供，普通文件content继续要求登录；后续不能公开普通附件作为Logo捷径，必须保留引用删除保护、编辑/发布不同人员授权语义及模块无环。
- S3/S4前端必须从entry响应取得portal，替换LoginView当前从route.query.portal推导的值；查询参数不能覆盖域名绑定门户。unknown/停用入口不能吞错后显示另一租户默认品牌或继续提交登录。当前branding.load的吞错默认行为需要同步移除/改为明确入口不可用状态，迟到响应不得回写旧租户标题/Logo。

- S2/S3已完成，当前下一步S4。入口/认证已接入；后续不得破坏HTTP缺入口拒绝、Host/Origin一致性及租户暂停恢复安全代次。
- 会话、验证码、MFA挑战、WS的入口租户/portal一致性已验证；TenantEntryScope仍不是身份，P09 ActorContext不被替换。后续品牌/存储接入须保留暂停/恢复后旧会话不可复活的拒绝路径。
- 现有测试大量通过 Users.as 建立合成租户；新增正确的测试目录/Host夹具，不能为兼容测试在生产开放未知Host、跳过租户校验或默认放行未登记租户。保留P07密码/MFA与P09应用身份拒绝路径。
- 旧public映射/白名单/双前端调用已删除。部署与回退必须前后端成套，旧前端不能访问新版已删除接口。
- 后续S4～S10全部保留：模板UI、共享配置、真实业务分库/事务/迁移/Outbox、Redis多资源、SQL筛选与定向重置、容量/全量/浏览器验收。总体目标未完成。
- 后续测试可复用专用 `sma_test_p10_20261111_1715`（IntegrationTest会清空），双库身份测试使用另两个 `sma_test_p10_jdbc_a_20261111`/`sma_test_p10_jdbc_b_20261111`。输出保持独立target目录，不清理其他任务库，不重启用户服务。

- S4a模板退役投影与结构专项：标准reactor `BrandEntryTests,BrandValidationTests,ModularityTests,ArchitectureRulesTests`，26/26、0跳过、BUILD SUCCESS，日志 `backend/target/p10-s4a-template-contract.log`。未知历史模板安全投影login1，不改不可变修订。双前端布局/MFA/素材/法律链接仍在实施，未由后端专项代替浏览器验收。

- S4a双前端已接三布局、共用MFA页面、分主题Logo/紧凑Logo/Favicon、双语欢迎文案、版权年份/ICP备案/公安备案/五类安全外链。新增品牌单测各7/7，旧端完整标准验证55/55通过（p10-s4a-legacy-verify.log）；next最终verify 101/101、语言/类型/lint/构建/发布预算通过（p10-s4a-next-verify-final.log）。128条旧端哈希仅更新审阅过的P10兼容路径。模板退役后端专项26/26通过。
- S4a浏览器首轮mock过宽拦截了/src/api/http.ts，收窄为pathname以/api/开头；第二轮发现新版TotpPage模板开闭标签不一致，已修正并通过最终verify。旧端语言下拉的隐藏combobox不支持直接点击，测试改为聚焦与ArrowDown键盘操作。当前浏览器复验仍未完成；皮肤默认/个人策略以及管理页属于S4b待交付，不能由展示布局通过代替。

#### S4b实施细分（S4a浏览器通过后按序实施）

1. 补管理只读契约：按操作ID查询本人的发布/回退回执；服务端合成预览，复用BrandResolver及ImagePublication安全PNG管道，不写草稿/修订/发布指针。分别校验当前租户/portal、查看及动作权限。预览仍有图片大小/并发预算，不能开放私有原图或让匿名读草稿。
2. next类型化品牌表单与独立预览：identity/login/legal/links/appearance/assets/override模式；法律字段无继承选项；静态模板缩略图。预览使用独立同源iframe、受检postMessage、仅内存视觉数据，不执行登录请求、不写用户偏好、不把图片正文或草稿写URL/storage。
3. 保存/发布/回退/历史：独立权限；所有写入绑定版本/身份；发布回退固定operationId。网络未知结果仅查询回执，未确认不得自动重放；保存读回核对，版本冲突保留本地表单并明确选择远端。图片2MiB及格式前置检查，仍以后端安全检查为准。
4. 外观策略：默认皮肤与个人选择的优先级、匿名范围隔离、租户禁止个人皮肤时的有效显示；不覆盖既有个人保存值或用预览改变全局主题。补文档说明兼容旧端的外观范围。
5. 专项单元/权限/并发/未知结果与真实隔离浏览器发布回退通过后记录S4b完成；之后才实施S4c域名验证启用。

- S4a最终浏览器：`npx playwright test --config playwright.tenant.config.ts --max-failures=1`，20/20、54.2秒，日志 `backend/target/p10-s4a-browser-final2.log`。两端三模板×明暗，桌面中文/390px英文、表单保留、租户标题、对应主题Logo、版权/备案/外链、键盘提交、MFA沿用挑战且不重取entry、禁止持久化挑战均通过；截图已抽查next login1/2桌面和login3手机、legacy login3暗色手机。旧端弹出层关闭/窗口缩放后的布局断言改为等待稳定后判定，未关闭溢出检查。S4a完成；仅mock浏览器，S4d仍须实际后端完整联调。开始S4b。

- S4b-1管理辅助契约：已新增BrandPreviewService与POST previews（服务端合成/安全PNG/no-store/无持久写入）、GET operations/{id}（原操作者/当前租户portal/动作权限/NOT_OBSERVED语义）。新增真实MySQL的BrandPreviewTests三项；标准reactor初轮验证进行中，日志p10-s4b-preview-contract.log。启动后新增BrandHttpTests的匿名/权限/no-store/不建草稿断言，尚未编入该轮，最终须另行复验，不能把初轮当其覆盖证据。
- next增加类型化brands契约及保存未知结果核对函数；大整数版本、不同编辑者内容、HTTP失败分类、法律字段禁止继承、图片格式/大小两项单测通过。页面尚未实现。

- S4b中段：新增preview/operation真实服务与原契约组合30/30通过（p10-s4b-preview-contract.log）。后续加入AppearancePolicy（Config拥有SPI、Tenant按发布指针实现，不形成反向模块依赖），个人原始偏好缓存之后合成有效皮肤；租户关闭个人选择时保存其他偏好不覆盖旧皮肤。组合34项中33通过，唯一错误为新增HTTP测试在MockMvc请求结束清理SecurityContext后直接调用drafts.get，属于夹具生命周期问题；拆分Users.as后单独复验BrandHttpTests（p10-s4b-http-recheck.log），没有放宽生产身份校验。
- next品牌页面与独立同源预览已接入。管理专项mock浏览器3/3通过（p10-s4b-management-browser.log）；全套租户mock浏览器24通过、4项旧端不适用跳过（p10-s4b-browser-final.log），验证三模板/双端、独立预览不写偏好且不发业务API、发布回退、网络未知结果只读核对、迟到预览丢弃、只读权限与冲突保留。next完整verify通过（p10-s4b-next-verify.log）。之后补上英文系统名显示和新版顶栏简称消费，需按变更范围复验。真实整页验收尚未运行，S4b保持进行中。

- S4b补充HTTP最终复验2/2、0跳过、BUILD SUCCESS，p10-s4b-http-recheck.log；只修正测试身份夹具，生产权限未改。该轮完成当前backend/target/p10-s4b/target制品打包。英文名称与重复语言选择修正后的next完整verify105/105（p10-s4b-next-verify-final.log）、旧端标准55/55（p10-s4b-legacy-verify.log）均通过。
- 已启动隔离真实整页联调：当前已验证JAR，backend8097/next5197/legacy5198，runner自建随机库/账号；brand-management.spec.ts与tenant-entry.spec.ts，日志p10-s4b-real-brand.log。等待结果，不重复启动或重启用户服务。

- 续作检查发现并行请求治理已在23:20后更新Brand/Tenant/DomainController，晚于22:57的P10制品。保留其LOCAL/QUERY/NATIVE边界，不把旧制品真实联调称为当前源码验收。已核对security-b5c-integration.log：BrandHttpTests2/2、TenantRequestGovernanceHttpTests2/2，组合107项全通过（23:41）；发布/回退保持原生operationId，预览QUERY不落通用写账本。P10另以当前源码在target/p10-s4b-current/target重编译、运行模板/结构/Advisor/Admission专项并打包，日志p10-s4b-current-package.log，BUILD SUCCESS。当前旧制品隔离进程仍正常迁移，继续观察后清理；随后用新制品复验整页。

- 等待S4b真实后端就绪期间完成S4c只读复查，新增[域名验证设计](tenant-domain-api.md)：DNS TXT控制权、部署入口IP允许清单、固定IP/SNI/TLS证书校验、短事务与有界网络工作、显式启用/主域名/安全代次。当前未实现，不把PENDING登记当域名已可用；S4b实际联调通过后才开始S4c代码。

- 第一轮隔离实际浏览器（旧22:57制品）：tenant-entry.spec.ts通过；brand-management.spec.ts在首次登录仍显示加载中时，5秒表单断言先超时，尚未进入品牌管理步骤（p10-s4b-real-brand.log）。已改为先等待/tenant/brand导航完成再检查表单就绪；未重发登录或放宽认证。最新治理要求草稿PUT带Idempotency-Key，直接API测试同步生成随机键，UI客户端原已带键。runner仍在清理自身随机库/账号，等待其明确退出后再启动新制品；当前不能标记S4b完成。

- 最新制品真实品牌管理整页已通过：brand-management.spec.ts，33.6秒，实际UI上传Logo/背景、无写入预览、草稿未公开、发布后匿名呈现、默认皮肤强制及个人选择恢复、回退新建修订且不改草稿均通过（p10-s4b-real-brand-current.log）。同轮旧双端兼容用例在直接multipart上传时400：最新FileUploadRequests原生上传也要求Idempotency-Key，UI已经正确携带并通过；补齐测试直接上传的随机键，不修改生产校验。该兼容用例需独立复验，当前runner清理进行中，S4b暂不标记完成。

### S4b最终验收（2026-11-12）

- 当前制品专项31/31及打包通过（p10-s4b-current-package.log）；此前真实预览/回执/外观/偏好与HTTP组合经夹具修正后全部通过，新增治理接入另有107项组合证据。next完整verify105/105、旧端55/55；修正直接API测试头后的type-check:tests通过。
- 最新制品实际管理整页1/1通过（p10-s4b-real-brand-current.log中的brand-management.spec.ts），截图已核对；实际双前端兼容最终1/1、18.4秒通过（p10-s4b-real-compat-final.log）。两份结果对应同一当前制品，不将前两次有失败的组合标记为全部通过。实际测试副本SHA256：6AB4BA6F9D3AE14121E6DCCF4BC076AA103137DF9B30DDED6594E1F5A49B72C4。
- .e2e-runtime/1794413438403/result.json为passed=true、credentialsPersisted=false；此前失败环境也已清理；8097/5197/5198/5199无监听。S4b完成，尚未部署到用户服务。

### S4c实施顺序

1. 域名网络验证契约和部署配置：严格DNS TXT、入口IP白名单、固定IP/SNI/TLS信任与期限、有界超时/并发；先做真实本地网络夹具及安全失败单测。
2. 追加tenant迁移、挑战/检查租约/版本与短事务生命周期、启用/禁用/主域名/最后入口保护、平台权限目录；真实MySQL/HTTP及旧入口回归。
3. next租户/域名管理页：登记与开通状态区分、复制挑战、检查结果/未知状态核对、显式启用/禁用；mock与隔离实际联调。
4. 对齐规则/运行文档后进入S4d整阶段回归。控制权/DNS/TLS设计见tenant-domain-api.md；外网生产DNS/TLS未经实际部署不能宣称验收。

- S4c-1完成：DomainAddresses严格IP字面量、部署入口允许清单、JndiDomainDns有界TXT/A/AAAA与CNAME链、NetworkDomainProbe逐个核准并验证所有返回地址、进程4并发预算；DomainTlsProbe固定IP/443、实际域名SNI和HTTPS身份校验、独立TLS上下文防会话复用掩盖换证、整链有效期及最早截止、到期关闭socket。使用现有BC ASN.1读取时间，不引入java.util.Date或放宽架构门禁。
- 标准reactor DomainProbeTests/DomainDnsTests/DomainTlsProbeTests/ModularityTests/ArchitectureRulesTests，27/27、0跳过、BUILD SUCCESS，日志p10-s4c1-network.log。真实本地UDP DNS证明TXT和CNAME链；临时自签证书/真实TLS服务证明固定IP、SNI/信任/主机名与截止，未连生产公网域名。暂无持久化激活接口，因此不宣称域名已经可以从UI启用。开始S4c-2。

- S4c-2实现中：新增未交付tenant V5，为域名追加版本/挑战/证据期限/最近检查状态；tenant_domain_check继承TenantEntity，回执属于平台操作者真实租户，目标ownerTenantId另存，不新增全局豁免业务表。开始/结束短事务与DNS/TLS分离，操作ID绑定版本和原操作者；挑战轮换/停用/主域名变更使旧检查失效，启用必须匹配当前挑战的近期成功证据。新检查不自动启用域名。
- 已接入显式启用/停用/主域名API、平台最后admin入口保护及停用提升安全代次，平台权限目录仅在平台租户播种，仍另验部署操作者名单。入口解析校验证据和证书截止；测试专用域名夹具同步提供显式有效证据，不对TEST来源新增生产豁免。相关变更尚待S4c-2组合验证，不标记完成。

- S4c-2策略补验：SecurityEndpointInventoryTests、SqlStructureSafetyTests，3/3、0跳过、BUILD SUCCESS（01:35:49），日志p10-s4c2-policy.log；普通比较门禁通过，未绕过或放宽策略清单。域名管理接口/部署配置/升级边界已补入契约与运行文档。主集成会话23390仍运行，尚不标记S4c-2完成。

- S4c-2完成（01:42:53）：标准reactor组合57/57、0失败/错误/跳过、BUILD SUCCESS，日志p10-s4c2-integration.log（15分03秒，含一次专用库清理及全部迁移）。DomainLifecycleTests6、HTTP1、真实旧结构升级1；并发/重放/归属/旧快照/租约/证据到期/最后入口保护均通过。TenantAuthentication7、Directory5、治理HTTP2、BrandEntry3、实际Socket1、规则网络链路1及结构/网络专项通过。另策略门禁3/3通过。专用升级库sma_test_p10_domain_upgrade_20261112现为Tenant V5，保留数据及重复migrate=0核验通过；不得当空库重复运行该测试。没有生产公网DNS/TLS验收。
- 开始S4c-3：实现next租户目录和域名管理，复用DataTable/TableWorkspace/权限按钮及现有API客户端；验收含真实DOM未知结果、权限、版本、迟到响应及隔离后端登记/挑战/最后入口拒绝。完整业务库开通仍为S7。

- S4c-3页面已接入/tenant/tenants与/tenant/domains：最小租户选项分页、域名证据详情、TXT复制/轮换、原operationId核对、独立启停/主域名与目录状态；登记明确不宣称业务环境开通。首轮浏览器暴露品牌入口刷新期间清空列表的问题，已将短暂entry=null与真正租户切换区分，并补会话边界单测。
- 当前next完整verify116/116、类型/lint/i18n/构建及发布预算通过（p10-s4c3-next-verify-final.log）；登记丢失响应/冲突保留、挑战与检查丢失响应、只读权限、退出后的迟到响应4项浏览器通过（p10-s4c3-browser-identity.log），窄屏截图已检查。锁屏/双语深色与真实后端专项仍未验收，不标记S4c-3完成。

- S4c-3补验：首个六场景组合5项通过，英文深色键盘用例发现关闭域名详情后焦点丢失；已在列表刷新后恢复原行按钮焦点，单项复验1/1通过（p10-s4c3-browser-keyboard-final.log）。六个场景已分别通过，不把原有失败组合写为6/6。焦点修正后产品类型、测试类型、专项lint均通过（p10-s4c3-type-final.log、p10-s4c3-test-types-final.log、p10-s4c3-lint-final.log）。
- 检测到共享认证源码晚于S4c-2制品，已用当前源码重新执行40项模板网络/Token租户/Advisor/Admission/架构/策略检查并打包，BUILD SUCCESS（p10-s4c3-current-package.log）。隔离目录真实联调使用这个制品，端口8098/5207，随机库sma_next_e2e_1794420007687、正常引导账号后显式配置运维身份，仅重启runner自己的子进程；会话52284仍在迁移，未产生UI结果。

- 真实目录整页1/1通过（25.9秒，p10-s4c3-real-directory.log）：真实MySQL/打包后端、正常登录与部署运维身份、UI登记租户/状态变更/登记域名/生成TXT、未配置网络验证时按钮禁用，以及直接启用请求被后端证据门禁拒绝、状态仍PENDING。实际截图已检查；证书公网正向验收未执行。运行副本SHA256 B06B78153B925AD4F56800E8C26B2D4DD0F3F4F2959007B550B8AECE2CBAA4A5。runner正在清理自身资源，最终退出/result.json尚待确认。
- S4页面代码已稳定，正在对最终状态运行两套完整前端门禁和整套tenant模拟浏览器（p10-s4-final-next-verify.log、p10-s4-final-legacy-verify.log、p10-s4-final-browser.log），作为整阶段验收证据。之前焦点与状态文案修改的旧日志不能代替这轮最终结果。

- S4c完成：真实runner退出0，.e2e-runtime/1794420007687/result.json为passed=true、credentialsPersisted=false；8098/5207/5208无监听，任务自有数据库/账号已清理。域名最后平台入口保护由真实MySQL生命周期用例证明，本次浏览器验证的是未验证启用拒绝，不把两者混称。开始S4d；沿用当前已通过的后端40项结构/安全/网络和此前57项集成证据，最终双前端/完整tenant浏览器正在收尾。
- S4d最新制品真实品牌复验已启动，brand-management.spec.ts＋tenant-entry.spec.ts，后端8098/next5207/legacy5208，日志p10-s4-final-real-brand.log；不重复目录专项，不使用用户服务。此前S4b实际品牌通过仍保留为独立证据，本轮等待最新制品结果。

- S4d最终前端门禁通过：next verify116/116、旧版标准verify55/55，均退出0，语言/类型/lint/构建及新版发布预算通过（p10-s4-final-next-verify.log、p10-s4-final-legacy-verify.log）；128条旧版哈希一致。整套tenant模拟浏览器30通过、10个旧版不适用跳过、无失败，1.7分钟、退出0（p10-s4-final-browser.log）。当前截图保存在test-results-tenant，含新目录明暗/窄屏和两端三布局。最新制品真实品牌双用例仍运行，会话1896、专用库sma_next_e2e_1794420915179，等待其结果与清理。

### S4最终验收

- 最新制品实际品牌整页与双前端兼容2/2通过，48.7秒（p10-s4-final-real-brand.log）；正常上传、预览、保存、发布、默认皮肤/个人策略、回退和双端匿名已发布PNG读取均通过。结合实际目录1/1、后端57项生命周期/迁移/入口及40项当前制品契约、双前端116/55、完整mock浏览器30通过/10旧版不适用跳过，S4完成。
- 会话1896退出0；.e2e-runtime/1794420915179/result.json为passed=true、credentialsPersisted=false；8098/5207/5208无监听，自有数据库/账号/临时前端及后端已清理。当前未部署用户服务，未验收生产公网DNS/TLS或完整分库。README、前端规则/路由清单说明及域名契约/运行说明已同步，39个文档链接检查及改动空白检查通过。
- 开始S5方案细化。共享配置/业务MySQL/Redis/数据源SQL管理/容量与全量发布S5～S10全部保留，整体目标仍未完成。

### S5顺序与方案

- 已创建[共享配置实施设计](tenant-shared-configuration.md)。细分S5a契约/纯规则 → S5b平台持久化/授权/发布 → S5c参数/旧数据/分代缓存 → S5d字典/树/历史/迁移 → S5e双前端适配与平台管理 → S5f回归。每项独立通过再推进；当前S5a开始，S5b～S5f未实施。
- 关键边界：保留现有租户显式值；代码目录声明类型/上限/公开投影，平台编辑受支持共享值；Config声明授权SPI由Tenant实现以避免模块环；首版不放宽PLATFORM DATA缓存，而以平台发布修订＋租户修订＋语言分代。当前仅读取了原参数/字典/缓存实现，尚未改变它们的运行行为或迁移脚本。

- S5a完成：新增ConfigurationCatalog、参数/字典定义、有效快照/来源/修订和EffectiveConfigurationQuery扩展契约；原ParameterQuery/DictionaryQuery签名未修改。SharedConfigurationRules为无数据库/网络的纯验证与合成，覆盖显式null/空值、锁定策略、JSON整体替换/结构预算、数值边界、品牌键归属、固定公开投影、树/隐藏/停用/历史与发布代码不可删除/换父。未接入现有运行读写。
- 首轮编译因Jackson3没有isContainerNode失败，改为isArray/isObject；标准reactor SharedConfigurationRulesTests12＋Architecture17＋I18n3＋Modularity2，共34/34、0跳过、BUILD SUCCESS（02:39:54，p10-s5a-rules-recheck.log）。失败日志p10-s5a-rules.log保留。S5b开始，平台持久化/授权/发布尚未交付。

- S5b按内部依赖推进：先校验代码目录/模块归属和授权SPI，再追加共享头/不可变修订迁移与短事务服务，最后接平台HTTP/权限及真实库升级/并发/拒绝专项。当前新增ConfigurationCatalogRegistry和ConfigurationPlatformAccess宿主SPI，Tenant适配现有部署运维名单，Config不反向依赖Tenant内部。定义的值/文案指纹与类型/约束契约哈希分开；参数key按原大小写不敏感语义消歧，安全分类取声明元数据。尚无共享持久化或HTTP新入口。
- 注册/授权及前阶段纯规则＋架构/模块/语言组合验证已启动，日志p10-s5b1-registry.log，未出结果不标记S5b通过。

- S5b-1完成：代码目录注册/真实模块归属、大小写别名消歧、稳定指纹/契约哈希、容量、唯一宿主授权和平台身份＋动作权限组合40/40、0跳过、BUILD SUCCESS（02:50:14，p10-s5b1-registry.log）。默认值/文案改变只改变来源指纹，不自动改写已有发布；类型/上限/策略改变会改变契约哈希。参数值/默认值的toString已脱敏。下一步S5b-2追加Config共享头/修订表及服务，然后验证真实迁移、并发和权限。

- S5b-2实现中：追加尚未交付Config V6，只新增平台共享头/不可变修订两表；明确列入平台元数据架构白名单，未放宽租户私有表。目录列表只读有界标签摘要；草稿与发布指针/历史字节计数在同平台事务，使用当前读防旧快照覆盖。宿主目录初始化用保留既有ID的原子登记，不覆盖用户草稿/已发布值。当前仍没有新HTTP入口，也未切换旧参数/字典查询。
- 已新建专用空升级库sma_test_p10_config_upgrade_20261112，只授予现有测试账号此库权限。真实服务/并发/回滚/重入与V5→V6保留数据升级组合验证已启动：主测试复用本任务sma_test_p10_s3b_final_20261111（会清空），输出target/p10-s5b2/target，日志p10-s5b2-store.log，会话待观察；不使用默认库。

- S5b-2保留数据升级1/1、0跳过通过（03:07:48，p10-s5b2-store.log）：专用库从Config V5追加V6，原租户参数值/secret/sort/brandArchived均不变，共享表无凭空客户数据，重复migrate=0。该升级库现为V6，不能再次当空库重跑此用例。主体服务集成会话57672仍在专用主库清理/启动阶段；当前源码编译、结构/平台表归属、SQL结构、语言和纯规则已通过，尚不能标记服务验收完成。

- S5b-2首轮52项中51通过、1个宿主重入用例错误（p10-s5b2-store.log）。同库发布原子性/回滚、并发发布一胜、旧快照、历史容量、字典代码/父级与大草稿拒绝、两个宿主并行初始化均通过；失败用例直接在Spring web测试线程执行宿主初始化，被现有非HTTP/非认证主体限制拒绝。夹具改为独立宿主线程执行正向重入，负向HTTP/认证拒绝断言保留；没有改生产授权或初始化规则。只复验该方法，日志p10-s5b2-host-recheck.log，不重跑已经升级到V6的专用升级库。S5b-2尚未完成，暂不开始HTTP接入。

- S5b-2读取端补充：共享修订读取拒绝未知schemaVersion、JSON null/格式损坏和无效标签，并去掉包含原JSON的解析异常原因；定义存在但指针不可用时明确拒绝，不混同未知key。新增SharedConfigurationReadTests与纯规则专项（无数据库）。首轮编译被并行file/StorageProfileRules.java的暂态语法错误阻断（p10-s5b2-read-schema.log）；复查03:27:01文件已修正，未覆盖对方改动，正在重新运行p10-s5b2-read-schema-recheck.log。宿主重入复验49386仍使用其已编译版本运行，两项结果分开记录。

- S5b-2完成：宿主线程修正后的单项真实复验1/1、BUILD SUCCESS（03:28:58，p10-s5b2-host-recheck.log）；结合首轮其余51项通过，8个持久化用例与保留数据升级均已有通过证据。读取端新增3项与12项纯规则15/15、BUILD SUCCESS（03:29:24，p10-s5b2-read-schema-recheck.log），明确拒绝未知格式/损坏修订/契约不符且不泄漏原JSON。两种日志分开，不把首轮失败组合写成一次全通过。Config V6现在视为已验收，后续仅追加迁移。
- S5b-3开始：平台共享配置HTTP、查看/编辑/发布权限和请求治理接入；普通租户有效参数/字典尚未切换，S5整体仍未完成。

- S5b-3已接入共享目录/详情、两类草稿、显式发布和历史7个HTTP映射；平台租户菜单与view/edit/publish独立权限，仍要求部署运维身份，LOCAL初次执行和重放均调用原动作授权。已审查这些入口仅同平台数据库写入、无网络/跨资源/独立提交，按规则更新策略基线；比较前后只新增本次SharedConfigurationController的7条记录，未移除并行file等已登记记录。
- 策略审查及Advisor/Admission/架构/模块/语言29/29通过（03:41:33，p10-s5b3-policy-review.log）。正在用普通基线比较（不带review开关）与共享HTTP/原参数字典偏好和请求治理组合联测，日志p10-s5b3-http.log；仍为任务专用主库与独立target输出，结果未出。

- S5b-3普通策略门禁首轮在并行新StorageSceneController的8个映射上失败，SharedConfigurationController七条已正确保留；随后复查基线03:46:35已补齐这些文件场景记录，未修改或删除其实现。单独复跑SecurityEndpointInventoryTests（无review开关，p10-s5b3-policy-strict.log），主HTTP组合19516仍继续运行，不重启专用库进程。

- S5b-3普通策略单项复验1/1、BUILD SUCCESS（03:52:53，p10-s5b3-policy-strict.log），未使用review开关，当前共享/并行文件映射均匹配。主组合已通过DictionaryTests2、ParameterTests4和PreferenceTests2，正在执行请求治理及共享HTTP；首轮策略差异已有单独通过证据，仍等待其余用例实际结果。

- S5b-3完成：p10-s5b3-http.log原组合46项中45通过，唯一失败为并行StorageScene接口基线更新时间差；普通策略单独复验1/1通过（p10-s5b3-policy-strict.log，无review开关）。共享HTTP2/2、原请求治理4/4、参数4/4、字典2/2、偏好2/2及目录/读取/架构均通过。不把原失败组合写为一次全绿。S5b完成；平台管理前端仍待S5e，不能作为完整功能部署。

### S5c参数细分

1. S5c-1：追加参数定义绑定、显式覆盖/继承标记和租户单调版本；实现当前读、覆盖/恢复继承、保留原行/ID/翻译的幂等迁移。先使用隔离定义验证真实MySQL并发/回滚/旧快照/空值/跨租户，不切换现有运行入口。
2. S5c-2：注册纯元数据的内置目录，宿主按TenantWorkQuery分页迁移；原ParameterQuery接有效读取，普通缓存按定义/共享/租户代次隔离，安全参数当前读。初始化不复制共享值；旧自定义参数/secret保持原行为。
3. S5c-3：租户管理有效列表与带双版本的覆盖/恢复继承HTTP，原行编辑入口防绕过；验证旧参数/认证/品牌消费者及请求治理。前端整体适配在S5e。

当前S5c-1开始，S5c-2/3及S5d～S10未完成。逐项验证并记账后继续。

- S5c-1已追加尚待验收的Config V7及DefinedParameterService，保留原参数行/ID/翻译，显式null与继承分离，双版本冲突、租户guard当前读和同事务事件/失效已接入。首轮真实库/结构组合运行中（p10-s5c1-layers.log）；专用V6→V7升级库sma_test_p10_parameter_upgrade_20261112及独立输出另行验证（p10-s5c1-upgrade.log）。两库互不重叠，未操作用户服务。
- 首轮启动后代码复查将guard查询收敛到id主键并保留tenant_id校验，参数查询使用tenant＋active_key唯一索引，避免FOR SHARE退化为跨租户扫描锁；该变更尚未编入首轮主体测试，完成后必须专项复验，不能把首轮结果作为最终源码证据。

- Config V6→V7专用旧结构升级1/1、BUILD SUCCESS（04:18:26，p10-s5c1-upgrade.log）：保留tenant/原ID/value/secret/sort/翻译，新增覆盖标记默认为true，版本初始0，显式null可持久化，重复migrate=0且已推进版本不回退。sma_test_p10_parameter_upgrade_20261112现为Config V7，不可重复运行要求空库的升级测试；V7不再原地修改。
- 参数实际服务组合仍在主专用库迁移阶段。另在保留的V7库、独立输出运行ParameterLayerLockMysqlTests：直接装配真实宿主MyBatis租户拦截器和当前Mapper，验证跨租户不互锁/越权写0行及旧快照当前读；不清库，不替代完整服务专项。

- S5c-1锁查询补验21/21、BUILD SUCCESS（p10-s5c1-locks.log）：当前Mapper＋真实宿主拦截器在专用V7库证明A租户持读锁时B租户可完成写入，越租户更新影响0行，旧事务快照与MyBatis缓存不能遮蔽新提交值；另架构17项、SQL结构2项通过。完整服务7项仍在主专用库迁移/启动，未标记S5c-1完成。

### S5c-1验收完成

- 主体组合51/51、0失败/错误/跳过，BUILD SUCCESS（p10-s5c1-layers.log）：DefinedParameterTests7项验证新租户无默认复制、显式null/空/继承、权限/锁定、并发单胜、回滚、旧快照、旧行/翻译保全、迁移重入及平台发布不覆盖租户值；原ParameterTests4、共享读/规则15、结构/语言/模块25均通过。
- 主体使用编译时的旧查询SQL；后续索引定位修正由p10-s5c1-locks.log的真实Mapper/实际租户拦截器2项＋结构19项（21/21）独立通过。Config V6→V7旧结构升级另1/1通过。三份证据范围分别记录，不把未编入首轮的修正混称为首轮已验证。
- README/专项规则/设计已同步，相关文档链接和差异空白检查通过。S5c-1完成；S5c-2开始，先实现不可变发布引用和分代缓存读取，再注册内置目录、启动迁移及原查询接线。旧查询/管理入口尚未切换，S5c整体及S5d～S10仍未完成。

- S5c-2实现中：普通读取先捕获租户版本＋轻量平台发布指针，命中缓存不读草稿/修订正文；未命中只加载捕获的不可变修订。若间隙出现租户写入，返回当前一致快照并拒绝放入旧地址；现有事务仍由ManagedCache绕过，安全定义按元数据直接当前读（包括大小写别名）。增加实际命中/回滚/null/安全旁路/固定修订与并发代次用例，组合运行于p10-s5c2-reader.log。
- 该组合编译后继续同一S5c-2的内置接线：13个内置定义改为纯静态元数据提供，-190共享登记→-180按TenantWorkQuery迁移→原品牌迁移/普通初始化；初始化仅保留分组，不再复制已登记默认值。原ParameterQuery签名保留，旧未登记private/secret走原路径；已登记键及绑定行禁止通过旧无版本save/delete绕过。reset-builtins作为显式全量恢复继承命令保留，原LOCAL幂等治理继续约束，不等同普通字段编辑。管理列表及新HTTP仍待S5c-3，前端在S5e，禁止中间状态部署。
- 这些运行接线和相应消费者夹具修改未编入reader首轮；必须在其完成后用当前源码另行做真实启动/旧参数/认证/品牌与偏好缓存组合验收。

- S5c-2内置接线静态门禁28/28通过、BUILD SUCCESS（04:36:04，p10-s5c2-wiring-compile.log），包含架构17、品牌归属1、目录5、共享读取3、模块2；它不证明Spring真实启动与迁移编排。已新增实际ApplicationReady前旧数据夹具，验证PROVISIONING租户同样回填且旧值等于默认也保持显式覆盖。
- 当前源码组合在新建专用sma_test_p10_s5c2_20261112运行（p10-s5c2-wiring.log），与reader首轮的sma_test_p10_s3b_final_20261111隔离；各自输出目录，不共用清库。覆盖启动/参数/本地与none缓存/偏好/认证并发/匿名品牌及旧品牌迁移，尚待结果。

- S5c-2缓存读取专项36/36、BUILD SUCCESS（04:43:14，p10-s5c2-reader.log）：参数层10项（含真实命中、null、回滚、安全大小写别名旁路、固定旧发布正文、间隙租户写与跨租户frame拒绝），原参数事务缓存2、共享读取3、架构/缓存边界/模块21均通过。该制品在内置目录接线前编译，不替代正在运行的当前接线组合p10-s5c2-wiring.log。

- 接线组合运行中复查旧品牌测试夹具发现：seed()把验证码也写成STRING，而真实旧内置定义为BOOLEAN；新参数迁移应拒绝这种类型冲突。已仅修正LegacyBrandImportTests夹具的captcha类型，不放宽生产迁移校验。当前运行已编译旧夹具，该方法结果须明确区分并用修正夹具专项复验。

- 当前组合已通过SessionConcurrencyTests5/5，已确认内置目录注册没有启动Bean循环且原自动锁屏参数消费者仍通过并发测试。后续启动/缓存/品牌仍待完成。
- 补齐ConfigCacheCatalog失效来源声明（覆盖、恢复继承、定义迁移、平台发布修订）；不改变策略上限/PLATFORM DATA禁令。该纯元数据补充晚于主组合编译，另运行CacheContractTests/CacheRegistrationOwnershipTests/BrandParameterOwnershipTests（p10-s5c2-metadata.log）检查。

- S5c-2当前接线组合结束：67项中66通过、1个旧品牌夹具类型错误（p10-s5c2-wiring.log）。实际启动迁移2、参数层10、会话并发5、本地/none基础缓存各5、参数4、偏好2、事务缓存2、请求治理4、匿名品牌3和架构/策略/模块均通过；唯一错误正是先前发现的captcha夹具STRING/BOOLEAN不一致。修正夹具的单方法真实复验正在sma_test_p10_s5c2_brand_20261112运行（p10-s5c2-brand-recheck.log），仍不标记S5c-2完成。
- 新增失效来源说明后的契约/真实模块注册/品牌键归属8/8、BUILD SUCCESS（04:52:56，p10-s5c2-metadata.log）；只是元数据/门禁补验，不替代主组合的数据库范围。原失败组合保持原结果，最终以专项补验闭环。

### S5c-2验收完成

- 修正真实旧类型后的品牌迁移方法1/1、BUILD SUCCESS（04:59:17，p10-s5c2-brand-recheck.log），captcha先绑定参数层，再由品牌导入仅归档品牌键；跨portal品牌发布及后续回退通过。结合p10-s5c2-wiring.log中其余66项通过，内置目录、实际启动迁移、原ParameterQuery、安全当前读、普通分代缓存及原消费者已闭环；原67项组合仍记录为66通过/1夹具错误，不改写其结果。
- 独立缓存读取36/36、静态目录/模块28/28、缓存来源元数据8/8及S5c-1旧结构/锁查询证据保留。Config V7不变，未部署用户服务。S5c-2完成，开始S5c-3租户有效参数目录/详情、双版本覆盖/恢复继承HTTP及原写入口拒绝专项；设计见共享配置文档末节。

- S5c-3已实现/api/config/effective-parameters的分页/详情/覆盖/恢复继承4个映射。只同平台库读写，沿用config.parameters LOCAL治理与view/update动作权限；重放重验原权限，双Long版本必填，覆盖的value字段必出现，显式null仍受定义约束。分页只读取本页最多50项正文。
- 新增Config V8区分旧显示覆盖与新值覆盖，旧名称/翻译/排序保留，新值覆盖仍显示平台发布名称/翻译。V8尚未验收；在保留的专用V7升级库推进V8，不清库不修改旧脚本。代码目录/校验/策略审查组合运行p10-s5c3-policy.log；已审查四个HTTP映射无网络/跨资源或独立提交，策略基线采用显式review标志，之后须普通基线复验。

- S5c-3迁移/策略审查/架构组合26/26、BUILD SUCCESS（05:07:40，p10-s5c3-policy.log）。保留库从Config V7仅追加V8，旧name/value/secret/翻译、value_overridden与租户版本8均不变，presentation_overridden默认true，重复migrate=0。该专用库现在为V8，不能再运行要求V7前置的该升级用例；后续仅追加迁移。
- 策略基线前后对比只增加DefinedParameterController的4条映射，没有删除其他任务记录。普通无review策略门禁及当前V8实际Mapper检查待补；主HTTP/参数组合正在新专用sma_test_p10_s5c3_20261112运行（p10-s5c3-http.log），尚未出结果。

- S5c-3普通策略/Mapper/Advisor组合6项中5通过，唯一失败是并行新增的12个文件上传任务映射在本轮复制基线后才落入基线（文件控制器05:07:02，基线05:10:17）。P10四个有效参数映射一直匹配；V8实际Mapper隔离/当前读2项与Advisor3项已通过。保留文件任务实现和基线，不回写其策略；仅用当前资源重新运行普通SecurityEndpointInventoryTests（无review开关）。

- 普通策略门禁单项复验1/1、BUILD SUCCESS（05:11:55，p10-s5c3-strict-recheck.log），没有review开关；本次四个参数映射和并行文件新增映射均匹配。V8实际Mapper/Advisor先前5项通过保留，首次策略组合失败照实记录。策略差异检查确认P10恰好新增4项、无既有项删除，文档链接/空白检查通过。主HTTP组合26349仍在专用库正常迁移，未标记S5c-3完成。

### S5c-3与参数子阶段最终验收

- 最终真实HTTP/参数组合31/31、0失败/错误/跳过、BUILD SUCCESS（05:16:23，p10-s5c3-http.log）：新增HTTP4项验证view/update权限、目录分页、显示翻译、null/缺字段区分、必填双版本、共享发布冲突、租户覆盖/恢复、原私有路由拒绝、LOCAL重放与权限撤销、回执查询及跨租户隔离；实际启动2、参数层10、原参数4、原请求治理4、共享HTTP2、语言3、模块2全部通过。
- Config V7→V8保留数据升级、架构/审查组合26/26通过；V8实际Mapper与Advisor5项通过，普通策略门禁因并行基线更新时间差首轮失败后单项1/1通过。结果按各日志分别记录，不把失败首轮写成全绿。当前V8旧名称/翻译保留，新值覆盖不冻结共享名称/翻译；大整数版本及ID按字符串传输。
- README、共享配置设计、运行约束与专项规则同步；P10四条新增策略与其他并行上传任务策略共存，无既有策略删除。相关文档链接和改动空白检查通过。没有部署或重启用户服务，没有把API/MySQL验证当成双前端或生产域名验收。
- S5c完成。接下来依序实施S5d字典继承/覆盖/历史代码/迁移/预热，S5e双前端适配及平台共享管理，S5f整阶段回归。S6～S10的MySQL业务路由、开通/迁移/Outbox、Redis多资源、SQL源筛选/定向重置与性能/完整发布全部保留；P10整体目标仍active，不标记完成。
- 本轮全部测试会话已退出：主服务36044、升级67788、锁58965、缓存18749、接线静态99792、接线64781、品牌复验4373、缓存元数据50282、S5c-3审查60974、普通门禁3577/56169、最终HTTP26349。专用保留参数升级库sma_test_p10_parameter_upgrade_20261112现为V8，不能重跑要求V6/V7初始状态的升级方法。

### S5d实施顺序

1. S5d-1：最小代码归属登记（Config平台命名空间元数据）、共享发布/启动补登记、冲突/回滚/并发与追加迁移；先验收后再接租户项目存储。
2. S5d-2：TenantEntity类型/项目层、单调字典版本、三模式合成与旧树/翻译迁移；保留原行和ID，私有稳定代码停用留历史，真实MySQL验证。
3. S5d-3：内置静态目录、按目录的启动迁移、DictionaryQuery/EffectiveConfigurationQuery、按语言和双版本缓存、预热新版本游标；验证原字典/消费者/缓存/预热。
4. S5d-4：租户有效字典管理HTTP、共享覆盖/隐藏/恢复与私有项管理，双版本/权限/LOCAL未知结果；字典整体回归并同步二开/运行规则，再进入S5e。

当前S5d-1开始。共享代码新增与租户私有重名的长期冲突、旧类型缺项隐藏及显示保全已补入共享配置设计。涉及路径为config内部持久化/服务/追加迁移、平台持久化精确白名单及专项测试；未更改业务表租户隔离，未开始前端或业务多数据源。

- S5d-1已追加尚未验收的Config V9：config_shared_head只增加dictionary_code_count，新增config_dictionary_code仅保存head、精确代码SHA-256和SHARED/PRIVATE归属；无租户、原文、标签或父级。共享发布与启动补登记在原发布事务内声明代码；租户私有声明为MANDATORY事务且锁定共享头后写入，回滚一并撤销。代码校验拒绝控制字符和无法UTF-8编码的孤立代理，避免替换字符别名。新增精确平台持久化白名单项、双语冲突/容量错误码。
- 验证组合使用新专用sma_test_p10_s5d1_20261112及保留V8库（推进到V9），日志p10-s5d1.log，覆盖互换冲突、同名多租户、大小写精确、拒绝发布无残留、调用方回滚、锁定/未登记/非法代码、三方并发、容量、旧发布补登记和V8→V9保留数据；结果未出不标记完成。

### S5d-1验收完成

- 组合56/56、0失败/错误/跳过、BUILD SUCCESS（p10-s5d1.log）：DictionaryCodeRegistryTests5项验证共享/私有互换拒绝、多租户同名私有代码只登记一次、大小写精确、冲突发布不移动指针且无残留、调用方回滚撤销声明、MANDATORY事务、锁定/未登记/控制字符/孤立代理拒绝、两租户与发布者三方并发、容量只拒绝新名称、旧发布启动补登记幂等且表中无租户/原文/标签/父级列。保留库V8→V9 1/1，原字典2、共享存储8、读取3、规则12、平台持久化白名单/架构/语言/SQL结构/模块均通过。
- 专用保留升级库现为Config V9，不可再运行要求V8前置的升级方法。S5d-1完成；开始S5d-2，已编写V10租户字典层（类型层/覆盖与私有项、NO PAD二进制代码、单调dictionary_revision）、DefinedDictionaryService和真实MySQL专项，尚未运行。

- S5d-2首轮p10-s5d2.log未进入测试：并行file/internal/inspection的InspectionProtocol.java与TextContentInspection.java正在编辑，缺少JsonFactory符号导致主编译失败。未修改他人文件；待其源码稳定后用同一专用库复跑，本轮不计为S5d-2验证证据。
- 复查并行文件模块已在05:42:28补齐JsonFactory导入（晚于本任务05:41:52编译）；未改其源码，以同一专用库/输出复跑S5d-2，日志p10-s5d2-rerun.log。

### S5d-2验收（按开发调度推进）

- 自2026-11-12起按[开发调度](development-schedule.md)执行：本线位于第1组“租户S5d～S5f”，完成后才可配合X3进入X1（阻塞S6）。每个子项通过后同时更新调度表与本进度。
- p10-s5d2-rerun.log：47项中46通过。DefinedDictionaryTests6（新租户继承、覆盖/隐藏/恢复、私有代码历史与回滚不留归属、LOCKED/TENANT_ONLY、平台停用不可被租户重启且发布使旧版本失效、旧树ID/翻译/排序/缺项隐藏保全及冲突无部分状态）、V9→V10保留升级1、代码归属5、原字典2、参数层10与V8/V10锁查询2、SQL结构2、模块2通过。
- 唯一失败为ArchitectureRulesTests.generalCodingRules：并行file模块ContentInspectionWorker使用System.out；当前提交已由其负责人显式豁免，不属本线改动，未修改其代码。S5d-3组合包含ArchitectureRulesTests作为当前源码复验。保留升级库现为Config V10，不能重跑需V9前置的升级用例。S5d-2完成。
- S5d-3已实现：13个内置字典改为纯静态EXTENDABLE目录（不再复制到新租户）、-175逐租户迁移、DictionaryQuery/EffectiveConfigurationQuery有效读取、定义指纹＋平台发布ID＋租户版本＋语言缓存键、原私有接口拒绝已登记键、预热loader v2先枚举已登记定义再接旧私有类型（含对应P08预热测试计数调整）。旧管理页面暂不显示内置字典，有效字典管理HTTP在S5d-4、前端在S5e，期间不可部署。

- S5d-4已实现/api/config/effective-dictionaries：GET目录（≤50，先筛定义元数据）/详情（有效项、来源、可提交性与租户显式层），PUT item-overrides、POST item-overrides/restore、PUT private-items、PUT enabled四个LOCAL写入；均同平台库、无网络/跨库/独立提交，需config:dictionary:view/update与平台/租户双版本，精确代码只走JSON正文，LOCAL重放重验原动作权限。旧/api/config/dictionaries对已登记键创建/修改/读取明细拒绝。
- 策略审查p10-s5d4-policy.log：23/23 BUILD SUCCESS（review开关）。注意：该运行07:15编译时恰好包含X3负责人07:14～07:15正在修改的SecurityEndpointInventoryTests/EndpointBudgetCatalog，因而07:18写出的security-endpoint-policies.txt及docs/security-endpoint-inventory.md同时采用X3新增的预算scope列与BUDGET行（文件约710行格式变化）。本线只审查了6条DefinedDictionaryController映射；X3预算行与格式由当前代码确定性生成，但不代表X3审查结论，须X3负责人复核或重新生成，未回滚以免覆盖其后续工作。普通无review门禁待X3稳定后复验。

### S5d-3验收完成

- 新专用库sma_test_p10_s5d3_20261112组合76/76、0失败/错误/跳过、BUILD SUCCESS（p10-s5d3.log）：DefinedDictionaryStartupTests2（真实ApplicationReady前旧数据、未开通租户迁移、旧显示/私有代码/停用类型保全、DictionaryQuery与EffectiveConfigurationQuery、版本缓存命中、事务回滚不污染、预热v2覆盖无租户行的共享定义）、DefinedDictionaryTests6、原字典2、预热单元4（含新增已登记定义先行枚举）、P08 CacheWarmupExecutionTests7（共享定义纳入计数）、本地/none基础缓存各5、参数启动2、共享存储8、缓存契约/注册/预热契约、平台持久化白名单、普通策略门禁、架构17（当前提交已闭环S5d-2唯一并行失败）、语言3、模块2。
- 该运行在S5d-4的State.layer补充前编译；S5d-4 HTTP组合与S5f整阶段回归覆盖当前源码。S5d-3完成，S5d-4后端HTTP在sma_test_p10_s5d4_20261112运行（p10-s5d4-http.log）。
- S5e已开始：next新增EffectiveParametersPanel（共享与内置参数：来源/双版本/覆盖/显式null/恢复继承）、EffectiveDictionariesPanel（树形有效项、覆盖/隐藏/恢复、私有项、启停，代码只在JSON正文）、/config/shared平台页（草稿/已发布代码稳定/发布绑定草稿与指针/历史快照）及typed layers.ts；未知结果只按原键核对。next类型检查、专项lint、i18n键/占位符一致、layers单元3/3通过（backend/target/p10-s5e/*.log）。旧版frontend新增只读EffectiveConfigView页签说明覆盖在新版完成，类型检查通过；旧版基线哈希、完整verify及浏览器验收待执行。
- S5d-4后端HTTP（p10-s5d4-http.log）：DefinedDictionaryHttpTests3/3（view/update权限、目录≤50与明细不含平台草稿、覆盖/恢复/私有非ASCII代码/启停、LOCAL重放最小回执与权限撤销、陈旧版本、共享代码冲突、锁定拒绝、旧私有接口拒绝已登记键）、DefinedParameterHttpTests4、原字典2通过；组合其余用例待结束。
- S5e浏览器首轮（任务专用5281，不复用5174开发服务）：新增config-layers 4项中3项通过（共享参数来源/显式null/恢复继承与固定双版本、字典覆盖/非ASCII私有代码只在正文、平台草稿与发布双版本）。未知结果用例暴露真实缺陷：弹窗保留输入但页面级“查询”按钮被模态遮挡；已在参数与字典编辑弹窗内显示错误及原键查询入口，复验运行中。

### S5d-4验收完成

- 新专用库`sma_test_p10_s5d4_20261112`的后端HTTP组合`p10-s5d4-http.log`为15/15、0失败/错误/跳过、BUILD SUCCESS，覆盖有效字典HTTP3、有效参数HTTP4、旧字典2、请求治理4、共享配置HTTP2。
- 策略审查`p10-s5d4-policy.log`为23/23；在并行X3预算目录更新稳定后，普通无review门禁`p10-s5d4-strict.log`为6/6、BUILD SUCCESS（预算目录5、端点策略1）。两次运行的用途分开记录。S5d-4完成；S5e前端仍需浏览器及双工程最终验收，S5f未开始。

- 后续审查发现S5d迁移对`builtin=false`且与已发布共享代码同名、父级恰好相同的旧私有项仍按覆盖合并，违反设计的冲突停止规则。已补拒绝判断、保留数据回滚用例和旧字典删除前归属检查；在专项复验前暂撤销S5d-4完成状态。S5e前端本地验证：新版完整verify 179/179、旧版标准verify 55/55，配置浏览器6/6、联合搜索/工作流回归合计18/18（测试与修复见各日志）；隔离真实双前端联调正在使用当前已构建制品运行，结果另记，不把它当作本次后端补丁验证。

### S5d最终复验完成

- `sma_test_p10_s5d4_20261112`专用库、根reactor独立输出`target/p10-s5d-correction/target`的`s5d-correction.log`为31/31、0失败/错误/跳过、BUILD SUCCESS：DefinedDictionaryHttpTests3、StartupTests2、DefinedDictionaryTests6（含同父级非builtin撞码拒绝且不留层）、原DictionaryTests2、ArchitectureRulesTests17、普通SecurityEndpointInventoryTests1。验证覆盖了上条修复后的源码；原V8→V9/V9→V10保留库升级证据仍以先前各只运行一次的专项日志为准。S5d-4和S5d完成。
- S5e最终本地前端复跑：next`next-verify-final2.log`为180/180单元、类型/lint/i18n/构建/发布检查通过；旧端`legacy-verify-final.log`为55/55且构建/i18n通过；`browser-final.log`20/20通过（配置6、统一搜索4、工作流10）。后续清空无结果字典详情和身份切换草稿内存两处小修仍需当前源码复验。隔离真实联调首轮`real-e2e.log`因runner旧SQL更新0行导致验证码仍启用、测试在登录页超时；测试库/账号/进程已清理。runner已改为任务自有租户的显式覆盖并重启自己的后端，待当前JAR复跑，不把首轮写作通过。

### S5e 双前端验收完成

- 当前源码的next `next-verify-label.log`：180/180单元、类型、lint、i18n、构建、发布检查通过；旧端 `legacy-verify-label.log`：55/55单元、lint、构建、i18n通过。两处页面修正（空字典详情清理、身份切换草稿内存清理）已由 `browser-config-current.log` 6/6 和当时源码浏览器回归 `browser-final.log` 20/20覆盖；新增显示占位只避免页面初始化期间对未就绪字段拼接缺失语言键。
- 隔离真实双前端 `real-e2e-contract.log` 1/1通过：任务专用8093/5281/5188和随机MySQL库，入口验证码显式租户覆盖核对后重启本任务子进程，next覆盖/恢复继承与平台页、旧端有效来源均通过；新增响应断言确认共享详情的类型/模式和旧端参数来源字段存在。`frontend-next/.e2e-runtime/1794445901578/result.json`记录通过、账号及库清理、凭据未保留。Vite初始化仍打印两类短暂缺失标签警告，显示层已用占位处理；没有因此声称生产部署或X5验收。首轮失败日志保留原样。
- S5e完成，开始S5f整阶段回归、旧库/启动和部署/二开说明。S6仍受P11 X1账本归属门禁阻塞，不能把S5前端验收当作分库路由或SQL数据源目录交付。

### S5f 整阶段回归进行中

- 已新建专用可清空库`sma_test_p10_s5f_20261112`，根reactor标准`verify`写入`backend/target/p10-s5f/verify.log`。首轮在后端约135个测试类后停止，**不是通过**：`DeploymentEvidenceAssemblyTests`执行的是09:25编译的旧类，源码09:44由并行P08工作修正；`SharedConfigurationTests`发现HTTP用例先发布同一`config.s5.draft`，已将HTTP夹具改用独立`config.s5.http`。后续连接数达到291/300，多个应用上下文因共享号段连接不可用连锁失败；仅停止本次S5f的Surefire子进程，连接数回落至11。原失败日志不改写，也不将连接耗尽误判为业务拒绝。
- 当前源码在同一专用库以独立输出目录`target/p10-s5f-recheck/target`复验S5相关22类与部署证据类，测试上下文缓存限定8，`backend/target/p10-s5f/recheck.log`为92项中91通过、1项旧结构升级因缺一次性前置而跳过、0失败/错误、BUILD SUCCESS。共享HTTP2与服务8同轮通过，部署证据1通过，现有品牌迁移/入口、参数/字典、缓存预热、结构/权限/语言均通过；连接数观测81，未再次触顶。V5→V10逐版保留数据升级已在S5b～S5d的专用库各验一次，不借本轮跳过项重跑已升级旧库。二开接入和部署说明已补充至`docs/agent-rules/secondary-development.md`、`docs/developer-manual.md`及`docs/tenant-platform-operations.md`。完整标准verify正以`-Dspring.test.context.cache.maxSize=8`、独立输出`target/p10-s5f-full/target`运行，未结束前S5f、S5及X1均不标完成。

### S5f 与 S5 本地验收完成

- 根reactor完整`verify`在专用库`sma_test_p10_s5f_20261112`、独立输出`target/p10-s5f-full/target`、`-Dspring.test.context.cache.maxSize=8`下通过，日志`backend/target/p10-s5f/full-capped.log`，2026-11-12 11:31:52结束，7个reactor模块均SUCCESS，backend 1124项、0失败/错误、57跳过，`BUILD SUCCESS`，总耗时1小时21分。共享配置HTTP2、服务、字典/参数、缓存与Modulith结构均包含在本轮；两个先前冲突用例同轮通过。跳过项包含需独立旧库形状或外部Redis条件的专项，不把本轮跳过记为通过；V5→V10保留数据升级与前端双端证据仍以各子项的独立记录为准。
- `git diff --check`针对本线进度、调度、README及运行说明返回0；换行规范提示不影响检查。S5a～S5f本地完成，允许按P11先启动X1账本归属，S6仍须等X1通过。真实外部DNS/TLS、外部Redis、业务双MySQL路由及S6～S10、X5联合验收尚未完成，不能声明P10整体交付或生产部署。

### P11 X1前置进度

- X1a本地完成：`BusinessLedger`公开会话和宿主provider边界新增后，缺业务资源绑定即拒绝，旧平台账本不承接业务执行。新边界/架构/模块22项及独立`sma_test_p11_x1a_20261112`库的平台启动、请求账本、缓存代次、新边界、模块24项均0失败/跳过；日志`backend/target/p11-x1a.log`和`backend/target/p11-x1a-integration.log`。X1b/X1c真实双库同事务/Outbox/汇聚尚未验收，S6继续阻塞；具体分项见[开发进度](development-progress.md)。
- X1b本地完成：`BusinessLedgerResourceFactory`将业务库Mapper、账本/互斥/失效与同一DataSource事务管理器绑定，缺租户/数据范围/全表/审计拦截器拒绝装配，跨源事务不能失效。双MySQL轻量夹具`sma_test_p11_x1b_platform_20261112`＋`sma_test_p11_x1b_business_20261112`的`backend/target/p11-x1b-lean.log`为24/24、0失败/跳过、BUILD SUCCESS，覆盖提交/回滚、回执重放、并发互斥与平台库账本不变。各轮夹具问题及失败日志单独记录于P11进度，未把早期失败组合写作通过。X1c仍待实施，S6继续阻塞。
- X1c本地完成：业务库独立Flyway框架账本只建执行权/互斥/代次/Outbox；同库事件稳定键与字节一致性、回滚不留事件、事务外权威代次及过期/故障stale汇聚经真实双库验证。最终`backend/target/p11-x1c-schema-recheck.log`为25/25、平台`backend/target/p11-x1c-platform.log`为17/17，均0失败/跳过、BUILD SUCCESS。测试账号不可新建数据库，改用精确命名的专用X1b业务库，测试前清空并按独立脚本重建；详细失败轮次和边界见[开发进度](development-progress.md)。X1前置通过，S6可开工；实际资源目录、池、路由、轮询与迁移任务仍待S6/S7。

### S6 业务MySQL资源与路由计划（2026-11-12）

拟改路径及影响：`tenant`新增平台资源目录/租户业务组绑定与只对平台运维开放的登记接口；`common :: request`已有业务账本工厂由宿主资源运行时接入；`common :: persistence`补最小物理身份工厂，业务Mapper继续按独立工厂/事务管理器装配。追加tenant迁移，不改已交付V1～V5；README、运行说明和本规则同步。业务源码仍在业务分支，基座只给通用接入点。连接配置只留受控secretRef和受限目标字段，不通过普通参数、URL或前端泄露凭据。

| 子项 | 内容与边界 | 依赖 | 验证 | 状态 |
| --- | --- | --- | --- | --- |
| S6a | 平台资源记录、租户＋存储组绑定及版本/状态约束；内部登记服务仅部署运维身份可写，目标受控且密钥仅引用，HTTP权限入口归S6d，不建池 | X1 | 旧库V5→V6迁移、越权、无效目标/版本/重复绑定拒绝、兼容架构门禁 | 完成（本地，2026-11-12） |
| S6b | 宿主按物理资源＋修订懒建有界池，固定DataSourceIdentity并装框架账本Mapper/事务/BusinessLedgerProvider；旧修订持租约排空后关闭，业务Mapper归S6c | S6a | 真实双库同租户/跨租户、缺绑定拒绝、池上限/耗尽/排空与连接身份 | 完成（本地，2026-11-12） |
| S6c | 静态二开业务读写事务接入与资源修订固定，禁止事务内切换、跨源嵌套与默认库回落 | S6b | 独立业务夹具两库写读/回滚/重放、变更绑定时在途连接仍归旧源 | 完成（本地，2026-11-12） |
| S6d | 资源API/目录完整隔离与故障矩阵、SQL真实连接身份复核；文档、部署与二开接入收口 | S6c | 根reactor相关测试、真实双库并发/故障/池回收和平台回归 | 完成（本地，2026-11-12） |

S6a验收：追加Tenant V6平台目录`tenant_storage_resource`和`tenant_storage_binding`，两者是明确审查的全局平台元数据例外，只保存物理资源非机密描述/secretRef及ownerTenantId＋storageGroup指针，不存业务行或密码。新绑定只能`PENDING`，S7完成迁移前不能作为可用业务源。资源修订只新增不可覆盖，注册目标必须命中部署`allowed-hosts`，无TLS仅显式允许回环目标；默认白名单为空。`StorageDirectory`只提供内部登记/绑定，不含HTTP管理入口、实际连接池和资源激活。`StorageDirectoryMysqlTests`在精确专用可清空`sma_test_p11_x1b_platform_20261112`先跑Tenant V1～V5、保留旧租户行再升V6，并验证无资源表旧形状、平台运维身份、坏目标/跳号/重复绑定和PENDING状态。根reactor独立输出`target/p10-s6a/target`、日志`backend/target/p10-s6a.log`为架构17、平台豁免1、模块2、真实迁移/目录1，共21/21、0失败/错误/跳过、BUILD SUCCESS。未验证空库全模块启动、HTTP权限、真实池/路由与S7开通；继续S6b。

S6b验收：宿主`StorageLedgerProvider`按可信租户及业务组查平台目录，只接ACTIVE且schemaCompatibility=v1的绑定；`StoragePoolRegistry`按物理ID/修订复用池、设置每资源/节点/物理主机预算及短等待、拒绝原修订配置漂移，租约关闭后才回收池。`BusinessLedgerResourceFactory.preparePhysical`在Common内为真实Hikari池固定`DataSourceIdentity`、审查过的MyBatis拦截器、审计和本地事务，不让Tenant访问Common内部Mapper。凭据通过`tenant :: storage`的宿主`StorageCredentialResolver`按secretRef获取；默认无resolver时拒绝建池，数据库目录不存密码。S6b只有框架账本Mapper，业务Mapper和业务写同事务由S6c接入，S7负责正式激活而非手改状态。根reactor`target/p10-s6b/target`的`backend/target/p10-s6b-unit.log`为20/20，最终`backend/target/p10-s6b-final.log`为真实平台/业务双MySQL、跨租户/缺绑定/PENDING拒绝、执行权/代次/Outbox归属、身份/SQL统计、池预算与模块架构共27/27，均0失败/跳过、BUILD SUCCESS。测试仅在已迁移的任务双库中手动激活夹具绑定；未证明生产开通/密钥适配器或业务Mapper写入。

S6c验收：平台Mapper继续使用平台工厂，静态业务Mapper由独立工厂和`BusinessRoutingDataSource`装配；业务通过公开`BusinessLedger.Session.executeLocal/query`进入固定物理资源，MyBatis业务写与框架账本共享本地事务，离开会话不能借默认库。跨资源嵌套及已有异源事务拒绝；原会话在绑定变化后仍固定原物理库，新会话读取新绑定。两个Mapper工厂和物理业务工厂均接SQL执行统计，业务Statement按实际连接的resourceId/revision归属。真实平台库＋业务A/B三库夹具`BusinessMapperRoutingMysqlTests`在精确专用库运行，业务B先清空再重建；最终`backend/target/p10-s6c-switch.log`为1/1、0失败/错误/跳过、BUILD SUCCESS，含提交/回滚/切换/错源拒绝/SQL归属。平台启动、请求账本、缓存代次、SQL与连接身份回归`backend/target/p10-s6c-platform-final.log`为26/26、0失败/错误/跳过、BUILD SUCCESS。此前三库与架构夹具`backend/target/p10-s6c-telemetry.log`为20/20；该轮尚未包含最后补充的切换场景，后者以上述最终三库结果为准。HTTP资源管理、正式迁移激活及生产凭据解析器仍归S6d/S7；进入S6d。

S6d执行顺序：先开放仅平台部署运维身份＋显式权限可用的资源修订/租户绑定目录API，读写分权，响应不含secretRef/密码，写入仍只创建REGISTERED资源与PENDING绑定；以真实HTTP核对跨租户、缺权限、重放与无效目标。再补池故障/竞争/回收和真实连接统计矩阵，最后同步部署、二开、README与规则并运行相关根reactor回归。S7前不开放人工直接激活或切库动作。

S6d验收：新增`GET/POST /api/tenant/storage-resources`与`GET/POST /api/tenant/storage-bindings`，目录读取仅返回平台运维可见的资源元数据与PENDING绑定，资源响应不含secretRef；登记/绑定分别要求`tenant:storage:register`/`tenant:storage:bind`，读取要求`tenant:storage:view`，全部叠加部署平台操作者身份。写入使用原LOCAL幂等治理，重复键返回原操作回执，不自动激活。原V5→V6迁移目录单项`backend/target/p10-s6d-directory.log`为1/1、0失败/跳过；首次HTTP轮因夹具租户仍处PROVISIONING，入口解析先返回404，改为ACTIVE后最终`backend/target/p10-s6d-http-final.log`为HTTP/普通端点策略/架构/模块22/22、0失败/错误/跳过、BUILD SUCCESS。策略审查前后差异仅新增4条资源端点，审查轮`backend/target/p10-s6d-policy-review.log`为21/21；最终轮未打开审查开关。三库切换、实际连接SQL归属、PENDING拒绝、池预算/缺凭据/装配失败与关闭排空`backend/target/p10-s6d-matrix.log`为11/11、0失败/跳过、BUILD SUCCESS；同库并发执行权/互斥及Outbox仍沿用已通过的X1c真实双库证据。实际生产凭据适配、迁移任务、ACTIVE激活与切库恢复尚未交付，全部进入S7；S6的本地资源登记/路由阶段完成。

近期优先级（2026-11-12）：用户授权按[单实例稳定二开基线](secondary-development-baseline-progress.md)先收敛S7d-3真实缺陷与回归，再做X2a归属契约/共享缓存接入和业务样例。专用Redis与SQL运维UI等延后；完整X2/X5状态不因缩小近期范围而改变。

### S7 业务库开通与迁移计划（2026-11-12）

拟改路径及影响：`tenant/internal/service`新增资源迁移执行器、持久任务与状态机，`tenant/internal/persistence`新增任务Mapper和Tenant V7追加迁移；`tenant/storage`只保留部署凭据SPI，业务模块不接触物理连接。`common :: request`既有同库账本/Outbox协议不变。平台目录和业务库迁移分开提交：领取/完成任务在平台短事务，Flyway和复制在事务外，最终绑定切换必须在停写/排空/核对后单独提交。新HTTP动作需独立权限、原生任务回执和端点基线；不改已发布V1～V6。

| 子项 | 内容与边界 | 依赖 | 验证 | 状态 |
| --- | --- | --- | --- | --- |
| S7a | 物理资源迁移执行权、独立Flyway历史与共享库仅执行一次；凭据/目标/池预算失效时拒绝 | S6 | 空库/旧库/重复/并发、失败后验证再续跑、双租户同物理源 | 完成（本地，2026-11-12） |
| S7b | 持久开通任务和PENDING→ACTIVE受控激活，平台/业务代次与Outbox核对 | S7a | 提交/回滚/重复键/并发、故障续跑、失败不得提供连接 | 完成（本地，2026-11-12） |
| S7c | 维护窗口迁移与换库：预检、备份证据、复制核对、停写排空、最终同步、切换、恢复 | S7b | 旧/新库双写拒绝、在途会话、未知结果核对与切后回退限制 | 完成（本地，S7c-1/2/3；真实多进程留X5） |
| S7d | 资源Outbox轮询/失效汇聚、默认租户初始化与平台任务HTTP/文档收口 | S7c | 真实双库事件重投、stale/故障、非默认平台租户引导、根reactor | 进行中（S7d-1/2及S7d-3独立引导通过；完整回归待复验） |

S7开工门禁：S6d本地验证通过。S7a不得直接把PENDING行改ACTIVE，也不得为迁移加载平台全套Common表。共享库执行权以物理资源ID＋修订为键；迁移结果必须以真实Flyway历史和实际schema验证，任务记录不能代替物理库校验。测试账号无CREATE DATABASE权限，仅用精确专用可清空库。

S7a首轮验收：内部`StorageMigrationService`只接受平台部署运维身份、已登记资源及部署`StorageCredentialResolver`；拒绝已有平台事务，以单节点一个迁移名额和目标MySQL命名锁串行，临时连接执行`db/business-ledger`独立Flyway，旧业务表按版本0建立历史而不清空，迁移后validate。真实旧库/重复执行/命名锁竞争/越权、池故障及架构/模块`backend/target/p10-s7a-final.log`为23/23、0失败/错误/跳过、BUILD SUCCESS。补测期间并行Mail接口签名不匹配使当前工作树根reactor编译失败，日志`backend/target/p10-s7a-final2.log`；未修改Mail文件，改以已通过的S6d输出做任务专用冻结复验。

S7a最终复验：从已通过的S6d独立输出复制主类/测试类到`target/p10-s7a-frozen/target`，在该冻结快照上仅编译`StorageJdbcUrl`、`StorageMigrationService`与补充的`StorageMigrationMysqlTests`，再以精确专用`sma_test_p11_x1b_business_20261112`运行单项。首次补测发现测试所持MySQL命名锁关闭连接时未及时释放，显式`RELEASE_LOCK`后`backend/target/p10-s7a-frozen-final.log`为1/1、0失败/错误/跳过、BUILD SUCCESS；同时覆盖旧表保留、空库重建、重复/续跑与锁竞争。先前完整源码/架构/模块23/23结果与本轮冻结补测合并构成S7a本地证据；冻结快照不等于当前并行工作树全量可编译，后续S7b根reactor仍须单独复验。S7a完成，进入S7b。

S7b验收：Tenant V7追加平台租户拥有的`tenant_storage_operation`持久回执，保留目标绑定ID、物理资源/修订、原绑定版本、fence、租约与运行状态，不保存凭据或业务数据。`StorageProvisionState`用平台短事务领取、同键重放/意图核对、RUNNING拒绝、失败置NEEDS_REVIEW、同键续跑提升fence，只有原fence与PENDING版本匹配才能在同一事务写ACTIVE绑定及SUCCEEDED回执。`StorageProvisionService`在事务外调用物理库迁移，失败不激活；迁移时额外核对执行权/互斥/缓存代次/Outbox实际表和关键列，Flyway历史存在但表缺失仍拒绝。根reactor主源码编译`backend/target/p10-s7b-compile.log`成功；全量测试编译被并行Auth/Identity/OpenAPI维护构造器变更阻断，日志`backend/target/p10-s7b.log`，未修改这些专项。任务专用冻结主类与测试支持类上的平台＋业务双库/服务编排`backend/target/p10-s7b-final.log`为3/3、0失败/错误/跳过、BUILD SUCCESS；物理锁及缺Outbox表拒绝`backend/target/p10-s7a-physical-lock-final.log`为1/1。S7b完成于本地冻结验证；当前全量测试编译仍需并行专项收敛后再跑，不能作为生产发布门禁通过。迁移操作HTTP、跨库切换/备份/停写与轮询仍待S7c/S7d。

S7c拟改路径与子项：`tenant/internal/service/StorageLedgerProvider`及新增会话门禁先提供按目标租户＋存储组的本节点在途统计；平台迁移任务/绑定状态由Tenant追加V8持久化跨节点排空证据和停写代次；目标数据库的复制、行数/摘要、业务Outbox代次与备份证据由受控外部迁移执行器提交，不能凭布尔请求字段宣称完成。切换事务只读任务/绑定原版本和目标修订，核对停写、所有节点排空、最终同步及证据后执行，旧修订保持在途租约到关闭。首版维护窗口，目标接受新写后回退须作为反向迁移任务核对数据，禁止仅改绑定。分项S7c-1会话计数与停写拒绝、S7c-2持久任务和跨节点排空/复制证据、S7c-3版本化切换/恢复与真实双库故障矩阵；每项单独验证后记进度。

S7c-1本地完成：`StorageSessionTracker`按可信租户＋存储组记录本节点在途会话，`block`先拒绝新会话并返回旧会话数，最后一次关闭后归零；调用方在资源池打开后、返回业务会话前登记，阻断与登记同键原子，拒绝时释放已借物理租约。`StorageLedgerProvider`使用该跟踪器，既有三参数测试构造保持兼容。根reactor真实平台/业务双库、架构与模块`backend/target/p10-s7c1-dual.log`为21/21、0失败/错误/跳过、BUILD SUCCESS。此计数只代表本节点，不授予跨节点排空资格；S7c-2须追加持久停写代次、成员证明和跨节点聚合后才能切库。

S7b续跑协议收紧（2026-11-12）：`NEEDS_REVIEW`或已过期RUNNING不能由普通`provision`同键重放自动续跑；只允许平台运维在核对原操作与目标物理库后调用独立`retryAfterReview`，递增fence再迁移。当前工作树根reactor平台＋业务双库状态/编排、S7c本节点门禁及架构/模块`backend/target/p10-s7b-review.log`为23/23、0失败/错误/跳过、BUILD SUCCESS，覆盖失败保留PENDING、普通重放拒绝、审查后续跑及重放已完成结果。此前冻结快照为开发期证据，本轮根reactor结果优先；全量标准verify仍未运行。

S7c-2本地完成（2026-11-12）：Tenant V8追加绑定`DRAINING`状态、持久业务会话租约、换库任务及逐节点排空证明。会话登记与停写共用绑定行锁，新会话在停写提交后拒绝；物理关闭失败保留租约供核对。`common :: deployment`新增宿主只读节点清单/本节点身份契约，按部署清单、进程ID和租约epoch冻结成员；每节点只可由本进程host worker证明排空，未知/离线/替换成员拒绝。外部`StorageMigrationEvidence`须从实际备份及源/目标库返回摘要、行数、Outbox水位；缺部署实现拒绝，不接受HTTP布尔证明。最终同步须在全部排空之后，任务才进入READY；绑定仍DRAINING，尚未切换。首次`backend/target/p10-s7c2.log`中V8实际迁移、S7b/MySQL、模块通过，架构旧Timestamp调用及最终证明转换失败；修复后根reactor`backend/target/p10-s7c2-final.log`为22/22、0失败/错误/跳过、BUILD SUCCESS（V6～V8真实MySQL、停写后登记拒绝、在途租约/缺节点证明/旧同步时间拒绝、架构及模块）。本项用模拟的两节点受控清单和物理证明测试状态机；真实双进程、真实源/目标业务数据复制与最终切换归S7c-3验收。S7c-3下一步，不把READY当已切库。

S7c-3本地实现与验收（2026-11-12）：Tenant V9允许仅切换前ABORTED恢复，V10记录READY时间。`commit`在平台短事务内锁定任务与绑定，复核ACTIVE目标、停写状态/旧版本、无持久会话租约、全部节点证明、成员未替换及READY后新鲜的源/目标摘要、行数与Outbox水位；同事务提升绑定版本、切向目标并记SWITCHED。切后`abort`拒绝，回原库需以当前目标为源创建新任务，重新复制和证明；切前ABORTED可恢复原库但提升版本。宿主节点成员变更在提交时由`DeploymentNodes.requireCurrent`共享锁核对。状态机使用两个独立节点实例共享平台库模拟排空，物理数据夹具使用两套真实独立MySQL业务库，验证缺行、最终增量未复制、目标损坏均拒绝。最终根reactor`backend/target/p10-s7c3-final-dual.log`为23/23、0失败/错误/跳过、BUILD SUCCESS（真实V6～V10迁移、物理夹具、架构/模块）。新增双实例夹具复验见`backend/target/p10-s7c3-peer-final.log`。该测试只核对`s7c_copy_probe`，并不声称已复制任意业务表；生产仍须部署实现`StorageMigrationEvidence`及备份执行器，且真实双进程完整验收归X5。S7c本地完成，进入S7d。

S7d子项（依赖S7c，逐项验收）：S7d-1补业务库Outbox的受控领取/投递/重投接口及跨资源失效汇聚，使用真实双库验证提交、失败重投和stale；S7d-2平台开通/切换任务HTTP、显式权限、原生回执与端点策略，验证授权/重放/未知状态；S7d-3非默认平台租户引导、运行说明和根reactor回归。三个子项依序推进；S7d-1完成前不开始HTTP。拟改`common :: request`资源轮询公开契约及内部资源工厂、`tenant/internal/service`调度与目录、租户控制器/权限/i18n/策略，数据库迁移只追加，业务模块仍不触碰Common内部Mapper。

S7d-1本地完成（2026-11-12）：业务框架V2追加Outbox投递fence、租约和尝试次数，资源内短事务以`FOR UPDATE SKIP LOCKED`领取后在事务外用持久行租户恢复工作身份，成功仅原fence可置DONE；失败留UNKNOWN并在租约过期后重投相同事件，消费者必须按稳定键幂等。宿主只轮询已打开的有限物理池，缺消费者/歧义拒绝完成；业务库授权代次可按物理资源＋可信租户汇聚到有界`BusinessRevisionRollup`，故障保留旧值且标stale。首次专项`p10-s7d1.log`触发事件命名架构规则与测试旧键重放，后续`p10-s7d1-jdbc.log`发现宿主领取被租户拦截器过滤，改为Common资源内JDBC受控领取；`p10-s7d1-tenant.log`发现共享测试库残留其他租户待投递事件。最终仅清空两套精确可清空业务测试库重建业务框架V1/V2，根reactor`backend/target/p10-s7d1-final.log`为20/20、0失败/错误/跳过、BUILD SUCCESS；覆盖真实双库隔离、失败UNKNOWN/原字节重投、租户工作身份、资源代次stale与架构/模块。没有外部事件消费者默认实现，生产无消费者时留待处理。下一S7d-2。

S7d-2本地完成（2026-11-12）：新增`StorageOperationsController`原生开通/审查后重试/换库领取及逐阶段执行、按原键/任务ID只读核对入口，`tenant:storage:provision/switch`权限叠加部署平台操作者身份；缺物理凭据时开通任务保留NEEDS_REVIEW，HTTP返回租户状态错误且GET可查，普通provision同键拒绝自动重试。端点策略审查命令`backend/target/p10-s7d2-policy-review.log`20/20通过，新增10条策略映射；普通门禁随HTTP最终测试通过。首轮完整HTTP`backend/target/p10-s7d2-http.log`为22项中1失败：未配置凭据时IllegalStateException落500而非原生业务状态，已在控制器转换为`TenantErrorCode.STATE`。首轮清理专用库受其他本线残余测试进程争用，终止本轮自有孤儿进程后在独立`sma_test_p10_s5f_20261112`重跑；`backend/target/p10-s7d2-isolated.log`为22/22、0失败/错误/跳过、BUILD SUCCESS，覆盖真实HTTP平台身份/权限、失败回执、同键重放、PENDING不可切库、架构/模块/普通端点策略。完整启动迁移耗时13分钟，外部物理凭据/复制SPI仍缺，未声称成功生产开通或切换。下一S7d-3。

S7d-3进行中（2026-11-12）：首次非默认平台租户用common公开`PlatformTenantQuery`将IAM首位管理员、权限目录及Config内置种子显式放入配置ID，目录仍先原子占位；已有平台库更换ID不自动迁移。根reactor独立`sma_test_p10_s5f_20261112`库的`backend/target/p10-s7d3-isolated.log`为22/22、0失败/错误/跳过、BUILD SUCCESS，真实全模块启动证实ID=42目录、回环域名、管理员和平台权限，ID=1不产生目录/权限/参数/字典行；README、运行说明与规则已补充边界。完整默认配置回归首轮`backend/target/p10-s7d3-full.log`编译后安全专项新增两个Defense控制器，随后才更新端点基线，编译快照缺4个新端点而普通门禁失败；保留日志并停止本轮自有测试进程，未改安全专项。当前源码复验`backend/target/p10-s7d3-policy.log`又遇并行P08握手适配器先于SocketHub修改落盘，主源码编译缺`beginHandshake`/`HANDSHAKE_ATTRIBUTE`/`Handshake`；该轮也未通过。待并行文件收敛，从当前一致源码重跑普通门禁和根reactor完整回归；此之前S7d/S7不标完成，也不进入X2。

S7d-3当前源码复验（同日）：P08 SocketHub落盘后，根reactor聚焦`backend/target/p10-s7d3-policy2.log`的架构17、端点1、模块2、平台身份2共22/22、0跳过、BUILD SUCCESS。完整默认配置第二轮`backend/target/p10-s7d3-full2.log`在专用P10库执行，应用启动与迁移2/2通过，端点策略、Config分层、IAM及已运行的租户前序模块无新增P10失败；全量途中有三项明确失败：P08 `MailDispatcherDrainTests:67` 两工作线程10秒内未同时进入传输，Common `IdentifierBindingTests:40`旧IdWorker取得Snowflake而非持久号段，安全专项`SecurityEventQueryHttpTests:101`固定预期3条却观察到6条同租户事件。安全专项该类在其独立库5/5通过。测试继续到`IdentityReauthenticationTests`后，主线程在MySQL网络读长时间停滞、JVM工作集约6.5GB；因已有三项失败，保留日志并仅停止本轮自有Surefire进程，不声称全量通过。S7d-3仍待三个跨线夹具/全量隔离问题收敛和根reactor复验，X2继续阻塞。
