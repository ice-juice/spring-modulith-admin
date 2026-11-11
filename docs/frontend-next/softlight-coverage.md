# 柔光重构 · 全路由覆盖清单

基于当前 `route-matrix.json` 的 28 条路由逐一核对。共享组件是正式业务页面实际导入的组件，不是独立样板。模拟回归 38/38 已通过，详见 `test-evidence.softlight-mock.json`；真实联调与最终产物另行记录。

| 路由 | 实际页面 | 改版落点 |
| --- | --- | --- |
| `/` | `views/HomeView.vue` | 柔光欢迎区、原创装饰、API 指标、自适应指标列、待办/快捷入口、上下文信息 |
| `/:pathMatch(.*)*` | `views/StatusView.vue` | 品牌色状态图形、明确 404 与返回入口 |
| `/403` | `views/StatusView.vue` | 品牌色状态图形、清晰权限说明与返回入口 |
| `/audit/logins` | `features/audit/LogsPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/audit/operations` | `features/audit/LogsPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/auth/sessions` | `features/auth/SessionsPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/config/dictionaries` | `features/config/DictionaryPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/config/parameters` | `features/config/ParametersPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/file/files` | `features/file/FilesPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/frame/:id` | `views/FrameView.vue` | 新表面容器；仍只解析已授权菜单 ID 与受控 iframe 目标 |
| `/iam/departments` | `features/iam/DepartmentsPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/iam/navigation` | `features/iam/NavigationPage.vue` | 导航组管理文案；保持原 API 与配置字段，宿主入口使用这些分组 |
| `/iam/permissions` | `features/iam/PermissionsPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/iam/posts` | `features/iam/PostsPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/iam/roles` | `features/iam/RolesPage.vue` | 默认双栏角色/权限；资料编辑、数据范围、独立权限树、草稿/迟到响应/保存保护 |
| `/iam/users` | `features/iam/UsersPage.vue` | 柔光成员概览、真实分页总数/当前页启用数、头像字标、合并筛选/表格表面；保留旧列键 |
| `/login` | `views/LoginView.vue` | 分栏品牌场景、同源品牌/背景配置、验证码/密码/记住登录及安全跳转保留 |
| `/message/inbox` | `features/message/InboxPage.vue` | 概览指标与分段导航；通知/待办/私信子页共享新表格与编辑组合 |
| `/message/notices` | `features/message/NoticesPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/message/todos` | `features/message/TasksPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/monitor/caches` | `features/monitor/CachesPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/monitor/database` | `features/monitor/DatabasePage.vue` | 统一主从表面、自适应表列表、表/索引局部滚动、导出保留 |
| `/monitor/events` | `features/monitor/PublicationsPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/monitor/server` | `features/monitor/ServerPage.vue` | 语义指标卡、统一字体与进度条、系统属性/连接池分组 |
| `/monitor/sql` | `features/monitor/SqlPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/openapi/apps` | `features/openapi/ApplicationsPage.vue` | 统一模块页头/说明、筛选与表格连续表面、密度/状态、编辑/详情弹窗；权限、请求与业务操作保持 |
| `/preferences` | `features/config/PreferencesPage.vue` | 宽版表单、分组偏好、明暗/语言/时区与标签兼容 |
| `/profile` | `features/iam/ProfilePage.vue` | 身份概览柔光表面、分组表单、头像与原草稿保护 |

## 对应验证

- `routes.spec.ts`：业务路由含消息中心三个页签在 1440/390px 正常渲染、无缺失翻译或文档溢出。
- `states.spec.ts`：22 组列表失败到空态恢复，以及刷新失败保留旧数据。
- `workflows.spec.ts`：iframe、安全、消息连接、受限用户、配置等契约。
- `visuals.spec.ts`：工作台/用户/角色，6 宽度 × 2 主题 × 2 语言；24 张实际页面图。
- `softlight-pages.spec.ts`：角色草稿/失败/迟到响应，登录 4 宽度 × 2 主题 × 2 语言、验证码与 AA；8 张登录图。
- `softlight-shell.spec.ts`：导航分组、无授权菜单、折叠/手机、快捷键、恢复/撤销分组回退、标签保留。
- `contracts.spec.ts` 与 `iam.spec.ts`：200 行、500 节点、五类数据范围、Long ID、错误保留、独立勾选与搜索隐藏选择。
- 其余业务写入由既有 `operations`/`foundation`/`display-polish` 套件覆盖；未通过隐藏失败或放宽原约束实现。

所有截图使用隔离模拟数据，证明实际前端渲染，不作为线上业务数据或实体设备验证。真实联调采用现有后端 JAR 的只读副本，具体哈希与独立运行记录在真实测试证据中，不声称重新构建/验收了其他并行修改的后端模块。
