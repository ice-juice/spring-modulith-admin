# JBolt 数据字典（MySQL，自动生成）

> 由 tools/jbolt-research/parse_schema.py 解析以下文件生成：jbolt_mysql_id_long.sql、jbolt_ai.sql、custom_business_mysql.sql

| 分组 | 表 | 说明 | 字段数 |
|---|---|---|---|
| platform | [jb_application](#jb_application) | API应用中心的应用APP | 14 |
| platform | [jb_dictionary](#jb_dictionary) | 字典表 | 9 |
| platform | [jb_dictionary_type](#jb_dictionary_type) | 字典类型 | 6 |
| platform | [jb_global_config](#jb_global_config) | 全局配置 | 13 |
| platform | [jb_global_config_type](#jb_global_config_type) | 全局参数类型分组 | 5 |
| platform | [jb_jbolt_file](#jb_jbolt_file) | 文件库 | 10 |
| platform | [jb_login_log](#jb_login_log) | 登录日志 | 23 |
| platform | [jb_online_user](#jb_online_user) | 在线用户 | 10 |
| platform | [jb_permission](#jb_permission) | function定义 | 15 |
| platform | [jb_remote_login_log](#jb_remote_login_log) | 异地登录日志记录 | 15 |
| platform | [jb_role](#jb_role) | 角色表 | 4 |
| platform | [jb_role_permission](#jb_role_permission) | 角色功能列表 | 3 |
| platform | [jb_system_log](#jb_system_log) | 系统操作日志表 | 10 |
| platform | [jb_dept](#jb_dept) | 组织机构 | 17 |
| platform | [jb_post](#jb_post) | 岗位 | 7 |
| platform | [jb_user](#jb_user) | 用户登录账户表 | 31 |
| platform | [jb_user_config](#jb_user_config) | 用户系统样式自定义设置表 | 8 |
| platform | [jb_wechat_article](#jb_wechat_article) | 微信图文信息 | 14 |
| platform | [jb_wechat_autoreply](#jb_wechat_autoreply) | 微信公众号自动回复规则 | 8 |
| platform | [jb_wechat_config](#jb_wechat_config) | 微信公众号配置表 | 6 |
| platform | [jb_wechat_keywords](#jb_wechat_keywords) | 微信关键词回复中的关键词定义 | 5 |
| platform | [jb_wechat_media](#jb_wechat_media) | 微信公众平台素材库同步管理 | 12 |
| platform | [jb_wechat_menu](#jb_wechat_menu) | 微信菜单 | 9 |
| platform | [jb_wechat_mpinfo](#jb_wechat_mpinfo) | 微信公众号与小程序 | 16 |
| platform | [jb_wechat_reply_content](#jb_wechat_reply_content) | 自动回复内容 | 13 |
| platform | [jb_wechat_user](#jb_wechat_user) | 微信用户信息_模板表 | 36 |
| platform | [jb_topnav](#jb_topnav) | 顶部导航 | 5 |
| platform | [jb_topnav_menu](#jb_topnav_menu) | 顶部菜单对应左侧一级导航中间表 | 3 |
| platform | [jb_todo](#jb_todo) | 待办事项 | 22 |
| platform | [jb_sys_notice](#jb_sys_notice) | 系统通知 | 14 |
| platform | [jb_sys_notice_reader](#jb_sys_notice_reader) | 通知阅读用户关系表 | 4 |
| platform | [jb_private_message](#jb_private_message) | 内部私信 | 5 |
| platform | [jb_hiprint_tpl](#jb_hiprint_tpl) | hiprint模版 | 11 |
| platform | [jb_qiniu](#jb_qiniu) | 七牛账号表 | 14 |
| platform | [jb_qiniu_bucket](#jb_qiniu_bucket) | 七牛bucket配置 | 17 |
| platform | [jb_datasource_filter](#jb_datasource_filter) | 数据源过滤配置 | 10 |
| platform | [jb_code_gen](#jb_code_gen) | 代码生成配置 | 165 |
| platform | [jb_code_gen_model_attr](#jb_code_gen_model_attr) | CodeGen模型详细设计 | 84 |
| platform | [jb_sensitive_word](#jb_sensitive_word) | 敏感词词库 | 3 |
| platform | [jb_user_extend](#jb_user_extend) | 用户扩展信息表 | 22 |
| ai | [tb_ai_app](#tb_ai_app) | AI应用 | 81 |
| ai | [tb_ai_app_link_function](#tb_ai_app_link_function) | AI应用绑定Functions | 3 |
| ai | [tb_ai_app_link_knowledge_base](#tb_ai_app_link_knowledge_base) | AI应用关联知识库 | 3 |
| ai | [tb_ai_app_message](#tb_ai_app_message) | AI应用消息记录 | 13 |
| ai | [tb_ai_app_session](#tb_ai_app_session) | AI应用下的session会话 | 6 |
| ai | [tb_ai_app_summary](#tb_ai_app_summary) | AI应用使用统计 | 5 |
| ai | [tb_ai_doc](#tb_ai_doc) | AI问答库 | 14 |
| ai | [tb_ai_file](#tb_ai_file) | AI文件 | 30 |
| ai | [tb_ai_function](#tb_ai_function) | AI_可调用Tools注册中心 | 21 |
| ai | [tb_ai_function_param](#tb_ai_function_param) | AI接口参数 | 8 |
| ai | [tb_ai_knowledge_base](#tb_ai_knowledge_base) | AI知识库 | 8 |
| ai | [tb_ai_page](#tb_ai_page) | AI功能页面 | 12 |
| ai | [tb_ai_file_tag](#tb_ai_file_tag) | AI文件标签 | 3 |
| ai | [tb_ai_llm](#tb_ai_llm) | LLM大模型配置 | 24 |
| ai | [tb_ai_embedding](#tb_ai_embedding) | Embedding模型资源配置 | 19 |
| ai | [tb_ai_vdb](#tb_ai_vdb) | 向量数据库资源配置 | 16 |
| custom_business | [jbc_custom_business](#jbc_custom_business) | 系统业务定义 | 8 |
| custom_business | [jbc_role_custom_table](#jbc_role_custom_table) | 角色自定义表格设置 | 13 |
| custom_business | [jbc_user_custom_business](#jbc_user_custom_business) | 用户针对一个业务自定义模板 | 7 |
| custom_business | [jbc_user_role_custom_table](#jbc_user_role_custom_table) | 用户自定义业务配置表格 | 14 |

## jb_application

API应用中心的应用APP（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(255) | 是 |  | 应用名称 |
| brief_info | varchar(255) |  | NULL | 应用简介 |
| app_id | varchar(255) |  | NULL | 应用ID |
| app_secret | varchar(255) |  | NULL | 应用密钥 |
| enable | char(1) | 是 | 0 | 是否启用 |
| create_time | datetime(0) |  | NULL | 创建时间 |
| update_time | datetime(0) |  | NULL | 更新时间 |
| user_id | bigint(20) |  | NULL | 创建用户ID |
| update_user_id | bigint(20) |  | NULL | 更新用户ID |
| type | int(11) | 是 |  | app类型 |
| need_check_sign | char(1) | 是 | 0 | 是否需要接口校验SIGN |
| is_inner | char(1) | 是 | 0 | 是否需要接口校验SIGN |
| link_target_id | bigint(20) |  | NULL | 关联目标ID |

索引：PRIMARY KEY (id)

## jb_dictionary

字典表（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 字典ID主键 |
| name | varchar(255) |  | NULL | 名称 |
| type_id | bigint(20) |  | NULL | 字典类型ID |
| pid | bigint(20) |  | NULL | 父类ID |
| sort_rank | int(11) |  | NULL | 排序 |
| sn | varchar(255) |  | NULL | 编号编码 |
| type_key | varchar(40) |  | NULL | 字典类型KEY |
| enable | char(1) | 是 | 1 | 是否启用 |
| is_build_in | char(1) | 是 | 0 | 是否内置 |

索引：PRIMARY KEY (id)

## jb_dictionary_type

字典类型（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(255) |  | NULL |  |
| mode_level | int(11) |  | NULL | 模式层级 |
| type_key | varchar(255) |  | NULL | 标识KEY |
| enable | char(1) | 是 | 1 | 是否启用 |
| is_build_in | char(1) | 是 | 0 | 是否内置 |

索引：PRIMARY KEY (id)

## jb_global_config

全局配置（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| config_key | varchar(255) | 是 |  | 配置KEY |
| config_value | varchar(255) |  | NULL | 配置项值 |
| create_time | datetime(0) | 是 |  | 创建时间 |
| user_id | bigint(20) |  | NULL | 创建用户ID |
| update_time | datetime(0) |  | NULL | 更新时间 |
| update_user_id | bigint(20) |  | NULL | 更新用户ID |
| name | varchar(255) |  | NULL |  |
| value_type | varchar(40) |  | NULL | 值类型 |
| type_key | varchar(40) |  | NULL | 类型key |
| type_id | bigint(20) |  | NULL | 类型ID |
| built_in | char(1) |  | 0 | 内置参数 |
| remark | varchar(255) |  | NULL | 备注 |

索引：PRIMARY KEY (id)

## jb_global_config_type

全局参数类型分组（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(40) | 是 |  | 类型名称 |
| sort_rank | int(11) |  | NULL | 序号 |
| type_key | varchar(40) | 是 |  | 类型KEY |
| built_in | char(1) |  | 0 | 内置 |

索引：PRIMARY KEY (id)

## jb_jbolt_file

文件库（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| local_path | varchar(255) |  | NULL | 保存物理地址 |
| local_url | varchar(255) |  | NULL | 本地可访问URL地址 |
| cdn_url | varchar(255) |  | NULL | 外部CDN地址 |
| create_time | datetime(0) |  | NULL | 创建时间 |
| user_id | bigint(20) |  | NULL | 用户ID |
| file_name | varchar(255) |  | NULL | 文件名 |
| file_type | int(11) |  | NULL | 文件类型 图片 附件 视频 音频 |
| file_size | int(11) |  | 0 | 文件大小 |
| file_suffix | varchar(255) |  | NULL | 后缀名 |

索引：PRIMARY KEY (id)

## jb_login_log

登录日志（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| user_id | bigint(20) |  | NULL | 用户ID |
| username | varchar(40) | 是 |  | 用户名 |
| login_ip | varchar(40) |  | NULL | IP地址 |
| login_time | datetime(0) | 是 |  | 登录时间 |
| login_state | int(11) | 是 |  | 登录状态 |
| is_browser | char(1) | 是 | 0 | 是否是浏览器访问 |
| browser_version | varchar(40) |  | NULL | 浏览器版本号 |
| browser_name | varchar(100) |  | NULL | 浏览器 |
| os_name | varchar(100) |  | NULL | 操作系统 |
| login_city | varchar(40) |  | NULL | 登录城市 |
| login_address | varchar(255) |  | NULL | 登录位置详情 |
| login_city_code | varchar(40) |  | NULL | 登录城市代码 |
| login_province | varchar(40) |  | NULL | 登录省份 |
| login_country | varchar(40) |  | NULL | 登录国家 |
| is_mobile | char(1) | 是 | 0 | 是否移动端 |
| os_platform_name | varchar(40) |  | NULL | 平台名称 |
| browser_engine_name | varchar(40) |  | NULL | 浏览器引擎名 |
| browser_engine_version | varchar(40) |  | NULL | 浏览器引擎版本 |
| login_address_latitude | varchar(40) |  | NULL | 登录地横坐标 |
| login_address_longitude | varchar(40) |  | NULL | 登录地纵坐标 |
| is_remote_login | char(1) | 是 | 0 | 是否为异地异常登录 |
| create_time | datetime(0) |  | NULL | 记录创建时间 |

索引：PRIMARY KEY (id)

## jb_online_user

在线用户（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| session_id | varchar(255) | 是 |  | 会话ID |
| user_id | bigint(20) | 是 |  | 用户ID |
| login_log_id | bigint(20) | 是 |  | 登录日志ID |
| login_time | datetime(0) |  | NULL | 登录时间 |
| update_time | datetime(0) |  | NULL | 更新时间 |
| expiration_time | datetime | 是 |  | 过期时间 |
| screen_locked | char(1) | 是 | 0 | 是否锁屏 |
| online_state | int(11) | 是 |  | 在线状态 |
| offline_time | datetime |  | NULL | 离线时间 |

索引：PRIMARY KEY (id)

## jb_permission

function定义（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| title | varchar(40) | 是 |  |  |
| pid | bigint(20) | 是 | 0 |  |
| url | varchar(255) |  | NULL | 地址 |
| icons | varchar(40) |  | NULL | 图标 |
| sort_rank | int(11) |  | NULL | 排序 |
| permission_level | int(11) |  | NULL | 层级 |
| permission_key | varchar(255) |  | NULL | 权限资源KEY |
| is_menu | char(1) | 是 | 0 | 是否是菜单 |
| is_target_blank | char(1) | 是 | 0 | 是否新窗口打开 |
| is_system_admin_default | char(1) | 是 | 0 | 是否系统超级管理员默认拥有的权限 |
| open_type | int(11) |  | 1 | 打开类型 1 默认 2 iframe 3 dialog |
| open_option | varchar(255) |  | NULL | 组件属性json |
| of_module | int(11) | 是 | 1 | 哪个模块 |
| of_module_link | varchar(40) |  | NULL | 具体指向关联 |

索引：PRIMARY KEY (id)

## jb_remote_login_log

异地登录日志记录（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| last_login_country | varchar(40) |  | NULL | 最近一次登录国家 |
| last_login_province | varchar(40) |  | NULL | 最近一次登录省份 |
| last_login_city | varchar(40) |  | NULL | 最近一次登录城市 |
| last_login_time | datetime(0) |  | NULL | 最近一次登录时间 |
| login_country | varchar(40) |  | NULL | 新登录国家 |
| login_province | varchar(40) |  | NULL | 新登录省份 |
| login_city | varchar(40) |  | NULL | 新登录城市 |
| login_time | datetime(0) |  | NULL | 新登录时间 |
| user_id | bigint(20) |  | NULL | 登录用户ID |
| username | varchar(40) |  | NULL | 登录用户名 |
| is_new | char(1) | 是 | 0 | 是否为最新一次 |
| last_login_ip | varchar(40) |  | NULL | 最近一次登录IP |
| login_ip | varchar(40) |  | NULL | 新登录IP |
| create_time | datetime(0) |  | NULL | 记录创建时间 |

索引：PRIMARY KEY (id)

## jb_role

角色表（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(20) | 是 |  | 名称 |
| sn | varchar(40) |  |  | 编码 |
| pid | bigint(20) |  | 0 | 父级角色ID |

索引：PRIMARY KEY (id)

## jb_role_permission

角色功能列表（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| role_id | bigint(20) | 是 |  | 角色ID |
| permission_id | bigint(20) | 是 |  | 权限ID |

索引：PRIMARY KEY (id)

## jb_system_log

系统操作日志表（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| title | varchar(255) |  | NULL | 标题 |
| type | int(11) |  | NULL | 操作类型 删除 更新 新增 |
| url | varchar(255) |  | NULL | 连接对象详情地址 |
| user_id | bigint(20) |  | NULL | 操作人ID |
| user_name | varchar(255) |  | NULL | 操作人姓名 |
| target_type | int(11) |  | NULL | 操作对象类型 |
| create_time | datetime(0) |  | NULL | 记录创建时间 |
| target_id | bigint(20) |  | NULL | 操作对象ID |
| open_type | int(11) |  | NULL | 打开类型URL还是Dialog |

索引：PRIMARY KEY (id)

## jb_dept

组织机构（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(40) | 是 |  | 名称 |
| full_name | varchar(40) | 是 |  | 全称 |
| pid | bigint(20) |  | NULL | 父级ID |
| dept_path | varchar(255) |  | NULL | 部门路径 |
| type | varchar(40) | 是 |  | 类型 |
| leader | varchar(40) |  | NULL | 负责人 |
| phone | varchar(40) |  | NULL | 联系电话 |
| email | varchar(100) |  | NULL | 电子邮箱 |
| address | varchar(100) |  | NULL | 联系地址 |
| zipcode | varchar(40) |  | NULL | 邮政编码 |
| remark | varchar(255) |  | NULL | 备注信息 |
| sort_rank | int(11) |  | NULL | 顺序 |
| sn | varchar(40) | 是 |  | 机构代码 |
| enable | char(1) | 是 | 1 | 启用/禁用 |
| update_time | datetime |  | NULL | 最后更新时间 |
| create_time | datetime |  | NULL | 创建时间 |

索引：PRIMARY KEY (id)

## jb_post

岗位（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(40) | 是 |  | 岗位名称 |
| type | varchar(40) | 是 |  | 岗位类型 |
| sort_rank | int(11) |  | NULL | 顺序 |
| remark | varchar(255) |  | NULL | 备注信息 |
| sn | varchar(40) | 是 |  | 编码 |
| enable | char(1) |  | 1 | 启用/禁用 |

索引：PRIMARY KEY (id)

## jb_user

用户登录账户表（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| username | varchar(40) | 是 |  | 用户名 |
| password | varchar(128) | 是 |  | 密码 |
| name | varchar(20) | 是 |  | 姓名 |
| sn | varchar(20) |  | NULL | 工号 |
| avatar | varchar(255) |  | NULL | 头像 |
| create_time | datetime(0) |  | NULL | 记录创建时间 |
| phone | varchar(40) |  | NULL | 手机号 |
| email | varchar(100) |  | NULL | 电子邮箱 |
| enable | char(1) | 是 | 0 | 启用 |
| sex | int(11) |  | 0 | 性别 |
| age | int(11) |  | 0 | 年龄 |
| pinyin | varchar(255) |  | NULL | 拼音码 |
| roles | varchar(255) |  | NULL | 角色 一对多 |
| is_system_admin | char(1) | 是 | 0 | 是否系统超级管理员 |
| create_user_id | bigint(20) |  | NULL | 创建人 |
| pwd_salt | varchar(128) |  | NULL | 密码盐值 |
| login_ip | varchar(255) |  | NULL | 最后登录IP |
| login_time | datetime(0) |  | NULL | 最后登录时间 |
| login_city | varchar(40) |  | NULL | 最后登录城市 |
| login_province | varchar(40) |  | NULL | 最后登录省份 |
| login_country | varchar(40) |  | NULL | 最后登录国家 |
| is_remote_login | char(1) | 是 | 0 | 是否异地登录 |
| dept_id | bigint(20) |  | NULL | 部门ID |
| dept_path | varchar(255) |  | NULL | 部门路径 |
| posts | varchar(255) |  | NULL | 岗位IDS |
| update_time | datetime |  | NULL | 更新时间 |
| update_user_id | bigint(20) |  | NULL | 更新人ID |
| last_pwd_update_time | datetime |  | NULL | 最近一次密码修改时间 |
| of_module | int(11) | 是 | 1 | 哪个模块 |
| of_module_link | varchar(40) |  | NULL | 具体指向关联 |

索引：PRIMARY KEY (id)

## jb_user_config

用户系统样式自定义设置表（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(255) | 是 |  | 配置名 |
| config_key | varchar(255) | 是 |  | 配置KEY |
| config_value | varchar(255) | 是 |  | 配置值 |
| user_id | bigint(20) | 是 |  | 用户ID |
| create_time | datetime(0) |  | NULL | 创建时间 |
| update_time | datetime(0) |  | NULL | 更新时间 |
| value_type | varchar(40) |  | NULL | 取值类型 |

索引：PRIMARY KEY (id)

## jb_wechat_article

微信图文信息（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| title | varchar(255) |  | NULL |  |
| content | varchar(255) |  | NULL |  |
| create_time | datetime(0) |  | NULL | 创建时间 |
| user_id | bigint(20) |  | NULL | 用户 ID |
| update_time | datetime(0) |  | NULL | 更新时间 |
| update_user_id | bigint(20) |  | NULL | 更新用户 ID |
| brief_info | varchar(120) |  | NULL | 文章摘要 |
| poster | varchar(255) |  | NULL |  |
| view_count | int(11) |  | NULL | 阅读数 |
| media_id | varchar(255) |  | NULL | 微信素材 ID |
| mp_id | bigint(20) |  | NULL | 微信 ID |
| url | varchar(255) |  | NULL | 图文链接地址 |
| type | int(11) |  | NULL | 本地图文 公众号图文素材 外部图文 |

索引：PRIMARY KEY (id)

## jb_wechat_autoreply

微信公众号自动回复规则（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| mp_id | bigint(20) |  | NULL | 微信 ID |
| type | int(11) |  | 0 | 类型 关注回复 无内容时回复 关键词回复 |
| name | varchar(40) |  | NULL | 规则名称 |
| reply_type | int(11) |  | NULL | 回复类型 全部还是随机一条 |
| create_time | datetime(0) |  | NULL | 记录创建时间 |
| user_id | bigint(20) |  | NULL | 用户 ID |
| enable | char(1) | 是 | 0 | 是否启用 |

索引：PRIMARY KEY (id)

## jb_wechat_config

微信公众号配置表（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| mp_id | bigint(20) |  | NULL | 微信 ID |
| config_key | varchar(255) |  | NULL | 配置key |
| config_value | varchar(255) |  | NULL | 配置值 |
| name | varchar(255) |  | NULL | 配置项名称 |
| type | int(11) |  | NULL | 配置类型 |

索引：PRIMARY KEY (id)

## jb_wechat_keywords

微信关键词回复中的关键词定义（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| mp_id | bigint(20) |  | NULL | 微信 ID |
| name | varchar(40) |  | NULL |  |
| type | int(11) |  | 0 | 模糊 全匹配 |
| auto_reply_id | bigint(20) |  | NULL | 回复规则ID |

索引：PRIMARY KEY (id)

## jb_wechat_media

微信公众平台素材库同步管理（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| title | varchar(500) |  | NULL | 标题 |
| digest | text |  | NULL | 描述 |
| type | varchar(255) |  | NULL | 类型 image video voice news |
| mp_id | bigint(20) |  | NULL | 微信 ID |
| media_id | varchar(255) |  | NULL | 微信素材ID |
| thumb_media_id | varchar(255) |  | NULL | 封面图ID |
| content_source_url | varchar(255) |  | NULL | 原文地址 |
| url | varchar(255) |  | NULL | 访问地址 |
| author | varchar(255) |  | NULL | 图文作者 |
| update_time | datetime(0) |  | NULL | 更新时间 |
| server_url | varchar(255) |  | NULL | 存服务器URL |

索引：PRIMARY KEY (id)

## jb_wechat_menu

微信菜单（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| mp_id | bigint(20) |  | NULL | 微信 ID |
| type | varchar(40) |  | NULL |  |
| name | varchar(40) |  | NULL |  |
| pid | bigint(20) |  | 0 |  |
| sort_rank | int(11) |  | NULL | 排序 |
| value | varchar(255) |  | NULL |  |
| app_id | varchar(255) |  | NULL | 微信小程序APPID |
| page_path | varchar(255) |  | NULL | 微信小程序页面地址 |

索引：PRIMARY KEY (id)

## jb_wechat_mpinfo

微信公众号与小程序（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(255) |  | NULL | 平台名称 |
| logo | varchar(255) |  | NULL | 头像LOGO |
| create_time | datetime(0) |  | NULL | 创建时间 |
| user_id | bigint(20) |  | NULL | 用户ID |
| enable | char(1) | 是 | 0 | 是否启用 |
| update_time | datetime(0) |  | NULL | 创建时间 |
| update_user_id | bigint(20) |  | NULL | 更新人ID |
| brief_info | varchar(255) |  | NULL | 简介 |
| remark | varchar(255) |  | NULL | 备注 |
| type | int(11) |  | NULL | 类型 订阅号、服务号、小程序、企业号 |
| is_authenticated | char(1) | 是 | 0 | 是否已认证 |
| subject_type | int(11) |  | NULL | 申请认证主体的类型 个人还是企业 |
| wechat_id | varchar(255) |  | NULL | 微信号 |
| qrcode | varchar(255) |  | NULL | 二维码 |
| link_app_id | bigint(20) |  | NULL | 关联应用ID |

索引：PRIMARY KEY (id)

## jb_wechat_reply_content

自动回复内容（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| type | varchar(40) | 是 | 0 | 类型 图文 文字 图片 音频 视频 |
| title | varchar(64) |  | NULL | 标题 |
| content | mediumtext |  |  | 内容 |
| poster | varchar(255) |  | NULL | 封面 |
| url | varchar(255) |  | NULL | 地址 |
| auto_reply_id | bigint(20) |  | NULL | 回复规则ID |
| create_time | datetime(0) |  | NULL | 创建时间 |
| user_id | bigint(20) |  | NULL | 用户 ID |
| media_id | varchar(255) |  | NULL | 关联数据 |
| mp_id | bigint(20) |  | NULL | 微信 ID |
| sort_rank | int(11) |  | NULL | 排序 |
| enable | char(1) | 是 | 0 | 是否启用 |

索引：PRIMARY KEY (id)

## jb_wechat_user

微信用户信息_模板表（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| nickname | varchar(255) |  | NULL | 用户昵称 |
| open_id | varchar(255) |  | NULL | openId |
| union_id | varchar(255) |  | NULL | unionID |
| sex | int(11) |  | 0 | 性别 1男 2女 0 未知 |
| language | varchar(255) |  | NULL | 语言 |
| subscribe | char(1) | 是 | 0 | 是否已关注 |
| country | varchar(255) |  | NULL | 国家 |
| province | varchar(255) |  | NULL | 省 |
| city | varchar(255) |  | NULL | 城市 |
| head_img_url | varchar(255) |  | NULL | 头像 |
| subscribe_time | datetime(0) |  | NULL | 关注时间 |
| remark | varchar(255) |  | NULL | 备注 |
| group_id | int(11) |  | NULL | 分组 |
| tag_ids | varchar(255) |  | NULL | 标签 |
| subscribe_scene | varchar(255) |  | NULL | 关注渠道 |
| qr_scene | int(11) |  | NULL | 二维码场景-开发者自定义 |
| qr_scene_str | varchar(255) |  | NULL | 二维码扫码场景描述-开发者自定义 |
| realname | varchar(255) |  | NULL | 真实姓名 |
| phone | varchar(255) |  | NULL | 手机号 |
| phone_country_code | varchar(40) |  | NULL | 手机号国家代码 |
| check_code | varchar(255) |  | NULL | 手机验证码 |
| is_checked | char(1) | 是 | 0 | 是否已验证 |
| source | int(11) |  | NULL | 来源 小程序还是公众平台 |
| session_key | varchar(255) |  | NULL | 小程序登录SessionKey |
| enable | char(1) | 是 | 0 | 禁用访问 |
| create_time | datetime(0) |  | NULL | 创建时间 |
| checked_time | datetime(0) |  | NULL | 验证绑定手机号时间 |
| mp_id | bigint(20) |  | NULL | 所属公众平台ID |
| weixin | varchar(255) |  | NULL | 微信号 |
| update_time | datetime(0) |  | NULL | 更新时间 |
| last_login_time | datetime(0) |  | NULL | 最后登录时间 |
| first_auth_time | datetime(0) |  | NULL | 首次授权时间 |
| last_auth_time | datetime(0) |  | NULL | 最后一次更新授权时间 |
| first_login_time | datetime(0) |  | NULL | 首次登录时间 |
| bind_user | varchar(255) |  | NULL | 绑定其他用户信息 |

索引：PRIMARY KEY (id)

## jb_topnav

顶部导航（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(40) |  | NULL | 名称 |
| icon | varchar(40) |  | NULL | 图标 |
| enable | char(1) |  | 0 | 是否启用 |
| sort_rank | int(11) |  | NULL | 排序 |

索引：PRIMARY KEY (id)

## jb_topnav_menu

顶部菜单对应左侧一级导航中间表（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| topnav_id | bigint(20) |  | NULL | 顶部导航ID |
| permission_id | bigint(20) |  | NULL | 菜单资源ID |

索引：PRIMARY KEY (id)

## jb_todo

待办事项（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| title | varchar(255) | 是 |  | 标题 |
| content | text |  |  | 详情内容 |
| user_id | bigint(20) | 是 |  | 所属用户 |
| state | int(11) | 是 |  | 状态 |
| specified_finish_time | datetime | 是 |  | 规定完成时间 |
| type | int(11) | 是 |  | 类型 链接还是内容 还是都有 |
| url | varchar(512) |  | NULL | 链接 |
| priority_level | int(11) | 是 |  | 优先级 |
| real_finish_time | datetime |  | NULL | 完成时间 |
| cancel_time | datetime |  | NULL | 取消时间 |
| create_user_id | bigint(20) | 是 |  | 创建人ID |
| update_user_id | bigint(20) |  | NULL | 更新人ID |
| create_time | datetime | 是 |  | 创建时间 |
| update_time | datetime | 是 |  | 更新时间 |
| source_msg_id | varchar(64) |  | NULL | 第三方系统消息主键 |
| source_request_id | varchar(64) |  | NULL | 第三方系统流程主键 |
| source_url | varchar(512) |  | NULL | 第三方url |
| send_user_key | varchar(64) |  | NULL | 发送人第三方系统标识 |
| receive_user_key | varchar(64) |  | NULL | 接收人第三方系统标识 |
| source_sys | varchar(64) |  | NULL | 来源系统 |
| is_readed | char(1) | 是 | 0 | 是否已读 |

索引：PRIMARY KEY (id)

## jb_sys_notice

系统通知（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| title | varchar(255) | 是 |  | 标题 |
| content | text | 是 |  | 消息内容 |
| type | int(11) | 是 |  | 通知类型 |
| priority_level | int(11) | 是 |  | 优先级 |
| read_count | int(11) |  | 0 | 已读人数 |
| create_time | datetime | 是 |  | 创建时间 |
| update_time | datetime |  | NULL | 更新时间 |
| create_user_id | bigint(20) |  | NULL | 创建人 |
| update_user_id | bigint(20) |  | NULL | 更新人 |
| receiver_type | int(11) |  | NULL | 接收人类型 |
| receiver_value | text |  |  | 接收人值 |
| files | varchar(255) |  | NULL | 附件 |
| del_flag | char(1) |  | 0 | 删除标志 |

索引：PRIMARY KEY (id)

## jb_sys_notice_reader

通知阅读用户关系表（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| sys_notice_id | bigint(20) | 是 |  | 通知ID |
| user_id | bigint(20) | 是 |  | 用户ID |
| create_time | datetime | 是 |  | 创建时间 |

索引：PRIMARY KEY (id)

## jb_private_message

内部私信（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| content | text | 是 |  | 私信内容 |
| create_time | datetime | 是 |  | 发送时间 |
| from_user_id | bigint(20) | 是 |  | 发信人 |
| to_user_id | bigint(20) | 是 |  | 收信人 |

索引：PRIMARY KEY (id)

## jb_hiprint_tpl

hiprint模版（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(40) | 是 |  | 模板名称 |
| sn | varchar(40) | 是 |  | 模板编码 |
| content | text | 是 |  | 模板内容 |
| remark | varchar(255) |  | NULL | 备注 |
| create_time | datetime | 是 |  | 创建时间 |
| update_time | datetime | 是 |  | 更新时间 |
| create_user_id | bigint(20) | 是 |  | 创建人 |
| update_user_id | bigint(20) | 是 |  | 更新人 |
| test_api_url | varchar(255) |  | NULL | 测试API地址 |
| test_json_data | text |  |  | 测试JSON数据 |

索引：PRIMARY KEY (id)

## jb_qiniu

七牛账号表（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(255) | 是 |  | 账号 |
| sn | varchar(40) | 是 |  | 编号SN |
| ak | varchar(64) | 是 |  | AK |
| sk | varchar(64) | 是 |  | SK |
| type | int(11) | 是 |  | 账号类型 |
| bucket_count | int(11) | 是 | 0 | 空间个数 |
| is_default | char(1) | 是 | 0 | 是否默认 |
| enable | char(1) | 是 | 0 | 是否启用 |
| remark | varchar(255) |  | NULL | 备注 |
| create_time | datetime | 是 |  | 创建时间 |
| update_time | datetime | 是 |  | 更新时间 |
| create_user_id | bigint(20) | 是 |  | 创建人 |
| update_user_id | bigint(20) | 是 |  | 更新人 |

索引：PRIMARY KEY (id)

## jb_qiniu_bucket

七牛bucket配置（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(64) | 是 |  | bucket名称 |
| sn | varchar(40) | 是 |  | 编码 |
| qiniu_id | bigint(20) | 是 |  | 所属七牛账号 |
| domain_url | varchar(255) |  | NULL | 绑定域名 |
| callback_url | varchar(255) |  | NULL | 回调地址 |
| callback_body | varchar(255) |  | NULL | 回调body定义 |
| callback_body_type | varchar(64) |  | NULL | 回调Body类型 |
| expires | int(11) |  | NULL | 有效期(秒) |
| is_default | char(1) | 是 |  | 是否默认 |
| enable | char(1) | 是 |  | 是否启用 |
| remark | varchar(255) |  | NULL | 备注 |
| create_user_id | bigint(20) | 是 |  | 创建人 |
| create_time | datetime | 是 |  | 创建时间 |
| update_user_id | bigint(20) | 是 |  | 更新人 |
| update_time | datetime |  | NULL | 更新时间 |
| region | varchar(20) | 是 |  | 地区 |

索引：PRIMARY KEY (id)

## jb_datasource_filter

数据源过滤配置（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(100) | 是 |  | 名称 |
| config_name | varchar(100) | 是 |  | 配置名称 |
| table_name_filter | text |  |  | 不需要生成的表名 |
| table_name_contains | text |  |  | 需要排除包含字符 |
| table_name_patterns | text |  |  | 需要排除符合正则的 |
| create_user_id | bigint | 是 |  | 创建人 |
| update_user_id | bigint | 是 |  | 更新人 |
| create_time | datetime | 是 |  | 创建时间 |
| update_time | datetime | 是 |  | 更新时间 |

索引：PRIMARY KEY (id)

## jb_code_gen

代码生成配置（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| pid | bigint(20) |  | 0 | 父ID |
| project_path | varchar(255) | 是 |  | 项目根路径 |
| is_sub_table | char(1) | 是 | 0 | 是否为子表 |
| sort_rank | int(11) | 是 | 1 | 子表的顺序 |
| type | varchar(40) | 是 |  | 模块类型 |
| main_table_name | varchar(150) | 是 |  | 主表名 |
| datasource_name | varchar(100) | 是 |  | 数据源 |
| datasource_remark | varchar(40) | 是 |  | 数据源说明 |
| database_type | varchar(40) | 是 |  | 数据库类型 |
| is_main_datasource | char(1) | 是 | 0 | 是否为主数据源 |
| main_table_pkey | varchar(64) |  | id | 主表主键 |
| table_remove_prefix | varchar(40) |  | NULL | 数据表删除前缀 |
| main_table_idgenmode | varchar(40) | 是 |  | 主表主键策略 |
| version_sn | int(11) | 是 |  | 版本序号 |
| main_table_remark | varchar(100) |  | NULL | 表备注 |
| author | varchar(100) | 是 |  | 功能作者 |
| style | int(11) | 是 |  | 样式类型 |
| is_crud | char(1) | 是 | 0 | 是否CRUD |
| is_editable | char(1) | 是 | 0 | 是否可编辑表格 |
| is_tree_table | char(1) | 是 | 0 | 是否为树表 |
| is_check_can_delete | char(1) | 是 | 0 | 检测是否可以刪除数据 |
| is_check_can_toggle | char(1) | 是 | 0 | 检测是否可以toggle数据 |
| is_check_can_recover | char(1) | 是 | 0 | 检测是否可以recover数据 |
| editable_submit_type | varchar(20) |  | NULL | 可编辑提交方式 |
| state | int(11) | 是 |  | 生成状态 |
| sub_table_count | int(11) |  | NULL | 子表数 |
| topnav_id | bigint(20) |  | NULL | 顶部导航 |
| permission_id | bigint(20) |  | NULL | 关联权限 |
| roles | varchar(255) |  | NULL | 可访问角色 |
| create_user_id | bigint(20) | 是 |  | 创建人ID |
| create_time | datetime | 是 |  | 创建时间 |
| update_user_id | bigint(20) | 是 |  | 更新人ID |
| update_time | datetime | 是 |  | 更新时间 |
| gen_user_id | bigint(20) |  | NULL | 创建后执行生成人ID |
| gen_time | datetime |  | NULL | 创建后执行生成时间 |
| model_name | varchar(64) |  | NULL | modelName |
| base_model_name | varchar(64) |  | NULL | baseModelName |
| remark | varchar(255) |  | NULL | 备注 |
| is_auto_cache | char(1) | 是 | 0 | 是否启用autoCache |
| is_id_cache | char(1) | 是 | 0 | 是否启用idCache |
| is_key_cache | char(1) | 是 | 0 | 是否启用keyCache |
| key_cache_column | varchar(40) |  | NULL | keyCache指定Column |
| key_cache_bind_column | varchar(40) |  | NULL | keyCache指定bindColumn |
| controller_name | varchar(100) | 是 |  | Controller Name |
| controller_path | varchar(100) | 是 |  | controller path |
| main_java_package | varchar(100) | 是 |  | java主包pacakge |
| service_name | varchar(100) | 是 |  | Service Name |
| controller_package | varchar(255) | 是 |  | Controller代码包 |
| service_package | varchar(255) | 是 |  | Service代码包 |
| index_html_page_icon | varchar(60) |  | NULL | index.html标题icon |
| index_html_page_title | varchar(40) |  | NULL | index.html页面标题 |
| model_package | varchar(255) |  | NULL | model 所属package |
| html_view_path | varchar(255) |  | NULL | html view path |
| routes_scan_package | varchar(255) |  | NULL | 路由扫描包 |
| is_gen_model | char(1) | 是 | 1 | 是否需要生成Model |
| is_table_use_record | char(1) | 是 | 0 | 表格是否使用record |
| is_table_record_camel_case | char(1) | 是 | 0 | 表格列名用驼峰的attrName |
| is_import_excel | char(1) | 是 | 0 | 是否支持Excel导入 |
| is_export_excel | char(1) | 是 | 0 | 是否支持Excel导出 |
| is_export_excel_by_checked_ids | char(1) | 是 | 0 | 是否启用 导出选中行功能 |
| is_export_excel_by_form | char(1) | 是 | 1 | 是否启用导出表单查询结果功能 |
| is_export_excel_all | char(1) | 是 | 0 | 是否启用导出所有数据 |
| is_copy_to_excel | char(1) | 是 | 0 | 是否支持表格复制到excel |
| is_copy_from_excel | char(1) | 是 | 0 | 是否支持从excel复制到可编辑表格 |
| is_toolbar | char(1) | 是 | 0 | 是否使用toolbar模式 |
| is_headbox | char(1) | 是 | 0 | 是否使用headbox |
| is_leftbox | char(1) | 是 | 0 | 是否使用leftBox |
| is_rightbox | char(1) | 是 | 0 | 是否使用rightBox |
| is_footbox | char(1) | 是 | 0 | 是否使用footbox |
| is_paginate | char(1) | 是 | 1 | 是否分页查询 |
| is_table_sortable_move | char(1) | 是 | 0 | 是否开启移动排序功能 |
| leftbox_width | int(11) |  | 220 | leftbox width |
| rightbox_width | int(11) |  | 220 | right width |
| is_headbox_height_auto | char(1) | 是 | 1 | headbox高度自动 |
| is_footbox_height_auto | char(1) | 是 | 1 | footbox高度自动 |
| headbox_height | int(11) |  | 60 | headbox height |
| footbox_height | int(11) |  | 220 | footbox height |
| is_leftbox_footer | char(1) | 是 | 1 | 是否启用leftbox的footer |
| is_rightbox_footer | char(1) | 是 | 1 | 是否启用rightbox的footer |
| leftbox_footer_button_count | int(11) |  | NULL | leftbox footer button count |
| rightbox_footer_button_count | int(11) |  | NULL | rightbox footer button count |
| leftbox_title | varchar(40) |  | leftbox | leftbox title |
| leftbox_icon | varchar(60) |  | fa fa-cog | leftbox icon |
| rightbox_title | varchar(40) |  | rightbox | rightbox title |
| rightbox_icon | varchar(60) |  | fa fa-cog | rightbox icon |
| is_show_optcol_sort | char(1) | 是 | 0 | 是否开启操作列排序功能 |
| is_show_optcol_edit | char(1) | 是 | 0 | 是否开启操作列显示编辑按钮 |
| is_show_optcol_del | char(1) | 是 | 0 | 是否开启操作列显示删除按钮 |
| is_show_optcol | char(1) | 是 | 1 | 是否显示操作列 |
| is_show_optcol_recover | char(1) | 是 | 0 | 是否显示操作列的恢复按钮 |
| default_sort_column | varchar(40) |  | NULL | 默认排序字段 |
| default_sort_type | varchar(40) |  | NULL | 默认排序方式 |
| table_optcol_width | int(11) | 是 | 80 | 长度 |
| is_table_column_resize | char(1) | 是 | 1 | 表格是否开启调整列宽功能 |
| is_table_prepend_column | char(1) | 是 | 0 | 是否增加填充列 |
| table_prepend_column_type | varchar(40) |  | NULL | 表格chechbox radio配置类型 |
| table_prepend_column_index | int(11) | 是 | 1 | 填充列到第几列 |
| is_table_prepend_column_linkparent | char(1) | 是 | 0 | 填充列linkparent |
| is_table_prepend_column_linkson | char(1) | 是 | 0 | 填充列linkson |
| table_prepend_column_rowspan | int(11) | 是 | 1 | 填充列表头是几行rowspan |
| is_table_prepend_column_click_active | char(1) | 是 | 0 | 是否点击行就切换列填充组件选中状态 |
| table_fixed_column_left | varchar(40) |  | NULL | 左侧固定列 |
| table_fixed_column_right | varchar(40) |  | NULL | 右侧固定列 |
| table_pagesize_options | varchar(255) |  | NULL | 分页pagesize自定义设置 |
| table_width_assign | varchar(40) |  | NULL | 表格宽度自定义值 |
| table_width | varchar(40) | 是 | fill | 表格宽度 |
| table_height_assign | varchar(40) |  | NULL | 表格高度自定义值 |
| table_height | varchar(40) | 是 | fill | 表格高度 |
| table_default_sort_column | varchar(60) |  | id | 表格默认排序字段 |
| table_default_sort_type | varchar(40) |  | desc | 表格默认排序类型 |
| is_keywords_search | char(1) | 是 | 1 | 是否开启关键词查询 |
| keywords_match_columns | varchar(255) |  | NULL | 关键词匹配列 |
| toolbar_extra_button_size | int(11) |  | NULL | toolbar 额外预留按钮个数 |
| is_deleted | char(1) | 是 | 0 | 删除标识 |
| form_column_size | int(11) | 是 | 1 | 表单分几列 |
| is_form_group_row | char(1) | 是 | 1 | 表单form-group风格 左右还是上下 |
| form_column_proportion | varchar(40) |  | NULL | 表单分多列 比例值 |
| form_column_direction | varchar(10) |  | v | 表单列排布方向 横向还是纵向 |
| form_group_proportion | varchar(40) |  | 2:10 | form-group row状态下的比例 |
| is_view_use_path | char(1) | 是 | 1 | 是否启用Path注解路由 |
| view_layout | varchar(255) | 是 |  | 使用布局器 |
| is_need_new_route | char(1) | 是 | 0 | 是否需要创建新的路由配置类 |
| routes_class_name | varchar(255) |  | NULL | 哪个路由配置类 |
| is_need_admin_interceptor | char(1) | 是 | 1 | 是否需要后台管理权限拦截器 |
| extra_interceptor_class_name | varchar(255) |  | NULL | 额外配置的拦截器 |
| is_table_multi_conditions_mode | char(1) | 是 | 0 | 表格查询条件是否启用高级多条件模式 |
| is_table_multi_conditions_default_hide | char(1) | 是 | 1 | 表格查询高级模式 是否隐藏条件 默认隐藏 |
| is_table_multi_conditions_btn_show_title | char(1) | 是 | 1 | 表格高级查询条件切换按钮是否显示标题 |
| is_toolbar_add_btn | char(1) | 是 | 1 | 表格toolbar上启用添加按钮 |
| is_toolbar_edit_btn | char(1) | 是 | 1 | 表格toolbar上启用编辑按钮 |
| is_toolbar_del_btn | char(1) | 是 | 1 | 表格toolbar上启用删除按钮 |
| is_toolbar_recover_btn | char(1) | 是 | 0 | 表格toolbar上启用恢复按钮 当有is_deleted时 |
| is_toolbar_refresh_btn | char(1) | 是 | 1 | 表格tolbar上启用刷新按钮 |
| is_page_title_add_btn | char(1) | 是 | 1 | 是否启用pageTitle上的添加按钮 |
| is_page_title_refresh_btn | char(1) | 是 | 1 | 是否启用pageTitle上的刷新按钮 |
| is_page_title_init_rank_btn | char(1) | 是 | 0 | 是否启用pageTitle上的初始化顺序按钮 |
| is_project_system_log | char(1) | 是 | 0 | 是否启用systemLog日志 |
| project_system_log_target_type_text | varchar(20) |  | NULL | 系统日志text |
| project_system_log_target_type_value | varchar(10) |  | NULL | 系统日志value值 |
| project_system_log_target_type_key_name | varchar(40) |  | NULL | 系统日志KeyName |
| form_dialog_area | varchar(20) |  | 980,700 | form表单的dialog的area属性 长宽 |
| is_base_model_gen_col_constant | char(1) | 是 | 1 | 是否在baseModel中生成字段常量 |
| is_base_model_gen_col_constant_to_uppercase | char(1) | 是 | 1 | 是否在baseModel中生成的字段常量 名称转大写 |
| thead_bg_color | varchar(20) |  | NULL | 表头背景色 |
| thead_fr_color | varchar(20) |  | NULL | 表头文字颜色 |
| is_table_row_click_active | char(1) | 是 | 0 | 是否表格选中定色 |
| active_tr_bg_color | varchar(20) |  | NULL | 选中行背景色 |
| active_tr_fr_color | varchar(20) |  | NULL | 选中行文字颜色 |
| is_gen_cache_util_class | char(1) | 是 | 0 | 是否生成缓存工具类 |
| cache_class_name | varchar(100) |  | NULL | 缓存工具类名 |
| cache_class_package | varchar(200) |  | NULL | 缓存工具类包 |
| is_cache_get_name | char(1) | 是 | 0 | 是否生成getName |
| is_cache_get_sn | char(1) | 是 | 0 | 是否生成getSn |
| is_cache_get_name_by_sn | char(1) | 是 | 0 | 是否生成getNameBySn(sn) |
| is_cache_get_by_sn | char(1) | 是 | 0 | 是否生成getBySn(sn) |
| model_title | varchar(40) |  | NULL | 模型名称 |
| is_gen_options_action | char(1) | 是 | 0 | 是否生成options接口 |
| is_return_option_type | char(1) |  | 1 | 是否返回Option类型 |
| options_text_column | varchar(40) |  | name | options接口text用哪一列 |
| options_value_column | varchar(40) |  | id | options接口value用哪一列 |
| is_gen_autocomplete_action | char(1) | 是 | 0 | 是否生成Autocomplete接口 |
| autocomplete_limit | int(10) | 是 | 20 | autocomplete接口limit |
| autocomplete_match_columns | varchar(255) |  | NULL | autocomplete接口匹配字段 |
| table_opt_sort_column | varchar(60) |  | NULL | 表格排序操作用哪个column |

索引：PRIMARY KEY (id)

## jb_code_gen_model_attr

CodeGen模型详细设计（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| code_gen_id | bigint(20) | 是 |  | 所属codeGen |
| col_name | varchar(40) | 是 |  | 列名 |
| attr_name | varchar(40) | 是 |  | 属性名 |
| java_type | varchar(40) | 是 |  | 属性类型 |
| attr_length | int(11) | 是 | 20 | 属性长度 |
| attr_fixed | int(11) |  | 0 | 属性小数点 |
| attr_default_value | varchar(100) |  | NULL | 默认值 |
| sort_rank | int(11) | 是 | 1 | 数据表内默认顺序 |
| sort_rank_intable | int(11) | 是 | 1 | 表格中的排序 |
| sort_rank_inform | int(11) | 是 | 1 | 表单中的排序 |
| sort_rank_insearch | int(11) | 是 | 1 | 查询条件中的顺序 |
| is_pkey | char(1) | 是 | 0 | 是否主键 |
| is_required | char(1) | 是 | 0 | 是否必填 |
| is_search_required | char(1) | 是 | 0 | 作为查询条件是否必填 |
| data_rule_for_search | varchar(255) |  | NULL | 查询条件必填校验规则 |
| data_tips_for_search | varchar(255) |  | NULL | 查询条件不符合校验的提示信息 |
| form_label | varchar(255) |  | NULL | 表单单显示文本 |
| placeholder | varchar(40) |  | NULL | 表单placeholder |
| table_label | varchar(40) |  | NULL | 表格中显示文本 |
| search_form_label | varchar(40) |  | NULL | 查询表单提示文本 |
| remark | varchar(255) |  | NULL | 备注 |
| is_keywords_column | char(1) | 是 | 0 | 是否为关键词查询列 |
| is_form_ele | char(1) | 是 | 1 | 是否表单可编辑元素 |
| is_table_col | char(1) | 是 | 1 | 是否表格列 |
| is_table_switchbtn | char(1) | 是 | 0 | 是否为表格switchbtn |
| table_col_width | int(11) | 是 | 100 | 列宽 |
| is_need_fixed_width | char(1) | 是 | 1 | 是否固定宽度 |
| is_search_ele | char(1) | 是 | 0 | 是否检索条件 |
| is_search_hidden | char(1) | 是 | 0 | 是否为检索隐藏条件 |
| col_format | varchar(255) |  | NULL | 格式化操作值 |
| search_ui_type | varchar(40) |  | NULL | 查询用ui 组件类型 |
| search_data_type | varchar(40) |  | NULL | 查询用组件数据源类型 |
| search_data_value | varchar(255) |  | NULL | 查询用组件数据值 |
| search_default_value | varchar(255) |  | NULL | 查询用组件默认值 |
| is_single_line | char(1) | 是 | 0 | 独立新行 |
| need_data_handler | char(1) |  | NULL | 是否需要data_handler |
| form_ui_type | varchar(40) |  | NULL | 表单组件类型 |
| form_jboltinput_filter_handler | varchar(60) |  | NULL | jboltinput filter handler |
| is_form_jboltinput_jstree_checkbox | char(1) |  | 0 | jboltinput jstree是否有checkbox |
| is_form_jboltinput_jstree_only_leaf | char(1) |  | NULL | jboltinput jstree checkbox只选子节点 |
| form_data_type | varchar(40) |  | NULL | 表单组件数据源类型 |
| form_data_value | varchar(255) |  | NULL | 表单组件数据值 |
| form_default_value | varchar(255) |  | NULL | 表单组件默认值 |
| data_rule_assign | varchar(255) |  | NULL | 表单校验规则 自定义 |
| data_rule | varchar(255) |  | NULL | 校验规则 |
| data_tips | varchar(255) |  | NULL | 校验提示信息 |
| is_import_col | char(1) | 是 | 0 | 是否为导入列 |
| is_export_col | char(1) | 是 | 0 | 导出列 |
| is_sortable | char(1) | 是 | 0 | 是否可排序 |
| table_ui_type | varchar(40) |  | NULL | 可编辑表格显示组件类型 |
| table_data_type | varchar(40) |  | NULL | 表格组件数据库类型 |
| table_data_value | varchar(255) |  | NULL | 表格组件数据值 |
| form_ele_width | int(11) | 是 | 0 | 组件自定义宽度 |
| is_item_inline | char(1) | 是 | 1 | radio checkbox等是否inline |
| form_data_text_attr | varchar(255) |  | NULL | data-text-attr |
| form_data_value_attr | varchar(40) |  | NULL | data-value-attr |
| form_data_column_attr | varchar(255) |  | NULL | data-column-attr |
| search_data_text_attr | varchar(255) |  | NULL | data-text-attr |
| search_data_value_attr | varchar(40) |  | NULL | data-value-attr |
| search_data_column_attr | varchar(255) |  | NULL | data-column-attr |
| table_data_text_attr | varchar(255) |  | NULL | data-text-attr |
| table_data_value_attr | varchar(40) |  | NULL | data-value-attr |
| table_data_column_attr | varchar(255) |  | NULL | data-column-attr |
| is_need_translate | char(1) | 是 | 0 | 是否需要翻译 |
| translate_type | varchar(40) |  | NULL | 翻译类型 |
| translate_use_value | varchar(250) |  | NULL | 翻译用值 |
| translate_col_name | varchar(250) |  | NULL | 翻译后的列名 |
| is_upload_to_qiniu | char(1) | 是 | 0 | 是否上传到七牛 |
| form_upload_url | varchar(255) |  | NULL | 上传地址 |
| form_img_uploader_area | varchar(20) |  | 200,200 | 上传组件area |
| form_maxsize | int(11) |  | 200 | 上传尺寸限制 |
| qiniu_bucket_sn | varchar(60) |  | NULL | 七牛bucket sn |
| qiniu_file_key | varchar(100) |  | [dateTime]/[randomId]/[filename] | 七牛file key |
| is_need_check_exists | char(1) | 是 | 0 | 是否检测数据重复字段 |
| is_fuzzy_query | char(1) | 是 | 0 | 是否模糊查询 |
| fuzzy_query_type | varchar(20) |  | default | 模糊查询类型 |
| form_tips | varchar(255) |  | NULL | 帮助信息 |
| form_tips_color | varchar(40) |  | secondary | 帮助信息的文字颜色 |
| data_min | decimal(10,2) | 是 | 0.00 | 最小值 |
| data_max | decimal(10,2) | 是 | 100.00 | 最大值 |
| data_step | decimal(10,2) | 是 | 1.00 | 步长 |
| ig_prepend_html | varchar(1000) |  | NULL | 前缀html |
| ig_append_html | varchar(1000) |  | NULL | 后缀html |

索引：PRIMARY KEY (id)

## jb_sensitive_word

敏感词词库（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| content | varchar(40) | 是 |  | 内容 |
| enable | char(1) | 是 | 1 | 启用状态 |

索引：PRIMARY KEY (id)

## jb_user_extend

用户扩展信息表（分组：platform）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| english_full_name | varchar(40) |  | NULL | 英文全名 |
| birthday | datetime |  | NULL | 出生日期 |
| residential_address | varchar(100) |  | NULL | 居住地址 |
| company_name | varchar(40) |  | NULL | 公司名称 |
| company_address | varchar(100) |  | NULL | 公司地址 |
| recipient_address | varchar(100) |  | NULL | 快递收件地址 |
| recipient_phone | varchar(20) |  | NULL | 收件人电话 |
| recipient_name | varchar(40) |  | NULL | 收件人姓名 |
| id_number | varchar(20) |  | NULL | 身份证号 |
| country | varchar(20) |  | NULL | 国家 |
| nation | varchar(20) |  | NULL | 民族 |
| province | varchar(20) |  | NULL | 省 |
| city | varchar(20) |  | NULL | 城市 |
| city_code | varchar(20) |  | NULL | 城市代码 |
| county | varchar(20) |  | NULL | 区县 |
| township | varchar(20) |  | NULL | 乡镇 |
| community | varchar(20) |  | NULL | 行政村 社区 |
| marital_status | int(11) |  | NULL | 婚姻状态 |
| is_cpc_member | char(1) | 是 | 0 | 是否党员 |
| is_cyl_member | char(1) | 是 | 0 | 是否共青团员 |
| professional_title | int(11) |  | NULL | 职称 |

索引：PRIMARY KEY (id)

## tb_ai_app

AI应用（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键ID |
| name | varchar(60) | 是 |  | 名称 |
| sn | varchar(60) | 是 |  | 编码 |
| function_count | int | 是 | 0 | 挂载Function数 |
| knowledge_base_count | int | 是 | 0 | 挂载知识库数 |
| icon | varchar(100) |  | NULL | 图标 |
| brief_info | varchar(255) |  | NULL | 介绍 |
| system_message_tpl | text |  | NULL | system_message模板 |
| prompt_tpl | text |  | NULL | prompt模板 |
| sort_rank | int | 是 | 1 | 排序 |
| first_user_msg | varchar(1000) |  | NULL | 首次进入时 用户消息 |
| first_ai_msg | varchar(1000) |  | NULL | 首次进入时AI提示语 |
| first_js_callback | varchar(255) |  | NULL | 首次进入时执行的js |
| ai_js_callback | varchar(255) |  | NULL | 每次AI返回内容后执行的js |
| enable | char(1) | 是 | 0 | 是否启用 |
| permission_key | varchar(255) |  | NULL | 什么权限可使用 |
| roles | varchar(255) |  | NULL | 角色 |
| is_keep_context | char(1) | 是 | 0 | 是否保持上下文 |
| used_count | int | 是 | 0 | 使用次数 |
| create_time | datetime | 是 |  | 创建时间 |
| create_user_id | bigint | 是 |  | 创建人 |
| update_time | datetime |  | NULL | 更新时间 |
| update_user_Id | bigint |  | NULL | 更新人 |
| is_left_box_extends | char(1) | 是 | 0 | 是否扩展左侧UI |
| is_right_box_extends | char(1) | 是 | 0 | 是否扩展右侧UI |
| is_top_box_extends | char(1) | 是 | 0 | 是否扩展上方UI |
| is_bottom_box_extends | char(1) | 是 | 0 | 是否扩展下方UI |
| is_ai_msg_input_box_extends | char(1) | 是 | 0 | 是否扩展消息输入框区域 |
| is_published | char(1) | 是 | 0 | 是否发布 |
| publish_type | int | 是 | 0 | 发布类型 |
| publish_time | datetime |  | NULL | 发布时间 |
| ui_chat_theme | varchar(40) | 是 | DEFAULT | UI对话风格 |
| ui_layout_theme | varchar(40) | 是 | DEFAULT | ui布局风格 |
| view_pwd | varchar(128) |  | NULL | 访问密码 |
| pwd_salt | varchar(128) |  | NULL | 密码盐 |
| link_sys_app_id | bigint |  | NULL | 绑定系统应用APP的ID |
| ai_avatar | varchar(255) |  | NULL | AI的头像 |
| bg_img | varchar(255) |  | NULL | 背景图 |
| mobile_bg_img | varchar(255) |  | NULL | 移动端背景图 |
| chat_empty_bg_img | varchar(255) |  | NULL | chat区域空内容背景图 |
| is_show_chat_empty_bg_img | char(1) | 是 | 1 | 是否显示数据空背景图 |
| custom_css_style | text |  |  | css自定义内容 |
| is_show_history_btn | char(1) | 是 | 1 | 是否显示可查看历史记录按钮 |
| is_show_new_page_btn | char(1) | 是 | 1 | 是否显示打开独立新页面按钮 |
| is_show_new_session_btn | char(1) | 是 | 1 | 是否显示可新建会话按钮 |
| is_show_refresh_btn | char(1) | 是 | 1 | 是否显示刷新按钮 |
| is_show_reference | char(1) | 是 | 0 | 是否显示参考记录 |
| is_show_ref_fragment | char(1) | 是 | 0 | 引用片段查看 |
| is_show_file_download | char(1) | 是 | 0 | 是否可下载原文 |
| is_only_function_call_mode | char(1) | 是 | 0 | 是否只能执行function |
| is_only_knowledge_mode | char(1) | 是 | 0 | 是否仅知识库模式 |
| no_match_reply_content | varchar(1000) |  | NULL | 知识库无匹配时回复内容 |
| no_match_function_reply_content | varchar(1000) |  | NULL | function无匹配时回复 |
| temperature | decimal(10, 2) | 是 | 0.30 | llm参数temperature |
| top_p | decimal(10, 2) | 是 | 0.85 | llm参数topP |
| top_k | int | 是 | 5 | llm参数topK |
| max_tokens | int | 是 | 2048 | 最大输出tokens量 |
| input_max_length | int | 是 | 1000 | 用户输入内容长度限制 |
| ai_msg_statement_info | varchar(100) | 是 | AI生成,仅供参考 | AI消息提示 |
| is_show_ai_msg_bottom_opt_btn_tts | char(1) | 是 | 0 | 是否显示语音播放按钮 |
| is_show_ai_msg_bottom_opt_btn_re_chat | char(1) | 是 | 1 | 是否显示重新生成按钮 |
| ai_cot_class | varchar(255) |  | NULL | 指定执行思维链 |
| is_need_rewrite_query | char(1) | 是 | 0 | 是否需要完善重写知识库问题 |
| is_show_chat_avatar | char(1) | 是 | 1 | 是否显示对话头像 |
| bubble_style | varchar(255) | 是 | DEFAULT | 对话气泡风格 |
| header_style | varchar(30) |  | DEFAULT | header风格 |
| page_tpl | varchar(60) | 是 | index.html | 页面模版 |
| is_show_thinking | char(1) | 是 | 0 | 是否显示深度思考信息 |
| is_only_html_mode | char(1) | 是 | 0 | 是否启用仅Html模式 |
| is_use_iframe_mode | char(1) | 是 | 0 | html使用Iframe渲染 |
| front_framework | varchar(40) |  | bootstrap | 前端框架依赖 |
| function_llm_id | bigint |  | NULL | 工具调用指定模型资源ID |
| is_function_llm_enable_thinking | char(1) | 是 | 0 | 工具调用模型是否启用思考 |
| func_result_llm_id | bigint |  | NULL | 工具调用结果推理模型ID |
| is_func_result_llm_enable_thinking | char(1) | 是 | 0 | 工具调用结果模型是否开启思考 |
| chat_llm_id | bigint |  | NULL | 对话模型ID |
| is_chat_llm_enable_thinking | char(1) | 是 | 1 | 对话模型是否启用思考 |
| kb_result_llm_id | bigint |  | NULL | 知识库问答对话模型ID |
| is_kb_result_llm_enable_thinking | char(1) | 是 | 1 | 知识库回答模型是否开启思考 |
| ai_loading_msg | varchar(100) | 是 | AI思考中... | AI loading内容 |
| is_need_md_formula | char(1) | 是 | 0 | 是否开启公式渲染 |

索引：PRIMARY KEY (id)

## tb_ai_app_link_function

AI应用绑定Functions（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键ID |
| app_id | bigint | 是 |  | AI应用 |
| function_id | bigint | 是 |  | AiFunction |

索引：PRIMARY KEY (id)

## tb_ai_app_link_knowledge_base

AI应用关联知识库（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键ID |
| app_id | bigint | 是 |  | AI应用 |
| knowledge_base_id | bigint | 是 |  | 知识库 |

索引：PRIMARY KEY (id)

## tb_ai_app_message

AI应用消息记录（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键ID |
| content | text | 是 |  | 消息内容 |
| role | varchar(20) | 是 |  | 角色 |
| ai_app_id | bigint | 是 |  | AI应用 |
| session_id | bigint | 是 |  | 会话 |
| user_id | bigint | 是 |  | 用户 |
| is_in_context | char(1) | 是 | 1 | 是否收录到上下文 |
| create_time | datetime | 是 |  | 发布时间 |
| is_hidden | char(1) | 是 | 0 | 是否隐藏 |
| link_ai_files_json | text |  |  | 参考记录 |
| link_msg_id | bigint |  | NULL | 关联消息ID |
| think_content | text |  |  | 深度思考内容 |
| think_time_consuming | int | 是 | 0 | 思考耗时 |

索引：PRIMARY KEY (id)

## tb_ai_app_session

AI应用下的session会话（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键ID |
| user_id | bigint | 是 |  | 用户ID |
| name | varchar(60) | 是 |  | 名称 |
| ai_app_id | bigint | 是 |  | AI应用 |
| create_time | datetime | 是 |  | 创建时间 |
| update_time | datetime |  | NULL | 更新时间 |

索引：PRIMARY KEY (id)；KEY session_user_id_index (user_id) COMMENT '用户ID index'

## tb_ai_app_summary

AI应用使用统计（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键ID |
| use_count | int | 是 | 0 | 使用量 |
| session_count | int | 是 | 0 | 会话数 |
| message_count | int | 是 | 0 | 消息数 |
| user_times | int | 是 | 0 | 使用人次 |

索引：PRIMARY KEY (id)

## tb_ai_doc

AI问答库（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键ID |
| title | varchar(5000) | 是 |  | 标题 |
| answer | varchar(5000) |  | NULL | 答案 |
| ai_knowledge_base_id | bigint | 是 |  | 知识库 |
| ai_file_id | bigint |  | NULL | 文件 |
| data_type | int | 是 |  | 数据类型 |
| train_type | int | 是 |  | 训练类型 |
| train_state | int | 是 | 1 | 训练状态 |
| vector_state | int | 是 | 1 | 向量化状态 |
| vector_time | datetime |  | NULL | 向量化时间 |
| train_time | datetime |  | NULL | 训练时间 |
| create_time | datetime | 是 |  | 创建时间 |
| update_time | datetime |  | NULL | 修改时间 |
| link_tag_id | bigint |  | NULL | 关联标签 |

索引：PRIMARY KEY (id)

## tb_ai_file

AI文件（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键ID |
| name | varchar(50) | 是 |  | 名称 |
| is_upload_to_local | char(1) | 是 | 1 | 上传类型 |
| url | varchar(250) |  | NULL | 文件地址 |
| train_type | int | 是 |  | 训练类型 |
| upload_type | int | 是 | 1 | 文件上传类型 |
| website_url | varchar(255) |  | NULL | 网页地址 |
| ai_knowledge_base_id | bigint | 是 |  | 知识库 |
| split_state | int | 是 | 1 | 拆分状态 |
| split_time | datetime |  | NULL | 拆分时间 |
| train_state | int | 是 | 1 | 训练状态 |
| train_time | datetime |  | NULL | 训练时间 |
| vector_state | int | 是 | 1 | 向量化状态 |
| vector_time | datetime |  | NULL | 向量化时间 |
| create_time | datetime | 是 |  | 创建时间 |
| create_user_id | bigint | 是 |  | 创建人 |
| update_time | datetime |  | NULL | 更新时间 |
| update_user_id | bigint |  | NULL | 更新人 |
| link_tag_id | bigint |  | NULL | 关联标签 |
| split_rule | int | 是 | 1 | 拆分规则 |
| ai_split_prompt | varchar(2000) |  | NULL | AI拆分专用prompt |
| ai_split_model_id | bigint |  | NULL | AI拆分使用的模型ID |
| qa_train_model_id | bigint |  | NULL | QA提取专用模型ID |
| title_selector | varchar(255) |  | NULL | 抽取标题的css选择器 |
| content_selector | varchar(255) |  | NULL | 抽取内容的css选择器 |
| split_chunk_size | int | 是 | 300 | 分段token大小 |
| split_overlap_size | int | 是 | 50 | 重叠token大小 |
| is_need_extract_resources | char(1) | 是 | 0 | 是否提取资源 |
| is_need_ocr_img | char(1) | 是 | 0 | 是否执行图片资源OCR |
| train_fail_reason | varchar(255) |  | NULL | 训练过程失败原因 |

索引：PRIMARY KEY (id)

## tb_ai_function

AI_可调用Tools注册中心（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键ID |
| sn | varchar(40) | 是 |  | 编号 |
| brief_info | varchar(255) |  | NULL | 功能描述 |
| of_module | int |  | NULL | 所属模块 |
| service_pkg | varchar(100) |  | NULL | Service Package |
| service_class | varchar(100) |  | NULL | Service Class |
| service_method | varchar(100) |  | NULL | Service Method |
| roles | varchar(255) |  | NULL | 应用角色 |
| permission_key | varchar(100) |  | NULL | 权限 |
| system_message | varchar(1000) |  | NULL | 调用前置要求 |
| return_value_requirements | varchar(1000) |  | NULL | 返回值特殊处理说明 |
| used_count | int | 是 | 0 | 调用次数 |
| enable | char(1) | 是 | 0 | 是否启用 |
| required_args | varchar(255) |  | NULL | 哪些参数必填 |
| param_count | int | 是 | 0 | 参数个数 |
| service_method_text | varchar(255) |  | NULL | Service Method Text |
| type | int |  | 1 | function类型 |
| api_url | varchar(255) |  | NULL | API_URL |
| api_param_json | varchar(1000) |  | NULL | API_参数 |
| api_header_json | varchar(1000) |  | NULL | API_header |
| api_http_method | varchar(10) |  | get | API_http_method |

索引：PRIMARY KEY (id)

## tb_ai_function_param

AI接口参数（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  |  主键ID |
| ai_function_id | bigint | 是 |  | 属于哪个接口 |
| name | varchar(60) | 是 |  | 参数名 |
| brief_info | varchar(255) |  | NULL | 参数描述 |
| type | varchar(100) | 是 |  | 参数值类型 |
| is_required | char(1) | 是 | 1 | 是否必填 |
| default_value | varchar(100) |  | NULL | 默认值 |
| sort_rank | int | 是 | 1 | 顺序 |

索引：PRIMARY KEY (id)

## tb_ai_knowledge_base

AI知识库（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  |  |
| name | varchar(50) | 是 |  | 名称 |
| brief_info | varchar(255) |  | NULL | 简介 |
| enable | char(1) | 是 | 0 | 是否启用 |
| create_time | datetime | 是 |  | 创建日期 |
| update_time | datetime |  | NULL | 更新日期 |
| create_user_id | bigint | 是 |  | 创建人 |
| update_user_id | bigint |  | NULL | 更新人 |

索引：PRIMARY KEY (id)

## tb_ai_page

AI功能页面（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键 |
| name | varchar(40) | 是 |  | 名称 |
| sn | varchar(40) | 是 |  | 编号 |
| type | int | 是 | 1 | 类型 |
| url | varchar(100) | 是 |  | 页面地址 |
| brief_info | varchar(255) |  | NULL | 描述 |
| used_count | int | 是 | 0 | 使用数 |
| enable | char(1) | 是 | 0 | 是否启用 |
| roles | varchar(255) |  | NULL | 应用角色 |
| permission_key | varchar(100) |  | NULL | 权限KEY |
| open_type | int | 是 | 1 | 打开方式 |
| link_id | bigint |  | NULL | 关联对象ID |

索引：PRIMARY KEY (id)

## tb_ai_file_tag

AI文件标签（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键ID |
| name | varchar(60) | 是 |  | 名称 |
| alias_name | varchar(60) |  | NULL | 别名 |

索引：PRIMARY KEY (id)

## tb_ai_llm

LLM大模型配置（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键ID |
| name | varchar(60) | 是 |  | 名称 |
| sn | varchar(60) | 是 |  | 编码 |
| type | varchar(60) | 是 |  | 类型 |
| model | varchar(60) | 是 |  | 模型名称 |
| is_local | char(1) | 是 | 0 | 是否本地部署 |
| is_with_think_switch | char(1) | 是 | 0 | 思考模式是否可控 |
| use_for | varchar(60) |  | NULL | 模型用途场景 |
| api_key | varchar(255) |  | NULL | API_KEY |
| api_secret | varchar(255) |  | NULL | API_SECRET |
| api_url | varchar(255) |  | NULL | API_URL |
| default_max_tokens | int | 是 | 2000 | max_tokens生成最大令牌数(默认) |
| default_temperature | decimal(10, 2) | 是 | 0.30 | 温度(默认) |
| default_top_p | decimal(10, 2) | 是 | 0.85 | 多样性(默认) |
| default_top_k | int | 是 | 5 | 采样(默认) |
| rate_limit_min | int | 是 | 0 | 频率限制_每分钟 |
| rate_limit_hour | int | 是 | 0 | 频率限制_每小时 |
| rate_limit_day | int | 是 | 0 | 频率限制_每天 |
| enable | char(1) | 是 |  | 是否启用 |
| remark | varchar(255) |  | NULL | 备注 |
| create_time | datetime | 是 |  | 创建时间 |
| create_user_id | bigint | 是 |  | 创建人 |
| update_time | datetime | 是 |  | 更新时间 |
| update_user_id | bigint | 是 |  | 更新人 |

索引：PRIMARY KEY (id)

## tb_ai_embedding

Embedding模型资源配置（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键ID |
| name | varchar(60) | 是 |  | 资源名称 |
| sn | varchar(60) | 是 |  | 编码 |
| type | varchar(60) | 是 |  | 资源类型 |
| model | varchar(100) |  | NULL | 模型名称 |
| is_local | char(1) | 是 | 0 | 是否本地部署 |
| api_key | varchar(255) |  | NULL | API_KEY |
| api_secret | varchar(255) |  | NULL | API_SECRET |
| api_url | varchar(255) |  | NULL | API_URL |
| dimension | int | 是 | 0 | 维度 |
| rate_limit_min | int | 是 | 0 | 频率限制_每分钟 |
| rate_limit_hour | int | 是 | 0 | 频率限制_每小时 |
| rate_limit_day | int | 是 | 0 | 频率限制_每天 |
| enable | char(1) | 是 |  | 是否启用 |
| remark | varchar(255) |  | NULL | 备注 |
| create_time | datetime | 是 |  | 创建时间 |
| create_user_id | bigint | 是 |  | 创建人 |
| update_time | datetime | 是 |  | 更新时间 |
| update_user_id | bigint | 是 |  | 更新人 |

索引：PRIMARY KEY (id)

## tb_ai_vdb

向量数据库资源配置（分组：ai）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 是 |  | 主键ID |
| name | varchar(60) | 是 |  | 名称 |
| sn | varchar(60) | 是 |  | 编码 |
| type | varchar(40) | 是 |  | 类型 |
| url | varchar(255) | 是 |  | URL |
| user_name | varchar(100) | 是 |  | 用户名 |
| password | varchar(100) | 是 |  | 密码 |
| database_name | varchar(100) | 是 |  | 数据库名 |
| embedding_id | bigint | 是 |  | Embeding模型 |
| enable | char(1) | 是 | 0 | 是否启用 |
| remark | varchar(255) |  | NULL | 备注 |
| create_time | datetime | 是 |  |  创建时间 |
| create_user_id | bigint | 是 |  | 创建人 |
| update_time | datetime | 是 |  | 更新时间 |
| update_user_id | bigint | 是 |  | 更新人 |
| is_main | char(1) | 是 | 0 | 是否默认Main资源 |

索引：PRIMARY KEY (id)

## jbc_custom_business

系统业务定义（分组：custom_business）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| name | varchar(60) | 是 |  | 业务名称 |
| remark | varchar(255) |  | NULL | 备注信息 |
| create_time | datetime | 是 |  | 创建时间 |
| update_time | datetime |  | NULL | 更新时间 |
| create_user_id | bigint(20) | 是 |  | 创建人 |
| update_user_id | bigint(20) |  | NULL | 更新人 |
| enable | char(1) | 是 | 0 | 是否启用 |

索引：PRIMARY KEY (id)

## jbc_role_custom_table

角色自定义表格设置（分组：custom_business）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| role_id | bigint(20) | 是 |  | 角色 |
| business_id | bigint(20) | 是 |  | 业务ID |
| attr_name | varchar(60) | 是 |  | 属性名 |
| title | varchar(30) | 是 |  | 标题 |
| sort_rank | int(11) | 是 | 1 | 排序 |
| java_type | varchar(40) | 是 |  | java类型 |
| render_format | varchar(60) |  | NULL | 渲染格式化 |
| width | int(11) | 是 | 100 | 默认宽度 |
| is_min_width | char(1) | 是 | 0 | 是否最小宽度 |
| is_show_in_thead | char(1) | 是 | 1 | 是否显示 |
| is_fixed_left | char(1) | 是 | 0 | 是否左固定列 |
| is_fixed_right | char(1) | 是 | 0 | 是否右固定列 |

索引：PRIMARY KEY (id)

## jbc_user_custom_business

用户针对一个业务自定义模板（分组：custom_business）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| user_id | bigint(20) | 是 |  | 用户ID |
| business_id | bigint(20) | 是 |  | 业务ID |
| name | varchar(60) | 是 |  | 业务模板名称 |
| remark | varchar(255) |  | NULL | 备注 |
| create_time | datetime | 是 |  | 创建时间 |
| update_time | datetime |  | NULL | 更新时间 |

索引：PRIMARY KEY (id)

## jbc_user_role_custom_table

用户自定义业务配置表格（分组：custom_business）

| 字段 | 类型 | 非空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint(20) | 是 |  | 主键ID |
| user_id | bigint(20) | 是 |  | 用户ID |
| user_custom_business_id | bigint(20) | 是 |  | 用户自定义业务模板ID |
| role_id | bigint(20) | 是 |  | 角色 |
| attr_name | varchar(60) | 是 |  | 属性名 |
| title | varchar(30) | 是 |  | 标题 |
| sort_rank | int(11) | 是 | 1 | 排序 |
| java_type | varchar(40) | 是 |  | java类型 |
| render_format | varchar(60) |  | NULL | 渲染格式化 |
| width | int(11) | 是 | 100 | 默认宽度 |
| is_min_width | char(1) | 是 | 0 | 是否最小宽度 |
| is_show_in_thead | char(1) | 是 | 1 | 是否显示 |
| is_fixed_left | char(1) | 是 | 0 | 是否左固定列 |
| is_fixed_right | char(1) | 是 | 0 | 是否右固定列 |

索引：PRIMARY KEY (id)
