package com.transvidcloud.backend.project.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.project.dto.CreateProjectRequest;
import com.transvidcloud.backend.project.dto.ProjectListResponse;
import com.transvidcloud.backend.project.dto.ProjectResponse;
import com.transvidcloud.backend.project.dto.UpdateProjectRequest;
import com.transvidcloud.backend.project.entity.ProjectItem;
import com.transvidcloud.backend.project.service.ProjectService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ProjectManagementIT - Testcontainers LocalStack DynamoDB Integration Test")
class ProjectManagementIT {

    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.DYNAMODB);

    static {
        localStack.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("tvc.aws.endpoint", () -> localStack.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", () -> localStack.getRegion());
        registry.add("spring.cloud.aws.credentials.access-key", () -> localStack.getAccessKey());
        registry.add("spring.cloud.aws.credentials.secret-key", () -> localStack.getSecretKey());
    }

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Autowired
    private TvcProperties tvcProperties;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectService projectService;

    @BeforeEach
    void initDynamoDbTables() {
        String projectsTableName = tvcProperties.getAws().getDynamodb().getTablePrefix() + "projects";
        try {
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(projectsTableName)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("projectId").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("ownerId").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("createdAt").attributeType(ScalarAttributeType.S).build()
                    )
                    .keySchema(
                            KeySchemaElement.builder().attributeName("projectId").keyType(KeyType.HASH).build()
                    )
                    .globalSecondaryIndexes(
                            GlobalSecondaryIndex.builder()
                                    .indexName("owner-index")
                                    .keySchema(
                                            KeySchemaElement.builder().attributeName("ownerId").keyType(KeyType.HASH).build(),
                                            KeySchemaElement.builder().attributeName("createdAt").keyType(KeyType.RANGE).build()
                                    )
                                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                                    .build()
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
        } catch (ResourceInUseException ignored) {
            // Bảng đã tồn tại
        }

        String videosTableName = tvcProperties.getAws().getDynamodb().getTablePrefix() + "videos";
        try {
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(videosTableName)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("videoId").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("projectId").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("createdAt").attributeType(ScalarAttributeType.S).build()
                    )
                    .keySchema(
                            KeySchemaElement.builder().attributeName("videoId").keyType(KeyType.HASH).build()
                    )
                    .globalSecondaryIndexes(
                            GlobalSecondaryIndex.builder()
                                    .indexName("project-index")
                                    .keySchema(
                                            KeySchemaElement.builder().attributeName("projectId").keyType(KeyType.HASH).build(),
                                            KeySchemaElement.builder().attributeName("createdAt").keyType(KeyType.RANGE).build()
                                    )
                                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                                    .build()
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
        } catch (ResourceInUseException ignored) {
            // Bảng đã tồn tại
        }
    }

    @Test
    @DisplayName("Kiểm thử tích hợp trọn vẹn CRUD Project, GSI owner-index, Ownership Isolation và Xóa an toàn Soft Delete")
    void testProjectFullLifecycleAndSafetyGuard() throws InterruptedException {
        String ownerA = "usr_alpha_999";
        String ownerB = "usr_beta_888";

        // 1. Tạo Project 1 cho User A
        CreateProjectRequest req1 = new CreateProjectRequest("Project Alpha 1", "Mô tả 1", "https://hook1.io");
        ProjectResponse p1 = projectService.createProject(req1, ownerA);
        assertThat(p1.projectId()).startsWith("prj_");
        assertThat(p1.ownerId()).isEqualTo(ownerA);

        // Chờ 1 chút để đảm bảo createdAt khác nhau phục vụ kiểm tra sort order
        Thread.sleep(100);

        // 2. Tạo Project 2 cho User A
        CreateProjectRequest req2 = new CreateProjectRequest("Project Alpha 2", "Mô tả 2", "https://hook2.io");
        ProjectResponse p2 = projectService.createProject(req2, ownerA);
        assertThat(p2.projectId()).startsWith("prj_");

        // 3. Liệt kê danh sách Project của User A qua GSI owner-index (kiểm tra mới nhất lên đầu)
        ProjectListResponse listA = projectService.listProjects(ownerA);
        assertThat(listA.items()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(listA.items().get(0).projectId()).isEqualTo(p2.projectId());
        assertThat(listA.items().get(1).projectId()).isEqualTo(p1.projectId());

        // 4. Xem chi tiết Project 1
        ProjectResponse detail1 = projectService.getProject(p1.projectId(), ownerA);
        assertThat(detail1.name()).isEqualTo("Project Alpha 1");

        // 5. Kiểm tra phân quyền sở hữu (Multi-tenant Isolation): User B không thể xem, sửa hoặc xóa Project của User A
        assertThatThrownBy(() -> projectService.getProject(p1.projectId(), ownerB))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

        assertThatThrownBy(() -> projectService.updateProject(p1.projectId(), new UpdateProjectRequest("Hack", null, null), ownerB))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

        assertThatThrownBy(() -> projectService.deleteProject(p1.projectId(), ownerB))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

        // 6. Cập nhật thông tin Project 1
        UpdateProjectRequest updateReq = new UpdateProjectRequest("Project Alpha 1 - Renamed", "Mô tả mới", null);
        ProjectResponse updated1 = projectService.updateProject(p1.projectId(), updateReq, ownerA);
        assertThat(updated1.name()).isEqualTo("Project Alpha 1 - Renamed");
        assertThat(updated1.description()).isEqualTo("Mô tả mới");

        // 7. Kiểm tra xóa an toàn khi còn video in-flight (PROCESSING)
        String videosTable = tvcProperties.getAws().getDynamodb().getTablePrefix() + "videos";
        String videoId = "vid_test_001";
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(videosTable)
                .item(Map.of(
                        "videoId", AttributeValue.builder().s(videoId).build(),
                        "projectId", AttributeValue.builder().s(p1.projectId()).build(),
                        "status", AttributeValue.builder().s("PROCESSING").build(),
                        "createdAt", AttributeValue.builder().s(Instant.now().toString()).build()
                ))
                .build());

        // Cố gắng xóa Project 1 -> Bị từ chối 409 RESOURCE_IN_USE
        assertThatThrownBy(() -> projectService.deleteProject(p1.projectId(), ownerA))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_IN_USE);

        // Chuyển video sang trạng thái COMPLETED
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(videosTable)
                .item(Map.of(
                        "videoId", AttributeValue.builder().s(videoId).build(),
                        "projectId", AttributeValue.builder().s(p1.projectId()).build(),
                        "status", AttributeValue.builder().s("COMPLETED").build(),
                        "createdAt", AttributeValue.builder().s(Instant.now().toString()).build()
                ))
                .build());

        // 8. Thực hiện Soft Delete Project 1 thành công
        projectService.deleteProject(p1.projectId(), ownerA);

        // 9. Xác minh sau khi Soft Delete:
        // - Bản ghi Project trong DynamoDB chuyển status = DELETED, deletedAt != null
        ProjectItem deletedItem = projectRepository.findById(p1.projectId()).orElseThrow();
        assertThat(deletedItem.getStatus()).isEqualTo(ProjectItem.STATUS_DELETED);
        assertThat(deletedItem.getDeletedAt()).isNotNull();

        // - Gọi getProject(p1) -> ném 404 RESOURCE_NOT_FOUND
        assertThatThrownBy(() -> projectService.getProject(p1.projectId(), ownerA))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

        // - Gọi listProjects(ownerA) -> không còn thấy p1
        ProjectListResponse listAfterDelete = projectService.listProjects(ownerA);
        assertThat(listAfterDelete.items())
                .noneMatch(item -> item.projectId().equals(p1.projectId()));

        // - Video COMPLETED trong bảng videos vẫn được bảo toàn nguyên vẹn 100%
        GetItemResponse videoRes = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(videosTable)
                .key(Map.of("videoId", AttributeValue.builder().s(videoId).build()))
                .build());
        assertThat(videoRes.hasItem()).isTrue();
        assertThat(videoRes.item().get("status").s()).isEqualTo("COMPLETED");
    }
}
