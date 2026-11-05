# Quickstart & Validation Guide: Xác nhận Upload, Chuyển trạng thái & Điều phối SQS Job (F-007)

**Feature**: F-007 — Xác nhận Upload, Chuyển trạng thái & Điều phối SQS Job (Epic A3/A5)  
**Date**: 2026-11-01

---

## 1. Điều kiện tiên quyết (Prerequisites)

- **Java SDK**: JDK 21 LTS
- **Build Tool**: Maven Wrapper (`./mvnw`)
- **Docker**: Đang chạy (dành cho Testcontainers LocalStack S3 + DynamoDB + SQS hoặc môi trường `make infra`)

---

## 2. Chạy Kiểm thử Tự động (Automated Verification)

### 2.1. Chạy Unit Tests & WebMvc Slice Tests

```bash
cd backend
./mvnw -B test -Dtest="VideoValidationHelperTest,VideoServiceTest,OutboxPublisherServiceTest,VideoControllerTest"
```

### 2.2. Chạy Integration Test thực tế với Testcontainers LocalStack (S3 + DynamoDB + SQS)

Kiểm chứng toàn bộ vòng đời: Khởi tạo Video -> PUT tệp thật lên S3 qua Presigned URL -> Gọi `POST /api/videos/{videoId}/confirm` -> Xác minh `TransactWriteItems` (`VideoItem` sang `PENDING` + `OutboxItem` sang `PUBLISHED`) -> Nhận và giải mã message từ hàng đợi SQS `tvc-local-jobs` -> Kiểm chứng gọi lặp Idempotent (`200 OK`, không nhân bản message SQS) -> Kiểm chứng phục hồi qua Outbox Relay khi giả lập bản ghi `OUTBOX#PENDING`.

```bash
cd backend
./mvnw -B verify -Dit.test="VideoConfirmDispatchIT"
```

### 2.3. Chạy Bộ Kiểm chứng Toàn diện (Zero Regression Gate)

Đảm bảo 100% các bài kiểm thử của F-001 -> F-006 và F-007 đều PASS:

```bash
cd backend
./scripts/verify.sh
```

---

## 3. Kịch bản Kiểm chứng Thủ công bằng cURL / Newman

### Bước 1: Khởi động LocalStack và Backend

```bash
# Từ thư mục gốc TransvidCloud
make infra && make smoke

# Chạy ứng dụng Backend
cd backend && ./mvnw spring-boot:run
```

### Bước 2: Khởi tạo Video & Tải tệp mẫu lên S3

```bash
# 1. Khởi tạo video mới (lấy VIDEO_ID và PRESIGNED_URL)
INIT_RES=$(curl -s -X POST "http://localhost:8080/api/projects/${PROJECT_ID}/videos" \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Demo Confirm Video F-007",
    "filename": "lecture.mp4",
    "sizeBytes": 16,
    "contentType": "video/mp4"
  }')

VIDEO_ID=$(echo "$INIT_RES" | jq -r '.video.videoId')
PRESIGNED_URL=$(echo "$INIT_RES" | jq -r '.upload.presignedUrl')

# 2. Tải trực tiếp dữ liệu mẫu (16 bytes) lên S3 qua Presigned PUT URL
curl -i -X PUT "$PRESIGNED_URL" \
  -H "Content-Type: video/mp4" \
  --data-binary "0123456789abcdef"
```

### Bước 3: Xác nhận Upload lần đầu (`HTTP 202 Accepted`)

```bash
curl -i -X POST "http://localhost:8080/api/videos/${VIDEO_ID}/confirm" \
  -H "Authorization: Bearer ${JWT_TOKEN}"
```
- **Kỳ vọng**: HTTP `202 Accepted`, JSON trả về `status: "PENDING"`, `sizeBytes: 16`.

### Bước 4: Gọi lặp lại Confirm (`HTTP 200 OK` — Idempotent)

```bash
curl -i -X POST "http://localhost:8080/api/videos/${VIDEO_ID}/confirm" \
  -H "Authorization: Bearer ${JWT_TOKEN}"
```
- **Kỳ vọng**: HTTP `200 OK`, JSON trả về `status: "PENDING"`, không gửi trùng message vào SQS.

### Bước 5: Chạy Postman Collection E2E với Newman

```bash
npx newman run backend/postman/F-007-video-confirm-dispatch.postman_collection.json
```
