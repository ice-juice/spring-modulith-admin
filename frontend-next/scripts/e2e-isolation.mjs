import path from 'node:path'

export function isolatedScope(suite, suffix, runtimeRoot) {
  if (!['next', 'security'].includes(suite) || !/^\d{13}$/.test(suffix)) throw new Error('Invalid isolated E2E scope')
  return Object.freeze({ suite, schema: `sma_${suite}_e2e_${suffix}`, user: `sma_${suite}_${suffix}`,
    work: path.resolve(runtimeRoot, suite === 'next' ? suffix : `${suite}-${suffix}`) })
}
export function ownedChildPath(parent, child) {
  const root = path.resolve(parent), target = path.resolve(child)
  if (!target.startsWith(root + path.sep)) throw new Error('Refusing cleanup outside this run directory')
  return target
}
export function isolatedPeerPort(suite, primary, frontend, value) {
  if (!value) return undefined
  const port = Number(value)
  if (suite !== 'security' || !Number.isInteger(port) || port < 1024 || port > 65535
    || [primary, frontend, 8080, 5173, 5174].includes(port)) throw new Error('Peer requires security scope and a distinct isolated port')
  return port
}
export async function stopOwnedChildren(children, timeout = 15000) {
  const results = await Promise.allSettled(children.map(child => stopOwnedChild(child, timeout)))
  if (results.some(result => result.status === 'rejected')) throw new Error('Some owned processes are still live; refusing resource cleanup')
}
export function stopOwnedChild(child, timeout = 15000) {
  if (!child || child.exitCode !== null || child.signalCode !== null) return Promise.resolve()
  return new Promise((resolve, reject) => {
    let timer
    const cleanup = () => { clearTimeout(timer); child.removeListener('exit', exited); child.removeListener('error', failed) }
    const exited = () => { cleanup(); resolve() }
    const failed = () => { cleanup(); reject(new Error('Owned process could not be stopped; refusing resource cleanup')) }
    child.once('exit', exited); child.once('error', failed)
    timer = setTimeout(failed, timeout)
    try { child.kill() } catch { failed() }
  })
}
