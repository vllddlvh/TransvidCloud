package com.transvidcloud.backend.apikey.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transvidcloud.backend.apikey.dto.ApiKeyCreatedResponse;
import com.transvidcloud.backend.apikey.dto.ApiKeyListResponse;
import com.transvidcloud.backend.apikey.dto.ApiKeyMetadataResponse;
import com.transvidcloud.backend.apikey.dto.CreateApiKeyRequest;
import com.transvidcloud.backend.apikey.service.ApiKeyService;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.common.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyController Web Slice Test")
class ApiKeyControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ApiKeyService apiKeyService;

    private MockMvc mockMvc;

    private final Authentication mockPrincipal = new UsernamePasswordAuthenticationToken("usr_test123", null, List.of());
    private final String projectId = "prj_abc123";

    @BeforeEach
    void setUp() {
        ApiKeyController controller = new ApiKeyController(apiKeyService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /projects/{projectId}/api-keys thành công trả về 201 Created và rawKey")
    void testCreateApiKeySuccess() throws Exception {
        CreateApiKeyRequest request = new CreateApiKeyRequest("CI Server Key");
        ApiKeyCreatedResponse response = new ApiKeyCreatedResponse(
                "hash_123456",
                projectId,
                "CI Server Key",
                "tvc_live_1234567890abcdef1234567890abcdef",
                "tvc_live_12345678...",
                "2026-10-31T12:00:00Z"
        );

        when(apiKeyService.createApiKey(eq(projectId), any(CreateApiKeyRequest.class), eq("usr_test123")))
                .thenReturn(response);

        mockMvc.perform(post("/projects/{projectId}/api-keys", projectId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keyHash", is("hash_123456")))
                .andExpect(jsonPath("$.projectId", is(projectId)))
                .andExpect(jsonPath("$.rawKey", is("tvc_live_1234567890abcdef1234567890abcdef")))
                .andExpect(jsonPath("$.keyPrefix", is("tvc_live_12345678...")));
    }

    @Test
    @DisplayName("POST /projects/{projectId}/api-keys từ chối 400 Bad Request khi tên rỗng hoặc vượt 100 ký tự")
    void testCreateApiKeyValidationFailure() throws Exception {
        CreateApiKeyRequest blankRequest = new CreateApiKeyRequest("   ");

        mockMvc.perform(post("/projects/{projectId}/api-keys", projectId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankRequest)))
                .andExpect(status().isBadRequest());

        CreateApiKeyRequest tooLongRequest = new CreateApiKeyRequest("A".repeat(101));

        mockMvc.perform(post("/projects/{projectId}/api-keys", projectId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooLongRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /projects/{projectId}/api-keys từ chối 403 Forbidden khi không phải chủ sở hữu")
    void testCreateApiKeyNotOwner() throws Exception {
        CreateApiKeyRequest request = new CreateApiKeyRequest("Test Key");

        when(apiKeyService.createApiKey(eq(projectId), any(CreateApiKeyRequest.class), eq("usr_test123")))
                .thenThrow(new AppException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(post("/projects/{projectId}/api-keys", projectId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /projects/{projectId}/api-keys thành công trả về 200 OK với danh sách key")
    void testListApiKeysSuccess() throws Exception {
        ApiKeyMetadataResponse key1 = new ApiKeyMetadataResponse("hash1", projectId, "Key 1", "tvc_live_11111111...", "2026-10-31T00:00:00Z");
        ApiKeyListResponse response = new ApiKeyListResponse(List.of(key1));

        when(apiKeyService.listApiKeys(eq(projectId), eq("usr_test123"))).thenReturn(response);

        mockMvc.perform(get("/projects/{projectId}/api-keys", projectId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].keyHash", is("hash1")))
                .andExpect(jsonPath("$.items[0].keyPrefix", is("tvc_live_11111111...")));
    }

    @Test
    @DisplayName("DELETE /projects/{projectId}/api-keys/{keyHash} thành công trả về 204 No Content")
    void testRevokeApiKeySuccess() throws Exception {
        doNothing().when(apiKeyService).revokeApiKey(projectId, "hash_to_delete", "usr_test123");

        mockMvc.perform(delete("/projects/{projectId}/api-keys/{keyHash}", projectId, "hash_to_delete")
                        .principal(mockPrincipal))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /projects/{projectId}/api-keys/{keyHash} trả về 404 Not Found khi key không tồn tại")
    void testRevokeApiKeyNotFound() throws Exception {
        doThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND))
                .when(apiKeyService).revokeApiKey(projectId, "not_found", "usr_test123");

        mockMvc.perform(delete("/projects/{projectId}/api-keys/{keyHash}", projectId, "not_found")
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }
}
