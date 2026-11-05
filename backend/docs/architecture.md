# Architecture Overview

## Mục đích và ranh giới hệ thống

- Product/system purpose: Cung cấp REST API cho SaaS Web và Developer Platform của hệ thống lưu trữ & chuyển mã video TransVidCloud.
- Main users/actors: SaaS Web User (xác thực bằng Bearer JWT) và External Developer Application (xác thực bằng Project API Key).
- In scope: Đăng ký/đăng nhập, quản lý Project/API Key/Video metadata trên DynamoDB, cấp S3 Presigned PUT URL, xác nhận upload (`HeadObject`) và đẩy job message vào SQS.
- Out of scope: Không proxy luồng file video qua Backend, không chạy FFmpeg/ffprobe, không xử lý hàng đợi SQS (thuộc trách nhiệm của Go Worker).

## Thành phần và trách nhiệm

| Module / Service | Trách nhiệm | Công nghệ | Entry point | Owner |
|---|---|---|---|---|
| `backend` | REST API, Auth (JWT + API Key), Project/Video metadata, S3 Presigner, SQS Job Producer | Java 21, Spring Boot 3.4, Spring Cloud AWS 3.3, DynamoDB Enhanced Client | `com.transvidcloud.backend.TransvidCloudBackendApplication` | Phương |

## Luồng dữ liệu / request chính

1. Client gọi API khởi tạo video -> Backend kiểm tra giới hạn `sizeBytes <= 200 MB`, lưu bản ghi `UPLOADING` vào bảng `videos` (DynamoDB) và trả về S3 Presigned PUT URL (15 phút).
2. Client tải trực tiếp video lên bucket `tvc-{env}-input` (S3).
3. Client gọi `POST /api/videos/{videoId}/confirm` -> Backend gọi `HeadObject` kiểm tra file trên S3, chuyển trạng thái `UPLOADING -> PENDING` trên DynamoDB và gửi message tới queue `tvc-{env}-jobs` (SQS).
4. Worker (Go) xử lý bất đồng bộ, ghi HLS/thumbnail lên bucket `tvc-{env}-output` và cập nhật trạng thái `COMPLETED` / `FAILED_*` vào DynamoDB.

## Module boundaries và contracts

- API/event contracts: Tuân thủ tuyệt đối `../contracts/openapi.yaml`, `../contracts/sqs-job-message.schema.json`, `../contracts/video-state-machine.md`.
- Shared libraries/types: Tách biệt DTO (Java `record`), Domain/Service và DynamoDB Item (`@DynamoDbBean`) qua MapStruct.
- Database ownership: Chia sẻ bảng `videos` với Go Worker theo quy tắc phân quyền trường và trạng thái trong `../contracts/video-state-machine.md`.
- External dependencies: Amazon DynamoDB, Amazon S3, Amazon SQS (hoặc LocalStack khi chạy local).

## Security boundaries

- Stateless authentication hỗ trợ đồng thời Bearer JWT (HMAC-SHA256) và API Key (băm SHA-256 lưu trong bảng `apikeys`).
- Kiểm tra quyền sở hữu tài nguyên (isolation theo `ownerId` và `projectId`) tại tầng Service cho mọi thao tác đọc/ghi.

## Architectural constraints

- Chỉ sử dụng DynamoDB làm CSDL duy nhất (theo `../docs/adr/0001-dynamodb-only.md`).
- Context path mặc định là `/api` trên cổng `8080`.

## Diagram / references

- Xem chi tiết tại `../docs/docs-backend/04-design-and-architecture.md` và `../docs/docs-backend/08-upload-and-processing-workflow.md`.
