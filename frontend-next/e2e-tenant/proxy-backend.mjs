import { createServer } from 'node:http'
import { createHash } from 'node:crypto'
const server = createServer((request, response) => {
  response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' })
  response.end(JSON.stringify({ host: request.headers.host, origin: request.headers.origin }))
})
server.on('upgrade', (request, socket) => {
  const key = request.headers['sec-websocket-key']
  if (typeof key !== 'string') { socket.destroy(); return }
  const accept = createHash('sha1').update(key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64')
  socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ' + accept + '\r\n\r\n')
  const data = Buffer.from(JSON.stringify({ host: request.headers.host, origin: request.headers.origin }))
  socket.write(Buffer.concat([Buffer.from([0x81, data.length]), data]))
  socket.on('data', () => socket.end())
  socket.on('error', () => socket.destroy())
})
server.listen(5199, '127.0.0.1')
