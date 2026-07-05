<template>
  <section>
    <div class="toolbar">
      <div class="page-title">分配管理</div>
      <div class="desktop-sound-toggle">
        <span>提示音</span>
        <el-switch
          v-model="notificationSoundEnabled"
          inline-prompt
          active-text="开"
          inactive-text="关"
          @change="updateNotificationSound"
        />
      </div>
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
        <el-option label="已通过" value="PASSED" />
        <el-option label="已交定金" value="DEPOSIT_PAID" />
        <el-option label="退单" value="REFUNDED" />
        <el-option label="已落地" value="LANDED" />
      </el-select>
      <template #advanced>
        <el-select v-model="filters.sourcePlatform" class="filter-field" clearable placeholder="来源平台">
          <el-option label="抖音" value="DOUYIN" />
          <el-option label="小红书" value="XIAOHONGSHU" />
        </el-select>
        <el-select v-model="filters.addMethod" class="filter-field" clearable placeholder="添加方式">
          <el-option label="主动" value="ACTIVE" />
          <el-option label="被动" value="PASSIVE" />
          <el-option label="领队" value="GUIDE" />
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
        <el-table-column label="添加方式" min-width="96">
          <template #default="{ row }">{{ addMethodText(row.addMethod) }}</template>
        </el-table-column>
        <el-table-column label="客户联系方式" min-width="160">
          <template #default="{ row }">{{ row.contactInfo || '待补充' }}</template>
        </el-table-column>
        <el-table-column prop="uploader" label="上传运营" min-width="110" />
        <el-table-column label="分配销售" min-width="140">
          <template #default="{ row }">{{ row.assignedSales || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" min-width="160" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <TextActions>
              <button v-if="canClaim(row)" class="table-action" type="button" @click="claimRow(row)">领取</button>
              <button v-if="canAssign(row)" class="table-action" type="button" @click="openAssign(row)">分配销售</button>
              <button v-if="canRelease(row)" class="table-action" type="button" @click="releaseRow(row)">释放</button>
              <button v-if="row.assignLogs?.length" class="table-action" type="button" @click="openAssignLogs(row)">日志</button>
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
          {{ sourcePlatformText(row.sourcePlatform) }} · {{ addMethodText(row.addMethod) }} · {{ row.uploader || '-' }} · 销售：{{ row.assignedSales || '未分配' }} · {{ row.createdAt }}
        </small>
        <TextActions class="card-actions">
          <button v-if="canClaim(row)" class="table-action" type="button" @click="claimRow(row)">领取</button>
          <button v-if="canAssign(row)" class="table-action" type="button" @click="openAssign(row)">分配销售</button>
          <button v-if="canRelease(row)" class="table-action" type="button" @click="releaseRow(row)">释放</button>
          <button v-if="row.assignLogs?.length" class="table-action" type="button" @click="openAssignLogs(row)">日志</button>
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
import { ElNotification } from 'element-plus'
import { listSalesCandidates } from '../api/auth'
import { assignClue, claimClue, listMySalesPool, listPublicSalesPool, releaseClue } from '../api/clue'
import AppPagination from '../components/AppPagination.vue'
import FilterPanel from '../components/FilterPanel.vue'
import TextActions from '../components/TextActions.vue'
import { useAuthStore } from '../stores/auth'
import {
  isNotificationSoundEnabled,
  playNewDataSound,
  setNotificationSoundEnabled,
  setupNotificationSoundUnlock
} from '../utils/notificationSound'
import { subscribeRealtime } from '../utils/realtime'
import { addMethodText, sourcePlatformText } from '../utils/status'
import { confirmAction, promptAction, runAction, showError, showSuccess } from '../utils/feedback'

const router = useRouter()
const authStore = useAuthStore()
const filters = reactive({
  customerCode: '',
  contactInfo: '',
  sourcePlatform: '',
  addMethod: '',
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
const notificationSoundEnabled = ref(isNotificationSoundEnabled())
const currentRow = ref(null)
const currentLogs = ref([])
const assignForm = reactive({ salesEmployeeCode: '', remark: '' })
let unsubscribeRealtime
let realtimeRefreshTimer

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

function canRelease(row) {
  if (!row.assignedSalesEmployeeCode) {
    return false
  }
  if (!isSales.value) {
    return true
  }
  return poolType.value === 'MINE' && row.assignedSalesEmployeeCode === authStore.user?.employeeCode
}

async function claimRow(row) {
  try {
    await confirmAction(`确认领取线索 ${row.customerCode} 吗？领取后将进入你的销售池，其他销售不能再领取。`, '领取确认', {
      confirmButtonText: '确认领取',
      type: 'warning'
    })
    await runAction({
      successMessage: '已领取到我的销售池',
      errorMessage: '领取失败，请刷新后重试',
      task: () => claimClue(row.customerCode)
    })
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
  const saved = await runAction({
    errorMessage: '分配失败',
    successMessage: '已分配销售',
    task: () => assignClue(currentRow.value.customerCode, { ...assignForm })
  })
  saving.value = false
  if (saved !== undefined) {
    assignVisible.value = false
    await fetchRows()
  }
}

async function releaseRow(row) {
  try {
    const { value } = await promptAction(`确认将客户 ${row.customerCode} 释放回销售公共池吗？释放后该线索会从你的销售池移出，其他销售可以重新领取。`, '释放确认', {
      confirmButtonText: '确认释放回公共池',
      cancelButtonText: '再想想',
      type: 'warning',
      inputType: 'textarea',
      inputPlaceholder: '可填写释放原因',
      closeOnClickModal: false,
      closeOnPressEscape: false
    })
    await releaseClue(row.customerCode, { remark: value || '释放回公共池' })
    await showSuccess('已释放回销售公共池')
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

function updateNotificationSound(enabled) {
  setNotificationSoundEnabled(enabled)
  if (enabled) setupNotificationSoundUnlock()
}

function handleRealtimeEvent(event) {
  if (!event?.targets?.includes('ASSIGN')) return
  scheduleRealtimeRefresh()
  if (poolType.value === 'PUBLIC' && ['CLUE_CREATED', 'ASSIGN_RELEASED'].includes(event.type)) {
    playNewDataSound()
    ElNotification({
      title: '有新待处理客资',
      message: event.message || '公共待分配池有新数据',
      type: 'success',
      duration: 3500
    })
  }
}

function scheduleRealtimeRefresh() {
  clearTimeout(realtimeRefreshTimer)
  realtimeRefreshTimer = setTimeout(() => {
    fetchRows()
  }, 300)
}

onMounted(() => {
  setupNotificationSoundUnlock()
  unsubscribeRealtime = subscribeRealtime(handleRealtimeEvent)
  fetchRows()
  window.addEventListener('crm-toggle-mobile-search', toggleMobileFilter)
  window.addEventListener('scroll', handleMobileScroll, { passive: true })
})

onBeforeUnmount(() => {
  clearTimeout(realtimeRefreshTimer)
  unsubscribeRealtime?.()
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
.desktop-sound-toggle {
  align-items: center;
  color: var(--text-muted);
  display: flex;
  font-weight: 800;
  gap: 8px;
  margin-left: auto;
}

.pool-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0 0 14px;
}

.pool-tab {
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(178, 174, 250, 0.48);
  border-radius: 999px;
  color: var(--text-muted);
  cursor: pointer;
  font-weight: 800;
  padding: 9px 16px;
}

.pool-tab.active {
  background: linear-gradient(90deg, var(--violet), var(--brand));
  border-color: transparent;
  box-shadow: 0 10px 24px rgba(75, 75, 254, 0.18);
  color: #fff;
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
  align-items: flex-start;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.card-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-start;
}

.contact-line {
  color: var(--text-strong);
  font-size: 16px;
  line-height: 1.45;
  margin: 0;
  word-break: break-all;
}

.compact-meta {
  color: var(--text-muted);
}

.assign-log-card {
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(178, 174, 250, 0.34);
  border-radius: 14px;
  display: grid;
  gap: 6px;
  padding: 12px 14px;
}

.assign-log-card strong {
  color: var(--text-strong);
}

.assign-log-card span,
.assign-log-card p {
  color: var(--text-muted);
  margin: 0;
}

@media (max-width: 760px) {
  .desktop-sound-toggle {
    display: none;
  }

  .desktop-table {
    display: none;
  }

  .pool-tabs {
    gap: 8px;
    margin-bottom: 12px;
  }

  .pool-tab {
    flex: 1;
    font-size: 13px;
    min-width: 0;
    padding: 10px 8px;
  }

  .clue-card {
    padding: 16px;
  }

  .mobile-load-state {
    color: var(--text-muted);
    font-size: 13px;
    padding: 4px 0 16px;
    text-align: center;
  }

  .mobile-loading-panel {
    background: rgba(255, 255, 255, 0.82);
    border-radius: 18px;
    box-shadow: var(--shadow-soft);
    display: grid;
    gap: 10px;
    padding: 18px;
  }

  .loading-line {
    animation: loading-shimmer 1.2s linear infinite;
    background: linear-gradient(90deg, rgba(178, 174, 250, 0.22), rgba(255, 255, 255, 0.78), rgba(178, 174, 250, 0.22));
    background-size: 220% 100%;
    border-radius: 999px;
    height: 14px;
  }

  .loading-line.strong {
    height: 18px;
    width: 72%;
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

@media (min-width: 761px) {
  .mobile-list {
    display: none;
  }
}
</style>
