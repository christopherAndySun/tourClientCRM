<template>
  <div class="app-shell" :class="{ 'menu-open': mobileMenuVisible }">
    <header class="mobile-topbar">
      <button class="mobile-menu-button" type="button" aria-label="打开菜单" @click="mobileMenuVisible = true">
        <span></span>
        <span></span>
        <span></span>
      </button>
      <router-link class="mobile-brand" to="/index">
        <img src="../assets/brand/logo-mark.svg" alt="旅游客户 CRM" />
        <span>旅游客户 CRM</span>
      </router-link>
      <div class="mobile-actions">
        <button
          v-if="showSearchAction"
          class="mobile-action-button"
          type="button"
          aria-label="搜索筛选"
          @click="toggleMobileSearch"
        >
          <span class="search-icon"></span>
        </button>
      </div>
    </header>

    <div v-if="mobileMenuVisible" class="mobile-mask" @click="mobileMenuVisible = false"></div>

    <aside class="side-nav">
      <div class="brand-title">
        <img src="../assets/brand/logo-mark.svg" alt="旅游客户 CRM" />
        <span>旅游客户 CRM</span>
      </div>

      <div v-for="group in visibleMenuGroups" :key="group.code" class="nav-group">
        <div class="nav-group-title">{{ group.title }}</div>
        <router-link
          v-for="menu in group.children"
          :key="menu.code"
          class="nav-link"
          :to="menu.path"
          @click="closeMobileMenu"
        >
          {{ menu.name }}
        </router-link>
      </div>

      <div class="mobile-account-panel">
        <router-link class="mobile-profile-link" to="/profile" @click="closeMobileMenu">
          <strong>{{ authStore.user?.name || '未登录' }}</strong>
          <span>{{ authStore.user?.employeeCode || '-' }}</span>
        </router-link>
        <button class="mobile-logout-link" type="button" @click="logout">退出登录</button>
      </div>
    </aside>

    <main class="content">
      <div class="user-bar">
        <router-link class="profile-link" to="/profile">
          <strong>{{ authStore.user?.name || '未登录' }}</strong>
          <span>{{ authStore.user?.employeeCode }}</span>
        </router-link>
        <button class="logout-link" type="button" @click="logout">退出登录</button>
      </div>
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listMenus } from '../api/menu'
import { FALLBACK_MENUS, groupMenus, mergeMenus } from '../composables/menuConfig'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const mobileMenuVisible = ref(false)
const menus = ref([...FALLBACK_MENUS])
const isAdmin = computed(() => authStore.user?.role === 'ADMIN')
const visibleMenuGroups = computed(() => {
  const visibleMenus = menus.value.filter((menu) => canView(menu))
  return groupMenus(visibleMenus)
})
const showSearchAction = computed(() => {
  return ['/clues', '/assign'].includes(route.path)
})

onMounted(() => {
  if (authStore.token && !authStore.user) {
    authStore.fetchCurrentUser()
  }
  fetchMenus()
})

function closeMobileMenu() {
  mobileMenuVisible.value = false
}

async function fetchMenus() {
  try {
    const res = await listMenus()
    menus.value = mergeMenus(res.data || FALLBACK_MENUS)
    localStorage.setItem('crm_menus', JSON.stringify(menus.value))
  } catch (error) {
    menus.value = mergeMenus(FALLBACK_MENUS)
  }
}

function canView(menu) {
  if (!menu.enabled && menu.code !== 'MENUS') return false
  return isAdmin.value || menu.code === 'STATS' || authStore.user?.menuPermissions?.includes(menu.code) || (menu.code === 'ASSIGN_LOGS' && authStore.user?.menuPermissions?.includes('ASSIGN'))
}

function logout() {
  authStore.logout()
  router.push('/login')
}

function toggleMobileSearch() {
  window.dispatchEvent(new CustomEvent('crm-toggle-mobile-search'))
}
</script>

<style scoped>
.mobile-topbar,
.mobile-mask {
  display: none;
}

.nav-group {
  margin-bottom: 22px;
}

.nav-group-title {
  margin: 0 12px 10px;
  color: rgba(255, 255, 255, 0.46);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 1px;
}

.user-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 42px;
  margin-bottom: 18px;
  color: var(--text-muted);
}

.profile-link {
  display: flex;
  align-items: center;
  gap: 8px;
  color: inherit;
  text-decoration: none;
}

.profile-link strong {
  color: var(--text-main);
}

.logout-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--brand);
  font: inherit;
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 4px;
  cursor: pointer;
}

.mobile-account-panel {
  display: none;
}

@media (max-width: 760px) {
  .mobile-topbar {
    position: sticky;
    top: 0;
    z-index: 28;
    display: grid;
    grid-template-columns: 48px minmax(0, 1fr) 48px;
    align-items: center;
    gap: 8px;
    min-height: calc(58px + env(safe-area-inset-top));
    padding: calc(8px + env(safe-area-inset-top)) 12px 8px;
    margin: 0 -12px 14px;
    border-bottom: 1px solid rgba(178, 174, 250, 0.28);
    background: rgba(248, 247, 255, 0.88);
    backdrop-filter: blur(18px);
    box-shadow: 0 10px 26px rgba(29, 25, 92, 0.08);
  }

  .mobile-menu-button {
    display: grid;
    gap: 4px;
    width: 44px;
    height: 44px;
    place-content: center;
    border: 1px solid rgba(255, 255, 255, 0.78);
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.9);
    box-shadow: var(--shadow-soft);
  }

  .mobile-menu-button span {
    display: block;
    width: 18px;
    height: 2px;
    border-radius: 999px;
    background: var(--brand-deep);
  }

  .mobile-brand {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    min-width: 0;
    color: var(--text-strong);
    font-size: 17px;
    font-weight: 900;
    text-decoration: none;
  }

  .mobile-brand img {
    width: 28px;
    height: 28px;
    border-radius: 10px;
  }

  .mobile-brand span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-actions {
    display: flex;
    justify-content: flex-end;
  }

  .mobile-action-button {
    width: 42px;
    height: 42px;
    border: 0;
    border-radius: 14px;
    background: linear-gradient(135deg, var(--violet), var(--brand));
    box-shadow: 0 10px 22px rgba(75, 75, 254, 0.24);
  }

  .search-icon {
    position: relative;
    display: inline-block;
    width: 17px;
    height: 17px;
    border: 3px solid #fff;
    border-radius: 999px;
  }

  .search-icon::after {
    content: "";
    position: absolute;
    right: -8px;
    bottom: -6px;
    width: 10px;
    height: 3px;
    border-radius: 999px;
    background: #fff;
    transform: rotate(45deg);
    transform-origin: center;
  }

  .mobile-mask {
    position: fixed;
    inset: 0;
    z-index: 29;
    display: block;
    background: rgba(20, 18, 47, 0.36);
    backdrop-filter: blur(4px);
  }

  .side-nav {
    z-index: 31;
    display: flex;
    flex-direction: column;
    transform: translateX(-112%);
    transition: transform 0.22s ease;
  }

  .menu-open .side-nav {
    transform: translateX(0);
  }

  .content {
    padding-top: 0;
  }

  .user-bar {
    display: none;
  }

  .mobile-account-panel {
    display: grid;
    gap: 12px;
    margin-top: auto;
    padding: 18px 12px calc(4px + env(safe-area-inset-bottom));
    border-top: 1px solid rgba(255, 255, 255, 0.14);
  }

  .mobile-profile-link {
    display: grid;
    gap: 4px;
    color: rgba(255, 255, 255, 0.9);
    text-decoration: none;
  }

  .mobile-profile-link strong {
    font-size: 16px;
  }

  .mobile-profile-link span {
    color: rgba(255, 255, 255, 0.58);
    font-size: 13px;
  }

  .mobile-logout-link {
    width: max-content;
    padding: 0;
    border: 0;
    background: transparent;
    color: #ffb4b4;
    font: inherit;
    font-weight: 800;
    text-decoration: underline;
    text-underline-offset: 4px;
    cursor: pointer;
  }
}
</style>
