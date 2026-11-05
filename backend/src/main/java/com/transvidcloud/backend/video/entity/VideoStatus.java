package com.transvidcloud.backend.video.entity;

/**
 * Trạng thái vòng đời của Video theo hợp đồng contracts/video-state-machine.md.
 */
public enum VideoStatus {
    UPLOADING,
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED_VALIDATION,
    FAILED_TRANSCODE,
    FAILED_STORAGE
}
