# Development Setup

> Mục tiêu: một developer/agent mới có thể chuẩn bị môi trường và chạy project bằng các bước có thể lặp lại.

## Prerequisites

| Thành phần | Phiên bản được hỗ trợ | Cách kiểm tra |
|---|---|---|
| OS/runtime | Linux / macOS / Windows (WSL2) | `uname -a` |
| Language SDK | Java 21 LTS (Temurin/OpenJDK) | `java -version` |
| Package/build manager | Maven Wrapper (`./mvnw`, Maven 3.9+) | `./mvnw -version` |
| Database / services | Docker Compose + LocalStack (S3, SQS, DynamoDB) | `docker compose version` |

## First-time setup

1. Chuẩn bị biến môi trường tại thư mục gốc repo: `cp ../.env.example ../.env`
2. Khởi động hạ tầng AWS giả lập (LocalStack): `(cd .. && make infra && make smoke)`
3. Tải dependencies và kiểm tra build tại `backend/`: `./mvnw -B test-compile`

Không ghi giá trị secret thật vào tài liệu. Chỉ nêu tên biến và cách lấy credential an toàn.

## Run locally

```bash
./mvnw spring-boot:run
```

## Health check / smoke test

```bash
curl -f http://localhost:8080/api/actuator/health
# Hoặc mở Swagger UI tại: http://localhost:8080/api/swagger-ui.html
```

## Troubleshooting

| Symptom | Likely cause | Safe diagnostic | Resolution |
|---|---|---|---|
| Lỗi kết nối cổng `4566` khi gọi API AWS | Container LocalStack chưa chạy | `docker ps` hoặc `(cd .. && make smoke)` | Chạy `(cd .. && make infra)` |
| Presigned PUT URL bị lỗi `SignatureDoesNotMatch` trên trình duyệt | Sai `S3_PRESIGN_ENDPOINT` hoặc chưa bật path-style access | Kiểm tra biến `S3_PRESIGN_ENDPOINT` | Đảm bảo `S3_PRESIGN_ENDPOINT=http://localhost:4566` ở local |
