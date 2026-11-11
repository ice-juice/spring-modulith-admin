# 新版管理前端规则

规则写入口使用useRuleTask.command，不能把试算/执行误当无副作用查询；确认前预留，cloneExact固定输入，原operationKey与expectedRevision保留。rule.assets同对象各面板共享锁；普通刷新不能清除UNKNOWN。原生模板发布/批次按原键GET核对，RECOVERY_REQUIRED不重跑成功项；完成回执不能作为Asset/Execution。直接运行资源清理只有统计观察而无持久完成证据，不得凭统计释放未知。回归rule task单元及authoring/instances/releases/operations浏览器，保留精确数值与共享恢复入口唯一性。

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
