# 08 JBolt 配置目录（自动生成）

> 由 build_feature_catalog.py 从运行中的 JBolt 参考库（jboltai）导出：全局参数、参数分组、内置字典。

## 全局参数分组

| 分组 | type_key | 内置 |
|---|---|---|
| 系统基础配置 | sys_config | 是 |
| 系统安全配置 | sys_safe_config | 是 |
| 系统备案相关 | sys_put_on_record | 是 |
| 后台登录 | admin_login | 是 |
| 后台样式 | admin_ui | 是 |
| 微信开发 | wechat_dev | 是 |

## 全局参数（39 项，首次访问时自动创建）

| 分组 | KEY | 名称 | 值类型 | 当前值 | 内置 |
|---|---|---|---|---|---|
| 系统基础配置 | ASSETS_VERSION | 系统静态资源版本号 | string | 20261110110137594 | 是 |
| 系统基础配置 | JBOLT_ACTION_REPORT_LINE_NUMBER_ENABLE | 系统 Action Report输出准确行号 | boolean | false | 是 |
| 系统基础配置 | JBOLT_ACTION_REPORT_WRITER | 系统 Action Report输出方式 | string | sysout | 是 |
| 系统基础配置 | JBOLT_AUTO_CACHE_LOG | 系统自动缓存Debug日志 | boolean | false | 是 |
| 系统基础配置 | SYSTEM_DEPT_ENABLE | 启用部门 | boolean | false | 是 |
| 系统基础配置 | SYSTEM_POST_ENABLE | 启用岗位 | boolean | false | 是 |
| 系统基础配置 | SYSTEM_USER_EXTEND_ENABLE | 启用用户信息扩展 | boolean | false | 是 |
| 系统安全配置 | JBOLT_ADMIN_USER_KEEPLOGIN_SECONDS | 后台用户保持登录时Cookie时长(秒) | int | 604800 | 是 |
| 系统安全配置 | JBOLT_ADMIN_USER_NOT_KEEPLOGIN_SECONDS | 后台用户不保持登录时Cookie时长(秒) | int | 28800 | 是 |
| 系统安全配置 | JBOLT_AUTO_LOCKSCREEN_SECONDS | 用户多长时间(秒)无操作自动锁屏 | int | 0 | 是 |
| 系统安全配置 | JBOLT_CHECK_JWT_API_USER_EFFECTIVE_ENABLE | 是否启用JWT-APIUser-有效性检测机制 | boolean | false | 是 |
| 系统安全配置 | JBOLT_CHECK_JWT_API_USER_EFFECTIVE_TOKEN | JWT-APIUser-有效性检测因子 | string | jbolt | 是 |
| 系统安全配置 | JBOLT_DICTIONARY_DELETE_ENABLE | 是否启用字典删除功能 | boolean | false | 是 |
| 系统安全配置 | JBOLT_LOCKSYSTEM_AFTER_AUTOLOGIN | 启用自动登录后进入锁屏状态 | boolean | false | 是 |
| 系统安全配置 | JBOLT_LOGIN_TERMINAL_ONLYONE | 同一账号不能多端登录 | boolean | false | 是 |
| 系统安全配置 | JBOLT_PASSWORD_CHANGE_NOTICE_DAYS | 多久没改密码就提醒 | int | 0 | 是 |
| 系统备案相关 | JBOLT_DOMAIN_PUT_ON_RECORD_INFO | 系统备案号 | string | NULL | 是 |
| 系统备案相关 | JBOLT_DOMAIN_PUT_ON_RECORD_LINK | 系统备案号链接 | string | http://beian.miit.gov.cn/ | 是 |
| 后台登录 | JBOLT_ADMIN_LOGIN_HTML_FILE | 登录页文件配置 | string | login.html | 是 |
| 后台登录 | JBOLT_LOGIN_BGIMG | 系统登录页背景图 | string | assets/css/img/login_bg.jpg | 是 |
| 后台登录 | JBOLT_LOGIN_BGIMG_BLUR | 系统登录页面背景图是否启用模糊风格 | boolean | false | 是 |
| 后台登录 | JBOLT_LOGIN_CAPTURE_TYPE | 系统登录页验证码类型 | string | default | 是 |
| 后台登录 | JBOLT_LOGIN_NEST | 系统登录页是否开启线条特效 | boolean | true | 是 |
| 后台登录 | JBOLT_LOGIN_USE_CAPTURE | 系统登录页面是否启用验证码 | boolean | true | 是 |
| 后台登录 | SYSTEM_COPYRIGHT_COMPANY | 系统版权所有人 | string | ©JBoltAI(jboltai.com) | 是 |
| 后台登录 | SYSTEM_COPYRIGHT_LINK | 系统版权所有人的网址链接 | string | https://jboltai.com | 是 |
| 后台登录 | SYSTEM_NAME | 系统名称 | string | JBoltAI数智化开发平台 | 是 |
| 后台样式 | JBOLT_ADMIN_STYLE | 系统Admin后台样式 | string | default | 是 |
| 后台样式 | JBOLT_ADMIN_WITH_TABS | 系统Admin后台是否启用多选项卡 | boolean | false | 是 |
| 后台样式 | JBOLT_DEPT_MGR_TYPE | 系统部门管理方式 | string | tree_table | 是 |
| 后台样式 | JBOLT_TAB_KEEP_AFTER_RELOAD | 启用页面重载保持住原有选项卡 | boolean | false | 是 |
| 后台样式 | SYSTEM_ADMIN_GLOBAL_SEARCH_SHOW | 系统后台全局搜索输入框是否启用 | boolean | false | 是 |
| 后台样式 | SYSTEM_ADMIN_H50 | 系统后台整体样式高度使用H50 | boolean | false | 是 |
| 后台样式 | SYSTEM_ADMIN_LEFT_NAV_WIDTH | 系统后台左侧导航宽度 | int | 220 | 是 |
| 后台样式 | SYSTEM_ADMIN_LOGO | 系统后台主页LOGO | string | assets/img/logo.png | 是 |
| 后台样式 | SYSTEM_ADMIN_NAV_MENU_DEFAULT_ICON | 系统后台导航菜单默认图标 | string | NULL | 是 |
| 微信开发 | WECHAT_ASSETS_SERVER_DOMAIN | 微信_静态资源_根URL | string | http://localhost | 是 |
| 微信开发 | WECHAT_MP_SERVER_DOMAIN | 微信公众号_服务器配置_根URL | string | http://localhost/wx/msg | 是 |
| 微信开发 | WECHAT_WXA_SERVER_DOMAIN | 微信小程序_客服消息推送配置_根URL | string | http://localhost/wxa/msg | 是 |

## 内置字典（41 类，301 项）

| 字典类型 | type_key | 层级 | 字典项（名称(编码)） |
|---|---|---|---|
| 选项_Boolean值 | options_boolean | 一级 | 是(true)、否(false) |
| 选项_enable | options_enable | 一级 | 启用(true)、禁用(false) |
| 性别 | sex | 一级 | 男(1)、女(2) |
| 机构类型 | dept_type | 一级 | 总部(1)、省级公司(2)、市级公司(3)、区县级公司(4)、办事处(5)、部门(6) |
| 岗位类型 | post_type | 一级 | 高管(1)、中层(2)、基层(3)、其他(4) |
| 系统通知_类型 | sys_notice_type | 一级 | 公告(1)、新闻(2)、会议(3)、放假(4)、其它(5) |
| 系统通知_优先级 | sys_notice_priority_level | 一级 | 普通(1)、一般(2)、紧急(3) |
| 系统通知_接收人类型 | sys_notice_receiver_type | 一级 | 全部(1)、角色(2)、部门(3)、岗位(4)、用户(5) |
| 待办事项_优先级 | todo_priority_level | 一级 | 普通(1)、一般(2)、紧急(3) |
| 待办事项_状态 | todo_state | 一级 | 未开始(1)、进行中(2)、已完成(3)、已取消(4)、未完成(5) |
| 待办事项_类型 | todo_type | 一级 | 无链接无内容(1)、无链接有内容(2)、有链接无内容(3)、有链接有内容(4) |
| 时间查询列名 | todo_datetime_column | 一级 | 创建时间(1)、更新时间(2)、待完成时间(3)、实际完成时间(4)、取消时间(5) |
| 七牛账号_类型 | qiniu_type | 一级 | 个人(1)、企业(2) |
| 七牛账号_地区 | qiniu_region | 一级 | 华东(z0)、华北(z1)、华南(z2)、北美(na0)、东南亚(as0) |
| 主键生成策略 | code_gen_idgenmode | 一级 | 自增_auto_int(auto)、自增_auto_long(auto_long)、自增_auto_string(auto_string)、自增序列_sequence(sequence)、自增序列_sequence_long(sequence_long)、自增序列_pgsql_serial(serial)、自增序列_pgsql_bigserial(bigserial)、雪花_snowflake_long(snowflake)、雪花_snowflake_字符串(snowflake_string)、UUID(uuid) |
| 表格列显示格式化 | code_gen_table_col_format | 一级 | Boolean转文字(boolean_to_str)、Boolean转对号[√](boolean_to_check)、Enable转文字(enable_to_str)、Enable转对号[√](enable_to_check)、日期_yyyy-MM-dd(date_ymd)、日期_yyyy-MM(date_ym)、日期时间_yyyy-MM-dd HH:mm:ss(date_ymdhms)、日期时间_yyyy-MM-dd HH:mm(date_ymdhm)、日期_HH:mm:ss(date_hms)、日期_HH:mm(date_hm)、时间_HH:mm:ss(time_hms)、时间_HH:mm(time_hm)、图片URL_real_image(real_image)、文件URL_real_url(real_url)、时间美化_pretty_time(pretty_time)、字符串转驼峰 |
| 组件数据类型 | code_gen_data_type | 一级 | 用户输入(user_input)、系统_数据字典(sys_dictionary)、自定义输入URL(url)、自定义输入Action名称(action)、自定义输入option(option)、枚举类(enum) |
| codeGen_属性翻译类型 | code_gen_translate_type | 一级 | 字典_ID(sys_dic_id)、字典_SN(sys_dic_sn)、系统用户_ID转UserName(sys_user_id_to_username)、系统用户_ID转Name(sys_user_id_to_name)、系统用户_用户名转Name(sys_user_username_to_name)、缓存(cache)、枚举类(enum)、自定义静态 Method(static_method)、自定义Service Method(service_method)、自定义Key-Value数据(kv_data) |
| codeGen_查询条件组件类型 | code_gen_condition_ui_type | 一级 | 单行输入框_input(input)、密码输入框_password(password)、数字输入框_number(number)、数字输入框_带计算器(calculator)、多行文本框_textarea(textarea)、选择器_单选_select(select)、选择器_多选_select(select_multi)、选择器_单选_select2(select2)、选择器_多选_select2(select2_multi)、输入自动补全_autocomplete(autocomplete)、单选_radio(radio)、多选_checkbox(checkbox)、jboltinput(jboltinput)、jboltinput_jstree(jboltinput_jstree)、jboltinput_table(jboltinput_table)、原生_日期_date(input |
| codeGen_可编辑表格组件类型 | code_gen_table_ui_type | 一级 | 单行输入框_input(input)、密码输入框_password(password)、数字输入框_number(number)、数字输入框_带计算器(calculator)、多行文本框_textarea(textarea)、选择器_单选_select(select)、选择器_多选_select(select_multi)、输入自动补全_autocomplete(autocomplete)、单checkbox(checkbox)、jboltinput(jboltinput)、jboltinput_jstree(jboltinput_jstree)、jboltinput_table(jboltinput_table)、原生_日期_date(input_date)、原生_时间_time(input_time)、原生_日期时间_datetime(input_datetime)、原生_月份_mon |
| codeGen_表单组件类型 | code_gen_form_ui_type | 一级 | 单行输入框_input(input)、密码输入框_password(password)、数字输入框_number(number)、数字输入框_带计算器(calculator)、多行文本框_textarea(textarea)、选择器_单选_select(select)、选择器_多选_select(select_multi)、选择器_单选_select2(select2)、选择器_多选_select2(select2_multi)、输入自动补全_autocomplete(autocomplete)、单选_radio(radio)、多选_checkbox(checkbox)、jboltinput(jboltinput)、jboltinput_jstree(jboltinput_jstree)、jboltinput_table(jboltinput_table)、富文本_summernote(s |
| 表单校验规则 | code_gen_form_data_rule | 一级 | 普通必填(required)、select必选(select)、radio必选(radio)、checkbox必选(checkbox)、数字(number)、正数(pnumber)、非负数(pznumber)、整数(int)、正整数(pint)、自然数(pzint)、电子邮件地址(email)、磁盘文件地址(filepath)、网址(url)、网址_不带http(url_nohttp)、日期时间(年-月-日 时:分:秒)(datetime)、日期时间(年-月-日 时:分)(datetime_hm)、日期(年-月-日)(date)、时间(时:分:秒)(time)、时间(时:分)(time_hm)、金额_两位小数(money)、金额_四位小数(money_4)、手机号(phone)、座机电话号(tel)、中文汉字(zh_cn)、IP地址(ip)、邮政编码(6位)(postalcode)、中国公 |
| codeGen_模糊查询类型 | code_gen_condition_fuzzy_query_type | 一级 | 默认Like查询(default)、左Like查询(left)、右Like查询(right) |
| 扩展数据字典Demo | extend_dic_demo | 多级 | 字典项1(值1)、字典项2(值2)、字典项2-1(值2-1)、字典项2-2(值2-2)、字典项2-2-1(值2-2-1)、字典项2-3(值2-3)、字典项3(值3)、字典项3-1(值3-1)、字典项4(值4) |
| AI_Page类型 | ai_page_type | 一级 | 功能页面(1)、AI应用(2)、业务数据(3) |
| AI_Page打开方式 | ai_page_open_type | 一级 | 默认(1)、默认+Iframe(2)、Dialog查询(3)、Dialog表单(4)、抽屉层加载(5)、抽屉层+Iframe加载(6)、浏览器新标签页(7)、浏览器新独立页面(8) |
| AI_应用类型 | ai_app_type | 一级 | 文字交互(1)、语音交互(2)、选项交互(3)、混合交互(4) |
| AI_模型 | ai_model | 一级 | baichuan(baichuan) |
| AI_应用图标类型 | ai_icon_type | 一级 | CSS FONT(1)、IMG URL(2) |
| AI_处理状态 | ai_process_state | 一级 | 等待处理(1)、进行中(2)、失败(3)、成功(4) |
| AI_训练类型 | ai_train_type | 一级 | 非QA类(1)、QA类(2) |
| AI_数据类型 | ai_data_type | 一级 | 自定义文本(1)、文件块(2) |
| AI_应用发布类型 | ai_app_publish_type | 一级 | 系统应用(1)、系统应用（跨域IFrame）(4)、访客应用(2)、密码访问(3) |
| AI_Function类型 | ai_function_type | 一级 | Native(1)、API(2) |
| AI_Function Http Method | ai_function_api_http_method | 一级 | POST(POST)、GET(GET) |
| AI_APP_UI_布局风格 | ai_app_ui_layout_theme | 一级 | 默认风格(DEFAULT)、PC_盒子(PC_BOX)、自适应盒子(AUTO_BOX) |
| AI_APP_UI_对话风格 | ai_app_ui_chat_theme | 一级 | 默认风格(DEFAULT)、左对齐瀑布(LEFT_WATERFALL) |
| AI_File_上传类型 | ai_file_upload_type | 一级 | 自主上传(1)、网页转文件(2) |
| AI_APP_UI_对话气泡风格 | ai_app_ui_chat_bubble_style | 一级 | 默认风格(DEFAULT)、风格一(style_1) |
| AI_APP_UI_对话Header风格 | ai_app_ui_chat_header_style | 一级 | 默认风格(DEFAULT)、风格一(style_1) |
| AI_APP_前端框架 | ai_app_front_framework | 一级 | Bootstrap5(bootstrap)、Tailwind4(tailwind) |
