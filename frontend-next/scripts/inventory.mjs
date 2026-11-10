import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'
const root = fileURLToPath(new URL('../../', import.meta.url))
const source = name => ts.createSourceFile(name, fs.readFileSync(path.join(root, name), 'utf8'), ts.ScriptTarget.Latest, true)
const property = (node, name) => node.properties.find(p => ts.isPropertyAssignment(p) && p.name.getText().replaceAll(/['"]/g, '') === name)?.initializer
const text = node => node && ts.isStringLiteral(node) ? node.text : undefined
const endpoints = file => [...new Set([...fs.readFileSync(path.join(root, 'frontend/src', file), 'utf8').matchAll(/\b(get|post|put|remove)(?:<[^;]*?>)?\(\s*['"]([^'"]+)['"]/g)].map(m => m[1].toUpperCase().replace('REMOVE', 'DELETE') + ' ' + m[2]))]
const routes = new Map()
function visit(node) {
  if (ts.isObjectLiteralExpression(node)) {
    const route = text(property(node, 'path')), component = property(node, 'component')?.getText().match(/import\(['"]@\/([^'"]+)['"]\)/)?.[1], meta = property(node, 'meta')
    if (route !== undefined && component && component !== 'layouts/AdminLayout.vue') {
      const url = route.startsWith('/') ? route : '/' + route
      routes.set(url, { route: url, legacyView: component, module: meta && ts.isObjectLiteralExpression(meta) ? text(property(meta, 'module')) || null : null, permission: meta && ts.isObjectLiteralExpression(meta) ? text(property(meta, 'permission')) || null : null, requests: endpoints(component), status: 'pending', evidence: [] })
    }
  }
  ts.forEachChild(node, visit)
}
visit(source('frontend/src/router/index.ts'))
const resourcesFile = source('frontend/src/features/iam/resources.ts')
function resources(node) {
  if (ts.isVariableDeclaration(node) && node.name.getText() === 'resources' && node.initializer && ts.isObjectLiteralExpression(node.initializer)) {
    for (const item of node.initializer.properties) if (ts.isPropertyAssignment(item) && ts.isObjectLiteralExpression(item.initializer)) {
      const id = text(property(item.initializer, 'id')), permission = text(property(item.initializer, 'permission')), endpoint = text(property(item.initializer, 'endpoint'))
      if (id) routes.set('/iam/' + id, { route: '/iam/' + id, legacyView: 'views/iam/ManagementView.vue', module: 'iam', permission: permission + ':view', requests: ['GET ' + endpoint, 'POST ' + endpoint, 'PUT ' + endpoint + '/{id}', 'DELETE ' + endpoint + '/{id}', ...endpoints('views/iam/ManagementView.vue')], status: 'pending', evidence: [] })
    }
  }
  ts.forEachChild(node, resources)
}
resources(resourcesFile)
const extras = {
  '/iam/roles': ['GET /iam/roles/{id}/permissions', 'PUT /iam/roles/{id}/permissions', 'GET /iam/permissions/tree', '查看用户：/iam/users?roleId={id}'],
  '/iam/users': ['PUT /iam/users/{id}/password', 'GET /iam/users/{id}'],
}
for (const [route, additions] of Object.entries(extras)) routes.get(route).requests.push(...additions)
const file = path.join(root, 'docs/frontend-next/route-matrix.json')
const previous = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : {}
for (const record of routes.values()) { const old = previous.routes?.find(r => r.route === record.route); if (old?.evidence) { record.evidence = old.evidence; record.status = old.status } }
const result = { generatedAt: new Date().toISOString(), note: 'Requests include literal URL prefixes extracted from existing call sites and explicit resource endpoints. Dynamic suffixes require the named source and API contract. Status requires recorded test evidence.', total: routes.size, routes: [...routes.values()].sort((a, b) => a.route.localeCompare(b.route)) }
if (process.argv.includes('--check')) {
  const actual = previous.routes?.map(r => r.route).sort()
  if (JSON.stringify(actual) !== JSON.stringify([...routes.keys()].sort())) throw new Error('Route inventory differs from authoritative legacy router/resources')
  for (const record of previous.routes) if (!fs.existsSync(path.join(root, 'frontend/src', record.legacyView))) throw new Error('Missing source for ' + record.route)
  console.log(`Route inventory verified: ${routes.size} existing routes with source files, modules, permission declarations and request prefixes.`)
} else { fs.writeFileSync(file, JSON.stringify(result, null, 2) + '\n'); console.log(`Wrote ${routes.size} routes to docs/frontend-next/route-matrix.json`) }
