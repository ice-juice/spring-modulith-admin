# Springboot Modulith Admin 开发手册

版本：2026-11-10。用于接手开发、运行、验证和维护本仓库。框架约束原文见 [README](../README.md)，业务使用见 [用户手册](user-manual.md)，本轮问题与验证证据见 [浏览器巡检记录](browser-audit.md)。

## 1. 工程与工具链

| 部分 | 技术与目录 |
|---|---|
| 后端 | `backend/`；Java 21、Spring Boot 4.1.2、Spring Modulith 2.1.2、MyBatis-Plus、Flyway、Spring Security。 |
| 前端 | `frontend/`；Vue 3、TypeScript、Vite、Element Plus、Pinia、vue-i18n。 |
| 数据库 | MySQL 8.0+，本机使用 8.4.10；首版没有其他数据库实现。 |
| 工具 | `tools/dev/` 验证与运行脚本，`tools/setup/` 环境配置，`tools/openapi/` 协议客户端。 |
| 文档 | `docs/`；决策在 `docs/proposals/`，进度、验收、部署分别维护。 |

Node 版本由根目录 `.nvmdrc` 锁定，当前为 26.8.1。具体依赖以 Maven POM 和 npm 锁文件为准，不能只看全局安装版本。

本机默认 `JAVA_HOME` 是供 JBolt 参考环境使用的 JDK 8，本项目必须使用 `JAVA21_HOME` 指向的 JDK 21。优先在当前 PowerShell 会话设置，不必修改其他项目的全局 Java 环境。

## 2. 本机启动

以下命令从仓库根目录执行。路径中有空格时始终加引号。

### 数据库和前置检查

```powershell
node --version
& (Join-Path $env:JAVA21_HOME 'bin/java.exe') -version
Get-NetTCPConnection -State Listen -LocalPort 3306,8080,5173 -ErrorAction SilentlyContinue
pwsh -NoProfile -File tools/setup/temp-windows-mysql.ps1 -Action status
```

本机 MySQL 使用上一级 `jbolt_reference_run/temp-mysql` 的数据目录；运行方式与环境配置见 [dev-environment.md](dev-environment.md)。若未运行，可使用该脚本的 `-Action start`。不要启动第二个实例争用同一数据目录或端口，也不要结束其他项目的 Java/Node/MySQL 进程。

默认开发库为 `springboot_modulith_admin`。数据库目标由 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` 控制。共享或正式环境应设置专属口令，不能沿用本机示例口令。

### 构建和运行后端

```powershell
pwsh -NoProfile -File tools/dev/verify-backend.ps1
pwsh -NoProfile -File tools/dev/run-backend.ps1
```

第二条命令在当前终端持续运行服务。脚本把构建 JAR 复制到 `backend/target/run/` 再启动，避免 Windows 锁住 Maven 正在生成的文件。启动前应先停止属于本项目的旧后端；`run-backend.ps1` 本身不负责停止旧进程。

从仓库根目录启动，保持 `data/` 相对路径稳定。开发主密钥默认在 `data/keys/openapi.key`，上传文件也使用持久目录；随意更换工作目录可能导致读取另一套目录。

首次没有管理员时使用：

```powershell
pwsh -NoProfile -File tools/dev/start-with-admin.ps1
```

脚本通过本地隐藏输入获取初始密码，仅核对并重启属于本项目的 `8080` 进程。已有管理员不会被重置。本次巡检已确认本机存在 `admin`，日常启动不需要再次初始化。

### 运行前端

另开终端：

```powershell
Set-Location frontend
npm ci
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

依赖已安装且锁文件未变化时可跳过 `npm ci`。前端访问 <http://127.0.0.1:5173/>，Vite 将 `/api` 和 `/actuator` 代理到 `8080`，消息 WebSocket 也经 `/api` 代理。

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-RestMethod http://127.0.0.1:5173/actuator/health
```

两处都应返回 `UP`。端口正在监听只能证明进程存在，还需检查健康和页面实际请求。

## 3. 模块结构与边界

这是模块化单体：一个后端进程、一个业务库。`com.sma.admin` 的直接子包是模块，公共模块为 `common`。

| 模块 | 主要职责 |
|---|---|
| common | 统一响应、错误码、权限注解、租户/数据范围、持久化、时间、审计基础设施。 |
| auth | 验证码、登录、会话、锁屏、维护与失效。 |
| iam | 部门、岗位、角色、权限、用户、菜单、顶部导航、个人资料。 |
| config | 字典、参数、个人偏好、类型化配置读取。 |
| audit | 消费登录和操作事件，提供日志查询。 |
| file | 文件元数据、本地存储、鉴权、预览及引用保护。 |
| message | 通知、待办、私信、未读汇总、实时连接。 |
| monitor | 服务器、缓存、SQL、事件与数据库结构。 |
| openapi | 应用凭据、签名、令牌与独立机器身份。 |
| workspace | 汇总模块贡献的权限内工作台统计和事项。 |

模块根包只放对外接口、record DTO 和事件；实现放在 `internal/web`、`internal/service`、`internal/persistence`。控制器不能直接调用 Mapper，其他模块不能引用对方的 `internal`。跨模块只读查询调用公开 API，有副作用的联动通过模块事件。

各模块 `package-info.java` 必须声明名称和 `allowedDependencies`；公共命名接口如 `common :: security`、`common :: persistence` 需明确依赖。禁止循环依赖、跨模块 SQL、跨模块 join 和外键。

## 4. 数据与事务

- 每个模块只写自己的表，表名前缀与模块 ID 一致，如 `iam_user`。
- 业务实体继承 `TenantEntity`。租户、操作者由可信认证上下文填充，不能由外部请求任意指定。
- MyBatis 插件顺序：防全表更新 → 租户 → 数据范围 → 分页。数据范围收窄已有查询条件，超级管理员仍受租户隔离。
- 事务边界在 service；结构调整先获取结构锁，避免部门/角色并发移动形成环。
- 主键为 Long 雪花 ID，JSON 使用字符串。前端不能把业务 ID 转为 JavaScript Number。
- 时间使用 `Instant`、数据库 `DATETIME(3)`，存 UTC；JDBC 会话强制 UTC。前端根据用户时区展示，范围筛选使用半开区间 `[from,to)`。
- 逻辑删除后保留历史；生成列唯一索引允许删除后重新使用业务编码。

### Flyway

公共迁移在 `backend/src/main/resources/db/migration/__root/`，模块迁移在对应模块目录。模块版本独立递增。已发布迁移不可编辑，应新增迁移修正。

权限声明启动时补充缺失项。仅对旧版自动发现、仍以权限 key 命名且未自定义位置/名称的内置逻辑项，允许升级为显式声明；必须保留 ID、已有授权、翻译、排序和启停设置。不能用启动同步覆盖管理员配置。

## 5. REST、安全与事件

后台接口为 `/api/<module>/...`。每个请求处理方法显式声明以下策略之一：

- `@RequiresPermission("module:resource:action")`
- `@PermitLoggedIn`
- `@PermitAnonymous`，同时需要在安全过滤链中放行路径。

响应为 `ApiResponse { code, error, message, data }`；成功 `code=0`。业务异常可能是 HTTP 200 加非零 code，认证失败、无权限、校验失败等还使用相应 HTTP 状态。前端 `api/http.ts` 统一处理，不得只判断 HTTP 200。

请求通过 Bearer 令牌认证。令牌原文不入数据库，数据库存 SHA-256 哈希；密码使用 BCrypt。每次请求重算有效权限/数据范围，账号凭据版本变化使旧会话立即失效。

公共事件使用 record，位于模块根包。消费者用 `@ApplicationModuleListener`，通过事件中的可信租户恢复 `TenantScope`，处理要幂等。事件发布登记、失败状态和重投由 Modulith 管理，重启默认不自动重发所有未完成事件。

审计不记录请求正文、密码、Token、AppSecret 或敏感参数值。新增关键操作使用权限动作或 `@AuditAction`，业务回跳 URL 只含受控路径与标识。

## 6. 前端开发约定

| 目录 | 修改入口 |
|---|---|
| `src/router/` | 路由、模块语言包、标题、页面权限和登录跳转。 |
| `src/layouts/AdminLayout.vue` | 菜单、导航折叠、顶栏、多标签、消息连接生命周期。 |
| `src/views/` | 页面组件。 |
| `src/features/` | 领域类型、表单转换、通用资源配置、辅助函数和测试。 |
| `src/stores/` | 认证、偏好、品牌和消息状态。 |
| `src/locales/` | 按模块拆分的 zh-CN/en-US JSON。 |
| `src/api/http.ts` | API envelope、令牌/语言/时区请求头、401/423 处理。 |
| `src/style.css` | 共享布局、工具栏、弹窗、响应式和主题。 |

### 新增页面

1. 在所属模块添加后端接口和权限声明，准备双语言消息。
2. 添加 Vue 页面，在 router 中配置 `module`、`titleKey`、`permission`。
3. 在 PermissionCatalog 声明菜单及操作。前端按钮使用 `v-permission` 或显式权限条件；后端仍须独立鉴权。
4. 新增语言键同时修改两种语言，插值名称必须一致。
5. 依据行为添加组件或集成回归测试，跑相关检查，再通过实际页面点击验证。

### 常见实现陷阱

- `common`、`auth`、`message` 是布局首次渲染需要的语言包，随启动同步加载；业务页模块继续按需加载。不要用不等待完成的加载制造首帧缺失翻译键。
- 列表有 `row-click` 时，编辑/授权/删除等行内按钮要阻止冒泡，避免触发行选择和切换标签。
- 折叠菜单要保留图标、可访问名称和 title 插槽。新图标可在 `MenuIcon.vue` 的显式映射中扩展；未知图标有默认回退。
- 多标签按页面路径去重，保留该页面最新 query/focus；不要为每次筛选生成同名标签。
- 顶部语言、时区立即保存，个人偏好表单应跟随这些字段更新，不能覆盖其他未保存的字段。
- 工具栏控件限定合理宽度，表格在自己的容器横向滚动；`.workspace` 必须允许缩小。
- 弹窗限制视口宽高，正文独立滚动，保留可见的取消/保存按钮；窄窗口表单标签改为上方显示。
- 大整数、空值、敏感参数的空白保留语义、时间转换必须使用现有类型和辅助函数。
- 空列表、无搜索结果、加载中和请求失败应分别呈现，不能用初始空列表判断接口失败。

## 7. 验证与测试数据

```powershell
# 在仓库根目录
pwsh -NoProfile -File tools/dev/verify-frontend.ps1
pwsh -NoProfile -File tools/dev/verify-backend.ps1
node --test tools/openapi/client.test.mjs
```

前端统一脚本依次运行 ESLint、类型检查/构建、Vitest、双语言键与占位符检查。前端单独调试可在 `frontend/` 执行 `npm run test:unit -- path/to/test.ts`。

后端 `verify` 包含架构规则、Modulith 模块校验、单元/集成测试和打包。完整日志为 `backend/target/verify.log`；逐类结果在 `backend/target/surefire-reports/`。针对某类测试可使用：

```powershell
pwsh -NoProfile -File tools/dev/verify-backend.ps1 -MavenArgs '-Dtest=PermissionTests,TodoTests'
```

测试默认使用 `springboot_modulith_admin_test`，启动时清空并重建专用测试库。不要把 `SMA_TEST_DB_URL` 指向开发或正式业务库。安全检查只接受默认测试库或 `sma_test_` 前缀的明确库名；用户名/口令可由 `SMA_TEST_DB_USERNAME`、`SMA_TEST_DB_PASSWORD` 提供。

同一测试库不要并行启动多套 Maven 测试进程。测试建立的账号、授权、消息和删除操作仅是测试夹具，不等于在真实用户库执行过浏览器验收。

### 浏览器回归

使用实际开发站点，按 [巡检记录](browser-audit.md) 的页面清单检查：初次打开、菜单、查询、分页、表单校验、取消、跨页回跳、中英文、主题和不同面板宽度。等待异步请求完成后再判断结果。

需要真实账号切换、密码/验证码、上传/下载、发消息、授权或删除的场景，应明确测试数据和执行者。自动测试、只读检查与实际提交的覆盖分别记录。

## 8. 开放接口开发

后台管理 `/api/openapi` 与应用调用 `/open/**` 使用不同身份。AppSecret 加密保存，主密钥必须跨重启保留。创建或轮换只返回一次凭据；管理员令牌和应用令牌不可互换。

签名请求使用 `X-App-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature`，HMAC-SHA256 覆盖方法、原始路径、规范化查询串、时间戳、nonce 和正文哈希。实现不要自行更改编码和排序规则。

完整协议和参考代码见 [openapi-design.md](openapi-design.md) 与 [sma-client.mjs](../tools/openapi/sma-client.mjs)。签名关闭仍要求有效凭据/令牌。

## 9. 部署、重启与排障

前端生产产物为 `frontend/dist/`，后端产物为 `backend/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar`。反向代理配置、环境变量及 WebSocket 转发见 [deployment.md](deployment.md)。

每次更新先完成构建与相关验证，再核实本项目进程、工作目录和端口，停止旧进程后启动新产物。保留数据库、上传目录和主密钥。重启后检查健康、已有会话恢复和核心页面，而不只是看进程是否存在。

| 现象 | 排查顺序 |
|---|---|
| Java 版本不满足 | 检查当前进程 `JAVA_HOME`；Wrapper 切到 `JAVA21_HOME`，mvnd 检查自己的 Java 配置。 |
| 8080/5173 被占用 | 查看监听 PID 和命令行，区分本项目、其他项目、旧会话。 |
| 数据库连接失败 | 检查 3306、实例状态、库名与 DB 环境变量，再看后端日志。 |
| 数据或密钥像“丢失” | 检查启动工作目录和 `data/` 路径、数据库连接目标，勿重新生成覆盖主密钥。 |
| 代码改了页面没变 | 对照开发服务器返回的模块和磁盘源文件。若 Vite 未收到变更，停止该项目的 dev 进程并重新启动，再刷新页面。 |
| 页面显示语言键 | 检查键是否存在、模块语言包是否在渲染前完成加载；不要只添加重复翻译。 |
| 401/403/423 | 分别检查会话、权限与数据范围、锁屏状态；不能通过放宽全局安全链解决。 |
| 消息不同步 | 检查会话、一次性 WebSocket 票据、代理 Upgrade、连接状态与补查机制。 |
| 模块/架构测试失败 | 根据规则定位依赖或分层，调整公开 API/事件，不随意放宽白名单。 |

首版按单实例设计。认证限流、WebSocket 票据/分发、SQL 统计等在内存中；集群化需要单独设计共享状态和消息分发，不能仅复制进程。

## 10. 文档维护

新增能力同步更新用户入口说明、开发约束和验收证据。问题记录至少写明页面、步骤、现象、根因、修复位置和验证方式。未经实际操作的场景标明未执行，不以构建成功替代浏览器检查。
