# frontend/ - SaaS Web (React + Vite)

**Phụ trách:** Vũ (Lưu hỗ trợ hosting lên S3/CloudFront). Cần từ Sprint 2.

Giao diện tối thiểu: đăng nhập, quản lý project, upload bằng presigned URL, danh sách + trạng thái video,
trình phát HLS (hls.js), lấy link streaming và mã nhúng.

## Cấu trúc dự kiến
```
src/pages/    trang: login, projects, videos, video detail
src/api/      client gọi /api/* (sinh/viết theo docs/contracts/openapi.yaml)
src/player/   HLS player và mã nhúng
```

## Quy tắc
- Luôn gọi **đường dẫn tương đối** (`/api/...`, `/media/...`) để code giống nhau ở local (nginx) và cloud (CloudFront).
- Upload: xin presigned URL từ API, PUT thẳng lên S3, rồi gọi `confirm`. Video không đi qua API.
- Chạy dev: `npm run dev` (cổng 5173), truy cập qua `http://localhost:8000` khi bật stack `full`.
