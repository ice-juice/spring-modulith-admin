# P10 运行与验证说明

交付范围以[实施进度](tenant-platform-progress.md)为准。当前尚未完成整套租户平台，不将此文件当成生产上线许可；完整设计见[P10](proposals/P10-tenant-branding-and-storage.md)。

## 平台数据源的统计身份

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

`tools/dev/run-backend.ps1`、`tools/dev/start-with-admin.ps1`及隔离E2E runner已显式传入这三个回环入口并启用health probes；生产启动默认仍为空。完成迁移和ApplicationReady初始化后再以`/actuator/health/readiness`的UP作为可登录条件，普通health UP不代表启动迁移已完成。两套Vite代理保留原Host/Origin，不能靠changeOrigin把所有域名改成localhost。

这些配置只在服务启动读取。当前阶段未切换用户正在运行的服务；本分支S2入口一致性与会话安全代次已通过本地真实MySQL/HTTP/WS专项。外部DNS/TLS、品牌页面与后续分库仍有独立验收，不能以S2通过宣称完整租户平台交付。

`sma.tenant.entry.trusted-proxy-addresses` 默认空，仅接受精确IP字面量；默认忽略来自其他来源的转发头。受信代理必须覆盖客户端提供的转发头并提供单值X-Forwarded-Host和X-Forwarded-Proto；优先直接保留原始Host。`server.forward-headers-strategy` 必须保持none，防止框架/native在本入口策略之前信任未经校验的转发信息；不兼容配置拒绝启动。外部scheme/host/port同时用于入口与Origin检查。

auth V5与OpenAPI V3新增租户安全代次。升级前已有的后台会话、验证码、MFA挑战及机器bearer没有可信代次，升级后需重新登录/领取/签发；不修改密码策略或复制旧认证材料成为新会话。暂停及恢复都推进代次，恢复不复活旧材料。迁移只加列，回退旧程序会失去此停用防护，因此应先停入口并撤销旧材料，不能把单退JAR描述为安全回退。

安全代次通过同一事务连接的InnoDB当前读取得，Mapper明确关闭一二级查询缓存，不从普通展示缓存读取。这样不会为每次状态核对再借第二条连接；共享锁在已有事务结束时释放，暂停写操作会等待这些在途事务，接口返回成功后新校验必须观察到新代次。不要通过扩大池大小掩盖嵌套借连接问题，也不要用旧REPEATABLE_READ快照替代此当前读。

## 本任务验证入口

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

### 共享参数切换约束（S5实施中）

Config V7保留旧列，但不等于允许旧写入程序共存：旧版save不会推进parameter_revision，也不知道继承标记。启用共享参数读取前，须隔离写流量并退出旧宿主/旧worker，再执行目录登记与逐租户绑定；就绪前不接配置写入。业务ParameterQuery签名保持兼容，旧管理HTTP对已登记键的无版本写入明确拒绝；双前端与新管理HTTP在S5e/S5f同批验收后才能上线。只回退JAR不能恢复已发布共享值和已恢复继承的租户语义，须先停写并按保留数据核对/恢复。当前仍是中间实现，具体已验证范围查tenant-platform-progress.md。
