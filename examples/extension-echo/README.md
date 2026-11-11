# 无状态业务插件示例

这是通用 JSON 回显示例，不包含计费、风控等具体业务，不注册 Spring Bean。

需要 Java 21。根目录先执行 `mvn -pl extension-sdk -am install -DskipTests`，再执行 `mvn -f examples/extension-echo/pom.xml package`。
产物是 `target/example-extension-echo-1.0.0.jar`，SDK 使用 provided 范围，不能打入插件 JAR。无需 Boot repackage/shade。

接入顺序见 [后端接入手册](../../docs/extension/backend-guide.md)：创建分组和 `echo` 插件，计算产物 SHA-256，独立强化授权上传，worker 校验并扫描，通过后独立强化授权发布；最后给用户分配 `extension:invoke:echo`。

也可以只上传 `src/main/java/example/EchoPlugin.java`。上传命令的参数必须是 `{"manifest": ...}`，其中 manifest 使用 `META-INF/sma-plugin.json` 的内容。源码不会在业务宿主或安全服务中编译；worker 返回的新 JAR hash 是发布授权绑定的对象。

部署生效后向 `/api/extension/invoke/{deployment}/echo/echo` 发送 JSON，使用业务系统登录令牌。响应原样返回 JSON。该入口的 `SERVICE` 声明代表通用业务入口，HTTP 是宿主提供的调用适配。

修改代码时同时更新 Maven 版本和插件清单版本，发布新制品；同一个 pluginId + version 不允许指向不同字节。灰度发布采用新 bundleId，稳定版本向量保持不变。有状态插件必须使用 work 入口和稳定幂等键，不能照搬这个无状态示例来重试外部副作用。
