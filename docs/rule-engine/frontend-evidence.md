# 规则界面开发验证记录

## F0：编辑器接入

- 固定 @vue-flow/core 1.48.2、monaco-editor 0.57.0、lossless-json 4.3.3；对应npm元数据许可证MIT，Vue Flow peer覆盖Vue3.5。仅新增编辑器和精确JSON依赖，没有替换UI库。
- 类型、局部ESLint及双语11模块检查通过；规则值/图专项4项单测通过。
- `npx playwright test --config playwright.rule.config.ts` 的编辑器探针1/1通过。独立5187端口和输出目录，避免现有5174及其他测试产物冲突；编辑、主题/窄屏、导航卸载无页面异常。
- 实际发现：Monaco0.57使用新的exports映射，旧`esm/vs`深路径不可用，按发行包改用`monaco-editor/editor/editor.api.js`；Chrome新EditContext不能用textarea fill，测试改为焦点及真实键盘输入。失败未计完成。
- 编译模型、监听、ResizeObserver逐编辑器释放；Worker引用计数归零才统一终止，避免关闭一个编辑器影响另一个。后续实际业务路由和生产包另作最终验收。

## F1：专业配置基础

- `npm run verify` 首轮通过：旧前端123文件、28路由基线保持，类型/测试类型、lint、11双语模块、全部单元测试及生产构建预算通过。日志 `frontend-next/.e2e-runtime/rule-f1-verify.log`。
- 初次引入Monaco出现约695KiB gzip的大懒块；管理路由静态依赖与首屏预算继续生效，首屏约144KiB gzip。编辑器不是首屏或运营目录的静态依赖；最终另记录Worker及完整编辑器加载成本。
- `rule-authoring.spec.ts` 3/3通过：场景创建、模板模拟精确发送20位小数金额、保存不发布、递归条件编辑、离开取消保留修改、修订冲突不丢失表达式或自动重试。日志 `rule-f1-browser.log`。
- 实际契约缺口：模板资产不暴露所属场景，不能通过Schema相等猜测。新增受模板管理权限/数据范围保护的只读 `/templates/{id}/scene`，旧JSON/记录签名不变；数据库与跨租户专项结果随后登记。
- 后端 `rule-ui-api-tests.log`：38项通过，含17平台集成及架构/Modulith/双语，失败/错误/跳过均0；场景关联查询与跨租户拒绝通过。前端首轮43单测通过，authoring3/3通过后F1-01标完成。

## F1-02：模板目录与实例

- 独立浏览器 `rule-instances.spec.ts` 2/2通过，日志 `rule-f12-browser.log`：按场景选命名模板、固定版本、参数说明与十进制传输、字符串业务ID、保存草稿不发布；业务授权403时保留表单。目录页面请求中没有Monaco、Worker或流程画布。
- 初次测试因使用label文本精确匹配带option的select而超时，增加明确aria-label并按combobox访问后通过，没有跳过业务操作。
- 为运营权限单独增加只读 `/catalog/scenes`、`/catalog/entries` 和受实例管理范围保护的 `/instances/{id}/template`；旧 `/catalog` 返回结构保留。目录entry提供code/name与原Version，避免运营看到无意义ID且不暴露模板草稿。后端验证完成后更新进度。
- `backend/target/rule-ui-catalog-tests.log`：38项通过，包含新目录名称/固定版本/实例文档与跨租户拒绝，兼容原目录。前端类型/测试类型、lint、12模块双语与6项规则单测通过后F1-02标完成。

## F1-03：发布与版本

- release浏览器3/3通过（rule-f13-browser.log）：保存修订发布及确认、固定版本回滚、实例试算门禁/停用、504未知结果不重试、无权限隐藏操作。类型与局部lint通过。
- 初轮测试误用“确定”而现有确认按钮为“确认”；另发现DataTable的具名插槽使用列key，已改为actions/state以实际显示操作与状态文案，复验全部通过。工作区期间公共表格布局另有在途调整，沿用其TableWorkspace组合，没有回退公共组件。

## F2：流程工作区

- `rule-flow.spec.ts` 2/2通过（rule-f2-browser.log）；真实指针拖线、键盘可达出口选择、节点配置、领域模型不含画布内部字段、模拟路径定位与刷新回读。
- 类型检查发现深度展开问题，改为浅引用管理画布节点；实际拖线首次因新增节点超出当前缩放范围失败，限制自动放大比例，提供整理布局并增大连接手柄，复测通过。画布坐标仅会话内使用，保存内容为后端图模型。

## F3～F4：决策表、治理与总体检查

- 决策表和升级预览2/2通过（`rule-f3-browser.log`）：显式单元格运算符与常量、FIRST改序、前后版本对比、迁移仅写草稿。支持阶梯约束、模板复制/差异及依赖影响。
- 治理专项2/2通过（`rule-f4-browser.log`）：维护断言、查看失败批跑报告、脱敏执行追踪、恢复待办和保留清理确认。
- `npm run verify` 最终通过：52单测、12模块中英键、123旧文件哈希、当前旧路由清单和生产构建；首屏149 KiB gzip，34个懒加载模块通过预算。Monaco主懒块约695 kB gzip、独立Worker约304 kB未压缩；Vue Flow主懒块约50 kB gzip；目录不请求两类编辑器。
- 规则浏览器全套16/16通过，生产静态包15/15通过（生产不包含开发编辑器探针）。生产初测13/15，CSS压缩缩写 `#fff` 不能被Monaco作为主题色读取；已在主题适配边界展开，失败日志 `rule-production-short-hex-failure.log`，复验全部通过。
- 人工复核测试生成的[桌面浅色工作区](rule-template-light-desktop.png)及[手机深色目录](rule-catalog-dark-mobile.png)，截图为隔离模拟数据。手机390px无页面横向溢出，英语界面保留业务自己的中文模板名。
- 后端 `backend/target/rule-ui-final-verify.log`：42项专项通过，含规则平台、IAM菜单兼容、架构、Modulith、双语；构建产出隔离JAR。旧查看按钮登记为菜单时保留原ID、显示设置和开关；不会自动给普通角色授权。其他后端套件未在本轮重跑。
- `npm run test:real -- rule.spec.ts` 隔离真实浏览器1/1通过（`rule-final-real.log`），使用Spring Boot JAR、独立MySQL和随机临时凭据。真实管理员登录后由侧栏建立场景和模板、模拟并发布、从目录选用；未接入业务对象绑定被真实后端拒绝，保留表单。运行ID `1794360314286`；专属数据库、账号及进程已清理，未保存凭据。
- 验收范围是P06规则专项与工程`verify`，没有重新验收所有无关模块的浏览器交互，没有生产部署、实体设备验收或真实商品绑定成功宣称。已构建 `frontend-next/dist`；保留历史发布包。
