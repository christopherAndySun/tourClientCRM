import { http } from './http'

export function listMenus() {
  return http.get('/menus')
}

export function saveMenus(data) {
  return http.put('/menus', data)
}
