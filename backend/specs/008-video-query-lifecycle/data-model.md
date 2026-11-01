# Data Model Design: F-008 — Truy Vấn Danh Sách (Cursor Pagination), Chi Tiết, Assets & Xóa Video

---

## 1. DTO & Records Mới

### 1.1. `VideoCursor` (Cấu trúc con trỏ phân trang)
Đại diện cho tọa độ khóa dừng của DynamoDB GSI `project-index`, dùng để serialize sang Base64URL.

```java
package com.transvidcloud.backend.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Tọa độ con trỏ phân trang trên GSI project-index của bảng tvc-{env}-videos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoCursor(
        String projectId,
        String videoId,
        String createdAt
) {}
```

---

### 1.2. `CursorCodec` (Bộ mã hóa/giải mã an toàn)
Thực thi mã hóa Base64URL không padding và thẩm định chéo `projectId` để chống tráo đổi cursor giữa các tenant.

```java
package com.transvidcloud.backend.video.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CursorCodec {

    private CursorCodec() {}

    public static String encode(ObjectMapper objectMapper, VideoCursor cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(cursor);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.error("Lỗi khi mã hóa cursor: {}", ex.getMessage());
            return null;
        }
    }

    public static VideoCursor decode(ObjectMapper objectMapper, String cursorString, String expectedProjectId) {
        if (cursorString == null || cursorString.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursorString.trim());
            VideoCursor cursor = objectMapper.readValue(bytes, VideoCursor.class);
            if (cursor == null || cursor.projectId() == null || cursor.videoId() == null || cursor.createdAt() == null) {
                throw new AppException(ErrorCode.INVALID_CURSOR);
            }
            // Thẩm định an ninh: cursor phải thuộc đúng project đang truy vấn
            if (expectedProjectId != null && !expectedProjectId.equals(cursor.projectId())) {
                log.warn("Phát hiện tráo đổi cursor: expected={}, actual={}", expectedProjectId, cursor.projectId());
                throw new AppException(ErrorCode.INVALID_CURSOR);
            }
            return cursor;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Cursor không hợp lệ: {}", cursorString);
            throw new AppException(ErrorCode.INVALID_CURSOR);
        }
    }
}
```

---

### 1.3. `VideoAssets` (Cấu trúc tài nguyên phát lại)
Phản hồi các URL phát HLS và thumbnail đã ghép tiền tố máy chủ truyền thông `TVC_MEDIA_BASE_URL`.

```java
package com.transvidcloud.backend.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoAssets(
        String masterPlaylistUrl,
        String hls360Url,
        String hls720Url,
        String thumbnailUrl
) {}
```

---

### 1.4. `VideoPageResponse` (Phản hồi phân trang)
Đóng gói danh sách video trả về cho client theo hợp đồng `VideoPageResponse` trong OpenAPI.

```java
package com.transvidcloud.backend.video.dto;

import java.util.List;

public record VideoPageResponse(
        List<VideoResponse> items,
        String nextCursor,
        boolean hasMore
) {}
```

---

### 1.5. Cập nhật `VideoResponse`
Bổ sung trường `assets` (kiểu `VideoAssets`) và các mốc thời gian `startedAt`, `completedAt`:

```java
public record VideoResponse(
        String videoId,
        String projectId,
        String title,
        VideoStatus status,
        Long sizeBytes,
        String contentType,
        Integer attemptCount,
        String errorCode,
        VideoAssets assets,
        String createdAt,
        String updatedAt,
        String startedAt,
        String completedAt
) {}
```

---

## 2. Thiết Kế Mở Rộng `VideoRepository`

Bổ sung 2 phương thức trọng yếu vào `VideoRepository`:

### 2.1. `findVideosByProjectWithPagination`
- **Mục đích**: Query GSI `project-index` có điều kiện Partition Key `projectId = :projectId`, sắp xếp `ScanIndexForward = false` (mới nhất lên đầu), giới hạn `limit`, nạp `exclusiveStartKey` từ cursor nếu có.
- **Output**: Trả về một DTO nội bộ `PagedResult<VideoItem>` chứa danh sách items và `lastEvaluatedKey`.

```java
public record PagedVideoResult(
        List<VideoItem> items,
        Map<String, AttributeValue> lastEvaluatedKey
) {}
```

### 2.2. `deleteVideoWithOutbox`
- **Mục đích**: Thực thi xóa bản ghi `VideoItem` (`videoId`) và đồng thời dọn dẹp bản ghi `OutboxItem` (`OUTBOX#{videoId}`) nếu có trên cùng bảng DynamoDB.
