# 方案索引

方案已于 2026-11-10 全部确认，确认结果写在各文档末尾的“决策记录”。

| 编号 | 方案 | 结论摘要 | 状态 |
|---|---|---|---|
| [P01](P01-rename-springboot-modulith-admin.md) | 项目改名为 Springboot Modulith Admin | 根包 `com.sma.admin`，库与账号均为 `springboot_modulith_admin` | 已实施 |
| [P02](P02-mvnd-build.md) | 引入 mvnd 加速构建 | mvnd 本地默认、mvnw 标准构建，两者统一为 Maven 3.9.16 | 已实施 |
| [P03](P03-i18n-architecture.md) | 管理后台多语言（i18n）架构 | zh-CN、en-US；用户数据只翻译菜单、字典、参数名称；数据库存 UTC，按用户时区显示 | 已实施 |

建议执行顺序：P01 改名 → P02 构建工具 → P03 i18n 底座（在第一个业务模块之前完成）。

