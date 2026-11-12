import { beforeEach, expect, it } from 'vitest'
import { AxiosError, AxiosHeaders, type AxiosRequestConfig } from 'axios'
import { clearReleaseSession, CommandNotDispatched, commandEvidence, commandStatusEvidence, lookupCommandStatus, resumeReleaseUpload, uploadStatusEvidence, executeCommand, inspectFiles, connectRelease, prepareCommand, releaseAccess, releaseHttp, releaseRequest, sha256, UnknownCommand } from '../client'
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

const uploadPolicy = { revision: 1, jarBytes: 100, sourceBytes: 30, sourceBatchBytes: 40, batchBytes: 150, batchFiles: 2 }
async function uploadFixture() {
  const file = new File(['original'], 'echo.jar')
  const files = await inspectFiles([file], uploadPolicy)
  const receipt = { id: 'receipt', resourceId: 'upload-1', state: 'ACCEPTED', createdAt: '2026-01-01T00:00:00Z' }
  const status = { id: 'upload-1', state: 'RECEIVING', expiresAt: '2030-01-01T00:00:00Z', resumable: true, files, artifactIds: [] as string[] }
  const calls: string[] = []
  releaseHttp.defaults.adapter = async config => {
    const path = String(config.headers['X-Release-Path']); calls.push(String(config.headers['X-Release-Method']) + ' ' + path)
    if (path === '/console/actions/cmd-original') return respond(config, receipt)
    if (path === '/uploads/upload-1') return respond(config, status)
    if (path === '/uploads/upload-1/files/0/chunks/0') return respond(config, {})
    if (path === '/uploads/upload-1/complete') { status.state = 'COMPLETE'; status.artifactIds = ['artifact-1']; return respond(config, []) }
    throw new Error('Unexpected request ' + path)
  }
  return { file, status, calls }
}
it('queries an accepted upload under the original command identity without sending any bytes', async () => {
  const { calls } = await uploadFixture()
  expect(commandStatusEvidence(await lookupCommandStatus('cmd-original', true), 'cmd-original', true)).toEqual({ id: 'cmd-original', state: 'PENDING' })
  expect(calls).toEqual(['GET /console/actions/cmd-original', 'GET /uploads/upload-1'])
})
it('completed uploads return their evidence without retransmitting or completing again', async () => {
  const { file, status, calls } = await uploadFixture(); status.state = 'COMPLETE'; status.artifactIds = ['artifact-1']
  expect((await resumeReleaseUpload('upload-1', [file], uploadPolicy, () => {})).state).toBe('COMPLETE')
  expect(calls).toEqual(['GET /uploads/upload-1'])
})
it('only resumes matching immutable files and tolerates JSON property order', async () => {
  const { file, status, calls } = await uploadFixture()
  const original = status.files[0]!; status.files = [{ sha256: original.sha256, bytes: original.bytes, name: original.name }]
  await expect(resumeReleaseUpload('upload-1', [new File(['modified'], 'echo.jar')], uploadPolicy, () => {})).rejects.toThrow()
  expect(calls).toEqual(['GET /uploads/upload-1'])
  expect((await resumeReleaseUpload('upload-1', [file], uploadPolicy, () => {})).state).toBe('COMPLETE')
  expect(calls.filter(call => !call.startsWith('GET'))).toEqual(['PUT /uploads/upload-1/files/0/chunks/0', 'POST /uploads/upload-1/complete'])
})
it('refuses expired, non-resumable, mismatched or malformed upload evidence without writes', async () => {
  const { file, status, calls } = await uploadFixture(); status.resumable = false
  await expect(resumeReleaseUpload('upload-1', [file], uploadPolicy, () => {})).rejects.toThrow()
  expect(calls).toEqual(['GET /uploads/upload-1'])
  expect(() => uploadStatusEvidence({ ...status, id: 'other' }, 'upload-1')).toThrow()
  expect(() => uploadStatusEvidence({ ...status, files: [{ ...status.files[0]!, sha256: 'invalid' }] }, 'upload-1')).toThrow()
  expect(() => uploadStatusEvidence({ ...status, state: 'COMPLETE' }, 'upload-1')).toThrow()
})
it('discards a late upload lookup after a release session change without any continuation', async () => {
  const { file, status } = await uploadFixture(); let finish!: () => void
  releaseHttp.defaults.adapter = config => new Promise(resolve => { finish = () => resolve(respond(config, status)) })
  const pending = resumeReleaseUpload('upload-1', [file], uploadPolicy, () => {})
  clearReleaseSession(); finish()
  await expect(pending).rejects.toMatchObject({ key: 'stale-session' })
})
it('accepts the server maximum of 100 files and rejects an unbounded or duplicate completion list', async () => {
  const { status } = await uploadFixture()
  status.files = Array.from({ length: 100 }, (_, index) => ({ name: `file-${index}.jar`, bytes: 1, sha256: 'a'.repeat(64) }))
  expect(uploadStatusEvidence(status, status.id).state).toBe('PENDING')
  expect(() => uploadStatusEvidence({ ...status, files: [...status.files, { ...status.files[0]!, name: 'overflow.jar' }] }, status.id)).toThrow()
  expect(() => uploadStatusEvidence({ ...status, state: 'COMPLETE', artifactIds: Array.from({ length: 100 }, () => 'same-artifact') }, status.id)).toThrow()
})
