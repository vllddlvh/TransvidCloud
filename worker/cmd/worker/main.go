// Worker: nhận message SQS -> transcode -> ghi output/state.
// Thiếu env bắt buộc hoặc thiếu ffmpeg/ffprobe thì thoát ngay (exit 1).
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"os/exec"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/aws/aws-sdk-go-v2/service/sqs"

	"github.com/vllddlvh/TransvidCloud/worker/internal/config"
	"github.com/vllddlvh/TransvidCloud/worker/internal/consumer"
	"github.com/vllddlvh/TransvidCloud/worker/internal/heartbeat"
	"github.com/vllddlvh/TransvidCloud/worker/internal/job"
	"github.com/vllddlvh/TransvidCloud/worker/internal/logging"
	"github.com/vllddlvh/TransvidCloud/worker/internal/shutdown"
	"github.com/vllddlvh/TransvidCloud/worker/internal/state"
	"github.com/vllddlvh/TransvidCloud/worker/internal/storage"
	"github.com/vllddlvh/TransvidCloud/worker/internal/validate"
	"github.com/vllddlvh/TransvidCloud/worker/internal/workdir"
)

const releaseTimeout = 10 * time.Second

func main() {
	logger := logging.New()
	if err := run(logger); err != nil {
		logger.Error("worker_startup_failed", "err", err)
		os.Exit(1)
	}
}

func run(logger *slog.Logger) error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}
	if err := checkBinaries(); err != nil {
		return err
	}

	awsCfg, err := awsconfig.LoadDefaultConfig(context.Background(),
		awsconfig.WithRegion(cfg.AWSRegion),
		awsconfig.WithEndpointResolverWithOptions(customResolver(cfg.AWSEndpointURL)),
	)
	if err != nil {
		return fmt.Errorf("tải cấu hình AWS: %w", err)
	}

	receiver := consumer.NewSQSReceiver(sqs.NewFromConfig(awsCfg), cfg.JobsQueueURL, visibilitySeconds(cfg.VisibilityTimeout))
	// LocalStack (localhost) không resolve virtual-host style; buộc path-style.
	s3Client := s3.NewFromConfig(awsCfg, func(o *s3.Options) { o.UsePathStyle = true })
	store := storage.New(s3Client, cfg.InputBucket, cfg.OutputBucket)
	st := state.New(dynamodb.NewFromConfig(awsCfg), cfg.VideosTable)
	runner := job.NewRunner(job.Deps{
		Receiver: receiver,
		Storage:  store,
		State:    st,
		Logger:   logger,
		Limits:   validate.Limits{MaxDurationSeconds: cfg.MaxDurationSecs, MaxHeightPixels: cfg.MaxHeightPixels},
		TmpDir:   cfg.TmpDir,
		// 2 lần thử lại, backoff 2s: đủ để phân biệt lỗi mạng thoáng qua với
		// mất quyền sở hữu thật sự, không kéo dài quá gần 1/4 interval.
		Heartbeat: heartbeat.Config{
			Interval:     cfg.HeartbeatInterval,
			Timeout:      cfg.VisibilityTimeout,
			MaxRetries:   2,
			RetryBackoff: 2 * time.Second,
		},
		FFmpegTimeout: cfg.FFmpegTimeout,
	})

	// Thư mục tạm có thể còn rác từ lần chạy trước bị chết; Prepare chỉ dọn
	// khi thấy marker của worker.
	if err := workdir.Prepare(cfg.TmpDir); err != nil {
		return fmt.Errorf("chuẩn bị thư mục tạm %s: %w", cfg.TmpDir, err)
	}

	ctx, stop := shutdown.Notify(context.Background())
	defer stop()

	logger.Info("worker_started",
		"inputBucket", cfg.InputBucket, "outputBucket", cfg.OutputBucket,
		"queueURL", cfg.JobsQueueURL, "videosTable", cfg.VideosTable,
		"maxDurationSeconds", cfg.MaxDurationSecs, "maxHeightPixels", cfg.MaxHeightPixels,
		"heartbeatInterval", cfg.HeartbeatInterval.String(),
		"visibilityTimeout", cfg.VisibilityTimeout.String(),
		"tmpDir", cfg.TmpDir)

	for {
		select {
		case <-ctx.Done():
			logger.Info("shutdown")
			return nil
		default:
		}

		msg, err := receiver.Receive(ctx)
		if err != nil {
			if ctx.Err() != nil {
				logger.Info("shutdown")
				return nil
			}
			logger.Error("receive_failed", "err", err)
			time.Sleep(2 * time.Second)
			continue
		}
		if msg == nil {
			continue
		}

		outcome, err := runner.Run(ctx, msg)
		switch {
		case ctx.Err() != nil:
			if !outcome.Acked {
				if rerr := releaseMessage(receiver, msg); rerr != nil {
					logger.Error("message_release_failed", "err", rerr)
				} else {
					logger.Info("message_released", "visibilitySeconds", heartbeat.ReleaseVisibilitySeconds)
				}
			}
			logger.Info("shutdown", "acked", outcome.Acked)
			return nil
		case errors.Is(err, heartbeat.ErrOwnershipLost):
			// Message đã thuộc worker khác; không đụng vào receipt handle cũ.
			logger.Warn("job_ownership_lost", "err", err)
		case err != nil:
			logger.Warn("job_run_error", "err", err)
		}
	}
}

// releaseMessage trả message về queue sớm khi SIGTERM. Dùng context riêng vì
// ctx của worker đã bị hủy.
func releaseMessage(receiver consumer.Receiver, msg *consumer.Message) error {
	ctx, cancel := context.WithTimeout(context.Background(), releaseTimeout)
	defer cancel()
	return receiver.ChangeVisibility(ctx, msg, heartbeat.ReleaseVisibilitySeconds)
}

// visibilitySeconds đổi duration sang giây (làm tròn lên) cho ReceiveMessage.
func visibilitySeconds(d time.Duration) int32 {
	s := int32((d + time.Second - 1) / time.Second)
	if s < 1 {
		return 1
	}
	return s
}

func checkBinaries() error {
	for _, bin := range []string{"ffmpeg", "ffprobe"} {
		if _, err := exec.LookPath(bin); err != nil {
			return fmt.Errorf("không tìm thấy %s trong PATH", bin)
		}
	}
	return nil
}

// customResolver trỏ mọi dịch vụ AWS về LocalStack khi có AWS_ENDPOINT_URL.
func customResolver(endpointURL string) aws.EndpointResolverWithOptionsFunc {
	return func(service, region string, _ ...interface{}) (aws.Endpoint, error) {
		if endpointURL == "" {
			return aws.Endpoint{}, &aws.EndpointNotFoundError{}
		}
		return aws.Endpoint{URL: endpointURL, SigningRegion: region}, nil
	}
}
