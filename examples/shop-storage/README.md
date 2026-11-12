# 静态业务分库接入示例

本目录不参加main构建，不是客户业务。复制到独立业务分支或隔离验证工程后才被宿主发现。基线状态见[单实例二开进度](../../docs/secondary-development-baseline-progress.md)。

业务标识/包为`shopdemo`/`com.sma.business.shopdemo`，存储组`shopdemo`，权限`shopdemo:order:create/view`，表前缀`shopdemo_`。示例只包含服务、业务表迁移、业务缓存和同资源Outbox投影；实际HTTP/页面及PermissionCatalog由业务按公开治理契约接入，配置字典接入见[二开规则](../../docs/agent-rules/secondary-development.md)。

1. 在业务分支合入`backend/src/main`内容。启动发现目录，但不会把`db/business/shopdemo`脚本迁到平台库。
2. 部署实现`StorageCredentialResolver`，由秘密引用取得凭据；平台操作者通过目录API登记物理库和`shopdemo`绑定（PENDING），再通过原生provision操作开通。业务账号不能自行授予平台运维身份。
3. 开通执行独立框架历史与`flyway_schema_history_business_shopdemo`，业务表建在绑定物理库。缺目录/脚本/凭据失败留待核对，检查后才能显式重试。
4. `OrderService.create`在固定业务会话内写订单、失效代次与Outbox；相同操作键重放原回执，不重复创建。`count`先校验业务权限，再读固定源的共享缓存。两方法均不要额外套平台`@Transactional`。
5. 宿主已打开资源的Outbox worker调用`OrderCreatedConsumer`，按稳定事件键创建本地投影。消费者不发送外部消息；绑定资源不一致时保留待核对，不盲目切库重投。

`OrderCreatedConsumer`展示至少一次投递下的同资源幂等。调用者丢失响应时用`receipt(originalKey)`核对，不换键重试未知结果。业务迁移只能追加版本，不能修改V1。已有ACTIVE业务库升级需在维护窗口按相同历史/位置执行并校验新的业务迁移，再部署匹配制品；本示例的初次provision重放不会重复执行升级，自动升级编排与运维页面尚未交付。

验证用例在本目录`backend/src/test`，由隔离验证工程运行；真实业务不依赖基座内部测试工具。该测试的部署/数据库装配属于测试夹具，示例生产源码只依赖公开契约。

业务测试位于`com.sma.business`，须在`@SpringBootTest(classes=SpringbootModulithAdminApplication.class)`显式指定宿主启动类；不能依赖向上查找`com.sma.admin`下的Boot配置。测试环境必须使用专用可清空平台库与业务库，禁止复用实际租户资源。

业务会话准入依赖真实宿主节点租约；即使单实例也须保持节点心跳有效。基座test profile默认关闭心跳，本验收测试显式启用独立节点组并等待租约，不使用无租约的兼容构造器替代生产装配。

首次业务写入前还须配置部署级`SMA_SECURITY_REQUESTS_FINGERPRINT_KEY`（Base64编码的32字节随机密钥），重启保留，轮换保留未退役回执所需旧键；缺失时幂等写入拒绝。测试里的固定零值仅供隔离夹具，禁止用于部署。验收会清空已精确核对名称的业务测试库，平台测试库由基座test监听器清空。
