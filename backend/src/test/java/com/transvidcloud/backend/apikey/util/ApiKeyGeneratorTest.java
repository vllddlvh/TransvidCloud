package com.transvidcloud.backend.apikey.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiKeyGeneratorTest {

    @Test
    @DisplayName("generateRawKey() sinh chuỗi có tiền tố tvc_live_ và độ dài đúng 41 ký tự")
    void testGenerateRawKeyFormat() {
        String rawKey = ApiKeyGenerator.generateRawKey();

        assertThat(rawKey).isNotNull();
        assertThat(rawKey).startsWith(ApiKeyGenerator.PREFIX);
        assertThat(rawKey).hasSize(41); // 9 chars prefix + 32 hex chars
        assertThat(rawKey.substring(ApiKeyGenerator.PREFIX.length())).matches("^[0-9a-f]{32}$");
    }

    @Test
    @DisplayName("generateRawKey() sinh các chuỗi độc lập không trùng lặp (CSPRNG entropy)")
    void testGenerateRawKeyUniqueness() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            keys.add(ApiKeyGenerator.generateRawKey());
        }
        assertThat(keys).hasSize(1000);
    }

    @Test
    @DisplayName("sha256Hex() tính đúng chuỗi băm SHA-256 64 ký tự hex")
    void testSha256Hex() {
        String testKey = "tvc_live_7f9a8b2c4d6e1f3a5b7c9d0e2f4a6b8c";
        String hash1 = ApiKeyGenerator.sha256Hex(testKey);
        String hash2 = ApiKeyGenerator.sha256Hex(testKey);

        assertThat(hash1).isNotNull().hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("sha256Hex() ném ngoại lệ khi rawKey null hoặc blank")
    void testSha256HexInvalidInput() {
        assertThatThrownBy(() -> ApiKeyGenerator.sha256Hex(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApiKeyGenerator.sha256Hex("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("extractKeyPrefix() trích xuất đúng định dạng tvc_live_xxxxxxxx...")
    void testExtractKeyPrefix() {
        String rawKey = "tvc_live_7f9a8b2c4d6e1f3a5b7c9d0e2f4a6b8c";
        String prefix = ApiKeyGenerator.extractKeyPrefix(rawKey);

        assertThat(prefix).isEqualTo("tvc_live_7f9a8b2c...");
    }

    @Test
    @DisplayName("extractKeyPrefix() ném ngoại lệ khi rawKey không có tiền tố tvc_live_")
    void testExtractKeyPrefixInvalid() {
        assertThatThrownBy(() -> ApiKeyGenerator.extractKeyPrefix("invalid_key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApiKeyGenerator.extractKeyPrefix(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
