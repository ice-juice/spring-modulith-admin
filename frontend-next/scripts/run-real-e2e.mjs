import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import net from 'node:net'
import { fileURLToPath } from 'node:url'
import { spawn, spawnSync } from 'node:child_process'
const frontend = fileURLToPath(new URL('../', import.meta.url)), root = path.dirname(frontend)
const suffix = Date.now().toString(), schema = 'sma_next_e2e_' + suffix, user = 'sma_next_' + suffix
const password = crypto.randomBytes(20).toString('hex'), adminPassword = 'A7' + crypto.randomBytes(5).toString('hex')
const work = path.join(frontend, '.e2e-runtime', suffix)
fs.mkdirSync(work, { recursive: true })
const mysql = process.env.SMA_E2E_MYSQL || path.join(process.env.LOCALAPPDATA, 'Programs/mysql-8.4.10-winx64/bin/mysql.exe')
const standalone = process.env.SMA_E2E_STANDALONE_MYSQL === '1'
const mysqlPort = Number(process.env.SMA_E2E_MYSQL_PORT || (standalone ? 3307 : 3306))
const backendPort = Number(process.env.SMA_E2E_BACKEND_PORT || 8081)
const backendUrl = `http://127.0.0.1:${backendPort}`
const frontendPort = Number(process.env.SMA_E2E_FRONTEND_PORT || 5174)
const legacyPort = Number(process.env.SMA_E2E_LEGACY_PORT || 5188)
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
if (process.env.SMA_E2E_LEGACY === '1') await free(legacyPort)
let backend, created = false, passed = false
const logFile = fs.openSync(path.join(work, 'backend.log'), 'a')
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
      if (mysqlServer.exitCode !== null) throw new Error('Isolated MySQL exited; see its log')
      try { sql('SELECT 1'); break } catch { if (Date.now() > readyBy) { console.log('Task-owned MySQL is still running; continuing readiness observation.'); readyBy = Date.now() + 60000 } }
      await new Promise(resolve => setTimeout(resolve, 300))
    }
  }
  sql(`CREATE DATABASE \`${schema}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '${user}'@'127.0.0.1' IDENTIFIED BY '${password}'; GRANT ALL PRIVILEGES ON \`${schema}\`.* TO '${user}'@'127.0.0.1';`)
  created = true
  const runJar = path.join(work, 'backend.jar'); fs.copyFileSync(jar, runJar)
  const authKeys = path.join(work, 'auth-security.keys')
  fs.writeFileSync(authKeys, 'encryption.active=e2e\nencryption.e2e=' + crypto.randomBytes(32).toString('base64') + '\npepper.active=e2e\npepper.e2e=' + crypto.randomBytes(32).toString('base64') + '\n', { mode: 0o600 })
  const fingerprintKey = crypto.randomBytes(32).toString('base64')
  const launch = (operatorId) => spawn(path.join(javaHome, 'bin/java.exe'), ['-jar', runJar, '--server.address=127.0.0.1', '--server.port=' + backendPort, '--sma.tenant.bootstrap-hosts=localhost,127.0.0.1,[::1]', '--management.endpoint.health.probes.enabled=true', '--sma.security.browser.allowed-origins=http://127.0.0.1:' + frontendPort + ',http://127.0.0.1:' + legacyPort, '--sma.file.directory=' + path.join(work, 'uploads'), '--sma.openapi.key-file=' + path.join(work, 'openapi.key'), ...(operatorId ? ['--sma.tenant.platform-admin-user-ids=' + operatorId] : [])], { cwd: work, windowsHide: true, stdio: ['ignore', logFile, logFile], env: { ...process.env, DB_NAME: schema, DB_USERNAME: user, DB_PASSWORD: password, DB_HOST: '127.0.0.1', DB_PORT: String(mysqlPort), SMA_APP_MASTER_KEY: '', SPRING_DATASOURCE_URL: `jdbc:mysql://127.0.0.1:${mysqlPort}/${schema}?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true`, SPRING_DATASOURCE_USERNAME: user, SPRING_DATASOURCE_PASSWORD: password, SMA_ADMIN_PASSWORD: adminPassword, SMA_AUTH_SECURITY_KEY_FILE: authKeys, SMA_SECURITY_REQUESTS_FINGERPRINT_KEY: fingerprintKey } })
  async function ready() {
    let deadline = Date.now() + 300000
    while (true) {
      if (backend.exitCode !== null) throw new Error('Isolated backend exited. See ' + path.relative(root, path.join(work, 'backend.log')))
      try { const response = await fetch(backendUrl + '/actuator/health/readiness', { signal: AbortSignal.timeout(1500) }); if (response.ok && (await response.json()).status === 'UP') break } catch { /* Poll only this known live process. */ }
      if (Date.now() > deadline) { console.log('Task-owned backend is still running; continuing readiness observation.'); deadline = Date.now() + 60000 }
      await new Promise(resolve => setTimeout(resolve, 500))
    }
  }
  backend = launch()
  await ready()
  if (process.env.SMA_E2E_PLATFORM_OPERATOR === '1') {
    // Discover the account created by the normal bootstrap in this runner's schema, then explicitly grant deployment identity.
    // Only our own child process is restarted; no production/default platform operator is configured or impersonated.
    const operatorId = sql(`SELECT id FROM \`${schema}\`.iam_user WHERE tenant_id=1 AND portal='admin' AND username='admin' AND deleted=0;`, user)
    if (!/^[1-9][0-9]{0,18}$/.test(operatorId)) throw new Error('Expected one task-owned bootstrap administrator.')
    const previous = backend
    await new Promise((resolve, reject) => { previous.once('exit', resolve); previous.once('error', reject); previous.kill() })
    await free(backendPort)
    backend = launch(operatorId)
    await ready()
  }
  sql(`UPDATE \`${schema}\`.config_parameter SET param_value='false' WHERE param_key='login.captchaEnabled';`, user)
  console.log('Isolated backend ready on ' + backendPort + '; database ' + schema + '. Existing 8080/5173 services are untouched.')
  const code = await new Promise((resolve, reject) => {
    const child = spawn(process.execPath, ['node_modules/@playwright/test/cli.js', 'test', '--config=playwright.real.config.ts', ...process.argv.slice(2)], { cwd: frontend, windowsHide: true, stdio: 'inherit', env: { ...process.env, SMA_NEXT_BACKEND_URL: backendUrl, SMA_E2E_ADMIN_PASSWORD: adminPassword } })
    child.once('error', reject); child.once('exit', code => resolve(code))
  })
  if (code !== 0) throw new Error('Real backend browser checks failed; inspect test-results-real/.')
  passed = true
} finally {
  if (backend && backend.exitCode === null) { backend.kill(); await new Promise(resolve => { backend.once('exit', resolve); setTimeout(resolve, 5000) }) }
  fs.closeSync(logFile)
  if (created && /^sma_next_e2e_\d+$/.test(schema) && /^sma_next_\d+$/.test(user)) sql(`DROP DATABASE IF EXISTS \`${schema}\`; DROP USER IF EXISTS '${user}'@'127.0.0.1';`)
  if (mysqlServer && mysqlServer.exitCode === null) {
    sql('SHUTDOWN')
    await new Promise(resolve => { mysqlServer.once('exit', resolve); setTimeout(resolve, 10000) })
    if (mysqlServer.exitCode === null) mysqlServer.kill()
    else if (mysqlData.startsWith(work + path.sep) && /^\d+$/.test(path.basename(work))) fs.rmSync(mysqlData, { recursive: true, force: true })
  }
  if (fs.existsSync(path.join(work, 'auth-security.keys'))) fs.unlinkSync(path.join(work, 'auth-security.keys'))
  fs.writeFileSync(path.join(work, 'result.json'), JSON.stringify({ schema, passed, finishedAt: new Date().toISOString(), credentialsPersisted: false }, null, 2) + '\n')
}
console.log('Real E2E passed; only the task-owned backend, schema and database user were cleaned up.')
