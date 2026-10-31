package com.transvidcloud.backend.common.aws;

import static org.assertj.core.api.Assertions.assertThat;

import com.transvidcloud.backend.common.config.AwsConfig;
import com.transvidcloud.backend.common.config.TvcProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

class AwsConfigUnitTest {

    @Test
    @DisplayName("Khởi tạo AWS Beans thành công khi có endpoint tùy biến (LocalStack mode)")
    void shouldCreateAwsBeansWithLocalStackEndpoint() {
        TvcProperties props = new TvcProperties();
        props.getAws().setEndpoint("http://localhost:4566");
        props.getAws().getS3().setPresignEndpoint("http://localhost:4566");

        AwsConfig config = new AwsConfig(props, "ap-southeast-1", "test", "test");
        AwsCredentialsProvider credentialsProvider = config.awsCredentialsProvider();
        Region region = config.awsRegion();

        DynamoDbClient dynamoDbClient = config.dynamoDbClient(credentialsProvider, region);
        DynamoDbEnhancedClient enhancedClient = config.dynamoDbEnhancedClient(dynamoDbClient);
        S3Client s3Client = config.s3Client(credentialsProvider, region);
        S3Presigner s3Presigner = config.s3Presigner(credentialsProvider, region);
        SqsAsyncClient sqsAsyncClient = config.sqsAsyncClient(credentialsProvider, region);

        assertThat(dynamoDbClient).isNotNull();
        assertThat(enhancedClient).isNotNull();
        assertThat(s3Client).isNotNull();
        assertThat(s3Presigner).isNotNull();
        assertThat(sqsAsyncClient).isNotNull();

        dynamoDbClient.close();
        s3Client.close();
        s3Presigner.close();
        sqsAsyncClient.close();
    }

    @Test
    @DisplayName("Khởi tạo AWS Beans thành công khi không có endpoint (Cloud mode)")
    void shouldCreateAwsBeansInCloudMode() {
        TvcProperties props = new TvcProperties();
        props.getAws().setEndpoint(null);
        props.getAws().getS3().setPresignEndpoint(null);

        AwsConfig config = new AwsConfig(props, "us-east-1", "test", "test");
        AwsCredentialsProvider credentialsProvider = config.awsCredentialsProvider();
        Region region = config.awsRegion();

        DynamoDbClient dynamoDbClient = config.dynamoDbClient(credentialsProvider, region);
        DynamoDbEnhancedClient enhancedClient = config.dynamoDbEnhancedClient(dynamoDbClient);
        S3Client s3Client = config.s3Client(credentialsProvider, region);
        S3Presigner s3Presigner = config.s3Presigner(credentialsProvider, region);
        SqsAsyncClient sqsAsyncClient = config.sqsAsyncClient(credentialsProvider, region);

        assertThat(dynamoDbClient).isNotNull();
        assertThat(enhancedClient).isNotNull();
        assertThat(s3Client).isNotNull();
        assertThat(s3Presigner).isNotNull();
        assertThat(sqsAsyncClient).isNotNull();

        dynamoDbClient.close();
        s3Client.close();
        s3Presigner.close();
        sqsAsyncClient.close();
    }
}
