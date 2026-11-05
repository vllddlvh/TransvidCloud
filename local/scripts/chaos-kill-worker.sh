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
#   TVC_CHAOS_RUNTIME=host|docker # docker: worker chạy trong image worker/Dockerfile
#   TVC_CHAOS_SCENARIOS=sigterm,sigkill,dlq,competing
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
RUNTIME="${TVC_CHAOS_RUNTIME:-host}"
WORKER_IMAGE="${TVC_CHAOS_IMAGE:-tvc-worker:chaos}"
CHAOS_IMAGE="${WORKER_IMAGE}-delay"
LOCALSTACK_CONTAINER="${TVC_LOCALSTACK_CONTAINER:-tvc-localstack}"
DOCKER_HOST_INTERNAL="host.docker.internal"
SCENARIOS="${TVC_CHAOS_SCENARIOS:-sigterm,sigkill,dlq,competing}"
VOLUMES=()
TAIL_PIDS=()
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
  if docker inspect "$LOCALSTACK_CONTAINER" >/dev/null 2>&1; then
    docker exec -i "$LOCALSTACK_CONTAINER" awslocal "$@"
  else
    (cd "$ROOT" && docker compose exec -T localstack awslocal "$@")
  fi
}

# Worker được định danh bằng "<pid>" (host) hoặc "docker:<container>" để cùng
# một kịch bản chạy được cả hai runtime.
worker_alive() {
  case "$1" in
    docker:*)
      [ "$(docker inspect -f '{{.State.Running}}' "${1#docker:}" 2>/dev/null || echo false)" = "true" ]
      ;;
    *)
      kill -0 "$1" 2>/dev/null
      ;;
  esac
}

worker_wait() { # worker_wait <ref> <timeout_s>
  local ref="$1" timeout="$2" start=$SECONDS
  while worker_alive "$ref"; do
    if [ $((SECONDS - start)) -ge "$timeout" ]; then
      return 1
    fi
    sleep 0.2
  done
  return 0
}

worker_exit_code() {
  case "$1" in
    docker:*)
      docker wait "${1#docker:}"
      ;;
    *)
      local code=0
      if wait "$1" 2>/dev/null; then code=0; else code=$?; fi
      printf '%s\n' "$code"
      ;;
  esac
}

worker_signal() { # worker_signal <ref> <TERM|KILL>
  local ref="$1" sig="$2"
  worker_alive "$ref" || return 0
  case "$ref" in
    docker:*)
      docker kill --signal="$sig" "${ref#docker:}" >/dev/null
      ;;
    *)
      kill -"$sig" "$ref"
      ;;
  esac
}

reap_worker() { # reap_worker <name> <ref> <expect_code>
  local name="$1" ref="$2" expect="$3" code=0
  if ! worker_wait "$ref" 60; then
    worker_signal "$ref" KILL
    fail "worker $name không thoát trong 60s"
  fi
  code="$(worker_exit_code "$ref")"
  printf '%s\n' "$code" > "$RUN_DIR/worker-$name.exit"
  record "worker $name exit=$code (mong $expect)"
  [ "$code" -eq "$expect" ] || fail "worker $name exit $code (mong $expect)"
}

stop_worker() { # stop_worker <name> <ref> [expect_code]
  local name="$1" ref="$2" expect="${3:-0}"
  worker_signal "$ref" TERM
  reap_worker "$name" "$ref" "$expect"
}

start_worker() { # start_worker <name> <visibility> <heartbeat> <ffmpeg_delay> <tmp_dir>
  local name="$1" visibility="$2" heartbeat="$3" delay="$4" tmp="$5"
  local log="$RUN_DIR/worker-$name.log" wq
  : > "$log"
  wq="${QUEUE_URL/localhost/$DOCKER_HOST_INTERNAL}"
  if [ "$RUNTIME" = "docker" ]; then
    local cname="tvc-chaos-$name-$RUN_ID"
    local vol
    vol="$(volume_for "$tmp")"
    docker volume create "$vol" >/dev/null
    VOLUMES+=("$vol")
    docker run -d --name "$cname" --init \
      --add-host="$DOCKER_HOST_INTERNAL:host-gateway" \
      -e AWS_REGION="$REGION" \
      -e AWS_ENDPOINT_URL="http://$DOCKER_HOST_INTERNAL:4566" \
      -e AWS_ACCESS_KEY_ID=test \
      -e AWS_SECRET_ACCESS_KEY=test \
      -e TVC_INPUT_BUCKET="$INPUT_BUCKET" \
      -e TVC_OUTPUT_BUCKET="$OUTPUT_BUCKET" \
      -e TVC_JOBS_QUEUE_URL="$wq" \
      -e TVC_DDB_VIDEOS="$VIDEOS_TABLE" \
      -e TVC_TMP_DIR=/tmp/tvc \
      -e TVC_HEARTBEAT_INTERVAL="$heartbeat" \
      -e TVC_VISIBILITY_TIMEOUT="$visibility" \
      -e TVC_FFMPEG_TIMEOUT=2m \
      -e TVC_CHAOS_FFMPEG_DELAY="$delay" \
      -e TVC_CHAOS_REAL_FFMPEG=/usr/bin/ffmpeg \
      -v "$vol:/tmp/tvc" \
      "$CHAOS_IMAGE" >/dev/null
    docker logs -f "$cname" >>"$log" 2>&1 &
    TAIL_PIDS+=("$!")
    printf '%s\n' "docker:$cname" > "$RUN_DIR/worker-$name.pid"
    WORKER_PID="docker:$cname"
    WORKER_LOG="$log"
    WORKER_TMP="$tmp"
    record "worker $name container=$cname volume=$vol visibility=$visibility heartbeat=$heartbeat ffmpegDelay=${delay}s log=$(basename "$log")"
    return
  fi
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

get_attempt_count() {
  aws_ dynamodb get-item --table-name "$VIDEOS_TABLE" \
    --key "{\"videoId\":{\"S\":\"$1\"}}" --query 'Item.attemptCount.N' --output text
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

volume_for() { # volume_for <tmp_dir>
  printf 'tvc-chaos-%s-%s\n' "$RUN_ID" "$(basename "$1")"
}

tmp_dir_exists() { # tmp_dir_exists <tmp_dir> <jobId>
  if [ "$RUNTIME" = "docker" ]; then
    docker run --rm -v "$(volume_for "$1"):/tmp/tvc" --entrypoint sh "$CHAOS_IMAGE" \
      -c "test -d /tmp/tvc/$2"
  else
    [ -d "$1/$2" ]
  fi
}

assert_temp_clean() { # assert_temp_clean <tmp_dir>
  local leftover
  if [ "$RUNTIME" = "docker" ]; then
    leftover="$(docker run --rm -v "$(volume_for "$1"):/tmp/tvc" --entrypoint sh "$CHAOS_IMAGE" -c \
      'find /tmp/tvc -mindepth 1 -maxdepth 1 ! -name .tvc-worker-tmp -print -quit' 2>/dev/null || true)"
  else
    leftover="$(find "$1" -mindepth 1 -maxdepth 1 ! -name '.tvc-worker-tmp' -print -quit 2>/dev/null || true)"
  fi
  [ -z "$leftover" ] || fail "thư mục tạm còn sót: $leftover"
  record "temp sạch: $1"
}

s3_cat() { # s3_cat <key>
  aws_ s3 cp "s3://$OUTPUT_BUCKET/$1" - --only-show-errors
}

# Tải playlist + segment đầu tiên của từng rendition về host và giải mã bằng
# ffprobe; chỉ kiểm "object tồn tại" chưa đủ chứng minh output phát được.
assert_playable() { # assert_playable <projectId> <videoId> <label>
  local prefix="$1/$2" dir="$RUN_DIR/playback-$3" master seg out height frames rendition
  mkdir -p "$dir"
  master="$(s3_cat "$prefix/hls/master.m3u8")"
  grep -Fq '360p/index.m3u8' <<<"$master" || fail "master thiếu 360p"
  grep -Fq '720p/index.m3u8' <<<"$master" || fail "master thiếu 720p"
  for rendition in 360p 720p; do
    mkdir -p "$dir/$rendition"
    s3_cat "$prefix/hls/$rendition/index.m3u8" > "$dir/$rendition/index.m3u8"
    seg="$(grep -E '\.ts$' "$dir/$rendition/index.m3u8" | head -n 1 || true)"
    [ -n "$seg" ] || fail "$rendition playlist không tham chiếu segment .ts"
    s3_cat "$prefix/hls/$rendition/$seg" > "$dir/$rendition/$seg"
    out="$(ffprobe -v error -allowed_extensions ALL -select_streams v:0 -count_frames \
      -show_entries stream=height,nb_read_frames -of csv=p=0 \
      "$dir/$rendition/index.m3u8" 2>/dev/null | head -n 1 | tr -d '\r')"
    height="${out%%,*}"
    frames="${out##*,}"
    [ "$height" = "${rendition%p}" ] || fail "ffprobe $rendition height=$height (mong ${rendition%p})"
    [ -n "$frames" ] && [ "$frames" -gt 0 ] || fail "ffprobe không giải mã được frame nào ở $rendition"
    record "playback $rendition: ffprobe height=$height frames=$frames segment=$seg"
  done
}

cleanup() {
  local code=$?
  set +e
  local f ref deadline i v
  for f in "$RUN_DIR"/*.pid; do
    [ -e "$f" ] || continue
    ref="$(cat "$f" 2>/dev/null || true)"
    [ -n "$ref" ] || continue
    if worker_alive "$ref"; then
      worker_signal "$ref" TERM
      deadline=$((SECONDS + 20))
      while worker_alive "$ref" && [ "$SECONDS" -lt "$deadline" ]; do
        sleep 0.2
      done
      worker_alive "$ref" && worker_signal "$ref" KILL
    fi
    case "$ref" in
      docker:*) docker rm -f "${ref#docker:}" >/dev/null 2>&1 ;;
    esac
  done

  # dừng docker logs -f trước khi đọc log chẩn đoán
  if [ "${#TAIL_PIDS[@]}" -gt 0 ]; then
    for i in "${TAIL_PIDS[@]}"; do kill "$i" 2>/dev/null; done
  fi

  if [ "$code" -ne 0 ]; then
    {
      echo "--- chẩn đoán: 20 dòng cuối mỗi worker log ---"
      local log
      for log in "$RUN_DIR"/worker-*.log; do
        [ -f "$log" ] || continue
        echo "### $log"
        tail -n 20 "$log"
      done
    } >> "$SUMMARY"
  fi

  if [ "${KEEP_CHAOS_RESOURCES:-0}" != "1" ]; then
    local video
    while read -r video; do
      [ -n "$video" ] || continue
      aws_ dynamodb delete-item --table-name "$VIDEOS_TABLE" \
        --key "{\"videoId\":{\"S\":\"$video\"}}" >/dev/null 2>&1 </dev/null
    done < "$RUN_DIR/videos.txt"
    aws_ s3 rm "s3://$INPUT_BUCKET/$PROJECT/" --recursive >/dev/null 2>&1
    aws_ s3 rm "s3://$OUTPUT_BUCKET/$PROJECT/" --recursive >/dev/null 2>&1
    local q d
    if [ -f "$RUN_DIR/queues.txt" ]; then
      while read -r q d; do
        [ -n "$q" ] || continue
        aws_ sqs delete-queue --queue-url "$q" >/dev/null 2>&1 </dev/null
        [ -n "$d" ] || continue
        aws_ sqs delete-queue --queue-url "$d" >/dev/null 2>&1 </dev/null
      done < "$RUN_DIR/queues.txt"
    fi
    if [ "${#VOLUMES[@]}" -gt 0 ]; then
      for v in "${VOLUMES[@]}"; do
        docker volume rm "$v" >/dev/null 2>&1
      done
    fi
    record "cleanup: đã xóa queue/object/item/volume của lần chạy"
  else
    record "cleanup: giữ tài nguyên vì KEEP_CHAOS_RESOURCES=1 (volume: ${VOLUMES[*]:-})"
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
  echo "runtime=$RUNTIME"
  echo "scenarios=$SCENARIOS"
  echo "git=$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  if [ -n "$(git -C "$ROOT" status --porcelain 2>/dev/null || true)" ]; then
    echo "gitDirty=yes"
  else
    echo "gitDirty=no"
  fi
  if [ "$RUNTIME" = "host" ]; then
    echo "go=$(go version 2>/dev/null || echo missing)"
    echo "ffmpeg=$(ffmpeg -version 2>/dev/null | head -n 1 || echo missing)"
  else
    echo "workerImage=$WORKER_IMAGE chaosImage=$CHAOS_IMAGE"
  fi
  echo "localstackContainer=$LOCALSTACK_CONTAINER image=$(docker inspect -f '{{.Config.Image}}' "$LOCALSTACK_CONTAINER" 2>/dev/null || echo unknown)"
  echo "queue: mỗi ca một queue + DLQ riêng, maxReceiveCount=3"
  echo "sigterm/sigkill: visibility=$VISIBILITY heartbeat=$HEARTBEAT ffmpegDelay=${FFMPEG_DELAY}s"
  echo "dlq: visibility=2s heartbeat=1s"
  echo "competing: 2 worker, 2 message cùng jobId, ffmpegDelay=${FFMPEG_DELAY}s"
} > "$SUMMARY"

case "$RUNTIME" in
  host|docker) ;;
  *) fail "TVC_CHAOS_RUNTIME=$RUNTIME (mong host|docker)" ;;
esac

for bin in docker ffprobe; do
  command -v "$bin" >/dev/null 2>&1 || fail "thiếu $bin trong PATH"
done
if [ "$RUNTIME" = "host" ]; then
  for bin in go ffmpeg; do
    command -v "$bin" >/dev/null 2>&1 || fail "thiếu $bin trong PATH"
  done
fi
[ -f "$SAMPLE" ] || fail "thiếu video mẫu $SAMPLE"
REAL_FFMPEG="/usr/bin/ffmpeg"
if [ "$RUNTIME" = "host" ]; then
  REAL_FFMPEG="$(command -v ffmpeg)"
fi

# ffmpeg wrapper: làm chậm có kiểm soát riêng lệnh HLS (có -filter_complex),
# không đổi lệnh/encoding thật. POSIX sh để chạy được trong image Debian
# bookworm-slim (không có bash); guard PPID chỉ có tác dụng ở host, còn trong
# container thì kernel dọn mọi process khi container chết.
cat > "$CHAOS_BIN/ffmpeg" <<'WRAPPER'
#!/bin/sh
set -eu
delay="${TVC_CHAOS_FFMPEG_DELAY:-0}"
real="${TVC_CHAOS_REAL_FFMPEG:?thiếu TVC_CHAOS_REAL_FFMPEG}"
for arg in "$@"; do
  if [ "$arg" = "-filter_complex" ] && [ "$delay" -gt 0 ]; then
    end=$(( $(date +%s) + delay ))
    while [ "$(date +%s)" -lt "$end" ]; do
      if [ -n "${PPID:-}" ] && ! kill -0 "$PPID" 2>/dev/null; then
        exit 130
      fi
      sleep 1
    done
    break
  fi
done
exec "$real" "$@"
WRAPPER
chmod +x "$CHAOS_BIN/ffmpeg"

WORKER_IMAGE_ID=""
CHAOS_IMAGE_ID=""
build_runtime() {
  if [ "$RUNTIME" = "docker" ]; then
    record "==> build image $WORKER_IMAGE từ worker/Dockerfile"
    docker build -q -t "$WORKER_IMAGE" "$ROOT/worker" >/dev/null
    WORKER_IMAGE_ID="$(docker image inspect -f '{{.Id}}' "$WORKER_IMAGE")"
    record "image $WORKER_IMAGE id=$WORKER_IMAGE_ID"
    record "image ffmpeg: $(docker run --rm --entrypoint ffmpeg "$WORKER_IMAGE" -version | head -n 1)"

    local ctx="$RUN_DIR/chaos-image"
    mkdir -p "$ctx"
    cp "$CHAOS_BIN/ffmpeg" "$ctx/ffmpeg"
    cat > "$ctx/Dockerfile" <<EOF
FROM $WORKER_IMAGE
USER root
COPY ffmpeg /usr/local/bin/ffmpeg
RUN chmod 0755 /usr/local/bin/ffmpeg
USER worker
EOF
    docker build -q -t "$CHAOS_IMAGE" "$ctx" >/dev/null
    CHAOS_IMAGE_ID="$(docker image inspect -f '{{.Id}}' "$CHAOS_IMAGE")"
    record "image $CHAOS_IMAGE (ffmpeg delay wrapper) id=$CHAOS_IMAGE_ID"
  else
    record "==> build worker binary"
    (cd "$ROOT/worker" && go build -o "$WORKER_BIN" ./cmd/worker)
    record "worker binary=$WORKER_BIN"
  fi
}

build_runtime

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
  echo "==> Ca SIGTERM giữa transcode"
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
  assert_playable "$PROJECT" "$VIDEO" sigterm
  assert_temp_clean "$TMP"
  stop_worker sigterm-redelivery "$pid2" 0
  record "sigterm: PASS"
}

scenario_sigkill() {
  echo "==> Ca SIGKILL giữa transcode"
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
  worker_signal "$pid" KILL
  reap_worker sigkill "$pid" 137
  tmp_dir_exists "$TMP" "$JOB" || fail "mong thư mục tạm mồ côi $TMP/$JOB sau SIGKILL"
  if job_line "$log" "$JOB" message_released >/dev/null 2>&1 \
    || grep -Fq '"msg":"message_released"' "$log"; then
    fail "SIGKILL không thể release message"
  fi
  record "sigkill: thư mục tạm mồ côi tồn tại, không có release/ACK"

  # Worker mới dùng lại đúng TVC_TMP_DIR: Prepare phải dọn job dir mồ côi.
  start_worker sigkill-redelivery "$VISIBILITY" "$HEARTBEAT" 0 "$TMP"
  local pid2="$WORKER_PID" log2="$WORKER_LOG"
  sleep 1
  if tmp_dir_exists "$TMP" "$JOB"; then
    fail "restart không dọn thư mục mồ côi $TMP/$JOB"
  fi
  record "sigkill: restart đã dọn thư mục mồ côi của $JOB"

  start=$SECONDS
  wait_for_job_line "$log2" "$JOB" job_received 120
  elapsed=$((SECONDS - start))
  record "sigkill: nhận lại sau ${elapsed}s kể từ restart (chờ visibility lần gia hạn cuối)"
  assert_attempt "$log2" "$JOB" 2
  wait_for_job_line "$log2" "$JOB" job_finished 180
  wait_status "$VIDEO" "COMPLETED" 60
  assert_assets "$PROJECT" "$VIDEO"
  assert_playable "$PROJECT" "$VIDEO" sigkill
  assert_temp_clean "$TMP"
  stop_worker sigkill-redelivery "$pid2" 0
  record "sigkill: PASS"
}

scenario_dlq() {
  echo "==> Ca DLQ sau 3 lần nhận (input không tồn tại)"
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

scenario_competing() {
  echo "==> Ca COMPETING: hai worker cùng xử lý một video (at-least-once)"
  setup_queue competing
  JOB="job-competing-$RUN_ID"
  VIDEO="vid-competing-$RUN_ID"
  KEY="$PROJECT/$VIDEO/source.mp4"
  TMP_A="$RUN_DIR/tmp-competing-a"
  TMP_B="$RUN_DIR/tmp-competing-b"
  echo "$VIDEO" >> "$RUN_DIR/videos.txt"
  seed_video "$VIDEO" "$PROJECT" "$KEY"
  upload_sample "$KEY"

  start_worker competing-a "$VISIBILITY" "$HEARTBEAT" "$FFMPEG_DELAY" "$TMP_A"
  local ref_a="$WORKER_PID" log_a="$WORKER_LOG"
  start_worker competing-b "$VISIBILITY" "$HEARTBEAT" "$FFMPEG_DELAY" "$TMP_B"
  local ref_b="$WORKER_PID" log_b="$WORKER_LOG"
  sleep 2
  # Hai message trùng jobId: mô phỏng SQS giao cùng job cho hai worker khi
  # chưa có lease/thuê quyền sở hữu (A5, hợp đồng còn DRAFT).
  send_job "$JOB" "$VIDEO" "$PROJECT" "$KEY"
  send_job "$JOB" "$VIDEO" "$PROJECT" "$KEY"
  record "competing: đã gửi 2 message cùng jobId=$JOB"

  wait_for_job_line "$log_a" "$JOB" transcode_started 120
  wait_for_job_line "$log_b" "$JOB" transcode_started 120
  record "competing: cả hai worker transcode cùng video (không có lease)"

  local start=$SECONDS finished=0 completed=0 by_other=0 visible=1 not_visible=1
  while [ $((SECONDS - start)) -lt 240 ]; do
    finished=$(( $(count_job_msg "$log_a" "$JOB" job_finished) + $(count_job_msg "$log_b" "$JOB" job_finished) ))
    if [ "$finished" -ge 2 ]; then break; fi
    sleep 0.5
  done
  [ "$finished" -ge 2 ] || fail "hai worker chưa kết thúc (job_finished=$finished)"
  completed=$(( $(grep -Fc '"status":"COMPLETED"' "$log_a" || true) + $(grep -Fc '"status":"COMPLETED"' "$log_b" || true) ))
  by_other=$(( $(grep -Fc '"status":"COMPLETED_BY_OTHER"' "$log_a" || true) + $(grep -Fc '"status":"COMPLETED_BY_OTHER"' "$log_b" || true) ))
  [ "$completed" -eq 1 ] || fail "job_finished COMPLETED=$completed (mong 1)"
  [ "$by_other" -eq 1 ] || fail "job_finished COMPLETED_BY_OTHER=$by_other (mong 1)"
  record "competing: 1 COMPLETED + 1 COMPLETED_BY_OTHER; worker thua ACK message trùng"

  wait_status "$VIDEO" "COMPLETED" 60
  assert_assets "$PROJECT" "$VIDEO"
  assert_playable "$PROJECT" "$VIDEO" competing
  local attempts
  attempts="$(get_attempt_count "$VIDEO")"
  [ "$attempts" -ge 2 ] || fail "attemptCount=$attempts (mong >=2)"
  record "competing: attemptCount=$attempts — cả hai worker đã MarkProcessing"

  start=$SECONDS
  while [ $((SECONDS - start)) -lt 30 ]; do
    read -r visible not_visible < <(queue_counts)
    if [ "$visible" = "0" ] && [ "$not_visible" = "0" ]; then break; fi
    sleep 0.5
  done
  [ "$visible" = "0" ] && [ "$not_visible" = "0" ] \
    || fail "queue chưa rỗng sau ACK: visible=$visible notVisible=$not_visible"
  record "competing: queue rỗng (cả hai message đã ACK)"

  stop_worker competing-a "$ref_a" 0
  stop_worker competing-b "$ref_b" 0
  assert_temp_clean "$TMP_A"
  assert_temp_clean "$TMP_B"
  record "competing: PASS — ghi nhận hạn chế không exactly-once (transcode trùng)"
}

run_scenario() {
  case "$1" in
    sigterm) scenario_sigterm ;;
    sigkill) scenario_sigkill ;;
    dlq) scenario_dlq ;;
    competing) scenario_competing ;;
    *) fail "kịch bản không hợp lệ: $1 (dùng sigterm,sigkill,dlq,competing)" ;;
  esac
}

scenario_list="$(printf '%s' "$SCENARIOS" | tr ',' ' ')"
for scenario in $scenario_list; do
  run_scenario "$scenario"
done

record "==> Tất cả kịch bản PASS ($SCENARIOS)"
