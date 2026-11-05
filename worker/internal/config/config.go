// Package config đọc biến môi trường theo docs-worker/3. Architecture.md mục 3.8.
// Thiếu biến bắt buộc thì Load trả lỗi để main thoát ngay lúc khởi động.
package config

import (
	"fmt"
	"os"
	"strconv"
	"time"

	"github.com/vllddlvh/TransvidCloud/worker/internal/workdir"
)

type Config struct {
	AWSRegion         string
	AWSEndpointURL    string
	InputBucket       string
	OutputBucket      string
	JobsQueueURL      string
	VideosTable       string
	MaxDurationSecs   float64
	MaxHeightPixels   int
	MaxUploadBytes    int64
	TmpDir            string
	FFmpegTimeout     time.Duration
	HeartbeatInterval time.Duration
	VisibilityTimeout time.Duration
}

func Load() (*Config, error) {
	required := map[string]string{
		"TVC_INPUT_BUCKET":   os.Getenv("TVC_INPUT_BUCKET"),
		"TVC_OUTPUT_BUCKET":  os.Getenv("TVC_OUTPUT_BUCKET"),
		"TVC_JOBS_QUEUE_URL": os.Getenv("TVC_JOBS_QUEUE_URL"),
		"TVC_DDB_VIDEOS":     os.Getenv("TVC_DDB_VIDEOS"),
	}
	var missing []string
	for name, val := range required {
		if val == "" {
			missing = append(missing, name)
		}
	}
	if len(missing) > 0 {
		return nil, fmt.Errorf("thiếu biến môi trường bắt buộc: %v", missing)
	}

	cfg := &Config{
		AWSRegion:      getEnv("AWS_REGION", "ap-southeast-1"),
		AWSEndpointURL: os.Getenv("AWS_ENDPOINT_URL"),
		InputBucket:    required["TVC_INPUT_BUCKET"],
		OutputBucket:   required["TVC_OUTPUT_BUCKET"],
		JobsQueueURL:   required["TVC_JOBS_QUEUE_URL"],
		VideosTable:    required["TVC_DDB_VIDEOS"],
		TmpDir:         getEnv("TVC_TMP_DIR", "/tmp/tvc"),
	}

	var err error
	if cfg.MaxDurationSecs, err = getEnvFloat("MAX_DURATION_SECONDS", 300); err != nil {
		return nil, err
	}
	if cfg.MaxHeightPixels, err = getEnvInt("MAX_HEIGHT_PIXELS", 1080); err != nil {
		return nil, err
	}
	if cfg.MaxUploadBytes, err = getEnvInt64("MAX_UPLOAD_BYTES", 209715200); err != nil {
		return nil, err
	}
	if cfg.FFmpegTimeout, err = getEnvDuration("TVC_FFMPEG_TIMEOUT", 15*time.Minute); err != nil {
		return nil, err
	}
	if cfg.HeartbeatInterval, err = getEnvDuration("TVC_HEARTBEAT_INTERVAL", 120*time.Second); err != nil {
		return nil, err
	}
	if cfg.VisibilityTimeout, err = getEnvDuration("TVC_VISIBILITY_TIMEOUT", 300*time.Second); err != nil {
		return nil, err
	}
	if err := cfg.validateReliability(); err != nil {
		return nil, err
	}
	return cfg, nil
}

func (c *Config) validateReliability() error {
	if c.HeartbeatInterval <= 0 {
		return fmt.Errorf("TVC_HEARTBEAT_INTERVAL phải dương, nhận %s", c.HeartbeatInterval)
	}
	if c.VisibilityTimeout <= 0 {
		return fmt.Errorf("TVC_VISIBILITY_TIMEOUT phải dương, nhận %s", c.VisibilityTimeout)
	}
	// Ít nhất hai lần gia hạn trong một cửa sổ visibility để còn dư cho retry.
	if c.HeartbeatInterval*2 > c.VisibilityTimeout {
		return fmt.Errorf("TVC_HEARTBEAT_INTERVAL (%s) phải <= một nửa TVC_VISIBILITY_TIMEOUT (%s)",
			c.HeartbeatInterval, c.VisibilityTimeout)
	}
	if c.FFmpegTimeout <= 0 {
		return fmt.Errorf("TVC_FFMPEG_TIMEOUT phải dương, nhận %s", c.FFmpegTimeout)
	}
	if err := workdir.ValidateRoot(c.TmpDir); err != nil {
		return err
	}
	return nil
}

func getEnv(name, def string) string {
	if v := os.Getenv(name); v != "" {
		return v
	}
	return def
}

func getEnvInt(name string, def int) (int, error) {
	v := os.Getenv(name)
	if v == "" {
		return def, nil
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return 0, fmt.Errorf("%s phải là số nguyên, nhận %q: %w", name, v, err)
	}
	return n, nil
}

func getEnvInt64(name string, def int64) (int64, error) {
	v := os.Getenv(name)
	if v == "" {
		return def, nil
	}
	n, err := strconv.ParseInt(v, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("%s phải là số nguyên, nhận %q: %w", name, v, err)
	}
	return n, nil
}

func getEnvFloat(name string, def float64) (float64, error) {
	v := os.Getenv(name)
	if v == "" {
		return def, nil
	}
	f, err := strconv.ParseFloat(v, 64)
	if err != nil {
		return 0, fmt.Errorf("%s phải là số, nhận %q: %w", name, v, err)
	}
	return f, nil
}

func getEnvDuration(name string, def time.Duration) (time.Duration, error) {
	v := os.Getenv(name)
	if v == "" {
		return def, nil
	}
	d, err := time.ParseDuration(v)
	if err != nil {
		return 0, fmt.Errorf("%s phải là duration (vd 15m, 120s), nhận %q: %w", name, v, err)
	}
	return d, nil
}
