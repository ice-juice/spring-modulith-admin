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
| M2 | 跨节点推送、可靠补查、共享文件与后台任务 | M1 | A事件/B连接、广播丢失恢复、A上传/B下载、任务重复/认领 | 进行中（M2a/b/c完成，M2d） |
| M3 | 节点查看/定向管理、LB/代理信任、密钥/ID/健康/优雅下线和回退 | M2 | 关闭粘性双实例、节点路由/故障注入、滚动升级及一致配置预检 | 待实施 |
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

### M2d真实双实例联调（实施中）

扩展既有隔离runner，在原14组M1互通后增加：同一收件人分别连接A/B，A发私信两个连接都收到；单独关闭B提示分发，A仍收到、B不收到但REST可读，恢复B后读取持久最新提示及完整未读；A经原上传协议写入，B下载字节一致且另一已认证用户被拒绝。复用原授权/回执，不实现新消息或上传旁路。客户端补充断连且无法取票期间30秒REST轮询的专项。仍用single模式测试协议，不代表M3配置一致性或无粘性LB已通过。

客户端消息store专项 **6/6通过**，包含新增“断连且取票不可用时仍每30秒读取授权REST状态”；脚本语法和对应ESLint通过。真实runner设置`SMA_CLUSTER_M2=1`，使用`p08-m2c-integration/target`已验证JAR；新增显式共享资源声明/已有managed别名，记录每次启动和退出的进程证据。关闭B分发是明确的配置故障注入，不称为网络分区；同机目录共享不称为多主机挂载。环境初始化截止放宽到15分钟以覆盖本机慢DDL，功能断言和业务超时不放宽。结果待记录。
