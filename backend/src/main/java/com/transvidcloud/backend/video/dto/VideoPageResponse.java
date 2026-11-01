package com.transvidcloud.backend.video.dto;

import java.util.List;

/**
 * Phản hồi danh sách Video kèm phân trang con trỏ khớp với hợp đồng VideoPageResponse.
 */
public record VideoPageResponse(
        List<VideoResponse> items,
        String nextCursor,
        boolean hasMore
) {}
