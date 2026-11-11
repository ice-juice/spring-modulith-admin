# 二次开发与包管理

继承[总规则](../../AGENTS.md)。基座和二开共享现有 Spring Boot 与 Vue 工程，但各有专属包根和目录。

| 接入方式 | 适合 | 边界 |
| --- | --- | --- |
| 同源码树的 Modulith 模块 | 单体二开的默认接入方式，与宿主一起发版 | 商城代码与基座一起由现有 Maven 工程编译，在同一 Spring 上下文按模块边界协作。新增模块不另设启动类或子工程。 |
| P05 业务插件 | 需要在宿主内更换受限业务实现，所需 SDK/宿主能力已存在 | 独立编译普通 JAR，走签名、校验、预热和切换流程。需要持久化或外部副作用时先设计稳定宿主能力和工作账本接入。详见[热更新规则](extension-hot-reload.md)。 |
| 独立业务应用 | 明确要求独立进程、独立部署时 | 独立构建，走已有且获准的 HTTP/API 契约。OpenAPI 机器身份不是后台用户身份；不用内部表或 Bean 补齐接口。 |

业务分支从已确认的基座提交演进。发布时记录基座标签或提交、业务提交及制品摘要；从同一业务提交构建前后端。真正采用插件或独立服务时，另行锁定外部依赖、SDK 和业务制品版本；不以浮动版本或 `SNAPSHOT` 作为正式交付依赖。

## 分支与合并

1. **基座主线 `main`**：收录可复用基座及其测试、文档、构建配置和不参与构建的教学示例。例如 `examples/shop-layout/` 可以留在 `main`；实际客户业务源码、页面、配置、Flyway 脚本、依赖和制品只进业务分支。合并前检查相对目标分支的实际差异，包括压缩合并或挑选提交后的内容。
2. **业务独立并行**：同仓二开从已确认的基座标签或提交建立独立分支，建议用 `biz/<bizid>`（如 `biz/shop`），功能分支合回对应业务分支。通常在原项目目录切换分支；需要同时打开两个分支时才使用 Git worktree，先确认本地路径。两个 worktree 共享 Git 历史和远端，绝不能把本机 worktree 路径当成构建或调用依赖。独立业务仓库在自己的分支开发，并固定依赖的基座版本。
3. **基座更新单向进入业务**：先确定目标基座标签或提交，在业务升级分支引入该版本，解决冲突并验证业务构建、接口和数据迁移，通过后合回业务分支。遇到破坏式更新按[升级与兼容](platform-upgrades.md)处理；迁移未完成时继续使用已验证的基座版本。
4. **业务发现的通用修复单独回流**：基于基座主线另开基座变更分支，只提取通用修复或扩展点及对应测试，独立评审后合入 `main`，再由业务分支引入。不得把业务分支或夹带业务改动的提交合回 `main`。

## 二开目录

下面是**开始开发真实商城后**的目录，从项目总 `README.md` 所在的 `spring Modulith/` 这一层看。后端 `com/sma/business/` 与基座的 `com/sma/admin/` 同级；前端 `src/business/` 与基座的 `src/features/` 同级。

```text
spring Modulith/                         # 实际二开所在的业务分支
├── README.md
├── backend/                            # 一个 Spring Boot 工程
│   └── src/
│       ├── main/
│       │   ├── java/com/sma/
│       │   │   ├── admin/              # 基座：iam/、config/ 等
│       │   │   └── business/
│       │   │       └── shop/           # 商城模块
│       │   │           ├── package-info.java
│       │   │           └── internal/
│       │   │               ├── web/    # Controller
│       │   │               ├── service/
│       │   │               └── persistence/
│       │   ├── resources/db/migration/shop/
│       │   └── resources/i18n/shop/
│       └── test/java/com/sma/business/shop/
└── frontend-next/                     # 一个管理前端工程
    └── src/
        ├── features/                   # 基座页面
        └── business/shop/              # 二开管理页面和业务 API 封装
```

在 [main 的空目录示例](../../examples/shop-layout/README.md) 中，同样的路径放在 `examples/shop-layout/` 下面供查看。示例不加入 Maven 或 Vite 构建，不注册模块和路由，也不创建数据库表。

## 开发、构建与部署

1. **开发**：在业务分支沿用基座启动类和开发配置。宿主在 `com.sma.business` 下发现独立的 `shop` 模块，模块声明依赖白名单，通过基座公开 Query/API 或 SPI 协作。基座不引用具体业务类；需要业务实现时由基座定义扩展接口。前端在原有路由中显式接入 `src/business/shop/`、权限和语言包。
2. **管理**：按包和目录审查业务差异，基座修复单独合回 `main`。升级基座时把指定基座版本引入业务分支，复验业务接口、迁移、权限和前端路由。
3. **打包**：使用同一个业务提交，在项目顶层用 `pwsh -NoProfile -File tools/dev/verify-backend.ps1 -Standard` 验证并构建现有后端 JAR；在 `frontend-next/` 用 `npm ci` 和 `npm run verify` 构建现有管理前端。后端产物仍在 `backend/target/`，前端在 `frontend-next/dist/`。实际项目如选用旧版前端，沿用其现有构建流程。
4. **部署**：按 [部署说明](../deployment.md) 部署同版本的后端 JAR 和前端静态文件。只启动一个后端 Java 进程，前端仍用现有静态站点及 API 代理；商城表由同一个宿主的 Flyway 按 `shop` 模块迁移。商城无需专用 JAR、服务端口、npm 工程或部署流程。

## 包名与资源命名

| 资源 | 规则与示例 |
| --- | --- |
| 业务 ID | 使用小写英文字母（`[a-z]+`），如 `shop`，与分支后缀、模块、目录、表及权限前缀一致；不占用 `common`、`iam`、`rule`、`extension` 等已用名称。 |
| 单体业务 Java 包 | `com.sma.business.<bizid>`，如 `backend/src/main/java/com/sma/business/shop/`。根包的 `package-info.java` 声明 `@ApplicationModule` 与依赖白名单；根包放公开 API、DTO、事件，Controller/Service/Mapper 分别放 `internal.web`、`internal.service`、`internal.persistence`。业务包根本身不放业务类。 |
| 热插件 Java 包与 ID | Java 包用 `<组织反向域名>.business.<bizid>.plugin`，如 `com.acme.business.acmeorder.plugin`；不得占用 `com.sma.*`。清单中的 `implementationClass` 必须与入口类全名一致。新插件 ID 优先用 `<bizid>`，同业务多插件用 `<bizid>-<用途>`；入口 ID 独立，例如 `quote`。插件和入口 ID 仍须通过协议校验。 |
| Maven / npm | 普通单体业务沿用基座坐标和前端包，路由名、菜单标识、翻译和本地存储 key 带业务 ID。仅独立制品使用独立 Maven 坐标。 |
| 路由与权限 | 单体业务如 `/api/shop/orders`、`shop:order:read`。热插件走宿主分发路由，调用权限如 `extension:invoke:shop-quote`。 |
| 表与迁移 | 表名如 `shop_order`；迁移如 `backend/src/main/resources/db/migration/shop/V1__create_orders.sql`，使用自己的迁移版本序列。 |
| 语言包 | 后端用 `backend/src/main/resources/i18n/shop/messages.properties` 与 `messages_en.properties`；前端用 `frontend-next/src/locales/{zh-CN,en-US}/shop.json`。 |
| 配置与缓存 | 属性用 `business.<bizid>.*`，如 `business.shop.max-items`；按业务环境显式加载，不覆盖基座默认配置。缓存键包含业务、可信租户及需要的 portal/契约版本。详见[数据与配置](data-and-configuration.md)。 |

## 接入检查

跨请求缓存遵守[二开缓存契约](../cache-secondary-development.md)。生产Catalog/预热加载器按解代理后的真实模块核验归属，业务管理权限使用自己的前缀；禁止借用基座owner或绕过`common :: cache`直接使用Redis/Caffeine/Spring Cache。例外运行对象须走所属模块适配，不能自建免租户全局表。验证`CacheRegistrationOwnershipTests`、`CacheBoundaryTests`及业务自己的失效/权限/事务专项；P08阶段交付以进度文档为准。

1. 在业务接入说明记录：归属、后端包和前端路径、依赖的公开契约、命名占用、迁移与配置清单，以及已验证的版本组合。实际开发时只创建用到的目录。
2. 业务依赖公开 Query/API/SPI、公开 REST 或 SDK。引用基座对象保存稳定 ID 或必要快照，并在所属模块查权限；不要新增跨模块 SQL、外键、扫描配置、路由特例或全局 Jackson/MyBatis 修改。
3. 扩展点缺失时，先检查 `PermissionCatalog`、`FileAccessPolicy`、`RuleBindingPolicy`、`ExtensionCapability` 等现有契约是否匹配；需要新契约时说明所有权、授权、事务、幂等及兼容期。通用变更和业务改动保持独立提交与发布，基座测试不依赖具体客户代码。
4. 发版前从固定依赖干净构建，验证鉴权、迁移及受影响调用，记录版本组合和回退点。评审二开改动时核对是否写入基座所有路径；若同一组合仓库确需基座变更，应有独立的基座变更记录。升级时按[基座升级与兼容](platform-upgrades.md)处理，避免靠拷贝覆盖或手工重打旧制品解决冲突。
