<template>
  <section>
    <div class="toolbar">
      <div class="page-title">数据统计</div>
    </div>

    <FilterPanel class="stats-filter" :collapsible="false">
      <el-date-picker
        v-model="dateRange"
        class="date-range"
        type="daterange"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        :shortcuts="dateShortcuts"
      />
      <template #actions>
        <el-button type="primary" :loading="loading" @click="fetchStats">刷新统计</el-button>
      </template>
    </FilterPanel>

    <div class="stats-grid">
      <article
        v-for="card in metricCards"
        :key="card.key"
        class="stat-card clickable"
        :class="card.className"
        @click="card.onClick"
      >
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
      </article>
    </div>

    <div class="stats-columns" :class="{ 'single-column': visibleAttributionSections.length === 1 }">
      <section v-if="showOperationAttribution" class="panel">
        <h2>运营归属</h2>
        <div v-if="operationTree.length" class="tree-list">
          <div v-for="item in operationTree" :key="item.employeeCode" class="tree-node">
            <button class="tree-row" type="button" @click="openEmployeeTreeDetail(item, 'UPLOADER')">
              <span>
                <strong>{{ item.employeeName }}</strong>
                <small>{{ item.employeeCode }} · {{ roleText(item.role) }}</small>
              </span>
              <em>{{ item.teamTotalCount }}</em>
            </button>
            <div v-if="item.children?.length" class="tree-children">
              <button v-for="child in item.children" :key="child.employeeCode" class="tree-row child" type="button" @click="openEmployeeTreeDetail(child, 'UPLOADER')">
                <span>
                  <strong>{{ child.employeeName }}</strong>
                  <small>{{ child.employeeCode }} · {{ roleText(child.role) }}</small>
                </span>
                <em>{{ child.teamTotalCount }}</em>
              </button>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无数据" />
      </section>

      <section v-if="showSalesAttribution" class="panel">
        <h2>销售归属</h2>
        <div v-if="salesTree.length" class="tree-list">
          <div v-for="item in salesTree" :key="item.employeeCode" class="tree-node">
            <button class="tree-row" type="button" @click="openEmployeeTreeDetail(item, 'SALES')">
              <span>
                <strong>{{ item.employeeName }}</strong>
                <small>{{ item.employeeCode }} · {{ roleText(item.role) }}</small>
              </span>
              <em>{{ item.teamTotalCount }}</em>
            </button>
            <div v-if="item.children?.length" class="tree-children">
              <button v-for="child in item.children" :key="child.employeeCode" class="tree-row child" type="button" @click="openEmployeeTreeDetail(child, 'SALES')">
                <span>
                  <strong>{{ child.employeeName }}</strong>
                  <small>{{ child.employeeCode }} · {{ roleText(child.role) }}</small>
                </span>
                <em>{{ child.teamTotalCount }}</em>
              </button>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无销售数据" />
      </section>
    </div>

    <el-dialog v-model="detailVisible" :title="detailTitle" width="min(860px, 94vw)">
      <el-table :data="pagedDetailRows" v-loading="detailLoading" row-key="customerCode" @row-click="goDetail">
        <el-table-column prop="customerCode" label="客户编号" min-width="130" />
        <el-table-column label="联系方式" min-width="160">
          <template #default="{ row }">{{ row.contactInfo || '待补充' }}</template>
        </el-table-column>
        <el-table-column prop="uploader" label="运营" min-width="100" />
        <el-table-column label="销售" min-width="100">
          <template #default="{ row }">{{ row.assignedSales || '未分配' }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="110">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="150" />
      </el-table>
      <AppPagination v-model:current-page="detailPage.current" v-model:page-size="detailPage.size" :total="detailRows.length" />
      <div class="detail-tip">点击任意一行可进入客户详情。</div>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { getClueStats, getPerformance, getStatsDetail } from '../api/clue'
import AppPagination from '../components/AppPagination.vue'
import FilterPanel from '../components/FilterPanel.vue'
import StatusTag from '../components/StatusTag.vue'
import { useAuthStore } from '../stores/auth'
import { statusText } from '../utils/status'

const router = useRouter()
const authStore = useAuthStore()
const today = formatDate(new Date())
const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const detailTitle = ref('统计明细')
const detailRows = ref([])
const detailPage = reactive({ current: 1, size: 10 })
const performanceRows = ref([])
const dateRange = ref(defaultMonthRange())
const stats = reactive({
  totalCount: 0,
  todayCount: 0,
  repeatDemandCount: 0,
  firstDemandCount: 0,
  statusCounts: {},
  uploaderCounts: {},
  salesCounts: {}
})
const currentUser = computed(() => authStore.user || JSON.parse(localStorage.getItem('crm_user') || 'null'))
const showOperationAttribution = computed(() => currentUser.value?.role === 'ADMIN' || currentUser.value?.position === 'OPERATION')
const showSalesAttribution = computed(() => currentUser.value?.role === 'ADMIN' || currentUser.value?.position === 'SALES')
const visibleAttributionSections = computed(() => [
  showOperationAttribution.value,
  showSalesAttribution.value
].filter(Boolean))

const dateShortcuts = [
  {
    text: '今天',
    value: () => {
      const date = new Date()
      return [date, date]
    }
  },
  {
    text: '本周',
    value: () => {
      const now = new Date()
      const day = now.getDay() || 7
      const start = new Date(now)
      start.setDate(now.getDate() - day + 1)
      return [start, now]
    }
  },
  {
    text: '本月',
    value: () => {
      const now = new Date()
      return [new Date(now.getFullYear(), now.getMonth(), 1), now]
    }
  }
]

const metricCards = computed(() => [
  { key: 'total', label: '总客资', value: stats.totalCount || 0, className: 'stat-primary', onClick: () => openDetail('ALL', '', '筛选范围总客资') },
  { key: 'today', label: '今日新增', value: stats.todayCount || 0, onClick: openTodayDetail },
  { key: 'repeat', label: '老客新需求', value: stats.repeatDemandCount || 0, onClick: openRepeatDetail },
  { key: 'new', label: '新录入', value: statusCount('NEW'), onClick: () => openDetail('STATUS', 'NEW', '新录入') },
  { key: 'following', label: '跟进中', value: statusCount('FOLLOWING'), onClick: () => openDetail('STATUS', 'FOLLOWING', '跟进中') },
  { key: 'invalid', label: '无效用户', value: statusCount('INVALID'), className: 'stat-danger', onClick: () => openDetail('STATUS', 'INVALID', '无效用户') },
  { key: 'deposit', label: '已交定金', value: depositCount.value, className: 'stat-pink', onClick: openDepositDetail },
  { key: 'refunded', label: '退单', value: statusCount('REFUNDED'), className: 'stat-danger', onClick: () => openDetail('STATUS', 'REFUNDED', '退单') },
  { key: 'landed', label: '已落地', value: landedCount.value, className: 'stat-green', onClick: openLandedDetail }
])
const depositCount = computed(() => {
  return stats.statusCounts?.DEPOSIT_PAID || 0
})
const landedCount = computed(() => {
  return stats.statusCounts?.LANDED || 0
})
const businessPerformanceRows = computed(() => performanceRows.value.filter((item) => item.role !== 'ADMIN'))
const operationTree = computed(() => showOperationAttribution.value ? buildEmployeeTree(businessPerformanceRows.value.filter((item) => item.position === 'OPERATION')) : [])
const salesTree = computed(() => showSalesAttribution.value ? buildEmployeeTree(businessPerformanceRows.value.filter((item) => item.position === 'SALES')) : [])
const pagedDetailRows = computed(() => {
  const start = (detailPage.current - 1) * detailPage.size
  return detailRows.value.slice(start, start + detailPage.size)
})

async function fetchStats() {
  loading.value = true
  try {
    const [statsRes, performanceRes] = await Promise.all([
      getClueStats(currentDateParams()),
      getPerformance(currentDateParams())
    ])
    Object.assign(stats, {
      totalCount: 0,
      todayCount: 0,
      repeatDemandCount: 0,
      firstDemandCount: 0,
      statusCounts: {},
      uploaderCounts: {},
      salesCounts: {},
      ...(statsRes.data || {})
    })
    performanceRows.value = performanceRes.data || []
  } finally {
    loading.value = false
  }
}

async function openDetail(type, value, title, overrideDates = null) {
  detailTitle.value = `${title}明细`
  detailVisible.value = true
  detailLoading.value = true
  detailPage.current = 1
  try {
    const res = await getStatsDetail({
      ...currentDateParams(overrideDates),
      type,
      value
    })
    detailRows.value = res.data || []
  } catch (error) {
    detailRows.value = []
    await ElMessageBox.alert(error.message || '统计明细加载失败', '提示', {
      confirmButtonText: '我知道了',
      type: 'warning'
    })
  } finally {
    detailLoading.value = false
  }
}

function openTodayDetail() {
  openDetail('ALL', '', '今日新增', [today, today])
}

async function openRepeatDetail() {
  await openDetail('ALL', '', '老客新需求')
  detailRows.value = detailRows.value.filter((row) => row.repeatDemand)
}

async function openDepositDetail() {
  await openDetail('ALL', '', '已交定金')
  detailRows.value = detailRows.value.filter((row) => ['DEPOSIT_PAID', 'DEALED'].includes(row.status))
}

async function openLandedDetail() {
  await openDetail('STATUS', 'LANDED', '已落地')
}

async function openEmployeeTreeDetail(row, type) {
  const codes = collectEmployeeCodes(row)
  const titlePrefix = type === 'SALES' ? '销售' : '运营'
  detailTitle.value = `${titlePrefix}：${row.employeeName}明细`
  detailVisible.value = true
  detailLoading.value = true
  detailPage.current = 1
  try {
    const res = await getStatsDetail({
      ...currentDateParams(),
      type: 'ALL',
      value: ''
    })
    const rows = res.data || []
    detailRows.value = rows.filter((item) => {
      const employeeCode = type === 'SALES' ? item.assignedSalesEmployeeCode : item.uploaderEmployeeCode
      return codes.includes(employeeCode)
    })
  } catch (error) {
    detailRows.value = []
    await ElMessageBox.alert(error.message || '统计明细加载失败', '提示', {
      confirmButtonText: '我知道了',
      type: 'warning'
    })
  } finally {
    detailLoading.value = false
  }
}

function goDetail(row) {
  if (!row?.customerCode) return
  detailVisible.value = false
  router.push(`/clues/${row.customerCode}`)
}

function currentDateParams(overrideDates = null) {
  const range = overrideDates || dateRange.value || []
  return {
    startDate: range[0],
    endDate: range[1]
  }
}

function statusCount(status) {
  return stats.statusCounts?.[status] || 0
}

function buildEmployeeTree(sourceRows) {
  const map = new Map(sourceRows.map((row) => [row.employeeCode, { ...row, children: [], teamTotalCount: row.totalCount || 0 }]))
  const roots = []
  map.forEach((row) => {
    const parent = map.get(row.leaderEmployeeCode)
    if (parent && parent.employeeCode !== row.employeeCode) {
      parent.children.push(row)
    } else {
      roots.push(row)
    }
  })
  const finalize = (items) => items
    .sort((left, right) => treeSortWeight(left) - treeSortWeight(right) || left.employeeCode.localeCompare(right.employeeCode))
    .map((item) => {
      item.children = finalize(item.children || [])
      item.teamTotalCount = (item.totalCount || 0) + item.children.reduce((sum, child) => sum + child.teamTotalCount, 0)
      return item
    })
  return finalize(roots)
}

function collectEmployeeCodes(row) {
  return [row.employeeCode, ...(row.children || []).flatMap(collectEmployeeCodes)]
}

function treeSortWeight(row) {
  if (row.role === 'ADMIN') return 0
  if (row.role === 'LEADER') return 1
  return 2
}

function roleText(role) {
  return {
    ADMIN: '管理员',
    LEADER: '组长',
    EMPLOYEE: '员工'
  }[role] || role
}

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function defaultMonthRange() {
  const now = new Date()
  return [formatDate(new Date(now.getFullYear(), now.getMonth(), 1)), formatDate(now)]
}

watch(() => detailPage.size, () => {
  detailPage.current = 1
})

onMounted(fetchStats)
</script>

<style scoped>
.date-range {
  width: 100%;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  min-height: 94px;
  padding: 14px 16px;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-soft);
}

.stat-card::after {
  content: "";
  position: absolute;
  right: -24px;
  bottom: -34px;
  width: 84px;
  height: 84px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(75, 75, 254, 0.16), rgba(178, 174, 250, 0.18));
}

.stat-primary,
.stat-pink,
.stat-green {
  color: #fff;
}

.stat-primary {
  background: linear-gradient(135deg, var(--brand), var(--violet));
}

.stat-pink {
  background: linear-gradient(135deg, var(--pink), var(--violet));
}

.stat-green {
  background: linear-gradient(135deg, #18a058, #4fc878);
}

.stat-danger {
  color: #fff;
  background: linear-gradient(135deg, #f05b72, #fe7ba7);
}

.clickable {
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.clickable:hover {
  transform: translateY(-2px);
  box-shadow: 0 20px 42px rgba(40, 36, 120, 0.14);
}

.stat-card span {
  display: block;
  color: inherit;
  opacity: 0.78;
  margin-bottom: 10px;
}

.stat-card strong {
  position: relative;
  z-index: 1;
  font-family: "DIN Alternate", "PingFang SC", sans-serif;
  font-size: 30px;
  line-height: 1;
}

.stats-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.stats-columns.single-column {
  grid-template-columns: minmax(0, 1fr);
}

.stats-wide {
  grid-column: 1 / -1;
}

h2 {
  margin: 0 0 14px;
  font-size: 18px;
  color: var(--text-strong);
}

.rank-list,
.tree-list {
  display: grid;
  gap: 10px;
}

.rank-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 46px;
  padding: 0 14px;
  border: 0;
  border-radius: 14px;
  background: rgba(178, 174, 250, 0.12);
  color: var(--text-main);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.rank-row:hover {
  background: rgba(75, 75, 254, 0.12);
}

.rank-row strong {
  color: var(--brand);
  font-family: "DIN Alternate", sans-serif;
  font-size: 20px;
}

.tree-node {
  display: grid;
  gap: 8px;
}

.tree-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 50px;
  padding: 8px 12px;
  border: 0;
  border-radius: 14px;
  background: rgba(178, 174, 250, 0.12);
  color: var(--text-main);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.tree-row:hover {
  background: rgba(75, 75, 254, 0.12);
}

.tree-row span {
  display: grid;
  gap: 2px;
}

.tree-row small {
  color: var(--text-muted);
  font-size: 12px;
}

.tree-row em {
  min-width: 42px;
  color: var(--brand);
  font-family: "DIN Alternate", sans-serif;
  font-size: 22px;
  font-style: normal;
  font-weight: 800;
  text-align: right;
}

.tree-children {
  display: grid;
  gap: 8px;
  padding-left: 18px;
  border-left: 2px solid rgba(178, 174, 250, 0.36);
}

.tree-row.child {
  min-height: 44px;
  background: rgba(255, 255, 255, 0.66);
}

.detail-tip {
  padding-top: 10px;
  color: var(--text-muted);
  font-size: 13px;
}

@media (max-width: 980px) {
  .stats-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .stats-columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .stats-columns,
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .date-range {
    width: 100%;
  }

  .stat-card {
    min-height: 88px;
  }
}
</style>
