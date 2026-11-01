# Feature Specification: F-008 — Truy vấn Danh sách (Cursor Pagination), Chi tiết, Assets & Xóa Video (Epic A2/A3)

**Feature Branch**: `008-video-query-lifecycle`  
**Created**: 2026-11-01T16:48:00+07:00  
**Status**: Draft  
**Input**: User description: "F-008: Truy vấn Danh sách (Cursor Pagination), Chi tiết, Assets & Xóa Video (Epic A2/A3)"

---

## Clarifications

### Session 2026-11-01
- Q: Khi người dùng thực hiện xóa video (`DELETE /api/videos/{videoId}`), hệ thống nên xử lý các tệp tin lưu trữ trên S3 (file gốc trong input bucket và các phân đoạn HLS/thumbnail trong output bucket) theo cơ chế nào? → A: Xóa bản ghi DynamoDB ngay lập tức trong request (Fast Delete) để API phản hồi 204 tức thì; tệp tin trên S3 được dọn dẹp bất đồng bộ hoặc theo chính sách vòng đời S3 Lifecycle.
- Q: Chuỗi con trỏ phân trang (`cursor`) trả về cho client nên mang cấu trúc dữ liệu nào khi mã hóa Base64URL? → A: Mã hóa JSON chứa bộ ba khóa `{ projectId, videoId, createdAt }` dưới dạng Base64URL; khi nhận request, Backend đối chiếu `projectId` trong cursor với path parameter, từ chối HTTP 400 `INVALID_CURSOR` nếu phát hiện tráo đổi cursor giữa các dự án.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Truy vấn Danh sách Video theo Dự án kèm Phân trang Con trỏ (Cursor Pagination) (Priority: P1)

Là một người dùng SaaS Web hoặc Nhà phát triển tích hợp qua API Key, tôi muốn xem danh sách các video trong Dự án của mình với khả năng phân trang con trỏ (Cursor Pagination) mượt mà và sắp xếp theo thời gian tạo mới nhất lên đầu, để có thể duyệt qua kho video lớn một cách hiệu quả và ổn định mà không bị trùng lặp hay sót dữ liệu.

**Why this priority**: Đây là luồng nghiệp vụ cốt lõi giúp người dùng quan sát và quản lý các video đã tải lên trong dự án. Việc hỗ trợ phân trang con trỏ giúp hệ thống mở rộng quy mô dữ liệu lớn mà không bị suy giảm hiệu năng.

**Independent Test**: Có thể kiểm chứng độc lập bằng cách tạo nhiều video trong một dự án, sau đó gọi `GET /api/projects/{projectId}/videos` với `limit` khác nhau và truyền `cursor` nhận được để duyệt qua các trang kế tiếp; xác nhận danh sách trả về đúng thứ tự thời gian mới nhất lên đầu và `hasMore = false` ở trang cuối cùng.

**Acceptance Scenarios**:

1. **Given** một Dự án có 25 video đã được tạo, **When** người dùng gọi `GET /api/projects/{projectId}/videos?limit=10`, **Then** hệ thống trả về HTTP 200 OK kèm danh sách 10 video mới nhất, `hasMore = true`, và chuỗi `nextCursor` không rỗng.
2. **Given** chuỗi `nextCursor` từ trang trước, **When** người dùng gọi `GET /api/projects/{projectId}/videos?limit=10&cursor={nextCursor}`, **Then** hệ thống trả về 10 video tiếp theo mà không bị trùng lặp với trang trước.
3. **Given** trang cuối cùng của danh sách (5 video còn lại), **When** người dùng truyền `cursor` của trang cuối, **Then** hệ thống trả về 5 video còn lại kèm `nextCursor = null` và `hasMore = false`.
4. **Given** client gửi chuỗi `cursor` sai định dạng, bị hỏng hoặc làm giả mạo, **When** gửi request, **Then** hệ thống từ chối với HTTP 400 Bad Request (`INVALID_CURSOR`).
5. **Given** tham số `limit` nhỏ hơn 1 hoặc lớn hơn 100, **When** gửi request, **Then** hệ thống từ chối với HTTP 400 Bad Request (`VALIDATION_ERROR`).

---

### User Story 2 - Tra cứu Chi tiết Video, Trạng thái & Danh mục Assets HLS/Thumbnail (Priority: P2)

Là một người dùng Web hoặc Nhà phát triển, tôi muốn tra cứu thông tin chi tiết của một video cụ thể bằng `videoId`, bao gồm trạng thái xử lý hiện tại (`status`), số lần thử (`attemptCount`), mã lỗi (`errorCode` nếu thất bại) và danh mục các đường dẫn phát HLS đa độ phân giải cùng thumbnail khi video hoàn tất chuyển mã, để nắm bắt tiến độ và nhúng video vào trình phát phát lại.

**Why this priority**: Cung cấp thông tin chi tiết và đường dẫn tài nguyên (`assets`) là mục đích cuối cùng của việc chuyển mã video để người dùng có thể phát lại video trên giao diện web hoặc ứng dụng di động.

**Independent Test**: Có thể kiểm chứng bằng cách tạo một video ở trạng thái `COMPLETED` (có lưu bản ghi assets) và một video ở trạng thái `PROCESSING`/`FAILED_*`, gọi `GET /api/videos/{videoId}` và xác minh cấu trúc phản hồi khớp hoàn toàn với OpenAPI schema.

**Acceptance Scenarios**:

1. **Given** một video đã chuyển mã thành công (`status = COMPLETED`), **When** người dùng gọi `GET /api/videos/{videoId}`, **Then** hệ thống trả về HTTP 200 OK với đầy đủ thông tin video và danh mục `assets` đã ghép tiền tố `TVC_MEDIA_BASE_URL` (`masterPlaylistUrl`, `hls360Url`, `hls720Url`, `thumbnailUrl`).
2. **Given** một video đang trong quá trình xử lý hoặc chưa hoàn tất (`status = UPLOADING`, `PENDING` hoặc `PROCESSING`), **When** người dùng gọi `GET /api/videos/{videoId}`, **Then** hệ thống trả về HTTP 200 OK với thông tin trạng thái hiện tại và trường `assets = null` (không có link phát).
3. **Given** một video bị lỗi xử lý (`status` thuộc nhóm `FAILED_*`), **When** người dùng tra cứu chi tiết, **Then** hệ thống trả về HTTP 200 OK kèm mã lỗi chi tiết `errorCode` tương ứng (ví dụ `FFMPEG_TIMEOUT`, `CODEC_UNSUPPORTED`).
4. **Given** một `videoId` không tồn tại trong hệ thống hoặc có tiền tố khóa nội bộ (như `OUTBOX#...`), **When** người dùng tra cứu, **Then** hệ thống từ chối với HTTP 404 Not Found (`RESOURCE_NOT_FOUND`).

---

### User Story 3 - Xóa Video & Bảo Vệ Tiến Trình Xử Lý Dở Dang (Priority: P3)

Là một chủ sở hữu Dự án hoặc Nhà phát triển, tôi muốn xóa các video không còn nhu cầu sử dụng hoặc video bị lỗi khỏi hệ thống, đồng thời hệ thống phải bảo vệ không cho phép xóa video đang trong tiến trình xử lý chuyển mã (`PROCESSING`) để tránh mồ côi tài nguyên hoặc xung đột với worker.

**Why this priority**: Cho phép người dùng làm sạch dữ liệu và tối ưu hóa lưu trữ, đồng thời bảo vệ tính toàn vẹn trạng thái của hệ thống xử lý video phân tán.

**Independent Test**: Kiểm chứng bằng cách gọi `DELETE /api/videos/{videoId}` đối với video ở trạng thái `PROCESSING` (phải nhận 409 Conflict), sau đó đổi video sang `COMPLETED` hoặc `FAILED_*` và gọi lại `DELETE` (phải nhận 204 No Content), rồi xác minh `GET` lại video đó trả về 404 Not Found.

**Acceptance Scenarios**:

1. **Given** một video đang trong tiến trình chuyển mã dở dang (`status = PROCESSING`), **When** người dùng gọi `DELETE /api/videos/{videoId}`, **Then** hệ thống từ chối với HTTP 409 Conflict (`INVALID_STATE_TRANSITION` / `RESOURCE_IN_USE`) và giữ nguyên video.
2. **Given** một video ở trạng thái an toàn để xóa (`UPLOADING`, `PENDING`, `COMPLETED`, `FAILED_VALIDATION`, `FAILED_TRANSCODE`, `FAILED_STORAGE`), **When** người dùng gọi `DELETE /api/videos/{videoId}`, **Then** hệ thống thực hiện xóa thành công và trả về HTTP 204 No Content.
3. **Given** một video vừa bị xóa, **When** người dùng gọi `GET /api/videos/{videoId}` hoặc xem danh sách video của dự án, **Then** hệ thống trả về HTTP 404 Not Found và video không còn xuất hiện trong danh sách.
4. **Given** một `videoId` không tồn tại, **When** người dùng gọi `DELETE /api/videos/{videoId}`, **Then** hệ thống từ chối với HTTP 404 Not Found (`RESOURCE_NOT_FOUND`).

---

### User Story 4 - Phân Quyền Đa Người Thuê Dual Security (Priority: P4)

Là người quản trị hệ thống, tôi muốn đảm bảo tất cả các thao tác liệt kê, xem chi tiết và xóa video đều được kiểm soát phân quyền nghiêm ngặt theo cơ chế Dual Security (Bearer JWT cho SaaS User và `X-API-Key` cho Project Developer), đảm bảo tuyệt đối không có sự truy cập trái phép chéo giữa các Dự án hoặc Người dùng khác nhau.

**Why this priority**: Bảo vệ quyền riêng tư dữ liệu và an ninh hệ thống trong môi trường đa người thuê (Multi-tenant isolation).

**Independent Test**: Kiểm chứng bằng cách dùng tài khoản Người dùng B hoặc API Key của Dự án B gọi tới các endpoint của Dự án A, xác nhận toàn bộ đều bị từ chối với HTTP 403 Forbidden (`ACCESS_DENIED`).

**Acceptance Scenarios**:

1. **Given** người dùng gửi request không có Bearer token hoặc `X-API-Key`, **When** gọi bất kỳ endpoint nào (`GET .../videos`, `GET .../videos/{videoId}`, `DELETE .../videos/{videoId}`), **Then** hệ thống từ chối với HTTP 401 Unauthorized (`UNAUTHENTICATED`).
2. **Given** người dùng đăng nhập bằng JWT của User B, **When** cố tình xem danh sách video của Dự án A thuộc quyền sở hữu của User A, **Then** hệ thống từ chối với HTTP 403 Forbidden (`ACCESS_DENIED`).
3. **Given** một `X-API-Key` được cấp cho Dự án B, **When** cố tình gửi kèm để xem chi tiết hoặc xóa video thuộc Dự án A, **Then** hệ thống từ chối với HTTP 403 Forbidden (`ACCESS_DENIED`).
4. **Given** yêu cầu truy cập vào Dự án không tồn tại hoặc đã bị xóa mềm, **When** gọi danh sách video, **Then** hệ thống từ chối với HTTP 404 Not Found (`RESOURCE_NOT_FOUND`).

---

### Edge Cases

- **Project rỗng**: Khi dự án chưa có video nào, `GET /api/projects/{projectId}/videos` trả về `items: []`, `nextCursor: null`, `hasMore: false` với mã 200 OK.
- **Cursor rỗng hoặc khoảng trắng**: Được coi là trang đầu tiên (tương đương không truyền `cursor`).
- **Cursor chứa mã hóa lạ hoặc can thiệp tham số**: Nếu chuỗi cursor không thể giải mã Base64URL hoặc JSON bên trong thiếu khóa phân trang cần thiết, lập tức trả về HTTP 400 `INVALID_CURSOR`.
- **Dữ liệu phân trang đúng bằng ngưỡng `limit`**: Nếu tổng số video bằng đúng `limit` và không còn bản ghi tiếp theo, `hasMore` phải là `false` và `nextCursor` phải là `null`.
- **Đường dẫn `assets` khi chưa cấu hình CDN**: Ghép mặc định tiền tố `/media` hoặc giá trị cấu hình `TVC_MEDIA_BASE_URL` chuẩn hóa (xử lý bỏ dấu gạch chéo thừa ở đuôi).
- **Video có số lần retry `attemptCount` lớn hơn 0**: Phải hiển thị chính xác số lần đã retry trong response để người dùng nắm được độ ổn định của quá trình chuyển mã.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống PHẢI cung cấp endpoint `GET /api/projects/{projectId}/videos` để liệt kê danh sách video thuộc dự án, sắp xếp theo thời gian tạo mới nhất lên đầu (descending theo `createdAt`).
- **FR-002**: Hệ thống PHẢI hỗ trợ phân trang con trỏ (Cursor Pagination) qua tham số query `limit` (mặc định 20, phạm vi từ 1 đến 100) và `cursor` (chuỗi mã hóa Base64URL từ JSON chứa bộ ba khóa `{ projectId, videoId, createdAt }`).
- **FR-003**: Hệ thống PHẢI trả về cấu trúc phân trang chuẩn `VideoPageResponse` gồm `items` (mảng các `VideoResponse`), `nextCursor` (chuỗi con trỏ cho trang kế tiếp, mang giá trị `null` nếu đã đến cuối danh sách) và `hasMore` (boolean).
- **FR-004**: Hệ thống PHẢI từ chối với HTTP 400 Bad Request (`INVALID_CURSOR`) khi client gửi chuỗi con trỏ `cursor` sai định dạng, hỏng, không thể giải mã, hoặc có `projectId` trong cursor không khớp với `projectId` trên đường dẫn URL.
- **FR-005**: Hệ thống PHẢI lọc bỏ hoàn toàn các bản ghi nội bộ (như bản ghi Transactional Outbox có tiền tố `OUTBOX#`) khỏi kết quả trả về của danh sách video.
- **FR-006**: Hệ thống PHẢI cung cấp endpoint `GET /api/videos/{videoId}` để lấy thông tin chi tiết của một video cụ thể.
- **FR-007**: Hệ thống PHẢI trả về đầy đủ các trường thông tin vòng đời video gồm: `videoId`, `projectId`, `title`, `status`, `sizeBytes`, `contentType`, `attemptCount`, `errorCode`, `createdAt`, `updatedAt`, `startedAt`, `completedAt`.
- **FR-008**: Hệ thống PHẢI tự động ghép tiền tố cấu hình `TVC_MEDIA_BASE_URL` (ví dụ `/media`) vào các đường dẫn tài nguyên `assets` (`masterPlaylistUrl`, `hls360Url`, `hls720Url`, `thumbnailUrl`) khi video ở trạng thái `COMPLETED`.
- **FR-009**: Hệ thống PHẢI để trường `assets` mang giá trị `null` khi video ở các trạng thái chưa hoàn tất chuyển mã (`UPLOADING`, `PENDING`, `PROCESSING`, `FAILED_*`).
- **FR-010**: Hệ thống PHẢI từ chối với HTTP 404 Not Found (`RESOURCE_NOT_FOUND`) khi client tra cứu hoặc thao tác trên `videoId` không tồn tại, hoặc khi client cố tình truyền khóa nội bộ có tiền tố `OUTBOX#`.
- **FR-011**: Hệ thống PHẢI cung cấp endpoint `DELETE /api/videos/{videoId}` để xóa video và trả về HTTP 204 No Content khi thực hiện thành công; hệ thống áp dụng cơ chế Fast Delete: xóa ngay bản ghi VideoItem trên DynamoDB để video lập tức biến mất khỏi mọi API tra cứu/danh sách với độ trễ < 100ms, còn việc dọn dẹp các tệp tin trên S3 được thực hiện bất đồng bộ hoặc theo chính sách vòng đời S3 Lifecycle.
- **FR-012**: Hệ thống PHẢI từ chối xóa video với HTTP 409 Conflict (`INVALID_STATE_TRANSITION` / `RESOURCE_IN_USE`) nếu video đang trong trạng thái xử lý chuyển mã `PROCESSING`.
- **FR-013**: Hệ thống PHẢI hỗ trợ cơ chế xác thực kép (Dual Security) gồm Bearer JWT (kiểm tra quyền sở hữu `ownerId` của dự án) và `X-API-Key` (kiểm tra phạm vi `projectId` tương ứng) trên cả 3 thao tác List, Detail và Delete.
- **FR-014**: Hệ thống PHẢI từ chối với HTTP 401 Unauthorized (`UNAUTHENTICATED`) khi yêu cầu thiếu thông tin xác thực hoặc token/key không hợp lệ.
- **FR-015**: Hệ thống PHẢI từ chối với HTTP 403 Forbidden (`ACCESS_DENIED`) khi người dùng hoặc API Key cố tình truy cập hoặc xóa video thuộc dự án của người dùng khác.

---

### Key Entities *(include if feature involves data)*

- **Video**: Đại diện cho thực thể video lưu trữ trong bảng `videos` của dự án. Gồm các thuộc tính định danh (`videoId`), dự án trực thuộc (`projectId`), tiêu đề (`title`), kích thước tệp (`sizeBytes`), loại nội dung (`contentType`), trạng thái vòng đời (`status`), số lần thử chuyển mã (`attemptCount`), mã lỗi nếu thất bại (`errorCode`), bản đồ các đường dẫn xuất bản (`assets`) và các dấu mốc thời gian ISO-8601 UTC (`createdAt`, `updatedAt`, `startedAt`, `completedAt`).
- **Cursor**: Đối tượng con trỏ phân trang trung gian, lưu trữ vị trí phân trang dựa trên khóa GSI `project-index` (bao gồm `projectId`, `videoId`, `createdAt`), được mã hóa Base64URL an toàn gửi cho client và giải mã khi nhận request trang tiếp theo.
- **VideoAssets**: Đại diện cho cấu trúc các tài nguyên phương tiện đã được chuyển mã xong và sẵn sàng phát lại, bao gồm Master HLS Playlist (`masterPlaylistUrl`), các luồng HLS phân giải con (`hls360Url`, `hls720Url`) và ảnh đại diện thu nhỏ (`thumbnailUrl`).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Phân trang con trỏ hoạt động ổn định và chính xác 100%, phản hồi danh sách video trong thời gian dưới 100ms trên các trang dữ liệu lớn mà không bị suy giảm hiệu năng hay phụ thuộc vào số lượng video trước đó.
- **SC-002**: 100% video ở trạng thái `COMPLETED` trả về danh mục `assets` với đường dẫn HLS và thumbnail đầy đủ, sẵn sàng cho client web/mobile phát lại video mà không cần xử lý thêm chuỗi URL.
- **SC-003**: 100% các yêu cầu xóa video đang ở trạng thái `PROCESSING` bị chặn đứng với HTTP 409 Conflict, bảo vệ an toàn cho worker chuyển mã ngầm và chống rò rỉ/mồ côi tài nguyên.
- **SC-004**: Đạt độ cô lập đa người thuê tuyệt đối (100%): Mọi hành vi gọi chéo dự án từ JWT hoặc API Key khác đều bị chặn đứng với HTTP 403 Forbidden và được ghi nhật ký cảnh báo an ninh.

---

## Assumptions

- **Mô hình phân trang con trỏ (Cursor-based Pagination)**: Do bảng DynamoDB `videos` sử dụng GSI `project-index` với Partition Key là `projectId` và Sort Key là `createdAt`, cơ chế phân trang con trỏ dựa trên việc mã hóa đối tượng `LastEvaluatedKey` (gồm `projectId`, `videoId`, `createdAt`) thành chuỗi Base64URL là giải pháp chuẩn, không làm lộ chi tiết lưu trữ nội bộ cho client.
- **Thứ tự sắp xếp**: Danh sách video luôn được sắp xếp theo thời gian tạo giảm dần (video mới nhất đứng đầu trang 1, tương đương `ScanIndexForward = false`).
- **Xóa bản ghi**: Khi xóa video (`DELETE /api/videos/{videoId}`), bản ghi video trong bảng DynamoDB sẽ được loại bỏ. Các tệp tin tạm hoặc file trên S3 liên quan có thể được thu hồi hoặc dọn dẹp theo chính sách vòng đời bucket S3.
- **Định cấu hình Base URL tài nguyên**: Tiền tố đường dẫn phát lại HLS và thumbnail được cấu hình linh hoạt thông qua biến môi trường `TVC_MEDIA_BASE_URL` (giá trị mặc định `/media`), cho phép dễ dàng chuyển đổi sang CDN CloudFront hoặc Domain phát trực tiếp trong tương lai.
