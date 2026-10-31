#!/usr/bin/env bash
# Benchmark throughput worker 1 vs 2 (local). Mỗi lượt:
#   upload + seed N video → pre-warm W worker container → gửi N message
#   → chờ tất cả COMPLETED → log job_finished/ACK → dừng worker.
# Dữ liệu thô: jobs.csv (từng job), results.csv (từng lượt), worker-*.log,
# submits.csv, final-batch.json. Không chạy AWS; số liệu local không dùng thay
# cho M4 trên AWS.
#
# Chạy: bash tests/bench/run.sh
# Biến: BENCH_JOBS=12 BENCH_ROUNDS=1 BENCH_WORKERS="1 2" BENCH_TIMEOUT=600
#       BENCH_IMAGE=tvc-worker:bench BENCH_PREWARM=3 KEEP_BENCH_RESOURCES=1
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RUN_ID="$(date +%s)-$$"
BENCH_DIR="$ROOT/local/bench-logs/$STAMP"
SUMMARY="$BENCH_DIR/summary.txt"
JOBS_CSV="$BENCH_DIR/jobs.csv"
RESULTS_CSV="$BENCH_DIR/results.csv"
SAMPLE="$ROOT/worker/testdata/sample-10s-720p.mp4"

JOBS="${BENCH_JOBS:-12}"
ROUNDS="${BENCH_ROUNDS:-1}"
WORKER_COUNTS="${BENCH_WORKERS:-1 2}"
TIMEOUT="${BENCH_TIMEOUT:-600}"
PREWARM="${BENCH_PREWARM:-3}"
IMAGE="${BENCH_IMAGE:-tvc-worker:bench}"
KEEP="${KEEP_BENCH_RESOURCES:-0}"
TARGET_DRAIN_SECONDS="${BENCH_TARGET_DRAIN_SECONDS:-120}"
REGION="ap-southeast-1"
ACCOUNT="000000000000"
INPUT_BUCKET="tvc-local-input"
OUTPUT_BUCKET="tvc-local-output"
VIDEOS_TABLE="tvc-local-videos"
LOCALSTACK_CONTAINER="${TVC_LOCALSTACK_CONTAINER:-tvc-localstack}"
DOCKER_HOST_INTERNAL="host.docker.internal"

ROUND_ACTIVE=0
ROUND=""
WORKERS=""
ROUND_DIR=""
ROUND_PROJECT=""
ROUND_QUEUE_URL=""
ROUND_DLQ_URL=""
ROUND_QUEUE_NAME=""
ROUND_DLQ_NAME=""
VIDEO_IDS=()
JOB_IDS=()
CONTAINERS=()
TAIL_PIDS=()

mkdir -p "$BENCH_DIR"
: > "$SUMMARY"
printf 'round,workers,job,jobId,videoId,worker,submitMs,receiveMs,finishMs,queueWaitMs,durationMs,ffmpegMs,bytesIn,bytesOut,attempt,status,latencyMs\n' > "$JOBS_CSV"
printf 'round,workers,jobs,wallSeconds,throughputJobsPerSec,p50QueueWaitMs,p95QueueWaitMs,p50DurationMs,p95DurationMs,p50LatencyMs,p95LatencyMs,workerUtilization,sumFfmpegMs,attemptMax\n' > "$RESULTS_CSV"

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

sha256_of() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | cut -d' ' -f1
  else
    sha256sum "$1" | cut -d' ' -f1
  fi
}

epoch_ms() {
  python3 -c 'import time; print(int(time.time()*1000))'
}

container_running() {
  [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null || echo false)" = "true" ]
}

stop_container() { # stop_container <name> <expect_code|-> <label>
  local c="$1" expect="$2" label="$3" code start
  docker inspect "$c" >/dev/null 2>&1 || return 0
  if container_running "$c"; then docker kill --signal=TERM "$c" >/dev/null; fi
  start=$SECONDS
  while container_running "$c" && [ $((SECONDS - start)) -lt 30 ]; do sleep 0.2; done
  if container_running "$c"; then docker kill --signal=KILL "$c" >/dev/null; fi
  code="$(docker wait "$c" 2>/dev/null || echo unknown)"
  record "$label exit=$code${expect:+ (mong $expect)}"
  docker rm -f "$c" >/dev/null 2>&1
  if [ "$expect" != "-" ] && [ "$code" != "$expect" ]; then
    fail "$label exit=$code (mong $expect)"
  fi
}

cleanup() {
  local code=$?
  set +e
  local p c
  for p in "${TAIL_PIDS[@]:-}"; do
    [ -n "$p" ] && kill "$p" 2>/dev/null
  done
  if [ "${#CONTAINERS[@]}" -gt 0 ]; then
    for c in "${CONTAINERS[@]}"; do
      [ -n "$c" ] || continue
      stop_container "$c" - "cleanup worker"
    done
  fi
  if [ "$ROUND_ACTIVE" = "1" ] && [ "$KEEP" != "1" ]; then
    local v
    for v in "${VIDEO_IDS[@]:-}"; do
      [ -n "$v" ] || continue
      aws_ dynamodb delete-item --table-name "$VIDEOS_TABLE" \
        --key "{\"videoId\":{\"S\":\"$v\"}}" >/dev/null 2>&1
    done
    aws_ s3 rm "s3://$INPUT_BUCKET/$ROUND_PROJECT/" --recursive >/dev/null 2>&1
    aws_ s3 rm "s3://$OUTPUT_BUCKET/$ROUND_PROJECT/" --recursive >/dev/null 2>&1
    [ -n "$ROUND_QUEUE_URL" ] && aws_ sqs delete-queue --queue-url "$ROUND_QUEUE_URL" >/dev/null 2>&1
    [ -n "$ROUND_DLQ_URL" ] && aws_ sqs delete-queue --queue-url "$ROUND_DLQ_URL" >/dev/null 2>&1
    record "cleanup: xóa queue/DLQ/object/item của lượt đang dở"
  fi
  if [ "$code" -ne 0 ]; then
    {
      echo "--- chẩn đoán ---"
      local f
      for f in "$BENCH_DIR"/*/worker-*.log; do
        [ -f "$f" ] || continue
        echo "### $f (20 dòng cuối)"
        tail -n 20 "$f"
      done
    } >> "$SUMMARY"
  fi
  if [ "$code" -eq 0 ]; then
    printf 'PASS — bằng chứng: %s\n' "$BENCH_DIR" | tee -a "$SUMMARY"
  else
    printf 'FAILED (exit=%s) — bằng chứng: %s\n' "$code" "$BENCH_DIR" | tee -a "$SUMMARY" >&2
  fi
  exit "$code"
}
trap cleanup EXIT

setup_round() {
  ROUND="$1"
  WORKERS="$2"
  ROUND_DIR="$BENCH_DIR/round-$ROUND-w$WORKERS"
  ROUND_PROJECT="prj-bench-$RUN_ID-r$ROUND-w$WORKERS"
  ROUND_QUEUE_NAME="tvc-bench-$RUN_ID-r$ROUND-w$WORKERS"
  ROUND_DLQ_NAME="tvc-bench-dlq-$RUN_ID-r$ROUND-w$WORKERS"
  ROUND_QUEUE_URL="http://localhost:4566/$ACCOUNT/$ROUND_QUEUE_NAME"
  ROUND_DLQ_URL="http://localhost:4566/$ACCOUNT/$ROUND_DLQ_NAME"
  ROUND_ACTIVE=1
  VIDEO_IDS=()
  JOB_IDS=()
  CONTAINERS=()
  TAIL_PIDS=()
  mkdir -p "$ROUND_DIR"
  : > "$ROUND_DIR/submits.csv"

  local dlq_arn="arn:aws:sqs:$REGION:$ACCOUNT:$ROUND_DLQ_NAME" attrs
  aws_ sqs create-queue --queue-name "$ROUND_DLQ_NAME" >/dev/null
  attrs="$(cat <<JSON
{"VisibilityTimeout":"300","ReceiveMessageWaitTimeSeconds":"20","RedrivePolicy":"{\"deadLetterTargetArn\":\"$dlq_arn\",\"maxReceiveCount\":\"3\"}"}
JSON
)"
  aws_ sqs create-queue --queue-name "$ROUND_QUEUE_NAME" --attributes "$attrs" >/dev/null
  record "==> round=$ROUND workers=$WORKERS queue=$ROUND_QUEUE_NAME jobs=$JOBS"

  local i video job key now
  now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  for i in $(seq 1 "$JOBS"); do
    video="vid-bench-$RUN_ID-r$ROUND-w$WORKERS-$i"
    job="job-bench-$RUN_ID-r$ROUND-w$WORKERS-$i"
    key="$ROUND_PROJECT/$video/source.mp4"
    VIDEO_IDS+=("$video")
    JOB_IDS+=("$job")
    aws_ s3 cp - "s3://$INPUT_BUCKET/$key" --content-type video/mp4 < "$SAMPLE"
    aws_ dynamodb put-item --table-name "$VIDEOS_TABLE" --item "$(printf \
      '{"videoId":{"S":"%s"},"projectId":{"S":"%s"},"status":{"S":"PENDING"},"createdAt":{"S":"%s"},"updatedAt":{"S":"%s"},"originalKey":{"S":"%s"}}' \
      "$video" "$ROUND_PROJECT" "$now" "$now" "$key")"
  done
  record "seed $JOBS video PENDING + upload $JOBS input ($(wc -c < "$SAMPLE" | tr -d ' ') bytes/video)"

  python3 - "${VIDEO_IDS[@]}" > "$ROUND_DIR/keys.json" <<'PY'
import json, sys
print(json.dumps({"tvc-local-videos": {"Keys": [{"videoId": {"S": v}} for v in sys.argv[1:]]}}))
PY
}

start_workers() {
  local w cname queue_container_url image_id="${WORKER_IMAGE_ID:-unknown}"
  queue_container_url="${ROUND_QUEUE_URL/localhost/$DOCKER_HOST_INTERNAL}"
  for w in $(seq 1 "$WORKERS"); do
    cname="tvc-bench-$RUN_ID-r$ROUND-w$WORKERS-$w"
    CONTAINERS+=("$cname")
    docker run -d --name "$cname" --init \
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
      "$IMAGE" >/dev/null
    docker logs -f "$cname" >> "$ROUND_DIR/worker-$w.log" 2>&1 &
    TAIL_PIDS+=("$!")
  done
  record "worker containers=$WORKERS image=$IMAGE id=$image_id heartbeat=120s visibility=300s"
  sleep "$PREWARM"
  record "pre-warm ${PREWARM}s xong, bắt đầu gửi job"
}

submit_jobs() {
  local i ms iso body start batch_json
  for i in $(seq 1 "$JOBS"); do
    read -r ms iso <<< "$(python3 -c 'import datetime,time; n=time.time(); print(int(n*1000), datetime.datetime.fromtimestamp(n, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))')"
    body="$(printf '{"schemaVersion":1,"jobId":"%s","videoId":"%s","projectId":"%s","inputKey":"%s","requestedAt":"%s"}' \
      "${JOB_IDS[$((i-1))]}" "${VIDEO_IDS[$((i-1))]}" "$ROUND_PROJECT" \
      "$ROUND_PROJECT/${VIDEO_IDS[$((i-1))]}/source.mp4" "$iso")"
    printf '%s\n' "$body" >> "$ROUND_DIR/messages.jsonl"
    printf '%s,%s,%s\n' "${JOB_IDS[$((i-1))]}" "${VIDEO_IDS[$((i-1))]}" "$ms" >> "$ROUND_DIR/submits.csv"
  done
  start=1
  while [ "$start" -le "$JOBS" ]; do
    batch_json="$(python3 - "$ROUND_DIR/messages.jsonl" "$start" 10 <<'PY'
import json, sys
path, start, size = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
with open(path, encoding="utf-8") as f:
    bodies = [line.rstrip("\n") for line in f]
start -= 1
entries = [{"Id": f"m{start + i + 1}", "MessageBody": b} for i, b in enumerate(bodies[start:start + size])]
print(json.dumps(entries))
PY
)"
    aws_ sqs send-message-batch --queue-url "$ROUND_QUEUE_URL" --entries "$batch_json" >/dev/null
    start=$((start + 10))
  done
  record "đã gửi $JOBS message (batch 10, requestedAt có trong từng message)"
}

wait_completed() {
  local start=$SECONDS completed failed total
  while [ $((SECONDS - start)) -lt "$TIMEOUT" ]; do
    aws_ dynamodb batch-get-item \
      --request-items "$(cat "$ROUND_DIR/keys.json")" --output json > "$ROUND_DIR/last-batch.json" \
      || { sleep 0.5; continue; }
    python3 - "$ROUND_DIR/last-batch.json" > "$ROUND_DIR/counts.txt" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    d = json.load(f)
items = d.get("Responses", {}).get("tvc-local-videos", [])
completed = sum(1 for it in items if it.get("status", {}).get("S") == "COMPLETED")
failed = [it for it in items if str(it.get("status", {}).get("S", "")).startswith("FAILED_")]
print(completed, len(failed), len(items))
for it in failed:
    print(it.get("videoId", {}).get("S", "?"), it.get("status", {}).get("S", "?"), it.get("errorCode", {}).get("S", ""), file=sys.stderr)
PY
    read -r completed failed total < "$ROUND_DIR/counts.txt"
    if [ "$failed" -gt 0 ]; then
      fail "có video FAILED_* (xem $ROUND_DIR/last-batch.json)"
    fi
    if [ "$completed" -eq "$JOBS" ]; then
      cp "$ROUND_DIR/last-batch.json" "$ROUND_DIR/final-batch.json"
      record "COMPLETED $completed/$JOBS sau $((SECONDS - start))s"
      return 0
    fi
    sleep 0.5
  done
  fail "timeout ${TIMEOUT}s: completed=$completed/$JOBS"
}

wait_finished_logs() {
  local start=$SECONDS count
  while [ $((SECONDS - start)) -lt 60 ]; do
    count="$(grep -hF '"msg":"job_finished"' "$ROUND_DIR"/worker-*.log 2>/dev/null | wc -l | tr -d ' ')"
    if [ "$count" -ge "$JOBS" ]; then return 0; fi
    sleep 0.2
  done
  fail "chỉ thấy $count/$JOBS job_finished trong log worker"
}

wait_queue_empty() {
  local start=$SECONDS visible=1 not_visible=1
  while [ $((SECONDS - start)) -lt 30 ]; do
    read -r visible not_visible < <(aws_ sqs get-queue-attributes --queue-url "$ROUND_QUEUE_URL" \
      --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible \
      --query '[Attributes.ApproximateNumberOfMessages,Attributes.ApproximateNumberOfMessagesNotVisible]' \
      --output text)
    if [ "$visible" = "0" ] && [ "$not_visible" = "0" ]; then
      record "queue rỗng sau ACK (visible=0 notVisible=0)"
      return 0
    fi
    sleep 0.5
  done
  fail "queue chưa rỗng sau 30s: visible=$visible notVisible=$not_visible"
}

stop_workers() {
  local w cname
  for w in $(seq 1 "$WORKERS"); do
    cname="tvc-bench-$RUN_ID-r$ROUND-w$WORKERS-$w"
    stop_container "$cname" 0 "worker-$w"
  done
  CONTAINERS=()
  local p
  for p in "${TAIL_PIDS[@]:-}"; do
    if [ -n "$p" ]; then kill "$p" 2>/dev/null || true; fi
  done
  TAIL_PIDS=()
  return 0
}

analyze_round() {
  python3 - "$ROUND_DIR" "$JOBS_CSV" "$RESULTS_CSV" "$ROUND" "$WORKERS" "$JOBS" <<'PY'
import csv, json, math, os, re, sys
from datetime import datetime, timezone

round_dir, jobs_csv, results_csv, rnd, workers, jobs = sys.argv[1:]
round_no, worker_count, job_count = int(rnd), int(workers), int(jobs)

def ts_ms(value):
    value = value.replace("Z", "+00:00")
    value = re.sub(r"(\.\d{6})\d+(?=[+-])", r"\1", value)
    return int(datetime.fromisoformat(value).timestamp() * 1000)

def percentile(values, p):
    if not values:
        return 0
    values = sorted(values)
    idx = max(0, math.ceil(p / 100 * len(values)) - 1)
    return values[idx]

submits = {}
with open(os.path.join(round_dir, "submits.csv"), encoding="utf-8") as f:
    for line in f:
        job, video, ms = line.strip().split(",")
        submits[job] = {"videoId": video, "submitMs": int(ms)}

records = {}
errors = []
for name in sorted(os.listdir(round_dir)):
    if not (name.startswith("worker-") and name.endswith(".log")):
        continue
    worker = name[len("worker-"):-len(".log")]
    with open(os.path.join(round_dir, name), encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if not line.startswith("{"):
                continue
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            job = entry.get("jobId")
            if job not in submits:
                continue
            msg = entry.get("msg")
            rec = records.setdefault(job, {"videoId": submits[job]["videoId"], "worker": worker})
            if msg == "job_received":
                if "receiveMs" in rec:
                    errors.append(f"{job}: job_received trùng trong log")
                rec["receiveMs"] = ts_ms(entry["time"])
                rec["queueWaitMs"] = int(entry.get("queueWaitMs", 0))
                rec["attempt"] = int(entry.get("attempt", 0))
            elif msg == "job_finished":
                if "finishMs" in rec:
                    errors.append(f"{job}: job_finished trùng trong log")
                rec["finishMs"] = ts_ms(entry["time"])
                rec["durationMs"] = int(entry.get("durationMs", 0))
                rec["ffmpegMs"] = int(entry.get("ffmpegMs", 0))
                rec["bytesIn"] = int(entry.get("bytesIn", 0))
                rec["bytesOut"] = int(entry.get("bytesOut", 0))
                rec["status"] = entry.get("status", "")

final_items = {}
with open(os.path.join(round_dir, "final-batch.json"), encoding="utf-8") as f:
    data = json.load(f)
for item in data.get("Responses", {}).get("tvc-local-videos", []):
    final_items[item["videoId"]["S"]] = item

rows = []
for job, sub in submits.items():
    rec = records.get(job)
    if not rec:
        errors.append(f"{job}: không có log job_received/job_finished")
        continue
    if "receiveMs" not in rec or "finishMs" not in rec:
        errors.append(f"{job}: thiếu receive/finish (receive={rec.get('receiveMs')} finish={rec.get('finishMs')})")
        continue
    item = final_items.get(sub["videoId"])
    if not item:
        errors.append(f"{job}: video {sub['videoId']} không có trong final batch")
        continue
    status = item.get("status", {}).get("S", "")
    if status != "COMPLETED":
        errors.append(f"{job}: status cuối={status}")
    assets = item.get("assets", {}).get("M", {})
    missing = [k for k in ("hls", "hls360", "hls720", "thumbnail") if k not in assets]
    if missing:
        errors.append(f"{job}: thiếu assets {missing}")
    attempt = int(item.get("attemptCount", {}).get("N", 0))
    row = {
        "round": round_no,
        "workers": worker_count,
        "job": job.rsplit("-", 1)[-1],
        "jobId": job,
        "videoId": sub["videoId"],
        "worker": rec["worker"],
        "submitMs": sub["submitMs"],
        "receiveMs": rec["receiveMs"],
        "finishMs": rec["finishMs"],
        "queueWaitMs": rec["queueWaitMs"],
        "durationMs": rec["durationMs"],
        "ffmpegMs": rec["ffmpegMs"],
        "bytesIn": rec["bytesIn"],
        "bytesOut": rec["bytesOut"],
        "attempt": attempt,
        "status": status,
        "latencyMs": rec["finishMs"] - sub["submitMs"],
    }
    if row["attempt"] != 1:
        errors.append(f"{job}: attemptCount={row['attempt']} (mong 1)")
    rows.append(row)

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
if len(rows) != job_count:
    print(f"chỉ có {len(rows)}/{job_count} job đủ dữ liệu", file=sys.stderr)
    sys.exit(1)

fieldnames = list(rows[0].keys())
with open(jobs_csv, "a", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writerows(rows)

wall_ms = max(r["finishMs"] for r in rows) - min(r["submitMs"] for r in rows)
wall_s = wall_ms / 1000
throughput = job_count / wall_s if wall_s > 0 else 0
queue_waits = [r["queueWaitMs"] for r in rows]
durations = [r["durationMs"] for r in rows]
latencies = [r["latencyMs"] for r in rows]
sum_duration_ms = sum(durations)
utilization = sum_duration_ms / (wall_ms * worker_count) if wall_ms > 0 else 0
sum_ffmpeg = sum(r["ffmpegMs"] for r in rows)
attempt_max = max(r["attempt"] for r in rows)

result = {
    "round": round_no,
    "workers": worker_count,
    "jobs": job_count,
    "wallSeconds": round(wall_s, 3),
    "throughputJobsPerSec": round(throughput, 3),
    "p50QueueWaitMs": percentile(queue_waits, 50),
    "p95QueueWaitMs": percentile(queue_waits, 95),
    "p50DurationMs": percentile(durations, 50),
    "p95DurationMs": percentile(durations, 95),
    "p50LatencyMs": percentile(latencies, 50),
    "p95LatencyMs": percentile(latencies, 95),
    "workerUtilization": round(utilization, 3),
    "sumFfmpegMs": sum_ffmpeg,
    "attemptMax": attempt_max,
}
with open(results_csv, "a", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=list(result.keys()))
    writer.writerow(result)

workers_seen = {}
for r in rows:
    workers_seen.setdefault(r["worker"], 0)
    workers_seen[r["worker"]] += 1
print(f"ROUND {round_no} workers={worker_count} jobs={job_count} wall={wall_s:.1f}s "
      f"throughput={throughput:.2f} job/s p50dur={result['p50DurationMs']}ms "
      f"p95dur={result['p95DurationMs']}ms p95queue={result['p95QueueWaitMs']}ms "
      f"util={utilization:.2f}")
print(f"DISTRIBUTION round={round_no} " + " ".join(f"{k}={v}" for k, v in sorted(workers_seen.items())))
PY
}

cleanup_resources() {
  local v
  if [ "$KEEP" = "1" ]; then
    record "giữ tài nguyên round=$ROUND: project=$ROUND_PROJECT queue=$ROUND_QUEUE_NAME"
    ROUND_ACTIVE=0
    return 0
  fi
  for v in "${VIDEO_IDS[@]:-}"; do
    [ -n "$v" ] || continue
    aws_ dynamodb delete-item --table-name "$VIDEOS_TABLE" \
      --key "{\"videoId\":{\"S\":\"$v\"}}" >/dev/null 2>&1
  done
  aws_ s3 rm "s3://$INPUT_BUCKET/$ROUND_PROJECT/" --recursive >/dev/null 2>&1
  aws_ s3 rm "s3://$OUTPUT_BUCKET/$ROUND_PROJECT/" --recursive >/dev/null 2>&1
  aws_ sqs delete-queue --queue-url "$ROUND_QUEUE_URL" >/dev/null 2>&1
  aws_ sqs delete-queue --queue-url "$ROUND_DLQ_URL" >/dev/null 2>&1
  record "cleanup round=$ROUND: đã xóa queue/DLQ/object/item"
  ROUND_ACTIVE=0
}

summarize_all() {
  python3 - "$RESULTS_CSV" "$TARGET_DRAIN_SECONDS" > "$BENCH_DIR/aggregate.txt" <<'PY'
import csv, math, statistics, sys
path, target_drain = sys.argv[1], float(sys.argv[2])
with open(path, encoding="utf-8") as f:
    rows = list(csv.DictReader(f))
if not rows:
    raise SystemExit("không có kết quả")
by_workers = {}
for row in rows:
    by_workers.setdefault(int(row["workers"]), []).append(row)
for workers, group in sorted(by_workers.items()):
    tp = [float(r["throughputJobsPerSec"]) for r in group]
    wall = [float(r["wallSeconds"]) for r in group]
    p95dur = [int(r["p95DurationMs"]) for r in group]
    p95queue = [int(r["p95QueueWaitMs"]) for r in group]
    util = [float(r["workerUtilization"]) for r in group]
    print(f"workers={workers} runs={len(group)} throughput_mean={statistics.mean(tp):.2f} job/s "
          f"wall_mean={statistics.mean(wall):.1f}s p95dur_mean={statistics.mean(p95dur):.0f}ms "
          f"p95queue_mean={statistics.mean(p95queue):.0f}ms util_mean={statistics.mean(util):.2f}")
one = by_workers.get(1)
if one:
    p95_ms = max(int(r["p95DurationMs"]) for r in one)
    p95_s = p95_ms / 1000
    backlog = max(1, math.ceil(target_drain / p95_s)) if p95_s > 0 else 1
    print(f"TARGET targetDrain={target_drain:.0f}s source=p95DurationMax(1worker)={p95_ms}ms "
          f"targetBacklogPerInstance={backlog}")
else:
    print("TARGET chưa tính được: cần lượt workers=1")
PY
  cat "$BENCH_DIR/aggregate.txt" >> "$SUMMARY"
  cat "$BENCH_DIR/aggregate.txt"
}

# ------------------------------ main ------------------------------
{
  echo "# Benchmark worker — $STAMP"
  echo "runId=$RUN_ID"
  echo "git=$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  if [ -n "$(git -C "$ROOT" status --porcelain 2>/dev/null || true)" ]; then
    echo "gitDirty=yes"
  else
    echo "gitDirty=no"
  fi
  echo "host=$(uname -srm) cpus=$(sysctl -n hw.ncpu 2>/dev/null || getconf _NPROCESSORS_ONLN 2>/dev/null || echo unknown)"
  echo "docker=$(docker --version 2>/dev/null || echo missing)"
  echo "sample=$(basename "$SAMPLE") bytes=$(wc -c < "$SAMPLE" | tr -d ' ') sha256=$(sha256_of "$SAMPLE")"
  echo "jobs=$JOBS rounds=$ROUNDS workerCounts=$WORKER_COUNTS prewarm=${PREWARM}s timeout=${TIMEOUT}s"
  echo "config: visibility=300s heartbeat=120s ffmpegTimeout=5m maxReceiveCount=3 image=$IMAGE"
  echo "scope=local (Docker Desktop + LocalStack trên cùng máy); KHÔNG phải benchmark AWS"
  echo "dataset=$JOBS bản sao của cùng một video 10s 720p; chưa có dataset đa dạng đã chốt với Lưu"
} > "$SUMMARY"

for bin in docker python3 ffprobe; do
  command -v "$bin" >/dev/null 2>&1 || fail "thiếu $bin trong PATH"
done
[ -f "$SAMPLE" ] || fail "thiếu video mẫu $SAMPLE"
docker inspect "$LOCALSTACK_CONTAINER" >/dev/null 2>&1 || fail "LocalStack chưa chạy — 'make infra' trước"

record "==> build image $IMAGE"
docker build -q -t "$IMAGE" "$ROOT/worker" >/dev/null
WORKER_IMAGE_ID="$(docker image inspect -f '{{.Id}}' "$IMAGE")"
record "image=$IMAGE id=$WORKER_IMAGE_ID"
record "image ffmpeg: $(docker run --rm --entrypoint ffmpeg "$IMAGE" -version | head -n 1)"

for round in $(seq 1 "$ROUNDS"); do
  for workers in $WORKER_COUNTS; do
    setup_round "$round" "$workers"
    start_workers
    submit_jobs
    wait_completed
    wait_finished_logs
    wait_queue_empty
    stop_workers
    analyze_round | tee -a "$SUMMARY"
    cleanup_resources
  done
done

summarize_all
record "==> Benchmark local hoàn tất; AWS M4 vẫn cần Nguyên/Lưu chạy cùng dataset/image trên on-demand"
