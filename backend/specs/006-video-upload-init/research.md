# Technical Research: Khởi tạo Upload Video & Cấp S3 Presigned PUT URL (F-006)

**Feature**: F-006 — Khởi tạo Upload Video & Cấp S3 Presigned PUT URL (Epic A3)  
**Date**: 2026-10-31  
**Status**: Completed

---

## 1. Nghiên cứu Ký S3 Presigned PUT URL với AWS SDK v2

### Quyết định kỹ thuật
Sử dụng `software.amazon.awssdk.services.s3.presigner.S3Presigner` đã được cấu hình trong `AwsConfig.java` để ký Presigned PUT URL có thời hạn 15 phút (900 giây) kèm theo các tiêu đề HTTP ràng buộc `Content-Type` và `Content-Length`.

### Căn cứ và Phân tích
- **Cơ chế ký SigV4 của AWS S3**: Khi tạo `PutObjectPresignRequest`, AWS SDK v2 nhận vào `PutObjectRequest` chứa:
  - `bucket`: tên bucket tiếp nhận `tvc-{env}-input`.
  - `key`: đường dẫn đối tượng `inputKey` dạng `{projectId}/{videoId}/source.{ext}`.
  - `contentType`: MIME type của video (ví dụ: `video/mp4`).
  - `contentLength`: dung lượng chính xác khai báo bằng byte.
- **Header bắt buộc đối với Client**: Khi `contentType` và `contentLength` được đưa vào yêu cầu ký, AWS SDK v2 sẽ đưa chúng vào danh mục `SignedHeaders` của thuật toán AWS SigV4. Client khi thực hiện lệnh HTTP PUT bắt buộc phải truyền chính xác tiêu đề `Content-Type` và `Content-Length` này; nếu gửi sai hoặc thiếu, S3 sẽ từ chối với lỗi `SignatureDoesNotMatch` (HTTP 403). Điều này đảm bảo tính toàn vẹn dữ liệu từ tầng Client tới S3.
- **Khả năng tương thích LocalStack**: Trong môi trường cục bộ, `AwsConfig` đã cấu hình `forcePathStyle(true)` và `S3_PRESIGN_ENDPOINT` (mặc định `http://localhost:4566`), giúp URL ký sẵn có định dạng `http://localhost:4566/tvc-local-input/{key}?...` mà trình duyệt hoặc HTTP client có thể gọi trực tiếp không gặp lỗi DNS.

### Giải pháp thay thế đã xem xét
- *Multipart Upload*: Chỉ cần thiết khi file > 100MB-5GB hoặc mạng không ổn định. Với MVP giới hạn 200MB theo `contracts/upload-limits.md`, single-part PUT đơn giản, nhanh hơn, không tốn thêm API hoàn tất multipart và giảm thiểu trạng thái rác trên S3.
- *Trung chuyển qua Backend (Stream Proxy)*: Vi phạm nguyên tắc phân tách ranh giới tính toán và lưu trữ (Constitution Principle II & System Constraint); gây nghẽn RAM và băng thông của Spring Boot app.

---

## 2. Thẩm định Giới hạn Dung lượng & Định dạng Video

### Quyết định kỹ thuật
- **Giới hạn dung lượng**:
  - `sizeBytes <= 0`: Bị chặn bởi Bean Validation (`@Min(1)`) -> HTTP 400 Bad Request.
  - `sizeBytes > 209715200` (200MB): Bị chặn bởi Service kiểm tra `tvcProperties.getUpload().getMaxBytes()` -> Ném `AppException(ErrorCode.UPLOAD_LIMIT_EXCEEDED)` -> HTTP 413 Payload Too Large.
- **Định dạng container & MIME Type**:
  - `contentType`: Phải thuộc danh mục cấu hình `tvcProperties.getUpload().getAllowedContentTypes()` (`video/mp4`, `video/quicktime`, `video/x-matroska`, `video/webm`).
  - `filename`: Trích xuất chuỗi sau dấu chấm cuối cùng, chuyển thành chữ thường. Đuôi tệp phải thuộc danh sách `mp4`, `mov`, `mkv`, `webm`.
  - Nếu `contentType` hoặc đuôi file không hợp lệ -> Ném `AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE)` -> HTTP 422 Unprocessable Entity.

### Căn cứ và Phân tích
- Tận dụng `ErrorCode.UPLOAD_LIMIT_EXCEEDED` (HTTP 413) và `ErrorCode.UNSUPPORTED_MEDIA_TYPE` (HTTP 422) đã có sẵn trong `ErrorCode.java` từ F-002.
- Việc kiểm tra kết hợp cả `contentType` (MIME header) và đuôi mở rộng của `filename` ngăn chặn việc người dùng đổi đuôi file giả mạo hoặc gửi MIME type sai lệch.

---

## 3. Sinh Định danh Video & Cấu trúc Đường dẫn S3 `inputKey`

### Quyết định kỹ thuật
- **Quy ước `videoId`**: Sử dụng `vid_` kết hợp 12 ký tự hex ngẫu nhiên sinh từ `UUID` hoặc `SecureRandom` (ví dụ: `vid_4a77e1c8d102`). Đảm bảo độ dài đồng nhất, tính ngẫu nhiên cao và tiền tố dễ nhận diện trong logs/metrics.
- **Quy ước `inputKey`**: Theo chuẩn `contracts/s3-key-convention.md`:
  `{projectId}/{videoId}/source.{ext}`
  Trong đó `{ext}` là phần mở rộng tệp tin trích xuất sau dấu chấm cuối cùng và đã được chuẩn hóa về chữ thường (lowercase).
  Ví dụ: `prj_1c05d9e2a3f4/vid_4a77e1c8d102/source.mp4`.

### Căn cứ và Phân tích
- Định danh suy ra từ `projectId/videoId` bảo đảm tính ổn định: Worker xử lý downstream luôn biết vị trí tìm file gốc mà không cần tra cứu phức tạp.
- Chuẩn hóa chữ thường ngăn ngừa lỗi phân biệt hoa thường (case-sensitivity) giữa hệ điều hành Linux (Worker/S3) và Windows/macOS của client.

---

## 4. Tích hợp Dual Security & Kiểm soát Ranh giới Project

### Quyết định kỹ thuật
- Tái sử dụng `ApiKeyAuthenticationFilter` đã xây dựng trong F-005.
- Tại Controller/Service:
  - Khi `Authentication` là `JwtAuthenticationToken`: Lấy `userId` từ token (`jwt.getSubject()`), gọi `projectService.getProject(projectId, userId)` để kiểm tra quyền sở hữu (`ownerId`). Nếu không phải owner -> ném `ACCESS_DENIED` (HTTP 403); nếu Project không tồn tại hoặc đã `DELETED` -> ném `RESOURCE_NOT_FOUND` (HTTP 404).
  - Khi `Authentication` là `ApiKeyAuthenticationToken`: `ApiKeyAuthenticationFilter` đã kiểm tra ranh giới `path.projectId == token.projectId`. Service chỉ cần tra cứu trạng thái Project bằng `projectRepository.findByProjectId(projectId)` để bảo đảm Project đang `ACTIVE` (chưa bị xóa mềm).

### Căn cứ và Phân tích
- Cơ chế này đảm bảo tính nhất quán tuyệt đối giữa SaaS Web và Developer Platform mà không làm lộ logic kiểm tra quyền ra Controller.
- Ngăn chặn hoàn toàn rủi ro gọi chéo tenant (cross-tenant access) hoặc inject video vào project đã bị vô hiệu hóa.

---

## 5. Chiến lược Kiểm chứng Thực tế (Real Verification)

### Quyết định kỹ thuật
- **Unit Tests**:
  - `VideoValidationTest`: Kiểm tra các hàm trích xuất phần mở rộng, đuôi hoa/thường, file nhiều dấu chấm, kiểm tra ranh giới 200MB.
  - `VideoServiceTest`: Mockito kiểm thử các luồng nghiệp vụ tạo video, kiểm tra quyền sở hữu, chặn 413, chặn 422, chặn 403, chặn 404.
- **WebMvc Slice Test**:
  - `VideoControllerTest`: Sử dụng `@WebMvcTest(VideoController.class)` kiểm tra serialization DTO `ApiResponse<InitVideoResponse>`, validation Jakarta (`@Valid`), và HTTP status code 201, 400, 413, 422.
- **Integration Test với Testcontainers LocalStack**:
  - `VideoUploadInitIT`:
    - Khởi tạo container LocalStack (S3 + DynamoDB).
    - Tạo các bảng `projects`, `videos` và bucket `tvc-local-input`.
    - Gọi API tạo video -> Nhận được `InitVideoResponse` chứa `presignedUrl`.
    - Dùng `RestTemplate` hoặc `HttpClient` thực hiện HTTP PUT gửi byte dữ liệu mẫu lên `presignedUrl` với đầy đủ headers.
    - Gọi S3Client `headObject` xác minh tệp đã nằm trên bucket S3 với đúng dung lượng và content-type.
    - Truy vấn DynamoDB bảng `videos` xác minh trạng thái `UPLOADING` và `attemptCount = 0`.
- **E2E Postman Collection**:
  - `backend/postman/F-006-video-upload-init.postman_collection.json` kiểm thử cả Bearer JWT và `X-API-Key`.
- **Zero Regression**:
  - Chạy `./scripts/verify.sh` để đảm bảo 100% 89 test hiện tại tiếp tục pass.
