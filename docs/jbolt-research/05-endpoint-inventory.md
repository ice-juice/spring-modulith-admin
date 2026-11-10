# JBolt 路由与控制器动作清单（自动生成）

> 由 tools/jbolt-research/inventory_controllers.py 扫描源码生成。JFinal 约定：URL = 路由前缀 + '/' + 方法名，index 方法对应路由前缀本身。

共 80 个路由前缀，805 个动作方法。

| Routes | URL 前缀 | 控制器 | 视图目录 | 类级注解 | 动作数 |
|---|---|---|---|---|---|
| @Path-scan | /admin/ai/app | AiAppAdminController | /_view/admin/ai/aiapp | @CheckPermission(PermissionKey.ADMIN_AI_APP) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/app", viewPath = "/_view/admin/ai/aiapp") | 89 |
| @Path-scan | /admin/ai/appMessage | AiAppMessageAdminController |  | @UnCheck @Path(value = "/admin/ai/appMessage") | 6 |
| @Path-scan | /admin/ai/appSession | AiAppSessionAdminController |  | @UnCheck @Path(value = "/admin/ai/appSession") | 4 |
| @Path-scan | /admin/ai/common | JBoltAICommonController |  | @UnCheck @Path(value = "/admin/ai/common") | 7 |
| @Path-scan | /admin/ai/config | JBoltAIConfigController | /_view/_admin/globalconfig | @CheckPermission(PermissionKey.ADMIN_AI_CONFIG) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/config", viewPath = "/_view/_admin/globalconfig") | 1 |
| @Path-scan | /admin/ai/dashboard | AiDashboardAdminController |  | @Path("/admin/ai/dashboard") @UnCheckIfSystemAdmin @CheckPermission(PermissionKey.DASHBOARD) | 1 |
| @Path-scan | /admin/ai/doc | AiDocAdminController | /_view/admin/ai/aidoc | @CheckPermission(PermissionKey.ADMIN_AI_DOC) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/doc", viewPath = "/_view/admin/ai/aidoc") | 8 |
| @Path-scan | /admin/ai/embedding | AiEmbeddingAdminController | /_view/admin/ai/embedding | @CheckPermission(PermissionKey.ADMIN_AI_EMBEDDING) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/embedding", viewPath = "/_view/admin/ai/embedding") | 14 |
| @Path-scan | /admin/ai/file | AiFileAdminController | /_view/admin/ai/aifile | @CheckPermission(PermissionKey.ADMIN_AI_FILE) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/file", viewPath = "/_view/admin/ai/aifile") | 18 |
| @Path-scan | /admin/ai/fileTag | AiFileTagAdminController | /_view/admin/ai/aifiletag | @CheckPermission(PermissionKey.ADMIN_AI_FILE_TAG) @UnCheckIfSystemAdmin @Before(JBoltAdminAuthInterceptor.class) @Path(value = "/admin/ai/fileTag", viewPath = "/_view/admin/ai/aifiletag") | 9 |
| @Path-scan | /admin/ai/functionParam | AiFunctionParamAdminController | /_view/admin/ai/aifunctionparam | @CheckPermission(PermissionKey.ADMIN_AI_TOOLS) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/functionParam", viewPath = "/_view/admin/ai/aifunctionparam") | 9 |
| @Path-scan | /admin/ai/genUI | JBoltAIGenUIController | /_view/admin/ai/genui | @CheckPermission(PermissionKey.ADMIN_AI_APP) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/genUI",viewPath = "/_view/admin/ai/genui") | 2 |
| @Path-scan | /admin/ai/knowledgeBase | AiKnowledgeBaseAdminController | /_view/admin/ai/aiknowledgebase | @CheckPermission(PermissionKey.ADMIN_AI_KB) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/knowledgeBase", viewPath = "/_view/admin/ai/aiknowledgebase") | 10 |
| @Path-scan | /admin/ai/llm | AILlmAdminController | /_view/admin/ai/llm | @CheckPermission(PermissionKey.ADMIN_AI_LLM) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/llm", viewPath = "/_view/admin/ai/llm") | 13 |
| @Path-scan | /admin/ai/page | AiPageAdminController | /_view/admin/ai/aipage | @CheckPermission(PermissionKey.ADMIN_AI_PAGE) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/page", viewPath = "/_view/admin/ai/aipage") | 22 |
| @Path-scan | /admin/ai/smartSearch | JBoltAiSmartSearchAdminController | /_view/admin/ai/smartsearch | @UnCheckIfSystemAdmin @Path(value = "/admin/ai/smartSearch", viewPath = "/_view/admin/ai/smartsearch") | 1 |
| @Path-scan | /admin/ai/test/render | TestAiRenderController |  | @Path("/admin/ai/test/render") | 1 |
| @Path-scan | /admin/ai/tools | AiToolsAdminController | /_view/admin/ai/aifunction | @CheckPermission(PermissionKey.ADMIN_AI_TOOLS) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/tools", viewPath = "/_view/admin/ai/aifunction") | 22 |
| @Path-scan | /admin/ai/vdb | AiVdbAdminController | /_view/admin/ai/vdb | @CheckPermission(PermissionKey.ADMIN_AI_VDB) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/vdb", viewPath = "/_view/admin/ai/vdb") | 16 |
| @Path-scan | /admin/customBusiness | CustomBusinessAdminController | /_view/_admin/customtablerender/custombusiness | @CheckPermission(PermissionKey.ADMIN_CUSTOM_BUSINESS) @UnCheckIfSystemAdmin @Path(value = "/admin/customBusiness", viewPath = "/_view/_admin/customtablerender/custombusiness") | 10 |
| @Path-scan | /admin/jboltfileChange | JboltFileChangeAdminController |  | @Path(value = "/admin/jboltfileChange") @UnCheck | 1 |
| @Path-scan | /ai/app/guest | AiAppGuestController | /_view/ai/aiapp | @Before(JBoltWebsocketGuestUseHeaderInterceptor.class) @Path(value = "/ai/app/guest", viewPath = "/_view/ai/aiapp") | 10 |
| @Path-scan | /ai/app/pwd | AiAppPwdController | /_view/ai/aiapp | @Before(JBoltWebsocketGuestInterceptor.class) @Path(value = "/ai/app/pwd", viewPath = "/_view/ai/aiapp") | 10 |
| @Path-scan | /ai/app/system | AiAppSystemController | /_view/ai/aiapp | @Path(value = "/ai/app/system", viewPath = "/_view/ai/aiapp") | 6 |
| @Path-scan | /ai/app/systemIframe | AiAppSystemIFrameController | /_view/ai/aiapp | @Path(value = "/ai/app/systemIframe", viewPath = "/_view/ai/aiapp") @Before(JBoltCrossOriginInterceptor.class) @CrossOrigin | 9 |
| @Path-scan | /api/jwt/refresh | JBoltRefreshJwtApiController |  | @CrossOrigin @Path("/api/jwt/refresh") | 1 |
| @Path-scan | /api/user | JBoltUserInfoApiController |  | @CrossOrigin @Path("/api/user") | 2 |
| @Path-scan | /api/user/auth | JBoltUserAuthApiController |  | @CrossOrigin @Path("/api/user/auth") | 2 |
| @Path-scan | /api/ws/guest/token | JBoltWebsocketGuestTokenApiController |  | @CrossOrigin @Path("/api/ws/guest/token") | 2 |
| @Path-scan | /api/ws/guest/token/v2 | JBoltWebsocketGuestTokenApiV2Controller |  | @Before(JBoltCrossOriginInterceptor.class) @CrossOrigin @Path("/api/ws/guest/token/v2") | 2 |
| AdminRoutes | /admin | AdminIndexController | /_view/_admin/index |  | 13 |
| AdminRoutes | /admin/dept | DeptAdminController | /_view/_admin/dept | @CheckPermission(PermissionKey.DEPT) @UnCheckIfSystemAdmin | 18 |
| AdminRoutes | /admin/dictionary | DictionaryAdminController | /_view/_admin/dictionary | @CheckPermission({PermissionKey.DICTIONARY}) @UnCheckIfSystemAdmin @OnlySaasPlatform | 22 |
| AdminRoutes | /admin/dictionarytype | DictionaryTypeAdminController | /_view/_admin/dictionary/type | @CheckPermission({PermissionKey.DICTIONARY}) @UnCheckIfSystemAdmin @OnlySaasPlatform | 9 |
| AdminRoutes | /admin/globalconfig | GlobalConfigAdminController | /_view/_admin/globalconfig | @CheckPermission(PermissionKey.GLOBALCONFIG) @UnCheckIfSystemAdmin | 14 |
| AdminRoutes | /admin/globalconfig/type | GlobalConfigTypeAdminController | /_view/_admin/globalconfig/type | @CheckPermission(PermissionKey.GLOBALCONFIG) @UnCheckIfSystemAdmin | 10 |
| AdminRoutes | /admin/hiprint | HiprintAdminController | /_view/_admin/hiprint | @CheckPermission(PermissionKey.HIPRINT_DESIGN) @UnCheckIfSystemAdmin @OnlySaasPlatform | 12 |
| AdminRoutes | /admin/jboltstyle | JBoltStyleAdminController | /_view/_admin |  | 1 |
| AdminRoutes | /admin/loginlog | LoginLogAdminController | /_view/_admin/loginlog | @CheckPermission(PermissionKey.JBOLT_LOGIN_LOG) @UnCheckIfSystemAdmin | 2 |
| AdminRoutes | /admin/msgcenter | JBoltMsgCenterAdminController | /_view/_admin/msgcenter | @UnCheck | 14 |
| AdminRoutes | /admin/neditor/preview | NeditorPreviewAdminController | /_view/_admin | @UnCheck | 1 |
| AdminRoutes | /admin/neditor/upload | NeditorUploadAdminController | /_view/_admin |  | 5 |
| AdminRoutes | /admin/onlineuser | OnlineUserAdminController | /_view/_admin/onlineuser | @CheckPermission(PermissionKey.ONLINE_USER) @UnCheckIfSystemAdmin | 4 |
| AdminRoutes | /admin/permission | PermissionAdminController | /_view/_admin/permission | @CheckPermission(PermissionKey.PERMISSION) @UnCheckIfSystemAdmin | 14 |
| AdminRoutes | /admin/pjaxerror | PjaxErrorController | /_view/_admin |  | 1 |
| AdminRoutes | /admin/post | PostAdminController | /_view/_admin/post | @CheckPermission(PermissionKey.POST) @UnCheckIfSystemAdmin | 13 |
| AdminRoutes | /admin/qiniu | QiniuAdminController | /_view/_admin/qiniu | @CheckPermission(PermissionKey.QINIU) @UnCheckIfSystemAdmin @OnlySaasPlatform | 12 |
| AdminRoutes | /admin/qiniu/bucket | QiniuBucketAdminController | /_view/_admin/qiniu/bucket | @CheckPermission(PermissionKey.QINIU_BUCKET) @UnCheckIfSystemAdmin @OnlySaasPlatform | 12 |
| AdminRoutes | /admin/redis | RedisAdminController | /_view/_admin | @CheckPermission(PermissionKey.JBOLT_SERVER_MONITOR) @UnCheckIfSystemAdmin @OnlySaasPlatform | 1 |
| AdminRoutes | /admin/role | RoleAdminController | /_view/_admin/role | @CheckPermission(PermissionKey.ROLE) @UnCheckIfSystemAdmin | 12 |
| AdminRoutes | /admin/rolepermission | RolePermissionAdminController | /_view/_admin/rolepermission | @CheckPermission(PermissionKey.ROLE) @UnCheckIfSystemAdmin | 5 |
| AdminRoutes | /admin/sensitiveword | SensitiveWordAdminController | /_view/_admin/sensitiveword | @CheckPermission(PermissionKey.SENSITIVE_WORD) @UnCheckIfSystemAdmin | 13 |
| AdminRoutes | /admin/servermonitor | ServerMonitorAdminController | /_view/_admin/servermonitor | @CheckPermission(PermissionKey.JBOLT_SERVER_MONITOR) @UnCheckIfSystemAdmin | 7 |
| AdminRoutes | /admin/summernote/upload | SummernoteUploadAdminController | /_view/_admin |  | 2 |
| AdminRoutes | /admin/sysnotice | SysNoticeAdminController | /_view/_admin/msgcenter/sysnotice | @CheckPermission(PermissionKey.SYS_NOTICE) | 15 |
| AdminRoutes | /admin/systemlog | SystemLogAdminController | /_view/_admin/systemlog | @CheckPermission(PermissionKey.SYSTEMLOG) @UnCheckIfSystemAdmin | 1 |
| AdminRoutes | /admin/todo | TodoAdminController | /_view/_admin/msgcenter/todo | @UnCheck | 10 |
| AdminRoutes | /admin/topnav | TopnavAdminController | /_view/_admin/topnav | @CheckPermission(PermissionKey.TOPNAV) @UnCheckIfSystemAdmin | 14 |
| AdminRoutes | /admin/topnav/menu | TopnavMenuAdminController | /_view/_admin/topnav/menu | @CheckPermission(PermissionKey.TOPNAV) @UnCheckIfSystemAdmin | 5 |
| AdminRoutes | /admin/user | UserAdminController | /_view/_admin/user | @CheckPermission(PermissionKey.USER) @UnCheckIfSystemAdmin | 24 |
| AdminRoutes | /admin/userconfig | UserConfigAdminController | /_view/_admin/userconfig |  | 3 |
| ApiTestRoutes | /api/test | ApiTestController |  | @CrossOrigin | 9 |
| AppDevCenterAdminRoutes | /admin/app | ApplicationAdminController | /_view/_admin/_app_dev_center/app | @CheckPermission(PermissionKey.APPLICATION) @UnCheckIfSystemAdmin @OnlySaasPlatform | 16 |
| DevDocAdminRoutes | /admin/devdoc/database | JBoltDatabaseDevDocController | /_view/_admin/_dev_doc/database | @CheckPermission(PermissionKey.APPLICATION) @UnCheckIfSystemAdmin @OnlySaasPlatform | 5 |
| JBoltCodeGenRoutes | /admin/codegen | CodeGenAdminController | /_view/_admin/_jbolt_code_gen/ | @CheckPermission(PermissionKey.JBOLT_CODE_GEN) @UnCheckIfSystemAdmin | 64 |
| JBoltCodeGenRoutes | /admin/codegen/modelattr | CodeGenModelAttrAdminController | /_view/_admin/_jbolt_code_gen/modelattr | @CheckPermission(PermissionKey.JBOLT_CODE_GEN) @UnCheckIfSystemAdmin | 25 |
| WebRoutes | / | IndexController | /_view/_web |  | 1 |
| WechatAdminRoutes | /admin/wechat/autoreply | WechatAutoReplyAdminController | /_view/_admin/_wechat/autoreply | @UnCheckIfSystemAdmin @OnlySaasPlatform | 10 |
| WechatAdminRoutes | /admin/wechat/config | WechatConfigAdminController | /_view/_admin/_wechat/config | @OnlySaasPlatform | 7 |
| WechatAdminRoutes | /admin/wechat/keywords | WechatKeywordsAdminController | /_view/_admin/_wechat/autoreply/keywords | @CheckPermission(PermissionKey.WECHAT_AUTOREPLY_KEYWORDS) @OnlySaasPlatform | 7 |
| WechatAdminRoutes | /admin/wechat/media | WechatMediaAdminController | /_view/_admin/_wechat/media | @CheckPermission(PermissionKey.WECHAT_MEDIA) @OnlySaasPlatform | 7 |
| WechatAdminRoutes | /admin/wechat/menu | WechatMenuAdminController | /_view/_admin/_wechat/menu | @CheckPermission(PermissionKey.WECHAT_MENU) @OnlySaasPlatform | 9 |
| WechatAdminRoutes | /admin/wechat/mpinfo | WechatMpinfoAdminController | /_view/_admin/_wechat/mpinfo | @CheckPermission(PermissionKey.WECHAT_MPINFO) @OnlySaasPlatform | 19 |
| WechatAdminRoutes | /admin/wechat/replycontent | WechatReplyContentAdminController | /_view/_admin/_wechat/autoreply/replycontent |  | 12 |
| WechatAdminRoutes | /admin/wechat/user | WechatUserAdminController | /_view/_admin/_wechat/user | @CheckPermission(PermissionKey.WECHAT_USER) @OnlySaasPlatform | 5 |
| WechatApiRoutes | /api/wechat | JBoltWechatApiController |  |  | 2 |
| WechatApiRoutes | /api/wxa | JBoltWxaApiController |  | @CrossOrigin | 9 |
| WechatRoutes | /wx/msg | WechatMsgController |  |  | 0 |
| WechatRoutes | /wxa/msg | WechatWxaMsgController |  |  | 0 |
| WechatTestRoutes | /test/wechat | JBoltWechatTestApiController | /_view/_test/wechat/ |  | 1 |

## /admin/ai/app  (AiAppAdminController)

源码：com/jboltai/platform/ai/admin/aiapp/AiAppAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_AI_APP) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/app", viewPath = "/_view/admin/ai/aiapp")

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| count |  |  |
| copy |  |  |
| downloadAppJson |  |  |
| addByJson |  |  |
| detail |  |  |
| designer |  |  |
| updateCustomCssStyle | @Before(Tx.class) | Long id, String customCssStyle |
| updateCustomJavascript |  | Long id, String customJavascript |
| knowledgeConfigTopPortal |  |  |
| knowledgeDatasPortal |  |  |
| functionCallDatasPortal |  |  |
| chooseFunctions |  |  |
| chooseKnowledgeBases |  |  |
| linkKnowledgeBaseDatas |  |  |
| linkFunctionCallDatas |  |  |
| unbindKnowledgeBase | @Before(Tx.class) |  |
| unbindFunctionCall | @Before(Tx.class) |  |
| logout | @Clear |  |
| publishConfigDialog |  |  |
| publishConfigPortal |  |  |
| linkFunctionDatas |  |  |
| permissions |  |  |
| aiKnowledgeBases |  |  |
| aiFunctions |  |  |
| aiFunctionChooseDatas |  |  |
| submitFunctions |  |  |
| aiKnowledgeBaseChooseDatas |  |  |
| submitKnowledgeBases |  |  |
| updateOneColumn | @Before(Tx.class) | @Para("" |
| toggleIsChatLlmEnableThinking |  |  |
| toggleIsFunctionLlmEnableThinking |  |  |
| toggleIsFuncResultLlmEnableThinking |  |  |
| toggleIsKbResultLlmEnableThinking |  |  |
| aiKnowledgeCall |  | @Para("id" |
| datas |  |  |
| sessionHistory |  |  |
| removeLinkKnowledgeBases | @Before(Tx.class) |  |
| removeLinkFunctionCall | @Before(Tx.class) |  |
| designerTest |  |  |
| test |  | @Para(value = "isDesignerMode",defaultValue = "false" |
| clearSessionMsgs | @UnCheck |  |
| open | @UnCheck |  |
| genTableBySql |  | @Para("datasource" |
| jdadHistorySession |  |  |
| jboltDatabaseAiDesigner |  | @Para(value = "datasource",defaultValue = DbKit.MAIN_CONFIG_NAME |
| add |  |  |
| save | @Before(Tx.class) | @Para("aiApp" |
| edit |  |  |
| qrcode |  |  |
| initPublish |  |  |
| publish | @Before(Tx.class) | @Para("id" |
| cancelPublish | @Before(Tx.class) |  |
| update | @Before(Tx.class) | @Para("aiApp" |
| delete | @Before(Tx.class) |  |
| up | @Before(Tx.class) |  |
| down | @Before(Tx.class) |  |
| initSortRank | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |
| toggleIsNeedRewriteQuery | @Before(Tx.class) |  |
| toggleIsShowReference | @Before(Tx.class) |  |
| toggleIsShowAiMsgBottomOptBtnTts | @Before(Tx.class) |  |
| toggleIsShowAiMsgBottomOptBtnReChat | @Before(Tx.class) |  |
| toggleIsShowRefFragment | @Before(Tx.class) |  |
| toggleIsShowFileDownload | @Before(Tx.class) |  |
| toggleIsOnlyKnowledgeMode | @Before(Tx.class) |  |
| toggleIsOnlyFunctionCallMode | @Before(Tx.class) |  |
| toggleIsKeepContext | @Before(Tx.class) |  |
| toggleIsLeftBoxExtends | @Before(Tx.class) |  |
| toggleIsRightBoxExtends | @Before(Tx.class) |  |
| toggleIsAiMsgInputBoxExtends | @Before(Tx.class) |  |
| toggleIsTopBoxExtends | @Before(Tx.class) |  |
| getUiExtendCode |  |  |
| delCustomJavascriptFile |  |  |
| loadCustomJavascriptCode |  |  |
| loadCustomDefaultJavascriptCode |  |  |
| updateUiExtendCode |  | String dir,String code,Long id |
| toggleIsBottomBoxExtends | @Before(Tx.class) |  |
| uploadIcon |  |  |
| uploadAiAvatar |  |  |
| uploadBgImg |  |  |
| uploadChatEmptyBgImg |  |  |
| aiCotClassAutocompleteDatas |  |  |
| refreshAICotClass |  |  |
| autocompleteDatas | @UnCheck |  |
| options | @UnCheck |  |
| runIframePreview |  |  |
| runIframeDownload |  |  |
| runIframeCode |  |  |

## /admin/ai/appMessage  (AiAppMessageAdminController)

源码：com/jboltai/platform/ai/admin/aiappmessage/AiAppMessageAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@UnCheck @Path(value = "/admin/ai/appMessage")

| 动作 | 注解 | 参数 |
|---|---|---|
| userHistoryDatas |  |  |
| add |  |  |
| save | @Before(Tx.class) | @Para("aiAppMessage" |
| edit |  |  |
| update | @Before(Tx.class) | @Para("aiAppMessage" |
| delete | @Before(Tx.class) |  |

## /admin/ai/appSession  (AiAppSessionAdminController)

源码：com/jboltai/platform/ai/admin/aiappsession/AiAppSessionAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@UnCheck @Path(value = "/admin/ai/appSession")

| 动作 | 注解 | 参数 |
|---|---|---|
| userHistoryDatas |  |  |
| save | @Before(Tx.class) | @Para("aiAppSession" |
| update | @Before(Tx.class) | @Para("aiAppSession" |
| delete | @Before(Tx.class) |  |

## /admin/ai/common  (JBoltAICommonController)

源码：com/jboltai/platform/ai/admin/rescenter/JBoltAICommonController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@UnCheck @Path(value = "/admin/ai/common")

| 动作 | 注解 | 参数 |
|---|---|---|
| llmTypes |  |  |
| embeddingTypes |  |  |
| vdbTypes |  |  |
| getAiModelBySn |  | String sn |
| getEmbeddingModelBySn |  | String sn |
| aiRateLimitTypes |  |  |
| llmUseForOptions |  |  |

## /admin/ai/config  (JBoltAIConfigController)

源码：com/jboltai/platform/ai/admin/rescenter/JBoltAIConfigController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_AI_CONFIG) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/config", viewPath = "/_view/_admin/globalconfig")

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |

## /admin/ai/dashboard  (AiDashboardAdminController)

源码：com/jboltai/platform/ai/admin/dashboard/AiDashboardAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@Path("/admin/ai/dashboard") @UnCheckIfSystemAdmin @CheckPermission(PermissionKey.DASHBOARD)

| 动作 | 注解 | 参数 |
|---|---|---|
| summarys |  |  |

## /admin/ai/doc  (AiDocAdminController)

源码：com/jboltai/platform/ai/admin/aidoc/AiDocAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_AI_DOC) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/doc", viewPath = "/_view/admin/ai/aidoc")

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| add |  |  |
| save | @Before(Tx.class) | @Para("aiDoc" |
| edit |  |  |
| update | @Before(Tx.class) | @Para("aiDoc" |
| delete | @Before(Tx.class) |  |
| linkFileDocs |  | @Para("aiAppId" |

## /admin/ai/embedding  (AiEmbeddingAdminController)

源码：com/jboltai/platform/ai/admin/rescenter/embedding/AiEmbeddingAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_AI_EMBEDDING) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/embedding", viewPath = "/_view/admin/ai/embedding")

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| add |  |  |
| save | @Before(Tx.class) | @Para("aiEmbedding" |
| getEmbeddingModelDimension |  | @Para("aiEmbedding" |
| formTest |  | @Para("aiEmbedding" |
| testById |  |  |
| edit |  |  |
| update | @Before(Tx.class) | @Para("aiEmbedding" |
| delete | @Before(Tx.class) |  |
| toggleIsLocal | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |
| autocompleteDatas | @UnCheck |  |
| options | @UnCheck |  |

## /admin/ai/file  (AiFileAdminController)

源码：com/jboltai/platform/ai/admin/aifile/AiFileAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_AI_FILE) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/file", viewPath = "/_view/admin/ai/aifile")

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| add |  |  |
| pdfViewer | @UnCheck |  |
| addQa |  |  |
| addWebsite |  |  |
| addWebsiteQa |  |  |
| urlToMd |  | @Para("url" |
| reTrain |  |  |
| submitMulti |  | @Para("aiKnowledgeBaseId" |
| submitByWebsite | @Before(Tx.class) | @Para("aiFile" |
| upload |  |  |
| uploadFiles |  |  |
| save |  | @Para("aiFile" |
| edit |  |  |
| update | @Before(Tx.class) | @Para("aiFile" |
| delete | @Before(Tx.class) |  |
| autocompleteDatas | @UnCheck |  |

## /admin/ai/fileTag  (AiFileTagAdminController)

源码：com/jboltai/platform/ai/admin/aifiletag/AiFileTagAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_AI_FILE_TAG) @UnCheckIfSystemAdmin @Before(JBoltAdminAuthInterceptor.class) @Path(value = "/admin/ai/fileTag", viewPath = "/_view/admin/ai/aifiletag")

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| add |  |  |
| save | @Before(Tx.class) | @Para("aiFileTag" |
| edit |  |  |
| update | @Before(Tx.class) | @Para("aiFileTag" |
| deleteByIds | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| autocompleteDatas | @UnCheck |  |

## /admin/ai/functionParam  (AiFunctionParamAdminController)

源码：com/jboltai/platform/ai/admin/aifunctionparam/AiFunctionParamAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_AI_TOOLS) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/functionParam", viewPath = "/_view/admin/ai/aifunctionparam")

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| add |  |  |
| save | @Before(Tx.class) | @Para("aiFunctionParam" |
| edit |  |  |
| update | @Before(Tx.class) | @Para("aiFunctionParam" |
| toggleIsRequired | @Before(Tx.class) |  |
| updateColumn | @Before(Tx.class) | @Para("" |
| sync | @Before(Tx.class) |  |

## /admin/ai/genUI  (JBoltAIGenUIController)

源码：com/jboltai/platform/ai/admin/genui/JBoltAIGenUIController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_AI_APP) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/genUI",viewPath = "/_view/admin/ai/genui")

| 动作 | 注解 | 参数 |
|---|---|---|
| preview |  |  |
| clear |  |  |

## /admin/ai/knowledgeBase  (AiKnowledgeBaseAdminController)

源码：com/jboltai/platform/ai/admin/aiknowledgebase/AiKnowledgeBaseAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_AI_KB) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/knowledgeBase", viewPath = "/_view/admin/ai/aiknowledgebase")

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| count |  |  |
| datas |  |  |
| add |  |  |
| save | @Before(Tx.class) | @Para("aiKnowledgeBase" |
| edit |  |  |
| update | @Before(Tx.class) | @Para("aiKnowledgeBase" |
| delete | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |
| autocompleteDatas | @UnCheck |  |

## /admin/ai/llm  (AILlmAdminController)

源码：com/jboltai/platform/ai/admin/rescenter/llm/AILlmAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_AI_LLM) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/llm", viewPath = "/_view/admin/ai/llm")

| 动作 | 注解 | 参数 |
|---|---|---|
| enableList | @CheckPermission({PermissionKey.ADMIN_AI_LLM,PermissionKey.ADMIN_AI_APP}) |  |
| index |  |  |
| datas |  |  |
| testById |  |  |
| formTest |  | @Para("aiLlm" |
| add |  |  |
| save | @Before(Tx.class) | @Para("aiLlm" |
| edit |  |  |
| update | @Before(Tx.class) | @Para("aiLlm" |
| delete | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |
| autocompleteDatas | @UnCheck |  |
| options | @UnCheck |  |

## /admin/ai/page  (AiPageAdminController)

源码：com/jboltai/platform/ai/admin/aipage/AiPageAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_AI_PAGE) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/page", viewPath = "/_view/admin/ai/aipage")

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| add |  |  |
| clearVDB |  |  |
| submitAllEnableToVDB |  |  |
| processAllEmptyRoles | @Before(Tx.class) |  |
| processAllRoles | @Before(Tx.class) |  |
| detail |  |  |
| permissionAdd |  |  |
| permissions |  |  |
| aiApps |  |  |
| permissionsAutocomplete |  |  |
| submitPermissions | @Before(Tx.class) |  |
| submitAiApps | @Before(Tx.class) |  |
| aiAppAdd |  |  |
| datasAdd |  |  |
| save | @Before(Tx.class) | @Para("aiPage" |
| edit |  |  |
| update | @Before(Tx.class) | @Para("aiPage" |
| deleteByIds | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |

## /admin/ai/smartSearch  (JBoltAiSmartSearchAdminController)

源码：com/jboltai/platform/ai/admin/smartsearch/JBoltAiSmartSearchAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@UnCheckIfSystemAdmin @Path(value = "/admin/ai/smartSearch", viewPath = "/_view/admin/ai/smartsearch")

| 动作 | 注解 | 参数 |
|---|---|---|
| index | @UnCheck | @Para("q" |

## /admin/ai/test/render  (TestAiRenderController)

源码：com/jboltai/platform/ai/admin/aifunction/test/TestAiRenderController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@Path("/admin/ai/test/render")

| 动作 | 注解 | 参数 |
|---|---|---|
| formSubmit | @UnCheck |  |

## /admin/ai/tools  (AiToolsAdminController)

源码：com/jboltai/platform/ai/admin/aifunction/AiToolsAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_AI_TOOLS) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/tools", viewPath = "/_view/admin/ai/aifunction")

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| reloadServices |  |  |
| services |  |  |
| submit |  |  |
| addForm |  |  |
| addApiFunctionForm |  |  |
| serviceMethods |  |  |
| add |  |  |
| save | @Before(Tx.class) | @Para("aiFunction" |
| saveApiFunction | @Before(Tx.class) | @Para("aiFunction" |
| updateApiFunction | @Before(Tx.class) | @Para("aiFunction" |
| edit |  |  |
| update | @Before(Tx.class) | @Para("aiFunction" |
| deleteByIds | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |
| autocompleteDatas | @UnCheck |  |
| updateColumn | @Before(Tx.class) | @Para("" |
| detail |  |  |
| json |  |  |
| test |  |  |

## /admin/ai/vdb  (AiVdbAdminController)

源码：com/jboltai/platform/ai/admin/rescenter/vdb/AiVdbAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_AI_VDB) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/vdb", viewPath = "/_view/admin/ai/vdb")

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| collections |  |  |
| listCollections |  |  |
| checkAndInitBuildInCollections |  |  |
| datas |  |  |
| formTest |  | @Para("aiVdb" |
| testById |  |  |
| add |  |  |
| save | @Before(Tx.class) | @Para("aiVdb" |
| edit |  |  |
| update | @Before(Tx.class) | @Para("aiVdb" |
| delete | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |
| toggleIsMain | @Before(Tx.class) |  |
| autocompleteDatas | @UnCheck |  |
| options | @UnCheck |  |

## /admin/customBusiness  (CustomBusinessAdminController)

源码：cn/jbolt/_admin/customtablerender/custombusiness/CustomBusinessAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@CheckPermission(PermissionKey.ADMIN_CUSTOM_BUSINESS) @UnCheckIfSystemAdmin @Path(value = "/admin/customBusiness", viewPath = "/_view/_admin/customtablerender/custombusiness")

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| add |  |  |
| save | @Before(Tx.class) | @Para("customBusiness" |
| edit |  |  |
| update | @Before(Tx.class) | @Para("customBusiness" |
| delete | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |
| autocompleteDatas |  |  |
| options |  |  |

## /admin/jboltfileChange  (JboltFileChangeAdminController)

源码：cn/jbolt/common/controller/JboltFileChangeAdminController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@Path(value = "/admin/jboltfileChange") @UnCheck

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |

## /ai/app/guest  (AiAppGuestController)

源码：com/jboltai/platform/ai/app/AiAppGuestController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@Before(JBoltWebsocketGuestUseHeaderInterceptor.class) @Path(value = "/ai/app/guest", viewPath = "/_view/ai/aiapp")

| 动作 | 注解 | 参数 |
|---|---|---|
| clearSessionMsgs |  |  |
| downloadFile |  |  |
| index |  |  |
| sessionHistory |  |  |
| userHistorySessionDatas |  |  |
| userHistoryMessageDatas |  |  |
| linkFileDocs |  | @Para("aiAppId" |
| runIframePreview |  |  |
| runIframeDownload |  |  |
| runIframeCode |  |  |

## /ai/app/pwd  (AiAppPwdController)

源码：com/jboltai/platform/ai/app/AiAppPwdController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@Before(JBoltWebsocketGuestInterceptor.class) @Path(value = "/ai/app/pwd", viewPath = "/_view/ai/aiapp")

| 动作 | 注解 | 参数 |
|---|---|---|
| clearSessionMsgs |  |  |
| index |  |  |
| login | @JBoltHttpPost | String sn,String pwd |
| sessionHistory |  |  |
| userHistorySessionDatas |  |  |
| userHistoryMessageDatas |  |  |
| linkFileDocs |  | @Para("aiAppId" |
| runIframePreview |  |  |
| runIframeDownload |  |  |
| runIframeCode |  |  |

## /ai/app/system  (AiAppSystemController)

源码：com/jboltai/platform/ai/app/AiAppSystemController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@Path(value = "/ai/app/system", viewPath = "/_view/ai/aiapp")

| 动作 | 注解 | 参数 |
|---|---|---|
| clearSessionMsgs |  |  |
| downloadFile |  |  |
| index |  |  |
| runIframePreview |  |  |
| runIframeDownload |  |  |
| runIframeCode |  |  |

## /ai/app/systemIframe  (AiAppSystemIFrameController)

源码：com/jboltai/platform/ai/app/AiAppSystemIFrameController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：@Path(value = "/ai/app/systemIframe", viewPath = "/_view/ai/aiapp") @Before(JBoltCrossOriginInterceptor.class) @CrossOrigin

| 动作 | 注解 | 参数 |
|---|---|---|
| clearSessionMsgs | @Before(JBoltAiAppSystemIframeInterceptor.class) |  |
| userHistoryMessageDatas | @Before(JBoltAiAppSystemIframeInterceptor.class) |  |
| sessionHistory |  |  |
| userHistorySessionDatas | @Before(JBoltAiAppSystemIframeInterceptor.class) |  |
| login |  | String username, String password |
| index |  |  |
| runIframePreview |  |  |
| runIframeDownload |  |  |
| runIframeCode |  |  |

## /api/jwt/refresh  (JBoltRefreshJwtApiController)

源码：cn/jbolt/api/common/controller/JBoltRefreshJwtApiController.java；父类：JBoltApiBaseController；路由拦截器：-；类级注解：@CrossOrigin @Path("/api/jwt/refresh")

| 动作 | 注解 | 参数 |
|---|---|---|
| index | @JBoltApplyJWT @JBoltHttpGet |  |

## /api/user  (JBoltUserInfoApiController)

源码：cn/jbolt/api/common/controller/JBoltUserInfoApiController.java；父类：JBoltApiBaseController；路由拦截器：-；类级注解：@CrossOrigin @Path("/api/user")

| 动作 | 注解 | 参数 |
|---|---|---|
| mineInfo | @JBoltHttpGet |  |
| updateMineInfo | @JBoltHttpPost | JBoltPara para |

## /api/user/auth  (JBoltUserAuthApiController)

源码：cn/jbolt/api/common/controller/JBoltUserAuthApiController.java；父类：JBoltApiBaseController；路由拦截器：-；类级注解：@CrossOrigin @Path("/api/user/auth")

| 动作 | 注解 | 参数 |
|---|---|---|
| index | @JBoltApplyJWT @JBoltHttpMethod({HttpMethod.GET,HttpMethod.POST}) | @Para("userName" |
| permissionKeys | @JBoltHttpGet |  |

## /api/ws/guest/token  (JBoltWebsocketGuestTokenApiController)

源码：cn/jbolt/api/common/controller/JBoltWebsocketGuestTokenApiController.java；父类：JBoltApiBaseController；路由拦截器：-；类级注解：@CrossOrigin @Path("/api/ws/guest/token")

| 动作 | 注解 | 参数 |
|---|---|---|
| gen | @Before(JBoltWebsocketGuestInterceptor.class) @OpenAPI |  |
| check | @OpenAPI |  |

## /api/ws/guest/token/v2  (JBoltWebsocketGuestTokenApiV2Controller)

源码：cn/jbolt/api/common/controller/JBoltWebsocketGuestTokenApiV2Controller.java；父类：Controller；路由拦截器：-；类级注解：@Before(JBoltCrossOriginInterceptor.class) @CrossOrigin @Path("/api/ws/guest/token/v2")

| 动作 | 注解 | 参数 |
|---|---|---|
| gen | @Before(JBoltWebsocketGuestUseHeaderInterceptor.class) |  |
| check |  |  |

## /admin  (AdminIndexController)

源码：cn/jbolt/index/AdminIndexController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：-

| 动作 | 注解 | 参数 |
|---|---|---|
| index | @UnCheck @Before(JBoltNoUrlPara.class) |  |
| menu | @UnCheck |  |
| lockSystem | @UnCheck |  |
| unLockSystem | @UnCheck |  |
| dashboard | @CheckPermission("dashboard") @UnCheckIfSystemAdmin |  |
| relogin | @Clear |  |
| logout | @Clear |  |
| terminalOffline | @Clear |  |
| forcedOffline | @Clear |  |
| login | @Clear |  |
| redirectToScreenLock |  |  |
| captcha | @Clear |  |
| myToken | @UnCheck |  |

## /admin/dept  (DeptAdminController)

源码：cn/jbolt/_admin/dept/DeptAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.DEPT) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| crudJsTreeDatas |  |  |
| options | @UnCheck |  |
| enableOptions | @UnCheck |  |
| enableJsTreeDatas | @UnCheck |  |
| add |  |  |
| addInDialog |  |  |
| edit |  |  |
| save | @Before(Tx.class) |  |
| update | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |
| up | @Before(Tx.class) |  |
| down | @Before(Tx.class) |  |
| initRank | @Before(Tx.class) |  |
| processAllDeptPath | @Before(Tx.class) |  |
| move | @Before(Tx.class) |  |

## /admin/dictionary  (DictionaryAdminController)

源码：cn/jbolt/_admin/dictionary/DictionaryAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission({PermissionKey.DICTIONARY}) @UnCheckIfSystemAdmin @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| datas | @Before(JBoltNoUrlPara.class) |  |
| options | @UnCheck @Before(JBoltNoUrlPara.class) |  |
| poptions | @UnCheck @Before(JBoltNoUrlPara.class) |  |
| soptions | @UnCheck @Before(JBoltNoUrlPara.class) |  |
| soptionsByPsn | @UnCheck @Before(JBoltNoUrlPara.class) |  |
| checkandinit |  |  |
| mgr |  |  |
| index |  |  |
| show |  |  |
| select |  |  |
| add |  |  |
| addItem |  |  |
| edit |  |  |
| editItem |  |  |
| save |  |  |
| update | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| up |  |  |
| down |  |  |
| initRank |  |  |
| clearByType |  |  |
| toggleEnable | @Before(Tx.class) |  |

## /admin/dictionarytype  (DictionaryTypeAdminController)

源码：cn/jbolt/_admin/dictionary/DictionaryTypeAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission({PermissionKey.DICTIONARY}) @UnCheckIfSystemAdmin @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| mgr |  |  |
| datas |  |  |
| codeGenOptions |  |  |
| add |  |  |
| edit |  |  |
| save |  |  |
| update | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |

## /admin/globalconfig  (GlobalConfigAdminController)

源码：cn/jbolt/_admin/globalconfig/GlobalConfigAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.GLOBALCONFIG) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  | String keywords,Boolean builtIn,Long type |
| edit |  |  |
| update |  |  |
| changeAssetsVersion |  |  |
| editCustomConfig |  |  |
| updateCustomConfig |  |  |
| saveCustomConfig |  |  |
| toggleBooleanValue |  |  |
| deleteCustomConfig |  |  |
| clearCacheByType |  |  |
| clearCache |  |  |
| clearReset |  |  |
| addCustomConfig |  |  |

## /admin/globalconfig/type  (GlobalConfigTypeAdminController)

源码：cn/jbolt/_admin/globalconfig/GlobalConfigTypeAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.GLOBALCONFIG) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| options |  |  |
| datas |  |  |
| add |  |  |
| edit |  |  |
| save | @Before(Tx.class) |  |
| update | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| up | @Before(Tx.class) |  |
| down | @Before(Tx.class) |  |
| initRank | @Before(Tx.class) |  |

## /admin/hiprint  (HiprintAdminController)

源码：cn/jbolt/_admin/hiprint/HiprintAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.HIPRINT_DESIGN) @UnCheckIfSystemAdmin @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| designer | @ActionKey("tpl/designer") |  |
| tpl |  |  |
| tplContent | @UnCheck @ActionKey("tpl/content") |  |
| tplDatas | @ActionKey("tpl/datas") |  |
| tplJsonDataEditor | @ActionKey("tpl/jsonDataEditor") |  |
| tplLoad | @ActionKey("tpl/load") |  |
| tplTestJsonData | @ActionKey("tpl/testJsonData") |  |
| tplAdd | @ActionKey("tpl/add") |  |
| tplEdit | @ActionKey("tpl/edit") |  |
| submitTpl | @ActionKey("tpl/submit") |  |
| tplDelete | @ActionKey("tpl/delete") |  |

## /admin/jboltstyle  (JBoltStyleAdminController)

源码：cn/jbolt/common/style/JBoltStyleAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：-

| 动作 | 注解 | 参数 |
|---|---|---|
| change | @UnCheck |  |

## /admin/loginlog  (LoginLogAdminController)

源码：cn/jbolt/_admin/loginlog/LoginLogAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.JBOLT_LOGIN_LOG) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| detail |  |  |

## /admin/msgcenter  (JBoltMsgCenterAdminController)

源码：cn/jbolt/_admin/msgcenter/JBoltMsgCenterAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@UnCheck

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| layer |  |  |
| sysNoticePortalDatas | @ActionKey("/admin/msgcenter/sysnotice/portalDatas") |  |
| sysNoticeDetail | @ActionKey("/admin/msgcenter/sysnotice/detail") |  |
| todoMarkAsRead | @ActionKey("/admin/msgcenter/todo/markAsRead") |  |
| sysNoticeMarkAsRead | @ActionKey("/admin/msgcenter/sysnotice/markAsRead") |  |
| sysNoticeMarkAllAsRead |  |  |
| sysNoticeMarkMultiAsRead | @ActionKey("/admin/msgcenter/sysnotice/markMultiAsRead") @Before(Tx.class) |  |
| userSysNoticeDatas | @ActionKey("/admin/msgcenter/sysnotice/userDatas") |  |
| userTodoDatas | @ActionKey("/admin/msgcenter/todo/userDatas") |  |
| privateMessagePortalDatas |  |  |
| todoPortalDatas | @ActionKey("/admin/msgcenter/todo/portalDatas") |  |
| userUnreadInfo | @ActionKey("/admin/msgcenter/unreadInfo") |  |
| todoDetail | @ActionKey("/admin/msgcenter/todo/detail") |  |

## /admin/neditor/preview  (NeditorPreviewAdminController)

源码：cn/jbolt/common/controller/NeditorPreviewAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@UnCheck

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |

## /admin/neditor/upload  (NeditorUploadAdminController)

源码：cn/jbolt/common/controller/NeditorUploadAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：-

| 动作 | 注解 | 参数 |
|---|---|---|
| index | @UnCheck @Before(NoUrlPara.class) |  |
| wordimg | @UnCheck |  |
| image | @UnCheck |  |
| catchImage | @UnCheck |  |
| video | @UnCheck |  |

## /admin/onlineuser  (OnlineUserAdminController)

源码：cn/jbolt/_admin/onlineuser/OnlineUserAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.ONLINE_USER) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| stateOptions |  |  |
| forcedOffline |  |  |

## /admin/permission  (PermissionAdminController)

源码：cn/jbolt/_admin/permission/PermissionAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.PERMISSION) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| options |  |  |
| add |  |  |
| insert |  |  |
| edit |  |  |
| save |  |  |
| update |  |  |
| delete |  |  |
| up |  |  |
| down |  |  |
| initRank |  |  |
| toggleSystemAdminDefault |  |  |
| modules | @UnCheck |  |

## /admin/pjaxerror  (PjaxErrorController)

源码：cn/jbolt/index/PjaxErrorController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：-

| 动作 | 注解 | 参数 |
|---|---|---|
| error |  |  |

## /admin/post  (PostAdminController)

源码：cn/jbolt/_admin/post/PostAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.POST) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| enableOptions | @UnCheck |  |
| options | @UnCheck |  |
| add |  |  |
| edit |  |  |
| save | @Before(Tx.class) |  |
| update | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| toggleEnable |  |  |
| up | @Before(Tx.class) |  |
| down | @Before(Tx.class) |  |
| initRank | @Before(Tx.class) |  |

## /admin/qiniu  (QiniuAdminController)

源码：cn/jbolt/_admin/qiniu/QiniuAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.QINIU) @UnCheckIfSystemAdmin @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| uploadParas | @UnCheck | @Para("sn" |
| index |  |  |
| options | @CheckPermission({PermissionKey.QINIU,PermissionKey.QINIU_BUCKET}) |  |
| datas |  |  |
| add |  |  |
| checkDefaultExist |  |  |
| edit |  |  |
| save | @Before(Tx.class) |  |
| update | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |
| toggleIsDefault | @Before(Tx.class) |  |

## /admin/qiniu/bucket  (QiniuBucketAdminController)

源码：cn/jbolt/_admin/qiniu/QiniuBucketAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.QINIU_BUCKET) @UnCheckIfSystemAdmin @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| syncAllQiniuBuckets |  |  |
| syncFromQiniu | @Before(Tx.class) |  |
| datas |  |  |
| options |  |  |
| add |  |  |
| edit |  |  |
| save | @Before(Tx.class) |  |
| update | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| toggleIsDefault | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |

## /admin/redis  (RedisAdminController)

源码：cn/jbolt/_admin/redis/RedisAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.JBOLT_SERVER_MONITOR) @UnCheckIfSystemAdmin @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| removeCurrentDatabaseAllDatas |  | @Para("cacheName" |

## /admin/role  (RoleAdminController)

源码：cn/jbolt/_admin/role/RoleAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.ROLE) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| options | @UnCheck |  |
| users |  |  |
| userDatas |  |  |
| add |  |  |
| addItem |  |  |
| edit |  |  |
| save |  |  |
| update |  |  |
| delete | @Before(Tx.class) |  |
| clearUsers | @Before(Tx.class) |  |

## /admin/rolepermission  (RolePermissionAdminController)

源码：cn/jbolt/_admin/rolepermission/RolePermissionAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.ROLE) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| setting |  |  |
| settingTree |  |  |
| submit |  |  |
| getCheckeds |  |  |
| clear |  |  |

## /admin/sensitiveword  (SensitiveWordAdminController)

源码：cn/jbolt/_admin/sensitiveword/SensitiveWordAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.SENSITIVE_WORD) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| counts |  |  |
| add |  |  |
| edit |  |  |
| reload |  |  |
| test |  |  |
| mgr |  |  |
| datas |  |  |
| save |  |  |
| update | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| deleteByIds | @Before(Tx.class) |  |

## /admin/servermonitor  (ServerMonitorAdminController)

源码：cn/jbolt/_admin/monitor/ServerMonitorAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.JBOLT_SERVER_MONITOR) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| disk |  |  |
| memory |  |  |
| server |  |  |
| jvm |  |  |
| cpu |  |  |
| cache |  |  |

## /admin/summernote/upload  (SummernoteUploadAdminController)

源码：cn/jbolt/common/controller/SummernoteUploadAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：-

| 动作 | 注解 | 参数 |
|---|---|---|
| index | @UnCheck @Before(NoUrlPara.class) |  |
| image | @UnCheck |  |

## /admin/sysnotice  (SysNoticeAdminController)

源码：cn/jbolt/_admin/msgcenter/SysNoticeAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.SYS_NOTICE)

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| add |  |  |
| edit |  |  |
| files |  |  |
| updateFiles |  |  |
| uploadFiles |  |  |
| pdfViewer |  |  |
| download |  |  |
| detail |  |  |
| save | @Before(Tx.class) |  |
| update | @Before(Tx.class) |  |
| batchDelete | @Before(Tx.class) |  |
| batchRestore | @Before(Tx.class) |  |
| chooseReceiver |  |  |

## /admin/systemlog  (SystemLogAdminController)

源码：cn/jbolt/_admin/systemlog/SystemLogAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.SYSTEMLOG) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |

## /admin/todo  (TodoAdminController)

源码：cn/jbolt/_admin/msgcenter/TodoAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@UnCheck

| 动作 | 注解 | 参数 |
|---|---|---|
| add |  |  |
| edit |  |  |
| save | @Before(Tx.class) |  |
| update | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| deleteByIds | @Before(Tx.class) |  |
| updateState | @Before(Tx.class) |  |
| batchUpdateState | @Before(Tx.class) |  |
| dashboardDatas |  |  |
| detail |  |  |

## /admin/topnav  (TopnavAdminController)

源码：cn/jbolt/_admin/topnav/TopnavAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.TOPNAV) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| options | @UnCheck |  |
| add |  |  |
| edit |  |  |
| save | @Before(Tx.class) |  |
| update | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| up | @Before(Tx.class) |  |
| down | @Before(Tx.class) |  |
| initRank | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |
| clearMenus |  |  |
| clearAllMenus |  |  |

## /admin/topnav/menu  (TopnavMenuAdminController)

源码：cn/jbolt/_admin/topnav/TopnavMenuAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.TOPNAV) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| setting |  |  |
| getChecked |  |  |
| clear |  |  |
| clearAll |  |  |
| submit | @Before(Tx.class) |  |

## /admin/user  (UserAdminController)

源码：cn/jbolt/_admin/user/UserAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.USER) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| options | @UnCheck |  |
| index |  |  |
| datas |  |  |
| sysnoticeUsers | @CheckPermission(PermissionKey.SYS_NOTICE) |  |
| autocomplete | @UnCheck |  |
| uploadAvatar |  |  |
| uploadMyAvatar | @UnCheck |  |
| sexOptions | @Before(JBoltCacheInterceptor.class) @UnCheck |  |
| pwd | @UnCheck |  |
| avatar | @UnCheck |  |
| editpwd | @CheckPermission(PermissionKey.USER) |  |
| submitpwd | @CheckPermission(PermissionKey.USER) |  |
| updatepwd | @UnCheck |  |
| add |  |  |
| extendForm |  |  |
| extendDetail |  |  |
| edit |  |  |
| save | @Before(Tx.class) |  |
| update | @Before(Tx.class) |  |
| delete | @Before(Tx.class) |  |
| toggleEnable | @Before(Tx.class) |  |
| deleteRole | @Before(Tx.class) |  |
| processAllDeptPath | @Before(Tx.class) |  |
| modules | @UnCheck |  |

## /admin/userconfig  (UserConfigAdminController)

源码：cn/jbolt/_admin/userconfig/UserConfigAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：-

| 动作 | 注解 | 参数 |
|---|---|---|
| index | @UnCheck |  |
| toggleBooleanConfig | @UnCheck |  |
| changeStringValue | @UnCheck |  |

## /api/test  (ApiTestController)

源码：cn/jbolt/apitest/ApiTestController.java；父类：JBoltApiBaseController；路由拦截器：-；类级注解：@CrossOrigin

| 动作 | 注解 | 参数 |
|---|---|---|
| paycallback | @Clear |  |
| unifiedorder |  |  |
| go |  |  |
| floor |  |  |
| uploadImgUseOpenApi | @OpenAPI |  |
| uploadImage |  |  |
| login | @JBoltApplyJWT |  |
| refreshJwt | @JBoltApplyJWT @JBoltHttpGet |  |
| applyJwt | @JBoltApplyJWT |  |

## /admin/app  (ApplicationAdminController)

源码：cn/jbolt/admin/appdevcenter/ApplicationAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.APPLICATION) @UnCheckIfSystemAdmin @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| appinfo | @Before(ApplicationValidator.class) |  |
| enableOptions |  |  |
| types |  |  |
| typesInForm |  |  |
| add |  |  |
| edit | @Before(ApplicationValidator.class) |  |
| save |  |  |
| update |  |  |
| delete | @Before({Tx.class,ApplicationValidator.class}) |  |
| toggleEnable | @Before(ApplicationValidator.class) |  |
| toggleNeedCheckSign | @Before(ApplicationValidator.class) |  |
| changeAppSecret | @Before(ApplicationValidator.class) |  |
| linkTarget | @Before(ApplicationValidator.class) |  |
| removeLinkTarget | @Before(ApplicationValidator.class) |  |

## /admin/devdoc/database  (JBoltDatabaseDevDocController)

源码：cn/jbolt/admin/devdoc/database/JBoltDatabaseDevDocController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.APPLICATION) @UnCheckIfSystemAdmin @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datasources |  |  |
| tableLayer |  |  |
| tables |  |  |
| refreshTables |  |  |

## /admin/codegen  (CodeGenAdminController)

源码：cn/jbolt/_admin/codegen/CodeGenAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.JBOLT_CODE_GEN) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| autocompletedemodatas | @UnCheck |  |
| types |  |  |
| refreshEnumsCache |  |  |
| enums |  |  |
| refreshCachesCache |  |  |
| refreshServicesCache |  |  |
| caches |  |  |
| datasources |  |  |
| styles |  |  |
| states |  |  |
| add |  |  |
| addSubTable |  |  |
| edit |  |  |
| save |  |  |
| update |  |  |
| updateBase |  |  |
| delete | @Before(Tx.class) |  |
| deleteByIds | @Before(Tx.class) |  |
| recoverByIds | @Before(Tx.class) |  |
| realDelete | @Before(Tx.class) |  |
| tablesAutocomplete |  |  |
| reloadTables |  |  |
| uploadImg |  |  |
| createCodeGenDialog |  | @Para("datasource" |
| getLastCodegenId |  | @Para("datasource" |
| editCodeGenConfig |  |  |
| keyCacheColumns |  |  |
| autocompleteColumns |  |  |
| optionsTextColumns |  |  |
| optionsValueColumns |  |  |
| keyCacheBindColumns |  |  |
| intSortColumns |  |  |
| permissionPortal |  |  |
| unbindPermission |  |  |
| keywordsMatchColumns |  |  |
| formPortal |  |  |
| testAjax |  |  |
| formPortalEditMode |  |  |
| formPortalTest |  |  |
| tablePortalEditMode |  |  |
| tablePortal |  |  |
| tablePortalDatas |  |  |
| initSortRankInform | @Before(Tx.class) |  |
| initSortRankIntable | @Before(Tx.class) |  |
| formDictionaryOptions |  |  |
| searchDictionaryOptions |  |  |
| routesOptions |  |  |
| refreshRoutesCache |  |  |
| genAll | @Before(Tx.class) |  |
| genModel | @Before(Tx.class) |  |
| genMainLogic | @Before(Tx.class) |  |
| downloadTpl |  |  |
| initImportExcel |  |  |
| importExcel |  |  |
| exportExcel |  |  |
| staticMethodClasses |  |  |
| javaPackageAutocomplete |  |  |
| services |  |  |
| initBindPermission |  |  |
| bindPermission |  |  |
| syncProjectPath |  |  |
| updateProjectPath |  |  |

## /admin/codegen/modelattr  (CodeGenModelAttrAdminController)

源码：cn/jbolt/_admin/codegen/modelattr/CodeGenModelAttrAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.JBOLT_CODE_GEN) @UnCheckIfSystemAdmin

| 动作 | 注解 | 参数 |
|---|---|---|
| datas |  |  |
| toggleIsPkey | @Before(Tx.class) |  |
| toggleIsNeedCheckExists | @Before(Tx.class) |  |
| toggleIsTableCol | @Before(Tx.class) |  |
| toggleIsImportCol | @Before(Tx.class) |  |
| toggleIsExportCol | @Before(Tx.class) |  |
| toggleIsNeedTranslate | @Before(Tx.class) |  |
| toggleIsSortable | @Before(Tx.class) |  |
| toggleIsSearchEle | @Before(Tx.class) |  |
| toggleIsFuzzyQuery | @Before(Tx.class) |  |
| toggleIsFormEle | @Before(Tx.class) |  |
| toggleIsRequired | @Before(Tx.class) |  |
| toggleIsSearchRequired | @Before(Tx.class) |  |
| toggleIsKeywordsColumn | @Before(Tx.class) |  |
| toggleIsSingleLine | @Before(Tx.class) |  |
| toggleIsTableSwitchbtn | @Before(Tx.class) |  |
| updateColumn | @Before(TxReadCommitted.class) | @Para("" |
| moveFormEle | @Before(Tx.class) | Long codeGenId,Long id,Long prevId,Long nextId |
| moveTableEle | @Before(Tx.class) | Long codeGenId,Long id,Long prevId,Long nextId |
| sync | @Before(Tx.class) |  |
| updateFormEleConfig | @Before(Tx.class) | JBoltPara para |
| colEditorData |  |  |
| submitColConfig |  |  |
| tableColUp |  |  |
| tableColDown |  |  |

## /  (IndexController)

源码：cn/jbolt/index/IndexController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：-

| 动作 | 注解 | 参数 |
|---|---|---|
| index | @Before(JBoltNoUrlPara.class) |  |

## /admin/wechat/autoreply  (WechatAutoReplyAdminController)

源码：cn/jbolt/admin/wechat/autoreply/WechatAutoReplyAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@UnCheckIfSystemAdmin @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| subscribeReplyMgr | @CheckPermission(PermissionKey.WECHAT_AUTOREPLY_SUBSCRIBE) @Before(WechatAutoReplyMgrValidator.class) |  |
| keywordsReplyMgr | @CheckPermission(PermissionKey.WECHAT_AUTOREPLY_KEYWORDS) @Before(WechatAutoReplyMgrValidator.class) |  |
| defaultReplyMgr | @CheckPermission(PermissionKey.WECHAT_AUTOREPLY_SUBSCRIBE) @Before(WechatAutoReplyMgrValidator.class) |  |
| add | @UnCheck @Before(WechatAutoReplyMpIdAndTypeValidator.class) |  |
| edit | @UnCheck @Before(WechatAutoReplyMpIdAndTypeValidator.class) |  |
| delete | @UnCheck @Before({WechatAutoReplyMpIdAndTypeValidator.class,Tx.class}) |  |
| toggleEnable | @UnCheck @Before(WechatAutoReplyMpIdAndTypeValidator.class) |  |
| replyTypes | @UnCheck |  |
| save | @UnCheck @Before(WechatAutoReplyMpIdAndTypeValidator.class) |  |
| update | @UnCheck @Before(WechatAutoReplyMpIdAndTypeValidator.class) |  |

## /admin/wechat/config  (WechatConfigAdminController)

源码：cn/jbolt/admin/wechat/config/WechatConfigAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| baseMgr | @CheckPermission(PermissionKey.WECHAT_CONFIG_BASEMGR) |  |
| extraMgr | @CheckPermission(PermissionKey.WECHAT_CONFIG_EXTRAMGR) |  |
| uploadWechatMusicImgMedia | @CheckPermission(PermissionKey.WECHAT_CONFIG_BASEMGR) |  |
| submitBaseConfig | @CheckPermission(PermissionKey.WECHAT_CONFIG_BASEMGR) @Before(Tx.class) |  |
| submitExtraConfig | @CheckPermission(PermissionKey.WECHAT_CONFIG_EXTRAMGR) @Before(Tx.class) |  |
| payMgr | @CheckPermission(PermissionKey.WECHAT_CONFIG_PAYMGR) |  |
| submitPayConfig | @CheckPermission(PermissionKey.WECHAT_CONFIG_PAYMGR) @Before(Tx.class) |  |

## /admin/wechat/keywords  (WechatKeywordsAdminController)

源码：cn/jbolt/admin/wechat/autoreply/WechatKeywordsAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.WECHAT_AUTOREPLY_KEYWORDS) @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| index | @Before(WechatKeywordsMgrValidator.class) |  |
| add | @Before(WechatKeywordsMgrValidator.class) |  |
| edit | @Before(WechatKeywordsMgrValidator.class) |  |
| types |  |  |
| save | @Before(WechatKeywordsMgrValidator.class) |  |
| update | @Before(WechatKeywordsMgrValidator.class) |  |
| delete | @Before(WechatKeywordsMgrValidator.class) |  |

## /admin/wechat/media  (WechatMediaAdminController)

源码：cn/jbolt/admin/wechat/media/WechatMediaAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.WECHAT_MEDIA) @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| index | @Before(WechatMediaMgrValidator.class) |  |
| download | @Before(WechatMediaMgrValidator.class) |  |
| choose | @CheckPermission({PermissionKey.WECHAT_AUTOREPLY_DEFAULT,PermissionKey.WECHAT_AUTOREPLY_KEYWORDS,PermissionKey.WECHAT_AUTOREPLY_SUBSCRIBE}) |  |
| chooseIt | @CheckPermission({PermissionKey.WECHAT_AUTOREPLY_DEFAULT,PermissionKey.WECHAT_AUTOREPLY_KEYWORDS,PermissionKey.WECHAT_AUTOREPLY_SUBSCRIBE}) @Before(WechatMediaMgrValidator.class) |  |
| types | @UnCheck |  |
| syncAll | @Before(WechatMediaMgrValidator.class) |  |
| syncNewDatas | @Before(WechatMediaMgrValidator.class) |  |

## /admin/wechat/menu  (WechatMenuAdminController)

源码：cn/jbolt/admin/wechat/menu/WechatMenuAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.WECHAT_MENU) @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| mgr |  |  |
| level1List |  |  |
| level2List |  |  |
| edit |  |  |
| save |  |  |
| update |  |  |
| delete |  |  |
| publish |  |  |
| types |  |  |

## /admin/wechat/mpinfo  (WechatMpinfoAdminController)

源码：cn/jbolt/admin/wechat/mpinfo/WechatMpinfoAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.WECHAT_MPINFO) @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
| datas |  |  |
| uploadImage |  |  |
| add |  |  |
| save |  |  |
| edit |  |  |
| editLinkApp |  |  |
| apps |  |  |
| update |  |  |
| delete |  |  |
| clearAllConfigs |  |  |
| toggleEnable |  |  |
| toggleAuthenticated |  |  |
| enableOptions |  |  |
| isAuthenticatedOptions |  |  |
| typeOptions |  |  |
| subjectTypeOptions |  |  |
| submitLinkApp | @Before(Tx.class) |  |
| removeLinkApp | @Before(Tx.class) |  |

## /admin/wechat/replycontent  (WechatReplyContentAdminController)

源码：cn/jbolt/admin/wechat/autoreply/WechatReplyContentAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：-

| 动作 | 注解 | 参数 |
|---|---|---|
| index | @UnCheck @Before(WechatReplyContentMgrValidator.class) |  |
| add | @UnCheck @Before(WechatReplyContentMgrValidator.class) |  |
| edit | @UnCheck @Before(WechatReplyContentMgrValidator.class) |  |
| up | @UnCheck @Before(WechatReplyContentMgrValidator.class) |  |
| down | @UnCheck @Before(WechatReplyContentMgrValidator.class) |  |
| initRank | @UnCheck @Before(WechatReplyContentMgrValidator.class) |  |
| delete | @UnCheck @Before(WechatReplyContentMgrValidator.class) |  |
| toggleEnable | @UnCheck @Before(WechatReplyContentMgrValidator.class) |  |
| types | @UnCheck |  |
| uploadImage | @UnCheck @Before(WechatReplyContentMgrValidator.class) |  |
| save | @UnCheck @Before(WechatReplyContentMgrValidator.class) |  |
| update | @UnCheck @Before(WechatReplyContentMgrValidator.class) |  |

## /admin/wechat/user  (WechatUserAdminController)

源码：cn/jbolt/admin/wechat/user/WechatUserAdminController.java；父类：JBoltBaseController；路由拦截器：JBoltAdminAuthInterceptor；类级注解：@CheckPermission(PermissionKey.WECHAT_USER) @OnlySaasPlatform

| 动作 | 注解 | 参数 |
|---|---|---|
| index | @Before(WechatUserMgrValidator.class) |  |
| sexOptions | @UnCheck |  |
| sync | @Before(WechatUserMgrValidator.class) |  |
| toggleEnable | @Before(WechatUserMgrValidator.class) |  |
| syncUser | @Before(WechatUserMgrValidator.class) |  |

## /api/wechat  (JBoltWechatApiController)

源码：cn/jbolt/wechat/api/JBoltWechatApiController.java；父类：JBoltApiBaseController；路由拦截器：-；类级注解：-

| 动作 | 注解 | 参数 |
|---|---|---|
| authorize | @UnCheckJBoltApi | @Para(value ="scope", defaultValue = "snsapi_userinfo" |
| authCallback | @UnCheckJBoltApi | @Para("code" |

## /api/wxa  (JBoltWxaApiController)

源码：cn/jbolt/wxa/api/JBoltWxaApiController.java；父类：JBoltApiBaseController；路由拦截器：-；类级注解：@CrossOrigin

| 动作 | 注解 | 参数 |
|---|---|---|
| login | @JBoltApplyJWT @JBoltHttpPost | String code |
| refreshJwt | @JBoltApplyJWT @JBoltHttpGet |  |
| decryptUserInfo |  | JBoltPara param |
| decryptPhoneNumber |  | JBoltPara param |
| bindOtherUser | @JBoltHttpPost @JBoltReApplyJWT | JBoltPara param |
| loginSystemUser | @JBoltHttpPost @JBoltReApplyJWT | JBoltPara param |
| unbindOtherUser | @JBoltHttpPost @JBoltReApplyJWT | JBoltPara param |
| myWechatUserInfo | @ActionKey("/api/wxa/user/me") |  |
| updateMyWechatUserInfo | @JBoltHttpPost | JBoltPara para |

## /wx/msg  (WechatMsgController)

源码：cn/jbolt/admin/wechat/autoreply/WechatMsgController.java；父类：MsgControllerAdapter；路由拦截器：-；类级注解：-


## /wxa/msg  (WechatWxaMsgController)

源码：cn/jbolt/admin/wechat/autoreply/WechatWxaMsgController.java；父类：WxaMsgController；路由拦截器：-；类级注解：-


## /test/wechat  (JBoltWechatTestApiController)

源码：cn/jbolt/wechat/api/test/JBoltWechatTestApiController.java；父类：JBoltBaseController；路由拦截器：-；类级注解：-

| 动作 | 注解 | 参数 |
|---|---|---|
| index |  |  |
