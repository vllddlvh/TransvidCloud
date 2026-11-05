package com.transvidcloud.backend.project.repository;

import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.project.entity.ProjectItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Tầng Repository tương tác với bảng DynamoDB tvc-{env}-projects.
 * Hỗ trợ lưu trữ, đọc theo ID, truy vấn theo GSI owner-index và Soft Delete.
 */
@Slf4j
@Repository
public class ProjectRepository {

    private final DynamoDbTable<ProjectItem> projectTable;

    public ProjectRepository(DynamoDbEnhancedClient enhancedClient, TvcProperties tvcProperties) {
        String tableName = tvcProperties.getAws().getDynamodb().getTablePrefix() + "projects";
        this.projectTable = enhancedClient.table(tableName, TableSchema.fromBean(ProjectItem.class));
    }

    /**
     * Lưu hoặc cập nhật toàn bộ bản ghi Project.
     *
     * @param item Bản ghi ProjectItem
     */
    public void save(ProjectItem item) {
        projectTable.putItem(item);
    }

    /**
     * Tìm kiếm Project theo projectId (Partition Key).
     *
     * @param projectId Mã định danh duy nhất của Project
     * @return Optional chứa ProjectItem nếu tìm thấy
     */
    public Optional<ProjectItem> findById(String projectId) {
        ProjectItem item = projectTable.getItem(Key.builder().partitionValue(projectId).build());
        return Optional.ofNullable(item);
    }

    /**
     * Truy vấn danh sách các Project của một owner thông qua GSI owner-index.
     * Sắp xếp mới nhất lên đầu (scanIndexForward = false) và loại trừ các project đã bị xóa mềm.
     *
     * @param ownerId Mã định danh người sở hữu
     * @return Danh sách các ProjectItem đang hoạt động
     */
    public List<ProjectItem> findByOwnerId(String ownerId) {
        DynamoDbIndex<ProjectItem> ownerIndex = projectTable.index("owner-index");
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(ownerId).build()
        );

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // Sắp xếp mới nhất lên đầu
                .build();

        return ownerIndex.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(item -> ProjectItem.STATUS_ACTIVE.equals(item.getStatus())
                        || (item.getStatus() == null && item.getDeletedAt() == null))
                .toList();
    }

    /**
     * Cập nhật các trường thông tin của Project.
     *
     * @param item Bản ghi ProjectItem đã cập nhật
     * @return ProjectItem sau khi cập nhật
     */
    public ProjectItem update(ProjectItem item) {
        return projectTable.updateItem(item);
    }

    /**
     * Thực hiện Soft Delete Project: chuyển trạng thái thành DELETED và ghi nhận mốc thời gian deletedAt.
     *
     * @param projectId Mã định danh dự án cần xóa mềm
     * @return Optional chứa ProjectItem sau khi xóa mềm, rỗng nếu không tìm thấy bản ghi ban đầu
     */
    public Optional<ProjectItem> softDelete(String projectId) {
        Optional<ProjectItem> existingOpt = findById(projectId);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }

        ProjectItem item = existingOpt.get();
        String now = Instant.now().toString();
        item.setStatus(ProjectItem.STATUS_DELETED);
        item.setDeletedAt(now);
        item.setUpdatedAt(now);

        projectTable.updateItem(item);
        return Optional.of(item);
    }
}
