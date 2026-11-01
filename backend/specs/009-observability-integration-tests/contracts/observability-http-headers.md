# Contract: HTTP Observability & Correlation Headers

**Version**: 1.0  
**Scope**: All REST API Endpoints under `/api/**`  
**Date**: 2026-11-01  

---

## 1. Request Headers

| Header Name | Type | Required | Description | Example |
|---|---|---|---|---|
| `X-Correlation-ID` | `String` | Optional | Mã định danh tương quan request do client (Frontend / API Gateway) gửi lên để truy vết giao dịch. | `550e8400-e29b-41d4-a716-446655440000` |
| `X-Request-ID` | `String` | Optional | Tên header thay thế (Fallback), được chấp nhận nếu `X-Correlation-ID` vắng mặt. | `req-9876543210` |

### Validation Rules:
- Độ dài tối đa: 128 ký tự.
- Ký tự hợp lệ: Chữ cái (`A-Z`, `a-z`), số (`0-9`), dấu gạch ngang (`-`), dấu gạch dưới (`_`).
- Nếu vi phạm validation hoặc vắng mặt: Backend tự động sinh mới UUID v4 và sử dụng cho request đó.

---

## 2. Response Headers

| Header Name | Type | Guaranteed | Description | Example |
|---|---|---|---|---|
| `X-Correlation-ID` | `String` | 100% | Luôn xuất hiện trong mọi phản hồi HTTP (kể cả 2xx, 4xx, 5xx) giúp client đối soát sự cố khi gọi API. | `550e8400-e29b-41d4-a716-446655440000` |
