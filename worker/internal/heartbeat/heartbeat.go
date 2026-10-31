// Package heartbeat giữ visibility của message SQS trong lúc job đang chạy.
// Mỗi tick đặt lại toàn bộ VisibilityTimeout kể từ thời điểm gọi; lỗi sau
// maxRetries lần thì báo mất quyền sở hữu để job dừng thay vì tiếp tục ghi.
package heartbeat

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"sync"
	"time"

	"github.com/vllddlvh/TransvidCloud/worker/internal/consumer"
)

// ReleaseVisibilitySeconds là visibility rút ngắn khi SIGTERM (mục 5.4).
const ReleaseVisibilitySeconds int32 = 30

const maxAPITimeout = 10 * time.Second

type Config struct {
	Interval     time.Duration
	Timeout      time.Duration
	MaxRetries   int
	RetryBackoff time.Duration
	newTicker    func(time.Duration) ticker
}

type ticker interface {
	C() <-chan time.Time
	Stop()
}

type realTicker struct{ *time.Ticker }

func (t realTicker) C() <-chan time.Time { return t.Ticker.C }

type Changer interface {
	ChangeVisibility(ctx context.Context, msg *consumer.Message, seconds int32) error
}

type Controller struct {
	ctx            context.Context
	changer        Changer
	msg            *consumer.Message
	log            *slog.Logger
	interval       time.Duration
	timeoutSeconds int32
	maxRetries     int
	retryBackoff   time.Duration
	newTicker      func(time.Duration) ticker
	apiTimeout     time.Duration
	onLost         func(error)

	stopCh   chan struct{}
	done     chan struct{}
	stopOnce sync.Once
}

// Start chạy heartbeat nền. Controller.Stop phải được gọi trước khi ACK hoặc
// trả message để không còn gia hạn sau khi message rời worker.
func Start(ctx context.Context, cfg Config, changer Changer, msg *consumer.Message,
	log *slog.Logger, onLost func(error)) (*Controller, error) {
	c, err := newController(ctx, cfg, changer, msg, log, onLost)
	if err != nil {
		return nil, err
	}
	go c.loop()
	return c, nil
}

func newController(ctx context.Context, cfg Config, changer Changer, msg *consumer.Message,
	log *slog.Logger, onLost func(error)) (*Controller, error) {
	if cfg.Interval <= 0 {
		return nil, fmt.Errorf("heartbeat interval phải dương, nhận %s", cfg.Interval)
	}
	if cfg.Timeout < time.Second {
		return nil, fmt.Errorf("visibility timeout phải >= 1s, nhận %s", cfg.Timeout)
	}
	if cfg.MaxRetries < 0 {
		return nil, fmt.Errorf("heartbeat max retries phải >= 0, nhận %d", cfg.MaxRetries)
	}
	if cfg.RetryBackoff < 0 {
		return nil, fmt.Errorf("heartbeat retry backoff phải >= 0, nhận %s", cfg.RetryBackoff)
	}
	if log == nil {
		log = slog.Default()
	}
	if onLost == nil {
		onLost = func(error) {}
	}
	newTicker := cfg.newTicker
	if newTicker == nil {
		newTicker = func(d time.Duration) ticker { return realTicker{time.NewTicker(d)} }
	}
	backoff := cfg.RetryBackoff
	if max := cfg.Interval / 4; backoff > max {
		backoff = max
	}
	apiTimeout := cfg.Interval / 2
	if apiTimeout > maxAPITimeout {
		apiTimeout = maxAPITimeout
	}
	if apiTimeout < 500*time.Millisecond {
		apiTimeout = 500 * time.Millisecond
	}
	return &Controller{
		ctx:            ctx,
		changer:        changer,
		msg:            msg,
		log:            log,
		interval:       cfg.Interval,
		timeoutSeconds: int32((cfg.Timeout + time.Second - 1) / time.Second),
		maxRetries:     cfg.MaxRetries,
		retryBackoff:   backoff,
		newTicker:      newTicker,
		apiTimeout:     apiTimeout,
		onLost:         onLost,
		stopCh:         make(chan struct{}),
		done:           make(chan struct{}),
	}, nil
}

func (c *Controller) loop() {
	defer close(c.done)
	t := c.newTicker(c.interval)
	defer t.Stop()
	for {
		select {
		case <-c.ctx.Done():
			return
		case <-c.stopCh:
			return
		case <-t.C():
			if !c.renew() {
				return
			}
		}
	}
}

// Stop dừng và join goroutine heartbeat; an toàn khi gọi nhiều lần.
func (c *Controller) Stop() {
	c.stopOnce.Do(func() {
		close(c.stopCh)
		<-c.done
	})
}

func (c *Controller) renew() bool {
	var lastErr error
	for attempt := 0; attempt <= c.maxRetries; attempt++ {
		if c.ctx.Err() != nil {
			return false
		}
		callCtx, cancel := context.WithTimeout(c.ctx, c.apiTimeout)
		lastErr = c.changer.ChangeVisibility(callCtx, c.msg, c.timeoutSeconds)
		cancel()
		if lastErr == nil {
			return true
		}
		// Shutdown đang diễn ra: dừng im lặng, không báo mất ownership.
		if c.ctx.Err() != nil {
			return false
		}
		if attempt < c.maxRetries && !sleepCtx(c.ctx, c.retryBackoff) {
			return false
		}
	}
	c.log.Error("heartbeat_failed", "attempt", c.msg.Attempt,
		"tries", c.maxRetries+1, "err", lastErr)
	c.onLost(fmt.Errorf("gia hạn visibility thất bại: %w", lastErr))
	return false
}

func sleepCtx(ctx context.Context, d time.Duration) bool {
	if d <= 0 {
		return ctx.Err() == nil
	}
	timer := time.NewTimer(d)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return true
	}
}

// ErrOwnershipLost được job dùng để nhận biết phải dừng mà không ghi FAILED_*.
var ErrOwnershipLost = errors.New("mất quyền sở hữu message")
