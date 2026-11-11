# 管理壳背景层次调整

目标：顶部导航、工作标签栏、主功能区从上到下逐步过渡；标签栏和主内容背景明确区分，延续鸢尾紫主题。保持紧凑尺寸、表格内部滚动和背景品牌。

| 阶段 | 工作 | 状态 |
| --- | --- | --- |
| C1 | 核对现状与确定浅色/深色层次 | 完成：标签栏原先透明，直接透出与主内容相同的背景 |
| C2 | 公共主题变量与管理壳样式调整 | 完成 |
| C3 | 桌面/手机截图、层次及可读性检查、构建门禁与文档 | 本次样式验证完成；全仓 Lint 另有失败 |

当前配色：浅色为白色顶部 → 淡鸢尾紫标签栏 → 原浅灰主功能区（#f6f7fa），按后续反馈不再使用灰紫内容底色；深色保持较亮紫灰顶部 → 中间紫灰标签栏 → 深色内容底色。表格和表单仍为实色卡片。

## 实际交付与验证

公共 `theme.css` 新增管理壳语义变量，顶部/标签栏使用独立实色背景；浅色内容区起止颜色均引用 `--background`，深色内容区保留渐变。选中标签单独提亮并保留鸢尾紫文字。尺寸和 DOM 结构不变，背景品牌仍可透出、仍可被表格覆盖。

| 层级 | 浅色 | 深色 |
| --- | --- | --- |
| 顶部导航 | #ffffff | #2b283b |
| 工作标签栏 | #f2eefb | #211f2f |
| 内容背景起点 → 终点 | #f6f7fa → #f6f7fa | #191824 → #15151d |
| 选中标签 | #ffffff | #393249 |

- 首轮灰紫方案的历史证据：`node .e2e-runtime/capture-shell-colors.mjs` 检查 1440/390px × 明/暗主题共 4 张实际页面截图，真实计算样式相对亮度逐级降低，无页面溢出、页面错误或未匹配模拟请求。见 [shell-color-validation.json](shell-color-validation.json)，浅色配色已被后续反馈替代。
- `npx playwright test e2e/accessibility.spec.ts e2e/softlight-shell.spec.ts --reporter=list --output=.e2e-runtime/shell-color-e2e`：**5/5 通过**（43.7s），覆盖双主题/双语言自动 AA、窄屏、200% 缩放、键盘焦点、菜单与标签操作。
- `npm run build` 和 `npm run release:check` 通过，入口 148 KiB gzip、33 个懒加载模块、138 个产物文件；未改写历史发布 ZIP。
- `npm run verify`：旧前端 123 文件基线、30 路由清单、应用与测试类型检查通过；停于并行 `features/rule/components/DecisionEditor.vue` 的 `UNIQUE` / `FIRST` 原始文本 Lint 错误。未修改该文件或放宽规则，不能宣称全仓门禁通过。日志 `.e2e-runtime/shell-color-verify.log`。

验证使用本机 Chrome 与隔离模拟接口，不代表真实后端或生产部署验收。

## 后续反馈：主功能区恢复浅灰（已完成）

按用户最新截图反馈，浅色主功能区恢复此前的 #f6f7fa，直接引用 --background；淡紫标签栏、白色顶部和选中标签保留，深色配置保持。最新设计规范、README 和前端规则同步采用此决定。

`node .e2e-runtime/capture-content-gray.mjs` 在 1440/390px × 明/暗主题下检查真实计算样式，浅色内容起止颜色均为 rgb(246,247,250)，标签栏仍为 rgb(242,238,251)，无页面溢出或页面错误。已查看桌面浅色截图；证据为 content-gray-validation.json 与 images/content-gray-*.png。首次采集遇到 5174 服务未运行，启动开发服务后复验通过。

`npm run verify` 本轮完整通过，包含基线、路由清单、应用/测试类型、Lint、双语、单测、构建及产物检查；产物预算 149 KiB gzip、34 个懒加载模块、140 个文件。日志 frontend-next/.e2e-runtime/content-gray-verify.log。未改写历史发布包，浏览器验证使用隔离模拟数据。
