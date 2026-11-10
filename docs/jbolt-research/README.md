# JBolt 研究资料索引

研究对象：`..\jbolt_ai_platform_jfinal_customer`（JBoltAI Platform 4.5.0，JFinal 基座版，jbolt_core 5.3.6）。
新系统目标：Spring Boot 4.1 + Spring Modulith 2.1 的前后端分离后台管理系统，首版只支持 MySQL。

| 文档 | 内容 | 来源 |
|---|---|---|
| [01-product-design.md](01-product-design.md) | 产品定位、菜单结构、各功能模块、全局参数、交互模式 | 人工分析 |
| [02-core-capabilities.md](02-core-capabilities.md) | 技术栈、请求链路、权限模型、会话与 API 安全、数据访问基座、缓存、事件、扩展点、SaaS、代码生成 | 人工分析 |
| [03-data-dictionary.md](03-data-dictionary.md) | 60 张表、1006 个字段的完整数据字典 | 脚本生成 |
| [04-data-model-analysis.md](04-data-model-analysis.md) | 实体关系、建模约定、问题与新系统建模建议 | 人工分析 |
| [05-endpoint-inventory.md](05-endpoint-inventory.md) | 80 个路由前缀、805 个控制器动作及权限注解 | 脚本生成 |
| [jbolt-core-api.md](jbolt-core-api.md) | jbolt_core.jar 全部类、公共方法、常量 | 脚本生成 |
| [06-runtime-observation.md](06-runtime-observation.md) | JBolt 运行观察：启动初始化行为、登录、24 个菜单页面巡检结果 | 运行巡检 + 人工浏览补充 |
| [07-feature-catalog.md](07-feature-catalog.md) | 功能目录：每个菜单的页面元素（查询、工具栏、列表、行操作、表单）与全部控制器动作（含 Javadoc） | 脚本生成 |
| [08-config-catalog.md](08-config-catalog.md) | 配置目录：全局参数分组与 39 项参数、41 类内置字典与 301 个字典项 | 脚本生成（参考库导出） |

完整的产品设计与功能地图见 [../jbolt-product-design.md](../jbolt-product-design.md)。

生成脚本在 `tools/jbolt-research/`：`parse_schema.py`（DDL → 数据字典）、`inventory_controllers.py`（路由与动作清单）、`dump_core_jar.py`（class 文件解析）。

## 关键结论

1. JBolt 的产品价值集中在“组织权限 + 系统配置 + 安全审计 + 消息中心 + 开发平台”这一套后台底座，且大量能力可以通过全局参数开关。
2. 核心逻辑在闭源 jbolt_core.jar：项目源码里的 UserService、RoleService 等大多是空子类。新系统必须按功能重写，不能直接复用代码。
3. 安全模型是“服务端会话 + 注解权限 + 角色组合缓存”，支持在线用户、强退、单终端、锁屏，没有数据权限。
4. 数据模型为兼容 5 种数据库而做了妥协（char(1) 布尔、无外键、逗号串多对多），新系统只支持 MySQL，可以直接规范化。

## 待验证事项

| 事项 | 验证方式 | 状态 |
|---|---|---|
| 密码散列的具体算法与拼接方式 | 反编译 JBoltUserService | 已确认：sha256(salt + md5(明文))，前端提交 2+md5+3 混淆串 |
| 角色父子关系是否参与权限继承 | 反编译 JBoltRolePermissionService / JBoltPermissionCache | 已确认：不继承，只取直接授权角色的并集 |
| 超管是否拥有全部权限 | 反编译 JBoltAuthInterceptorKit | 已确认：只放行 @UnCheckIfSystemAdmin 与“超管默认”权限，其余按角色校验 |
| 单终端登录、锁屏、强退的实际交互 | 运行 JBolt 实测 | 待执行 |
| 导入导出、通知推送等页面细节 | 运行 JBolt 实测 | 待执行 |

