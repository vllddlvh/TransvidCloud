package com.transvidcloud.backend.video.controller;

import com.transvidcloud.backend.video.dto.ConfirmVideoResult;
import com.transvidcloud.backend.video.dto.InitVideoRequest;
import com.transvidcloud.backend.video.dto.InitVideoResponse;
import com.transvidcloud.backend.video.dto.VideoPageResponse;
import com.transvidcloud.backend.video.dto.VideoResponse;
import com.transvidcloud.backend.video.service.VideoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho các tác vụ khởi tạo và quản lý vòng đời Video.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    /**
     * Khởi tạo Video và nhận S3 Presigned PUT URL.
     * Hỗ trợ đồng thời Bearer JWT và X-API-Key.
     *
     * @param projectId      Mã định danh dự án
     * @param request        Thông tin video khai báo
     * @param authentication Ngữ cảnh xác thực Spring Security
     * @return InitVideoResponse kèm HTTP 201 Created
     */
    @PostMapping("/projects/{projectId}/videos")
    public ResponseEntity<InitVideoResponse> initVideoUpload(
            @PathVariable String projectId,
            @Valid @RequestBody InitVideoRequest request,
            Authentication authentication
    ) {
        InitVideoResponse response = videoService.initVideoUpload(projectId, request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Liệt kê danh sách Video trong Project kèm phân trang con trỏ (Cursor Pagination).
     * Sắp xếp theo createdAt giảm dần (mới nhất lên đầu).
     *
     * @param projectId      Mã định danh dự án
     * @param limit          Số lượng video tối đa một trang (1..100, mặc định 20)
     * @param cursor         Con trỏ Base64URL trang trước hoặc null/rỗng nếu là trang đầu
     * @param authentication Ngữ cảnh xác thực Spring Security
     * @return VideoPageResponse kèm HTTP 200 OK
     */
    @GetMapping("/projects/{projectId}/videos")
    public ResponseEntity<VideoPageResponse> listVideosByProject(
            @PathVariable String projectId,
            @RequestParam(required = false, defaultValue = "20") @Min(1) @Max(100) Integer limit,
            @RequestParam(required = false) String cursor,
            Authentication authentication
    ) {
        VideoPageResponse response = videoService.listVideosByProject(projectId, limit, cursor, authentication);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy chi tiết Video, trạng thái xử lý và danh mục Assets HLS / Thumbnail.
     *
     * @param videoId        Mã định danh video
     * @param authentication Ngữ cảnh xác thực Spring Security
     * @return VideoResponse kèm HTTP 200 OK
     */
    @GetMapping("/videos/{videoId}")
    public ResponseEntity<VideoResponse> getVideoDetails(
            @PathVariable String videoId,
            Authentication authentication
    ) {
        VideoResponse response = videoService.getVideoDetails(videoId, authentication);
        return ResponseEntity.ok(response);
    }

    /**
     * Xóa video (Fast Delete).
     * Từ chối HTTP 409 Conflict nếu video đang trong trạng thái PROCESSING.
     *
     * @param videoId        Mã định danh video
     * @param authentication Ngữ cảnh xác thực Spring Security
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/videos/{videoId}")
    public ResponseEntity<Void> deleteVideo(
            @PathVariable String videoId,
            Authentication authentication
    ) {
        videoService.deleteVideo(videoId, authentication);
        return ResponseEntity.noContent().build();
    }

    /**
     * Xác nhận đã tải video lên S3 (UPLOADING -> PENDING) và đưa job vào hàng đợi SQS.
     * Thao tác có tính chất idempotent:
     * - Lần đầu tiên: trả về HTTP 202 Accepted.
     * - Gọi lặp lại: trả về HTTP 200 OK.
     *
     * @param videoId        Mã định danh video
     * @param authentication Ngữ cảnh xác thực Spring Security
     * @return VideoResponse kèm mã HTTP phù hợp
     */
    @PostMapping("/videos/{videoId}/confirm")
    public ResponseEntity<VideoResponse> confirmVideoUpload(
            @PathVariable String videoId,
            Authentication authentication
    ) {
        ConfirmVideoResult result = videoService.confirmVideoUpload(videoId, authentication);
        HttpStatus status = result.newlyConfirmed() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.video());
    }
}
