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
| `SMA_SECURITY_SOURCE_TRUSTED_PROXIES` | 请求来源观测的显式IP/CIDR列表，默认空；不扩展租户Host信任或改变现有认证计数。边界与maximum-hops/header-characters配置见[安全运行说明](security-defense-operations.md#请求来源观测s2a1) |
| `SMA_DEFENSE_COLLECTION_ENABLED` | 默认false；启用安全信号后台采集时，必须显式提供独立节点目录、目录外32字节key-file和稳定环境节点scope。容量/退避/指标与恢复约束见[安全运行说明](security-defense-operations.md#有界采集与节点故障缓冲s2c)，验证阶段见S2进度 |
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

P08已将WebSocket一次性票据改为共享MySQL原子消费，并完成M1两JVM登录、验证码/TOTP、锁屏/撤销、机器预算与重启保留会话验收；在线连接仍只属于接入节点，断线后要重新取票。Message持久提示、同机跨节点推送/文件读取和M3a节点定向已通过19组真实双JVM验证，显式Redis超时为1s。此证据不替代生产多主机挂载、200ms默认预算或负载均衡验收。完整multi启动仍受M3后续配置一致性、受控迁移、共享存储及无粘性LB门禁约束。实际阶段与边界见[P08进度](cache-governance-progress.md)及[运行说明](cache-governance-operations.md)，不代表现有运行进程已升级；四线整体交付另须总排期X5联合验收。

## P08 受控逐模块迁移与服务节点只校验

M3b1新增入口（验证状态见[P08进度](cache-governance-progress.md)）：使用本次构建的同一宿主JAR，在部署流水线中显式执行命令，数据库连接从受保护部署环境/配置读取，不把口令放在命令行。

```powershell
java -jar backend/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar --sma-migration=migrate
java -jar backend/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar --sma-migration=validate
# 迁移成功后，服务实例使用只有正常DML权限的账号启动：
$env:SMA_DEPLOYMENT_SCHEMA_MODE = 'validate'
java -jar backend/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar
```

命令在main进入业务Spring上下文前分流，隔离上下文只加载数据源与迁移操作；不扫描组件、不启动HTTP/调度器/业务初始化。成功完成并关闭连接池后进程正常退出，失败为非零退出。参数必须精确为migrate或validate且只能出现一次，非法命令不会回落为正常服务。它只执行制品内Flyway迁移，**不执行Java业务初始化**；管理员/权限目录及配置兼容初始化仍属于正常服务流程，不能声称迁移命令已经完成这些业务工作。

继续使用真实Modulith模块发现、`db/migration/__root`及`db/migration/<module>`，根历史为`flyway_schema_history`，模块历史为`flyway_schema_history_<module>`，基线版本仍为0。拒绝外部迁移路径、自定义历史表、第二个Flyway连接、替代schema和目标版本/乱序覆盖。迁移模式的DDL权限只授予部署作业；业务账号仍需原来的读写权限。保留MySQL DDL不可整体事务回滚的事实：一次执行中后续模块失败时，先核对已提交历史与备份，禁止自动repair、清库或盲目重发命令。

`SMA_DEPLOYMENT_SCHEMA_MODE` / `sma.deployment.schema-mode`重启生效，默认`migrate`保持旧单机兼容；`validate`不执行DDL或baseline。校验拒绝空库、未执行迁移、失败/缺失/未来版本、校验和不符和制品已删除模块的历史；关闭Flyway不能绕过所选校验。只有整个制品的迁移通过后才启动服务实例。新增模块/SQL需先用新制品执行部署作业，再启动同批实例；不支持未经验证的混合版本滚动。

本入口尚不等于生产集群就绪：`deployment-mode=multi`仍拒绝启动。配置/密钥一致性和双向存储已完成本机验收，接流、摘流/代理及实际无粘性LB按M3c/M3d的当前进度交付；首次号段升级仍须停旧写入器。不要仅凭validate通过认定旧JAR可以安全回退。

只校验节点拒绝全局spring.main.lazy-initialization=true，防止把版本检查推迟到接流后；受控入口拒绝Hikari catalog/schema/connection-init-sql覆盖，沿用宿主数据库身份边界。实际制品回归开关为SMA_CLUSTER_SCHEMA_MIGRATION=1，配合原SMA_CLUSTER_M2=1/SMA_CLUSTER_ROUTING=1；只操作runner创建的临时库/账号/进程，不用开发库。

部署流水线需按环境串行运行一个迁移作业，自动重试关闭。当前Flyway锁按模块生效，不是覆盖整次发布的分布式锁；本入口没有宣称允许两个不同制品并行迁移。命令失败后先读取历史及核对已提交DDL，再决定后续处理。实际服务账号测试撤销的是CREATE/ALTER/DROP/INDEX四项权限，不能据此扩大为所有数据库特权均已移除的证明。

P08部署证据使用实际加载的Auth加密/pepper环、请求HMAC环、租户凭据环及OpenAPI主密钥；相同key ID不代表相同材料，保留旧键和活动键均参与比较。OpenAPI证据检查缺文件时不创建任何目录或密钥，单机当前进程自行生成的钥不能直接获得集群资格。文件路径可因节点不同，但修改磁盘文件不等于运行对象已轮换；沿用重启及既有密钥退役流程。宿主SPI、12域白名单、持久租约资格及双JVM差异/恢复已通过本机验收，不把摘要齐备当作完整multi可用。协议与限制见[部署一致性设计](proposals/P08-deployment-evidence.md)。

持久证据使用既有nodes.expected排序后的首节点作为首次基线引导者，运维须先核对该节点配置；其他节点先启动只等待。基线在同一nodes.version内不可覆盖，即使全部节点离线也保留；配置/密钥变更需清单版本前进，并按原租约规则退出旧代次。证据资格绑定进程/epoch和挑战，最长15秒且不超过租约，普通心跳不自动续证。V14已通过保留升级及空库验证，之后只追加迁移；自动运行与接流分别由下面的evidence/traffic开关控制。

证据运行时开关SMA_DEPLOYMENT_EVIDENCE_ENABLED（sma.deployment.evidence.enabled）默认false、重启生效。显式启用要求nodes.enabled=true、至少两节点清单、schema-mode=validate及非lazy启动。运行时复用当前节点租约，单独零队列worker续报；本地成功观察最多10秒，收集超过5秒不提交迟到结果。健康贡献者名称deploymentEvidence，未匹配/失联/过期均DOWN，只显示状态；默认根health会汇聚该项。它不自动配置readiness分组或阻断直接业务HTTP，完整接流/摘流由M3c部署策略组合；在此之前不得据健康项上线生产multi。

隔离双JVM证据验收开关SMA_CLUSTER_EVIDENCE=1要求SMA_CLUSTER_SCHEMA_MIGRATION=1。runner显式将readinessState,deploymentEvidence纳入本次readiness组，原M2/节点选择分别用SMA_CLUSTER_M2=1及SMA_CLUSTER_ROUTING=1。测试生成独立租户凭据环、保留HMAC键与错误键，结束删除；只操作新库和受控子进程，不对当前服务换钥。八类错误配置只应使该节点的资格/readiness失败；这不是直接业务路由强制拦截或无粘性LB验收。

M3b2双JVM验收结果：34组通过，含显式readiness组合后八类错误配置503、健康peer原会话连续可用、缺OpenAPI文件不生成密钥及进程重启重新证明。该轮单主机、Redis超时1s，未替换用户服务；完整multi仍关闭，不能把测试readiness组合当作生产LB/可信代理/摘流已交付。证据记录见P08进度。

P08共享存储预检按完整资源集合处理：所有已登记根/别名必须显式shared，不能只声明legacy而遗漏managed别名。Common探针每资源仅首次写入，绑定manifest/node/process/epoch；写确认丢失后只核对原摘要，失踪探针不自动重建。路径可以在各节点不同，peer报价必须经持久租约信任后才交给I/O校验。已实现固定锚点、双向证书和健康运行时；单次publish/verify成功仍不能替代完整证书或生产multi验收。

V15复用group/lease表保存固定锚点与有界报告，不新增业务表或租户豁免。节点必须当前配置MATCH且租约有效才能注册/报告；首次或自身故障后恢复需要活peer对新generation的反向确认。健康节点持续读到自身和固定锚点时，peer离线不自动撤销已有证书；观察断档不能直接继承旧证书。清单换代仍须退出旧租约并更换存储锚点。该协议及运行时已通过本机独立JVM验收，生产共享挂载仍需单独验证。

共享存储运行时开关SMA_DEPLOYMENT_STORAGE_ENABLED（sma.deployment.storage.enabled）默认false，重启生效；开启须同时开启部署配置证据，否则装配失败。健康项名sharedStorage，只输出状态，部署readiness需显式组合它。独立零队列worker按5秒周期预算实际校验自身/固定锚点/受信peer，READY最多10秒且依赖当前配置MATCH/lease epoch。该运行时不单独开放multi或绕过最终LB/摘流策略。

两节点初始资格依赖互证，应同时启动后等待两端sharedStorage就绪，不能等待A就绪才启动B造成引导互等。真实文件/MySQL、独立JVM和故障矩阵证据见P08进度；它们不替代实际多主机挂载验收。

M3b本机最终矩阵已通过40组（当前制品41专项），其中readinessState,deploymentEvidence,sharedStorage在测试部署显式组合。共享存储初始需双方同时启动；错误/缺失managed根和探针丢失拒绝B而A继续原会话，恢复原字节/根后重新互证。此结论仅同机独立JVM与显式Redis1s，不替代生产NFS/SMB等多主机锁/挂载验证、最终LB摘流或写请求重试治理；multi仍保持关闭。复验开关SMA_CLUSTER_STORAGE=1需同时开启EVIDENCE/SCHEMA_MIGRATION/M2，日志与制品哈希见P08进度。

## P08 M3c1：实例接流门禁（已完成本地验收）

新增重启生效的`SMA_DEPLOYMENT_TRAFFIC_ENABLED` / `sma.deployment.traffic.enabled`，默认false。开启时要求至少两节点的真实租约清单、非lazy的schema validate、配置证据和共享存储运行时全部启用。以下配置只用于已满足这些前置条件的部署，不能单独据此开放`deployment-mode=multi`：

```yaml
sma:
  deployment:
    schema-mode: validate
    evidence:
      enabled: true
    storage:
      enabled: true
    traffic:
      enabled: true
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
      show-components: never
      group:
        readiness:
          include: readinessState,deploymentTraffic,deploymentEvidence,sharedStorage
        liveness:
          include: livenessState
```

readiness决定是否接收新流量；liveness只代表本进程生命周期健康，不依赖Redis、数据库或共享目录，防止共享故障触发所有节点反复重启。门禁自身健康项名为`deploymentTraffic`。必须在业务HTTP端口提供固定`/actuator/health/readiness`和`/actuator/health/liveness`；支持统一servlet context-path，但不支持另设management端口、改health/base路径或MVC servlet路径。拒绝额外/缺失健康分组、错误include/exclude、详情公开及DOWN/OUT_OF_SERVICE改映射为200。启动校验同时核对实际聚合器和HTTP映射，不能只写对配置字符串。

门禁在租户解析、认证、正文预算之前运行。配置/存储资格失效或进程未完成启动时，登录、验证码、WebSocket新握手、`/api`、`/app`、`/open`和其他新业务请求均返回统一503及`Cache-Control: no-store`；只有精确健康路径的GET/HEAD不受门禁拦截，尾部斜杠、子路径、POST/OPTIONS不继承豁免。每次仅读取现有有界本地观察，资格失效存在其原有最长10秒观察窗口，不宣称跨实例瞬时一致；不在HTTP线程探测DB/文件。

已准入请求继续原处理链，不在返回后撤销业务提交或自动重试。503不能证明先前超时的写入未提交，更不能释放UNKNOWN回执；客户端仍按原请求键只读核对。ContextClosed关闭接流不可逆，后续ACCEPTING事件不能重新开放；本项尚不包含部署主动摘流入口、既有WS排空、业务worker领取准入、实际进程宽限与worker停机顺序，这些属于M3c2。证据/租约基础线程在未就绪时继续运行以恢复资格，不能随业务准入一起关闭。

traffic开关进入`common.host-policy`证据。升级至新增证据字段的制品或切换开关会改变摘要：不能在旧清单基线上覆盖digest或忽略不匹配；先退出旧代次全部租约、推进nodes.version，再由指定首节点建立新基线，并重新进行共享存储互证。它目前不是无中断混合版本滚动协议，发布/回退限制不能省略。

验证入口为DeploymentTrafficTests、DeploymentTrafficHttpTests及模块/普通端点门禁；实际双JVM矩阵追加`SMA_CLUSTER_TRAFFIC=1`（要求STORAGE/EVIDENCE/SCHEMA_MIGRATION/M2），保持single模式。最新通过范围与日志以[P08进度](cache-governance-progress.md)为准，不能把注入状态的HTTP测试等同真实LB验收。

共享存储适配的只读verify使用共享读锁，发布/退役仍排他；生产挂载必须支持真实shared锁，不能以自动exclusive回退获得资格。此项已补独立子JVM持锁回归，不替代多主机挂载测试。门禁排查可临时启用`logging.level.com.sma.admin.common.internal.deployment.DeploymentTrafficFilter=DEBUG`，仅记录固定拒绝状态，不记录请求、身份、路径或密钥；生产默认不输出逐请求DEBUG。隔离runner失败时另外记录有界健康/证书状态，不自动重发业务写或保留明文凭据。

显式开发loopback租户引导现按原子占位＋当前锁定读处理多实例首次竞争，保留已有租户名称、状态、安全代次及禁用域名，不覆盖冲突或恢复软删除；这是正常服务启动DML，不并入受控迁移命令。生产域名仍须原验证流程。本地真实MySQL并发/旧快照验证已通过，整个双JVM接流矩阵仍以P08进度为准。

M3c1当前证据为同制品完整双JVM41组和启动后聚焦23组，两轮均通过并清理。拒绝验收必须记录已离开NOT_READY的WAITING_CONFIGURATION/WAITING_STORAGE，不能只看到启动期503就认定配置隔离有效。`SMA_CLUSTER_TRAFFIC_ONLY=1`只补这一专项，要求`SMA_CLUSTER_TRAFFIC_BASE_RESULT`指向同一JAR、已通过并清理的完整结果；runner检查引用并在新结果保存引用哈希，不替代全业务矩阵。实际结果、失败记录与制品哈希见P08进度；受控排空、代理和LB仍按后续子项验收。

M3c2a共享摘流/工作许可已通过本地验收：内部host控制和HTTP Gate使用同一不可逆状态；工作许可在启动就绪及资格有效时申请，最多1024个且无排队，拒绝不领取新任务。在途许可不因摘流或资格丢失自动清零，实际收尾后关闭；活动数为0也不证明HTTP/WS或持久队列已完成。本机文件控制、各worker接线和实际进程停止仍在[后续方案](proposals/P08-deployment-drain.md)，目前不能据该内部API进行生产滚动排空。

c2b1缓存调度已接入：调度排队不预占许可，执行时及每次领取前重新检查；当前已领取项可以完成，未领取项仍按持久队列/原路由截止处理，不因摘流改派原生旧进程任务或清除UNKNOWN。真实MySQL用例证明一次APPLIED后停止后续领取。其他后台消费者和实际关闭顺序尚未全部接入，因此这里的内部摘流状态仍不是可用于生产滚动发布的完整停机入口。

c2b2邮件调度已接入：目录发现、恢复及每次领取分别检查部署准入，已领取邮件可完成结果提交。SMTP超时/取消后，发送线程尚未退出则活动许可继续保留；数据库UNKNOWN可已提交但不能据此宣称物理排空。超时先于DATA则禁止迟到发送，可能已发送则保持UNKNOWN不自动重发。P08专用MySQL、Identity邮件回归和本机TLS断连通过，外部供应商送达不在该证据范围。调度开关和预算仍按[Mail手册](integration-services-developer-manual.md)配置，默认关闭；受控进程关闭及其他消费者仍待c2后续验收，不能据缓存/邮件局部通过开放multi。

c2b3安全与身份维护已接入：会话、nonce、Identity登录/邮箱/重新认证，以及Auth重加密按有界事务逐项申请许可；停流后当前事务可提交，下一事务停止。后台维护暂停不延长会话/挑战/授权有效期，访问与消费仍按原逻辑独立核验期限；恢复资格后继续发现未处理租户。真实MySQL摘流及原批次/锁/租户/身份协议通过，不新增部署配置。SessionEndedEvent等持久监听尚待后续准入验收，不能把发布事务完成或当前活动数0当作所有事件已消费；本机控制和实际停机仍按c2c执行。

c2b4持久监听准入已完成：专用deploymentEventExecutor固定4线程/128队列，拒绝时不在HTTP线程执行；七个生产监听在领域事务前检查资格，已准入许可覆盖框架完成记录事务。未准入记录保留FAILED，恢复资格后经事件管理原权限/租户与原ID重投；不会自动清理或批量重发失败事件。指定ID重投已修复较早失败记录阻塞，但管理查询/重投仍读取未完成集合，不宣称无限积压下的分页性能。事件执行器排队/拒绝后的技术收尾仍须c2c等待，DeploymentWork=0不能替代线程池/HTTP/WS停止检查。当前真实框架/MySQL、HTTP重投和原审计/Socket分轮验证见P08进度，multi仍关闭。

c2b5消息信号处理已接入：每条推送/会话重验前重新准入，摘流时已进入同步调用允许结束，下一条停止；未处理批次和数据库信号保持，资格恢复可重读，观察版本不等于客户端收到。过期清理独立准入，不扩大删除范围。真实MySQL信号保留及Tomcat/WebSocket通知/待办/私信/强退通过；连接ping、缓冲发送及1001部署关闭仍在后续WS子项，当前不能据工作计数0认定连接排空。

c2b6存储后台消费已接入：Outbox独立单worker、每轮最多8个已有资源，领域领取前申请许可，摘流拒绝下一次领取；许可覆盖同步消费者、物理库结果提交和池租约释放。UNKNOWN沿用原至少一次事件协议，摘流不清除或改写，消费者仍须幂等。定时闲置回收需准入，但已有会话close/release必须完成；StorageDrainWorker继续既有停写任务的技术确认，不启动业务或切库。新P08独立业务库验证UNKNOWN/DONE/PENDING及attempt1/1/0，默认宿主冒烟已通过；c2c仍须等业务与技术调度结束后销毁DB，multi未开放。

c2b7插件控制同步与后台任务已接入，不新增配置开关：独立30秒控制定时器不继承请求上下文，资格不足/摘流时lastSync为DEFERRED，不开始新周期；已准入周期允许按原签名/租约协议完成。invokeJob在解析业务对象前检查部署准入，拒绝不领取，已有UNKNOWN保持原工作/版本引用；已进入execute的调用可以完成，后续调用仍受原租约期限限制。销毁后不得重新初始化runtime。真实签名JAR、受控控制I/O、MySQL及默认宿主分轮通过，实际HTTPS控制服务与关闭顺序未在本轮验收；35秒scheduler等待上限和当前工作数0都不是物理排空证明，multi仍关闭。旧后台适配器的事务外host迁移见[插件接入手册](extension/backend-guide.md)。

c2b8 WebSocket采用独立sma-socket-lifecycle单worker，250ms调度、每轮最多128个连接轮转，正常ping间隔至少30秒且逐连接准入。摘流后拒绝新握手（消费票据之前）和新发送；已有缓冲发送真实线程结束后以1001关闭，不发session-ended、不注销共享会话。成功升级到连接建立之间仍计入pending handshake，连接等afterConnectionClosed后移除，即使回调先发生也保留未返回的I/O计数。关闭失败保留重试；销毁后晚到升级仍尝试1001，不能靠清空计数表示排空。

只读DeploymentConnections快照供后续受控停机协调，0不代表HTTP、所有worker或持久队列已结束。10000连接无阻塞轮转约20秒，实际I/O可更久，不能把64KiB缓冲/5秒发送观察值视为严格网络总时限。真实Tomcat/JDK客户端1001与MySQL原会话/新票据保留、原推送/强退及票据专项14项通过；前端完整verify318项包含重新取票/REST刷新。另一真实节点重连、关闭协调线程和整机自然退出仍在c2c，代理/LB/S6/X5未完成，multi保持关闭。

P08 c2b9子任务观察与入口清单按[活动台账](proposals/P08-deployment-activity-inventory.md)验收。新增DeploymentActivity仅只读输出组件queued/running，DeploymentTaskTracker不创建线程或授予准入；取消Future不清除已开始的真实任务。LocalTransferStorage现已保持超时后的文件线程计数至文件/锁关闭，拒绝未开始仍走原NotStarted，取消/未知仍为STORAGE，不删除原不可变对象或重发写入。本地文件锁与线程测试已通过，原符号链接测试受Windows权限跳过1项；真实HTTP排空/数据库收尾、Google/Console/见证等其余传输仍待验收，不能据此进行生产滚动停机。

c2b9b2-2 FileClusterStorage现在以file.shared-witness提供内层I/O的只读活动观察。探针超时/关闭只请求取消，已运行线程直到真正finally才释放计数；取消仍返回原unavailable，不伪造资格成功或重新创建未知/丢失探针。它属于基础协调，摘流时不申请新的业务许可；c2c须先结束外层SharedStorageRuntime，再确认内层I/O退出，最后释放共享存储资源。2线程零队列、默认2秒探针预算及原锁/摘要规则保持。46项单元/独立JVM文件锁与4项默认宿主/真实MySQL双runtime通过；阻塞I/O为受控夹具，生产NFS/SMB及整机滚动停机仍另验。

c2b9b3-1辅助传输新增DeploymentTransport契约，当前GoogleHttpTransport已实现：只接受host在停止新父请求/工作并等已准入父流程完成后发起关闭（父流程可能有多次传输）；stopAccepting拒绝新子任务但保留已接受任务，专属执行器terminated后调用HttpClient.shutdown。transportTerminated只有任务/执行器/JDK客户端都实际终止才true；Future取消和快照0都不足以证明。PreDestroy沿用强制取消兜底，未结束仍不得报告成功。

Google保持固定HTTPS端点、4线程零队列、12秒等待、64KiB响应及无跳转/Cookie；无新增用户配置、HTTP管理入口或自动重试兑换。真实回环HTTP请求完成/慢正文取消后断连与客户端终止、原身份MySQL/HTTP分轮通过，未调用真实Google账号。完整c2c需实际等待父请求/worker及各传输，当前不能据局部关闭契约进行生产滚动发布。

c2b9b3-2 ConsoleProxyService提供extension.console-http观察：完整forward计数覆盖机器凭据读取、发布会话交换和原代理动作，固定1024个本机在途、无队列；原请求治理仍是入口预算。host受控关闭拒绝新forward，已接受的两阶段流程保留，无在途后才关闭客户端；snapshot为0不代表sendAsync库资源已退出，transportTerminated另查JDK实际终止。每次HTTP原20秒请求/25秒等待、HTTPS/证书/权限/路径/凭据隔离保持，无新增用户配置或停机HTTP入口。

强制销毁阻止尚未通过本地send检查的后续阶段，但不能撤销已经提交或与关闭并发的远端动作；未知命令/分片/complete仍用原协议核对，不自动重发。本地HTTPS合成服务及宿主请求治理分轮通过，未操作用户发布服务；整机c2c仍须先等已准入父流程完成，再关闭各辅助传输。
