import { createHash, createHmac, randomBytes } from 'node:crypto'
import { fileURLToPath } from 'node:url'

const encode = (value) => encodeURIComponent(value).replace(/[!'()*]/g, character => '%' + character.charCodeAt(0).toString(16).toUpperCase())
export function canonicalQuery(url) {
  return [...url.searchParams].map(([key, value]) => [encode(key), encode(value)])
    .sort((a, b) => a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0)
    .map(([key, value]) => key + '=' + value).join('&')
}
export function sign(method, url, body, secret, timestamp, nonce) {
  const hash = createHash('sha256').update(body).digest('hex')
  const canonical = [method.toUpperCase(), url.pathname, canonicalQuery(url), timestamp, nonce, hash].join('\n')
  return createHmac('sha256', secret).update(canonical).digest('hex')
}
export class SmaClient {
  #token
  #secret
  constructor({baseUrl = 'http://127.0.0.1:8080', appId, appSecret, signatureRequired = true}) {
    this.baseUrl = new URL(baseUrl); this.appId = appId; this.#secret = appSecret; this.signatureRequired = signatureRequired
    if (!appId || !appSecret) throw new Error('AppId and AppSecret are required')
  }
  async authenticate() {
    const ticket = await this.request('/open/token', {method: 'POST', data: {appId: this.appId, appSecret: this.#secret}, anonymous: true})
    this.#token = ticket.token
    return ticket.expiresAt
  }
  async request(path, {method = 'GET', data, anonymous = false} = {}) {
    const url = new URL(path, this.baseUrl)
    if (url.origin !== this.baseUrl.origin || !url.pathname.startsWith('/open/')) throw new Error('The URL must stay within this application API')
    if (!anonymous && !this.#token) await this.authenticate()
    const body = data === undefined ? '' : JSON.stringify(data), headers = {'Accept-Language': 'en-US'}
    if (data !== undefined) headers['Content-Type'] = 'application/json'
    if (!anonymous) headers.Authorization = 'Bearer ' + this.#token
    if (this.signatureRequired) {
      const timestamp = Math.floor(Date.now() / 1000).toString(), nonce = randomBytes(18).toString('base64url')
      Object.assign(headers, {'X-App-Id': this.appId, 'X-Timestamp': timestamp, 'X-Nonce': nonce, 'X-Signature': sign(method, url, body, this.#secret, timestamp, nonce)})
    }
    const response = await fetch(url, {method, headers, body: body || undefined}), payload = await response.json()
    if (!response.ok || payload.code !== 0) throw new Error(payload.error + ': ' + payload.message)
    return payload.data
  }
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const client = new SmaClient({baseUrl: process.env.SMA_API_URL, appId: process.env.SMA_APP_ID, appSecret: process.env.SMA_APP_SECRET})
  console.log(JSON.stringify(await client.request('/open/me'), null, 2))
}
