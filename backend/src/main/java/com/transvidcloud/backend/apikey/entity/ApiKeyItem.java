package com.transvidcloud.backend.apikey.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

/**
 * Bản ghi lưu trữ trên bảng DynamoDB tvc-{env}-apikeys.
 * Hỗ trợ tra cứu nhanh qua keyHash (Partition Key) và truy vấn danh sách qua GSI project-index (projectId).
 */
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class ApiKeyItem {

    private String keyHash;
    private String projectId;
    private String name;
    private String keyPrefix;
    private String createdAt;

    @DynamoDbPartitionKey
    public String getKeyHash() {
        return keyHash;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "project-index")
    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
