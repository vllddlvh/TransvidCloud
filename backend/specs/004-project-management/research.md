# Research & Technical Decisions: Quản lý Project & Phân quyền sở hữu tài nguyên

**Feature**: `F-004: Quản lý Project & Phân quyền sở hữu tài nguyên (Epic A2)`  
**Branch**: `004-project-management` | **Date**: 2026-10-31  
**Spec Reference**: [specs/004-project-management/spec.md](spec.md)

---

## 1. Nghiên cứu 1: Thiết kế tầng lưu trữ DynamoDB cho Project và GSI `owner-index`

### Bối cảnh & Yêu cầu
Theo [contracts/dynamodb-tables.md](../../../contracts/dynamodb-tables.md), bảng `projects` có tên `tvc-{env}-projects` với Partition Key là `projectId` và Global Secondary Index (GSI) `owner-index` có Partition Key là `ownerId` và Sort Key là `createdAt`. Hệ thống cần hỗ trợ tạo, lấy chi tiết theo `projectId` và liệt kê toàn bộ dự án thuộc quyền sở hữu của một người dùng theo thứ tự mới nhất lên đầu.

### Quyết định kỹ thuật
- **Sử dụng `DynamoDbEnhancedClient`**: Định nghĩa lớp thực thể `ProjectItem` ánh xạ với bảng `tvc-{env}-projects` bằng các annotation `@DynamoDbBean`, `@DynamoDbPartitionKey`, `@DynamoDbSecondaryPartitionKey(indexNames = "owner-index")`, `@DynamoDbSecondarySortKey(indexNames = "owner-index")`.
- **Truy vấn GSI `owner-index`**: Sử dụng `DynamoDbIndex.query()` với `QueryConditional.keyEqualTo(Key.builder().partitionValue(ownerId).build())`.
- **Sắp xếp mặc định**: Thiết lập thuộc tính `scanIndexForward(false)` trong `QueryEnhancedRequest` để trả về kết quả sắp xếp giảm dần theo thời gian tạo (`createdAt`), đảm bảo các dự án mới nhất xuất hiện ở đầu danh sách theo đúng yêu cầu đã làm rõ.
- **Phát sinh mã `projectId`**: Sử dụng hàm tiện ích sinh ID định dạng `prj_` kết hợp với chuỗi hex ngẫu nhiên an toàn 12 ký tự (tương tự như `usr_` của Feature F-003, ví dụ: `prj_1c05d9a2b4e8`), đảm bảo tính duy nhất và khả năng phân biệt cao.

### Các giải pháp thay thế đã đánh giá
- *Dùng DynamoDB Scan*: Bị loại bỏ vì vi phạm nghiêm trọng Engineering Standards và Constitution (gây suy giảm hiệu năng khi số lượng bản ghi tăng lớn).
- *Lưu danh sách `projectIds` trong bản ghi `UserItem`*: Bị loại bỏ vì giới hạn 400KB của DynamoDB Item và gây xung đột khi cập nhật danh sách đồng thời.

---

## 2. Nghiên cứu 2: Cơ chế Soft Delete và Lọc dự án đã xóa

### Bối cảnh & Yêu cầu
Theo kết quả phiên làm rõ `/speckit-clarify` (Session 2026-10-31), thao tác xóa Project không xóa vật lý bản ghi trong DynamoDB mà thực hiện **Soft Delete**. Mục đích là bảo tồn dữ liệu lịch sử, không xóa cascade các bản ghi video hoàn tất/thất bại và không xóa file trên S3. Các truy vấn danh sách `GET /api/projects` phải tự động loại trừ các dự án đã bị xóa mềm, và các yêu cầu truy cập chi tiết/sửa/xóa một dự án đã xóa mềm phải bị từ chối với mã 404 (`RESOURCE_NOT_FOUND`).

### Quyết định kỹ thuật
- **Thuộc tính trạng thái**: Bổ sung hai trường vào `ProjectItem`:
  - `status`: Kiểu `String`, nhận giá trị `ACTIVE` khi khởi tạo và chuyển thành `DELETED` khi xóa mềm.
  - `deletedAt`: Kiểu `String` (ISO 8601 UTC), lưu mốc thời gian xóa (ví dụ: `2026-10-31T20:30:00Z`), nhận giá trị `null` khi đang hoạt động.
- **Thao tác xóa mềm**: `ProjectRepository.softDelete(String projectId)` cập nhật bản ghi bằng conditional update, thiết lập `status = "DELETED"`, `deletedAt = Instant.now().toString()`, `updatedAt = Instant.now().toString()`.
- **Lọc danh sách `GET /api/projects`**:
  - Khi truy vấn qua GSI `owner-index`, bổ sung `Expression` lọc: `attribute_not_exists(#status) OR #status = :active` (hoặc `attribute_not_exists(#deletedAt)`), kết hợp với lọc bảo vệ tại tầng Service stream filter: `items.stream().filter(p -> "ACTIVE".equals(p.getStatus())).toList()`.
- **Xử lý khi truy cập Project đã xóa**: Tầng Service khi đọc chi tiết `getProjectById` nếu tìm thấy bản ghi nhưng `status == DELETED` thì coi như không tồn tại, ném `AppException(ErrorCode.RESOURCE_NOT_FOUND)` -> HTTP 404.

### Các giải pháp thay thế đã đánh giá
- *Xóa cứng (Physical DeleteItem)*: Bị loại bỏ do người dùng đã chỉ đạo rõ ràng tại `/speckit-clarify`: xóa cứng làm mất dấu vết audit và gây rủi ro mồ côi tài nguyên.
- *Chuyển bản ghi sang bảng `projects-archive`*: Bị loại bỏ vì vi phạm nguyên tắc YAGNI/KISS, làm phức tạp hóa kiến trúc không cần thiết cho giai đoạn hiện tại.

---

## 3. Nghiên cứu 3: Kiểm tra an toàn trước khi xóa (Safety Guard with Active Videos)

### Bối cảnh & Yêu cầu
Yêu cầu `FR-008` và hợp đồng `contracts/openapi.yaml` quy định: hệ thống phải từ chối xóa Project nếu vẫn còn video bên trong đang trong quá trình tải lên hoặc xử lý dở dang (`UPLOADING`, `PENDING` hoặc `PROCESSING`). Phản hồi phải trả về HTTP 409 Conflict với mã lỗi `RESOURCE_IN_USE`.

### Quyết định kỹ thuật
- **Bảng `videos` và GSI `project-index`**: Bảng `tvc-{env}-videos` có GSI `project-index` với Partition Key là `projectId` và Sort Key là `createdAt` (theo `contracts/dynamodb-tables.md`).
- **Thực hiện kiểm tra an toàn**:
  - Tầng Service của Project sẽ gọi phương thức kiểm tra `hasActiveVideos(String projectId)` thông qua `VideoSafetyChecker` hoặc adapter truy vấn bảng `videos`.
  - Truy vấn GSI `project-index` theo `projectId` với `FilterExpression`:
    `#status IN (:s1, :s2, :s3)` với các giá trị tương ứng `UPLOADING`, `PENDING`, `PROCESSING`.
  - Giới hạn truy vấn `limit(1)`: Chỉ cần tồn tại ít nhất 1 video đang xử lý là lập tức dừng truy vấn và ném lỗi `AppException(ErrorCode.RESOURCE_IN_USE)`.
  - Nếu kết quả rỗng (không có video in-flight): Cho phép tiếp tục thực hiện Soft Delete bản ghi Project.

### Các giải pháp thay thế đã đánh giá
- *Quét toàn bảng `videos`*: Bị loại bỏ vì vi phạm chuẩn kiến trúc (không dùng Scan).
- *Lưu cờ `activeVideoCount` trực tiếp trong bản ghi Project*: Tiềm ẩn nguy cơ bất đồng bộ dữ liệu (race condition) khi có nhiều worker cập nhật trạng thái video song song. Truy vấn GSI `project-index` của bảng `videos` đảm bảo dữ liệu là nguồn sự thật duy nhất (SSOT).

---

## 4. Nghiên cứu 4: Kiểm tra phân quyền sở hữu tài nguyên (Ownership Isolation)

### Bối cảnh & Yêu cầu
Mỗi request thao tác với Project (`GET`, `PATCH`, `DELETE`) phải xác minh danh tính người dùng hiện tại và chỉ cho phép thực hiện nếu người dùng đó là chủ sở hữu (`ownerId`). Nếu truy cập Project của người dùng khác, hệ thống phải từ chối với mã 403 Forbidden (`ACCESS_DENIED`).

### Quyết định kỹ thuật
- **Trích xuất Principal hiện tại**:
  - Filter `SecurityAuthFilter` (đã xây dựng ở Feature F-003) phân tích Bearer JWT và thiết lập `Authentication` vào `SecurityContextHolder`.
  - Trong Controller, tiêm danh tính người dùng thông qua `Authentication.getName()` hoặc `jwt.getSubject()` (chính là `userId`, ví dụ: `usr_bf0493d495c1`).
- **Xác thực quyền sở hữu tại Service layer**:
  - Hàm `getProject(String projectId, String currentUserId)`:
    1. Đọc bản ghi `ProjectItem` từ DynamoDB theo `projectId`.
    2. Nếu không tìm thấy hoặc `status == DELETED` -> ném `AppException(ErrorCode.RESOURCE_NOT_FOUND)` (404).
    3. Nếu `!project.getOwnerId().equals(currentUserId)` -> ném `AppException(ErrorCode.ACCESS_DENIED)` (403).
    4. Nếu khớp -> trả về kết quả DTO.
  - Quy tắc này áp dụng tương tự và thống nhất cho cả `updateProject` và `deleteProject`.

---

## 5. Nghiên cứu 5: Chuẩn hóa DTO, Validation và Error Handling

### Bối cảnh & Yêu cầu
Khớp 100% với hợp đồng `contracts/openapi.yaml`:
- `CreateProjectRequest`: `name` (1..120), `description` (..500), `webhookUrl` (URI, optional).
- `UpdateProjectRequest`: `name` (1..120), `description` (..500), `webhookUrl` (URI, optional, nullable).
- `ProjectResponse`: `projectId`, `ownerId`, `name`, `description`, `webhookUrl`, `createdAt`, `updatedAt`.
- `ProjectListResponse`: `items: List<ProjectResponse>`.

### Quyết định kỹ thuật
- **DTOs dạng Java `record` bất biến**:
  - `CreateProjectRequest`: sử dụng `@NotBlank`, `@Size(min=1, max=120)`, `@Size(max=500)`, `@Pattern` cho URL webhook.
  - `UpdateProjectRequest`: `@Size(min=1, max=120)`, `@Size(max=500)`.
  - `ProjectResponse`: Ánh xạ qua MapStruct từ `ProjectItem`.
  - `ProjectListResponse`: Wrapper chứa danh sách `List<ProjectResponse>`.
- **Mở rộng `ErrorCode`**:
  - Bổ sung vào enum `ErrorCode` các mã lỗi chuẩn theo contract OpenAPI:
    - `ACCESS_DENIED(1007, "Access denied", HttpStatus.FORBIDDEN)` (đã có từ F-001/F-002, mã 403).
    - `RESOURCE_NOT_FOUND(1005, "Resource not found", HttpStatus.NOT_FOUND)` (mã 404).
    - `RESOURCE_IN_USE(1009, "Resource is currently in use", HttpStatus.CONFLICT)` (mã 409).
- **Validation URL Webhook**: Hỗ trợ cả scheme `http://` và `https://` để tạo điều kiện thuận lợi cho việc kiểm thử môi trường nội bộ/local (ví dụ: `http://localhost:3000/webhook`), đồng thời bảo đảm đúng định dạng URL tiêu chuẩn.
