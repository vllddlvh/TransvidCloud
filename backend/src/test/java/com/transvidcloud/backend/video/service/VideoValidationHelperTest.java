package com.transvidcloud.backend.video.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VideoValidationHelperTest {

    @Test
    @DisplayName("generateVideoId: phải sinh chuỗi có tiền tố vid_ và đúng 16 ký tự tổng cộng (vid_ + 12 hex)")
    void testGenerateVideoIdFormat() {
        String id1 = VideoValidationHelper.generateVideoId();
        String id2 = VideoValidationHelper.generateVideoId();

        assertNotNull(id1);
        assertTrue(id1.startsWith("vid_"));
        assertEquals(16, id1.length());
        assertTrue(id1.matches("^vid_[0-9a-f]{12}$"));

        // Tính ngẫu nhiên không trùng lặp
        assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("extractExtension: trích xuất phần mở rộng chuẩn hóa chữ thường và xử lý tên tệp nhiều dấu chấm")
    void testExtractExtension() {
        assertEquals("mp4", VideoValidationHelper.extractExtension("sample.mp4"));
        assertEquals("mp4", VideoValidationHelper.extractExtension("SAMPLE.MP4"));
        assertEquals("mov", VideoValidationHelper.extractExtension("my.vacation.video.MOV"));
        assertEquals("mkv", VideoValidationHelper.extractExtension("archive.v1.0.final.mkv"));
        assertEquals("webm", VideoValidationHelper.extractExtension("test.WEBM"));

        // Trường hợp không có đuôi hợp lệ
        assertEquals("", VideoValidationHelper.extractExtension("noextension"));
        assertEquals("", VideoValidationHelper.extractExtension("endingdot."));
        assertEquals("", VideoValidationHelper.extractExtension(""));
        assertEquals("", VideoValidationHelper.extractExtension(null));
    }

    @Test
    @DisplayName("isExtensionAllowed: chỉ chấp nhận mp4, mov, mkv, webm không phân biệt hoa thường")
    void testIsExtensionAllowed() {
        assertTrue(VideoValidationHelper.isExtensionAllowed("mp4"));
        assertTrue(VideoValidationHelper.isExtensionAllowed("MP4"));
        assertTrue(VideoValidationHelper.isExtensionAllowed("mov"));
        assertTrue(VideoValidationHelper.isExtensionAllowed("mkv"));
        assertTrue(VideoValidationHelper.isExtensionAllowed("webm"));

        assertFalse(VideoValidationHelper.isExtensionAllowed("avi"));
        assertFalse(VideoValidationHelper.isExtensionAllowed("flv"));
        assertFalse(VideoValidationHelper.isExtensionAllowed("png"));
        assertFalse(VideoValidationHelper.isExtensionAllowed(""));
        assertFalse(VideoValidationHelper.isExtensionAllowed(null));
    }

    @Test
    @DisplayName("isContentTypeAllowed: kiểm tra MIME type cho phép")
    void testIsContentTypeAllowed() {
        List<String> allowed = List.of("video/mp4", "video/quicktime", "video/x-matroska", "video/webm");

        assertTrue(VideoValidationHelper.isContentTypeAllowed("video/mp4", allowed));
        assertTrue(VideoValidationHelper.isContentTypeAllowed("VIDEO/MP4", allowed));
        assertTrue(VideoValidationHelper.isContentTypeAllowed("video/quicktime", allowed));

        assertFalse(VideoValidationHelper.isContentTypeAllowed("video/x-msvideo", allowed));
        assertFalse(VideoValidationHelper.isContentTypeAllowed("application/json", allowed));
        assertFalse(VideoValidationHelper.isContentTypeAllowed("", allowed));
        assertFalse(VideoValidationHelper.isContentTypeAllowed(null, allowed));
    }

    @Test
    @DisplayName("validateSizeBytes: ném UPLOAD_LIMIT_EXCEEDED khi vượt quá maxBytes")
    void testValidateSizeBytes() {
        long maxBytes = 209715200L; // 200MB

        // Không ném lỗi khi <= 200MB
        VideoValidationHelper.validateSizeBytes(100L, maxBytes);
        VideoValidationHelper.validateSizeBytes(maxBytes, maxBytes);

        // Ném lỗi 413 khi vượt quá 200MB
        AppException ex = assertThrows(AppException.class, () ->
                VideoValidationHelper.validateSizeBytes(maxBytes + 1, maxBytes));
        assertEquals(ErrorCode.UPLOAD_LIMIT_EXCEEDED, ex.getErrorCode());
    }
}
