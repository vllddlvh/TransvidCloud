# Feature Specification: Quản lý API Key & Xác thực Dual Security JWT + X-API-Key

**Feature Branch**: `phuong-backend`

**Created**: 2026-10-31

**Status**: Draft

**Input**: User description: "F-005: Quản lý API Key & Xác thực Dual Security JWT + X-API-Key (Epic A2)"

## Clarifications

### Session 2026-10-31

- Q: Giới hạn số lượng API Key tối đa cho mỗi Project là bao nhiêu? → A: **Tối đa 5 API Key** đang hoạt động cho mỗi Project. Nếu Project đã có đủ 5 key mà người dùng tiếp tục gửi yêu cầu tạo thêm, hệ thống sẽ từ chối với mã lỗi HTTP 400 Bad Request (`INVALID_KEY` / "Maximum 5 API keys allowed per project").
- Q: Hệ thống nên xử lý thế nào khi một request gửi đồng thời cả header Bearer JWT và header X-API-Key? → A: **Ưu tiên `Authorization: Bearer <JWT>`**. Nếu request chứa Bearer token hợp lệ, hệ thống sẽ xác thực theo ngữ cảnh người dùng SaaS Web; chỉ khi không có tiêu đề Bearer token hoặc Bearer token không được cung cấp, hệ thống mới kiểm tra và xác thực qua tiêu đề `X-API-Key`.
- Q: Định dạng hiển thị `keyPrefix` của API Key trên giao diện quản trị là gì? → A: **`tvc_live_` + 8 ký tự hex đầu + `...`** (Ví dụ: `tvc_live_7f9a8b2c...`), khớp hoàn toàn với quy chuẩn hợp đồng `contracts/openapi.yaml`.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sinh API Key mới cho Project (Priority: P1)

Người dùng quản trị sở hữu Project (sau khi đăng nhập vào SaaS Web bằng JWT) muốn tạo một mã khóa truy cập API Key mới cho dự án của mình để tích hợp với hệ thống ngoài hoặc tự động hóa gọi API mà không cần phụ thuộc vào phiên làm việc cá nhân. Người dùng cung cấp tên gợi nhớ của key (ví dụ: "E-Learning Backend Key"). Hệ thống kiểm tra số lượng key hiện tại của project (tối đa 5 keys), sinh một chuỗi ngẫu nhiên bảo mật cao có tiền tố định danh (`tvc_live_...`), lưu trữ bản băm SHA-256 an toàn vào bảng lưu trữ và trả về khóa bí mật đầy đủ (`rawKey`) cho người dùng lưu lại. Hệ thống nêu rõ cảnh báo khóa chỉ hiển thị duy nhất một lần này.

**Why this priority**: API Key là phương thức xác thực cốt lõi cho các nhà phát triển bên ngoài (Developer Platform / PaaS API) tương tác với hệ thống TransvidCloud. Không có API Key, các hệ thống bên thứ ba không thể tải lên hay quản lý video theo chương trình.

**Independent Test**: Có thể kiểm thử độc lập hoàn toàn bằng cách gửi yêu cầu tạo API Key mới với tên hợp lệ từ một phiên người dùng sở hữu Project; xác minh hệ thống tạo thành công, trả về HTTP 201 kèm chuỗi `rawKey`, tiền tố `keyPrefix` (`tvc_live_xxxxxxxx...`) và mã băm `keyHash`, đồng thời xác nhận trong cơ sở dữ liệu chỉ lưu bản băm mà không lưu `rawKey`.

**Acceptance Scenarios**:

1. **Given** Người dùng là chủ sở hữu hợp lệ của một Project đang hoạt động (`ACTIVE`) và Project có dưới 5 API Key, **When** Người dùng gửi yêu cầu `POST /api/projects/{projectId}/api-keys` với tên khóa hợp lệ (từ 1 đến 100 ký tự), **Then** Hệ thống sinh khóa mới ngẫu nhiên, lưu bản băm SHA-256 (`keyHash`) cùng các trường thông tin vào cơ sở dữ liệu và phản hồi HTTP 201 Created chứa `rawKey`, `keyHash`, `keyPrefix`, `name`, `projectId` và thời gian tạo `createdAt`.
2. **Given** Người dùng gửi yêu cầu với tên khóa bị để trống hoặc vượt quá 100 ký tự, **When** Hệ thống xử lý yêu cầu, **Then** Hệ thống từ chối yêu cầu và phản hồi mã lỗi HTTP 400 Bad Request (`INVALID_KEY` hoặc lỗi dữ liệu validation).
3. **Given** Một Project đã đạt đến giới hạn tối đa 5 API Key đang hoạt động, **When** Người dùng gửi yêu cầu tạo thêm API Key, **Then** Hệ thống từ chối và phản hồi mã lỗi HTTP 400 Bad Request kèm thông báo vượt quá số lượng key tối đa cho phép (`Maximum 5 API keys allowed per project`).
4. **Given** Người dùng A cố gắng tạo API Key cho một Project thuộc quyền sở hữu của Người dùng B, **When** Gửi yêu cầu, **Then** Hệ thống từ chối với HTTP 403 Forbidden (`ACCESS_DENIED`).
5. **Given** Người dùng gửi yêu cầu tạo API Key cho một Project không tồn tại hoặc đã bị xóa mềm (`DELETED`), **When** Gửi yêu cầu, **Then** Hệ thống từ chối với HTTP 404 Not Found (`RESOURCE_NOT_FOUND`).

---

### User Story 2 - Liệt kê danh sách API Key của Project (Priority: P1)

Người quản trị dự án muốn xem danh sách toàn bộ các API Key đang tồn tại trong dự án của mình để theo dõi, quản lý vòng đời và kiểm soát các ứng dụng đang tích hợp. Danh sách hiển thị tên gợi nhớ, mã băm định danh (`keyHash`), tiền tố nhận diện (`keyPrefix`) và thời điểm tạo, tuyệt đối không hiển thị giá trị đầy đủ `rawKey`.

**Why this priority**: Cho phép người dùng kiểm toán, nhận biết các khóa đang hoạt động trong dự án và lấy được định danh khóa cần thu hồi khi cần thiết.

**Independent Test**: Tạo 2 API Key cho Project A, sau đó gửi yêu cầu `GET /api/projects/{projectId}/api-keys` để kiểm tra đúng 2 bản ghi metadata xuất hiện và trường `rawKey` hoàn toàn không có trong phản hồi.

**Acceptance Scenarios**:

1. **Given** Project đang có các API Key hợp lệ, **When** Chủ sở hữu Project gửi yêu cầu `GET /api/projects/{projectId}/api-keys`, **Then** Hệ thống trả về danh sách các API Key kèm HTTP 200 OK, mỗi phần tử chứa `keyHash`, `projectId`, `name`, `keyPrefix`, `createdAt` và không chứa `rawKey`.
2. **Given** Project chưa có bất kỳ API Key nào, **When** Chủ sở hữu gửi yêu cầu xem danh sách, **Then** Hệ thống phản hồi HTTP 200 OK kèm danh sách rỗng mà không gây lỗi.
3. **Given** Người dùng không phải là chủ sở hữu của Project, **When** Gửi yêu cầu liệt kê API Key, **Then** Hệ thống từ chối với HTTP 403 Forbidden (`ACCESS_DENIED`).
4. **Given** Người dùng gửi yêu cầu lên một Project không tồn tại hoặc đã bị xóa mềm (`DELETED`), **When** Gửi yêu cầu, **Then** Hệ thống phản hồi HTTP 404 Not Found (`RESOURCE_NOT_FOUND`).

---

### User Story 3 - Thu hồi (Revoke / Xóa) API Key (Priority: P1)

Khi một khóa API bị nghi ngờ lộ lọt hoặc hệ thống tích hợp ngừng hoạt động, người quản trị dự án muốn thu hồi (vô hiệu hóa tức thì) API Key đó bằng cách xóa bản băm định danh tương ứng khỏi hệ thống.

**Why this priority**: Đảm bảo an toàn bảo mật; khi khóa bị xâm phạm hoặc hết nhu cầu sử dụng, người dùng có thể ngay lập tức cắt quyền truy cập của khóa đó.

**Independent Test**: Tạo một API Key, sau đó gửi yêu cầu `DELETE /api/projects/{projectId}/api-keys/{keyHash}`; xác minh hệ thống phản hồi HTTP 204 No Content, bản ghi bị xóa khỏi cơ sở dữ liệu và ngay lập tức không thể dùng key này để gọi các API Video.

**Acceptance Scenarios**:

1. **Given** Một API Key hợp lệ thuộc Project của người dùng đang đăng nhập, **When** Người dùng gửi yêu cầu `DELETE /api/projects/{projectId}/api-keys/{keyHash}`, **Then** Hệ thống xóa bản ghi khóa khỏi bảng lưu trữ và phản hồi HTTP 204 No Content.
2. **Given** Khóa API đã bị thu hồi hoặc mã băm `keyHash` không tồn tại trong hệ thống, **When** Người dùng gửi yêu cầu xóa, **Then** Hệ thống phản hồi HTTP 404 Not Found (`RESOURCE_NOT_FOUND`).
3. **Given** Mã `keyHash` tồn tại nhưng thuộc về một Project khác, **When** Người dùng gửi yêu cầu xóa trong Project hiện tại, **Then** Hệ thống từ chối và phản hồi HTTP 404 Not Found (`RESOURCE_NOT_FOUND`) để đảm bảo tính toàn vẹn phạm vi dự án.
4. **Given** Người dùng không sở hữu Project chứa API Key đó, **When** Gửi yêu cầu xóa, **Then** Hệ thống từ chối với HTTP 403 Forbidden (`ACCESS_DENIED`).

---

### User Story 4 - Xác thực Dual Security với X-API-Key trên các endpoint nghiệp vụ (Priority: P1)

Hệ thống bên ngoài (External Developer Client) gọi các API thuộc phạm vi Project (như khởi tạo video, truy vấn video, xóa video) bằng cách truyền khóa bí mật qua tiêu đề HTTP `X-API-Key: tvc_live_...`. Hệ thống Backend tự động nhận diện tiêu đề này, băm khóa bằng SHA-256 để kiểm tra trong bảng lưu trữ, xác thực tính hợp lệ và tự động thiết lập phạm vi ngữ cảnh thực thi đúng với `projectId` của khóa đó. Nếu gọi chéo sang Project khác, hệ thống phải lập tức từ chối.

**Why this priority**: Đây là bản chất của cơ chế xác thực kép (Dual Security). Nó cho phép ứng dụng bên thứ ba truy cập các tài nguyên video của dự án một cách an toàn, ổn định và độc lập với phiên đăng nhập của người dùng web.

**Independent Test**: Gửi yêu cầu `POST /api/projects/{projectId}/videos` với tiêu đề `X-API-Key` hợp lệ tương ứng với `projectId` -> xác minh yêu cầu được xác thực thành công; gửi cùng key đó tới một `projectId` khác -> xác minh bị từ chối HTTP 403 (`ACCESS_DENIED`); gửi key sai hoặc key đã thu hồi -> xác minh bị từ chối HTTP 401 (`UNAUTHENTICATED`).

**Acceptance Scenarios**:

1. **Given** Khách hàng bên ngoài gửi yêu cầu API kèm tiêu đề `X-API-Key` mang khóa hợp lệ của Project A, **When** Yêu cầu nhắm tới tài nguyên thuộc Project A, **Then** Hệ thống xác thực thành công và xử lý nghiệp vụ bình thường.
2. **Given** Khách hàng gửi yêu cầu API với tiêu đề `X-API-Key` sai, không tồn tại hoặc đã bị thu hồi, **When** Hệ thống xử lý yêu cầu, **Then** Hệ thống từ chối yêu cầu và phản hồi HTTP 401 Unauthorized (`UNAUTHENTICATED`).
3. **Given** Khách hàng sử dụng `X-API-Key` hợp lệ của Project A nhưng lại gửi yêu cầu thao tác trên đường dẫn của Project B (ví dụ: `POST /api/projects/prj_B/videos`), **When** Hệ thống đối chiếu phạm vi ủy quyền, **Then** Hệ thống từ chối truy cập và phản hồi HTTP 403 Forbidden (`ACCESS_DENIED`).
4. **Given** Một yêu cầu API gửi đến đồng thời cả tiêu đề `Authorization: Bearer <JWT>` và `X-API-Key: tvc_live_...`, **When** Hệ thống xử lý xác thực, **Then** Hệ thống ưu tiên xác thực theo Bearer JWT của người dùng SaaS Web; chỉ khi không có Bearer token mới thẩm định qua `X-API-Key`.

---

### User Story 5 - Đảm bảo khả năng tương thích xác thực Bearer JWT trên các endpoint Video (Priority: P1)

Người dùng đăng nhập trên SaaS Web tiếp tục sử dụng Access Token dạng Bearer JWT (`Authorization: Bearer <token>`) để thao tác trên các endpoint Video của dự án mà mình sở hữu. Hệ thống bộ lọc bảo mật phải hỗ trợ song song cả hai cơ chế mà không gây xung đột hay làm hỏng tính năng đã xây dựng trước đó (Zero Regression).

**Why this priority**: Bảo đảm trải nghiệm liền mạch cho người dùng SaaS Web và bảo vệ toàn bộ các tính năng đã hoạt động ở các feature trước.

**Independent Test**: Gửi các yêu cầu CRUD Video với Bearer JWT hợp lệ -> xác minh hệ thống xác thực và kiểm tra quyền sở hữu `ownerId` thành công 100%.

**Acceptance Scenarios**:

1. **Given** Người dùng đăng nhập gửi yêu cầu kèm Bearer JWT hợp lệ tới các API Video của dự án mà mình sở hữu, **When** Hệ thống xử lý, **Then** Hệ thống xác thực danh tính người dùng và cho phép thực thi bình thường.
2. **Given** Người dùng gửi yêu cầu không có cả Bearer JWT và `X-API-Key`, **When** Hệ thống xử lý các endpoint được bảo vệ, **Then** Hệ thống phản hồi HTTP 401 Unauthorized (`UNAUTHENTICATED`).
3. **Given** Khách hàng sử dụng `X-API-Key` để cố gắng truy cập các endpoint chỉ dành riêng cho tài khoản người dùng cá nhân (như `POST /api/projects`, `GET /api/projects`, `POST /api/projects/{projectId}/api-keys`), **When** Hệ thống kiểm tra quyền hạn, **Then** Hệ thống từ chối với HTTP 401 Unauthorized hoặc 403 Forbidden vì các endpoint này chỉ chấp nhận `BearerAuth`.

---

### Edge Cases

- Tiêu đề `X-API-Key` có mặt trong request nhưng giá trị rỗng, chỉ gồm khoảng trắng hoặc không đúng định dạng tiền tố `tvc_live_` -> Phản hồi ngay HTTP 401 Unauthorized (`UNAUTHENTICATED`).
- Giá trị chuỗi bí mật của API Key sau khi băm SHA-256 bị va chạm hoặc không tìm thấy trong bảng lưu trữ -> Phản hồi HTTP 401 Unauthorized (`UNAUTHENTICATED`).
- Người dùng gửi yêu cầu thu hồi API Key với định dạng chuỗi `keyHash` không hợp lệ -> Phản hồi HTTP 400 Bad Request hoặc 404 Not Found.
- Dự án cha bị xóa mềm (`status = DELETED`), nhưng client bên ngoài vẫn gửi request kèm `X-API-Key` của dự án đó -> Hệ thống từ chối với HTTP 404 Not Found (`RESOURCE_NOT_FOUND`) hoặc HTTP 403 Forbidden, không cho phép tiếp tục tạo hay xử lý tài nguyên.
- Tên API Key chứa các ký tự đặc biệt hoặc HTML/script -> Hệ thống chỉ lưu trữ chuỗi văn bản thuần túy (tối đa 100 ký tự) và từ chối các chuỗi không hợp lệ qua validation chuẩn.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống BẮT BUỘC cung cấp endpoint `POST /api/projects/{projectId}/api-keys` cho phép chủ sở hữu dự án tạo API Key mới với tên gợi nhớ từ 1 đến 100 ký tự.
- **FR-002**: Chuỗi `rawKey` sinh ra BẮT BUỘC sử dụng nguồn ngẫu nhiên bảo mật cao (CSPRNG), có tiền tố `tvc_live_` và giá trị ngẫu nhiên đủ độ phức tạp (tối thiểu 32 ký tự hex) để đảm bảo không thể đoán trước.
- **FR-003**: Hệ thống BẮT BUỘC chỉ lưu trữ giá trị băm `SHA-256` của khóa (`keyHash`) trong bảng lưu trữ DynamoDB `apikeys`, TUYỆT ĐỐI KHÔNG lưu trữ chuỗi `rawKey` dưới dạng bản rõ ở bất kỳ vị trí nào.
- **FR-004**: Giá trị `rawKey` BẮT BUỘC chỉ được trả về một lần duy nhất trong phản hồi HTTP 201 Created khi khởi tạo và không bao giờ xuất hiện lại trong bất kỳ phản hồi API hay bản ghi log nào.
- **FR-005**: Hệ thống BẮT BUỘC sinh trường `keyPrefix` theo định dạng `tvc_live_` + 8 ký tự hex đầu + `...` (ví dụ: `tvc_live_7f9a8b2c...`) để hiển thị an toàn trên giao diện quản trị.
- **FR-006**: Hệ thống BẮT BUỘC cung cấp endpoint `GET /api/projects/{projectId}/api-keys` trả về danh sách các khóa thuộc dự án bao gồm `keyHash`, `projectId`, `name`, `keyPrefix` và `createdAt`.
- **FR-007**: Hệ thống BẮT BUỘC cung cấp endpoint `DELETE /api/projects/{projectId}/api-keys/{keyHash}` cho phép chủ sở hữu thu hồi (xóa) khóa; khóa bị xóa BẮT BUỘC mất hiệu lực ngay lập tức.
- **FR-008**: Các endpoint quản lý API Key (`POST/GET /api/projects/{projectId}/api-keys` và `DELETE .../api-keys/{keyHash}`) BẮT BUỘC chỉ cho phép truy cập bởi người dùng đã xác thực bằng `BearerAuth` và là chủ sở hữu hợp pháp của dự án (`ownerId`).
- **FR-009**: Hệ thống BẮT BUỘC tích hợp bộ lọc xác thực bảo mật hỗ trợ đồng thời tiêu đề `Authorization: Bearer <JWT>` và tiêu đề `X-API-Key: <key>` trên các endpoint nghiệp vụ được đánh dấu hỗ trợ Dual Security trong hợp đồng API.
- **FR-010**: Khi xác thực qua `X-API-Key`, hệ thống BẮT BUỘC tính mã băm SHA-256 của khóa truyền vào, tra cứu trong bảng `apikeys` theo khóa chính `keyHash`. Nếu không tìm thấy, hệ thống BẮT BUỘC phản hồi HTTP 401 Unauthorized (`UNAUTHENTICATED`).
- **FR-011**: Khi xác thực thành công qua `X-API-Key`, hệ thống BẮT BUỘC kiểm tra tính tương khớp giữa `projectId` của khóa với `projectId` trên đường dẫn URL (nếu có). Trường hợp không khớp, hệ thống BẮT BUỘC từ chối với HTTP 403 Forbidden (`ACCESS_DENIED`).
- **FR-012**: Hệ thống BẮT BUỘC giới hạn mỗi Project chỉ được sở hữu tối đa 5 API Key đang hoạt động. Nếu đã có đủ 5 key mà tiếp tục tạo thêm, hệ thống BẮT BUỘC từ chối với HTTP 400 Bad Request kèm thông báo vượt quá số lượng cho phép.
- **FR-013**: Khi một request đồng thời chứa cả Bearer JWT và `X-API-Key`, hệ thống BẮT BUỘC ưu tiên thẩm định Bearer JWT trước; chỉ khi không có Bearer token mới thẩm định qua `X-API-Key`.

### Key Entities *(include if feature involves data)*

- **ApiKeyItem**:
  - `keyHash` (String, Partition Key): Mã băm SHA-256 duy nhất của API Key (64 ký tự hex).
  - `projectId` (String, GSI Partition Key `project-index`): Mã định danh dự án sở hữu khóa.
  - `name` (String): Tên gợi nhớ do người dùng đặt (1-100 ký tự).
  - `keyPrefix` (String): Tiền tố hiển thị nhận diện khóa (`tvc_live_xxxxxxxx...`).
  - `createdAt` (String / Instant): Thời điểm tạo khóa theo định dạng ISO-8601 UTC.
- **ProjectItem**:
  - Thực thể dự án sở hữu khóa (đã được xây dựng trong F-004), dùng để kiểm tra quyền sở hữu của người dùng web và trạng thái hoạt động của dự án.
- **ApiKeyAuthenticationToken / SecurityContext**:
  - Đối tượng ngữ cảnh xác thực đại diện cho phiên gọi API qua `X-API-Key`, chứa thông tin `projectId` và quyền hạn truy cập tương ứng.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Người dùng tạo mới API Key nhận được chuỗi bí mật `rawKey` trong thời gian dưới 500ms; hệ thống bảo đảm khóa sử dụng được ngay lập tức sau khi tạo.
- **SC-002**: 100% các yêu cầu xác thực bằng `X-API-Key` hợp lệ được xử lý thành công mà không đòi hỏi người dùng phải đăng nhập hay duy trì phiên JWT.
- **SC-003**: 100% các nỗ lực truy cập tài nguyên chéo dự án bằng `X-API-Key` đều bị chặn đứng với mã lỗi 403 Forbidden.
- **SC-004**: 100% các khóa đã bị xóa qua thao tác thu hồi lập tức bị từ chối truy cập với mã lỗi 401 Unauthorized trong mọi lần gọi tiếp theo.
- **SC-005**: Bảo đảm an toàn bí mật tuyệt đối: 0% trường hợp chuỗi `rawKey` bị ghi vào nhật ký hệ thống (logs) hay lưu trữ dạng bản rõ trong cơ sở dữ liệu.

## Assumptions

- **A-001**: API Key không có thời hạn tự động hết hạn (no expiration date / TTL) trong phạm vi MVP hiện tại; khóa có hiệu lực vô thời hạn cho đến khi chủ sở hữu chủ động thực hiện thu hồi qua API.
- **A-002**: Định dạng chuỗi `rawKey` tuân thủ tiền tố `tvc_live_` kết hợp với 32 ký tự hex ngẫu nhiên (tổng cộng 41 ký tự), khớp với quy chuẩn được mô tả trong tài liệu hợp đồng `contracts/openapi.yaml`.
- **A-003**: Mỗi API Key gắn chặt với duy nhất một `projectId` và kế thừa toàn bộ quyền hạn thao tác tài nguyên trong phạm vi dự án đó (không phân chia role nhỏ hơn bên trong API Key ở phiên bản hiện tại).
- **A-004**: Cơ sở dữ liệu DynamoDB bảng `tvc-{env}-apikeys` đã được định nghĩa với Partition Key `keyHash` và GSI `project-index` (PK `projectId`).
