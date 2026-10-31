package com.transvidcloud.backend.video.service;

import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Tiện ích hỗ trợ thẩm định dữ liệu video và sinh định danh an toàn.
 */
public final class VideoValidationHelper {

    public static final String VIDEO_ID_PREFIX = "vid_";
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp4", "mov", "mkv", "webm");

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private VideoValidationHelper() {
        // Utility class
    }

    /**
     * Sinh videoId ngẫu nhiên an toàn theo quy ước: vid_ + 12 ký tự hex.
     */
    public static String generateVideoId() {
        byte[] bytes = new byte[6]; // 6 bytes = 12 hex characters
        SECURE_RANDOM.nextBytes(bytes);
        return VIDEO_ID_PREFIX + HexFormat.of().formatHex(bytes);
    }

    /**
     * Trích xuất phần mở rộng tệp tin sau dấu chấm cuối cùng và chuẩn hóa về chữ thường (lowercase).
     *
     * @param filename Tên tệp tin gốc của client
     * @return Phần mở rộng chữ thường hoặc chuỗi rỗng nếu không có
     */
    public static String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1).trim().toLowerCase();
    }

    /**
     * Kiểm tra phần mở rộng tệp tin có thuộc danh mục được hỗ trợ (mp4, mov, mkv, webm).
     */
    public static boolean isExtensionAllowed(String ext) {
        if (ext == null || ext.isBlank()) {
            return false;
        }
        return ALLOWED_EXTENSIONS.contains(ext.toLowerCase());
    }

    /**
     * Kiểm tra loại nội dung contentType có thuộc danh mục cấu hình cho phép hay không.
     */
    public static boolean isContentTypeAllowed(String contentType, List<String> allowedTypes) {
        if (contentType == null || contentType.isBlank() || allowedTypes == null) {
            return false;
        }
        String normalized = contentType.trim().toLowerCase();
        return allowedTypes.stream().anyMatch(type -> type.equalsIgnoreCase(normalized));
    }

    /**
     * Thẩm định dung lượng file: nếu vượt quá maxBytes thì ném AppException(UPLOAD_LIMIT_EXCEEDED).
     */
    public static void validateSizeBytes(Long sizeBytes, long maxBytes) {
        if (sizeBytes != null && sizeBytes > maxBytes) {
            throw new AppException(ErrorCode.UPLOAD_LIMIT_EXCEEDED);
        }
    }
}
