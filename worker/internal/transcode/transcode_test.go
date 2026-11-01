package transcode

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestBuildFilterMap(t *testing.T) {
	tests := []struct {
		name                   string
		srcHeight              int
		wantFilter             string
		wantMap360, wantMap720 string
	}{
		{"1080p: scale cả hai", 1080,
			"[0:v]split=2[v1][v2]; [v1]scale=-2:360[v360]; [v2]scale=-2:720[v720]", "[v360]", "[v720]"},
		{"480p: chỉ scale 360p", 480,
			"[0:v]split=2[v1][v2]; [v1]scale=-2:360[v360]", "[v360]", "[v2]"},
		{"360p: không scale (không phóng to)", 360,
			"[0:v]split=2[v1][v2]", "[v1]", "[v2]"},
		{"240p: không scale (không phóng to)", 240,
			"[0:v]split=2[v1][v2]", "[v1]", "[v2]"},
		{"720p: 720p giữ nguyên", 720,
			"[0:v]split=2[v1][v2]; [v1]scale=-2:360[v360]", "[v360]", "[v2]"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			filter, m360, m720 := buildFilterMap(tt.srcHeight)
			if filter != tt.wantFilter || m360 != tt.wantMap360 || m720 != tt.wantMap720 {
				t.Errorf("got (%q, %q, %q), want (%q, %q, %q)",
					filter, m360, m720, tt.wantFilter, tt.wantMap360, tt.wantMap720)
			}
		})
	}
}

func TestBuildArgs(t *testing.T) {
	args := BuildArgs("/in/source.mp4", "/out", 1080)
	joined := strings.Join(args, " ")
	for _, want := range []string{
		"-preset veryfast",
		"-hls_time 4",
		"-hls_segment_filename /out/hls/360p/seg_%03d.ts",
		"/out/hls/360p/index.m3u8",
		"/out/hls/720p/index.m3u8",
		"-map [v360]",
		"-map [v720]",
	} {
		if !strings.Contains(joined, want) {
			t.Errorf("args thiếu %q: %v", want, args)
		}
	}
}

func TestRenditionDims(t *testing.T) {
	tests := []struct {
		srcW, srcH, target, wantW, wantH int
	}{
		{1280, 720, 360, 640, 360},
		{1280, 720, 720, 1280, 720},
		{1920, 1080, 720, 1280, 720},
		// Không phóng to: nguồn nhỏ hơn target thì giữ nguyên nguồn.
		{640, 360, 720, 640, 360},
		{320, 240, 360, 320, 240},
	}
	for _, tt := range tests {
		w, h := renditionDims(tt.srcW, tt.srcH, tt.target)
		if w != tt.wantW || h != tt.wantH {
			t.Errorf("renditionDims(%d,%d,%d) = (%d,%d), want (%d,%d)",
				tt.srcW, tt.srcH, tt.target, w, h, tt.wantW, tt.wantH)
		}
		if w%2 != 0 || h%2 != 0 {
			t.Errorf("kích thước phải chẵn: %dx%d", w, h)
		}
	}
}

func TestWriteMaster(t *testing.T) {
	dir := t.TempDir()
	if err := WriteMaster(dir, 1280, 720); err != nil {
		t.Fatalf("WriteMaster: %v", err)
	}
	b, err := os.ReadFile(filepath.Join(dir, "hls", "master.m3u8"))
	if err != nil {
		t.Fatalf("đọc master: %v", err)
	}
	got := string(b)
	for _, want := range []string{
		"#EXTM3U",
		"BANDWIDTH=1010000,RESOLUTION=640x360",
		"360p/index.m3u8",
		"BANDWIDTH=3100000,RESOLUTION=1280x720",
		"720p/index.m3u8",
	} {
		if !strings.Contains(got, want) {
			t.Errorf("master thiếu %q:\n%s", want, got)
		}
	}
}

func TestBuildThumbnailArgs(t *testing.T) {
	long := BuildThumbnailArgs("in.mp4", "out.jpg", 9)
	if long[2] != "1" {
		t.Errorf("video 9s mong -ss 1, nhận %v", long)
	}
	short := BuildThumbnailArgs("in.mp4", "out.jpg", 0.5)
	if short[2] != "0" {
		t.Errorf("video 0.5s mong fallback -ss 0, nhận %v", short)
	}
}
