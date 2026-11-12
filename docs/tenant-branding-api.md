# 租户品牌接口与数据契约（S3已验收，S4分阶段验收中）

本文件细化[P10](proposals/P10-tenant-branding-and-storage.md)。S3接口、迁移与双前端消费已通过本地和隔离联调，实际模板/管理页面按S4实施，证据见[专项进度](tenant-platform-progress.md)。旧 `/api/config/parameters/public` 已删除，不保留别名；匿名旧路径401、已认证旧GET为405（现有ID修改路由的MVC方法匹配），均不返回公共参数。

## 输入范围

租户和portal来自受信Host目录与认证身份，不由品牌表单中的tenantId/portal决定。平台操作者修改某租户品牌也必须使用独立审计入口和显式目标授权，不把普通品牌API改成任意tenantId查询。

BrandContent schemaVersion=1：

| 字段组 | 字段与语义 |
| --- | --- |
| identity | systemName必填，shortName、englishName可空 |
| login | templateId为login1/2/3；title/description各含zhCN/enUS；backgroundBlur仅展示 |
| legal | copyrightOwner/startYear/url、icpRecord/url、policeRecord/url；链接不能没有对应主体或编号 |
| links | website/help/support/privacy/agreement；仅显式http(s)链接 |
| appearance | primaryColor为六位十六进制颜色或空，defaultSkin为iris/celadon/amber，allowPersonalSkin |
| assets | LOGO_LIGHT/LOGO_DARK/LOGO_COMPACT/FAVICON/LOGIN_BACKGROUND到文件ID的映射；ID通过JSON字符串传输 |
| overrides | 字段到INHERIT/OVERRIDE/HIDE的显式映射；缺省OVERRIDE；法律主体/备案不允许继承平台主体 |

模板ID只选择本地编译组件；任何URL、导入路径、Java类、HTML、CSS和脚本都不是模板配置。保存校验不能改变验证码/MFA或登录策略。背景和Logo使用素材引用，不给普通附件新增匿名下载。

## 管理接口目标

| 路由 | 权限 | 行为 |
| --- | --- | --- |
| GET /api/tenant/brands/draft | tenant:brand:view | 返回当前草稿、草稿版本、当前发布ID |
| PUT /api/tenant/brands/draft | tenant:brand:edit | expectedVersion＋content；并发冲突不覆盖 |
| GET /api/tenant/brands/templates | tenant:brand:view | 本地模板目录、版本、支持能力与启用状态 |
| POST /api/tenant/brands/publications | tenant:brand:publish | 固定草稿版本与预期线上版本，校验素材后发布新修订 |
| GET /api/tenant/brands/revisions | tenant:brand:view | 当前租户/portal历史，分页、不含秘密 |
| POST /api/tenant/brands/rollbacks | tenant:brand:rollback | 选择旧修订内容生成新的发布记录；不修改历史 |

发布与回退须含幂等操作ID；同ID相同意图读回原结果，不同内容拒绝。前端超时先查结果，不自动重发非幂等操作。读取草稿不自动写数据库；首次保存用版本0。保存与发布分开，编辑失败不能改变线上指针。

发布前资产检查：同租户/portal、受支持图片格式、大小/像素、可安全公开状态、引用有效。编辑者与发布者可不同，发布者不能因图片原上传人不同被要求绕过文件归属；通过所属模块公开契约验证受信品牌引用，不能直接查询file表。发布中锁定引用/头版本，防止删除或并发替换产生悬空引用。历史仍可回退的资产必须受引用保护。

当前S3b实现采用PNG/JPEG/GIF解码并重新生成静态PNG快照（GIF只取首帧）；2MiB输入/输出、4096边长、400万像素和进程4并发上限。这个受控图像管道不代表通用恶意文件扫描已交付，WebP/ICO暂不可选，Favicon使用PNG。快照随发布事务保存，普通文件content接口仍要求登录。历史发布图片可按原修订读取，仅限同一已验证租户/portal，不允许以文件ID访问草稿图片。

P11 X4a加固：公开快照编码只按真实PNG/JPEG/GIF头选择`java.desktop`内固定JDK读取器，固定JDK PNG写出器；不会先调用第三方ImageIO插件探测上传内容，缺少受审提供者直接拒绝。内存输入不使用全局ImageIO临时文件缓存，可寻址输出缓存和最终PNG同时受2MiB限制。新栅格写出不带原元数据/尾随字节，GIF只取首帧的既有语义保持；这不是对GIF所有帧或原件恶意软件的完整检查。二开增加图片插件不会扩展这条公开快照能力；需要新格式须改受审策略并验证，不能通过MIME/后缀或全局SPI注册绕过。旧上传识别到统一扫描的迁移仍属于P09，X4状态见[安全专项进度](security-defense-progress.md)。

每portal暂设100个发布修订、64MiB图片快照硬上限；达到上限返回明确容量错误，不自动清掉回退历史。后续需平台维护的留存/归档流程，不能用普通缓存清理删除这些持久版本。

## 匿名入口目标

`GET /api/tenant/entry`只返回服务端解析出的入口、已经发布且合成后的品牌、固定模板和允许公开的登录能力。无已发布品牌时使用该租户的安全初始品牌，不从别的租户取得版权或素材。unknown/停用入口由过滤器拒绝，不能套默认租户。

建议DTO分为 `entry`、`branding`、`login` 三部分：

- entry：租户ID、portal、域名ID、入口状态所需的非敏感展示字段；不输出资源绑定/数据库地址。
- branding：发布修订、名称/Logo/背景/Favicon链接、欢迎文案、法律主体、受控外观和帮助链接；不输出草稿或内部资产存储路径。
- login：服务端认证策略投影，例如captchaEnabled、支持的登录能力。品牌表单不能编辑这些安全决策。

S3c首版HTTP契约固定为：`entry={tenantId,domainId,portal}`；`branding={revisionId,revision,identity,login,legal,links,appearance,assets,legacyBackgroundUrl}`；`login={passwordEnabled,captchaEnabled}`。branding内login仅是模板和展示文案，外层login是认证能力；资产映射值为站内公开修订图片路径，不是源文件ID。legacyBackgroundUrl仅保留迁移生成的只读引用。尚未发布时revisionId为null、revision为"0"，使用当前目录租户名称及空法律/素材，不能读取草稿。所有Java Long均沿用全局字符串协议（包括展示用revision序号），前端不能把ID转成JS Number。

图片路由为`GET /api/tenant/brand-images/{revisionId}/{slot}`，slot为受控AssetSlot，返回重编码后的image/png；缺失或其他租户/portal的修订图片返回404。entry与图片均明确no-store，避免共享代理缓存跨Host响应或绕过停用入口；不为首版启用CDN长缓存或304快捷路径。匿名白名单仅覆盖entry和这个图片路由的GET/HEAD，不覆盖品牌管理路径。旧Bearer不影响公开入口刷新，但已建立的身份与Host冲突仍拒绝。

前端从entry.portal创建登录请求，URL中的portal只能作为不受信输入处理；不得覆盖绑定门户。加载失败取消验证码请求与登录提交，清除上一入口的标题/Logo，不缓存密码或挑战。迟到响应按请求代次丢弃。

## 继承与失效

INHERIT、OVERRIDE、HIDE必须在服务端合成，不能只靠前端隐藏仍把敏感/错误主体返回。HIDE清除对应公开字段；清空覆盖值与删除覆盖恢复继承分开。法律主体无平台默认继承；发布时记录实际合成来源版本，以便后续平台默认升级不会偷偷改写既有发布快照。

优先返回不可变发布快照和修订标识。后续缓存必须包含可信tenant/portal/domain/locale及发布修订，不把请求Host当未经验证的缓存命名空间。匿名品牌接口不得透传ParameterQuery.publicValues或publicReadable参数全集。

S3b合成默认来自部署型 `sma.tenant.brand-defaults`（revision默认builtin-1及类型化content）；默认不含任何文件ID或覆盖指令，禁止引用其他租户私有文件。发布存储已合成内容和默认修订，后续更改默认不改写旧发布。平台可视化共享定义管理由S5扩展，不能把当前部署型默认描述为已存在平台管理页面。

## 两套前端迁移

移除两套branding store的旧公共参数HTTP调用，替换为同一新DTO。内部旧组件所需键可由已知字段显式映射，禁止重新返回任意参数字典；登录模板/Logo逐项替换为新组件属性。next的mock-api、full-api、foundation、softlight-pages及相关真实E2E一并迁移，ParameterTests改为证明旧路径已不存在且新入口不泄漏私有参数。

Vite开发代理与生产代理必须保留租户Host并对齐Origin；changeOrigin/rewriteWsOrigin不应把租户入口悄悄改成平台localhost。前后端同批发布，没有旧public路径兼容窗口；回退须前后端成套且保留新品牌数据。

## 旧品牌数据切换细节（后端与双前端已验收）

旧键仅限system.name、system.copyrightCompany/copyrightUrl、system.recordInfo/recordUrl、login.backgroundUrl/backgroundBlur；login.captchaEnabled仍属于认证策略，不能变成品牌可编辑字段。Config提供由其拥有的类型化迁移读取/归档契约，Tenant不直接读取config表；一次迁移固定源摘要，转换品牌内容、记录迁移回执和停止旧键写入。匿名GET不能触发建表、回填、发布或跨租户遍历。

为保留已有背景图，迁移器可把旧的合法http(s)/本地资源地址转成只读的遗留背景引用；该例外只允许迁移产生，新品牌表单不能新增或改写任意图片URL。服务端不抓取URL；管理页明确标注“迁移背景”，可上传素材替换或明确隐藏，发布/回退保存引用字符串。外站图片字节可能改变，因此不把此类引用描述为不可变PNG快照；新上传素材仍走已验证的图片快照管道。非法地址、secret或未公开旧值、超长/不完整法律字段需进入迁移问题清单，不静默截断或转成匿名可见内容。

存储采用head/revision上的独立只读legacyBackgroundUrl元数据，不扩张BrandContent的可编辑图片输入。迁移器初始化草稿头及首个发布修订；后续保存新LOGIN_BACKGROUND素材或明确HIDE/INHERIT时清除草稿头的遗留引用，已发布修订保持不变，回退复制所选旧修订的引用。普通品牌保存请求不能设置这个字段；匿名branding只可投影当前发布修订的legacyBackgroundUrl，新PNG素材优先。Config原行归档保留核对证据，秘密值在迁移公开契约中先行脱敏，不能在日志/问题清单中打印原值。此流程已通过真实MySQL事务/并发/原功能18项组合及独立旧库结构升级1项，日志见专项进度；前端展示与切换仍需独立验收。

旧参数与品牌存在同时编辑的窗口时必须锁定并核对源摘要；不能覆盖已存在的新品牌草稿或发布指针。旧键归档后从普通参数编辑及内置重置/初始化中退出，避免重置参数重新生成第二份品牌来源。旧数据归档与新品牌写入在同一平台库事务内完成，失败保留原数据与可重入状态。整个切换按租户/portal核对名称、版权、备案和背景；遗留参数原本按租户共享，初始迁移至该租户已登记门户后各门户独立维护，不能把平台租户法律信息当作其他租户默认值。

新entry、图片HTTP、后端迁移和双前端消费已完成本地与隔离联调，旧public路由已删除。迁移冲突/不可转换数据的处置与真实旧库升级证据仍是实际部署的前提；本次验收不代表生产服务已升级。

## S4管理辅助接口（实现中，验收查进度）

- `POST /api/tenant/brands/previews`：`expectedVersion + content`，要求tenant:brand:view；服务端合成继承/隐藏、按原文件归属验证素材，并返回暂态安全PNG data URL、默认修订和只读迁移背景。响应no-store，不建立草稿头、文件引用、历史或发布指针。最多五个素材槽，仍受单图2MiB、像素和并发预算限制。仅用于已登录管理页内存预览，不能持久化、放进URL或开放匿名预览。
- `GET /api/tenant/brands/operations/{operationId}`：要求tenant:brand:view，只查询当前租户/portal且原操作者拥有的操作；找到时还验证原动作publish或rollback权限。`COMMITTED + receipt`证明该操作提交；`NOT_OBSERVED + null`不证明失败，可能尚未提交，界面不能据此自动重试或声称已取消。
- 前端保存响应未知时读回草稿，版本使用字符串/BigInt比较，内容使用规范键顺序核对。相同旧版本仍属于未确认；较新版本且内容一致可显示已核对保存状态；内容不同保留本地并提示冲突。不会把发布未知结果转成另一个新operationId重发。

### 外观策略与兼容范围

Config拥有新增的宿主SPI `AppearancePolicy.current()`，Tenant提供唯一实现，只依据可信租户/portal的已发布品牌。Config缓存仍存个人原始偏好，返回前合成当前策略：没有个人偏好时用租户默认；允许个人选择时用已保存的个人skin；禁止时返回租户默认。禁止期间保存语言/主题等其他偏好会保留已有原始skin，不把有效默认值覆盖进去；重新允许或回退后恢复原选择。已有个人偏好整条记录继续视为用户已保存选择，不将它批量重置。SPI缺省为iris/允许个人选择；不可注册多个竞争实现或由业务覆盖Tenant Bean。读取不缓存有效结果，当前每次合成会读取发布头/修订，性能验收在S10统一评估。

新版匿名skin缓存按tenant/portal隔离；预览不调用applyAppearance、不写localStorage，不修改真实登录偏好。旧版Element Plus继续支持品牌标识/登录模板/明暗Logo和法律链接兼容，不新增新版iris/celadon/amber皮肤选择界面。品牌primaryColor只影响登录装饰；不会套到操作按钮上破坏文字对比度。

名称显示在英文环境优先使用非空englishName，否则systemName；欢迎文案按所选语言优先、另一语言兜底。shortName用于管理壳的简短品牌显示。重复选择当前语言也不能把租户标题还原成基座默认名。
