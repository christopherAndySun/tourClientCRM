<template>
  <section>
    <div class="toolbar">
      <div class="page-title">系统设置</div>
      <el-button type="primary" :loading="saving" @click="submit">保存设置</el-button>
    </div>

    <el-alert
      class="settings-tip"
      title="这里维护系统级配置。OCR、钉钉机器人等后续新增配置都统一放在这里。"
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

      <article class="settings-section">
        <div class="section-head">
          <div>
            <h2>OCR 图片识别配置</h2>
            <p>用于识别截图中的微信号、手机号、抖音号等信息。OCR 只识别第一张抖音截图一次。</p>
          </div>
          <el-tag type="warning">第三方服务</el-tag>
        </div>

        <el-form label-position="top">
          <el-form-item label="APP CODE">
            <el-input v-model="form.ocrAppCode" clearable placeholder="请输入 OCR APP CODE" />
          </el-form-item>
          <el-form-item label="APP SECRET">
            <el-input v-model="form.ocrAppSecret" type="password" show-password clearable placeholder="请输入 OCR APP SECRET" />
          </el-form-item>
        </el-form>
      </article>

      <article class="settings-section">
        <div class="section-head">
          <div>
            <h2>钉钉客资播报</h2>
            <p>本部运营每新增一条客资后，自动向本部钉钉群播报当天客资汇总；分公司数据不会发送到这里。</p>
          </div>
          <el-switch
            v-model="form.dingtalkHqClueEnabled"
            active-text="启用"
            inactive-text="停用"
            inline-prompt
          />
        </div>

        <el-form label-position="top">
          <el-form-item label="本部客资播报 Webhook">
            <el-input
              v-model="form.dingtalkHqClueWebhook"
              type="password"
              show-password
              clearable
              placeholder="请输入钉钉机器人 webhook"
            />
          </el-form-item>
          <el-alert
            title="钉钉流程的关键词建议配置为“客资数据”，输出内容使用 webhook 入参里的 content/message/text 字段均可。"
            type="success"
            :closable="false"
            show-icon
          />
        </el-form>
      </article>

      <article class="settings-section">
        <div class="section-head">
          <div>
            <h2>备注</h2>
            <p>可记录供应商、用途、有效期、接入说明等信息。</p>
          </div>
        </div>
        <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
      </article>

      <div v-if="form.updatedAt" class="updated-at">最后保存：{{ form.updatedAt }}</div>
    </section>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSystemSettings, saveSystemSettings } from '../api/settings'
import { showError } from '../utils/feedback'

const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const form = reactive({
  ocrAppCode: '',
  ocrAppSecret: '',
  dingtalkHqClueWebhook: '',
  dingtalkHqClueEnabled: false,
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
      dingtalkHqClueWebhook: res.data?.dingtalkHqClueWebhook || '',
      dingtalkHqClueEnabled: Boolean(res.data?.dingtalkHqClueEnabled),
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
  if (form.dingtalkHqClueEnabled && !form.dingtalkHqClueWebhook) {
    await showError('启用钉钉客资播报前，请先填写本部客资播报 Webhook')
    return
  }
  try {
    await ElMessageBox.confirm('确认保存系统设置吗？保存后后续 OCR 和钉钉播报会使用这组配置。', '保存确认', {
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
      dingtalkHqClueWebhook: form.dingtalkHqClueWebhook,
      dingtalkHqClueEnabled: form.dingtalkHqClueEnabled,
      remark: form.remark
    })
    Object.assign(form, res.data || {})
    form.dingtalkHqClueEnabled = Boolean(res.data?.dingtalkHqClueEnabled)
    ElMessage.success('系统设置已保存')
  } catch (error) {
    await showError(error.message || '系统设置保存失败')
  } finally {
    saving.value = false
  }
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

.settings-section {
  display: grid;
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: 1px solid #edf1f7;
}

.settings-section:last-of-type {
  border-bottom: 0;
  padding-bottom: 0;
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
