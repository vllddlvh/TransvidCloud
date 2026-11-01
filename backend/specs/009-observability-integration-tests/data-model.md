# Data Model: F-009 — Quan Sát Hệ Thống, Bảo Mật Log & Bộ Kiểm Thử Tích Hợp / Contract

**Feature**: F-009  
**Date**: 2026-11-01  
**Status**: Completed  

---

## 1. Ngữ Cảnh Nhật Ký (LogContext & MDC Envelope)

Lớp tiện ích và phong bì ngữ cảnh (MDC Envelope) dùng để gắn các định danh nghiệp vụ vào luồng thực thi của từng luồng (Thread / Virtual Thread).

### Cấu trúc các khóa MDC (`LogContext.java`):

| Tên Khóa MDC | Kiểu Dữ Liệu | Nguồn Gốc | Mục Đích | Ví Dụ |
|---|---|---|---|---|
| `correlationId` | `String` (UUID) | Header `X-Correlation-ID` hoặc tự sinh | Định danh xuyên suốt một HTTP request hoặc chu trình background job | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` |
| `userId` | `String` | Trích xuất từ JWT Principal hoặc API Key owner | Nhận diện người dùng thực hiện thao tác | `usr_9f8e7d6c5b4a` |
| `projectId` | `String` | Path parameter hoặc API Key scope | Nhận diện dự án đang được thao tác | `prj_1a2b3c4d5e6f` |
| `videoId` | `String` | Path param hoặc body nghiệp vụ video | Nhận diện video cụ thể trong chu trình tải lên/xử lý | `vid_9876543210ab` |
| `jobId` | `String` (UUID) | Sinh ra khi confirm video / dispatch SQS | Nhận diện mã công việc gửi sang hàng đợi Go Worker | `job_fedcba098765` |

### Vòng đời quản lý MDC:
1. **Khởi tạo**: Tại `CorrelationIdFilter.doFilterInternal()` -> Trích xuất hoặc sinh mới `correlationId` và ghi vào `MDC.put("correlationId", id)`.
2. **Làm giàu ngữ cảnh (Enrichment)**: Khi request đi vào Controller / Service xác định được `userId`, `projectId`, `videoId` -> Gọi `LogContext.setProjectId(...)`, `LogContext.setVideoId(...)`.
3. **Giải phóng (Cleanup)**: Trong khối `finally` của `CorrelationIdFilter` -> Bắt buộc gọi `MDC.clear()` để ngăn ngừa rò rỉ bộ nhớ hoặc lẫn lộn ngữ cảnh giữa các Virtual Threads.

---

## 2. Mô Hình Bộ Lọc Tương Quan (CorrelationIdFilter Model)

### Thuộc tính cấu hình:

```java
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_CORRELATION_ID_KEY = "correlationId";
    private static final int MAX_CORRELATION_ID_LENGTH = 128;
    private static final Pattern SAFE_CORRELATION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9-_]+$");
}
```

### Quy tắc chuẩn hóa mã tương quan:
- Nếu header có giá trị hợp lệ (khớp `SAFE_CORRELATION_ID_PATTERN` và độ dài $\le 128$): Tái sử dụng giá trị của client.
- Nếu header vắng mặt, rỗng hoặc chứa ký tự đặc biệt nguy hiểm (nguy cơ Log Injection): Tự động sinh `UUID.randomUUID().toString()`.
- Luôn đính kèm header vào phản hồi: `response.setHeader("X-Correlation-ID", correlationId)`.

---

## 3. Mô Hình Che Giấu Dữ Liệu Nhạy Cảm (Log Masking Model)

### Quy tắc phát hiện và che giấu trong `MaskingMessageConverter`:

| Loại Dữ Liệu | Mẫu Nhận Diện (Regex Pattern) | Quy Tắc Thay Thế (Mask Replacement) |
|---|---|---|
| **Mật khẩu** | `(?i)("password"\s*:\s*")([^"]+)(")` | `$1[PROTECTED]$3` |
| **Raw API Key** | `(?i)("rawApiKey"\s*:\s*")([^"]+)(")` | `$1[PROTECTED]$3` |
| **Bearer Token** | `(?i)(Bearer\s+)[A-Za-z0-9\-_.~+/]+=*` | `$1[PROTECTED]` |
| **S3 Presigned Signature** | `(https?://[^?\s]+\?[^ \n\r]*X-Amz-Signature=)[^&\s\n\r]+` | `$1[PROTECTED]` |
| **AWS Secret Credentials** | `(?i)(aws_secret_access_key\s*=\s*)([^\s\n\r]+)` | `$1[PROTECTED]` |

---

## 4. Mô Hình Kiểm Chứng Hợp Đồng SQS Job Message (ContractVerificationPayload)

Cấu trúc đối chiếu dữ liệu SQS Message theo hợp đồng `../contracts/sqs-job-message.schema.json`:

```json
{
  "type": "object",
  "required": [
    "jobId",
    "videoId",
    "projectId",
    "inputBucket",
    "inputKey",
    "outputBucket",
    "outputPrefix",
    "createdAt",
    "schemaVersion"
  ],
  "properties": {
    "jobId": { "type": "string", "format": "uuid" },
    "videoId": { "type": "string", "pattern": "^vid_[a-f0-9]{12}$" },
    "projectId": { "type": "string", "pattern": "^prj_[a-f0-9]{12}$" },
    "inputBucket": { "type": "string" },
    "inputKey": { "type": "string" },
    "outputBucket": { "type": "string" },
    "outputPrefix": { "type": "string" },
    "createdAt": { "type": "string", "format": "date-time" },
    "schemaVersion": { "type": "integer", "enum": [1] }
  }
}
```
