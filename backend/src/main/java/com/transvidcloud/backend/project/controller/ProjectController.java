package com.transvidcloud.backend.project.controller;

import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.project.dto.CreateProjectRequest;
import com.transvidcloud.backend.project.dto.ProjectListResponse;
import com.transvidcloud.backend.project.dto.ProjectResponse;
import com.transvidcloud.backend.project.dto.UpdateProjectRequest;
import com.transvidcloud.backend.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho các tác vụ Quản lý Project & Phân quyền sở hữu tài nguyên.
 */
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /**
     * Tạo Project mới cho người dùng hiện tại.
     */
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            Authentication authentication
    ) {
        String currentUserId = resolveUserId(authentication);
        ProjectResponse response = projectService.createProject(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Liệt kê danh sách các Project đang hoạt động của người dùng hiện tại.
     */
    @GetMapping
    public ResponseEntity<ProjectListResponse> listProjects(
            Authentication authentication
    ) {
        String currentUserId = resolveUserId(authentication);
        ProjectListResponse response = projectService.listProjects(currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Xem thông tin chi tiết một Project.
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable String projectId,
            Authentication authentication
    ) {
        String currentUserId = resolveUserId(authentication);
        ProjectResponse response = projectService.getProject(projectId, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Cập nhật thông tin Project (Partial Update).
     */
    @PatchMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable String projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            Authentication authentication
    ) {
        String currentUserId = resolveUserId(authentication);
        ProjectResponse response = projectService.updateProject(projectId, request, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Xóa Project an toàn (Soft Delete).
     */
    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteProject(
            @PathVariable String projectId,
            Authentication authentication
    ) {
        String currentUserId = resolveUserId(authentication);
        projectService.deleteProject(projectId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Trích xuất userId từ Authentication context (hỗ trợ cả Jwt principal và chuẩn Principal).
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
