# 方案索引

P01～P03 已于 2026-11-10 确认，确认结果写在各文档末尾的“决策记录”。后续方案以各文档及下表状态为准，草案不代表已实施或已确认全部细节。

| 编号 | 方案 | 结论摘要 | 状态 |
|---|---|---|---|
| [P01](P01-rename-springboot-modulith-admin.md) | 项目改名为 Springboot Modulith Admin | 根包 `com.sma.admin`，库与账号均为 `springboot_modulith_admin` | 已实施 |
| [P02](P02-mvnd-build.md) | 引入 mvnd 加速构建 | mvnd 本地默认、mvnw 标准构建，两者统一为 Maven 3.9.16 | 已实施 |
| [P03](P03-i18n-architecture.md) | 管理后台多语言（i18n）架构 | zh-CN、en-US；用户数据只翻译菜单、字典、参数名称；数据库存 UTC，按用户时区显示 | 已实施 |
| [P05](P05-versioned-api-extension-security.md) | 业务模块热更新、JAR 分组与安全发布 | 通用扩展底座，分组管理，单人强化验证发布，哈希与签名校验，首版配额及依赖 / 漏洞 / 例外规则 | v3 方案，默认策略已设计，未实施 |

建议执行顺序：P01 改名 → P02 构建工具 → P03 i18n 底座（在第一个业务模块之前完成）。

