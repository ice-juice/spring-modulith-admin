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
const ports = [Number(process.env.SMA_CLUSTER_PORT_A || 18101), Number(process.env.SMA_CLUSTER_PORT_B || 18102)]
const originPort = Number(process.env.SMA_CLUSTER_ORIGIN_PORT || 5197), origin = `http://127.0.0.1:${originPort}`
const javaHome = process.env.JAVA21_HOME || spawnSync('powershell', ['-NoProfile', '-Command', "[Environment]::GetEnvironmentVariable('JAVA21_HOME','User')"], { encoding: 'utf8', windowsHide: true }).stdout.trim()
const jar = process.env.SMA_E2E_BACKEND_JAR
if (!jar || !mysqlDefaults || !fs.existsSync(jar) || !fs.existsSync(mysqlDefaults) || !fs.existsSync(path.join(javaHome, 'bin/java.exe'))) throw new Error('Set current verified SMA_E2E_BACKEND_JAR and protected SMA_CLUSTER_MYSQL_DEFAULTS; Java 21 and MySQL client are required.')
if (new Set([...ports, originPort]).size !== 3) throw new Error('Distinct isolated ports required')
const password = crypto.randomBytes(24).toString('hex'), adminPassword = 'A7' + crypto.randomBytes(5).toString('hex')
const accountPassword = 'M7n4R8p2V6', namespace = `p08_cluster_${suffix}`
const includeM2 = process.env.SMA_CLUSTER_M2 === '1'
const fingerprintKey = crypto.randomBytes(32).toString('base64')
const runnerSource = fs.readFileSync(fileURLToPath(import.meta.url))
const runnerSha256 = crypto.createHash('sha256').update(runnerSource).digest('hex')
const jarSha256 = crypto.createHash('sha256').update(fs.readFileSync(jar)).digest('hex')
const children = [], logs = [], evidence = [], processes = []
let created = false, browser, server, passed = false
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
function check(condition, message) { if (!condition) throw new Error(message) }
function record(name, details = {}) { evidence.push({ name, ...details }); console.log('PASS ' + name) }
function sql(statement, rootAccount = false) {
  const args = rootAccount ? [`--defaults-extra-file=${mysqlDefaults}`] : ['--no-defaults']
  args.push('--host=127.0.0.1', `--port=${mysqlPort}`, `--user=${rootAccount ? 'root' : user}`, '--batch', '--skip-column-names')
  const result = spawnSync(mysql, args, { input: statement, encoding: 'utf8', windowsHide: true,
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
function launch(node, { signalsEnabled = true } = {}) {
  const log = fs.openSync(path.join(work, `node-${node}.log`), 'a'); logs.push(log)
  const child = spawn(path.join(javaHome, 'bin/java.exe'), ['-Xmx512m', '-jar', path.join(work, 'backend.jar'),
    '--server.address=127.0.0.1', `--server.port=${ports[node]}`, '--management.endpoint.health.probes.enabled=true',
    '--sma.tenant.bootstrap-hosts=localhost,127.0.0.1,[::1]', `--sma.security.browser.allowed-origins=${origin}`,
    `--sma.message.socket.allowed-origins=${origin}`, `--sma.file.directory=${path.join(work, 'uploads')}`,
    `--sma.openapi.key-file=${path.join(work, 'openapi.key')}`, '--sma.cache.provider=redis', `--sma.cache.namespace=${namespace}`,
    '--sma.cache.deployment-mode=single', `--sma.cache.nodes.cluster=${namespace}`, `--sma.cache.nodes.id=node-${node === 0 ? 'a' : 'b'}`,
    '--sma.cache.nodes.expected=node-a,node-b', '--spring.data.redis.host=127.0.0.1', `--spring.data.redis.port=${redisPort}`,
    `--sma.message.signals.enabled=${signalsEnabled}`,
    ...(includeM2 ? ['--sma.file.cluster.shared-resources=file.legacy,file.local.default', `--sma.file.storage.local-roots.default=${path.join(work, 'managed')}`] : [])],
  { cwd: work, windowsHide: true, stdio: ['ignore', log, log], env: { ...process.env,
    DB_NAME: schema, DB_USERNAME: user, DB_PASSWORD: password, DB_HOST: '127.0.0.1', DB_PORT: String(mysqlPort),
    SPRING_DATASOURCE_URL: `jdbc:mysql://127.0.0.1:${mysqlPort}/${schema}?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true`,
    SPRING_DATASOURCE_USERNAME: user, SPRING_DATASOURCE_PASSWORD: password, SMA_APP_MASTER_KEY: '', SMA_ADMIN_PASSWORD: adminPassword,
    SMA_AUTH_SECURITY_KEY_FILE: path.join(work, 'auth.keys'), SMA_SECURITY_REQUESTS_FINGERPRINT_KEY: fingerprintKey } })
  const processEvidence = { node, pid: child.pid, startedAt: new Date().toISOString() }
  processes.push(processEvidence)
  child.once('exit', (code, signal) => { processEvidence.exitCode = code; processEvidence.signal = signal })
  children[node] = child; return child
}
async function api(node, route, { method = 'GET', token, body } = {}) {
  const r = await fetch(`http://127.0.0.1:${ports[node]}${route}`, { method, signal: AbortSignal.timeout(30000),
    headers: { 'Content-Type': 'application/json', 'User-Agent': 'P08-isolated-cluster', 'Idempotency-Key': crypto.randomUUID(), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
  const value = await r.json(); return { http: r.status, ...value }
}
function ok(result, label) { check(result.http === 200 && result.code === 0, `${label}: HTTP ${result.http}, code ${result.code}, error ${result.error || 'none'}`); return result.data }
const me = (node, token) => api(node, '/api/auth/me', { token })
const login = (node, username = 'admin', secret = adminPassword, extra = {}) => api(node, '/api/auth/login', { method: 'POST', body: { username, password: secret, portal: 'admin', remember: false, ...extra } })
const preference = { locale: 'en-US', timeZone: 'UTC', theme: 'dark', tabsEnabled: true, keepTabsAfterReload: false, skin: 'iris' }
function setParameter(key, value) {
  check(['login.captchaEnabled', 'auth.singleTerminal'].includes(key) && ['true', 'false'].includes(value), 'Unexpected fixture parameter')
  const changed = sql(`UPDATE \`${schema}\`.config_parameter SET param_value='${value}' WHERE param_key='${key}'; SELECT ROW_COUNT();`)
  check(changed === '1', 'Fixture parameter must identify exactly one isolated row')
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
  fs.writeFileSync(path.join(work, 'auth.keys'), `encryption.active=e2e\nencryption.e2e=${crypto.randomBytes(32).toString('base64')}\npepper.active=e2e\npepper.e2e=${crypto.randomBytes(32).toString('base64')}\n`, { mode: 0o600 })
  fs.writeFileSync(path.join(work, 'openapi.key'), crypto.randomBytes(32), { mode: 0o600 })
  sql(`CREATE DATABASE \`${schema}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`, true); created = true
  sql(`CREATE USER '${user}'@'127.0.0.1' IDENTIFIED BY '${password}'; GRANT ALL PRIVILEGES ON \`${schema}\`.* TO '${user}'@'127.0.0.1';`, true)
  launch(0); console.log(`Starting isolated node A; evidence directory ${path.relative(root, work)}`); await ready(0)
  launch(1); console.log('Starting isolated node B against migrated schema'); await ready(1)
  setParameter('login.captchaEnabled', 'false')
  const admin = ok(await login(0), 'A login').token
  check(ok(await me(1, admin), 'B me').username === 'admin', 'Cross-node identity differs')
  record('A login, B authenticated access', { pids: children.map(c => c.pid), ports })

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

  setParameter('login.captchaEnabled', 'true')
  const challenge = ok(await api(0, '/api/auth/captcha'), 'Issue captcha')
  check(/^[A-Za-z0-9_-]+$/.test(challenge.id), 'Unexpected challenge ID shape')
  const answerHash = crypto.createHash('sha256').update(`${challenge.id}:ABCD`).digest('hex')
  sql(`UPDATE \`${schema}\`.auth_captcha SET answer_hash='${answerHash}' WHERE challenge_id='${challenge.id}';`)
  const captchaRace = await Promise.all([0, 1].map(n => login(n, 'cluster1', accountPassword, { captchaId: challenge.id, captcha: 'ABCD' })))
  check(captchaRace.filter(r => r.code === 0 && r.data?.token).length === 1, 'Captcha was not consumed exactly once')
  setParameter('login.captchaEnabled', 'false'); record('One captcha consumed once across A/B')

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

  setParameter('auth.singleTerminal', 'true')
  const terminals = await Promise.all([0, 1].map(n => login(n, 'cluster2', accountPassword).then(r => ok(r, 'Concurrent terminal login').token)))
  const terminalChecks = await Promise.all(terminals.map(token => Promise.all([0, 1].map(n => me(n, token)))))
  check(terminalChecks.filter(rs => rs.every(r => r.code === 0)).length === 1 && terminalChecks.filter(rs => rs.every(r => r.http === 401)).length === 1, 'Single-terminal winner differs across nodes')
  setParameter('auth.singleTerminal', 'false'); record('Concurrent single-terminal login has one shared winner')

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
  passed = true
} catch (failure) {
  // Log the stage failure before potentially slow database teardown, never a response/request body.
  console.error('Cluster check stopped: ' + String(failure.message).replaceAll(adminPassword, '[redacted]').replaceAll(password, '[redacted]'))
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
    for (const name of ['auth.keys', 'openapi.key']) { const file = path.join(work, name); if (fs.existsSync(file)) fs.unlinkSync(file) }
    if (fs.existsSync(work)) fs.writeFileSync(path.join(work, 'result.json'), JSON.stringify({ passed, cleanupComplete, runnerSha256, jarSha256, m2Included: includeM2, deploymentMode: 'single', namespace, schema, processes, evidence, finishedAt: new Date().toISOString(), credentialsPersisted: false, boundaries: ['No load balancer qualification', ...(includeM2 ? ['Single-host shared directory; no multi-host mount qualification', 'Delivery pause is configuration fault injection, not a network partition'] : ['No cross-node event broadcast qualification']), 'Redis namespace data expires under registered TTL; never global flush'] }, null, 2) + '\n')
  }
}
