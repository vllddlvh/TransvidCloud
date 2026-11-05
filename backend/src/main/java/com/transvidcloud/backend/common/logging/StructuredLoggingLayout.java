package com.transvidcloud.backend.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Layout Logback tùy biến hỗ trợ 2 chế độ xuất:
 * 1. Text màu có cấu trúc (cho môi trường local/test/dev).
 * 2. Single-line JSON (cho môi trường docker/prod hoặc khi LOG_FORMAT=json) phục vụ CloudWatch Logs Insights (Epic C5).
 * Tự động che giấu (masking) thông tin nhạy cảm trên cả 2 định dạng.
 */
public class StructuredLoggingLayout extends LayoutBase<ILoggingEvent> {

    private static final DateTimeFormatter TEXT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Boolean jsonFormatOverride = null;

    public void setJsonFormat(boolean jsonFormat) {
        this.jsonFormatOverride = jsonFormat;
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        if (event == null) {
            return "";
        }

        if (isJsonMode()) {
            return formatJson(event);
        } else {
            return formatText(event);
        }
    }

    private boolean isJsonMode() {
        if (jsonFormatOverride != null) {
            return jsonFormatOverride;
        }

        String envFormat = System.getenv("LOG_FORMAT");
        if ("json".equalsIgnoreCase(envFormat)) {
            return true;
        }

        String propFormat = System.getProperty("tvc.logging.format");
        if ("json".equalsIgnoreCase(propFormat)) {
            return true;
        }

        String activeProfiles = System.getProperty("spring.profiles.active");
        if (activeProfiles != null) {
            return activeProfiles.contains("docker") || activeProfiles.contains("prod");
        }

        return false;
    }

    private String formatJson(ILoggingEvent event) {
        Map<String, Object> logMap = new LinkedHashMap<>();
        logMap.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        logMap.put("level", event.getLevel().toString());
        logMap.put("logger", event.getLoggerName());

        String rawMessage = event.getFormattedMessage();
        String maskedMessage = MaskingMessageConverter.mask(rawMessage);
        logMap.put("message", maskedMessage);

        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc != null && !mdc.isEmpty()) {
            putIfPresent(logMap, "correlationId", mdc.get(LogContext.CORRELATION_ID));
            putIfPresent(logMap, "userId", mdc.get(LogContext.USER_ID));
            putIfPresent(logMap, "projectId", mdc.get(LogContext.PROJECT_ID));
            putIfPresent(logMap, "videoId", mdc.get(LogContext.VIDEO_ID));
            putIfPresent(logMap, "jobId", mdc.get(LogContext.JOB_ID));
        }

        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            logMap.put("stackTrace", MaskingMessageConverter.mask(formatStackTrace(throwableProxy)));
        }

        try {
            return objectMapper.writeValueAsString(logMap) + "\n";
        } catch (JsonProcessingException e) {
            return "{\"error\":\"Failed to serialize log to JSON: " + e.getMessage() + "\"}\n";
        }
    }

    private String formatText(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append(TEXT_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())));
        sb.append(" [").append(event.getThreadName()).append("] ");
        sb.append(String.format("%-5s", event.getLevel())).append(" ");
        sb.append(event.getLoggerName()).append(" ");

        Map<String, String> mdc = event.getMDCPropertyMap();
        sb.append("[corr=").append(getOrDefault(mdc, LogContext.CORRELATION_ID, "none"));
        sb.append(" user=").append(getOrDefault(mdc, LogContext.USER_ID, "none"));
        sb.append(" prj=").append(getOrDefault(mdc, LogContext.PROJECT_ID, "none"));
        sb.append(" vid=").append(getOrDefault(mdc, LogContext.VIDEO_ID, "none")).append("] - ");

        String rawMessage = event.getFormattedMessage();
        sb.append(MaskingMessageConverter.mask(rawMessage));
        sb.append("\n");

        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            sb.append(MaskingMessageConverter.mask(formatStackTrace(throwableProxy))).append("\n");
        }

        return sb.toString();
    }

    private String formatStackTrace(IThrowableProxy tp) {
        StringBuilder sb = new StringBuilder();
        sb.append(tp.getClassName()).append(": ").append(tp.getMessage()).append("\n");
        for (StackTraceElementProxy step : tp.getStackTraceElementProxyArray()) {
            sb.append("\tat ").append(step.getStackTraceElement().toString()).append("\n");
        }
        return sb.toString();
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private String getOrDefault(Map<String, String> mdc, String key, String defaultValue) {
        if (mdc == null) {
            return defaultValue;
        }
        String val = mdc.get(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
