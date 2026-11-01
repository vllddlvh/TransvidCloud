package com.transvidcloud.backend.common.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;

/**
 * Cấu hình khởi tạo các Beans AWS SDK v2 dùng chung.
 * Tương thích linh hoạt giữa môi trường cục bộ (LocalStack) và môi trường Cloud.
 */
@Configuration
public class AwsConfig {

    private final TvcProperties tvcProperties;
    private final String regionStr;
    private final String accessKey;
    private final String secretKey;

    public AwsConfig(
            TvcProperties tvcProperties,
            @Value("${spring.cloud.aws.region.static:ap-southeast-1}") String regionStr,
            @Value("${spring.cloud.aws.credentials.access-key:test}") String accessKey,
            @Value("${spring.cloud.aws.credentials.secret-key:test}") String secretKey
    ) {
        this.tvcProperties = tvcProperties;
        this.regionStr = regionStr;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Bean
    @ConditionalOnMissingBean
    public AwsCredentialsProvider awsCredentialsProvider() {
        if (accessKey != null && !accessKey.isBlank() && !"test".equalsIgnoreCase(accessKey)) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        if ("test".equalsIgnoreCase(accessKey)) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean
    @ConditionalOnMissingBean
    public Region awsRegion() {
        return Region.of(regionStr != null && !regionStr.isBlank() ? regionStr : "ap-southeast-1");
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamoDbClient dynamoDbClient(AwsCredentialsProvider credentialsProvider, Region region) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider);

        String endpoint = tvcProperties.getAws().getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Client s3Client(AwsCredentialsProvider credentialsProvider, Region region) {
        S3ClientBuilder builder = S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider);

        String endpoint = tvcProperties.getAws().getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true);
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Presigner s3Presigner(AwsCredentialsProvider credentialsProvider, Region region) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider);

        String presignEndpoint = tvcProperties.getAws().getS3().getPresignEndpoint();
        if (presignEndpoint == null || presignEndpoint.isBlank()) {
            presignEndpoint = tvcProperties.getAws().getEndpoint();
        }

        if (presignEndpoint != null && !presignEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(presignEndpoint))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build());
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public SqsAsyncClient sqsAsyncClient(AwsCredentialsProvider credentialsProvider, Region region) {
        SqsAsyncClientBuilder builder = SqsAsyncClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider);

        String endpoint = tvcProperties.getAws().getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }
}
