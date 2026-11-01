#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
PROJECT_ID="prj-e2e"
VIDEO_ID="vid-worker-sample"
KEY="$PROJECT_ID/$VIDEO_ID/source.mp4"
QUEUE="$ENDPOINT/000000000000/tvc-local-jobs"
WORKER_BIN="${WORKER_BIN:-$ROOT/worker/bin/worker}"
SAMPLE="$ROOT/worker/testdata/sample-10s-720p.mp4"

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=ap-southeast-1

host_aws() { command -v aws >/dev/null 2>&1 && aws --version >/dev/null 2>&1; }
CONTAINER="${LOCALSTACK_CONTAINER:-tvc-localstack}"
if host_aws; then
  aws_() { aws --endpoint-url "$ENDPOINT" "$@"; }
else
  aws_() { docker exec -i "$CONTAINER" awslocal "$@"; }
fi

aws_ s3api head-bucket --bucket tvc-local-input >/dev/null
if host_aws; then
  aws --endpoint-url "$ENDPOINT" s3 cp "$SAMPLE" "s3://tvc-local-input/$KEY" --only-show-errors
else
  docker cp "$SAMPLE" "$CONTAINER:/tmp/tvc-sample.mp4"
  docker exec "$CONTAINER" awslocal s3 cp /tmp/tvc-sample.mp4 "s3://tvc-local-input/$KEY" --only-show-errors
  docker exec "$CONTAINER" rm -f /tmp/tvc-sample.mp4
fi

NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
aws_ dynamodb put-item --table-name tvc-local-videos --item "{
  \"videoId\":{\"S\":\"$VIDEO_ID\"},
  \"projectId\":{\"S\":\"$PROJECT_ID\"},
  \"status\":{\"S\":\"PENDING\"},
  \"createdAt\":{\"S\":\"$NOW\"},
  \"updatedAt\":{\"S\":\"$NOW\"}
}"

JOB_ID="job-e2e-$(date +%s)"
aws_ sqs send-message --queue-url "$QUEUE" --message-body "{
  \"schemaVersion\":1,
  \"jobId\":\"$JOB_ID\",
  \"videoId\":\"$VIDEO_ID\",
  \"projectId\":\"$PROJECT_ID\",
  \"inputKey\":\"$KEY\",
  \"requestedAt\":\"$NOW\"
}" >/dev/null

if [ ! -x "$WORKER_BIN" ]; then
  (cd "$ROOT/worker" && go build -o bin/worker ./cmd/worker)
fi

mkdir -p "$ROOT/frontend/.nginx"
TVC_INPUT_BUCKET=tvc-local-input \
TVC_OUTPUT_BUCKET=tvc-local-output \
TVC_DDB_VIDEOS=tvc-local-videos \
TVC_JOBS_QUEUE_URL="$QUEUE" \
AWS_ENDPOINT_URL="$ENDPOINT" \
AWS_REGION=ap-southeast-1 \
AWS_ACCESS_KEY_ID=test \
AWS_SECRET_ACCESS_KEY=test \
TVC_TMP_DIR="$(mktemp -d)" \
  "$WORKER_BIN" >"$ROOT/frontend/.nginx/worker-media.log" 2>&1 &
WORKER_PID=$!
trap 'kill "$WORKER_PID" 2>/dev/null || true' EXIT

STATUS=""
for _ in $(seq 1 60); do
  STATUS="$(aws_ dynamodb get-item --table-name tvc-local-videos \
    --key "{\"videoId\":{\"S\":\"$VIDEO_ID\"}}" --query 'Item.status.S' --output text)"
  case "$STATUS" in
    COMPLETED) break ;;
    FAILED_*) echo "worker failed: $STATUS" >&2; exit 1 ;;
  esac
  sleep 2
done

if [ "$STATUS" != "COMPLETED" ]; then
  echo "timed out waiting for COMPLETED (last status: ${STATUS:-none})" >&2
  exit 1
fi

for suffix in hls/master.m3u8 hls/360p/index.m3u8 hls/720p/index.m3u8 thumb/default.jpg; do
  aws_ s3api head-object --bucket tvc-local-output --key "$PROJECT_ID/$VIDEO_ID/$suffix" >/dev/null
done

echo "COMPLETED: http://localhost:8000/media/$PROJECT_ID/$VIDEO_ID/hls/master.m3u8"
