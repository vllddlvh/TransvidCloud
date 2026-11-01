# Research & Technical Decisions: F-011 — Đồng bộ Đầy đủ Tính năng Frontend theo Đặc tả Backend (F-003 -> F-008)

**Feature**: `011-frontend-feature-parity`  
**Date**: 2026-11-01T21:50:00+07:00

---

## 1. Cơ chế Chuyển đổi Chất lượng Phát (Auto / 720p / 360p) trên `HlsPlayer`

- **Decision**: Bổ sung bộ chọn độ phân giải (Quality Selector: `Auto`, `720p`, `360p`) trực tiếp trong component `HlsPlayer.tsx`. Khi người dùng chọn `Auto`, trình phát nạp `assets.hls` (`master.m3u8` — Adaptive Bitrate); khi người dùng chọn `720p` hoặc `360p`, trình phát nạp trực tiếp URL rendition tương ứng (`assets.hls720` hoặc `assets.hls360`) đồng thời bảo toàn vị trí thời gian phát (`video.currentTime`) và trạng thái đang phát (`!video.paused`).
- **Rationale**:
  - Hoạt động đồng nhất trên mọi trình duyệt (cả Chrome/Firefox dùng `hls.js` lẫn Safari dùng native HLS `<video src>`), không phụ thuộc vào việc `hls.js` đã parse xong `hls.levels` hay chưa.
  - Khai thác trực tiếp các trường `hls360Url` và `hls720Url` mà Backend (`F-008`) đã trả về trong `VideoAssets`.
  - Đơn giản, dễ kiểm thử trong cả Unit Test (JSDOM) lẫn Playwright E2E trên trình duyệt thật.
- **Alternatives considered**:
  - *Chỉ dùng `hls.currentLevel` của `hls.js`*: Không hoạt động trên trình duyệt dùng native HLS (`canPlayType('application/vnd.apple.mpegurl')`) và phụ thuộc vào thứ tự index của levels trong `master.m3u8`.

---

## 2. Giao diện Quản lý Developer API Keys (`F-005`)

- **Decision**: Tích hợp khu vực/hộp thoại **API Keys** ngay trên trang chi tiết Project (`/projects/:id/videos` — `Videos.tsx`), bao gồm:
  1. Danh sách các API Key đang hoạt động (`name`, `keyPrefix` dạng `tvc_live_xxxxxxxx...`, `createdAt`) kèm chỉ báo giới hạn `x / 5 keys`.
  2. Form tạo API Key mới (`POST /api/projects/{projectId}/api-keys`): khi tạo thành công (`201 Created`), hiển thị cảnh báo nổi bật *"Copy your API key now — it will only be shown once"* kèm chuỗi `rawKey` đầy đủ và nút **Copy API key**.
  3. Nút **Revoke** trên từng key (`DELETE /api/projects/{projectId}/api-keys/{keyHash}`): xóa khóa khỏi trạng thái ngay sau khi nhận `204 No Content`.
- **Rationale**:
  - Trong kiến trúc TransVidCloud (`F-005`), mỗi API Key gắn chặt với một `projectId` cụ thể. Đặt quản lý API Key ngay tại trang Project giúp người dùng thao tác tập trung mà không cần rời khỏi ngữ cảnh dự án.
  - Tuân thủ nghiêm ngặt quy tắc bảo mật `FR-004` của `F-005`: `rawKey` chỉ lưu trong state tạm thời của modal/panel vừa tạo và bị xóa ngay khi người dùng đóng hộp thoại.
- **Alternatives considered**:
  - *Tạo trang riêng `/projects/:id/api-keys`*: Làm tăng số lượng route phải điều hướng qua lại trong khi mỗi Project chỉ có tối đa 5 API Keys.

---

## 3. Quản trị Vòng đời Project: Edit, Webhook & Soft Delete (`F-004`)

- **Decision**:
  1. Thay thế cách lấy thông tin Project ở `Videos.tsx` từ `listProjects().find(...)` sang gọi trực tiếp `api.getProject(projectId)` (`GET /api/projects/{projectId}`).
  2. Bổ sung nút **Edit project** mở Modal cho phép cập nhật `name` (1–120 ký tự), `description` (tối đa 500 ký tự), và `webhookUrl` (URI hợp lệ hoặc để trống để gỡ bỏ — gửi `null` hoặc chuỗi mới qua `PATCH /api/projects/{projectId}`).
  3. Bổ sung nút **Delete project** mở hộp thoại xác nhận xóa mềm (`DELETE /api/projects/{projectId}`). Nếu Backend trả về `409 Conflict` (`RESOURCE_IN_USE` do còn video đang `UPLOADING`/`PENDING`/`PROCESSING`), hiển thị thông báo lỗi trực quan ngay trong hộp thoại; nếu thành công (`204 No Content`), chuyển hướng người dùng về `/projects`.
  4. Trên thẻ Project tại `Projects.tsx`, bỏ hiển thị `{project.videoCount} videos` (vì `ProjectResponse` của Backend không có trường `videoCount`), thay bằng trạng thái `ACTIVE` / chỉ báo Webhook đã cấu hình và thời gian tạo `createdAt`.
- **Rationale**:
  - Đồng bộ 100% với đặc tả `F-004` (`specs/004-project-management/spec.md`) và hợp đồng `contracts/openapi.yaml`.
- **Alternatives considered**:
  - *Tính `videoCount` bằng cách gọi `GET /api/projects/{id}/videos` cho từng project trên trang danh sách*: Gây lỗi N+1 requests không cần thiết.

---

## 4. Xóa Video, Nhập `title` khi Upload & Phân trang Cursor (`F-006`, `F-008`)

- **Decision**:
  1. **Ô nhập `title` trong `UploadDialog.tsx` (`F-006`):** Khi người dùng chọn file, tự động điền giá trị mặc định cho `title` bằng tên file đã cắt bỏ phần mở rộng (ví dụ `lecture-01.mp4` $\rightarrow$ `lecture-01`), cho phép người dùng chỉnh sửa (1–200 ký tự) trước khi bấm *Start upload*.
  2. **Hiển thị `title` và `filename` (`F-006`, `F-008`):** `VideoResponse` từ Backend lưu tiêu đề vào trường `title` và đường dẫn gốc vào `inputKey`. Frontend chuẩn hóa để hiển thị rõ `title` (và `filename`), cùng đầy đủ các mốc thời gian `createdAt`, `startedAt`, `completedAt`, `updatedAt` và số lần thử `attemptCount`.
  3. **Xóa Video (`DELETE /api/videos/{videoId}` — `F-008`):** Bổ sung nút **Delete video** tại trang `VideoDetail.tsx` (kèm xác nhận xóa). Khi `video.status === 'PROCESSING'`, nút xóa bị vô hiệu hóa (`disabled`) kèm giải thích theo `FR-012` của `F-008`. Sau khi xóa thành công (`204 No Content`), điều hướng người dùng về `/projects/{projectId}/videos`.
  4. **Hỗ trợ `204 No Content` trong `client.ts`:** Mở rộng hàm `request()` trong `frontend/src/api/client.ts` để hỗ trợ phương thức `'PATCH'` và xử lý phản hồi rỗng (`204 No Content`) cho các thao tác `DELETE`.
- **Rationale**:
  - Đáp ứng đầy đủ các Acceptance Criteria của `F-006` và `F-008` mà vẫn giữ tương thích ngược với toàn bộ 136 unit tests hiện có của Frontend.
- **Alternatives considered**:
  - *Xóa video không cần xác nhận*: Dễ gây thao tác nhầm của người dùng vì `DELETE /api/videos/{videoId}` là Fast Delete xóa ngay bản ghi khỏi DynamoDB.
