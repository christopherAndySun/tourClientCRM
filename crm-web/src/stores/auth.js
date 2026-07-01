import { defineStore } from 'pinia'
import { getCurrentUser, login, updateCurrentUser } from '../api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('crm_token') || '',
    user: JSON.parse(localStorage.getItem('crm_user') || 'null')
  }),
  actions: {
    async loginWithPassword(form) {
      const res = assertSuccess(await login(form))
      this.setSession(res.data)
    },
    async fetchCurrentUser() {
      const res = assertSuccess(await getCurrentUser())
      this.user = res.data
      localStorage.setItem('crm_user', JSON.stringify(res.data))
    },
    async updateProfile(form) {
      const res = assertSuccess(await updateCurrentUser(form))
      this.user = res.data
      localStorage.setItem('crm_user', JSON.stringify(res.data))
    },
    setSession(data) {
      this.token = data.token
      this.user = {
        name: data.name,
        employeeCode: data.employeeCode,
        role: data.role,
        position: data.position,
        leaderEmployeeCode: data.leaderEmployeeCode,
        menuPermissions: data.menuPermissions || []
      }
      localStorage.setItem('crm_token', this.token)
      localStorage.setItem('crm_user', JSON.stringify(this.user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('crm_token')
      localStorage.removeItem('crm_user')
    }
  }
})

function assertSuccess(response) {
  if (response?.success === false) {
    throw new Error(response.message || '操作失败')
  }
  return response
}
