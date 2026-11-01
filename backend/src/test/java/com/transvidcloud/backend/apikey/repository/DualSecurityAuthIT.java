package com.transvidcloud.backend.apikey.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transvidcloud.backend.apikey.dto.ApiKeyCreatedResponse;
import com.transvidcloud.backend.apikey.dto.CreateApiKeyRequest;
import com.transvidcloud.backend.apikey.service.ApiKeyService;
import com.transvidcloud.backend.auth.dto.AuthResponse;
import com.transvidcloud.backend.auth.dto.RegisterRequest;
import com.transvidcloud.backend.auth.service.AuthService;
import com.transvidcloud.backend.auth.service.JwtTokenService;
import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.project.dto.CreateProjectRequest;
import com.transvidcloud.backend.project.dto.ProjectResponse;
import com.transvidcloud.backend.project.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("DualSecurityAuthIT - Kiểm thử tích hợp Xác thực Dual Security (JWT + X-API-Key) trên Testcontainers LocalStack")
class DualSecurityAuthIT {

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Autowired
    private TvcProperties tvcProperties;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ApiKeyService apiKeyService;

    @BeforeEach
    void initTables() {
        createTableIfNotExists(tvcProperties.getAws().getDynamodb().getTablePrefix() + "users", "userId", null, null);
        createTableIfNotExists(tvcProperties.getAws().getDynamodb().getTablePrefix() + "projects", "projectId", "owner-index", "ownerId");
        createTableIfNotExists(tvcProperties.getAws().getDynamodb().getTablePrefix() + "apikeys", "keyHash", "project-index", "projectId");
    }

    private void createTableIfNotExists(String tableName, String pk, String gsiName, String gsiPk) {
        try {
            CreateTableRequest.Builder builder = CreateTableRequest.builder()
                    .tableName(tableName)
                    .billingMode(BillingMode.PAY_PER_REQUEST);

            if (gsiName != null) {
                builder.attributeDefinitions(
                        AttributeDefinition.builder().attributeName(pk).attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName(gsiPk).attributeType(ScalarAttributeType.S).build()
                );
                builder.keySchema(KeySchemaElement.builder().attributeName(pk).keyType(KeyType.HASH).build());
                builder.globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName(gsiName)
                        .keySchema(KeySchemaElement.builder().attributeName(gsiPk).keyType(KeyType.HASH).build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build());
            } else {
                builder.attributeDefinitions(
                        AttributeDefinition.builder().attributeName(pk).attributeType(ScalarAttributeType.S).build()
                );
                builder.keySchema(KeySchemaElement.builder().attributeName(pk).keyType(KeyType.HASH).build());
            }
            dynamoDbClient.createTable(builder.build());
        } catch (ResourceInUseException ignored) {
            // Already exists
        }
    }

    @Test
    @DisplayName("Kiểm thử toàn diện Dual Security: X-API-Key, Bearer JWT, ưu tiên JWT, chặn gọi chéo 403, 401 khi key thu hồi")
    void testDualSecurityAuthenticationFlow() throws Exception {
        // 1. Khởi tạo User A và Project A
        String emailA = "user_a_" + System.currentTimeMillis() + "@transvidcloud.io";
        AuthResponse authA = authService.register(new RegisterRequest(emailA, "Password123@", "User A"));
        String jwtTokenA = authA.accessToken();
        String userIdA = authA.user().userId();
        ProjectResponse projectA = projectService.createProject(new CreateProjectRequest("Project A", "Desc", null), userIdA);
        String projectAId = projectA.projectId();

        // Khởi tạo User B và Project B
        String emailB = "user_b_" + System.currentTimeMillis() + "@transvidcloud.io";
        AuthResponse authB = authService.register(new RegisterRequest(emailB, "Password123@", "User B"));
        ProjectResponse projectB = projectService.createProject(new CreateProjectRequest("Project B", "Desc", null), authB.user().userId());
        String projectBId = projectB.projectId();

        // 2. Tạo API Key cho Project A
        ApiKeyCreatedResponse keyA = apiKeyService.createApiKey(projectAId, new CreateApiKeyRequest("Dual Auth Key A"), userIdA);
        String rawKeyA = keyA.rawKey();

        // 3. Kịch bản 1: Gọi endpoint bằng X-API-Key hợp lệ -> 200 OK, authType: API_KEY
        mockMvc.perform(get("/projects/{projectId}/test-dual-resource", projectAId)
                        .header("X-API-Key", rawKeyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId", is(projectAId)))
                .andExpect(jsonPath("$.authType", is("API_KEY")));

        // 4. Kịch bản 2: Gọi endpoint bằng Bearer JWT hợp lệ -> 200 OK, authType: BEARER_JWT
        mockMvc.perform(get("/projects/{projectId}/test-dual-resource", projectAId)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId", is(projectAId)))
                .andExpect(jsonPath("$.authType", is("BEARER_JWT")));

        // 5. Kịch bản 3: Gửi ĐỒNG THỜI cả Bearer JWT và X-API-Key -> Ưu tiên Bearer JWT
        mockMvc.perform(get("/projects/{projectId}/test-dual-resource", projectAId)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .header("X-API-Key", rawKeyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authType", is("BEARER_JWT")));

        // 6. Kịch bản 4: Gọi bằng X-API-Key sai hoặc không tồn tại -> 401 Unauthorized (UNAUTHENTICATED)
        mockMvc.perform(get("/projects/{projectId}/test-dual-resource", projectAId)
                        .header("X-API-Key", "tvc_live_00000000000000000000000000000000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));

        // 7. Kịch bản 5: Không gửi cả JWT lẫn X-API-Key -> 401 Unauthorized
        mockMvc.perform(get("/projects/{projectId}/test-dual-resource", projectAId))
                .andExpect(status().isUnauthorized());

        // 8. Kịch bản 6: Dùng API Key của Project A nhưng gọi vào URL của Project B -> 403 Forbidden (ACCESS_DENIED)
        mockMvc.perform(get("/projects/{projectId}/test-dual-resource", projectBId)
                        .header("X-API-Key", rawKeyA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));

        // 9. Kịch bản 7: Dùng X-API-Key gọi endpoint chỉ dành riêng cho Web User (POST /projects) -> 403 Forbidden (ACCESS_DENIED)
        CreateProjectRequest newProjectRequest = new CreateProjectRequest("Unauthorized via API Key", "Desc", null);
        mockMvc.perform(post("/projects")
                        .header("X-API-Key", rawKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newProjectRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));

        // 10. Kịch bản 8: Dùng X-API-Key gọi endpoint quản lý API Key -> 403 Forbidden (ACCESS_DENIED)
        CreateApiKeyRequest newKeyRequest = new CreateApiKeyRequest("Sub Key");
        mockMvc.perform(post("/projects/{projectId}/api-keys", projectAId)
                        .header("X-API-Key", rawKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newKeyRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));

        // 11. Kịch bản 9: Thu hồi API Key -> Gọi lại ngay lập tức bị từ chối 401 Unauthorized
        apiKeyService.revokeApiKey(projectAId, keyA.keyHash(), userIdA);

        mockMvc.perform(get("/projects/{projectId}/test-dual-resource", projectAId)
                        .header("X-API-Key", rawKeyA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }
}
