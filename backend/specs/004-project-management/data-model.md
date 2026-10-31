# Data Model & Storage Design: Quản lý Project & Phân quyền sở hữu tài nguyên

**Feature**: `F-004: Quản lý Project & Phân quyền sở hữu tài nguyên (Epic A2)`  
**Branch**: `004-project-management` | **Date**: 2026-10-31  
**Spec Reference**: [specs/004-project-management/spec.md](spec.md)

---

## 1. DynamoDB Schema: Bảng `projects` (`tvc-{env}-projects`)

Bảng lưu trữ metadata của Project, được định cấu hình billing mode `PAY_PER_REQUEST` (on-demand), tuân thủ tuyệt đối [contracts/dynamodb-tables.md](../../../contracts/dynamodb-tables.md).

### 1.1. Cấu trúc Khóa & Chỉ mục (Key & Index Schema)

| Thuộc tính | Kiểu dữ liệu | Vai trò | Mục đích sử dụng |
|---|---|---|---|
| `projectId` | String (`S`) | **Partition Key (HASH)** | Khóa chính định danh duy nhất cho từng dự án, tiền tố `prj_` (ví dụ: `prj_1c05d9a2b4e8`). |
| `ownerId` | String (`S`) | **GSI Partition Key** | Thuộc chỉ mục GSI `owner-index`, đại diện cho `userId` của người dùng sở hữu (`usr_...`). |
| `createdAt` | String (`S`) | **GSI Sort Key (RANGE)** | Thuộc chỉ mục GSI `owner-index`, định dạng ISO 8601 UTC. Dùng để sắp xếp danh sách dự án. |

### 1.2. Danh sách thuộc tính (Attributes)

```text
{
  "projectId": "prj_1c05d9a2b4e8",           // PK, String, 16 ký tự
  "ownerId": "usr_bf0493d495c1",             // GSI PK, String, ID của chủ sở hữu
  "name": "E-Learning Course Videos",        // String, 1 - 120 ký tự
  "description": "Kho video bài giảng kỳ 1", // String, tối đa 500 ký tự (tùy chọn)
  "webhookUrl": "https://example.com/hook",  // String URI, tùy chọn
  "status": "ACTIVE",                        // String, Enum: ACTIVE | DELETED
  "createdAt": "2026-10-31T20:30:00Z",       // GSI SK, String ISO 8601 UTC
  "updatedAt": "2026-10-31T20:30:00Z",       // String ISO 8601 UTC
  "deletedAt": null                          // String ISO 8601 UTC, null khi ACTIVE, có giá trị khi DELETED
}
```

### 1.3. Global Secondary Indexes (GSI)

- **Tên GSI**: `owner-index`
- **Key Schema**:
  - Partition Key: `ownerId` (`S`)
  - Sort Key: `createdAt` (`S`)
- **Projection Type**: `ALL`
- **Access Pattern**: Liệt kê toàn bộ Project của một người dùng:
  - Lệnh truy vấn: `QueryEnhancedRequest` với điều kiện `ownerId = :currentUserId`.
  - Hướng duyệt: `scanIndexForward(false)` (mới nhất lên đầu).
  - Biểu thức lọc: `attribute_not_exists(#deletedAt)` hoặc `#status = :active`.

---

## 2. Truy vấn liên quan đến Bảng `videos` (`tvc-{env}-videos`)

Để phục vụ kiểm tra an toàn trước khi xóa (Safety Guard), Feature `F-004` thực hiện truy vấn **chỉ đọc** trên bảng `videos`.

### 2.1. Access Pattern: Kiểm tra video đang xử lý dở dang
- **Chỉ mục sử dụng**: GSI `project-index` trên bảng `videos` (PK: `projectId`, SK: `createdAt`).
- **Truy vấn**:
  - `QueryConditional.keyEqualTo(Key.builder().partitionValue(projectId).build())`
  - `FilterExpression`: `#status IN (:s1, :s2, :s3)` với `:s1 = "UPLOADING"`, `:s2 = "PENDING"`, `:s3 = "PROCESSING"`.
  - `Limit`: `1` (ngắt truy vấn ngay khi phát hiện có ít nhất 1 video chưa hoàn tất).

---

## 3. Java Classes & DTOs Architecture

### 3.1. Entity: `ProjectItem` (`@DynamoDbBean`)
Vị trí: `com.transvidcloud.backend.project.entity.ProjectItem`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class ProjectItem {

    private String projectId;
    private String ownerId;
    private String name;
    private String description;
    private String webhookUrl;
    private String status;
    private String createdAt;
    private String updatedAt;
    private String deletedAt;

    @DynamoDbPartitionKey
    public String getProjectId() {
        return projectId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "owner-index")
    public String getOwnerId() {
        return ownerId;
    }

    @DynamoDbSecondarySortKey(indexNames = "owner-index")
    public String getCreatedAt() {
        return createdAt;
    }
}
```

### 3.2. DTOs (Data Transfer Objects)
Vị trí: `com.transvidcloud.backend.project.dto`

1. **`CreateProjectRequest`** (Java `record`):
   ```java
   public record CreateProjectRequest(
       @NotBlank(message = "Tên project không được để trống")
       @Size(min = 1, max = 120, message = "Tên project phải từ 1 đến 120 ký tự")
       String name,

       @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
       String description,

       @Pattern(regexp = "^(https?://.+)?$", message = "Webhook URL phải là URL hợp lệ (http hoặc https)")
       String webhookUrl
   ) {}
   ```

2. **`UpdateProjectRequest`** (Java `record`):
   ```java
   public record UpdateProjectRequest(
       @Size(min = 1, max = 120, message = "Tên project phải từ 1 đến 120 ký tự")
       String name,

       @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
       String description,

       @Pattern(regexp = "^(https?://.+)?$", message = "Webhook URL phải là URL hợp lệ (http hoặc https)")
       String webhookUrl
   ) {}
   ```

3. **`ProjectResponse`** (Java `record`):
   ```java
   public record ProjectResponse(
       String projectId,
       String ownerId,
       String name,
       String description,
       String webhookUrl,
       String createdAt,
       String updatedAt
   ) {}
   ```

4. **`ProjectListResponse`** (Java `record`):
   ```java
   public record ProjectListResponse(
       List<ProjectResponse> items
   ) {}
   ```

### 3.3. Mapper Interface: `ProjectMapper` (MapStruct)
Vị trí: `com.transvidcloud.backend.project.mapper.ProjectMapper`

- Chuyển đổi `ProjectItem` sang `ProjectResponse`.
- Chuyển đổi danh sách `List<ProjectItem>` sang `ProjectListResponse`.
- Khởi tạo `ProjectItem` từ `CreateProjectRequest`.

---

## 4. State Machine & Vòng đời Project (Lifecycle)

```
        +-------------------------+
        |   POST /api/projects    |
        +-------------------------+
                     |
                     v
             [ Trạng thái: ACTIVE ] <-----+
                     |                    |
        PATCH /api/projects/{projectId}   |
        (Cập nhật name/desc/webhook) -----+
                     |
        DELETE /api/projects/{projectId}
        (Kiểm tra: KHÔNG còn video in-flight)
                     |
                     v
             [ Trạng thái: DELETED ]
                     |
   (Không xuất hiện trong GET /api/projects)
   (Mọi thao tác sửa/tạo con bị từ chối 404)
```

- **ACTIVE**: Dự án đang hoạt động bình thường; cho phép tạo video, cấp API key, sửa thông tin và xem chi tiết.
- **DELETED**: Dự án đã bị xóa mềm; mốc `deletedAt` được ghi nhận; dữ liệu lịch sử được bảo toàn nguyên vẹn; bị loại trừ khỏi danh sách thông thường và từ chối mọi yêu cầu biến đổi.
