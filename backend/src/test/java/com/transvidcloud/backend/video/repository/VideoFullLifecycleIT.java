package com.transvidcloud.backend.video.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transvidcloud.backend.apikey.dto.ApiKeyCreatedResponse;
import com.transvidcloud.backend.apikey.dto.CreateApiKeyRequest;
import com.transvidcloud.backend.apikey.security.ApiKeyAuthenticationToken;
import com.transvidcloud.backend.apikey.security.ApiKeyPrincipal;
import com.transvidcloud.backend.apikey.service.ApiKeyService;
import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.project.dto.CreateProjectRequest;
import com.transvidcloud.backend.project.dto.ProjectResponse;
import com.transvidcloud.backend.project.service.ProjectService;
import com.transvidcloud.backend.video.dto.ConfirmVideoResult;
import com.transvidcloud.backend.video.dto.InitVideoRequest;
import com.transvidcloud.backend.video.dto.InitVideoResponse;
import com.transvidcloud.backend.video.dto.VideoPageResponse;
import com.transvidcloud.backend.video.dto.VideoResponse;
import com.transvidcloud.backend.video.entity.VideoItem;
import com.transvidcloud.backend.video.entity.VideoStatus;
import com.transvidcloud.backend.video.service.VideoService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.core.sync.RequestBody;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("VideoFullLifecycleIT - Kiểm thử tích hợp toàn bộ vòng đời video & hợp đồng SQS/Multi-tenant")
class VideoFullLifecycleIT {

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(
                    LocalStackContainer.Service.DYNAMODB,
                    LocalStackContainer.Service.S3,
                    LocalStackContainer.Service.SQS
            );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("tvc.aws.endpoint", () -> localStack.getEndpoint().toString());
        registry.add("tvc.aws.s3.presign-endpoint", () -> localStack.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", () -> localStack.getRegion());
        registry.add("spring.cloud.aws.credentials.access-key", () -> localStack.getAccessKey());
        registry.add("spring.cloud.aws.credentials.secret-key", () -> localStack.getSecretKey());
    }

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private SqsAsyncClient sqsAsyncClient;

    @Autowired
    private TvcProperties tvcProperties;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private VideoService videoService;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String inputBucket;
    private String queueUrl;

    @BeforeEach
    void initResources() {
        inputBucket = tvcProperties.getAws().getS3().getInputBucket();
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(inputBucket).build());
        } catch (BucketAlreadyOwnedByYouException ignored) {
        }

        String prefix = tvcProperties.getAws().getDynamodb().getTablePrefix();

        createTableIfNotExists(prefix + "projects",
                List.of(
                        AttributeDefinition.builder().attributeName("projectId").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("ownerId").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("createdAt").attributeType(ScalarAttributeType.S).build()
                ),
                List.of(KeySchemaElement.builder().attributeName("projectId").keyType(KeyType.HASH).build()),
                List.of(GlobalSecondaryIndex.builder()
                        .indexName("owner-index")
                        .keySchema(
                                KeySchemaElement.builder().attributeName("ownerId").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("createdAt").keyType(KeyType.RANGE).build()
                        )
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build())
        );

        createTableIfNotExists(prefix + "apikeys",
                List.of(
                        AttributeDefinition.builder().attributeName("keyHash").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("projectId").attributeType(ScalarAttributeType.S).build()
                ),
                List.of(KeySchemaElement.builder().attributeName("keyHash").keyType(KeyType.HASH).build()),
                List.of(GlobalSecondaryIndex.builder()
                        .indexName("project-index")
                        .keySchema(KeySchemaElement.builder().attributeName("projectId").keyType(KeyType.HASH).build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build())
        );

        createTableIfNotExists(prefix + "videos",
                List.of(
                        AttributeDefinition.builder().attributeName("videoId").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("projectId").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("createdAt").attributeType(ScalarAttributeType.S).build()
                ),
                List.of(KeySchemaElement.builder().attributeName("videoId").keyType(KeyType.HASH).build()),
                List.of(GlobalSecondaryIndex.builder()
                        .indexName("project-index")
                        .keySchema(
                                KeySchemaElement.builder().attributeName("projectId").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("createdAt").keyType(KeyType.RANGE).build()
                        )
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build())
        );

        String queueName = tvcProperties.getAws().getSqs().getJobsQueueName();
        try {
            sqsAsyncClient.createQueue(CreateQueueRequest.builder().queueName(queueName).build()).join();
        } catch (Exception ignored) {
        }
        queueUrl = sqsAsyncClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).join().queueUrl();

        try {
            sqsAsyncClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build()).join();
        } catch (Exception ignored) {
        }
    }

    private void createTableIfNotExists(String tableName,
                                        List<AttributeDefinition> attributes,
                                        List<KeySchemaElement> keySchema,
                                        List<GlobalSecondaryIndex> gsiList) {
        try {
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(tableName)
                    .attributeDefinitions(attributes)
                    .keySchema(keySchema)
                    .globalSecondaryIndexes(gsiList)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
        } catch (ResourceInUseException ignored) {
        }
    }

    private Authentication createJwtAuth(String userId) {
        Jwt jwt = Jwt.withTokenValue("mock-jwt-token-" + userId)
                .header("alg", "HS256")
                .subject(userId)
                .claim("email", userId + "@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private Authentication createApiKeyAuth(String projectId, String name, String keyPrefix) {
        ApiKeyPrincipal principal = new ApiKeyPrincipal(projectId, name, keyPrefix);
        return new ApiKeyAuthenticationToken(principal, "mock-raw-key", List.of(new SimpleGrantedAuthority("ROLE_API_KEY")));
    }

    @Test
    @DisplayName("US3: Vòng đời video hoàn chỉnh: Init -> S3 Upload -> Confirm -> SQS Schema v1 -> Worker Emulation -> Query Detail/List")
    void testFullVideoLifecycleAndSqsContract() throws Exception {
        String ownerId = "usr_owner_full_001";
        Authentication jwtAuth = createJwtAuth(ownerId);

        // 1. Tạo Project A
        ProjectResponse projectA = projectService.createProject(
                new CreateProjectRequest("Project Alpha", "Description", null),
                ownerId
        );
        String projectId = projectA.projectId();

        // 2. Cấp API Key A
        ApiKeyCreatedResponse apiKeyA = apiKeyService.createApiKey(
                projectId,
                new CreateApiKeyRequest("Dev Key A"),
                ownerId
        );
        Authentication apiKeyAuth = createApiKeyAuth(projectId, "Dev Key A", apiKeyA.keyPrefix());

        // 3. Khởi tạo Video Upload qua API Key
        InitVideoRequest initReq = new InitVideoRequest("Test Video", "test-video.mp4", 1024L * 1024L, "video/mp4");
        InitVideoResponse initResp = videoService.initVideoUpload(projectId, initReq, apiKeyAuth);

        String videoId = initResp.video().videoId();
        assertThat(initResp.video().status()).isEqualTo(VideoStatus.UPLOADING);
        assertThat(initResp.upload().presignedUrl()).isNotBlank();

        // 4. Giả lập tải file nhị phân lên S3 input bucket
        String inputKey = projectId + "/" + videoId + "/source.mp4";
        s3Client.putObject(
                PutObjectRequest.builder().bucket(inputBucket).key(inputKey).contentType("video/mp4").build(),
                RequestBody.fromBytes("MOCK_VIDEO_BINARY_DATA".getBytes(StandardCharsets.UTF_8))
        );

        // 5. Xác nhận Upload (Confirm)
        ConfirmVideoResult confirmResult = videoService.confirmVideoUpload(videoId, apiKeyAuth);
        assertThat(confirmResult.newlyConfirmed()).isTrue();
        assertThat(confirmResult.video().status()).isEqualTo(VideoStatus.PENDING);

        // 6. Nhận message từ SQS và thẩm định hợp đồng SQS Schema v1
        ReceiveMessageResponse sqsResponse = sqsAsyncClient.receiveMessage(
                ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(1)
                        .waitTimeSeconds(5)
                        .build()
        ).join();

        assertThat(sqsResponse.messages()).hasSize(1);
        String messageBody = sqsResponse.messages().get(0).body();
        JsonNode jsonNode = objectMapper.readTree(messageBody);

        // Đối chiếu với sqs-job-message.schema.json
        assertThat(jsonNode.hasNonNull("jobId")).isTrue();
        assertThat(jsonNode.get("jobId").asText()).matches("^job_[0-9a-f]{12}$");
        assertThat(jsonNode.get("videoId").asText()).isEqualTo(videoId);
        assertThat(jsonNode.get("projectId").asText()).isEqualTo(projectId);
        assertThat(jsonNode.get("inputKey").asText()).isEqualTo(inputKey);
        assertThat(jsonNode.hasNonNull("requestedAt")).isTrue();
        // SchemaVersion = 1 theo hợp đồng
        assertThat(jsonNode.get("schemaVersion").asInt()).isEqualTo(1);

        // 7. Giả lập Worker cập nhật trạng thái COMPLETED và các đường dẫn assets
        VideoItem videoItem = videoRepository.findByVideoId(videoId).orElseThrow();
        videoItem.setStatus(VideoStatus.COMPLETED.name());
        videoItem.setAssets(java.util.Map.of(
                "masterPlaylistUrl", "master.m3u8",
                "hls720Url", "720p/index.m3u8",
                "hls360Url", "360p/index.m3u8",
                "thumbnailUrl", "thumb.jpg"
        ));
        videoItem.setUpdatedAt(Instant.now().toString());
        videoRepository.save(videoItem);

        // 8. Tra cứu chi tiết video qua JWT -> assets được ghép TVC_MEDIA_BASE_URL
        VideoResponse detail = videoService.getVideoDetails(videoId, jwtAuth);
        assertThat(detail.status()).isEqualTo(VideoStatus.COMPLETED);
        assertThat(detail.assets()).isNotNull();
        assertThat(detail.assets().masterPlaylistUrl()).isEqualTo("/media/master.m3u8");
        assertThat(detail.assets().hls720Url()).isEqualTo("/media/720p/index.m3u8");
        assertThat(detail.assets().hls360Url()).isEqualTo("/media/360p/index.m3u8");
        assertThat(detail.assets().thumbnailUrl()).isEqualTo("/media/thumb.jpg");

        // 9. Liệt kê danh sách video theo Project -> xuất hiện videoId vừa tạo
        VideoPageResponse page = videoService.listVideosByProject(projectId, 10, null, jwtAuth);
        assertThat(page.items()).isNotEmpty();
        assertThat(page.items().stream().anyMatch(v -> v.videoId().equals(videoId))).isTrue();
    }

    @Test
    @DisplayName("US3: Cô lập đa người thuê (Multi-tenancy Isolation): Tenant B bị từ chối 403 Forbidden khi truy cập video của Tenant A")
    void testMultiTenantIsolationBlocked() {
        String ownerA = "usr_owner_tenantA";
        String ownerB = "usr_owner_tenantB";
        Authentication authA = createJwtAuth(ownerA);
        Authentication authB = createJwtAuth(ownerB);

        // Tenant A tạo Project A và Video A
        ProjectResponse projectA = projectService.createProject(new CreateProjectRequest("Project A", null, null), ownerA);
        InitVideoResponse videoA = videoService.initVideoUpload(
                projectA.projectId(),
                new InitVideoRequest("Video A", "vidA.mp4", 1024L, "video/mp4"),
                authA
        );
        String videoIdA = videoA.video().videoId();

        // Tenant B tạo Project B và API Key B
        ProjectResponse projectB = projectService.createProject(new CreateProjectRequest("Project B", null, null), ownerB);
        ApiKeyCreatedResponse apiKeyB = apiKeyService.createApiKey(
                projectB.projectId(),
                new CreateApiKeyRequest("Key B"),
                ownerB
        );
        Authentication apiKeyAuthB = createApiKeyAuth(projectB.projectId(), "Key B", apiKeyB.keyPrefix());

        // 1. Tenant B dùng JWT cố tình xem chi tiết Video A -> Phải bị từ chối 403 ACCESS_DENIED
        assertThatThrownBy(() -> videoService.getVideoDetails(videoIdA, authB))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        // 2. Tenant B dùng API Key B cố tình xem chi tiết Video A -> Phải bị từ chối 403 ACCESS_DENIED
        assertThatThrownBy(() -> videoService.getVideoDetails(videoIdA, apiKeyAuthB))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        // 3. Tenant B dùng API Key B cố tình xóa Video A -> Phải bị từ chối 403 ACCESS_DENIED
        assertThatThrownBy(() -> videoService.deleteVideo(videoIdA, apiKeyAuthB))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("US3: Kiểm tra xung đột trạng thái: Chặn xóa khi PROCESSING (409) và cho phép xóa khi COMPLETED (204)")
    void testStateConflictAndFastDelete() {
        String owner = "usr_owner_state_001";
        Authentication auth = createJwtAuth(owner);

        ProjectResponse project = projectService.createProject(new CreateProjectRequest("Prj State", null, null), owner);
        InitVideoResponse videoResp = videoService.initVideoUpload(
                project.projectId(),
                new InitVideoRequest("Video Demo", "demo.mp4", 2048L, "video/mp4"),
                auth
        );
        String videoId = videoResp.video().videoId();

        // Cập nhật video sang PROCESSING
        VideoItem item = videoRepository.findByVideoId(videoId).orElseThrow();
        item.setStatus(VideoStatus.PROCESSING.name());
        videoRepository.save(item);

        // Chặn xóa 409
        assertThatThrownBy(() -> videoService.deleteVideo(videoId, auth))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

        // Đổi video sang COMPLETED
        item.setStatus(VideoStatus.COMPLETED.name());
        videoRepository.save(item);

        // Xóa thành công
        videoService.deleteVideo(videoId, auth);

        // Tra cứu lại phải trả về 404 RESOURCE_NOT_FOUND
        assertThatThrownBy(() -> videoService.getVideoDetails(videoId, auth))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
