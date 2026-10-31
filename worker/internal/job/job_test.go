package job

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"github.com/vllddlvh/TransvidCloud/worker/internal/consumer"
	"github.com/vllddlvh/TransvidCloud/worker/internal/heartbeat"
	"github.com/vllddlvh/TransvidCloud/worker/internal/state"
	"github.com/vllddlvh/TransvidCloud/worker/internal/transcode"
	"github.com/vllddlvh/TransvidCloud/worker/internal/validate"
)

const validBody = `{"schemaVersion":1,"jobId":"job_9f2c1b","videoId":"vid_4a77e1","projectId":"prj_1c05d9","inputKey":"prj_1c05d9/vid_4a77e1/source.mp4","requestedAt":"2026-10-19T09:15:30Z"}`

type fakeReceiver struct {
	mu        sync.Mutex
	deleted   int
	deleteErr error
	visCalls  []int32
	visErr    error
}

func (f *fakeReceiver) Receive(context.Context) (*consumer.Message, error) { return nil, nil }

func (f *fakeReceiver) Delete(context.Context, *consumer.Message) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.deleted++
	return f.deleteErr
}

func (f *fakeReceiver) ChangeVisibility(_ context.Context, _ *consumer.Message, seconds int32) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.visCalls = append(f.visCalls, seconds)
	return f.visErr
}

func (f *fakeReceiver) deleteCount() int {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.deleted
}

func (f *fakeReceiver) visibilityCalls() []int32 {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]int32(nil), f.visCalls...)
}

type fakeStorage struct {
	downloadKey string
	downloadErr error
	uploadErr   error
	prefix      string
	objects     int
}

func (f *fakeStorage) DownloadToFile(_ context.Context, key, _ string) error {
	f.downloadKey = key
	return f.downloadErr
}

func (f *fakeStorage) UploadDir(_ context.Context, keyPrefix, _ string) (int, int64, error) {
	f.prefix = keyPrefix
	if f.uploadErr != nil {
		return 0, 0, f.uploadErr
	}
	if f.objects == 0 {
		f.objects = 3
	}
	return f.objects, 1024, nil
}

type fakeStore struct {
	status              string
	getErr              error
	markProcessingErr   error
	markProcessingCalls int
	completedAssets     map[string]string
	completedErr        error
	completedCalls      int
	failedStatus        string
	failedCode          string
	failedErr           error
	failedCalls         int
}

func (f *fakeStore) GetStatus(context.Context, string) (string, error) { return f.status, f.getErr }

func (f *fakeStore) MarkProcessing(context.Context, string) error {
	f.markProcessingCalls++
	return f.markProcessingErr
}

func (f *fakeStore) MarkCompleted(_ context.Context, _ string, assets map[string]string) error {
	f.completedCalls++
	f.completedAssets = assets
	return f.completedErr
}

func (f *fakeStore) MarkFailed(_ context.Context, _, status, code string) error {
	f.failedCalls++
	f.failedStatus = status
	f.failedCode = code
	return f.failedErr
}

type testRig struct {
	runner    *Runner
	receiver  *fakeReceiver
	storage   *fakeStorage
	store     *fakeStore
	probeHits int
	ffmpegHit int
}

func newRig(t *testing.T) *testRig {
	t.Helper()
	rig := &testRig{
		receiver: &fakeReceiver{},
		storage:  &fakeStorage{},
		store:    &fakeStore{},
	}
	rig.runner = NewRunner(Deps{
		Receiver:      rig.receiver,
		Storage:       rig.storage,
		State:         rig.store,
		Logger:        slog.New(slog.NewJSONHandler(io.Discard, nil)),
		Limits:        validate.Limits{MaxDurationSeconds: 300, MaxHeightPixels: 1080},
		TmpDir:        filepath.Join(t.TempDir(), "tvc"),
		FFmpegTimeout: time.Minute,
		Heartbeat:     heartbeat.Config{Interval: time.Hour, Timeout: 300 * time.Second},
		Probe: func(context.Context, string) (*validate.ProbeResult, error) {
			rig.probeHits++
			return &validate.ProbeResult{
				HasVideo: true, Codec: "h264", DurationSeconds: 10,
				Width: 1280, Height: 720, HasAudio: true,
			}, nil
		},
		RunFFmpeg: func(context.Context, []string) error {
			rig.ffmpegHit++
			return nil
		},
		WriteMaster: func(string, int, int) error { return nil },
	})
	return rig
}

func run(t *testing.T, rig *testRig, ctx context.Context) (Outcome, error) {
	t.Helper()
	return rig.runner.Run(ctx, &consumer.Message{Body: validBody, ReceiptHandle: "rh", Attempt: 1})
}

func TestRunHappyPath(t *testing.T) {
	rig := newRig(t)
	out, err := run(t, rig, context.Background())
	if err != nil {
		t.Fatalf("Run: %v", err)
	}
	if !out.Acked || rig.receiver.deleteCount() != 1 {
		t.Errorf("ACK sai: outcome=%+v deleted=%d", out, rig.receiver.deleteCount())
	}
	if rig.store.markProcessingCalls != 1 || rig.store.completedCalls != 1 {
		t.Errorf("state calls: processing=%d completed=%d", rig.store.markProcessingCalls, rig.store.completedCalls)
	}
	if rig.store.failedCalls != 0 {
		t.Errorf("không mong MarkFailed, nhận %d", rig.store.failedCalls)
	}
	for _, k := range []string{"hls", "hls360", "hls720", "thumbnail"} {
		if rig.store.completedAssets[k] == "" {
			t.Errorf("thiếu asset %q: %v", k, rig.store.completedAssets)
		}
	}
	if rig.storage.prefix != "prj_1c05d9/vid_4a77e1" {
		t.Errorf("upload prefix = %q", rig.storage.prefix)
	}
	if rig.ffmpegHit != 2 {
		t.Errorf("số lần ffmpeg = %d, want 2", rig.ffmpegHit)
	}
	if calls := rig.receiver.visibilityCalls(); len(calls) != 0 {
		t.Errorf("heartbeat không nên chạy với interval dài: %v", calls)
	}
}

func TestRunSkipsCompleted(t *testing.T) {
	rig := newRig(t)
	rig.store.status = state.StatusCompleted
	out, err := run(t, rig, context.Background())
	if err != nil {
		t.Fatalf("Run: %v", err)
	}
	if !out.Acked || rig.receiver.deleteCount() != 1 {
		t.Errorf("phải ACK message trùng: %+v", out)
	}
	if rig.store.markProcessingCalls != 0 || rig.probeHits != 0 || rig.ffmpegHit != 0 {
		t.Errorf("không được xử lý lại: processing=%d probe=%d ffmpeg=%d",
			rig.store.markProcessingCalls, rig.probeHits, rig.ffmpegHit)
	}
}

func TestRunSkipAckFailureReturnsError(t *testing.T) {
	rig := newRig(t)
	rig.store.status = state.StatusCompleted
	rig.receiver.deleteErr = errors.New("sqs delete failed")
	out, err := run(t, rig, context.Background())
	if err == nil || out.Acked {
		t.Fatalf("mong lỗi ACK, nhận out=%+v err=%v", out, err)
	}
}

func TestRunInvalidMessageAcked(t *testing.T) {
	rig := newRig(t)
	out, err := rig.runner.Run(context.Background(), &consumer.Message{Body: `{`, Attempt: 1})
	if err != nil || !out.Acked {
		t.Fatalf("message sai schema phải ACK: out=%+v err=%v", out, err)
	}
	if rig.receiver.deleteCount() != 1 {
		t.Errorf("delete count = %d", rig.receiver.deleteCount())
	}
	if rig.store.markProcessingCalls != 0 {
		t.Error("không được chạm DynamoDB")
	}
}

func TestRunUnsafeJobIDAcked(t *testing.T) {
	rig := newRig(t)
	body := `{"schemaVersion":1,"jobId":"../escape","videoId":"vid_4a77e1","projectId":"prj_1c05d9","inputKey":"prj_1c05d9/vid_4a77e1/source.mp4"}`
	out, err := rig.runner.Run(context.Background(), &consumer.Message{Body: body, Attempt: 1})
	if err != nil || !out.Acked {
		t.Fatalf("jobId không an toàn phải ACK: out=%+v err=%v", out, err)
	}
	if rig.receiver.deleteCount() != 1 || rig.store.markProcessingCalls != 0 {
		t.Errorf("xử lý sai: deleted=%d processing=%d", rig.receiver.deleteCount(), rig.store.markProcessingCalls)
	}
}

func TestRunValidationFailureAcks(t *testing.T) {
	tests := []struct {
		name     string
		probe    *validate.ProbeResult
		probeErr error
		wantCode string
	}{
		{"probe lỗi", nil, errors.New("ffprobe failed"), "NOT_A_VIDEO"},
		{"quá cao", &validate.ProbeResult{HasVideo: true, Codec: "h264", DurationSeconds: 10, Height: 1440}, nil, "HEIGHT_EXCEEDED"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			rig := newRig(t)
			rig.runner = NewRunner(Deps{
				Receiver: rig.receiver, Storage: rig.storage, State: rig.store,
				Logger: slog.New(slog.NewJSONHandler(io.Discard, nil)),
				Limits: validate.Limits{MaxDurationSeconds: 300, MaxHeightPixels: 1080},
				TmpDir: filepath.Join(t.TempDir(), "tvc"), FFmpegTimeout: time.Minute,
				Heartbeat:   heartbeat.Config{Interval: time.Hour, Timeout: 300 * time.Second},
				Probe:       func(context.Context, string) (*validate.ProbeResult, error) { return tt.probe, tt.probeErr },
				RunFFmpeg:   func(context.Context, []string) error { rig.ffmpegHit++; return nil },
				WriteMaster: func(string, int, int) error { return nil },
			})
			out, err := run(t, rig, context.Background())
			if err != nil || !out.Acked {
				t.Fatalf("validation fail phải ACK: out=%+v err=%v", out, err)
			}
			if rig.store.failedStatus != state.StatusFailedValidation || rig.store.failedCode != tt.wantCode {
				t.Errorf("failed = %s/%s, want FAILED_VALIDATION/%s",
					rig.store.failedStatus, rig.store.failedCode, tt.wantCode)
			}
			if rig.ffmpegHit != 0 {
				t.Errorf("không được chạy FFmpeg khi validation fail: %d", rig.ffmpegHit)
			}
			if rig.receiver.deleteCount() != 1 {
				t.Errorf("delete count = %d", rig.receiver.deleteCount())
			}
		})
	}
}

func TestRunValidationMarkFailedFailureKeepsMessage(t *testing.T) {
	rig := newRig(t)
	rig.runner = NewRunner(Deps{
		Receiver: rig.receiver, Storage: rig.storage, State: rig.store,
		Logger: slog.New(slog.NewJSONHandler(io.Discard, nil)),
		Limits: validate.Limits{MaxDurationSeconds: 300, MaxHeightPixels: 1080},
		TmpDir: filepath.Join(t.TempDir(), "tvc"), FFmpegTimeout: time.Minute,
		Heartbeat: heartbeat.Config{Interval: time.Hour, Timeout: 300 * time.Second},
		Probe: func(context.Context, string) (*validate.ProbeResult, error) {
			return &validate.ProbeResult{HasVideo: true, Codec: "h264", DurationSeconds: 10, Height: 1440}, nil
		},
		RunFFmpeg:   func(context.Context, []string) error { return nil },
		WriteMaster: func(string, int, int) error { return nil },
	})
	rig.store.failedErr = errors.New("ddb down")
	out, err := run(t, rig, context.Background())
	if err == nil || out.Acked {
		t.Fatalf("chưa lưu được kết quả thì không ACK: out=%+v err=%v", out, err)
	}
	if rig.receiver.deleteCount() != 0 {
		t.Errorf("không được xóa message: %d", rig.receiver.deleteCount())
	}
}

func TestRunValidationAlreadyCompletedAcks(t *testing.T) {
	rig := newRig(t)
	rig.store.failedErr = state.ErrAlreadyCompleted
	rig.runner = NewRunner(Deps{
		Receiver: rig.receiver, Storage: rig.storage, State: rig.store,
		Logger: slog.New(slog.NewJSONHandler(io.Discard, nil)),
		Limits: validate.Limits{MaxDurationSeconds: 300, MaxHeightPixels: 1080},
		TmpDir: filepath.Join(t.TempDir(), "tvc"), FFmpegTimeout: time.Minute,
		Heartbeat: heartbeat.Config{Interval: time.Hour, Timeout: 300 * time.Second},
		Probe: func(context.Context, string) (*validate.ProbeResult, error) {
			return &validate.ProbeResult{HasVideo: true, Codec: "h264", DurationSeconds: 10, Height: 1440}, nil
		},
		RunFFmpeg:   func(context.Context, []string) error { return nil },
		WriteMaster: func(string, int, int) error { return nil },
	})
	out, err := run(t, rig, context.Background())
	if err != nil || !out.Acked {
		t.Fatalf("video đã COMPLETED bởi worker khác thì ACK: out=%+v err=%v", out, err)
	}
	if rig.receiver.deleteCount() != 1 {
		t.Errorf("delete count = %d", rig.receiver.deleteCount())
	}
}

func TestRunDownloadFailureKeepsMessage(t *testing.T) {
	rig := newRig(t)
	rig.storage.downloadErr = errors.New("s3 down")
	out, err := run(t, rig, context.Background())
	if err == nil || out.Acked {
		t.Fatalf("lỗi hệ thống phải giữ message: out=%+v err=%v", out, err)
	}
	if rig.store.failedStatus != state.StatusFailedStorage || rig.store.failedCode != "S3_GET_FAILED" {
		t.Errorf("failed = %s/%s", rig.store.failedStatus, rig.store.failedCode)
	}
	if rig.receiver.deleteCount() != 0 {
		t.Errorf("không được ACK: %d", rig.receiver.deleteCount())
	}
}

func TestRunDownloadFailureMarkFailedFailsKeepsMessage(t *testing.T) {
	rig := newRig(t)
	rig.storage.downloadErr = errors.New("s3 down")
	rig.store.failedErr = errors.New("ddb down")
	out, err := run(t, rig, context.Background())
	if err == nil || out.Acked {
		t.Fatalf("mong lỗi và không ACK: out=%+v err=%v", out, err)
	}
	if rig.receiver.deleteCount() != 0 {
		t.Errorf("không được ACK: %d", rig.receiver.deleteCount())
	}
}

func TestRunDownloadFailureAlreadyCompletedAcks(t *testing.T) {
	rig := newRig(t)
	rig.storage.downloadErr = errors.New("s3 down")
	rig.store.failedErr = state.ErrAlreadyCompleted
	out, err := run(t, rig, context.Background())
	if err != nil || !out.Acked {
		t.Fatalf("video đã COMPLETED thì ACK: out=%+v err=%v", out, err)
	}
	if rig.receiver.deleteCount() != 1 {
		t.Errorf("delete count = %d", rig.receiver.deleteCount())
	}
}

func TestRunTranscodeFailureKeepsMessage(t *testing.T) {
	rig := newRig(t)
	rig.runner = NewRunner(Deps{
		Receiver: rig.receiver, Storage: rig.storage, State: rig.store,
		Logger: slog.New(slog.NewJSONHandler(io.Discard, nil)),
		Limits: validate.Limits{MaxDurationSeconds: 300, MaxHeightPixels: 1080},
		TmpDir: filepath.Join(t.TempDir(), "tvc"), FFmpegTimeout: time.Minute,
		Heartbeat: heartbeat.Config{Interval: time.Hour, Timeout: 300 * time.Second},
		Probe: func(context.Context, string) (*validate.ProbeResult, error) {
			return &validate.ProbeResult{HasVideo: true, Codec: "h264", DurationSeconds: 10, Width: 1280, Height: 720}, nil
		},
		RunFFmpeg: func(context.Context, []string) error {
			return &transcode.Error{Code: "FFMPEG_EXIT_1", Cause: errors.New("boom")}
		},
		WriteMaster: func(string, int, int) error { return nil },
	})
	out, err := run(t, rig, context.Background())
	if err == nil || out.Acked {
		t.Fatalf("lỗi transcode phải giữ message: out=%+v err=%v", out, err)
	}
	if rig.store.failedStatus != state.StatusFailedTranscode || rig.store.failedCode != "FFMPEG_EXIT_1" {
		t.Errorf("failed = %s/%s", rig.store.failedStatus, rig.store.failedCode)
	}
	if rig.receiver.deleteCount() != 0 {
		t.Errorf("không được ACK: %d", rig.receiver.deleteCount())
	}
}

func TestRunMarkCompletedFailsMarksFailed(t *testing.T) {
	rig := newRig(t)
	rig.store.completedErr = errors.New("ddb down")
	out, err := run(t, rig, context.Background())
	if err == nil || out.Acked {
		t.Fatalf("mong lỗi: out=%+v err=%v", out, err)
	}
	if rig.store.failedStatus != state.StatusFailedStorage || rig.store.failedCode != "DDB_UPDATE_FAILED" {
		t.Errorf("failed = %s/%s", rig.store.failedStatus, rig.store.failedCode)
	}
	if rig.receiver.deleteCount() != 0 {
		t.Errorf("không được ACK khi chưa ghi COMPLETED: %d", rig.receiver.deleteCount())
	}
}

func TestRunCompletedByOtherAcks(t *testing.T) {
	rig := newRig(t)
	rig.store.completedErr = state.ErrAlreadyCompleted
	out, err := run(t, rig, context.Background())
	if err != nil || !out.Acked {
		t.Fatalf("worker khác đã COMPLETED thì ACK message trùng: out=%+v err=%v", out, err)
	}
	if rig.receiver.deleteCount() != 1 {
		t.Errorf("delete count = %d", rig.receiver.deleteCount())
	}
	if rig.store.failedCalls != 0 {
		t.Errorf("không được ghi FAILED_*: %d", rig.store.failedCalls)
	}
}

func TestRunAckFailureAfterCompletion(t *testing.T) {
	rig := newRig(t)
	rig.receiver.deleteErr = errors.New("sqs delete failed")
	out, err := run(t, rig, context.Background())
	if err == nil || out.Acked {
		t.Fatalf("ACK lỗi phải trả error: out=%+v err=%v", out, err)
	}
	if rig.store.completedCalls != 1 {
		t.Errorf("COMPLETED phải được ghi trước ACK: %d", rig.store.completedCalls)
	}
	if rig.store.failedCalls != 0 {
		t.Errorf("không được ghi FAILED_* khi ACK lỗi: %d", rig.store.failedCalls)
	}
}

func TestRunOwnershipLossDuringTranscode(t *testing.T) {
	rig := newRig(t)
	started := make(chan struct{})
	rig.runner = NewRunner(Deps{
		Receiver: rig.receiver, Storage: rig.storage, State: rig.store,
		Logger: slog.New(slog.NewJSONHandler(io.Discard, nil)),
		Limits: validate.Limits{MaxDurationSeconds: 300, MaxHeightPixels: 1080},
		TmpDir: filepath.Join(t.TempDir(), "tvc"), FFmpegTimeout: time.Minute,
		Heartbeat: heartbeat.Config{Interval: time.Hour, Timeout: 300 * time.Second},
		Probe: func(context.Context, string) (*validate.ProbeResult, error) {
			return &validate.ProbeResult{HasVideo: true, Codec: "h264", DurationSeconds: 10, Width: 1280, Height: 720}, nil
		},
		RunFFmpeg: func(ctx context.Context, _ []string) error {
			close(started)
			<-ctx.Done()
			return ctx.Err()
		},
		WriteMaster: func(string, int, int) error { return nil },
	})
	ctx, cancel := context.WithCancelCause(context.Background())
	done := make(chan error, 1)
	go func() {
		_, err := run(t, rig, ctx)
		done <- err
	}()
	<-started
	cancel(heartbeat.ErrOwnershipLost)
	err := <-done
	if !errors.Is(err, heartbeat.ErrOwnershipLost) {
		t.Fatalf("err = %v, want ownership lost", err)
	}
	if rig.store.failedCalls != 0 || rig.receiver.deleteCount() != 0 {
		t.Errorf("mất quyền không được ghi FAILED_* hoặc ACK: failed=%d deleted=%d",
			rig.store.failedCalls, rig.receiver.deleteCount())
	}
}

func TestRunShutdownDuringTranscode(t *testing.T) {
	rig := newRig(t)
	started := make(chan struct{})
	rig.runner = NewRunner(Deps{
		Receiver: rig.receiver, Storage: rig.storage, State: rig.store,
		Logger: slog.New(slog.NewJSONHandler(io.Discard, nil)),
		Limits: validate.Limits{MaxDurationSeconds: 300, MaxHeightPixels: 1080},
		TmpDir: filepath.Join(t.TempDir(), "tvc"), FFmpegTimeout: time.Minute,
		Heartbeat: heartbeat.Config{Interval: time.Hour, Timeout: 300 * time.Second},
		Probe: func(context.Context, string) (*validate.ProbeResult, error) {
			return &validate.ProbeResult{HasVideo: true, Codec: "h264", DurationSeconds: 10, Width: 1280, Height: 720}, nil
		},
		RunFFmpeg: func(ctx context.Context, _ []string) error {
			close(started)
			<-ctx.Done()
			return ctx.Err()
		},
		WriteMaster: func(string, int, int) error { return nil },
	})
	ctx, cancel := context.WithCancelCause(context.Background())
	done := make(chan error, 1)
	go func() {
		_, err := run(t, rig, ctx)
		done <- err
	}()
	<-started
	cancel(context.Canceled)
	err := <-done
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("err = %v, want context.Canceled", err)
	}
	if rig.store.failedCalls != 0 || rig.receiver.deleteCount() != 0 {
		t.Errorf("shutdown không được ghi FAILED_* hoặc ACK: failed=%d deleted=%d",
			rig.store.failedCalls, rig.receiver.deleteCount())
	}
}

func TestRunHeartbeatFailureStopsJob(t *testing.T) {
	rig := newRig(t)
	rig.receiver.visErr = errors.New("sqs down")
	rig.runner = NewRunner(Deps{
		Receiver: rig.receiver, Storage: rig.storage, State: rig.store,
		Logger: slog.New(slog.NewJSONHandler(io.Discard, nil)),
		Limits: validate.Limits{MaxDurationSeconds: 300, MaxHeightPixels: 1080},
		TmpDir: filepath.Join(t.TempDir(), "tvc"), FFmpegTimeout: time.Minute,
		Heartbeat: heartbeat.Config{Interval: 10 * time.Millisecond, Timeout: time.Second},
		Probe: func(context.Context, string) (*validate.ProbeResult, error) {
			return &validate.ProbeResult{HasVideo: true, Codec: "h264", DurationSeconds: 10, Width: 1280, Height: 720}, nil
		},
		RunFFmpeg: func(ctx context.Context, _ []string) error {
			<-ctx.Done()
			return ctx.Err()
		},
		WriteMaster: func(string, int, int) error { return nil },
	})
	done := make(chan error, 1)
	go func() {
		_, err := run(t, rig, context.Background())
		done <- err
	}()
	select {
	case err := <-done:
		if !errors.Is(err, heartbeat.ErrOwnershipLost) {
			t.Fatalf("err = %v, want ownership lost", err)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("heartbeat lỗi không dừng job")
	}
	if len(rig.receiver.visibilityCalls()) == 0 {
		t.Error("chưa gọi ChangeVisibility")
	}
	if rig.store.failedCalls != 0 || rig.receiver.deleteCount() != 0 {
		t.Errorf("không được ghi FAILED_* hoặc ACK: failed=%d deleted=%d",
			rig.store.failedCalls, rig.receiver.deleteCount())
	}
}

func TestParseMessage(t *testing.T) {
	tests := []struct {
		name     string
		body     string
		wantCode string
		wantOK   bool
	}{
		{"hợp lệ", validBody, "", true},
		{"json hỏng", `{`, "INVALID_MESSAGE", false},
		{"schema khác 1", `{"schemaVersion":2,"jobId":"j","videoId":"v","projectId":"p","inputKey":"k"}`, "UNSUPPORTED_SCHEMA", false},
		{"thiếu jobId", `{"schemaVersion":1,"videoId":"v","projectId":"p","inputKey":"k"}`, "INVALID_MESSAGE", false},
		{"thiếu inputKey", `{"schemaVersion":1,"jobId":"j","videoId":"v","projectId":"p"}`, "INVALID_MESSAGE", false},
		{"jobId path traversal", `{"schemaVersion":1,"jobId":"../x","videoId":"v","projectId":"p","inputKey":"k"}`, "INVALID_MESSAGE", false},
		{"jobId có dấu /", `{"schemaVersion":1,"jobId":"a/b","videoId":"v","projectId":"p","inputKey":"k"}`, "INVALID_MESSAGE", false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			m, err := parseMessage(tt.body)
			if tt.wantOK {
				if err != nil {
					t.Fatalf("mong hợp lệ, nhận %v", err)
				}
				if m.JobID != "job_9f2c1b" {
					t.Errorf("jobId = %q", m.JobID)
				}
				return
			}
			pe, ok := err.(*ParseError)
			if !ok {
				t.Fatalf("mong ParseError, nhận %T %v", err, err)
			}
			if pe.Code != tt.wantCode {
				t.Errorf("code = %q, want %q", pe.Code, tt.wantCode)
			}
		})
	}
}
