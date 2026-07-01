import { http } from './http'

export function listDeals(params = {}) {
  return http.get('/deals', { params })
}

export function getDeal(dealCode) {
  return http.get(`/deals/${dealCode}`)
}

export function createDeal(data) {
  return http.post('/deals', data)
}

export function updateDeal(dealCode, data) {
  return http.put(`/deals/${dealCode}`, data)
}

export function cancelDeal(dealCode, data = {}) {
  return http.delete(`/deals/${dealCode}`, { data })
}

export function exportDeals(params = {}) {
  return http.get('/deals/export', {
    params,
    responseType: 'blob',
    transformResponse: [(data) => data]
  })
}
