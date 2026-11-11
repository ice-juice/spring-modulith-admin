import { expect, it } from 'vitest'
import { safeBrowserDestination, safeLocalPath, safeHttpLink } from '../web-addresses'

it('rejects executable schemes, external relative destinations and parser ambiguities', () => {
  for (const url of ['javascript:alert(1)', 'data:text/html,hi', '//evil.test', '/\\evil.test', '/%0aevil', 'https://trusted@evil.test', 'https://trusted\\@evil.test', '/a/../b', '/a/%2e%2e/b']) {
    expect(safeBrowserDestination(url), url).toBeUndefined()
  }
  expect(safeLocalPath('/iam/users?page=2')).toBe('/iam/users?page=2')
  expect(safeHttpLink('https://example.test/help?q=hello%20world')).toContain('https://example.test')
})
// Rich HTML tests run in e2e-security/browser-security.spec.ts: DOMPurify explicitly
// does not support happy-dom as a secure HTML parser (see its bundled README).
