import { defineStore } from 'pinia'
import { getCurrentUser, login, logout as logoutRequest, updateCurrentUser } from '../api/auth'
import { clearSession, isSessionActive, setSessionMeta } from '../utils/session'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: isSessionActive() ? 'cookie-session' : '',
    user: null
  }),
  actions: {
    async loginWithPassword(form) {
      const res = assertSuccess(await login(form))
      this.setSession(res.data)
    },
    async fetchCurrentUser() {
      const res = assertSuccess(await getCurrentUser())
      this.user = res.data
      this.token = 'cookie-session'
    },
    async updateProfile(form) {
      const res = assertSuccess(await updateCurrentUser(form))
      this.user = res.data
    },
    setSession(data) {
      this.token = 'cookie-session'
      this.user = {
        name: data.name,
        employeeCode: data.employeeCode,
        role: data.role,
        position: data.position,
        leaderEmployeeCode: data.leaderEmployeeCode,
        orgType: data.orgType,
        branchId: data.branchId,
        branchName: data.branchName,
        mustChangePassword: Boolean(data.mustChangePassword),
        menuPermissions: data.menuPermissions || []
      }
      setSessionMeta(data.expiresAt)
    },
    async logout() {
      try {
        await logoutRequest()
      } catch (error) {
        // 本地退出不能被网络失败阻塞。
      }
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
