# 图标设置与图库扩充

用户已确认实施此前图标方案。本次为基座可复用能力：可搜索/分类的线性及3D图标选择、真实预览、保存回显、清除和取消、未知旧值保留、菜单/导航分组/权限按钮展示。图库只用审核后本地素材，不接受任意URL/HTML/SVG脚本；不改变权限语义或扩大数据范围。

## 方案与范围

- 存储复用 IAM permission/navigation 的 icon VARCHAR(64)，规范编号 `lucide:<kebab-name>`、`3d:<name>`；旧名称别名兼容。无DDL。未识别的旧值不自动清空，显示备用图标。明确清除使用 `none`，防止内置目录同步把空值补回默认图标；旧null仍表示未自定义、沿用默认表现。
- 图库扩充：既有 Lucide 包精选分类目录（目标至少150个），作者 3dicons V1 集合本地 WebP（按官方集合实际数量），中文/英文名称与关键词；分批显示，搜索作用于完整图库。
- 官网 https://3dicons.co/about 明确初代V1为CC0，允许商用/复制/修改/共享；仅从 https://3dicons.co/collection/b68cf8-v1 采集，排除新付费集合。Gumroad本轮读取超时，不冒充已核实该商店全部商品。
- 新增统一 AppIcon、IconPicker 及业务 PermissionButton。使用 v-permission 的原权限按钮逐项迁移到 PermissionButton，保留原事件/禁用/权限逻辑，配置图标覆盖原装饰图标；空配置保留原默认图标。不得把只负责授权的指令变成HTML注入器。
- IAM PermissionQuery 增加默认空映射方法，auth/me 附加已授权资源 icon 映射；旧实现继续可调用。按现有 granted 规则过滤禁用祖先、租户、portal及角色；普通用户无需权限资源管理权限。导航分组复用现有 icon 返回字段。
- 修改路径：frontend-next/src/lib/icons、components/business、features/iam、NavigationTree/NavigationGroupSwitcher、权限按钮调用点、双语及测试；backend/iam公开查询及实现、auth的Me返回、集成测试；public/icons/3d与授权清单。不做自定义上传、不采购付费包，不改已有权限授权或运行服务。

| 顺序 | 内容 | 验证 | 状态 |
| --- | --- | --- | --- |
| I1 | 素材授权/清单、本地图标目录、统一渲染 | 数量/ID/大小/路径/来源、旧别名与缺图回退单测 | 完成 |
| I2 | 选择器、权限/导航编辑与菜单展示 | 保存/取消/清除/未知值、搜索/分类/分页、键盘/窄屏 | 完成 |
| I3 | 已授权图标映射与业务按钮接入 | 后端隔离/权限过滤、所有调用点核对、配置后按钮实际变化 | 完成 |
| I4 | 全门禁、真实联调、截图与文档 | 明暗/三皮肤/窄屏、性能预算、真实保存重登录及回退 | 完成 |

部署需要新后端与新前端一起发布；旧后端缺少映射时按钮保留默认图标，旧前端对新图标ID可能显示默认文件夹。历史发布包不覆盖；当前8080是否更新单独记录，不将隔离验证写成当前服务已部署。

I1：本地209线性 + 122个V1 3D图标，3D WebP总计871170字节，逐文件SHA256与URL见 public/icons/3d/manifest.json，授权说明 NOTICE.md。`npx vitest run src/lib/__tests__/icons.test.ts` **3/3通过**（旧别名、恶意/未知标识拒绝、双语搜索、唯一ID/长度、全部素材哈希、图片失败回退及重新选择），`npm run type-check`通过。官网V1集合当前返回122个资源；GitHub猜测仓库不可用，未依赖该来源。

I2：IconPicker与权限/导航编辑接入，AppIcon接入侧栏与导航分组、列表回显。`e2e/icons.spec.ts` **2/2通过**（10.5s），类型与本次范围Lint通过。首次用例定位把RowActions当menuitem、把双重取消按钮当唯一元素，按实际控件语义修正后通过，未放宽保存/草稿断言。

I3：PermissionQuery增加default icons空映射、RolePermissionService复用granted过滤、auth/me返回permissionIcons。标准后端入口根reactor、隔离库sma_test_icons_20261111与target/icons-verify输出，PermissionIconTests/PermissionTests/RoleTests/AuthFlowTests **11/11通过、BUILD SUCCESS**，日志 backend/target/icons-verify-final.log。首次新增测试使用无授权测试账号授予权限，被现有门禁拒绝；改为测试管理员上下文后通过，未修改授权限制。旧实现无需实现新增方法的编译/执行检查通过。

前端先接入44个显式权限按钮，随后补充规则条件权限/表单按钮与上传组件。PermissionButton仅使用已授权key对应的已登记图标，保留禁用/事件/无配置默认图标。新增单测4/4、图标浏览器3/3通过（14.8s）。字符串替换初版漏掉多行闭合标签，已改用Vue AST定位并修正导入，类型检查通过；后续完整检查遇到并行扩展模块ReleaseLogin尚未落盘，保留原错误，I4重查。

## I4 验证记录

素材目视复核发现官网200px缩略图不透明，已替换为128px透明WebP：121个使用官网500px透明PNG，1个at图标原图下载端点返回500，使用官方缩略图仅移除与边界连通的白底。逐文件变换方式、源文件和结果哈希记录在manifest，不保存短期签名URL。122个图标角落透明度均检查为0，总图片477164字节；目录连同许可证/清单约549KB。已复核深色手机截图白块消除，不将初始缩略图版本作为最终效果。

- 最终素材/按钮单测 **4/4通过**；`e2e/icons.spec.ts` **4/4通过**（36.7s）：图标选择/保存/回显/清除/未知值、失败草稿、权限按钮、键盘/手机、3皮肤×明暗的6个视觉组合，所有图片均请求本站，可访问性检查通过。截图 `images/icons-picker-{iris,celadon,amber}-{light-1440,dark-390}.png`。
- 标准入口后端专项 **11/11通过**（上述I3），不宣称全仓后端完整回归。
- 真实后端8085/前端5195、独立随机数据库，`node scripts/run-real-e2e.mjs e2e-real/icons.spec.ts` **1/1通过**（14.5s），使用backend/target/icons-verify构建。真实修改内置openapi:app:create为3d:rocket、业务按钮显示、新会话重登录仍显示、清除none后按钮无图标且仍可操作。首轮未等待筛选完成就点击编辑产生多元素定位，加入等待唯一筛选结果后通过。专属后端/数据库/账号由runner清理，当前8080未动。
- 规则套件原端口5187被其他运行占用，未停止该进程，使用SMA_RULE_TEST_PORT=5197及独立输出：**26/26通过**（1.4m）。
- 最终全量前端门禁与常规回归在下方补充结果；不得以单次局部绿灯替代。

最终核对补充角色授权保存、宿主扩展运行状态/重对账按钮；独立发布账号动作保持其自身授权机制。常规回归首次57项中54通过、3失败：开发样板页新增皮肤入口后窄屏顶栏溢出、两条密码用例仍使用旧的超长新密码。样板页顶栏允许换行；两条用例仅更新测试新密码以符合当前8～12字母数字规则，保留原拒绝/写入断言，不改生产密码规则。随后显示/业务流程/角色/扩展回归重验，最后样板专项1/1通过。中间全仓Lint误扫研究时下载到.e2e-runtime的外站JS；当前已有统一runtime忽略规则，源码仍全部检查，未放宽业务Lint规则。

最终 `npm run verify` 完整通过（`.e2e-runtime/icons/complete-verify.log`）：28文件/71单测，类型/Lint/双语/基线/30路由清单、构建/产物预算；入口150KiB gzip，34懒模块，271产物。官网下载图标不会进入首屏JS，实际按展示项加载。历史ZIP未覆盖。

最终补充回归 `npx playwright test e2e/foundation.spec.ts e2e/workflows.spec.ts e2e/softlight-pages.spec.ts e2e/extension.spec.ts --reporter=list --output=.e2e-runtime/icons/completion-final` **20/20通过**（1.1m）；此前57项中的三项失败均已修复复验，保留原始54通过/3失败记录，不改写成原运行全绿。最终接入62个PermissionButton调用点（28文件）及UploadButton；源码清单与测试证据见 [icon-settings-validation.json](icon-settings-validation.json)。

## 使用与发布

在权限资源或导航编辑点击图标，搜索/筛选并选中，确认后保存资源。新值`lucide:*`/`3d:*`≤64字符，none明确清除；旧名称兼容，无需修改数据库结构。内置目录原空值会按原同步规则填默认，因此明确清除不能用null。保存后当前浏览器reload auth元数据，其他会话重新加载后生效。

新业务按钮显式使用PermissionButton并提供原权限key，禁止按图标是否存在判断授权。图标映射为空或旧后端缺字段时保留默认图标。非权限性质的“返回/取消”、单纯本地编辑辅助操作，以及扩展控制台独立发布身份动作不自动绑定IAM图标。宿主后台的主要权限按钮已接入，上传组件保留加载图标。

本次交付源码、本地构建、隔离联调证据。部署时先更新返回permissionIcons的后端，再更新前端；旧前端读取新ID只能显示其默认图标，回退时无需还原数据库，不删除已保存图标配置。历史发布ZIP不覆盖。外部严格JSON消费者尚未验证新增字段；公开接口通过default方法保持旧实现可编译使用。

最终当前服务复核：本任务没有执行8080重启，但并行部署已于2026-11-11 14:49:13+08:00启动本仓库运行JAR（PID40488，文件修改时间14:45:01）。读取运行JAR，与本次验证候选逐类比较SessionService、SessionService$Me、PermissionQuery、RolePermissionService，四个class摘要全部一致且包含permissionIcons；8080健康UP。5174沿用源码服务，可刷新后使用。此为运行制品/健康确认，当前用户账号未被登录或改写，业务保存验收仍以上述隔离真实后端结果为准。
