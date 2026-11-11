# 数据、迁移、配置与语言包

继承[总规则](../../AGENTS.md)。以下持久化约定适用于共用 MySQL 8 的基座和静态业务模块；独立服务自行管理其库与迁移，热插件不直接访问数据库。

## 表与数据归属

- 模块拥有自己的表和 Mapper。表使用 `<module>_<entity>` 小写下划线，例如 `acmeorder_order`、`acmeorder_order_item`；关系用关联表。新索引使用 `ix_<范围>_<用途>`，唯一索引使用 `uk_<范围>_<用途>`，同表唯一并控制在 MySQL 名称长度限制内。已有表/索引命名不因这份规范直接重命名。
- InnoDB、`utf8mb4` / `utf8mb4_0900_ai_ci`；宿主业务实体继承 `TenantEntity`，主键 `id BIGINT` 由 MyBatis-Plus `assign_id` 分配，列包含 `tenant_id`、`created_at/by`、`updated_at/by`、`deleted`；布尔使用 `TINYINT(1)`。业务时刻以 `Instant` / `DATETIME(3)` 存 UTC，自然日按用户 IANA 时区转成半开 UTC 范围。字段定义以[基座数据约定](../../README.md#7-数据与持久化约定)与同类迁移为准。
- 租户取可信上下文。查询、写入、逻辑删除、分页总数都保留租户过滤；有数据范围的 Mapper 用 `@DataScoped`，按契约维护 `dept_id`、`created_by`。接口显式校验适用的 portal、对象归属和权限。唯一索引通常包含 `tenant_id`、需要时包含 `portal`；需复用逻辑删除后的编码时沿用 `active_*` 生成列做唯一约束。测试用不同租户、无授权者及删除后重建验证。
- 不跨模块 join、外键、Mapper、直连 JDBC 或借用基座表存业务字段。引用 ID 通过对方公开 API 校验，展示可保留必要快照。全局框架表与独立的发布安全库按其自身规则管理；二开不能把业务表登记成租户豁免表。

## Flyway 与共存

- 基座和普通单体二开都用 `backend/src/main/resources/db/migration/<module>/V<n>__<action>.sql`，如商城写在业务分支的 `db/migration/shop/`；版本在模块内递增，`__root` 仅放框架级公共迁移。按[二开目录](secondary-development.md#二开目录)验证模块被发现且迁移实际执行。独立服务使用自己的库和历史；热插件 JAR 不携带可执行 DDL，也不能启动另一套 Flyway。
- 只追加修正迁移；不改已交付脚本、不重编号、不手改历史表、不以 repair 清除未查明的校验失败。并行开发撞号时，只协调尚未交付的脚本。SQL 从空库及上一版真实形状的脱敏快照分别验证，记录版本、耗时、数据核对和可恢复点。
- 新旧程序要共存时，先加兼容字段/表并部署能读旧数据的代码，再按租户分批可重入回填和核对；根据业务选择双读或双写并声明主数据来源。旧消费者、在途工作及回退窗口退出后，另发迁移移除旧字段。大表索引/回填要有锁、批次与时长预算。MySQL DDL 不能假定可用事务回滚。

## 配置归属与定义

P08主键号段属于Common平台技术状态，IdBlockEntity是精确的非租户例外；不包含业务载荷，不构成业务绕过TenantEntity的依据。实体assign_id及IdentifierAllocation/既有IdWorker统一绑定，禁止另建雪花或重装全局生成器，IdentifierBoundaryTests为门禁。分配独立提交且不退款；业务同库回执的原子性不包含号段预留这一框架元数据。ID不代表时间/提交顺序，最近列表用createdAt＋ID；固定迁移种子须低于2^52保留起点，高位历史导入需停写核对分配下界。Common V13已经过旧结构及空库验证，之后仅追加；升级须停旧写入器，不能回退到旧生成算法。具体机制与验证见[补充方案](../proposals/P08-id-allocation.md)。

### P08 缓存契约（分阶段实施）

OpenAPI的LoginBudgetEntity/LoginBudgetGuardEntity属于尚未确定租户前的平台凭据引导元数据，作为明确例外继承BaseEntity；只允许保存受限计数、带密钥摘要及加密摘要密钥，不存应用/账户业务数据，不提供通用读写入口。正常OpenAPI应用/令牌/nonce继续TenantEntity和原引导查询白名单。业务不得复用此例外；PlatformPersistenceIsolationTests按真实映射类型精确列举已审查平台表，OpenLoginBudgetTests验证无租户引导、共享原子预算及不能回滚退还。

静态二开缓存的具体接入、预热和版本边界见[二开缓存契约](../cache-secondary-development.md)。启动校验真实提供模块，不能仅声明一个owner字符串；业务管理权限不得复用基座前缀。禁止直接依赖缓存客户端绕过容量/隔离/失效，架构入口为CacheBoundaryTests，注册入口为CacheRegistrationOwnershipTests；该规则针对可信源码，不是任意Java代码沙箱。

节点清单/租约是Common框架元数据，只有CacheNodeGroupEntity和CacheNodeLeaseEntity按全局BaseEntity映射；不含租户业务载荷、不给业务新增免租户表提供例外。全局更新拒绝HTTP/tenant job身份，只由宿主运行时调用；任务及确认仍按可信租户隔离。清单版本单调，旧进程/旧版本不得冒充新代次。验证CacheNodeMembershipTests、CacheNodeIdentityTests及架构门禁；V9后仅追加迁移。

适用于跨请求可重建数据及登记的运行时缓存；临时集合和请求内 memoization 除外。新缓存必须声明模块归属、可信隔离范围、有限 TTL、容量与单条预算、格式版本、失效来源和操作能力；具体公开契约为 `common :: cache`。安全状态不得作为可清理缓存，缓存不代替权限/租户/对象校验。业务 key 不含明文凭据，不缓存 Mapper 实体或任意多态对象。写入、删除、恢复、导入、初始化和翻译修改均须覆盖失效；未提交/旧事务快照不得回填共享缓存。Redis 存储与持久失效的交付状态见[P08 进度](../cache-governance-progress.md)，不得将规划当已实现。例外运行时缓存须登记适配器及在途保护；验证入口为 CacheContractTests 和各区域读写/回滚/租户隔离专项，最终执行根 reactor 标准验证。

| 类型 | 落点与规则 |
| --- | --- |
| 部署/启动配置 | 基座 `sma.<module>.*`，独立发布安全服务已有 `release.*`；业务用独立 `business.<bizid>.*`（例如 `business.acmeorder.max-items`）。YAML 字段用 kebab-case，Spring 使用类型化 `@ConfigurationProperties`，明确类型、单位、缺省、校验和启动失败行为。通过业务自己的配置文件、部署覆盖或环境变量显式接入，勿把业务环境写进基座默认值。 |
| 租户可调业务参数 | 使用 Config 模块公开 `ParameterQuery` 与现有管理流程，使用业务前缀 key（例如 `acmeorder.maxItems`），注明租户、默认、缓存失效、是否实时生效和权限。存在多个来源时逐项定义优先级；不能假定数据库参数自动覆盖所有 YAML。 |
| 机密与前端 | 密码、token、主密钥用部署环境或受保护文件，示例仅留占位；不放入日志、制品、插件清单、共享配置或 `VITE_*`。保持敏感参数不可公开、脱敏显示、编辑留空保留旧值的既有语义。前端只放可公开配置。 |
| 热更新配置 | 显式区分启动时绑定、业务参数、规则快照、插件版本及支持热读的文件（如 `node-token-file`）。新增键必须说明何时生效；配置重命名先支持旧键/冲突告警，明确移除版本。不把任意 YAML 覆盖宣传为热刷新。 |

后端语言包放 `i18n/<module>/`，key 带模块前缀；`ErrorCode` 有双语文案，zh-CN/en-US 的 key 与占位符保持一致。前端按目标工程的语言包和校验脚本处理。配置变更同步更新相应示例、部署说明及兼容记录。
