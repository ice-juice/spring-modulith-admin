import {test} from 'node:test'
import assert from 'node:assert/strict'
import {canonicalQuery,sign,SmaClient} from './sma-client.mjs'

test('canonical query preserves repeats, UTF-8, spaces and RFC3986 punctuation', () => {
  const url = new URL('http://127.0.0.1/open/me?&&z=last&a=two+words&a=one&q=%E4%BD%A0%E5%A5%BD&star=*&')
  assert.equal(canonicalQuery(url), 'a=one&a=two%20words&q=%E4%BD%A0%E5%A5%BD&star=%2A&z=last')
  assert.equal(canonicalQuery(new URL('http://127.0.0.1/open/me?=&&x&')), '=&x=')
})
test('signatures cover query, body, path, timestamp and nonce', () => {
  const url = new URL('http://127.0.0.1/open/echo?a=1'), secret = 'fixed-test-vector', timestamp = '1700000000', nonce = '0123456789abcdef'
  const original = sign('POST', url, '{"value":1}', secret, timestamp, nonce)
  assert.equal(original.length, 64)
  assert.notEqual(original, sign('POST', url, '{"value":2}', secret, timestamp, nonce))
  assert.notEqual(original, sign('POST', new URL('http://127.0.0.1/open/echo?a=2'), '{"value":1}', secret, timestamp, nonce))
  assert.notEqual(original, sign('GET', url, '{"value":1}', secret, timestamp, nonce))
})
test('the client will not transfer credentials to another origin', async () => {
  const client = new SmaClient({appId:'test-only-id', appSecret:'test-only-secret'})
  await assert.rejects(client.request('https://example.test/open/token'), /must stay within/)
})
