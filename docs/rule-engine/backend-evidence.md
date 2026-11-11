# 规则引擎后端验证记录

## 最终验收结果

- 2026-11-11 04:52:37+08:00，`backend/mvnw.cmd -B -ntp -f pom.xml -pl backend -am -Dsma.verify.directory=<repo>/backend/target/rule-verification/target verify` 完整成功，含后端打包。
- 后端160项，依赖模块15项，共175项，失败0、错误0、跳过0；59个测试类。规则专项51项（平台17、表达式/契约13、流程8、决策表7、区间3、容量2、LiteFlow兼容1），另有架构14、语言包3、Modulith2及全部既有后端回归。
- 日志 `backend/target/rule-backend-verify.log`；报告 `backend/target/rule-verification/target/surefire-reports/`。源码64文件在最终编译到验收记录期间保持一致。源码/日志/制品SHA256、逐套件统计与容量样本已固化在 [verification.json](verification.json)，避免只引用会被下一轮构建覆盖的target报告。
- 最后审查补强的发布绑定快照、按模板/版本/状态检索、停用保留原版本、NONE数据范围拒绝批次读写、遗留RUNNING批次不重放均通过。
- 在途计划清理专项验证：请求持有租约时清理不移除链，执行结束后能清理。宽值图、循环Java容器、展开Schema总预算及浮点金额拒绝通过。
- 本机纯计算：每项100个样本；表达式p50/p95为83/133微秒，简单流程485/852微秒，200行决策表2458/3063微秒；首次源码编译247/473微秒，紧随缓存命中22/53微秒。真实认证HTTP+MySQL执行单样本377655微秒，功能验证用途。两组数据范围不同，不作生产QPS或SLA承诺。
- 最终范围是backend及reactor依赖，不包含独立extension-security / extension-worker服务；没有规则前端验收、真实商品模块接入或生产恢复演练。后续验收见 [operations.md](operations.md)。

## B4：用例治理与批量迁移

- `backend/target/rule-governance-tests.log`：63 项测试通过，失败 0、错误 0、跳过 0；其中 RulePlatformTests 14 项。
- 验证已保存正例 / 负例、用例修订摘要、固定版本内容摘要、失败报告保留，以及失败用例阻止发布且不改变原生效版本。
- 发布证明记录 testRunId / caseSetHash；发布门禁使用独立报告事务，避免失败回滚丢失诊断。
- 批量迁移逐项事务包含草稿与回执；部分成功明确返回 COMPLETED_WITH_ERRORS，同 operationKey 重试不再修改成功项，变更请求重用 key 返回冲突。旧生效版本继续执行。
- 上述完成状态不涵盖随后增加的执行治理与生命周期；对应结果另记。

## B4：执行治理与生命周期

- `backend/target/rule-operations-tests.log`：66项通过，失败 / 错误 / 跳过均0；平台集成16项。包含V1～V4真实MySQL迁移。
- 执行详情、状态统计、独立发布修订、停用与未生效记录、保留清理最小天数与ALL范围检查通过；过期且被版本引用的发布报告没有被删除，未引用报告与过期执行按规则删除。
- 废弃重试幂等，新实例拒绝，已有直接 / 间接实例继续；撤销后直接及父流程失败，不替换模板。影响清单包含父模板与间接实例。手工测试夹具破坏版本摘要后执行失败，恢复摘要后保留原记录。
- 后续新增绑定快照回归、执行检索、容量测量与在途租约维护，最终以全量verify为准。

## 收尾失败记录

- 首次全量verify的容量测试编译遇到 `java.util.concurrent.Flow` 与规则 `Flow` 的通配导入歧义；已改为明确并发类导入。失败日志 `backend/target/rule-backend-verify-compile-failure.log`，不计通过。
- 隔离库全量测试暴露两个既有测试硬编码默认数据库名：ApplicationSmokeTests、DatabaseDocTests。改为与已配置测试URL的库名精确比较，同时保留 `springboot_modulith_admin_test|sma_test_[a-z0-9_]+` 白名单；TestDatabase.clean 的安全保护不变。不改用共享默认测试库规避失败。

## 2026-11-11：B0 内核与契约

- 运行环境：JDK 21.0.13，Spring Boot 4.1.2，Spring Modulith 2.1.2，Jackson 3；以本仓库 Maven Wrapper 构建。
- 发行依赖：`com.alibaba:qlexpress4:4.1.4`、`com.yomahub:liteflow-core:2.16.3`。Maven Central 对应发行 POM 的许可证分别写明 Apache 2 / Apache-2.0，指向 Apache 2.0 正文；未引入第三方设计器或脚本插件。
- 源码 API 从同版本 sources.jar 核对；QLExpress 使用 v4 的隔离策略、精度、可序列化预编译计划、追踪及参数化执行，不以 v3 API 冒充已兼容。
- `backend/mvnw.cmd -B -ntp -f pom.xml -pl backend -am -Dtest=RuleEngineTests,LiteFlowCompatibilityTests,ArchitectureRulesTests,ModularityTests,I18nBundlesTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过。
- 结果：RuleEngineTests 12、LiteFlowCompatibilityTests 1、ArchitectureRulesTests 14、ModularityTests 2、I18nBundlesTests 3，共 32 项，失败 0、错误 0、跳过 0。构建日志 `backend/target/rule-foundation-tests.log`；详细 XML 在 `backend/target/surefire-reports/`。
- 覆盖：十进制计算、纯函数、结构化结果、短路及未求值追踪、非法语法和能力、输入 / 参数快照、缓存全局及单租户上限、字段 / 默认值 / 范围、条件分组和数值比较、两个 LiteFlow 版本链路调用 Java 表达式节点。

### 实际发现与限制

对原生 QLExpress 执行 `while(true) {}`，配置 `timeoutMillis(10)` 后未及时结束。通过 jcmd 确认测试线程停留在 QLExpress WhileInstruction，超过约 80 秒仍在运行；核对父进程和测试命令后只终止了本次 surefire JVM。失败日志保留于 `backend/target/rule-native-timeout-failure.log`。

因此平台不开放通用脚本：使用 AST 允许列表，在执行前拒绝循环、声明、赋值、lambda、类构造与方法调用；限制源码、嵌套、数值、集合、结果和缓存。原生超时只是附加保护，不能宣称为任意 Java / 脚本的硬中止能力。回归验证平台拒绝循环，而非将未通过的原生测试标记成功。

### 证据范围

B0 为真实库的本地编译和内核 / 架构测试，尚不等于规则管理 HTTP / MySQL 全流程验证。后续 B1 的持久化、发布、租户 / 权限及 API 结果另行记录。规则前端仍等待 next 框架验收，未安装编辑器或实现页面。

## B1：首个后端管理闭环

- 独立数据库：`sma_test_rule`，符合现有测试库名称安全检查；未清理开发 / 生产库。
- 构建隔离：后端 POM 增加按 `sma.verify.directory` 激活的可选输出目录；默认构建行为不变。本次使用 `backend/target/rule-verification/target`，保留末级 target 以兼容架构工具识别测试目录。
- 实际发现：另一次 mvnd 构建在本次测试期间重写默认 test-classes，造成测试类文件短暂不可读；采用独立目录后不再共享输出。不停止其他构建或放宽架构规则。
- 内核与项目门禁：`rule-isolated-architecture.log` 记录 32 项全部通过（14 架构、3 i18n、2 模块、1 LiteFlow、12 核心）。
- 平台集成：`rule-m1-final-integration.log` 记录 RulePlatformTests 10 项全部通过，失败 0、错误 0、跳过 0。详细报告位于 `backend/target/rule-verification/target/surefire-reports/`。
- 集成覆盖：真实 MySQL 迁移、模板断言门禁、模板升级不重绑旧实例、独立参数、参数策略精度上限、实例试算、草稿 / 生效分离、回滚、停用、重复发布幂等、跨租户 / portal / 数据范围、绑定授权及有效期。
- 并发覆盖：两个线程编辑同一修订，恰好一个保存成功，另一方得到冲突，未发生静默覆盖。
- HTTP 证据：随机端口嵌入服务器、真实持久用户 / 角色 / Bearer 令牌，Java HttpClient 完成创建场景 → 创建 / 发布模板 → 创建 / 试算 / 发布实例 → 执行；检查实际审计落库。不是仅以 MockMvc 代替网络联调。
- 业务范围：绑定授权提供 SPI，并以测试专用 TEST_OBJECT 实现验证；生产尚无商品 / 活动模块接入，不将本次平台验证称为促销业务已上线。前端仍全部等待 next 框架最终验收。

## B2：流程执行后端

- `rule-m2-verification.log`：49 项全部通过（14 架构、3 i18n、2 模块、1 兼容、12 核心、6 流程、11 平台集成），失败 0、错误 0、跳过 0。
- 流程集成用例在真实 MySQL 上发布带固定版本子规则的模板，创建 / 试算 / 发布实例，执行后验证正确金额、子规则追踪与未执行分支。
- 补充 `rule-flow-selection-tests.log`：7 项流程专项通过，包括真实 LiteFlow SWITCH 分支与默认出口、结构校验、分支汇合值可用性、固定子规则映射、错误节点定位、32 个并发请求的步骤值隔离。
- 图和计划有界，编排链按租户与内容摘要命名；缓存租约保护在途链路，运行数据不保存在共享计划里。不接受用户上传原始 EL。
- 期间扩展模块在其他工作中发生一次临时编译错误；重新检查时源码已修正，未改写该模块，再次构建后流程专项通过。该次失败未计入成功检查。
- 本期仅实现后端图模型、执行计划及 API 文档；没有实现 Vue Flow 画布。

## B3：决策表、参数和迁移

- `rule-m3-verification.log`：62 项全部通过，失败 / 错误 / 跳过均为 0。包括 19 项架构 / 模块 / i18n、31 项内核 / 流程 / 表格 / 参数、12 项平台集成。
- 决策表覆盖 UNIQUE 多命中冲突、FIRST 顺序、短路与未执行行、明确默认输出、通配符、日期比较、未触发行的非法单元格类型拒绝。
- 流程可通过固定 TABLE 节点调用已发布决策表，真实 MySQL 模板 / 实例发布执行测试通过。
- 阶梯参数以 Schema 和有界区间约束校验；纯函数 intervalValue 使用半开区间，验证重叠、缺口、顺序、无上界末档及金额边界。
- 兼容测试发现 Jackson 3 对缺省原始布尔参数的严格行为；Schema 标记改为明确规范化默认值，旧简写 JSON 可执行，未关闭全局校验。
- 模板复制创建独立草稿、不自动发布；版本差异、受权限保护的影响查询和同输入升级对比通过。升级保存新草稿后旧实例仍返回原结果，只有重新试算 / 发布后才切换。
- 前端仍未实施。
