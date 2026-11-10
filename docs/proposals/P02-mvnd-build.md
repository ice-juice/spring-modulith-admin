# P02 引入 mvnd 加速构建



## 8. 决策记录（2026-11-10）

- 同意“mvnd 本地默认、mvnw 标准构建”。
- 实施中发现：Wrapper 原为 Maven 3.10.0，与 mvnd 内置的 3.9.16 共用本地仓库时会反复向远程核对依赖来源（在线 `compile` 需 24 秒，mvnd 离线模式拒绝使用缓存）。已把 Wrapper 统一为 Maven 3.9.16，问题消失。
- 实测（中位数，3 次）：`compile` mvnw 1.7s / mvnd 0.3s；`verify` mvnw 4.3s / mvnd 2.5s。超过 20% 的门槛，mvnd 作为本地默认命令。
## 1. 结论

可以引入，建议 **用户级安装 mvnd 1.0.6 作为日常本地构建命令，Maven Wrapper（`mvnw`）保留为标准构建入口**（CI、首次构建、排查问题）。两者使用同一个本地仓库和同一份 `pom.xml`，结果一致。

收益要实事求是：mvnd 把 Maven 放在常驻的守护进程里，省掉每次构建的 JVM 启动、插件类加载和 JIT 预热，并默认并行构建多模块工程。本项目后端是单模块工程，测试由 Surefire 另起 JVM 执行，所以“质的飞跃”主要体现在反复执行 `compile` / `test` 的开发循环上，首次构建没有收益。上线前用第 5 节的方法实测，用数据决定是否作为默认命令。

## 2. 版本与约束（2026-11-10 核实）

| 项 | 结论 |
|---|---|
| 稳定版本 | mvnd **1.0.6**，内置 Maven 3.9.x；Windows x86_64 提供原生客户端，下载包附 SHA512 与 GPG 签名 |
| 预览版本 | mvnd 2.0.0-rc-3，内置 Maven 4.0，仍是 RC，不采用 |
| 守护进程 JDK | **必须 Java 17+**。用户级 `JAVA_HOME` 当前是 JDK 8（运行 JBolt 用），因此在 `~/.m2/mvnd.properties` 中把 `java.home` 固定为 JDK 21 |
| 与 Wrapper 的关系 | 项目 Wrapper 当前下载 Maven 3.10.0；mvnd 内置 3.9.x。两者都满足 Enforcer 的 `[3.9,)` 要求 |
| Wrapper 直接驱动 mvnd | 技术上可行（`mvnw.cmd` 识别 `maven-mvnd-*` 分发包），但 Wrapper 对 mvnd **不支持校验和**，且分发包与平台绑定（Windows 包无法用于 WSL/Linux/CI），因此不采用 |
| JBolt 参考工程 | 可以用 mvnd 编译（JDK 21 的 javac 能以 1.8 为目标编译），运行仍使用 JDK 8 |

## 3. 方案

### 3.1 安装

- 下载 `maven-mvnd-1.0.6-windows-amd64.zip`，用官方 SHA512 校验，并用 GPG 签名核对发布者，解压到 `%LOCALAPPDATA%\Programs\maven-mvnd-1.0.6-windows-amd64`。
- 用户环境变量：`MVND_HOME` 指向安装目录，`PATH` 追加 `%MVND_HOME%\bin`（沿用现有的 `set-user-dev-env.ps1`，修改前自动备份）。

### 3.2 配置

`~/.m2/mvnd.properties`（个人机器相关）：

```properties
# 守护进程使用 JDK 21，与 JAVA_HOME（JDK 8）解耦
java.home=C:/Users/<用户名>/AppData/Local/Programs/Eclipse Adoptium/jdk-21.0.13+9
mvnd.jvmArgs=-Xmx2g -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8
```

`backend/.mvn/mvnd.properties`（项目共享，只放与机器无关的设置）：

```properties
# 单模块工程，限制并行线程，避免占满 CPU
mvnd.threads=2
# 空闲 3 小时后自动退出守护进程
mvnd.idleTimeout=3h
```

### 3.3 终端 UTF-8 与 JAVA_TOOL_OPTIONS

PowerShell 7 的用户配置文件（`$PROFILE.CurrentUserAllHosts`）加入：

```powershell
[Console]::InputEncoding  = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding           = [System.Text.UTF8Encoding]::new($false)
$PSDefaultParameterValues['*:Encoding'] = 'utf8'
```

用户环境变量：

```text
JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8
```

说明：

- JDK 21 的 `file.encoding` 默认已是 UTF-8（JEP 400），但控制台输出跟随系统代码页（中文 Windows 为 GBK），需要 `stdout.encoding` / `stderr.encoding` 才能输出 UTF-8；JDK 8 只识别 `file.encoding`，另外两个属性会被忽略，不会报错。设置后 JBolt 在 JDK 8 下的控制台日志也不再乱码。
- `JAVA_TOOL_OPTIONS` 对本机所有 Java 程序生效，每个 JVM 启动时会向标准错误输出一行 `Picked up JAVA_TOOL_OPTIONS: ...`。这行提示无害，但个别解析 `java -version` 输出的工具可能受影响，出现问题时可以改为只在 `mvnd.jvmArgs`、`MAVEN_OPTS` 中设置。
- Windows“使用 Unicode UTF-8 提供全球语言支持”系统选项需要管理员权限和重启，并可能影响旧程序，不建议开启。

### 3.4 使用方式

| 场景 | 命令 |
|---|---|
| 日常开发（本地） | `mvnd verify`、`mvnd test`、`mvnd spring-boot:run` |
| 标准构建（CI、首次构建、排查） | `.\mvnw.cmd verify` |
| 停止守护进程 | `mvnd --stop` |
| 查看守护进程 | `mvnd --status` |

修改 `pom.xml` 依赖或插件后，守护进程会自动感知；遇到异常行为先 `mvnd --stop` 再重试。

## 4. 实施步骤（确认后执行）

1. 下载、校验、解压 mvnd 1.0.6；配置 `MVND_HOME`、`PATH`。
2. 写入 `~/.m2/mvnd.properties` 与 `backend/.mvn/mvnd.properties`。
3. 写入 PowerShell 配置文件的 UTF-8 设置；设置用户环境变量 `JAVA_TOOL_OPTIONS`。
4. 新开终端验证：`mvnd --version` 显示守护进程 JDK 21；`java -version` 出现 `Picked up JAVA_TOOL_OPTIONS`；中文日志与测试输出不乱码。
5. 执行第 5 节性能对比，结果写入 `docs/dev-environment.md`。
6. 更新 README 第 13 节的常用命令。

## 5. 性能验证方法

在后端目录、离线模式（`-o`）下，各执行 1 次预热后连续 3 次，记录耗时：

| 命令 | 说明 |
|---|---|
| `.\mvnw.cmd -o -q verify` ×3 | 基线：每次新 JVM |
| `mvnd -o -q verify` ×3 | 守护进程已预热 |
| `.\mvnw.cmd -o -q compile` ×3 与 `mvnd -o -q compile` ×3 | 开发循环中最常见的增量编译 |

取中位数比较。如果 `verify` 的中位数改善不足 20%，仍保留 mvnd 作为可选命令，不在文档中作为首选。

## 6. 回滚

删除 `MVND_HOME` 与 `PATH` 中的 mvnd 条目、`JAVA_TOOL_OPTIONS` 环境变量、PowerShell 配置文件中的 4 行设置，以及两个 `mvnd.properties`；`mvnw` 构建不受影响。

## 7. 待确认

1. 是否同意“mvnd 为本地默认、mvnw 为标准构建”的定位？
2. `JAVA_TOOL_OPTIONS` 是否接受“所有 Java 程序都会打印 Picked up 提示”的副作用？

