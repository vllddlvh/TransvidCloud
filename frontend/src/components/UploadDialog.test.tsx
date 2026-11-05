import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { UploadDialog, UploadTransportContext } from './UploadDialog'

it('blocks an oversized file in the modal before any upload can start', async () => {
  const user = userEvent.setup({ applyAccept: false })
  render(<UploadDialog projectId="demo-project" onComplete={vi.fn()} onClose={vi.fn()} />)
  const file = new File(['video'], 'too-large.mp4', { type: 'video/mp4' })
  Object.defineProperty(file, 'size', { value: 210763776 })
  await user.upload(screen.getByLabelText('Video file'), file)
  expect(screen.getByRole('alert')).toHaveTextContent('200 MB')
  expect(screen.getByRole('button', { name: 'Start upload' })).toBeDisabled()
})

it('rejects a wrong extension even if its MIME type claims to be video', async () => {
  const user = userEvent.setup({ applyAccept: false })
  render(<UploadDialog projectId="demo-project" onComplete={vi.fn()} onClose={vi.fn()} />)
  await user.upload(screen.getByLabelText('Video file'), new File(['video'], 'malformed.exe', { type: 'video/mp4' }))
  expect(screen.getByRole('alert')).toHaveTextContent('MP4, MOV, MKV, or WEBM')
  expect(screen.getByRole('button', { name: 'Start upload' })).toBeDisabled()
})

it('can be dismissed while confirmation stalls', async () => {
  const user = userEvent.setup({ applyAccept: false })
  const onClose = vi.fn()
  vi.spyOn(api, 'createVideo').mockResolvedValue({
    video: { videoId: 'stalled', projectId: 'demo-project', filename: 'clip.mp4', sizeBytes: 5, contentType: 'video/mp4', status: 'UPLOADING', createdAt: '2026-01-12T09:00:00.000Z', updatedAt: '2026-01-12T09:00:00.000Z' },
    uploadUrl: '/signed-put', expiresAt: new Date(Date.now() + 900000).toISOString(), headers: { 'Content-Type': 'video/mp4' },
  })
  vi.spyOn(api, 'confirmVideo').mockReturnValue(new Promise(() => {}))
  const transport = vi.fn().mockResolvedValue(undefined)
  render(<UploadTransportContext.Provider value={transport}>
    <UploadDialog projectId="demo-project" onComplete={vi.fn()} onClose={onClose} />
  </UploadTransportContext.Provider>)
  await user.upload(screen.getByLabelText('Video file'), new File(['video'], 'clip.mp4', { type: 'video/mp4' }))
  await user.click(screen.getByRole('button', { name: 'Start upload' }))
  await expect(screen.findByText('Confirming your video…')).resolves.toBeVisible()
  await user.click(screen.getByRole('button', { name: 'Close' }))
  expect(onClose).toHaveBeenCalledOnce()
})

it('allows customizing the video title before initiating upload', async () => {
  const user = userEvent.setup({ applyAccept: false })
  const onComplete = vi.fn()
  const createSpy = vi.spyOn(api, 'createVideo').mockResolvedValue({
    video: { videoId: 'vid-custom', projectId: 'demo-project', filename: 'clip.mp4', title: 'Lecture 01 Introduction', sizeBytes: 5, contentType: 'video/mp4', status: 'UPLOADING', createdAt: '2026-01-12T09:00:00.000Z', updatedAt: '2026-01-12T09:00:00.000Z' },
    uploadUrl: '/signed-put', expiresAt: new Date(Date.now() + 900000).toISOString(), headers: { 'Content-Type': 'video/mp4' },
  })
  vi.spyOn(api, 'confirmVideo').mockResolvedValue({
    videoId: 'vid-custom', projectId: 'demo-project', filename: 'clip.mp4', title: 'Lecture 01 Introduction', sizeBytes: 5, contentType: 'video/mp4', status: 'PENDING', createdAt: '2026-01-12T09:00:00.000Z', updatedAt: '2026-01-12T09:00:00.000Z',
  })
  const transport = vi.fn().mockResolvedValue(undefined)
  render(<UploadTransportContext.Provider value={transport}>
    <UploadDialog projectId="demo-project" onComplete={onComplete} onClose={vi.fn()} />
  </UploadTransportContext.Provider>)
  await user.upload(screen.getByLabelText('Video file'), new File(['video'], 'clip.mp4', { type: 'video/mp4' }))
  const titleInput = screen.getByLabelText('Video title')
  await user.clear(titleInput)
  await user.type(titleInput, 'Lecture 01 Introduction')
  await user.click(screen.getByRole('button', { name: 'Start upload' }))
  expect(createSpy).toHaveBeenCalledWith('demo-project', {
    title: 'Lecture 01 Introduction',
    filename: 'clip.mp4',
    sizeBytes: 5,
    contentType: 'video/mp4',
  })
  expect(onComplete).toHaveBeenCalledOnce()
})

afterEach(() => vi.restoreAllMocks())
