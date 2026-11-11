# 首版运行与部署

后端 Java 21、MySQL 8；前端为独立静态站点。开发环境与工具链见 [dev-environment.md](dev-environment.md)。

## 构建与验证

在项目根目录执行：

```powershell
pwsh -NoProfile -File tools/dev/verify-backend.ps1            # 本地默认 mvnd
pwsh -NoProfile -File tools/dev/verify-backend.ps1 -Standard  # 标准 Maven Wrapper
pwsh -NoProfile -File tools/dev/verify-frontend.ps1
node --test tools/openapi/client.test.mjs
```

后端产物 `backend/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar`；前端产物 `frontend/dist/`。集成测试仅使用专用测试库并会重建表，不能把测试连接改成业务库。

## 配置

| 环境变量 | 用途 |
|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | MySQL 连接目标 |
| `DB_USERNAME` / `DB_PASSWORD` | 业务库账号与口令；生产自行设置 |
| `SMA_ADMIN_PASSWORD` | P07 后首次初始化 `admin` 使用 8～12 位字母数字组合，拒绝弱密码；已有管理员不覆盖，初始化后移除该环境变量 |
| `SMA_AUTH_SECURITY_KEY_FILE` | P07 TOTP 加密与安全密码 pepper 的独立密钥环文件；格式、轮换与测试边界见[认证安全运维](auth-security-operations.md) |
| `SMA_APP_MASTER_KEY` | Base64 编码的 32 字节 AES 主密钥，应用密钥加密使用；需持久保存 |
| `SMA_FILE_DIRECTORY` | 持久上传目录，对应 `sma.file.directory` |
| `SMA_MESSAGE_SOCKET_ALLOWED_ORIGINS` | 若前后端不同源，设置明确的前端来源；默认本地 5173 |
| `JAVA_TOOL_OPTIONS` | UTF-8 编码；本机已设置文件/标准输出/标准错误为 UTF-8 |

生产环境需提供主密钥；本地未配置时自动生成 `data/keys/openapi.key`。应用重启不能丢失/更换主密钥，否则已存 AppSecret 无法解密。上传目录、密钥目录均在 `.gitignore`；备份与恢复时分别处理数据库、上传文件和主密钥。

本机开发启动脚本 `run-backend.ps1` / `start-with-admin.ps1` 在未显式设置 `SMA_AUTH_SECURITY_KEY_FILE` 时，会使用已存在的 `data/keys/auth-security.keys`，不自动生成或覆盖。显式环境变量优先；密钥文件需独立保管并限制读取权限。2026-11-11 本机8080已部署P07及账号安全菜单，前端地址为 `http://127.0.0.1:5174/`，详见[实际部署记录](auth-security-progress.md)。

Flyway 启动时逐模块迁移，不能手动修改已提交迁移脚本。数据库 JDBC 会话固定 UTC；系统、反向代理和用户显示时区可不同。

## 静态站点与代理

使用同源部署较简单：静态站点根目录为 `frontend/dist`；所有前端路由回退 `index.html`；`/api/**`、`/open/**` 转发到后端。`/api/message/ws` 需要 WebSocket Upgrade 转发；限制代理日志，避免记录一次性握手票据及认证头。后端默认端口 8080。

```nginx
location / { try_files $uri $uri/ /index.html; }
location /api/message/ws {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 75s;
    access_log off;
}
location /api/ { proxy_pass http://127.0.0.1:8080; }
location /open/ { proxy_pass http://127.0.0.1:8080; }
location /actuator/health { proxy_pass http://127.0.0.1:8080; }
```

正式网络入口使用 HTTPS。上传代理上限至少 21 MiB（单文件上限为 20 MiB）；开放 API 正文上限 64 KiB。不要把后端 `data` 上传目录当作公开静态目录。应用管理密钥、Bearer、验证码和参数敏感值不进入访问日志/链路正文采集。

P07 将认证限流、TOTP 挑战及其消费状态持久化到数据库；WebSocket 票据和连接仍不跨实例共享。首版仍运行单实例，扩展多实例前应完善票据/消息分发及迁移锁部署规范。应用 nonce 与令牌已使用数据库持久化和唯一约束。P07 当前开发与验收状态见[实施进度](auth-security-progress.md)，不能据文档假定现有运行进程已升级。
