<template>
  <section>
    <div class="toolbar">
      <div class="page-title">成交记录</div>
    </div>

    <FilterPanel v-model:expanded="filterExpanded">
      <el-input v-model="filters.customerCode" class="filter-field" clearable placeholder="客户编号" />
      <el-input v-model="filters.customerName" class="filter-field" clearable placeholder="客户姓名" />
      <el-select v-model="salesEmployeeCode" class="sales-select" filterable placeholder="所有销售">
        <el-option label="所有销售" value="" />
        <el-option v-for="sales in salesUsers" :key="sales.employeeCode" :label="`${sales.name}（${sales.employeeCode}）`" :value="sales.employeeCode" />
      </el-select>
      <el-date-picker v-model="dateRange" class="date-range" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
      <template #advanced>
        <el-input v-model="filters.dealCode" class="filter-field" clearable placeholder="成交编号" />
        <el-select v-model="filters.status" class="filter-field" clearable placeholder="成交状态">
          <el-option label="已交定金" value="DEPOSIT_PAID" />
          <el-option label="退单" value="REFUNDED" />
          <el-option label="已落地" value="LANDED" />
        </el-select>
        <el-input v-model="filters.keyword" class="filter-field" clearable placeholder="行程/报价/退单备注" />
      </template>
      <template #actions>
        <el-button type="primary" :loading="loading" @click="fetchRows">查询</el-button>
        <el-button :loading="exporting" @click="downloadExcel">导出 Excel</el-button>
      </template>
    </FilterPanel>

    <div class="panel desktop-table">
      <el-table :data="pagedRows" width="100%" v-loading="loading">
        <el-table-column prop="dealCode" label="成交编号" min-width="110" />
        <el-table-column prop="customerCode" label="客户编号" min-width="130" />
        <el-table-column prop="customerName" label="客户姓名" min-width="120" />
        <el-table-column label="状态" min-width="100"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
        <el-table-column prop="deposit" label="定金/预付金" min-width="130" />
        <el-table-column prop="refundAmount" label="退单金额" min-width="110" />
        <el-table-column label="销售" min-width="150"><template #default="{ row }">{{ row.dealUser || '-' }}（{{ row.dealUserCode || '-' }}）</template></el-table-column>
        <el-table-column prop="dealDate" label="成交日期" min-width="120" />
        <el-table-column prop="travelDate" label="出行时间" min-width="140" />
        <el-table-column prop="itinerary" label="行程" min-width="180" show-overflow-tooltip />
        <el-table-column prop="refundRemark" label="退单备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <TextActions>
              <button class="table-action" type="button" @click="openDetail(row)">详情</button>
              <button v-if="!isAutoDeal(row)" class="table-action" type="button" @click="openEdit(row)">编辑</button>
              <button v-if="!isAutoDeal(row) && row.status !== 'REFUNDED'" class="table-action danger" type="button" @click="cancelRow(row)">退单</button>
            </TextActions>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" :total="rows.length" />
    </div>

    <div class="mobile-list">
      <article v-for="row in pagedRows" :key="row.dealCode" class="deal-card">
        <div class="card-head"><strong>{{ row.customerName }} · {{ row.customerCode }}</strong><StatusTag :status="row.status" /></div>
        <p>{{ row.itinerary || '暂无行程' }}</p>
        <small>{{ row.dealUser || '-' }}（{{ row.dealUserCode || '-' }}） · 定金：{{ row.deposit || '-' }} · {{ row.dealDate || '-' }}</small>
        <small v-if="row.status === 'REFUNDED'" class="refund-note">退单：{{ row.refundAmount || '-' }} · {{ row.refundRemark || '-' }} · {{ row.refundedAt || '-' }}</small>
        <small v-if="row.status === 'LANDED'">落地：{{ row.landingAt || '-' }} · {{ row.landingRemark || '-' }}</small>
        <TextActions class="card-actions">
          <button class="table-action" type="button" @click="openDetail(row)">详情</button>
          <button v-if="!isAutoDeal(row)" class="table-action" type="button" @click="openEdit(row)">编辑</button>
          <button v-if="!isAutoDeal(row) && row.status !== 'REFUNDED'" class="table-action danger" type="button" @click="cancelRow(row)">退单</button>
        </TextActions>
      </article>
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" compact :total="rows.length" />
    </div>

    <el-dialog v-model="detailVisible" title="成交详情" width="min(720px, 94vw)">
      <div v-if="currentDeal" class="deal-detail">
        <div class="detail-hero"><div><strong>{{ currentDeal.customerName }}</strong><span>{{ currentDeal.customerCode }} · {{ currentDeal.dealCode }}</span></div><StatusTag :status="currentDeal.status" /></div>
        <dl class="detail-grid">
          <div><dt>销售</dt><dd>{{ currentDeal.dealUser || '-' }}（{{ currentDeal.dealUserCode || '-' }}）</dd></div>
          <div><dt>状态</dt><dd>{{ statusText(currentDeal.status) }}</dd></div>
          <div><dt>定金/预付金</dt><dd>{{ currentDeal.deposit || '-' }}</dd></div>
          <div><dt>成交日期</dt><dd>{{ currentDeal.dealDate || '-' }}</dd></div>
          <div><dt>预定时间</dt><dd>{{ currentDeal.bookingDate || '-' }}</dd></div>
          <div><dt>加粉时间</dt><dd>{{ currentDeal.addWechatDate || '-' }}</dd></div>
          <div><dt>报价</dt><dd>{{ currentDeal.quoteText || '-' }}</dd></div>
          <div><dt>出行时间</dt><dd>{{ currentDeal.travelDate || '-' }}</dd></div>
          <div class="detail-wide"><dt>行程</dt><dd>{{ currentDeal.itinerary || '-' }}</dd></div>
          <div><dt>登记时间</dt><dd>{{ currentDeal.createdAt || '-' }}</dd></div>
          <div v-if="currentDeal.status === 'REFUNDED'"><dt>退单金额</dt><dd>{{ currentDeal.refundAmount || '-' }}</dd></div>
          <div v-if="currentDeal.status === 'REFUNDED'"><dt>退款时间</dt><dd>{{ currentDeal.refundedAt || '-' }}</dd></div>
          <div v-if="currentDeal.status === 'REFUNDED'" class="detail-wide"><dt>退单备注</dt><dd>{{ currentDeal.refundRemark || '-' }}</dd></div>
          <div v-if="currentDeal.status === 'LANDED'"><dt>落地时间</dt><dd>{{ currentDeal.landingAt || '-' }}</dd></div>
          <div v-if="currentDeal.status === 'LANDED'" class="detail-wide"><dt>落地备注</dt><dd>{{ currentDeal.landingRemark || '-' }}</dd></div>
        </dl>
      </div>
    </el-dialog>

    <el-dialog v-model="cancelVisible" title="退单信息" width="min(520px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="退单金额"><el-input v-model="cancelForm.refundAmount" placeholder="例如：200" /></el-form-item>
        <el-form-item label="退款时间"><el-date-picker v-model="cancelForm.refundedAt" class="full-field" type="datetime" value-format="YYYY-MM-DD HH:mm" placeholder="默认当前时间" /></el-form-item>
        <el-form-item label="退单备注"><el-input v-model="cancelForm.remark" type="textarea" :rows="3" placeholder="例如：客户取消行程，定金已退" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="cancelVisible = false">取消</el-button><el-button type="danger" :loading="cancelSaving" @click="submitCancel">确认退单</el-button></template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑成交记录" width="min(640px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="客户姓名"><el-input v-model="editForm.customerName" /></el-form-item>
        <el-form-item label="定金/预付金"><el-input v-model="editForm.deposit" /></el-form-item>
        <el-form-item label="预定时间"><el-date-picker v-model="editForm.bookingDate" class="full-field" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="加粉时间"><el-date-picker v-model="editForm.addWechatDate" class="full-field" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="报价"><el-input v-model="editForm.quoteText" /></el-form-item>
        <el-form-item label="出行时间"><el-input v-model="editForm.travelDate" /></el-form-item>
        <el-form-item label="行程"><el-input v-model="editForm.itinerary" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="成交日期"><el-date-picker v-model="editForm.dealDate" class="full-field" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitEdit">保存修改</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSalesCandidates } from '../api/auth'
import { cancelDeal, exportDeals, listDeals, updateDeal } from '../api/deal'
import AppPagination from '../components/AppPagination.vue'
import FilterPanel from '../components/FilterPanel.vue'
import StatusTag from '../components/StatusTag.vue'
import TextActions from '../components/TextActions.vue'
import { statusText } from '../utils/status'

const filters = reactive({ customerCode: '', customerName: '', dealCode: '', status: '', keyword: '' })
const filterExpanded = ref(false)
const salesEmployeeCode = ref('')
const dateRange = ref(defaultMonthRange())
const loading = ref(false)
const exporting = ref(false)
const saving = ref(false)
const cancelVisible = ref(false)
const cancelSaving = ref(false)
const cancelTarget = ref(null)
const rows = ref([])
const page = reactive({ current: 1, size: 10 })
const salesUsers = ref([])
const detailVisible = ref(false)
const editVisible = ref(false)
const currentDeal = ref(null)
const cancelForm = reactive({ remark: '', refundAmount: '', refundedAt: '' })
const editForm = reactive({ dealCode: '', customerCode: '', customerName: '', deposit: '', bookingDate: '', addWechatDate: '', quoteText: '', travelDate: '', itinerary: '', dealDate: '' })
const pagedRows = computed(() => rows.value.slice((page.current - 1) * page.size, (page.current - 1) * page.size + page.size))

async function fetchRows() {
  loading.value = true
  try {
    const res = await listDeals(queryParams())
    rows.value = res.data || []
    page.current = 1
  } catch (error) {
    await showError(error.message || '成交记录加载失败')
  } finally {
    loading.value = false
  }
}

async function fetchSalesUsers() {
  try {
    const res = await listSalesCandidates()
    salesUsers.value = res.data || []
  } catch (error) {
    salesUsers.value = []
  }
}

async function downloadExcel() {
  exporting.value = true
  try {
    const blob = await exportDeals(queryParams())
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `成交记录-${new Date().toISOString().slice(0, 10)}.xlsx`
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

function openDetail(row) { currentDeal.value = row; detailVisible.value = true }
function openEdit(row) { Object.assign(editForm, row); editVisible.value = true }

async function submitEdit() {
  saving.value = true
  try {
    await updateDeal(editForm.dealCode, { ...editForm })
    ElMessage.success('成交记录已更新')
    editVisible.value = false
    await fetchRows()
  } catch (error) {
    await showError(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function cancelRow(row) {
  try {
    await ElMessageBox.confirm(`确认将成交记录 ${row.dealCode} 标记为退单吗？`, '退单确认', { confirmButtonText: '继续退单', cancelButtonText: '取消', type: 'warning' })
    cancelTarget.value = row
    Object.assign(cancelForm, { remark: '', refundAmount: '', refundedAt: formatDateTime(new Date()) })
    cancelVisible.value = true
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') await showError(error.message || '退单失败')
  }
}

async function submitCancel() {
  if (!cancelTarget.value) return
  cancelSaving.value = true
  try {
    await cancelDeal(cancelTarget.value.dealCode, { remark: cancelForm.remark || '退单', refundAmount: cancelForm.refundAmount, refundedAt: cancelForm.refundedAt })
    ElMessage.success('已标记退单')
    cancelVisible.value = false
    cancelTarget.value = null
    await fetchRows()
  } catch (error) {
    await showError(error.message || '退单失败')
  } finally {
    cancelSaving.value = false
  }
}

function queryParams() { return { ...Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '')), salesEmployeeCode: salesEmployeeCode.value, startDate: dateRange.value?.[0], endDate: dateRange.value?.[1] } }
function isAutoDeal(row) { return row?.dealCode?.startsWith('AUTO-') }
function defaultMonthRange() { const now = new Date(); const start = new Date(now.getFullYear(), now.getMonth(), 1); return [formatDate(start), formatDate(now)] }
function formatDateTime(date) { const hours = String(date.getHours()).padStart(2, '0'); const minutes = String(date.getMinutes()).padStart(2, '0'); return `${formatDate(date)} ${hours}:${minutes}` }
function formatDate(date) { const year = date.getFullYear(); const month = String(date.getMonth() + 1).padStart(2, '0'); const day = String(date.getDate()).padStart(2, '0'); return `${year}-${month}-${day}` }
function showError(message) { return ElMessageBox.alert(message, '提示', { confirmButtonText: '我知道了', type: 'warning' }) }

watch([() => page.size, () => rows.value.length], () => { const maxPage = Math.max(1, Math.ceil(rows.value.length / page.size)); if (page.current > maxPage) page.current = maxPage })
onMounted(async () => { await Promise.all([fetchRows(), fetchSalesUsers()]) })
</script>
<style scoped>
.date-range,
.full-field,
.sales-select {
  width: 100%;
}

.deal-card {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.card-head,
.card-actions {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.card-actions {
  justify-content: flex-start;
}

.deal-card p {
  margin: 0;
}

.deal-card small {
  color: var(--text-muted);
}

.refund-note {
  color: var(--danger) !important;
}

.deal-detail {
  display: grid;
  gap: 16px;
}

.detail-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(178, 174, 250, 0.34);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.74);
}

.detail-hero strong,
.detail-hero span {
  display: block;
}

.detail-hero strong {
  color: var(--text-strong);
  font-size: 20px;
}

.detail-hero span {
  margin-top: 4px;
  color: var(--text-muted);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.detail-grid div {
  margin: 0;
  padding: 14px;
  border: 1px solid rgba(178, 174, 250, 0.28);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.68);
}

.detail-grid dt {
  margin-bottom: 6px;
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
}

.detail-grid dd {
  margin: 0;
  color: var(--text-main);
  line-height: 1.6;
}

.detail-wide {
  grid-column: 1 / -1;
}

@media (max-width: 760px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
