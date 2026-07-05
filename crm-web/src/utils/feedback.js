async function messageBox() {
  const { ElMessageBox } = await import('element-plus')
  return ElMessageBox
}

async function message() {
  const { ElMessage } = await import('element-plus')
  return ElMessage
}

export async function showInfo(text) {
  if (!text) return
  const ElMessage = await message()
  ElMessage.info(text)
}

export async function showSuccess(text) {
  if (!text) return
  const ElMessage = await message()
  ElMessage.success(text)
}

export async function showError(message, title = '提示') {
  const ElMessageBox = await messageBox()
  return ElMessageBox.alert(message || '操作失败，请稍后重试', title, {
    confirmButtonText: '我知道了',
    type: 'warning'
  })
}

export async function confirmAction(message, title = '确认操作', options = {}) {
  const ElMessageBox = await messageBox()
  return ElMessageBox.confirm(message, title, {
    confirmButtonText: options.confirmButtonText || '确认',
    cancelButtonText: options.cancelButtonText || '取消',
    type: options.type || 'warning',
    ...options
  })
}

export async function promptAction(message, title = '确认操作', options = {}) {
  const ElMessageBox = await messageBox()
  return ElMessageBox.prompt(message, title, {
    confirmButtonText: options.confirmButtonText || '确认',
    cancelButtonText: options.cancelButtonText || '取消',
    ...options
  })
}

export async function withPageLoading(text = '处理中...') {
  const { ElLoading } = await import('element-plus')
  return ElLoading.service({
    lock: true,
    text,
    background: 'rgba(255, 255, 255, 0.72)'
  })
}

export async function runAction({
  loadingRef,
  loadingMessage = '',
  successMessage = '',
  errorMessage = '操作失败',
  onError,
  task
}) {
  if (loadingRef) loadingRef.value = true
  await showInfo(loadingMessage)
  try {
    const result = await task()
    await showSuccess(successMessage)
    return result
  } catch (error) {
    if (onError) await onError(error)
    try {
      await showError(error.message || errorMessage)
    } catch {
      // 关闭错误弹窗不应该阻断调用方的 loading 复位。
    }
    return undefined
  } finally {
    if (loadingRef) loadingRef.value = false
  }
}
