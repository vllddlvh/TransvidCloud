package com.transvidcloud.backend.video.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.transvidcloud.backend.video.dto.VideoCursor;
import com.transvidcloud.backend.video.dto.VideoPageResponse;
import com.transvidcloud.backend.video.dto.VideoResponse;
import com.transvidcloud.backend.video.entity.OutboxItem;
import com.transvidcloud.backend.video.entity.VideoItem;
import com.transvidcloud.backend.video.entity.VideoStatus;
import com.transvidcloud.backend.video.service.VideoService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("VideoQueryLifecycleIT - Testcontainers LocalStack DynamoDB + S3 Integration Test (F-008)")
class VideoQueryLifecycleIT {

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(
                    LocalStackContainer.Service.DYNAMODB,
                    LocalStackContainer.Service.S3
            );

    static {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Content-Length");
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("tvc.aws.endpoint", () -> localStack.getEndpoint().toString());
        registry.add("tvc.aws.s3.presign-endpoint", () -> localStack.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", () -> localStack.getRegion());
        registry.add("spring.cloud.aws.credentials.access-key", () -> localStack.getAccessKey());
        registry.add("spring.cloud.aws.credentials.secret-key", () -> localStack.getSecretKey());
        registry.add("tvc.media.base-url", () -> "/media");
    }

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Autowired
    private S3Client s3Client;

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
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void initResources() {
        // 1. Tạo S3 Buckets
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(tvcProperties.getAws().getS3().getInputBucket()).build());
        } catch (BucketAlreadyOwnedByYouException ignored) {}

        // 2. Tạo DynamoDB Tables
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
    }

    private void createTableIfNotExists(
            String tableName,
            List<AttributeDefinition> attributeDefinitions,
            List<KeySchemaElement> keySchema,
            List<GlobalSecondaryIndex> gsis
    ) {
        CreateTableRequest.Builder builder = CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(attributeDefinitions)
                .keySchema(keySchema)
                .billingMode(BillingMode.PAY_PER_REQUEST);

        if (gsis != null && !gsis.isEmpty()) {
            builder.globalSecondaryIndexes(gsis);
        }

        try {
            dynamoDbClient.createTable(builder.build());
        } catch (ResourceInUseException ignored) {}
    }

    private Authentication createJwtAuth(String userId) {
        Jwt jwt = Jwt.withTokenValue("mock.token.value")
                .header("alg", "HS256")
                .subject(userId)
                .claim("userId", userId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("IT-01: Phân trang con trỏ (Cursor Pagination) trên 5 video thực tế và lọc bỏ khóa OUTBOX#")
    void testCursorPagination_EndToEnd() {
        String ownerId = "usr_it_cursor_01";
        Authentication auth = createJwtAuth(ownerId);

        ProjectResponse project = projectService.createProject(
                new CreateProjectRequest("Dự án IT Pagination", "Test phân trang cursor", null),
                ownerId
        );
        String projectId = project.projectId();

        // Tạo 5 video với mốc createdAt tăng dần
        Instant baseTime = Instant.now().minus(10, ChronoUnit.HOURS);
        for (int i = 1; i <= 5; i++) {
            VideoItem item = VideoItem.builder()
                    .videoId("vid_it_00" + i)
                    .projectId(projectId)
                    .title("Video Bài Học " + i)
                    .status(VideoStatus.COMPLETED.name())
                    .sizeBytes(10485760L * i)
                    .contentType("video/mp4")
                    .attemptCount(0)
                    .createdAt(baseTime.plus(i * 10, ChronoUnit.MINUTES).toString())
                    .updatedAt(baseTime.plus(i * 10, ChronoUnit.MINUTES).toString())
                    .build();
            videoRepository.save(item);
        }

        // Tạo 1 bản ghi nội bộ Outbox để kiểm tra lọc sạch
        OutboxItem outboxItem = OutboxItem.builder()
                .videoId(OutboxItem.buildPk("vid_it_001"))
                .projectId(OutboxItem.GSI_PARTITION_PENDING)
                .createdAt(baseTime.plus(15, ChronoUnit.MINUTES).toString())
                .outboxStatus(OutboxItem.STATUS_PENDING)
                .attemptCount(0)
                .build();
        outboxRepository.save(outboxItem);

        // 1. Lấy trang 1 với limit = 2 (mới nhất lên đầu: vid_it_005, vid_it_004)
        VideoPageResponse page1 = videoService.listVideosByProject(projectId, 2, null, auth);
        assertThat(page1).isNotNull();
        assertThat(page1.items()).hasSize(2);
        assertThat(page1.items().get(0).videoId()).isEqualTo("vid_it_005");
        assertThat(page1.items().get(1).videoId()).isEqualTo("vid_it_004");
        assertThat(page1.hasMore()).isTrue();
        assertThat(page1.nextCursor()).isNotNull();

        // 2. Lấy trang 2 với nextCursor từ trang 1 (vid_it_003, vid_it_002)
        VideoPageResponse page2 = videoService.listVideosByProject(projectId, 2, page1.nextCursor(), auth);
        assertThat(page2).isNotNull();
        assertThat(page2.items()).hasSize(2);
        assertThat(page2.items().get(0).videoId()).isEqualTo("vid_it_003");
        assertThat(page2.items().get(1).videoId()).isEqualTo("vid_it_002");
        assertThat(page2.hasMore()).isTrue();
        assertThat(page2.nextCursor()).isNotNull();

        // 3. Lấy trang 3 với nextCursor từ trang 2 (vid_it_001)
        VideoPageResponse page3 = videoService.listVideosByProject(projectId, 2, page2.nextCursor(), auth);
        assertThat(page3).isNotNull();
        assertThat(page3.items()).hasSize(1);
        assertThat(page3.items().get(0).videoId()).isEqualTo("vid_it_001");
        assertThat(page3.hasMore()).isFalse();
        assertThat(page3.nextCursor()).isNull();
    }

    @Test
    @DisplayName("IT-02: Lấy chi tiết Video kèm ghép tiền tố /media cho assets khi COMPLETED và null khi PROCESSING")
    void testGetVideoDetails_CompletedAndProcessing() {
        String ownerId = "usr_it_detail_01";
        Authentication auth = createJwtAuth(ownerId);

        ProjectResponse project = projectService.createProject(
                new CreateProjectRequest("Dự án Chi Tiết", "Test assets", null),
                ownerId
        );
        String projectId = project.projectId();

        // Video COMPLETED có assets
        String compVideoId = "vid_it_comp_001";
        VideoItem compItem = VideoItem.builder()
                .videoId(compVideoId)
                .projectId(projectId)
                .title("Video Đã Xử Lý Xong")
                .status(VideoStatus.COMPLETED.name())
                .sizeBytes(20971520L)
                .contentType("video/mp4")
                .attemptCount(1)
                .assets(Map.of(
                        "masterPlaylist", projectId + "/" + compVideoId + "/hls/master.m3u8",
                        "hls360", projectId + "/" + compVideoId + "/hls/360p/index.m3u8",
                        "hls720", projectId + "/" + compVideoId + "/hls/720p/index.m3u8",
                        "thumbnail", projectId + "/" + compVideoId + "/thumb/default.jpg"
                ))
                .createdAt(Instant.now().toString())
                .updatedAt(Instant.now().toString())
                .build();
        videoRepository.save(compItem);

        VideoResponse compResponse = videoService.getVideoDetails(compVideoId, auth);
        assertThat(compResponse).isNotNull();
        assertThat(compResponse.status()).isEqualTo(VideoStatus.COMPLETED);
        assertThat(compResponse.assets()).isNotNull();
        assertThat(compResponse.assets().masterPlaylistUrl()).isEqualTo("/media/" + projectId + "/" + compVideoId + "/hls/master.m3u8");
        assertThat(compResponse.assets().hls360Url()).isEqualTo("/media/" + projectId + "/" + compVideoId + "/hls/360p/index.m3u8");
        assertThat(compResponse.assets().hls720Url()).isEqualTo("/media/" + projectId + "/" + compVideoId + "/hls/720p/index.m3u8");
        assertThat(compResponse.assets().thumbnailUrl()).isEqualTo("/media/" + projectId + "/" + compVideoId + "/thumb/default.jpg");

        // Video PROCESSING có assets thô nhưng API phải trả về null
        String procVideoId = "vid_it_proc_001";
        VideoItem procItem = VideoItem.builder()
                .videoId(procVideoId)
                .projectId(projectId)
                .title("Video Đang Xử Lý")
                .status(VideoStatus.PROCESSING.name())
                .sizeBytes(20971520L)
                .contentType("video/mp4")
                .attemptCount(0)
                .assets(Map.of("masterPlaylist", "raw/path.m3u8"))
                .createdAt(Instant.now().toString())
                .updatedAt(Instant.now().toString())
                .build();
        videoRepository.save(procItem);

        VideoResponse procResponse = videoService.getVideoDetails(procVideoId, auth);
        assertThat(procResponse).isNotNull();
        assertThat(procResponse.status()).isEqualTo(VideoStatus.PROCESSING);
        assertThat(procResponse.assets()).isNull();
    }

    @Test
    @DisplayName("IT-03: Fast Delete và Conflict Guard (chặn xóa 409 khi PROCESSING, xóa thành công khi COMPLETED)")
    void testDeleteVideo_FastDeleteAndConflictGuard() {
        String ownerId = "usr_it_delete_01";
        Authentication auth = createJwtAuth(ownerId);

        ProjectResponse project = projectService.createProject(
                new CreateProjectRequest("Dự án Xóa", "Test fast delete", null),
                ownerId
        );
        String projectId = project.projectId();

        // 1. Chặn xóa khi PROCESSING
        String procVideoId = "vid_it_proc_conflict";
        VideoItem procItem = VideoItem.builder()
                .videoId(procVideoId)
                .projectId(projectId)
                .title("Video Đang Chạy")
                .status(VideoStatus.PROCESSING.name())
                .createdAt(Instant.now().toString())
                .build();
        videoRepository.save(procItem);

        assertThatThrownBy(() -> videoService.deleteVideo(procVideoId, auth))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

        // Kiểm tra video vẫn còn nguyên
        assertThat(videoRepository.findByVideoId(procVideoId)).isPresent();

        // 2. Xóa thành công khi COMPLETED
        String compVideoId = "vid_it_comp_delete";
        VideoItem compItem = VideoItem.builder()
                .videoId(compVideoId)
                .projectId(projectId)
                .title("Video Xóa Thành Công")
                .status(VideoStatus.COMPLETED.name())
                .createdAt(Instant.now().toString())
                .build();
        videoRepository.save(compItem);

        videoService.deleteVideo(compVideoId, auth);

        // Kiểm tra video đã biến mất hoàn toàn
        assertThat(videoRepository.findByVideoId(compVideoId)).isEmpty();

        // Tra cứu lại chi tiết ném 404 RESOURCE_NOT_FOUND
        assertThatThrownBy(() -> videoService.getVideoDetails(compVideoId, auth))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("IT-04: Phân quyền Dual Security (Bearer JWT và X-API-Key) trên cả 3 endpoints")
    void testDualSecurity_JwtAndApiKeyIsolation() {
        // Project A thuộc User A
        String ownerA = "usr_owner_A";
        Authentication authA = createJwtAuth(ownerA);
        ProjectResponse projectA = projectService.createProject(
                new CreateProjectRequest("Dự án A", "Dự án riêng của A", null),
                ownerA
        );
        String projectIdA = projectA.projectId();

        String videoA = "vid_secret_A";
        VideoItem itemA = VideoItem.builder()
                .videoId(videoA)
                .projectId(projectIdA)
                .title("Video Bí Mật Của A")
                .status(VideoStatus.COMPLETED.name())
                .createdAt(Instant.now().toString())
                .build();
        videoRepository.save(itemA);

        // Project B thuộc User B
        String ownerB = "usr_owner_B";
        Authentication authB = createJwtAuth(ownerB);
        ProjectResponse projectB = projectService.createProject(
                new CreateProjectRequest("Dự án B", "Dự án của B", null),
                ownerB
        );
        String projectIdB = projectB.projectId();

        ApiKeyCreatedResponse apiKeyB = apiKeyService.createApiKey(
                projectIdB,
                new CreateApiKeyRequest("Key B"),
                ownerB
        );
        Authentication apiKeyAuthB = new ApiKeyAuthenticationToken(
                new ApiKeyPrincipal(projectIdB, "Key B", apiKeyB.keyPrefix()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_PROJECT_API"))
        );

        // 1. User B dùng JWT gọi vào tài nguyên của Project A -> 403 ACCESS_DENIED
        assertThatThrownBy(() -> videoService.listVideosByProject(projectIdA, 10, null, authB))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        assertThatThrownBy(() -> videoService.getVideoDetails(videoA, authB))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        assertThatThrownBy(() -> videoService.deleteVideo(videoA, authB))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        // 2. User B dùng API Key của Project B gọi vào tài nguyên của Project A -> 403 ACCESS_DENIED
        assertThatThrownBy(() -> videoService.listVideosByProject(projectIdA, 10, null, apiKeyAuthB))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        assertThatThrownBy(() -> videoService.getVideoDetails(videoA, apiKeyAuthB))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        assertThatThrownBy(() -> videoService.deleteVideo(videoA, apiKeyAuthB))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }
}
