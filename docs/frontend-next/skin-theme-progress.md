# 皮肤与主题分离

目标：皮肤提供 iris（鸢尾紫，默认）、celadon（青瓷绿）、amber（琥珀棕）；主题维持 light/dark/system。两项独立保存、独立切换。浅色主功能区固定 #f6f7fa，表格实色，不增加导航或表格布局高度。

本次为基座外观功能，在既有 preferences/config/auth 模块内增量实现，不调整目录架构。右上角增加独立皮肤入口，个人偏好页同步。皮肤随账号保存；旧客户端不传 skin 时保留原皮肤，新账号默认 iris。不改旧迁移，追加 config V4；旧程序可继续使用原字段。新前端需配套新后端，部署先后端后前端，回退保留新增列。

| 顺序 | 范围 | 验收 | 状态 |
| --- | --- | --- | --- |
| S1 | config 偏好存储/迁移、PreferenceView、auth/me 皮肤字段 | 空库迁移、默认值、旧请求保留、非法值拒绝、用户/租户隔离、reset | 完成 |
| S2 | preferences store、DisplaySettings、PreferencesPage、双语、三套明暗主题变量 | 类型/单测、独立切换、系统主题、失败回滚、账号恢复 | 完成 |
| S3 | 浏览器验证、截图、完整门禁与文档同步 | 3 皮肤 × 2 明暗 × 桌面/手机、AA、保存/刷新、表格空间不变 | 完成（当前服务部署不在本次验证中） |

拟改路径：backend/config 的偏好实体、服务与公开视图；auth/internal/service/SessionService 的 Me 视图；db/migration/config/V4__preference_skin.sql；frontend-next 的上述组件/store、styles/theme.css、index.html 与双语资源；对应测试及 README/AGENTS。公开 PreferenceView 保留原构造入口，增加可选展示字段，不改变授权语义。

## S1 验证

通过标准后端入口（PowerShell 数组参数调用）执行根 reactor：`& ./tools/dev/verify-backend.ps1 -Standard -MavenArgs @('-pl','backend','-am','-Dtest=PreferenceTests,AuthFlowTests','-Dsurefire.failIfNoSpecifiedTests=false','-Dsma.verify.directory=target/skin-verify') -LogPath 'E:/juiceProgram/spring Modulith/backend/target/skin-verify.log'`，**6/6 通过，BUILD SUCCESS**。环境使用专用可清空库 `sma_test_skin_20261111`，输出隔离。首次通过 pwsh -File 传数组被合成单个字符串、Maven 参数失败，改用 PowerShell 原生调用后通过。

独立升级库 `sma_test_skin_upgrade_20261111` 按 V3 建表并插入旧 dark 偏好，再应用 V4：theme 仍为 dark、skin 默认 iris。旧六参数 PreferenceView 消费者先编译，再仅替换新的 PreferenceView.class，执行成功，原构造器和读取器保持可调用。无线上库变更；外部严格 JSON 解码消费者未验收。

## S2 验证

新增独立皮肤菜单（三个带色点的单选项）与个人偏好 skin 字段，theme 仍为三种明暗模式。皮肤缓存按 portal/用户隔离，服务端为账号偏好权威来源，旧响应缺失 skin 回到 iris。未登录皮肤使用 guest 命名空间。浅色工作区保持 #f6f7fa，绿色/棕色只调整语义配色与对应表面。

`npm run type-check` 通过；preferences/auth-mfa 单测 **6/6 通过**。`npx playwright test e2e/skin-theme.spec.ts --grep 'persist independently|personal preferences' --reporter=list --output=.e2e-runtime/skin-behavior` **2/2 通过**：独立切换、刷新恢复、跟随系统、保存失败回滚、偏好页保存/reset、不同账号缺字段回退。首次类型检查发现 Reka 单选事件可返回 null，已通过类型判断修正并复验。

## S3 最终证据

- `npx playwright test e2e/skin-theme.spec.ts --grep 'all skins' --reporter=list --output=.e2e-runtime/skin-matrix` **1/1 通过**（36.5s）：3 皮肤 × 2 明暗 × 1440/390/320px，18 个组合的页面无溢出、皮肤菜单 AA、表格高度不变、浅色工作区 #f6f7fa。12 张截图位于 `images/skin-{iris,celadon,amber}-{light,dark}-{1440,390}.png`，已目视检查青瓷绿桌面浅色、琥珀棕手机深色。初次发现模态菜单给背景加 aria-hidden 引发焦点检查失败，改为非模态菜单后通过，未关闭规则。
- 最终显示/偏好/表格回归：`npx playwright test e2e/skin-theme.spec.ts e2e/display-polish.spec.ts e2e/table-workspace.spec.ts --grep-invert 'all skins' --reporter=list --output=.e2e-runtime/skin-final-regression` **8/8 通过**（1.2m）。
- 最终 `npm run verify` **完整通过**：22 文件/54 单测、应用/测试类型、Lint、12 双语模块、123 旧前端基线及30路由清单、构建与发布预算；入口150KiB gzip、34懒模块、141产物。日志 `.e2e-runtime/skin-final-verify.log`。
- 真实浏览器检查：指定 `SMA_E2E_BACKEND_JAR=backend/target/skin-verify/springboot-modulith-admin-0.0.1-SNAPSHOT.jar`、后端8084/前端5194、输出 `.e2e-runtime/skin-real`，运行 `node scripts/run-real-e2e.mjs e2e-real/skin-theme.spec.ts`，**1/1 通过**（11.2s）。真实偏好 API 保存、旧请求保留、非法值400拒绝、新浏览器会话重新登录恢复青瓷绿/深色、琥珀棕刷新恢复通过；专属实例与随机测试数据库/账号已清理，日志 `.e2e-runtime/skin-real.log`。

## 交付与升级边界

本次没有替换正在运行的8080 JAR，没有重启用户现有服务或更改业务库。5174读取最新前端源码，但账号皮肤持久化需配套新后端，不能把旧后端与新界面组合当作已部署功能。可部署后端候选为 `backend/target/skin-verify/springboot-modulith-admin-0.0.1-SNAPSHOT.jar`；前端候选为 `frontend-next/dist/`。后端验证为本次专项6项，未冒充全仓后端回归。

部署时先备份运行制品和配置，部署后端由 Flyway 追加 skin 列，再切前端；不直接手工修改业务库。旧客户端不传 skin 的请求已用真实 API 验证保持皮肤，旧Java构造消费验证通过；未验证的外部严格JSON解码端需接入方确认。回退保留新增列及迁移历史，回退到旧程序不会显示皮肤但可继续处理原字段；不能运行旧新版本混合请求来宣称新皮肤可持久化。历史发布ZIP未改写。
