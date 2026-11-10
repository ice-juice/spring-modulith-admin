# 07 JBolt 功能目录（自动生成）

> 由 tools/jbolt-research/build_feature_catalog.py 从 permission_init.json、控制器源码（Javadoc）、页面模板（_view）生成。
> 每个菜单列出：页面标题、查询条件、工具栏按钮、列表字段、行操作、表单字段、控制器动作（含 Javadoc 说明）。

## 操作台

- 权限 key：`dashboard`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/dashboard`

## 系统管理

- 权限 key：`systemmgr`；类型：菜单；打开方式：主区域；超管默认：是

### 系统管理 / 用户管理

- 权限 key：`user`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/user`
- 控制器：`UserAdminController`（cn/jbolt/_admin/user/UserAdminController.java），类注解 @CheckPermission(PermissionKey.USER) @UnCheckIfSystemAdmin
- 页面标题：用户管理
- 查询条件：输入关键字搜索
- 工具栏：查询、新增
- 列表字段：序号、姓名、头像、性别、角色、用户名、手机号、是否为超管、是否启用、操作
- 行操作：编辑
- 表单字段：姓名、编号、性别、年龄、手机号、E-Mail、用户名、密码、角色、部门、岗位

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| options | /admin/user/options | 用户表数作为选项数据源 | @UnCheck |
| index | /admin/user | 管理首页 |  |
| datas | /admin/user/datas | 表格数据接口 |  |
| sysnoticeUsers | /admin/user/sysnoticeUsers | 系统通知 可用 选择用户数据接口 | @CheckPermission(PermissionKey.SYS_NOTICE) |
| autocomplete | /admin/user/autocomplete | 获取用户列表 通过关键字匹配 autocomplete组件使用 | @UnCheck |
| uploadAvatar | /admin/user/uploadAvatar | 上传头像 |  |
| uploadMyAvatar | /admin/user/uploadMyAvatar | 上传头像 | @UnCheck |
| sexOptions | /admin/user/sexOptions |  | @Before(JBoltCacheInterceptor.class) @UnCheck |
| pwd | /admin/user/pwd | 进入自身密码修改界面 | @UnCheck |
| avatar | /admin/user/avatar | 进入自身头像修改界面 | @UnCheck |
| editpwd | /admin/user/editpwd | 进入重置用户密码界面 | @CheckPermission(PermissionKey.USER) |
| submitpwd | /admin/user/submitpwd | 重置用户密码 | @CheckPermission(PermissionKey.USER) |
| updatepwd | /admin/user/updatepwd | 修改用户自己的密码 | @UnCheck |
| add | /admin/user/add | 新增 |  |
| extendForm | /admin/user/extendForm | 查看扩展信息 |  |
| extendDetail | /admin/user/extendDetail | 查看扩展信息 |  |
| edit | /admin/user/edit | 编辑 |  |
| save | /admin/user/save | 保存 | @Before(Tx.class) |
| update | /admin/user/update | 更新 | @Before(Tx.class) |
| delete | /admin/user/delete | 删除 | @Before(Tx.class) |
| toggleEnable | /admin/user/toggleEnable | 切换启用状态 | @Before(Tx.class) |
| deleteRole | /admin/user/deleteRole | 删除角色 | @Before(Tx.class) |
| processAllDeptPath | /admin/user/processAllDeptPath | 处理deptPath | @Before(Tx.class) |
| modules | /admin/user/modules | 列出模块 | @UnCheck |

### 系统管理 / 部门管理

- 权限 key：`dept`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/dept`
- 控制器：`DeptAdminController`（cn/jbolt/_admin/dept/DeptAdminController.java），类注解 @CheckPermission(PermissionKey.DEPT) @UnCheckIfSystemAdmin
- 页面标题：部门管理
- 查询条件：输入关键字 进行过滤
- 工具栏：新增、初始化排序、刷新
- 列表字段：部门名称、部门全称、部门类型、负责人、备注、启用/禁用、操作
- 行操作：新增下级部门、编辑、删除、上移、下移
- 表单字段：上级部门、部门名称、部门全称、部门代码、负责人、邮政编码、电子邮箱、联系地址、联系电话、备注信息

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/dept | / public class DeptAdminController extends JBoltBaseController { private DeptSer |  |
| datas | /admin/dept/datas | 数据源 |  |
| crudJsTreeDatas | /admin/dept/crudJsTreeDatas | crud树的数据源 |  |
| options | /admin/dept/options | select数据源 | @UnCheck |
| enableOptions | /admin/dept/enableOptions | select数据源 只需要enable=true的 | @UnCheck |
| enableJsTreeDatas | /admin/dept/enableJsTreeDatas | JSTree树形数据源 | @UnCheck |
| add | /admin/dept/add | 新增 |  |
| addInDialog | /admin/dept/addInDialog | 新增Dialog |  |
| edit | /admin/dept/edit | 编辑 |  |
| save | /admin/dept/save | 保存 | @Before(Tx.class) |
| update | /admin/dept/update | 更新 | @Before(Tx.class) |
| delete | /admin/dept/delete | 删除 | @Before(Tx.class) |
| toggleEnable | /admin/dept/toggleEnable | 切换启用状态 | @Before(Tx.class) |
| up | /admin/dept/up | 排序 上移 | @Before(Tx.class) |
| down | /admin/dept/down | 排序 下移 | @Before(Tx.class) |
| initRank | /admin/dept/initRank | 排序 初始化 | @Before(Tx.class) |
| processAllDeptPath | /admin/dept/processAllDeptPath | 处理所有部门的deptPath | @Before(Tx.class) |
| move | /admin/dept/move | jstree 节点move | @Before(Tx.class) |

### 系统管理 / 岗位管理

- 权限 key：`post`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/post`
- 控制器：`PostAdminController`（cn/jbolt/_admin/post/PostAdminController.java），类注解 @CheckPermission(PermissionKey.POST) @UnCheckIfSystemAdmin
- 页面标题：岗位管理
- 查询条件：输入关键字搜索
- 工具栏：查询、新增、初始化排序
- 列表字段：序号、岗位名称、岗位类型、编码、备注信息、启用/禁用、操作
- 行操作：编辑、删除、上移、下移
- 表单字段：岗位名称、岗位类型、编码、备注信息

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/post | / public class PostAdminController extends JBoltBaseController { private PostSer |  |
| datas | /admin/post/datas | 数据源 |  |
| enableOptions | /admin/post/enableOptions | select数据源 | @UnCheck |
| options | /admin/post/options | select数据源 | @UnCheck |
| add | /admin/post/add | 新增 |  |
| edit | /admin/post/edit | 编辑 |  |
| save | /admin/post/save | 保存 | @Before(Tx.class) |
| update | /admin/post/update | 更新 | @Before(Tx.class) |
| delete | /admin/post/delete | 删除 | @Before(Tx.class) |
| toggleEnable | /admin/post/toggleEnable | 切换启用状态 |  |
| up | /admin/post/up | 排序 上移 | @Before(Tx.class) |
| down | /admin/post/down | 排序 下移 | @Before(Tx.class) |
| initRank | /admin/post/initRank | 排序 初始化 | @Before(Tx.class) |

### 系统管理 / 权限配置

- 权限 key：`role_permission_menu`；类型：菜单；打开方式：主区域；超管默认：是

#### 系统管理 / 权限配置 / 角色管理

- 权限 key：`role`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/role`
- 控制器：`RoleAdminController`（cn/jbolt/_admin/role/RoleAdminController.java），类注解 @CheckPermission(PermissionKey.ROLE) @UnCheckIfSystemAdmin
- 页面标题：角色管理
- 工具栏：新增、刷新
- 列表字段：角色、编码、操作
- 行操作：编辑
- 表单字段：请输入名称:、请输入编码:

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/role | 管理首页 |  |
| datas | /admin/role/datas |  |  |
| options | /admin/role/options |  | @UnCheck |
| users | /admin/role/users | 查询role上所有用户列表进入页面 |  |
| userDatas | /admin/role/userDatas | 角色下用户数据查询 |  |
| add | /admin/role/add | 新增 |  |
| addItem | /admin/role/addItem | 新增Item |  |
| edit | /admin/role/edit | 编辑 |  |
| save | /admin/role/save | 保存 |  |
| update | /admin/role/update | 更新 |  |
| delete | /admin/role/delete | 删除 | @Before(Tx.class) |
| clearUsers | /admin/role/clearUsers | 清空角色上的用户列表 | @Before(Tx.class) |

#### 系统管理 / 权限配置 / 顶部导航

- 权限 key：`topnav`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/topnav`
- 控制器：`TopnavAdminController`（cn/jbolt/_admin/topnav/TopnavAdminController.java），类注解 @CheckPermission(PermissionKey.TOPNAV) @UnCheckIfSystemAdmin
- 页面标题：顶部导航管理
- 查询条件：输入关键字搜索
- 工具栏：查询、新增、初始化排序
- 列表字段：序号、名称、图标、分配菜单、是否启用、操作
- 行操作：编辑、删除、上移、下移
- 表单字段：名称、图标

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/topnav | / public class TopnavAdminController extends JBoltBaseController { private Topna |  |
| datas | /admin/topnav/datas | 数据源 |  |
| options | /admin/topnav/options | options 数据源 | @UnCheck |
| add | /admin/topnav/add | 新增 |  |
| edit | /admin/topnav/edit | 编辑 |  |
| save | /admin/topnav/save | 保存 | @Before(Tx.class) |
| update | /admin/topnav/update | 更新 | @Before(Tx.class) |
| delete | /admin/topnav/delete | 删除 | @Before(Tx.class) |
| up | /admin/topnav/up | 排序 上移 | @Before(Tx.class) |
| down | /admin/topnav/down | 排序 下移 | @Before(Tx.class) |
| initRank | /admin/topnav/initRank | 排序 初始化 | @Before(Tx.class) |
| toggleEnable | /admin/topnav/toggleEnable | 切换启用状态 | @Before(Tx.class) |
| clearMenus | /admin/topnav/clearMenus | 清空一个顶部导航下的菜单配置 |  |
| clearAllMenus | /admin/topnav/clearAllMenus | 清空所有顶部导航下的菜单配置 |  |

#### 系统管理 / 权限配置 / 权限资源

- 权限 key：`permission`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/permission`
- 控制器：`PermissionAdminController`（cn/jbolt/_admin/permission/PermissionAdminController.java），类注解 @CheckPermission(PermissionKey.PERMISSION) @UnCheckIfSystemAdmin
- 页面标题：系统资源权限管理
- 工具栏：初始化排序、新增
- 列表字段：序号、资源、URL、加载方式、打开类型、KEY、资源类型、超管默认、操作
- 行操作：添加下级资源、编辑、删除、上移、下移
- 表单字段：所属模块、上级资源、资源名称、ICONS、资源地址、权限KEY、是否是菜单、是、否、加载方式、浏览器当前窗口、浏览器新窗口、打开类型、系统默认、IFrame、Dialog、JBoltLayer组件、组件属性配置、是否超级管理员默认可用权限、设置插入位置、start、before、after、end

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/permission |  |  |
| datas | /admin/permission/datas | ajax数据接口 |  |
| options | /admin/permission/options | ajax options数据接口 |  |
| add | /admin/permission/add |  |  |
| insert | /admin/permission/insert |  |  |
| edit | /admin/permission/edit |  |  |
| save | /admin/permission/save |  |  |
| update | /admin/permission/update |  |  |
| delete | /admin/permission/delete |  |  |
| up | /admin/permission/up |  |  |
| down | /admin/permission/down |  |  |
| initRank | /admin/permission/initRank |  |  |
| toggleSystemAdminDefault | /admin/permission/toggleSystemAdminDefault | 切换是否是超管默认 |  |
| modules | /admin/permission/modules | 列出模块 | @UnCheck |

### 系统管理 / 字典参数

- 权限 key：`dictionary_config`；类型：菜单；打开方式：主区域；超管默认：是

#### 系统管理 / 字典参数 / 数据字典

- 权限 key：`dictionary`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/dictionary`
- 控制器：`DictionaryAdminController`（cn/jbolt/_admin/dictionary/DictionaryAdminController.java），类注解 @CheckPermission({PermissionKey.DICTIONARY}) @UnCheckIfSystemAdmin @OnlySaasPlatform
- 页面标题：字典管理
- 工具栏：检测并初始化
- 行操作：添加新数据分类
- 表单字段：请选择上级：、请输入名称：、请输入编码：

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| datas | /admin/dictionary/datas |  | @Before(JBoltNoUrlPara.class) |
| options | /admin/dictionary/options | 字典管理Controller / public class DictionaryAdminController extends JBoltBaseControl | @UnCheck @Before(JBoltNoUrlPara.class) |
| poptions | /admin/dictionary/poptions | 根据获取一级options | @UnCheck @Before(JBoltNoUrlPara.class) |
| soptions | /admin/dictionary/soptions | 子类级别数据 根据父类ID获取数据 | @UnCheck @Before(JBoltNoUrlPara.class) |
| soptionsByPsn | /admin/dictionary/soptionsByPsn | 子类级别数据 根据父类SN获取数据 | @UnCheck @Before(JBoltNoUrlPara.class) |
| checkandinit | /admin/dictionary/checkandinit |  |  |
| mgr | /admin/dictionary/mgr | 加载管理portal |  |
| index | /admin/dictionary | 管理首页 |  |
| show | /admin/dictionary/show | 从日志过来的显示一个 |  |
| select | /admin/dictionary/select | 除了自己以外的其它所有数据 |  |
| add | /admin/dictionary/add | 新增 |  |
| addItem | /admin/dictionary/addItem | 新增 |  |
| edit | /admin/dictionary/edit | 编辑 |  |
| editItem | /admin/dictionary/editItem | 编辑 |  |
| save | /admin/dictionary/save | 保存 |  |
| update | /admin/dictionary/update | 更新 | @Before(Tx.class) |
| delete | /admin/dictionary/delete | 删除 | @Before(Tx.class) |
| up | /admin/dictionary/up | 上移 |  |
| down | /admin/dictionary/down | 下移 |  |
| initRank | /admin/dictionary/initRank | 初始化顺序 |  |
| clearByType | /admin/dictionary/clearByType | 清空分类下的字典数据 |  |
| toggleEnable | /admin/dictionary/toggleEnable |  | @Before(Tx.class) |

#### 系统管理 / 字典参数 / 全局参数

- 权限 key：`globalconfig`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/globalconfig`
- 控制器：`GlobalConfigAdminController`（cn/jbolt/_admin/globalconfig/GlobalConfigAdminController.java），类注解 @CheckPermission(PermissionKey.GLOBALCONFIG) @UnCheckIfSystemAdmin
- 页面标题：系统参数配置管理#if(typeName??): #end
- 查询条件：关键词查询、是否内置
- 工具栏：查询、刷新缓存、重置全部
- 列表字段：名称、操作、序号、参数名、参数KEY、参数值、所属类型、值类型、内置、更新时间、编辑
- 行操作：编辑、设置登录页面背景图、上移、下移、刷新、新增系统参数类型、初始化顺序
- 表单字段：启用、禁用、sysout-控制台、jboltlog-日志文件、默认、GIF验证码、气泡PNG、数字运算型、数字运算型（中文）、(数据单位：天)、树形表格管理、JS_TREE树管理、系统名称、color值：、color值参考：、[60~400之间的数字]、参数类型：、系统内置：、是、否、可视化选择、[看大图]、'>[看视频]、手动录入

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/globalconfig |  |  |
| datas | /admin/globalconfig/datas | 全局配置项 / public class GlobalConfigAdminController extends JBoltBaseController { p |  |
| edit | /admin/globalconfig/edit | 列出登录背景图数据源 / private List<String> getLoginBgimgs() { List<String> imgs=new Array |  |
| update | /admin/globalconfig/update | 更新 |  |
| changeAssetsVersion | /admin/globalconfig/changeAssetsVersion | 刷新assetsVersion |  |
| editCustomConfig | /admin/globalconfig/editCustomConfig | 编辑非内置 |  |
| updateCustomConfig | /admin/globalconfig/updateCustomConfig | 更新自定义参数 |  |
| saveCustomConfig | /admin/globalconfig/saveCustomConfig | 保存自定义参数 |  |
| toggleBooleanValue | /admin/globalconfig/toggleBooleanValue | 切换Boolean类型的值 |  |
| deleteCustomConfig | /admin/globalconfig/deleteCustomConfig | 删除自定义参数 |  |
| clearCacheByType | /admin/globalconfig/clearCacheByType | 清空缓存 指定type |  |
| clearCache | /admin/globalconfig/clearCache | 清空缓存 |  |
| clearReset | /admin/globalconfig/clearReset | 清空缓存 重新初始化 |  |
| addCustomConfig | /admin/globalconfig/addCustomConfig | 新增参数 |  |

### 系统管理 / 系统通知

- 权限 key：`sys_notice`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/sysnotice`
- 控制器：`SysNoticeAdminController`（cn/jbolt/_admin/msgcenter/SysNoticeAdminController.java），类注解 @CheckPermission(PermissionKey.SYS_NOTICE)
- 页面标题：系统通知管理
- 查询条件：输入关键字搜索、类型、优先级、接收者类型
- 工具栏：查询、回收站
- 列表字段：序号、标题、附件、类型、优先级、已读数、接收类型、接收值、创建时间、更新时间、创建人、更新人
- 行操作：查看通知详情、系统通知附件列表管理
- 表单字段：标题、内容

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/sysnotice | 系统通知管理 Controller / public class SysNoticeAdminController extends JBoltBaseContr |  |
| datas | /admin/sysnotice/datas | 数据源 |  |
| add | /admin/sysnotice/add | 新增 |  |
| edit | /admin/sysnotice/edit | 编辑 |  |
| files | /admin/sysnotice/files | 查看附件列表 |  |
| updateFiles | /admin/sysnotice/updateFiles | 更新附件列表 |  |
| uploadFiles | /admin/sysnotice/uploadFiles | 上传多文件 |  |
| pdfViewer | /admin/sysnotice/pdfViewer |  |  |
| download | /admin/sysnotice/download |  |  |
| detail | /admin/sysnotice/detail | 详情 |  |
| save | /admin/sysnotice/save | 保存 | @Before(Tx.class) |
| update | /admin/sysnotice/update | 更新 | @Before(Tx.class) |
| batchDelete | /admin/sysnotice/batchDelete | 批量删除 | @Before(Tx.class) |
| batchRestore | /admin/sysnotice/batchRestore | 批量还原恢复回收站数据 | @Before(Tx.class) |
| chooseReceiver | /admin/sysnotice/chooseReceiver | 根据类型进入不同选择界面 |  |

### 系统管理 / 七牛配置

- 权限 key：`qiniu_config`；类型：菜单；打开方式：主区域；超管默认：是

#### 系统管理 / 七牛配置 / 七牛账号

- 权限 key：`qiniu`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/qiniu`
- 控制器：`QiniuAdminController`（cn/jbolt/_admin/qiniu/QiniuAdminController.java），类注解 @CheckPermission(PermissionKey.QINIU) @UnCheckIfSystemAdmin @OnlySaasPlatform
- 页面标题：七牛账号管理
- 查询条件：输入关键字搜索、账号类型、是否默认、是否启用
- 工具栏：查询、重置、新增
- 列表字段：序号、账号、编号SN、账号类型、AK、SK、Bucket个数、是否默认、是否启用、备注、创建时间、更新时间、创建人、更新人、操作
- 行操作：查看备注、修改、删除
- 表单字段：账号类型、账号、编号SN、AK、SK、是否默认、是、否、备注

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| uploadParas | /admin/qiniu/uploadParas | 七牛账号管理 Controller / public class QiniuAdminController extends JBoltBaseControlle | @UnCheck |
| index | /admin/qiniu | 首页 |  |
| options | /admin/qiniu/options | 作为其他组件的选项列表 | @CheckPermission({PermissionKey.QINIU,PermissionKey.QINIU_BUCKET}) |
| datas | /admin/qiniu/datas | 数据源 |  |
| add | /admin/qiniu/add | 新增 |  |
| checkDefaultExist | /admin/qiniu/checkDefaultExist | 检查是否存在冲突默认数据 |  |
| edit | /admin/qiniu/edit | 编辑 |  |
| save | /admin/qiniu/save | 保存 | @Before(Tx.class) |
| update | /admin/qiniu/update | 更新 | @Before(Tx.class) |
| delete | /admin/qiniu/delete | 删除 | @Before(Tx.class) |
| toggleEnable | /admin/qiniu/toggleEnable | 切换启用状态 | @Before(Tx.class) |
| toggleIsDefault | /admin/qiniu/toggleIsDefault | 切换toggleIsDefault | @Before(Tx.class) |

#### 系统管理 / 七牛配置 / 七牛Bucket

- 权限 key：`qiniu_bucket`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/qiniu/bucket`
- 控制器：`QiniuBucketAdminController`（cn/jbolt/_admin/qiniu/QiniuBucketAdminController.java），类注解 @CheckPermission(PermissionKey.QINIU_BUCKET) @UnCheckIfSystemAdmin @OnlySaasPlatform
- 页面标题：七牛Bucket管理
- 查询条件：输入关键字搜索、所属账号、地区
- 工具栏：查询、同步所有Bucket、新增
- 列表字段：序号、bucket名称、编码(SN)、所属七牛账号、所属地区、绑定域名、回调地址、回调body定义、回调Body类型、有效期(秒)、是否默认、是否启用、备注、创建时间、更新时间、创建人、更新人、操作
- 行操作：查看备注、修改、删除
- 表单字段：所属七牛账号、所属地区、bucket名称、编码(SN)、有效期(秒)、绑定域名、回调地址、回调body定义、回调Body类型、是否默认、是、否、备注

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/qiniu/bucket | 七牛Bucket管理 Controller / public class QiniuBucketAdminController extends JBoltBas |  |
| syncAllQiniuBuckets | /admin/qiniu/bucket/syncAllQiniuBuckets | 从线上同步所有账号的bucket |  |
| syncFromQiniu | /admin/qiniu/bucket/syncFromQiniu | 从线上同步bucket | @Before(Tx.class) |
| datas | /admin/qiniu/bucket/datas | 数据源 |  |
| options | /admin/qiniu/bucket/options | options数据源 |  |
| add | /admin/qiniu/bucket/add | 新增 |  |
| edit | /admin/qiniu/bucket/edit | 编辑 |  |
| save | /admin/qiniu/bucket/save | 保存 | @Before(Tx.class) |
| update | /admin/qiniu/bucket/update | 更新 | @Before(Tx.class) |
| delete | /admin/qiniu/bucket/delete | 删除 | @Before(Tx.class) |
| toggleIsDefault | /admin/qiniu/bucket/toggleIsDefault | 切换toggleIsDefault | @Before(Tx.class) |
| toggleEnable | /admin/qiniu/bucket/toggleEnable | 切换启用状态 | @Before(Tx.class) |

### 系统管理 / 敏感词词库

- 权限 key：`sensitive_word`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/sensitiveword`
- 控制器：`SensitiveWordAdminController`（cn/jbolt/_admin/sensitiveword/SensitiveWordAdminController.java），类注解 @CheckPermission(PermissionKey.SENSITIVE_WORD) @UnCheckIfSystemAdmin
- 页面标题：敏感词词库管理
- 列表字段：总量、启用/禁用、已加载、序号、敏感词、启用/禁用、操作
- 行操作：编辑、删除
- 表单字段：敏感词

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/sensitiveword |  |  |
| counts | /admin/sensitiveword/counts |  |  |
| add | /admin/sensitiveword/add |  |  |
| edit | /admin/sensitiveword/edit |  |  |
| reload | /admin/sensitiveword/reload |  |  |
| test | /admin/sensitiveword/test |  |  |
| mgr | /admin/sensitiveword/mgr |  |  |
| datas | /admin/sensitiveword/datas |  |  |
| save | /admin/sensitiveword/save |  |  |
| update | /admin/sensitiveword/update |  | @Before(Tx.class) |
| toggleEnable | /admin/sensitiveword/toggleEnable |  | @Before(Tx.class) |
| delete | /admin/sensitiveword/delete |  | @Before(Tx.class) |
| deleteByIds | /admin/sensitiveword/deleteByIds |  | @Before(Tx.class) |

## 系统监控

- 权限 key：`jbolt_monitor`；类型：菜单；打开方式：主区域；超管默认：是

### 系统监控 / Druid数据库监控

- 权限 key：`druid_monitor`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/druid/monitor`

### 系统监控 / 服务器监控

- 权限 key：`jbolt_server_monitor`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/servermonitor`
- 控制器：`ServerMonitorAdminController`（cn/jbolt/_admin/monitor/ServerMonitorAdminController.java），类注解 @CheckPermission(PermissionKey.JBOLT_SERVER_MONITOR) @UnCheckIfSystemAdmin
- 页面标题：服务器运行监控
- 工具栏：刷新

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/servermonitor |  |  |
| disk | /admin/servermonitor/disk |  |  |
| memory | /admin/servermonitor/memory |  |  |
| server | /admin/servermonitor/server |  |  |
| jvm | /admin/servermonitor/jvm |  |  |
| cpu | /admin/servermonitor/cpu |  |  |
| cache | /admin/servermonitor/cache |  |  |

### 系统监控 / 日志监控

- 权限 key：`jbolt_log_monitor`；类型：菜单；打开方式：主区域；超管默认：是

#### 系统监控 / 日志监控 / 登录日志

- 权限 key：`jbolt_login_log`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/loginlog`
- 控制器：`LoginLogAdminController`（cn/jbolt/_admin/loginlog/LoginLogAdminController.java），类注解 @CheckPermission(PermissionKey.JBOLT_LOGIN_LOG) @UnCheckIfSystemAdmin
- 页面标题：登录日志
- 查询条件：输入关键字搜索、日期范围
- 工具栏：查询、刷新
- 列表字段：序号、用户名(id)、登录状态、异地登录、登录时间、操作系统、浏览器、登录IP、登录地址

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/loginlog |  |  |
| detail | /admin/loginlog/detail | 登录日志controller / public class LoginLogAdminController extends JBoltBaseControlle |  |

#### 系统监控 / 日志监控 / 关键操作日志

- 权限 key：`systemlog`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/systemlog`
- 控制器：`SystemLogAdminController`（cn/jbolt/_admin/systemlog/SystemLogAdminController.java），类注解 @CheckPermission(PermissionKey.SYSTEMLOG) @UnCheckIfSystemAdmin
- 页面标题：系统操作日志
- 查询条件：输入关键字搜索、开始日期、截止日期
- 工具栏：查询、刷新
- 列表字段：序号、数据类型、操作类型、日志、操作人、记录时间
- 行操作：查看日志描述

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/systemlog |  |  |

### 系统监控 / 在线用户

- 权限 key：`online_user`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/onlineuser`
- 控制器：`OnlineUserAdminController`（cn/jbolt/_admin/onlineuser/OnlineUserAdminController.java），类注解 @CheckPermission(PermissionKey.ONLINE_USER) @UnCheckIfSystemAdmin
- 页面标题：在线用户管理
- 查询条件：输入关键字搜索、状态
- 工具栏：查询、刷新
- 列表字段：序号、用户名、会话ID、登录日志、在线状态、登录时间、更新时间、过期时间、离线时间、操作

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/onlineuser | 在线用户管理 Controller / public class OnlineUserAdminController extends JBoltBaseCont |  |
| datas | /admin/onlineuser/datas | 数据源 |  |
| stateOptions | /admin/onlineuser/stateOptions | 状态选项 |  |
| forcedOffline | /admin/onlineuser/forcedOffline | 强退用户 |  |

## 开发平台

- 权限 key：`dev_platform`；类型：菜单；打开方式：主区域；超管默认：是

### 开发平台 / 应用中心

- 权限 key：`application`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/app`
- 控制器：`ApplicationAdminController`（cn/jbolt/admin/appdevcenter/ApplicationAdminController.java），类注解 @CheckPermission(PermissionKey.APPLICATION) @UnCheckIfSystemAdmin @OnlySaasPlatform
- 页面标题：应用开发中心
- 查询条件：输入关键字搜索、应用类型、状态
- 工具栏：查询、创建新应用
- 列表字段：序号、应用名称、类型、AppId/AppSecret、简介、创建时间、更新时间、是否启用、接口校验Signature、关联对象、操作
- 行操作：应用开发中心关联对象、编辑应用基本信息
- 表单字段：应用名称、应用简介 (200字内)、接口校验 (Signature)、开启、关闭

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/app | 应用开发者中心 主要管理调用接口的Application 不管是APP还是小程序或者其它端应用 都可以集成进来 为调用开发API类应用准备 / public c |  |
| datas | /admin/app/datas | 数据源 |  |
| appinfo | /admin/app/appinfo | 查看app的appId和appSecret信息 | @Before(ApplicationValidator.class) |
| enableOptions | /admin/app/enableOptions | enable 启用状态数据源 |  |
| types | /admin/app/types | Api应用中心应用类型列表数据源 |  |
| typesInForm | /admin/app/typesInForm | Api应用中心应用类型列表数据源 form里用 |  |
| add | /admin/app/add |  |  |
| edit | /admin/app/edit |  | @Before(ApplicationValidator.class) |
| save | /admin/app/save | 保存 |  |
| update | /admin/app/update | 更新 |  |
| delete | /admin/app/delete | 删除 | @Before({Tx.class,ApplicationValidator.class}) |
| toggleEnable | /admin/app/toggleEnable | 切换状态 | @Before(ApplicationValidator.class) |
| toggleNeedCheckSign | /admin/app/toggleNeedCheckSign | 切换是否需要接口needchecksign | @Before(ApplicationValidator.class) |
| changeAppSecret | /admin/app/changeAppSecret | 变更AppSecret | @Before(ApplicationValidator.class) |
| linkTarget | /admin/app/linkTarget | 查看关联对象 | @Before(ApplicationValidator.class) |
| removeLinkTarget | /admin/app/removeLinkTarget | 解除关联对象 | @Before(ApplicationValidator.class) |

### 开发平台 / 微信公众平台

- 权限 key：`wechat_mpinfo`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/wechat/mpinfo`
- 控制器：`WechatMpinfoAdminController`（cn/jbolt/admin/wechat/mpinfo/WechatMpinfoAdminController.java），类注解 @CheckPermission(PermissionKey.WECHAT_MPINFO) @OnlySaasPlatform
- 页面标题：微信公众平台管理
- 查询条件：输入关键字搜索、类型、主体类型、认证、状态
- 工具栏：查询、新增
- 列表字段：序号、头像、名称、二维码、类型|主体、简介、备注、启用、认证、核心配置、关联应用、操作
- 行操作：点击查看二维码、公众平台基础配置、公众平台微信支付配置、公众平台其它配置、公众平台自定义菜单配置、公众平台素材库管理、公众平台关注时回复内容配置、公众平台自定义关键词回复配置、公众平台默认消息回复配置、公众平台用户管理、公众平台-应用开发中心关联设置、编辑公众平台信息
- 表单字段：名称、微信号、简介、备注

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/wechat/mpinfo | 微信公众平台管理 / public class WechatMpinfoAdminController extends JBoltBaseController  |  |
| datas | /admin/wechat/mpinfo/datas | 数据源 |  |
| uploadImage | /admin/wechat/mpinfo/uploadImage | 上传公众平台相关图片 |  |
| add | /admin/wechat/mpinfo/add | 进入新增表单 |  |
| save | /admin/wechat/mpinfo/save | 保存 |  |
| edit | /admin/wechat/mpinfo/edit | 编辑 |  |
| editLinkApp | /admin/wechat/mpinfo/editLinkApp | 编辑关联的APP |  |
| apps | /admin/wechat/mpinfo/apps | 获取application列表 通过关键字匹配 autocomplete组件使用 |  |
| update | /admin/wechat/mpinfo/update | 更新 |  |
| delete | /admin/wechat/mpinfo/delete | 删除一个公众平台 |  |
| clearAllConfigs | /admin/wechat/mpinfo/clearAllConfigs | 清空所有配置 慎用 |  |
| toggleEnable | /admin/wechat/mpinfo/toggleEnable | 切换启用状态 |  |
| toggleAuthenticated | /admin/wechat/mpinfo/toggleAuthenticated | 切换认证状态 |  |
| enableOptions | /admin/wechat/mpinfo/enableOptions | enable 启用状态数据源 |  |
| isAuthenticatedOptions | /admin/wechat/mpinfo/isAuthenticatedOptions | isAuthenticated  是否认证数据源 |  |
| typeOptions | /admin/wechat/mpinfo/typeOptions | 类型数据源 |  |
| subjectTypeOptions | /admin/wechat/mpinfo/subjectTypeOptions | 主体类型数据源 |  |
| submitLinkApp | /admin/wechat/mpinfo/submitLinkApp | 提交关联APP | @Before(Tx.class) |
| removeLinkApp | /admin/wechat/mpinfo/removeLinkApp | 解除关联APP | @Before(Tx.class) |

#### 开发平台 / 微信公众平台 / 基础配置

- 权限 key：`wechat_config_basemgr`；类型：按钮/逻辑权限；打开方式：主区域；超管默认：是

#### 开发平台 / 微信公众平台 / 菜单配置

- 权限 key：`wechat_menu`；类型：按钮/逻辑权限；打开方式：主区域；超管默认：是

#### 开发平台 / 微信公众平台 / 支付配置

- 权限 key：`wechat_config_paymgr`；类型：按钮/逻辑权限；打开方式：主区域；超管默认：是

#### 开发平台 / 微信公众平台 / 关注回复

- 权限 key：`wechat_autoreply_subscribe`；类型：按钮/逻辑权限；打开方式：主区域；超管默认：是

#### 开发平台 / 微信公众平台 / 关键词回复

- 权限 key：`wechat_autoreply_keywords`；类型：按钮/逻辑权限；打开方式：主区域；超管默认：是

#### 开发平台 / 微信公众平台 / 默认回复

- 权限 key：`wechat_autoreply_default`；类型：按钮/逻辑权限；打开方式：主区域；超管默认：是

#### 开发平台 / 微信公众平台 / 素材库

- 权限 key：`wechat_media`；类型：按钮/逻辑权限；打开方式：主区域；超管默认：是

#### 开发平台 / 微信公众平台 / 用户管理

- 权限 key：`wechat_user`；类型：按钮/逻辑权限；打开方式：主区域；超管默认：是

#### 开发平台 / 微信公众平台 / 其它配置

- 权限 key：`wechat_config_extramgr`；类型：按钮/逻辑权限；打开方式：主区域；超管默认：是

### 开发平台 / 报表设计器

- 权限 key：`ureport_designer`；类型：菜单；打开方式：iframe；超管默认：是；URL：`ureport/designer`

### 开发平台 / 打印设计器

- 权限 key：`hiprint_design`；类型：菜单；打开方式：iframe；超管默认：是；URL：`admin/hiprint`
- 控制器：`HiprintAdminController`（cn/jbolt/_admin/hiprint/HiprintAdminController.java），类注解 @CheckPermission(PermissionKey.HIPRINT_DESIGN) @UnCheckIfSystemAdmin @OnlySaasPlatform
- 页面标题：Hiprint-打印模板设计器

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/hiprint | jbolt hiprint 封装 / public class HiprintAdminController extends JBoltBaseControll |  |
| designer | /admin/hiprint/designer | 设计器 | @ActionKey("tpl/designer") |
| tpl | /admin/hiprint/tpl | 仓库管理 |  |
| tplContent | /admin/hiprint/tplContent | 模板获取 | @UnCheck @ActionKey("tpl/content") |
| tplDatas | /admin/hiprint/tplDatas | 模板库数据 | @ActionKey("tpl/datas") |
| tplJsonDataEditor | /admin/hiprint/tplJsonDataEditor | 模板库数据 | @ActionKey("tpl/jsonDataEditor") |
| tplLoad | /admin/hiprint/tplLoad | 模板库数据加载一个 | @ActionKey("tpl/load") |
| tplTestJsonData | /admin/hiprint/tplTestJsonData | 模板库数据加载一个 | @ActionKey("tpl/testJsonData") |
| tplAdd | /admin/hiprint/tplAdd | 新增模板 | @ActionKey("tpl/add") |
| tplEdit | /admin/hiprint/tplEdit | 编辑模板 | @ActionKey("tpl/edit") |
| submitTpl | /admin/hiprint/submitTpl | 提交模板 | @ActionKey("tpl/submit") |
| tplDelete | /admin/hiprint/tplDelete | 删除模板 | @ActionKey("tpl/delete") |

### 开发平台 / 代码生成器

- 权限 key：`jbolt_code_gen`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/codegen`
- 控制器：`CodeGenAdminController`（cn/jbolt/_admin/codegen/CodeGenAdminController.java），类注解 @CheckPermission(PermissionKey.JBOLT_CODE_GEN) @UnCheckIfSystemAdmin
- 页面标题：JBolt平台代码生成器
- 查询条件：输入关键字搜索、构建类型、数据源、样式类型、生成状态
- 工具栏：查询、回收站
- 列表字段：序号、数据库表、数据源、版本号、构建类型、是否树表、是否CRUD、是否可编辑、删除表前缀、Main Java Package、功能作者、子表数、样式类型、生成状态、创建时间、更新时间、操作
- 行操作：查看基础信息、编辑基础信息、设计详细生成配置、删除
- 表单字段：项目根路径、数据源、数据表、删除表名前缀、是否CRUD、是、否、树表样式、表格可编辑、开发作者、版本号、Main Java Package、Model Package、备注

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/codegen | JBolt 开发平台 - 可视化代码生成器 / public class CodeGenAdminController extends JBoltBaseCon |  |
| datas | /admin/codegen/datas | codeGen数据源 |  |
| autocompletedemodatas | /admin/codegen/autocompletedemodatas | autocomplete组件默认的demo数据源 | @UnCheck |
| types | /admin/codegen/types | 构建类型 |  |
| refreshEnumsCache | /admin/codegen/refreshEnumsCache | 刷新enum缓存 |  |
| enums | /admin/codegen/enums | 内置枚举类 |  |
| refreshCachesCache | /admin/codegen/refreshCachesCache | 刷新cache缓存 |  |
| refreshServicesCache | /admin/codegen/refreshServicesCache | 刷新service缓存 |  |
| caches | /admin/codegen/caches | 内置cahce类 |  |
| datasources | /admin/codegen/datasources | 系统数据源 |  |
| styles | /admin/codegen/styles | 样式类型数据源 |  |
| states | /admin/codegen/states | 生成状态数据源 |  |
| add | /admin/codegen/add | 添加主配置 |  |
| addSubTable | /admin/codegen/addSubTable | 添加子表配置 |  |
| edit | /admin/codegen/edit | 编辑主配置 |  |
| save | /admin/codegen/save | 保存基础信息 |  |
| update | /admin/codegen/update | 更新基础信息 |  |
| updateBase | /admin/codegen/updateBase | 详细配置里更新基础信息 |  |
| delete | /admin/codegen/delete | 删除 | @Before(Tx.class) |
| deleteByIds | /admin/codegen/deleteByIds | 删除批量 | @Before(Tx.class) |
| recoverByIds | /admin/codegen/recoverByIds | 批量还原 | @Before(Tx.class) |
| realDelete | /admin/codegen/realDelete | 真删除 | @Before(Tx.class) |
| tablesAutocomplete | /admin/codegen/tablesAutocomplete | 数据源中的可选表 |  |
| reloadTables | /admin/codegen/reloadTables | 重新对数据源的表进行加载 |  |
| uploadImg | /admin/codegen/uploadImg |  |  |
| createCodeGenDialog | /admin/codegen/createCodeGenDialog | 创建代码生成器对话框 |  |
| getLastCodegenId | /admin/codegen/getLastCodegenId |  |  |
| editCodeGenConfig | /admin/codegen/editCodeGenConfig | 进入代码生成详细配置UI |  |
| keyCacheColumns | /admin/codegen/keyCacheColumns | keycache column数据源 |  |
| autocompleteColumns | /admin/codegen/autocompleteColumns | autocomplete接口匹配 column数据源 |  |
| optionsTextColumns | /admin/codegen/optionsTextColumns | options接口 text column数据源 |  |
| optionsValueColumns | /admin/codegen/optionsValueColumns | options接口 value column数据源 |  |
| keyCacheBindColumns | /admin/codegen/keyCacheBindColumns | keycache bindColumn数据源 |  |
| intSortColumns | /admin/codegen/intSortColumns | 可以排序的int类型的columns |  |
| permissionPortal | /admin/codegen/permissionPortal | 加载权限菜单设置 |  |
| unbindPermission | /admin/codegen/unbindPermission | 确认清空解绑权限菜单 |  |
| keywordsMatchColumns | /admin/codegen/keywordsMatchColumns | 可以当做关键词查询匹配列的数据 |  |
| formPortal | /admin/codegen/formPortal | form portal预览 |  |
| testAjax | /admin/codegen/testAjax | 测试ajax success |  |
| formPortalEditMode | /admin/codegen/formPortalEditMode | form portal 编辑模式 |  |
| formPortalTest | /admin/codegen/formPortalTest | form portal test 测试预览 |  |
| tablePortalEditMode | /admin/codegen/tablePortalEditMode | table portal预览 编辑模式 |  |
| tablePortal | /admin/codegen/tablePortal | table portal预览 |  |
| tablePortalDatas | /admin/codegen/tablePortalDatas | table预览的demo数据 |  |
| initSortRankInform | /admin/codegen/initSortRankInform | 初始化form元素排序 | @Before(Tx.class) |
| initSortRankIntable | /admin/codegen/initSortRankIntable | 初始化form元素排序 | @Before(Tx.class) |
| formDictionaryOptions | /admin/codegen/formDictionaryOptions | 选择字典数据 |  |
| searchDictionaryOptions | /admin/codegen/searchDictionaryOptions | 选择字典数据 |  |
| routesOptions | /admin/codegen/routesOptions | 路由列表数据 |  |
| refreshRoutesCache | /admin/codegen/refreshRoutesCache | 刷新routes缓存 |  |
| genAll | /admin/codegen/genAll | 一次性生成model、主逻辑等内容 | @Before(Tx.class) |
| genModel | /admin/codegen/genModel | 一次性生成model | @Before(Tx.class) |
| genMainLogic | /admin/codegen/genMainLogic | 一次性生成mainLogic | @Before(Tx.class) |
| downloadTpl | /admin/codegen/downloadTpl | 模拟导出模板 |  |
| initImportExcel | /admin/codegen/initImportExcel | 模拟初始化打开导入excel的dialog |  |
| importExcel | /admin/codegen/importExcel | 模拟导入成功 |  |
| exportExcel | /admin/codegen/exportExcel | 模拟导出 |  |
| staticMethodClasses | /admin/codegen/staticMethodClasses | 关键词搜索静态方法 类 |  |
| javaPackageAutocomplete | /admin/codegen/javaPackageAutocomplete | javapackageautocomplete查询 |  |
| services | /admin/codegen/services | 关键词搜索service方法 |  |
| initBindPermission | /admin/codegen/initBindPermission | 初始化 绑定permission |  |
| bindPermission | /admin/codegen/bindPermission | 绑定permission |  |
| syncProjectPath | /admin/codegen/syncProjectPath | 同步本项目地址 |  |
| updateProjectPath | /admin/codegen/updateProjectPath | 更新projectpath |  |

### 开发平台 / 开发文档

- 权限 key：`admin_dev_doc`；类型：菜单；打开方式：主区域；超管默认：是

#### 开发平台 / 开发文档 / 数据库文档

- 权限 key：`admin_dev_doc_database`；类型：菜单；打开方式：主区域；超管默认：是；URL：`admin/devdoc/database`
- 控制器：`JBoltDatabaseDevDocController`（cn/jbolt/admin/devdoc/database/JBoltDatabaseDevDocController.java），类注解 @CheckPermission(PermissionKey.APPLICATION) @UnCheckIfSystemAdmin @OnlySaasPlatform
- 页面标题：项目数据库文档 （开发人员专用）
- 列表字段：配置项、配置值

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/devdoc/database |  |  |
| datasources | /admin/devdoc/database/datasources | JBolt平台内置后台开发平台使用的数据库开发文档 / public class JBoltDatabaseDevDocController extends J |  |
| tableLayer | /admin/devdoc/database/tableLayer | 打开一个数据源的table列表Layer页面 |  |
| tables | /admin/devdoc/database/tables | 查询所有可用数据源配置信息 |  |
| refreshTables | /admin/devdoc/database/refreshTables | 刷新表数据 |  |

## 独立逻辑权限

- 权限 key：`logic_permission`；类型：按钮/逻辑权限；打开方式：主区域；超管默认：否

### 独立逻辑权限 / Ureport报表查看权

- 权限 key：`ureport_detail`；类型：按钮/逻辑权限；打开方式：主区域；超管默认：否

## AI_数智化中心

- 权限 key：`jbolt_ai_dev`；类型：菜单；打开方式：主区域；超管默认：是

### AI_数智化中心 / AI_Tools注册中心

- 权限 key：`admin_ai_tools`；类型：菜单；打开方式：主区域；超管默认：是；URL：`/admin/ai/tools`
- 控制器：`AiToolsAdminController`（com/jboltai/platform/ai/admin/aifunction/AiToolsAdminController.java），类注解 @CheckPermission(PermissionKey.ADMIN_AI_TOOLS) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/tools", viewPath = "/_view/admin/ai/aifunction")
- 页面标题：AI_可调用Tools注册中心
- 查询条件：输入关键字搜索、类型、是否启用
- 工具栏：查询、重置
- 列表字段：序号、编码、类型、功能描述、Service Package/Class/Method、应用角色、调用前置要求、返回值特殊处理说明、哪些参数必填、调用次数、参数管理、是否启用、操作
- 行操作：AI_Function参数管理
- 表单字段：功能描述、调用前置要求、返回值特殊处理说明

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/ai/tools | AI_Tools注册中心 / public class AiToolsAdminController extends JBoltBaseController { |  |
| datas | /admin/ai/tools/datas | 数据源 |  |
| reloadServices | /admin/ai/tools/reloadServices |  |  |
| services | /admin/ai/tools/services | 关键词搜索service方法 |  |
| submit | /admin/ai/tools/submit |  |  |
| addForm | /admin/ai/tools/addForm | 新增 |  |
| addApiFunctionForm | /admin/ai/tools/addApiFunctionForm | 新增 api function |  |
| serviceMethods | /admin/ai/tools/serviceMethods |  |  |
| add | /admin/ai/tools/add | 新增 |  |
| save | /admin/ai/tools/save | 保存 | @Before(Tx.class) |
| saveApiFunction | /admin/ai/tools/saveApiFunction | 保存 | @Before(Tx.class) |
| updateApiFunction | /admin/ai/tools/updateApiFunction | 更新 | @Before(Tx.class) |
| edit | /admin/ai/tools/edit | 编辑 |  |
| update | /admin/ai/tools/update | 更新 | @Before(Tx.class) |
| deleteByIds | /admin/ai/tools/deleteByIds | 批量删除 | @Before(Tx.class) |
| delete | /admin/ai/tools/delete | 删除 | @Before(Tx.class) |
| toggleEnable | /admin/ai/tools/toggleEnable | 切换启用状态 | @Before(Tx.class) |
| autocompleteDatas | /admin/ai/tools/autocompleteDatas | autocomplete组件专用数据 | @UnCheck |
| updateColumn | /admin/ai/tools/updateColumn |  | @Before(Tx.class) |
| detail | /admin/ai/tools/detail |  |  |
| json | /admin/ai/tools/json |  |  |
| test | /admin/ai/tools/test |  |  |

### AI_数智化中心 / AI_功能&应用注册中心

- 权限 key：`admin_ai_page`；类型：菜单；打开方式：主区域；超管默认：是；URL：`/admin/ai/page`
- 控制器：`AiPageAdminController`（com/jboltai/platform/ai/admin/aipage/AiPageAdminController.java），类注解 @CheckPermission(PermissionKey.ADMIN_AI_PAGE) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/page", viewPath = "/_view/admin/ai/aipage")
- 页面标题：AI_可调用页面&应用注册中心
- 查询条件：输入关键字搜索、类型、是否启用、打开方式
- 工具栏：查询、重置、清空VDB、同步VDB
- 列表字段：序号、名称、编号、类型、描述、打开方式、页面地址、应用角色、权限KEY、使用数、是否启用、操作
- 行操作：编辑AI功能注册
- 表单字段：名称、编号、页面地址、描述、权限KEY

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/ai/page | AI功能注册中心 / public class AiPageAdminController extends JBoltBaseController { priv |  |
| datas | /admin/ai/page/datas | 数据源 |  |
| add | /admin/ai/page/add | 新增 |  |
| clearVDB | /admin/ai/page/clearVDB | 清空VBD |  |
| submitAllEnableToVDB | /admin/ai/page/submitAllEnableToVDB | 全部提交VBD |  |
| processAllEmptyRoles | /admin/ai/page/processAllEmptyRoles |  | @Before(Tx.class) |
| processAllRoles | /admin/ai/page/processAllRoles |  | @Before(Tx.class) |
| detail | /admin/ai/page/detail | 详情 |  |
| permissionAdd | /admin/ai/page/permissionAdd | 新增permission |  |
| permissions | /admin/ai/page/permissions |  |  |
| aiApps | /admin/ai/page/aiApps |  |  |
| permissionsAutocomplete | /admin/ai/page/permissionsAutocomplete |  |  |
| submitPermissions | /admin/ai/page/submitPermissions |  | @Before(Tx.class) |
| submitAiApps | /admin/ai/page/submitAiApps |  | @Before(Tx.class) |
| aiAppAdd | /admin/ai/page/aiAppAdd | 新增ai应用 |  |
| datasAdd | /admin/ai/page/datasAdd | 新增 业务数据 |  |
| save | /admin/ai/page/save | 保存 | @Before(Tx.class) |
| edit | /admin/ai/page/edit | 编辑 |  |
| update | /admin/ai/page/update | 更新 | @Before(Tx.class) |
| deleteByIds | /admin/ai/page/deleteByIds | 批量删除 | @Before(Tx.class) |
| delete | /admin/ai/page/delete | 删除 | @Before(Tx.class) |
| toggleEnable | /admin/ai/page/toggleEnable | 切换启用状态 | @Before(Tx.class) |

### AI_数智化中心 / AI_应用开发中心

- 权限 key：`admin_ai_app`；类型：菜单；打开方式：主区域；超管默认：是；URL：`/admin/ai/app`
- 控制器：`AiAppAdminController`（com/jboltai/platform/ai/admin/aiapp/AiAppAdminController.java），类注解 @CheckPermission(PermissionKey.ADMIN_AI_APP) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/app", viewPath = "/_view/admin/ai/aiapp")
- 页面标题：AI应用
- 工具栏：筛选条件
- 列表字段：序号、名称、操作、排序、定制优化、快速测试、挂载Function、挂载知识库、是否启用、保持上下文、图标LOGO、大背景图、AI对话头像、AI对话空内容背景图、UI_布局风格、UI_对话风格、用户输入长度限制、编码、介绍、system_message模板、prompt模板、首次进入场景 用户消息、首次进入场景AI提示语、知识库无匹配时回复内容、AI消息声明信息、应用角色、什么权限可使用、使用次数、发布应用、UI扩展、应用配置、API参数、创建时间、更新时间、发布状态、发布类型、发布时间、应用地址、左、右、上、下、显示参考记录、引用片段查看、可下载源文件、仅知识库模式、语音播放、重新生成、max_tokens、temperature、top_p、top_k
- 行操作：编辑AI应用、上移、下移、[移动端测试模式]、[PC端测试模式]、维护应用绑定的Functions、清空绑定的Functions、维护应用绑定的知识库、清空绑定的知识库、取消发布此AI应用、发布此AI应用、复制AI应用访问链接、打开AI应用
- 表单字段：名称、编码、介绍、图标LOGO、大背景图、AI对话头像、AI聊天区域空内容显示背景图、什么权限可使用、Max_Tokens、temperature、top_p、top_k

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/ai/app | AI应用 / public class AiAppAdminController extends JBoltBaseController { private A |  |
| count | /admin/ai/app/count |  |  |
| copy | /admin/ai/app/copy |  |  |
| downloadAppJson | /admin/ai/app/downloadAppJson | 下载应用配置文件JSON |  |
| addByJson | /admin/ai/app/addByJson |  |  |
| detail | /admin/ai/app/detail | 详情 |  |
| designer | /admin/ai/app/designer |  |  |
| updateCustomCssStyle | /admin/ai/app/updateCustomCssStyle |  | @Before(Tx.class) |
| updateCustomJavascript | /admin/ai/app/updateCustomJavascript |  |  |
| knowledgeConfigTopPortal | /admin/ai/app/knowledgeConfigTopPortal |  |  |
| knowledgeDatasPortal | /admin/ai/app/knowledgeDatasPortal |  |  |
| functionCallDatasPortal | /admin/ai/app/functionCallDatasPortal |  |  |
| chooseFunctions | /admin/ai/app/chooseFunctions |  |  |
| chooseKnowledgeBases | /admin/ai/app/chooseKnowledgeBases |  |  |
| linkKnowledgeBaseDatas | /admin/ai/app/linkKnowledgeBaseDatas |  |  |
| linkFunctionCallDatas | /admin/ai/app/linkFunctionCallDatas |  |  |
| unbindKnowledgeBase | /admin/ai/app/unbindKnowledgeBase |  | @Before(Tx.class) |
| unbindFunctionCall | /admin/ai/app/unbindFunctionCall |  | @Before(Tx.class) |
| logout | /admin/ai/app/logout |  | @Clear |
| publishConfigDialog | /admin/ai/app/publishConfigDialog |  |  |
| publishConfigPortal | /admin/ai/app/publishConfigPortal |  |  |
| linkFunctionDatas | /admin/ai/app/linkFunctionDatas |  |  |
| permissions | /admin/ai/app/permissions |  |  |
| aiKnowledgeBases | /admin/ai/app/aiKnowledgeBases |  |  |
| aiFunctions | /admin/ai/app/aiFunctions |  |  |
| aiFunctionChooseDatas | /admin/ai/app/aiFunctionChooseDatas |  |  |
| submitFunctions | /admin/ai/app/submitFunctions |  |  |
| aiKnowledgeBaseChooseDatas | /admin/ai/app/aiKnowledgeBaseChooseDatas |  |  |
| submitKnowledgeBases | /admin/ai/app/submitKnowledgeBases |  |  |
| updateOneColumn | /admin/ai/app/updateOneColumn |  | @Before(Tx.class) |
| toggleIsChatLlmEnableThinking | /admin/ai/app/toggleIsChatLlmEnableThinking |  |  |
| toggleIsFunctionLlmEnableThinking | /admin/ai/app/toggleIsFunctionLlmEnableThinking |  |  |
| toggleIsFuncResultLlmEnableThinking | /admin/ai/app/toggleIsFuncResultLlmEnableThinking |  |  |
| toggleIsKbResultLlmEnableThinking | /admin/ai/app/toggleIsKbResultLlmEnableThinking |  |  |
| aiKnowledgeCall | /admin/ai/app/aiKnowledgeCall | 快速测试使用请勿商用 |  |
| datas | /admin/ai/app/datas | 数据源 |  |
| sessionHistory | /admin/ai/app/sessionHistory |  |  |
| removeLinkKnowledgeBases | /admin/ai/app/removeLinkKnowledgeBases |  | @Before(Tx.class) |
| removeLinkFunctionCall | /admin/ai/app/removeLinkFunctionCall |  | @Before(Tx.class) |
| designerTest | /admin/ai/app/designerTest | 设计工具箱里加载测试预览 |  |
| test | /admin/ai/app/test | 测试专用入口 |  |
| clearSessionMsgs | /admin/ai/app/clearSessionMsgs |  | @UnCheck |
| open | /admin/ai/app/open | 后台管理内部打开应用正式使用的入口 | @UnCheck |
| genTableBySql | /admin/ai/app/genTableBySql | 根据sql生表 |  |
| jdadHistorySession | /admin/ai/app/jdadHistorySession |  |  |
| jboltDatabaseAiDesigner | /admin/ai/app/jboltDatabaseAiDesigner |  |  |
| add | /admin/ai/app/add | 新增 |  |
| save | /admin/ai/app/save | 保存 | @Before(Tx.class) |
| edit | /admin/ai/app/edit | 编辑 |  |
| qrcode | /admin/ai/app/qrcode | aiApp二维码 |  |
| initPublish | /admin/ai/app/initPublish | 进入初始化发布页面 |  |
| publish | /admin/ai/app/publish | publish发布 | @Before(Tx.class) |
| cancelPublish | /admin/ai/app/cancelPublish | 取消publish发布 | @Before(Tx.class) |
| update | /admin/ai/app/update | 更新 | @Before(Tx.class) |
| delete | /admin/ai/app/delete | 删除 | @Before(Tx.class) |
| up | /admin/ai/app/up | 排序 上移 | @Before(Tx.class) |
| down | /admin/ai/app/down | 排序 下移 | @Before(Tx.class) |
| initSortRank | /admin/ai/app/initSortRank | 排序 初始化 | @Before(Tx.class) |
| toggleEnable | /admin/ai/app/toggleEnable | 切换启用状态 | @Before(Tx.class) |
| toggleIsNeedRewriteQuery | /admin/ai/app/toggleIsNeedRewriteQuery | 切换is_need_rewrite_query | @Before(Tx.class) |
| toggleIsShowReference | /admin/ai/app/toggleIsShowReference | 切换is_show_reference | @Before(Tx.class) |
| toggleIsShowAiMsgBottomOptBtnTts | /admin/ai/app/toggleIsShowAiMsgBottomOptBtnTts | 切换is_show_ai_msg_bottom_opt_btn_tts | @Before(Tx.class) |
| toggleIsShowAiMsgBottomOptBtnReChat | /admin/ai/app/toggleIsShowAiMsgBottomOptBtnReChat | 切换is_show_ai_msg_bottom_opt_btn_re_chat | @Before(Tx.class) |
| toggleIsShowRefFragment | /admin/ai/app/toggleIsShowRefFragment | 切换is_show_reference | @Before(Tx.class) |
| toggleIsShowFileDownload | /admin/ai/app/toggleIsShowFileDownload | 切换is_show_file_download | @Before(Tx.class) |
| toggleIsOnlyKnowledgeMode | /admin/ai/app/toggleIsOnlyKnowledgeMode | 切换is_only_knowledge_mode | @Before(Tx.class) |
| toggleIsOnlyFunctionCallMode | /admin/ai/app/toggleIsOnlyFunctionCallMode | 切换is_only_function_call_mode | @Before(Tx.class) |
| toggleIsKeepContext | /admin/ai/app/toggleIsKeepContext | 切换is_keep_context | @Before(Tx.class) |
| toggleIsLeftBoxExtends | /admin/ai/app/toggleIsLeftBoxExtends | 切换is_left_box_extends | @Before(Tx.class) |
| toggleIsRightBoxExtends | /admin/ai/app/toggleIsRightBoxExtends | 切换is_right_box_extends | @Before(Tx.class) |
| toggleIsAiMsgInputBoxExtends | /admin/ai/app/toggleIsAiMsgInputBoxExtends | 切换is_ai_msg_input_box_extends | @Before(Tx.class) |
| toggleIsTopBoxExtends | /admin/ai/app/toggleIsTopBoxExtends | 切换is_top_box_extends | @Before(Tx.class) |
| getUiExtendCode | /admin/ai/app/getUiExtendCode |  |  |
| delCustomJavascriptFile | /admin/ai/app/delCustomJavascriptFile |  |  |
| loadCustomJavascriptCode | /admin/ai/app/loadCustomJavascriptCode |  |  |
| loadCustomDefaultJavascriptCode | /admin/ai/app/loadCustomDefaultJavascriptCode |  |  |
| updateUiExtendCode | /admin/ai/app/updateUiExtendCode |  |  |
| toggleIsBottomBoxExtends | /admin/ai/app/toggleIsBottomBoxExtends | 切换is_bottom_box_extends | @Before(Tx.class) |
| uploadIcon | /admin/ai/app/uploadIcon | 上传图标Logo |  |
| uploadAiAvatar | /admin/ai/app/uploadAiAvatar | 上传Ai对话头像 |  |
| uploadBgImg | /admin/ai/app/uploadBgImg | 上传Ai背景图 |  |
| uploadChatEmptyBgImg | /admin/ai/app/uploadChatEmptyBgImg | 上传Ai背景图（内容为空时 的背景📈） |  |
| aiCotClassAutocompleteDatas | /admin/ai/app/aiCotClassAutocompleteDatas | cot 数据关键词查询检索 |  |
| refreshAICotClass | /admin/ai/app/refreshAICotClass | 刷新cot缓存 |  |
| autocompleteDatas | /admin/ai/app/autocompleteDatas | autocomplete组件专用数据 | @UnCheck |
| options | /admin/ai/app/options | 得到select radio checkbox专用options数据 | @UnCheck |
| runIframePreview | /admin/ai/app/runIframePreview | 预览 |  |
| runIframeDownload | /admin/ai/app/runIframeDownload | 下载代码 |  |
| runIframeCode | /admin/ai/app/runIframeCode | 代码 |  |

### AI_数智化中心 / AI_知识库中心

- 权限 key：`admin_ai_kbc`；类型：菜单；打开方式：主区域；超管默认：是

#### AI_数智化中心 / AI_知识库中心 / AI_知识库

- 权限 key：`admin_ai_kb`；类型：菜单；打开方式：主区域；超管默认：是；URL：`/admin/ai/knowledgeBase`
- 控制器：`AiKnowledgeBaseAdminController`（com/jboltai/platform/ai/admin/aiknowledgebase/AiKnowledgeBaseAdminController.java），类注解 @CheckPermission(PermissionKey.ADMIN_AI_KB) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/knowledgeBase", viewPath = "/_view/admin/ai/aiknowledgebase")
- 页面标题：AI知识库管理
- 查询条件：输入关键字搜索、是否启用
- 工具栏：查询、重置
- 列表字段：序号、名称、简介、AI_FILE、AI_DOC、是否启用、操作
- 行操作：编辑AI知识库
- 表单字段：名称、简介

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/ai/knowledgeBase | AI知识库 / public class AiKnowledgeBaseAdminController extends JBoltBaseController  |  |
| count | /admin/ai/knowledgeBase/count | 统计数量 |  |
| datas | /admin/ai/knowledgeBase/datas | 数据源 |  |
| add | /admin/ai/knowledgeBase/add | 新增 |  |
| save | /admin/ai/knowledgeBase/save | 保存 | @Before(Tx.class) |
| edit | /admin/ai/knowledgeBase/edit | 编辑 |  |
| update | /admin/ai/knowledgeBase/update | 更新 | @Before(Tx.class) |
| delete | /admin/ai/knowledgeBase/delete | 删除 | @Before(Tx.class) |
| toggleEnable | /admin/ai/knowledgeBase/toggleEnable | 切换启用状态 | @Before(Tx.class) |
| autocompleteDatas | /admin/ai/knowledgeBase/autocompleteDatas |  | @UnCheck |

#### AI_数智化中心 / AI_知识库中心 / AI_知识库训练文件

- 权限 key：`admin_ai_file`；类型：菜单；打开方式：主区域；超管默认：是；URL：`/admin/ai/file`
- 控制器：`AiFileAdminController`（com/jboltai/platform/ai/admin/aifile/AiFileAdminController.java），类注解 @CheckPermission(PermissionKey.ADMIN_AI_FILE) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/file", viewPath = "/_view/admin/ai/aifile")
- 页面标题：AI文件
- 查询条件：输入关键字搜索、请选择知识库、上传类型、训练类型、拆分状态、训练状态、向量化状态
- 工具栏：查询、重置
- 列表字段：序号、文件名称、训练类型、上传类型、知识库、AI_DOC、训练异常、是否拆分、拆分时间、是否训练、训练时间、是否向量化、向量化时间、创建时间、更新时间、文件地址、操作
- 行操作：重新训练、删除
- 表单字段：知识库、上传、名称、文件地址、文件大小(KB)

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/ai/file | AI文件 / public class AiFileAdminController extends JBoltBaseController { private  |  |
| datas | /admin/ai/file/datas | 数据源 |  |
| add | /admin/ai/file/add | 新增 |  |
| pdfViewer | /admin/ai/file/pdfViewer |  | @UnCheck |
| addQa | /admin/ai/file/addQa | 新增 |  |
| addWebsite | /admin/ai/file/addWebsite | 新增 |  |
| addWebsiteQa | /admin/ai/file/addWebsiteQa | 新增 |  |
| urlToMd | /admin/ai/file/urlToMd | 网页Html转Markdown |  |
| reTrain | /admin/ai/file/reTrain |  |  |
| submitMulti | /admin/ai/file/submitMulti | 提交aiFile多文件 |  |
| submitByWebsite | /admin/ai/file/submitByWebsite |  | @Before(Tx.class) |
| upload | /admin/ai/file/upload | 本地上传 |  |
| uploadFiles | /admin/ai/file/uploadFiles | 上传多文件 |  |
| save | /admin/ai/file/save | 保存 |  |
| edit | /admin/ai/file/edit | 编辑 |  |
| update | /admin/ai/file/update | 更新 | @Before(Tx.class) |
| delete | /admin/ai/file/delete | 删除 | @Before(Tx.class) |
| autocompleteDatas | /admin/ai/file/autocompleteDatas | autocomplete组件专用数据 | @UnCheck |

#### AI_数智化中心 / AI_知识库中心 / AI_知识库训练结果

- 权限 key：`admin_ai_doc`；类型：菜单；打开方式：主区域；超管默认：是；URL：`/admin/ai/doc`
- 控制器：`AiDocAdminController`（com/jboltai/platform/ai/admin/aidoc/AiDocAdminController.java），类注解 @CheckPermission(PermissionKey.ADMIN_AI_DOC) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/doc", viewPath = "/_view/admin/ai/aidoc")
- 页面标题：AI DOC管理
- 查询条件：输入关键字搜索、请选择知识库、数据类型、是否训练、是否向量化
- 工具栏：查询、重置
- 列表字段：序号、问题/内容、答案、知识库、文件、数据类型、训练异常、训练状态、训练时间、向量化状态、向量化时间、创建时间、修改时间、操作
- 行操作：编辑AI问答库
- 表单字段：知识库、训练类型、非QA类、QA类、问题/内容、答案

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/ai/doc | AI问答库 / public class AiDocAdminController extends JBoltBaseController { private  |  |
| datas | /admin/ai/doc/datas | 数据源 |  |
| add | /admin/ai/doc/add | 新增 |  |
| save | /admin/ai/doc/save | 保存 | @Before(Tx.class) |
| edit | /admin/ai/doc/edit | 编辑 |  |
| update | /admin/ai/doc/update | 更新 | @Before(Tx.class) |
| delete | /admin/ai/doc/delete | 删除 | @Before(Tx.class) |
| linkFileDocs | /admin/ai/doc/linkFileDocs |  |  |

#### AI_数智化中心 / AI_知识库中心 / AI_文件标签

- 权限 key：`admin_ai_file_tag`；类型：菜单；打开方式：主区域；超管默认：是；URL：`/admin/ai/fileTag`
- 控制器：`AiFileTagAdminController`（com/jboltai/platform/ai/admin/aifiletag/AiFileTagAdminController.java），类注解 @CheckPermission(PermissionKey.ADMIN_AI_FILE_TAG) @UnCheckIfSystemAdmin @Before(JBoltAdminAuthInterceptor.class) @Path(value = "/admin/ai/fileTag", viewPath = "/_view/admin/ai/aifiletag")
- 页面标题：AI文件标签
- 查询条件：输入关键字搜索
- 工具栏：查询、重置
- 列表字段：序号、名称、别名、操作
- 行操作：编辑AI文件标签
- 表单字段：名称、别名

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/ai/fileTag | AI文件标签 / public class AiFileTagAdminController extends JBoltBaseController { pri |  |
| datas | /admin/ai/fileTag/datas | 数据源 |  |
| add | /admin/ai/fileTag/add | 新增 |  |
| save | /admin/ai/fileTag/save | 保存 | @Before(Tx.class) |
| edit | /admin/ai/fileTag/edit | 编辑 |  |
| update | /admin/ai/fileTag/update | 更新 | @Before(Tx.class) |
| deleteByIds | /admin/ai/fileTag/deleteByIds | 批量删除 | @Before(Tx.class) |
| delete | /admin/ai/fileTag/delete | 删除 | @Before(Tx.class) |
| autocompleteDatas | /admin/ai/fileTag/autocompleteDatas | autocomplete组件专用数据 | @UnCheck |

### AI_数智化中心 / AI_全局参数配置

- 权限 key：`admin_ai_config`；类型：菜单；打开方式：主区域；超管默认：是；URL：`/admin/ai/config`
- 控制器：`JBoltAIConfigController`（com/jboltai/platform/ai/admin/rescenter/JBoltAIConfigController.java），类注解 @CheckPermission(PermissionKey.ADMIN_AI_CONFIG) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/config", viewPath = "/_view/_admin/globalconfig")
- 页面标题：系统参数配置管理#if(typeName??): #end
- 查询条件：关键词查询、是否内置
- 工具栏：查询、刷新缓存、重置全部
- 列表字段：名称、操作、序号、参数名、参数KEY、参数值、所属类型、值类型、内置、更新时间、编辑
- 行操作：编辑、设置登录页面背景图、上移、下移、刷新、新增系统参数类型、初始化顺序
- 表单字段：启用、禁用、sysout-控制台、jboltlog-日志文件、默认、GIF验证码、气泡PNG、数字运算型、数字运算型（中文）、(数据单位：天)、树形表格管理、JS_TREE树管理、系统名称、color值：、color值参考：、[60~400之间的数字]、参数类型：、系统内置：、是、否、可视化选择、[看大图]、'>[看视频]、手动录入

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/ai/config |  |  |

### AI_数智化中心 / AI_资源配置中心

- 权限 key：`admin_ai_resource_center`；类型：菜单；打开方式：主区域；超管默认：是

#### AI_数智化中心 / AI_资源配置中心 / LLM大模型配置

- 权限 key：`admin_ai_llm`；类型：菜单；打开方式：主区域；超管默认：是；URL：`/admin/ai/llm`
- 控制器：`AILlmAdminController`（com/jboltai/platform/ai/admin/rescenter/llm/AILlmAdminController.java），类注解 @CheckPermission(PermissionKey.ADMIN_AI_LLM) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/llm", viewPath = "/_view/admin/ai/llm")
- 页面标题：LLM资源管理
- 查询条件：输入关键字搜索、供应商、私有化部署、Think可控、用途场景、状态
- 工具栏：查询、重置
- 列表字段：序号、模型资源名称、编码、大模型供应商、部署规格、模型用途场景、是否私有化部署、思考模式可控、API_KEY、API_URL、API_SECRET、max_tokens(默认)、temperature:温度(默认)、top_p:多样性(默认)、top_k:采样(默认)、频率限制、备注、测试、是否启用、操作
- 行操作：点击执行LLM大模型资源测试、编辑LLM大模型资源
- 表单字段：大模型资源名称、编码、大模型供应商、部署规格、API_URL、API_KEY、API_SECRET、max_tokens(默认)、temperature(默认)、top_P(默认)、tok_K(默认)、频率限制、备注

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| enableList | /admin/ai/llm/enableList | LLM资源管理 / public class AILlmAdminController extends JBoltBaseController { privat | @CheckPermission({PermissionKey.ADMIN_AI_LLM,PermissionKey.ADMIN_AI_APP}) |
| index | /admin/ai/llm | 首页 |  |
| datas | /admin/ai/llm/datas | 数据源 |  |
| testById | /admin/ai/llm/testById | 列表里测试 |  |
| formTest | /admin/ai/llm/formTest | 表单里测试数据是否配置正常 |  |
| add | /admin/ai/llm/add | 新增 |  |
| save | /admin/ai/llm/save | 保存 | @Before(Tx.class) |
| edit | /admin/ai/llm/edit | 编辑 |  |
| update | /admin/ai/llm/update | 更新 | @Before(Tx.class) |
| delete | /admin/ai/llm/delete | 删除 | @Before(Tx.class) |
| toggleEnable | /admin/ai/llm/toggleEnable | 切换启用状态 | @Before(Tx.class) |
| autocompleteDatas | /admin/ai/llm/autocompleteDatas | autocomplete组件专用数据 | @UnCheck |
| options | /admin/ai/llm/options | 得到select radio checkbox专用options数据 | @UnCheck |

#### AI_数智化中心 / AI_资源配置中心 / Embedding模型配置

- 权限 key：`admin_ai_embedding`；类型：菜单；打开方式：主区域；超管默认：是；URL：`/admin/ai/embedding`
- 控制器：`AiEmbeddingAdminController`（com/jboltai/platform/ai/admin/rescenter/embedding/AiEmbeddingAdminController.java），类注解 @CheckPermission(PermissionKey.ADMIN_AI_EMBEDDING) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/embedding", viewPath = "/_view/admin/ai/embedding")
- 页面标题：Embedding资源管理
- 查询条件：输入关键字搜索、大模型类型、是否私有化部署、是否启用
- 工具栏：查询、重置
- 列表字段：序号、模型资源名称、编码、模型供应商、部署规格、是否私有化部署、API_KEY、API_URL、API_SECRET、维度、频率限制、备注、测试、是否启用、操作
- 行操作：点击执行Embedding模型资源测试、编辑Embedding模型资源
- 表单字段：Embedding模型资源名称、编码、Embedding模型供应商、部署规格、API_URL、API_KEY、API_SECRET、维度、频率限制、备注

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/ai/embedding | Embedding模型资源配置 / public class AiEmbeddingAdminController extends JBoltBaseContr |  |
| datas | /admin/ai/embedding/datas | 数据源 |  |
| add | /admin/ai/embedding/add | 新增 |  |
| save | /admin/ai/embedding/save | 保存 | @Before(Tx.class) |
| getEmbeddingModelDimension | /admin/ai/embedding/getEmbeddingModelDimension | 获取Embedding模型维度 |  |
| formTest | /admin/ai/embedding/formTest | 表单里测试数据是否配置正常 |  |
| testById | /admin/ai/embedding/testById | 列表里测试 |  |
| edit | /admin/ai/embedding/edit | 编辑 |  |
| update | /admin/ai/embedding/update | 更新 | @Before(Tx.class) |
| delete | /admin/ai/embedding/delete | 删除 | @Before(Tx.class) |
| toggleIsLocal | /admin/ai/embedding/toggleIsLocal | 切换is_local | @Before(Tx.class) |
| toggleEnable | /admin/ai/embedding/toggleEnable | 切换启用状态 | @Before(Tx.class) |
| autocompleteDatas | /admin/ai/embedding/autocompleteDatas | autocomplete组件专用数据 | @UnCheck |
| options | /admin/ai/embedding/options | 得到select radio checkbox专用options数据 | @UnCheck |

#### AI_数智化中心 / AI_资源配置中心 / 向量数据库配置

- 权限 key：`admin_ai_vdb`；类型：菜单；打开方式：主区域；超管默认：是；URL：`/admin/ai/vdb`
- 控制器：`AiVdbAdminController`（com/jboltai/platform/ai/admin/rescenter/vdb/AiVdbAdminController.java），类注解 @CheckPermission(PermissionKey.ADMIN_AI_VDB) @UnCheckIfSystemAdmin @Path(value = "/admin/ai/vdb", viewPath = "/_view/admin/ai/vdb")
- 页面标题：向量数据库资源配置
- 查询条件：输入关键字搜索、类型、是否启用
- 工具栏：查询、重置
- 列表字段：序号、名称、编码、类型、Embeding模型、集合列表、URL、用户名、密码、数据库名、创建时间、更新时间、备注、测试、系统Main资源、是否启用、操作
- 行操作：查看集合列表、点击执行向量数据库资源测试、编辑向量数据库资源配置
- 表单字段：名称、编码、类型、URL、用户名、密码、数据库名、Embeding模型、备注

| 动作 | URL | 说明（Javadoc） | 注解 |
|---|---|---|---|
| index | /admin/ai/vdb | 向量数据库资源配置 / public class AiVdbAdminController extends JBoltBaseController { priv |  |
| collections | /admin/ai/vdb/collections | collections |  |
| listCollections | /admin/ai/vdb/listCollections | 获取集合数据 |  |
| checkAndInitBuildInCollections | /admin/ai/vdb/checkAndInitBuildInCollections |  |  |
| datas | /admin/ai/vdb/datas | 数据源 |  |
| formTest | /admin/ai/vdb/formTest | 表单里测试数据是否配置正常 |  |
| testById | /admin/ai/vdb/testById | 列表里测试 |  |
| add | /admin/ai/vdb/add | 新增 |  |
| save | /admin/ai/vdb/save | 保存 | @Before(Tx.class) |
| edit | /admin/ai/vdb/edit | 编辑 |  |
| update | /admin/ai/vdb/update | 更新 | @Before(Tx.class) |
| delete | /admin/ai/vdb/delete | 删除 | @Before(Tx.class) |
| toggleEnable | /admin/ai/vdb/toggleEnable | 切换启用状态 | @Before(Tx.class) |
| toggleIsMain | /admin/ai/vdb/toggleIsMain | 切换isMain | @Before(Tx.class) |
| autocompleteDatas | /admin/ai/vdb/autocompleteDatas | autocomplete组件专用数据 | @UnCheck |
| options | /admin/ai/vdb/options | 得到select radio checkbox专用options数据 | @UnCheck |

## 菜单之外的路由

这些路由不直接挂在菜单上，作为子页面、公共接口、对外 API 或测试入口存在。

| 路由前缀 | 控制器 | 动作数 | 动作 |
|---|---|---|---|
| /admin/ai/appMessage | AiAppMessageAdminController | 6 | userHistoryDatas、add、save、edit、update、delete |
| /admin/ai/appSession | AiAppSessionAdminController | 4 | userHistoryDatas、save、update、delete |
| /admin/ai/common | JBoltAICommonController | 7 | llmTypes、embeddingTypes、vdbTypes、getAiModelBySn、getEmbeddingModelBySn、aiRateLimitTypes、llmUseForOptions |
| /admin/ai/dashboard | AiDashboardAdminController | 1 | summarys |
| /admin/ai/functionParam | AiFunctionParamAdminController | 9 | index、datas、add、save、edit、update、toggleIsRequired、updateColumn、sync |
| /admin/ai/genUI | JBoltAIGenUIController | 2 | preview、clear |
| /admin/ai/smartSearch | JBoltAiSmartSearchAdminController | 1 | index |
| /admin/ai/test/render | TestAiRenderController | 1 | formSubmit |
| /admin/customBusiness | CustomBusinessAdminController | 10 | index、datas、add、save、edit、update、delete、toggleEnable、autocompleteDatas、options |
| /admin/jboltfileChange | JboltFileChangeAdminController | 1 | index |
| /ai/app/guest | AiAppGuestController | 10 | clearSessionMsgs、downloadFile、index、sessionHistory、userHistorySessionDatas、userHistoryMessageDatas、linkFileDocs、runIframePreview、runIframeDownload、runIframeCode |
| /ai/app/pwd | AiAppPwdController | 10 | clearSessionMsgs、index、login、sessionHistory、userHistorySessionDatas、userHistoryMessageDatas、linkFileDocs、runIframePreview、runIframeDownload、runIframeCode |
| /ai/app/system | AiAppSystemController | 6 | clearSessionMsgs、downloadFile、index、runIframePreview、runIframeDownload、runIframeCode |
| /ai/app/systemIframe | AiAppSystemIFrameController | 9 | clearSessionMsgs、userHistoryMessageDatas、sessionHistory、userHistorySessionDatas、login、index、runIframePreview、runIframeDownload、runIframeCode |
| /api/jwt/refresh | JBoltRefreshJwtApiController | 1 | index |
| /api/user | JBoltUserInfoApiController | 2 | mineInfo、updateMineInfo |
| /api/user/auth | JBoltUserAuthApiController | 2 | index、permissionKeys |
| /api/ws/guest/token | JBoltWebsocketGuestTokenApiController | 2 | gen、check |
| /api/ws/guest/token/v2 | JBoltWebsocketGuestTokenApiV2Controller | 2 | gen、check |
| /admin | AdminIndexController | 13 | index、menu、lockSystem、unLockSystem、dashboard、relogin、logout、terminalOffline、forcedOffline、login、redirectToScreenLock、captcha、myToken |
| /admin/dictionarytype | DictionaryTypeAdminController | 9 | mgr、datas、codeGenOptions、add、edit、save、update、delete、toggleEnable |
| /admin/globalconfig/type | GlobalConfigTypeAdminController | 10 | options、datas、add、edit、save、update、delete、up、down、initRank |
| /admin/jboltstyle | JBoltStyleAdminController | 1 | change |
| /admin/msgcenter | JBoltMsgCenterAdminController | 14 | index、layer、sysNoticePortalDatas、sysNoticeDetail、todoMarkAsRead、sysNoticeMarkAsRead、sysNoticeMarkAllAsRead、sysNoticeMarkMultiAsRead、userSysNoticeDatas、userTodoDatas、privateMessagePortalDatas、todoPortalDatas、userUnreadInfo、todoDetail |
| /admin/neditor/preview | NeditorPreviewAdminController | 1 | index |
| /admin/neditor/upload | NeditorUploadAdminController | 5 | index、wordimg、image、catchImage、video |
| /admin/pjaxerror | PjaxErrorController | 1 | error |
| /admin/redis | RedisAdminController | 1 | removeCurrentDatabaseAllDatas |
| /admin/rolepermission | RolePermissionAdminController | 5 | setting、settingTree、submit、getCheckeds、clear |
| /admin/summernote/upload | SummernoteUploadAdminController | 2 | index、image |
| /admin/todo | TodoAdminController | 10 | add、edit、save、update、delete、deleteByIds、updateState、batchUpdateState、dashboardDatas、detail |
| /admin/topnav/menu | TopnavMenuAdminController | 5 | setting、getChecked、clear、clearAll、submit |
| /admin/userconfig | UserConfigAdminController | 3 | index、toggleBooleanConfig、changeStringValue |
| /api/test | ApiTestController | 9 | paycallback、unifiedorder、go、floor、uploadImgUseOpenApi、uploadImage、login、refreshJwt、applyJwt |
| /admin/codegen/modelattr | CodeGenModelAttrAdminController | 25 | datas、toggleIsPkey、toggleIsNeedCheckExists、toggleIsTableCol、toggleIsImportCol、toggleIsExportCol、toggleIsNeedTranslate、toggleIsSortable、toggleIsSearchEle、toggleIsFuzzyQuery、toggleIsFormEle、toggleIsRequired、toggleIsSearchRequired、toggleIsKeywordsColumn、toggleIsSingleLine、toggleIsTableSwitchbtn、updateC |
| /admin/wechat/autoreply | WechatAutoReplyAdminController | 10 | subscribeReplyMgr、keywordsReplyMgr、defaultReplyMgr、add、edit、delete、toggleEnable、replyTypes、save、update |
| /admin/wechat/config | WechatConfigAdminController | 7 | baseMgr、extraMgr、uploadWechatMusicImgMedia、submitBaseConfig、submitExtraConfig、payMgr、submitPayConfig |
| /admin/wechat/keywords | WechatKeywordsAdminController | 7 | index、add、edit、types、save、update、delete |
| /admin/wechat/media | WechatMediaAdminController | 7 | index、download、choose、chooseIt、types、syncAll、syncNewDatas |
| /admin/wechat/menu | WechatMenuAdminController | 9 | mgr、level1List、level2List、edit、save、update、delete、publish、types |
| /admin/wechat/replycontent | WechatReplyContentAdminController | 12 | index、add、edit、up、down、initRank、delete、toggleEnable、types、uploadImage、save、update |
| /admin/wechat/user | WechatUserAdminController | 5 | index、sexOptions、sync、toggleEnable、syncUser |
| /api/wechat | JBoltWechatApiController | 2 | authorize、authCallback |
| /api/wxa | JBoltWxaApiController | 9 | login、refreshJwt、decryptUserInfo、decryptPhoneNumber、bindOtherUser、loginSystemUser、unbindOtherUser、myWechatUserInfo、updateMyWechatUserInfo |
| /wx/msg | WechatMsgController | 0 |  |
| /wxa/msg | WechatWxaMsgController | 0 |  |
| /test/wechat | JBoltWechatTestApiController | 1 | index |
