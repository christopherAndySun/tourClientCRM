<template>
  <section>
    <div class="toolbar">
      <div class="page-title">分配日志</div>
    </div>

    <FilterPanel v-model:expanded="filterExpanded">
      <el-input v-model="filters.customerCode" class="filter-field" clearable placeholder="客户编号" />
      <el-select v-model="filters.action" class="filter-field" clearable placeholder="操作类型">
        <el-option label="分配" value="ASSIGN" />
        <el-option label="转派" value="TRANSFER" />
        <el-option label="领取" value="CLAIM" />
        <el-option label="抢单冲突" value="CLAIM_CONFLICT" />
        <el-option label="释放" value="RELEASE" />
      </el-select>
      <el-date-picker v-model="dateRange" class="date-range" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
      <template #advanced>
        <el-input v-model="filters.operator" class="filter-field" clearable placeholder="操作人/工号" />
        <el-input v-model="filters.salesEmployeeCode" class="filter-field" clearable placeholder="销售工号" />
      </template>
      <template #actions>
        <el-button type="primary" :loading="loading" @click="fetchRows">查询</el-button>
      </template>
    </FilterPanel>

    <div class="panel desktop-table">
      <el-table :data="pagedRows" width="100%" v-loading="loading">
        <el-table-column prop="createdAt" label="操作时间" min-width="150" />
        <el-table-column prop="customerCode" label="客户编号" min-width="130" />
        <el-table-column label="动作" min-width="110">
          <template #default="{ row }">
            <el-tag :type="actionType(row.action)">{{ row.actionText || actionText(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作人" min-width="140">
          <template #default="{ row }">{{ row.operator || '-' }}（{{ row.operatorCode || '-' }}）</template>
        </el-table-column>
        <el-table-column label="原销售" min-width="140">
          <template #default="{ row }">{{ row.fromSales || '-' }}（{{ row.fromSalesCode || '-' }}）</template>
        </el-table-column>
        <el-table-column label="目标销售" min-width="140">
          <template #default="{ row }">{{ row.toSales || '-' }}（{{ row.toSalesCode || '-' }}）</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <TextActions>
              <button class="table-action" type="button" @click="goDetail(row)">详情</button>
            </TextActions>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" :total="rows.length" />
    </div>

    <div class="mobile-list">
      <article v-for="row in pagedRows" :key="`${row.customerCode}-${row.createdAt}-${row.action}`" class="assign-log-card">
        <div class="card-head">
          <strong>{{ row.customerCode }}</strong>
          <el-tag :type="actionType(row.action)">{{ row.actionText || actionText(row.action) }}</el-tag>
        </div>
        <p>{{ row.operator || '-' }}（{{ row.operatorCode || '-' }}） · {{ row.createdAt }}</p>
        <small>原销售：{{ row.fromSales || '-' }}（{{ row.fromSalesCode || '-' }}）</small>
        <small>目标销售：{{ row.toSales || '-' }}（{{ row.toSalesCode || '-' }}）</small>
        <small v-if="row.remark">备注：{{ row.remark }}</small>
        <TextActions class="card-actions">
          <button class="table-action" type="button" @click="goDetail(row)">详情</button>
        </TextActions>
      </article>
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" compact :total="rows.length" />
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { listAssignLogs } from '../api/clue'
import AppPagination from '../components/AppPagination.vue'
import FilterPanel from '../components/FilterPanel.vue'
import TextActions from '../components/TextActions.vue'

const router = useRouter()
const loading = ref(false)
const filterExpanded = ref(false)
const rows = ref([])
const page = reactive({ current: 1, size: 10 })
const filters = reactive({
  customerCode: '',
  action: '',
  operator: '',
  salesEmployeeCode: ''
})
const dateRange = ref(defaultMonthRange())
const pagedRows = computed(() => rows.value.slice((page.current - 1) * page.size, (page.current - 1) * page.size + page.size))

async function fetchRows() {
  loading.value = true
  try {
    const res = await listAssignLogs(queryParams())
    rows.value = res.data || []
    page.current = 1
  } catch (error) {
    await showError(error.message || '分配日志加载失败')
  } finally {
    loading.value = false
  }
}

function queryParams() {
  return {
    ...Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '')),
    startDate: dateRange.value?.[0],
    endDate: dateRange.value?.[1]
  }
}

function goDetail(row) {
  router.push(`/clues/${row.customerCode}`)
}

function actionText(action) {
  return {
    ASSIGN: '分配',
    TRANSFER: '转派',
    CLAIM: '领取',
    CLAIM_CONFLICT: '抢单冲突',
    RELEASE: '释放'
  }[action] || action
}

function actionType(action) {
  return {
    ASSIGN: 'primary',
    TRANSFER: 'warning',
    CLAIM: 'success',
    CLAIM_CONFLICT: 'danger',
    RELEASE: 'info'
  }[action] || 'info'
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

watch([() => page.size, () => rows.value.length], () => {
  const maxPage = Math.max(1, Math.ceil(rows.value.length / page.size))
  if (page.current > maxPage) page.current = maxPage
})

onMounted(fetchRows)
</script>

<style scoped>
.date-range {
  width: 100%;
}

.assign-log-card {
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

.assign-log-card p,
.assign-log-card small {
  margin: 0;
}

.assign-log-card small {
  color: var(--text-muted);
}
</style>
