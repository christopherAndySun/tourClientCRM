<template>
  <section>
    <div class="toolbar">
      <div class="page-title">菜单管理</div>
      <el-button type="primary" :loading="saving" @click="submitMenus">保存菜单</el-button>
    </div>

    <el-alert
      class="menu-tip"
      title="菜单编码是系统内部权限标识，页面不展示。菜单管理会始终保留启用，避免误停后无法恢复。"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="menu-grid" v-loading="loading">
      <section v-for="group in menuGroups" :key="group.code" class="panel menu-card">
        <h2>{{ group.title }}</h2>
        <div class="menu-list">
          <article v-for="menu in group.children" :key="menu.code" class="menu-row" :class="{ disabled: !menu.enabled }">
            <div class="menu-main">
              <el-input v-model="menu.name" placeholder="菜单名称" />
              <el-input v-model="menu.description" placeholder="菜单说明" />
            </div>
            <div class="menu-controls">
              <el-input-number v-model="menu.sort" :min="1" :step="10" controls-position="right" />
              <el-switch
                v-model="menu.enabled"
                :disabled="menu.code === 'MENUS'"
                active-text="启用"
                inactive-text="停用"
                inline-prompt
              />
            </div>
          </article>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMenus, saveMenus } from '../api/menu'
import { FALLBACK_MENUS, groupMenus, mergeMenus } from '../composables/menuConfig'
import { showError } from '../utils/feedback'

const loading = ref(false)
const saving = ref(false)
const menus = ref([])
const menuGroups = computed(() => groupMenus(menus.value))

async function fetchMenus() {
  loading.value = true
  try {
    const res = await listMenus()
    menus.value = cloneMenus(mergeMenus(res.data || FALLBACK_MENUS))
  } catch (error) {
    menus.value = cloneMenus(mergeMenus(FALLBACK_MENUS))
  } finally {
    loading.value = false
  }
}

async function submitMenus() {
  const invalid = menus.value.find((menu) => !menu.name?.trim())
  if (invalid) {
    await showError('菜单名称不能为空')
    return
  }
  try {
    await ElMessageBox.confirm('确认保存菜单名称、排序和启停设置吗？停用后对应菜单会从左侧导航和权限配置中隐藏。', '保存确认', {
      confirmButtonText: '确认保存',
      cancelButtonText: '取消',
      type: 'warning'
    })
    saving.value = true
    const payload = menus.value.map((menu) => ({
      ...menu,
      name: menu.name.trim(),
      description: menu.description?.trim() || ''
    }))
    const res = await saveMenus(payload)
    menus.value = cloneMenus(res.data || payload)
    ElMessage.success('菜单已保存')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      await showError(error.message || '菜单保存失败')
    }
  } finally {
    saving.value = false
  }
}

function cloneMenus(source) {
  return source.map((item) => ({ ...item }))
}

onMounted(fetchMenus)
</script>

<style scoped>
.menu-tip {
  margin-bottom: 14px;
}

.menu-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.menu-card h2 {
  margin: 0 0 14px;
  color: var(--text-strong);
}

.menu-list {
  display: grid;
  gap: 10px;
}

.menu-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 210px;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 16px;
  background: rgba(178, 174, 250, 0.12);
}

.menu-row.disabled {
  opacity: 0.58;
}

.menu-main {
  display: grid;
  gap: 8px;
}

.menu-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 980px) {
  .menu-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .menu-row {
    grid-template-columns: 1fr;
  }

  .menu-controls {
    justify-content: space-between;
  }
}
</style>
