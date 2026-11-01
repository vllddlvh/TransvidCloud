package com.transvidcloud.backend.auth.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transvidcloud.backend.auth.dto.AuthResponse;
import com.transvidcloud.backend.auth.dto.LoginRequest;
import com.transvidcloud.backend.auth.dto.RegisterRequest;
import com.transvidcloud.backend.auth.dto.UserSummary;
import com.transvidcloud.backend.auth.service.AuthService;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Web Slice Test")
class AuthControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /auth/register thành công trả về 201 Created và AuthResponse")
    void register_success() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "Password123!", "Nguyen Van A");
        UserSummary summary = new UserSummary("usr_123", "user@example.com", "Nguyen Van A", "2026-10-31T00:00:00Z");
        AuthResponse response = AuthResponse.bearer("jwt.token.mock", 86400L, summary);

        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", is("jwt.token.mock")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresInSeconds", is(86400)))
                .andExpect(jsonPath("$.user.userId", is("usr_123")))
                .andExpect(jsonPath("$.user.email", is("user@example.com")));
    }

    @Test
    @DisplayName("POST /auth/register với email không hợp lệ trả về 400 Bad Request")
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "Password123!", "Name");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details", notNullValue()));
    }

    @Test
    @DisplayName("POST /auth/register với mật khẩu ngắn hơn 8 ký tự trả về 400 Bad Request")
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "short", "Name");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("POST /auth/register trùng email trả về 409 Conflict")
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "Password123!", "Name");

        when(authService.register(any())).thenThrow(new AppException(ErrorCode.EMAIL_ALREADY_EXISTS));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("EMAIL_ALREADY_EXISTS")));
    }

    @Test
    @DisplayName("POST /auth/login thành công trả về 200 OK và AuthResponse")
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "Password123!");
        UserSummary summary = new UserSummary("usr_123", "user@example.com", "Nguyen Van A", "2026-10-31T00:00:00Z");
        AuthResponse response = AuthResponse.bearer("jwt.token.mock", 86400L, summary);

        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("jwt.token.mock")))
                .andExpect(jsonPath("$.user.email", is("user@example.com")));
    }

    @Test
    @DisplayName("POST /auth/login thiếu email hoặc password trả về 400 Bad Request")
    void login_missingFields_returns400() throws Exception {
        LoginRequest request = new LoginRequest("", "");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("POST /auth/login sai thông tin đăng nhập trả về 401 Unauthorized")
    void login_badCredentials_returns401() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword!");

        when(authService.login(any())).thenThrow(new AppException(ErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")));
    }
}
