import { defineStore } from 'pinia'
import { getCurrentUser, login, updateCurrentUser } from '../api/auth'
import { clearSession, getStoredUser, getToken, setSessionStorage, updateStoredUser } from '../utils/session'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: getStoredUser()
  }),
  actions: {
    async loginWithPassword(form) {
      const res = assertSuccess(await login(form))
      this.setSession(res.data)
    },
    async fetchCurrentUser() {
      const res = assertSuccess(await getCurrentUser())
      this.user = res.data
      updateStoredUser(res.data)
    },
    async updateProfile(form) {
      const res = assertSuccess(await updateCurrentUser(form))
      this.user = res.data
      updateStoredUser(res.data)
    },
    setSession(data) {
      this.token = data.token
      this.user = {
        name: data.name,
        employeeCode: data.employeeCode,
        role: data.role,
        position: data.position,
        leaderEmployeeCode: data.leaderEmployeeCode,
        orgType: data.orgType,
        branchId: data.branchId,
        branchName: data.branchName,
        menuPermissions: data.menuPermissions || []
      }
      setSessionStorage(this.token, this.user, data.expiresAt)
    },
    logout() {
      this.token = ''
      this.user = null
      clearSession()
    }
  }
})

function assertSuccess(response) {
  if (response?.success === false) {
    throw new Error(response.message || '操作失败')
  }
  return response
}
