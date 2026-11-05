package com.transvidcloud.backend.auth.controller;

import com.transvidcloud.backend.auth.dto.AuthResponse;
import com.transvidcloud.backend.auth.dto.LoginRequest;
import com.transvidcloud.backend.auth.dto.RegisterRequest;
import com.transvidcloud.backend.auth.dto.UserSummary;
import com.transvidcloud.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho các tác vụ xác thực tài khoản SaaS.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Đăng ký tài khoản SaaS mới.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Đăng nhập và nhận JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy thông tin tài khoản của phiên đăng nhập hiện tại (yêu cầu Bearer Token).
     */
    @GetMapping("/me")
    public ResponseEntity<UserSummary> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        UserSummary userSummary = authService.getCurrentUser(userId);
        return ResponseEntity.ok(userSummary);
    }
}
