import { defineStore } from 'pinia'
import { onScopeDispose, ref, watch } from 'vue'
import { get, post } from '@/api/http'
import { getToken } from '@/lib/token'
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
  const identity = () => JSON.stringify([getToken(), auth.user?.id, auth.user?.portal, auth.user?.locked, [...(auth.user?.permissions || [])].sort()])
  async function refresh() {
    if (!auth.user || auth.user.locked || !getToken()) return
    try {
      const owner = identity(), current = epoch, sequence = ++refreshSequence, token = getToken()!
      const result = await get<typeof summary.value>('/message/summary', undefined, { expectedIdentity: token })
      if (current === epoch && owner === identity() && sequence === refreshSequence) summary.value = result
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
    if (!running || connecting || socket || !auth.user || auth.user.locked || !getToken()) return
    connecting = true
    const current = epoch, owner = identity(), token = getToken()!
    const owns = () => running && current === epoch && owner === identity() && !auth.user?.locked
    state.value = 'connecting'
    try {
      const ticket = await post<{ ticket: string }>('/message/socket-ticket', undefined, { expectedIdentity: token })
      if (!owns()) return
      const connection = new WebSocket(
        (location.protocol === 'https:' ? 'wss://' : 'ws://') +
          location.host +
          '/api/message/ws?ticket=' +
          encodeURIComponent(ticket.ticket),
      )
      socket = connection
      connection.onopen = () => {
        if (socket !== connection || !owns()) { connection.close(); return }
        state.value = 'connected'
        delay = 1000
        void refresh()
      }
      connection.onmessage = async (event) => {
        if (socket !== connection || !owns()) return
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
              const accepted = await auth.load(false)
              if (!accepted || socket !== connection || !owns()) return
              if (auth.user?.locked) pause()
              else connection.close()
            } catch {
              if (socket === connection && owns()) stop()
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
          if (socket !== connection || !owns()) return
          window.dispatchEvent(new CustomEvent('sma:message', { detail: payload }))
        }
      }
      connection.onclose = () => {
        if (socket !== connection || !owns()) return
        socket = undefined
        state.value = 'offline'
        schedule()
      }
      connection.onerror = () => connection.close()
    } catch {
      if (owns()) { state.value = 'offline'; schedule() }
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
  let boundIdentity = identity()
  function identityChanged() {
    const next = identity()
    if (next === boundIdentity) return
    boundIdentity = next
    pause()
    summary.value = { unreadNotices: '0', pendingTodos: '0', unreadMessages: '0' }
    if (running && auth.user && !auth.user.locked && getToken()) { void refresh(); void connect() }
  }
  watch(identity, identityChanged, { flush: 'sync' })
  window.addEventListener('storage', identityChanged)
  onScopeDispose(() => { window.removeEventListener('storage', identityChanged); stop() })
  return { summary, state, refresh, connect, pause, start, stop }
})
