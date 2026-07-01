import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('crm_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    if (response.data?.success === false) {
      return Promise.reject(new Error(response.data.message || '操作失败'))
    }
    return response.data
  },
  (error) => Promise.reject(normalizeHttpError(error))
)

function normalizeHttpError(error) {
  if (error?.response?.data?.message) {
    return new Error(error.response.data.message)
  }
  if (error?.response?.status) {
    return new Error(`请求失败，状态码：${error.response.status}`)
  }
  if (error?.code === 'ECONNABORTED') {
    return new Error('请求超时，请稍后重试')
  }
  return new Error(error?.message || '网络异常，请稍后重试')
}
