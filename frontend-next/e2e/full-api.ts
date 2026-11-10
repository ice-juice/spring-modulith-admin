import type { Page } from '@playwright/test'
const stamp = '2026-11-10T08:00:00Z'
export async function fullApi(page: Page) {
  const writes: { method: string; path: string; body: Record<string, unknown> }[] = [], unmatched: string[] = []
  const permissionKeys = [
    ...['user', 'role', 'department', 'post', 'permission', 'navigation'].flatMap(resource => ['view', 'create', 'update', 'delete'].map(action => 'iam:' + resource + ':' + action)),
    'iam:role:grant', 'iam:user:reset-password', 'auth:session:view', 'auth:session:revoke', 'audit:login:view', 'audit:operation:view',
    ...['dictionary', 'parameter'].flatMap(resource => ['view', 'create', 'update', 'delete'].map(action => 'config:' + resource + ':' + action)),
    'file:file:view', 'file:file:upload', 'file:file:delete',
    ...['view', 'create', 'update', 'delete', 'restore'].map(action => 'message:notice:' + action), 'message:todo:view', 'message:todo:assign',
    'monitor:server:view', 'monitor:sql:view', 'monitor:sql:reset', 'monitor:cache:view', 'monitor:cache:clear', 'monitor:event:view', 'monitor:event:retry', 'monitor:database:view',
    ...['view', 'create', 'update', 'delete', 'rotate', 'link'].map(action => 'openapi:app:' + action),
  ]
  const menus = [
    ['users', '用户管理', '/iam/users', 'iam:user:view'], ['roles', '角色管理', '/iam/roles', 'iam:role:view'], ['departments', '部门管理', '/iam/departments', 'iam:department:view'], ['posts', '岗位管理', '/iam/posts', 'iam:post:view'], ['permissions', '权限资源', '/iam/permissions', 'iam:permission:view'], ['navigation', '顶部导航', '/iam/navigation', 'iam:navigation:view'],
    ['dictionary', '数据字典', '/config/dictionaries', 'config:dictionary:view'], ['parameters', '系统参数', '/config/parameters', 'config:parameter:view'], ['files', '文件管理', '/file/files', 'file:file:view'], ['notices', '系统通知', '/message/notices', 'message:notice:view'], ['todos', '待办管理', '/message/todos', 'message:todo:view'], ['server', '服务器监控', '/monitor/server', 'monitor:server:view'], ['database', '数据库文档', '/monitor/database', 'monitor:database:view'], ['apps', '应用中心', '/openapi/apps', 'openapi:app:view'],
  ].map(([id, name, path, permission]) => ({ id, name, path, permission, parentId: 'org', type: 'MENU', icon: 'Grid', openMode: 'ROUTE', children: [] }))
  const preferences = { locale: 'zh-CN', timeZone: 'Asia/Shanghai', theme: 'light', tabsEnabled: true, keepTabsAfterReload: false }
  const me = { id: '101', username: 'e2e.admin', name: '测试管理员', portal: 'admin', superAdmin: true, permissions: permissionKeys, navigation: [], menus: [{ id: 'org', parentId: null, name: '管理功能', path: null, icon: 'Setting', type: 'DIRECTORY', openMode: 'ROUTE', children: menus }], features: { departmentEnabled: true, postEnabled: true, extendedProfileEnabled: true }, ...preferences }
  const profile = { id: '101', username: 'e2e.admin', name: '测试管理员', email: 'admin@example.test', mobile: '', gender: 'UNSPECIFIED', avatarFileId: null }
  const base = { id: '9007199254740993123', parentId: '0', name: '测试记录', code: 'TEST', sort: 0, enabled: true, builtin: false }
  const role = { ...base, name: '运维角色', dataScope: 'SELF', departmentIds: [] }
  const member = { ...base, username: 'test.member', name: '测试成员', email: 'member@example.test', mobile: '', roleIds: [role.id], postIds: [], departmentId: null, gender: 'UNSPECIFIED', locale: 'zh-CN', timeZone: 'Asia/Shanghai' }
  const permission = { ...base, permissionKey: 'iam:custom:view', type: 'MENU', path: '/custom', icon: 'Grid', openMode: 'ROUTE', nameKey: '', hidden: false, superAdminDefault: true, translations: {} }
  const dictionaryTypes = [{ ...base, id: 'type1', typeKey: 'custom_status', name: '自定义状态', tree: false, translations: {} }]
  const dictionaryItems = [{ ...base, id: 'item1', typeId: 'type1', value: 'active', name: '正常', translations: {} }]
  const parameterGroups = [{ ...base, id: 'group1', groupKey: 'ui', name: '界面设置', translations: {} }]
  const parameters = [{ ...base, id: 'param1', paramKey: 'system.name', groupKey: 'ui', name: '系统名称', valueType: 'STRING', value: 'SMA', publicReadable: true, secret: false, translations: {} }]
  const asset = { id: 'file1', name: 'sample.png', contentType: 'image/png', kind: 'IMAGE', sizeBytes: '68', checksum: 'test-checksum', ownerId: '101', uploader: '测试管理员', createdAt: stamp, previewable: true, contentPath: '/file/files/file1/content' }
  const assets = [asset]
  const notice = { id: 'notice1', title: '系统维护通知', content: '<p>请在维护前保存工作。</p>', type: '1', priority: 1, audienceType: 'ALL', audienceIds: [], fileIds: [], recipientCount: '2', readCount: '0', createdBy: '101', createdAt: stamp, updatedAt: stamp, readAt: null as string | null }
  const task = { id: 'task1', userId: '101', userName: '测试管理员', title: '核对待办事项', content: '核对信息后提交', url: '/iam/users', type: 4, priority: 1, state: 1, dueAt: '2030-11-10T08:00:00Z', completedAt: null, cancelledAt: null, readAt: null as string | null, source: { system: 'Example', messageId: null, requestId: null, url: null, senderKey: null, receiverKey: null }, createdAt: stamp }
  const privateMessage = { id: 'private1', senderId: '102', recipientId: '101', senderName: '测试成员', recipientName: '测试管理员', content: '这是一条测试私信', createdAt: stamp, readAt: null as string | null }
  const app = { id: 'app1', name: '测试应用', type: 1, description: '用于接口测试', appId: 'app_test_9007199254740993123', enabled: false, signatureRequired: true, targetType: null, targetId: null, targetName: null, createdAt: stamp }
  let locked = false
  const sockets: { send: (data: string) => void }[] = []
  await page.routeWebSocket('**/api/message/ws?*', socket => { sockets.push(socket) })
  await page.route('**/actuator/health', route => route.fulfill({ json: { status: 'UP' } }))
  await page.route('http://127.0.0.1:5174/api/**', async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname.slice(4), method = req.method()
    let body: Record<string, unknown> = {}; try { body = req.postDataJSON() || {} } catch { /* Multipart requests are handled by upload endpoints. */ }
    const ok = (data: unknown) => route.fulfill({ json: { code: 0, error: null, message: 'ok', data } })
    const paged = (items: unknown[]) => ok({ items, page: url.searchParams.get('page') || '1', size: url.searchParams.get('size') || '20', total: String(items.length) })
    if (method !== 'GET') writes.push({ method, path, body })
    if (path === '/config/parameters/public') return ok({ 'login.captchaEnabled': 'false', 'system.name': 'SMA 测试' })
    if (path === '/auth/login') return ok({ token: 'isolated-full-e2e-token' })
    if (path === '/auth/me') return ok({ ...me, ...preferences, locked })
    if (path === '/auth/logout') return ok(null)
    if (path === '/auth/lock') { locked = true; return ok(null) }
    if (path === '/auth/unlock') { locked = false; return ok(null) }
    if (path === '/auth/sessions') return paged([{ id: 'current', username: 'e2e.admin', name: '测试管理员', ip: '127.0.0.1', userAgent: 'Chrome testing', lastSeenAt: stamp, expiresAt: stamp, locked: false, current: true }, { id: 'other', username: 'test.member', name: '测试成员', ip: '127.0.0.2', userAgent: 'Browser testing', lastSeenAt: stamp, expiresAt: stamp, locked: false, current: false }])
    if (/^\/auth\/sessions\/[^/]+\/revoke$/.test(path)) return ok(null)
    if (path === '/config/preferences') { if (method === 'PUT') Object.assign(preferences, body); if (method === 'DELETE') Object.assign(preferences, { locale: 'zh-CN', theme: 'light', timeZone: 'Asia/Shanghai', tabsEnabled: true, keepTabsAfterReload: false }); return ok(preferences) }
    if (path === '/iam/profile') { if (method === 'PUT') Object.assign(profile, body); return ok(profile) }
    if (path.endsWith('/password')) return ok(null)
    if (path === '/iam/users') return method === 'GET' ? paged([member]) : ok({ ...member, ...body, id: 'new-user' })
    if (path === '/iam/users/' + member.id) { if (method === 'PUT') Object.assign(member, body); return ok(member) }
    if (path === '/iam/roles') return ok([role])
    if (path === '/iam/roles/options') return ok([{ value: role.id, label: role.name }])
    if (path === '/iam/posts/options') return ok([{ value: 'post1', label: '开发岗位' }])
    if (path === '/iam/roles/tree') return ok([{ id: role.id, name: role.name, children: [] }])
    if (path === '/iam/departments/tree') return ok([{ id: 'dept1', name: '研发部', children: [] }])
    if (path === '/iam/permissions/tree') return ok([{ id: 'menu1', name: '组织权限', children: [{ id: 'read', name: '查看用户' }, { id: 'write', name: '编辑用户' }] }])
    if (/^\/iam\/roles\/[^/]+\/permissions$/.test(path)) return ok(method === 'GET' ? ['menu1', 'read'] : null)
    if (path === '/iam/departments') return ok([{ ...base, id: 'dept1', name: '研发部' }])
    if (path === '/iam/posts') return paged([{ ...base, id: 'post1', name: '开发岗位' }])
    if (path === '/iam/permissions') return ok([permission])
    if (path === '/iam/navigation') return ok([{ ...base, id: 'nav1', name: '业务导航', menuIds: [] }])
    if (/^\/iam\/(departments|posts|roles|permissions|navigation)\/[^/]+$/.test(path) && method !== 'GET') return ok(body)
    if (path === '/config/dictionaries/types') return ok(dictionaryTypes)
    if (path === '/config/dictionaries/items') return ok(dictionaryItems)
    if (path === '/config/dictionaries/initialize' || path === '/config/parameters/refresh' || path === '/config/parameters/reset-builtins') return ok(null)
    if (/^\/config\/dictionaries\/[^/]+\/options$/.test(path)) {
      const key = path.split('/')[3], values = key === 'todo_state' ? ['待处理', '进行中', '已完成', '已取消', '已逾期'] : key === 'todo_type' ? ['普通', '内容', '链接', '内容与链接'] : ['普通', '重要', '紧急']
      return ok(values.map((label, index) => ({ value: String(index + 1), label })))
    }
    if (path === '/config/parameters/groups') return ok(parameterGroups)
    if (path === '/config/parameters') return ok(parameters)
    if (/^\/config\/(dictionaries\/(types|items)|parameters(\/groups)?)\/[^/]+$/.test(path)) return ok(body)
    if (path.startsWith('/audit/')) return paged([{ id: 'log1', username: 'e2e.admin', status: 'SUCCESS', message: '查看应用', action: 'view', clientPath: '/openapi/apps?focus=app1', resultCode: 0, ip: '127.0.0.1', country: '', province: '', city: '', browser: 'Chrome', operatingSystem: 'Windows', localAddress: true, unusualLocation: false, durationMs: '12', occurredAt: stamp }])
    if (path === '/message/summary') return ok({ unreadNotices: notice.readAt ? '0' : '1', pendingTodos: '1', unreadMessages: privateMessage.readAt ? '0' : '1' })
    if (path === '/message/socket-ticket') return ok({ ticket: 'isolated-test-ticket' })
    if (path === '/workspace/dashboard') return ok({ metrics: [{ id: 'users', name: '用户数量', value: '12', path: '/iam/users' }, { id: 'tasks', name: '待处理事项', value: '1', path: '/message/inbox?tab=tasks' }], entries: [{ kind: '待办', title: task.title, path: '/message/inbox?tab=tasks&focus=task1', time: stamp }] })
    if (path === '/message/notices/audience' || path === '/message/todos/recipients' || path === '/message/private/recipients') return ok([{ value: '102', label: '测试成员' }, { value: '101', label: '测试管理员' }])
    if (/\/readers$/.test(path)) return paged([{ userId: '101', name: '测试管理员', readAt: stamp }])
    if (path === '/message/notices' || path === '/message/notices/inbox') return method === 'GET' ? paged([notice]) : ok({ ...notice, ...body })
    if (path.startsWith('/message/notices/inbox/') && method === 'PUT') { notice.readAt = stamp; return ok(path.endsWith('/read') ? notice : null) }
    if (/^\/message\/notices\/[^/]+$/.test(path)) { if (method === 'PUT') Object.assign(notice, body); return ok(notice) }
    if (/^\/message\/notices\/.*(restore|delete)/.test(path)) return ok(null)
    if (path === '/message/todos' || path === '/message/todos/managed') return method === 'GET' ? paged([task]) : ok({ ...task, ...body })
    if (path === '/message/todos/task1' || path === '/message/todos/managed/task1') return ok(task)
    if (path.startsWith('/message/todos/')) { if (path.endsWith('/state')) task.state = Number(body.state); return ok(path.endsWith('/read') ? task : null) }
    if (path === '/message/private') return method === 'GET' ? paged([privateMessage]) : ok(privateMessage)
    if (path.startsWith('/message/private/')) { privateMessage.readAt = stamp; return ok(privateMessage) }
    if (path === '/file/files' || path === '/file/files/images') {
      if (method === 'POST') { const next = { ...asset, id: 'upload' + assets.length, name: 'uploaded.png' }; assets.push(next); return ok(next) }
      return paged(assets)
    }
    if (/^\/file\/files\/[^/]+\/content$/.test(path)) return route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF1EAAAAASUVORK5CYII=', 'base64') })
    if (/^\/file\/files\/[^/]+$/.test(path)) return ok(assets.find(item => path.endsWith('/' + item.id)) || asset)
    if (path === '/monitor/server') return ok({ sampledAt: stamp, os: 'Windows', arch: 'amd64', processors: 8, cpuLoad: .2, processCpuLoad: .1, physicalTotalBytes: '34359738368', physicalFreeBytes: '17179869184', javaVersion: '21', vm: 'OpenJDK', heapUsedBytes: '268435456', heapCommittedBytes: '536870912', heapMaxBytes: '1073741824', nonHeapUsedBytes: '134217728', threads: 32, uptimeMillis: '86400000', disks: [{ name: 'E:', type: 'NTFS', totalBytes: '1000000000', usableBytes: '500000000' }], pool: { active: 1, idle: 4, total: 5, waiting: 0 } })
    if (path === '/monitor/caches') return ok([{ id: 'parameters', name: '参数缓存', size: '20', hits: '80', misses: '2' }])
    if (path.startsWith('/monitor/caches/')) return ok(null)
    if (path === '/monitor/sql') return method === 'DELETE' ? ok(null) : paged([{ id: 'mapper.selectUsers', command: 'SELECT', calls: '12', errors: '0', rows: '12', slowCalls: '0', averageMillis: 1.2, maxMillis: 3.4, lastAt: stamp }])
    if (path === '/monitor/publications') return paged([{ id: 'event1', eventType: 'RoleChangedEvent', listener: 'AuditListener', status: 'FAILED', attempts: 1, publishedAt: stamp, lastResubmissionAt: null, completedAt: null }])
    if (path.endsWith('/retry')) return ok(null)
    if (path === '/monitor/database') return ok({ database: 'test_database', product: 'MySQL', version: '8.4', tables: [{ name: 'iam_user', remarks: '用户表', columns: [{ name: 'id', type: 'BIGINT', nullable: false, defaultValue: null, remarks: '主键', primaryKey: true }], indexes: [{ name: 'PRIMARY', unique: true, columns: ['id'] }] }] })
    if (path === '/monitor/database/markdown') return route.fulfill({ contentType: 'text/markdown', headers: { 'content-disposition': 'attachment; filename=database-schema.md' }, body: '# Test database\n\n## iam_user\n' })
    if (path === '/openapi/apps/types') return ok([{ value: '1', label: '通用应用' }])
    if (path === '/openapi/apps') return method === 'GET' ? paged([app]) : ok({ id: 'app2', appId: 'test_created_app', appSecret: 'fake-test-secret-only' })
    if (path === '/openapi/apps/app1') { if (method === 'PUT') Object.assign(app, body); return ok(app) }
    if (path === '/openapi/apps/app1/rotate') return ok({ id: 'app1', appId: app.appId, appSecret: 'fake-rotated-secret-only' })
    if (path === '/openapi/apps/app1/target') return ok(null)
    unmatched.push(method + ' ' + path)
    return route.fulfill({ status: 501, json: { code: 501, message: 'Missing test fixture', data: null } })
  })
  return { writes, unmatched, me, preferences, notice, task, app, sockets }
}
