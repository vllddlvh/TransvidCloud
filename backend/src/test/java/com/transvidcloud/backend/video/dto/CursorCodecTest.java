package com.transvidcloud.backend.video.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("encode và decode thành công với VideoCursor hợp lệ")
    void encodeAndDecode_Success() {
        VideoCursor cursor = new VideoCursor("prj_123", "vid_456", "2026-11-01T10:00:00Z");

        String encoded = CursorCodec.encode(objectMapper, cursor);
        assertThat(encoded).isNotNull();
        assertThat(encoded).doesNotContain("=").doesNotContain("+").doesNotContain("/");

        VideoCursor decoded = CursorCodec.decode(objectMapper, encoded, "prj_123");
        assertThat(decoded).isNotNull();
        assertThat(decoded.projectId()).isEqualTo("prj_123");
        assertThat(decoded.videoId()).isEqualTo("vid_456");
        assertThat(decoded.createdAt()).isEqualTo("2026-11-01T10:00:00Z");
    }

    @Test
    @DisplayName("decode trả về null khi cursor null hoặc khoảng trắng")
    void decode_NullOrBlank_ReturnsNull() {
        assertThat(CursorCodec.decode(objectMapper, null, "prj_123")).isNull();
        assertThat(CursorCodec.decode(objectMapper, "", "prj_123")).isNull();
        assertThat(CursorCodec.decode(objectMapper, "   ", "prj_123")).isNull();
    }

    @Test
    @DisplayName("decode ném INVALID_CURSOR khi phát hiện tráo đổi projectId")
    void decode_TamperedProjectId_ThrowsInvalidCursor() {
        VideoCursor cursor = new VideoCursor("prj_tenantA", "vid_001", "2026-11-01T10:00:00Z");
        String encoded = CursorCodec.encode(objectMapper, cursor);

        assertThatThrownBy(() -> CursorCodec.decode(objectMapper, encoded, "prj_tenantB"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_CURSOR));
    }

    @Test
    @DisplayName("decode ném INVALID_CURSOR khi chuỗi Base64 không hợp lệ")
    void decode_MalformedBase64_ThrowsInvalidCursor() {
        assertThatThrownBy(() -> CursorCodec.decode(objectMapper, "not-valid-base64!@#$%", "prj_123"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_CURSOR));
    }

    @Test
    @DisplayName("decode ném INVALID_CURSOR khi JSON thiếu trường bắt buộc")
    void decode_MissingRequiredFields_ThrowsInvalidCursor() {
        String invalidJson = "{\"projectId\":\"prj_123\"}"; // thiếu videoId và createdAt
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(invalidJson.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> CursorCodec.decode(objectMapper, encoded, "prj_123"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_CURSOR));
    }

    @Test
    @DisplayName("encodeFromLastEvaluatedKey tạo cursor chính xác và toExclusiveStartKey chuyển đổi ngược đúng")
    void encodeFromLastEvaluatedKey_And_toExclusiveStartKey() {
        Map<String, AttributeValue> lastEvaluatedKey = Map.of(
                "projectId", AttributeValue.builder().s("prj_abc").build(),
                "videoId", AttributeValue.builder().s("vid_xyz").build(),
                "createdAt", AttributeValue.builder().s("2026-11-01T12:00:00Z").build()
        );

        String cursorString = CursorCodec.encodeFromLastEvaluatedKey(objectMapper, lastEvaluatedKey);
        assertThat(cursorString).isNotNull();

        VideoCursor cursor = CursorCodec.decode(objectMapper, cursorString, "prj_abc");
        assertThat(cursor).isNotNull();

        Map<String, AttributeValue> startKey = CursorCodec.toExclusiveStartKey(cursor);
        assertThat(startKey).isNotNull();
        assertThat(startKey.get("projectId").s()).isEqualTo("prj_abc");
        assertThat(startKey.get("videoId").s()).isEqualTo("vid_xyz");
        assertThat(startKey.get("createdAt").s()).isEqualTo("2026-11-01T12:00:00Z");
    }
}
