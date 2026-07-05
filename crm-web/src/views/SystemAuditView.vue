<template>
  <section>
    <div class="toolbar">
      <div class="page-title">系统审计</div>
    </div>

    <el-alert
      class="audit-tip"
      title="系统审计用于追溯登录、导出、下载 Word、账号和系统设置等关键操作。普通业务数据不在这里修改。"
      type="info"
      :closable="false"
      show-icon
    />

    <FilterPanel v-model:expanded="filterExpanded">
      <el-select v-model="filters.action" class="filter-field" clearable placeholder="操作类型">
        <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-input v-model="filters.operator" class="filter-field" clearable placeholder="操作人/工号" />
      <el-input v-model="filters.targetCode" class="filter-field" clearable placeholder="对象编号" />
      <template #advanced>
        <el-select v-model="filters.targetType" class="filter-field" clearable placeholder="对象类型">
          <el-option label="用户" value="USER" />
          <el-option label="客户线索" value="CLUE" />
          <el-option label="系统设置" value="SYSTEM_SETTINGS" />
          <el-option label="客户导出" value="CLUE_EXPORT" />
          <el-option label="绩效导出" value="PERFORMANCE_EXPORT" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          class="date-range filter-field"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
        />
      </template>
      <template #actions>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      </template>
    </FilterPanel>

    <div class="panel desktop-table">
      <el-table :data="rows" width="100%" v-loading="loading">
        <el-table-column prop="createdAt" label="操作时间" min-width="150" />
        <el-table-column label="操作类型" min-width="150">
          <template #default="{ row }">{{ row.actionText || actionText(row.action) }}</template>
        </el-table-column>
        <el-table-column label="操作人" min-width="150">
          <template #default="{ row }">{{ userText(row.operator, row.operatorCode) }}</template>
        </el-table-column>
        <el-table-column label="对象" min-width="180">
          <template #default="{ row }">{{ targetText(row) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="260" show-overflow-tooltip />
      </el-table>
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" :total="total" />
    </div>

    <div class="mobile-list" v-loading="loading">
      <article v-for="row in rows" :key="`${row.action}-${row.operatorCode}-${row.createdAt}-${row.targetCode}`" class="audit-card">
        <div class="card-head">
          <strong>{{ row.actionText || actionText(row.action) }}</strong>
          <el-tag>{{ row.createdAt || '-' }}</el-tag>
        </div>
        <p>{{ userText(row.operator, row.operatorCode) }}</p>
        <small>对象：{{ targetText(row) }}</small>
        <small v-if="row.remark">备注：{{ row.remark }}</small>
      </article>
      <el-empty v-if="!loading && rows.length === 0" description="暂无系统审计记录" />
      <AppPagination v-model:current-page="page.current" v-model:page-size="page.size" compact :total="total" />
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { listSystemAuditLogs } from '../api/systemAudit'
import AppPagination from '../components/AppPagination.vue'
import FilterPanel from '../components/FilterPanel.vue'
import { showError } from '../utils/feedback'

const loading = ref(false)
const filterExpanded = ref(false)
const rows = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })
const dateRange = ref(defaultMonthRange())
const filters = reactive({
  action: '',
  operator: '',
  targetType: '',
  targetCode: ''
})
const actionOptions = [
  { label: '登录系统', value: 'LOGIN' },
  { label: '退出登录', value: 'LOGOUT' },
  { label: '新增账号', value: 'USER_CREATE' },
  { label: '修改账号', value: 'USER_UPDATE' },
  { label: '删除账号', value: 'USER_DELETE' },
  { label: '保存系统设置', value: 'SETTINGS_UPDATE' },
  { label: '下载 Word', value: 'WORD_DOWNLOAD' },
  { label: 'Word 下载失败', value: 'WORD_DOWNLOAD_FAILED' },
  { label: '导出客户线索', value: 'CLUE_EXPORT' },
  { label: '导出员工绩效', value: 'PERFORMANCE_EXPORT' },
  { label: '放回三方公共池', value: 'THIRD_PARTY_RESTORE' }
]

async function fetchRows() {
  loading.value = true
  try {
    const res = await listSystemAuditLogs(queryParams())
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    await showError(error.message || '系统审计加载失败')
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

function userText(name, code) {
  if (!name && !code) return '-'
  return `${name || '-'}（${code || '-'}）`
}

function targetText(row) {
  const type = targetTypeText(row.targetType)
  return `${type}${row.targetCode ? `：${row.targetCode}` : ''}`
}

function actionText(action) {
  return actionOptions.find((item) => item.value === action)?.label || action || '-'
}

function targetTypeText(type) {
  const map = {
    USER: '用户',
    CLUE: '客户线索',
    SYSTEM_SETTINGS: '系统设置',
    CLUE_EXPORT: '客户导出',
    PERFORMANCE_EXPORT: '绩效导出'
  }
  return map[type] || type || '-'
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

watch([() => page.current, () => page.size], fetchRows)

onMounted(fetchRows)
</script>

<style scoped>
.audit-tip {
  margin-bottom: 14px;
}

.date-range {
  width: 100%;
}

.audit-card {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.card-head {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.audit-card p,
.audit-card small {
  margin: 0;
}

.audit-card small {
  color: var(--text-muted);
}
</style>
