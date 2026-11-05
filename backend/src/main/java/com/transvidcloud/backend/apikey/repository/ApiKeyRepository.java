package com.transvidcloud.backend.apikey.repository;

import com.transvidcloud.backend.apikey.entity.ApiKeyItem;
import com.transvidcloud.backend.common.config.TvcProperties;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

/**
 * Tầng Repository tương tác với bảng DynamoDB tvc-{env}-apikeys.
 * Hỗ trợ lưu trữ, tìm kiếm theo keyHash, truy vấn theo GSI project-index, đếm số lượng key và xóa cứng.
 */
@Slf4j
@Repository
public class ApiKeyRepository {

    public static final String GSI_PROJECT_INDEX = "project-index";

    private final DynamoDbTable<ApiKeyItem> apiKeyTable;

    public ApiKeyRepository(DynamoDbEnhancedClient enhancedClient, TvcProperties tvcProperties) {
        String tableName = tvcProperties.getAws().getDynamodb().getTablePrefix() + "apikeys";
        this.apiKeyTable = enhancedClient.table(tableName, TableSchema.fromBean(ApiKeyItem.class));
    }

    /**
     * Lưu bản ghi ApiKeyItem vào bảng DynamoDB.
     */
    public void save(ApiKeyItem item) {
        apiKeyTable.putItem(item);
    }

    /**
     * Tìm kiếm bản ghi API Key theo Partition Key (keyHash).
     */
    public Optional<ApiKeyItem> findByKeyHash(String keyHash) {
        if (keyHash == null || keyHash.isBlank()) {
            return Optional.empty();
        }
        ApiKeyItem item = apiKeyTable.getItem(Key.builder().partitionValue(keyHash).build());
        return Optional.ofNullable(item);
    }

    /**
     * Truy vấn toàn bộ các API Key thuộc một Project thông qua GSI project-index.
     */
    public List<ApiKeyItem> findByProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return List.of();
        }
        DynamoDbIndex<ApiKeyItem> projectIndex = apiKeyTable.index(GSI_PROJECT_INDEX);
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(projectId).build()
        );
        return projectIndex.query(QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .build())
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    /**
     * Đếm số lượng API Key hiện tại của Project.
     */
    public int countByProjectId(String projectId) {
        return findByProjectId(projectId).size();
    }

    /**
     * Xóa vật lý bản ghi API Key theo keyHash.
     */
    public void deleteByKeyHash(String keyHash) {
        if (keyHash == null || keyHash.isBlank()) {
            return;
        }
        apiKeyTable.deleteItem(Key.builder().partitionValue(keyHash).build());
    }
}
