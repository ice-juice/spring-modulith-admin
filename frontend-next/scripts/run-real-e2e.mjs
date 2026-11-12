import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import net from 'node:net'
import { fileURLToPath } from 'node:url'
import { spawn, spawnSync } from 'node:child_process'
import { isolatedScope, ownedChildPath, stopOwnedChild, stopOwnedChildren, isolatedPeerPort } from './e2e-isolation.mjs'
import { securityTenantFixture } from './security-tenant-fixture.mjs'
const frontend = fileURLToPath(new URL('../', import.meta.url)), root = path.dirname(frontend)
const suffix = Date.now().toString(), scope = isolatedScope(process.env.SMA_E2E_SUITE || 'next', suffix, path.join(frontend, '.e2e-runtime'))
const { schema, user, work } = scope
const tenantFixture = process.env.SMA_E2E_SECURITY_TENANTS === '1' ? securityTenantFixture(scope) : undefined
const password = crypto.randomBytes(20).toString('hex'), adminPassword = 'A7' + crypto.randomBytes(5).toString('hex')
fs.mkdirSync(work, { recursive: true })
const mysql = process.env.SMA_E2E_MYSQL || path.join(process.env.LOCALAPPDATA, 'Programs/mysql-8.4.10-winx64/bin/mysql.exe')
const standalone = process.env.SMA_E2E_STANDALONE_MYSQL === '1'
const mysqlPort = Number(process.env.SMA_E2E_MYSQL_PORT || (standalone ? 3307 : 3306))
const backendPort = Number(process.env.SMA_E2E_BACKEND_PORT || 8081)
const backendUrl = `http://127.0.0.1:${backendPort}`
const frontendPort = Number(process.env.SMA_E2E_FRONTEND_PORT || 5174)
const legacyPort = Number(process.env.SMA_E2E_LEGACY_PORT || 5188)
const peerPort = isolatedPeerPort(scope.suite, backendPort, frontendPort, process.env.SMA_E2E_PEER_BACKEND_PORT)
if (peerPort && process.env.SMA_E2E_LEGACY === '1' && peerPort === legacyPort) throw new Error('Peer and legacy frontend require distinct ports')
if (scope.suite === 'security' && (!process.env.SMA_E2E_BACKEND_JAR || !process.env.SMA_E2E_BACKEND_PORT || !process.env.SMA_E2E_FRONTEND_PORT
  || [backendPort, frontendPort].some(port => !Number.isInteger(port) || port < 1024 || port > 65535 || [8080, 5173, 5174].includes(port))
  || backendPort === frontendPort)) throw new Error('Security E2E requires a verified artifact and explicit isolated backend/frontend ports')
const mysqlData = path.join(work, 'mysql-data')
let mysqlServer
const javaHome = process.env.JAVA21_HOME || spawnSync('powershell', ['-NoProfile', '-Command', "[Environment]::GetEnvironmentVariable('JAVA21_HOME','User')"], { encoding: 'utf8', windowsHide: true }).stdout.trim()
const jar = process.env.SMA_E2E_BACKEND_JAR || path.join(root, 'backend/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar')
if (!fs.existsSync(mysql) || !fs.existsSync(path.join(javaHome, 'bin/java.exe')) || !fs.existsSync(jar)) throw new Error('Real E2E requires the existing local MySQL client, JDK 21 and a current backend package.')
function sql(statement, account = 'root') {
  const result = spawnSync(mysql, ['--host=127.0.0.1', '--port=' + mysqlPort, '--user=' + account, '--batch', '--skip-column-names'], { input: statement, encoding: 'utf8', windowsHide: true, env: { ...process.env, MYSQL_PWD: account === 'root' ? standalone ? '' : process.env.SMA_E2E_MYSQL_ROOT_PASSWORD || 'root' : password } })
  if (result.status !== 0) throw new Error('Isolated database command failed: ' + String(result.stderr).replaceAll(password, '[redacted]').replaceAll(adminPassword, '[redacted]'))
  return result.stdout.trim()
}
async function free(port) { await new Promise((resolve, reject) => { const server = net.createServer(); server.once('error', () => reject(new Error('Port ' + port + ' is occupied; no existing service will be stopped.'))); server.listen(port, '127.0.0.1', () => server.close(resolve)) }) }
await free(backendPort); await free(frontendPort)
if (peerPort) await free(peerPort)
if (process.env.SMA_E2E_LEGACY === '1') await free(legacyPort)
let backend, peer, databaseCreated = false, accountCreated = false, passed = false, artifactSha256
const children = [], processes = [], phases = []
const logFile = fs.openSync(path.join(work, 'backend.log'), 'a')
const peerLogFile = peerPort ? fs.openSync(path.join(work, 'peer.log'), 'a') : undefined
try {
  if (standalone) {
    await free(mysqlPort)
    const mysqld = path.join(path.dirname(mysql), 'mysqld.exe')
    fs.mkdirSync(mysqlData)
    const initLog = fs.openSync(path.join(work, 'mysql-init.log'), 'a')
    const initializer = spawn(mysqld, ['--no-defaults', '--initialize-insecure', '--datadir=' + mysqlData], { windowsHide: true, stdio: ['ignore', initLog, initLog] })
    console.log('Initializing task-owned MySQL: PID ' + initializer.pid + ', logs in ' + path.relative(root, work))
    const initialized = await new Promise((resolve, reject) => { initializer.once('error', reject); initializer.once('exit', resolve) })
    fs.closeSync(initLog)
    if (initialized !== 0) throw new Error('Isolated MySQL initialization failed with exit ' + initialized + '; see mysql-init.log')
    mysqlServer = spawn(mysqld, ['--no-defaults', '--datadir=' + mysqlData, '--port=' + mysqlPort, '--bind-address=127.0.0.1', '--mysqlx=OFF', '--log-error=' + path.join(work, 'mysql.log')], { windowsHide: true, stdio: 'ignore' })
    let readyBy = Date.now() + 60000
    while (true) {
      if (mysqlServer.exitCode !== null || mysqlServer.signalCode !== null) throw new Error('Isolated MySQL exited; see its log')
      try { sql('SELECT 1'); break } catch { if (Date.now() > readyBy) { console.log('Task-owned MySQL is still running; continuing readiness observation.'); readyBy = Date.now() + 60000 } }
      await new Promise(resolve => setTimeout(resolve, 300))
    }
  }
  sql(`CREATE DATABASE \`${schema}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`)
  databaseCreated = true
  sql(`CREATE USER '${user}'@'127.0.0.1' IDENTIFIED BY '${password}';`)
  accountCreated = true
  sql(`GRANT ALL PRIVILEGES ON \`${schema}\`.* TO '${user}'@'127.0.0.1';`)
  const runJar = path.join(work, 'backend.jar'); fs.copyFileSync(jar, runJar)
  artifactSha256 = crypto.createHash('sha256').update(fs.readFileSync(runJar)).digest('hex')
  const authKeys = path.join(work, 'auth-security.keys')
  fs.writeFileSync(authKeys, 'encryption.active=e2e\nencryption.e2e=' + crypto.randomBytes(32).toString('base64') + '\npepper.active=e2e\npepper.e2e=' + crypto.randomBytes(32).toString('base64') + '\n', { mode: 0o600 })
  const fingerprintKey = crypto.randomBytes(32).toString('base64')
  const tenantKeys = path.join(work, 'tenant-credentials.keys')
  if (tenantFixture) fs.writeFileSync(tenantKeys, 'active=e2e\nkey.e2e=' + crypto.randomBytes(32).toString('base64') + '\n', { mode: 0o600 })
  const launch = (operatorId, port = backendPort, validate = false) => {
    const log = port === backendPort ? logFile : peerLogFile
    const child = spawn(path.join(javaHome, 'bin/java.exe'), ['-jar', runJar, '--server.address=127.0.0.1', '--server.port=' + port, '--sma.tenant.bootstrap-hosts=localhost,127.0.0.1,[::1]', '--management.endpoint.health.probes.enabled=true', '--sma.security.browser.allowed-origins=http://127.0.0.1:' + frontendPort + ',http://127.0.0.1:' + legacyPort, '--sma.file.directory=' + path.join(work, 'uploads'), '--sma.openapi.key-file=' + path.join(work, 'openapi.key'), ...(validate ? ['--sma.deployment.schema-mode=validate', '--sma.cache.deployment-mode=single'] : []), ...(operatorId ? ['--sma.tenant.platform-admin-user-ids=' + operatorId] : [])], { cwd: work, windowsHide: true, stdio: ['ignore', log, log], env: { ...process.env, DB_NAME: schema, DB_USERNAME: user, DB_PASSWORD: password, DB_HOST: '127.0.0.1', DB_PORT: String(mysqlPort), SMA_APP_MASTER_KEY: '', SPRING_DATASOURCE_URL: `jdbc:mysql://127.0.0.1:${mysqlPort}/${schema}?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true`, SPRING_DATASOURCE_USERNAME: user, SPRING_DATASOURCE_PASSWORD: password, SMA_ADMIN_PASSWORD: adminPassword, SMA_AUTH_SECURITY_KEY_FILE: authKeys, SMA_SECURITY_REQUESTS_FINGERPRINT_KEY: fingerprintKey, ...(tenantFixture ? { SMA_TENANT_CREDENTIAL_KEY_FILE: tenantKeys } : {}) } })
    const evidence = { port, pid: child.pid, validate, startedAt: new Date().toISOString() }
    children.push(child); processes.push(evidence)
    child.once('exit', (code, signal) => { evidence.exitCode = code; evidence.signal = signal })
    return child
  }
  async function ready(child = backend, port = backendPort) {
    let deadline = Date.now() + 300000
    while (true) {
      if (child.exitCode !== null || child.signalCode !== null) throw new Error('Isolated backend exited on ' + port + '; inspect its owned log')
      try { const response = await fetch(`http://127.0.0.1:${port}/actuator/health/readiness`, { signal: AbortSignal.timeout(1500) }); if (response.ok && (await response.json()).status === 'UP') break } catch { /* Poll only this known live process. */ }
      if (Date.now() > deadline) { console.log('Task-owned backend on ' + port + ' is still running; continuing readiness observation.'); deadline = Date.now() + 60000 }
      await new Promise(resolve => setTimeout(resolve, 500))
    }
  }
  backend = launch()
  await ready()
  // The registered captcha parameter no longer has a copied row in a fresh tenant.
  // Seed only this task-owned tenant's explicit override, then restart our child so
  // the parameter cache and the public login projection both observe the same value.
  sql(`INSERT INTO \`${schema}\`.config_parameter
    (id,tenant_id,param_key,group_key,name,name_key,value_type,param_value,builtin,public_readable,secret,sort,created_at,created_by,updated_at,updated_by,deleted,definition_contract,value_overridden,presentation_overridden)
    SELECT 9223372036854775000,1,'login.captchaEnabled','login','Login captcha','config.param.login.captchaEnabled','BOOLEAN','false',1,1,0,0,NOW(3),0,NOW(3),0,0,h.contract_hash,1,0
    FROM \`${schema}\`.config_shared_head h WHERE h.kind='PARAMETER' AND h.config_key='login.captchaEnabled'
      AND NOT EXISTS (SELECT 1 FROM \`${schema}\`.config_parameter p WHERE p.tenant_id=1 AND p.param_key='login.captchaEnabled' AND p.deleted=0);
    UPDATE \`${schema}\`.config_parameter SET param_value='false',value_overridden=1 WHERE tenant_id=1 AND param_key='login.captchaEnabled' AND deleted=0;`, user)
  if (sql(`SELECT COUNT(*) FROM \`${schema}\`.config_parameter WHERE tenant_id=1 AND param_key='login.captchaEnabled' AND param_value='false' AND value_overridden=1 AND deleted=0;`, user) !== '1') throw new Error('Task-owned captcha override was not initialized.')
  let operatorId
  if (process.env.SMA_E2E_PLATFORM_OPERATOR === '1') {
    // Discover the account created by the normal bootstrap in this runner's schema, then explicitly grant deployment identity.
    // Only our own child process is restarted; no production/default platform operator is configured or impersonated.
    operatorId = sql(`SELECT id FROM \`${schema}\`.iam_user WHERE tenant_id=1 AND portal='admin' AND username='admin' AND deleted=0;`, user)
    if (!/^[1-9][0-9]{0,18}$/.test(operatorId)) throw new Error('Expected one task-owned bootstrap administrator.')
  }
  const previous = backend
  await stopOwnedChild(previous)
  await free(backendPort)
  if (tenantFixture) {
    if (sql(tenantFixture.preflight, user) !== '0') throw new Error('Security tenant fixture would collide with existing data')
    const permissionCount = Number(sql(`SELECT COUNT(*) FROM \`${schema}\`.iam_permission WHERE tenant_id=1 AND portal='admin' AND deleted=0;`, user))
    if (permissionCount < 1 || permissionCount > 10000) throw new Error('Security fixture permission catalog is outside its reserved range')
    sql(tenantFixture.seed, user)
    if (sql(tenantFixture.verified, user) !== '1') throw new Error('Security tenant fixture initialization was incomplete')
  }
  backend = launch(operatorId, backendPort, !!peerPort)
  await ready()
  if (peerPort) { peer = launch(operatorId, peerPort, true); await ready(peer, peerPort) }
  const entry = await (await fetch(backendUrl + '/api/tenant/entry')).json()
  if (entry?.data?.login?.captchaEnabled !== false) throw new Error('Task-owned login still requires captcha after the configuration restart.')
  console.log('Isolated backend ready on ' + backendPort + '; database ' + schema + '. Existing 8080/5173 services are untouched.')
  async function browserPhase(phase) {
    const output = process.env.SMA_E2E_OUTPUT_DIR || (process.env.SMA_E2E_FRONTEND_PORT ? 'test-results-real-' + frontendPort : 'test-results-real')
    const phaseOutput = peerPort ? output + '-' + phase : output
    const startedAt = Date.now()
    const code = await new Promise((resolve, reject) => {
      const child = spawn(process.execPath, ['node_modules/@playwright/test/cli.js', 'test', '--config=playwright.real.config.ts', ...process.argv.slice(2)], { cwd: frontend, windowsHide: true, stdio: 'inherit', env: { ...process.env,
        SMA_NEXT_BACKEND_URL: backendUrl, SMA_E2E_ADMIN_PASSWORD: adminPassword,
        SMA_E2E_RUN_PHASE: phase, SMA_E2E_RUN_ID: suffix, SMA_E2E_RUN_WORK: work,
        ...(tenantFixture ? { SMA_E2E_TENANT_HOST: tenantFixture.host, SMA_E2E_TENANT_USERNAME: tenantFixture.username } : {}),
        ...(peerPort ? { SMA_E2E_PEER_BACKEND_URL: `http://127.0.0.1:${peerPort}`, SMA_E2E_OUTPUT_DIR: phaseOutput } : {}),
      } })
      children.push(child)
      child.once('error', reject); child.once('exit', code => resolve(code))
    })
    const report = path.join(frontend, phaseOutput, 'results.json')
    const stats = fs.existsSync(report) && fs.statSync(report).mtimeMs >= startedAt ? JSON.parse(fs.readFileSync(report, 'utf8')).stats : undefined
    const verified = code === 0 && stats?.expected > 0 && stats?.unexpected === 0
    phases.push({ phase, passed: verified, reportAvailable: !!stats, tests: stats?.expected || 0, failed: stats?.unexpected || 0, skipped: stats?.skipped || 0 })
    if (!verified) throw new Error('Real backend browser checks failed or proved no passing cases in ' + phase + '; inspect owned test output')
  }
  await browserPhase(peerPort ? 'before-restart' : 'single')
  if (peerPort) {
    await stopOwnedChildren([backend, peer])
    await free(backendPort); await free(peerPort)
    backend = launch(operatorId, backendPort, true); peer = launch(operatorId, peerPort, true)
    await Promise.all([ready(backend), ready(peer, peerPort)])
    await browserPhase('after-restart')
  }
  passed = true
} finally {
  let cleanupComplete = false
  try {
    await stopOwnedChildren(children)
    fs.closeSync(logFile)
    if (peerLogFile !== undefined) fs.closeSync(peerLogFile)
    await free(backendPort); await free(frontendPort)
    if (peerPort) await free(peerPort)
    if (process.env.SMA_E2E_LEGACY === '1') await free(legacyPort)
    if (databaseCreated) sql(`DROP DATABASE IF EXISTS \`${scope.schema}\`;`)
    if (accountCreated) sql(`DROP USER IF EXISTS '${scope.user}'@'127.0.0.1';`)
    if (sql(`SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${scope.schema}';`) !== '0'
      || sql(`SELECT COUNT(*) FROM mysql.user WHERE user='${scope.user}' AND host='127.0.0.1';`) !== '0') {
      throw new Error('Isolated database/account absence could not be verified; cleanup is incomplete')
    }
    if (mysqlServer) {
      await stopOwnedChild(mysqlServer)
      fs.rmSync(ownedChildPath(work, mysqlData), { recursive: true, force: true })
    }
    for (const name of ['auth-security.keys', 'openapi.key', 'tenant-credentials.keys']) {
      const file = ownedChildPath(work, path.join(work, name))
      if (fs.existsSync(file)) fs.unlinkSync(file)
    }
    cleanupComplete = true
  } finally {
    fs.writeFileSync(path.join(work, 'result.json'), JSON.stringify({ suite: scope.suite, schema, passed, cleanupComplete, artifactSha256, processes, phases,
      finishedAt: new Date().toISOString(), credentialsPersisted: !cleanupComplete }, null, 2) + '\n')
  }
}
console.log('Real E2E passed; only the task-owned backend, schema and database user were cleaned up.')
