package com.transvidcloud.backend.video.controller;

import com.transvidcloud.backend.video.dto.InitVideoRequest;
import com.transvidcloud.backend.video.dto.InitVideoResponse;
import com.transvidcloud.backend.video.service.VideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho các tác vụ khởi tạo và quản lý Video.
 */
@RestController
@RequestMapping("/projects/{projectId}/videos")
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
    @PostMapping
    public ResponseEntity<InitVideoResponse> initVideoUpload(
            @PathVariable String projectId,
            @Valid @RequestBody InitVideoRequest request,
            Authentication authentication
    ) {
        InitVideoResponse response = videoService.initVideoUpload(projectId, request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
