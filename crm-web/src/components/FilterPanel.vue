<template>
  <section
    class="panel filter-panel crm-filter-panel"
    :class="{
      'is-expanded': expanded,
      'is-mobile-open': mobileOpen,
      'is-not-collapsible': !collapsible
    }"
  >
    <div class="crm-filter-grid">
      <slot />
      <template v-if="expanded">
        <slot name="advanced" />
      </template>
      <div class="crm-filter-actions">
        <slot name="actions" />
        <button v-if="collapsible" class="filter-more" type="button" @click="$emit('update:expanded', !expanded)">
          {{ expanded ? '收起 ▲' : '展开 ▼' }}
        </button>
      </div>
    </div>
  </section>
</template>

<script setup>
defineProps({
  expanded: {
    type: Boolean,
    default: false
  },
  collapsible: {
    type: Boolean,
    default: true
  },
  mobileOpen: {
    type: Boolean,
    default: true
  }
})

defineEmits(['update:expanded'])
</script>

<style scoped>
.crm-filter-panel {
  margin-bottom: 14px;
  overflow: visible;
}

.crm-filter-grid {
  align-items: center;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
}

.crm-filter-actions {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: flex-start;
  min-width: max-content;
}

.filter-more {
  background: #fff;
  border: 1px solid rgba(92, 108, 255, 0.22);
  border-radius: 12px;
  color: var(--text-main);
  cursor: pointer;
  min-height: 42px;
  padding: 0 14px;
}

.crm-filter-panel :deep(.el-input),
.crm-filter-panel :deep(.el-select),
.crm-filter-panel :deep(.el-date-editor) {
  width: 100%;
}

.crm-filter-panel :deep(.el-input__wrapper),
.crm-filter-panel :deep(.el-select__wrapper),
.crm-filter-panel :deep(.el-date-editor.el-input__wrapper) {
  min-height: 42px;
}

.crm-filter-panel :deep(.el-range-editor.el-input__wrapper) {
  height: 42px;
  min-height: 42px;
}

@media (min-width: 1181px) {
  .crm-filter-panel:not(.is-expanded) .crm-filter-grid {
    grid-template-columns: repeat(4, minmax(180px, 1fr)) minmax(max-content, auto);
  }

  .crm-filter-panel.is-expanded .crm-filter-grid {
    grid-template-columns: repeat(4, minmax(180px, 1fr));
  }

  .crm-filter-panel.is-not-collapsible .crm-filter-grid {
    grid-template-columns: minmax(260px, 420px) minmax(max-content, auto);
  }
}

@media (max-width: 760px) {
  .crm-filter-panel {
    display: none;
  }

  .crm-filter-panel.is-mobile-open,
  .crm-filter-panel.is-not-collapsible {
    animation: filterDrop 0.18s ease both;
    display: block;
  }

  .crm-filter-grid {
    grid-template-columns: 1fr;
  }

  .crm-filter-actions {
    display: grid;
    grid-template-columns: 1fr;
    min-width: 0;
  }

  .crm-filter-actions :deep(.el-button),
  .crm-filter-actions .filter-more {
    width: 100%;
  }

  @keyframes filterDrop {
    from {
      opacity: 0;
      transform: translateY(-8px);
    }

    to {
      opacity: 1;
      transform: translateY(0);
    }
  }
}
</style>
