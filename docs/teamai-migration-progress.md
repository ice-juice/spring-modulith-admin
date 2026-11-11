# TeamAI 单仓迁移进度

## 已确认方案

将原 `ice-juice/spring-modulith-teamai` 的共享资源迁入本项目根目录的 `.teamai/`，当前项目 `ice-juice/spring-modulith-admin` 同时承载应用与 TeamAI 资源。仅工作区启用，保留统计、会话发布、自动 recall、自动 MCP 与团队钩子关闭的设置。现有项目规则仍以 `AGENTS.md` 与专项文档为准，不复制成两份。

用户已授权迁移及清理旧独立仓库。先备份并验证资源保全与新绑定，再删除旧 GitHub 仓库和旧本地副本；备份保留在项目外供恢复，不参与同步。保留工作区其他改动。

## 任务与验收

| 顺序 | 任务 | 依赖 | 验证方法 | 状态 |
| --- | --- | --- | --- | --- |
| 1 | 清点、备份旧配置和仓库 | 无 | 确认资源清单、工作区状态及可恢复备份 | 完成 |
| 2 | 迁移资源，切换 workspace 单仓模式 | 1 | 配置指向本项目 `.teamai/`；规则和文档完整；统计开关保留 | 完成 |
| 3 | 验证同步与接入，更新 README 和代理规则 | 2 | `teamai pull`、`status`、`doctor`、`hooks list`；记录宿主限制 | 完成（宿主限制见下） |
| 4 | 清理独立 GitHub 仓库和旧本地副本 | 3 | 核对精确目标、备份存在、旧绑定消失、远端删除结果 | 进行中 |

## 执行记录

- 2026-11-11：Node 26.8.1 / TeamAI CLI 0.29.0。当前为 `main`，工作区有大量并行开发改动；本次只处理 TeamAI 配置与关联文档。
- 旧仓库资源：一份 `rules/team-collaboration.md`、一份 `docs/getting-started.md`；无技能、环境变量、代理或 MCP 定义。现有工具接入为 Claude、Codex、Copilot 和 Antigravity。
- 已更新旧仓库远端引用并备份完整分区、项目 `.teamai/` 和本地排除配置；`git bundle verify` 通过，含 main、reports、learnings 完整历史。备份：`C:\Users\hgc18\AppData\Local\TeamAI-backups\spring-modulith-20261111-173555`。
- `teamai init --self --scope workspace --provider git --force` 成功，`repo.kind: self`，本地资源为本项目 `.teamai/`；保留四种工具接入与原有关闭开关。CLI 自动生成提交 `4fe161b`，仅 TeamAI 资源及 Claude/Codex 钩子配置，未带入其他开发改动。
- `teamai pull` 成功同步 1 份规则和 1 份文档；`status` 确认当前应用仓库绑定；`hooks list` 确认 Claude、Codex、Copilot、Antigravity 的注册位置。
- `doctor` 已执行，资源、配置、Git 钩子和同步检查通过，但不是全绿：现有 CLI 不接受小九AI 深层 `CODEX_HOME`，仍按标准 `.codex` 注册；Copilot 尚未信任本目录。不修改宿主路径或扩大信任。手动同步项目资源已验证，小九AI/Copilot 自动会话接入未验收，这两项作为宿主限制保留。
- 共享配置关闭开关、旧规则逐字节比较、README/AGENTS/成员上手/迁移进度的本地链接及 `git diff --check` 通过。本次不涉及应用代码、构建或数据库，无需重启服务或执行应用测试。
