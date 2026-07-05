import { http } from './http'

export function login(data) {
  return http.post('/auth/login', data)
}

export function logout() {
  return http.post('/auth/logout')
}

export function register(data) {
  return http.post('/auth/register', data)
}

export function getCurrentUser() {
  return http.get('/auth/me')
}

export function updateCurrentUser(data) {
  return http.put('/auth/me', data)
}

export function listUsers(params = {}) {
  return http.get('/auth/users', { params })
}

export function listLeaders() {
  return http.get('/auth/leaders')
}

export function listSalesCandidates() {
  return http.get('/auth/sales')
}

export function createUser(data) {
  return http.post('/auth/users', data)
}

export function updateUser(employeeCode, data) {
  return http.put(`/auth/users/${employeeCode}`, data)
}

export function deleteUser(employeeCode) {
  return http.delete(`/auth/users/${employeeCode}`)
}
