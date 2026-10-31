package com.transvidcloud.backend.video.service;

import com.transvidcloud.backend.apikey.security.ApiKeyPrincipal;
import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.project.entity.ProjectItem;
import com.transvidcloud.backend.project.repository.ProjectRepository;
import com.transvidcloud.backend.video.dto.InitVideoRequest;
import com.transvidcloud.backend.video.dto.InitVideoResponse;
import com.transvidcloud.backend.video.dto.PresignedUploadInfo;
import com.transvidcloud.backend.video.dto.VideoResponse;
import com.transvidcloud.backend.video.entity.VideoItem;
import com.transvidcloud.backend.video.entity.VideoStatus;
import com.transvidcloud.backend.video.mapper.VideoMapper;
import com.transvidcloud.backend.video.repository.VideoRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Tầng Service xử lý nghiệp vụ khởi tạo video và cấp S3 Presigned PUT URL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final ProjectRepository projectRepository;
    private final VideoMapper videoMapper;
    private final S3Presigner s3Presigner;
    private final TvcProperties tvcProperties;

    /**
     * Khởi tạo bản ghi Video ở trạng thái UPLOADING và cấp S3 Presigned PUT URL 15 phút.
     * Hỗ trợ xác thực kép (Bearer JWT và X-API-Key).
     *
     * @param projectId      Mã định danh dự án
     * @param request        Thông tin video khai báo
     * @param authentication Ngữ cảnh xác thực Spring Security
     * @return InitVideoResponse chứa metadata video và S3 Presigned PUT URL
     */
    public InitVideoResponse initVideoUpload(String projectId, InitVideoRequest request, Authentication authentication) {
        // 1. Kiểm tra xác thực và quyền sở hữu Project
        validateProjectAccess(projectId, authentication);

        // 2. Kiểm tra giới hạn dung lượng file tối đa (200MB)
        VideoValidationHelper.validateSizeBytes(request.sizeBytes(), tvcProperties.getUpload().getMaxBytes());

        // 3. Kiểm tra định dạng container và MIME type
        String ext = VideoValidationHelper.extractExtension(request.filename());
        boolean extAllowed = VideoValidationHelper.isExtensionAllowed(ext);
        boolean contentTypeAllowed = VideoValidationHelper.isContentTypeAllowed(
                request.contentType(),
                tvcProperties.getUpload().getAllowedContentTypes()
        );

        if (!extAllowed || !contentTypeAllowed) {
            log.warn("Từ chối khởi tạo video: định dạng hoặc MIME type không hợp lệ. filename={}, contentType={}",
                    request.filename(), request.contentType());
            throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        // 4. Sinh videoId duy nhất và cấu thành inputKey
        String videoId = VideoValidationHelper.generateVideoId();
        String inputKey = projectId + "/" + videoId + "/source." + ext;

        // 5. Ký S3 Presigned PUT URL
        String inputBucket = tvcProperties.getAws().getS3().getInputBucket();
        int ttlMinutes = tvcProperties.getAws().getS3().getPresignTtlMinutes();
        int ttlSeconds = ttlMinutes * 60;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(inputBucket)
                .key(inputKey)
                .contentType(request.contentType().trim())
                .contentLength(request.sizeBytes())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(ttlMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        String presignedUrl = presigned.url().toString();

        PresignedUploadInfo uploadInfo = new PresignedUploadInfo(
                presignedUrl,
                "PUT",
                ttlSeconds,
                Map.of(
                        "Content-Type", request.contentType().trim(),
                        "Content-Length", String.valueOf(request.sizeBytes())
                )
        );

        // 6. Lưu bản ghi VideoItem vào DynamoDB
        String now = Instant.now().toString();
        VideoItem videoItem = VideoItem.builder()
                .videoId(videoId)
                .projectId(projectId)
                .title(request.title().trim())
                .sizeBytes(request.sizeBytes())
                .contentType(request.contentType().trim())
                .status(VideoStatus.UPLOADING.name())
                .attemptCount(0)
                .inputKey(inputKey)
                .createdAt(now)
                .updatedAt(now)
                .build();

        videoRepository.save(videoItem);
        log.info("Khởi tạo Video thành công: videoId={}, projectId={}, inputKey={}", videoId, projectId, inputKey);

        VideoResponse videoResponse = videoMapper.toVideoResponse(videoItem);
        return new InitVideoResponse(videoResponse, uploadInfo);
    }

    /**
     * Kiểm tra tính hợp lệ của Project và quyền truy cập của người dùng / API Key.
     */
    private void validateProjectAccess(String projectId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        ProjectItem project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (ProjectItem.STATUS_DELETED.equals(project.getStatus())) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Object principal = authentication.getPrincipal();

        // 1. Nếu xác thực qua X-API-Key
        if (principal instanceof ApiKeyPrincipal apiKeyPrincipal) {
            if (!apiKeyPrincipal.projectId().equals(projectId)) {
                log.warn("API Key của Project {} không được phép truy cập Project {}",
                        apiKeyPrincipal.projectId(), projectId);
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
            return;
        }

        // 2. Nếu xác thực qua Bearer JWT
        String userId = null;
        if (principal instanceof Jwt jwt) {
            userId = jwt.getSubject();
        } else {
            userId = authentication.getName();
        }

        if (userId == null || !project.getOwnerId().equals(userId)) {
            log.warn("Truy cập trái phép vào Project {}: user yêu cầu={}, ownerId={}",
                    projectId, userId, project.getOwnerId());
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
