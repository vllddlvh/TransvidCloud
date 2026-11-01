package com.transvidcloud.backend.video.repository;

import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.video.entity.OutboxItem;
import java.time.Instant;
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
 * Tầng Repository quản lý các bản ghi Transactional Outbox trên bảng tvc-{env}-videos.
 */
@Slf4j
@Repository
public class OutboxRepository {

    private final DynamoDbTable<OutboxItem> outboxTable;

    public OutboxRepository(DynamoDbEnhancedClient enhancedClient, TvcProperties tvcProperties) {
        String tableName = tvcProperties.getAws().getDynamodb().getTablePrefix() + "videos";
        this.outboxTable = enhancedClient.table(tableName, TableSchema.fromBean(OutboxItem.class));
    }

    /**
     * Lưu hoặc cập nhật bản ghi OutboxItem.
     */
    public void save(OutboxItem item) {
        outboxTable.putItem(item);
    }

    /**
     * Tìm bản ghi OutboxItem theo targetVideoId (PK: OUTBOX#{targetVideoId}).
     */
    public Optional<OutboxItem> findByTargetVideoId(String targetVideoId) {
        String pk = OutboxItem.buildPk(targetVideoId);
        OutboxItem item = outboxTable.getItem(Key.builder().partitionValue(pk).build());
        return Optional.ofNullable(item);
    }

    /**
     * Truy vấn danh sách sự kiện Outbox đang ở trạng thái PENDING qua GSI project-index.
     * Sử dụng Query conditional (PK = "OUTBOX#PENDING"), hoàn toàn không dùng DynamoDB Scan.
     * Sắp xếp theo createdAt tăng dần (scanIndexForward = true) để gửi theo thứ tự FIFO.
     *
     * @param limit Số lượng bản ghi tối đa trên một đợt quét
     * @return Danh sách OutboxItem chờ phát hành
     */
    public List<OutboxItem> findPendingOutboxEvents(int limit) {
        DynamoDbIndex<OutboxItem> projectIndex = outboxTable.index(OutboxItem.GSI_PROJECT_INDEX);
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(OutboxItem.GSI_PARTITION_PENDING).build()
        );

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(true)
                .limit(limit)
                .build();

        return projectIndex.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .limit(limit)
                .toList();
    }

    /**
     * Đánh dấu sự kiện Outbox đã phát hành thành công lên SQS.
     * Chuyển GSI PK sang "OUTBOX#PUBLISHED" để loại khỏi danh sách chờ gửi.
     */
    public void markAsPublished(String targetVideoId, String publishedAt) {
        findByTargetVideoId(targetVideoId).ifPresent(item -> {
            item.setProjectId(OutboxItem.GSI_PARTITION_PUBLISHED);
            item.setOutboxStatus(OutboxItem.STATUS_PUBLISHED);
            item.setPublishedAt(publishedAt);
            item.setUpdatedAt(Instant.now().toString());
            save(item);
            log.debug("Đánh dấu Outbox PUBLISHED thành công cho video: {}", targetVideoId);
        });
    }

    /**
     * Ghi nhận lần gửi SQS thất bại để hỗ trợ quan sát và retry.
     */
    public void recordFailedAttempt(String targetVideoId, String errorMessage) {
        findByTargetVideoId(targetVideoId).ifPresent(item -> {
            int currentAttempts = item.getAttemptCount() != null ? item.getAttemptCount() : 0;
            item.setAttemptCount(currentAttempts + 1);
            item.setLastError(errorMessage);
            item.setUpdatedAt(Instant.now().toString());
            save(item);
            log.warn("Ghi nhận Outbox gửi SQS thất bại (lần {}): videoId={}, error={}",
                    item.getAttemptCount(), targetVideoId, errorMessage);
        });
    }

    public DynamoDbTable<OutboxItem> getTable() {
        return outboxTable;
    }
}
