# 新版管理前端

独立 Vue SPA，采用 shadcn-vue / Reka UI、Tailwind CSS 4；v0.2.0 默认使用已确认的鸢尾紫“柔光工作空间”主题。旧 `../frontend/` 保留，两者不互相导入源码。

## 启动

Node.js `^22.18.0 || >=24.12.0`，使用 npm 锁文件。本轮验证环境为 Windows、Node 26.8.1、npm 11.19.0、Google Chrome。

```powershell
cd frontend-next
npm ci
npm run dev
```

打开 http://127.0.0.1:5174 。默认代理现有后端 http://127.0.0.1:8080，端口占用会报错，脚本不会停止其他程序。

指定其他后端：

```powershell
$env:SMA_NEXT_BACKEND_URL='http://127.0.0.1:8080'
npm run dev
```

开发组件页：`/dev/components`，生产构建会移除该入口和页面模块。使用正常登录流程；5173 和 5174 的本地存储相互独立。

P10品牌入口已切换到`/api/tenant/entry`，需要同批升级的后端；旧`/api/config/parameters/public`不再支持。portal由后端域名绑定返回，入口不可用时登录/验证码禁用。开发代理保留Host/Origin，使用根目录开发启动脚本可显式登记回环入口；自有域名仍需验证，不能以默认租户兜底。

## 检查

```powershell
npm run verify
npm run test:e2e
npm run test:real
npx playwright test --config playwright.production.config.ts
```

- `verify`：已审核的旧前端文件哈希、类型、ESLint、双语言键与插值、Vitest、生产构建和产物检查。
- `test:e2e`：独立浏览器、模拟接口，全路由和业务交互。默认使用已安装 Chrome，也可用 `PLAYWRIGHT_CHANNEL` 指定；未安装浏览器时运行 `npx playwright install chromium`。
- `test:real`：现有后端 JAR、JDK 21、本地 MySQL 客户端；创建临时数据库与独立数据库用户，在 8081 启动专属后端，在 5174 启动新前端。不会使用生产管理员密码。测试管理员随机生成，只有子进程环境中持有。真实测试禁用 trace，避免记录凭据。
- `npx playwright test --config playwright.tenant.config.ts`：5197/5198双前端与5199代理探针，验证入口状态/绑定portal及Host、Origin保留。`SMA_E2E_LEGACY=1`配合`node scripts/run-real-e2e.mjs tenant-entry.spec.ts`可做实际品牌发布后的双前端联调，端口和JAR支持环境变量独立指定；见[P10运行说明](../docs/tenant-platform-operations.md)。

没有正在运行的 MySQL 时可以使用专属测试实例：

```powershell
$env:SMA_E2E_STANDALONE_MYSQL='1'
npm run test:real
```

该模式使用新的临时数据目录和 3307 端口，测试结束停止专属实例并清理其数据。常规模式默认 3306，支持 `SMA_E2E_MYSQL_PORT`、`SMA_E2E_MYSQL_ROOT_PASSWORD`、`SMA_E2E_MYSQL`；JDK 使用 `JAVA21_HOME`。本机 MySQL 客户端默认查找 `%LOCALAPPDATA%/Programs/mysql-8.4.10-winx64/bin/mysql.exe`。需要当前后端包 `../backend/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar`，后端构建方式参见仓库开发文档。结果存入被忽略的 `test-results*/` 和 `.e2e-runtime/`。

模拟接口、真实接口、生产产物检查与截图脚本都使用 5174，必须顺序运行。生产检查前先构建，它只启动 Vite preview，不重用开发服务。

实际页面截图：`node scripts/capture-softlight.mjs`（Node 26 原生 TypeScript 支持；使用隔离模拟 API）。只看原始设计样板：`npx vite --config design-preview/vite.config.ts`，端口 5186，不接真实 API，也不进入生产包。

## 快捷入口、可调面板与收藏布局

- 顶部打开“我的随手栏”，在常用标签/个人便签旁点击 `+` 展开授权页面入口；点击前重新核对服务端 targets，进入已有工作标签时保留原查询和导航守卫。方向键切换分类，Escape 收起快捷入口。
- `/iam/roles` 的角色目录与权限区之间可拖动调整；规则模板页打开“模拟与解释”后，可调整右侧面板。聚焦分隔条后左右键每次16px、Shift每次32px，Home/End到边界，Escape或双击复位。容器窄时自动上下排列，编辑内容不重建。
- 随手栏常用标签区提供列表/卡片切换；搜索、别名编辑与排序不因布局切换清空。布局偏好以服务端 scope 为键存本地，仅保存 `list/cards`；不保存搜索词或草稿。偏好存储不可用时仍可切换。
- 通用组件位于 `src/components/business/QuickTabMenu.vue`、`ResizablePanels.vue`；收藏状态与动效位于 `features/workspace/bookmark-layout.ts`。支持语义主题、双语及减少动态效果，不增加 GSAP 或外站运行依赖。来源和取舍见 [LeiBox 参考](../docs/frontend-next/leibox-reference/README.md)，实际验收见 [专项进度](../docs/frontend-next/leibox-ui-progress.md)。

验证：`npx playwright test --config playwright.leibox.config.ts`（独立5216端口）；生产包验证先运行 `npm run verify`，再设置 `$env:SMA_LEIBOX_STATIC='1'` 执行同一命令。该套件覆盖本功能、IAM、规则编辑/流程、表格工作区和管理壳；原随手栏继续使用 `playwright.shelf.config.ts`（5198）。均不复用用户运行服务。UI/模拟接口验收不代表后端或部署已升级。

## 构建与发布

```powershell
npm run build
npm run release:check
```

产物为 `dist/`；检查结果和 SHA256 清单在 `dist/release-manifest.json`。默认部署根路径。子路径部署时设置 `SMA_NEXT_BASE=/admin-next/` 后重新构建，路由使用相同 BASE_URL；API 和 WebSocket 继续使用根路径 `/api`。

本轮发布产物为 `release/frontend-next-0.2.0.zip`，解压后即为静态站点根目录。SHA256 及旧产物快照说明在 `../docs/frontend-next/release-summary.json`。保留 v0.1.1、v0.1.0 和旧前端快照。需要后续打包时使用新版本号，不覆盖已发布 ZIP；先运行构建与产物检查，再使用 PowerShell `Compress-Archive -Path 'dist/*' -DestinationPath 'release/frontend-next-<新版本>.zip'`；同时更新校验记录。

静态服务器需配置 SPA history 回退，并将 `/api`（包含 `/api/message/ws` 的 WebSocket Upgrade）及 `/actuator/health` 转发到后端。开发代理配置不会进入生产产物。生产通常使用同源访问，跨域部署须由后端显式配置授权域名。

切换、回退、兼容性和验收范围见 [交付与验收记录](../docs/frontend-next/acceptance.md) 及 [技术决策](../docs/frontend-next/technical-decisions.md)。旧工程继续保留，旧启动脚本不改变指向。

## P07 账号安全接入

`/login/totp` 为独立二步验证页，挑战只在内存中保存；`/auth/security` 从右上角账号菜单进入，提供 TOTP 绑定/换绑/解绑及安全操作密码管理，二维码在本地生成。实际交付与验证状态见[P07 进度](../docs/auth-security-progress.md)，接口及超时处理约定见[认证安全 API](../docs/auth-security-api.md)。既有发布 ZIP 不会随工作区修改被覆盖。

真实验收可以使用 `SMA_E2E_FRONTEND_PORT` 指定空闲的 next 端口；设置 `SMA_E2E_LEGACY=1` 会同时启动旧版兼容页面，端口由 `SMA_E2E_LEGACY_PORT` 指定（默认5188）。`SMA_E2E_BACKEND_JAR` 可选择隔离构建的后端包；`SMA_E2E_STANDALONE_MYSQL=1` 使用任务自有 MySQL 实例，`SMA_E2E_MYSQL_PORT` 可覆盖默认3307。所有端口先检查，不停止已有服务。指定 next 端口时结果写入 `test-results-real-<端口>`，也可用 `SMA_E2E_OUTPUT_DIR` 指定结果目录。可通过 `node scripts/run-real-e2e.mjs auth-security.spec.ts` 仅运行认证专项。

Monaco 保持0.57.0，通过 package overrides 将其 DOMPurify 统一到3.4.17，避免重新引入已审计出的旧版 sanitizer；锁文件与完整 verify 一起核验，不用降级编辑器规避审计结果。

## 鸢尾紫管理壳

顶部“搜索”或Ctrl/⌘K打开统一搜索面板；手机保留搜索图标，侧栏不再设置搜索输入。菜单模式可跨当前导航组查找已授权菜单；全局模式按来源检索用户、文件、管理通知、我的待办、应用与参数名称/标识。上下键选择、Enter打开、Escape关闭；每来源先显示5条，支持加载更多和单独重试。点击只携带记录focus ID，关键词不保存到浏览器或目标页面URL。

扩展入口为 `src/lib/search.ts` 的 `SearchProvider` / `searchRegistry.register()`，调度在 `src/composables/useGlobalSearch.ts`，展示在 `src/components/business/GlobalSearch.vue`。新来源提供稳定ID、双语labelKey、permission（若需要）和search(request)，使用request.signal取消查询，返回items/hasMore；分页从1开始，结果target必须是受控内部路由或http(s)外链。正式业务实现仍放各业务自己的适配层，不能绕过后端权限。

预留的semantic能力和citations字段供后续AI检索使用；当前只启用keyword提供者，无AI调用或不可用占位按钮。以后接入语义检索必须明确启用，并通过后端执行租户/数据范围过滤、返回可追溯来源；不能把当前用户的全文或凭据直接发给第三方。当前搜索不是全文引擎，参数值、附件正文、私信内容不在此轮范围。测试与实际证据见 [统一搜索进度](../docs/frontend-next/unified-search-progress.md)。

权限资源与导航编辑中的“图标”按钮可打开图库：209线性 + 122透明3D，支持中英文搜索、分类、每页48个结果、明暗背景下的菜单/按钮尺寸预览。点击“使用此图标”只更新编辑草稿，保存资源才写入；取消保留原值，清除保存为none，未知旧值保留并使用备用图标。侧栏、导航分组和权限列表显示实际图标。

业务权限按钮使用 `components/business/PermissionButton.vue` 的 `:permission` 属性，原Button的事件、disabled、variant等透传；从auth/me.permissionIcons读取授权范围内图标，配置图标替换原装饰SVG。未配置时保留原图标，none明确去除，未知ID保留默认。`UploadButton`单独接入file:file:upload，加载状态图标仍优先显示。新前端+旧后端时菜单仍可显示，按钮没有映射时保留默认表现，不能算持久化按钮图标已部署。

图库目录为 `src/lib/icons/`，3D文件为 `public/icons/3d/`，本地NOTICE/LICENSE/manifest保存来源和逐文件哈希。扩充时添加稳定短ID、双语名称、分类并跑 `icons.test.ts`、`npm run verify` 和 `e2e/icons.spec.ts`；实际链路验证用隔离 `scripts/run-real-e2e.mjs e2e-real/icons.spec.ts`。实现细节和当前服务边界见 [图标功能进度](../docs/frontend-next/icon-settings-progress.md)。

右上角调色盘为“皮肤”：鸢尾紫（默认）、青瓷绿、琥珀棕；旁边太阳/月亮按钮为“主题”：浅色、深色、跟随系统。两项独立保存到账号偏好，个人偏好页也可修改或恢复默认。浅色皮肤共享 #f6f7fa 主内容底色，仅品牌及相关表面随皮肤变化。

需要配套支持 skin 的后端及 config V4 迁移，先后端后前端；旧后端不会保存此新增字段。`npx playwright test e2e/skin-theme.spec.ts` 检查独立切换、回滚、账号恢复与三皮肤显示；真实检查使用 `scripts/run-real-e2e.mjs e2e-real/skin-theme.spec.ts`（按现有隔离环境约定设置端口/JAR）。当前交付的真实持久化证据来自隔离库，不代表 8080 已升级，详见 [实施记录](../docs/frontend-next/skin-theme-progress.md)。

侧栏“全部功能/当前分组名称”入口承接原顶部业务导航；不是租户切换。工作标签仍按个人偏好显示和恢复。角色页使用角色/权限双栏，未保存权限在切换或离开时需要明确处理。更多使用规则、完整覆盖及实际截图见 [设计规范](../docs/frontend-next/design-spec.md)、[重构进度](../docs/frontend-next/softlight-progress.md) 与 [实际截图册](../docs/frontend-next/softlight-gallery.html)。

侧栏底部仅保留导航收起/展开按钮；用户名、头像、个人资料和安全操作统一使用右上角账号菜单，为导航列表留出更多高度。

顶部导航、工作标签栏和主内容背景分别采用白色、淡鸢尾紫、原浅灰色（#f6f7fa）；主功能区不铺紫色背景。深色采用紫灰 → 深紫灰 → 深色。选中标签单独提亮，数据卡片保持实色。配色及截图验证见 [背景层次调整](../docs/frontend-next/shell-color-progress.md)。

管理页面采用 18px 紧凑标题栏，筛选和数据区提前显示；点击标题旁的信息图标可查看原页面说明，键盘聚焦后按 Enter 同样可用。

## 规则界面

- `/rule/scenes` / `/rule/templates`：设置场景契约、新增专业模板，在工作区选择表达式、条件、流程或决策表，编辑参数和发布断言，模拟、验证后保存草稿并发布。
- `/rule/catalog` / `/rule/instances`：按场景选择固定模板版本并填写参数与业务绑定；实例先试算、再发布，可停用、回滚或预览升级。真实业务对象须由所属模块授权。
- `/rule/operations`：按权限查询执行、查看批量迁移回执及运维资源；测试用例与版本比较从模板工作区打开。

通过既有IAM权限在侧栏“规则中心”显示对应菜单，原查看权限ID和授予保留；角色授权树在同一目录下提供操作权限。目录出现不代表角色获得其全部子权限。界面采用现有鸢尾紫明暗主题；Monaco及Worker与Vue Flow按需加载，运营目录不会下载它们。规则文档和输入保持十进制精度，Long ID保持字符串。完整结果与限制见 [验证记录](../docs/rule-engine/frontend-evidence.md)。

专业编辑支持当前页撤销/重做（最多50步，另有容量上限），以及当前契约字段和服务器函数参考与补全。修改规则、输入或参数后需重新模拟；请求期间的迟到结果不覆盖新状态，保存期间保护编辑内容。历史不进入浏览器存储，也不包含模拟面板输入。

新增场景时可选择示例契约，再到新增模板中选配套示例：比例折扣表达式、会员资格条件、满额减免流程、阶梯优惠决策表。每组提供参数帮助与3条发布断言，模拟面板可载入示例数据；创建、保存和发布仍分别操作。详见[示例用法](../docs/rule-engine/samples.md)与[本轮进度](../docs/rule-engine/authoring-followup.md)。

流程编排支持从组件库拖入或点击添加节点、拖线与出口配置、选中连线后断开及删除确认。属性区提供条件构建器/布尔表达式切换、SWITCH分支维护、计算输出类型和对象字段、子规则固定版本选择及逐字段输入/参数映射；返回节点可选择保证执行的前驱结果。画布坐标仅保留当前会话，不写入执行文档；保存与发布分开。方案、分项进度和验收边界见[可视化流程设计](../docs/rule-engine/visual-flow-design.md)。真实联调可设置 `SMA_E2E_STATIC=1` 运行 `npm run test:real -- rule-flow.spec.ts`，对已构建dist使用preview及隔离后端/数据库，避免开发热更新打断状态；仍按真实测试说明配置独立端口。

验证：`npm run verify`；`npx playwright test --config playwright.rule.config.ts`（默认5187，可设 `SMA_RULE_TEST_PORT`）；设 `SMA_RULE_PRODUCTION=1` 复验已构建的 `dist`；真实规则专项 `npm run test:real -- rule.spec.ts`。已有端口占用时可设 `SMA_E2E_FRONTEND_PORT`、`SMA_E2E_BACKEND_PORT`、`SMA_E2E_MYSQL_PORT` 和 `SMA_E2E_BACKEND_JAR` 指向隔离运行。

管理页不设置占位品牌页脚。品牌名位于内容区底部中央的背景层，不占据布局高度，也不拦截操作；表格或主功能组件覆盖它时正常隐藏。登录页继续提供配置的版权与备案信息。

数据列表默认填满中部可用空间，多行数据在表格内部滚动，表头和分页保持可见。窗口缩小或筛选条件过多时，筛选工具区独立滚动；空表与少量数据仍保留完整数据区域。数据库字段和索引通过标签切换共用数据区。

开发页面时使用 `TableWorkspace` 的 `#tools` / 默认 / `#footer` 插槽分别承载筛选工具、表格和自定义分页。`DataTable` 的默认 `scrollMode` 为 `internal`；只有明确需要随页面自然增长的页面才设 `scroll-mode="page"`，并配合可自然增长的外层。专用回归入口为 `npx playwright test e2e/table-workspace.spec.ts`；实施记录和验证边界见 [表格空间优化进度](../docs/frontend-next/table-workspace-progress.md)。
## 个人随手栏

个人随手栏在 `src/features/workspace/`，由管理壳全局加载，页面标题与工作页签提供收藏入口。支持个人便签自动保存、冲突另存、回收站及三种显示方式；[使用说明](../docs/personal-shelf-user-guide.md)、[实现/验收进度](../docs/personal-shelf-progress.md)。专项模拟浏览器使用 `npx playwright test --config playwright.shelf.config.ts`（独立5198端口），真实验收使用 `e2e-real/personal-shelf.spec.ts` 配合隔离后端；本功能不访问旧前端或运行中的用户会话。

# P05 扩展管理入口

`/extension/manage` 使用已确认柔光风格，复用 PageHeader/DataTable/EditorDialog。分组、制品、上传、发布/灰度/回滚、门禁、账号/机器、信任、审计以及宿主工作对账位于 `src/features/extension/`。需要宿主 `extension:console:view` 菜单权限；页面使用当前后台身份自动连接；发布权限按独立服务中的显式账号绑定决定，每动作仍验证发布操作密码/TOTP。发布session只在服务端，密码/一次性凭据不保存。

前端只调用宿主 `/api/extension/consoles/requests`，不直接访问发布服务，不再配置 `SMA_NEXT_RELEASE_URL`。宿主使用独立CONSOLE机器凭据通过HTTPS连接发布服务；只读查询不弹验证，首次因素绑定与敏感操作在当前页完成。配置及迁移见 [统一入口部署](../docs/extension/unified-console-deployment.md)。

专项验证 `npx playwright test --config playwright.extension.config.ts` 使用独立输出目录 `test-results-extension/`，真实浏览器+模拟HTTP；不得把它当作真实容器/扫描或生产MFA验收。完整验证与并行旧前端基线变动等限制见 [全栈进度](../docs/extension/fullstack-progress.md)。

## P10 租户品牌与目录

以下为新版新增路由，旧版迁移的`route-matrix.json`仍只追踪原有路由，不把这些页面列为旧前端已有能力。

| 路由 | 最低权限 | 功能 |
| --- | --- | --- |
| `/tenant/brand` | tenant:brand:view | 当前租户/门户的品牌、三种登录布局、上传、预览、草稿与发布历史；编辑/发布/回退分别授权 |
| `/brand-preview` | 无业务读取权限 | 独立视觉预览frame，只接受同源父窗口的受检消息，不调用认证或草稿接口 |
| `/tenant/tenants` | tenant:tenant:view | 平台租户目录、登记与状态变更；还需部署平台操作者身份 |
| `/tenant/domains` | tenant:domain:view | 归属租户选项、域名登记、DNS挑战、验证证据、显式启停和主域名；动作分别授权 |

登记租户不会自动创建业务数据库或账号；域名验证通过后还需显式启用，界面不能跳过服务端证据校验。请求结果未确认时保留待核对操作，查询原回执或当前状态，不自动重发；离开不会撤销服务端操作。锁屏隐藏域名详情，解锁后继续核对原编号。

模拟浏览器使用`playwright.tenant.config.ts`，目录专项只属于next工程。真实目录用`SMA_E2E_PLATFORM_OPERATOR=1`运行隔离runner及`tenant-directory.spec.ts`，仅对该次正常引导创建的临时账号登记部署运维身份。部署说明见[运行说明](../docs/tenant-platform-operations.md)，交付和验证边界见[P10进度](../docs/tenant-platform-progress.md)。

