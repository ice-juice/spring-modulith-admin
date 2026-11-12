# 方案索引

P01～P03 已于 2026-11-10 确认，确认结果写在各文档末尾的“决策记录”。后续方案以各文档及下表状态为准，草案不代表已实施或已确认全部细节。

| 编号 | 方案 | 结论摘要 | 状态 |
|---|---|---|---|
| [P11](P11-cross-stream-resource-ownership.md) | 跨专项资源与账本归属 | 同事务账本随业务资源走、Redis 连接归属、预算 scope 登记、品牌验收提前、联合验收 | 已确认；约束 P08/P09/租户平台/安全专项的后续阶段 |
| [P12](P10-security-defense-and-branding.md)（原“安全专项”，文件名保留以免断链） | 安全防御、攻击监控、前后端防重复操作 | 保持密码策略；分层防护、限流/幂等、诱捕与可靠告警；品牌只做安全验收，设计归租户平台 | 已授权；[专项进度](../security-defense-progress.md)逐项验收 |
| [P10](P10-tenant-branding-and-storage.md) | 租户品牌、登录模板、配置分层、业务分库与 SQL 数据源统计 | 域名入口、品牌发布、login1/2/3、共享/独有配置、MySQL/Redis 绑定、删除旧公共参数 HTTP；按实际连接统计 | 已授权实施；[专项进度](../tenant-platform-progress.md)逐项验收 |
| [P08](P08-cache-governance-and-cluster.md) | 统一缓存治理与多实例访问连续性 | Local/Redis/None、统一目录与二开约束、可靠失效、会话/票据/消息/文件及负载均衡验收；[主键分配](P08-id-allocation.md)、[共享文件见证](P08-shared-file-witness.md)补充 | 已授权分阶段实施；以[实施进度](../cache-governance-progress.md)为准 |
| [P01](P01-rename-springboot-modulith-admin.md) | 项目改名为 Springboot Modulith Admin | 根包 `com.sma.admin`，库与账号均为 `springboot_modulith_admin` | 已实施 |
| [P02](P02-mvnd-build.md) | 引入 mvnd 加速构建 | mvnd 本地默认、mvnw 标准构建，两者统一为 Maven 3.9.16 | 已实施 |
| [P03](P03-i18n-architecture.md) | 管理后台多语言（i18n）架构 | zh-CN、en-US；用户数据只翻译菜单、字典、参数名称；数据库存 UTC，按用户时区显示 | 已实施 |
| [P05](P05-versioned-api-extension-security.md) | 业务模块热更新、JAR 分组与安全发布 | 通用扩展底座，分组管理，单人强化验证发布，哈希与签名校验，首版配额及依赖 / 漏洞 / 例外规则 | v3 方案，默认策略已设计，未实施 |
| [P06](P06-rule-engine-and-management.md) | 通用规则引擎与场景模板管理 | 四种编辑；next 中采用 Vue Flow + Monaco；专业制模板、运营选用；[实施进度](../rule-engine/progress.md)按后端 B / 前端 F 分开跟踪 | 后端B0～B4完成；新版规则界面按F0～F4实施和验收 |
| [P07](P07-totp-and-security-password.md) | TOTP、登录密码策略与安全操作密码 | 绑定后分两步登录；锁屏仅验证登录密码；登录密码 8～12 位字母数字组合并拒绝弱密码；安全操作密码 6～8 位字母数字组合，两种密码双向不同；首版无弱验证恢复入口 | S0～S5本地实现与验收完成，已部署本机当前服务；证据见[实施进度](../auth-security-progress.md) |
| [P09](P09-identity-mail-storage-and-secure-upload.md) | Google 登录、邮件、多云存储与安全上传 | Google/邮件/存储基础配置按租户独立；应用身份域、事务邮件、Local/OSS/S3/R2、分片直传、隔离扫描及旧文件兼容 | 已授权分阶段实施，先完成本地验证；[进度](../integration-services-progress.md)、[二开手册](../integration-services-developer-manual.md)随验收更新，外部联调单列 |

建议执行顺序：P01 改名 → P02 构建工具 → P03 i18n 底座（在第一个业务模块之前完成）。

编号说明：仓库曾有两份 P10。租户平台保留 P10；安全专项改称 P12，文件名 `P10-security-defense-and-branding.md` 保留不动以免断开既有链接和进度引用。跨专项约束为 P11。


[P08 部署配置与密钥一致性证据](P08-deployment-evidence.md)：M3b2宿主证据SPI、实际密钥环、模块白名单与清单/租约绑定的细化方案；实际状态见缓存进度。

[P08 受控摘流与排空](P08-deployment-drain.md)：M3c2共享准入/工作许可、worker与WS、本机控制、HTTP及实际进程排空的分项方案；完成范围以缓存进度为准。
