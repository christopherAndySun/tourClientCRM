import { expect, test } from '@playwright/test'

const user = {
  name: 'Xiaobai',
  employeeCode: 'XA',
  role: 'MEMBER',
  position: 'OPERATION',
  menuPermissions: ['STATS', 'CLUES', 'CLUE_CREATE', 'ASSIGN']
}

const menus = [
  { code: 'STATS', groupCode: 'MANAGE', groupName: 'Manage', name: 'Stats', path: '/index', sort: 10, enabled: true },
  { code: 'CLUES', groupCode: 'BUSINESS', groupName: 'Business', name: 'Clues', path: '/clues', sort: 20, enabled: true },
  { code: 'CLUE_CREATE', groupCode: 'BUSINESS', groupName: 'Business', name: 'Create Clue', path: '/clues/create', sort: 30, enabled: true },
  { code: 'ASSIGN', groupCode: 'BUSINESS', groupName: 'Business', name: 'Assign', path: '/assign', sort: 40, enabled: true }
]

test('redirects protected pages to login when session is missing', async ({ page }) => {
  await page.goto('/clues')

  await expect(page).toHaveURL(/\/login$/)
})

test('restores logged-in shell from auth/me without user localStorage', async ({ page }) => {
  await mockLoggedInApi(page)
  await page.addInitScript(() => {
    localStorage.setItem('crm_session_expires_at', String(Date.now() + 24 * 60 * 60 * 1000))
    localStorage.removeItem('crm_user')
  })

  await page.goto('/index')

  await expect(page.locator('.app-shell')).toBeVisible()
  const storedUser = await page.evaluate(() => localStorage.getItem('crm_user'))
  expect(storedUser).toBeNull()

  const isMobileViewport = await page.evaluate(() => window.matchMedia('(max-width: 760px)').matches)
  if (isMobileViewport) {
    await expect(page.locator('.mobile-menu-button')).toBeVisible()
  } else {
    await expect(page.locator('.user-bar').getByText('XA')).toBeVisible()
  }

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  expect(overflow).toBeLessThanOrEqual(1)
})

async function mockLoggedInApi(page) {
  await page.route('**/api/auth/me', (route) => fulfillApi(route, { success: true, data: user }))
  await page.route('**/api/menus', (route) => fulfillApi(route, { success: true, data: menus }))
  await page.route('**/api/clues/stats**', (route) => fulfillApi(route, {
    success: true,
    data: {
      totalCount: 3,
      todayCount: 1,
      repeatDemandCount: 0,
      statusCounts: { NEW: 2, FOLLOWING: 1 },
      uploaderCounts: {},
      salesCounts: {}
    }
  }))
  await page.route('**/api/clues/performance**', (route) => fulfillApi(route, {
    success: true,
    data: [
      {
        employeeCode: 'XA',
        employeeName: 'Xiaobai',
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
  }))
}

function fulfillApi(route, body) {
  return route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(body)
  })
}
