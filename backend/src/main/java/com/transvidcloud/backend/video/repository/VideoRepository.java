package com.transvidcloud.backend.video.repository;

import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.video.entity.OutboxItem;
import com.transvidcloud.backend.video.entity.VideoItem;
import com.transvidcloud.backend.video.entity.VideoStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Tầng Repository tương tác với bảng DynamoDB tvc-{env}-videos.
 */
@Slf4j
@Repository
public class VideoRepository {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<VideoItem> videoTable;
    private final DynamoDbTable<OutboxItem> outboxTable;

    public VideoRepository(DynamoDbEnhancedClient enhancedClient, TvcProperties tvcProperties) {
        this.enhancedClient = enhancedClient;
        String tableName = tvcProperties.getAws().getDynamodb().getTablePrefix() + "videos";
        this.videoTable = enhancedClient.table(tableName, TableSchema.fromBean(VideoItem.class));
        this.outboxTable = enhancedClient.table(tableName, TableSchema.fromBean(OutboxItem.class));
    }

    /**
     * Lưu bản ghi VideoItem mới hoặc cập nhật.
     */
    public void save(VideoItem item) {
        videoTable.putItem(item);
    }

    /**
     * Tìm kiếm Video theo videoId (Partition Key).
     * Loại bỏ các khóa phân vùng nội bộ của Outbox (OUTBOX#...).
     */
    public Optional<VideoItem> findByVideoId(String videoId) {
        if (videoId == null || videoId.startsWith(OutboxItem.PREFIX_OUTBOX)) {
            return Optional.empty();
        }
        VideoItem item = videoTable.getItem(Key.builder().partitionValue(videoId).build());
        return Optional.ofNullable(item);
    }

    /**
     * Truy vấn danh sách video trong Project qua GSI project-index.
     */
    public List<VideoItem> findByProjectId(String projectId) {
        DynamoDbIndex<VideoItem> projectIndex = videoTable.index(VideoItem.GSI_PROJECT_INDEX);
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(projectId).build()
        );

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false)
                .build();

        return projectIndex.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(item -> item.getVideoId() != null && !item.getVideoId().startsWith(OutboxItem.PREFIX_OUTBOX))
                .toList();
    }

    /**
     * Thực hiện giao dịch nguyên tử TransactWriteItems:
     * 1. Cập nhật VideoItem sang trạng thái PENDING với điều kiện trạng thái hiện tại là UPLOADING.
     * 2. Lưu bản ghi OutboxItem mới ở trạng thái PENDING với điều kiện attribute_not_exists.
     *
     * @param video  Bản ghi VideoItem đã chuyển trạng thái PENDING
     * @param outbox Bản ghi OutboxItem sự kiện chờ phát hành SQS
     * @throws software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException Nếu vi phạm điều kiện chuyển đổi
     */
    public void confirmVideoWithOutboxInTransaction(VideoItem video, OutboxItem outbox) {
        Map<String, String> expressionNames = Map.of("#st", "status");
        Map<String, AttributeValue> expressionValues = Map.of(
                ":uploading", AttributeValue.builder().s(VideoStatus.UPLOADING.name()).build()
        );

        Expression videoCondition = Expression.builder()
                .expression("attribute_exists(videoId) AND #st = :uploading")
                .expressionNames(expressionNames)
                .expressionValues(expressionValues)
                .build();

        Expression outboxCondition = Expression.builder()
                .expression("attribute_not_exists(videoId)")
                .build();

        enhancedClient.transactWriteItems(builder -> builder
                .addPutItem(videoTable, TransactPutItemEnhancedRequest.builder(VideoItem.class)
                        .item(video)
                        .conditionExpression(videoCondition)
                        .build())
                .addPutItem(outboxTable, TransactPutItemEnhancedRequest.builder(OutboxItem.class)
                        .item(outbox)
                        .conditionExpression(outboxCondition)
                        .build())
        );
        log.info("Thực thi TransactWriteItems thành công: videoId={}, outboxId={}", video.getVideoId(), outbox.getVideoId());
    }
}
