<template>
  <section>
    <div class="toolbar">
      <div class="page-title">分配管理</div>
    </div>

    <div class="pool-tabs">
      <button class="pool-tab" :class="{ active: poolType === 'PUBLIC' }" type="button" @click="switchPool('PUBLIC')">公共待分配池</button>
      <button v-if="canViewMyPool" class="pool-tab" :class="{ active: poolType === 'MINE' }" type="button" @click="switchPool('MINE')">我的销售池</button>
    </div>

    <FilterPanel v-model:expanded="filterExpanded" :mobile-open="mobileFilterVisible">
      <el-input v-model="filters.customerCode" class="filter-field" clearable placeholder="客户编号" />
      <el-input v-model="filters.contactInfo" class="filter-field" clearable placeholder="联系方式" />
      <el-date-picker
        v-model="dateRange"
        class="date-range"
        type="daterange"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        :shortcuts="dateShortcuts"
      />
      <el-select v-model="status" class="status-select">
        <el-option label="新录入/跟进中" value="PENDING" />
        <el-option label="全部状态" value="" />
        <el-option label="新录入" value="NEW" />
        <el-option label="跟进中" value="FOLLOWING" />
        <el-option label="已交定金" value="DEPOSIT_PAID" />
        <el-option label="退单" value="REFUNDED" />
        <el-option label="已落地" value="LANDED" />
      </el-select>
      <template #advanced>
        <el-select v-model="filters.sourcePlatform" class="filter-field" clearable placeholder="来源平台">
          <el-option label="抖音" value="DOUYIN" />
          <el-option label="小红书" value="XIAOHONGSHU" />
        </el-select>
        <el-select v-model="filters.assignedSales" class="filter-field" clearable filterable placeholder="销售/编号">
          <el-option v-for="sales in salesUsers" :key="sales.employeeCode" :label="`${sales.name}（${sales.employeeCode}）`" :value="sales.employeeCode" />
        </el-select>
        <el-input v-model="filters.keyword" class="filter-field" clearable placeholder="备注等模糊搜索" />
      </template>
      <template #actions>
        <el-button type="primary" :loading="loading" @click="searchRows">查询</el-button>
      </template>
    </FilterPanel>

    <div class="panel desktop-table">
      <el-table :data="desktopRows" width="100%" v-loading="loading">
        <el-table-column prop="customerCode" label="客户编号" min-width="130" />
        <el-table-column label="来源平台" min-width="100">
          <template #default="{ row }">{{ sourcePlatformText(row.sourcePlatform) }}</template>
        </el-table-column>
        <el-table-column label="客户联系方式" min-width="160">
          <template #default="{ row }">{{ row.contactInfo || '待补充' }}</template>
        </el-table-column>
        <el-table-column prop="uploader" label="上传运营" min-width="110" />
        <el-table-column label="分配销售" min-width="140">
          <template #default="{ row }">{{ row.assignedSales || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" min-width="160" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <TextActions>
              <button v-if="canClaim(row)" class="table-action" type="button" @click="claimRow(row)">领取</button>
              <button v-if="canAssign(row)" class="table-action" type="button" @click="openAssign(row)">分配销售</button>
              <button class="table-action" type="button" @click="goDetail(row)">详情</button>
            </TextActions>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" :total="total" />
    </div>

    <div class="mobile-list" v-loading="loading">
      <div v-if="loading && !filteredRows.length" class="mobile-loading-panel">
        <div class="loading-line strong"></div>
        <div class="loading-line"></div>
        <div class="loading-line short"></div>
      </div>
      <article v-for="row in mobileRows" :key="row.customerCode" class="clue-card">
        <div class="card-head">
          <strong>{{ row.customerCode }}</strong>
        </div>
        <p class="contact-line">{{ row.contactInfo || '客户联系方式待补充' }}</p>
        <small class="compact-meta">
          {{ sourcePlatformText(row.sourcePlatform) }} · {{ row.uploader || '-' }} · 销售：{{ row.assignedSales || '未分配' }} · {{ row.createdAt }}
        </small>
        <TextActions class="card-actions">
          <button v-if="canClaim(row)" class="table-action" type="button" @click="claimRow(row)">领取</button>
          <button v-if="canAssign(row)" class="table-action" type="button" @click="openAssign(row)">分配销售</button>
          <button class="table-action" type="button" @click="goDetail(row)">详情</button>
        </TextActions>
      </article>
      <div v-if="rows.length" class="mobile-load-state">
        <span v-if="loadingMore">加载中...</span>
        <span v-else-if="hasMore">上滑加载更多</span>
        <span v-else>已经到底了</span>
      </div>
      <el-empty v-if="!loading && !filteredRows.length" description="暂无可分配线索" />
    </div>

    <el-dialog v-model="assignVisible" title="分配销售" width="min(520px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="客户编号">
          <el-input :model-value="currentRow?.customerCode" disabled />
        </el-form-item>
        <el-form-item label="选择销售">
          <el-select v-model="assignForm.salesEmployeeCode" class="full-field" filterable placeholder="请选择销售">
            <el-option v-for="sales in salesUsers" :key="sales.employeeCode" :label="`${sales.name}（${sales.employeeCode}）`" :value="sales.employeeCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="分配备注">
          <el-input v-model="assignForm.remark" type="textarea" :rows="3" placeholder="可填写沟通说明、客户重点等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAssign">确认分配</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="logVisible" title="分配日志" width="min(760px, 94vw)">
      <el-timeline v-if="currentLogs.length">
        <el-timeline-item v-for="log in currentLogs" :key="`${log.createdAt}-${log.action}-${log.operatorCode}`" :timestamp="log.createdAt" placement="top">
          <div class="assign-log-card">
            <strong>{{ log.actionText || log.action }}</strong>
            <span>操作人：{{ log.operator }}（{{ log.operatorCode }}）</span>
            <p v-if="log.fromSales || log.toSales">
              {{ log.fromSales || '未分配' }}（{{ log.fromSalesEmployeeCode || '-' }}） → {{ log.toSales || '未分配' }}（{{ log.toSalesEmployeeCode || '-' }}）
            </p>
            <p v-if="log.remark">{{ log.remark }}</p>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无分配日志" />
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSalesCandidates } from '../api/auth'
import { assignClue, claimClue, listMySalesPool, listPublicSalesPool, releaseClue } from '../api/clue'
import AppPagination from '../components/AppPagination.vue'
import FilterPanel from '../components/FilterPanel.vue'
import TextActions from '../components/TextActions.vue'
import { useAuthStore } from '../stores/auth'
import { sourcePlatformText } from '../utils/status'

const router = useRouter()
const authStore = useAuthStore()
const filters = reactive({
  customerCode: '',
  contactInfo: '',
  sourcePlatform: '',
  assignedSales: '',
  keyword: ''
})
const filterExpanded = ref(false)
const status = ref('PENDING')
const dateRange = ref([])
const poolType = ref('PUBLIC')
const loading = ref(false)
const loadingMore = ref(false)
const saving = ref(false)
const rows = ref([])
const users = ref([])
const total = ref(0)
const hasMore = ref(false)
const page = reactive({ current: 1, size: 10 })
const mobileFilterVisible = ref(false)
const assignVisible = ref(false)
const logVisible = ref(false)
const currentRow = ref(null)
const currentLogs = ref([])
const assignForm = reactive({ salesEmployeeCode: '', remark: '' })
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

const canViewMyPool = computed(() => authStore.user?.role === 'ADMIN' || authStore.user?.position === 'SALES')
const isSales = computed(() => authStore.user?.position === 'SALES')
const salesUsers = computed(() => users.value.filter((user) => user.position === 'SALES'))
const mobileRows = computed(() => rows.value)
const desktopRows = computed(() => rows.value)
const filteredRows = computed(() => rows.value)

async function fetchRows({ append = false } = {}) {
  const isMobile = window.matchMedia('(max-width: 760px)').matches
  if (append) {
    if (loading.value || loadingMore.value || !hasMore.value) return
    loadingMore.value = true
  } else {
    loading.value = true
    if (isMobile) {
      page.current = 1
    }
  }
  try {
    const poolRequest = poolType.value === 'MINE'
      ? listMySalesPool(filterParams())
      : listPublicSalesPool(filterParams())
    const [clueRes, userRes] = await Promise.all([poolRequest, listSalesCandidates()])
    const payload = normalizePage(clueRes.data)
    rows.value = append ? [...rows.value, ...payload.records] : payload.records
    total.value = payload.total
    hasMore.value = payload.hasMore
    users.value = userRes.data || []
  } catch (error) {
    await showError(error.message || '分配数据加载失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function normalizePage(data) {
  if (Array.isArray(data)) {
    return { records: data, total: data.length, hasMore: false }
  }
  return {
    records: data?.records || [],
    total: data?.total || 0,
    hasMore: Boolean(data?.hasMore)
  }
}

function searchRows() {
  if (page.current === 1) {
    fetchRows()
    return
  }
  page.current = 1
}

function filterParams() {
  return {
    ...Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '')),
    status: status.value,
    startDate: dateRange.value?.[0],
    endDate: dateRange.value?.[1],
    page: page.current,
    pageSize: page.size
  }
}

function switchPool(type) {
  poolType.value = type
  page.current = 1
  fetchRows()
}

function canClaim(row) {
  return poolType.value === 'PUBLIC' && isSales.value && !row.assignedSalesEmployeeCode
}

function canAssign(row) {
  return poolType.value === 'PUBLIC' && !isSales.value && !row.assignedSalesEmployeeCode
}

function canTransfer(row) {
  return !isSales.value && Boolean(row.assignedSalesEmployeeCode)
}

function canRelease(row) {
  return !isSales.value && Boolean(row.assignedSalesEmployeeCode)
}

async function claimRow(row) {
  try {
    await ElMessageBox.confirm(`确认领取线索 ${row.customerCode} 吗？领取后将进入你的销售池，其他销售不能再领取。`, '领取确认', {
      confirmButtonText: '确认领取',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await claimClue(row.customerCode)
    ElMessage.success('已领取到我的销售池')
    await fetchRows()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      await showError(error.message || '领取失败，请刷新后重试')
    }
  }
}

function openAssign(row) {
  currentRow.value = row
  assignForm.salesEmployeeCode = row.assignedSalesEmployeeCode || ''
  assignForm.remark = ''
  assignVisible.value = true
}

function openAssignLogs(row) {
  currentLogs.value = [...(row.assignLogs || [])].reverse()
  logVisible.value = true
}

async function submitAssign() {
  if (!currentRow.value || !assignForm.salesEmployeeCode) {
    await showError('请选择销售人员')
    return
  }
  saving.value = true
  try {
    await assignClue(currentRow.value.customerCode, { ...assignForm })
    ElMessage.success('已分配销售')
    assignVisible.value = false
    await fetchRows()
  } catch (error) {
    await showError(error.message || '分配失败')
  } finally {
    saving.value = false
  }
}

async function releaseRow(row) {
  try {
    const { value } = await ElMessageBox.prompt(`确认将客户 ${row.customerCode} 释放回销售公共池吗？释放后其他销售可以重新领取。`, '释放确认', {
      confirmButtonText: '确认释放',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '可填写释放原因'
    })
    await releaseClue(row.customerCode, { remark: value || '释放回公共池' })
    ElMessage.success('已释放回销售公共池')
    await fetchRows()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      await showError(error.message || '释放失败')
    }
  }
}

function goDetail(row) {
  router.push(`/clues/${row.customerCode}`)
}

function showError(message) {
  return ElMessageBox.alert(message, '提示', { confirmButtonText: '我知道了', type: 'warning' })
}

onMounted(() => {
  fetchRows()
  window.addEventListener('crm-toggle-mobile-search', toggleMobileFilter)
  window.addEventListener('scroll', handleMobileScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('crm-toggle-mobile-search', toggleMobileFilter)
  window.removeEventListener('scroll', handleMobileScroll)
})

function toggleMobileFilter() {
  mobileFilterVisible.value = !mobileFilterVisible.value
}

watch(() => page.size, () => {
  const wasFirstPage = page.current === 1
  page.current = 1
  if (wasFirstPage) fetchRows()
})

watch(() => page.current, (current, previous) => {
  if (window.matchMedia('(max-width: 760px)').matches) return
  if (current !== previous) fetchRows()
})

function handleMobileScroll() {
  if (!window.matchMedia('(max-width: 760px)').matches) return
  if (!hasMore.value || loading.value || loadingMore.value) return
  const nearBottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 180
  if (nearBottom) {
    page.current += 1
    fetchRows({ append: true })
  }
}
</script>

<style scoped>
.pool-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0 0 14px;
}

.pool-tab {
  padding: 9px 16px;
  border: 1px solid rgba(178, 174, 250, 0.48);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--text-muted);
  font-weight: 800;
  cursor: pointer;
}

.pool-tab.active {
  border-color: transparent;
  background: linear-gradient(90deg, var(--violet), var(--brand));
  color: #fff;
  box-shadow: 0 10px 24px rgba(75, 75, 254, 0.18);
}

.date-range,
.status-select,
.full-field {
  width: 100%;
}

.clue-card {
  display: grid;
  gap: 9px;
  padding: 14px;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.card-actions {
  display: flex;
  justify-content: flex-start;
  gap: 8px;
}

.contact-line {
  margin: 0;
  word-break: break-all;
  color: var(--text-strong);
  font-size: 16px;
  line-height: 1.45;
}

.compact-meta {
  color: var(--text-muted);
}

.assign-log-card {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid rgba(178, 174, 250, 0.34);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
}

.assign-log-card strong {
  color: var(--text-strong);
}

.assign-log-card span,
.assign-log-card p {
  margin: 0;
  color: var(--text-muted);
}

@media (max-width: 760px) {
  .pool-tabs {
    gap: 8px;
    margin-bottom: 12px;
  }

  .pool-tab {
    flex: 1;
    min-width: 0;
    padding: 10px 8px;
    font-size: 13px;
  }

  .clue-card {
    padding: 16px;
  }

  .mobile-load-state {
    padding: 4px 0 16px;
    color: var(--text-muted);
    text-align: center;
    font-size: 13px;
  }

  .mobile-loading-panel {
    display: grid;
    gap: 10px;
    padding: 18px;
    border-radius: 18px;
    background: rgba(255, 255, 255, 0.82);
    box-shadow: var(--shadow-soft);
  }

  .loading-line {
    height: 14px;
    border-radius: 999px;
    background: linear-gradient(90deg, rgba(178, 174, 250, 0.22), rgba(255, 255, 255, 0.78), rgba(178, 174, 250, 0.22));
    background-size: 220% 100%;
    animation: loading-shimmer 1.2s linear infinite;
  }

  .loading-line.strong {
    width: 72%;
    height: 18px;
  }

  .loading-line.short {
    width: 46%;
  }

  @keyframes loading-shimmer {
    from {
      background-position: 120% 0;
    }
    to {
      background-position: -120% 0;
    }
  }

}
</style>
