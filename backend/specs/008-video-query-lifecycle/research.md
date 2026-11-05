# Nghiên Cứu & Thiết Kế Kỹ Thuật: F-008 — Truy Vấn Danh Sách (Cursor Pagination), Chi Tiết, Assets & Xóa Video

---

## 1. Cơ Chế Phân Trang Con Trỏ (Cursor Pagination) Trên DynamoDB GSI

### Bối cảnh & Thách thức
Bảng DynamoDB `tvc-{env}-videos` sử dụng GSI `project-index` có cấu trúc:
- **Partition Key**: `projectId` (String)
- **Sort Key**: `createdAt` (String - ISO-8601 UTC)
- **Table Partition Key**: `videoId` (String)

Trong DynamoDB, phân trang hiệu quả trên Index được thực hiện qua cơ chế `ExclusiveStartKey` và `LastEvaluatedKey`:
- Không sử dụng offset truyền thống (`LIMIT/OFFSET` kiểu SQL) vì DynamoDB buộc phải quét toàn bộ các bản ghi bị bỏ qua, gây lãng phí RCU và tăng thời gian phản hồi.
- `LastEvaluatedKey` trả về bản đồ các thuộc tính khóa cần thiết để DynamoDB tiếp tục đọc từ vị trí dừng của trang trước.

Đối với GSI `project-index` trên bảng `videos`, `LastEvaluatedKey` chứa 3 thuộc tính:
1. `projectId`: Khóa phân vùng của GSI (ví dụ `prj_1c05d9`).
2. `createdAt`: Khóa sắp xếp của GSI (ví dụ `2026-10-31T09:15:00Z`).
3. `videoId`: Khóa phân vùng của bảng chính (dùng để phân định bản ghi khi nhiều video có cùng thời điểm `createdAt`).

### Giải pháp Thiết kế: Tamper-proof Base64URL Cursor
Để không làm lộ cấu trúc lưu trữ nội bộ của DynamoDB ra ngoài client và ngăn chặn gian lận dữ liệu giữa các Project khác nhau:
1. **Cấu trúc DTO `VideoCursor`**:
   ```json
   {
     "projectId": "prj_1c05d9",
     "videoId": "vid_4a77e1",
     "createdAt": "2026-10-31T09:15:00Z"
   }
   ```
2. **Thuật toán Mã hóa (Encoding)**:
   - Khi DynamoDB trả về `LastEvaluatedKey`: trích xuất `projectId`, `videoId`, `createdAt`.
   - Serialize thành chuỗi JSON ngắn gọn qua `ObjectMapper`.
   - Mã hóa thành chuỗi Base64URL (không có padding `=`) bằng `Base64.getUrlEncoder().withoutPadding().encodeToString(...)`.
   - Trả về trường `nextCursor` trong `VideoPageResponse`.
3. **Thuật toán Giải mã & Thẩm định (Decoding & Validation)**:
   - Khi client truyền `?cursor=...`:
     - Nếu `cursor` rỗng hoặc null: coi là trang đầu tiên (`ExclusiveStartKey = null`).
     - Giải mã Base64URL và deserialize JSON thành `VideoCursor`.
     - **Kiểm tra chéo an ninh (Multi-tenant tamper guard)**: Đối chiếu `cursor.projectId()` với `projectId` trong đường dẫn URL. Nếu không khớp hoặc JSON hỏng ➔ Ném ngay `AppException(ErrorCode.INVALID_CURSOR)` (HTTP 400).
   - Tái tạo `ExclusiveStartKey`:
     ```java
     Map<String, AttributeValue> startKey = Map.of(
         "projectId", AttributeValue.builder().s(cursor.projectId()).build(),
         "videoId", AttributeValue.builder().s(cursor.videoId()).build(),
         "createdAt", AttributeValue.builder().s(cursor.createdAt()).build()
     );
     ```

### Sắp Xếp Giảm Dần & Lọc Bỏ OutboxItem
- Danh sách video cần hiển thị video mới nhất lên đầu ➔ Thiết lập `ScanIndexForward = false`.
- Loại bỏ các bản ghi nội bộ Transactional Outbox (có `videoId` bắt đầu bằng `OUTBOX#`) để bảo đảm người dùng chỉ nhận được các bản ghi video thật sự.

---

## 2. Cơ Chế Ghép Tiền Tố CDN / Media Base URL Cho Video Assets

### Bối cảnh
Khi video chuyển mã hoàn tất (`status = COMPLETED`), Worker lưu trữ cấu trúc các tệp tin xuất bản trong trường `assets` của `VideoItem` dưới dạng đường dẫn tương đối, ví dụ:
- `masterPlaylist`: `prj_xxx/vid_yyy/hls/master.m3u8`
- `hls360`: `prj_xxx/vid_yyy/hls/360p/index.m3u8`
- `hls720`: `prj_xxx/vid_yyy/hls/720p/index.m3u8`
- `thumbnail`: `prj_xxx/vid_yyy/thumb/default.jpg`

Hợp đồng OpenAPI quy định client phải nhận được đường dẫn đầy đủ đã ghép tiền tố máy chủ truyền thông `TVC_MEDIA_BASE_URL` (ví dụ `/media/prj_xxx/...` hoặc `https://cdn.transvidcloud.io/prj_xxx/...`).

### Giải pháp Thiết kế: `VideoMapper` & `TvcProperties`
1. Đọc cấu hình `tvcProperties.getMedia().getBaseUrl()` (mặc định cấu hình trong `application.yml` là `/media`).
2. Chuẩn hóa Base URL: loại bỏ dấu gạch chéo ở cuối (trailing slash) nếu có để tránh sinh ra đường dẫn có `//`.
3. Trong `VideoMapper`:
   - Nếu `status != VideoStatus.COMPLETED` hoặc `videoItem.getAssets() == null`: gán `assets = null`.
   - Nếu `status == VideoStatus.COMPLETED`:
     - `masterPlaylistUrl = baseUrl + "/" + relativeMaster`
     - `hls360Url = baseUrl + "/" + relative360`
     - `hls720Url = baseUrl + "/" + relative720`
     - `thumbnailUrl = baseUrl + "/" + relativeThumb`

---

## 3. Cơ Chế Bảo Vệ Xóa (Conflict Guard) & Fast Delete

### Bối cảnh
- Khi video đang trong tiến trình xử lý chuyển mã (`status = PROCESSING`): Worker đang chạy container Docker / FFmpeg phân đoạn video và ghi vào S3 output. Nếu client xóa video tại thời điểm này sẽ dẫn đến lỗi bất định, mồ côi tài nguyên và xung đột trạng thái.
- Hợp đồng OpenAPI yêu cầu: Từ chối xóa với `409 Conflict` nếu video ở trạng thái `PROCESSING`.
- Đối với các trạng thái khác (`UPLOADING`, `PENDING`, `COMPLETED`, `FAILED_*`): Cho phép xóa thành công và phản hồi HTTP 204 No Content tức thì.

### Giải pháp Thiết kế
1. **Kiểm tra trạng thái trước khi xóa**:
   ```java
   if (VideoStatus.PROCESSING.name().equals(video.getStatus())) {
       log.warn("Từ chối xóa: video {} đang trong tiến trình PROCESSING", videoId);
       throw new AppException(ErrorCode.INVALID_STATE_TRANSITION); // Ánh xạ HTTP 409 Conflict
   }
   ```
2. **Cơ chế Fast Delete (Theo quyết định Clarify Q1)**:
   - Xóa bản ghi `VideoItem` trong DynamoDB ngay lập tức (`videoRepository.deleteById(videoId)`).
   - Xóa đồng thời bản ghi `OutboxItem` tương ứng (`OUTBOX#videoId`) nếu còn tồn tại để dọn sạch khóa Single-Table.
   - Trả về HTTP 204 No Content trong thời gian < 100ms.
   - Các file vật lý trên S3 được dọn dẹp bất đồng bộ hoặc thông qua chính sách S3 Lifecycle Rules (ví dụ tự động thu hồi sau 30 ngày đối với thư mục tạm).

---

## 4. Phân Quyền Đa Người Thuê (Dual Security Enforcement)

Cả 3 endpoints đều tái sử dụng cơ chế thẩm định quyền sở hữu đã được kiểm chứng ở F-006 và F-007:
1. `GET /api/projects/{projectId}/videos`:
   - Gọi `validateProjectAccess(projectId, authentication)`.
   - JWT User: Phải là `ownerId` của Project.
   - API Key: `apiKeyPrincipal.projectId()` phải trùng khớp `projectId`.
2. `GET /api/videos/{videoId}` & `DELETE /api/videos/{videoId}`:
   - Tra cứu `VideoItem` từ `videoId`. Nếu không tìm thấy hoặc ID có tiền tố `OUTBOX#` ➔ Ném ngay `RESOURCE_NOT_FOUND` (404).
   - Gọi `validateProjectAccess(video.getProjectId(), authentication)`.
   - Nếu vi phạm quyền sở hữu ➔ Ném ngay `ACCESS_DENIED` (403).
