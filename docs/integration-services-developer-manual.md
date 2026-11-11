# P09 二开开发手册：应用身份、邮件与文件服务

状态：随实施更新，**尚未完成整套能力交付**。已验收范围、实际命令与未验证项以[实施进度](integration-services-progress.md)为准；本手册不会把设计中的方法写成已经存在的 API。

## 1. 接入边界

二开代码放 `com.sma.business.<bizid>`，管理页面放 `frontend-next/src/business/<bizid>`，实际业务在独立业务分支开发。业务模块通过公开 API/DTO/事件或 SPI 接入，不引用 `internal`、Mapper、基座实体或跨表 SQL。模块依赖在 `package-info.java` 明确声明。

支持目标为本地、OSS、AWS S3、R2。Google 应用 ID/凭据、邮件账号、存储空间均由各租户独立配置；配置缺失不使用其他租户或全局账号代替。前端传来的 tenantId、Bucket、SMTP 地址和 secret 引用都不能成为可信调用上下文。

## 2. 身份类型与方法授权

以下代码已经加入 `common :: security`，当前是身份安全基础，尚不代表 Google 登录/应用会话端点已上线：

- `SmaPrincipal`：现有后台人类/机器身份；`CurrentUser.require()` 只接受后台人类身份。
- `AppUserPrincipal`：应用用户，包含 subjectId、tenantId、realmId、clientId、audience、sessionId，由身份模块验证会话后建立；业务 Controller 不接受其 JSON 作为认证。
- `ActorContext.require()`：读取当前已验证的身份，返回 `ActorRef`；未认证或未知主体拒绝，不默认落到租户 1。ActorRef 只是引用/审计数据，传入一个 ActorRef 不能获得身份。
- `@PermitLoggedIn` / `@RequiresPermission`：仅允许后台人类主体，机器或应用用户即使带同名 authority 也不能进入。
- `@PermitMachine`：仅接受经过机器安全链验证的身份，现有 `/open/**` 登录后接口使用此注解；具体客户端 capability 和业务对象权限仍由所属服务检查。
- `@PermitApplicationUser("shop")`：只接受 audience 精确为 shop 的应用用户。注解仍须配合已注册的应用安全路由、租户过滤和对象授权；单独添加注解不会自动提供登录/URL 安全链。

示意业务模块声明（identity/mail/file 公开契约到对应阶段再补入）：

```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Shop", allowedDependencies = {"common", "common :: security"})
package com.sma.business.shop;
```

应用业务服务从认证上下文取得主体，不使用请求中的 userId：

```java
var caller = ActorContext.requireApplicationUser("shop");
long tenantId = caller.tenantId();
long subjectId = caller.subjectId();
// 用本业务拥有的 API/Mapper 校验该租户、主体是否能操作当前业务对象。
```

`created_by` 等旧审计字段指向 IAM 用户。应用用户请求下 `CurrentUser.userId()` 为 0；新业务应以明确的 `subjectId`/ActorRef 保存应用用户归属，不能把应用主体 ID 误当后台用户 ID。旧 `CurrentUser.optional()` 面对应用主体会拒绝，避免旧数据范围代码把它当作无身份系统任务而放行。

受信工作线程先从持久任务读取租户与工作 ID，然后恢复作用域：

```java
ActorContext.withinJob(trustedTenantId, trustedWorkId, () -> {
    // 这里是 SYSTEM_JOB；没有后台/应用用户的方法权限。
    // 根据任务所属模块的授权、配置版本与状态执行，结束后自动恢复上下文。
    return null;
});
```

该方法是受信服务端代码使用的基础设施，不能在匿名 HTTP 方法里把请求的 tenantId 直接传进来。当前已有已认证调用者时，不允许通过它切换到另一租户。异步执行必须显式恢复，不能依赖线程池继承 ThreadLocal。

## 3. 租户凭据保护与部署

`TenantConfigurationRef(tenantId, module, configurationId, configurationVersion, credentialVersion)` 标识 identity/mail/file 自己拥有的一份配置。`partitionKey()` 包含全部维度，可作为 SDK/配置快照隔离键的基础，不能只按 provider 或配置名称缓存。`requireCurrentTenant()` 检查可信当前身份；该引用不是授权票据，配置所属模块仍须先检查权限、查自己的租户表并核验启停状态。

已实现 `TenantSecretCodec`：使用 AES-256-GCM，HKDF-SHA256 按租户和模块派生密钥；认证附加数据绑定配置 ID、配置/凭据版本及用途。密文搬到另一租户、配置、版本或用途均无法解密。部署配置：

```yaml
sma:
  common:
    credentials:
      key-file: ${SMA_TENANT_CREDENTIAL_KEY_FILE:}
```

文件由部署人员在受保护目录创建，格式如下（占位符必须替换为密码学安全随机 32 字节的 Base64，不是账号密码；不要放进 Git/前端/数据库备份）：

```properties
active=key2026a
key.key2026a=<BASE64_OF_RANDOM_32_BYTES>
```

空路径允许基座启动，但凭据加解密不可用；配置了无效文件、重复 key、未知字段或错误密钥长度时启动失败。仅授予应用运行账号和部署管理员读取权限。配置文件启动时读取，更新需滚动部署，不能宣称热更新；多实例必须使用同一兼容密钥环。

密钥轮换：先部署同时包含旧/新 key 的环，将 `active` 切为新 key；旧密文可读，新密文用新 key。所属模块按原引用调用 `reencrypt` 并原子替换密文；确认历史配置、在途任务和可恢复备份均完成处理后才另行退役旧 key。配置版本变化时须用旧引用解密、用新引用重新加密，不能直接拷贝密文。更换加密主密钥不等于更换 SMTP/Google/云账号凭据。

底层适配示意（普通业务调用未来的 Mail/File/Identity API，无须取得明文凭据）：

```java
try (SecretValue secret = codec.decrypt(ownedReference, "smtp-password", storedEnvelope)) {
    char[] value = secret.copy();
    try {
        // 只交给已授权且绑定本租户配置的传输适配器。
    } finally {
        java.util.Arrays.fill(value, '\0');
    }
}
```

`SecretValue.toString()` 和 JSON 序列化均输出 `[REDACTED]`；关闭后不能再次读取。禁止把 secret、密文、验证码或带签名 URL 拼入日志或异常消息。该机制不改变已有后台 TOTP/OpenAPI 密钥格式，也不把各租户外部服务账号改成一套部署级账号。

## 4. 邮件账号管理 API（C2a，已通过本地验收）

以下管理接口已通过真实MySQL/MockMvc本地验收，C8管理页面待实施。账号管理接口不发邮件、不做外网连接；保存成功只代表配置入库。SMTP与调度能力及其本地/外部验证边界见第7章；实际运行服务尚未由本任务升级部署。

| HTTP | 权限 | 用途 |
| --- | --- | --- |
| `GET /api/mail/profiles` | `mail:profile:view` | 当前租户分页列表，可用 keyword 按名称筛选 |
| `GET /api/mail/profiles/{id}` | `mail:profile:view` | 当前租户配置详情，只有 passwordConfigured 标记，没有密码或密文 |
| `POST /api/mail/profiles` | `mail:profile:manage` | 创建独立 SMTP 账号配置与首个不可变版本 |
| `PUT /api/mail/profiles/{id}` | `mail:profile:manage` | 使用 expectedVersion 更新，保留历史连接版本 |
| `POST /api/mail/profiles/{id}/disable` | `mail:profile:manage` | 紧急停用，无须解密凭据；在途发送后续由实时启停门禁阻断 |

使用现有后台 Bearer 会话。tenantId 不在请求字段中，由后台已验证会话决定；其他租户的 ID 返回不可访问。应用用户与机器不能调用这些管理 API。新增权限需按现有 IAM 流程授权；管理前端完成前是逻辑权限，不生成指向不存在页面的菜单。

创建请求示例（密码占位符由当前租户管理人员替换，不应写入示例制品/业务源码）：

```json
{
  "code": "transactional",
  "name": "注册与交易通知",
  "host": "smtp.example.com",
  "port": 587,
  "tlsMode": "STARTTLS",
  "username": "mailer@example.com",
  "password": "<CURRENT_TENANT_SMTP_PASSWORD>",
  "fromAddress": "noreply@example.com",
  "fromName": "应用通知",
  "applications": ["shop"],
  "dailyQuota": 1000,
  "perMinuteLimit": 30,
  "enabled": true
}
```

TLS 模式支持 STARTTLS/TLS，未提供明文/忽略证书选项。applications 为本配置允许使用的应用编码；真实发送阶段还会校验调用者、业务场景、模板和出站网络策略。配置编码不可改名，可修改显示名称；不同租户可使用相同编码。

更新请求为 `{"expectedVersion": 1, "configuration": {上述配置字段}}`。password 省略、null 或空值表示保留现有密码；替换密码会增加 credentialVersion。响应中的 version 是管理并发版本，configurationVersion 是连接快照版本；停用只增加 version，不需要解密或改写历史快照。必须把最新响应的 version 用作下次 expectedVersion，不能假设它等于 configurationVersion。ID 和 Long 版本按字符串返回，前端不要转换成 JS Number。

遵守现有基座响应约定，不能只判断 HTTP 200：业务异常使用 HTTP 200 加非零 `ApiResponse.code`，如不存在/跨租户 code=404、编码重复/过期版本 code=409、业务参数错误 code=400、密钥未配置/无法解密 code=503；此时 data=null。未登录 HTTP 401、无管理权限 HTTP 403、请求参数绑定/Bean Validation 失败 HTTP 400。修改/创建失败不会留下半个配置版本，停用仍在密钥不可用时有效。未提供删除历史版本、跨租户复制凭据或导出明文密码的接口。

## 5. 邮件模板（C2b1，已通过本地验收）

管理接口为 `/api/mail/templates`：GET 分页/详情需要 `mail:template:view`，POST 创建和 PUT 更新需要 `mail:template:manage`，`POST /{id}/publish` 与 `/{id}/disable` 需要 `mail:template:publish`。写入使用 `expectedVersion` 防止覆盖旧页面；预览 `POST /{id}/preview` 只渲染变量，不发邮件、不记录变量或正文。

创建请求中的 code、application、scene、language 是稳定身份（本期语言 zh-CN/en-US），profileCode 选择本租户邮件账号；发布时核验该账号已启用且允许此 application。不同租户可使用同名模板，模板发布版本绑定具体账号配置/凭据版本。后续修改草稿不改变已发布内容；发布显式生成新版本，停用是实时安全开关。

```json
{
  "code": "shop.registration",
  "application": "shop",
  "scene": "registration",
  "language": "zh-CN",
  "profileCode": "transactional",
  "content": {
    "subject": "注册验证码",
    "body": "<p>您的验证码为 <strong>{{code}}</strong>，请勿向他人透露。</p>",
    "html": true,
    "variables": {
      "code": {"type": "TEXT", "maxLength": 6, "required": true}
    },
    "linkHosts": []
  }
}
```

模板只支持 `{{variableName}}`。变量必须全部声明，支持 TEXT、INTEGER、DECIMAL、BOOLEAN、HTTPS_URL；数值以字符串传入，避免金额/大整数被 JS Number 改写。未知变量、缺少必填值、超长值、主题中的换行均拒绝。不支持表达式、脚本、循环或远程 include。

HTML 限定基础排版标签，禁止 script、style、图片/追踪资源及事件属性。正文变量默认 HTML 转义；动态链接只允许完整 `href="{{resetUrl}}"`，变量类型须为 HTTPS_URL，URL 必须是已登记 linkHosts 中的 HTTPS 主机（不允许 userinfo 或其他端口）。静态链接也受同一域名白名单约束；模板不执行网络抓取。前端预览必须用隔离显示容器，不能把任意返回 HTML 注入管理主站。

更新格式 `{"expectedVersion": 1, "configuration": {完整创建字段}}`；发布/停用格式 `{"expectedVersion": 1}`。预览仅传变量键值，例如 `{"code":"123456"}`；真实验证码由 identity 模块生成/校验，不能把预览示例当作验证码服务。

## 6. 邮件任务公开 API（C2b2，已通过本地验收）

`MailSendApi.enqueue` 只把任务加入持久队列，返回 `MailDeliveryView`；不能把 QUEUED 当作已发送。它加入调用方现有数据库事务，业务回滚时任务一起回滚。仅适用于同一数据库资源；独立业务数据库/服务应在自己的事务保存 outbox，再可靠调用基座，不能假设跨库原子提交。

业务须实现公开 `MailSendPolicy`，返回自己拥有的 application，并在 `canSend(actor, command)` 中验证场景、实际业务对象与收件人归属；缺少策略默认拒绝。不要用“application 字符串相同就放行所有地址”作为真实业务授权。SMTP 账号和模板也必须允许该应用，策略不能覆盖租户配置和硬配额。

```java
MailDeliveryView delivery = mailSendApi.enqueue(new MailSendApi.Command(
    "shop", "registration", "shop.registration", "zh-CN",
    verifiedRecipient,
    Map.of("code", generatedCode),
    challengeExpiresAt,
    "registration:" + challengeId
));
```

收件人、变量和期限在调用前由所属业务/identity 确定；示例不代表允许客户端直接选择验证码或邮箱。幂等键在 tenant/application/scene 内唯一，同键相同内容返回原任务，同键不同内容拒绝；期限也是原请求的一部分，重试不能重新延长。请求指纹使用按租户/任务派生的 HMAC，不能对六位验证码使用可离线枚举的普通 SHA 哈希。

`MailStatusQuery.get(id)` 只返回脱敏收件人、状态、尝试次数和有限结果码；创建者可查询自己的任务，管理人员需 `mail:delivery:view`。`MailSendApi.cancel(id)` 只允许 QUEUED/CLAIMED，创建者或具有 `mail:delivery:manage` 的管理员可取消；已经进入 SENDING 的外部操作不能假装撤回。

管理 HTTP 为 `GET /api/mail/deliveries`（page/size/state/application 筛选）、`GET /api/mail/deliveries/{id}` 和 `POST /api/mail/deliveries/{id}/cancel`。列表/详情需要查看权限，取消需要管理权限；均限制当前租户并采用标准 ApiResponse。此门面没有任意发送端点，二开应在自己的业务路由里完成对象/收件人授权后调用公开 Java API。

传输生命周期为 QUEUED → CLAIMED → SENDING → ACCEPTED/FAILED/UNKNOWN；安全可重试错误回到 QUEUED 并有限退避。ACCEPTED 仅表示 SMTP 接受。`receiptState` 单独表达可信回执结果 DELIVERED/BOUNCED/COMPLAINT，null 表示尚无回执；不得把 null 当作送达。CLAIMED 超时说明尚未开始发送，可恢复；SENDING 超时可能已产生副作用，必须 UNKNOWN，不自动重发。终止时删除加密正文；仍可用请求指纹处理原幂等键，密钥退役前需保留相应指纹核验 key。

`sma.mail.work.maximum-queued-per-profile` 默认 10000、`maximum-attempts` 默认 5、`lease-duration` 默认 2 分钟。实际发送尝试按账号的 UTC 日/分钟限额原子记账，领取/发送/完成各用短事务和 fencing；默认关闭自动调度，显式启用后才处理持久队列。

账号的 pendingDeliveries 预占与新任务在同一事务提交；取消/终止释放，安全重试保留。不要手工改任务状态、删配额行或直接更新计数；恢复走所属模块工作 API。工作线程停用账号、凭据已轮换或应用授权撤销时取消尚未发送任务，不自动借用新账号或其他租户账号。

## 7. SMTP 出站配置（C2b3a，已通过本地协议验收）

发送器使用 Boot 管理的 Jakarta Mail 2.1.5 / Angus Mail 2.0.5。每封邮件单独创建绑定该租户账号版本的 Session 和连接，不修改一套全局邮件账号。STARTTLS 必须升级成功，TLS 使用隐式 TLS；两者均校验系统信任链和精确主机名，不提供跳过校验开关。

部署人员先配置可访问的 SMTP 目的地，租户随后只能使用范围内的主机/端口；这是出站安全约束，不代替租户自己的账号、发件人和凭据：

```yaml
sma:
  mail:
    smtp:
      routes:
        - host: smtp.example.com
          ports: [465, 587]
      connect-timeout: 5s
      read-timeout: 10s
      write-timeout: 10s
      operation-timeout: 40s
      maximum-concurrent: 4
```

未登记主机/端口、解析到未批准内网地址、混合公网与私网结果均拒绝。每次发送先解析并检查全部地址，实际 TCP 连接固定到被核验地址，不在连接时重新采用未经校验的 DNS 结果。必要的企业内网 SMTP 可在该 route 上显式设置 `approved-private-addresses` 字面 IP 列表；禁止配置通配符，链路本地/元数据、多播及未指定地址即使列入也拒绝。私有 CA 通过受保护的 JVM truststore 部署，不通过关闭证书校验适配。

总发送截止时间必须小于工作租约（至少留 5 秒），单项超时限定在 100ms～60s。发送使用固定并发、有界接入，不把工作积压堆入无限线程队列；超时关闭底层 Socket。认证失败、证书失败及明确 5xx 失败不会当成功；明确 4xx 允许有限退避；提交 DATA 后断线/结果未知返回 UNKNOWN。固定 Message-ID 用于追踪，不能用它宣称接收方保证去重。

当前仅本机 TLS 协议验证，未使用实际供应商账号发送外部邮件。投递回执需 C2b3c 单独验收，SMTP 接受不等于收件箱送达。

### 7.1 租户调度（C2b3b，已通过本地验收）

部署启用自动处理前应完成：当前租户的 SMTP 账号、已发布模板、业务 `MailSendPolicy`，以及上述部署出站 routes。调度开关默认 false，也可通过 `SMA_MAIL_DISPATCH_ENABLED=true` 显式启用：

```yaml
sma:
  mail:
    dispatch:
      enabled: true
      concurrency: 2
      batch-per-tenant: 10
      tenant-page-size: 100
      poll-millis: 5000
```

开关和并发预算在启动时读取，修改后按部署流程重启/滚动发布。并发 1～16、单轮每租户任务 1～100、目录页大小 1～100。调度使用有界线程池和队列，不会为每个租户永久创建线程；同实例同租户不重复并发，跨实例通过数据库租约协调。网络发送在事务之外进行。

租户列表从 `TenantWorkQuery` 分页读取平台权威目录，重新启动会从头发现并恢复持久任务，不依赖 JVM 中的“见过哪些租户”集合。该公开契约仅供受信后台调度；带用户/机器认证的调用不能枚举租户，也不能把它包装成匿名 HTTP 接口。tenant 模块拥有目录表，mail 不跨模块查表。

租户停用、维护或不存在时不进入发送；已领取任务在开始发送前再次检查。进入外部发送的在途邮件不能保证撤回；结果无法确认时继续保留 UNKNOWN 并对账。未启用调度时也不会自动清理过期载荷，因此生产开启发送后须持续运行调度/维护，停用部署前应先排空或显式执行清理流程。

### 7.2 回执桥接与抑制（C2b3c，已通过本地桥接验收）

通用 SMTP 不提供统一的 webhook 验签协议。本基座提供 `POST /open/mail/receipts` 标准桥接端点；供应商网关必须先验证原始厂商签名、时间与订阅归属，再转换为本端点请求。当前没有原生 SES/SNS 或其他厂商 webhook 适配器，不能把厂商未经验证的 JSON 原样转交。

网关在相应租户的开放平台中登记独立机器应用，凭据只保存在受保护的服务端配置。通过 `/open/token` 获得 Bearer 后，每次回执仍须按开放平台协议带 `X-App-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature`。即使该机器应用关闭了普通接口的 signatureRequired，邮件回执仍强制验签。时间偏差最多 300 秒，nonce 由数据库去重；HTTP 重试使用新 nonce，但沿用同一供应商 eventId。

```json
{
  "application": "shop",
  "profileId": "1234567890123456789",
  "provider": "ses-gateway",
  "eventId": "vendor-event-001",
  "messageId": "sma-12345678-1234-1234-1234-123456789012@mail.invalid",
  "type": "BOUNCED",
  "occurredAt": "2026-11-11T00:00:00.000Z"
}
```

`profileId` 是该租户的邮件账号 ID。`messageId` 必须由网关将供应商消息关联到原邮件的 Message-ID（去掉邮件头尖括号）；没有可信关联时不得猜测。type 仅支持 DELIVERED、BOUNCED（永久硬退信）、COMPLAINT；暂时延迟/软退信不能映射为永久抑制。事件时间最多允许提前 5 分钟、回溯 30 天，历史补录须独立审查，不能修改 occurredAt 绕过窗口。

二开网关适配模块依赖 `mail`、`common :: security`，实现 `MailReceiptPolicy.canAccept(ActorRef gateway, MailReceiptApi.Command receipt)`。它必须把实际认证主体 `gateway.subject()`（`app:<开放平台 appId>`）与受保护配置中的 tenant/profile/application/provider 授权绑定，不能仅判断请求中的 application 名称。默认没有放行策略；后台人类、应用用户和普通后台任务均不能直接提交回执。纯 Java 适配也调用 `MailReceiptApi`，不访问 mail 内部实现或表。

下面是服务端 Node.js 的签名片段；bearer/appId/appSecret 来自受保护凭据，payload 必须已通过供应商验签和归属核对：

```js
import { createHash, createHmac, randomUUID } from 'node:crypto';
const path = '/open/mail/receipts';
const body = JSON.stringify(payload);
const timestamp = String(Math.floor(Date.now() / 1000));
const nonce = randomUUID();
const bodyHash = createHash('sha256').update(body, 'utf8').digest('hex');
const canonical = ['POST', path, '', timestamp, nonce, bodyHash].join('\n');
const signature = createHmac('sha256', appSecret).update(canonical, 'utf8').digest('hex');
const response = await fetch(new URL(path, platformOrigin), {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${bearer}`,
    'X-App-Id': appId, 'X-Timestamp': timestamp, 'X-Nonce': nonce, 'X-Signature': signature },
  body,
});
const result = await response.json();
if (!response.ok || result.code !== 0) throw new Error('Mail receipt rejected');
```

响应 data 包含 deliveryId、receiptState、duplicate。事件去重范围是 tenant/profile/provider/eventId；相同 ID 的投递对象、事件类型或毫秒精度事件时间发生变化时拒绝。乱序按 COMPLAINT > BOUNCED > DELIVERED 合并，旧 DELIVERED 不会清除退信。`MailDeliveryView.state` 保留传输结果，`receiptState` 保留供应商证据：例如 UNKNOWN + DELIVERED 表示 SMTP 本地结果不确定但后续已有送达证据，不能再自动重发。管理任务列表的 state=DELIVERED/BOUNCED/COMPLAINT 按回执筛选，其余按传输状态筛选。

Java 保留原 8 参数 MailDeliveryView 构造器和既有访问器；新构造器增加可空 receiptState。JSON 响应新增该字段，使用严格未知字段拒绝策略的调用方须先更新 DTO，不能假定所有旧解码器自动兼容。业务不要依赖 record 的组件数量、toString 或反射布局作为协议。

硬退信/投诉在同事务写入抑制记录，范围为 tenant/application/profile；入队及开始发送前均检查。收件人索引使用账号独立随机密钥的 HMAC，按大小写不敏感形式匹配；不同租户/账号不会共享可关联摘要。索引密钥加密绑定租户、账号和独立用途，密钥缺失时不能降级绕过。抑制不自动过期，也不提供普通业务“忽略抑制再发”的开关；新增账号不等于可以绕过供应商或平台封禁策略。

升级 V6 只追加字段和表；旧的待发送载荷会在开始发送前补索引。升级前已经终止并擦除了载荷、且没有 recipientKey 的历史邮件无法重建索引，回执明确拒绝，不能猜测或声明已成功抑制。部署前应排空/登记这类历史任务的供应商侧抑制记录。加密环轮换须保留解密账号索引密钥的旧 key，不能重新生成 recipientKeyEnvelope，否则既有抑制将失配；重封装只能保留原始随机密钥。

启用抑制前须关闭旧版本所有发送调度并等待在途操作结束，再完成全节点升级后启用新调度。旧 worker 不理解抑制门禁，不能与新 worker 混合发送。回退程序前同样先停发；保留 V6 表和证据，不回删迁移或在旧发送器上继续投递。这是安全行为升级的维护窗口，不宣称零停机共存。

## 8. 身份域与 Google 客户端配置（C3a，已通过本地验收）

本章先说明配置管理，再说明登录、应用会话与账号操作。Google调用入口见8.4，邮箱注册/重置/密码登录见8.13，本人再认证与关联管理见8.14。配置保存不触发外部授权或发送，真实外部联调按进度单列。

先创建租户内的身份域 realm，再为 Web/Android/iOS 分别创建 client。同一用户体系的自有客户端可显式选同一 realm，默认应为不同业务创建不同 realm。realm code、client code 均在当前租户内唯一，不同租户可同名；client 的 realmId、code、application 和 platform 创建后不可改变，避免已有身份被搬到另一应用。

| 用途 | HTTP | 权限 |
| --- | --- | --- |
| 身份域列表、详情 | GET /api/identity/realms、GET /api/identity/realms/{id} | identity:realm:view |
| 创建、更新、停用身份域 | POST /api/identity/realms、PUT /api/identity/realms/{id}、POST /api/identity/realms/{id}/disable | identity:realm:manage |
| 客户端列表、详情 | GET /api/identity/clients、GET /api/identity/clients/{id} | identity:client:view |
| 创建、更新、停用客户端 | POST /api/identity/clients、PUT /api/identity/clients/{id}、POST /api/identity/clients/{id}/disable | identity:client:manage |

列表支持标准 page/size；客户端列表可用 realmId 筛选。租户由已认证后台身份确定，不接受请求 body 指定 tenantId。两层服务权限和数据库租户过滤同时生效；管理 HTTP 沿用 ApiResponse，业务冲突可能是 HTTP200＋非零 code，不能只检查 HTTP 状态。Long ID/版本按字符串输出。

创建身份域示例：

```json
{"code":"shop","name":"商城用户","enabled":true,"registrationEnabled":true,"passwordEnabled":true}
```

registrationEnabled控制新身份自助创建，passwordEnabled控制邮箱密码认证；它们不影响后台管理员IAM/TOTP。创建Web客户端时，realmId使用上一步返回的ID：

```json
{
  "realmId": "1234567890123456789",
  "code": "shop-web",
  "name": "商城 H5",
  "application": "shop",
  "platform": "WEB",
  "enabled": true,
  "googleEnabled": true,
  "googleClientId": "123456-example.apps.googleusercontent.com",
  "googleSecret": "REPLACE_FROM_SECRET_STORE",
  "callbackUris": ["https://app.example.com/oauth/callback"],
  "allowedOrigins": ["https://app.example.com"],
  "accessTokenSeconds": 900,
  "refreshTokenSeconds": 2592000
}
```

googleSecret 是示意占位，实际值通过受保护后台配置提交。Google secret 不得写入 VITE_*、H5 代码、App 包或业务日志。服务端先配置第 3 节的加密 key-file；这里保存各租户自己的 Google 应用 ID 与加密 secret，不使用全局 Google 账号。响应只含 secretConfigured，不返回秘密或密文。

Web 启用 Google 时必须提供对应应用 secret；Android/iOS 为 public client，禁止保存 secret，allowedOrigins 必须为空。原生客户端 callbackUris 使用已登记的 HTTPS Universal/App Link 或 reverse-DNS 私有 URI（如 `com.example.shop:/oauth/callback`），不能复用 Web 的服务端秘密。仅使用邮箱登录的客户端可以设置 googleEnabled=false、googleClientId=null，并不提供 googleSecret。

首版配置限制为精确 ASCII 地址：HTTPS 使用标准 443（地址不显式写端口）、小写 DNS 主机，不接受 HTTP/localhost/IP、通配符、userinfo、query、fragment、编码路径或 `..` 路径。Origin 只含 scheme+host，不带末尾斜杠/路径。一个客户端最多 16 个回调和 16 个 Origin；这是一份精确许可清单，不能用前缀判断请求地址。真实 Google 控制台的登记地址须一致。私有 URI 仅用于原生客户端，必须使用至少三级 reverse-DNS scheme。

更新 body 为 `{"expectedVersion": "1", "configuration": {完整创建字段}}`，停用为 `{"expectedVersion":"1"}`。秘密留空或省略保留原值；更换 Google client ID 时不能把旧 secret 隐式配给新 ID，必须一起提供新 secret。清除 Google 配置需 googleEnabled=false、googleClientId=null。后台页面不回填秘密占位值，否则可能误触发凭据轮换。

配置每次更新追加不可变 revision；configurationVersion 表示配置快照，credentialVersion 仅在替换/清除秘密时推进。管理 version 防旧页面覆盖，realm/client 的 securityVersion 每次更新或停用推进，供后续登录事务和会话做撤销检查；停用不依赖秘密解密。重新启用要使用最新管理版本，不能通过旧页面恢复旧安全代次。

loginHandle 是随机公开入口标识，不含租户/身份信息；它不是 access token，也不赋予租户权限。正式 HTTP 入口见 8.4，二开不要通过扫描表或直接构造 ActorContext 代替身份认证 API。

### 8.1 Google 协议与部署边界（C3b，已通过本地协议验收）

基座使用 Spring Security OAuth2 JOSE 7.1.2 管理的 Nimbus JOSE JWT 10.9.1 校验 Google ID token，不自写 RSA/JWS 验签。只接受 RS256、Google 官方两个 issuer（规范化为 `https://accounts.google.com`）、当前配置 googleClientId 的单一精确 audience。默认 azp 若存在也必须等于 googleClientId；原生 SDK 的混合场景可另配唯一的 googleAuthorizedParty（Android/iOS Google 客户端 ID），此时 azp 必须存在且精确匹配，不能配置租户级允许列表。nonce 必须匹配该次登录事务，允许时钟偏差最多 60 秒，令牌签发/有效窗口最多 2 小时；不接受 JWT header 自带的 jku、x5u 或内嵌公钥。

公钥只从 `https://www.googleapis.com/oauth2/v3/certs` 获取，授权码只向 `https://oauth2.googleapis.com/token` 兑换；不提供租户自定义 endpoint、自动发现或关闭 TLS 校验的配置。生产采用 JDK HTTPS 信任链和主机校验，不跟随重定向、不保留 Cookie。部署必须允许这两个官方主机出站，并保持可靠的系统时间；不能通过更换任意地址或跳过校验解决连通性问题。

Google 网络操作不占用数据库事务；最大并发 4，无无限等待队列，总截止 12 秒、连接超时 5 秒、响应体上限 64 KiB，ID token 上限 16 KiB。超过预算或服务不可用返回 identity.error.provider-unavailable，供应商明确拒绝或 token 验证失败返回 identity.error.invalid-google-proof；不向客户端泄露供应商原始正文、授权码或秘密。

公钥属于公开的提供方材料，可在节点内共享；用户 claims、客户端凭据和认证决定不共享缓存。公钥最多 16 个、缓存遵从供应商 max-age/Age 且最长 6 小时，未知 kid 刷新间隔至少 30 秒，同节点只允许一个在途刷新，失败不使用过期公钥。缓存登记为受保护的 identity.google-jwks 区域，管理员不能通过清理/暂停缓存改变认证安全状态。

授权请求固定为 openid/email/profile 和 PKCE S256，不请求 offline access，不保留 Google access/refresh token。兑换使用原配置快照、原 redirect_uri 和 code_verifier；Google 登录身份以 issuer+sub 识别，email_verified 只是资料，不能据此自动合并同邮箱账号。

本节协议实现为 internal，二开不能直接依赖或把“验签成功”当成完成一次性登录。C3c 已本地验收的 HTTP 契约见 8.4；本地 HTTP 协议与合成签名测试不代表真实 Google 网络连通、控制台配置或 Android/iOS SDK 已完成联调。

### 8.2 应用会话持久层（C3c1，已通过本地数据库验收）

本阶段新增最小主体、Google 外部身份映射、会话族与刷新历史。外部身份唯一键是 tenant/realm/issuer/sub；即使 Google 邮箱相同，也不合并不同 sub。业务会员资料仍由业务模块拥有。公开事件 `ApplicationSubjectCreated` 只携带 tenantId、realmId、clientId、application、subjectId 和 method，不携带令牌、秘密或邮箱；正式业务订阅应使用事务提交后的 Modulith 事件机制并按 subjectId 幂等。

访问令牌、刷新令牌和 WEB CSRF 值各使用 256 bit 随机值，数据库只保存摘要。刷新会原子消费旧令牌，生成一个后继，并更换访问令牌和 WEB CSRF 值；旧访问令牌立即失效。刷新族的绝对截止时间不因轮换延长。客户端必须对刷新实行单飞；同一个刷新令牌并发使用会触发重用检测并撤销整族，包括刚生成的后继，不能在失败后继续循环重放旧令牌。

每次认证/刷新都校验租户、身份域、客户端和主体的当前安全代次。停用再启用不能恢复旧会话；旧配置快照不能用于新签发。WEB/NATIVE 模式取客户端配置，不能用同一个令牌改成另一种模式绕过浏览器保护。会话上限配置为 `sma.identity.sessions.maximum-per-subject`，默认 10、范围 1～100；配额按主体锁内的最新数据计算。

这些是 identity 内部协调器能力；二开不要直接调用 IdentitySessionStore 或自行构造已验证身份对象签发会话。已本地验收的可调用入口及调用顺序见 8.4。

### 8.3 登录事务部署说明（C3c2，已通过本地数据库与协议验收）

Google 登录事务将 CODE 与 ID_TOKEN 两种流程分别绑定。state、发起方 proof 只保存摘要，nonce 在有效期内加密保存；客户端、回调、Origin、PKCE S256 和配置安全代次在开始时固定。浏览器/设备必须保留自己的 PKCE verifier，完成登录时不能切换客户端、回调、流程或 Origin。内部 Start/Completion 包含受信入口元数据，不能把它们直接作为 HTTP 请求 DTO 绑定来源地址或 Origin；正式 HTTP 门面在 C3c3 交付。

状态为 PENDING→VERIFYING→COMPLETED/FAILED。进入 VERIFYING 后，服务端最多进行一次提供方核验，不会在进程崩溃、网络不确定或重复完成请求后自动重兑授权码。会话签发与 COMPLETED 在同一事务提交；事务失败不会留下可用账号或会话。正式 access/refresh token 不写入登录事务，完成响应丢失时需重新发起登录，不通过同一 state 取回或重发令牌。

部署预算位于 `sma.identity.login`，默认值如下；它们约束运行资源，不代替每个租户自己的 Google 客户端配置：

| 配置 | 默认 | 含义 |
| --- | --- | --- |
| lifetime | 5m | 登录事务有效期，允许 30s～10m |
| retention | 1d | 终态元数据保留期，最长 7d；终态立即擦除 nonce |
| maximum-outstanding | 1000 | 每租户未终止登录事务上限 |
| maximum-retained | 10000 | 每租户全部保留事务上限 |
| maximum-rate-keys | 2000 | 每租户来源/客户端速率记录上限 |
| tenant-per-minute / tenant-per-day | 200 / 5000 | 租户成功创建事务的分钟/UTC 日预算 |
| client-per-minute / source-per-minute | 20 / 10 | 客户端及来源 IP 的分钟预算 |

来源地址使用受信入口取得的 IP 字面值，不进行 DNS 查询，不相信请求 JSON 或任意转发头；数据库速率索引使用租户隔离 HMAC，不记录原始 IP。各层预算与事务预占一起提交，拒绝请求不留下半条事务或错误预占。

`sma.identity.login-maintenance.enabled` 默认 true，`tenant-page-size` 默认 10（1～50），`poll-millis` 默认 10000（1000～300000）。维护从平台目录分页发现租户，包含停用租户，逐批终止过期 PENDING/VERIFYING、擦除 nonce、释放预占并清除超期元数据/速率记录；不会访问 Google，也不会为未使用身份登录的租户创建预算记录。开始新事务时也会做有限清理。生产应保持维护运行，并按租户规模监控扫描延迟；测试 profile 显式关闭自动维护以固定时间测试恢复路径。

### 8.4 H5/App HTTP 契约（C3c3，已通过本地完整宿主验收）

本节入口已通过真实 MySQL 与 MockMvc HTTP 验收，真实 Google/设备 SDK/浏览器策略仍待联调。所有路径位于独立 `/app/**` 安全链，必须通过 HTTPS 和已登记的租户 Host 访问；开发环境也需要受信 HTTPS 域名或代理。每次请求带查询参数 `appClient=<loginHandle>`，包括 CORS OPTIONS。loginHandle 可以放 URL，access/refresh token、验证码和 PKCE verifier 不可以。Host 决定租户，loginHandle 只能选择该租户的启用客户端，不能通过请求 JSON/header 指定另一个 tenantId。

| 方法与路径 | body/用途 | 成功 data |
| --- | --- | --- |
| GET `/app/identity/bootstrap` | 无；取得公开配置及 H5 CSRF | name、application、platform、googleEnabled、googleClientId、registrationEnabled、csrfToken |
| POST `/app/identity/google/transactions` | callbackUri、flow（CODE/ID_TOKEN）、pkceChallenge | state、nonce、authorizationUri（CODE）、googleClientId、platform、expiresAt；仅原生含 proof |
| POST `/app/identity/google/completions` | state、verifier、callbackUri、flow、credential；仅原生还传 proof | subjectId、realmId、clientId、application、mode、accessExpiresAt、refreshExpiresAt；H5 含 csrfToken，原生含 accessToken/refreshToken |
| POST `/app/identity/sessions/refresh` | H5 `{}`；原生 `{"refreshToken":"..."}` | 新会话响应，字段同上 |
| GET `/app/identity/sessions/current` | 当前应用认证 | 本人主体、域、客户端、application、mode、到期时刻 |
| DELETE `/app/identity/sessions/current` | 当前应用认证；发送 JSON `{}` | 成功回执；撤销当前会话族 |

这些 API 遵循 ApiResponse 包装。业务拒绝通常为 HTTP200＋非零 code；安全过滤器使用 HTTP400/401/403/413/415/503。调用方同时检查 HTTP 状态和 code，不把 HTTP200 当成登录成功。所有长 ID 使用字符串，不转 JavaScript Number。请求体须为 application/json；identity 写入口默认实际字节上限 64 KiB（包含 chunked/未知 Content-Length），不能以声明小长度绕过。

**H5**：客户端 platform=WEB，精确登记 H5 Origin。fetch 必须设置 credentials=include，所有写请求附 `X-App-CSRF`，其值来自 bootstrap 或最新完成/刷新响应的 csrfToken。令牌仅在 Secure/HttpOnly、Path=/、无 Domain 的 `__Host-sma-access-<handle>` 和 `__Host-sma-refresh-<handle>` Cookie 中；JS 不保存/读取正式令牌，也不发送 Authorization。登录发起 proof 同样只在 HttpOnly Cookie 中，完成请求 JSON 传 proof 会被拒绝。CSRF 不接受 query 参数。

Cookie SameSite 由客户端 cookieSameSite 配置：默认 LAX，跨站 H5 按需设 NONE；两者始终 Secure。省略更新该字段保留旧值。第三方 Cookie 被浏览器阻止时，NONE 也不能保证可用，应通过同站 API 域名/网关部署；本地 MockMvc 不证明具体浏览器的第三方 Cookie 策略。只有允许清单内的精确 Origin 得到带凭据 CORS 响应，预检须包含同一 appClient。允许请求头为 content-type、x-app-csrf、authorization、accept-language、x-time-zone、idempotency-key；实际认证仍限制 WEB 不接受 Bearer。无 Origin 的安全同源请求仅在 API 自身 Origin 也位于该客户端清单时接受。

H5 CODE 调用顺序：生成 32 随机字节的 base64url verifier（43 字符）→ SHA-256 后 base64url 为 pkceChallenge → bootstrap → 创建事务 → 在当前页跳转 authorizationUri → 回调页面核对返回 state 与本页面保存的 state → POST completions（credential 为 code）→ 更新内存中的 csrfToken。将 verifier/state 仅短期存于当前标签页 sessionStorage，完成或失败后删除；不要把完整回调 URL 写入日志。登录回调 URI 必须与创建事务完全相同，完成请求从登记的 H5 Origin 发出，不能直接把 Google 的页面跳转当成服务端完成 API。

H5 ID_TOKEN 使用相同创建/完成步骤，调用 Google Identity SDK 时携带返回的 nonce 与当前 googleClientId；credential 改为 SDK 返回的 ID token，flow 始终 ID_TOKEN。nonce 必须真正进入签名的 ID token，不能在完成 body 中伪补一个 nonce。

**Android/iOS**：platform=ANDROID/IOS、allowedOrigins 为空、无服务端 secret。创建事务返回 proof，由原生客户端短期保留；完成时提交 proof/verifier 和 code 或 ID token。正式令牌仅存在设备安全存储中，业务请求附 `Authorization: Bearer <accessToken>`，不发 Origin 或本客户端认证 Cookie。刷新接口不带 Authorization，body 提交 refreshToken。Google SDK 若使用 Web/server client ID 作为 aud，则 googleClientId 配该 ID、googleAuthorizedParty 配精确原生客户端 ID；配置更改会使旧事务和会话失效。省略 authorizedParty 更新保留旧值，空字符串明确恢复默认 aud 规则；更换 audience 且不传该字段时清除旧 override。

两种客户端都必须串行刷新（单飞）。网络丢失后不要自动重放同一 refresh token 或 Google completion；刷新重用会撤销整个会话族，Google VERIFYING 状态不会再次兑换。无法确认响应时重新登录。成功刷新立即替换旧令牌/CSRF，不保留旧值重试。H5 每个浏览器/客户端同时保留一个登录 proof Cookie，新发起登录会覆盖旧 proof；同客户端多标签页应协调登录和刷新，不能把多个在途登录互相覆盖后继续重试。不同客户端的 Cookie 名称独立。

业务入口放自己的 `/app/<module>/...` 控制器，声明 `@PermitApplicationUser("shop")`，并通过 `ActorContext.requireApplicationUser("shop")` 取得 subjectId/tenantId/realmId/clientId，继续做对象归属校验。业务不能使用平台专用 `@PermitApplicationSelf`，不能引用 identity internal 或自行构造 AppUserPrincipal 作为 HTTP 认证。后台账号和 OpenAPI 机器令牌不能用于应用用户接口。

资源配置 `sma.identity.http.maximum-concurrent` 默认 64（1～256），`maximum-json-bytes` 默认 65536（4096～1048576），属于节点启动预算。并发满立即返回 503，不排无限队列；预算不替代租户/来源登录速率限制。入口/令牌响应均 no-store/no-referrer，业务自定义日志也应删除凭据与回调 query。

### 8.5 邮箱身份基础配置（C3d1，已通过本地数据库/HTTP 验收）

本节为邮箱挑战准备配置，当前不代表验证码、注册或密码重置端点已交付。每个租户客户端、每种语言独立绑定邮件模板，不使用全局邮箱账号。先按邮件章节创建当前租户 SMTP profile（applications 包含 `identity`），再发布以下三种模板；模板 code 可自定义，application/scene 固定：

| 用途 | application | scene | 模板变量 |
| --- | --- | --- | --- |
| 注册 | identity | registration | code：TEXT，maxLength=6；minutes：INTEGER，maxLength≥2 |
| 密码重置 | identity | password-reset | 同上 |
| 绑定邮箱 | identity | email-binding | 同上 |

正文示例：`您的验证码为 {{code}}，有效期 {{minutes}} 分钟。` 两个变量设为 required=true；不得把 code 定义成 INTEGER，否则 `000001` 这样的有效验证码会丢失前导零。新增绑定会用非秘密示例值验证模板能否渲染；草稿编辑不影响已发布版本。绑定操作只校验可用性，不发邮件、不锁住模板未来状态，实际入队和发送仍再次检查授权/启停/凭据版本。

读取：`GET /api/identity/clients/{id}/email-configurations`，需要 identity:client:view。保存：`PUT /api/identity/clients/{id}/email-configurations/{language}`，language 首版为 zh-CN/en-US；需要 identity:client:manage，启用配置还需 mail:template:view，并要求 realm/client 启用且 realm.passwordEnabled=true。不存在记录时 expectedVersion=0；修改时传读取的 version，旧版本会被拒绝。

```json
{
  "expectedVersion": "0",
  "configuration": {
    "enabled": true,
    "registrationTemplate": "app-registration",
    "resetTemplate": "app-password-reset",
    "bindingTemplate": "app-email-binding"
  }
}
```

不同租户允许使用相同模板 code，但只能解析本租户的模板/账号。未发布、用途不匹配、语言未配置、账号停用或凭据版本不匹配时拒绝启用绑定。停用现有配置传最新 version 和 enabled=false，不依赖模板可用性及邮件查看权限；不会借停用自动解密任何秘密。

邮箱身份规范化规则固定：ASCII dot-atom 本地部分，保留大小写、点号与 +tag；域名按 IDNA 转 ASCII 后小写。本地部分最长64、规范化全地址最长254字符；拒绝空白/换行、连续点号、引号形式、IP 字面域、单标签域和 SMTPUTF8 本地部分。当前 SMTP 契约要求 ASCII 字母顶级域，国际化非顶级域如 `bücher.de` 可转 punycode，国际化顶级域暂拒绝。不要在业务侧额外去点、去 +tag 或统一小写本地部分，否则会改变账号身份。

每个 realm 拥有独立随机 HMAC 索引密钥，该密钥再使用租户绑定信封加密。邮箱不会以六位码式普通摘要作为可枚举索引；不同租户/realm 的相同地址得到不同索引。部署加密环更换 active key 时保留旧解密键，可继续解析原索引密钥，因此邮箱身份键不变；不能删除/重新生成 identity_email_realm_key 来“修复”密钥故障。此表为安全状态，不是可清理缓存。

密码新建/变更使用 `common :: security` 的 PasswordStrengthPolicy.evaluate(identifier,candidate)，返回 ACCEPTED/INVALID_FORMAT/WEAK，不执行认证或写库。宿主当前实现复用原 IAM 登录密码策略：8～12位 ASCII 字母数字，至少包含两类并排除弱密码/用户名关联等。此契约只校验待写入的新密码，不能用于验证历史密码，更不改变后台独立安全密码/TOTP 规则。业务调用不得记录 candidate，身份流程应将判定映射为本模块双语错误并原子消费验证授权。

### 8.6 邮箱挑战状态与部署预算（C3d2，已通过本地数据库验收）

本节说明验证码状态机，二开实际调用8.13/8.14的HTTP接口；不能直接引用IdentityEmailChallengeStore或自行构造可信任务/投递编号。PREPARED需在创建事务中绑定实际MailSendApi入队结果，成为PENDING后才可验证。六位随机验证码按挑战引用、用途和部署密钥做HMAC，proof/grant使用独立256 bit随机值且仅存摘要；目标邮箱短期加密，终态清除目标密文、短码验证值和grant摘要。

正确验证码只产生短期 grant，并不发登录会话。grant 绑定原租户/realm/client/用途/目标/主体代次与 Origin，不能切换注册/重置/绑定用途。验证尝试独立提交；业务回滚不返还错误次数。grant 消费与实际凭据变更同库同事务，业务失败时一起回滚；禁止在消费回调中访问网络、独立提交或跨库写入。首次消费成功后不能重放，配置/主体代次变化和过期均拒绝。

启动预算位于 `sma.identity.email`，不属于租户 SMTP 账号配置：

| 配置 | 默认 | 含义 |
| --- | --- | --- |
| lifetime / grant-lifetime | 5m / 2m | 挑战/授权有效期；授权不超过原挑战到期时刻 |
| retention | 1d | 终态元数据保留，最长7d；秘密在终态立即清除 |
| maximum-outstanding / maximum-retained | 1000 / 10000 | 每租户未终止/全部保留挑战数量 |
| maximum-rate-keys | 5000 | 每租户来源/客户端/邮箱速率记录容量 |
| tenant-per-minute / tenant-per-day | 100 / 2000 | 每租户成功创建挑战额度 |
| client-per-minute / source-per-minute | 20 / 5 | 客户端及受信来源 IP 的创建额度 |
| target-per-day | 10 | 每 realm/规范化邮箱的每日创建额度；另有固定60秒重发冷却，跨用途合计 |
| guesses-per-minute / guesses-per-day | 500 / 20000 | 每租户验证尝试额度，含不存在挑战 |
| source-guesses-per-minute | 30 | 每来源验证尝试额度 |
| maximum-failures | 5 | 每挑战正确 proof 下最多输错次数，达到后永久失效 |

日预算按 UTC 自然日计算；来源仅使用入口提供的 IP 字面值，不查询 DNS、不信任请求 body 的来源字段。来源索引不存原始 IP。预算和挑战都不是普通缓存，不能通过清缓存重新获得猜测次数。`sma.identity.email-maintenance.enabled` 默认 true，tenant-page-size 默认10、poll-millis 默认10000；维护包含停用租户，每轮有界清理，不访问邮件网络，也不给未使用该能力的租户创建预算。测试 profile 关闭自动维护，显式推进时间验证恢复。

### 8.7 验证码与邮件任务联动（C3d3，已通过本地完整宿主验收）

身份协调器在同一数据库事务创建 challenge、调用 MailSendApi.enqueue 并回写 deliveryId。任一步失败全部回滚，返回结果不包含验证码。identity 保留发送场景只接受可信协调器提供的真实挑战，逐项核验收件人、验证码 HMAC、模板、用途、语言、到期时刻和任务引用；业务不能直接借 `application=identity` 发送任意内容。

邮件 API 新增可选 `MailSendApi.Guard(owner, reference)`，原8参数 Command 构造器继续可用，旧普通通知默认没有 guard。需要随业务状态撤销的通知，可在第9参数传入服务端构造的 Guard，并由所属业务提供 MailDeliveryGate：owner 必须全局唯一（如 shop.notice），reference 仅使用已授权业务对象的非秘密标识。发信时仍必须通过 MailSendPolicy；guard 不替代收件人/对象授权。严禁把邮箱、验证码、token 或恢复链接放进 reference。

Gate 在邮件新的短工作事务内执行，只做本模块的普通只读查询，不能 FOR UPDATE/SHARE、写状态、再开独立事务、回调 mail 或访问网络。根据持久对象判断该 deliveryId/用途/目标引用是否仍有效；不用进程缓存放行。未注册或重复 owner 不允许静默回退：新命令缺对应实现不能入队，已有任务丢失检查实现会取消；重复 owner 在启动时拒绝。

工作线程在发送准备和调用 SMTP 前复核有效性。挑战已验证/消费/过期、客户端/域/主体代次或邮箱配置变化时取消任务并擦除待发正文。检查之后才在事务外调用 SMTP；若检查遇到数据库故障则不调用发送器，已进入 SENDING 的任务按既有 UNKNOWN 恢复规则处理，不盲目重发。外部 SMTP 已放行后不能承诺撤回邮件，验证码最终消费仍独立检查期限和授权。

升级需要先迁移 mail V7，再确保旧发送 worker 全部退出，才启用产生 guarded 任务的新能力。旧 worker 不认识 guard，不能与新的验证码任务混跑；Java 构造器兼容只证明调用方可链接，不代表旧 worker 可以处理新安全语义。回退时先停投递、隔离 guarded 任务并完成对账，不能清除 guard 字段后让旧版本继续发送。以上步骤是部署说明，本次未操作运行服务或执行发布。

### 8.8 邮箱账号与凭据变更（C3d4a，已通过本地数据库验收）

本节描述账号事务语义；密码登录和显式绑定的HTTP调用见8.13/8.14。身份账号保存在identity自己的表中，不写后台IAM用户表。邮箱凭据的唯一性是tenant/realm/规范化邮箱索引，每个主体最多一个邮箱凭据；地址加密，密码只存宿主PasswordEncoder生成的哈希。

注册先完成 REGISTRATION 验证并取得一次性 grant，再在一个事务里检查唯一性、校验新密码、创建主体/邮箱凭据、签发应用会话并消费 grant。Google 用户即使声明同一邮箱，也不会被隐式合并。已有邮箱凭据不能通过再次注册替换密码；证明邮箱所有权之后，重复注册会提示使用已有登录方式或恢复流程。主体创建事件 ApplicationSubjectCreated 的 method 可能是 GOOGLE 或 EMAIL，消费者不能假定只有一种来源，业务会员资料仍归业务模块。

密码恢复的发起响应保持相同挑战结构，不返回账号存在状态。验证码证明邮箱可控，不证明该地址存在可恢复的密码账号；不存在账号、停用账号或 Google-only 账号不会借恢复流程创建密码。重置必须持有 PASSWORD_RESET grant，并匹配当前已验证邮箱凭据、原主体及安全代次；改密、增加凭据版本、撤销旧访问/刷新会话族和消费授权在同一事务提交。外层业务失败会一起回滚，授权可在原期限内继续正确操作，不能先单独消费再改密。

密码强度沿用8.5的宿主规则。哈希使用既有 BCrypt strength=12，`sma.identity.password.maximum-concurrent` 默认4（范围1～16），无排队；满额返回503并保留未消费授权。弱密码在计算哈希前拒绝，密码、哈希和完整邮箱不进入普通日志或响应。二开不要自行降低强度或绕过该写入流程。

会话记录authenticationMethod/authenticatedAt：Google会话为GOOGLE，邮箱注册完成为EMAIL_VERIFIED，密码登录为PASSWORD。刷新只轮换令牌，不更新真实认证时刻；不能把一次刷新当成高风险操作的重新验证。旧行迁移为UNKNOWN/null，继续遵守原会话安全代次和期限，不因此获得新的认证证明。本人敏感绑定使用8.10～8.14的一次性再认证流程，单凭新邮箱验证码不允许绑定原账号。

### 8.9 邮箱密码登录（C3d4b，已通过本地数据库验收）

密码登录HTTP路径见8.13；二开不能直接构造PreparedPasswordLogin或调用内部签发方法。流程分为独立提交尝试预算、事务外密码校验、短事务复核并签发会话。密码本身不会进入预算记录或数据库查询；准备快照最多有效30秒，期间密码/邮箱/主体/客户端/租户状态变化时，旧验证结果不能继续签发。

验证预算与8.6共用租户/来源硬限制，另有 `sma.identity.password.per-target-per-minute`，默认5、范围1～10，按 realm/规范化邮箱计数，换 IP 或客户端不能绕过同一 realm 的账号预算。预算先提交，外层业务失败不返还次数；调用方不能把整个密码协调器包在一个业务事务中。正确密码也受预算和节点CPU并发限制，不应自动循环重试429/503。

未知、停用及无密码账号使用启动时预热的dummy hash执行正常成本校验，与错误密码返回相同的验证失败。历史密码验证不执行8～12位的新密码写入策略；BCrypt输入上限为72个UTF-8字节，超限直接拒绝。密码正确后仍需重新检查凭据哈希/版本及主体安全代次，不把“旧哈希匹配”单独当作授权。

凭据隔离按 tenant/realm，同一 realm 的不同已登记客户端可登录同一主体，但每次签发都绑定实际 client/application 和WEB/NATIVE模式；不能用A客户端令牌访问B客户端。不同 realm 或租户的同名邮箱互不关联。是否能发注册/恢复邮件还取决于当前客户端自己的邮件配置，不借用另一客户端的默认账号。

### 8.10 显式再认证授权（C3d4c1，已通过本地数据库验收）

敏感认证方式变更使用独立的一次性再认证grant，不使用“刚刷新过会话”作为证明。grant绑定原tenant/realm/client/subject/session、主体及配置安全代次、具体动作和目标；邮箱目标存本身份域的规范化HMAC索引，不能换成另一个邮箱。授权只存摘要，默认2分钟有效，消费后擦除摘要；失败的同资源业务修改与消费一起回滚。

密码再认证已通过本地验收：使用原主体自己的已有密码凭据，独立提交尝试额度，事务外完成有界BCrypt校验，再次检查会话/凭据版本后才签发grant。它与普通密码登录共享同一realm邮箱目标的猜测预算，不能切换入口绕过次数。仅掌握新邮箱验证码或有效应用会话，不能代替原账号再认证。actor必须由应用安全链提供，绝不能从HTTP body构造AppUserPrincipal传入内部服务。

动作分别为EMAIL_BINDING、GOOGLE_BINDING、PASSWORD_REMOVAL、GOOGLE_REMOVAL；邮箱动作绑定目标索引，移除Google方式绑定当前主体拥有的具体关联ID。不同动作不互换；注销后或换一个会话，旧grant也不能使用。Google再认证和关联流程见8.11；邮箱绑定及移除HTTP见8.14，均已通过本地专项。

部署配置 `sma.identity.reauthentication`：lifetime 默认2m（30s～5m）、retention 默认10m（不短于有效期且不超过1h）、maximum-per-realm 默认1000、maximum-per-subject 默认10。容量包含保留期内的终态元数据，防止连续创建/消费绕过存储额度。清理复用 email-maintenance，每类清理独立容错；停用租户的过期数据也会处理，普通缓存清理不能碰这些授权状态。

### 8.11 Google再认证与关联（C3d4c2，已通过本地数据库验收）

Google操作使用独立于普通LOGIN的REAUTH/LINK_GOOGLE事务，固定绑定原账号会话、动作、目标与配置快照。授权码流程请求Google账号选择；原生SDK的ID token必须携带本次事务新nonce，不能拿缓存旧token完成再认证。验证issuer/sub归属，不因email相同接受另一个Google主体。该证明生成8.10的一次性授权，不能把普通登录完成结果用作关联授权。账号选择不等于强制Google重新输入密码，真实终端交互仍需联调。

新增Google关联先消费GOOGLE_BINDING授权，再创建新Google事务；二者在同一数据库事务中，创建失败保留授权。Google交换/验签在事务外，完成前复查原会话及配置；新增成功撤销该主体所有旧会话，调用方必须重新登录。网络/提交不确定失败不重新兑换，操作按失败或到期终止；不能自动重放提供方code。二开使用8.14的HTTP接口，不直接调用internal服务。

他人现有或历史关联均不能转移或合并。撤销后的issuer/sub继续保留原主体归属，匿名登录返回已撤销关联错误；同主体只能通过显式绑定恢复。`sma.identity.bindings.maximum-google-links`默认5（1～100），`maximum-remembered-google-links`默认100（不小于active上限且≤1000），后者包含历史记录，不能靠反复解绑绕过容量。

升级边界：V10为旧事务添加默认LOGIN，保留原登录协议。启用操作流程前必须下线所有不认识intent的旧实例；新操作行不得被旧登录实例处理。若需回退旧程序，先停止新操作入口、排空全部PENDING/VERIFYING操作事务并保留审计及历史归属；旧程序不认识撤销历史保护，已发生关联撤销时禁止直接回退到旧身份实现。真实Google账号选择/授权页面尚未联调。

### 8.12 邮箱绑定与移除登录方式（C3d4c3，已通过本地数据库验收）

邮箱绑定先取得EMAIL_BINDING再认证授权，目标必须是同一规范化邮箱。消费原账号授权、创建新邮箱挑战、邮件入队在同一数据库事务中完成；失败可在原授权有效期内修正后重试。获得目标邮箱验证码后，还必须以发起操作的原账号、原客户端、原会话完成绑定；另一会话即使属于同一账号，也不能接走这次操作。注销、配置/安全代次变化后应重新开始整个流程。

绑定邮箱默认只验证联系地址并保留已有密码。无密码账号可显式提供新密码，经现有密码策略校验后添加邮箱密码登录；已有密码时拒绝此参数，必须走密码修改或重置流程。只有联系邮箱、没有密码哈希的账号，不能通过普通密码重置隐式添加密码。邮箱与Google身份始终不按相同email自动合并；他人已占用的邮箱拒绝改绑。

移除密码会保留已验证邮箱及其密文联系信息，但该邮箱不再算作登录方式。移除Google关联保留其历史归属，后续匿名登录不能重新注册或转移给别的主体。每次移除前检查当前客户端可用的剩余方式：Google须配置启用且存在活动关联，密码须身份域启用、邮箱已验证且密码存在。不能删除最后一种方式；并发操作仍须满足这一约束。

凭据变化原子提升主体安全代次并撤销旧会话，返回需要重新登录。二开使用8.14的绑定/移除HTTP接口；不要依赖internal类。V11添加可空绑定快照，旧普通注册/重置挑战不受该字段影响；旧的无绑定快照挑战不能用于完成新的邮箱绑定流程。真实邮件送达及终端交互仍需后续联调。

### 8.13 邮箱注册、重置和密码登录HTTP（C3d5a，已通过本地HTTP验收）

以下入口均需HTTPS和本租户可信Host，并带查询参数 `appClient=<本客户端loginHandle>`；不接受请求体指定租户、主体、Origin或来源IP。注册/重置验证码固定用途集只包含 `REGISTRATION`、`PASSWORD_RESET`，不允许传 `EMAIL_BINDING`。邮箱配置、发送模板和相关账号仍需事先按本租户发布。

| POST路径（前缀 `/app/identity`） | JSON请求 | 成功data |
| --- | --- | --- |
| `/email/challenges` | `purpose,email,language`，language为zh-CN/en-US | `handle,expiresAt`；仅Native另返回proof |
| `/email/verifications` | `purpose,handle,code`；仅Native还需proof | 一次性`grant,expiresAt` |
| `/registrations` | `grant,password`；仅Native还需proof | 与Google登录相同的应用Session |
| `/password/resets` | `grant,password`；仅Native还需proof | 无载荷；原主体旧会话已撤销，需重新登录 |
| `/password/sessions` | `email,password` | 与Google登录相同的应用Session |

H5先GET `/app/identity/bootstrap`取得csrfToken，所有POST都带 `X-App-CSRF` 与 `credentials: 'include'`，Origin须在本客户端精确白名单内。后端将注册proof与重置proof分别放入每客户端独立的Secure、HttpOnly、Path=/ Cookie；H5不得在JSON里传proof。账号创建/密码登录后用返回的csrfToken替换前端内存值，访问/刷新令牌仅在HttpOnly Cookie内。注册完成清除注册proof；重置和退出清除该客户端认证及挑战Cookie。

```javascript
// H5示例：在已登记Origin运行。应用层处理错误与用户输入，不记录邮箱、密码、code、grant。
const root = new URL('/app/identity/', apiOrigin);
const url = path => {
  const target = new URL(path, root);
  target.searchParams.set('appClient', loginHandle);
  return target;
};
const bootstrap = await fetch(url('bootstrap'), { credentials: 'include' });
const initial = await bootstrap.json();
if (!bootstrap.ok || initial.code !== 0) throw new Error('应用身份入口不可用');
let csrf = initial.data.csrfToken;
async function postIdentity(path, input) {
  const response = await fetch(url(path), {
    method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'X-App-CSRF': csrf },
    body: JSON.stringify(input),
  });
  const envelope = await response.json();
  if (!response.ok || envelope.code !== 0) throw new Error(envelope.message || '操作失败');
  if (envelope.data?.csrfToken) csrf = envelope.data.csrfToken;
  return envelope.data;
}
// 第一步：用户请求注册验证码。后端入队成功不等于真实邮件已送达。
const pending = await postIdentity('email/challenges', {
  purpose: 'REGISTRATION', email: userEmail, language: 'zh-CN',
});
// 第二步：用户输入邮件验证码；handle/code/grant均只保留本次流程内存。
const verified = await postIdentity('email/verifications', {
  purpose: 'REGISTRATION', handle: pending.handle, code: enteredCode,
});
const session = await postIdentity('registrations', {
  grant: verified.grant, password: newPassword,
});
// session.subjectId等Long标识保持字符串，不转Number。
```

Native使用相同路径，但不发送Origin、Cookie或X-App-CSRF。保存挑战响应proof到本次流程内存，验证码验证与最终注册/重置都带同一个proof；会话accessToken/refreshToken存系统安全凭据存储，后续受保护接口使用Bearer。匿名挑战/登录端点不要带已有Bearer，它不是这些端点的授权依据。Native携带本客户端的认证/挑战Cookie会被拒绝。

密码重置将示例purpose改为PASSWORD_RESET，最后调用password/resets；成功后重新bootstrap/登录，不自动重发原grant。密码登录直接调用password/sessions。错误码401表示证明或密码不满足条件，409表示冲突或验证码已使用，429/503不要自动循环重试；状态与限流以服务器为准。验证码错误次数独立提交，不能靠HTTP失败回滚；默认最多5次。验证grant默认最多2分钟，且绝不晚于原挑战到期，前端使用响应expiresAt，不自行延长。

同一客户端、同一用途再次请求挑战会覆盖该浏览器的对应proof Cookie，不支持并行多标签页继续同用途的旧流程；注册和重置分别隔离。跨站H5需要本客户端SameSite=None且终端允许相关Cookie；第三方Cookie被浏览器阻止时不能降级为在H5 JSON中返回会话令牌，应使用受支持的同站部署方式。真实浏览器/OAuth/邮件送达仍按外部联调清单验证。

### 8.14 本人认证方式管理HTTP（C3d5b，已通过本地HTTP验收）

以下路径前缀为 `/app/identity/account`，全部要求当前客户端的应用用户会话。H5使用现有Cookie和写请求X-App-CSRF；Native使用Bearer。后台管理员和机器令牌不适用。请求仍需appClient查询参数和可信Host，但不接受主体ID或租户ID选择要修改的账号。

| 方法与路径 | 请求JSON | 成功data |
| --- | --- | --- |
| GET `/methods` | 无 | `verifiedEmail,passwordConfigured,passwordEnabled,googleEnabled,googleLinks[{id,createdAt}]`；ID保持字符串 |
| POST `/reauthentication/password` | `action,target,password` | 原账号一次性`grant,expiresAt,action` |
| POST `/reauthentication/google/transactions` | `action,target,google:{callbackUri,flow,pkceChallenge}` | Google事务，结构同登录开始 |
| POST `/reauthentication/google/completions` | 同Google登录完成：`state,verifier,callbackUri,flow,credential`，仅Native加proof | 原账号一次性`grant,expiresAt,action` |
| POST `/google-links/transactions` | `grant,google:{callbackUri,flow,pkceChallenge}` | 新Google身份验证事务 |
| POST `/google-links/completions` | 同Google登录完成结构 | `linkId,changed,signInRequired` |
| POST `/email-bindings/challenges` | `grant,email,language` | `handle,expiresAt`，仅Native加proof |
| POST `/email-bindings/verifications` | `handle,code`，仅Native加proof | 新邮箱一次性`grant,expiresAt` |
| POST `/email-bindings/completions` | 新邮箱`grant`，仅Native加proof；无密码账号可显式传newPassword | `changed,signInRequired` |
| DELETE `/password` | 原账号`grant` | `changed,signInRequired` |
| DELETE `/google-links/{id}` | 原账号`grant` | `changed,signInRequired` |

原账号授权的action/target对应：EMAIL_BINDING→目标邮箱，GOOGLE_BINDING→固定字符串google，PASSWORD_REMOVAL→固定字符串password，GOOGLE_REMOVAL→methods返回的本人关联ID。原账号grant与邮箱验证grant是两种不同证明，不能互换。关联ID不是Google的sub，methods不会返回provider sub、密码哈希或密文。

```javascript
// 接续8.13的H5 postIdentity。此时已登录，csrf使用当前会话返回值。
const approval = await postIdentity('account/reauthentication/password', {
  action: 'EMAIL_BINDING', target: nextEmail, password: currentPassword,
});
const pendingBinding = await postIdentity('account/email-bindings/challenges', {
  grant: approval.grant, email: nextEmail, language: 'zh-CN',
});
const mailboxApproval = await postIdentity('account/email-bindings/verifications', {
  handle: pendingBinding.handle, code: enteredCode,
});
const changed = await postIdentity('account/email-bindings/completions', {
  grant: mailboxApproval.grant,
  // 仅当前没有密码且明确要添加密码时，才在这里传newPassword。
});
// changed.signInRequired为true时旧会话已撤销，重新bootstrap并登录。
```

Google再认证必须选择原账号已有的Google身份；新关联事务则验证希望新增的Google身份。两次事务各自生成新的PKCE verifier/challenge，严格使用对应state/nonce/proof与入口；不能拿普通登录事务去完成再认证或绑定。授权码流程使用返回authorizationUri，原生SDK使用本次nonce，不复用缓存ID token。新增关联前的原账号grant一经成功创建关联事务即消费；取消或失败后需取得新的原账号授权，不能重复兑换旧code。

H5的Google再认证、Google新增关联、邮箱绑定分别使用独立HttpOnly proof Cookie；JSON不传proof。Native把对应开始响应proof带入该流程的完成请求，邮箱绑定需在验证码验证和最终完成都带同一proof。邮箱绑定从验证阶段就校验原账号/会话，另一会话即使属于本人也不能消费原挑战。所有grant/proof/密码/code只作短期流程数据，不写URL、日志或持久浏览器存储。

DELETE请求同样需要JSON Content-Type和H5 CSRF。先取得对应动作与目标的原账号授权再删除，最后一种可登录方式返回409；重复关联已有Google账号返回changed=false、signInRequired=false。真实变更返回signInRequired=true时，H5认证/挑战Cookie被清除，Native自行丢弃旧令牌并重新登录。不要把接口成功后的旧Bearer保留为“仍然登录”。本节端点已通过本地HTTP验收，真实供应商与浏览器终端策略仍单列联调。

### 8.15 Java主体与联系邮箱查询（C3e，已通过本地数据库与样例编译验收）

静态业务模块声明依赖identity，只通过根包接口调用。主体基本信息不包含邮箱或凭据；联系邮箱单独授权。下列接口的租户均来自可信ActorContext，不接受请求字段选择租户。

| 接口 | 作用与约束 |
| --- | --- |
| `SubjectQuery.current(application)` | 当前APP_USER本人基本主体；application必须与会话audience一致，并再次验证会话。无需业务策略，不提供联系邮箱。后台、机器和job不能冒充“本人”。 |
| `SubjectQuery.find(SubjectReadRequest)` | 按业务用途读取基本主体，必须有该application的SubjectAccessPolicy批准BASIC。返回tenantId/realmId/subjectId/application/enabled，不授予业务权限。 |
| `SubjectContactQuery.verifiedEmail(SubjectReadRequest)` | 必须单独获批VERIFIED_EMAIL；返回主体引用、经基座邮箱挑战验证的地址和验证时刻。Google的email claim不构成此联系信息。 |
| `SubjectAccessPolicy` | 所属业务注册一个application对应策略；缺失默认拒绝，重复启动失败。回调收到原始可信actor，验证用途和真实业务对象归属，不只比较application字符串。 |

SubjectReadRequest只包含application、realmId、subjectId、purpose，purpose为最多64字符的业务用途编码。APP_USER请求还必须同audience和realm，策略不能突破租户与身份域边界。管理员、机器和可信job均需明确策略授权，没有超管默认放行。策略需为有界只读检查，不能递归调用身份查询、访问网络或取得业务写锁；query会短事务读取权威状态，不使用缓存或忽略配置停用。

已授权的find在主体/身份域/可用application客户端不匹配时返回Optional.empty；主体存在但停用时基本信息enabled=false，联系邮箱返回empty。租户不可执行业务、会话失效、策略拒绝会抛出异常，不能将这些异常吞成“没有邮箱”。缺密钥或密文损坏同样失败，不回退到Google地址或别的租户配置。

```java
// 位于自己的业务模块，注入两个公开接口。此请求已经过应用安全链。
SubjectQuery.Subject self = subjects.current("capabilitydemo");
var request = new SubjectReadRequest("capabilitydemo", self.realmId(), self.subjectId(), "account-notice");
Optional<SubjectContactQuery.VerifiedEmail> contact = contacts.verifiedEmail(request);
// 联系邮箱仍须经业务收件偏好/对象授权，再通过MailSendApi独立发送授权入队。
```

完整的可编译适配示例见[独立教学目录](../examples/integration-services/README.md)。它包含SubjectAccessPolicy与MailSendPolicy组合、同库事务通知入队和明确待实现的业务授权接口；不加入main的生产构建，没有业务表、路由或默认允许策略。只在业务分支采用自己的包与application，保留通知ID/截止时间/收件快照/邮件delivery关联，重试先查原任务，不能换邮箱或换幂等键盲发。

`ApplicationSubjectCreated`仍为公开创建事件，只有原子创建主体成功才发布；业务监听者恢复事件的可信租户，并按subjectId幂等创建自己的资料。共享身份不意味着共享业务权限，会员、角色、订阅和同意记录由业务自己拥有。查询结果和联系地址都不是授权凭据，不缓存身份状态以替代每次入口认证。

方案最初的EmailChallengeApi只是草案名。最终验证码、登录和凭据变更使用8.13/8.14的HTTP协议，以保留来源、Cookie/CSRF、一次性证明和会话控制；不向业务开放内部会话签发或允许传入“已验证”的授权快照。Java通知复用上述联系查询及第6章MailSendApi，避免复制认证状态机。

## 9. 租户存储配置（C4a，已通过本地数据库与HTTP验收）

存储配置按当前后台租户维护，允许不同租户使用同名code和不同云账号；不会回退到全局账号。当前阶段仅交付配置模型/API，不代表云SDK传输、安全扫描或新上传接口已经可用。旧文件接口仍按原本地配置工作，其安全与兼容接入在后续文件阶段完成。

| 方法与路径 | 权限 | 作用 |
| --- | --- | --- |
| GET `/api/file/storage-profiles/options` | `file:storage:view` | 支持的配置类型及部署允许的本地目录别名；不返回物理路径 |
| GET `/api/file/storage-profiles` | `file:storage:view` | 当前租户分页，可用keyword筛选名称 |
| GET `/api/file/storage-profiles/{id}` | `file:storage:view` | 配置明细，秘密仅显示是否配置和accessKeyId末4位hint |
| POST `/api/file/storage-profiles` | `file:storage:manage` | 新建配置，须Idempotency-Key |
| PUT `/api/file/storage-profiles/{id}` | `file:storage:manage` | `{expectedVersion,configuration}`，须Idempotency-Key |
| POST `/api/file/storage-profiles/{id}/stop` | `file:storage:manage` | `{expectedVersion,scope}`；scope为UPLOADS/READS/ALL，须Idempotency-Key |

```json
{
  "code": "private-assets",
  "name": "租户私有附件",
  "provider": "S3",
  "region": "us-east-1",
  "bucket": "replace-with-your-private-bucket",
  "prefix": "assets",
  "accessKeyId": "<通过受保护表单提供>",
  "secretAccessKey": "<通过受保护表单提供>",
  "applications": ["capabilitydemo"],
  "uploadsEnabled": true,
  "readsEnabled": true
}
```

示例密钥是占位说明，不可原样提交。provider只能LOCAL、OSS、S3、R2，没有七牛。code/provider创建后不可改；更换厂商创建新profile。applications是允许接入的应用集合，后续场景授权仍需独立检查。prefix是逻辑对象前缀，不接受绝对路径、反斜杠、空路径段、`.`/`..`、末尾点、Windows设备名或ADS。

| 类型 | 基础配置与当前限制 |
| --- | --- |
| LOCAL | localRoot选择options返回的部署别名，region/endpoint/bucket及云凭据留空。默认别名default映射`data/managed-files`，部署可用`sma.file.storage.local-roots.<alias>`覆盖；租户不能填写实际服务器路径。真正目录安全、租户分区和上传在后续阶段验收。 |
| S3 | region必填，如us-east-1；endpoint省略则生成`https://s3.<region>.amazonaws.com`，中国区使用amazonaws.com.cn。若显式提供只允许同一标准endpoint，不支持任意S3兼容服务。使用普通通用Bucket，特殊目录/表/别名Bucket不在本配置范围。 |
| OSS | region如cn-hangzhou；endpoint省略生成`https://oss-cn-hangzhou.aliyuncs.com`。不使用S3协议配置，不接受任意自定义/内网endpoint。 |
| R2 | region省略或auto；endpoint必须是`https://<32位accountId>.r2.cloudflarestorage.com`。不接受CDN域名或默认假定支持S3的sessionToken；此版本R2拒绝sessionToken。 |

云accessKeyId、secretAccessKey、可选sessionToken作为一个秘密整体租户加密，密文绑定profile/configurationVersion/credentialVersion。API不返回这些原值；提供二者才能替换，空或省略二者保留已有秘密，但每次配置更新仍生成不可变revision并重新绑定密文。仅提供一个凭据字段拒绝，sessionToken不能单独修改。临时sessionToken必须有未来credentialExpiresAt；只改到期时间不能续期，必须提供一整组新凭据。所有时刻使用UTC、存毫秒精度。

version用于管理并发，configurationVersion绑定不可变连接快照，credentialVersion在替换秘密时推进。旧revision不覆盖、不删除；后续任务/文件固定自身版本，改路由不能移动已存文件。stop不解密秘密，缺密钥或凭据已过期时仍能关闭上传/读取，且提升version防旧页面重新启用；开启需正常更新最新版本。此阶段只保存开关，实际传输/读取执行边界在后续适配阶段验证。

管理保存无外部网络副作用；域名语法白名单不等于已完成SSRF防护，运行适配器还必须做出站DNS/IP/重定向/超时检查。没有“保存成功即云连接成功”的保证，连接诊断和真实云操作按后续阶段验收。不要把厂商控制台URL或公开Bucket URL填为endpoint。

## 10. 上传场景登记与租户发布（C4b，已通过本地数据库与HTTP验收）

业务通过FileSceneCatalog声明自己的application、sceneCode、nameKey、maximumBytes、formats和modes，代码和nameKey以application加点开头。真实实现类必须位于该基座/业务模块包，且模块有ApplicationModule声明；不能在file模块实现类中伪称shop。每application注册一个FileScenePolicy，缺策略默认拒绝、重复启动失败。这里只注册可用范围，租户仍需创建并发布自己的配置。

FileSceneAccess携带application/scene/action、可选业务objectType/objectId和fileId；这些是待验证的引用，不是授权。FileScenePolicy收到可信ActorRef，需要检查当前人、应用、业务对象与动作。APP_USER还必须精确匹配application audience。UPLOAD/READ/BIND/UNBIND/DELETE不能仅凭同租户或知道ID放行；BIND/UNBIND必须有业务对象和fileId，运行文件服务仍须检查文件归属、READY和引用关系。

```java
// 业务分支：com.sma.business.capabilitydemo.internal.service
@Component
final class BusinessScenes implements FileSceneCatalog {
    public Collection<Definition> scenes() {
        return List.of(new Definition("capabilitydemo", "capabilitydemo.avatar",
            "capabilitydemo.scene.avatar", 5L * 1024 * 1024,
            Set.of(FileFormat.PNG, FileFormat.JPEG), Set.of(UploadMode.SERVER)));
    }
}
// 另提供FileScenePolicy并核验真实会员/对象关系，不能只返回true。
```

基座登记file.image（上限5MiB、PNG/JPEG/GIF/WEBP、SERVER）及file.attachment（上限20MiB、受控图片/文档/文本、SERVER/MULTIPART）。二者仅面向后台人类身份；图片上传仍需登录，普通附件要求file:file:upload，应用用户/机器/job不能借用。旧附件和头像接口的迁移在后续阶段接入，本次登记不改变旧文件来源。

| 管理路径（前缀`/api/file/scenes`） | 权限 | 说明 |
| --- | --- | --- |
| GET `/catalog`、列表、`/{id}`、`/{id}/published` | file:scene:view | 登记范围、当前租户草稿和已发布快照；未发布时published不可用 |
| POST 列表、PUT `/{id}` | file:scene:manage | 新建/编辑草稿；PUT含expectedVersion和configuration；须Idempotency-Key |
| POST `/{id}/publish` | file:scene:publish | `{expectedVersion}`，须Idempotency-Key；重新核验配置和空间授权，生成不可变发布 |
| POST `/{id}/disable` | file:scene:manage | `{expectedVersion}`，须Idempotency-Key；停用新准入并提升版本 |

```json
{
  "code": "file.image",
  "name": "后台图片",
  "configuration": {
    "profileIds": ["替换为本租户存储ID"],
    "defaultProfileId": "替换为上述某个ID",
    "formats": ["PNG", "JPEG"],
    "modes": ["SERVER"],
    "maximumBytes": 5242880,
    "retentionDays": 365
  }
}
```

profileIds默认最多8个（部署sma.file.scenes.maximum-profiles可在1～16内调整），全部须属于当前租户、上传/读取启用且applications包含场景application。Local不支持DIRECT。类型和方式只能选Catalog子集，maximumBytes不超过Catalog与部署硬上限的较小者；默认部署硬上限20GiB只是准入限制，不代表已完成20GiB传输或扫描验收。retentionDays为保留策略字段（1～部署上限，默认上限3650），清理执行随任务生命周期阶段验收。

发布时保存每个profile的configurationVersion/credentialVersion/provider，更新草稿或存储配置不修改旧发布。需要采用新存储配置时重新发布场景；停止上传/读取或从profile撤销application会实时阻止新准入。偏好存储只能从已发布profileIds中选择，未传时选defaultProfileId；客户端不能修改Bucket、prefix或最终object key。

当前枚举中的格式与模式是策略表达，不保证运行处理器/适配器已可用。没有完整类型检查、隔离扫描和传输能力时，后续任务必须拒绝或保持隔离；没有关闭扫描、直接设READY或发布任意HTML/脚本的配置项。手册后续上传API会给出实际运行能力与验收上限。

## 11. 上传任务状态与配额（C4c1，内部状态层已通过本地数据库验收）

内部任务、Local字节传输与公开FileUploadApi/HTTP入口均已通过本地验证，调用见11.2。业务不能直接依赖UploadTaskStore。完整内容检测、隔离扫描和最终READY仍在后续子项；本节不作为已能发布安全文件的声明。

任务固定可信上传者（包括身份类型、租户、APP_USER的realm/client以及后台portal）、租户安全代次、已发布场景和空间配置/凭据版本。原始文件名只作展示，标准化后校验路径、控制符、Unicode隐藏格式符、设备名、尾点/空格及危险双后缀；实际存储路径使用服务器生成标识。声明FileFormat和全文件SHA-256只用于传输一致性，不能替代服务端内容检查与完整扫描。

初始化使用16～128字符的幂等键；同上传者同键同输入返回原任务，不同输入409。原始输入保存租户任务HMAC指纹，密钥轮换保留旧指纹核验能力；不会因场景路由变化重新创建同键任务。所有权、当前权限、空间授权与租户代次仍在每次访问复查，知道uploadId不授予操作权。

内部状态：INITIATED→UPLOADING→VERIFYING，取消为ABORTING，未完成到期为EXPIRED。VERIFYING仅表示请求冻结和检查，不可下载/绑定。每片持久RECEIVING/STORED，大小和摘要固定；同片不同内容拒绝。同片RECEIVING重试只返回待核对状态，不重新授予写入权；后续传输实现须核对原不可覆盖字节后确认。完成要求所有片已确认且总字节匹配，最终仍需全文件摘要与安全检查。

`sma.file.tasks`部署默认：lifetime=24h、retention=7d、maximum-active=100、maximum-retained=10000、per-minute=30、per-day=1000、part-bytes=16MiB、maximum-parts=10000、maximum-concurrent-parts=3、maximum-reserved-bytes=100GiB。作用域为租户任务预算，当前只接Local SERVER/MULTIPART状态；这些是准入天花板，不是测得吞吐或大文件支持规模。任务初始化独立提交，调用方业务回滚不撤销已接受的任务，业务应保存/查询同一幂等键并明确取消。

当前每任务预占3倍声明大小，为分片/冻结/待发布临时副本保留空间；活动任务数和字节预算不因取消或过期自动释放，须等待可信清理流程确认物理占用已消除。禁止手改表释放配额或将未知写结果自动重试。后续云直传若无法严格限制上传字节，需额外供应商/网关硬限额，不能用事后清理冒充防费用攻击。

### 11.1 本地传输与冻结（C4c2，已通过本地字节流与数据库验收）

本地分片路径只由部署根别名、可信tenant/profile、服务器uploadId、part number和claimId构成。逐级检查真实目录，拒绝符号链接/目录联接/非目录节点；最终文件用CREATE_NEW和NOFOLLOW_LINKS排他创建，不拿原始文件名拼路径。目录根和父目录必须由部署账号独占管理，上传者不能获得本机文件系统写权限；Java层检查不等于对恶意本机管理员的文件系统沙箱。Windows无符号链接创建权限的测试边界会单列记录。

`sma.file.local-transfer`默认maximum-concurrent=8、deadline=10m、minimum-free-bytes=256MiB。固定64KiB缓冲流式计数/摘要，不把完整文件读进内存。磁盘剩余空间检查是额外水位保护，不是操作系统物理预留；并发IO故障仍按失败/未知处理。节点线程池饱和在I/O前明确拒绝时，可把尚未执行claim转成RETRYABLE；超时或其他错误不能冒充此证明。

每片首先固定大小/摘要和唯一claim，写入持有文件锁；接收失败保留其不可覆盖路径。重试时：文件仍被写入→BUSY不重写；不存在且领取状态未知→ABSENT待核对；已完整且摘要一致→刷盘后确认原结果；已关闭但内容不完整→最多一次新claim路径重试。第一次失败的片文件保留待清理，已确认的其他分片不受影响。两次分片尝试加一次冻结副本最多占三倍声明字节；再次失败不无限创建新副本。

完整上传合并时逐片核对其不可变回执、顺序、大小和摘要，再核对全文件SHA-256；生成客户端无写权限的新冻结文件。后续迟到上传或原暂存片变化不能替换冻结内容。文件写成功但数据库确认丢失时，通过锁定读取原路径、重新计算摘要与刷盘确认，不重新写同一对象。冻结成功只进入SCANNING，尚不可绑定、下载或预览，恶意软件与容器检查在后续统一流水线执行。

HTTP和Java接入见11.2。进程在实际创建文件前中断导致ABSENT、冻结不完整或反复失败时，需要后续取消/清理或运维对账流程处理，不能通过超时清状态来恢复写权限。部署根别名变更属于存储迁移，不能直接改目录指向后假设旧任务/文件会自动迁移。

### 11.2 公开上传API与HTTP（C4c3，已通过本地联动验收）

静态业务模块声明依赖`file`，注入根包`FileUploadApi`。输入Command包含key/application/scene/objectType/objectId/preferredProfile/mode/name/format/size/checksum；没有tenantId、上传者ID、Bucket或服务器路径。上下文中的原actor及已发布场景决定身份、路由和权限，preferredProfile只能选发布允许的空间。注册业务场景与授权见第10章，完整可编译教学代码见[上传教学示例](../examples/integration-services/README.md)。

初始化独立提交，业务事务回滚不撤销它；`putPart`和`complete`必须从无外层数据库事务的编排层调用，前者负责关闭InputStream。Task包含uploadId、预分配fileId、state、version、name、format、mode、expectedSize、partBytes、partCount、receivedParts、receivedBytes、expiresAt。fileId不是读取授权；只有后续安全门禁及对象授权均通过才能使用文件。Java的Long与HTTP的ID/version应按原类型处理，前端保留字符串，不转换为JS Number。

后台前缀为`/api/file/upload-tasks`，应用前缀为`/app/file/upload-tasks`。旧`/api/file/uploads`仍是旧同步上传回执查询，不与本路径混用。

| 方法及相对路径 | 输入 | 返回 |
| --- | --- | --- |
| POST 根路径 | JSON Start及`Idempotency-Key` | 原键同输入复用的Task |
| GET `/{id}` | uploadId | 本人Task，仍复查当前场景授权 |
| GET `/{id}/parts?after=0&limit=100` | after为已看到的片号，limit 1～100 | Part列表：number/size/checksum/state |
| PUT `/{id}/parts/{number}` | `application/octet-stream`原始字节；`X-Upload-Size`、`X-Upload-SHA256` | 确认后的Task，或原写者尚未确认的状态 |
| POST `/{id}/complete` | JSON `{"version":"最新版本"}` | 冻结合并状态，目前最多SCANNING |
| DELETE `/{id}` | JSON `{"version":"最新版本"}` | ABORTING；不表示磁盘空间已释放 |

Start示例（把业务场景和对象替换为真实登记值）：

```json
{
  "application": "capabilitydemo",
  "scene": "capabilitydemo.document",
  "objectType": "document",
  "objectId": "业务真实文档标识",
  "mode": "SERVER",
  "name": "notes.txt",
  "format": "TEXT",
  "size": 12,
  "checksum": "替换为实际完整文件的64位小写SHA256"
}
```

调用顺序：先以流式算法计算完整文件SHA256并保留原始内容；初始化后保存同一key/uploadId；按服务端partBytes和partCount切片，片号从1开始，末片可短，每片独立计算SHA256；逐片上传，再查询状态确认所有片为STORED；使用最新version完成。SERVER/MULTIPART当前都使用该服务端二进制通道，Local不支持DIRECT。请求大小不可自行超过任务partBytes；不可把10GiB文件整体读进浏览器或Java内存计算摘要。浏览器大文件使用受控Worker中的增量SHA256实现，不能用一次性`crypto.subtle.digest(await file.arrayBuffer())`代替大文件方案。

WEB每次请求带`?appClient=<loginHandle>`、已批准Origin、`credentials: include`；写请求带当前`X-App-CSRF`。登录/刷新更新Cookie后读取新的CSRF值，不能缓存旧值。原生App使用同一appClient及自己的Bearer，不发送Origin或应用Cookie。后台凭据不能调用应用接口，APP_USER也不能调用后台接口。所有应用端方法按实际audience和任务owner.realm/client复查，同一会员换到另一客户端也不能读取旧客户端任务。

JSON最多64KiB；分片尺寸头、摘要头、Idempotency-Key不允许重复，拒绝Content-Encoding。实际读取字节同样有界，不相信Content-Length。文件入口节点并发16，本地传输另有8个默认I/O槽位；应用安全链仍有自身节点并发上限。超限可能返回429/503，应保留原键查询并退避，不能换键制造新任务。部署需同时配置网关/Servlet连接读取超时和带宽预算；本节不是网络吞吐验收。

业务失败通常HTTP200＋非零ApiResponse.code；认证、CSRF、类型和正文限制也会用实际HTTP401/403/400/413/415。网络断开后先查任务/片列表；若RECEIVING且原文件ABSENT/BUSY，继续显示处理中或待对账，不重新领写。若传输层明确在I/O前拒绝，RETRYABLE可用同片/同摘要重试；已关闭不完整片仅允许一次新路径恢复。版本冲突先读状态；不能用旧version覆盖别人推进的状态，也不能在SCANNING时显示“已可使用”。取消和失败清理、READY、业务绑定及下载仍待后续生命周期章节交付。

### 11.3 完整字节预检与后续安全门禁

C4d1的内部FileContentProbe已通过本地单元/架构验证；它以固定64KiB缓冲检查完整源字节的大小/SHA256，检查常见二进制签名，并对声明文本做严格增量UTF-8及控制字符校验。WebP核对RIFF长度，MP4限制初步容器品牌，PDF限制头版本；Office只识别ZIP候选。它尚未接最终发布，仍不能把SCANNING变为READY。

预检Candidate不是“已解码”或“无病毒”：只含PNG头的残缺文件仍可能通过初步签名，合法UTF-8并不证明JSON结构、CSV公式安全或没有恶意内容。后续必须在隔离worker内完成各格式结构检查，图片重编码，再由受控扫描器扫描完整冻结副本和必要衍生物。预检循环检查截止/中断并关闭输入，但不能强行中断任意阻塞InputStream，调用它的传输/worker必须另有限时与资源隔离。该类是内部实现，二开不能自行调用后把结果当放行凭证。

## 12. 后续随实现补充的调用章节

| 章节 | 需交付内容 | 当前状态 |
| --- | --- | --- |
| 租户配置与凭据 | 权限、配置管理、密钥部署/轮换、未配置错误、应用授权 | Google/邮件/四类存储配置均已本地验收；管理页面待实施 |
| Google / 邮箱身份 | H5/App 流程、注册/重置/绑定、会话与错误处理、示例 | 登录/账号操作HTTP及公开二开查询已本地验收；真实外部联调待办 |
| 事务邮件 | 模板发布、Java/API 调用、幂等、验证码/通知、状态与回执 | 模板/队列/SMTP/调度/回执桥接本地验收完成；真实厂商联调待办 |
| 文件服务 | 场景、上传/分片/续传、READY、业务绑定、授权下载/删除 | 未交付 |
| 存储适配 | 本地/OSS/S3/R2 配置与差异、最小权限和迁移 | 未交付 |
| 前端接入 | 通用上传组件、任务状态、租户切换、敏感信息处理 | 未交付 |
| 运维和验证 | 回退、清理、隔离扫描、监控、真实外部联调要求 | 未交付 |

用户已要求先实现并完成本地验证；未准备外部测试配置时，真实 Google 登录、真实投递和云区域联调仍需单列验收。
