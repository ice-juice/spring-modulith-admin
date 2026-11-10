# JBolt 产品能力迁移执行方案

2026-11-10。本文件按最终决定更新，详细产品证据保留在 [产品设计](jbolt-product-design.md) 和 [研究资料](jbolt-research/README.md)。框架约束以 [README](../README.md) 为准，逐项实施与测试记录以 [开发进度](development-progress.md) 为准。

## 1. 实施原则

以 JBolt 的产品功能和交互为参考，在 Spring Boot 4.1.2、Spring Modulith 2.1.2、Java 21 上重新实现。保留参考工程源码；新工程使用自己的 Flyway 表、REST API、Vue 3 页面和测试。

前后端分离，首版 MySQL 8。根包 `com.sma.admin`，数据库和账号同名 `springboot_modulith_admin`。本地使用 mvnd，标准构建使用 Maven Wrapper。PowerShell 和 Java 输出固定 UTF-8。

## 2. 模块与数据所有权

| 模块 | 职责 | 主要交互 |
|---|---|---|
| common | 响应、异常、i18n、UTC、持久化、安全注解与 SPI | 不依赖业务模块 |
| auth | 登录、验证码、会话、锁屏、强退、过期维护 | IAM 查询、Config 参数偏好 |
| iam | 用户、部门、岗位、角色、资源、授权、导航、个人资料 | FileQuery 校验头像，贡献权限及文件策略 |
| config | 字典、参数、偏好、名称翻译与参数缓存 | 公开只读查询及 CacheRegion |
| audit | 登录和操作日志、离线 IP 定位 | 消费可靠事件 |
| file | 元数据、本地存储、受控下载、图片验证 | 通过 FileAccessPolicy 判断业务引用 |
| message | 通知、待办、私信、汇总和 WebSocket | IAM 受众/通讯录、FileQuery、Auth SessionQuery |
| monitor | 服务器/连接池、SQL、缓存、失败事件、数据库文档 | SPI 和框架 API，不读其他模块业务表 |
| openapi | 应用中心、凭据、应用令牌、签名及 nonce | 独立应用鉴权链 |
| workspace | 操作台统计、个人事项 | DashboardContributor 汇总 |

启动类的直接子包就是 Modulith 模块。公开 record、接口与事件放模块根包，实现放 `internal`。不跨模块查询、连接或更新表，不建跨模块外键。模块依赖无环且列入白名单，由架构测试强制执行。

## 3. 从 JBolt 转换的设计

| 能力 | 最终实现 |
|---|---|
| 注解权限 | 每个 Controller 声明 RequiresPermission / PermitLoggedIn / PermitAnonymous，默认拒绝 |
| 权限目录 | 各模块 PermissionCatalog + 注解扫描汇总；内置保护，保留管理员自定义 |
| 角色用户岗位 | 多对多关联表，父子角色不继承授权；用户分页可按角色/岗位筛选 |
| 数据权限 | ALL / DEPARTMENT_TREE / DEPARTMENT / SELF / CUSTOM，多角色并集，数据库查询再取交集 |
| 多租户兼容 | 业务表 tenant_id，租户插件过滤；超管同样受限，首版不含租户开通平台 |
| 多后台入口 | portal 隔离用户、权限、导航、消息与应用 |
| 认证 | BCrypt，单次验证码，服务端随机令牌哈希，凭据版本使旧会话失效 |
| 登录策略 | 参数控制验证码、保持登录、单终端、空闲锁屏、改密提醒 |
| 缓存 | 参数缓存按租户和版本刷新；管理通过 CacheRegion SPI，仅显示统计 |
| 日志 | 事务事件 + ApplicationModuleListener，幂等入库，失败可重试，不记录请求正文或凭据 |
| 多语言 | zh-CN / en-US；菜单、字典、参数名称可翻译，业务内容按原文保存 |
| 时间 | Instant / DATETIME(3) UTC；个人偏好 > 租户默认 > 浏览器 > UTC，使用 IANA 时区 |
| 通知 | 五类受众发布快照、读回执、附件、回收站；服务端清洗富文本 |
| 待办 | 五状态、四类内容/链接组合、截止时间和第三方来源；结束状态保护 |
| 私信 | 同租户 portal 双方授权，收件人已读操作，普通文本渲染 |
| 实时消息 | 一次性握手票据、绑定会话、提交后推送、重连和定时补查 |
| 文件 | 随机存储键、20 MiB 限制、真实图片解码、ACL/引用保护、回滚清理 |
| 数据库监控 | Hikari 指标 + 实际 JDBC 聚合统计，不保存 SQL 正文或参数 |
| 数据库文档 | JDBC 元数据读取表、字段、索引，双语言 Markdown 导出 |
| 应用中心 | 13 种分类、一次性显示密钥、AES-GCM 加密、轮换、关联对象元数据 |
| 对外鉴权 | 独立不透明应用令牌、HMAC-SHA256、时间戳、数据库 nonce 防重放 |
| 操作台 | 各模块只贡献当前用户可见的统计和个人事项，聚合层不跨模块查表 |

详细协同协议见 [message-design.md](message-design.md)，应用签名与客户端见 [openapi-design.md](openapi-design.md)。

## 4. 范围和执行顺序

| 阶段 | 范围 | 状态 |
|---|---|---|
| P0 | 改名、mvnd、测试、i18n、UTC、租户/持久化/数据权限、前端底座 | 完成 |
| P1 | IAM、Auth 和管理框架页面 | 完成 |
| P2 | Config、Audit 和页面 | 完成 |
| P3 | File、Message、Monitor 和页面 | 完成 |
| P4 | 应用中心、对外鉴权、操作台、全量对照 | 代码和自动验证完成；登录后的人工浏览器验收待办 |

每一功能先实现，再运行验证，回写进度并提交 Git 后继续。标准 Maven Wrapper 完整 verify 已通过 101 项测试，前端构建/ESLint、32 项 Vitest、10 模块双语言检查、Node 客户端 3 项协议测试通过。最终对照和验证边界见 [acceptance.md](acceptance.md)。

本期排除 UReport、微信公众平台、七牛、敏感词、代码生成平台。用户明确 hiprint 与 AI 中心另期实施。首版文件只实现本地适配，数据库只实现 MySQL；接口保留后续扩展空间。

## 5. 环境与最终验收

JDK 8 运行 JBolt 参考实例，JDK 21 运行新框架。MySQL 当前为 Windows 临时实例；WSL 尚需管理员启用和重启，不视作已完成。环境步骤见 [dev-environment.md](dev-environment.md)。部署及持久化见 [deployment.md](deployment.md)。

Browser Runtime 中密码和验证码须由用户本人输入。自动测试已验证认证和受保护业务，浏览器已验证匿名路由和登录页；用户完成登录后，按验收文档继续真实页面巡检。P4-4 保持进行中，直到这一步完成。
