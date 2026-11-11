# 规则后端 API 与接入约定

当前实现范围与测试状态以 [progress.md](progress.md) 为准；后端验证见 [backend-evidence.md](backend-evidence.md)，新版规则界面验证见 [frontend-evidence.md](frontend-evidence.md)。

## 基本约定

- 后台接口统一 `/api/rule`，使用现有管理员 Bearer 认证、权限注解与 ApiResponse；机器应用令牌不能直接访问。
- 租户和 portal 来自宿主认证，不接受请求伪造。专业管理接口受数据范围约束，模板目录按租户 / portal 提供已发布版本。
- 雪花 ID 在后台响应中沿用全局 Long 字符串约定；前端保留字符串。输入命令中的 ID 可按既有 Jackson 约定解析。
- JSON 命令由模块专用 Jackson 3 解码，启用十进制，拒绝未知属性；不改变其他模块 JSON 行为。金额不先转成 Double 再补救。
- 保存不发布；模拟不写业务数据；试算只更新与当前实例草稿摘要绑定的试算证明。实例草稿变化会使该证明失效。
- 管理写操作走现有操作审计；规则发布另有不可变 release 记录。执行记录只保存版本、状态、摘要、位置追踪和耗时，不记录原始输入或输出值。

## 当前基础接口

| 方法 | 路径 | 权限 / 作用 |
| --- | --- | --- |
| GET | `/functions` | `rule:template:view`；纯函数名称 |
| GET / POST | `/scenes` | `rule:scene:view` / `rule:scene:edit`；场景目录及契约 |
| GET / POST | `/templates` | `rule:template:view` / `rule:template:edit`；模板列表 / 创建 |
| GET | `/templates/{id}` | 模板管理详情及草稿 |
| GET | `/templates/{id}/scene` | `rule:template:view`；按受管理模板查准确场景契约 |
| PUT | `/templates/{id}/draft` | 模板编辑；`expectedRevision` 和 `template` |
| POST | `/templates/{id}/publish` | `rule:template:publish`；运行模板用例后冻结并发布 |
| GET | `/catalog?sceneId=...` | `rule:catalog:view`；该场景可选已发布模板版本 |
| GET | `/catalog/scenes?page=1&size=20` | `rule:catalog:view`；当前租户/portal的场景选择器 |
| GET | `/catalog/entries?sceneId=...` | `rule:catalog:view`；命名目录项 `{code,name,version}`，旧目录结构保留 |
| POST | `/validate` | `rule:simulation:run`；返回 valid / problems |
| POST | `/simulations` | `rule:simulation:run`；指定模板文档与 input / params 试算 |
| GET / POST | `/instances` | `rule:instance:view` / `rule:instance:edit`；实例管理 |
| GET | `/instances/{id}` | 实例管理详情及草稿 |
| GET | `/instances/{id}/template` | `rule:instance:view`；校验实例管理范围后返回草稿所固定的模板版本 |
| PUT | `/instances/{id}/draft` | `rule:instance:edit`；expectedRevision 和 instance |
| POST | `/instances/{id}/trial` | `rule:instance:edit`；expectedRevision 和代表性 input |
| POST | `/instances/{id}/publish` | `rule:instance:publish`；必须先通过本修订试算 |
| POST | `/instances/{id}/disable` | `rule:instance:disable`；停用新执行 |
| GET | `/assets/{id}/versions` | `rule:template:view`；不可变版本，仍校验目标管理范围 |
| POST | `/assets/{id}/rollback` | `rule:release:rollback`；目标版本必须属于同一制品并验证依赖 |
| POST | `/instances/{id}/execute` | `rule:execution:run`；只执行当前已发布实例，正文仅 input |
| GET | `/instances/{id}/executions?limit=20` | `rule:execution:view`；脱敏执行摘要 |

发布 / 停用正文：`{"expectedRevision":1,"operationKey":"unique_operation_001"}`。相同用户、同一动作 / 目标 / 修订 / key 的重试返回原发布结果；key 被用于其他命令返回冲突。发布也提升管理修订号，后续编辑必须重新读取。

回滚另传 `versionId`。发布修订单调递增，回滚选择以前的不可变版本，不覆盖历史内容；草稿保留为当前编辑内容，当前生效指针单独展示。

## 契约结构

实际 JSON 字段以公共 record 为准：`RuleSchema`、`RuleDefinition`、`RuleTemplateDocument`、`RuleCommands`、`RuleExecutionApi`。这些类型不引用 Mapper、持久化实体或第三方引擎对象。

- `RuleSchema.fields`：稳定字段名到 Field；支持 STRING / BOOLEAN / INTEGER / DECIMAL / INSTANT / OBJECT / LIST，必填、默认、范围、精度、枚举、长度、集合项及敏感标记。
- 正式场景的输入 / 参数 Schema 关闭额外属性。模板只能缩小场景允许的参数范围，不能放宽精度、枚举、集合或嵌套限制。
- `RuleTemplateDocument`：formatVersion=1、inputSchema、parameterSchema、outputSchema、definition、parameterUi、examples。
- `parameterUi` 只含 label / help / group / order 等声明式元数据；它不执行 HTML、JavaScript 或远程资源。
- `examples` 每项包括 name / input / params / expectedOutput。模板发布必须有 1～50 个真实可运行断言；改变逻辑、契约或表单形成新草稿。
- 实例包括固定 templateVersionId、params、objectType / objectId、environment、startAt / endAt。时间使用 UTC，生效区间为半开区间。

表达式示例：`round(input.amount * params.ratio, 2)`。运行 input 来自当次业务快照；params 来自已发布实例；context 由后端提供，目前有 evaluatedAt。不可把请求中的参数作为实例 params 覆盖。

## 业务模块接入

业务模块通过根包 `RuleExecutionApi` 调用 `execute(Request)`，只传实例 ID 和类型化数据快照，不跨规则表查询。业务模块根据结果执行自身的持久化或其他动作。

业务对象绑定需要归属模块实现 `common :: security` 下的 `RuleBindingPolicy`。默认没有任何业务对象授权，不能仅靠传入对象 ID 建立绑定；宿主实现必须根据当前用户与可信对象归属判断。当前集成测试的 TEST_OBJECT 授权只存在于测试配置，不进入生产代码，也不代表已有商品 / 活动模块完成接入。

## 执行和失败

结果包含 executionId、status、output、instanceRevision、releaseRevision、templateVersionId、contentHash、evaluatedAt、trace、problems。releaseRevision 是发布 / 停用 / 回滚次数，不等于草稿修订或不可变内容修订；模拟时为空。

SUCCESS 与 NOT_MATCHED 是正常计算结果；INVALID_INPUT、NOT_CONFIGURED、DISABLED、NOT_EFFECTIVE、TIMEOUT、ERROR 单独区分。不存在或无权访问的资源按管理接口错误 / 授权协议处理，不能用“无优惠”掩盖故障。

追踪不保留中间原值，短路未执行条件明确为 SKIPPED。错误定位提供稳定 path / code / line / column；模拟与线上使用同一执行语义。

表达式开放一个受限 QLExpress 4 方言及 min / max / abs / round / size / contains / intervalValue 纯函数，禁止循环、赋值、声明、lambda、Java 类 / 方法和宿主 Bean。原生超时不能强制终止所有脚本，实际失败证据与资源边界见验证记录。

## 流程后端

FLOW 文档由 startNode 和 nodes 定义，稳定节点 ID 仅接受受限标识符。节点支持 START、EXPRESSION、CONDITION、SWITCH、SUBRULE、TABLE、RESULT；图模型不依赖 Vue Flow，后续画布只需映射节点和连线。

- START / EXPRESSION / SUBRULE 使用 next；CONDITION 使用 onTrue / onFalse；RESULT 终止流程；SWITCH 使用 cases 和必填的默认 next。
- EXPRESSION 声明输出 Schema；CONDITION 使用布尔表达式或条件树，不能同时提供两者。SWITCH 表达式返回字符串，cases 的键是预配置选择值，不匹配时走默认出口。
- SUBRULE 使用固定 subRuleVersionId，分别声明 inputBindings / parameterBindings；映射值为受控表达式。子规则输出契约必须与节点声明一致，跨租户、不可用版本和循环依赖被拒绝。
- 前置节点结果通过 context.steps.<节点ID> 引用；普通属性路径会校验是否在全部前置分支上都已生成，避免汇合点使用仅在某一分支存在的值。
- 流程转换为后端生成的受限 LiteFlow EL；不接受直接上传 EL。只有当前图校验通过后才装载计划。
- 计划缓存按租户和内容摘要隔离，使用请求租约，执行中的计划不会被缓存淘汰；数据上下文只属于当前请求。节点追踪区分执行和跳过，错误带节点路径及表达式位置。
- 限制：最多 64 个图节点、图深度 32、选择分支 16、子规则深度 8、单次嵌套执行预算 256 步；不开放循环、共享变量并行或业务写入动作。

实际测试状态见进度台账。B2 专项与数据库发布回归分别记录，新增能力不会被当成前端画布已完成。

## 持久化设计

当前将 SCENE / TEMPLATE / INSTANCE 作为严格区分的制品类型保存在 `rule_asset`，通过 `rule_version`、`rule_release` 保存不可变内容和发布修订；运行记录在 `rule_execution`。这对应 P06 的逻辑对象模型，不要求为每种制品重复建一套相同版本表。

所有表带 tenant_id 和项目审计字段。变更通过模块 Flyway 管理；数据库唯一约束与租户写锁确保并发修订和发布幂等。每次执行固定当前实例版本及模板依赖；模板升级不自动重绑已有实例。

## 决策表、区间参数和迁移

- DECISION_TABLE 包括 hitPolicy=UNIQUE / FIRST、inputs、rows、defaultOutput。UNIQUE 检出多行命中时失败；FIRST 采用配置顺序；ANY 单元格必须显式声明。输出为 outputSchema 中的 OBJECT 属性，输出表达式使用相同受控内核。
- TABLE 流程节点使用固定决策表版本、显式输入 / 参数绑定和输出 Schema；不接受任意 SQL 或脚本。
- intervalConstraints 声明列表参数的 lower / upper 字段、可空末上界、连续要求及上下界；区间为 `[lower,upper)`，拒绝重叠、逆序和不允许的缺口。intervalValue(list,value,lowerKey,upperKey,resultKey,default) 只读取经过约束的区间值。

| 方法 | 路径 | 权限与契约 |
| --- | --- | --- |
| POST | `/templates/{id}/copy` | template:edit；code / name / 可选 versionId；创建独立草稿 |
| GET | `/assets/{id}/diff?fromVersionId=...&toVersionId=...` | template:view；最多128处差异，只返回路径与类型 |
| GET | `/templates/{id}/instances?page=1&size=20` | template:view；直接引用该模板的草稿或生效实例 |
| POST | `/instances/{id}/upgrade-preview` | instance:edit；expectedRevision / templateVersionId / 可选 params / sampleInputs（1～20）；对比相同样本 |
| POST | `/instances/{id}/upgrade-draft` | instance:edit；与预览同契约；仅生成新草稿 |

以上权限均加 `rule:` 前缀。升级必须保持场景一致、参数约束通过、样本正常计算；输出差异由调用方审核，不自动认定差异不可接受。新草稿仍需试算和发布。

## 测试治理与批量迁移

| 方法 | 路径 | 权限与契约 |
| --- | --- | --- |
| GET / POST | `/templates/{templateId}/testcases` | rule:test:view / edit；新增正文为 CaseSave，expectedRevision=0 |
| PUT | `/templates/{templateId}/testcases/{caseId}` | rule:test:edit；CaseSave 包含 expectedRevision 与 testcase |
| GET / POST | `/templates/{templateId}/test-runs` | rule:test:view / run；批跑正文 versionId / 可选 compareVersionId |
| POST | `/migration-batches` | rule:batch:migrate；operationKey / items，每项 instanceId 和 migration |
| GET | `/migration-batches/{id}` | rule:batch:view；受租户、portal、原提交者 / ALL 范围控制 |

CaseInput 包含 name / input / params / expectedStatus / expectedOutput / expectedProblemCode / enabled / evaluatedAt；可停用用例。默认 evaluatedAt 固定为 2026-01-01T00:00:00Z，时间规则需要显式指定测试时间。每模板最多100个保存用例，发布还必须有1～50个文档断言。批跑和发布使用同一执行路径，报告包含固定内容摘要及用例集摘要，不保存输入输出原值。版本对比返回 changed，不自动修改断言。

发布执行全部启用用例并绑定 testRunId / caseSetHash；失败报告在独立事务保留，原发布事务回滚。新用例不会追溯改变既有版本证明；再次发布生成新的证明。

批量迁移最多50项，不能重复实例；每项迁移与回执同事务提交。结果区分 COMPLETED、COMPLETED_WITH_ERRORS，返回逐项结果及未完成ID；相同调用者以同 key 和同规范请求重试返回原回执。进程中断后的 RUNNING 批次返回 RECOVERY_REQUIRED，不能作为成功或自动重放；需要核对已完成回执，针对未完成项读取最新修订后提交新 key。该能力只迁移草稿。

## 执行运维与生命周期

| 方法 | 路径 | 权限与契约 |
| --- | --- | --- |
| GET | `/instances/{id}/executions/{executionId}` | rule:execution:view；校验实例管理范围，返回脱敏追踪和诊断 |
| POST | `/executions/search` | rule:execution:view；ExecutionSearch：可选 instanceId / templateId / templateVersionId / status，必填 from / until / page / size；时间窗最长31天，每页1～100 |
| GET | `/instances/{id}/execution-statistics?from=...&until=...` | rule:execution:view；UTC半开时间窗，最长31天；按状态计数、平均 / 最大耗时微秒 |
| GET | `/resources` | rule:maintenance:view + ALL；本进程当前租户表达式与流程缓存数量及租户上限 |
| POST | `/resources/clear` | rule:maintenance:clean + ALL；清理当前租户表达式及空闲流程计划，保留在途租约 |
| POST | `/retention/cleanup` | rule:maintenance:clean + ALL；days（7～3650）/ limit（1～500，每表）；物理清理当前租户 / portal 过期执行及未被版本引用的测试报告 |
| POST | `/templates/{id}/deprecate` | rule:template:lifecycle；Publish 正文；禁止新选用，已有实例继续 |
| POST | `/templates/{id}/revoke` | rule:template:lifecycle；Publish 正文；阻止后续直接及间接执行 |
| GET | `/templates/{id}/dependency-impact` | rule:template:view；直接 / 传递依赖的可管理模板和实例，包含草稿及当前生效版本 |

影响查询每次扫描最多500个可管理资产，每个根最多64个依赖、深度8；遇容量、损坏或不可用版本返回 truncated=true，不能据不完整报告判断无影响。它不跨租户 / portal 展示资源。

执行检索返回摘要，不批量载入追踪正文；数据范围按实例归属校验，即使某个执行调用者不同也不能扩大可见实例。资源返回 expressionAtLimit / planAtLimit（0或1），供宿主轮询显示容量提示；本轮没有额外告警通知通道。状态统计结果可计算错误率，不生成按实例ID展开的全局高基数指标标签。

已授权且定位成功的停用、未配置、未生效、撤销及计算错误也写执行记录。不存在或绑定无权的请求不暴露执行状态。维护清理不删除版本、发布操作、用例或批量幂等回执；发布证明始终保留。没有后台自动清理任务，运维按保留策略主动调用，清理结果应纳入审计。
