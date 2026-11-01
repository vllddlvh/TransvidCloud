package com.transvidcloud.backend.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test: MaskingMessageConverter")
class MaskingMessageConverterTest {

    @Test
    @DisplayName("Che giấu mật khẩu trong chuỗi JSON")
    void shouldMaskPasswordInJson() {
        String input = "{\"email\":\"admin@transvidcloud.com\",\"password\":\"mySuperSecretPass123!\"}";
        String masked = MaskingMessageConverter.mask(input);

        assertThat(masked).contains("\"password\":\"[PROTECTED]\"");
        assertThat(masked).doesNotContain("mySuperSecretPass123!");
    }

    @Test
    @DisplayName("Che giấu mật khẩu trong chuỗi key-value thông thường")
    void shouldMaskPasswordInKeyValue() {
        String input = "Authentication attempt with password=PlainTextPassword999 for user=admin";
        String masked = MaskingMessageConverter.mask(input);

        assertThat(masked).contains("password=[PROTECTED]");
        assertThat(masked).doesNotContain("PlainTextPassword999");
    }

    @Test
    @DisplayName("Che giấu rawApiKey trong JSON và key-value")
    void shouldMaskRawApiKey() {
        String inputJson = "{\"name\":\"Default Key\",\"rawApiKey\":\"tvc_live_9876543210abcdef\"}";
        String maskedJson = MaskingMessageConverter.mask(inputJson);

        assertThat(maskedJson).contains("\"rawApiKey\":\"[PROTECTED]\"");
        assertThat(maskedJson).doesNotContain("tvc_live_9876543210abcdef");

        String inputKv = "Generated key: rawApiKey=tvc_live_9876543210abcdef";
        String maskedKv = MaskingMessageConverter.mask(inputKv);
        assertThat(maskedKv).contains("rawApiKey=[PROTECTED]");
        assertThat(maskedKv).doesNotContain("tvc_live_9876543210abcdef");
    }

    @Test
    @DisplayName("Che giấu Bearer JWT Token")
    void shouldMaskBearerToken() {
        String input = "Outgoing request with Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.ABC-123_xyz";
        String masked = MaskingMessageConverter.mask(input);

        assertThat(masked).contains("Bearer [PROTECTED]");
        assertThat(masked).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
    }

    @Test
    @DisplayName("Che giấu query string chữ ký của S3 Presigned URL (X-Amz-Signature)")
    void shouldMaskS3PresignedUrlSignature() {
        String url = "https://tvc-local-input.s3.localhost:4566/prj_123/vid_456/source.mp4"
                + "?X-Amz-Algorithm=AWS4-HMAC-SHA256"
                + "&X-Amz-Credential=test%2F20261101%2Fap-southeast-1%2Fs3%2Faws4_request"
                + "&X-Amz-Date=20261101T170000Z"
                + "&X-Amz-Expires=900"
                + "&X-Amz-SignedHeaders=host"
                + "&X-Amz-Signature=d41d8cd98f00b204e9800998ecf8427e998877665544332211";

        String input = "Generated S3 presigned URL: " + url;
        String masked = MaskingMessageConverter.mask(input);

        assertThat(masked).contains("X-Amz-Signature=[PROTECTED]");
        assertThat(masked).doesNotContain("d41d8cd98f00b204e9800998ecf8427e998877665544332211");
    }

    @Test
    @DisplayName("Che giấu AWS secret credentials trong chuỗi cấu hình")
    void shouldMaskAwsSecretCredentials() {
        String input = "Configured client with aws_secret_access_key=superSecretAwsKey123 and secretKey=mySecretKey";
        String masked = MaskingMessageConverter.mask(input);

        assertThat(masked).contains("aws_secret_access_key=[PROTECTED]");
        assertThat(masked).contains("secretKey=[PROTECTED]");
        assertThat(masked).doesNotContain("superSecretAwsKey123");
        assertThat(masked).doesNotContain("mySecretKey");
    }

    @Test
    @DisplayName("Giữ nguyên các chuỗi log thông thường không chứa thông tin nhạy cảm")
    void shouldPreserveNormalLogMessages() {
        String normal = "User logged in successfully with email=user@transvidcloud.com and role=USER";
        assertThat(MaskingMessageConverter.mask(normal)).isEqualTo(normal);
    }

    @Test
    @DisplayName("Xử lý an toàn với chuỗi null hoặc rỗng")
    void shouldHandleNullOrEmptySafely() {
        assertThat(MaskingMessageConverter.mask(null)).isNull();
        assertThat(MaskingMessageConverter.mask("")).isEmpty();
    }
}
