package com.transvidcloud.backend.video.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.transvidcloud.backend.common.exception.AppException;
import com.transvidcloud.backend.common.exception.ErrorCode;
import com.transvidcloud.backend.common.exception.GlobalExceptionHandler;
import com.transvidcloud.backend.video.dto.ConfirmVideoResult;
import com.transvidcloud.backend.video.dto.InitVideoRequest;
import com.transvidcloud.backend.video.dto.InitVideoResponse;
import com.transvidcloud.backend.video.dto.PresignedUploadInfo;
import com.transvidcloud.backend.video.dto.VideoAssets;
import com.transvidcloud.backend.video.dto.VideoPageResponse;
import com.transvidcloud.backend.video.dto.VideoResponse;
import com.transvidcloud.backend.video.entity.VideoStatus;
import com.transvidcloud.backend.video.service.VideoService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
@DisplayName("VideoController Web Slice Test")
class VideoControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private VideoService videoService;

    private MockMvc mockMvc;

    private final Authentication mockPrincipal = new UsernamePasswordAuthenticationToken("usr_test123", null, List.of());
    private final String projectId = "prj_1c05d9e2a3f4";

    @BeforeEach
    void setUp() {
        VideoController controller = new VideoController(videoService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /projects/{projectId}/videos: Thành công trả về HTTP 201 Created và cấu trúc InitVideoResponse")
    void testInitVideoUpload_Success_Returns201() throws Exception {
        InitVideoRequest request = new InitVideoRequest(
                "Khóa học Spring Boot",
                "lecture-01.mp4",
                52428800L,
                "video/mp4"
        );

        VideoResponse videoResponse = new VideoResponse(
                "vid_4a77e1c8d102",
                projectId,
                "Khóa học Spring Boot",
                VideoStatus.UPLOADING,
                52428800L,
                "video/mp4",
                0,
                null,
                null,
                Instant.now(),
                Instant.now(),
                null,
                null
        );

        PresignedUploadInfo uploadInfo = new PresignedUploadInfo(
                "http://localhost:4566/tvc-local-input/prj_1c05d9e2a3f4/vid_4a77e1c8d102/source.mp4?sig=abc",
                "PUT",
                900,
                Map.of("Content-Type", "video/mp4", "Content-Length", "52428800")
        );

        InitVideoResponse response = new InitVideoResponse(videoResponse, uploadInfo);
        when(videoService.initVideoUpload(eq(projectId), any(InitVideoRequest.class), any()))
                .thenReturn(response);

        mockMvc.perform(post("/projects/{projectId}/videos", projectId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.video.videoId", is("vid_4a77e1c8d102")))
                .andExpect(jsonPath("$.video.projectId", is(projectId)))
                .andExpect(jsonPath("$.video.title", is("Khóa học Spring Boot")))
                .andExpect(jsonPath("$.video.status", is("UPLOADING")))
                .andExpect(jsonPath("$.video.attemptCount", is(0)))
                .andExpect(jsonPath("$.upload.method", is("PUT")))
                .andExpect(jsonPath("$.upload.expiresInSeconds", is(900)))
                .andExpect(jsonPath("$.upload.requiredHeaders.Content-Type", is("video/mp4")))
                .andExpect(jsonPath("$.upload.requiredHeaders.Content-Length", is("52428800")));
    }

    @Test
    @DisplayName("POST /projects/{projectId}/videos: Lỗi validation (title để trống hoặc sizeBytes <= 0) trả về HTTP 400")
    void testInitVideoUpload_ValidationError_Returns400() throws Exception {
        InitVideoRequest invalidRequest = new InitVideoRequest(
                "", // Blank title
                "demo.mp4",
                0L, // Invalid sizeBytes
                "video/mp4"
        );

        mockMvc.perform(post("/projects/{projectId}/videos", projectId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("POST /projects/{projectId}/videos: Dung lượng vượt quá 200MB trả về HTTP 413 Payload Too Large")
    void testInitVideoUpload_PayloadTooLarge_Returns413() throws Exception {
        InitVideoRequest request = new InitVideoRequest("Demo", "demo.mp4", 209715201L, "video/mp4");
        when(videoService.initVideoUpload(eq(projectId), any(InitVideoRequest.class), any()))
                .thenThrow(new AppException(ErrorCode.UPLOAD_LIMIT_EXCEEDED));

        mockMvc.perform(post("/projects/{projectId}/videos", projectId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code", is("UPLOAD_LIMIT_EXCEEDED")));
    }

    @Test
    @DisplayName("POST /projects/{projectId}/videos: Định dạng không hỗ trợ trả về HTTP 422 Unprocessable Entity")
    void testInitVideoUpload_UnsupportedMediaType_Returns422() throws Exception {
        InitVideoRequest request = new InitVideoRequest("Demo", "demo.avi", 1000L, "video/x-msvideo");
        when(videoService.initVideoUpload(eq(projectId), any(InitVideoRequest.class), any()))
                .thenThrow(new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

        mockMvc.perform(post("/projects/{projectId}/videos", projectId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UNSUPPORTED_MEDIA_TYPE")));
    }

    @Test
    @DisplayName("POST /projects/{projectId}/videos: Không có quyền truy cập Project trả về HTTP 403 Forbidden")
    void testInitVideoUpload_AccessDenied_Returns403() throws Exception {
        InitVideoRequest request = new InitVideoRequest("Demo", "demo.mp4", 1000L, "video/mp4");
        when(videoService.initVideoUpload(eq(projectId), any(InitVideoRequest.class), any()))
                .thenThrow(new AppException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(post("/projects/{projectId}/videos", projectId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("POST /projects/{projectId}/videos: Project không tồn tại trả về HTTP 404 Not Found")
    void testInitVideoUpload_ProjectNotFound_Returns404() throws Exception {
        InitVideoRequest request = new InitVideoRequest("Demo", "demo.mp4", 1000L, "video/mp4");
        when(videoService.initVideoUpload(eq(projectId), any(InitVideoRequest.class), any()))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(post("/projects/{projectId}/videos", projectId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }

    // ==========================================
    // Tests cho POST /videos/{videoId}/confirm (F-007)
    // ==========================================

    private VideoResponse createTestVideoResponse(String videoId, VideoStatus status) {
        return new VideoResponse(
                videoId,
                projectId,
                "Demo Video",
                status,
                52428800L,
                "video/mp4",
                0,
                null,
                null,
                Instant.now(),
                Instant.now(),
                null,
                null
        );
    }

    @Test
    @DisplayName("POST /videos/{videoId}/confirm: Thành công lần đầu trả về HTTP 202 Accepted")
    void testConfirmVideoUpload_NewlyConfirmed_Returns202() throws Exception {
        String videoId = "vid_4a77e1c8d102";
        VideoResponse videoResponse = createTestVideoResponse(videoId, VideoStatus.PENDING);
        ConfirmVideoResult result = new ConfirmVideoResult(videoResponse, true);

        when(videoService.confirmVideoUpload(eq(videoId), any())).thenReturn(result);

        mockMvc.perform(post("/videos/{videoId}/confirm", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.videoId", is(videoId)))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.sizeBytes", is(52428800)));
    }

    @Test
    @DisplayName("POST /videos/{videoId}/confirm: Gọi lặp Idempotent trả về HTTP 200 OK")
    void testConfirmVideoUpload_Idempotent_Returns200() throws Exception {
        String videoId = "vid_4a77e1c8d102";
        VideoResponse videoResponse = createTestVideoResponse(videoId, VideoStatus.PENDING);
        ConfirmVideoResult result = new ConfirmVideoResult(videoResponse, false);

        when(videoService.confirmVideoUpload(eq(videoId), any())).thenReturn(result);

        mockMvc.perform(post("/videos/{videoId}/confirm", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId", is(videoId)))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    @DisplayName("POST /videos/{videoId}/confirm: Tệp S3 không tồn tại trả về HTTP 422 S3_OBJECT_NOT_FOUND")
    void testConfirmVideoUpload_S3NotFound_Returns422() throws Exception {
        String videoId = "vid_4a77e1c8d102";
        when(videoService.confirmVideoUpload(eq(videoId), any()))
                .thenThrow(new AppException(ErrorCode.S3_OBJECT_NOT_FOUND));

        mockMvc.perform(post("/videos/{videoId}/confirm", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("S3_OBJECT_NOT_FOUND")));
    }

    @Test
    @DisplayName("POST /videos/{videoId}/confirm: Tệp S3 rỗng 0 byte trả về HTTP 422 UPLOAD_SIZE_MISMATCH")
    void testConfirmVideoUpload_UploadSizeMismatch_Returns422() throws Exception {
        String videoId = "vid_4a77e1c8d102";
        when(videoService.confirmVideoUpload(eq(videoId), any()))
                .thenThrow(new AppException(ErrorCode.UPLOAD_SIZE_MISMATCH));

        mockMvc.perform(post("/videos/{videoId}/confirm", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UPLOAD_SIZE_MISMATCH")));
    }

    @Test
    @DisplayName("POST /videos/{videoId}/confirm: Tệp S3 vượt 200MB trả về HTTP 413 UPLOAD_LIMIT_EXCEEDED")
    void testConfirmVideoUpload_UploadLimitExceeded_Returns413() throws Exception {
        String videoId = "vid_4a77e1c8d102";
        when(videoService.confirmVideoUpload(eq(videoId), any()))
                .thenThrow(new AppException(ErrorCode.UPLOAD_LIMIT_EXCEEDED));

        mockMvc.perform(post("/videos/{videoId}/confirm", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code", is("UPLOAD_LIMIT_EXCEEDED")));
    }

    @Test
    @DisplayName("POST /videos/{videoId}/confirm: Không có quyền truy cập Project trả về HTTP 403 Forbidden")
    void testConfirmVideoUpload_AccessDenied_Returns403() throws Exception {
        String videoId = "vid_4a77e1c8d102";
        when(videoService.confirmVideoUpload(eq(videoId), any()))
                .thenThrow(new AppException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(post("/videos/{videoId}/confirm", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("POST /videos/{videoId}/confirm: Video không tồn tại trả về HTTP 404 RESOURCE_NOT_FOUND")
    void testConfirmVideoUpload_VideoNotFound_Returns404() throws Exception {
        String videoId = "vid_unknown";
        when(videoService.confirmVideoUpload(eq(videoId), any()))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(post("/videos/{videoId}/confirm", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("POST /videos/{videoId}/confirm: Video ở trạng thái FAILED_* trả về HTTP 409 INVALID_STATE_TRANSITION")
    void testConfirmVideoUpload_InvalidStateTransition_Returns409() throws Exception {
        String videoId = "vid_failed";
        when(videoService.confirmVideoUpload(eq(videoId), any()))
                .thenThrow(new AppException(ErrorCode.INVALID_STATE_TRANSITION));

        mockMvc.perform(post("/videos/{videoId}/confirm", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_STATE_TRANSITION")));
    }

    // ==========================================
    // Tests cho GET /projects/{projectId}/videos (F-008 US1 & US4)
    // ==========================================

    @Test
    @DisplayName("GET /projects/{projectId}/videos: Thành công trả về HTTP 200 OK kèm VideoPageResponse")
    void testListVideosByProject_Success_Returns200() throws Exception {
        VideoResponse v1 = createTestVideoResponse("vid_001", VideoStatus.COMPLETED);
        VideoPageResponse pageResponse = new VideoPageResponse(List.of(v1), "next-cursor-str", true);

        when(videoService.listVideosByProject(eq(projectId), eq(20), eq("sample-cursor"), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/projects/{projectId}/videos", projectId)
                        .param("limit", "20")
                        .param("cursor", "sample-cursor")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].videoId", is("vid_001")))
                .andExpect(jsonPath("$.nextCursor", is("next-cursor-str")))
                .andExpect(jsonPath("$.hasMore", is(true)));
    }

    @Test
    @DisplayName("GET /projects/{projectId}/videos: Cursor không hợp lệ trả về HTTP 400 INVALID_CURSOR")
    void testListVideosByProject_InvalidCursor_Returns400() throws Exception {
        when(videoService.listVideosByProject(eq(projectId), any(), eq("corrupted-cursor"), any()))
                .thenThrow(new AppException(ErrorCode.INVALID_CURSOR));

        mockMvc.perform(get("/projects/{projectId}/videos", projectId)
                        .param("cursor", "corrupted-cursor")
                        .principal(mockPrincipal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_CURSOR")));
    }

    @Test
    @DisplayName("GET /projects/{projectId}/videos: Limit ngoài phạm vi 1..100 trả về HTTP 400 VALIDATION_ERROR")
    void testListVideosByProject_LimitOutOfRange_Returns400() throws Exception {
        when(videoService.listVideosByProject(eq(projectId), eq(0), any(), any()))
                .thenThrow(new AppException(ErrorCode.VALIDATION_ERROR));

        mockMvc.perform(get("/projects/{projectId}/videos", projectId)
                        .param("limit", "0")
                        .principal(mockPrincipal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("GET /projects/{projectId}/videos: Không có quyền truy cập trả về HTTP 403 ACCESS_DENIED")
    void testListVideosByProject_AccessDenied_Returns403() throws Exception {
        when(videoService.listVideosByProject(eq(projectId), any(), any(), any()))
                .thenThrow(new AppException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(get("/projects/{projectId}/videos", projectId)
                        .principal(mockPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    // ==========================================
    // Tests cho GET /videos/{videoId} (F-008 US2 & US4)
    // ==========================================

    @Test
    @DisplayName("GET /videos/{videoId}: Thành công khi video COMPLETED trả về HTTP 200 OK kèm Assets đầy đủ")
    void testGetVideoDetails_Success_Returns200WithAssets() throws Exception {
        String videoId = "vid_comp_001";
        VideoAssets assets = new VideoAssets(
                "/media/prj/vid/hls/master.m3u8",
                "/media/prj/vid/hls/360p/index.m3u8",
                "/media/prj/vid/hls/720p/index.m3u8",
                "/media/prj/vid/thumb/default.jpg"
        );
        VideoResponse videoResponse = new VideoResponse(
                videoId,
                projectId,
                "Bài Giảng Hay",
                VideoStatus.COMPLETED,
                52428800L,
                "video/mp4",
                0,
                null,
                assets,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now()
        );

        when(videoService.getVideoDetails(eq(videoId), any())).thenReturn(videoResponse);

        mockMvc.perform(get("/videos/{videoId}", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId", is(videoId)))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.assets.masterPlaylistUrl", is("/media/prj/vid/hls/master.m3u8")))
                .andExpect(jsonPath("$.assets.hls360Url", is("/media/prj/vid/hls/360p/index.m3u8")))
                .andExpect(jsonPath("$.assets.thumbnailUrl", is("/media/prj/vid/thumb/default.jpg")));
    }

    @Test
    @DisplayName("GET /videos/{videoId}: Video không tồn tại trả về HTTP 404 RESOURCE_NOT_FOUND")
    void testGetVideoDetails_NotFound_Returns404() throws Exception {
        String videoId = "vid_not_found";
        when(videoService.getVideoDetails(eq(videoId), any()))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/videos/{videoId}", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /videos/{videoId}: Chưa xác thực trả về HTTP 401 UNAUTHENTICATED")
    void testGetVideoDetails_Unauthenticated_Returns401() throws Exception {
        String videoId = "vid_comp_001";
        when(videoService.getVideoDetails(eq(videoId), any()))
                .thenThrow(new AppException(ErrorCode.UNAUTHENTICATED));

        mockMvc.perform(get("/videos/{videoId}", videoId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }

    @Test
    @DisplayName("GET /videos/{videoId}: Không có quyền truy cập trả về HTTP 403 ACCESS_DENIED")
    void testGetVideoDetails_AccessDenied_Returns403() throws Exception {
        String videoId = "vid_comp_001";
        when(videoService.getVideoDetails(eq(videoId), any()))
                .thenThrow(new AppException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(get("/videos/{videoId}", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    // ==========================================
    // Tests cho DELETE /videos/{videoId} (F-008 US3 & US4)
    // ==========================================

    @Test
    @DisplayName("DELETE /videos/{videoId}: Thành công trả về HTTP 204 No Content")
    void testDeleteVideo_Success_Returns204() throws Exception {
        String videoId = "vid_to_delete";

        mockMvc.perform(delete("/videos/{videoId}", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isNoContent());

        verify(videoService).deleteVideo(eq(videoId), any());
    }

    @Test
    @DisplayName("DELETE /videos/{videoId}: Video đang PROCESSING trả về HTTP 409 INVALID_STATE_TRANSITION")
    void testDeleteVideo_ProcessingConflict_Returns409() throws Exception {
        String videoId = "vid_processing";
        doThrow(new AppException(ErrorCode.INVALID_STATE_TRANSITION))
                .when(videoService).deleteVideo(eq(videoId), any());

        mockMvc.perform(delete("/videos/{videoId}", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_STATE_TRANSITION")));
    }

    @Test
    @DisplayName("DELETE /videos/{videoId}: Video không tồn tại trả về HTTP 404 RESOURCE_NOT_FOUND")
    void testDeleteVideo_NotFound_Returns404() throws Exception {
        String videoId = "vid_unknown";
        doThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND))
                .when(videoService).deleteVideo(eq(videoId), any());

        mockMvc.perform(delete("/videos/{videoId}", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("DELETE /videos/{videoId}: Không có quyền xóa trả về HTTP 403 ACCESS_DENIED")
    void testDeleteVideo_AccessDenied_Returns403() throws Exception {
        String videoId = "vid_forbidden";
        doThrow(new AppException(ErrorCode.ACCESS_DENIED))
                .when(videoService).deleteVideo(eq(videoId), any());

        mockMvc.perform(delete("/videos/{videoId}", videoId)
                        .principal(mockPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }
}
