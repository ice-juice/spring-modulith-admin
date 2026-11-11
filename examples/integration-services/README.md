# 应用身份、事务邮件与上传任务接入示例

本目录是基座main中的教学材料，不参与宿主Maven/Vite构建，不注册业务模块、路由或表。`IntegrationServicesExampleCompilationTests`只把源码编译到临时目录，校验公开API签名；这不是完整业务或运行验收。

实际接入应在业务分支创建自己的 `backend/src/main/java/com/sma/business/<bizid>/`。本例业务标识、包、application均为`capabilitydemo`；复制前替换为已登记且不冲突的业务ID。基座当前为开发期SNAPSHOT，部署时固定已验证的基座提交，不能把本例当热插件SDK能力。

## 准备配置

1. 当前租户创建身份域和登录客户端，application设为自己的业务ID。按[开发手册](../../docs/integration-services-developer-manual.md)8.4/8.13/8.14实现H5或Native登录。
2. 当前租户配置邮件账号，allowedApplications包含该业务ID；发布application为该业务ID、scene和templateCode均为`account-notice`的模板。示例只传`noticeId`，模板将其声明为必填TEXT、最大64字符，并发布要使用的语言版本。
3. 实现`NoticeAuthorization`并作为业务Bean注册。`mayRead`检查原actor、真实会员/通知归属、通知用途和收件偏好；后台和机器身份不豁免。`mayEnqueue`再次核验实际业务通知、收件人、模板、截止时间和幂等键。不能只比较application、持有subjectId或工作ID前缀就放行。未提供实现时本例无法启动，这是有意保留的接入要求。

## 调用与事务

应用用户请求先经过独立应用安全链；业务控制器声明字面量`@PermitApplicationUser("capabilitydemo")`。调用`currentSubject()`只读取已验证的本人基本主体，不得到邮箱或后台权限。

创建通知时，在业务同库事务内调用`enqueueNewNotice`，传入业务持久通知的realmId、subjectId、不可变noticeId和截止时间；将返回delivery.id与业务通知同事务保存。代码先走SubjectContactQuery的业务授权，再走MailSendApi独立发送授权；没有验证联系邮箱时返回Optional.empty，不回退到Google email claim。权限拒绝抛异常，不能当作“用户无邮箱”吞掉。

定时发送流程必须从自己持久化的、已授权任务恢复可信租户与工作身份，不能从外部请求body直接调用ActorContext.withinJob。身份/邮件配置均由当前租户选择，示例不携带外部账号密钥。

仅首次入队使用此方法。重试先查看已保存delivery的状态，不重新查当前邮箱并构造另一封邮件；同键不同收件人/变量/截止时间会冲突。邮件SENDING之后的不确定结果按UNKNOWN对账，不自动换键重发。入队成功不等于SMTP已发送或收件箱已送达。

## 上传示例

`AttachmentScenes`登记`capabilitydemo.document`场景（1MiB、TEXT、SERVER），依赖业务实现`AttachmentAuthorization.mayUpload`检查真实文档及原始调用人的归属。缺实现不能启动；后台/机器身份也不能自动豁免。业务须提供`capabilitydemo.scene.document`双语文案，并在当前租户创建授权给capabilitydemo的Local空间、创建并发布该场景配置。

业务从无外层事务的编排层调用`AttachmentUploadService.uploadSmallText`，传原始请求键、真实documentId、文件名、最多1MiB内容和可选已发布profileId。该方法演示公开FileUploadApi的初始化、分片、完成与原键恢复，不直接调用内部任务类。返回SCANNING仍是隔离文件，不能马上写成业务可读附件；后续扫描/绑定契约交付前不得绕过门禁。业务事务与上传不原子，业务失败须保留任务ID供查询/取消。

此教学方法仅用于小文本，不能对大文件使用readAllBytes；大文件按手册11.2保存任务ID、逐片计算摘要并流式传输。HTTP应用上传还要求真实客户端audience=capabilitydemo及WEB CSRF或Native Bearer。上传HTTP与完整安全流水线的验收状态以专项进度为准。

本示例不包含业务表、路由、授权实现或自动任务；这些由业务自己拥有。不得引用identity/mail/file的internal或Mapper，也不得把此教学包直接放入main生产源码。
