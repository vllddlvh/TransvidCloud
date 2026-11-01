// Package job ghép các bước thành pipeline chạy một job theo
// docs-worker/3. Architecture.md mục 3.3. Heartbeat giữ visibility từ lúc nhận
// tới trước ACK; mọi nhánh chỉ ACK sau khi kết quả đã được lưu.
package job

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/vllddlvh/TransvidCloud/worker/internal/consumer"
	"github.com/vllddlvh/TransvidCloud/worker/internal/heartbeat"
	"github.com/vllddlvh/TransvidCloud/worker/internal/state"
	"github.com/vllddlvh/TransvidCloud/worker/internal/transcode"
	"github.com/vllddlvh/TransvidCloud/worker/internal/validate"
	"github.com/vllddlvh/TransvidCloud/worker/internal/workdir"
)

const ackTimeout = 10 * time.Second

type Message struct {
	SchemaVersion int    `json:"schemaVersion"`
	JobID         string `json:"jobId"`
	VideoID       string `json:"videoId"`
	ProjectID     string `json:"projectId"`
	InputKey      string `json:"inputKey"`
	RequestedAt   string `json:"requestedAt"`
}

// ParseError: message sai schema — xóa message, không retry.
type ParseError struct {
	Code  string // UNSUPPORTED_SCHEMA | INVALID_MESSAGE
	Cause error
}

func (e *ParseError) Error() string { return e.Code + ": " + e.Cause.Error() }
func (e *ParseError) Unwrap() error { return e.Cause }

// InterruptedError: job dừng vì SIGTERM hoặc mất quyền sở hữu message.
type InterruptedError struct {
	Cause error
}

func (e *InterruptedError) Error() string { return "job_interrupted: " + e.Cause.Error() }
func (e *InterruptedError) Unwrap() error { return e.Cause }

// Outcome cho supervisor biết message đã được ACK chưa (để quyết định trả
// message lúc shutdown và không trả nhầm message đã xóa).
type Outcome struct {
	Acked bool
}

type Storage interface {
	DownloadToFile(ctx context.Context, key, dst string) error
	UploadDir(ctx context.Context, keyPrefix, dir string) (int, int64, error)
}

type Store interface {
	GetStatus(ctx context.Context, videoID string) (string, error)
	MarkProcessing(ctx context.Context, videoID string) error
	MarkCompleted(ctx context.Context, videoID string, assets map[string]string) error
	MarkFailed(ctx context.Context, videoID, status, errorCode string) error
}

type Deps struct {
	Receiver      consumer.Receiver
	Storage       Storage
	State         Store
	Logger        *slog.Logger
	Limits        validate.Limits
	TmpDir        string
	FFmpegTimeout time.Duration
	Heartbeat     heartbeat.Config

	Probe       func(ctx context.Context, path string) (*validate.ProbeResult, error)
	RunFFmpeg   func(ctx context.Context, args []string) error
	WriteMaster func(outDir string, srcW, srcH int) error
}

type Runner struct {
	deps Deps
}

func NewRunner(d Deps) *Runner {
	if d.Probe == nil {
		d.Probe = validate.Probe
	}
	if d.RunFFmpeg == nil {
		d.RunFFmpeg = transcode.Run
	}
	if d.WriteMaster == nil {
		d.WriteMaster = transcode.WriteMaster
	}
	return &Runner{deps: d}
}

// Run xử lý một message; xem quyết định xóa/giữ message ở Contact docs mục 5.4.
func (r *Runner) Run(ctx context.Context, msg *consumer.Message) (Outcome, error) {
	if err := ctx.Err(); err != nil {
		return Outcome{}, interrupted(ctx)
	}

	m, err := parseMessage(msg.Body)
	if err != nil {
		var pe *ParseError
		errors.As(err, &pe)
		r.deps.Logger.Warn("invalid_message", "code", pe.Code, "err", pe.Cause)
		if ackErr := r.deleteMessage(ctx, msg); ackErr != nil {
			r.deps.Logger.Error("ack_failed", "err", ackErr)
			return Outcome{}, ackErr
		}
		return Outcome{Acked: true}, nil
	}

	log := r.deps.Logger.With("jobId", m.JobID, "videoId", m.VideoID, "projectId", m.ProjectID)
	jobCtx, cancelJob := context.WithCancelCause(ctx)
	defer cancelJob(nil)

	hb, err := heartbeat.Start(jobCtx, r.deps.Heartbeat, r.deps.Receiver, msg, log,
		func(err error) { cancelJob(fmt.Errorf("%w: %v", heartbeat.ErrOwnershipLost, err)) })
	if err != nil {
		log.Error("job_failed", "errorCode", "HEARTBEAT_START_FAILED", "err", err)
		return Outcome{}, err
	}
	defer hb.Stop()

	started := time.Now()
	logArgs := []any{"attempt", msg.Attempt, "inputKey", m.InputKey}
	if t, parseErr := time.Parse(time.RFC3339, m.RequestedAt); parseErr == nil {
		logArgs = append(logArgs, "queueWaitMs", time.Since(t).Milliseconds())
	} else {
		logArgs = append(logArgs, "queueWaitMsMissing", true)
	}
	log.Info("job_received", logArgs...)

	// Idempotency: đã COMPLETED thì bỏ qua và xóa message.
	status, err := r.deps.State.GetStatus(jobCtx, m.VideoID)
	if err != nil {
		if ierr := interruptedErr(jobCtx); ierr != nil {
			return Outcome{}, ierr
		}
		log.Error("job_failed", "errorCode", "DDB_GET_FAILED", "err", err)
		return Outcome{}, err
	}
	if status == state.StatusCompleted {
		log.Info("skipped_completed")
		if err := r.ack(jobCtx, hb, msg, log); err != nil {
			return Outcome{}, err
		}
		return Outcome{Acked: true}, nil
	}

	if err := r.deps.State.MarkProcessing(jobCtx, m.VideoID); err != nil {
		if errors.Is(err, state.ErrAlreadyCompleted) {
			log.Info("skipped_completed", "reason", "completed_by_other")
			if ackErr := r.ack(jobCtx, hb, msg, log); ackErr != nil {
				return Outcome{}, ackErr
			}
			return Outcome{Acked: true}, nil
		}
		if ierr := interruptedErr(jobCtx); ierr != nil {
			return Outcome{}, ierr
		}
		log.Error("job_failed", "errorCode", "DDB_UPDATE_FAILED", "err", err)
		return Outcome{}, err
	}
	log.Info("mark_processing")

	workDir, err := workdir.Path(r.deps.TmpDir, m.JobID)
	if err != nil {
		log.Error("job_failed", "errorCode", "INVALID_MESSAGE", "err", err)
		return Outcome{}, err
	}
	srcPath := filepath.Join(workDir, "source"+filepath.Ext(m.InputKey))
	outDir := filepath.Join(workDir, "out")
	defer os.RemoveAll(workDir)

	if !strings.HasPrefix(m.InputKey, m.ProjectID+"/"+m.VideoID+"/") {
		log.Warn("input_key_mismatch", "inputKey", m.InputKey)
	}

	var bytesIn int64
	if err := r.deps.Storage.DownloadToFile(jobCtx, m.InputKey, srcPath); err != nil {
		return r.failSystem(jobCtx, hb, msg, log, m.VideoID, state.StatusFailedStorage, "S3_GET_FAILED", started, err)
	}
	if fi, statErr := os.Stat(srcPath); statErr == nil {
		bytesIn = fi.Size()
	}
	log.Info("downloaded", "bytesIn", bytesIn)
	if ierr := interruptedErr(jobCtx); ierr != nil {
		return Outcome{}, ierr
	}

	probe, err := r.deps.Probe(jobCtx, srcPath)
	if err != nil {
		if ierr := interruptedErr(jobCtx); ierr != nil {
			return Outcome{}, ierr
		}
		return r.failValidation(jobCtx, hb, msg, log, m.VideoID, "NOT_A_VIDEO", started, err)
	}
	if err := validate.Check(probe, r.deps.Limits); err != nil {
		var ve *validate.ValidationError
		errors.As(err, &ve)
		return r.failValidation(jobCtx, hb, msg, log, m.VideoID, ve.Code, started, err)
	}
	log.Info("validated", "duration", probe.DurationSeconds, "height", probe.Height,
		"codec", probe.Codec, "hasAudio", probe.HasAudio)

	if err := os.MkdirAll(outDir, 0o755); err != nil {
		return r.failSystem(jobCtx, hb, msg, log, m.VideoID, state.StatusFailedStorage, "LOCAL_FS_FAILED", started, err)
	}
	for _, d := range []string{"hls/360p", "hls/720p", "thumb"} {
		if err := os.MkdirAll(filepath.Join(outDir, d), 0o755); err != nil {
			return r.failSystem(jobCtx, hb, msg, log, m.VideoID, state.StatusFailedStorage, "LOCAL_FS_FAILED", started, err)
		}
	}

	log.Info("transcode_started")
	ffmpegStarted := time.Now()
	tctx, cancel := context.WithTimeout(jobCtx, r.deps.FFmpegTimeout)
	tErr := r.deps.RunFFmpeg(tctx, transcode.BuildArgs(srcPath, outDir, probe.Height))
	cancel()
	if tErr != nil {
		if ierr := interruptedErr(jobCtx); ierr != nil {
			return Outcome{}, ierr
		}
		return r.failSystem(jobCtx, hb, msg, log, m.VideoID, state.StatusFailedTranscode, ffmpegCode(tErr), started, tErr)
	}

	if tErr = r.deps.RunFFmpeg(jobCtx, transcode.BuildThumbnailArgs(srcPath, filepath.Join(outDir, "thumb", "default.jpg"), probe.DurationSeconds)); tErr != nil {
		if ierr := interruptedErr(jobCtx); ierr != nil {
			return Outcome{}, ierr
		}
		return r.failSystem(jobCtx, hb, msg, log, m.VideoID, state.StatusFailedTranscode, ffmpegCode(tErr), started, tErr)
	}

	if err := r.deps.WriteMaster(outDir, probe.Width, probe.Height); err != nil {
		return r.failSystem(jobCtx, hb, msg, log, m.VideoID, state.StatusFailedTranscode, "MASTER_WRITE_FAILED", started, err)
	}
	ffmpegMs := time.Since(ffmpegStarted).Milliseconds()
	log.Info("transcode_done", "ffmpegMs", ffmpegMs)
	if ierr := interruptedErr(jobCtx); ierr != nil {
		return Outcome{}, ierr
	}

	keyPrefix := m.ProjectID + "/" + m.VideoID
	objects, bytesOut, err := r.deps.Storage.UploadDir(jobCtx, keyPrefix, outDir)
	if err != nil {
		if ierr := interruptedErr(jobCtx); ierr != nil {
			return Outcome{}, ierr
		}
		return r.failSystem(jobCtx, hb, msg, log, m.VideoID, state.StatusFailedStorage, "S3_PUT_FAILED", started, err)
	}
	log.Info("uploaded", "objects", objects, "bytesOut", bytesOut)
	if ierr := interruptedErr(jobCtx); ierr != nil {
		return Outcome{}, ierr
	}

	assets := map[string]string{
		"hls":       keyPrefix + "/hls/master.m3u8",
		"hls360":    keyPrefix + "/hls/360p/index.m3u8",
		"hls720":    keyPrefix + "/hls/720p/index.m3u8",
		"thumbnail": keyPrefix + "/thumb/default.jpg",
	}
	if err := r.deps.State.MarkCompleted(jobCtx, m.VideoID, assets); err != nil {
		if errors.Is(err, state.ErrAlreadyCompleted) {
			// Worker khác thắng ConditionExpression — output đã đúng key, coi như xong.
			if ackErr := r.ack(jobCtx, hb, msg, log); ackErr != nil {
				return Outcome{}, ackErr
			}
			log.Info("job_finished", "status", "COMPLETED_BY_OTHER",
				"durationMs", time.Since(started).Milliseconds())
			return Outcome{Acked: true}, nil
		}
		if ierr := interruptedErr(jobCtx); ierr != nil {
			return Outcome{}, ierr
		}
		return r.failSystem(jobCtx, hb, msg, log, m.VideoID, state.StatusFailedStorage, "DDB_UPDATE_FAILED", started, err)
	}
	if ierr := interruptedErr(jobCtx); ierr != nil {
		// Đã lưu COMPLETED nhưng không còn giữ message: lần nhận lại sẽ
		// idempotency-skip và ACK, không ghi lùi FAILED_*.
		log.Warn("job_completed_without_ack", "err", ierr)
		return Outcome{}, ierr
	}

	if err := r.ack(jobCtx, hb, msg, log); err != nil {
		return Outcome{}, err
	}
	log.Info("job_finished", "status", state.StatusCompleted,
		"durationMs", time.Since(started).Milliseconds(), "attempt", msg.Attempt,
		"ffmpegMs", ffmpegMs, "bytesIn", bytesIn, "bytesOut", bytesOut)
	return Outcome{Acked: true}, nil
}

// ack dừng heartbeat trước khi xóa message để không gia hạn lại sau ACK.
func (r *Runner) ack(ctx context.Context, hb *heartbeat.Controller, msg *consumer.Message, log *slog.Logger) error {
	hb.Stop()
	if err := r.deleteMessage(ctx, msg); err != nil {
		log.Error("ack_failed", "err", err)
		return err
	}
	return nil
}

// deleteMessage dùng context riêng đủ ngắn để ACK không bị cancel theo job;
// lỗi xóa được trả về để message còn có thể được xử lý lại.
func (r *Runner) deleteMessage(ctx context.Context, msg *consumer.Message) error {
	ackCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), ackTimeout)
	defer cancel()
	return r.deps.Receiver.Delete(ackCtx, msg)
}

// failValidation: ghi FAILED_VALIDATION rồi xóa message (retry vô ích — N10).
// Không xóa nếu chưa lưu được kết quả.
func (r *Runner) failValidation(ctx context.Context, hb *heartbeat.Controller, msg *consumer.Message,
	log *slog.Logger, videoID, code string, started time.Time, cause error) (Outcome, error) {
	if ierr := interruptedErr(ctx); ierr != nil {
		return Outcome{}, ierr
	}
	err := r.deps.State.MarkFailed(ctx, videoID, state.StatusFailedValidation, code)
	switch {
	case err == nil:
		log.Info("job_failed", "status", state.StatusFailedValidation, "errorCode", code,
			"durationMs", time.Since(started).Milliseconds(), "err", cause)
	case errors.Is(err, state.ErrAlreadyCompleted):
		log.Info("skipped_completed", "reason", "completed_by_other")
	default:
		if ierr := interruptedErr(ctx); ierr != nil {
			return Outcome{}, ierr
		}
		log.Error("job_failed", "errorCode", code, "markFailedErr", err)
		return Outcome{}, fmt.Errorf("ghi %s thất bại, giữ message để retry: %w", code, err)
	}
	if ackErr := r.ack(ctx, hb, msg, log); ackErr != nil {
		return Outcome{}, ackErr
	}
	return Outcome{Acked: true}, nil
}

// failSystem: ghi FAILED_* rồi giữ message để SQS giao lại (N10). Nếu video đã
// COMPLETED bởi worker khác thì ACK message trùng; nếu chưa lưu được lỗi thì
// cũng giữ message thay vì ACK.
func (r *Runner) failSystem(ctx context.Context, hb *heartbeat.Controller, msg *consumer.Message,
	log *slog.Logger, videoID, status, code string, started time.Time, cause error) (Outcome, error) {
	if ierr := interruptedErr(ctx); ierr != nil {
		return Outcome{}, ierr
	}
	err := r.deps.State.MarkFailed(ctx, videoID, status, code)
	switch {
	case err == nil:
		log.Error("job_failed", "status", status, "errorCode", code,
			"durationMs", time.Since(started).Milliseconds(), "err", cause)
		return Outcome{}, cause
	case errors.Is(err, state.ErrAlreadyCompleted):
		if ackErr := r.ack(ctx, hb, msg, log); ackErr != nil {
			return Outcome{}, ackErr
		}
		log.Info("job_finished", "status", "COMPLETED_BY_OTHER",
			"durationMs", time.Since(started).Milliseconds())
		return Outcome{Acked: true}, nil
	default:
		if ierr := interruptedErr(ctx); ierr != nil {
			return Outcome{}, ierr
		}
		log.Error("job_failed", "errorCode", code, "markFailedErr", err)
		return Outcome{}, fmt.Errorf("%w (ghi %s thất bại: %v)", cause, code, err)
	}
}

func interruptedErr(ctx context.Context) error {
	if ctx.Err() == nil {
		return nil
	}
	return interrupted(ctx)
}

func interrupted(ctx context.Context) error {
	cause := context.Cause(ctx)
	if cause == nil {
		cause = ctx.Err()
	}
	return &InterruptedError{Cause: cause}
}

func ffmpegCode(err error) string {
	var te *transcode.Error
	if errors.As(err, &te) {
		return te.Code
	}
	return "FFMPEG_START_FAILED"
}

func parseMessage(body string) (*Message, error) {
	var m Message
	if err := json.Unmarshal([]byte(body), &m); err != nil {
		return nil, &ParseError{Code: "INVALID_MESSAGE", Cause: err}
	}
	if m.SchemaVersion != 1 {
		return nil, &ParseError{Code: "UNSUPPORTED_SCHEMA", Cause: fmt.Errorf("schemaVersion=%d", m.SchemaVersion)}
	}
	for _, f := range []struct{ name, val string }{
		{"jobId", m.JobID}, {"videoId", m.VideoID}, {"projectId", m.ProjectID}, {"inputKey", m.InputKey},
	} {
		if f.val == "" {
			return nil, &ParseError{Code: "INVALID_MESSAGE", Cause: fmt.Errorf("thiếu %s", f.name)}
		}
	}
	if err := workdir.ValidateJobID(m.JobID); err != nil {
		return nil, &ParseError{Code: "INVALID_MESSAGE", Cause: err}
	}
	return &m, nil
}
