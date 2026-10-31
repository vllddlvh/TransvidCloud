package com.transvidcloud.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TvcPropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(TvcProperties.class)
    static class TestConfig {}

    @Test
    @DisplayName("Nạp cấu hình hợp lệ thành công với các giá trị mặc định")
    void shouldLoadValidPropertiesSuccessfully() {
        runner.withPropertyValues(
                "tvc.security.jwt.secret=this-is-a-valid-secret-key-that-is-at-least-32-bytes-long",
                "tvc.aws.endpoint=http://localhost:4566",
                "tvc.aws.s3.input-bucket=tvc-test-input",
                "tvc.aws.s3.output-bucket=tvc-test-output",
                "tvc.aws.sqs.jobs-queue-name=tvc-test-jobs",
                "tvc.aws.dynamodb.table-prefix=tvc-test-"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            TvcProperties props = context.getBean(TvcProperties.class);
            assertThat(props.getAws().getEndpoint()).isEqualTo("http://localhost:4566");
            assertThat(props.getAws().getS3().getInputBucket()).isEqualTo("tvc-test-input");
            assertThat(props.getAws().getS3().getPresignTtlMinutes()).isEqualTo(15);
            assertThat(props.getUpload().getMaxBytes()).isEqualTo(209715200L);
            assertThat(props.getSecurity().getJwt().getSecret()).hasSizeGreaterThanOrEqualTo(32);
            assertThat(props.getSecurity().getCors().getAllowedOrigins()).contains("http://localhost:5173");
        });
    }

    @Test
    @DisplayName("Fail-fast khi jwt.secret quá ngắn (dưới 32 ký tự)")
    void shouldFailWhenJwtSecretIsTooShort() {
        runner.withPropertyValues(
                "tvc.security.jwt.secret=too-short-secret"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("JWT secret must be at least 32 bytes long for HMAC-SHA256");
        });
    }

    @Test
    @DisplayName("Fail-fast khi presign-ttl-minutes nhỏ hơn hoặc bằng 0")
    void shouldFailWhenPresignTtlIsInvalid() {
        runner.withPropertyValues(
                "tvc.security.jwt.secret=this-is-a-valid-secret-key-that-is-at-least-32-bytes-long",
                "tvc.aws.s3.presign-ttl-minutes=0"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("presignTtlMinutes");
        });
    }

    @Test
    @DisplayName("Fail-fast khi upload.max-bytes nhỏ hơn hoặc bằng 0")
    void shouldFailWhenMaxBytesIsInvalid() {
        runner.withPropertyValues(
                "tvc.security.jwt.secret=this-is-a-valid-secret-key-that-is-at-least-32-bytes-long",
                "tvc.upload.max-bytes=0"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("maxBytes");
        });
    }
}
