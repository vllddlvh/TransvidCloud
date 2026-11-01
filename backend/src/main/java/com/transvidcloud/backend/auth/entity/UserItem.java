package com.transvidcloud.backend.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

/**
 * Bản ghi lưu trữ trên bảng DynamoDB tvc-{env}-users.
 * Hỗ trợ cả bản ghi tài khoản chính (itemType = USER) và bản ghi khóa email duy nhất (itemType = EMAIL_LOCK).
 */
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "passwordHash")
@DynamoDbBean
public class UserItem {

    public static final String TYPE_USER = "USER";
    public static final String TYPE_EMAIL_LOCK = "EMAIL_LOCK";

    private String userId;
    private String email;
    private String passwordHash;
    private String fullName;
    private String createdAt;
    private String itemType;
    private String targetUserId;

    @DynamoDbPartitionKey
    public String getUserId() {
        return userId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "email-index")
    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getItemType() {
        return itemType;
    }

    public String getTargetUserId() {
        return targetUserId;
    }
}
