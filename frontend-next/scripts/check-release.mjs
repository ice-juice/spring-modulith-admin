import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { gzipSync } from 'node:zlib'
import { fileURLToPath } from 'node:url'
const root = fileURLToPath(new URL('../', import.meta.url)), dist = path.join(root, 'dist')
const manifest = JSON.parse(fs.readFileSync(path.join(dist, '.vite/manifest.json'), 'utf8'))
function list(directory) { return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => entry.isDirectory() ? list(path.join(directory, entry.name)) : [path.join(directory, entry.name)]) }
const forbidden = /element-plus|@element-plus|lucide-vue-next|(?:\.\.\/)+frontend\//
for (const file of list(path.join(root, 'src'))) if (forbidden.test(fs.readFileSync(file, 'utf8'))) throw new Error('Legacy dependency/import found: ' + path.relative(root, file))
const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'))
if (Object.keys(pkg.dependencies).some(name => forbidden.test(name))) throw new Error('Legacy runtime dependency present')
if (Object.keys(manifest).some(key => key.includes('ComponentGallery'))) throw new Error('Development component gallery included in production build')
function graph(key, result = new Set()) {
  const chunk = manifest[key]
  if (!chunk || result.has(chunk.file)) return result
  result.add(chunk.file)
  for (const css of chunk.css || []) result.add(css)
  for (const imported of chunk.imports || []) graph(imported, result)
  return result
}
const entryKey = Object.keys(manifest).find(key => manifest[key].isEntry)
if (!entryKey) throw new Error('Missing production entry')
const initial = graph(entryKey), compressed = files => [...files].reduce((total, file) => total + gzipSync(fs.readFileSync(path.join(dist, file))).length, 0)
const budgets = { initialGzipBytes: 200 * 1024, perLazyRouteGzipBytes: 300 * 1024 }
const initialGzipBytes = compressed(initial)
if (initialGzipBytes > budgets.initialGzipBytes) throw new Error('Initial payload exceeds 200 KiB gzip budget')
const lazyRoutes = Object.keys(manifest).filter(key => /(?:Page|View|Layout)\.vue$/.test(key)).map(key => ({ source: key, gzipBytes: compressed(new Set([...graph(key)].filter(file => !initial.has(file)))) }))
for (const route of lazyRoutes) if (route.gzipBytes > budgets.perLazyRouteGzipBytes) throw new Error('Lazy route exceeds 300 KiB gzip budget: ' + route.source)
const files = list(dist).filter(file => !file.endsWith('release-manifest.json')).map(file => { const data = fs.readFileSync(file); return { file: path.relative(dist, file).replaceAll('\\', '/'), bytes: data.length, sha256: crypto.createHash('sha256').update(data).digest('hex') } })
const report = { builtAt: new Date().toISOString(), node: process.version, version: pkg.version, lockSha256: crypto.createHash('sha256').update(fs.readFileSync(path.join(root, 'package-lock.json'))).digest('hex'), budgets, initialGzipBytes, initialFiles: [...initial], lazyRoutes, files }
fs.writeFileSync(path.join(dist, 'release-manifest.json'), JSON.stringify(report, null, 2) + '\n')
console.log(`Release checks passed: no legacy dependencies/gallery; initial ${Math.round(initialGzipBytes / 1024)} KiB gzip, ${lazyRoutes.length} lazy modules within budget; ${files.length} files hashed.`)
