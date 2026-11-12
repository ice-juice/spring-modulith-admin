# 新版前端写入口覆盖清单

日期：2026-11-12。对应[前端安全进度](security-frontend-progress.md)C3d3b4；本地覆盖审查，不是全站真实后端或跨标签验收。客户端调用契约见[请求客户端契约](security-request-client-contract.md)，服务端路由/权限与协议以[后端入口清单](security-endpoint-inventory.md)为准。

## 审查范围与方法

覆盖 `frontend-next/src` 内 TS/Vue 生产源码的普通API调用、原始Axios、动态方法/命令、WebSocket，以及通用资源编辑器的消费者。排除单元测试、纯UI集合操作与未接业务API的组件展示页。按事件处理器→提交机制→适配器→后端协议追踪；不把搜索命中行数、路由数或HTTP方法数量称为端点覆盖率。

本轮先检索 `post/put/patch/remove`、Axios `.request`、`fetch/XMLHttpRequest/sendBeacon`、`ruleWrite/releaseRequest` 与方法选择，再读取调用者和相关后端协议。候选原始输出为 `frontend-next/.e2e-runtime/security-c3d3b-write-candidates.txt` 和 `security-c3d3b-adapter-candidates.txt`；这些是审查过程材料，早于本轮修复的行号不能当当前定位。最终源码清单及SHA-256另存 `security-c3d3b4-audit.json`，只用于定位此次验证输入，不替代运行测试。

## 当前写入口

下表的“完成”均指C3本地接入与对应专项证据，跨标签C4和真实后端S1d尚未验收。历史验证命令/失败与修复记录见前端进度，最新全门禁为51文件236项。

| 范围/实际消费者 | 写入口与归属 | 提交/恢复方式 | 本地证据 |
| --- | --- | --- | --- |
| IAM `ResourceEditor`、`ResourcePage`；用户、部门、岗位、权限、导航和角色页面 | 动态资源新增/编辑/删除，用户密码重置、角色授权/删除，个人资料 | `useSubmission`/对象池；同对象编辑/授权/删除共用范围；LOCAL原键回执后授权重读；密码仅内存 | C3a/b1；submission、IAM、operations、完整verify |
| Config `ParametersPage`、`DictionaryPage`、`PreferencesPage`、`DisplaySettings` | 私有参数/字典CRUD，刷新/内置重置/初始化，个人偏好保存/恢复 | LOCAL原键；偏好菜单和页面共用本人范围；未知保持原输入 | C3a；submission、operations及verify |
| Config `EffectiveParametersPanel`、`EffectiveDictionariesPanel`、`SharedConfigPage` → `layers.ts` | 租户覆盖/恢复继承、字典条目覆盖/恢复/私有条目/启用、共享草稿与发布 | 同对象LOCAL登记；保留平台/租户revision、draftVersion和原发布ID；回执后授权重读；身份/权限保护见`useDirectorySession` | 租户S5e/f关联证据见[租户进度](tenant-platform-progress.md)；本清单仅审查调用链，不替该线宣布整体完成 |
| Message `NoticesPage`、`TasksPage`、`PrivatePage`、`InboxPage`、`useMessageDetail` | 公告/待办/私信创建、编辑、删除/恢复、状态/分配、单项/批量已读 | LOCAL原键；确认前固定批量目标与模式；重叠资源集合互斥；完成后纯GET重读详情，不重复写以获得正文 | C3b2；submission、IAM/operations及verify |
| File `FilesPage`、`useFileUpload`、`UploadButton`；头像/品牌/公告附件 | 文件删除；通用文件/图片上传 | 删除LOCAL；上传原任务/原键；仅ACCEPTED且可续传时使用仍在内存的原File，先核对原键；UNKNOWN/RUNNING不重传 | C3c4；file-upload、submission、tenant management；P09新upload-tasks全流水线不在此项冒充完成 |
| OpenAPI `ApplicationsPage` | 应用CRUD/开关、密钥轮换、目标绑定/解除 | LOCAL对象池；创建/轮换凭据只在直接成功响应显示，回执不冒充秘密，未知不再次轮换 | C3b1；submission/operations及verify |
| Workspace `shelf.ts`、`notes.ts` 与管理壳 | 书签新增/改名/移除/排序、布局；便签保存/状态转换 | LOCAL原键，同时保留领域requestKey/version；输入正文仅内存，状态转换在等待保存前预留；未知不再创建 | C3c1；notes/shelf单元及shelf浏览器 |
| Tenant `brands.ts`、`directory.ts` 及品牌/租户/域名页面 | 品牌草稿/发布/回退；目录登记/状态；域名挑战/检查/启用/主域名 | 草稿及普通修改LOCAL；发布/检查NATIVE保留原operationId并核对原对象与回执状态；当前内容相同/NOT_OBSERVED不能完成旧操作 | C3c2；directory/brands及tenant浏览器；X4公开图片另有专项证据 |
| Monitor 缓存 `CachesPage`、策略面板 → `cache.ts` | 预览、提交、取消、策略保存/重置 | 预览不是执行；提交NATIVE原preview/key，取消独立意图；策略LOCAL；QUEUED/RUNNING/部分UNKNOWN不当完成。目录/策略/预览节点头由P08约束，持久提交/历史回执不误加当前节点头 | C3c3a、C3d3b3；cache单元与浏览器；多实例验收见P08 |
| Monitor `SqlPage`、`PublicationsPage` → `useMonitorCommands` | 实例SQL统计重置；失败事件重投 | NATIVE持久原意图；统计集合/事件ID分别互斥；当前统计、事件COMPLETED或列表缺失都不解除UNKNOWN；重投直接确认不等于监听器完成 | C3d3b1；4项新增浏览器＋原operations工作流 |
| Rule 场景/模板/实例/版本/用例/运行/运维页面 → `useRuleTask.command` | 场景/草稿/实例/用例保存、模板治理、执行、用例运行、批次迁移、资源/保留清理 | 精确JSON快照；按后端LOCAL/NATIVE分别处理；模板发布与批次按原键查询；资源清理无完成回执时不靠统计解锁；普通回执不当Asset/Execution | C3c3b、C3d3b3；task/exact及authoring/instances/releases/operations浏览器 |
| Extension `ExtensionPage`、`RuntimePanel` → `client.ts` | 发布控制台命令/授权/上传分片/完成，登记票据/机器令牌，宿主工作核对 | 独立代理只发捕获的宿主Authorization；保留Prepared键、challenge/grant、原生work与分片范围；未知不重放凭据；首次秘密不能靠完成回执再造 | C3c3c；client单元/extension浏览器；独立制品及真实发布证据另验 |
| Auth `LoginView`、`TotpPage`、auth store | 登录、一次性OTP验证、退出 | 同步认证门禁/代次/原身份绑定；未知OTP清挑战，不重放；迟到退出返回不能导航新身份 | C3d1；auth-mfa/http及authentication浏览器 |
| Auth `useAccountSecurity`、`useSecurityControls` | 验证器绑定/确认/取消/解除、安全密码设置/修改/重置；本人登录密码、锁/解锁、统一退出确认 | 凭据/锁独立元数据门禁；本人改密LOCAL核对原键与精确操作，其他原生凭据不伪造回执；密码/OTP/QR只在内存；锁未知跨刷新遮挡 | C3d2a/b；account-security/security-controls及布局/工作流/便签浏览器 |
| Auth `SessionsPage` → `useSessionRevocations` | 管理强制下线 | NATIVE按目标登记，当前会话走统一退出；确认前捕获原ID/身份，未知不凭分页列表缺失解除；不同目标独立 | C3d3a；11项新单元、2浏览器与原工作流 |
| Message `stores/messages.ts` | 短期一次socket-ticket与通知连接 | 原会话绑定；同账号换token、锁屏/权限变化关闭旧连接/清计数；旧推送和异步/me不能结束新身份；重连遵循票据退避，不套业务回执或持久化票据 | C3d3b2；messages/auth/http33项、锁屏/423工作流 |

## 必须保留的分类

- `POST /common/requests/lookup`、`/file/uploads/lookup`是只读原结果查询，不是重发原写。
- Rule `/validate`、`/simulations`、`/instances/{id}/upgrade-preview`、`/executions/search`属于后端明确QUERY；模拟不保存执行记录。执行/批次/用例运行等写入口仍走`useRuleTask.command`，不能根据“试算”这个名称混为一谈。
- 品牌预览不发布配置；缓存预览生成冻结计划，不能自行执行。预览的预算/生命周期仍由各自后端约束。
- 发布代理的外层POST可承载授权GET，必须按内层白名单方法/路径与原生协议分类。
- 代码中的`Set.delete`、DOM元素`remove`、注册表回调变量`fetch`、图库演示按钮均不是远程业务写入口。没有发现另一路生产`XMLHttpRequest`/`sendBeacon`写传输。

## 发送与验收边界

普通`createApi`调用默认固定调用时的身份，特殊规则适配使用`bindRequestIdentity`；显式`expectedIdentity: null`表示匿名。身份核对只在客户端内存，不能成为自造认证头。发布代理使用显式捕获的Authorization。API自动补随机键仅协议兼容，业务意图仍必须由提交机制持有原键。

当前清单不声称同浏览器多标签原子协调、跨设备幂等、真实DB事务效果、双节点/LB、生产CSP/WAF或P09杀毒/容器流水线已验收。C4继续做操作面板与跨标签；S1d使用专用库验证慢网/断连/真实后端，X5才做四线合流。新增页面、命令、动态适配器或复用点须同步本表及对应测试，不能依赖本次检索永久覆盖未来变更。

C4更新（2026-11-12）：普通注册表C4a1/a2及本人凭据C4a3a已具备本地验证的同源浏览器锁/UNKNOWN同步；最新完整门禁261项。凭据标记使用localStorage v2按会话摘要分键，旧v1匹配迁移，原键核对同组互斥。锁屏/认证其他流程a3b/c、统一面板/离开提示、旧版兼容与真实后端S1d尚未验收；上表C3历史证据范围不扩大到这些未完成项。
C4a3b补充：会话锁现由shared-session-lock-intents承接，与凭据门禁独立；LOCK/UNLOCK同scope互斥，UNKNOWN及确认锁态同步遮挡，peer确认解锁还须授权读回明确未锁且原key未变。最新完整门禁278项。登录/MFA/退出跨标签a3c、面板/兼容与S1d仍待验收，不能用锁屏双页夹具代替真实后端认证证据。
C4a3c3补充：原会话退出现在由auth.logout(beforeExit?)与session-exit摘要标记协调，确认前锁、确认后至多一次发送；其他标签本地停止使用旧token，LOCAL_EXIT不声明服务器成功。C4a整体已本地验收，最新门禁305项；统一面板/离页提示C4b、旧版兼容C4c及真实后端S1d仍待验收。
C4b1管理壳新增OperationPanel，仅调用POST /common/requests/lookup按原键读取权威回执；不新增业务写请求。原生/上传记录保持原协议，不提供自动重发或清UNKNOWN。范围/锁屏/迟到/存储故障由useOperationPanel＋OperationRegistry共同约束；证据为7项定向单元、22项含面板的组合浏览器及312项完整verify，详见前端进度。

C4b2面板新增的POST /file/uploads/lookup是文件模块原键只读查询，不发multipart、不续传。静态业务恢复路由及随手栏入口只导航/授权读取；不新增业务写入。持续离页提示与刷新元数据恢复由管理壳展示，结果未知记录不清除，验证见security-c4b2日志。
