# docs-frontend — Tài liệu frontend SaaS Web

Tài liệu cho `frontend/` — giao diện SaaS Web của TransVidCloud (React + Vite), phụ trách: **Vũ** (Lưu hỗ trợ hosting lên S3/CloudFront).

## Mục lục

| # | Tài liệu | Nội dung |
|---|---|---|
| 1 | [1. Introduction.md](1.%20Introduction.md) | Phạm vi, tech stack, cấu trúc thư mục, nguyên tắc làm việc |
| 2 | [2. Timelines.md](2.%20Timelines.md) | Epic A6 bóc nhỏ thành task theo sprint, mốc và thứ tự cắt giảm |
| 3 | [3. Pages & Features.md](3.%20Pages%20%26%20Features.md) | Màn hình và luồng người dùng: đăng nhập, project, upload, trạng thái video, trình phát HLS |
| 4 | [4. Design System.md](4.%20Design%20System.md) | Hệ thiết kế áp dụng từ template YouTube-style (token màu, chữ, khoảng cách, component) |
| 5 | 5. API Integration.md | *(đang để trống — chờ backend chốt API contract trước khi viết)* |

## Tài liệu liên quan

- **Template thiết kế:** [`Frontend template/`](Frontend%20template/) — bản tham chiếu YouTube-style: `DESIGN.md` (token, component, do's & don'ts), `theme.css`, `variables.css`, `tokens.json`. Đọc `4. Design System.md` để biết cách áp dụng vào app.
- **Hợp đồng tích hợp:** [`docs/contracts/`](../contracts/) — nguồn sự thật cho API (`openapi.yaml`, đang DRAFT), trạng thái video (`video-state-machine.md`), giới hạn upload (`upload-limits.md`).
- **Kế hoạch chung:** [`docs/introduction/1. Timelines.md`](../introduction/1.%20Timelines.md) — epic, phân công và mốc M0–M5 của cả nhóm.
- **Chạy local:** [`docs/introduction/2. LOCAL_AND_CICD.md`](../introduction/2.%20LOCAL_AND_CICD.md) — chạy dev qua nginx ở `http://localhost:8000`.
- **README code:** [`frontend/README.md`](../../frontend/README.md) — lệnh chạy, kiểm thử, build và bằng chứng từng tuần (`work/Week1–3.md`).

---

## Guideline chi tiết

Bộ quy tắc bắt buộc khi viết và review code frontend. Mọi thay đổi lớn phải tuân theo mục 8; vi phạm nguyên tắc mục 1–2 là lỗi phải sửa trước khi merge.

### 0. Chạy frontend (tham khảo nhanh)

**Yêu cầu:** Node **20.19+** (hoặc 22.12+). Week 2 chạy độc lập — không cần backend, Docker hay LocalStack.

**Dev (mock, mặc định):**

```bash
cd frontend
npm ci
npm run dev          # cổng 5173, VITE_API_MODE=mock
```

- Mở `http://localhost:5173`. Tài khoản mock: `demo@transvidcloud.test` / `DemoPass123!`; session và dữ liệu demo giữ qua reload trong localStorage.
- Cấu hình: copy `.env.example` → `.env.local` nếu cần đổi; khởi động lại Vite sau khi đổi env. Biến mock: `VITE_MOCK_SESSION_MINUTES` (đặt `0.1` để test redirect hết phiên), `VITE_MOCK_UPLOAD_FAILURE_ONCE=true` (PUT đầu lỗi mạng, thử lại bằng **Retry upload**), `VITE_MOCK_PROCESS=auto|freeze|fail`.
- Danh sách và chi tiết poll mỗi 3 giây khi còn video `PENDING`/`PROCESSING`; video `COMPLETED` phát HLS fixture 360p/720p tại `/media/*`.

**Dev nối backend thật:**

```bash
VITE_API_MODE=real npm run dev
```

Không đổi code UI; cần nginx/CloudFront route `/api/*` → backend (127.0.0.1:8080) và `/media/*` → storage (LocalStack 127.0.0.1:4566). Không có backend thì UI báo lỗi, không tự rơi về mock.

**Chạy qua nginx :8000** (mở `http://localhost:8000`, cả ba mode đều proxy `/api/*` và `/media/*`):

```bash
npm run dev                        # terminal 1
bash scripts/serve-nginx.sh dev    # proxy Vite 5173

npm run build:mock                 # trước mode static
bash scripts/serve-nginx.sh static # phục vụ dist-mock

npm run build                      # trước mode prod
bash scripts/serve-nginx.sh prod   # phục vụ dist
```

Config/log nginx ghi vào `frontend/.nginx/` (đã gitignore).

**Kiểm thử & build:**

```bash
npm run api:check                  # schema OpenAPI khớp với schema.d.ts
npm test                           # vitest unit
npm run build                      # dist/ — chỉ real, kèm kiểm bundle
npm run build:mock                 # dist-mock/ — chỉ dùng cho demo
npx playwright install chromium    # lần đầu
npm run test:e2e                   # 4 server nội bộ cổng 5181–5184
```

Chi tiết đầy đủ (e2e theo `E2E_BASE_URL`, bằng chứng worker HLS, ngưỡng bundle): [`frontend/README.md`](../../frontend/README.md).

### 1. Nguyên tắc kiến trúc

1. **Đường dẫn tương đối:** mọi request phải là `/api/...`, `/media/...` — không hard-code origin, không đọc `window.location` để ghép URL. Cùng code chạy được ở local (nginx :8000) và cloud (CloudFront).
2. **Upload không đi qua API:** luồng luôn là *xin presigned URL → PUT thẳng lên S3 (XHR) → gọi `confirm`*. API không bao giờ nhận bytes video. Không fetch rồi upload bằng `fetch()` — cần `XMLHttpRequest.upload` để có tiến trình.
3. **Client check chỉ để UX:** kiểm tra dung lượng (200 MB = 2 097 200 byte) và định dạng (mp4/mov/mkv/webm) phía client để chặn sớm; không coi là lớp bảo vệ, không xác thực nội dung video. Nguồn sự thật: `docs/contracts/upload-limits.md`.
4. **Contract-first:** endpoint, trạng thái video, key S3, message lỗi lấy từ `docs/contracts/` — muốn đổi thì sửa contract trước, không tự quyết trong frontend.
5. **Polling cho trạng thái:** trạng thái video (xem `docs/contracts/video-state-machine.md`) cập nhật bằng polling (mỗi 3 giây khi còn `PENDING`/`PROCESSING`), dừng khi tab ẩn hoặc mọi video đã ở trạng thái cuối. Không realtime (SSE/WebSocket) trong MVP.
6. **Không fallback ngầm về mock:** khi `VITE_API_MODE=real` mà backend lỗi, UI hiển thị lỗi; tuyệt đối không tự rơi về fixture/mock.

### 2. Cấu trúc thư mục & trách nhiệm

| Thư mục | Trách nhiệm | Ghi chú đặt file |
|---|---|---|
| `src/pages/` | Trang theo route: `Login`, `Projects`, `Videos`, `VideoDetail`, `Embed` | Một file một trang; logic dùng lại → tách xuống `components/` |
| `src/components/` | Component dùng chung: layout, auth guard, modal, upload dialog, polling (`useResource`), clipboard, notice | Component giao diện + hook dùng chung |
| `src/api/` | Client gọi `/api/*`, types sinh từ OpenAPI (`schema.d.ts`), session, transport XHR, luồng upload | Không chứa UI; không viết tay type trùng contract |
| `src/player/` | `HlsPlayer` (hls.js) dùng chung cho detail và embed | Lazy-load (dynamic import); không import trực tiếp vào main bundle |
| `src/mocks/` | MSW handlers, fixtures, HLS media fixture, tiến trình upload giả lập | Chỉ chạy khi `VITE_API_MODE=mock`; không import từ mocks vào code thật ngoài `src/runtime/` |
| `src/runtime/` | Chọn mock/real lúc khởi động, đăng ký service worker | Lớp duy nhất biết đến `VITE_API_MODE` |
| `src/test/` | Thiết lập kiểm thử (vitest setup) | |
| `e2e/` | Kịch bản Playwright (Week 1 + 2: upload, manifest HLS, embed, retry, failed transcode) | |
| `scripts/` | `serve-nginx.sh`, `check-production-bundle.mjs` | |

Quy ước bổ sung:

- Test unit đặt **cạnh file nguồn**: `Foo.tsx` ↔ `Foo.test.tsx` (vitest + Testing Library).
- Asset hằng số (favicon…) đặt `src/assets/` để Vite hăm tên; `public/` chỉ giữ `mockServiceWorker.js`.
- Giới hạnUpload, trạng thái video… không hard-code chuỗi trạng thái rải rác — đi qua type/hằng số từ `src/api` (sinh từ contract).

### 3. Quy ước code TypeScript / React

- **TypeScript strict**, React 19 function component + hooks; không class component, không thêm state library — state cục bộ `useState`/hook tự viết (`useResource`).
- **Đặt tên:** component/file `.tsx` PascalCase; util & hook camelCase, hook có tiền tố `use`.
- **Router (react-router-dom v7):** route hiện có — `/login`, `/embed` (công khai, không autoplay), `/projects`, `/projects/:id/videos`, `/videos/:id` (bên trong `RequireAuth` + `Layout`). Route mới phải quyết định công khai hay cần đăng nhập ngay từ đầu.
- **Kiểu trạng thái video và assets** bắt buộc lấy từ `src/api/schema.d.ts` (sinh từ OpenAPI); không khai báo `interface` song song cùng dữ liệu đó.
- **Style:** dùng token CSS (`src/variables.css`) — không hard-code màu/kích thước trong component. Chi tiết token: `4. Design System.md`.
- **Clipboard:** dùng helper `components/clipboard.ts` (Clipboard API + fallback `execCommand`), không gọi API thẳng trong component.

### 4. Tích hợp API & mock

- Quy trình khi contract đổi: sửa `docs/contracts/openapi.yaml` → chạy `npm run api:generate` → commit `src/api/schema.d.ts` **cùng** thay đổi. `npm run api:check` phải pass (dùng để phát hiện schema lệch).
- **Hai mode runtime, không đổi code UI:** `VITE_API_MODE=mock` (MSW + fixture, mặc định dev) và `VITE_API_MODE=real`. Biến phụ trợ cho mock: `VITE_MOCK_SESSION_MINUTES`, `VITE_MOCK_UPLOAD_FAILURE_ONCE`, `VITE_MOCK_PROCESS=auto|freeze|fail`.
- Cấu hình qua `.env.local` (dev) / `.env.production` (real) / `.env.mock` (demo); đổi env phải khởi động lại Vite.
- Request/response hiện là **DRAFT**; danh sách giả định và checklist hợp nhất nằm ở `5. API Integration.md` và `docs/contracts/frontend-week2-gaps.md`.

### 5. Design system

- Nền tảng là template YouTube-style trong [`Frontend template/`](Frontend%20template/); bản rút gọn + cách áp dụng: `4. Design System.md`.
- Quy tắc nhanh: ink `#0f0f0f` (không `#000000`); signal-blue `#065fd4` cho link/outline button, không làm CTA filled; play-red `#ff0000` chỉ cho logo play triangle. Roboto 400/500/700, heading tối đa 20px. Base spacing 4px (scale 4/8/12/16/24/32/240); bo góc pill 18px, card 10px.
- Trạng thái video dùng graphite/ash cho chờ/xử lý; lỗi dùng đỏ riêng (`#cc0000` hoặc token `--color-error` khi thêm), không mượn play-red.

### 6. Kiểm thử

- **Unit (vitest + Testing Library):** mọi module `src/api`, `src/components` có logic đều có test cạnh file. Tính năng mới kèm unit test cho nhánh chính + nhánh lỗi.
- **E2E (Playwright):** mặc định `npm run test:e2e` tự khởi động 4 server nội bộ (cổng 5181–5184: mock auto, real không backend, mock lỗi PUT, mock lỗi transcode); không tái sử dụng server của project khác. Chạy với app có sẵn thì đặt `E2E_BASE_URL` (khi đó bỏ qua các test cần server phụ); `E2E_HOSTED=1` thêm kiểm tra service worker/manifest; `E2E_REAL_MEDIA_PATH` bật test hls.js với output worker thật.
- **Bắt buộc phủ các nhánh thất bại:** PUT lỗi mạng rồi Retry upload, timeout không tiến trình 120 giây, `FAILED_TRANSCODE`. Không chỉ test happy path.

### 7. Build & triển khai

- `npm run build` → `dist/` (chỉ real; fail cố ý nếu `VITE_API_MODE ≠ real`, kèm `scripts/check-production-bundle.mjs`: cấm mockServiceWorker/mocks/fixture trong bundle; ngưỡng main ≤ 700 000 B, chunk `HlsPlayer` ≥ 200 000 B để đảm bảo lazy).
- `npm run build:mock` → `dist-mock/` chỉ dùng demo — **không deploy bản mock làm production**.
- Hosting qua nginx/CloudFront route `/api/*` → backend, `/media/*` → storage; frontend không biết origin backend.
- Node ≥ 20.19 (hoặc 22.12+); ghi rõ kết quả build/kiểm chứng kèm ngày trong `frontend/README.md` hoặc `work/WeekN.md`.

### 8. Git & quy trình thay đổi

- Quyết định thiết kế quan trọng (đổi thư viện player, đổi cách quản lý state, đổi hệ thiết kế) thì cập nhật docs này và mở ADR nếu ảnh hưởng nhóm khác.
- Muốn đổi endpoint, message, trạng thái: sửa `docs/contracts/` trước (contract-first), không tự quyết trong frontend.
- Commit theo phong cách hiện có (tiếng Anh, mệnh lệnh, ví dụ `Complete frontend week 2: live status, HLS player and embed`); gộp kèm `schema.d.ts` sinh lại và docs cập nhật trong cùng commit thay đổi contract.
- Bằng chứng nghiệm thu (kết quả e2e, build, số liệu) ghi vào `work/WeekN.md` để báo cáo nhóm dùng lại.
