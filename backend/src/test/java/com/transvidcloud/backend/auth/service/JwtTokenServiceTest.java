package com.transvidcloud.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.transvidcloud.backend.common.config.TvcProperties;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@DisplayName("JwtTokenService Unit Test")
class JwtTokenServiceTest {

    private static final String TEST_SECRET = "test-secret-must-be-at-least-32-bytes-long-123456";
    private static final long EXPIRATION_SECONDS = 3600L;

    private JwtTokenService jwtTokenService;
    private NimbusJwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        TvcProperties properties = new TvcProperties();
        properties.getSecurity().getJwt().setSecret(TEST_SECRET);
        properties.getSecurity().getJwt().setExpirationSeconds(EXPIRATION_SECONDS);

        jwtTokenService = new JwtTokenService(properties);

        SecretKey key = new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        jwtDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Test
    @DisplayName("Phát hành token JWT hợp lệ với claims sub, email và thời gian hết hạn")
    void shouldGenerateValidJwtToken() {
        String userId = "usr_123456789abc";
        String email = "test@example.com";

        String token = jwtTokenService.generateToken(userId, email);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);

        Jwt decodedJwt = jwtDecoder.decode(token);
        assertThat(decodedJwt.getSubject()).isEqualTo(userId);
        assertThat(decodedJwt.getClaimAsString("email")).isEqualTo(email);
        assertThat(decodedJwt.getExpiresAt()).isNotNull();
        assertThat(decodedJwt.getIssuedAt()).isNotNull();

        long diffSeconds = decodedJwt.getExpiresAt().getEpochSecond() - decodedJwt.getIssuedAt().getEpochSecond();
        assertThat(diffSeconds).isEqualTo(EXPIRATION_SECONDS);
    }

    @Test
    @DisplayName("Trả về đúng expirationSeconds đã cấu hình")
    void shouldReturnConfiguredExpirationSeconds() {
        assertThat(jwtTokenService.getExpirationSeconds()).isEqualTo(EXPIRATION_SECONDS);
    }
}
