# 安全专项前端提交与查询治理

属于[安全专项](security-defense-progress.md)S1c，依赖S1b/B6完整验证通过。用户已批准实施，密码规则不变。C1～C4已完成本地验收；S1d隔离真实后端、外部服务与生产部署仍另行验收。

## 范围与路径

新版管理前端为主要交互交付工程。拟修改 `frontend-next/src/api/http.ts`、`src/composables/`、`src/lib/`、公共业务组件、对应 `src/features/` 与双语资源/测试。旧前端只维护独立客户端协议兼容；不跨工程导入源码，也不以客户端随机请求键声明防重复交互已经完成。

| 顺序 | 依赖 | 内容 | 验证 | 状态 |
| --- | --- | --- | --- | --- |
| C1 | B6 | API显式原意图键/响应策略/Retry-After与错误分类；本人作用域操作登记、同步互斥、慢响应、未知结果恢复基础 | 同步重复入口/取消确认/失败/超时/迟到响应/换身份/存储失败/重载原键单元与协议测试；typecheck/i18n | 完成（基础单元与类型验证；页面覆盖见C3/C4） |
| C2 | C1 | 查询300ms防抖与中文组合输入；读请求合并和取消/代次；EditorDialog、上传、确认及可复用行内/批量提交机制 | 主按钮/回车/程序触发共用门禁；不同对象不误锁；普通CRUD与上传真实浏览器 | 完成（公共机制与通用资源页；其余实际入口归C3） |
| C3 | C2 | Config/IAM/Message/File/OpenAPI等普通入口逐项覆盖；Rule、缓存、品牌、插件、工作台及认证原生协议适配 | 每个写入口登记，版本/领域键不丢失；普通回执不冒充业务DTO；原生任务及撤权/切身份/锁屏回归 | 完成（本地）；[入口清单](security-frontend-write-inventory.md)，不代替C4/S1d |
| C4 | C3 | 操作状态面板、页面离开/刷新恢复、同浏览器多标签协调、双语与键盘交互；独立旧客户端兼容 | 新版verify及独立旧版验证，按已审查变更核对基线；更新README与规则 | 完成（本地；含a跨标签、b面板恢复和c独立旧版兼容） |

S1d再执行隔离真实后端慢网连点20次、超时断连及双进程/跨标签专项；不将模拟浏览器当真实数据库或负载均衡证据。

### C3接入顺序

| 子项 | 依赖 | 实际入口 | 验证与状态 |
| --- | --- | --- | --- |
| C3a | C2 | Config字典/参数动作、偏好/显示设置；IAM资料、用户密码重置、角色删除及授权 | 完成（本地）；本人改密/锁屏按C3d认证协议处理 |
| C3b | C3a | Message公告/待办/私信/已读/批量、文件删除、开放应用管理及一次返回密钥 | 完成（本地；b1与b2证据见下） |
| C3c | C3b | Workspace原版本/便签键、Tenant品牌/域名任务、缓存/规则/插件原生协议；上传ACCEPTED显式恢复 | 完成（本地；c1工作台/c2品牌域名/c3缓存规则插件/c4通用上传），恢复边界见各项记录 |
| C3d | C3c | 认证/会话/锁屏与其余控制入口；写入口覆盖清单、当前权限下的迟到响应隔离 | d1/d2/d3本地完成；会话/监控/消息/动态适配器遗漏已补齐，下一C4 |

C3d2按验证拆a/b：a先完成本人凭据操作的共享门禁与账号安全页面（当前实施），b再接本人登录密码、锁屏/解锁及退出前确认。a的标记只保留schema、当前会话SHA-256作用域、随机操作键和UNKNOWN，不含token/密码/OTP/secret/请求正文；发送前写入sessionStorage，直接成功或明确拒绝才清除，普通settings GET不能清除，刷新/页面重建不解锁未知。新认证会话有新作用域，允许明确重新认证后的新意图，不宣称旧请求已取消。会话hash仅客户端隔离，不代替服务端认证；跨标签原子协调仍归C4。路径限新增前端凭据意图store、useAccountSecurity及相应测试/双语/规则；旧原生认证契约和后端密码规则保持。a先验拒绝/竞态/刷新与真实浏览器，再推进b。

C3d2b开始（a已通过33专项/7浏览器/完整verify198项）：本人改密复用凭据门禁，确认/随手栏保存前捕获输入和会话；LOCAL未知只按原键查权威回执，NOT_FOUND/普通资料GET不能释放，密码不重建。锁/解锁保留原生协议，先隐藏私有界面并清密；未确认的锁操作不得由普通/me读取或刷新取消，未知时需结束旧会话再重新登录，避免迟到旧unlock覆盖新lock。锁操作用独立单槽元数据，不与凭据写互相妨碍紧急锁屏；标记仍无密码/token。页头与锁屏退出共用一个确认前门禁，迟到确认不对新身份发送请求，网络退出失败仍完成原身份本地退出。路径限会话意图store复用、SecurityControls逻辑composable与组件/AdminLayout、相关测试/双语/文档；不改后端协议和密码规则，先专项再浏览器与完整verify。

C3d按依赖拆分（2026-11-12）：d1认证状态层与请求身份绑定（当前实施），d2账号安全/本人改密/锁屏和异步确认前的意图保护，d3会话撤销及剩余入口覆盖清单。每项通过后再推进，C4仍待C3整体完成。d1限api/http、auth store及现有登录/MFA/退出消费者：同步拒绝重复登录/OTP；请求发出前比对捕获的会话，不让旧退出请求使用新token；取消/身份变化使迟到响应和旧/auth/me失败失效；MFA传输结果未知时不复用挑战/OTP。认证材料只按原会话规则保存token，密码/OTP/挑战不进入操作注册表或持久化；不改后端密码/MFA策略、不使用LOCAL自动重试。先单元和HTTP适配器竞态测试，再相关浏览器与完整verify。当前auth相关源码无并行修改，保留其他线在建配置/密钥证据和UI改动。

C3b细分：b1先完成文件删除、开放应用普通管理及首次密钥返回；增加按对象独立的前端提交上下文，避免某一行处理时误锁其他行，同对象动作仍共用锁。验证并发不同对象、同对象编辑/轮换/删除、未知结果回查不伪造密钥后记录，再进入b2消息公告/待办/私信/已读及批量快照。两者通过后关闭C3b。

C3b1完成（2026-11-12）：文件删除、通用资源逐行删除和开放应用创建/编辑/开关/轮换/删除/关联/解除已接入。useSubmissionPool给不同对象独立上下文，仍用同一权威注册表阻止同对象冲突；页面退出立即撤销尚未发送的预留，不取消或重跑已发送工作。应用创建/轮换完成回执不会转为Credentials，未知查回只提示首次秘密不可再次读取，不自动轮换；凭据在锁屏/身份/权限变化后清除。验证20项提交/注册表单元通过，当前全部46文件146项单元通过，类型/测试类型/变更lint/双语通过。真实浏览器分轮5项通过：确认取消、不同文件同时删除、未知轮换只查原键、应用首次凭据原流程、创建完成回执不冒充秘密。继续b2。

C3b2前置发现：公告收件箱目前只有带写效果的`PUT /inbox/{id}/read`，普通用户没有按ID纯读内容接口。先在现有message模块补最小收件人授权GET并验证不改变readAt、跨用户/租户/portal及删除拒绝，再由前端在完成回执后重读，不能为获取原DTO再制造一个写意图。无新表或权限豁免。

C3b2只读接口验证中：新增`GET /api/message/notices/inbox/{id}`，复用显式收件人/tenant/portal/未删除检查，保留管理详情的原权限。快照`backend/target/security-c3b-message-ec24422406214dcb9ddd979a90c4c836`保存1407输入哈希；根reactor结构/端点审查23/23通过（`security-c3b-message-policy.log`）。逐行比较仅新增上述GET，已审查更新基线并保存其新hash；当前清单319路由/33精确例外。正在专用库普通基线及NoticeInboxLookupHttp/原Notice/Message治理整合（`security-c3b-message-integration.log`），无review更新标志；结果未出前不把新接口记为已验收。

C3b2只读前置完成：根reactor普通verify 33/33、0跳过、BUILD SUCCESS，16分43秒（`security-c3b-message-integration.log`）；2项新HTTP、4项Notice及4项Message治理回归均通过。新GET不需要管理权限/幂等键、标记QUERY且无写回执，重复纯读不改变readAt；跨用户/租户/portal、删除和撤销收件人拒绝。工作区这3个变更源码/测试与验收快照hash一致。开始消息前端接入，不据此声称消息页面已经完成。

C2验收（2026-11-12）：`npm run verify`完整通过，45文件143项单元、类型/测试类型、ESLint、双语、旧版基线、构建与预算通过，日志`.e2e-runtime/security-c2-verify.log`。新增查询/提交/原生恢复定向25项通过；最后权限同会话变更读取回归6/6、类型和专项lint通过，并重新build/release成功（入口156KiB gzip）。实际页面接入包括通用资源编辑/删除、文件上传/任务查询，列表取消和读后刷新。

`playwright.security.config.ts submission.spec.ts`5/5：20次按钮+表单提交只一次请求、慢提示、中文组合输入/取消尾部防抖、确认取消无写入、LOCAL未知保留草稿并查原键、上传未知只查自身任务/成功后ACL重读。均为真实浏览器加受控API夹具，不等于S1d真实数据库业务效果。首轮因隔离浏览器默认英文而中文定位超时，已只给该文件明确zh-CN后重验。原IAM/operations回归7/9首次通过，另2项被既有随手栏中部入口挡住；移入顶部工具栏后9/9通过，原鼠标断言保持。随手栏专项4/4含320px、浮窗/键盘/恢复通过。开发Vite仍报告既有ResizeObserver通知；与生产安全/视觉验收分别记录。C2不声称所有页面已治理、已支持跨标签原子锁或已经部署。

## 恢复约束

C3a完成（2026-11-12）：Parameters/Dictionary维护、Preferences/DisplaySettings同一本人范围、Profile、Users密码重置、Roles删除及两个授权入口已接入；角色编辑/授权/删除共用`iam.roles.<id>`，确认前锁定。资料/偏好和授权成功后重新读当前对象；通用回执不当DTO。未决表单冻结输入但允许核对，避免原操作查回成功后覆盖后来编辑；密码重置锁屏清密、规则不变、恢复记录无密码。DisplaySettings时区弹窗内可查原结果，UNKNOWN不会把取消入口一并锁死。

验证：变更文件ESLint、应用/测试类型检查通过；提交/注册表18项通过；`submission.spec.ts`合并分轮9项通过，含配置恢复20次触发、角色授权/删除互斥、原密码格式拒绝及未知密码无持久化、时区弹窗恢复；IAM/operations9/9通过。查询计时用例首轮7/8通过，唯一失败为locator自动等待超过300ms后再手动搜索，两次合法搜索被误算一次；改为同一浏览器任务中输入后明确点击，验证取消尚未触发计时器，定向通过。时区测试一次因取消/关闭同名定位严格模式失败，改为明确底部取消按钮后通过。未弱化产品断言，未执行真实用户写操作。继续C3b，消息/批量/密钥及原生协议覆盖仍未完成。

C1验收（2026-11-12）：新增`request-meta`、`operation-result`、`operation-registry`和可信owner适配；写入前持久UNKNOWN，存储失败不发送，恢复只查原键。LOCAL明确重试保留ticket/key与原请求闭包，NATIVE无默认成功推断或未知重试；3秒慢响应不伪造进度。查询也绑定完整会话与失效代次，锁屏后同账号返回不接受旧响应。API只保留有界协议头，完成回执不当业务DTO。

`npm run test:unit` 43文件133项通过；随后补充锁屏往返与类型收窄，定向协议/注册表18项通过，原客户端7项此前同轮通过。`npm run type-check`、`npm run type-check:tests`、新增/改动文件ESLint、`npm run i18n:check`通过。初次存储故障测试对happy-dom Storage原型spy未命中，已改显式Storage适配器故障注入；一次测试unknown类型收窄错误已修复。C1是基础库验收，页面/浏览器、跨标签原子锁和恢复面板仍分别属于C2～C4，不宣称已经全站防重复。

- 操作锁在处理函数入口、确认和异步校验之前同步取得。取消确认/纯本地校验失败尚未发送请求，可释放；不能仅依靠按钮下一帧disabled。
- 操作身份使用服务器验证的品牌入口tenant/portal和当前用户，加站点/API范围；会话令牌仅用于内存中的迟到响应判别，不写入操作记录。重载只保存原意图键、任务/回执ID、操作/对象范围与状态，不保存正文、密码、OTP、文件或密钥。
- 成功释放后允许新的独立意图；同对象冲突动作共用门禁，不把所有用户或所有对象锁成一个全站按钮。不能按正文哈希永久合并确实独立的创建。
- 网络超时、5xx、主动取消及页面关闭均不能证明后台回滚。UNKNOWN继续保留原意图；查询NOT_FOUND也不能独立证明旧请求永远不会到达。通用LOCAL明确安全的重试必须由用户触发并复用原键/原输入，不能自动换键。重载丢失原输入时只提供查询与核对，不从持久化恢复秘密。
- 通用409完成回执不包含业务DTO，不能强制转换成原创建结果使用ID；刷新真实资源/列表后提示已完成。FAILED_FINAL与明确未受理错误分别处理，不能把未知结果伪装为失败。
- file查file任务，ACCEPTED才允许按原键/文件明确恢复；品牌查operationId，缓存查原预览/任务，插件查原work。认证OTP/登录/会话及外部动作保留自己的一次性协议，不套通用自动重试。
- 同浏览器跨标签协调优先使用浏览器原子锁；存储通知只能同步状态，不能被宣传为分布式执行保证。服务端唯一键/版本/领域状态是最终保障。容量、存储失败及不支持的浏览器能力必须有明确状态，不能丢掉未知请求后假装READY。
- 立即显示处理状态，3秒后提示较慢；不伪造百分比。结果查询有退避、上限及隐藏页面降频，查询不会再次执行写操作。
- 前端搜索包含compositionstart/end，显式搜索立即执行并取消待触发计时器；不同参数晚到结果不能覆盖新结果。不复用陈旧权限/认证状态作为请求去重缓存。


C3b2完成（2026-11-12）：公告创建/编辑/回收/恢复/批量、待办创建/分配/编辑/状态/删除/批量、私信发送与各收件箱已读已接入。批量目标及动作在确认前冻结，注册表支持有界多对象集合和显式集合锁；互相重叠禁止并发，不同对象独立执行。重复打开未读项不会使首个合法详情响应失效；已读完成回执后走带授权GET重读。RichEditor显式setEditable、EntitySelect关闭已禁用弹层并使用中文组合输入防抖，AttachmentList禁用期间关闭选择器并拒绝迟到附件追加。

验证：应用/测试类型、变更ESLint、双语通过；全部46文件149项单元通过。submission浏览器15/15加新增私信1/1通过，覆盖未知正文冻结/无正文持久化、连点20次已读仅一写一读、确认期间目标变更仍只提交原集合、重叠对象互斥、不同对象独立、原密码规则。IAM/operations 9/9通过，原五种公告受众/附件/回收及四种待办/UTC/分配均保持。首次批量测试暴露1280×720消息中心工具区占比挤压表格，已限定该页嵌套工具区30%，真实鼠标复选框操作通过；测试定位另修正实际中文标签及确认时后台aria-hidden查询，无force click。Vite仍出现既有ResizeObserver通知。均为受控API浏览器，不等同真实数据库或生产部署证据。

C3c拆分：c1工作台标签/便签（LOCAL回执与原requestKey/version共同保留）；c2品牌/租户/域名（当前唯一实现与原生operationId）；c3缓存/规则/插件原生任务；c4文件ACCEPTED原输入恢复、明确上传上下文。各项验证、记录后再开始下一项，不把原生受理当执行完成。


C3c1完成（本地）：工作台标签/布局与便签接入LOCAL操作登记及原键查询；标签版本、便签requestKey/version均保留，不用请求头键替代领域键。便签保存期间继续编辑，下一个成功版本才保存新输入；UNKNOWN停止自动保存，手动核对也不得因NOT_FOUND/列表无对象而再创建。归档/回收在等待先前保存前同步占有状态转换门禁。已完成最小回执重新授权读对象；正文仍只在内存，恢复元数据不含草稿。

验证：工作台/对象池单元14/14，应用/测试类型及变更ESLint通过；shelf浏览器分轮4场景通过（标签、布局/320px/无障碍/锁屏、便签/清单/版本冲突/归档恢复、锁屏退出保留确认）。便签原503用例更新为先原键查询PENDING/FAILED_FINAL、明确失败后再由用户重试；没有把503当作已回滚。首轮便签场景遇到验证期间HMR导致身份过期提示，停止源码修改后单独重跑通过；保留该失败与重跑边界，不称首轮4/4。当前未执行真实后端工作台验收，归S1d/S6。


C3c2完成（本地）：品牌草稿LOCAL、发布/回退NATIVE与目录/域名LOCAL、DNS/TLS检查NATIVE分别接入；发布/检查operationId与同一次注册表原键绑定，先预留再确认。本地回执完成后授权重读，不能用“当前内容碰巧相同”替代原回执；文案区分原操作已核对与当前状态。原生回执验证对象/编号/状态，不把受理或NOT_OBSERVED当完成。关闭弹窗不删除未决登记；重开对象/品牌页面刷新仍可查原键。读取会话票据补令牌/权限判别，品牌仅确认tenant变化时清理，不因临时entry为空清空草稿。

验证：tenant单元8/8，应用/测试类型、变更ESLint和双语通过；tenant next品牌/目录浏览器10/10通过，包括发布连点20次只一确认/一次发布、刷新后NOT_OBSERVED保留同键、后续只查原回执、锁屏往返/退出晚响应隔离、Long版本、只读权限、英文/深色/390px、发布历史与回退。LOCAL夹具现在保存原键回执再模拟丢响应，不通过列表猜测完成。未修改后端租户实现、未触发真实DNS/TLS或生产品牌发布。继续c3a缓存，之后c3b规则/c3c插件，最后c4上传。


C3c3a缓存完成（本地）：缓存提交按原preview ID＋原键登记，QUEUED/RUNNING保留PENDING，PREVIEW/UNKNOWN继续核对；PARTIAL若含UNKNOWN子项不能释放。原生任务回执不与内部LOCAL入队回执混淆。刷新恢复只读原任务，取消请求单独登记以允许取消正在运行的任务；同任务取消去重，取消受理不称底层执行成功。策略保存/重置LOCAL保留原输入/version并在确认前预留，完成后授权重读；待执行同区域任务与策略动作共用目标范围。预览只生成不执行的冻结计划，单次同步门禁/固定选择，无法自行入队；未将未知预览创建当作清理已执行。

验证：缓存/注册表单元25/25，应用/测试类型与变更ESLint通过；原cache浏览器11/11及新增刷新/取消1/1通过。包含丢响应只读恢复、节点确认与在线状态分离、策略冲突草稿、原生UNKNOWN、英文/中文/明暗布局；刷新后原提交键仍在、只有一次submit，取消仍可用且只一次cancel，终态查询才释放原登记。未部署或执行真实用户缓存清理；Vite仍有既有ResizeObserver通知。继续c3b规则。

C3c3b前置缺口：模板发布NATIVE与批量迁移已有operationKey，但当前公开GET只支持资产版本/批次ID；首响应丢失时缺少按原键的纯查询。拟在现有RuleService/RuleBatchService及控制器补按原键查询、限定原操作者/tenant/portal/当前动作权限及对象范围，不新建表、不重放写操作；先验证HTTP/架构/普通端点基线再接前端。


C3c3b只读前置验证中：隔离快照`backend/target/security-c3c-rule-842e65932f1e452794181ff620d917e3`保存1466输入哈希；根reactor架构/模块/i18n/端点审查23/23通过（`security-c3c-rule-policy.log`）。逐行基线比较仅新增`GET /api/rule/templates/{id}/publications/{operationKey}`与`GET /api/rule/migration-batches/operations/{operationKey}`，均QUERY，已审查同步基线并保存审查hash。当前普通verify正在专用库运行RuleOperationLookupHttpTests及RulePlatformTests与结构门禁，无baseline更新标志；结果未出前不接规则前端。

本轮首次验证启动过早：快照复制命令返回仍在运行，而调用者未先等待完成，导致后续Maven落在工作区。已通过jcmd核对独立构建进程user.dir与review参数，仅停止该Maven PID 34636；日志停在后端编译，未执行测试/基线更新。保存`security-c3c-rule-wrong-root.log`，随后明确检查快照manifest存在并用Stop错误策略，重新在上述快照完成23项审查。该误启动不计成功证据，也未重启用户服务。


C3截至缓存子项的完整前端门禁：`npm run verify`通过，46文件153项单元、应用/测试类型、ESLint、双语、旧版基线、生产构建与发布检查均成功。日志`frontend-next/.e2e-runtime/security-c3-current-verify.log`；入口156KiB gzip、37延迟模块。该证据不覆盖其后规则/插件/上传及C4仍待实现部分。


C3c3b只读前置完成：普通根reactor verify 43/43、0跳过、BUILD SUCCESS，13分42秒（`security-c3c-rule-integration.log`）。新增2项HTTP证明原键查询纯读、原操作者/租户/portal/当前动作权限/数据范围拒绝，超管无原操作者豁免；18项RulePlatform原回归通过，结构门禁23项通过。工作区6个变更源/测试hash与验收快照一致。未部署后端；开始规则前端接入。


C3c3b规则前端完成（本地）：场景/模板/实例/测试用例、试算与执行、发布/停用/回滚/生命周期、升级草稿、批量迁移及维护均按实际LOCAL/QUERY/NATIVE分类接入。资产编辑/测试/发布共用对象范围，批量目标在确认前固定；cloneExact保持超安全整数和十进制原值，HTTP键不省略原生operationKey。最小完成回执不传给Asset/Execution消费者，创建丢回执不拼undefined路由。旧只读刷新不能清除未决登记；模板发布刷新后按原键恢复，批次RECOVERY_REQUIRED持续未知并展示逐项证据。

验证：新增2项HTTP＋18项原规则后端及结构门禁43项通过；前端规则9文件28项单元、应用/测试类型和变更ESLint通过。浏览器原18项分轮全部通过，新增原发布键刷新恢复及批次中断恢复2项通过；第一轮17/18，唯一失败暴露共享范围使4个子面板重复显示同一未知提示，集中主面板/弹窗恢复入口后release 4/4通过，operations 3/3通过。保留全部实际业务断言，没有以first定位掩盖重复提示。当前这些浏览器使用受控API夹具，不计真实数据库业务效果。

运行资源直接清理仍是原生同步节点动作，缺少持久完成回执；响应丢失后仅允许查看当前资源统计，不把统计变化当作原动作成功，不释放UNKNOWN或自动再清理。原生缓存任务的核对另走monitor。此保守边界需在最终运维说明中保留，不能宣传所有原生操作都能自动收敛。继续c3c插件，之后c4上传。


调度对齐：已同步development-schedule与security-defense-progress，C3c3c插件仍验证中。基点ab8f46a起按提交点直接验证，停止新增源码快照。后续每个子项同时更新三处状态；不把现有品牌交互证据当作P11/X4资产安全验收。


C3c3c插件前置（调度第1组）：命令/运行对账已具原键登记与浏览器连点/刷新证据，但上传完成丢响应仍缺只读状态。新增发布服务精确GET /uploads/{upload}，返回有界任务状态/过期时间/原文件摘要与制品ID，不返回session或凭据；只允许原发布操作者和原会话，且重验当前上传范围。宿主仅增此精确GET代理白名单。按当前提交点直接根reactor验证；首轮新测试变量与已有pending重名导致编译失败，已改为pendingUpload，正在复验，未标子项完成。


插件前置第二轮未通过：15项发布工作流通过，新增调用所在原工作流1项失败（局部变量替换误命中已有pending断言，当前源码已恢复原断言）；3项HTTP上下文因运行期间共用target/test-classes中的SecurityHttpTests.class暂时缺失失败，根reactor后端尚未执行。为按新调度直接提交点验证且满足输出隔离，在根pom添加与backend既有同义sma.verify.directory profile，扩展子模块继承各自独立输出。使用target/security-extension-lookup/target重新全轮验证，不启用基线更新、不创建快照。


插件上传状态前置完成：根reactor `-pl extension-security,backend -am verify -Dsma.verify.directory=target/security-extension-lookup/target`，ReleaseWorkflow/SecurityHttp/ConsoleProxy/ConsoleRequestScope/普通端点/架构/Modularity共45项全部通过、0跳过、BUILD SUCCESS，3分25秒，日志security-c3c-extension-isolated.log。当前提交点直接验证，根profile令扩展与backend均输出至各自独立目录；HTTP校验为本地H2/MockMvc及代理替身，不声称真实HTTPS或MySQL发布服务联调。继续前端插件上传恢复。

C3c3c插件恢复完成（本地，2026-11-12）：原命令登记保持到上传COMPLETE，ACCEPTED不提前解锁；刷新只查询原command与upload，明确续传前重新查询、核对原文件名称/大小/SHA-256，已完成不再发送分片或complete，不申请新grant或创建新command。原任务查询与续传在同页互斥；失效身份、存储故障及无权/过期保留未知，不持久化文件/OTP/正文。文件批量上限与ResourcePolicy的100项一致。运行/一次性凭据恢复边界仍保留原协议，不宣称自动恢复未知凭据。

验证：定向`npx vitest run src/features/extension/__tests__/client.test.ts src/lib/__tests__/operation-registry.test.ts` 33项；`npx playwright test --config playwright.security-extension.config.ts` 8/8（30.1秒），覆盖原命令刷新、两类丢响应、错文件零发送、20次续传点击一次发送、代理边界、双语/窄屏。日志`.e2e-runtime/security-extension-upload-focused.log`、`security-extension-upload-browser-final.log`。`npm run verify`最终通过：47文件171项、两类类型检查、ESLint、双语、旧版基线、路由清单、生产构建与发布预算，入口156KiB gzip/38延迟模块；日志`security-extension-upload-verify-final.log`。首轮verify被并行配置页面12处i18n文案错误阻断，安全线未覆盖其代码；并行修正后完整复跑通过。所有证据基于ab8f46a及当前工作区差异；不替代真实发布服务/HTTPS、多节点、C4或X5。调度及安全主进度同步，继续C3c4。


C3c4通用上传完成（本地，2026-11-12）：新增useFileUpload，沿用B5文件原生任务协议。原File/imageOnly/key仅在内存，ACCEPTED且resumable只允许用户明确续传且发送前重新查询；运行中/未知不重传，已完成通过当前ACL读文件。查询/续传与连续20次操作同页互斥。UploadButton要求显式scope，文件库、头像当前选择、品牌槽位/草稿version、每次打开的公告表单分别绑定；关闭重开表单、移除头像、切身份/锁屏不把旧结果附到新上下文。头像和公告上传期间禁止保存旧输入；品牌保留原受限图片校验，并补组件自身file语言包加载。

边界：刷新后元数据可恢复查询，但原文件已不在内存，因此不提供重建原上传请求；仍保留原文件的页面可按原任务继续。旧表单实例的记录仍保留供C4集中核对，不自动转挂到新表单。此项不替代P09新upload-tasks、C4跨标签原子锁、S1d真实后端或X4品牌公开资产验收。

验证：`npx vitest run src/composables/__tests__/file-upload.test.ts src/composables/__tests__/submission.test.ts src/lib/__tests__/operation-registry.test.ts`32项通过。`npm run verify`最终48文件178项通过，类型/测试类型/ESLint/i18n/旧版128文件哈希/30路由清单/生产构建/发布预算全部通过，156KiB gzip、38延迟模块；日志`.e2e-runtime/security-upload-context-verify-final.log`。

浏览器分轮覆盖25个场景：`npx playwright test --config playwright.security.config.ts submission.spec.ts`首轮18通过、1失败（新增测试的取消按钮同时匹配页脚与关闭图标）；限定dialog-footer后仍18通过、1失败（未处理未保存确认框）；补真实确认步骤后`-g 'late attachment'`1/1，再新增`-g 'previous avatar'`1/1，合计20个场景已通过。日志`security-upload-context-browser.log`、`security-upload-context-browser-final.log`、`security-upload-attachment-recheck.log`、`security-upload-avatar-browser.log`。品牌`npx playwright test --config playwright.tenant.config.ts management.spec.ts --project=next`4通过、1失败（复用上传组件未加载file语言包）；修正产品组件后`-g 'brand upload recovery'`1/1，合计5个场景通过。日志`security-upload-brand-browser-final.log`、`security-upload-brand-recheck.log`。更早一次CLI把测试文件误作project，未启动测试，不计成功。所有浏览器使用模拟API，非生产文件或扫描验收。C3c关闭，调度与安全主进度同步；下一项C3d认证/剩余入口，之后C4。

C3d1本地完成（2026-11-12）：auth store新增同步登录/OTP门禁与代次归属，reset后旧finally不能释放新登录；登录/MFA在权限读取前后核对token/代次，旧/auth/me失败也不能让路由清除新会话。退出按原token/代次唯一发起，迟到成功或失败返回false，两个现有退出消费者据此不导航新会话。HTTP的expectedIdentity是内存发送前校验，不是服务端权限或自定义头；匿名登录/OTP绑定null，阻止排队旧退出被拦截器换成新token发送。MFA未知网络/202/5xx结果丢弃挑战，不缓存/重放OTP。登录和MFA页面离开/入口刷新丢弃迟到结果并清密，正常MFA交接保留。

验证：security-c3d1-unit.log的auth-mfa/http共22项通过；type-check:tests通过。security-c3d1-browser-final.log的authentication四项＋protocol-compat一项共5/5通过（11.4秒），覆盖20次触发仅一个登录/OTP、真实页面MFA交接、未知OTP不得重放、离开登录页及权限读取期间迟到不恢复token/导航；后两项等待响应结束及网络空闲后断言。此前四项初轮也通过。完整npm run verify首次因并行frontend/AGENTS.md新添P10只读配置规则与旧哈希不符而退出；审阅该文档差异后只更新其基线哈希，未改旧端源码。security-c3d1-verify-recheck.log最终48文件191项单元、两类类型检查、ESLint、13模块双语、旧版128文件基线、30路由清单、生产构建与发布预算全通过（156KiB gzip/38延迟模块）。

LeiBox按Vue“认证表单 提交状态”与“提交等待”检索，仅得到筛选/AI等待动效，不匹配认证协议用途，沿用现有按钮/提示，未下载组件或新增依赖。所有浏览器均为API夹具，不替代S1d真实后端。C3d1完成并双写调度/安全主进度，README/前端规则/客户端契约同步；下一C3d2仍须解决确认前意图保护、锁屏/本人改密/账号安全恢复，C3d整体/C4/S1d未完成。

C3d2a本地完成（2026-11-12）：credential-intents按实际站点/portal/user/token组合SHA-256建立当前会话作用域，单个sessionStorage标记仅schema/scope/key/UNKNOWN，限制512字符及精确结构。共享begin在异步摘要准备前占位，sent在网络发送前持久化；不存认证材料，不重建请求，不把客户端标记当后端幂等证明。只有直接成功/当前明确拒绝清除，GET settings与刷新/重建保留未知；新认证会话另建作用域但不声称旧请求完成。useAccountSecurity绑定expectedIdentity和固定本次key，身份/锁屏变化清密并丢弃迟到QR；服务器已确认后本地二维码失败仍允许手工密钥流程，不回写UNKNOWN。

验证：security-c3d2a-unit.log首轮31项通过；补跨消费者共享与损坏标记后security-c3d2a-unit-final.log33/33通过。security-c3d2a-browser-final.log的账号安全2＋登录/MFA4＋协议1共7/7通过（23.7秒），明确网络丢失后只发一次、查询/刷新仍禁用、存储不含密码/token/OTP/secret，以及正常绑定本地QR显示；初轮2项也通过，其中第二项标题已修正为实际验证的正常QR场景，局部QR失败由单元测试覆盖。完整npm run verify的security-c3d2a-verify.log：48文件198项、两类类型检查、lint、13模块双语、128旧端基线、30路由、构建和发布预算全通过，156KiB gzip/38延迟模块。全是本地/API夹具证据，不是S1d真实后端或C4跨标签。README/前端规则/客户端契约、调度与安全主进度已同步，下一d2b。

C3d2b完成（本地）：useSecurityControls在异步退出/便签保存前固定本人密码输入及身份；LOCAL未知按原键只接受匹配操作的SUCCEEDED/FAILED_FINAL回执，NOT_FOUND/其他操作/普通资料读取不释放。密码清空不重放。独立锁意图先遮挡再发送；锁/解锁未知跨刷新保留，普通/me不能解除，需退出重登。正常错误密码允许重新输入；锁失败不能直接调用unlock绕过遮挡。统一页头/锁屏退出确认门禁，迟到确认不退出新身份，网络退出失败仍清本地原会话。无后端协议或密码规则变化。

验证日志在frontend-next/.e2e-runtime：security-c3d2b-unit.log定向44/44；最终security-c3d2b-final.log为npm run verify，49文件210项单元、baseline128文件、route30、两类类型、ESLint、i18n与构建发布全部通过（157KiB gzip/38 lazy modules/295 files hashed）。security-c3d2b-browser-final.log安全9/9；security-c3d2b-workflows.log布局4项通过，两个中文工作流因默认英文失败，明确配置zh-CN/Asia-Shanghai后，最终security-c3d2b-workflows-final.log两项通过；security-c3d2b-shelf-final.log便签锁屏退出1项通过。分轮证据不相加冒充独立全量；均为API夹具浏览器，S1d真实后端及C4跨标签尚未验证。5196/5198测试端口已退出。
C3d3拆分实施：d3a先接SessionsPage（useSessionRevocations、当前授权查询、按目标原生意图登记、双语和测试），d3b再审查剩余写入口覆盖清单。既有revoke是NATIVE且无原操作查询；分页列表缺失不释放UNKNOWN，前端只接受本次直接成功或明确未受理拒绝，未知不重发、不新增伪回执接口。不同目标可独立，确认前冻结ID及身份，当前会话不撤销；锁屏/权限/身份切换取消旧查询与未发确认，迟到响应不刷新新身份。刷新重建保留仅元数据登记，存储失败不发写；未知提示不能建议重新登录绕过目标门禁。d3a先单元→浏览器→完整verify，双写完成后再做d3b。该项只改前端，不改变后端会话协议或触发真实用户撤销。
C3d3a完成（本地）：SessionsPage接useSessionRevocations/useSubmissionPool；按目标预留NATIVE意图，确认前固定原ID/身份，当前会话禁止撤销。未知/冲突/服务端故障留原键；直接成功或本次明确拒绝可释放。列表缺失、刷新及页面重建不清UNKNOWN，当前无原操作回执不显示虚假的核对成功入口；另一个目标可独立操作。仅持久化操作/目标/原键元数据，不含token/IP/设备/用户名。查询带expectedIdentity与signal；锁屏/权限/身份变化清私有列表并取消旧读取/未发确认，迟到写结果不刷新新身份。

验证：security-c3d3a-unit.log定向34/34（会话11项＋对象池/注册表）；security-c3d3a-browser.log两项浏览器通过（20次点击一次确认/一次请求、未知刷新及列表缺失、元数据无隐私、取消与成功）；security-c3d3a-workflow.log原锁屏/改密/会话撤销工作流1/1；security-c3d3a-verify.log完整npm run verify为50文件221项通过，baseline128/route30、类型/测试类型/lint/i18n、构建发布检查通过。5196测试端口已退出，未操作真实用户会话，无后端改动。下一项d3b剩余写入口清单；C3整体/C4/S1d未完成。
C3d3b审查发现两处未接入的真实业务写：SqlPage统计重置与PublicationsPage事件重投仍只有内存busy。服务端两者明确NATIVE，无LOCAL原子回执；统计归零、事件从FAILED消失或进入COMPLETED都不是本次重置/重投的历史完成证明。拆d3b1先补这两页的原意图登记/刷新恢复及确认前保护，沿用现有组件；验证浏览器20次触发、取消、未知刷新/列表变化不重发、不同事件独立、原监控工作流及完整verify，再推进d3b2消息票据与动态适配器审查。此次不改后端协议、不接触并行P08缓存页面。
C3d3b1完成（本地）：SQL统计重置和事件重投使用useMonitorCommands/原生目标登记，确认前固定目标/身份。未知不因列表/统计读回释放，同事件禁重发、不同事件独立；刷新恢复仅元数据。成功只表示本次命令得到直接确认，事件重投不等于监听器执行完成。页面查询改为可取消/身份绑定，权限/锁屏/身份变化清列表；关闭页面后确认不能发出旧命令。服务端仍NATIVE，不新增回执、不修改P08节点路由或缓存页面。

验证：security-c3d3b1-browser.log 4/4（SQL/事件各20次触发、未知跨刷新、状态变COMPLETED仍未知、不同事件独立、取消、离页迟到确认无写）；security-c3d3b1-workflow.log原operations SQL/事件/被引用文件场景1/1；security-c3d3b1-verify.log完整verify50文件221项、类型/测试类型/lint/i18n/baseline128/route30、构建发布通过（157KiB gzip/38 lazy modules/296 files）。实际命令为playwright.security.config.ts monitor-commands.spec.ts、playwright.security-controls.config.ts operations.spec.ts -g 'SQL reset'、npm run verify。均本地API夹具，当前状态不能作为历史操作回执；下一d3b2消息票据与动态适配器审查。
C3d3b2开始：消息socket-ticket是短期一次消费握手票据，重连属于协议恢复而非重复业务提交，不持久化ticket或套LOCAL回执。当前messages仅以userId比较，发现同一用户换token后旧票据/旧socket事件仍可能被接受；SESSION_INVALID异步/me返回也可能停止新连接。先补完整会话作用域/发送前expectedIdentity、同步身份/锁屏失效、迟到事件隔离和清理，验证消息单元、锁屏/退出浏览器及完整verify；之后继续动态调用清单审查，不能因已有id比较而标覆盖完成。限messages store/测试，不修改并行后端Socket票据共享预算。
C3d3b2完成（本地）：messages的summary/一次性socket-ticket在发送前绑定原token；完整会话作用域含token、用户、portal、锁屏和权限，变化同步关闭旧连接/清计数，迟到票据和旧强制下线事件不能进入或结束新会话。同一userId换token仍失效；SESSION_INVALID等待auth.load后再次验证原连接归属，旧失败不停止新连接。重连保留短期一次票据协议与退避，连接同步门禁，存储事件和store销毁清理；不将ticket保存或套LOCAL。

验证：security-c3d3b2-unit.log消息/auth-mfa/http共33/33；新增同账号换token旧握手、旧强制退出、迟到/me不关新连接、锁屏计数/旧summary、20次连接仅一票据。security-c3d3b2-browser.log锁屏/改密/会话撤销及423两项通过；security-c3d3b2-verify.log完整verify50文件226项、两类类型/lint/i18n/baseline128/route30及构建发布通过（157KiB gzip/38 lazy modules/296 files）。均本地协议/夹具验证，不代表真实WebSocket集群联调。下一项d3b3动态适配器与完整入口清单。
C3d3b3发送边界审查：普通createApi调用、ruleWrite原始Axios与cache submit仍可能在进入HTTP适配器前的微任务中使用后来登录的token；原意图注册表只在执行入口/回包检查，不能覆盖该间隙。补统一本地bindRequestIdentity：默认在API调用时捕获token，显式null/原token仍保留；身份仅客户端配置不进入请求头/正文。规则精确JSON适配显式传递，缓存submit复用同一post（保留原键/无定向语义），发布代理已直接绑定Authorization不用改。先HTTP排队竞态/规则精确格式/缓存协议定向测试，再相关浏览器及完整verify；不改后端、不更改并行节点路由契约。此项完成后再关闭入口清单审查。
C3d3b3完成（本地）：createApi默认在调用时捕获身份，bindRequestIdentity将其置于仅客户端Axios配置；显式原token/匿名null优先，微任务排队后身份改变则在适配器前拒绝。ruleWrite精确JSON适配使用相同绑定，缓存submit复用post保留原键与持久任务无定向语义；未更改P08节点/进程头。发布代理原有显式Authorization保持。此保护只限制发送身份，不把随机键等同业务幂等，UNKNOWN恢复仍依原协议。

验证：security-c3d3b3-unit.log 33/33，覆盖GET/POST/PUT/PATCH/DELETE默认身份排队、匿名变登录拒发、rule/cache原始适配器竞态、精确十进制往返/原键/回执元数据、缓存提交无节点头；security-c3d3b3-browser.log认证/协议5项，security-c3d3b3-cache.log定向目录/原键丢响应/刷新取消3项，security-c3d3b3-rule.log authoring11项均通过。security-c3d3b3-verify.log完整verify51文件236项、baseline128/route30、类型/测试类型/lint/i18n/构建发布通过（157KiB gzip/38 lazy modules/296 files）。动态调用审查已确认rule validate/simulations/upgrade-preview在后端是QUERY，不产生持久执行记录；与execution/run等原生写区分。下一项d3b4整理当前完整入口清单，C4/S1d仍待。

C3d3b4及C3整体本地完成：新增security-frontend-write-inventory.md，逐组登记生产事件入口/公共处理器/特殊适配器/协议与实际证据，明确只读POST、预览、一次票据和原生未知边界。扫描普通API、原始Axios、动态rule/release/method调用及其他传输后，本轮发现的会话/监控/消息/发送竞态均已分别验证关闭；未把并行租户S5/缓存M3的整体阶段代为完成。当前333个生产TS/Vue文件的SHA-256记录在.e2e-runtime/security-c3d3b4-audit.json；文档本轮137个本地链接无缺失，定向diff --check通过。无新产品源码变更，不重复运行已通过的236项全门禁；C4操作面板/跨标签与S1d真实后端下一阶段独立实施。
C4开始，按依赖拆分（2026-11-12）：C4a1先实现浏览器Web Locks资源锁原语并验收；C4a2将其接入OperationRegistry/useSubmission，确认前跨标签准入、锁内重新读持久记录，发送/查询/显式续传统一协调与storage事件同步；C4a3再接凭据/锁屏/认证原生入口，C4b统一操作面板/离开提示/原协议恢复及双语键盘，C4c旧客户端独立兼容与整体验收。每项通过并双写后再下一项，不能以原语单测宣布全站跨标签完成。

C4a设计边界：同origin/后端范围/tenant/portal/user共用锁域，token不进锁名或记录；每个具体资源用独占锁，祖先集合用共享锁，集合操作对对应命名空间独占；多目标排序并一次性尝试，冲突立即返回busy、不排队自动执行。取消未发送确认只释放临时锁，崩溃由浏览器释放；UNKNOWN在发网前已持久化，锁释放/TTL/页面关闭不能清除。查询与续传也取得同组锁，避免在途命令与恢复交错。缺少Web Locks或存储故障明确拒发，不用localStorage通知冒充原子互斥；不支持时显示能力不可用。所有浏览器保证只限同浏览器origin，最终授权/幂等仍在服务端。

拟改现有frontend-next/src/lib与composables/store、受影响公共组件/管理壳、双语及专项测试；不改目录归属、后端协议、并行P08/租户代码。当前C3指纹与236项全门禁是前置，C4a每项有独立单元及同浏览器双页面验证后再集成全门禁。
C4a1完成（本地原语）：BrowserOperationCoordinator对精确资源独占、祖先命名空间共享、集合命名空间独占；按确定顺序一次性尝试多锁，冲突立即busy并释放已取得部分，不排队自动执行业务。不同资源/owner可并行；页面关闭由浏览器释放，原语没有清UNKNOWN或租约超时接管功能。缺失/拒绝能力失败关闭，资源集合/锁数有界。token不进入锁名，锁域由调用者传入已验证站点/API/tenant/portal/user。

验证：security-c4a1-unit.log 5/5，security-c4a1-browser.log真实同context双页面2/2（重叠/集合/不同目标/tenant、关页自动释放、无残留锁或排队），security-c4a1-types.log测试/引用源码类型通过，security-c4a1-lint.log定向ESLint通过；5196已退出。原语尚未挂到生产注册表，不宣称已全站跨标签防重。下一C4a2接入注册表、锁内读存储和协调恢复；凭据/认证另归a3。

C4a2完成（本地）：生产useOperations为OperationRegistry提供Web Locks协调器，useSubmission先同步reserve，再异步admit，成功前不确认/校验/发送；原键资源锁覆盖确认、写入/查询/显式续传。持久UNKNOWN发送前写入，短暂ledger锁只包同步存储刷新/容量检查/写标记，不包确认或网络；并发不同对象不争用长锁，最后一名额不被双页同时越过。每次准入重新读取并校验存储；storage通知仅触发刷新，不作锁证据。直接终态或权威查询清记录可同步到其他标签；NOT_FOUND/关页/锁释放不能清UNKNOWN。会话切换与取消释放未发送锁，已发送保留恢复记录；缺Web Locks或存储失败明确拒发。

异步准入前已固定普通表单输入、用户密码、偏好、私信/公告/待办、工作台原版本；资源编辑器若输入在等待锁时改变则取消原校验，不把新输入套旧意图。参数/字典维护及显示偏好初始化绑定操作范围，已打开的其他标签也能显示待核对状态。新增9项注册表协调反例覆盖同目标/独立目标、容量竞争、未知跨页核对、续传/查询互斥、能力/存储故障、身份切换（含落盘后发网前）、终态清理失败。

验证：security-c4a2-unit.log初轮39/39（后补两项由最终全量覆盖）；security-c4a2-final.log最终npm run verify，53文件250项、baseline128/route30、两类类型、ESLint/i18n及构建发布通过（157KiB gzip/38 lazy modules/296 files）。security-c4a2-browser.log首轮24项中23通过，上传续传旧断言把查询按钮隐藏当作请求完成；改为等待实际writes=2后，原字节/键/查询次数断言保持，重验通过。新增peer LOCAL核对首轮失败暴露参数页未初始bind，修复后security-c4a2-peer-final.log1/1及security-c4a2-final-browser.log4/4通过（peer核对、上传、参数确认、时区恢复）。其他已通过23项不重复跑；完整coverage为coordination5场景及submission20场景分轮通过，不能称首轮全绿。

扩展回归：security-c4a2-shelf.log4/4、security-c4a2-rule.log operations/releases7/7、security-c4a2-cache.log原键丢响应/刷新取消2/2、security-c4a2-extension.log原命令/宿主核对2/2。均真实浏览器/API夹具；测试5187/5194/5196/5198已退出，无用户服务重启或真实数据写。原语/注册表已生产接入，凭据/锁屏的sessionStorage仍待C4a3，操作面板/离开提示/旧版独立兼容仍待b/c；S1c/S1d不提前完成。
C4a3拆a/b/c：a先将本人凭据意图跨标签共享（会话scope SHA-256，原键/UNKNOWN元数据；Web Locks覆盖提交与LOCAL回执核对），b再做会话锁/解锁已确认状态的跨标签遮挡与恢复，c最后登录/MFA/退出原生协调。a不改变锁屏存储或登录协议，避免用凭据门禁阻塞紧急锁屏。新共享凭据标记按scope分键、数量有界，不存token/正文/密码/OTP/QR；旧sessionStorage待确认标记只在对应scope迁移，冲突保守拒发，不能通过升级丢失UNKNOWN。不同新认证会话维持既定新scope语义，不宣称旧操作取消。读取settings不释放，原键核对也必须取得同组锁；缺能力/存储失败明确提示。a验收共享多store/双页提交竞争、未知跨刷新/关页、原键只读终态、旧标记迁移/冲突、能力/存储故障、身份切换与秘密不落盘，之后完整verify和原凭据/锁屏工作流。

C4a3a完成（本地）：本人凭据和本人改密共用新shared-credential-intents，Web Locks覆盖确认/提交/原键查询，摘要scope分键的共享UNKNOWN发送前持久化；不会共享密码/OTP/二维码/token。短全局存储事务保护256条容量，旧v1匹配scope时先落新标记后删旧记录，冲突/损坏失败关闭，迁移遇忙可在锁释放后继续显式原键核对。同一会话另一标签收到UNKNOWN会清掉旧凭据表单并禁止重发；关闭发送页/刷新/settings GET均不解除。新认证会话保留既定新scope语义，原未知标记不被覆盖。锁屏门禁仍独立；本轮不改登录/MFA/锁屏协议。

验证：最终security-c4a3a-complete.log完整npm run verify，54文件261项、baseline128/route30、两类类型、ESLint/i18n、构建发布通过（157KiB gzip/38 lazy modules/296 files）。新增shared-credential-intents共11项，另account-security/security-controls23项由最终全量覆盖。security-c4a3a-browser.log账号安全4＋双标签/能力3共7/7；迟到授锁修正后security-c4a3a-final-browser.log3/3；原锁屏/改密/退出及423工作流security-c4a3a-workflow.log2/2通过。最后只增加迁移遇忙可恢复的分支与对应单元，完整261项覆盖；正常双页浏览器未受该分支影响。5196已退出，无真实用户凭据写或服务重启。

失败与修复：初轮锁屏临时重置scope导致UNKNOWN短暂消失，改为保留已知scope/状态并失效票据；新增存储故障夹具的happy-dom spy未由全局restore恢复，改显式finally mockRestore后反例通过；最后补等待授锁期间锁屏/销毁立即释放迟到锁和迁移遇忙后恢复两项。未放宽密码策略/存储拒发/秘密不落盘断言。下一项C4a3b跨标签锁屏与已确认解锁状态，再a3c登录/MFA/退出；C4整体及S1d尚未完成。
C4a3b开始：新增shared-session-lock-intents，仍与凭据门禁独立；同会话摘要scope＋Web Locks串行LOCK/UNLOCK，发送前写UNKNOWN，直接成功才写LOCKED/UNLOCKED终态，明确错误密码回到LOCKED。锁失败/任何未知保留UNKNOWN，普通/me不能清除。终态仅元数据（schema/scope/key/action/state），每条≤512字符、最多256scope；不存password/token。原v1未知标记匹配scope时迁移为UNKNOWN，不重放；冲突/存储/能力失败关闭。

跨标签显示：UNKNOWN/LOCKED同步遮挡、清密码；首次加载在摘要核对之前保守遮挡已有锁标记。活跃标签观察新的UNLOCKED后必须用原身份读取/me，且共享原key仍为确认UNLOCKED、无新的本地锁/退出意图，才能解除本地遮挡。启动时历史UNLOCKED不覆盖服务器已锁状态；读回失败/变新锁仍遮挡，旧回调不能影响新身份。本地锁请求在等待授锁前已遮挡；与另一页解锁竞争不能被迟到解锁解除。缺少可保存标记时本地退出，紧急退出继续可用。验证多store＋真实双页锁/解锁/错误密码/未知刷新/旧读回/授锁后失效、旧标记迁移、能力/容量/存储失败，再原账号/锁屏/便签回归与完整verify；之后才推进a3c认证。

C4a3b完成（本地）：shared-session-lock-intents独立共享scope/Web Locks串行LOCK/UNLOCK；schema2仅scope/key/action/state元数据，UNKNOWN发网前持久，成功终态LOCKED/UNLOCKED保留用于同源标签协调。错误密码明确拒绝恢复LOCKED；锁失败/未知保留UNKNOWN，无重发或普通/me解锁。旧v1匹配迁移，损坏/冲突/容量/存储故障失败关闭。标签初始校验前遮挡已有锁标记；活跃标签观察新确认UNLOCKED后按原身份GET /auth/me，要求明确locked=false、用户/portal匹配、共享原key未变化且无新本地锁/退出，才恢复界面。历史UNLOCKED不覆盖启动时服务器已锁；旧读回不能撤销新锁。本地锁先遮挡，无法记录则退出；与原凭据门禁仍独立。

验证：security-c4a3b-unit-recheck.log4文件46项通过，之后新增缺失锁字段/服务器仍锁/身份不匹配4项与落盘后换身份1项，由最终security-c4a3b-complete.log覆盖。最终npm run verify55文件278项、baseline128/route30、两类类型、ESLint/i18n和构建发布通过（157KiB gzip/38 lazy modules/296 files）。新增shared-session-lock-intents8项涵盖跨store竞争、未知重建、错误密码/锁拒绝、历史终态、旧标记冲突、缺标记/能力、终态写失败/容量、迟到授锁；SecurityControls现21项覆盖界面与读回竞态。

真实浏览器/API夹具：security-c4a3b-browser.log账号安全4＋锁双页4共8/8；最后身份边界修正后security-c4a3b-final-browser.log双页4/4复验；security-c4a3b-workflow.log原锁/改密/撤销及423工作流2/2；security-c4a3b-shelf.log未保存便签锁屏退出1/1。均通过，无本轮失败重试；多轮有重叠不相加冒充独立总数。测试5196/5198已退出，未操作真实用户密码/锁状态，不改后端unlock已未锁时直接返回的协议。下一a3c登录/MFA/退出跨标签，再C4b面板/C4c兼容；S1d仍独立验收。
C4a3c拆分：c1先修令牌存储归属，c2再接登录/MFA跨标签准入与认证结果/共享身份变更失效，c3完成退出原会话协调和页面生命周期回归。当前token.ts的clearToken无条件删session/local，存在本标签会话退出误删其他共享会话以及删除后回落到另一共享身份的问题。c1按有效token条件删除；明确退出后本标签标记detached，不再自动回落共享token；新会话save显式选择session/remember，不把本标签登录当作清除其他共享会话的授权。detached只存布尔状态，不是凭据/退出服务器证明；旧无标记浏览器保留共享登录行为。c1验收独立Storage双标签模型、不同token/旧预期值/退出后重开与HTTP401，不修改密码/MFA规则或后端。每项门禁与双写后再推进，C4整体/S1d仍未完成。

C4a3c1完成（本地）：token.ts按有效token条件清理，本标签独立session登录不会删除另一个不同共享令牌；clearToken(expected)遇到已变化的有效令牌返回false，不清新身份。明确退出/本标签登录设置sessionStorage布尔detached标记，删除本标签token后不再自动回落到共享账号；新显式remember登录重新接入共享slot。标记仅值1，无认证材料，不代表服务器会话结束。旧无标记标签仍可读取记住登录令牌，不改变后端密码或MFA规则。

验证：security-c4a3c1-unit.log token/http/auth-mfa共33/33（新增5项独立Storage双标签模型）；security-c4a3c1-browser.log存储双页2＋原认证4共6/6，包含真实浏览器401仅清当前私有会话、另一共享身份保留、退出刷新不回落、MFA交接/未知OTP/离页晚响应。security-c4a3c1-verify.log完整verify56文件283项、baseline128/route30、类型/测试类型/lint/i18n/构建发布通过（157KiB gzip/38 lazy modules/296 files）。5196已退出，均API夹具，无生产凭据修改。此项只修存储归属；登录/MFA跨标签准入、共享身份变化与退出协调仍分别待c2/c3，不声明a3c整体完成。
C4a3c2开始：登录/MFA请求共用同origin的认证存储槽Web Lock（含记住登录可能写共享token，故不按用户输入拆锁）；只包单次HTTP＋本次权限读取，不在用户等待OTP期间长占锁，不排队/不自动重放。匿名标识只用origin/固定资源，不把用户名/密码/OTP/challenge/token放锁名或恢复记录。输入先快照，失效/离页释放临时锁并丢弃晚结果，不声称服务器旧登录已取消。

登录开始只detach本标签，不清其他共享身份；锁内核对本次认证代次与原共享槽值，MFA挑战只内存并绑定产生时共享槽。共享槽在另一标签变化时，依赖该共享身份的当前UI/权限或待MFA/在途认证必须失效，保持其他私有会话独立；不自动接纳新账号。取消使用本次认证代次的句柄，最多清本次已接受的token，不得由旧页面reset新会话。能力不支持明确拒发，密码/MFA后台策略不变。验收双store/双页准入、取消/迟到授锁/旧finally、新共享token/权限读取竞态、MFA等待期间另一标签登录、秘密不持久化和既有认证工作流；c3原会话退出去重仍后续。

C4a3c2完成（本地）：auth.login/verifyMfa共用同origin固定认证存储槽Web Lock，取得前同步busy并快照输入，冲突立即拒绝，不排队/重放；锁覆盖单次请求至本次权限读取，等待用户OTP期间释放。登录开始仅detach本标签并保留其他共享令牌；匿名调用和原共享槽同时核对，挑战仅内存绑定产生时槽值。共享身份改变使依赖它的旧认证/待MFA/已加载权限失效，另一个独立私有会话不受影响；不自动采用新共享账号。LoginView/TotpPage用captureAuthentication绑定本次代次取消，最多清原接受token，旧页面取消不清新身份。取消不表示服务器旧会话创建已回滚；未知OTP仍丢弃挑战，缺Web Locks明确拒发。销毁只释放本store锁/监听，不访问已销毁依赖store。

验证：security-c4a3c2-unit.log4文件42项通过（新增authentication-coordination9项＋原auth-mfa/token/http），真实浏览器security-c4a3c2-browser.log认证/存储9项通过；security-c4a3c2-boundaries.log15项通过（文件筛选包含authentication-coordination3、coordination5、credential3、session-lock4），与前轮有重叠不相加成独立总量。缺能力的凭据/会话/普通操作场景现先正常登录再移除浏览器能力，保证测到各自边界；另有独立无能力登录拒发场景。

最终security-c4a3c2-final.log完整npm run verify57文件292项全部通过；baseline128/route30、类型/测试类型、ESLint/i18n及构建发布通过（158KiB gzip/38 lazy modules/296 files）。初轮security-c4a3c2-verify.log为35失败/257通过，均关联销毁回调尝试访问已销毁Pinia偏好store；清理改为释放本store资源后全量复验关闭，不称首轮通过。早期旧finally测试暴露取消后释放浏览器锁的微任务尚未结束，加入本标签release屏障后42专项通过；新意图不排队等待别的标签。一次带竖线的Windows npx过滤命令被shell拆分未执行测试，已改明确文件列表后15通过。5196已退出，全部业务响应为API夹具，未变更后端密码/MFA规则。下一c3原会话退出协调；C4/S1d整体仍未完成。
C4a3c3开始：原会话退出用origin＋token的SHA-256摘要scope（不保存原token）和Web Locks，锁在beforeExit未保存确认前取得；确认取消不写退出标记/不注销。确认后共享LOCAL_EXIT元数据先落盘，再至多发送一次/auth/logout；该标记只证明本浏览器停止使用该会话，不是服务器完成回执，未知/刷新/关页不重放。其他标签观察匹配摘要后只detach自己的会话，不先删除共享令牌干扰原发送者；新token或其他私有会话不受影响。重新加载携带旧token也先核对退出标记，不恢复使用旧会话。

存储/Web Locks不可用或原会话已有退出者时保留各标签未保存确认和本地退出能力，但不新增无法协调的网络注销；对无法共享本地意图不宣称所有标签/后端已退出。元数据schema/scope/key/state（LOCAL_EXIT）、≤512字符/≤256scope，锁名不含token，容量事务不包网络/确认。原发送者回包后只清原身份；旧结果/迟到摘要/读回不能清新登录。验收原会话双页单次发送、确认取消、网络未知后刷新、私有/共享其他身份隔离、能力/存储故障和原认证/锁屏/便签回归，后续C4b面板与c兼容/S1d仍独立。

C4a3c3及C4a跨标签协调完成（本地）：session-exit按origin＋原token摘要scope取得Web Locks，再执行beforeExit未保存确认；取消不写标记。确认后LOCAL_EXIT元数据先落盘，只有首个持有者返回发送键并至多POST一次/auth/logout；后端仍NATIVE。该元数据仅表示本浏览器停止使用旧会话，不是服务器退出成功证明；未知/刷新/旧token恢复不重放。匹配标签detach本地并清身份，不抢先删除共享token干扰发送者；不匹配的新token/独立私有身份保持。load携带已退役token时在权限读取前拒绝恢复。缺Web Locks/摘要/存储或达到256scope上限时保留未保存确认及本地退出，不发送无法协调的注销，也不宣称后端/其他设备已退出。

旧退出回包只清原身份，摘要/通知晚到不清新会话；确认期间身份失效释放临时锁，即使弹窗还未返回也不泄漏。其他标签的退出意图可使等待非独占确认的标签失效，不能以本地取消撤销另一页已确认退出。令牌已消失的本地页仍尊重取消，确认期间的新登录不被旧回调清除。元数据仅schema/scope/key/state（LOCAL_EXIT），≤512字符、≤256scope，无原token/密码；短容量事务不包含网络或确认。

验证：security-c4a3c3-unit-recheck.log4文件46项通过（session-exit6、logout-coordination6、原auth-mfa13/security-controls21）；之后补无token确认竞态1项由最终security-c4a3c3-complete.log覆盖。最终npm run verify59文件305项、baseline128/route30、两类类型、ESLint/i18n、构建发布通过（159KiB gzip/38 lazy modules/296 files）。security-c4a3c3-browser.log初轮7场景中6通过：第二页已自动退出，测试仍等待点击消失的菜单导致超时。改为验证真实自动退出、单次请求与旧token拒绝恢复，security-c4a3c3-browser-final.log退出3/3通过；原认证4项沿用初轮通过证据。security-c4a3c3-shelf.log未保存便签锁屏退出1/1通过。测试5196/5198已退出，无真实用户注销/服务重启。

C4a1/a2/a3现均本地验收，包含普通操作/凭据/锁屏/认证/退出协调；只保证使用新版客户端的同origin浏览器，服务端租户授权、跨设备/集群和S1d真实慢网断连仍独立。下一C4b统一状态面板/离页与恢复入口，再C4c旧版兼容；不将C4a结束当作S1c或专项整体完成。
C4b按依赖拆分：b1操作状态面板（当前可信owner、原键LOCAL核对、存储故障与迟到结果隔离、双语/键盘/窄屏），b2离页/刷新提示和受授权的业务恢复入口，b3组合浏览器与整体验收。当前b1实施；不得从持久记录重建写请求，不提供清除UNKNOWN或换键重发。b1以定向单元、真实浏览器/API夹具及完整verify验收，通过双写后再b2。

C4b1操作状态面板本地完成：AdminLayout顶部入口复用OperationRegistry，只显示当前可信API/tenant/portal/user范围，所有记录只含原有元数据；LOCAL显式按原键查询，20次同步触发只发一次读取，终态仅刷新面板并提示业务授权重读。NOT_FOUND/畸形/网络错误/原生未知不删除记录；NATIVE/UPLOAD不误走LOCAL、不提供清除或重发。锁屏隐藏面板，身份/权限代次与迟到响应隔离，存储故障不显示伪空列表。

验证：security-c4b1-unit-final.log定向7/7；security-c4b1-complete.log完整verify60文件312项、baseline128/route30、应用与测试类型、ESLint/i18n、构建发布通过（160KiB gzip/38 lazy modules/296 files）。security-c4b1-browser-complete.log共22/22，包含面板4、普通协调5、认证3、凭据3、退出3、锁屏4；真实浏览器/API夹具，不是S1d真实后端。320px浅色及深色英文截图已人工检查，保存在.e2e-runtime/security-c4b1-panel-*.png。首次单元原生夹具漏传证据解释器、首次lint原始感叹号、后续测试类型可选held字段错误均已修复复验，未放宽产品断言。文档153个本地链接无缺失；下一b2离页/刷新提示与受授权恢复入口，C4/S1c及X5仍未完成。

C4b2开始：离页提示采用管理壳内持续可见的非阻断提示，提醒页面离开/刷新会丢失未保存输入或当前File、不会取消已发操作；原未保存表单守卫继续由业务负责。刷新/路由切换后按可信owner恢复同一记录，不自动查询/执行。恢复入口由代码白名单映射到已注册业务路由，并检查当前路由权限；不读取存储中的URL、不把原键/业务正文拼到URL。原生页面只按已有适配器核对，没有权威回执仍UNKNOWN。验收路由/协议白名单单元、离页与重载浏览器、撤权/锁屏隐藏/中英文和完整verify；b3组合与c旧版兼容仍后续。

C4b2补充：已登记的UPLOAD可通过文件模块既有lookupUpload/uploadEvidence按原键只读核对，ACCEPTED/RUNNING仍保持未决；面板不保留或恢复File、不续传、不自动下载/绑定，成功后业务授权重读。其余NATIVE仅提供已注册且当前授权的页面入口；无法判定资产类别时不猜编辑器。

C4b2本地完成：管理壳在SLOW/PENDING/UNKNOWN时显示持续离页提示和面板入口；路由切换/刷新不清原编号，不自动查询/写入。operation-recovery只由代码白名单产生无参数本地路由，实际路由必须已注册且通过当前权限（含已有anyPermission）；点击时再次检查，未知业务/不明确规则资产不猜编辑器。随手栏复用原入口；普通上传按既有模块原键核对，不恢复File/不续传/不下载绑定。

验证：security-c4b2-unit.log10/10；security-c4b2-complete.log最终完整verify61文件315项、baseline128/route30、类型/测试类型/lint/i18n/构建发布通过（160KiB gzip/38 lazy modules/296 files）。security-c4b2-browser-complete.log面板4＋恢复3共7/7（本地真实浏览器/API夹具），含离页/刷新同键、不自动查询、撤权隐藏入口、锁屏隐藏、ACCEPTED保留与原上传终态。首轮RouteMeta弱类型适配错误、浏览器首页定位误用而实际为“操作台”均已修正；320px截图发现顶部溢出及提示横向挤压，已调整小屏间距/纵向排版，补全部可见工具栏按钮边界断言并复验，截图.e2e-runtime/security-c4b2-recovery-320.png。下一b3组合回归，不以b2通过代替C4c/S1d。

C4b3开始组合验收：安全浏览器完整配置（含输入/提交、认证、凭据/锁屏/退出、协调、面板及恢复），再原安全操作/业务流程与随手栏回归；仅用5196/5198测试服务。按实际失败修复，全部通过后双写C4b状态，C4c独立旧版兼容及S1d仍后续。

C4b3首轮安全组合63/64通过：跨标签原语测试在page.close完成后立即acquire得到busy，页面关闭事件不等于浏览器锁管理器已释放。已改为只读poll held归零后再单次acquire，不重试获取、不改生产门禁；待复验。随手栏4/4通过，原业务流程进行中。安全日志另有3次ResizeObserver通知循环告警，对应交互断言通过，保留日志，不宣称控制台无告警；b3/C4b未标完成。

C4b3及C4b整体已本地验收：security-c4b3-close-recheck.log关页锁释放场景连续3/3通过，采用只读navigator.locks.query等待实际释放后单次获取，生产代码和互斥/不排队断言未放宽。与首轮security-c4b3-security.log的其余63项构成本轮64个安全场景的分轮证据，不能把重复3次计为3个独立场景。security-c4b3-workflows.log原业务/安全布局20/20、security-c4b3-shelf.log随手栏4/4通过；security-c4b3-test-types.log测试类型及security-c4b3-test-lint.log改动测试ESLint通过。最终产品完整verify仍为C4b2的315项，产品源码此后无变更，不重复构建。

保留首轮完整日志内3次ResizeObserver通知循环告警，不宣称控制台零告警；对应业务交互、后续原流程与窄屏布局断言通过，本轮未修改第三方观察器。全部测试仅本地浏览器/API夹具，不作为S1d真实后端或X5多线证据。调度、README、规则和开发手册已同步；下一C4c独立旧版协议/标准门禁，C4/S1c、安全专项与四线整体均未标完成。

C4c开始独立旧版兼容：先运行根tools/dev/verify-frontend.ps1（独立旧工程eslint/build/unit/i18n），再以旧工程自身Vite入口5198做真实浏览器/API夹具验证缺键补UUID、显式原键与Long ID保留、202/409/429/503及业务拒绝/网络未知不自动重试、原生载荷和原键查询。测试不从next导入旧版生产代码，也不把旧版随机键能力当作新版跨标签/UNKNOWN交互。无旧版生产源码变更时不改基线哈希。当前S1c仍进行中，S1d等待C4c通过。

C4c及C4/S1c已完成本地验收：复用原playwright.security-legacy.config.ts，保留旧工程自身入口/5197与原protocol-compat，仅扩展legacy-compat用例；初轮独立5198新增3项通过后，最终复用既有5197入口4/4通过（security-c4c-legacy-browser-final.log）。验证自动键、大小写显式原键、POST/PUT/PATCH/DELETE与GET、字符串Long ID/原生operationId/version、202/409/429/503/非零业务码与网络未知、原键读取且无自动重试或正文持久化。未改旧版生产源码/基线哈希，旧版随机键兼容不等于新版跨标签和用户意图治理能力。

旧版标准入口最终powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/dev/verify-frontend.ps1通过：eslint、生产构建/类型、28文件55项单元、10模块双语。最初不带进程参数因本机脚本策略拒绝启动（security-c4c-legacy-standard.log），仅当前验证子进程使用参数，未改系统策略；最终日志security-c4c-legacy-standard-process.log。旧版产物已有大chunk构建提示保留，不放宽预算或冒充新版release门禁。新增测试类型/ESLint分别security-c4c-types.log与security-c4c-lint.log通过。产品新版最终仍315项完整verify；所有本轮测试句柄结束，未重启用户服务。

已双写调度及安全总进度，README/新版前端规则/客户端契约/开发手册同步。S1c只完成前端本地及API夹具验收；下一S1d使用专用库/隔离宿主验证真实慢网、断连、跨标签与双进程，安全S2～S6、P09容器/外部联调及X5仍未交付。

C4c收尾基线检查曾因本轮向旧版AGENTS.md追加规则而拒绝；旧版规则也属于128文件基线。已仅撤回该追加段，并以原基线SHA-256核对字节完全一致，未更改任何基线哈希或既有租户规则。兼容说明维护于新版规则、客户端契约和开发手册；最终复查记录见security-c4c-baseline-final-recheck.log。
