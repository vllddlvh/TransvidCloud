package com.transvidcloud.backend.project.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.common.exception.GlobalExceptionHandler;
import com.transvidcloud.backend.project.dto.CreateProjectRequest;
import com.transvidcloud.backend.project.dto.ProjectListResponse;
import com.transvidcloud.backend.project.dto.ProjectResponse;
import com.transvidcloud.backend.project.dto.UpdateProjectRequest;
import com.transvidcloud.backend.project.service.ProjectService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectController Web Slice Test")
class ProjectControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProjectService projectService;

    private MockMvc mockMvc;

    private final Authentication mockPrincipal = new UsernamePasswordAuthenticationToken("usr_test123", null, List.of());

    @BeforeEach
    void setUp() {
        ProjectController controller = new ProjectController(projectService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /projects thành công trả về 201 Created và ProjectResponse")
    void createProject_success() throws Exception {
        CreateProjectRequest request = new CreateProjectRequest(
                "Khóa học DevOps 2026",
                "Video bài giảng thực hành",
                "https://example.com/webhook"
        );
        ProjectResponse response = new ProjectResponse(
                "prj_1234567890ab",
                "usr_test123",
                "Khóa học DevOps 2026",
                "Video bài giảng thực hành",
                "https://example.com/webhook",
                "2026-10-31T20:00:00Z",
                "2026-10-31T20:00:00Z"
        );

        when(projectService.createProject(any(), eq("usr_test123"))).thenReturn(response);

        mockMvc.perform(post("/projects")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId", is("prj_1234567890ab")))
                .andExpect(jsonPath("$.name", is("Khóa học DevOps 2026")))
                .andExpect(jsonPath("$.ownerId", is("usr_test123")));
    }

    @Test
    @DisplayName("POST /projects từ chối 400 Bad Request khi tên rỗng hoặc vượt quá 120 ký tự")
    void createProject_invalidName_returns400() throws Exception {
        CreateProjectRequest requestEmpty = new CreateProjectRequest("", null, null);

        mockMvc.perform(post("/projects")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestEmpty)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));

        String longName = "A".repeat(121);
        CreateProjectRequest requestLong = new CreateProjectRequest(longName, null, null);

        mockMvc.perform(post("/projects")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("POST /projects từ chối 400 Bad Request khi Webhook URL sai định dạng")
    void createProject_invalidWebhookUrl_returns400() throws Exception {
        CreateProjectRequest request = new CreateProjectRequest("Valid Name", null, "ftp://invalid-url");

        mockMvc.perform(post("/projects")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("GET /projects thành công trả về 200 OK và danh sách ProjectListResponse")
    void listProjects_success() throws Exception {
        ProjectResponse p1 = new ProjectResponse("prj_1", "usr_test123", "P1", null, null, "2026-10-31T00:00:00Z", "2026-10-31T00:00:00Z");
        ProjectResponse p2 = new ProjectResponse("prj_2", "usr_test123", "P2", null, null, "2026-10-31T01:00:00Z", "2026-10-31T01:00:00Z");
        ProjectListResponse response = new ProjectListResponse(List.of(p2, p1));

        when(projectService.listProjects("usr_test123")).thenReturn(response);

        mockMvc.perform(get("/projects").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].projectId", is("prj_2")));
    }

    @Test
    @DisplayName("GET /projects/{projectId} thành công trả về 200 OK")
    void getProject_success() throws Exception {
        ProjectResponse response = new ProjectResponse("prj_1", "usr_test123", "P1", null, null, "2026-10-31T00:00:00Z", "2026-10-31T00:00:00Z");

        when(projectService.getProject("prj_1", "usr_test123")).thenReturn(response);

        mockMvc.perform(get("/projects/prj_1").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId", is("prj_1")));
    }

    @Test
    @DisplayName("GET /projects/{projectId} trả về 404 Not Found khi không tìm thấy")
    void getProject_notFound() throws Exception {
        when(projectService.getProject("prj_missing", "usr_test123"))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/projects/prj_missing").principal(mockPrincipal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /projects/{projectId} trả về 403 Forbidden khi truy cập dự án của người khác")
    void getProject_accessDenied() throws Exception {
        when(projectService.getProject("prj_other", "usr_test123"))
                .thenThrow(new AppException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(get("/projects/prj_other").principal(mockPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("PATCH /projects/{projectId} thành công trả về 200 OK")
    void updateProject_success() throws Exception {
        UpdateProjectRequest request = new UpdateProjectRequest("Tên mới", "Mô tả mới", null);
        ProjectResponse response = new ProjectResponse("prj_1", "usr_test123", "Tên mới", "Mô tả mới", null, "2026-10-31T00:00:00Z", "2026-10-31T02:00:00Z");

        when(projectService.updateProject(eq("prj_1"), any(), eq("usr_test123"))).thenReturn(response);

        mockMvc.perform(patch("/projects/prj_1")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Tên mới")));
    }

    @Test
    @DisplayName("DELETE /projects/{projectId} thành công trả về 204 No Content")
    void deleteProject_success() throws Exception {
        doNothing().when(projectService).deleteProject("prj_1", "usr_test123");

        mockMvc.perform(delete("/projects/prj_1").principal(mockPrincipal))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /projects/{projectId} trả về 409 Conflict khi còn video in-flight")
    void deleteProject_conflict() throws Exception {
        doThrow(new AppException(ErrorCode.RESOURCE_IN_USE))
                .when(projectService).deleteProject("prj_busy", "usr_test123");

        mockMvc.perform(delete("/projects/prj_busy").principal(mockPrincipal))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("RESOURCE_IN_USE")));
    }
}
