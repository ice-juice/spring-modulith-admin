# S2 安全事件采集实施计划

依据[安全方案](proposals/P10-security-defense-and-branding.md)第4节；前置S1d和X4已本地通过。属于已授权安全专项，保持密码策略、Host租户绑定和P08并行排空工作。每项验证后同步[调度](development-schedule.md)与[安全总进度](security-defense-progress.md)。

| 顺序 | 任务和边界 | 验证 | 状态 |
| --- | --- | --- | --- |
| S2a1 | common.security只读来源契约及请求生命周期；部署显式CIDR、IPv4/IPv6、受信代理链；不覆写remoteAddr、不改变认证预算或租户规则 | 字面地址/CIDR/伪造头/链边界/容量、异常及线程隔离、配置绑定、部署摘要与原Host过滤器/架构 | 完成（本地35/37/10项分轮验证） |
| S2a2 | common类型化安全信号与可信归属捕获；固定分类/事实/严重度，主体从当前安全上下文取值，匿名不凭Host或请求tenantId获得TENANT归属 | 后台/应用/机器/匿名/未知主体，敏感内容与长度预算、失效及线程边界 | 完成（本地最终根reactor52项；未接持久化） |
| S2b | defense模块、平台与租户独立事件存储、共享去重/容量/保留期；受审平台表例外及独立提交 | 独立MySQL迁移、业务回滚保留事件、幂等/跨租户/平台运维/容量/并发，架构 | 完成（本地根reactor38项，含8真实存储场景；无生产HTTP接线） |
| S2c | 有界采集与持久故障缓冲、恢复及丢弃指标；部署节点身份/工作准入，不能因检测故障放行原拒绝 | 故障/饱和/重启/补传/租约、无无限队列和HTTP外部I/O | 完成（本地最终49项含3真实数据库场景；生产拒绝点待S2d） |
| S2d | 入口拒绝、认证/授权/签名等确定性信号接线；受信查询和脱敏展示API | 真实HTTP过滤器前拒绝及业务拒绝、普通业务不误判、平台/租户/权限矩阵、策略基线 | 实施中：d1/d2a/d2b本地完成；d2b最后11项聚焦通过，前序数据库/查询/重启分轮证据见下；下一d3保留调度及完整闭环 |
| S2e | 当前制品集成、容量/恢复及文档收尾 | 根reactor、真实HTTP/数据库专项、README/规则/二开/运维手册同步 | 待实施，依赖d |

S2a1落点：`common/security/RequestSource*.java`、`common/internal/security/RequestSource*.java`与数值IP解析器；测试在common对应包。新增`sma.security.source.trusted-proxies`、`maximum-hops`、`maximum-header-characters`，重启生效，纳入宿主部署证据。该网段表只决定安全来源元数据，不扩大`sma.tenant.entry.trusted-proxy-addresses`的Host/Proto信任，不自动迁移旧认证计数。代理必须覆盖外来来源头并追加真实对端，受信链从直连向左剥离，遇第一个不受信地址停止；全受信而缺少可确认客户端、重复/畸形/超限头均记录有限原因并回落直连证据。仅支持X-Forwarded-For，受信对端同时提供Forwarded视为不受支持的歧义输入。拒绝主机名、端口、zone、非规范IPv4和/0；无DNS请求。原始头/路径/查询/正文不进入来源DTO。

S2a1不新建defense表或HTTP入口；S2完整完成前不声称安全事件已落库、攻击监控或告警已交付。当前只支持同步请求线程读取；异步使用明确捕获的不可变来源值，不能继承ThreadLocal或由来源值构造身份。

S2a1完成（2026-11-12）：根reactor隔离`target/security-s2a/target`首轮RequestSource/Configuration/Evidence、TenantRequestFilter、DeploymentFieldInventory、Modularity/Architecture/普通端点35项通过（security-s2a-source.log）。独立`sma_test_security_source`＋`target/security-s2a-http/target`执行同组及RequestSourceHttpTests，37/37通过、0跳过，BUILD SUCCESS（security-s2a-source-http.log，9分53秒）。真实Tomcat/HTTP覆盖代理链、缺失/畸形/超长/重复/Forwarded歧义、正常入口200、原保护401、响应不泄漏来源和下一请求独立上下文；异常/跨线程/未知Host在单元链验证。测试进程45220终态、随机54214无监听；专用可清空库保留用于后续专项，不触碰用户服务。JAR SHA256 `0dc1fdfa7568ab2311eced87ac5214dd139baee3f9c1b1993f8b1aff3b1ba4c3`。

追加DeploymentSettings/RequestSourceEvidence/FieldInventory 10项通过（security-s2a-settings.log），确认来源配置进入原host-policy且原秘密排除/配置差异仍有效；分轮数字有重叠，不相加为全量。README、后端规则、运维/二开/部署文档同步。S2a2只补捕获契约，后续b/c/d才提供持久存储、故障缓冲和生产拒绝点接线，当前不宣称事件已入库。

S2a2完成（2026-11-12）：新增`SecuritySignal`/`SecuritySignalCapture`与common内部捕获服务。固定Fact携带分类、严重度、结果、推断和规则版本，禁止自由证据输入；归属只从已认证已识别主体捕获，匿名已知Host/背景TenantScope不获得租户归属，入口不匹配/未知主体/HTTP转job降为无主体PLATFORM。后台用户名、密码、任务workId不进入信号；APP_USER保留subject/audience/realm/client，机器保留应用标识/portal。请求级correlationId由内部来源绑定生成，不取外来trace头；捕获不入队/落库/改认证。

根reactor同`target/security-s2a/target`：首轮50项通过（security-s2a-signals.log），补最大ID/地址/audience的序列化≤2KiB及实际Bean绑定后，`-Dtest=SecuritySignalCaptureTests,RequestSourceTests,RequestSourceConfigurationTests,RequestSourceEvidenceTests,DeploymentSettingsTests,DeploymentFieldInventoryTests,ActorContextTests,ModularityTests,ArchitectureRulesTests,SecurityEndpointInventoryTests -Dsurefire.failIfNoSpecifiedTests=false verify`最终52/52通过、0失败/跳过、BUILD SUCCESS（security-s2a-signals-final.log，1分08秒）。含信号8项、来源/配置/摘要、ActorContext及模块/普通策略基线；无新增HTTP入口。a1的37项真实宿主证据仍为前一制品，不冒充最终信号捕获已做端到端事件存储。下一S2b平台/租户独立持久事件和访问契约，整体S2仍实施中。

S2b实现边界：新增已授权`defense`静态模块与V1迁移，`defense_platform_event`保存无租户归属事件，`defense_tenant_event`保持TenantEntity自动过滤；`defense_capacity`两条固定范围计数为受审平台技术表，`defense_tenant_capacity`按租户计数。仅前两种无租户表纳入精确架构白名单，不改通用拦截器、不让业务申请免租户。平台/所有租户总量/单租户容量均在同库锁和REQUIRES_NEW事务更新；相同事件ID同摘要重复不占容量，异内容拒绝；按发生时间计算保留截止，过期重放不重建事件，不能靠重复延长保留。只允许宿主worker调用内部append/prune，HTTP/认证主体/tenant job不得直接投递任意DTO；后续S2c连接受信采集与缓冲。

查询用defense公开只读契约，租户读取强制当前后台租户及`defense:event:view`，平台读取通过defense拥有的PlatformAccess SPI＋tenant现有部署运维身份＋`defense:platform:view`；平台权限不隐含跨租户读取。保留标记只能宿主内部维护，清理跳过保留记录、每轮最多100条并同步计数。S2b无自动后台清理、HTTP端点或管理页面；调度、输入速率/缓冲和实际拒绝点分别归S2c/d。配置容量/保留期纳入部署证据，验收使用独立`sma_test_security_events`与`target/security-s2b/target`。

S2b首轮结构27项通过（security-s2b-structure.log，1分05秒）；真实库首轮security-s2b-store.log在编译阶段因并行StorageSwitchState调用尚未写入的StorageDirectory.bindingForSwitch失败，未运行数据库用例。观察到并行16:43补齐该方法后，重跑security-s2b-store-recheck.log，不回退或修改租户换库实现。另补专用测试库trigger故障，使事件插入/全局计数更新后租户计数更新失败，验证全事务回滚及同包可重新明确投递。

S2b第二轮共38项：36通过、架构1失败（并行StorageSwitchState旧日期API，后续并行已修复）、数据库故障夹具1错误（创建trigger需要SUPER且binary logging开启）。defense V1在新专用库真实迁移成功，部署证据装配通过，存储其余7场景全部通过。当前源码结构复验29/29通过（security-s2b-structure-recheck.log，1分20秒），未放宽日期门禁。故障夹具改为专用表临时CHECK约束，正常插入records=0后仅目标租户更新计数失败；finally删除测试约束，不改MySQL全局变量/权限。待最后复跑通过前不标S2b完成。

第三轮security-s2b-store-final.log在testCompile阶段被并行StorageSwitchMysqlTests的finalProof作用域错误阻断，尚未重新执行数据库测试。观察17:00并行已修复变量后，继续相同根reactor/独立库/输出的security-s2b-store-final-recheck.log；未修改并行换库文件或跳过测试编译。

S2b完成（2026-11-12）：最终根reactor `-Dsma.verify.directory=target/security-s2b/target -Dtest=SecurityEventStoreTests,DefenseStorageConfigurationTests,DeploymentEvidenceAssemblyTests,ModularityTests,ArchitectureRulesTests,PlatformPersistenceIsolationTests,SecurityEndpointInventoryTests,I18nBundlesTests,SqlStructureSafetyTests,DeploymentFieldInventoryTests -Dsurefire.failIfNoSpecifiedTests=false -Dspring.test.context.cache.maxSize=2 verify` **38/38通过、0失败/错误/跳过、BUILD SUCCESS**（security-s2b-store-final-recheck.log，13分02秒）。只用`sma_test_security_events`，defense V1真实建表成功；8个存储场景覆盖外层业务回滚保留事件、重复不延长/异内容拒绝、同包4线程唯一提交及并发容量、跨租户/平台运维拒绝、全租户总容量、保留/批次清理/过期不重建、HTTP/job拒绝及CHECK故障导致事件/两级计数共同回滚。部署证据装配、配置、模块/架构/平台表/i18n/SQL/普通端点门禁同轮通过。

最终制品SHA256 `b423e859e1d792d3ef244a7630cf7ae9fb336127ae01eab263857b50e1c9a890`。测试进程48076已退出；临时CHECK/trigger不存在，平台/租户总量计数均归零，专用可清空库保留。此前失败记录保留，不把分轮27/29加为本次总量。README模块目录与旧宽泛免租户说明修正，后端/数据规则、二开与运行手册、调度同步。当前仅有宿主内部存储与公开Java只读契约；自动采集/缓冲/清理调度/HTTP管理/告警未交付。下一S2c有界采集与持久故障缓冲；S2整体和X5未完成，旧宿主库升级/回退组合归S2e/X5，空库迁移不替代生产升级演练。

S2c拆分（按依赖验收）：c1先实现defense内部加密磁盘缓冲原语及文件系统故障测试，c2接入仅Fact采集接口、内存有界准入/速率/优先预留、独立工作线程和DeploymentWork准入、计数指标及显式启用配置，c3做真实数据库故障/恢复/重启/排空联调并同步文档后才结束c。HTTP只做类型化捕获和无等待入队，不执行磁盘/数据库IO；队列接收不等于持久化。worker先以原eventId持久落盘再尝试数据库，确认STORED/DUPLICATE/EXPIRED后才删除，冲突保留并计数，容量/未知提交保留原包。后台失败不得改变原HTTP认证拒绝。

c1落点为`defense/internal/service/EncryptedSignalSpool`，不新增HTTP/表/自动线程。显式部署目录与32字节独立AES-GCM密钥，AAD绑定格式版本和事件ID，磁盘不保存明文；每条≤2KiB JSON，目录条数/总字节有硬上限并为高优先信号预留容量。单目录OS文件锁防同机双进程共享，拒绝符号链接/路径逃逸及未知文件，写入先CREATE_NEW临时文件＋force再ATOMIC_MOVE；启动对受限遗留临时文件逐条验证并恢复，坏密文/错密钥失败关闭且保留证据，不自动清空。ACK只删除已验证事件；磁盘满/权限/原子移动不支持等不虚报持久化。进程重启恢复与OS级落盘验证不等同断电/硬件耐久，后者部署验收另记。c2再提供受保护密钥文件加载/指标和公共接线，密钥不默认生成或借其他租户账号。

S2c1完成（2026-11-12）：EncryptedSignalSpool支持独立AES-GCM信号缓冲、固定版本/规则分类快照、OS独占锁、CREATE_NEW/force/原子移动、启动临时文件核验恢复、精确ACK和条数/字节/优先预留；错误保留文件并失败关闭。首轮测试编译遇测试变量java遮蔽包名已修正；第二轮基础通过但Windows无符号链接权限导致1项跳过，改为真实Junction重解析点验证后最终`-Dsma.verify.directory=target/security-s2c/target -Dtest=EncryptedSignalSpoolTests,ModularityTests,ArchitectureRulesTests,PlatformPersistenceIsolationTests,SecurityEndpointInventoryTests -Dsurefire.failIfNoSpecifiedTests=false verify` **30/30通过，0失败/错误/跳过，BUILD SUCCESS**（security-s2c-spool-final.log，1分17秒）。其中缓冲9项包括错密钥/篡改/文件改名/分类版本不匹配、容量/冲突/残留部分写、独占锁、真实Windows Junction与独立子JVM写盘后halt再重开恢复。子进程确认终态，JUnit临时目录清理；不宣称硬件断电耐久。

当前仅内部文件原语，不启动后台线程或改变HTTP流程。下一c2显式启用、受保护密钥加载、只接Fact的无等待采集、工作准入、持久状态与丢弃指标；数据库/磁盘故障及重启联合验收归c3。不能用原语通过将整个S2c或事件监控标交付。

S2c2完成（本地组件验收，2026-11-12）：新增common.SecuritySignals只接Fact，当前线程捕获身份后tryLock有界队列；四分之一内存和速率配额留给较高严重度，饱和/争用/捕获失败/停机均明确拒绝并计数。后台单执行线程不继承上下文，每单元先DeploymentWork准入，先加密持久化再写数据库，STORED/DUPLICATE/EXPIRED才ACK；容量与冲突保留，退避和轮转防单租户阻塞。关闭先停新工作，已准入IO实际退出后才关磁盘锁/释放许可，未持久队列关闭计数不虚报送达。显式启用/独立目录/32字节密钥及节点环境scope；HMAC派生密钥绑定scope，错范围不读旧文件。默认禁用，无新增HTTP写入。

security-s2c-collection.log首轮40项通过；补scope绑定、负nanoTime、禁用/准入异常和环境/部署摘要后，根reactor`-Dsma.verify.directory=target/security-s2c/target -Dtest=SecurityCollectionTests,EncryptedSignalSpoolTests,DefenseCollectionConfigurationTests,DefenseStorageConfigurationTests,DeploymentFieldInventoryTests,ModularityTests,ArchitectureRulesTests,PlatformPersistenceIsolationTests,SecurityEndpointInventoryTests -Dsurefire.failIfNoSpecifiedTests=false verify`最终44/44通过、0失败/跳过（security-s2c-collection-final.log，1分31秒）。包含磁盘9、采集9和配置2项；提交确认丢失此阶段用真实磁盘＋内存数据库替身，c3继续真实MySQL，不把它当真实数据库故障恢复已完成。指标只用固定result标签，无租户/IP/正文/路径基数。

C3首次真实库运行security-s2c-mysql.log使用专用`sma_test_security_collection`/`target/security-s2c-mysql/target`，编译后补强了测试：将数据库故障替身改为仅目标租户的临时CHECK约束触发实际JDBC失败，并增加服务close超过3秒仍保持实际许可/磁盘所有权的独立测试。第一轮仍保留原编译边界，最终须重新编译复跑新用例；不修改运行进程的classes，不把第一次结果当新增场景已通过。

C3首轮security-s2c-mysql.log **48/48通过，0失败/跳过，BUILD SUCCESS（12分49秒）**；真实数据库提交后丢确认重开去重、正常typed service异步/租户与部署许可均通过。该轮数据库不可用仍为提交前异常替身，未包含随后补强的CHECK写失败；service关闭超时新增组件回归security-s2c-collection-shutdown.log为45/45通过。继续同专用库与独立输出执行security-s2c-mysql-final.log的当前源码49项组合，不把首轮旧测试编译当作新故障场景证据。

S2c全部本地完成（2026-11-12）：当前源码根reactor `-Dsma.verify.directory=target/security-s2c-mysql/target -Dtest=SecurityCollectionMysqlTests,SecurityCollectionTests,EncryptedSignalSpoolTests,DefenseCollectionConfigurationTests,DefenseStorageConfigurationTests,DeploymentEvidenceAssemblyTests,DeploymentFieldInventoryTests,ModularityTests,ArchitectureRulesTests,PlatformPersistenceIsolationTests,SecurityEndpointInventoryTests -Dsurefire.failIfNoSpecifiedTests=false -Dspring.test.context.cache.maxSize=2 verify` **49/49通过，0失败/错误/跳过，BUILD SUCCESS**（security-s2c-mysql-final.log，14分22秒）。独立sma_test_security_collection清库重建；3项真实数据库证明CHECK导致实际JDBC写失败后加密事件保留/重开恢复、实际提交后丢确认只去重一次、正常typed采集在干净工作线程按原租户及真实DeploymentWork许可提交。10项组件包含3秒关闭超时保持许可和OS锁直到IO退出，9项文件测试包含独立JVM halt重开与Windows Junction拒绝；部署装配和结构门禁同轮通过。

制品SHA256 `ef08b28425087943785513267c9934791631945fa30c4e156612efbfb3aaa8c9`。测试进程43424已退出，临时CHECK不存在；9181/9182/9183各仅一事件，专用可清空库保留。JUnit临时密钥/目录与子JVM均按测试生命周期清理；未修改用户服务、生产启用配置或真实账号。README、后端规则、运行/部署/二开手册和调度同步。C3首轮48项保留其旧测试编译边界，不替代当前49项；进程故障不等同断电/硬件耐久，多主机挂载不在范围。下一S2d生产拒绝点、权威查询/保留调度接线；S2整体、告警/封禁、P09外部环境及X5仍未完成。

S2d按依赖拆分：d1先将common.LocalizedErrors的已审固定错误key映射至Fact，覆盖过滤器ErrorResponseWriter与MVC异常；Tenant入口、Browser来源拒绝通过兼容新增writeObserved方法精确指定观察事实。RequestSourceScope当前线程绑定内最多一次尝试，不重试被拒收信号，退出后不残留；必须在认证清理前调用，缺来源绑定不推断。普通DATA_NOT_FOUND/404、版本冲突/幂等完成、5xx故障不泛化为攻击。common只依赖SecuritySignals接口，provider延迟解析避免装配循环；缺失/异常/饱和不改原状态码/正文/响应头。d2再增加权威查询API和受限方法/路由/状态投影，d3接保留调度及真实组合。未知路由扫描/文件流水线明确拒绝等须各自审查真实来源，不以任意404/文件异常补齐；阶段验收前不声称全站所有拒绝已覆盖。

S2d1组件验证：首轮security-s2d-rejections.log因新测试LocaleResolver类型不匹配而编译失败，改为FixedLocaleResolver后security-s2d-rejections-recheck.log 52/52通过；补真实HTTP签名重放测试并移除对随机UUID含“99”的不稳定脱敏断言后，security-s2d-final-unit.log再次52/52通过、0跳过、BUILD SUCCESS（18:24，1分33秒）。根reactor输出target/security-s2d/target，测试为RequestRejectionSignalsTests/ResponseTests、RequestSourceTests、TenantRequestFilterTests、BrowserRequestFilterTests、SecuritySignalCaptureTests及Modularity/Architecture/SecurityEndpointInventory。独立sma_test_security_rejections＋target/security-s2d-http/target的真实HTTP/认证/Identity/OpenAPI首轮security-s2d-http.log为49通过/2错误，0跳过：重新when设置采集替身提前执行旧Answer，传入null Fact，尚未发出故障/拒收断言请求。改为doAnswer/doReturn/doThrow，生产捕获不放宽；原Auth4/Identity9/OpenAPI6/Signature2均通过。最终security-s2d-http-final.log用根Standard入口重新编译完整组合，包含新增真实签名重放及兼容default用例，当前d1尚未完成。

d1最终HTTP重编译期间补充Retry-After原值/响应头集合不变断言；经javap确认该轮testCompile早于这一小改动，因此另外执行security-s2d-response-headers.log，根Standard＋target/security-s2d/target、RequestRejectionResponseTests **2/2通过、0跳过、BUILD SUCCESS（18:32，53秒）**。完整HTTP组合保留其编译边界，不冒充包含新增header断言。四份当前手册/规则本地链接检查59项有效；最终README/状态表在HTTP通过后同步。

后续d2实施顺序（仍依赖d1通过）：d2a先在defense.internal.web提供当前租户与平台独立列表/UUID详情只读入口，复用SecurityEvents每次权威授权，limit范围1～100且不接受tenantId切换；固定投影不透传存储payload。显式后台权限＋QUERY治理，平台同时验证部署运维身份；核对匿名/机器/APP拒绝、tenant superAdmin不越权、跨租户列表/详情不可见、错误参数、无写副作用及普通策略基线。d2b再增加受限方法/匹配路由模板/实际状态/部署节点元数据与未知路由观测，独立审查历史载荷/缓冲兼容和2KiB总预算；不得写原始URI/查询/header或以普通404推断攻击。d3负责保留调度及HTTP到真实持久化组合，d2a通过不代表d2全部完成。

S2d1完成（2026-11-12）：最终根 `tools/dev/verify-backend.ps1 -Standard`，`-pl backend -am -Dsma.verify.directory=target/security-s2d-http/target -Dtest=RequestRejectionHttpTests,RequestRejectionSignalsTests,RequestRejectionResponseTests,AuthFlowTests,OpenAuthenticationTests,SignatureProtocolTests,IdentityApplicationHttpTests,ModularityTests,ArchitectureRulesTests,SecurityEndpointInventoryTests -Dsurefire.failIfNoSpecifiedTests=false -Dspring.test.context.cache.maxSize=2`，**53/53通过、0失败/错误/跳过、BUILD SUCCESS（18:46，17分02秒）**，日志security-s2d-http-final.log。仅使用sma_test_security_rejections，保留正常首次迁移与清库安全门禁；当前制品SHA256 `96255f3c3b3c2d16046ec58bf64ceb54ee63a12af3481b6b32c71ecfd84a7653`。测试进程34076已退出，随机端口58791无该进程残留，用户服务未重启。

5个采集HTTP用例验证真实Tomcat的401/Origin403/415/坏JSON400/超限413/未知Host404、真实签名nonce重放409，以及采集抛错仍保持原响应；带可信身份的MockMvc验证权限拒绝捕获当前tenant/用户、普通数据不存在和幂等完成不采、拒收只尝试一次。后者不冒充浏览器真实登录；整个类最终sink为替身，不替代d3真实worker持久闭环。原Auth4/Identity9/OpenAPI6/Signature2与结构同轮通过。此前52项组件/结构及额外2项header用例分轮保留，不相加冒充一次无重复全量。README、后端规则、二开/运行手册、调度和安全总进度已同步。

S2d2a开工：按上方计划仅增加受限只读HTTP投影与查询入口，复用已验收存储契约，暂不改变事件载荷/数据库结构或缓冲格式。该子项通过前不推进d2b生产编辑；P09外部账号/容器与S3～S6/X5继续保持原未验证状态。

d2a首轮结构security-s2d-query-contracts.log **21/21通过、0跳过、BUILD SUCCESS（18:52，1分38秒）**，模块/架构/精确平台表例外及接口策略通过。基线前后比对只增加events/platform-events列表及UUID详情四条GET；普通后续命令不含review标志。查询实际组合security-s2d-query-http.log发现新测试使用PER_CLASS，Spring实例注入先于仓库beforeTestClass清库，宿主已启动后被测试清库；现改回默认每方法实例、静态受限种子引用，不跳过清库或改生产逻辑。待原进程终态后重编译复验，该轮不得据启动成功标为通过。

d2a查询首轮自然结束为**28通过/5夹具错误、0跳过，BUILD FAILURE**；五项均在seed阶段因清库后的tenant_tenant不存在而失败，未进入HTTP断言。准备终止前复核发现测试JVM42600已自行退出，进程身份保护未执行任何停止操作；未影响用户服务。随后使用相同专用库和独立输出运行security-s2d-query-http-fixed.log，重新编译标准生命周期夹具，先正常清库再启动宿主；仍未标d2a完成。

S2d2a完成（2026-11-12）：最终根Standard入口，`-pl backend -am -Dsma.verify.directory=target/security-s2d-query/target -Dtest=SecurityEventQueryHttpTests,ModularityTests,ArchitectureRulesTests,SecurityEndpointInventoryTests,PlatformPersistenceIsolationTests,RequestRejectionSignalsTests,RequestRejectionResponseTests -Dsurefire.failIfNoSpecifiedTests=false -Dspring.test.context.cache.maxSize=2`，**33/33通过、0失败/错误/跳过、BUILD SUCCESS（19:04，7分29秒）**，日志security-s2d-query-http-fixed.log。正常清库先于应用启动，未使用review开关；专用库仍为sma_test_security_rejections，未并行运行该库其他验证。

5个真实MySQL/HTTP链路场景覆盖租户列表/UUID详情/固定字段/no-store/limit、客户端tenantId不切范围、跨租户/平台UUID与不存在同响应、平台部署身份＋显式权限且超管不越权、非法UUID/limit与POST拒绝，以及事件payload/hash/held/updated_at和3租户记录＋1平台记录不变。匿名使用真实Tomcat HTTP得到401；后台、机器、APP主体通过受控身份MockMvc运行实际安全链，其中非后台主体即使携带同名权限也403。未做前端页面或真实浏览器登录验收。HTTP只读投影保留原持久分类版本，没有新增迁移、缓冲格式、写入口或授权豁免。

最终制品SHA256 `0cc59409309308c0e2df85b1a85ed7a9057c33111dc9d3ec83c72e87815b0a3f`。测试进程32636已退出，随机端口64660的测试生命周期结束；专用库保留。接口基线仅新增4条本项GET；生成清单另保留14条此前已在基线中审查的租户存储入口，无删除已有行。README、后端规则、二开/运行手册、接口清单、调度及总进度同步；6份相关文档225个本地链接有效。首轮夹具失败与结构21项分轮证据保留，不相加冒充一次全量。下一d2b受限请求元数据与未知路由事实，之后d3保留调度/完整采集闭环；S2整体、告警/封禁及X5仍未完成，用户服务未部署或重启。

S2d2b开工与验收顺序：b1先扩展SecuritySignal可空metadata，保留旧六参数构造，空metadata不序列化，旧JSON/摘要及v1缓冲继续可读；新metadata含既有CacheNodeIdentity的部署组/节点/进程UUID，不读取请求头，不新增部署密钥或HTTP I/O。新增包使用schema=2，保留AES-GCM帧和原AAD，旧消费者必须拒绝新版本而非丢字段重放；须先停旧消费者，完整保留原缓冲/密钥再升级，产生新包后不能原地回退旧消费者。无表结构变更，事件/包2KiB预算保持。验证旧JSON精确字节、旧构造字节码调用、旧密文恢复/去重、新包版本与最大元数据大小，以及原采集/模块门禁。

b1通过后b2连接同步请求作用域和MVC拦截器：方法限定枚举，路由只能取已选HandlerMethod的声明模板并精确匹配框架模板；无匹配时只记录UNRESOLVED/UNMAPPED，不保留原URI/查询/正文。统一响应点显式传实际HTTP状态，MVC业务拒绝仍200；背景/尚未决定的状态保留空值，不伪造最终网络送达。未知路由只由框架NoResource/NoHandler事实产生LOW/OBSERVED/NO_ATTACK_INFERENCE，普通业务数据不存在仍不采。查询用includeMetadata=true显式选入新字段，默认投影兼容旧严格解码客户端。b3再用真实HTTP/专用MySQL验证新旧载荷、丢确认去重及权限/默认投影；逐项通过后更新进度，不提前推进保留调度。

S2d2b1完成（本地组件，2026-11-12）：security-s2d-metadata-compatibility.log根Standard/target/security-s2d-metadata/target **59/59通过、0跳过（19:16，1分56秒）**，包含SecuritySignalCompatibility4/Capture9、EncryptedSignalSpool11、Collection10、配置2与模块/架构/缓存边界/平台表/普通接口门禁。随后将最大模板改为256字符且包含最大JSON转义扩张，security-s2d-metadata-size.log的Compatibility **4/4通过（19:17，54秒）**；信号和包均≤2KiB。旧形状Java API独立编译consumer，再以当前API加载，证明六参数构造/访问器链接；该证据是受控旧形状夹具，不冒充已发布二开制品的全兼容矩阵。

旧六字段JSON逐字节一致，legacy密文可重开、去重且不改原字节；新包schema2使用原AES-GCM帧/AAD，旧schema1等值校验拒绝它，即使JSON读者忽略未知字段也不能静默接受。Observer取既有CacheNodeIdentity部署组/节点/进程快照，不触发网络/数据库；背景capture的http为空。record等值包含新增metadata，旧二开不能复制六字段后声称完整保留新信号；以原eventId关联，不能把DTO当持久或授权回执。b1未接请求方法/模板/响应状态，亦未以组件测试代替旧库真实重放；下一b2请求接线和opt-in查询，再b3真实组合。

S2d2b2组件通过（2026-11-12）：security-s2d-request-metadata.log根Standard/target/security-s2d-metadata/target **72/72通过、0失败/错误/跳过（19:25，1分51秒）**。RequestObservation4证明选定HandlerMethod声明模板精确匹配、伪造/超长模板和未知方法有界回落、只有实际资源处理器/无处理器可产生UNMAPPED事实、一次状态捕获及线程/下一请求隔离；原拒绝/来源/兼容/磁盘/采集与结构全部通过。LocalizedErrors区分MVC业务HTTP200与过滤器/异常实际选择状态；状态是观察时响应决定，不是最终网络送达。默认HTTP投影仍不输出metadata，includeMetadata=true才选入；无新路径、权限或策略基线修改。下一b3真实HTTP＋MySQL，当前不把组件通过视作完整d2b完成。

b3首轮security-s2d-metadata-http-mysql.log已开始专用sma_test_security_rejections及target/security-s2d-metadata-http/target组合。复核新增真实登录夹具发现遗漏必填portal，已补admin；javap确认本轮编译早于修正，原轮结果不能算修正后登录HTTP200的证据，结束后须聚焦复验。另补封闭旧Fact解码器测试，security-s2d-metadata-consumer-boundary.log根Standard **5/5通过、0跳过（19:33，25秒）**：旧事实正常读，新UNMAPPED被旧封闭枚举拒绝。升级手册据此明确先适配消费者，不宣传字段/构造兼容为所有旧枚举无修改兼容；该新增用例同样未混算入先前编译的数据库组合。

b3首轮完成为**69通过/1夹具失败、0错误/跳过，BUILD FAILURE**：唯一失败为新增登录夹具HTTP400而期望200；当轮编译未含portal修正，最终根因追查见后文。两项SecurityMetadataMysqlTests均通过，真实旧形状payload/hash/接收与到期时间不变、元数据差异冲突、新值读取/隔离，以及schema1/2混合目录在实际MySQL提交后未ACK、重开仅去重各一次通过；SecurityEventQueryHttpTests5项默认字段/opt-in/权限通过。数据库组合的独立子JVM用例仍为旧v1夹具，补强后的security-s2d-metadata-process.log **16/16通过、0跳过（19:39，37秒）**明确由独立子JVM写v2后halt、父进程重开保留probe-child和HTTP观察；同时保留legacy密文恢复与封闭枚举边界。继续security-s2d-metadata-http-final.log聚焦RequestRejectionHttpTests/ResponseTests/RequestObservationTests，包含portal修正，不复跑已通过的其他数据库场景；最终通过前不标d2b完成。

聚焦http-final为**10通过/1夹具失败、0错误/跳过**。更正此前归因：补portal仍400，直接使用当前编译的AuthController.Login与同版Jackson3解析证明遗漏boolean remember会触发MismatchedInputException（FAIL_ON_NULL_FOR_PRIMITIVES），尚未进入业务拒绝；日志展示转义不是实际JSON语法错误。现用JsonMapper.writeValueAsString(Map.of(...,"portal","admin","remember",false))生成完整夹具，独立解析确认DECODED/remember=false，并为HTTP断言附原响应诊断。不放宽生产解析或登录约束，继续同库/输出security-s2d-metadata-http-input-fixed.log聚焦复验，未通过前仍不标d2b完成。

S2d2b完成（本地，2026-11-12）：最后根Standard入口 `-pl backend -am -Dsma.verify.directory=target/security-s2d-metadata-http/target -Dtest=RequestRejectionHttpTests,RequestRejectionResponseTests,RequestObservationTests -Dsurefire.failIfNoSpecifiedTests=false -Dspring.test.context.cache.maxSize=2`，security-s2d-metadata-http-input-fixed.log **11/11通过、0失败/错误/跳过、BUILD SUCCESS（20:09，11分47秒）**。真实HTTP证实401前置拒绝的UNRESOLVED、坏JSON400及业务拒绝200的MATCHED声明模板、框架缺失资源404的UNMAPPED；用户名/密码/原URI未进入信号，采集故障和原响应契约保持。两个登录夹具失败均保留并已纠正，不放宽生产解析。该类sink仍为替身；HTTP到真实队列/磁盘/数据库端到端归d3。

分轮证据同时包括b2组件72项、首轮70项中的69项通过（包含真实MySQL旧载荷/摘要不变及混合schema提交后丢ACK去重2项、查询默认投影/opt-in/隔离5项）、独立子JVM v2 halt恢复/旧密文/闭合枚举边界16项。唯一首轮失败已在最终11项复验；不能相加或宣传为一次全量全绿。当前JAR SHA256 `0adecfca9e5b04128b740c161ac389dc0cd60c89acd5761dad25e56f7815a3b5`，测试JVM45768已退出，随机55561无测试监听。专用sma_test_security_rejections为最终聚焦轮重建状态，之前SQL夹具行不声称仍保留，验证证据在日志/报告中。

README、后端规则、开发/运行手册及调度/安全总进度同步，四份手册/规则66个本地链接有效。无新表/迁移/路由或权限豁免，用户服务未部署/重启。新Fact需要旧封闭枚举消费者先适配，schema2不能原地回退旧消费者，真实二开制品升级与整体S2e/X5仍未验收。下一d3自动保留期维护及完整HTTP采集持久化组合；文件流水线的明确拒绝接线须继续单独审查，不能把当前范围当全站监控或告警完成。

S2d3实施顺序：d3a先增加defense内部保留期维护，直接复用已验收Store.prunePlatform/pruneTenant的短事务、held保护与容量原子更新。通过TenantWorkQuery分页取得受信租户，包含不接受业务的已停用目录项，不跨模块查表、不增加免租户映射。当前受支持租户生命周期不删除目录；不能手工删目录制造孤儿事件，未来删除协议须先处理保留数据引用。每轮平台一次、目录一页、最多tenant-batch个租户各一个清理批次；拒绝不推进游标，单租户清理失败记录后继续其他租户，后续完整轮次重试，不把失败当成功。空页回到起点，held/未过期记录永不被维护器删除。

维护配置新增sma.defense.maintenance（避免与已有SMA_DEFENSE_RETENTION保留时长冲突）：默认enabled=true、interval=30s、tenant-batch=8；测试profile显式关闭自动维护，专项按需启用或驱动实际执行器。每单元在事务外取得DeploymentWork，排队不预占、执行重查；单后台线程、无继承请求上下文、有界且不积压调度。关闭停止新工作，最多等待3秒，不中断事务或提前释放实际许可；整机关闭仍须P08等待在途工作结束。配置纳入部署摘要并标明节点预算，多实例复用原数据库锁，不声明全局单调度者。

d3a先验证配置/摘要、拒绝/排队/在途/关闭/失败与公平轮询，再专用MySQL验证过期、held、停用租户、容量归还及双worker并发；通过并同步进度后，d3b再启用真实采集器做HTTP→队列→加密磁盘→数据库及保留维护组合。文件扫描明确拒绝接线仍独立审查，S2e进行最终覆盖与升级收尾；当前仅d3a开工。

d3a首轮security-s2d-retention-unit.log生产编译通过、测试编译失败：配置证据夹具向既有DefenseCollectionProperties的primitive boolean传null。当前文件已修正为false，不修改生产类型/校验；补真实Spring fixedDelay触发专用worker测试后，继续security-s2d-retention-unit-fixed.log。新执行源已审查并仅追加本项executor/schedule/submit三条到P08活动清单，原条目保留；普通门禁校验，不自动接受其他新活动。当前未进入MySQL或d3b。

d3a组件复验security-s2d-retention-unit-fixed.log **39/39通过、0失败/错误/跳过、BUILD SUCCESS（20:38，1分20秒）**：Retention8＋真实Spring调度1、维护/存储/采集配置6、部署字段/活动清单及模块/架构/平台表/接口门禁。证明停用租户不跳过、每轮最多配置数量、拒绝保留当前游标、单租户失败后公平推进且下一轮重试、坏目录不推进、排队不占许可/执行重查、真实线程不继承上下文、关闭3秒后仍保留在途许可。继续实际MySQL过期/held/容量/回滚/双worker矩阵，d3a整体仍待验收。
