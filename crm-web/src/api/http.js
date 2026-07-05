import axios from 'axios'
import { clearSession, getToken } from '../utils/session'

export const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    if (response.data?.success === false) {
      const message = response.data.message || '操作失败'
      if (message.includes('请先登录') || message.includes('登录已过期')) {
        clearSession()
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
      }
      return Promise.reject(new Error(message))
    }
    return response.data
  },
  (error) => Promise.reject(normalizeHttpError(error))
)

function normalizeHttpError(error) {
  if (error?.response?.status === 401) {
    clearSession()
    if (window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
  }
  if (error?.response?.data?.message) {
    return new Error(error.response.data.message)
  }
  if (error?.response?.status) {
    return new Error(`请求失败，状态码：${error.response.status}`)
  }
  if (error?.code === 'ECONNABORTED') {
    return new Error('请求超时，请检查网络后重试')
  }
  return new Error(error?.message || '网络异常，请稍后重试')
}
