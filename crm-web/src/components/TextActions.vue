<template>
  <div class="text-actions">
    <slot />
  </div>
</template>

<style scoped>
.text-actions {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 14px;
}

@media (max-width: 760px) {
  .text-actions {
    display: flex;
    justify-content: flex-start;
  }
}
</style>
