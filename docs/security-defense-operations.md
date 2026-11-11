# 安全防御运行与接入

真实交付状态见[专项进度](security-defense-progress.md)。密码策略保持 P07，不在本专项调整。

本地数据库专项使用独立可清空测试库及隔离构建目录。含万条容量夹具时，测试JDBC URL保留 `rewriteBatchedStatements=true`（与仓库测试配置缺省一致），避免准备数据逐条自动提交拖过用例有效窗；这仅优化测试连接发送批次，不修改生产MySQL持久性或容量断言。多上下文整合可在本次测试进程设置 `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0` 和 `-Dspring.test.context.cache.maxSize=2`，避免累计连接池挤占本机其他工作。不得为了提速跳过数据库清理安全检查或降低全局持久性参数。

## 浏览器写请求

`BrowserRequestFilter` 在 Spring Security 前处理 `/api/**`，不替代认证/对象授权。写请求拒绝未受信 Origin、无受信来源的跨站 Fetch Metadata，以及 text/plain、application/x-www-form-urlencoded；JSON 和受支持 multipart 继续由实际接口解析。GET/HEAD/OPTIONS 不执行此写来源检查，仍经过原安全链。`/open/**` 保持原机器签名协议。

同源比较使用容器实际 scheme/Host/port，不自行信任客户端 Forwarded/X-Forwarded-*。代理应保留外部 Host 并在可信边界处理 TLS；若后端看到内部地址，可使用启动配置 `sma.security.browser.allowed-origins`（环境变量 `SMA_SECURITY_BROWSER_ALLOWED_ORIGINS`，逗号分隔）显式登记完整外部来源，如 `https://admin.example.com`。不接受通配、路径、用户信息或 opaque origin。该配置只用于写请求来源校验，不自动打开跨域 CORS。

本机 dev profile 仅显式允许 localhost/127.0.0.1 的 5173/5174；生产必须使用明确的生产 profile 和实际域名，不沿用默认 dev。独立 E2E 使用自己的精确前端端口来源。配置启动生效，没有数据库任意覆盖入口。Cookie 认证若未来加入，须同时加入绑定会话的 CSRF token，不能据此过滤器豁免。

API 返回 nosniff、no-referrer 和受限 Permissions-Policy。页面 CSP 必须在真正托管前端 HTML 的站点配置，其验收在 S1.2，API 响应头不能替代页面策略。

`/api/**` 的非安全方法JSON（含+json）在Jackson解析前检查实际字节：`sma.security.browser.maximum-json-bytes` 默认2097152（2 MiB）。正文延迟到下游实际读取时才加载，认证已拒绝的请求不读取正文、不占解析名额。按当时真实认证主体分配独立名额：后台人类 `maximum-concurrent-json` 默认16（1～128），匿名 `maximum-concurrent-anonymous-json` 默认4（1～16），不能靠Bearer头的存在进入人类名额。环境变量依次为 `SMA_SECURITY_BROWSER_MAXIMUM_JSON_BYTES`、`SMA_SECURITY_BROWSER_MAXIMUM_CONCURRENT_JSON`、`SMA_SECURITY_BROWSER_MAXIMUM_CONCURRENT_ANONYMOUS_JSON`，启动生效；字节范围1 KiB～16 MiB。缺失或伪造Content-Length仍按实际读取拒绝413；名额用尽返回503和Retry-After，不无限排队。仅在内存保留有界原始字节并在同步链结束后清零，不改写控制台代理内容。LOCAL规范化指纹仍有独立1 MiB上限；multipart沿用上传配置，二进制、/open和/app保留各自原生限制。本边界不替代反向代理的连接/读取超时和入口总速率配置。

新版 Vite dev/preview 的 HTML 已配置 nosniff、SAMEORIGIN、no-referrer、Permissions-Policy 和 CSP Report-Only。Nginx 示例为 [nginx-security-headers.conf](../tools/deploy/nginx-security-headers.conf)，须在实际服务 HTML 的 server/location include；子 location 的 add_header 会影响继承，需要实测。Report-Only 仍不拦截资源；先观察本站 Monaco/worker/图片/iframe/WebSocket，再按部署域名收紧并切换为强制 CSP。HTTPS/HSTS 由实际TLS入口配置，未确认全子域HTTPS前不启用 includeSubDomains/preload。本阶段不声称已部署生产Nginx或收集CSP报告。

新版富文本统一清洗并禁止媒体跟踪标签，链接/跳转共用URL语法校验，清洗器不可用时只显示文本。旧前端公告仍依赖服务端 Jsoup 清洗；本专项不把旧前端的直接 v-html 当作新增页面的可复用范例。旧品牌接口迁移由租户平台同批处理。

## URL 与 SQL

`common :: security` 的 `WebAddresses` 提供本地资源路径、http(s) 链接及精确 Origin 的语法校验。禁止危险协议、凭据型 URL、控制字符和反斜线歧义；网络出站的 DNS/IP/端口授权属于 SSRF 策略，不能以这个语法函数替代。旧品牌参数在写入端增加 URL 语法校验，品牌迁移仍由租户平台唯一负责。

`SqlStructureSafetyTests` 禁止 Mapper SQL `${...}` 文本插值并验证排序拒绝路径；不是完整静态污点分析或运行时注入检测。数据值继续参数绑定，动态表/列名只能来自受控映射，常量 `FOR UPDATE` 不受误伤。测试查询中的 SQL 符号应当按普通数据检索，不能靠删除字符改变业务输入。

## 请求治理契约（基础本地验收通过，接口按B1～B6接入）

JSON正文包装器只支持当前同步MVC阻塞读取链，名额在该链结束后释放，正文同时清零。新增Servlet异步正文消费、流式JSON或异步代理时必须重新设计读取生命周期并增加专项验证，不能直接复用该包装器。此限制不涉及既有独立WebSocket协议。

`common :: request` 的 `RequestExecution.executeLocal` 仅包装宿主同一数据库/事务资源内的写操作。执行和回执读取均检查当前主体/租户/操作权限；最小回执属于调用者，不含业务内容，可在对象删除后查询。实际业务内容读取和变更仍须由模块服务执行当前对象及数据范围授权，不能以回执替代。回调中不能执行网络、文件发布、REQUIRES_NEW写入或跨资源事务。业务变更与SUCCEEDED回执同事务提交。主键/业务状态/版本仍由业务模块控制，不能靠幂等键抵抗不同键重复业务动作。

首次执行返回业务结果；重放仅返回不含业务正文的回执，业务详情必须重新通过原授权查询取得。原随机键可用于查回执以应对首次响应丢失；同一主体在不同操作/目标上复用原键也拒绝。RUNNING超时只显示UNKNOWN，绝不释放执行权或自动重跑。确认回滚后为FAILED_FINAL，同键不自动重试；不确定结果需要业务对账。账本初始上限每租户100000条，达到上限拒绝新写入；尚无自动删除未决记录或对外清空入口。

启用受保护写接口前设置 `sma.security.requests.fingerprint-key` 为32字节随机密钥的Base64（环境变量 `SMA_SECURITY_REQUESTS_FINGERPRINT_KEY`）；不写入仓库或前端。`fingerprint-key-id` 默认v1，轮换时更换ID并通过 `previous-fingerprint-keys` 保留旧键（`旧ID=Base64;另一ID=Base64`，最多8键）。老回执仍依赖其记录的键版本；完成相关回执迁移/退役前不能移除旧键。缺键拒绝受保护写操作，不影响启动和已有未接入接口。此处只改变请求指纹密钥，完全不改变用户密码策略或哈希。

`max-records-per-tenant` 与 `max-concurrent` 为启动配置（默认100000、8）；配置需与实际连接池预算协调。限流使用数据库原子预算，429携带Retry-After；普通检测事件存储故障不放宽认证，幂等权威不可用时不绕过提交。跨资源二开需使用业务资源内账本/Outbox适配器，不能直接套平台库executeLocal。

已接入的GovernedApi LOCAL写接口要求Idempotency-Key。同键重放先核对指纹和权限，只返回最小回执；不同键快速重复还受主体/接口/对象冷却控制，`minimum-write-interval` 默认500ms（100ms～60s，启动配置）。已有对象按路径标识隔离，个人偏好按本人隔离；创建型接口按本人＋接口限制短突发，批量调用要使用明确登记的批量策略。429返回Retry-After，不能自动换键重试。原键查状态使用POST `/api/common/requests/lookup`，业务详情仍走原授权查询。

B4新增策略的实际验收状态见专项进度：`LOCAL`使用上述同库回执；`QUERY`用于已审查的无业务写入POST查询/模拟，不生成回执；`NATIVE`保留模块自有任务、operationKey、部分提交或运行时副作用语义，不由通用层声称原子完成。非LOCAL策略必须填写审查原因，响应标记X-Request-Policy；此响应头不授予身份或权限。模板发布的候选报告使用独立事务，发布/规则批次/缓存任务应查各自原生状态；事件重投保留框架至少一次语义，运行时清理需检查当前资源状态。NATIVE超时/异常不能推导全部回滚，也不能自动重发。原生重放仍可能遇到HTTP冷却，应遵守Retry-After，不能改用新键绕过。

`RequestAdmission`不增加数据库事务或通用回执。原生执行与POST查询和LOCAL写共享本节点的max-concurrent预算，同步嵌套调用复用已有名额，防止缓存任务套接导致双重占位；这不是集群总并发上限。预算/冷却使用共享数据库，任务正确性和异步worker并发继续由所属模块负责。普通GET的执行并发不由该名额控制。

P08双进程验收修正：LOCAL租户容量锁允许在现有3秒短事务期限内等待，避免不同用户在短暂受理竞争时立即503；容量预留提交后才执行业务。业务资源冲突锁仍NOWAIT，同键重放、最大记录数和未知结果不自动重跑保持原协议。RequestCapacityContentionTests覆盖短占用成功及长占用有界拒绝；跨进程与负载实测状态见[P08进度](cache-governance-progress.md)，不以此推导集群无限并发能力。

Auth与SocketTicket使用原生会话/挑战/一次性票据协议；绑定sma.session-id的处理方法按服务端解析出的会话区分冷却，客户端同名头无效。匿名登录、验证码、MFA挑战仍走原限流与防重放，机器/open签名原始字节和nonce不变；密码长度、弱密码及双向不同规则未调整。插件invoke/work/reconcile均保留NATIVE，能力适配器仍承担副作用与授权契约。

扩展控制台的隧道POST由所属模块用同一转发路由白名单判定GET查询和写入，不能靠客户端声明safe或版本跳过治理。分片资源按真实上传/文件/分片目标隔离，数字索引规范化；默认写预算600/分钟以支持正常分片并发，读预算240/分钟，两类计数分开，仍受租户总预算及本节点并发约束。治理层不读取或改写代理正文。NATIVE额外作用域必须有界、经过模块验证，不能包含凭据或正文。

频率计数是临时状态：每次成功预占最多清理当前租户2条已过期超过5分钟的计数，使用过期索引及SKIP LOCKED，不删除正在使用的预算。幂等回执、对象互斥和UNKNOWN工作不参与此清理；这里不提供对安全账本的手动清空接口。

开发启动脚本会从被Git忽略的 `data/keys/request-fingerprint.key` 读取或首次创建独立随机密钥，限制文件权限且不覆盖已有文件；显式环境密钥优先。普通 `java -jar` 生产部署必须自行配置密钥。该助手未改变用户密码规则，也不会启动或重启服务。独立E2E使用自己的随机密钥和测试数据库。

已验收的IAM组织、岗位、导航、资源、角色及授权、用户与个人资料接口遵守同一协议。角色编辑与授权共享对象互斥组；不同对象的冷却分别计算。密码修改成功后原会话可能按既有策略失效，需重新登录再查本人回执；回执不会重放密码或个人资料，无法据回执替代当前对象读取权限。

消息/待办/随手栏的个人写入按用户＋对象隔离；公告管理使用共享对象锁，个人已读按本人覆盖。便签CreateNote.requestKey和version继续生效，HTTP操作键不能替代领域键或版本。消息回执不缓存正文，重放不会再次发布事务事件；SocketTicket仍使用原生一次性协议，入口适配按B5另验。

## 隔离验证入口

后台租户/域名操作使用平台目录自己的运维身份判断及显式权限。品牌草稿、Mail配置/模板、Identity后台配置为LOCAL；品牌发布/回退继续使用请求体operationId及原生查询，邮件取消查看原投递任务状态，不以通用账本替代。预览为QUERY。应用端/app的Cookie/CSRF与原生Bearer仍走独立协议，不能使用后台人类账本豁免身份检查。

只写密码/Google secret等输入必须参与同键异参检查；内部指纹序列化器仅用于摘要，不得用于API响应或日志。通用账本存HMAC而非秘密正文；原普通请求的规范化摘要保持兼容。配置密码留空保留旧值等业务语义仍由所属模块判断。

### 文件上传协议（B5b本地验收通过）

`POST /api/file/files` 与 `/api/file/files/images` 要求同一次用户意图使用稳定的 `Idempotency-Key`。上传为NATIVE：通用本库事务只接受file模块任务、预留文件ID，随后由file任务唯一领取并写入存储。通用接受回执的SUCCEEDED仅表示任务已受理，不表示文件可用；客户端必须查file回执。首次成功继续返回原FileView，并通过 `X-Upload-Id` 给出任务ID；成功重放通过当前文件ACL重新读取，不缓存业务响应。

响应丢失后使用 `POST /api/file/uploads/lookup`（JSON `{ "key": "原键" }`），或 `GET /api/file/uploads/{uploadId}`。回执仅含本人任务ID、状态、成功文件ID、时间及resumable，不含文件名、文件内容或原请求键。lookup不要求重新发送字节。普通文件回执还要求当前上传权限，图片入口保留原本人上传规则；租户/portal/用户及租户安全代次必须匹配。

只有ACCEPTED且resumable=true，才允许用户显式使用原键和原文件恢复；原子领取后为RUNNING。RUNNING或UNKNOWN绝不重新写入，超时不释放执行权。SUCCEEDED表示已核对提交的预留ID、归属、长度和校验和；FAILED_FINAL不自动重试。同键改字节、原文件名、类型或上传用途返回冲突。非完成的上传响应为202（失败终态409），携带file回执；遵循Retry-After后查询，不换新键重传。

数据库提交确认不确定时保留二进制，只有确认回滚才尝试删除本次写入的对象。任务结果更新失败后，查询可依据已提交文件元数据收敛为成功；历史逻辑删除元数据仅作完成证据，内容仍不可下载，也不会被重放恢复。UNKNOWN及孤立二进制需要人工核对，当前不提供自动清空/重跑入口。此协议未宣称本地磁盘掉电耐久、跨数据库原子性或未来云存储适配已验收；存储扩展须保留同一任务边界。

使用 Java 21、根 Maven reactor、`-Dsma.verify.directory=target/security-defense/target`。设置 `SMA_TEST_DB_URL` 指向专用 `sma_test_security_defense`；Flyway clean 只允许测试命名库。不要对正在运行的用户服务执行攻击或超频演练。

全套测试会创建多种Spring上下文，标准缓存可让多组Hikari池长期占连接。本专项的隔离完整验证使用 `-Dspring.test.context.cache.maxSize=2` 与仅本次测试进程的 `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0`，限制空闲测试池保留，不修改数据库全局max_connections或运行服务。各extension模块未覆盖sma.verify.directory时在独立源码快照运行根verify，记录源文件哈希与后续差异；不能把旧快照当成并行更新后源码的验收。
