import { createRouter, createWebHistory } from 'vue-router'
import { listMenus } from '../api/menu'
import { FALLBACK_MENUS, mergeMenus } from '../composables/menuConfig'

const LoginView = () => import('../views/LoginView.vue')
const DashboardView = () => import('../views/DashboardView.vue')
const ClueListView = () => import('../views/ClueListView.vue')
const ClueCreateView = () => import('../views/ClueCreateView.vue')
const StatsView = () => import('../views/StatsView.vue')
const UserManageView = () => import('../views/UserManageView.vue')
const ProfileView = () => import('../views/ProfileView.vue')
const PerformanceView = () => import('../views/PerformanceView.vue')
const DealListView = () => import('../views/DealListView.vue')
const AssignView = () => import('../views/AssignView.vue')
const AssignLogView = () => import('../views/AssignLogView.vue')
const OperationLogView = () => import('../views/OperationLogView.vue')
const OrgManageView = () => import('../views/OrgManageView.vue')
const MenuManageView = () => import('../views/MenuManageView.vue')
const SystemSettingsView = () => import('../views/SystemSettingsView.vue')

const routes = [
  { path: '/login', component: LoginView },
  {
    path: '/',
    component: DashboardView,
    children: [
      { path: '', redirect: '/index' },
      { path: 'clues', component: ClueListView, meta: { menu: 'CLUES' } },
      { path: 'clues/create', component: ClueCreateView, meta: { menu: 'CLUE_CREATE' } },
      { path: 'clues/:customerCode', component: ClueCreateView, meta: { anyMenu: ['CLUES', 'ASSIGN', 'DEALS', 'PERFORMANCE', 'OPERATION_LOGS'] } },
      { path: 'assign', component: AssignView, meta: { menu: 'ASSIGN' } },
      { path: 'assign-logs', component: AssignLogView, meta: { menu: 'ASSIGN_LOGS' } },
      { path: 'index', component: StatsView, meta: { menu: 'STATS' } },
      { path: 'stats', redirect: '/index' },
      { path: 'performance', component: PerformanceView, meta: { menu: 'PERFORMANCE' } },
      { path: 'operation-logs', component: OperationLogView, meta: { menu: 'OPERATION_LOGS' } },
      { path: 'deals', component: DealListView, meta: { menu: 'DEALS' } },
      { path: 'users', component: UserManageView, meta: { menu: 'USERS', adminOnly: true } },
      { path: 'org', component: OrgManageView, meta: { menu: 'ORG', adminOnly: true } },
      { path: 'menus', component: MenuManageView, meta: { menu: 'MENUS', adminOnly: true } },
      { path: 'settings', component: SystemSettingsView, meta: { menu: 'SETTINGS', adminOnly: true } },
      { path: 'profile', component: ProfileView }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const token = localStorage.getItem('crm_token')
  if (to.path !== '/login' && !token) {
    return '/login'
  }
  if (to.path === '/login' && token) {
    return '/index'
  }
  const user = JSON.parse(localStorage.getItem('crm_user') || 'null')
  if (to.meta.adminOnly && user?.role !== 'ADMIN') {
    return firstAllowedPath(user)
  }
  if (to.meta.menu && to.meta.menu !== 'MENUS') {
    const menus = await ensureMenus()
    const menu = menus.find((item) => item.code === to.meta.menu)
    if (menu && !menu.enabled) {
      return firstAllowedPath(user)
    }
  }
  if (to.meta.menu && user?.role !== 'ADMIN' && to.meta.menu !== 'STATS' && !hasMenuPermission(user, to.meta.menu)) {
    return firstAllowedPath(user)
  }
  if (to.meta.anyMenu && user?.role !== 'ADMIN' && !to.meta.anyMenu.some((menu) => user?.menuPermissions?.includes(menu))) {
    return firstAllowedPath(user)
  }
  return true
})

function firstAllowedPath(user) {
  const menus = mergeMenus(JSON.parse(localStorage.getItem('crm_menus') || 'null') || FALLBACK_MENUS)
  const enabledCodes = new Set(menus.filter((menu) => menu.enabled || menu.code === 'MENUS').map((menu) => menu.code))
  const menuPathMap = [
    ['STATS', '/index'],
    ['CLUES', '/clues'],
    ['CLUE_CREATE', '/clues/create'],
    ['ASSIGN', '/assign'],
    ['ASSIGN_LOGS', '/assign-logs'],
    ['DEALS', '/deals'],
    ['PERFORMANCE', '/performance'],
    ['OPERATION_LOGS', '/operation-logs'],
    ['USERS', '/users'],
    ['ORG', '/org'],
    ['MENUS', '/menus'],
    ['SETTINGS', '/settings']
  ]
  if (user?.role === 'ADMIN') {
    return menuPathMap.find(([menu]) => enabledCodes.has(menu))?.[1] || '/menus'
  }
  return menuPathMap.find(([menu]) => enabledCodes.has(menu) && (menu === 'STATS' || hasMenuPermission(user, menu)))?.[1] || '/profile'
}

function hasMenuPermission(user, menu) {
  if (user?.menuPermissions?.includes(menu)) return true
  return menu === 'ASSIGN_LOGS' && user?.menuPermissions?.includes('ASSIGN')
}

async function ensureMenus() {
  const cached = JSON.parse(localStorage.getItem('crm_menus') || 'null')
  if (cached?.length) {
    const menus = mergeMenus(cached)
    localStorage.setItem('crm_menus', JSON.stringify(menus))
    return menus
  }
  try {
    const res = await listMenus()
    const menus = mergeMenus(res.data || FALLBACK_MENUS)
    localStorage.setItem('crm_menus', JSON.stringify(menus))
    return menus
  } catch (error) {
    return mergeMenus(FALLBACK_MENUS)
  }
}

export default router
