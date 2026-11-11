# 扩展管理统一后台入口：接线与迁移

正常页面使用已有后台登录，读取分组、版本、记录不再登录第二套账号。发布权限和操作密码/TOTP仍由独立发布服务验证。原后台“账号安全”中的密码/TOTP不自动复制到发布服务；首次绑定一次发布安全因素，日常只在敏感操作时验证。

## 部署配置

先按 [后端手册](backend-guide.md) 部署发布安全服务的独立MySQL、制品目录、加密密钥与HTTPS。CONSOLE机器凭据和NODE、WORKER、AUDITOR凭据不可复用，发布签名私钥及发布库访问权不能给宿主。

在发布服务中创建 `kind=CONSOLE`、`environments=["security"]` 的机器。初始部署可放入已有受保护 bootstrap 文件的 machines 列表；已有服务通过机器管理的强化授权创建并一次性领取token。将token保存到宿主仅服务账号可读的文件（Linux 0600；Windows ACL仅服务账号/SYSTEM/Administrators可读），配置：

```text
SMA_EXTENSION_CONSOLE_ENABLED=true
SMA_EXTENSION_CONSOLE_URL=https://release.internal.example
SMA_EXTENSION_CONSOLE_TOKEN_FILE=/etc/sma/console.token
```

URL只能是HTTPS站点根地址，无用户名、查询参数或片段。使用JVM信任的证书；内部CA可通过部署侧 `javax.net.ssl.trustStore` 安装信任，禁止trust-all或跳过主机名检查。地址/开关是启动配置，token文件每次请求读取，可按机器轮换协议原子替换。此开关独立于插件运行时 `sma.extension.enabled`。

浏览器仅走现有 `/api` 代理，具体入口 `POST /api/extension/consoles/requests`。`X-Release-Method`、`X-Release-Path` 只指定白名单内的操作路径，JSON或上传分片原字节放请求体；租户、portal、用户ID取宿主已认证上下文，不从请求头/正文接受。后台Authorization不转发到发布服务。前端不再需要 `/release` 代理或 `SMA_NEXT_RELEASE_URL`。

## 首个身份绑定

发布服务中保留现有发布账号和权限，以稳定后台用户ID绑定，不按可变用户名自动关联。

初始映射可在发布机创建受保护文件，内容如下（ID均为字符串；用实际部署值替换）：

```json
{
  "machineId": "admin-console",
  "subject": { "tenantId": "1", "portal": "admin", "hostUserId": "123456789012345678" },
  "releaseUsername": "release-admin"
}
```

发布服务启动配置 `SMA_RELEASE_CONSOLE_BINDING_FILE` 指向该文件。机器必须已存在、启用且为CONSOLE/security，目标发布账号必须存在。首次引导只允许创建映射；重复的相同初始映射可恢复，不能覆盖已修改/停用的映射。成功后删除输入文件；清除该一次性环境变量再做后续重启。已有 `SMA_RELEASE_BOOTSTRAP_FILE` 先于此映射引导执行，同样遵循一次性文件规则。无需修改数据库或给宿主签名权限。

后续在“后台账号绑定”中新增/编辑映射，动作 `IDENTITY_BIND` 需发布安全管理员的操作密码和TOTP。参数：

```json
{
  "machineId": "admin-console",
  "subject": { "tenantId": "1", "portal": "admin", "hostUserId": "123456789012345678" },
  "userId": "<发布账号ID>",
  "expectedRevision": 0,
  "enabled": true
}
```

新增修订为0，修改填写当前修订。一个发布身份只能有一个有效后台身份绑定，防止不同租户/自然人共享因素和权限。停用/重绑、机器凭据修订变化、发布凭据变化都会使旧连接及待授权操作失效。原有角色/环境/分组授权不由后台超级管理员标志自动扩大。

## 用户日常流程

1. 后台角色获得 `extension:console:view` 后进入“业务扩展 → 扩展管理”，自动加载本人发布权限。未配置、未绑定、停用分别显示明确状态；失败不返回伪造空列表。
2. 未绑定TOTP的发布账号可先读取已授权信息；点击“绑定安全验证”，输入注册票据和发布操作密码，添加验证器并保存一次性恢复码。普通页面不要求第二个用户名或登录密码。
3. 每次上传、发布、回滚或管理变更核对参数后，输入发布操作密码和新的TOTP。120秒挑战与60秒一次性grant绑定动作、制品hash、环境和版本，未知结果按原幂等键查询，不盲重试。
4. 修改发布操作密码走 `OPERATION_PASSWORD_UPDATE`，旧操作密码+TOTP验证后更新，其他旧会话失效。新建发布身份时可不指定独立登录密码，服务端产生不可预测随机值；旧 API 显式提供登录密码仍保留兼容。
5. 验证器丢失用发布操作密码+一次性恢复码进入重新绑定。恢复立即撤销旧会话与因素；恢复码不能重用。缺少密码/恢复码时由另一位发布管理员完成一次强化授权重置：`FACTOR_RESET` 的可选 `newOperationPassword` 留空保留原密码，填写则重置为新密码并撤销旧凭据。管理员不能借此重置自己绕过因素；唯一管理员同时遗失全部恢复因素时须走部署侧受控应急流程，不能用后台超管身份解锁。

## 会话、安全与故障边界

代理每次请求校验后台会话/锁屏/权限；安全服务独立校验CONSOLE机器和用户映射。控制面会话绑定后台会话摘要（不保存原token）、映射修订、机器修订和发布凭据版本，租约90秒，单会话绝对上限1小时。页面30秒只读心跳续租；刷新同一有效后台会话不要求额外登录，长时间离开后按当前权限建立新连接。挑战/单次授权不跨会话搬运。

浏览器不保存发布session、机器token或操作因素。网络请求与响应有上限、超时，不跟随重定向，不自动重试写请求。较长操作/准备过程遇到租约失效可能被阻断；回到页面重新核对状态。待激活发布和灰度自动回退还校验绑定/机器修订，撤销不能只挡新请求却继续执行旧授权。

宿主被攻陷时，攻击者可能读取该宿主绑定身份可代理的数据、窃取正在输入的因素；CONSOLE凭据本身不能生成已验证发布授权。服务器root/JVM完全失陷不能靠同JVM类加载校验保证安全。生产仍需独立发布服务、隔离凭据、独立审计与制品签名。

## 升级与验证

新增 `CONSOLE`、`IDENTITY_BIND`、`OPERATION_PASSWORD_UPDATE` 与独立 `console-*` 文档记录，不改已发布迁移、SDK或既有User/Session记录字段。先部署兼容新协议的发布服务，再部署宿主与前端；三者使用本轮验证组合。旧 `/auth/login`、enrollment/recovery 和机器接口保留，新版页面不再调用旧登录。回退到不识别新枚举的旧控制面可能无法解析已新增记录，不能仅替换旧JAR；应保存本次升级前受保护备份并按有状态回退流程执行。

验证入口：ConsoleIdentityTests、ConsoleProxyTests、IdentityServiceTests、CanaryGuardTests、ReleaseWorkflowTests、SecurityMysqlHttpsTests；前端 `npm run verify` 与 `npx playwright test --config playwright.extension.config.ts`。实际结果及未验证项见 [进度](unified-console-progress.md)。真实Docker/Grype、多节点及生产故障演练仍按既有P05清单独立验收。
