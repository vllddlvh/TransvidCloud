#!/usr/bin/env bash
# Chaos local cho worker (epic A5): SIGTERM giữa transcode, SIGKILL giữa transcode,
# và DLQ sau 3 lần nhận. Đây là "worker integration": tự tạo queue/video/message
# riêng, không đi qua API upload/confirm (A4).
#
# Điều kiện: LocalStack đang chạy (`make infra`), host có go/ffmpeg/ffprobe/docker.
# Script không purge queue dùng chung; mỗi lần chạy tạo queue riêng
# tvc-chaos-jobs-<runId> + DLQ riêng, và xóa chúng ở cuối (trừ khi
# KEEP_CHAOS_RESOURCES=1). Bằng chứng nằm ở local/chaos-logs/<UTC>/.
#
# Biến tùy chỉnh:
#   TVC_CHAOS_VISIBILITY=10s  TVC_CHAOS_HEARTBEAT=3s
#   TVC_CHAOS_FFMPEG_DELAY=20 # giây làm chậm có kiểm soát trước khi chạy ffmpeg thật
#   KEEP_CHAOS_RESOURCES=1    # giữ queue/object/item để xem lại
#
# Exit != 0 khi timeout hoặc assertion sai.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RUN_ID="$(date +%s)-$$"
RUN_DIR="$ROOT/local/chaos-logs/$STAMP"
SUMMARY="$RUN_DIR/summary.txt"
MESSAGES="$RUN_DIR/messages.jsonl"

REGION="ap-southeast-1"
ENDPOINT="http://localhost:4566"
ACCOUNT="000000000000"
INPUT_BUCKET="tvc-local-input"
OUTPUT_BUCKET="tvc-local-output"
VIDEOS_TABLE="tvc-local-videos"
QUEUE_NAME=""
DLQ_NAME=""
QUEUE_URL=""
DLQ_URL=""
DLQ_ARN=""

VISIBILITY="${TVC_CHAOS_VISIBILITY:-10s}"
HEARTBEAT="${TVC_CHAOS_HEARTBEAT:-3s}"
FFMPEG_DELAY="${TVC_CHAOS_FFMPEG_DELAY:-20}"
PROJECT="prj-chaos-$RUN_ID"
SAMPLE="$ROOT/worker/testdata/sample-10s-720p.mp4"
WORKER_BIN="$RUN_DIR/worker"
CHAOS_BIN="$RUN_DIR/bin"
REAL_FFMPEG=""

mkdir -p "$RUN_DIR" "$CHAOS_BIN"
: > "$MESSAGES"
: > "$RUN_DIR/videos.txt"

record() {
  printf '%s\n' "$*" >> "$SUMMARY"
  printf '  %s\n' "$*"
}

fail() {
  printf 'FAIL: %s\n' "$*" | tee -a "$SUMMARY" >&2
  exit 1
}

aws_() {
  (cd "$ROOT" && docker compose exec -T localstack awslocal "$@")
}

wait_exit() { # wait_exit <pid> <timeout_s>
  local pid="$1" timeout="$2" start=$SECONDS
  while kill -0 "$pid" 2>/dev/null; do
    if [ $((SECONDS - start)) -ge "$timeout" ]; then
      return 1
    fi
    sleep 0.2
  done
  return 0
}

reap_worker() { # reap_worker <name> <pid> <expect_code>
  local name="$1" pid="$2" expect="$3" code=0
  if ! wait_exit "$pid" 60; then
    kill -KILL "$pid" 2>/dev/null || true
    fail "worker $name không thoát trong 60s"
  fi
  if wait "$pid" 2>/dev/null; then code=0; else code=$?; fi
  printf '%s\n' "$code" > "$RUN_DIR/worker-$name.exit"
  record "worker $name exit=$code (mong $expect)"
  [ "$code" -eq "$expect" ] || fail "worker $name exit $code (mong $expect)"
}

stop_worker() { # stop_worker <name> <pid> [expect_code]
  local name="$1" pid="$2" expect="${3:-0}"
  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM "$pid"
  fi
  reap_worker "$name" "$pid" "$expect"
}

start_worker() { # start_worker <name> <visibility> <heartbeat> <ffmpeg_delay> <tmp_dir>
  local name="$1" visibility="$2" heartbeat="$3" delay="$4" tmp="$5"
  local log="$RUN_DIR/worker-$name.log"
  : > "$log"
  (
    cd "$ROOT/worker"
    exec env \
      AWS_REGION="$REGION" \
      AWS_ENDPOINT_URL="$ENDPOINT" \
      AWS_ACCESS_KEY_ID=test \
      AWS_SECRET_ACCESS_KEY=test \
      TVC_INPUT_BUCKET="$INPUT_BUCKET" \
      TVC_OUTPUT_BUCKET="$OUTPUT_BUCKET" \
      TVC_JOBS_QUEUE_URL="$QUEUE_URL" \
      TVC_DDB_VIDEOS="$VIDEOS_TABLE" \
      TVC_TMP_DIR="$tmp" \
      TVC_HEARTBEAT_INTERVAL="$heartbeat" \
      TVC_VISIBILITY_TIMEOUT="$visibility" \
      TVC_FFMPEG_TIMEOUT=2m \
      PATH="$CHAOS_BIN:$PATH" \
      TVC_CHAOS_FFMPEG_DELAY="$delay" \
      TVC_CHAOS_REAL_FFMPEG="$REAL_FFMPEG" \
      "$WORKER_BIN" >>"$log" 2>&1
  ) &
  local pid=$!
  printf '%s\n' "$pid" > "$RUN_DIR/worker-$name.pid"
  WORKER_PID="$pid"
  WORKER_LOG="$log"
  WORKER_TMP="$tmp"
  record "worker $name pid=$pid visibility=$visibility heartbeat=$heartbeat ffmpegDelay=${delay}s log=$(basename "$log")"
}

job_line() { # job_line <file> <jobId> <msg> — dòng khớp gần nhất
  grep -F "\"jobId\":\"$2\"" "$1" 2>/dev/null | grep -F "\"msg\":\"$3\"" | tail -n 1
}

wait_for_job_line() { # wait_for_job_line <file> <jobId> <msg> <timeout_s>
  local file="$1" job="$2" msg="$3" timeout="$4" start=$SECONDS
  while [ $((SECONDS - start)) -lt "$timeout" ]; do
    if [ -f "$file" ] && job_line "$file" "$job" "$msg" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.2
  done
  fail "timeout ${timeout}s: job $job không có msg=$msg trong $file"
}

count_job_msg() { # count_job_msg <file> <jobId> <msg>
  grep -F "\"jobId\":\"$2\"" "$1" 2>/dev/null | grep -Fc -- "\"msg\":\"$3\"" || true
}

assert_attempt() { # assert_attempt <file> <jobId> <expected>
  local line
  line="$(job_line "$1" "$2" job_received || true)"
  [ -n "$line" ] || fail "không có job_received cho $2"
  printf '%s' "$line" | grep -Fq "\"attempt\":$3" \
    || fail "job_received mong attempt=$3: $line"
}

get_status() {
  aws_ dynamodb get-item --table-name "$VIDEOS_TABLE" \
    --key "{\"videoId\":{\"S\":\"$1\"}}" --query 'Item.status.S' --output text
}

get_error_code() {
  aws_ dynamodb get-item --table-name "$VIDEOS_TABLE" \
    --key "{\"videoId\":{\"S\":\"$1\"}}" --query 'Item.errorCode.S' --output text
}

wait_status() { # wait_status <videoId> <status> <timeout_s>
  local video="$1" want="$2" timeout="$3" start=$SECONDS status
  while [ $((SECONDS - start)) -lt "$timeout" ]; do
    status="$(get_status "$video" 2>/dev/null || true)"
    if [ "$status" = "$want" ]; then return 0; fi
    sleep 0.5
  done
  fail "timeout ${timeout}s: video $video status=$status (mong $want)"
}

queue_counts() { # in ra: <visible> <notVisible>
  aws_ sqs get-queue-attributes --queue-url "$QUEUE_URL" \
    --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible \
    --query '[Attributes.ApproximateNumberOfMessages,Attributes.ApproximateNumberOfMessagesNotVisible]' \
    --output text
}

seed_video() { # seed_video <videoId> <projectId> <inputKey>
  local now item
  now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  item="$(printf '{"videoId":{"S":"%s"},"projectId":{"S":"%s"},"status":{"S":"PENDING"},"createdAt":{"S":"%s"},"updatedAt":{"S":"%s"},"originalKey":{"S":"%s"}}' \
    "$1" "$2" "$now" "$now" "$3")"
  aws_ dynamodb put-item --table-name "$VIDEOS_TABLE" --item "$item"
  record "seed video=$1 project=$2 status=PENDING"
}

upload_sample() { # upload_sample <key>
  aws_ s3 cp - "s3://$INPUT_BUCKET/$1" --content-type video/mp4 < "$SAMPLE"
  record "upload inputKey=$1 ($(wc -c < "$SAMPLE" | tr -d ' ') bytes)"
}

send_job() { # send_job <jobId> <videoId> <projectId> <inputKey>
  local now body
  now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  body="$(printf '{"schemaVersion":1,"jobId":"%s","videoId":"%s","projectId":"%s","inputKey":"%s","requestedAt":"%s"}' \
    "$1" "$2" "$3" "$4" "$now")"
  printf '%s\n' "$body" >> "$MESSAGES"
  aws_ sqs send-message --queue-url "$QUEUE_URL" --message-body "$body" >/dev/null
  record "send jobId=$1 videoId=$2 inputKey=$4"
}

assert_assets() { # assert_assets <projectId> <videoId>
  local prefix="$1/$2" key ct master pl seg assets
  local -a files
  files=("hls/master.m3u8|application/vnd.apple.mpegurl" \
         "hls/360p/index.m3u8|application/vnd.apple.mpegurl" \
         "hls/720p/index.m3u8|application/vnd.apple.mpegurl" \
         "thumb/default.jpg|image/jpeg")
  local entry
  for entry in "${files[@]}"; do
    key="${entry%%|*}"
    ct="${entry#*|}"
    if ! ct="$(aws_ s3api head-object --bucket "$OUTPUT_BUCKET" --key "$prefix/$key" \
        --query ContentType --output text 2>/dev/null)"; then
      fail "thiếu output s3://$OUTPUT_BUCKET/$prefix/$key"
    fi
    [ "$ct" = "${entry#*|}" ] || fail "$key Content-Type=$ct (mong ${entry#*|})"
    record "asset $key Content-Type=$ct"
  done

  master="$(aws_ s3 cp "s3://$OUTPUT_BUCKET/$prefix/hls/master.m3u8" - --only-show-errors)"
  grep -Fq '360p/index.m3u8' <<<"$master" || fail "master thiếu 360p"
  grep -Fq '720p/index.m3u8' <<<"$master" || fail "master thiếu 720p"
  pl="$(aws_ s3 cp "s3://$OUTPUT_BUCKET/$prefix/hls/360p/index.m3u8" - --only-show-errors)"
  grep -Fq '#EXTM3U' <<<"$pl" || fail "360p playlist không hợp lệ"
  seg="$(grep -E '\.ts$' <<<"$pl" | head -n 1 || true)"
  [ -n "$seg" ] || fail "360p playlist không tham chiếu segment .ts"
  aws_ s3api head-object --bucket "$OUTPUT_BUCKET" --key "$prefix/hls/360p/$seg" \
    --query ContentLength --output text >/dev/null || fail "thiếu segment $seg"
  record "playback: master -> 360p/720p, segment $seg tồn tại"

  assets="$(aws_ dynamodb get-item --table-name "$VIDEOS_TABLE" \
    --key "{\"videoId\":{\"S\":\"$2\"}}" --query 'Item.assets.M' --output json)"
  for key in hls hls360 hls720 thumbnail; do
    grep -Fq "\"$key\"" <<<"$assets" || fail "assets thiếu $key"
  done
  record "assets DynamoDB đủ hls/hls360/hls720/thumbnail"
}

assert_temp_clean() { # assert_temp_clean <tmp_dir>
  local leftover
  leftover="$(find "$1" -mindepth 1 -maxdepth 1 ! -name '.tvc-worker-tmp' -print -quit 2>/dev/null || true)"
  [ -z "$leftover" ] || fail "thư mục tạm còn sót: $leftover"
  record "temp sạch: $1"
}

cleanup() {
  local code=$?
  set +e
  local pidfile pid
  for pidfile in "$RUN_DIR"/*.pid; do
    [ -e "$pidfile" ] || continue
    pid="$(cat "$pidfile" 2>/dev/null || true)"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      kill -TERM "$pid" 2>/dev/null
      wait_exit "$pid" 20 || kill -KILL "$pid" 2>/dev/null
    fi
  done

  if [ "$code" -ne 0 ]; then
    {
      echo "--- chẩn đoán: 20 dòng cuối mỗi worker log ---"
      local f
      for f in "$RUN_DIR"/worker-*.log; do
        [ -f "$f" ] || continue
        echo "### $f"
        tail -n 20 "$f"
      done
    } >> "$SUMMARY"
  fi

  if [ "${KEEP_CHAOS_RESOURCES:-0}" != "1" ]; then
    local v
    while read -r v; do
      [ -n "$v" ] || continue
      aws_ dynamodb delete-item --table-name "$VIDEOS_TABLE" \
        --key "{\"videoId\":{\"S\":\"$v\"}}" >/dev/null 2>&1
    done < "$RUN_DIR/videos.txt"
    aws_ s3 rm "s3://$INPUT_BUCKET/$PROJECT/" --recursive >/dev/null 2>&1
    aws_ s3 rm "s3://$OUTPUT_BUCKET/$PROJECT/" --recursive >/dev/null 2>&1
    local q d
    if [ -f "$RUN_DIR/queues.txt" ]; then
      while read -r q d; do
        [ -n "$q" ] || continue
        aws_ sqs delete-queue --queue-url "$q" >/dev/null 2>&1
        [ -n "$d" ] || continue
        aws_ sqs delete-queue --queue-url "$d" >/dev/null 2>&1
      done < "$RUN_DIR/queues.txt"
    fi
    record "cleanup: đã xóa queue/object/item của lần chạy"
  else
    record "cleanup: giữ tài nguyên vì KEEP_CHAOS_RESOURCES=1"
  fi

  rm -f "$WORKER_BIN"
  if [ "$code" -eq 0 ]; then
    printf 'PASS — bằng chứng: %s\n' "$RUN_DIR" | tee -a "$SUMMARY"
  else
    printf 'FAILED (exit=%s) — bằng chứng: %s\n' "$code" "$RUN_DIR" | tee -a "$SUMMARY" >&2
  fi
  exit "$code"
}
trap cleanup EXIT

{
  echo "# Chaos worker — $STAMP"
  echo "runId=$RUN_ID"
  echo "git=$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  if [ -n "$(git -C "$ROOT" status --porcelain 2>/dev/null || true)" ]; then
    echo "gitDirty=yes"
  else
    echo "gitDirty=no"
  fi
  echo "go=$(go version 2>/dev/null || echo missing)"
  echo "ffmpeg=$(ffmpeg -version 2>/dev/null | head -n 1 || echo missing)"
  echo "localstack=$(cd "$ROOT" && docker compose config --images 2>/dev/null | grep localstack | head -n 1 || echo unknown)"
  echo "queue: mỗi ca một queue + DLQ riêng, maxReceiveCount=3"
  echo "sigterm/sigkill: visibility=$VISIBILITY heartbeat=$HEARTBEAT heartbeatDelay=${FFMPEG_DELAY}s"
  echo "dlq: visibility=2s heartbeat=1s"
} > "$SUMMARY"

for bin in go ffmpeg ffprobe docker; do
  command -v "$bin" >/dev/null 2>&1 || fail "thiếu $bin trong PATH"
done
[ -f "$SAMPLE" ] || fail "thiếu video mẫu $SAMPLE"
REAL_FFMPEG="$(command -v ffmpeg)"

# ffmpeg wrapper: làm chậm có kiểm soát riêng lệnh HLS (có -filter_complex),
# không đổi lệnh/encoding thật. Nếu worker cha chết (SIGKILL) thì thoát, tránh
# ffmpeg mồ côi chạy tiếp trong thư mục đã bị dọn.
cat > "$CHAOS_BIN/ffmpeg" <<'WRAPPER'
#!/usr/bin/env bash
set -eu
delay="${TVC_CHAOS_FFMPEG_DELAY:-0}"
real="${TVC_CHAOS_REAL_FFMPEG:?thiếu TVC_CHAOS_REAL_FFMPEG}"
for arg in "$@"; do
  if [ "$arg" = "-filter_complex" ] && [ "$delay" -gt 0 ]; then
    end=$((SECONDS + delay))
    while [ "$SECONDS" -lt "$end" ]; do
      kill -0 "$PPID" 2>/dev/null || exit 130
      sleep 1
    done
    break
  fi
done
exec "$real" "$@"
WRAPPER
chmod +x "$CHAOS_BIN/ffmpeg"

record "==> build worker"
(cd "$ROOT/worker" && go build -o "$WORKER_BIN" ./cmd/worker)

record "==> kiểm tra LocalStack"
aws_ sqs list-queues >/dev/null 2>&1 \
  || fail "LocalStack chưa sẵn sàng — chạy 'make infra' trước"

# Mỗi ca dùng queue riêng: worker cũ vừa dừng có thể còn giữ long-poll trên
# LocalStack và nhận mất message của ca kế tiếp.
setup_queue() { # setup_queue <suffix>
  QUEUE_NAME="tvc-chaos-$1-$RUN_ID"
  DLQ_NAME="tvc-chaos-$1-dlq-$RUN_ID"
  QUEUE_URL="http://localhost:4566/$ACCOUNT/$QUEUE_NAME"
  DLQ_URL="http://localhost:4566/$ACCOUNT/$DLQ_NAME"
  DLQ_ARN="arn:aws:sqs:$REGION:$ACCOUNT:$DLQ_NAME"
  record "==> tạo queue riêng $QUEUE_NAME + DLQ (maxReceiveCount=3)"
  aws_ sqs create-queue --queue-name "$DLQ_NAME" >/dev/null
  local attrs redrive
  attrs="$(cat <<JSON
{"VisibilityTimeout":"300","ReceiveMessageWaitTimeSeconds":"20","RedrivePolicy":"{\"deadLetterTargetArn\":\"$DLQ_ARN\",\"maxReceiveCount\":\"3\"}"}
JSON
)"
  aws_ sqs create-queue --queue-name "$QUEUE_NAME" --attributes "$attrs" >/dev/null
  printf '%s %s\n' "$QUEUE_URL" "$DLQ_URL" >> "$RUN_DIR/queues.txt"
  redrive="$(aws_ sqs get-queue-attributes --queue-url "$QUEUE_URL" --attribute-names RedrivePolicy \
    --query 'Attributes.RedrivePolicy' --output text)"
  record "redrivePolicy=$redrive"
}

scenario_sigterm() {
  echo "==> Ca 1/3: SIGTERM giữa transcode"
  setup_queue sigterm
  JOB="job-sigterm-$RUN_ID"
  VIDEO="vid-sigterm-$RUN_ID"
  KEY="$PROJECT/$VIDEO/source.mp4"
  TMP="$RUN_DIR/tmp-sigterm"
  echo "$VIDEO" >> "$RUN_DIR/videos.txt"
  seed_video "$VIDEO" "$PROJECT" "$KEY"
  upload_sample "$KEY"
  send_job "$JOB" "$VIDEO" "$PROJECT" "$KEY"

  start_worker sigterm "$VISIBILITY" "$HEARTBEAT" "$FFMPEG_DELAY" "$TMP"
  local pid="$WORKER_PID" log="$WORKER_LOG" visible not_visible elapsed status
  wait_for_job_line "$log" "$JOB" transcode_started 120
  record "sigterm: transcode_started (job=$JOB)"

  # visibility ban đầu 10s; ngủ 12s mà message vẫn invisible => heartbeat đã gia hạn.
  sleep 12
  read -r visible not_visible < <(queue_counts)
  record "sigterm: sau 12s (visibility=$VISIBILITY): visible=$visible notVisible=$not_visible"
  [ "$not_visible" = "1" ] || fail "heartbeat không giữ message: visible=$visible notVisible=$not_visible"
  [ "$visible" = "0" ] || fail "message hiện trong queue khi job còn chạy: visible=$visible"

  record "sigterm: gửi SIGTERM lúc $(date -u +%H:%M:%SZ)"
  stop_worker sigterm "$pid" 0
  grep -Fq '"msg":"message_released"' "$log" \
    || fail "không thấy message_released (visibility 30s) sau SIGTERM"
  grep -Fq '"visibilitySeconds":30' "$log" \
    || fail "message_released không có visibilitySeconds=30"
  if job_line "$log" "$JOB" job_finished >/dev/null 2>&1; then
    fail "worker vẫn ACK/hoàn tất job dở sau SIGTERM"
  fi
  status="$(get_status "$VIDEO")"
  [ "$status" = "PROCESSING" ] || fail "status=$status sau SIGTERM (mong PROCESSING, không FAILED_*)"
  record "sigterm: status=$status, không ACK job dở"

  start_worker sigterm-redelivery "$VISIBILITY" "$HEARTBEAT" 0 "$TMP"
  local pid2="$WORKER_PID" log2="$WORKER_LOG" start=$SECONDS
  wait_for_job_line "$log2" "$JOB" job_received 120
  elapsed=$((SECONDS - start))
  record "sigterm: worker khác nhận lại sau ${elapsed}s (release visibility 30s)"
  [ "$elapsed" -ge 20 ] || fail "nhận lại quá sớm (${elapsed}s): release 30s có vẻ không có hiệu lực"
  assert_attempt "$log2" "$JOB" 2
  wait_for_job_line "$log2" "$JOB" job_finished 180
  wait_status "$VIDEO" "COMPLETED" 60
  assert_assets "$PROJECT" "$VIDEO"
  assert_temp_clean "$TMP"
  stop_worker sigterm-redelivery "$pid2" 0
  record "sigterm: PASS"
}

scenario_sigkill() {
  echo "==> Ca 2/3: SIGKILL giữa transcode"
  setup_queue sigkill
  JOB="job-sigkill-$RUN_ID"
  VIDEO="vid-sigkill-$RUN_ID"
  KEY="$PROJECT/$VIDEO/source.mp4"
  TMP="$RUN_DIR/tmp-sigkill"
  echo "$VIDEO" >> "$RUN_DIR/videos.txt"
  seed_video "$VIDEO" "$PROJECT" "$KEY"
  upload_sample "$KEY"
  send_job "$JOB" "$VIDEO" "$PROJECT" "$KEY"

  start_worker sigkill "$VISIBILITY" "$HEARTBEAT" "$FFMPEG_DELAY" "$TMP"
  local pid="$WORKER_PID" log="$WORKER_LOG" start elapsed status
  wait_for_job_line "$log" "$JOB" transcode_started 120
  sleep 4 # để có ít nhất một lần heartbeat trước khi kill
  record "sigkill: kill -9 lúc $(date -u +%H:%M:%SZ)"
  kill -KILL "$pid"
  reap_worker sigkill "$pid" 137
  [ -d "$TMP/$JOB" ] || fail "mong thư mục tạm mồ côi $TMP/$JOB sau SIGKILL"
  if job_line "$log" "$JOB" message_released >/dev/null 2>&1 \
    || grep -Fq '"msg":"message_released"' "$log"; then
    fail "SIGKILL không thể release message"
  fi
  record "sigkill: thư mục tạm mồ côi tồn tại, không có release/ACK"

  # Worker mới dùng lại đúng TVC_TMP_DIR: Prepare phải dọn job dir mồ côi.
  start_worker sigkill-redelivery "$VISIBILITY" "$HEARTBEAT" 0 "$TMP"
  local pid2="$WORKER_PID" log2="$WORKER_LOG"
  sleep 1
  [ ! -d "$TMP/$JOB" ] || fail "restart không dọn thư mục mồ côi $TMP/$JOB"
  record "sigkill: restart đã dọn thư mục mồ côi của $JOB"

  start=$SECONDS
  wait_for_job_line "$log2" "$JOB" job_received 120
  elapsed=$((SECONDS - start))
  record "sigkill: nhận lại sau ${elapsed}s kể từ restart (chờ visibility lần gia hạn cuối)"
  assert_attempt "$log2" "$JOB" 2
  wait_for_job_line "$log2" "$JOB" job_finished 180
  wait_status "$VIDEO" "COMPLETED" 60
  assert_assets "$PROJECT" "$VIDEO"
  assert_temp_clean "$TMP"
  stop_worker sigkill-redelivery "$pid2" 0
  record "sigkill: PASS"
}

scenario_dlq() {
  echo "==> Ca 3/3: DLQ sau 3 lần nhận (input không tồn tại)"
  setup_queue dlq
  JOB="job-dlq-$RUN_ID"
  VIDEO="vid-dlq-$RUN_ID"
  KEY="$PROJECT/$VIDEO/source.mp4" # cố ý không upload
  TMP="$RUN_DIR/tmp-dlq"
  echo "$VIDEO" >> "$RUN_DIR/videos.txt"
  seed_video "$VIDEO" "$PROJECT" "$KEY"
  send_job "$JOB" "$VIDEO" "$PROJECT" "$KEY"

  start_worker dlq 2s 1s 0 "$TMP"
  local pid="$WORKER_PID" log="$WORKER_LOG" status code count out dlq_body dlq_count start
  wait_status "$VIDEO" "FAILED_STORAGE" 60
  code="$(get_error_code "$VIDEO")"
  [ "$code" = "S3_GET_FAILED" ] || fail "errorCode=$code (mong S3_GET_FAILED)"
  record "dlq: lần lỗi đầu -> FAILED_STORAGE/S3_GET_FAILED"

  start=$SECONDS
  while [ $((SECONDS - start)) -lt 90 ]; do
    count="$(count_job_msg "$log" "$JOB" job_received)"
    if [ "$count" -ge 3 ]; then break; fi
    sleep 0.5
  done
  count="$(count_job_msg "$log" "$JOB" job_received)"
  [ "$count" -eq 3 ] || fail "job_received=$count (mong 3 lần nhận)"
  record "dlq: worker đã nhận 3 lần, vẫn giữ message (không ACK)"

  dlq_body=""
  dlq_count=""
  start=$SECONDS
  while [ $((SECONDS - start)) -lt 60 ]; do
    out="$(aws_ sqs receive-message --queue-url "$DLQ_URL" --max-number-of-messages 1 \
      --wait-time-seconds 1 --message-system-attribute-names ApproximateReceiveCount \
      --query 'Messages[0].[Body,Attributes.ApproximateReceiveCount]' --output text 2>/dev/null || true)"
    case "$out" in
      \{*)
        dlq_body="${out%%$'\t'*}"
        dlq_count="${out#*$'\t'}"
        break
        ;;
    esac
  done
  [ -n "$dlq_body" ] || fail "không thấy message trong DLQ sau 60s"
  grep -Fq "\"jobId\":\"$JOB\"" <<<"$dlq_body" || fail "message DLQ không đúng jobId $JOB"
  # SQS tăng ReceiveCount lên 4 khi vượt maxReceiveCount=3 rồi mới redrive;
  # worker chỉ nhận message 3 lần (đếm ở trên).
  [ "$dlq_count" -ge 3 ] || fail "DLQ ApproximateReceiveCount=$dlq_count (mong >=3)"
  printf 'DLQ message: %s\nDLQ ApproximateReceiveCount: %s\n' "$dlq_body" "$dlq_count" >> "$MESSAGES"
  record "dlq: SQS tự redrive message sang DLQ (receiveCount=$dlq_count, worker nhận 3 lần), worker không SendMessage"
  stop_worker dlq "$pid" 0
  assert_temp_clean "$TMP"
  record "dlq: PASS"
}

scenario_sigterm
scenario_sigkill
scenario_dlq

record "==> Tất cả kịch bản PASS"
