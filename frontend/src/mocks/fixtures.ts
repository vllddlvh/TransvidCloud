import type { Project, Session, Video, VideoStatus } from '../api/types'

export function completeVideoAssets(projectId: string, videoId: string): NonNullable<Video['assets']> {
  const root = `/media/${projectId}/${videoId}`
  return {
    hls: `${root}/hls/master.m3u8`,
    hls360: `${root}/hls/360p/index.m3u8`,
    hls720: `${root}/hls/720p/index.m3u8`,
    thumbnail: `${root}/thumb/default.jpg`,
  }
}

export const DEMO_USER: Session['user'] = {
  userId: 'demo-user', email: 'demo@transvidcloud.test', name: 'Demo user',
}

// Fresh objects on every call: handlers/tests never share mutable module fixtures.
export function createFixtures(): { projects: Project[]; videos: Video[] } {
  const createdAt = '2026-01-12T09:00:00.000Z'
  const projects: Project[] = [
    { projectId: 'demo-project', name: 'Getting started', description: 'Explore the seven stages of your video library.', videoCount: 7, createdAt },
    { projectId: 'product-stories', name: 'Product stories', description: 'Product walkthroughs and launch stories.', videoCount: 0, createdAt },
    { projectId: 'team-library', name: 'Team library', description: 'A shared home for team recordings.', videoCount: 0, createdAt },
  ]
  const samples: { id: string; filename: string; status: VideoStatus; errorCode?: string }[] = [
    { id: 'demo-uploading', filename: 'Workspace tour.mp4', status: 'UPLOADING' },
    { id: 'demo-pending', filename: 'Product introduction.mp4', status: 'PENDING' },
    { id: 'demo-processing', filename: 'A day with the team.mp4', status: 'PROCESSING' },
    { id: 'demo-completed', filename: 'Welcome to TransVidCloud.mp4', status: 'COMPLETED' },
    { id: 'demo-validation', filename: 'Long recording.mp4', status: 'FAILED_VALIDATION', errorCode: 'DURATION_EXCEEDED' },
    { id: 'demo-transcode', filename: 'Camera test.mp4', status: 'FAILED_TRANSCODE', errorCode: 'TRANSCODE_FAILED' },
    { id: 'demo-storage', filename: 'Interview.mp4', status: 'FAILED_STORAGE', errorCode: 'STORAGE_WRITE_FAILED' },
  ]
  const videos: Video[] = samples.map(({ id, filename, status, errorCode }) => ({
    videoId: id, projectId: 'demo-project', filename, sizeBytes: 10_485_760,
    contentType: 'video/mp4', status, createdAt, updatedAt: createdAt,
    ...(errorCode ? { errorCode } : {}),
    ...(status === 'COMPLETED' ? {
      durationSeconds: 42,
      assets: completeVideoAssets('demo-project', id),
    } : {}),
  }))
  return { projects, videos }
}
