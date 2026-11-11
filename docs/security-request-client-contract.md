# 请求治理客户端契约

适用于[已登记接口](security-endpoint-inventory.md)，交付状态查[安全专项进度](security-defense-progress.md)。新版前端统一交互正在按[前端计划](security-frontend-progress.md)实施；本契约不意味着页面已经全部接入。

## 原意图键与最小回执

LOCAL写操作在首次发送前生成一次随机UUID，使用请求头 `Idempotency-Key`。同一次意图的查询/安全重试保留原键；不同请求体、路径对象、版本或写入用途不能借用该键。键不是身份凭据，当前tenant/user/portal及权限检查始终有效。不通过伪造客户端版本或X-Request-Policy请求头绕过要求。

正常首次成功仍返回原ApiResponse业务DTO，并给出X-Operation-Id。同键成功重放返回HTTP409、error为common.request.completed、data为最小RequestReceipt，**不能转换成原业务DTO**。例如创建用户重放的data.operationId不是用户ID；应按授权刷新真实列表或详情后提示此前已完成。

通用回执结构为operationId、operation、state、createdAt、updatedAt，不保存密码、正文或业务响应。首次响应丢失也能使用原键查询：

公告收件人可用 `GET /api/message/notices/inbox/{id}` 重新获取当前有权访问的详情；该接口不改变readAt，不需管理权限，也不接受历史回执作为内容授权。`PUT /inbox/{id}/read`继续表示明确标记已读。前端收到已完成最小回执时使用该GET，不能为获取DTO重新制造一次写操作。

```http
POST /api/common/requests/lookup
Authorization: Bearer <当前会话>
Content-Type: application/json

{"key":"首次发送前保存的UUID"}
```

已知回执ID也可GET `/api/common/requests/{id}`。两个查询共享本人120/min预算，还受通用读/租户门禁；查询不执行原写操作。operationId仅作关联，业务内容仍走当前对象授权接口。

## 结果与恢复

下表的普通业务完成语义以LOCAL为准。NATIVE返回code=0可能仅表示受理或一次查询成功，必须继续检查其任务状态，不能当成外部效果已完成。

| 信号 | 含义 | 客户端处理 |
| --- | --- | --- |
| ApiResponse.code=0，正常业务DTO | 当前响应确认成功 | 刷新真实数据，完成当前意图；下一次独立操作才创建新键 |
| 409 common.request.completed | 原键已有成功提交 | 按最小回执处理，不执行依赖原DTO的后续步骤，不再创建或发送 |
| 409 common.request.conflict | 同键用于不同输入/用途/目标 | 保留原意图，先查原结果；不能自动换新键覆盖 |
| 202 common.request.processing | 原意图仍处理中 | 保留键，按Retry-After及有限退避查询，不重复执行 |
| 202 common.request.unknown | 服务端不能确定最终结果 | 保留键与未知状态，不自动释放、重跑或提示失败 |
| 409 common.request.failed，回执FAILED_FINAL | 原本地事务确认回滚，原键不会再执行 | 保留输入供修改；用户明确再次操作时才建立新意图 |
| 429，带Retry-After | 主体/接口/对象或租户预算超限 | 停止连发；旧意图先查回执。不得自动换键绕过冷却 |
| 503、5xx、网络超时/断连/取消 | 可能未受理，也可能已有旧意图或完成但响应丢失 | 保留原键，核对原结果；不能单凭这些信号宣布已回滚 |
| 查询NOT_FOUND | 当前查询未观察到该原键 | 不是旧请求永远不会到达的证明；仅在LOCAL契约下由用户明确选择原键/原输入重试，不自动新建意图 |
| 400/403或HTTP200非零业务code | 参数/权限/业务拒绝 | 区分新请求明确拒绝与旧未知意图；存在先前未知尝试时仍须核对原回执，保留表单 |

HTTP成功码不等于ApiResponse业务成功；202也不是业务DTO。Retry-After使用秒数；过期后只表示可以再查询/尝试获得预算，不代表旧操作已终止。隐藏页面降频、轮询有次数和总时长上限。身份/租户切换后的迟到响应不得更新新页面。

## 模块原生协议

| 场景 | 查询与重试边界 |
| --- | --- |
| 文件上传 | POST `/api/file/uploads/lookup`传原键，或GET任务ID；X-Upload-Id是文件任务ID。通用接受事务成功只表示任务受理。仅ACCEPTED且resumable=true可显式原键/原文件恢复；RUNNING/UNKNOWN不重传，已删除文件不恢复 |
| 品牌发布/回退 | 请求体operationId保持稳定，GET `/api/tenant/brands/operations/{operationId}`核对；保留expectedDraftVersion/expectedPublishedRevisionId，必须提交原生operationId，不能仅加通用请求头省略它；客户端可给登记键与原生号使用同一随机UUID，但仍按原生接口核对 |
| 域名DNS/TLS验证 | POST检查使用原operationId与expectedVersion，GET `/api/tenant/domains/{id}/checks/{operationId}`核对；网络读取位于独立短事务之间，不能套LOCAL事务或把RUNNING当成功。挑战/启停/主域名更新仍走版本与本地回执 |
| 缓存任务 | 保留原预览ID、预期版本、提交键及任务ID，查询原任务/确认报告；任务受理、逻辑效果、节点确认不是同一状态；PARTIAL包含UNKNOWN子项时保留未知，内部LOCAL入队回执不能充当原生任务编号 |
| 规则发布/批次 | 模板按GET `/api/rule/templates/{id}/publications/{operationKey}`、批次按GET `/api/rule/migration-batches/operations/{operationKey}`查询原操作者回执；候选报告、发布修订及逐项回执分别核对，RECOVERY_REQUIRED/404不代表原请求未执行，不能推导部分提交为整体回滚 |
| 插件及控制台 | 保留原work、bundle、业务键和代理协议。分片按upload/file/chunk区分；未知执行按原work对账，不切新版本或换键重发 |
| 邮件取消/回执 | 查询原delivery状态；取消与队列释放由邮件模块控制。SMTP结果未知不得由前端重发邮件 |
| 会话、登录、MFA、签名入口 | 使用原会话/挑战/nonce/刷新族语义；不缓存或重放密码、OTP、token响应，不用通用LOCAL事务包裹 |
| QUERY查询/预览 | 无写回执；允许有界防抖、取消和相同读取作用域的在途合并，必须隔离迟到响应 |

X-Request-Policy是服务器的有效分类提示，不提供权限，也不能代替模块的原生协议。控制台隧道及文件状态查询等NATIVE适配器按实际协议区分读写，不能由客户端声明safe字段决定。

## 升级与持久化

### 当前消费者核对（B6审查，不代表S1c已完成）

| 前端入口 | 必须保留/补齐的协议 |
| --- | --- |
| IAM `ResourceEditor` / `ResourcePage`、角色授权、用户密码；Config参数/字典/个人偏好 | 同对象编辑/删除/授权共享提交范围；确认之前取得锁。LOCAL完成回执只触发真实数据重读，不能当作新建资源返回值 |
| Message公告、私信、待办及已读/批量已读 | 已读也属于写操作；批量目标与模式在确认前固定快照；个人动作与管理动作按服务端范围分别治理 |
| File `files.ts` / `UploadButton` 及头像/品牌上传消费者 | 上传采用文件任务回执和原键；不能在finally中将未知上传恢复成可直接重传。成功文件仍经当前ACL读取 |
| Workspace `shelf.ts` / `notes.ts` | 保留服务端版本和便签requestKey；通用HTTP键与便签领域键各有用途。正文仅在内存，不从存储重建写入 |
| Tenant `directory.ts` / `brands.ts` | 域名检查、品牌发布/回退使用原生操作号，版本和租户入口不丢失；草稿及目录普通修改按LOCAL核对 |
| Monitor `cache.ts`、规则 `api.ts` / `useRuleTask` | 任务受理不等于效果完成；保留预览、报告、发布和任务状态，不以通用成功回执覆盖原协议 |
| Extension `client.ts` / `RuntimePanel` | 独立Axios控制台隧道同样需要处理慢响应/未知状态；已有Prepared命令键、challenge/grant和分片范围不能替换 |
| Auth store、`useAccountSecurity`、`SecurityControls`、会话页 | 保留MFA挑战/OTP/会话及锁屏清密语义，不持久化认证材料、不自动重放；本专项不更改密码规则 |

动态资源页面共享处理器，不把源码调用行数当成接口覆盖数；后端编译清单和前端实际事件入口需要分别验收。搜索、预览及POST回执查询也不能仅凭HTTP方法推断为业务写入。

需要原意图键的接口缺键明确拒绝。两套独立客户端目前均在缺头时补随机UUID，保留显式原键；这只解决协议兼容，不能保证一个用户意图的多次调用自动共用同一键。新版统一交互按C1～C4交付，旧版仍须遵循服务端拒绝与原生任务规则。

浏览器操作记录仅保存由服务端身份/入口确定的作用域、原键、操作/对象标识、回执/任务ID及状态。不得持久化token、密码、OTP、文件字节或业务正文；重载后不得从存储自动重建写请求。存储通知不构成原子锁，跨标签协调和后端唯一性须分别验证。

部署先提供HMAC密钥及新迁移，再发布支持操作键/恢复规则的客户端。页面CSP、代理限流、跨域暴露响应头和外部通知的真实部署验证独立进行；本地接口通过不能替代这些验证。
