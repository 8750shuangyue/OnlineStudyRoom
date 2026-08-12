import { expect, test } from '@playwright/test'

const username = `e2e${Date.now()}`

test('注册 → 房间列表 → 创建房间 → 首页仪表盘', async ({ page }) => {
  await page.goto('/register')
  await page.getByLabel(/用户名/).fill(username)
  await page.getByLabel(/密码/).fill('secret123')
  await page.getByRole('button', { name: '注册并登录' }).click()

  // 首次进入会出现新手引导，先跳过
  await page.getByRole('button', { name: '跳过' }).click().catch(() => {})

  await expect(page).toHaveURL(/\/rooms/)
  await expect(page.getByText('自习室房间')).toBeVisible()

  // 创建房间
  await page.getByRole('button', { name: /创建房间/ }).click()
  await page.getByLabel(/房间名称/).fill(`冒烟房间-${username}`)
  await page.getByRole('button', { name: '创建', exact: true }).click()
  await expect(page.getByText(`冒烟房间-${username}`)).toBeVisible()

  // 首页仪表盘
  await page.getByRole('link', { name: '首页' }).click()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByText(/(早上好|下午好|晚上好|夜深了)/)).toBeVisible()
})
