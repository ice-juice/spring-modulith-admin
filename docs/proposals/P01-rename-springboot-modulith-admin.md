# P01 项目改名为 Springboot Modulith Admin



## 7. 决策记录（2026-11-10）

- 根包 `com.sma.admin`；数据库账号与库同名 `springboot_modulith_admin`。
- 工作区上级目录不迁出；项目内文件不再引用任何绝对路径。
- 已实施：包目录迁移、坐标与类名、前端包名、文档与脚本、数据库重建；验收见 [开发进度](../development-progress.md) P0-1。
## 1. 目标

产品名称改为 **Springboot Modulith Admin**。项目内的代码、目录、构建坐标、前端包名、配置、脚本和文档都不再出现旧名关键词，数据库按新名称重新创建。

## 2. 命名对照

| 项 | 当前 | 改后 |
|---|---|---|
| 产品显示名 | 旧名 Admin | Springboot Modulith Admin |
| 简称（用于短标识） | — | SMA |
| Maven groupId | `com.<旧名>` | `com.sma` |
| Maven artifactId / 制品 | `<旧名>-admin` | `springboot-modulith-admin`（jar：`springboot-modulith-admin-0.0.1-SNAPSHOT.jar`） |
| Java 根包（Modulith 模块根） | `com.<旧名>.admin` | `com.sma.admin`，模块为 `com.sma.admin.<模块id>` |
| 启动类 | `<旧名>AdminApplication` | `SpringbootModulithAdminApplication` |
| `@Modulithic(systemName)` | 旧名 Admin | `Springboot Modulith Admin` |
| `spring.application.name` | `<旧名>-admin` | `springboot-modulith-admin` |
| Jackson 模块名 | `<旧名>-long-as-string` | `sma-long-as-string` |
| 前端 `package.json` name | `<旧名>-admin-web` | `springboot-modulith-admin-web` |
| 数据库名 | `<旧名>_admin` | `springboot_modulith_admin` |
| 数据库账号 | `<旧名>_admin` | `springboot_modulith_admin`（25 字符，满足 MySQL 32 字符上限；口令仅用于本机开发，写在 dev-environment 文档） |
| 测试中的根包常量 | `com.<旧名>.admin` | `com.sma.admin` |

根包选择说明：Spring Modulith 以启动类所在包为根识别模块，根包会出现在每个类的包名里，越短越好读。`com.sma.admin` 取 Springboot Modulith Admin 的缩写，层级与现在一致，迁移成本最低。备选是 `org.springbootmodulith.admin`，语义更完整但更长。表前缀、URL 前缀、权限 key 前缀都只用模块 id，不受改名影响。

## 3. 影响范围（2026-11-10 扫描）

旧名出现在 39 个文件、2 个目录中：

| 类别 | 内容 | 处理 |
|---|---|---|
| 后端代码 | `src/main|test/java/com/<旧名>/admin/**` 共 17 个 Java 文件、`pom.xml`、`application.yml` | 目录整体迁移到 `com/sma/admin`，修改包声明、import、类名、坐标、应用名、数据库连接 |
| 前端 | `package.json`、`package-lock.json`、`src/api/http.ts`、`src/views/HomeView.vue` | 改包名与界面文案，重新生成 lock 文件 |
| 文档 | README、`docs/dev-environment.md`、迁移方案、研究资料 | 改名称；去掉含旧名的绝对路径 |
| 工具脚本 | `tools/setup/*`、`tools/jbolt-research/*` | 绝对路径改为基于脚本位置的相对路径，或读取环境变量 |
| 数据库 | 临时 MySQL 与 WSL 安装脚本中的库名、账号 | 新建库与账号，删除旧库与旧账号 |

**工作区上级目录**：项目当前位于 `E:\<旧名>Program\spring Modulith`，上级目录名包含旧名，它属于你的个人目录结构，不在项目内。改名后项目文件不再引用任何绝对路径，因此不受影响；是否移动工作区由你决定（移动后需同步更新 JBolt 参考环境的位置，见第 6 节）。

## 4. 实施步骤（确认后执行）

1. 停止正在运行的后端、前端开发服务。
2. 建议先 `git init` 并提交一次基线，让改名形成可审阅的 diff（需要先配置 git 的 user.name / user.email）。
3. 后端：迁移包目录 → 批量替换包名与 import → 重命名启动类 → 修改 `pom.xml` 坐标、`application.yml` 应用名与数据源 → 修改 Jackson 模块名与测试中的根包常量。
4. 前端：修改 `package.json` 名称，`npm install` 重新生成 lock，修改界面文案。
5. 脚本：`tools/setup` 与 `tools/jbolt-research` 中的路径改为 `$PSScriptRoot` / `Path(__file__)` 相对路径；外部目录（JBolt 原工程、JBolt 运行副本）改为环境变量 `SMA_JBOLT_HOME`、`SMA_JBOLT_RUN_HOME`，未设置时给出明确提示。
6. 数据库：在当前 MySQL（临时实例，WSL 就绪后为 WSL 实例）中创建 `springboot_modulith_admin` 库与同名账号，删除旧库与旧账号；更新 `wsl-mysql-setup.sh`。旧库只有 Flyway 历史表和事件发布表，没有业务数据，无需迁移，由 Flyway 在新库重建。
7. 文档：README、开发环境、迁移方案、研究资料统一改名。

## 5. 验收标准

- `rg -i "<旧名>"` 在项目内（排除 `node_modules`、`target`、`dist`、下载缓存）结果为 0，且没有含旧名的目录。
- `mvnw verify` 通过：Modulith 结构校验、13 条架构规则、单元测试全部绿色，模块文档生成在新根包下。
- 后端连接 `springboot_modulith_admin` 启动成功，Flyway 在新库执行 `V1__event_publication`，`/actuator/health` 为 UP，受保护接口返回 401。
- 前端 `npm run build` 通过，开发服务器经代理读到后端健康状态。

## 6. 待确认

1. 根包用 `com.sma.admin`（推荐）还是 `org.springbootmodulith.admin`？
2. 数据库账号是否与库同名（推荐），还是使用更短的 `sma_app`？
3. 是否需要把工作区从当前上级目录迁出？

