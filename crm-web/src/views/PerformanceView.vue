<template>
  <section>
    <div class="toolbar">
      <div class="page-title">{{ isEmployeeOnly ? '我的客资明细' : '员工绩效' }}</div>
    </div>

    <FilterPanel :collapsible="false">
      <el-date-picker
        v-model="dateRange"
        class="date-range"
        type="daterange"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
      />
      <template #actions>
        <el-button type="primary" :loading="loading || detailLoading" @click="refreshPage">查询</el-button>
        <el-button v-if="!isEmployeeOnly" :loading="exporting" @click="downloadExcel">导出 Excel</el-button>
      </template>
    </FilterPanel>

    <template v-if="!isEmployeeOnly">
    <div class="panel desktop-table">
      <el-table
        :data="pagedTreeRows"
        width="100%"
        row-key="employeeCode"
        v-loading="loading"
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column prop="employeeName" label="员工" min-width="120" />
        <el-table-column prop="employeeCode" label="员工编号" min-width="120" />
        <el-table-column label="角色" min-width="100">
          <template #default="{ row }">{{ roleText(row.role) }}</template>
        </el-table-column>
        <el-table-column label="岗位" min-width="100">
          <template #default="{ row }">{{ positionText(row.position) }}</template>
        </el-table-column>
        <el-table-column prop="leaderEmployeeCode" label="直属组长" min-width="110" />
        <el-table-column prop="totalCount" label="总客资" min-width="100" sortable />
        <el-table-column prop="todayCount" label="今日新增" min-width="100" sortable />
        <el-table-column prop="repeatDemandCount" label="老客新需求" min-width="120" sortable />
        <el-table-column prop="dealedCount" label="已交定金" min-width="100" sortable />
        <el-table-column prop="refundedCount" label="退单" min-width="90" sortable />
        <el-table-column prop="landedCount" label="已落地" min-width="100" sortable />
        <el-table-column prop="invalidCount" label="无效用户" min-width="100" sortable />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <button class="table-action" type="button" @click="openEmployeeClues(row)">查看数据</button>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" :total="treeRows.length" />
    </div>

    <div class="mobile-list">
      <article v-for="row in pagedMobileRows" :key="row.employeeCode" class="performance-card">
        <div class="card-head">
          <strong>{{ row.employeeName }}（{{ row.employeeCode }}）</strong>
          <el-tag>{{ roleText(row.role) }}</el-tag>
        </div>
        <small>{{ positionText(row.position) }} · 直属组长：{{ row.leaderEmployeeCode || '-' }}</small>
        <div class="metric-grid">
          <span>总客资：{{ row.totalCount }}</span>
          <span>今日：{{ row.todayCount }}</span>
          <span>已交定金：{{ row.dealedCount }}</span>
          <span>退单：{{ row.refundedCount || 0 }}</span>
          <span>已落地：{{ row.landedCount || 0 }}</span>
          <span>无效：{{ row.invalidCount }}</span>
        </div>
        <div class="card-actions">
          <button class="table-action" type="button" @click="openEmployeeClues(row)">查看数据</button>
        </div>
      </article>
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" compact :total="mobileRows.length" />
    </div>
    </template>

    <section v-else class="panel employee-detail-panel">
      <el-table class="desktop-table" :data="pagedEmployeeClues" width="100%" v-loading="detailLoading" @row-click="goDetail">
        <el-table-column prop="customerCode" label="客户编号" min-width="130" />
        <el-table-column label="来源平台" min-width="100">
          <template #default="{ row }">{{ sourcePlatformText(row.sourcePlatform) }}</template>
        </el-table-column>
        <el-table-column label="客户联系方式" min-width="150">
          <template #default="{ row }">{{ row.contactInfo || '待补充' }}</template>
        </el-table-column>
        <el-table-column prop="assignedSales" label="分配销售" min-width="110">
          <template #default="{ row }">{{ row.assignedSales || '-' }}</template>
        </el-table-column>
        <el-table-column label="当前状态" min-width="190">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
            <el-tag v-if="row.repeatDemand" class="status-extra" type="success">老客新需求</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="定金" min-width="100">
          <template #default="{ row }">{{ row.depositAmount || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" min-width="160" />
      </el-table>

      <div class="mobile-list detail-mobile-list">
        <article v-for="row in pagedEmployeeClues" :key="row.customerCode" class="clue-card" @click="goDetail(row)">
          <div class="card-head">
            <strong>{{ row.customerCode }}</strong>
            <StatusTag :status="row.status" />
          </div>
          <span>{{ row.contactInfo || '客户联系方式待补充' }}</span>
          <small>{{ sourcePlatformText(row.sourcePlatform) }} · 销售：{{ row.assignedSales || '未分配' }} · {{ row.createdAt }}</small>
          <div class="tag-wrap">
            <el-tag v-if="row.repeatDemand" type="success">老客新需求</el-tag>
            <el-tag v-if="row.depositAmount" type="warning">定金 {{ row.depositAmount }}</el-tag>
          </div>
        </article>
      </div>
      <AppPagination v-model:current-page="detailPage.current" v-model:page-size="detailPage.size" :total="employeeClues.length" />
    </section>

    <el-dialog v-model="detailVisible" :title="detailTitle" width="min(980px, 94vw)">
      <el-table class="desktop-table" :data="pagedEmployeeClues" width="100%" v-loading="detailLoading">
        <el-table-column prop="customerCode" label="客户编号" min-width="130" />
        <el-table-column label="来源平台" min-width="100">
          <template #default="{ row }">{{ sourcePlatformText(row.sourcePlatform) }}</template>
        </el-table-column>
        <el-table-column label="客户联系方式" min-width="150">
          <template #default="{ row }">{{ row.contactInfo || '待补充' }}</template>
        </el-table-column>
        <el-table-column prop="assignedSales" label="分配销售" min-width="110">
          <template #default="{ row }">{{ row.assignedSales || '-' }}</template>
        </el-table-column>
        <el-table-column label="当前状态" min-width="190">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
            <el-tag v-if="row.repeatDemand" class="status-extra" type="success">老客新需求</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="定金" min-width="100">
          <template #default="{ row }">{{ row.depositAmount || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" min-width="160" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <button class="table-action" type="button" @click="goDetail(row)">查看详情</button>
          </template>
        </el-table-column>
      </el-table>

      <div class="mobile-list detail-mobile-list">
        <article v-for="row in pagedEmployeeClues" :key="row.customerCode" class="clue-card">
          <div class="card-head">
            <strong>{{ row.customerCode }}</strong>
            <StatusTag :status="row.status" />
          </div>
          <span>{{ row.contactInfo || '客户联系方式待补充' }}</span>
          <small>{{ sourcePlatformText(row.sourcePlatform) }} · 销售：{{ row.assignedSales || '未分配' }} · {{ row.createdAt }}</small>
          <div class="tag-wrap">
            <el-tag v-if="row.repeatDemand" type="success">老客新需求</el-tag>
            <el-tag v-if="row.depositAmount" type="warning">定金 {{ row.depositAmount }}</el-tag>
          </div>
          <div class="card-actions">
            <button class="table-action" type="button" @click="goDetail(row)">查看详情</button>
          </div>
        </article>
      </div>
      <AppPagination v-model:current-page="detailPage.current" v-model:page-size="detailPage.size" :total="employeeClues.length" />
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { exportPerformance, getEmployeeClues, getPerformance } from '../api/clue'
import AppPagination from '../components/AppPagination.vue'
import FilterPanel from '../components/FilterPanel.vue'
import StatusTag from '../components/StatusTag.vue'
import { useAuthStore } from '../stores/auth'
import { sourcePlatformText } from '../utils/status'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const exporting = ref(false)
const detailLoading = ref(false)
const dateRange = ref(defaultMonthRange())
const rows = ref([])
const detailVisible = ref(false)
const selectedEmployee = ref(null)
const employeeClues = ref([])
const page = reactive({ current: 1, size: 10 })
const detailPage = reactive({ current: 1, size: 10 })
const currentUser = computed(() => authStore.user || JSON.parse(localStorage.getItem('crm_user') || 'null'))
const isEmployeeOnly = computed(() => currentUser.value?.role === 'EMPLOYEE')

const detailTitle = computed(() => {
  if (!selectedEmployee.value) return '员工数据'
  return `${selectedEmployee.value.employeeName}（${selectedEmployee.value.employeeCode}）客资明细`
})

const treeRows = computed(() => buildTreeRows(rows.value))
const mobileRows = computed(() => flattenTreeRows(treeRows.value))
const pagedTreeRows = computed(() => paginate(treeRows.value, page.current, page.size))
const pagedMobileRows = computed(() => paginate(mobileRows.value, page.current, page.size))
const pagedEmployeeClues = computed(() => paginate(employeeClues.value, detailPage.current, detailPage.size))

async function fetchRows() {
  loading.value = true
  try {
    const res = await getPerformance(dateParams())
    rows.value = res.data || []
    page.current = 1
  } catch (error) {
    await showError(error.message || '绩效数据加载失败')
  } finally {
    loading.value = false
  }
}

async function refreshPage() {
  if (isEmployeeOnly.value) {
    await openEmployeeClues({
      employeeCode: currentUser.value?.employeeCode,
      employeeName: currentUser.value?.name
    }, false)
    return
  }
  await fetchRows()
}

async function downloadExcel() {
  exporting.value = true
  try {
    const blob = await exportPerformance(dateParams())
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `员工绩效-${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (error) {
    await showError(error.message || '导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

async function openEmployeeClues(row, showDialog = true) {
  selectedEmployee.value = row
  detailPage.current = 1
  if (showDialog) {
    detailVisible.value = true
  }
  detailLoading.value = true
  try {
    const res = await getEmployeeClues(row.employeeCode, dateParams())
    employeeClues.value = res.data?.clues || []
  } catch (error) {
    await showError(error.message || '员工数据加载失败')
  } finally {
    detailLoading.value = false
  }
}

function goDetail(row) {
  detailVisible.value = false
  router.push(`/clues/${row.customerCode}`)
}

function buildTreeRows(sourceRows) {
  const map = new Map(sourceRows.map((row) => [row.employeeCode, { ...row, children: [] }]))
  const roots = []
  map.forEach((row) => {
    const parent = map.get(row.leaderEmployeeCode)
    if (parent && parent.employeeCode !== row.employeeCode) {
      parent.children.push(row)
    } else {
      roots.push(row)
    }
  })
  const sortRows = (items) => items
    .sort((left, right) => treeSortWeight(left) - treeSortWeight(right) || left.employeeCode.localeCompare(right.employeeCode))
    .map((item) => {
      item.children = sortRows(item.children || [])
      if (!item.children.length) {
        delete item.children
      }
      return item
    })
  return sortRows(roots)
}

function flattenTreeRows(sourceRows, level = 0) {
  return sourceRows.flatMap((row) => [
    { ...row, treeLevel: level, children: undefined },
    ...flattenTreeRows(row.children || [], level + 1)
  ])
}

function treeSortWeight(row) {
  if (row.role === 'ADMIN') return 0
  if (row.role === 'LEADER') return 1
  return 2
}

function paginate(sourceRows, current, size) {
  const start = (current - 1) * size
  return sourceRows.slice(start, start + size)
}

function dateParams() {
  return {
    startDate: dateRange.value?.[0],
    endDate: dateRange.value?.[1]
  }
}

function defaultMonthRange() {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), 1)
  return [formatDate(start), formatDate(now)]
}

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
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

function showError(message) {
  return ElMessageBox.alert(message, '提示', {
    confirmButtonText: '我知道了',
    type: 'warning'
  })
}

watch(() => page.size, () => {
  page.current = 1
})

watch(() => detailPage.size, () => {
  detailPage.current = 1
})

onMounted(refreshPage)
</script>

<style scoped>
.date-range {
  width: 100%;
}

.performance-card,
.clue-card {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.performance-card small,
.clue-card small {
  color: var(--text-muted);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.metric-grid span {
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(178, 174, 250, 0.12);
  color: var(--text-main);
}

.status-extra {
  margin-left: 6px;
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.detail-mobile-list {
  margin-top: 10px;
}

.card-actions {
  display: flex;
  justify-content: flex-start;
}

@media (max-width: 760px) {
  .date-range {
    width: 100%;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
