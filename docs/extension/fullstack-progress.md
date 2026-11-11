# P05 后端收尾与新版管理界面实施计划

后续已确认调整：正常页面取消独立登录，改为后台身份自动连接、敏感操作独立验证。当前设计与验证以 [统一入口进度](unified-console-progress.md) 为准；下文独立登录描述为历史阶段记录。

用户已授权完成剩余后端并开发新版前端。任务归属为基座维护；保留当前 main 的并行 P07、规则引擎和柔光 UI 修改。沿用 P05 独立发布安全服务，不改变认证信任边界。新页面位于 frontend-next/src/features/extension，沿用现有组件、主题和双语约定。

## 方案与验收

2026-11-11 菜单归组补充：按用户要求改为“业务扩展”一级目录 → “扩展管理”子菜单，保留 `/extension/manage` 和 `extension:console:view`。修改扩展目录声明/双语标签以及 IAM 内置根菜单归组同步；旧菜单 ID、显式角色授权、管理员开关与自定义文案保留，已有导航分组迁到新目录。目录只承担导航，不自动授予子菜单或发布能力。专项验证新安装、原有授权/导航迁移、重复同步、管理员自定义位置和禁用状态；验证后构建并更新本地后端。

| 顺序 | 菜单归组任务 | 验证 | 状态 |
|---|---|---|---|
| 1 | 声明一级目录与子菜单，兼容旧菜单同步 | PermissionMenuGroupingTests、PermissionTests、RolePermissionTests、架构与模块测试 | 完成；最终合并版本31项通过，浏览器1项通过 |
| 2 | 更新本地服务并核对真实菜单父子关系 | 新包启动健康检查、只读数据库核对 | 完成；本地后端UP，真实父子ID关系已核对 |

菜单首轮验证：`tools/dev/verify-backend.ps1 -Standard -LogPath backend/target/extension-menu-verify.log`，Maven 参数 `-pl backend -am -Dtest=PermissionMenuGroupingTests,PermissionTests,RolePermissionTests,ModularityTests,ArchitectureRulesTests -Dsurefire.failIfNoSpecifiedTests=false -Dsma.verify.directory=target/extension-menu/target`，专用库 `sma_test_extension_acceptance`（UTC连接参数），25项通过并完成打包。并行规则目录修改了同一同步逻辑，部署前对最新合并源码增加 RuleMenuTests、AccountSecurityMenuTests、I18nBundlesTests 复验，证据写入 `extension-menu-final-verify.log`。

菜单前端验证：`npx playwright test --config playwright.extension.config.ts extension.spec.ts` 1项通过，新增目录展开/收起及子菜单路由断言，继续覆盖原发布验证流程；类型检查 `npm run type-check:tests` 与 `npx eslint e2e/extension.spec.ts` 通过。首次定位器与工作标签同名冲突，已限定到 navigation 后复验通过。桌面截图已检查父子层级；仍为模拟HTTP，不代表真实发布服务联调。

最终合并回归：同一标准入口和隔离目录，测试列表增加 `RuleMenuTests,AccountSecurityMenuTests,I18nBundlesTests`，`backend/target/extension-menu-final-verify.log` 显示31项通过、零失败/错误/跳过，BUILD SUCCESS，产物 `backend/target/extension-menu/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar`。本轮仅菜单/架构/语言范围回归，不重述为全量业务验收。

本地更新（2026-11-11 13:42 +08:00）：备份当时运行JAR为 `backend/target/run/before-menu-20261111-134220.jar`，沿用原认证 key-file，更新运行JAR与标准启动脚本使用的 target JAR；新进程32792启动成功，`http://127.0.0.1:8080/actuator/health` 返回UP。只读核对真实库：原子菜单ID `2120383953423667201` 不变，其 parent_id 为新一级目录 `2120391763637047297`（parent_id=0）；原有 enabled/hidden/超管设置保留（更新前已为1/0/1，本轮未改权）。页面标签由双语 nameKey 显示“扩展管理”，保留旧默认名/自定义翻译。并行规则中心菜单亦已归组。运行日志 `backend/target/run/menu-20261111-134220.log`；发布服务部署和生产验收范围不因本次菜单修改而改变。

管理入口复用现有后台壳和独立发布服务 HTTPS 代理；独立发布 session 只存内存，业务 token 不传给安全服务。上传先本地 SHA-256 与限额校验，强化验证绑定精确文件/参数；操作密码与 OTP 不持久化，写入不自动重试，未知结果按幂等键对账。查询/切换丢弃迟到响应，敏感命令同时由服务端判权。

| 顺序 | 任务 | 依赖 | 验证 | 状态 |
|---|---|---|---|---|
| 1 | 控制面查询、范围权限上下文、动作对账与统计查询 | 现有发布服务 | 真实事务/HTTP 拒绝路径、范围隔离 | 已通过专项验证 |
| 2 | 新版管理基础页面：独立登录/注册恢复、分组/插件/制品、上传、发布/灰度/回滚、账号/节点/信任/审计 | 1 | TS、单元、双语、模拟 E2E、明暗/手机页面 | 基础流程已验证；后续阶段补运行/门禁页面及真实联调 |
| 3 | 持久工作异常对账与应用身份公开适配 | 现有账本和公开契约 | 租户/主体/权限、防重复副作用、崩溃恢复 | 框架契约与专项验证通过，真实业务适配器另验 |
| 4 | 可信节点窗口观测、灰度门禁与预授权处置 | 发布协议/遥测 | 过期/缺节点/伪造/重放、阈值与单调修订 | 控制面专项通过，宿主真实多机阶段6验证 |
| 5 | 外部审计锚点、保留/分页/运行调度及部署验收工具 | 1、3、4 | 故障关闭、并发/配额/孤儿引用保护、恢复演练 | 审计/保留主链路与模板通过代码验证；大规模查询/真实运维仍保留验收 |
| 6 | 独立 MySQL/HTTPS/多节点、真实容器与扫描、全量回归和运维文档 | 前述阶段 | 根标准 Maven、前端 verify、真实 E2E、生产构建 | 全后端244项/构建通过，独立MySQL/HTTPS通过；真实容器与生产演练待环境 |

每项验证通过后记录命令/结果再开始下一项，失败先处理。Docker/Grype、真实多节点与故障演练若环境不具备，记录具体缺口，不以 fixture 代替。不能因某阶段通过而缩减完整目标。

## 当前事实

2026-11-11：已读取根/后端/前端 AGENTS 和热插件、升级、数据、二开规则，核对 main 和现有工作树。最新设计为 frontend-next v0.2.0 柔光主题；公共组件和账号安全正在被并行任务修改。旧验证 179 项为排除 RuleFlowTests 的历史结果，本轮不能直接复用为新代码验收。前端现在已获授权，不再适用旧文档的“等待前端框架”。

阶段1：标准 Wrapper `backend/mvnw.cmd -B -f pom.xml -pl extension-security -am test -Dtest=ReleaseWorkflowTests,SecurityHttpTests -Dsurefire.failIfNoSpecifiedTests=false` 成功，日志 backend/target/extension-console-tests.log。新增真实 H2 事务范围隔离/秘密不回传测试与既有发布/HTTP 拒绝路径通过；只验证指定测试，不宣称全 reactor/生产 MySQL 验收。分页响应有界，当前底层文档集合仍先加载过滤，规模优化保留阶段5。

阶段2：`npm run verify` 最终通过（backend/target/extension-frontend-verify.log）：类型与测试类型、lint、双语、全部单元、生产构建及包预算通过，初始144KiB gzip。首次因并行规则组件5项lint失败，未修改该模块，后续其变更到位后完整复验通过。`npx playwright test --config playwright.extension.config.ts` 通过，真实Chrome+模拟HTTP覆盖独立身份不转发宿主令牌、分组强化授权、文件hash/精确文件清单、授权后才分片上传、密钥不落存储、中文桌面/手机深色/英文及AA检查。三项新客户端单元验证身份隔离、超时查原回执不重复提交、上传配额。截图 console-desktop.png / console-mobile-dark.png / console-mobile-english.png；已查看桌面和手机布局，修复登录组件切换时未触发目录加载及页头断开按钮插槽。未验证真实安全服务浏览器登录/真实容器/全量后台生产部署，阶段6保留。

阶段3契约：新增 common::extension 的 ExtensionWorkReconciler（稳定业务适配器核验权威结果，终态前必须对迟到执行 fencing），只允许 PENDING / SUCCEEDED / CANCELLED，不提供未知结果自动重试。账本按原 claim 原子收敛，引用只释放一次，追加 extension V3 保存证据摘要。OpenAPI 沿用原 /open 独立签名认证链和唯一 OpenController 路由例外，通过 ExtensionApplicationGateway 调用；权限取业务 ExtensionApplicationPolicy，空适配器拒绝，不向机器主体赋人类权限。SDK v1 不变，新增公开契约，不修改旧迁移或解除架构规则。新测试使用独立库 sma_test_extension_fullstack、输出 backend/target/extension-fullstack，保留其他开发者测试库和进程。

阶段3证据：extension-reconciliation-tests.log 中安全服务14项、MySQL工作账本4项、运行指标/凭据文件/JOB身份共4项通过；首轮架构及Modulith因输出目录未以target/test-classes结束而把测试类误当生产类。调整为 target/extension-boundaries/target 后，extension-identity-boundary-tests.log 的架构16项+应用授权1项+身份2项通过，extension-modularity-tests.log 模块验证2项通过；未放宽规则。前端再跑 npm run verify 完整通过，43项单元、生产构建与包预算通过。

阶段4方案：完整一分钟宿主窗口（错误/慢调用比率，慢调用>=1s）由NODE身份报送，精确绑定发布修订/候选bundle/时间；缺节点、缺完整窗口、启动不足窗口或样本不足均不可晋升。不自动晋升；自动回滚须通过GUARD_CONFIGURE强化授权预先启用，绑定当前灰度修订、阈值、失效时间和原操作者资格，回滚目标只允许该修订的稳定向量，仍走PREPARE/READY/RELEASE。未提供/已过期/撤销的自动处置授权不能继续使用。

阶段4验证：标准 Wrapper `-pl extension-security -am test -Dtest=ReleaseWorkflowTests,CanaryGuardTests -Dsurefire.failIfNoSpecifiedTests=false` 成功，日志 extension-canary-tests.log。默认初次灰度不超过10%；更高权重须同一候选的健康门禁，晋升须当前修订健康门禁；只有配置automaticRollback才允许自动回退。回滚动作仍重新检查操作者会话/凭据/权限、分组/策略与稳定制品资格，并取得新的节点ready。测试覆盖完整多节点窗口/不足样本、错误和慢调用阈值、WORKER伪装NODE拒绝、同窗口改内容拒绝、健康晋升、自动回滚不会提前切路由。宿主接入完整分钟报告及前端配置/观察入口已写入，随最终reactor与E2E复验。

阶段5验证：extension-control-worker-verify.log 的上游完整verify/打包成功（协议7、runtime8、安全服务23、worker6）。新增AUDITOR独立只读导出与锚点回执、worker audit-mirror 模式：完整批验证后append-only写文件/force落盘，再确认锚点；旧链重写、源锚点变化、断批重启均有测试。可选 release.audit.required 启用后，缺失/过期/积压确认阻断敏感命令和激活，已有租约仍不受此新门禁中断。扫描库更新/任务通过同目录文件锁互斥。MySQL/HTTPS专项里增加制品引用阻止删除、物理清理后才释放配额、删除后不可重校验；全部通过。历史保留新增修正：只计算同环境已发布历史，保护期从最后发布时刻计算，旧记录缺发布时刻时拒绝删除；对应测试随最终回归验证。

阶段6已有证据：extension-mysql-https-tests.log 标准Wrapper专项15项成功，含真实独立 MySQL sma_test_release_https、实际随机端口Tomcat TLS、受信任证书正向与默认信任拒绝、MFA注册/登录/动作授权/幂等HTTP、AUDITOR导出和ack及机器令牌不能访问人类接口。没有关闭证书验证。测试生成短期fixture证书，未修改业务应用/生产库。Docker命令仍不存在，真实镜像/Grype/跨机器网络分区/WORM与灾备验收不等于已完成。

## 最终集成检查（持续更新）

- 前端后续随并行任务升级为 `TableWorkspace`，保留其结构，分组/权限用可展开区域、手机功能选择用紧凑选择器，沿用公共工具区限高滚动，保留表内滚动和分页。局部55%/45%试验导致手机表内可用高度不足150px，已移除局部覆盖，保留新公共样式。`playwright.extension.config.ts` 覆盖扩展流程及 table-workspace 共5项通过；最后扩展专项重验也通过（extension-ui-final-flow.log），包括高度>150px、页内无滚动、分页可见和AA。
- 前端历史完整 `npm run verify` 通过；最终再次运行时，旧版frontend被并行认证/规则任务修改12个文件，baseline检查失败，未修改基线hash掩盖。随后分别执行 type-check、type-check:tests、lint、i18n:check、test:unit、build、release:check 全通过，最后微调后的最新52项单元、初始149KiB gzip，日志 extension-frontend-final-checks.log。这是绕过入口中失败基线后的分项实际结果，**不是最终 npm run verify 无例外通过**。
- 后端标准全量首轮使用独立测试库但URL遗漏UTC参数，导致认证会话/时区相关失败；这是本次命令配置错误，保留日志 extension-final-fullstack-verify.log。另建 sma_test_extension_acceptance，恢复 connectionTimeZone=UTC 与 forceConnectionTimeZoneToSession=true，用根标准脚本、不排除测试重新verify，**最终BUILD SUCCESS，244项全通过、0失败/0错误/0跳过**：协议7、runtime8、安全服务26（含真实MySQL/TLS）、worker6、业务宿主197。日志 extension-final-utc-verify.log；全模块打包成功。最后一次代码验证未排除RuleFlowTests或任何其他测试。
- 新增候选有在途时不报告完整窗口、同窗口报告不可变测试，extension-latest-boundaries.log 的24项（架构16、Modulith2、应用1、遥测2、凭据1、身份2）通过并打包宿主。新契约不依赖具体客户代码。
- 生产前置预检 `tools/dev/check-extension-prerequisites.ps1` 实测失败：容器引擎缺失，Java/Grype固定镜像未配置。报告 backend/target/extension-prerequisites.json，未安装容器、下载镜像、运行真实扫描或设置计划任务。systemd服务/定时器是可审阅模板，不是已部署状态。

## 交付与剩余边界

页面 `/extension/manage`，菜单由宿主 `extension:console:view` 贡献，默认不自动授权。需部署本轮同批宿主/安全服务/worker以及frontend-next静态包，并配置独立发布服务代理和HTTPS Origin；当前运行的业务服务未自动重启，所以不能宣称所有新功能已在原服务上启用。使用流程见 [console-user-guide.md](console-user-guide.md)，部署/门禁/镜像/恢复见 [deploy/README.md](deploy/README.md)，机器可读证据 [fullstack-verification-2026-11-11.json](fullstack-verification-2026-11-11.json)。

剩余范围不是“已完成”：真实Docker/Grype镜像/schema及隔离执行，真实多机网络分区和切流，持续装卸/压力与磁盘耗尽，外部WORM/灾备、真实业务fencing/幂等适配、真实浏览器对安全服务的端到端部署验收。目录查询已提供有界分页/范围过滤，但低频安全库部分集合仍先加载再筛选，规模化索引优化保留；孤儿引用默认保留，需要业务权威对账后回收，未提供危险的强清入口。前端最终总verify仍受并行旧前端baseline变动阻断，分项结果与该入口状态分别记录。

用户已收到真实容器验收环境选择问题；未选择/提供环境前不假定获准安装Docker、修改系统虚拟化或连接未知服务器。本轮无代码提交、无生产发布、无业务应用重启；使用独立专用测试库与受保护临时fixture证书用于验证，不包含生产数据。
