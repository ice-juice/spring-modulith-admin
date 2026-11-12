# P08 缓存治理与多实例实施进度

方案：[P08](proposals/P08-cache-governance-and-cluster.md)。2026-11-11 用户已授权整理全部方案并开始实施；保持完整范围，不以单节点缓存替代多实例验收。

## 实施任务与门禁

每项完成后立即验证、记录证据并更新状态；失败先修复当前项，不跳过进入依赖阶段。计划中的测试名是拟定交付，不代表已有通过证据。

| 顺序 | 任务 | 依赖 | 验证方法 | 状态 |
| --- | --- | --- | --- | --- |
| S0 | 固化方案、区域清单、公开契约与现状边界 | 无 | 文档链接检查；区域契约校验测试；ModularityTests/ArchitectureRulesTests | 完成（22项通过） |
| S1 | Local/None、容量/TTL/并发保护、参数迁移、旧 SPI 兼容 | S0 | 缓存契约测试；ParameterTests/MonitorTests；回滚与并发回填 | 完成（标准reactor通过） |
| S2 | Redis、持久失效补偿、节点游标/代次与故障保护 | S1 | 真 Redis、独立客户端/观察器、重复/乱序/断线恢复、配额/序列化/回源测试；后端双进程另见M3 | 完成（标准reactor及双前端通过） |
| S3 | 字典/偏好/IAM 展示等基础数据接入 | S2 | 每类写入口与租户/语言/portal/用户隔离；鉴权回归 | 完成（完整回归＋失败专项修正复验） |
| S4 | 缓存目录/策略/任务/预热/暂停/统计/审计与 UI | S3 | 后端拒绝路径、幂等任务/部分失败；前端 verify、E2E；旧版兼容 | 完成（分轮证据见下） |
| S5 | 二开门禁、运行时/文件/保护状态适配 | S4 | 隔离业务夹具、禁止依赖、规则在途保护、节点资源边界 | 完成（S5a/b/c，分轮证据见下） |
| M1 | 共享票据、OpenAPI 限流、会话并发加固 | S5 | 双实例跨节点认证、验证码/TOTP/票据一次消费、撤销/锁屏竞争 | 完成（M1a/b/c/d、主键及受理容量前置，真实双JVM14组通过） |
| M2 | 跨节点推送、可靠补查、共享文件与后台任务 | M1 | A事件/B连接、广播丢失恢复、A上传/B下载、任务重复/认领 | 完成（M2a/b/c＋本机17组双JVM；多主机/LB另见M3） |
| M3 | 节点查看/定向管理、LB/代理信任、密钥/ID/健康/优雅下线和回退 | M2 | 关闭粘性双实例、节点路由/故障注入、滚动升级及一致配置预检 | 进行中（M3a/b/c1及c2a/b1～b8完成；b9清单/公共观察/本地文件、共享见证及Google/Console传输已分项通过，其余传输/隐式执行待收口；整机排空/LB未完成，multi仍关闭） |
| S6 | 拓扑压测、运维手册、最终完整验收 | M3 | Redis standalone/Sentinel/Cluster；根 reactor 标准入口；新旧前端；全需求证据审计 | 待实施 |

## 2026-11-11 启动记录

### 后续 S2 的内部验收顺序

S1 全量通过后按以下顺序推进，各项未通过不得进入依赖项；不缩减 S2 总范围。

| 子项 | 交付与验证 | 状态 |
| --- | --- | --- |
| S2a | 提取存储/编码/可信作用域边界，Local/None 同一契约回归；参数 API 不变 | 完成（35项通过） |
| S2b | Redis 存储、命名空间/schema 隔离、绝对/负 TTL、同槽原子代次回填；真实 Redis 双客户端契约测试 | 完成（41项通过，其中真实Redis6项） |
| S2c | 同业务事务的持久失效登记、提交快路/重试、每节点补偿、断线冷启动；独立 MySQL＋双实例并发测试 | 完成（61项通过，证据边界见记录） |
| S2d | Redis 断线/恢复/代次丢失、回源限额/熔断、区域及租户配额、健康指标；故障注入与标准回归 | 完成（最终结果见下） |

完整 Sentinel/Cluster 拓扑与 LB 验收仍按 S6/M3 执行，S2b 的单机 Redis 通过不代表这些范围已完成。

- 分支：main；本任务为基座维护。工作区已有大量未提交后端、规则、认证、前端修改，保持原样，仅增量修改本任务需要的内容。
- 已复查：参数本地缓存、旧 SPI/监控接口、规则编译缓存、MySQL 会话与挑战、内存 WS 票据/连接、OpenAPI 本地限流、文件目录、模块/架构门禁。
- 已将两轮方案与多实例解释完整整理为 P08，包含二开约束、配置切换、可靠失效、运行时边界、用户访问连续性、兼容和验收矩阵。
- 尚未执行：本任务测试、Redis/LB 双实例、生产部署。当前不能声称支持 Redis 或集群。

### S0 验证

- 新增 `common :: cache`：CacheDescriptor/CachePolicy/CacheCatalog/ManagedCache；内部 CacheRegistry 拒绝重复与未知区域。CacheContractTests 覆盖归属前缀、保护状态禁止变更、TTL/配额边界及集合不可变。
- P08 两份文档相对链接检查：PASS。
- 首次隔离输出 `target/p08-s0`：4 项契约通过，但 ArchUnit/Modulith 将不符合标准路径模式的测试类识别为生产模块，架构失败；没有放宽门禁。
- 改用保持标准尾部的隔离目录后执行：`& tools/dev/verify-backend.ps1 -Standard -MavenArgs @('-pl','backend','-am','-Dtest=CacheContractTests,ModularityTests,ArchitectureRulesTests','-Dsurefire.failIfNoSpecifiedTests=false','-Dsma.verify.directory=target/p08-s0/target') -LogPath 'E:/juiceProgram/spring Modulith/backend/target/p08-s0-recheck.log'`。
- 结果：22/22（契约4、架构16、Modulith2），BUILD SUCCESS。此轮无数据库集成与运行服务验证。S0 完成，进入 S1；运行时注册归属自动校验继续在后续接入和二开门禁验证，不能把元数据前缀校验当作安全授权。

### S1 当前实现与验证

- 新增 Caffeine Local/None、可信 tenant/portal/user 键、JSON 快照、正/负绝对 TTL、缩短式抖动、同键加载合并、区域/全局重量及条目预算、单条限制、作用域元数据上限与统计。
- 参数使用统一 ManagedCache，保留旧 CacheRegion/Monitor HTTP 形状；`auth.*`/`login.*` 与 secret 值不入缓存；缺省值不被负缓存固化。真实事务绕过共享快照，提交后失效、回滚保留、旧加载不能回填。
- 缓存构造不允许把尚未交付的 redis/multi 配置静默当 local。部署覆盖文档：[运行说明](cache-governance-operations.md)。
- 单元专项：`verify-backend.ps1 -Standard`，Maven 参数 `-pl backend -am -Dtest=CacheContractTests,LocalManagedCacheTests,ModularityTests,ArchitectureRulesTests -Dsurefire.failIfNoSpecifiedTests=false -Dsma.verify.directory=target/p08-s1/target`；32/32 通过，日志 `backend/target/p08-s1-unit.log`。
- 独立创建本任务库 `sma_test_p08_20261111_0650`，未清理默认测试库/开发库。设置 `SMA_TEST_DB_URL=jdbc:mysql://127.0.0.1:3306/sma_test_p08_20261111_0650?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true`。
- 集成专项在上述 Maven 测试列表追加 `ParameterCacheTransactionTests,ParameterTests,MonitorTests`：39/39 通过，BUILD SUCCESS；日志 `backend/target/p08-s1-integration.log`。真实 MySQL 验证提交/回滚、secret 排除、各调用者 fallback、租户隔离、旧监控统计清理；未部署运行服务。
- 已追加配置绑定专项（带点区域 Map key、none 真正绕过、非法覆盖/未知 provider 拒绝启动），正在执行根 reactor 全量标准验证：`& tools/dev/verify-backend.ps1 -Standard -MavenArgs @('-Dsma.verify.directory=target/p08-s1-full/target') -LogPath 'E:/juiceProgram/spring Modulith/backend/target/p08-s1-full.log'`，仍使用上述独立库。结果待记录，S1 尚未标记完成。
- 环境预检：未发现 docker/podman/Redis 可执行程序；`wsl --list --quiet` 返回安装帮助而非可运行发行版。后续 Redis 验证需准备任务隔离运行时，不能把 Mock 当真实拓扑验收。
- 已准备隔离 Redis 前置环境：GitHub `redis-windows/redis-windows` 的 `8.10.3` msys2 社区构建（不安装系统服务）；下载页发布 SHA-256 与实际归档一致：`af16df340525b431e9ec8dabfd625d5e7a233f82805b5418819904ea909c447f`。归档来源记录在 `backend/target/p08-tools/redis-source.json`，仅监听 `127.0.0.1:16379`、无持久化、64MB；PID 6472，PING=PONG。首次启动绝对 Windows 配置路径未形成进程/监听，核实退出后用工作目录相对配置成功。此证据只证明测试运行时可用，不能代替 Redis Provider 或 Linux/Sentinel/Cluster 验收。
- 全量最终结果：上述标准根 reactor 命令 BUILD SUCCESS（09:54）；backend **231/231**，整个 reactor 共 **284 项，283 通过、1 条件跳过、0 失败/错误**。跳过项为已有 `SecurityMysqlHttpsTests`（未设置独立发布安全服务 HTTPS/MySQL 专项环境）。19 项新增缓存相关测试均通过；其他现有认证、配置、消息、规则、扩展等回归通过。
- S1 打包产物 `backend/target/p08-s1-full/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar`，未替换现有运行服务。文档链接与本任务差异空白检查通过。S1 已完成，进入 S2a；Redis/集群/新管理 UI 仍未交付。

### S2a 验证

- 提取内部 CacheLookup（可信作用域/键摘要/类型准入）、CacheCodec（独立 JSON 快照）、CacheTransactions（事务绕过/提交回调），保持公开 ManagedCache 与参数 API 不变。
- 标准 reactor 专项参数：`-pl backend -am -Dtest=CacheContractTests,LocalManagedCacheTests,CacheConfigurationTests,ModularityTests,ArchitectureRulesTests -Dsurefire.failIfNoSpecifiedTests=false -Dsma.verify.directory=target/p08-s2a/target`；35/35、BUILD SUCCESS，日志 `backend/target/p08-s2a.log`。未改数据库/外部运行服务。进入 S2b。

### S2b 验证与下一步

- 新增内部 RedisCacheStore：环境/租户/区域/portal/用户/schema/类型键空间，随机不可复用代次，同槽 Lua 原子获取/读取/条件回填，正负 TTL、精确坏条目删除。普通模块不能直接调用此内部存储。
- 使用 Boot BOM 的 `spring-data-redis 4.1.2`、`lettuce-core 7.5.2.RELEASE`；显式基础设施配置，不引入会让 Local/None 自动探测 Redis 的 starter 自动配置。
- 真 Redis 测试：两个独立 LettuceConnectionFactory，`SMA_CACHE_REDIS_TEST_PORT=16379`。测试仅随机 `p08test-*` namespace，收尾精确删除自己的键，不执行 KEYS/FLUSHDB。
- 实际命令：`& tools/dev/verify-backend.ps1 -Standard -MavenArgs @('-pl','backend','-am','-Dtest=RedisCacheStoreTests,CacheContractTests,LocalManagedCacheTests,CacheConfigurationTests,ModularityTests,ArchitectureRulesTests','-Dsurefire.failIfNoSpecifiedTests=false','-Dsma.verify.directory=target/p08-s2b/target') -LogPath 'E:/juiceProgram/spring Modulith/backend/target/p08-s2b.log'`。
- 结果：**41/41，0 跳过，BUILD SUCCESS**。其中 6 项真实 Redis 测试覆盖跨客户端读/清理、在途旧结果拒绝回填、代次键丢失不复活旧值、同槽 key、租户/namespace/schema/类型隔离、正/负 TTL 不续期、精确清理损坏条目。
- 证明范围：同一 Windows Redis 实例上的两个客户端；不是两个后端应用、LB、Sentinel、Cluster 或故障恢复整体验收。此前全量标准回归发生在 S1；S2a/b 后执行了上述有针对性的结构及契约回归，最终仍需全量。
- 下一项 S2c：接入同业务事务的持久失效登记、提交后快路/重试、各节点补偿和启动冷恢复，并用专用 MySQL＋真实 Redis 验证。然后 S2d 完成 provider 装配、回源限额/熔断/配额/健康，才能开放 `provider=redis`；不能以当前底层测试绕过这些步骤。
- 续作环境：专用 MySQL `sma_test_p08_20261111_0650`；Redis 127.0.0.1:16379，PID 6472（再次使用前核对 PID/监听/PING）；代码在 main，保留全部既有工作区修改。当前没有未收尾的 Maven 测试进程。Redis 测试环境保留供后续阶段使用，最终收尾只停止核实属于本任务的进程。

### S2c 实施与验证（进行中）

- 上一目标轮属于实际进展；本轮已复查 main、代码、规则、进度和 3306/16379 监听。Redis PID 6472 仍响应 PONG；继续使用原专用测试库，未操作开发库或业务服务。
- 追加 Common 的 `V1__cache_revisions.sql` 与租户受控 Mapper/实体；失效记录按租户/区域合并为递增版本，同业务事务更新。同一事务内重复区域失效合并，回滚不发布；各节点对自己使用过的范围独立核对当前提交版本，不使用可能跳过迟提交事件的 ID 游标。
- 新增 DurableManagedCache：提交后快速应用、每秒独立补偿、过期前台校验、冷读校验、失败绕过、恢复重试；独立 READ_COMMITTED 查询避免旧事务快照；可信后台范围临时恢复并保留调用者原身份。
- Local/None 生产装配已接上持久版本包装；参数清理明确开启写事务。Redis 增加同槽同步权威版本，固定宽度十进制比较保护 BIGINT 精度和乱序重试；尚未开放 Redis provider 装配。
- 首次无数据库专项：45 项通过、1 项测试配置错误（Mockito 在已经抛错的方法上用 when 重新设桩）；改为 doReturn，不改变实现拒绝/回源逻辑。
- 首轮整合验证：`SMA_CACHE_REDIS_TEST_PORT=16379`，独立 `SMA_TEST_DB_URL` 同 S1；标准 reactor 参数 `-pl backend -am -Dtest=CacheContractTests,CacheConfigurationTests,LocalManagedCacheTests,DurableManagedCacheTests,RedisCacheStoreTests,CacheRevisionIntegrationTests,ParameterCacheTransactionTests,ParameterTests,MonitorTests,ArchitectureRulesTests,ModularityTests -Dsurefire.failIfNoSpecifiedTests=false -Dsma.verify.directory=target/p08-s2c/target`；**57/57，0 跳过，BUILD SUCCESS**，日志 `backend/target/p08-s2c-integration.log`。
- 在该基础上补充：MySQL 真实业务提交/回滚驱动两套 Redis 客户端与失效观察器、目标失败后补偿；Caffeine 使用单调计时器，权威轮询检测墙钟回拨，避免延长缓存寿命或抑制校验。当前执行同专项追加 `RedisDurableRevisionIntegrationTests`，日志 `backend/target/p08-s2c-final.log`，结果待记录。
- 边界：此轮独立缓存/观察器及 Redis 客户端共享测试 JVM；完整后端双进程和关闭粘性 LB 仍是 M3/S6 的独立门禁。S2c 尚未标记完成。
- 最终追加专项结果：**61/61，0 跳过，BUILD SUCCESS**（03:04），日志 `backend/target/p08-s2c-final.log`。真实 MySQL→两套 Redis 客户端整合测试2项通过，墙钟回拨新增2项通过；S2c 完成。生产 Local/None 的失效已持久化，Redis provider 装配/熔断/配额仍在 S2d，整体多实例未验收。

### S2d 内部顺序

1. **完成**：回源总并发/区域并发上限、有限等待、熔断恢复门控与本地缓存同键竞争保护；单元/HTTP503验证。
2. **完成**：Redis provider 装配、连接参数校验、权威版本恢复、坏条目回源；63项专项含真实 TCP 断线恢复通过。
3. **完成**：Redis 区域/租户配额、统计及健康状态、配置切换回归与根 reactor 标准验证；S2整体已通过。

### S2d 第一项验证

- 新增 CacheResilienceProperties、CacheLoadGuard：全进程最多32个回源、每区域8个；None、事务绕过和版本校验失败的回源同样受保护。许可在正常/异常退出后释放，无无界队列；失败返回 CacheUnavailableException，经统一错误处理转换为真实 HTTP503 和中英文文案。
- 本地同键合并/版本核对等待改为有限锁等待（默认100ms、上限1s）；繁忙时受限回源。版本查询独立事务超时2秒；每次提交保存最低已提交版本，防止较旧的慢轮询在快速刷新因锁忙未执行时隐藏新提交。
- None 纯读取不再额外轮询权威版本，写入仍持久登记。新增 CacheCircuit 原语，单调时钟、失败阈值、单个半开探测、拒绝旧在途成功关闭新熔断周期；下一项才接入 Redis provider。
- 实际验证：在既有独立 MySQL/Redis 环境执行标准 reactor，Maven 参数 `-pl backend -am -Dtest=CacheContractTests,CacheConfigurationTests,LocalManagedCacheTests,DurableManagedCacheTests,CacheResilienceTests,RedisCacheStoreTests,CacheRevisionIntegrationTests,RedisDurableRevisionIntegrationTests,ParameterCacheTransactionTests,ParameterTests,MonitorTests,I18nWebTests,I18nBundlesTests,ArchitectureRulesTests,ModularityTests -Dsurefire.failIfNoSpecifiedTests=false -Dsma.verify.directory=target/p08-s2d/target`。
- **75/75，0 跳过，BUILD SUCCESS（03:09）**，日志 `backend/target/p08-s2d-resilience.log`。覆盖全局/区域额度、异常/中断释放、半开并发、慢轮询/提交竞争、双语HTTP503及此前MySQL/Redis整合回归。
- 仍待实现：生产 Redis provider 与连接配置、真正的 Redis 断线/恢复故障注入、分布式配额/健康统计；S2整体保持进行中，S3～S6/M1～M3不标记完成。当前无运行中的 Maven 验证；测试 Redis 6472 仍保留，后续复用前核对。

### S2d 第二项验证

- RedisManagedCache 已装配进 ManagedCache；通过 `sma.cache.provider=redis` 与显式 `spring.data.redis` 配置连接。Local/None 不创建 Redis 客户端或绑定 Redis 凭据；连接参数限制超时、拓扑互斥、端点格式，凭据字符串表示脱敏。standalone 实际连接已验；Sentinel/Cluster 工厂分支已编译，拓扑实际验收仍待 S6。
- 存储升级为 v2 单租户/区域 Hash：权威版本、代次、计数和字段原子共存，字段有绝对TTL，代次切换按作用域 UNLINK，无全库扫描；旧 v1 测试数据不复用。Hash 原子操作限制每作用域最多10000条/64MiB（有效预算仍不超过注册策略），拒绝写入时保留业务回源结果。
- Redis 每次读取先同步已经观察到的数据库版本；恢复旧快照/丢失代次不会覆盖已知新版本。没有进程内数据副本；断线、熔断、事务场景受限回源；损坏编码精确失效并回源。
- 首轮 `backend/target/p08-s2d-provider.log`：61项通过。追加真实装配和断线恢复后，标准 reactor 参数 `-pl backend -am -Dtest=RedisManagedCacheTests,RedisCacheStoreTests,CacheRedisConfigurationTests,CacheConfigurationTests,CacheContractTests,CacheResilienceTests,LocalManagedCacheTests,DurableManagedCacheTests,ArchitectureRulesTests,ModularityTests -Dsurefire.failIfNoSpecifiedTests=false -Dsma.verify.directory=target/p08-s2d-provider/target`；环境 `SMA_CACHE_REDIS_TEST_PORT=16379`。
- 最终日志 `backend/target/p08-s2d-provider-fault.log`：**63/63，0跳过，BUILD SUCCESS**。测试通过任务内 localhost TCP 代理断开/恢复链路，未停止原 Redis 或业务服务；证明失联不复用本地旧值、失效提交不依赖 Redis 成功、恢复后读取新值。此轮没有重跑 MySQL 集成，下一项统一回归。

### S2d 第三项及 S2 最终验收

- Hash 内的条目/字节配额由所有客户端在同一 Lua 操作中竞争，新增并发超额拒绝与超大替换保留旧值测试；作用域计数不扫描整个 Redis。Redis 启动必须使用部署专属 namespace（拒绝默认 sma），并检查服务端正数 maxmemory，Cluster 分支检查发现的节点；不会替用户 CONFIG SET。
- 新增 governedCache 健康状态及4个低基数 Micrometer 指标。Redis/BYPASS计数故障返回未知size/bytes=-1，两套前端只针对该值显示已有双语“暂时不可用”；保留原HTTP形状。旧前端缓存页有意变更已审阅，只更新 baseline.json 中该文件一条哈希。
- 完整 Spring 应用使用真实 Redis 与专用 MySQL：参数读取、事务回滚、提交失效、旧监控清理和健康状态通过。阶段日志 `backend/target/p08-s2d-complete.log`：**87/87**。
- 首次根全量在新增指标测试编译处失败：SimpleMeterRegistry 有 close() 但不实现 AutoCloseable；改为 finally 显式关闭。不是运行实现失败，没有放宽门禁。
- 最终标准命令：设置专用 `SMA_TEST_DB_URL` 与 `SMA_CACHE_REDIS_TEST_PORT=16379`，执行 `& tools/dev/verify-backend.ps1 -Standard -MavenArgs @('-Dsma.verify.directory=target/p08-s2-full/target') -LogPath 'E:/juiceProgram/spring Modulith/backend/target/p08-s2-full-final.log'`。
- **BUILD SUCCESS（09:30）；backend 270/270，整个 reactor 323项，322通过、1条件跳过、0失败/错误**。唯一跳过仍为既有 SecurityMysqlHttpsTests（独立发布安全服务专项环境未配置），缓存/Redis测试全部实际执行。
- 前端：`frontend-next` 的 `npm run verify` 通过（74单测、双语、类型/ESLint、基线、构建及发布预算）；旧版 `tools/dev/verify-frontend.ps1` 通过（47单测、类型/ESLint、构建及双语）。日志分别 `backend/target/p08-frontend-next.log`、`backend/target/p08-frontend-legacy.log`。此次只改变不可用计数文本，没有新做浏览器人工验收。
- 交付边界：三种 provider 及 standalone 的真实故障回源可用；未替换当前业务服务。完整双后端/LB、Sentinel/Cluster真实拓扑、共享票据/消息/文件与管理新界面仍按后续阶段执行。`deployment-mode=multi` 仍拒绝启动，不能把 S2 当作整个应用集群已完成。

### S3 内部顺序

1. 全局 defaults 与区域覆盖的边界、字典选项（租户/语言）、原始个人偏好（用户隔离/动态默认）及所有写入口失效；接入后旧缓存目录也要可见。
2. IAM 展示数据逐项审计：只缓存最小展示DTO/元数据，保留每请求授权/数据范围；覆盖相关新增/修改/删除/恢复/同步写入口。
3. 参数、字典、偏好、IAM 的 Local/Redis/None 契约回归、跨租户/语言/portal/用户及权限撤销拒绝；验证后再进入 S4。

### S3a 验证记录（完成）

- 已接入 `config.dictionary-options` 和 `config.preferences`；旧缓存目录自动发现登记的 DATA 区域。字典按语言区分，偏好只存当前用户原始记录，默认值每次合成；写入与 reset 在业务事务中登记持久失效。
- 新增配置覆盖测试与 Local/None/真实 Redis 共用的基础数据契约测试：翻译、树不可变、重命名、启停、删除、初始化负缓存失效、事务回滚、跨租户/跨用户、默认值变化、管理目录与命中统计。
- 首轮 `backend/target/p08-s3a.log`：42项，22通过、20错误。根因是 CacheRegistry 实现新的公开 CacheRegions 接口后被 Modulith 观测代理，但 final 类无法生成 CGLIB 子类；已去掉内部实现类的 final，保留不可变注册映射和公开接口边界。不是 Redis/MySQL 连接问题。
- 复验日志 `backend/target/p08-s3a-recheck.log`；独立数据库与 Redis 端口沿用 S2，根 reactor 指定 `CacheConfigurationTests,LocalBasicDataCacheTests,NoneBasicDataCacheTests,RedisBasicDataCacheTests,DictionaryTests,PreferenceTests,ParameterCacheTransactionTests,MonitorTests,ModularityTests,ArchitectureRulesTests`，输出 `target/p08-s3a/target`。结果待记录，S3a 未完成。
- 上述复验在 testCompile 遇到并行身份功能的 PermitApplicationUser 尚未进入当次主源码编译的短暂缺项；核对源文件已存在后原命令重跑，未修改并行身份代码。
- 最终 `backend/target/p08-s3a-recheck2.log`：**45/45，0跳过，BUILD SUCCESS**。包括三种 provider 各5项共同契约（真实命中/None不存储、字典、偏好、回滚及目录）、配置4项、架构/模块18项与既有功能回归8项。S3a 完成，S3b 接入可开始；S3整体未完成，尚未宣称多实例可部署。

### S3b 接入前审计

- DepartmentMapper 带 DataScoped，不能缓存无范围区分的查询结果。缓存键必须包含从当前可信身份重新取得的数据范围摘要，回源仍使用原 Mapper 拦截器；数据范围改变立即切换键。后台身份与人类身份不共用范围。
- PostMapper 无用户数据范围，展示选项按租户登记；保存/删除失效。UserService 和 ProfileService 都能改用户展示名称，bootstrap 也能创建用户，必须覆盖这些路径；账号/凭据/有效状态校验继续直查。
- PermissionService 的 entities/require 被实时授权使用，保留权威查询。只缓存展示 list 的 DTO；角色 grant 不入普通缓存。菜单展示与导航最终结果仍和本次权限交集计算。
- PermissionService.sync 会移动导航关联，除菜单展示外必须同时失效导航元数据；管理保存/删除和翻译修改也覆盖。现有业务事务内继续绕过缓存，不能为提高命中率削弱事务一致性。

### S3b 实施与验证（完成）

- IAM 新增5个展示区域：`iam.department-display`、`iam.post-options`、`iam.menu-metadata`、`iam.navigation-metadata`、`iam.user-display`。注册独立于使用缓存的服务，避免 Catalog→ManagedCache→Catalog 装配循环；跨模块仍只走 `common :: cache`。
- 部门键包含可信当前数据范围摘要，回源保留 DataScoped；用户目录仅存 id/name 选项或映射，按租户/portal隔离。批量姓名键规范排序后摘要，支持原有最多200个Long ID。禁用账号仍可用于历史姓名展示，搜索仍排除禁用账号；身份认证不读取该缓存。
- 字段/列表使用不可变DTO，菜单/导航多次查询通过原有只读事务读取一致快照。PermissionService.entities/require、角色有效状态/授权与账号凭据保持权威读取；业务事务中的调用仍绕过缓存。PermissionService.sync 同事务失效导航关联元数据。
- 写入口覆盖部门/岗位CRUD、用户创建/更新/删除、个人资料更新、bootstrap、菜单CRUD/翻译/目录同步、导航CRUD/翻译。现有模块没有独立恢复/导入入口；后续新增必须按失效规则接入。
- 新增三种provider共用的 IAM 专项5项：数据范围变化、岗位启停/重建、用户资料与门户隔离、权限撤销、目录同步导致导航关联移动，同时检查真实命中与None不存储。
- 正在运行根reactor专项（专用MySQL/Redis），日志 `backend/target/p08-s3b.log`，输出 `target/p08-s3b/target`；含三种IAM缓存专项与Department/Post/User/Profile/Permission/Navigation/RolePermission/AuthFlow、配置、双语、架构和Modulith回归。结果待记录。
- 结果：**62/62，0跳过，BUILD SUCCESS（04:23）**。三种provider各5项实际执行，IAM所有展示区域命中、同用户权限范围变化、授权撤销、资料改名、跨portal/租户、目录同步、回滚与None禁用均通过。S3b完成。
- S3c 将执行根reactor完整标准回归，统一覆盖此前Config/参数/缓存底层及安全功能；在该结果通过前，S3整体仍为进行中，不启动依赖它的S4实现。

### S3c 标准回归（完成，完整轮＋失败专项组合验证）

- 根reactor命令沿用S2完整入口，仅输出改为 `-Dsma.verify.directory=target/p08-s3-full/target`，专用MySQL及Redis16379环境不变。
- 首轮 `backend/target/p08-s3-full.log`：backend 316项、2失败/166错误；各扩展模块通过，原有发布安全MySQL/HTTPS条件专项仍跳过1项。整合失败根因是并行新增 `TenantCredentialCrypto` 的final声明无法创建Modulith代理；仅去掉该实现类final，不改加密算法/密钥逻辑。
- 两项断言失败为并行身份加固后旧 `AccessAnnotationsTests` 仍用字符串伪装人类身份。夹具改为真实SmaPrincipal，并增加带权限的未知principal拒绝用例；保留实际新权限拒绝逻辑。
- 正在复验 `backend/target/p08-s3-full-recheck.log`；尚未标记S3完成。此次全量会纳入编译时已存在的并行身份/凭据代码，不能把这些功能误记为P08交付。
- 该轮结束：backend **328项，321通过、7失败、0错误**；失败仅为4项I18nWeb旧WithMockUser身份、2项AccessAnnotations旧字符串身份和1项并行MailProfile HTTP/业务错误码断言。代理问题已消除，三种provider、IAM、TOTP11项、安全密码7项、会话、消息、规则及插件回归均通过。测试修正发生在该轮编译后，不能以源码已修复冒充该轮通过。
- I18nWeb测试在标注WithMockUser的用例执行前设置可信SmaPrincipal，匿名401用例保持匿名；保留双语503/业务错误的原断言。MailProfile断言已由并行开发更新为既有HTTP200＋业务404协议，本任务未改邮件实现或该测试。
- 最新完整复验日志 `backend/target/p08-s3-full-final.log`，重新编译后执行，最终结果待记录。
- 上述完整轮最终结果：backend **352项，350通过、1失败、1条件跳过**；整reactor **405项，402通过、1失败、2条件跳过**。新增跳过为并行P10的DataSourceIdentityMysqlTests（需第二专用MySQL库），另一项仍是SecurityMysqlHttpsTests。缓存/基础数据/IAM/安全/规则等全部通过，I18nWeb与AccessAnnotations修正已实际验证。
- 唯一失败是并行TenantDirectoryTests在MockMvc请求清空身份上下文后调用服务，期望业务拒绝却收到身份拒绝；并行开发已把服务断言移到请求前。本任务未修改租户生产代码或该测试。按验证范围重新编译并以根reactor标准入口专项复验 `TenantDirectoryTests`，日志 `backend/target/p08-s3-tenant-recheck.log`。明确区别“完整轮”与“失败专项复验”，不把它们表述成一次全量零失败；未受影响的已通过用例不反复重跑。
- 专项复验 **5/5，0跳过，BUILD SUCCESS**，同时完成根reactor打包；包括并行开发补充的租户用例。S3已验证范围的失败全部闭环，S3完成。组合证据为全量405项＋失败专项5项，绝非一次405项全部通过；两项无环境的条件专项仍保留在最终验收边界。

### S4 内部实施顺序（S3通过后）

| 子项 | 内容及边界 | 验证与完成门禁 | 状态 |
| --- | --- | --- | --- |
| S4a | 元数据只读治理契约、目录/详情/筛选、声明策略与有效来源、能力及权限；保留旧REST形状，旧清理入口也执行区域管理权 | 目录不得暴露值、租户/权限拒绝、未知/保护/无能力目标拒绝、旧接口兼容 | 完成（48/48） |
| S4b | 持久租户区域策略、乐观版本、暂停/恢复、统计独立重置；与业务版本同事务发布，节点核对状态 | 暂停禁读写、恢复新代次、策略缩短不复活旧条目、乱序/并发/回滚、Local/Redis/None一致 | 完成（分轮证据见下） |
| S4c | 准确目标预览、幂等异步任务、持久执行明细/审计、限速可取消预热；重试先查原任务 | 批量部分失败、重复请求、越权目标、认领竞争、崩溃恢复、预热不扩大范围；节点确认区分逻辑失效与物理回收 | 完成（分轮证据见下） |
| S4d | 现有缓存管理路由下目录/概览/任务/策略四页签，分组和场景筛选、权限按钮、双语状态及准确操作预览 | 新版verify与E2E、隔离真实联调、明暗/中英/窄屏/键盘；旧版兼容验证 | 完成（分轮证据见下） |

S4策略约束：配置来源按代码上限、部署defaults、部署区域覆盖、租户运行策略逐项显示；页面编辑只能进一步收紧部署有效上限。后端provider、namespace和连接配置仍只在部署重启时切换。暂停/策略变更必须产生新版本，所有节点核对同一持久状态；不能把某个节点内存中的开关宣称为集群策略。

S4a已写入（待验证）：新增common公开CacheInspection只读契约；观察包含进程生命周期ID、采样时刻、共享/本地条目范围、本进程统计窗口、序列化估算口径、声明/部署有效策略与逐字段来源。Redis区域实际字节上限另报min(区域预算,部署max-bytes)，Local另报本进程聚合预算，防止把两者混同。进程ID不作为集群节点租约或分布式ID分配依据。

新GET `/api/monitor/caches/directory`及`/directory/{id}`复用原菜单查看权限，旧GET列表形状保留；区域提供声明操作和当前身份允许操作。普通清理在服务层同时验证monitor:cache:clear和区域managePermission，旧clearAll仅处理登记的、当前身份获准的DATA/CLEAR区域；保护/未知/只读区域不能清理。旧SPI缓存若未注册，只保留过渡元数据展示，不能借旧接口绕过治理。

S4a专项正在执行：`CacheInspectionTests,CacheDirectoryTests,CacheConfigurationTests,MonitorTests,LocalBasicDataCacheTests,NoneBasicDataCacheTests,RedisBasicDataCacheTests,RedisProviderIntegrationTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests`，根reactor标准入口、独立库/Redis，输出`target/p08-s4a/target`，日志`backend/target/p08-s4a.log`。尚未增加前端四页签或运行策略写接口。

S4a首次专项在RedisProviderIntegrationTests排在首位时暴露测试生命周期问题：PER_CLASS使Spring实例提前创建，随后beforeTestClass清库会影响已启动测试上下文。该轮中止；该测试类仅1项用例，改用默认逐方法生命周期和AfterEach精确清理，未改变清库安全检查。中止shell后确认原测试JVM仍存活，按日志PID42476和原surefire启动jar时间戳核对后停止；新轮在清库前已移除旧进程。复验日志`backend/target/p08-s4a-recheck.log`，未重启业务服务或测试Redis。

S4a复验结果：**48/48，0跳过，BUILD SUCCESS（09:49）**，根reactor完成打包。覆盖只读策略来源/实际预算、目录交集筛选、保护状态/未知目标/只读区域拒绝、旧清理的双权限、真实HTTP详情不暴露内容或namespace、三种provider与Redis应用兼容、架构及模块回归。新接口已验证；前端四页签、持久策略和任务尚未实现，不据此宣称整个S4完成。

S4任务约束：目标由服务端按当时区域目录/能力/可信租户固化，预览与提交之间的变化必须检测；请求键限当前租户与发起者，重复提交返回同一任务。执行前复验操作者和权限，撤权后不得凭保留的旧权限快照执行。任务状态和每项结果持久化，租约/认领有界；结果不包含缓存值、完整键或连接凭据。UI对未知/离线/部分失败单独显示，不能因请求返回200就写成全部完成。

S4b实现注意：Common目前已有并行V2迁移，下一脚本先核对占号后追加，不改V1/V2。数据代次、策略版本和统计窗口不能混为一项：重置统计不得清掉缓存数据；暂停/策略收紧则要失效旧数据代次。权威版本与策略需在同一读事务取得一致快照，禁止先读版本后另开事务读策略造成混配。Local/Redis的作用域状态须同时接收有效策略和统计窗口，保留硬预算、绝对TTL及在途代次围栏；None仍可展示策略但不能因此开始存储。

预热先提供注册加载器契约和基座安全场景，禁止把任意字符串转换为SQL/脚本；按页、速率、总数/时长和当前授权范围限额。无加载器或用户私有区域不得显示伪预热能力。运行时/资源/保护状态适配在S5完成，S4不把它们当作普通Redis数据区域。

### S5/M1接入前源码补查（尚未实施）

- RuleOperationsService已有当前租户ALL范围保护，ExpressionEngine有本地容量，RuleFlowRuntime.clearTenantIdle只删除users=0的计划；统一菜单的适配器必须委托这些能力并保留原业务管理权，不能另写无保护的Map.clear。
- SqlTelemetry为当前租户＋数据源＋语句的本节点统计，已有reset；登记RESET_STATS而非普通DATA/CLEAR。IpLocationService持有随应用加载的v4/v6资源，不能为了“统一清理”关闭仍在服务的lookup。
- SocketTicketService当前在进程内保存票据哈希/Grant，issue/consume只有本节点语义；OpenLoginThrottle也是内存尝试计数。M1必须共享一次消费和失败预算，不能被普通cache.provider=none或管理清理重置。SocketHub连接仍应留在所属节点。
- NonceMaintenance当前通过进程内tenants集合枚举清理范围，M2需可信持久租户发现和幂等清理，不只依赖本节点访问历史。这些现状仍未改造，不能据基础数据Redis测试宣称LB已验收。

### S4b内部顺序与当前边界

1. **S4b1完成（31/31）**：持久状态和同一快照的读取；分离dataRevision/controlVersion/statsEpoch，行锁＋expectedVersion避免覆盖，回滚和跨租户竞争验证。Common已存在V1～V4，追加V5，不改旧迁移。
2. **S4b2完成（73项整合＋60项运行专项）**：Local/Redis/None应用持久状态；暂停禁读写、恢复/策略切换新代次、统计窗口独立重置、在途与冷启动/故障一致性，真实Redis验证。
3. **S4b3完成**：公开治理契约、权限/审计/并发冲突API、目录有效策略与暂停状态、注册能力；拒绝未支持能力、部署上限之外的配置。最新源码专项38/38通过。

先验证当前子项再推进依赖项；S4b1仅内部协调存储，不开放页面/接口或声明暂停已可用。运行策略只在所有消费节点升级后启用；回退不理解策略的旧版本之前须显式取消暂停/运行覆盖并换缓存namespace，不能把混合旧节点当作已支持运行治理。

S4b1实现：追加Common V5，数据revision允许0以支持初次仅重置统计而不清数据；新增controlVersion、statsEpoch、paused与整组运行策略字段。旧advance仍只增dataRevision。控制更新先创建/锁定当前租户区域行，再校验expectedVersion；策略重置显式持久NULL，毫秒精度不静默截断。state使用独立READ_COMMITTED事务的一次SELECT取得完整已提交状态。

S4b1专项正在执行：`CacheControlPersistenceTests,CacheRevisionIntegrationTests,ParameterCacheTransactionTests,ModularityTests,ArchitectureRulesTests,SqlStructureSafetyTests`，根标准reactor、原独立MySQL库，输出`target/p08-s4b1/target`，日志`backend/target/p08-s4b1.log`。已确认16379/PID6472响应PONG；测试仍使用既有3306。旧V1升级验证在专用测试库内的明确任务probe表重放V1形状和V5变更，验证旧revision保留、初始0版本及不完整策略拒绝，结束精确删除该probe表。

S4b1结果：**31/31，0跳过，BUILD SUCCESS**，根reactor打包完成。真实MySQL的初次统计重置/策略清空NULL/暂停恢复版本、事务回滚、租户隔离、并发编辑赢家、业务失效版本和旧结构升级均通过。较长耗时来自独立库重建；等待期间核实测试JVM与数据库查询存活，未重启或跳过。运行层尚待S4b2，不能把内部状态持久化表述为可用暂停接口。

S4b2审计发现：Redis synchronize原先为容忍乱序会向旧版本调用者返回当前generation；运行策略接入后，读写路径必须增加“请求版本与当前版本一致”的获取方式，否则落后节点可能借新generation以旧预算/TTL回填。保留修复/核对的单调语义，同时让旧版本读写受限回源；所有更新节点仍依赖持久状态核对。统计重置不能改变Redis数据generation或Hash条目计数。

S4b2已接入：Durable改为一次读取完整State，分别保留最低已提交dataRevision/controlVersion，拒绝策略版本或统计窗口倒退；None纯读取仍直接受限回源，控制通知/元数据/补偿会应用统计窗口。Local保留区域/节点聚合硬预算，并新增租户区域运行配额；暂停/策略切换保留代次围栏。各节点统计使用整体替换的计数窗口，在途旧请求只能更新旧窗口，不污染新统计。Redis读写严格获取同版本generation，落后观察器回源；不清Hash条目来重置统计。

首轮无MySQL运行专项 `backend/target/p08-s4b2-runtime.log`：**58/58，0跳过，BUILD SUCCESS**，包括三种provider共用14项与原Local/Redis/持久失效/容量熔断契约。随后追加控制版本慢轮询/统计倒退2项，以及真实MySQL＋两套观察器/Redis客户端的提交回滚传播3项。整合日志`backend/target/p08-s4b2-integration.log`，同专用库/Redis、输出`target/p08-s4b2/target`；结果待记录，S4b2尚未完成。

S4b2最终证据：

- 整合 `backend/target/p08-s4b2-integration.log`：**73/73，0跳过，BUILD SUCCESS（07:17）**。真实MySQL State驱动两套Local/None/Redis观察器；漏快速通知补偿、回滚不暂停/清统计、提交暂停/恢复及配额均实际执行，原参数/字典/偏好/管理接口和结构门禁通过。
- 整合运行期间复查并修正Redis冷初始化的双字段竞态：读路径的revision/paused/policy统一取同一个不可变State，不能混用先更新的AtomicLong revision和旧策略。该修正在独立输出`target/p08-s4b2-unit-final/target`重新编译验证，日志`backend/target/p08-s4b2-unit-final.log`：**60/60，0跳过，BUILD SUCCESS**。此轮不连接MySQL，不与整合清库冲突；Redis各自使用独立namespace。两个测试集合存在重叠，不声称133项不同测试或一次全量通过。
- S4b2完成。最新源码制品为`backend/target/p08-s4b2-unit-final/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar`；未部署。没有运行中的本任务Maven验证；专用Redis继续保留给后续阶段，复用前重查PID/监听/PING。
- 边界：当前控制写方法仅Common内部与测试使用；S4b3尚未提供公开治理入口、权限/审计、目录运行策略展示。普通业务不能调用internal存储管理策略，不能宣称管理页已经可暂停。区域统计重置不重置全局熔断/回源拒绝的累计健康指标；后台确认存在传播窗口，完整节点确认待S4c/M3。

### S4b3实施记录（完成）

- 新增公开CacheGovernance契约，Monitor通过自己的服务适配请求；修改策略/恢复部署策略、暂停/恢复、统计重置分别使用policy/pause/reset-stats权限，并叠加区域managePermission。基座Config/IAM区域的区域权限改为monitor:cache:view，避免策略编辑被错误地绑定到clear权限；原清理依然要求clear，实际菜单授权含view祖先。二开仍按自己的管理权声明。
- Common V6追加不可变控制审计表；行锁内取得精确before，状态写入和after审计同事务提交，审计失败回滚控制状态。记录可信租户/操作者/portal、原因、前后版本/策略，不记录缓存内容、完整key或连接信息。history按当前租户/区域管理权过滤，最多100项；请求拒绝仍由原HTTP审计记录。
- 新HTTP治理路径在原/api/monitor/caches下：GET/PUT `{id}/policy`、POST `{id}/policy/reset`、POST `{id}/pause`、POST `{id}/resume`、POST `{id}/statistics/reset`、GET `{id}/history`。写入必须显式expectedVersion和原因，旧版本返回真实HTTP409＋双语文案，不自动重试覆盖。部署provider/namespace/连接参数不作为可编辑字段。
- 目录观测增加可空的Control，保留旧Observation构造器；确认仅代表当前进程，不能冒充所有节点已完成。未确认时paused为未知、有效策略未知；确认后展示TENANT_RUNTIME/DEPLOYMENT_CAP等逐字段来源。部署全局字节上限同样限制运行策略，不能用区域覆盖扩大。
- 首轮专项 `backend/target/p08-s4b3.log`（输出`target/p08-s4b3/target`，原专用MySQL/Redis）已启动，含新权限/审计/并发与HTTP测试及缓存目录、三后端基础数据、持久状态、架构/双语/安全端点清单。运行中又补强“版本字段不可省略”和“部署全局字节上限”，最终需重新编译对应用例验证，不能把旧编译结果代替最新源码。
- 最新非数据库专项已独立重编译：`backend/target/p08-s4b3-unit.log`，输出`target/p08-s4b3-unit/target`，**33/33，0跳过，BUILD SUCCESS**。验证操作权与业务区域管理权交集、机器/只读/保护区域拒绝、全局与区域部署上限、毫秒精度、JSON省略version保持null并校验失败、状态未知不冒充未暂停，以及双语/模块/端点规则。该轮不连接MySQL，与正在运行的整合库隔离；两轮证据分别记录。
- 首轮整合结束：62项、1失败/1错误；其余60项通过。审计故障注入在配置Mockito时触发MANDATORY事务代理，留下未完成桩并影响后续HTTP审计断言。仅在测试中解包真实spy目标设置故障，不放宽生产审计事务要求。最新源码正在根reactor专项复验（`backend/target/p08-s4b3-final.log`、`target/p08-s4b3-final/target`），同时覆盖缺失expectedVersion的真实HTTP400；结果未出前S4b不标记完成。
- 最新专项结束：**38/38，0跳过，BUILD SUCCESS（19:23，10:09）**。包含审计失败/外层回滚、并发赢家、真实HTTP409/400、不同操作权限及租户隔离；错误注入改正后HTTP审计记录恢复，未更改生产事务/审计逻辑。原整合60项通过＋最新专项38项是两轮有重叠的组合证据，不表述成98个独立用例或一次完整根回归。根reactor打包完成，产物未部署。S4b完成，进入S4c1。

### S4c细化计划（依赖S4b3验收，尚未交付）

| 子项 | 交付 | 验收 |
| --- | --- | --- |
| S4c1 | Common公开任务契约、服务端固化预览和准确跳过原因、持久任务/明细、复用RequestExecution提交幂等、查询与取消 | 同键同任务、异载荷冲突、租户/操作者隔离、目录/策略变化拒绝、过期与容量边界；事务回滚不产生可执行半任务 |
| S4c2 | 有界认领与围栏、执行前恢复最新账号/权限、逐项事务、部分失败和崩溃恢复 | 双worker竞争、撤权/停用、旧围栏拒绝、取消不撤销已完成项、数据库提交不明先查任务 |
| S4c3 | 注册分页预热加载器、限额/取消、节点观察确认 | 无加载器不能预热、用户私有范围拒绝、在途失效、离线节点与未知状态不算成功；逻辑失效和空间回收区分 |

任务创建只把本地数据库写入交给既有`common :: request`账本，不将缓存网络操作放进RequestExecution.Work；任务执行和外部/本地预热另有原生任务状态机。重放仍授权，但不能因原预览过期或操作已推进而新建第二任务。幂等键域沿用可信租户＋操作者＋portal，不自行建立另一套可冲突账本。普通缓存清理永不影响任务、请求回执或安全预算。

### S4c1实现记录（完成）

- 新增公开CacheTasks及Common内部Planner/Store/Service；Common V7追加预览/任务、条目和租户容量锁，不改V1～V6。操作先支持CLEAR/PAUSE/RESUME/RESET_STATS；预热要等S4c3注册加载器后再开放。
- 服务端按区域ID稳定排序，owner/group/scenario按交集过滤，固化注册元数据/部署策略摘要与控制版本；业务数据revision变化不使预览无谓过期。最多128个条目，不截断成假“全部”；保护、未知、无适配器、无能力、无权与显式过滤排除逐项解释。提交时在事务内锁控制行复验，分组不授予权限。
- 预览默认5分钟（可配置30秒～10分钟），按操作者30次/分钟、租户120次/分钟预算限制；持久容量默认10000条/租户，租户行锁防并发超额。只限量物理退役从未提交的过期PREVIEW；已受理任务/明细/回执不因容量压力删除。无获准目标不能提交。
- 提交复用RequestExecution；同键重放返回原任务，异目标或一份预览重复新键拒绝，未知/失败回执不重新执行；任务和逐项PENDING在同一数据库事务入库。只记录可信租户、发起者/portal和租户安全代次，无旧权限快照。任务元数据仅本人/portal可见；取消另复验操作与区域管理权，重复取消幂等，保留原回执。
- 根reactor专项已启动：`CacheTaskPlannerTests,CacheTaskSubmissionTests,CacheGovernanceTests,RequestExecutionTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests,SecurityEndpointInventoryTests`，专用MySQL库不变，输出`target/p08-s4c1/target`，日志`backend/target/p08-s4c1.log`。此阶段尚无HTTP任务入口或后台执行器，不能把QUEUED受理表述为已执行清理。
- 整合等待期间复查：预览限流提前到区域版本读取前，部署namespace/抖动参与摘要但不返回原文。最新源码独立非数据库专项`backend/target/p08-s4c1-unit.log`（`target/p08-s4c1-unit/target`）**29/29，0跳过，BUILD SUCCESS**：交集筛选、准确排除、128上限、部署/控制变化、预算前置、停租户拒绝与配置边界，以及架构/模块/双语/端点清单。整合运行仍为此前编译，分别记录证据，不将这两处更新冒充已进入那次整合。
- 整合首轮50项，26通过/24错误、0跳过，BUILD FAILURE。错误均为Spring上下文未能启动：编译时并行RequestGovernanceProperties新增重载构造器但尚无ConstructorBinding。当前源码已由并行工作补齐注解和RequestConfigurationTests真实Bean绑定回归，本任务未改该配置类。Common V7空库迁移实际通过，但任务/请求账本/治理业务断言本轮没有执行，不能计为通过。将最新源码重新编译并加入配置绑定专项复验，日志`backend/target/p08-s4c1-recheck.log`；S4c1仍未完成。
- 验证环境隔离准备：复验继续使用3306原专用库，不中断。另以已安装的MySQL 8.4.10建立任务专属实例，仅127.0.0.1:13306，目录`backend/target/p08-tools/mysql-13306`，实际监听PID35444（launcher31704）。库`sma_test_p08_local`，测试账号只授权该库；随机凭据仅保存在受Windows ACL限制的本地target文件，不输出、不入仓库；一次性bootstrap文件已删除。JDBC实际核对port/database/current_user通过。此实例flush-log-at-trx-commit=2、禁binlog，用于后续功能/事务测试；不得作为断电持久性或生产MySQL HA证据。故障耐久性演练需单独恢复生产耐久配置并记录。未修改现有3306/3319服务或配置，后续复用前重查监听与身份。
- 新实例功能验证：根reactor标准入口，`-Dsma.verify.directory=target/p08-s4c1-isolated/target -Dtest=CacheTaskSubmissionTests -Dsurefire.failIfNoSpecifiedTests=false`，环境从本地受限连接文件加载。日志`backend/target/p08-s4c1-isolated.log`：**8/8，0跳过，BUILD SUCCESS**。实际证明受理/回执原子性、重复及并发提交、租户/用户/portal隔离、过期/容量/取消/回滚与重放；无后台执行或HTTP任务入口。本轮也验证新测试环境可用；原3306综合复验仍需收尾。
- 原3306综合复验已结束：`backend/target/p08-s4c1-recheck.log` **55/55，0跳过，BUILD SUCCESS（20:04，19:12）**。包含当前源码全部任务测试、原请求幂等11项、治理API5项、请求配置真实Bean绑定及结构门禁。S4c1完成；新旧两套MySQL的重复受理证据不相加成不同测试。Common V7已验证，后续只追加迁移。两轮产物均未部署；所有本阶段验证会话已结束，原测试Redis和两个专用数据库继续保留供后续验收。下一项S4c2执行器/新鲜授权/任务HTTP，S4c3才提供预热与节点确认。

S4c2执行前设计检查（实现与验证记录见下）：

- Common提供可信后台任务的身份解析SPI，由IAM按当前账号、角色/portal及数据范围重建；只接受持久任务恢复的SYSTEM_JOB上下文，不能让HTTP传userId恢复他人身份。worker不保存或重放旧permissions集合。每项执行前复验租户安全代次；停用/恢复租户不能让旧任务重新获得执行权。
- 同一任务串行执行，一次认领推进fence，租约到期允许其他worker接手；每项在READ_COMMITTED短事务中锁任务行、核对owner/fence/期限、查当前身份与能力/版本、提交权威变更和APPLIED结果。只在提交后触发缓存通知，失效失败由原持久版本补偿。未知数据库提交先查任务；不吞异常后在rollback-only事务里伪写成功/失败。
- 已认领但取消尚未提交的，后续PENDING项终止；已APPLIED项不回退。剩余项可分别DENIED/CHANGED/FAILED/CANCELLED，最终按实际项汇总SUCCEEDED/PARTIAL/FAILED/CANCELLED。取消请求与已终止分开，离线节点确认另由S4c3补充。
- 定时器通过TenantWorkQuery分页，固定每轮租户数/任务数，不扫描历史请求租户集合；测试禁自动调度并显式驱动。验证双worker竞争、旧fence、提交后进程丢失、撤权/停用、暂停恢复安全代次、版本变化、取消与部分结果；完成这些前不开放任务HTTP入口。

### S4c2a执行器与新鲜授权（完成）

- 新增CacheTaskActorResolver宿主SPI，IAM实现仅接受`cache-task:<任务UUID>`的SYSTEM_JOB，重新查询有效账号、portal、角色权限与数据范围；临时身份只用于解析IAM元数据，执行动作使用最终权限，结束恢复任务身份。Common不依赖IAM实现。
- 原生执行器按持久租户范围与租约/fence认领任务；每项在独立READ_COMMITTED事务核对租约、安全代次、当前账号/能力/控制版本，再把权威失效/治理和APPLIED结果原子提交。请求受理回执和执行结果分别表达，提交确认丢失后从持久项状态恢复，不能重放已完成项。
- Common V8仅追加每项attempts。最多3次认领尝试；崩溃/基础设施失败保留租约，到期接管前核对结果，耗尽转明确失败。权限/控制变化在回滚后以新事务记录DENIED/CHANGED，不在rollback-only事务中伪记结果。取消保留已APPLIED项，最终SUCCEEDED/PARTIAL/FAILED/CANCELLED按各项真实状态汇总。
- 调度通过TenantWorkQuery分页，固定租户/项目预算；独立单worker、容量1队列及单轮互斥，慢DB操作不占用共享Spring定时线程。测试profile明确关闭自动消费，集成测试显式驱动。尚未开放任务HTTP入口。
- 在13306任务独立MySQL执行根reactor标准专项：`CacheTaskExecutionTests,CacheTaskActorTests,CacheTaskDispatcherTests,CacheTaskSubmissionTests,CacheGovernanceTests,CacheTaskPlannerTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests,SecurityEndpointInventoryTests`。输出`target/p08-s4c2a/target`，日志`backend/target/p08-s4c2a.log`；结果待记录，不能进入S4c2b HTTP开放或S4c3。
- 该轮提前发现1项双语门禁失败：资源复制后并行Identity新增LOGIN_LIMIT/EXPIRED/USED枚举，编译产物缺对应双语键。当前源码语言包已由并行工作补齐，本任务未改Identity。数据库专项继续执行；另在独立输出重编译最新非数据库专项（`backend/target/p08-s4c2a-unit.log`）验证语言资源与身份/调度/结构，不中断或复用正在运行的测试输出。
- 整合结束：52项，51通过/1双语失败、0错误/0跳过；其中执行器真实MySQL7项、受理8项、治理API5项及身份/调度全部通过。最新非数据库复验**28/28，0跳过，BUILD SUCCESS**，失败双语已闭环；这是一轮含失败的整合＋最新专项组合证据，不能称为一次80项全过。真实IAM撤权/禁用、租户代次、双worker、旧fence、三次崩溃、提交确认丢失、部分失败、取消及控制审计均已验证。S4c2a完成，Common V8后仅追加迁移；最新产物未部署，进入S4c2b HTTP接口。

### S4c2b任务HTTP（完成）

- 在原缓存路由下新增`/api/monitor/caches/tasks`：POST `/preview`、POST `/{id}/submit`（原Idempotency-Key）、GET列表/详情、POST `/{id}/cancel`。Monitor只调用公开CacheTasks，所有处理方法显式view权限，服务层按持久动作再检查clear/pause/reset-stats与区域管理权；不能只看Controller的view注解就认为已授权写入。
- 使用原生任务协议，不标注GovernedApi以免二次包裹请求账本事务。预览/提交/取消分别使用明确AuditAction与双语说明。结果返回任务状态与逐项结果，QUEUED/PENDING不是执行成功，APPLIED是逻辑提交。
- 在13306独立库启动根reactor专项：CacheTaskHttpTests、执行器/受理/治理/Monitor回归及架构、Modulith、双语、端点清单；输出`target/p08-s4c2b/target`、日志`backend/target/p08-s4c2b.log`。验证完整HTTP流、同键复用、缺key/无效动作、交叉身份、操作权和真实409；结果待记录。
- 首轮50项：47通过、MonitorTests 3项上下文错误，0断言失败。新HTTP5项、执行器7项、受理8项、治理5项和结构门禁均通过。Monitor上下文错误为本任务13306测试实例max_connections=40耗尽（多个Spring上下文各保留连接池），不是断言失败；经CLI核对@@port=13306后，仅把此测试实例及其本地ini上限调为400，未改应用池或其他数据库配置。失败专项重新编译复验`backend/target/p08-s4c2b-monitor.log`（输出`target/p08-s4c2b-monitor/target`），结果待记录。
- Monitor失败专项已闭环：**3/3，0跳过，BUILD SUCCESS（20:45，05:21）**。当前S4c2b组合证据是原47项通过＋Monitor重编译3项通过，不能表述为一次完整轮50/50。任务HTTP、单区域策略、旧缓存/监控协议和受影响拒绝路径全部通过；S4c2完成，任务可通过上述HTTP接口受理并由后台执行。产物未部署；所有本轮Maven会话已结束。下一项S4c3预热与节点确认，S4整体、统一页面和多实例门禁仍未完成。

S4c3开工门禁与细节（尚未实施）：

- 先明确节点集合的来源、生命周期和过期规则；仅有本进程UUID或“曾报告过缓存的节点”不代表部署节点全集。逻辑APPLIED与每节点已观察的dataRevision/controlVersion分开返回；缺样本、离线或观测失败保持未知/未确认，不能直接填全部成功。进程标识复用到检查/执行/确认时仍不是分布式ID分配依据。保留共享Redis条目不重复累加的语义。
- 预热须有唯一注册加载器、额外业务读取权限和固定分页/条数/时长/速率预算。首个基座安全场景可选字典；None、暂停、无加载器及用户私有区域拒绝，不把任意区域伪装成可预热。需记录实际执行节点，Local预热不能称为全节点预热。
- 预热枚举数据可用短只读事务，但ManagedCache回填不能包在任务数据库事务中（当前契约会主动绕过缓存）。读取/回填与游标检查点分开，检查点受任务fence、当前授权和控制版本约束；失败重放只允许无业务写副作用的分页读取。尝试预算须按未完成批次计算，不能让正常多页任务因累计认领超过3次而失败；成功进度、取消请求和已加载条数明确区分。
- 任务协议新增字段/枚举只能追加迁移，并验证原V7/V8任务载荷可读；PREWARM不能投递给不认识它的旧worker。补齐兼容/部署顺序后，再接统一四页签。

### S4c3a 节点范围与持久确认（完成）

- 来源采用部署expected节点清单和Common拥有的全局节点租约，不把“用过缓存的节点”当全集。node-id为清单逻辑ID，process-id为本次进程UUID，租约epoch识别接管；这不是Snowflake节点ID。配置清单最多32节点，每次任务最多512个区域/节点组合；清单变化或节点代次变化拒绝首次提交。
- 拟改路径仍在已授权的common/cache、common/internal/cache/service/persistence、monitor与Common追加迁移。新增全局表仅为框架节点租约及其清单互斥行（继承BaseEntity，沿用现有平台元数据映射规则，不添加InterceptorIgnore或放宽全局拦截器）；不保存用户/租户业务内容，只允许宿主无请求身份的心跳更新。不同清单不能在同一存活集群中同时登记。任务节点快照及确认表仍按TenantEntity隔离。没有全局HTTP写入口。
- 预览固定节点清单与当时已知进程/epoch，提交只用该快照。缺失快照的V7/V8任务保留逻辑执行，节点覆盖标LEGACY_UNKNOWN；旧预览须重新生成，不伪造历史节点集合。已知节点离线不阻止逻辑失效，但其确认保持等待；代次替换不得冒充旧进程确认。
- 每个实例在独立有界线程分页发现租户，读取发给自己的已提交确认请求，应用持久CacheRevisionState后才记录确认。节点租约再次校验，旧进程不能写新代次确认；数据/控制版本都要达到要求，较新版本显式标记为覆盖确认。确认不执行预热、不读取缓存值、不等同物理回收。
- 验证：真实MySQL双节点租约冲突/过期接管、清单/代次变化、租户/任务归属、缺失/离线/替换/旧代次拒绝、真实Local/Redis状态应用与确认、旧任务未知覆盖；通过后再进入S4c3b预热。

内部顺序：S4c3a1先交付节点清单/租约及任务范围冻结，验证冲突/接管/旧载荷；S4c3a2再接持久确认、应用状态与HTTP观察，验证离线/替换不误报；S4c3b最后接分页预热及取消/预算。三个子项全部通过才完成S4c3。

S4c3a1当前实现与验证：

- 已写入节点属性/共享进程标识、宿主租约存储、任务node_scope_json与只读`/{id}/nodes`。节点范围与原Task View分离，旧调用保持原形状；字段`stateAtCapture`只代表预览时刻，不冒充实时状态。V7/V8任务无快照返回null；旧预览首次提交要求重建，已受理旧任务仍可逻辑执行。
- 全局写入只允许无请求/任务身份的宿主调用；用户提交只锁已有清单行，不在HTTP中初始化全局节点。未知节点一直保持未知，不能在提交时偷偷绑定新进程。清单变更还必须提升`nodes.version`，且等所有旧租约退出；即使全部新节点离线，旧版本也不能回滚清单。预览清单按版本/节点/进程/epoch检测变化，心跳续期本身不使预览过期。
- 当前V9仍为未交付草稿：首轮编译后复查补入单调清单版本，修改尚未验证的V9及对应代码，并增加真实配置Bean绑定和旧版本复活拒绝用例；V1～V8未变。首轮`backend/target/p08-s4c3a1.log`继续运行，不能代替最新源码验证。独立非数据库最新轮`backend/target/p08-s4c3a1-unit.log`已启动；待首轮终止后再用同一专用库重新编译验证最新V9。
- 首轮旧编译53/53通过；最新非数据库27/27通过；重新编译后的单调清单版本整合`backend/target/p08-s4c3a1-final.log` **54/54，0跳过，BUILD SUCCESS**，真实MySQL验证旧配置在全部租约过期后仍不能回滚清单、接管/冻结/旧任务与任务执行/HTTP回归。V9最终结构已验证，此后只追加迁移。
- 末轮运行期间发现租约可用性风险：用户提交若持有清单排他锁，可能延迟别的租户所在进程续租。当前改为任务校验持共享锁；同一进程续租也走共享路径，只有新登记、接管或清单变更取排他锁，统一保持清单→节点的锁顺序并在加锁后复验预读条件。新增真实事务并发用例证明提交锁持有期间心跳仍能完成。正在重新编译专项`backend/target/p08-s4c3a1-renewal.log`（`target/p08-s4c3a1-renewal/target`），结果待记录；54项不是这一锁修正的验证证据。
- 最新锁修正专项 **46/46，0跳过，BUILD SUCCESS（21:30，08:01）**。包括8项真实节点租约/范围用例、配置Bean绑定、原任务受理及HTTP与结构门禁；已证明共享提交锁持有期间同进程续租完成，身份替换和清单回退继续拒绝。S4c3a1完成；53、54、27、46各轮有重叠，不相加为独立测试数。V9和当前源码已验证并打包，未部署；无本轮运行中的验证会话。
- 边界：本阶段提供宿主内部租约存储、共享进程标识与任务预览的固定节点快照；尚无自动心跳/确认处理器，`stateAtCapture`不表示实时状态，不能据此宣称节点确认或负载均衡已经交付。下一项S4c3a2持久确认与状态应用，然后S4c3b预热。

### S4c3a2 自动心跳与持久确认（完成）

- 确认请求与每项APPLIED同事务发布；每租户/任务/区域/预览节点一条，绑定预览进程/epoch/清单及最低dataRevision/controlVersion。旧任务无快照保持LEGACY_UNKNOWN；旧已完成任务缺确认记录显示UNRECORDED，不伪造历史确认。未知进程不得自动绑定后来出现的实例。
- 心跳与确认处理分别使用独立有界线程，慢Redis/DB确认不能拖住续租或共享Spring调度。按TenantWorkQuery分页发现可信租户，应用本节点Durable状态后，在短事务内再次验证当前节点租约及两个版本，才持久化确认。失败保留请求并有界退避；确认不再次推进业务失效版本，也不声称物理回收。
- 报告把历史确认和当前ONLINE/OFFLINE/REPLACED/配置变化分开；较新版本确认为CONFIRMED_NEWER。任务仍保留独立逻辑状态，不能把HTTP200或某节点成功等同全节点完成。旧确认不可被重复上报改写。
- 已追加Common V10租户确认表和事务发布、双版本/租约复验、重试退避；自动心跳5秒，独立确认循环按16租户/每租户16请求与轮次时间预算处理，不能阻塞续租。宿主调度入口也拒绝请求身份，防止异步切线程绕过全局写入边界。无外部写入确认的HTTP接口。
- 新只读API：GET `/api/monitor/caches/nodes`为当前租约及本地观察器状态；GET `/api/monitor/caches/tasks/{id}/confirmation`为本人任务证据与当前节点状态。原`/{id}/nodes`仍是不可变预览快照。历史已确认后节点离线不抹去证据，但当前在线标记为false；版本更高显式展示覆盖确认，物理回收统一NOT_MEASURED。
- 正在13306专用MySQL＋16379专用Redis验证：`CacheConfirmationTests,CacheNodeRuntimeTests,CacheNodeMembershipTests,CacheTaskExecutionTests,CacheTaskHttpTests,CacheTaskSubmissionTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests,SecurityEndpointInventoryTests`；根标准reactor输出`target/p08-s4c3a2/target`，日志`backend/target/p08-s4c3a2.log`。包含三后端独立观察、实际回填失效、原子发布回滚、版本门槛、重复确认不可改写、旧代次、跨租户/人类身份拒绝、退避、旧任务、HTTP及独立心跳线程。结果待记录。
- 上述整合 **62/62，0跳过，BUILD SUCCESS（22:08，08:59）**。V10和三后端确认均实际执行；并行架构门禁本轮为17项，未降低检查。此后V10仅追加迁移。为核实调度组合而非仅单独处理器，再补真实数据库的后台线程闭环：独立Runtime心跳、TenantWorkQuery发现、后台状态应用及持久确认；仅运行新增方法，日志`backend/target/p08-s4c3a2-background.log`、输出`target/p08-s4c3a2-background/target`。生产实现没有再改动，结果待记录。
- 新增后台闭环 **1/1，0跳过，BUILD SUCCESS（22:22，08:17）**：真实异步线程完成心跳、租户发现、Local状态应用和数据库确认。测试显式触发调度入口；不是两个独立部署JVM或LB验收。S4c3a2完成，无遗留验证进程；后续S4c3b预热、S4d页面及M1～M3/S6门禁仍待实施。

### S4c3b 受控预热（完成）

- b1先定义注册SPI、固定预算和字典加载器，验证未知/重复/私有区域拒绝、权限、None/关闭/暂停、分页与语言上下文恢复；b2再接PREWARM任务、检查点/重试/取消和实际缓存命中。b1未开放HTTP任务，不能当成已可预热。
- 新契约`CacheWarmupLoader`由所属模块实现，固定声明业务读取权限、版本及条数/分页/速率/时长上限；加载器只能幂等读取，不接收HTTP脚本/SQL/游标。注册元数据冻结，重复或用户私有区域启动失败。字典使用主键游标逐项枚举zh-CN/en-US，回填调用正常options路径，显式拒绝实际事务并恢复语言上下文。
- `sma.cache.tasks.warmup.enabled`默认false，须所有任务消费者升级后显式开启。b2会追加迁移及任务枚举，旧worker不能混用；关闭新受理不抹去历史账本。计数只声明成功读取，不保证缓存保留，Local须注明实际执行进程。
- b1专项完成：根reactor标准入口，`CacheWarmupContractTests,DictionaryWarmupLoaderTests,CacheTaskPlannerTests,ModularityTests,ArchitectureRulesTests`，日志`backend/target/p08-s4c3b1.log`，输出`target/p08-s4c3b1/target`；**28/28，0跳过，BUILD SUCCESS（22:34，01:24）**。这是契约/加载器单元与结构检查，尚未做真实数据库预热或开放任务。进入b2执行协议后验证真实命中、回滚/接管、权限撤回和取消。
- b2已写入PREWARM、独立权限、预览冻结加载器声明、V11检查点，以及目录可用性/拒绝原因。每次加载和检查点重新验证最新IAM权限、租户安全代次、task fence、控制/数据版本及取消；加载器和缓存I/O不持任务数据库事务。每页成功重置尝试，丢失确认重读持久游标；同项执行进程变更明确失败，不把多个Local进程累计描述为全量变热。
- 每任务上限1000次检查点读取、从首次执行起120秒；区域声明只能更小。每页至多16项、按声明速率页后冷却（有界突发，不睡眠占worker），取消可在下一key前观察。正常缓存淘汰/None/限额回源意味着读取成功不能保证驻留；预热不发布失效确认，报告为NOT_APPLICABLE_PREWARM。旧非预热载荷保留兼容构造和nullable字段；启用PREWARM后不能混合旧worker，回退必须先升级读端兼容而非仅关开关。
- 首轮真实MySQL13306/Redis16379专项正在执行，日志`backend/target/p08-s4c3b2.log`、输出`target/p08-s4c3b2/target`，含Local/Redis真实字典预热、4页不耗尽重试、失读重放、取消/撤权/数据及策略变化、时长/总量/进程边界和HTTP，以及旧任务/目录/架构回归。运行期间复查补入字典预热专用3秒只读事务和同快照最多512字典项限制，普通options不改变；将对最新源码重编译验证，首轮不能代替该补强的证据。
- 最新非数据库轮`backend/target/p08-s4c3b2-unit.log`为32项、31通过/1失败：新增纯单元夹具缺少MyBatis lambda列元数据，断言未到达条数上限。测试显式初始化DictionaryItemEntity元数据后专项复验；没有降低生产配额或改用无界源查询。
- 失败专项`backend/target/p08-s4c3b2-unit-recheck.log` **3/3，0跳过，BUILD SUCCESS（22:47，57秒）**，实际检查3秒/只读/REPEATABLE_READ与513项拒绝。组合证据不能写作一次32项全部通过；其后新增加载器版本冻结、停用仍可取消和旧Target JSON可读测试，将纳入最新整合复验。
- 首轮整合 **70/70，0跳过，BUILD SUCCESS（22:50，08:55）**。真实Local与Redis各7项、原执行/受理/HTTP/目录及结构门禁全部通过；V11实际迁移成功，此后只追加迁移。最新回源限制及兼容测试另以`backend/target/p08-s4c3b2-final.log`重新编译整合，输出`target/p08-s4c3b2-final/target`，结果待记录；b2尚不标完成。

- 最新源码复验 **58/58，0跳过，BUILD SUCCESS（22:59，09:05）**：真实Local/Redis各7项、三种provider基础数据各5项、4项注册/兼容、3项字典加载器、结构和双语门禁通过。包含实际3秒/512项主动回源路径、加载器声明变化拒绝和旧JSON读取。S4c3b、S4c3与S4c完成；70和58含重叠，不相加为独立用例数。产物未部署，全部本轮Maven会话已结束；应用双进程/LB/Sentinel/Cluster仍待后续验收。

### S4d统一页面实施细分（完成）

1. **d1协议与交互状态**：在`frontend-next/src/features/monitor/`增量整理cache类型/API与请求状态，复用既有http/auth/RequestGate；Long版本、ID和累计量保持字符串，未知值不转0。维护同一预览ID与Idempotency-Key，提交超时先查原任务，禁止自动换键重发。测试范围包括未知结果、版本冲突、迟到响应及身份切换。
2. **d2四页签**：保留`/monitor/caches`菜单，目录按owner/group/scenario/kind/provider/status筛选，显式选中目标和服务端预览；概览说明共享条目与本进程计数，节点页面不把租约在线冒充可达；任务展示逻辑状态、逐项结果、历史确认/当前在线与预热实际进程；策略展示声明/部署/租户有效来源、上限与版本，编辑前后审计。保护/资源/运行时适配交付前保持明确能力边界，不能出现普通清空按钮。
3. **d3验收**：新版完整verify，Playwright模拟接口验证筛选交集、权限/拒绝、原键恢复、双语/明暗/窄屏/键盘；专用端口/独立schema运行真实后端任务与策略联调，旧前端保持原接口并执行兼容验证。截图/日志只写任务生成目录，不触碰用户运行页面和未提交修改。通过后同步README与前端规则，再进入S5。

d1完成：新增`features/monitor/cache.ts`类型/API与原键提交状态机，Long保持字符串，大整数计数用BigInt展示，未知值不转0；所有筛选按交集。响应丢失仅自动读回，显式重试先读取原任务，再复用原键；并发点击共享请求，身份变化丢弃迟到结果。`npm run test:unit -- src/features/monitor/__tests__/cache.test.ts` **6/6通过**，专项ESLint通过；首次全测试类型检查发现randomUUID默认值推断过窄，显式string参数后`npm run type-check:tests`通过，新增兼容Duration字符串/秒数测试再次6/6。尚未替换页面，进入d2。

d2实现：原CachesPage接四页签及Overview/Tasks/Policies组件；目录完整返回后按owner/group/scenario/kind/provider/status/关键词交集筛选，选择范围固定为ID并由服务端再次排除。预览展示目标/排除原因/预热预算；操作提交使用d1状态机。任务历史和在线分开、默认手动刷新，可选5秒有界刷新；策略展示代码/部署/有效值及来源，旧expectedVersion随草稿保留，未知/冲突只读回，停止重复保存。未改菜单/路由/旧后端接口。

- 模拟浏览器首轮7项中6通过：策略冲突草稿、离线历史确认、保护状态、四种语言/主题与390/1440布局、键盘/Axe通过；筛选用例因原生select缺精确可访问名失败。补aria-label后失败专项1/1通过，提交响应丢失仅一次POST也实际验证。首轮观察到Vite转发的ResizeObserver通知警告，未作为无运行错误证明；后续生产包需区分并检查。
- 完整`npm run verify`通过（日志`frontend-next/.e2e-runtime/p08-cache-verify.log`），含基线/目录清单/类型/测试类型/ESLint/双语/单元/构建/发布预算；尚不能替代真实API联调。
- d3真实联调启动：复用本任务13306 MySQL创建新的独立schema，后端18084、前端5195，使用S4c3b2-final JAR、预热显式开启。日志`frontend-next/.e2e-runtime/p08-cache-real.log`，不占用户8080/5174。验证真实字典预热、任务完成和节点确认、策略保存/恢复及匿名拒绝，结果待记录。
- 生产构建模拟API浏览器专项`SMA_CACHE_E2E_PRODUCTION=1 npx playwright test --config playwright.cache.config.ts` **7/7通过（24.7秒）**，四种语言/主题×两种宽度截图，Axe及键盘通过，pageerror为空；未复现开发Vite的ResizeObserver通知。完整verify的单元数为111/111。正在执行旧版标准验证，未改旧版源码。
- 旧版标准`tools/dev/verify-frontend.ps1`通过，构建＋55/55单元＋双语；日志`backend/target/p08-cache-legacy-frontend.log`。广义operations/states隔离生产回归7项、5通过/2超时，日志`frontend-next/.e2e-runtime/p08-cache-regression.log`：通知已读按钮、事件重试按钮被现有“我的随手栏”浮动入口拦截点击；未改非缓存产品页面掩盖问题。缓存提交改为独立用例，`--grep 'cache clear uses'` **1/1通过（4.4秒）**；原通知/事件用例保留，不能把此记录描述为全站E2E通过。
- 首轮真实联调未通过：PREWARM与CLEAR均已SUCCEEDED、节点CONFIRMED，但切策略后页面被重置为目录。原因是身份watch使用返回新数组的getter，在同身份auth/me对象刷新时误触发全页清理。改为三个独立标量watch源，仅用户/portal/锁状态真实变化才清理；追加模拟WebSocket SESSION_INVALID触发真实auth.load(false)的草稿保留回归。需要重编译/浏览器专项及新的隔离真实联调，不能以先前模拟7/7掩盖此问题。
- 身份刷新专项1/1通过；首次断言使用普通getByRole无法查询被模态框正确隐藏的后台tab，改为includeHidden后验证同一tab和草稿都保留，没有改变产品隐藏语义。最新`npm run verify`再次通过，日志`frontend-next/.e2e-runtime/p08-cache-verify-identity.log`。生产包8项和新独立schema真实联调（同时启动5193旧版）进行中，分别见`p08-cache-production-identity.log`与`p08-cache-real-recheck.log`。
- 最新生产浏览器 **8/8通过（27.4秒）**。截图复查发现原dark夹具只改auth/me.theme，仍被config/preferences中的light覆盖，故先前dark文件名不能作为实际深色证据。同步修改两处夹具并断言html.dark，深色专项 **2/2通过（10.5秒）**，真实深色390/1440截图及Axe重新检查。日志`p08-cache-production-dark.log`；产品主题实现未改。
- 第二轮真实联调中身份刷新修正已生效，预热/清理/确认及策略保存通过；在恢复策略断言失败：全局确认框退出动画期间编辑dialog仍是aria-hidden，测试将普通getByRole计数0误作保存已完成，立即读到了尚未恢复的运行覆盖。截图显示空标题退出中的alertdialog和disabled编辑表单，不能据此判断后端恢复失败。改为等待精确policy/reset POST成功回执，再检查includeHidden的编辑框确实卸载和权威状态；生产实现不改，需第三轮隔离联调闭环。前两轮owned schema/用户/后端已由runner清理。
- 第三轮真实浏览器 **1/1通过（26.2秒，测试21.6秒）**：新旧前端5195/5193、后端18084、独立MySQL schema；真实登录、字典预热进度、CLEAR与本节点持久确认、策略保存/恢复权威值、匿名401、旧版登录和原缓存列表均通过。日志`frontend-next/.e2e-runtime/p08-cache-real-final.log`；runner退出清理独立服务/schema/账号及密钥。生产页面最新verify和8项浏览器＋2项真实深色补充证据有效；新增E2E等待条件只改变测试。S4d/S4完成，非DATA适配继续S5，应用LB双实例与拓扑仍未验收。非缓存浮动随手栏遮挡两例保留为独立已知边界。

### S5接入门禁细化（S4已验收，S5a实施中）

- **S5a二开注册归属与边界**：当前仅CacheDescriptor验证owner/id字符串相符，未证明提供Bean属于该模块。增加启动校验（解代理后真实类所属admin/business模块、目录与加载器一致），业务不得伪装基座区域或管理权；架构检查禁止绕过common直接依赖Redis/Caffeine/Spring Cache。明确静态模块是可信代码，规则不是恶意插件沙箱；隔离样例验证合法注册、跨模块冒名、重复、用户私有预热及升级版本变化。
- **S5b非DATA目录适配**：规则表达式/流程运行对象、SQL统计、IP库/文件资源与认证/nonce/票据/凭据保护状态按真实生命周期登记；只提供所属模块授权后的元数据。规则在途计划只能清空闲项；SqlTelemetry reset不得改业务数据或数据库状态；IP库和上传文件不做普通缓存删除；保护状态无清理/暂停/预热入口。统计未知与真实零分开，绝不返回密钥/缓存值或原始文件位置。
- **S5c可执行适配与UI验收**：非DATA操作必须由所属模块适配器校验原业务权限/数据范围，节点本地动作标明实际进程，不冒充全节点。运行时副作用不套“数据库回滚即可撤销”的任务语义，操作结果须有明确不确定/重查边界；与S4任务/审计/权限展示衔接后分别验证在途保护、跨租户拒绝、旧SPI兼容和统一页面。每子项单独记录证据后继续，不以目录展示完成全部S5。

S5c执行细化待实现：节点本地适配器操作必须冻结目标进程和适配器版本，由该进程执行；跨节点命中不能静默改为当前节点。执行前短事务记录“即将执行”，原业务授权重建后在事务外调用适配器，再记录结果；已开始但确认丢失的项显示UNKNOWN，不让接管者自动重做。原节点退出则明确未执行/不确定，不伪造全节点成功。资源文件清除和保护状态不在该入口开放。与现有DATA同库原子APPLIED及预热幂等读取分别处理，并添加版本/旧载荷迁移门禁。

S5a当前实现：生产CacheRegistry装配验证提供Bean解代理后的真实模块包与ApplicationModule元数据，descriptor.owner须匹配；业务managePermission须属于业务自身前缀。预热加载器同样核验真实所属模块，不允许接管他模块区域。普通孤立存储契约测试保留不装配启动校验的内部构造器，生产工厂和Spring加载器构造器始终传入校验器；二开不能访问internal绕过。架构门禁禁止common/internal/cache以外代码直接依赖Redis/Lettuce/Jedis/Redisson/Caffeine/Spring Cache/JSR Cache。

- 独立JavaCompiler生成shop/other/missing业务类与模块元数据，在独立ClassLoader下验证合法/冒名/缺元数据/跨模块加载器/JDK代理；配置测试改用真实ConfigCacheCatalog，避免旧lambda夹具伪装config来源。日志`backend/target/p08-s5a.log`含配置绑定/实际Redis及架构，结果待记录。
- 实际宿主装配及任务/预热回归使用独立13306测试schema，输出`target/p08-s5a-integration/target`，日志`backend/target/p08-s5a-integration.log`。与不使用MySQL的单元轮并行，Redis使用独立namespace，不并发清同库。通过前不进入S5b实现。
- 首轮非数据库36项、35通过/1失败：归属、代理、独立业务编译、架构、Local/None配置均通过；旧Redis配置夹具仍用未设置State返回值的mock，触发现有持久状态不可用绕过缓存，第二次回源返回wrong。夹具显式返回State.initial后复验，不改生产故障降级语义。
- Redis配置复验 **5/5，0跳过，BUILD SUCCESS（23:53，01:56）**；追加独立编译合法ManagedCache业务与违规StringRedisTemplate业务的架构拒绝证明，专项`backend/target/p08-s5a-boundary.log` **2/2，0跳过，BUILD SUCCESS（23:54，01:33）**。与首轮有重叠，不累计为一次全量。实际宿主整合仍运行中。
- 已整理[二开缓存接入契约](cache-secondary-development.md)：真实归属、命名/权限、隔离、DTO、事务失效、跨库限制、预热预算、版本/旧worker边界与验证入口。明确可信静态Java规范不等于恶意代码沙箱，任意Map的缓存用途还需评审。
- S5a实际宿主整合 **39/39，0跳过，BUILD SUCCESS（00:00，10:36）**：严格生产装配、受理/预热/目录和模块/架构检查通过；独立新增架构负例另见2/2，不合并成一次全量。S5a完成，本轮无运行中的验证会话。进入S5b只读适配，再按S5c添加原生副作用协议。

### S5b非DATA只读适配（完成）

- 新公开CacheResourceAdapter/CacheResources仅观察；宿主验证真实归属/唯一性并盖采样进程与时间。DATA使用原观察接口，PROTECTED不接受适配器或变更能力；受限/失败/缺失观测保持-1未知，不吞成0、不泄漏异常路径。
- 注册Common SQL统计/请求账本/指纹密钥，Rule编译对象与流程计划，Audit IPv4/IPv6库，File持久存储，以及Auth会话/验证码/MFA/安全代次/失败预算、Message票据/连接、OpenAPI令牌/nonce/登录预算；原Identity Google公钥保留。新增相应模块common::cache公开依赖与中英元数据，不改认证状态、资源生命周期或历史迁移。
- Rule适配器委托原RuleOperationsService.resources，保留维护查看权限＋ALL范围和租户过滤；表达式64/计划32独立上限。SQL观察要求原monitor:sql:view且仅可信租户；IP库只读2个资源且不close；文件统计未知、不扫目录，明确是持久业务资产。该阶段运行时操作allowedOperations仍空，普通任务预览维持ADAPTER_REQUIRED，直到S5c原生协议实现。
- 后端非数据库专项 **34/34，0跳过，BUILD SUCCESS（00:09，01:31）**，日志`backend/target/p08-s5b-unit.log`，覆盖注册重复/保护适配拒绝、业务拒绝/故障未知、目录兼容、架构/模块/双语。实际MySQL宿主/HTTP/跨租户及原任务回归`backend/target/p08-s5b-integration.log`进行中。
- 前端目录增加资源provider筛选、对象计数与只读详情，展示生命周期、隔离、采样进程、资源上限/受保护数量及声明失效来源。完整verify及资源/保护浏览器专项分别见`frontend-next/.e2e-runtime/p08-resources-verify.log`、`p08-resources-browser.log`，结果待记录；非DATA仍未开放清理。
- 前端完整verify通过（111项单元、类型/Lint/双语/构建/发布预算），新增资源及保护状态浏览器 **2/2通过（8.7秒）**；生产包完整缓存页面9项再验中，日志`p08-resources-production.log`。规则资源上限文案补充“表达式64/计划32不可互借”，避免把合计96解释成统一配额。
- 生产包完整缓存浏览器 **9/9通过（28.0秒）**，含资源详情、保护禁变更、未知值、同身份刷新、中英文/实际明暗/390与1440/键盘及Axe。后端真实整合仍待结束，S5b尚未标完成。
- 实际宿主整合 **39/39，0跳过，BUILD SUCCESS（00:18，10:04）**，四类目录、保护/资源普通任务拒绝、真实规则ALL范围及租户隔离、HTTP双语生命周期/不泄漏内部资源和旧Monitor/任务接口通过。S5b完成，本轮验证会话均结束；源码仅规则上限说明在编译后补清“64/32不可互借”，不改变行为。下一项S5c原生运行时操作，S5整体仍未完成。

### S5c运行时原生操作（实施中）

沿用S4任务入口，首批仅rule.runtime的CLEAR和common.sql-statistics的RESET_STATS。RESOURCE文件/IP库及PROTECTED始终不可变。增加默认关闭的部署开关，必须所有读端/worker升级后启用；不理解新结果状态/载荷的旧程序不能与新任务共存或直接回退。

预览固定适配器版本与执行进程；提交可以跨节点，效果只由固定进程执行。目标不可达的未执行项有5分钟路由截止；不能偷换为另一节点。执行前短事务持久“已开始”，原业务权限/范围最新复验后在任务事务外调用适配器，再持久结果。开始标记存在而租约已过期或调用结果不确定时标UNKNOWN，接管不自动重做；取消不反向撤销在途或已完成效果。统计数字只代表当时从缓存索引移除/剩余对象，不是物理回收证据。DATA原子提交、预热幂等读取和原生副作用继续分离。

验证包含同节点执行、跨节点不误执行、到期未执行、开始后崩溃/丢确认不重做、撤权/取消、规则在途保留与跨租户、旧载荷读取和明确部署回退门禁。只追加Common V12（开工前已核对V1～V11），不改既有迁移。

S5c当前实现：公开适配器增加默认拒绝的授权/执行方法，默认关闭`sma.cache.tasks.resources.enabled`；任务Target保存进程/node/适配器版本，V12增加原生开始/完成/结果/路由截止与UNKNOWN状态约束。认领SQL筛掉其他进程未到期工作（含首个pending ordinal），避免16个异节点任务堵住后面的DATA任务；参数全部绑定，保留租户及行锁/fence。开始提交确认丢失不调用适配器；效果调用异常或过期接管保留UNKNOWN，完成提交确认丢失先读原结果。原生与DATA混合的节点报告为DATA_TARGETS_ONLY，不宣称所有目标已完成失效确认。

- 初轮无数据库专项因测试通配import造成RuleDefinition.Flow与JDK Flow类型冲突而编译失败，日志`backend/target/p08-s5c-unit.log`；改显式并发类型导入，生产代码未改。复验`p08-s5c-unit-recheck.log`含原生适配器在途保护、数据范围/跨租户、注册权限/进程/事务拒绝及结构检查。
- 真实MySQL专项`backend/target/p08-s5c-integration.log`已启动，含原生9项故障/路由/撤权/取消/混合任务/SQL统计测试及原DATA执行、预热、受理、目录、HTTP和门禁。结果待记录；未开放已部署环境的原生开关。

- 编译复验又发现并行P10测试DomainProbeTests使用Java不支持的复合var声明，仅拆为两条声明修复编译，不改租户生产行为。最终无数据库专项`backend/target/p08-s5c-unit-final.log` **36/36，0跳过，BUILD SUCCESS（00:38，01:03）**；早期编译失败的轮次没有执行迁移。
- 最终实际整合`backend/target/p08-s5c-integration-final.log`已在00:39执行Common V12，之后视为不可改写，修正只能追加迁移。原生故障专项9/9已通过，其余DATA/预热/HTTP回归仍运行，尚未标记S5c完成。
- 最新前端完整`npm run verify`通过（111单元、类型/Lint/双语/构建/发布预算），日志`frontend-next/.e2e-runtime/p08-native-verify.log`；生产包完整缓存页面 **10/10通过（33.6秒）**，日志`p08-native-production.log`，包括绑定进程预览、UNKNOWN不重试及不宣称全节点确认。新增真实SQL统计重置验收，待使用本阶段已验证JAR运行。
- 实际宿主整合 **65/65，0跳过，BUILD SUCCESS（00:45，07:49）**。已使用该轮JAR启动隔离新旧前端真实联调`frontend-next/.e2e-runtime/p08-native-real.log`，独立schema/账号/端口，不替换用户服务；部署原生开关只在本次验收进程开启。新增旧载荷/只读SPI兼容、适配版本改变和关闭开关拒绝受理专项`backend/target/p08-s5c-compat.log`并行验证，不使用数据库。
- 兼容补充 **10/10，0跳过，BUILD SUCCESS（00:47，01:44）**，包括无resource/warmup的历史JSON、旧只读SPI默认拒绝副作用、适配版本/关闭开关阻止首次执行但仍允许有权读取原回执；没有生产代码变化，真实浏览器继续使用65项整合的JAR。
- 独立JavaCompiler以旧版接口编译只读适配器和六参数Target调用者，再以parent-first类加载器连接新版宿主，二进制兼容 **1/1通过**。初轮发现编译器默认运行类路径注解处理器并生成构建元数据，补`-proc:none`限制到测试临时产物后复验 **1/1，BUILD SUCCESS（00:51，22.8秒）**，日志`backend/target/p08-s5c-binary-final.log`。
- 首轮真实浏览器已通过预热/清理/节点确认/策略生命周期，原生步骤等待不存在的行内“重置统计”按钮；当前产品入口是勾选行后点工具栏重置。修正测试为勾选SQL区域并断言预览仅1项，不修改产品交互；此轮不作为通过证据，需独立环境重跑。S5c仍未完成。

- 修正后的生产页面单区域选择专项 **1/1通过（4.6秒）**，测试文件ESLint通过。第二轮真实新旧前端＋独立后端/数据库联调 **1/1通过（34.4秒，测试28.9秒）**：绑定进程预览、仅SQL区域RESET_STATS、持久APPLIED及完成时间、NOT_APPLICABLE_RESOURCE展示均通过；原预热/清理/确认/策略/匿名401/旧前端同轮通过。日志`frontend-next/.e2e-runtime/p08-native-real-final.log`，独立环境`1794416198565`，端口18085/5191/5192。开发Vite再次产生ResizeObserver通知；生产包10项与新增选择专项未出现该问题，不宣称开发日志零通知。S5c/S5完成，进入M1a；本机多实例仍未验收，未部署。

### M1实施拆分（按子项验收）

| 子项 | 具体交付与边界 | 独立验收 |
| --- | --- | --- |
| M1a | Message自有MySQL一次性票据，摘要存储、短TTL、可信tenant/portal/session绑定、跨实例原子消费、每会话替换；现有连接仍归节点 | 两个独立服务对象共享真实数据库竞争消费，仅一次成功；过期/重发/跨租户/portal/撤销拒绝，真实WS原回归 |
| M1b | OpenAPI登录预算从进程Map改为共享数据库预占；匿名凭据引导预算不需要先假定租户，输入只存摘要，有界清理/容量，失败不能回滚或成功清除 | 两个独立调用者并发耗用同一预算，失败提交/过期窗口/未知应用/容量隔离；原机器认证与签名回归 |
| M1c | 会话自动锁屏和lastSeen更新改为受条件约束的最小字段更新，检查手动锁/解锁/退出竞争，避免旧实体全字段覆盖新安全状态 | 可控并发顺序、锁屏/解锁/强退/改密/单终端/验证码/TOTP回归 |
| M1d | 两个独立JVM共享专用数据库/密钥，A登录B访问、A取票B握手、两节点同票竞争、跨节点撤销/挑战一次消费 | 实际独立端口证据；暂用single模式测试互通，不放开multi启动门禁，不替代M3的LB/摘流/配置预检 |

每子项先实现并完成专项验证再继续下一项。票据属于PROTECTED，provider=none或清缓存均不得改变一次消费语义。旧内存票据无法迁移，新旧版本混合只能安排排空旧票据TTL并统一升级，不能承诺混跑跨节点取票可用。共享预算会收紧原“失败后计数、成功清零”行为，运行说明须列出窗口和配额；限流不是普通缓存，不允许Redis故障时回退本机计数。

当前只读审查确认SessionService自动锁屏会更新整个旧SessionEntity，SessionManager.lock/unlock也使用实体更新；M1c要覆盖过时lastSeen/锁屏状态竞争。这里是待修复证据，不代表已经交付加固或双实例验收。

后续范围补充审查（仍未实施）：

- M2：当前SocketEventListener直接推本节点连接；新版前端已有30秒REST摘要轮询和重连补查，可保留为丢广播恢复底线，仍须补跨节点实时信号。信号不携带正文/令牌，不能以事件全局单消费者代替每节点投递；会话撤销保留心跳和推送前权威校验。
- M2：SessionMaintenance/NonceMaintenance仍依赖本节点seen租户集合，需要改为可信TenantWorkQuery有界分页，清理幂等/加锁，不能清空未过期安全预算。Mail/Identity已有持久任务/可信租户分页，复用现有协议，不另建并行队列或自动重发UNKNOWN。
- M2：LocalStorage当前只读取`sma.file.directory`；多实例需显式共享存储声明和跨节点实际读写见证，不能仅因为路径字符串相同就认为磁盘相同。文件仍是持久业务资产，保持原上传任务和回滚清理边界。
- M3：现有目录仅采样处理HTTP请求的本进程，节点租约清单不包含远端资源统计。按节点查看、远端目标原生操作及节点统计应在集群管理验收补齐或明确受支持边界，不能把本地结果累加为全局值，也不能用LB随机命中当作选节点功能。该项与方案第8节要求一起纳入最终审计。
- M3：当前默认雪花ID没有显式集群唯一节点分配保证；多实例开启前必须实现可靠分配/租约或等价无冲突方案，测试重启、并发和时钟异常。密钥环/缓存namespace/模块与规则版本、文件见证、代理信任、readiness和摘流必须一并验证；现有multi启动拒绝保持。

### M1a共享一次性票据（完成）

Message新增V3（开工时已核对只有V1/V2），自有租户guard和票据表；不跨Auth表、不增加免租户查询。数据库只存随机票据摘要，消费使用带tenant/portal/有效期/摘要条件的原子删除；独立提交后返回，调用者外层回滚不能恢复已消费票据。首次握手仍需可信入口，成功后和后续推送仍检查SessionQuery权威会话。

同会话重发在租户guard锁下替换；maxConnections同时作为共享租户内待消费票据上限（跨portal），本节点连接上限仍独立生效。清理每次最多200个过期项；限额拒绝时仍提交本次清理，避免调低容量后无法释放旧项。跨租户/portal尝试不会删除合法票据；登录会话/令牌不迁移到Redis。目录中票据scope改TENANT_PORTAL，连接仍NODE。

验证`backend/target/p08-m1a.log`：两个独立服务对象＋真实MySQL并发消费、重发/过期/撤销/容量/外层回滚，以及真实SocketTests、原请求治理HTTP/缓存目录/模块/架构/i18n专项，结果待记录。不能以此代替M1d独立JVM、M2跨节点推送或M3无粘性LB；下一子项未开始实现。

代码审查补充：Auth的SessionValidity自己开启权威只读事务，票据事务若跨该调用持锁/连接，会放大连接池压力。改为签发前检查会话、消费独立提交后再校验权威会话；权威查询失败不返回grant，也不恢复已消费票据。增加事务边界/权威异常回归。首轮m1a已编译旧实现，完成后须对最新票据与真实Socket专项复验，不能把首轮直接作为最终版本证据。

首轮 **40/40，0跳过，BUILD SUCCESS**，包括真实WS消息/强退及HTTP治理；Message V3已于01:13:35在专用数据库执行，之后只追加迁移。最新事务边界/权威故障版本开始复验`backend/target/p08-m1a-final.log`，继续使用同一独立测试库但与首轮串行，包含票据6项、真实Socket、Auth/Message请求治理及模块/架构。最终结果待记录，M1a未标完成。

最新版本复验31项、30通过/1测试夹具错误：新增权威异常用例在恢复mock时使用`when(mock.active()).thenReturn`，调用了之前配置的抛异常行为；事务边界断言和消费后权威异常均已执行，失败在恢复夹具而非生产调用。改`doReturn(...).when(...)`，生产代码未改；只重跑该项`backend/target/p08-m1a-authority-recheck.log`，其余30项真实Socket/HTTP/架构结果保留，不伪称首轮31项全绿。复验通过前不进入M1b。

权威故障专项复验 **1/1通过，0跳过，BUILD SUCCESS（01:34，08:50）**；最新生产实现的30项既有通过＋失败夹具修正后的1项组成分轮证据，不合并为一次31项全绿。测试证明消费已提交后Auth失败不会恢复票据，票据自己的事务不跨权威查询。M1a完成，M1整体仍未完成；进入M1b。最新JAR位于`backend/target/p08-m1a-authority-recheck/target/`，未替换运行服务。

### M1b共享OpenAPI登录预算（完成）

固定窗口预占：来源60次/5分钟、来源＋AppId10次/5分钟；成功、错误凭据、签名失败和调用者回滚均不退还。平台最多10000个计数项，每次预占清理至多200个过期项；容量满允许已有主体继续按原窗口受限使用，未知主体拒绝。匿名凭据引导尚无可信租户，因此表仅保存平台技术预算和加密摘要密钥，正常应用/令牌/nonce表仍按原租户规则。

使用独立随机HMAC密钥，经OpenAPI部署主密钥加密持久保存；载入/加解密在预算事务外，每个预算在独立短事务中锁平台guard、检查密钥代次、预占并提交。跨节点使用同一数据库/主密钥，配置错误不降级本机。原子预算、匿名无租户、并发、外层回滚、数据库到期、10000项容量/有界清理、密文故障及原机器认证/签名为验收点。同步更新原生接口安全清单及运行说明；完成后再进入M1c。

- 已追加OpenAPI V4及共享计数/密钥guard，普通应用/令牌/nonce不变；目录将机器登录预算标为PLATFORM保护状态，并登记进程内部署加密密钥为NODE保护状态。密钥损坏、代次变化，或密钥行丢失但已有计数时均拒绝，不能生成新key绕过预算。
- 无数据库边界首轮`p08-m1b-boundary.log`被并行IdentityCredentialOperationTests的void/Supplier编译问题阻断；随后该测试已由现有工作修正，本任务未修改该文件。整合轮已成功编译，使用专用库`p08-m1b-integration.log`；边界重跑`p08-m1b-boundary-final.log`。
- 新平台表门禁首次遗漏两个既有不继承BaseEntity的Extension节点检查点/制品引用实体而失败。已逐项核对实体注释和P05规则：只包含平台修订/制品引用计数，不含业务载荷且不授予执行资格；将这两个既有明确例外加入精确名单，不放宽到整个模块。数据库专项继续运行，失败门禁另行复验；尚未标记M1b完成。

- 边界重跑28项、27通过/1仍使用修正前已编译的平台表名单；接口清单审查已成功，基线前后对比仅`POST /open/token`的预算描述发生预期变化。随后不带审查更新标志的普通门禁`p08-m1b-boundary-verified.log` **2/2、0跳过，BUILD SUCCESS（01:48，01:19）**，修正名单和接口基线均通过，没有将审查标志加入标准脚本。
- 真实数据库整合46项、45通过/1为同一已修正名单断言。**OpenLoginBudgetTests 8/8、OpenAuthenticationTests 6/6、SignatureProtocolTests 2/2及目录/既有结构门禁通过**，日志`p08-m1b-integration.log`。包括32并发仅10个配额成功、来源总限额、外层回滚不退款、匿名无默认租户、10000行容量/每次200过期清理、密文损坏/密钥丢失/代次变化拒绝。结合上述2项普通门禁复验构成分轮闭环，不宣传整合轮46项一次全绿。
- OpenAPI V4已于01:48:43执行，之后只追加迁移。M1b完成，进入M1c；独立JVM共享主密钥/预算与LB仍待M1d/M3，尚未部署。

### M1c会话并发更新（完成）

Auth自己的SessionMapper仅更新锁屏/活跃时间字段，不回写旧会话整体；自动锁屏绑定观察到的lastSeen并以数据库时间判断空闲，活跃时间只单调前进。手动解锁在既有账户安全锁之后锁会话行，保留密码/租户/安全代次校验，不修改认证证据或令牌。验收覆盖旧自动锁屏与新解锁交错、活跃更新与锁屏/退出竞争、未来时间不回退，以及原锁屏/单终端/改密/TOTP/验证码和Socket回归。

当前实现由Auth内部SessionActivityService统一处理条件更新，必要更新后重读会话并复验权威有效性；SessionManager手动锁/解锁使用最小字段SQL，解锁按账户安全锁→会话锁顺序。数据库时间读取会增加未锁会话鉴权的一次轻量查询，性能边界需在S6测量，不以节点本地时钟代替这个一致性判断。

专项`backend/target/p08-m1c.log`正在运行，包含SessionConcurrencyTests五种可控数据库交错（明确验证自动锁屏参数和拦截点实际命中）及原AuthFlow/Session/密码/TOTP/Captcha/Socket/请求治理与平台表/接口清单门禁。没有新增Auth迁移，保留现有schema和token形状；未通过前不进入M1d。

真实整合61项、60通过/1为并行P09新增五个邮箱HTTP入口时的安全清单快照差异；SessionConcurrencyTests **5/5**，AuthFlow、锁屏/强退、密码、TOTP **11/11**、验证码、票据 **6/6**、真实WS及模块/SQL/平台表检查均通过。P09已按其专项记录完成新接口审查与基线更新，本任务没有替其放宽门禁。普通安全清单复验`backend/target/p08-m1c-policy-check.log` **1/1，0跳过，BUILD SUCCESS（02:19，01:11）**。以分轮证据闭环，M1c完成，不将61项首轮描述为全绿。

### M1d前置调整：可靠主键分配（完成）

两套真实JVM将同时签发票据、预算及业务记录，原默认雪花算法的进程/机器派生编号没有严格唯一保证。因此把M3原有ID任务前移为M1d0，再进行M1d实际互通；M2/M3/S6其他范围保留。方案与迁移/回退边界见[主键分配补充](proposals/P08-id-allocation.md)。该前置未完成前不启动双实例写入验收，不以低碰撞概率当作已支持。

当前实现：Common V13初始化单一号段状态，按本库有前导索引的数值主键最大值设置下界，空库保留起点2^52；历史ID和业务载荷不修改。每次独立事务领取1024个ID，领取确认丢失丢弃整段，业务回滚不退款；实体assign_id、IdWorker和新公开IdentifierAllocation统一绑定生成器。短连接继承Hikari主库认证/TLS参数，不占额外业务池名额，禁止自动重连，网络/SQL/等待都有界。平台状态登记PROTECTED及精确表名单。

已把13个服务的默认“最近”排序由单独ID倒序改为createdAt＋ID，待完整回归；自定义排序、权限和过滤保留。固定种子应位于保留起点以下；引入高位历史ID须停写并按分配下界核对流程处理，不能在活跃号段中插入猜测ID。

先单独运行GlobalIdMigrationIT（不启动应用、使用已有受保护的专用测试库），验证V12旧结构＋高/低ID夹具、空库、16384个并发分配、重建、业务回滚、提交确认丢失和耗尽；再进行宿主实际装配与根标准回归。结果待记录，V13尚未记录成功执行。

迁移/结构专项 **29/29，0跳过，BUILD SUCCESS（02:45，02:19）**，日志`backend/target/p08-m1d0-migration.log`。Common V13已分别对旧结构和空库成功执行，之后只追加迁移；旧8×10^18和小ID及原载荷保留，16384个并发分配无重复，未知提交整段丢弃、事务回滚不退款、重建及耗尽拒绝通过。此轮没有启动完整宿主；下一轮验证MyBatis、IdWorker、公开API实际绑定与默认最近排序，再跑完整标准回归。

完整宿主专项 **47/47，0跳过，BUILD SUCCESS（02:57，05:01）**，日志`backend/target/p08-m1d0-binding.log`。实际MyBatis实体、IdWorker、IdentifierAllocation三入口绑定，业务回滚不复用ID，最近文件按createdAt而非ID顺序，缓存受理/共享预算/会话并发及结构检查通过。开始根标准完整回归`backend/target/p08-m1d0-full.log`，使用专用13306测试库及16379任务Redis；通过前M1d0不标完成。

根标准完整回归结束为 **BUILD FAILURE**：backend 888项，882通过、1错误、5条件跳过；唯一错误是并行P10的`SharedConfigurationTests.hostReinitializationPreservesEditsAndDefaultChangesWhileContractDriftFails`在Web测试线程调用仅允许宿主初始化的入口。当前P10源码已改为既有`host(...)`线程夹具，本任务不放宽初始化授权。跳过为`DataSourceIdentityMysqlTests`、`SharedConfigurationSchemaUpgradeTests`、`BrandMigrationTests`、`DomainSchemaUpgradeTests`、`LegacyBrandSchemaUpgradeTests`，未提供它们各自独立的P10数据库环境；发布安全模块另跳过`SecurityMysqlHttpsTests`，不能宣称这些环境通过。

完整轮编译后追加的Hikari catalog/schema/connectionInitSql拒绝保护，独立连接单元 **2/2通过**（`p08-m1d0-connection-guard.log`）。随后在同一专用测试库串行启动`p08-m1d0-recheck.log`，仅复验修正的P10方法＋`IdentifierBindingTests`实际主库装配；输出`target/p08-m1d0-recheck/target`。最终结果与打包成功前M1d0仍保持实施中，不把完整失败轮改写为全绿。

M1d验收入口新增`frontend-next/scripts/run-cluster-e2e.mjs`：独立schema/账号、两个真实Java21进程、共享部署密钥和Redis命名空间、显式不同node ID；先A完成迁移再B启动。不改现有单实例runner，不使用用户服务或数据库，不记录密码/令牌/TOTP材料。覆盖共享登录/锁屏/授权撤销、并发写入ID、Redis偏好失效与清缓存后会话、验证码/MFA/WS票据竞争、共享机器预算、单终端和节点停机重启。此阶段仍使用single模式，只证明互通协议，不替代M3无粘性LB或M2跨节点事件推送。

当前源码复验 **4/4通过，0跳过，BUILD SUCCESS**：IdentifierBindingTests三项＋修正的P10初始化夹具一项。主库默认Hikari装配、实体/IdWorker/公开API统一分配、回滚及时间排序仍通过，保护不接受catalog/schema/init-SQL覆盖。结合29项迁移、47项宿主、2项连接保护和完整轮其余已通过项构成分轮证据；完整轮失败和六项跨模块环境跳过保留。M1d0完成，使用`backend/target/p08-m1d0-recheck/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar`启动M1d实际双进程验收；不修改用户正在运行的服务。

### M1d真实双进程（实施中）

首轮`frontend-next/.e2e-runtime/p08-cluster-m1d.log`，独立目录`cluster-1794426161071`：A登录/B访问、A锁屏/B拒绝业务访问/B解锁/A恢复通过。八个隔离账号的并发登录成功，但紧接八个并发偏好写入中出现HTTP503，停止且不自动重试；两个子进程和数据库已清理。首轮未完成其余断言，不作为M1d完成证据；初版脚本未记录稳定错误key，不能仅凭503断言具体限额来源。

功能互通轮改为每批两项、两个节点同时写入，共八个不同用户，以检验跨进程主键与事务；八个并发登录保留。没有修改生产预算或超时，原八并发偏好写入的503进入S6负载矩阵待定位。新脚本加入稳定错误key、脚本快照/hash、清理状态，补强A强退/B HTTP立即拒绝及WS心跳时限关闭、真实Redis命中计数断言；必须完整重跑新脚本，不把首轮已加载旧脚本当作新增断言证据。

只读定位发现既有`RequestExecutionService`在每个租户的LOCAL请求受理短事务中使用容量锁`FOR UPDATE NOWAIT`，因此独立用户的同时写入也可能在业务执行前返回`common.request.unavailable`，不能将低并发成功误写成并发容量通过。后续功能版将并发ID分配断言绑定到八个真实登录与八个原生票据写入，偏好夹具顺序建立；治理受理容量的负载行为单独保留待验证，不通过提高或移除锁来隐藏。第二轮已启动的脚本仍是两偏好并发版本，其证据按实际脚本快照记录。

### M2后续任务划分（待M1d通过后实施）

| 子项 | 实施内容 | 验收 |
| --- | --- | --- |
| M2a | Message持久化最小通知信号；每个节点独立读取并推给本节点可信连接；保留REST补查 | 两独立接收者均收到、重复/合并/中断恢复、跨tenant/portal/user隔离、回滚、容量和清理 |
| M2b | Auth/OpenAPI过期维护改用可信TenantWorkQuery分页，包含停用租户，去掉seen集合依赖 | 没有访问过的租户仍清理、逐批有界、并发幂等、未到期状态保留、目录失败不伪造默认租户 |
| M2c | 既有本地文件适配补明确共享部署契约和读写见证，保持文件上传原生任务与回滚边界 | A上传/B实际读字节、权限/租户拒绝、错误挂载见证失败；与P09存储配置不建平行体系 |
| M2d | 在实际双JVM脚本补A事件/B连接、广播故障后REST恢复及文件互通 | 当前JAR、隔离端口/库/存储的真实证据，再进入M3 |

消息信号是可合并的刷新提示，不承担正文或消息送达回执；正文/未读以Message持久数据及原授权查询为准。方向为按可信收件人＋portal＋类型保存有限的最新版本/对象标识，节点轮询各自已认证连接的范围，不使用全局竞争消费者或ID高水位。节点重新建立连接时补查REST；中断超过信号保留期也通过REST恢复。会话信号只触发权威会话复验，不能凭旧信号授予访问。持久信号登记受保护，普通缓存清理不能删除；细化实现时须覆盖容量、清理和旧客户端兼容。

第二轮`p08-cluster-m1d-recheck.log`（`cluster-1794426570160`）确认两偏好并发仍返回`common.request.unavailable`，八个跨节点并发登录通过。进程/数据库/密钥清理完成，未重发任何写入。由于两个独立用户的普通请求已受影响，将该共享受理问题提前到M1d闭环，不以缩小并发验收规避。

修复仅调整Common受理容量短事务的容量锁为`FOR UPDATE`，允许在已有3秒事务/SQL期限内等待；业务资源冲突仍为NOWAIT，内存并发预算、持久容量、同键回执及UNKNOWN不重试规则不变。新增RequestCapacityContentionTests，用另一个真实事务确定性持有容量锁：短暂持有后成功且重放不重做，持续持有在既有期限拒绝且业务与回执均未写入。先回归该项＋原RequestExecution/Admission，再用新JAR恢复原八并发偏好写入的双JVM验收；验证未通过前保持M1d实施中。

容量修复专项 **42/42通过，0跳过，BUILD SUCCESS（04:06，05:50）**，日志`backend/target/p08-m1d-capacity.log`。新可控争用2项、原RequestExecution 13项、RequestAdmission与真实请求治理HTTP，以及模块/架构/SQL门禁通过；持续占用按原期限拒绝且无业务写入，短占用后只执行一次并正确重放。没有新增迁移或放宽业务资源冲突。使用本轮JAR恢复八并发偏好写入，同时验证八并发票据及全部互通场景；实际双进程结果仍待记录。

第三轮`p08-cluster-m1d-capacity-fixed.log`（`cluster-1794427671139`）已通过原八并发偏好写入＋八并发登录/票据、跨表ID唯一、两节点锁屏/解锁、权限即时撤销、实际Redis命中/更新失效/清缓存不退出、验证码一次消费。随后脚本误用未安装的Playwright bundled Chromium而停止，未执行WS及后续MFA/机器预算/重启断言。改为与现有`playwright.real.config.ts`一致的已安装Chrome选择，并将浏览器启动放到建库前预检；不改产品代码。此轮仍为失败，保留已通过步骤，待完整重跑。

第四轮完整通过（`p08-cluster-m1d-browser-fixed.log`，`cluster-1794428011527/result.json`）：**14组真实双JVM断言通过，退出码0，passed=true，cleanupComplete=true**。覆盖A登录/B访问、共享锁屏解锁、八并发登录、八并发票据与偏好写入及跨表ID唯一、权限即时撤销、Redis真实命中与失效/清缓存不退出、验证码竞争、Chrome原生WS的A票B握手/重放/同票竞抢、A强退/B HTTP立即拒绝且WS心跳时限关闭、单终端同一胜者、TOTP跨节点绑定和一次性MFA、共享OpenAPI十次预算及既有机器会话、跨节点退出、A停机/B继续/A重启保持原会话。临时进程/库/账号/密钥已清理，脚本与JAR哈希保存于结果文件，不保存凭据。

M1/M1d完成，进入M2a。该轮是两个独立端口的single模式互通，未使用LB，不等同跨节点事件广播、共享文件或完整multi部署资格；M3门禁保持关闭。后续S6仍要测量容量锁等待、M1c数据库时间查询开销、Redis故障及HA拓扑，不能把上述八并发功能测试写成吞吐承诺。

### M2a跨节点最小提示（完成，双JVM广播另在M2d验收）

Message追加V4（开工核对仅V1/V2/V3）：按租户/portal/收件人/类型合并最新提示，保存对象ID、版本、过期时间及受限会话原因，不存正文/令牌；每租户guard保护容量，默认50000条、保留1小时，单批SQL最多500行，单事件沿用既有最多10000收件人的上限。后台每次物理删除最多200条过期提示；普通缓存治理将其标PROTECTED，不允许清理。框架事件成功提交提示后，各节点分别观察自己的已认证连接，无全局竞争认领或基于ID的提交高水位。

SocketSignalProcessor使用独立有界单工作线程，每轮只查询一个tenant/portal的至多500用户，避免占用Spring调度线程和在数据库事务内做WS I/O；按当前连接范围保留本进程观察版本，断开清理，记录行ID＋版本以识别删除重建。相同提示可能合并或重复，客户端仍用原消息类型触发授权REST刷新；新连接本就补查，原30秒轮询保留。会话提示只重新核验当前会话，不能凭提示关闭仍有效的会话或授予访问。低频清理用可信TenantWorkQuery，包含已停用租户且不创建无用guard。

验收先执行独立观察器/断线恢复/新行与新版本/租户恢复/HTTP拒绝单元和结构门禁，再执行真实MySQL的隔离、回滚、共享容量、并发写入、200条清理及原真实Socket/Message HTTP回归。最后M2d还需两JVM的A事件/B接收证明；本阶段代码未验证前不宣称广播已经交付。Message V4尚未记录实际执行，执行成功后不可改写。

首轮无数据库专项 **30/30通过，0跳过，BUILD SUCCESS（04:37，01:36）**，日志`backend/target/p08-m2a-unit.log`。两个独立节点观察同一提示、版本/删除重建/重连、失败不提前确认、可信租户分页和HTTP拒绝四项，以及模块/架构/i18n/缓存/SQL门禁通过。随后运行真实MySQL存储、原消息和Socket回归；该阶段尚未完成。

真实整合 **35/35通过，0跳过，BUILD SUCCESS（04:49，11:40）**，日志`backend/target/p08-m2a-integration.log`。存储五项覆盖租户/portal/用户隔离、独立读取、版本合并、回滚与非worker拒绝、共享容量和并发、删除重建及200条物理清理边界；原票据6项、公告/私信/待办/消息HTTP、真实WS通知与强退、缓存目录及平台表/接口普通门禁通过。**Message V4于04:48:23成功执行，之后仅追加迁移**。M2a完成，进入M2b；独立JVM的A事件/B接收及丢广播后补查仍按M2d验证，不将单进程真实WS测试扩大为双节点证据。

### M2b可信目录维护（完成）

Auth会话维护与OpenAPI nonce维护移除seen集合，旧内部seen()只保留无状态兼容入口。通过TenantWorkQuery分页发现所有租户（包含停用者），在ActorContext.withinJob中恢复可信范围；目录失败不回退租户1。各有单个有界后台线程，默认每10秒取8个租户，在租户之间检查2秒调度预算，避免清理阻塞消息心跳的Spring调度线程；单事务仍分别限制5秒/3秒。

会话和待绑定TOTP过期判定改用数据库时间，单次200条，锁定行SKIP LOCKED；加密轮换仍最多100条并保留旧密钥要求。nonce删除使用数据库时间＋每次200条，原来的无界DELETE移除；不会退款未过期安全预算或nonce。测试配置关闭自动维护并显式驱动，不改变生产缺省开启。没有新迁移。

验收：独立目录分页/停用租户/目录故障/HTTP拒绝单元；真实数据库的未访问租户发现、锁行跳过/重复维护、nonce跨节点并发和200条上限，以及原会话/密码/TOTP/机器签名回归。结果待记录，不进入M2c。

无数据库专项 **25/25通过，0跳过，BUILD SUCCESS（04:59，01:48）**，日志`backend/target/p08-m2b-unit.log`。两类维护的四项目录/停用租户/页尾回绕/故障不回退/HTTP拒绝，以及模块/架构/SQL检查通过。开始真实MySQL专项；自动维护在测试profile关闭，仅测试明确驱动，不将单位目录测试等同数据库清理或生产调度验收。

真实数据库及认证回归 **42/42通过，0跳过，BUILD SUCCESS（05:09，09:07）**，日志`backend/target/p08-m2b-integration.log`。新会话清理2项、nonce清理2项，原会话维护、AuthFlow、密码7项、会话并发5项、TOTP11项、真实WS、OpenAPI认证/签名和普通接口门禁通过。证明未访问/停用租户也被发现，锁定行跳过后可继续清理，两个独立nonce worker总共只删203条过期记录，每次不超过200，未过期及其他租户保留。M2b完成，进入M2c；没有新增迁移。

### M2c共享文件契约（完成，集群就绪另见M3）

细化方案见[共享文件见证](proposals/P08-shared-file-witness.md)。已核对P09正在实现的LocalStorageLocation/LocalTransferStorage和原FileService：本项仅在Common增加可信启动见证SPI、File增加共享声明与小型见证适配，沿用既有根目录/别名，不改上传、分片、UNKNOWN或扫描协议。先验收同目录互证/不同目录拒绝、边界/锁/链接/超时，再于M2d实际A上传/B下载；M3才据受信集群证据决定multi就绪。

当前实现新增`common :: deployment`的SharedStorageWitness，File适配既有legacy根及managed别名；默认不声明共享，未知共享资源拒绝。每个见证固定部署/资源/node/process及32字节挑战，排他创建、文件锁、无跟随链接、长度/摘要校验和成功前刷盘；相同身份仅核对，不覆盖未知内容，调用者只取得摘要。缺失根目录不自动创建，只有根下专用探针目录可创建；HTTP/认证调用和数据库事务中调用均拒绝。最多两个I/O worker、无排队、默认2秒调用截止，超时不无限新建线程；只清理本适配器已知且仍匹配的单个见证，不删目录或peer文件。目录登记PROTECTED。

开始六项实际文件专项及结构检查：双适配器互证/peer清理拒绝、不同或缺失根、锁定/损坏/不可覆盖、宿主/事务边界、超时与饱和不产生迟到写、Windows junction（其他系统symlink）不写外部目录。尚未执行集群就绪或多主机挂载验收。

文件级及结构专项 **30/30通过，0跳过，BUILD SUCCESS（05:26，01:17）**，日志`backend/target/p08-m2c-unit.log`。六项实际文件测试均通过，包括本机真实junction拒绝且外部目录未写入；无跳过。两适配器共享同一临时目录验证通过，不同/缺失目录、锁定/损坏、覆盖、peer退役、匿名HTTP/认证/事务调用拒绝及取消后无迟到写通过。随后验证实际宿主装配与旧文件上传、回滚、头像/品牌及缓存目录；M2c仍未完成。

实际宿主回归 **23/23通过，0跳过，BUILD SUCCESS（05:35，05:49）**，日志`backend/target/p08-m2c-integration.log`。旧文件读写、原生上传回执7项/HTTP2项、确认回滚清理、个人资料头像、品牌发布、缓存目录及普通安全清单通过；新SPI和配置Bean装配成功，不创建未触发的探针目录。M2c完成，无新增迁移；尚未接M3集群就绪判定，也未做生产多主机挂载验收。

### M2d真实双实例联调（完成，本机）

扩展既有隔离runner，在原14组M1互通后增加：同一收件人分别连接A/B，A发私信两个连接都收到；单独关闭B提示分发，A仍收到、B不收到但REST可读，恢复B后读取持久最新提示及完整未读；A经原上传协议写入，B下载字节一致且另一已认证用户被拒绝。复用原授权/回执，不实现新消息或上传旁路。客户端补充断连且无法取票期间30秒REST轮询的专项。仍用single模式测试协议，不代表M3配置一致性或无粘性LB已通过。

客户端消息store专项 **6/6通过**，包含新增“断连且取票不可用时仍每30秒读取授权REST状态”；脚本语法和对应ESLint通过。真实runner设置`SMA_CLUSTER_M2=1`，使用`p08-m2c-integration/target`已验证JAR；新增显式共享资源声明/已有managed别名，记录每次启动和退出的进程证据。关闭B分发是明确的配置故障注入，不称为网络分区；同机目录共享不称为多主机挂载。环境初始化截止放宽到15分钟以覆盖本机慢DDL，功能断言和业务超时不放宽。结果待记录。

按总排期继续（基线提交ab8f46a）：上一轮`p08-cluster-m2d.log`已结束，退出1，`cluster-1794433532014/result.json`明确passed=false、cleanupComplete=true，未完成任何业务断言。原因是P10内置参数已经通过定义/平台默认/租户覆盖解析，旧SQL假定config_parameter必有一行而失败；不改生产参数初始化或分层规则。runner改为先用真实验证码接口生成挑战（只在本轮隔离库固定该条答案），正常登录后用`/api/config/effective-parameters/{key}/override`及双版本设置验证码/单终端验收参数，并在B读回；不再直写配置表。

当前提交工作区直接执行根标准入口，输出`target/p08-m2d-current/target`、日志`backend/target/p08-m2d-current.log`，验证有效参数HTTP、共享提示/真实WS、原文件上传HTTP、缓存目录、安全普通清单及模块门禁，再使用该轮JAR完整重跑M2d。遵守总排期失败不前进，M3仍待实施；M2d完成时同时更新总排期与本文，P08整体交付仍需X5联合验收。

本轮首个根构建在extension-security测试编译阶段失败：并行ReleaseWorkflowTests同方法两处pending重名。现有并行工作已将上传变量改为pendingUpload，但部署PREPARING断言也误跟随为上传变量；仅将该断言指回部署pending，不改生产实现。复验日志`p08-m2d-current-recheck.log`使用独立输出，增加`ReleaseWorkflowTests#singleUserUploadValidationPrepareAndActivationRunRealJar`验证该处修正。前轮未启动后端数据库测试，不能记录为通过。runner及新增参数API路径语法/ESLint通过。

插件该方法复验1/1通过，当前消息store复验6/6通过。数据库轮编译期间X3预算登记新增BUDGET策略行，普通清单快照因此失败，未开启自动审查接受。X3已按其进度完成显式登记审查后，本任务另用`p08-m2d-policy-recheck.log`复跑普通SecurityEndpointInventoryTests，**1/1、0跳过、BUILD SUCCESS（07:21，01:49）**；首轮失败保留，数据库轮其余测试仍待完成。

当前数据库整合结束：backend 19项中18通过、1为上述旧清单快照失败，0跳过；有效参数HTTP4、原文件上传HTTP2、共享提示存储5、真实WS1、模块2、缓存目录4均通过。结合普通清单1/1复验构成分轮证据，不把19项首轮改称全绿。两轮编译class逐文件比较：P08相关Common/Auth/Message/File/参数代码一致，差异仅并行DefinedDictionaryService及其新读实现；双实例使用普通清单轮生成的当前JAR，SHA256由runner记录。恢复完整`SMA_CLUSTER_M2=1`验收，等待真实消息与文件互通结果。

`p08-cluster-m2d-parameters.log`（cluster-1794439614126）在A迁移完成后的Redis初始化失败，Lettuce明确报告200ms连接初始化超时；没有执行认证/消息断言。Redis16379进程仍运行，独立PING=PONG；不重启该Redis、不修改默认200ms。runner增加显式`SMA_CLUSTER_REDIS_TIMEOUT`部署覆盖（默认仍200ms）并在结果文件记录，下一轮本机功能验证使用受支持1s。该范围不能作为200ms默认或生产容量通过证据；S6仍需按实际预算/负载验证连接与故障恢复。

最终真实联调`p08-cluster-m2d-redis1s.log` **17组全部通过，退出0**；`cluster-1794440238625/result.json`记录passed=true、cleanupComplete=true、m2Included=true、redisTimeout=1s及实际各次进程启停/JAR和脚本哈希。新参数API双节点读回、原14组认证/Redis/并发/重启场景继续通过；新增A事件被A/B两个真实Chrome WS分别接收、B分发关闭期间A可推/B无推但REST未读为2、B恢复后读取持久最新提示，A原生上传/B字节一致且已认证无权限用户403/404拒绝均通过。所有本轮进程/库/账号/临时密钥已清理，没有修改用户服务。

M2完成并与总排期同步，进入M3。此轮仅同机两个独立JVM和共享本地目录，分发故障为配置注入；不扩大为网络分区、生产多主机共享挂载、200ms默认预算或无粘性LB验收。P08整体交付还受X5联合验收约束，S6未完成。

### M3实施拆分（依赖顺序）

| 子项 | 内容 | 验收 | 状态 |
| --- | --- | --- | --- |
| M3a1 | 缓存管理HTTP选定node＋process；错路由/旧进程明确拒绝，不转发任意地址 | 选择协议、鉴权、旧进程/错节点、请求副作用前拒绝；原HTTP兼容 | 完成（27专项＋HTTP分轮30通过/1环境跳过） |
| M3a2 | 缓存管理页面节点选择，目录/策略/预览绑定所选进程，任务回执仍按原持久协议 | 切换/迟到响应/未决操作、双语/浏览器、实际两个端口选节点 | 完成（179前端单元＋16生产浏览器＋19组真实双JVM） |
| M3b | 部署身份与配置/密钥一致性、共享文件双向见证、受控迁移入口 | 不一致/缺钥/缺挂载/重复节点拒绝，迁移不启动任意业务worker | 完成（41当前专项＋40组真实双JVM，同主机范围） |
| M3c | readiness/liveness、优雅摘流、可信代理、LB示例与受控滚动/回退 | 健康B不因A离线退出，重启进程重新证明，原会话延续 | 进行中（c1/c2a/b1已验收；其他worker/WS/本机排空与c3代理未完成） |
| M3d | 无粘性真实LB完整矩阵、节点定向读取/原生任务、故障与滚动 | 实际LB＋两后端证据，全部前置满足后开放multi | 待实施 |

节点选择只接受已登记node ID与精确process UUID，不接受URL或连接信息；由部署LB按固定白名单映射节点，接收端再次验证本机身份，不能在错节点返回本地数据冒充所选节点。选择不是授权，仍要求缓存查看/具体动作/区域权限。无选择的既有调用保留接收进程语义；定向目标不可达不得静默回退随机节点。M3a只补协议，不提前开放multi。路径限Common缓存公开契约/Monitor HTTP、frontend-next缓存页及部署/测试文档。

M3a1当前实现：`X-SMA-Cache-Node`与`X-SMA-Cache-Process`成对选择，node须在部署expected名单，process须为规范UUID。Monitor仅对`/api/monitor/caches`及子路径加MVC拦截，在Controller/任务/请求预算副作用前校验；非法或重复头400、错节点503、旧进程409，错误不返回实际本机身份。先要求当前后台人类与monitor:cache:view，原动作/区域权限仍单独生效；未选择且仅有旧动作权限的调用保持旧规则，不新增隐式查看权。合法查看响应返回实际node/process并no-store。错误只表明本次未进入处理器，已有操作仍须查询原回执，不据此释放UNKNOWN。先验证四项选择单元与架构，再跑四项真实HTTP＋原缓存任务/治理回归；M3a2前端尚未开始。

M3a1无数据库专项 **27/27通过，0跳过，BUILD SUCCESS（07:52，01:11）**，日志`backend/target/p08-m3a1-unit.log`。四项精确选择/缺字段/未知节点或URL/旧进程/权限测试、架构17项、模块2项、双语3项和普通安全清单1项通过。开始实际HTTP及既有任务/控制回归，验证拒绝时没有任务或请求预算副作用；未完成前不进入M3a2。

M3a1实际回归 **31项、30通过/1条件跳过、0失败/错误，BUILD SUCCESS（07:59，05:56）**，日志`backend/target/p08-m3a1-http.log`。新增HTTP4项全部通过，精确响应process、重复/缺字段拒绝、错节点/旧进程在任务/预算写入前拒绝、匿名/缺查看权不泄漏身份及原动作权限仍生效；原生资源9项、缓存任务HTTP5项、Monitor3项与9项节点确认通过。该轮未设置SMA_CACHE_REDIS_TEST_PORT，CacheConfirmationTests的Redis参数化用例1项跳过；不宣称它在本轮通过（M2d Redis另有实际证据）。M3a1完成，进入M3a2，完整M3/multi仍未完成。

M3a2当前实现：缓存目录增加接入节点/具体节点选择，选定时固定node＋process，不跟随名册刷新偷偷换进程；目录/概览/策略/预览携带固定头，任务提交/回执仍使用原持久任务定位且不依赖选定节点。切换使旧读失效并清空旧目录；路由/旧进程错误不回退随机节点、不显示旧统计。策略编辑或预览弹窗期间锁住节点选择/主刷新，写入捕获节点快照并保留原幂等键。共用createApi只新增可选、拷贝后的静态头，不替代认证、响应元数据/回执或重试协议。HTTP/缓存单元14项和应用类型检查通过；正在补三项真实浏览器的节点绑定预览、迟到响应与重启、离线不回退，完整前端验收及真实两端口定向仍待执行。

前端完整`npm run verify` **通过**（48文件179项单元、应用/测试类型、Lint、双语、Vite构建及发布预算），日志`.e2e-runtime/p08-m3a2-verify.log`。新增开发浏览器首轮3项中2通过/1测试在网络请求到达前断言记录为空，改expect.poll后单项1/1通过；生产包16项中15通过/1对弹窗隐藏背景控件的测试查询方式错误，改includeHidden检查实际disabled后单项1/1通过（`p08-m3a2-production-recheck.log`），未修改产品逻辑。分轮记录，不宣传首次16项全绿；将以独立输出补保留生产截图。实际双JVM选择脚本设置SMA_CLUSTER_ROUTING=1，读取响应真实node/process并精确选择A/B、错节点预览不增任务、A重启拒绝旧进程；使用M3a1真实HTTP轮JAR运行，结果待记录。

M3a2最终证据：生产包首个完整重跑16/16通过（`p08-m3a2-production-final.log`）。视觉检查后将选定node/process提示移至所有标签共用页头，保证策略页仍显示目标；最终完整verify因并行配置页面三处原始分页斜线Lint失败，仅替换为既有common.pageOf，不改配置逻辑。随后`.e2e-runtime/p08-m3a2-verify-latest.log` **完整通过（48文件179单元）**；最终生产包 **16/16通过，35.2秒**（`p08-m3a2-production-latest.log`）。独立产物`test-results-cache-m3a-latest`保存中英/明暗/1440和390宽截图、键盘与Axe检查；已查看窄屏目录与选节点策略图，页面无横向溢出，策略目标可见，表内横向滚动保留。

实际双JVM `p08-cluster-m3a-selection.log` **19组通过、退出0**；`cluster-1794442227862/result.json`记录passed=true、cleanupComplete=true、routingIncluded=true、m2Included=true、redisTimeout=1s及产物哈希。新增精确A/B统计process核对、携B选择到A的预览503且任务列表未变、A重启后旧process409且不定向的共享回执仍可读；原17组M1/M2认证/Redis/消息暂停恢复/文件/重启继续通过。清理所有本轮进程/库/账号/临时密钥，未替换用户服务。M3a1/a2完成，总排期同步；实际LB转发/白名单、密钥配置预检和多主机挂载仍未验收，下一项M3b，multi门禁继续关闭。

### M3b依赖拆分与验收计划

1. **M3b1受控迁移（完成，见末尾证据）**：宿主main在创建业务上下文之前识别显式迁移命令，使用仅含数据源和Flyway的隔离上下文；无组件扫描、Web、调度器、权限初始化、事件重投或业务启动监听器。沿用Modulith真实模块发现、根历史表和模块历史表/基线0，固定制品内迁移。提供migrate及validate命令，正常服务可显式配置仅校验；默认单机启动行为保持兼容。校验必须拒绝待执行、缺失、失败、未来或校验和不符的迁移，不通过baseline/repair掩盖问题。真实专用MySQL验证空库拒绝且无写、逐模块迁移/重复执行、已有历史兼容、无业务Bean，以及实际制品命令退出；随后同步两份进度。
2. **M3b2配置/密钥证据**：模块自有公开SPI生成带域隔离的配置/密钥证据，包含有效及保留解密键；不公开原密钥/连接串，不跨模块internal。复用现有节点/进程租约，追加精确归属的部署证据存储，缺失或不一致拒绝资格。测试错误键、同ID不同材料、旧进程与重复node。
3. **M3b3共享存储双向见证**：消费M2c的SharedStorageWitness，绑定部署清单、资源、双方进程及新鲜挑战；没有互证不能宣称共享，重启重新证明，有效节点不得因peer离线被无限拖死。测试相同/不同目录、丢失挂载、过期见证、慢I/O、重启与双JVM；此后才能进入M3c。

拟改路径限宿主启动入口、Common内部部署协调/公开deployment SPI、各密钥所属模块适配、File既有见证消费及相关测试和部署文档。不会建立第二套上传协议或节点名册；P11保护状态不随业务Redis迁移。M3b完成前不推进M3c，不开放multi；首次号段迁移仍须停旧写入器，DDL失败保留现场核对，不能自动repair或回滚旧算法。

M3b1实现/首轮验证记录：第一次根构建因Modulith core仅runtime可见而编译失败，补显式compile依赖（原制品已有该依赖）；第二轮23项中22通过/1模块环失败，改为common::deployment宿主引导契约，由宿主传应用类型，Common不引用宿主根类；第三轮模块门禁通过，但配置测试发现Boot的Flyway默认target=latest被误拒绝，已修正只拒绝非latest目标。失败日志分别为p08-m3b1-unit.log、p08-m3b1-unit-recheck.log、p08-m3b1-boundary.log；本项仍未完成。当前p08-m3b1-mysql.log在独立输出重编译并跑专用MySQL及全部边界复验，未进入M3b2。

M3b1补充边界：拒绝池catalog/schema/connection-init-sql覆盖，服务validate拒绝关闭Flyway或全局lazy初始化；保留正常默认单机路径。补充测试首轮p08-m3b1-final-unit.log因测试Class泛型断言编译失败，改为类型名断言后，当前根标准入口p08-m3b1-sealed-unit.log **31/31通过，0跳过，BUILD SUCCESS（08:46，02:01）**：命令/配置7、架构17、缓存边界2、模块2、普通安全清单1、SQL结构2。真实MySQL第一轮仍在清理阶段，最终增强断言尚须复跑；实际制品runner已增加SMA_CLUSTER_SCHEMA_MIGRATION=1，在自己的新库验证命令退出及无业务初始化，撤销服务账号DDL权后以validate运行原19组跨实例矩阵。M3b1未标完成，下一项仍不启动。

首轮MySQL与结构整合p08-m3b1-mysql.log **25/25通过，0跳过，BUILD SUCCESS**（其中受控迁移2项，约588秒），验证全模块迁移/重复执行/旧Modulith历史兼容及拒绝路径。补充更精确的Bean隔离与错误类型断言后，p08-m3b1-mysql-final.log被并行BrandPublicSecurityTests的content辅助方法重名阻断在测试编译，未执行数据库测试；当前并行源码已改名brandContent，本任务未改该文件。使用独立输出p08-m3b1-mysql-latest重编译复验这2项。实际JAR已通过空库无写拒绝、完整迁移不建管理员、校验及重复迁移保持逐行历史，正在启动validate服务节点。

实际制品联调p08-cluster-m3b1-schema.log **23组通过、退出0**；cluster-1794444435380/result.json记录passed=true、cleanupComplete=true、schemaMigrationIncluded=true、redisTimeout=1s，JAR SHA256为2e635946b56e088a38c56e9793cc5d414b4332d38e54017a97f1fa68635d24fb。新增4组覆盖空库validate拒绝且零表、独立migrate退出且iam_user为0、validate及重复migrate保持逐行历史、两服务节点撤销CREATE/ALTER/DROP/INDEX后validate启动且服务日志未调用DbMigrate；原19组跨节点身份/权限/票据/Redis/消息/文件/重启全部保留。运行快照的结果标签笼统写without DDL privileges，当前runner仅把该标签收紧为四项实际权限，未改测试行为；不声称所有数据库特权皆移除。所有本轮JVM/临时库/账号/密钥已清理，用户服务未替换。增强MySQL专项仍在完成，M3b1继续保持进行中。

M3b1最终验收：增强MySQL专项p08-m3b1-mysql-latest.log **2/2通过、0跳过、BUILD SUCCESS（09:05，10:37）**。精确断言只存在迁移入口自有Bean、无业务runner/调度器/Web；空库validate零表、全模块迁移及重复运行/原Modulith历史兼容、pending/checksum/future精确异常、已删除模块历史拒绝均通过，并清空本专项可清空库。结合最新31项门禁和23组真实制品构成分轮证据，不把历史失败轮改称首次全绿。runner脚本语法/ESLint通过，受影响路径git diff --check通过；18101/18102/5197已无监听。README、后端规则、部署/缓存运行说明与总排期同批同步，M3b1完成。下一项M3b2配置/密钥证据，M3b3共享见证、M3c/d、S6及X5未完成，multi仍关闭；未部署用户服务。

收尾一致性核对：真实制品所用sealed-unit输出与最终mysql-latest输出的9个本项部署/启动class逐文件SHA256一致；不把两轮之间其他并行模块的变化纳入本项运行证据。最终runner语法及ESLint、受影响文档/已跟踪代码diff检查通过。

### M3b2配置与密钥证据（完成）

| 子项 | 验收与证据 | 状态 |
| --- | --- | --- |
| M3b2a | 宿主SPI、四类实际运行密钥；41专项、34安全消费者及装配1项分轮通过 | 完成 |
| M3b2b | 8个配置域及12域总集合；39专项、字段清单1、37宿主消费者及最终12域宿主3项分轮通过 | 完成 |
| M3b2c | 持久挑战/一致性基线、既有租约/epoch绑定、故障隔离及独立JVM矩阵 | 完成（c1/c2分轮＋c3真实双JVM34组，清理完成） |

细化设计见[部署一致性证据](proposals/P08-deployment-evidence.md)。按依赖拆分：M3b2a宿主SPI/有界摘要/真实归属注册及四类实际运行密钥适配（当前实施）；M3b2b模块配置白名单与真实宿主集合验收；M3b2c追加持久证据并绑定现有清单/租约/epoch、独立JVM不一致拒绝。每项验证通过再推进，b2整体尚未完成。初始完整密钥环和活动键严格一致，密钥轮换须协调窗口；OpenAPI预检只读、不自动生成。共享文件实际互证仍在b3，multi门禁不变。

M3b2a第一轮根标准入口p08-m3b2a-unit.log **41/41通过，0跳过，BUILD SUCCESS（09:19，01:16）**：8项新证据/实际密钥对象测试、Auth加解密5、租户凭据7、请求HMAC2、架构17、模块2。新增common::deployment源SPI、定长挑战/有界规范编码、按真实模块归属注册；四类源从现有运行对象取证，保留旧键，集合缺域或源异常只报告不可用。OpenAPI探针不创建目录/文件且不认可本进程临时生成的钥，原single加解密协议不变。正在补真实宿主Bean集合及原请求账本/Auth持久/机器认证预算回归；M3b2a未标完成，b2b不提前启动。

M3b2a宿主首轮p08-m3b2a-host.log共35项，34通过/1错误，0跳过。原请求账本13、Auth持久4、OpenAPI共享预算8/认证6、应用冒烟2及普通清单1通过；新增装配测试在Spring测试线程的模拟HTTP请求下调用collect，被正确的宿主门禁拒绝。仅把该测试改为无继承ThreadLocal的独立宿主线程，仍断言tenant job拒绝，不放宽生产检查；单项复验待记录，a仍未完成。

M3b2a完成：p08-m3b2a-host-recheck.log真实宿主装配 **1/1通过、0跳过、BUILD SUCCESS**（宿主用例447秒）。实际AOP/Bean集合四个必需源齐备，独立宿主线程取得稳定完整摘要，tenant job仍拒绝。组合证据为41项密码学/结构门禁、首轮34项安全消费者通过及装配单项复验，不把首轮35项写为全绿。README、后端规则、部署/缓存运行说明与总排期同步；进入M3b2b配置白名单，持久比较、租约绑定、共享存储和multi仍未交付。

M3b2b实施：新增7个配置域（Common宿主/缓存、Auth、Message、Identity、File、Mail），与4个密钥域共同强制齐备。仅读取类型化白名单，缓存先逐区域按真实有效策略/注册格式摘要再合并；Redis拓扑规范排序、逻辑DB/TLS/预算参与，ACL凭据不参与。认证/应用身份/文件/邮件共享预算及浏览器来源/代理清单参与；本地路径、节点ID/端口、密钥路径和后台worker启停角色不作为配置一致性字段。文件资源只比较登记ID/共享声明，物理共享留给b3。正在验证逐字段变化、集合顺序、实际宿主11域以及原受影响模块。

M3b2b第一轮p08-m3b2b-unit.log **37/37通过、0跳过、BUILD SUCCESS（09:45，01:34）**。8项新白名单测试逐项变更Identity全部27项及File上传/认证/邮件预算，验证变化必影响摘要；缓存namespace/区域schema/能力/实际策略、Redis逻辑DB/TLS差异拒绝，等价配置/种子顺序与不同ACL凭据不误报；本地路径不进入文件声明，收集不创建目录。原密钥/证据/缓存边界和模块架构继续通过。下一步真实宿主11域装配及受影响模块回归；b2b仍未完成。

白名单复核补入Tenant域：平台部署操作者名单若各节点不同会造成随机403，入口验证及品牌默认值也应一致，因此增加tenant.platform-policy；只读取Tenant自己已绑定的公开配置，不改平台授权、域名或品牌持久流程。当前总计12个必需域（4密钥＋8配置），默认品牌revision相同但内容不同时也必须改变摘要。前一轮宿主仍按11域编译产物验证，不能充作12域完成证据；追加Tenant纯配置专项和最终12域装配复验。

M3b2b最终验收：p08-m3b2b-host.log原11域宿主及文件3/应用会话8/邮件Outbox15/缓存目录4/Monitor3等 **37/37通过，0跳过，BUILD SUCCESS（09:55，07:35）**。补Tenant域后p08-m3b2b-complete-unit.log **39/39通过（09:57，01:31）**；p08-m3b2b-inventory.log新增配置字段清单 **1/1通过（10:04，01:42）**，已登记类型新增字段即要求显式纳入/排除审查。最终p08-m3b2b-final-host.log **3/3通过、0跳过、BUILD SUCCESS（10:08，08:23）**：实际12域必需集合齐备，反向注册顺序不影响总摘要，宿主可收集且tenant job拒绝，默认single启动/健康和匿名保护继续通过。b2b完成，组合证据分轮记录，未运行新的无粘性LB或多JVM配置拒绝场景；这些在b2c/M3d。README、规则、部署/二开手册、方案索引和总排期同批同步；下一项b2c，M3b2整体及multi仍未完成，未部署用户服务。

### M3b2c实施拆分

当前c1：追加Common V14到既有group/lease表，固定首节点建立不可覆写基线，随机挑战按清单换代，报告绑定node/process/epoch并最多15秒有效；无新租户豁免。先做旧V13结构升级/空库、真实MySQL错误节点不影响健康节点、竞争/过期/重启/宿主身份边界。通过后c2独立有界worker与状态/启动联接，再c3实际双JVM矩阵；各项通过同步两份进度，不把内部存储完成当作流量门禁完成。细化见部署证据方案。

M3b2c1迁移/结构首轮p08-m3b2c1-migration.log **23/23通过，0跳过，BUILD SUCCESS（10:22，04:38）**。Common V13真实旧group/lease记录保留升级V14，新证据为空不继承资格；重复迁移零执行，空库V1～V14全部执行及validate通过。平台免租户表集合未增加，架构/模块/SQL门禁通过。V14从此仅追加修正迁移；开始6项真实存储协议及原节点/确认回归，c1尚未完成。

M3b2c1完成：p08-m3b2c1-store.log **27/27通过、0跳过、BUILD SUCCESS**，包括新存储6项、原节点清单8、节点runtime2、确认10（显式配置Redis16379，参数化真实Redis不跳过）、普通安全清单1。实际MySQL验证指定首节点引导、错误peer/源不可用不影响健康节点、基线不可覆盖、心跳不续证、旧进程/epoch/挑战拒绝、清单递增换代及并发唯一基线；HTTP/job/外层事务调用拒绝。结合V14迁移/结构23项，c1完成并开始c2；尚无后台自动证据续报或接流门禁，multi仍关闭。

M3b2c2实施：新增默认关闭的sma.deployment.evidence.enabled，仅允许enabled节点租约、至少两节点清单及eager schema-mode=validate。独立单线程/零队列每秒尝试取当前租约→事务外收集→持久报告，迟于5秒的收集不报告；本地MATCH按单调时钟最多10秒且随当前租约/epoch失效。deploymentEvidence健康贡献者只输出安全状态，未证实即DOWN；它尚未组合共享存储/摘流，也不冒充M3c的最终readiness。默认single无新worker/健康项。开始线程/超时/状态/配置及模块门禁，真实宿主接线随后验收。

M3b2c2首轮p08-m3b2c2-unit.log **26/26通过，0跳过，BUILD SUCCESS**：证据runtime4、原节点runtime2、架构17、普通安全清单1、模块2。证明单调过期、lease/epoch失配拒绝、慢源只执行一次/无排队/不提交迟到证据、异常脱敏、HTTP调用拒绝、默认不装worker及validate/lazy限制。补充禁用租约/单节点拒绝用例并与真实宿主HTTP联接同轮执行，日志p08-m3b2c2-http；c2仍未标完成。

M3b2c2完成：最终p08-m3b2c2-http.log **6/6通过、0跳过、BUILD SUCCESS（10:43，07:54）**。真实宿主先独立迁移再validate启动，实际定时线程取得12域证据并写入基线；MockMvc真实过滤链/健康汇总初始200 UP，注入错误/缺失报告503 DOWN，恢复真实收集后200 UP，节点租约仍OWNED。该轮另含最新5项运行时测试（补单节点/禁用租约拒绝），结合前轮26项架构/模块/普通清单构成分轮证据。它是单JVM真实宿主＋模拟错误证据注入，不能称为两JVM错误密钥验收；c3尚未实施。关闭测试上下文和worker，未替换用户服务。README、部署/规则和两份进度同步；完整M3b2仍未完成，后续c3、共享存储b3、M3c/d/S6/X5保留，multi仍关闭。

### M3b2c3独立JVM验收（进行中）

复用run-cluster-e2e隔离库/账号/端口及真实Chrome，追加SMA_CLUSTER_EVIDENCE=1（要求受控迁移开关）。部署级生成共享测试租户凭据环与保留请求旧键；显式把deploymentEvidence加入该测试的readiness组。B依次使用同ID异值HMAC、缺失租户密钥、遗漏旧HMAC键、不同会话TTL及OpenAPI缺文件；核对其持久报告和readiness 503，A仍UP且原会话可用，基线不变。恢复B配置后重获MATCH并原会话可用；A退出期间B仍资格有效，A新进程重新证明。保留原M1/M2/节点定向矩阵。所有进程/库/密钥限本轮，失败不重发业务写；结果不能扩展为LB或共享存储资格，multi不变。

M3b2c3当前制品根标准入口p08-m3b2c3-current.log **38/38通过、0跳过、BUILD SUCCESS（10:48，01:48）**；runtime/密钥/配置/字段清单、架构/模块/普通端点基线通过。runner语法和ESLint通过，已开始p08-cluster-m3b2c3-evidence.log。八类B差异为HMAC活动同ID异值、遗漏旧键、Auth环同ID异值、租户凭据同ID异值/缺失、会话TTL、OpenAPI同ID异值/缺文件；恢复B及停A超过30秒后再启动A验收重新证明。所有拒绝看readiness＋精确新process/epoch持久证据，不用旧行或仅进程存在冒充通过。未得最终结果前b2c3保持进行中。

M3b2c3功能断言已完成：本次原23组＋双实例初始资格、8类B差异、恢复B、A退出超30秒/B持续资格/A重启共34组通过，正在等待runner资源清理与最终result.json。错误节点按新process/epoch报告MISMATCH或UNAVAILABLE且readiness 503，A仍MATCH/原会话有效，基线全程不变；缺OpenAPI探针文件未生成。恢复B和A均重新证明，旧会话保留。最终清理前不标完成。

M3b2c3最终验收：p08-cluster-m3b2c3-evidence.log **34组通过、退出0**；cluster-1794451764894/result.json记录passed=true、cleanupComplete=true、evidenceIncluded=true、redisTimeout=1s，JAR SHA256=403db2a3587857332441d08b243bcde758d5b25e671bb3e8237459631128698e。八类错误B依次使用新process，epoch4～11；活动HMAC/遗漏旧HMAC/Auth环/租户环/会话TTL/OpenAPI材料不同均MISMATCH，租户缺钥/OpenAPI缺文件为UNAVAILABLE，readiness均503且A仍MATCH/原会话正常。基线始终不变；OpenAPI缺文件未生成；恢复B新epoch重新MATCH并保留原会话，A退出超过32秒后B仍资格有效，A重启重新证明不改基线。原23组受控迁移/身份/缓存/消息/文件/定向操作全部保留。临时进程/库/账号及7个受控密钥路径已清理，18101/18102/5197无监听，未改用户服务。M3b2整体完成并双写总排期；证据仅同主机独立JVM、Redis显式1s、readiness按测试部署显式包含证据贡献者，不是200ms/真实LB/共享挂载资格。下一项M3b3，然后M3c/d、S6及X5；multi保持关闭。

### M3b3共享存储资格（完成，本机独立JVM）

按依赖拆分：a真实归属存储注册、进程/epoch绑定的一次写入探针与单向校验；b持久报价/双向确认及失效/恢复围栏；c有界worker/健康与实际双JVM错误挂载/重启矩阵。当前a实施。先验证真实文件同目录/不同目录、资源和身份混用拒绝、未知写只核对不重建、自身探针丢失/宿主身份/超时及原FileClusterStorage门禁，再推进b。细化见共享文件见证方案；multi仍关闭。

M3b3a首轮p08-m3b3a-probe.log **38/38通过、0跳过、BUILD SUCCESS（11:16，02:28）**：新探针6项、原FileClusterStorage实际文件6项（含Windows junction）、证据4、架构17/缓存边界2/普通清单1/模块2。验证同目录两资源各自随机/互读、不同或缺失根拒绝、旧process/epoch/清单及缺资源拒绝、未知写只verify、不重建丢失自身探针、声明变更/重复/跨模块/容量/宿主身份/累计超时拒绝。补充畸形UUID错误脱敏和真实宿主默认未声明共享不授予资格；最新专项及默认File/宿主回归尚待记录，a不标完成。

M3b3a完成：最新p08-m3b3a-host.log **19/19通过、0跳过、BUILD SUCCESS（11:25，08:14）**，包括探针7/原文件见证6/原File3/真实宿主装配1/冒烟2。默认未声明共享的目录不授予共享资格，但原单机文件与健康继续可用；畸形UUID统一错误无输入泄漏。结合首轮38项结构/实际文件证据，本子项完成。进入b持久锚点/报价/双向确认；探针没有自动worker，也不构成最终资格，multi仍关闭。

M3b3b实施设计：追加V15只扩展既有group/lease技术字段。group固定manifest绑定的存储锚点报价（只清单首节点初建、同代次不可换）；每节点保存不可变process/epoch报价、递增本地generation、一次最多31个peer读确认、15秒观察和certified状态。首次需锚点全部可读＋当前peer相互确认同generation；自身故障或观察过期推进generation并废旧确认。已证实节点持续自检时不因peer离线失去资格，错误新peer不改原证书。报告重新验证当前配置MATCH/租约及双方generation，I/O在事务外。先迁移及真实MySQL协议，后c运行时/真实文件双JVM。

M3b3b首轮结构p08-m3b3b-compile.log **22/22通过、0跳过、BUILD SUCCESS（11:30，01:26）**，新存储编译及架构17/平台隔离1/SQL2/模块2通过。已补V14→V15保留配置证据/空库验证及6项真实MySQL双向协议（单向不授证、固定锚点、资源集合、故障代次、证据断档、peer离线、进程替换与宿主边界）；开始迁移专项，存储协议尚待执行，b未完成。

M3b3b迁移p08-m3b3b-migration.log **1/1通过、0跳过、BUILD SUCCESS（11:34，02:32）**：V14→V15保留原配置摘要/节点epoch及状态，新存储证据NULL；重复迁移零执行；空库15个Common迁移和validate通过。V15从此只追加修正。开始7项存储协议和原配置证据/节点回归，b尚未完成。

M3b3b完成：p08-m3b3b-store.log **23/23通过、0跳过、BUILD SUCCESS（11:40，04:37）**，新存储7项、原部署配置存储6/节点清单8、平台隔离1/普通清单1。真实MySQL证明单向不可授证、固定锚点/资源集合、失败/断档废旧reverse确认、健康节点peer离线继续自检、重启不可继承及旧清单/迟到报告拒绝。组合22结构＋1迁移分轮证据，V15保持不可改；进入c有界实际I/O与健康/双JVM。当前无自动存储worker，不把内部certified当作已接流，multi仍关闭。

M3b3c1运行时接线：默认关闭sma.deployment.storage.enabled，开启须已有配置证据runtime。独立单线程零队列，每轮5秒总预算、READY单调10秒并要求配置MATCH/当前epoch；自身及固定锚点每轮实际校验，peer按受信快照选择，引导/最前在线节点轮询peer，其他节点优先该peer。错误peer不会让已健康节点错误失效；自身/锚点失败或迟到结果报告本地失败，旧代次拒绝。sharedStorage健康仅给state；默认不启用，不删锚点/peer文件。开始线程边界及实际文件＋MySQL双实例对象测试，真实两个JVM和故障目录矩阵随后验收。

M3b3c1首轮p08-m3b3c1-unit.log 30项中29通过/1测试错误，0跳过：慢I/O测试在释放latch前调用Mockito.verify同步方法，测试主线程被mock的synchronized监视器阻塞直至夹具超时，导致推进单调测试时钟晚于实际返回。改为AtomicInteger检查次数并先推进时钟/释放，再验证store失败报告；生产实现未改。默认错误配置测试补齐其他依赖，精确验证缺DeploymentEvidenceRuntime被拒绝。修正与真实I/O/MySQL组合复验，未通过前不推进双JVM。

M3b3c1本地运行时验收完成：p08-m3b3c1-io.log **7/7通过、0跳过、BUILD SUCCESS（11:56，09:15）**，包括修正后线程/配置3、真实文件＋MySQL两节点runtime对象2、应用冒烟2。证明实际双向文件互证可取得证书，不同目录无法互证，删除B自身探针后不重建且只撤销B证书，A继续可用；原默认单机健康和认证保护通过。首轮慢I/O夹具失败保留，结合此前29项通过构成分轮证据，不称首次30项全绿。当前是同JVM的两runtime对象＋真实文件/DB，尚未验证两个独立JVM或真正OS挂载；下一项c2扩展隔离runner并显式组合sharedStorage健康，验证同/错/缺目录、探针失踪、恢复及重启，完成前M3b3整体不交付。README/部署/规则与总排期同步，multi关闭，用户服务未替换。

### M3b3c2独立双JVM验收（进行中）

扩展原隔离runner，SMA_CLUSTER_STORAGE=1要求配置证据/受控迁移/M2全部开启；readiness显式包含sharedStorage。两个服务先同时启动再等待互证，避免A等待未启动B的引导死锁。保留原34组，追加managed目录错误/缺失、B自身探针删除后超过资格TTL仍不重建、恢复原字节后新代次互认、重启及健康A原会话连续可用。故障只修改本轮生成路径/进程，结果不当作生产多主机挂载或LB验证。当前制品先根标准入口验证，再实际运行，完成前不标M3b3完成。

M3b3c2当前制品p08-m3b3c2-current.log **41/41通过、0跳过、BUILD SUCCESS（12:00，01:32）**；探针/原File见证/runtime/配置runtime及结构/模块/普通端点通过。runner语法/ESLint通过，实际p08-cluster-m3b3c2-storage.log已启动。双节点同时启动后等待配置＋存储双健康；原34组保留，新增6组存储场景（初始互证、split/missing根、恢复根、探针丢失超过17秒、原字节恢复新互认）。当前待实际结果，不标M3b3完成。

M3b3c2功能断言全部结束，正在清理：原34组完整保留，新增初始共享互证、B split/missing根、正确根恢复、丢失自身探针超过17秒不重建/仅B失效、恢复本轮原32字节后新代次互认共40组。全程配置基线及存储锚点不变、原会话保持。最终result/cleanup确认前不标完成。

M3b3c2最终验收：p08-cluster-m3b3c2-storage.log **40组通过、退出0**，cluster-1794456095162/result.json记录passed=true、cleanupComplete=true、storageIncluded/evidenceIncluded=true、redisTimeout=1s及22次受控JVM启停；JAR SHA256=2cff2d801edfbbcc94f186820b6f737072eaf6ee349af3a741e50cdfbcb200e4。原34组完整通过；新增两个独立JVM实际双向文件互证、managed错误/缺失根503且A可用、缺失根不自动创建、B正确根重启新进程重新证明、探针删除超17秒不重建/仅B证书失效、恢复本轮原字节后新代次反向确认恢复READY通过。readiness包含配置与存储贡献者，原会话正常、锚点和配置基线保持不变；A退出超租约/B继续访问及A重启矩阵同时通过。测试库/账号/密钥/进程已清理，18101/18102/5197无监听，用户服务未替换。结合本轮41项根门禁，M3b3及M3b完成并同步总排期。证据限同机独立JVM＋真实本地文件、显式Redis1s；不是实际多主机挂载、200ms负载或无粘性LB证明。下一项M3c readyness/liveness/摘流/代理，再M3d/S6/X5；multi保持关闭。

### M3c健康/摘流/代理实施拆分

1. c1统一接流：默认关闭traffic.enabled，启用必须evidence/storage运行时与schema validate、节点租约、规范readiness/liveness组合；HTTP最前端拒绝未就绪新请求（含登录/WS/app/open），健康精确GET/HEAD继续可见。在途请求不倒退/重发，503不能替代历史UNKNOWN回执。门禁基于现有有界观察，不在每请求查DB/文件。验证配置拒绝、来源/认证前拒绝且无副作用、恢复与启动/关闭事件、真实HTTP健康分离。
2. c2受控排空：host-only部署摘流控制、不可逆排空状态、既有WS正常关闭并取票重连、HTTP宽限/worker与租约停止顺序；同时审查业务worker首次资格/失效后的领取准入，证据/租约基础线程须能在未就绪时持续恢复，已开始工作按其原持久协议收尾。实际进程关停及原会话延续验收，禁止新增任意用户停机端点。
3. c3可信代理与部署流程：核对客户端IP/Host/Origin信任，固定节点路由LB配置、schema/密钥/存储版本维护与滚动回退规程及验证；之后M3d真实无粘性LB才开放multi。
c1开工范围为Common部署门禁与HTTP filter、健康/配置/测试及文档；验收中另外修复File见证并发读锁和Tenant首次引导竞争，详见下方记录。既有安全认证/租户/幂等契约不变。用户服务不重启，测试用隔离库/端口。

M3c1首轮p08-m3c1-gate-unit.log **37/37通过、0跳过、BUILD SUCCESS（12:27，01:53）**：新门禁/过滤/配置8、主机策略8、字段清单1、架构17/普通端点1/模块2。默认关闭traffic.enabled；开启要求eager validate、节点清单及配置/存储证据，最前端过滤只豁免精确GET/HEAD健康路径。readiness固定四项、liveness仅本进程状态，校验实际状态聚合及503映射；停止事件不可重新接流，在途链只执行一次，不读被拒正文。traffic开关参与主机部署证明；这会改变旧清单摘要，禁止覆写既有基线。开始隔离真实HTTP验收，c1尚未完成；WS排空和真实LB仍属后续。

M3c1真实HTTP首轮p08-m3c1-gate-http.log **17通过/1装配错误，0跳过，BUILD FAILURE（12:36）**：Modulith observability对ApplicationListener创建CGLIB代理，DeploymentTrafficGate的final限制导致Tomcat启动失败，尚未进入HTTP断言。已去掉final并补实际类代理状态/关闭回归；不关闭observability、不放宽门禁。此前补根健康详情禁止公开及聚合器检查的p08-m3c1-gate-current.log37/37通过，但该制品仍含final缺陷，不用于后续双JVM。修复后重新执行门禁/HTTP/结构，未通过前不推进矩阵或c2。

M3c1 HTTP修复复验p08-m3c1-gate-fixed.log **39/39通过、0跳过、BUILD SUCCESS（12:47）**：真实Tomcat/过滤链、错误资格下登录/captcha/app/open/真实WebSocket握手503、liveness200、资格恢复、HEAD健康及关闭后不可重新接流通过；状态由mock运行时注入，非双JVM资格证明。保留Modulith observability，新增实际类代理回归通过。进一步校验根健康的状态映射后，最新p08-m3c1-gate-final.log **38/38通过、0跳过、BUILD SUCCESS**，制品SHA256=ec9425afaaab779fa520232a5ee8a05585f80af02225a37c3fd19658771e8b0f。使用该制品开始SMA_CLUSTER_TRAFFIC=1独立双JVM，保留原40组，并在全部配置/存储故障下直接检查业务接流与liveness；c1尚未最终标完成。

前端runner语法和专项ESLint通过。完整verify首轮在OperationPanel.vue的原始叹号命中i18n检查；检查时当前并行工作已改成CircleAlert，未改动该页面，正在重新执行完整verify。首轮失败保留，不把旧日志称为通过。

前端完整verify第二轮在并行新增operation-panel.spec.ts命中value.held可空类型错误；读取当前文件时已是value.held?.length || 0，仍保留该并行修正、不改其代码。复验记录到p08-m3c1-frontend-verify-current.log；不得据此前两轮失败声称完整前端通过。

前端最终完整verify **退出0**：p08-m3c1-frontend-verify-current.log记录60文件/312单测通过、类型/测试类型/ESLint/i18n通过、生产构建及发布预算通过（首包160KiB gzip、38懒模块、296文件哈希）。两次先前并行页面报错均按当前已修正文件复验，未接管或覆盖其代码。双JVM本轮目录cluster-1794458905801，仍在执行，最终结果/清理之前c1不标完成。

M3c1双JVM首轮cluster-1794458905801 **passed=false、cleanupComplete=true、退出1**：迁移/重验及去DDL服务启动四组通过，两端readiness曾UP且A登录成功，但随后读取fixture有效参数GET返回common.error.unavailable/503，尚未进入错误节点矩阵。原日志不足以区分门禁瞬时失效与业务层503，不能直接认定根因或加重试掩盖。正在补失败时最多12条只读健康/配置状态/存储证书快照（不含token/正文/路径/钥/摘要），以同制品在全新临时库复现；业务写保持一次发送、生产门禁不放宽。c1继续进行，c2不启动。

新增固定状态DEBUG拒绝日志及有界只读失败采样，38专项再次通过（p08-m3c1-gate-diagnostic）；采样仅失败后执行，SQL各2秒/HTTP各1.5秒，最多12条，不重试业务。静态排查另发现FileClusterStorage.matches对只读verify也持排他锁：健康节点同时读同一锚点可能互相拒绝。先补独立JVM持共享读锁的确定性回归，要求并发读取通过、发布/退役仍拒绝；初始503与此缺陷是否相关尚待复验，不作已确认根因陈述。

并发见证红灯已确定重现：p08-m3c1-witness-red.log **6通过/1失败，0跳过**，独立子JVM实际持有SHARED锁时，原实现第一轮只读verify即false。修复仅让verify使用共享读锁；publish已有文件核对、retire及首次写仍排他，写权限/摘要/长度/force/无链接/预算约束保留，不支持shared锁的挂载不降级冒充成功。开始File、Probe、Runtime、门禁与结构组合复验，通过后再做全新双JVM矩阵，不把单个回归等同首轮503根因已逐帧证实。

并发见证修复组合p08-m3c1-witness-fixed.log **60/60通过、0跳过、BUILD SUCCESS（13:09，02:17）**：新跨JVM持锁回归及File7、Probe7、Storage runtime3、Evidence runtime5、Traffic9、配置/清单9、架构/普通端点/模块20全部通过。原文件独占锁/错误字节/无授权/超时/junction约束保留。制品SHA256=546320dfc0ce685821c276217dd848b3a08e705b00d80aa9f6c673609d437432，已启动p08-cluster-m3c1-traffic-fixed.log全新临时库矩阵；最终结果及清理前c1仍进行中。

最新前端p08-m3c1-frontend-final.log完整verify **退出0，61文件/315单测及类型/i18n/构建/发布检查通过**。双JVM第二轮cluster-1794460209814在双服务同时首次启动时B因TenantDirectoryBootstrap的tenant_tenant主键1竞争退出，已结束并cleanupComplete=true，未进入业务矩阵。只读诊断确认A配置MATCH、存储未互证、readiness503/liveness200，符合门禁预期；这与上一轮参数GET503不是同一已证实原因。

当前修复范围扩展到既有TenantDirectoryBootstrap/TenantRecordMapper及专用测试：平台租户使用原子INSERT ON DUPLICATE KEY无改值占位并持有行锁，按目标ID当前读取检查冲突/已删除；域名也用当前锁定读，不能因旧REPEATABLE_READ快照重复插入。保留人工名称/状态/安全代次、禁用域名和域名归属，不加表/迁移/租户豁免。新增TenantDirectoryBootstrapTests在唯一集成上下文/空目录专用库验收两真实事务并发与旧快照、幂等、禁用保留、代码冲突/软删除拒绝。该项通过前不重跑矩阵或推进c2。

租户引导修复p08-m3c1-bootstrap.log **40/40通过、0跳过、BUILD SUCCESS（13:29，05:27）**：唯一集成上下文的真实MySQL旧快照/并发引导、人工名称/状态/代次/禁用域名保留、代码冲突/域名归属/软删除拒绝通过；门禁9、File7、架构/SQL/平台隔离/普通端点/模块23回归通过。制品SHA256=ce55737e951852fda2ffce444327302063bcc8dfd42016cd0d424f25d7734910，包含并发共享读锁与原子引导修复。下一轮全新临时库双JVM矩阵开始前已记录本项通过；c1最终结果仍待完整矩阵和清理。

全业务双JVM矩阵cluster-1794461473552 / p08-cluster-m3c1-traffic-bootstrap.log **41组通过、退出0、passed/cleanupComplete=true**，同制品22次进程生命周期。并发首次引导、A登录B访问、原身份/缓存/消息/文件/定向任务、八类配置故障、peer退出/重启、存储错/缺根及探针丢失/恢复均通过，端口已释放。原定向负例产生一条HTTP503诊断但当时readiness/liveness均UP，脚本按预期负例通过；它不是门禁误拒。

验收审查发现本轮部分配置故障请求仍处于NOT_READY启动阶段，故这41组只作为全业务/总体拒绝基线，不单凭它宣称已完成启动后的持续隔离。已增强blockedTraffic：只读轮询me，并从当前OS PID及有界日志片段确认WAITING_CONFIGURATION或WAITING_STORAGE（证明ApplicationAvailability已完成启动），再一次性检查登录/captcha/WS/app/open拒绝。11次具体状态证据写入trafficChecks。新增SMA_CLUSTER_TRAFFIC_ONLY=1用于补证，必须给SMA_CLUSTER_TRAFFIC_BASE_RESULT指向同一JAR、已通过且已清理的完整M2/路由/迁移/配置/存储/traffic结果；输出显式标注业务矩阵另见引用，不能把聚焦运行当全业务验收。准备以相同制品补完本项，c1仍不标完成。

### M3c1最终验收（完成，本机独立JVM）

聚焦补证p08-cluster-m3c1-post-startup.log / cluster-1794462687521/result.json **23组通过、退出0、passed=true、cleanupComplete=true**；19次受控进程生命周期，trafficChecks包含当前PID对应的**8次WAITING_CONFIGURATION、3次WAITING_STORAGE**。每次先证明ApplicationAvailability已经结束NOT_READY，再验证me/captcha/登录/WS/app/open的503及no-store；liveness保持UP，A健康peer和B恢复后的原会话正常。该结果通过文件哈希引用同制品完整41组基线cluster-1794461473552，不能把23组单独称为全业务矩阵。两轮JAR均为**ce55737e951852fda2ffce444327302063bcc8dfd42016cd0d424f25d7734910**；18101/18102/5197无监听，临时库/账号/生成密钥已按runner清理，未重启用户服务。

后端分轮证据为真实HTTP/类代理39项、共享读锁/配置/门禁60项，以及当前制品真实MySQL并发引导/结构/门禁40项，全部0跳过通过；中途失败及原因保留在上文，不能相加当作不重复的完整测试数。最新前端p08-m3c1-frontend-delivery.log **完整verify退出0，61文件/315单测，类型/ESLint/i18n/生产构建和发布预算通过**。语法、runner专项ESLint及文档diff检查通过。README已收敛P08过时阶段说明，部署/运维/规则和总排期同步。

交付包括：默认关闭的traffic门禁、固定健康分组/状态映射校验、前置拒绝及在途请求保留、不可逆关闭状态；并发见证共享读锁及原子租户引导修复。证据仍限同主机独立JVM/真实本地文件、Redis显式1s，不覆盖生产多主机挂载、200ms压测或无粘性LB。**下一项M3c2**：host-only主动摘流、既有WS正常关闭/取票重连、业务worker资格与领取准入、HTTP/worker/租约排空顺序和实际进程验证；同步审查启动元数据锁读的索引范围。随后c3/M3d、S6及X5，`deployment-mode=multi`继续拒绝启动。

交付只读核对：本次四轮双JVM（含两次失败）的临时schema和账号各为0残留，28个生成密钥路径均不存在；无本轮JVM或18101/18102/5197监听。实际JAR与两份成功结果哈希一致，聚焦结果引用的完整result文件哈希一致。分支仍为main，改动均属基座及验收/文档；未操作用户服务或并行专项资源。

### M3c2受控摘流与排空（进行中）

已核对当前源码：HTTP只在ContextClosed进入不可逆拒绝，Mail/Cache等后台领取尚无共同准入，多个线程池直接shutdownNow；SocketHub通用close会发送session-ended，不能用它模拟部署排空。按[具体方案](proposals/P08-deployment-drain.md)依次做c2a共享状态/工作许可、c2b真实worker与WS、c2c本机控制/HTTP生命周期/实际进程、c2d索引与合并验收。当前c2a实施，改动限Common契约/协调与Gate及测试；不声称已有外部控制或全部worker接线，不重启用户服务。multi继续关闭。

c2a核心实现：新增公开DeploymentWork.tryStart/snapshot与幂等可跨线程关闭的Permit，可信host或SYSTEM_JOB在事务外准入，HTTP/普通身份/活动事务拒绝；本地活动数最多1024，不排队。共享协调器把准入和不可逆摘流线性化，资格失败可恢复，摘流不能恢复、不能清零未完成计数。HTTP Gate复用同一状态，默认单机也须ApplicationAvailability就绪；traffic开启但Gate缺失/异常即拒绝。现有worker尚未接线，控制入口仍为内部方法；开始单元/装配/并发门禁与真实HTTP复验。

c2a首轮p08-m3c2a-work-unit.log **45/45通过、0跳过、BUILD SUCCESS（14:31，02:25）**：新工作许可7、原Traffic9、配置/清单9、架构/普通端点/模块20。证明容量、并发开始/摘流、跨线程重复关闭、在途计数不清零、资格恢复与不可逆关闭、host/job/HTTP/事务边界、真实类代理及Spring条件绑定。开始p08-m3c2a-work-http.log真实HTTP与默认单机装配回归；本子项尚不标完成，不推进worker接线。

**M3c2a完成**：p08-m3c2a-work-http.log **19/19通过、0跳过、BUILD SUCCESS（14:43，10:45）**，含真实Tomcat/HTTP1、默认应用2、Traffic9与Work7。真实装配中内部host摘流令readiness503/liveness200及业务拒绝，已获准许可仍计数、后续准入拒绝，关闭后不能重新开放；原默认单机健康/匿名保护继续通过。结合45项结构/条件/权限/并发证据完成核心契约，README/规则/二开手册与两份进度同步；它没有新增用户控制端点，也不意味着所有worker或WS已接线。下一项c2b，先b1缓存任务，再Mail/维护/事件/WS及独立定时器，按消费者逐项验收；multi不变。

c2b1缓存任务接线首轮p08-m3c2b1-cache-unit.log **44/44通过、0跳过、BUILD SUCCESS（15:04，01:31）**：执行线程先申请目录发现许可，再对每个claim＋executeOne申请独立许可；调度不预占、回调重查、不继承ThreadLocal、拒绝不跳过租户游标、异常不重发。首轮Dispatcher8/Work7/Traffic9/结构20通过；随后补HTTP不能经tick洗成宿主任务的第9项及真实MySQL排空夹具。下一轮验证当前项仍提交APPLIED、后续项保留QUEUED/PENDING，并回归原CacheTaskExecution/Native未知与fence协议；通过前不推进Mail接线。close仍是进程销毁兜底，受控等待/有序关闭在c2c另验。

**M3c2b1完成**：p08-m3c2b1-cache-mysql.log **33/33通过、0跳过、BUILD SUCCESS（15:22，11:59）**，包括新真实MySQL队列1、Dispatcher9、Work7、原任务执行7/原生资源9。真实应用装配中，第一项已领取后发起摘流，许可保持1；第一项仍提交SUCCEEDED/APPLIED和一次revision，计数归零后第二项仍QUEUED/PENDING，后续tick不再claim。原撤权、取消、竞争fence、失去提交确认、native UNKNOWN和旧进程约束继续通过；未修改任务/回执或自动重跑。当前制品SHA256=b7ab649474f54c926d6414424973f430c1405c841fe2b09aebe3f02c1743a1a7，验证进程已退出。

本轮完成c2a与c2b1，合并证据分轮记录（45核心/19HTTP/44调度/33数据库，不能相加冒充无重复全量）。README/部署/后端规则/二开手册和总排期同步。**下一项c2b2 Mail领取及实际发送生命周期**，之后其他维护/事件/独立定时器与WS；本机控制、HTTP异步跟踪、有序关闭及真实双JVM进程退出仍归c2c，当前没有整机受控排空或生产LB交付，multi继续关闭。用户服务和其他专项资源未操作。

### M3c2b2 Mail准入与传输生命周期（完成，本地协议与专用MySQL）

依赖已完成c2a/b1。拟改mail内部Dispatcher/Cycle/SmtpMailSender，新增内部许可汇合与真实线程完成跟踪；公开MailSendApi、领域事务/fence/UNKNOWN保持。目录发现、recover和每次claim分别在事务外准入，回调执行时重查，HTTP不能转换成宿主任务。许可同时等待结果处理与SMTP线程finally退出；提交前取消/线程池拒绝释放未执行分支，已运行的取消仅请求终止，不能提前归零。超时与DATA启动互斥，防止超时返回RETRYABLE后才开始发送。

验收顺序：①调度拒绝/队列后摘流/逐项领取/在途结束/提交失败不重发，SMTP饱和/开始前取消/忽略中断直到真实退出/跨线程重复完成及原TLS协议；②P08专用MySQL库的真实队列摘流、原MailOutbox和IdentityEmailDelivery回归，以及模块/架构门禁。每步记录实际命令/结果，失败不推进。SMTP只连本机合成协议夹具，不宣称供应商投递或整机排空通过。

c2b2首轮p08-m3c2b2-mail-unit.log **53/53通过、0跳过、BUILD SUCCESS（15:48，01:42）**：Dispatcher9、TransportLifecycle8、Smtp5/Network4、DeploymentWork7、架构/普通端点/模块20。忽略中断的DNS调用与已取消Future保留活动许可，线程实际退出才释放；提交前取消/饱和/非法调用不泄漏，DATA与超时竞争互斥，原TLS/STARTTLS/证书/拒收/未知协议通过。补真实peer断连及新MySQL两租户在途ACCEPTED/UNKNOWN、后续留队夹具后，开始原MailOutbox/IdentityEmailDelivery与当前协议复验；本子项尚未完成，不进入下一worker。

**M3c2b2完成**：p08-m3c2b2-mail-mysql.log **55/55通过、0跳过、BUILD SUCCESS（15:56，06:20）**，包含真实MySQL摘流1、原MailOutbox15、IdentityEmailDelivery6、Dispatcher9、TransportLifecycle8、Smtp5/Network4、DeploymentWork7。真实应用装配中，两租户在途任务分别提交ACCEPTED/UNKNOWN，两个后续任务仍QUEUED；已提交结果不让尚未结束的传输分支过早释放许可，完成后计数归零，后续调用不再发送。领域数据库夹具采用受控传输替身；另一本机TLS夹具确认超时UNKNOWN、实际发送线程退出与peer观察到断连，不能混称真实供应商投递。

当前制品`backend/target/p08-m3c2b2-mail-mysql/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar`，SHA256=`6c2768eaf229cc013680540eef6bb125c1c7350335f0760c357276ff2478bed2`。两轮均经根`tools/dev/verify-backend.ps1 -Standard`，分别使用`-Dsma.verify.directory=target/p08-m3c2b2-mail-unit/target`、`target/p08-m3c2b2-mail-mysql/target`和同名日志；`-Dtest`对应上文列出的测试类，`-Dsurefire.failIfNoSpecifiedTests=false`仅避免其他reactor模块没有该专项时报错。MySQL仅P08专用`sma_test_p08_local`，未操作用户库/服务。验证会话已退出，原MailSendApi/数据库结构及UNKNOWN不自动重发规则不变。

README、后端规则、部署说明、Mail二开手册与总排期同批同步。**下一项c2b3维护任务**，需覆盖Auth会话/密钥维护、Identity登录/邮件/重新认证、OpenAPI nonce，然后持久事件/消息信号/独立定时器/WS；不能只加调度入口检查而漏过排队回调和逐项事务。c2c本机控制/HTTP异步/有序关闭和真实双JVM退出、c3代理/M3d LB及S6/X5仍待验收，multi继续关闭。

### M3c2b3 安全与身份维护准入（完成，专用MySQL）

依赖已验收c2b2。路径为Auth SessionMaintenance、OpenAPI NonceMaintenance、Identity Login/EmailMaintenance及其测试；不修改数据结构、TTL/过期与身份授权算法。目录发现独立许可；每个有界清理事务、重加密事务分别准入，许可到事务提交/回滚后结束。排队不预占，执行线程不继承请求上下文；严格host入口包含直接cleanTenant，不能把匿名HTTP/其他租户job转换为宿主维护。拒绝时保留游标，资格恢复可重新扫描；Auth清理后摘流不能再开始重加密，Identity邮箱清理后不能再开始重新认证清理。

验证顺序：①四维护器现有目录回归，新增启动/资格拒绝、恢复游标、排队后摘流、逐事务拒绝、在途提交及异常释放、HTTP/身份/事务边界；②P08专用MySQL真实摘流与原Session/Nonce清理、Identity登录/邮箱/重新认证持久协议，结构/模块门禁。SessionEndedEvent持久消费者属于后续c2b4，当前只证明发布事务完成，不能声称异步监听已经排空。验证后双写状态，再推进下一项。

c2b3首轮p08-m3c2b3-maintenance-unit.log **46通过/1失败、0跳过，BUILD FAILURE（16:05，01:37）**。失败为IdentityEmailMaintenanceTests恢复阶段用when(store.maintain())重设返回时意外执行先前Answer，导致在测试准备期断言active=1；改用doReturn避免执行，不放宽实际维护期间断言或生产门禁。Auth/Nonce/IdentityLogin与结构/模块已通过，本轮仍保留失败记录，完整47项复验通过前不进入MySQL。

c2b3修复复验p08-m3c2b3-maintenance-fixed.log **47/47通过、0跳过、BUILD SUCCESS（16:07，01:48）**：四维护器各5、DeploymentWork7、结构/普通端点/模块20。确认请求/身份/外层事务不能进入、排队回调重查、许可覆盖commit、两个领域事务间停止、恢复不跳租户及异常归还；首轮失败保留。开始专用MySQL SecurityMaintenanceDrainTests、原SessionMaintenance/Sweep/Nonce与IdentityLoginTransaction/EmailChallenge/Reauthentication回归；未完成前不标c2b3或推进持久事件。

**M3c2b3完成**：p08-m3c2b3-maintenance-mysql.log **55/55通过、0跳过、BUILD SUCCESS（16:18，10:27）**。包含SecurityMaintenanceDrain1、SessionMaintenance/Sweep3、NonceMaintenance2、IdentityLoginTransaction8/EmailChallenge8/Reauthentication6、四目录维护器20及DeploymentWork7。真实MySQL事务在提交前保持两个活动许可；摘流后当前会话/nonce清理均成功提交，随后两个新执行单位拒绝、下一租户过期和有效记录均保持，当前租户仅删除已过期行，活动计数归零。原200行上限、SKIP LOCKED、并发清理一次、租户范围和身份挑战/授权过期/保留预算协议回归通过。SessionEndedEvent在本夹具使用捕获发布器，未把异步消费算入已排空。

两次成功命令均为根`tools/dev/verify-backend.ps1 -Standard`，输出目录分别`-Dsma.verify.directory=target/p08-m3c2b3-maintenance-fixed/target`和`target/p08-m3c2b3-maintenance-mysql/target`，`-Dtest`对应上文测试类，并设置`-Dsurefire.failIfNoSpecifiedTests=false`供其他reactor模块使用；日志同前缀。当前制品`backend/target/p08-m3c2b3-maintenance-mysql/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar` SHA256=`698576cc02b286b80284ec9466257a9eefb8a45359470174480a93be5b06b8f6`。只使用P08专用`sma_test_p08_local`，进程已退出；无生产配置/数据结构变更，用户服务及并行专项资源未操作。

README/后端规则/部署说明和总排期同批更新。**下一项c2b4持久事件**：已定位Audit Operation/Login、Auth UserSession、Message Socket四类监听器（7个方法），ApplicationModuleListener的事务在方法体之前开启，必须在事务前准入、拒绝留持久失败/待处理，实际框架重投须单独验收；不能在方法体申请后发现已有事务而放宽DeploymentWork。随后消息信号/StoragePoolRegistry/Extension独立定时器/WS及调度清单，再c2c实际进程排空、c3/M3d/S6/X5。multi仍关闭，局部维护测试不替代完整LB与登录连续访问矩阵。

### M3c2b4 持久事件准入（完成，真实框架与专用MySQL）

依赖c2b3完成；范围Common内部deployment Advisor/测试、监听器契约架构门禁和文档。当前7个生产ApplicationModuleListener均为单参数void同步方法体，异步由Spring代理执行、事务由框架开启。已读取本机Modulith2.1.2实际字节码，CompletionRegisteringAdvisor顺序HIGHEST_PRECEDENCE+10，先标处理中、proceed后标完成，异常标失败；因此不能在其外部直接因未就绪抛错，也不能只在方法体申请事务外许可。

方案：外层+5在实际执行线程取得许可（或记录拒绝），作用域覆盖完成/失败元数据落库；内层+20位于框架完成跟踪之内、领域事务之前，未获准则抛固定无载荷异常交回Modulith标FAILED。许可只在外层finally关闭，嵌套调用用线程作用域栈恢复且拒绝在已有事务/HTTP/身份上发起消费。自动覆盖ApplicationModuleListener，不增加需人工逐个记住的注解，不改变持久listener ID。只支持当前单参数void监听契约，返回Future/内部脱离式异步必须另行设计，不能假装已排空。拒绝路径仅允许框架标记失败这类技术收尾，不执行业务事务；整机关闭仍须等待事件执行器，不能把未获准事件的技术收尾漏在c2c之外。

验证顺序：①真实Spring Advisor排序/异步执行、准入/拒绝/嵌套/异常释放、7方法清单与新增监听契约；②专用MySQL＋真实事件注册/完成失败/重投，证明未获准无领域写、已准入可提交且许可覆盖框架结果记录、恢复后按原ID重投；回归Publication/治理、LoginLog/审计、Auth会话与Socket。首项通过并记录后才开始数据库项，整机multi与其他worker不在本项完成范围。

c2b4首轮p08-m3c2b4-event-unit.log **33/33通过、0跳过、BUILD SUCCESS（16:28，01:31）**：DeploymentEventTests5、DeploymentEventBoundary1、DeploymentWork7和结构/普通端点/模块20。用真实Async/Completion/Transaction Advisor证明排队不占许可、拒绝进入框架FAILED且不建领域事务，许可覆盖commit/rollback/完成记录，嵌套作用域恢复及请求/job/外层事务拒绝。契约门禁枚举当前7方法，禁止非void、多参数、final/static和非REQUIRES_NEW监听。开始真实应用/MySQL验证代理排序、拒绝不写审计、同publication ID重投、领域已提交而完成记录尚未结束时仍计数；原消费者另同轮回归，当前子项仍进行中。

c2b4首轮宿主p08-m3c2b4-event-mysql.log **10通过/6失败、0跳过，BUILD FAILURE**：审计、Socket及Publication重投失败，真实代理顺序断言已通过且拒绝记录FAILED成功。栈确认默认task执行器使用Micrometer ContextSnapshot包装，正常请求/重投身份传播到工作线程触发host拒绝；手工ProxyFactory单元没有此宿主传播行为。修复扩展为显式专用`@Async("deploymentEventExecutor")`：Common提供4线程/128队列、AbortPolicy、禁止继承请求ThreadLocal、无ContextSnapshot装饰、非默认候选的执行器，现有7方法与测试重投监听显式指定；架构门禁保证未来监听不漏配。不放宽host或盲目清空执行线程身份，不替换其他默认异步消费者。先复验真实线程池隔离/容量和原33项，再完整原失败矩阵＋其余6监听拒绝重投；当前未完成。

执行器复验首轮p08-m3c2b4-event-executor.log在新增测试lambda结束括号处编译失败，未执行测试；修正为`});`后使用同一独立输出目录、另记p08-m3c2b4-event-executor-fixed.log，原失败日志保留。未跳过当前门禁启动数据库回归。

c2b4执行器修复p08-m3c2b4-event-executor-fixed.log **34/34通过、0跳过、BUILD SUCCESS（16:41，01:35）**：真实线程池4个工作线程＋128队列，继承RequestAttributes/自定义ThreadLocal及认证身份不进入执行线程，满载明确拒绝且不在调用线程执行；原Advisor6/监听契约1/Work7/结构20通过。开始p08-m3c2b4-event-fixed-mysql：保留原所有失败消费者，增加DeploymentEventConsumersTests覆盖其余6个生产监听的拒绝无写和同ID重投；登录监听另验领域提交与框架完成记录之间的许可保持。当前仍未完成。

p08-m3c2b4-event-fixed-mysql.log **编译失败、测试未执行（16:43，22秒）**：并行新增StorageSwitchState依赖的StorageDirectory.bindingForSwitch当时缺失；只读复核当前StorageDirectory第150行已具备该方法。未改动租户专项源码，使用全新p08-m3c2b4-event-current-mysql输出目录重新构建，同一完整专项集复验。原日志保留，当前子项仍进行中。

p08-m3c2b4-event-current-mysql.log **14通过/4错误、0跳过，BUILD FAILURE**：专用执行器让原LoginLog/OperationLog、SessionManagement和真实Socket全部恢复。六消费者测试在断言阶段用了event-test job名，被原SocketSignalStore的message-signal/message-socket白名单拒绝；仅修正测试job名。另三个失败均在显式重投后等待完成，读取当前Modulith DefaultEventPublicationRegistry字节码确认：FailedEventPublications的batchSize=1在全局FAILED查询先LIMIT1，再执行ID过滤，队列前有其他失败记录时所选事件永远未提交。新增摘流产生多条FAILED揭露了原PublicationService缺陷，不能删测试失败记录掩盖。

修复扩展到Monitor PublicationService：使用公开IncompleteEventPublications精确谓词（指定ID＋FAILED＋当前租户），仍由框架markResubmitted原子状态竞争/实际监听器分发，不复制内部协议或直接调用监听器；入口原授权/失败状态/租户与HTTP原生请求治理保持。补PublicationTests较早外租户FAILED仍保留、所选ID完成回归；框架该API读取未完成集合，沿用现有管理查询的集合扫描边界，不能宣称新增分页/全局并发上限。先前全局maxInFlight=1本身也不是跨节点互斥；单事件执行权仍由框架CAS。准备全套复验，不推进后续。

p08-m3c2b4-event-retry.log **37通过/1失败、0跳过，BUILD FAILURE**。新六消费者拒绝/同ID重投通过，原LoginLog3/OperationLog1/SessionManagement1/Socket1、Publication2（含较早外租户FAILED）/HTTP治理1、Advisor6及结构22通过。唯一失败是DeploymentEventMysqlTests在registry.markCompleted的spy内部断言无事务；实际框架该方法自身由事务代理包装，领域事务已先提交，当前是完成记录的独立事务。修改该断言为事务存在，仍由主测试线程独立读取领域行确认已提交，并保持许可1、完成标记尚无、摘流后待投拒绝等实质断言。生产代码无再改；聚焦复验该项、六消费者和原重投/HTTP治理，未通过不标完成。

**M3c2b4完成**：p08-m3c2b4-event-final.log **12/12通过、0跳过、BUILD SUCCESS（17:18，07:13）**：DeploymentEventMysql1、Consumers1、Advisor6、监听契约1、Publication2及HTTP治理1。真实宿主代理顺序为Async→生命周期→Modulith完成跟踪→准入→领域事务；专用sma-events线程无请求/认证身份。七个生产方法未获准均保留FAILED且无领域写，恢复后指定原publication ID完成；其他租户更早FAILED保持不动。登录审计行已由独立读连接看见，但框架完成事务刻意未结束时许可仍1；此时摘流拒绝后续事件，放行完成记录后许可归零，原事件不重发。最后修正的是测试对框架独立事务的认识，没有放宽生产准入。

此前event-executor-fixed34项通过提供实际4线程/128队列隔离与容量证据；event-retry37项通过涵盖原LoginLog/OperationLog、SessionManagement、实际Tomcat/WebSocket通知及强退、结构/模块/端点与重投治理，其唯一失败已由event-final修复复验。中途生产上下文传播问题、精确重投缺陷、测试笔误及并行编译失败全部保留，不相加冒充无重复全量/首次通过。当前制品`backend/target/p08-m3c2b4-event-final/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar` SHA256=`d1786f55a67fe7f97fea08d9afd4e265115e797a524db253f9ad9e3aa241c613`。

所有构建均经根`tools/dev/verify-backend.ps1 -Standard`，`-Dsma.verify.directory=target/<上述日志同前缀>/target`（executor-fixed复用event-executor目录）、`-Dtest`对应各轮列明的测试类，并保留`-Dsurefire.failIfNoSpecifiedTests=false`供其他reactor模块使用。数据库只用P08专用`sma_test_p08_local`，验证会话已退出，未改用户服务/生产库，也未接管并行租户改动。Monitor精确重投仍读未完成集合，性能边界未升级为数据库分页或全局并发互斥。

README/后端规则/部署及二开缓存手册、总排期同批同步。**下一项c2b5消息信号处理**，随后StoragePoolRegistry、Extension独立定时器、WS与调度/异步清单。c2c须等待专用事件执行器连同未准入事件的框架技术收尾，再关闭数据库；当前工作数0不等于队列/HTTP/WS已排空。c3可信代理/M3d真实LB、S6/X5仍待验证，multi不开放。

### M3c2b5 消息信号准入（完成，本地MySQL与Socket）

P08 M3c2b5开工：SocketSignalProcessor按订阅发现、信号读取、每条实际推送/会话重验、目录发现和过期清理分别事务外准入。严格host入口，排队不预占、执行回调重查、不继承ThreadLocal；拒绝不记observed、不跳批次/维护游标，已进入的同步推送收尾。先专项拒绝/恢复/队列/在途/异常测试，再专用MySQL真实信号保留与原Socket/Store回归；SocketHub连接关闭、ping和底层缓冲发送生命周期归后续WS子项，本项不称整机排空。
验证顺序：单元/结构先通过并记录，再真实MySQL信号读取与摘流后保留、原SocketSignalStore及真实Socket推送回归；失败不推进。数据结构/信号合并/授权仍保持，普通hint不是持久投递确认。

M3c2b5首轮p08-m3c2b5-signal-unit.log **37/37通过、0跳过、BUILD SUCCESS（17:25，01:32）**：SignalProcessor10、Work7及结构20。验证排队不占许可/执行重查，读后拒绝保留当前批次，当前推送结束前计数不归零，下一条停止，清理拒绝不推进游标/截止，以及真实线程上下文隔离。开始专用MySQL信号保留与原Store/真实Socket回归，本项未完成前不推进其他worker。

**P08 M3c2b5完成**：p08-m3c2b5-signal-mysql.log **24/24通过、0跳过、BUILD SUCCESS（17:32，05:55）**，SignalDrain1/Processor10/Store5/真实Socket1/Work7。真实MySQL两条已提交信号读取后停在第一条Hub调用，摘流保持许可1，释放后只完成一条、下一条及维护不启动，计数归零；原信号ID/版本与portal隔离保持。该用例Hub为受控替身，另一个真实Tomcat/WS用例验证通知/待办/私信/强退，不混称实际连接已排空。首轮37项含结构/模块门禁，分轮证据不相加冒充无重复全量。

当前制品backend/target/p08-m3c2b5-signal-mysql/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar SHA256=bc2d6166bf04a0e9749b40e67fca7902f6133a72584b5b7e630b7bcc1f500597；两轮根tools/dev/verify-backend.ps1 -Standard，输出目录target/p08-m3c2b5-signal-unit/target与target/p08-m3c2b5-signal-mysql/target，-Dtest对应上述类，其他reactor模块用-Dsurefire.failIfNoSpecifiedTests=false。只用P08专用sma_test_p08_local，测试进程已退出，未操作用户服务。README/后端规则/部署/方案和两份进度同步。

下一项c2b6存储相关后台调度：最新源码新增StorageOutboxWorker、StorageDrainWorker，需与StoragePoolRegistry一同审查业务消费和基础排空确认的归属，不能照旧清单漏掉。随后Extension独立定时器、SocketHub ping/缓冲/连接关闭及清单门禁，c2c实际进程排空、c3/M3d/S6/X5仍未完成，multi不开放。

### M3c2b6 存储相关后台调度（完成，独立业务MySQL）

P08 M3c2b6开工：StorageOutboxWorker业务消费停止新领取，改独立单worker有界调度、host/回调准入；StoragePoolRegistry目录发现与逐项deliverOne/定时evictIdle事务外申请许可，覆盖消费者真实返回、结果提交及池租约释放。拒绝不推进资源游标，当前执行结束后再归还，原至少一次/UNKNOWN租约重投和fence保持。StorageDrainWorker是存储切换的技术排空确认例外，摘流时继续扫描既有DRAINING任务，不提交切库/执行业务；加强host入口与预算测试，c2c仍须等协调线程结束才销毁DB。池在途会话释放/关闭兜底不能因摘流跳过。先单元/结构验证，再独立P08物理业务库真实Outbox在途完成/后续保留及默认宿主兼容；不使用并行P10/P11现有库。

M3c2b6首轮p08-m3c2b6-storage-unit.log **41/41通过、0跳过、BUILD SUCCESS（17:40，01:38）**：Outbox调度5、Drain协调2、Pool5、SessionTracker2、Work7及结构20。证明拒绝/恢复游标、8资源预算、独立执行线程、在途消费保留池租约直到返回、异常归还、定时回收资格与关闭释放区别。开始专用P08物理库sma_test_p08_storage_drain_20261112（13306，本轮新建）真实Outbox UNKNOWN/在途DONE/后续PENDING和默认宿主兼容验证；不复用并行P10/P11测试资源，未完成前不推进后续。

验证命令核对：storage-mysql列表中的SpringbootModulithAdminApplicationTests不是仓库实际类名，实际为ApplicationSmokeTests；该轮不计默认HTTP冒烟证据。真实Outbox用例仍正常运行，结束后顺序补跑正确ApplicationSmokeTests，不并发清理平台库、不把未选中测试记为通过。

storage-mysql首轮 **21通过/1错误、0跳过，BUILD FAILURE**：新StorageOutboxDrainMysqlTests夹具请求键p08-drain-0短于RequestCommand要求16位，入队前失败；只读确认业务Outbox仍0行。改测试键为p08-storage-drain-*并显式配置测试指纹密钥，生产协议不放宽；使用新日志复验真实Outbox与正确ApplicationSmokeTests，保留首轮失败记录。

storage-fixed **23通过/1错误、0跳过**：正确ApplicationSmokeTests2/2已通过，其余单元通过；新Outbox夹具仍有单独eventKey短于16位，原请求键修复不覆盖事件键。只读确认Outbox0行、请求账本1条，保留FAILED_FINAL证据，修正事件键并为后续合成用例统一生成32位随机前缀（新测试意图），不清除失败回执、不放宽生产校验。只复验当前失败真实Outbox及相关调度/池测试，默认宿主已取得本轮证据。

**P08 M3c2b6完成**：p08-m3c2b6-storage-final.log **22/22通过、0跳过、BUILD SUCCESS（18:18，11:13）**，真实Outbox1、Outbox调度5、Drain协调2、Pool5、SessionTracker2、Work7。真实物理业务库先一次消费失败保留UNKNOWN；第二条执行期间摘流保持许可1，完成后DONE，第三条PENDING。把原UNKNOWN租约调为已过期后，本机仍不再领取；直接读库核对state=UNKNOWN/DONE/PENDING、attempt和fence=1/1/0。当前任务正确恢复可信tenant，消费者不占数据库事务，finish之后计数归零。保留原FAILED_FINAL夹具回执1条、新成功入队回执3条，未删证据。

分轮证据为首轮41项单元/结构和storage-fixed默认ApplicationSmokeTests2项通过，加本轮22项；中途请求键/事件键夹具失败及错误冒烟选择均保留，不称首次全绿或相加冒充无重复全量。制品backend/target/p08-m3c2b6-storage-final/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar SHA256=4e08e32e02cb13d533b140fb8b496a19069ea29d665123a98bb06e8a560854cf。命令均经根tools/dev/verify-backend.ps1 -Standard，-Dsma.verify.directory=target/<各日志前缀>/target、-Dtest按各轮上述类、-Dsurefire.failIfNoSpecifiedTests=false供其他reactor模块使用。

数据库仅P08的sma_test_p08_local与本轮新建sma_test_p08_storage_drain_20261112（127.0.0.1:13306），后者保留合成状态用于核对；测试进程已退出，未动用户服务/P10/P11库。README/后端与租户规则/部署/二开说明及两份进度同步。StorageDrainWorker只是原技术确认例外，未宣称真实多实例切库通过；业务会话/Outbox与切库的联合证据仍归X5。

下一项c2b7插件独立定时器与运行时同步，随后WS连接/ping/缓冲发送和调度清单；c2c本机控制/HTTP异步/有序退出、c3代理/M3d LB/S6/X5仍待完成，multi继续关闭。

### M3c2b7 插件同步与后台任务（完成，本地JAR与MySQL）

P08 M3c2b7开工：ExtensionRuntimeService.synchronize每个控制同步周期在执行线程、事务外申请许可，覆盖信任/撤销/租约/准备/激活/回收及权限登记的整个同步调用；摘流后不开始新周期，当前周期继续收尾。PluginRuntime调用同步执行、入口检查租约，在途实例持有inFlight引用且没有宿主Future提前返回，因此停止新同步不会中断已有execute；未开始调用遇到租约过期仍按原协议拒绝。后台invokeJob严格host并在policy.resolve之前准入，许可覆盖journal领取、同步插件执行和结果提交；不修改HTTP同步调用原入口，HTTP生命周期由c2c另验。独立scheduler禁止继承ThreadLocal，销毁前禁止再启动runtime。先调度/服务单元与真实插件JAR回归，再默认宿主及原journal持久协议验证；无外部发布/部署消息，不使用用户插件服务。

c2b7首轮extension-unit：真实PluginRuntimeTests9/9通过；宿主37项中35通过/2失败，空信任数组夹具被原parseTrust非空校验拒绝，未进入控制I/O。修正夹具为临时Ed25519公钥/有效期限/用途，不放宽生产信任校验，复跑相同专项后再进入数据库。真实JAR用例已证明租约过期拒绝新调用、已进入宿主能力的原调用保留inFlight并完成。

M3c2b7修复后专项 p08-m3c2b7-extension-fixed.log：真实PluginRuntimeTests **9/9**、宿主单元/架构/模块/端点 **37/37通过，0跳过，BUILD SUCCESS**。有效临时公钥夹具已进入控制I/O，验证拒绝新同步/新job、在途I/O及结果提交期间许可保持、线程上下文隔离、UNKNOWN不重复执行；下一步使用P08专用平台MySQL验证原持久回执与默认宿主。本项尚未完成，不推进WS。

c2b7夹具复核：ExtensionJobDrainMysqlTests原制品摘要与旧ExtensionWorkJournalTests重复；平台引用计数是全局技术状态，测试顺序可能互相干扰。当前mysql进程使用编译时的旧夹具继续完成，源码已改为专项独立摘要并把引用断言收紧为精确1；该源码必须另以相反类顺序复验后才能标完成，生产代码无变化。

c2b7 p08-m3c2b7-extension-mysql.log **7/7通过，0跳过，BUILD SUCCESS（18:44，11:47）**：默认ApplicationSmokeTests2、原ExtensionWorkJournalTests4、新JobDrain1；拒绝后恢复读取原UNKNOWN/claim/版本及引用通过。此轮旧夹具按原journal先执行顺序通过，不能替代独立摘要修订；接着使用reversealphabetical顺序执行新JobDrain再原journal，验证平台引用不受顺序污染。未开始WS。

**P08 M3c2b7完成（本地）**：p08-m3c2b7-extension-final.log **5/5通过，0跳过，BUILD SUCCESS（18:53，08:13）**，reversealphabetical实际先执行ExtensionJobDrainMysqlTests1，再ExtensionWorkJournalTests4。新夹具专属摘要保留原UNKNOWN、同一claim/版本与精确1条持久引用，原回执固定版本/唯一claim/权限/对账全部通过；独立SQL读取核对tenant97290为UNKNOWN1条、claim仍存在，专属摘要reference_count=1。生产逻辑无需因夹具隔离而修改。

分轮证据：extension-fixed真实PluginRuntimeTests9/9、宿主单元/结构37/37；extension-mysql默认ApplicationSmokeTests2＋原journal4＋摘流1共7/7；extension-final反向顺序5/5。不相加冒充无重复全量，首轮信任夹具失败及摘要隔离修订均保留。最终制品backend/target/p08-m3c2b7-extension-final/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar SHA256=9d695f4b2644eb0bf81fdf3e02718a77d58fabcf583c1f35fc8600721e12dc7c。

命令均经根tools/dev/verify-backend.ps1 -Standard，-Dsma.verify.directory=target/<对应日志前缀>/target及-Dsurefire.failIfNoSpecifiedTests=false；final指定-Dtest=ExtensionJobDrainMysqlTests,ExtensionWorkJournalTests和-Dsurefire.runOrder=reversealphabetical，mysql额外ApplicationSmokeTests，fixed专项列表见原记录/日志。只用P08专用sma_test_p08_local（13306），测试进程已退出；未接触用户插件控制服务/用户进程/P10/P11库。7个范围源码摘要核对无漂移，9份文档225条本地链接检查通过，范围git diff --check通过。

README、后端/插件规则、插件接入/二开缓存/部署手册及两份进度已同步。升级说明明确SNAPSHOT后台入口收紧：事务内/附身份旧调用须由业务持久意图交给可信host worker，不能清空身份绕过；外部旧业务制品兼容矩阵未验收。控制I/O受控替身、真实签名JAR和MySQL分别提供证据，不等于真实HTTPS控制服务或整机关闭。

下一项c2b8：SocketHub连接准入、ping/缓冲发送生命周期、1001部署关闭与会话保留/重新取票，再补调度及异步入口完整清单。c2c本机控制/HTTP异步/有序停机、c2d组合审计、c3代理/M3d无粘性LB、S6和X5仍未完成；multi保持关闭。

P08 M3c2b8开工：Common新增只读DeploymentConnections生命周期观察SPI，Message接入握手预留→连接交接→所有发送/关闭操作计数，握手失败释放、升级成功回调交接且晚到回调重查摘流；拒绝新握手不消费票据。沿用Spring有界并发发送装饰器，统计全部同步调用以覆盖其缓冲刷新线程，不能只跟踪信号发送者。独立单worker负责每连接ping准入与技术关闭，摘流停止新业务发送，等待原发送后1001关闭，不发送session-ended/不注销共享会话；计数等真实回调和I/O结束，不超时清零。拟改common/deployment公开只读契约、message SocketHub/握手适配及测试；前端沿用现有重新取票/REST刷新逻辑，补契约测试。先单元/握手/结构，再专用MySQL真实Tomcat/原票据及消息回归，再更新双进度；实际双JVM滚动停机归c2c，不以本项替代。

c2b8首轮p08-m3c2b8-socket-unit.log **44通过/1夹具错误，0跳过**。Hub生命周期6、原信号10、Work7及结构20通过；握手用例重设when时调用旧throw stub，改doReturn。复核另补每轮128项轮转队列，失败关闭不得饿死后续连接；晚到升级除摘流外还重验资格，资格不足1001且不查会话。新增预算/晚到资格测试后复验当前专项，不推进数据库。

c2b8 socket-fixed测试编译失败：并行安全专项SecuritySignalCaptureService增加CacheNodeIdentity参数时，测试仍为旧构造调用；未执行测试。只读确认当前SecuritySignalCaptureTests已同步双参数构造，保留并行修改，使用新输出socket-current重新构建。前端消息状态12/12通过，新增1001保留token/新票据/重连REST刷新，前端运行逻辑无改动。子项仍进行中。

c2b8 socket-current **47/47通过、0跳过、BUILD SUCCESS**，Hub8/握手2/原信号10/Work7及结构20。另复核最终销毁与晚到升级竞争：原已预留握手若在worker关闭后才到连接回调，仍需主动1001关闭，不能只标记等待已停止worker；补finally关闭兜底及专门测试。先聚焦复验Hub/握手，不把前一轮制品当作这次小修已验证。前端12项及type-check:tests通过。

c2b8 socket-late **11/11通过、0跳过、BUILD SUCCESS**：Hub9＋握手2，销毁后晚到升级仍1001关闭、预留交接/真实回调计数通过。进入P08专用MySQL真实Tomcat 1001且共享会话保留、拒绝新握手不消费票据、原推送/强退及票据一次消费回归；本子项未完成前不推进调度清单/整机停机。

c2b8前端完整npm run verify通过：62文件/318项测试，类型、测试类型、lint、i18n、路由/基线、Vite构建和release预算均通过，日志backend/target/p08-m3c2b8-frontend-verify.log；本轮前端仅新增测试，保留并行安全身份修改。后端真实MySQL验证仍进行中，不用模拟WebSocket测试替代真实关闭/共享会话证据。

**P08 M3c2b8完成（本地Tomcat/MySQL）**：p08-m3c2b8-socket-mysql.log **14/14通过，0跳过，BUILD SUCCESS（19:28，11:25）**：SocketDeploymentMysql1、SignalDrain1、Store5、Ticket6、原Socket1。真实JDK客户端收到1001，Hub等待实际afterConnectionClosed后计数归零，无session-ended；原登录仍能resolve并发行新票据，摘流节点拒绝新握手且未消耗该票据，随后原协议一次消费成功/重复拒绝。原通知/待办/私信/强退及共享票据竞争、过期/会话绑定全部回归。此轮是单机单宿主真实连接，新票据在服务层核验，不称另一真实节点已重连或实际进程滚动停机。

分轮证据：socket-current **47/47**含Hub8/握手2/信号10/Work7/结构20；销毁后晚到升级小修socket-late **11/11**含Hub9/握手2；真实mysql **14/14**；frontend-next完整npm run verify **318/318**及类型/lint/i18n/构建/release通过，messages.test.ts12项含新增1001保留token/退避/新票据/REST刷新。保留首轮Mockito夹具错误及并行安全构造器编译失败，不相加冒充一次完整后端回归。

最终JAR：backend/target/p08-m3c2b8-socket-mysql/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar，SHA256=9fbf1b3d669b8191489738e25226ed74f282d327349c05331fbe4458acbe5199。后端均经根tools/dev/verify-backend.ps1 -Standard，-Dsma.verify.directory=target/<日志前缀>/target、-Dtest按各轮上述测试类、-Dsurefire.failIfNoSpecifiedTests=false。前端日志backend/target/p08-m3c2b8-frontend-verify.log。只用P08平台库sma_test_p08_local（13306）；8个范围源码摘要未漂移，测试JVM43696和最后随机端口55955已退出；用户服务、其他专项库及并行改动保持。

规则/方案/二开手册明确：DeploymentConnections为只读观察SPI，握手/连接/在途I/O分别计数；101保留预留到回调交接，关闭返回和超时都不能清零。正常ping逐连接准入，独立worker每250ms轮转最多128项；技术关闭继续运行，失败关闭保留且不会饿死后续连接。原Spring 64KiB缓冲/5秒观察限额保持，所有发送线程覆盖计数；不是客户端送达确认或严格网络时限。10000连接无阻塞轮转约20秒，实际宽限和执行器结束仍须c2c验收。

下一项c2b9调度/异步完整清单及遗漏收口，尤其文件检查/安全采集/规则执行等并行新增来源；之后c2c本机控制、HTTP异步及有序停机，c2d组合审计、c3/M3d代理/LB、S6与X5。multi仍拒绝启动，本项未重启/部署用户服务。

c2b8收口核对：独立SQL确认tenant98740原auth_session共1条，未删除1、未锁定1、仍未到期1（不读取token/hash）。8份文档233个本地链接有效；范围git diff --check通过，8个源码/测试文件摘要无漂移。README、后端/前端规则、部署/二开手册及双进度已同步，本地WS子项完成；全目标仍未完成。

P08 M3c2b9开工，按依赖拆分：b9a先建立编译后调度/异步/线程池/HTTP异步/子进程及外部规则执行入口的精确清单门禁与人工归属台账；台账显式区分已准入业务、技术协调、父请求同步、需补踪的子线程/进程及独立worker，不用登记代替验收。b9b逐项补Google/LocalTransfer/FileCluster等Future取消后仍存活的实际执行观察，Console异步响应和TLS超时线程另核验；b9c检查检查容器/子进程与规则引擎隐式并发；b9d同制品回归和清单收口。实际有序关闭及数据库/协调资源销毁仍归c2c。

本轮拟改backend架构测试/测试资源清单、docs/proposals/P08-deployment-activity-inventory.md及现有方案/双进度；b9a通过后再实施生命周期公共观察契约与逐个消费者。发现GoogleHttpTransport、LocalTransferStorage、FileClusterStorage在Future.cancel后方法可先返回，父许可/HTTP计数不能单独证明真实子线程结束；SecurityCollectionWorker已逐I/O单元准入；ContentInspectionWorker是独立进程入口，不能误作宿主定时器。并行安全/文件变更继续保留。

b9a首轮inventory-discovery编译成功，发现86条入口；空审查清单使门禁按预期失败（1通过/1拒绝），未自动批准。逐项核对后剔除把AbortPolicy误当执行器的噪声，新增自定义Executor继承识别、Caffeine/HttpClient/Hikari/Lettuce资源及异步返回类型，补真实编译夹具验证组合/重复注解、虚拟线程、并行流、子进程和异步HTTP可被发现；只做源码/字节码检查，夹具不执行线程或命令。待扩展发现结果人工登记后复验，再推进生命周期修复。

b9a扩展发现inventory-expanded：真实字节码检测夹具通过，空清单仍按预期拒绝（1通过/1拒绝）；审查后最终80条：Scheduled17、Async7、异步返回2、执行器20、提交18、HTTP4、规则库1、子进程1、独立线程1、资源9（含重复调用次数）。已逐类核对并登记新台账，明确Google/本地传输/见证子线程等未完成项，保留库自动线程与运行时调度核对边界。按审查结果写入固定基线，开始普通门禁及模块/架构复验，无自动批准开关。

**M3c2b9a完成（静态入口审查）**：p08-m3c2b9-inventory-reviewed.log **23/23通过、0跳过、BUILD SUCCESS**（Inventory2、持久事件清单1、架构17、模块2、端点1）。80条精确基线与人工归属台账一致；真实检测夹具验证了组合/重复Scheduled、组合Async、自定义Executor、虚拟线程/并行流、异步HTTP及子进程的发现。先前两轮因空审查基线拒绝为预期引导记录，未自动批准或放宽比较。此完成仅指入口发现/审查门禁，不表示b9剩余生命周期或整机排空通过。

进入b9b1公共实际任务观察：增加只读DeploymentActivity与有界DeploymentTaskTracker，登记提交/运行状态，Future取消前未启动可释放、已启动须等run finally，拒绝与shutdownNow取消队列不泄漏；不另建线程、不授予工作/租户权限、不替代父HTTP/DeploymentWork。先并发/取消/异常/关闭/容量专项和结构门禁，再接入LocalTransfer/FileCluster及Google等消费者，不提前声称消费者已修复。

**M3c2b9b1完成（公共观察原语）**：p08-m3c2b9-task-tracker.log **34/34通过，0跳过，BUILD SUCCESS**（Tracker5、Work7、Inventory2及结构20）。真实执行器证明运行中Future取消/close保持running，未启动取消及拒绝释放，shutdownNow移出队列结合close不泄漏，40轮启动/取消/关闭竞争无重复执行/负计数，成功/异常及重复run保持一次释放。清单新增唯一公共Executor.execute转交点，共81条。公共原语不创建线程、不赋权，消费者尚未接入不能据此称已修复。

下一b9b2先接入LocalTransferStorage：跟踪真实文件工作与资源finally，超时后的原不可变对象/未知账本保持；关闭后的Future取消保守映射原STORAGE错误而不是NotStarted。线程禁止继承调用者ThreadLocal，原maximumConcurrent/零队列/路径检查不变。验证真实文件锁、超时且忽略中断的线程仍计数、关闭/拒绝和原写入/冻结/隔离回归，通过后再处理FileCluster见证。

**M3c2b9b2-1 LocalTransfer完成（真实本地文件）**：p08-m3c2b9-local-transfer.log **34通过、0失败/错误、1跳过，BUILD SUCCESS（19:46，01:36）**，新Deployment2全部通过；旧Storage7中6通过、符号链接权限夹具1跳过，Windows Junction拒绝已通过。新用例证明方法超时后线程忽略中断仍保持running1及真实FileLock，close两次不清零；释放阻塞后才归零/释放锁，原不可变证据文件仍存在；未开始拒绝关闭输入且不创建对象。子线程无继承ThreadLocal/认证身份，原校验/写入/冻结协议保持。Tracker5/Inventory2/结构19同轮通过。

制品backend/target/p08-m3c2b9-local-transfer/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar SHA256=b368366b77338ec858609cd04b8c467df313abace561ff9323b0e2a98e2f9dfa。未执行数据库或HTTP联调，本项不改变数据结构/公开上传入口；未来整机关闭仍需c2c。顺手补静态门禁对“子类仅在类上加Async、全部继承父方法”的遗漏检测，真实继承夹具聚焦复验后再进入FileCluster；生产运行代码无再次修改。

b9继承检测补验：p08-m3c2b9-inventory-inherited.log **2/2通过，0跳过，BUILD SUCCESS**，类级Async即使只继承方法也会产生ASYNC_TYPE条目；当前基座仍81条。5个本项运行class（观察接口/快照、tracker/Task、本地传输）与已通过本地文件专项的编译字节完全一致，未因清单测试修订偷换运行证据。8份文档228个本地链接有效，范围git diff --check通过。

本轮后端命令均为tools/dev/verify-backend.ps1 -Standard，-Dsma.verify.directory=target/<对应日志名>/target、-Dsurefire.failIfNoSpecifiedTests=false；inventory-reviewed指定DeploymentActivityInventoryTests,DeploymentEventBoundaryTests,ArchitectureRulesTests,ModularityTests,SecurityEndpointInventoryTests；task-tracker指定DeploymentTaskTrackerTests,DeploymentActivityInventoryTests,DeploymentWorkTests,ArchitectureRulesTests,ModularityTests,SecurityEndpointInventoryTests；local-transfer指定LocalTransferDeploymentTests,LocalTransferStorageTests,DeploymentTaskTrackerTests,DeploymentActivityInventoryTests,ArchitectureRulesTests,ModularityTests；inventory-inherited仅DeploymentActivityInventoryTests。所有验证会话已结束，仅临时测试文件，无数据库/用户进程改动；原symlink跳过不冒充已测。

下一项b9b2-2 FileClusterStorage真实见证子线程，之后Google/Console/TLS的库活动和规则/独立检查边界；b9整体、c2c有序停机、c2d/c3/M3d/S6/X5未完成。公共观察不自动阻止业务工作，不替代父准入；multi继续关闭。

P08 M3c2b9b2-2开工：FileClusterStorage使用DeploymentTaskTracker观察内层见证线程，组件id=file.shared-witness；发布/核验/退役原协议保持，2线程零队列和原探针预算不变，工作线程禁止继承ThreadLocal。Future取消/组件关闭不能清除运行计数；取消保守映射原unavailable，不返回成功或重建不确定探针。只将内部execute开放为包内测试接缝并保留host入口校验，不构成二开API。先真实线程/文件锁超时及原独立JVM共享读锁/路径/摘要测试，再共享Probe/Runtime及专用MySQL互证回归；不改变SharedStorageRuntime/Store状态协议或用户挂载。

b9b2-2见证单元p08-m3c2b9-witness-unit.log **46/46通过、0跳过，BUILD SUCCESS（19:52，01:46）**：受控内部I/O夹具在方法超时后仍持真实FileLock/running1，close重复不清零；未开始取消/执行器饱和无探针落盘，正常和异常均实际归还，线程无继承身份。原独立JVM共享读锁/发布退役排他、摘要/目录链接拒绝及SharedStorageProbe/Runtime、入口清单/结构通过。下一步使用P08专用MySQL执行真实双runtime＋文件/持久互证及默认宿主兼容，不把单元计数通过当作完整互证已复验。

**M3c2b9b2-2 FileClusterStorage完成（本地）**：p08-m3c2b9-witness-mysql.log **4/4通过、0跳过、BUILD SUCCESS（20:03，09:47）**，ApplicationSmokeTests2＋SharedStorageRuntimeIntegrationTests2。真实文件与MySQL双runtime维持互证，丢失一侧probe后该侧UNAVAILABLE且持久代次推进/撤销资格，健康peer继续READY，原文件不重建；独立目录不能取得双向资格。此用例同JVM双runtime，不称独立双JVM滚动停机或生产NFS/SMB验收。

结合witness-unit **46/46通过、0跳过**：新Deployment3、原Storage7（含独立JVM共享读锁及Windows Junction）、Tracker5、Probe7、Runtime3、Inventory2及结构19。阻塞夹具调用内部实际执行接缝并持真实FileLock，方法超时/close后仍计running1，实际退出后才归零；原公开见证读写/锁/摘要另由原Storage专项验证，边界分别记录。无迁移/用户配置变更，技术见证继续服务摘流收尾，观察计数不授予资格。

最终制品backend/target/p08-m3c2b9-witness-mysql/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar SHA256=843c617047e8f70fc1ee9566c35e60ba71122e7dd3de0272ccd7544ca99b0083。两轮均根tools/dev/verify-backend.ps1 -Standard，-Dsma.verify.directory=target/p08-m3c2b9-witness-unit/target或target/p08-m3c2b9-witness-mysql/target，-Dtest按上述测试类，-Dsurefire.failIfNoSpecifiedTests=false。仅P08专用sma_test_p08_local（13306），测试JVM9080已退出；3个范围源码/清单摘要未漂移，未动用户服务/其他专项库。

README、部署/二开手册、后端规则、活动台账与两份进度同步。下一b9b3逐个核验Google/Console/TLS实际传输与库资源；随后规则/独立检查进程边界及b9收口。c2c整机控制/HTTP异步/有序关闭、c2d/c3/M3d/S6/X5仍未完成，multi保持关闭。

b9b2-2最终核对：7份文档225个本地链接有效，范围git diff --check通过；FileClusterStorage在单元与MySQL两轮编译字节一致。当前总目标继续进行，不把共享见证这一子项完成等同于完整multi可用。

P08 M3c2b9b3-1 Google传输开工：GoogleHttpTransport接入DeploymentTaskTracker，仍4线程零队列/12秒总等待/64KiB响应预算与固定Google端点；取消后实际任务仍计数，禁止继承调用者ThreadLocal。新增common::deployment的DeploymentTransport受控关闭契约：host停止新提交，执行器等待已接受任务，线程池真正terminated后才调用HttpClient.shutdown；transportTerminated同时核对线程池、任务和JDK客户端真实终止，不把任务0等同于库资源结束。PreDestroy仅保留强制取消兜底，不能假称优雅关闭。

公共tracker新增stopAccepting（只拒绝新提交，不取消已有任务），先与原取消/竞争单元验证，再Google受控忽略中断夹具、真实本地HTTP成功/慢正文取消/断连/优雅终止及原Google协议/IDToken回归；通过后再进原Identity真实MySQL HTTP/登录操作专项。控制契约无用户HTTP入口，内部关闭要求host/无事务，无外部Google账号调用；Console/TLS/NodeControl后续单独验收，当前不放开multi。

b9b3关闭契约前置p08-m3c2b9-transport-contract.log **34/34通过、0跳过、BUILD SUCCESS（20:09，01:51）**：Tracker6证明stopAccepting保留原队列/结果并拒绝新提交，原取消/竞争及Work7/Inventory2/结构19通过。DeploymentTransport契约只定义host关闭及真实终止观察，尚无消费者不能称传输已完成。进入Google接线及真实本地协议专项。

b9b3-1 Google专项p08-m3c2b9-google-unit.log **43/43通过、0跳过、BUILD SUCCESS**：HttpTransport5含真实请求优雅完成/客户端终止，Deployment3含忽略中断任务仍计数、host关闭拒绝与事务前置、真实慢正文取消后peer断连及JDK isTerminated；IdToken7、Tracker6、Inventory2及结构20。生产无跳转/无Cookie/4并发/64KiB/固定端点/原错误码保持。清单为82条（专属TransportExecutor构造链），新增库关闭不计作资格或请求成功。

进入P08专用MySQL的原IdentityLoginTransaction、IdentityGoogleOperation、IdentityApplicationHttp与默认ApplicationSmoke回归，验证原单次兑换/未知不重换/租户与HTTP契约及真实装配；这些集成使用原受控Google签名/传输夹具，不访问供应商。本项数据库通过前不推进Console/TLS。

Google关闭契约补充（仅Javadoc/文档，无运行行为修改）：宿主必须先停止新父请求/工作准入并等待已准入父流程完成，再调用shutdownTransport；同一父流程可能先兑换再取公钥，不能在其两次调用之间提前关闭传输。父方法超时后存活的子任务由tracker/客户端继续观察收尾；完整父HTTP/worker顺序仍归c2c。当前MySQL验证继续原编译代码，不因注释修改重启。

**M3c2b9b3-1 Google传输完成（本地）**：p08-m3c2b9-google-mysql.log **27/27通过、0跳过、BUILD SUCCESS（20:22，09:03）**，ApplicationSmoke2、IdentityApplicationHttp9、IdentityGoogleOperation8、IdentityLoginTransaction8。原state/proof/PKCE/租户/Origin/登录与重认证区分、单次兑换竞争、未知VERIFYING不重换、失败会话提交与配置变化保留原行为；真实MySQL和应用HTTP链通过，Google签名/传输为原受控夹具，无供应商账号访问。

分轮证据：transport-contract **34/34**验证stopAccepting保留已接受队列；google-unit **43/43**（Transport5、Deployment3、IdToken7、Tracker6、Inventory2及结构20）验证真实本地请求完成后才关闭客户端、慢正文取消后peer真实断连/JDK终止、忽略中断的替身任务仍计数、host关闭/事务门禁、4并发和原64KiB/无跳转/Cookie约束。不能将工作计数0当作客户端已关闭，transportTerminated同时核对真实库终止；PreDestroy仅强制取消兜底，不是完整滚动停机。

最终制品backend/target/p08-m3c2b9-google-mysql/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar SHA256=7d92cea5f4cfc304318025214932b3ee8de74105b1a7241558ed36d95a83387b。所有命令经根tools/dev/verify-backend.ps1 -Standard，-Dsma.verify.directory=target/<对应日志前缀>/target、-Dsurefire.failIfNoSpecifiedTests=false，-Dtest按上述各轮类（统一后缀Tests）。google-mysql精确列表IdentityLoginTransactionTests,IdentityGoogleOperationTests,IdentityApplicationHttpTests,ApplicationSmokeTests；google-unit精确列表GoogleTransportDeploymentTests,GoogleHttpTransportTests,GoogleIdTokenTests,DeploymentTaskTrackerTests,DeploymentActivityInventoryTests,ArchitectureRulesTests,ModularityTests,SecurityEndpointInventoryTests。

数据库仅P08的sma_test_p08_local（13306），测试JVM43060已退出；单元与MySQL两轮4个关键运行class字节一致。期间仅对DeploymentTransport补父流程关闭顺序Javadoc并记录，无行为漂移或重启验证。清单82条已人工审查/普通门禁验证，未自动批准。README/后端规则/部署及二开手册/活动台账与双进度同步，用户服务和其他专项库未改。

下一b9b3-2 Console代理传输，再TLS/NodeControl库资源及规则/独立检查边界；c2c父HTTP/worker与辅助传输的实际关闭顺序、c2d/c3/M3d/S6/X5仍未完成，multi继续关闭。

Google子项收口核对：7份文档227个本地链接有效，范围git diff --check通过；6个范围源码/测试/清单摘要与已记录最终版本一致。关闭契约注释修订已单独记录，未修改测试中的运行行为；全目标仍在实施中。

P08 M3c2b9b3-2 Console代理开工：ConsoleProxyService按整个forward（可信身份/机器凭据读取→发布会话交换→原代理动作）登记运行计数，固定最多1024个本机在途、无队列。host shutdownTransport拒绝新forward但保留原两阶段流程，全部forward返回后才HttpClient.shutdown；终止必须同时父调用为0及JDK客户端isTerminated。无新线程或身份传播，Future取消/超时不当作库已退出；PreDestroy强制关闭阻止尚未发出的下一阶段，不自动重做未知发布写。

保持HTTPS配置、路径白名单、权限、原host token不外传、机器token ACL、2MiB响应/1MiB分片、请求20秒/等待25秒及NATIVE回执协议。包内测试接缝可缩短等待/容量但不能放宽生产配置。先受控客户端两阶段/容量/拒绝/取消及真实本地HTTPS证书校验、慢正文未知写/优雅终止，再原Console请求治理HTTP和默认宿主MySQL回归。只用合成回环服务，无用户发布服务/外部发布动作；通过后再推进TLS/NodeControl。

Console首轮p08-m3c2b9-console-unit.log在测试编译阶段失败，未执行测试：并行新增DefenseMaintenanceConfigurationTests给已有primitive boolean的DefenseCollectionProperties.enabled传null。仅将该测试夹具首参数修为false（默认禁用采集），不修改安全生产实现/其他字段。记录此一行跨线编译修正后复跑，未标当前子项完成。

并行新增SecurityRetentionService已源码审查：宿主入口、单线程有界调度/禁止继承身份，平台清理/目录发现/逐租户清理各自DeploymentWork准入，关闭等待原事务而不提前归还；它是业务维护而非基础协调例外。仅在P08活动清单登记新增构造、Scheduled（含initialDelay）及提交3条，基线由82到85；不修改其生产代码、不把登记当作安全专项已验收。Console没有新增执行器或外部发布调用。

Console修复后p08-m3c2b9-console-fixed.log **35/35通过、0跳过、BUILD SUCCESS（20:34，01:42）**：ConsoleTransportDeployment5（含真实本地HTTPS、可信/不可信证书、两阶段优雅完成、未知命令只发一次、库终止）、原Proxy5/Scope1、跨线夹具Configuration2、Inventory2及结构20。已审新增SecurityRetention3条也通过普通入口门禁，当前85条；首轮编译笔误记录保留。

进入P08专用MySQL的ConsoleRequestGovernanceHttpTests与ApplicationSmokeTests，验证原QUERY/NATIVE/分片重复拒绝、权限/白名单及默认宿主实际装配。代理外部目标仍受控替身，真实HTTPS传输证据来自上轮本机合成服务；不调用用户发布服务、不自动重发未知动作，数据库通过前不推进其他传输。

**M3c2b9b3-2 Console传输完成（本地）**：p08-m3c2b9-console-mysql.log **3/3通过、0跳过、BUILD SUCCESS（20:40，05:44）**，ApplicationSmoke2＋ConsoleRequestGovernanceHttp1。默认宿主实际装配通过，原QUERY/NATIVE、分片重复/伪造查询拒绝、权限/白名单保持；治理用例代理为受控替身，真实HTTPS来自console-fixed本机合成服务，分别记录，不宣称真实发布服务验收。

console-fixed **35/35通过**包括新Deployment5、原Proxy5/Scope1、Inventory2/结构20及跨线夹具Config2。真实HTTPS确认已接受会话交换在关闭后仍完成原命令，后续请求拒绝；慢正文未知命令只发送1次、JDK客户端实际终止；不可信证书在进入合成处理器前拒绝。受控客户端另验证2项容量同时占用、强制close不清运行计数/迟到会话不再开始后续阶段、Future完成仍须独立库终止证据。生产每次20秒请求/25秒等待与凭据隔离保持，1024本机在途无排队。

首轮console-unit在编译阶段被并行DefenseMaintenanceConfigurationTests的primitive boolean传null阻断；只修测试首参false，并在console-fixed验证该配置2项通过，不更改安全生产实现。并行SecurityRetentionService新增3入口已按源码人工审查登记，85条普通清单通过；登记不代表保留维护已完成。

制品backend/target/p08-m3c2b9-console-mysql/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar SHA256=8b8a98f3f01e5d1a6723c5af214f9fa8fc2d9f869feb8ae7c0dc339b23204e8f。均根tools/dev/verify-backend.ps1 -Standard，-Dsma.verify.directory=target/<对应日志前缀>/target、-Dsurefire.failIfNoSpecifiedTests=false；fixed指定ConsoleTransportDeploymentTests,ConsoleProxyTests,ConsoleRequestScopeTests,DeploymentActivityInventoryTests,ArchitectureRulesTests,ModularityTests,SecurityEndpointInventoryTests,DefenseMaintenanceConfigurationTests；mysql指定ConsoleRequestGovernanceHttpTests,ApplicationSmokeTests。P08专用sma_test_p08_local（13306），JVM30628已退出；2个Console运行class两轮字节一致，4个范围源码/清单/修复夹具摘要无漂移。

README/后端及插件规则/部署说明/活动台账与双进度已同步；无外部发布动作，无用户服务重启或其他库改动。下一b9b3-3 TLS定时关闭及NodeControl客户端资源，随后规则/独立检查边界和b9收口；c2c/c2d/c3/M3d/S6/X5未完成，multi继续关闭。

Console子项最终文档核对：7份文档229个本地链接有效，范围git diff --check通过。失败记录、跨线夹具一行修正和新增维护入口审查均保留；当前全目标继续实施，不将本地代理传输通过当作完整多实例交付。

P08 M3c2b9b3-3a TLS探测开工：DomainTlsProbe同时观察同步探测/套接字归属与截止关闭回调，回调经DeploymentTaskTracker登记后交原单线程scheduler，取消未开始任务移出延迟队列，已运行回调保留计数至finally。固定最多1024个本机探测，线程不继承身份；原固定地址、SNI/证书主机名/信任、端口443和≤12秒预算保持。host shutdownTransport拒绝新探测，但在既有探测真正返回前保留其截止任务；之后关scheduler，并同时核对套接字归属、回调计数和scheduler终止。PreDestroy同样保留在途截止保护，不提前shutdownNow丢失关闭任务。

包内Socket工厂仅用于受控竞争夹具；真实Socket关闭后才释放归属，关闭异常且isClosed=false保持未完成，不超时清零。先新回调竞争/失败保留/host门禁与原真实TLS固定地址/证书/无响应peer/优雅关闭，再对应领域回归；不改变租户域名授权/数据库状态或用户服务。通过后再独立处理NodeControl流式响应及HttpClient资源。
