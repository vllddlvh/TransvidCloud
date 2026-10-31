package com.transvidcloud.backend.apikey.controller;

import com.transvidcloud.backend.apikey.dto.ApiKeyCreatedResponse;
import com.transvidcloud.backend.apikey.dto.ApiKeyListResponse;
import com.transvidcloud.backend.apikey.dto.CreateApiKeyRequest;
import com.transvidcloud.backend.apikey.service.ApiKeyService;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho các tác vụ quản lý API Key:
 * - Tạo mới API Key cho Project
 * - Liệt kê danh sách API Key
 * - Thu hồi (xóa) API Key
 */
@RestController
@RequestMapping("/projects/{projectId}/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /**
     * Tạo API Key mới cho Project.
     * Trả về rawKey duy nhất một lần trong response này.
     */
    @PostMapping
    public ResponseEntity<ApiKeyCreatedResponse> createApiKey(
            @PathVariable String projectId,
            @Valid @RequestBody CreateApiKeyRequest request,
            Authentication authentication
    ) {
        String currentUserId = resolveUserId(authentication);
        ApiKeyCreatedResponse response = apiKeyService.createApiKey(projectId, request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Liệt kê các API Key của Project.
     * Chỉ trả về metadata và keyPrefix (tuyệt đối không trả lại rawKey).
     */
    @GetMapping
    public ResponseEntity<ApiKeyListResponse> listApiKeys(
            @PathVariable String projectId,
            Authentication authentication
    ) {
        String currentUserId = resolveUserId(authentication);
        ApiKeyListResponse response = apiKeyService.listApiKeys(projectId, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Thu hồi (xóa) API Key.
     * Làm key mất hiệu lực tức thì.
     */
    @DeleteMapping("/{keyHash}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> revokeApiKey(
            @PathVariable String projectId,
            @PathVariable String keyHash,
            Authentication authentication
    ) {
        String currentUserId = resolveUserId(authentication);
        apiKeyService.revokeApiKey(projectId, keyHash, currentUserId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Trích xuất userId từ Authentication context (chỉ người dùng web có JWT mới được quản lý API Key).
     */
    private String resolveUserId(Authentication authentication) {
        if (authentication == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (authentication.getPrincipal() instanceof Jwt jwt && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject();
        }
        if (authentication.getName() != null && !authentication.getName().isBlank()) {
            return authentication.getName();
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
