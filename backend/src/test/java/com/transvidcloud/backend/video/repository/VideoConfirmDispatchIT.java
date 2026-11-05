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
import com.transvidcloud.backend.video.dto.ConfirmVideoResult;
import com.transvidcloud.backend.video.dto.InitVideoRequest;
import com.transvidcloud.backend.video.dto.InitVideoResponse;
import com.transvidcloud.backend.video.dto.SqsJobMessage;
import com.transvidcloud.backend.video.entity.OutboxItem;
import com.transvidcloud.backend.video.entity.VideoItem;
import com.transvidcloud.backend.video.entity.VideoStatus;
import com.transvidcloud.backend.video.service.OutboxRelayScheduler;
import com.transvidcloud.backend.video.service.VideoService;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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
@DisplayName("VideoConfirmDispatchIT - Testcontainers LocalStack S3 + DynamoDB + SQS Integration Test")
class VideoConfirmDispatchIT {

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(
                    LocalStackContainer.Service.DYNAMODB,
                    LocalStackContainer.Service.S3,
                    LocalStackContainer.Service.SQS
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
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxRelayScheduler outboxRelayScheduler;

    @Autowired
    private ObjectMapper objectMapper;

    private String inputBucket;
    private String queueUrl;

    @BeforeEach
    void initResources() throws Exception {
        // 1. Tạo S3 Input Bucket
        inputBucket = tvcProperties.getAws().getS3().getInputBucket();
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(inputBucket).build());
        } catch (BucketAlreadyOwnedByYouException ignored) {
        }

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

        // 3. Tạo SQS Queue
        String queueName = tvcProperties.getAws().getSqs().getJobsQueueName();
        try {
            sqsAsyncClient.createQueue(CreateQueueRequest.builder().queueName(queueName).build()).join();
        } catch (Exception ignored) {
        }
        queueUrl = sqsAsyncClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).join().queueUrl();

        // Purge queue trước mỗi test
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
        Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
                .header("alg", "HS256")
                .subject(userId)
                .claim("user_id", userId)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private void uploadFileViaPresignedUrl(String presignedUrl, byte[] content, String contentType) throws Exception {
        URI uri = URI.create(presignedUrl);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Content-Type", contentType);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(content);
        }
        int code = connection.getResponseCode();
        assertThat(code).isIn(200, 204);
    }

    @Test
    @DisplayName("Real Verification US1 & US3: Khởi tạo Video -> Upload S3 thật -> Confirm 202 -> DynamoDB PENDING -> SQS nhận SqsJobMessage schema v1")
    void testConfirmVideo_FullLifecycle_RealVerification() throws Exception {
        String ownerId = "usr_confirm_owner_001";

        // 1. Tạo Project & Init Video
        ProjectResponse project = projectService.createProject(
                new CreateProjectRequest("Project Test Confirm", "Mô tả test", null),
                ownerId
        );
        String projectId = project.projectId();
        Authentication jwtAuth = createJwtAuth(ownerId);

        byte[] videoBytes = "MP4_SAMPLE_VIDEO_CONTENT_REAL_TEST".getBytes(StandardCharsets.UTF_8);
        InitVideoResponse initResp = videoService.initVideoUpload(
                projectId,
                new InitVideoRequest("test-video", "test-video.mp4", (long) videoBytes.length, "video/mp4"),
                jwtAuth
        );
        String videoId = initResp.video().videoId();

        // 2. Upload file byte thật lên LocalStack S3 qua Presigned PUT URL
        uploadFileViaPresignedUrl(initResp.upload().presignedUrl(), videoBytes, "video/mp4");

        // 3. Gọi Confirm Video Upload
        ConfirmVideoResult result = videoService.confirmVideoUpload(videoId, jwtAuth);

        // Xác nhận kết quả trả về
        assertThat(result.newlyConfirmed()).isTrue();
        assertThat(result.video().status()).isEqualTo(VideoStatus.PENDING);
        assertThat(result.video().videoId()).isEqualTo(videoId);
        assertThat(result.video().sizeBytes()).isEqualTo((long) videoBytes.length);

        // 4. Kiểm tra DynamoDB VideoItem
        Optional<VideoItem> videoOpt = videoRepository.findByVideoId(videoId);
        assertThat(videoOpt).isPresent();
        VideoItem video = videoOpt.get();
        assertThat(video.getStatus()).isEqualTo(VideoStatus.PENDING.name());
        assertThat(video.getSizeBytes()).isEqualTo((long) videoBytes.length);

        // 5. Kiểm tra DynamoDB OutboxItem
        Optional<OutboxItem> outboxOpt = outboxRepository.findByTargetVideoId(videoId);
        assertThat(outboxOpt).isPresent();
        OutboxItem outbox = outboxOpt.get();
        assertThat(outbox.getTargetVideoId()).isEqualTo(videoId);
        assertThat(outbox.getJobId()).startsWith("job_");
        assertThat(outbox.getOutboxStatus()).isEqualTo(OutboxItem.STATUS_PUBLISHED);
        assertThat(outbox.getProjectId()).isEqualTo(OutboxItem.GSI_PARTITION_PUBLISHED);
        assertThat(outbox.getPublishedAt()).isNotNull();

        // 6. Kiểm tra SQS queue nhận được message đúng chuẩn v1
        ReceiveMessageResponse receiveResp = sqsAsyncClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(3)
                .build()).join();

        assertThat(receiveResp.messages()).isNotEmpty();
        var matchingMsg = receiveResp.messages().stream()
                .filter(m -> m.body().contains(videoId))
                .findFirst();
        assertThat(matchingMsg).isPresent();

        SqsJobMessage sqsMsg = objectMapper.readValue(matchingMsg.get().body(), SqsJobMessage.class);
        assertThat(sqsMsg.schemaVersion()).isEqualTo(1);
        assertThat(sqsMsg.jobId()).isEqualTo(outbox.getJobId());
        assertThat(sqsMsg.videoId()).isEqualTo(videoId);
        assertThat(sqsMsg.projectId()).isEqualTo(projectId);
        assertThat(sqsMsg.inputKey()).isEqualTo(video.getInputKey());
        assertThat(sqsMsg.requestedAt()).isNotNull();
    }

    @Test
    @DisplayName("Real Verification US2: Idempotent Confirm lặp lại trả về 200 OK (newlyConfirmed=false) và không gửi thêm SQS message")
    void testConfirmVideo_Idempotent_SecondCallNoDuplicateSqs() throws Exception {
        String ownerId = "usr_idempotent_owner_002";
        Authentication jwtAuth = createJwtAuth(ownerId);

        ProjectResponse project = projectService.createProject(
                new CreateProjectRequest("Project Idempotent", null, null),
                ownerId
        );
        String projectId = project.projectId();

        byte[] videoBytes = "IDEMPOTENT_SAMPLE_BYTES".getBytes(StandardCharsets.UTF_8);
        InitVideoResponse initResp = videoService.initVideoUpload(
                projectId,
                new InitVideoRequest("idempotent", "idempotent.mp4", (long) videoBytes.length, "video/mp4"),
                jwtAuth
        );
        String videoId = initResp.video().videoId();
        uploadFileViaPresignedUrl(initResp.upload().presignedUrl(), videoBytes, "video/mp4");

        // Lần 1: Xác nhận thành công (202 Accepted)
        ConfirmVideoResult result1 = videoService.confirmVideoUpload(videoId, jwtAuth);
        assertThat(result1.newlyConfirmed()).isTrue();

        // Purge queue để đo chính xác lần 2 có gửi thêm message không
        sqsAsyncClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build()).join();
        Thread.sleep(1000);

        // Lần 2: Xác nhận lặp lại (Idempotent 200 OK)
        ConfirmVideoResult result2 = videoService.confirmVideoUpload(videoId, jwtAuth);
        assertThat(result2.newlyConfirmed()).isFalse();
        assertThat(result2.video().status()).isEqualTo(VideoStatus.PENDING);

        // Xác minh SQS không nhận thêm message nào
        ReceiveMessageResponse receiveResp = sqsAsyncClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(2)
                .build()).join();

        assertThat(receiveResp.messages()).isEmpty();
    }

    @Test
    @DisplayName("Real Verification US2: Outbox Relay phát bù thành công khi OutboxItem đang ở trạng thái OUTBOX#PENDING")
    void testOutboxRelay_CompensatePendingEvents() throws Exception {
        String videoId = "vid_relay_test_999";
        String jobId = "job_relay999999";
        String payload = objectMapper.writeValueAsString(new SqsJobMessage(
                jobId,
                videoId,
                "prj_relay_test",
                "raw/prj_relay_test/" + videoId + "/source.mp4",
                Instant.now().toString()
        ));

        OutboxItem pendingItem = OutboxItem.builder()
                .videoId(OutboxItem.buildPk(videoId))
                .targetVideoId(videoId)
                .targetProjectId("prj_relay_test")
                .projectId(OutboxItem.GSI_PARTITION_PENDING)
                .jobId(jobId)
                .outboxStatus(OutboxItem.STATUS_PENDING)
                .createdAt(Instant.now().toString())
                .payload(payload)
                .attemptCount(0)
                .build();
        outboxRepository.save(pendingItem);

        // Kích hoạt Outbox Relay quét và phát bù
        outboxRelayScheduler.relayPendingOutboxEvents();

        // Kiểm tra OutboxItem đã chuyển sang OUTBOX#PUBLISHED
        Optional<OutboxItem> updatedOutbox = outboxRepository.findByTargetVideoId(videoId);
        assertThat(updatedOutbox).isPresent();
        assertThat(updatedOutbox.get().getOutboxStatus()).isEqualTo(OutboxItem.STATUS_PUBLISHED);
        assertThat(updatedOutbox.get().getProjectId()).isEqualTo(OutboxItem.GSI_PARTITION_PUBLISHED);
        assertThat(updatedOutbox.get().getPublishedAt()).isNotNull();

        // Kiểm tra SQS đã nhận message
        ReceiveMessageResponse receiveResp = sqsAsyncClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(3)
                .build()).join();

        var found = receiveResp.messages().stream()
                .filter(m -> m.body().contains(jobId))
                .findFirst();
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("Real Verification US3 & US4: Bắt lỗi 422 khi file chưa upload lên S3, 403 khi cross-tenant, 404 khi video không tồn tại")
    void testConfirmVideo_ErrorCases() throws Exception {
        String owner1 = "usr_alice_001";
        String owner2 = "usr_bob_002";
        Authentication authAlice = createJwtAuth(owner1);
        Authentication authBob = createJwtAuth(owner2);

        ProjectResponse project = projectService.createProject(
                new CreateProjectRequest("Alice Project", null, null),
                owner1
        );
        String projectId = project.projectId();

        InitVideoResponse initResp = videoService.initVideoUpload(
                projectId,
                new InitVideoRequest("sample", "sample.mp4", 1024L, "video/mp4"),
                authAlice
        );
        String videoId = initResp.video().videoId();
        String s3Key = "raw/" + projectId + "/" + videoId + "/source.mp4";

        // 1. Chưa upload lên S3 mà gọi confirm -> S3_OBJECT_NOT_FOUND (422)
        assertThatThrownBy(() -> videoService.confirmVideoUpload(videoId, authAlice))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.S3_OBJECT_NOT_FOUND);
                });

        // Upload file rỗng 0 byte lên S3
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(inputBucket)
                .key(s3Key)
                .build(), software.amazon.awssdk.core.sync.RequestBody.empty());

        // File 0 byte -> S3_OBJECT_NOT_FOUND (422)
        assertThatThrownBy(() -> videoService.confirmVideoUpload(videoId, authAlice))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.S3_OBJECT_NOT_FOUND);
                });

        // 2. Upload byte hợp lệ
        byte[] validBytes = "VALID_VIDEO_CONTENT".getBytes(StandardCharsets.UTF_8);
        uploadFileViaPresignedUrl(initResp.upload().presignedUrl(), validBytes, "video/mp4");

        // 3. User Bob gọi confirm video của Alice -> ACCESS_DENIED (403)
        assertThatThrownBy(() -> videoService.confirmVideoUpload(videoId, authBob))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });

        // 4. VideoId không tồn tại -> RESOURCE_NOT_FOUND (404)
        assertThatThrownBy(() -> videoService.confirmVideoUpload("vid_non_existent", authAlice))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("Real Verification US4: Hỗ trợ xác thực qua API Key của Project chứa video")
    void testConfirmVideo_WithApiKeyAuthentication() throws Exception {
        String ownerId = "usr_apikey_owner_003";
        Authentication jwtAuth = createJwtAuth(ownerId);

        ProjectResponse project = projectService.createProject(
                new CreateProjectRequest("Project with API Key", null, null),
                ownerId
        );
        String projectId = project.projectId();

        ApiKeyCreatedResponse apiKeyResp = apiKeyService.createApiKey(
                projectId,
                new CreateApiKeyRequest("Dev Key"),
                ownerId
        );

        byte[] videoBytes = "APIKEY_VIDEO_CONTENT".getBytes(StandardCharsets.UTF_8);
        InitVideoResponse initResp = videoService.initVideoUpload(
                projectId,
                new InitVideoRequest("apikey-video", "apikey-video.mp4", (long) videoBytes.length, "video/mp4"),
                jwtAuth
        );
        String videoId = initResp.video().videoId();
        uploadFileViaPresignedUrl(initResp.upload().presignedUrl(), videoBytes, "video/mp4");

        // Xác thực bằng API Key của đúng project
        ApiKeyPrincipal principal = new ApiKeyPrincipal(projectId, "Dev Key", apiKeyResp.keyPrefix());
        Authentication apiKeyAuth = new ApiKeyAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_PROJECT_API"))
        );

        ConfirmVideoResult result = videoService.confirmVideoUpload(videoId, apiKeyAuth);
        assertThat(result.newlyConfirmed()).isTrue();
        assertThat(result.video().status()).isEqualTo(VideoStatus.PENDING);
    }
}
