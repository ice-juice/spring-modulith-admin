# 06 JBolt 运行观察记录

> 记录日期：2026-11-10。运行对象：JBolt 运行副本 `..\jbolt_reference_run`（去掉 AI 模块，JDK 8u512，监听 `127.0.0.1:8090`）。
> 数据库：临时 Windows 免安装 MySQL 8.4.10（`127.0.0.1:3306`，库 `jboltai`）。WSL MySQL 启用后改为 WSL 实例。

## 1. 启动过程观察

- 启动耗时 11~22 秒。`JBoltAutoInitData` 在空库上自动完成：超级管理员 admin（总管理）、43 条权限资源（32 个菜单 + 11 个按钮/逻辑权限，AI 分支已从运行副本的初始化 JSON 去掉）、系统内置 Application（“内置_平台自身”）。
- 全局参数采用“访问时懒初始化”：第一次读取某个参数时才写入 `jb_global_config`（首轮启动后 14 条，访问各页面后 39 条）。`JBOLT_LOGIN_USE_CAPTURE` 在库里不存在时按“启用验证码”处理。
- 内置字典默认不初始化（`onstart_check_and_init_dictionary_enable=false`）。运行副本已改为 `true`，启动后写入 41 个字典类型、301 条字典项。新系统应在首次部署时自动初始化内置字典。
- 每分钟执行一次在线用户清理任务（`jb_online_user` 中过期会话改为离线并删除）。
- 日志：开发模式下每条 SQL 都输出（Druid full SQL log），控制台编码是 UTF-8，Windows 控制台按 GBK 显示会乱码，读日志文件即可。

## 2. 登录页

浏览器打开 `/admin/login`：系统名称“JBoltAI数智化开发平台”，用户名、密码、验证码（图片，点击刷新）、“保持登录”复选框、“登录系统”按钮，底部版权链接。登录请求是 AJAX POST，成功返回 `{"state":"ok"}`，前端提交的密码为 `2 位随机串 + md5(明文) + 3 位随机串`（与反编译结论一致）。

验证码机制（已用 HTTP 实测带验证码的完整登录成功）：

- 基于 JFinal `CaptchaRender`：4 位字符取自 `3456789ABCDEFGHJKMNPQRSTUVWXYabcdefghjkmnpqrstuvwxy`，校验不区分大小写；验证码 key 放在 cookie `_jfinal_captcha`，值存在服务端内存缓存，有效期 180 秒，校验成功后作废，输错不作废。
- 服务重启后，重启前打开的登录页上的验证码全部失效，需要刷新登录页；登录失败时前端会清空密码并自动刷新验证码图片。
- 默认图形验证码有旋转、干扰线和背景干扰字符，较难辨认。全局参数“系统登录页验证码类型”（`JBOLT_LOGIN_CAPTURE_TYPE`）可选 `default`、`gif`、`bubble_png`、`number_calc`（算术）、`number_calc_cn`（中文算术）；`JBOLT_LOGIN_USE_CAPTURE` 可关闭验证码。
- 登录日志状态码：1 成功、2 验证码错误、3 用户名或密码错误、4 已禁用、5 未分配角色、6 未知异常。新系统的登录日志可以沿用这套状态划分。

## 3. 菜单页面巡检

脚本 `tools/jbolt-research/crawl_reference_run.py` 以默认管理员登录后，对权限表中每个带 URL 的菜单分别做普通请求和 pjax 请求（请求头 `X-PJAX: true`），并请求 `<url>/datas` 统计数据量。结果明细在 `jbolt-runtime-crawl.json`。巡检期间临时关闭了验证码，结束后已恢复为启用并重启。

| 菜单 | 权限 key | URL | 普通/pjax | 页面标题 | datas 条数 |
|---|---|---|---|---|---|
| 操作台 | dashboard | admin/dashboard | 200 / 200 | JBoltAI数智化开发平台 | |
| 用户管理 | user | admin/user | 200 / 200 | 系统用户管理 | 1 |
| 部门管理 | dept | admin/dept | 200 / 200 | 部门管理 | 0 |
| 岗位管理 | post | admin/post | 200 / 200 | 岗位管理 | 0 |
| 角色管理 | role | admin/role | 200 / 200 | 角色管理 | 0 |
| 顶部导航 | topnav | admin/topnav | 200 / 200 | 顶部导航管理 | 0 |
| 权限资源 | permission | admin/permission | 200 / 200 | 系统资源权限管理 | 5（一级） |
| 数据字典 | dictionary | admin/dictionary | 200 / 200 | 字典管理 | |
| 全局参数 | globalconfig | admin/globalconfig | 200 / 200 | 系统参数配置管理 | 39 |
| 系统通知 | sys_notice | admin/sysnotice | 200 / 200 | 系统通知管理 | 0 |
| 七牛账号 | qiniu | admin/qiniu | 200 / 200 | 七牛账号管理 | 0 |
| 七牛Bucket | qiniu_bucket | admin/qiniu/bucket | 200 / 200 | 七牛Bucket管理 | 0 |
| 敏感词词库 | sensitive_word | admin/sensitiveword | 200 / 200 | 敏感词词库管理 | 0 |
| Druid数据库监控 | druid_monitor | admin/druid/monitor | 200 / 200 | （Druid 自带页面） | |
| 服务器监控 | jbolt_server_monitor | admin/servermonitor | 200 / 200 | 服务器运行监控 | |
| 登录日志 | jbolt_login_log | admin/loginlog | 200 / 200 | 登录日志 | |
| 关键操作日志 | systemlog | admin/systemlog | 200 / 200 | 系统操作日志 | |
| 在线用户 | online_user | admin/onlineuser | 200 / 200 | 在线用户管理 | 1 |
| 应用中心 | application | admin/app | 200 / 200 | 应用开发中心 | 1 |
| 微信公众平台 | wechat_mpinfo | admin/wechat/mpinfo | 200 / 200 | 微信公众平台管理 | 0 |
| 打印设计器 | hiprint_design | admin/hiprint | 200 / 200 | Hiprint-打印模板设计器 | |
| 代码生成器 | jbolt_code_gen | admin/codegen | 200 / 200 | JBolt平台代码生成器 | 0 |
| 数据库文档 | admin_dev_doc_database | admin/devdoc/database | 200 / 200 | 项目数据库文档（开发人员专用） | |
| 报表设计器 | ureport_designer | ureport/designer | 404 / 404 | | |

结论：24 个带 URL 的菜单中 23 个正常渲染、无异常信息；报表设计器 404 是因为 `jbolt_ureport_enable=false`（默认关闭），与去掉 AI 模块无关。去掉 AI 模块后的运行副本可以完整展示平台核心后台。

## 4. 待人工浏览补充

按 Browser Runtime 规则，后台登录的密码和验证码必须由用户本人在浏览器中输入，Agent 不能代填。以下交互细节需要登录后在浏览器中查看并补充到本文：

- 主框架：左侧菜单、顶部导航切换、多选项卡、全局搜索、锁屏、主题切换。
- 列表页：JBoltTable 的分页、筛选、树表、行内编辑、导入导出。
- 表单：新增/编辑弹窗、校验提示、部门树与用户选择组件。
- 权限：新建角色、只授权部分菜单和按钮，用该角色用户登录后的菜单与按钮差异。
- 会话：保持登录、单终端登录被顶替、强制下线、锁屏解锁。

