package com.transvidcloud.backend.project.repository;

import com.transvidcloud.backend.common.config.TvcProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.Map;
import java.util.Set;

/**
 * Kiểm tra trạng thái an toàn của các video thuộc Project trước khi thực hiện xóa.
 * Sử dụng DynamoDbClient truy vấn trực tiếp GSI project-index trên bảng tvc-{env}-videos.
 */
@Slf4j
@Component
public class VideoSafetyChecker {

    public static final Set<String> ACTIVE_STATUSES = Set.of("UPLOADING", "PENDING", "PROCESSING");

    private final DynamoDbClient dynamoDbClient;
    private final String videosTableName;

    public VideoSafetyChecker(DynamoDbClient dynamoDbClient, TvcProperties tvcProperties) {
        this.dynamoDbClient = dynamoDbClient;
        this.videosTableName = tvcProperties.getAws().getDynamodb().getTablePrefix() + "videos";
    }

    /**
     * Kiểm tra xem Project có video nào đang trong trạng thái xử lý dở dang (UPLOADING, PENDING, PROCESSING) hay không.
     *
     * @param projectId Mã định danh dự án
     * @return true nếu có ít nhất 1 video chưa kết thúc; false nếu không có
     */
    public boolean hasActiveVideos(String projectId) {
        try {
            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(videosTableName)
                    .indexName("project-index")
                    .keyConditionExpression("projectId = :pId")
                    .filterExpression("#st IN (:s1, :s2, :s3)")
                    .expressionAttributeNames(Map.of("#st", "status"))
                    .expressionAttributeValues(Map.of(
                            ":pId", AttributeValue.builder().s(projectId).build(),
                            ":s1", AttributeValue.builder().s("UPLOADING").build(),
                            ":s2", AttributeValue.builder().s("PENDING").build(),
                            ":s3", AttributeValue.builder().s("PROCESSING").build()
                    ))
                    .limit(1)
                    .build();

            QueryResponse response = dynamoDbClient.query(queryRequest);
            boolean hasActive = response.hasItems() && !response.items().isEmpty();
            if (hasActive) {
                log.warn("Project {} còn video đang xử lý dở dang, không thể xóa", projectId);
            }
            return hasActive;
        } catch (Exception ex) {
            log.error("Lỗi khi kiểm tra active videos cho project {}: {}", projectId, ex.getMessage());
            // Nếu bảng videos chưa có dữ liệu hoặc gặp lỗi kết nối, throw hoặc trả về false tùy context
            throw ex;
        }
    }
}
