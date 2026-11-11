# 规则编辑体验补齐

2026-11-11：用户授权继续完善剩余功能。核对原RE0～RE4计划和实际实现后，先补专业编辑已有承诺中的缺口，沿用现有目录、公开接口和鸢尾紫UI，不调整架构或数据库。

| 顺序 | 任务 | 验收方法 | 状态 |
| --- | --- | --- | --- |
| U1 | 四类模板统一撤销/重做；刷新重新建立基线，保存与发布分离；内存有界，不存储试算输入 | 精确数值/分支/历史上限单测；流程连线、节点撤销/重做及保存浏览器测试；类型/lint | 完成：4单测、流程浏览器3/3、类型与局部lint通过；rule-u1-browser.log |
| U2 | 模拟和校验结果随规则、输入、参数修改失效；迟到结果不覆盖新状态，清除旧节点追踪 | 组件/浏览器覆盖编辑中请求、非法JSON、刷新输入与校验；不得自动重试 | 完成：迟到成功/错误2单测，authoring与flow浏览器7/7、类型及lint通过；rule-u2-final-browser.log |
| U3 | 专业编辑字段和函数参考；函数来自既有后端目录，字段来自当前契约；表达式补全按编辑器模型隔离 | 契约路径单测、浏览器补全/模型隔离/语言/窄屏；完整前端verify及规则回归 | 完成：3单测、authoring5/5、主题/窄屏2/2；完整verify66单测及预算通过，rule-u3-verify-recheck.log |
| U4 | 用户明确要求补充规则模板与示例：提供可选的示范场景、四类模板起点、参数说明和精确断言；通过正常草稿/试算/发布流程创建 | 四类样本与真实后端语义核验；浏览器选择、取消、创建及无自动发布；最终verify及规则回归 | 完成：完整verify71单测；规则开发浏览器26/26、修正后产物25/25；隔离真实4/4，12组模拟及4个模板发布断言通过，测试资源已清理 |

范围为 `frontend-next/src/features/rule/`、双语资源、规则专项测试和说明文档。后端保持既有契约；当前部署授权延续，前端源码服务可直接加载验证后的变更，不重启无关进程。测试使用独立端口与浏览器数据，不操作用户会话。

示例保存链路同步补充了提交快照基线和保存期间只读保护，避免异步回包误清仍在编辑的草稿；失败后恢复编辑且保留内容。门禁补齐`.e2e-runtime` / `test-results-*`生成目录忽略，不降低产品源码检查；并行图标组件的两处lint修正保留其功能，未回退并行变更。

RE5的组合计价、并行、灰度/集群、标准交换、评分卡等仍按实际需求独立立项；本次不以新增菜单或占位页面冒充交付。真实商品模块绑定及生产负载/恢复演练仍为独立验收。

## 本轮交付与使用

U1～U4全部完成。新增当前页撤销/重做、字段和函数参考/补全、模拟结果失效及迟到响应保护、保存期间只读和提交快照基线。四类可选示例覆盖表达式、条件、流程、决策表，每组3条断言，使用方法见[samples.md](samples.md)。示例只有明确保存后才创建草稿，发布仍独立操作；没有在当前业务库自动创建示例。

历史上限为50步和约2M个序列化字符，超出容量时优先淘汰旧快照，仅保留当前页内存。刷新重新建立基线，不恢复未保存的草稿或真实模拟输入；原离开/刷新保护继续生效。Monaco引用的补全提供器按模型隔离并释放；函数名称由后端目录提供，签名参数仅作编写提示，最终语法和权限以服务端为准。

## 最终验证

| 验证 | 实际结果与证据 |
| --- | --- |
| 规则专项单测 | `npx vitest run src/features/rule/__tests__`，7文件18项通过，含精确金额、分支历史、迟到结果和错误、契约筛选、克隆隔离 |
| 完整前端门禁 | `npm run verify`，71项单测、类型/测试类型、lint、12模块双语、123旧文件基线、30旧路由清单、构建与发布预算通过；`.e2e-runtime/rule-followup-final-verify-recheck.log` |
| 开发浏览器 | `SMA_RULE_TEST_PORT=5187 npx playwright test --config playwright.rule.config.ts`，26/26通过；`.e2e-runtime/rule-followup-final-browser.log`。之后决策表默认输出的修正由当前产物和真实联调覆盖 |
| 当前产物浏览器 | `SMA_RULE_TEST_PORT=5187 SMA_RULE_PRODUCTION=1 npx playwright test --config playwright.rule.config.ts`，25/25通过；`.e2e-runtime/rule-followup-production.log`。不包含开发专用探针 |
| 真实后端 | `SMA_E2E_FRONTEND_PORT=5187 SMA_E2E_BACKEND_PORT=8082 SMA_E2E_BACKEND_JAR=<已验证候选JAR> npm run test:real -- rule-samples.spec.ts`，4/4通过，每类3组输入模拟且模板发布通过全部输出断言；`.e2e-runtime/rule-followup-verified-real.log` |
| 真实隔离与清理 | 运行ID1794379337000，独立schema/账号/随机管理员凭据；结果`passed=true, credentialsPersisted=false`。专属后端、测试库、账号及密钥文件已清理，原8080/5174保留 |
| 产物与容量 | 当前dist构建成功，首屏150KiB gzip，34个懒模块，271文件摘要；Monaco懒块约733KiB gzip，目录仍不加载编辑器 |
| 源码一致性 | 最终验证期间53个规则相关文件摘要一致，包含规则目录、双语、专项浏览器及lint生成物边界配置；机器证据见[authoring-followup-verification.json](authoring-followup-verification.json) |

人工复核隔离模拟数据的[桌面浅色工作区](authoring-followup-workspace-light-desktop.png)、[手机深色参考面板](authoring-followup-reference-dark-mobile.png)与[手机示例场景](authoring-followup-sample-dark-mobile.png)，390px页面无横向溢出，键盘可展开参考、选择示例和编辑。

## 发现、修正与边界

- 真实联调发现决策表即使有ANY兜底行也必须提供合法默认输出。示例改为可空应付金额及`{payable:null,rate:1}`默认结果，保留原价行；未命中表示无法报价，不能当作零元价格，随后真实发布通过。
- 资格未命中是NOT_MATCHED/false，修正原先统一期待SUCCESS的测试；保存快照的作用域错误修正后重新验收。失败日志`rule-u4-real.log`、`rule-u4-real-recheck.log`、`rule-followup-final-real.log`保留，未将这些运行计为完成证据。
- 补全首次缺少Monaco的suggest控制器，已按已安装版本导入所需模块；后续真实键盘补全和产物测试通过。JSON报错后仍保留固定可访问标签。生成目录中的调试压缩JS曾触发lint，修正生成物忽略后完整门禁通过。
- 本轮没有改后端规则契约或数据库迁移，没有重新运行完整Maven；真实联调用此前通过完整回归的候选JAR，版本与摘要记录在机器证据。没有把真实示例联调等同于实际商品接入、生产容量或恢复验收。
- 当前5174继续既有Vite源码服务，刷新即可加载新增界面；构建的dist经静态preview验收，但未将当前服务切换为生产静态托管。没有登录用户当前浏览器进行人工业务验收。
