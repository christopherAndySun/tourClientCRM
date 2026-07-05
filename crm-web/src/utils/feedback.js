async function messageBox() {
  const { ElMessageBox } = await import('element-plus')
  return ElMessageBox
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
