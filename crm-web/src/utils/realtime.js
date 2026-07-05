import { isSessionActive } from './session'

let socket
let reconnectTimer
let reconnectAttempts = 0
const listeners = new Set()

export function subscribeRealtime(listener) {
  listeners.add(listener)
  ensureRealtimeConnected()
  return () => {
    listeners.delete(listener)
  }
}

export function ensureRealtimeConnected() {
  if (!isSessionActive() || typeof WebSocket === 'undefined') return
  if (socket && [WebSocket.OPEN, WebSocket.CONNECTING].includes(socket.readyState)) return

  clearTimeout(reconnectTimer)
  socket = new WebSocket(`${websocketBaseUrl()}/ws/realtime`)
  socket.addEventListener('open', () => {
    reconnectAttempts = 0
  })
  socket.addEventListener('message', (event) => {
    const data = parseEvent(event.data)
    if (!data || data.type === 'CONNECTED') return
    listeners.forEach((listener) => listener(data))
  })
  socket.addEventListener('close', scheduleReconnect)
  socket.addEventListener('error', () => {
    try {
      socket?.close()
    } catch (error) {
      // Ignore close races; reconnect is handled by the close event.
    }
  })
}

function scheduleReconnect() {
  if (!listeners.size || !isSessionActive()) return
  clearTimeout(reconnectTimer)
  const delay = Math.min(30000, 1000 * 2 ** reconnectAttempts)
  reconnectAttempts += 1
  reconnectTimer = setTimeout(ensureRealtimeConnected, delay)
}

function websocketBaseUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}`
}

function parseEvent(raw) {
  try {
    return JSON.parse(raw)
  } catch (error) {
    return null
  }
}
