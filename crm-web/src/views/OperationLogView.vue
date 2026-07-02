<template>
  <section>
    <div class="toolbar">
      <div class="page-title">操作日志</div>
    </div>

    <FilterPanel v-model:expanded="filterExpanded">
      <el-input v-model="filters.customerCode" class="filter-field" clearable placeholder="客户编号" />
      <el-input v-model="filters.operator" class="filter-field" clearable placeholder="操作人/工号" />
      <el-select v-model="filters.field" class="filter-field" clearable placeholder="修改字段">
        <el-option v-for="item in fieldOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <template #advanced>
        <el-date-picker v-model="dateRange" class="date-range" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
      </template>
      <template #actions>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      </template>
    </FilterPanel>

    <div class="panel desktop-table">
      <el-table :data="rows" width="100%" v-loading="loading">
        <el-table-column prop="createdAt" label="操作时间" min-width="150" />
        <el-table-column prop="customerCode" label="客户编号" min-width="130" />
        <el-table-column label="操作人" min-width="150">
          <template #default="{ row }">{{ userText(row.operator, row.operatorCode) }}</template>
        </el-table-column>
        <el-table-column label="修改字段" min-width="130">
          <template #default="{ row }">{{ row.fieldText || fieldText(row.field) }}</template>
        </el-table-column>
        <el-table-column prop="oldValue" label="修改前" min-width="180" show-overflow-tooltip />
        <el-table-column prop="newValue" label="修改后" min-width="180" show-overflow-tooltip />
        <el-table-column label="当前归属" min-width="190">
          <template #default="{ row }">
            运营：{{ row.uploader || '-' }} / 销售：{{ row.assignedSales || '未分配' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <TextActions>
              <button class="table-action" type="button" @click="goDetail(row)">详情</button>
            </TextActions>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" :total="total" />
    </div>

    <div class="mobile-list" v-loading="loading">
      <article v-for="row in rows" :key="`${row.customerCode}-${row.createdAt}-${row.field}`" class="operation-log-card">
        <div class="card-head">
          <strong>{{ row.customerCode }}</strong>
          <el-tag>{{ row.fieldText || fieldText(row.field) }}</el-tag>
        </div>
        <p>{{ userText(row.operator, row.operatorCode) }} · {{ row.createdAt }}</p>
        <small>修改前：{{ row.oldValue || '-' }}</small>
        <small>修改后：{{ row.newValue || '-' }}</small>
        <small>运营：{{ row.uploader || '-' }} / 销售：{{ row.assignedSales || '未分配' }}</small>
        <TextActions class="card-actions">
          <button class="table-action" type="button" @click="goDetail(row)">详情</button>
        </TextActions>
      </article>
      <el-empty v-if="!loading && rows.length === 0" description="暂无操作日志" />
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" compact :total="total" />
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { listOperationLogs } from '../api/clue'
import AppPagination from '../components/AppPagination.vue'
import FilterPanel from '../components/FilterPanel.vue'
import TextActions from '../components/TextActions.vue'

const router = useRouter()
const loading = ref(false)
const filterExpanded = ref(false)
const rows = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })
const filters = reactive({ customerCode: '', operator: '', field: '' })
const dateRange = ref(defaultMonthRange())
const fieldOptions = [
  { label: '来源平台', value: 'sourcePlatform' },
  { label: '客户联系方式', value: 'contactInfo' },
  { label: '是否有微信号', value: 'hasWechatId' },
  { label: '当前状态', value: 'status' },
  { label: '销售归属', value: 'assignedSales' },
  { label: '备注', value: 'remark' },
  { label: '定金金额', value: 'depositAmount' },
  { label: '状态备注', value: 'statusRemark' },
  { label: '抖音截图数量', value: 'douyinImages' },
  { label: '微信截图数量', value: 'wechatImages' },
  { label: '退单金额', value: 'refundAmount' },
  { label: '退款时间', value: 'refundedAt' },
  { label: '落地时间', value: 'landingAt' },
  { label: '落地备注', value: 'landingRemark' }
]

async function fetchRows() {
  loading.value = true
  try {
    const res = await listOperationLogs(queryParams())
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    await showError(error.message || '操作日志加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.current = 1
  fetchRows()
}

function queryParams() {
  return {
    ...Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '')),
    startDate: dateRange.value?.[0],
    endDate: dateRange.value?.[1],
    page: page.current,
    pageSize: page.size
  }
}

function goDetail(row) {
  router.push(`/clues/${row.customerCode}`)
}

function userText(name, code) {
  if (!name && !code) return '-'
  return `${name || '-'}（${code || '-'}）`
}

function fieldText(field) {
  return fieldOptions.find((item) => item.value === field)?.label || field || '-'
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

function showError(message) {
  return ElMessageBox.alert(message, '提示', { confirmButtonText: '我知道了', type: 'warning' })
}

watch([() => page.current, () => page.size], fetchRows)

onMounted(fetchRows)
</script>

<style scoped>
.date-range {
  width: 100%;
}

.operation-log-card {
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

.operation-log-card p,
.operation-log-card small {
  margin: 0;
}

.operation-log-card small {
  color: var(--text-muted);
}
</style>
