package com.transvidcloud.backend.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("Unit Test: CorrelationIdFilter")
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        LogContext.clear();
    }

    @AfterEach
    void tearDown() {
        LogContext.clear();
    }

    @Test
    @DisplayName("Tự động sinh UUID mới khi client không truyền header tương quan")
    void shouldGenerateNewCorrelationIdWhenHeaderMissing() throws ServletException, IOException {
        FilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                assertThat(LogContext.getCorrelationId()).isNotBlank();
                // Phải là UUID hợp lệ
                UUID.fromString(LogContext.getCorrelationId());
            }
        };

        filter.doFilter(request, response, filterChain);

        String responseHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseHeader).isNotBlank();
        UUID.fromString(responseHeader);

        // Sau khi filter hoàn thành, MDC phải được giải phóng
        assertThat(MDC.get(LogContext.CORRELATION_ID)).isNull();
    }

    @Test
    @DisplayName("Tiếp nhận và tái sử dụng header X-Correlation-ID hợp lệ từ client")
    void shouldReuseValidCorrelationIdFromHeader() throws ServletException, IOException {
        String customId = "trace-client-12345_ABC";
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, customId);

        FilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                assertThat(LogContext.getCorrelationId()).isEqualTo(customId);
            }
        };

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo(customId);
        assertThat(MDC.get(LogContext.CORRELATION_ID)).isNull();
    }

    @Test
    @DisplayName("Tiếp nhận header fallback X-Request-ID khi X-Correlation-ID vắng mặt")
    void shouldFallbackToRequestIdWhenCorrelationIdMissing() throws ServletException, IOException {
        String fallbackId = "req-id-7890";
        request.addHeader(CorrelationIdFilter.REQUEST_ID_HEADER, fallbackId);

        FilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                assertThat(LogContext.getCorrelationId()).isEqualTo(fallbackId);
            }
        };

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo(fallbackId);
        assertThat(MDC.get(LogContext.CORRELATION_ID)).isNull();
    }

    @Test
    @DisplayName("Tự động sinh UUID mới thay thế nếu header chứa ký tự đặc biệt nguy hiểm")
    void shouldGenerateNewUuidIfHeaderContainsUnsafeCharacters() throws ServletException, IOException {
        String unsafeId = "bad<script>alert(1)</script>\nINJECTION";
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, unsafeId);

        FilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                String currentId = LogContext.getCorrelationId();
                assertThat(currentId).isNotEqualTo(unsafeId);
                UUID.fromString(currentId);
            }
        };

        filter.doFilter(request, response, filterChain);

        String responseHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseHeader).isNotEqualTo(unsafeId);
        UUID.fromString(responseHeader);
    }

    @Test
    @DisplayName("Tự động sinh UUID mới thay thế nếu header vượt quá 128 ký tự")
    void shouldGenerateNewUuidIfHeaderExceedsMaxLength() throws ServletException, IOException {
        String longId = "a".repeat(129);
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, longId);

        FilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                String currentId = LogContext.getCorrelationId();
                assertThat(currentId).isNotEqualTo(longId);
                UUID.fromString(currentId);
            }
        };

        filter.doFilter(request, response, filterChain);

        String responseHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseHeader).isNotEqualTo(longId);
        UUID.fromString(responseHeader);
    }

    @Test
    @DisplayName("Đảm bảo MDC được dọn dẹp an toàn ngay cả khi FilterChain ném ngoại lệ")
    void shouldClearMdcEvenWhenExceptionThrown() {
        FilterChain throwingChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new RuntimeException("Test unexpected failure");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Test unexpected failure");

        assertThat(MDC.get(LogContext.CORRELATION_ID)).isNull();
    }
}
