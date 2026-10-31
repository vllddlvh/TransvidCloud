package com.transvidcloud.backend.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.project.dto.CreateProjectRequest;
import com.transvidcloud.backend.project.dto.ProjectListResponse;
import com.transvidcloud.backend.project.dto.ProjectResponse;
import com.transvidcloud.backend.project.dto.UpdateProjectRequest;
import com.transvidcloud.backend.project.entity.ProjectItem;
import com.transvidcloud.backend.project.mapper.ProjectMapper;
import com.transvidcloud.backend.project.repository.ProjectRepository;
import com.transvidcloud.backend.project.repository.VideoSafetyChecker;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectService Unit Test")
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private VideoSafetyChecker videoSafetyChecker;

    private final ProjectMapper projectMapper = Mappers.getMapper(ProjectMapper.class);

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, videoSafetyChecker, projectMapper);
    }

    @Test
    @DisplayName("Tạo project mới thành công: sinh mã prj_, gán đúng ownerId và trạng thái ACTIVE")
    void createProject_success() {
        CreateProjectRequest request = new CreateProjectRequest(
                "Khóa học Spring Boot",
                "Mô tả khóa học",
                "https://example.com/webhook"
        );
        String ownerId = "usr_user123";

        ProjectResponse response = projectService.createProject(request, ownerId);

        assertThat(response).isNotNull();
        assertThat(response.projectId()).startsWith("prj_");
        assertThat(response.ownerId()).isEqualTo(ownerId);
        assertThat(response.name()).isEqualTo("Khóa học Spring Boot");
        assertThat(response.description()).isEqualTo("Mô tả khóa học");
        assertThat(response.webhookUrl()).isEqualTo("https://example.com/webhook");

        verify(projectRepository).save(argThat(item ->
                item.getProjectId().startsWith("prj_")
                        && item.getOwnerId().equals(ownerId)
                        && item.getStatus().equals(ProjectItem.STATUS_ACTIVE)
        ));
    }

    @Test
    @DisplayName("Liệt kê danh sách project của user thành công")
    void listProjects_success() {
        String ownerId = "usr_user123";
        ProjectItem item1 = ProjectItem.builder()
                .projectId("prj_001")
                .ownerId(ownerId)
                .name("Project 1")
                .status(ProjectItem.STATUS_ACTIVE)
                .createdAt("2026-10-31T10:00:00Z")
                .updatedAt("2026-10-31T10:00:00Z")
                .build();

        when(projectRepository.findByOwnerId(ownerId)).thenReturn(List.of(item1));

        ProjectListResponse response = projectService.listProjects(ownerId);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).projectId()).isEqualTo("prj_001");
    }

    @Test
    @DisplayName("Xem chi tiết project thành công khi đúng chủ sở hữu")
    void getProject_success() {
        String projectId = "prj_001";
        String ownerId = "usr_owner";
        ProjectItem item = ProjectItem.builder()
                .projectId(projectId)
                .ownerId(ownerId)
                .name("Project Alpha")
                .status(ProjectItem.STATUS_ACTIVE)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(item));

        ProjectResponse response = projectService.getProject(projectId, ownerId);

        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.name()).isEqualTo("Project Alpha");
    }

    @Test
    @DisplayName("Xem chi tiết project ném RESOURCE_NOT_FOUND (404) khi không tồn tại")
    void getProject_notFound_whenMissing() {
        when(projectRepository.findById("prj_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProject("prj_missing", "usr_any"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Xem chi tiết project ném RESOURCE_NOT_FOUND (404) khi đã bị xóa mềm")
    void getProject_notFound_whenDeleted() {
        String projectId = "prj_deleted";
        ProjectItem item = ProjectItem.builder()
                .projectId(projectId)
                .ownerId("usr_owner")
                .status(ProjectItem.STATUS_DELETED)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> projectService.getProject(projectId, "usr_owner"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Xem chi tiết project ném ACCESS_DENIED (403) khi khác chủ sở hữu (Multi-tenant Isolation)")
    void getProject_accessDenied_whenOwnerMismatch() {
        String projectId = "prj_001";
        ProjectItem item = ProjectItem.builder()
                .projectId(projectId)
                .ownerId("usr_owner_A")
                .status(ProjectItem.STATUS_ACTIVE)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> projectService.getProject(projectId, "usr_hacker_B"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("Cập nhật thông tin project thành công và làm mới updatedAt")
    void updateProject_success() {
        String projectId = "prj_001";
        String ownerId = "usr_owner";
        ProjectItem item = ProjectItem.builder()
                .projectId(projectId)
                .ownerId(ownerId)
                .name("Tên cũ")
                .description("Mô tả cũ")
                .status(ProjectItem.STATUS_ACTIVE)
                .createdAt("2026-10-31T00:00:00Z")
                .updatedAt("2026-10-31T00:00:00Z")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(item));
        when(projectRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProjectRequest request = new UpdateProjectRequest(
                "Tên mới",
                "Mô tả mới",
                "https://webhook.new.io"
        );

        ProjectResponse response = projectService.updateProject(projectId, request, ownerId);

        assertThat(response.name()).isEqualTo("Tên mới");
        assertThat(response.description()).isEqualTo("Mô tả mới");
        assertThat(response.webhookUrl()).isEqualTo("https://webhook.new.io");
        assertThat(response.updatedAt()).isNotEqualTo("2026-10-31T00:00:00Z");
    }

    @Test
    @DisplayName("Cập nhật project ném ACCESS_DENIED (403) khi không phải chủ sở hữu")
    void updateProject_accessDenied() {
        String projectId = "prj_001";
        ProjectItem item = ProjectItem.builder()
                .projectId(projectId)
                .ownerId("usr_owner_A")
                .status(ProjectItem.STATUS_ACTIVE)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(item));

        UpdateProjectRequest request = new UpdateProjectRequest("Tên sửa", null, null);

        assertThatThrownBy(() -> projectService.updateProject(projectId, request, "usr_other_B"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("Xóa an toàn Project ném RESOURCE_IN_USE (409) khi còn video đang xử lý dở dang")
    void deleteProject_conflict_whenHasActiveVideos() {
        String projectId = "prj_001";
        String ownerId = "usr_owner";
        ProjectItem item = ProjectItem.builder()
                .projectId(projectId)
                .ownerId(ownerId)
                .status(ProjectItem.STATUS_ACTIVE)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(item));
        when(videoSafetyChecker.hasActiveVideos(projectId)).thenReturn(true);

        assertThatThrownBy(() -> projectService.deleteProject(projectId, ownerId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_IN_USE);
    }

    @Test
    @DisplayName("Xóa an toàn Project thành công: Soft Delete được gọi khi không còn video in-flight")
    void deleteProject_success_whenNoActiveVideos() {
        String projectId = "prj_001";
        String ownerId = "usr_owner";
        ProjectItem item = ProjectItem.builder()
                .projectId(projectId)
                .ownerId(ownerId)
                .status(ProjectItem.STATUS_ACTIVE)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(item));
        when(videoSafetyChecker.hasActiveVideos(projectId)).thenReturn(false);

        projectService.deleteProject(projectId, ownerId);

        verify(projectRepository).softDelete(projectId);
    }

    @Test
    @DisplayName("Xóa project ném ACCESS_DENIED (403) khi không phải chủ sở hữu")
    void deleteProject_accessDenied() {
        String projectId = "prj_001";
        ProjectItem item = ProjectItem.builder()
                .projectId(projectId)
                .ownerId("usr_owner_A")
                .status(ProjectItem.STATUS_ACTIVE)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> projectService.deleteProject(projectId, "usr_other_B"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }
}
