# 新版管理前端规则

C4a3a本人凭据共用shared-credential-intents：同步占位后取得Web Locks，再确认/发送；同一scope的提交与原键核对共享锁。localStorage的sma.credential-intent.v2.<scope>仅保存schema、会话SHA-256摘要、原键、UNKNOWN（每条≤512字符、最多256条），不得保存密码/OTP/挑战/QR/token；全局短事务只包容量检查/落盘，不包网络。旧sessionStorage v1仅匹配scope时先落共享标记再删除旧标记，冲突/损坏拒发；迁移忙可随后显式查询。新登录是新scope，不清旧UNKNOWN或冒充旧写取消。直接终态/匹配LOCAL回执才释放，settings/刷新不能解除。身份/锁屏变化清密并隔离旧响应，等待授锁时失效必须释放迟到锁；本地QR失败不制造服务器UNKNOWN。回归shared-credential-intents/account-security/security-controls、security credential-coordination/account-security及原锁屏工作流、完整verify。会话锁仍独立，a3b规则见下；a3c与S1d另验。

C3d1认证请求在真正发送前用RequestOptions.expectedIdentity比对捕获的会话（匿名为null），值仅内存，不发自造认证头、不持久化。auth store登录/OTP同步门禁，reset推进代次，旧finally/退出/权限读取成功或失败不能改新身份；MFA未知结果清挑战，不复用OTP。页面离开清密并丢弃迟到导航，正常MFA交接保留；logout返回false时不得导航新会话。后端认证和密码规则不改，异步确认前意图保护及账号安全/锁屏仍按C3d2验收。回归auth-mfa/http单元、security authentication/protocol-compat浏览器和完整verify，不把API夹具当真实后端身份验证。

P08节点选择固定node＋process，缓存目录/概览/策略/预览带定向头，不能随名册更新悄悄绑定新进程；切换清旧结果、迟到响应丢弃、错路由/旧进程拒绝不回退随机节点。编辑策略或预览期间保护目标和草稿，写操作捕获目标并保留原请求键。任务提交/历史回执走原持久协议，可不定向查询；当前路由拒绝不能用于释放历史UNKNOWN。页头跨标签显示选定目标，统计不跨节点累加。验证http/cache单元、playwright.cache.config.ts生产包及隔离run-cluster-e2e的SMA_CLUSTER_ROUTING=1；实际LB资格见M3，不以mock路由当部署验收。

P08隔离双JVM脚本的SMA_CLUSTER_TRAFFIC=1要求STORAGE/EVIDENCE/SCHEMA_MIGRATION/M2全部启用，验证实际资格故障节点的直接业务拒绝、liveness与健康peer原会话。503不释放历史UNKNOWN、不自动重发业务写；脚本仅读取轮询可重试，故障注入和清理仅限本轮资源。此开关仍用single模式，不宣称生产LB/多主机挂载/WS排空已经验收。修改runner执行语法、ESLint、完整verify及对应真实矩阵，证据范围见[P08进度](../docs/cache-governance-progress.md)。

traffic负例必须用当前PID的固定日志状态证明已离开NOT_READY，再一次性检查写入口拒绝；不能把应用尚未完成启动的503当作配置故障隔离。SMA_CLUSTER_TRAFFIC_ONLY仅允许引用同JAR且已通过/清理的完整基线结果，输出明确标记聚焦范围并保存引用哈希；不能以聚焦23组替代原完整业务场景。失败采样仅限有界状态/身份，不输出token/正文/密钥/路径或原异常，保持只读诊断与清理范围。

安全C3c4通用上传使用useFileUpload/UploadButton并显式传scope：文件库、头像当前选择、品牌槽位/草稿版本、公告表单实例不能共用笼统upload锁。续传仅限ACCEPTED且resumable，发送前再查原键，使用当前内存中原File及原imageOnly；RUNNING/UNKNOWN禁止重传。刷新/锁屏/离开后不得从元数据重建文件，只有授权查询；完成文件仍按当前ACL读取。上下文切换丢弃迟到结果，关闭重开新公告用新实例scope；头像/公告上传中阻止保存旧附件输入。UploadButton自加载file语言包，品牌保留受限图片验证。回归file-upload/submission/operation-registry单元、security submission与tenant management浏览器、完整verify；P09 upload-tasks和跨标签C4另行验收。

规则写入口使用useRuleTask.command；执行/用例运行/批次等不能误当无副作用查询，后端明确QUERY的validate/simulations/upgrade-preview另按只读处理（simulations不保存执行记录）。确认前预留，cloneExact固定输入，原operationKey与expectedRevision保留。rule.assets同对象各面板共享锁；普通刷新不能清除UNKNOWN。原生模板发布/批次按原键GET核对，RECOVERY_REQUIRED不重跑成功项；完成回执不能作为Asset/Execution。直接运行资源清理只有统计观察而无持久完成证据，不得凭统计释放未知。回归rule task单元及authoring/instances/releases/operations浏览器，保留精确数值与共享恢复入口唯一性。

消息写入遵守C3b：批量IDs/模式在确认前固定，resource集合与单对象锁共享；全部已读使用显式集合锁。完成回执后按授权GET读取详情，不重发写操作获取正文。重复已读点击不使首个合法详情失效。富文本/teleport选择器/附件需显式disabled，fieldset不能代替；禁用期间不得追加迟到附件。回归submission和IAM/operations；消息中心嵌套工具区应给表格保留可点击空间，不用强制点击掩盖遮挡。

配置/IAM提交范围：PreferencesPage和DisplaySettings共用`config.preferences.self`；角色通用编辑、授权和删除共用`iam.roles.<id>`，不能按按钮名称拆锁绕开冲突。未决表单保持原输入，查询原结果前不得以新编辑覆盖其语义；正文和密码仅在内存，锁屏清除密码。状态未知可以关闭/查询，不应把弹窗取消和恢复入口同时置为busy。回归submission中的配置/角色/密码/时区场景与IAM/operations原回归。

安全专项公共交互：FilterBar文本输入300ms防抖，组合输入期间不提交，显式搜索取消计时器。useLatestRequest仅用于读操作，必须把signal传给API并用完整参数/语言键合并；写后刷新先cancel旧读，防止复用写入前结果。useSubmission先取得原意图再异步确认/校验，UNKNOWN保留键并提供查询；通用ResourceEditor/ResourcePage和UploadButton已接入，其余页面按C3清单推进。随手栏入口放顶部工具栏正常布局，禁止重新用悬浮入口覆盖业务按钮。回归submission浏览器、IAM/operations和personal-shelf专项；移动入口不改变随手栏数据/布局状态。

请求治理基础库见`lib/operation-registry.ts`及[客户端契约](../docs/security-request-client-contract.md)。先reserve再异步确认/校验；execute只接捕获的固定输入，同一次LOCAL明确重试保留ticket/key和原闭包，不用新键绕过未知结果。NATIVE须显式解释原生任务状态；通用完成回执不能当业务DTO。操作记录只含可信owner、操作/对象、键、任务/回执ID及状态，密码/token/正文/文件不得持久化；结果查询同样校验会话及失效代次。存储/容量故障不得丢掉未知意图后恢复READY。页面覆盖及浏览器验收状态见前端安全进度，基础单元通过不等于全站已接入。修改回归operation-protocol/operation-registry及受影响页面测试。

P10入口协议、portal和代理边界统一遵守[租户平台规则](../docs/agent-rules/tenant-platform.md)。本工程消费者位于stores/branding和views/LoginView；验证branding单测、`playwright.tenant.config.ts`（5197/5198/5199）、隔离`e2e-real/tenant-entry.spec.ts`（SMA_E2E_LEGACY=1）及完整verify，交付状态见[P10进度](../docs/tenant-platform-progress.md)。

安全专项C3c2：品牌草稿与租户目录本地写只按LOCAL原键确认后授权重读；品牌发布/回退及域名验证使用注册表原键作为原生operationId并核对回执对象/编号/状态。确认前预留，关闭弹窗或刷新不能清除未决记录；NOT_OBSERVED/404不释放。回归tenant management/directory十项浏览器与tenant单元，不能用当前内容匹配代替原操作证据。

P10租户/域名管理位于features/tenant的TenantsPage、DomainsPage与DomainDetails。目录登记不等于业务开通；验证成功不自动启用。各写操作按当前权限与版本提交，未知结果只核对原操作/当前对象，不自动换键重发；锁屏隐藏详情并保留待核对编号，退出或确认身份切换清理本地状态。品牌入口刷新短暂entry=null不能当作真实租户变化清空列表/表单；只有不同的已确认范围才失效。Long版本用字符串/BigInt比较。验证directory与directory-session单测、e2e-tenant/directory.spec.ts及隔离e2e-real/tenant-directory.spec.ts；真实runner的SMA_E2E_PLATFORM_OPERATOR=1只授权该次正常引导生成的临时账号，并重启其自己的子进程。范围、权限及后端权威约束仍见租户平台规则。

安全专项C3c1工作台使用LOCAL原键恢复且保留原requestKey/version；NOT_FOUND或远端列表无对象不能解锁UNKNOWN并重建。保存中允许继续编辑，成功后才提交下一份输入；状态转换在等待保存前取得门禁。完成回执不能当Board/PersonalNote，必须授权重读。回归notes/shelf单元及playwright.shelf.config.ts。

个人随手栏由 `features/workspace/` 与管理壳接入：标签使用服务端稳定菜单ID，不保存任意URL参数；便签正文和搜索词仅在内存，布局按服务端scope隔离持久化。身份切换清空状态和迟到响应，锁屏隐藏正文并停止自动保存；不得因样式刷新清空同身份编辑。保存失败保留内容，不自动重试写入；未知结果先读回核对，版本冲突提供远端/另存。浮窗z30、入口z40，均低于业务弹窗，窄屏退回抽屉；归档/删除/关闭浮窗语义独立。验证workspace单测、`playwright.shelf.config.ts`、隔离`e2e-real/personal-shelf.spec.ts`和`npm run verify`；详见[进度](../docs/personal-shelf-progress.md)。

安全专项：外部链接/本地跳转复用 `lib/web-addresses.ts`，富文本仅通过 `SafeHtml`/`sanitizeRichHtml`，不能新增直接解释未经清洗数据的 HTML。清洗器不可用时降级纯文本；DOMPurify 不支持 happy-dom 安全解析，富文本攻击回归必须用真实浏览器 `npx playwright test --config playwright.security.config.ts`，生产包另用 `playwright.security-production.config.ts`（先 build），专用5196/4196端口不复用用户页面。页面CSP当前为Report-Only，生产静态服务需部署对应响应头，不能把API头或开发验证当成生产强制CSP。

本文件补充[总规则](../AGENTS.md)。本工程继续使用 `frontend-next/` 名称，独立于 `frontend/`，复用后端已有协议。依赖按本工程 lockfile 使用 `npm ci`，不为迁移引入另一套 UI 或复制旧版源码、登录凭据。

- UI 原语留在 `src/components/ui/`，可复用页面组合优先用 `src/components/business/`。基座页面在 `src/features/`；二开在业务分支使用同一个前端工程，页面、业务 API 调用与私有逻辑放 `src/business/<bizid>/`。在现有路由、菜单和权限体系中显式登记业务入口。见[二开目录](../docs/agent-rules/secondary-development.md#二开目录)。
- API 走既有客户端，复用已有会话、菜单、权限、分页和错误约定。菜单/按钮可见性不代替服务端授权。Long ID 保持字符串。请求切换时丢弃过期响应；写入超时先查结果，不自动重试非幂等请求。
- 权限资源列表的类型、状态与关键词按交集筛选，重置清除全部条件；执行筛选时展开结果，匹配子节点不能因父级被筛掉或原先折叠而消失。ResourcePage 的树形列表提供全部展开/收起，作用于当前筛选结果的所有分支（含隐藏的深层分支），不得清空筛选或触发写请求；普通列表不显示按钮，无可操作分支或加载时禁用。筛选后允许用户主动再次收起。当前接口返回完整列表，允许前端过滤；若未来改为分页接口，必须同步提供服务端筛选，禁止只过滤当前页冒充完整结果。回归入口：`npx playwright test e2e/iam.spec.ts e2e/table-workspace.spec.ts`。
- 可见文本和 `aria-label` 等使用 zh-CN/en-US；颜色用主题变量并覆盖明暗主题。时间以 UTC/带偏移值传输，显示按用户 IANA 时区；前端存储按用户与功能隔离，不保存敏感输入或把它放入 URL。
- 在本目录运行 `npm run verify`；涉及真实行为时按 [README](README.md) 运行对应的 `npm run test:e2e` 或 `npm run test:real`，两者占用同一前端端口，不并行启动。发布静态包前检查路由 base、API/WS 代理、生产产物与旧包回退。详见[新版技术决策](../docs/frontend-next/technical-decisions.md)。
- E2E的临时脚本、截图和结果放 `.e2e-runtime/` 或 `test-results-*`，与dist/coverage一样作为生成物排除ESLint；禁止把产品源码或需维护的测试移入这些目录绕过检查。验证入口仍为 `npm run verify`。

## 柔光主题与管理壳

- 快捷标签、可调双栏和收藏布局复用 `QuickTabMenu`、`ResizablePanels` 与 workspace/bookmark-layout。快捷目标只取服务端授权 targets，点击前重读并核对同一身份，走 shelfNavigate/现有路由守卫；禁止把示例菜单或任意 URL 直接接入。双栏按实际容器限制尺寸、提供键盘/触屏与窄屏堆叠，不能通过布局 key 重建表单或清草稿；ResizeObserver 延迟到下一帧，销毁时释放观察器/捕获/动画帧。收藏视图只按服务端 scope 保存 list/cards，正文/别名草稿/搜索不持久化；锁屏/身份隔离仍按随手栏协议。LeiBox 原包只在 docs/frontend-next/leibox-reference 留存来源，不得将其演示 HTML/SVG/网络脚本直接注入产品。验证 `playwright.leibox.config.ts`（5216，SMA_LEIBOX_STATIC=1验证已构建包）、workspace 单元、原 shelf 专项及完整 verify；不将模拟UI当真实后端验收。

安全专项C3c3a缓存任务按原预览/原键恢复；QUEUED/RUNNING不当完成，PARTIAL含UNKNOWN子项继续保留未知。内部LOCAL入队回执不是原生任务编号；取消请求独立于原提交，以允许取消在途任务。策略LOCAL原输入/version保持，回执后重读；预览不能自行入队。回归cache/operation-registry单元、cache-governance浏览器（含刷新/取消）。

P08缓存管理保留`/monitor/caches`，协议与状态机在`features/monitor/cache.ts`。Long版本/ID保持字符串，计数未知不当零，共享条目不跨节点重复累加；逻辑完成、历史节点确认与当前在线分别展示。一次提交绑定原预览ID和Idempotency-Key，未知结果先读回，禁止自动换键重发；身份改变或页面离开丢弃迟到响应并清理操作草稿。业务读取权限、区域管理权和操作权仍由服务端检查。阶段进度见[缓存治理进度](../docs/cache-governance-progress.md)，验证cache.test.ts、完整verify及后续隔离真实任务/策略联调，不把协议单测当页面验收。

原生运行对象预览必须显示绑定节点/进程；UNKNOWN不提供自动重试或伪装失败未执行。纯原生及预热不显示全节点失效完成，混合报告仅覆盖DATA；计数不宣称物理内存回收。验证`playwright.cache.config.ts`及隔离`e2e-real/cache-governance.spec.ts`（原生验收显式开启部署开关），保留旧载荷可选字段兼容。

- 统一搜索只保留顶部入口与Ctrl/⌘K，侧栏/移动导航不重复设置输入区。菜单搜索覆盖当前用户全部授权菜单，不受导航分组限制。全局查询接入 `lib/search.ts` 的 SearchProvider，复用既有授权API与当前会话；无权来源不请求、迟到响应丢弃、关闭/锁屏/换账号取消并清空，不持久化关键词。不得用当前页过滤冒充全局搜索，不展示参数值/凭据，不将搜索词附到目标URL。未来semantic提供者须明确启用并经过服务端租户/权限过滤，不能在前端自动上传全文给AI。验证 `search.test.ts`、`e2e/unified-search.spec.ts`、隔离 `e2e-real/unified-search.spec.ts` 以及 `npm run verify`。

- 权限/导航图标统一使用 `lib/icons` 的本地目录、`AppIcon` 与 `IconPicker`，禁止将数据库字符串解释为HTML或外站资源。图标ID≤64字符，支持lucide:/3d:、旧别名、none（明确无图标）；未知值保留并降级，不能因编辑其他字段误清空。权限按钮使用 `PermissionButton :permission`，只读取auth/me的已授权permissionIcons；权限指令继续只管授权，不注入DOM。扩展独立发布账号的授权动作不映射成宿主权限按钮。新增素材必须包含来源/许可证/哈希并用本地资源，3D图标保持透明背景。验证：icons.test.ts、permission-button.test.ts、e2e/icons.spec.ts及隔离e2e-real/icons.spec.ts；扩库仍遵守release预算。

适用于所有基座及二开页面，默认遵守；例外必须说明产品目的并覆盖明暗/窄屏验证。

- 默认皮肤为鸢尾紫（iris），可选青瓷绿（celadon）和琥珀棕（amber）；skin 与 light/dark/system 主题独立，不合并编码。右上角独立入口与个人偏好页使用同一服务端偏好，失败回滚；旧响应缺 skin 按 iris 处理，不继承上一账号。颜色、玻璃/柔光与阴影使用 `styles/theme.css` 的语义变量；浅色主功能区固定 #f6f7fa，高频数据表格及表单正文保持实色。禁止皮肤切换改变业务权限或布局尺寸。验证入口：`e2e/skin-theme.spec.ts`、`e2e-real/skin-theme.spec.ts` 与 `npm run verify`；真实测试必须使用隔离环境。
- 管理壳顶部、工作标签栏、内容背景使用 `--shell-*` 语义变量；浅色主功能区使用原浅灰 `--background`（#f6f7fa），不得铺设紫色背景。淡紫标签栏与浅灰内容区保持可区分，选中标签单独提亮；深色保持现有层次。不通过增加高度或间隔实现区分，表格/表单保持实色。验证明暗主题及窄屏实际截图。
- 页面复用 `PageHeader`、`FilterBar`、`DataTable`、`EditorDialog` 等现有组合，不因新样式另建平行的业务请求或权限体系。模块页头描述在双语资源中维护。 管理页面默认使用紧凑标题栏（18px），模块分类不单独占行；说明通过标题旁的信息按钮按需展开，保持键盘/触屏可用。页面纵向留白以筛选与数据可见性为先，例外需说明用途并做窄屏验证。
- 侧栏导航组切换器承接既有 `navigation` 分组；显示服务端分组名称，只筛选已授权菜单。它不是租户或组织切换。保留工作标签、用户/portal 隔离的分组恢复及分组失效回退。
- 角色页默认采用角色/权限双栏。权限独立勾选、隐藏选择、数据范围及服务端授权继续生效；切换/离开须保护草稿，迟到响应不得覆盖新角色，写入失败不得丢弃勾选或自动重试。
- 样板 `design-preview/` 不接业务 API，也不随生产入口构建；实际页面验收看 `e2e/softlight-*.spec.ts`、既有完整 E2E 和真实联调。生产产物使用 `npx playwright test --config playwright.production.config.ts`，与模拟/真实测试顺序运行。

- 账号头像、用户名及个人资料/安全操作统一放在右上角账号菜单；侧栏底部只保留导航收起/展开控件，不重复设置用户入口，以优先保证菜单可见空间。

## 规则界面

规则基座页面集中在 `src/features/rule/`。专业编辑使用结构化契约、Vue Flow与按需加载的Monaco；运营目录不加载编辑器。金额和大整数必须经模块无损JSON适配，画布位置等库内部对象不进入规则文档。操作权限和数据范围最终由后端判断；保存草稿、试算、发布、回滚分别触发。不要持久化试算输入；超时先核对服务端，禁止自动重试写操作。专项目录、用法及验证见 [规则界面实施计划](../docs/rule-engine/frontend-plan.md) 和 [前端证据](../docs/rule-engine/frontend-evidence.md)。

规则编辑历史仅保留当前页内存中的有限快照，不能包含模拟面板真实输入或改变发布指针；刷新重新建立基线。模拟/校验必须绑定提交时的文档与输入快照，编辑后清除旧结果并丢弃迟到结果及错误。Monaco补全来自当前契约和服务器函数目录，按模型隔离并随编辑器释放。示例通过正常创建流程明确保存，仅对匹配契约显示，不自动写租户数据或发布；回归 `history.test.ts`、`task.test.ts`、`samples.test.ts`、`references.test.ts`、规则authoring/flow/visual浏览器测试及隔离真实 `rule-samples.spec.ts`。

流程拖线和出口表单统一走flow.ts连接校验，不得在表单绕过开始节点/出口/成环检查。可用步骤引用仅取所有可达路径的共同前驱，不能把分支一侧或尚未执行的结果作为保证可用输入。节点condition/expression互斥，切换确认；子规则绑定不可变版本，输入映射、参数映射和输出契约按所选版本配置，晚到请求不能写入新选中节点。画布坐标/库内部对象不进入领域JSON。标识符和精确数值控件保留未提交的本地输入，避免父组件刷新覆盖；条件表单按容器宽度布局。验证`flow.test.ts`、`flow-authoring.test.ts`、`flow-inputs.test.ts`、规则flow/visual浏览器与隔离真实`e2e-real/rule-flow.spec.ts`。多线程开发时真实验收可用`SMA_E2E_STATIC=1`针对已构建dist，保留默认开发入口。

- 管理区以实用性和数据可见空间优先。底部品牌仅为内容区居中的背景装饰，必须脱离文档流、位于主功能/表格下层且不拦截指针或键盘；不得为了保证品牌可见而预留页脚高度、分隔带或额外底部留白。登录页的版权/备案信息按其原功能保留。

## P07 认证交互

账号安全页使用左侧状态卡片、右侧操作区的自适应布局；以内容容器宽度判断分栏，窄屏优先显示正在操作的表单。二维码保持正方形白底和完整静区，不使用外部生成服务；手动密钥和验证码输入不得撑破布局。布局改动回归 `e2e/security-layout.spec.ts`（模拟接口、独立浏览器会话），覆盖明暗、中英文、桌面/窄屏和键盘操作；不得拿用户当前绑定材料做截图或重复提交。

MFA_REQUIRED 只保留内存挑战，不能保存为访问令牌或预加载权限；取消后丢弃迟到响应。账号安全页的二维码仅本地生成，密钥、密码及动态码不进入 URL、持久存储或埋点。锁屏清除敏感表单，解锁只验证登录密码并重新加载状态；同账号的主题/语言刷新不清空表单。新登录密码 8～12 位、安全操作密码 6～8 位的前端检查只作提示，弱密码与双向不同由服务端强制。旧登录/解锁输入仍支持历史密码。回归 `auth-mfa.test.ts`、`account-security.test.ts` 与 `e2e-real/auth-security.spec.ts`；实际验收见[进度](../docs/auth-security-progress.md)。

- 数据列表默认使用 `TableWorkspace`：标题/筛选放 `#tools`，`DataTable` 放默认插槽，自定义分页放 `#footer` 并传入表格的 `page`。表格填满中部剩余空间，数据在表内双轴滚动，表头、工具栏与分页保持可见；少量/空数据不收缩外框。管理壳自动识别直属 `TableWorkspace`，有中间包装的列表路由需显式声明 `meta.contentLayout: 'table'` 并保证整条容器链 `min-height:0`。窄屏工具区独立限高滚动，禁止由数据行数撑高整页。复合仪表盘内的表格也需限高；只有明确的产品要求才可使用 `scroll-mode="page"`，同时采用允许自然增长的外层布局。修改后运行 `e2e/table-workspace.spec.ts` 并覆盖相应页面的窄屏、分页与键盘操作。

P10静态登录布局与MFA共用认证流程，禁止在模板复制认证请求。布局样式不应重建同身份表单；Logo只使用当前明暗槽，不能借另一槽绕过HIDE。版权/备案及外链复用安全地址检查。`e2e-tenant/layout.spec.ts`覆盖三模板、明暗、双语、窄屏、键盘和挑战延续；真实后端验收仍用隔离runner。皮肤策略/管理页实施状态查P10进度。

P10共享配置管理使用`features/config/layers.ts`的类型化契约；参数与字典的有效层要显示来源及平台/租户双版本，平台草稿保存和发布分开。LOCAL写入固定原`Idempotency-Key`及请求时版本，结果未知只查原键；身份、权限或确认的租户范围变化清理待提交草稿，迟到响应不得写回。验证`e2e/config-layers.spec.ts`、隔离`e2e-real/config-layers.spec.ts`及`npm run verify`；S5f与分库状态见[专项进度](../docs/tenant-platform-progress.md)。

P08实际制品迁移回归使用run-cluster-e2e.mjs的SMA_CLUSTER_SCHEMA_MIGRATION=1：先在隔离新库验证validate无写拒绝、migrate退出且不建管理员及原历史保持，再撤销CREATE/ALTER/DROP/INDEX，令全部服务启动/重启显式validate。命令失败不自动重试，记录原制品/脚本哈希和进程退出，清理只限本轮资源。权限证据仅限实际撤销项；该开关不开放multi或替代真实LB验收。

C3d2b本人改密/锁屏/退出统一使用useSecurityControls：异步确认前固定身份与输入，LOCAL原键只核对匹配操作的终态回执，NOT_FOUND不清UNKNOWN；密码不持久化或重放。会话锁意图与凭据意图独立，锁/解锁未知保持遮挡且跨刷新恢复，普通/me不能作为完成证据；锁失败不得借unlock释放，需退出重登。页头和锁屏退出共用门禁，迟到结果不能退出新身份。回归security-controls/account-security/auth-mfa/http单元，security account-security/authentication/protocol-compat、security-controls工作流/布局、shelf locked sign out及完整verify；API夹具不替代S1d真实后端。
C3d3a会话撤销使用useSessionRevocations及按目标NATIVE登记；当前会话不走强制下线。确认前固定ID/身份，未知跨刷新保留，分页列表无目标不能释放，禁止重登/新键绕过；不得以LOCAL通用查询冒充原生回执。查询带signal与expectedIdentity，锁屏/权限变化清私有列表，晚响应不影响新身份。回归session-revocations/submission-pool/operation-registry单元、security sessions浏览器、security-controls原lock工作流及完整verify；服务端当前无历史撤销回执。
P08证据runner使用SMA_CLUSTER_EVIDENCE=1＋受控迁移，显式readiness包含deploymentEvidence，故障重启须观察新process/递增epoch及当前有效持久报告，不能凭旧行判断成功。每个错误配置验证健康peer原会话和固定基线；所有密码/密钥仅环境或本轮受保护文件，结果只记录故障标签、PID/epoch和哈希，不记录原密钥/证据值。缺OpenAPI文件探针不能生成文件。失败不重试业务写；清理覆盖本轮全部正常/错误/缺失探针密钥，保留其他线程/用户环境。

C3d3b1 SQL重置/事件重投走useMonitorCommands的NATIVE目标登记；确认前固定身份/目标，未知跨刷新保留，统计读回或事件COMPLETED不能释放旧操作，不用LOCAL查询伪造回执。直接重投确认不代表监听器完成。回归security monitor-commands（20次点击/取消/未知恢复/不同事件/迟到确认）、security-controls operations SQL场景及完整verify；P08节点路由另行验收。
C3d3b2消息连接归属必须包含token/portal/用户/锁屏/权限，不能仅用userId。messages申请socket-ticket/读summary绑定expectedIdentity；旧连接强制退出或SESSION_INVALID迟到验证不得影响新身份，锁屏清计数并关旧连接。票据只用于一次握手，不持久化、不套LOCAL；重连保持同步门禁/退避与销毁清理。回归messages/auth-mfa/http单元、security-controls锁屏/423工作流与完整verify；真实集群Socket验收仍独立。
C3d3b3普通API默认在调用时固定身份，排队后变更在适配器前拒绝；特殊原始Axios写必须bindRequestIdentity或显式绑定已捕获Authorization，禁止延迟取新token执行旧意图。ruleWrite保留精确JSON，cache submit保留原键且不加当前节点头；节点策略/预览照原P08定向。回归http/write-adapters、rule exact/task、cache单元；security authentication/protocol、cache定向与rule authoring浏览器和完整verify。默认身份绑定不是业务幂等或跨标签互斥。

生产写入口审查以[覆盖清单](../docs/security-frontend-write-inventory.md)为索引，新增页面/动态命令/原始适配器须同步实际处理器、协议与测试；按后端语义区分只读POST、预览、一次票据，不能只看HTTP方法。清单为C3本地证据，C4跨标签和S1d真实后端另验，不允许把未知原生命令当前状态读回当历史回执。
C4浏览器协调原语browser-operation-coordinator以站点/API/tenant/portal/user作用域和资源集合协调，不得把token/正文/凭据放入锁名。精确对象独占、祖先集合共享、集合写独占；多锁冲突只返回busy，不排队自动重发。关闭页面只释放浏览器锁，不能清持久UNKNOWN；不支持Web Locks失败关闭。原语验证browser-operation-coordinator单元和security coordination真实双页面；当前生产接入阶段见前端安全进度，不能仅凭原语测试声明跨标签全站完成。
C4a2生产普通注册表启用浏览器协调：同步reserve后admit，确认前持有资源锁；所有原键查询/续传同组互斥，不能绕过注册表或把storage事件当原子锁。短ledger事务只做同步存储/容量检查，禁止网络/弹窗等待；UNKNOWN发送前持久化，取消/关页仅释放临时锁。异步准入前固定表单/ID/version/批量输入，变化时取消旧意图；静态恢复入口初始化bind。缺Web Locks或存储故障必须明确拒发。回归operation-coordination/browser-operation-coordinator/operation-registry/submission单元，security coordination/submission双页/恢复及shelf/rule/cache/extension受影响浏览器、完整verify；凭据C4a3、面板C4b、旧版C4c另验。

P08存储runner开关SMA_CLUSTER_STORAGE=1要求EVIDENCE/SCHEMA_MIGRATION/M2同时开启；readiness显式包含sharedStorage，先启动两个后端再等待READY。故障仅在本轮managed目录做split/missing，探针路径由受信持久Offer的manifest/epoch/node/process派生，删除前核对root内规范路径、非symlink及原32字节摘要；恢复仅原字节且CREATE_NEW。等待新进程及epoch，不能以配置MATCH替代存储certified/health。结果只记标签和状态不记挑战字节，原34组保留，不把同机目录测试当真实多主机挂载/LB。

C4a3b会话锁使用shared-session-lock-intents，保持与凭据独立。Web Locks覆盖同scope锁/解锁，localStorage v2只存schema/scope/key/action/state（≤512字符、≤256scope）；UNKNOWN先落盘，直接成功才写LOCKED/UNLOCKED，错误密码仅恢复LOCKED。未知不能由普通/me、刷新、删除标记或另一次unlock解除。新确认UNLOCKED通知须按原身份读回明确locked=false和匹配用户/portal，且原key仍最新、无新本地锁/退出，才允许另一标签解除遮挡；初始历史标记不覆盖服务器锁态。迟到授锁/落盘后换身份不可泄漏锁或退出新身份。缺能力/存储拒发并保留本地退出能力。回归shared-session-lock-intents/security-controls，security session-lock-coordination/account-security、原lock/423工作流、shelf locked sign out及完整verify；登录/MFA/退出跨标签仍归a3c。
C4a3c1令牌统一经lib/token操作：私有session token优先，detached本标签禁止自动回落另一共享身份；saveToken私有保存不得清其他共享token，remember才显式选择共享slot。clearToken(expected)遇到有效token变化必须返回false，只删除匹配token，禁止直接无条件同时清session/localStorage。detached只存1，不加密码/挑战/业务正文。回归token/http/auth-mfa单元、security authentication-storage双页及authentication浏览器、完整verify；共享身份监听/认证准入和退出协调按c2/c3继续验收。
C4a3c2登录/MFA统一由auth store按origin认证槽Web Lock准入，确认输入先快照，锁覆盖请求/本次权限读取；等待OTP不持锁，竞争拒发不排队/重放。用户名/密码/OTP/challenge/token不进锁名/恢复记录。页面离开和入口变化调用captureAuthentication代次句柄，不得旧页面reset新登录；共享token变更使依赖的旧身份/挑战失效，私有会话独立。登录开始仅detach本标签，取消最多清本次接受token。store销毁只释放资源，不调用已销毁的其他store。回归authentication-coordination/auth-mfa/token/http单元、security authentication-coordination/authentication/authentication-storage与相关缺能力浏览器、完整verify；服务端认证与退出协调c3另验。
C4a3c3退出统一auth.logout(beforeExit?)：门禁必须在未保存确认前，取消不落标记。session-exit共享LOCAL_EXIT摘要元数据只表示本浏览器停止使用旧会话，禁止当后端成功回执；首发送者至多一次POST，未知/刷新/旧token恢复不重发。匹配其他标签只detach自身，不先删共享token干扰发送者；新身份和不同私有token保持。能力/存储/容量不足仍保留确认和本地退出，不能额外发未协调请求。旧退出/旧确认/晚摘要不得清新账号，销毁/失效释放临时锁。回归session-exit/logout-coordination/auth-mfa/security-controls单元、security logout-coordination/authentication与shelf locked sign out浏览器、完整verify；C4b面板/C4c旧版及S1d真实后端另验。
C4b1操作状态面板只读投影OperationRegistry；按可信owner过滤，锁屏隐藏。核对仅LOCAL原键，PREPARING/SENDING/SLOW与冷却中不发查询；同步门禁覆盖读取，身份/代次/销毁后迟到结果不更新面板。NOT_FOUND、畸形或原生未知不得解除记录，不提供删除UNKNOWN/换键/自动重发。通用回执不能作为业务DTO，成功后业务授权重读。新增文案双语，回归operation-panel单元及playwright.security.config.ts operation-panel.spec.ts、完整verify；离页/刷新与恢复入口后续按C4b2验收。

C4b2离页提示为非阻断持续提示，不能据路由切换/刷新清除未决或自动核对/重发；原页面未保存输入仍由自己的守卫负责。operation-recovery只接受代码白名单目的地并检查实际注册路由及当前权限，不能持久化任意URL或把原键/正文拼到URL；点击再次授权，缺映射保留原编号，不猜资产类型。UPLOAD仅file.upload/file.image-upload复用lookupUpload/uploadEvidence只读核对，ACCEPTED/RUNNING保持未决，不从元数据重建File/续传/下载或绑定；其余NATIVE仍走业务页面原适配器。回归operation-recovery/operation-panel单元与浏览器及完整verify；小屏工具栏需全部可见且恢复提示不得挤压表格按钮。

C4b组合回归使用完整playwright.security.config.ts、playwright.security-controls.config.ts及playwright.shelf.config.ts（独立5196/5198）。关页事件不等于Web Locks已释放；测试先只读观察实际held归零，再单次获取，禁止通过循环获取或自动重放掩盖互斥问题。API夹具、旧版独立兼容与S1d真实后端证据分开登记。

C4c旧版兼容测试使用既有playwright.security-legacy.config.ts/5197，保留protocol-compat并扩展legacy-compat；此配置实际运行frontend自身Vite，next只提供测试工具，不跨工程导入生产源码。与5196新版安全/5198随手栏分开，保留旧版基线，真实后端S1d另验。

S1d真实验收使用run-real-e2e.mjs的SMA_E2E_SUITE=security与明确制品/独立端口，不能指向用户8080/5173/5174；request-governance.spec.ts仅在此scope执行。只读核验真实业务结果，传输层故障必须标明发送前阻断或提交后丢响应，不能用伪造业务响应或响应延迟冒充服务端事务并发。runner清理须先确认自己的ChildProcess真正终止，再删除生成库/账号并查询确认不存在；路径解析必须留在本轮目录内。结果同时检查passed/cleanupComplete与制品SHA256。修改runner回归node --test scripts/e2e-isolation.test.mjs、脚本/测试类型及lint、实际专项和完整verify；保留普通runner的隔离命名空间与并行租户配置初始化逻辑。

S1d2仅security scope允许SMA_E2E_PEER_BACKEND_PORT，必须与主实例/前端及已启用旧版端口不同且空闲。迁移只由初始实例完成，双实例只validate；所有子进程都应尝试停止，任一仍活着不得清库。JSON报告须属于本轮、至少一个通过用例且无失败，不能将全跳过/旧报告计为成功。跨阶段只保存原键/业务ID/回执ID；正常签发的测试token只留在worker内存中，禁止修改登录预算绕过夹具问题。低并发与压力协议分开验证，20请求压力不得减少数量或接受任意5xx；声明的保护拒绝仍须原键权威回执及唯一业务结果。验证request-governance-cluster.spec.ts两阶段、隔离单元及完整verify，不替代生产多节点资格或跨租户完整矩阵。

S1d3a同租户范围验收使用request-governance-scope.spec.ts：普通角色/成员必须经真实IAM接口创建、正常登录，撤权后新会话再验旧回执，不能只凭旧token失效判通过。超管文件读取权与本人回执分开断言；上传丢响应由真实route.fetch后abort注入，刷新只读原生任务，删除后成功回执不得恢复文件或重传。精确验证403/404/业务错误key，不接受任意失败。仅security临时库及明确制品/独立端口，顺序准备遵守生产写间隔，禁止清空预算；回归该真实专项及完整verify。

S1d3b显式SMA_E2E_SECURITY_TENANTS只允许security生成库；TEST域名/账号/权限目录仅在自身宿主退出后初始化并检查保留ID范围无冲突，密码hash只在该库内部复制不输出。禁止新增生产认证旁路或伪造tenantId；真实Host和正常登录必须验证两边同等权限。配置测试使用停用账号、随机虚拟凭据和本轮加密环，不接触提供方；清理包含tenant-credentials.keys。回归e2e-isolation.test.mjs、request-governance-tenant.spec.ts与完整verify；TEST域名不代表DNS/TLS或S7开通通过。

部署WebSocket关闭1001必须保留当前共享登录身份，使用现有退避重新获取一次性ticket，重连成功刷新授权REST状态；不能复用已消费ticket或把正常部署下线当session-ended强退。锁屏/换账号/换token时原epoch与expectedIdentity隔离继续有效，旧连接事件不得清除新身份。验证messages.test.ts的1001/new ticket/REST刷新及原身份/强退测试，完整npm run verify；真实后端连接关闭和跨节点恢复分别以P08专项/双JVM验收为准。
