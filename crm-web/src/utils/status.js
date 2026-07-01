export function statusText(status) {
  return {
    NEW: '新录入',
    FOLLOWING: '跟进中',
    TO_DEAL: '跟进中',
    DEPOSIT_PAID: '已交定金',
    DEALED: '已交定金',
    INVALID: '无效用户',
    REFUNDED: '退单',
    LANDED: '已落地'
  }[status] || status || '-'
}

export function statusType(status) {
  return {
    NEW: 'info',
    FOLLOWING: 'primary',
    TO_DEAL: 'primary',
    DEPOSIT_PAID: 'success',
    DEALED: 'success',
    INVALID: 'danger',
    REFUNDED: 'danger',
    LANDED: 'success'
  }[status] || 'info'
}

export function sourcePlatformText(sourcePlatform) {
  return {
    DOUYIN: '抖音',
    XIAOHONGSHU: '小红书'
  }[sourcePlatform] || '抖音'
}
