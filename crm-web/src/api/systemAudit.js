import { http } from './http'

export function listSystemAuditLogs(params = {}) {
  return http.get('/system-audit', { params })
}
