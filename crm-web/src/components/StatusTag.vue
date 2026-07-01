<template>
  <el-tag :type="statusType(status)">{{ text || statusText(status) }}</el-tag>
</template>

<script setup>
import { statusText, statusType } from '../utils/status'

defineProps({
  status: {
    type: String,
    default: ''
  },
  text: {
    type: String,
    default: ''
  }
})
</script>
