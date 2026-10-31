package com.transvidcloud.backend.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình tập trung cho TransvidCloud Backend.
 * Ánh xạ toàn bộ tham số dưới tiền tố "tvc" từ application.yml / biến môi trường.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "tvc")
public class TvcProperties {

    @Valid
    @NotNull
    private AwsProperties aws = new AwsProperties();

    @Valid
    @NotNull
    private UploadProperties upload = new UploadProperties();

    @Valid
    @NotNull
    private SecurityProperties security = new SecurityProperties();

    @Valid
    @NotNull
    private MediaProperties media = new MediaProperties();

    @Getter
    @Setter
    public static class AwsProperties {
        private String endpoint;

        @Valid
        @NotNull
        private S3Properties s3 = new S3Properties();

        @Valid
        @NotNull
        private SqsProperties sqs = new SqsProperties();

        @Valid
        @NotNull
        private DynamoDbProperties dynamodb = new DynamoDbProperties();
    }

    @Getter
    @Setter
    public static class S3Properties {
        @NotBlank
        private String inputBucket = "tvc-local-input";

        @NotBlank
        private String outputBucket = "tvc-local-output";

        private String presignEndpoint;

        @Min(1)
        private int presignTtlMinutes = 15;
    }

    @Getter
    @Setter
    public static class SqsProperties {
        @NotBlank
        private String jobsQueueName = "tvc-local-jobs";
    }

    @Getter
    @Setter
    public static class DynamoDbProperties {
        @NotBlank
        private String tablePrefix = "tvc-local-";
    }

    @Getter
    @Setter
    public static class UploadProperties {
        @Min(1)
        private long maxBytes = 209715200L;

        @NotEmpty
        private List<String> allowedContentTypes = new ArrayList<>(List.of(
                "video/mp4",
                "video/quicktime",
                "video/x-matroska",
                "video/webm"
        ));
    }

    @Getter
    @Setter
    public static class SecurityProperties {
        @Valid
        @NotNull
        private JwtProperties jwt = new JwtProperties();

        @Valid
        @NotNull
        private CorsProperties cors = new CorsProperties();
    }

    @Getter
    @Setter
    public static class JwtProperties {
        @NotBlank
        @Size(min = 32, message = "JWT secret must be at least 32 bytes long for HMAC-SHA256")
        private String secret = "dev-only-change-me-must-be-at-least-32-bytes-long";

        @Min(60)
        private long expirationSeconds = 86400L;
    }

    @Getter
    @Setter
    public static class CorsProperties {
        @NotEmpty
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:5173",
                "http://localhost:8000"
        ));
    }

    @Getter
    @Setter
    public static class MediaProperties {
        @NotBlank
        private String baseUrl = "/media";
    }
}
