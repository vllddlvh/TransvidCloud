package main

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/vllddlvh/TransvidCloud/worker/internal/consumer"
	"github.com/vllddlvh/TransvidCloud/worker/internal/heartbeat"
)

type releaseCall struct {
	msg     *consumer.Message
	seconds int32
	hasDL   bool
}

type fakeReceiver struct {
	releaseCall *releaseCall
	err         error
}

func (f *fakeReceiver) Receive(context.Context) (*consumer.Message, error) { return nil, nil }
func (f *fakeReceiver) Delete(context.Context, *consumer.Message) error    { return nil }
func (f *fakeReceiver) ChangeVisibility(ctx context.Context, msg *consumer.Message, seconds int32) error {
	_, hasDL := ctx.Deadline()
	f.releaseCall = &releaseCall{msg: msg, seconds: seconds, hasDL: hasDL}
	return f.err
}

func TestReleaseMessage(t *testing.T) {
	msg := &consumer.Message{Body: "{}", ReceiptHandle: "rh-1"}
	rec := &fakeReceiver{}
	if err := releaseMessage(rec, msg); err != nil {
		t.Fatalf("releaseMessage: %v", err)
	}
	call := rec.releaseCall
	if call == nil {
		t.Fatal("không gọi ChangeVisibility")
	}
	if call.msg != msg {
		t.Errorf("message = %v, want con trỏ gốc", call.msg)
	}
	if call.seconds != heartbeat.ReleaseVisibilitySeconds {
		t.Errorf("seconds = %d, want %d", call.seconds, heartbeat.ReleaseVisibilitySeconds)
	}
	if !call.hasDL {
		t.Error("release phải có deadline để không treo khi SQS không phản hồi")
	}
}

func TestReleaseMessagePropagatesError(t *testing.T) {
	wantErr := errors.New("sqs down")
	rec := &fakeReceiver{err: wantErr}
	if err := releaseMessage(rec, &consumer.Message{ReceiptHandle: "rh-2"}); !errors.Is(err, wantErr) {
		t.Fatalf("err = %v, want %v", err, wantErr)
	}
}

func TestVisibilitySeconds(t *testing.T) {
	tests := []struct {
		in   time.Duration
		want int32
	}{
		{300 * time.Second, 300},
		{90 * time.Second, 90},
		{1500 * time.Millisecond, 2},
		{500 * time.Millisecond, 1},
		{0, 1},
	}
	for _, tt := range tests {
		if got := visibilitySeconds(tt.in); got != tt.want {
			t.Errorf("visibilitySeconds(%v) = %d, want %d", tt.in, got, tt.want)
		}
	}
}
