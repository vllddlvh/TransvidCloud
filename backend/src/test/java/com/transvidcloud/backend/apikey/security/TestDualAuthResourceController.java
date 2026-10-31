package com.transvidcloud.backend.apikey.security;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller phục vụ kiểm thử tích hợp cơ chế Dual Security (JWT + X-API-Key)
 * trên tài nguyên thuộc phạm vi Project.
 */
@RestController
@RequestMapping("/projects/{projectId}/test-dual-resource")
public class TestDualAuthResourceController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> getResource(
            @PathVariable String projectId,
            Authentication authentication
    ) {
        String authType = (authentication instanceof ApiKeyAuthenticationToken) ? "API_KEY" : "BEARER_JWT";
        return ResponseEntity.ok(Map.of(
                "projectId", projectId,
                "authType", authType,
                "principalName", authentication.getName()
        ));
    }
}
