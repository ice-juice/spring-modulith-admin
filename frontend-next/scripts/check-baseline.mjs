import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { fileURLToPath } from 'node:url'
const root = fileURLToPath(new URL('../../', import.meta.url))
const baseline = JSON.parse(fs.readFileSync(path.join(root, 'docs/frontend-next/baseline.json'), 'utf8'))
const failures = baseline.legacyFiles.filter(({ path: name, sha256 }) => {
  const file = path.join(root, name)
  return !fs.existsSync(file) || crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex') !== sha256
})
if (failures.length) { console.error('Legacy frontend changed since baseline:', failures.map(x => x.path)); process.exit(1) }
console.log(`Legacy frontend preserved: ${baseline.legacyFiles.length} file hashes match.`)
