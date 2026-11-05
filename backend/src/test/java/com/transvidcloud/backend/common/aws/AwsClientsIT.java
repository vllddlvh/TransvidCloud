package com.transvidcloud.backend.common.aws;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AwsClientsIT {

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
    private DynamoDbEnhancedClient enhancedClient;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    @Autowired
    private SqsAsyncClient sqsAsyncClient;

    @Test
    @DisplayName("DynamoDbClient & EnhancedClient: Tạo bảng và đọc ghi item thành công trên LocalStack")
    void shouldInteractWithDynamoDb() {
        String tableName = "tvc-test-items";

        dynamoDbClient.createTable(CreateTableRequest.builder()
                .tableName(tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName("id")
                        .attributeType(ScalarAttributeType.S)
                        .build())
                .build());

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(Map.of(
                        "id", AttributeValue.builder().s("item-001").build(),
                        "name", AttributeValue.builder().s("Test Item").build()
                ))
                .build());

        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.builder().s("item-001").build()))
                .build());

        assertThat(response.hasItem()).isTrue();
        assertThat(response.item().get("name").s()).isEqualTo("Test Item");
        assertThat(enhancedClient).isNotNull();
    }

    @Test
    @DisplayName("S3Client: Tạo bucket, put và get object thành công với path-style access")
    void shouldPutAndGetObjectInS3() throws Exception {
        String bucket = "tvc-test-s3";
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());

        byte[] payload = "TransvidCloud S3 Test Content".getBytes(StandardCharsets.UTF_8);
        PutObjectResponse putResponse = s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key("sample.txt").contentType("text/plain").build(),
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(payload)
        );
        assertThat(putResponse.eTag()).isNotBlank();

        byte[] downloaded = s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key("sample.txt").build()
        ).asByteArray();

        assertThat(downloaded).isEqualTo(payload);
    }

    @Test
    @DisplayName("S3Presigner: Ký Presigned PUT URL và upload file qua HTTP thành công")
    void shouldGeneratePresignedPutUrlAndUpload() throws Exception {
        String bucket = "tvc-presign-test";
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key("video-source.mp4")
                        .contentType("video/mp4")
                        .build())
                .build());

        URI presignedUri = presigned.url().toURI();
        assertThat(presignedUri.toString()).contains(bucket);

        byte[] videoPayload = new byte[]{0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70}; // ftyp header
        HttpURLConnection connection = (HttpURLConnection) presignedUri.toURL().openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Content-Type", "video/mp4");
        try (OutputStream os = connection.getOutputStream()) {
            os.write(videoPayload);
        }

        int responseCode = connection.getResponseCode();
        assertThat(responseCode).isIn(200, 204);

        HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key("video-source.mp4")
                .build());

        assertThat(head.contentLength()).isEqualTo((long) videoPayload.length);
        assertThat(head.contentType()).isEqualTo("video/mp4");
    }

    @Test
    @DisplayName("SqsAsyncClient: Tạo queue, gửi và nhận thông điệp bất đồng bộ thành công")
    void shouldSendAndReceiveMessageInSqs() throws Exception {
        String queueName = "tvc-jobs-test";
        CreateQueueResponse createQueue = sqsAsyncClient.createQueue(CreateQueueRequest.builder()
                .queueName(queueName)
                .build()).get();

        String queueUrl = createQueue.queueUrl();
        assertThat(queueUrl).isNotBlank();

        String messageBody = "{\"jobId\":\"job-test-001\",\"videoId\":\"vid-123\"}";
        SendMessageResponse send = sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build()).get();

        assertThat(send.messageId()).isNotBlank();

        ReceiveMessageResponse receive = sqsAsyncClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(2)
                .build()).get();

        assertThat(receive.messages()).hasSize(1);
        assertThat(receive.messages().get(0).body()).isEqualTo(messageBody);
    }
}
