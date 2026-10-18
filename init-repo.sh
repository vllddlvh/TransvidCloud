#!/usr/bin/env bash
# ============================================================================
# init-repo.sh - Khởi tạo khung monorepo TransVidCloud
#
# Cách dùng (Git Bash, tại thư mục gốc repo):
#     bash init-repo.sh            # tạo trong thư mục hiện tại
#     bash init-repo.sh ./some/dir # tạo trong thư mục chỉ định
#     FORCE=1 bash init-repo.sh    # GHI ĐÈ mọi file đã có (mặc định: bỏ qua file đã tồn tại)
#     FORCE_ONLY="README.md docs/EPICS.md" bash init-repo.sh
#                                  # chỉ ghi đè các file liệt kê (cách an toàn để cập nhật bản mới)
#
# Script an toàn khi chạy lại: không đè file đã có, không commit, không đụng .git.
# ============================================================================
set -euo pipefail

TARGET="${1:-.}"
mkdir -p "$TARGET"
cd "$TARGET"

CREATED=0
SKIPPED=0

# write <path> < nội_dung  : tạo file (và thư mục cha) từ stdin
write() {
  local f="$1"
  mkdir -p "$(dirname "$f")"
  local force_this=0
  [[ "${FORCE:-0}" == "1" ]] && force_this=1
  [[ " ${FORCE_ONLY:-} " == *" $f "* ]] && force_this=1
  if [[ -e "$f" && "$force_this" != "1" ]]; then
    cat >/dev/null
    SKIPPED=$((SKIPPED + 1))
    echo "  [bỏ qua] $f (đã tồn tại)"
  else
    cat >"$f"
    CREATED=$((CREATED + 1))
    echo "  [tạo]    $f"
  fi
}

echo "==> Khởi tạo TransVidCloud tại: $(pwd)"

# ----------------------------------------------------------------------------
# Tệp gốc
# ----------------------------------------------------------------------------
write .gitattributes <<'__END__'
# Ép LF cho mọi script/cấu hình: tránh lỗi CRLF khi chạy trong container Linux
* text=auto
*.sh   text eol=lf
*.yml  text eol=lf
*.yaml text eol=lf
*.tf   text eol=lf
Makefile text eol=lf
*.mp4  binary
__END__

write .gitignore <<'__END__'
# Môi trường / bí mật
.env
*.pem
*.key

# IDE
.idea/
.vscode/
*.iml

# Backend (Java)
backend/target/
backend/.mvn/wrapper/maven-wrapper.jar

# Worker (Go)
worker/bin/
worker/*.exe

# Frontend
frontend/node_modules/
frontend/dist/

# Terraform
**/.terraform/
*.tfstate
*.tfstate.*
*.tfvars
!*.tfvars.example
crash.log

# Video: bỏ qua mọi video, trừ video mẫu nhỏ cho test
*.mp4
*.mov
*.mkv
!worker/testdata/*.mp4

# Rác hệ điều hành
.DS_Store
Thumbs.db
__END__

write .env.example <<'__END__'
# Copy thành .env:  cp .env.example .env   (KHÔNG commit .env)

# --- AWS / LocalStack ---------------------------------------------------
AWS_REGION=ap-southeast-1
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
# Có giá trị => dùng LocalStack. Để TRỐNG khi chạy trên AWS thật.
AWS_ENDPOINT_URL=http://localhost:4566
# URL S3 mà TRÌNH DUYỆT truy cập được (dùng để ký presigned URL)
S3_PRESIGN_ENDPOINT=http://localhost:4566

# --- Tên tài nguyên (local dùng tiền tố tvc-local-) ----------------------
S3_BUCKET_FRONTEND=tvc-local-frontend
S3_BUCKET_INPUT=tvc-local-input
S3_BUCKET_OUTPUT=tvc-local-output
SQS_QUEUE_NAME=tvc-local-jobs
SQS_DLQ_NAME=tvc-local-jobs-dlq
DDB_TABLE_PREFIX=tvc-local-

# --- App ----------------------------------------------------------------
JWT_SECRET=dev-only-change-me

# --- Giới hạn upload MVP (xem contracts/upload-limits.md) ----------------
MAX_UPLOAD_BYTES=209715200        # 200 MB
MAX_DURATION_SECONDS=300          # 5 phút
MAX_HEIGHT_PIXELS=1080

# --- LocalStack ---------------------------------------------------------
# Từ 23/03/2026 image LocalStack yêu cầu auth token (có gói free phi thương mại).
# Mỗi thành viên tự đăng ký tại https://app.localstack.cloud, dán token vào .env (KHÔNG commit).
LOCALSTACK_AUTH_TOKEN=
# Sau khi pull thành công, ghi cố định tag đã dùng được để cả nhóm giống nhau.
LOCALSTACK_IMAGE=localstack/localstack:latest
__END__

write Makefile <<'__END__'
# Điểm vào duy nhất cho mọi lệnh. Logic phức tạp nằm trong scripts/ và local/scripts/.
# Lưu ý: Git Bash mặc định KHÔNG có make. Cài: `winget install GnuWin32.Make` hoặc `choco install make`.
SHELL := /bin/bash
COMPOSE := docker compose
.DEFAULT_GOAL := help

help: ## Liệt kê lệnh
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-14s %s\n",$$1,$$2}'

infra: ## Chỉ chạy LocalStack (dev backend/worker bằng IDE)
	$(COMPOSE) up -d localstack

up: ## Chạy toàn bộ stack local (LocalStack + backend + worker + nginx)
	$(COMPOSE) --profile full up -d --build

down: ## Dừng stack
	$(COMPOSE) --profile full down

reset: ## Xóa sạch dữ liệu local và dựng lại LocalStack
	$(COMPOSE) --profile full down -v
	$(MAKE) infra

logs: ## Xem log (make logs S=worker)
	$(COMPOSE) logs -f $(S)

worker-scale: ## Thử competing consumers: make worker-scale N=3
	$(COMPOSE) --profile full up -d --scale worker=$(N)

smoke: ## Kiểm tra LocalStack đã có đủ bucket/queue/bảng
	bash ./local/scripts/smoke-test.sh

chaos: ## Kịch bản kill worker giữa chừng (retry/DLQ)
	bash ./local/scripts/chaos-kill-worker.sh

test: ## Unit test các module đã có code
	@if [ -f backend/mvnw ]; then (cd backend && ./mvnw -q test); else echo "backend: chưa có code, bỏ qua"; fi
	@if [ -f worker/go.mod ]; then (cd worker && go test ./...); else echo "worker: chưa có code, bỏ qua"; fi
	@if [ -f frontend/package.json ]; then (cd frontend && npm test --if-present); else echo "frontend: chưa có code, bỏ qua"; fi

e2e: up smoke ## Chạy e2e local (bổ sung tests/e2e khi pipeline xong)

tf-plan: ## terraform plan cho môi trường dev
	cd infra/terraform/envs/dev && terraform init && terraform plan

.PHONY: help infra up down reset logs worker-scale smoke chaos test e2e tf-plan
__END__

write docker-compose.yml <<'__END__'
# Local stack. Profiles:
#   (mặc định)  chỉ localstack            -> make infra
#   app / full  thêm backend, worker      -> make up
#   full        thêm nginx giả lập CloudFront
services:
  localstack:
    image: ${LOCALSTACK_IMAGE:-localstack/localstack:latest}
    ports:
      - "4566:4566"
    environment:
      SERVICES: s3,sqs,dynamodb,ssm
      AWS_DEFAULT_REGION: ap-southeast-1
      LOCALSTACK_AUTH_TOKEN: ${LOCALSTACK_AUTH_TOKEN:-}   # bắt buộc với image hiện tại
    volumes:
      # Script chạy tự động mỗi khi LocalStack "ready" (tạo bucket/queue/bảng)
      - ./local/localstack/init:/etc/localstack/init/ready.d
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:4566/_localstack/health"]
      interval: 5s
      timeout: 5s
      retries: 20

  # --- Bật khi backend/ và worker/ đã có Dockerfile --------------------------
  backend:
    build: ./backend
    profiles: [app, full]
    depends_on:
      localstack: { condition: service_healthy }
    environment:
      AWS_ENDPOINT_URL: http://localstack:4566          # nội bộ container
      S3_PRESIGN_ENDPOINT: http://localhost:4566        # URL trình duyệt truy cập được
      AWS_REGION: ap-southeast-1
      AWS_ACCESS_KEY_ID: test
      AWS_SECRET_ACCESS_KEY: test
      JWT_SECRET: ${JWT_SECRET:-dev-only-change-me}
      MAX_UPLOAD_BYTES: ${MAX_UPLOAD_BYTES:-209715200}
    ports:
      - "8080:8080"

  worker:
    build: ./worker
    profiles: [app, full]
    depends_on:
      localstack: { condition: service_healthy }
    environment:
      AWS_ENDPOINT_URL: http://localstack:4566
      AWS_REGION: ap-southeast-1
      AWS_ACCESS_KEY_ID: test
      AWS_SECRET_ACCESS_KEY: test
      SQS_QUEUE_NAME: tvc-local-jobs
      MAX_DURATION_SECONDS: ${MAX_DURATION_SECONDS:-300}
      MAX_HEIGHT_PIXELS: ${MAX_HEIGHT_PIXELS:-1080}
    # Không publish port để dùng được: docker compose up --scale worker=3

  edge:
    image: nginx:alpine
    profiles: [full]
    ports:
      - "8000:80"
    volumes:
      - ./local/nginx/default.conf:/etc/nginx/conf.d/default.conf:ro
    extra_hosts:
      - "host.docker.internal:host-gateway"
__END__

# ----------------------------------------------------------------------------
# local/ : môi trường chạy thử trên máy
# ----------------------------------------------------------------------------
write local/README.md <<'__END__'
# local/ - Môi trường chạy thử trên máy

**Phụ trách:** Cloud (Lưu chủ trì, Nguyên review) - epic C0.

Chứa mọi thứ chỉ phục vụ chạy local; **không bao giờ được deploy lên AWS**.

| Thư mục | Nội dung |
|---|---|
| `localstack/init/` | Script tạo S3, SQS+DLQ, DynamoDB trên LocalStack khi khởi động |
| `nginx/` | Cấu hình nginx giả lập CloudFront (1 domain: `/`, `/api/*`, `/media/*`) |
| `scripts/` | `smoke-test.sh`, `seed.sh`, `chaos-kill-worker.sh` |

Quy tắc: tên tài nguyên, key schema, cấu hình queue ở đây phải **khớp `contracts/`** và Terraform.
Hướng dẫn chạy: xem `docs/LOCAL_AND_CICD.md`.
__END__

write local/localstack/README.md <<'__END__'
# local/localstack/

Script trong `init/` được LocalStack chạy tự động mỗi lần khởi động (`ready.d`).
Dữ liệu **không được lưu** giữa các lần chạy: `make reset` luôn cho ra trạng thái sạch.

- `init/01-resources.sh`: tạo 3 bucket, queue + DLQ (visibility 300s, long polling 20s, `maxReceiveCount=3`), 4 bảng DynamoDB.
- `init/cors.json`: CORS cho bucket input để trình duyệt PUT bằng presigned URL.

Sửa key schema ở đây thì **phải sửa `contracts/dynamodb-tables.md` và Terraform** (module `database`) cùng PR.
__END__

write local/localstack/init/01-resources.sh <<'__END__'
#!/bin/bash
# Chạy bên trong container LocalStack (có sẵn lệnh awslocal).
set -euo pipefail
REGION=ap-southeast-1
P=tvc-local

echo "[init] S3 buckets"
for b in frontend input output; do
  awslocal s3 mb "s3://${P}-${b}"
done
awslocal s3api put-bucket-cors --bucket "${P}-input" \
  --cors-configuration file:///etc/localstack/init/ready.d/cors.json

echo "[init] SQS + DLQ"
awslocal sqs create-queue --queue-name "${P}-jobs-dlq" >/dev/null
cat > /tmp/queue-attrs.json <<JSON
{"VisibilityTimeout":"300","ReceiveMessageWaitTimeSeconds":"20","RedrivePolicy":"{\"deadLetterTargetArn\":\"arn:aws:sqs:${REGION}:000000000000:${P}-jobs-dlq\",\"maxReceiveCount\":\"3\"}"}
JSON
awslocal sqs create-queue --queue-name "${P}-jobs" --attributes file:///tmp/queue-attrs.json >/dev/null

echo "[init] DynamoDB tables (theo contracts/dynamodb-tables.md)"
awslocal dynamodb create-table --table-name "${P}-users" \
  --attribute-definitions AttributeName=userId,AttributeType=S AttributeName=email,AttributeType=S \
  --key-schema AttributeName=userId,KeyType=HASH \
  --global-secondary-indexes 'IndexName=email-index,KeySchema=[{AttributeName=email,KeyType=HASH}],Projection={ProjectionType=ALL}' \
  --billing-mode PAY_PER_REQUEST >/dev/null

awslocal dynamodb create-table --table-name "${P}-projects" \
  --attribute-definitions AttributeName=projectId,AttributeType=S AttributeName=ownerId,AttributeType=S AttributeName=createdAt,AttributeType=S \
  --key-schema AttributeName=projectId,KeyType=HASH \
  --global-secondary-indexes 'IndexName=owner-index,KeySchema=[{AttributeName=ownerId,KeyType=HASH},{AttributeName=createdAt,KeyType=RANGE}],Projection={ProjectionType=ALL}' \
  --billing-mode PAY_PER_REQUEST >/dev/null

awslocal dynamodb create-table --table-name "${P}-apikeys" \
  --attribute-definitions AttributeName=keyHash,AttributeType=S AttributeName=projectId,AttributeType=S \
  --key-schema AttributeName=keyHash,KeyType=HASH \
  --global-secondary-indexes 'IndexName=project-index,KeySchema=[{AttributeName=projectId,KeyType=HASH}],Projection={ProjectionType=ALL}' \
  --billing-mode PAY_PER_REQUEST >/dev/null

awslocal dynamodb create-table --table-name "${P}-videos" \
  --attribute-definitions AttributeName=videoId,AttributeType=S AttributeName=projectId,AttributeType=S AttributeName=createdAt,AttributeType=S \
  --key-schema AttributeName=videoId,KeyType=HASH \
  --global-secondary-indexes 'IndexName=project-index,KeySchema=[{AttributeName=projectId,KeyType=HASH},{AttributeName=createdAt,KeyType=RANGE}],Projection={ProjectionType=ALL}' \
  --billing-mode PAY_PER_REQUEST >/dev/null

echo "[init] Hoàn tất"
__END__

write local/localstack/init/cors.json <<'__END__'
{
  "CORSRules": [
    {
      "AllowedOrigins": ["*"],
      "AllowedMethods": ["GET", "PUT", "POST", "HEAD"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag"]
    }
  ]
}
__END__

write local/nginx/README.md <<'__END__'
# local/nginx/

Giả lập CloudFront khi chạy local: một địa chỉ `http://localhost:8000` với 3 đường dẫn giống production.

| Path | Đích | Tương ứng cloud |
|---|---|---|
| `/` | Vite dev server trên máy (`localhost:5173`) | CloudFront -> S3 frontend |
| `/api/*` | container `backend:8080` | CloudFront -> EC2 API |
| `/media/*` | bucket `tvc-local-output` trên LocalStack | CloudFront -> S3 output (OAC) |

Frontend luôn gọi đường dẫn tương đối (`/api/...`, `/media/...`) để code giống hệt local và cloud.
__END__

write local/nginx/default.conf <<'__END__'
# Giả lập CloudFront. Dùng resolver + biến để nginx không chết khi service chưa chạy.
resolver 127.0.0.11 valid=10s;

server {
    listen 80;

    location /api/ {
        set $backend http://backend:8080;
        proxy_pass $backend;
        proxy_set_header Host $host;
        client_max_body_size 10m;
    }

    location /media/ {
        set $s3 http://localstack:4566;
        rewrite ^/media/(.*)$ /tvc-local-output/$1 break;
        proxy_pass $s3;
    }

    location / {
        set $web http://host.docker.internal:5173;
        proxy_pass $web;
        proxy_set_header Host $host;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
__END__

write local/scripts/README.md <<'__END__'
# local/scripts/

Script hỗ trợ kiểm thử local. Gọi qua `make`, ít khi chạy tay.

| Script | Mục đích |
|---|---|
| `smoke-test.sh` | Kiểm tra LocalStack đã có đủ bucket, queue, bảng |
| `seed.sh` | Tạo dữ liệu mẫu (user, project) cho demo/dev |
| `chaos-kill-worker.sh` | Gửi job, kill worker giữa chừng, kiểm tra retry và DLQ |

Lưu ý Git Bash: lệnh `docker exec` có đường dẫn kiểu `/abc` cần tiền tố `MSYS_NO_PATHCONV=1`.
__END__

write local/scripts/smoke-test.sh <<'__END__'
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
__END__

write local/scripts/seed.sh <<'__END__'
#!/usr/bin/env bash
# TODO (Phương/Vũ): tạo user + project mẫu qua API hoặc ghi thẳng DynamoDB local.
echo "seed.sh: chưa triển khai. Bổ sung khi API đăng ký/project hoạt động."
__END__

write local/scripts/chaos-kill-worker.sh <<'__END__'
#!/usr/bin/env bash
# TODO (Vũ): kịch bản
#   1. gửi 1 job (video dài ~30s)
#   2. docker compose kill worker khi job đang PROCESSING
#   3. start lại worker; sau visibility timeout job được nhận lại và COMPLETED
#   4. gửi message lỗi cố ý, xác nhận sau 3 lần nhận thì vào DLQ
echo "chaos-kill-worker.sh: chưa triển khai (epic A5/A8)."
__END__

# ----------------------------------------------------------------------------
# contracts/ : nguồn sự thật
# ----------------------------------------------------------------------------
write contracts/README.md <<'__END__'
# contracts/ - Hợp đồng tích hợp (nguồn sự thật duy nhất)

**Phụ trách:** Phương (chủ trì) - cả 4 người review. Đây là thư mục quan trọng nhất ở Sprint 1.

Backend, Worker, Frontend, Terraform và script local **đều bám vào đây**. Khi code và contract
lệch nhau, contract đúng, code sai (hoặc phải sửa contract trong cùng PR).

| File | Nội dung | Ai dùng |
|---|---|---|
| `openapi.yaml` | REST API cho SaaS Web và Developer API | Backend, Frontend |
| `sqs-job-message.schema.json` | Cấu trúc message job gửi qua SQS | Backend (gửi), Worker (nhận) |
| `video-state-machine.md` | Các trạng thái video và ai được phép chuyển | Backend, Worker, Frontend |
| `dynamodb-tables.md` | Key schema, GSI, truy vấn, ví dụ item | Backend, Worker, Terraform, local init |
| `s3-key-convention.md` | Quy ước đặt tên key input/output | Backend, Worker, CloudFront |
| `upload-limits.md` | Giới hạn upload cho MVP và nơi kiểm tra từng giới hạn | Backend, Worker, Frontend, Terraform |

## Quy trình sửa contract
1. Mở PR chỉ sửa `contracts/` (hoặc kèm code thích ứng), gắn nhãn `contract-change`.
2. Cần **duyệt của cả bên App và bên Cloud** (CODEOWNERS).
3. Ghi `schemaVersion` mới nếu thay đổi message SQS không tương thích ngược.
4. Báo trong nhóm chat trước khi merge.

Các file hiện là **DRAFT** để chốt trong ngày 1-2 của Sprint 1.
__END__

write contracts/video-state-machine.md <<'__END__'
# Video state machine (DRAFT)

```
UPLOADING --(API: confirm)--> PENDING --(Worker nhận job)--> PROCESSING --> COMPLETED
                                                                  |
                                                                  +--> FAILED_VALIDATION | FAILED_TRANSCODE | FAILED_STORAGE
```

| Chuyển trạng thái | Ai ghi | Ghi chú |
|---|---|---|
| (tạo mới) -> UPLOADING | API | Khi cấp presigned URL |
| UPLOADING -> PENDING | API | Khi Client gọi confirm; đồng thời gửi SQS |
| PENDING / PROCESSING / FAILED_* -> PROCESSING | Worker | Nhận job (kể cả giao lại/retry). `ConditionExpression`: `status <> COMPLETED` |
| PROCESSING -> COMPLETED | Worker | Ghi `assets`; **COMPLETED là trạng thái cuối, không bị ghi đè** |
| PROCESSING -> FAILED_* | Worker | Ghi `errorCode`; vẫn có thể được retry qua SQS |

Quy tắc:
- Message SQS giao lại (at-least-once): Worker thấy video đã `COMPLETED` thì bỏ qua và xóa message.
- Mỗi lần Worker nhận job, tăng `attemptCount`.
- IAM: API chỉ ghi nhóm trạng thái tiếp nhận; Worker chỉ ghi nhóm trạng thái xử lý.
__END__

write contracts/dynamodb-tables.md <<'__END__'
# DynamoDB tables (DRAFT)

Tên bảng: `tvc-{env}-{table}` (local: `tvc-local-*`). Billing: on-demand.

| Bảng | Partition key | GSI | Dùng cho |
|---|---|---|---|
| `users` | `userId` | `email-index` (PK `email`) | Đăng nhập theo email |
| `projects` | `projectId` | `owner-index` (PK `ownerId`, SK `createdAt`) | Liệt kê project của user; chứa `webhookUrl`, `webhookSecret` |
| `apikeys` | `keyHash` | `project-index` (PK `projectId`) | Xác thực API key (chỉ lưu hash) |
| `videos` | `videoId` | `project-index` (PK `projectId`, SK `createdAt`) | Liệt kê video theo project; nguồn sự thật về trạng thái |

## Trường chính của `videos`
`videoId`, `projectId`, `status`, `attemptCount`, `inputKey`, `assets` (map: `hls360`, `hls720`, `thumbnail`), `errorCode`, `createdAt`, `updatedAt`.

## Lưu ý
- Email duy nhất: dùng `TransactWriteItems` với item khóa `EMAIL#<email>` + `attribute_not_exists`.
- Phân trang bằng `LastEvaluatedKey` (cursor), không dùng offset.
- Thay đổi key schema phải sửa đồng thời: file này, `local/localstack/init/01-resources.sh`, Terraform module `database`.
__END__

write contracts/s3-key-convention.md <<'__END__'
# S3 key convention (DRAFT)

| Bucket | Key | Nội dung |
|---|---|---|
| `tvc-{env}-input` | `{projectId}/{videoId}/source.{ext}` | Video gốc, client PUT qua presigned URL |
| `tvc-{env}-output` | `{projectId}/{videoId}/hls/master.m3u8` | Master playlist |
| `tvc-{env}-output` | `{projectId}/{videoId}/hls/360p/index.m3u8` (+ segment `.ts`) | Rendition 360p |
| `tvc-{env}-output` | `{projectId}/{videoId}/hls/720p/index.m3u8` (+ segment `.ts`) | Rendition 720p |
| `tvc-{env}-output` | `{projectId}/{videoId}/thumb/default.jpg` | Thumbnail |
| `tvc-{env}-frontend` | build của React | Static site |

- Key **ổn định** (suy ra từ `projectId/videoId`): xử lý lại cùng job ghi đè đúng chỗ cũ, không sinh rác.
- Content-Type: `.m3u8` = `application/vnd.apple.mpegurl`, `.ts` = `video/mp2t`, `.jpg` = `image/jpeg`.
- URL phát: `/media/{projectId}/{videoId}/hls/master.m3u8` (qua CloudFront hoặc nginx local).
__END__

write contracts/upload-limits.md <<'__END__'
# Giới hạn upload cho MVP (DRAFT)

| Giới hạn | Giá trị mặc định | Biến cấu hình | Kiểm tra ở |
|---|---|---|---|
| Dung lượng tối đa | 200 MB (209715200 byte) | `MAX_UPLOAD_BYTES` | Frontend, API (khi khai báo), API (`confirm` bằng HeadObject) |
| Thời lượng tối đa | 5 phút (300 giây) | `MAX_DURATION_SECONDS` | Worker (ffprobe) |
| Độ phân giải tối đa | 1080p (chiều cao 1080) | `MAX_HEIGHT_PIXELS` | Worker (ffprobe) |
| Định dạng | mp4, mov, mkv, webm | (cố định trong code) | Frontend (gợi ý), Worker (ffprobe quyết định, không tin phần mở rộng) |
| Hết hạn presigned URL | 15 phút | (cố định) | API |
| Lưu giữ video gốc | 7 ngày | Terraform (lifecycle bucket input) | S3 |

## Luồng kiểm tra
1. Frontend: chặn sớm file quá lớn hoặc sai định dạng (chỉ để trải nghiệm tốt hơn, không phải lớp bảo vệ).
2. API `POST videos`: client khai báo `sizeBytes`, `contentType`; vượt giới hạn thì trả lỗi 413/422. Ký presigned URL kèm Content-Length đã khai báo.
3. API `POST confirm`: HeadObject xác nhận object có thật và dung lượng không vượt giới hạn, rồi mới chuyển `PENDING`.
4. Worker: ffprobe kiểm tra thời lượng, độ phân giải, codec; sai thì ghi `FAILED_VALIDATION` kèm `errorCode`.
5. S3 lifecycle: xóa video gốc sau 7 ngày.

## Ghi chú
- Presigned PUT không tự giới hạn kích thước; Content-Length đã ký cần được thử nghiệm để chắc S3 từ chối file sai kích thước.
- 200 MB đủ nhỏ để upload bằng một lần PUT, không cần multipart.
- Muốn đổi giới hạn: sửa file này, `.env.example` và biến cấu hình trên cloud trong cùng PR.
__END__

write contracts/sqs-job-message.schema.json <<'__END__'
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "TransVidCloud job message (DRAFT)",
  "type": "object",
  "required": ["schemaVersion", "jobId", "videoId", "projectId", "inputKey"],
  "additionalProperties": false,
  "properties": {
    "schemaVersion": { "type": "integer", "const": 1 },
    "jobId": { "type": "string" },
    "videoId": { "type": "string" },
    "projectId": { "type": "string" },
    "inputKey": { "type": "string" },
    "requestedAt": { "type": "string", "format": "date-time" }
  }
}
__END__

write contracts/openapi.yaml <<'__END__'
# DRAFT - Phương hoàn thiện ở Sprint 1. Chỉ liệt kê khung endpoint.
openapi: 3.0.3
info:
  title: TransVidCloud API
  version: 0.1.0
servers:
  - url: /api
paths:
  /auth/register: { post: { summary: Đăng ký, responses: { "201": { description: Created } } } }
  /auth/login:    { post: { summary: Đăng nhập (JWT), responses: { "200": { description: OK } } } }
  /projects:
    get:  { summary: Liệt kê project của user, responses: { "200": { description: OK } } }
    post: { summary: Tạo project, responses: { "201": { description: Created } } }
  /projects/{projectId}/api-keys:
    post: { summary: Tạo API key, responses: { "201": { description: Created } } }
  /projects/{projectId}/videos:
    get:  { summary: Liệt kê video (cursor), responses: { "200": { description: OK } } }
    post: { summary: Khởi tạo video và nhận presigned URL, responses: { "201": { description: Created } } }
  /videos/{videoId}/confirm:
    post: { summary: Xác nhận đã upload (UPLOADING -> PENDING), responses: { "202": { description: Accepted } } }
  /videos/{videoId}:
    get: { summary: Chi tiết video, trạng thái, assets, responses: { "200": { description: OK } } }
__END__

# ----------------------------------------------------------------------------
# backend/
# ----------------------------------------------------------------------------
write backend/README.md <<'__END__'
# backend/ - API (Java, Spring Boot)

**Phụ trách:** Phương (App A).

Phục vụ cả SaaS Web (JWT) và Developer API (API key). Chỉ tiếp nhận yêu cầu, ghi metadata,
cấp presigned URL và đưa job vào SQS. **Không bao giờ chạy FFmpeg** và không trung chuyển video.

## Cấu trúc dự kiến
```
src/main/java/.../
  auth/       JWT, API key, filter xác thực
  project/    CRUD project, API key, cấu hình webhook
  video/      khởi tạo video, presigned URL, confirm, truy vấn trạng thái
  job/        gửi message SQS theo contracts/sqs-job-message.schema.json
  webhook/    callback khi COMPLETED/FAILED (P1)
  config/     AWS client (endpoint override cho LocalStack), bảo mật
src/test/     unit + integration (Testcontainers / LocalStack)
Dockerfile
```

## Quy tắc
- Chỉ ghi trạng thái `UPLOADING -> PENDING`. Trạng thái xử lý thuộc về Worker.
- Đọc `AWS_ENDPOINT_URL` (nội bộ) và `S3_PRESIGN_ENDPOINT` (URL cho trình duyệt) từ biến môi trường.
- API phải khớp `contracts/openapi.yaml`. Thay đổi API = sửa contract trước.
- Dùng AWS SDK v2 (DynamoDB Enhanced Client). Không thêm CSDL khác (xem `docs/adr/0001-dynamodb-only.md`).
- Giới hạn upload: kiểm tra `sizeBytes` khi khai báo và HeadObject ở `confirm` (xem `contracts/upload-limits.md`).
- Cấu hình CORS qua biến môi trường (chỉ cần khi frontend và API khác origin).
- Dockerfile do Lưu viết, bạn cung cấp: lệnh chạy, cổng, biến môi trường.
__END__

# ----------------------------------------------------------------------------
# worker/
# ----------------------------------------------------------------------------
write worker/README.md <<'__END__'
# worker/ - Xử lý video (Go + FFmpeg)

**Phụ trách:** Vũ (App B).

Nhận job từ SQS, tải video gốc từ S3 input, chuyển mã HLS 360p/720p + thumbnail bằng FFmpeg,
ghi kết quả lên S3 output, cập nhật trạng thái DynamoDB có điều kiện.

## Cấu trúc dự kiến
```
cmd/worker/      main: vòng lặp consumer SQS (long polling, gia hạn visibility)
cmd/metricpub/   đẩy metric BacklogPerInstance lên CloudWatch (Nguyên triển khai)
internal/
  queue/         nhận/xóa/gia hạn message SQS
  transcode/     gọi FFmpeg, sinh HLS + thumbnail
  storage/       Get/Put S3
  store/         UpdateItem DynamoDB có điều kiện
  config/        đọc biến môi trường
testdata/        video mẫu ngắn 5-10s (được phép commit)
Dockerfile       image có sẵn ffmpeg
```

## Quy tắc
- **At-least-once:** job có thể bị giao lại. Dùng output key ổn định; video đã `COMPLETED` thì bỏ qua và xóa message.
- Chỉ ghi nhóm trạng thái xử lý (`PROCESSING`, `COMPLETED`, `FAILED_*`).
- Không phụ thuộc cấu trúc nội bộ của backend, chỉ phụ thuộc `contracts/`.
- Công thức `BacklogPerInstance = messages / max(1, workers)` viết thành hàm thuần và có unit test.
- Bắt đầu bằng POC FFmpeg chạy trên file local, độc lập AWS.
- Tối ưu chi phí: giải mã một lần, tạo 360p/720p bằng filter `split`, preset `veryfast`, không tạo 1080p; dọn thư mục tạm sau mỗi job.
- Kiểm tra ffprobe (thời lượng, độ phân giải, codec) theo `contracts/upload-limits.md`, sai thì `FAILED_VALIDATION`.
- Chạy trên Spot: bắt SIGTERM, dừng nhận job mới và trả message về queue (không xóa message dang dở).
__END__

write worker/testdata/README.md <<'__END__'
# worker/testdata/

Video mẫu ngắn (5-10 giây, vài trăm KB) cho unit/e2e test. Chỉ commit file nhỏ.
Các video khác bị `.gitignore` chặn.
__END__

# ----------------------------------------------------------------------------
# frontend/
# ----------------------------------------------------------------------------
write frontend/README.md <<'__END__'
# frontend/ - SaaS Web (React + Vite)

**Phụ trách:** Vũ (Lưu hỗ trợ hosting lên S3/CloudFront). Cần từ Sprint 2.

Giao diện tối thiểu: đăng nhập, quản lý project, upload bằng presigned URL, danh sách + trạng thái video,
trình phát HLS (hls.js), lấy link streaming và mã nhúng.

## Cấu trúc dự kiến
```
src/pages/    trang: login, projects, videos, video detail
src/api/      client gọi /api/* (sinh/viết theo contracts/openapi.yaml)
src/player/   HLS player và mã nhúng
```

## Quy tắc
- Luôn gọi **đường dẫn tương đối** (`/api/...`, `/media/...`) để code giống nhau ở local (nginx) và cloud (CloudFront).
- Upload: xin presigned URL từ API, PUT thẳng lên S3, rồi gọi `confirm`. Video không đi qua API.
- Chạy dev: `npm run dev` (cổng 5173), truy cập qua `http://localhost:8000` khi bật stack `full`.
__END__

# ----------------------------------------------------------------------------
# infra/
# ----------------------------------------------------------------------------
write infra/README.md <<'__END__'
# infra/ - Hạ tầng AWS dưới dạng mã

**Phụ trách:** Cloud (Lưu: dữ liệu/mạng; Nguyên: IAM/compute/edge/vận hành).

Toàn bộ hạ tầng AWS được tạo bằng Terraform, **không tạo tay trên console** (trừ bước bootstrap state).
Xem `terraform/README.md`.
__END__

write infra/terraform/README.md <<'__END__'
# infra/terraform/

```
modules/   khối tái sử dụng (mỗi thư mục một nhóm tài nguyên)
envs/      dev, demo: gọi cùng modules, chỉ khác biến cấu hình
```

## Quy ước
- Tên tài nguyên: `tvc-{env}-{resource}`; region `ap-southeast-1`.
- Remote state trong S3 (Terraform >= 1.10 hỗ trợ khóa state bằng S3, `use_lockfile`).
- Không commit `*.tfstate`, `*.tfvars` (chỉ `*.tfvars.example`).
- Chạy: `cd envs/dev && terraform init && terraform plan`; apply sau khi review plan.
- Key schema bảng DynamoDB, cấu hình SQS phải khớp `contracts/` và `local/localstack/init`.
__END__

write infra/terraform/modules/README.md <<'__END__'
# infra/terraform/modules/

| Module | Tài nguyên | Chủ |
|---|---|---|
| `storage` | 3 bucket S3 (frontend/input/output), CORS, chặn public | Lưu |
| `queue` | SQS + DLQ (visibility 300s, maxReceiveCount 3), alarm DLQ | Lưu |
| `database` | 4 bảng DynamoDB + GSI | Lưu |
| `network` | VPC, public subnet, S3 Gateway Endpoint | Lưu |
| `iam` | Role riêng cho API, Worker, CI (least privilege) | Nguyên |
| `compute` | EC2 API, Launch Template + ASG Worker (1-4) | Nguyên |
| `edge` | CloudFront (3 behavior), OAC, hosting frontend | Nguyên (Lưu hỗ trợ hosting) |
| `observability` | CloudWatch log/alarm, SNS, AWS Budgets, scaling policy | Nguyên |

Mỗi module có `main.tf`, `variables.tf`, `outputs.tf`. Module chỉ nhận đầu vào qua biến, không đọc tên môi trường cứng.
__END__

write infra/terraform/modules/.gitkeep </dev/null
write infra/terraform/envs/README.md <<'__END__'
# infra/terraform/envs/

- `dev/`: môi trường phát triển, dùng hằng ngày.
- `demo/`: môi trường trình diễn; dựng trước buổi demo, **hủy bằng `terraform destroy` sau khi dùng** để tiết kiệm chi phí.

Hai môi trường dùng chung mã trong `../modules`, chỉ khác file biến (`terraform.tfvars`, không commit).
__END__
write infra/terraform/envs/dev/.gitkeep </dev/null
write infra/terraform/envs/demo/.gitkeep </dev/null

# ----------------------------------------------------------------------------
# scripts/ : dùng chung local + cloud
# ----------------------------------------------------------------------------
write scripts/README.md <<'__END__'
# scripts/ - Script triển khai dùng chung

**Phụ trách:** Nguyên (cloud/vận hành).

Được GitHub Actions gọi (và chạy tay được khi cần).

| Script | Mục đích |
|---|---|
| `deploy-api.sh` | Triển khai image API mới lên EC2 qua SSM Run Command |
| `bump-worker-tag.sh` | Ghi tag image Worker vào SSM Parameter Store, kích hoạt ASG Instance Refresh |

Yêu cầu: AWS CLI đã đăng nhập (OIDC trên CI, hoặc profile cá nhân khi chạy tay).
__END__

write scripts/deploy-api.sh <<'__END__'
#!/usr/bin/env bash
# Dùng: ENV=dev TAG=<commit-sha> ECR_REPO=<acct>.dkr.ecr.ap-southeast-1.amazonaws.com/tvc-api ./scripts/deploy-api.sh
set -euo pipefail
: "${ENV:?cần ENV}" "${TAG:?cần TAG}" "${ECR_REPO:?cần ECR_REPO}"
REGION=${AWS_REGION:-ap-southeast-1}

aws ssm send-command \
  --region "$REGION" \
  --document-name "AWS-RunShellScript" \
  --targets "Key=tag:Name,Values=tvc-${ENV}-api" \
  --comment "deploy api ${TAG}" \
  --parameters "commands=[
    \"aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ECR_REPO%%/*}\",
    \"docker pull ${ECR_REPO}:${TAG}\",
    \"docker rm -f tvc-api || true\",
    \"docker run -d --name tvc-api --restart unless-stopped -p 8080:8080 -e SPRING_PROFILES_ACTIVE=${ENV} ${ECR_REPO}:${TAG}\"
  ]"
# TODO (Nguyên):
#   - đọc cấu hình/secret từ /tvc/${ENV}/... trong Parameter Store; thêm health check + rollback
#   - chạy thêm container cloudflared (--network host) với token lấy từ /tvc/${ENV}/cloudflared/token (SecureString)
#   - cho container API nghe ở 127.0.0.1:8080 vì API không mở inbound ra internet
__END__

write scripts/bump-worker-tag.sh <<'__END__'
#!/usr/bin/env bash
# Dùng: ENV=dev TAG=<commit-sha> ./scripts/bump-worker-tag.sh
set -euo pipefail
: "${ENV:?cần ENV}" "${TAG:?cần TAG}"
REGION=${AWS_REGION:-ap-southeast-1}

aws ssm put-parameter --region "$REGION" \
  --name "/tvc/${ENV}/worker/image-tag" --type String --value "$TAG" --overwrite

aws autoscaling start-instance-refresh --region "$REGION" \
  --auto-scaling-group-name "tvc-${ENV}-worker-asg" \
  --preferences '{"MinHealthyPercentage":50,"ScaleInProtectedInstances":"Wait"}'
# ScaleInProtectedInstances=Wait: không thay máy đang transcode (đang bật scale-in protection), chờ job xong.
__END__

# ----------------------------------------------------------------------------
# tests/, docs/, .github/
# ----------------------------------------------------------------------------
write tests/e2e/README.md <<'__END__'
# tests/e2e/ - Kiểm thử đầu-cuối

**Phụ trách:** Lưu chủ trì (Vũ, Phương viết kịch bản), cả nhóm bổ sung.

Kịch bản chính: đăng ký -> tạo project -> xin presigned URL -> upload video mẫu -> confirm -> chờ `COMPLETED`
-> kiểm tra có `master.m3u8`, 360p/720p, thumbnail -> phát được qua `/media/*`.

Chạy được ở hai chế độ qua biến `BASE_URL`: local (`http://localhost:8000`) và cloud (domain CloudFront).
Cùng một bộ test, hai môi trường: đó là cách chứng minh "local chạy được thì cloud chạy được".
__END__

write docs/README.md <<'__END__'
# docs/ - Tài liệu

| Mục | Nội dung |
|---|---|
| `LOCAL_AND_CICD.md` | Chạy local, kiểm thử, rồi CI/CD lên AWS thật |
| `EPICS.md` | Các epic của nhóm App và nhóm Cloud, checklist theo sprint |
| `adr/` | Architecture Decision Records: ghi lại các quyết định và đánh đổi |
| `report/` | Mã nguồn báo cáo LaTeX, hình ảnh |

Quy tắc: quyết định kiến trúc quan trọng (đổi CSDL, bỏ CloudFront, đổi cách co giãn...) phải có một ADR ngắn.
__END__

write docs/adr/README.md <<'__END__'
# docs/adr/

Mỗi quyết định một file `NNNN-ten-ngan.md`, gồm: Bối cảnh, Quyết định, Hệ quả, Phương án đã loại.
Không sửa ADR cũ; nếu đổi ý, tạo ADR mới đánh dấu thay thế ADR trước.
__END__

write docs/adr/0001-dynamodb-only.md <<'__END__'
# ADR 0001: Chỉ dùng DynamoDB làm CSDL

**Trạng thái:** Chấp nhận

## Bối cảnh
Hệ thống có Backend (Spring Boot) và Worker (Go) cùng đọc/ghi metadata video, user, project, API key.

## Quyết định
Dùng DynamoDB (on-demand) cho toàn bộ dữ liệu. Không dùng PostgreSQL/RDS.

## Lý do
- Truy vấn đơn giản, cố định, không cần join hay báo cáo ad-hoc.
- Worker cập nhật trạng thái bằng `UpdateItem` có điều kiện, khớp thiết kế chống ghi đè `COMPLETED`.
- Worker chỉ cần IAM role, không cần chuỗi kết nối/Security Group; giữ được mạng đơn giản (không NAT, không private subnet cho DB).
- Chi phí gần 0 ở quy mô demo; LocalStack hỗ trợ sẵn khi chạy local.

## Hệ quả
- Phải thiết kế key/GSI theo access pattern (`contracts/dynamodb-tables.md`).
- Email duy nhất cần giao dịch ghi hai item.
- Nếu sau này cần báo cáo phức tạp: xuất dữ liệu sang Athena thay vì thêm CSDL quan hệ.

## Phương án đã loại
PostgreSQL/RDS cho tất cả; mô hình lai (Postgres + DynamoDB) vì tăng gấp đôi migration, test và nguy cơ sai lệch dữ liệu.
__END__

write docs/report/README.md <<'__END__'
# docs/report/

Báo cáo LaTeX của môn học (báo cáo tiến độ, báo cáo cuối kỳ), hình ảnh và slide demo.
Mỗi người cập nhật phần mình phụ trách; báo cáo cuối phải phản ánh **kiến trúc triển khai thực tế**.
__END__

write .github/README.md <<'__END__'
# .github/

- `workflows/ci.yml`: chạy khi mở PR và khi push `main`; kiểm tra build/test từng module **nếu module đã có code**.
- `workflows/deploy.yml`: triển khai lên AWS (chạy tay `workflow_dispatch`, chọn môi trường). Cần hoàn tất epic C2/C6 trước.
- `CODEOWNERS`: yêu cầu người phụ trách duyệt PR theo thư mục.

Thay `@github-user-*` trong `CODEOWNERS` bằng tên GitHub thật của từng người.
__END__

write .github/CODEOWNERS <<'__END__'
# Thay các handle bên dưới bằng tên GitHub thật.
# App: Phương = backend | Vũ = worker + frontend. Cloud: Lưu = local/Docker/dữ liệu | Nguyên = compute/edge/vận hành
/contracts/        @github-user-phuong @github-user-vu @github-user-luu @github-user-nguyen
/backend/          @github-user-phuong
/worker/           @github-user-vu
/frontend/         @github-user-vu @github-user-phuong
/infra/            @github-user-luu @github-user-nguyen
/local/            @github-user-luu @github-user-nguyen
/scripts/          @github-user-nguyen
/.github/          @github-user-nguyen
__END__

write .github/workflows/ci.yml <<'__END__'
name: CI
on:
  pull_request:
  push:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - if: ${{ hashFiles('backend/pom.xml') != '' }}
        uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - if: ${{ hashFiles('backend/pom.xml') != '' }}
        run: cd backend && ./mvnw -B verify

  worker:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - if: ${{ hashFiles('worker/go.mod') != '' }}
        uses: actions/setup-go@v5
        with: { go-version-file: worker/go.mod }
      - if: ${{ hashFiles('worker/go.mod') != '' }}
        run: cd worker && go vet ./... && go test ./...

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - if: ${{ hashFiles('frontend/package.json') != '' }}
        uses: actions/setup-node@v4
        with: { node-version: '20', cache: npm, cache-dependency-path: frontend/package-lock.json }
      - if: ${{ hashFiles('frontend/package.json') != '' }}
        run: cd frontend && npm ci && npm run build --if-present && npm test --if-present

  terraform:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - if: ${{ hashFiles('infra/terraform/envs/dev/*.tf') != '' }}
        uses: hashicorp/setup-terraform@v3
      - if: ${{ hashFiles('infra/terraform/envs/dev/*.tf') != '' }}
        run: |
          terraform fmt -check -recursive infra/terraform
          cd infra/terraform/envs/dev && terraform init -backend=false && terraform validate
__END__

write .github/workflows/deploy.yml <<'__END__'
# Triển khai thủ công lên AWS. Chỉ dùng sau khi hoàn tất: hạ tầng Terraform, role OIDC, repo ECR.
# Cần cấu hình trong GitHub (Settings > Secrets and variables > Actions > Variables):
#   AWS_DEPLOY_ROLE_ARN, ECR_REGISTRY, FRONTEND_BUCKET, CLOUDFRONT_DISTRIBUTION_ID
name: Deploy
on:
  workflow_dispatch:
    inputs:
      env:
        description: Môi trường
        type: choice
        options: [dev, demo]
        default: dev

permissions:
  id-token: write   # OIDC: không lưu access key trong GitHub
  contents: read

env:
  AWS_REGION: ap-southeast-1
  TAG: ${{ github.sha }}

jobs:
  build-push:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        component: [backend, worker]
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ vars.AWS_DEPLOY_ROLE_ARN }}
          aws-region: ${{ env.AWS_REGION }}
      - uses: aws-actions/amazon-ecr-login@v2
      - name: Build và push image (tag = commit SHA)
        run: |
          IMAGE=${{ vars.ECR_REGISTRY }}/tvc-${{ matrix.component }}
          docker build -t "$IMAGE:$TAG" ${{ matrix.component }}
          docker push "$IMAGE:$TAG"

  deploy-api:
    needs: build-push
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ vars.AWS_DEPLOY_ROLE_ARN }}
          aws-region: ${{ env.AWS_REGION }}
      - run: ENV=${{ inputs.env }} ECR_REPO=${{ vars.ECR_REGISTRY }}/tvc-backend ./scripts/deploy-api.sh

  deploy-worker:
    needs: build-push
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ vars.AWS_DEPLOY_ROLE_ARN }}
          aws-region: ${{ env.AWS_REGION }}
      - run: ENV=${{ inputs.env }} ./scripts/bump-worker-tag.sh

  deploy-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ vars.AWS_DEPLOY_ROLE_ARN }}
          aws-region: ${{ env.AWS_REGION }}
      - run: |
          cd frontend && npm ci && npm run build
          aws s3 sync dist "s3://${{ vars.FRONTEND_BUCKET }}" --delete
          aws cloudfront create-invalidation --distribution-id "${{ vars.CLOUDFRONT_DISTRIBUTION_ID }}" --paths "/*"
__END__

# ----------------------------------------------------------------------------
# README gốc: hướng dẫn cấu trúc
# ----------------------------------------------------------------------------
write README.md <<'__END__'
# TransVidCloud

Nền tảng xử lý và phân phối video trên AWS (môn Điện toán đám mây, UET-VNU). Một backend, hai cách dùng:
**SaaS Web** cho người dùng cuối và **Developer API** cho hệ thống bên ngoài; cùng dùng chung pipeline xử lý video.

```
Client -> CloudFront -> Cloudflare Tunnel -> EC2 API (Spring Boot) -> SQS -> Worker ASG (Go + FFmpeg) -> S3 output -> CloudFront -> người xem
                              |                                  |
                              +---------- DynamoDB <-------------+
```

## Bắt đầu nhanh
```bash
cp .env.example .env     # rồi điền LOCALSTACK_AUTH_TOKEN (xem docs/LOCAL_AND_CICD.md, mục 1.1)
make infra      # LocalStack: S3, SQS+DLQ, DynamoDB
make smoke      # kiểm tra tài nguyên đã tạo
```
Chi tiết (chạy full stack, kiểm thử, CI/CD lên AWS): [`docs/LOCAL_AND_CICD.md`](docs/LOCAL_AND_CICD.md)
Việc cần làm của từng nhóm: [`docs/EPICS.md`](docs/EPICS.md)

## Cấu trúc repo
```
transvidcloud/
├── README.md              (file này)
├── Makefile               lệnh dùng hằng ngày: make help
├── docker-compose.yml     local stack (LocalStack, backend, worker, nginx)
├── .env.example           biến môi trường mẫu
├── contracts/             HỢP ĐỒNG tích hợp: API, message SQS, state, bảng, S3 key
├── backend/               Spring Boot API
├── worker/                Go worker + FFmpeg (+ metricpub)
├── frontend/              React SaaS Web
├── infra/terraform/       hạ tầng AWS (modules + envs dev/demo)
├── local/                 chỉ phục vụ chạy local: init LocalStack, nginx, script test
├── scripts/               script deploy dùng chung (SSM, Instance Refresh)
├── tests/e2e/             test đầu-cuối chạy được cả local lẫn cloud
├── docs/                  tài liệu, ADR, báo cáo
└── .github/               CI/CD, CODEOWNERS
```

| Thư mục | Làm gì | Chủ |
|---|---|---|
| `contracts/` | Nguồn sự thật cho mọi giao diện giữa các thành phần | Phương (cả nhóm review) |
| `backend/` | API, xác thực, project/video, presigned URL, đẩy job vào SQS | Phương |
| `worker/` | Chuyển mã HLS 360p/720p, thumbnail, cập nhật trạng thái | Vũ |
| `frontend/` | Giao diện SaaS tối thiểu, trình phát HLS | Vũ |
| `infra/` | Terraform tạo S3, SQS, DynamoDB, VPC, IAM, EC2/ASG, CloudFront, CloudWatch | Lưu + Nguyên |
| `local/` + Docker | Giả lập AWS trên máy, Dockerfile, compose; đường đi local -> cloud | Lưu |
| `scripts/` | Deploy API (SSM) và Worker (Instance Refresh) | Nguyên |
| `tests/e2e/` | Kịch bản upload -> COMPLETED, chạy cả local và cloud | Lưu (App viết kịch bản) |
| `docs/` | Hướng dẫn, ADR, báo cáo LaTeX | Cả nhóm |
| `.github/` | Pipeline CI/CD, quy định duyệt PR | Nguyên |

Mỗi thư mục có `README.md` riêng mô tả trách nhiệm và quy tắc.

## Nguyên tắc làm việc
1. **Contract-first:** muốn đổi API, message, bảng hay S3 key thì sửa `contracts/` trước, cần cả bên App và Cloud duyệt.
2. **Local trước, cloud sau:** chỉ đẩy lên AWS khi luồng chính đã chạy được trên local.
3. **Cùng code, khác cấu hình:** local và cloud chỉ khác biến môi trường (`AWS_ENDPOINT_URL` có giá trị = LocalStack, để trống = AWS thật).
4. **Hạ tầng là mã:** mọi thay đổi AWS qua Terraform, không sửa tay trên console.
5. **Không commit bí mật:** `.env`, `*.tfvars`, access key. Secret trên cloud nằm ở SSM Parameter Store.

## Quy ước Git
- Nhánh: `main` (ổn định), nhánh làm việc `feature/<module>-<mô-tả>` hoặc theo tên cá nhân.
- Mọi thay đổi vào `main` qua Pull Request; CODEOWNERS yêu cầu người phụ trách thư mục duyệt.
- Commit ngắn gọn theo dạng `module: nội dung` (vd: `worker: thêm transcode 720p`).
__END__

# ----------------------------------------------------------------------------
# docs/LOCAL_AND_CICD.md
# ----------------------------------------------------------------------------
write docs/LOCAL_AND_CICD.md <<'__END__'
# Triển khai local và CI/CD lên cloud

Mục tiêu: **chạy và kiểm thử hết trên máy, sau đó đẩy lên AWS bằng pipeline có kiểm soát**,
để lỗi còn lại trên cloud chỉ là lỗi IAM, mạng hoặc cấu hình.

---

## Phần 1. Chạy local

### 1.1. Cài đặt (Windows + Git Bash)

| Công cụ | Ghi chú |
|---|---|
| Docker Desktop (backend WSL2) | Bắt buộc |
| Git Bash | Đã có |
| `make` | Git Bash **không có sẵn**: `winget install GnuWin32.Make` hoặc `choco install make`. Có thể chạy thẳng `docker compose` nếu không muốn cài |
| JDK 21 | Cho backend (Phương) |
| Go (bản trong `worker/go.mod`) + FFmpeg | Cho worker (Vũ); FFmpeg cũng có trong image Docker |
| Node 20 | Cho frontend |
| AWS CLI, Terraform >= 1.10 | Cho nhóm cloud |
| **LocalStack auth token** | Từ 23/03/2026 image LocalStack yêu cầu token (có gói free phi thương mại). Mỗi người đăng ký tại app.localstack.cloud, dán vào `LOCALSTACK_AUTH_TOKEN` trong `.env`. Nếu không muốn dùng token, xem phương án thay thế ở Phần 3 |

### 1.2. Khởi động nhanh
```bash
cp .env.example .env
make infra      # chỉ LocalStack (S3, SQS+DLQ, DynamoDB), khoảng 20-30 giây
make smoke      # phải báo OK cho 3 bucket, 2 queue, 4 bảng
```

### 1.3. Ba chế độ chạy

| Chế độ | Lệnh | Khi nào dùng |
|---|---|---|
| Chỉ hạ tầng | `make infra` | Dev backend/worker bằng IDE, chạy trực tiếp trên máy, trỏ `AWS_ENDPOINT_URL=http://localhost:4566` |
| Toàn bộ stack | `make up` | Kiểm thử tích hợp, demo: LocalStack + backend + worker + nginx tại `http://localhost:8000` |
| Nhiều worker | `make worker-scale N=3` | Kiểm tra nhiều worker cùng nhận job từ queue |

Lệnh khác: `make down`, `make reset` (xóa sạch, dựng lại), `make logs S=worker`, `make help`.

### 1.4. Local và cloud tương ứng thế nào

| Cloud | Local | Ghi chú |
|---|---|---|
| S3 x3 bucket | LocalStack S3 | Có CORS cho bucket input |
| SQS + DLQ | LocalStack SQS | Cùng cấu hình: visibility 300s, long polling 20s, `maxReceiveCount=3` |
| DynamoDB | LocalStack DynamoDB | Bảng tạo theo `contracts/dynamodb-tables.md` |
| CloudFront | nginx (`localhost:8000`) | `/`, `/api/*`, `/media/*` |
| EC2 API, ASG Worker | Container `backend`, `worker` | Co giãn local chỉ giả lập bằng `--scale` |
| CloudWatch, Auto Scaling, IAM, SSM | **Không giả lập** | Kiểm thử trên AWS thật (Sprint 3-4) |

### 1.5. Biến môi trường then chốt

| Biến | Local | Cloud |
|---|---|---|
| `AWS_ENDPOINT_URL` | `http://localhost:4566` (hoặc `http://localstack:4566` trong container) | **để trống** |
| `S3_PRESIGN_ENDPOINT` | `http://localhost:4566` | để trống (dùng endpoint S3 thật) |
| Credential | `test` / `test` | IAM Role gắn vào EC2 |
| Tên tài nguyên | `tvc-local-*` | `tvc-{env}-*` |

> **Bẫy hay gặp: hai endpoint cho S3.** Backend gọi S3 qua `localstack:4566` (nội bộ container),
> nhưng presigned URL gửi cho trình duyệt phải là `localhost:4566`. Vì vậy phải tách hai biến trên
> và dùng path-style addressing khi chạy local.

### 1.6. Các tầng kiểm thử local

| Tầng | Lệnh | Kiểm tra gì |
|---|---|---|
| Unit | `make test` | Logic từng module (vd: công thức `BacklogPerInstance`) |
| Smoke | `make smoke` | LocalStack có đủ tài nguyên |
| E2E | `make e2e` | upload -> job -> HLS 360p/720p + thumbnail -> `COMPLETED` -> phát qua `/media/*` |
| Chaos | `make chaos` | Kill worker giữa chừng -> job được nhận lại; lỗi 3 lần -> vào DLQ |

### 1.7. Tiêu chí "sẵn sàng đẩy lên cloud"
- [ ] `make e2e` pass trên local
- [ ] Kịch bản retry/DLQ pass
- [ ] Contract không còn thay đổi chưa được duyệt
- [ ] Image backend và worker build được bằng `docker build`
- [ ] Terraform `plan` sạch cho môi trường `dev`

---

## Phần 2. Lên cloud (AWS)

### 2.1. Tổng quan luồng

```
Developer --PR--> CI (build, test, validate)
                    | merge main
                    v
          Deploy (chạy tay, chọn env)
   build image -> ECR (tag = commit SHA)
        |-> API:    SSM Run Command -> EC2 pull image mới
        |-> Worker: ghi tag vào SSM Parameter Store -> ASG Instance Refresh
        '-> Frontend: build -> S3 sync -> CloudFront invalidation
```

Hạ tầng (S3, SQS, DynamoDB, EC2, ASG, CloudFront...) **do Terraform tạo**, tách riêng khỏi pipeline deploy ứng dụng.

### 2.2. Thứ tự làm lần đầu

| Bước | Việc | Chủ |
|---|---|---|
| 1 | **Bootstrap state:** tạo tay một lần bucket S3 chứa Terraform state (bật versioning) | Lưu |
| 2 | `terraform apply` ở `envs/dev`: module `storage`, `queue`, `database`, `network` | Lưu |
| 3 | Thêm `iam`, `compute`: role riêng cho API/Worker/CI, EC2 API, Launch Template + ASG Worker | Nguyên |
| 4 | Tạo 2 repo ECR (`tvc-backend`, `tvc-worker`) và **OIDC provider + role cho GitHub Actions** | Nguyên |
| 5 | Khai báo biến GitHub Actions: `AWS_DEPLOY_ROLE_ARN`, `ECR_REGISTRY`, `FRONTEND_BUCKET`, `CLOUDFRONT_DISTRIBUTION_ID` | Nguyên |
| 6 | Đưa cấu hình/secret vào SSM Parameter Store (`/tvc/dev/...`) | Nguyên |
| 7 | Chạy workflow **Deploy** (env = dev), kiểm tra `tests/e2e` với `BASE_URL` trỏ domain CloudFront | Cả nhóm |
| 8 | Tạo Cloudflare Tunnel (thủ công một lần), thêm `edge` (CloudFront + OAC) và `observability` (CloudWatch, SNS, Budgets, scaling) | Nguyên (Lưu hỗ trợ hosting frontend) |

### 2.3. Hai pipeline

**CI (`ci.yml`) - tự chạy mỗi PR và push `main`:** build + test từng module đã có code (bước nào chưa có code sẽ tự bỏ qua),
`terraform fmt` + `validate`. Không có quyền AWS.

**Deploy (`deploy.yml`) - chạy tay, chọn `dev` hoặc `demo`:**
1. Lấy quyền AWS tạm thời qua **OIDC** (không lưu access key trong GitHub).
2. Build image backend/worker, gắn tag = commit SHA, đẩy lên ECR.
3. API: `scripts/deploy-api.sh` gửi lệnh qua SSM Run Command để EC2 kéo và chạy image mới.
4. Worker: `scripts/bump-worker-tag.sh` ghi tag mới vào Parameter Store rồi kích hoạt ASG Instance Refresh (thay máy dần dần).
5. Frontend: build React, `s3 sync` lên bucket frontend, invalidation CloudFront.

### 2.4. Môi trường

| Môi trường | Dùng để | Quản lý |
|---|---|---|
| `local` | Phát triển hằng ngày | Docker Compose |
| `dev` | Thử nghiệm trên AWS thật, benchmark | Terraform `envs/dev` |
| `demo` | Trình diễn, dựng trước buổi demo | Terraform `envs/demo`; **destroy sau khi dùng** |

### 2.5. Rollback
Image gắn tag theo commit SHA nên rollback = chạy lại Deploy với SHA cũ (hoặc `ENV=... TAG=<sha cũ>` cho từng script).
Thay đổi hạ tầng: `git revert` rồi `terraform apply`.

### 2.6. Bảo mật và chi phí
- Không commit bí mật; secret ở SSM Parameter Store, truy cập bằng IAM Role (least privilege, tách role API/Worker/CI).
- Không mở SSH; quản trị máy bằng SSM Session Manager.
- AWS Budgets cảnh báo ở 25 và 50 USD.
- Worker tối thiểu 1 máy; sau buổi thử nghiệm/demo hãy `terraform destroy` môi trường không dùng.

---

### 2.7. Tên miền và Cloudflare Tunnel
- DNS của tên miền đặt ở Cloudflare. **API** đi qua Cloudflare Tunnel: `cloudflared` chạy trên EC2 API và chủ động kết nối ra ngoài, nên EC2 **không cần mở inbound** và không cần chứng chỉ riêng.
- **Frontend và HLS** vẫn do CloudFront phân phối (S3 qua OAC). Với `/api/*`, CloudFront dùng origin là hostname của tunnel (ví dụ `origin-api.<domain>`), và không chuyển tiếp Host của người xem.
- Các bước (Nguyên): tạo tunnel trong Cloudflare, lưu token vào Parameter Store (SecureString) `/tvc/{env}/cloudflared/token`, khai báo public hostname trỏ `http://localhost:8080`, chạy container `cloudflared` cùng API.
- Làm trước với tên `*.cloudfront.net`; gắn tên miền riêng sau (ACM ở us-east-1, CNAME ở Cloudflare dạng DNS only).
- Local không cần Cloudflare. Có thể dùng `cloudflared` tạm để chia sẻ bản local cho thành viên khác xem.
- Rủi ro và phương án thay thế (hai subdomain + CORS): xem `docs/EPICS.md` mục 8.1.

### 2.8. Giới hạn upload và chi phí Worker
- **Giới hạn upload MVP:** 200 MB, 5 phút, tối đa 1080p; kiểm tra ở frontend, API, `confirm`, Worker và lifecycle S3 (chi tiết `contracts/upload-limits.md`).
- **Worker rẻ:** Spot với nhiều loại máy nhỏ, min 0 khi không dùng và min 1 khi demo/benchmark, EBS gp3 nhỏ, không NAT Gateway.
- **Benchmark:** dùng on-demand loại máy cố định (tránh CPU credits của `t3` làm sai số liệu).
- Kiểm tra **Service Quotas** (vCPU on-demand/Spot) ngay tuần đầu; tài khoản mới có thể bị giới hạn thấp.
- Sau mỗi đợt thử nghiệm: `terraform destroy` môi trường không dùng; theo dõi AWS Budgets.

## Phần 3. Xử lý sự cố thường gặp (Windows)

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| `make: command not found` | Git Bash không có make. Cài như mục 1.1 hoặc chạy `docker compose` trực tiếp |
| Script báo `\r: command not found` | File bị CRLF. Đã có `.gitattributes` ép LF; chạy `git add --renormalize .` hoặc `dos2unix` |
| `docker exec ... /path` bị đổi thành `C:/Program Files/Git/...` | Thêm tiền tố `MSYS_NO_PATHCONV=1` |
| Script init của LocalStack không chạy | Kiểm tra `chmod +x local/localstack/init/01-resources.sh`; xem `make logs S=localstack` |
| Trình duyệt PUT lên presigned URL bị lỗi CORS / sai host | Kiểm tra `S3_PRESIGN_ENDPOINT=http://localhost:4566` và `cors.json` của bucket input |
| LocalStack báo thiếu/sai auth token, hoặc không pull được image | Điền `LOCALSTACK_AUTH_TOKEN` trong `.env`. Nếu nhóm không muốn dùng token: thay bằng MinIO (S3) + ElasticMQ (SQS) + DynamoDB Local (không cần token, code chỉ đổi endpoint nhưng `01-resources.sh` phải viết lại bằng `aws` CLI trỏ `--endpoint-url`). Các emulator mới (Floci, MiniStack) tuyên bố tương thích cổng 4566 nhưng nhóm chưa kiểm chứng, chỉ nên thử trước khi chọn |
| nginx không lên / 502 | Service đích chưa chạy (`backend`, Vite dev server cổng 5173). Khởi động service rồi thử lại |
| Trên cloud `/api/*` trả 502/403 qua CloudFront | Kiểm tra tunnel đang chạy trên EC2, hostname tunnel đúng, và CloudFront **không** chuyển tiếp Host của người xem |
| Upload bị S3 từ chối (403/SignatureDoesNotMatch) | Content-Length hoặc Content-Type lúc PUT khác với khai báo khi ký URL; hoặc file vượt giới hạn |
__END__

# ----------------------------------------------------------------------------
# docs/EPICS.md
# ----------------------------------------------------------------------------
write docs/EPICS.md <<'__END__'
# Epic và phân việc (bản v3, đã chốt phân công)

Hai nhóm: **App** (Vũ, Phương: backend, worker, frontend) và **Cloud** (Lưu, Nguyên: hạ tầng và triển khai từ local lên cloud).
Ưu tiên: **P0** = cốt lõi, **P1** = nâng cao, hoãn nếu chậm. Effort lấy từ Bảng 3.3 của báo cáo (pd); số đánh dấu "ước" là ước tính mới của nhóm.

---

## 0. Cân đối năng lực (đọc trước)

| | pd |
|---|---|
| Baseline báo cáo (P0 + P1) | 43,0 |
| P0 đã cắt gọn và điều chỉnh theo phân công mới (mục 3) | **khoảng 34** |
| Capacity hữu dụng ở 1,5 ngày/người/tuần (báo cáo) | 24 |
| Capacity hữu dụng ở 2 ngày/người/tuần (4 người x 5 tuần x 2 x 0,8) | 32 |

Bỏ hết P1 mà P0 vẫn vượt capacity báo cáo khoảng 10 pd. Nhóm chọn một trong hai:
1. Nâng lên khoảng 2 ngày/người/tuần (vừa khít, gần như không có dự phòng); hoặc
2. Giữ 1,5 ngày/tuần và cắt tiếp theo mục 7.

**M2 (cuối Sprint 2)** là điểm quyết định: chưa có pipeline end-to-end chạy trên AWS thì không làm P1.

---

## 1. Phân vai (đã chốt)

| Người | Nhóm | Phụ trách | Thư mục chính |
|---|---|---|---|
| **Phương** | App | Backend, Developer API, contract | `backend/`, `contracts/` |
| **Vũ** | App | Worker (Go + FFmpeg), frontend React | `worker/`, `frontend/` |
| **Lưu** | Cloud | Local stack + Docker, hạ tầng dữ liệu/mạng (Terraform), e2e/benchmark harness | `local/`, `docker-compose.yml`, `tests/e2e/`, `infra/terraform/modules/{storage,queue,database,network}` |
| **Nguyên** | Cloud | IAM, compute, tên miền + Cloudflare Tunnel + CloudFront, quan sát và co giãn, CI/CD | `infra/terraform/modules/{iam,compute,edge,observability}`, `scripts/`, `.github/` |

Ranh giới App - Cloud:
- App viết **code** và cung cấp lệnh chạy, cổng, biến môi trường. Cloud viết **Dockerfile, compose, script deploy** và review chéo với App.
- Cloud sở hữu đường đi **local -> cloud**: `make up` chạy được, rồi cùng image đó chạy được trên AWS.

Tải ước tính theo người (pd, đã tính việc dùng chung):

| Người | Việc chính | pd |
|---|---|---|
| Phương | A1 (0,75), A2 (5,5), A3 (2,0), A5 (0,5), A8 (0,5), R1 (0,75) | 10,0 |
| Vũ | A1 (0,75), A4 (4,0), A5 (1,5), A6 (2,0), A8 (0,5), R1 (0,75) | 9,5 |
| Lưu | C0 (2,5), C1 (1,5), C4 hosting (0,5), A8 (1,5), R1 (0,75) | 6,75 |
| Nguyên | C2 (1,5), C3 (2,0), C4 (1,5), C5 (1,5), A8 (0,5), R1 (0,75) | 7,75 |
| **Tổng** | | **34,0** |

Cặp App nặng hơn Cloud khoảng 3 pd mỗi người. Nếu App chậm, hai việc có thể chuyển sang Lưu mà không đổi ranh giới: viết lớp truy cập DynamoDB/SQS cho backend (báo cáo A03 vốn ghi "Lưu hỗ trợ tích hợp") và dựng bộ khung trang frontend.

---

## 2. Quyết định đã chốt cho MVP

| Chủ đề | Quyết định | Ảnh hưởng |
|---|---|---|
| Giới hạn upload | Tối đa **200 MB**, **5 phút**, độ phân giải tối đa **1080p**, định dạng mp4/mov/mkv/webm; cấu hình bằng biến môi trường. Chi tiết: `contracts/upload-limits.md` | A3 (API), A4 (Worker kiểm tra), C1 (lifecycle S3) |
| Tên miền | Domain đã đăng ký, DNS ở Cloudflare; **API đi qua Cloudflare Tunnel** (`cloudflared` chạy trên EC2 API); CloudFront giữ vai trò phân phối frontend và HLS | C3, C4; báo cáo mục 2.2 phải sửa |
| Worker | **Tối ưu chi phí**: Spot, số máy tối thiểu cấu hình được (0 khi không dùng, 1 khi demo/benchmark), máy nhỏ, giữ nguyên ASG + SQS | C3, C5, A8 |

---

## 3. Bảng epic và đối chiếu báo cáo

| Epic | Báo cáo | pd | Chủ | Sprint | Ưu tiên |
|---|---|---|---|---|---|
| A1 Contract và khung code | A01 | 1,5 (báo cáo 2,5; phần Docker chuyển sang C0) | Phương, Vũ | 1 | P0 |
| A2 Auth, project, video | A03 | 5,5 | Phương | 1-3 | P0 |
| A3 Upload và điều phối job | A04 | 2,0 | Phương | 2 | P0 |
| A4 Worker pipeline | A05 | 4,0 (báo cáo 5,0; phần deploy EC2 chuyển sang C3) | Vũ | 1-2 | P0 |
| A5 Độ tin cậy job | A06 | 2,0 | Vũ, Phương | 2 | P0 |
| A6 Frontend tối thiểu | A07, A08 (player) | 2,0 (báo cáo 3,0) | Vũ | 2-3 | P0 |
| A7 Webhook | A10 | 1,5 | Phương | 3 | **P1** |
| A8 E2E và benchmark | A12 | 3,0 (báo cáo 4,0) | Lưu chủ trì, Vũ/Phương hỗ trợ | 4 | P0 |
| C0 Local stack và Docker | (mới) | 2,5 | Lưu | 1 | P0 |
| C1 Hạ tầng dữ liệu bằng Terraform | A02 | 1,5 | Lưu | 1, 3 | P0 (4 module) |
| C2 IAM và bảo mật | A02 | 1,5 | Nguyên | 1-2 | P0 |
| C3 Compute và deploy | A05, A09 | 2,0 | Nguyên | 2 | P0 |
| C4 Tên miền, Cloudflare Tunnel, CloudFront | A08 | 2,0 | Nguyên (Lưu hosting React) | 3 | P0 |
| C5 Quan sát và co giãn | A09 | 1,5 | Nguyên, Vũ phối hợp | 3 | P0 |
| C6 CI/CD và Terraform phần còn lại | A11 | 7,0 | Nguyên | 3-4 | **P1** |
| R1 Báo cáo và demo | A13 | 3,0 | Cả nhóm | Chốt | P0 |

Khác báo cáo: C0 là việc mới; Terraform 4 module dữ liệu/mạng lên P0; C4 thêm Cloudflare Tunnel; A6, A8, C5 giảm phạm vi; thêm R1 vào danh sách epic.

---

## 4. Nhóm App

### A1. Contract và khung code (Sprint 1) - Phương, Vũ
- [ ] Chốt `video-state-machine.md`, `sqs-job-message.schema.json`, `s3-key-convention.md`, `dynamodb-tables.md`, `upload-limits.md`
- [ ] `openapi.yaml` đủ endpoint luồng chính
- [ ] Khung `backend/` (Spring Boot) và `worker/` (Go) chạy được ở máy cá nhân; ghi rõ cổng và biến môi trường để Lưu viết Dockerfile
- **Xong khi:** cả 4 người review contract.

### A2. Auth, project, video (Sprint 1-3) - Phương
- [ ] Đăng ký/đăng nhập JWT; tạo API key (lưu hash) và xác thực bằng API key
- [ ] CRUD project; danh sách video theo project (phân trang cursor)
- [ ] Phân quyền: chỉ thấy tài nguyên của mình
- [ ] CORS cấu hình qua biến môi trường (chỉ cần nếu frontend và API khác origin)

### A3. Upload và điều phối job (Sprint 2) - Phương
- [ ] `POST videos`: client khai báo `sizeBytes`, `contentType`; **từ chối nếu vượt giới hạn**; ký presigned URL 15 phút kèm Content-Length đã khai báo (cần thử để chắc S3 từ chối file sai kích thước)
- [ ] `POST confirm`: HeadObject kiểm tra tồn tại và dung lượng, rồi `UPLOADING -> PENDING` và gửi SQS
- [ ] `GET video`: trạng thái, assets, link streaming
- **Xong khi:** upload file 201 MB bị từ chối, file hợp lệ vào queue.

### A4. Worker pipeline (Sprint 1-2) - Vũ
- [ ] **POC FFmpeg** trên file local: HLS 360p + 720p và thumbnail
- [ ] Tối ưu chi phí CPU: giải mã một lần, tạo hai bản (filter `split`), preset `veryfast`, không tạo 1080p
- [ ] ffprobe kiểm tra thời lượng, độ phân giải, codec -> `FAILED_VALIDATION` nếu vượt giới hạn hoặc không phải video
- [ ] Consumer SQS, tải input, đẩy output đúng key và Content-Type, ghi DynamoDB có điều kiện, dọn thư mục tạm sau mỗi job
- **Xong khi:** `make up`, upload video mẫu cho ra `COMPLETED` và đủ assets.

### A5. Độ tin cậy job (Sprint 2) - Vũ, Phương
- [ ] Gia hạn visibility (~120s/lần); idempotency; `FAILED_*`; 3 lần nhận thì vào DLQ
- [ ] **Xử lý SIGTERM** để Worker Spot bị thu hồi thì dừng gọn và trả message về queue
- [ ] Hoàn thiện `local/scripts/chaos-kill-worker.sh` (kill giữa chừng, retry, DLQ)

### A6. Frontend tối thiểu (Sprint 2-3) - Vũ
- [ ] Đăng nhập, danh sách/tạo project
- [ ] Upload bằng presigned URL, **kiểm tra dung lượng/định dạng phía client**, danh sách video và trạng thái (polling)
- [ ] Trình phát HLS (hls.js), link streaming và đoạn mã nhúng dạng copy
- Luôn gọi đường dẫn tương đối `/api/...`, `/media/...`.

### A7. Webhook (Sprint 3, P1) - Phương
- [ ] Cấu hình `webhookUrl`, callback khi `COMPLETED`/`FAILED_*` có ký và retry

---

## 5. Nhóm Cloud

### C0. Local stack và Docker (Sprint 1) - Lưu, Nguyên review
- [ ] `make infra` và `make smoke` pass trên máy từng người (có `LOCALSTACK_AUTH_TOKEN`)
- [ ] Dockerfile cho `backend` (JRE), `worker` (có FFmpeg), build frontend; `make up` chạy toàn bộ qua nginx `localhost:8000`
- [ ] README quickstart; kiểm tra trên máy Windows của cả nhóm
- **Xong khi:** M0 (ngày 3) cho `make infra`; Dockerfile xong trước cuối Sprint 1. **Việc chặn nhóm App, làm đầu tiên.**

### C1. Hạ tầng dữ liệu bằng Terraform (Sprint 1, 3) - Lưu
- [ ] Bootstrap bucket lưu state; module `storage`, `queue`, `database`, `network`; `envs/dev` apply được
- [ ] **Lifecycle S3:** xóa object bucket input sau 7 ngày (MVP), CORS cho presigned PUT
- **Xong khi:** khớp `contracts/` và `local/`.

### C2. IAM và bảo mật (Sprint 1-2) - Nguyên
- [ ] Role riêng API/Worker/CI (least privilege); SSM Parameter Store `/tvc/{env}/...`
- [ ] Security Group: **API không mở inbound** (Cloudflare Tunnel chỉ cần outbound); Worker không mở inbound
- [ ] Review chéo với Phương, Vũ về quyền DynamoDB/SQS

### C3. Compute và deploy (Sprint 2) - Nguyên
- [ ] EC2 API (Docker): container API + container `cloudflared` (`--network host`, token lấy từ Parameter Store dạng SecureString)
- [ ] ECR cho `tvc-backend`, `tvc-worker`
- [ ] Launch Template + ASG Worker: **Spot, nhiều loại máy nhỏ** (mixed instances, on-demand base 0), min/max cấu hình bằng biến, EBS gp3 nhỏ; user-data đọc tag image từ Parameter Store, pull từ ECR, chạy container
- [ ] Session Manager, không mở SSH; **kiểm tra Service Quotas (vCPU on-demand và Spot) ngay tuần 1**, xin tăng nếu cần
- **Xong khi:** M2, pipeline end-to-end chạy trên AWS.

### C4. Tên miền, Cloudflare Tunnel, CloudFront (Sprint 3) - Nguyên, Lưu hosting React
- [ ] Cloudflare Tunnel: hostname dành cho origin (ví dụ `origin-api.<domain>`) trỏ tới `http://localhost:8080` trên EC2
- [ ] CloudFront 3 behavior (`/`, `/api/*`, `/media/*`); `/api/*` dùng origin là hostname của tunnel, **không chuyển tiếp header Host của người xem**; OAC cho bucket output
- [ ] Hosting React ở bucket frontend; dùng tên `*.cloudfront.net` trước, gắn tên miền riêng (ACM ở us-east-1, CNAME ở Cloudflare, chế độ DNS only) khi pipeline đã ổn
- **Xong khi:** một địa chỉ HTTPS dùng được web, API và phát HLS.

### C5. Quan sát và co giãn (Sprint 3) - Nguyên, Vũ phối hợp
- [ ] CloudWatch log JSON (có `jobId`/`videoId`), alarm DLQ, SNS email, AWS Budgets 25/50 USD
- [ ] Co giãn theo backlog: nếu chạy **min 0** thì giữ `metricpub` (công thức `max(1, workers)` cho phép tăng từ 0); nếu luôn **min >= 1** có thể dùng metric math của ASG và bỏ `metricpub` (xem mục 8.4)
- [ ] Scale-in protection khi đang transcode
- **Xong khi:** M3, worker tự tăng 1 lên 2 và giảm khi hết tải.

### C6. CI/CD và Terraform phần còn lại (Sprint 3-4, P1) - Nguyên
- [ ] OIDC, hoàn thiện `deploy.yml`, hai script deploy, CODEOWNERS; Terraform cho `iam`/`compute`/`edge`/`observability`
- **Ghi chú:** nếu chậm, tạo tay có ghi lại các bước và deploy bằng script vẫn đạt mục tiêu môn học.

### A8. E2E và benchmark (Sprint 4) - Lưu chủ trì, Vũ/Phương hỗ trợ, Nguyên cấp môi trường
- [ ] Bộ e2e chạy được với `BASE_URL` local và cloud; có ca upload quá giới hạn
- [ ] Benchmark throughput **1 vs 2 worker** (4 nếu còn thời gian) trên **on-demand, loại máy cố định** để kết quả lặp lại được; demo và dev mới dùng Spot
- [ ] 1 kịch bản Worker lỗi trên AWS; thu số liệu và biểu đồ

### R1. Báo cáo và demo (giai đoạn chốt) - Cả nhóm
- [ ] Báo cáo phản ánh kiến trúc **thực tế** (Cloudflare Tunnel, Spot, giới hạn upload), kết quả thí nghiệm
- [ ] Demo chạy thử ít nhất 2 lần; dựng môi trường `demo` trước và `destroy` sau

---

## 6. Điểm đồng bộ và mốc

| Điểm | Bên App | Bên Cloud | Khi nào |
|---|---|---|---|
| Local stack, Dockerfile | Cung cấp lệnh chạy, cổng, env | Lưu dựng, App review | Ngày 1-3 và cuối Sprint 1 |
| Key schema DynamoDB | Viết code theo | Lưu tạo bảng (local + Terraform) | Sprint 1 |
| Message SQS, giới hạn upload | Gửi/nhận, kiểm tra | Queue, lifecycle, alarm | Sprint 1-2 |
| IAM theo bảng/queue | Liệt kê quyền cần | Nguyên viết policy | Sprint 2 |
| Image Docker | Code build được | ECR, EC2, ASG | Cuối Sprint 2 |
| SIGTERM/Spot | Worker xử lý tín hiệu dừng | ASG Spot, thời gian chờ | Sprint 2-3 |
| Benchmark, chaos | Kịch bản, hỗ trợ | Lưu chủ trì, Nguyên cấp môi trường | Sprint 4 |

| Mốc | Tiêu chí | Quyết định |
|---|---|---|
| **M0** (ngày 3) | `make infra` chạy; backend/worker nối được LocalStack | Mở khóa App |
| **M1** (cuối Sprint 1) | Contract chốt; POC FFmpeg; Dockerfile; Terraform tạo được S3/SQS/DynamoDB thật | |
| **M2** (cuối Sprint 2) | Pipeline end-to-end chạy trên AWS | **Giữ hay hoãn P1** |
| **M3** (Sprint 3) | SaaS + API qua CloudFront và tên miền; worker scale 1 lên 2 | |
| **M4** (Sprint 4) | Benchmark và chaos có số liệu | |
| **M5** | Báo cáo và demo | |

---

## 7. Cắt giảm khi thiếu thời gian (theo thứ tự)

1. A7 Webhook, C6 CI/CD và Terraform ngoài 4 module dữ liệu/mạng (đã là P1).
2. Gắn tên miền riêng cho CloudFront (dùng `*.cloudfront.net` cho demo).
3. A6: bỏ đoạn mã nhúng, chỉ giữ trình phát và link streaming.
4. A8: benchmark 1 vs 2 worker, một kịch bản lỗi.
5. C5: chỉ làm scaling theo backlog, bỏ alarm phụ ngoài DLQ.
6. A2: JWT trước, API key sau (cắt cuối cùng vì là điểm khác biệt của Developer API).

Không cắt: pipeline upload -> queue -> worker -> HLS -> `COMPLETED`, một thí nghiệm co giãn có số liệu, một thí nghiệm khôi phục lỗi, giới hạn upload, báo cáo và demo.

---

## 8. Rủi ro kỹ thuật cần chốt sớm

### 8.1. Cloudflare Tunnel kết hợp CloudFront
- Chuỗi chạy: người dùng -> CloudFront -> Cloudflare (proxy) -> Tunnel -> EC2. Origin của CloudFront là hostname tunnel có chứng chỉ hợp lệ do Cloudflare cấp, nên không còn lỗi chứng chỉ khi nối tới EC2 (vấn đề của thiết kế gốc dùng tên DNS mặc định của EC2).
- CloudFront **không được chuyển tiếp Host của người xem** tới origin (dùng origin request policy loại trừ Host), nếu không Cloudflare không khớp ingress của tunnel.
- Token tunnel là bí mật: lưu SecureString trong Parameter Store, không commit, không in log. Tạo tunnel là bước thủ công một lần, ghi lại trong `docs/`.
- Hostname origin vẫn truy cập được trực tiếp từ internet (không qua CloudFront). Với MVP chấp nhận vì API có xác thực; có thể thêm header bí mật hoặc Cloudflare Access sau.
- Upload video đi thẳng lên S3 bằng presigned URL nên **không bị giới hạn kích thước request của Cloudflare**.
- **Phương án đơn giản hơn nếu chuỗi trên rắc rối:** bỏ `/api/*` khỏi CloudFront, frontend ở `app.<domain>` (CloudFront), API ở `api.<domain>` (Tunnel). Đổi lại phải cấu hình CORS (API và bucket output) và local không còn giống cloud.
- Local không cần Cloudflare; nginx vẫn giả lập một domain. Có thể dùng `cloudflared` tạm để chia sẻ bản local cho thành viên khác xem.

### 8.2. Giới hạn upload
- Presigned PUT không tự giới hạn kích thước; lớp bảo vệ gồm: client (UX) -> API từ chối khai báo vượt giới hạn và ký kèm Content-Length -> `confirm` kiểm tra HeadObject -> Worker kiểm tra ffprobe -> lifecycle S3 dọn file.
- 200 MB nằm trong một lần PUT, không cần multipart.
- Worker cần ổ đĩa đủ cho input + output của một job (EBS gp3 20-30 GB là dư).

### 8.3. Tối ưu chi phí Worker
- **Spot:** hợp với thiết kế vì SQS giao lại message khi Worker chết. Khi bị thu hồi, Worker nhận cảnh báo (khoảng 2 phút) nên xử lý SIGTERM để trả job. Dùng nhiều loại máy nhỏ để giảm rủi ro hết Spot capacity.
- **Min 0:** tiết kiệm nhất khi không dùng, đổi lại khởi động nguội 2-4 phút (khởi tạo máy + pull image). Dùng min 1 khi demo/benchmark.
- **Quota:** tài khoản AWS mới có thể có hạn mức vCPU thấp; 4 Worker 2 vCPU + 1 API có thể vượt, cần kiểm tra Service Quotas sớm.
- **Public IPv4:** mỗi địa chỉ IPv4 công khai bị tính phí theo giờ, kể cả Worker; vẫn rẻ hơn NAT Gateway. Với Cloudflare Tunnel, EC2 API không cần Elastic IP.
- **T3 và benchmark:** dòng `t3` có CPU credits, hết credits sẽ bị giới hạn làm sai kết quả; dùng dòng `c` (hoặc bật unlimited) khi benchmark.
- Bản ARM (Graviton) rẻ hơn nhưng phải build image arm64; bỏ qua ở MVP.
- Terraform `destroy` môi trường không dùng; AWS Budgets 25/50 USD.

### 8.4. Co giãn từ 0 và metric math
- Công thức báo cáo `BacklogPerInstance = backlog / max(1, workers)` cho phép tăng từ 0 máy. Metric math của ASG (`ApproximateNumberOfMessagesVisible / GroupInServiceInstances`) được AWS hướng dẫn cho SQS nhưng chia cho số máy đang chạy nên **không dùng được khi 0 máy**. Quyết định theo chế độ: min 0 -> `metricpub`; min >= 1 -> metric math, bớt một thành phần cần viết và vận hành.

### 8.5. Khác
- **Instance Refresh** với ít máy: dùng `ScaleInProtectedInstances=Wait` để không thay máy đang transcode.
- **Tên tài nguyên:** `tvc-{env}-api`, `tvc-{env}-worker-asg`, `/tvc/{env}/worker/image-tag` được script dùng cứng; Terraform phải đặt đúng tên.
- **Effort deploy EC2/ASG** chỉ được gộp trong A05, A09 ở báo cáo; C3 đã tách riêng để theo dõi.
__END__

# ----------------------------------------------------------------------------
# Kết thúc
# ----------------------------------------------------------------------------
chmod +x local/scripts/*.sh local/localstack/init/*.sh scripts/*.sh 2>/dev/null || true

echo
echo "==> Xong: tạo $CREATED file, bỏ qua $SKIPPED file đã tồn tại."
echo
echo "Bước tiếp theo:"
echo "  1. cp .env.example .env"
echo "  2. Đọc README.md, docs/LOCAL_AND_CICD.md, docs/EPICS.md"
echo "  3. make infra && make smoke   (cần Docker Desktop và make)"
echo "  4. Sửa handle trong .github/CODEOWNERS, rồi git add . && git commit"
