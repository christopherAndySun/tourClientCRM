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
      <el-table :data="rows" width="100%" v-loading="loading">
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
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <TextActions>
              <button class="table-action" type="button" @click="openDetail(row)">详情</button>
              <button class="table-action" type="button" :disabled="downloadingCode === row.customerCode" @click="downloadRow(row)">
                {{ downloadingCode === row.customerCode ? '下载中' : '下载' }}
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
        </TextActions>
      </article>
      <el-empty v-if="!loading && !rows.length" :description="activeTab === 'pending' ? '暂无待下载客资' : '暂无已下载记录'" />
      <AppPagination v-if="rows.length" v-model:current-page="page.current" v-model:page-size="page.size" :total="total" compact />
    </div>
  </section>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { getClue } from '../api/clue'
import { listThirdPartyDownloaded, listThirdPartyPending, markThirdPartyDownloaded } from '../api/thirdPartyDownload'
import AppPagination from '../components/AppPagination.vue'
import FilterPanel from '../components/FilterPanel.vue'
import StatusTag from '../components/StatusTag.vue'
import TextActions from '../components/TextActions.vue'
import { downloadClueWordFile } from '../utils/clueWord'
import {
  isNotificationSoundEnabled,
  playNewDataSound,
  setNotificationSoundEnabled,
  setupNotificationSoundUnlock
} from '../utils/notificationSound'
import { subscribeRealtime } from '../utils/realtime'
import { addMethodText, sourcePlatformText } from '../utils/status'

const router = useRouter()
const activeTab = ref('pending')
const filterExpanded = ref(false)
const mobileFilterVisible = ref(false)
const loading = ref(false)
const downloadingCode = ref('')
const rows = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })
const dateRange = ref([])
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
let unsubscribeRealtime
let realtimeRefreshTimer

async function fetchRows() {
  loading.value = true
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
    rows.value = payload.records.map(normalizeRow)
    total.value = payload.total
  } finally {
    loading.value = false
  }
}

function normalizePage(data) {
  return {
    records: data?.records || [],
    total: data?.total || 0
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

function handleTabChange() {
  page.current = 1
  fetchRows()
}

function openDetail(row) {
  router.push(`/clues/${row.customerCode}`)
}

async function downloadRow(row) {
  downloadingCode.value = row.customerCode
  try {
    ElMessage.info('正在生成 Word 文档...')
    const res = await getClue(row.customerCode)
    await downloadClueWordFile(res.data)
    if (activeTab.value === 'pending') {
      await markThirdPartyDownloaded(row.customerCode)
    }
    ElMessage.success(activeTab.value === 'pending' ? 'Word 文档已生成，客资已移入已下载列表' : 'Word 文档已生成')
    await fetchRows()
  } catch (error) {
    await showError(error.message || '下载 Word 失败')
  } finally {
    downloadingCode.value = ''
  }
}

function userLabel(name, code) {
  if (name && code) return `${name}（${code}）`
  return name || code || '-'
}

function showError(message) {
  return ElMessageBox.alert(message, '提示', {
    confirmButtonText: '我知道了',
    type: 'warning'
  })
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

watch(() => [page.current, page.size], fetchRows)

onMounted(() => {
  setupNotificationSoundUnlock()
  unsubscribeRealtime = subscribeRealtime(handleRealtimeEvent)
  fetchRows()
  window.addEventListener('crm-toggle-mobile-search', toggleMobileFilter)
})

onBeforeUnmount(() => {
  clearTimeout(realtimeRefreshTimer)
  unsubscribeRealtime?.()
  window.removeEventListener('crm-toggle-mobile-search', toggleMobileFilter)
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
