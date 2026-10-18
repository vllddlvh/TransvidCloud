# local/nginx/

Giả lập CloudFront khi chạy local: một địa chỉ `http://localhost:8000` với 3 đường dẫn giống production.

| Path | Đích | Tương ứng cloud |
|---|---|---|
| `/` | Vite dev server trên máy (`localhost:5173`) | CloudFront -> S3 frontend |
| `/api/*` | container `backend:8080` | CloudFront -> EC2 API |
| `/media/*` | bucket `tvc-local-output` trên LocalStack | CloudFront -> S3 output (OAC) |

Frontend luôn gọi đường dẫn tương đối (`/api/...`, `/media/...`) để code giống hệt local và cloud.
