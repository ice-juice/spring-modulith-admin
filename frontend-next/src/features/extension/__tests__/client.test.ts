import { beforeEach, expect, it } from 'vitest'
import { AxiosError, AxiosHeaders, type AxiosRequestConfig } from 'axios'
import { clearReleaseSession, CommandNotDispatched, commandEvidence, executeCommand, inspectFiles, connectRelease, prepareCommand, releaseAccess, releaseHttp, releaseRequest, sha256, UnknownCommand } from '../client'
import { loadLocaleModule } from '@/locales'
const context = { action:'UPLOAD',environment:'production',groupId:'general',groupRevision:1,pluginId:'echo',expectedRevision:1,policyRevision:1,files:[] }
function respond(config: AxiosRequestConfig, data: unknown) { return {config:config as never,data:{code:0,error:null,message:'ok',data},headers:new AxiosHeaders(),status:200,statusText:'OK'} }
beforeEach(async () => { clearReleaseSession(); localStorage.clear(); localStorage.setItem('sma.access-token','host-fixture'); await loadLocaleModule('extension') })
it('uses only the host proxy without a second login and keeps host login on a release rejection',async()=>{
  localStorage.setItem('sma.access-token','host-secret');const headers:string[]=[]
  releaseHttp.defaults.adapter=async config=>{
    headers.push(String(config.headers.Authorization || ''))
    expect(config.url).toBe('/requests');expect(config.baseURL).toBe('/api/extension/consoles');if(config.headers['X-Release-Path']==='/console/identity')return respond(config,{enrolled:true})
    if(config.headers['X-Release-Path']==='/console/access')return respond(config,{userId:'operator',username:'operator',securityAdmin:true,scopes:[],environments:['production']})
    throw new AxiosError('denied','401',config,undefined,{...respond(config,null),status:401})
  }
  await connectRelease()
  await expect(releaseRequest('GET','/groups')).rejects.toThrow()
  expect(headers).toEqual(['Bearer host-secret','Bearer host-secret','Bearer host-secret']);expect(releaseAccess.value).toBeNull();expect(localStorage.getItem('sma.access-token')).toBe('host-secret')
})
it('binds exact UTF-8 bytes and reconciles a timed-out command without resubmitting it',async()=>{
  let writes=0, lookups=0
  releaseHttp.defaults.adapter=async config=>{
    if(config.headers['X-Release-Path']==='/challenges')return respond(config,{id:'challenge',expiresAt:'2030-01-01T00:00:00Z'})
    if(config.headers['X-Release-Path']==='/authorizations')return respond(config,{token:'one-use-grant'})
    if(config.headers['X-Release-Path']==='/commands'){writes++;throw new AxiosError('timeout','ECONNABORTED',config)}
    if(String(config.headers['X-Release-Path']).startsWith('/console/actions/')){lookups++;return respond(config,{id:'action',resourceId:'upload',state:'ACCEPTED'})}
    throw new Error('Unexpected request')
  }
  const body={name:'中文组'},prepared=await prepareCommand(context,body)
  expect(prepared.intent.parametersSha256).toBe(await sha256(new TextEncoder().encode(JSON.stringify(body)).buffer))
  expect(new TextDecoder().decode(Uint8Array.from(atob(prepared.parameters),c=>c.charCodeAt(0)))).toBe(JSON.stringify(body))
  expect((await executeCommand(prepared,'fixture-password','123456')).id).toBe('action');expect(writes).toBe(1);expect(lookups).toBe(1)
  releaseHttp.defaults.adapter=async config=>{if(config.headers['X-Release-Path']==='/authorizations')return respond(config,{token:'grant'});throw new AxiosError('timeout','ECONNABORTED',config)}
  await expect(executeCommand(prepared,'fixture-password','234567')).rejects.toBeInstanceOf(UnknownCommand)
})
it('checks upload quotas and duplicate filenames before any hashing or upload',async()=>{
  const policy={revision:1,jarBytes:100,sourceBytes:30,sourceBatchBytes:40,batchBytes:150,batchFiles:2}
  await expect(inspectFiles([new File(['a'],'bad.jar'),new File(['b'],'bad.jar')],policy)).rejects.toThrow()
  await expect(inspectFiles([new File(['a'.repeat(31)],'Source.java')],policy)).rejects.toThrow()
  await expect(inspectFiles([new File(['x'],'evil.exe')],policy)).rejects.toThrow()
})
it('discards an in-flight response after locking without clearing the host login', async () => {
  let finish: (() => void) | undefined
  releaseHttp.defaults.adapter = config => new Promise(resolve => { finish = () => resolve(respond(config, [])) })
  const pending = releaseRequest('GET', '/groups')
  window.dispatchEvent(new Event('sma:locked'))
  finish!()
  await expect(pending).rejects.toMatchObject({ key: 'stale-session' })
  expect(releaseAccess.value).toBeNull()
  expect(localStorage.getItem('sma.access-token')).toBe('host-fixture')
})
it('distinguishes a lost authorization response from an unknown dispatched business command', async () => {
  let commands = 0
  releaseHttp.defaults.adapter = async config => {
    if (config.headers['X-Release-Path'] === '/challenges') return respond(config, { id: 'challenge', expiresAt: '2030-01-01T00:00:00Z' })
    if (config.headers['X-Release-Path'] === '/commands') commands++
    throw new AxiosError('timeout', 'ECONNABORTED', config)
  }
  const prepared = await prepareCommand(context, { name: 'draft' })
  await expect(executeCommand(prepared, 'not-retained-password', '123456')).rejects.toBeInstanceOf(CommandNotDispatched)
  expect(commands).toBe(0)
})
it('accepts only recognized command receipt states and leaves unrecognized states uncertain', () => {
  const receipt = { id: 'action', resourceId: 'upload', state: 'ACCEPTED', createdAt: '2026-01-01T00:00:00Z' }
  expect(commandEvidence(receipt, 'original-command').state).toBe('SUCCEEDED')
  expect(commandEvidence({ ...receipt, state: 'UNRECOGNIZED' }, 'original-command').state).toBe('UNKNOWN')
  expect(commandEvidence({ ...receipt, state: 'FAILED' }, 'original-command').state).toBe('FAILED_FINAL')
})
