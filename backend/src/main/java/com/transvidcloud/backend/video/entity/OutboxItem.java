package com.transvidcloud.backend.video.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

/**
 * Bản ghi Transactional Outbox lưu trữ trên cùng bảng DynamoDB tvc-{env}-videos (Single-Table design).
 * - Partition Key (videoId): "OUTBOX#" + targetVideoId (đảm bảo tính duy nhất và khóa nguyên tử theo video).
 * - GSI project-index Partition Key (projectId): "OUTBOX#PENDING" hoặc "OUTBOX#PUBLISHED".
 * - GSI project-index Sort Key (createdAt): thời điểm tạo sự kiện (ISO-8601 UTC) để quét FIFO.
 */
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class OutboxItem {

    public static final String GSI_PROJECT_INDEX = "project-index";
    public static final String PREFIX_OUTBOX = "OUTBOX#";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String GSI_PARTITION_PENDING = "OUTBOX#PENDING";
    public static final String GSI_PARTITION_PUBLISHED = "OUTBOX#PUBLISHED";

    private String videoId;        // PK: OUTBOX#{targetVideoId}
    private String projectId;      // GSI PK: OUTBOX#PENDING hoặc OUTBOX#PUBLISHED
    private String createdAt;      // GSI SK: ISO-8601 timestamp
    private String jobId;          // Định danh job cố định
    private String targetVideoId;  // videoId gốc
    private String targetProjectId;// projectId gốc
    private String inputKey;       // Khóa S3 của video gốc
    private String payload;        // Nội dung JSON của SqsJobMessage
    private String outboxStatus;   // PENDING / PUBLISHED
    private Integer attemptCount;  // Số lần thử gửi SQS
    private String lastError;      // Lỗi lần gửi gần nhất (nếu có)
    private String updatedAt;      // Thời điểm cập nhật
    private String publishedAt;    // Thời điểm gửi SQS thành công

    public static String buildPk(String targetVideoId) {
        return PREFIX_OUTBOX + targetVideoId;
    }

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

    public String getJobId() {
        return jobId;
    }

    public String getTargetVideoId() {
        return targetVideoId;
    }

    public String getTargetProjectId() {
        return targetProjectId;
    }

    public String getInputKey() {
        return inputKey;
    }

    public String getPayload() {
        return payload;
    }

    public String getOutboxStatus() {
        return outboxStatus;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public String getPublishedAt() {
        return publishedAt;
    }
}
