package config

import (
	"testing"
	"time"
)

func setRequiredEnv(t *testing.T) {
	t.Helper()
	t.Setenv("TVC_INPUT_BUCKET", "tvc-local-input")
	t.Setenv("TVC_OUTPUT_BUCKET", "tvc-local-output")
	t.Setenv("TVC_JOBS_QUEUE_URL", "http://localhost:4566/000000000000/tvc-local-jobs")
	t.Setenv("TVC_DDB_VIDEOS", "tvc-local-videos")
}

func TestLoadMissingRequired(t *testing.T) {
	// Xóa các biến bắt buộc (môi trường test sạch do t.Setenv không chạy).
	t.Setenv("TVC_INPUT_BUCKET", "")
	t.Setenv("TVC_OUTPUT_BUCKET", "")
	t.Setenv("TVC_JOBS_QUEUE_URL", "")
	t.Setenv("TVC_DDB_VIDEOS", "")
	if _, err := Load(); err == nil {
		t.Fatal("mong lỗi khi thiếu biến bắt buộc")
	}
}

func TestLoadDefaults(t *testing.T) {
	setRequiredEnv(t)
	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if cfg.AWSRegion != "ap-southeast-1" {
		t.Errorf("AWSRegion = %q", cfg.AWSRegion)
	}
	if cfg.MaxDurationSecs != 300 || cfg.MaxHeightPixels != 1080 || cfg.MaxUploadBytes != 209715200 {
		t.Errorf("giới hạn mặc định sai: %+v", cfg)
	}
	if cfg.TmpDir != "/tmp/tvc" {
		t.Errorf("TmpDir = %q", cfg.TmpDir)
	}
	if cfg.FFmpegTimeout.Minutes() != 15 || cfg.HeartbeatInterval.Seconds() != 120 || cfg.VisibilityTimeout.Seconds() != 300 {
		t.Errorf("duration mặc định sai: %+v", cfg)
	}
}

func TestLoadReliabilityValidation(t *testing.T) {
	tests := []struct {
		name  string
		env   string
		value string
	}{
		{"heartbeat 0", "TVC_HEARTBEAT_INTERVAL", "0s"},
		{"heartbeat âm", "TVC_HEARTBEAT_INTERVAL", "-10s"},
		{"visibility 0", "TVC_VISIBILITY_TIMEOUT", "0s"},
		{"heartbeat quá gần visibility", "TVC_HEARTBEAT_INTERVAL", "200s"},
		{"visibility quá ngắn", "TVC_VISIBILITY_TIMEOUT", "100s"},
		{"ffmpeg timeout 0", "TVC_FFMPEG_TIMEOUT", "0s"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			setRequiredEnv(t)
			t.Setenv(tt.env, tt.value)
			if _, err := Load(); err == nil {
				t.Errorf("mong lỗi khi %s=%s", tt.env, tt.value)
			}
		})
	}
}

func TestLoadAllowsShortHeartbeatForIntegration(t *testing.T) {
	setRequiredEnv(t)
	t.Setenv("TVC_HEARTBEAT_INTERVAL", "200ms")
	t.Setenv("TVC_VISIBILITY_TIMEOUT", "1s")
	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if cfg.HeartbeatInterval != 200*time.Millisecond || cfg.VisibilityTimeout != time.Second {
		t.Errorf("giá trị heartbeat sai: %+v", cfg)
	}
}

func TestLoadRejectsUnsafeTmpDir(t *testing.T) {
	for _, dir := range []string{"/", "/tmp", "/var/tmp", "relative/tvc"} {
		t.Run(dir, func(t *testing.T) {
			setRequiredEnv(t)
			t.Setenv("TVC_TMP_DIR", dir)
			if _, err := Load(); err == nil {
				t.Errorf("mong từ chối TVC_TMP_DIR=%q", dir)
			}
		})
	}
}

func TestLoadOverridesAndBadValue(t *testing.T) {
	setRequiredEnv(t)
	t.Setenv("MAX_DURATION_SECONDS", "600")
	t.Setenv("MAX_HEIGHT_PIXELS", "720")
	t.Setenv("TVC_FFMPEG_TIMEOUT", "30m")
	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if cfg.MaxDurationSecs != 600 || cfg.MaxHeightPixels != 720 || cfg.FFmpegTimeout.Minutes() != 30 {
		t.Errorf("override sai: %+v", cfg)
	}

	t.Setenv("MAX_HEIGHT_PIXELS", "không-phải-số")
	if _, err := Load(); err == nil {
		t.Error("mong lỗi khi MAX_HEIGHT_PIXELS không phải số")
	}
}
