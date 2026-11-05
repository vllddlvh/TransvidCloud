package com.transvidcloud.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transvidcloud.backend.auth.dto.AuthResponse;
import com.transvidcloud.backend.auth.dto.LoginRequest;
import com.transvidcloud.backend.auth.dto.RegisterRequest;
import com.transvidcloud.backend.auth.dto.UserSummary;
import com.transvidcloud.backend.auth.entity.UserItem;
import com.transvidcloud.backend.auth.mapper.UserMapper;
import com.transvidcloud.backend.auth.repository.UserRepository;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Test")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private UserMapper userMapper;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenService, userMapper);
    }

    @Test
    @DisplayName("Đăng ký thành công: chuẩn hóa email, băm mật khẩu, ghi transaction và trả về token")
    void register_success() {
        RegisterRequest request = new RegisterRequest("  User@Example.Com  ", "Secret123!", "Nguyen Van A");

        when(passwordEncoder.encode("Secret123!")).thenReturn("$2a$10$hashedPassword");
        when(jwtTokenService.generateToken(any(), eq("user@example.com"))).thenReturn("mocked.jwt.token");
        when(jwtTokenService.getExpirationSeconds()).thenReturn(86400L);

        UserSummary mockSummary = new UserSummary("usr_123", "user@example.com", "Nguyen Van A", "2026-10-31T00:00:00Z");
        when(userMapper.toUserSummary(any())).thenReturn(mockSummary);

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("mocked.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(86400L);
        assertThat(response.user().email()).isEqualTo("user@example.com");

        verify(userRepository).registerUserInTransaction(
                argThat(item -> item.getEmail().equals("user@example.com")
                        && item.getPasswordHash().equals("$2a$10$hashedPassword")
                        && item.getFullName().equals("Nguyen Van A")
                        && item.getUserId().startsWith("usr_")),
                argThat(lock -> lock.getUserId().equals("EMAIL#user@example.com")
                        && lock.getEmail().equals("user@example.com"))
        );
    }

    @Test
    @DisplayName("Đăng ký thất bại khi email đã tồn tại: ném AppException(EMAIL_ALREADY_EXISTS)")
    void register_duplicateEmail_throwsConflict() {
        RegisterRequest request = new RegisterRequest("user@example.com", "Secret123!", null);

        when(passwordEncoder.encode("Secret123!")).thenReturn("$2a$10$hashed");
        doThrow(TransactionCanceledException.builder().message("ConditionalCheckFailed").build())
                .when(userRepository).registerUserInTransaction(any(), any());

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("Đăng nhập thành công với email và mật khẩu đúng")
    void login_success() {
        LoginRequest request = new LoginRequest("User@Example.com", "Secret123!");
        UserItem userItem = UserItem.builder()
                .userId("usr_abc")
                .email("user@example.com")
                .passwordHash("$2a$10$hashed")
                .fullName("Test User")
                .itemType(UserItem.TYPE_USER)
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(userItem));
        when(passwordEncoder.matches("Secret123!", "$2a$10$hashed")).thenReturn(true);
        when(jwtTokenService.generateToken("usr_abc", "user@example.com")).thenReturn("mocked.login.jwt");
        when(jwtTokenService.getExpirationSeconds()).thenReturn(86400L);

        UserSummary mockSummary = new UserSummary("usr_abc", "user@example.com", "Test User", "2026-10-31T00:00:00Z");
        when(userMapper.toUserSummary(userItem)).thenReturn(mockSummary);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("mocked.login.jwt");
        assertThat(response.user().userId()).isEqualTo("usr_abc");
    }

    @Test
    @DisplayName("Đăng nhập thất bại khi email không tồn tại: ném AppException(INVALID_CREDENTIALS)")
    void login_emailNotFound_throwsInvalidCredentials() {
        LoginRequest request = new LoginRequest("unknown@example.com", "Secret123!");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("Đăng nhập thất bại khi mật khẩu không khớp: ném AppException(INVALID_CREDENTIALS)")
    void login_wrongPassword_throwsInvalidCredentials() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword!");
        UserItem userItem = UserItem.builder()
                .userId("usr_abc")
                .email("user@example.com")
                .passwordHash("$2a$10$hashed")
                .itemType(UserItem.TYPE_USER)
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(userItem));
        when(passwordEncoder.matches("WrongPassword!", "$2a$10$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("Lấy thông tin tài khoản thành công theo userId")
    void getCurrentUser_success() {
        UserItem userItem = UserItem.builder()
                .userId("usr_abc")
                .email("user@example.com")
                .fullName("Test User")
                .itemType(UserItem.TYPE_USER)
                .build();
        UserSummary summary = new UserSummary("usr_abc", "user@example.com", "Test User", "2026-10-31T00:00:00Z");

        when(userRepository.findById("usr_abc")).thenReturn(Optional.of(userItem));
        when(userMapper.toUserSummary(userItem)).thenReturn(summary);

        UserSummary result = authService.getCurrentUser("usr_abc");
        assertThat(result.userId()).isEqualTo("usr_abc");
        assertThat(result.email()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("Lấy thông tin tài khoản thất bại khi không tìm thấy: ném NOT_FOUND")
    void getCurrentUser_notFound_throwsNotFound() {
        when(userRepository.findById("usr_none")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser("usr_none"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }
}
