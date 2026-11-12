# 网上商城：单体二开空目录示例

这是可以留在 `main` 的目录样例。它位于 `E:/juiceProgram/spring Modulith` 项目中：项目总 `README.md`、`backend/` 和 `examples/` 同级，本样例在 `examples/shop-layout/` 内。下面的 `backend/`、`frontend-next/` 是用来展示路径的镜像目录，里面只有 `.gitkeep` 占位。

| 本例中的目录（相对于 `examples/shop-layout/`） | 真实二开时的用途 |
| --- | --- |
| `backend/src/main/java/com/sma/business/shop/internal/web/` | 商城 Controller，例如将来的 `ShopOrderController.java` |
| `backend/src/main/java/com/sma/business/shop/internal/service/` | 商城业务逻辑和事务 |
| `backend/src/main/java/com/sma/business/shop/internal/persistence/` | 商城实体和 Mapper |
| `backend/src/main/resources/db/migration/shop/` | 历史同库布局空占位；当前路由业务应改用db/business/shop并声明BusinessSchemaCatalog，不在此创建脚本 |
| `backend/src/main/resources/i18n/shop/` | 商城后端语言包 |
| `backend/src/test/java/com/sma/business/shop/` | 商城模块和接口测试 |
| `frontend-next/src/business/shop/` | 商城管理页面、业务 API 调用与组件 |

开始真实开发时，检出业务分支，从**项目总 `README.md` 所在的那一层**打开已有的 `backend/` 和 `frontend-next/`，按表中的相对路径创建需要的文件。后端 `com/sma/business/` 与基座的 `com/sma/admin/` 同级；前端 `src/business/` 与基座的 `src/features/` 同级。商城有自己的 `package-info.java`、`@ApplicationModule` 和依赖白名单，仍由同一个 Spring Boot 启动类加载。

真实业务沿用现有构建：Maven 产出一个宿主 JAR，Vue 产出一套管理前端静态文件；同一业务提交的两个产物一起发版。本样例没有 Controller、模块声明、路由、配置、DDL 或构建文件，Maven 和 Vite 都不会打包它。

操作步骤见[二次开发与包管理](../../docs/agent-rules/secondary-development.md#开发构建与部署)。
