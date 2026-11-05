const contentTypes: Record<string, string> = {
  mp4: 'video/mp4',
  mov: 'video/quicktime',
  mkv: 'video/x-matroska',
  webm: 'video/webm',
}

export function validateVideoFile(file: File): string | null {
  if (file.size === 0) return 'This file is empty. Choose a video with content.'
  if (file.size > 209715200) return 'This file exceeds the 200 MB limit. Choose a smaller video.'
  if (!/^.+\.(mp4|mov|mkv|webm)$/i.test(file.name)) {
    return 'Choose an MP4, MOV, MKV, or WEBM video.'
  }
  return null
}

export function videoContentType(file: File): string {
  return contentTypes[file.name.split('.').pop()!.toLowerCase()] ?? file.type
}
