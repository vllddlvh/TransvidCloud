package com.transvidcloud.backend.common.logging;

import org.slf4j.MDC;

/**
 * Tiện ích quản lý ngữ cảnh nhật ký (MDC - Mapped Diagnostic Context).
 * Đồng bộ các định danh nghiệp vụ vào luồng xử lý và đảm bảo dọn dẹp an toàn.
 */
public final class LogContext {

    public static final String CORRELATION_ID = "correlationId";
    public static final String USER_ID = "userId";
    public static final String PROJECT_ID = "projectId";
    public static final String VIDEO_ID = "videoId";
    public static final String JOB_ID = "jobId";

    private LogContext() {
        // Utility class
    }

    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CORRELATION_ID, correlationId);
        }
    }

    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID);
    }

    public static void setUserId(String userId) {
        if (userId != null && !userId.isBlank()) {
            MDC.put(USER_ID, userId);
        }
    }

    public static void setProjectId(String projectId) {
        if (projectId != null && !projectId.isBlank()) {
            MDC.put(PROJECT_ID, projectId);
        }
    }

    public static void setVideoId(String videoId) {
        if (videoId != null && !videoId.isBlank()) {
            MDC.put(VIDEO_ID, videoId);
        }
    }

    public static void setJobId(String jobId) {
        if (jobId != null && !jobId.isBlank()) {
            MDC.put(JOB_ID, jobId);
        }
    }

    public static void remove(String key) {
        MDC.remove(key);
    }

    public static void clear() {
        MDC.clear();
    }
}
