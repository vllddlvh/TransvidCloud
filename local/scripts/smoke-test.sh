#!/usr/bin/env bash
# Kiểm tra LocalStack đã khởi tạo đúng tài nguyên.
set -euo pipefail
aws_() { docker compose exec -T localstack awslocal "$@"; }
fail=0
check() { # check <mô tả> <chuỗi cần có> <lệnh...>
  local desc="$1" want="$2"; shift 2
  if "$@" | grep -F -- "$want" >/dev/null; then echo "  OK   $desc"; else echo "  FAIL $desc"; fail=1; fi
}
echo "==> Smoke test LocalStack"
for b in frontend input output; do check "bucket tvc-local-$b" "tvc-local-$b" aws_ s3 ls; done
check "queue tvc-local-jobs"     'tvc-local-jobs"'     aws_ sqs list-queues --output json
check "queue tvc-local-jobs-dlq" 'tvc-local-jobs-dlq"' aws_ sqs list-queues --output json
for t in users projects apikeys videos; do check "bảng tvc-local-$t" "tvc-local-$t" aws_ dynamodb list-tables; done
# TODO (e2e): upload video mẫu -> confirm -> chờ COMPLETED -> kiểm tra HLS/thumbnail
exit $fail
