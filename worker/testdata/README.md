# worker/testdata/

Video mẫu ngắn, cố định (5-10 giây, tổng ~2,5 MB) cho unit/e2e test. Chỉ commit file nhỏ;
video lớn (dài, độ phân giải cao) để trong `local/samples/` và không commit.

## Bộ video mẫu

| File | Nội dung | Dùng cho case |
|---|---|---|
| `sample-10s-720p.mp4` | 10s, 1280x720, 30fps, h264 (veryfast) + aac | Happy path transcode (360p+720p+thumb) |
| `sample-360p.mp4` | 5s, 640x360, h264 + aac | Nguồn ≤ 360: bỏ scale, không phóng to |
| `sample-no-audio.mp4` | 5s, 1280x720, h264, **không audio** | Transcode với `-map 0:a?` |
| `fake-video.mp4` | File text giả mạo đuôi `.mp4` | `NOT_A_VIDEO` |

File lớn (không commit, trong `local/samples/`): `sample-long-301s.mp4` (> 300s →
`DURATION_EXCEEDED`), `sample-1440p.mp4` (2560x1440 → `HEIGHT_EXCEEDED`).

## Cấu hình FFmpeg dùng cho mọi thực nghiệm (đã chốt POC 2026-10-26)

- Bản FFmpeg/ffprobe: **8.0.1** (Apple clang, arm64).
- Một lần chạy, giải mã một lần: filter `split`, preset **`veryfast`**, không 1080p (N2/N3/N4).
- 360p: `-b:v 800k -maxrate 856k -bufsize 1200k`, audio `aac 96k`.
- 720p: `-b:v 2800k -maxrate 3000k -bufsize 4800k`, audio `aac 128k`.
- HLS: `-hls_time 4 -hls_playlist_type vod`, segment `seg_%03d.ts`.
- Thumbnail: `-ss 1 -frames:v 1 -q:v 3` (fallback `-ss 0` nếu video < 1s).
- Master playlist viết tay; `BANDWIDTH` 1010000 (360p) / 3100000 (720p), `RESOLUTION` lấy từ ffprobe.
- Quy tắc scale: **không phóng to** — rendition nào có target ≥ chiều cao nguồn thì map thẳng nguồn, không chạy `scale`.

Lệnh transcode đầy đủ: `docs/docs-worker/3. Architecture.md` mục 3.5.

## Sinh lại bộ mẫu

```bash
cd worker/testdata
ffmpeg -y -v error -f lavfi -i "testsrc2=size=1280x720:rate=30" -f lavfi -i "sine=frequency=440:duration=10" \
  -t 10 -c:v libx264 -preset veryfast -crf 30 -pix_fmt yuv420p -c:a aac -b:a 96k -shortest sample-10s-720p.mp4
ffmpeg -y -v error -f lavfi -i "testsrc2=size=640x360:rate=30" -f lavfi -i "sine=frequency=300:duration=5" \
  -t 5 -c:v libx264 -preset veryfast -crf 30 -pix_fmt yuv420p -c:a aac -b:a 96k -shortest sample-360p.mp4
ffmpeg -y -v error -f lavfi -i "testsrc2=size=1280x720:rate=30" \
  -t 5 -c:v libx264 -preset veryfast -crf 30 -pix_fmt yuv420p sample-no-audio.mp4
printf 'Day la mot file text gia mao duoi mp4.\n' > fake-video.mp4
```
