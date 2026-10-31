package com.transvidcloud.backend.video.mapper;

import com.transvidcloud.backend.video.dto.VideoResponse;
import com.transvidcloud.backend.video.entity.VideoItem;
import com.transvidcloud.backend.video.entity.VideoStatus;
import java.time.Instant;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper chuyển đổi giữa VideoItem và VideoResponse DTO.
 */
@Mapper(componentModel = "spring")
public interface VideoMapper {

    VideoResponse toVideoResponse(VideoItem videoItem);

    List<VideoResponse> toVideoResponseList(List<VideoItem> items);

    default Instant mapStringToInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    default VideoStatus mapStringToVideoStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return VideoStatus.valueOf(value);
    }
}
