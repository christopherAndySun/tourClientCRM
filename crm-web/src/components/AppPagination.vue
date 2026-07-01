<template>
  <div v-if="total > 0" class="app-pagination" :class="{ 'is-compact': compact }">
    <el-pagination
      :current-page="currentPage"
      :page-size="pageSize"
      :page-sizes="pageSizes"
      :total="total"
      :background="background"
      :small="compact"
      :layout="resolvedLayout"
      @update:current-page="$emit('update:currentPage', $event)"
      @update:page-size="$emit('update:pageSize', $event)"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  currentPage: {
    type: Number,
    required: true
  },
  pageSize: {
    type: Number,
    required: true
  },
  total: {
    type: Number,
    required: true
  },
  pageSizes: {
    type: Array,
    default: () => [10, 20, 50, 100]
  },
  compact: {
    type: Boolean,
    default: false
  },
  background: {
    type: Boolean,
    default: true
  },
  layout: {
    type: String,
    default: ''
  }
})

defineEmits(['update:currentPage', 'update:pageSize'])

const resolvedLayout = computed(() => {
  if (props.layout) return props.layout
  return props.compact ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'
})
</script>

<style scoped>
.app-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

.app-pagination :deep(.el-pagination) {
  --el-pagination-button-height: 34px;
  --el-pagination-button-width: 34px;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px 2px;
  color: var(--text-main);
}

.app-pagination :deep(.el-pagination .el-select) {
  width: 112px;
}

.app-pagination :deep(.el-pagination__jump) {
  margin-left: 8px;
}

.app-pagination.is-compact {
  justify-content: center;
  padding-top: 12px;
}

@media (max-width: 760px) {
  .app-pagination {
    justify-content: center;
  }

  .app-pagination :deep(.el-pagination) {
    justify-content: center;
  }
}
</style>
