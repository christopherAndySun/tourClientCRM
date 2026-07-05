export const FALLBACK_MENUS = [
  { code: 'CLUES', groupCode: 'BUSINESS', groupName: '业务', name: '客户列表', description: '个人客户线索工作区', path: '/clues', sort: 10, enabled: true },
  { code: 'CLUE_CREATE', groupCode: 'BUSINESS', groupName: '业务', name: '新增客户', description: '录入客户和老客新需求', path: '/clues/create', sort: 20, enabled: true },
  { code: 'ASSIGN', groupCode: 'BUSINESS', groupName: '业务', name: '分配管理', description: '销售池领取、分配、转派和释放', path: '/assign', sort: 30, enabled: true },
  { code: 'ASSIGN_LOGS', groupCode: 'BUSINESS', groupName: '业务', name: '分配日志', description: '查看领取、释放、转派和抢单冲突记录', path: '/assign-logs', sort: 35, enabled: true },
  { code: 'DEALS', groupCode: 'BUSINESS', groupName: '业务', name: '成交记录', description: '登记、编辑、退单成交数据', path: '/deals', sort: 40, enabled: true },
  { code: 'THIRD_PARTY_POOL', groupCode: 'BUSINESS', groupName: '业务', name: '三方下载池', description: '临时给第三方下载 Word 客资', path: '/third-party-pool', sort: 45, enabled: true },
  { code: 'STATS', groupCode: 'MANAGE', groupName: '管理', name: '数据统计', description: '团队数据统计面板', path: '/index', sort: 10, enabled: true },
  { code: 'PERFORMANCE', groupCode: 'MANAGE', groupName: '管理', name: '员工绩效', description: '员工绩效和明细查询', path: '/performance', sort: 20, enabled: true },
  { code: 'OPERATION_LOGS', groupCode: 'MANAGE', groupName: '管理', name: '操作日志', description: '查看客户字段修改前后记录', path: '/operation-logs', sort: 25, enabled: true },
  { code: 'USERS', groupCode: 'MANAGE', groupName: '管理', name: '账号管理', description: '员工账号与权限配置', path: '/users', sort: 30, enabled: true },
  { code: 'ORG', groupCode: 'MANAGE', groupName: '管理', name: '组织架构', description: '查看和调整部门、组长、组员关系', path: '/org', sort: 40, enabled: true },
  { code: 'MENUS', groupCode: 'MANAGE', groupName: '管理', name: '菜单管理', description: '维护菜单名称、排序和启停', path: '/menus', sort: 50, enabled: true },
  { code: 'SETTINGS', groupCode: 'MANAGE', groupName: '管理', name: '系统设置', description: '维护 OCR 等系统级配置', path: '/settings', sort: 60, enabled: true },
  { code: 'SYSTEM_AUDIT', groupCode: 'MANAGE', groupName: '管理', name: '系统审计', description: '查看登录、导出、下载和权限变更记录', path: '/system-audit', sort: 70, enabled: true }
]

export function groupMenus(menus = FALLBACK_MENUS) {
  const groups = new Map()
  menus
    .filter(Boolean)
    .slice()
    .sort((a, b) => groupOrder(a.groupCode) - groupOrder(b.groupCode) || (a.sort || 0) - (b.sort || 0))
    .forEach((menu) => {
      if (!groups.has(menu.groupCode)) {
        groups.set(menu.groupCode, { code: menu.groupCode, title: menu.groupName, children: [] })
      }
      groups.get(menu.groupCode).children.push(menu)
    })
  return Array.from(groups.values())
}

export function mergeMenus(source = []) {
  const merged = new Map()
  ;(Array.isArray(source) ? source : []).filter(Boolean).forEach((menu) => {
    merged.set(menu.code, menu)
  })
  FALLBACK_MENUS.forEach((menu) => {
    if (!merged.has(menu.code)) {
      merged.set(menu.code, menu)
    }
  })
  return Array.from(merged.values())
}

function groupOrder(code) {
  return code === 'BUSINESS' ? 1 : code === 'MANAGE' ? 2 : 99
}
