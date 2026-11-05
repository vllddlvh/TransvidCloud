package com.transvidcloud.backend.apikey.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Tiện ích sinh chuỗi bí mật API Key và băm mật mã an toàn.
 */
public final class ApiKeyGenerator {

    public static final String PREFIX = "tvc_live_";
    private static final int RANDOM_BYTES_COUNT = 16; // 128 bits -> 32 hex characters
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private ApiKeyGenerator() {
        // Utility class
    }

    /**
     * Sinh ngẫu nhiên chuỗi rawKey bảo mật cao có dạng tvc_live_<32_hex_chars> (tổng chiều dài 41 ký tự).
     */
    public static String generateRawKey() {
        byte[] randomBytes = new byte[RANDOM_BYTES_COUNT];
        SECURE_RANDOM.nextBytes(randomBytes);
        return PREFIX + HEX_FORMAT.formatHex(randomBytes);
    }

    /**
     * Tính toán mã băm mật mã một chiều SHA-256 của chuỗi rawKey (ra chuỗi 64 hex characters).
     */
    public static String sha256Hex(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalArgumentException("API Key must not be null or blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HEX_FORMAT.formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Trích xuất keyPrefix hiển thị an toàn trên giao diện: tvc_live_ + 8 ký tự hex đầu + ...
     * Ví dụ: tvc_live_7f9a8b2c...
     */
    public static String extractKeyPrefix(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid raw API key format");
        }
        // PREFIX length is 9. Take next 8 chars.
        if (rawKey.length() < PREFIX.length() + 8) {
            throw new IllegalArgumentException("Raw API key length is too short");
        }
        return rawKey.substring(0, PREFIX.length() + 8) + "...";
    }
}
