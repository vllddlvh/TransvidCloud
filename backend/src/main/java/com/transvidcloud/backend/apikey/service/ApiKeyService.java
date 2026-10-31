package com.transvidcloud.backend.apikey.service;

import com.transvidcloud.backend.apikey.dto.ApiKeyCreatedResponse;
import com.transvidcloud.backend.apikey.dto.ApiKeyListResponse;
import com.transvidcloud.backend.apikey.dto.CreateApiKeyRequest;
import com.transvidcloud.backend.apikey.entity.ApiKeyItem;
import com.transvidcloud.backend.apikey.mapper.ApiKeyMapper;
import com.transvidcloud.backend.apikey.repository.ApiKeyRepository;
import com.transvidcloud.backend.apikey.util.ApiKeyGenerator;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.project.service.ProjectService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service xử lý nghiệp vụ quản lý API Key:
 * - Tạo mới khóa ngẫu nhiên, chỉ lưu SHA-256 hash, giới hạn tối đa 5 keys/project.
 * - Liệt kê danh sách khóa (chỉ metadata, không lộ rawKey).
 * - Thu hồi (xóa cứng) khóa.
 * - Kiểm tra phân quyền sở hữu Project qua ProjectService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    public static final int MAX_KEYS_PER_PROJECT = 5;

    private final ApiKeyRepository apiKeyRepository;
    private final ProjectService projectService;
    private final ApiKeyMapper apiKeyMapper;

    /**
     * Tạo API Key mới cho Project.
     * Yêu cầu người dùng hiện tại phải là chủ sở hữu Project và Project chưa vượt quá 5 keys.
     */
    public ApiKeyCreatedResponse createApiKey(String projectId, CreateApiKeyRequest request, String currentUserId) {
        log.info("Bắt đầu tạo API Key mới cho Project: projectId={}, userId={}", projectId, currentUserId);

        // 1. Kiểm tra tồn tại, trạng thái ACTIVE và quyền sở hữu Project
        projectService.getProject(projectId, currentUserId);

        // 2. Kiểm tra giới hạn số lượng API Key của Project (tối đa 5 keys)
        int currentCount = apiKeyRepository.countByProjectId(projectId);
        if (currentCount >= MAX_KEYS_PER_PROJECT) {
            log.warn("Từ chối tạo API Key: Project {} đã đạt tối đa {} keys", projectId, MAX_KEYS_PER_PROJECT);
            throw new AppException(ErrorCode.INVALID_KEY, "Maximum 5 API keys allowed per project");
        }

        // 3. Sinh chuỗi ngẫu nhiên rawKey (tvc_live_...), băm SHA-256 keyHash và trích xuất keyPrefix
        String rawKey = ApiKeyGenerator.generateRawKey();
        String keyHash = ApiKeyGenerator.sha256Hex(rawKey);
        String keyPrefix = ApiKeyGenerator.extractKeyPrefix(rawKey);
        String now = Instant.now().toString();

        ApiKeyItem item = ApiKeyItem.builder()
                .keyHash(keyHash)
                .projectId(projectId)
                .name(request.name().trim())
                .keyPrefix(keyPrefix)
                .createdAt(now)
                .build();

        apiKeyRepository.save(item);
        log.info("Tạo thành công API Key: projectId={}, keyPrefix={}, keyHash={}", projectId, keyPrefix, keyHash);

        return apiKeyMapper.toCreatedResponse(item, rawKey);
    }

    /**
     * Liệt kê danh sách các API Key của Project.
     * Chỉ trả về metadata và keyPrefix, tuyệt đối không bao giờ trả về rawKey.
     */
    public ApiKeyListResponse listApiKeys(String projectId, String currentUserId) {
        // Kiểm tra quyền sở hữu Project
        projectService.getProject(projectId, currentUserId);

        List<ApiKeyItem> items = apiKeyRepository.findByProjectId(projectId);
        return apiKeyMapper.toListResponse(items);
    }

    /**
     * Thu hồi (xóa cứng) API Key bằng keyHash.
     * Khóa bị xóa sẽ mất hiệu lực tức thì trên toàn hệ thống.
     */
    public void revokeApiKey(String projectId, String keyHash, String currentUserId) {
        log.info("Yêu cầu thu hồi API Key: projectId={}, keyHash={}, userId={}", projectId, keyHash, currentUserId);

        // 1. Kiểm tra quyền sở hữu Project
        projectService.getProject(projectId, currentUserId);

        // 2. Tìm kiếm API Key theo keyHash
        ApiKeyItem item = apiKeyRepository.findByKeyHash(keyHash)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "API Key not found"));

        // 3. Đảm bảo key thuộc đúng projectId của URL
        if (!projectId.equals(item.getProjectId())) {
            log.warn("API Key {} không thuộc về Project {}", keyHash, projectId);
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "API Key does not belong to this project");
        }

        // 4. Xóa vật lý bản ghi khỏi DynamoDB
        apiKeyRepository.deleteByKeyHash(keyHash);
        log.info("Thu hồi thành công API Key: keyHash={}", keyHash);
    }
}
