package com.transvidcloud.backend.video.repository;

import com.transvidcloud.backend.video.entity.VideoItem;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Container chứa kết quả phân trang truy vấn từ DynamoDB VideoRepository.
 * Bao gồm danh sách VideoItem và lastEvaluatedKey của trang hiện tại.
 */
public record PagedVideoResult(
        List<VideoItem> items,
        Map<String, AttributeValue> lastEvaluatedKey
) {}
