# 我的随手栏：设计与实施进度

2026-11-12安全专项前端回归修复：原右侧中部悬浮入口在默认1280×720窗口遮住“回收/重试”等表格按钮，浏览器鼠标点击确认被入口拦截。将原入口移入AdminLayout顶部工具栏正常布局，保持抽屉/浮窗/固定栏状态和数据协议；工具栏z40、浮窗z30、弹窗z50。原9项IAM/operations鼠标回归全部通过，随手栏专项4/4通过，含320px无横向溢出、明暗/英文、键盘与浮窗恢复。未改成强制点击或通过放大窗口规避问题，未重启用户服务。

用户已确认常用页面标签与个人便签都需要，并授权开始实施。本项归属基座，主线 main；当前工作区已有大量并行修改，禁止覆盖或回退。

已完成实现与验收。后端以固定源码副本执行完整根 reactor，前端以当前源码执行完整门禁及浏览器专项；实际边界见下方最终记录。现有用户业务服务未重启或部署，使用方式见[使用说明](personal-shelf-user-guide.md)，逐条需求见[验收对照](personal-shelf-acceptance.md)。

## 范围与路径

- 后端复用 `backend/src/main/java/com/sma/admin/workspace/`，新增个人标签、便签、设置服务和 `/api/workspace/shelf/**`。通过 IAM 的 AccountQuery / PermissionQuery 公开面解析授权菜单，不跨模块读表。
- 追加 `backend/src/main/resources/db/migration/workspace/V1__personal_shelf.sql`，独立拥有 workspace_shelf、workspace_bookmark、workspace_note。现有用户偏好重置不删除收藏或便签。模块新增 common persistence 与 IAM 依赖，不改变认证链。
- 前端新增 `frontend-next/src/features/workspace/`，接入 `layouts/AdminLayout.vue`、`components/business/PageHeader.vue` 及工作页签操作；新增双语词条。旧前端不新增入口，既有协议保持兼容。
- 专项测试在 backend workspace 测试与 frontend-next 单元/E2E 测试；本文件记录实测，README 和相关 AGENTS 同批同步。

## 确定的产品规则

常用标签最多30项，稳定菜单ID去重，首页为内置入口；保存别名和顺序，实时解析菜单名和权限，失效不泄露原内容。关闭工作页签与取消收藏互不影响。标签不保存临时查询参数。

便签支持纯文本/清单，标题50字、正文10000字、清单100项，未归档便签200张；列表置顶、搜索、复制、关联页面、归档、回收站30天恢复。列表置顶和悬浮显示独立；关闭悬浮不删除。最多3张独立悬浮便签。

右侧抽屉为默认，支持悬浮小面板和宽屏固定侧栏，窄屏退回抽屉。窗口拖动、缩放、折叠与恢复位置按设备及身份保存；正文不进 localStorage。锁屏隐藏正文，账号/租户/portal变更丢弃上一身份内存及迟到响应。

后端所有读写绑定可信 tenant/user/portal。按所有者串行化容量和列表写入，便签版本冲突返回409，保留客户端内容并支持采用远端/另存。自动保存约1秒，失败不静默重试写入；不承诺离线编辑。服务端使用请求键使新建便签可查询确认，超时先核对结果。

## 按依赖实施及验收

| 顺序 | 任务 | 验证方法 | 状态 |
|---|---|---|---|
| S1 | 数据、接口、菜单授权、版本冲突、回收站 | 专用MySQL库 ShelfTests；ModularityTests / ArchitectureRulesTests；迁移重跑及已有数据保留 | 完成 |
| S2 | 抽屉、标签、添加入口、排序与冲突提示 | 前端单测及专用E2E，校验身份切换/失败/失效入口/重复收藏 | 完成 |
| S3 | 文字/清单便签、自动保存、归档回收与多端冲突 | 前端单测及E2E，保存竞态/失败保留/离开保护/过期响应 | 完成 |
| S4 | 悬浮、固定侧栏、拖动缩放、3张上限、窄屏 | 浏览器真实交互、明暗/中英/窄屏截图及键盘测试 | 完成 |
| S5 | 全量门禁、文档与交付审计 | 根 verify-backend.ps1 -Standard；frontend-next npm run verify；专项E2E；逐条需求审计 | 完成 |

## 实测记录

- **用户授权部署重启（2026-11-11 20:09～20:12）**：保存旧运行JAR、旧默认构建和业务库一致性SQL备份至 `backend/target/restart-20261111-200930/`（仅当前Windows用户可读写），保留原进程运行环境及认证密钥。确认既有迁移未修改/删除后，将已通过全量验收的固定副本JAR部署到8080；新增迁移完成，workspace V1成功，现有用户数仍为2。后端PID42272、新版Vite前端PID14676（5174），直连与前端代理健康均UP，前端页面与随手栏模块HTTP200，未登录随手栏/便签接口401。已同步默认启动JAR以避免下次脚本回退旧包。未使用用户账号执行登录或业务写入，登录后的功能仍以此前隔离真实验收为证据。

下列记录按收尾结果与历史轮次保留；历史失败均说明了原因与后续处理，不代表当前功能仍未完成。

- **最终后端门禁通过**：2026-11-11 19:59，固定副本根 `tools/dev/verify-backend.ps1 -Standard`，未排除测试，全部 reactor 模块 BUILD SUCCESS。共574项：537通过、0失败、0错误、37项按既有条件跳过（Redis、双数据源、发布服务真实MySQL/TLS等可选外部专项，不计入通过）。宿主521项中485通过/36跳过，随手栏9/9通过；日志 `backend/target/shelf-snapshot-final.log`。冻结副本及SHA-256清单保留在 `backend/target/shelf-verification-snapshot`，后端业务逻辑不变，仅补入当前工作区已完成的8行缓存任务双语文案。
- **最终前端门禁通过**：`npm run verify`，36文件94项单测、类型/测试类型、ESLint、12模块双语校验、构建/产物预算通过；最新日志 `frontend-next/.e2e-runtime/shelf-verify-navigation-final.log`。专项模拟4/4、隔离真实后端1/1、管理壳/表格/可访问性9/9。真实验收的持久化/隔离流程与后续新增退出/导航保护分别由对应真实/模拟用例证明，不混称为同一产物的完整真实验收。
- **交付审计通过**：main分支；最终25个前端源码与其验证清单、16个后端随手栏文件与后端副本哈希一致。需求对照表已逐项核对。并行任务在冻结后增加的其他能力不计入本次验证范围，原工作区并行修改均保留；未创建提交、未重启现有业务服务。
- **临时环境收尾**：真实E2E的8099后端、5199前端及临时schema/用户已由其脚本清理，result.json为passed=true、credentialsPersisted=false；核对3319监听进程确实使用本任务datadir后已发送正常SHUTDOWN。测试日志、快照与测试数据文件保留为验证证据。
- 收尾复核：3319、8099、5199、5198均无监听，本任务MySQL的39052/31096进程均已退出；未停止其他服务。

- S5冻结副本首轮：宿主521项中仅语言完整性1项失败，0错误、36可选环境跳过；随手栏9项通过，其余模块通过。冻结时缓存任务错误码先于其文案落盘；主工作区19:31已补齐4个common.cache-task键的中英文。仅将这8行现有文案同步至副本并更新manifest中的两个资源哈希，不同步其他并行Java/迁移/测试变化。完整根标准复验已启动，日志 `backend/target/shelf-snapshot-final.log`；首轮日志 `backend/target/shelf-snapshot-full.log` 保留。

- S5标签切换补验：通过管理壳提供的导航回调切换已打开工作页签，保留其现有筛选query；收藏记录仍只保存稳定菜单ID。重复导航按已选中处理，导航被未保存保护拒绝时保持面板。四项专项4/4通过（46.5s），完整前端verify94项通过，最新日志 `frontend-next/.e2e-runtime/shelf-verify-navigation-final.log`。25个最终前端源码哈希见 `frontend-next/.e2e-runtime/shelf-final-source-manifest.json`；这些前端补验独立于后端冻结副本。

- S5最终前端结果：最新完整verify为36文件94项单测全通过，日志 `frontend-next/.e2e-runtime/shelf-verify-final-audit.log`；最新模拟浏览器4/4（44.7s），覆盖新增锁屏退出确认和空清单项校验恢复。源码哈希复核34个随手栏/集成文件与固定副本一致。直接依赖的HTTP客户端、auth/preferences/router、CurrentUser/SmaPrincipal/PermitLoggedIn/ActorContext、持久化配置及IAM公开查询均一致；并行安全任务在快照后给RequestGovernanceHttpTests追加冷却测试，该新增范围不计入本次快照验收，未覆盖其修改。

- S5后端第二轮：宿主501项仅I18nBundlesTests 1项失败、0错误、36可选外部环境跳过；之前请求治理/缓存治理/租户目录失败项均通过。原因是并行身份模块19:13新增错误码，19:15补翻译，资源复制与编译跨越该窗口。当前源文件翻译已齐全。为保证一次完整一致构建，复制main当前已跟踪与未忽略文件到 `backend/target/shelf-verification-snapshot`，经SHA-256复核得到稳定的2025文件清单，记录 `shelf-source-manifest.json`。最终根标准全量验证在此副本执行，专用3319库，日志 `backend/target/shelf-snapshot-full.log`；用户工作区和其他任务可继续修改，交付前会核对本功能源文件与清单相同。
- S5前端最终审计：修复清单空项校验后自动保存不恢复的问题，网络失败仍停止自动重试。新增逻辑用例后94项单测通过，四项浏览器专项4/4通过（44.7s），包含暂留空项再补全自动保存恢复。最新完整verify日志 `frontend-next/.e2e-runtime/shelf-verify-final-audit.log`。

- S5交付审计补漏：SecurityControls新增可选beforeExit回调，由管理壳在锁屏退出时提示未保存便签、普通退出/修改密码前flush；不改变认证接口。新增浏览器用例证明取消保留会话、确认后才发送logout，1/1通过。最终前端门禁/四项专项正在复验，日志 `frontend-next/.e2e-runtime/shelf-verify-exit-guard.log`。此前真实后端验收证明持久化/隔离；新增退出保护由模拟浏览器专项验证。

- S5后端首轮全量：根标准脚本未排除任何测试；宿主491项，4失败/2错误/36可选外部环境跳过，随手栏9项仍通过。失败位于并行请求治理/缓存治理夹具，以及临时MySQL默认连接上限。当前请求治理Advisor的ROLE_INFRASTRUCTURE修复已由并行工作落盘（晚于首轮编译）；本任务将RequestGovernanceHttpTests断言限定为精确的非内置参数组，将CacheGovernanceTests审计spy定位到事务代理内部对象，并在HTTP请求使用Users.request明确租户域名。未改生产授权/事务边界或削弱回执断言。临时3319实例max_connections调为400，承载全量测试缓存的多个Spring上下文；不改用户运行实例。首轮日志 `backend/target/shelf-full.log`，修复后完整标准复验 `backend/target/shelf-full-final.log` 进行中。

- S5补充回归：原有softlight-shell/table-workspace/accessibility套件9/9通过（2.0m），未复用用户页面。表格缩放用例期间Vite记录1条ResizeObserver loop通知，断言与其他页面均通过；本功能没有新增ResizeObserver。新增真实测试的测试类型与专项ESLint通过，文档相对链接及本任务diff空白检查通过。
- 真实验收清理已确认：运行句柄正常exit 0，运行目录result.json为passed=true、credentialsPersisted=false，8099/5199无监听。3319为本任务专用MySQL，仍供后端全量门禁使用，待结束后关闭。

- S5前端完整门禁：`npm run verify` 通过，36个文件/93项单测、12模块中英key/占位符校验、类型与测试类型、ESLint、构建和release预算全部通过。最终日志 `frontend-next/.e2e-runtime/shelf-verify-final.log`，入口154KiB gzip，34懒加载模块在预算内。补充迟到读取防覆盖用例已包含。
- S5模拟专项：最终3/3通过，结果 `frontend-next/test-results-shelf/results.json`，包含弹窗z层级与锁屏正文DOM移除；记录截图明暗/中英/窄屏/三便签。
- S5真实专项：`SMA_E2E_BACKEND_PORT=8099 SMA_E2E_FRONTEND_PORT=5199 SMA_E2E_MYSQL_PORT=3319 SMA_E2E_STATIC=1 node scripts/run-real-e2e.mjs personal-shelf.spec.ts`，静态产物+隔离Spring/MySQL，1/1通过（13.6s）。证明真实自动保存、清单勾选、Long字符串、旧版本409、另一账号读写拒绝、全新浏览器账号恢复、浮窗刷新恢复；结果 `frontend-next/test-results-shelf-real/results.json`。运行目录 `.e2e-runtime/1794394584133`，清理仍由原运行句柄执行中。

- S4：几何与状态单测10/10，浮窗浏览器专项1/1；验证鼠标吸边、键盘缩放、48px折叠、固定侧栏让出380px、320px退回抽屉、3张上限、刷新恢复、正文不持久化和英文深色axe AA无违规。检查floating-light/docked-dark/narrow-dark/drawer-english-dark截图，位于test-results-shelf。修正了悬浮便签挡住右侧入口的问题（入口z40、浮窗z30，均低于业务弹窗z50）。S5全量门禁开始。

- S3：9/9前端专项单测通过（新增5项便签竞态、响应丢失核对、失败停重试、冲突副本、身份/锁屏）；两项浏览器流程2/2，完整覆盖文字转清单、自动保存、失败时关闭拦截、冲突另存、归档/回收恢复、正文不进浏览器存储。类型/测试类型/专项lint通过；完成项折叠与用户时区更新时间已补齐。开始S4。

- S2：type-check、type-check:tests、专项ESLint通过；shelf.test.ts 4/4，独立5198端口playwright.shelf.config.ts 1/1（添加、排序、冲突保留别名、撤销、刷新恢复、关闭工作标签独立、320px、失效入口）。结果 `frontend-next/test-results-shelf/results.json`。开始S3。

- S1最终通过：2026-11-11 18:30，独立3319实例，根标准脚本指定 ShelfTests,ModularityTests,ArchitectureRulesTests，27/27通过、0跳过，BUILD SUCCESS。日志 `backend/target/shelf-s1-consistent.log`。包含9项随手栏真实MySQL用例，菜单30项上限、改名/撤权、隔离、并发冲突、回收恢复/过期、容量、新建去重、HTTP认证与no-store、迁移重跑。开始S2。

- 初始检查：当前 main，相关功能尚未实现；既有工作页签、未保存保护、认证及租户平台均在并行演进中。未重启用户服务或修改现有数据。
- S1 首轮：独立库 `sma_test_shelf`，根标准验证脚本以 `-pl backend -am -Dtest=ShelfTests,ModularityTests,ArchitectureRulesTests -Dsurefire.failIfNoSpecifiedTests=false -Dsma.verify.directory=target/shelf/target` 执行；25/25通过（随手栏7、架构16、Modulith2），日志 `backend/target/shelf-s1.log`。这是首版验证，不代替后续补充代码的验证。
- S1 补充轮：增加菜单改名/失效/30项上限、私有HTTP响应、迁移重跑及过期物理清理。架构18项通过；9个随手栏测试中2项测试准备失败：HTTP模拟身份租户与入口不匹配，菜单测试依赖不存在的预置管理员。已经改为入口租户身份与自主创建的测试账号；重跑中，S1尚未标记完成。原日志 `backend/target/shelf-s1-final.log`，复验日志 `backend/target/shelf-s1-recheck.log`。
- S1 升级形状检查：独立 `sma_test_shelf_upgrade_20261111` 应用 config V3/V4，插入旧偏好，再执行 workspace V1。返回 `dark / iris / tabs_enabled=1 / keep_tabs_after_reload=1`，workspace表数3；原偏好保留。未迁移用户运行库。
- S1当时记录：前端与浮窗尚未实现，S2等待S1专项验证通过后启动；后续完成情况见表格和新增实测记录。
- S1复验：9项中8通过，剩余容量用例假设预置菜单至少30项但实际为27项；改为用例自行创建30项测试菜单，生产上限仍为30。HTTP缓存头和迁移重跑用例已通过。
- 本任务临时MySQL实例：127.0.0.1:3319，数据目录 `backend/target/shelf/mysql`，启动包装进程31096、实际监听进程39052（使用前须重查）。独立可清空库 `sma_test_shelf`，仅用于验证；采用测试实例的非逐事务刷盘配置，不作为崩溃持久性证据。原3306实例未改参数。
- `shelf-s1-isolated.log`：架构18项通过，应用上下文启动失败，原因是并行缓存模块在18:15:45新增CacheRevisionEntity字段与common V5迁移，Maven资源复制发生在之前、Java编译在之后，产物缺V5但有新字段，报unknown control_version。源码已齐全，重新构建执行中，日志 `backend/target/shelf-s1-consistent.log`；未手工补列或覆盖其他任务修改。
