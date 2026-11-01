# Implementation Plan: F-011 — Đồng bộ Đầy đủ Tính năng Frontend theo Đặc tả Backend (F-003 -> F-008)

**Branch**: `011-frontend-feature-parity` (`phuong-integration`) | **Date**: 2026-11-01T21:51:00+07:00 | **Spec**: [`specs/011-frontend-feature-parity/spec.md`](spec.md)

**Input**: Feature specification from `specs/011-frontend-feature-parity/spec.md`

---

## Summary

Bổ sung và đồng bộ toàn bộ các tính năng còn thiếu trên Frontend (`frontend/src/`) để khớp 100% với năng lực và đặc tả của Backend (`F-003` $\rightarrow$ `F-008` tại `backend/specs/` và `contracts/openapi.yaml`):
1. **Chuyển đổi chất lượng phát (Auto / 720p / 360p) & Quản lý Video Assets (`F-008`):** Bổ sung Quality Selector (`Auto`, `720p`, `360p`) ngay trên `HlsPlayer.tsx`; hiển thị đầy đủ 4 đường dẫn tài nguyên đầu ra (`Master HLS`, `720p HLS`, `360p HLS`, `Thumbnail`) kèm nút sao chép nhanh và toàn bộ siêu dữ liệu vòng đời (`title`, `attemptCount`, `startedAt`, `completedAt`, `updatedAt`) trên `VideoDetail.tsx`.
2. **Quản lý Developer API Keys theo Project (`F-005`):** Triển khai giao diện và API client cho `GET /api/projects/{projectId}/api-keys`, `POST /api/projects/{projectId}/api-keys` (giới hạn 5 keys/project, hiển thị `rawKey` một lần duy nhất kèm nút Copy & cảnh báo bảo mật) và `DELETE /api/projects/{projectId}/api-keys/{keyHash}` (Revoke tức thì).
3. **Quản trị vòng đời Project (`F-004`):** Bổ sung truy vấn chi tiết `GET /api/projects/{projectId}`, chỉnh sửa thông tin & cấu hình `webhookUrl` (`PATCH /api/projects/{projectId}`), và xóa mềm Project (`DELETE /api/projects/{projectId}` kèm xử lý lỗi `409 RESOURCE_IN_USE` khi còn video đang xử lý).
4. **Hoàn thiện vòng đời Video (`F-006`, `F-008`):** Bổ sung ô nhập tiêu đề `title` (1–200 ký tự) trong `UploadDialog.tsx`, chức năng xóa video (`DELETE /api/videos/{videoId}` có bảo vệ khóa khi `status === 'PROCESSING'`), và hỗ trợ phân trang con trỏ (`limit`, `cursor`, `hasMore`).

---

## Technical Context

**Language/Version**: TypeScript 5.8, React 19, Node.js $\ge 20$ (kết nối Backend Java 21 / Spring Boot 3.4.5)  
**Primary Dependencies**: React Router 7, `hls.js`, `lucide-react`, Vite 6, MSW 2 (Mock Service Worker)  
**Storage**: Không lưu trạng thái lâu dài trên FE ngoài `localStorage` cho JWT Session (`tvc.session`); dữ liệu lưu tại DynamoDB & S3 trên Backend/LocalStack  
**Testing**: Vitest + React Testing Library (`npm test`), OpenAPI TypeScript drift check (`npm run api:check`), Playwright Real-Stack E2E (`npx playwright test`)  
**Target Platform**: Trình duyệt Web hiện đại (Desktop & Mobile responsive), phục vụ qua Vite dev server / Nginx Edge  
**Project Type**: Single-Page Web Application (Frontend trong Monorepo `TransvidCloud`)  
**Performance Goals**: Chuyển đổi độ phân giải HLS mượt mà giữ nguyên vị trí phát; mọi thao tác CRUD phản hồi tức thì dưới 200ms trên mạng nội bộ  
**Constraints**: Tuân thủ tuyệt đối hợp đồng `/contracts/openapi.yaml` (SSOT); không lưu `rawKey` của API Key sau khi đóng hộp thoại khởi tạo; không cho phép xóa video đang `PROCESSING` hoặc xóa Project còn video đang xử lý  
**Scale/Scope**: Bao phủ toàn bộ 16 endpoints trong `contracts/openapi.yaml` trên 5 màn hình/luồng chính (`Login`, `Projects`, `Videos`, `VideoDetail`, `Embed`)  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá tuân thủ trong Thiết kế F-011 | Kết quả |
|---|---|:---:|
| **I. Code Quality & Maintainability** | Tách biệt rõ tầng API client (`client.ts`), normalizers (`types.ts`) và các UI components/modals; giữ mã nguồn ngắn gọn, tuân thủ DRY, KISS và YAGNI. | **PASS** |
| **II. Architecture & Modularity** | Giữ nguyên cấu trúc module của `frontend/src/` (`api/`, `components/`, `pages/`, `player/`, `mocks/`), không thêm thư viện ngoài. | **PASS** |
| **III. Security & Privacy** | Tuân thủ `F-005`: `rawKey` của API Key chỉ hiển thị 1 lần trong state tạm của modal tạo khóa, tuyệt đối không lưu vào `localStorage` hay log; mọi request quản trị đều đính kèm `Bearer <JWT>`. | **PASS** |
| **IV. Correctness & Data Integrity** | Khớp 100% với `/contracts/openapi.yaml`; xử lý chuẩn xác các mã trạng thái `201`, `202`, `204 No Content`, `400`, `409 Conflict`. | **PASS** |
| **V. Testing & Verification** | Kiểm chứng bằng cả 3 lớp: OpenAPI contract check (`npm run api:check`), Unit/Component tests (`npm test`), và Playwright E2E trên Docker + LocalStack thật. | **PASS** |
| **VI. Error Handling & Observability** | Chuẩn hóa việc bóc tách `ErrorResponse` (`code`, `message`) từ Backend để hiển thị thông báo rõ ràng trên UI (đặc biệt với lỗi `409 RESOURCE_IN_USE` và giới hạn 5 API Keys). | **PASS** |
| **VII. Documentation & Technical Decisions** | Khởi tạo đầy đủ bộ tài liệu SDD tại `specs/011-frontend-feature-parity/` và cập nhật hướng dẫn tự kiểm thử tại `docs/LOCAL_INTEGRATION_AND_TEST_GUIDE.md`. | **PASS** |
| **VIII. Dependency & Engineering Discipline** | Sử dụng 100% dependencies hiện có (`react`, `hls.js`, `lucide-react`), không cài thêm package mới. | **PASS** |
| **IX. Human Oversight & Governance** | Lập kế hoạch chi tiết từ yêu cầu rà soát của người dùng, ưu tiên Backend làm tham chiếu chuẩn (SSOT). | **PASS** |

---

## Project Structure

### Documentation (this feature)

```text
backend/specs/011-frontend-feature-parity/
├── spec.md              # Đặc tả yêu cầu đồng bộ tính năng FE theo BE (US1 -> US4)
├── plan.md              # Kế hoạch kiến trúc và lộ trình thực thi (Tệp này)
├── research.md          # Nghiên cứu kỹ thuật Quality Selector, API Keys, Project/Video CRUD
├── data-model.md        # Thiết kế TypeScript interfaces, normalizers và bảng ánh xạ API
├── quickstart.md        # Hướng dẫn chạy kiểm thử Unit, Build và Playwright E2E
└── contracts/
    └── frontend-parity-api.yaml # Ánh xạ hợp đồng OpenAPI được tiêu thụ trên FE
```

### Source Code Touched

```text
frontend/
├── src/
│   ├── api/
│   │   ├── types.ts                  # Bổ sung ApiKey, CreatedApiKey, UpdateProjectRequest, VideoPage, mở rộng Project & Video
│   │   ├── client.ts                 # Hỗ trợ PATCH & 204 No Content; thêm getMe, getProject, updateProject, deleteProject, listApiKeys, createApiKey, revokeApiKey, deleteVideo, listVideosPage
│   │   ├── client.test.ts            # Unit test cho toàn bộ các phương thức API client mới
│   │   └── contract.test.ts          # Kiểm thử hợp đồng cho API Keys, Project PATCH/DELETE, Video DELETE
│   ├── player/
│   │   ├── HlsPlayer.tsx             # Bổ sung bộ chọn độ phân giải (Auto / 720p / 360p)
│   │   └── HlsPlayer.test.tsx        # Unit test chuyển đổi chất lượng 360p/720p/Auto
│   ├── components/
│   │   ├── UploadDialog.tsx          # Thêm ô nhập tiêu đề video (title: 1..200 ký tự)
│   │   └── UploadDialog.test.tsx     # Unit test gửi title tùy chỉnh khi khởi tạo upload
│   ├── pages/
│   │   ├── Projects.tsx              # Cập nhật thẻ Project hiển thị đúng trường thực tế từ Backend
│   │   ├── Videos.tsx                # Tích hợp getProject, Edit/Delete Project, quản lý Developer API Keys (List/Create/Revoke) & phân trang
│   │   └── VideoDetail.tsx           # Hiển thị đủ 4 đường dẫn VideoAssets (Master, 720p, 360p, Thumbnail), metadata vòng đời & nút Delete Video
│   ├── mocks/
│   │   └── handlers.ts               # Bổ sung MSW handlers cho API Keys, Project PATCH/DELETE, Video DELETE, Auth Me
│   └── App.test.tsx                  # Bổ sung integration test cho các luồng UI mới
├── e2e/
│   └── real-stack.spec.ts            # Mở rộng kịch bản Playwright E2E kiểm chứng full luồng mới trên LocalStack
└── ../docs/
    └── LOCAL_INTEGRATION_AND_TEST_GUIDE.md # Cập nhật hướng dẫn tự trải nghiệm các tính năng mới trên UI
```

---

## Execution Plan

### Phase 0: Research & Decisions (Hoàn thành)
- Xác định cơ chế chuyển đổi độ phân giải (`Auto` / `720p` / `360p`) trên `HlsPlayer.tsx` tương thích cả `hls.js` và native HLS.
- Thiết kế UX/State cho quản lý Developer API Keys (`F-005`) đảm bảo `rawKey` chỉ hiển thị 1 lần duy nhất.
- Thiết kế luồng Edit/Delete Project (`F-004`) và Delete Video (`F-008`) kèm xử lý mã lỗi `409 Conflict`.
- *Output*: [`research.md`](research.md).

### Phase 1: Design & Contracts (Hoàn thành)
- Định nghĩa chi tiết các kiểu dữ liệu TypeScript, hàm chuẩn hóa (normalizers) và bảng ánh xạ 16 API endpoints trong [`data-model.md`](data-model.md).
- Trích xuất hợp đồng tích hợp trong [`contracts/frontend-parity-api.yaml`](contracts/frontend-parity-api.yaml).
- Thiết lập kịch bản xác minh trong [`quickstart.md`](quickstart.md).

### Phase 2: Implementation & Verification (Tiếp theo — qua `/speckit-tasks` hoặc triển khai trực tiếp)
1. **Bước 1 — Tầng API Client, Types & MSW Mocks:**
   - Mở rộng `types.ts`, `client.ts` (hỗ trợ `PATCH`, `204 No Content`, và 8 endpoints còn thiếu) cùng `mocks/handlers.ts`.
2. **Bước 2 — Multi-Rendition Player (`Auto / 720p / 360p`), Assets & Video Lifecycle (`F-006`, `F-008`):**
   - Nâng cấp `HlsPlayer.tsx` có thanh chọn chất lượng `Auto` / `720p` / `360p`.
   - Nâng cấp `VideoDetail.tsx` hiển thị đầy đủ 4 đường dẫn `assets` (`Master`, `720p`, `360p`, `Thumbnail`), metadata vòng đời (`title`, `attemptCount`, `startedAt`, `completedAt`) và nút **Delete video**.
   - Bổ sung ô nhập `Video title` trong `UploadDialog.tsx`.
3. **Bước 3 — Quản trị Project (`F-004`) & Developer API Keys (`F-005`):**
   - Nâng cấp `Videos.tsx` và `Projects.tsx`: thêm hộp thoại **Edit project** (`name`, `description`, `webhookUrl`), **Delete project** (xử lý `409 RESOURCE_IN_USE`), và hộp thoại/panel **API Keys** (liệt kê `x / 5 keys`, tạo key mới hiển thị `rawKey` 1 lần, thu hồi key).
4. **Bước 4 — Kiểm thử toàn diện & Cập nhật Tài liệu:**
   - Viết/cập nhật Unit & Contract tests (`npm run api:check && npm test && npm run build`).
   - Chạy Playwright Real-Stack E2E (`real-stack.spec.ts`) trên Docker Compose + LocalStack.
   - Cập nhật `docs/LOCAL_INTEGRATION_AND_TEST_GUIDE.md`.
