# P05 后端最新续接状态（2026-11-11）

> 历史检查点：用户随后已授权开发前端并继续补齐后端。**当前状态以 [全栈实施记录](fullstack-progress.md) 为准**；下文“前端不在范围”和当时未实现清单仅描述上一阶段，不再作为当前范围。

本记录更新旧版 docs/extension-backend-progress.md 中已过时的待办。旧文件当前被其他进程占用，无法写入；未关闭或中断该进程，故新增此检查点。

**完整目标仍在实施中，未宣称生产可用。** 前端不在本次范围，计费/风控不是业务需求，单人操作密码+TOTP授权上传和发布，不需要第二人。接入说明见 [backend-guide.md](backend-guide.md)，示例见 [extension-echo](../../examples/extension-echo/README.md)。

## 已实现的主链路

- 六模块 Maven reactor：SDK、协议、同 JVM 运行时、独立安全服务、隔离 worker、业务宿主。
- 分组/插件/授权、分片上传与 SHA-256、配额预占、不可变内存快照、递归依赖检查、字节码危险 API/natives/内部引用准入检查。不是 JVM 沙箱。
- 独立身份、Argon2id、全局 TOTP 时间步防重放、动作绑定 grant、幂等、恢复码；账号/密码/因素管理、单次注册票据与通知。
- 受保护本地引导及加密回执，导出失败可恢复；过期且未注册的初始账号可按精确原始输入重新发票据和种子，不重建管理员。
- Java 21 源码固定编译、禁 annotation processor。新增可信 CompilerDriver 在容器 tmpfs 编译，输入/SDK只读，有界 stdout ZIP 回传，无可写宿主输出挂载。新产物独立 hash、来源与工具链证明。
- 固定镜像摘要、无网扫描/探测、非 root、CPU/内存/PID/输出/时间限制。Grype SBOM 适配已有代码；**本机无 Docker，未做真实容器/扫描验收**，不提供 native 生产降级。
- 分环境 artifact/execution 签名密钥；stage/install/ack/activate/remove/retire。宿主不持有私钥，公钥文件热重载，运行租约和撤销检查。
- 多版本路由、稳定身份灰度桶、版本向量、持久业务工作账本、原始输入 hash/原 bundle 固定、原子 claim、成功结果重放、UNKNOWN 不盲重试、事务引用计数和历史向量恢复。
- 独立安全库发布审计 hash-chain、查询；后台 ExtensionJobPolicy/JOB 身份适配；动态权限目录只登记不自动授予。

## 本次续接新增/修复

1. 发布超时/取消/权限或节点资格变更时，部署和动作回执同步进入 EXPIRED/CANCELLED/FAILED；保留已激活稳定版。每次 PREPARING 绑定 preparationId，旧 ready 不得用于新动作；激活后相同回执重试幂等。
2. 节点 credential revision 在准备时固定，已就绪节点被禁用或改权后不得继续激活。CANARY 不能替换稳定向量/节点/桶盐，PROMOTE 只能选择当前候选，ROLLBACK 只能选择历史稳定向量并递增修订。
3. 强化认证机器创建/改权/禁用/暂存 token 轮换/显式激活；明文 token 仅创建会话一次领取，rotationId 防止串用；新增环境自动建立环境签名密钥。宿主 node-token-file 支持原子替换后零重启读取，文件异常失败关闭。
4. 部署查询按全部成员分组权限过滤，不暴露发布人的会话信息。
5. 宿主实际调用日志只记身份/版本/hash/结果状态/耗时，不记业务正文；按版本计数/失败/耗时分桶，限制指标键数量和处理数值溢出，观测失败不把已成功业务转成失败。
6. worker stdout/stderr 严格限额，源码和扫描报告不再通过可写宿主输出目录回传。修复 Shade 继承父 POM transformer 配置导致的打包失败。
7. 增加后端部署/API/SDK/恢复手册和无业务副作用的 echo 源码/JAR 示例。
8. 每轮宿主同步先单独续租/恢复当前服务版本，再预热待发布版本；每个修订独立记录租约保留，避免新准备覆盖旧版的制品保护。新节点/离线节点可从完整可信签名向量直接追上更新修订，继续拒绝倒退。

## 当前验证证据

- extension-continuation-test.log：续接初始字节码/协议/运行时/安全服务/worker测试通过。
- extension-release-lifecycle-test.log：协议7、runtime6、安全服务17项通过，含多节点禁用、取消/过期/旧回执、token分阶段轮换。
- extension-worker-bounds-test.log：worker5项通过，含固定可信源码fixture编译、有界输出和可写挂载拒绝。不能称为真实Docker验证。
- 首次全reactor：业务后端137项通过，但worker打包失败，安全服务被跳过。失败是Shade配置合并，已修正；不能把这一轮称为全量成功。
- extension-packaging-test.log：修正后上游模块verify和可执行包构建成功：协议7、runtime7、worker5、安全服务18项；覆盖过期引导恢复、发布语义和观测失败不改变业务结果。
- 最新宿主指标/凭据文件与最终 reactor 检查已结束，终态见下面追加条目。日志位于backend/target；旧日志可能因清理丢失。

### 续接追加证据

- `extension-sample-package.log`：按示例文档执行 SDK 安装和独立 Maven package 成功。实际 Maven JAR 通过 shaded worker 的 ContractProbe 加载/初始化/关闭，未执行任意外部代码。示例 SHA-256：`69226fd1094944492e186cb2dbb2c0585aa8cc9d493278ac571564be5ba3982a`，2,684 字节。它仍未经真实 Grype 扫描，不可因此直接生产发布。
- 第二次全 reactor `verify.log`：上游模块成功，宿主 147 项中 1 项 error：并行规则模块 `RuleFlowTests.olderFlowJsonWithoutNewOptionalFieldsRemainsExecutable`，`RuleJson.read:23` 报 `INVALID_JSON`。这不是全仓通过；未改动该并行模块。最新宿主 ExtensionTelemetryTests / NodeCredentialTests 通过。
- 新增稳定版本独立续租与离线跳版本测试后，使用 `verify -Dtest=*Tests,!RuleFlowTests -Dsurefire.failIfNoSpecifiedTests=false` 检查其余 reactor，日志 `extension-final-verify.log`：**BUILD SUCCESS，179 项通过**，协议7、runtime8、worker5、安全服务19、业务后端140；0失败、0错误。明确排除了该规则测试类，不能描述为没有例外的全仓通过。最终全部后端模块打包成功。
- 机器可读记录见 [verification-2026-11-11.json](verification-2026-11-11.json)。没有仍在执行的本轮测试进程；没有提交代码或重启业务服务。旧进度文件仍保留原状，本文件为最新续接检查点。

## 仍保留的工作范围

- 真实Docker/Grype与镜像版本/schema、扫描库更新互斥，独立安全库MySQL/HTTPS/多机/网络分区，长时间装卸/资源压力/主机权限/灾备演练。
- 自动灰度门禁、可信多节点时间窗聚合、告警及预授权回滚。当前手动灰度/晋升/回滚和节点内存指标不等于这些能力。
- 崩溃RUNNING与UNKNOWN的业务权威结果核验、fencing、人工处置和引用对账；真实事件/调度和OpenAPI应用身份通道。
- 外部不可变审计与锚点、采集故障/保留策略；同库hash-chain不能防完全控制该库的攻击者重写整条链。
- 查询分页/存储统计、角色和通知送达、保留策略攻击矩阵、离线孤儿引用退役。部分列表仍加载后筛选，仅适用于低频控制面。
- 数据库/事件/缓存兼容、结果契约与非幂等能力的具体业务接入；不能宣称通用框架提供exactly-once。
- 按P05逐条完成验收，不因BUILD SUCCESS缩小目标或标记完整完成。

## 接续约束

保持当前用户修改和并行frontend-next/P06/rule工作，不重启应用、不触碰生产数据、不自动安装容器、不提交无关内容。prepare wire DTO新增preparationId，机器记录新增revision，宿主与安全服务应使用同批构建。root/SYSTEM或JVM任意代码执行可以绕过本机防护；依靠外部隔离/撤销/可信重建。离线租约最长15分钟且受扫描时效约束，禁用凭据不会瞬间撤回已签发租约。
