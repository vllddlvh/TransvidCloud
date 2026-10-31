package com.transvidcloud.backend.project.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tầng nghiệp vụ Quản lý Project & Phân quyền sở hữu tài nguyên (CRUD & Isolation).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final VideoSafetyChecker videoSafetyChecker;
    private final ProjectMapper projectMapper;

    /**
     * Tạo Project mới cho người dùng hiện tại.
     *
     * @param request Dữ liệu yêu cầu tạo project
     * @param ownerId Định danh người sở hữu (từ JWT)
     * @return ProjectResponse thông tin chi tiết project vừa tạo
     */
    public ProjectResponse createProject(CreateProjectRequest request, String ownerId) {
        String projectId = "prj_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String now = Instant.now().toString();

        String trimmedDescription = request.description() != null && !request.description().isBlank()
                ? request.description().trim()
                : null;

        String trimmedWebhookUrl = request.webhookUrl() != null && !request.webhookUrl().isBlank()
                ? request.webhookUrl().trim()
                : null;

        ProjectItem item = ProjectItem.builder()
                .projectId(projectId)
                .ownerId(ownerId)
                .name(request.name().trim())
                .description(trimmedDescription)
                .webhookUrl(trimmedWebhookUrl)
                .status(ProjectItem.STATUS_ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        projectRepository.save(item);
        log.info("Tạo thành công Project mới: projectId={}, ownerId={}, name={}", projectId, ownerId, item.getName());
        return projectMapper.toProjectResponse(item);
    }

    /**
     * Liệt kê danh sách các Project đang hoạt động của người dùng hiện tại.
     * Sắp xếp mới nhất lên đầu.
     *
     * @param ownerId Định danh người sở hữu
     * @return ProjectListResponse chứa mảng items
     */
    public ProjectListResponse listProjects(String ownerId) {
        List<ProjectItem> items = projectRepository.findByOwnerId(ownerId);
        return projectMapper.toProjectListResponse(items);
    }

    /**
     * Xem thông tin chi tiết một Project.
     * Kiểm tra quyền sở hữu tài nguyên: nếu không phải chủ sở hữu thì ném ACCESS_DENIED (403).
     *
     * @param projectId Mã định danh dự án
     * @param currentUserId Định danh người dùng hiện tại
     * @return ProjectResponse chi tiết dự án
     */
    public ProjectResponse getProject(String projectId, String currentUserId) {
        ProjectItem item = getProjectAndValidateAccess(projectId, currentUserId);
        return projectMapper.toProjectResponse(item);
    }

    /**
     * Cập nhật thông tin Project (Partial Update).
     *
     * @param projectId Mã định danh dự án
     * @param request Dữ liệu cập nhật
     * @param currentUserId Định danh người dùng hiện tại
     * @return ProjectResponse sau khi cập nhật
     */
    public ProjectResponse updateProject(String projectId, UpdateProjectRequest request, String currentUserId) {
        ProjectItem item = getProjectAndValidateAccess(projectId, currentUserId);

        if (request.name() != null && !request.name().isBlank()) {
            item.setName(request.name().trim());
        }

        if (request.description() != null) {
            item.setDescription(request.description().isBlank() ? null : request.description().trim());
        }

        if (request.webhookUrl() != null) {
            item.setWebhookUrl(request.webhookUrl().isBlank() ? null : request.webhookUrl().trim());
        }

        item.setUpdatedAt(Instant.now().toString());
        ProjectItem updatedItem = projectRepository.update(item);
        log.info("Cập nhật thành công Project: projectId={}, ownerId={}", projectId, currentUserId);
        return projectMapper.toProjectResponse(updatedItem);
    }

    /**
     * Xóa Project an toàn (Soft Delete).
     * Kiểm tra an toàn: nếu còn video in-flight (UPLOADING, PENDING, PROCESSING) thì từ chối (409 RESOURCE_IN_USE).
     *
     * @param projectId Mã định danh dự án
     * @param currentUserId Định danh người dùng hiện tại
     */
    public void deleteProject(String projectId, String currentUserId) {
        getProjectAndValidateAccess(projectId, currentUserId);

        // Kiểm tra an toàn: còn video đang xử lý dở dang hay không
        if (videoSafetyChecker.hasActiveVideos(projectId)) {
            log.warn("Từ chối xóa Project {}: tồn tại video đang xử lý dở dang", projectId);
            throw new AppException(ErrorCode.RESOURCE_IN_USE);
        }

        projectRepository.softDelete(projectId);
        log.info("Xóa mềm thành công Project: projectId={}, ownerId={}", projectId, currentUserId);
    }

    /**
     * Hàm phụ trợ kiểm tra sự tồn tại của Project, trạng thái hoạt động và quyền sở hữu.
     */
    private ProjectItem getProjectAndValidateAccess(String projectId, String currentUserId) {
        ProjectItem item = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (ProjectItem.STATUS_DELETED.equals(item.getStatus())) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (!item.getOwnerId().equals(currentUserId)) {
            log.warn("Truy cập trái phép vào Project {}: user yêu cầu={}, ownerId={}", projectId, currentUserId, item.getOwnerId());
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        return item;
    }
}
