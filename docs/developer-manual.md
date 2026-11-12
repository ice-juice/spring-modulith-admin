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

这是模块化单体：一个后端进程、一个业务库。`com.sma.admin`（基座）和 `com.sma.business`（二开）的直接子包都是模块，公共模块为 `common`。模块 ID 在两边全局唯一；目录与接入方式见[二次开发与包管理](agent-rules/secondary-development.md)。

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

需要让多个租户继承同一参数或字典定义时，静态模块实现 `ConfigurationCatalog` 登记所属模块的键、类型、默认值和覆盖策略。业务参数用 `<bizid>.`、字典类型用 `<bizid>_` 前缀；运行读取使用 `ParameterQuery`、`DictionaryQuery` 或 `EffectiveConfigurationQuery`。平台运维人员管理共享草稿与发布，租户管理员按定义权限覆盖或恢复继承；版本和原请求键必须由管理客户端保留。具体 API、旧库切换与验证边界见[共享配置设计](tenant-shared-configuration.md)、[运行说明](tenant-platform-operations.md)和[专项进度](tenant-platform-progress.md)。二开不得访问 Config 内部表或把凭据写入共享参数。

各模块 `package-info.java` 必须声明名称和 `allowedDependencies`；公共命名接口如 `common :: security`、`common :: persistence` 需明确依赖。禁止循环依赖、跨模块 SQL、跨模块 join 和外键。

## 4. 数据与事务

- 每个模块只写自己的表，表名前缀与模块 ID 一致，如 `iam_user`。
- 业务实体继承 `TenantEntity`。租户、操作者由可信认证上下文填充，不能由外部请求任意指定。
- MyBatis 插件顺序：防全表更新 → 租户 → 数据范围 → 分页。数据范围收窄已有查询条件，超级管理员仍受租户隔离。
- 事务边界在 service；结构调整先获取结构锁，避免部门/角色并发移动形成环。
- 主键为不可解释的正Long，实体assign_id、现有IdWorker和`common :: persistence`公开`IdentifierAllocation.nextId()`统一使用共享MySQL号段。不要从ID推导时间或全局提交顺序，不自建/替换生成器，不退还回滚或进程退出的ID。JSON使用字符串，前端不能转为JavaScript Number；首次升级/回退及实现状态见[P08主键方案](proposals/P08-id-allocation.md)。
- 时间使用 `Instant`、数据库 `DATETIME(3)`，存 UTC；JDBC 会话强制 UTC。前端根据用户时区展示，范围筛选使用半开区间 `[from,to)`。
- 逻辑删除后保留历史；生成列唯一索引允许删除后重新使用业务编码。

### Flyway

公共迁移在 `backend/src/main/resources/db/migration/__root/`，模块迁移在对应模块目录。模块版本独立递增。已发布迁移不可编辑，应新增迁移修正。

上述默认启动迁移属于平台模块。当前`com.sma.business`业务Mapper使用租户存储组路由，需实现`BusinessSchemaCatalog`，把脚本放`db/business/<module>`；正式开通在业务物理库执行独立历史后才激活。不要把业务脚本留在平台默认迁移目录。业务写/失效/Outbox使用同一BusinessLedger会话，跨请求读缓存用BusinessCache；完整样例见[shop-storage](../examples/shop-storage/README.md)，交付状态见[单实例基线](secondary-development-baseline-progress.md)。

权限声明启动时补充缺失项。仅对旧版自动发现、仍以权限 key 命名且未自定义位置/名称的内置逻辑项，允许升级为显式声明；必须保留 ID、已有授权、翻译、排序和启停设置。不能用启动同步覆盖管理员配置。

## 5. REST、安全与事件

后台接口为 `/api/<module>/...`。每个请求处理方法显式声明以下策略之一：

- `@RequiresPermission("module:resource:action")`：后台人类身份与指定权限。
- `@PermitLoggedIn`：后台人类身份，不接受应用用户或机器。
- `@PermitApplicationUser("shop")`：精确应用 audience，仍须业务对象授权及已注册的应用安全链。
- `@PermitMachine`：机器身份及所属接口的能力/对象校验。
- `@PermitAnonymous`，同时需要在安全过滤链中放行路径。

Google、事务邮件、多存储与安全上传按 P09 分阶段实施，实际调用方式见[二开服务手册](integration-services-developer-manual.md)，已验收范围见[专项进度](integration-services-progress.md)。新增应用身份基础不代表 Google/应用登录端点已经交付。

响应为 `ApiResponse { code, error, message, data }`；成功 `code=0`。业务异常可能是 HTTP 200 加非零 code，认证失败、无权限、校验失败等还使用相应 HTTP 状态。前端 `api/http.ts` 统一处理，不得只判断 HTTP 200。

请求通过 Bearer 令牌认证。令牌原文不入数据库，数据库存 SHA-256 哈希；登录密码使用 BCrypt。P07 登录新密码为 8～12 位字母数字组合，写入前拒绝弱密码；旧密码验证保持兼容。安全操作密码为 6～8 位字母数字组合，采用独立 Argon2id＋pepper，两种密码不能相同。每次请求重算有效权限/数据范围，并检查账号凭据版本、安全版本及所需认证方式。绑定 TOTP 后第二步成功前没有正式 Token；锁屏只验证登录密码且不续期。公开接入和事务约定见[认证安全 API](auth-security-api.md)，实际交付状态见[实施进度](auth-security-progress.md)。

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

首版按单实例设计。P07 认证限流与挑战已使用数据库共享状态，但 WebSocket 票据/分发、SQL 统计等仍在内存中；集群化需要继续设计共享分发，不能仅复制进程。安全验证会使用独立提交的限流预算及最新已提交的会话状态；业务集成不要缓存全局“已验证”标记。

## 10. 文档维护

新增能力同步更新用户入口说明、开发约束和验收证据。问题记录至少写明页面、步骤、现象、根因、修复位置和验证方式。未经实际操作的场景标明未执行，不以构建成功替代浏览器检查。

### 前端未决操作与恢复入口

新版管理壳顶部“操作状态”汇总当前可信API、租户、入口与用户的未决记录。页面切换或刷新后，已发送的操作仍可能完成；正文、密码和所选File不会从记录恢复。面板当前覆盖普通OperationRegistry，不合并登录、MFA、改密和锁屏的特殊协议。当前本地验证范围见[前端进度](security-frontend-progress.md)，服务协议见[客户端契约](security-request-client-contract.md)。

二开页面按以下方式接入：

1. 普通写入使用`useSubmission`，提供稳定的`operation`、对象级`resource`与服务端实际支持的`LOCAL/NATIVE`协议；同一对象的编辑/删除/授权共用范围，批量目标在异步确认之前固定。输入快照仅留在内存，把回调收到的原`idempotencyKey`传给已有API客户端。不要自己写localStorage恢复条目。
2. `LOCAL`可由面板按原键核对权威回执。回执成功只证明原事务完成，业务仍须通过授权GET重读；不要把回执当业务DTO。NOT_FOUND/超时/畸形结果不允许清UNKNOWN或换键重发。
3. 常规文件上传使用`useFileUpload`/`UploadButton`并提供业务独有scope。面板使用原上传任务协议核对，ACCEPTED/RUNNING保持未决；刷新后没有原File，面板不提供续传、下载或自动绑定。文件当前读取权限仍由后端决定。P09后续云分片上传以[能力开发手册](integration-services-developer-manual.md)的交付状态为准。
4. NATIVE任务在业务页面保留原生适配器。需要“前往业务页面”时，在业务分支为`lib/operation-recovery.ts`登记明确的本地目的地，并确保实际路由的权限声明正确；禁止从恢复记录读取URL或把业务正文、令牌、原键拼到URL。无映射或无当前权限时不显示跳转按钮，原记录仍保留。
5. 页面自行负责草稿/未保存内容的离页守卫。管理壳的持续提示只告知离页和刷新后果，不替业务确认丢弃输入；成功核对后应重新读取业务数据。新增入口同步登记[写入口清单](security-frontend-write-inventory.md)，运行对应单元、真实浏览器夹具与`npm run verify`，真实后端验收另记。

旧版兼容边界：`frontend/`继续独立构建。其HTTP客户端只在缺少Idempotency-Key时生成UUID，已有显式键与原生operationId/version均保留；治理错误通过ApiError.key/details传递，不自动重试。需要显式请求键时使用该工程自己的`http`配置headers。旧版没有新版完整的操作状态面板和跨标签意图门禁，不能把随机请求键当作用户动作去重。验证入口为根`tools/dev/verify-frontend.ps1`与新版工程测试工具内的`playwright.security-legacy.config.ts`（实际启动旧工程5197）；S1c本地完成，真实后端S1d仍单独验收。

### 隔离真实请求治理验收

安全来源元数据可注入`common :: security`的`RequestSourceQuery`，在同步HTTP处理期间调用`current()`；非HTTP线程返回空。`RequestSource`只表示网络观测，不可用于构造`SmaPrincipal`/`AppUserPrincipal`、切换租户或授予访问。需要异步诊断时显式捕获不可变值并按隐私规则处理；请求结束不再从工作线程推断来源。受信代理和错误依据见[安全运行说明](security-defense-operations.md#请求来源观测s2a1)，事件落库与告警按后续S2/S3验收。

可信模块观察到确定性拒绝时可注入同一命名接口内的`SecuritySignalCapture`，调用`capture(SecuritySignal.Fact.AUTHORIZATION_REJECTED)`得到当时归属快照。它不会落库或发送告警，不能当作审计已持久化的回执；后台采集使用下方SecuritySignals契约。不要让HTTP调用者提供Fact/Attribution或自行绑定认证来上报事件。匿名Host解析不赋予事件TENANT归属，旧上下文退出后也不能靠提交旧tenantId重建身份。

需要交给有界后台采集时，注入`common :: security`的`SecuritySignals`，在确定事实、原认证上下文尚有效时调用`observe(SecuritySignal.Fact.AUTHORIZATION_REJECTED)`。仅使用代码确定的Fact，不从请求绑定枚举或直接提交捕获DTO；部署默认禁用。返回QUEUED_VOLATILE不代表持久化，REJECTED/DISABLED不改变原业务拒绝响应，也不得通过反复调用或新事件ID自动重试。该接口适用于尽力而为的检测信号，不能代替要求与业务原子提交的强制审计；后者需自己的审计事务契约。启用、节点密钥及监控指标见[运行说明](security-defense-operations.md#有界采集与节点故障缓冲s2c)，实际验证以[S2进度](security-event-progress.md)为准。

宿主统一错误响应已接入固定错误码的拒绝观察，覆盖范围见[运行说明](security-defense-operations.md#统一拒绝响应观测s2d1)。业务使用原`BizException`/权限拒绝时不必为同一错误再手工`observe`。统一响应映射只对自身路径做请求级去重，不会去重业务对公开`observe`的主动调用；二开新增确定事实须选择唯一观察点。自定义错误key不会自动推断分类，不能用伪造宿主key或传客户端Fact注册检测。普通404/幂等完成不采集；原HTTP200＋非零业务code仍保留，客户端继续按既有协议处理。

需要保留自己的业务错误码、同时说明确定拒绝事实时，使用`common :: security`的`ObservedRejection`：`throw new ObservedRejection(YourError.INVALID_INPUT, SecuritySignal.Fact.REQUEST_INPUT_REJECTED)`。`YourError`代表业务自己实现的ErrorCode；Fact必须由可信代码分支固定，不能从HTTP字段绑定。该类型是BizException子类，只有宿主MVC最终处理它时才尝试观察，原HTTP200、业务code、错误key和本地化参数保持；不要为同一异常另行observe。构造/抛出异常、事务回滚、传输worker执行本身都不会发送信号，也不保证持久化。

异步操作只有在原调用方收到这个确定异常并经MVC响应时，才使用当时仍有效的身份；超时或取消后晚到的结果不会借旧请求上下文补报。被业务吞掉、改包装或在后台任务中处理的异常不自动采集，不能据此替代任务终态事件或审计。部署需要包含此新增API的宿主版本；旧业务的BizException用法不变，新业务不能向旧宿主直接投放依赖该新类的制品。`FILE_INSPECTION_REJECTED`表示文件门禁拒绝、NO_ATTACK_INFERENCE，不证明文件有病毒或请求人是攻击者；接入与验收边界见[S2进度](security-event-progress.md)。

安全事件只读Java契约为`defense.SecurityEvents`，模块显式依赖`defense`后可注入：`tenant(20)`读取当前后台租户最近20条，`tenant(eventId)`按原UUID读取；不存在或跨租户返回空。调用者必须有`defense:event:view`。`platform(...)`还须部署运维身份和`defense:platform:view`，不能用tenant superAdmin代替。返回View保存当时fact/category/severity/outcome/assessment/ruleVersion和发生/接收/到期时间；不要用当前枚举规则重算并覆盖历史判断。不要调用defense内部Store/Mapper或向其传HTTP反序列化事件；有界采集及故障缓冲已按S2c验证，下述只读HTTP查询已按S2d2a本地验收；请求元数据已按S2d2b、自动清理已按S2d3a本地验收。最终HTTP采集持久闭环以[S2进度](security-event-progress.md)的d3b记录为准。

后台HTTP消费者使用`GET /api/defense/events?limit=20`和`GET /api/defense/events/{eventId}`；平台运维使用独立`/api/defense/platform-events`及UUID详情。返回`ApiResponse`，必须同时检查HTTP状态与业务code；跨租户/不存在详情沿用HTTP200＋非零code，不可据此重试任何写入。limit只能1～100，没有tenantId切换参数；平台权限不授予遍历租户数据。权限、投影、预算及缓存边界见[查询说明](security-defense-operations.md#安全事件查询-apis2d2a)，验收状态见[S2进度](security-event-progress.md)。同进程业务继续依赖公开`SecurityEvents`，不要直接依赖`internal.web`的HTTP投影类；本阶段不提供导出或修改证据API。

需要请求方法、声明路由模板、响应决定状态或观察节点时，为安全事件列表/详情附加`includeMetadata=true`。默认响应字段保持原样；旧记录或非HTTP观察可能缺少metadata或http，不要从当前页面URL补猜旧事件信息。`UNMAPPED_REQUEST_OBSERVED`仅为低级未匹配/缺失资源观察，普通业务404不会因为状态码自动产生该事实。Java消费者保留原SecuritySignal对象与eventId；六字段旧构造仍可用，但复制会丢新增metadata，等值/直接JSON消费者须按新字段适配。schema1/2升级与回退限制见[运行说明](security-defense-operations.md#请求元数据与缓冲升级s2d2b)，当前验收范围见[S2进度](security-event-progress.md)。

安全事件到期维护由宿主负责，业务继续使用公开observe/查询契约，不调用defense内部prune/hold或直接改容量表。maintenance默认开启，独立于默认关闭的collection；它包含停用租户且跳过held记录。关闭维护不改变已有expiresAt。配置、每节点预算、工作许可和关闭语义见[维护说明](security-defense-operations.md#保留期维护s2d3a)，交付状态以[S2进度](security-event-progress.md)为准。

采集和维护worker固定使用宿主类加载器，避免Boot打包后丢失宿主类/资源可见性；同时禁止继承调用者的线程局部变量或类加载器。这不传递租户或权限，业务仍通过原公开契约和可信身份范围调用。二开代码不能依赖在请求线程更换TCCL来影响后台执行，也不调用内部线程工厂；具体回归和打包探针边界见S2进度。

本地真实HTTP到持久化、故障补传及自动保留回收已按S2d3b验证；S2d4a另通过真实TLS multipart/App分片到采集器、加密缓冲和MySQL的文件组合验证。二开复跑方式与独占测试库/临时证书密钥清理要求见[闭环验证](security-defense-operations.md#http采集闭环验证s2d3b)。它不改变公开observe的尽力而为语义，也不代表持久扫描宿主、恶意软件检查、告警或外部Google/SMTP/云服务已经验收。

S1d专项复用`frontend-next/scripts/run-real-e2e.mjs`，使用`SMA_E2E_SUITE=security`。此模式要求明确提供已验证的宿主JAR和独立后端/前端端口；不得使用8080、5173或5174。脚本只使用现有本地MySQL/JDK，创建本轮专属`sma_security_e2e_<时间戳>`库和随机账号，保留普通runner的默认命名空间。真实浏览器不需要Google、SMTP或云存储测试账号。

在仓库根目录按根reactor构建、验证当前宿主后，从`frontend-next/`执行；以下JAR路径应替换为本轮已验证的实际绝对路径：

```powershell
$env:SMA_E2E_SUITE = 'security'
$env:SMA_E2E_BACKEND_JAR = 'E:/juiceProgram/spring Modulith/backend/target/security-s1d/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar'
$env:SMA_E2E_BACKEND_PORT = '18261'
$env:SMA_E2E_PEER_BACKEND_PORT = ''
$env:SMA_E2E_FRONTEND_PORT = '5206'
$env:SMA_E2E_OUTPUT_DIR = 'test-results-security-real'
node scripts/run-real-e2e.mjs request-governance.spec.ts
```

运行结果保存在`.e2e-runtime/security-<时间戳>/result.json`：`passed`表示浏览器用例通过，`cleanupComplete`表示自己的进程已确认退出、库/账号删除并查询确认不存在、生成钥文件已删除；两者必须都为true才算本轮成功。`artifactSha256`绑定实际复制并启动的JAR。清理超时不能当作进程停止，不能继续删除仍被进程使用的资源。启动观察超时但进程仍存活时继续查该进程和迁移日志，不重启用户服务。

当前四个单宿主场景验证真实业务提交、20次UI触发、已提交丢响应后的原键回执/显式重放拒绝、不同对象独立操作及15秒客户端超时后404仍保留未知。网络故障由浏览器传输层注入，业务响应与结果查询均来自真实Java/MySQL；延迟响应不等于服务端事务仍在执行。这些证据不替代双JVM、跨租户/权限完整矩阵或真实外部服务验收，阶段以[安全进度](security-defense-progress.md)为准。

双JVM验收在同样的security scope下增加独立端口，再运行专用用例：

```powershell
$env:SMA_E2E_PEER_BACKEND_PORT = '18262'
$env:SMA_E2E_OUTPUT_DIR = 'test-results-security-dual'
node scripts/run-real-e2e.mjs request-governance-cluster.spec.ts
```

runner先用一个临时实例完成迁移/初始化，然后让两个实例以`schema-mode=validate`运行；`before-restart`通过后才确认两者退出并重新启动，再执行`after-restart`。两实例使用同一复制JAR、部署钥及临时库，保持single部署模式；不是P08生产multi/LB验收。每轮独立保存JSON报告，最终`processes`记录端口/进程/退出，`phases`记录新鲜报告的通过/失败/跳过数；没有实际通过用例时不能成功。

测试worker每个节点正常登录一次，在内存中复用合法会话，不写storage-state/token文件；重启后的新worker重新登录。这样不会为了测试去清除或放宽既有登录预算。跨阶段仅保存原操作键、业务ID和回执ID。压力响应只记录节点、状态码、错误key、Retry-After及策略；精确的`503 common.request.unavailable`/`Retry-After: 1`仍需原键权威核对，不能当失败后重发，也不能据此声称所有并发请求都已受理。完整验收状态仍以安全进度为准。

同租户操作者与上传原生协议使用`request-governance-scope.spec.ts`，同上指定security制品/独立端口，并设置`SMA_E2E_PEER_BACKEND_PORT=''`后执行`node scripts/run-real-e2e.mjs request-governance-scope.spec.ts`。夹具通过正常IAM接口创建SELF范围角色/成员，正常登录后持有内存会话；撤权后重新登录，避免将旧会话失效误当作当前权限检查。测试不改生产预算，顺序写入间隔遵守默认500ms约束。验收结果以专项进度为准。

二开处理回执时遵守以下边界：

- `POST /api/common/requests/lookup {key}`或`GET /api/common/requests/{operationId}`仅允许原操作者并重查保存的动作权限；超管对业务对象的管理权限不等于读取他人回执的权限。换账号后不能接管旧未决操作。
- 常规上传的响应为`X-Request-Policy: NATIVE`和`X-Upload-Id`。通过`POST /api/file/uploads/lookup {key}`或`GET /api/file/uploads/{uploadId}`核对原生任务；不能用common内部受理成功代替文件上传完成。上传回执只含任务/文件ID、状态、可续传标记与时间。
- 原生`SUCCEEDED`保留历史完成证据，文件详情和下载仍走当前授权接口。文件逻辑删除后，即使旧任务仍成功，同键原文件重放也不能恢复文件；不能把历史回执当作永久下载凭证。同键更换文件名、类型、大小或内容也不是合法重试。

跨租户配置专项沿用上述security制品和端口，加上`$env:SMA_E2E_SECURITY_TENANTS='1'`，运行`node scripts/run-real-e2e.mjs request-governance-tenant.spec.ts`。runner只在本轮生成库建立`tenant-security.test`的TEST目录、不同ID的第二租户管理员、权限目录和验证码测试覆盖；通过数据库内复制本轮密码hash准备账号，随后仍走真实Host和正常登录。TEST目录不是实际DNS/TLS验证或生产开通方式，不能复制到业务环境。此开关拒绝普通next或任意已有数据库。

测试生成独立租户配置加密环，随机虚拟Google/SMTP/云凭据只在内存；Google客户端、邮箱和存储读写均停用，不做外部登录、发信或对象传输。双租户具备相同管理权限后再检查同编码独立配置、跨租户读写及关联拒绝、列表隔离和单租户编辑不影响另一租户。清理额外包含`tenant-credentials.keys`，仍须检查`passed`与`cleanupComplete`。结果与未验证边界见[安全进度](security-defense-progress.md)。
