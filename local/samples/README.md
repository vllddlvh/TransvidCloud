# local/samples/

Video mẫu lớn phục vụ thực nghiệm — **không commit** (đã chặn trong `.gitignore`, trừ file README này).

| File | Nội dung | Dùng cho case |
|---|---|---|
| `sample-long-301s.mp4` | 301s, 320x240, 10fps, h264 | `DURATION_EXCEEDED` (> 300s) |
| `sample-1440p.mp4` | 5s, 2560x1440, h264 | `HEIGHT_EXCEEDED` (> 1080) |
| `poc/` | Bằng chứng POC tuần 1: nguồn video thật + output HLS/thumb + `player.html` | M1 |

Sinh lại file lớn:

```bash
ffmpeg -y -v error -f lavfi -i "testsrc2=size=320x240:rate=10" \
  -t 301 -c:v libx264 -preset ultrafast -crf 35 -pix_fmt yuv420p local/samples/sample-long-301s.mp4
ffmpeg -y -v error -f lavfi -i "testsrc2=size=2560x1440:rate=30" \
  -t 5 -c:v libx264 -preset veryfast -crf 30 -pix_fmt yuv420p local/samples/sample-1440p.mp4
```
