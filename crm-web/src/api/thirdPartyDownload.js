import { http } from './http'

export function listThirdPartyPending(params = {}) {
  return http.get('/third-party-downloads/pending', { params })
}

export function listThirdPartyDownloaded(params = {}) {
  return http.get('/third-party-downloads/downloaded', { params })
}

export function markThirdPartyDownloaded(customerCode) {
  return http.post(`/third-party-downloads/${customerCode}/mark-downloaded`)
}

export function restoreThirdPartyPending(customerCode) {
  return http.post(`/third-party-downloads/${customerCode}/restore-pending`)
}

export function recordThirdPartyDownloadFailure(customerCode, message) {
  return http.post(`/third-party-downloads/${customerCode}/record-failure`, { message })
}
