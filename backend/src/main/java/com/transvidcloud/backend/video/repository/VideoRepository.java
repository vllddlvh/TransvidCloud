package com.transvidcloud.backend.video.repository;

import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.video.entity.VideoItem;
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
 * Tầng Repository tương tác với bảng DynamoDB tvc-{env}-videos.
 */
@Slf4j
@Repository
public class VideoRepository {

    private final DynamoDbTable<VideoItem> videoTable;

    public VideoRepository(DynamoDbEnhancedClient enhancedClient, TvcProperties tvcProperties) {
        String tableName = tvcProperties.getAws().getDynamodb().getTablePrefix() + "videos";
        this.videoTable = enhancedClient.table(tableName, TableSchema.fromBean(VideoItem.class));
    }

    /**
     * Lưu bản ghi VideoItem mới hoặc cập nhật.
     */
    public void save(VideoItem item) {
        videoTable.putItem(item);
    }

    /**
     * Tìm kiếm Video theo videoId (Partition Key).
     */
    public Optional<VideoItem> findByVideoId(String videoId) {
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
                .toList();
    }
}
