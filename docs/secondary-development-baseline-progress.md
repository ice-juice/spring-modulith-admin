# 单实例稳定二开基线

2026-11-12 用户授权优先推进到稳定二开，非必要增强延后。本计划调整近期交付顺序，不把局部通过当作完整生产发布，也不撤销原P10/P11目标。

## 近期交付边界

静态Modulith业务模块、单宿主进程、平台库和按存储组绑定的MySQL业务库、既有品牌/共享配置、受控业务账本和Outbox。缓存先固定部署级共享资源及公开接入/失效契约；独立Redis客户端和在线改绑实现留S8。业务从固定基座提交接入，禁止追随未验收工作树或内部实现。

前次答复的“X5后再补S8/S9”不符合现有总计划：完整X5仍依赖S8/S9及其他专项。此处增加范围明确的单实例二开验收，不代称X5，也不提前允许多实例/生产换库。X2拆成近期归属契约与后续真实多Redis验收，X2整体不因只完成契约而标完成。

## 顺序与验收

| 项 | 工作 | 验收 | 状态 |
| --- | --- | --- | --- |
| B1 | S7d-3阻塞修复：所有平台/业务MyBatis工厂使用同一平台号段；排查邮件摘流和安全事件夹具；固定源码验证 | 号段实体/工具/API、动态建池后号段不变、原三项失败组合、默认/独立引导；最终全量与非默认复验并入B4 | 完成（本地专项及B4完整回归） |
| B2 | X2a公开缓存归属契约：平台安全状态固定；可重建业务缓存声明存储组与失效权威；共享资源先行 | 错误归属/PROTECTED拒绝、业务库写入回滚/失效/跨租户、共享Redis真实回归；不把平台代次当业务代次 | 完成（本地；完整X2b/S8未完成） |
| B3 | 二开最小接入样例及契约收口：业务Mapper/迁移、权限、配置接入说明、Outbox消费者和缓存 | 隔离业务样例构建、真实库开通/提交/回滚/重放、稳定版本与升级说明 | 完成（服务级样例；接入说明已收口） |
| B4 | 固定源码单实例基线验收 | 根reactor与前端必需门禁、登录/品牌/首个业务链路、保守连接/线程容量、品牌/审计重启及业务恢复边界；记录源码与制品摘要 | 完成（2026-11-13固定快照本地验收；完整X2/X5仍后补） |

必须保留全量失败，不以禁用用例、放宽隔离或调低持久性绕过门禁。使用任务专属可清空数据库和输出，先聚焦复现/修复，再完整验证。多上下文测试和并行源码漂移需要固定源码快照及数据库连接预算。

## 后补队列

- X2b/S8：专用Redis多资源、租户改绑、冷启动/预热、单资源故障/公平配额真实验收；未完成前只承诺已测共享资源组合。
- S9：SQL数据源目录、筛选、定向重置与资源运维UI；实际连接上的SQL归属继续保留。
- 多实例、负载均衡、全业务数据搬库/反向迁移、规模容量矩阵、扩展云存储与增强安全运营，继续沿各专项执行。
- X5及S10仍是完整平台联合验收与发布收尾。其他会话已在进行的专项保留其工作，不由本线撤销或清理。

在B4通过前，不发布“稳定二开”版本或标记S7整体完成。已发现的真实基础设施缺陷属于近期必修。

## B1执行记录

- 发现真实号段装配缺口：S6手工创建平台/静态业务工厂与动态物理账本工厂均未显式传入宿主IdentifierGenerator，实体ASSIGN_ID可能回退Snowflake。现三处注入同一平台生成器，动态工厂缺生成器拒绝；追加工厂归属与动态装配后实体/工具ID验证，真实三库夹具也检查平台号段上界。此问题不能归类为无关夹具失败。
- Mail摘流夹具的线程改为禁止继承请求ThreadLocal，并在等待失败时先展开已结束Future的实际异常；不放宽生产准入或10秒断言。Defense只读HTTP测试改为对比请求前后计数，保留原证据字节不变断言，兼容同库前序场景。
- 固定源码快照为`backend/target/p10-baseline-snapshot/source-manifest.json`（2068文件），新专用可清空库为`127.0.0.1:13306/sma_test_p10_baseline_20261112`，独立限库账号；不复用P08库。现有本机测试MySQL设置保持原值，未做断电持久性验证。
- 首轮`p10-baseline-focused.log`在测试URL附加5秒socketTimeout误限制Flyway DDL，应用启动失败；已移除测试全局socketTimeout，号段生成器本身的2秒连接/5秒网络预算保持。保留失败日志，复验写入`p10-baseline-focused2.log`，未通过前不更新完成状态。
- B1第一子项已通过：`p10-baseline-focused2.log`根Standard入口34/34、0失败/错误/跳过、BUILD SUCCESS（21:03，08:14）。包括实体/API/工具及动态工厂号段4、Mail摘流1、安全采集3＋查询HTTP5、架构17/号段边界1/普通端点1/模块2。该结果证明三项已知失败组合修复；不是完整平台发布或多实例验收。
- B1第二子项正在运行：`p10-baseline-three-databases.log`使用13306新建的精确平台/业务A/业务B三库，验证平台号段与两业务库路由/回滚/Outbox和默认宿主。原3306同名历史验证库没有被修改。完成后再开始B2；完整根回归仍归最终B4收口，S7整体不提前标完成。
- B1真实三库分轮通过：首轮9项8通过/1错误，目录夹具写死3306导致新账号连错实例，改用测试URL实际端口后`p10-baseline-three-databases-fixed.log`2/2通过（目录池1＋号段边界1，21:10）。首轮业务Mapper1、Outbox1、号段4、默认启动2全部通过；生产号段修复无再次变化。接着独立空目录引导，不把分轮证据相加当作一次全量。
- 独立空目录引导`p10-baseline-empty-bootstrap.log`3/3、0跳过、BUILD SUCCESS（21:17，06:13），覆盖RR旧快照并发首次启动、禁止覆盖管理员修改/复活软删除/抢占域名及平台身份。B1专项完成；进入B2。原S7非默认ID=42专项22/22保留，最终当前基线复验并入B4；全量失败不改写为通过。

## B2实施顺序

先增加兼容的会话物理身份只读方法、业务缓存目录和受控读入口；平台ManagedCache保持原注册与治理。业务读取在同一已打开会话中取业务库代次和加载，复用P08本地/Redis实现，每次固定源ID/修订/存储组命名空间；总请求、租户并发和活动资源数量有界，PROTECTED拒绝。先完成单元/结构与独立业务目录编译验证，再实际双业务库＋共享Redis验证提交/回滚/切源和故障。新目录进入部署缓存证据，未来多资源仍须X2b验收。

B2验收：首轮`p10-baseline-cache-unit.log`43/43通过；真实平台＋双业务MySQL与共享Redis16379的`p10-baseline-cache-real.log`16/16、0跳过通过（新实际缓存链1、原RedisStore9/Managed6）。新链证明同键共享命中、提交失效、业务回滚不失效、切换后新会话读新库、旧会话固定旧库、跨租户及TCP代理断连后有界回源；仅清理本轮精确Redis键。补独立编译BusinessCacheCatalog与平台失效拒绝后`p10-baseline-cache-final-unit.log`40/40通过（21:26），实际运行行为未改。B2/X2a本地完成，整个X2多资源故障/改绑仍未完成；无SQL运维UI或多实例声明。

## B3实施顺序

新增`common :: persistence`的BusinessSchemaCatalog，由业务模块声明存储组；宿主验证真实模块，业务迁移使用`db/business/<module>`和独立历史，避免平台默认Flyway误迁。正式provision按持久任务的storageGroup执行框架与业务schema后才激活；缺目录/缺脚本拒绝。低层框架迁移方法保留给内部旧专项，不代替正式开通。验证独立编译目录、缺脚本/失败不激活、真实MySQL迁移重入与业务表保留，再交付不参与main构建的教学样例和二开手册。

B3迁移接入已分项通过：`p10-baseline-schema-unit.log`24/24、`p10-baseline-schema-mysql.log`25/25均0失败/错误/跳过、BUILD SUCCESS（真实库轮21:39，04:13）。正式任务携带持久storageGroup，缺目录拒绝；业务独立Flyway历史、重复迁移保留行、缺脚本拒绝、凭据失败保留PENDING/NEEDS_REVIEW、审查后成功激活及重放不重复迁移均通过。迁移只追加新业务位置，不改已发布平台脚本。现有ACTIVE业务库升级仍由维护窗口显式执行对应位置/历史的迁移并validate，不以旧provision回执重放代替升级。

教学样例`examples/shop-storage`已放到独立`p10-sample-snapshot`验证工程（2095文件源码摘要），主线仍不编译业务源码。样例实际宿主/Mapper发现、开通、缓存、幂等与Outbox正在`p10-baseline-sample.log`验证，完成前B3不标完成。

样例首轮结构/缓存边界/模块21项通过，业务启动夹具未显式指定同级admin包的宿主启动类而失败；已添加`@SpringBootTest(classes=SpringbootModulithAdminApplication.class)`，原失败日志保留，复验为`p10-baseline-sample2.log`。示例README补充该二开测试入口要求。

B2小容量边界补验：物理源槽数同时受总字节和maxScopes约束；maxScopes=1时活动源占用期间拒绝新源，释放后可淘汰切换，避免按源放大预算。`p10-baseline-cache-budget.log`13/13、0跳过、BUILD SUCCESS（21:55），对应更新的source-amendment-b2.json。

样例第二轮21项结构通过、业务链1项失败：测试profile默认关闭节点心跳，真实StorageLedgerProvider因没有当前节点租约拒绝访问。已在样例测试显式启用独立节点组、等待真实心跳，保留生产租约检查，并增加会话结束后持久租约释放断言；复验日志`p10-baseline-sample3.log`。两套前端依赖从各自锁文件安装完成，开始固定源码verify；非默认ID引导使用新专用库`sma_test_p10_nondefault_20261112`单独复验，不能复用已产生默认租户的平台库。

前端固定源码门禁通过：`p10-baseline-next-verify.log`完整npm run verify，62文件318单测、类型/测试类型/ESLint/i18n/路由与旧版哈希/生产构建/发布预算均通过（初始168KiB gzip、38懒加载模块、298文件摘要）；`p10-baseline-legacy-verify.log`标准脚本构建、28文件55单测、ESLint与双语校验通过。源码范围见frontend-source-manifest.json；尚未以本轮实际后端完成浏览器登录/品牌/重启验收。

非默认平台引导当前版本复验通过：`p10-baseline-nondefault.log`20/20、0跳过、BUILD SUCCESS（22:04），含真实ID=42首次引导1、架构17、模块2；目录/域名/管理员/权限及ID=1无错误种子均有断言。原命令包含不存在的PlatformTenantTests，未把它计为执行；实际类数/项数以上述报告为准。两套前端及该引导门禁独立于样例修复，先并行验收；完整基线完成仍必须等B3。合并初始与增量清单后2910文件均匹配，verified-input-manifest.json SHA256为6998c3e42232c44e47fec5874f3ef9bb741cff66ea136dd15e6379b914e451be。

样例第三轮节点租约/业务读取通过，但首次写入因夹具未配置请求指纹密钥而拒绝（21结构通过、1错误，p10-baseline-sample3.log）。正式部署仍要求独立随机密钥，未放宽生产校验；样例追加与既有请求测试一致的测试密钥，精确业务测试库清理及订单/代次/Outbox同事务回滚断言，复验为p10-baseline-sample4.log。原基座源码无新增修改，完整根reactor继续使用原已核对快照。

B4真实打包发现必修缺陷：Java21线程工厂禁止继承ThreadLocal时同时丢失Boot宿主上下文类加载器，sma-events异步审计出现LoginLogListener/OperationLogListener ClassNotFoundException。事件线程改为固定宿主类加载器，继续禁止继承请求身份；新增子加载器验收。旧完整轮p10-baseline-full.log在宿主迁移阶段停止，仅终止经Surefire路径确认的本轮测试JVM，失败日志保留，不能计为全量通过。

修复专项p10-baseline-event-loader.log中事件7项、架构17/模块2通过，执行源清单2项中1项失败。人工核对快照：TLS仍是原单线程截止调度，但构造签名和受控callback登记未同步；业务迁移池改到私有List三参重载，仍保留平台授权、无外层事务、单并发/物理锁、2连接和父调用关闭。当前工作树清单及P08说明已有这几项审查，只把对应三处/四条签名同步到冻结清单，未覆盖其他会话清单；复验p10-baseline-event-loader2.log。

首轮真实浏览器p10-baseline-real.log：品牌管理及共享配置2项通过，双前端入口因三场景合计6次同IP/账号密码校验触发5次/5分钟门禁而失败；截图显示明确限流。临时库/账号/进程cleanupComplete=true，旧运行制品f521ba940ba740487dc74afa1e7f30930d7ca8d4cf28fbc836b9ea004c7770b4。修复异步审计后仅组合品牌＋双前端入口（4次登录）复验；原配置通过单列，不提高生产限额，也不把整个首轮标通过。

B3样例最终通过：p10-baseline-sample4.log根Standard入口22/22、0失败/错误/跳过、BUILD SUCCESS（22:26，15:10）。真实宿主发现shopdemo及其Mapper/目录，独立业务schema开通、同库订单/代次/Outbox回滚、提交后缓存失效、原键重放、跨租户隔离、宿主Outbox消费者本地投影、显式权限拒绝、平台无业务表及所有会话持久租约释放均通过。样例源码清单为sample-source-manifest.json加boot、lease-budget、governance增量；该轮未混入随后修复的事件加载器源码，其补丁另由B4事件28项及当前完整/打包验收覆盖。样例无HTTP页面，配置/字典沿公开目录接入说明，不声明完整业务产品。B3完成；B4修正版full2及real2仍在验证。

B4打包/前端闭环通过：p10-baseline-real2.log当前JAR的品牌编辑/上传/预览/发布/回退与双端入口共2/2、0跳过通过；额外只读核对4条成功登录审计、12条成功操作审计，未出现异步ClassNotFoundException，再以schema-mode=validate重启并确认发布品牌和审计行保留。result.json的passed和cleanupComplete均true（22:32），临时进程/数据库/账号已清理。证据在p10-baseline-snapshot/frontend-next/.e2e-runtime/1794493492815/{result.json,baseline-audit-restart.json}；实际JAR SHA256 cc398429f21752a1dcdab621e14e44443892f9695b0f1390147ea57f978310dd。最终修正版2910文件输入清单verified-input-manifest-b4.json SHA256 5199212cc1f8ec8b4c197cb67b1113e7b44ea2fce16276254c9cc76328f622eb；不把品牌/审计重启证明扩大成全部业务搬库或Outbox冷启动自动发现能力。完整full2仍在执行。

B4 full2发现两项失败，保持未完成：MailDispatcherDrainTests再次遇两个单次SKIP LOCKED首次领取不保证同时成功的夹具假设（原B1聚焦通过不足以证明确定性）；StorageOutboxDrainMysqlTests缺独立业务库启用条件，在普通全量被触发后因未设置专用URL拒绝。P08当前已完成邮件夹具顺序建立两个在途的修复，本线读取并采用该夹具，保留两项同时阻塞/真实提交/UNKNOWN/未领取与原10秒门槛。Outbox专项增加显式环境开关，保留loopback:13306与专用库名称白名单，允许任务后缀，不能动P08已有库；本轮创建sma_test_p08_storage_drain_20261112_p10并用独立限库账号复验。修复专项为p10-baseline-drain.log，使用已结束的样例验证工程与专用平台库；仍运行的full2源码/输出保持原值，等待收集完整结果后再更新固定基线。

full2第三类失败为SecurityEventStoreTests：固定平台4条/租户3条/租户总8条容量与前序HTTP/采集测试遗留事件共用，8项中6失败1错误，主要为预期STORED而实际CAPACITY。生产拒绝符合容量规则；夹具追加专用测试库名称核对、JUnit类隔离和每项事务性重置本模块四张事件/容量表，保留小容量、并发唯一、故障回滚、留存/持有与权限断言，不扩大生产预算。当前执行中的full2未换源码，新夹具等待本轮完成后在最终全量验证。

摘流修复专项p10-baseline-drain.log已通过9/9、0跳过、BUILD SUCCESS（22:53），包括实际MySQL邮件摘流1、真实业务库Outbox摘流1及事件线程7。业务Outbox仍验证UNKNOWN/DONE/未领取PENDING/attempts=1,1,0与真实许可结束，未更改生产重试。随后运行p10-baseline-capacity.log，把安全采集MySQL、查询HTTP和容量存储组合验证；基线full2仍继续收集全部失败，正式完成需最终修正版全量。

容量修复组合p10-baseline-capacity.log通过16/16、0跳过、BUILD SUCCESS（23:04）：前序真实采集3、查询HTTP5、容量存储8；固定小容量及全部原断言保留。三类夹具修复均有本轮证据。为与仍在收集后半段结果的full2隔离，从2910文件固定输入另建p10-final-snapshot，仅替换这三个已验证测试文件；最终清单SHA256 731f5037a2320fe8033c0be553d2283330145c4b7adff5abcfa03cc864c921fb。生产源码/资源与通过real2的JAR来源一致。full3使用新专用库sma_test_p10_final_20261112、输出target/final/target和上下文缓存上限8；本机63.7GiB内存/25.4GiB空闲、MySQL连接上限400，测试上下文复用预算已核对，未放宽业务测试断言。full2源码/输出及失败日志继续保留，B4仍待full3最终结果。

full2第四类夹具冲突：RulePlatformTests的真实HTTP场景与先运行的SocketDeploymentMysqlTests都占用全局唯一127.0.0.3，导致18项中1项在登记域名时重复键错误。规则测试改用自有NETWORK_HOST=127.0.0.51，并同步实际HTTP目标，保留域名唯一约束/可信租户/权限与审计断言。正通过p10-baseline-rule-host.log按原先Socket→Rule顺序组合复验。full3仍包含旧夹具，因此在发现后停止经路径核对的本轮JVM并保留日志，不计为完成；等full2剩余扫描及该复验结束后再统一启动最终轮，避免遗漏后续组合问题。

full2完整扫描结束（23:33，01:08h）：根reactor共1465项，7失败/3错误/70跳过；宿主1410项，其他四模块55项。失败准确集中在上述四类夹具，其他模块已跑完，无第五类问题。该轮仍为BUILD FAILURE。新最终轮full4须等待规则域名组合通过后再启动；固定生产代码及已通过的real2制品保持，不复用失败状态作为成功交付。

规则域名组合p10-baseline-rule-host.log 2/2、0跳过、BUILD SUCCESS（23:34）：按SocketDeploymentMysqlTests→规则真实HTTP顺序验证，独立域名保持两方原权限、摘流、网络发布执行与审计断言。四类夹具问题全部完成针对性验证。final快照追加此测试修正，旧full3清单另存；full4最终2910文件清单SHA256为2e359b972ddcfcb435128649ec86dc92475f62421b4c9543c3833ffc35ac6d2e。23:36开始根Standard完整回归，使用sma_test_p10_final_20261112、target/final/target、上下文缓存8；本轮结果为最后完成门槛。

## 最终验收结果（2026-11-13）

full4根reactor已完整通过，00:31:39结束，55:20，所有模块BUILD SUCCESS：共1465项，1394通过、0失败/错误、71条件跳过。宿主1410项/70跳过，协议7、运行时9、发布服务33/1跳过、worker6；跳过不是通过，本基线所需三库/共享Redis/独立引导/Outbox摘流另有上述专项证据。完整规则18项、邮件摘流1项、安全容量8项及持久号段4项均在最终组合中通过。

实际入口：根`tools/dev/verify-backend.ps1 -Standard -MavenArgs @('-Dsma.verify.directory=target/final/target','-Dspring.test.context.cache.maxSize=8')`，固定目录p10-final-snapshot，专用库sma_test_p10_final_20261112，日志p10-baseline-full4.log。源输入清单保持2e359b972ddcfcb435128649ec86dc92475f62421b4c9543c3833ffc35ac6d2e；与real2通过JAR来源的生产代码/资源一致，后续只修复测试夹具。

B1～B4完成，S7d-3/S7完成本地收口，可基于固定版本开展单实例静态业务二开。交付与使用见[基线说明](secondary-development-baseline-release.md)。X2a完成不等于完整X2，专用Redis、SQL运维UI、自动业务资源发现/冷启动恢复、多实例/生产搬库及X5继续按后补计划验收；本地验证不提供生产吞吐或断电持久性证明。
