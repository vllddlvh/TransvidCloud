package com.transvidcloud.backend.video.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transvidcloud.backend.apikey.security.ApiKeyPrincipal;
import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.common.logging.LogContext;
import com.transvidcloud.backend.project.entity.ProjectItem;
import com.transvidcloud.backend.project.repository.ProjectRepository;
import com.transvidcloud.backend.video.dto.ConfirmVideoResult;
import com.transvidcloud.backend.video.dto.CursorCodec;
import com.transvidcloud.backend.video.dto.InitVideoRequest;
import com.transvidcloud.backend.video.dto.InitVideoResponse;
import com.transvidcloud.backend.video.dto.PresignedUploadInfo;
import com.transvidcloud.backend.video.dto.SqsJobMessage;
import com.transvidcloud.backend.video.dto.VideoCursor;
import com.transvidcloud.backend.video.dto.VideoPageResponse;
import com.transvidcloud.backend.video.dto.VideoResponse;
import com.transvidcloud.backend.video.entity.OutboxItem;
import com.transvidcloud.backend.video.entity.VideoItem;
import com.transvidcloud.backend.video.entity.VideoStatus;
import com.transvidcloud.backend.video.mapper.VideoMapper;
import com.transvidcloud.backend.video.repository.OutboxRepository;
import com.transvidcloud.backend.video.repository.PagedVideoResult;
import com.transvidcloud.backend.video.repository.VideoRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
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

    private static final Set<String> FAILED_STATUSES = Set.of(
            VideoStatus.FAILED_VALIDATION.name(),
            VideoStatus.FAILED_TRANSCODE.name(),
            VideoStatus.FAILED_STORAGE.name()
    );

    private final VideoRepository videoRepository;
    private final ProjectRepository projectRepository;
    private final VideoMapper videoMapper;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final OutboxRepository outboxRepository;
    private final OutboxPublisherService outboxPublisherService;
    private final ObjectMapper objectMapper;
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
        LogContext.setVideoId(videoId);
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

        LogContext.setProjectId(projectId);

        ProjectItem project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (ProjectItem.STATUS_DELETED.equals(project.getStatus())) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Object principal = authentication.getPrincipal();

        // 1. Nếu xác thực qua X-API-Key
        if (principal instanceof ApiKeyPrincipal apiKeyPrincipal) {
            LogContext.setUserId(apiKeyPrincipal.getName());
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
        LogContext.setUserId(userId);

        if (userId == null || !project.getOwnerId().equals(userId)) {
            log.warn("Truy cập trái phép vào Project {}: user yêu cầu={}, ownerId={}",
                    projectId, userId, project.getOwnerId());
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * Xác nhận video đã upload lên S3, chuyển trạng thái nguyên tử UPLOADING -> PENDING
     * kèm ghi OutboxItem và điều phối SQS Job.
     * Thao tác có tính chất idempotent:
     * - Lần đầu tiên thành công: trả về newlyConfirmed = true (HTTP 202 Accepted).
     * - Gọi lặp khi video đã ở PENDING/PROCESSING/COMPLETED: trả về newlyConfirmed = false (HTTP 200 OK).
     *
     * @param videoId        Mã định danh video
     * @param authentication Ngữ cảnh xác thực Spring Security
     * @return ConfirmVideoResult chứa VideoResponse và cờ newlyConfirmed
     */
    public ConfirmVideoResult confirmVideoUpload(String videoId, Authentication authentication) {
        LogContext.setVideoId(videoId);
        // 1. Tra cứu bản ghi VideoItem
        VideoItem video = videoRepository.findByVideoId(videoId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        // 2. Kiểm tra quyền sở hữu Project
        validateProjectAccess(video.getProjectId(), authentication);

        String currentStatus = video.getStatus();

        // 3. Chặn nếu video ở trạng thái lỗi kết thúc chu trình
        if (FAILED_STATUSES.contains(currentStatus)) {
            log.warn("Từ chối confirm: video {} đang ở trạng thái lỗi {}", videoId, currentStatus);
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        // 4. Xử lý Idempotent: nếu video đã ở PENDING, PROCESSING hoặc COMPLETED
        if (VideoStatus.PENDING.name().equals(currentStatus)
                || VideoStatus.PROCESSING.name().equals(currentStatus)
                || VideoStatus.COMPLETED.name().equals(currentStatus)) {
            log.info("Xác nhận lặp lại (Idempotent): videoId={}, status={}", videoId, currentStatus);

            // Nếu OutboxItem vẫn còn ở PENDING (do lần gửi trước bị gián đoạn mạng), thử kích hoạt lại
            outboxRepository.findByTargetVideoId(videoId).ifPresent(outbox -> {
                if (OutboxItem.STATUS_PENDING.equals(outbox.getOutboxStatus())) {
                    log.info("Thử phát lại Outbox còn tồn đọng cho videoId={}", videoId);
                    outboxPublisherService.publishOutboxItem(outbox);
                }
            });

            VideoResponse videoResponse = videoMapper.toVideoResponse(video);
            return new ConfirmVideoResult(videoResponse, false);
        }

        // 5. Kiểm tra đối tượng trên S3 thông qua HeadObject
        HeadObjectResponse headResponse = checkS3Object(video);
        Long contentLength = headResponse.contentLength();

        // 6. Chuẩn bị thông điệp SQS và bản ghi OutboxItem
        String now = Instant.now().toString();
        String jobId = VideoValidationHelper.generateJobId();
        LogContext.setJobId(jobId);

        SqsJobMessage jobMessage = new SqsJobMessage(
                jobId,
                videoId,
                video.getProjectId(),
                video.getInputKey(),
                now
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(jobMessage);
        } catch (JsonProcessingException e) {
            log.error("Lỗi đóng gói JSON SqsJobMessage: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        OutboxItem outboxItem = OutboxItem.builder()
                .videoId(OutboxItem.buildPk(videoId))
                .projectId(OutboxItem.GSI_PARTITION_PENDING)
                .createdAt(now)
                .jobId(jobId)
                .targetVideoId(videoId)
                .targetProjectId(video.getProjectId())
                .inputKey(video.getInputKey())
                .payload(payload)
                .outboxStatus(OutboxItem.STATUS_PENDING)
                .attemptCount(0)
                .updatedAt(now)
                .build();

        video.setStatus(VideoStatus.PENDING.name());
        video.setSizeBytes(contentLength);
        video.setUpdatedAt(now);

        // 7. Thực thi giao dịch nguyên tử TransactWriteItems
        try {
            videoRepository.confirmVideoWithOutboxInTransaction(video, outboxItem);
        } catch (TransactionCanceledException ex) {
            log.warn("Xung đột đồng thời khi confirm: videoId={}, nạp lại trạng thái mới nhất", videoId);
            VideoItem latestVideo = videoRepository.findByVideoId(videoId)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

            if (VideoStatus.UPLOADING.name().equals(latestVideo.getStatus())) {
                throw ex;
            }
            return new ConfirmVideoResult(videoMapper.toVideoResponse(latestVideo), false);
        }

        // 8. Fast-path: Phát hành tức thì lên SQS
        outboxPublisherService.publishOutboxItem(outboxItem);

        log.info("Xác nhận upload thành công: videoId={}, projectId={}, jobId={}, sizeBytes={}",
                videoId, video.getProjectId(), jobId, contentLength);

        VideoResponse videoResponse = videoMapper.toVideoResponse(video);
        return new ConfirmVideoResult(videoResponse, true);
    }

    /**
     * Kiểm tra tính tồn tại và giới hạn dung lượng đối tượng tệp trên S3.
     */
    private HeadObjectResponse checkS3Object(VideoItem video) {
        String inputBucket = tvcProperties.getAws().getS3().getInputBucket();
        HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(inputBucket)
                .key(video.getInputKey())
                .build();

        HeadObjectResponse headResponse;
        try {
            headResponse = s3Client.headObject(headRequest);
        } catch (NoSuchKeyException ex) {
            log.warn("Tệp đối tượng không tồn tại trên S3: bucket={}, key={}", inputBucket, video.getInputKey());
            throw new AppException(ErrorCode.S3_OBJECT_NOT_FOUND);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                log.warn("S3 trả về 404 cho key={}", video.getInputKey());
                throw new AppException(ErrorCode.S3_OBJECT_NOT_FOUND);
            }
            log.error("Lỗi khi gọi S3 HeadObject: key={}, error={}", video.getInputKey(), ex.getMessage(), ex);
            throw ex;
        }

        Long contentLength = headResponse.contentLength();
        if (contentLength == null || contentLength <= 0) {
            log.warn("Tệp trên S3 có kích thước không hợp lệ (0 byte): key={}", video.getInputKey());
            throw new AppException(ErrorCode.UPLOAD_SIZE_MISMATCH);
        }

        if (contentLength > tvcProperties.getUpload().getMaxBytes()) {
            log.warn("Tệp trên S3 vượt quá dung lượng tối đa (200MB): size={}, max={}",
                    contentLength, tvcProperties.getUpload().getMaxBytes());
            throw new AppException(ErrorCode.UPLOAD_LIMIT_EXCEEDED);
        }

        return headResponse;
    }

    /**
     * Liệt kê danh sách video trong Project kèm phân trang con trỏ (Cursor Pagination).
     *
     * @param projectId      Định danh dự án
     * @param limit          Số lượng video tối đa một trang (1..100, mặc định 20)
     * @param cursor         Con trỏ Base64URL trang trước hoặc null/rỗng nếu là trang đầu
     * @param authentication Ngữ cảnh xác thực Spring Security
     * @return VideoPageResponse chứa danh sách VideoResponse, nextCursor và cờ hasMore
     */
    public VideoPageResponse listVideosByProject(String projectId, Integer limit, String cursor, Authentication authentication) {
        validateProjectAccess(projectId, authentication);

        int effectiveLimit = (limit != null) ? limit : 20;
        if (effectiveLimit < 1 || effectiveLimit > 100) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        VideoCursor decodedCursor = CursorCodec.decode(objectMapper, cursor, projectId);
        Map<String, AttributeValue> exclusiveStartKey = CursorCodec.toExclusiveStartKey(decodedCursor);

        PagedVideoResult pagedResult = videoRepository.findVideosByProjectWithPagination(
                projectId,
                effectiveLimit,
                exclusiveStartKey
        );

        List<VideoResponse> items = pagedResult.items().stream()
                .map(videoMapper::toVideoResponse)
                .toList();

        String nextCursor = CursorCodec.encodeFromLastEvaluatedKey(objectMapper, pagedResult.lastEvaluatedKey());
        boolean hasMore = (nextCursor != null);

        return new VideoPageResponse(items, nextCursor, hasMore);
    }

    /**
     * Lấy thông tin chi tiết của một Video cụ thể, tự động ghép tiền tố TVC_MEDIA_BASE_URL cho assets nếu COMPLETED.
     *
     * @param videoId        Định danh video
     * @param authentication Ngữ cảnh xác thực Spring Security
     * @return VideoResponse chi tiết
     */
    public VideoResponse getVideoDetails(String videoId, Authentication authentication) {
        LogContext.setVideoId(videoId);
        VideoItem video = videoRepository.findByVideoId(videoId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        validateProjectAccess(video.getProjectId(), authentication);

        return videoMapper.toVideoResponse(video);
    }

    /**
     * Xóa video (Fast Delete).
     * Từ chối HTTP 409 Conflict (INVALID_STATE_TRANSITION) nếu video đang ở trạng thái PROCESSING.
     *
     * @param videoId        Định danh video
     * @param authentication Ngữ cảnh xác thực Spring Security
     */
    public void deleteVideo(String videoId, Authentication authentication) {
        LogContext.setVideoId(videoId);
        VideoItem video = videoRepository.findByVideoId(videoId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        validateProjectAccess(video.getProjectId(), authentication);

        if (VideoStatus.PROCESSING.name().equals(video.getStatus())) {
            log.warn("Từ chối xóa video {}: video đang ở trạng thái PROCESSING", videoId);
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        videoRepository.deleteVideoWithOutbox(videoId);
        log.info("Đã xóa thành công video: videoId={}, projectId={}", videoId, video.getProjectId());
    }
}
