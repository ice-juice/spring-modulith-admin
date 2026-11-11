# 业务模块热更新后端接入手册

本手册描述当前代码，最新实施与验证状态见 [全栈实施记录](fullstack-progress.md)。面向内部可信开发人员；没有双人审批。新版管理前端现已进入实施范围，入口 `/extension/manage`。源码/JAR 不能用于替换核心认证、发布服务或宿主稳定模块。

## 1. 部署单元和信任边界

| 单元 | 程序 | 持有内容 |
|---|---|---|
| 业务宿主 | `backend` | 公钥、NODE 凭据、已签名制品缓存、业务工作账本 |
| 发布安全服务 | `extension-security` Boot JAR | 独立身份/TOTP、授权、制品目录、发布状态、私钥、独立数据库、审计 |
| 构建/校验工作节点 | `extension-worker` shaded JAR | WORKER 凭据、固定摘要镜像、只读 SDK/探测器、扫描库 |
| 插件 | 普通 JAR + `META-INF/sma-plugin.json` | 自有类、声明依赖、最小 SDK 接口 |

生产中安全服务和 worker 使用不同系统身份、独立网络访问策略。业务宿主不可访问安全数据库、KEK、私钥、Docker socket 或 worker 管理权限。仅“不同 Java 包”或同机同权限运行不构成隔离。

扩展默认关闭。没有目录扫描、自动捡拾 JAR、HTTP 任意路径加载、上传后立即执行或本地编译降级接口。业务宿主仅处理经独立签名、PREPARE 和节点运行租约授权的精确字节；RELEASE 才能切换路由。

同 JVM 的 ClassLoader 和字节码检查不是恶意代码沙箱。如果攻击者已经控制 JVM 或机器管理员权限，可以绕过本机检查；需要外部撤销、隔离节点和从可信镜像重建。不得以文件 SHA-256 或禁止 Attach 宣称可以防住 root/SYSTEM。

## 2. 构建和默认资源策略

Java 21；根目录运行 `mvn verify`，Windows 项目环境可用 `pwsh -NoProfile -File tools/dev/verify-backend.ps1`。根 reactor 联合构建 SDK、协议、运行时、安全服务、worker 和业务后端。MySQL 集成测试使用专用 `springboot_modulith_admin_test`，会清理该测试库；不要把测试连接配置成业务库。

| 限制 | 初值 |
|---|---|
| 单 JAR / 单批 / 文件数 | 100 MiB / 300 MiB / 20 |
| 单源码 / 批量源码 | 1 MiB / 10 MiB |
| 解包 / 条目 / 嵌套 / 压缩比 | 500 MiB / 20,000 / 2 层 / 200 |
| 单插件 / 分组 / 仓库 | 2 GiB / 5 GiB / 20 GiB |
| 历史 / 同时驻留版本 | 已发布历史至少 20 版和 30 天保护；最多 3 个驻留版本 |
| 最低剩余磁盘 | 5 GiB 与磁盘容量 10% 取较大值 |
| worker 并发 / 排队 / 超时 | 2 / 20 / 600 秒 |

配额在上传预占、流式读取、解包及最终产物处核验；有在途调用、业务引用或有效租约的版本不能因为达到数量限制而强制卸载/删除。首次试用应预留足够堆和 Metaspace：宿主准入按最坏解包预算预留内存，可能拒绝小堆配置的新版本。

依赖默认不放行。管理员用 `DEPENDENCY_REGISTER` 登记固定 Maven 坐标与 SHA-256，插件清单逐个列出 `lib/*.jar`；不允许 SNAPSHOT、浮动版本、未声明嵌套库、伪装宿主框架包、native/agent/外部 Class-Path。不要把 Spring Boot fat JAR 当作插件上传。

完整扫描、最新依赖清单与 24 小时内扫描库是发布条件。高危/严重/已知利用漏洞按 `VulnerabilityGate` 拒绝；临时例外绑定具体插件、环境、漏洞、依赖 hash 和策略修订，仍需单人强化授权；不能豁免未知扫描结果、恶意代码或扫描器不可用。细节见 P05 第 19 节及协议实现。

## 3. 安全服务配置与首次引导

安全服务不与业务后端共用数据库用户。创建独立空 MySQL 库（例如 `sma_release_security`），使用该库限定权限的账号。Flyway 自动迁移。

必填环境变量：

| 变量 | 用途 |
|---|---|
| `SMA_RELEASE_DB_URL` | 独立 MySQL JDBC URL |
| `SMA_RELEASE_DB_USERNAME` / `SMA_RELEASE_DB_PASSWORD` | 独立库凭据 |
| `SMA_RELEASE_DIRECTORY` | 受保护制品仓库目录 |
| `SMA_RELEASE_ENCRYPTION_KEY` | 随机 32 字节的标准 Base64 KEK；不能使用示例固定值 |
| `SMA_RELEASE_ORIGIN` | 未来管理界面的唯一 HTTPS Origin |
| `SMA_RELEASE_BOOTSTRAP_FILE` | 仅首次引导/恢复时配置的绝对路径 |
| `SMA_RELEASE_BIND` / `SMA_RELEASE_PORT` | 默认 `127.0.0.1:8092` |

服务默认为回环 HTTP 监听，需在同一受保护机器上配置 HTTPS 反向代理，或配置 Boot 原生 TLS。宿主和 worker 强制使用 HTTPS，并验证系统信任的证书，不允许关闭证书验证。反向代理禁止公网直达内部端口，不将任意客户端转发头视作可信来源。不要在日志或仓库中保存上述秘密。

引导输入 JSON 的结构：

```json
{
  "username": "release-admin",
  "loginPassword": "<随机独立登录密码，至少12字符>",
  "operationPassword": "<另一个随机操作密码，至少12字符>",
  "machines": [
    {"id":"host-01","kind":"NODE","environments":["production"],"token":"<随机32字节URL-safe Base64>"},
    {"id":"worker-01","kind":"WORKER","environments":["production"],"token":"<另一随机32字节URL-safe Base64>"}
  ],
  "resultFile": "<受保护目录中的绝对结果文件路径>"
}
```

Unix 输入文件权限 0600、父目录 0700；Windows 使用仅服务账号和 SYSTEM/Administrators 可读的 ACL。结果目录也必须满足要求。结果文件包括 `account`（15 分钟注册票据）和 `keys`（公钥列表），不会打印到控制台。成功导出后输入文件被删除，随即清除服务配置中的引导路径，避免下次启动寻找已删除文件。

旧独立API注册流程（新版页面使用 console/enrollment，无需登录密码）：`auth/enrollment/begin` 使用登录密码和注册票据，仅显示一次 TOTP 种子；`auth/enrollment/complete` 使用新的验证码完成绑定，立即离线保存一次性恢复码。相同 TOTP 时间步不能用于多个动作，包括注册后立刻登录；等待下一时间步。

若数据库提交成功但结果文件导出失败，使用**完全相同**的原始输入重试，服务会解密已有回执，不会重复建管理员。票据过期且账号尚未完成注册时，先由运维移走/删除已过期的受保护结果文件，再使用相同原始输入恢复；系统重签注册票据和 TOTP 种子并撤销旧票据。已完成注册、被禁用、凭据已修改的账号不能通过这条路径重置 MFA。正常账号恢复使用一次性恢复码或已授权安全管理员操作。

## 4. 宿主配置和运行租约

在业务后端配置受保护的 `sma.extension`：

```yaml
sma:
  extension:
    enabled: true
    security-url: https://release.internal.example
    node-id: host-01
    node-token-file: /etc/sma/extension/node.token
    environment: production
    issuer: release-security
    audience: sma-admin
    trust-file: /etc/sma/extension/trust.json
    cache-directory: /var/lib/sma/extension-cache
    deployments: [business-extensions]
```

`trust.json` 是引导结果中的 **keys 数组本身**，不是整个结果对象。字段包括 `id, environment, algorithm, publicKey, notBefore, notAfter, purposes, revoked`。只安装需要的生产公钥；文件和父目录由部署身份维护，业务进程只读，缓存和公钥目录不能允许普通用户写入。不要让上传接口修改该文件。

宿主同步公钥变动、撤销、策略、待发布版本和历史工作引用。运行租约初值 15 分钟，实际有效期也受扫描时效约束。控制面故障时保持尚有效的旧版本资格；资格过期后拒绝新插件调用，保护在途调用自然结束。冷启动需要重新取得可信资格，不能只从业务数据库恢复执行。节点 token 失效后无法续租，但已经取得的离线租约不可能隔空立即失效，风险窗口为剩余有效期。

每轮同步先通过 `activeOnly=true` 恢复/续租当前服务版本，再处理待发布版本；待发布不会阻断旧版续租。每个修订的租约保留记录独立到期，避免新准备记录覆盖旧租约的制品保留保护。新节点或离线节点可用完整可信签名向量直接前进到更新修订，仍拒绝倒退，无需逐版执行中间发布。

建议部署 JVM 固定 classpath、受保护启动脚本，禁用 Attach；限制动态 agent、调试端口、远程 JMX、Java 注入环境变量和普通账号改写安装目录。上述系统控制必须在真实部署中验收，当前代码测试不代替主机加固。

## 5. worker 配置与隔离构建

生产 worker 需要已安装的容器引擎，以及预先拉取并审核的 Java 21 JDK 和 Grype 镜像。镜像必须以 `repository@sha256:...` 配置，不允许浮动 tag，不在执行任务时自动拉取镜像。本机没有检测到 Docker，当前未验证真实容器/Grype 运行。

| 环境变量 | 值 |
|---|---|
| `SMA_WORKER_SECURITY_URL` | 安全服务 HTTPS URL |
| `SMA_WORKER_TOKEN` / `SMA_WORKER_ENVIRONMENT` | 专属 WORKER token / 环境 |
| `SMA_WORKER_DIRECTORY` | 专属工作目录，限定磁盘预算 |
| `SMA_WORKER_CONTAINER_ENGINE` | 受保护引擎可执行文件绝对路径 |
| `SMA_WORKER_SCANNER_IMAGE` / `SMA_WORKER_JAVA_IMAGE` | 固定摘要镜像 |
| `SMA_WORKER_SCANNER_DB` | 专属离线扫描库目录 |
| `SMA_WORKER_CONTAINER_USER` | 非 root UID:GID，例如 `65532:65532`；目录权限需匹配 |
| `SMA_WORKER_SDK_JAR` / `SMA_WORKER_SDK_SHA256` | 本次构建的 SDK JAR 及实测 SHA-256 |
| `SMA_WORKER_PROBE_JAR` / `SMA_WORKER_PROBE_SHA256` | 本次 shaded worker JAR 及实测 SHA-256，包含 ContractProbe |

运行 `java -jar extension-worker-0.0.1-SNAPSHOT.jar refresh-database` 更新漏洞库；运行同一 JAR 的 `once` 模式领取并处理一个任务。通过受控服务管理器定期调用，避免同一 token 并发抢任务；扫描库更新与扫描使用互斥调度，防止更新中读取半成品。受控 systemd 一次任务和 timer 模板见 deploy/，需按目标主机安装并实测。

源码构建使用固定 Java 21 javac、`-proc:none`、SDK 和允许的依赖，不执行 Maven/Gradle、上传脚本或用户编译参数。可信 CompilerDriver 在容器 `/build` 2 GiB tmpfs 编译；宿主输入和 SDK 均只读挂载，产物通过有界 stdout ZIP 回传，宿主不向容器提供可写输出目录。产物在宿主重新检查条目数、总展开字节、类格式和 SHA-256，然后生成独立 JAR 制品及来源证明。编译器日志和扫描报告有独立大小上限。

容器禁网（扫描库更新任务除外）、只读根、非 root、丢弃 capabilities、no-new-privileges、2 GiB 内存、2 CPU、64 PID、固定超时；超时清理命名容器。只允许扫描库更新任务写 `/db`。宿主必须保证工作区、容器引擎和镜像配置不受上传者控制。

扫描失败、扫描库过期、镜像缺失或容器引擎不可用都会拒绝发布；没有本地任意代码执行或空扫描通过的后备路径。测试中使用 fixture 扫描结果、固定可信源码，不代表生产扫描通过。

## 6. 管理 API 与单人强化授权

安全服务路由前缀 `/release/v1`。正常后台通过专用宿主代理和CONSOLE机器交换映射身份，安全服务session仅留在服务器；敏感动作仍独立验证操作密码+TOTP。旧独立登录API继续支持迁移和运维，不用于新版页面。业务系统token不能作为发布session，CONSOLE机器token也不能直接调用人类命令接口。接线、首次绑定、撤销和兼容见 [统一入口部署](unified-console-deployment.md)。浏览器直连旧API时Origin仍必须匹配配置。

敏感动作统一使用以下顺序：

1. 固定参数 JSON 的原始 UTF-8 字节，计算 `parametersSha256`；上传时还固定按文件名排序的 `files: [{name,sha256,bytes}]`。
2. `POST /challenges` 提交 `ActionIntent`：`action,environment,groupId,groupRevision,pluginId,expectedRevision,policyRevision,files,parametersSha256`。
3. `POST /authorizations` 提交 `challengeId,operationPassword,otp`，获得有效期 60 秒的单次 grant。挑战有效期 120 秒。
4. `POST /commands` 提交 `idempotencyKey,grant,intent,parameters`。`parameters` 是原始 JSON 字节的 Base64；不是再序列化的 JSON 对象。密码、grant、参数正文不要写到请求日志。
5. 保存 action receipt，以 `GET /actions/{id}` 对账。相同幂等键与相同意图返回原回执；换参数必须重新挑战并使用新幂等键。不要盲目重试换键。

跨语言调用无需复制服务器对 ActionIntent 的摘要算法：提交精确相同 intent 即可。`parametersSha256` 针对提交的原始参数字节；签名协议的 canonical JSON 由共享 Java 协议库处理。

| 命令 | parameters 类型 / 语义 |
|---|---|
| `GROUP_CREATE`, `GROUP_UPDATE` | `{name,enabled}`；分组 revision 比较更新 |
| `PLUGIN_CREATE` | `{name}`；插件属于 intent 分组 |
| `GROUP_MOVE` | `{targetGroupId,targetGroupRevision}`；同时要求两组授权 |
| `GROUP_GRANT` | `{userId,actions,read}`；只允许安全管理员 |
| `UPLOAD` | 普通 JAR 使用 `{}`；源码使用 `{manifest: ArtifactManifest}` |
| `VALIDATE`, `DELETE`, `REVOKE`, `QUARANTINE` | JSON 字符串 artifactId |
| `PUBLISH`, `CANARY`, `PROMOTE`, `ROLLBACK` | `ExecutionDocuments.Release`：稳定/灰度 bundle、精确 members、权重、目标节点、前后 revision 等 |
| `CANCEL_RELEASE` | `{deploymentId,preparationId,pendingRevision,reason}` |
| `USER_CREATE` | `{username,loginPassword,operationPassword,securityAdmin}` |
| `USER_UPDATE` | `{userId,expectedCredentialVersion,enabled,securityAdmin}` |
| `FACTOR_RESET` | `{userId,expectedCredentialVersion}`；不允许自重置因素 |
| `CREDENTIAL_UPDATE` | `{currentLoginPassword,newLoginPassword,newOperationPassword}`；至少一个新密码，intent 固定 `security/identity/user-{userId}` |
| `MACHINE_CREATE` | `{id,kind,environments}`；服务端生成 token |
| `MACHINE_UPDATE` | `{id,expectedRevision,environments,enabled}` |
| `MACHINE_ROTATE` | `{id,expectedRevision}`；生成暂存 token，旧 token 此时有效 |
| `MACHINE_ACTIVATE` | `{id,expectedRevision,rotationId}`；必须已领取暂存 token，激活后旧 token 立即失效 |
| `TRUST_UPDATE` | `{operation,family,keyId}`，参照 SigningService.Rotation 的字段和流程 |
| `POLICY_UPDATE`, `DEPENDENCY_REGISTER`, `EXCEPTION_CREATE` | 对应 ResourcePolicy、`{coordinate,sha256}`、ExceptionPermit |

操作码权限在服务端重新验证。安全管理员也需要强化认证；普通业务库的超级管理员标志不能授予发布权。单人可以持有上传和发布两项权限，不需要第二人。账号改权/因素或密码变更提高凭据版本，旧会话、挑战、授权和待发布资格失效；最后一个有效且已注册的安全管理员不能被禁用。

上传：`PUT /uploads/{upload}/files/{fileIndex}/chunks/{chunkIndex}`，Content-Type 为 octet-stream，每片 1 MiB（尾片除外）；`POST /uploads/{upload}/complete` 做整文件 hash 核验。客户端 SHA-256 只保证预期字节一致，签名和独立授权决定是否可以执行。

查询：`GET /groups`、`/groups/{group}/plugins`、`/plugins/{plugin}/artifacts?environment=...`、`/deployments?environment=...&offset=0&limit=100`、`/deployments/{id}?environment=...`、`/accounts`、`/security/audit?after=0&limit=100`、`/security/public-keys`、`/security/machines`。部署视图要求对所有成员组有读权限，不返回发布人会话信息。

账号创建返回 userId，随后由同一创建会话调用 `POST /accounts/{id}/enrollment-ticket` 领取一次注册票据；实际路径以 AccountController 为准。机器 token 通过 `POST /security/machines/{id}/token` 同会话领取一次，有效领取窗口 15 分钟，响应不得落入代理日志。领取响应丢失时重新授权暂存另一 token；不能从查询接口找回明文 token。

## 7. 发布、灰度与密钥轮换

初次 `PUBLISH` 仅接受稳定版本；`CANARY` 保留稳定版本、节点集合和桶盐，并指定候选 bundle；`PROMOTE` 仅允许当前灰度候选成为稳定版本；`ROLLBACK` 仅允许历史稳定向量且使用更大 revision。灰度权重按万分比，500 = 5%。同一可信租户/主体稳定落桶，客户端不能通过版本头指定代码。

发布动作先 ACCEPTED / PREPARING，所有目标节点返回本次 `preparationId`、release hash 和精确制品集合后才 ACTIVE。10 分钟内未就绪则 EXPIRED；账号、权限、归属、策略或目标节点凭据发生变化则 FAILED；经授权可 CANCELLED。取消/过期后重新发布必须新动作、新强化验证；旧就绪回执不能用于新动作。已有稳定路由保持有效租约内运行。

节点同步激活不是全局同一时刻切换；上游负载均衡若要求严格一致，必须只路由到已经确认目标修订的节点。当前已提供完整节点分钟窗口门禁和单人强化授权的自动回滚；阈值、时间窗、缺节点行为与部署限制见 deploy/README.md。未提供负载均衡控制器，不宣称全局瞬时切流。当前节点指标可从业务宿主 `/api/extension/metrics` 查询，要求 `extension:runtime:read`；按版本记录调用/失败/耗时分桶，p95 是桶上界，重启后重置，不是集中持久监控。

签名 key 分环境和 `artifact` / `execution` 家族。轮换顺序：强化授权 stage → 获取公钥 → 运维更新每个目标宿主受保护 trust.json → 节点自动提交指纹 ack → 强化授权 activate → 等旧文档/租约过渡完成后移除旧公钥 → 节点 ack 确认已移除 → 强化授权 retire。未安装的新 key 不能激活，仍被节点信任或正在签发的 key 不能退休。私钥只在安全服务内部使用 KEK 加密存储。

机器 token 轮换和签名公钥轮换是不同流程。建议使用 `node-token-file`：宿主在每次控制面请求时读取受保护文件，运维原子替换文件后即可零重启切换。该文件包含单个 URL-safe token，可带结尾换行；权限必须限制为服务身份和受信运维可读。先暂存并领取新 token，再安排文件替换和 `MACHINE_ACTIVATE`；两步之间短暂续租失败不延长旧租约，必须在现有租约有效期内完成。文件缺失、过长或包含额外内容时失败关闭。也支持启动时 `node-token`，但两种来源不可同时配置，静态来源不提供热切换。

## 8. SDK、有状态工作和恢复

最小 SDK 与可构建样例见 [echo 示例](../../examples/extension-echo/README.md)。实现 `BusinessPlugin`，单版本单实例；initialize 不产生业务副作用，close 释放资源；execute 通过 PluginCall 获取宿主可信身份、版本、deadline。不要自行创建线程、反射主工程、直连数据库/网络、加载 native、动态类或访问文件；通过已声明、宿主明确实现的 `ExtensionCapability` 贡献业务能力。

`BusinessExtensions.invoke` 为无状态调用；`invokeWork` 接受幂等键并在业务库固定原 bundle 和输入 SHA-256。HTTP 分别是 `/api/extension/invoke/{deployment}/{plugin}/{entry}` 和 `/api/extension/work/{deployment}/{plugin}/{entry}`，后者需要 `Idempotency-Key`；`GET /api/extension/work/{id}` 查询结果。业务 token 与条目权限均要通过。

相同工作键和不同输入字节拒绝；SUCCEEDED 重放已有结果，不重新执行；RUNNING/UNKNOWN 不自动重跑。异常或外部效果不确定时保留引用及原向量，不能“重新点击”切换到新版本重做。新增 `/api/extension/work/{id}/reconcile` 及新版“运行与工作”查询界面：必须具有 `extension:work:reconcile`，仍要求工作归属和入口权限。稳定业务模块实现 `ExtensionWorkReconciler`，按原工作ID、输入hash、claim和版本核验权威业务记录。返回终态前必须以业务事务/fencing保证迟到执行不能再产生效果；返回 PENDING 不改变账本。只允许收敛到 SUCCEEDED 或 CANCELLED，未注册适配器直接拒绝，没有由请求指定结果、任意重试或强清引用的入口。该机制不承诺 exactly-once，业务适配器的隔离/幂等实现仍需实测。

内部 EVENT/TASK 通过稳定宿主实现 `ExtensionJobPolicy`，由可信租户上下文调用 `invokeJob`；拒绝附带人类 HTTP 会话的伪造后台调用。通用事件消费者/调度产品仍需接入具体稳定业务适配器。

OpenAPI 新入口 `POST /open/extensions/{deployment}/{plugin}/{entry}` 沿用现有 OpenAPI token/签名/nonce 验证链，正文上限 64 KiB；可带 `Idempotency-Key` 使用持久工作。业务模块必须提供 `ExtensionApplicationPolicy`，依据服务端已认证的 appId、tenant、portal 返回该插件的权限集合；没有策略/无权限则拒绝，不能用人类角色给机器提权。回复 `data` 包含 workId（字符串）、state、releaseRevision（字符串）、bundleId、json（Base64 JSON字节）、failure；无状态 workId/修订信息为空。有状态回复固定原向量。新公开契约位于 common::extension，SDK v1 和原人类调用语义不变，宿主/OpenAPI需同批升级。

## 9. 运维与验收边界

关键发布动作在独立安全库事务内写入 hash-chain 审计。该链可检测锚点之间的不一致，但同库完全可写的攻击者可以重写整条链；生产应配置独立存储和周期外部锚定，已提供 AUDITOR 独立拉取、链锚点校验、追加文件留存和确认门禁；真正的WORM保留与独立主机权限仍需运维配置，见 deploy/README.md。宿主 `sma.extension.invocations` 日志仅含调用标识、租户/主体、版本/hash、结果状态和耗时，不含 JSON 正文、密码或业务键；日志采集、访问控制和保留期由部署方配置。

备份必须同时覆盖安全数据库、制品仓库、KEK 和受保护公钥配置，分离保存恢复凭据。不要把旧备份恢复到仍在接收流量的控制面：节点单调检查会拒绝修订倒退。恢复演练需隔离流量、核对最后可信审计锚点与发布 revision，重新校验机器凭据/信任和扫描时效，再受控恢复；当前未完成灾备实测。

疑似宿主失陷时先从入口和网络隔离节点，强化授权禁用其机器身份并撤销受影响制品；等待租约撤销边界，在干净机器重新部署。不要依赖向已失陷 JVM 下发命令实现自我证明。

生产前仍需完成：真实 Docker/Grype、独立安全服务 MySQL/HTTPS/多节点链路、扫描库更新互斥、主机权限、备份恢复、长时间卸载和压力测试；自动灰度门禁、受控业务对账、应用身份通道与独立审计镜像已有实现；真实业务适配器、跨机灾备/WORM以及生产容器扫描仍需按部署手册验收。当前验收证据在实施记录中逐项区分，不以单元测试替代这些条件。
