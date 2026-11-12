import { test } from 'node:test'
import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import path from 'node:path'
import { isolatedScope, ownedChildPath, stopOwnedChild, stopOwnedChildren, isolatedPeerPort } from './e2e-isolation.mjs'
import { securityTenantFixture } from './security-tenant-fixture.mjs'

test('tenant fixture rejects ordinary schemas and SQL injection before any database action', () => {
  for (const scope of [undefined, { suite: 'next', schema: 'sma_security_e2e_1794450000000' },
    { suite: 'security', schema: 'sma_admin' }, { suite: 'security', schema: 'sma_security_e2e_1794450000000`; DROP DATABASE prod;--' }]) {
    assert.throws(() => securityTenantFixture(scope), /generated security schema/)
  }
  const fixture = securityTenantFixture(isolatedScope('security', '1794450000000', 'runtime'))
  assert.equal(fixture.host, 'tenant-security.test')
})

test('security and ordinary runs have distinct bounded database, user and filesystem scopes', () => {
  const a = isolatedScope('next', '1794450000000', 'runtime'), b = isolatedScope('security', '1794450000000', 'runtime')
  for (const key of ['schema', 'user', 'work']) assert.notEqual(a[key], b[key])
  assert.ok(b.user.length <= 32)
  for (const suite of ['', 'other', '../security']) assert.throws(() => isolatedScope(suite, '1794450000000', 'runtime'))
  assert.throws(() => isolatedScope('security', '1;DROP DATABASE', 'runtime'))
})
test('cleanup accepts only a resolved child of the owned run, not its parent or a sibling', () => {
  const root = path.resolve('runtime', 'security-1794450000000')
  assert.equal(ownedChildPath(root, path.join(root, 'mysql-data')), path.join(root, 'mysql-data'))
  for (const target of [root, path.dirname(root), root + '-other', path.join(root, '..', 'other')]) assert.throws(() => ownedChildPath(root, target))
})
test('only confirmed child termination permits cleanup, and a timeout never counts as stopped', async () => {
  const child = new EventEmitter(); child.exitCode = null; child.signalCode = null
  let kills = 0; child.kill = () => { kills++; queueMicrotask(() => { child.signalCode = 'SIGTERM'; child.emit('exit') }) }
  await stopOwnedChild(child, 1000); await stopOwnedChild(child, 1000)
  assert.equal(kills, 1); assert.equal(child.listenerCount('exit'), 0)
  const live = new EventEmitter(); live.exitCode = null; live.signalCode = null; live.kill = () => true
  await assert.rejects(stopOwnedChild(live, 5), /refusing resource cleanup/)
  assert.equal(live.exitCode, null); assert.equal(live.listenerCount('exit'), 0)
})
test('optional peer cannot reuse a protected/occupied scope port or silently join an ordinary run', () => {
  assert.equal(isolatedPeerPort('next', 18261, 5206, undefined), undefined)
  assert.equal(isolatedPeerPort('security', 18261, 5206, '18262'), 18262)
  for (const value of ['18261', '5206', '8080', '5173', '5174', '0', '65536', 'NaN', '1234.5']) {
    assert.throws(() => isolatedPeerPort('security', 18261, 5206, value))
  }
  assert.throws(() => isolatedPeerPort('next', 18261, 5206, '18262'))
})
test('all children are offered termination even if one fails, without treating partial cleanup as success', async () => {
  const live = new EventEmitter(); live.exitCode = null; live.signalCode = null; live.kill = () => true
  const ending = new EventEmitter(); ending.exitCode = null; ending.signalCode = null
  ending.kill = () => { queueMicrotask(() => { ending.exitCode = 0; ending.emit('exit') }); return true }
  await assert.rejects(stopOwnedChildren([live, ending], 5), /refusing resource cleanup/)
  assert.equal(ending.exitCode, 0); assert.equal(live.exitCode, null)
})
