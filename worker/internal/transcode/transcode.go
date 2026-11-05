// Package transcode bọc FFmpeg theo docs-worker/3. Architecture.md mục 3.5:
// giải mã một lần bằng filter split, preset veryfast, HLS 360p + 720p,
// thumbnail riêng một lần chạy, master playlist viết tay.
package transcode

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
)

// Error mang errorCode ghi vào DynamoDB (FAILED_TRANSCODE).
type Error struct {
	Code  string
	Cause error
}

func (e *Error) Error() string { return "transcode failed: " + e.Code + ": " + e.Cause.Error() }
func (e *Error) Unwrap() error { return e.Cause }

// buildFilterMap trả filter_complex và nhãn map cho 2 rendition.
// Không phóng to: rendition nào có target >= chiều cao nguồn thì map thẳng nguồn.
func buildFilterMap(srcHeight int) (filter, map360, map720 string) {
	switch {
	case srcHeight > 720:
		return "[0:v]split=2[v1][v2]; [v1]scale=-2:360[v360]; [v2]scale=-2:720[v720]", "[v360]", "[v720]"
	case srcHeight > 360:
		return "[0:v]split=2[v1][v2]; [v1]scale=-2:360[v360]", "[v360]", "[v2]"
	default:
		return "[0:v]split=2[v1][v2]", "[v1]", "[v2]"
	}
}

// BuildArgs dựng lệnh FFmpeg một lần chạy sinh 2 playlist HLS.
func BuildArgs(input, outDir string, srcHeight int) []string {
	filter, map360, map720 := buildFilterMap(srcHeight)
	return []string{
		"-y", "-i", input,
		"-filter_complex", filter,
		"-map", map360, "-map", "0:a?",
		"-c:v:0", "libx264", "-preset", "veryfast", "-b:v:0", "800k", "-maxrate:0", "856k", "-bufsize:0", "1200k",
		"-c:a:0", "aac", "-b:a:0", "96k",
		"-map", map720, "-map", "0:a?",
		"-c:v:1", "libx264", "-preset", "veryfast", "-b:v:1", "2800k", "-maxrate:1", "3000k", "-bufsize:1", "4800k",
		"-c:a:1", "aac", "-b:a:1", "128k",
		"-f", "hls", "-hls_time", "4", "-hls_playlist_type", "vod",
		"-hls_segment_filename", filepath.Join(outDir, "hls", "360p", "seg_%03d.ts"),
		filepath.Join(outDir, "hls", "360p", "index.m3u8"),
		"-f", "hls", "-hls_time", "4", "-hls_playlist_type", "vod",
		"-hls_segment_filename", filepath.Join(outDir, "hls", "720p", "seg_%03d.ts"),
		filepath.Join(outDir, "hls", "720p", "index.m3u8"),
	}
}

// BuildThumbnailArgs lấy frame giây thứ 1; video ngắn hơn 1 giây thì giây 0.
func BuildThumbnailArgs(input, outPath string, durationSeconds float64) []string {
	ss := "1"
	if durationSeconds < 1 {
		ss = "0"
	}
	return []string{"-y", "-ss", ss, "-i", input, "-frames:v", "1", "-q:v", "3", outPath}
}

// renditionDims tính kích thước thực của rendition, khớp cách FFmpeg scale=-2:h.
func renditionDims(srcW, srcH, targetH int) (int, int) {
	if srcH <= targetH {
		return srcW, srcH
	}
	w := (srcW*targetH + srcH/2) / srcH
	w &^= 1 // làm tròn xuống số chẵn (yêu cầu h264 4:2:0)
	return w, targetH
}

// WriteMaster ghi master playlist với RESOLUTION thật của từng rendition.
func WriteMaster(outDir string, srcW, srcH int) error {
	w360, h360 := renditionDims(srcW, srcH, 360)
	w720, h720 := renditionDims(srcW, srcH, 720)
	content := fmt.Sprintf(`#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=1010000,RESOLUTION=%dx%d
360p/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=3100000,RESOLUTION=%dx%d
720p/index.m3u8
`, w360, h360, w720, h720)
	path := filepath.Join(outDir, "hls", "master.m3u8")
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	return os.WriteFile(path, []byte(content), 0o644)
}

// Run chạy FFmpeg với context timeout từ caller; giữ 2KB cuối stderr để chẩn đoán.
func Run(ctx context.Context, args []string) error {
	cmd := exec.CommandContext(ctx, "ffmpeg", args...)
	var stderr bytes.Buffer
	cmd.Stderr = &stderr
	err := cmd.Run()
	if err == nil {
		return nil
	}
	if errors.Is(ctx.Err(), context.DeadlineExceeded) {
		return &Error{Code: "FFMPEG_TIMEOUT", Cause: ctx.Err()}
	}
	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) {
		return &Error{Code: fmt.Sprintf("FFMPEG_EXIT_%d", exitErr.ExitCode()), Cause: fmt.Errorf("%s", tail(stderr.Bytes()))}
	}
	return &Error{Code: "FFMPEG_START_FAILED", Cause: err}
}

func tail(b []byte) string {
	const max = 2048
	if len(b) <= max {
		return string(b)
	}
	return "..." + string(b[len(b)-max:])
}
