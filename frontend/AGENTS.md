# 旧版管理前端规则

P10跨端入口协议与租户边界统一遵守[租户平台规则](../docs/agent-rules/tenant-platform.md)。本工程兼容消费者位于stores/branding和views/LoginView；变更回归branding/login-ui单测、旧版标准验证及next工程的双前端`playwright.tenant.config.ts`和隔离`e2e-real/tenant-entry.spec.ts`。模板管理和分库交付状态见[P10进度](../docs/tenant-platform-progress.md)。

本文件补充[总规则](../AGENTS.md)。此目录是既有 Vue / Element Plus 管理端及兼容基线。只在明确涉及旧版维护时修改；新增页面或二开不要借机搬迁、改名或批量改写旧工程。与 `frontend-next/` 分别安装、构建和交付，禁止相互导入源码。

- 复用既有 API 客户端、菜单/路由与权限判断。后台校验权限，前端负责可见性与操作体验。用字符串保存 Long ID，用户可见文案及无障碍标签同步维护 zh-CN/en-US。
- 合同变化同时检查两套前端的受影响消费者；旧版维护若导致新版基线校验变化，先说明原因并核对差异，不能用更新哈希掩盖旧版改动。
- 从根目录运行 `pwsh -NoProfile -File tools/dev/verify-frontend.ps1`。若修改登录、路由或业务交互，补实际浏览器验证，并区分构建和人工验收结果。

P07 的旧版登录兼容使用独立 TOTP 页与账号安全页，API 契约见[认证安全 API](../docs/auth-security-api.md)。挑战只保留内存，二维码本地生成，写入不自动重试；锁屏清除敏感表单，解锁只输入登录密码并重新加载状态。同账号的语言/主题刷新不能丢失表单。新登录密码为 8～12 位字母数字，安全操作密码为 6～8 位字母数字；旧登录/解锁输入不套新设长度。旧版源码在本工程独立维护，不从 next 导入。回归 auth-mfa、account-security、password 单测及真实兼容场景；基线哈希仅在审阅有意变更后更新。

P10静态登录布局与MFA共用认证流程，禁止在模板复制认证请求。布局样式不应重建同身份表单；Logo只使用当前明暗槽，不能借另一槽绕过HIDE。版权/备案及外链复用安全地址检查。`e2e-tenant/layout.spec.ts`覆盖三模板、明暗、双语、窄屏、键盘和挑战延续；真实后端验收仍用隔离runner。皮肤策略/管理页实施状态查P10进度。
