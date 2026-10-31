#!/usr/bin/env bash
# E2E thật cho pipeline video. Hai chế độ:
#   worker — worker integration có thể chạy ngay: LocalStack + image worker thật
#            + playback qua nginx /media/*. Đây KHÔNG phải A4 (không qua API).
#   api    — A4: đăng nhập → tạo project → tạo video → PUT → confirm → chờ
#            COMPLETED → assets/playback. Cần backend + worker đang chạy; schema
#            theo docs/contracts/openapi.yaml (DRAFT, chưa được Phương duyệt).
#
# Dùng:
#   bash tests/e2e/run.sh worker
#   E2E_EMAIL=... E2E_PASSWORD=... bash tests/e2e/run.sh api
#   BASE_URL=https://domain bash tests/e2e/run.sh api   # cloud
#
# Biến: E2E_TIMEOUT=180, E2E_PORT=8000, E2E_MEDIA_BASE (bỏ qua nginx tự dựng),
# E2E_WORKER_IMAGE=tvc-worker:e2e, KEEP_E2E_RESOURCES=1.
# Exit != 0 khi timeout/assertion sai. Bằng chứng: local/e2e-logs/<UTC>-<mode>/.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODE="${1:-worker}"
case "$MODE" in
  worker|api) ;;
  *) echo "usage: $0 worker|api" >&2; exit 2 ;;
esac

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RUN_ID="$(date +%s)-$$"
RUN_DIR="$ROOT/local/e2e-logs/$STAMP-$MODE"
SUMMARY="$RUN_DIR/summary.txt"
SAMPLE="$ROOT/worker/testdata/sample-10s-720p.mp4"
TIMEOUT="${E2E_TIMEOUT:-180}"
KEEP="${KEEP_E2E_RESOURCES:-0}"
PORT="${E2E_PORT:-8000}"
REGION="ap-southeast-1"
ENDPOINT="http://localhost:4566"
ACCOUNT="000000000000"
INPUT_BUCKET="tvc-local-input"
OUTPUT_BUCKET="tvc-local-output"
VIDEOS_TABLE="tvc-local-videos"
LOCALSTACK_CONTAINER="${TVC_LOCALSTACK_CONTAINER:-tvc-localstack}"
DOCKER_HOST_INTERNAL="host.docker.internal"

PROJECT=""
VIDEO=""
JOB=""
INPUT_KEY=""
QUEUE_URL=""
DLQ_URL=""
WORKER_CONTAINER=""
TAIL_PID=""
NGINX_PID=""
MEDIA_BASE=""
BASE_URL=""
API_BASE=""
ORIGIN=""
TOKEN=""
UPLOAD_TICKET=""

mkdir -p "$RUN_DIR"
: > "$SUMMARY"

record() {
  printf '%s\n' "$*" >> "$SUMMARY"
  printf '  %s\n' "$*"
}

fail() {
  printf 'FAIL: %s\n' "$*" | tee -a "$SUMMARY" >&2
  exit 1
}

json_get() { # json_get <file> <dot.path>
  python3 - "$1" "$2" 2>/dev/null <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    d = json.load(f)
for part in sys.argv[2].split("."):
    d = d[int(part)] if isinstance(d, list) else d[part]
print(d)
PY
}

json_headers() { # in "key<TAB>value" cho <file>.<path> (mặc định headers)
  python3 - "$1" "${2:-headers}" 2>/dev/null <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    d = json.load(f)
for part in sys.argv[2].split("."):
    d = d[part]
for k, v in d.items():
    print(f"{k}\t{v}")
PY
}

content_type() {
  tr -d '\r' < "$1" | awk 'tolower($0) ~ /^content-type:/ {sub(/^[^:]+:[[:space:]]*/, ""); print; exit}'
}

aws_() {
  if docker inspect "$LOCALSTACK_CONTAINER" >/dev/null 2>&1; then
    docker exec -i "$LOCALSTACK_CONTAINER" awslocal "$@"
  else
    (cd "$ROOT" && docker compose exec -T localstack awslocal "$@")
  fi
}

sha256_of() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | cut -d' ' -f1
  else
    sha256sum "$1" | cut -d' ' -f1
  fi
}

wait_media_server() {
  local code start=$SECONDS
  while [ $((SECONDS - start)) -lt 10 ]; do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 1 "$MEDIA_BASE/media/__e2e_probe" || true)"
    if [ -n "$code" ] && [ "$code" != "000" ]; then return 0; fi
    if [ -n "$NGINX_PID" ] && ! kill -0 "$NGINX_PID" 2>/dev/null; then
      fail "nginx thoát khi khởi động — xem $RUN_DIR/nginx/nginx.log"
    fi
    sleep 0.2
  done
  fail "nginx không sẵn sàng trên $MEDIA_BASE"
}

start_media_server() {
  command -v nginx >/dev/null 2>&1 || fail "thiếu nginx trong PATH"
  local dir="$RUN_DIR/nginx"
  mkdir -p "$dir/logs"
  cat > "$dir/mime.types" <<'EOF'
types {
  text/html html;
  application/vnd.apple.mpegurl m3u8;
  video/mp2t ts;
  image/jpeg jpg jpeg;
  application/octet-stream bin;
}
EOF
  cat > "$dir/nginx.conf" <<EOF
worker_processes 1;
pid $dir/nginx.pid;
error_log $dir/logs/error.log info;

events { worker_connections 128; }

http {
  include $dir/mime.types;
  default_type application/octet-stream;
  access_log $dir/logs/access.log;

  server {
    listen 127.0.0.1:$PORT;
    server_name localhost;

    location /media/ {
      rewrite ^/media/(.*)\$ /tvc-local-output/\$1 break;
      proxy_pass http://127.0.0.1:4566;
      proxy_set_header Host \$host;
    }

    location / {
      return 404;
    }
  }
}
EOF
  nginx -c "$dir/nginx.conf" -p "$dir" -g 'daemon off;' >>"$dir/nginx.log" 2>&1 &
  NGINX_PID=$!
  MEDIA_BASE="http://127.0.0.1:$PORT"
  wait_media_server
  record "media server: $MEDIA_BASE/media/* -> LocalStack $OUTPUT_BUCKET"
}

fetch_media() { # fetch_media <url> <out> <headers>
  local code
  if ! code="$(curl -sS --connect-timeout 5 --max-time 30 -D "$3" -o "$2" -w '%{http_code}' "$1")"; then
    fail "curl lỗi khi tải $1"
  fi
  [ "$code" = "200" ] || fail "GET $1 -> HTTP $code"
}

assert_type() { # assert_type <file> <expected substring> <label>
  local got got_lc
  got="$(content_type "$1")"
  got_lc="$(printf '%s' "$got" | tr '[:upper:]' '[:lower:]')"
  case "$got_lc" in
    *"$2"*) ;;
    *) fail "$3 Content-Type=$got (mong $2)" ;;
  esac
  printf '%s' "$got"
}

assert_playback_http() { # assert_playback_http <label> <master_url> <thumb_url> <relative_record>
  local label="$1" master_url="$2" thumb_url="$3" rel="$4"
  local dir="$RUN_DIR/playback-$label" base="${master_url%/hls/master.m3u8}"
  local master seg out height frames rendition got
  mkdir -p "$dir"
  fetch_media "$master_url" "$dir/master.m3u8" "$dir/master.headers"
  got="$(assert_type "$dir/master.headers" "application/vnd.apple.mpegurl" "$label master")"
  record "playback $rel/hls/master.m3u8 HTTP 200 Content-Type=$got"
  master="$(cat "$dir/master.m3u8")"
  grep -Fq '360p/index.m3u8' <<<"$master" || fail "master thiếu 360p"
  grep -Fq '720p/index.m3u8' <<<"$master" || fail "master thiếu 720p"

  for rendition in 360p 720p; do
    mkdir -p "$dir/$rendition"
    fetch_media "$base/hls/$rendition/index.m3u8" "$dir/$rendition/index.m3u8" "$dir/$rendition/headers"
    got="$(assert_type "$dir/$rendition/headers" "application/vnd.apple.mpegurl" "$rendition playlist")"
    local seg_count=0
    while IFS= read -r seg; do
      [ -n "$seg" ] || continue
      fetch_media "$base/hls/$rendition/$seg" "$dir/$rendition/$seg" "$dir/$rendition/seg.headers"
      assert_type "$dir/$rendition/seg.headers" "video/mp2t" "$rendition segment" >/dev/null
      seg_count=$((seg_count + 1))
    done < <(grep -E '\.ts$' "$dir/$rendition/index.m3u8" || true)
    [ "$seg_count" -gt 0 ] || fail "$rendition playlist không tham chiếu segment .ts"
    out="$(ffprobe -v error -allowed_extensions ALL -select_streams v:0 -count_frames \
      -show_entries stream=height,nb_read_frames -of csv=p=0 \
      "$dir/$rendition/index.m3u8" 2>/dev/null | head -n 1 | tr -d '\r')"
    height="${out%%,*}"
    frames="${out##*,}"
    [ "$height" = "${rendition%p}" ] || fail "$rendition height=$height (mong ${rendition%p})"
    [ -n "$frames" ] && [ "$frames" -gt 0 ] || fail "$rendition không giải mã được frame nào"
    record "playback $rel/hls/$rendition/index.m3u8 HTTP 200 Content-Type=$got segments=$seg_count height=$height frames=$frames"
  done

  fetch_media "$thumb_url" "$dir/default.jpg" "$dir/thumb.headers"
  got="$(assert_type "$dir/thumb.headers" "image/jpeg" "thumbnail")"
  local magic
  magic="$(od -An -tx1 -N3 "$dir/default.jpg" | tr -d ' \n')"
  [ "$magic" = "ffd8ff" ] || fail "thumbnail không phải JPEG (magic=$magic)"
  record "playback $rel/thumb/default.jpg HTTP 200 Content-Type=$got JPEG=$magic"
}

# ---------- API mode (A4; schema DRAFT) ----------

api_call() { # api_call <method> <path> <body_file|-> <out_file> [extra curl args...]
  local method="$1" path="$2" body="$3" out="$4" code
  shift 4
  local args=(-sS --connect-timeout 5 --max-time 60 -o "$out" -w '%{http_code}' -X "$method")
  if [ -n "$TOKEN" ]; then args+=(-H "Authorization: Bearer $TOKEN"); fi
  if [ "$body" != "-" ]; then
    args+=(-H 'Content-Type: application/json' --data-binary "@$body")
  fi
  args+=("$@")
  if ! code="$(curl "${args[@]}" "$API_BASE$path")"; then
    fail "không gọi được $method $API_BASE$path — backend/A3 chưa chạy?"
  fi
  printf '%s' "$code"
}

expect_2xx() { # expect_2xx <code> <out_file> <bước>
  case "$1" in
    2??) ;;
    *)
      record "$3: HTTP $1"
      if [ -s "$2" ]; then record "body: $(head -c 400 "$2")"; fi
      fail "$3: HTTP $1"
      ;;
  esac
}

json_or_fail() { # json_or_fail <file> <path> <bước>
  local value
  if ! value="$(json_get "$1" "$2")" || [ -z "$value" ]; then
    fail "$3: response thiếu \`$2\` (contract DRAFT chưa khớp?)"
  fi
  printf '%s' "$value"
}

run_api() {
  command -v curl >/dev/null 2>&1 || fail "thiếu curl"
  command -v python3 >/dev/null 2>&1 || fail "thiếu python3"
  command -v ffprobe >/dev/null 2>&1 || fail "thiếu ffprobe"
  [ -f "$SAMPLE" ] || fail "thiếu video mẫu $SAMPLE"
  [ -n "${E2E_EMAIL:-}" ] && [ -n "${E2E_PASSWORD:-}" ] \
    || fail "đặt E2E_EMAIL và E2E_PASSWORD để chạy chế độ api"

  BASE_URL="${BASE_URL:-http://localhost:8000}"
  BASE_URL="${BASE_URL%/}"
  case "$BASE_URL" in
    */api) API_BASE="$BASE_URL"; ORIGIN="${BASE_URL%/api}" ;;
    *) API_BASE="$BASE_URL/api"; ORIGIN="$BASE_URL" ;;
  esac
  record "api base=$API_BASE origin=$ORIGIN (openapi DRAFT)"

  local body="$RUN_DIR/login.json"
  python3 -c 'import json,sys; print(json.dumps({"email":sys.argv[1],"password":sys.argv[2]}))' \
    "$E2E_EMAIL" "$E2E_PASSWORD" > "$RUN_DIR/login-request.json"
  local code
  code="$(api_call POST /auth/login "$RUN_DIR/login-request.json" "$body")"
  rm -f "$RUN_DIR/login-request.json"
  expect_2xx "$code" "$body" "login"
  TOKEN="$(json_or_fail "$body" accessToken login)"
  rm -f "$body" # không lưu access token trong bằng chứng
  record "login OK (access token giữ trong bộ nhớ, không ghi bằng chứng)"

  local project_body="$RUN_DIR/project-request.json"
  python3 -c 'import json,sys; print(json.dumps({"name":sys.argv[1],"description":"E2E runner"}))' \
    "e2e-$RUN_ID" > "$project_body"
  code="$(api_call POST /projects "$project_body" "$RUN_DIR/project.json")"
  expect_2xx "$code" "$RUN_DIR/project.json" "tạo project"
  PROJECT="$(json_or_fail "$RUN_DIR/project.json" projectId "tạo project")"
  record "projectId=$PROJECT"

  local size filename video_body
  size="$(wc -c < "$SAMPLE" | tr -d ' ')"
  filename="$(basename "$SAMPLE")"
  python3 -c 'import json,sys; print(json.dumps({"filename":sys.argv[1],"sizeBytes":int(sys.argv[2]),"contentType":"video/mp4"}))' \
    "$filename" "$size" > "$RUN_DIR/video-request.json"
  UPLOAD_TICKET="$RUN_DIR/upload-ticket.json"
  code="$(api_call POST "/projects/$PROJECT/videos" "$RUN_DIR/video-request.json" "$UPLOAD_TICKET")"
  expect_2xx "$code" "$UPLOAD_TICKET" "tạo video"
  VIDEO="$(json_or_fail "$UPLOAD_TICKET" video.videoId "tạo video")"
  local ticket_upload expires headers_count
  ticket_upload="$(json_or_fail "$UPLOAD_TICKET" uploadUrl "tạo video")"
  expires="$(json_or_fail "$UPLOAD_TICKET" expiresAt "tạo video")"
  headers_count="$(json_headers "$UPLOAD_TICKET" | wc -l | tr -d ' ')"
  [ "$headers_count" -ge 1 ] || fail "upload ticket không có header nào để PUT"
  local asset_base="/media/$PROJECT/$VIDEO"
  record "videoId=$VIDEO sizeBytes=$size expiresAt=$expires uploadHeaders=$headers_count (không ghi URL ký)"

  local upload_url="$ticket_upload"
  case "$upload_url" in
    http*) ;;
    *) upload_url="$(python3 -c 'import sys,urllib.parse; print(urllib.parse.urljoin(sys.argv[1]+"/", sys.argv[2]))' "$ORIGIN" "$upload_url")" ;;
  esac
  local upload_args=(-sS --connect-timeout 5 --max-time 120 -o "$RUN_DIR/upload-response.txt" -w '%{http_code}' -X PUT --data-binary "@$SAMPLE")
  local hk hv
  while IFS=$'\t' read -r hk hv; do
    [ -n "$hk" ] || continue
    case "$(printf '%s' "$hk" | tr '[:upper:]' '[:lower:]')" in
      content-length|host) continue ;; # curl tự quản lý, không gửi thủ công
    esac
    upload_args+=(-H "$hk: $hv")
  done < <(json_headers "$UPLOAD_TICKET")
  # Không gửi Authorization lên presigned URL.
  if ! code="$(curl "${upload_args[@]}" "$upload_url")"; then
    fail "PUT lên uploadUrl thất bại"
  fi
  case "$code" in 2??) ;; *) fail "PUT uploadUrl -> HTTP $code" ;; esac
  rm -f "$UPLOAD_TICKET" # URL ký không được lưu trong bằng chứng
  record "PUT uploadUrl HTTP $code (không kèm bearer)"

  code="$(api_call POST "/videos/$VIDEO/confirm" - "$RUN_DIR/confirm.json")"
  expect_2xx "$code" "$RUN_DIR/confirm.json" "confirm"
  record "confirm HTTP $code"

  local start=$SECONDS status="" detail="$RUN_DIR/video.json"
  while [ $((SECONDS - start)) -lt "$TIMEOUT" ]; do
    code="$(api_call GET "/videos/$VIDEO" - "$detail")"
    expect_2xx "$code" "$detail" "GET video"
    status="$(json_get "$detail" status || true)"
    case "$status" in
      COMPLETED) break ;;
      FAILED_VALIDATION|FAILED_TRANSCODE|FAILED_STORAGE)
        local error_code
        error_code="$(json_get "$detail" errorCode || true)"
        fail "video $VIDEO $status errorCode=$error_code"
        ;;
      UPLOADING|PENDING|PROCESSING|"") ;;
      *) fail "status không thuộc contract: $status" ;;
    esac
    sleep 1
  done
  [ "$status" = "COMPLETED" ] || fail "timeout ${TIMEOUT}s: video $VIDEO status=$status"
  record "video $VIDEO COMPLETED sau $((SECONDS - start))s"

  local hls thumb
  hls="$(json_or_fail "$detail" assets.hls "assets")"
  thumb="$(json_or_fail "$detail" assets.thumbnail "assets")"
  [ "$hls" = "$asset_base/hls/master.m3u8" ] || fail "assets.hls=$hls (mong $asset_base/hls/master.m3u8)"
  [ "$thumb" = "$asset_base/thumb/default.jpg" ] || fail "assets.thumbnail=$thumb (mong $asset_base/thumb/default.jpg)"
  record "assets.hls=$hls assets.thumbnail=$thumb"

  local master_url thumb_url
  case "$hls" in http*) master_url="$hls" ;; *) master_url="$ORIGIN$hls" ;; esac
  case "$thumb" in http*) thumb_url="$thumb" ;; *) thumb_url="$ORIGIN$thumb" ;; esac
  assert_playback_http api "$master_url" "$thumb_url" "$asset_base"

  if [ "$KEEP" != "1" ]; then
    record "cleanup api: contract DRAFT không có DELETE; giữ project/video $PROJECT/$VIDEO trên môi trường test"
  fi
  record "KẾT LUẬN A4: API E2E PASS (videoId=$VIDEO)"
}

# ---------- Worker mode (integration; not A4) ----------

setup_queue() {
  local suffix="$1"
  local queue_name="tvc-e2e-$suffix-$RUN_ID"
  local dlq_name="tvc-e2e-$suffix-dlq-$RUN_ID"
  QUEUE_URL="http://localhost:4566/$ACCOUNT/$queue_name"
  DLQ_URL="http://localhost:4566/$ACCOUNT/$dlq_name"
  local dlq_arn="arn:aws:sqs:$REGION:$ACCOUNT:$dlq_name"
  aws_ sqs create-queue --queue-name "$dlq_name" >/dev/null
  local attrs
  attrs="$(cat <<JSON
{"VisibilityTimeout":"300","ReceiveMessageWaitTimeSeconds":"20","RedrivePolicy":"{\"deadLetterTargetArn\":\"$dlq_arn\",\"maxReceiveCount\":\"3\"}"}
JSON
)"
  aws_ sqs create-queue --queue-name "$queue_name" --attributes "$attrs" >/dev/null
  printf '%s\n' "$queue_name" > "$RUN_DIR/queue-name.txt"
  record "queue=$queue_name dlq=$dlq_name visibility=300s wait=20s maxReceiveCount=3"
}

seed_and_upload() {
  local now
  now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  aws_ dynamodb put-item --table-name "$VIDEOS_TABLE" --item "$(printf \
    '{"videoId":{"S":"%s"},"projectId":{"S":"%s"},"status":{"S":"PENDING"},"createdAt":{"S":"%s"},"updatedAt":{"S":"%s"},"originalKey":{"S":"%s"}}' \
    "$VIDEO" "$PROJECT" "$now" "$now" "$INPUT_KEY")"
  aws_ s3 cp - "s3://$INPUT_BUCKET/$INPUT_KEY" --content-type video/mp4 < "$SAMPLE"
  record "seed video=$VIDEO PENDING; upload inputKey=$INPUT_KEY ($(wc -c < "$SAMPLE" | tr -d ' ') bytes)"
}

send_job() {
  local now body
  now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  body="$(printf '{"schemaVersion":1,"jobId":"%s","videoId":"%s","projectId":"%s","inputKey":"%s","requestedAt":"%s"}' \
    "$JOB" "$VIDEO" "$PROJECT" "$INPUT_KEY" "$now")"
  printf '%s\n' "$body" >> "$RUN_DIR/message.jsonl"
  aws_ sqs send-message --queue-url "$QUEUE_URL" --message-body "$body" >/dev/null
  record "send jobId=$JOB videoId=$VIDEO requestedAt=$now"
}

start_worker_container() {
  local image="$1" queue_container_url code
  queue_container_url="${QUEUE_URL/localhost/$DOCKER_HOST_INTERNAL}"
  WORKER_CONTAINER="tvc-e2e-worker-$RUN_ID"
  docker run -d --name "$WORKER_CONTAINER" --init \
    --add-host="$DOCKER_HOST_INTERNAL:host-gateway" \
    -e AWS_REGION="$REGION" \
    -e AWS_ENDPOINT_URL="http://$DOCKER_HOST_INTERNAL:4566" \
    -e AWS_ACCESS_KEY_ID=test \
    -e AWS_SECRET_ACCESS_KEY=test \
    -e TVC_INPUT_BUCKET="$INPUT_BUCKET" \
    -e TVC_OUTPUT_BUCKET="$OUTPUT_BUCKET" \
    -e TVC_JOBS_QUEUE_URL="$queue_container_url" \
    -e TVC_DDB_VIDEOS="$VIDEOS_TABLE" \
    -e TVC_TMP_DIR=/tmp/tvc \
    -e TVC_HEARTBEAT_INTERVAL=120s \
    -e TVC_VISIBILITY_TIMEOUT=300s \
    -e TVC_FFMPEG_TIMEOUT=5m \
    "$image" >/dev/null
  docker logs -f "$WORKER_CONTAINER" >> "$RUN_DIR/worker.log" 2>&1 &
  TAIL_PID=$!
  record "worker container=$WORKER_CONTAINER image=$image heartbeat=120s visibility=300s"
}

get_status() {
  aws_ dynamodb get-item --table-name "$VIDEOS_TABLE" \
    --key "{\"videoId\":{\"S\":\"$VIDEO\"}}" --query 'Item.status.S' --output text
}

get_error_code() {
  aws_ dynamodb get-item --table-name "$VIDEOS_TABLE" \
    --key "{\"videoId\":{\"S\":\"$VIDEO\"}}" --query 'Item.errorCode.S' --output text
}

wait_completed() {
  local start=$SECONDS status=""
  while [ $((SECONDS - start)) -lt "$TIMEOUT" ]; do
    status="$(get_status 2>/dev/null || true)"
    case "$status" in
      COMPLETED) return 0 ;;
      FAILED_VALIDATION|FAILED_TRANSCODE|FAILED_STORAGE)
        fail "video $VIDEO $status errorCode=$(get_error_code 2>/dev/null || true)"
        ;;
    esac
    if [ -n "$WORKER_CONTAINER" ] \
      && [ "$(docker inspect -f '{{.State.Running}}' "$WORKER_CONTAINER" 2>/dev/null || echo false)" != "true" ]; then
      fail "worker container đã thoát trước khi COMPLETED (xem $RUN_DIR/worker.log)"
    fi
    sleep 1
  done
  fail "timeout ${TIMEOUT}s: video $VIDEO status=$status"
}

assert_objects() {
  local prefix="$PROJECT/$VIDEO" key expected want_cc got ct cc seg
  local -a entries=(
    "hls/master.m3u8|application/vnd.apple.mpegurl|max-age=60"
    "hls/360p/index.m3u8|application/vnd.apple.mpegurl|max-age=60"
    "hls/720p/index.m3u8|application/vnd.apple.mpegurl|max-age=60"
    "thumb/default.jpg|image/jpeg|"
  )
  local entry
  for entry in "${entries[@]}"; do
    IFS='|' read -r key expected want_cc <<< "$entry"
    got="$(aws_ s3api head-object --bucket "$OUTPUT_BUCKET" --key "$prefix/$key" \
      --query '[ContentType,CacheControl]' --output text 2>/dev/null)" \
      || fail "thiếu output s3://$OUTPUT_BUCKET/$prefix/$key"
    ct="$(printf '%s' "$got" | cut -f1)"
    cc="$(printf '%s' "$got" | cut -f2)"
    [ "$ct" = "$expected" ] || fail "$key Content-Type=$ct (mong $expected)"
    if [ -n "$want_cc" ]; then
      case "$cc" in
        *"$want_cc"*) ;;
        *) fail "$key Cache-Control=$cc (mong $want_cc)" ;;
      esac
    fi
    record "object $key Content-Type=$ct CacheControl=$cc"
  done

  seg="$(aws_ s3 cp "s3://$OUTPUT_BUCKET/$prefix/hls/360p/index.m3u8" - --only-show-errors \
    | grep -E '\.ts$' | head -n 1 || true)"
  [ -n "$seg" ] || fail "360p playlist không tham chiếu segment .ts"
  got="$(aws_ s3api head-object --bucket "$OUTPUT_BUCKET" --key "$prefix/hls/360p/$seg" \
    --query '[ContentType,CacheControl]' --output text 2>/dev/null)" \
    || fail "thiếu segment $seg"
  [ "$(printf '%s' "$got" | cut -f1)" = "video/mp2t" ] || fail "segment $seg Content-Type sai: $got"
  case "$(printf '%s' "$got" | cut -f2)" in
    *immutable*) ;;
    *) fail "segment $seg thiếu Cache-Control immutable: $got" ;;
  esac
  record "object hls/360p/$seg Content-Type=video/mp2t CacheControl=$(printf '%s' "$got" | cut -f2)"

  local assets attempts
  assets="$(aws_ dynamodb get-item --table-name "$VIDEOS_TABLE" \
    --key "{\"videoId\":{\"S\":\"$VIDEO\"}}" --query 'Item.assets.M' --output json)"
  for key in hls hls360 hls720 thumbnail; do
    grep -Fq "\"$key\"" <<<"$assets" || fail "DynamoDB assets thiếu $key"
  done
  attempts="$(aws_ dynamodb get-item --table-name "$VIDEOS_TABLE" \
    --key "{\"videoId\":{\"S\":\"$VIDEO\"}}" --query 'Item.attemptCount.N' --output text)"
  [ "$attempts" = "1" ] || fail "attemptCount=$attempts (mong 1)"
  record "DynamoDB assets đủ hls/hls360/hls720/thumbnail attemptCount=$attempts"
}

job_line() { # job_line <file> <jobId> <msg>
  grep -F "\"jobId\":\"$2\"" "$1" 2>/dev/null | grep -F "\"msg\":\"$3\"" | tail -n 1
}

wait_worker_log() { # wait_worker_log <msg> <timeout_s>
  local start=$SECONDS
  while [ $((SECONDS - start)) -lt "$2" ]; do
    if job_line "$RUN_DIR/worker.log" "$JOB" "$1" >/dev/null 2>&1; then return 0; fi
    sleep 0.2
  done
  fail "timeout ${2}s: worker log không có msg=$1 cho $JOB"
}

assert_temp_clean() {
  local leftover="" start=$SECONDS
  while [ $((SECONDS - start)) -lt 10 ]; do
    leftover="$(docker exec "$WORKER_CONTAINER" sh -c \
      'find /tmp/tvc -mindepth 1 -maxdepth 1 ! -name .tvc-worker-tmp -print -quit' 2>/dev/null || true)"
    [ -z "$leftover" ] && break
    sleep 0.2
  done
  [ -z "$leftover" ] || fail "thư mục tạm còn sót: $leftover"
  record "temp container sạch: /tmp/tvc"
}

stop_worker_container() { # stop_worker_container <expect_code>
  local expect="$1" code start
  [ -n "$WORKER_CONTAINER" ] || return 0
  if [ "$(docker inspect -f '{{.State.Running}}' "$WORKER_CONTAINER" 2>/dev/null || echo false)" = "true" ]; then
    docker kill --signal=TERM "$WORKER_CONTAINER" >/dev/null
  fi
  start=$SECONDS
  while [ "$(docker inspect -f '{{.State.Running}}' "$WORKER_CONTAINER" 2>/dev/null || echo false)" = "true" ] \
    && [ $((SECONDS - start)) -lt 30 ]; do
    sleep 0.2
  done
  if [ "$(docker inspect -f '{{.State.Running}}' "$WORKER_CONTAINER" 2>/dev/null || echo false)" = "true" ]; then
    docker kill --signal=KILL "$WORKER_CONTAINER" >/dev/null
  fi
  code="$(docker wait "$WORKER_CONTAINER" 2>/dev/null || echo unknown)"
  record "worker exit=$code (mong $expect)"
  docker rm -f "$WORKER_CONTAINER" >/dev/null 2>&1
  WORKER_CONTAINER=""
  [ "$code" = "$expect" ] || fail "worker exit=$code (mong $expect)"
}

run_worker() {
  command -v docker >/dev/null 2>&1 || fail "thiếu docker để chạy image worker"
  command -v ffprobe >/dev/null 2>&1 || fail "thiếu ffprobe"
  command -v curl >/dev/null 2>&1 || fail "thiếu curl"
  [ -f "$SAMPLE" ] || fail "thiếu video mẫu $SAMPLE"
  docker inspect "$LOCALSTACK_CONTAINER" >/dev/null 2>&1 \
    || fail "LocalStack chưa chạy — chạy 'make infra' trước"

  local image="${E2E_WORKER_IMAGE:-tvc-worker:e2e}"
  record "==> build image $image từ worker/Dockerfile"
  docker build -q -t "$image" "$ROOT/worker" >/dev/null
  local image_id
  image_id="$(docker image inspect -f '{{.Id}}' "$image")"
  record "image=$image id=$image_id"
  record "image ffmpeg: $(docker run --rm --entrypoint ffmpeg "$image" -version | head -n 1)"

  MEDIA_BASE="${E2E_MEDIA_BASE:-}"
  if [ -z "$MEDIA_BASE" ]; then
    start_media_server
  else
    MEDIA_BASE="${MEDIA_BASE%/}"
    wait_media_server
    record "media server dùng sẵn: $MEDIA_BASE"
  fi

  setup_queue e2e
  PROJECT="prj-e2e-$RUN_ID"
  VIDEO="vid-e2e-$RUN_ID"
  JOB="job-e2e-$RUN_ID"
  INPUT_KEY="$PROJECT/$VIDEO/source.mp4"
  seed_and_upload
  start_worker_container "$image"
  send_job

  wait_completed
  record "video $VIDEO COMPLETED"
  assert_objects

  wait_worker_log job_finished 30
  wait_worker_log job_received 30
  local received finished
  received="$(job_line "$RUN_DIR/worker.log" "$JOB" job_received)"
  finished="$(job_line "$RUN_DIR/worker.log" "$JOB" job_finished)"
  printf '%s' "$received" | grep -Fq "\"videoId\":\"$VIDEO\"" || fail "job_received thiếu videoId=$VIDEO"
  printf '%s' "$received" | grep -Fq '"attempt":1' || fail "job_received attempt!=1: $received"
  printf '%s' "$received" | grep -Fq '"queueWaitMs":' || fail "job_received thiếu queueWaitMs (requestedAt có trong message)"
  printf '%s' "$finished" | grep -Fq '"status":"COMPLETED"' || fail "job_finished không COMPLETED: $finished"
  printf '%s' "$finished" | grep -Fq '"durationMs":' || fail "job_finished thiếu durationMs"
  record "worker log: job_received attempt=1 queueWaitMs; job_finished COMPLETED durationMs"

  assert_temp_clean
  assert_playback_http worker "$MEDIA_BASE/media/$PROJECT/$VIDEO/hls/master.m3u8" \
    "$MEDIA_BASE/media/$PROJECT/$VIDEO/thumb/default.jpg" "/media/$PROJECT/$VIDEO"

  local counts
  counts="$(aws_ sqs get-queue-attributes --queue-url "$QUEUE_URL" \
    --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible \
    --query '[Attributes.ApproximateNumberOfMessages,Attributes.ApproximateNumberOfMessagesNotVisible]' \
    --output text)"
  record "queue sau ACK: visible/notVisible=$counts (chỉ ghi nhận, không dùng làm assertion chính)"

  stop_worker_container 0
  record "KẾT LUẬN: worker-integration PASS (videoId=$VIDEO jobId=$JOB) — KHÔNG phải A4"
}

cleanup() {
  local code=$?
  set +e
  # Không để token/presigned URL tồn tại trong bằng chứng dù lỗi giữa chừng.
  rm -f "$RUN_DIR/login.json" "$RUN_DIR/login-request.json" "$RUN_DIR/upload-ticket.json"
  if [ -n "$TAIL_PID" ]; then
    kill "$TAIL_PID" 2>/dev/null
  fi
  if [ -n "$WORKER_CONTAINER" ] && docker inspect "$WORKER_CONTAINER" >/dev/null 2>&1; then
    docker kill --signal=TERM "$WORKER_CONTAINER" >/dev/null 2>&1
    local start=$SECONDS
    while [ "$(docker inspect -f '{{.State.Running}}' "$WORKER_CONTAINER" 2>/dev/null || echo false)" = "true" ] \
      && [ $((SECONDS - start)) -lt 20 ]; do sleep 0.2; done
    if [ "$(docker inspect -f '{{.State.Running}}' "$WORKER_CONTAINER" 2>/dev/null || echo false)" = "true" ]; then
      docker kill --signal=KILL "$WORKER_CONTAINER" >/dev/null 2>&1
    fi
    docker logs "$WORKER_CONTAINER" >> "$RUN_DIR/worker.log" 2>&1
    docker rm -f "$WORKER_CONTAINER" >/dev/null 2>&1
  fi
  if [ -n "$NGINX_PID" ]; then
    kill "$NGINX_PID" 2>/dev/null
    wait "$NGINX_PID" 2>/dev/null
  fi

  if [ "$code" -ne 0 ]; then
    {
      echo "--- chẩn đoán ---"
      [ -f "$RUN_DIR/worker.log" ] && { echo "### worker.log (20 dòng cuối)"; tail -n 20 "$RUN_DIR/worker.log"; }
      [ -f "$RUN_DIR/nginx/nginx.log" ] && { echo "### nginx.log"; tail -n 20 "$RUN_DIR/nginx/nginx.log"; }
      [ -f "$RUN_DIR/nginx/logs/error.log" ] && { echo "### nginx error.log"; tail -n 20 "$RUN_DIR/nginx/logs/error.log"; }
    } >> "$SUMMARY"
  fi

  if [ "$MODE" = "worker" ] && [ "$KEEP" != "1" ] && [ -n "$QUEUE_URL" ]; then
    aws_ dynamodb delete-item --table-name "$VIDEOS_TABLE" \
      --key "{\"videoId\":{\"S\":\"$VIDEO\"}}" >/dev/null 2>&1
    aws_ s3 rm "s3://$INPUT_BUCKET/$PROJECT/" --recursive >/dev/null 2>&1
    aws_ s3 rm "s3://$OUTPUT_BUCKET/$PROJECT/" --recursive >/dev/null 2>&1
    aws_ sqs delete-queue --queue-url "$QUEUE_URL" >/dev/null 2>&1
    aws_ sqs delete-queue --queue-url "$DLQ_URL" >/dev/null 2>&1
    record "cleanup: đã xóa queue/DLQ/object/item của ca này"
  elif [ "$MODE" = "worker" ]; then
    record "cleanup: giữ queue/DLQ/object/item vì KEEP_E2E_RESOURCES=1"
  fi

  if [ "$code" -eq 0 ]; then
    printf 'PASS — bằng chứng: %s\n' "$RUN_DIR" | tee -a "$SUMMARY"
  else
    printf 'FAILED (exit=%s) — bằng chứng: %s\n' "$code" "$RUN_DIR" | tee -a "$SUMMARY" >&2
  fi
  exit "$code"
}
trap cleanup EXIT

{
  echo "# E2E — $STAMP ($MODE)"
  echo "runId=$RUN_ID"
  echo "git=$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  if [ -n "$(git -C "$ROOT" status --porcelain 2>/dev/null || true)" ]; then
    echo "gitDirty=yes"
  else
    echo "gitDirty=no"
  fi
  echo "sample=$(basename "$SAMPLE") bytes=$(wc -c < "$SAMPLE" | tr -d ' ') sha256=$(sha256_of "$SAMPLE")"
  echo "host=$(uname -srm) ffprobe=$(ffprobe -version 2>/dev/null | head -n 1 || echo missing)"
  echo "localstack=$(docker inspect -f '{{.Config.Image}}' "$LOCALSTACK_CONTAINER" 2>/dev/null || echo unknown)"
  echo "timeout=${TIMEOUT}s keep=$KEEP"
  if [ "$MODE" = "api" ]; then
    echo "contract=docs/contracts/openapi.yaml (DRAFT, chưa được duyệt)"
  else
    echo "mode=worker-integration (không qua API; không phải A4)"
  fi
} > "$SUMMARY"

if [ "$MODE" = "api" ]; then
  run_api
else
  run_worker
fi

record "==> E2E $MODE PASS"
