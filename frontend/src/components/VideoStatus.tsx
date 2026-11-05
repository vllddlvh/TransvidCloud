import { Circle, CircleCheck, CircleAlert, LoaderCircle } from 'lucide-react'
import type { Video } from '../api/types'

const labels: Record<Video['status'], string> = {
  UPLOADING: 'Uploading', PENDING: 'Pending', PROCESSING: 'Processing', COMPLETED: 'Completed',
  FAILED_VALIDATION: 'Validation failed', FAILED_TRANSCODE: 'Transcode failed', FAILED_STORAGE: 'Storage failed',
}

export function VideoStatus({ status }: { status: Video['status'] }) {
  const spinning = status === 'PROCESSING' || status === 'UPLOADING'
  const Icon = status.startsWith('FAILED_') ? CircleAlert : status === 'COMPLETED' ? CircleCheck
    : spinning ? LoaderCircle : Circle
  return <span className={`status-badge ${status.startsWith('FAILED_') ? 'status-error' : ''}`}>
    <Icon className={spinning ? 'status-spin' : undefined} size={14} aria-hidden="true" />{labels[status]}
  </span>
}

export const formatDate = (value: string) => new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value))
export const formatBytes = (bytes: number) => bytes < 1048576 ? `${Math.round(bytes / 1024)} KB` : `${(bytes / 1048576).toFixed(1)} MB`
