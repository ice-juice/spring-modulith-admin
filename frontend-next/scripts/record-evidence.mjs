import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { fileURLToPath } from 'node:url'
const root = fileURLToPath(new URL('../', import.meta.url)), kind = process.argv[2]
if (!['mock', 'real', 'visual', 'production'].includes(kind)) throw new Error('Specify mock, real, visual or production')
const source = path.join(root, kind === 'real' ? 'test-results-real/results.json' : kind === 'production' ? 'test-results-production/results.json' : 'test-results/results.json')
const report = JSON.parse(fs.readFileSync(source, 'utf8'))
if (report.stats.unexpected || report.stats.flaky || report.errors.length) throw new Error('Failed/flaky results cannot be recorded as accepted evidence')
const tests = [], measurements = []
function walk(suite) {
  for (const child of suite.suites || []) walk(child)
  for (const spec of suite.specs || []) for (const test of spec.tests) {
    const result = test.results.at(-1)
    if (result.status !== 'passed') throw new Error('A test did not pass: ' + spec.title)
    tests.push({ file: spec.file, title: spec.title, status: result.status, durationMillis: result.duration })
    for (const attachment of result.attachments || []) if (attachment.name.endsWith('-performance') && attachment.body) measurements.push({ name: attachment.name, ...JSON.parse(Buffer.from(attachment.body, 'base64').toString('utf8')) })
  }
}
for (const suite of report.suites) walk(suite)
const output = path.resolve(root, '../docs/frontend-next/test-evidence.' + kind + '.json')
const evidence = { recordedAt: new Date().toISOString(), kind, runner: 'Playwright / installed Google Chrome / Windows', startedAt: report.stats.startTime, durationMillis: report.stats.duration, passed: tests.length, failed: 0, lockSha256: crypto.createHash('sha256').update(fs.readFileSync(path.join(root, 'package-lock.json'))).digest('hex'), measurements, tests }
if (kind === 'real') {
  const runtimeRoot = path.join(root, '.e2e-runtime')
  const latest = fs.readdirSync(runtimeRoot).filter(name => /^\d+$/.test(name)).sort().reverse().find(name => fs.existsSync(path.join(runtimeRoot, name, 'result.json')) && JSON.parse(fs.readFileSync(path.join(runtimeRoot, name, 'result.json'), 'utf8')).passed)
  if (!latest) throw new Error('Missing successful isolated runtime record')
  const record = JSON.parse(fs.readFileSync(path.join(runtimeRoot, latest, 'result.json'), 'utf8'))
  if (Date.parse(record.finishedAt) < Date.parse(report.stats.startTime)) throw new Error('Runtime record predates these tests')
  evidence.runtime = { ...record, backendJarSha256: crypto.createHash('sha256').update(fs.readFileSync(path.join(runtimeRoot, latest, 'backend.jar'))).digest('hex') }
}
fs.writeFileSync(output, JSON.stringify(evidence, null, 2) + '\n')
console.log(`Recorded ${tests.length} passing ${kind} tests; no credentials or traces copied.`)
