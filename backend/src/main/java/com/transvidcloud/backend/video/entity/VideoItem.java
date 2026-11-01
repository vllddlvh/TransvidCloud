package com.transvidcloud.backend.video.entity;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

/**
 * Bản ghi lưu trữ trên bảng DynamoDB tvc-{env}-videos.
 * Partition Key: videoId.
 * GSI project-index: Partition Key projectId, Sort Key createdAt.
 */
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class VideoItem {

    public static final String GSI_PROJECT_INDEX = "project-index";

    private String videoId;
    private String projectId;
    private String title;
    private Long sizeBytes;
    private String contentType;
    private String status;
    private Integer attemptCount;
    private String inputKey;
    private Map<String, String> assets;
    private String errorCode;
    private String createdAt;
    private String updatedAt;
    private String startedAt;
    private String completedAt;

    @DynamoDbPartitionKey
    public String getVideoId() {
        return videoId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = GSI_PROJECT_INDEX)
    public String getProjectId() {
        return projectId;
    }

    @DynamoDbSecondarySortKey(indexNames = GSI_PROJECT_INDEX)
    public String getCreatedAt() {
        return createdAt;
    }

    public String getTitle() {
        return title;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public String getStatus() {
        return status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public String getInputKey() {
        return inputKey;
    }

    public Map<String, String> getAssets() {
        return assets;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }
}
