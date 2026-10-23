# Week 1 — Khung project + mock layer + luồng chính (F1–F3, chế độ độc lập)

Tuần làm việc đầu tiên của frontend, tương ứng **Sprint 2** trong [2. Timelines.md](../2.%20Timelines.md).

> **Chế độ làm việc mới — phát triển độc lập với backend.** Frontend không chờ backend (Phương, A2/A3) mà xây toàn bộ luồng trên **mock API sinh theo contract** (`docs/contracts/openapi.yaml`). Hai phần chỉ **hợp nhất ở tuần 9** (trước M4): khi đó đổi sang backend thật, rà lệch contract và chạy e2e. Lợi ích: Vũ làm frontend không bị chặn, không phụ thuộc tiến độ ai; rủi ro (mock drift) khống chế bằng contract-first — xem cuối trang.

Phạm vi tuần này: **F1 Dựng khung project · F2 Đăng nhập + project · F3 Upload bằng presigned URL** — toàn bộ chạy trên mock, tổng 1,5 pd baseline.

- **Chủ trì:** Vũ.
- **Nguyên tắc áp dụng cho mọi task:** đường dẫn tương đối `/api/...`, `/media/...` (không hard-code origin); mọi màu/kích thước qua token `variables.css` (không hex trong JSX); TypeScript bắt buộc kiểu cho trạng thái video và assets; **mock và real cùng một interface** — UI không biết mình đang gọi ai.

## Mục tiêu cuối tuần

Người dùng mở `http://localhost:8000`, đăng nhập (mock JWT), tạo/xem project, upload một video mp4 hợp lệ qua luồng presigned (mock presigned URL + PUT mô phỏng) tới `confirm` thành công — video xuất hiện trong danh sách với trạng thái `UPLOADING → PENDING`. **Không cần backend, không cần LocalStack** trong tuần này. Trình phát HLS và polling thật là việc của tuần 2 (F4–F5, vẫn trên mock trước).

---

## Buổi 1 — F1. Dựng khung project (0,5 pd)

Khởi tạo `frontend/` (hiện chỉ có README) thành app chạy được, áp design token và layout chuẩn. Task này vốn không phụ thuộc backend — giữ nguyên như kế hoạch cũ.

- [ ] **F1.1 Scaffold Vite + React + TypeScript**
  - `npm create vite@latest` (react-ts), Node 20 theo [LOCAL_AND_CICD](../../introduction/2.%20LOCAL_AND_CICD.md) mục 1.1
  - Tạo cấu trúc thư mục theo [1. Introduction.md](../1.%20Introduction.md): `src/pages/`, `src/api/`, `src/player/` (player chỉ tạo folder + file trống, code ở tuần 2)
  - Dev server cổng **5173**, truy cập qua `http://localhost:8000` khi bật stack `full` (nginx proxy); chạy được cả `npm run dev` đơn lẻ khi không bật stack — không cần Docker cho tuần này
- [ ] **F1.2 Áp design token + layout**
  - Import `Frontend template/variables.css` vào global CSS; mọi component dùng `var(--...)` — xem [4. Design System.md](../4.%20Design%20System.md) mục 6
  - **Sidebar** trái 240px fixed (nav item 40px cao, icon 24px + label 14px; active = `#0f0f0f`, hover fill xám nhạt) và **Header** 56px sticky (hamburger + logo trái, search input giữa ~640px, icon button + Sign in phải) — tham chiếu [DESIGN.md](../Frontend%20template/DESIGN.md) mục Components
  - Tuân thủ Do's & Don'ts: không `#000000`, không shadow, không gradient, không vượt font 20px; `#065fd4` chỉ dùng cho link/outline button; `#ff0000` chỉ cho logo play triangle
- [ ] **F1.3 Router + khung trang**
  - React Router với các route: `/login`, `/projects`, `/projects/:id/videos`, `/videos/:id` (chi tiết), `/embed` (chỉ tạo route trống)
  - Mỗi route hiển thị placeholder dùng đúng layout (login và embed **không** có sidebar/header)
  - Toast/thông báo dùng Card (nền trắng, border 1px `#c6c6c6`, radius 10px) — phục vụ xử lý lỗi chung ở [3. Pages & Features.md](../3.%20Pages%20%26%20Features.md) cuối trang

**Xong khi:** mở `http://localhost:8000` thấy layout đúng token, điều hướng 4 route hoạt động, `npm run build` pass — tất cả không cần backend hay Docker.

## Buổi 2 — Mock API layer + F2. Đăng nhập + project (0,5 pd)

Nền tảng của chế độ độc lập: **một lớp mock trung thành với contract**, sau đó làm trang login/project trên nền đó.

- [ ] **F2.1 Mock API layer (làm trước F2.2)**
  - Cài **MSW** (Mock Service Worker) — chặn cả `fetch` lẫn `XMLHttpRequest`, nên progress bar upload ở F3 vẫn hoạt động thật trên mock
  - `src/api/` định nghĩa **interface duy nhất** cho từng nhóm API (auth, projects, videos): hàm signature, kiểu request/response lấy nguyên từ `docs/contracts/openapi.yaml` và [video-state-machine.md](../../contracts/video-state-machine.md)
  - Hai implementation phía sau interface: **mock** (MSW handler + fixture data) và **real** (gọi `/api/...` thật) — chọn bằng biến môi trường `VITE_API_MODE=mock|real`, mặc định tuần này là `mock`. **UI không bao giờ import trực tiếp MSW hay biết chế độ đang chạy**
  - Fixture data: 1 user test, 2–3 project, video mẫu ở **mọi trạng thái** (`UPLOADING`, `PENDING`, `PROCESSING`, `COMPLETED` có assets, cả ba `FAILED_*` kèm `errorCode`) — dùng lại cho F4/F5 tuần 2 và cho demo UI mọi nhánh trạng thái
  - Mock hành vi lỗi: sai mật khẩu → 401, token "hết hạn" sau N phút → 401, `POST videos` khai báo vượt 200 MB → 413, sai định dạng → 422 — phủ đúng bảng xử lý lỗi trong [3. Pages & Features.md](../3.%20Pages%20%26%20Features.md)
- [ ] **F2.2 Trang `/login`**
  - Form email + mật khẩu → login (qua interface ở trên); lưu token mock; redirect `/projects`
  - Sai thông tin: thông báo inline, **không reload trang**. Component: pill button outline (`#065fd4` border + text, nền trắng, radius 18px) + input pill
  - Đăng ký qua UI **không thuộc MVP** — trong mock, user test có sẵn; đăng ký thật chỉ cần khi hợp nhất tuần 9 (curl `/auth/register`)
- [ ] **F2.3 Guard + danh sách / tạo project**
  - Route guard: trang cần đăng nhập redirect về `/login` khi chưa có token; token hết hạn (mock) → đá về login kèm thông báo phiên hết hạn
  - `/projects`: danh sách card project (tên, số video, thời gian tạo); nút "Tạo project" (pill button neutral) mở form tên + mô tả; click card → `/projects/:id/videos` (trang placeholder ở tuần này)
  - Tạo project qua mock cập nhật fixture → thấy kết quả ngay không cần backend

**Xong khi:** đăng nhập bằng user mock, tạo project, reload giữ phiên, hết hạn mock bị đá về login đúng luồng; tắt MSW (`VITE_API_MODE=real`) thì trang báo lỗi mạng một cách đúng nghĩa — chứng tỏ UI chỉ nói chuyện với interface.

## Buổi 3 — F3. Upload bằng presigned URL trên mock (0,5 pd)

Luồng presigned 3 bước dựng **đầy đủ UI/state**, chỉ phần "thật" của S3 hoãn tới tuần 9.

- [ ] **F3.1 Modal upload + kiểm tra client**
  - Nút "Tải video lên" ở trang danh sách video mở modal (dùng Card)
  - Kiểm tra chỉ để UX (không phải lớp bảo vệ — nguồn sự thật là [upload-limits.md](../../contracts/upload-limits.md)): dung lượng ≤ **200 MB**, đuôi **mp4/mov/mkv/webm**; vi phạm → chặn ngay kèm thông báo cụ thể
- [ ] **F3.2 Luồng presigned 3 bước (mock)**
  1. Khai báo `sizeBytes`, `contentType` → nhận presigned URL (mock trả URL giả, hết hạn 15 phút); mock trả 413/422 khi khai báo vượt giới hạn → hiện lỗi, dừng
  2. PUT file bằng **XMLHttpRequest** để có progress bar (fetch không hỗ trợ upload progress) — MSW chặn XHR và mô phỏng tiến trình theo thời gian; thêm cơ chế mock lỗi mạng giữa chừng để kiểm tra nút thử lại
  3. `confirm` → đóng modal, video xuất hiện trong danh sách, trạng thái `UPLOADING → PENDING`; mock có thể tiếp tục tự chuyển `PENDING → PROCESSING → COMPLETED` sau vài giây để tuần 2 test polling/player sớm
- [ ] **F3.3 Ghi nhận hợp đồng — đầu ra quan trọng nhất của tuần này**
  - Mỗi giả định khi viết mock (tên field, mã lỗi, dạng response, thứ tự bước) ghi ngay vào `5. API Integration.md` với nhãn **"giả định — chờ Phương xác nhận"** — đây là checklist đối chiếu khi hợp nhất tuần 9
  - Nếu thấy contract thiếu/thất thiển (vd chưa định nghĩa error body, cursor pagination), **không tự bịa trong mock**: mở issue/ghi vào `docs/contracts/` để Phương xử lý (contract-first)

**Xong khi:** upload file 10 MB mp4 qua mock chạy đủ 3 bước, progress bar chuyển động, kết thúc ở `PENDING`; file 201 MB bị chặn ở client lẫn bị mock từ chối 413; ngắt mạng giữa chừng (tắt MSW) thì nút thử lại hoạt động.

---

## Kế hoạch hợp nhất (tuần 9, sau M3 — để đối chiếu, không làm tuần này)

| Bước | Việc | Ghi chú |
|---|---|---|
| 1 | Đổi `VITE_API_MODE=real`, giữ nguyên mọi code UI | Interface phải giữ nguyên — nếu phải sửa UI nghĩa là mock drift, ghi lại nguyên nhân |
| 2 | Rà từng "giả định" đã ghi trong `5. API Integration.md` với backend thật | Sai ở đâu sửa contract hoặc sửa mock-kiểu (types), không sửa thẳng logic UI |
| 3 | Chạy stack local đầy đủ (`make up`): presigned PUT thật lên LocalStack, rà CORS (`S3_PRESIGN_ENDPOINT`, bucket input) | Bẫy CORS đã ghi trong [LOCAL_AND_CICD](../../introduction/2.%20LOCAL_AND_CICD.md) Phần 3 |
| 4 | Polling + HLS thật (F4/F5 từng làm trên mock), e2e `upload → COMPLETED → phát` | Hợp nhất trước M4 để kịp đóng băng cuối tuần 9 |
| 5 | Xóa/tách fixture không dùng, giữ mock cho demo UI khi backend không trực tuyến | Mock vẫn có giá trị cho diễn tập R1 |

## Rủi ro của chế độ độc lập và cách khống chế

1. **Mock drift — mock không giống backend thật.** Khống chế: mock sinh từ `openapi.yaml` (không tự bịa endpoint); mọi giả định ghi `5. API Integration.md`; hợp nhất ở tuần 9 có đủ thời gian sửa trước M4 (nửa sau tuần 9 mới đóng băng).
2. **Hợp nhất dồn vào tuần 9 trùng benchmark/báo cáo (M4).** Khống chế: nếu tuần 8 backend đã có endpoint thật, **hợp nhất sớm từng phần** (auth trước, upload sau) — chế độ độc lập không cấm tích hợp sớm, chỉ không bắt buộc.
3. **Quá tải vì kiêm việc worker (A4).** Mock giúp F2/F3 không bị chặn tiến độ ai, nhưng vẫn cần chốt F1 sớm; nếu dồn nguồn lực vào worker cho M2 thì F2/F3 trượt tối đa tới tuần 8 — không cắt upload presigned và hiển thị trạng thái tới `COMPLETED` (luồng cốt lõi demo).
4. **Hành vi S3 thật khác mô phỏng (CORS, SignatureDoesNotMatch, Content-Length).** Đây là phần **không mock được** — chấp nhận là nợ kỹ thuật của tuần 9, đã có sẵn bảng xử lý sự cố để đối chiếu.

## Việc cộng thêm nếu sớm (không mở P1)

- Trang `/projects/:id/videos` hiển thị danh sách video từ fixture — kèm badge đủ 7 trạng thái state machine, tận dụng fixture đã dựng ở F2.1.
- Bắt skeleton `5. API Integration.md` đầy đủ từ contract (chưa cần chờ backend chốt hết).
- Trang `/videos/:id` cơ bản: thông tin + trạng thái từ fixture — player thật để dành tuần 2.
