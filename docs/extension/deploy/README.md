# P05 运维装配与验收

这些是部署模板，不会自动安装服务、修改主机权限或重启现有应用。生产部署前完成本文实测项；源码、编译和fixture扫描测试不是生产扫描证据。

## 运行角色

- 业务宿主：持有 NODE 凭据及公钥；启用管理代理时另持有CONSOLE凭据（不能签名或替代因素验证），见 [统一入口部署](../unified-console-deployment.md)；无发布库/签名KEK/Docker权限。
- 发布安全服务：独立MySQL、KEK和制品目录，提供HTTPS；只对受控管理入口、节点和工作者开放。
- worker：独立主机/系统身份，执行审核过的固定摘要镜像。Docker权限通常等价于该机器管理员权限，不能与业务宿主/安全服务共享权限边界。
- auditor：**不同于worker的独立留存主机/账号**，只持有 AUDITOR 凭据、security 环境、审计目录。使用同一工具JAR的 audit-mirror 模式不意味着可以共用worker的机器身份、主机或磁盘。

## 预检

在项目根目录执行：

```powershell
pwsh -NoProfile -File tools/dev/check-extension-prerequisites.ps1
```

它只检查容器引擎与已预置固定摘要镜像，不下载/执行镜像，不触发构建或扫描。默认读 `SMA_WORKER_JAVA_IMAGE` 和 `SMA_WORKER_SCANNER_IMAGE`，写 `backend/target/extension-prerequisites.json`。任何缺失返回非零。本轮机器实测无Docker且没有固定镜像配置，预检失败是预期事实，不能改成空扫描通过。

## worker 调度

`sma-extension-worker.service/.timer` 每次运行一个任务，完成后15秒再领取；`sma-extension-scan-update.service/.timer` 周期更新漏洞库。配置文件参考 [后端手册](../backend-guide.md) worker 环境变量表；安装路径和UID/GID应按目标服务器替换。

worker和扫描库更新都持有 `SMA_WORKER_SCANNER_DB/sma-worker.lock`，任务执行期间保持锁，冲突返回失败，留待下次调度。该锁是同一共享目录的进程互斥，不宣称跨主机分布式锁。多个worker共享扫描库时会串行；要并发运行，给每个worker独立扫描库/工作目录/机器凭据，再由控制面配额限制并发。

初始化扫描库必须在发布前成功完成。超过24小时没有成功刷新即拒绝扫描/发布。库更新任务允许网络，源码编译/探测/扫描本身无网络。运维需给镜像缓存、扫描库和工作目录配置磁盘配额；代码的有界流和容器tmpfs不等于对Docker镜像缓存设置了配额。

## 独立审计留存

通过机器管理创建 kind=`AUDITOR`、environments=`["security"]`，用强化认证领取一次token。单独主机配置仅服务身份可读的 `/etc/sma-auditor/auditor.env`：

```text
SMA_AUDIT_SECURITY_URL=https://release.internal.example
SMA_AUDIT_TOKEN=<独立随机AUDITOR令牌>
SMA_AUDIT_DIRECTORY=/var/lib/sma-auditor
```

执行 `java -jar extension-worker.jar audit-mirror`，或安装本目录 auditor service/timer。每次读取已留存链、验证序号和SHA-256，携带原锚点向服务端继续拉取；整批验证后逐条CREATE_NEW/落盘，再确认锚点。网络超时或进程崩溃后按已有连续文件继续，不覆盖历史记录。源库倒退、已有锚点不一致或本地链篡改会失败。此模式不需要Docker/Java镜像/WORKER令牌。

“代码不覆盖文件”不等于WORM：生产应额外配置存储不可变保留、备份、目录ACL和独立监控，业务宿主/安全服务均不能写审计留存目录。该系统未宣称能抵抗同时掌控所有信任域的攻击者。镜像客户端启动时检查完整已有链，单目录上限100万条；到达上限前制定离线分段归档和锚点保管流程，不得直接删旧记录继续。

首次先建立auditor并完成一次同步，再配置安全服务：

```text
SMA_RELEASE_AUDIT_REQUIRED=true
SMA_RELEASE_AUDIT_MAX_LAG_SECONDS=120
SMA_RELEASE_AUDIT_MAX_PENDING=100
```

这些是启动配置。启用后，缺有效auditor确认、确认超过时限或未镜像事件超过上限时，新敏感命令、新发布准备和最终激活失败关闭；既有有效运行租约仍可续签。初次引导默认 `required=false`，**生产验收必须明确是否已开启**。若auditor故障导致冻结，优先恢复独立同步；不要在普通发布流程中关闭此门禁。

## 灰度守护

第一步灰度最多10%；更高比例或晋升要求当前候选完整健康分钟窗口。界面“设置灰度门禁”固定当前发布修订、最低调用数、最大错误率/慢调用率、授权截止时间及是否预授权回滚。万分比100为1%；慢调用定义为>=1秒；默认界面阈值为错误1%、慢调用5%、最低100次。

节点只上报宿主实际执行计数，不能用插件自报指标替代；新进程未覆盖完整分钟、存在丢弃计数、节点缺失/凭据被撤销、过期窗口或样本不足均不能晋升。配置有效期最长1小时，不自动晋升。越阈值且已预授权时只回退到本灰度修订的稳定向量，并生成新的递增修订，重新走所有节点准备和激活。原操作者登出/改权、策略变化或稳定制品失去资格会阻断自动处置。

计数来自已完成调用；报告时如果候选还有在途调用，该窗口保守标为不完整，避免卡住的调用尚未计数却使门禁通过。同一窗口报文首次形成后固定，重试不得重新写结果。持续繁忙的实例可能长期不能取得完整窗口，需要在受控流量下观察；不为自动晋升而放宽这一条件，也不强杀插件线程。

这不是跨节点原子网络切流：负载均衡流量池需要按真实节点修订就绪情况配置。节点报告来自经过认证的机器，已失陷节点可以谎报；因此仍需要外部监控和隔离，不声称指标可证明失陷机器可信。

## 恢复与留存

工作 UNKNOWN/RUNNING 只由业务稳定适配器从权威记录核验；适配器必须先对迟到执行fencing再返回终态。不删工作表、不强清引用、不换幂等键重做。离线孤儿引用默认持续保留，优先恢复原工作账本/节点对账；当前没有任意强制清引用的管理接口。

已发布制品按同环境的真实发布历史保留至少20版和30天（最后发布时刻）；未发布草稿不挤占历史配额。当前版本、待发布、持久引用和未过期租约另有保护。旧开发数据没有 publication-time 时失败关闭，需从可信发布审计恢复元数据，不能推测日期后自动删包。DELETE_PENDING 物理清理成功后才扣减配额。

备份需成套保存发布库、仓库和KEK，独立保存审计锚点与信任公钥。灾备先隔离流量、核对锚点和节点最高修订，再恢复扫描/签名/机器信任。单独恢复旧数据库不能让节点接受修订倒退；真实灾备/镜像签名密钥丢失演练仍是生产验收项。

## 可重复验证

- 后端：根 `tools/dev/verify-backend.ps1 -Standard`。并行开发使用独立 `SMA_TEST_DB_URL` 与 `-Dsma.verify.directory=target/<任务>/target`，路径必须保留末尾 `target/test-classes` 以便架构工具正确排除测试代码。可用 `-LogPath` 指定独立日志。
- 安全库与TLS：设置 `SMA_RELEASE_IT_DB_URL=jdbc:mysql://.../sma_test_release_<用途>`、用户名/密码，运行 `SecurityMysqlHttpsTests`。它只允许清理明确的该前缀测试库，创建临时fixture证书，实际验证TLS而不关闭证书检查。
- 前端：`npm run verify`；浏览器 `npx playwright test --config playwright.extension.config.ts`；后者是模拟发布API，截图与AA不代替真实服务、浏览器MFA或容器验收。
- 生产验收仍需：预置真实镜像/扫描库、隔离编译/扫描成功与失败路径、至少两个真实宿主的新旧版本/网络分区、反复装卸/资源趋势、磁盘耗尽、灾备/WORM和运维告警实测。未执行的项目见全栈进度，不把模板文件视作已部署。
