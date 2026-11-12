# 单实例二开基线：2026-11-12实施批次

状态：2026-11-13本地单实例二开验收通过，B1～B4完成。可基于本次固定输入开始静态业务模块二开；完整X2/X5继续后补。完整历史见[基线进度](secondary-development-baseline-progress.md)。

## 支持范围

Java 21、MySQL 8、单Spring Boot宿主、静态Modulith业务模块。平台数据使用平台库，业务按可信租户与已登记存储组选择MySQL资源；可重建业务缓存使用本地缓存或部署共享Redis。租户品牌、登录模板、入口解析和共享/私有配置沿现有公开契约接入。

此基线用于首批业务开发和单实例接入验收，不替代完整X2/S8、多实例X5或生产换库验收。源码包中的历史进度与其他专项说明，不应扩大本文件列出的支持范围。

## 二开固定接入点

| 需求 | 接入方式 |
| --- | --- |
| 业务源码 | 独立业务分支，后端`com.sma.business.<module>`，前端`src/business/<module>`；真实业务不合入基座main |
| 业务表和迁移 | `BusinessSchemaCatalog`声明存储组，`db/business/<module>`，独立`flyway_schema_history_business_<module>`；模块最多31个小写字母，`ledger`保留；正式provision完成框架及业务迁移后才激活 |
| 业务事务与幂等 | 事务外`BusinessLedger.open(group)`，在`Session.executeLocal`中写本模块Mapper；订单、幂等回执、互斥、失效代次和Outbox属于同一物理库 |
| 读取与缓存 | `Session.query`或`BusinessCache`；`BusinessCacheCatalog`声明DATA区域，写入通过`Session.invalidate`；不直接接入RedisTemplate或平台版本表 |
| Outbox | `Session.enqueue`与业务同事务，`BusinessOutboxConsumer`按稳定事件键幂等；外部接收方另有去重/对账协议 |
| 字典和参数 | `ConfigurationCatalog`登记业务前缀，使用`ParameterQuery`、`DictionaryQuery`或`EffectiveConfigurationQuery`；保留共享发布与租户覆盖双版本 |
| 权限和页面 | 使用业务权限前缀和`PermissionCatalog`；后台HTTP遵守认证、对象授权与请求治理，教学存储样例不包含完整HTTP/页面 |
| 物理凭据 | 部署提供`StorageCredentialResolver`，平台管理接口只登记秘密引用；连接凭据不进入业务接口、前端或源码包 |
| 公共登录入口 | `/api/tenant/entry`；旧`/api/config/parameters/public`不再作为公共入口 |

可复制的服务级教学样例：[shop-storage](../examples/shop-storage/README.md)。详细约束见[存储契约](secondary-development-storage-contract.md)、[缓存契约](cache-secondary-development.md)、[二开规则](agent-rules/secondary-development.md)。

## 运行与升级约束

- 单实例也须启用节点心跳，当前进程取得有效租约后再开放业务流量。业务会话使用try-with-resources关闭；不得先开启平台事务再切库。
- 必须配置独立随机请求指纹密钥，重启保留，轮换保留未退役回执所需旧键。样例测试中的固定零值仅供夹具。
- 业务库初次开通与ACTIVE库升级是不同流程。已有ACTIVE库在维护窗口按相同位置/历史追加迁移并validate，再部署匹配制品；不能重放旧provision成功回执来代替升级。
- 当前Outbox只轮询已打开物理资源，冷启动不会自动打开全部绑定。持续投递场景须准备资源打开与积压监控；全目录自动发现、预热后补。
- 缓存命中仍有业务源解析、持久会话准入及业务库代次读取开销。连接与内存预算见[存储契约](secondary-development-storage-contract.md)；本轮没有生产吞吐/P95承诺。
- 后续专用Redis与运维功能优先在宿主适配层扩展；业务继续依赖上述公开契约。破坏式变更必须遵守[升级与兼容规则](agent-rules/platform-upgrades.md)。

## 验证记录

不同轮次的专项不能相加冒充一次完整回归；未配置外部环境的跳过项单列。71项条件跳过包括独立三库/Redis、旧结构升级、非默认引导、HTTPS与文件系统条件；本基线所需的业务库/共享Redis/引导/Outbox已另行验证，其余跳过项不计为通过。

| 范围 | 本轮证据 |
| --- | --- |
| 完整根reactor | full4共1465项：1394通过、0失败/错误、71条件跳过；所有模块BUILD SUCCESS（2026-11-13 00:31，55:20） |
| 新前端 | 完整`npm run verify`通过，62文件318单测，类型/ESLint/i18n/路由/生产构建/发布预算通过 |
| 旧前端 | 标准验证通过，28文件55单测、构建/ESLint/i18n通过 |
| 业务接入样例 | 22/22：真实宿主发现、开通、订单提交/回滚、缓存失效、原键重放、跨租户、Outbox投影、权限及租约释放 |
| 实际打包登录/品牌 | real2 2/2，覆盖品牌编辑/上传/预览/发布/回退、双前端入口和旧公共入口移除 |
| 审计与重启 | 实际4条成功登录审计、12条成功操作审计入库；validate模式重启后发布品牌和审计行保留；临时资源已清理 |
| 共享Redis与业务库 | 平台＋双业务MySQL与真实Redis16/16；提交/回滚、源修订隔离、跨租户、故障回源；小容量边界另13/13 |
| 首次引导 | 默认/空目录及非默认平台ID=42分轮验证，当前非默认引导＋结构20/20 |
| 组合夹具修复 | 摘流9/9、安全采集/查询/容量16/16、Socket→规则真实网络2/2；原失败日志保留 |

修复的运行缺陷包括：平台/静态业务/动态业务MyBatis工厂统一绑定持久号段；打包运行的异步事件线程固定宿主类加载器并保持请求上下文隔离。完整失败历史及各日志路径见[基线进度](secondary-development-baseline-progress.md)。

## 固定输入与交付

Git基线分支：`codex/single-instance-baseline-20261112`。仓库中保留[验收摘要](secondary-development-baseline-evidence.json)和[原交付字节清单](secondary-development-baseline-delivery-manifest.json)。本次提交来自上述已验收源包，不采集原工作区后续改动；Git按`.gitattributes`统一行尾，旧前端字节基线相应同步。原交付字节清单用于核对原始源包，不用于要求不同平台checkout保持相同行尾。构建输出、本地数据库与运行凭据不进入Git。

Git提交中的旧前端基线覆盖122个版本化文件，移除6个本地编辑器/日志/target产物条目，9个文件仅同步行尾摘要；没有删除运行源码或减少业务断言。已执行的[打包审计与重启验收脚本](../frontend-next/scripts/run-p10-baseline-real.mjs)一并保留；按现有隔离runner配置测试MySQL、独立端口和已构建JAR，设置`SMA_E2E_LEGACY=1`、`SMA_E2E_PLATFORM_OPERATOR=1`后，从frontend-next运行`node scripts/run-p10-baseline-real.mjs brand-management.spec.ts tenant-entry.spec.ts`。脚本只操作自身临时库和进程，不配置生产凭据。

- 最终输入：`backend/target/p10-final-snapshot/verified-input-manifest-final.json`，2910文件，SHA256 `2e359b972ddcfcb435128649ec86dc92475f62421b4c9543c3833ffc35ac6d2e`。
- 已通过实际浏览器/审计/重启的JAR：SHA256 `cc398429f21752a1dcdab621e14e44443892f9695b0f1390147ea57f978310dd`。最终快照与该JAR来源的生产代码/资源一致，后续变更仅为已验证的测试夹具。
- 交付目录为`backend/target/p10-stable-dev-20261112/`，包含`source.zip`、`backend.jar`和`evidence.json`。交付清单记录源码、文档及制品摘要；不把当前并行工作树的HEAD当作本次验收版本。
- 后端复验入口为根`tools/dev/verify-backend.ps1 -Standard`；本轮用独立输出目录及`-Dspring.test.context.cache.maxSize=8`。运行前配置专用可清空测试库；三库/Redis/引导/外部资源专项按各自环境单独运行。

专用Redis及在线改绑（X2b/S8）、SQL数据源运维UI（S9）、多实例/全业务搬库/云集成增强和X5继续后补。
