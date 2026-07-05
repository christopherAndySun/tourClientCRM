<template>
  <section>
    <div class="toolbar">
      <div class="page-title">{{ isEdit ? '客户详情' : '新增客户' }}</div>
    </div>

    <el-form class="panel clue-form" label-position="top">
      <el-alert v-if="!isEdit" title="客户编号由系统自动生成。老客户新出行需求确认后，会作为本次运营的客资保存。" type="info" show-icon :closable="false" />
      <el-form-item v-if="isEdit" label="客户编号"><el-input v-model="form.customerCode" disabled /></el-form-item>
      <el-form-item label="来源平台"><el-select v-model="form.sourcePlatform" class="full-field"><el-option label="抖音" value="DOUYIN" /><el-option label="小红书" value="XIAOHONGSHU" /></el-select></el-form-item>
      <el-form-item label="添加方式"><el-radio-group v-model="form.addMethod"><el-radio-button value="ACTIVE">主动</el-radio-button><el-radio-button value="PASSIVE">被动</el-radio-button><el-radio-button value="GUIDE">领队</el-radio-button></el-radio-group><div class="upload-tip">主动/被动：中转微信聊完后分享领队名片；领队：运营拿到联系方式后直接发给领队添加，进入公共池。</div></el-form-item>
      <el-form-item label="当前状态"><el-select v-model="form.status" class="full-field"><el-option label="新录入" value="NEW" /><el-option label="跟进中" value="FOLLOWING" /><el-option label="已通过" value="PASSED" /><el-option label="无效用户" value="INVALID" /><el-option label="已交定金" value="DEPOSIT_PAID" /><el-option label="退单" value="REFUNDED" /><el-option label="已落地" value="LANDED" /></el-select></el-form-item>
      <div v-if="form.status === 'DEPOSIT_PAID'" class="status-fields"><el-form-item label="定金金额"><el-input v-model="form.depositAmount" placeholder="例如：200" /></el-form-item><el-form-item label="剩余尾款"><el-input v-model="form.remainingBalance" placeholder="例如：1080" /></el-form-item><el-form-item class="status-wide" label="状态备注"><el-input v-model="form.statusRemark" type="textarea" :rows="3" placeholder="例如：客户已付定金，待确认尾款和行程" /></el-form-item></div>
      <div v-if="form.status === 'REFUNDED'" class="status-fields"><el-form-item label="退单金额"><el-input v-model="form.refundAmount" placeholder="例如：200" /></el-form-item><el-form-item class="status-wide" label="退单备注"><el-input v-model="form.statusRemark" type="textarea" :rows="3" placeholder="例如：客户取消行程，定金已退" /></el-form-item></div>
      <div v-if="form.status === 'LANDED'" class="status-fields"><el-form-item label="落地时间"><el-date-picker v-model="form.landingAt" class="full-field" type="datetime" value-format="YYYY-MM-DD HH:mm" placeholder="请选择落地时间" /></el-form-item><el-form-item class="status-wide" label="落地备注"><el-input v-model="form.landingRemark" type="textarea" :rows="3" placeholder="例如：客户已完成出行，反馈满意" /></el-form-item></div>
      <el-form-item v-if="isEdit && form.assignedSales" label="分配销售"><el-input :model-value="`${form.assignedSales}（${form.assignedSalesEmployeeCode || '-'}）`" disabled /></el-form-item>
      <el-form-item label="客户联系方式"><el-input v-model="form.contactInfo" placeholder="非必填，客户加微信或提供手机号后再补充" /></el-form-item>
      <el-form-item label="是否有微信号"><el-radio-group v-model="form.hasWechatId"><el-radio-button :value="true">有</el-radio-button><el-radio-button :value="false">无</el-radio-button></el-radio-group></el-form-item>
      <el-form-item label="抖音截图"><el-upload v-model:file-list="form.douyinImages" class="crm-picture-upload" list-type="picture-card" accept="image/*" multiple :auto-upload="false" :on-change="(_, fileList) => syncUploadList('douyinImages', fileList)" :on-remove="(_, fileList) => syncUploadList('douyinImages', fileList)" :on-preview="previewImage"><el-icon><Plus /></el-icon></el-upload><div class="upload-tip">{{ ocrRecognizing ? '正在识别第一张图片...' : 'OCR 只识别第一张图片一次，没识别到就手动填写联系方式。' }}</div></el-form-item>
      <el-form-item label="微信截图"><el-upload v-model:file-list="form.wechatImages" class="crm-picture-upload" list-type="picture-card" accept="image/*" multiple :auto-upload="false" :on-change="(_, fileList) => syncUploadList('wechatImages', fileList)" :on-remove="(_, fileList) => syncUploadList('wechatImages', fileList)" :on-preview="previewImage"><el-icon><Plus /></el-icon></el-upload></el-form-item>
      <el-form-item :label="isEdit ? '本次跟进备注' : '备注'"><el-input v-model="form.remark" type="textarea" :rows="4" :placeholder="isEdit ? '填写本次沟通结果，例如：客户要和家人确认，明天下午再回访' : '客户需求、沟通要点等'" /></el-form-item>
      <section v-if="isEdit && customerProfile.rootCustomerCode" class="status-history customer-profile-card"><h2>统一客户档案</h2><dl><div><dt>主档客资</dt><dd>{{ customerProfile.rootCustomerCode }}</dd></div><div><dt>主联系方式</dt><dd>{{ customerProfile.primaryContactInfo || '待补充' }}</dd></div><div><dt>历史需求</dt><dd>{{ customerProfile.totalDemands }} 次</dd></div></dl></section>
      <section v-if="isEdit && historyDemands.length" class="status-history customer-history"><h2>历史需求</h2><div class="history-demand-list"><article v-for="item in historyDemands" :key="item.customerCode" class="history-demand-item"><button class="history-demand-card" type="button" @click="goHistoryDetail(item)"><div><strong>第 {{ item.demandSequence || 1 }} 次需求 · {{ item.customerCode }}</strong><span>{{ sourcePlatformText(item.sourcePlatform) }} · {{ addMethodText(item.addMethod) }} · 运营：{{ item.uploader || '-' }} · 销售：{{ item.assignedSales || '未分配' }}</span></div><StatusTag :status="item.status" /><small>{{ item.createdAt }}</small></button><div v-if="item.followRecords?.length" class="history-demand-follow-list"><div v-for="record in item.followRecords" :key="`${item.customerCode}-${record.createdAt}-${record.remark}`" class="history-demand-follow"><strong>{{ record.operator }}（{{ record.operatorCode }}）</strong><span>{{ record.createdAt }}</span><p>{{ record.remark }}</p></div></div><el-empty v-else class="history-demand-empty" description="该次需求暂无跟进记录" /></article></div></section>
      <section v-if="isEdit && form.followRecords?.length" class="status-history"><h2>跟踪记录</h2><el-timeline><el-timeline-item v-for="item in form.followRecords" :key="`${item.createdAt}-${item.remark}`" :timestamp="item.createdAt" placement="top"><div class="history-card follow-card"><strong>{{ item.operator }}（{{ item.operatorCode }}）</strong><p>{{ item.remark }}</p></div></el-timeline-item></el-timeline></section>
      <section v-if="isEdit" class="status-history"><h2>状态流转记录</h2><el-timeline v-if="form.statusHistory?.length"><el-timeline-item v-for="item in form.statusHistory" :key="`${item.createdAt}-${item.status}`" :timestamp="item.createdAt" placement="top"><div class="history-card"><strong>{{ item.statusText || statusText(item.status) }}</strong><span>{{ item.operator }}（{{ item.operatorCode }}）</span><p v-if="item.depositAmount">定金：{{ item.depositAmount }}</p><p v-if="item.remark">{{ item.remark }}</p></div></el-timeline-item></el-timeline><el-empty v-else description="暂无流转记录" /></section>
      <section v-if="isEdit && form.operationLogs?.length" class="status-history operation-history"><h2>操作日志</h2><el-timeline><el-timeline-item v-for="item in reversedOperationLogs" :key="`${item.createdAt}-${item.field}-${item.oldValue}-${item.newValue}`" :timestamp="item.createdAt" placement="top"><div class="history-card operation-card"><strong>{{ item.actionText || item.action }}：{{ item.fieldText || item.field }}</strong><span>{{ item.operator }}（{{ item.operatorCode }}）</span><p><b>修改前：</b>{{ item.oldValue || '-' }}</p><p><b>修改后：</b>{{ item.newValue || '-' }}</p></div></el-timeline-item></el-timeline></section>
      <div class="form-actions"><el-button @click="$router.back()">返回</el-button><el-button type="primary" :loading="submitting" @click="submit()">{{ isEdit ? '保存设置' : '提交入库' }}</el-button></div>
    </el-form>
    <el-dialog v-model="preview.visible" :title="preview.name" width="min(760px, 92vw)"><img class="preview-dialog-image" :src="preview.url" :alt="preview.name" /></el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { createClue, getClue, getClueHistory, updateClue, uploadClueImage } from '../api/clue'
import { recognizeWechatId } from '../api/ocr'
import StatusTag from '../components/StatusTag.vue'
import { resolveAssetUrl } from '../utils/assets'
import { getStoredUser } from '../utils/session'
import { addMethodText, sourcePlatformText, statusText } from '../utils/status'
import { showError } from '../utils/feedback'

const route = useRoute()
const router = useRouter()
const submitting = ref(false)
const ocrRecognizing = ref(false)
const lastOcrImageKey = ref('')
const historyLoading = ref(false)
const historyDemands = ref([])
const customerProfile = reactive({ rootCustomerCode: '', primaryContactInfo: '', totalDemands: 0 })
const isEdit = computed(() => Boolean(route.params.customerCode))
const originalStatus = ref('NEW')
const originalContactInfo = ref('')
const reversedOperationLogs = computed(() => [...(form.operationLogs || [])].reverse())

const form = reactive({
  customerCode: '', sourcePlatform: 'DOUYIN', addMethod: 'ACTIVE', contactInfo: '', hasWechatId: true, status: 'NEW', douyinImages: [], wechatImages: [], remark: '', repeatDemand: false,
  assignedSales: '', assignedSalesEmployeeCode: '', depositAmount: '', remainingBalance: '', statusRemark: '', refundAmount: '', refundedAt: '', landingAt: '', landingRemark: '',
  statusHistory: [], followRecords: [], assignLogs: [], operationLogs: []
})
const preview = reactive({ visible: false, name: '', url: '' })

onMounted(loadDetail)

async function loadDetail() {
  if (!isEdit.value) return
  const res = await getClue(route.params.customerCode)
  if (!res.data) {
    await showError('客户线索不存在')
    router.push('/clues')
    return
  }
  const loadedStatus = normalizeStatus(res.data.status)
  originalStatus.value = loadedStatus
  originalContactInfo.value = res.data.contactInfo || ''
  Object.assign(form, {
    ...res.data,
    addMethod: res.data.addMethod || 'ACTIVE',
    hasWechatId: res.data.hasWechatId !== false,
    status: loadedStatus,
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
    Object.assign(customerProfile, {
      rootCustomerCode: res.data?.rootCustomerCode || '',
      primaryContactInfo: res.data?.primaryContactInfo || '',
      totalDemands: res.data?.totalDemands || 0
    })
    historyDemands.value = res.data?.demands || []
  } catch (error) {
    Object.assign(customerProfile, { rootCustomerCode: '', primaryContactInfo: '', totalDemands: 0 })
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
  const hasImages = form.douyinImages.length || form.wechatImages.length
  if (hasImages) ElMessage.info('正在上传图片并保存客户线索，请不要关闭页面...')
  const statusValid = await validateStatusFields()
  if (!statusValid) {
    submitting.value = false
    return
  }
  try {
    const payload = buildPayload(allowRepeatDemand)
    if (isEdit.value) {
      const res = await updateClue(form.customerCode, payload)
      if (!res?.data?.customerCode) throw new Error('保存成功但未返回客户详情，请刷新后重试')
      Object.assign(form, {
        ...res.data,
        addMethod: res.data?.addMethod || 'ACTIVE',
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
      originalContactInfo.value = res.data?.contactInfo || ''
      ElMessage.success('客户线索已更新')
      router.back()
    } else {
      await createClue(payload)
      ElMessage.success(allowRepeatDemand ? '老客户新需求已保存' : '客户线索已提交')
      router.replace(postCreatePath())
    }
  } catch (error) {
    if (!isEdit.value && error?.message?.includes('请不要重复保存')) {
      const action = await ElMessageBox.confirm(`${error.message}。如果这是客户本次新的出行需求，可以保存为本次运营的新增客资。`, '发现老客户', {
        confirmButtonText: '保存为本次新需求', cancelButtonText: '取消', type: 'warning'
      }).catch(() => 'cancel')
      if (action !== 'cancel') await submit(true)
      return
    }
    if (isEdit.value && error?.message?.includes('请不要重复保存')) {
      const action = await ElMessageBox.confirm(`${error.message}。请选择这次联系方式的处理方式。`, '发现已有客户', {
        confirmButtonText: '合并为已有客户需求',
        cancelButtonText: '不保存联系方式',
        distinguishCancelAndClose: true,
        type: 'warning'
      }).catch((reason) => reason)
      if (action === 'confirm') {
        await submit(true)
        return
      }
      if (action === 'cancel') {
        form.contactInfo = originalContactInfo.value
        await submit(false)
        return
      }
      return
    }
    await showError(error.message || '保存失败，请检查网络后重试')
  } finally {
    submitting.value = false
  }
}

function buildPayload(allowRepeatDemand = false) {
  return { ...form, contactInfo: form.contactInfo.trim(), allowRepeatDemand, douyinImages: compactImages(form.douyinImages), wechatImages: compactImages(form.wechatImages) }
}

async function validateStatusFields() {
  if ((form.status === 'REFUNDED' || form.status === 'LANDED') && !hasDepositFlow()) {
    await showError(`该订单没交定金，不能直接${statusText(form.status)}`)
    return false
  }
  if (form.status === 'DEPOSIT_PAID' && !String(form.depositAmount || '').trim()) {
    await showError('当前状态为已交定金时，请填写定金金额')
    return false
  }
  if (form.status === 'REFUNDED' && !String(form.refundAmount || '').trim()) {
    await showError('当前状态为退单时，请填写退单金额')
    return false
  }
  if (form.status === 'REFUNDED' && !String(form.statusRemark || '').trim()) {
    await showError('当前状态为退单时，请填写退单备注')
    return false
  }
  if (form.status === 'LANDED' && !String(form.landingAt || '').trim()) {
    await showError('当前状态为已落地时，请填写落地时间')
    return false
  }
  return true
}

function hasDepositFlow() {
  if (['DEPOSIT_PAID', 'REFUNDED', 'LANDED'].includes(originalStatus.value)) return true
  if (String(form.depositAmount || '').trim() && originalStatus.value === 'DEPOSIT_PAID') return true
  return (form.statusHistory || []).some((item) => normalizeStatus(item?.status) === 'DEPOSIT_PAID' || String(item?.depositAmount || '').trim())
}

function postCreatePath() {
  const user = getStoredUser()
  if (user?.role === 'ADMIN' || user?.menuPermissions?.includes('CLUES')) return '/clues'
  if (user?.menuPermissions?.includes('ASSIGN')) return '/assign'
  return '/index'
}

async function syncUploadList(field, fileList) {
  try {
    const normalized = await Promise.all(fileList.map((file, index) => normalizeUploadFile(file, index, field)))
    form[field] = normalized
    if (field === 'douyinImages') {
      await recognizeFirstDouyinImage()
    }
  } catch (error) {
    await showError(error.message || '图片上传失败，请重试')
  }
}


async function recognizeFirstDouyinImage() {
  if (!form.hasWechatId || !form.douyinImages.length) return
  const firstImage = [...form.douyinImages].sort((left, right) => (left.sortOrder || 0) - (right.sortOrder || 0))[0]
  if (!firstImage?.url?.startsWith('data:image')) return
  const imageKey = `${firstImage.uid || firstImage.name}-${firstImage.url.length}`
  if (imageKey === lastOcrImageKey.value) return
  lastOcrImageKey.value = imageKey
  ocrRecognizing.value = true
  ElMessage.info('正在识别第一张抖音截图...')
  try {
    const res = await recognizeWechatId(firstImage.url, firstImage.storageUrl || '')
    const candidates = res.data?.candidates || []
    if (candidates.length === 1) {
      form.contactInfo = candidates[0]
      ElMessage.success(`已识别并填入微信号：${candidates[0]}`)
      return
    }
    if (candidates.length > 1) {
      const selected = await selectOcrCandidate(candidates)
      if (selected) {
        form.contactInfo = selected
      }
      return
    }
    ElMessage.warning(res.data?.message || '未识别到微信号或手机号，请手动填写联系方式')
  } catch (error) {
    ElMessageBox.alert(error.message || 'OCR 识别失败，请手动填写联系方式', 'OCR 识别失败', {
      confirmButtonText: '我知道了',
      type: 'warning'
    })
  } finally {
    ocrRecognizing.value = false
  }
}

async function selectOcrCandidate(candidates) {
  const options = candidates.map((item) => `<label class="ocr-candidate"><input type="radio" name="ocrCandidate" value="${escapeHtml(item)}" /> <span>${escapeHtml(item)}</span></label>`).join('')
  let selectedValue = ''
  const action = await ElMessageBox.confirm(`<div class="ocr-candidate-list">${options}</div>`, '请选择识别到的微信号', {
    dangerouslyUseHTMLString: true,
    confirmButtonText: '填入联系方式',
    cancelButtonText: '取消',
    type: 'info',
    beforeClose: (action, instance, done) => {
      if (action !== 'confirm') {
        done()
        return
      }
      const checked = document.querySelector('input[name="ocrCandidate"]:checked')
      if (!checked) {
        ElMessage.warning('请先选择一个识别结果')
        return
      }
      selectedValue = checked.value
      done()
    }
  }).catch(() => 'cancel')
  if (action === 'cancel') return ''
  return selectedValue
}

function escapeHtml(value) {
  return String(value || '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]))
}
async function normalizeUploadFile(file, index = 0, field = '') {
  const sortOrder = Number.isFinite(file.sortOrder) ? file.sortOrder : index
  if (file.url?.startsWith('data:')) return { name: file.name, url: file.url, storageUrl: file.storageUrl, uid: file.uid, sortOrder }
  if (file.raw) {
    const [previewUrl, uploadRes] = await Promise.all([
      readImageFile(file.raw),
      uploadClueImage(file.raw, field === 'wechatImages' ? 'WECHAT' : 'DOUYIN', sortOrder)
    ])
    const uploaded = uploadRes.data || {}
    return {
      name: uploaded.name || file.name,
      url: previewUrl,
      storageUrl: uploaded.url,
      uid: uploaded.uid || file.uid,
      sortOrder,
      sizeBytes: uploaded.sizeBytes,
      contentType: uploaded.contentType
    }
  }
  return { name: file.name, url: resolveAssetUrl(file.storageUrl || file.url), storageUrl: file.storageUrl || file.url, uid: file.uid, sortOrder }
}
function previewImage(file) { preview.name = file.name; preview.url = resolveAssetUrl(file.url); preview.visible = true }
function normalizeSavedImages(images = []) { return images.map((image, index) => ({ name: image.name || `图片${index + 1}`, url: resolveAssetUrl(image.url), storageUrl: image.url, uid: image.uid || `${Date.now()}-${index}`, sortOrder: Number.isFinite(image.sortOrder) ? image.sortOrder : index })).sort((left, right) => left.sortOrder - right.sortOrder) }
function compactImages(images = []) { return images.map((image, index) => ({ name: image.name, url: image.storageUrl || image.url, uid: image.uid, sortOrder: Number.isFinite(image.sortOrder) ? image.sortOrder : index })) }
function readImageFile(file) {
  if (!file?.type?.startsWith('image/')) {
    return readFileAsDataUrl(file)
  }
  return compressImageFile(file).catch(() => readFileAsDataUrl(file))
}

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

async function compressImageFile(file) {
  const imageUrl = URL.createObjectURL(file)
  try {
    const image = await loadImage(imageUrl)
    const maxSide = 1280
    const scale = Math.min(1, maxSide / Math.max(image.naturalWidth, image.naturalHeight))
    const width = Math.max(1, Math.round(image.naturalWidth * scale))
    const height = Math.max(1, Math.round(image.naturalHeight * scale))
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    context.drawImage(image, 0, 0, width, height)
    return canvas.toDataURL('image/jpeg', 0.78)
  } finally {
    URL.revokeObjectURL(imageUrl)
  }
}

function loadImage(url) {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = reject
    image.src = url
  })
}
function normalizeStatus(status) { if (status === 'TO_DEAL') return 'FOLLOWING'; if (status === 'DEALED') return 'DEPOSIT_PAID'; return status || 'NEW' }
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

.status-wide {
  grid-column: 1 / -1;
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

:global(.ocr-candidate-list) {
  display: grid;
  gap: 10px;
  margin-top: 4px;
}

:global(.ocr-candidate) {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.78);
  cursor: pointer;
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

.customer-profile-card dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.customer-profile-card div {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
}

.customer-profile-card dt {
  color: var(--text-muted);
  font-size: 12px;
}

.customer-profile-card dd {
  margin: 6px 0 0;
  color: var(--text-strong);
  font-weight: 800;
  overflow-wrap: anywhere;
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

.history-demand-item {
  display: grid;
  gap: 10px;
  padding: 10px;
  border: 1px solid rgba(178, 174, 250, 0.24);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.58);
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

.history-demand-follow-list {
  display: grid;
  gap: 8px;
}

.history-demand-follow {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(79, 200, 120, 0.11), rgba(178, 174, 250, 0.13));
}

.history-demand-follow strong {
  color: var(--text-strong);
  font-size: 13px;
}

.history-demand-follow span,
.history-demand-follow p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.history-demand-empty {
  --el-empty-padding: 8px 0;
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

  .customer-profile-card dl {
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
