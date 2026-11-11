# 顶部统一搜索

用户范围：移除左侧导航搜索区，顶部统一提供菜单与全局搜索，预留未来AI深度检索扩展。本轮沿用现有模块/目录与已授权查询API，不新增后端模块、数据库或AI服务，不改变授权。

## 已确定方案

- 顶部桌面显示搜索文字与Ctrl/⌘K，窄屏保留搜索图标；侧栏与移动导航不再放搜索输入。快捷键与点击打开同一弹窗。默认菜单模式，即时搜索当前账号全部授权菜单，不受当前导航分组限制。
- 全局模式搜索菜单、用户、文件、通知管理、个人待办、应用与系统参数。用户/文件/通知/待办/应用走服务端keyword+分页查询；参数接口按服务端keyword过滤完整列表，只展示名称与参数标识，不展示参数值/密钥。无权来源不发请求，服务端保留原租户/数据范围约束。只针对已接入来源的关键词检索，不宣称全文/附件内容/向量搜索已实现。
- 统一结果结构：id、source、title、description、icon、target；每来源独立加载/失败/更多结果，默认每来源5项，可分页追加。缺少权限或取消后不回填迟到结果，关闭/锁屏/账号变化清空查询，查询不持久化、不加到目标URL。目标只传focus ID，页面定位对应记录。
- `SearchProvider`在现有lib/search.ts定义可注册来源与能力标识(keyword/semantic)，输入query/page/size/signal/context，输出分页结果；UI只依赖该契约。未来AI来源可返回相同结果及可选引用/摘要，仍需服务端权限过滤，不由前端把全文或凭据传第三方。未注册AI来源不显示假按钮。
- 用现有Dialog组件及语义化结果列表，支持输入自动聚焦、上下键/Enter、Escape、Tab；明暗/皮肤与窄屏可用，保持表格内部滚动。

拟改路径：frontend-next/src/layouts/AdminLayout.vue、components/business/GlobalSearch.vue、lib/search.ts、composables/useGlobalSearch.ts、FilesPage/ResourcePage中focus定位，双语与对应测试；沿用Applications/Tasks/Notices已有focus跳转，必要时校验补齐。现有结构内的组件与查询适配，不调整仓库目录架构。

| 阶段 | 内容 | 验证 | 状态 |
| --- | --- | --- | --- |
| S1 | 检索来源契约与现有API适配、权限/取消/分页 | 单测：菜单跨分组、分页、未知来源、失败隔离、迟到结果 | 完成 |
| S2 | 顶部统一面板与侧栏移除、深链接定位 | 浏览器：快捷键/菜单/全局/更多/部分失败、窄屏 | 完成 |
| S3 | 回归、截图、门禁与文档 | verify、菜单/权限/表格回归、三皮肤/AA、真实查询接口联调 | 完成 |

S1：SearchProvider注册接口、六个现有API适配、受控路由/外链校验、取消与请求代际隔离、按来源分页/失败重试完成。`npx vitest run src/lib/__tests__/search.test.ts` **3/3通过**，应用类型检查通过；测试覆盖旧响应不覆盖新结果、权限不足不调用、semantic默认不启用、追加分页、重复注册拒绝。日志 `.e2e-runtime/search-s1-final.log`。

S2：`npx playwright test e2e/unified-search.spec.ts e2e/softlight-shell.spec.ts --reporter=list --output=.e2e-runtime/search-s2-final` **5/5通过**（24.5s），菜单跨导航组、快捷键、侧栏/手机移除搜索区、按来源分页/失败重试、权限不足不请求、参数值不显示、目标URL不携带搜索词、三皮肤/窄屏及弹窗AA通过。首次用例误写空态文案和模拟待办标题，按实际双语与fixture修正后通过，未改产品行为。

## S3 验收与证据

- 搜索/菜单/显示/AA/表格回归：`npx playwright test e2e/unified-search.spec.ts e2e/softlight-shell.spec.ts e2e/display-polish.spec.ts e2e/accessibility.spec.ts e2e/table-workspace.spec.ts --reporter=list --output=.e2e-runtime/search-regression` **14/14通过**（2.0m），包含200行内部滚动及三种窄/短视口；去除侧栏输入未破坏菜单分组、标签或表格尺寸。
- 补充同一文件结果反复打开：若目标与当前focus完全相同，先清理旧focus再导航，使既有详情监听重新加载；不把关键词/记录正文放入URL。极窄顶部隐藏重复标题避免显示单个截断字。最终 `e2e/unified-search.spec.ts` **4/4通过**（19.3s），日志 `.e2e-runtime/search-final-ui.log`。
- 最终 `npm run verify` **完整通过**（`.e2e-runtime/search-final-verify.log`）：29文件/74单测、类型、Lint、双语、旧前端123文件基线/30路由清单、构建及预算；入口151KiB gzip、34懒模块、271产物。
- 真实查询：`node scripts/run-real-e2e.mjs e2e-real/unified-search.spec.ts`，使用现有运行JAR的独立副本，端口8086/5196与随机隔离库，**1/1通过**（8.0s）；真实keyword查询定位admin记录、无结果及关键词不持久化通过。专属实例、数据库和账号已清理。日志 `.e2e-runtime/search-real.log`；未操作当前用户数据或重启8080。
- 截图 `images/unified-search-iris-light-1440.png`、`unified-search-celadon-dark-390.png`、`unified-search-amber-light-320.png`；已复核桌面浅色/320px浅色，弹窗AA自动检查覆盖三组。截图使用隔离模拟数据，不代表真实业务内容。

## 范围与扩展边界

全部改动在新版前端及文档中，没有后端架构、数据库迁移或新权限点。现有服务端查询继续决定租户和数据范围；前端预检查只减少无权请求。通知来源是拥有通知管理权限的管理列表，个人消息收件箱/私信不伪装成已实现的关键词检索。参数来源仅按既有API搜索名称/键，结果不展示值。

`SearchProvider`的请求包含query/page/size/AbortSignal/当前账号上下文；结果包含稳定id/source/title/description/icon/target，预留citations。注册冲突拒绝；默认只启用keyword能力，semantic必须经后续明确UI/后端接入后启用。没有向AI或第三方提交检索数据，也没有引入向量库/全文索引。

5174沿用现有源码服务，刷新可用；本次无需更新后端运行包，未覆盖历史发布ZIP。检索词不写本地存储或目标页面URL；既有GET查询接口仍在本站API查询参数中发送关键词，不声称消除服务端已有访问日志。
