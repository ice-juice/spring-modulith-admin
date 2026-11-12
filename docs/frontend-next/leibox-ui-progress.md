# LeiBox 交互功能实施与验收

日期：2026-11-12。用户已确认开发三个预览方向。本任务归属可复用前端基座，main；不修改后端契约、数据库或业务二开目录。

## 方案与边界

- 快捷标签栏：在个人随手栏提供分类切换及展开的授权页面入口；复用服务端 targets、稳定菜单 ID、现有工作标签导航和未保存保护。点击时重新读取授权目标；不保存任意 URL 或搜索词。
- 可调宽度面板：在 components/business 提供通用双栏组合，接入角色目录/权限以及规则编辑区。拖动、方向键、Home/End、Escape/双击复位；边界由容器及内容最小宽度决定，窄屏上下布局，布局变化不重建表单。
- 收藏视图：在 BookmarkList 切换列表/卡片，保持 DOM 身份、搜索词、重命名草稿和排序。按服务端 scope 保存纯布局偏好；原写入/UNKNOWN 恢复协议不变。
- 原始 LeiBox Vue 包保存在 leibox-reference 作为可追溯参考（不参与产品构建）。审查后以项目 Vue 组件/语义主题/现有图标与权限契约适配；不直接引入演示内容或 HTML 字符串。原包收藏动效依赖 GSAP，最终采用原生 DOM 动画重写，未增加运行依赖。来源与署名写入该目录 README。
- 影响路径：frontend-next/src/components/business/、src/features/workspace/、src/features/iam/RolesPage.vue、src/features/rule/、src/locales/{zh-CN,en-US}/common.json、对应 e2e/单元测试与文档。已修改文件先读差异；保留并行安全/租户/缓存工作，不重启用户服务。

## 顺序与验收

| 项 | 依赖 | 内容与验收方法 | 状态 |
| --- | --- | --- | --- |
| L1 | 无 | 快捷标签栏、真实授权目标与导航；类型/i18n/lint、浏览器展开/选择/权限变化/窄屏 | 完成 |
| L2 | L1 | 通用双栏与两处接入；鼠标/触屏/键盘/上下限/容器缩放/草稿保留浏览器验收 | 完成 |
| L3 | L2 | 收藏卡片/列表、scope 偏好、连续布局；切换不丢别名/搜索/排序、失效入口、锁屏/身份隔离 | 完成 |
| L4 | L3 | 完整 npm run verify、原随手栏/IAM/规则受影响回归；README/规则/来源与实际证据 | 完成（固定副本、本地） |

本专项不实现 P08/P09/P10/安全专项的协议改动；只引用其现有交互门禁。模拟接口浏览器验证与真实后端验证分别记录，不把 UI 回归当成服务端新增能力验收。

## 执行记录

### 提交版本独立验证（2026-11-12）

按用户要求仅提交本功能，以 `ab8f46a` 主线加本次暂存树导出独立副本 `frontend-next/.e2e-runtime/leibox-commit/candidate/`，排除安全、租户、缓存等未提交修改；共32个相关文件。语言包、README、AGENTS和主进度按改动块提取。表格测试只提交可配置端口修正，针对并行配置页新默认tab的两条选择逻辑保留在工作区，不引入尚未提交的配置页依赖。

- `npm run verify` 在旧前端基线检查处失败：主线9个旧前端文件内容与主线 baseline.json 已不一致（包含 audit/auth/iam 双语、LoginView、TotpView、vite.config），不是本次文件改动；保留 `candidate-verify.log`，未改基线或把该命令报告为通过。
- 继续逐项执行主线路由清单、type-check、type-check:tests、lint、i18n:check、test:unit、vite build、release:check，全部通过：47文件162单测、13模块双语、入口164KiB gzip、37懒加载模块、293产物哈希。日志 `candidate-checks.log`；该数量与下文包含并行修改的317项验证属于不同源码集合。
- 该独立生产包执行 `SMA_LEIBOX_STATIC=1 npx playwright test --config playwright.leibox.config.ts leibox-ui.spec.ts`，4/4、15.4秒通过，覆盖三个功能、草稿保留、授权重读、触屏/键盘、窄屏/双语/无障碍。日志 `candidate-browser.log`。未触及用户运行服务，也未提交独立副本或验证生成物。

### 最终验证（2026-11-12）

固定副本：`frontend-next/.e2e-runtime/leibox-verification-20261112/`，复制995个文件后逐个SHA-256核对无变化，随后补入旧前端基线明确包含的6个被忽略文件。不是git HEAD快照，包含复制时的既有并行修改；源码清单在 `source-manifest.json`。

- `npm run verify`：退出0；旧前端128项哈希、30条路由清单、产品/测试类型、ESLint、13模块双语、62文件317项单测、生产构建和发布检查全部通过。入口168KiB gzip，38个懒加载模块在预算内，298个产物哈希；日志 `verify-final.log`。首次快照因遗漏6个忽略的历史基线文件失败，已从原工作区补齐，不改写基线。
- `$env:SMA_LEIBOX_STATIC='1'; npx playwright test --config playwright.leibox.config.ts`：使用同一生产包、独立5216端口。最终综合轮30/31通过，最后一项表格回归修正默认tab假设后单独1/1通过，共31项分轮通过；日志 `production-regression-final.log` 与 `table-workspace-final-recheck.log`，不宣称单轮31全绿。
- 原随手栏 `npx playwright test --config playwright.shelf.config.ts`：固定副本中的配置仅把dev改为preview（5198），生产包4/4、37.2秒，覆盖标签操作、便签自动保存/冲突/归档恢复、浮窗/固定/窄屏与锁屏退出。日志 `shelf-production-regression.log`。首次dev轮3/4，首项在页面goto等待load阶段超时，未改功能或增加超时；同包preview复验通过。
- 回归测试修正：规则场景保存等待精确请求记录后再断言；规则表格夹具沿用可配置的测试端口；字典/参数页显式选择私有字典项/租户私有参数再检查DataTable布局，默认共享视图不冒充旧列表。端口/异步断言修正后测试类型与专项lint通过，最后tab定位修正后专项lint与整项桌面/手机回归通过。失败历史见 `production-regression.log`、`production-regression-final.log`、`table-workspace-final.log`（纠正中文定位后中止）及最终复验日志。
- 视觉与交互：检查角色页浅色和320px深色收藏截图；浏览器覆盖鼠标/真实触屏指针、键盘上下限/复位、390px双栏堆叠、规则表单DOM身份、搜索与别名草稿保留、失效入口、布局scope隔离、减少动态效果、英文深色axe AA零违规。原随手栏另有明暗/双语与多浮窗回归。
- 最终14个本任务源码/测试/配置文件与验证副本一致，后续两处回归测试修正已同步，见 `final-source-check.json`。其他前端生产源码一致；并行工作后来追加了 `stores/__tests__/messages.test.ts`，该新增用例不在本轮317项证据范围内。未验证后端新能力、真实接口联调或生产部署，未重启用户服务、未覆盖发布ZIP。
- README、前端README/AGENTS、主开发进度与来源记录已同步，定向diff空白检查通过。

### 分项记录

- 初始核对：main 存在大量并行未提交修改，随手栏 store、管理壳、common 语言包亦有既有修改。本轮只在明确接入点增量编辑。三个 Vue 包已从实时 LeiBox 目录下载，未执行其演示引擎。
- L1：`npm run type-check`、`npm run i18n:check`（13模块双语）、新组件/接入/测试专项 ESLint 通过；`npx playwright test --config playwright.leibox.config.ts` 1/1，12.4秒，独立5216端口、模拟接口。验证真实授权目标来源、点击前撤权不跳转、无写请求、320px无整页横溢、方向键与Escape返回。记录位于 frontend-next/test-results-leibox/results.json（后续轮次会更新）。

- L2：类型/i18n/专项lint通过；浏览器 resiz 两项最终2/2（角色草稿与规则DOM身份），新增CDP真实触屏输入专项1/1。鼠标64px拖动、触屏40px拖动、Home/End边界、Escape/双击复位、390px上下布局与恢复均通过，无业务写入。首轮误把登录/WS票据计入角色写入、规则输入定位到隐藏表单，已改为精确业务路径与名称定位；ResizeObserver改为下一动画帧更新并取消销毁帧，复验无循环通知。原始参考采用固定演示DOM，产品用Vue模板保留草稿与授权上下文。

- L3：type-check/type-check:tests/i18n/专项ESLint通过；bookmark-layout单元2/2，覆盖scope切换、损坏/拒绝存储。收藏浏览器专项1/1（10.2秒），覆盖DOM身份与别名草稿、过滤/分类切换、排序一次写入、失效入口禁用、刷新恢复、scope更换默认列表、敏感草稿未写入存储、320px深色和reduced-motion、英文深色axe AA零违规。首轮测试写错现有搜索标签文案，已按语言包改为搜索页面标签复验。
