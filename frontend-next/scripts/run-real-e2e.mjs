import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import net from 'node:net'
import { fileURLToPath } from 'node:url'
import { spawn, spawnSync } from 'node:child_process'
const frontend = fileURLToPath(new URL('../', import.meta.url)), root = path.dirname(frontend)
const suffix = Date.now().toString(), schema = 'sma_next_e2e_' + suffix, user = 'sma_next_' + suffix
const password = crypto.randomBytes(20).toString('hex'), adminPassword = crypto.randomBytes(24).toString('hex')
const work = path.join(frontend, '.e2e-runtime', suffix)
fs.mkdirSync(work, { recursive: true })
const mysql = process.env.SMA_E2E_MYSQL || path.join(process.env.LOCALAPPDATA, 'Programs/mysql-8.4.10-winx64/bin/mysql.exe')
const javaHome = process.env.JAVA21_HOME || spawnSync('powershell', ['-NoProfile', '-Command', "[Environment]::GetEnvironmentVariable('JAVA21_HOME','User')"], { encoding: 'utf8', windowsHide: true }).stdout.trim()
const jar = path.join(root, 'backend/target/springboot-modulith-admin-0.0.1-SNAPSHOT.jar')
if (!fs.existsSync(mysql) || !fs.existsSync(path.join(javaHome, 'bin/java.exe')) || !fs.existsSync(jar)) throw new Error('Real E2E requires the existing local MySQL client, JDK 21 and a current backend package.')
function sql(statement, account = 'root') {
  const result = spawnSync(mysql, ['--host=127.0.0.1', '--port=3306', '--user=' + account, '--batch', '--skip-column-names'], { input: statement, encoding: 'utf8', windowsHide: true, env: { ...process.env, MYSQL_PWD: account === 'root' ? process.env.SMA_E2E_MYSQL_ROOT_PASSWORD || 'root' : password } })
  if (result.status !== 0) throw new Error('Isolated database command failed: ' + String(result.stderr).replaceAll(password, '[redacted]').replaceAll(adminPassword, '[redacted]'))
}
async function free(port) { await new Promise((resolve, reject) => { const server = net.createServer(); server.once('error', () => reject(new Error('Port ' + port + ' is occupied; no existing service will be stopped.'))); server.listen(port, '127.0.0.1', () => server.close(resolve)) }) }
await free(8081); await free(5174)
let backend, created = false, passed = false
const logFile = fs.openSync(path.join(work, 'backend.log'), 'a')
try {
  sql(`CREATE DATABASE \`${schema}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '${user}'@'127.0.0.1' IDENTIFIED BY '${password}'; GRANT ALL PRIVILEGES ON \`${schema}\`.* TO '${user}'@'127.0.0.1';`)
  created = true
  const runJar = path.join(work, 'backend.jar'); fs.copyFileSync(jar, runJar)
  backend = spawn(path.join(javaHome, 'bin/java.exe'), ['-jar', runJar, '--server.address=127.0.0.1', '--server.port=8081', '--sma.file.directory=' + path.join(work, 'uploads'), '--sma.openapi.key-file=' + path.join(work, 'openapi.key')], { cwd: work, windowsHide: true, stdio: ['ignore', logFile, logFile], env: { ...process.env, DB_NAME: schema, DB_USERNAME: user, DB_PASSWORD: password, DB_HOST: '127.0.0.1', DB_PORT: '3306', SMA_APP_MASTER_KEY: '', SPRING_DATASOURCE_URL: `jdbc:mysql://127.0.0.1:3306/${schema}?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true`, SPRING_DATASOURCE_USERNAME: user, SPRING_DATASOURCE_PASSWORD: password, SMA_ADMIN_PASSWORD: adminPassword } })
  const deadline = Date.now() + 180000
  while (true) {
    if (backend.exitCode !== null) throw new Error('Isolated backend exited. See ' + path.relative(root, path.join(work, 'backend.log')))
    try { const response = await fetch('http://127.0.0.1:8081/actuator/health', { signal: AbortSignal.timeout(1500) }); if (response.ok && (await response.json()).status === 'UP') break } catch { /* Poll only this known live process. */ }
    if (Date.now() > deadline) throw new Error('Isolated backend readiness timed out; see its log. No other backend was restarted.')
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  sql(`UPDATE \`${schema}\`.config_parameter SET param_value='false' WHERE param_key='login.captchaEnabled';`, user)
  console.log('Isolated backend ready on 8081; database ' + schema + '. Existing 8080/5173 services are untouched.')
  const code = await new Promise((resolve, reject) => {
    const child = spawn(process.execPath, ['node_modules/@playwright/test/cli.js', 'test', '--config=playwright.real.config.ts'], { cwd: frontend, windowsHide: true, stdio: 'inherit', env: { ...process.env, SMA_NEXT_BACKEND_URL: 'http://127.0.0.1:8081', SMA_E2E_ADMIN_PASSWORD: adminPassword } })
    child.once('error', reject); child.once('exit', code => resolve(code))
  })
  if (code !== 0) throw new Error('Real backend browser checks failed; inspect test-results-real/.')
  passed = true
} finally {
  if (backend && backend.exitCode === null) { backend.kill(); await new Promise(resolve => { backend.once('exit', resolve); setTimeout(resolve, 5000) }) }
  fs.closeSync(logFile)
  if (created && /^sma_next_e2e_\d+$/.test(schema) && /^sma_next_\d+$/.test(user)) sql(`DROP DATABASE IF EXISTS \`${schema}\`; DROP USER IF EXISTS '${user}'@'127.0.0.1';`)
  fs.writeFileSync(path.join(work, 'result.json'), JSON.stringify({ schema, passed, finishedAt: new Date().toISOString(), credentialsPersisted: false }, null, 2) + '\n')
}
console.log('Real E2E passed; only the task-owned backend, schema and database user were cleaned up.')
