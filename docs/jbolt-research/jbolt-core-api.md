# jbolt_core.jar 类与方法清单（自动生成）

> 由 tools/jbolt-research/dump_core_jar.py 直接解析 class 文件生成。共 411 个 class（顶层 375 个），3 个资源文件。

## 资源文件

- META-INF/MANIFEST.MF (82 B)
- META-INF/maven/cn.jbolt/jbolt_core/pom.xml (12320 B)
- META-INF/maven/cn.jbolt/jbolt_core/pom.properties (53 B)

## cn.jbolt.core.Interceptor

### JBoltExceptionGlobalInterceptor (class) extends Object implements Interceptor

方法：<init>()；intercept(Invocation)

### JBoltOnlineUserGlobalInterceptor (class) extends Object implements Interceptor

方法：<init>()；intercept(Invocation)

### JBoltSecurityCheck (class) extends Object

方法：<init>()；checkHasPermission(ZSet[String)；checkHasPermission(ZString[String)；checkIsSystemAdminDefaultPermission(Z[String)；isMustCheckSaasTenantPermission(Method)；isNoAnnotation(Method)；isOnlySaasPlatform(Controller)；isOnlySaasPlatform(Method)；isPermissionCheck(Controller)；isPermissionCheck(Method)；isUncheck(Controller)；isUncheck(Method)；isUncheckIfSystemAdmin(ControllerMethod)

### JBoltWebsocketGuestInterceptor (class) extends Object implements Interceptor

方法：<init>()；intercept(Invocation)

## cn.jbolt.core.Interceptor.reqlimit

### JBoltRateLimitType (enum) extends Enum

方法：valueOf(String)；values()

常量：DEFAULT、IP

### JBoltRateLimiter (interface) extends Object implements Annotation

方法：count()；limitType()；msg()；prefix()；time()；value()

### JBoltRateResult (class) extends Object

方法：getMsg()；isNotPass()；isPass()；notPass()；notPass(String)；pass()；setMsg(String)；setPass(Z)

常量：DEFAULT_MSG、PASS、NOT_PASS、CHECK_EXCEPTION、NO_IP

## cn.jbolt.core.Interceptor.reqlimit.redis

### JBoltRedisRateLimitUtil (class) extends Object

方法：<init>()；initRedisLuaScriptSha()；limit(StringIIString)

### JBoltRedisRequestRateLimitInterceptor (class) extends Object implements Interceptor

方法：<init>()；<init>(Z)；intercept(Invocation)

## cn.jbolt.core.actionreport

### JBoltActionReport (class) extends ActionReporter

方法：<init>()；report(StringControllerAction)

### JBoltActionReportLogWriter (class) extends Writer

方法：<init>()；close()；flush()；write(String)；write([CII)

### JBoltActionReportSystemOutWriter (class) extends Writer

方法：<init>()；close()；flush()；write(String)；write([CII)

## cn.jbolt.core.annotation

### JBoltAutoCache (interface) extends Object implements Annotation

方法：bindColumn()；column()；idCache()；keyCache()；name()；prefix()

### TableBind (interface) extends Object implements Annotation

方法：dataSource()；idGenMode()；idSequence()；primaryKey()；separate()；table()

### UnProcessBoolean (interface) extends Object implements Annotation

方法：value()

## cn.jbolt.core.api

### HttpMethod (enum) extends Enum

方法：valueOf(String)；values()

常量：GET、HEAD、POST、PUT、PATCH、DELETE、OPTIONS、TRACE、CONNECT

### JBoltApiBaseController (class) extends JBoltCommonController

方法：<init>()；renderH5PageFail(String)；renderH5PageFailRet(Ret)；renderH5PageSuccess(String)；renderH5PageSuccessRet(Ret)

### JBoltApiBindUserBean (class) extends Object

方法：<init>(IObject)；getType()；getUserId()；setType(I)；setUserId(Object)

常量：TYPE_SYSTEM_USER

### JBoltApiInterceptor (class) extends Object implements Interceptor

方法：<init>()；intercept(Invocation)

### JBoltApiJwtManger (class) extends Object

方法：<init>()；createJBoltApiToken(ApplicationJBoltApiUser)；createJBoltApiToken(ApplicationJBoltApiUserLong)；createJBoltApiToken(ApplicationJBoltApiUserLongLong)；createJBoltApiToken(JBoltJwtParseRet)；createJBoltApiTokenToResponse(ControllerApplicationJBoltApplyJWT)；createJBoltApiTokenToResponse(ControllerApplicationJBoltReApplyJWT)；createJBoltApiTokens(ApplicationJBoltApiUserLongLong)；getJwtParseRet(ControllerZ)；getJwtParseRet(InvocationZ)；me()；parseJWT(ApplicationStringZ)；refreshJwt(Controller)

常量：JWT_TTL、REFRESH_JWT_SURPLUS_TTL、JBOLT_API_TOKEN_KEY、JBOLT_API_REFRESH_TOKEN_KEY、JBOLT_JWT_SIGNATURE、JBOLT_JWT_TIMESTAMP、JBOLT_JWT_NONCE

### JBoltApiKit (class) extends JBoltParaValidator

方法：<init>()；clear()；getApiUser()；getApiUserId()；getApiUserIdAs()；getApiUserIdToInt()；getApiUserIdToLong()；getApiUserIdToStr()；getAppId()；getApplication()；getApplicationId()；getApplicationIdAs()；getApplicationIdToStr()；getApplicationIsInner()；getApplicationIsPCInnerPlatform()；getApplicationLinkTargetId()；getApplyJwtUser()；getBindSystemUserId()；getBindUserId(I)；getBindUserIdToInt(I)；getBindUserIdToLong(I)；getBindUserIdToStr(I)；getBindUsers()；getJwtParseRet()；getWechatAppId()；getWechatMpId()；isSystemUser()；processBindUser(JBoltApiUserBeanString)；processWechatConfig(Application)；processWxaConfig(Application)；removeApplication()；removeApplyJwtUser()；removeJwtParseRet()；removeWechatAppId()；removeWechatMpId()；setApplication(Application)；setApplyJwtUser(JBoltApiUser)；setJwtParseRet(JBoltJwtParseRet)；setWechatAppId(String)；setWechatMpId(Long)

### JBoltApiRet (class) extends Ret

方法：API_FAIL(ApplicationString)；API_FAIL(ApplicationStringObject)；API_FAIL(IApplicationString)；API_FAIL(IApplicationStringObject)；API_FAIL(IStringObject)；API_FAIL(String)；API_FAIL(StringObject)；API_SUCCESS(String)；API_SUCCESS(StringObject)；API_SUCCESS_WITH_DATA(Object)；APPLICATION_NOT_ENABLE(Application)；APPLICATION_NOT_EXIST(String)；APP_NOT_LINK_WECHAT_MP(Application)；APP_NOT_LINK_WECHAT_XCX(Application)；FORCED_OFFLINE(Application)；JWT_PARSE_FAIL(IApplication)；JWT_PARSE_FAIL(IApplicationString)；NOT_ADMIN_LOGIN(Application)；SIGNATURE_CHECK_FAILED(Application)；TERMINAL_OFFLINE(Application)；WECHAT_MP_CONFIG_ERROR(Application)；WECHAT_MP_DISABLED(Application)；WECHAT_XCX_BINDUSER_ERROR(Application)；WECHAT_XCX_BINDUSER_NO_USER(ApplicationString)；WECHAT_XCX_BINDUSER_PARAM_ERROR(Application)；WECHAT_XCX_BINDUSER_SAMETYPE_EXIST(ApplicationString)；WECHAT_XCX_BINDUSER_UPDATE_ERROR(ApplicationString)；WECHAT_XCX_CHECK_USER_INFO_FAIL(Application)；WECHAT_XCX_CONFIG_ERROR(Application)；WECHAT_XCX_DECRYPTUSERINFO_FAIL(Application)；WECHAT_XCX_DISABLED(Application)；WECHAT_XCX_GETPHONENUMBER_FAIL(Application)；WECHAT_XCX_GETUSERINFO_FAIL(Application)；WECHAT_XCX_UPDATE_USERINFO_FAIL(ApplicationString)；WECHAT_XCX_UPDATE_USER_PHONENUMBER_FAIL(ApplicationString)；WECHAT_XCX_USER_CREATE_FAIL(Application)；WX_LOGIN_CODE_NULL(Application)；delete([String)；fail(IString)；fail(IStringApplication)；fail(IStringApplicationI)；fail(IStringObject)；fail(IStringObjectApplication)；fail(IStringObjectApplicationI)；getApp()；getCode()；getData()；getLocation()；getMsg()；set(ObjectObject)；setMsg(String)；success()；success(String)；success(StringObject)；successWithData(Object)；warn(IString)；warn(IStringApplication)；warn(IStringObject)；warn(IStringObjectApplication)

常量：NO_JBOLT_APPID、NO_JBOLT_APPID_PARAM、APPID_CHECK_ERROR、NO_JBOLT_JWT、JWT_IS_EXPIRED、OPTIONS、HTTP_METHOD_ERROR、NO_JBOLT_REFRESH_JWT、REFRESH_JWT_IS_EXPIRED、JWT_API_USER_CHECK_EFFECTIVE_TOKEN_FAILD、API_SUCCESS

### JBoltApiUser (interface) extends Object

方法：getApplicationId()；getBindUsers()；getCheckEffectiveToken()；getIsSystemUser()；getIsTenant()；getSessionKey()；getTenantSn()；getUserId()；getUserIdAs()；getUserIdToInt()；getUserIdToLong()；getUserIdToStr()；getUserName()；setCheckEffectiveToken(String)

### JBoltApiUserBean (class) extends Object implements JBoltApiUser

方法：<init>()；<init>(ObjectObjectString)；<init>(ObjectObjectStringString)；<init>(ObjectObjectStringZ)；<init>(ObjectObjectStringZString)；getApplicationId()；getBindUsers()；getCheckEffectiveToken()；getIsSystemUser()；getIsTenant()；getSessionKey()；getTenantSn()；getUserId()；getUserIdAs()；getUserIdToInt()；getUserIdToLong()；getUserIdToStr()；getUserName()；setApplicationId(Object)；setBindUsers(List)；setCheckEffectiveToken(String)；setIsSystemUser(Z)；setIsTenant(Z)；setSessionKey(String)；setTenantSn(String)；setUserId(Object)；setUserName(String)

### JBoltApplyJWT (interface) extends Object implements Annotation

方法：inHeader()；refreshSurplusTTL()；value()

### JBoltJwtParseRet (class) extends Object

方法：<init>()；getApiRet()；getApiUser()；getPcInnerPlatformUserSessionId()；getUserId()；getUserIdAs()；getUserIdToInt()；getUserIdToLong()；getUserIdToStr()；getUserSessionKey()；isExpired()；isOk()；isSignCheckFailed()；setApiRet(JBoltApiRet)；setApiUser(JBoltApiUser)；setExpired(Z)；setSignCheckFailed(Z)

### JBoltReApplyJWT (interface) extends Object implements Annotation

方法：inHeader()；refreshSurplusTTL()；value()

### OpenAPI (interface) extends Object implements Annotation

方法：parseJwtIfExists()

### UnCheckJBoltApi (interface) extends Object implements Annotation

## cn.jbolt.core.api.httpmethod

### JBoltHttpConnect (interface) extends Object implements Annotation

### JBoltHttpDelete (interface) extends Object implements Annotation

### JBoltHttpGet (interface) extends Object implements Annotation

### JBoltHttpHead (interface) extends Object implements Annotation

### JBoltHttpMethod (interface) extends Object implements Annotation

方法：value()

### JBoltHttpOptions (interface) extends Object implements Annotation

### JBoltHttpPatch (interface) extends Object implements Annotation

### JBoltHttpPost (interface) extends Object implements Annotation

### JBoltHttpPut (interface) extends Object implements Annotation

### JBoltHttpTrace (interface) extends Object implements Annotation

## cn.jbolt.core.base

### JBoltActionMapping (class) extends ActionMapping

方法：<init>(Routes)

### JBoltGlobalConfigKey (class) extends Object

方法：<init>()

常量：WECHAT_MP_SERVER_DOMAIN、WECHAT_WXA_SERVER_DOMAIN、WECHAT_ASSETS_SERVER_DOMAIN、SYSTEM_NAME、SYSTEM_ADMIN_LOGO、SYSTEM_COPYRIGHT_COMPANY、SYSTEM_COPYRIGHT_LINK、JBOLT_ADMIN_STYLE、JBOLT_ADMIN_WITH_TABS、JBOLT_LOGIN_BGIMG_BLUR、JBOLT_LOGIN_USE_CAPTURE、JBOLT_LOGIN_CAPTURE_TYPE、JBOLT_LOGIN_BGIMG、JBOLT_LOGIN_NEST、JBOLT_ACTION_REPORT_WRITER、JBOLT_ACTION_REPORT_LINE_NUMBER_ENABLE、JBOLT_AUTO_CACHE_LOG、ASSETS_VERSION、SYSTEM_ADMIN_H50、SYSTEM_ADMIN_NAV_MENU_DEFAULT_ICON、SYSTEM_ADMIN_GLOBAL_SEARCH_SHOW、SYSTEM_ADMIN_LEFT_NAV_WIDTH、JBOLT_ADMIN_USER_KEEPLOGIN_SECONDS、JBOLT_ADMIN_USER_NOT_KEEPLOGIN_SECONDS、JBOLT_AUTO_LOCKSCREEN_SECONDS、JBOLT_LOGIN_TERMINAL_ONLYONE、JBOLT_ADMIN_LOGIN_HTML_FILE、SYSTEM_DEPT_ENABLE、SYSTEM_POST_ENABLE、SYSTEM_USER_EXTEND_ENABLE、JBOLT_TAB_KEEP_AFTER_RELOAD、JBOLT_LOCKSYSTEM_AFTER_AUTOLOGIN、JBOLT_DOMAIN_PUT_ON_RECORD_INFO、JBOLT_DOMAIN_PUT_ON_RECORD_LINK、JBOLT_DEPT_MGR_TYPE、JBOLT_PASSWORD_CHANGE_NOTICE_DAYS、JBOLT_DICTIONARY_DELETE_ENABLE、JBOLT_CHECK_JWT_API_USER_EFFECTIVE_ENABLE、JBOLT_CHECK_JWT_API_USER_EFFECTIVE_TOKEN

### JBoltIDGenMode (interface) extends Object

方法：isNeedSysAutoAssign(String)

常量：AUTO、AUTO_LONG、AUTO_STRING、UUID、ASSIGN_STRING、ASSIGN_INT、ASSIGN_LONG、SEQUENCE、SEQUENCE_LONG、SERIAL、BIGSERIAL、SNOWFLAKE、SNOWFLAKE_STRING

### JBoltInjector (class) extends Object

方法：<init>()；injectBean(ClassHttpServletRequestZ)；injectBean(ClassStringHttpServletRequestZ)；injectBean(ClassStringMap)；injectBean(ClassStringMapZ)；injectModel(ClassHttpServletRequestZ)；injectModel(ClassStringHttpServletRequestZ)；injectModel(ClassStringMap)；injectModel(ClassStringMapZ)

### JBoltMsg (interface) extends Object

常量：GLOBAL_EXCEPTION_DEFAULT_ERROR_MSG、PARAM_ERROR、SUCCESS、WARN、FAIL、NOPERMISSION、FAIL_NEED_CATEGORY、FAIL_INIT_GOODS、TABLE_NOT_EXIST、DATA_NOT_EXIST、DATA_SAME_NAME_EXIST、DATA_NAME_SN_COUNT_NOT_EQUALS、DATA_SAME_SN_EXIST、DATA_INUSE、DEMO_MODE_CAN_NOT_DELETE、DEMO_MODE_LIMIT_MSG、DATA_IMPORT_FAIL、DATA_IMPORT_FAIL_EMPTY、NOT_ALLOWED_JBOLT_API、ADMIN_NO_LOGIN_INFO、ADMIN_TERMINAL_OFFLINE_INFO、ADMIN_TERMINAL_OFFLINE_PAGE_MSG、ADMIN_FORCED_OFFLINE_PAGE_MSG、ADMIN_AJAXPORTAL_NO_LOGIN_INFO、ADMIN_AJAXPORTAL_TERMINAL_OFFLINE_INFO、ERROR_404_NOTFOUND、JBOLT_SYSTEM_LOCKED、EXIST_SON、DEPT_USER_USE、DEPT_SAME_FULLNAME_EXIST、DEPT_SAME_SN_EXIST、PARENT_CAN_NOT_SAME_SELF、JBOLTTABLE_IS_BLANK、INTERCEPTOR_MUST_EXTEND_JBOLT_BASE_CONTROLLER、INTERCEPTOR_MUST_EXTEND_JBOLT_API_BASE_CONTROLLER、INTERCEPTOR_NO_PERMISSIONKEY、INTERCEPTOR_NO_AUTH_ASSIGN、INTERCEPTOR_CHECK_NO_AUTH、INTERCEPTOR_NO_AUTH_ONLYSAASPLATFORM

### JBoltPageSize (interface) extends Object

常量：PAGESIZE_ADMIN_LIST、PAGESIZE_ADMIN_LIST_10、PAGESIZE_ADMIN_LIST_15、PAGESIZE_ADMIN_LIST_20、PAGESIZE_ADMIN_LIST_25、PAGESIZE_ADMIN_LIST_30、PAGESIZE_ADMIN_LIST_35、PAGESIZE_ADMIN_LIST_40、PAGESIZE_ADMIN_LIST_45、PAGESIZE_ADMIN_LIST_50、PAGESIZE_ADMIN_LIST_100、PAGESIZE_API_LIST_5、PAGESIZE_API_LIST_6、PAGESIZE_API_LIST_10、PAGESIZE_API_LIST_15、PAGESIZE_API_LIST_20

### JBoltRequestType (class) extends Object

方法：<init>()

常量：NULL、AJAX、JSON、PJAX、AJAXPORTAL、DIALOG、IFRAME、NORMAL、SINGLE_PAGE、JBOLTAPI

### TreeCheckIsParentNode (interface) extends Object

方法：isParent(Object)

## cn.jbolt.core.base.config

### JBoltConfig (class) extends Object

方法：<init>()；changeAssetsVersion()；configActionReportWriter()；configAssetsVersion()；configJBoltActionReportLineNumberEnable()；configJBoltAutoCacheLog()；configWechat()；createDruidPlugin()；getDriverClass(String)；getProjectSystemLogProcessor()；isDM()；isLoadByServerStart()；isMysql()；isOracle()；isPostgresql()；isProxyByCglib()；isProxyByJFinal()；isProxyByJavassist()；isSqlServer()；loadConfig()；pdevIsPro()；setDialect(ActiveRecordPlugin)；setLoadByServerStart()；setProjectSystemLogProcessor(JBoltProjectSystemLogProcessor)

### JBoltExtendDatabaseConfig (class) extends Object

方法：config(PluginsJBoltProjectConfig)；createDruidPlugin(Setting)；createDruidPlugin(String)；getSetting()；me()；setDialect(ActiveRecordPluginString)

### JBoltProjectConfig (class) extends JFinalConfig

方法：<init>()；configConstant(Constants)；configConstants(Constants)；configEngine(Engine)；configEngines(Engine)；configHandler(Handlers)；configInterceptor(Interceptors)；configInterceptors(Interceptors)；configPlugin(Plugins)；configPlugins(Plugins)；configRoute(Routes)；configRoutes(Routes)；createDruidPlugin()；initJwtCheckToken()；onStart()；onStop()

### JBoltSnowFlakeIdConfig (class) extends Object

方法：<init>()；getDataCenterId()；getWorkId()

## cn.jbolt.core.bean

### IpRegion (class) extends Object

方法：<init>()；getAddress()；getCity()；getCityCode()；getCountry()；getIp()；getIsp()；getLatitude()；getLongitude()；getProvince()；setCity(String)；setCityCode(String)；setCountry(String)；setIp(String)；setIsp(String)；setLatitude(String)；setLongitude(String)；setProvince(String)

### JBoltDateRange (class) extends Object

方法：<init>(StringString)；<init>(StringStringString)；<init>(StringStringStringString)；getEnd()；getEndDate()；getEndDate(Date)；getEndDateStr()；getEndDateStr(String)；getEndDateTime()；getEndDateTime(Date)；getEndDateTimeStr()；getEndDateTimeStr(String)；getEndTime()；getEndTimeStr()；getEndTimeStr(String)；getEndTimestamp()；getEndTimestamp(Timestamp)；getEndYear()；getEndYearMonth()；getFormat()；getOriginDateRange()；getParaName()；getSplitChar()；getStart()；getStartDate()；getStartDate(Date)；getStartDateStr()；getStartDateStr(String)；getStartDateTime()；getStartDateTime(Date)；getStartDateTimeStr()；getStartDateTimeStr(String)；getStartTime()；getStartTimeStr()；getStartTimeStr(String)；getStartTimestamp()；getStartTimestamp(Timestamp)；getStartYear()；getStartYearMonth()；getType()；isEmpty()；setEnd(String)；setFormat(String)；setOriginDateRange(String)；setParaName(String)；setSplitChar(String)；setStart(String)；setType(String)

常量：TYPE_YEAR、TYPE_MONTH、TYPE_DATE、TYPE_DATETIME、TYPE_TIME、DEFAULT_PARA_NAME

### JBoltOfModuleInfo (class) extends Object

方法：<init>(I)；<init>(IString)；getOfModule()；getOfModuleLink()；isNotPlatformInnerAdminCall()；isPlatformInnerAdminCall()；setOfModule(I)；setOfModuleLink(String)

常量：PLATFORM_INNER_ADMIN、PLATFORM_INNER_ADMIN_INFO

### JsTreeBean (class) extends Object

方法：<init>(LongLongStringStringZ)；<init>(ObjectObjectStringStringStringZ)；<init>(ObjectObjectStringZ)；<init>(ObjectObjectStringZStringZ)；<init>(ObjectObjectStringZZStringZ)；getChildren()；getData()；getIcon()；getId()；getOrder()；getParent()；getState()；getText()；getType()；setChildren(Boolean)；setData(Object)；setIcon(String)；setId(String)；setOrder(I)；setParent(String)；setState(JsTreeStateBean)；setText(String)；setType(String)

### JsTreeStateBean (class) extends Object

方法：<init>()；<init>(Z)；<init>(ZZ)；isDisabled()；isOpened()；isSelected()；setDisabled(Z)；setOpened(Z)；setSelected(Z)

### MultipleUploadFile (class) extends Object

方法：<init>()；<init>(StringStringString)；getFileId()；getFileName()；getUrl()；setFileId(String)；setFileName(String)；setUrl(String)

### Option (interface) extends Object

方法：getText()；getValue()

### OptionBean (class) extends Object implements Serializable, Option

方法：<init>()；<init>(String)；<init>(StringObject)；<init>(StringObjectBoolean)；getSelected()；getText()；getValue()；setSelected(Boolean)；setText(String)；setValue(Object)

### RangeSlider (class) extends Object

方法：create(StringString)；fromSplit(StringString)；getFrom()；getFromBigDecimalValue()；getFromDoubleValue()；getFromIntValue()；getTo()；getToBigDecimalValue()；getToDoubleValue()；getToIntValue()；setFrom(String)；setTo(String)

常量：SPLIT

### SortInfo (class) extends Object

方法：<init>(StringString)；<init>(StringStringString)；getKey()；getSortColumn()；getSortType()；setKey(String)；setSortColumn(String)；setSortType(String)

## cn.jbolt.core.cache

### JBoltApplicationCache (class) extends JBoltCache

方法：<init>()；get(Long)；getApiCallApplication(String)；getAppId(Long)；getAttr(LongString)；getByAppId(String)；getCacheTypeName()；getName(Long)；getPcInnerPlatformApplication()；removeApiCallApplication(String)；removePcInnerPlatformApplication()

常量：me

### JBoltCache (class) extends JBoltCacheParaValidator

方法：<init>()；getCacheTypeName()

### JBoltCacheInterceptor (class) extends Object implements Interceptor

方法：<init>()；intercept(Invocation)

### JBoltCacheKit (class) extends Object

方法：<init>()；get(StringObject)；get(StringObjectClass)；get(StringObjectClassJ)；get(StringObjectIDataLoader)；get(StringObjectIDataLoaderJ)；getKeys(String)；getRedisKeys(StringString)；getRedisKeysList(StringString)；put(StringObjectObject)；put(StringObjectObjectJ)；remove(String[Object)；removeAll(String)；removeRedisAllDbDatas(String)

### JBoltCacheModelClass (interface) extends Object implements Annotation

方法：value()

### JBoltCacheParaValidator (class) extends Object implements IJBoltParaValidator

方法：<init>()；hasNotOk([Object)；hasOk([Object)；isExcel(UploadFile)；isImage(String)；isImage(UploadFile)；isNull(Object)；isOk(Model)；isOk(Object)；isOk(Record)；isOk([Object)；notExcel(UploadFile)；notImage(String)；notImage(UploadFile)；notNull(Object)；notOk(Model)；notOk(Object)；notOk(Record)；notOk([Object)

### JBoltCacheType (interface) extends Object

常量：EHCACHE、CAFFEINE、REDIS、J2CACHE

### JBoltDeptCache (class) extends JBoltCache

方法：<init>()；checkHasDept()；get(Object)；getBySn(String)；getCacheTypeName()；getFullName(Object)；getFullNameBySn(String)；getName(Object)；getNameBySn(String)；getSn(Object)；getSnAndName(Object)；removeCheckHasDept()

常量：me

### JBoltDictionaryCache (class) extends JBoltCache

方法：<init>()；get(Long)；getBySn(StringString)；getCacheTypeName()；getListBySns(StringString)；getListByTypeId(LongZ)；getListByTypeKey(StringZ)；getName(Long)；getNameBySn(StringString)；getNameListBySns(StringString)；getNameStrBySns(StringString)；getParentListToRoot(Long)；getSn(Long)；isBuildIn(Long)；removeListByTypeId(Long)

常量：me

### JBoltDictionaryTypeCache (class) extends JBoltCache

方法：<init>()；get(Long)；getByKey(String)；getCacheTypeName()；getKey(Long)；getModeLevel(Long)；getName(Long)；getNameByKey(String)；getTypes()；isBuildIn(Long)；removeTypes()

常量：me

### JBoltEvictInterceptor (class) extends Object implements Interceptor

方法：<init>()；intercept(Invocation)

### JBoltGlobalConfigCache (class) extends JBoltCache

方法：<init>()；deptEnable()；get(String)；getAssetsVersion()；getBigDecimalConfigValue(String)；getBigDecimalConfigValue(StringBigDecimal)；getBooleanConfigValue(String)；getBooleanConfigValue(StringBoolean)；getCacheTypeName()；getCheckJwtApiUserEffectiveEnable()；getCheckJwtApiUserEffectiveToken()；getConfigValue(String)；getConfigValue(StringString)；getConfigValueWithSupplier(StringSupplier)；getIntConfigValue(String)；getIntConfigValue(StringInteger)；getJBoltActionReportLineNumberEnable()；getJBoltAdminWithTabs()；getJBoltAutoCacheLog()；getJBoltAutoLockScreenSeconds()；getJBoltLoginBgimgBlur()；getJFinalActionReportWriter()；getJboltLoginBgType()；getJboltLoginBgimg()；getJboltLoginNest()；getJboltStyle()；getJboltTabKeepAfterReload()；getKeepLoginSeconds()；getLoginFile()；getNotKeepLoginSeconds()；getSystemAdminGlobalSearchShow()；getSystemAdminH50()；getSystemAdminLeftNavWidth()；getSystemAdminNavMenuDefaultIcon()；getSystemCopyrightColor(String)；getSystemCopyrightCompany(String)；getSystemDeptMgrType()；getSystemDeptMgrTypeValue()；getSystemDictionaryDeleteEnable()；getSystemDomainPutOnRecordInfo()；getSystemDomainPutOnRecordLink()；getSystemName(String)；getSystemNameColor(String)；getSystemPasswordChangeNoticeDays()；getWechatMpServerDomainRootUrl()；getWechatWxaServerDomainRootUrl()；isJBoltLoginUseCapture()；isLoginTerminalOnlyOne()；postEnable()；userExtendEnable()

常量：me

### JBoltGlobalConfigTypeCache (class) extends JBoltCache

方法：<init>()；get(Long)；getByKey(String)；getCacheTypeName()；getName(Long)

常量：me

### JBoltOnlineUserCache (class) extends JBoltCache

方法：<init>()；get(Long)；getBySessionId(String)；getCacheTypeName()；getOnlineUserSessionListByUserId(Long)；getUserBySessionId(String)；getUserIdBySessionId(String)；removeOnlineUserSessionListByUserId(Long)

常量：me

### JBoltPermissionCache (class) extends JBoltCache

方法：<init>()；get(Long)；getByKey(String)；getCacheTypeName()；getCurrentUserMenus()；getOfModule(Long)；getOfModuleByKey(String)；getOfModuleLink(Long)；getOfModuleLinkByKey(String)；getOfModuleLinkByKeyToInt(String)；getOfModuleLinkByKeyToLong(String)；getOfModuleLinkToInt(Long)；getOfModuleLinkToLong(Long)；getRoleMenus(String)；getRoleMenusWithSystemAdminDefault(String)；getRolePermissionKeySet(String)；getUserMenus(Long)；isPlatformInnerAdminUser(Long)；isPlatformInnerAdminUserByKey(String)；removeMenusAndPermissionsByRoleGroups()；removeRolesMenus(String)；removeRolesMenusWithSystemAdminDefault(String)；removeRolesPermissionKeySet(String)

常量：me

### JBoltPostCache (class) extends JBoltCache

方法：<init>()；checkHasPost()；get(Long)；getBySn(String)；getCacheTypeName()；getName(Long)；getPostSnAndName(Long)；getPosts(String)；removeCheckHasPost()

常量：me

### JBoltRoleCache (class) extends JBoltCache

方法：<init>()；get(Long)；getBySn(String)；getCacheTypeName()；getIdBySn(String)；getIdsBySns([String)；getListByIds(String)；getListByIds([Long)；getListBySns(String)；getListBySns([String)；getName(Long)；getNameBySn(String)；getNameListByIds(String)；getNameListByIds([Long)；getNameListBySns(String)；getNameListBySns([String)；getRoles(String)；getSn(Long)；getSnListByIds(String)；getSnListByIds([Long)；getSnStrByIds(String)；getSnStrByIds([Long)

常量：me

### JBoltTopnavCache (class) extends JBoltCache

方法：<init>()；get(Long)；getCacheTypeName()；getCurrentUserTopNavs()；getEnableTopNavs()；getJBoltEnableTopnav()；getName(Long)；getTopNavs()；removeEnableTopNavs()；removeJBoltEnableTopnav()

常量：me

### JBoltUserCache (class) extends JBoltCache

方法：<init>()；get(Long)；getAvatar(Long)；getByUsername(String)；getCacheTypeName()；getCurrentAdminUser()；getDeptId(Long)；getDeptName(Long)；getDeptSn(Long)；getEmail(Long)；getIdByUsername(String)；getLastPwdUpdateTime(Long)；getListByIds(List)；getListByIds([Long)；getListByIdsStr(String)；getName(Long)；getNameByUsername(String)；getOfModule(Long)；getOfModule(String)；getOfModuleLink(Long)；getOfModuleLink(String)；getOfModuleLinkToInt(Long)；getOfModuleLinkToInt(String)；getOfModuleLinkToLong(Long)；getOfModuleLinkToLong(String)；getPhone(Long)；getRealAvatar(Long)；getRoles(Long)；getRolesByUserName(String)；getUserName(Long)；isNotPlatformInnerAdminUser(Long)；isPlatformInnerAdminUser(Long)；isPlatformInnerAdminUser(String)；isPlatformInnerAdminUser(User)

常量：me

### JBoltUserConfigCache (class) extends JBoltCache

方法：<init>()；get(LongString)；getCacheTypeName()；getConfigValue(LongString)；getCurrentUserJBoltAdminWithTabs()；getCurrentUserJboltStyle()；getJBoltAdminWithTabs(Long)；getJBoltLoginBgimgBlur(Long)；getJBoltLoginNest(Long)；getJboltStyle(Long)

常量：me

### JBoltWechatConfigCache (class) extends JBoltCache

方法：<init>()；get(Long)；getAppId(Long)；getAppIdConfig(String)；getAppSecret(Long)；getCacheTypeName()；getConfigValue(LongIString)；getMchId(Long)；getMpidByAppId(String)；getPaternerKey(Long)；removeAppIdConfig(String)；removeConfigValue(LongIString)；removeMpidByAppId(String)

常量：me

### JBoltWechatMpinfoCache (class) extends JBoltCache

方法：<init>()；get(Long)；getCacheTypeName()

常量：me

## cn.jbolt.core.cache.caffeine

### CaffeineCache (class) extends Object implements ICache

方法：<init>()；get(StringObject)；put(StringObjectObject)；remove(StringObject)；removeAll(String)

### CaffeineCacheKit (class) extends Object

方法：<init>()；asMap(String)；buildCaffeine(StringLong)；get(StringObject)；get(StringObjectClass)；get(StringObjectFunction)；getCache(String)；getKeys(String)；put(StringObjectObject)；put(StringObjectObjectJ)；putCache(StringCache)；remove(String[Object)；removeAll(String)；removeCache(String)

## cn.jbolt.core.cache.redis

### JBoltRedisFstSerializer (class) extends Object implements ISerializer

方法：<init>()；fieldFromBytes([B)；fieldToBytes(Object)；keyFromBytes([B)；keyToBytes(String)；valueFromBytes([B)；valueToBytes(Object)

常量：me

### JBoltRedisSerializerType (enum) extends Enum

方法：valueOf(String)；values()

常量：fst、jb_fst、fury

### RedisCache (class) extends Object implements ICache

方法：<init>()；get(StringObject)；put(StringObjectObject)；remove(StringObject)；removeAll(String)

## cn.jbolt.core.captcha

### Encoder (class) extends Object

### GifEncoder (class) extends Object

方法：<init>()；addFrame(BufferedImage)；finish()；getFrameByteArray()；outFlush()；reset()；setDelay(I)；setDispose(I)；setFrameRate(F)；setQuality(I)；setRepeat(I)；setSize(II)；setTransparent(Color)；start(OutputStream)；start(String)

### JBoltCaptcha (class) extends Randoms

方法：<init>()；gen(String)；getExpireAt()；getFont()；getHeight()；getKey()；getLen()；getValue()；getWidth()；isExpired()；notExpired()；setFont(Font)；setHeight(I)；setLen(I)；setWidth(I)；text()；toString()

常量：DEFAULT_EXPIRE_TIME

### JBoltCaptchaCache (class) extends Object implements ICaptchaCache

方法：<init>()；get(String)；put(Captcha)；remove(String)；removeAll()

### JBoltCaptchaRender (class) extends CaptchaRender

方法：<init>()；<init>(String)；render()

### JBoltGifCaptcha (class) extends JBoltCaptcha

方法：<init>()；<init>(II)；<init>(III)；<init>(IIIFont)；out(OutputStream)

### JBoltSpecCaptcha (class) extends JBoltCaptcha

方法：<init>()；<init>(II)；<init>(III)；<init>(IIIFont)；out(OutputStream)

### NumberCalcCaptcha (class) extends Object

方法：<init>()；content()；create()；result()；resultStr()

### NumberCalcCnCaptcha (class) extends Object

方法：<init>()；drawDisturbLine1(Graphics)；drawDisturbLine2(Graphics)；drawRandomString(Graphics2DStringIF)；drawVerificationCodeImage()；getCalcResult()；getCalcResultStr()；getRandomColor(II)；getRandomString()

### Quant (class) extends Object

方法：<init>([BII)；colorMap()；inxbuild()；learn()；map(III)；process()；unbiasnet()

### Randoms (class) extends Captcha

方法：<init>()；alpha()；num(I)；num(II)

常量：ALPHA

## cn.jbolt.core.common.enums

### DictionaryTypeMode (enum) extends Enum

方法：getText()；getValue()；setText(String)；setValue(I)；valueOf(String)；values()

常量：LEVEL_ONE、LEVEL_MULTI

### ImageType (enum) extends Enum

方法：eq(String)；isImage(String)；notImage(String)；valueOf(String)；values()

常量：JPG、PNG、JPEG、GIF、BMP

### JBoltApplicationType (enum) extends Enum

方法：getText()；getValue()；setText(String)；setValue(I)；valueOf(String)；values()

常量：PC_INNER_PLATFORM、PC_WEB、MOBILE_H5、MP_H5、WECHAT_XCX、ALIPAY_XCX、DOUYIN_XCX、BAIDU_XCX、TOUTIAO_XCX、QQ_XCX、MOBILE_APP、PC_DESKTOP、OTHER

### JBoltDeptMgrType (enum) extends Enum

方法：getText()；getValue()；setText(String)；setValue(String)；valueOf(String)；values()

常量：TREE_TABLE、JS_TREE

### JBoltGlobalConfigTypeBuiltInEnum (enum) extends Enum

方法：getText()；getValue()；setText(String)；setValue(String)；valueOf(String)；values()

常量：SYS_CONFIG、SYS_SAFE_CONFIG、SYS_PUT_ON_RECORD、ADMIN_LOGIN、ADMIN_UI、WECHAT_DEV

### JBoltLoginState (enum) extends Enum

方法：getText()；getValue()；setText(String)；setValue(I)；valueOf(String)；values()

常量：LOGIN_SUCCESS、CAPTURE_ERROR、USERNAME_PWD_ERROR、ENABLE_ERROR、NOT_ASSIGN_ROLE_ERROR、LOGIN_ERROR

### JBoltSystemLogOpenType (enum) extends Enum

方法：getText()；getValue()；setText(String)；setValue(I)；valueOf(String)；values()

常量：URL、DIALOG

### JBoltSystemLogTargetType (enum) extends Enum

方法：getText()；getValue()；setText(String)；setValue(I)；valueOf(String)；values()

常量：NONE、DICTIONARY_TYPE、DICTIONARY、PERMISSION、USER、ROLE、GLOBAL_CONFIG、GLOBAL_CONFIG_TYPE、WECHAT_MPINFO、WECHAT_CONFIG、WECHAT_AUTOREPLY、WECHAT_REPLYCONTENT、WECHAT_KEYWORDS、WECHAT_MENU、APPLICATION、USER_CONFIG、TOPNAV、DEPT、POST、QINIU、QINIU_BUCKET、JBOLT_FILE

### JBoltSystemLogType (enum) extends Enum

方法：getText()；getValue()；setText(String)；setValue(I)；valueOf(String)；values()

常量：SAVE、UPDATE、DELETE、RECOVER、LINK_SAVE、LINK_UPDATE、LINK_DELETE、LINK_RECOVER

### JBoltUserOnlineState (enum) extends Enum

方法：getText()；getValue()；setText(String)；setValue(I)；valueOf(String)；values()

常量：ONLINE、OFFLINE、FORCED_OFFLINE、TERMINAL_OFFLINE

## cn.jbolt.core.consts

### JBoltConst (interface) extends Object

常量：JBOLT_VERSION、JBOLT_DEFAULT_SYSTEM_NAME、JBOLT_DEFAULT_SYSTEM_COPYRIGHT_COMPANY、JBOLT_DEFAULT_SYSTEM_COPYRIGHT_LINK、JBOLT_CACHE_DEFAULT_NAME、JBOLT_CACHE_DEFAULT_PREFIX、JBOLT_CAPTCHA_CACHE_PREFIX、JBOLT_DEFAULT_CHECK_JWT_API_USER_EFFECTIVE_ENABLE、JBOLT_DEFAULT_CHECK_JWT_API_USER_EFFECTIVE_TOKEN、JBOLT_CAPTCHA_TYPE_DEFAULT、JBOLT_CAPTCHA_TYPE_GIF、JBOLT_CAPTCHA_TYPE_BUBBLE_PNG、JBOLT_CAPTCHA_TYPE_NUMBER_CALC、JBOLT_CAPTCHA_TYPE_NUMBER_CALC_CN、CHECKBOX_ON、HTTPS、HTTP、DOT_CSS、DOT_JS、WWW、MHSP、HTTPS_MHSP、HTTP_MHSP、MH、DOT_HTML、SLASH、SLASH_STR、DOT、TRUE、RQKEY_JB_RQTYPE、RQKEY_CTX、RQKEY_PMKEY、RQKEY_NEED_ALWAYS_HTTPS、RQKEY_BASEPATH、JBOLT_BASE_UPLOAD_PATH_PRE、JBOLT_GLOBAL_UPLOAD_TO、RQ_HEAD_KEY_JBOLTAPI、RQ_HEAD_KEY_XPJAX、RQ_HEAD_KEY_AJAXPORTAL、RQ_HEAD_KEY_AJAX_DOWNLOAD、RQ_HEAD_KEY_XREQUESTEDWITH、RQ_HEAD_KEY_ACCEPT、RQ_HEAD_KEY_CONTENT_TYPE、RQ_CONTENT_TYPE_JSON、XMLHTTPREQUEST、RQ_TYPE_DIALOG、RQ_TYPE_SINGLE_PAGE、RQ_TYPE_DIALOG_STR、RQ_TYPE_SINGLE_PAGE_STR、RQ_TYPE_IFRAME、RQ_TYPE_IFRAME_STR、RESOURCE_SUFFIXES、UNLOCKSYSTEM_ACTION_URL、IMG_NOTHING_BASE64、IMG_NOTHING_BASE64_FILE、CONFIG_PROPERTIES_KEY_DB_TYPE、DEFAULT_PRIMARY_KEY_NAME、JBOLT_SESSIONID_KEY、JBOLT_KEEPLOGIN_KEY、JBOLT_SESSIONID_REFRESH_TOKEN、JBOLT_KEEPLOGIN_DEFAULT_SECONDS、JBOLT_NOT_KEEPLOGIN_DEFAULT_SECONDS、JBOLT_AUTO_LOCKSCREEN_DEFAULT_SECONDS、JBOLT_ADMIN_LOGIN_ACTION_KEY、JBOLT_ADMIN_TERMINAL_OFFLINE_ACTION_KEY、JBOLT_ADMIN_FORCED_OFFLINE_ACTION_KEY、JBOLT_ADMIN_REDIRECT_TO_SCREENLOCK_PAGE_ACTION_KEY、JBOLT_ADMIN_LOGIN_DEFAULT_FILE、JBOLT_ID_SEQUENCE_DEFAULT、JBOLT_SQLSERVER_SCHEMA_DEFAULT、CAFFEINE_CACHE_DEFAULT_INITIALCAPACITY、CAFFEINE_CACHE_DEFAULT_MAXIMUMSIZE、CAFFEINE_CACHE_DEFAULT_EXPIREAFTERACCESS_MINUTES、REDIS_EXPIRES_NONE、REDIS_EXPIRES_KEY、DEFAULT_DATABASE_PASSWORD_SALT、JBOLT_PROXY_TYPE_JFINAL_PROXY、JBOLT_PROXY_TYPE_CGLIB、JBOLT_PROXY_TYPE_JAVASSIST、JBOLT_DEFAULT_PROXY_TYPE

## cn.jbolt.core.controller.base

### JBoltBaseController (class) extends JBoltCommonController

方法：<init>()；isAdminLogin()；renderDialogFail(String)；renderDialogFailRet(Ret)；renderFail(String)；renderFail(StringObject)；renderFormFail(String)；renderFormFailRet(Ret)；renderJsonConfirm(StringStringString)；renderJsonConfirm(StringStringStringObject)；renderJsonConfirm(StringStringStringObjectZ)；renderJsonConfirmReqDownload(StringString)；renderJsonConfirmReqGet(StringString)；renderJsonConfirmReqPost(StringString)；renderJsonConfirmReqPost(StringStringObject)；renderJsonConfirmReqPost(StringStringObjectZ)；renderPageFail(String)；renderPageFailRet(Ret)；renderPdfViewer()；renderPjaxFail(String)；renderPjaxFail(StringString)

### JBoltCommonController (class) extends Controller implements IJBoltParaValidator

方法：<init>()；getBean(Class)；getBean(ClassString)；getBean(ClassStringZ)；getBean(ClassZ)；getBeans(ClassString)；getCheckBoxBoolean(String)；getCheckBoxValues(String)；getCheckBoxValuesToInt(String)；getCheckBoxValuesToLong(String)；getDateRange()；getDateRange(String)；getDateRange(StringString)；getId(Class)；getId(IClass)；getId(StringClass)；getJBoltPara()；getJBoltTable()；getJBoltTables()；getJSONArray()；getJSONArray(String)；getJSONList(Class)；getJSONList(StringClass)；getJSONObject()；getJSONObject(String)；getJSONObjectList()；getJSONObjectList(String)；getModel(Class)；getModel(ClassString)；getModel(ClassStringZ)；getModel(ClassZ)；getModels(ClassString)；getParaToArray(String)；getParaToArray(StringString)；getParaToIntArray(String)；getParaToIntArray(StringString)；getParaToLongArray(String)；getParaToLongArray(StringString)；getRangeSlider(I)；getRangeSlider(IString)；getRangeSlider(String)；getRangeSlider(StringString)；getTime(String)；hasNotOk([Object)；hasOk([Object)；isAjax()；isAjaxPortal()；isExcel(UploadFile)；isImage(String)；isImage(UploadFile)；isNull(Object)；isOk(Model)；isOk(Object)；isOk(Record)；isOk([Object)；isPjax()；keepBean(ClassString)；keepCheckbox(String)；keepModel(ClassString)；notExcel(UploadFile)；notImage(String)；notImage(UploadFile)；notNull(Object)；notOk(Model)；notOk(Object)；notOk(Record)；notOk([Object)；renderBubblePngCaptcha()；renderByteToHTMLFile([BString)；renderByteToImage([B)；renderByteToImageFile([BString)；renderByteToJsonFile([BString)；renderByteToPDF(String)；renderByteToPDF([B)；renderByteToPDFFile([BString)；renderByteToTXT([B)；renderByteToTXTFile([BString)；renderBytesToExcelXlsFile(JBoltExcel)；renderBytesToExcelXlsFile([BString)；renderBytesToExcelXlsxFile(JBoltExcel)；renderBytesToExcelXlsxFile([BString)；renderBytesToWordDocFile(JBoltWord)；renderBytesToWordDocFile([BString)；renderBytesToWordDocxFile(JBoltWord)；renderBytesToWordDocxFile([BString)；renderGifCaptcha()；renderJBoltCaptcha(String)；renderJBoltTableJsonData(ObjectObject)；renderJsonData(Object)；renderJsonData(ObjectString)；renderJsonData(ObjectZ)；renderJsonFail(String)；renderJsonSuccess()；renderJsonSuccess(String)；renderStringToHTMLFile(StringString)；renderStringToJsonFile(StringString)；renderStringToPDFFile(StringString)；renderStringToTXT(String)；renderStringToTXTFile(StringString)；setDateRange(JBoltDateRange)；setDateRange(JBoltDateRangeString)；setDateRange(String)；setDateRange(StringString)

## cn.jbolt.core.converter

### JBoltTimestampConverter (class) extends Object implements IConverter

方法：<init>()；convert(String)；doConvert(String)；doConvertDate(String)；doConvertDateTime(String)；doConvertInputString(String)；doConvertShowString(String)；doConvertString(String)；doConvertWithT(String)

## cn.jbolt.core.converter.htmltojson

### JBoltHighlighterRendererFactory (class) extends Object implements StyleRendererFactory

方法：<init>()；create(String)

### JBoltHtmlToJson (class) extends Object

方法：by(StringJBoltHtmlToJsonParams)；elementIsEmpty(Element)；get()；isCommentNode(String)；isDataNode(String)；isNotNullSpan(Node)；isScriptNode(String)；isTextNode(String)；processChildNodes(NodeStringJSONObjectZ)；processClass(NodeStringJSONObject)；processDataNode(StringString)；processId(NodeStringJSONObject)；processLiWithoutLeftWhiteSpace(StringJSONObjectJSONArray)；processMain(ElementStringJSONObject)；processMutilTextNode(JSONArrayStringString)；processPreLi(StringI)；processPreOl(ElementJSONObject)；processStyle(NodeStringJSONObject)；processTagA(JSONObjectElement)；processTagImg(JSONObjectElement)；processTagPre(JSONObjectElement)；processTagVideoOrAudio(JSONObjectStringElement)；processTextNode(StringString)

### JBoltHtmlToJsonNodeProcessor (interface) extends Object

方法：process(JBoltHtmlToJsonNodeZJSONObject)

### JBoltHtmlToJsonParams (class) extends Object

方法：<init>()；getBaseUri()；getHighlight()；getLinenums()；getNodeFilter()；getNodeProcessor()；getType()；isKeepClass()；isKeepId()；isSkipEmptyTextNode()；setBaseUri(String)；setHighlight(Boolean)；setKeepClass(Z)；setKeepId(Z)；setLinenums(Boolean)；setNodeFilter(Predicate)；setNodeProcessor(JBoltHtmlToJsonNodeProcessor)；setSkipEmptyTextNode(Z)；setType(String)

常量：TYPE_HTML、TYPE_MD、TYPE_MARKDOWN

## cn.jbolt.core.crossorigin

### CrossOrigin (interface) extends Object implements Annotation

### JBoltCrossOriginInterceptor (class) extends Object implements Interceptor

方法：<init>()；intercept(Invocation)

## cn.jbolt.core.db.datasource

### DatasourceFilterService (class) extends JBoltBaseService

方法：<init>()；save(DatasourceFilter)；update(DatasourceFilter)

常量：defaultTableNameFilters、defaultTableNameContains、defaultTableNamePatterns

### JBoltDataSourceUtil (class) extends Object

方法：clear()；getAllConfigs()；getAllDatasource()；getAllOptions()；getAllTableMetas(JBoltDatasource)；getAllTableMetas(JBoltDatasourcePredicate)；getAllTableMetas(String)；getAllTableMetas(StringPredicate)；getAllTablesFromCache(JBoltDatasource)；getAllTablesFromCache(JBoltDatasourcePredicate)；getAllTablesFromCache(String)；getAllTablesFromCache(StringPredicate)；getConfig(String)；getDatasource(String)；getExtendConfigs()；getExtendJBoltDataSources()；getJBoltDatasource(String)；getMainConfig()；getTableMetas(StringStringI)；getTableNameRemarkFullKvs(StringStringI)；getTableNameRemarkKvs(StringStringI)；getTableOptions(StringStringI)；getTableStream(StringStringI)；init()；processDataSourceFilter(JBoltDatasource)；refreshAllTablesCache(String)；reload()；reloadDatasourceTables(String)

常量：me

### JBoltDatasource (class) extends Object implements Serializable

方法：<init>()；_getDevDocTableSkipPredicate()；clearTables()；getConfig()；getDatasourceFilter()；getDbName()；getDbSchema()；getDbType()；getIdGenMode()；getIsMainDatasource()；getJdbcUrl()；getModelPackages()；getName()；getOptionText()；getRemark()；getTableSize()；getTableSkipPredicate()；getTables()；getUser()；hasTables()；setConfig(Config)；setDatasourceFilter(DatasourceFilter)；setDbName(String)；setDbSchema(String)；setDbType(String)；setIdGenMode(String)；setIsMainDatasource(Z)；setJdbcUrl(String)；setModelPackages(List)；setName(String)；setRemark(String)；setTables(List)；setUser(String)；tableNameContain([StringString)

### JBoltTableMetaUtil (class) extends Object

方法：<init>()；getTableMeta(StringStringStringZ)

## cn.jbolt.core.db.datasource.database.table

### JBoltDatabaseColumn (class) extends Object implements Serializable

方法：<init>()；getAttrName()；getDefaultValue()；getFixed()；getIsPrimaryKey()；getIsRequired()；getJavaType()；getLength()；getName()；getRemark()；getType()；setAttrName(String)；setDefaultValue(String)；setFixed(I)；setIsPrimaryKey(Z)；setIsRequired(Z)；setJavaType(String)；setLength(I)；setName(String)；setRemark(String)；setType(String)

### JBoltDatabaseTable (class) extends Object implements Serializable

方法：<init>()；getBaseModelName()；getColumns()；getIsMappingModel()；getJavaPackage()；getModelName()；getName()；getPrimaryKey()；getRemark()；setBaseModelName(String)；setColumns(List)；setIsMappingModel(Z)；setJavaPackage(String)；setModelName(String)；setName(String)；setPrimaryKey(String)；setRemark(String)

## cn.jbolt.core.db.dbpro

### JBoltDbPro (class) extends DbPro

方法：<init>(String)；batchSave(ListI)；batchSave(StringListI)；batchUpdate(ListI)；save(StringStringRecord)

### JBoltDbProFactory (class) extends Object implements IDbProFactory

方法：<init>()；getDbPro(String)

## cn.jbolt.core.db.dialect

### JBoltDMDialect (class) extends JBoltDialect

方法：<init>()；forDbDeleteById(String[String)；forDbFindById(String[String)；forDbSave(String[StringRecordStringBuilderList)；forDbUpdate(String[String[ObjectRecordStringBuilderList)；forFindAll(String)；forModelDeleteById(Table)；forModelFindById(TableString)；forModelSave(TableMapStringBuilderList)；forModelUpdate(TableMapSetStringBuilderList)；forPaginate(IIStringBuilder)；forTableBuilderDoBuild(String)

### JBoltDialect (class) extends Dialect

方法：<init>()

### JBoltMysqlDialect (class) extends JBoltDialect

方法：<init>()；forDbDeleteById(String[String)；forDbFindById(String[String)；forDbSave(String[StringRecordStringBuilderList)；forDbUpdate(String[String[ObjectRecordStringBuilderList)；forFindAll(String)；forModelDeleteById(Table)；forModelFindById(TableString)；forModelSave(TableMapStringBuilderList)；forModelUpdate(TableMapSetStringBuilderList)；forPaginate(IIStringBuilder)；forTableBuilderDoBuild(String)

### JBoltPostgresqlDialect (class) extends PostgreSqlDialect

方法：<init>()；forDbSave(String[StringRecordStringBuilderList)；forModelSave(TableMapStringBuilderList)；getModelGeneratedKey(ModelPreparedStatementTable)；getRecordGeneratedKey(PreparedStatementRecord[String)

### JBoltSqlServerDialect (class) extends Dialect

方法：<init>()；fillStatement(PreparedStatementList)；fillStatement(PreparedStatement[Object)；forDbDeleteById(String[String)；forDbFindById(String[String)；forDbSave(String[StringRecordStringBuilderList)；forDbUpdate(String[String[ObjectRecordStringBuilderList)；forModelDeleteById(Table)；forModelFindById(TableString)；forModelSave(TableMapStringBuilderList)；forModelUpdate(TableMapSetStringBuilderList)；forPaginate(IIStringBuilder)；forTableBuilderDoBuild(String)

## cn.jbolt.core.db.sql

### Condition (class) extends Object implements Serializable, Cloneable

方法：<init>()；<init>(StringObjectObjectString)；<init>(StringObjectString)；and()；bracketLeft()；bracketRight()；clone()；exists(String)；findInSet(ObjectStringZ)；getCompareState()；getDbType()；getDescription()；getInSql()；getInValues()；getKey()；getType()；getValue1()；getValue2()；in(StringString)；in(String[Object)；inSql(StringString)；instr(StringStringZ)；isNotNull(String)；isNull(String)；notExists(String)；notFindInSet(ObjectStringZ)；notIn(StringString)；notIn(String[Object)；notInSql(StringString)；notInstr(StringStringZ)；or()；setCompareState(String)；setDbType(String)；setInSql(String)；setInValues([Object)；setKey(String)；setType(I)；setValue1(Object)；setValue2(Object)；toSql(String)；toSql(StringZ)；validate()

常量：EQ、SQL_EXPRESS_EQ、SQL_EXPRESS_NOT_EQ、SQL_EXPRESS_GT、SQL_EXPRESS_GE、SQL_EXPRESS_LT、SQL_EXPRESS_LE、NOT_EQ、GT、LT、GE、LE、WHITESPACE、LIKE、NOTLIKE、STARTWITH、NOT_STARTWITH、ENDWITH、NOT_ENDWITH、KEY_WHITESPACE、KEY_PERCENT、KEY_SINGLE_QUOTATION_MARK、KEY_IS_NULL、KEY_IS_NOT_NULL、KEY_INSTR、KEY_EXISTS、KEY_NOT_EXISTS、OR、AND、BRACKET_LEFT、BRACKET_RIGHT、TYPE_COMPARE、TYPE_IN、TYPE_NOT_IN、TYPE_LINK、TYPE_FINDINSET、TYPE_ISNULL、TYPE_ISNOTNULL、TYPE_INSTR、TYPE_NOT_INSTR、TYPE_EXISTS、TYPE_NOT_EXISTS、TYPE_NOT_FINDINSET

### DBType (class) extends Object

方法：<init>()；isDB2(String)；isDM(String)；isH2(String)；isMysql(String)；isOracle(String)；isPostgresql(String)；isSqlServer(String)；isSqlite(String)

常量：MYSQL、ORACLE、POSTGRESQL、SQLSERVER、SQLITE、H2、DB2、DM

### OrderBy (class) extends Object

方法：asc(String)；create(StringZ)；desc(String)；getColumn()；getType()；setColumn(String)；setType(String)；toString()

### Sql (class) extends Object implements Serializable, Cloneable

方法：and()；asc(Z[String)；asc([String)；avg(String)；between(StringBigDecimalBigDecimal)；between(StringIntegerInteger)；betweenDateRange(StringJBoltDateRange)；bracketLeft()；bracketRight()；bwDate(StringDateDate)；bwDateTime(StringDateDate)；clearGroupBy()；clearOrderBy()；clearPage()；count()；count(String)；countByGroup(String)；create()；create(String)；delete()；delete(String)；desc(Z[String)；desc([String)；distinct([String)；distinctCount(String)；distinctToDistinctCount()；dm()；dontCheckWhereExists()；enable()；enableEq(Boolean)；endWith(StringString)；eq(StringObject)；eq(StringObjectZ)；eqBooleanToChar(StringBoolean)；eqBooleanToChar(StringBooleanZ)；eqDate(StringDate)；eqDate(StringDateZ)；eqDateTime(StringDate)；eqDateTime(StringDateZ)；eqIfNotBlank(StringString)；eqIfOk(StringBigDecimal)；eqIfOk(StringInteger)；eqIfOk(StringLong)；eqInnerColumn(StringString)；eqInnerColumn(StringStringZ)；eqOr(String[Object)；eqQM([String)；eqSqlExpress(StringString)；eqSqlExpress(StringStringZ)；exists(Sql)；findInSet(ObjectStringZ)；first()；firstPage(I)；forceCheckWhereExists()；from(Class)；from(ClassString)；from(Sql)；from(SqlString)；from(String)；from(StringString)；from(StringZZ)；fromIfBlank(String)；fromIfBlank(StringZ)；fromSql(StringString)；fullJoin(ClassStringString)；fullJoin(ClassStringStringString)；fullJoin(String)；fullJoin(StringStringString)；fullJoin(StringStringStringString)；fullJoinOnId(ClassStringString)；fullJoinOnId(ClassStringStringString)；fullJoinOnId(StringStringString)；ge(StringObject)；ge(StringObjectZ)；geQM([String)；geSqlExpress(StringString)；geSqlExpress(StringStringZ)；getColumns()；getCountColumns()；getInsertValues()；getMainTableAsName()；getOrderBys()；getPageNumber()；getPageSize()；getReturnColumns()；getSelect()；getWhereValues()；groupBy(String)；groupBy(StringZ)；groupBys(Z[String)；groupBys([String)；gt(StringObject)；gt(StringObjectZ)；gtQM([String)；gtSqlExpress(StringString)；gtSqlExpress(StringStringZ)；hasMainTableAsName()；hasOrderColumns()；hasPage()；hasTable()；hasUnion()；having(String)；idEq(Object)；idEqQM()；idEqQM(String)；idNotEq(Object)；idNotEqQM()；idNotEqQM(String)；in(StringString)；in(StringStringZ)；in(StringZ[J)；in(StringZ[Object)；in(String[Object)；inList(StringList)；inList(StringListZ)；inLong(String[J)；inSql(StringSql)；inSql(StringString)；innerJoin(ClassStringString)；innerJoin(ClassStringStringString)；innerJoin(String)；innerJoin(StringStringString)；innerJoin(StringStringStringString)；innerJoinOnId(ClassStringString)；innerJoinOnId(StringStringString)；insert(String)；insert(StringString)；insert(StringString[Object)；instr(StringStringZ)；isCheckWhereExists()；isDM()；isDelete()；isDeletedEq(Boolean)；isGroupBy()；isGroupByAndQueryCount()；isHaving()；isLimitOne()；isMysql()；isNotNull([String)；isNull([String)；isOracle()；isPostgresql()；isPrepared()；isQuery()；isQueryColumnList()；isQueryCount()；isQueryDistinct()；isQueryDistinctCount()；isQueryMax()；isSelect()；isSqlServer()；isUpdate()；le(StringObject)；le(StringObjectZ)；leQM([String)；leSqlExpress(StringString)；leSqlExpress(StringStringZ)；leftJoin(ClassStringString)；leftJoin(ClassStringStringString)；leftJoin(String)；leftJoin(StringStringString)；leftJoin(StringStringStringString)；leftJoinOnId(ClassStringString)；leftJoinOnId(StringStringString)；leftLike(StringString)；leftOuterJoin(ClassStringString)；leftOuterJoin(ClassStringStringString)；leftOuterJoin(String)；leftOuterJoin(StringStringString)；leftOuterJoin(StringStringStringString)；leftOuterJoinOnId(ClassStringString)；leftOuterJoinOnId(StringStringString)；like(StringString)；like(StringStringZ)；likeMulti(StringZ[String)；likeMulti(String[String)；lt(StringObject)；lt(StringObjectZ)；ltQM([String)；ltSqlExpress(StringString)；ltSqlExpress(StringStringZ)；main([String)；max(String)；me(String)；min(String)；minus(String)；minus(StringBigDecimalInteger)；minus(StringIInteger)；minus(StringInteger)；mysql()；notEndWith(StringString)；notEq(StringObject)；notEq(StringObjectZ)；notEqBooleanToChar(StringBoolean)；notEqBooleanToChar(StringBooleanZ)；notEqQM([String)；notExists(Sql)；notFindInSet(ObjectStringZ)；notIn(StringString)；notIn(StringStringZ)；notIn(StringZ[Object)；notIn(String[Object)；notInSql(StringSql)；notInSql(StringString)；notInstr(StringStringZ)；notLike(StringString)；notLikeMulti(String[String)；notStartWith(StringString)；or()；oracle()；orderBy(StringString)；orderBy(StringStringZ)；orderBy(StringZ)；orderBy(StringZZ)；orderBy(ZZ[String)；orderBy(Z[String)；orderBy([OrderBy)；orderById()；orderById(Z)；orderById(ZZ)；orderByIdAscIfPgSql()；orderByIdDescIfPgSql()；orderBySortRank()；orderBySortRank(String)；orderBySortRank(StringZ)；orderBySortRank(Z)；orderBySql(String)；page(II)；pidEq(Object)；pidEqQM()；pidEqQM(String)；pidNotEqQM()；pidNotEqQM(String)；plus(String)；plus(StringBigDecimal)；plus(StringI)；postgresql()；prepared()；processOracleJoinSearchReturnColumns(String)；random(I)；randomOne()；rightJoin(ClassStringString)；rightJoin(ClassStringStringString)；rightJoin(String)；rightJoin(StringStringString)；rightJoin(StringStringStringString)；rightJoinOnId(ClassStringString)；rightJoinOnId(StringStringString)；rightLike(StringString)；rightOuterJoin(ClassStringString)；rightOuterJoin(ClassStringStringString)；rightOuterJoin(String)；rightOuterJoin(StringStringString)；rightOuterJoin(StringStringStringString)；rightOuterJoinOnId(ClassStringString)；rightOuterJoinOnId(StringStringString)；schema(String)；select(List)；select([String)；selectAppend([String)；selectAvg(String)；selectId()；selectMax(String)；selectMin(String)；selectPrepend([String)；selectSum(String)；set(StringObject)；setColumns([String)；setCountColumns(String)；setDbType(String)；setInsertValues([Object)；setOrderBys(List)；setSqlExpress(StringString)；sqlExpressEq(StringObject)；sqlExpressEq(StringObjectZ)；sqlExpressGe(StringObject)；sqlExpressGe(StringObjectZ)；sqlExpressGt(StringObject)；sqlExpressGt(StringObjectZ)；sqlExpressLe(StringObject)；sqlExpressLe(StringObjectZ)；sqlExpressLt(StringObject)；sqlExpressLt(StringObjectZ)；sqlExpressNotEq(StringObject)；sqlExpressNotEq(StringObjectZ)；sqlserver()；startWith(StringString)；sum(String)；toDateTime(Date)；toDateTime(String)；toEndDateTime(Date)；toEndDateTime000000(Date)；toSql()；toSql(StringBuilder)；toSqlExceptSelect()；toStartDateTime(Date)；toStartDateTime000000(Date)；toString()；toTime(String)；toTime(Time)；union(Sql)；unionAll(Sql)；update(String)；values([Object)

常量：TRUE、FALSE、TYPE_NONE、TYPE_SELECT、TYPE_INSERT、TYPE_INSERT_BATCH、TYPE_UPDATE、TYPE_DELETE、KEY_SELECT、KEY_STAR、KEY_QUESTION_MARK、KEY_INSERT、KEY_UPDATE、KEY_DELETE、KEY_LIMIT、KEY_OFFSET、KEY_ROWNUM_ORACLE、KEY_FROM、KEY_SET、KEY_WHERE、KEY_ORDERBY、KEY_GROUPBY、KEY_HAVING、KEY_WHITESPACE、KEY_COMMA、KEY_AS、KEY_AT、KEY_ON、KEY_ASC、KEY_DESC、KEY_IF、KEY_IF_BRACKETS_LEFT、KEY_IIF、KEY_IIF_BRACKETS_LEFT、KEY_ASC_TRIMED、KEY_DESC_TRIMED、KEY_COUNT_START、KEY_COUNT_COLUMN、KEY_MAX_COLUMN、KEY_MIN_COLUMN、KEY_SUM_COLUMN、KEY_AVG_COLUMN、KEY_DISTINCT、KEY_COUNT_DISTINCT、KEY_INSERT_VALUES、KEY_NULLSTRING、KEY_AND、KEY_AND_LEFT、KEY_AND_RIGHT、KEY_BETWEEN、KEY_BETWEEN_LEFT、KEY_BETWEEN_RIGHT、KEY_LEFT_JOIN、KEY_LEFT_OUTER_JOIN、KEY_RIGHT_JOIN、KEY_RIGHT_OUTER_JOIN、KEY_INNER_JOIN、KEY_FULL_JOIN、KEY_ID、KEY_ENABLE、KEY_IS_DELETED、KEY_PID、KEY_SORT_RANK、KEY_DOT、KEY_DOT_WITH_SPLASH、KEY_MYSQL_SINGLE_QUOTES、KEY_SINGLE_QUOTES、KEY_SINGLE_QUOTES_2、KEY_SQUARE_BRACKETS_LEFT、KEY_SQUARE_BRACKETS_RIGHT、KEY_BRACKET_LEFT、KEY_BRACKET_RIGHT、KEY_UNION、KEY_UNION_ALL、KEY_RAND_MYSQL、KEY_RAND_DM、KEY_RAND_ORACLE、KEY_RAND_POSTGRESQL、KEY_NEWID_SQLSERVER、KEY_TOP1

### SqlBuilder (class) extends Object

方法：buildBatchInsert(String[StringList)；buildCTE(StringStringString)；buildCaseWhen(StringMapObject)；buildDynamicWhere(ListString)；buildPaginationSql(StringIIString)；buildPivot(StringStringString[String)；buildRecursiveCTE(StringStringStringString)；buildWindowFunction(StringStringString)；isSafeIdentifier(String)

### SqlExpress (class) extends Object implements Serializable, Cloneable

方法：<init>(Serializable)；getValue()；isBlank()；setValue(Serializable)；toString()

### SqlUtil (class) extends Object

方法：boolToChar(String)；boolToChar(Z)；boolToInt(String)；boolToInt(Z)；findInSet(StringObjectObjectZ)；formatSql(String)；getSqlStats(String)；isValidSqlIdentifier(String)；likeValue(Object)；processBooleanValue(Object)；processTenantTableName(Class)；processTenantTableName(String)；processTenantTableNameInSql(ClassString)；sqlValue(Object)

### UnionSql (class) extends Object

方法：<init>(SqlZ)；appendToSql(StringBuilder)；getSql()；isUnionAll()；setSql(Sql)；setUnionAll(Z)；toString()

## cn.jbolt.core.db.util

### JBoltDbKit (class) extends Object

方法：<init>()；createIdSequence(StringString)；createProjectTenantSeparateTableFromSaasConfig(JBoltDatasourceJBoltDatasourceString)；createSeparateTable(StringStringJBoltDatasourceStringString)；createSeparateTable(StringStringStringStringString)；createTenantSeparateTable(ClassStringString)；createTenantSeparateTable(JBoltDatasourceJBoltDatasourceClassString)；getCurrentTenantTable(Class)；getTenantTable(ClassString)；jdbcExeSql(StringString)；jdbcExeSql(StringStringZ)；tableExist(JBoltDatasourceString)；tableExist(JBoltDatasourceStringZ)；tableExist(StringString)；tableExist(StringStringZ)

### JBoltTxKit (class) extends Object

方法：<init>()；clear()；getErrorMsg()；isNeedRollback()；setErrorMsg(String)；setNeedRollback(Z)

## cn.jbolt.core.enjoy.directive

### ActionDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

### AjaxPortalDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

### BooleanToStrDirective (class) extends Directive

方法：<init>()；<init>(String)；exec(EnvScopeWriter)；getDirectiveName()；setDirectiveName(String)；setExprList(ExprList)

### ColumnTypeToDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；getFixed(String)；getLength(String)；setExprList(ExprList)

### DateTimeDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

### EnableDirective (class) extends BooleanToStrDirective

方法：<init>()

### EnumToOptionsDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

### FileSizeFormatterDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

### GlobalConfigDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

### ImagePathToBase64Directive (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

### ImageUrlToBase64Directive (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；image2Base64(String)；setExprList(ExprList)

### JBoltAdminLogoDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

### JsonDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；objectToJson(ObjectString)；objectToJson(Object[StringString)；setExprList(ExprList)

### LikeValueDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

### MyStringExt (class) extends Object

方法：<init>()；toLowerCase(String)；toUpperCase(String)

### PermissionDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；hasEnd()

### PrettyTimeDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

### RealImageDirective (class) extends RealUrlDirective

方法：<init>()

### RealUrlDirective (class) extends Directive

方法：<init>()；<init>(String)；exec(EnvScopeWriter)；setExprList(ExprList)

### RoleDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；hasEnd()

### RoleSnDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；hasEnd()

### RowNumberDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

### SexDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

### SqlValueDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

### YearDirective (class) extends Directive

方法：<init>()；exec(EnvScopeWriter)；setExprList(ExprList)

## cn.jbolt.core.enumutil

### JBoltEnableEnum (enum) extends Enum

方法：getText()；getValue()；setText(String)；setValue(Boolean)；valueOf(String)；values()

常量：TRUE、FALSE

### JBoltEnum (class) extends Object

方法：<init>()；addEnjoyEngineShareObject(EngineClass)；addToNameBeanMap(Class)；addToNameBeanMap(ClassStringString)；addToNameEnumObjectMap(Class)；addToTvBeanMap(Class)；addToTvBeanMap(ClassString)；addToTvBeanMap(ClassStringString)；addToVEnumObjectMap(Class)；addToVEnumObjectMap(ClassStringString)；getByText(ClassString)；getByValue(ClassObject)；getEnumBeanMap(Class)；getEnumBeanMap(String)；getEnumClassNameList()；getEnumClassNameOptionList()；getEnumNameBeanList(Class)；getEnumNameBeanList(ClassStringString)；getEnumNameBeanMap(Class)；getEnumNameBeanMap(ClassStringString)；getEnumObjectByName(ClassString)；getEnumObjectByValue(ClassObject)；getEnumOptionList(Class)；getEnumOptionList(ClassStringString)；getEnumOptionListWithout(ClassStringString[Object)；getEnumOptionListWithout(Class[Object)；getIntValueByText(ClassString)；getIntegerValueByText(ClassString)；getTextByValue(ClassObject)；getTextByValue(StringObject)

### JBoltEnumBean (class) extends Object implements Option

方法：<init>(StringObject)；getText()；getValue()；getValueAs()；setText(String)；setValue(Object)

## cn.jbolt.core.gen

### IndexHtmlLayoutType (enum) extends Enum

方法：valueOf(String)；values()

常量：NORMAL_CRUD、NORMAL_TABLE、MASTER_SLAVE_CRUD、SLAVE_TABLE_CRUD、MASTER_SLAVE_TABLE、SLAVE_TABLE

### JBoltAbstractAssetsCompressor (class) extends Object

方法：<init>()；css(StringString)；js(StringString)

### JBoltBaseModelGenerator (class) extends BaseModelGenerator

方法：<init>(StringStringString)；generate(List)

### JBoltColumnToBuildAttrNameFunction (interface) extends Object

方法：build(String)

### JBoltDataDictionaryGenerator (class) extends DataDictionaryGenerator

方法：<init>(DataSourceString)；generate(List)

### JBoltField (interface) extends Object implements Annotation

方法：columnName()；columnType()；fixed()；maxLength()；name()；order()；remark()；required()；type()

### JBoltFieldBean (class) extends Object

方法：<init>(JBoltField)；<init>(StringStringStringStringStringZIII)；<init>(StringStringStringStringZIII)；getColumnName()；getFixed()；getMaxLength()；getName()；getOrder()；getRemark()；getRequired()；getType()；getUiType()；isRequired()；setColumnName(String)；setFixed(I)；setMaxLength(I)；setName(String)；setOrder(I)；setRemark(String)；setRequired(Z)；setType(String)；setUiType(String)

常量：TYPE_INPUT、TYPE_INPUT_PASSWORD、TYPE_INPUT_PNUMBER、TYPE_INPUT_PINT、TYPE_INPUT_PZINT、TYPE_INPUT_INT、TYPE_INPUT_PZNUMBER、TYPE_INPUT_NUMBER、TYPE_HIDDEN、TYPE_SELECT、TYPE_SELECT2、TYPE_AUTOCOMPLETE、TYPE_CHECKBOX、TYPE_RADIO、TYPE_RADIO_BOOLEAN、TYPE_TEXTAREA、TYPE_IMGUPLOADER、TYPE_FILEUPLOADER、TYPE_NEDITOR、TYPE_SUMMERNOTE、TYPE_DATETIME、TYPE_DATE、TYPE_TIME、TYPE_YEAR、TYPE_MONTH、TYPE_WEEK

### JBoltGenMethod (class) extends Object

方法：<init>(String)；<init>(StringJBoltField)；getJboltField()；getName()；setName(String)；setjBoltField(JBoltFieldBean)

### JBoltGenerator (class) extends Generator

方法：<init>(StringDataSourceStringString)；generate()

### JBoltHtmlDataDictionaryGenerator (class) extends Object

方法：<init>(StringStringStringString)；generate(List)

常量：SEPARATOR

### JBoltInnerPermissionKeyGen (class) extends Object

方法：<init>()；gen(String)；genToCurrentProject()

常量：SEPARATOR

### JBoltMainLogicBean (class) extends Object

方法：<init>(ClassStringStringStringStringStringStringZZZStringStringStringZZIndexHtmlLayoutTypeString)；getAuthor()；getCheckDelete()；getCheckPermissionKeys()；getControllerKey()；getDataName()；getDataSource()；getDialogArea()；getFormColControlMinCount()；getFormColCount()；getHasPid()；getIndexHtmlLayoutType()；getIsCRUD()；getIsMainDataSource()；getIsMasterSlave()；getItemLogicBeans()；getJavaPackage()；getMainModelClass()；getMatchColumns()；getNeedFootBox()；getNeedHeadBox()；getNeedLeftBox()；getNeedPaginate()；getNeedRightBox()；getNeedSort()；getNeedToolbar()；getNeedTrDeleteBtn()；getNeedTrEditBtn()；getOrderColumn()；getOrderType()；getPageTitle()；getPermissions()；getPrimaryKey()；getPrimaryKeyAttr()；getProjectPath()；getProjectSystemLogTargetType()；getRealControllerKey()；getSystemLogTargetType()；getUnCheckIfSystemAdmin()；getViewFolder()；isCheckDelete()；isCrudType()；isHasPid()；isMasterSlave()；isNeedFootBox()；isNeedHeadBox()；isNeedLeftBox()；isNeedPaginate()；isNeedRightBox()；isNeedSort()；isNeedToolbar()；isNeedTrDeleteBtn()；isNeedTrEditBtn()；isUnCheckIfSystemAdmin()；isUsePathAnnotation()；setAuthor(String)；setCheckDelete(Z)；setControllerKey(String)；setDataName(String)；setDataSource(String)；setDialogArea(String)；setFormColControlMinCount(I)；setFormColCount(I)；setHasPid(Z)；setIndexHtmlLayoutType(IndexHtmlLayoutType)；setIsMainDataSource(Z)；setItemLogicBeans(List)；setJavaPackage(String)；setMainModelClass(Class)；setMatchColumns(String)；setNeedFootBox(Z)；setNeedHeadBox(Z)；setNeedLeftBox(Z)；setNeedPaginate(Z)；setNeedRightBox(Z)；setNeedSort(Z)；setNeedToolbar(Z)；setNeedTrDeleteBtn(Z)；setNeedTrEditBtn(Z)；setOrderColumn(String)；setOrderType(String)；setPageTitle(String)；setPermissions(List)；setPrimaryKey(String)；setProjectPath(String)；setSystemLogTargetType(SystemLogTargetType)；setUnCheckIfSystemAdmin(Z)；setUsePathAnnotation(Z)；setViewFolder(String)

### JBoltMainLogicGenerator (class) extends Object

方法：<init>()；addGenBean(JBoltMainLogicBean)；generate()；generateOne(JBoltMainLogicBean)

### JBoltMappingKitGenerator (class) extends MappingKitGenerator

方法：<init>(StringString)；generate(List)

### JBoltMetaBuilder (class) extends MetaBuilder

方法：<init>(DataSource)；build()；getSchemaPattern()；setSchemaPattern(String)

### JBoltModelGenerator (class) extends ModelGenerator

方法：<init>(StringStringStringStringStringString)；generate(List)；getTranslateImportClass(String)

### JBoltOracleInitSqlGen (class) extends Object

方法：<init>()；gen()；genSequenceSql(Set)；genTriggerSql(Set)；main([String)

### JBoltOraclePermissionGen (class) extends Object

方法：<init>()；main([String)

### JBoltPostgresqlSequenceSqlGen (class) extends Object

方法：<init>()；gen()；genSequenceSql(Set)；main([String)

### JBoltProjectGenConfig (class) extends Object

方法：<init>()；addDontGenSeparateTableNamePrefixe([String)；addDontGenTableNames([String)；addDontGenTableNamesIndexOfStr([String)；addDontGenTableNamesPatterns([String)；init(StringStringZStringZZStringJBoltColumnToBuildAttrNameFunction[String[StringZListZ)；setDbConfig(StringStringStringString)；setModelAutoCache(ZZZStringString)；tableNamesInFiterList(String)；tableNamesIndexOf(String)；tableNamesMatch(String)

常量：SEPARATOR、projectType、generateDaoInModel、generateChainSetterInBaseModel、charToBoolean、baseModelExtendsJBoltBaseModel

### JFinalModelGenerator (class) extends Object

方法：<init>()；generate(StringStringString)；main([String)

### SystemLogTargetType (class) extends Object

方法：<init>(StringStringI)；getName()；getTargetTypeStr()；getText()；getValue()；setName(String)；setText(String)；setValue(I)

常量：NONE

### _JBoltOracleDialect (class) extends OracleDialect

方法：<init>()；forTableBuilderDoBuild(String)

### _JBoltOracleMetaBuilder (class) extends JBoltMetaBuilder

方法：<init>(DataSource)

### _JBoltSqlServerMetaBuilder (class) extends JBoltMetaBuilder

方法：<init>(DataSource)

## cn.jbolt.core.handler.base

### JBoltBaseHandler (class) extends Handler

方法：<init>()；handle(StringHttpServletRequestHttpServletResponse[Z)；processBasePath(HttpServletRequest)；processBasePath(HttpServletRequestZ)；processBasePathStrBuilder(HttpServletRequestZ)；setTenantSnParser(JBoltSaasTenantSnParser)；unlimited([String)

### JBoltSaasTenantSnDefaultParser (class) extends Object implements JBoltSaasTenantSnParser

方法：<init>()；<init>(String)；isFail()；parse(Controller)；parse(HttpServletRequest)；parse(Invocation)

### JBoltSaasTenantSnDomainParser (class) extends Object implements JBoltSaasTenantSnParser

方法：<init>()；isFail()；parse(Controller)；parse(HttpServletRequest)；parse(Invocation)

### JBoltSaasTenantSnHeaderParser (class) extends Object implements JBoltSaasTenantSnParser

方法：<init>()；<init>(String)；isFail()；parse(Controller)；parse(HttpServletRequest)；parse(Invocation)

### JBoltSaasTenantSnParser (interface) extends Object

方法：isFail()；parse(Controller)；parse(HttpServletRequest)；parse(Invocation)

常量：DEFAULT_TENANT_SN_KEY、TYPE_DEFAULT、TYPE_DOMAIN、TYPE_HEADER

## cn.jbolt.core.json

### BigDecimalSerializer (class) extends Object implements ObjectSerializer

方法：<init>()；write(JSONSerializerObjectObjectTypeI)

常量：instance

### JBoltFastJson (class) extends Json

方法：<init>()；addDeserializer(TypeObjectDeserializer)；addSerializer(TypeObjectSerializer)；addSerializerFeature([SerializerFeature)；init()；parse(StringClass)；processNullValueDefaultFeatures()；toJson(Object)；writeMapNullValue()；writeNullBooleanAsFalse()；writeNullListAsEmpty()；writeNullNumberAsZero()；writeNullStringAsEmpty()

### JBoltFastJsonFactory (class) extends Object implements IJsonFactory

方法：<init>()；getJson()；me()

### JBoltSqlTimeDeserializer (class) extends Object implements ObjectDeserializer

方法：<init>()；deserialze(DefaultJSONParserTypeObject)；getFastMatchToken()

常量：instance

## cn.jbolt.core.kit

### JBoltControllerKit (class) extends Object

方法：<init>()；checkNeedCrossOrigin(ControllerMethod)；getDateRange(Controller)；getDateRange(ControllerString)；getDateRange(ControllerStringString)；getDateRangePara(Controller)；getDateRangePara(ControllerString)；getId(ControllerIClass)；getId(ControllerStringClass)；getJBoltPara(ActionController)；getJBoltPara(Controller)；getJBoltTable(Controller)；getJBoltTableMenuFilter(ActionController)；getJBoltTableMenuFilter(Controller)；getJBoltTables(Controller)；getJSONArray(Controller)；getJSONArray(ControllerString)；getJSONList(ControllerClass)；getJSONList(ControllerStringClass)；getJSONObject(Controller)；getJSONObject(ControllerString)；getJSONObjectList(Controller)；getJSONObjectList(ControllerString)；getParaToArray(ControllerString)；getParaToArray(ControllerStringString)；getParaToIntArray(ControllerString)；getParaToIntArray(ControllerStringString)；getParaToLongArray(ControllerString)；getParaToLongArray(ControllerStringString)；getRangeSlider(ControllerI)；getRangeSlider(ControllerIString)；getRangeSlider(ControllerString)；getRangeSlider(ControllerStringString)；isAjax(Controller)；isAjaxPortal(Controller)；isCallJBoltApi(Controller)；isDialog(Controller)；isIframe(Controller)；isJsonRequest(Controller)；isNormal(Controller)；isOptions(String)；isPjax(Controller)；processCrossOrigin(ControllerMethod)；render404Error(Controller)；renderAdminInterceptorForcedOfflineInfo(Controller)；renderAdminInterceptorNotLoginInfo(Controller)；renderAdminInterceptorTerminalOfflineInfo(Controller)；renderAdminSystemLockedInfo(Controller)；renderAjaxPortalFail(ControllerString)；renderAjaxPortalSuccess(ControllerString)；renderDialogFail(ControllerString)；renderDialogSuccess(ControllerString)；renderFail(ControllerString)；renderFail(ControllerStringObject)；renderFail(ControllerStringObjectZ)；renderFormFail(ControllerString)；renderH5PageFail(ControllerString)；renderH5PageSuccess(ControllerString)；renderIframeFail(ControllerString)；renderIframeSuccess(ControllerString)；renderJBoltApiFail(ControllerString)；renderJBoltApiFail(ControllerStringObject)；renderJBoltApiFail(ControllerStringObjectZ)；renderJBoltApiRet(ControllerJBoltApiRet)；renderJBoltApiRet(ControllerJBoltApiRetZ)；renderJBoltApiRet(ControllerRet)；renderJBoltApiRet(ControllerRetZ)；renderJBoltApiSuccess(Controller)；renderJBoltApiSuccess(ControllerStringObject)；renderJBoltApiSuccess(ControllerStringObjectZ)；renderJson(HttpServletResponseRet)；renderJsonConfirm(ControllerStringStringStringObjectZ)；renderJsonData(ControllerObject)；renderJsonData(ControllerObjectZ)；renderJsonFail(ControllerString)；renderJsonFail(ControllerStringObject)；renderJsonFail(ControllerStringObjectZ)；renderJsonSuccess(Controller)；renderJsonSuccess(ControllerString)；renderJsonSuccess(ControllerStringObject)；renderJsonSuccess(ControllerStringObjectZ)；renderPageFail(ControllerString)；renderPageSuccess(ControllerString)；renderPjaxFail(ControllerString)；renderPjaxSuccess(ControllerString)；renderSuccess(ControllerStringObject)；renderSuccess(ControllerStringObjectZ)；renderSystemLockedPage(Controller)；renderValidatorError(Controller)；renderWxaPayResult(ControllerStringString)；renderWxaPayResultFail(ControllerString)；renderWxaPayResultSuccess(ControllerString)；setApiJwts(ControllerStringStringZ)；setDateRange(ControllerJBoltDateRange)；setDateRange(ControllerJBoltDateRangeString)；setDateRange(ControllerString)；setDateRange(ControllerStringString)；setDateRange(ControllerStringStringString)；setMsg(ControllerString)；toAdminLogin(Controller)；toForcedOfflinePage(Controller)；toTerminalOfflinePage(Controller)

### JBoltCurrentOfModuleKit (class) extends Object

方法：<init>()；clear()；getJboltOfModule()；getJboltOfModuleInfo()；getJboltOfModuleLink()；getJboltOfModuleLinkToInt()；getJboltOfModuleLinkToLong()；isNotPlatformInnerAdminCall()；isPlatformInnerAdminCall()；setJboltOfModuleInfo(JBoltOfModuleInfo)

### JBoltHandlerKit (class) extends Object

方法：redirect(StringHttpServletRequestHttpServletResponse[Z)；redirect301(StringHttpServletRequestHttpServletResponse[Z)；renderError404(HttpServletRequestHttpServletResponse[Z)；renderError404(StringHttpServletRequestHttpServletResponse[Z)；renderError500(HttpServletRequestHttpServletResponse[Z)；renderError500(StringHttpServletRequestHttpServletResponse[Z)；renderFail(StringHttpServletRequestHttpServletResponse[Z)；renderFail(StringObjectHttpServletRequestHttpServletResponse[Z)；renderH5PageFail(HttpServletRequestHttpServletResponseString)；renderIframeFail(HttpServletRequestHttpServletResponseString)；renderJBoltApiFail(HttpServletRequestHttpServletResponseStringObject)；renderJBoltApiRet(HttpServletRequestHttpServletResponseJBoltApiRet)；renderJson(HttpServletRequestHttpServletResponseObject)；renderJsonFail(HttpServletRequestHttpServletResponseStringObject)；renderPageFail(HttpServletRequestHttpServletResponseString)；renderPjaxFail(HttpServletRequestHttpServletResponseString)；renderView(HttpServletRequestHttpServletResponseString)

### JBoltHttpSchemeKit (class) extends Object

方法：<init>()；get()；getFull()；remove()；set(String)

### JBoltSaasTenant (class) extends Object implements Serializable, Cloneable

方法：createById(Object)；createBySn(String)；getErrorMsg()；getId()；getName()；getSn()；isAccessible()；isSelfRequest()；setAccessible(Z)；setErrorMsg(String)；setId(Object)；setName(String)；setSelfRequest(Z)；setSn(String)

### JBoltSaasTenantAccessibleProcessor (interface) extends Object

方法：process(Object)

### JBoltSaasTenantFunction (interface) extends Object

方法：call()

### JBoltSaasTenantGetAllProcessor (interface) extends Object

方法：get()

### JBoltSaasTenantIdToNameProcessor (interface) extends Object

方法：process(Object)

### JBoltSaasTenantIdToSnProcessor (interface) extends Object

方法：process(Object)

### JBoltSaasTenantKit (class) extends Object

方法：<init>()；call(JBoltSaasTenantFunction)；call(Supplier)；clear()；get()；getAccessibleSaasTenants()；getAllSaasTenants()；getErrorMsg(String)；getId()；getIdAs()；getIdToInt()；getIdToLong()；getIdToStr()；getName()；getSn()；isAccessible()；isOk()；isSelfRequest()；processAccessible(Object)；processIdBySn(String)；processNameById(Object)；processNameBySn(String)；processSnById(Object)；self(String)；setAccessibleProcessor(JBoltSaasTenantAccessibleProcessor)；setGetAllTenantsProcessor(JBoltSaasTenantGetAllProcessor)；setIdToNameProcessor(JBoltSaasTenantIdToNameProcessor)；setIdToSnProcessor(JBoltSaasTenantIdToSnProcessor)；setSnToIdProcessor(JBoltSaasTenantSnToIdProcessor)；setSnToNameProcessor(JBoltSaasTenantSnToNameProcessor)；useById(Object)；useBySn(String)

### JBoltSaasTenantSnToIdProcessor (interface) extends Object

方法：process(String)

### JBoltSaasTenantSnToNameProcessor (interface) extends Object

方法：process(String)

### JBoltSnowflakeKit (class) extends Object

方法：main([String)；nextId()；nextIdStr()

常量：me

### JBoltUserKit (class) extends Object

方法：<init>()；clear()；getOnlineUser()；getUser()；getUserAvatar()；getUserDeptId()；getUserDeptName()；getUserDeptSn()；getUserId()；getUserName()；getUserOfModule()；getUserOfMoudleLink()；getUserRoleIds()；getUserRoleSnList()；getUserRoleSns()；getUserSessionId()；getUserUserName()；isEnable()；isPlatformInnerAdminUser()；isSystemAdmin()；removeScreenLocked()；removeUserId()；removeUserSessionId()；setScreenIsLocked()；setUserId(Long)；setUserSessionId(String)；userScreenIsLocked()

## cn.jbolt.core.listener

### JBoltSessionListener (class) extends Object implements SessionListener

方法：<init>()；attributeAdded(SessionStringObject)；attributeRemoved(SessionStringObject)；attributeUpdated(SessionStringObjectObject)；sessionCreated(SessionHttpServerExchange)；sessionDestroyed(SessionHttpServerExchangeSessionListener$SessionDestroyedReason)；sessionIdChanged(SessionString)

## cn.jbolt.core.map

### BaiduMapApi (class) extends Object

方法：<init>()；ipToAddress(String)；main([String)

## cn.jbolt.core.model

### Application (class) extends BaseApplication

方法：<init>()；deleteExtraCache(String)；getTypeName()

### DatasourceFilter (class) extends BaseDatasourceFilter

方法：<init>()

### Dept (class) extends BaseDept

方法：<init>()；getTypeName()

### Dictionary (class) extends BaseDictionary

方法：<init>()；_isCancelSystemDictionaryDeleteEnable()；cancelSystemDictionaryDeleteEnable()；getInnerCustom()

### DictionaryType (class) extends BaseDictionaryType

方法：<init>()

### GlobalConfig (class) extends BaseGlobalConfig

方法：<init>()；getTypeName()

常量：TYPE_STRING、TYPE_INT、TYPE_DECIMAL、TYPE_BOOLEAN、CAPTCHA_TYPE_DEFAULT、CAPTCHA_TYPE_GIF、CAPTCHA_TYPE_BUBBLE_PNG、CAPTCHA_TYPE_NUMBER_CALC、CAPTCHA_TYPE_NUMBER_CALC_CN

### GlobalConfigType (class) extends BaseGlobalConfigType

方法：<init>()

### JboltFile (class) extends BaseJboltFile

方法：<init>()；setLocalUrl(String)

常量：FILE_TYPE_IMAGE、FILE_TYPE_VEDIO、FILE_TYPE_AUDIO、FILE_TYPE_OFFICE、FILE_TYPE_ATTACHMENT

### LoginLog (class) extends BaseLoginLog

方法：<init>()；getLoginStateName()

### OnlineUser (class) extends BaseOnlineUser

方法：<init>()；getIsExpiration()；getStateName()；getUserUsername()；isForcedOffline()；isOffline()；isOnlineOk()；isTerminalOffline()

### Permission (class) extends BasePermission

方法：<init>()；getOfModuleName()；getTopnavId()

常量：LEVEL_1、OPEN_TYPE_DEFAULT、OPEN_TYPE_IFRAME、OPEN_TYPE_DIALOG、OPEN_TYPE_JBOLT_LAYER

### Post (class) extends BasePost

方法：<init>()；getTypeName()

### RemoteLoginLog (class) extends BaseRemoteLoginLog

方法：<init>()

### Role (class) extends BaseRole

方法：<init>()

### RolePermission (class) extends BaseRolePermission

方法：<init>()

### SensitiveWord (class) extends BaseSensitiveWord

方法：<init>()

### SystemLog (class) extends BaseSystemLog

方法：<init>()；getTargetTypeName()；getTypeName()

### Topnav (class) extends BaseTopnav

方法：<init>()

### TopnavMenu (class) extends BaseTopnavMenu

方法：<init>()

### User (class) extends BaseUser

方法：<init>()；getDeptSnAndName()；getUserPosts()；getUserRoles()；setDeptSnAndName(String)；setUserPosts(List)；setUserRoles(List)

常量：SEX_NONE、SEX_MALE、SEX_FEMALE

### UserConfig (class) extends BaseUserConfig

方法：<init>()

### UserConfigType (interface) extends Object

常量：TYPE_STRING、TYPE_INT、TYPE_DECIMAL、TYPE_BOOLEAN

### WechatConfig (class) extends BaseWechatConfig

方法：<init>()

常量：TYPE_BASE、TYPE_PAY、TYPE_EXTRA

### WechatMpinfo (class) extends BaseWechatMpinfo

方法：<init>()；getLinkAppName()；getSubjectTypeName()；getTypeName()

## cn.jbolt.core.model.base

### BaseApplication (class) extends JBoltBaseModel

方法：<init>()；getAppId()；getAppSecret()；getBriefInfo()；getCreateTime()；getEnable()；getId()；getIsInner()；getLinkTargetId()；getName()；getNeedCheckSign()；getType()；getUpdateTime()；getUpdateUserId()；getUserId()；setAppId(String)；setAppSecret(String)；setBriefInfo(String)；setCreateTime(Date)；setEnable(Boolean)；setId(Long)；setIsInner(Boolean)；setLinkTargetId(Long)；setName(String)；setNeedCheckSign(Boolean)；setType(Integer)；setUpdateTime(Date)；setUpdateUserId(Long)；setUserId(Long)

### BaseDatasourceFilter (class) extends JBoltBaseModel

方法：<init>()；getConfigName()；getCreateTime()；getCreateUserId()；getId()；getName()；getTableNameContains()；getTableNameFilter()；getTableNamePatterns()；getUpdateTime()；getUpdateUserId()；setConfigName(String)；setCreateTime(Date)；setCreateUserId(Long)；setId(Long)；setName(String)；setTableNameContains(String)；setTableNameFilter(String)；setTableNamePatterns(String)；setUpdateTime(Date)；setUpdateUserId(Long)

### BaseDept (class) extends JBoltBaseModel

方法：<init>()；getAddress()；getCreateTime()；getDeptPath()；getEmail()；getEnable()；getFullName()；getId()；getLeader()；getName()；getPhone()；getPid()；getRemark()；getSn()；getSortRank()；getType()；getUpdateTime()；getZipcode()；setAddress(String)；setCreateTime(Date)；setDeptPath(String)；setEmail(String)；setEnable(Boolean)；setFullName(String)；setId(Long)；setLeader(String)；setName(String)；setPhone(String)；setPid(Long)；setRemark(String)；setSn(String)；setSortRank(Integer)；setType(String)；setUpdateTime(Date)；setZipcode(String)

### BaseDictionary (class) extends JBoltBaseModel

方法：<init>()；getEnable()；getId()；getIsBuildIn()；getName()；getPid()；getSn()；getSortRank()；getTypeId()；getTypeKey()；setEnable(Boolean)；setId(Long)；setIsBuildIn(Boolean)；setName(String)；setPid(Long)；setSn(String)；setSortRank(Integer)；setTypeId(Long)；setTypeKey(String)

常量：ID、NAME、TYPE_ID、PID、SORT_RANK、SN、TYPE_KEY、ENABLE、IS_BUILD_IN

### BaseDictionaryType (class) extends JBoltBaseModel

方法：<init>()；getEnable()；getId()；getIsBuildIn()；getModeLevel()；getName()；getTypeKey()；setEnable(Boolean)；setId(Long)；setIsBuildIn(Boolean)；setModeLevel(Integer)；setName(String)；setTypeKey(String)

常量：ID、NAME、MODE_LEVEL、TYPE_KEY、ENABLE、IS_BUILD_IN

### BaseGlobalConfig (class) extends JBoltBaseModel

方法：<init>()；getBuiltIn()；getConfigKey()；getConfigValue()；getCreateTime()；getId()；getName()；getRemark()；getTypeId()；getTypeKey()；getUpdateTime()；getUpdateUserId()；getUserId()；getValueType()；setBuiltIn(Boolean)；setConfigKey(String)；setConfigValue(String)；setCreateTime(Date)；setId(Long)；setName(String)；setRemark(String)；setTypeId(Long)；setTypeKey(String)；setUpdateTime(Date)；setUpdateUserId(Long)；setUserId(Long)；setValueType(String)

### BaseGlobalConfigType (class) extends JBoltBaseModel

方法：<init>()；getBuiltIn()；getId()；getName()；getSortRank()；getTypeKey()；setBuiltIn(Boolean)；setId(Long)；setName(String)；setSortRank(Integer)；setTypeKey(String)

### BaseJboltFile (class) extends JBoltBaseModel

方法：<init>()；getCdnUrl()；getCreateTime()；getFileName()；getFileSize()；getFileSuffix()；getFileType()；getId()；getLocalPath()；getLocalUrl()；getUserId()；setCdnUrl(String)；setCreateTime(Date)；setFileName(String)；setFileSize(Integer)；setFileSuffix(String)；setFileType(Integer)；setId(Long)；setLocalPath(String)；setLocalUrl(String)；setUserId(Long)

### BaseLoginLog (class) extends JBoltBaseModel

方法：<init>()；getBrowserEngineName()；getBrowserEngineVersion()；getBrowserName()；getBrowserVersion()；getCreateTime()；getId()；getIsBrowser()；getIsMobile()；getIsRemoteLogin()；getLoginAddress()；getLoginAddressLatitude()；getLoginAddressLongitude()；getLoginCity()；getLoginCityCode()；getLoginCountry()；getLoginIp()；getLoginProvince()；getLoginState()；getLoginTime()；getOsName()；getOsPlatformName()；getUserId()；getUsername()；setBrowserEngineName(String)；setBrowserEngineVersion(String)；setBrowserName(String)；setBrowserVersion(String)；setCreateTime(Date)；setId(Long)；setIsBrowser(Boolean)；setIsMobile(Boolean)；setIsRemoteLogin(Boolean)；setLoginAddress(String)；setLoginAddressLatitude(String)；setLoginAddressLongitude(String)；setLoginCity(String)；setLoginCityCode(String)；setLoginCountry(String)；setLoginIp(String)；setLoginProvince(String)；setLoginState(Integer)；setLoginTime(Date)；setOsName(String)；setOsPlatformName(String)；setUserId(Long)；setUsername(String)

### BaseOnlineUser (class) extends JBoltBaseModel

方法：<init>()；getExpirationTime()；getId()；getLoginLogId()；getLoginTime()；getOfflineTime()；getOnlineState()；getScreenLocked()；getSessionId()；getUpdateTime()；getUserId()；setExpirationTime(Date)；setId(Long)；setLoginLogId(Long)；setLoginTime(Date)；setOfflineTime(Date)；setOnlineState(Integer)；setScreenLocked(Boolean)；setSessionId(String)；setUpdateTime(Date)；setUserId(Long)

### BasePermission (class) extends JBoltBaseModel

方法：<init>()；getIcons()；getId()；getIsMenu()；getIsSystemAdminDefault()；getIsTargetBlank()；getOfModule()；getOfModuleLink()；getOpenOption()；getOpenType()；getPermissionKey()；getPermissionLevel()；getPid()；getSortRank()；getTitle()；getUrl()；setIcons(String)；setId(Long)；setIsMenu(Boolean)；setIsSystemAdminDefault(Boolean)；setIsTargetBlank(Boolean)；setOfModule(Integer)；setOfModuleLink(String)；setOpenOption(String)；setOpenType(Integer)；setPermissionKey(String)；setPermissionLevel(Integer)；setPid(Long)；setSortRank(Integer)；setTitle(String)；setUrl(String)

常量：ID、PID、TITLE、URL、ICONS、SORT_RANK、PERMISSION_LEVEL、PERMISSION_KEY、IS_MENU、IS_TARGET_BLANK、IS_SYSTEM_ADMIN_DEFAULT、OPEN_TYPE、OPEN_OPTION、OF_MODULE、OF_MODULE_LINK

### BasePost (class) extends JBoltBaseModel

方法：<init>()；getEnable()；getId()；getName()；getRemark()；getSn()；getSortRank()；getType()；setEnable(Boolean)；setId(Long)；setName(String)；setRemark(String)；setSn(String)；setSortRank(Integer)；setType(String)

### BaseRemoteLoginLog (class) extends JBoltBaseModel

方法：<init>()；getCreateTime()；getId()；getIsNew()；getLastLoginCity()；getLastLoginCountry()；getLastLoginIp()；getLastLoginProvince()；getLastLoginTime()；getLoginCity()；getLoginCountry()；getLoginIp()；getLoginProvince()；getLoginTime()；getUserId()；getUsername()；setCreateTime(Date)；setId(Long)；setIsNew(Boolean)；setLastLoginCity(String)；setLastLoginCountry(String)；setLastLoginIp(String)；setLastLoginProvince(String)；setLastLoginTime(Date)；setLoginCity(String)；setLoginCountry(String)；setLoginIp(String)；setLoginProvince(String)；setLoginTime(Date)；setUserId(Long)；setUsername(String)

### BaseRole (class) extends JBoltBaseModel

方法：<init>()；getId()；getName()；getPid()；getSn()；setId(Long)；setName(String)；setPid(Long)；setSn(String)

### BaseRolePermission (class) extends JBoltBaseModel

方法：<init>()；getId()；getPermissionId()；getRoleId()；setId(Long)；setPermissionId(Long)；setRoleId(Long)

### BaseSensitiveWord (class) extends JBoltBaseModel

方法：<init>()；getContent()；getEnable()；getId()；setContent(String)；setEnable(Boolean)；setId(Long)

### BaseSystemLog (class) extends JBoltBaseModel

方法：<init>()；getCreateTime()；getId()；getOpenType()；getTargetId()；getTargetType()；getTitle()；getType()；getUrl()；getUserId()；getUserName()；setCreateTime(Date)；setId(Long)；setOpenType(Integer)；setTargetId(Long)；setTargetType(Integer)；setTitle(String)；setType(Integer)；setUrl(String)；setUserId(Long)；setUserName(String)

### BaseTopnav (class) extends JBoltBaseModel

方法：<init>()；getEnable()；getIcon()；getId()；getName()；getSortRank()；setEnable(Boolean)；setIcon(String)；setId(Long)；setName(String)；setSortRank(Integer)

### BaseTopnavMenu (class) extends JBoltBaseModel

方法：<init>()；getId()；getPermissionId()；getTopnavId()；setId(Long)；setPermissionId(Long)；setTopnavId(Long)

### BaseUser (class) extends JBoltBaseModel

方法：<init>()；getAge()；getAvatar()；getCreateTime()；getCreateUserId()；getDeptId()；getDeptPath()；getEmail()；getEnable()；getId()；getIsRemoteLogin()；getIsSystemAdmin()；getLastPwdUpdateTime()；getLoginCity()；getLoginCountry()；getLoginIp()；getLoginProvince()；getLoginTime()；getName()；getOfModule()；getOfModuleLink()；getPassword()；getPhone()；getPinyin()；getPosts()；getPwdSalt()；getRoles()；getSex()；getSn()；getUpdateTime()；getUpdateUserId()；getUsername()；setAge(Integer)；setAvatar(String)；setCreateTime(Date)；setCreateUserId(Long)；setDeptId(Long)；setDeptPath(String)；setEmail(String)；setEnable(Boolean)；setId(Long)；setIsRemoteLogin(Boolean)；setIsSystemAdmin(Boolean)；setLastPwdUpdateTime(Date)；setLoginCity(String)；setLoginCountry(String)；setLoginIp(String)；setLoginProvince(String)；setLoginTime(Date)；setName(String)；setOfModule(Integer)；setOfModuleLink(String)；setPassword(String)；setPhone(String)；setPinyin(String)；setPosts(String)；setPwdSalt(String)；setRoles(String)；setSex(Integer)；setSn(String)；setUpdateTime(Date)；setUpdateUserId(Long)；setUsername(String)

常量：ID、SN、USERNAME、PASSWORD、NAME、AVATAR、EMAIL、CREATE_TIME、PHONE、ENABLE、SEX、AGE、PINYIN、ROLES、IS_SYSTEM_ADMIN、CREATE_USER_ID、PWD_SALT、LOGIN_IP、LOGIN_TIME、LOGIN_CITY、LOGIN_PROVINCE、LOGIN_COUNTRY、IS_REMOTE_LOGIN、DEPT_ID、DEPT_PATH、POSTS、UPDATE_TIME、UPDATE_USER_ID、LAST_PWD_UPDATE_TIME、OF_MODULE、OF_MODULE_LINK

### BaseUserConfig (class) extends JBoltBaseModel

方法：<init>()；getConfigKey()；getConfigValue()；getCreateTime()；getId()；getName()；getUpdateTime()；getUserId()；getValueType()；setConfigKey(String)；setConfigValue(String)；setCreateTime(Date)；setId(Long)；setName(String)；setUpdateTime(Date)；setUserId(Long)；setValueType(String)

### BaseWechatConfig (class) extends JBoltBaseModel

方法：<init>()；getConfigKey()；getConfigValue()；getId()；getMpId()；getName()；getType()；setConfigKey(String)；setConfigValue(String)；setId(Long)；setMpId(Long)；setName(String)；setType(Integer)

### BaseWechatMpinfo (class) extends JBoltBaseModel

方法：<init>()；getBriefInfo()；getCreateTime()；getEnable()；getId()；getIsAuthenticated()；getLinkAppId()；getLogo()；getName()；getQrcode()；getRemark()；getSubjectType()；getType()；getUpdateTime()；getUpdateUserId()；getUserId()；getWechatId()；setBriefInfo(String)；setCreateTime(Date)；setEnable(Boolean)；setId(Long)；setIsAuthenticated(Boolean)；setLinkAppId(Long)；setLogo(String)；setName(String)；setQrcode(String)；setRemark(String)；setSubjectType(Integer)；setType(Integer)；setUpdateTime(Date)；setUpdateUserId(Long)；setUserId(Long)；setWechatId(String)

### JBoltBaseModel (class) extends Model implements IBean

方法：<init>()；_getBatchUpdateKey()；_getCompositePrimaryKeys()；_getCurrentTenantTableName()；_getDataSourceConfigName()；_getDatabaseName()；_getDbType()；_getIdGenMode()；_getIdValue()；_getIdValues()；_getKeyBindColumnValue()；_getKeyValue()；_getOriginTableName()；_getPrimaryKey()；_getSchema()；_getTable()；_getTable(Z)；_getTableName()；_isAutoCache()；_isIdCacheEnable()；_isKeyCacheEnable()；_isSeparate()；addItem(Object)；afterSave()；afterUpdate(Z)；autoProcessDeleteTime()；autoProcessDeleteUserId()；autoProcessIdValue()；beforeSaveInBatchSave(Date)；beforeUpdate()；beforeUpdate(Date)；checkNeedAutoProcessCreateTime()；checkNeedAutoProcessCreateUserIdValue()；checkNeedAutoProcessDeleteUserIdValue()；checkNeedAutoProcessIdValue()；checkNeedAutoProcessUpdateTime()；checkNeedAutoProcessUpdateUserIdValue()；checkNeedBeforeSaveInBatchSave()；clearCache()；containsAttr(String)；delete()；deleteById(Object)；deleteByIds([Object)；deleteCacheById([Object)；deleteCacheByKey(ObjectObject)；deleteIdCache()；deleteKeyCache()；findById(Object)；findByIdLoadColumns(ObjectString)；findByIds([Object)；getBoolean(String)；getEachLevel()；getItems()；hasColumn(String)；hasColumnAll([String)；hasColumnAny([String)；loadCacheByKey(String)；loadCacheByKey(StringObject)；processEachLevelByParentLevel(Integer)；putEachLevel(Integer)；putItems(List)；save()；set(StringObject)；set(StringObjectZ)；setObjectCreateUserId(Object)；setObjectUpdateUserId(Object)；setObjectUserId(Object)；superFindById(Object)；superFindByIdLoadColumns(ObjectString)；update()

常量：COLUMN_CREATE_TIME、COLUMN_UPDATE_TIME、COLUMN_DELETE_TIME、COLUMN_USER_ID、COLUMN_CREATE_USER_ID、COLUMN_UPDATE_USER_ID、COLUMN_DELETE_USER_ID

### JBoltModelConfig (enum) extends Enum

方法：addAutoCache(Class)；addConfig(ClassStringString)；addConfigIfNotExist(Class)；addInnerSeparateModel(Class)；addInnerSeparateModels([Class)；addProjectSeparateModel(Class)；addProjectSeparateModelFromPackage(StringZ)；addProjectSeparateModelFromPackages(Z[String)；addProjectSeparateModelFromSaasConfigPackages(String)；addProjectSeparateModels([Class)；addTableBind(ClassStringString)；getCompositePrimaryKeys(Class)；getDataSourceConfigName(Class)；getDatabase(Class)；getDbType(Class)；getIdGenMode(Class)；getIdSequenceStr(Class)；getJBoltAutoCache(Class)；getModelAttrToColumnMap(Class)；getModelClassByTable(String)；getModelColumnToAttrMap(Class)；getPrimaryKey(Class)；getProjectSaasConfigSeparateModelSet()；getSchema(Class)；getTableBind(Class)；getTableName(Class)；getTableNameWithDatabase(Class)；isMainDataSource(Class)；isSeparate(Class)；modelAttrToColumn(ClassString)；modelColumnToAttr(ClassString)；processDatabaseAndTableName(ClassString)；processNameByDbType(StringString)；processTableName(Class)；valueOf(String)；values()

常量：me

## cn.jbolt.core.para

### IJBoltParaValidator (interface) extends Object

方法：hasNotOk([Object)；hasOk([Object)；isExcel(UploadFile)；isImage(String)；isImage(UploadFile)；isNull(Object)；isOk(Model)；isOk(Object)；isOk(Record)；isOk([Object)；notExcel(UploadFile)；notImage(String)；notImage(UploadFile)；notNull(Object)；notOk(Model)；notOk(Object)；notOk(Record)；notOk([Object)

### JBoltNoUrlPara (class) extends Object implements Interceptor

方法：<init>()；intercept(Invocation)

### JBoltPara (class) extends JSONObject

方法：<init>(Map)；<init>(String)；getBigDecimalArray(String)；getBigDecimalList(String)；getIntArray(String)；getIntList(String)；getJBoltDateRange(String)；getJBoltDateRange(StringString)；getLongArray(String)；getLongList(String)；getRawData()；getStringArray(String)；getStringList(String)；getStringToArray(String)；getStringToArray(StringString)；getStringToBigDecimalArray(String)；getStringToBigDecimalArray(StringString)；getStringToIntArray(String)；getStringToIntArray(StringString)；getStringToLongArray(String)；getStringToLongArray(StringString)

### JBoltParaGetter (class) extends ParaGetter

方法：<init>(StringString)；get(ActionController)

### JBoltParaValidator (class) extends Object

方法：<init>()；hasNotOk([Object)；hasOk([Object)；isExcel(File)；isExcel(UploadFile)；isImage(String)；isImage(UploadFile)；isNull(Object)；isOk(BigDecimal)；isOk(BigInteger)；isOk(Boolean)；isOk(Collection)；isOk(Date)；isOk(DateTime)；isOk(Double)；isOk(Enum)；isOk(Float)；isOk(Integer)；isOk(List)；isOk(Long)；isOk(Map)；isOk(Model)；isOk(Object)；isOk(Record)；isOk(Set)；isOk(String)；isOk([B)；isOk([Object)；isOk([Serializable)；notExcel(File)；notExcel(UploadFile)；notImage(String)；notImage(UploadFile)；notNull(Object)；notOk(BigDecimal)；notOk(BigInteger)；notOk(Boolean)；notOk(Collection)；notOk(Date)；notOk(DateTime)；notOk(Double)；notOk(Enum)；notOk(Float)；notOk(Integer)；notOk(List)；notOk(Long)；notOk(Map)；notOk(Model)；notOk(Object)；notOk(Record)；notOk(Set)；notOk(String)；notOk([B)；notOk([Integer)；notOk([Object)；notOk([Serializable)

### JBoltValidator (class) extends Validator

方法：<init>()

## cn.jbolt.core.para.jbolttablemenufilter

### JBoltTableMenuFilter (class) extends Object

方法：<init>()；getInclude()；getItems()；getKeywords()；getPageNumber()；getPageSize()；getPaging()；getReturnColumns()；getSortColumn()；getSortType()；setInclude(Z)；setItems(List)；setKeywords(String)；setPageNumber(I)；setPageSize(I)；setPaging(Z)；setReturnColumns(List)；setSortColumn(String)；setSortType(String)

### JBoltTableMenuFilterGetter (class) extends ParaGetter

方法：<init>(StringString)；get(ActionController)

### JBoltTableMenuFilterItem (class) extends Object

方法：<init>()；getColumn()；getComparison()；getType()；getValue()；setColumn(String)；setComparison(String)；setType(I)；setValue(Object)

常量：TYPE_STRING、TYPE_INT、TYPE_BIGDECIMAL、TYPE_DATE、TYPE_DATETIME、TYPE_TIME、TYPE_BOOLEAN

## cn.jbolt.core.permission

### CheckPermission (interface) extends Object implements Annotation

方法：checkAll()；value()

### JBoltAdminAuthInterceptor (class) extends Object implements Interceptor

方法：<init>()；intercept(Invocation)

### JBoltApiAuthInterceptor (class) extends Object implements Interceptor

方法：<init>()；intercept(Invocation)

### JBoltAuthInterceptorKit (class) extends Object

方法：<init>()；getPermissionKeys(ControllerMethod)；intercept(Invocation)；isCheckAll(ControllerMethod)

### JBoltUserAuthKit (class) extends Object

方法：<init>()；hasPermission(LongZ[String)；hasPermission(Long[String)；hasRole(LongString)；hasRole(LongZString)；hasRole(LongZ[Long)；hasRole(Long[Long)；hasRoleSn(LongZ[String)；hasRoleSn(Long[String)

### MustCheckSaasTenantPermission (interface) extends Object implements Annotation

### OnlySaasPlatform (interface) extends Object implements Annotation

### UnCheck (interface) extends Object implements Annotation

### UnCheckIfSystemAdmin (interface) extends Object implements Annotation

## cn.jbolt.core.plugin

### JBoltActiveRecordPlugin (class) extends ActiveRecordPlugin

方法：<init>(IDataSourceProvider)；<init>(StringStringIDataSourceProvider)；<init>(StringStringStringIDataSourceProvider)；addAutoBindPackage(String)；processAutoBindTable()；start()

## cn.jbolt.core.poi.excel

### JBoltCellStyle (class) extends Object

方法：create(IICellStyle)；create(IStringCellStyle)；getCellStyle()；getCol()；getRow()；setCellStyle(CellStyle)；setCol(I)；setRow(I)

### JBoltExcel (class) extends Object

方法：addSheet(JBoltExcelSheet)；create()；createByTpl(File)；createByTpl(String)；from(File)；from(String)；getFileName()；getFromFile()；getOptMode()；getSheet(I)；getSheet(String)；getSheetSize()；getSheets()；isBig()；isExport()；isExportByTpl()；isImport()；isXlsx()；setBig(Z)；setFileName(String)；setSheets(List)；setSheets([JBoltExcelSheet)；setXlsx(Z)；toByteArray()；toFile(String)；useTpl(File)；useTpl(String)

常量：OPT_MODE_IMPORT、OPT_MODE_EXPORT、OPT_MODE_EXPORT_BY_TPL、OPT_MODE_EXPORT_BY_DYNAMIC_TPL

### JBoltExcelCellStyleHandler (interface) extends Object

方法：process(ExcelWriterJBoltExcelSheetIJBoltExcelDataI)

### JBoltExcelData (class) extends HashMap

方法：<init>(Map)；change(JBoltExcelDataChangeHandlerI)；change(StringObject)；changeBooleanToStr(StringStringString)；changeDateToStr(StringString)；changeStrToBoolean(StringKv)；changeStrToBoolean(StringString)；changeWithKey(StringStringObject)；changeWithKey(StringStringSupplier)；fromKv(Kv)；fromMap(Map)；fromModel(Model)；fromOkv(Okv)；fromRecord(Record)；getAs(Object)；getBoolean(Object)；getBooleanByIndex(I)；getByIndexAs(I)；getDate(Object)；getInt(Object)；getIntByIndex(I)；getLong(Object)；getLongByIndex(I)；getNumber(Object)；getNumberByIndex(I)；getObjectValueByIndex(I)；getStr(Object)；getStrByIndex(I)；isFalse(Object)；isNull(Object)；isTrue(Object)；notNull(Object)

### JBoltExcelDataChangeHandler (interface) extends Object

方法：change(JBoltExcelDataI)

### JBoltExcelDataFilter (interface) extends Object

方法：accept(JBoltExcelData)

### JBoltExcelDataMapper (class) extends Object

方法：create(List)；get(II)；get(String)；get(StringI)；getAs(II)；getAs(String)；getAs(StringI)；getBoolean(II)；getBoolean(String)；getBoolean(StringI)；getDatas()；getInt(II)；getInt(String)；getInt(StringI)；getLong(II)；getLong(String)；getLong(StringI)；getNumber(II)；getNumber(String)；getNumber(StringI)；getRowData(I)；getStr(II)；getStr(String)；getStr(StringI)；hasDatas()；setDatas(List)

### JBoltExcelHeader (class) extends Object

方法：<init>(IString)；<init>(IStringString)；<init>(String)；<init>(StringString)；<init>(StringStringI)；<init>(StringStringII)；col(I)；create(IStringString)；create(String)；create(StringI)；create(StringII)；create(StringString)；create(StringStringI)；create(StringStringII)；getCol()；getColumn()；getContent()；getWidth()；setCol(I)；setColumn(String)；setContent(String)；setWidth(I)；toString()；width(I)

### JBoltExcelMerge (class) extends Object

方法：<init>(I)；<init>(II)；<init>(III)；<init>(IIII)；<init>(IIIIString)；<init>(IIIIStringZ)；<init>(IIIIString[BZ)；<init>(IIIIZ)；<init>(IIII[BZ)；<init>(IIIString)；<init>(IIIStringZ)；<init>(IIIZ)；<init>(IIString)；<init>(IString)；create(I)；create(II)；create(III)；create(IIII)；create(IIIIString)；create(IIIIStringZ)；create(IIIIZ)；create(IIIString)；create(IIIStringZ)；create(IIIZ)；create(IIString)；create(IString)；create(String)；create(StringI)；create(StringII)；create(StringIIString)；create(StringIIStringZ)；create(StringIIZ)；create(StringIString)；create(StringString)；create(StringStringII)；create(StringStringIIString)；create(StringStringIIStringZ)；create(StringStringIIZ)；create(StringStringII[B)；getColumn()；getContent()；getFirstColumn()；getFirstRow()；getLastColumn()；getLastRow()；getPic()；isPicData()；isSetHeaderStyle()；setColumn(String)；setContent(String)；setFirstColumn(I)；setFirstRow(I)；setLastColumn(I)；setLastRow(I)；setPic([B)；setSetHeaderStyle(Z)

### JBoltExcelPositionChange (class) extends Object

方法：create(IIII)；create(IIIII)；create(IIIIII)；getColIncrease()；getOriginFirstCol()；getOriginFirstRow()；getOriginLastCol()；getOriginLastRow()；getRowIncrease()；increaseCol(I)；increaseRow(I)；isNeedChange()；setColIncrease(I)；setOriginFirstCol(I)；setOriginFirstRow(I)；setOriginLastCol(I)；setOriginLastRow(I)；setRowIncrease(I)

### JBoltExcelPositionData (class) extends Object

方法：create(IIObject)；create(IIObjectCellStyle)；create(IIObjectCellStyleZ)；create(IIObjectZ)；create(IStringObject)；create(IStringObjectCellStyle)；create(IStringObjectCellStyleZ)；create(IStringObjectZ)；create(StringIObject)；create(StringIObjectCellStyle)；create(StringIObjectCellStyleZ)；create(StringIObjectZ)；create(StringObject)；create(StringObjectCellStyle)；create(StringObjectCellStyleZ)；create(StringObjectZ)；create(StringStringObject)；getCellStyle()；getCol()；getLastCol()；getLastRow()；getRow()；getValue()；isValueIsFormula()；setCellStyle(CellStyle)；setCol(I)；setLastCol(I)；setLastRow(I)；setRow(I)；setValue(Object)；setValueIsFormula(Z)

### JBoltExcelSheet (class) extends Object

方法：addCellFormluas(List)；addCellFormluas([JBoltExcelPositionData)；addCellStyle(JBoltCellStyle)；addCellStyles(List)；addHeaders(List)；addHeaders([JBoltExcelHeader)；addMerges(List)；addMerges([JBoltExcelMerge)；addPositionChanges(List)；addPositionChanges([JBoltExcelPositionChange)；addPositionDatas(List)；addPositionDatas([JBoltExcelPositionData)；create()；create(I)；create(String)；getCellFormluas()；getCellStyleHandler()；getCellStyles()；getDataChangeHandler()；getDataFilter()；getDataStartRow()；getDatas()；getExcel()；getHeaderColumns()；getHeaderContents()；getHeaderStartRow()；getHeaders()；getMerges()；getName()；getPositionChanges()；getPositionDatas()；getSheetIndex()；getStyleHandler()；getWaterMark()；hasCellFormluas()；hasCellStyles()；hasDatas()；hasHeaders()；hasMerges()；hasPositionChanges()；hasPositionDatas()；isForceFormulaRecalculation()；isMergeAsHeader()；isProcessHeaderAlias()；setCellFormluas(List)；setCellStyleHandler(JBoltExcelCellStyleHandler)；setCellStyles(List)；setDataChangeHandler(JBoltExcelDataChangeHandler)；setDataFilter(JBoltExcelDataFilter)；setDataStartRow(I)；setDatas(List)；setExcel(JBoltExcel)；setForceFormulaRecalculation(Z)；setHeaderStartRow(I)；setHeaders(IList)；setHeaders(IZList)；setHeaders(IZ[JBoltExcelHeader)；setHeaders(I[JBoltExcelHeader)；setHeaders(List)；setHeaders(ZList)；setHeaders(Z[JBoltExcelHeader)；setHeaders([JBoltExcelHeader)；setKvDatas(IList)；setMapDatas(IArrayList)；setMergeAsHeader(Z)；setMerges(List)；setMerges([JBoltExcelMerge)；setModelDatas(IList)；setName(String)；setOkvDatas(IList)；setPositionChanges(List)；setPositionDatas(List)；setProcessHeaderAlias(Z)；setRecordDatas(IList)；setSheetIndex(I)；setStyleHandler(JBoltExcelStyleHandler)；setWaterMark(JBoltWaterMark)

### JBoltExcelStyleHandler (interface) extends Object

方法：process(ExcelWriter)

### JBoltExcelUtil (class) extends Object

方法：<init>()；addXlsxWaterMark(SheetJBoltWaterMark)；colNumToStr(I)；colStrToNum(String)；colStrToNum(StringI)；createBarcode128PicByZxing(StringIII)；createBarcode39PicByZxing(StringIII)；getExcelBytes(JBoltExcel)；positionStrSplit(String)；processColumnWidth(ExcelWriterJBoltExcelSheet)；readDataMapper(JBoltExcelIStringBuilder)；readDataMapper(JBoltExcelJBoltExcelSheet)；readDataMapper(JBoltExcelStringStringBuilder)；readJBoltExcelDatas(JBoltExcelIZStringBuilder)；readJBoltExcelDatas(JBoltExcelJBoltExcelSheetZStringBuilder)；readJBoltExcelDatas(JBoltExcelStringZZStringBuilder)；readModels(JBoltExcelClassStringBuilder)；readModels(JBoltExcelIClassStringBuilder)；readModels(JBoltExcelStringClassStringBuilder)；readRecords(JBoltExcelIZStringBuilder)；readRecords(JBoltExcelJBoltExcelSheetZStringBuilder)；readRecords(JBoltExcelStringZStringBuilder)；saveFile(JBoltExcelString)；toModel(ClassJBoltExcelDataZStringBuilder)；toRecord(JBoltExcelData)；toRecord(Map)

### JBoltExcelWaterRemarkUtil (class) extends Object

方法：<init>()；putWaterRemarkToExcel(WorkbookSheetStringIIIIIIII)

### JBoltWaterMark (class) extends Object

方法：create(String)；create(StringDate)；create(StringDateII)；create(StringDateString)；create(StringDateStringFont)；create(StringDateStringII)；create(StringDateStringString)；create(StringDateStringStringFont)；create(StringDateStringStringFontII)；create(StringII)；create(StringString)；create(StringStringFont)；create(StringStringFontII)；create(StringStringII)；getColor()；getDate()；getDateFormat()；getFont()；getHeight()；getText()；getWidth()；setColor(String)；setDate(Date)；setDateFormat(String)；setFont(Font)；setHeight(I)；setText(String)；setWidth(I)

### JBoltWaterMarkUtil (class) extends Object

方法：<init>()；createJBoltWatermarkImage(JBoltWaterMark)

## cn.jbolt.core.poi.word

### JBoltWord (class) extends Object

方法：createByTpl(File)；createByTpl(String)；getDatas()；getFileName()；getTplFile()；setDatas(Map)；setFileName(String)；toByteArray()；toFile(String)；useTpl(File)；useTpl(String)

### JBoltWordUtil (class) extends Object

方法：getWordBytes(JBoltWord)；saveFile(JBoltWordString)

常量：me

## cn.jbolt.core.render

### JBoltByteFileType (enum) extends Enum

方法：valueOf(String)；values()

常量：XLSX、XLS、DOC、DOCX、TXT、PDF、HTML、JSON、JPG

### JBoltByteRender (class) extends Render

方法：<init>()；create()；create([B)；encodeFileName()；render()；setFileByte([B)；setFileName(String)；setFileType(JBoltByteFileType)；setRenderType(JBoltByteRenderType)；toString()

### JBoltByteRenderType (enum) extends Enum

方法：valueOf(String)；values()

常量：DOWNLOAD、VIEW

### JBoltErrorRender (class) extends Render

方法：<init>(I)；getErrorCode()；render()；set401ErrorMsg(String)；set403ErrorMsg(String)；set404ErrorMsg(String)；set500ErrorMsg(String)

### JBoltRenderFactory (class) extends RenderFactory

方法：<init>()；getErrorRender(I)

## cn.jbolt.core.safe

### HttpServletRequestWrapper (class) extends HttpServletRequestWrapper

方法：<init>(HttpServletRequest)；getHeader(String)；getParameter(String)；getParameterMap()；getParameterValues(String)；main([String)

### XssHandler (class) extends Handler

方法：<init>()；handle(StringHttpServletRequestHttpServletResponse[Z)；unlimited([String)

### XssUtil (class) extends Object

方法：<init>()；main([String)；processExcelString(Object)；processXSS(String)

## cn.jbolt.core.sensitiveword

### JBoltSensitiveWordUtil (class) extends Object

方法：<init>()；containsAny(String)；findAll(String)；findAllWords(String)；findFirst(String)；getCount()；init()；reload()；replace(String)；replace(StringC)

常量：me

## cn.jbolt.core.server

### JBoltServer (class) extends UndertowServer

方法：getProjectName()；getProjectVersion()；printProjectNameLogo()；start()；stop()

## cn.jbolt.core.service

### JBoltApplicationService (class) extends JBoltBaseService

方法：<init>()；checkAndInitPcInnerPlatformApplication()；genAppId()；genAppSecret()；getApiApplicationByAppId(String)；getByAppId(String)

### JBoltDeptService (class) extends JBoltBaseService

方法：<init>()；checkCanDelete(DeptKv)；checkCanDelete(JBoltBaseModelKv)；checkInUse(DeptKv)；checkInUse(JBoltBaseModelKv)；delete(Long)；down(Long)；getAllJsTreeDatas(LongI)；getAllList()；getEnableJsTree(LongI)；getEnableList()；getTreeDatas(ZZ)；getTreeTableDatas()；initRank()；move(LongLongInteger)；processAllDeptPath()；processSonDeptIds(Long)；save(Dept)；toggleEnable(Long)；up(Long)；update(Dept)

### JBoltDictionaryService (class) extends JBoltBaseService

方法：<init>()；changeEnableByTypeId(LongZ)；changeSonsEnableByTypeId(LongLongZ)；checkAndInit()；checkCanDelete(DictionaryKv)；checkCanDelete(JBoltBaseModelKv)；checkCanToggle(DictionaryStringKv)；checkCanToggle(JBoltBaseModelStringKv)；checkTypeInUse(Long)；clearByTypeId(Long)；deleteDictionaryById(LongZ)；doUp(Long)；down(Long)；existName(LongStringLongLong)；existSn(LongStringLong)；getCacheNames(String)；getCountByTypeId(Long)；getDictionaryMapByTypeKey(StringString)；getDictionaryMapByTypeKey(StringStringString)；getListByTypeId(LongBoolean)；getListByTypeId(LongStringBoolean)；getListByTypeId(LongStringZBoolean)；getListByTypeId(LongStringZBoolean[Long)；getListByTypeId(LongZBoolean)；getListByTypeIdWithoutSelf(LongLongBoolean)；getListByTypeKey(String)；getListByTypeKey(StringBoolean)；getListByTypeKey(StringStringBoolean)；getListByTypeKey(StringStringZBoolean)；getListByTypeKey(StringStringZBoolean[Long)；getListByTypeKey(StringZBoolean)；getOptionListByTypeId(LongZ)；getOptionListByTypeKey(StringBoolean)；getRootOptionListByTypeKey(StringBoolean)；getSonListByTypeId(LongLongBoolean)；getSonOptionListByTypeKey(StringLongBoolean)；getSonOptionListByTypeKeyAndPsn(StringStringBoolean)；initRank(Long)；save(Dictionary)；save(DictionaryZ)；update(Dictionary)；updateTypeKey(LongString)

### JBoltDictionaryTypeService (class) extends JBoltBaseService

方法：<init>()；checkCanDelete(DictionaryTypeKv)；checkCanDelete(JBoltBaseModelKv)；checkCanToggle(DictionaryTypeStringKv)；checkCanToggle(JBoltBaseModelStringKv)；checkInUse(DictionaryTypeKv)；checkInUse(JBoltBaseModelKv)；delete(Long)；findByTypeKey(String)；save(DictionaryType)；update(DictionaryType)

### JBoltFileService (class) extends JBoltBaseService

方法：<init>()；deleteFileById(ObjectZ)；deleteFileByLocalPath(StringZ)；deleteFileByLocalUrl(StringZ)；getListByIds(String)；paginateAdminData(II)；saveAttachmentFile(UploadFileString)；saveAudioFile(UploadFileString)；saveExcelFile(UploadFileString)；saveFile(UploadFileStringI)；saveImageFile(UploadFileString)；saveJBoltFile(FileStringI)；saveJBoltFile(UploadFileStringI)；saveVideoFile(UploadFileString)

### JBoltGlobalConfigService (class) extends JBoltBaseService

方法：<init>()；changeAssetsVersion()；checkAndInit()；checkAndInitConfig(String)；checkCanDelete(GlobalConfigKv)；checkCanDelete(JBoltBaseModelKv)；checkTypeInUse(LongString)；clearCacheByType(Long)；clearReset()；deleteCustomConfig(Long)；getAdminList(LongBooleanString)；getByConfigKey(String)；initTenantGlobalConfigs(StringStringString)；saveCustomConfig(GlobalConfig)；toggleBooleanValue(Long)；update(GlobalConfig)；updateCustomConfig(GlobalConfig)；updateJboltStyle(String)；updateTypeId(StringLong)；updateTypeKey(LongString)

### JBoltGlobalConfigTypeService (class) extends JBoltBaseService

方法：<init>()；checkAndInitType(JBoltEnumBean)；checkAndInitTypes()；checkCanDelete(GlobalConfigTypeKv)；checkCanDelete(JBoltBaseModelKv)；checkInUse(GlobalConfigTypeKv)；checkInUse(JBoltBaseModelKv)；delete(Long)；doDown(Long)；doUp(Long)；getAdminList()；initRank()；save(GlobalConfigType)；update(GlobalConfigType)

### JBoltLoginLogService (class) extends JBoltBaseService

方法：<init>()；updateIsRemoteLogin(LoginLogZ)

### JBoltLoginLogUtil (class) extends Object

方法：<init>()；createLoginLog(HttpServletRequest)

### JBoltOnlineUserService (class) extends JBoltBaseService

方法：<init>()；currentUserLogout()；deleteAllSaasTenantOfflineAndExpirationUser()；deleteBySessionId(String)；deleteOfflineAndExpirationUser()；forcedOffline(Long)；genNewSessionId()；genNewSessionIdRefreshToken(UserStringJ)；getByUserId(Long)；getSessionListByDeptId(Object)；getSessionListByPostId(Object)；getSessionListByRoleId(Object)；getSessionListByUserId(Long)；lockCurrentUserScreen()；paginateAdminDatas(IIStringInteger)；processAllSameUserOffline(Long)；processPcInnnerPlatformUserReLogin(HttpServletRequestUserStringI)；processUserLogin(ZUserLoginLog)；unlockCurrentUserScreen()

### JBoltPermissionService (class) extends JBoltBaseService

方法：<init>()；delPermissionById(LongZ)；down(Long)；getAdminPermissionsWithLevel(Long)；getAllPermissionsOptionsWithLevel()；getAllPermissionsWithLevel()；getByPermissionkey(String)；getIsMenuPermissionsByRoles(StringInteger)；getIsMenuSystemAdminDefaultListWithLevel()；getIsMenuSystemAdminDefaultPermissions()；getLevel1MenusJoinTopnavMenu()；getMenusByRoles(StringInteger)；getMenusByRolesWithSystemAdminDefault(String)；getParentPermissionsWithLevel(Long)；getPermissionsByRole(Long)；initRank()；initTenantPermissions(StringStringString)；save(Permission)；toggleSystemAdminDefault(Long)；up(Long)；update(Permission)

常量：PERMISSION_KEY_NONE

### JBoltPostService (class) extends JBoltBaseService

方法：<init>()；delete(Long)；down(Long)；getEnableList()；getEnableOptionsList()；initRank()；paginateAdminDatas(IIString)；save(Post)；toggleEnable(Long)；up(Long)；update(Post)

### JBoltProjectSystemLogProcessor (interface) extends Object

方法：processSystemLogUrl(IObject)；targetTypeToName(I)；typeToName(I)

### JBoltRemoteLoginLogService (class) extends JBoltBaseService

方法：<init>()；createLog(UserLoginLog)；getUserLastRemoteLog(Long)

### JBoltRolePermissionService (class) extends JBoltBaseService

方法：<init>()；checkRoleHasPermission(LongString)；checkUserHasPermission(LongZ[String)；deleteByPermission(Long)；deleteByRole(Long)；deleteRolePermission(Long)；doSubmit(LongString)；getListByRole(Long)；getListByRoles(String)；getPermissionsByRoles(String)；getPermissionsKeySetByRoles(String)；getRoleIdsByPermissionKey(String)；getRolesByPermissionKey(String)

### JBoltRoleService (class) extends JBoltBaseService

方法：<init>()；checkCanDelete(JBoltBaseModelKv)；checkCanDelete(RoleKv)；checkInUse(JBoltBaseModelKv)；checkInUse(RoleKv)；delete(Long)；getAllRoleTreeDatas()；getRoleTreeDatas(Integer)；save(Role)；update(Role)

### JBoltSensitiveWordService (class) extends JBoltBaseService

方法：<init>()；delete(Long)；getAllEnableWords()；paginateAdminDatas(IIStringBoolean)；save(SensitiveWord)；update(SensitiveWord)

### JBoltSystemLogService (class) extends JBoltBaseService

方法：<init>()；paginateAdminList(IIntegerStringDateDate)；saveLog(IIObjectStringIObjectString)；targetTypeName(I)；targetTypeToName(I)；typeName(I)；typeToName(I)

### JBoltTopnavMenuService (class) extends JBoltBaseService

方法：<init>()；checkTopnavUsed(Long)；clearAllTopnavMenus()；clearTopnavMenus(Long)；deleteByTopnavId(Long)；getCheckedLeftMenus(Long)；submit(LongString)

### JBoltTopnavService (class) extends JBoltBaseService

方法：<init>()；checkCanToggle(JBoltBaseModelStringKv)；checkCanToggle(TopnavStringKv)；checkHasEnableTopnav()；clearAllMenus()；clearMenus(Long)；delete(Long)；down(Long)；getAdminList(String)；getEnableList()；getEnableListByIds(List)；initRank()；save(Topnav)；submit(TopnavZ)；toggleEnable(Long)；up(Long)；update(Topnav)

### JBoltUserConfigService (class) extends JBoltBaseService

方法：<init>()；changeStringValue(LongLongString)；checkAndInit(Long)；checkAndInitConfig(LongString)；getAdminList(Long)；getByConfigKey(LongString)；toggleBooleanConfig(LongLong)；update(LongUserConfig)

### JBoltUserService (class) extends JBoltBaseService

方法：<init>()；calFrontendPasswordWithSalt(StringString)；calPasswordWithSalt(StringString)；changeUserEnable(LongZ)；checkCanToggle(JBoltBaseModelStringKv)；checkCanToggle(UserStringKv)；checkDeptInUse(Long)；checkExistSuperAdmin()；checkMyLastPwdUpdateTime()；checkPwd(LongString)；checkRoleInUse(Long)；checkUserHasRole(LongZ[Long)；checkUserHasRoleSn(LongZ[String)；checkUserLastPwdUpdateTime(Long)；clearUsersByRole(Long)；deleteUserRole(LongLong)；getAllRoleGroups()；getDeptUserTree(IStringLong)；getEnableUserByDept(Long)；getFirstSystemAdmin()；getFirstSystemAdminUserName()；getOneSystemAdmin()；getUser(StringString)；getUsersByRoleId(Long)；getUsersByRoleIdSql(Long)；initTenantSystemAdminUser(StringStringString)；paginateUsersByRoleId(IILong)；pcInnerPlatformAppUserReLogin(HttpServletRequestHttpServletResponseLongString)；processUserLoginInfo(UserZLoginLog)；processUserRemoteLogin(UserLoginLog)；submitPwd(LongString)；toggleEnable(Long)；updatePwd(StringString)；updateUserAvatar(LongString)

### JBoltWechatConfigService (class) extends JBoltBaseService

方法：<init>()；checkAndInitConfig(LongI)；checkOneMpConfigExist(LongIStringLong)；checkWechatMpinfoInUse(Long)；configAllEnable()；configOneMpinfo(WechatMpinfo)；deleteByMpId(Long)；getAppIdConfig(String)；getBaseConfigList(Long)；getConfigList(LongI)；getPayConfigList(Long)；getWechatBaseConfigValue(LongString)；getWechatConfigAppId(Long)；getWechatConfigAppSecret(Long)；getWechatConfigMusicPostMediaId(Long)；getWechatConfigValue(LongIString)；getWechatExtraConfigValue(LongString)；getWechatMpidByAppId(String)；getWechatPayConfigValue(LongString)；removeOneEnableApiConfig(Long)；updateConfigs(LongIBooleanList)

常量：FORMAT_XML、FORMAT_JSON

### JBoltWechatMpinfoService (class) extends JBoltBaseService

方法：<init>()；getAllEnableList()

## cn.jbolt.core.service.base

### IJBoltService (interface) extends Object

常量：TRUE、SEPARATOR、TRUE_STR、FALSE、FALSE_STR

### JBoltBaseRecordService (class) extends JBoltCommonDatabaseService

方法：<init>()；checkCanDelete(RecordKv)；checkCanRecover(Record)；checkCanToggle(RecordStringKv)；checkInUse(RecordKv)；convertJsTree(ListObjectIString)；convertJsTree(ListObjectIStringString)；convertJsTree(ListObjectIStringStringString)；convertJsTree(ListObjectIStringStringStringStringStringStringStringZTreeCheckIsParentNode)；convertJsTree(ListObjectIStringStringStringStringStringZ)；convertJsTree(ListObjectIStringStringStringStringZ)；convertJsTree(ListObjectIStringStringStringZ)；convertJsTree(ListObjectIStringStringZ)；convertJsTree(ListObjectIStringZ)；convertToRecordTree(ListStringStringTreeCheckIsParentNode)；dbTemplate(String)；dbTemplate(StringKv)；dbTemplate(StringOkv)；deleteBy(Okv)；deleteBy(OkvZ)；deleteById(Object)；deleteById(ObjectKv)；deleteById(ObjectZ)；deleteById(ObjectZKv)；deleteById(ObjectZZKv)；deleteByIds(String)；deleteByIds(StringString)；deleteByIds(StringStringZ)；deleteByIds(StringZ)；deleteByIds([Object)；deleteByIds([ObjectZ)；exists(Sql)；exists(Sql[Object)；exists(StringObject)；exists(StringObjectObject)；exists(StringObjectObjectObject)；exists(StringObjectObjectStringObject)；existsById(Object)；existsInSons(StringObjectStringObject)；existsName(String)；existsName(StringObject)；existsName(StringObjectObject)；existsName(StringObjectStringObject)；existsNameWithPid(StringObject)；existsNameWithPid(StringStringObject)；existsSn(String)；existsSn(StringObject)；existsSn(StringObjectObject)；existsSn(StringObjectStringObject)；existsSnWithPid(StringObject)；existsSnWithPid(StringStringObject)；existsSon(Object)；existsSon(StringObject)；findAll(StringZ)；findById(Object)；findById(ObjectZ)；findByIds([Object)；findFirst()；findFirst(Okv)；findFirst(OkvZ)；findFirst(OkvZZ)；findFirst(Z)；findFirst(ZZ)；getAllParents()；getAllParents(Object)；getAllParents(StringObjectString)；getAllParents(StringObjectStringZ)；getAllParentsOrderById()；getAutocompleteList(StringIntegerBooleanString)；getAutocompleteList(StringIntegerBooleanStringSql)；getAutocompleteList(StringIntegerBooleanStringString)；getAutocompleteList(StringIntegerBooleanStringStringSql)；getAutocompleteList(StringIntegerString)；getAutocompleteList(StringIntegerStringSql)；getCommonList(Okv)；getCommonList(OkvString)；getCommonList(OkvStringString)；getCommonList(OkvZ)；getCommonList(StringOkv)；getCommonList(StringOkvStringString)；getCommonList(StringOkvStringStringZ)；getCommonList(StringOkvZ)；getCommonList(StringStringString)；getListByIds(String)；getListByIds([Object)；getOptionList()；getOptionList(StringString)；getOptionList(StringStringOkv)；getOptionList(StringStringOkvStringString)；getOptionListOrderBy(StringString)；getRandomOne(Okv)；getRandomOne(OkvZ)；getRandomOne(OkvZZ)；getSons(Object)；getSons(StringObjectString)；getSonsOrderById(Object)；getSonsOrderBySortRank(Object)；idSequence()；paginate(I)；paginate(II)；paginate(OkvI)；paginate(OkvII)；paginate(OkvIIZ)；paginate(OkvIZ)；paginate(OkvStringStringII)；paginate(OkvStringStringIIZ)；paginate(OkvStringStringIIZZ)；paginate(SqlZ)；paginate(StringOkvIIZ)；paginate(StringOkvStringStringII)；paginate(StringOkvStringStringIIZZ)；paginate(StringStringII)；paginateByJboltTableMenuFilter(JBoltTableMenuFilterZ[String)；paginateByJboltTableMenuFilter2(JBoltTableMenuFilterZ[String)；paginateByKeywords(StringIIStringStringOkvZ)；paginateByKeywords(StringStringIIStringStringOkvZ)；paginateByKeywords(StringStringStringIIStringStringOkvZ)；paginateBySqlTemplate(StringOkvII)；recoverById(Object)；recoverById(ObjectZ)；recoverByIds(String)；recoverByIds(StringStringZ)；recoverByIds(StringZ)；recoverByIds([ObjectZ)；save(Record)；save(RecordString)；selectSql(JBoltTableMenuFilterString[String)；selectSql(JBoltTableMenuFilter[String)；tableExist()；toggleBoolean(Object[String)

### JBoltBaseRecordTableSeparateService (class) extends JBoltCommonDatabaseService

方法：<init>()；afterToggleBoolean(ObjectRecordStringKv)；checkCanDelete(ObjectRecordKv)；checkCanRecover(ObjectRecord)；checkCanToggle(ObjectRecordStringKv)；checkInUse(ObjectRecordKv)；convertJsTree(ObjectListObjectI)；convertJsTree(ObjectListObjectIStringString)；convertJsTree(ObjectListObjectIStringStringString)；convertJsTree(ObjectListObjectIStringStringStringStringStringStringStringZTreeCheckIsParentNode)；convertJsTree(ObjectListObjectIStringStringStringStringStringZ)；convertJsTree(ObjectListObjectIStringStringStringStringZ)；convertJsTree(ObjectListObjectIStringStringStringZ)；convertJsTree(ObjectListObjectIStringStringZ)；convertJsTree(ObjectListObjectIStringZ)；createTable(Object)；dataSourceConfigName()；dbTemplate(ObjectString)；dbTemplate(ObjectStringKv)；dbTemplate(ObjectStringOkv)；dbType()；deleteBy(ObjectOkv)；deleteBy(ObjectOkvZ)；deleteById(ObjectObject)；deleteById(ObjectObjectKv)；deleteById(ObjectObjectZ)；deleteById(ObjectObjectZKv)；deleteById(ObjectObjectZZKv)；deleteByIds(ObjectString)；deleteByIds(ObjectStringString)；deleteByIds(ObjectStringStringZ)；deleteByIds(ObjectStringZ)；deleteByIds(Object[Object)；deleteByIds(Object[ObjectZ)；exists(ObjectSql)；exists(ObjectSql[Object)；exists(ObjectStringObject)；exists(ObjectStringObjectObject)；exists(ObjectStringObjectObjectObject)；exists(ObjectStringObjectObjectStringObject)；existsById(ObjectObject)；existsInSons(ObjectStringObjectStringObject)；existsName(ObjectString)；existsName(ObjectStringObject)；existsName(ObjectStringObjectObject)；existsName(ObjectStringObjectStringObject)；existsNameWithPid(ObjectStringObject)；existsNameWithPid(ObjectStringStringObject)；existsSn(ObjectString)；existsSn(ObjectStringObject)；existsSn(ObjectStringObjectObject)；existsSn(ObjectStringObjectStringObject)；existsSnWithPid(ObjectStringObject)；existsSnWithPid(ObjectStringStringObject)；existsSon(ObjectObject)；existsSon(ObjectStringObject)；findAll(ObjectStringZ)；findById(ObjectObject)；findByIds(Object[Object)；findFirst(Object)；findFirst(ObjectOkv)；findFirst(ObjectOkvZ)；getAllParents(Object)；getAllParents(ObjectObject)；getAllParents(ObjectStringObjectString)；getAllParents(ObjectStringObjectStringZ)；getAllParentsOrderById(Object)；getAutocompleteList(ObjectStringIntegerBooleanString)；getAutocompleteList(ObjectStringIntegerBooleanStringSql)；getAutocompleteList(ObjectStringIntegerBooleanStringString)；getAutocompleteList(ObjectStringIntegerBooleanStringStringSql)；getAutocompleteList(ObjectStringIntegerString)；getAutocompleteList(ObjectStringIntegerStringSql)；getCommonList(ObjectOkv)；getCommonList(ObjectOkvString)；getCommonList(ObjectOkvStringString)；getCommonList(ObjectOkvZ)；getCommonList(ObjectStringOkv)；getCommonList(ObjectStringOkvStringString)；getCommonList(ObjectStringOkvStringStringZ)；getCommonList(ObjectStringOkvZ)；getCommonList(ObjectStringStringString)；getCommonListWithoutColumns(ObjectOkvZ[String)；getCommonListWithoutColumns(ObjectString)；getListByIds(ObjectString)；getListByIds(Object[Object)；getOptionList(Object)；getOptionList(ObjectStringString)；getOptionList(ObjectStringStringOkv)；getOptionList(ObjectStringStringOkvStringString)；getOptionListOrderBy(ObjectStringString)；getRandomOne(ObjectOkv)；getRandomOne(ObjectOkvZ)；getSons(ObjectObject)；getSons(ObjectStringObjectString)；getSonsOrderById(ObjectObject)；getSonsOrderBySortRank(ObjectObject)；getTableSelectColumnStrWithout([String)；getTableSelectColumnStrWithoutWithPre(String[String)；getTableSelectColumnsWithout([String)；getTableSelectColumnsWithoutWithPre(String[String)；idGenMode()；idSequence(Object)；paginate(ObjectI)；paginate(ObjectII)；paginate(ObjectOkvI)；paginate(ObjectOkvII)；paginate(ObjectOkvIIZ)；paginate(ObjectOkvIZ)；paginate(ObjectOkvStringStringII)；paginate(ObjectOkvStringStringIIZ)；paginate(ObjectOkvStringStringIIZZ)；paginate(ObjectSqlSqlZ)；paginate(ObjectSqlZ)；paginate(ObjectStringOkvIIZ)；paginate(ObjectStringOkvStringStringII)；paginate(ObjectStringOkvStringStringIIZZ)；paginate(ObjectStringStringII)；paginateByKeywords(ObjectStringIIStringStringOkvZ)；paginateByKeywords(ObjectStringStringIIStringStringOkvZ)；paginateByKeywords(ObjectStringStringStringIIStringStringOkvZ)；paginateBySqlTemplate(ObjectStringOkvII)；primaryKey()；recoverById(ObjectObject)；recoverById(ObjectObjectZ)；recoverByIds(ObjectString)；recoverByIds(ObjectStringStringZ)；recoverByIds(ObjectStringZ)；recoverByIds(Object[ObjectZ)；save(ObjectRecord)；save(ObjectRecordString)；schema()；selectSql(ObjectJBoltTableMenuFilterString[String)；selectSql(ObjectJBoltTableMenuFilter[String)；tableExist(Object)；toggleBoolean(ObjectObject[String)

### JBoltBaseService (class) extends JBoltCommonDatabaseService

方法：<init>()；batchSave(List)；batchSave(ListI)；batchUpdate(List)；batchUpdate(ListI)；changeAllColumnToFalse(String)；changeAllColumnToFalseByExcludedId(StringObject)；changeAllColumnToTrue(String)；changeAllColumnToTrueByExcludedId(StringObject)；changeColumnBoolean(StringObjectSqlZ)；changeColumnFalse(StringObjectSql)；changeColumnFalseByExcludedId(StringObject)；changeColumnFalseById(StringObject)；changeColumnTrue(StringObjectSql)；changeColumnTrueByExcludedId(StringObject)；changeColumnTrueById(StringObject)；checkCanDelete(JBoltBaseModelKv)；checkCanRecover(JBoltBaseModel)；checkCanToggle(JBoltBaseModelStringKv)；checkColumnBooleanExist(StringZObjectSql)；checkColumnFalseExist(StringObject)；checkColumnTrueExist(StringObject)；checkInUse(JBoltBaseModelKv)；clearCache()；clearCacheBySql(Sql)；convertJsTree(ListObjectIString)；convertJsTree(ListObjectIStringString)；convertJsTree(ListObjectIStringStringString)；convertJsTree(ListObjectIStringStringStringStringStringStringStringZTreeCheckIsParentNode)；convertJsTree(ListObjectIStringStringStringStringStringZ)；convertJsTree(ListObjectIStringStringStringStringZ)；convertJsTree(ListObjectIStringStringStringZ)；convertJsTree(ListObjectIStringStringStringZTreeCheckIsParentNode)；convertJsTree(ListObjectIStringStringZ)；convertJsTree(ListObjectIStringZ)；convertJsTreeForAsync(ListZ)；convertJsTreeForAsync(ListZStringStringStringZ)；convertJsTreeForAsync(ListZStringZ)；convertJsTreeForAsync(ListZZ)；convertToJsTreeBean(JBoltBaseModelZZStringStringStringStringStringZZ)；convertToModelTree(ListStringStringTreeCheckIsParentNode)；daoTemplate(String)；daoTemplate(StringKv)；daoTemplate(StringOkv)；dataSourceConfigName()；dbTemplate(String)；dbTemplate(StringKv)；dbTemplate(StringOkv)；dbType()；deleteBy(Okv)；deleteBy(OkvZ)；deleteById(Object)；deleteById(ObjectKv)；deleteById(ObjectZ)；deleteById(ObjectZKv)；deleteById(ObjectZZZKv)；deleteByIds(String)；deleteByIds(StringString)；deleteByIds(StringStringZ)；deleteByIds(StringZ)；deleteByIds([Object)；deleteByIds([ObjectZ)；deleteByIds([ObjectZZ)；deleteCacheById([Object)；deleteCacheByKey(String)；deleteCacheByKey(StringObject)；deleteSql()；exists(Sql)；exists(Sql[Object)；exists(StringObject)；exists(StringObjectObject)；exists(StringObjectObjectObject)；exists(StringObjectObjectStringObject)；existsById(Object)；existsColumnIsNull(String)；existsInSons(StringObjectStringObject)；existsName(String)；existsName(StringObject)；existsName(StringObjectObject)；existsName(StringObjectStringObject)；existsNameWithPid(StringObject)；existsNameWithPid(StringObjectObject)；existsNameWithPid(StringObjectStringObject)；existsNameWithPid(StringStringObject)；existsSn(String)；existsSn(StringObject)；existsSn(StringObjectObject)；existsSn(StringObjectStringObject)；existsSnWithPid(StringObject)；existsSnWithPid(StringStringObject)；existsSon(Object)；existsSon(StringObject)；find(Sql)；find(String[Object)；findAll()；findAll(BooleanBoolean)；findAllEnable()；findAllEnable(Z)；findAllIsDeleted()；findAllIsDeleted(Z)；findAllIsDeletedFalse()；findById(Object)；findByIdLoadColumns(Object[String)；findByIdWithoutColumns(Object[String)；findByIds([Object)；findFirst()；findFirst(Okv)；findFirst(OkvStringString)；findFirst(OkvStringStringZ)；findFirst(OkvZ)；findFirst(Sql)；findFirstRecord()；findFirstRecord(Sql)；findFirstRecord(SqlZ)；findRecord(Sql)；findRecord(SqlZ)；findRecord(StringZ[Object)；findRecordById(Object)；findRecordById(ObjectZ[String)；getAllParents()；getAllParents(Object)；getAllParents(StringObjectString)；getAllParents(StringObjectStringZ)；getAllParentsOrderById()；getAllTableColumnsStr(Class)；getAllTableColumnsStr(ClassString)；getAutocompleteList(StringIntegerBooleanString)；getAutocompleteList(StringIntegerBooleanStringSql)；getAutocompleteList(StringIntegerBooleanStringString)；getAutocompleteList(StringIntegerBooleanStringStringSql)；getAutocompleteList(StringIntegerString)；getAutocompleteList(StringIntegerStringSql)；getAutocompleteList(StringIntegerStringString)；getCacheByKey(String)；getCacheByKey(StringObject)；getCommonList(Okv)；getCommonList(OkvString)；getCommonList(OkvStringString)；getCommonList(OkvStringStringZ)；getCommonList(OkvZ)；getCommonList(String)；getCommonList(StringOkv)；getCommonList(StringOkvStringString)；getCommonList(StringOkvStringStringZ)；getCommonList(StringOkvZ)；getCommonList(StringString)；getCommonList(StringStringString)；getCommonListByKeywords(StringString)；getCommonListByKeywords(StringStringString)；getCommonListByKeywords(StringStringStringString)；getCommonListByKeywords(StringStringStringStringOkv)；getCommonListByKeywords(StringStringStringStringString)；getCommonListByKeywords(StringStringStringStringStringOkv)；getCommonListWithoutColumns(OkvZ[String)；getCommonListWithoutColumns(String)；getCount()；getCount(Okv)；getCount(OkvZ)；getCount(Sql)；getListByIds(String)；getListByIds([Object)；getListByPid(Object)；getNextRankFromZero()；getNextRankFromZero(Okv)；getNextSortRank()；getNextSortRank(Okv)；getNextSortRank(OkvBooleanZ)；getNextSortRank(OkvZ)；getNextSortRankByPid(Object)；getNextSortRankByPid(StringObject)；getOneColumnValueById(ObjectString)；getOptionList()；getOptionList(BooleanBoolean)；getOptionList(StringString)；getOptionList(StringStringBooleanBoolean)；getOptionList(StringStringOkv)；getOptionList(StringStringOkvStringString)；getOptionListEnable()；getOptionListEnable(StringString)；getOptionListEnable(StringStringBoolean)；getOptionListIsDeleted(StringString)；getOptionListIsDeleted(StringStringBoolean)；getOptionListIsDeletedFalse(StringString)；getOptionListOrderBy(StringString)；getRandomOne(Okv)；getRandomOne(OkvZ)；getRecordListByIds([Object)；getRecordListByIds([ObjectZ[String)；getSons(Object)；getSons(StringObjectString)；getSonsOrderById(Object)；getSonsOrderBySortRank(Object)；getTableSelectColumnStrWithout([String)；getTableSelectColumnStrWithoutWithPre(String[String)；getTableSelectColumnsWithout([String)；getTableSelectColumnsWithoutWithPre(String[String)；idGenMode()；isEmpty()；notEmpty()；paginate(I)；paginate(II)；paginate(OkvI)；paginate(OkvII)；paginate(OkvIIZ)；paginate(OkvIZ)；paginate(OkvStringStringII)；paginate(OkvStringStringIIZ)；paginate(OkvStringStringIIZZ)；paginate(Sql)；paginate(StringII)；paginate(StringOkvIIZ)；paginate(StringOkvStringStringII)；paginate(StringOkvStringStringIIZZ)；paginate(StringStringII)；paginate(StringStringStringII)；paginateByDbSqlTemplate(StringOkvII)；paginateByDbSqlTemplate(StringOkvIIZ)；paginateByJboltTableMenuFilter(JBoltTableMenuFilter[String)；paginateByKeywords(StringIIStringString)；paginateByKeywords(StringIIStringStringOkv)；paginateByKeywords(StringStringIIStringString)；paginateByKeywords(StringStringIIStringStringOkv)；paginateByKeywords(StringStringStringIIStringStringOkv)；paginateBySqlTemplate(StringOkvII)；paginateBySqlTemplate(StringOkvIIStringString)；paginateRecord(Sql)；paginateRecord(SqlSqlZ)；paginateRecord(SqlZ)；primaryKey()；realDeleteById(Object)；realDeleteById(ObjectKv)；realDeleteById(ObjectZ)；realDeleteById(ObjectZKv)；realDeleteByIds(String)；realDeleteByIds(StringString)；realDeleteByIds(StringStringZ)；realDeleteByIds(StringZ)；realDeleteByIds([Object)；realDeleteByIds([ObjectZ)；recoverById(Object)；recoverById(ObjectZ)；recoverByIds(String)；recoverByIds(StringStringZ)；recoverByIds(StringZ)；recoverByIds([ObjectZ)；selectSql()；selectSql(JBoltTableMenuFilterString[String)；selectSql(JBoltTableMenuFilter[String)；superFindById(Object)；superFindByIdLoadColumns(Object[String)；table()；toOptions(List)；toOptions(ListStringString)；toggleBoolean(KvObject[String)；toggleBoolean(Object[String)；toggleEnable(Object)；toggleIsDefault(Object)；updateColumn(ObjectStringObject)；updateOneColumn(JBoltBaseModel)；updateSql()

### JBoltCommonDatabaseService (class) extends JBoltCommonService

方法：<init>()；convertToRecordTree(ListStringStringTreeCheckIsParentNode)；emptyPage(Integer)；processEachLevelByParentLevel(RecordInteger)；toDateTime(String)；toEndTime(Date)；toStartTime(Date)

### JBoltCommonService (class) extends Object implements IJBoltService, IJBoltParaValidator

方法：<init>()；convertToJsTreeBean(RecordZZStringStringStringStringStringZZ)；deleteWebappFile(String)；hasNotOk([Object)；hasOk([Object)；isExcel(UploadFile)；isImage(String)；isImage(UploadFile)；isNull(Object)；isOk(Model)；isOk(Object)；isOk(Record)；isOk([Object)；notExcel(UploadFile)；notImage(String)；notImage(UploadFile)；notNull(Object)；notOk(Model)；notOk(Object)；notOk(Record)；notOk([Integer)；notOk([Object)；notOk([Serializable)；toDateYMD(Date)

常量：RET_STATE、RET_MSG、RET_DATA、WARN_STATE

### JBoltSaasTenantService (class) extends Object

方法：<init>()；creatTenantSeparateCoreTableAndInitDatas(StringStringStringString)

## cn.jbolt.core.ui.jbolttable

### JBoltTable (class) extends Object

方法：<init>()；deleteIsBlank()；deleteIsNotBlank()；formIsBlank()；formIsNotBlank()；getDelete()；getForm()；getFormBean(Class)；getFormBean(ClassString)；getFormModel(Class)；getFormModel(ClassString)；getFormRecord()；getParamRecord()；getParamToBigDecimal(String)；getParamToBool(String)；getParamToDate(String)；getParamToDouble(String)；getParamToInt(String)；getParamToLong(String)；getParamToObject(String)；getParamToStr(String)；getParams()；getParamsBean(Class)；getParamsBean(ClassString)；getParamsModel(Class)；getParamsModel(ClassString)；getSave()；getSaveBeanList(Class)；getSaveModelList(Class)；getSaveRecordList()；getUpdate()；getUpdateBeanList(Class)；getUpdateModelList(Class)；getUpdateRecordList()；isBlank()；isNotBlank()；paramsIsBlank()；paramsIsNotBlank()；saveIsBlank()；saveIsNotBlank()；setDelete([Object)；setForm(JSONObject)；setParams(JSONObject)；setSave(JSONArray)；setUpdate(JSONArray)；toJson()；updateIsBlank()；updateIsNotBlank()

常量：PARAM_KEY

### JBoltTableMulti (class) extends LinkedHashMap

方法：<init>()；getJBoltTable(String)

常量：PARAM_KEY

## cn.jbolt.core.util

### CodeGenKvDataGenUtil (class) extends Object

方法：<init>()；gen(String)

### JBoltArrayUtil (class) extends Object

方法：<init>()；contains([StringInteger)；contains([StringLong)；contains([StringString)；from(String)；from(StringString)；from2(String)；from2(StringString)；from3(String)；from3(StringString)；getDistinctIntegerArray(ListString)；getDistinctIntegerList(ListString)；getDistinctLongList(ListString)；getDistinctStringList(ListString)；getIntegerArray(ListString)；getLongArray(ListString)；getStringArray(ListString)；isEmpty([Object)；join(List)；join(ListString)；join(ListStringZ)；join(ListZ)；join([Object)；join([ObjectString)；join([ObjectStringZ)；join([ObjectZ)；join([String)；join([StringString)；join([StringStringZ)；join([StringZ)；listFrom(StringString)；main([String)；merge(StringStringString)；merge([Integer[Integer)；merge([Long[Long)；merge([String[String)；notEmpty([Object)；splitStrByLine(String)；splitStrByLineToList(String)；toBigDecimal(String)；toBigDecimal(StringString)；toDisInt(String)；toDisInt(StringString)；toDisLong(String)；toDisLong(StringString)；toInt(String)；toInt(StringString)；toLong(String)；toLong(StringString)

常量：COMMA

### JBoltCamelCaseUtil (class) extends Object

方法：<init>()；convert(List)；convert(Page)；convert(Record)；keyToCamelCase(List)；keyToCamelCase(Page)；keyToCamelCase(Record)

### JBoltColorUtil (class) extends Object

方法：<init>()；colorClassByLevel(I)；colorClassByPriorityLevel(I)；colorClassByServiceState(I)；colorClassByState(I)

### JBoltConsoleUtil (class) extends Object

方法：<init>()；printErrorMessageWithDate(String)；printJboltcn()；printMessage(String)；printMessageWithDate(String)

### JBoltCookieUtil (class) extends Object

方法：<init>()；get(HttpServletRequestString)；getValue(HttpServletRequestString)；set(HttpServletResponseStringStringStringStringBooleanI)

### JBoltDataSizeUtil (class) extends Object

方法：<init>()；getPrintSize(D)；main([String)

### JBoltDateUtil (class) extends Object

方法：<init>()；HHmmssTo000000(Date)；HHmmssTo000000Str(Date)；HHmmssTo235959(Date)；HHmmssTo235959Str(Date)；daysBetween(DateDate)；daysBetween(DateDateI)；format(DateString)；formatWithT(DateString)；get365Before(Date)；getAge(Date)；getByAgeEnd(I)；getByAgeStart(I)；getCalendarByDate(Date)；getContainDays(DateDate)；getDate(String)；getDate(StringString)；getDaysAfter(DateI)；getDaysAfter(I)；getDaysAgo(DateI)；getDaysAgo(I)；getFromStr(StringString)；getHourAfter(DateI)；getHourAfter(I)；getHourAgo(DateI)；getHourAgo(I)；getMinutesAfter(DateI)；getMinutesAfter(I)；getMinutesAgo(DateI)；getMinutesAgo(I)；getMonth()；getMonthsAfter(DateI)；getMonthsAfter(I)；getNow()；getNow(Z)；getNowDate()；getNowStr()；getNowStr(String)；getNowStrYMD()；getOneWeekAllDays(Date)；getQuarterNumber(Calendar)；getQuartersAfter(DateI)；getQuartersAfter(I)；getSecondsAfter(DateI)；getSecondsAfter(I)；getSecondsBefore(DateI)；getSecondsBefore(I)；getTime(String)；getTime(Time)；getTimeDiff(DateDate)；getUsedTimeStr(Date)；getWeek(Date)；getWeek2(Date)；getYear()；getYearFirstDay(I)；getYearLastDay(I)；getYearStr()；getYearStrFull()；getYearsAfter(DateI)；getYearsAfter(I)；getYesterday()；getYesterday(Calendar)；getYesterdayCalendar()；getYesterdayStr()；getYesterdayStr(String)；isFirstDayOfTheMonth(Calendar)；isFirstDayOfTheMonth(Date)；isFirstDayOfTheQuarter(Date)；isFirstDayOfTheYear(Date)；isLastDayOfTheMonth(Date)；isLastDayOfTheQuarter(Date)；isLastDayOfTheYear(Date)；isMonday(Date)；isNotToday(Calendar)；isSameDay(CalendarCalendar)；isSunday(Date)；isTheDayBeforeYesterday(Date)；isYesterday(Date)；isYesterdayOrMoreRecent(Date)；lastMonthFirstDay(Date)；lastMonthLastDay(Date)；lastQuarterFirstMonth(Date)；lastQuarterLastMonth(Date)；lastWeekMonday(Date)；lastWeekSunday(Date)；main([String)；nextMonthDate(Date)；nextMonthFirstDay(Date)；nextMonthNow()；nextQuarterDate(Date)；nextQuarterNow()；nextWeekMonday(Date)；nextWeekSunday(Date)；nextYearDate(Date)；nextYearNow()；removeMillisecond(Date)；thisMonthFirstDay(Date)；thisMonthLastDay(Date)；thisMonthMaxDate(Date)；thisQuarterFirstDay(Date)；thisQuarterFirstMonth(Date)；thisQuarterLastDay(Date)；thisQuarterLastMonth(Date)；thisWeekMonday(Date)；thisWeekSunday(Date)；thisYearFirstDay(Date)；thisYearLastDay(Date)；toDate(Date)；toDate(String)；toDate(StringString)；tomorrow()；tomorrow(Date)；tomorrowHHmmssTo000000(Date)；tomorrowHHmmssTo000000Str(Date)；tomorrowNowStr()；tomorrowNowStr(String)；tomorrowStr()；tomorrowStr(String)；yesterday(Date)

常量：YYYY、YMDE、YMD、HMS、HM、YMDHMSS、YMDHMSS2、YMDHMS、YMDHM、MDHM、MD、MM、DD、FLOOR、CEIL、ROUND

### JBoltDesUtil (class) extends Object

方法：<init>()；getDecryptData(String)；getEncryptData(String)

### JBoltFileSizeUtil (class) extends Object

方法：<init>()；format(Long)

### JBoltIpUtil (class) extends Object

方法：<init>()；getIp(HttpServletRequest)；getServerIp()；isValidAddress(String)；main([String)；toAddressByBaiduMapApi(String)；toAddressByIp2Region(String)

### JBoltListMap (class) extends HashMap

方法：<init>()；addItem(ObjectObject)

### JBoltPinYinUtil (class) extends Object

方法：<init>()；getFirstSpell(String)；getFullSpell(String)；getSpells(String)；getSpells(StringI)；main([String)

常量：DEFAULT_MAX_LENGTH、EMPATY

### JBoltQiniuUtil (class) extends Object

方法：<init>()；genToken(StringStringString)；getBucketInfo(StringStringString)；getBucketInfoList(StringString)；getBucketNameList(StringString)；getBucketNames(StringString)；getBucketRegion(StringStringString)；getDomainList(StringStringString)；getDomainStr(StringStringString)；getDomains(StringStringString)；main([String)

### JBoltRandomUtil (class) extends Object

方法：<init>()；random(I)；randomLow(I)；randomLowWithNumber(I)；randomNumber(I)；randomNumberToInt(I)；randomUp(I)；randomUpWithNumber(I)

### JBoltRealUrlUtil (class) extends Object

方法：<init>()；get(Object)；get(ObjectObject)；getImage(Object)；getImage(ObjectObject)；getRealLocalPath(String)；getWechatImage(String)；getWechatImage(StringString)

### JBoltRemarkUtil (class) extends Object

方法：<init>()；changeToSingleLine(String)

### JBoltStringUtil (class) extends Object

方法：<init>()；arrayToString([StringString)；containsSpecialUnicode(String)；count(StringC)；filterEmoji(String)；getImgUrlFromHtml(String)；getNumber(String)；getPreWords(StringIString)；isAudio(String)；isBlank(String)；isBr(String)；isCommentNode(String)；isDataNode(String)；isEmbed(String)；isEmpty(String)；isFigure(String)；isHr(String)；isImg(String)；isNotBlank(String)；isNotEmpty(String)；isNotNullSpan(Node)；isPbr(Node)；isScriptNode(String)；isTextNode(String)；isVideo(String)；listToString(ListString)；processHtmlToTextContent(StringIII[String)；removeNonChinese(String)；removeNumber(String)；replace(StringStringString)；splitInt(String)；toLowerCaseFirstWord(String)；toString([String)；toUpperCase(String)；toUpperCaseFirstWord(String)；unescapeJava(String)

### JBoltUploadFileUtil (class) extends Object

方法：<init>()；delete(String)；getRealLocalPath(String)

## cn.jbolt.core.wechat

### JBoltWechatApi (class) extends Object

方法：call(Supplier)；use(String)；use(StringJBoltWechatApi$Type)

### JBoltWechatMpinfoSubjectType (enum) extends Enum

方法：getText()；getValue()；setText(String)；setValue(I)；valueOf(String)；values()

常量：PERSONAL、INDIVIDUAL_BUSINESS、COMPANY、MEDIA、ORG、GOV、GOV_SPONSORED_INSTITUTION

### JBoltWechatTplMsgEngineUtil (class) extends Object

方法：clearAllCache()；load(String)；reload(String)；reload(StringString)；render(StringMap)；render(StringStringMap)

常量：me

### WechatConfigKey (class) extends Object

方法：<init>()；getConfigKeys(IZ)；getConfigNames(IZ)

常量：APP_ID、APP_SECRET、APP_GHID、SERVER_DOMAIN_URL、SERVER_TOKEN、SERVER_ENCODINGAESKEY、SERVER_MSG_ENCRYPT_TYPE、SERVER_DATA_FORMAT、MUSIC_POST_MEDIAID、baseConfigKeys、extraConfigKeys、baseWxaConfigKeys、baseConfigNames、extraConfigNames、baseWxaConfigNames、MCH_ID、PATERNERKEY、PATERNERKEY_V3、LIMIT_PAY、RECEIPT、payConfigKeys、payConfigNames

### WechatMpinfoType (enum) extends Enum

方法：getText()；getValue()；setText(String)；setValue(I)；valueOf(String)；values()

常量：DYH、FWH、QYWX、XCX
