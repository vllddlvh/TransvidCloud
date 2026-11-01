# S3 key convention (DRAFT)

| Bucket | Key | Nội dung |
|---|---|---|
| `tvc-{env}-input` | `{projectId}/{videoId}/source.{ext}` | Video gốc, client PUT qua presigned URL |
| `tvc-{env}-output` | `{projectId}/{videoId}/hls/master.m3u8` | Master playlist |
| `tvc-{env}-output` | `{projectId}/{videoId}/hls/360p/index.m3u8` (+ segment `.ts`) | Rendition 360p |
| `tvc-{env}-output` | `{projectId}/{videoId}/hls/720p/index.m3u8` (+ segment `.ts`) | Rendition 720p |
| `tvc-{env}-output` | `{projectId}/{videoId}/thumb/default.jpg` | Thumbnail |
| `tvc-{env}-frontend` | build của React | Static site |

- Key **ổn định** (suy ra từ `projectId/videoId`): xử lý lại cùng job ghi đè đúng chỗ cũ, không sinh rác.
- Content-Type: `.m3u8` = `application/vnd.apple.mpegurl`, `.ts` = `video/mp2t`, `.jpg` = `image/jpeg`.
- URL phát: `/media/{projectId}/{videoId}/hls/master.m3u8` (qua CloudFront hoặc nginx local).
