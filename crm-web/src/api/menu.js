import { http } from './http'
import { FALLBACK_MENUS } from '../composables/menuConfig'

export function listMenus() {
  return http.get('/menus').catch(() => ({ success: true, data: FALLBACK_MENUS }))
}

export function saveMenus(data) {
  return http.put('/menus', data)
}
