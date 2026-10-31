package com.transvidcloud.backend.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transvidcloud.backend.apikey.security.ApiKeyPrincipal;
import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.project.entity.ProjectItem;
import com.transvidcloud.backend.project.repository.ProjectRepository;
import com.transvidcloud.backend.video.dto.InitVideoRequest;
import com.transvidcloud.backend.video.dto.InitVideoResponse;
import com.transvidcloud.backend.video.entity.VideoItem;
import com.transvidcloud.backend.video.entity.VideoStatus;
import com.transvidcloud.backend.video.mapper.VideoMapper;
import com.transvidcloud.backend.video.repository.VideoRepository;
import java.net.URI;
import java.net.URL;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private Authentication authentication;

    private VideoMapper videoMapper;
    private TvcProperties tvcProperties;
    private VideoService videoService;

    private final String projectId = "prj_1c05d9e2a3f4";
    private final String ownerId = "usr_owner_001";

    @BeforeEach
    void setUp() {
        videoMapper = Mappers.getMapper(VideoMapper.class);
        tvcProperties = new TvcProperties();
        tvcProperties.getAws().getS3().setInputBucket("tvc-local-input");
        tvcProperties.getAws().getS3().setPresignTtlMinutes(15);
        tvcProperties.getUpload().setMaxBytes(209715200L);
        tvcProperties.getUpload().setAllowedContentTypes(List.of(
                "video/mp4", "video/quicktime", "video/x-matroska", "video/webm"
        ));

        videoService = new VideoService(
                videoRepository,
                projectRepository,
                videoMapper,
                s3Presigner,
                tvcProperties
        );
    }

    private ProjectItem createActiveProject() {
        return ProjectItem.builder()
                .projectId(projectId)
                .ownerId(ownerId)
                .status(ProjectItem.STATUS_ACTIVE)
                .build();
    }

    private void mockJwtUser(String userId) {
        Jwt jwt = mock(Jwt.class);
        lenient().when(jwt.getSubject()).thenReturn(userId);
        when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getPrincipal()).thenReturn(jwt);
    }

    private void mockPresigner(String returnUrl) throws Exception {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URI(returnUrl).toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);
    }

    @Test
    @DisplayName("initVideoUpload: Thành công cho SaaS Web User qua JWT -> Trả về 201 kèm Presigned URL và lưu UPLOADING")
    void testInitVideoUpload_Success_JwtUser() throws Exception {
        mockJwtUser(ownerId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(createActiveProject()));
        mockPresigner("http://localhost:4566/tvc-local-input/" + projectId + "/source.mp4?sig=123");

        InitVideoRequest request = new InitVideoRequest(
                "Bài giảng 1",
                "lecture-01.MP4",
                52428800L,
                "video/mp4"
        );

        InitVideoResponse response = videoService.initVideoUpload(projectId, request, authentication);

        assertThat(response).isNotNull();
        assertThat(response.video()).isNotNull();
        assertThat(response.video().title()).isEqualTo("Bài giảng 1");
        assertThat(response.video().status()).isEqualTo(VideoStatus.UPLOADING);
        assertThat(response.video().sizeBytes()).isEqualTo(52428800L);
        assertThat(response.video().attemptCount()).isEqualTo(0);

        assertThat(response.upload()).isNotNull();
        assertThat(response.upload().method()).isEqualTo("PUT");
        assertThat(response.upload().expiresInSeconds()).isEqualTo(900);
        assertThat(response.upload().requiredHeaders())
                .containsEntry("Content-Type", "video/mp4")
                .containsEntry("Content-Length", "52428800");

        ArgumentCaptor<VideoItem> captor = ArgumentCaptor.forClass(VideoItem.class);
        verify(videoRepository).save(captor.capture());
        VideoItem saved = captor.getValue();
        assertThat(saved.getVideoId()).startsWith("vid_");
        assertThat(saved.getProjectId()).isEqualTo(projectId);
        assertThat(saved.getInputKey()).isEqualTo(projectId + "/" + saved.getVideoId() + "/source.mp4");
        assertThat(saved.getStatus()).isEqualTo(VideoStatus.UPLOADING.name());
        assertThat(saved.getAttemptCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("initVideoUpload: Thành công cho Developer Application qua X-API-Key")
    void testInitVideoUpload_Success_ApiKey() throws Exception {
        ApiKeyPrincipal principal = new ApiKeyPrincipal(projectId, "Server Key", "tvc_live_12345678...");
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(createActiveProject()));
        mockPresigner("http://localhost:4566/tvc-local-input/" + projectId + "/source.mov");

        InitVideoRequest request = new InitVideoRequest(
                "Upload từ Worker",
                "sample.mov",
                10485760L,
                "video/quicktime"
        );

        InitVideoResponse response = videoService.initVideoUpload(projectId, request, authentication);

        assertThat(response).isNotNull();
        assertThat(response.video().status()).isEqualTo(VideoStatus.UPLOADING);
        assertThat(response.upload().presignedUrl()).contains("source.mov");

        verify(videoRepository).save(any(VideoItem.class));
    }

    @Test
    @DisplayName("initVideoUpload: Từ chối với HTTP 413 UPLOAD_LIMIT_EXCEEDED khi dung lượng vượt quá 200MB")
    void testInitVideoUpload_Throws413_WhenSizeExceeds200MB() {
        mockJwtUser(ownerId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(createActiveProject()));

        InitVideoRequest request = new InitVideoRequest(
                "Video Quá Lớn",
                "big.mp4",
                209715201L, // 200MB + 1 byte
                "video/mp4"
        );

        assertThatThrownBy(() -> videoService.initVideoUpload(projectId, request, authentication))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.UPLOAD_LIMIT_EXCEEDED));

        verify(videoRepository, never()).save(any());
    }

    @Test
    @DisplayName("initVideoUpload: Từ chối với HTTP 422 UNSUPPORTED_MEDIA_TYPE khi MIME type không được hỗ trợ")
    void testInitVideoUpload_Throws422_WhenContentTypeUnsupported() {
        mockJwtUser(ownerId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(createActiveProject()));

        InitVideoRequest request = new InitVideoRequest(
                "Video AVI",
                "clip.avi",
                1000000L,
                "video/x-msvideo"
        );

        assertThatThrownBy(() -> videoService.initVideoUpload(projectId, request, authentication))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

        verify(videoRepository, never()).save(any());
    }

    @Test
    @DisplayName("initVideoUpload: Từ chối với HTTP 422 UNSUPPORTED_MEDIA_TYPE khi đuôi tệp tin không hợp lệ")
    void testInitVideoUpload_Throws422_WhenExtensionUnsupported() {
        mockJwtUser(ownerId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(createActiveProject()));

        InitVideoRequest request = new InitVideoRequest(
                "Tệp không hợp lệ",
                "document.pdf",
                1000000L,
                "video/mp4"
        );

        assertThatThrownBy(() -> videoService.initVideoUpload(projectId, request, authentication))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

        verify(videoRepository, never()).save(any());
    }

    @Test
    @DisplayName("initVideoUpload: Từ chối với HTTP 403 ACCESS_DENIED khi JWT User không sở hữu Project")
    void testInitVideoUpload_Throws403_WhenJwtUserNotOwner() {
        mockJwtUser("usr_hacker_999");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(createActiveProject()));

        InitVideoRequest request = new InitVideoRequest("Demo", "video.mp4", 1000L, "video/mp4");

        assertThatThrownBy(() -> videoService.initVideoUpload(projectId, request, authentication))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED));

        verify(videoRepository, never()).save(any());
    }

    @Test
    @DisplayName("initVideoUpload: Từ chối với HTTP 403 ACCESS_DENIED khi API Key của Project A gọi vào Project B")
    void testInitVideoUpload_Throws403_WhenApiKeyCrossTenant() {
        ApiKeyPrincipal principal = new ApiKeyPrincipal("prj_other_AAA", "Key A", "prefix");
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(createActiveProject()));

        InitVideoRequest request = new InitVideoRequest("Demo", "video.mp4", 1000L, "video/mp4");

        assertThatThrownBy(() -> videoService.initVideoUpload(projectId, request, authentication))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED));

        verify(videoRepository, never()).save(any());
    }

    @Test
    @DisplayName("initVideoUpload: Từ chối với HTTP 404 RESOURCE_NOT_FOUND khi Project không tồn tại hoặc đã DELETED")
    void testInitVideoUpload_Throws404_WhenProjectNotFoundOrDeleted() {
        mockJwtUser(ownerId);
        when(projectRepository.findById("prj_unknown")).thenReturn(Optional.empty());

        InitVideoRequest request = new InitVideoRequest("Demo", "video.mp4", 1000L, "video/mp4");

        assertThatThrownBy(() -> videoService.initVideoUpload("prj_unknown", request, authentication))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        ProjectItem deletedProject = createActiveProject();
        deletedProject.setStatus(ProjectItem.STATUS_DELETED);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(deletedProject));

        assertThatThrownBy(() -> videoService.initVideoUpload(projectId, request, authentication))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
