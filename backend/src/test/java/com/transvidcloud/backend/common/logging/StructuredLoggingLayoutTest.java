package com.transvidcloud.backend.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test: StructuredLoggingLayout")
class StructuredLoggingLayoutTest {

    @Mock
    private ILoggingEvent loggingEvent;

    @Test
    @DisplayName("Định dạng Text có cấu trúc cho console kèm thông tin MDC và Masking")
    void shouldFormatTextWithMdcAndMasking() {
        StructuredLoggingLayout layout = new StructuredLoggingLayout();
        layout.setJsonFormat(false);

        when(loggingEvent.getTimeStamp()).thenReturn(1762000000000L);
        when(loggingEvent.getThreadName()).thenReturn("test-worker-1");
        when(loggingEvent.getLevel()).thenReturn(Level.INFO);
        when(loggingEvent.getLoggerName()).thenReturn("com.transvidcloud.backend.video.VideoService");
        when(loggingEvent.getFormattedMessage()).thenReturn("User logged in with password=plainPassword123");
        when(loggingEvent.getMDCPropertyMap()).thenReturn(Map.of(
                LogContext.CORRELATION_ID, "corr-abc-123",
                LogContext.USER_ID, "usr_111",
                LogContext.PROJECT_ID, "prj_222",
                LogContext.VIDEO_ID, "vid_333"
        ));

        String output = layout.doLayout(loggingEvent);

        assertThat(output).contains("[test-worker-1]");
        assertThat(output).contains("INFO");
        assertThat(output).contains("[corr=corr-abc-123 user=usr_111 prj=prj_222 vid=vid_333]");
        assertThat(output).contains("password=[PROTECTED]");
        assertThat(output).doesNotContain("plainPassword123");
    }

    @Test
    @DisplayName("Định dạng Single-line JSON khi cấu hình jsonFormat = true")
    void shouldFormatJsonWhenEnabled() {
        StructuredLoggingLayout layout = new StructuredLoggingLayout();
        layout.setJsonFormat(true);

        when(loggingEvent.getTimeStamp()).thenReturn(1762000000000L);
        when(loggingEvent.getLevel()).thenReturn(Level.INFO);
        when(loggingEvent.getLoggerName()).thenReturn("com.transvidcloud.backend.auth.AuthService");
        when(loggingEvent.getFormattedMessage()).thenReturn("Created API key with rawApiKey=tvc_live_1234567890");
        when(loggingEvent.getMDCPropertyMap()).thenReturn(Map.of(
                LogContext.CORRELATION_ID, "trace-555",
                LogContext.PROJECT_ID, "prj_777"
        ));

        String output = layout.doLayout(loggingEvent);

        assertThat(output).startsWith("{").endsWith("}\n");
        assertThat(output).contains("\"level\":\"INFO\"");
        assertThat(output).contains("\"correlationId\":\"trace-555\"");
        assertThat(output).contains("\"projectId\":\"prj_777\"");
        assertThat(output).contains("rawApiKey=[PROTECTED]");
        assertThat(output).doesNotContain("tvc_live_1234567890");
    }
}
