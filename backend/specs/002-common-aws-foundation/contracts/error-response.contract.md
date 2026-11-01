# API Error Contract: Chuẩn Phản hồi Lỗi REST (F-002)

**Feature**: `002-common-aws-foundation` | **Ngày lập**: 2026-10-31 | **Nguồn sự thật**: `contracts/openapi.yaml`

Tài liệu này xác lập hợp đồng giao tiếp cho mọi phản hồi lỗi HTTP (`4xx` và `5xx`) được trả về từ TransvidCloud Backend.

---

## 1. JSON Schema Cốt lõi (`ErrorResponse`)

Mọi phản hồi lỗi đều tuân thủ schema JSON sau:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "ErrorResponse",
  "type": "object",
  "required": ["code", "message", "timestamp"],
  "properties": {
    "code": {
      "type": "string",
      "description": "Mã định danh lỗi máy đọc được theo chuẩn ErrorCode",
      "example": "VALIDATION_ERROR"
    },
    "message": {
      "type": "string",
      "description": "Thông điệp mô tả chi tiết lỗi dành cho người dùng",
      "example": "Dữ liệu yêu cầu không hợp lệ"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time",
      "description": "Thời điểm phát sinh lỗi theo chuẩn ISO-8601 UTC",
      "example": "2026-10-31T10:15:30Z"
    },
    "details": {
      "type": "object",
      "additionalProperties": true,
      "description": "Thông tin bổ sung giải thích chi tiết nguyên nhân vi phạm",
      "example": {
        "email": "Email không đúng định dạng",
        "password": "Mật khẩu phải có ít nhất 8 ký tự"
      }
    }
  }
}
```

---

## 2. Bảng Phản ánh Mã Lỗi HTTP & Mã Định Danh

| Mã HTTP | Tên HTTP Status | Mã code (`ErrorCode`) | Khi nào xảy ra? |
|---|---|---|---|
| `400` | Bad Request | `VALIDATION_ERROR` | Request body hoặc query param vi phạm các ràng buộc `@Valid` / Bean Validation. |
| `400` | Bad Request | `MALFORMED_JSON` | Body request không thể parse do lỗi cú pháp JSON (thiếu ngoặc, ký tự lạ). |
| `400` | Bad Request | `TYPE_MISMATCH` | Tham số trên URL hoặc query string không đúng kiểu dữ liệu khai báo. |
| `401` | Unauthorized | `UNAUTHENTICATED` | Thiếu token, token hết hạn hoặc chữ ký JWT không hợp lệ. |
| `403` | Forbidden | `UNAUTHORIZED` | Token hợp lệ nhưng tài khoản không có quyền thao tác trên tài nguyên của người khác. |
| `404` | Not Found | `NOT_FOUND` | Không tìm thấy video, project, hoặc endpoint yêu cầu. |
| `405` | Method Not Allowed | `METHOD_NOT_ALLOWED` | Gọi sai phương thức HTTP trên endpoint (ví dụ gọi `PUT` thay vì `POST`). |
| `409` | Conflict | `CONFLICT` | Trạng thái hiện tại của tài nguyên xung đột (ví dụ xóa video đang ở trạng thái `PROCESSING`). |
| `413` | Payload Too Large | `UPLOAD_LIMIT_EXCEEDED` | Khai báo dung lượng file vượt quá giới hạn tối đa (200 MB = 209,715,200 bytes). |
| `422` | Unprocessable Entity | `UNSUPPORTED_MEDIA_TYPE` | Định dạng mime-type không nằm trong danh sách video cho phép. |
| `500` | Internal Server Error | `INTERNAL_SERVER_ERROR` | Lỗi ngoại lệ hệ thống chưa phân loại hoặc lỗi kết nối hạ tầng ngoại vi. |

---

## 3. Các Ví dụ Phản hồi Tiêu biểu

### 3.1. Lỗi Validation dữ liệu đầu vào (HTTP 400)
```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "code": "VALIDATION_ERROR",
  "message": "Dữ liệu yêu cầu không hợp lệ",
  "timestamp": "2026-10-31T10:15:30Z",
  "details": {
    "name": "Tên dự án không được để trống",
    "email": "Email không đúng định dạng"
  }
}
```

### 3.2. Lỗi Vượt quá Giới hạn Tải lên (HTTP 413)
```http
HTTP/1.1 413 Payload Too Large
Content-Type: application/json

{
  "code": "UPLOAD_LIMIT_EXCEEDED",
  "message": "Dung lượng file vượt quá giới hạn tối đa 200 MB (209715200 bytes)",
  "timestamp": "2026-10-31T10:15:30Z"
}
```

### 3.3. Lỗi Chưa Xác thực (HTTP 401)
```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{
  "code": "UNAUTHENTICATED",
  "message": "Yêu cầu xác thực tài khoản hoặc token không hợp lệ",
  "timestamp": "2026-10-31T10:15:30Z"
}
```

### 3.4. Lỗi Hệ thống Nội bộ (HTTP 500)
```http
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "Đã xảy ra sự cố nội bộ trong hệ thống. Vui lòng thử lại sau",
  "timestamp": "2026-10-31T10:15:30Z"
}
```
*(Ghi chú: Tuyệt đối không có trường `stackTrace`, `exceptionClass` hay thông tin rò rỉ nào khác trong response).*
