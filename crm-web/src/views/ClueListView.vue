<template>
  <section>
    <div class="toolbar">
      <div class="page-title">客户线索</div>
      <el-button v-if="canCreateClue" class="desktop-create" type="primary" @click="$router.push('/clues/create')">新增客户</el-button>
    </div>

    <FilterPanel v-model:expanded="filterExpanded" :mobile-open="mobileFilterVisible">
      <el-input v-model="filters.customerCode" class="filter-field" clearable placeholder="客户编号" />
      <el-input v-model="filters.contactInfo" class="filter-field" clearable placeholder="联系方式" />
      <el-select v-model="filters.status" class="filter-field" clearable placeholder="当前状态">
        <el-option label="新录入" value="NEW" />
        <el-option label="跟进中" value="FOLLOWING" />
        <el-option label="已交定金" value="DEPOSIT_PAID" />
        <el-option label="退单" value="REFUNDED" />
        <el-option label="已落地" value="LANDED" />
        <el-option label="无效用户" value="INVALID" />
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
        <el-input v-model="filters.uploader" class="filter-field" clearable placeholder="上传运营/编号" />
        <el-input v-model="filters.assignedSales" class="filter-field" clearable placeholder="分配销售/编号" />
        <el-input v-model="filters.keyword" class="filter-field" clearable placeholder="备注等模糊搜索" />
      </template>
      <template #actions>
        <el-button type="primary" :loading="loading" @click="searchRows">查询</el-button>
        <el-button :loading="exporting" @click="downloadExcel">导出 Excel</el-button>
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
        <el-table-column prop="uploader" label="上传运营" min-width="100" />
        <el-table-column label="分配销售" min-width="120">
          <template #default="{ row }">{{ row.assignedSales || '-' }}</template>
        </el-table-column>
        <el-table-column label="需求次数" min-width="96">
          <template #default="{ row }">第 {{ row.demandSequence || 1 }} 次</template>
        </el-table-column>
        <el-table-column label="当前状态" min-width="210">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
            <el-tag v-if="row.repeatDemand" class="status-extra" type="success">老客新需求</el-tag>
            <el-tag v-if="row.depositAmount" class="status-extra" type="warning">定金 {{ row.depositAmount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" min-width="160" />
        <el-table-column label="操作" width="156" fixed="right">
          <template #default="{ row }">
            <TextActions>
              <button class="table-action" type="button" @click="editRow(row)">详情/编辑</button>
              <button class="table-action danger" type="button" @click="removeRow(row)">删除</button>
            </TextActions>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" :total="total" />
    </div>

    <div ref="mobileListRef" class="mobile-list" v-loading="loading">
      <div v-if="loading && !rows.length" class="mobile-loading-panel">
        <div class="loading-line strong"></div>
        <div class="loading-line"></div>
        <div class="loading-line short"></div>
      </div>
      <article v-for="row in mobileRows" :key="row.customerCode" class="clue-card">
        <div class="card-head">
          <strong>{{ row.customerCode }}</strong>
          <div class="tag-wrap">
            <StatusTag :status="row.status" />
            <el-tag v-if="row.repeatDemand" type="success">老客新需求</el-tag>
          </div>
        </div>
        <p class="contact-line">{{ row.contactInfo || '客户联系方式待补充' }}</p>
        <small class="compact-meta">
          {{ sourcePlatformText(row.sourcePlatform) }} · {{ row.uploader || '-' }} · 销售：{{ row.assignedSales || '-' }} · {{ row.createdAt }}
        </small>
        <TextActions class="card-actions">
          <button class="table-action" type="button" @click="editRow(row)">详情/编辑</button>
          <button class="table-action danger" type="button" @click="removeRow(row)">删除</button>
        </TextActions>
      </article>
      <div v-if="rows.length" class="mobile-load-state">
        <span v-if="loadingMore">加载中...</span>
        <span v-else-if="hasMore">上滑加载更多</span>
        <span v-else>已经到底了</span>
      </div>
      <el-empty v-if="!loading && !rows.length" description="暂无客户线索" />
    </div>

    <button
      v-if="canCreateClue"
      class="mobile-fab"
      type="button"
      :style="fabStyle"
      aria-label="新增客户"
      @click="handleFabClick"
      @pointerdown="startFabDrag"
    >
      +
    </button>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteClue, exportClues, listClues } from '../api/clue'
import { downloadBlob, todayFilename } from '../utils/download'
import AppPagination from '../components/AppPagination.vue'
import FilterPanel from '../components/FilterPanel.vue'
import StatusTag from '../components/StatusTag.vue'
import TextActions from '../components/TextActions.vue'
import { useAuthStore } from '../stores/auth'
import { sourcePlatformText } from '../utils/status'

const router = useRouter()
const authStore = useAuthStore()
const filters = reactive({
  customerCode: '',
  contactInfo: '',
  status: '',
  sourcePlatform: '',
  uploader: '',
  assignedSales: '',
  keyword: ''
})
const filterExpanded = ref(false)
const dateRange = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const exporting = ref(false)
const rows = ref([])
const total = ref(0)
const hasMore = ref(false)
const page = reactive({ current: 1, size: 10 })
const mobileListRef = ref()
const mobileFilterVisible = ref(false)
const fabMoved = ref(false)
const fabPosition = reactive(loadFabPosition())
const fabDrag = reactive({ active: false, startX: 0, startY: 0, offsetX: 0, offsetY: 0 })
const fabStyle = computed(() => ({
  left: `${fabPosition.x}px`,
  top: `${fabPosition.y}px`
}))

const canCreateClue = computed(() => {
  return authStore.user?.role === 'ADMIN' || authStore.user?.menuPermissions?.includes('CLUE_CREATE')
})
const desktopRows = computed(() => rows.value)
const mobileRows = computed(() => rows.value)

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
    const res = await listClues({
      ...filterParams(),
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      page: page.current,
      pageSize: page.size
    })
    const payload = normalizePage(res.data)
    rows.value = append ? [...rows.value, ...payload.records] : payload.records
    total.value = payload.total
    hasMore.value = payload.hasMore
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function searchRows() {
  if (page.current === 1) {
    fetchRows()
    return
  }
  page.current = 1
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

async function downloadExcel() {
  exporting.value = true
  ElMessage.info('????????????...')
  try {
    const blob = await exportClues({
      ...filterParams(),
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1]
    })
    downloadBlob(blob, todayFilename('????'))
    ElMessage.success('?????????')
  } catch (error) {
    await showError(error.message || '??????????????')
  } finally {
    exporting.value = false
  }
}

function filterParams() {
  return Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== ''))
}

function editRow(row) {
  router.push(`/clues/${row.customerCode}`)
}

async function removeRow(row) {
  try {
    await ElMessageBox.confirm(`确认删除客户 ${row.customerCode} 吗？`, '删除确认', {
      confirmButtonText: '继续删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await ElMessageBox.confirm(`客户 ${row.customerCode} 删除后不可恢复，请再次确认。`, '二次确认', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'error'
    })
    await deleteClue(row.customerCode)
    ElMessage.success('已删除')
    await fetchRows()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      await showError(error.message || '删除失败')
    }
  }
}

function showError(message) {
  return ElMessageBox.alert(message, '提示', {
    confirmButtonText: '我知道了',
    type: 'warning'
  })
}

onMounted(() => {
  fetchRows()
  window.addEventListener('crm-toggle-mobile-search', toggleMobileFilter)
  window.addEventListener('scroll', handleMobileScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('crm-toggle-mobile-search', toggleMobileFilter)
  window.removeEventListener('scroll', handleMobileScroll)
  stopFabDrag()
})

function toggleMobileFilter() {
  mobileFilterVisible.value = !mobileFilterVisible.value
}

function handleMobileScroll() {
  if (!window.matchMedia('(max-width: 760px)').matches) return
  if (!hasMore.value || loading.value || loadingMore.value) return
  const nearBottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 180
  if (nearBottom) {
    page.current += 1
    fetchRows({ append: true })
  }
}

function handleFabClick() {
  if (fabMoved.value) {
    fabMoved.value = false
    return
  }
  router.push('/clues/create')
}

function startFabDrag(event) {
  if (!window.matchMedia('(max-width: 760px)').matches) return
  fabDrag.active = true
  fabMoved.value = false
  fabDrag.startX = event.clientX
  fabDrag.startY = event.clientY
  fabDrag.offsetX = event.clientX - fabPosition.x
  fabDrag.offsetY = event.clientY - fabPosition.y
  event.currentTarget.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', moveFab)
  window.addEventListener('pointerup', stopFabDrag)
}

function moveFab(event) {
  if (!fabDrag.active) return
  const nextX = event.clientX - fabDrag.offsetX
  const nextY = event.clientY - fabDrag.offsetY
  const maxX = window.innerWidth - 72
  const maxY = window.innerHeight - 92
  fabPosition.x = Math.min(Math.max(12, nextX), maxX)
  fabPosition.y = Math.min(Math.max(84, nextY), maxY)
  if (Math.abs(event.clientX - fabDrag.startX) > 4 || Math.abs(event.clientY - fabDrag.startY) > 4) {
    fabMoved.value = true
  }
}

function stopFabDrag() {
  if (!fabDrag.active) return
  fabDrag.active = false
  localStorage.setItem('crm_clue_fab_position', JSON.stringify(fabPosition))
  window.removeEventListener('pointermove', moveFab)
  window.removeEventListener('pointerup', stopFabDrag)
}

function loadFabPosition() {
  const saved = JSON.parse(localStorage.getItem('crm_clue_fab_position') || 'null')
  if (saved?.x && saved?.y) return saved
  return { x: Math.max(12, window.innerWidth - 78), y: Math.max(110, window.innerHeight - 168) }
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
</script>

<style scoped>
.date-range {
  width: 100%;
  max-width: 100%;
}

.status-extra {
  margin-left: 6px;
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
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

.card-actions {
  display: flex;
  justify-content: flex-start;
  gap: 8px;
}

@media (max-width: 760px) {
  .desktop-create {
    display: none;
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

  .mobile-fab {
    position: fixed;
    z-index: 24;
    display: grid;
    width: 58px;
    height: 58px;
    place-content: center;
    border: 0;
    border-radius: 22px;
    background: linear-gradient(135deg, var(--violet), var(--brand));
    color: #fff;
    font-size: 38px;
    font-weight: 400;
    line-height: 1;
    box-shadow: 0 16px 34px rgba(75, 75, 254, 0.32);
    touch-action: none;
  }

}

@media (min-width: 761px) {
  .mobile-fab {
    display: none;
  }
}
</style>
