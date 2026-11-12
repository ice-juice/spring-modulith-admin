# 安全防御运行与接入

真实交付状态见[专项进度](security-defense-progress.md)。密码策略保持 P07，不在本专项调整。

本地数据库专项使用独立可清空测试库及隔离构建目录。含万条容量夹具时，测试JDBC URL保留 `rewriteBatchedStatements=true`（与仓库测试配置缺省一致），避免准备数据逐条自动提交拖过用例有效窗；这仅优化测试连接发送批次，不修改生产MySQL持久性或容量断言。多上下文整合可在本次测试进程设置 `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0` 和 `-Dspring.test.context.cache.maxSize=2`，避免累计连接池挤占本机其他工作。不得为了提速跳过数据库清理安全检查或降低全局持久性参数。

## 请求来源观测（S2a1）

`RequestSourceQuery.current()`提供当前同步HTTP线程的不可变网络观测，包含直连地址、解析地址、来源依据和已剥离受信跳数；不包含转发头原文、路径、查询、正文或凭据。来源观测不证明操作者或租户身份。默认不信任代理，仅使用容器直连IP；已有认证预算继续使用原地址，不能把本子项当作所有认证限流已切换到真实客户端IP。

| 部署配置 / 环境变量 | 默认与边界 |
| --- | --- |
| `sma.security.source.trusted-proxies` / `SMA_SECURITY_SOURCE_TRUSTED_PROXIES` | 空；最多64个严格IPv4/IPv6地址或CIDR，逗号分隔，拒绝主机名、端口、zone、/0和含主机位的网段 |
| `maximum-hops` / `SMA_SECURITY_SOURCE_MAXIMUM_HOPS` | 16，范围1～32；X-Forwarded-For地址数量上限 |
| `maximum-header-characters` / `SMA_SECURITY_SOURCE_MAXIMUM_HEADER_CHARACTERS` | 2048，范围64～8192；整条X-Forwarded-For字符上限 |

仅当直连IP命中受信网段，才解析X-Forwarded-For；从最右侧向左剥离连续受信代理，在第一个不受信地址停止，不使用攻击者自行添加的左端地址。接入代理必须覆盖外来转发头并正确追加上一跳；本配置不能证明代理没有伪造地址。所有地址均作字面量解析，无DNS。IPv4映射IPv6规范化为IPv4；映射网段只接受对应IPv6前缀97～128，并按IPv4范围规范化。

没有转发头记`MISSING_CHAIN`；重复、畸形、超限、全链均受信无可辨客户端，或受信对端提供不支持的`Forwarded`头，记`INVALID_CHAIN`并保留直连证据。非受信对端的任何转发头只记`UNTRUSTED_FORWARDING`。这些均不改变原请求是否放行，不能由观测故障绕过原认证/授权。客户端不确定时不得凭直连代理IP自动封禁整条入口链。

配置重启生效，`server.forward-headers-strategy=none`必须保持。该信任表独立于租户入口的精确`trusted-proxy-addresses`：来源CIDR不能授权X-Forwarded-Host/Proto，也不改变Host到租户的映射。来源策略加入`common.host-policy`部署摘要，新旧宿主策略摘要不同，升级按既有部署资格流程重新建立证据，不能混用旧基线。同步结束/异常均清理上下文；异步只允许明确捕获DTO，不能继承线程身份。来源契约本身不负责持久化；事件存储和采集见下方S2b/c，封禁或告警仍按后续阶段验收，状态见[采集进度](security-event-progress.md)。

## 安全信号捕获（S2a2）

`SecuritySignalCapture.capture(Fact)`只生成不可变信号，没有入队、存储或通知效果。调用点必须是在实际观察到事实且认证上下文尚未清理的位置；调用方只能选择固定事实枚举，不能传tenantId、身份、原始证据或自定义错误文本。每次生成独立eventId，同一同步HTTP请求共用服务端随机correlationId，不采用请求头的traceId。后续持久化必须保留采集时分类与ruleVersion，不能重算历史分类替代当时证据。

匿名即使Host已归属某个租户也进入PLATFORM；未知主体、主体与入口tenant/portal不一致、HTTP经withinJob转换身份均为PLATFORM/UNRECOGNIZED，不携带被拒绝的主体/租户ID。正常后台、应用用户和机器主体按认证上下文保留租户和稳定ID，应用audience/realm/client与后台portal分开。可信后台任务只保留tenant和SYSTEM_JOB，不复制可能包含业务正文的workId。事件DTO本身不是授权证明；未来存储接口不能直接接受HTTP反序列化的DTO作为可信事件。

事实分类明确区分拒绝结果和攻击推断：普通认证、授权、参数、容量、文件检查失败只记录事实；签名重放和诱捕命中可标记可疑活动，但没有任何“利用成功/系统失陷”值。当前不采用户名、邮箱、凭据、完整路径/查询/正文，信号结构受限；后续补路由、规则、环境节点等元数据时必须继续做白名单和总大小验证，不能新增自由文本透传。有界采集/缓冲见下方S2c，生产拒绝点接线仍归S2d。

## 安全事件存储（S2b分阶段）

defense拥有平台事件、租户事件及相应容量计数。平台事件不使用tenant=0/1伪造归属；租户表继续由租户拦截器保护。内部宿主工作线程可把可信捕获包写入独立3秒事务，事件与计数原子提交；同ID同摘要返回DUPLICATE、异内容CONFLICT，容量拒绝CAPACITY，超过保留期EXPIRED。失败提交可能需要按原eventId核对/重投同一包；不能生成新ID掩盖不确定结果。有界队列/故障缓冲通过下方S2c组件显式启用；生产拒绝点尚未按S2d接线，不能据此宣称全站自动采集。

| 配置 / 环境变量 | 默认和范围 |
| --- | --- |
| `sma.defense.storage.maximum-platform-records` / `SMA_DEFENSE_MAXIMUM_PLATFORM_RECORDS` | 100000，1～10000000 |
| `maximum-tenant-records` / `SMA_DEFENSE_MAXIMUM_TENANT_RECORDS` | 每租户100000，1～1000000 |
| `maximum-tenant-total-records` / `SMA_DEFENSE_MAXIMUM_TENANT_TOTAL_RECORDS` | 所有租户合计1000000，不小于单租户预算且不超过10000000 |
| `retention` / `SMA_DEFENSE_RETENTION` | 30d，1小时～365天，按发生时间计算，不由重复投递延长 |
| `cleanup-batch` / `SMA_DEFENSE_CLEANUP_BATCH` | 100，1～100；仅清理过期且未held记录 |

上述是部署启动配置，纳入`defense.storage-policy`必要证据源；新模块需要先执行defense V1迁移，再以新制品重新建立部署证据，旧validate宿主不能冒充已迁移。容量按行限制，单条payload同时受2KiB UTF-8限制，不包含MySQL索引/事务日志空间；持有记录仍占容量。普通业务不能修改payload或直接删表释放计数，DBA有高权限，应用约束不是数据库防篡改保证。未接管理API前，内部hold原语仅供受信宿主工作流，不能当作已交付法证管理功能。

平台只读查询必须同时满足部署平台操作者与`defense:platform:view`，该权限不默认赋予超管；本租户查询需`defense:event:view`和后台admin入口。平台操作者也不会自动获得所有租户安全事件。Java契约每次最多100条，HTTP入口与权限边界见下方S2d2a；当前无后台页面或导出功能，自动保留调度和最终组合按S2后续阶段验收。

## 有界采集与节点故障缓冲（S2c）

可信模块调用`SecuritySignals.observe(Fact)`，身份在调用线程通过现有捕获服务固定。返回`QUEUED_VOLATILE`仅表示进入内存，进程在写盘前终止仍会丢失；`DISABLED`表示部署未启用，`REJECTED`表示准入/速率/内存/锁争用/捕获失败。HTTP调用路径不进行磁盘、数据库或外部网络IO，也不能提交任意SecuritySignal/tenantId。拒绝采集不能使原认证/授权失败变成成功。生产拒绝点尚须S2d接线，本组件存在不代表全站攻击监控已启用。

后台单线程每单元先取得DeploymentWork许可，再将原信号写入加密目录，然后尝试数据库。STORED/DUPLICATE/EXPIRED才删除对应磁盘项，CAPACITY/CONFLICT或未知提交保留原eventId及原包；轮转查看其他记录，失败按固定间隔重试而非紧循环。EXPIRED遵循事件保留期，计入独立计数，不能当作成功存储。磁盘先写临时文件、force后原子移动；重新打开时严格核验并恢复完整临时文件，部分写、错钥、篡改、格式/规则版本不兼容或未知文件使缓冲失败关闭并保留文件，不能自动删除坏文件后宣称恢复。

| `sma.defense.collection.*` / 环境变量 | 默认与边界 |
| --- | --- |
| `enabled` / `SMA_DEFENSE_COLLECTION_ENABLED` | false；启用须同时提供directory、key-file及scope |
| `directory` / `SMA_DEFENSE_COLLECTION_DIRECTORY` | 无默认目录；每节点独占私有目录，OS文件锁禁止两个进程同时消费，不得共享网络目录或人工写入 |
| `key-file` / `SMA_DEFENSE_COLLECTION_KEY_FILE` | 无默认密钥；受保护的32字节原始二进制文件，放在缓冲目录外，仅服务账号可读，不能放Git/VITE/日志，不自动创建 |
| `scope` / `SMA_DEFENSE_COLLECTION_SCOPE` | 必填环境＋节点稳定标识，如`staging.node-a`；1～96位小写字母数字点横线，参与HMAC派生，重启必须保持 |
| `memory-records` / `SMA_DEFENSE_COLLECTION_MEMORY_RECORDS` | 1024，4～10000；四分之一容量为非LOW信号预留 |
| `disk-records` / `SMA_DEFENSE_COLLECTION_DISK_RECORDS` | 10000，4～100000；四分之一记录名额预留 |
| `disk-bytes` / `SMA_DEFENSE_COLLECTION_DISK_BYTES` | 33554432，8192～1073741824；四分之一密文预算预留，单条JSON包最多2KiB |
| `per-second` / `SMA_DEFENSE_COLLECTION_PER_SECOND` | 1000，1～10000；节点固定窗口，LOW不能用完最后四分之一配额 |
| `batch` / `SMA_DEFENSE_COLLECTION_BATCH` | 32，1～100；单轮后台单元上限 |
| `retry-delay` / `SMA_DEFENSE_COLLECTION_RETRY_DELAY` | 5s，250ms～5min；文件/数据库失败和积压处理退避 |

全部启动生效；预算/启用状态进入部署摘要，节点私有路径、scope和密钥不比较成跨节点相同值。各节点必须使用独立目录、scope和密钥；目录或密钥迁移须停止旧消费者并完整搬迁，不能给有积压目录换密钥/scope后删除报错文件。缓冲加密使用AES-GCM，AAD绑定版本与事件ID，派生密钥绑定scope；不提供任意客户端上传/解密接口。权限、ACL及备份由部署管理，应用文件校验不能防御已掌握服务密钥的系统管理员。禁用配置仍保留已有磁盘文件；重新启用以原配置恢复。

固定指标：`sma.defense.collection.enabled`、`sma.defense.queue.records`、`sma.defense.spool.available/records/bytes`；`sma.defense.collection{result=...}`包含queued/disk-persisted/stored/duplicate/expired/conflict/capacity/failure/drop-capacity/drop-rate/drop-contention/drop-capture/drop-shutdown。计数是本进程尝试次数，不保证事件数或跨进程累计；spool unavailable时records/bytes只是最后已知值，不能当实时占用。无IP/租户/事件ID/文件路径动态标签。部署监控需独立观察长期unavailable、failure/drop增长和容量，不能依赖故障中的事件库自身告警。

摘流或关闭停止接收新观察与新工作许可；已准入的实际IO保持许可到退出。关闭最多观察3秒，超时不强制释放文件锁；后台最终退出后才清理，尚在内存的信号单独记drop-shutdown，不算已落盘。该语义与force/原子移动测试证明进程故障恢复，不证明断电、硬盘缓存或网络文件系统耐久。磁盘故障和内存容量耗尽时可能丢弃，必须检查指标，不能承诺无限无损。

## 统一拒绝响应观测（S2d1）

宿主的 `ErrorResponseWriter` 与 MVC 异常响应共同经过固定错误码映射。它在当前请求的认证上下文清理前尝试调用一次 `SecuritySignals.observe`；同一次拒绝不会因过滤器与异常处理重复经过而重复观察，缺失采集器、采集异常或拒收也不重试。采集仍受上述显式启用、容量和节点配置约束；默认禁用时，原拒绝照常返回。

| 已接入的明确事实 | 分类及范围 |
| --- | --- |
| 未认证、凭据/验证码/TOTP/Google证明/邮箱挑战/密码登录/再认证无效 | `AUTHENTICATION_REJECTED` |
| 权限不足、机器签名无效 | `AUTHORIZATION_REJECTED` |
| 明确参数校验、正文/文件大小、媒体类型及必要请求键拒绝 | `REQUEST_INPUT_REJECTED` |
| 已审请求/认证/身份挑战/上传额度拒绝 | `REQUEST_BUDGET_REJECTED` |
| 机器签名 nonce 重放 | `SIGNATURE_REPLAY_REJECTED`，仅标记可疑活动 |
| 租户入口的格式/未知入口/访问拒绝 | `TENANT_ENTRY_REJECTED`；基础设施不可用不归为该事实 |
| 后台浏览器 Origin / Fetch Metadata 拒绝 | `BROWSER_ORIGIN_REJECTED` |

错误码清单由宿主 `RequestRejectionSignals` 固定审核，不按任意 HTTP 状态、翻译文案或响应正文推断。普通未知路由404、数据不存在、版本冲突、幂等已完成/处理中、锁屏、普通图片无效和5xx故障不在该清单。MVC业务拒绝沿用HTTP200＋非零业务code，采集不改为401/403；过滤器原状态码、正文及响应头同样保留。机器签名重放在主体认证完成前被拒时归平台，不能根据请求中的appId/token推断租户。

受限请求元数据与框架未知/缺失资源观察见下方S2d2b；关联扫描检测、文件扫描结果接线及告警仍待后续。`RequestRejectionHttpTests`使用真实Tomcat和既有安全链，最终采集sink为受控替身，用于断言捕获时机和响应兼容；它不代替启用真实worker后的HTTP到数据库持久化组合验收。当前状态与实际命令见[采集进度](security-event-progress.md)。

## 安全事件查询 API（S2d2a）

以下入口使用后台认证，事件数据只读；请求限流计数仍按宿主标准治理更新。平台事件和租户事件分别查询，没有接受客户端tenantId的跨租户查询模式。实现和本地验收状态见[采集进度](security-event-progress.md)，接口存在不表示当前用户服务已部署。

| 方法与路径 | 权限与行为 |
| --- | --- |
| `GET /api/defense/events?limit=20` | 当前后台租户及显式`defense:event:view`；最近记录，limit 1～100，默认20 |
| `GET /api/defense/events/{eventId}` | 同上，eventId为UUID；只查当前租户 |
| `GET /api/defense/platform-events?limit=20` | 部署指定平台租户/运维用户，并有显式`defense:platform:view`；同样有界 |
| `GET /api/defense/platform-events/{eventId}` | 同上；平台权限不授予读取其他租户事件的能力 |

列表按接收时间和内部ID倒序，仅提供有界最近记录，不支持全量导出或历史翻页。成功响应为原`ApiResponse`，`Cache-Control: no-store`。详情不存在、跨租户或把平台UUID用于租户入口均沿用HTTP200＋非零code及`common.error.data-not-found`；非法UUID/limit返回400，无权限返回403，匿名请求返回401。后台租户超级管理员仍须显式权限；机器和应用用户不能借同名权限访问后台入口。两组接口各按主体＋接口240/min、租户共享读6000/min治理，GET不占写提交名额，不创建业务回执。

固定返回字段：eventId、correlationId、occurredAt、receivedAt、expiresAt、fact、category、severity、outcome、assessment、ruleVersion、attribution、source、held。分类和版本来自持久记录，不用当前规则覆盖；归属和数值网络来源是已授权证据，不能作为身份凭据。接口不返回内部存储payload/payloadHash、容量行、密钥、Cookie、原始header/URI/查询/正文。禁止把查询内容放公共缓存或日志。保留标记只读，本阶段无修改、删除、导出、保留操作或跨租户管理员通道。

## 请求元数据与缓冲升级（S2d2b）

新捕获信号的metadata.observer包含部署组deployment、nodeId和进程UUID processId，取自既有`sma.cache.nodes.cluster/id`及宿主进程身份；部署组应按环境区分，默认sma/local不是可信的业务租户标识。数据在捕获时固定，不在查询或补传时用当前节点覆盖。没有HTTP来源的后台观察保留http为空；它不代表来自当前HTTP会话。

metadata.http.method只保留GET/HEAD/POST/PUT/PATCH/DELETE/OPTIONS/TRACE/CONNECT或OTHER；未知方法原文丢弃。route为MATCHED/UNRESOLVED/UNMAPPED，只有选定HandlerMethod的声明路径与MVC实际模板精确匹配才保存template（最多256字符）；声明过长/复杂组合不匹配则保留UNRESOLVED，不能改存原始URI。状态status是观察时选定的HTTP响应码，未决定时为空；业务拒绝可能是200＋非零code，不能用业务code替代HTTP状态，也不表示网络已发送成功。路径变量、查询字符串、Cookie、凭据和正文仍不保存。

框架资源处理器确认找不到资源，或MVC确认没有handler时，才观察`UNMAPPED_REQUEST_OBSERVED`；它是LOW/OBSERVED/NO_ATTACK_INFERENCE，不触发攻击成功或封禁推断。控制器里的普通业务不存在/404仍不采。认证先拒绝的请求记录认证事实及UNRESOLVED路由，不为猜测路由而在认证前额外分派处理器。应用未收到的边缘拒绝不在此范围。

HTTP查询默认保持S2d2a固定字段。需元数据时，列表或详情显式附加`includeMetadata=true`，例如`GET /api/defense/events?limit=20&includeMetadata=true`；权限/租户/平台运维门禁不变。旧记录没有metadata时仍省略该字段，消费者须接受缺省；默认响应不会因为新字段破坏旧严格解码器。完整真实HTTP/数据库验收状态以[采集进度](security-event-progress.md)为准。

旧六字段SecuritySignal构造和访问器仍可链接；metadata为空时JSON不增加字段，旧摘要不重算。新增元数据参与record等值和JSON，旧二开复制六字段会丢失元数据，不能声称完整复制；应保留原捕获对象，用eventId关联。HTTP默认投影兼容不等于任意直接序列化Java信号的旧严格解码器都兼容。

Fact新增UNMAPPED_REQUEST_OBSERVED：旧消费者若将fact定义成封闭枚举或无默认分支的穷举switch，必须先升级并处理未知事实；仅保持字段集合不能让旧枚举识别新值。兼容测试明确覆盖旧事实可读、新事实被旧封闭枚举拒绝，不能把六参数链接/默认字段兼容宣传为所有旧消费者无修改可用。部署顺序为先适配查询消费者，再启用新版采集；消费者不能把未知事实自动解释为已入侵。

新元数据包及新增UNMAPPED事实使用缓冲schema2，AES-GCM帧/AAD不变；当前消费者可读原schema1包，原包恢复/重复不改写字节。旧消费者必须保留并拒绝schema2，禁止手改版本或剥离字段降级。升级先停止旧消费者，保留数据库、完整缓冲、原目录/scope及密钥再切新版；产生新记录/新包后不能只回退旧JAR。尚无已验证的降级转换工具，回退须隔离写入、保全新版证据并另行核对恢复方案，不能删除新事件或缓冲来掩盖不兼容。2KiB信号/缓冲包预算保持，不新增表或路径豁免。

## 浏览器写请求

`BrowserRequestFilter` 在 Spring Security 前处理 `/api/**`，不替代认证/对象授权。写请求拒绝未受信 Origin、无受信来源的跨站 Fetch Metadata，以及 text/plain、application/x-www-form-urlencoded；JSON 和受支持 multipart 继续由实际接口解析。GET/HEAD/OPTIONS 不执行此写来源检查，仍经过原安全链。`/open/**` 保持原机器签名协议。

同源比较使用容器实际 scheme/Host/port，不自行信任客户端 Forwarded/X-Forwarded-*。代理应保留外部 Host 并在可信边界处理 TLS；若后端看到内部地址，可使用启动配置 `sma.security.browser.allowed-origins`（环境变量 `SMA_SECURITY_BROWSER_ALLOWED_ORIGINS`，逗号分隔）显式登记完整外部来源，如 `https://admin.example.com`。不接受通配、路径、用户信息或 opaque origin。该配置只用于写请求来源校验，不自动打开跨域 CORS。

本机 dev profile 仅显式允许 localhost/127.0.0.1 的 5173/5174；生产必须使用明确的生产 profile 和实际域名，不沿用默认 dev。独立 E2E 使用自己的精确前端端口来源。配置启动生效，没有数据库任意覆盖入口。Cookie 认证若未来加入，须同时加入绑定会话的 CSRF token，不能据此过滤器豁免。

API 返回 nosniff、no-referrer 和受限 Permissions-Policy。页面 CSP 必须在真正托管前端 HTML 的站点配置，其验收在 S1.2，API 响应头不能替代页面策略。

`/api/**` 的非安全方法JSON（含+json）在Jackson解析前检查实际字节：`sma.security.browser.maximum-json-bytes` 默认2097152（2 MiB）。正文延迟到下游实际读取时才加载，认证已拒绝的请求不读取正文、不占解析名额。按当时真实认证主体分配独立名额：后台人类 `maximum-concurrent-json` 默认16（1～128），匿名 `maximum-concurrent-anonymous-json` 默认4（1～16），不能靠Bearer头的存在进入人类名额。环境变量依次为 `SMA_SECURITY_BROWSER_MAXIMUM_JSON_BYTES`、`SMA_SECURITY_BROWSER_MAXIMUM_CONCURRENT_JSON`、`SMA_SECURITY_BROWSER_MAXIMUM_CONCURRENT_ANONYMOUS_JSON`，启动生效；字节范围1 KiB～16 MiB。缺失或伪造Content-Length仍按实际读取拒绝413；名额用尽返回503和Retry-After，不无限排队。仅在内存保留有界原始字节并在同步链结束后清零，不改写控制台代理内容。LOCAL规范化指纹仍有独立1 MiB上限；multipart沿用上传配置，二进制、/open和/app保留各自原生限制。本边界不替代反向代理的连接/读取超时和入口总速率配置。

新版 Vite dev/preview 的 HTML 已配置 nosniff、SAMEORIGIN、no-referrer、Permissions-Policy 和 CSP Report-Only。Nginx 示例为 [nginx-security-headers.conf](../tools/deploy/nginx-security-headers.conf)，须在实际服务 HTML 的 server/location include；子 location 的 add_header 会影响继承，需要实测。Report-Only 仍不拦截资源；先观察本站 Monaco/worker/图片/iframe/WebSocket，再按部署域名收紧并切换为强制 CSP。HTTPS/HSTS 由实际TLS入口配置，未确认全子域HTTPS前不启用 includeSubDomains/preload。本阶段不声称已部署生产Nginx或收集CSP报告。

新版富文本统一清洗并禁止媒体跟踪标签，链接/跳转共用URL语法校验，清洗器不可用时只显示文本。旧前端公告仍依赖服务端 Jsoup 清洗；本专项不把旧前端的直接 v-html 当作新增页面的可复用范例。旧品牌接口迁移由租户平台同批处理。

## URL 与 SQL

品牌公开资产只消费tenant的类型化entry和修订图片接口，不将普通File原件链接改成匿名。公开快照用固定JDK PNG/JPEG/GIF读取器转静态PNG，源和输出各≤2MiB、单边≤4096、像素≤400万、每节点4个解码名额；可寻址输出缓存同样有界，其他ImageIO SPI不会被调用。原件的元数据/尾随内容和GIF后续帧不进入静态公开快照，但原件仍不因此获得病毒扫描通过状态。WebP/ICO/文档/SVG不能作为此接口的公开素材，P09完整扫描和发布仍按其阶段验收。

entry仅有entry/branding/login三个固定投影，不返回原文件ID/路径、草稿、覆盖指令、发布操作者或一般参数集合。匿名图片白名单精确限GET/HEAD及已发布修订；当前租户/portal由受信Host目录建立，未知或停用入口不回落。域名级no-store、image/png和nosniff继续保留；历史发布修订按原契约可读，不等于公开历史原件。三项法律链接和五项帮助链接统一经WebAddresses语法校验；它们是浏览器目的地，不触发服务端抓取，也不构成SSRF出站许可。回归入口为PublicImageEncoderTests、BrandValidationTests、BrandPublicSecurityTests及原品牌/文件专项；X4a纯边界与X4b数据库/HTTP结果分别记录在专项进度。

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

## 预算 scope 与多实例容量（P11 X3）

接口清单的每行包含预算ID、scope、所属模块/存储、N实例换算；对应目录列出物理表、键、条件和事务语义。`shared`指相同数据库资源、相同租户/作用域键和时间窗口上的累计额度，不因增加实例而增大；它不等于某个限流方法一定独立提交。`node`是每进程名额，同配置N实例为N×L，异配置为各节点L之和。换算是受理容量上界，不是吞吐或p95实测。部署入口仍需要全局连接/带宽/读取超时，公开品牌与机器入口的边缘限流按S5配置。

以下表由`EndpointBudgetCatalogTests`与受审端点目录同步检查，默认值改变须重新审查并更新；`L`按接口/场景/租户配置取值，完整维度见[接口清单](security-endpoint-inventory.md)。表不穷举下游工作线程、邮件投递或云厂商限额。

<!-- budget-capacity:start -->
| 预算ID | scope | 所属模块/存储 | 单实例默认 | 3实例同配置总上限 | N实例 |
| --- | --- | --- | --- | --- | --- |
| auth.security | shared | auth/platform-mysql | L（同键/同窗口） | L（仍共享） | 同一资源/键/窗口：L（不乘N） |
| common.admission | node | common/process-memory | 8 | 24 | 同配置：N × 8；异配置：Σ Lᵢ |
| common.anonymous-json | node | common/process-memory | 4 | 12 | 同配置：N × 4；异配置：Σ Lᵢ |
| common.browser-json | node | common/process-memory | 16 | 48 | 同配置：N × 16；异配置：Σ Lᵢ |
| common.requests | shared | common/platform-mysql | L（同键/同窗口） | L（仍共享） | 同一资源/键/窗口：L（不乘N） |
| file.http | node | file/process-memory | 16 | 48 | 同配置：N × 16；异配置：Σ Lᵢ |
| file.local-transfer | node | file/process-memory | 8 | 24 | 同配置：N × 8；异配置：Σ Lᵢ |
| file.tasks | shared | file/platform-mysql | L（同键/同窗口） | L（仍共享） | 同一资源/键/窗口：L（不乘N） |
| identity.email | shared | identity/platform-mysql | L（同键/同窗口） | L（仍共享） | 同一资源/键/窗口：L（不乘N） |
| identity.http | node | identity/process-memory | 64 | 192 | 同配置：N × 64；异配置：Σ Lᵢ |
| identity.login | shared | identity/platform-mysql | L（同键/同窗口） | L（仍共享） | 同一资源/键/窗口：L（不乘N） |
| identity.password | node | identity/process-memory | 4 | 12 | 同配置：N × 4；异配置：Σ Lᵢ |
| openapi.login | shared | openapi/platform-mysql | L（同键/同窗口） | L（仍共享） | 同一资源/键/窗口：L（不乘N） |
| tenant.brand-work | node | tenant/process-memory | 4 | 12 | 同配置：N × 4；异配置：Σ Lᵢ |
<!-- budget-capacity:end -->

例如3节点应用链的64个名额合计最多192，后台提交8个合计24，品牌图片事务4个合计12；同一租户后台写预算仍为1500/min，不是4500/min。节点不同设置时，例如请求名额8/4/4合计16；实例新增/下线要重新核算连接池和下游处理预算。

门禁串联时取共同约束，不能相加：应用上传同时占用应用链64、上传入口16，字节处理还占本地I/O 8；3节点同配置分别为192、48、24，受理实际同时受三者及租户共享任务预算限制，不是264。匿名JSON 4是总JSON 16的子预算，不额外扩充容量。普通GET不占common提交槽位；含JSON的后台写仅在读取正文时占浏览器JSON名额。

品牌4个名额在`BrandWorkBudget`所在JVM跨租户共享，事务结束释放；品牌公开PNG读取本身不占该预算，不应标成shared数据库配额。身份登录/邮箱预算参与所属工作事务，file任务预算参与独立任务提交；准入事务回滚和已提交任务的后续业务失败不是同一情况，取消/过期也不自动释放未清理文件字节。

新增或调整入口时先审查其实际调用的预算层与条件，再登记`EndpointBudgetCatalog`并用`SecurityEndpointInventoryTests`检查编译后方法。目录字段与端点引用一起进入`security-endpoint-policies.txt`；缺scope/存储、失效物理表、未使用的预算项、默认节点配置漂移、部署表换算漂移均触发门禁。显式审查使用`-Dsma.security.review-policy-baseline=true`，更新后必须关闭该标志复跑普通门禁，标准脚本不得默认开启。此为静态/本地契约验证，双实例真实累计及联合故障矩阵仍由P08/X5验收。

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
