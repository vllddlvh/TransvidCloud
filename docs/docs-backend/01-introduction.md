# 1. Backend Overview

## Mục tiêu

Backend cung cấp REST API cho hai nhóm sử dụng TransVidCloud:

- **SaaS Web:** người dùng đăng nhập, quản lý project/video và xem kết quả.
- **Developer API:** ứng dụng bên ngoài tích hợp upload, theo dõi xử lý và lấy assets.

Hai nhóm dùng chung nghiệp vụ và processing pipeline.

## Trách nhiệm Backend

- Đăng ký/đăng nhập và xác thực request.
- Quản lý project, API key và metadata video.
- Cấp presigned URL để client upload thẳng lên S3.
- Xác nhận upload, điều phối job qua SQS và cung cấp trạng thái.
- Trả thông tin video/assets cho client.
- Kiểm tra quyền truy cập tài nguyên và tích hợp AWS an toàn.

## Ngoài phạm vi Backend

- Không proxy toàn bộ byte video qua API.
- Không chạy FFmpeg/transcoding trong HTTP request.
- Không consume queue hay thực thi job video; việc này thuộc Worker.
- Không tự định nghĩa lại schema API, SQS, trạng thái hoặc S3 key đã công bố trong `contracts/`.

## Công nghệ

Thiết kế nền tảng dùng Java 21 LTS, Spring Boot 3.4.x, REST API, AWS SDK for Java v2 và DynamoDB. Cấu trúc build/package cụ thể phải khớp với code trong `backend/`.

## Sơ đồ rút gọn

```text
SaaS Web / External App
          |
          v
   Spring Boot API
     |    |     |
 DynamoDB S3   SQS
                 |
                 v
           Go Worker + FFmpeg
                 |
                 v
              S3 output
```
