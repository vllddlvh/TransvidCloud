package com.transvidcloud.backend.auth.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Dịch vụ phát hành và quản lý JWT Access Token (HMAC-SHA256).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final TvcProperties tvcProperties;

    /**
     * Tạo JWT token có chữ ký HMAC-SHA256 cho người dùng.
     *
     * @param userId Định danh người dùng (sub claim)
     * @param email  Email người dùng
     * @return Chuỗi JWT đã ký
     */
    public String generateToken(String userId, String email) {
        String secret = tvcProperties.getSecurity().getJwt().getSecret();
        long expirationSeconds = tvcProperties.getSecurity().getJwt().getExpirationSeconds();

        Instant now = Instant.now();
        Instant expirationTime = now.plusSeconds(expirationSeconds);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim("email", email)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expirationTime))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);

        try {
            JWSSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            log.error("Không thể ký JWT token cho userId: {}", userId, e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Lấy thời gian sống của token tính bằng giây.
     */
    public long getExpirationSeconds() {
        return tvcProperties.getSecurity().getJwt().getExpirationSeconds();
    }
}
