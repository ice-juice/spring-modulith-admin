# 新前端技术决策与组件来源

日期：2026-11-11。方案依据：P04；用户最新要求优先：工程始终保留 `frontend-next/` 名称，旧 `frontend/` 源码保留。

## 实际采用版本

以 `frontend-next/package-lock.json` 为唯一安装锁定清单，不使用 `--force` / `--legacy-peer-deps`。

| 能力 | 采用 | 与 P04 初稿的关系 |
| --- | --- | --- |
| SPA | Vue 3.5.42、TypeScript 6、Vite 8.3.7 | Vite 使用已修复版本；独立工程不影响旧工具链 |
| 状态/路由 | Pinia 4.0.3、Vue Router 5.3.1 | 保留后端菜单、权限、令牌与路由协议 |
| 基础 UI | shadcn-vue CLI 2.9.0 生成的 new-york 源码、Reka UI 2.11 | CLI 只用于生成，不留作常驻依赖 |
| CSS | Tailwind CSS / @tailwindcss/vite 4.3.5 | 使用 v4 CSS 入口、语义变量和明暗主题 |
| 表格 | @tanstack/vue-table 9.3.1 | 使用 v9 `useTable` / features API；服务端分页和排序 |
| 表单 | VeeValidate 4.15.1 + Zod 4.6.7 | Zod 3 依赖审计存在问题，移除 @vee-validate/zod；以公开 TypedSchema 接口实现薄适配层 |
| 图标 | @lucide/vue | 与当前官方生成组件一致；旧名称使用显式映射 |
| 富文本 | Tiptap 3.33.0 + DOMPurify | 兼容既有 HTML 格式；展示前净化；按路由延迟加载 |
| 国际化 | vue-i18n 11.4.18、dayjs 1.11.23 | 公共语言预先加载，业务模块按路由加载，UTC / IANA 转换 |
| 测试 | Vitest、Vue Test Utils、Playwright、axe-core | 单元、模拟接口、隔离真实后端、可访问性分别记录 |

## 组件源码与定制

- 来源：https://www.shadcn-vue.com ，CLI 固定为 2.9.0，采用 new-york 风格。源码放在 `src/components/ui/`，MIT 许可保存为 `LICENSE.shadcn-vue`。
- 基础定制：`cn` 使用项目工具，关闭按钮国际化，Checkbox 半选图形，确认层位于编辑层上方；保留组件可访问性属性透传。
- 业务组合：PageHeader、FilterBar、DataTable、EditorDialog、EntitySelect、PermissionTree、FilePicker、文件/头像/附件与统一反馈位于业务层。基础组件不请求业务 API。
- v0.1.1 新增 ScrollArea，直接组合既有 Reka UI 的 Root/Viewport/Scrollbar/Thumb，未新增依赖。样式使用主题色和透明轨道；保留原生滚动，滚动条支持拖动和聚焦显示。它是项目组合组件，不归入此前 CLI 已生成组件的数量。
- 权限树默认保持旧版独立勾选；级联需显式传参。搜索不改写隐藏项选择，禁用项不可被修改。
- 确认框的肯定动作由项目 Button 直接结算结果，避免 Reka 关闭事件先于点击事件把确认错误结算为取消。

## 状态、查询和持久化

- 不跨工程导入源码或复制登录秘密。沿用令牌协议、公共 UI 偏好键，新增表格与标签键带 `sma.next` 版本/用户标识。
- 标签按路由路径归一身份，更新查询不产生重复页签。表格列显隐、密度、受支持排序按用户和表格隔离；损坏存储安全回退。
- URL 同步：用户的 roleId/focus、字典 typeId、消息中心 tab、对象详情 focus、受控 frame id。普通列表临时关键词、日期和页码只存于当前页面，重新进入重置；不把敏感筛选或表单写入 URL。
- 列表采用请求代次或请求门，忽略迟到响应；消息票据、连接与汇总同时校验用户及代次。写请求不自动重试。
- 截止时间区分本地输入与 UTC；DST 空缺/重复输入明确拒绝。编辑未变更的原截止时间保留精确时刻。
- 参数分组选项使用业务 groupKey；敏感参数留空表示保留，遮蔽值不会写回。后端字段校验错误映射到对应字段。
- 新布尔参数默认提交明确的 `false`，与开关的关闭状态一致；表单卸载时允许字段暂时不存在，不保留失效监听读取。
- EntitySelect 在浮层打开期间使用 Reka 的 `always` 定位策略，避免锚点宽度参与 ResizeObserver 时产生循环通知；关闭后由底层定位组件清理。

## 依赖审计边界

本轮 `npm audit --omit=dev` 为 0。完整审计曾报告开发工具链 14 条 moderate，主要为 ESLint / i18n lint / Vue Test Utils 的 glob、minimatch 传递依赖；无 high/critical。它们不会进入生产静态包。没有为消除计数而强制跨主版本覆盖依赖。最终审计结果见验收记录；后续工具链升级应重新运行完整门禁。

## 发布策略

新旧工程分别构建；新工程保持独立命名。发布只替换静态资源指向，不包含数据库迁移，不改后端业务和既有启动脚本。正式环境切换需要使用已核验的新产物，并保留原产物及部署指向用于回退。

## v0.2.0 鸢尾紫柔光重构

用户已确认鸢尾紫。保持原 Vue/shadcn-vue/REKA/Tailwind 4 和锁定依赖，仅提升应用版本元数据；没有引入新 UI/字体/图像或动效网络依赖。使用主题 token 与共享组件覆盖所有页面；导航组从顶部横排移到侧栏下拉，工作标签独立。角色默认双栏授权，使用原 GET/PUT 接口并保护草稿/请求归属。工作台不伪造原接口没有的趋势数据。

验证依据：[本轮进度](softlight-progress.md)、[路由覆盖](softlight-coverage.md)、`test-evidence.softlight-*.json`。前后端测试分开，真实测试基于证据中注明哈希的既有 JAR 副本；不声称验收并行后端工作。生产包使用新的 `playwright.production.config.ts` 在 preview 服务验证，不复用 dev server。旧版本 ZIP 保留；未切换正式部署或旧启动脚本。
