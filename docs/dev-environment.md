# 开发环境

更新日期：2026-11-10。所有工具均为用户级安装，不需要管理员权限。下文路径中的 `..` 指仓库的上一级目录（工作区目录）。

## 1. 工具

| 工具 | 版本 | 位置 | 环境变量 |
|---|---|---|---|
| JDK 8（JBolt 参考环境） | Temurin 8u512-b09 | `%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk8u512-b09` | `JAVA_HOME`（默认）、`JAVA8_HOME` |
| JDK 21（本项目） | Temurin 21.0.13+9 LTS | `%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-21.0.13+9` | `JAVA21_HOME` |
| Maven | 3.9.16 | `%LOCALAPPDATA%\Programs\apache-maven-3.9.16` | `MAVEN_HOME` |
| Maven Wrapper | 下载 Maven 3.9.16 | `backend/mvnw.cmd` | — |
| mvnd | 1.0.6（内置 Maven 3.9.16） | `%LOCALAPPDATA%\Programs\maven-mvnd-1.0.6-windows-amd64` | `MVND_HOME` |
| Node.js | 26.8.1（nvm-desktop） | `.nvmdrc` 锁定 | — |
| MySQL（临时） | 8.4.10 | `%LOCALAPPDATA%\Programs\mysql-8.4.10-winx64` | — |

用户 `PATH` 前部依次为 `%MVND_HOME%\bin`、`%JAVA_HOME%\bin`、`%MAVEN_HOME%\bin`。每次修改用户环境变量前，`tools/setup/set-user-dev-env.ps1` 会把原值备份到 `tools/setup/logs/`。所有安装包均校验了官方 SHA256/SHA512，mvnd 与 MySQL 另外核对了 GPG 签名。

## 2. 编码（UTF-8）

- PowerShell 7 用户配置文件 `Documents\PowerShell\profile.ps1`：控制台输入输出、`$OutputEncoding`、cmdlet 默认编码均为 UTF-8。
- 用户环境变量 `JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`：JDK 8 与 JDK 21 的文件与控制台输出均为 UTF-8。每个 JVM 启动时会打印一行 `Picked up JAVA_TOOL_OPTIONS`，属正常现象。

## 3. 构建

| 用途 | 命令 | 说明 |
|---|---|---|
| 本地默认 | `mvnd verify` / `mvnd test` / `mvnd spring-boot:run` | 守护进程使用 `~/.m2/mvnd.properties` 中的 JDK 21，与 `JAVA_HOME` 无关 |
| 标准构建 | `$env:JAVA_HOME = $env:JAVA21_HOME; .\mvnw.cmd verify` | CI、首次构建、排查问题 |
| 停止守护进程 | `mvnd --stop` | 修改构建配置后行为异常时使用 |

项目共享的 mvnd 设置在 `backend/.mvn/mvnd.properties`（线程数 2、空闲 3 小时退出）。实测数据见 [P02 决策记录](proposals/P02-mvnd-build.md)。

从项目根目录可运行 `pwsh -NoProfile -File tools/dev/verify-backend.ps1`；加 `-Standard` 使用 Wrapper。前端统一验证入口为 `tools/dev/verify-frontend.ps1`。后端打包后用 `tools/dev/run-backend.ps1` 启动独立 JAR 副本，避免 Windows 锁定构建产物。

初始化管理员：首次启动前在当前终端设置 `$env:SMA_ADMIN_PASSWORD='自行设置至少12字符的密码'`。只在默认租户没有超级管理员时创建 `admin`；未设置时不会创建。此值不写入仓库、不打印到日志；数据库只存 BCrypt。已有管理员不会被该环境变量重置。

本地可运行 `pwsh -NoProfile -File tools/dev/start-with-admin.ps1`，在当前 PowerShell 隐藏输入密码。脚本只重启核实属于本项目的 8080 开发进程，并通过子进程环境传入密码。2026-11-10 最后只读检查：开发库默认租户尚无管理员，因此人工浏览器验收前需执行该初始化步骤。脚本只做了语法检查，实际初始化等待用户本人设置密码。

## 4. 数据库

WSL 尚未启用（需要管理员授权并重启），当前使用 Windows 上的免安装 MySQL 8.4.10：

- 数据目录：`../jbolt_reference_run/temp-mysql`（可用环境变量 `SMA_JBOLT_RUN_HOME` 改位置），只监听 `127.0.0.1:3306`。
- 管理：`tools/setup/temp-windows-mysql.ps1 -Action start|stop|status`。
- 库与账号（仅本机开发）：

| 库 | 账号 / 口令 | 用途 |
|---|---|---|
| `springboot_modulith_admin` | `springboot_modulith_admin` / `sma_dev` | 本项目开发库 |
| `springboot_modulith_admin_test` | 同上 | 本项目集成测试库（测试启动时由 Flyway 清空重建） |
| `jboltai` | `root` / `root` | JBolt 参考环境 |

WSL 就绪后的步骤：以管理员身份运行 `tools/setup/elevated-enable-wsl.ps1` 并重启 → 安装 Ubuntu 24.04 → 在 Ubuntu 中以 root 执行 `tools/setup/wsl-mysql-setup.sh` → 停止临时实例。

## 5. JBolt 参考环境

- 源码：`../jbolt_ai_platform_jfinal_customer`（可用 `SMA_JBOLT_HOME` 覆盖），保持不变。
- 运行副本：`../jbolt_reference_run`，去掉了依赖闭源 `jboltai_sdk` 的 AI 模块，JDK 8 运行，监听 `127.0.0.1:8090`，默认账号 admin / 123。改动脚本 `tools/jbolt-research/prepare_reference_run.py`。
- 研究脚本统一通过 `tools/jbolt-research/paths.py` 定位上述目录。

## 6. 前端

```powershell
cd frontend
npm install
npm run dev      # http://127.0.0.1:5173，/api 与 /actuator 代理到 8080
npm run build    # 类型检查 + 生产构建
```

