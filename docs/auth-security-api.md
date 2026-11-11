# P07 认证安全接口

实现验收状态见[进度](auth-security-progress.md)，本文描述当前接口供前端接入，不能代替真实浏览器或发布验收。统一使用 `ApiResponse { code, error, message, data }`，仅 `code=0` 成功；业务拒绝可能返回 HTTP 200 加非零 code。沿用现有 API 客户端，不只判断 HTTP 状态。

## 登录和锁屏

`POST /api/auth/login` 保留 username、password、portal、captchaId、captcha、remember。登录密码输入仍支持已有长/特殊字符密码，不用新设密码规则校验此字段。返回两种状态：

| data.status | 字段与行为 |
| --- | --- |
| AUTHENTICATED | token、expiresAt、rememberAllowed；此时才保存令牌和加载 me |
| MFA_REQUIRED | challengeToken、expiresAt、rememberAllowed=false；token 为 null，不得保存为登录 Token |

`POST /api/auth/login-challenges/verify`：`{ challengeToken, code }`，code 为保留前导零的 6 位数字字符串。成功返回 AUTHENTICATED，rememberAllowed=false，前端忽略此前的长期保持登录选择。挑战只保留内存，失效/取消/刷新后重新开始，不能写入 URL 或浏览器持久存储。登录和第二步响应均有 `Cache-Control: no-store`。

`POST /api/auth/unlock` 保留 `{ password }`，只验证登录密码；需要原有效、已完成全部认证的锁屏会话。不接受登录挑战、不延长绝对期限、不更新 MFA 时间。过期/撤销后重新登录。

## 安全设置

以下端点均要求有效、未锁屏的人类会话。成功的凭据设置/变更/重置使现有会话失效，包括操作者本人，前端清空会话并回到登录页。查询和申请绑定响应不可缓存。

`GET /api/auth/security-settings` 返回：

```text
totpEnabled: boolean
totpPending: boolean
totpEnabledAt: UTC instant | null
securityPasswordSet: boolean
securityPasswordChangedAt: UTC instant | null
setupAvailable: boolean
```

不返回密钥、哈希或密钥 ID。setupAvailable=false 表示部署尚未提供可用的认证密钥环，不可用时不提供绕过操作。

| 动作 | 接口 | 请求体 |
| --- | --- | --- |
| 申请首次绑定/换绑 | POST /api/auth/totp-enrollments | password：登录密码；已绑定时还需 code：当前 TOTP |
| 确认绑定/换绑 | POST /api/auth/totp-enrollments/confirm | challengeToken、code：新验证器的 TOTP |
| 取消待绑定 | POST /api/auth/totp-enrollments/cancel | challengeToken |
| 解绑 | POST /api/auth/totp-credentials/disable | password：登录密码；code：当前 TOTP |

申请成功返回 `{ challengeToken, secret, uri, expiresAt, replacing }`，uri 为 otpauth URI，前端本地生成二维码，不调用外部二维码服务。只在本次待绑定流程显示 secret，取消/关闭后清除内存；再次发起使旧申请不可确认。换绑确认前旧验证器仍有效。

code 不需要时省略或传 null，不传空字符串。绑定确认已经使用的动态码不能紧接着用于登录；提示等待下一次更新，不扩大服务端时间窗口。重放拒绝也适用于解绑/换绑和安全密码管理的重新认证。

## 安全操作密码

新安全密码为 6～8 位 ASCII 字母数字，至少一字母、一数字，大小写敏感，不自动 trim。请求字段：

```text
password: 当前登录密码（验证旧值，不套新设8～12位规则）
code: 已绑定时的当前TOTP；不需要时省略/null
newPassword: 新安全操作密码
confirmation: 再次输入的新安全操作密码
oldPassword: 修改时需要的原安全操作密码
```

| 动作 | 接口 | 验证 |
| --- | --- | --- |
| 首次设置 | POST /api/auth/security-passwords | 登录密码；已绑定时加 TOTP；必须尚未设置 |
| 修改 | PUT /api/auth/security-passwords | 登录密码＋原安全操作密码；已绑定时加 TOTP |
| 忘记后重置 | POST /api/auth/security-passwords/reset | 登录密码＋已绑定 TOTP；未绑定时拒绝 |

无匿名验证接口，不开放普通管理员清除接口。安全操作密码与登录密码双向不同，服务端在持有统一凭据锁后判断；前端比较仅是提示。错误或超时不自动重试写请求，先查询状态；会话已失效时按新流程登录。

供静态业务模块接入的公开 Java 接口为 `SecurityPasswordVerification.verifyForCurrentSession(sessionId, candidate)`。sessionId 来自服务端认证请求属性 `sma.session-id`，身份来自 CurrentUser；调用前仍需检查业务权限和对象归属。需要与业务写入一致时，业务开启事务后调用验证，再执行敏感写入，不能将验证结果缓存为通用已验证状态。当前没有业务操作接入该能力。

## 登录新密码策略

创建用户、首次管理员初始化、本人修改和管理员重置，统一要求新值 8～12 位 ASCII 字母数字并拒绝弱词表、简单整串重复和账号关联值；还不能等于当前安全操作密码。历史登录密码验证保持兼容。

服务端错误键：`iam.error.password-format`（格式）、`iam.error.weak-password`（弱密码）、`iam.error.password-conflict`（两种密码冲突/账号策略）。安全操作密码格式使用 `auth.error.security-password-format`，未设置/错误使用 `auth.error.security-password-invalid`，OTP 使用 `auth.error.invalid-totp`，挑战失效使用 `auth.error.invalid-challenge`，限流使用 `auth.error.rate-limited`。

前端需区分完整成功、待二步验证、失效、限流及配置不可用；用户可见文案保持 zh-CN/en-US。不要展示服务端堆栈，也不要将密码、OTP、二维码或 Token 加入日志/埋点。
