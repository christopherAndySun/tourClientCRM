<template>
  <section>
    <div class="toolbar">
      <div class="page-title">三方下载池</div>
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
      <el-button
        v-if="activeTab === 'pending'"
        class="desktop-batch-button"
        type="primary"
        :disabled="!selectedRows.length || batchDownloading"
        :loading="batchDownloading"
        @click="batchDownload"
      >
        批量下载
      </el-button>
      <el-button class="desktop-batch-button" :loading="failureLoading" @click="openFailureQueue">
        失败队列
      </el-button>
    </div>

    <el-alert
      class="page-tip"
      title="这是第三方临时交接使用的下载池。下载后只会写入独立的已下载记录，不会修改客户状态、分配关系或绩效数据。"
      type="info"
      show-icon
      :closable="false"
    />

    <el-tabs v-model="activeTab" class="download-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="公共池" name="pending" />
      <el-tab-pane label="已下载" name="downloaded" />
    </el-tabs>

    <FilterPanel v-model:expanded="filterExpanded" :mobile-open="mobileFilterVisible">
      <el-input v-model="filters.customerCode" class="filter-field" clearable placeholder="客户编号" />
      <el-input v-model="filters.contactInfo" class="filter-field" clearable placeholder="联系方式" />
      <el-select v-model="filters.status" class="filter-field" clearable placeholder="当前状态">
        <el-option label="新录入" value="NEW" />
        <el-option label="跟进中" value="FOLLOWING" />
        <el-option label="已通过" value="PASSED" />
        <el-option label="无效用户" value="INVALID" />
        <el-option label="已交定金" value="DEPOSIT_PAID" />
        <el-option label="退单" value="REFUNDED" />
        <el-option label="已落地" value="LANDED" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        class="date-range filter-field"
        type="daterange"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
      />
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
        <el-input v-model="filters.uploader" class="filter-field" clearable placeholder="上传运营/编号" />
        <el-input v-model="filters.assignedSales" class="filter-field" clearable placeholder="分配销售/编号" />
        <el-input v-model="filters.keyword" class="filter-field" clearable placeholder="备注等模糊搜索" />
      </template>
      <template #actions>
        <el-button type="primary" :loading="loading" @click="searchRows">查询</el-button>
      </template>
    </FilterPanel>

    <div class="panel desktop-table">
      <el-table :data="rows" width="100%" v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column v-if="activeTab === 'pending'" type="selection" width="48" />
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
        <el-table-column label="分配销售" min-width="120">
          <template #default="{ row }">{{ row.assignedSales || '-' }}</template>
        </el-table-column>
        <el-table-column label="当前状态" min-width="150">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'downloaded'" prop="downloadedAt" label="下载时间" min-width="150" />
        <el-table-column v-if="activeTab === 'downloaded'" label="下载人" min-width="120">
          <template #default="{ row }">{{ userLabel(row.downloadedBy, row.downloadedByCode) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" min-width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <TextActions>
              <button class="table-action" type="button" @click="openDetail(row)">详情</button>
              <button class="table-action" type="button" :disabled="downloadingCode === row.customerCode" @click="downloadRow(row)">
                {{ downloadingCode === row.customerCode ? '下载中' : '下载' }}
              </button>
              <button v-if="activeTab === 'downloaded'" class="table-action" type="button" :disabled="restoringCode === row.customerCode" @click="restoreRow(row)">
                {{ restoringCode === row.customerCode ? '处理中' : '放回公共池' }}
              </button>
            </TextActions>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" :total="total" />
    </div>

    <div class="mobile-list" v-loading="loading">
      <article v-for="row in rows" :key="row.customerCode" class="download-card">
        <div class="card-head">
          <strong>{{ row.customerCode }}</strong>
          <StatusTag :status="row.status" />
        </div>
        <p class="contact-line">{{ row.contactInfo || '客户联系方式待补充' }}</p>
        <small>
          {{ sourcePlatformText(row.sourcePlatform) }} · {{ addMethodText(row.addMethod) }} · 运营：{{ row.uploader || '-' }} · 销售：{{ row.assignedSales || '-' }}
        </small>
        <small v-if="activeTab === 'downloaded'">
          下载：{{ row.downloadedAt || '-' }} · {{ userLabel(row.downloadedBy, row.downloadedByCode) }}
        </small>
        <TextActions class="card-actions">
          <button class="table-action" type="button" @click="openDetail(row)">详情</button>
          <button class="table-action" type="button" :disabled="downloadingCode === row.customerCode" @click="downloadRow(row)">
            {{ downloadingCode === row.customerCode ? '下载中' : '下载' }}
          </button>
          <button v-if="activeTab === 'downloaded'" class="table-action" type="button" :disabled="restoringCode === row.customerCode" @click="restoreRow(row)">
            {{ restoringCode === row.customerCode ? '处理中' : '放回公共池' }}
          </button>
        </TextActions>
      </article>
      <div v-if="rows.length" class="mobile-load-state">
        <span v-if="loadingMore">加载中...</span>
        <span v-else-if="hasMore">上滑加载更多</span>
        <span v-else>已经到底了</span>
      </div>
      <el-empty v-if="!loading && !rows.length" :description="activeTab === 'pending' ? '暂无待下载客资' : '暂无已下载记录'" />
    </div>

    <el-dialog v-model="failureVisible" title="下载失败队列" width="min(980px, 94vw)">
      <FilterPanel :collapsible="false">
        <el-input v-model="failureFilters.customerCode" class="filter-field" clearable placeholder="客户编号" />
        <el-input v-model="failureFilters.operator" class="filter-field" clearable placeholder="操作人/编号" />
        <el-date-picker
          v-model="failureDateRange"
          class="date-range filter-field"
          type="daterange"
          start-placeholder="失败开始日期"
          end-placeholder="失败结束日期"
          value-format="YYYY-MM-DD"
        />
        <template #actions>
          <el-button type="primary" :loading="failureLoading" @click="searchFailures">查询</el-button>
          <el-button :loading="failureExporting" @click="downloadFailureExcel">导出失败明细</el-button>
        </template>
      </FilterPanel>
      <el-table :data="failureRows" width="100%" v-loading="failureLoading">
        <el-table-column prop="customerCode" label="客户编号" min-width="130" />
        <el-table-column label="联系方式" min-width="150">
          <template #default="{ row }">{{ row.contactInfo || '待补充' }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" min-width="110" />
        <el-table-column prop="remark" label="失败原因" min-width="220" show-overflow-tooltip />
        <el-table-column prop="failedAt" label="失败时间" min-width="150" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <TextActions>
              <button class="table-action" type="button" :disabled="downloadingCode === row.customerCode" @click="retryFailure(row)">
                {{ downloadingCode === row.customerCode ? '重试中' : '重试下载' }}
              </button>
              <button class="table-action" type="button" @click="openDetail(row)">详情</button>
            </TextActions>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current-page="failurePage.current" v-model:page-size="failurePage.size" :total="failureTotal" />
    </el-dialog>
  </section>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'
import { downloadClueWord } from '../api/clue'
import {
  exportThirdPartyFailures,
  listThirdPartyFailures,
  listThirdPartyDownloaded,
  listThirdPartyPending,
  markThirdPartyDownloaded,
  recordThirdPartyDownloadFailure,
  restoreThirdPartyPending
} from '../api/thirdPartyDownload'
import AppPagination from '../components/AppPagination.vue'
import FilterPanel from '../components/FilterPanel.vue'
import StatusTag from '../components/StatusTag.vue'
import TextActions from '../components/TextActions.vue'
import { downloadBlob, todayFilename } from '../utils/download'
import {
  isNotificationSoundEnabled,
  playNewDataSound,
  setNotificationSoundEnabled,
  setupNotificationSoundUnlock
} from '../utils/notificationSound'
import { subscribeRealtime } from '../utils/realtime'
import { addMethodText, sourcePlatformText } from '../utils/status'
import { confirmAction, runAction, showError, showSuccess } from '../utils/feedback'

const router = useRouter()
const activeTab = ref('pending')
const filterExpanded = ref(false)
const mobileFilterVisible = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const batchDownloading = ref(false)
const failureVisible = ref(false)
const failureLoading = ref(false)
const failureExporting = ref(false)
const downloadingCode = ref('')
const restoringCode = ref('')
const rows = ref([])
const selectedRows = ref([])
const total = ref(0)
const hasMore = ref(false)
const page = reactive({ current: 1, size: 10 })
const failurePage = reactive({ current: 1, size: 10 })
const dateRange = ref([])
const failureDateRange = ref([])
const failureRows = ref([])
const failureTotal = ref(0)
const notificationSoundEnabled = ref(isNotificationSoundEnabled())
const filters = reactive({
  customerCode: '',
  contactInfo: '',
  status: '',
  sourcePlatform: '',
  addMethod: '',
  uploader: '',
  assignedSales: '',
  keyword: ''
})
const failureFilters = reactive({ customerCode: '', operator: '' })
let unsubscribeRealtime
let realtimeRefreshTimer

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
    const api = activeTab.value === 'pending' ? listThirdPartyPending : listThirdPartyDownloaded
    const res = await api({
      ...filterParams(),
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      page: page.current,
      pageSize: page.size
    })
    const payload = normalizePage(res.data)
    const nextRows = payload.records.map(normalizeRow)
    rows.value = append ? [...rows.value, ...nextRows] : nextRows
    total.value = payload.total
    hasMore.value = payload.hasMore
  } catch (error) {
    await showError(error.message || '三方下载池加载失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function normalizePage(data) {
  return {
    records: data?.records || [],
    total: data?.total || 0,
    hasMore: Boolean(data?.hasMore)
  }
}

function normalizeRow(item) {
  return {
    ...(item?.clue || item || {}),
    downloadedBy: item?.downloadedBy || '',
    downloadedByCode: item?.downloadedByCode || '',
    downloadedAt: item?.downloadedAt || ''
  }
}

function filterParams() {
  return Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== ''))
}

function searchRows() {
  if (page.current === 1) {
    fetchRows()
    return
  }
  page.current = 1
}

async function openFailureQueue() {
  failureVisible.value = true
  await fetchFailures()
}

async function fetchFailures() {
  failureLoading.value = true
  try {
    const res = await listThirdPartyFailures({
      ...Object.fromEntries(Object.entries(failureFilters).filter(([, value]) => value !== '')),
      startDate: failureDateRange.value?.[0],
      endDate: failureDateRange.value?.[1],
      page: failurePage.current,
      pageSize: failurePage.size
    })
    const payload = normalizePage(res.data)
    failureRows.value = payload.records
    failureTotal.value = payload.total
  } catch (error) {
    await showError(error.message || '失败队列加载失败')
  } finally {
    failureLoading.value = false
  }
}

function searchFailures() {
  if (failurePage.current === 1) {
    fetchFailures()
    return
  }
  failurePage.current = 1
}

async function downloadFailureExcel() {
  await runAction({
    loadingRef: failureExporting,
    loadingMessage: '正在导出失败明细...',
    successMessage: '失败明细已导出',
    errorMessage: '失败明细导出失败',
    task: async () => {
      const blob = await exportThirdPartyFailures({
        ...Object.fromEntries(Object.entries(failureFilters).filter(([, value]) => value !== '')),
        startDate: failureDateRange.value?.[0],
        endDate: failureDateRange.value?.[1]
      })
      downloadBlob(blob, todayFilename('三方下载失败明细'))
    }
  })
}

async function retryFailure(row) {
  const success = await downloadAndMark(row, { refresh: true, markDownloadedAfter: true })
  if (success) {
    await fetchFailures()
  }
}

function handleTabChange() {
  page.current = 1
  rows.value = []
  selectedRows.value = []
  fetchRows()
}

function openDetail(row) {
  router.push(`/clues/${row.customerCode}`)
}

async function downloadRow(row) {
  await downloadAndMark(row, { refresh: true })
}

async function downloadAndMark(row, { refresh = false, markDownloadedAfter = activeTab.value === 'pending' } = {}) {
  downloadingCode.value = row.customerCode
  const success = await runAction({
    loadingMessage: '正在生成 Word 文档...',
    successMessage: activeTab.value === 'pending' ? 'Word 文档已生成，客资已移入已下载列表' : 'Word 文档已生成',
    errorMessage: '下载 Word 失败',
    onError: (error) => recordDownloadFailure(row.customerCode, error.message || '下载 Word 失败'),
    task: async () => {
      const blob = await downloadClueWord(row.customerCode)
      downloadBlob(blob, `${row.customerCode}.docx`)
      if (markDownloadedAfter) {
        await markThirdPartyDownloaded(row.customerCode)
      }
      return true
    }
  })
  downloadingCode.value = ''
  if (refresh) {
    await fetchRows()
  }
  return Boolean(success)
}

async function batchDownload() {
  if (!selectedRows.value.length) return
  batchDownloading.value = true
  let successCount = 0
  try {
    for (const row of selectedRows.value) {
      if (await downloadAndMark(row)) {
        successCount += 1
      }
    }
    await showSuccess(`批量下载完成，成功 ${successCount} 条，失败 ${selectedRows.value.length - successCount} 条`)
  } finally {
    batchDownloading.value = false
    selectedRows.value = []
    await fetchRows()
  }
}

async function restoreRow(row) {
  try {
    await confirmAction(`确认将 ${row.customerCode} 放回公共池吗？`, '放回公共池', {
      confirmButtonText: '确认放回',
      type: 'warning'
    })
  } catch {
    return
  }
  restoringCode.value = row.customerCode
  const restored = await runAction({
    successMessage: '已放回公共池',
    errorMessage: '放回公共池失败',
    task: () => restoreThirdPartyPending(row.customerCode)
  })
  restoringCode.value = ''
  if (restored !== undefined) {
    await fetchRows()
  }
}

async function recordDownloadFailure(customerCode, message) {
  try {
    await recordThirdPartyDownloadFailure(customerCode, message)
  } catch (error) {
    // 失败记录不阻塞前端提示，避免二次报错干扰运营处理。
  }
}

function handleSelectionChange(selection) {
  selectedRows.value = selection
}

function userLabel(name, code) {
  if (name && code) return `${name}（${code}）`
  return name || code || '-'
}

function updateNotificationSound(enabled) {
  setNotificationSoundEnabled(enabled)
  if (enabled) setupNotificationSoundUnlock()
}

function toggleMobileFilter() {
  mobileFilterVisible.value = !mobileFilterVisible.value
}

function handleRealtimeEvent(event) {
  if (!event?.targets?.includes('THIRD_PARTY_POOL')) return
  scheduleRealtimeRefresh()
  if (event.type === 'CLUE_CREATED' && activeTab.value === 'pending') {
    playNewDataSound()
    ElNotification({
      title: '有新客资',
      message: event.message || '三方下载池有新客资进入',
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

watch(() => page.size, () => {
  const wasFirstPage = page.current === 1
  page.current = 1
  if (wasFirstPage) fetchRows()
})

watch(() => page.current, (current, previous) => {
  if (window.matchMedia('(max-width: 760px)').matches) return
  if (current !== previous) fetchRows()
})

watch([() => failurePage.current, () => failurePage.size], () => {
  if (failureVisible.value) {
    fetchFailures()
  }
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

.desktop-batch-button {
  margin-left: 12px;
}

.page-tip {
  margin-bottom: 14px;
}

.download-tabs {
  margin-bottom: 12px;
}

.download-card {
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 18px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
  display: grid;
  gap: 8px;
  margin-bottom: 12px;
  padding: 14px;
}

.card-head {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.contact-line {
  color: #111827;
  font-size: 15px;
  margin: 0;
  word-break: break-all;
}

.download-card small {
  color: #64748b;
  line-height: 1.6;
}

.card-actions {
  padding-top: 4px;
}

@media (max-width: 760px) {
  .desktop-sound-toggle,
  .desktop-table {
    display: none;
  }

  .page-tip {
    border-radius: 14px;
  }
}

@media (min-width: 761px) {
  .mobile-list {
    display: none;
  }
}
</style>
