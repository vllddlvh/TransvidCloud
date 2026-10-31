package com.transvidcloud.backend.apikey.security;

import com.transvidcloud.backend.apikey.entity.ApiKeyItem;
import com.transvidcloud.backend.apikey.repository.ApiKeyRepository;
import com.transvidcloud.backend.apikey.util.ApiKeyGenerator;
import com.transvidcloud.backend.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter bảo mật cho xác thực qua X-API-Key:
 * - Ưu tiên Bearer JWT khi request chứa header Authorization: Bearer <token>.
 * - Xác thực qua X-API-Key: kiểm tra định dạng tvc_live_..., băm SHA-256, tra cứu DynamoDB bảng apikeys.
 * - Kiểm soát ranh giới Project: Chặn 403 Forbidden nếu API Key gọi chéo sang Project khác
 *   hoặc gọi vào các endpoint chỉ dành cho Web User (như quản lý Project, quản lý API Key).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_API_KEY = "X-API-Key";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private static final Pattern PROJECT_PATH_PATTERN = Pattern.compile("^(/api)?/projects/([^/]+)(/.*)?$");
    private static final Pattern PROJECT_ROOT_PATTERN = Pattern.compile("^(/api)?/projects/?$");
    private static final Pattern API_KEYS_PATTERN = Pattern.compile("^(/api)?/projects/[^/]+/api-keys(/.*)?$");

    private final ApiKeyRepository apiKeyRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(HEADER_AUTHORIZATION);
        // 1. Ưu tiên Authorization: Bearer <JWT> khi có mặt trong request
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Kiểm tra header X-API-Key
        String rawKey = request.getHeader(HEADER_API_KEY);
        if (rawKey != null) {
            String trimmedKey = rawKey.trim();
            if (trimmedKey.isEmpty() || !trimmedKey.startsWith(ApiKeyGenerator.PREFIX) || trimmedKey.length() != 41) {
                log.warn("Tiêu đề X-API-Key không đúng định dạng tvc_live_<32_hex>");
                writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHENTICATED.getCode(),
                        "Yêu cầu xác thực tài khoản hoặc token không hợp lệ");
                return;
            }

            try {
                String keyHash = ApiKeyGenerator.sha256Hex(trimmedKey);
                Optional<ApiKeyItem> apiKeyOpt = apiKeyRepository.findByKeyHash(keyHash);
                if (apiKeyOpt.isEmpty()) {
                    log.warn("Xác thực X-API-Key thất bại: keyHash không tồn tại hoặc đã bị thu hồi");
                    writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHENTICATED.getCode(),
                            "Yêu cầu xác thực tài khoản hoặc token không hợp lệ");
                    return;
                }

                ApiKeyItem item = apiKeyOpt.get();
                ApiKeyPrincipal principal = new ApiKeyPrincipal(item.getProjectId(), item.getName(), item.getKeyPrefix());
                ApiKeyAuthenticationToken authentication = new ApiKeyAuthenticationToken(
                        principal,
                        trimmedKey,
                        List.of(new SimpleGrantedAuthority("ROLE_API_KEY"))
                );

                // 3. Kiểm soát ranh giới Project và phân quyền endpoint cho API Key
                String path = request.getRequestURI();
                // 3.1. Chặn API Key gọi các endpoint chỉ dành cho Web User (tạo/list project hoặc quản lý api-keys)
                if (PROJECT_ROOT_PATTERN.matcher(path).matches() || API_KEYS_PATTERN.matcher(path).matches()) {
                    log.warn("API Key không được phép truy cập endpoint quản trị tài khoản cá nhân: path={}", path);
                    writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.ACCESS_DENIED.getCode(),
                            "Bạn không có quyền truy cập tài nguyên này");
                    return;
                }

                // 3.2. Chặn API Key của Project A truy cập tài nguyên của Project B
                Matcher projectMatcher = PROJECT_PATH_PATTERN.matcher(path);
                if (projectMatcher.matches()) {
                    String targetProjectId = projectMatcher.group(2);
                    if (!item.getProjectId().equals(targetProjectId)) {
                        log.warn("Truy cập trái phép: API Key thuộc Project {} nhưng cố truy cập Project {}",
                                item.getProjectId(), targetProjectId);
                        writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.ACCESS_DENIED.getCode(),
                                "Bạn không có quyền truy cập tài nguyên này");
                        return;
                    }
                }

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Xác thực thành công API Key cho Project: {}", item.getProjectId());
            } catch (Exception e) {
                log.error("Lỗi khi xử lý xác thực API Key: {}", e.getMessage(), e);
                writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHENTICATED.getCode(),
                        "Yêu cầu xác thực tài khoản hoặc token không hợp lệ");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String errorCode, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String json = """
                {"code":"%s","message":"%s","timestamp":"%s"}
                """.formatted(errorCode, message, Instant.now());
        response.getWriter().write(json);
    }
}
