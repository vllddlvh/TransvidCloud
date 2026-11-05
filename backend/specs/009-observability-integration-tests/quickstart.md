# Quickstart & Validation Guide: F-009

**Feature**: F-009 — Quan sát hệ thống, Bảo mật Log & Bộ kiểm thử Tích hợp / Contract  
**Date**: 2026-11-01  

---

## 1. Yêu Cầu Tiên Quyết

- Java 21 LTS cài đặt trên máy hoặc sử dụng `./mvnw`.
- Docker daemon đang chạy (để Testcontainers khởi tạo LocalStack cho integration tests).
- Backend đang chạy cục bộ trên cổng `8080` (hoặc kiểm thử qua Maven test).

---

## 2. Kịch Bản Kiểm Thử Thủ Công Qua cURL

### Kịch bản 1: Tự động sinh `X-Correlation-ID` khi client không truyền

Gửi request gọi API Health Check hoặc bất kỳ endpoint nào:

```bash
curl -i http://localhost:8080/api/actuator/health
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Header trong response có chứa: `X-Correlation-ID: <uuid-random>` (ví dụ: `550e8400-e29b-41d4-a716-446655440000`).

---

### Kịch bản 2: Tiếp nhận và tái sử dụng `X-Correlation-ID` từ client

```bash
curl -i -H "X-Correlation-ID: my-custom-trace-9999" http://localhost:8080/api/actuator/health
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Header phản hồi trả về đúng: `X-Correlation-ID: my-custom-trace-9999`
- Console log của Backend hiển thị: `[corr=my-custom-trace-9999 ...]`

---

### Kịch bản 3: Kiểm chứng chuyển đổi định dạng log (Text vs JSON)

Khởi động ứng dụng với biến môi trường `LOG_FORMAT=json`:

```bash
LOG_FORMAT=json ./mvnw spring-boot:run
```

**Kết quả mong đợi:**
- Các dòng log in ra console dưới dạng JSON một dòng:
  ```json
  {"timestamp":"...","level":"INFO","logger":"...","message":"...","correlationId":"..."}
  ```

---

## 3. Chạy Kiểm Thử Tích Hợp Tự Động (Real Verification)

Chạy riêng 2 test class kiểm thử tích hợp mới của F-009 với Testcontainers LocalStack:

```bash
# 1. Chạy bài test Observability & Log Masking
./mvnw -B test -Dtest="ObservabilityLoggingIT"

# 2. Chạy bài test vòng đời video toàn diện
./mvnw -B test -Dtest="VideoFullLifecycleIT"
```

Chạy toàn bộ test suite dự án để khẳng định không có regression:

```bash
./scripts/verify.sh
```

**Kết quả mong đợi:**
- 100% test classes PASS.
- 0 failures, 0 errors, 0 flaky tests.
