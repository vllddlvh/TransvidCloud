package com.transvidcloud.backend.video.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.transvidcloud.backend.video.dto.InitVideoRequest;
import com.transvidcloud.backend.video.dto.InitVideoResponse;
import com.transvidcloud.backend.video.entity.VideoItem;
import com.transvidcloud.backend.video.entity.VideoStatus;
import com.transvidcloud.backend.video.service.VideoService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("VideoUploadInitIT - Testcontainers LocalStack S3 + DynamoDB Integration Test")
class VideoUploadInitIT {

    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.DYNAMODB, LocalStackContainer.Service.S3);

    static {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Content-Length");
        localStack.start();
    }

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
    private TvcProperties tvcProperties;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private VideoService videoService;

    @Autowired
    private VideoRepository videoRepository;

    private String inputBucket;

    @BeforeEach
    void initResources() {
        // 1. Tạo S3 Input Bucket
        inputBucket = tvcProperties.getAws().getS3().getInputBucket();
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(inputBucket).build());
        } catch (BucketAlreadyOwnedByYouException ignored) {
        }

        // 2. Tạo DynamoDB Tables: projects, apikeys, videos
        String prefix = tvcProperties.getAws().getDynamodb().getTablePrefix();

        // Bảng projects
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

        // Bảng apikeys
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

        // Bảng videos
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
        Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
                .header("alg", "HS256")
                .subject(userId)
                .claim("user_id", userId)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("Real Verification: Toàn bộ vòng đời Khởi tạo Video -> Nhận Presigned PUT URL -> PUT byte thật lên LocalStack S3 -> Verify S3 HeadObject và DynamoDB")
    void testVideoUploadInit_FullLifecycle_RealVerification() throws Exception {
        String ownerId = "usr_owner_it_001";
        String hackerId = "usr_hacker_it_002";

        // 1. Tạo Project thật
        ProjectResponse project = projectService.createProject(
                new CreateProjectRequest("Khóa học Lập trình Đám mây", "Dự án video thực tế", null),
                ownerId
        );
        String projectId = project.projectId();
        assertThat(projectId).startsWith("prj_");

        // 2. Tạo API Key thật cho Project
        ApiKeyCreatedResponse apiKey = apiKeyService.createApiKey(
                projectId,
                new CreateApiKeyRequest("CI Key"),
                ownerId
        );
        String rawApiKey = apiKey.rawKey();

        // 3. User Story 1 (SaaS Web User qua JWT) khởi tạo video
        Authentication jwtAuth = createJwtAuth(ownerId);
        byte[] fakeVideoBytes = "FAKE_VIDEO_CONTENT_BYTE_DATA_FOR_PRESIGNED_PUT_TESTING".getBytes(StandardCharsets.UTF_8);
        long fileSize = fakeVideoBytes.length;

        InitVideoRequest initRequest = new InitVideoRequest(
                "Bài 1 - Giới thiệu Đám mây",
                "intro.lecture.MP4",
                fileSize,
                "video/mp4"
        );

        InitVideoResponse initResponse = videoService.initVideoUpload(projectId, initRequest, jwtAuth);

        assertThat(initResponse).isNotNull();
        assertThat(initResponse.video()).isNotNull();
        String videoId = initResponse.video().videoId();
        assertThat(videoId).startsWith("vid_");
        assertThat(initResponse.video().status()).isEqualTo(VideoStatus.UPLOADING);
        assertThat(initResponse.video().attemptCount()).isEqualTo(0);

        String presignedUrl = initResponse.upload().presignedUrl();
        assertThat(presignedUrl).isNotBlank().contains(inputBucket).contains("source.mp4");
        assertThat(initResponse.upload().requiredHeaders())
                .containsEntry("Content-Type", "video/mp4")
                .containsEntry("Content-Length", String.valueOf(fileSize));

        // 4. KIỂM CHỨNG THỰC TẾ: Client thực hiện HTTP PUT trực tiếp lên LocalStack S3 qua Presigned URL
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest s3PutRequest = HttpRequest.newBuilder()
                .uri(URI.create(presignedUrl))
                .header("Content-Type", "video/mp4")
                .header("Content-Length", String.valueOf(fileSize))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(fakeVideoBytes))
                .build();

        HttpResponse<String> s3PutResponse = httpClient.send(s3PutRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(s3PutResponse.statusCode())
                .as("S3 Presigned PUT URL phải cho phép tải lên thành công với HTTP 200 OK")
                .isEqualTo(200);

        // 5. Xác minh đối tượng thực tế trên S3 bằng HeadObject
        String expectedInputKey = projectId + "/" + videoId + "/source.mp4";
        HeadObjectResponse headResponse = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(inputBucket)
                .key(expectedInputKey)
                .build());
        assertThat(headResponse).isNotNull();
        assertThat(headResponse.contentLength()).isEqualTo(fileSize);
        assertThat(headResponse.contentType()).isEqualTo("video/mp4");

        // 6. Xác minh bản ghi trong bảng DynamoDB videos
        Optional<VideoItem> videoItemOpt = videoRepository.findByVideoId(videoId);
        assertThat(videoItemOpt).isPresent();
        VideoItem item = videoItemOpt.get();
        assertThat(item.getVideoId()).isEqualTo(videoId);
        assertThat(item.getProjectId()).isEqualTo(projectId);
        assertThat(item.getTitle()).isEqualTo("Bài 1 - Giới thiệu Đám mây");
        assertThat(item.getStatus()).isEqualTo(VideoStatus.UPLOADING.name());
        assertThat(item.getAttemptCount()).isEqualTo(0);
        assertThat(item.getInputKey()).isEqualTo(expectedInputKey);

        // 7. User Story 2: Developer Application khởi tạo video qua X-API-Key
        ApiKeyPrincipal principal = new ApiKeyPrincipal(projectId, "CI Key", apiKey.keyPrefix());
        Authentication apiKeyAuth = new ApiKeyAuthenticationToken(
                principal,
                rawApiKey,
                List.of(new SimpleGrantedAuthority("ROLE_API_KEY"))
        );

        InitVideoRequest devRequest = new InitVideoRequest(
                "Video tự động từ API Key",
                "camera_clip.mov",
                1024L,
                "video/quicktime"
        );
        InitVideoResponse devResponse = videoService.initVideoUpload(projectId, devRequest, apiKeyAuth);
        assertThat(devResponse.video().status()).isEqualTo(VideoStatus.UPLOADING);
        assertThat(devResponse.upload().presignedUrl()).contains("source.mov");

        // 8. User Story 3: Giới hạn dung lượng tối đa 200MB (HTTP 413)
        InitVideoRequest oversizedRequest = new InitVideoRequest(
                "Heavy Video",
                "heavy.mp4",
                209715201L, // 200MB + 1 byte
                "video/mp4"
        );
        assertThatThrownBy(() -> videoService.initVideoUpload(projectId, oversizedRequest, jwtAuth))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.UPLOAD_LIMIT_EXCEEDED));

        // 9. User Story 4: Kiểm soát định dạng video (HTTP 422)
        InitVideoRequest invalidFormatRequest = new InitVideoRequest(
                "Clip AVI",
                "video.avi",
                1024L,
                "video/x-msvideo"
        );
        assertThatThrownBy(() -> videoService.initVideoUpload(projectId, invalidFormatRequest, jwtAuth))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

        // 10. User Story 5: Phân quyền & Cô lập Project (HTTP 403 / 404 / 401)
        // User B cố gọi vào Project của User A
        Authentication hackerJwtAuth = createJwtAuth(hackerId);
        assertThatThrownBy(() -> videoService.initVideoUpload(projectId, initRequest, hackerJwtAuth))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED));

        // API Key của Project khác cố gọi vào Project này
        ApiKeyPrincipal otherPrincipal = new ApiKeyPrincipal("prj_other_999", "Other Key", "tvc_live_xxx...");
        Authentication otherApiKeyAuth = new ApiKeyAuthenticationToken(
                otherPrincipal,
                "rawKey",
                List.of(new SimpleGrantedAuthority("ROLE_API_KEY"))
        );
        assertThatThrownBy(() -> videoService.initVideoUpload(projectId, initRequest, otherApiKeyAuth))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED));

        // Gọi vào Project không tồn tại
        assertThatThrownBy(() -> videoService.initVideoUpload("prj_non_existent", initRequest, jwtAuth))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        // Tạo một Project mới riêng biệt không có video rồi xóa mềm để kiểm thử gọi vào Project đã DELETED
        ProjectResponse projectToDelete = projectService.createProject(
                new CreateProjectRequest("Project Để Xóa", "Test Deleted", null),
                ownerId
        );
        String deletedProjectId = projectToDelete.projectId();
        projectService.deleteProject(deletedProjectId, ownerId);

        assertThatThrownBy(() -> videoService.initVideoUpload(deletedProjectId, initRequest, jwtAuth))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
