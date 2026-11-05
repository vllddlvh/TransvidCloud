package com.transvidcloud.backend.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter tự động trích xuất hoặc khởi tạo mã tương quan (Correlation ID) cho mọi request HTTP.
 * Đính kèm vào MDC và HTTP response header để phục vụ truy vết phân tán.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final int MAX_CORRELATION_ID_LENGTH = 128;
    private static final Pattern SAFE_CORRELATION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9-_]+$");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);

        LogContext.setCorrelationId(correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            LogContext.clear();
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String headerValue = request.getHeader(CORRELATION_ID_HEADER);
        if (headerValue == null || headerValue.isBlank()) {
            headerValue = request.getHeader(REQUEST_ID_HEADER);
        }

        if (isValidCorrelationId(headerValue)) {
            return headerValue.trim();
        }

        return UUID.randomUUID().toString();
    }

    private boolean isValidCorrelationId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String trimmed = id.trim();
        return trimmed.length() <= MAX_CORRELATION_ID_LENGTH
                && SAFE_CORRELATION_ID_PATTERN.matcher(trimmed).matches();
    }
}
