# 01 JBolt 产品设计

> 研究对象：..\jbolt_ai_platform_jfinal_customer（JBoltAI Platform 4.5.0，JFinal 5.2.7 基座，jbolt_core 5.3.6）
> 证据来源：permission_init.json、dictionary_init.json、全局参数初始化代码、`_view/_admin` 页面、控制器源码、jbolt_core.jar 字节码中的方法与字符串。标注“待运行验证”的条目需要在 JBolt 实际运行后确认。

## 1. 产品定位

JBolt 是一个面向企业内部管理后台的“平台底座”。它提供开箱即用的组织权限体系、系统配置、日志监控、消息中心和开发工具，业务功能由二开或代码生成器在它上面扩展。这个工程是 JBoltAI 版本，在原版 JBolt Pro 之上增加了 AI 应用、知识库、模型资源管理等模块。

产品能力可以分成五层：

| 层次 | 内容 |
|---|---|
| 组织与权限 | 用户、部门、岗位、角色（树形）、权限资源（菜单与按钮同树）、角色授权、顶部导航分组、超级管理员 |
| 系统配置 | 数据字典（单级/多级）、全局参数（分组、内置项）、用户个人界面偏好、敏感词库、七牛存储账号与 Bucket |
| 安全与审计 | 登录（验证码、记住登录、单终端登录）、在线用户与强制下线、锁屏、异地登录识别、登录日志、关键操作日志 |
| 协同 | 系统通知（按接收人范围下发、已读统计、附件）、待办事项（含第三方来源字段）、站内私信、WebSocket 实时推送 |
| 开发平台 | 可视化代码生成器、数据库文档、API 应用中心（AppId/Secret、签名、JWT）、hiprint 打印设计器、UReport 报表、微信公众号/小程序、AI 数智化中心 |

## 2. 菜单结构（permission_init.json 初始化）

启动时如果 `jb_permission` 为空，`JBoltAutoInitData` 会读取 permission_init.json 写入下面这棵树。树里的每个节点都是一条权限资源：`isMenu=true` 的是菜单，`isMenu=false` 的是按钮或逻辑权限。

```text
操作台 [dashboard]
系统管理 [systemmgr]
├─ 用户管理 [user]          ├─ 部门管理 [dept]          ├─ 岗位管理 [post]
├─ 权限配置 [role_permission_menu]
│   ├─ 角色管理 [role]       ├─ 顶部导航 [topnav]        └─ 权限资源 [permission]
├─ 字典参数 [dictionary_config]
│   ├─ 数据字典 [dictionary] └─ 全局参数 [globalconfig]
├─ 系统通知 [sys_notice]
├─ 七牛配置 [qiniu_config]  ├─ 七牛账号 [qiniu]  └─ 七牛Bucket [qiniu_bucket]
└─ 敏感词词库 [sensitive_word]
系统监控 [jbolt_monitor]
├─ Druid数据库监控 [druid_monitor]   ├─ 服务器监控 [jbolt_server_monitor]
├─ 日志监控 [jbolt_log_monitor]：登录日志 [jbolt_login_log]、关键操作日志 [systemlog]
└─ 在线用户 [online_user]
开发平台 [dev_platform]
├─ 应用中心 [application]
├─ 微信公众平台 [wechat_mpinfo]（下挂 9 个按钮级权限：基础配置、菜单、支付、关注回复、关键词回复、默认回复、素材库、用户管理、其它配置）
├─ 报表设计器 [ureport_designer]（openType=2，iframe 打开）
├─ 打印设计器 [hiprint_design]（openType=2）
├─ 代码生成器 [jbolt_code_gen]
└─ 开发文档 [admin_dev_doc]：数据库文档 [admin_dev_doc_database]
独立逻辑权限 [logic_permission]（非菜单）：Ureport报表查看权 [ureport_detail]
AI_数智化中心 [jbolt_ai_dev]
├─ AI_Tools注册中心、AI_功能&应用注册中心、AI_应用开发中心
├─ AI_知识库中心：知识库、训练文件、训练结果、文件标签
├─ AI_全局参数配置
└─ AI_资源配置中心：LLM大模型、Embedding模型、向量数据库
```

设计要点：

- 菜单最多三级，`permission_level` 冗余存层级，`sort_rank` 控制同级顺序，后台提供上移、下移、初始化顺序。
- `is_system_admin_default` 标记“超级管理员默认拥有”的权限。超管看到的菜单 = 默认权限菜单；普通用户看到的菜单 = 其所有角色授权的菜单并集（`JBoltPermissionCache.getRoleMenus` 与 `getRoleMenusWithSystemAdminDefault`）。
- “独立逻辑权限”是不出现在菜单里的纯权限点，用于报表查看等非页面能力。这说明权限资源同时承担“菜单”和“能力开关”两种角色。
- `open_type`：1 默认（pjax 载入主区域）、2 iframe、3 dialog；`is_target_blank` 控制新窗口；`open_option` 存打开组件的 JSON 参数。

## 3. 功能模块逐项说明

### 3.1 用户管理（/admin/user，24 个动作）

- 列表：关键字（姓名、用户名、手机号、拼音码）模糊查询；按所属模块、性别、岗位、角色、启用状态筛选；启用部门时左侧部门树筛选，可切换“已分配部门/未分配部门”。非超管看不到超管账号。
- 新增、编辑：姓名、用户名、密码、工号、性别、年龄、手机、邮箱、头像、角色（多选）、部门、岗位（多选）、是否超管；启用“用户扩展信息”后同屏编辑扩展表（身份证、民族、地址、婚姻状态、政治面貌、职称等）。
- 其他：启用/禁用开关、重置密码、修改自己的密码和头像、导入导出（待运行验证）、按角色查看用户、为角色批量移除用户、用户选择器（自动补全、通知选人）。
- 约束：用户名唯一且不超过 40 字符；不能删除或禁用超管；不能删除、禁用自己；不能修改自己的角色。
- 新增时自动：生成拼音码、按性别给默认头像、写 `dept_path`、记录 `create_user_id`、记录操作日志。

### 3.2 部门、岗位

- 部门是树：`pid` + `dept_path`（祖先 ID 路径，用于“本部门及下级”查询，用户表也冗余一份 `dept_path`）。字段包括全称、类型（字典 dept_type）、负责人、电话、邮箱、地址、邮编、机构代码（唯一）、排序、启用。
- 部门管理有两种界面形态（全局参数 `JBOLT_DEPT_MGR_TYPE`）：树形表格或 JS 树。提供“部门路径补全”批处理。
- 岗位是平铺列表：名称、类型（字典 post_type）、编码、排序、启用。用户与岗位多对多，存在 `jb_user.posts` 逗号串里。
- 部门、岗位、用户扩展三项可以通过全局参数整体开关（`SYSTEM_DEPT_ENABLE`、`SYSTEM_POST_ENABLE`、`SYSTEM_USER_EXTEND_ENABLE`），关闭后相关界面元素隐藏。

### 3.3 角色、权限资源、角色授权、顶部导航

- 角色是树（`pid`），有名称和编码 `sn`。有下级或已被用户使用的角色不能删除。角色编码可生成 `RoleSn` 常量类供代码判断。
- 权限资源管理：树形维护菜单与按钮，字段见第 2 节；支持切换“超管默认拥有”。可以一键生成 `PermissionKey.java` 常量类（JBoltPermissionKeyGen）。
- 角色授权（/admin/rolepermission）：左侧角色，右侧整棵权限树勾选，提交后整体替换该角色的授权并清理缓存。
- 顶部导航：可以定义多个顶部导航项（名称、图标、排序、启用），每个导航项关联若干个一级菜单（`jb_topnav_menu`）。启用后，后台顶部显示导航条，点击切换左侧菜单组。

### 3.4 数据字典与全局参数

- 字典类型：名称、`type_key`、层级模式（一级 / 多级树）、启用、是否内置。字典项：名称、编号 `sn`、父级、排序、启用、是否内置。内置字典不可修改、不可禁用；是否允许删除字典由全局参数 `JBOLT_DICTIONARY_DELETE_ENABLE` 控制。
- 字典的消费方式：下拉选项接口（按 typeKey 取选项、取子级选项）、模板与表格翻译、代码生成器的“属性翻译”。启动时可选从 dictionary_init.json、extend_dictionary_init.json 检查补齐内置字典（共 41 类）。
- 全局参数按类型分组（系统基础、系统安全、备案、后台登录、后台样式、微信开发），值类型有 string、boolean、int；内置参数不能删除，可以改值。完整参数见第 4 节。

### 3.5 安全、会话与日志

- 登录：用户名 + 密码（前端明文提交，后端加盐 SHA-256 校验）+ 可选验证码（类型可配）+ “保持登录”。登录页文件可配置（login.html、ai_login.html 等多套模板）。
- 登录结果写 `jb_login_log`：IP、通过 ip2region 解析的国家/省/市、浏览器、操作系统、是否移动端、登录状态（成功、验证码错、用户名密码错、已禁用、未分配角色、未知异常）。
- 异地登录：与上次登录城市比较，不同则写 `jb_remote_login_log` 并标记用户 `is_remote_login`。
- 会话：登录后生成 sessionId 写入 `jb_online_user` 和 cookie `jboltid`，另发一个 JWT 形式的 refresh token cookie（`jbolt_refresh_token`）。保持登录与否对应两种 cookie 时长（默认 7 天 / 8 小时，可配）。
- 在线用户：状态有在线、离线、强退下线、异端顶替（单终端登录时被挤下线）；支持按用户、部门、岗位、角色查询在线会话；超管可强制下线他人（不能强退自己和超管）。每分钟定时清理过期会话。
- 锁屏：手动锁屏，或无操作超过 `JBOLT_AUTO_LOCKSCREEN_SECONDS` 自动锁屏；锁屏状态存在 `jb_online_user.screen_locked`，解锁需要输入登录密码。可以配置“自动登录后先进入锁屏”。
- 密码策略：`JBOLT_PASSWORD_CHANGE_NOTICE_DAYS` 天未改密码提醒。
- 关键操作日志：服务层在新增、修改、删除、恢复时调用 `addSaveSystemLog` 等方法，记录标题、类型、对象类型、对象 ID、操作人、详情链接和打开方式；日志列表可以直接点开被操作对象。

### 3.6 消息中心

- 系统通知：标题、富文本内容、类型、优先级（字典）、接收人类型（全部、指定角色/部门/岗位/用户，字典 sys_notice_receiver_type）、接收人值、附件、已读人数、逻辑删除。阅读记录存 `jb_sys_notice_reader`。
- 待办：所属用户、类型（链接、内容）、状态、优先级、规定完成时间、实际完成时间、取消时间、是否已读，并预留第三方流程字段（来源系统、来源消息/流程 ID、来源 URL、收发人第三方标识），可以承接 OA 或流程引擎推送的待办。
- 私信：发信人、收信人、内容。
- 后台右上角消息中心统一展示（/admin/msgcenter 14 个动作），新消息通过 WebSocket 推送（`/websocket.ws`，访客 token 接口 `/api/ws/guest/token`）。

### 3.7 系统监控

- Druid SQL 监控（受 `druid_monitor` 权限保护）、服务器监控（oshi：CPU、内存、磁盘、JVM 等）、Redis 管理（启用 Redis 缓存时）、缓存管理。

### 3.8 开发平台

- 代码生成器：`jb_code_gen`（165 个配置字段）+ `jb_code_gen_model_attr`（84 个字段）。可以选择数据源和表，配置主子表、树表、可编辑表格、页面布局（头部/左/右/底部区域）、工具栏按钮、导入导出 Excel、查询条件与模糊查询、表单组件与校验、字段翻译（字典/枚举/缓存/Service）、七牛上传、缓存策略；生成 Model、BaseModel、Service、Controller、Routes、页面，并自动写入菜单权限、绑定顶部导航和角色。
- 数据库文档：基于 screw 生成数据库结构文档。
- API 应用中心：维护应用（类型 13 种：内置平台、PC 网页、H5、公众号、各类小程序、APP 等），自动生成 AppId/AppSecret，可要求签名校验。对外 API 走 JWT（HS256，`jboltjwt` / `jboltrefreshjwt` 头）和签名（`jboltsignature`、`jbolttimestamp`、`jboltnonce`）。
- 打印设计器 hiprint：模板名称、编码、内容 JSON、测试 API 和测试数据。
- UReport 报表设计器（默认关闭）。
- 微信公众平台：多公众号/小程序管理、基础/支付配置、菜单、自动回复（关注、关键词、默认）、素材库、粉丝同步。

### 3.9 AI 数智化中心（JBoltAI 特有）

LLM、Embedding、向量库资源配置；AI 应用（81 个配置字段：模型参数、提示词模板、知识库、工具调用、UI 风格、发布方式）；知识库、训练文件（上传或抓取网页）、训练结果（问答对）；AI Tools/Function 注册（含 HTTP API 型工具）；AI 功能页面注册；应用发布后对外访问（访客、密码、系统内嵌、iframe 四种入口）。核心实现依赖闭源 `jboltai_sdk`。

## 4. 全局参数清单（JBoltGlobalConfigKey）

| 分组 | 参数 |
|---|---|
| 系统基础 | 系统名称、后台 LOGO、版权所有人及链接、静态资源版本号、Action Report 输出方式与行号、自动缓存 Debug 日志 |
| 系统安全 | 同一账号不能多端登录 `JBOLT_LOGIN_TERMINAL_ONLYONE`、保持登录/不保持登录 cookie 时长、无操作自动锁屏秒数、自动登录后锁屏、改密提醒天数、字典删除开关、JWT API 用户有效性检测开关与因子 |
| 备案 | 备案号、备案链接 |
| 后台登录 | 登录页文件、是否启用验证码、验证码类型、背景图、背景模糊、线条特效 |
| 后台样式 | 后台样式（主题）、是否多选项卡、刷新后保持选项卡、整体高度 H50、导航默认图标、全局搜索框、左侧导航宽度、部门管理方式、启用部门/岗位/用户扩展 |
| 微信开发 | 公众号服务器根 URL、小程序消息推送根 URL、静态资源根 URL |

用户个人偏好（`jb_user_config`）覆盖其中四项：后台样式、是否多选项卡、登录页背景模糊、登录页线条特效。

## 5. 交互与界面模式

- 后台主框架：左侧多级菜单 + 可选顶部导航分组 + 可选多选项卡；主区域用 pjax 局部加载页面；全局搜索框可搜索菜单。
- 页面打开方式统一抽象为 5 种布局：主区域（`__admin_layout`）、弹窗 dialog、iframe、独立单页 singlepage、JBoltLayer 侧滑层。请求头 `X-PJAX`、`_jb_rqtype_` 参数决定使用哪种布局渲染，同一个页面模板可以在不同容器中复用。
- 列表页：JBoltTable（自研 jQuery 表格，51 万行级别的 js），支持分页、固定列、列宽拖拽、行内编辑、树表、主从表、Excel 导入导出、复制粘贴、右键菜单、多条件筛选框。
- 表单：数据属性驱动（`data-rule` 校验、`data-autoload` + `data-url` 自动加载下拉选项、`data-switchbtn` 开关、`data-dialogbtn` 弹窗按钮），提交后返回统一 JSON（`state` + `msg` + `data`），按容器类型显示成功或失败页。
- 常用公共组件：部门树、用户选择、字典下拉、图片上传与裁剪、富文本（neditor、summernote）、日期、地区选择、拼音、二维码、PDF 预览、视频、打印。

## 6. 对新系统（前后端分离、首版仅 MySQL）的启示

1. 产品功能清单可以基本照搬第 2、3 节，但界面要用 SPA 重做：pjax/布局识别改为前端路由 + 组件容器（页面、抽屉、弹窗、iframe、新窗口）。
2. 统一 JSON 响应、下拉选项接口、树数据接口、开关切换接口、上移下移排序接口，这些是 JBolt 高频复用的后端约定，新系统应在 `common` 中做成标准件。
3. 部门/岗位/用户扩展的全局开关、顶部导航分组、超管默认权限、逻辑权限点、操作日志可回跳对象，这些是 JBolt 产品体验的亮点，建议保留。
4. 会话体系（在线用户、强退、单终端、锁屏、保持登录）依赖服务端状态，新系统用服务端令牌实现最自然。
5. 首版范围建议：组织权限 + 字典参数 + 安全审计 + 消息中心 + 文件 + 监控；代码生成器、API 应用中心放第二批；微信、hiprint、UReport、AI 中心按需。

