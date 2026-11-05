import { expect, test } from '@playwright/test'
import { auxiliaryOrigins, hasLocalAuxiliaryServers } from './support'

test('failed confirmation retries only confirm, without duplicating the video or PUT', async ({ page }) => {
  test.skip(!hasLocalAuxiliaryServers, 'Requires the local real-mode server')
  const now = new Date().toISOString()
  const project = { projectId: 'retry-project', name: 'Confirm retry', description: '', videoCount: 0, createdAt: now }
  const video = { videoId: 'retry-video', projectId: project.projectId, filename: 'confirm.mp4', contentType: 'video/mp4', sizeBytes: 5, status: 'UPLOADING', createdAt: now, updatedAt: now }
  let creates = 0, puts = 0, confirms = 0
  const authorization: (string | undefined)[] = []
  await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const reply = (body: unknown, status = 200) => route.fulfill({ status, json: body })
    if (path === '/api/auth/login') return reply({ accessToken: 'browser-test-token', expiresAt: new Date(Date.now() + 600000).toISOString(), user: { userId: 'test-user', name: 'Test user', email: 'test@example.test' } })
    if (path === '/api/projects') return reply({ items: [project] })
    if (path === '/api/projects/retry-project/videos' && request.method() === 'GET') return reply({ items: creates ? [video] : [] })
    if (path === '/api/projects/retry-project/videos' && request.method() === 'POST') {
      creates++
      return reply({ video, uploadUrl: '/signed-e2e-put', expiresAt: new Date(Date.now() + 900000).toISOString(), headers: { 'Content-Type': 'video/mp4' } }, 201)
    }
    if (path === '/api/videos/retry-video/confirm') {
      confirms++
      if (confirms === 1) return reply({ error: { code: 'TEMPORARY_ERROR', message: 'Confirmation unavailable. Try again.' } }, 503)
      video.status = 'PENDING'
      return reply(video, 202)
    }
    return route.fulfill({ status: 404 })
  })
  await page.route('**/signed-e2e-put', async (route) => {
    puts++
    authorization.push(route.request().headers().authorization)
    await route.fulfill({ status: 200, body: '' })
  })
  await page.goto(`${auxiliaryOrigins.real}/login`)
  await page.getByLabel('Email address').fill('test@example.test')
  await page.getByLabel('Password', { exact: true }).fill('test-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.getByRole('link').filter({ has: page.getByRole('heading', { name: 'Confirm retry' }) }).click()
  await page.getByRole('button', { name: 'Upload video' }).click()
  await page.getByLabel('Video file').setInputFiles({ name: 'confirm.mp4', mimeType: 'video/mp4', buffer: Buffer.from('video') })
  await page.getByRole('button', { name: 'Start upload' }).click()
  await expect(page.getByRole('alert')).toContainText('Confirmation unavailable')
  await page.getByRole('button', { name: 'Retry upload' }).click()
  await expect(page.getByRole('dialog')).not.toBeVisible()
  await expect(page.getByText('Pending', { exact: true })).toBeVisible()
  expect({ creates, puts, confirms }).toEqual({ creates: 1, puts: 1, confirms: 2 })
  expect(authorization).toEqual([undefined])
})
