package com.transvidcloud.backend.video.mapper;

import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.video.dto.VideoAssets;
import com.transvidcloud.backend.video.dto.VideoResponse;
import com.transvidcloud.backend.video.entity.VideoItem;
import com.transvidcloud.backend.video.entity.VideoStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * MapStruct mapper chuyển đổi giữa VideoItem và VideoResponse DTO.
 * Tự động ghép tiền tố TVC_MEDIA_BASE_URL cho VideoAssets khi video ở trạng thái COMPLETED.
 */
@Mapper(componentModel = "spring")
public abstract class VideoMapper {

    @Autowired
    protected TvcProperties tvcProperties;

    public void setTvcProperties(TvcProperties tvcProperties) {
        this.tvcProperties = tvcProperties;
    }

    @Mapping(target = "assets", expression = "java(mapAssets(videoItem))")
    public abstract VideoResponse toVideoResponse(VideoItem videoItem);

    public abstract List<VideoResponse> toVideoResponseList(List<VideoItem> items);

    public Instant mapStringToInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    public VideoStatus mapStringToVideoStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return VideoStatus.valueOf(value);
    }

    protected VideoAssets mapAssets(VideoItem videoItem) {
        if (videoItem == null || !VideoStatus.COMPLETED.name().equals(videoItem.getStatus()) || videoItem.getAssets() == null) {
            return null;
        }

        Map<String, String> rawAssets = videoItem.getAssets();
        String baseUrl = (tvcProperties != null && tvcProperties.getMedia() != null)
                ? tvcProperties.getMedia().getBaseUrl()
                : "/media";

        String master = getAssetValue(rawAssets, "masterPlaylistUrl", "masterPlaylist", "hls");
        String hls360 = getAssetValue(rawAssets, "hls360Url", "hls360");
        String hls720 = getAssetValue(rawAssets, "hls720Url", "hls720");
        String thumb = getAssetValue(rawAssets, "thumbnailUrl", "thumbnail");

        return new VideoAssets(
                prependBaseUrl(baseUrl, master),
                prependBaseUrl(baseUrl, hls360),
                prependBaseUrl(baseUrl, hls720),
                prependBaseUrl(baseUrl, thumb)
        );
    }

    private String getAssetValue(Map<String, String> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private String prependBaseUrl(String baseUrl, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath;
        }
        String cleanBase = (baseUrl != null) ? baseUrl.replaceAll("/+$", "") : "";
        if (!cleanBase.isEmpty() && relativePath.startsWith(cleanBase)) {
            return relativePath;
        }
        String cleanPath = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        return cleanBase + cleanPath;
    }
}
