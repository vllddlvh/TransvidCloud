package com.transvidcloud.backend.auth.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.transvidcloud.backend.auth.entity.UserItem;
import com.transvidcloud.backend.auth.repository.UserRepository;
import com.transvidcloud.backend.auth.service.JwtTokenService;
import com.transvidcloud.backend.common.config.TvcProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SecurityAuthFilter Component Test - Xác thực Bearer Token")
class SecurityAuthFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private TvcProperties tvcProperties;

    @MockBean
    private UserRepository userRepository;

    @Test
    @DisplayName("Truy cập /auth/me không có Bearer token -> bị chặn 401 Unauthorized")
    void accessMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Truy cập /auth/me với Bearer token hợp lệ -> thành công 200 OK")
    void accessMe_withValidToken_returns200() throws Exception {
        String userId = "usr_sec_123456";
        String email = "sec-user@example.com";
        String token = jwtTokenService.generateToken(userId, email);

        UserItem userItem = UserItem.builder()
                .userId(userId)
                .email(email)
                .fullName("Security Tester")
                .createdAt("2026-10-31T00:00:00Z")
                .itemType(UserItem.TYPE_USER)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(userItem));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(userId)))
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.fullName", is("Security Tester")));
    }

    @Test
    @DisplayName("Truy cập /auth/me với token sai chữ ký số -> bị chặn 401 Unauthorized")
    void accessMe_withInvalidSignatureToken_returns401() throws Exception {
        // Ký bằng một secret khác
        String wrongSecret = "wrong-secret-key-must-be-at-least-32-bytes-long!";
        JWSSigner signer = new MACSigner(wrongSecret.getBytes(StandardCharsets.UTF_8));

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("usr_fake")
                .claim("email", "fake@example.com")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(signer);
        String invalidToken = signedJWT.serialize();

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Truy cập /auth/me với token đã hết hạn -> bị chặn 401 Unauthorized")
    void accessMe_withExpiredToken_returns401() throws Exception {
        String secret = tvcProperties.getSecurity().getJwt().getSecret();
        JWSSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));

        Instant past = Instant.now().minusSeconds(3600);
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("usr_expired")
                .claim("email", "expired@example.com")
                .issueTime(Date.from(past.minusSeconds(3600)))
                .expirationTime(Date.from(past))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(signer);
        String expiredToken = signedJWT.serialize();

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Truy cập /auth/register và /auth/login không cần token -> không bị chặn 401 tại Security Filter")
    void publicEndpoints_doNotRequireToken() throws Exception {
        // Gửi body rỗng -> bị từ chối 400 Bad Request từ Controller validation, KHÔNG phải 401 Unauthorized từ Security Filter
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
