# P07 认证安全扩展实施进度

依据：[P07 v2](proposals/P07-totp-and-security-password.md)。用户于 2026-11-11 授权开始实施。保留既有并行修改，按阶段验证；未验证不能标完成。

当前结论：S0～S5 本地实现与验收完成；2026-11-11 已按用户授权部署到本机当前8080服务，5174前端继续运行，账号安全菜单已同步。生产环境及生产灾备演练不在本次部署范围。

## 当前服务部署（2026-11-11 13:32，Asia/Singapore）

- 部署已通过26项菜单专项的 `backend/target/p07-menu/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar`，SHA-256 `45FFA15D8E6AFC4BB563B1135A2B3CC3E6030EF2D10BFBBC31436D01784442D5`。运行包及标准启动脚本使用的构建包均更新；后端PID由38460变为38620，前端PID38600未重启。
- 部署前发现当前库已是auth V4，且无已绑定TOTP/安全密码；新旧JAR迁移脚本逐项摘要完全一致，无新DDL。备份目录 `data/deployments/p07-menu-20261111-133135/` 保存旧包、172496字节数据库一致性导出、部署清单和启动日志，仅当前Windows账号可读。首次备份命令因参数传递报错，修正后成功完成才停止服务，没有使用失败备份继续部署。
- 创建独立随机加密键与pepper到受保护的 `data/keys/auth-security.keys`，通过子进程环境注入，未打印密钥、未使用测试密钥。原有OpenAPI主密钥不变；两份本机启动脚本支持在无显式环境配置时读取已有认证密钥文件，脚本AST检查通过，不自动生成/覆盖密钥。
- 实际验收：8080健康为UP；5174 `/auth/security` 返回200，代理安全设置接口返回预期401；`smoke-backend.ps1` 验证账号安全设置、安全密码、用户接口的中英文匿名401均通过。数据库菜单 `auth:account-security:view` 为MENU，路径 `/auth/security`，enabled=1、hidden=0、super_admin_default=1，父目录安全管理启用且超管默认授权。Flyway auth V0～V4均成功。
- 本轮没有使用用户密码或接管登录会话，因此未声称完成当前服务的已登录浏览器点击验收；超管授权逻辑已在前序集成测试覆盖。用户刷新/重新登录即可重载菜单。既有开发/验收记录中的“未部署”为当时状态，以本节为准。备份不可作为绑定后随意降级的依据，仍遵守P07回退规则。

## 锁定的实施契约

UI优化（2026-11-11，完成）：新版 `SecurityPage.vue` 调整为320px左侧状态栏＋自适应右侧操作区，容器达到840px时左右分栏、960px时二维码/验证码并排；窄屏将当前操作区置顶。增加图标、状态点、当前项目边框、倒计时标记与紧凑提示；脚本/composable及后端认证逻辑未改，保留原提交/取消与不确定结果处理。页面在当前5174开发服务热更新，无后端重启或用户凭据写入。

验证：`npm run verify` 完整通过（53项单测、类型、lint、i18n、基线与发布检查），日志 `backend/target/p07-security-ui-verify.log`。随后细调二维码分栏断点，`npm run build`＋`npm run release:check` 再次通过，日志 `p07-security-ui-build-final.log`。`npx playwright test e2e/security-layout.spec.ts --output=test-results/security-layout` 4/4通过，日志 `p07-security-ui-browser-final.log`；覆盖1440浅色中文、1280深色中文、768平板、390深色英文，验证分栏、无横向溢出、键盘到确认按钮、取消清除与安全密码表单。截图已查看，使用合成密钥和模拟API，不属于新增真实后端认证验收。精选截图在 `docs/frontend-next/images/security-layout-*.png`。

菜单权限补充（2026-11-11，已验证）：按用户要求新增 `auth:account-security:view`，路径 `/auth/security`，位于“安全管理 → 账号安全”；通过既有启动目录同步创建，默认启用、不隐藏、超管默认授权。普通角色可通过现有授权界面获得菜单；重复同步保留管理员修改及已有授权。右上角个人入口及本人安全 API 保持原有验证语义，菜单授权不等于操作他人凭据或免除 TOTP。无表结构/前端路由变更。

补充验证：`tools/dev/verify-backend.ps1 -Standard -MavenArgs @('-Dsma.verify.directory=target/p07-menu/target','-Dtest=AccountSecurityMenuTests,PermissionTests,ArchitectureRulesTests,ModularityTests,I18nBundlesTests','-Dsurefire.failIfNoSpecifiedTests=false') -LogPath 'E:/juiceProgram/spring Modulith/backend/target/p07-menu-verify.log'`，根 reactor 构建成功，26/26通过；验证前确认专用测试库无活动连接。覆盖超管默认菜单、普通角色显式授权、重复同步保留设置/授权ID及租户隔离。本轮为专项验证，未重复整个全量测试或真实浏览器验收；后端包在 `backend/target/p07-menu/target/`，未部署、未修改业务库。README及后端规则已同步。

- 登录密码新设值：8～12 位 ASCII 字母数字，至少一字母、一数字，弱词表/模式/用户名关联拒绝；旧密码验证不套新规则。安全操作密码为 6～8 位字母数字。双向不同采用 IAM 定义的同步策略及凭据写锁，不反向依赖 auth。
- 登录第二步只返回独立随机挑战；只有完成所有步骤才签发会话；绑定账号禁长期 remember，正式会话最多 8 小时。解锁仅验证登录密码且不延长绝对期限。
- TOTP：java-otp 0.4.0，RFC 6238 SHA1/6 位/30 秒/前后一步；标准向量覆盖 SHA1/256/512。挑战 3 分钟/5 次，绑定 10 分钟；动态码时间步跨操作原子消费。
- 安全密码：Spring Security Argon2id + Bouncy Castle 1.83，初始参数 19 MiB、2 次、并行度 1，独立 pepper；在测试中记录实际耗时。TOTP AES-GCM 与 pepper 分开密钥环，部署注入，不生成生产默认密钥。
- 共享数据库限流采用固定窗口、原子预算预占；失败不受业务事务回滚影响。TOTP/安全密码每账号 5 次/15 分钟，来源叠加限制；挑战生成也限流。安全依赖异常拒绝，不静默回退。
- 后端隔离输出 `target/p07/target`（保持 ArchUnit 可识别的 `target/test-classes` 末段）；专用可清空测试库 `springboot_modulith_admin_test` 串行使用，不与其他数据库验证并行。不启动/重启已有业务服务，不迁移业务数据库。完整标准验证安排在 S5。

## 分项状态

| 阶段 | 顺序和依赖 | 验证 | 状态 |
| --- | --- | --- | --- |
| S0 | 当前工作区、契约、库版本及验证环境 | 读规则/源码、依赖 API、隔离测试库/基线核验 | 完成 |
| S1 | S0 后：追加迁移、凭据密钥、挑战、共享限流 | 标准算法/加密测试、旧库升级、租户与并发限流 | 完成 |
| S2 | S1 后：TOTP 闭环、统一会话有效性 | 登录/绑定/换绑/解绑、拒绝路径、重放与并发、仅密码解锁 | 完成 |
| S3a | S2 后：登录密码与同步契约 | 所有写入口拒绝弱密码、历史兼容、模块依赖 | 完成 |
| S3b | S3a 后：安全密码及双向不同 | 设置/改密/重置、并发同值、锁定及公开 API | 完成 |
| S4 | S3b 后：两套前端适配 | 单测、构建、i18n、真实浏览器认证流程 | 完成 |
| S5 | S4 后：全量及文档 | 标准后端 reactor、前端 verify、兼容/恢复证据 | 完成（本地验收） |

## 验证证据

以下按执行先后保留失败、诊断与复验记录；条目中的“进行中/等待”为当时状态，当前状态以分项表和最新验收记录为准。

- S0：已确认分支 main、现有大量并行修改、JDK 21.0.13、MySQL 进程；java-otp 0.4.0 / BC 1.83 已在独立发布服务使用，本任务仅依赖库，不引用其内部源码。已检查 java-otp 实际公开 API。基线尚待执行。
- 测试环境：尝试创建 `sma_test_p07` 被现有账号权限拒绝，未修改任何已有 schema。读取可见库及连接后确认默认专用测试库存在且无活动连接，现有应用只连接业务库；改为串行使用默认测试库，不扩大数据库权限。基线命令为根 reactor 的 AuthFlowTests、ModularityTests、ArchitectureRulesTests，日志 `backend/target/p07-baseline.log`。
- 基线发现：`target/p07/test-classes` 不匹配 ArchUnit 默认测试排除路径，测试类被错当生产模块，产生 6 项架构失败。保持门禁不变，仅将输出目录改为 `target/p07/target`，独立重验不涉及数据库的架构测试；原认证测试继续运行，未因观察超时重启。
- S0 验收：AuthFlowTests 4/4 通过；修正输出路径后 ArchitectureRulesTests 16/16、ModularityTests 2/2 通过，命令使用根 reactor、`-pl backend -am -Dsma.verify.directory=target/p07/target -Dtest=ModularityTests,ArchitectureRulesTests -Dsurefire.failIfNoSpecifiedTests=false test`；日志 `backend/target/p07-architecture.log`。原总命令仍因输出路径误扫失败，不记为整体成功。
- S0 词表来源：SecLists `Passwords/Common-Credentials/10k-most-common.txt`，MIT License，源文件 SHA-256 `68782d6a4a19a4768d5f15dd66bd534e7a33055cc755411e33f16d18c50fdcce`，2026-11-11 获取到本地研究目录；后续随制品保留许可证与来源摘要，不在线上传用户密码。
- S1 验收：根 reactor `-pl backend -am -Dsma.verify.directory=target/p07/target -Dtest=AuthSecurityCryptoTests,AuthSecurityMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test` 6/6 通过；日志 `backend/target/p07-s1-crypto-migration.log`。V3 历史会话升级 V4 与空库迁移/Flyway 校验均通过；RFC 全向量、AAD 篡改/跨账号/跨用途拒绝及密钥轮换通过。
- S1 集成：相同入口 `-Dtest=AuthSecurityPersistenceTests,ArchitectureRulesTests,ModularityTests,I18nBundlesTests` 24/24 通过，日志 `backend/target/p07-s1-persistence.log`；12 并发只允许 5 次预算预占、外事务回滚不退预算、挑战租户/用途隔离和消费后拒绝通过。Argon2id 19 MiB/2 次/并行度 1，本机两次编码＋两次校验 399ms；不代表生产负载压测。字段映射与密钥格式见[运维文档](auth-security-operations.md)。
- S2 首轮：根 reactor 同隔离输出，`-Dtest=TotpAuthenticationTests,AuthFlowTests,AuthSecurityPersistenceTests,ArchitectureRulesTests,ModularityTests,I18nBundlesTests` 34/34 通过，日志 `backend/target/p07-s2-auth.log`。含 6 项 TOTP 流程、4 项原认证回归；绑定前 Token 失效、挑战拒绝、重放、并发单次兑换、改密失效、仅密码解锁不续期均通过。新增密钥清理及补充用例后在 `backend/target/p07-s2-final.log` 复验，未完成前 S2 保持进行中。
- S2 最终验收：`-Dtest=TotpAuthenticationTests,ArchitectureRulesTests,ModularityTests,I18nBundlesTests` 31/31 通过（10+16+2+3），`backend/target/p07-s2-final.log`；增加 HTTP 第二步/Cache-Control、换绑完成、换 IP/新挑战无法清空预算、过期待绑定密钥清理验证。仅后端测试环境，未部署、未完成前端浏览器验收。
- S3a 验收：根 reactor 同隔离输出，`-Dtest=LoginPasswordPolicyTests,LoginPasswordWriteTests,UserTests,AuthFlowTests,ArchitectureRulesTests,ModularityTests,I18nBundlesTests` 34/34 通过，日志 `backend/target/p07-s3a-password.log`。创建/初始化/修改/重置弱密码拒绝且旧哈希/版本不变、历史长特殊字符密码兼容、登录密码与已设置安全密码相同被拒绝。同步替换 12 个测试文件中不符合新设规则的原成功样例，负面断言保留，另建明确历史密码 fixture。
- S3a 初始化脚本：纯预检 helper 共 17 个接受/拒绝样例通过，启动脚本 AST 解析通过；未执行启动脚本、未重启服务。词表 v1 2082 条及 MIT 许可证已保存，派生摘要 `ddbe906c330547a70a0c72d8a2e137101ee524757027e406fe2fdb1d8a43a78e` 在服务启动时校验。
- S3b：设置/修改/强验证重置与 `SecurityPasswordVerification` 已编码，正在执行 `backend/target/p07-s3b-security-password.log` 对应的 SecurityPasswordTests、LoginPasswordWriteTests、TotpAuthenticationTests 及架构/模块/i18n 检查；结果未确认前不标完成。
- S3b 首轮结果：安全密码 6 项与其他检查通过，但 TOTP 回归 1 项在生成上一窗口码后跨越 30 秒边界，被服务端按设计拒绝，整体失败。将两组流程测试的验证码时钟固定到每个用例开始时刻，并相应使用固定时刻构造过期挑战；没有扩大生产窗口或放宽重放/过期断言。正在 `p07-s3b-final.log` 重跑相关两组流程及登录密码写入验证。
- S3b 追加审查：业务事务可能在认证撤销前建立 REPEATABLE_READ 快照。`SessionValidity` 改用独立 READ_COMMITTED 读取安全依据，`SecuritySessionGuard` 在写事务中锁定读取当前会话；新增保留旧会话、只推进账号凭据版本的回归，证明不能依赖异步清理或旧事务快照。该追加修改晚于 `p07-s3b-final.log` 对应编译，必须另外验证后才可验收 S3b。
- S3b 固定时钟复验：`p07-s3b-final.log` 19/19 通过（安全密码6、TOTP10、登录写策略3）；生产窗口未变化。当前追加快照防护在 `p07-s3b-snapshot.log` 验证，包含新增快照撤销用例、仅密码解锁原回归及架构/模块/i18n。S3b 尚待该结果。
- S3b 最终验收：根 reactor 同隔离输出，`-Dtest=SecurityPasswordTests,TotpAuthenticationTests,SessionManagementTests,ArchitectureRulesTests,ModularityTests,I18nBundlesTests` 39/39 通过，日志 `backend/target/p07-s3b-snapshot.log`。安全密码7、TOTP10、旧解锁1、架构16、模块2、语言包3；包含旧业务事务快照撤销、双密码并发同值拒绝、匿名写拒绝、仅密码解锁不续期。前端接入契约见[API 约定](auth-security-api.md)。全量 reactor 与浏览器验收仍待 S4/S5。

## 交付审计

- S4 两套认证 store、独立 TOTP 页、账号安全页/二维码、新密码规则、双语及旧密码输入兼容已完成静态与真实浏览器验收；next 并行 UI 修改保留。
- 旧版基线已逐项审阅并更新对应13项哈希和新增路由 inventory；后续只更新本任务有意改变且已核验的项。
- real E2E harness 已使用符合规则的随机管理员密码、独立随机认证密钥与专用端口；结果按端口隔离，避免覆盖并行工作的 test-results-real。临时数据库与服务仅由任务自身创建/清理，不能操作业务库。
- S5 已完成完整标准 reactor、两套前端验证、真实浏览器闭环及部署/用户手册和规则同步。密钥轮换、可重入重加密/离线恢复、失败审计、公开验证事务约定与拒绝路径的实际证据见下文。生产压测/外部渗透、实际备份工具的PITR与生产发布未执行；不称为已上线。

## S4 接入记录

- 两套前端已加入内存 MFA 挑战状态、独立 `/login/totp` 页面、右上角账号安全入口与 `/auth/security` 管理页。绑定二维码通过本地 qrcode 1.5.4 生成；支持设置/修改/强验证重置安全密码，写入超时不自动重试，需显式查询状态。密码新设校验与双语提示同步，旧登录/解锁输入保持兼容。
- 审阅旧版 13 个有意改变的既有文件后，仅更新对应 baseline 哈希，明细 `backend/target/p07-legacy-baseline-review.json`；新增两条路由由既有 inventory 工具生成，未用全量更新掩盖无关差异。
- 新版 `npm run verify` 通过，日志 `backend/target/p07-next-verify-final.log`；旧版标准 `pwsh -NoProfile -File tools/dev/verify-frontend.ps1` 通过，日志 `backend/target/p07-legacy-verify.log`。新增两套 MFA 状态测试和安全设置状态测试均通过，覆盖不保存挑战、取消后迟到响应、超时不重试及离开清除密钥。
- 本次新增依赖审计发现已有 Monaco 0.57.0 固定的 DOMPurify 3.4.15 存在 high/critical 公告；保留 Monaco 版本，通过明确 override 使用项目已有的 DOMPurify 3.4.17。修复后 `npm ls dompurify` 只有 3.4.17，审计 high/critical 均 0，剩余 14 项 moderate；未执行全树强制升级。审计 JSON `backend/target/p07-next-audit-fixed.json`，修复后完整新版 verify 已通过。
- 真实验收使用 5187（next）、5188（legacy）、8081（专用后端）、3307（独立 MySQL），不停止已有 5174/8080/3306 服务。隔离构建产物 `backend/target/p07/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar`，初始密码与认证密钥仅为临时随机测试凭据。
- 第一次独立 MySQL 初始化被原 runner 的 120 秒硬超时终止，已确认该进程退出；改成异步等待实际退出并持续观察日志，再创建新任务目录运行。当前真实验收日志 `backend/target/p07-real-security-retry.log`，等待初始化/后续浏览器结果，S4 未验收。
- 首轮真实浏览器已走通安全密码设置/修改、绑定、错误 TOTP 拒绝、二步登录、仅密码解锁、旧版二步登录和解绑，但合并场景最后第六次同账号密码登录触发既定 5 次/5 分钟预算，测试整体超时失败（专用库只读查询确认 PASSWORD 计数5、TOTP计数4）。没有放宽后端限流；调整测试用独立管理员身份覆盖旧版未绑定登录，解绑后验证退出，避免同一场景重复叠加第六次登录。
- 截图复核发现解锁后安全页未自动刷新、同账号主题/语言资料重载会误清空安全页。已增加锁屏清除敏感输入、解锁重新查询、按稳定账号标识判断身份变化；两套对应4项 composable 测试通过，并增加真实界面恢复及主题切换断言。首轮截图/失败记录保存 `backend/target/p07-real-first-observation/`，不能将该轮记为通过。所有该轮临时实例已退出，认证密钥已清理。
- S4 最终真实验收：`p07-real-all-final.log` 中4/4通过（认证安全、原后台综合、配置/锁屏、并行规则界面），共3.5分钟。独立目录 `frontend-next/test-results-real-5187` 的四张截图已逐张查看，涵盖新版桌面、暗色中英文窄屏及旧版窄屏；解锁后显示正常安全状态。仅任务创建的数据库/服务被清理。日志中的 ResizeObserver 提示来自现有管理布局，未造成该轮断言失败；不能据此宣称全站无控制台告警。
- 该轮还发现 `audit.statuses.MFA_FAILURE` 缺失翻译和 Vite 监听带端口测试产物导致 EBUSY。已补两套审计语言包的 MFA_FAILURE/MFA_REJECTED/RATE_LIMITED，逐项检查并更新对应2项旧版基线（累计15项）；Vite 忽略规则兼容带端口目录。交付前两套完整静态验证另记 `p07-next-verify-delivery.log` / `p07-legacy-verify-delivery.log`。

## S5 收尾记录

- 增加租户内每批100行的可重入 TOTP 重加密，保留旧键、AAD 和防重放时间步；新增持久化验证及退役旧键后的解密验证。登录限流及无效匿名 MFA 请求补不含凭据的失败审计。
- 增加离线恢复 SQL 与重复执行测试：撤销会话/挑战、清除待绑定材料、推进版本与时间步，保留已绑定凭据。运维规程要求核对备份后的安全变更并隔离至少15分钟；缺少可信恢复点时不能开放流量。当前只在专用测试库验证，未执行业务库恢复或线上部署。
- 恢复与算法专项已通过：根 reactor `-pl backend -am -Dsma.verify.directory=target/p07/target -Dtest=AuthSecurityCryptoTests,AuthSecurityMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test`，6/6，日志 `p07-s5-restore.log`。包含退役旧键后读取迁移密文、重复重加密不改新密文、离线撤销脚本两次执行仍保留绑定且旧会话/挑战为零。不是生产备份软件/PITR演练。
- 最终两套前端完整静态门禁通过：next 53项单测、旧版47项单测，类型/构建/i18n及新版发布检查通过；日志 `p07-next-verify-delivery.log`、`p07-legacy-verify-delivery.log`。新版入口148 KiB gzip，34个按需模块。未覆盖/替换已发布ZIP。
- 源码审查：安全设置控制器沿用操作审计（包含失败，不保存请求凭据）；登录失败新增匿名挑战拒绝和限流状态；SQL监控只统计语句标识/耗时，不采集参数。应用身份使用独立安全链，人类设置与公开安全密码验证均调用 `CurrentUser.require()`；REST和WebSocket共用会话有效性判断。恢复与回退边界已集中到运维文档。
- 标准全量验收通过：`pwsh -NoProfile -File tools/dev/verify-backend.ps1 -Standard -MavenArgs '-Dsma.verify.directory=target/p07/target' -LogPath 'E:/juiceProgram/spring Modulith/backend/target/p07-standard-verify.log'`，根 reactor 全部模块 BUILD SUCCESS，8分15秒。合计246项，245通过、0失败、1按环境条件跳过（独立发布服务 SecurityMysqlHttpsTests，不属于P07认证功能）；主应用199项全部通过。
- 全量命令执行期间最后补充了超管/半认证WebSocket票据断言、匿名失败审计使用当前portal，以及密钥ID大小写轮换回归，因此另以同根 reactor 执行 `-Dtest=AuthSecurityPersistenceTests,TotpAuthenticationTests,ArchitectureRulesTests,ModularityTests,I18nBundlesTests -Dsurefire.failIfNoSpecifiedTests=false verify`；日志 `p07-s5-final-security.log`，待结果确认后验收S5，不用前一次编译结果替代这些增量检查。
- S5 最终增量验收：上述 verify 已 BUILD SUCCESS，36/36通过（持久化4、TOTP11、架构16、模块2、语言包3），4分12秒。额外证明超管不能调用单密码签发入口、挑战不能换WebSocket票据、Key_A→key_a区分大小写重加密且防重放状态不变。隔离JAR已重打包到 `backend/target/p07/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar`，没有替换正在运行的服务。
- 收尾：P07文档相对链接检查与本任务改动的 `git diff --check` 通过；方案索引、README、开发/部署/用户手册及后端/两套前端规则已同步。仅已有业务端口8080/5174/3306仍在监听，任务端口8081/5187/5188/3307已退出；任务随机认证密钥已删除。保留现有并行工作，未创建提交或覆盖已发布版本。
