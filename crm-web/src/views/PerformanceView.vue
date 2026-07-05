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
      <EmployeeClueList :rows="employeeClues" :loading="detailLoading" @open="goDetail" />
      <AppPagination v-model:current-page="detailPage.current" v-model:page-size="detailPage.size" :total="employeeClueTotal" />
    </section>

    <el-dialog v-model="detailVisible" :title="detailTitle" width="min(980px, 94vw)">
      <EmployeeClueList :rows="employeeClues" :loading="detailLoading" show-actions @open="goDetail" />
      <AppPagination v-model:current-page="detailPage.current" v-model:page-size="detailPage.size" :total="employeeClueTotal" />
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { exportPerformance, getEmployeeClues, getPerformance } from '../api/clue'
import { downloadBlob, todayFilename } from '../utils/download'
import AppPagination from '../components/AppPagination.vue'
import FilterPanel from '../components/FilterPanel.vue'
import StatusTag from '../components/StatusTag.vue'
import { useAuthStore } from '../stores/auth'
import { addMethodText, sourcePlatformText } from '../utils/status'
import { getStoredUser } from '../utils/session'

const EmployeeClueList = defineComponent({
  name: 'EmployeeClueList',
  props: {
    rows: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    showActions: { type: Boolean, default: false }
  },
  emits: ['open'],
  setup(props, { emit }) {
    return () => h('div', { class: 'employee-clue-list' }, [
      h(ElTable, {
        class: 'desktop-table',
        data: props.rows,
        width: '100%',
        loading: props.loading,
        onRowClick: (row) => emit('open', row)
      }, () => [
        h(ElTableColumn, { prop: 'customerCode', label: '客户编号', minWidth: '130' }),
        h(ElTableColumn, { label: '来源平台', minWidth: '100' }, {
          default: ({ row }) => sourcePlatformText(row.sourcePlatform)
        }),
        h(ElTableColumn, { label: '添加方式', minWidth: '96' }, {
          default: ({ row }) => addMethodText(row.addMethod)
        }),
        h(ElTableColumn, { label: '客户联系方式', minWidth: '150' }, {
          default: ({ row }) => row.contactInfo || '待补充'
        }),
        h(ElTableColumn, { label: '分配销售', minWidth: '110' }, {
          default: ({ row }) => row.assignedSales || '-'
        }),
        h(ElTableColumn, { label: '当前状态', minWidth: '190' }, {
          default: ({ row }) => h('span', { class: 'status-cell' }, [
            h(StatusTag, { status: row.status }),
            row.repeatDemand ? h(ElTag, { class: 'status-extra', type: 'success' }, () => '老客新需求') : null
          ])
        }),
        h(ElTableColumn, { label: '定金', minWidth: '100' }, {
          default: ({ row }) => row.depositAmount || '-'
        }),
        h(ElTableColumn, { label: '剩余尾款', minWidth: '100' }, {
          default: ({ row }) => row.remainingBalance || '-'
        }),
        h(ElTableColumn, { prop: 'createdAt', label: '上传时间', minWidth: '160' }),
        props.showActions ? h(ElTableColumn, { label: '操作', width: '110', fixed: 'right' }, {
          default: ({ row }) => h('button', { class: 'table-action', type: 'button', onClick: (event) => { event.stopPropagation(); emit('open', row) } }, '查看详情')
        }) : null
      ]),
      h('div', { class: 'mobile-list detail-mobile-list' }, props.rows.map((row) => h('article', {
        key: row.customerCode,
        class: 'clue-card',
        onClick: () => emit('open', row)
      }, [
        h('div', { class: 'card-head' }, [
          h('strong', row.customerCode),
          h(StatusTag, { status: row.status })
        ]),
        h('span', row.contactInfo || '客户联系方式待补充'),
        h('small', `${sourcePlatformText(row.sourcePlatform)} · ${addMethodText(row.addMethod)} · 销售：${row.assignedSales || '未分配'} · ${row.createdAt || '-'}`),
        h('div', { class: 'tag-wrap' }, [
          row.repeatDemand ? h(ElTag, { type: 'success' }, () => '老客新需求') : null,
          row.depositAmount ? h(ElTag, { type: 'warning' }, () => `定金 ${row.depositAmount}`) : null,
          row.remainingBalance ? h(ElTag, { type: 'info' }, () => `尾款 ${row.remainingBalance}`) : null
        ])
      ])))
    ])
  }
})

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
const employeeClueTotal = ref(0)
const page = reactive({ current: 1, size: 10 })
const detailPage = reactive({ current: 1, size: 10 })
const currentUser = computed(() => authStore.user || getStoredUser())
const isEmployeeOnly = computed(() => currentUser.value?.role === 'EMPLOYEE')

const detailTitle = computed(() => {
  if (!selectedEmployee.value) return '员工数据'
  return `${selectedEmployee.value.employeeName || selectedEmployee.value.name}（${selectedEmployee.value.employeeCode}）客资明细`
})

const treeRows = computed(() => buildTreeRows(rows.value))
const mobileRows = computed(() => flattenTreeRows(treeRows.value))
const pagedTreeRows = computed(() => paginate(treeRows.value, page.current, page.size))
const pagedMobileRows = computed(() => paginate(mobileRows.value, page.current, page.size))

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
  ElMessage.info('正在导出员工绩效...')
  try {
    const blob = await exportPerformance(dateParams())
    downloadBlob(blob, todayFilename('员工绩效'))
    ElMessage.success('员工绩效导出成功')
  } catch (error) {
    await showError(error.message || '员工绩效导出失败')
  } finally {
    exporting.value = false
  }
}

async function openEmployeeClues(row, showDialog = true) {
  selectedEmployee.value = row
  if (showDialog) {
    detailVisible.value = true
  }
  detailLoading.value = true
  try {
    const res = await getEmployeeClues(row.employeeCode, {
      ...dateParams(),
      page: detailPage.current,
      pageSize: detailPage.size
    })
    const data = res.data?.clues || {}
    employeeClues.value = data.records || []
    employeeClueTotal.value = Number(data.total || 0)
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

watch([() => detailPage.current, () => detailPage.size], () => {
  if (selectedEmployee.value) {
    openEmployeeClues(selectedEmployee.value, detailVisible.value)
  }
})

onMounted(refreshPage)
</script>

<style scoped>
.date-range {
  width: 100%;
}

.performance-card,
:deep(.clue-card) {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.card-head,
:deep(.card-head) {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.performance-card small,
:deep(.clue-card small) {
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

:deep(.status-cell) {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

:deep(.tag-wrap) {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

:deep(.detail-mobile-list) {
  margin-top: 10px;
}

.card-actions {
  display: flex;
  justify-content: flex-start;
}

@media (max-width: 760px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
