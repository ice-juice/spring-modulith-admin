import { expect, test } from '@playwright/test'
import { fullApi } from './full-api'
import AxeBuilder from '@axe-core/playwright'

test('an unknown console command survives reload and queries its original key without reauthorizing', async ({ page }) => {
  const fixture = await fullApi(page)
  fixture.me.permissions.push('extension:console:view')
  let writes = 0, grants = 0, key = '', observed = false
  await page.route('**/api/extension/consoles/requests', route => {
    const path = route.request().headers()['x-release-path']!, ok = (data: unknown) => route.fulfill({ json: { code: 0, data } })
    if (path === '/console/access') return ok({ userId: 'operator', username: 'operator', securityAdmin: true, scopes: [], environments: ['production'] })
    if (path === '/console/identity') return ok({ enrolled: true })
    if (path === '/groups' || path.startsWith('/console/bindings?')) return ok([])
    if (path === '/policy') return ok({ revision: 1 })
    if (path === '/challenges') return ok({ id: 'binding-challenge', expiresAt: '2030-01-01T00:00:00Z' })
    if (path === '/authorizations') { grants++; return ok({ token: 'ephemeral-test-grant' }) }
    if (path === '/commands') { writes++; key = route.request().postDataJSON().idempotencyKey; return route.fulfill({ status: 503, json: { code: 503, message: 'Lost acknowledgement' } }) }
    if (path.startsWith('/console/actions/')) { expect(path.split('/').at(-1)).toBe(key); return observed ? ok({ id: 'binding-receipt', state: 'ACCEPTED', resourceId: 'binding', createdAt: '2026-11-11T00:00:00Z' }) : route.fulfill({ status: 404, json: { code: 404, error: 'action-not-found' } }) }
    throw new Error('Unexpected endpoint ' + path)
  })
  await page.addInitScript(() => localStorage.setItem('sma.access-token', 'host-fixture-token'))
  await page.goto('/extension/manage'); await page.getByRole('button', { name: '后台账号绑定', exact: true }).click()
  await page.getByRole('button', { name: '绑定后台账号', exact: true }).click()
  await page.getByLabel('后台用户 ID', { exact: true }).fill('9007199254740993')
  await page.getByLabel('用户编号', { exact: true }).fill('release-user')
  await page.getByRole('button', { name: '核对并验证', exact: true }).click()
  await page.getByLabel('操作密码', { exact: true }).fill('private-operation-secret')
  await page.getByLabel('二步验证码', { exact: true }).fill('123456')
  await page.getByRole('button', { name: '验证并执行', exact: true }).evaluate(button => { for (let i = 0; i < 20; i++) (button as HTMLButtonElement).click() })
  await expect.poll(() => writes).toBe(1)
  await expect(page.getByText(key, { exact: true })).toBeVisible()
  const saved = await page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('sma.operation.v1.')).map(key => localStorage.getItem(key)).join(''))
  expect(saved).toContain(key); expect(saved).not.toMatch(/private-operation-secret|123456|ephemeral-test-grant/)
  await page.reload(); await expect(page.getByText(key, { exact: true })).toBeVisible()
  observed = true; await page.getByRole('button', { name: '查询原动作', exact: true }).click()
  await expect(page.getByText('binding-receipt', { exact: true })).toBeVisible()
  expect(writes).toBe(1); expect(grants).toBe(1)
})

test('runtime reconciliation reserves before confirmation and an unknown response is resolved with GET only', async ({ page }) => {
  const fixture = await fullApi(page)
  fixture.me.permissions.push('extension:console:view', 'extension:work:read', 'extension:work:reconcile')
  let writes = 0, state = 'UNKNOWN'
  await page.route('**/api/extension/consoles/requests', route => {
    const path = route.request().headers()['x-release-path']!, ok = (data: unknown) => route.fulfill({ json: { code: 0, data } })
    if (path === '/console/access') return ok({ userId: 'operator', username: 'operator', securityAdmin: true, scopes: [], environments: ['production'] })
    if (path === '/console/identity') return ok({ enrolled: true })
    if (path === '/groups') return ok([])
    if (path === '/policy') return ok({ revision: 1 })
    throw new Error('Unexpected endpoint ' + path)
  })
  await page.route('**/api/extension/work/42', route => route.fulfill({ json: { code: 0, data: { id: '42', state, releaseRevision: '1', bundleId: 'original-bundle', result: null, failure: null } } }))
  await page.route('**/api/extension/work/42/reconcile', route => { writes++; state = 'SUCCEEDED'; return route.fulfill({ status: 503, json: { code: 503, message: 'Lost reconciliation response' } }) })
  await page.addInitScript(() => localStorage.setItem('sma.access-token', 'host-fixture-token'))
  await page.goto('/extension/manage'); await page.getByRole('button', { name: '运行与工作', exact: true }).click()
  await page.getByLabel('业务工作编号', { exact: true }).fill('42')
  await page.locator('form').getByRole('button', { name: '查询', exact: true }).click()
  await page.getByRole('button', { name: '核验业务结果', exact: true }).evaluate(button => { for (let i = 0; i < 20; i++) (button as HTMLButtonElement).click() })
  await expect(page.getByRole('alertdialog')).toHaveCount(1)
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(page.getByText('SUCCEEDED original-bundle', { exact: true })).toBeVisible()
  expect(writes).toBe(1)
})

test('binding identities preserves large user IDs and requires verification before writing', async ({ page }) => {
  await fullApi(page)
  await page.route('**/api/auth/me', route => route.fulfill({ json: { code: 0, data: { id: '101', username: 'fixture', name: 'Fixture', portal: 'admin', permissions: ['extension:console:view'], menus: [], navigation: [], locale: 'zh-CN', timeZone: 'Asia/Shanghai' } } }))
  let writes = 0
  await page.route('**/api/extension/consoles/requests', route => {
    const path = route.request().headers()['x-release-path']!, ok = (data: unknown) => route.fulfill({ json: { code: 0, data } })
    if (path === '/console/access') return ok({ userId: 'operator', username: 'operator', securityAdmin: true, scopes: [], environments: ['production'] })
    if (path === '/console/identity') return ok({ enrolled: true })
    if (path === '/groups' || path.startsWith('/console/bindings?')) return ok([])
    if (path === '/policy') return ok({ revision: 1 })
    if (path === '/challenges') { expect(route.request().postDataJSON().action).toBe('IDENTITY_BIND'); return ok({ id: 'binding-challenge', expiresAt: '2030-01-01T00:00:00Z' }) }
    if (path === '/authorizations') { expect(route.request().postDataJSON()).toMatchObject({ operationPassword: 'fixture-operation', otp: '123456' }); return ok({ token: 'binding-grant' }) }
    if (path === '/commands') {
      writes++; const parameters = JSON.parse(Buffer.from(route.request().postDataJSON().parameters, 'base64').toString('utf8'))
      expect(parameters).toEqual({ machineId: 'admin-console', subject: { tenantId: '1', portal: 'admin', hostUserId: '9007199254740993' }, userId: 'release-user', expectedRevision: 0, enabled: true })
      return ok({ id: 'binding-receipt', state: 'ACCEPTED', resourceId: 'binding', createdAt: '2026-11-11T00:00:00Z' })
    }
    throw new Error('Unexpected endpoint ' + path)
  })
  await page.addInitScript(() => localStorage.setItem('sma.access-token', 'host-fixture-token'))
  await page.goto('/extension/manage')
  await page.getByRole('button', { name: '后台账号绑定', exact: true }).click()
  await page.getByRole('button', { name: '绑定后台账号', exact: true }).click()
  await page.getByLabel('后台用户 ID', { exact: true }).fill('9007199254740993')
  await page.getByLabel('用户编号', { exact: true }).fill('release-user')
  await page.getByRole('button', { name: '核对并验证', exact: true }).click()
  expect(writes).toBe(0)
  await page.getByLabel('操作密码', { exact: true }).fill('fixture-operation')
  await page.getByLabel('二步验证码', { exact: true }).fill('123456')
  await page.getByRole('button', { name: '验证并执行', exact: true }).click()
  await expect(page.getByText('binding-receipt', { exact: true })).toBeVisible()
  expect(writes).toBe(1)
})

test('unconfigured and unbound consoles explain the state without another login', async ({ page }) => {
  await fullApi(page)
  await page.route('**/api/auth/me', route => route.fulfill({ json: { code: 0, data: { id: '101', username: 'fixture', name: 'Fixture', portal: 'admin', permissions: ['extension:console:view'], menus: [], navigation: [], locale: 'zh-CN', timeZone: 'Asia/Shanghai' } } }))
  let unbound = false
  await page.route('**/api/extension/consoles/requests', route => route.fulfill({ status: unbound ? 400 : 503, json: { code: unbound ? 400 : 503, error: unbound ? 'extension.console-identity-unbound' : 'extension.error.console-unconfigured', message: '', data: null } }))
  await page.addInitScript(() => localStorage.setItem('sma.access-token', 'host-fixture-token'))
  await page.goto('/extension/manage')
  await expect(page.getByText('发布管理服务尚未配置，请联系管理员完成服务连接与账号绑定。', { exact: true })).toBeVisible()
  await expect(page.getByLabel('登录密码', { exact: true })).toHaveCount(0)
  unbound = true; await page.getByRole('button', { name: '重新连接', exact: true }).click()
  await expect(page.getByText('当前后台账号尚未绑定发布权限，请联系发布管理员授权。', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '上传制品', exact: true })).toHaveCount(0)
})

test('first-time factors are enrolled for the current identity without a username or login password', async ({ page }) => {
  await fullApi(page)
  await page.route('**/api/auth/me', route => route.fulfill({ json: { code: 0, data: { id: '101', username: 'fixture', name: 'Fixture', portal: 'admin', permissions: ['extension:console:view'], menus: [], navigation: [], locale: 'zh-CN', timeZone: 'Asia/Shanghai' } } }))
  let enrolled = false
  await page.route('**/api/extension/consoles/requests', route => {
    const path = route.request().headers()['x-release-path'], ok = (data: unknown) => route.fulfill({ json: { code: 0, data } })
    if (path === '/console/access') return ok({ userId: 'operator', username: 'operator', securityAdmin: true, scopes: [], environments: ['production'] })
    if (path === '/console/identity') return ok({ enrolled })
    if (path === '/groups') return ok([])
    if (path === '/policy') return ok({ revision: 1 })
    if (path === '/console/enrollment/begin') {
      expect(route.request().postDataJSON()).toEqual({ operationPassword: 'fixture-operation', enrollmentToken: 'fixture-ticket', otp: '' })
      return ok({ secret: 'JBSWY3DPEHPK3PXP' })
    }
    if (path === '/console/enrollment/complete') { expect(route.request().postDataJSON().otp).toBe('123456'); enrolled = true; return ok(['fixture-recovery-code']) }
    throw new Error('Unexpected endpoint ' + path)
  })
  await page.addInitScript(() => localStorage.setItem('sma.access-token', 'host-fixture-token'))
  await page.goto('/extension/manage')
  await page.getByRole('button', { name: '绑定安全验证', exact: true }).click()
  await page.getByLabel('注册票据', { exact: true }).fill('fixture-ticket')
  await page.getByLabel('操作密码', { exact: true }).fill('fixture-operation')
  await page.locator('form').getByRole('button', { name: '绑定安全验证', exact: true }).click()
  await expect(page.getByText('JBSWY3DPEHPK3PXP', { exact: true })).toBeVisible()
  await page.getByLabel('二步验证码', { exact: true }).fill('123456')
  await page.getByRole('button', { name: '确认绑定', exact: true }).click()
  await page.getByRole('button', { name: '已安全保存，进入管理', exact: true }).click()
  await expect(page.getByText('JBSWY3DPEHPK3PXP', { exact: true })).toHaveCount(0)
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toContain('fixture-operation')
  await expect(page.getByLabel('登录密码', { exact: true })).toHaveCount(0)
})

test('release console enters with host login and verifies each sensitive action',async({page})=>{
  await fullApi(page)
  await page.route('**/api/auth/me',async route=>route.fulfill({json:{code:0,data:{id:'101',username:'fixture',name:'Fixture',portal:'admin',permissions:['extension:management','extension:console:view'],menus:[
    {id:'extension-root',parentId:'0',name:'业务扩展',type:'DIRECTORY',permission:'extension:management',path:null,icon:'Folder',openMode:'ROUTE',children:[
      {id:'extension-console',parentId:'extension-root',name:'扩展管理',type:'MENU',permission:'extension:console:view',path:'/extension/manage',openMode:'ROUTE',children:[]},
    ]},
  ],navigation:[],features:{},locale:'zh-CN',timeZone:'Asia/Shanghai'}}}))
  let commands=0, uploaded=0, challenge:Record<string,unknown>|undefined
  await page.route('**/release/v1/**',()=>{ throw new Error('Browser must not contact the release service directly') })
  await page.route('**/api/extension/consoles/requests',async route=>{
    const request=route.request(),path=request.headers()['x-release-path']!
    const ok=(data:unknown)=>route.fulfill({json:{code:0,error:null,message:'ok',data}})
    expect(request.headers().authorization).toBe('Bearer host-fixture-token')
    if(path==='/console/identity')return ok({enrolled:true})
    if(path==='/console/access')return ok({userId:'operator',username:'发布管理员',securityAdmin:true,scopes:[],environments:['production']})
    if(path==='/groups')return ok([{id:'general',name:'通用模块',revision:1,enabled:true}])
    if(path==='/policy')return ok({revision:1,jarBytes:104857600,batchBytes:314572800,batchFiles:20,sourceBytes:1048576,sourceBatchBytes:10485760})
    if(path==='/groups/general/plugins')return ok([{id:'echo',groupId:'general',name:'通用回显',revision:1}])
    if(path.startsWith('/console/artifacts?'))return ok({items:[{id:'artifact-one',name:'echo.jar',pluginId:'echo',environment:'production',sha256:'a'.repeat(64),bytes:2684,state:'VERIFIED',manifest:{version:'1.0.0'},createdAt:'2026-11-11T00:00:00Z'}],total:1})
    if(path==='/challenges'){challenge=request.postDataJSON();return ok({id:'challenge-one',expiresAt:'2030-01-01T00:00:00Z'})}
    if(path==='/authorizations'){expect(request.postDataJSON().operationPassword).toBe('fixture-operation');return ok({token:'grant-one'})}
    if(path==='/commands'){commands++;const body=request.postDataJSON();expect(body.intent).toEqual(challenge);if(body.intent.action==='GROUP_CREATE')expect(JSON.parse(Buffer.from(body.parameters,'base64').toString('utf8'))).toEqual({name:'新分组',enabled:true});else {expect(body.intent.files).toHaveLength(1);expect(body.intent.files[0].name).toBe('fixture.jar');expect(body.intent.files[0].sha256).toHaveLength(64)}return ok({id:'receipt-'+commands,state:'ACCEPTED',resourceId:body.intent.action==='UPLOAD'?'upload-one':'new-group',createdAt:'2026-11-11T00:00:00Z'})}
    if(path==='/uploads/upload-one/files/0/chunks/0'){uploaded++;expect(request.postDataBuffer()?.toString()).toBe('fixed test bytes');return ok({})}
    if(path==='/uploads/upload-one/complete')return ok([])
    throw new Error('Unhandled release endpoint '+path)
  })
  await page.addInitScript(()=>localStorage.setItem('sma.access-token','host-fixture-token'))
  await page.goto('/extension/manage')
  await expect(page.getByRole('navigation').getByRole('button',{name:'业务扩展',exact:true})).toHaveAttribute('aria-expanded','true')
  await expect(page.getByRole('link',{name:'扩展管理',exact:true})).toHaveAttribute('href','/extension/manage')
  await page.getByRole('navigation').getByRole('button',{name:'业务扩展',exact:true}).click()
  await expect(page.getByRole('link',{name:'扩展管理',exact:true})).toHaveCount(0)
  await page.getByRole('navigation').getByRole('button',{name:'业务扩展',exact:true}).click()
  await expect(page.getByRole('link',{name:'扩展管理',exact:true})).toBeVisible()
  await expect(page.getByLabel('登录密码',{exact:true})).toHaveCount(0)
  await expect(page.getByRole('textbox',{name:'账号',exact:true})).toHaveCount(0)
  await expect(page.getByRole('cell',{name:'echo.jar',exact:true})).toBeVisible()
  await page.reload();await expect(page.getByRole('cell',{name:'echo.jar',exact:true})).toBeVisible()
  expect(await page.evaluate(()=>document.documentElement.scrollHeight<=innerHeight+1)).toBe(true)
  await expect(page.locator('.data-table-pagination')).toBeInViewport()
  await page.screenshot({path:'../docs/extension/console-desktop.png',fullPage:true})
  await page.locator('summary').filter({hasText:'分组与权限'}).click();await page.getByRole('button',{name:'新建分组',exact:true}).click();await page.getByLabel('唯一标识（小写字母开头）').fill('new-group');await page.getByLabel('名称',{exact:true}).fill('新分组')
  await page.getByRole('button',{name:'核对并验证'}).click();expect(commands).toBe(0)
  await page.getByLabel('操作密码',{exact:true}).fill('fixture-operation');await page.getByLabel('二步验证码',{exact:true}).fill('234567');await page.getByRole('button',{name:'验证并执行'}).click()
  await expect(page.getByText('receipt-1',{exact:true})).toBeVisible();expect(commands).toBe(1);await expect(page.getByRole('dialog')).toHaveCount(0);await page.waitForFunction(()=>document.getAnimations().every(a=>a.playState!=='running'))
  await page.getByRole('button',{name:'上传制品',exact:true}).click();await page.locator('#release-files').setInputFiles({name:'fixture.jar',mimeType:'application/java-archive',buffer:Buffer.from('fixed test bytes')});await expect(page.getByRole('dialog').getByText(/fixture.jar/)).toBeVisible()
  await page.getByRole('button',{name:'核对并验证'}).click();expect(uploaded).toBe(0);await page.getByLabel('操作密码',{exact:true}).fill('fixture-operation');await page.getByLabel('二步验证码',{exact:true}).fill('345678');await page.getByRole('button',{name:'验证并执行'}).click()
  await expect(page.getByRole('dialog')).toHaveCount(0);expect(uploaded).toBe(1);expect(commands).toBe(2)
  expect(await page.evaluate(()=>JSON.stringify({...localStorage,...sessionStorage}))).not.toContain('fixture-operation')
  await page.locator('details[open] > summary').click();await page.locator('.extension-workspace > .table-workspace-tools').evaluate(el=>el.scrollTop=0);await page.setViewportSize({width:390,height:844});await page.evaluate(()=>document.documentElement.classList.add('dark'))
  await page.waitForFunction(()=>document.getAnimations().every(a=>a.playState!=='running'))
  await page.screenshot({path:'../docs/extension/console-mobile-dark.png',fullPage:true})
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1)).toBe(true)
  expect(await page.evaluate(()=>document.documentElement.scrollHeight<=innerHeight+1)).toBe(true)
  await expect(page.locator('.data-table-pagination')).toBeInViewport()
  expect(await page.locator('.data-table [data-slot=scroll-area-viewport]').evaluate(el=>el.clientHeight)).toBeGreaterThan(150)
  const accessibility=await new AxeBuilder({page}).include('main').withTags(['wcag2a','wcag2aa','wcag21aa']).analyze();expect(accessibility.violations.map(v=>({id:v.id,nodes:v.nodes.map(n=>n.target)}))).toEqual([])
  await page.getByRole('button',{name:'语言与时区',exact:true}).click();await page.getByRole('menuitem',{name:'English',exact:true}).click();await expect(page.getByRole('heading',{name:'Business extensions',exact:true})).toBeVisible()
  await page.screenshot({path:'../docs/extension/console-mobile-english.png',fullPage:true})
})
