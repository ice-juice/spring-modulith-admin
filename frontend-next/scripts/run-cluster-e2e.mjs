// P08 M1d: two real JVMs, isolated database and shared Redis namespace. No production services.
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import net from 'node:net'
import http from 'node:http'
import { fileURLToPath } from 'node:url'
import { spawn, spawnSync } from 'node:child_process'
import { chromium } from '@playwright/test'

const root = fileURLToPath(new URL('../../', import.meta.url))
const suffix = Date.now().toString(), schema = `sma_p08_cluster_${suffix}`, user = `sma_p08_${suffix}`
const work = path.join(root, 'frontend-next/.e2e-runtime', `cluster-${suffix}`)
const mysql = process.env.SMA_E2E_MYSQL || path.join(process.env.LOCALAPPDATA, 'Programs/mysql-8.4.10-winx64/bin/mysql.exe')
const mysqlDefaults = process.env.SMA_CLUSTER_MYSQL_DEFAULTS
const mysqlPort = Number(process.env.SMA_CLUSTER_MYSQL_PORT || 13306)
const redisPort = Number(process.env.SMA_CLUSTER_REDIS_PORT || 16379)
const redisTimeout = process.env.SMA_CLUSTER_REDIS_TIMEOUT || '200ms'
if (!/^(?:[1-9][0-9]{1,3}ms|[1-5]s)$/.test(redisTimeout)) throw new Error('Invalid isolated Redis timeout')
const ports = [Number(process.env.SMA_CLUSTER_PORT_A || 18101), Number(process.env.SMA_CLUSTER_PORT_B || 18102)]
const originPort = Number(process.env.SMA_CLUSTER_ORIGIN_PORT || 5197), origin = `http://127.0.0.1:${originPort}`
const javaHome = process.env.JAVA21_HOME || spawnSync('powershell', ['-NoProfile', '-Command', "[Environment]::GetEnvironmentVariable('JAVA21_HOME','User')"], { encoding: 'utf8', windowsHide: true }).stdout.trim()
const jar = process.env.SMA_E2E_BACKEND_JAR
if (!jar || !mysqlDefaults || !fs.existsSync(jar) || !fs.existsSync(mysqlDefaults) || !fs.existsSync(path.join(javaHome, 'bin/java.exe'))) throw new Error('Set current verified SMA_E2E_BACKEND_JAR and protected SMA_CLUSTER_MYSQL_DEFAULTS; Java 21 and MySQL client are required.')
if (new Set([...ports, originPort]).size !== 3) throw new Error('Distinct isolated ports required')
const password = crypto.randomBytes(24).toString('hex'), adminPassword = 'A7' + crypto.randomBytes(5).toString('hex')
const accountPassword = 'M7n4R8p2V6', namespace = `p08_cluster_${suffix}`
const includeM2 = process.env.SMA_CLUSTER_M2 === '1'
const includeRouting = process.env.SMA_CLUSTER_ROUTING === '1'
const includeSchemaMigration = process.env.SMA_CLUSTER_SCHEMA_MIGRATION === '1'
const includeEvidence = process.env.SMA_CLUSTER_EVIDENCE === '1'
const includeStorage = process.env.SMA_CLUSTER_STORAGE === '1'
const includeTraffic = process.env.SMA_CLUSTER_TRAFFIC === '1'
const trafficOnly = process.env.SMA_CLUSTER_TRAFFIC_ONLY === '1'
if (trafficOnly && !includeTraffic) throw new Error('Focused traffic verification requires the traffic gate')
if (includeTraffic && !includeStorage) throw new Error('Traffic qualification requires storage and evidence verification')
if (includeStorage && (!includeEvidence || !includeM2)) throw new Error('Storage qualification requires evidence and M2 verification')
if (includeEvidence && !includeSchemaMigration) throw new Error('Evidence verification requires controlled schema migration')
const fingerprintKey = crypto.randomBytes(32).toString('base64')
const previousFingerprintKey = crypto.randomBytes(32).toString('base64')
const wrongFingerprintKey = crypto.randomBytes(32).toString('base64')
const ownedKeyNames = ['auth.keys', 'openapi.key', 'tenant.keys', 'wrong-auth.keys', 'wrong-tenant.keys', 'wrong-openapi.key', 'absent-openapi.key']
const runnerSource = fs.readFileSync(fileURLToPath(import.meta.url))
const runnerSha256 = crypto.createHash('sha256').update(runnerSource).digest('hex')
const jarSha256 = crypto.createHash('sha256').update(fs.readFileSync(jar)).digest('hex')
let businessMatrixReference
if (trafficOnly) {
  const reference = process.env.SMA_CLUSTER_TRAFFIC_BASE_RESULT
  check(reference && fs.existsSync(reference) && fs.statSync(reference).size <= 1048576, 'Focused verification requires a bounded successful full result')
  const bytes = fs.readFileSync(reference), prior = JSON.parse(bytes.toString('utf8'))
  check(prior.passed === true && prior.cleanupComplete === true && prior.jarSha256 === jarSha256 && prior.trafficOnly !== true
    && prior.m2Included && prior.routingIncluded && prior.schemaMigrationIncluded && prior.evidenceIncluded && prior.storageIncluded && prior.trafficIncluded,
  'Focused verification requires a cleaned full business/traffic matrix for the identical JAR')
  businessMatrixReference = { path: path.relative(root, reference), sha256: crypto.createHash('sha256').update(bytes).digest('hex') }
}
const children = [], logs = [], evidence = [], processes = []
const failureObservations = []
const trafficChecks = []
let created = false, browser, server, passed = false
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
function check(condition, message) { if (!condition) throw new Error(message) }
function record(name, details = {}) { evidence.push({ name, ...details }); console.log('PASS ' + name) }
function sql(statement, rootAccount = false, readTimeout) {
  const args = rootAccount ? [`--defaults-extra-file=${mysqlDefaults}`] : ['--no-defaults']
  args.push('--host=127.0.0.1', `--port=${mysqlPort}`, `--user=${rootAccount ? 'root' : user}`, '--batch', '--skip-column-names')
  const result = spawnSync(mysql, args, { input: statement, encoding: 'utf8', windowsHide: true, ...(readTimeout ? { timeout: readTimeout } : {}),
    env: { ...process.env, MYSQL_PWD: rootAccount ? '' : password } })
  // Never print SQL, connection credentials, or fixture contents.
  if (result.status !== 0) throw new Error('Isolated MySQL command failed')
  return result.stdout.trim()
}
async function free(port) {
  await new Promise((resolve, reject) => { const probe = net.createServer(); probe.once('error', () => reject(new Error(`Port ${port} occupied; existing service preserved`))); probe.listen(port, '127.0.0.1', () => probe.close(resolve)) })
}
async function stop(child) {
  if (!child || child.exitCode !== null || child.signalCode !== null) return
  const ended = new Promise(resolve => child.once('exit', resolve)); child.kill()
  await Promise.race([ended, pause(10000)])
  check(child.exitCode !== null || child.signalCode !== null, 'Owned JVM did not stop; refusing destructive database cleanup')
}
async function ready(node) {
  const deadline = Date.now() + 900000
  while (Date.now() < deadline) {
    check(children[node].exitCode === null, `Node ${node} exited; inspect its protected local log`)
    try { const r = await fetch(`http://127.0.0.1:${ports[node]}/actuator/health/readiness`, { signal: AbortSignal.timeout(2000) }); if (r.ok && (await r.json()).status === 'UP') return } catch { /* Only readiness GET is retried. */ }
    await pause(500)
  }
  throw new Error(`Node ${node} readiness deadline exceeded`)
}
function launch(node, { signalsEnabled = true, migrationMode, evidenceFault = 'none', storageFault = 'none' } = {}) {
  check(['none', 'split', 'missing'].includes(storageFault), 'Unknown storage fixture')
  check(['none', 'fingerprint', 'retained-key', 'auth-key', 'tenant-key', 'tenant-missing', 'session-policy', 'openapi-key', 'openapi-missing'].includes(evidenceFault), 'Unknown evidence fixture')
  const logName = migrationMode ? `migration-${processes.length}-${migrationMode}.log` : `node-${node}.log`
  const log = fs.openSync(path.join(work, logName), 'a'); logs.push(log)
  const child = spawn(path.join(javaHome, 'bin/java.exe'), ['-Xmx512m', '-jar', path.join(work, 'backend.jar'),
    '--server.address=127.0.0.1', `--server.port=${ports[node]}`, '--management.endpoint.health.probes.enabled=true',
    '--sma.tenant.bootstrap-hosts=localhost,127.0.0.1,[::1]', `--sma.security.browser.allowed-origins=${origin}`,
    `--sma.message.socket.allowed-origins=${origin}`, `--sma.file.directory=${path.join(work, 'uploads')}`,
    `--sma.openapi.key-file=${path.join(work, evidenceFault === 'openapi-missing' ? 'absent-openapi.key' : evidenceFault === 'openapi-key' ? 'wrong-openapi.key' : 'openapi.key')}`, '--sma.cache.provider=redis', `--sma.cache.namespace=${namespace}`,
    '--sma.cache.deployment-mode=single', `--sma.cache.nodes.cluster=${namespace}`, `--sma.cache.nodes.id=node-${node === 0 ? 'a' : 'b'}`,
    '--sma.cache.nodes.expected=node-a,node-b', '--spring.data.redis.host=127.0.0.1', `--spring.data.redis.port=${redisPort}`, `--spring.data.redis.timeout=${redisTimeout}`,
    `--sma.message.signals.enabled=${signalsEnabled}`,
    ...(includeSchemaMigration ? ['--sma.deployment.schema-mode=validate'] : []),
    ...(includeEvidence ? ['--sma.deployment.evidence.enabled=true', `--management.endpoint.health.group.readiness.include=readinessState,deploymentEvidence${includeStorage ? ',sharedStorage' : ''}${includeTraffic ? ',deploymentTraffic' : ''}`,
      ...(evidenceFault === 'session-policy' ? ['--sma.auth.session-ttl=PT9H'] : [])] : []),
    ...(includeStorage ? ['--sma.deployment.storage.enabled=true'] : []),
    ...(includeTraffic ? ['--sma.deployment.traffic.enabled=true', '--management.endpoint.health.group.liveness.include=livenessState',
      '--logging.level.com.sma.admin.common.internal.deployment.DeploymentTrafficFilter=DEBUG'] : []),
    ...(migrationMode ? [`--sma-migration=${migrationMode}`] : []),
    ...(includeM2 ? ['--sma.file.cluster.shared-resources=file.legacy,file.local.default', `--sma.file.storage.local-roots.default=${path.join(work, storageFault === 'split' ? 'isolated-managed' : storageFault === 'missing' ? 'missing-managed' : 'managed')}`] : [])],
  { cwd: work, windowsHide: true, stdio: ['ignore', log, log], env: { ...process.env,
    DB_NAME: schema, DB_USERNAME: user, DB_PASSWORD: password, DB_HOST: '127.0.0.1', DB_PORT: String(mysqlPort),
    SPRING_DATASOURCE_URL: `jdbc:mysql://127.0.0.1:${mysqlPort}/${schema}?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true`,
    SPRING_DATASOURCE_USERNAME: user, SPRING_DATASOURCE_PASSWORD: password, SMA_APP_MASTER_KEY: '', SMA_ADMIN_PASSWORD: adminPassword,
    SMA_AUTH_SECURITY_KEY_FILE: path.join(work, evidenceFault === 'auth-key' ? 'wrong-auth.keys' : 'auth.keys'),
    SMA_TENANT_CREDENTIAL_KEY_FILE: includeEvidence && evidenceFault !== 'tenant-missing' ? path.join(work, evidenceFault === 'tenant-key' ? 'wrong-tenant.keys' : 'tenant.keys') : '',
    SMA_SECURITY_REQUESTS_FINGERPRINT_KEY: evidenceFault === 'fingerprint' ? wrongFingerprintKey : fingerprintKey,
    SMA_SECURITY_REQUESTS_PREVIOUS_FINGERPRINT_KEYS: includeEvidence && evidenceFault !== 'retained-key' ? `previous=${previousFingerprintKey}` : '' } })
  const processEvidence = { node, command: migrationMode || 'serve', evidenceFault, storageFault, log: logName, pid: child.pid, startedAt: new Date().toISOString() }
  processes.push(processEvidence)
  child.once('exit', (code, signal) => { processEvidence.exitCode = code; processEvidence.signal = signal })
  children[node] = child; return child
}
async function schemaCommand(mode, expectedCode) {
  const child = launch(0, { migrationMode: mode })
  let timer
  try {
    const exitCode = await Promise.race([
      new Promise((resolve, reject) => { child.once('error', reject); child.once('exit', resolve) }),
      new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('Controlled migration deadline exceeded')), 900000) }),
    ])
    check(exitCode === expectedCode, `Controlled ${mode} exited unexpectedly; inspect owned process log`)
  } finally { clearTimeout(timer); await stop(child) }
}
async function blockedTraffic(node, token, requiredState) {
  if (!includeTraffic) return
  check(['WAITING_CONFIGURATION', 'WAITING_STORAGE'].includes(requiredState), 'Unknown expected refusal state')
  const live = await fetch(`http://127.0.0.1:${ports[node]}/actuator/health/liveness`, { signal: AbortSignal.timeout(5000) })
  const liveBody = await live.json()
  check(live.status === 200 && liveBody.status === 'UP' && !liveBody.components && !liveBody.details, 'Dependency failure incorrectly broke or exposed liveness')
  const logPath = path.join(work, `node-${node}.log`), offset = fs.statSync(logPath).size, pid = children[node].pid
  const deadline = Date.now() + 60000
  let observed = false
  while (Date.now() < deadline) {
    check(children[node].pid === pid && children[node].exitCode === null, 'Faulted node exited before post-startup traffic verification')
    // Seeing a specific dependency refusal proves ApplicationAvailability has left NOT_READY.
    // Poll only the read endpoint; never repeat the synthetic POST below or any business write.
    const response = await fetch(`http://127.0.0.1:${ports[node]}/api/auth/me`, { headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(5000) })
    const body = await response.json()
    check(response.status === 503 && body.error === 'common.error.unavailable', 'Faulted node allowed access while finishing startup')
    const end = fs.statSync(logPath).size, start = Math.max(offset, end - 65536), bytes = Buffer.alloc(end - start)
    const fd = fs.openSync(logPath, 'r')
    try { fs.readSync(fd, bytes, 0, bytes.length, start) } finally { fs.closeSync(fd) }
    observed = bytes.toString('utf8').split('\n').some(line => line.includes(` ${pid} --- `) && line.includes(`Deployment traffic refused: ${requiredState}`))
    if (observed) break
    await pause(500)
  }
  check(observed, 'Dependency refusal was not proved after application startup')
  trafficChecks.push({ node, pid, state: requiredState, at: new Date().toISOString() })
  // Each request is issued once; never retry a business write or interpret rejection as a historic receipt.
  for (const [method, route] of [['GET', '/api/auth/me'], ['GET', '/api/auth/captcha'], ['POST', '/api/auth/login'], ['GET', '/api/message/ws?ticket=invalid-test-ticket'], ['GET', '/app/identity/sessions'], ['GET', '/open/login']]) {
    const response = await fetch(`http://127.0.0.1:${ports[node]}${route}`, {
      method, headers: { Authorization: `Bearer ${token}`, ...(method === 'POST' ? { 'Content-Type': 'application/json' } : {}) },
      ...(method === 'POST' ? { body: '{}' } : {}), signal: AbortSignal.timeout(5000),
    })
    const body = await response.json()
    check(response.status === 503 && response.headers.get('cache-control') === 'no-store' && body.error === 'common.error.unavailable', 'Unqualified node accepted or bypassed traffic gate')
  }
}
function deploymentRow(node, readTimeout) {
  const hash = crypto.createHash('sha256').update(namespace).digest('hex')
  const raw = sql(`SELECT process_id,epoch,COALESCE(evidence_state,'NONE'),IF(evidence_process_id=process_id AND evidence_epoch=epoch AND evidence_until>UTC_TIMESTAMP(3) AND lease_until>UTC_TIMESTAMP(3),1,0) FROM \`${schema}\`.common_cache_node_lease WHERE cluster_hash='${hash}' AND node_id='node-${node === 0 ? 'a' : 'b'}';`, false, readTimeout)
  if (!raw) return null
  const [processId, epoch, state, fresh] = raw.split('\t')
  check(/^[0-9a-f-]{36}$/.test(processId) && /^[1-9][0-9]*$/.test(epoch), 'Unexpected node evidence identity')
  return { processId, epoch, state, fresh: fresh === '1' }
}
function deploymentBaseline() {
  const hash = crypto.createHash('sha256').update(namespace).digest('hex')
  return sql(`SELECT evidence_nonce,evidence_digest FROM \`${schema}\`.common_cache_node_group WHERE cluster_hash='${hash}';`)
}
async function qualification(node, expected, previousProcess) {
  const deadline = Date.now() + 120000
  while (Date.now() < deadline) {
    check(children[node].exitCode === null, `Evidence node ${node} exited; inspect owned log`)
    let response, body
    try {
      response = await fetch(`http://127.0.0.1:${ports[node]}/actuator/health/readiness`, { signal: AbortSignal.timeout(2000) })
      body = await response.json()
    } catch { await pause(500); continue } // Read-only connection observation, never a mutation retry.
    if (expected !== 'MATCH') check(response.status !== 200 && body.status !== 'UP', 'Incorrect evidence node became ready')
    const row = deploymentRow(node)
    if (row && row.processId !== previousProcess && row.state === expected && row.fresh) {
      check(response.status === (expected === 'MATCH' ? 200 : 503) && body.status === (expected === 'MATCH' ? 'UP' : 'DOWN'), 'Readiness disagrees with current process evidence')
      return row
    }
    await pause(500)
  }
  throw new Error(`Node ${node} did not reach expected ${expected} qualification`)
}
function storageRow(node, readTimeout) {
  const hash = crypto.createHash('sha256').update(namespace).digest('hex')
  const raw = sql(`SELECT COALESCE(storage_report_json,'null') FROM \`${schema}\`.common_cache_node_lease WHERE cluster_hash='${hash}' AND node_id='node-${node === 0 ? 'a' : 'b'}';`, false, readTimeout)
  return raw ? JSON.parse(raw) : null
}
function storageAnchor() {
  const hash = crypto.createHash('sha256').update(namespace).digest('hex')
  return sql(`SELECT storage_anchor_json FROM \`${schema}\`.common_cache_node_group WHERE cluster_hash='${hash}';`)
}
async function storageQualification(node, expectedReady, previousProcess, requireReport = true) {
  const deadline = Date.now() + 120000
  while (Date.now() < deadline) {
    check(children[node].exitCode === null, 'Storage node exited; inspect owned log')
    let response, body
    try { response = await fetch(`http://127.0.0.1:${ports[node]}/actuator/health/readiness`, { signal: AbortSignal.timeout(2000) }); body = await response.json() }
    catch { await pause(500); continue }
    if (!expectedReady && previousProcess) check(response.status !== 200, 'Bad storage process became ready')
    const identity = deploymentRow(node), report = storageRow(node)
    const bound = report && identity && report.offer.processId === identity.processId && String(report.offer.epoch) === identity.epoch
    if (identity?.fresh && identity.state === 'MATCH' && identity.processId !== previousProcess && (!requireReport || bound)
        && response.status === (expectedReady ? 200 : 503) && body.status === (expectedReady ? 'UP' : 'DOWN')
        && (!expectedReady || (bound && report.certified && report.healthy))) return { identity, report: bound ? report : null }
    await pause(500)
  }
  throw new Error('Storage qualification observation deadline exceeded')
}
async function api(node, route, { method = 'GET', token, body, headers = {} } = {}) {
  const r = await fetch(`http://127.0.0.1:${ports[node]}${route}`, { method, signal: AbortSignal.timeout(30000),
    headers: { 'Content-Type': 'application/json', 'User-Agent': 'P08-isolated-cluster', 'Idempotency-Key': crypto.randomUUID(), ...headers, ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
  const value = await r.json()
  if (includeTraffic && r.status === 503) await observeTraffic(node, 'http-503')
  return { http: r.status, ...value }
}
async function observeTraffic(node, reason) {
  if (failureObservations.length >= 12) return
  const observation = { node, reason, at: new Date().toISOString() }
  try {
    const probes = await Promise.all(['readiness', 'liveness'].map(async name => {
      const response = await fetch(`http://127.0.0.1:${ports[node]}/actuator/health/${name}`, { signal: AbortSignal.timeout(1500) })
      const body = await response.json()
      return { name, http: response.status, status: ['UP', 'DOWN', 'OUT_OF_SERVICE', 'UNKNOWN'].includes(body.status) ? body.status : 'UNAVAILABLE' }
    }))
    observation.probes = probes
  } catch { observation.probesUnavailable = true }
  try {
    const row = deploymentRow(node, 2000), storage = storageRow(node, 2000)
    observation.configuration = row
    observation.storage = storage ? { generation: storage.generation, healthy: storage.healthy, certified: storage.certified, until: storage.until } : null
  } catch { observation.recordsUnavailable = true }
  failureObservations.push(observation)
  // Only fixed states and process/epoch identity; never request/token/body, paths, keys or digests.
  console.log('Traffic failure observation ' + JSON.stringify(observation))
}
function ok(result, label) { check(result.http === 200 && result.code === 0, `${label}: HTTP ${result.http}, code ${result.code}, error ${result.error || 'none'}`); return result.data }
const me = (node, token) => api(node, '/api/auth/me', { token })
const login = (node, username = 'admin', secret = adminPassword, extra = {}) => api(node, '/api/auth/login', { method: 'POST', body: { username, password: secret, portal: 'admin', remember: false, ...extra } })
const preference = { locale: 'en-US', timeZone: 'UTC', theme: 'dark', tabsEnabled: true, keepTabsAfterReload: false, skin: 'iris' }
async function setParameter(key, value, token) {
  check(['login.captchaEnabled', 'auth.singleTerminal'].includes(key) && ['true', 'false'].includes(value), 'Unexpected fixture parameter')
  const route = `/api/config/effective-parameters/${key}`
  const current = ok(await api(0, route, { token }), 'Read effective fixture parameter')
  const saved = ok(await api(0, `${route}/override`, { method: 'PUT', token, body: {
    expectedPlatformRevision: current.revision.platform, expectedTenantRevision: current.revision.tenant, value,
  } }), 'Override fixture parameter')
  check(saved.value === value && saved.overridden, 'Parameter override not applied')
  check(ok(await api(1, route, { token }), 'Read parameter on B').value === value, 'B missed parameter override')
}
async function captchaFixture() {
  const challenge = ok(await api(0, '/api/auth/captcha'), 'Issue captcha')
  check(/^[A-Za-z0-9_-]+$/.test(challenge.id), 'Unexpected challenge ID shape')
  const answerHash = crypto.createHash('sha256').update(`${challenge.id}:ABCD`).digest('hex')
  const changed = sql(`UPDATE \`${schema}\`.auth_captcha SET answer_hash='${answerHash}' WHERE tenant_id=1 AND portal='admin' AND challenge_id='${challenge.id}' AND deleted=0; SELECT ROW_COUNT();`)
  check(changed === '1', 'Captcha fixture must identify exactly one issued challenge')
  return { captchaId: challenge.id, captcha: 'ABCD' }
}
function totp(secret, step) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
  const bits = [...secret.replace(/=+$/, '')].map(c => alphabet.indexOf(c).toString(2).padStart(5, '0')).join('')
  const key = Buffer.from(bits.match(/.{8}/g).map(byte => parseInt(byte, 2)))
  const counter = Buffer.alloc(8); counter.writeBigUInt64BE(BigInt(step))
  const mac = crypto.createHmac('sha1', key).update(counter).digest(), offset = mac[mac.length - 1] & 15
  return ((mac.readUInt32BE(offset) & 0x7fffffff) % 1000000).toString().padStart(6, '0')
}
async function codeAfter(secret, used = -1) {
  while (Math.floor(Date.now() / 30000) <= used || Date.now() % 30000 > 22000) await pause(200)
  const step = Math.floor(Date.now() / 30000); return { code: totp(secret, step), step }
}
try {
  for (const port of [...ports, originPort]) await free(port)
  // Match the repository's real-browser runner and fail before database work if unavailable.
  browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL || (fs.existsSync('C:/Program Files/Google/Chrome/Application/chrome.exe') ? 'chrome' : undefined) })
  fs.mkdirSync(work, { recursive: true }); fs.copyFileSync(jar, path.join(work, 'backend.jar'))
  fs.writeFileSync(path.join(work, 'runner-source.mjs'), runnerSource)
  if (includeM2) { fs.mkdirSync(path.join(work, 'uploads')); fs.mkdirSync(path.join(work, 'managed')) }
  if (includeStorage) fs.mkdirSync(path.join(work, 'isolated-managed'))
  fs.writeFileSync(path.join(work, 'auth.keys'), `encryption.active=e2e\nencryption.e2e=${crypto.randomBytes(32).toString('base64')}\npepper.active=e2e\npepper.e2e=${crypto.randomBytes(32).toString('base64')}\n`, { mode: 0o600 })
  fs.writeFileSync(path.join(work, 'openapi.key'), crypto.randomBytes(32), { mode: 0o600 })
  if (includeEvidence) {
    fs.writeFileSync(path.join(work, 'tenant.keys'), `active=e2e\nkey.e2e=${crypto.randomBytes(32).toString('base64')}\n`, { mode: 0o600 })
    fs.writeFileSync(path.join(work, 'wrong-tenant.keys'), `active=e2e\nkey.e2e=${crypto.randomBytes(32).toString('base64')}\n`, { mode: 0o600 })
    fs.writeFileSync(path.join(work, 'wrong-auth.keys'), `encryption.active=e2e\nencryption.e2e=${crypto.randomBytes(32).toString('base64')}\npepper.active=e2e\npepper.e2e=${crypto.randomBytes(32).toString('base64')}\n`, { mode: 0o600 })
    fs.writeFileSync(path.join(work, 'wrong-openapi.key'), crypto.randomBytes(32), { mode: 0o600 })
  }
  sql(`CREATE DATABASE \`${schema}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`, true); created = true
  sql(`CREATE USER '${user}'@'127.0.0.1' IDENTIFIED BY '${password}'; GRANT ALL PRIVILEGES ON \`${schema}\`.* TO '${user}'@'127.0.0.1';`, true)
  if (includeSchemaMigration) {
    await schemaCommand('validate', 1)
    check(sql(`SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${schema}';`) === '0', 'Empty validation wrote a schema object')
    record('Packaged validate rejects empty schema without creating tables')
    await schemaCommand('migrate', 0)
    check(sql(`SELECT COUNT(*) FROM \`${schema}\`.iam_user;`) === '0', 'Migration started business account bootstrap')
    const histories = () => sql(`SELECT table_name FROM information_schema.tables WHERE table_schema='${schema}' AND table_name LIKE 'flyway_schema_history%' ORDER BY table_name;`)
      .split(/\s+/).map(table => {
        check(/^flyway_schema_history(?:_[a-z]+)?$/.test(table), 'Unexpected module history name')
        return table + '\n' + sql(`SELECT installed_rank,version,type,script,checksum,success FROM \`${schema}\`.\`${table}\` ORDER BY installed_rank;`)
      }).join('\n')
    const before = histories()
    check(before.includes('flyway_schema_history_auth') && before.includes('flyway_schema_history_common'), 'Module migrations missing')
    record('Packaged migrate applies module histories and exits without account bootstrap')
    await schemaCommand('validate', 0)
    await schemaCommand('migrate', 0)
    check(histories() === before, 'Validation/repeated migration changed schema histories')
    check(sql(`SELECT COUNT(*) FROM \`${schema}\`.iam_user;`) === '0', 'Repeated migration started business bootstrap')
    record('Packaged validate and repeated migrate preserve applied module histories')
    sql(`REVOKE CREATE,ALTER,DROP,INDEX ON \`${schema}\`.* FROM '${user}'@'127.0.0.1';`, true)
  }
  launch(0); console.log(`Starting isolated node A; evidence directory ${path.relative(root, work)}`)
  if (!includeStorage) await ready(0)
  launch(1); console.log('Starting isolated node B against migrated schema'); await Promise.all([ready(0), ready(1)])
  if (includeSchemaMigration) {
    for (const node of [0, 1]) check(!fs.readFileSync(path.join(work, `node-${node}.log`), 'utf8').includes('DbMigrate'), 'Serving node invoked migration instead of validation')
    record('Two serving JVMs start in validate mode without CREATE/ALTER/DROP/INDEX privileges')
  }
  const admin = ok(await login(0, 'admin', adminPassword, await captchaFixture()), 'A login').token
  await setParameter('login.captchaEnabled', 'false', admin)
  check(ok(await me(1, admin), 'B me').username === 'admin', 'Cross-node identity differs')
  record('A login, B authenticated access', { pids: children.map(c => c.pid), ports })
  if (includeEvidence) {
    await qualification(0, 'MATCH'); await qualification(1, 'MATCH')
    record('Both independent JVMs prove matching configuration and keys with readiness explicitly gated')
  }
  if (includeStorage) {
    await storageQualification(0, true); await storageQualification(1, true)
    record('Both JVMs prove mutual storage with sharedStorage included in readiness')
  }
  if (!trafficOnly) {
  const routing = []
  if (includeRouting) {
    for (const n of [0, 1]) {
      const response = await fetch(`http://127.0.0.1:${ports[n]}/api/monitor/caches/directory`, { headers: { Authorization: `Bearer ${admin}` }, signal: AbortSignal.timeout(30000) })
      ok({ http: response.status, ...await response.json() }, 'Read actual cache process')
      const nodeId = response.headers.get('X-SMA-Cache-Node'), processId = response.headers.get('X-SMA-Cache-Process')
      check(nodeId === `node-${n === 0 ? 'a' : 'b'}` && /^[0-9a-f-]{36}$/.test(processId || ''), 'Missing actual node/process headers')
      routing.push({ 'X-SMA-Cache-Node': nodeId, 'X-SMA-Cache-Process': processId })
      const selected = ok(await api(n, '/api/monitor/caches/directory', { token: admin, headers: routing[n] }), 'Selected directory')
      check(selected.filter(row => row.observation).every(row => row.observation.processId === processId), 'Selected statistics belong to another process')
    }
    const before = ok(await api(0, '/api/monitor/caches/tasks', { token: admin }), 'Tasks before wrong route')
    const wrong = await api(0, '/api/monitor/caches/tasks/preview', { method: 'POST', token: admin, headers: routing[1], body: { action: 'CLEAR', regions: ['config.preferences'], reason: 'isolated wrong-route assertion' } })
    check(wrong.http === 503 && wrong.error === 'common.cache.selection-wrong-node', 'Wrong node accepted preview')
    const after = ok(await api(1, '/api/monitor/caches/tasks', { token: admin }), 'Tasks after wrong route')
    check(JSON.stringify(before.map(task => task.id).sort()) === JSON.stringify(after.map(task => task.id).sort()), 'Wrong route created a task')
    record('Explicit A/B process selection returns matching statistics and wrong-route preview has no task effect')
  }

  ok(await api(0, '/api/auth/lock', { method: 'POST', token: admin }), 'A lock')
  check(ok(await me(1, admin), 'B locked me').locked, 'B missed lock')
  check((await api(1, '/api/config/preferences', { token: admin })).code !== 0, 'Locked session reached business route')
  ok(await api(1, '/api/auth/unlock', { method: 'POST', token: admin, body: { password: adminPassword } }), 'B unlock')
  check(!ok(await me(0, admin), 'A unlocked me').locked, 'A missed unlock')
  record('Shared lock and unlock')

  const role = ok(await api(0, '/api/iam/roles', { method: 'POST', token: admin, body: { parentId: null, name: 'Cluster role', code: 'clusterRole', sort: 0, enabled: true, dataScope: 'SELF', departmentIds: [] } }), 'Create role')
  const users = []
  for (let i = 0; i < 8; i++) {
    const actor = ok(await api(i % 2, '/api/iam/users', { method: 'POST', token: admin, body: { user: { username: `cluster${i}`, name: `Cluster ${i}`, gender: 'UNSPECIFIED', enabled: true, roleIds: i === 0 ? [role.id] : [], postIds: [], timeZone: 'UTC' }, password: accountPassword } }), 'Create isolated user')
    users.push(actor); await pause(550)
  }
  // Four anonymous requests per node, within the ordinary production admission ceiling.
  const sessions = await Promise.all(users.map((u, i) => login(i % 2, u.username, accountPassword).then(r => ok(r, 'Concurrent login').token)))
  record('Eight concurrent logins split across two JVMs')
  // Exercise both native writes and LOCAL receipts, including the bounded shared capacity reservation.
  const ticketWrites = await Promise.all(sessions.map((token, i) => api(i % 2, '/api/message/socket-ticket', { method: 'POST', token })))
  ticketWrites.forEach(r => ok(r, 'Concurrent ticket creation'))
  const writes = await Promise.all(sessions.map((token, i) => api(i % 2, '/api/config/preferences', { method: 'PUT', token, body: preference })))
  writes.forEach(r => ok(r, 'Eight concurrent preference writes'))
  const ids = sql(`SELECT id FROM \`${schema}\`.iam_user WHERE username LIKE 'cluster%'; SELECT id FROM \`${schema}\`.config_user_preference; SELECT id FROM \`${schema}\`.auth_session; SELECT id FROM \`${schema}\`.message_socket_ticket;`).split(/\s+/)
  check(ids.length >= 33 && new Set(ids).size === ids.length, 'Generated IDs collide across processes/tables')
  record('Concurrent two-JVM writes and globally distinct IDs', { checkedIds: ids.length, actors: users.length })

  const permission = sql(`SELECT id FROM \`${schema}\`.iam_permission WHERE permission_key='monitor:cache:view' AND deleted=0;`)
  check(/^[0-9]+$/.test(permission), 'One permission fixture expected')
  check((await api(1, '/api/monitor/caches/directory', { token: sessions[0] })).code !== 0, 'Unprivileged cache access accepted')
  ok(await api(0, `/api/iam/roles/${role.id}/permissions`, { method: 'PUT', token: admin, body: { permissionIds: [permission] } }), 'Grant')
  ok(await api(1, '/api/monitor/caches/directory', { token: sessions[0] }), 'B sees grant')
  await pause(550)
  ok(await api(0, `/api/iam/roles/${role.id}/permissions`, { method: 'PUT', token: admin, body: { permissionIds: [] } }), 'Revoke permission')
  check((await api(1, '/api/monitor/caches/directory', { token: sessions[0] })).code !== 0, 'B retained revoked permission')
  record('Cross-node permission grant and immediate revocation')

  const cachePath = '/api/monitor/caches/directory/config.preferences'
  const observations = await Promise.all([0, 1].map(n => api(n, cachePath, { token: admin }).then(r => ok(r, 'Redis directory'))))
  check(observations.every(v => v.observation.provider === 'redis'), 'Actual provider is not Redis')
  ok(await api(0, '/api/config/preferences', { token: admin }), 'Prime A preference'); ok(await api(1, '/api/config/preferences', { token: admin }), 'Prime B preference')
  ok(await api(1, '/api/config/preferences', { token: admin }), 'Repeat B preference')
  const redisSample = ok(await api(1, cachePath, { token: admin }), 'Redis hit evidence').observation.snapshot
  check(BigInt(redisSample.hits) > BigInt(observations[1].observation.snapshot.hits), 'No actual Redis cache hit on B')
  ok(await api(0, '/api/config/preferences', { method: 'PUT', token: admin, body: preference }), 'A preference update')
  check(ok(await api(1, '/api/config/preferences', { token: admin }), 'B preference read').theme === 'dark', 'B served stale preference')
  ok(await api(1, '/api/monitor/caches/config.preferences', { method: 'DELETE', token: admin }), 'Clear cache')
  ok(await me(0, admin), 'Session after cache clear A'); ok(await me(1, admin), 'Session after cache clear B')
  record('Actual Redis invalidation and login survives cache clearing')

  await setParameter('login.captchaEnabled', 'true', admin)
  const challenge = await captchaFixture()
  const captchaRace = await Promise.all([0, 1].map(n => login(n, 'cluster1', accountPassword, challenge)))
  check(captchaRace.filter(r => r.code === 0 && r.data?.token).length === 1, 'Captcha was not consumed exactly once')
  await pause(550)
  await setParameter('login.captchaEnabled', 'false', admin); record('One captcha consumed once across A/B')

  server = http.createServer((_request, response) => { response.writeHead(200, { 'Content-Type': 'text/html' }); response.end('<!doctype html><title>P08 isolated socket test</title>') })
  await new Promise(resolve => server.listen(originPort, '127.0.0.1', resolve))
  const page = await browser.newPage(); await page.goto(origin)
  async function socketRace(ticket, nodes) {
    return page.evaluate(({ ticket, ports: targets }) => Promise.all(targets.map(port => new Promise(resolve => {
      const ws = new WebSocket(`ws://127.0.0.1:${port}/api/message/ws?ticket=${ticket}`)
      let settled = false
      const done = connected => { if (!settled) { settled = true; clearTimeout(timer); ws.close(); resolve(connected) } }
      const timer = setTimeout(() => done(false), 10000)
      ws.onmessage = event => { if (JSON.parse(event.data).type === 'connected') done(true) }
      ws.onerror = () => done(false); ws.onclose = () => done(false)
    }))), { ticket, ports: nodes.map(n => ports[n]) })
  }
  const ticket = ok(await api(0, '/api/message/socket-ticket', { method: 'POST', token: admin }), 'A ticket').ticket
  check((await socketRace(ticket, [1]))[0], 'A ticket did not connect B')
  check(!(await socketRace(ticket, [0]))[0], 'Consumed ticket was reused')
  await pause(550)
  const sharedTicket = ok(await api(0, '/api/message/socket-ticket', { method: 'POST', token: admin }), 'Race ticket').ticket
  check((await socketRace(sharedTicket, [0, 1])).filter(Boolean).length === 1, 'Ticket race did not produce one connection')
  record('Browser WebSocket A ticket / B handshake, replay rejection and A/B race')

  const heldTicket = ok(await api(0, '/api/message/socket-ticket', { method: 'POST', token: sessions[5] }), 'Revocation socket ticket').ticket
  check(await page.evaluate(({ ticket, port }) => new Promise(resolve => {
    const socket = new WebSocket(`ws://127.0.0.1:${port}/api/message/ws?ticket=${ticket}`)
    window.p08Socket = socket; window.p08SocketClosed = false
    const timer = setTimeout(() => { socket.close(); resolve(false) }, 10000)
    socket.onmessage = event => { if (JSON.parse(event.data).type === 'connected') { clearTimeout(timer); resolve(true) } }
    socket.onclose = () => { clearTimeout(timer); window.p08SocketClosed = true; resolve(false) }
    socket.onerror = () => { clearTimeout(timer); socket.close(); resolve(false) }
  }), { ticket: heldTicket, port: ports[1] }), 'Revocation socket not connected')
  const activeSession = ok(await api(0, '/api/auth/sessions?keyword=cluster5', { token: admin }), 'Find isolated session').items
  check(activeSession.length === 1, 'Expected exactly one isolated session to revoke')
  ok(await api(0, `/api/auth/sessions/${activeSession[0].id}/revoke`, { method: 'POST', token: admin }), 'Force logout on A')
  check((await me(1, sessions[5])).http === 401, 'B accepted forced-out session')
  await page.waitForFunction(() => window.p08SocketClosed === true, undefined, { timeout: 40000 })
  record('A forced logout denies B HTTP and closes B WebSocket within heartbeat bound')

  await setParameter('auth.singleTerminal', 'true', admin)
  const terminals = await Promise.all([0, 1].map(n => login(n, 'cluster2', accountPassword).then(r => ok(r, 'Concurrent terminal login').token)))
  const terminalChecks = await Promise.all(terminals.map(token => Promise.all([0, 1].map(n => me(n, token)))))
  check(terminalChecks.filter(rs => rs.every(r => r.code === 0)).length === 1 && terminalChecks.filter(rs => rs.every(r => r.http === 401)).length === 1, 'Single-terminal winner differs across nodes')
  await pause(550)
  await setParameter('auth.singleTerminal', 'false', admin); record('Concurrent single-terminal login has one shared winner')

  const enrollment = ok(await api(0, '/api/auth/totp-enrollments', { method: 'POST', token: sessions[3], body: { password: accountPassword, code: null } }), 'A TOTP enrollment')
  const initialCode = await codeAfter(enrollment.secret)
  ok(await api(1, '/api/auth/totp-enrollments/confirm', { method: 'POST', token: sessions[3], body: { challengeToken: enrollment.challengeToken, code: initialCode.code } }), 'B enrollment confirmation')
  check((await me(0, sessions[3])).http === 401, 'TOTP setup did not revoke old session on A')
  const pending = ok(await login(0, 'cluster3', accountPassword), 'MFA login')
  check(pending.status === 'MFA_REQUIRED' && !pending.token, 'MFA challenge bypassed')
  const fresh = await codeAfter(enrollment.secret, initialCode.step)
  const mfaRace = await Promise.all([0, 1].map(n => api(n, '/api/auth/login-challenges/verify', { method: 'POST', body: { challengeToken: pending.challengeToken, code: fresh.code } })))
  const successfulMfa = mfaRace.filter(r => r.code === 0 && r.data?.token)
  check(successfulMfa.length === 1, 'MFA challenge was not consumed once'); ok(await me(1, successfulMfa[0].data.token), 'Cross-node MFA session')
  record('Shared TOTP enrollment, revocation and one-time MFA challenge')

  const app = ok(await api(0, '/api/openapi/apps', { method: 'POST', token: admin, body: { name: 'Cluster budget', type: 1, enabled: true, signatureRequired: false } }), 'Create machine app')
  const machine = ok(await api(0, '/open/token', { method: 'POST', body: { appId: app.appId, appSecret: app.appSecret } }), 'Machine login').token
  for (let i = 0; i < 9; i++) {
    const wrong = await api(i % 2, '/open/token', { method: 'POST', body: { appId: app.appId, appSecret: 'x'.repeat(43) } })
    check(wrong.code !== 0 && wrong.code !== 429, 'Budget exhausted before ten attempts')
  }
  const exhausted = await api(1, '/open/token', { method: 'POST', body: { appId: app.appId, appSecret: app.appSecret } })
  check(exhausted.code === 429 || exhausted.http === 429, 'Shared machine budget did not reject attempt eleven')
  ok(await api(1, '/open/me', { token: machine }), 'Existing machine session after budget exhaustion')
  record('Shared OpenAPI login budget and cross-node machine session')

  if (includeM2) {
    const receivers = [await browser.newPage(), await browser.newPage()]
    for (const receiver of receivers) await receiver.goto(origin)
    async function connectReceiver(node, receiver) {
      const value = ok(await api(1 - node, '/api/message/socket-ticket', { method: 'POST', token: sessions[6] }), 'Cross-node message ticket').ticket
      check(await receiver.evaluate(({ ticket, port }) => new Promise(resolve => {
        window.p08InboxSocket?.close(); window.p08Inbox = []
        const socket = new WebSocket(`ws://127.0.0.1:${port}/api/message/ws?ticket=${ticket}`); window.p08InboxSocket = socket
        const timer = setTimeout(() => { socket.close(); resolve(false) }, 10000)
        socket.onmessage = event => {
          if (window.p08InboxSocket !== socket) return
          const payload = JSON.parse(event.data)
          if (payload.type === 'connected') { clearTimeout(timer); resolve(true) }
          else window.p08Inbox.push(payload)
        }
        socket.onclose = () => { clearTimeout(timer); resolve(false) }
        socket.onerror = () => { clearTimeout(timer); socket.close(); resolve(false) }
      }), { ticket: value, port: ports[node] }), 'Message receiver did not connect')
    }
    async function delivered(receiver, id) {
      await receiver.waitForFunction(expected => window.p08Inbox.some(item => item.type === 'private-message' && item.id === expected), id, { timeout: 15000 })
    }
    await connectReceiver(0, receivers[0]); await pause(550); await connectReceiver(1, receivers[1])
    const first = ok(await api(0, '/api/message/private', { method: 'POST', token: admin, body: { recipientId: users[6].id, content: 'P08 synthetic cross-node notification' } }), 'A private message')
    await Promise.all(receivers.map(receiver => delivered(receiver, first.id)))
    check(ok(await api(1, '/api/message/summary', { token: sessions[6] }), 'B authorized summary').unreadMessages === '1', 'Cross-node unread summary differs')
    record('A event reaches the same recipient on both independent WebSocket nodes')

    // Controlled delivery outage with the database and REST still available. This is not a network-partition claim.
    await stop(children[1]); await free(ports[1]); launch(1, { signalsEnabled: false }); await ready(1)
    await connectReceiver(1, receivers[1])
    const second = ok(await api(0, '/api/message/private', { method: 'POST', token: admin, body: { recipientId: users[6].id, content: 'P08 synthetic notification during delivery pause' } }), 'Message while B delivery paused')
    await delivered(receivers[0], second.id); await pause(2500)
    check(!await receivers[1].evaluate(id => window.p08Inbox.some(item => item.type === 'private-message' && item.id === id), second.id), 'B delivery pause fixture did not take effect')
    check(ok(await api(1, '/api/message/summary', { token: sessions[6] }), 'REST during delivery pause').unreadMessages === '2', 'REST did not recover missed notification')
    await stop(children[1]); await free(ports[1]); launch(1); await ready(1); await connectReceiver(1, receivers[1])
    await delivered(receivers[1], second.id)
    check(ok(await api(1, '/api/message/summary', { token: sessions[6] }), 'REST after delivery restore').unreadMessages === '2', 'Restored node lost durable unread state')
    record('Paused B delivery preserves REST state; restarted B replays the retained latest hint')
    for (const receiver of receivers) await receiver.close()

    const bytes = Buffer.from(`P08 shared file fixture ${suffix}\n`, 'utf8'), form = new FormData()
    form.append('file', new Blob([bytes], { type: 'text/plain' }), 'cluster-file.txt')
    const upload = await fetch(`http://127.0.0.1:${ports[0]}/api/file/files`, { method: 'POST', headers: { Authorization: `Bearer ${admin}`, 'Idempotency-Key': crypto.randomUUID() }, body: form, signal: AbortSignal.timeout(30000) })
    const file = ok({ http: upload.status, ...await upload.json() }, 'A native file upload')
    const download = await fetch(`http://127.0.0.1:${ports[1]}/api/file/files/${file.id}/content`, { headers: { Authorization: `Bearer ${admin}` }, signal: AbortSignal.timeout(30000) })
    check(download.ok && Buffer.from(await download.arrayBuffer()).equals(bytes), 'B did not read identical shared file bytes')
    ok(await me(1, sessions[7]), 'Unprivileged file reader is authenticated')
    const denied = await fetch(`http://127.0.0.1:${ports[1]}/api/file/files/${file.id}/content`, { headers: { Authorization: `Bearer ${sessions[7]}` }, signal: AbortSignal.timeout(30000) })
    check([403, 404].includes(denied.status), 'Shared storage bypassed file authorization'); await denied.arrayBuffer()
    record('A native upload / B identical download with unauthorized authenticated reader rejected', { bytes: bytes.length })
  }

  const logoutToken = ok(await login(0, 'cluster4', accountPassword), 'Logout fixture login').token
  ok(await api(0, '/api/auth/logout', { method: 'POST', token: logoutToken }), 'A logout')
  check((await me(1, logoutToken)).http === 401, 'B accepted logged-out token'); record('A logout immediately revokes B access')
  await stop(children[0]); ok(await me(1, admin), 'B survives A stop')
  await free(ports[0]); launch(0); await ready(0); ok(await me(0, admin), 'A restart retains session'); ok(await me(1, admin), 'B still serves session')
  record('A stop / B continuity / A restart retains same session')
  if (includeRouting) {
    const stale = await api(0, '/api/monitor/caches/directory', { token: admin, headers: routing[0] })
    check(stale.http === 409 && stale.error === 'common.cache.selection-replaced-process', 'Restart silently rebound old selected process')
    ok(await api(0, '/api/monitor/caches/tasks', { token: admin }), 'Shared tasks remain readable after selected process restart')
    record('Old selected process rejects after A restart while shared task reads remain available')
  }
  }
  if (includeEvidence) {
    const baseline = deploymentBaseline()
    check(/^[a-f0-9]{64}\t[a-f0-9]{64}$/.test(baseline), 'Missing immutable deployment baseline')
    let previous = await qualification(1, 'MATCH')
    for (const fault of ['fingerprint', 'retained-key', 'auth-key', 'tenant-key', 'tenant-missing', 'session-policy', 'openapi-key', 'openapi-missing']) {
      await stop(children[1]); await free(ports[1]); launch(1, { evidenceFault: fault })
      const expected = ['tenant-missing', 'openapi-missing'].includes(fault) ? 'UNAVAILABLE' : 'MISMATCH'
      const rejected = await qualification(1, expected, previous.processId)
      await blockedTraffic(1, admin, 'WAITING_CONFIGURATION')
      check(BigInt(rejected.epoch) > BigInt(previous.epoch), 'Restart inherited an old evidence epoch')
      await qualification(0, 'MATCH'); ok(await me(0, admin), 'Healthy A remains authenticated during rejected B')
      check(deploymentBaseline() === baseline, 'Rejected peer altered immutable baseline')
      if (fault === 'openapi-missing') check(!fs.existsSync(path.join(work, 'absent-openapi.key')), 'Missing-key probe generated a file')
      record('Rejected B qualification preserves healthy A and baseline', { fault, expected, epoch: rejected.epoch })
      previous = rejected
    }
    await stop(children[1]); await free(ports[1]); launch(1); await ready(1)
    const recovered = await qualification(1, 'MATCH', previous.processId)
    check(BigInt(recovered.epoch) > BigInt(previous.epoch), 'Recovered process did not re-prove qualification')
    ok(await me(1, admin), 'Recovered B retains original login'); check(deploymentBaseline() === baseline, 'Recovery replaced baseline')
    record('Restored B re-proves its new process and retains original session')
    await stop(children[0])
    // Exceed both the 15s evidence lifetime and 30s membership lease using live read-only observations.
    const until = Date.now() + 32000
    while (Date.now() < until) { await qualification(1, 'MATCH'); ok(await me(1, admin), 'B continues without bootstrap A'); await pause(1000) }
    const oldA = deploymentRow(0)
    await free(ports[0]); launch(0); await ready(0)
    const returned = await qualification(0, 'MATCH', oldA.processId)
    check(BigInt(returned.epoch) > BigInt(oldA.epoch) && deploymentBaseline() === baseline, 'Restarted bootstrap did not preserve baseline and re-prove')
    ok(await me(0, admin), 'Restarted bootstrap retains original login')
    record('Qualified B survives bootstrap A lease expiry; restarted A re-proves without replacing baseline')
  }
  if (includeStorage) {
    const anchor = storageAnchor()
    let previous = (await storageQualification(1, true)).identity
    for (const fault of ['split', 'missing']) {
      await stop(children[1]); await free(ports[1]); launch(1, { storageFault: fault })
      const rejected = await storageQualification(1, false, previous.processId, fault !== 'missing')
      await blockedTraffic(1, admin, 'WAITING_STORAGE')
      check(BigInt(rejected.identity.epoch) > BigInt(previous.epoch), 'Storage restart reused prior epoch')
      await storageQualification(0, true); ok(await me(0, admin), 'Healthy A during wrong B directory')
      check(storageAnchor() === anchor, 'Wrong directory replaced shared anchor')
      if (fault === 'missing') check(!fs.existsSync(path.join(work, 'missing-managed')), 'Missing mount was created locally')
      record('Bad B managed root rejects readiness while A remains qualified', { fault })
      previous = rejected.identity
    }
    await stop(children[1]); await free(ports[1]); launch(1); await ready(1)
    const recovered = await storageQualification(1, true, previous.processId)
    ok(await me(1, admin), 'Recovered B storage retains session')
    record('Restored B root re-proves storage in new process')
    const hash = text => crypto.createHash('sha256').update(text).digest('hex')
    const offer = recovered.report.offer
    const probe = path.join(work, 'managed', '.sma-cluster', hash(hash(`${offer.manifestHash}:${offer.epoch}`)), hash('file.local.default'), offer.nodeId, `${offer.processId}.probe`)
    const managed = path.resolve(work, 'managed')
    check(path.resolve(probe).startsWith(managed + path.sep) && fs.lstatSync(probe).isFile() && !fs.lstatSync(probe).isSymbolicLink(), 'Probe fixture outside owned managed root')
    const original = fs.readFileSync(probe)
    check(original.length === 32 && hash(original) === offer.digests['file.local.default'], 'Probe fixture digest differs')
    fs.unlinkSync(probe)
    await storageQualification(1, false)
    const until = Date.now() + 17000
    while (Date.now() < until) {
      check(!fs.existsSync(probe), 'Lost probe was silently recreated')
      await storageQualification(0, true); ok(await me(0, admin), 'A remains usable during missing B probe'); await pause(1000)
    }
    const failed = storageRow(1)
    await blockedTraffic(1, admin, 'WAITING_STORAGE')
    check(!failed.certified && BigInt(failed.generation) > BigInt(recovered.report.generation), 'Lost probe retained old certificate')
    record('Lost B probe stays absent beyond evidence TTL and revokes only B')
    fs.writeFileSync(probe, original, { flag: 'wx' }) // Restore this run's exact known bytes; production never auto-recreates it.
    const restored = await storageQualification(1, true)
    check(BigInt(restored.report.generation) >= BigInt(failed.generation) && storageAnchor() === anchor, 'Restoration reused old generation or changed anchor')
    ok(await me(1, admin), 'B restored probe retains original session')
    record('Restored exact probe bytes require fresh reciprocal acknowledgement before READY')
  }
  if (includeTraffic) {
    check(trafficChecks.length === 11, 'Post-startup fault coverage is incomplete')
    record('Post-startup traffic gate rejects login/session/WS/app/open on all eleven configuration and storage faults; liveness stays UP and recovery retains sessions')
  }
  passed = true
} catch (failure) {
  // Log the stage failure before potentially slow database teardown, never a response/request body.
  console.error('Cluster check stopped: ' + String(failure.message).replaceAll(adminPassword, '[redacted]').replaceAll(password, '[redacted]'))
  if (includeTraffic && children.some(child => child?.exitCode === null)) {
    for (let sample = 0; sample < 3; sample++) {
      for (const node of [0, 1]) if (children[node]?.exitCode === null) await observeTraffic(node, 'failure-diagnosis')
      if (sample < 2) await pause(1000)
    }
  }
  throw failure
} finally {
  let cleanupComplete = false
  try {
    console.log('Cleaning only this run\'s JVMs, database and generated keys')
    if (browser) await browser.close()
    if (server) await new Promise(resolve => server.close(resolve))
    for (const child of children) await stop(child)
    for (const log of logs) fs.closeSync(log)
    if (created && /^sma_p08_cluster_\d+$/.test(schema) && /^sma_p08_\d+$/.test(user)) sql(`DROP DATABASE IF EXISTS \`${schema}\`; DROP USER IF EXISTS '${user}'@'127.0.0.1';`, true)
    cleanupComplete = true
  } finally {
    for (const name of ownedKeyNames) { const file = path.join(work, name); if (fs.existsSync(file)) fs.unlinkSync(file) }
    if (fs.existsSync(work)) fs.writeFileSync(path.join(work, 'result.json'), JSON.stringify({ passed, cleanupComplete, runnerSha256, jarSha256, redisTimeout, m2Included: includeM2 && !trafficOnly, routingIncluded: includeRouting && !trafficOnly, schemaMigrationIncluded: includeSchemaMigration, evidenceIncluded: includeEvidence, storageIncluded: includeStorage, trafficIncluded: includeTraffic, trafficOnly, businessMatrixReference, deploymentMode: 'single', namespace, schema, processes, evidence, failureObservations, trafficChecks, finishedAt: new Date().toISOString(), credentialsPersisted: false, boundaries: ['No load balancer qualification', ...(trafficOnly ? ['Focused post-startup traffic matrix; full business matrix is separate'] : []), ...(includeM2 ? ['Single-host shared directory; no multi-host mount qualification', ...(trafficOnly ? [] : ['Delivery pause is configuration fault injection, not a network partition'])] : ['No cross-node event broadcast qualification']), 'Redis namespace data expires under registered TTL; never global flush'] }, null, 2) + '\n')
  }
}
