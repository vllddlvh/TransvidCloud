# Quickstart Validation Guide: Khởi tạo Upload Video & Cấp S3 Presigned PUT URL (F-006)

**Feature**: F-006 — Khởi tạo Upload Video & Cấp S3 Presigned PUT URL (Epic A3)  
**Date**: 2026-10-31  
**Status**: Ready for Verification

---

## 1. Điều kiện tiên quyết & Chuẩn bị môi trường

1. **Khởi động LocalStack**:
   ```bash
   # Từ thư mục gốc repository
   make infra && make smoke
   ```
   Đảm bảo container LocalStack đang chạy và các bảng `projects`, `videos` cũng như bucket `tvc-local-input` đã được tạo.

2. **Lấy Token xác thực hoặc API Key**:
   * **Cách 1: Lấy Bearer JWT qua API đăng nhập**:
     ```bash
     curl -s -X POST http://localhost:8080/api/auth/login \
       -H "Content-Type: application/json" \
       -d '{"email":"phuong@transvidcloud.com","password":"Password123!"}'
     # Trích xuất access_token: export JWT_TOKEN="eyJhbGci..."
     ```
   * **Cách 2: Sử dụng Project API Key**:
     ```bash
     export API_KEY="tvc_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
     ```

---

## 2. Kịch bản Xác minh Thủ công qua cURL

### Kịch bản 1: Khởi tạo Video hợp lệ qua Bearer JWT (201 Created)

```bash
curl -i -X POST http://localhost:8080/api/projects/prj_1c05d9e2a3f4/videos \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Bài giảng Chương 1 - Kiến trúc Hệ thống",
    "filename": "lecture-01.mp4",
    "sizeBytes": 52428800,
    "contentType": "video/mp4"
  }'
```

**Kết quả mong đợi**:
* HTTP Status: `201 Created`
* JSON Response:
  ```json
  {
    "code": 1000,
    "message": null,
    "result": {
      "video": {
        "videoId": "vid_xxxxxxxxxxxx",
        "projectId": "prj_1c05d9e2a3f4",
        "title": "Bài giảng Chương 1 - Kiến trúc Hệ thống",
        "status": "UPLOADING",
        "sizeBytes": 52428800,
        "contentType": "video/mp4",
        "attemptCount": 0,
        "errorCode": null,
        "assets": null,
        "createdAt": "...",
        "updatedAt": "..."
      },
      "upload": {
        "presignedUrl": "http://localhost:4566/tvc-local-input/prj_1c05d9e2a3f4/vid_xxxxxxxxxxxx/source.mp4?...",
        "method": "PUT",
        "expiresInSeconds": 900,
        "requiredHeaders": {
          "Content-Type": "video/mp4",
          "Content-Length": "52428800"
        }
      }
    }
  }
  ```

---

### Kịch bản 2: Tải file lên S3 qua Presigned PUT URL được cấp

Sử dụng đường dẫn `presignedUrl` trích xuất từ phản hồi Kịch bản 1:

```bash
# Tạo file mẫu kích thước 50MB (hoặc đúng 52428800 bytes)
dd if=/dev/urandom of=/tmp/sample.mp4 bs=1048576 count=50

# Thực hiện HTTP PUT trực tiếp lên S3
curl -i -X PUT "$PRESIGNED_URL" \
  -H "Content-Type: video/mp4" \
  -H "Content-Length: 52428800" \
  --data-binary "@/tmp/sample.mp4"
```

**Kết quả mong đợi**:
* HTTP Status từ S3/LocalStack: `200 OK`
* Kiểm tra tệp tin trên S3:
  ```bash
  awslocal s3 ls s3://tvc-local-input/prj_1c05d9e2a3f4/vid_xxxxxxxxxxxx/source.mp4
  ```

---

### Kịch bản 3: Từ chối file vượt quá 200MB (413 Payload Too Large)

```bash
curl -i -X POST http://localhost:8080/api/projects/prj_1c05d9e2a3f4/videos \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Video dung lượng lớn",
    "filename": "heavy-video.mp4",
    "sizeBytes": 209715201,
    "contentType": "video/mp4"
  }'
```

**Kết quả mong đợi**:
* HTTP Status: `413 Payload Too Large`
* Mã lỗi: `UPLOAD_LIMIT_EXCEEDED` ("Dung lượng tải lên vượt quá giới hạn tối đa cho phép").

---

### Kịch bản 4: Từ chối định dạng không hợp lệ (422 Unprocessable Entity)

```bash
curl -i -X POST http://localhost:8080/api/projects/prj_1c05d9e2a3f4/videos \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Video định dạng AVI",
    "filename": "clip.avi",
    "sizeBytes": 10485760,
    "contentType": "video/x-msvideo"
  }'
```

**Kết quả mong đợi**:
* HTTP Status: `422 Unprocessable Entity`
* Mã lỗi: `UNSUPPORTED_MEDIA_TYPE` ("Định dạng phương tiện hoặc nội dung không được hỗ trợ").

---

### Kịch bản 5: Khởi tạo Video qua `X-API-Key` (201 Created)

```bash
curl -i -X POST http://localhost:8080/api/projects/prj_1c05d9e2a3f4/videos \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Tự động hóa tải video qua API Key",
    "filename": "autoupload.mov",
    "sizeBytes": 31457280,
    "contentType": "video/quicktime"
  }'
```

**Kết quả mong đợi**:
* HTTP Status: `201 Created`
* `inputKey` sinh ra: `prj_1c05d9e2a3f4/vid_.../source.mov`.

---

## 3. Lệnh Kiểm thử Tự động hóa Toàn diện

```bash
# 1. Chạy Unit Tests và Slice Tests
./mvnw -B test -Dtest="VideoValidationTest,VideoServiceTest,VideoControllerTest"

# 2. Chạy Real Verification Integration Test (Testcontainers S3 + DynamoDB)
./mvnw -B test -Dtest="VideoUploadInitIT"

# 3. Chạy Zero Regression trên toàn bộ hệ thống
./scripts/verify.sh
```
