import { http } from './http'

export function getSystemSettings() {
  return http.get('/settings')
}

export function saveSystemSettings(data) {
  return http.put('/settings', data)
}
