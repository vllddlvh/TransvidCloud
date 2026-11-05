# Feature Specification: Quản lý Project & Phân quyền sở hữu tài nguyên

**Feature Branch**: `phuong-backend`

**Created**: 2026-10-31

**Status**: Draft

**Input**: User description: "feature F-004: Quản lý Project & Phân quyền sở hữu tài nguyên (Epic A2)"

## Clarifications

### Session 2026-10-31

- Q: Khi người dùng thực hiện xóa một Project hợp lệ (không còn video nào đang trong trạng thái xử lý dở dang), hệ thống nên xử lý các video đã hoàn tất (COMPLETED hoặc FAILED_*) như thế nào? → A: Áp dụng cơ chế **Soft Delete** cho Project. Khi thỏa mãn điều kiện không còn video xử lý dở dang, hệ thống không xóa vật lý bản ghi Project trong DynamoDB mà đánh dấu trạng thái `DELETED` kèm mốc thời gian `deletedAt`. Toàn bộ video records (`COMPLETED`, `FAILED_*`), metadata, lịch sử job và các object trên S3 đều được giữ nguyên vẹn (không cascade delete video, không xóa S3 sync). Project đã xóa sẽ bị ẩn khỏi danh sách `GET /api/projects` và bị phong tỏa mọi thao tác tạo video mới hoặc biến đổi dữ liệu. Chính sách xóa video, thùng rác (Trash) và dọn dẹp S3 sau 30 ngày sẽ được triển khai độc lập ở các feature sau (F-008).
- Q: Khi người dùng truy vấn danh sách Project qua GET /api/projects, thứ tự sắp xếp mặc định theo thời gian tạo nên là gì? → A: Sắp xếp theo thứ tự **mới nhất lên đầu** (giảm dần theo thời gian tạo `createdAt`, tương ứng với cấu hình `ScanIndexForward = false` khi truy vấn trên GSI `owner-index`).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Khởi tạo Project mới cho người dùng SaaS (Priority: P1)

Người dùng sau khi đăng nhập vào nền tảng muốn tạo một Project (Dự án) mới để bắt đầu tổ chức và quản lý các video chuyển mã của mình. Người dùng cung cấp tên dự án (bắt buộc) cùng mô tả ngắn và địa chỉ Webhook nhận thông báo (tùy chọn). Hệ thống kiểm tra tính hợp lệ của dữ liệu, tự động gắn quyền sở hữu cho người dùng hiện tại, khởi tạo dự án với trạng thái hoạt động `ACTIVE` và trả về thông tin chi tiết của dự án vừa tạo.

**Why this priority**: Project là không gian chứa cốt lõi cho mọi tài nguyên tiếp theo (Video, API Key, cấu hình Webhook). Nếu không thể tạo project, người dùng không thể thực hiện bất kỳ chức năng tải lên hay xử lý video nào trên nền tảng.

**Independent Test**: Có thể kiểm thử độc lập hoàn toàn bằng cách gửi yêu cầu tạo dự án mới với tên hợp lệ từ một phiên đăng nhập hợp lệ; xác minh hệ thống tạo thành công dự án mới, tự động gán đúng định danh người sở hữu và trả về mã định danh dự án duy nhất.

**Acceptance Scenarios**:

1. **Given** Người dùng đã đăng nhập với phiên làm việc hợp lệ, **When** Người dùng gửi yêu cầu tạo dự án với tên dự án hợp lệ (từ 1 đến 120 ký tự), mô tả (tối đa 500 ký tự) và địa chỉ Webhook hợp lệ, **Then** Hệ thống tạo dự án mới thành công với trạng thái `ACTIVE`, gán người dùng hiện tại làm chủ sở hữu (`ownerId`), lưu vết thời gian tạo/cập nhật và trả về thông tin đầy đủ của dự án với mã thành công 201.
2. **Given** Người dùng gửi yêu cầu tạo dự án chỉ có tên bắt buộc (không có mô tả và webhook), **When** Gửi yêu cầu, **Then** Hệ thống khởi tạo dự án thành công với các trường tùy chọn để trống.
3. **Given** Người dùng gửi yêu cầu với tên dự án bị để trống hoặc vượt quá 120 ký tự, hoặc địa chỉ Webhook không đúng định dạng URL, **When** Gửi yêu cầu, **Then** Hệ thống từ chối yêu cầu và phản hồi thông báo lỗi dữ liệu đầu vào không hợp lệ với mã lỗi 400.

---

### User Story 2 - Liệt kê và Xem chi tiết Project thuộc sở hữu (Priority: P1)

Người dùng muốn xem danh sách toàn bộ các dự án đang hoạt động mà mình đang sở hữu để lựa chọn dự án cần làm việc, đồng thời có thể bấm vào xem thông tin chi tiết của một dự án cụ thể (bao gồm tên, mô tả, cấu hình webhook, ngày tạo và ngày cập nhật). Các dự án đã bị xóa mềm không được xuất hiện trong danh sách này.

**Why this priority**: Cho phép người dùng điều hướng và quản lý các không gian làm việc của mình, là điều kiện tiên quyết để hiển thị giao diện bảng điều khiển (Dashboard) trên SaaS Web.

**Independent Test**: Có thể kiểm thử độc lập bằng cách tạo 2 dự án cho người dùng A, sau đó gọi chức năng liệt kê để kiểm tra đúng 2 dự án xuất hiện; gọi xem chi tiết từng dự án bằng mã dự án để xác minh thông tin trả về chính xác.

**Acceptance Scenarios**:

1. **Given** Người dùng A sở hữu nhiều dự án đang hoạt động (`ACTIVE`) trong hệ thống, **When** Người dùng gửi yêu cầu xem danh sách dự án của mình, **Then** Hệ thống trả về danh sách các dự án đang hoạt động thuộc sở hữu của người dùng A, sắp xếp theo thứ tự thời gian tạo giảm dần (dự án mới nhất hiển thị lên đầu).
2. **Given** Người dùng B mới tạo tài khoản và chưa có dự án nào (hoặc toàn bộ dự án đã bị xóa mềm), **When** Người dùng gửi yêu cầu xem danh sách dự án, **Then** Hệ thống trả về danh sách rỗng với mã 200 mà không gây lỗi.
3. **Given** Người dùng gửi yêu cầu xem chi tiết một dự án đang hoạt động bằng mã dự án hợp lệ mà mình sở hữu, **When** Hệ thống xử lý yêu cầu, **Then** Hệ thống trả về đầy đủ thông tin chi tiết của dự án đó với mã 200.
4. **Given** Người dùng yêu cầu xem chi tiết một mã dự án không tồn tại hoặc đã bị xóa mềm (`DELETED`), **When** Hệ thống xử lý yêu cầu, **Then** Hệ thống phản hồi lỗi không tìm thấy tài nguyên với mã 404 (`RESOURCE_NOT_FOUND`).

---

### User Story 3 - Cập nhật thông tin Project và Cấu hình Webhook (Priority: P2)

Người dùng muốn chỉnh sửa thông tin dự án đã tạo, chẳng hạn như đổi tên dự án, cập nhật mô tả, hoặc bổ sung/thay đổi/gỡ bỏ địa chỉ Webhook nhận thông báo khi luồng xử lý video hoàn tất.

**Why this priority**: Giúp người dùng linh hoạt điều chỉnh cấu hình và tích hợp hệ thống mà không cần phải xóa đi tạo lại dự án.

**Independent Test**: Có thể kiểm thử độc lập bằng cách tạo một dự án, gửi yêu cầu cập nhật tên và webhookUrl mới, sau đó kiểm tra lại chi tiết dự án để xác nhận các thông tin đã được thay đổi và thời gian `updatedAt` được làm mới.

**Acceptance Scenarios**:

1. **Given** Dự án đang tồn tại ở trạng thái `ACTIVE` và thuộc quyền sở hữu của người dùng, **When** Người dùng gửi yêu cầu cập nhật tên dự án mới và/hoặc mô tả mới, **Then** Hệ thống cập nhật các trường được chỉ định, cập nhật mốc thời gian sửa đổi gần nhất (`updatedAt`) và trả về thông tin dự án sau khi cập nhật với mã 200.
2. **Given** Người dùng muốn gỡ bỏ Webhook đã cấu hình trước đó, **When** Người dùng gửi yêu cầu cập nhật với trường `webhookUrl` mang giá trị rỗng/null, **Then** Hệ thống xóa bỏ liên kết Webhook khỏi dự án thành công.
3. **Given** Người dùng gửi dữ liệu cập nhật với tên rỗng hoặc vượt quá 120 ký tự, hoặc URL webhook sai định dạng, **When** Gửi yêu cầu, **Then** Hệ thống từ chối cập nhật và trả về mã lỗi 400.
4. **Given** Người dùng gửi yêu cầu cập nhật lên một dự án đã bị xóa mềm (`DELETED`), **When** Hệ thống xử lý yêu cầu, **Then** Hệ thống từ chối và phản hồi mã 404 (`RESOURCE_NOT_FOUND`).

---

### User Story 4 - Xóa an toàn Project bằng Soft Delete và Chặn xóa khi còn video đang xử lý (Priority: P2)

Người dùng muốn dọn dẹp và xóa bỏ một dự án không còn nhu cầu sử dụng. Để đảm bảo an toàn dữ liệu và tính toàn vẹn của hệ thống xử lý bất đồng bộ, hệ thống phải kiểm tra trạng thái các video bên trong dự án: nếu dự án vẫn còn video đang trong quá trình tải lên hoặc xử lý (`UPLOADING`, `PENDING`, `PROCESSING`), hệ thống phải từ chối xóa để tránh thất thoát dữ liệu và lỗi xử lý của Worker; khi dự án không còn video nào đang xử lý dở dang, hệ thống thực hiện Soft Delete (chuyển trạng thái `DELETED` và ghi nhận `deletedAt`). Toàn bộ bản ghi video hoàn tất/thất bại và file S3 được giữ nguyên vẹn để phục vụ audit/lịch sử.

**Why this priority**: Bảo vệ tính toàn vẹn của chu trình xử lý video, ngăn chặn lỗi mồ côi tài nguyên (orphan jobs), bảo toàn dữ liệu lịch sử và tránh các thao tác xóa vật lý nặng nề ảnh hưởng hiệu năng.

**Independent Test**:
- Kiểm thử trường hợp 1: Tạo dự án có video ở trạng thái `PROCESSING`, gửi yêu cầu xóa -> xác minh hệ thống từ chối với mã 409 (`RESOURCE_IN_USE`).
- Kiểm thử trường hợp 2: Tạo dự án trống hoặc dự án chỉ có video đã hoàn tất (`COMPLETED`/`FAILED_*`), gửi yêu cầu xóa -> xác minh hệ thống thực hiện soft delete thành công, phản hồi mã 204, bản ghi Project chuyển `DELETED`, và các video cũ vẫn tồn tại nguyên vẹn.

**Acceptance Scenarios**:

1. **Given** Một dự án thuộc quyền sở hữu của người dùng và không chứa bất kỳ video nào đang xử lý dở dang (không có video ở trạng thái `UPLOADING`, `PENDING`, `PROCESSING`), **When** Người dùng gửi yêu cầu xóa dự án, **Then** Hệ thống thực hiện Soft Delete (cập nhật trạng thái `DELETED`, ghi nhận `deletedAt`), giữ nguyên các bản ghi video liên quan và phản hồi mã 204 Không có nội dung (No Content).
2. **Given** Một dự án đang có ít nhất một video đang ở trạng thái xử lý dở dang (`UPLOADING`, `PENDING` hoặc `PROCESSING`), **When** Người dùng gửi yêu cầu xóa dự án, **Then** Hệ thống từ chối thao tác xóa, giữ nguyên dữ liệu dự án và phản hồi mã lỗi 409 Xung đột kèm mã lỗi nghiệp vụ `RESOURCE_IN_USE`.
3. **Given** Người dùng gửi yêu cầu xóa một mã dự án không tồn tại hoặc đã bị xóa trước đó, **When** Hệ thống xử lý yêu cầu, **Then** Phản hồi mã lỗi 404 Không tìm thấy tài nguyên (`RESOURCE_NOT_FOUND`).

---

### User Story 5 - Cô lập quyền sở hữu tài nguyên đa người dùng (Multi-tenant Isolation) (Priority: P1)

Nền tảng phục vụ nhiều khách hàng SaaS đồng thời. Người dùng tuyệt đối không được phép xem, chỉnh sửa hay xóa các dự án thuộc quyền sở hữu của người dùng khác, ngay cả khi biết chính xác mã định danh `projectId` của dự án đó.

**Why this priority**: Đảm bảo an toàn bảo mật, quyền riêng tư dữ liệu và tuân thủ nguyên tắc cách ly người thuê (tenant isolation) bắt buộc của nền tảng đám mây.

**Independent Test**: Tạo dự án cho Người dùng A. Đăng nhập bằng tài khoản Người dùng B và cố gắng thực hiện các thao tác: xem chi tiết, cập nhật thông tin, hoặc xóa dự án của Người dùng A -> xác minh 100% các thao tác đều bị chặn với mã từ chối truy cập 403 (`ACCESS_DENIED`).

**Acceptance Scenarios**:

1. **Given** Dự án thuộc quyền sở hữu của Người dùng A, **When** Người dùng B (đã đăng nhập hợp lệ) gửi yêu cầu xem chi tiết `GET /api/projects/{projectId}` của dự án đó, **Then** Hệ thống từ chối truy cập và phản hồi mã 403 Cấm truy cập (`ACCESS_DENIED`).
2. **Given** Dự án thuộc quyền sở hữu của Người dùng A, **When** Người dùng B gửi yêu cầu cập nhật `PATCH /api/projects/{projectId}` hoặc xóa `DELETE /api/projects/{projectId}`, **Then** Hệ thống từ chối thao tác và phản hồi mã 403 (`ACCESS_DENIED`), không có bất kỳ thay đổi nào xảy ra với dự án.
3. **Given** Người dùng B gửi yêu cầu liệt kê danh sách dự án của mình, **When** Hệ thống trả kết quả, **Then** Danh sách tuyệt đối không chứa bất kỳ dự án nào của Người dùng A.

---

### Edge Cases

- **Tên dự án chứa ký tự đặc biệt hoặc khoảng trắng thừa**: Người dùng nhập tên dự án có khoảng trắng ở đầu/cuối hoặc các ký tự Unicode/dấu tiếng Việt. Hệ thống phải cắt tỉa khoảng trắng thừa và lưu trữ nguyên vẹn ký tự Unicode hợp lệ.
- **Dự án không có video nhưng có cấu hình Webhook**: Hệ thống phải cho phép xóa mềm bình thường mà không phát sinh lỗi.
- **Cập nhật rỗng (Empty Patch Body)**: Khi người dùng gửi yêu cầu `PATCH` mà không truyền bất kỳ trường nào cần thay đổi, hệ thống xử lý an toàn, giữ nguyên thông tin hiện tại và trả về chi tiết dự án (không báo lỗi hệ thống).
- **Thao tác trên Project đã xóa mềm**: Mọi thao tác truy vấn chi tiết, cập nhật hoặc tạo tài nguyên con (Video, API Key) trên một Project đã bị đánh dấu `DELETED` đều bị từ chối với mã 404 (`RESOURCE_NOT_FOUND`).
- **Trùng lặp tên dự án trong cùng một tài khoản**: Một người dùng có thể tạo nhiều dự án có cùng tên hiển thị (vì `projectId` là khóa chính định danh duy nhất), tuy nhiên hệ thống khuyến khích tên dự án mang tính phân biệt.
- **Yêu cầu không có thông tin xác thực**: Mọi truy cập vào các điểm cuối quản lý Project mà không có JWT Bearer token hợp lệ đều bị từ chối ngay tại bộ lọc bảo mật với mã 401 (`UNAUTHORIZED`).

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống PHẢI cung cấp khả năng tạo Project mới (`POST /api/projects`) cho người dùng đã được xác thực thông qua JWT Bearer token.
- **FR-002**: Hệ thống PHẢI xác thực dữ liệu đầu vào khi tạo Project: tên dự án là bắt buộc (độ dài từ 1 đến 120 ký tự); mô tả dự án là tùy chọn (độ dài tối đa 500 ký tự); địa chỉ Webhook là tùy chọn (phải là định dạng URI hợp lệ nếu có).
- **FR-003**: Khi tạo Project thành công, hệ thống PHẢI tự động sinh mã định danh duy nhất có tiền tố `prj_` (ví dụ: `prj_1c05d9...`), tự động trích xuất và gán mã định danh của người dùng hiện tại vào trường `ownerId`, thiết lập trạng thái ban đầu là `ACTIVE`, và khởi tạo các mốc thời gian `createdAt`, `updatedAt` theo chuẩn ISO 8601.
- **FR-004**: Hệ thống PHẢI cung cấp khả năng liệt kê toàn bộ Project đang hoạt động (`GET /api/projects`) thuộc quyền sở hữu của người dùng hiện tại (`ownerId`), sử dụng chỉ mục thứ cấp toàn cục (GSI `owner-index`) với Partition Key là `ownerId` và Sort Key là `createdAt`, sắp xếp mặc định theo thứ tự mới nhất lên đầu (`createdAt` giảm dần, `ScanIndexForward = false`), loại trừ các dự án có trạng thái `DELETED`, tuyệt đối KHÔNG thực hiện thao tác quét toàn bảng (DynamoDB Scan).
- **FR-005**: Hệ thống PHẢI cung cấp khả năng truy vấn chi tiết một Project theo mã (`GET /api/projects/{projectId}`) cho các dự án đang hoạt động.
- **FR-006**: Hệ thống PHẢI cung cấp khả năng cập nhật một phần (`PATCH /api/projects/{projectId}`) cho phép sửa tên dự án, mô tả dự án và cấu hình địa chỉ `webhookUrl` (hỗ trợ xóa webhook bằng giá trị null), đồng thời tự động cập nhật mốc thời gian `updatedAt`.
- **FR-007**: Hệ thống PHẢI cung cấp khả năng xóa Project (`DELETE /api/projects/{projectId}`) theo cơ chế Soft Delete.
- **FR-008**: Trước khi thực hiện thao tác xóa Project, hệ thống PHẢI kiểm tra trạng thái các video thuộc dự án đó; nếu tồn tại bất kỳ video nào đang trong trạng thái xử lý dở dang (`UPLOADING`, `PENDING` hoặc `PROCESSING`), hệ thống PHẢI từ chối xóa và phản hồi mã lỗi 409 Conflict với mã lỗi `RESOURCE_IN_USE`.
- **FR-009**: Nếu dự án thỏa mãn điều kiện không còn video nào đang xử lý dở dang, hệ thống PHẢI tiến hành Soft Delete bản ghi Project (cập nhật trạng thái thành `DELETED`, thiết lập mốc thời gian `deletedAt`), bảo lưu nguyên vẹn toàn bộ các bản ghi video liên quan (`COMPLETED`, `FAILED_*`) cùng các object S3, và phản hồi mã thành công 204 No Content.
- **FR-010**: Hệ thống PHẢI áp dụng kiểm tra quyền sở hữu nghiêm ngặt (Ownership Isolation) tại tầng dịch vụ cho mọi thao tác đọc chi tiết, cập nhật và xóa Project: nếu người dùng yêu cầu không phải là chủ sở hữu (`ownerId != currentUser.userId`), hệ thống PHẢI từ chối với mã lỗi 403 Forbidden (`ACCESS_DENIED`).
- **FR-011**: Nếu mã dự án yêu cầu không tồn tại trong hệ thống hoặc đã bị xóa mềm (`status == DELETED`), hệ thống PHẢI phản hồi mã lỗi 404 Not Found (`RESOURCE_NOT_FOUND`).
- **FR-012**: Mọi yêu cầu truy cập các điểm cuối Project không có token hoặc token không hợp lệ PHẢI bị từ chối với mã lỗi 401 Unauthorized (`UNAUTHORIZED`).
- **FR-013**: Toàn bộ cấu trúc phản hồi thành công và thông điệp lỗi PHẢI tuân thủ hợp đồng OpenAPI chuẩn hóa của dự án (`ProjectResponse`, `ProjectListResponse`, `ErrorResponse`).
- **FR-014**: Hệ thống PHẢI ngăn chặn việc tạo tài nguyên mới (như khởi tạo upload video hay cấp API key) hoặc chỉnh sửa dữ liệu thông qua một Project đã bị đánh dấu `DELETED`.

### Key Entities *(include if feature involves data)*

- **Project (Dự án)**: Không gian tổ chức video và phạm vi gán API Key của người dùng.
  - Các thuộc tính chính:
    - `projectId` (String, Khóa chính Partition Key): Mã định danh duy nhất của dự án, định dạng tiền tố `prj_`.
    - `ownerId` (String, GSI Partition Key): Mã định danh của người dùng sở hữu dự án (`usr_...`).
    - `name` (String, Bắt buộc): Tên dự án (1-120 ký tự).
    - `description` (String, Tùy chọn): Mô tả tóm tắt dự án (tối đa 500 ký tự).
    - `webhookUrl` (String, Tùy chọn): Địa chỉ URL nhận callback thông báo trạng thái video (P1).
    - `status` (String): Trạng thái dự án, nhận giá trị `ACTIVE` (đang hoạt động) hoặc `DELETED` (đã xóa mềm).
    - `createdAt` (String ISO 8601, GSI Sort Key): Thời điểm dự án được khởi tạo.
    - `updatedAt` (String ISO 8601): Thời điểm dự án được cập nhật gần nhất.
    - `deletedAt` (String ISO 8601, Tùy chọn): Thời điểm dự án bị xóa mềm (null khi đang hoạt động).
- **Video Active Status Check (Thực thể kiểm tra trạng thái video hoạt động)**:
  - Khái niệm logic đại diện cho các video đang trong chu trình xử lý hoạt động thuộc về dự án (`status` thuộc nhóm `{UPLOADING, PENDING, PROCESSING}`), được sử dụng làm điều kiện bảo vệ khi xóa dự án.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Người dùng hoàn thành thao tác tạo Project mới và nhận kết quả phản hồi trong thời gian dưới 1 giây.
- **SC-002**: Người dùng truy vấn danh sách Project của mình và nhận dữ liệu phản hồi trong thời gian dưới 1 giây đối với tài khoản có tới 100 dự án.
- **SC-003**: 100% các thao tác truy cập, chỉnh sửa hoặc xóa Project từ người dùng không phải là chủ sở hữu đều bị từ chối chính xác với mã lỗi 403 (`ACCESS_DENIED`), không để lộ bất kỳ thông tin dự án nào.
- **SC-004**: 100% các yêu cầu xóa Project có chứa video đang trong trạng thái xử lý (`UPLOADING`, `PENDING`, `PROCESSING`) bị ngăn chặn thành công với mã lỗi 409 (`RESOURCE_IN_USE`), đảm bảo không có tác vụ xử lý nào bị mồ côi.
- **SC-005**: 100% các thao tác xóa Project hợp lệ được thực hiện theo cơ chế Soft Delete thành công, bảo toàn 100% bản ghi video lịch sử (`COMPLETED`, `FAILED_*`) và file S3 mà không phát sinh lỗi.
- **SC-006**: 100% các truy vấn danh sách Project đều được thực thi thông qua chỉ mục thứ cấp GSI `owner-index`, đạt tỷ lệ quét bảng bằng 0 (Zero Table Scans).
- **SC-007**: Đạt tỷ lệ 100% ca kiểm thử tự động (Unit Tests & Integration Tests trên Testcontainers DynamoDB) vượt qua thành công, bảo đảm Zero Regression đối với các tính năng đã phát triển trước đó (`F-001`, `F-002`, `F-003`).

---

## Assumptions

- **Mô hình sở hữu đơn giản (Single Owner Model)**: Trong giai đoạn MVP, mỗi Project chỉ thuộc về duy nhất 1 người dùng cá nhân (`ownerId`); tính năng chia sẻ nhóm (Team Collaboration, Role-Based Access Control) chưa thuộc phạm vi của tính năng này.
- **Phương thức xác thực**: Toàn bộ các thao tác trên điểm cuối `/api/projects` được thực hiện bởi người dùng giao diện Web và được bảo vệ thông qua Bearer JWT token cấp từ Feature `F-003`.
- **Chính sách xóa Video & Vòng đời lưu trữ (Out of Scope)**: Chính sách xóa từng video, đưa video vào thùng rác (Trash), chu kỳ lưu giữ 30 ngày (30-day retention) và quy trình dọn dẹp bất đồng bộ file S3 là các nghiệp vụ độc lập thuộc các feature sau (F-008), không thuộc phạm vi xử lý của Feature F-004.
