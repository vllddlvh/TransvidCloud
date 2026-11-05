package com.transvidcloud.backend.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestExceptionController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    record SampleRequest(
            @NotBlank(message = "Tên không được để trống")
            @Size(min = 3, message = "Tên phải có ít nhất 3 ký tự")
            String name
    ) {}

    @RestController
    static class TestExceptionController {

        @PostMapping("/test/validation")
        public String testValidation(@Valid @RequestBody SampleRequest request) {
            return "OK: " + request.name();
        }

        @GetMapping("/test/type-mismatch/{id}")
        public String testTypeMismatch(@PathVariable Integer id) {
            return "ID: " + id;
        }

        @GetMapping("/test/app-exception/upload-limit")
        public void testUploadLimit() {
            throw new AppException(ErrorCode.UPLOAD_LIMIT_EXCEEDED);
        }

        @GetMapping("/test/app-exception/unauthenticated")
        public void testUnauthenticated() {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        @GetMapping("/test/app-exception/unauthorized")
        public void testUnauthorized() {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        @GetMapping("/test/app-exception/not-found")
        public void testNotFound() {
            throw new AppException(ErrorCode.NOT_FOUND);
        }

        @GetMapping("/test/app-exception/conflict")
        public void testConflict() {
            throw new AppException(ErrorCode.CONFLICT);
        }

        @GetMapping("/test/app-exception/unsupported-media")
        public void testUnsupportedMedia() {
            throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        @GetMapping("/test/unhandled")
        public void testUnhandled() {
            throw new RuntimeException("Sensitive database stacktrace or internal detail");
        }
    }

    @Test
    @DisplayName("Validation thất bại trả về HTTP 400 với mã VALIDATION_ERROR và trường details")
    void shouldHandleValidationErrors() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", is("Dữ liệu yêu cầu không hợp lệ")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.details.name", notNullValue()));
    }

    @Test
    @DisplayName("JSON malformed trả về HTTP 400 với mã MALFORMED_JSON")
    void shouldHandleMalformedJson() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MALFORMED_JSON")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("Sai kiểu dữ liệu tham số URL trả về HTTP 400 với mã TYPE_MISMATCH")
    void shouldHandleTypeMismatch() throws Exception {
        mockMvc.perform(get("/test/type-mismatch/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("TYPE_MISMATCH")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("Phương thức HTTP không hỗ trợ trả về HTTP 405 với mã METHOD_NOT_ALLOWED")
    void shouldHandleMethodNotAllowed() throws Exception {
        mockMvc.perform(put("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"abc\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code", is("METHOD_NOT_ALLOWED")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("AppException UPLOAD_LIMIT_EXCEEDED trả về HTTP 413")
    void shouldHandleUploadLimitExceeded() throws Exception {
        mockMvc.perform(get("/test/app-exception/upload-limit"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code", is("UPLOAD_LIMIT_EXCEEDED")))
                .andExpect(jsonPath("$.message", is(ErrorCode.UPLOAD_LIMIT_EXCEEDED.getDefaultMessage())))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("AppException UNAUTHENTICATED trả về HTTP 401")
    void shouldHandleUnauthenticated() throws Exception {
        mockMvc.perform(get("/test/app-exception/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("AppException UNAUTHORIZED trả về HTTP 403")
    void shouldHandleUnauthorized() throws Exception {
        mockMvc.perform(get("/test/app-exception/unauthorized"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("AppException NOT_FOUND trả về HTTP 404")
    void shouldHandleNotFound() throws Exception {
        mockMvc.perform(get("/test/app-exception/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("AppException CONFLICT trả về HTTP 409")
    void shouldHandleConflict() throws Exception {
        mockMvc.perform(get("/test/app-exception/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("AppException UNSUPPORTED_MEDIA_TYPE trả về HTTP 422")
    void shouldHandleUnsupportedMediaType() throws Exception {
        mockMvc.perform(get("/test/app-exception/unsupported-media"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("UNSUPPORTED_MEDIA_TYPE")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("Lỗi hệ thống chưa phân loại trả về HTTP 500, tuyệt đối không lộ stack trace")
    void shouldHandleUnhandledExceptionSafely() throws Exception {
        mockMvc.perform(get("/test/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code", is("INTERNAL_SERVER_ERROR")))
                .andExpect(jsonPath("$.message", is(ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage())))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.message", not(containsString("Sensitive database stacktrace"))));
    }
}
