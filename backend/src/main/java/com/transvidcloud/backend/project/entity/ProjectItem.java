package com.transvidcloud.backend.project.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

/**
 * Bản ghi lưu trữ trên bảng DynamoDB tvc-{env}-projects.
 * Hỗ trợ tra cứu theo projectId (khóa chính) và truy vấn theo owner-index (ownerId, createdAt).
 */
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class ProjectItem {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    private String projectId;
    private String ownerId;
    private String name;
    private String description;
    private String webhookUrl;
    private String status;
    private String createdAt;
    private String updatedAt;
    private String deletedAt;

    @DynamoDbPartitionKey
    public String getProjectId() {
        return projectId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "owner-index")
    public String getOwnerId() {
        return ownerId;
    }

    @DynamoDbSecondarySortKey(indexNames = "owner-index")
    public String getCreatedAt() {
        return createdAt;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getStatus() {
        return status;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public String getDeletedAt() {
        return deletedAt;
    }
}
