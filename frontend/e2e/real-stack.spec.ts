import { expect, test } from '@playwright/test'
import { resolve } from 'node:path'

const realStack = process.env.E2E_REAL_STACK === '1'
const sampleMp4 = resolve(process.cwd(), '../worker/testdata/sample-10s-720p.mp4')

test.skip(!realStack, 'Set E2E_REAL_STACK=1 and E2E_BASE_URL=http://localhost:8000 with full stack running')

test('completes full real-stack lifecycle: Register -> Create Project -> S3 Presigned PUT -> Worker Transcode -> HLS Playback & Embed -> Invalid Video FAILED_VALIDATION', async ({ page, browser }) => {
  test.setTimeout(120_000)
  const stamp = Date.now()
  const email = `ui-e2e-${stamp}@transvidcloud.test`

  // 1. Register new account via UI
  await page.goto('/login')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Create your account' })).toBeVisible()
  await page.getByLabel('Full name').fill('Real Stack Tester')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password').fill('DemoPass123!')
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(page.getByRole('heading', { name: 'Projects', exact: true })).toBeVisible({ timeout: 15_000 })
  await expect(page.locator('.account-name')).toHaveText('Real Stack Tester')

  // 2. Create a new project
  await page.getByRole('button', { name: 'Create project' }).first().click()
  await page.getByLabel('Project name').fill(`E2E Project ${stamp}`)
  await page.getByLabel(/Description/).fill('Full integration test on LocalStack')
  await page.getByRole('dialog').getByRole('button', { name: 'Create project' }).click()

  const projectLink = page.getByRole('link', { name: new RegExp(`E2E Project ${stamp}`) })
  await expect(projectLink).toBeVisible({ timeout: 10_000 })
  await projectLink.click()

  // 3. Upload valid MP4 video via S3 Presigned PUT URL & wait for Worker COMPLETED
  await page.getByRole('button', { name: 'Upload video' }).click()
  await page.getByLabel('Video file').setInputFiles(sampleMp4)
  await page.getByRole('button', { name: 'Start upload' }).click()
  await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 30_000 })

  const validRow = page.getByRole('link').filter({ has: page.getByRole('heading', { name: 'sample-10s-720p.mp4' }) })
  await expect(validRow).toBeVisible({ timeout: 10_000 })
  await expect(validRow.getByText('Completed', { exact: true })).toBeVisible({ timeout: 60_000 })

  // 4. Open Video Detail & verify HLS playback (hls.js MANIFEST_PARSED)
  await validRow.click()
  await expect(page.locator('[data-manifest-state="parsed"]')).toBeVisible({ timeout: 20_000 })
  const videoUrl = page.url()
  const videoId = videoUrl.split('/videos/')[1]

  // 5. Verify unauthenticated Embed page (/embed?video=...&project=...)
  const projectHref = await page.getByRole('link', { name: 'Videos' }).getAttribute('href')
  const projectId = projectHref?.split('/projects/')[1]?.split('/')[0] ?? ''
  expect(projectId).toBeTruthy()

  const anonContext = await browser.newContext()
  const anonPage = await anonContext.newPage()
  await anonPage.goto(`/embed?video=${encodeURIComponent(videoId)}&project=${encodeURIComponent(projectId)}`)
  await expect(anonPage.locator('[data-manifest-state="parsed"]')).toBeVisible({ timeout: 20_000 })
  await anonContext.close()

  // 6. Negative path: upload corrupted non-video file (.mp4) -> Worker marks FAILED_VALIDATION (NOT_A_VIDEO)
  await page.getByRole('link', { name: 'Videos' }).click()
  await page.getByRole('button', { name: 'Upload video' }).click()
  await page.getByLabel('Video file').setInputFiles({
    name: 'corrupted-not-video.mp4',
    mimeType: 'video/mp4',
    buffer: Buffer.from('this is not a valid mp4 container'),
  })
  await page.getByRole('button', { name: 'Start upload' }).click()
  await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 30_000 })

  const invalidRow = page.getByRole('link').filter({ has: page.getByRole('heading', { name: 'corrupted-not-video.mp4' }) })
  await expect(invalidRow).toBeVisible({ timeout: 10_000 })
  await expect(invalidRow.getByText('Validation failed', { exact: true })).toBeVisible({ timeout: 45_000 })
  await invalidRow.click()
  await expect(page.getByRole('heading', { name: 'Processing failed' })).toBeVisible()
  await expect(page.getByText('Error code: NOT_A_VIDEO')).toBeVisible()
})
