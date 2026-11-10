import { defineStore } from 'pinia'
import { ref } from 'vue'
import { get, post } from '@/api/http'
import { useAuth } from './auth'
export const useMessages = defineStore('messages', () => {
  const auth = useAuth(),
    summary = ref({ unreadNotices: '0', pendingTodos: '0', unreadMessages: '0' }),
    state = ref<'offline' | 'connecting' | 'connected'>('offline')
  let running = false,
    connecting = false,
    socket: WebSocket | undefined,
    retry: ReturnType<typeof setTimeout> | undefined,
    polling: ReturnType<typeof setInterval> | undefined,
    delay = 1000,
    epoch = 0,
    refreshSequence = 0
  async function refresh() {
    if (!auth.user || auth.user.locked) return
    try {
      const owner = auth.user.id, current = epoch, sequence = ++refreshSequence
      const result = await get<typeof summary.value>('/message/summary')
      if (current === epoch && owner === auth.user?.id && !auth.user?.locked && sequence === refreshSequence) summary.value = result
    } catch {
      /* Authentication and lock errors are handled centrally. */
    }
  }
  function schedule() {
    if (!running || auth.user?.locked || retry) return
    retry = setTimeout(() => {
      retry = undefined
      void connect()
    }, delay)
    delay = Math.min(30000, delay * 2)
  }
  async function connect() {
    if (!running || connecting || socket || !auth.user || auth.user.locked) return
    connecting = true
    const current = epoch, owner = auth.user.id
    state.value = 'connecting'
    try {
      const ticket = await post<{ ticket: string }>('/message/socket-ticket')
      if (!running || auth.user?.locked || current !== epoch || owner !== auth.user?.id) return
      const connection = new WebSocket(
        (location.protocol === 'https:' ? 'wss://' : 'ws://') +
          location.host +
          '/api/message/ws?ticket=' +
          encodeURIComponent(ticket.ticket),
      )
      socket = connection
      connection.onopen = () => {
        if (socket !== connection || current !== epoch || !running || auth.user?.id !== owner) { connection.close(); return }
        state.value = 'connected'
        delay = 1000
        void refresh()
      }
      connection.onmessage = async (event) => {
        if (socket !== connection || !running || current !== epoch || owner !== auth.user?.id) return
        let payload: { type?: string; reason?: string; id?: string }
        try {
          payload = JSON.parse(event.data)
        } catch {
          return
        }
        if (payload.type === 'session-ended') {
          if (payload.reason === 'TRANSPORT_ERROR') {
            connection.close()
            return
          }
          if (payload.reason === 'SESSION_INVALID') {
            try {
              await auth.load(false)
              if (auth.user?.locked) pause()
              else connection.close()
            } catch {
              stop()
            }
            return
          }
          stop()
          auth.reset()
          window.dispatchEvent(new Event('sma:unauthorized'))
          return
        }
        if (['notice', 'todo', 'private-message'].includes(payload.type || '')) {
          await refresh()
          if (socket !== connection || current !== epoch || owner !== auth.user?.id) return
          window.dispatchEvent(new CustomEvent('sma:message', { detail: payload }))
        }
      }
      connection.onclose = () => {
        if (socket !== connection || current !== epoch) return
        socket = undefined
        state.value = 'offline'
        schedule()
      }
      connection.onerror = () => connection.close()
    } catch {
      if (current === epoch) { state.value = 'offline'; schedule() }
    } finally {
      if (current === epoch) connecting = false
    }
  }
  function pause() {
    epoch++
    refreshSequence++
    connecting = false
    if (retry) clearTimeout(retry)
    retry = undefined
    if (socket) {
      const previous = socket
      socket = undefined
      previous.onclose = null
      previous.close()
    }
    state.value = 'offline'
  }
  function start() {
    if (running) return
    running = true
    delay = 1000
    void refresh()
    void connect()
    polling = setInterval(() => {
      void refresh()
    }, 30000)
  }
  function stop() {
    running = false
    if (polling) clearInterval(polling)
    polling = undefined
    pause()
    summary.value = { unreadNotices: '0', pendingTodos: '0', unreadMessages: '0' }
  }
  return { summary, state, refresh, connect, pause, start, stop }
})
