# 新前端交付与验收

日期：2026-11-11。范围：P04 中已有后台功能，独立 `frontend-next/`；旧 `frontend/` 和旧启动入口保留。当前为本地实施和发布准备，未替换正式环境静态资源。

## 可审查交付物

| 内容 | 位置 |
| --- | --- |
| 新工程及运行说明 | `frontend-next/`、`frontend-next/README.md` |
| 任务与逐次验证证据 | `docs/frontend-next/progress.md` |
| 原工程保护基线 | `docs/frontend-next/baseline.json`，123 个文件 SHA256 |
| 28 条原有路由对照 | `docs/frontend-next/route-matrix.json`，另覆盖收件中心两个子页 |
| 视觉规范与截图 | `docs/frontend-next/design-spec.md`、`images/` |
| 版本、组件来源和方案调整 | `docs/frontend-next/technical-decisions.md` |
| 独立生产产物 | `frontend-next/dist/`，包含构建依赖图及 SHA256 清单 `release-manifest.json` |
| 新版发布包与旧产物快照 | 当前 `frontend-next/release/frontend-next-0.2.0.zip`；v0.1.1、v0.1.0 和 `legacy-frontend-existing-20261111.zip` 保留，校验见 `release-summary.json` |

## 验证口径

- `verify` 包含干净安装后的应用与测试类型检查、lint、10 模块中英文键和插值、纯逻辑测试、路由清单一致性、旧前端哈希及生产构建检查。
- 模拟接口浏览器测试验证请求参数、交互、错误/空态、权限及响应式；不能代替实际后端行为。
- 真实测试使用独立 MySQL 数据、临时数据库账户、随机管理员、8081 后端及独立浏览器上下文，覆盖管理员和新建受限成员。不会操作已有业务数据。
- 实际后端读取、写入、WebSocket 推送、双用户回复及密钥轮换后的旧令牌/旧密钥失效均已通过；最终结果见本页末尾记录。

真实联调的两项流程分别覆盖：用户/岗位/角色创建和授权持久化、直接无权 URL、通知实时到达、个人待办、成员私信回复、文件上传下载、应用凭据、数据库文档；参数分组/参数与字典类型/字典项创建、其他主要页面读取、锁屏恢复。

## 质量与性能

- 样板检查覆盖浅/深色、中文/英文；自动 WCAG 2.0/2.1/2.2 AA 相关规则检查在工作台、用户、角色页面执行。键盘检查包括模态焦点约束、Escape、焦点返回及移动端行操作。
- 主要宽度：1920、1440、1024、768、390、320；另做双倍 CSS 缩放与窄布局检查。它不等同于所有浏览器及实体触屏设备的人工认证。
- 现有业务路由完成 1440/390px 巡检；22 类业务列表检查失败到空态的恢复，刷新失败保留原结果。
- 表格实际请求 200 行、权限树 500 节点搜索设交互预算，测量不启用 CPU/网络节流，不把本机结果当作线上 SLA。
- 产物预算：入口及其静态依赖合计不超过 200 KiB gzip；单个懒加载页面新增依赖不超过 300 KiB gzip。富文本只随通知模块加载，所有产物文件有 SHA256。
- 生产依赖审计为 0；完整开发依赖审计为 14 条 moderate，无 high/critical，集中在 glob/minimatch 传递依赖。详见技术决策，没有强制升级或隐藏冲突。

## 部署与回退

保留 `frontend-next` 名称是本轮用户的明确要求，优先于 P04 初稿末尾统一目录的建议。新旧工程继续独立；本轮不修改旧开发脚本指向。

旧产物快照直接取自实施结束时已经存在的 `frontend/dist/`，未重新构建，不据此宣称它与当前源码或正式服务器版本完全一致。正式切换前仍应保留实际部署的旧产物；原工作区源码由 123 文件基线另行核验。

1. 使用 `npm ci`、`npm run verify` 产生新 `dist/`，保留锁文件、release-manifest、验收记录及旧版可运行产物。不要用 `git reset` 恢复本来就含用户修改的工作区。
2. 新静态资源放入新的版本目录；默认根部署。如果使用 `/admin-next/`，先设置 `SMA_NEXT_BASE=/admin-next/` 再构建，并按相同路径配置 history 回退。
3. `/api` 与 `/api/message/ws` 转发到同一后端，保留 WebSocket Upgrade；健康检查使用 `/actuator/health`。生产不依赖 Vite 代理。
4. `index.html` 使用不缓存或需重新验证的缓存策略；内容哈希资源可以长期缓存。静态资源目录与 HTML 一起切换，避免混用版本。
5. 在新指向核对登录、深链、403、主题/语言/时区、一次性消息票据和推送；同源切换按既有令牌键读取，表格和标签使用新的用户隔离键。不同端口不复制登录令牌。
6. 如有阻塞问题，静态服务器恢复旧产物目录和原 history 回退配置，刷新 HTML。此重构不包含数据库迁移；不要清空用户存储、重置数据库或回滚后端业务。

根路径部署的 Nginx 配置示意（版本目录、后端地址和 TLS 按实际环境填入）：

```nginx
map $http_upgrade $connection_upgrade {
  default upgrade;
  '' close;
}
server {
  listen 80;
  root /srv/sma/frontend-next/current;
  location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $http_host;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $connection_upgrade;
    proxy_read_timeout 90s;
  }
  location = /actuator/health {
    proxy_pass http://127.0.0.1:8080;
  }
  location = /index.html { add_header Cache-Control "no-cache"; }
  location /assets/ { try_files $uri =404; add_header Cache-Control "public, max-age=31536000, immutable"; }
  location / { try_files $uri $uri/ /index.html; }
}
```

正式网络、域名、TLS、反向代理和人工产品视觉签收属于实际发布环境验收。本地实现完成不表示已经上线，也不表示所有第三方嵌入网站允许 iframe。

## P04 逐项交付核对

| 方案部分 | 对应实现与证据 |
| --- | --- |
| 目标、范围与工程保护 | 保留旧 frontend 的 123 文件基线；新工程独立运行和构建；无后端业务改动 |
| 视觉与响应式 | 语义主题、统一框架、字体/间距/密度、窄屏抽屉和行操作菜单；72 组样板布局及 24 张实际页面截图 |
| 工具链与组件来源 | 锁文件实际安装；shadcn-vue MIT 许可；Zod 4 / 图标命名调整有明确技术决策 |
| 数据表格与筛选 | 服务端分页/排序、边界、请求竞争、局部滚动、当前页选择、用户隔离列/密度偏好；200 行实测 |
| 表单与编辑容器 | VeeValidate 字段、后端字段错误映射、选项失败重试、草稿保护、重复写保护、固定操作区、DST 和敏感值规则 |
| 权限及层级选择 | 独立/可选级联、半选/禁用、搜索保留、展开/收起、键盘；五类数据范围及 500 节点实测 |
| 跨页面状态 | 401/423、旧请求身份隔离、品牌、用户偏好、标签身份、路由回跳；消息单连接/重连/强退清理、文件对象 URL 释放 |
| 所有既有页面 | 28 条原有路由逐项映射到新页面；消息中心通知/个人待办/私信子页单独验证；frame 受控目标验证 |
| 真实业务闭环 | 双用户认证、权限、通知实时推送和私信回复；IAM/字典/参数写入、文件字节下载、运维读取、应用一次性凭据和轮换 |
| 质量门禁与切换准备 | 干净安装、类型/lint/i18n、单元/浏览器/隔离后端、AA/键盘/缩放、构建预算、依赖退出、产物 SHA256 及切换回退说明 |

未把 P04 已排除的 hiprint、AI 数智化中心等后续功能加入空入口。独立 frontend-next 名称、旧代码和旧启动入口按用户最新要求保留。

## 首次交付验证记录（v0.1.0）

- 干净 `npm ci`、`npm run verify` 通过：应用和测试类型、ESLint、10 模块双语言、16 个单元测试文件 / **36 项测试**、生产构建与产物检查。
- 完整浏览器回归 **32/32 通过**，零失败、零重试；证据 `test-evidence.mock.json`。另保存独立视觉检查 `test-evidence.visual.json`。
- 最终隔离真实后端 **2/2 通过**：本轮临时数据库 `sma_next_e2e_1794337075170`，前端表单卸载回归也已通过；证据 `test-evidence.real.json` 同时记录被测 JAR 的 SHA256。临时数据库、账号、MySQL 实例及后端已清理。
- 生产 `dist` 经本地 preview 再做 **3/3** 登录/权限深链、过期会话及受控 iframe 冒烟检查；测试后专属 preview 已停止，证据 `test-evidence.production.json`。
- 本机测量：200 行表格请求到显示 **871ms**（预算 5000ms）；500 节点树搜索 **42ms**（预算 3000ms），未使用性能节流。
- 入口及静态依赖 **138743 字节 gzip**，最大懒页面增量低于 300 KiB；94 个产物文件 SHA256、新 ZIP 95 个条目核对通过。具体文件/哈希见 `build-evidence.json` 与 `release-summary.json`。
- `audit-production.json`：0 漏洞；`audit-development.json`：14 moderate、0 high/critical，边界及后续处理见技术决策。
- 旧 `frontend/` 的 **123 个文件哈希一致**，旧源码没有修改；已有旧 dist 另存只读快照。本轮完成开发与切换准备，正式环境切换未执行。

## 显示优化补丁（v0.1.1）

- 侧栏、移动导航、顶部业务导航、工作标签、公共数据表格及数据库表列表改为 Reka ScrollArea：透明轨道、6px 可见的青绿色圆角滑块、悬停显示/离开淡出；键盘聚焦时保持可见，支持原生滚轮、触摸与滑块拖动。其他滚动区域使用同主题的细滚动条兜底。
- 明暗主题分别设置滑块颜色，常用背景上对比度大于 3:1；支持系统高对比和减少动态效果偏好。数据库列表以内容高度显示，长列表最大 65dvh，短列表不会留下大片空白。
- 主题拥有独立入口和当前模式图标；另一个入口仅包含语言与时区。保存、刷新恢复及保存失败回退沿用既有偏好协议。
- `verify` 通过，包含 36 项单测、应用/测试类型、lint、i18n、构建、旧工程哈希及产物检查。10 项针对性浏览器回归通过：滚轮/拖动/键盘、自动淡出、数据库局部滚动、分别保存偏好、明暗/中英文 AA、320–1920px 和双倍缩放、全部旧业务路由的桌面/窄屏检查。
- 生产 `dist` 的同两项显示专项再次通过。证据：`test-evidence.display-polish.json`、`test-evidence.display-polish-production.json`；截图：`images/scrollbars-navigation-light.png`、`images/scrollbars-database-dark.png`。
- 当前构建清单为 `build-evidence.json`，v0.1.0 原清单保存在 `build-evidence.v0.1.0.json`。新包和原包分别保留；本补丁未重跑实际后端业务写入流程。


## v0.2.0 鸢尾紫柔光重构验收（2026-11-11）

已按用户确认的方案依次完成主题、管理壳、公共组件、四类代表页、全路由及产物验证。阶段测试通过后才更新状态，见 [柔光进度](softlight-progress.md)。侧栏导航组承接顶部业务导航，工作标签保留；角色页默认双栏授权，未修改权限协议。

| 验证 | 本轮结果 | 证据 |
| --- | --- | --- |
| 工程门禁 | 旧前端 123 哈希、28 路由、类型/lint/i18n、16 文件/36 单测、构建和预算通过 | `design-review-softlight/s6-verify.log` |
| 全量模拟 | 38/38 通过，无重试/失败 | `test-evidence.softlight-mock.json` |
| 独立真实后端 | 2/2 通过；临时 MySQL 3307 / Spring 8081，运行 1794354474180 已清理 | `test-evidence.softlight-real.json` |
| 生产静态包 | 9/9 通过，使用 preview，未复用 dev server | `test-evidence.softlight-production.json` |
| 实际页面截图 | 核心 32 张 + 其他页面 22 张；人工抽查桌面、深色手机、监控/消息/资料 | `softlight-gallery.html`、`softlight-visual-review.json` |
| 产物校验 | ZIP 101 条目匹配 100 文件哈希及清单；入口依赖图 143601 字节 gzip | `softlight-package-verification.json` |
| 旧包与依赖保护 | v0.1.1/v0.1.0/legacy ZIP 哈希未变；lock 仅应用版本元数据变化，依赖图完全相同 | `release-summary.json` |

模拟/真实套件在版本标记升至 0.2.0 之前完成，随后仅更改应用版本元数据和交付/测试工具；应用源码未改变。最终工程门禁与生产包 9 项检查针对 0.2.0。实际后端使用证据中记录哈希的现有 JAR 副本，不代表重新验收了并行后端改动。

没有修改旧启动脚本、清空用户偏好或切换正式站点。对外域名/TLS/线上代理、全部浏览器和实体设备仍属于正式部署验收；本地测试不会被描述为已经上线。正式切换时保留当前线上目录，回退只恢复静态资源指向，无数据库迁移。
