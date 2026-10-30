# frontend/ - SaaS Web (React + Vite)

**Phụ trách:** Vũ (Lưu hỗ trợ hosting lên S3/CloudFront). Cần từ Sprint 2.

Giao diện tối thiểu: đăng nhập, quản lý project, upload bằng presigned URL, danh sách + trạng thái video,
trình phát HLS (hls.js), lấy link streaming và mã nhúng.

## Chạy Week 2 độc lập

Node **20.19+** (hoặc 22.12+). Không cần backend, Docker hay LocalStack.

```bash
cd frontend
npm ci
npm run dev
```

Mở `http://localhost:5173`. Stack `full` có thể proxy cùng ứng dụng qua `http://localhost:8000`.

- Tài khoản mock: `demo@transvidcloud.test` / `DemoPass123!`.
- Mặc định `VITE_API_MODE=mock`; session và dữ liệu demo được giữ qua reload trong localStorage.
- Copy `.env.example` thành `.env.local` nếu cần thay đổi cấu hình; khởi động lại Vite sau khi đổi.
- `VITE_MOCK_SESSION_MINUTES=0.1`: phiên hết hạn sau 6 giây để kiểm tra redirect.
- `VITE_MOCK_UPLOAD_FAILURE_ONCE=true`: PUT đầu tiên bị lỗi mạng, dùng **Retry upload** để thử lại.
- `VITE_MOCK_PROCESS=auto|freeze|fail`: mock tự chuyển `PENDING → PROCESSING → COMPLETED` (mặc định), đứng yên, hoặc kết thúc bằng `FAILED_TRANSCODE`.
- Danh sách và chi tiết poll mỗi 3 giây khi còn video `PENDING`/`PROCESSING`, dừng khi tab ẩn hoặc mọi video đã ở trạng thái cuối.
- Video `COMPLETED` phát HLS từ fixture hai rendition (360p/720p) do mock phục vụ tại `/media/*`; trang chi tiết có copy link streaming và mã nhúng, `/embed?video=<id>` công khai và không tự phát.
- Clipboard API cần secure context (localhost/HTTPS); ngoài ra dùng fallback `execCommand`. Trên LAN IP không HTTPS, copy có thể bị chặn.
- Tiến trình mock được mô phỏng theo thời gian vì Service Worker không cung cấp tiến trình upload byte; real dùng `XMLHttpRequest.upload`.
- PUT real **không** giới hạn tổng thời gian (200 MB trên đường truyền chậm là hợp lệ); chỉ hủy khi không có tiến trình trong 120 giây rồi cho phép **Retry upload**.
- Giới hạn 200 MB = **209715200 byte**. File mp4/mov/mkv/webm; kiểm tra client chỉ phục vụ UX, không xác thực nội dung video.
- Trang `/embed` đọc video công khai chỉ trong mock (chỉ video `COMPLETED`); backend thật chưa có contract cho embed — xem [`docs/contracts/frontend-week2-gaps.md`](../docs/contracts/frontend-week2-gaps.md).

## Kiểm thử và build

```bash
npm run api:check
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

Playwright tự khởi động bốn server riêng trên cổng 5181–5184 (mock auto, real không backend, mock lỗi PUT, mock lỗi transcode), không tái sử dụng server của project khác. Bộ e2e gồm 15 kịch bản: Week 1 cộng thêm upload → tự hoàn tất, manifest HLS, copy link/mã nhúng, embed công khai không autoplay và nhánh `FAILED_TRANSCODE`. Sau khi sửa contract, chạy `npm run api:generate` và commit types được sinh cùng thay đổi (khi tạo commit).

## Kết nối backend thật

```bash
VITE_API_MODE=real npm run dev
VITE_API_MODE=real npm run build
```

Không đổi code UI. Dùng nginx/CloudFront để route `/api/*` đến backend và `/media/*` đến storage; Vite độc lập không cung cấp backend. Khi không có backend, UI báo lỗi thay vì tự rơi về fixture. Không deploy bản build mock làm môi trường production.

Request/response hiện là **đề xuất DRAFT**, chưa được backend duyệt. Danh sách giả định và checklist hợp nhất nằm ở [`5. API Integration.md`](../docs/docs-frontend/5.%20API%20Integration.md).

## Cấu trúc
```
src/pages/       login, projects, videos, video detail, public embed
src/components/  layout, auth guard, modal, upload, polling, clipboard, notifications
src/api/         client /api/*, generated types, session, XHR transport
src/mocks/       MSW handlers, fixtures, HLS media fixture, simulated upload progress
src/player/      HlsPlayer dùng chung cho detail và embed
src/test/        thiết lập kiểm thử
e2e/             kịch bản trình duyệt Week 1 + Week 2
```

## Quy tắc
- Luôn gọi **đường dẫn tương đối** (`/api/...`, `/media/...`) để code giống nhau ở local (nginx) và cloud (CloudFront).
- Upload: xin presigned URL từ API, PUT thẳng lên S3, rồi gọi `confirm`. Video không đi qua API.
- Chạy dev: `npm run dev` (cổng 5173), truy cập qua `http://localhost:8000` khi bật stack `full`.
