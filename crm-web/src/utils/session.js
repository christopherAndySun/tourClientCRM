const TOKEN_KEY = 'crm_token'
const USER_KEY = 'crm_user'
const EXPIRES_KEY = 'crm_token_expires_at'
const DEFAULT_SESSION_MS = 24 * 60 * 60 * 1000

export function getToken() {
  if (isSessionExpired()) {
    clearSession()
    return ''
  }
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getStoredUser() {
  if (isSessionExpired()) {
    clearSession()
    return null
  }
  return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
}

export function setSessionStorage(token, user, expiresAt) {
  localStorage.setItem(TOKEN_KEY, token || '')
  localStorage.setItem(USER_KEY, JSON.stringify(user || null))
  localStorage.setItem(EXPIRES_KEY, resolveExpiresAt(expiresAt))
}

export function updateStoredUser(user) {
  localStorage.setItem(USER_KEY, JSON.stringify(user || null))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(EXPIRES_KEY)
}

export function isSessionExpired() {
  const expiresAt = localStorage.getItem(EXPIRES_KEY)
  if (!expiresAt) return false
  return Date.now() >= Number(expiresAt)
}

function resolveExpiresAt(expiresAt) {
  if (!expiresAt) return String(Date.now() + DEFAULT_SESSION_MS)
  const normalized = String(expiresAt).replace(' ', 'T')
  const parsed = Date.parse(normalized)
  return String(Number.isNaN(parsed) ? Date.now() + DEFAULT_SESSION_MS : parsed)
}
