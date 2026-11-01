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
