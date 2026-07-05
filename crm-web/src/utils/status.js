export function statusText(status) {
  return {
    NEW: '新录入',
    OLD_NEW: '老客新需求',
    FOLLOWING: '跟进中',
    TO_DEAL: '跟进中',
    PASSED: '已通过',
    DEPOSIT_PAID: '已交定金',
    DEALED: '已交定金',
    INVALID: '无效用户',
    REFUNDED: '退单',
    LANDED: '已落地',
    DELETED: '已删除'
  }[status] || status || '-'
}

export function statusType(status) {
  return {
    NEW: 'info',
    OLD_NEW: 'warning',
    FOLLOWING: 'primary',
    TO_DEAL: 'primary',
    PASSED: 'success',
    DEPOSIT_PAID: 'success',
    DEALED: 'success',
    INVALID: 'danger',
    REFUNDED: 'danger',
    LANDED: 'success',
    DELETED: 'info'
  }[status] || 'info'
}

export function sourcePlatformText(sourcePlatform) {
  return {
    DOUYIN: '抖音',
    XIAOHONGSHU: '小红书'
  }[sourcePlatform] || '抖音'
}

export function addMethodText(addMethod) {
  return {
    ACTIVE: '主动',
    PASSIVE: '被动',
    GUIDE: '领队'
  }[addMethod] || '主动'
}
