package com.transvidcloud.backend.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cấu trúc thông điệp điều phối công việc chuyển mã video gửi tới SQS tvc-{env}-jobs.
 * Tuân thủ nghiêm ngặt contracts/sqs-job-message.schema.json (schemaVersion: 1, additionalProperties: false).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SqsJobMessage(
        int schemaVersion,
        String jobId,
        String videoId,
        String projectId,
        String inputKey,
        String requestedAt
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public SqsJobMessage(String jobId, String videoId, String projectId, String inputKey, String requestedAt) {
        this(CURRENT_SCHEMA_VERSION, jobId, videoId, projectId, inputKey, requestedAt);
    }
}
