import { expect, test, type Page } from '@playwright/test'
import { auxiliaryOrigins, hasLocalAuxiliaryServers, primaryOrigin } from './support'

async function signIn(page: Page, origin: string) {
  await page.goto(`${origin}/login`)
  await page.getByLabel('Email address').fill('demo@transvidcloud.test')
  await page.getByLabel('Password', { exact: true }).fill('DemoPass123!')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Getting started', exact: true })).toBeVisible()
}

test('a failed PUT retries the same video and reaches PENDING', async ({ page }) => {
  test.skip(!hasLocalAuxiliaryServers, 'Requires the local PUT-failure server')
  await signIn(page, auxiliaryOrigins.putFailure)
  await page.goto(`${auxiliaryOrigins.putFailure}/projects/demo-project/videos`)
  await page.getByRole('button', { name: 'Upload video' }).click()
  await page.getByLabel('Video file').setInputFiles({ name: 'retry.mp4', mimeType: 'video/mp4', buffer: Buffer.alloc(1024, 1) })
  await page.getByRole('button', { name: 'Start upload' }).click()
  await expect(page.getByRole('alert')).toContainText('interrupted')
  await page.getByRole('button', { name: 'Retry upload' }).click()
  await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 15000 })
  const row = page.getByRole('link').filter({ has: page.getByRole('heading', { name: 'retry.mp4' }) })
  await expect(row).toHaveCount(1)
  await expect(row.getByText('Pending', { exact: true })).toBeVisible()
})

test('real mode without a backend reports an error without falling back to mocks', async ({ page }) => {
  test.skip(!hasLocalAuxiliaryServers, 'Requires the local real-mode server')
  await page.goto(`${auxiliaryOrigins.real}/login`)
  await page.getByLabel('Email address').fill('demo@transvidcloud.test')
  await page.getByLabel('Password', { exact: true }).fill('DemoPass123!')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page).toHaveURL(/\/login$/)
  expect(await page.evaluate(() => localStorage.getItem('tvc.session'))).toBeNull()
  expect(await page.evaluate(async () => (await navigator.serviceWorker.getRegistrations()).length)).toBe(0)
})

test('desktop navigation toggles and releases content space', async ({ page }) => {
  await signIn(page, primaryOrigin)
  const toggle = page.getByRole('button', { name: 'Toggle navigation' })
  await expect(toggle).toHaveAttribute('aria-expanded', 'true')
  await toggle.click()
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await expect(page.locator('.sidebar')).not.toBeInViewport()
  await toggle.click()
  await expect(page.getByRole('navigation')).toBeInViewport()
})

test('reload of an expired session explains why sign-in is needed', async ({ page }) => {
  await signIn(page, primaryOrigin)
  await page.evaluate(() => {
    const session = JSON.parse(localStorage.getItem('tvc.session')!)
    session.expiresAt = new Date(Date.now() - 1000).toISOString()
    localStorage.setItem('tvc.session', JSON.stringify(session))
  })
  await page.reload()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('alert')).toContainText('session has expired')
})
