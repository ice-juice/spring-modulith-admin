# P10 运行与验证说明

交付范围以[实施进度](tenant-platform-progress.md)为准。当前尚未完成整套租户平台，不将此文件当成生产上线许可；完整设计见[P10](proposals/P10-tenant-branding-and-storage.md)。

## 平台数据源的统计身份

S6a资源目录已本地验证：Tenant V6增加平台`tenant_storage_resource`与`tenant_storage_binding`，资源ID＋修订不可原地覆盖，绑定按ownerTenantId＋storageGroup唯一且初始为PENDING。`sma.tenant.storage.allowed-hosts`默认空；登记目标必须在部署白名单中，`allow-insecure-loopback`默认false，仅显式开启且目标为localhost/127.0.0.1时才可登记`DISABLED` TLS。资源记录只含`secretRef`，不保存真实密码；不能通过直接改PENDING状态替代S7开通。V5→V6专项21项、专用库与未验收范围见[租户进度](tenant-platform-progress.md)。

S6b宿主池已本地验证：`sma.tenant.storage.pool`默认每节点最多8个活跃物理池、总连接上限32、同主机上限16、空闲回收5分钟；单资源目录`poolMax`限制1～8，借连接等待为目录毫秒预算且Hikari最低250ms。按资源ID＋修订复用，绑定须ACTIVE且schemaCompatibility=v1，修订描述漂移拒绝；租约在会话关闭后释放，旧池不会在在途期间关闭。部署必须提供`tenant :: storage`的`StorageCredentialResolver`从secretRef读取用户名/密码；默认没有解析器，任何资源开池会拒绝。S6b真实双库验证框架账本路径，实际两库与预算/身份27项证据见[租户进度](tenant-platform-progress.md)。

S6c静态业务Mapper经独立工厂和路由DataSource装配。二开代码在事务前打开`BusinessLedger`会话，在`executeLocal`中写业务表并登记失效/Outbox，或在`query`中读取业务表；两者从会话固定的物理连接取源，会话外查询拒绝，不能在已有平台事务中切入。切换绑定时在途会话仍用旧源，新会话解析新源。平台库、业务A/B三库夹具与平台回归分别为1项和26项、均0失败/跳过，证据见[租户进度](tenant-platform-progress.md)。生产开通/激活仍须S7任务，HTTP目录管理须S6d。

S6d平台目录HTTP入口为`GET/POST /api/tenant/storage-resources`和`GET/POST /api/tenant/storage-bindings`。读取须`tenant:storage:view`，写入分别须`tenant:storage:register`与`tenant:storage:bind`，全部还须部署配置的`platform-admin-user-ids`身份及平台租户admin入口；租户超级管理员不能自行获得平台操作权。资源登记请求只提交受白名单限制的host/port/schema、tlsMode、secretRef与池预算，不提交完整JDBC URL或明文密码；响应只显示是否已配置凭据，不回显secretRef。绑定请求提供租户ID/存储组/资源ID/修订与schemaCompatibility，结果始终PENDING；重复写入使用`Idempotency-Key`并按原LOCAL回执处理，未知结果先重新查询目录。HTTP/端点策略/模块22项和三库/故障11项通过，证据见[租户进度](tenant-platform-progress.md)。生产部署应提供经运维审查的`StorageCredentialResolver`，并把同一物理服务器的别名归并到同一受控主机名；当前池的每主机预算按登记主机名计算，别名不能自动识别为同一服务器。

S7a内部迁移入口已在旧业务表夹具验证。运行时由平台任务调用`StorageMigrationService.migrate(resourceId, revision)`，只使用登记的目标与部署凭据；同一物理资源通过MySQL命名锁排斥并发，临时池最多两个连接且节点同时只允许一个迁移。业务库独立历史表`flyway_schema_history_business_ledger`，不执行平台全套Flyway，不允许自动clean/repair。迁移后仍需S7b任务验证并激活绑定；当前没有HTTP迁移按钮。空库、故障续跑与正式激活的剩余门禁见[租户进度](tenant-platform-progress.md)。

S7b内部开通任务已本地双库验证：平台操作者以绑定ID、预期绑定版本和16～64字符operationKey调用`StorageProvisionService.provision`；RUNNING不能并发重跑，迁移失败记NEEDS_REVIEW并保留PENDING，同键人工核对后续跑，fence阻止旧执行者提交。Flyway历史与四张实际框架表同时通过，才在平台短事务激活绑定并完成任务；完成后同键读取SUCCEEDED且不再迁移。V7只追加`tenant_storage_operation`平台回执；不存在对外HTTP开通入口，运营调用接线归S7d，换库归S7c。并行工作树全量测试编译被其他专项构造器变动阻断，本阶段冻结输出的3项双库专项和主源码编译通过，边界见[租户进度](tenant-platform-progress.md)。

S7b后续收紧：`NEEDS_REVIEW`和已过期RUNNING的普通`provision`重放拒绝；核对真实物理库和原任务意图后，由平台运维明确调用内部`retryAfterReview`。根reactor相关23项已通过，不能把HTTP超时当作未执行而直接重试。前述冻结输出是开发期证据，最新根reactor结果见[租户进度](tenant-platform-progress.md)。

S7c-1本节点`StorageSessionTracker`按可信tenantId＋storageGroup登记在途业务会话；`block`在同一键上先关闭新准入，再返回当前计数，旧会话自行完成后递减。它只供后续维护窗口任务排空，不是跨节点证明；要切库必须取得S7c-2持久停写代次、节点清单和每节点完成证据。真实双库/架构/模块21项已通过，细节见[租户进度](tenant-platform-progress.md)。

P11 X1业务账本前置已本地验收。业务资源的框架迁移位置为`classpath:db/business-ledger`，使用独立`flyway_schema_history_business_ledger`，当前V1只含执行权、互斥、缓存代次和Outbox；不能把平台`db/migration/common`全套表迁到业务库。S6/S7负责把真实资源登记、池租约、迁移锁/历史、轮询及派发接入运行时；当前不能据脚本存在就开通业务资源。业务会话在事务前选资源，Outbox需在该资源可写事务中入队；未知结果用原事件类型＋稳定键复查，异内容拒绝。权威代次需在事务外读实际业务库；汇聚超过设定年龄或读取失败报告stale，不代表异步目录已强一致。X1c验证使用专用平台/业务库的25项双库与架构、17项平台账本/代次回归，详见[P11进度](development-progress.md#p11-跨专项资源与账本归属)。

平台池默认标识为 `platform`、修订 `1`。部署可指定：

```yaml
sma:
  persistence:
    resource-id: platform
    resource-revision: 1
    slow-statement-millis: 500
```

resource-id 只允许小写字母开头的小写字母/数字/下划线/短横线，最多64字符；revision 为正整数。不得填连接串、用户名或密码。换成另一实际资源时使用不同 ID；同一登记资源更换连接配置/凭据时提升修订。未来业务路由由资源登记自动提供身份，不由业务请求传入。

平台 `dataSource` Bean 在借出连接时附加标记，已有连接的标记不可变。执行统计仍为本节点/本进程、当前租户的 MyBatis StatementHandler 调用；返回 resourceId/resourceRevision。unknown 表示连接未受宿主管理或身份无法取得，不应当作平台库。当前不包含完整游标行数、批量提交、直连 JDBC 或 Flyway 的全量 SQL 审计；不返回 SQL 正文/参数、数据库 URL 和凭据。

数据源过滤、已授权资源目录及单资源重置在 S9 验收前不可宣称已经支持。SQL 数据源能力与 Redis 监控区分：Redis 不产生 SQL，不放入 SQL 数据源列表。

## 平台运维身份和本地入口

新增 `sma.tenant.platform-tenant-id`（默认1）和 `platform-admin-user-ids`（默认空）。平台管理 API 同时要求明确操作权限与此部署名单中的后台人类身份；名单只在指定平台租户的 admin portal 有效。不要把租户 superAdmin 或可自行授予的角色作为跨租户管理依据。

`sma.tenant.bootstrap-hosts` 默认空。开发/隔离测试可显式填写 localhost、127.0.0.1、`[::1]`，不能填写端口或任意业务域名。bootstrap 仅登记缺少的回环入口，不激活已停用记录，也不把已归属其他租户的域名抢占。生产自有域名须走后续验证/证书/发布流程，直接注册不会使其生效。

首次使用非默认 `platform-tenant-id` 时，必须在空平台目录上配置目标 ID 和显式回环入口，再启动完整应用并等待 readiness。目录先占位目标平台租户；IAM 首位管理员、权限目录、内置参数与字典的启动任务通过宿主平台租户契约绑定同一 ID，后续共享配置与旧品牌迁移按目录逐租户运行。不要把已有 ID=1 的平台库直接改配置复用：`platform` 唯一代码、既有管理员、权限和品牌历史都需要单独的数据迁移与回退方案。部署运维名单中的用户 ID 还须属于配置的平台租户。非默认 ID 的完整启动验收使用单独可清空测试库，设置 `SMA_P10_NONDEFAULT_BOOTSTRAP=true` 并运行 `NonDefaultPlatformBootstrapTests`；普通全量回归不启用这个会清库且要求空目录的专项。

`tools/dev/run-backend.ps1`、`tools/dev/start-with-admin.ps1`及隔离E2E runner已显式传入这三个回环入口并启用health probes；生产启动默认仍为空。完成迁移和ApplicationReady初始化后再以`/actuator/health/readiness`的UP作为可登录条件，普通health UP不代表启动迁移已完成。两套Vite代理保留原Host/Origin，不能靠changeOrigin把所有域名改成localhost。

这些配置只在服务启动读取。当前阶段未切换用户正在运行的服务；本分支S2入口一致性与会话安全代次已通过本地真实MySQL/HTTP/WS专项。外部DNS/TLS、品牌页面与后续分库仍有独立验收，不能以S2通过宣称完整租户平台交付。

`sma.tenant.entry.trusted-proxy-addresses` 默认空，仅接受精确IP字面量；默认忽略来自其他来源的转发头。受信代理必须覆盖客户端提供的转发头并提供单值X-Forwarded-Host和X-Forwarded-Proto；优先直接保留原始Host。`server.forward-headers-strategy` 必须保持none，防止框架/native在本入口策略之前信任未经校验的转发信息；不兼容配置拒绝启动。外部scheme/host/port同时用于入口与Origin检查。

auth V5与OpenAPI V3新增租户安全代次。升级前已有的后台会话、验证码、MFA挑战及机器bearer没有可信代次，升级后需重新登录/领取/签发；不修改密码策略或复制旧认证材料成为新会话。暂停及恢复都推进代次，恢复不复活旧材料。迁移只加列，回退旧程序会失去此停用防护，因此应先停入口并撤销旧材料，不能把单退JAR描述为安全回退。

安全代次通过同一事务连接的InnoDB当前读取得，Mapper明确关闭一二级查询缓存，不从普通展示缓存读取。这样不会为每次状态核对再借第二条连接；共享锁在已有事务结束时释放，暂停写操作会等待这些在途事务，接口返回成功后新校验必须观察到新代次。不要通过扩大池大小掩盖嵌套借连接问题，也不要用旧REPEATABLE_READ快照替代此当前读。

## 本任务验证入口

近期单实例二开按[基线计划](secondary-development-baseline-progress.md)执行。正式业务库provision除框架表外，必须有`BusinessSchemaCatalog`及`db/business/<module>`版本脚本；无法找到目录/脚本时保留原生失败回执，不用手工改ACTIVE。业务缓存通过`BusinessCache`读同库权威代次，写事务通过`Session.invalidate`登记；共享Redis与本地模式已分项验证，专用资源改绑后补。教学接入见[shop-storage](../examples/shop-storage/README.md)。

### 品牌发布资源边界（S3b及S3c入口本地验收通过）

公开入口为`GET /api/tenant/entry`，图片为`GET /api/tenant/brand-images/{revisionId}/{slot}`；必须经过域名目录解析，只有同租户/portal的已发布修订图片可读，均返回no-store。无发布时使用目录租户名称与空法律/素材，不读取草稿；不透传参数全集。原文件content仍需鉴权。S3接口、迁移、双前端消费已通过本地及隔离浏览器联调；必须前后端同批发布，不能让旧前端继续调用已删除的公共参数HTTP。

### 旧品牌参数升级

Tenant V4和Config V5只追加列/回执表，原参数行保留。ApplicationReady阶段在目录引导之后、接受就绪流量之前按100租户分页迁移；每租户的所有已登记门户、第一份发布、回执和Config归档在一个平台库事务中完成。只处理已登记租户；旧系统存在孤立租户ID时先完成目录接入。无域名记录的旧租户使用admin，单租户超过100门户或已有新品牌草稿/发布时明确阻止自动迁移，需先核对，不覆盖新配置。

非法源在启动错误中列出租户和字段名，不打印原值。secret与非公开字段不进入发布内容，回执只保留排除键名。合法旧背景URL只保存为只读引用，服务端不抓取；管理输入不支持新增任意图片URL。上传新背景或HIDE/INHERIT清除草稿引用，不改旧发布；回退复制历史引用。外站图片字节仍由原站控制，不将这种遗留引用称为PNG快照。

普通ParameterQuery及参数管理不再读写7个品牌旧键，初始化/重置不会重建；大小写变体同样拒绝。login.captchaEnabled仍由认证策略控制。前后端与相关二开应同批切换，禁止旧版本继续写归档参数；回退应用需核对切换后新品牌修改，保留所有归档和修订，不能只退JAR便宣称数据已恢复。

验收：LegacyBrandImportTests、LegacyBrandSchemaUpgradeTests、LegacyBrandBootstrapTests、BrandParameterOwnershipTests及原Parameter/BrandEntry/BrandPublication/BrowserSecurity专项。真实组合18项、保留旧库升级1项、旧映射删除专项与双前端隔离联调通过；详细日志见专项进度，后续S4～S10不能由这些结果代替。

### S3双前端验收入口

在frontend-next执行`npx playwright test --config playwright.tenant.config.ts`，自动使用5197/5198/5199做双前端与HTTP/WS代理探针，不依赖用户后端。真实联调使用`node scripts/run-real-e2e.mjs tenant-entry.spec.ts`，设置SMA_E2E_LEGACY=1、独立后端/前端/旧前端端口及SMA_E2E_BACKEND_JAR指向已验证JAR；runner只创建/清理其自己的临时数据库、账号和进程。真实测试覆盖正常上传、草稿不公开、发布后两端展示、原附件仍受保护、URL portal不覆盖绑定、旧公共路径不再返回数据。不要把mock或代理回显测试称为真实业务联调。

新ImagePublication契约只允许有事务、已认证且具备源文件访问授权的调用；输出为有界PNG快照，不提供原存储路径。品牌草稿/历史引用通过FileAccessPolicy保护，不能为了发布Logo放开所有附件。暂支持PNG/JPEG/GIF首帧；原始文件与输出各≤2MiB、边长≤4096、总像素≤400万。进程同时最多4个解码、4个持图事务，超额明确拒绝。不是通用杀毒扫描，WebP/ICO支持尚未交付。

`sma.tenant.brand-defaults.revision`及`content`为部署默认，发布快照固定它们的结果；默认内容不能携带跨租户文件ID或另一组覆盖规则。法律主体不可默认继承。历史每portal100修订/64MiB图片上限，容量不足不自动删除旧历史；发布/回退与图片快照、指针同库原子提交。没有使用普通缓存存放发布账本或不可变图片。

匿名HTTP入口与原public API删除在S3c完成；当前已实现的published/publicImage服务要求TenantEntryScope，不能从任意tenantId授予读取范围。旧发布图片URL后续按租户/portal/修订/slot定位，历史图片可用于已加载页面与回退，绝不以任意fileId代替发布修订。

### 验证命令范围

S7c-2换库任务当前为宿主内部流程：平台运维以原绑定版本和受控目标领取任务，部署物理核对器检查备份/初次复制，任务停写后各受控进程排空并提交本进程证明，最终同步必须晚于全部排空。V8表保存任务、停写代次、会话租约和节点证明；不保存密码或业务行。`StorageMigrationEvidence`没有默认实现，缺失时拒绝物理核对。READY仅表示待切换，绑定保持DRAINING；旧进程残留租约需调查，不得直接删除、把离线节点当排空或手工改ACTIVE。实际切换及恢复按S7c-3交付。真实MySQL状态机与模拟双节点专项见[租户进度](tenant-platform-progress.md)。

S7c-3在READY后再次执行受控物理核对，再由短事务锁定任务与绑定，验证新鲜证明、全部节点仍在线且未替换、会话租约为零，最后同事务切目标资源和提升绑定版本。切换后即使原库仍存在，也不能直接调用`abort`回去；以现有目标为源发起新任务，复制新增数据并重复停写核对。切前`abort`恢复原绑定并提升版本。生产部署必须实现源/目标全业务表及Outbox的物理复制、摘要与备份验证；本地真实双库探针只证明核对协议和拒绝分支。HTTP入口归S7d。

S7d-1业务Outbox消费者由宿主接入`BusinessOutboxConsumer`并按事件稳定键实现幂等。V2持久领取失败保留UNKNOWN，租约到期可重投；未配置匹配消费者或存在多个匹配者时不确认DONE。宿主每轮最多处理8个已打开物理资源，空闲资源不会因此建池；需要持续投递的部署须保持资源可轮询并监控UNKNOWN年龄。业务代次汇聚异常返回旧值加stale，不把旧值当权威当前值。

S7d-2平台任务HTTP：`POST /api/tenant/storage-provisions`用bindingId、expectedVersion、operationKey领取开通；失败后`GET /api/tenant/storage-provisions/{operationKey}`核对，人工检查目标库后才调用`POST /api/tenant/storage-provisions/retries`。换库从`POST /api/tenant/storage-switches`领取，随后按copy-checks、stops、final-checks、commits执行，`GET /api/tenant/storage-switches/{id}`查询原生任务；切前可aborts恢复。所有写入口要求部署平台操作者身份及provision或switch权限，GET要求view；请求不接收备份布尔字段或数据库密码。缺部署物理核对器时copy-checks失败且任务留PREPARING。

所有后端验证从根 reactor 经 `tools/dev/verify-backend.ps1 -Standard` 执行。独立输出路径保留 `target/.../target` 后缀，避免架构测试将测试类误识别为生产类。

- 连接标记/SQL基础：DataSourceIdentityTests、SqlDataSourceTelemetryTests、SqlTelemetryTests。
- 真实双库：DataSourceIdentityMysqlTests，同时设置 `SMA_TEST_DB_URL` 与 `SMA_P10_JDBC_DB_B`，都必须是独立的 `sma_test_p10_*` 库；测试账号仅需这两个库的权限。此专项只操作 `common_p10_connection_probe`，不清空其他表；无 B 库配置时明确跳过，跳过不算双库通过。
- 入口/平台运维：TenantEntryScopeTests、PlatformOperatorTests；与 P09 的 ActorContextTests、ActorMethodSecurityTests 一起验证。
- 租户目录/迁移/HTTP：TenantDirectoryTests，使用专用 MySQL 库。现有 IntegrationTest 会清空指定测试库，严禁设置为开发库/生产库或其他并行任务库。
- 结构与双语：ModularityTests、ArchitectureRulesTests、I18nBundlesTests。

本轮实际专用库、日志与每项结果写在专项进度；测试库保留供续作，不在本轮删除用户服务、数据库或全局缓存。

### S4品牌管理与预览（验收状态查进度）

新版入口 `/tenant/brand`，要求tenant:brand:view，编辑/发布/回退分别检查tenant:brand:edit/publish/rollback；上传还需file:file:upload。草稿与发布分开，历史回退建立新修订并保留当前草稿。显示“结果尚未确认”时使用核对结果；未观察到回执可能仍在执行，不自动重发，也不能通过普通缓存清理“取消”。

`/brand-preview`仅承载本地编译的展示组件，不读取入口/草稿API。父页通过经过origin/source及类型/大小检查的postMessage传递临时视觉内容；无登录提交、无偏好写入。服务端预览使用有界安全PNG，响应no-store，私有原图不会因预览获得匿名URL。静态部署需允许该前端路由，以及现有CSP允许的站内frame和data:image/png素材；本地预览不代表生产CSP已部署验证。

新增验证：BrandPreviewTests、BrandAppearanceTests、BrandHttpTests以及PreferenceTests；前端`e2e-tenant/management.spec.ts`和`appearance.spec.ts`仅next，旧端对应跳过不是通过。真实整页使用隔离runner的`brand-management.spec.ts`，与`tenant-entry.spec.ts`共同验证实际上传/预览/发布/皮肤/回退和双端读取。使用独立端口与当前已验证JAR，不能复用用户登录或默认数据库。

### S4c域名验证部署（本地集成及隔离目录联调已通过）

类型化配置前缀为`sma.tenant.domain-verification`，启动读取：

| 配置 | 默认 | 约束与用途 |
| --- | --- | --- |
| ingress-addresses | 空 | 最多32个精确IP字面量；空表示未配置外部验证，不接受hostname/CIDR |
| dns-timeout | 1s | 100ms～2s，单次DNS解析预算 |
| connect-timeout | 2s | 100ms～5s，TLS连接预算 |
| total-timeout | 12s | 1s～12s，整次探测预算；不得小于上述单次预算 |

部署方先配置反向代理、443端口及有效证书，再把实际对外入口IP加入清单；管理者按页面挑战添加`_sma-tenant.<hostname>` TXT。验证要求所有解析出的IPv4/IPv6地址都在清单内、各地址的TLS信任/主机名/期限均通过。证书信任使用运行JVM的信任库，私有CA由部署管理，不在租户表单关闭校验。检查不会申请、安装或续签证书。

成功检查只保存证据；显式启用还要求租户ACTIVE、当前挑战对应的近期成功证据。现有ACTIVE入口重新检查失败时，上次有效证据保留到证书截止；换证后须重新检查以更新期限。停用入口提升租户安全代次，会使该租户既有认证材料失效，重新启用不恢复旧会话。最后一个有效的平台admin入口不能通过域名API关闭。

Tenant V5仅追加验证字段和检查表。迁移不伪造外部域名的控制权或TLS证据，只有原来显式登记的DEPLOYMENT回环入口保留LOCAL语义。旧版手工写成ACTIVE但没有新证据的外部记录会被新版拒绝，升级前须纳入入口切换计划；不要用SQL填充READY规避验证。当前开发期未承诺新旧域名生命周期共存，发布须先完成S4验收，再同批部署前后端；单退旧程序会失去证据期限检查，不能视为安全回退。

域名检查回执保留原操作者/输入版本和租约，UNKNOWN或未找到结果先对账，不能改operationId自动重发；容量上限触发时先处理运维归档方案，不直接清理在途回执。验证命令包括DomainLifecycleTests、DomainLifecycleHttpTests及DomainProbe/DomainDns/DomainTlsProbeTests。DomainSchemaUpgradeTests只允许专用空库构建V4后升级V5，已升级库不能再次作为该测试的空库输入。

next目录页面的模拟浏览器入口为`npx playwright test directory.spec.ts --config playwright.tenant.config.ts --project=next`。真实目录入口为`node scripts/run-real-e2e.mjs tenant-directory.spec.ts`，显式设置`SMA_E2E_PLATFORM_OPERATOR=1`及独立端口、当前已验证JAR；runner在自己的随机库通过正常引导创建管理员后，只重启自己创建的后端子进程，将该账号ID显式加入部署运维名单。默认runner不添加此身份，不修改用户服务或把任意测试账号作为默认平台操作者。真实目录测试不配置公网入口IP，不实际发起公网DNS/TLS；网络验收另有可控本地夹具证据。

### 共享参数切换约束（S5本地验收完成）

Config V7保留旧列，但不等于允许旧写入程序共存：旧版save不会推进parameter_revision，也不知道继承标记。启用共享参数读取前，须隔离写流量并退出旧宿主/旧worker，再执行目录登记与逐租户绑定；就绪前不接配置写入。业务ParameterQuery签名保持兼容，旧管理HTTP对已登记键的无版本写入明确拒绝；双前端与新管理HTTP已在S5e/S5f同批本地验收。只回退JAR不能恢复已发布共享值和已恢复继承的租户语义，须先停写并按保留数据核对/恢复。具体已验证范围查tenant-platform-progress.md。

### 共享字典与管理端切换（S5本地验收完成）

Config V9/V10追加共享代码归属摘要、租户字典层及单调版本。升级前备份旧字典类型、项目和翻译，停止旧宿主及旧写入worker；启动按可信租户目录逐租户短事务迁移。旧私有项与共享代码撞名、父级不匹配、树超限或契约漂移时停止就绪并保留原行，核对冲突后再重启，不通过手工修改归属摘要或抹除历史代码绕过。已发布代码停用后仍占用原代码，恢复继承会推进租户版本；只回退JAR不会自动恢复旧语义。

新版`/config/parameters`和`/config/dictionaries`区分有效共享层与未登记私有项，`/config/shared`只对部署平台运维身份及相应shared权限开放。租户写入必须同时携带平台发布版本和租户版本及原Idempotency-Key；网络结果未知时先按原键查回执，保存草稿与发布是两个独立动作。旧版管理端只读显示有效层和来源，私有配置继续使用原管理页；它不提供已登记键的无版本编辑入口。S5e已通过双前端verify、模拟浏览器和隔离真实联调，验证入口为`frontend-next/e2e/config-layers.spec.ts`及`frontend-next/e2e-real/config-layers.spec.ts`；真实runner使用独立端口、随机库、任务引导账号和显式平台运维名单。S5f根reactor完整verify在专用库通过，backend 1124项中57项因独立旧库或外部条件等跳过；逐版升级和完整证据见[专项进度](tenant-platform-progress.md)。前后端须同批部署，外部环境激活另行验收。
