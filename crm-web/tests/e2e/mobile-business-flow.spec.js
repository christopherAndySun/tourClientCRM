import { expect, test } from '@playwright/test'

const menus = [
  { code: 'STATS', groupCode: 'MANAGE', groupName: '管理', name: '数据统计', path: '/index', sort: 10, enabled: true },
  { code: 'CLUES', groupCode: 'BUSINESS', groupName: '业务', name: '客户列表', path: '/clues', sort: 20, enabled: true },
  { code: 'CLUE_CREATE', groupCode: 'BUSINESS', groupName: '业务', name: '新增客户', path: '/clues/create', sort: 30, enabled: true },
  { code: 'ASSIGN', groupCode: 'BUSINESS', groupName: '业务', name: '分配管理', path: '/assign', sort: 40, enabled: true }
]

test.skip(({ isMobile }) => !isMobile, '移动端业务流程只在 mobile 项目里验证')

test('移动端新增客户会上传图片并提交线索', async ({ page }) => {
  await loginAs(page, {
    name: '小白',
    employeeCode: 'XA',
    role: 'MEMBER',
    position: 'OPERATION',
    menuPermissions: ['STATS', 'CLUES', 'CLUE_CREATE', 'ASSIGN']
  })
  await mockCommonApis(page)

  let uploaded = false
  let createdPayload = null
  await page.route('**/api/uploads/images', (route) => {
    uploaded = true
    return fulfillApi(route, success({
      name: 'douyin.jpg',
      url: '/uploads/clues/douyin.jpg',
      uid: 'upload-1',
      sortOrder: 0,
      sizeBytes: 1234,
      contentType: 'image/jpeg'
    }))
  })
  await page.route('**/api/ocr/wechat-id', (route) => fulfillApi(route, success({
    candidates: ['wx123456'],
    fullText: '微信号 wx123456',
    message: '识别成功'
  })))
  await page.route('**/api/clues', async (route) => {
    if (route.request().method() !== 'POST') return route.continue()
    createdPayload = await route.request().postDataJSON()
    return fulfillApi(route, success({ customerCode: 'XA0706-01', ...createdPayload }))
  })

  await page.goto('/clues/create')
  await page.locator('.crm-picture-upload input[type="file"]').first().setInputFiles({
    name: 'douyin.jpg',
    mimeType: 'image/jpeg',
    buffer: Buffer.from([0xff, 0xd8, 0xff, 0xd9])
  })
  await expect.poll(() => uploaded).toBe(true)
  await page.locator('.form-actions .el-button--primary').click()
  await expect.poll(() => createdPayload?.douyinImages?.[0]?.url).toBe('/uploads/clues/douyin.jpg')
  await expect.poll(() => createdPayload?.contactInfo).toBe('wx123456')
})

test('移动端分配管理可以领取并释放线索', async ({ page }) => {
  await loginAs(page, {
    name: '销售A',
    employeeCode: 'SA',
    role: 'EMPLOYEE',
    position: 'SALES',
    menuPermissions: ['STATS', 'ASSIGN']
  })
  await mockCommonApis(page)

  let mode = 'public'
  let claimCalled = false
  let releaseCalled = false
  await page.route('**/api/auth/sales', (route) => fulfillApi(route, success([
    { name: '销售A', employeeCode: 'SA', position: 'SALES' }
  ])))
  await page.route('**/api/clues/sales-pool/public**', (route) => fulfillApi(route, success(pageData(mode === 'public' ? [publicClue()] : []))))
  await page.route('**/api/clues/sales-pool/mine**', (route) => fulfillApi(route, success(pageData(mode === 'mine' ? [mineClue()] : []))))
  await page.route('**/api/clues/XA0706-01/claim', (route) => {
    claimCalled = true
    mode = 'mine'
    return fulfillApi(route, success(mineClue()))
  })
  await page.route('**/api/clues/XA0706-01/release', (route) => {
    releaseCalled = true
    mode = 'public'
    return fulfillApi(route, success(publicClue()))
  })

  await page.goto('/assign')
  await expect(page.locator('.mobile-list .clue-card')).toBeVisible()
  await page.locator('.mobile-list .clue-card .table-action').first().click()
  await page.locator('.el-message-box .el-button--primary').click({ force: true })
  await expect.poll(() => claimCalled).toBe(true)

  await page.locator('.pool-tab').nth(1).click()
  await expect(page.locator('.mobile-list .clue-card')).toBeVisible()
  await page.locator('.mobile-list .clue-card .table-action').first().click()
  await page.locator('.el-message-box textarea').fill('移动端释放测试')
  await page.locator('.el-message-box .el-button--primary').click({ force: true })
  await expect.poll(() => releaseCalled).toBe(true)
})

test('移动端客户详情可以保存修改并打开客户档案', async ({ page }) => {
  await loginAs(page, {
    name: '小白',
    employeeCode: 'XA',
    role: 'MEMBER',
    position: 'OPERATION',
    menuPermissions: ['STATS', 'CLUES', 'CLUE_CREATE', 'ASSIGN']
  })
  await mockCommonApis(page)

  let saved = false
  await page.route('**/api/clues/XA0706-01', async (route) => {
    if (route.request().method() === 'GET') {
      return fulfillApi(route, success(detailClue()))
    }
    if (route.request().method() === 'PUT') {
      saved = true
      return fulfillApi(route, success(detailClue()))
    }
    return route.continue()
  })
  await page.route('**/api/clues/XA0706-01/history', (route) => fulfillApi(route, success(profileData())))
  await page.route('**/api/customers/XA0706-01', (route) => fulfillApi(route, success(profileData())))

  await page.goto('/clues/XA0706-01')
  await expect(page.locator('.customer-profile-card')).toBeVisible()
  await page.locator('.customer-profile-card .el-button').click()
  await expect(page).toHaveURL(/\/customers\/XA0706-01$/)
  await expect(page.locator('.profile-summary')).toBeVisible()
  await page.goto('/clues/XA0706-01')
  await page.locator('.form-actions .el-button--primary').click()
  await expect.poll(() => saved).toBe(true)
})

async function loginAs(page, user) {
  await page.route('**/api/auth/me', (route) => fulfillApi(route, success(user)))
  await page.addInitScript(() => {
    window.__CRM_DISABLE_REALTIME__ = true
    localStorage.setItem('crm_session_expires_at', String(Date.now() + 24 * 60 * 60 * 1000))
    localStorage.removeItem('crm_user')
  })
}

async function mockCommonApis(page) {
  await page.route('**/api/menus', (route) => fulfillApi(route, success(menus)))
  await page.route('**/ws/**', (route) => route.abort())
}

function success(data) {
  return { success: true, data }
}

function fulfillApi(route, body) {
  return route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(body)
  })
}

function pageData(records) {
  return { records, total: records.length, page: 1, pageSize: 10, hasMore: false }
}

function publicClue() {
  return {
    customerCode: 'XA0706-01',
    sourcePlatform: 'DOUYIN',
    addMethod: 'ACTIVE',
    contactInfo: 'wx123',
    uploader: '小白',
    uploaderEmployeeCode: 'XA',
    assignedSales: '',
    assignedSalesEmployeeCode: '',
    status: 'NEW',
    createdAt: '2026-07-06 10:00',
    assignLogs: []
  }
}

function mineClue() {
  return {
    ...publicClue(),
    status: 'FOLLOWING',
    assignedSales: '销售A',
    assignedSalesEmployeeCode: 'SA'
  }
}

function detailClue() {
  return {
    ...publicClue(),
    douyinImages: [],
    wechatImages: [],
    statusHistory: [],
    followRecords: [],
    operationLogs: []
  }
}

function profileData() {
  return {
    customerKey: 'wx123',
    rootCustomerCode: 'XA0706-01',
    primaryContactInfo: 'wx123',
    totalDemands: 1,
    demands: [detailClue()]
  }
}
