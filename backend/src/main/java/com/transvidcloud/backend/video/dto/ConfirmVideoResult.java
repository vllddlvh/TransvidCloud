package com.transvidcloud.backend.video.dto;

/**
 * Kết quả nội bộ từ VideoService trả về cho VideoController khi xác nhận video:
 * - video: DTO VideoResponse chứa thông tin mới nhất
 * - newlyConfirmed: true nếu là lần xác nhận đầu tiên (chuyển từ UPLOADING -> PENDING, trả 202 Accepted);
 *                   false nếu là gọi lặp idempotent (đã ở PENDING/PROCESSING/COMPLETED, trả 200 OK).
 */
public record ConfirmVideoResult(
        VideoResponse video,
        boolean newlyConfirmed
) {}
