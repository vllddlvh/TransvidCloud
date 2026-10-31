// Package validate bọc ffprobe và so giới hạn theo docs-worker/3. Architecture.md
// mục 3.4. Thứ tự kiểm: có video -> codec -> thời lượng -> chiều cao.
package validate

import (
	"context"
	"encoding/json"
	"fmt"
	"os/exec"
)

type ProbeResult struct {
	DurationSeconds float64
	Width           int
	Height          int
	Codec           string
	HasVideo        bool
	HasAudio        bool
}

type Limits struct {
	MaxDurationSeconds float64
	MaxHeightPixels    int
}

// ValidationError mang errorCode ghi vào DynamoDB (FAILED_VALIDATION).
type ValidationError struct {
	Code string
}

func (e *ValidationError) Error() string { return "validation failed: " + e.Code }

// Danh sách codec chốt trong A1: h264 + codec phổ biến trong mp4/mov/mkv/webm.
var allowedCodecs = map[string]bool{
	"h264":  true,
	"hevc":  true,
	"vp8":   true,
	"vp9":   true,
	"av1":   true,
	"mpeg4": true,
}

type ffprobeOutput struct {
	Format struct {
		Duration string `json:"duration"`
	} `json:"format"`
	Streams []struct {
		CodecType string `json:"codec_type"`
		CodecName string `json:"codec_name"`
		Width     int    `json:"width"`
		Height    int    `json:"height"`
	} `json:"streams"`
}

// Probe chạy ffprobe trên input; lỗi chạy (file không giải mã được) tức NOT_A_VIDEO.
func Probe(ctx context.Context, input string) (*ProbeResult, error) {
	cmd := exec.CommandContext(ctx, "ffprobe", "-v", "error", "-print_format", "json",
		"-show_format", "-show_streams", input)
	out, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("ffprobe failed: %w", err)
	}
	r, err := parseProbeOutput(out)
	if err != nil {
		return nil, err
	}
	return r, nil
}

func parseProbeOutput(data []byte) (*ProbeResult, error) {
	var out ffprobeOutput
	if err := json.Unmarshal(data, &out); err != nil {
		return nil, fmt.Errorf("không đọc được output ffprobe: %w", err)
	}
	r := &ProbeResult{}
	if out.Format.Duration != "" {
		fmt.Sscanf(out.Format.Duration, "%f", &r.DurationSeconds)
	}
	for _, s := range out.Streams {
		if s.CodecType == "video" {
			r.HasVideo = true
			r.Codec = s.CodecName
			r.Width = s.Width
			r.Height = s.Height
		}
		if s.CodecType == "audio" {
			r.HasAudio = true
		}
	}
	return r, nil
}

// Check trả ValidationError theo đúng thứ tự ưu tiên ở mục 3.4.
func Check(r *ProbeResult, l Limits) error {
	if !r.HasVideo {
		return &ValidationError{Code: "NOT_A_VIDEO"}
	}
	if !allowedCodecs[r.Codec] {
		return &ValidationError{Code: "CODEC_UNSUPPORTED"}
	}
	if r.DurationSeconds > l.MaxDurationSeconds {
		return &ValidationError{Code: "DURATION_EXCEEDED"}
	}
	if r.Height > l.MaxHeightPixels {
		return &ValidationError{Code: "HEIGHT_EXCEEDED"}
	}
	return nil
}
