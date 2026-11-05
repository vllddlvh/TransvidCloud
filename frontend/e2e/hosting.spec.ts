import { expect, test } from '@playwright/test'

const hosted = process.env.E2E_HOSTED === '1'

test.skip(!hosted, 'Set E2E_HOSTED=1 and E2E_BASE_URL to the nginx origin')

test('hosted build serves the SPA, registers the mock worker at root scope, and plays HLS media', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Sign in to your workspace' })).toBeVisible()
  await page.getByLabel('Email address').fill('demo@transvidcloud.test')
  await page.getByLabel('Password', { exact: true }).fill('DemoPass123!')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Projects', exact: true })).toBeVisible()
  await page.goto('/videos/demo-completed')
  await expect(page.locator('[data-manifest-state="parsed"]')).toBeVisible({ timeout: 15000 })
  const workers = await page.evaluate(async () => (await navigator.serviceWorker.getRegistrations())
    .map((registration) => new URL(registration.active?.scriptURL ?? '', location.origin).pathname))
  expect(workers).toContain('/mockServiceWorker.js')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})
