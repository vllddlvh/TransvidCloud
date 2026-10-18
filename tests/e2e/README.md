# tests/e2e/ - Kiểm thử đầu-cuối

**Phụ trách:** Lưu chủ trì (Vũ, Phương viết kịch bản), cả nhóm bổ sung.

Kịch bản chính: đăng ký -> tạo project -> xin presigned URL -> upload video mẫu -> confirm -> chờ `COMPLETED`
-> kiểm tra có `master.m3u8`, 360p/720p, thumbnail -> phát được qua `/media/*`.

Chạy được ở hai chế độ qua biến `BASE_URL`: local (`http://localhost:8000`) và cloud (domain CloudFront).
Cùng một bộ test, hai môi trường: đó là cách chứng minh "local chạy được thì cloud chạy được".
