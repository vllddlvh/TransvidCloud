package com.transvidcloud.backend.video.controller;

import com.transvidcloud.backend.video.dto.ConfirmVideoResult;
import com.transvidcloud.backend.video.dto.InitVideoRequest;
import com.transvidcloud.backend.video.dto.InitVideoResponse;
import com.transvidcloud.backend.video.dto.VideoResponse;
import com.transvidcloud.backend.video.service.VideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho các tác vụ khởi tạo và quản lý Video.
 */
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
