# LeiBox 交互参考来源

2026-11-12 使用项目 `.agents/skills/leibox/scripts/leibox.mjs pull <slug> --framework vue` 下载完整 Vue 包。以下目录仅作设计/源码追溯，不参与前端构建；产品实现位于现有 `frontend-next/src/components/business/` 与 `features/workspace/`，以 Vue 模板替换演示内容、硬编码主题和直接 DOM 注入，并保留现有授权/保存协议。

| 原包 | 文档 | 设计参考/署名 |
| --- | --- | --- |
| glass-tab-menu | https://leibox.pantoria.cn/docs/animations/glass-tab-menu | solutionb：https://x.com/SolutionB2u/status/2113575003341881728/video/1 |
| elastic-panel-resize | https://leibox.pantoria.cn/docs/animations/elastic-panel-resize | Alvish：https://x.com/alvish/status/2111084319716647114/video/1 |
| collection-layout-morph | https://leibox.pantoria.cn/docs/animations/collection-layout-morph | Previous Leon：https://x.com/shawn_kel/status/2087937950424129978/video/1 |

源码提供方：LeiBox。保留包内原始声明；目录未提供统一的原设计许可，不能据公开链接推断授权。收藏原包包含 Graphical/Lobe Icons 素材记录及适用声明，产品不使用这些演示插图。原包依赖分别为 Vue、Vue、Vue + GSAP 3.12.7；基座适配采用自身 Vue/CSS/浏览器动画能力，不安装 GSAP，不从外站运行时加载资源。

取舍与验收见 [专项进度](../leibox-ui-progress.md)。这些参考文件不是为绕过检查而搬移的产品源码。
