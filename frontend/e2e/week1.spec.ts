import { expect, test, type Page } from '@playwright/test'
import { mkdtemp, rm, truncate, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

async function login(page: Page, origin = '') {
  await page.goto(`${origin}/projects`)
  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel('Email address').fill('demo@transvidcloud.test')
  await page.getByLabel('Password', { exact: true }).fill('DemoPass123!')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Projects', exact: true })).toBeVisible()
}

test('login validation, project creation, reload persistence and logout', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('Email address').fill('demo@transvidcloud.test')
  await page.getByLabel('Password', { exact: true }).fill('incorrect')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await page.getByLabel('Password', { exact: true }).fill('DemoPass123!')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Getting started', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Create project', exact: true }).click()
  await page.getByLabel('Project name').fill('Launch campaign')
  await page.getByLabel('Description').fill('Week one browser acceptance')
  await page.getByRole('dialog').getByRole('button', { name: 'Create project' }).click()
  await expect(page.getByRole('heading', { name: 'Launch campaign' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Launch campaign' })).toBeVisible()
  await page.getByRole('link').filter({ has: page.getByRole('heading', { name: 'Launch campaign' }) }).click()
  await expect(page.getByRole('heading', { name: 'Your first video belongs here' })).toBeVisible()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login$/)
})

test('10 MB video follows presign, PUT and confirm with visible progress', async ({ page }) => {
  await login(page)
  await page.getByRole('link').filter({ has: page.getByRole('heading', { name: 'Getting started', exact: true }) }).click()
  await expect(page.getByText('Validation failed', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Upload video' }).click()
  await page.getByLabel('Video file').setInputFiles({ name: 'acceptance-10mb.mp4', mimeType: 'video/mp4', buffer: Buffer.alloc(10 * 1024 * 1024, 1) })
  await page.getByRole('button', { name: 'Start upload' }).click()
  await expect(page.getByRole('progressbar')).toHaveAttribute('value', /^(?:[1-9]|[1-8][0-9]|90)$/)
  await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 15000 })
  const row = page.getByRole('link').filter({ has: page.getByRole('heading', { name: 'acceptance-10mb.mp4' }) })
  await expect(row.getByText('Pending', { exact: true })).toBeVisible()
  await row.click()
  await expect(page.getByRole('heading', { name: 'acceptance-10mb.mp4' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'File information' })).toBeVisible()
})

test('bad file types and a 201 MB declaration are rejected', async ({ page }) => {
  await login(page)
  await page.goto('/projects/demo-project/videos')
  await page.getByRole('button', { name: 'Upload video' }).click()
  await page.getByLabel('Video file').setInputFiles({ name: 'not-a-video.txt', mimeType: 'text/plain', buffer: Buffer.from('not video') })
  await expect(page.getByRole('alert')).toContainText('MP4, MOV, MKV, or WEBM')
  await expect(page.getByRole('button', { name: 'Start upload' })).toBeDisabled()
  const directory = await mkdtemp(join(tmpdir(), 'tvc-upload-'))
  try {
    const path = join(directory, 'large.mp4')
    await writeFile(path, '')
    await truncate(path, 210763776)
    await page.getByLabel('Video file').setInputFiles(path)
    await expect(page.getByRole('alert')).toContainText('200 MB')
    await expect(page.getByRole('button', { name: 'Start upload' })).toBeDisabled()
  } finally {
    await rm(directory, { recursive: true, force: true })
  }
  const status = await page.evaluate(async () => {
    const { accessToken } = JSON.parse(localStorage.getItem('tvc.session')!)
    const response = await fetch('/api/projects/demo-project/videos', { method: 'POST', headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ filename: 'large.mp4', contentType: 'video/mp4', sizeBytes: 210763776 }) })
    return response.status
  })
  expect(status).toBe(413)
})

test('a session that expires while open redirects with an explanation', async ({ page }) => {
  await login(page)
  await page.evaluate(() => {
    const session = JSON.parse(localStorage.getItem('tvc.session')!)
    session.expiresAt = new Date(Date.now() + 600).toISOString()
    localStorage.setItem('tvc.session', JSON.stringify(session))
    window.dispatchEvent(new Event('tvc:session'))
  })
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('alert')).toContainText('session has expired')
})

test('mobile navigation and public embed remain usable', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await login(page)
  const longName = 'a'.repeat(100)
  await page.getByRole('button', { name: 'Create project', exact: true }).click()
  await page.getByLabel('Project name').fill(longName)
  await page.getByRole('dialog').getByRole('button', { name: 'Create project' }).click()
  await expect(page.getByRole('heading', { name: longName })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByRole('button', { name: 'Toggle navigation' }).click()
  await expect(page.getByRole('navigation')).toBeInViewport()
  await page.getByRole('button', { name: 'Close navigation' }).click({ position: { x: 350, y: 100 } })
  await expect(page.locator('.sidebar nav a').first()).not.toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.goto('/embed')
  await expect(page.getByText('Video is not available yet.')).toBeVisible()
  await expect(page.getByRole('navigation')).not.toBeVisible()
})
