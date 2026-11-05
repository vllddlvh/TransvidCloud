package heartbeat

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"sync"
	"testing"
	"time"

	"github.com/vllddlvh/TransvidCloud/worker/internal/consumer"
)

type fakeTicker struct{ ch chan time.Time }

func (f *fakeTicker) C() <-chan time.Time { return f.ch }
func (f *fakeTicker) Stop()               {}

type fakeChanger struct {
	mu             sync.Mutex
	timeoutSeconds []int32
	errs           []error
	onCall         func(int)
}

func (f *fakeChanger) ChangeVisibility(_ context.Context, _ *consumer.Message, seconds int32) error {
	f.mu.Lock()
	idx := len(f.timeoutSeconds)
	f.timeoutSeconds = append(f.timeoutSeconds, seconds)
	var err error
	if idx < len(f.errs) {
		err = f.errs[idx]
	}
	hook := f.onCall
	f.mu.Unlock()
	if hook != nil {
		hook(idx)
	}
	return err
}

func (f *fakeChanger) calls() []int32 {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]int32(nil), f.timeoutSeconds...)
}

func testLogger() *slog.Logger {
	return slog.New(slog.NewJSONHandler(io.Discard, nil))
}

func longTickerFactory(t *fakeTicker) func(time.Duration) ticker {
	return func(time.Duration) ticker { return t }
}

func TestStartRenewsOnTickAndStopsCleanly(t *testing.T) {
	fake := &fakeTicker{ch: make(chan time.Time, 1)}
	changer := &fakeChanger{}
	got := make(chan int32, 1)
	changer.onCall = func(int) { got <- changer.calls()[0] }

	ctrl, err := Start(context.Background(), Config{
		Interval:  time.Hour,
		Timeout:   300 * time.Second,
		newTicker: longTickerFactory(fake),
	}, changer, &consumer.Message{ReceiptHandle: "rh", Attempt: 1}, testLogger(), nil)
	if err != nil {
		t.Fatalf("Start: %v", err)
	}

	fake.ch <- time.Now()
	select {
	case seconds := <-got:
		if seconds != 300 {
			t.Errorf("visibility = %d, want 300", seconds)
		}
	case <-time.After(time.Second):
		t.Fatal("heartbeat không gọi ChangeVisibility")
	}

	// Stop phải dừng (join) và không còn gia hạn sau đó.
	ctrl.Stop()
	ctrl.Stop()
	fake.ch <- time.Now()
	time.Sleep(50 * time.Millisecond)
	if n := len(changer.calls()); n != 1 {
		t.Errorf("sau Stop còn gia hạn: %d lần", n)
	}
}

func TestStopStopsWhenContextCancelled(t *testing.T) {
	fake := &fakeTicker{ch: make(chan time.Time)}
	ctx, cancel := context.WithCancel(context.Background())
	ctrl, err := Start(ctx, Config{
		Interval:  time.Hour,
		Timeout:   300 * time.Second,
		newTicker: longTickerFactory(fake),
	}, &fakeChanger{}, &consumer.Message{}, testLogger(), nil)
	if err != nil {
		t.Fatalf("Start: %v", err)
	}
	cancel()
	done := make(chan struct{})
	go func() { ctrl.Stop(); close(done) }()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("Stop không trả sau khi context bị hủy")
	}
}

func TestRenewRetriesThenReportsOwnershipLost(t *testing.T) {
	changeErr := errors.New("sqs down")
	changer := &fakeChanger{errs: []error{changeErr, changeErr, changeErr}}
	var lost error
	ctrl, err := newController(context.Background(), Config{
		Interval:     time.Hour,
		Timeout:      300 * time.Second,
		MaxRetries:   2,
		RetryBackoff: 0,
	}, changer, &consumer.Message{Attempt: 3}, testLogger(), func(err error) { lost = err })
	if err != nil {
		t.Fatalf("newController: %v", err)
	}

	if ctrl.renew() {
		t.Fatal("renew mong thất bại")
	}
	if n := len(changer.calls()); n != 3 {
		t.Errorf("số lần gọi = %d, want 3", n)
	}
	if lost == nil || !errors.Is(lost, changeErr) {
		t.Errorf("onLost = %v, want wrap %v", lost, changeErr)
	}
}

func TestRenewSucceedsAfterRetry(t *testing.T) {
	changeErr := errors.New("transient")
	changer := &fakeChanger{errs: []error{changeErr, nil, nil}}
	called := 0
	ctrl, err := newController(context.Background(), Config{
		Interval:     time.Hour,
		Timeout:      300 * time.Second,
		MaxRetries:   2,
		RetryBackoff: 0,
	}, changer, &consumer.Message{}, testLogger(), func(error) { called++ })
	if err != nil {
		t.Fatalf("newController: %v", err)
	}

	if !ctrl.renew() {
		t.Fatal("renew mong thành công sau retry")
	}
	if n := len(changer.calls()); n != 2 {
		t.Errorf("số lần gọi = %d, want 2", n)
	}
	if called != 0 {
		t.Errorf("không mong onLost, nhận %d lần", called)
	}
}

func TestRenewDoesNotReportOwnershipLostOnShutdown(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	changeErr := errors.New("network")
	changer := &fakeChanger{errs: []error{changeErr, changeErr, changeErr}}
	changer.onCall = func(int) { cancel() }

	lost := false
	ctrl, err := newController(ctx, Config{
		Interval:     time.Hour,
		Timeout:      300 * time.Second,
		MaxRetries:   2,
		RetryBackoff: 0,
	}, changer, &consumer.Message{}, testLogger(), func(error) { lost = true })
	if err != nil {
		t.Fatalf("newController: %v", err)
	}

	if ctrl.renew() {
		t.Fatal("renew mong thất bại")
	}
	if lost {
		t.Error("shutdown không được báo ownership lost")
	}
	if n := len(changer.calls()); n != 1 {
		t.Errorf("sau cancel không được retry, số lần gọi = %d", n)
	}
}

func TestNewControllerValidation(t *testing.T) {
	tests := []struct {
		name string
		cfg  Config
	}{
		{"interval 0", Config{Interval: 0, Timeout: 300 * time.Second}},
		{"timeout < 1s", Config{Interval: time.Minute, Timeout: 500 * time.Millisecond}},
		{"retries âm", Config{Interval: time.Minute, Timeout: 300 * time.Second, MaxRetries: -1}},
		{"backoff âm", Config{Interval: time.Minute, Timeout: 300 * time.Second, RetryBackoff: -time.Second}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if _, err := newController(context.Background(), tt.cfg, &fakeChanger{}, &consumer.Message{}, testLogger(), nil); err == nil {
				t.Fatal("mong lỗi cấu hình")
			}
		})
	}
}

func TestNewControllerRoundsTimeoutUp(t *testing.T) {
	ctrl, err := newController(context.Background(), Config{
		Interval: time.Minute,
		Timeout:  1500 * time.Millisecond,
	}, &fakeChanger{}, &consumer.Message{}, testLogger(), nil)
	if err != nil {
		t.Fatalf("newController: %v", err)
	}
	if ctrl.timeoutSeconds != 2 {
		t.Errorf("timeoutSeconds = %d, want 2", ctrl.timeoutSeconds)
	}
}
