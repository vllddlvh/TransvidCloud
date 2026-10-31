// Package shutdown bắt SIGTERM/SIGINT để dừng vòng nhận message gọn gàng.
// (Chiến lược "trả message ngay" khi có job đang chạy thuộc Sprint 2.)
package shutdown

import (
	"context"
	"os/signal"
	"syscall"
)

func Notify(ctx context.Context) (context.Context, context.CancelFunc) {
	return signal.NotifyContext(ctx, syscall.SIGTERM, syscall.SIGINT)
}
