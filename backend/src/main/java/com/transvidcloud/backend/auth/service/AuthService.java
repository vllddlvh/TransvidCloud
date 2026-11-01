package com.transvidcloud.backend.auth.service;

import com.transvidcloud.backend.auth.dto.AuthResponse;
import com.transvidcloud.backend.auth.dto.LoginRequest;
import com.transvidcloud.backend.auth.dto.RegisterRequest;
import com.transvidcloud.backend.auth.dto.UserSummary;
import com.transvidcloud.backend.auth.entity.UserItem;
import com.transvidcloud.backend.auth.mapper.UserMapper;
import com.transvidcloud.backend.auth.repository.UserRepository;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * Nghiệp vụ xác thực người dùng SaaS (Đăng ký, Đăng nhập, Cấp JWT).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;

    /**
     * Đăng ký tài khoản người dùng SaaS mới.
     * Đảm bảo email duy nhất tuyệt đối bằng DynamoDB TransactWriteItems.
     *
     * @param request Dữ liệu đăng ký
     * @return AuthResponse chứa JWT Access Token và thông tin người dùng
     */
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String userId = "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String passwordHash = passwordEncoder.encode(request.password());
        String createdAt = Instant.now().toString();
        String fullName = request.fullName() != null && !request.fullName().isBlank()
                ? request.fullName().trim()
                : null;

        UserItem userItem = UserItem.builder()
                .userId(userId)
                .email(normalizedEmail)
                .passwordHash(passwordHash)
                .fullName(fullName)
                .createdAt(createdAt)
                .itemType(UserItem.TYPE_USER)
                .build();

        UserItem lockItem = UserItem.builder()
                .userId("EMAIL#" + normalizedEmail)
                .email(normalizedEmail)
                .targetUserId(userId)
                .createdAt(createdAt)
                .itemType(UserItem.TYPE_EMAIL_LOCK)
                .build();

        try {
            userRepository.registerUserInTransaction(userItem, lockItem);
            log.info("Đăng ký thành công tài khoản mới: userId={}, email={}", userId, normalizedEmail);
        } catch (TransactionCanceledException e) {
            log.warn("Đăng ký thất bại do email đã tồn tại: {}", normalizedEmail);
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String token = jwtTokenService.generateToken(userId, normalizedEmail);
        UserSummary summary = userMapper.toUserSummary(userItem);
        return AuthResponse.bearer(token, jwtTokenService.getExpirationSeconds(), summary);
    }

    /**
     * Đăng nhập tài khoản người dùng SaaS.
     *
     * @param request Dữ liệu đăng nhập
     * @return AuthResponse chứa JWT Access Token và thông tin người dùng
     */
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        UserItem userItem = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("Đăng nhập thất bại: email không tồn tại {}", normalizedEmail);
                    return new AppException(ErrorCode.INVALID_CREDENTIALS);
                });

        if (!passwordEncoder.matches(request.password(), userItem.getPasswordHash())) {
            log.warn("Đăng nhập thất bại: mật khẩu không khớp cho email {}", normalizedEmail);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        log.info("Đăng nhập thành công: userId={}, email={}", userItem.getUserId(), normalizedEmail);
        String token = jwtTokenService.generateToken(userItem.getUserId(), normalizedEmail);
        UserSummary summary = userMapper.toUserSummary(userItem);
        return AuthResponse.bearer(token, jwtTokenService.getExpirationSeconds(), summary);
    }

    /**
     * Lấy thông tin tài khoản người dùng theo userId.
     *
     * @param userId Định danh người dùng
     * @return UserSummary
     */
    public UserSummary getCurrentUser(String userId) {
        UserItem userItem = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        return userMapper.toUserSummary(userItem);
    }
}
