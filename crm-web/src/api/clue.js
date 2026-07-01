import { http } from './http'

export function listClues(params = {}) {
  return http.get('/clues', { params })
}

export function getClue(customerCode) {
  return http.get(`/clues/${customerCode}`)
}

export function getClueHistory(customerCode) {
  return http.get(`/clues/${customerCode}/history`)
}

export function createClue(data) {
  return http.post('/clues', data, { timeout: 60000 })
}

export function updateClue(customerCode, data) {
  return http.put(`/clues/${customerCode}`, data, { timeout: 60000 })
}

export function assignClue(customerCode, data) {
  return http.put(`/clues/${customerCode}/assign`, data)
}

export function claimClue(customerCode) {
  return http.put(`/clues/${customerCode}/claim`)
}

export function releaseClue(customerCode, data = {}) {
  return http.put(`/clues/${customerCode}/release`, data)
}

export function listPublicSalesPool(params = {}) {
  return http.get('/clues/sales-pool/public', { params })
}

export function listMySalesPool(params = {}) {
  return http.get('/clues/sales-pool/mine', { params })
}

export function listAssignLogs(params = {}) {
  return http.get('/clues/assign-logs', { params })
}

export function listOperationLogs(params = {}) {
  return http.get('/clues/operation-logs', { params })
}

export function updateClueStatus(customerCode, data) {
  return http.put(`/clues/${customerCode}/status`, data)
}

export function deleteClue(customerCode) {
  return http.delete(`/clues/${customerCode}`)
}

export function exportClues(params = {}) {
  return http.get('/clues/export', {
    params,
    responseType: 'blob'
  })
}

export function getClueStats(params = {}) {
  return http.get('/clues/stats', { params })
}

export function getStatsDetail(params = {}) {
  return http.get('/clues/stats/detail', { params })
}

export function getPerformance(params = {}) {
  return http.get('/clues/performance', { params })
}

export function exportPerformance(params = {}) {
  return http.get('/clues/performance/export', {
    params,
    responseType: 'blob'
  })
}

export function getEmployeeClues(employeeCode, params = {}) {
  return http.get(`/clues/performance/${employeeCode}/clues`, { params })
}
