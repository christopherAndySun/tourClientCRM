<template>
  <section>
    <div class="toolbar">
      <div class="page-title">系统设置</div>
      <el-button type="primary" :loading="saving" @click="submit">保存设置</el-button>
    </div>

    <el-alert
      class="settings-tip"
      title="这里维护系统级配置。OCR 接入前先保存 APP CODE 和 APP SECRET，后续新增配置也统一放在这里。"
      type="info"
      :closable="false"
      show-icon
    />

    <section class="panel settings-card" v-loading="loading">
      <el-alert
        v-if="loadError"
        class="load-error"
        :title="loadError"
        type="warning"
        :closable="false"
        show-icon
      />

      <div class="section-head">
        <div>
          <h2>OCR 图片识别配置</h2>
          <p>用于后续识别截图中的微信号、手机号、抖音号等信息。</p>
        </div>
        <el-tag type="warning">待接入</el-tag>
      </div>

      <el-form label-position="top">
        <el-form-item label="APP CODE">
          <el-input v-model="form.ocrAppCode" clearable placeholder="请输入 OCR APP CODE" />
        </el-form-item>
        <el-form-item label="APP SECRET">
          <el-input v-model="form.ocrAppSecret" type="password" show-password clearable placeholder="请输入 OCR APP SECRET" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="可记录供应商、用途、有效期等信息" />
        </el-form-item>
      </el-form>

      <div v-if="form.updatedAt" class="updated-at">最后保存：{{ form.updatedAt }}</div>
    </section>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSystemSettings, saveSystemSettings } from '../api/settings'

const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const form = reactive({
  ocrAppCode: '',
  ocrAppSecret: '',
  remark: '',
  updatedAt: ''
})

async function fetchSettings() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getSystemSettings()
    Object.assign(form, {
      ocrAppCode: res.data?.ocrAppCode || '',
      ocrAppSecret: res.data?.ocrAppSecret || '',
      remark: res.data?.remark || '',
      updatedAt: res.data?.updatedAt || ''
    })
  } catch (error) {
    loadError.value = error.message || '系统设置加载失败'
  } finally {
    loading.value = false
  }
}

async function submit() {
  try {
    await ElMessageBox.confirm('确认保存系统设置吗？保存后后续 OCR 接入会使用这组配置。', '保存确认', {
      confirmButtonText: '确认保存',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  saving.value = true
  try {
    const res = await saveSystemSettings({
      ocrAppCode: form.ocrAppCode,
      ocrAppSecret: form.ocrAppSecret,
      remark: form.remark
    })
    Object.assign(form, res.data || {})
    ElMessage.success('系统设置已保存')
  } catch (error) {
    await showError(error.message || '系统设置保存失败')
  } finally {
    saving.value = false
  }
}

function showError(message) {
  return ElMessageBox.alert(message, '提示', { confirmButtonText: '我知道了', type: 'warning' })
}

onMounted(fetchSettings)
</script>

<style scoped>
.settings-tip {
  margin-bottom: 14px;
}

.settings-card {
  display: grid;
  gap: 18px;
}

.load-error {
  margin-bottom: 2px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.section-head h2 {
  margin: 0;
  color: #17233c;
  font-size: 20px;
}

.section-head p {
  margin: 8px 0 0;
  color: #68758c;
  line-height: 1.6;
}

.updated-at {
  color: #68758c;
  font-size: 13px;
}

@media (max-width: 768px) {
  .section-head {
    flex-direction: column;
  }
}
</style>
