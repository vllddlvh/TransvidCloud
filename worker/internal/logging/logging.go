// Package logging cung cấp logger JSON một dòng mỗi bản ghi, khớp
// docs/contracts (mục 5.5 docs-worker/5. Contact docs.md).
package logging

import (
	"log/slog"
	"os"
)

func New() *slog.Logger {
	return slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo})).
		With("service", "worker")
}
