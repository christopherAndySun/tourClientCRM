import { expect, test } from '@playwright/test'

const menus = [
  { code: 'STATS', groupCode: 'MANAGE', groupName: '管理', name: '数据统计', path: '/index', sort: 10, enabled: true },
  { code: 'CLUES', groupCode: 'BUSINESS', groupName: '业务', name: '客户列表', path: '/clues', sort: 20, enabled: true },
  { code: 'CLUE_CREATE', groupCode: 'BUSINESS', groupName: '业务', name: '新增客户', path: '/clues/create', sort: 30, enabled: true },
  { code: 'ASSIGN', groupCode: 'BUSINESS', groupName: '业务', name: '分配管理', path: '/assign', sort: 40, enabled: true }
]

test('未登录访问业务页面会跳转到登录页', async ({ page }) => {
  await page.goto('/clues')

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: '账号登录' })).toBeVisible()
})

test('已登录后默认进入数据统计且页面没有横向滚动', async ({ page }) => {
  await mockLoggedInApi(page)
  await page.addInitScript(() => {
    localStorage.setItem('crm_user', JSON.stringify({
      name: '小白',
      employeeCode: 'XA',
      role: 'MEMBER',
      position: 'OPERATION',
      menuPermissions: ['STATS', 'CLUES', 'CLUE_CREATE', 'ASSIGN']
    }))
    localStorage.setItem('crm_session_expires_at', String(Date.now() + 24 * 60 * 60 * 1000))
  })

  await page.goto('/index')

  await expect(page.getByText('数据统计').first()).toBeVisible()
  const isMobileViewport = await page.evaluate(() => window.matchMedia('(max-width: 760px)').matches)
  if (isMobileViewport) {
    await expect(page.getByLabel('打开菜单')).toBeVisible()
  } else {
    await expect(page.locator('.user-bar').getByText('XA')).toBeVisible()
  }
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  expect(overflow).toBeLessThanOrEqual(1)
})

async function mockLoggedInApi(page) {
  await page.route('**/api/menus', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({ success: true, data: menus })
  }))
  await page.route('**/api/clues/stats**', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({
      success: true,
      data: {
        totalCount: 3,
        todayCount: 1,
        repeatDemandCount: 0,
        statusCounts: { NEW: 2, FOLLOWING: 1 },
        uploaderCounts: {},
        salesCounts: {}
      }
    })
  }))
  await page.route('**/api/clues/performance**', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({
      success: true,
      data: [
        {
          employeeCode: 'XA',
          employeeName: '小白',
          role: 'MEMBER',
          position: 'OPERATION',
          leaderEmployeeCode: '',
          totalCount: 3,
          todayCount: 1,
          repeatDemandCount: 0,
          dealedCount: 0,
          refundedCount: 0,
          landedCount: 0,
          invalidCount: 0
        }
      ]
    })
  }))
}
