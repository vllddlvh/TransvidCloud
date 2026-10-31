import { expect, test, type Page } from '@playwright/test'
import { auxiliaryOrigins, hasLocalAuxiliaryServers, primaryOrigin } from './support'

async function login(page: Page, origin = primaryOrigin) {
  await page.goto(`${origin}/projects`)
  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel('Email address').fill('demo@transvidcloud.test')
  await page.getByLabel('Password', { exact: true }).fill('DemoPass123!')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Projects', exact: true })).toBeVisible()
}

async function upload(page: Page, name: string) {
  await page.getByRole('button', { name: 'Upload video' }).click()
  await page.getByLabel('Video file').setInputFiles({ name, mimeType: 'video/mp4', buffer: Buffer.alloc(4096, 1) })
  await page.getByRole('button', { name: 'Start upload' }).click()
  await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 15000 })
  return page.getByRole('link').filter({ has: page.getByRole('heading', { name }) })
}

test.describe('clipboard actions', () => {
  test.use({ permissions: ['clipboard-read', 'clipboard-write'] })

  test('copies the absolute master URL and the exact embed iframe', async ({ page }) => {
    await login(page)
    await page.goto('/videos/demo-completed')
    await expect(page.locator('[data-manifest-state="parsed"]')).toBeVisible({ timeout: 15000 })
    await page.getByRole('button', { name: 'Copy streaming link' }).click()
    await expect(page.getByRole('status')).toContainText('Streaming link copied.')
    const origin = await page.evaluate(() => location.origin)
    expect(await page.evaluate(() => navigator.clipboard.readText()))
      .toBe(`${origin}/media/demo-project/demo-completed/hls/master.m3u8`)
    await page.getByRole('button', { name: 'Copy embed code' }).click()
    await expect(page.getByRole('status')).toContainText('Embed code copied.')
    expect(await page.evaluate(() => navigator.clipboard.readText()))
      .toBe(`<iframe src="${origin}/embed?video=demo-completed" width="640" height="360" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen frameborder="0"></iframe>`)
  })
})

test('uploading a video reaches COMPLETED and the detail player parses the master manifest', async ({ page }) => {
  await login(page)
  await page.getByRole('link').filter({ has: page.getByRole('heading', { name: 'Getting started', exact: true }) }).click()
  const row = await upload(page, 'week2-complete.mp4')
  await expect(row.getByText('Pending', { exact: true })).toBeVisible()
  await expect(row.getByText('Completed', { exact: true })).toBeVisible({ timeout: 35000 })
  await row.click()
  await expect(page.locator('[data-manifest-state="parsed"]')).toBeVisible({ timeout: 15000 })
  await expect(page.getByRole('button', { name: 'Copy streaming link' })).toBeVisible()
})

test('embed is public, chrome-free, and never autoplays', async ({ page }) => {
  await page.goto('/embed?video=demo-completed')
  await expect(page.locator('[data-manifest-state="parsed"]')).toBeVisible({ timeout: 15000 })
  await expect(page.getByRole('navigation')).toHaveCount(0)
  expect(await page.locator('video').evaluate((video: HTMLVideoElement) => ({
    autoplay: video.autoplay, paused: video.paused,
  }))).toEqual({ autoplay: false, paused: true })
})

test('embed shows a compact unavailable state for missing or incomplete videos', async ({ page }) => {
  await page.goto('/embed')
  await expect(page.getByRole('heading', { name: 'Video is not available yet.' })).toBeVisible()
  await page.goto('/embed?video=demo-processing')
  await expect(page.getByRole('heading', { name: 'Video is not available yet.' })).toBeVisible()
  await expect(page.locator('video')).toHaveCount(0)
  await expect(page.getByRole('navigation')).toHaveCount(0)
})

test('fail mode surfaces TRANSCODE_FAILED with automatic-retry guidance', async ({ page }) => {
  test.skip(!hasLocalAuxiliaryServers, 'Requires the local transcode-failure server')
  await login(page, auxiliaryOrigins.processFailure)
  await page.goto(`${auxiliaryOrigins.processFailure}/projects/demo-project/videos`)
  const row = await upload(page, 'week2-failure.mp4')
  await expect(row.getByText('Transcode failed', { exact: true })).toBeVisible({ timeout: 35000 })
  await row.click()
  await expect(page.getByText('Error code: TRANSCODE_FAILED')).toBeVisible()
  await expect(page.getByText(/retry processing automatically/i)).toBeVisible()
})
