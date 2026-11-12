# 请求治理客户端契约

适用于[已登记接口](security-endpoint-inventory.md)，交付状态查[安全专项进度](security-defense-progress.md)。新版前端统一交互已按[前端计划](security-frontend-progress.md)完成C1～C4本地验收；真实后端S1d与生产验收仍独立。

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
| 文件上传 | POST `/api/file/uploads/lookup`传原键，或GET任务ID；X-Upload-Id是文件任务ID。通用接受事务成功只表示任务受理。仅ACCEPTED且resumable=true可显式原键/原文件恢复；发送前必须再查状态，RUNNING/UNKNOWN不重传。新版仅保留当前页面内存File；刷新后只查回执，不从恢复记录重建multipart。已删除文件不恢复 |
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

### 认证请求的身份绑定（C3d1）

新版RequestOptions支持`expectedIdentity`，值为发起操作时捕获的当前token，匿名登录/OTP显式传null。不传则保留其他调用的原行为。Axios发送拦截器在真正发出请求前比较当前身份；不同则本地拒绝，既不把旧操作改用新token发送，也不加入任何自定义认证头/URL参数。此值仅在请求内存中存在，不能写入操作注册表或作为服务端授权证明。后端继续校验真实Bearer、租户和权限。

auth store同步限制单次登录/OTP在途；取消后新请求使用新代次，旧finally不能释放新请求门禁。`/auth/me`的过期成功和失败都丢弃，登录/MFA只有在自己的权限读取仍有效时才返回完成。MFA网络/202/5xx等未知结果清除当前一次性挑战，必须重新登录，不提供原OTP重放。登录页离开、入口失效及MFA页离开清理敏感输入和未完成请求的归属；正常登录→MFA→主页交接保留原协议。

`auth.logout()`返回true才表示本次仍属于当前本地会话并已清除；迟到或重复退出返回false，调用者不能据此把新身份导航到登录页。当前会话退出网络失败仍清本地凭据并抛错，不自动重发退出。页面异步确认/未保存等待之前的意图锁，以及账号安全、锁屏/本人改密的未知状态处理按C3d2继续验收；C3d1不代表认证所有页面或跨标签C4完成。

### 消费者协议（B6清单，经C3/C4本地验证）

账号安全页和本人改密现用`shared-credential-intents`（C4a3a）：异步准备之前同步占位并取得Web Locks，发送前将`sma.credential-intent.v2.<scope>`写入localStorage。标记仅含schema、站点/portal/用户/当前会话组合的SHA-256作用域、随机原键和UNKNOWN，每条最大512字符、最多256条；token、密码、OTP、挑战、密钥和正文不写入。旧sessionStorage v1标记只在匹配当前scope时先保存共享标记再删除旧记录，冲突/损坏拒发。它不是后端权限或原生幂等回执，不允许从标记重建请求。存储/摘要/Web Locks不可用时明确拒绝凭据写入。

直接成功、当前新请求明确拒绝或已验证的原LOCAL终态才移除标记；网络/202/408/409/5xx等不确定结果保留。settings GET、刷新、页面重建和解除锁屏不能宣布原写失败或解锁重传。新认证会话有新作用域，旧标记不自动执行且不自动删除；仅含有界元数据，不作为历史业务账本。局部QR渲染/路由失败不推翻服务器成功证明。共享storage事件仅用于重新读取标记，原键核对与提交必须取得同组Web Locks，不能用通知代替锁。当前支持使用新版门禁的同源标签；旧页面须重新加载，锁屏/登录等跨标签流程按C4a3b/c另验。

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

C3d2本人改密使用LOCAL原键核对，仅接受原操作iam.users.9721c28fc095e11b25656509的SUCCEEDED/FAILED_FINAL；其他凭据原生操作没有此回执，NOT_FOUND不得释放未知。确认前固定输入和身份，锁屏/关闭旧表单/身份变化使旧确认失效。密码仅内存且每轮清理。

锁屏/解锁使用C4a3b独立共享会话标记`sma.session-lock-intent.v2.<scope>`：schema=2、会话SHA-256作用域、随机key、action=LOCK/UNLOCK、state=UNKNOWN/LOCKED/UNLOCKED，每条≤512字符、最多256scope；无密码/token。Web Locks串行同scope的锁/解锁，发送前UNKNOWN持久化，直接成功才写对应终态；明确错误密码保持LOCKED，锁失败/未知不释放。旧v1未知匹配scope时保守迁移。没有可持久化标记或浏览器能力时不发锁请求并本地退出；与已记录同一锁意图相遇可保持其遮挡，不重复发送。

UNKNOWN/LOCKED同步遮挡与清密；刷新或普通/me不构成原操作完成证据，未知需要结束旧会话重新登录。新UNLOCKED通知只是触发当前状态核对：必须原身份、原确认key仍最新、无新的本地锁/退出意图，且授权/me明确返回locked=false及匹配用户/portal，另一标签才可解除遮挡。读回失败/字段缺失/迟到旧结果/历史启动标记均不能解除新的锁；缺少服务端历史回执时不得重新unlock消除未知。页头/锁屏退出原确认门禁保持；登录/MFA/退出跨标签仍归C4a3c。
C3d3a会话管理：POST /auth/sessions/{id}/revoke为NATIVE，仅本次直接响应判定终态；分页GET是当前在线状态，不是历史回执。未知目标跨刷新继续阻止再次提交，重新登录也不作为旧撤销完成证明，不提供自动重试/清标记入口。不同目标独立；确认前固定ID与身份，expectedIdentity保护实际发送，当前会话由统一退出入口处理。授权/锁屏变化清列表且丢弃旧响应，IP/设备/用户名不进入操作登记。现无原操作完成查询，后续新增必须单独完成后端安全与持久证据验收。
C3d3b1：DELETE /monitor/sql和POST /monitor/publications/{id}/retry为NATIVE。前端用useMonitorCommands按统计集合/事件ID登记；当前计数归零、事件状态变化或分页列表缺失均不证明本次命令终结，未知必须保留，不能套LOCAL回执查询或重放。直接成功仅确认重置/重投命令，不代表异步监听器完成。SQL现为当前实例统计，不宣称全节点效果或固定节点路由；多实例边界继续按P08验收。
C3d3b2消息通知：socket-ticket为短期一次握手，重连重新申请票据不是业务写重放，不保存票据或套LOCAL回执。messages在申请票据和读summary时绑定原token；身份判断不能只比较userId，必须覆盖同账号换会话、portal、锁屏/权限和生命周期。旧socket的强制退出、SESSION_INVALID异步/me回调不能清除新身份或停止新连接；锁屏同步关闭连接并清显示计数，解锁后重新授权读取。
C3d3b3默认身份绑定：createApi在调用时捕获当前token（未登录为null），Axios真正发送前再次核对；RequestOptions.expectedIdentity可显式指定原会话或匿名，不能作为HTTP头/正文参数。特殊原始Axios写适配器必须用bindRequestIdentity或直接绑定已捕获Authorization；ruleWrite和cache submit已接入，保留精确JSON/原键/节点选择边界。调用时捕获不替代确认前原意图预留与原生UNKNOWN恢复，也不代表跨标签原子互斥。
当前生产入口逐组覆盖及只读POST/动态代理例外见[前端写入口清单](security-frontend-write-inventory.md)。新增入口须同时登记实际事件处理器、调用适配器、LOCAL/NATIVE/QUERY分类和验证方法，不能用搜索行数或随机请求键替代治理证据。C3/C4已本地完成；真实后端S1d及X5仍独立验收。
C4a1/a2普通操作跨标签：useSubmission同步reserve后必须await registry.admit，再进入异步确认/校验；自定义调用者不得跳过admit。execute本身也检查准入；输入在点击时固定，不能在await后重新取编辑中的表单。Web Locks按已验证站点/API/tenant/portal/user＋资源集合协调，token只在内存。不同对象可并行，同对象/重叠集合拒绝且不排队执行；短ledger锁仅包同步容量检查和发送前UNKNOWN写入，不能在其中做HTTP或等待弹窗。

lookupLocal/lookupNative/continueNative与发送共用资源锁，锁内重读持久记录；storage通知仅刷新，不代替原子锁或权威终态。取消未发送意图释放临时锁，关页只释放浏览器锁，持久UNKNOWN必须保留；NOT_FOUND/统计/列表不解除。能力缺失/存储故障明确拒发，不静默退化。只保证同origin同浏览器，跨设备/服务端仍由服务端授权与幂等保障；独立凭据门禁的跨标签治理已按C4a3完成本地验收，具体边界见下文。

C4a3c1令牌存储：getToken优先本标签sessionStorage token；本标签detached=1时不回落localStorage共享token。saveToken(...,false)只保存私有会话并detach，不删除其他共享身份；明确remember保存选择共享slot并重新接入。clearToken(expected)仅当有效token仍匹配时清除各slot中相同token并detach，其他token保留，返回false表示未清理。没有旧标记的标签保持原共享读取行为。detached只是布尔本地退出/私有会话状态，不含凭据，也不代表后端退出成功。认证流程仍须核对原意图/会话与取消归属；此存储边界不替代后续c2/c3跨标签认证协调。
C4a3c2登录/MFA调用先同步占位，再申请同origin认证存储槽Web Lock；锁名只有origin/固定资源，不包含用户名、密码、OTP、challenge或token。输入先快照；冲突/缺能力拒发，不排队，不持久化认证恢复记录。锁覆盖HTTP及本次权限读取，MFA用户输入等待期间释放。新显式登录可建立新会话，取消旧登录只丢弃本地归属，不证明后端旧请求已取消。

登录开始detach本标签，保留另一共享身份；授权前后核对原共享槽，MFA挑战仅内存并绑定槽快照。共享身份变化时旧认证/待MFA失效，旧共享UI清理且不自动切换为新账号；独立私有会话保持。页面取消必须用captureAuthentication()取得的代次句柄，禁止旧页面直接reset当前新认证；它只清本次接受token，其他共享token保留。销毁不创建/更新依赖store，只撤销内存归属和释放锁/监听。原会话退出跨标签去重仍归c3。
C4a3c3 auth.logout(beforeExit?)：先同步防重复并按原token摘要取得浏览器锁，再等待beforeExit；拒绝确认返回false且不写退出元数据/不请求。确认后session-exit存LOCAL_EXIT（schema/scope/key/state，≤512字符、≤256scope），仅首个发送者得到原键。该标记不是服务器成功回执；网络未知、关页、刷新都不重发。load若当前token匹配退出标记，在权限GET之前停止恢复。其他匹配标签detach本地并失效身份，不先删共享token；新身份/其他私有会话不受影响。

返回true只表示此次仍属于原身份且本地已退出，不能推断服务器已注销。缺能力/存储/容量或已有退出者时，保留未保存确认后只本地退出，不额外发请求。返回false表示确认取消、重复或归属过期；不得据此导航新账号。无token的退出也须保护等待确认期间的新登录。beforeExit失败不清身份；网络失败在清原本地身份后继续抛错。清理/失效释放退出锁；匹配其他标签的LOCAL_EXIT可强制结束本地旧会话，不能由旧弹窗晚返回复活。
C4b1顶部OperationPanel/useOperationPanel只投影当前owner的普通注册表，不合并凭据、锁屏、认证的独立协议标记。查询限定LOCAL原键，成功/FAILED_FINAL仅按权威回执释放；面板提示回到业务页面授权重读，不把回执当DTO。NOT_FOUND/畸形/网络错误仍保留；原生任务不走LOCAL、不自动重发。20次同步查询合并为一个在途读取；锁屏/身份切换/销毁隔离旧展示。容量与存储故障明确提示，不把读取失败显示为空列表。C4b2继续离页/刷新提示及受授权业务恢复入口。

C4b2管理壳在慢响应/未决状态持续提示离页后输入和File的丢失边界；提示不阻断正常导航，未保存草稿的确认仍归各业务页面。刷新只读取持久元数据，不自动核对或重放。业务恢复入口由operation-recovery白名单给出静态已注册路径，按当前路由权限校验，操作编号与正文不进入URL。UPLOAD且operation为file.upload/file.image-upload时，面板使用文件模块lookupUpload/uploadEvidence；ACCEPTED/RUNNING保持PENDING，原任务ID不符或缺证据保留未知，最终回执不替代文件当前ACL。无File则面板只查，不续传、不下载/绑定；其他NATIVE只返回已授权业务入口，缺少历史回执不能用当前列表解除。

C4c独立旧客户端兼容已本地验证：原客户端缺键补UUID、显式原键及原生载荷不变、非零业务/治理错误保留ApiError.key/details且不自动重试。它没有新版完整的用户意图注册表和跨标签协调，不得因请求头兼容声明同等交互保障。旧版生产源码与next仍独立；验证命令和证据见前端进度。
