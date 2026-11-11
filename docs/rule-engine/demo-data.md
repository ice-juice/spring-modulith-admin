# 当前后台示例数据

用户于2026-11-11明确授权在当前服务创建场景、模板、目录和实例，并确认使用受限演示对象。通过正常后台会话创建，不重置账号、不读取用户令牌、不直接写表。目录沿用已发布模板投影，不建立另一套目录表。

## 创建计划

| 顺序 | 任务 | 验收与状态 |
| --- | --- | --- |
| S1 | 默认关闭的演示对象绑定适配器，仅固定demo_rule场景和RULE_DEMO类型；对象限当前租户及固定ID，拒绝匿名/机器/无规则权限身份 | 已完成：43项专项通过；18:29:32本地最小补丁上线，PID9444，后端及5174代理健康UP；已登录会话正常创建首个演示实例 |
| S2 | 当前后台创建4个场景和4个模板，包含四类编辑方式与12条发布断言；数据录入可与S1测试独立推进 | 已完成：通过用户登录的当前后台创建，4模板均已发布，12条发布断言全部通过；目录读取已发布固定版本 |
| S3 | 创建并试算发布5个演示实例，覆盖比例折扣、会员资格、满减和阶梯优惠，依赖S1和S2 | 已完成：5实例均试算通过并发布，9次生效版本执行覆盖主要分支；14项浏览器输出核对通过，9条版本及发布回执完整、摘要一致 |

| 场景编码 | 场景与模板 | 演示对象尾缀 |
| --- | --- | --- |
| demo_rule_ratio | 比例折扣（表达式），普通/会员两种实例参数 | ratio_a / ratio_b |
| demo_rule_eligibility | 会员资格（条件组） | eligibility_a |
| demo_rule_reduction | 满额减免（流程） | reduction_a |
| demo_rule_tiers | 阶梯优惠（决策表） | tiers_a |

绑定配置：`sma.rule.demo.enabled`默认false，部署环境变量`SMA_RULE_DEMO_ENABLED=true`显式启用；仅当前演示服务启用。对象类型为`RULE_DEMO`，对象标识为`demo:<可信租户ID>:<尾缀>`，没有真实商品、订单、会员资料。场景和对象前后缀均为精确白名单，不接受任意前缀或客户端tenantId。不能以演示模式授权真实业务对象，真实业务仍实现自己的RuleBindingPolicy。

适用路径是既有rule内部service、测试、配置示例和本说明，不改模块/目录架构或数据库。启用/禁用绑定配置需重启，禁用后演示实例后续授权检查拒绝；已发布内容及审计保留。账号登录由用户本人完成。实际结果随后逐项更新。

## 当前后台清单

租户1，后台入口 `http://127.0.0.1:5174/rule/scenes`。全部名称以【演示】标识；场景是契约记录，无独立发布流程，显示DRAFT正常。模板通过发布断言后进入目录，目录不是另一套可写数据表。

| 场景 | 场景ID | 模板ID | 已发布模板版本 |
| --- | --- | --- | --- |
| 商品比例折扣 | 2120457429417230337 | 2120458854046138370 | 2120459046954762241 |
| 会员资格判断 | 2120457629842046978 | 2120459867557756930 | 2120459984926965761 |
| 满额减免流程 | 2120457991420411906 | 2120460277580333058 | 2120460438423502849 |
| 阶梯优惠决策表 | 2120458139026358273 | 2120460883602735106 | 2120461142601007106 |

模板编码依次为 `demo_rule_ratio_template`、`demo_rule_eligibility_template`、`demo_rule_reduction_template`、`demo_rule_tiers_template`。每个版本保存3条内置发布断言，真实后台报告均为passed=true，共12条。会员资格的两条未命中用例属于预期结果，不能将NOT_MATCHED误判为失败。

### 已发布实例

入口 `http://127.0.0.1:5174/rule/instances`，5个实例均为PUBLISHED，管理修订2、发布修订1，环境default、生效/失效时间留空；参数及绑定来自正常后台表单，固定依赖上表已发布模板版本。

| 名称 / 编码 | 实例ID | 固定参数 | 演示对象标识 |
| --- | --- | --- | --- |
| 普通商品九折 / demo_rule_ratio_a | 2120464001321185281 | ratio=0.9 | demo:1:ratio_a |
| 会员商品八折 / demo_rule_ratio_b | 2120464140316225537 | ratio=0.8 | demo:1:ratio_b |
| 会员满百资格 / demo_rule_eligibility_a | 2120464233119395842 | threshold=100 | demo:1:eligibility_a |
| 商品满百减十 / demo_rule_reduction_a | 2120464317051613185 | threshold=100，reduction=10 | demo:1:reduction_a |
| 商品阶梯优惠 / demo_rule_tiers_a | 2120464405182328834 | highThreshold=200，lowThreshold=100，highRatio=0.8，lowRatio=0.9 | demo:1:tiers_a |

每个名称在后台带【演示】前缀。所有对象类型为RULE_DEMO，仅为适配器识别的虚拟演示对象，不创建或代表真实商品、会员或订单。

| 实例与输入 | 浏览器实际输出 | 状态 |
| --- | --- | --- |
| 九折：amount=300.15 | 270.14 | SUCCESS |
| 八折：amount=300.15 | 240.12 | SUCCESS |
| 会员资格：amount=150，member=true | true | SUCCESS |
| 会员资格：amount=150，member=false | false | NOT_MATCHED（预期） |
| 满减：amount=150 | 140.00 | SUCCESS |
| 满减：amount=99 | 99 | SUCCESS |
| 阶梯高档：amount=250 | payable=200.00，rate=0.8 | SUCCESS |
| 阶梯低档：amount=150 | payable=135.00，rate=0.9 | SUCCESS |
| 阶梯原价：amount=50 | payable=50，rate=1 | SUCCESS |

5项发布前试算与上表9项已发布执行均核对实际输出。页面期间发生并行前端热更新，首次阶梯试算后详情面板重置，重新读取页面并再次试算后才发布，未重复创建或发布实例。发布后已重新读取并验证4模板＋5实例的9条生效版本摘要与9条PUBLISH回执。

## 本地部署边界

当前运行制品与其他并行开发中的工作区版本不同。本次为当前本地开发服务制作最小补丁：复制原运行JAR，仅新增 `RuleDemoBindingPolicy.class`，逐项比较原1037个ZIP条目内容全部相同；不夹带工作区其他模块、配置或迁移。通过进程环境显式开启演示适配器。源码与默认关闭配置同时保留，后续标准完整构建会包含该适配器。本次不等同于完整工作区发行或生产恢复演练。

原JAR、数据库一致性备份、条目比较、部署及回退脚本位于当前工作区 `backend/target/rule-demo-deployment/20261111-181049/` 及 `backend/target/rule-demo-*.ps1`，仅当前用户和SYSTEM可访问备份目录。备份含敏感业务数据，不能提交仓库。部署不改账号、既有权限、密钥和数据库结构，回退仅恢复原制品并关闭演示配置，保留本次示例数据与审计。

## 验证记录（持续更新）

专用可清空测试库为 `sma_test_rule`，隔离输出为 `backend/target/rule-demo/target`。根reactor标准入口加专项筛选：`RuleDemoBindingPolicyTests,RuleDemoDataTests,RulePlatformTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests`。

首轮演示适配器4项、MySQL全流程1项、架构16项、模块2项、多语言3项通过；旧规则HTTP测试因未导入现有 `TenantMockMvcConfiguration` 而被新增租户入口校验拒绝，17项规则回归中1项失败。仅修正该测试导入，保持生产域名和权限规则，重跑于2026-11-11 18:28:31+08:00完成，43项全部通过，失败/错误/跳过均0，BUILD SUCCESS、退出码0。首轮及重跑日志分别为 `backend/target/rule-demo-tests.log` 和 `backend/target/rule-demo-tests-recheck.log`。

实际命令（仓库根目录，专用测试库连接和授权已通过环境提供，不在文档记录密码）：

```powershell
$ruleDemoOutput = Join-Path (Get-Location) 'backend/target/rule-demo/target'
& tools/dev/verify-backend.ps1 -Standard -MavenArgs @(
  '-pl', 'backend', '-am', "-Dsma.verify.directory=$ruleDemoOutput",
  '-Dtest=RuleDemoBindingPolicyTests,RuleDemoDataTests,RulePlatformTests,ModularityTests,ArchitectureRulesTests,I18nBundlesTests',
  '-Dsurefire.failIfNoSpecifiedTests=false'
) -LogPath (Join-Path (Get-Location) 'backend/target/rule-demo-tests-recheck.log')
```

这是43项专项验证，不替代整个并行工作区的全部后端测试。没有更改前端源码，本轮前端验收通过当前已登录后台实际创建、目录读取、实例试算及执行完成。

最终数据库、模板发布断言、版本依赖、9次执行状态及部署检查见[数据验证JSON](demo-data-verification.json)；5项试算、9项执行的浏览器实际输出及4个目录快照见[浏览器验证JSON](demo-data-browser-verification.json)。后端及5174代理均健康UP，密钥摘要保留。演示绑定仅在本次后端进程显式开启；以后通过其他启动脚本重启，需要显式保留开关并使用已包含适配器的制品，默认配置仍然关闭。
