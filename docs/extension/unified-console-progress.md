# 扩展管理统一入口与操作验证

用户已确认：后台登录后直接进入管理页；上传、发布、回滚等写操作再验证操作密码和TOTP。变更范围为宿主 extension 模块、extension-security 身份适配、协议新增动作、frontend-next 扩展页面及部署文档。保留核心认证、已发布SDK和原有发布账号API。

## 设计与边界

- 浏览器使用后台会话请求宿主的专用发布代理；代理每次校验当前人类身份和 `extension:console:view`，仅转发明确允许的控制面路径。后台token不转发到发布服务，浏览器也不接触控制面session或机器凭据。
- 独立 `CONSOLE` 机器身份只证明宿主来源。发布服务按机器ID、租户、portal和稳定用户ID查找显式绑定，不接受客户端声明的发布角色。发布账号/分组权限及操作因素仍由发布服务保管。
- 控制面session绑定原后台会话摘要、映射修订、机器凭据修订和发布凭据版本；离开/刷新页面可以自动恢复连接，退出、锁屏及撤权后宿主入口拒绝。独立服务短租约和强校验继续生效；操作授权仍逐次绑定具体参数/哈希，不能靠后台“已验证”字段代替。
- 敏感命令继续经120秒挑战、60秒单次授权及操作密码+TOTP。无自动写重试；不确定结果查询原幂等回执。首次安全因素绑定使用受保护注册票据+操作密码，登录表单从正常管理页移除。账号映射建立/变更需独立管理员强化授权，初始映射仅由发布机受保护文件引导。
- 宿主被攻陷可能泄露其可代理的数据及正在输入的因素；同JVM/root失陷不能被本方案消除。机器凭据本身不能生成已验证发布授权。生产须独立部署、HTTPS校验证书和受保护机器凭据。
- 协议新增动作/机器类型需宿主与控制面同批升级；旧独立登录接口保留供迁移/恢复，正常页面不再使用。未配置服务显示配置状态，未绑定显示授权状态，不伪装空数据或降级放行。

## 实施与验收

| 顺序 | 内容 | 验证 | 状态 |
|---|---|---|---|
| 1 | 发布服务身份映射、机器范围、会话及因素绑定 | 错机/跨租户/映射撤销/会话串用/OTP重放/无因素不能写 | 完成：28项专项通过 |
| 2 | 宿主专用代理及配置 | 当前身份不可伪造、路径白名单、TLS/限额/禁止重试、架构 | 完成：23项专项通过 |
| 3 | 页面自动连接、因素设置与敏感操作弹窗 | 单元、类型/lint/双语、浏览器直接进入和刷新/锁屏/拒绝路径 | 完成：完整前端verify、7项浏览器通过 |
| 4 | 联合回归、部署使用文档与本地验证 | 根reactor、前端检查、服务健康；真实发布环境不足明确记录 | 实现与本地验证完成；常驻发布服务接线仍待配置 |

保留工作区并行的规则中心、账号安全和皮肤改动；测试使用独立数据库与以target结尾的输出目录，不清空当前业务库。

阶段1：根reactor `backend/mvnw.cmd -B -ntp -f pom.xml -pl extension-security -am test -Dtest=ConsoleIdentityTests,IdentityServiceTests,CanaryGuardTests,ReleaseWorkflowTests,SecurityHttpTests -Dsurefire.failIfNoSpecifiedTests=false`，28项通过，无失败/错误/跳过；日志 `backend/target/extension-unified-security-final.log`。本轮H2事务与HTTP控制流程，不替代独立MySQL/TLS部署验收。

阶段2：根reactor `-pl backend -am test -Dtest=ConsoleProxyTests,ArchitectureRulesTests,I18nBundlesTests -Dsurefire.failIfNoSpecifiedTests=false -Dsma.verify.directory=target/extension-unified/target`，23项通过，日志 `extension-unified-proxy.log`。测试验证后台机器/未授权用户拒绝、真实主体ID不丢精度、不转发后台token、独立机器与发布凭据分离、命令网络失败只发送一次、路径拒绝及响应上限取消。传输为mock，真实证书与联调在最终阶段另记。

阶段3：`npm run verify` 完整通过（包括baseline、类型、lint、双语、全部单元、生产构建与包预算），`extension-unified-frontend-final.log`。`npx playwright test --config playwright.extension.config.ts` 7项通过，`extension-unified-ui-final.log`；覆盖自动进入/刷新无第二次登录、操作强化验证及原字节上传、无配置/未绑定状态、首次绑定因素和秘密不落存储，以及TableWorkspace桌面/手机行为。新增恢复入口仅要求发布操作密码和一次性恢复码，不依赖另一套登录密码；其服务端拒绝/撤销用例随阶段4回归。30秒只读心跳维持控制面短会话，不自动重发写请求。

阶段4结果（2026-11-11）：

- 根标准入口 `tools/dev/verify-backend.ps1 -Standard -LogPath backend/target/extension-unified-final-verify.log -MavenArgs @('-Dsma.verify.directory=target/extension-unified/target')` BUILD SUCCESS；专用宿主库 `sma_test_extension_acceptance` 和发布库 `sma_test_release_https` 均保留UTC连接参数。268项通过（协议7、运行时8、发布服务31、worker6、宿主216），无排除、失败、错误或跳过。发布服务真实MySQL+HTTPS验证已加入身份绑定的强化命令、CONSOLE交换、读取、敏感命令和映射撤销后401；测试CA由客户端显式信任，无trust-all。
- 随后补齐管理员重置其他用户时可选设置新操作密码，保留旧Reset二参构造和省略字段语义，禁止管理员以该接口重置自己绕过因素。`backend/mvnw.cmd -B -ntp -f pom.xml -pl extension-security -am verify` 全服务复验/打包成功：协议7、运行时8、发布服务32项（含真实MySQL+HTTPS），日志 `extension-unified-recovery-verify.log`。这是对后续安全服务改动的补充验证，不把第一次根运行改写成269项。
- 账号绑定界面改为结构化字段，用户/租户ID保持字符串。最终 `npm run verify` 再次完整通过，71项前端单元；`extension-unified-frontend-delivery.log`。最终扩展浏览器专项4项通过（含超出JS安全整数的用户ID和写前强化验证），`extension-unified-ui-delivery.log`；之前的4项TableWorkspace检查已通过，未重复修改表格布局。最终构建初始150KiB gzip、34个懒加载模块，静态包检查通过。
- 本地后端已用验证后的宿主JAR更新并启动，PID40488，健康接口UP，匿名调用控制面代理返回401。保留原认证key-file和旧JAR备份 `backend/target/run/before-unified-20261111-144913.jar`，日志 `backend/target/run/unified-20261111-144913.log`。前端5174使用现有Vite服务，新界面刷新生效。
- 本机8092仍没有常驻发布安全服务，宿主console开关默认关闭；正常页面显示明确“尚未配置”提示，不退回第二套登录，也不绕过校验。生产连接配置、独立账号映射和真实用户因素注册尚未执行。部署步骤见 [统一入口接线](unified-console-deployment.md)。本次不包含Docker/Grype、真实多节点、外部WORM或生产灾备验收。
