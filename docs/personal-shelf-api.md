# 个人随手栏接口与数据约定

所属模块 workspace，前后端实现与本地验收已完成；验证范围及未部署边界见[专项进度](personal-shelf-progress.md)。

所有接口以 `/api/workspace/shelf` 为前缀，要求后台人类登录。租户、用户、portal 只取可信认证上下文，管理员也不能读取其他用户的便签。响应不缓存。沿用 ApiResponse：业务冲突为 HTTP 200、业务 code 409；认证/授权错误沿用宿主协议。Long ID 和 version 在 JSON 中为字符串，客户端不得转 Number。

| 方法与后缀 | 语义 |
|---|---|
| GET 空后缀 | 当前 scope、mode、列表 version、bookmarks 及可用 targets |
| POST /bookmarks | menuId、alias；去重收藏，重复返回当前列表 |
| PUT /bookmarks/{id} | version、alias；修改个人名称 |
| DELETE /bookmarks/{id}?version=… | 按列表版本取消收藏 |
| PUT /bookmarks/order | version、完整 ids 排列；重复、缺项、非本人 ID 均拒绝 |
| PUT /mode | version、mode（drawer/floating/docked） |
| GET /notes | 本人全部有效便签；清理本人的过期回收站正文 |
| GET /notes/{id} | 读取当前便签及版本，用于核对超时或解决冲突 |
| POST /notes | requestKey（UUID）、note；同一身份内相同请求键不重复创建 |
| PUT /notes/{id} | version、note；只编辑 active 便签，冲突不覆盖 |
| PUT /notes/{id}/status | version、status（active/archived/trash）；回收站只允许恢复 active |

note 包含 title、kind（text/checklist）、content、items（id/text/done）、color（neutral/purple/green/amber）、pinned、relatedMenuId。返回额外的 id/requestKey/status/trashedAt/version/updatedAt/relatedTarget。更换类型不丢弃原有文字或清单。null relatedMenuId 明确移除关联。

menuId=0 表示首页，其他入口从 IAM 公开查询获得，只接受已授权的内部 ROUTE 菜单；不保存 URL 参数。收藏保存稳定菜单 ID 与个人别名，展示实时解析。失效 target 返回 null，不回传旧的菜单路径和名称。已有失效关联不妨碍编辑便签；新关联必须授权。

列表变更以 owner 行锁和列表版本保护。便签以 owner 行锁保护容量，再以单条版本防止覆盖；不同便签有独立版本。更新失败回滚，客户端不得把失败当已保存。新建超时通过 requestKey 核对列表；编辑超时读取单条当前版本与内容核对，不直接重复提交。

回收站保存30天；超过期限立刻不可读取或恢复，并在本人下次加载列表时物理清除。无人访问时不承诺后台定时清理。归档/删除不销毁正文，恢复 active 仍受200张容量限制。个人配置重置不触及这三张表。

升级为新增表，旧应用不读取这些表；回退应用无需删除数据。禁止改写已部署的 workspace V1。便签正文不进入操作日志、URL、浏览器持久化或其他用户接口。
