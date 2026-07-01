<template>
  <section>
    <div class="toolbar">
      <div class="page-title">{{ isEdit ? '客户详情' : '新增客户' }}</div>
    </div>

    <el-form class="panel clue-form" label-position="top">
      <el-alert v-if="!isEdit" title="客户编号由系统自动生成。老客户新出行需求确认后，会作为本次运营的客资保存。" type="info" show-icon :closable="false" />
      <el-form-item v-if="isEdit" label="客户编号"><el-input v-model="form.customerCode" disabled /></el-form-item>
      <el-form-item label="来源平台"><el-select v-model="form.sourcePlatform" class="full-field"><el-option label="抖音" value="DOUYIN" /><el-option label="小红书" value="XIAOHONGSHU" /></el-select></el-form-item>
      <el-form-item label="当前状态"><el-select v-model="form.status" class="full-field"><el-option label="新录入" value="NEW" /><el-option label="跟进中" value="FOLLOWING" /><el-option label="无效用户" value="INVALID" /><el-option label="已交定金" value="DEPOSIT_PAID" /><el-option label="退单" value="REFUNDED" /><el-option label="已落地" value="LANDED" /></el-select></el-form-item>
      <div v-if="['DEPOSIT_PAID', 'REFUNDED', 'LANDED'].includes(form.status)" class="status-fields"><el-form-item label="定金金额"><el-input v-model="form.depositAmount" placeholder="例如：200" /></el-form-item><el-form-item label="状态备注"><el-input v-model="form.statusRemark" type="textarea" :rows="3" placeholder="例如：客户已付定金，待销售确认行程" /></el-form-item></div>
      <div v-if="form.status === 'REFUNDED'" class="status-fields"><el-form-item label="退单金额"><el-input v-model="form.refundAmount" placeholder="例如：200" /></el-form-item><el-form-item label="退款时间"><el-date-picker v-model="form.refundedAt" class="full-field" type="datetime" value-format="YYYY-MM-DD HH:mm" placeholder="请选择退款时间" /></el-form-item></div>
      <div v-if="form.status === 'LANDED'" class="status-fields"><el-form-item label="落地时间"><el-date-picker v-model="form.landingAt" class="full-field" type="datetime" value-format="YYYY-MM-DD HH:mm" placeholder="请选择落地时间" /></el-form-item><el-form-item label="落地备注"><el-input v-model="form.landingRemark" type="textarea" :rows="3" placeholder="例如：客户已完成出行，反馈满意" /></el-form-item></div>
      <el-form-item v-if="isEdit && form.assignedSales" label="分配销售"><el-input :model-value="`${form.assignedSales}（${form.assignedSalesEmployeeCode || '-'}）`" disabled /></el-form-item>
      <el-form-item label="客户联系方式"><el-input v-model="form.contactInfo" placeholder="非必填，客户加微信或提供手机号后再补充" /></el-form-item>
      <el-form-item label="是否有微信号"><el-radio-group v-model="form.hasWechatId"><el-radio-button :value="true">有</el-radio-button><el-radio-button :value="false">无</el-radio-button></el-radio-group></el-form-item>
      <el-form-item label="抖音截图"><el-upload v-model:file-list="form.douyinImages" class="crm-picture-upload" list-type="picture-card" accept="image/*" multiple :auto-upload="false" :on-change="(_, fileList) => syncUploadList('douyinImages', fileList)" :on-remove="(_, fileList) => syncUploadList('douyinImages', fileList)" :on-preview="previewImage"><el-icon><Plus /></el-icon></el-upload><div class="upload-tip">后续 OCR 只识别第一张图片，请把包含微信号或手机号的截图放在第一张。</div></el-form-item>
      <el-form-item label="微信截图"><el-upload v-model:file-list="form.wechatImages" class="crm-picture-upload" list-type="picture-card" accept="image/*" multiple :auto-upload="false" :on-change="(_, fileList) => syncUploadList('wechatImages', fileList)" :on-remove="(_, fileList) => syncUploadList('wechatImages', fileList)" :on-preview="previewImage"><el-icon><Plus /></el-icon></el-upload></el-form-item>
      <el-form-item :label="isEdit ? '本次跟进备注' : '备注'"><el-input v-model="form.remark" type="textarea" :rows="4" :placeholder="isEdit ? '填写本次沟通结果，例如：客户要和家人确认，明天下午再回访' : '客户需求、沟通要点等'" /></el-form-item>
      <section v-if="isEdit && historyDemands.length" class="status-history customer-history"><h2>历史需求</h2><div class="history-demand-list"><button v-for="item in historyDemands" :key="item.customerCode" class="history-demand-card" type="button" @click="goHistoryDetail(item)"><div><strong>第 {{ item.demandSequence || 1 }} 次需求 · {{ item.customerCode }}</strong><span>{{ sourcePlatformText(item.sourcePlatform) }} · 运营：{{ item.uploader || '-' }} · 销售：{{ item.assignedSales || '未分配' }}</span></div><StatusTag :status="item.status" /><small>{{ item.createdAt }}</small></button></div></section>
      <section v-if="isEdit && form.followRecords?.length" class="status-history"><h2>跟踪记录</h2><el-timeline><el-timeline-item v-for="item in form.followRecords" :key="`${item.createdAt}-${item.remark}`" :timestamp="item.createdAt" placement="top"><div class="history-card follow-card"><strong>{{ item.operator }}（{{ item.operatorCode }}）</strong><p>{{ item.remark }}</p></div></el-timeline-item></el-timeline></section>
      <section v-if="isEdit" class="status-history"><h2>状态流转记录</h2><el-timeline v-if="form.statusHistory?.length"><el-timeline-item v-for="item in form.statusHistory" :key="`${item.createdAt}-${item.status}`" :timestamp="item.createdAt" placement="top"><div class="history-card"><strong>{{ item.statusText || statusText(item.status) }}</strong><span>{{ item.operator }}（{{ item.operatorCode }}）</span><p v-if="item.depositAmount">定金：{{ item.depositAmount }}</p><p v-if="item.remark">{{ item.remark }}</p></div></el-timeline-item></el-timeline><el-empty v-else description="暂无流转记录" /></section>
      <section v-if="isEdit && form.operationLogs?.length" class="status-history operation-history"><h2>操作日志</h2><el-timeline><el-timeline-item v-for="item in reversedOperationLogs" :key="`${item.createdAt}-${item.field}-${item.oldValue}-${item.newValue}`" :timestamp="item.createdAt" placement="top"><div class="history-card operation-card"><strong>{{ item.actionText || item.action }}：{{ item.fieldText || item.field }}</strong><span>{{ item.operator }}（{{ item.operatorCode }}）</span><p><b>修改前：</b>{{ item.oldValue || '-' }}</p><p><b>修改后：</b>{{ item.newValue || '-' }}</p></div></el-timeline-item></el-timeline></section>
      <div class="form-actions"><el-button @click="$router.back()">返回</el-button><el-button v-if="isEdit" type="success" plain @click="openDealDialog">登记定金</el-button><el-button type="primary" :loading="submitting" @click="submit()">{{ isEdit ? '保存修改' : '提交入库' }}</el-button></div>
    </el-form>
    <el-dialog v-model="preview.visible" :title="preview.name" width="min(760px, 92vw)"><img class="preview-dialog-image" :src="preview.url" :alt="preview.name" /></el-dialog>
    <el-dialog v-model="dealDialogVisible" title="登记定金" width="min(640px, 92vw)"><el-form label-position="top"><el-form-item label="客户姓名"><el-input v-model="dealForm.customerName" placeholder="请输入客户姓名" /></el-form-item><el-form-item label="定金/预付金"><el-input v-model="dealForm.deposit" placeholder="例如：200" /></el-form-item><el-form-item label="预定时间"><el-date-picker v-model="dealForm.bookingDate" class="full-field" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="加粉时间"><el-date-picker v-model="dealForm.addWechatDate" class="full-field" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="报价"><el-input v-model="dealForm.quoteText" placeholder="例如：1280/位，4大1小" /></el-form-item><el-form-item label="出行时间"><el-input v-model="dealForm.travelDate" placeholder="例如：预计 7 月中旬" /></el-form-item><el-form-item label="行程"><el-input v-model="dealForm.itinerary" type="textarea" :rows="3" placeholder="例如：青岛 5 日游" /></el-form-item><el-form-item label="成交日期"><el-date-picker v-model="dealForm.dealDate" class="full-field" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-form><template #footer><el-button @click="dealDialogVisible = false">取消</el-button><el-button type="success" :loading="dealSubmitting" @click="submitDeal">确认登记</el-button></template></el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { createClue, getClue, getClueHistory, updateClue } from '../api/clue'
import { createDeal } from '../api/deal'
import StatusTag from '../components/StatusTag.vue'
import { sourcePlatformText, statusText } from '../utils/status'

const route = useRoute()
const router = useRouter()
const submitting = ref(false)
const dealSubmitting = ref(false)
const dealDialogVisible = ref(false)
const historyLoading = ref(false)
const historyDemands = ref([])
const isEdit = computed(() => Boolean(route.params.customerCode))
const reversedOperationLogs = computed(() => [...(form.operationLogs || [])].reverse())

const form = reactive({
  customerCode: '', sourcePlatform: 'DOUYIN', contactInfo: '', hasWechatId: true, status: 'NEW', douyinImages: [], wechatImages: [], remark: '', repeatDemand: false,
  assignedSales: '', assignedSalesEmployeeCode: '', depositAmount: '', statusRemark: '', refundAmount: '', refundedAt: '', landingAt: '', landingRemark: '',
  statusHistory: [], followRecords: [], assignLogs: [], operationLogs: []
})
const preview = reactive({ visible: false, name: '', url: '' })
const dealForm = reactive({ customerName: '', deposit: '', bookingDate: '', addWechatDate: '', quoteText: '', travelDate: '', itinerary: '', dealDate: formatDate(new Date()) })

onMounted(loadDetail)

async function loadDetail() {
  if (!isEdit.value) return
  const res = await getClue(route.params.customerCode)
  if (!res.data) {
    await showError('客户线索不存在')
    router.push('/clues')
    return
  }
  Object.assign(form, {
    ...res.data,
    hasWechatId: res.data.hasWechatId !== false,
    status: normalizeStatus(res.data.status),
    douyinImages: normalizeSavedImages(res.data.douyinImages),
    wechatImages: normalizeSavedImages(res.data.wechatImages),
    statusHistory: res.data.statusHistory || [],
    followRecords: res.data.followRecords || [],
    assignLogs: res.data.assignLogs || [],
    operationLogs: res.data.operationLogs || [],
    remark: ''
  })
  await loadHistory()
}

async function loadHistory() {
  if (!isEdit.value || !form.customerCode) return
  historyLoading.value = true
  try {
    const res = await getClueHistory(form.customerCode)
    historyDemands.value = res.data?.demands || []
  } catch (error) {
    historyDemands.value = []
  } finally {
    historyLoading.value = false
  }
}

function goHistoryDetail(item) {
  if (!item?.customerCode || item.customerCode === form.customerCode) return
  router.push(`/clues/${item.customerCode}`)
}

async function submit(allowRepeatDemand = false) {
  submitting.value = true
  try {
    const payload = buildPayload(allowRepeatDemand)
    if (isEdit.value) {
      const res = await updateClue(form.customerCode, payload)
      if (!res?.data?.customerCode) throw new Error('保存成功但未返回客户详情，请刷新后重试')
      Object.assign(form, {
        ...res.data,
        hasWechatId: res.data?.hasWechatId !== false,
        status: normalizeStatus(res.data?.status),
        douyinImages: normalizeSavedImages(res.data?.douyinImages || []),
        wechatImages: normalizeSavedImages(res.data?.wechatImages || []),
        statusHistory: res.data?.statusHistory || [],
        followRecords: res.data?.followRecords || [],
        assignLogs: res.data?.assignLogs || [],
        operationLogs: res.data?.operationLogs || [],
        remark: ''
      })
      ElMessage.success('客户线索已更新')
      router.back()
    } else {
      await createClue(payload)
      ElMessage.success(allowRepeatDemand ? '老客户新需求已保存' : '客户线索已提交')
      router.push('/clues')
    }
  } catch (error) {
    if (!isEdit.value && error?.message?.includes('请不要重复保存')) {
      const action = await ElMessageBox.confirm(`${error.message}。如果这是客户本次新的出行需求，可以保存为本次运营的新增客资。`, '发现老客户', {
        confirmButtonText: '保存为本次新需求', cancelButtonText: '取消', type: 'warning'
      }).catch(() => 'cancel')
      if (action !== 'cancel') await submit(true)
      return
    }
    await showError(error.message || '保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function buildPayload(allowRepeatDemand = false) {
  return { ...form, contactInfo: form.contactInfo.trim(), allowRepeatDemand, douyinImages: compactImages(form.douyinImages), wechatImages: compactImages(form.wechatImages) }
}

function openDealDialog() {
  Object.assign(dealForm, { customerName: '', deposit: form.depositAmount || '', bookingDate: '', addWechatDate: '', quoteText: '', travelDate: '', itinerary: '', dealDate: formatDate(new Date()) })
  dealDialogVisible.value = true
}

async function submitDeal() {
  if (!dealForm.customerName.trim() || !dealForm.deposit.trim()) {
    await showError('请填写客户姓名和定金/预付金')
    return
  }
  dealSubmitting.value = true
  try {
    await createDeal({ ...dealForm, customerCode: form.customerCode })
    ElMessage.success('定金已登记')
    dealDialogVisible.value = false
    router.push('/deals')
  } catch (error) {
    await showError(error.message || '登记定金失败')
  } finally {
    dealSubmitting.value = false
  }
}

async function syncUploadList(field, fileList) {
  const normalized = await Promise.all(fileList.map((file, index) => normalizeUploadFile(file, index)))
  form[field] = normalized
}
async function normalizeUploadFile(file, index = 0) {
  const sortOrder = Number.isFinite(file.sortOrder) ? file.sortOrder : index
  if (file.url?.startsWith('data:')) return { name: file.name, url: file.url, uid: file.uid, sortOrder }
  if (file.raw) return { name: file.name, url: await readImageFile(file.raw), uid: file.uid, sortOrder }
  return { name: file.name, url: file.url, uid: file.uid, sortOrder }
}
function previewImage(file) { preview.name = file.name; preview.url = file.url; preview.visible = true }
function normalizeSavedImages(images = []) { return images.map((image, index) => ({ name: image.name || `图片${index + 1}`, url: image.url, uid: image.uid || `${Date.now()}-${index}`, sortOrder: Number.isFinite(image.sortOrder) ? image.sortOrder : index })).sort((left, right) => left.sortOrder - right.sortOrder) }
function compactImages(images = []) { return images.map((image, index) => ({ name: image.name, url: image.url, uid: image.uid, sortOrder: Number.isFinite(image.sortOrder) ? image.sortOrder : index })) }
function readImageFile(file) { return new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = reject; reader.readAsDataURL(file) }) }
function normalizeStatus(status) { if (status === 'TO_DEAL') return 'FOLLOWING'; if (status === 'DEALED') return 'DEPOSIT_PAID'; return status || 'NEW' }
function formatDate(date) { const year = date.getFullYear(); const month = String(date.getMonth() + 1).padStart(2, '0'); const day = String(date.getDate()).padStart(2, '0'); return `${year}-${month}-${day}` }
function showError(message) { return ElMessageBox.alert(message, '提示', { confirmButtonText: '我知道了', type: 'warning' }) }
</script>
<style scoped>
.clue-form {
  width: 100%;
  max-width: 900px;
  min-width: 0;
}

.full-field {
  width: 100%;
}

.status-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.crm-picture-upload {
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.crm-picture-upload :deep(.el-upload-list) {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  max-width: 100%;
}

.crm-picture-upload :deep(.el-upload-list--picture-card .el-upload-list__item),
.crm-picture-upload :deep(.el-upload--picture-card),
.crm-picture-upload :deep(.el-upload-list__item-thumbnail) {
  width: 120px;
  height: 120px;
  margin: 0;
  border-radius: 8px;
  object-fit: cover;
}

.upload-tip {
  width: 100%;
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.preview-dialog-image {
  width: 100%;
  max-height: 72vh;
  display: block;
  object-fit: contain;
}

.status-history {
  margin-top: 8px;
}

.status-history h2 {
  margin: 0 0 14px;
  color: var(--text-strong);
  font-size: 18px;
}

.history-card {
  display: grid;
  gap: 4px;
  padding: 12px;
  border-radius: 14px;
  background: rgba(178, 174, 250, 0.12);
}

.follow-card {
  background: linear-gradient(135deg, rgba(79, 200, 120, 0.13), rgba(178, 174, 250, 0.14));
}

.operation-card {
  background: linear-gradient(135deg, rgba(250, 163, 144, 0.12), rgba(178, 174, 250, 0.14));
}

.follow-card strong {
  color: var(--text-strong);
}

.history-card span,
.history-card p {
  margin: 0;
  color: var(--text-muted);
}

.history-card b {
  color: var(--text-main);
}

.history-demand-list {
  display: grid;
  gap: 10px;
}

.history-demand-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px;
  border: 1px solid rgba(178, 174, 250, 0.3);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--text-main);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.history-demand-card:hover {
  background: rgba(178, 174, 250, 0.12);
}

.history-demand-card strong,
.history-demand-card span {
  display: block;
}

.history-demand-card strong {
  color: var(--text-strong);
}

.history-demand-card span,
.history-demand-card small {
  color: var(--text-muted);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 760px) {
  .clue-form {
    width: 100%;
    max-width: none;
    overflow-x: hidden;
    padding-bottom: calc(96px + env(safe-area-inset-bottom));
  }

  .clue-form :deep(.el-form-item__content),
  .clue-form :deep(.el-input),
  .clue-form :deep(.el-select),
  .clue-form :deep(.el-date-editor),
  .clue-form :deep(.el-textarea),
  .clue-form :deep(.el-upload) {
    min-width: 0;
    max-width: 100%;
  }

  .status-fields {
    grid-template-columns: 1fr;
  }

  .crm-picture-upload :deep(.el-upload-list--picture-card .el-upload-list__item),
  .crm-picture-upload :deep(.el-upload--picture-card),
  .crm-picture-upload :deep(.el-upload-list__item-thumbnail) {
    width: 96px;
    height: 96px;
  }

  .form-actions {
    position: fixed;
    right: 0;
    bottom: 0;
    left: 0;
    width: 100%;
    max-width: 100vw;
    min-width: 0;
    z-index: 30;
    justify-content: flex-end;
    flex-wrap: nowrap;
    overflow-x: hidden;
    background: rgba(255, 255, 255, 0.96);
    border-top: 1px solid var(--line);
    box-shadow: 0 -12px 28px rgba(32, 39, 75, 0.12);
    padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
    backdrop-filter: blur(14px);
  }

  .form-actions :deep(.el-button) {
    flex: 1 1 0;
    min-width: 0;
    max-width: 128px;
    height: 42px;
    padding-right: 12px;
    padding-left: 12px;
  }

  .history-demand-card {
    grid-template-columns: 1fr;
  }
}
</style>
