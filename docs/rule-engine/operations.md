# 规则引擎运行、验证与恢复

本规程适用于规则后端；新版规则管理界面及验收状态见 [进度台账](progress.md) 与 [前端运行说明](../../frontend-next/README.md#规则界面)。单进程本地缓存、同步有界执行；未提供多副本任务协调、硬进程隔离或跨数据中心容灾。

## 运行前检查

1. 使用 JDK21+、仓库 Maven Wrapper；运行完整后端构建，部署生成的后端 jar。规则模块随应用启动，由现有模块 Flyway 执行 V1～V4，不手工建另一套同名表。
2. 按业务环境设置 `sma.rule.environment`（默认 `default`）；实例环境必须匹配。时间输入统一 UTC，生效区间为 `[startAt,endAt)`。
3. 通过现有 IAM 分配 `rule:*` 逻辑权限；清理资源需对应权限及 ALL 数据范围。新版规则菜单沿用相应查看权限；旧前端不提供规则页面。
4. 业务归属模块实现 `RuleBindingPolicy` 后再建立真实业务对象绑定。没有默认放行；TEST_OBJECT 仅是测试夹具。业务通过 `RuleExecutionApi` 传可信快照，依据 SUCCESS / NOT_MATCHED 与错误状态决定业务后续动作。
5. 专业人员建立场景与模板，准备1～50个文档断言及保存用例；发布模板后创建实例、试算、发布。变更生效版本必须经过发布或回滚动作，不直接改数据库指针。

## 可重复后端验证

以下命令从仓库根目录执行；需要本机 MySQL 和已授权的专用测试账号。`sma_test_rule` 是可清空测试库，测试开始会清空它，不能指向开发 / 生产库；测试安全校验仅允许既定测试库命名。账号通过已有 `SMA_TEST_DB_USERNAME` / `SMA_TEST_DB_PASSWORD` 环境变量提供，不写入命令或文档。

```powershell
$env:JAVA_HOME = $env:JAVA21_HOME
$env:SMA_TEST_DB_URL = 'jdbc:mysql://127.0.0.1:3306/sma_test_rule?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true'
$ruleOutput = Join-Path (Get-Location) 'backend/target/rule-verification/target'
& backend/mvnw.cmd -B -ntp -f pom.xml -pl backend -am "-Dsma.verify.directory=$ruleOutput" verify *> backend/target/rule-backend-verify.log
$LASTEXITCODE
```

并行任务不得共享该验证目录或测试库。隔离路径末级保持 `target`，使 ArchUnit / Modulith 正确识别测试类。命令执行完检查退出码、BUILD SUCCESS、测试失败 / 错误 / 跳过数，以及 surefire XML；不能只以编译完成判断通过。

本机性能样本在 `backend/target/rule-verification/target/rule-capacity-samples.json`。测试进行20次预热、100次顺序测量，含契约校验；表达式、简单流程和200行决策表分别记录 p50 / p95 / 最大微秒。它不含数据库、网络、租户争用或多核并发压力，不是生产吞吐量或延迟承诺。

同一文件另记录100个首次源码编译与紧随其后的缓存命中耗时；JVM及类加载已启动，不能当作应用冷启动耗时。

## 资源与保留策略

- 表达式计划：全局256、单租户64；流程计划：全局128、单租户32。在途流程使用租约，清理只移除空闲计划，容量被在途计划占满时返回明确失败。
- 单文档最大131072字符；报告最大1048576字符；值图最多4096节点、深度10；单字符串、集合、数值还有独立约束。流程64节点、图深度32、子规则深度8、每次嵌套执行256步；决策表最多16输入列、200行；用例100个、文档断言50个、单迁移批次50项。
- 执行详情不保存输入输出原值；模板文档、参数和测试用例本身是受权限保护的业务配置，不应存储凭证。日志保留状态、版本、摘要、追踪位置与耗时。
- 通过实例统计接口观察状态分布与平均 / 最大耗时，窗口最长31天。与业务关联时使用 executionId、instanceRevision、releaseRevision 和 templateVersionId，不能只看当前模板名称。
- 运维主动调用保留清理：至少保留7天，每表每次最多500条；只删除当前租户 / portal 的过期执行与未被版本引用的报告。版本、发布操作、批次回执及发布证明不能由该接口删除。重复清理直到返回0需要运维明确安排，平台没有自动定时任务。

## 备份与恢复步骤

以下为运行规程；本轮未进行生产备份、停机或恢复演练。正式上线前应在隔离环境完成整库恢复演练并补充组织的 RPO / RTO。

1. 通过现有 MySQL 备份体系做一致性快照，记录应用版本、配置环境及 Flyway 历史。建议备份整个业务库，保证规则、IAM / 租户 / 业务绑定与审计保持同一时间点。只做规则模块备份时，必须同时保存 `rule_asset`、`rule_version`、`rule_release`、`rule_execution`、`rule_testcase`、`rule_testrun`、`rule_migration_batch`、`rule_write_lock` 和 `flyway_schema_history_rule`；不要丢弃发布幂等回执。
2. 在独立数据库恢复快照，先验证 Flyway 校验和与对应应用版本；不要用空表迁移覆盖已有版本。逐租户检查 asset.active_version_id 指向同资产的不可变版本、INSTANCE 版本依赖指向同租户 / portal 的 TEMPLATE 版本、test_run_id 指向对应发布证明。
3. 对 `rule_version` 检查 `content_hash = SHA2(content_json,256)`，逐字节保留原始 JSON，不能重新排版后保留旧摘要。对恢复版本运行保存用例及典型模拟，确认权限 / 绑定和有效期后再恢复业务流量。完整性失败应停止受影响规则的新执行，不手工改摘要掩盖差异。
4. 缓存不需要恢复，应用按租户与摘要重新编译。不要复制进程内 live 标记。
5. 对遗留 RUNNING 批次读取回执：重启后显示 RECOVERY_REQUIRED。已完成草稿与逐项回执同事务，成功项不重放；核对 pendingInstanceIds，重新读取未完成实例修订后用新 operationKey 建立剩余迁移。迁移仍需逐实例试算 / 发布。
6. 功能性回退优先使用 release rollback，形成新的发布修订与审计，保留当前草稿和历史版本。REVOKED 是阻断状态，不接受直接回滚恢复；确认后复制为新模板、重新测试发布。废弃 / 撤销只约束后续请求，不取消已经开始的计算。

## 已知边界

用户授权的后台演示数据及受限对象适配器见[演示数据清单](demo-data.md)。`SMA_RULE_DEMO_ENABLED=true`为启动开关，默认关闭；修改后需重启，不能通过规则参数或客户端租户字段开启。关闭后演示实例的后续绑定授权拒绝，既有规则配置、版本和审计仍保留；真实业务对象必须使用所属业务的绑定策略。

QLExpress 原生 timeout 未能终止所有无限循环，平台在解析阶段拒绝循环和通用脚本能力；原生超时不是硬终止沙箱。仅运行受限表达式，不开放 Java Bean、类、方法调用、任意 LiteFlow EL 或业务写动作。

影响分析是有界扫描；truncated=true 表示不完整，不能作为无影响结论。当前批次是同步单进程实现；多副本共享运行协调、异步长批次、标准 DMN 导入导出、复杂事实推理及跨环境制品晋级属于后续按需扩展。
