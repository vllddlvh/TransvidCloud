package validate

import (
	"context"
	"os/exec"
	"path/filepath"
	"testing"
)

func TestParseProbeOutput(t *testing.T) {
	valid720 := `{
	  "format": {"duration": "10.000000"},
	  "streams": [
	    {"codec_type": "video", "codec_name": "h264", "width": 1280, "height": 720},
	    {"codec_type": "audio", "codec_name": "aac"}
	  ]
	}`
	tests := []struct {
		name    string
		input   string
		want    ProbeResult
		wantErr bool
	}{
		{"video h264 + audio", valid720,
			ProbeResult{DurationSeconds: 10, Width: 1280, Height: 720, Codec: "h264", HasVideo: true, HasAudio: true}, false},
		{"chỉ có audio", `{"format":{"duration":"4"},"streams":[{"codec_type":"audio","codec_name":"aac"}]}`,
			ProbeResult{DurationSeconds: 4, HasAudio: true}, false},
		{"không có audio", `{"format":{"duration":"4"},"streams":[{"codec_type":"video","codec_name":"h264","width":640,"height":360}]}`,
			ProbeResult{DurationSeconds: 4, Width: 640, Height: 360, Codec: "h264", HasVideo: true}, false},
		{"json hỏng", `{không phải json`, ProbeResult{}, true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := parseProbeOutput([]byte(tt.input))
			if (err != nil) != tt.wantErr {
				t.Fatalf("err = %v, wantErr = %v", err, tt.wantErr)
			}
			if err != nil {
				return
			}
			if *got != tt.want {
				t.Errorf("got %+v, want %+v", *got, tt.want)
			}
		})
	}
}

func TestCheck(t *testing.T) {
	limits := Limits{MaxDurationSeconds: 300, MaxHeightPixels: 1080}
	tests := []struct {
		name    string
		probe   ProbeResult
		wantErr string
	}{
		{"hợp lệ 720p", ProbeResult{HasVideo: true, Codec: "h264", DurationSeconds: 10, Height: 720}, ""},
		{"hợp lệ hevc", ProbeResult{HasVideo: true, Codec: "hevc", DurationSeconds: 10, Height: 720}, ""},
		{"hợp lệ vp9 webm", ProbeResult{HasVideo: true, Codec: "vp9", DurationSeconds: 10, Height: 720}, ""},
		{"không có video", ProbeResult{}, "NOT_A_VIDEO"},
		{"codec lạ", ProbeResult{HasVideo: true, Codec: "mpeg2", DurationSeconds: 10, Height: 720}, "CODEC_UNSUPPORTED"},
		{"quá dài", ProbeResult{HasVideo: true, Codec: "h264", DurationSeconds: 301, Height: 720}, "DURATION_EXCEEDED"},
		{"quá cao", ProbeResult{HasVideo: true, Codec: "h264", DurationSeconds: 10, Height: 1440}, "HEIGHT_EXCEEDED"},
		{"video không tiếng vẫn hợp lệ", ProbeResult{HasVideo: true, Codec: "h264", DurationSeconds: 10, Height: 720}, ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := Check(&tt.probe, limits)
			if tt.wantErr == "" {
				if err != nil {
					t.Fatalf("không mong lỗi, nhận %v", err)
				}
				return
			}
			ve, ok := err.(*ValidationError)
			if !ok {
				t.Fatalf("mong ValidationError, nhận %T %v", err, err)
			}
			if ve.Code != tt.wantErr {
				t.Errorf("code = %s, want %s", ve.Code, tt.wantErr)
			}
		})
	}
}

// Kiểm thử ffprobe thật trên bộ video mẫu (bỏ qua nếu máy không có ffprobe).
func TestProbeRealSamples(t *testing.T) {
	if _, err := exec.LookPath("ffprobe"); err != nil {
		t.Skip("không có ffprobe trong PATH")
	}
	ctx := context.Background()
	dir := filepath.Join("..", "..", "testdata")

	r, err := Probe(ctx, filepath.Join(dir, "sample-10s-720p.mp4"))
	if err != nil {
		t.Fatalf("probe sample-10s-720p: %v", err)
	}
	if !r.HasVideo || r.Codec != "h264" || r.Height != 720 || r.DurationSeconds < 9 || r.DurationSeconds > 11 {
		t.Errorf("kết quả sai: %+v", r)
	}

	if _, err := Probe(ctx, filepath.Join(dir, "fake-video.mp4")); err == nil {
		t.Error("mong lỗi khi probe file giả mạo")
	}
}
