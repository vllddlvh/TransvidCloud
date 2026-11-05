package com.transvidcloud.backend.video.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Bộ tiện ích mã hóa và giải mã an toàn cho con trỏ phân trang (Cursor)
 * trên DynamoDB GSI project-index.
 */
@Slf4j
public final class CursorCodec {

    private CursorCodec() {}

    /**
     * Mã hóa VideoCursor thành chuỗi Base64URL không padding.
     */
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

    /**
     * Mã hóa LastEvaluatedKey của DynamoDB thành chuỗi Base64URL cursor.
     */
    public static String encodeFromLastEvaluatedKey(ObjectMapper objectMapper, Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        AttributeValue projectVal = lastEvaluatedKey.get("projectId");
        AttributeValue videoVal = lastEvaluatedKey.get("videoId");
        AttributeValue createdVal = lastEvaluatedKey.get("createdAt");

        if (projectVal == null || videoVal == null || createdVal == null) {
            return null;
        }

        VideoCursor cursor = new VideoCursor(projectVal.s(), videoVal.s(), createdVal.s());
        return encode(objectMapper, cursor);
    }

    /**
     * Giải mã chuỗi Base64URL cursor và thẩm định an ninh đối chiếu với expectedProjectId.
     */
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
            // Thẩm định an ninh chống tráo đổi cursor giữa các project
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

    /**
     * Chuyển đổi VideoCursor sang ExclusiveStartKey dạng Map<String, AttributeValue> của DynamoDB.
     */
    public static Map<String, AttributeValue> toExclusiveStartKey(VideoCursor cursor) {
        if (cursor == null) {
            return null;
        }
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("projectId", AttributeValue.builder().s(cursor.projectId()).build());
        key.put("videoId", AttributeValue.builder().s(cursor.videoId()).build());
        key.put("createdAt", AttributeValue.builder().s(cursor.createdAt()).build());
        return key;
    }
}
