package com.transvidcloud.backend.common.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback Message Converter tự động phát hiện và che giấu (masking) các thông tin nhạy cảm
 * (mật khẩu, raw API Key, token JWT, AWS credentials, chữ ký tạm thời của S3 Presigned URL)
 * trước khi xuất ra stream log.
 */
public class MaskingMessageConverter extends MessageConverter {

    private static final Pattern PASSWORD_JSON_PATTERN =
            Pattern.compile("(?i)(\"password\"\\s*:\\s*\")([^\"]+)(\")");
    private static final Pattern PASSWORD_KV_PATTERN =
            Pattern.compile("(?i)(password\\s*=\\s*)([^\\s,\\]\\}]+)");

    private static final Pattern API_KEY_JSON_PATTERN =
            Pattern.compile("(?i)(\"rawApiKey\"\\s*:\\s*\")([^\"]+)(\")");
    private static final Pattern API_KEY_KV_PATTERN =
            Pattern.compile("(?i)(rawApiKey\\s*=\\s*)([^\\s,\\]\\}]+)");

    private static final Pattern BEARER_TOKEN_PATTERN =
            Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9\\-_.~+/]+=*");

    private static final Pattern S3_SIGNATURE_PATTERN =
            Pattern.compile("(https?://[^?\\s\\n\\r]+\\?[^\\s\\n\\r]*X-Amz-Signature=)[^&\\s\\n\\r]+");
    private static final Pattern S3_SECURITY_TOKEN_PATTERN =
            Pattern.compile("(https?://[^?\\s\\n\\r]+\\?[^\\s\\n\\r]*X-Amz-Security-Token=)[^&\\s\\n\\r]+");

    private static final Pattern AWS_SECRET_KV_PATTERN =
            Pattern.compile("(?i)(aws_secret_access_key\\s*=\\s*|secretKey\\s*=\\s*)([^\\s,\\]\\}]+)");
    private static final Pattern AWS_SECRET_JSON_PATTERN =
            Pattern.compile("(?i)(\"secretKey\"\\s*:\\s*\")([^\"]+)(\")");

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        return mask(message);
    }

    /**
     * Hàm tiện ích thực hiện lọc và thay thế chuỗi nhạy cảm.
     *
     * @param input Chuỗi log đầu vào
     * @return Chuỗi log đã được che giấu dữ liệu nhạy cảm
     */
    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String masked = input;

        // 1. Masking Password (JSON & Key-Value)
        masked = PASSWORD_JSON_PATTERN.matcher(masked).replaceAll("$1[PROTECTED]$3");
        masked = PASSWORD_KV_PATTERN.matcher(masked).replaceAll("$1[PROTECTED]");

        // 2. Masking Raw API Key (JSON & Key-Value)
        masked = API_KEY_JSON_PATTERN.matcher(masked).replaceAll("$1[PROTECTED]$3");
        masked = API_KEY_KV_PATTERN.matcher(masked).replaceAll("$1[PROTECTED]");

        // 3. Masking Bearer Token
        masked = BEARER_TOKEN_PATTERN.matcher(masked).replaceAll("$1[PROTECTED]");

        // 4. Masking S3 Presigned URL Signatures & Tokens
        masked = S3_SIGNATURE_PATTERN.matcher(masked).replaceAll("$1[PROTECTED]");
        masked = S3_SECURITY_TOKEN_PATTERN.matcher(masked).replaceAll("$1[PROTECTED]");

        // 5. Masking AWS Secret Credentials
        masked = AWS_SECRET_KV_PATTERN.matcher(masked).replaceAll("$1[PROTECTED]");
        masked = AWS_SECRET_JSON_PATTERN.matcher(masked).replaceAll("$1[PROTECTED]$3");

        return masked;
    }
}
