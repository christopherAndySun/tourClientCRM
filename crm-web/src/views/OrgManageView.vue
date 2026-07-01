<template>
  <section>
    <div class="toolbar">
      <div class="page-title">组织架构</div>
      <el-button :loading="loading" @click="fetchUsers">刷新</el-button>
    </div>

    <section class="panel admin-panel">
      <div>
        <span>顶层管理员</span>
        <strong>{{ adminUsers.map((user) => user.name).join('、') || 'admin' }}</strong>
        <small>管理员负责维护运营、销售两个部门，以及各部门下的组长和组员关系。</small>
      </div>
      <el-tag type="success">公司顶层</el-tag>
    </section>

    <div class="department-grid">
      <section v-for="department in departments" :key="department.position" class="panel department-card">
        <div class="department-head">
          <div>
            <h2>{{ department.name }}部门</h2>
            <span>{{ department.leaders.length }} 个组长 · {{ department.members.length }} 个员工 · {{ department.ungrouped.length }} 个未分组</span>
          </div>
        </div>

        <div class="leader-list">
          <article v-for="leader in department.leaders" :key="leader.employeeCode" class="leader-card">
            <div class="leader-head">
              <div>
                <strong>{{ leader.name }}</strong>
                <span>{{ leader.employeeCode }} · 组长</span>
              </div>
              <button class="table-action" type="button" @click="openAdjust(leader)">调整</button>
            </div>
            <div class="member-list">
              <div v-for="member in membersOf(leader.employeeCode)" :key="member.employeeCode" class="member-row">
                <span>{{ member.name }}（{{ member.employeeCode }}）</span>
                <button class="table-action" type="button" @click="openAdjust(member)">调整</button>
              </div>
              <el-empty v-if="!membersOf(leader.employeeCode).length" description="暂无组员" />
            </div>
          </article>
        </div>

        <article class="ungrouped-card">
          <div class="leader-head">
            <div>
              <strong>未挂靠组长</strong>
              <span>员工换部门、组长变更释放后会先进入这里</span>
            </div>
          </div>
          <div class="member-list">
            <div v-for="member in department.ungrouped" :key="member.employeeCode" class="member-row">
              <span>{{ member.name }}（{{ member.employeeCode }}）</span>
              <button class="table-action" type="button" @click="openAdjust(member)">调整</button>
            </div>
            <el-empty v-if="!department.ungrouped.length" description="暂无未分组员工" />
          </div>
        </article>
      </section>
    </div>

    <el-dialog v-model="adjustVisible" title="调整组织关系" width="min(560px, 92vw)" destroy-on-close>
      <el-alert
        class="adjust-tip"
        title="员工更换部门会清空原直属组长；组长降为员工或更换部门后，原组员会释放到未分组。"
        type="warning"
        :closable="false"
        show-icon
      />

      <el-form v-if="currentUser" label-position="top">
        <el-form-item label="员工">
          <el-input :model-value="`${currentUser.name}（${currentUser.employeeCode}）`" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="adjustForm.role" class="full-field" :disabled="currentUser.employeeCode === 'ADMIN'" @change="handleRoleChange">
            <el-option label="员工" value="EMPLOYEE" />
            <el-option label="组长" value="LEADER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门/岗位">
          <el-select v-model="adjustForm.position" class="full-field" @change="handlePositionChange">
            <el-option label="运营" value="OPERATION" />
            <el-option label="销售" value="SALES" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="adjustForm.role === 'EMPLOYEE'" label="直属组长">
          <el-select v-model="adjustForm.leaderEmployeeCode" class="full-field" clearable placeholder="选择同部门组长">
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
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAdjust">保存调整</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, updateUser } from '../api/auth'

const users = ref([])
const loading = ref(false)
const saving = ref(false)
const adjustVisible = ref(false)
const currentUser = ref(null)
const adjustForm = reactive({
  role: 'EMPLOYEE',
  position: 'OPERATION',
  leaderEmployeeCode: ''
})

const adminUsers = computed(() => users.value.filter((user) => user.role === 'ADMIN'))
const orgUsers = computed(() => users.value.filter((user) => user.role !== 'ADMIN'))
const departments = computed(() => ['OPERATION', 'SALES'].map((position) => {
  const departmentUsers = orgUsers.value.filter((user) => user.position === position)
  const leaders = departmentUsers.filter((user) => user.role === 'LEADER')
  const members = departmentUsers.filter((user) => user.role === 'EMPLOYEE')
  return {
    position,
    name: positionText(position),
    leaders,
    members,
    ungrouped: members.filter((user) => !user.leaderEmployeeCode)
  }
}))
const leaderOptions = computed(() => orgUsers.value.filter((user) => {
  return user.role === 'LEADER'
    && user.position === adjustForm.position
    && user.employeeCode !== currentUser.value?.employeeCode
}))

async function fetchUsers() {
  loading.value = true
  try {
    const res = await listUsers()
    users.value = res.data || []
  } catch (error) {
    await showError(error.message || '组织架构加载失败')
  } finally {
    loading.value = false
  }
}

function membersOf(code) {
  return orgUsers.value.filter((user) => user.leaderEmployeeCode === code)
}

function openAdjust(user) {
  currentUser.value = user
  adjustForm.role = user.role
  adjustForm.position = user.position
  adjustForm.leaderEmployeeCode = user.leaderEmployeeCode || ''
  adjustVisible.value = true
}

function handleRoleChange() {
  if (adjustForm.role !== 'EMPLOYEE') {
    adjustForm.leaderEmployeeCode = ''
  }
}

function handlePositionChange() {
  adjustForm.leaderEmployeeCode = ''
}

async function submitAdjust() {
  if (!currentUser.value) return

  try {
    const warnings = []
    if (currentUser.value.position !== adjustForm.position) {
      warnings.push('该员工更换部门后，原直属组长会被清空。')
    }
    if (currentUser.value.role === 'LEADER' && (adjustForm.role !== 'LEADER' || currentUser.value.position !== adjustForm.position)) {
      warnings.push('该组长原有组员会被释放到未分组，需要管理员重新分配。')
    }
    if (warnings.length) {
      await ElMessageBox.confirm(warnings.join(''), '组织调整确认', {
        confirmButtonText: '确认调整',
        cancelButtonText: '取消',
        type: 'warning'
      })
    }
    saving.value = true
    await updateUser(currentUser.value.employeeCode, {
      name: currentUser.value.name,
      password: '',
      role: adjustForm.role,
      position: adjustForm.position,
      leaderEmployeeCode: adjustForm.role === 'EMPLOYEE' ? adjustForm.leaderEmployeeCode : null,
      menuPermissions: currentUser.value.menuPermissions || []
    })
    ElMessage.success('组织关系已调整')
    adjustVisible.value = false
    await fetchUsers()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      await showError(error.message || '组织关系调整失败')
    }
  } finally {
    saving.value = false
  }
}

function positionText(position) {
  return { OPERATION: '运营', SALES: '销售' }[position] || position
}

function showError(message) {
  return ElMessageBox.alert(message, '提示', { confirmButtonText: '我知道了', type: 'warning' })
}

onMounted(fetchUsers)
</script>

<style scoped>
.admin-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.admin-panel span,
.admin-panel strong,
.admin-panel small {
  display: block;
}

.admin-panel span,
.admin-panel small {
  color: var(--text-muted);
}

.admin-panel strong {
  margin: 6px 0;
  color: var(--text-strong);
  font-size: 24px;
}

.department-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.department-card {
  display: grid;
  gap: 14px;
}

.department-head h2 {
  margin: 0 0 6px;
  color: var(--text-strong);
}

.department-head span,
.leader-head span {
  color: var(--text-muted);
}

.leader-list,
.member-list {
  display: grid;
  gap: 10px;
}

.leader-card,
.ungrouped-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border-radius: 18px;
  background: rgba(178, 174, 250, 0.12);
}

.ungrouped-card {
  background: rgba(255, 255, 255, 0.72);
  border: 1px dashed rgba(75, 75, 254, 0.22);
}

.leader-head,
.member-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.leader-head strong,
.leader-head span {
  display: block;
}

.member-row {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.78);
}

.full-field {
  width: 100%;
}

.adjust-tip {
  margin-bottom: 14px;
}

@media (max-width: 980px) {
  .department-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .admin-panel,
  .leader-head,
  .member-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .member-row {
    width: 100%;
  }
}
</style>
