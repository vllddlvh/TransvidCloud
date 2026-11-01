package com.transvidcloud.backend.apikey.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.transvidcloud.backend.apikey.dto.ApiKeyCreatedResponse;
import com.transvidcloud.backend.apikey.dto.ApiKeyListResponse;
import com.transvidcloud.backend.apikey.dto.CreateApiKeyRequest;
import com.transvidcloud.backend.apikey.entity.ApiKeyItem;
import com.transvidcloud.backend.apikey.service.ApiKeyService;
import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.project.dto.CreateProjectRequest;
import com.transvidcloud.backend.project.dto.ProjectResponse;
import com.transvidcloud.backend.project.service.ProjectService;
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
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ApiKeyManagementIT - Testcontainers LocalStack DynamoDB Integration Test")
class ApiKeyManagementIT {

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
    private ProjectService projectService;

    @Autowired
    private ApiKeyService apiKeyService;

    private String apikeysTableName;
    private String projectsTableName;

    @BeforeEach
    void initDynamoDbTables() {
        projectsTableName = tvcProperties.getAws().getDynamodb().getTablePrefix() + "projects";
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
            // Table already exists
        }

        apikeysTableName = tvcProperties.getAws().getDynamodb().getTablePrefix() + "apikeys";
        try {
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(apikeysTableName)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("keyHash").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("projectId").attributeType(ScalarAttributeType.S).build()
                    )
                    .keySchema(
                            KeySchemaElement.builder().attributeName("keyHash").keyType(KeyType.HASH).build()
                    )
                    .globalSecondaryIndexes(
                            GlobalSecondaryIndex.builder()
                                    .indexName("project-index")
                                    .keySchema(
                                            KeySchemaElement.builder().attributeName("projectId").keyType(KeyType.HASH).build()
                                    )
                                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                                    .build()
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
        } catch (ResourceInUseException ignored) {
            // Table already exists
        }
    }

    @Test
    @DisplayName("Kiểm thử trọn vòng đời: Tạo key lưu SHA-256 (không lưu rawKey), GSI list, giới hạn 5 keys, thu hồi và cô lập tenant")
    void testFullApiKeyManagementLifecycle() {
        String ownerId = "usr_owner_it_001";
        String hackerId = "usr_hacker_it_002";

        // 1. Tạo Project thực tế trên DynamoDB
        ProjectResponse project = projectService.createProject(new CreateProjectRequest("Transvid IT Project", "Desc", null), ownerId);
        String projectId = project.projectId();
        assertThat(projectId).isNotNull().startsWith("prj_");

        // 2. Tạo API Key 1
        CreateApiKeyRequest createRequest = new CreateApiKeyRequest("Backend Integration Key");
        ApiKeyCreatedResponse created = apiKeyService.createApiKey(projectId, createRequest, ownerId);

        assertThat(created.rawKey()).isNotNull().startsWith("tvc_live_").hasSize(41);
        assertThat(created.keyPrefix()).startsWith("tvc_live_").endsWith("...");
        assertThat(created.keyHash()).hasSize(64);

        // 3. Kiểm tra trực tiếp bảng DynamoDB: CHỈ LƯU keyHash, TUYỆT ĐỐI KHÔNG LƯU rawKey
        GetItemResponse getResp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(apikeysTableName)
                .key(Map.of("keyHash", AttributeValue.builder().s(created.keyHash()).build()))
                .build());

        assertThat(getResp.hasItem()).isTrue();
        Map<String, AttributeValue> item = getResp.item();
        assertThat(item.get("keyHash").s()).isEqualTo(created.keyHash());
        assertThat(item.get("projectId").s()).isEqualTo(projectId);
        assertThat(item.get("keyPrefix").s()).isEqualTo(created.keyPrefix());
        assertThat(item.containsKey("rawKey")).isFalse(); // Bảo mật: không lưu bản rõ

        // 4. Liệt kê danh sách API Key qua GSI project-index
        ApiKeyListResponse listResponse = apiKeyService.listApiKeys(projectId, ownerId);
        assertThat(listResponse.items()).hasSize(1);
        assertThat(listResponse.items().get(0).keyHash()).isEqualTo(created.keyHash());
        assertThat(listResponse.items().get(0).keyPrefix()).isEqualTo(created.keyPrefix());

        // 5. Kiểm thử giới hạn tối đa 5 API Key/Project
        for (int i = 2; i <= 5; i++) {
            apiKeyService.createApiKey(projectId, new CreateApiKeyRequest("Key số " + i), ownerId);
        }
        ApiKeyListResponse list5 = apiKeyService.listApiKeys(projectId, ownerId);
        assertThat(list5.items()).hasSize(5);

        // Thử tạo key thứ 6 -> Bắt buộc bị từ chối 400 INVALID_KEY
        assertThatThrownBy(() -> apiKeyService.createApiKey(projectId, new CreateApiKeyRequest("Key thứ 6"), ownerId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException appEx = (AppException) e;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_KEY);
                    assertThat(appEx.getMessage()).contains("Maximum 5 API keys allowed");
                });

        // 6. Kiểm tra Cô lập Đa người thuê (Multi-tenant Isolation)
        // User B không thể tạo key trên Project của User A
        assertThatThrownBy(() -> apiKeyService.createApiKey(projectId, new CreateApiKeyRequest("Hacker Key"), hackerId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        // User B không thể xem danh sách key của User A
        assertThatThrownBy(() -> apiKeyService.listApiKeys(projectId, hackerId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        // User B không thể thu hồi key của User A
        assertThatThrownBy(() -> apiKeyService.revokeApiKey(projectId, created.keyHash(), hackerId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        // 7. Thu hồi API Key hợp lệ
        apiKeyService.revokeApiKey(projectId, created.keyHash(), ownerId);

        // Xác nhận bản ghi bị xóa hoàn toàn khỏi DynamoDB
        GetItemResponse getAfterDelete = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(apikeysTableName)
                .key(Map.of("keyHash", AttributeValue.builder().s(created.keyHash()).build()))
                .build());
        assertThat(getAfterDelete.hasItem()).isFalse();

        // Thu hồi lại lần 2 -> ném 404 RESOURCE_NOT_FOUND
        assertThatThrownBy(() -> apiKeyService.revokeApiKey(projectId, created.keyHash(), ownerId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        // Danh sách giảm xuống còn 4 keys
        ApiKeyListResponse listAfterDelete = apiKeyService.listApiKeys(projectId, ownerId);
        assertThat(listAfterDelete.items()).hasSize(4);
    }
}
