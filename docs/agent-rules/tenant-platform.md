# 租户入口、品牌与业务存储

继承[总规则](../../AGENTS.md)。适用于 P10 新增 tenant 模块、入口认证、品牌模板、配置分层、业务 MySQL/Redis 路由和 SQL 统计。实施状态必须查[专项进度](../tenant-platform-progress.md)，设计不代表已交付。

- 匿名域名解析只提供入口范围，不赋予用户/平台权限；不得用 HTTP tenantId、未知 Host 默认租户或租户内 superAdmin 获得平台访问权。P09 ActorContext 的身份边界保持生效。
- HTTP入口在浏览器Origin检查之前统一规范化外部scheme/host/port；框架/native转发处理必须关闭，只有部署登记的精确代理IP可以提供单值转发头。会话/挑战/验证码/机器bearer必须核对租户安全代次；缺少代次的旧材料不可默认设为有效。安全代次通过关闭MyBatis查询缓存的当前读获取，避免旧事务快照和额外嵌套借连接。
- `/api/config/parameters/public` 按用户确认删除，不建立兼容别名；前后端同批迁移。匿名入口仅输出品牌/登录能力白名单，不透传普通参数表。
- 新entry与修订图片匿名白名单只允许精确GET/HEAD，不能放开tenant品牌管理路径。响应no-store，图片只读当前可信租户/portal的已发布快照；无发布不读草稿，原文件不开放匿名访问。旧品牌迁移不得网络抓取背景URL、泄漏secret/非公开值或静默截断。验证BrandEntryTests、BrandEntryHttpTests、TokenTenantBoundaryTests、LegacyBrandConverterTests；数据归档与转换器的交付状态分别查进度。
- 旧品牌迁移只由可信宿主启动编排，Config源读取/归档必须参加调用方事务并用无MyBatis缓存的当前锁定读；各portal首个发布、回执和源归档同平台事务。已有新品牌不得覆盖，失败须阻止就绪。普通参数读写/种子/重置禁止品牌键及大小写变体；遗留背景是独立只读元数据，不能从BrandContent请求注入。Config V5/Tenant V4之后仅追加迁移。验证LegacyBrandImportTests、LegacyBrandSchemaUpgradeTests、LegacyBrandBootstrapTests、BrandParameterOwnershipTests；旧表形状专项库已升级后不得直接重跑要求旧前提的用例。
- 登录风格使用静态注册模板和共同认证状态机；租户不得上传执行代码。发布、回退保留不可变历史，草稿不可匿名访问；品牌素材必须验证归属和发布安全状态。
- 双前端只消费typed entry，portal取已验证响应，不取URL。入口失败清除旧品牌并禁用登录/验证码；按请求代次丢弃旧响应，范围变更使旧身份失效。同身份语言/主题切换不清空登录表单。开发代理保留Host/Origin，不能默认改写为localhost。验证branding单测、playwright.tenant.config.ts及隔离e2e-real/tenant-entry.spec.ts；mock/代理探针与真实后端联调分开记录。
- BrandContent为schemaVersion明确的类型化数据，login1/2/3不是任意组件路径。BrandDraftService限定可信tenant/portal且校验权限/expectedVersion，保存不得修改publishedRevisionId；禁止从草稿直接输出匿名品牌。验证BrandValidationTests、BrandDraftTests；实际发布/素材/模板页面按后续专项独立验收。
- 品牌发布与回退必须锁定头版本并固定操作ID/意图/操作者；权限撤销或另一操作者不得重放。历史只插入，回退新建修订；图片快照/引用/指针与发布同库提交。素材只能走file的ImagePublication授权契约，普通附件不开放匿名读取；引用检查用当前读，草稿/历史使用中禁止删除。进程持图事务和解码均有界，事务完成才释放持图预算。验证BrandPublicationTests、BrandHttpTests、BrandWorkBudgetTests、PublicImageEncoderTests及BrandMigrationTests，交付状态查进度。
- 平台共享定义和租户覆盖分别管理；禁止用 tenant_id=0 或跳过租户插件实现共享。覆盖范围按参数/字典定义声明，不能统一让用户/租户覆盖安全规则。
- 业务数据源只能由宿主登记/创建。平台/业务 Mapper 和事务资源分别绑定，事务前固定路由，事务内不得切换；独立库仍保留租户/数据范围校验。缺绑定/不可用时拒绝，禁止默认库回退。
- 数据库连接上的 DataSourceIdentity 由实际物理池绑定，资源 ID/修订不得来自 HTTP、Mapper 包名或 JDBC URL 推测。routing facade 不能冒充物理资源；旧连接保留旧修订，unknown 独立展示。统计不得额外借连接或读取 SQL/参数/凭据。
- 连接池/Redis 客户端按物理资源修订复用并设预算；不得逐租户永久创建无限池。单个资源故障不能耗尽宿主全部回源/线程。
- 跨库操作不假定普通 @Transactional 原子性；Outbox、缓存失效登记和幂等执行权/互斥记录必须与业务写入同资源提交。禁止通过平台库先提交失效或先占执行权后宣称业务事务可靠。平台库只保留目录、跨库对账与运维视图；`common_id_block` 按块预分配不受影响，各模块预算表本就独立提交。详见 [P11 跨专项资源归属](../proposals/P11-cross-stream-resource-ownership.md)。
- 部署级 Redis 连接工厂归 P08，provider/地址/拓扑/命名空间为重启生效配置；本模块的资源登记只决定业务可重建缓存绑定到哪个已登记物理资源并设预算，不改变区域语义、键布局与失效协议。租户级改绑只对新解析生效，须冷启动或限速预热。PROTECTED 状态不随业务资源迁移。
- Flyway 只执行该资源归属的迁移，共享物理库只迁移一次。切库走持久迁移任务和停写排空；目标接受新写后禁止仅改配置回退。

验证入口：根 `tools/dev/verify-backend.ps1 -Standard`，阶段至少覆盖 DataSourceIdentityTests、SqlDataSourceTelemetryTests、SqlTelemetryTests、ModularityTests、ArchitectureRulesTests；后续入口/分库/品牌/Redis 专项按进度追加。真实数据库测试必须使用独立 `sma_test_p10_*` 库与输出目录，前端在对应目录执行 verify 和隔离浏览器验收。单一连接/Mock 测试不代表真实双库或集群验收。

S4管理消费：新版入口features/tenant/BrandPage；预览为同源独立frame，只接收受检origin/source和有界视觉消息，不发认证/草稿API或写本地偏好。服务端previews为QUERY、响应no-store、通过ImagePublication重编码且不建引用/修订；草稿LOCAL与发布/回退NATIVE原生operationId保持区别。操作查询只返回原操作者且当前动作权限仍有效的回执；NOT_OBSERVED不是失败证明。发生未知结果时只核对，不生成新键自动重放。

Config拥有AppearancePolicy宿主SPI，Tenant按可信租户/portal发布指针提供唯一实现；不能让Config反向依赖Tenant内部类或由业务覆盖Bean。缓存只存原始个人偏好，读取后合成有效皮肤；禁止个人选择期间保存语言/主题不得毁掉原skin。匿名选择按入口范围隔离，预览不改变真实偏好。两端英文名称可回退systemName，重复选择当前语言保留租户标题。

验证补充：BrandPreviewTests、BrandAppearanceTests、BrandHttpTests、PreferenceTests和TenantRequestGovernanceHttpTests；前端tenant单测、playwright.tenant.config.ts及隔离brand-management.spec.ts/tenant-entry.spec.ts。直接调用API的测试也必须遵循上传/草稿当前Idempotency-Key要求，不能以省略新协议要求的旧夹具失败为理由放宽生产校验。整体验收状态以专项进度为准。

域名生命周期仅允许平台部署操作者与动作权限共同授权。网络验证使用NATIVE、begin/finish短事务，不能在LOCAL/既有事务中执行DNS/TLS；挑战/域名版本/原操作者/租约共同约束回执。域名与同租户portal主入口的变更先锁所属租户，依赖状态使用当前读，避免旧REPEATABLE_READ快照恢复旧主域名或覆盖已完成检查。验证成功不自动启用，新的启用必须使用当前挑战的近期证据；已有入口仍检查TLS截止。域名禁用提升租户安全代次，最后有效平台admin入口受保护。检查回执继承TenantEntity，不能新增全局豁免。Tenant V5已通过迁移验收，后续只追加脚本。验证DomainLifecycleTests、DomainLifecycleHttpTests、DomainSchemaUpgradeTests（独立空库）、DomainProbe/Dns/TlsProbeTests及原认证/WS/规则HTTP专项；不把本地网络夹具称为生产域名验收。

共享配置目录由实际归属的静态模块声明，命名空间、类型、覆盖策略、公开投影及有限预算由宿主校验，不能让表单提交任意可执行验证器或抢占别的模块。ConfigurationPlatformAccess由Config定义、Tenant适配部署运维名单；平台身份与当前动作权限必须共同满足，LOCAL重放也重验。两个共享表只放平台元数据/共享修订，租户覆盖不能放入它们。Config V6已通过保留数据升级和持久化专项，后续只追加迁移。当前指针与修订使用禁MyBatis缓存的当前读；初始化不覆盖已有草稿/发布，契约漂移停止就绪，未知schema不回退。验证SharedConfigurationRules/ReadTests、ConfigurationCatalogRegistryTests、ConfigurationPlatformAccessTests、SharedConfigurationTests与独立空库的SharedConfigurationSchemaUpgradeTests；旧查询切换、迁移与前端是否完成仍以S5进度为准。

共享参数迁移扩展到Config V7：旧行默认显式覆盖，definition_contract只经宿主按定义约束核对后绑定，显式null与继承标记不同；保留ID/值/翻译。租户parameter_revision留在config_write_lock，恢复继承仍单调推进，不删除版本行。读写锁必须以guard主键及参数tenant＋active_key索引定位，并保留租户拦截，禁止只按未索引tenant_id扫描加锁。V7已通过旧结构专项，后续仅追加迁移；DefinedParameterService运行切换状态查S5c进度。验证DefinedParameterTests及ParameterLayerLockMysqlTests（专用已升级V7库，不清库）；ParameterInheritanceSchemaUpgradeTests仅用专用空库、升级一次后不可重复当空库运行。

S5c-2原ParameterQuery运行接线中：注册定义按规范key读取，安全分类使用目录元数据；已绑定但定义退出的私有行不能回落成普通值。内置目录只能依赖纯静态元数据，不能注入BuiltinParameters/ParameterService造成注册循环。启动按-190共享登记、-180租户参数迁移、-100品牌迁移执行；迁移包括未开通/停用租户，不用acceptsBusiness跳过。普通读取的缓存调用放在短源事务之外，持有调用方事务时继续旁路；新版本不得回填旧key。实际验收状态查S5c；验证DefinedParameterStartupTests、DefinedParameterTests、ParameterTests/CacheTransactionTests、BrandEntryHttpTests、LegacyBrandImportTests、SessionConcurrencyTests及偏好缓存消费者。

S5c-3有效参数HTTP在/api/config/effective-parameters，以key定位可信当前租户的定义；只能查看当前有效层，不能借它读平台草稿/其他租户覆盖。覆盖/恢复继承需config:parameter:update与双版本，LOCAL重放重验原动作权限；value必须显式出现，null与继承分开。旧private接口拒绝已登记键及绑定行。目录最多50条，先筛代码元数据再读正文；新增管理入口不得放开匿名public旧路径。

Config V8已完成旧结构保留数据升级，后续只追加。presentation_overridden只区分显示来源，不能当成值覆盖：旧行名称/翻译保留，新值覆盖不复制显示覆盖；管理呈现读取已发布文案。验证DefinedParameterHttpTests、原参数/共享HTTP、普通SecurityEndpointInventoryTests和ParameterLayerLockMysqlTests（使用已升级到当前版本的专用库）；ParameterPresentationSchemaUpgradeTests只接受保留的V7前置库并只升级一次。完整前端及S5交付仍以进度为准。
