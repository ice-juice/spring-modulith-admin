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

