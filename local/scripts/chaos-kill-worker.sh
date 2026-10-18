#!/usr/bin/env bash
# TODO (Vũ): kịch bản
#   1. gửi 1 job (video dài ~30s)
#   2. docker compose kill worker khi job đang PROCESSING
#   3. start lại worker; sau visibility timeout job được nhận lại và COMPLETED
#   4. gửi message lỗi cố ý, xác nhận sau 3 lần nhận thì vào DLQ
echo "chaos-kill-worker.sh: chưa triển khai (epic A5/A8)."
