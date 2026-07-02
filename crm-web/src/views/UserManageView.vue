<template>
  <section>
    <div class="toolbar">
      <div class="page-title">账号管理</div>
      <el-button type="primary" @click="openCreateDialog">新增员工账号</el-button>
    </div>

    <el-alert
      class="manage-tip"
      title="账号基础信息和菜单权限分开维护。新增账号会按岗位默认勾选菜单，后续可在列表中单独配置。"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="panel desktop-table">
      <el-table :data="users" width="100%" v-loading="loading">
        <el-table-column prop="name" label="姓名" min-width="120" />
        <el-table-column prop="employeeCode" label="员工编号" min-width="120" />
        <el-table-column label="角色" min-width="110">
          <template #default="{ row }">{{ roleText(row.role) }}</template>
        </el-table-column>
        <el-table-column label="岗位" min-width="110">
          <template #default="{ row }">{{ positionText(row.position) }}</template>
        </el-table-column>
        <el-table-column label="直属组长" min-width="120">
          <template #default="{ row }">{{ row.leaderEmployeeCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <TextActions>
              <button class="table-action" type="button" @click="openEditDialog(row)">编辑</button>
              <button class="table-action" type="button" @click="openPermissionDialog(row)">菜单权限</button>
              <button class="table-action danger" type="button" :disabled="row.role === 'ADMIN'" @click="removeUser(row)">删除</button>
            </TextActions>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" :total="total" />
    </div>

    <div class="mobile-list" v-loading="loading">
      <article v-for="user in users" :key="user.employeeCode" class="user-card">
        <div>
          <strong>{{ user.name }}</strong>
          <span>{{ user.employeeCode }}</span>
        </div>
        <small>{{ roleText(user.role) }} · {{ positionText(user.position) }} · 组长：{{ user.leaderEmployeeCode || '-' }}</small>
        <TextActions class="card-actions">
          <button class="table-action" type="button" @click="openEditDialog(user)">编辑</button>
          <button class="table-action" type="button" @click="openPermissionDialog(user)">菜单权限</button>
          <button class="table-action danger" type="button" :disabled="user.role === 'ADMIN'" @click="removeUser(user)">删除</button>
        </TextActions>
      </article>
      <el-empty v-if="!loading && users.length === 0" description="暂无账号" />
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" compact :total="total" />
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑员工账号' : '新增员工账号'" width="min(560px, 92vw)" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="可填写中文、英文或数字" />
        </el-form-item>
        <el-form-item label="员工编号" prop="employeeCode">
          <el-input
            v-model="form.employeeCode"
            :disabled="isEdit"
            placeholder="2-5 位大写字母，例如 XB"
            @input="form.employeeCode = form.employeeCode.toUpperCase()"
          />
        </el-form-item>
        <el-form-item :label="isEdit ? '新密码' : '初始密码'" prop="password">
          <el-input v-model="form.password" type="password" show-password :placeholder="isEdit ? '不修改密码时留空' : '至少 6 位'" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" class="full-field" :disabled="form.employeeCode === 'ADMIN'" @change="handleRoleChange">
            <el-option label="员工" value="EMPLOYEE" />
            <el-option label="组长" value="LEADER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位" prop="position">
          <el-select v-model="form.position" class="full-field" @change="applyDefaultMenus">
            <el-option label="运营" value="OPERATION" />
            <el-option label="销售" value="SALES" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === 'EMPLOYEE'" label="直属组长">
          <el-select v-model="form.leaderEmployeeCode" class="full-field" clearable placeholder="可选择直属组长">
            <el-option
              v-for="leader in leaderOptions"
              :key="leader.employeeCode"
              :label="`${leader.name}（${leader.employeeCode}）`"
              :value="leader.employeeCode"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">{{ isEdit ? '保存修改' : '确认创建' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permissionDialogVisible" title="菜单权限配置" width="min(760px, 94vw)" destroy-on-close>
      <div v-if="permissionUser" class="permission-dialog">
        <div class="permission-user">
          <strong>{{ permissionUser.name }}</strong>
          <span>{{ permissionUser.employeeCode }} · {{ roleText(permissionUser.role) }} · {{ positionText(permissionUser.position) }}</span>
        </div>

        <el-alert
          v-if="permissionUser.role === 'ADMIN'"
          title="管理员默认拥有全部菜单权限，不能取消。"
          type="info"
          :closable="false"
          show-icon
        />

        <el-checkbox-group v-model="permissionForm.menuPermissions" :disabled="permissionUser.role === 'ADMIN'" class="permission-tree">
          <section v-for="group in menuGroups" :key="group.title" class="permission-group">
            <div class="permission-group-title">{{ group.title }}</div>
            <div class="permission-menu-list">
              <label v-for="menu in group.children" :key="menu.value" class="permission-menu-item">
                <el-checkbox :label="menu.value">
                  <span>{{ menu.label }}</span>
                  <small>{{ menu.desc }}</small>
                </el-checkbox>
              </label>
            </div>
          </section>
        </el-checkbox-group>
      </div>

      <template #footer>
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPermission" @click="submitPermissions">保存权限</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createUser, deleteUser, listLeaders, listUsers, updateUser } from '../api/auth'
import { listMenus } from '../api/menu'
import AppPagination from '../components/AppPagination.vue'
import TextActions from '../components/TextActions.vue'
import { FALLBACK_MENUS, groupMenus, mergeMenus } from '../composables/menuConfig'

const menus = ref([...FALLBACK_MENUS])
const enabledMenus = computed(() => menus.value.filter((menu) => menu.enabled || menu.code === 'MENUS'))
const menuGroups = computed(() => groupMenus(enabledMenus.value).map((group) => ({
  ...group,
  children: group.children.map((menu) => ({
    value: menu.code,
    label: menu.name,
    desc: menu.description
  }))
})))
const menuOptions = computed(() => menuGroups.value.flatMap((group) => group.children))
const ALL_MENUS = computed(() => menuOptions.value.map((menu) => menu.value))
const DEFAULT_MENUS = {
  OPERATION: ['CLUES', 'CLUE_CREATE', 'ASSIGN', 'ASSIGN_LOGS', 'STATS', 'PERFORMANCE'],
  SALES: ['ASSIGN', 'ASSIGN_LOGS', 'DEALS', 'STATS', 'PERFORMANCE']
}

const users = ref([])
const leaders = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const savingPermission = ref(false)
const page = reactive({ current: 1, size: 10 })
const dialogVisible = ref(false)
const permissionDialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const permissionUser = ref(null)

const form = reactive({
  name: '',
  employeeCode: '',
  password: '',
  role: 'EMPLOYEE',
  position: 'OPERATION',
  leaderEmployeeCode: '',
  menuPermissions: []
})

const permissionForm = reactive({
  menuPermissions: []
})

const leaderOptions = computed(() => leaders.value.filter((leader) => leader.employeeCode !== form.employeeCode))

const rules = computed(() => ({
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  employeeCode: [
    { required: true, message: '请填写员工编号', trigger: 'blur' },
    { pattern: /^[A-Z]{2,5}$/, message: '员工编号必须是 2-5 位大写字母', trigger: 'blur' }
  ],
  password: isEdit.value
    ? [{ min: 6, message: '密码至少 6 位', trigger: 'blur' }]
    : [
        { required: true, message: '请填写初始密码', trigger: 'blur' },
        { min: 6, message: '密码至少 6 位', trigger: 'blur' }
      ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  position: [{ required: true, message: '请选择岗位', trigger: 'change' }]
}))

async function fetchUsers() {
  loading.value = true
  try {
    const [userRes, leaderRes] = await Promise.all([
      listUsers({ page: page.current, pageSize: page.size }),
      listLeaders()
    ])
    const pageData = userRes.data || {}
    users.value = pageData.records || []
    total.value = Number(pageData.total || 0)
    leaders.value = leaderRes.data || []
    await fetchMenus()
  } catch (error) {
    await showError(error.message || '账号列表加载失败')
  } finally {
    loading.value = false
  }
}

async function fetchMenus() {
  try {
    const menuRes = await listMenus()
    menus.value = mergeMenus(menuRes.data || FALLBACK_MENUS)
  } catch (error) {
    menus.value = mergeMenus(FALLBACK_MENUS)
  }
}

function openCreateDialog() {
  isEdit.value = false
  Object.assign(form, {
    name: '',
    employeeCode: '',
    password: '',
    role: 'EMPLOYEE',
    position: 'OPERATION',
    leaderEmployeeCode: '',
    menuPermissions: [...DEFAULT_MENUS.OPERATION]
  })
  dialogVisible.value = true
}

function openEditDialog(user) {
  isEdit.value = true
  Object.assign(form, {
    name: user.name,
    employeeCode: user.employeeCode,
    password: '',
    role: user.role,
    position: user.position,
    leaderEmployeeCode: user.leaderEmployeeCode || '',
    menuPermissions: normalizeUserMenus(user)
  })
  dialogVisible.value = true
}

function openPermissionDialog(user) {
  permissionUser.value = user
  permissionForm.menuPermissions = normalizeUserMenus(user)
  permissionDialogVisible.value = true
}

function handleRoleChange() {
  if (form.role === 'ADMIN') {
    form.menuPermissions = [...ALL_MENUS.value]
    form.leaderEmployeeCode = ''
    return
  }
  if (form.role !== 'EMPLOYEE') {
    form.leaderEmployeeCode = ''
  }
  if (!isEdit.value) {
    applyDefaultMenus()
  }
}

function applyDefaultMenus() {
  if (form.role === 'ADMIN') {
    form.menuPermissions = [...ALL_MENUS.value]
    return
  }
  if (!isEdit.value) {
    form.menuPermissions = [...defaultMenusForPosition(form.position)]
  }
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      employeeCode: form.employeeCode.trim().toUpperCase(),
      password: form.password,
      role: form.role,
      position: form.position,
      leaderEmployeeCode: form.role === 'EMPLOYEE' ? form.leaderEmployeeCode : null,
      menuPermissions: form.role === 'ADMIN' ? [...ALL_MENUS.value] : form.menuPermissions
    }
    if (isEdit.value) {
      await updateUser(form.employeeCode, payload)
      ElMessage.success('账号已更新')
    } else {
      await createUser(payload)
      ElMessage.success('账号已创建')
    }
    dialogVisible.value = false
    await fetchUsers()
  } catch (error) {
    await showError(error.message || '保存失败，请检查填写信息')
  } finally {
    saving.value = false
  }
}

async function submitPermissions() {
  if (!permissionUser.value) return

  savingPermission.value = true
  try {
    const user = permissionUser.value
    await updateUser(user.employeeCode, {
      name: user.name,
      password: '',
      role: user.role,
      position: user.position,
      leaderEmployeeCode: user.leaderEmployeeCode,
      menuPermissions: user.role === 'ADMIN' ? [...ALL_MENUS.value] : permissionForm.menuPermissions
    })
    ElMessage.success('菜单权限已保存')
    permissionDialogVisible.value = false
    await fetchUsers()
  } catch (error) {
    await showError(error.message || '菜单权限保存失败')
  } finally {
    savingPermission.value = false
  }
}

async function removeUser(user) {
  try {
    await ElMessageBox.confirm(`确认删除账号 ${user.employeeCode} 吗？`, '删除确认', {
      confirmButtonText: '继续删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await ElMessageBox.confirm(`账号 ${user.employeeCode} 删除后不可恢复，请再次确认。`, '二次确认', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'error'
    })
    await deleteUser(user.employeeCode)
    ElMessage.success('账号已删除')
    await fetchUsers()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      await showError(error.message || '删除失败')
    }
  }
}

function normalizeUserMenus(user) {
  if (user.role === 'ADMIN') {
    return [...ALL_MENUS.value]
  }
  return user.menuPermissions?.length ? [...user.menuPermissions] : [...defaultMenusForPosition(user.position)]
}

function defaultMenusForPosition(position) {
  const enabledCodes = new Set(ALL_MENUS.value)
  return (DEFAULT_MENUS[position] || DEFAULT_MENUS.OPERATION).filter((code) => enabledCodes.has(code))
}

function showError(message) {
  return ElMessageBox.alert(message, '提示', {
    confirmButtonText: '我知道了',
    type: 'warning'
  })
}

function roleText(role) {
  return {
    ADMIN: '管理员',
    LEADER: '组长',
    EMPLOYEE: '员工'
  }[role] || role
}

function positionText(position) {
  return {
    OPERATION: '运营',
    SALES: '销售'
  }[position] || position
}

watch([() => page.current, () => page.size], fetchUsers)

onMounted(fetchUsers)
</script>

<style scoped>
.manage-tip {
  margin-bottom: 14px;
}

.full-field {
  width: 100%;
}

.permission-dialog {
  display: grid;
  gap: 14px;
}

.permission-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid rgba(178, 174, 250, 0.34);
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(246, 245, 255, 0.9));
}

.permission-user strong {
  color: var(--text-strong);
  font-size: 16px;
}

.permission-user span {
  color: var(--text-muted);
  font-size: 13px;
}

.permission-tree {
  display: grid;
  gap: 14px;
}

.permission-group {
  padding: 16px;
  border: 1px solid rgba(178, 174, 250, 0.34);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
}

.permission-group-title {
  margin-bottom: 12px;
  color: var(--text-strong);
  font-size: 16px;
  font-weight: 800;
}

.permission-menu-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.permission-menu-item {
  display: block;
  min-height: 68px;
  padding: 12px;
  border: 1px solid rgba(178, 174, 250, 0.32);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.78);
}

.permission-menu-item :deep(.el-checkbox) {
  align-items: flex-start;
  height: auto;
  margin-right: 0;
}

.permission-menu-item :deep(.el-checkbox__label) {
  display: grid;
  gap: 2px;
  line-height: 1.4;
}

.permission-menu-item small {
  color: var(--text-muted);
  font-size: 12px;
}

.user-card {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.user-card div,
.card-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.user-card small {
  color: var(--text-muted);
}

.text-actions {
  justify-content: flex-start !important;
  flex-wrap: wrap;
  margin-top: 4px;
}

@media (max-width: 760px) {
  .permission-user {
    align-items: flex-start;
    flex-direction: column;
    gap: 2px;
  }

  .permission-menu-list {
    grid-template-columns: 1fr;
  }
}
</style>
