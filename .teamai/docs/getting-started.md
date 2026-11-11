# 成员上手

1. 获取当前应用仓库 `https://github.com/ice-juice/spring-modulith-admin` 的访问权限并下载项目。项目根目录是同时包含 `README.md`、`backend/`、`frontend-next/` 和 `.teamai/` 的目录；本机为 `E:\juiceProgram\spring Modulith`。
2. 安装 Node.js 20+ 和 `teamai-cli`，在项目根目录运行 `teamai init --self --scope workspace --provider git`。本项目使用同仓模式，不再加入独立的 TeamAI 仓库；各自按权限配置项目远端认证。
3. 运行 `teamai pull`、`teamai status`、`teamai doctor`，确认资源来自当前项目 `.teamai/`。小九AI 的自定义技能目录是否自动同步，以实际检查为准；无法自动同步时手动运行 `teamai pull`。
4. 共享资源维护在 `.teamai/`；框架与业务开发规则以根 `AGENTS.md`、目录级规则和 `docs/agent-rules/` 为唯一依据。通用资源随基座主线管理，业务专属资源随业务分支管理。
5. 统计上报、会话发布与自动 recall 默认关闭。环境变量定义只保存非秘密配置；凭据在个人本机配置，不进入仓库。移除本机接入时用 `/teamai 卸载 TeamAI`。
