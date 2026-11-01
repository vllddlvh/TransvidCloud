# Feature Specification: F-011 — Đồng bộ Đầy đủ Tính năng Frontend theo Đặc tả Backend (F-003 -> F-008)

**Feature Branch**: `phuong-integration`  
**Created**: 2026-11-01T21:48:00+07:00  
**Status**: Approved  
**Input**: Rà soát và bổ sung toàn bộ các tính năng còn thiếu trên Frontend (`frontend/src/`) so với đặc tả Backend (`backend/specs/003..008` và `contracts/openapi.yaml`).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Chuyển đổi Độ phân giải Phát (Auto / 720p / 360p) & Quản lý Danh mục Video Assets (Priority: P1)

Là một người dùng SaaS Web xem chi tiết video đã chuyển mã thành công (`status = COMPLETED`), tôi muốn có thể chủ động chuyển đổi chất lượng phát giữa **Auto (ABR Master)**, **720p HD** và **360p SD** ngay trên trình phát, đồng thời xem và sao chép từng đường dẫn tài nguyên đầu ra (`masterPlaylistUrl`, `hls720Url`, `hls360Url`, `thumbnailUrl`) cùng toàn bộ siêu dữ liệu vòng đời xử lý (`title`, `attemptCount`, `startedAt`, `completedAt`, `updatedAt`).

**Why this priority**: Giúp người dùng kiểm chứng trực quan kết quả chuyển mã đa độ phân giải (360p/720p) của Worker và lấy link phát/thumbnail phục vụ tích hợp.

**Independent Test**: Mở trang `/videos/{videoId}` của một video ở trạng thái `COMPLETED`, kiểm tra bộ chọn chất lượng (`Auto`, `720p`, `360p`) chuyển đổi luồng HLS mượt mà và bảng thông tin hiển thị đủ 4 đường dẫn assets kèm nút Copy.

**Acceptance Scenarios**:

1. **Given** một video ở trạng thái `COMPLETED` có đầy đủ `assets` (`hls`, `hls360`, `hls720`, `thumbnail`), **When** người dùng mở trang chi tiết video `/videos/{videoId}`, **Then** trình phát HLS hiển thị bộ chọn độ phân giải (`Auto`, `720p`, `360p`) và mặc định phát ở chế độ `Auto`.
2. **Given** người dùng đang xem video `COMPLETED`, **When** người dùng bấm chọn `720p` hoặc `360p`, **Then** trình phát chuyển sang phát luồng rendition tương ứng (`360p/index.m3u8` hoặc `720p/index.m3u8` hoặc cố định level trong `hls.js`) và hiển thị rõ trạng thái chất lượng đang chọn.
3. **Given** video ở trạng thái `COMPLETED`, **When** người dùng xem phần thông tin tài nguyên (Output Assets), **Then** giao diện hiển thị đầy đủ các đường dẫn `Master HLS`, `720p HLS`, `360p HLS`, `Thumbnail` kèm nút sao chép nhanh từng đường dẫn.
4. **Given** thông tin chi tiết video từ `GET /api/videos/{videoId}`, **When** hiển thị trên trang chi tiết, **Then** giao diện hiển thị đầy đủ `title`, `filename`, `attemptCount`, `createdAt`, `startedAt`, `completedAt`, `updatedAt`.

---

### User Story 2 - Quản lý Developer API Keys theo Project (Tạo, Liệt kê, Thu hồi) (Priority: P1)

Là chủ sở hữu Project trên SaaS Web, tôi muốn xem danh sách các API Key của dự án (`GET /api/projects/{projectId}/api-keys`), tạo mới API Key (`POST /api/projects/{projectId}/api-keys` — tối đa 5 keys/project) với cảnh báo khóa bí mật đầy đủ (`rawKey`) chỉ hiển thị một lần duy nhất, và thu hồi khóa (`DELETE /api/projects/{projectId}/api-keys/{keyHash}`) khi không còn sử dụng.

**Why this priority**: Thực thi trọn vẹn đặc tả `F-005` (`specs/005-apikey-dual-auth/spec.md`) trên giao diện người dùng để nhà phát triển có thể lấy `X-API-Key` tích hợp hệ thống ngoài.

**Independent Test**: Mở mục quản lý API Keys trong trang Project, tạo 1 API Key mới -> xác minh nhận được `rawKey` (`tvc_live_...`) kèm nút Copy; đóng thông báo -> xác minh danh sách chỉ hiển thị `keyPrefix` (`tvc_live_xxxxxxxx...`); bấm Revoke -> xác minh khóa bị xóa khỏi danh sách.

**Acceptance Scenarios**:

1. **Given** người dùng đang ở trang chi tiết Project `/projects/{projectId}/videos`, **When** người dùng mở bảng/hộp thoại quản lý API Keys, **Then** hệ thống hiển thị danh sách các API Key hiện có (`name`, `keyPrefix`, `createdAt`), số lượng khóa hiện tại trên giới hạn `x / 5 keys`.
2. **Given** Project có dưới 5 API Keys, **When** người dùng nhập tên gợi nhớ (`name`, 1–100 ký tự) và bấm tạo khóa, **Then** hệ thống gọi `POST /api/projects/{projectId}/api-keys`, hiển thị hộp thoại cảnh báo bảo mật kèm toàn bộ chuỗi `rawKey` và nút sao chép.
3. **Given** Project đã đạt giới hạn 5 API Keys đang hoạt động, **When** người dùng xem giao diện tạo key hoặc gửi yêu cầu tạo thêm, **Then** nút tạo mới bị khóa hoặc hiển thị rõ thông báo lỗi vượt giới hạn 5 API Keys.
4. **Given** một API Key đang tồn tại trong danh sách, **When** người dùng bấm `Revoke` và xác nhận thu hồi, **Then** hệ thống gọi `DELETE /api/projects/{projectId}/api-keys/{keyHash}` và xóa khóa khỏi danh sách ngay lập tức.

---

### User Story 3 - Quản trị Vòng đời Project: Xem chi tiết, Chỉnh sửa, Cấu hình Webhook & Xóa Mềm (Priority: P1)

Là chủ sở hữu Project, tôi muốn chỉnh sửa tên dự án, mô tả, cấu hình địa chỉ `webhookUrl` (`PATCH /api/projects/{projectId}`), hoặc xóa mềm dự án (`DELETE /api/projects/{projectId}`) khi không còn video nào đang xử lý dở dang.

**Why this priority**: Hoàn thiện đầy đủ các thao tác CRUD Project theo đặc tả `F-004` (`specs/004-project-management/spec.md`).

**Independent Test**: Tại trang Project, mở hộp thoại Edit Project -> sửa `name`, `description`, `webhookUrl` -> lưu thành công và giao diện cập nhật ngay. Sau đó bấm Delete Project trên dự án không có video đang xử lý -> dự án biến mất khỏi danh sách `/projects`.

**Acceptance Scenarios**:

1. **Given** người dùng đang ở trang Project `/projects/{projectId}/videos`, **When** trang tải dữ liệu, **Then** Frontend gọi trực tiếp `GET /api/projects/{projectId}` để lấy đầy đủ `name`, `description`, `webhookUrl`, `status`, `createdAt`, `updatedAt`.
2. **Given** người dùng bấm `Edit project`, **When** người dùng cập nhật `name` (1–120 ký tự), `description` (tối đa 500 ký tự) hoặc `webhookUrl` (URL hợp lệ hoặc để trống để gỡ bỏ) và lưu lại, **Then** Frontend gọi `PATCH /api/projects/{projectId}` và cập nhật thông tin hiển thị.
3. **Given** một Project không có video nào đang ở trạng thái `UPLOADING`, `PENDING`, `PROCESSING`, **When** người dùng bấm `Delete project` và xác nhận, **Then** Frontend gọi `DELETE /api/projects/{projectId}`, nhận `204 No Content` và điều hướng về `/projects`.
4. **Given** một Project đang có video ở trạng thái `UPLOADING`, `PENDING` hoặc `PROCESSING`, **When** người dùng cố gắng xóa Project, **Then** giao diện hiển thị rõ thông báo lỗi từ Backend (`409 RESOURCE_IN_USE`) giải thích không thể xóa khi còn video đang xử lý.

---

### User Story 4 - Xóa Video, Nhập Tiêu đề Video (`title`) khi Upload & Phân trang Cursor (Priority: P1)

Là người dùng quản lý kho video của Project, tôi muốn đặt tiêu đề hiển thị (`title`, 1–200 ký tự) khi tải video lên (`F-006`), duyệt danh sách video có hỗ trợ tải thêm theo phân trang con trỏ (`nextCursor` / `hasMore` của `F-008`), và xóa các video không còn nhu cầu sử dụng (`DELETE /api/videos/{videoId}` của `F-008`, chặn xóa khi đang `PROCESSING`).

**Why this priority**: Hoàn thiện trọn vẹn luồng nghiệp vụ Video theo đặc tả `F-006` và `F-008`.

**Independent Test**: Mở hộp thoại Upload, nhập `title` tùy chỉnh và tải video lên -> kiểm tra tiêu đề hiển thị đúng; tại trang chi tiết video (khi trạng thái khác `PROCESSING`), bấm `Delete video` -> video bị xóa và điều hướng về danh sách video của Project.

**Acceptance Scenarios**:

1. **Given** người dùng chọn tệp video trong hộp thoại Upload, **When** tệp hợp lệ được chọn, **Then** ô nhập `Video title` tự động gợi ý tên tệp (bỏ phần mở rộng) và cho phép người dùng chỉnh sửa (1–200 ký tự) trước khi gửi `POST /api/projects/{projectId}/videos`.
2. **Given** một video ở trạng thái khác `PROCESSING` (`UPLOADING`, `PENDING`, `COMPLETED`, `FAILED_*`), **When** người dùng bấm `Delete video` và xác nhận, **Then** Frontend gọi `DELETE /api/videos/{videoId}` và xóa video thành công.
3. **Given** một video đang ở trạng thái `PROCESSING`, **When** người dùng xem trang chi tiết video, **Then** nút `Delete video` bị vô hiệu hóa (disabled) kèm chú thích không thể xóa khi đang chuyển mã (hoặc hiển thị thông báo lỗi `409 Conflict` nếu gọi xóa).
4. **Given** danh sách video trong Project có nhiều trang (`hasMore = true` và `nextCursor` khác null), **When** người dùng xem danh sách hoặc bấm `Load more`, **Then** Frontend sử dụng `cursor` và `limit` để nạp trang tiếp theo theo đúng chuẩn `F-008`.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Frontend API Client (`frontend/src/api/client.ts`) PHẢI hỗ trợ đầy đủ 16 endpoints trong `contracts/openapi.yaml`, bao gồm bổ sung: `GET /api/auth/me`, `GET /api/projects/{projectId}`, `PATCH /api/projects/{projectId}`, `DELETE /api/projects/{projectId}`, `GET /api/projects/{projectId}/api-keys`, `POST /api/projects/{projectId}/api-keys`, `DELETE /api/projects/{projectId}/api-keys/{keyHash}`, và `DELETE /api/videos/{videoId}`.
- **FR-002**: Trình phát `HlsPlayer` PHẢI cung cấp bộ chọn độ phân giải (`Auto`, `720p`, `360p`) khi video có các tài nguyên tương ứng.
- **FR-003**: Trang `VideoDetail` PHẢI hiển thị đầy đủ 4 đường dẫn `assets` (`Master HLS`, `720p HLS`, `360p HLS`, `Thumbnail`) kèm nút sao chép, hiển thị đầy đủ siêu dữ liệu vòng đời (`title`, `attemptCount`, `startedAt`, `completedAt`, `updatedAt`), và cung cấp chức năng xóa video (`DELETE /api/videos/{videoId}`).
- **FR-004**: Hộp thoại `UploadDialog` PHẢI cho phép người dùng nhập/chỉnh sửa trường `title` (1–200 ký tự) khi khởi tạo upload.
- **FR-005**: Trang chi tiết Project (`Videos.tsx`) PHẢI tích hợp giao diện quản lý:
  - Chỉnh sửa thông tin Project (`name`, `description`, `webhookUrl`) và xóa mềm Project (`DELETE /api/projects/{projectId}`).
  - Quản lý Developer API Keys: liệt kê, tạo mới (hiển thị `rawKey` một lần duy nhất kèm nút Copy), và thu hồi (`DELETE .../api-keys/{keyHash}`).
- **FR-006**: Thẻ Project trên trang `Projects.tsx` PHẢI hiển thị dữ liệu thực tế từ `ProjectResponse` của Backend (loại bỏ hiển thị trường `videoCount` luôn bằng 0 không có trong OpenAPI schema).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% các endpoints trong `contracts/openapi.yaml` (thuộc phạm vi MVP `F-003` $\rightarrow$ `F-008`) được kết nối và có thể thao tác trực tiếp trên giao diện Frontend.
- **SC-002**: 100% unit tests & contract tests trên Frontend (`npm run api:check && npm test && npm run build`) vượt qua không lỗi.
- **SC-003**: Kịch bản Playwright Real-Stack E2E (`frontend/e2e/real-stack.spec.ts`) kiểm chứng thành công các tính năng mới trên môi trường Docker Compose + LocalStack thực tế.
