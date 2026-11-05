package com.transvidcloud.backend.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cấu trúc tài nguyên phát lại của Video (HLS playlists và thumbnail).
 * Các đường dẫn đã được ghép tiền tố cấu hình TVC_MEDIA_BASE_URL.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoAssets(
        String masterPlaylistUrl,
        String hls360Url,
        String hls720Url,
        String thumbnailUrl
) {}
