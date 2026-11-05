package com.transvidcloud.backend.apikey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transvidcloud.backend.apikey.dto.ApiKeyCreatedResponse;
import com.transvidcloud.backend.apikey.dto.ApiKeyListResponse;
import com.transvidcloud.backend.apikey.dto.ApiKeyMetadataResponse;
import com.transvidcloud.backend.apikey.dto.CreateApiKeyRequest;
import com.transvidcloud.backend.apikey.entity.ApiKeyItem;
import com.transvidcloud.backend.apikey.mapper.ApiKeyMapper;
import com.transvidcloud.backend.apikey.repository.ApiKeyRepository;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.project.dto.ProjectResponse;
import com.transvidcloud.backend.project.service.ProjectService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private ProjectService projectService;

    private ApiKeyMapper apiKeyMapper;
    private ApiKeyService apiKeyService;

    private final String projectId = "prj_test_123";
    private final String userId = "usr_owner_1";

    @BeforeEach
    void setUp() {
        apiKeyMapper = Mappers.getMapper(ApiKeyMapper.class);
        apiKeyService = new ApiKeyService(apiKeyRepository, projectService, apiKeyMapper);
    }

    @Test
    @DisplayName("Tạo API Key thành công khi Project thuộc sở hữu của user và chưa vượt 5 keys")
    void testCreateApiKeySuccess() {
        when(projectService.getProject(projectId, userId)).thenReturn(mockProjectResponse());
        when(apiKeyRepository.countByProjectId(projectId)).thenReturn(2);

        CreateApiKeyRequest request = new CreateApiKeyRequest("Dev Server Key");
        ApiKeyCreatedResponse response = apiKeyService.createApiKey(projectId, request, userId);

        assertThat(response).isNotNull();
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.name()).isEqualTo("Dev Server Key");
        assertThat(response.rawKey()).isNotNull().startsWith("tvc_live_").hasSize(41);
        assertThat(response.keyPrefix()).startsWith("tvc_live_").endsWith("...");
        assertThat(response.keyHash()).isNotNull().hasSize(64);

        ArgumentCaptor<ApiKeyItem> captor = ArgumentCaptor.forClass(ApiKeyItem.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKeyItem saved = captor.getValue();
        assertThat(saved.getKeyHash()).isEqualTo(response.keyHash());
        assertThat(saved.getProjectId()).isEqualTo(projectId);
        assertThat(saved.getName()).isEqualTo("Dev Server Key");
        assertThat(saved.getKeyPrefix()).isEqualTo(response.keyPrefix());
    }

    @Test
    @DisplayName("Từ chối tạo API Key khi Project đã có đủ 5 keys (HTTP 400 INVALID_KEY)")
    void testCreateApiKeyExceedsLimit() {
        when(projectService.getProject(projectId, userId)).thenReturn(mockProjectResponse());
        when(apiKeyRepository.countByProjectId(projectId)).thenReturn(5);

        CreateApiKeyRequest request = new CreateApiKeyRequest("Key thứ 6");

        assertThatThrownBy(() -> apiKeyService.createApiKey(projectId, request, userId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException appEx = (AppException) e;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_KEY);
                    assertThat(appEx.getMessage()).contains("Maximum 5 API keys allowed");
                });

        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Từ chối tạo API Key khi user không sở hữu Project (HTTP 403 ACCESS_DENIED)")
    void testCreateApiKeyNotOwner() {
        when(projectService.getProject(projectId, "usr_hacker"))
                .thenThrow(new AppException(ErrorCode.ACCESS_DENIED));

        CreateApiKeyRequest request = new CreateApiKeyRequest("Hacked Key");

        assertThatThrownBy(() -> apiKeyService.createApiKey(projectId, request, "usr_hacker"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Liệt kê API Keys trả về danh sách metadata an toàn không chứa rawKey")
    void testListApiKeysSuccess() {
        when(projectService.getProject(projectId, userId)).thenReturn(mockProjectResponse());

        ApiKeyItem key1 = ApiKeyItem.builder()
                .keyHash("hash1")
                .projectId(projectId)
                .name("Key 1")
                .keyPrefix("tvc_live_11111111...")
                .createdAt("2026-10-31T10:00:00Z")
                .build();
        ApiKeyItem key2 = ApiKeyItem.builder()
                .keyHash("hash2")
                .projectId(projectId)
                .name("Key 2")
                .keyPrefix("tvc_live_22222222...")
                .createdAt("2026-10-31T11:00:00Z")
                .build();

        when(apiKeyRepository.findByProjectId(projectId)).thenReturn(List.of(key1, key2));

        ApiKeyListResponse response = apiKeyService.listApiKeys(projectId, userId);

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting(ApiKeyMetadataResponse::keyHash).containsExactly("hash1", "hash2");
        assertThat(response.items()).extracting(ApiKeyMetadataResponse::keyPrefix).containsExactly("tvc_live_11111111...", "tvc_live_22222222...");
    }

    @Test
    @DisplayName("Thu hồi API Key thành công khi key tồn tại và thuộc đúng Project")
    void testRevokeApiKeySuccess() {
        when(projectService.getProject(projectId, userId)).thenReturn(mockProjectResponse());

        ApiKeyItem keyItem = ApiKeyItem.builder()
                .keyHash("hash_to_delete")
                .projectId(projectId)
                .name("Old Key")
                .keyPrefix("tvc_live_oldkey...")
                .createdAt("2026-10-31T10:00:00Z")
                .build();

        when(apiKeyRepository.findByKeyHash("hash_to_delete")).thenReturn(Optional.of(keyItem));

        apiKeyService.revokeApiKey(projectId, "hash_to_delete", userId);

        verify(apiKeyRepository).deleteByKeyHash("hash_to_delete");
    }

    @Test
    @DisplayName("Thu hồi thất bại khi keyHash không tồn tại (HTTP 404 RESOURCE_NOT_FOUND)")
    void testRevokeApiKeyNotFound() {
        when(projectService.getProject(projectId, userId)).thenReturn(mockProjectResponse());
        when(apiKeyRepository.findByKeyHash("non_existent_hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revokeApiKey(projectId, "non_existent_hash", userId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verify(apiKeyRepository, never()).deleteByKeyHash(anyString());
    }

    @Test
    @DisplayName("Thu hồi thất bại khi keyHash thuộc về Project khác (HTTP 404 RESOURCE_NOT_FOUND)")
    void testRevokeApiKeyWrongProject() {
        when(projectService.getProject(projectId, userId)).thenReturn(mockProjectResponse());

        ApiKeyItem keyItemOtherProject = ApiKeyItem.builder()
                .keyHash("hash_other_proj")
                .projectId("prj_other_project")
                .name("Key other")
                .build();

        when(apiKeyRepository.findByKeyHash("hash_other_proj")).thenReturn(Optional.of(keyItemOtherProject));

        assertThatThrownBy(() -> apiKeyService.revokeApiKey(projectId, "hash_other_proj", userId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verify(apiKeyRepository, never()).deleteByKeyHash(anyString());
    }

    private ProjectResponse mockProjectResponse() {
        return new ProjectResponse(projectId, userId, "Test Project", "Desc", null, "2026-10-31T00:00:00Z", "2026-10-31T00:00:00Z");
    }
}
