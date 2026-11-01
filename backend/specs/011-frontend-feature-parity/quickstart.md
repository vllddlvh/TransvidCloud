# Quickstart & Validation Guide: F-011 — Đồng bộ Đầy đủ Tính năng Frontend

**Feature**: `011-frontend-feature-parity`  
**Date**: 2026-11-01T21:51:00+07:00

---

## 1. Điều kiện tiên quyết (Prerequisites)

- Docker & Docker Compose đang chạy 4 services (`localstack`, `backend`, `worker`, `edge`):
  ```bash
  make up
  make smoke
  ```
- Node.js $\ge 20$ và dependencies trong `frontend/` đã được cài đặt.

---

## 2. Kiểm thử Unit, Contract & Build trên Frontend

Chạy toàn bộ cổng kiểm tra tự động của Frontend:

```bash
cd frontend
npm run api:check
npm test
npm run build
```

**Kết quả kỳ vọng:**
- `npm run api:check`: Không có sai lệch giữa `/contracts/openapi.yaml`, `/docs/contracts/openapi.yaml` và `src/api/schema.d.ts`.
- `npm test`: 100% test suites vượt qua (bao gồm các test mới cho API Keys, Edit/Delete Project, Quality Selector `Auto / 720p / 360p`, Delete Video, Upload `title`).
- `npm run build`: TypeScript type-check (`tsc -b`) và Vite production bundle thành công (`exit code 0`).

---

## 3. Kiểm thử Playwright Real-Stack E2E (Trình duyệt thật + Docker Stack)

1. Đảm bảo Vite dev server ở chế độ kết nối Backend thật đang chạy:
   ```bash
   cd frontend && npm run dev:real
   ```
2. Chạy Playwright E2E trên stack thật (`http://localhost:8000`):
   ```bash
   cd frontend
   E2E_REAL_STACK=1 E2E_BASE_URL=http://localhost:8000 npx playwright test e2e/real-stack.spec.ts
   ```

**Các kịch bản E2E được kiểm chứng tự động:**
1. **Auth & Project CRUD (`F-003`, `F-004`):** Đăng ký tài khoản $\rightarrow$ Tạo Project $\rightarrow$ Chỉnh sửa tên/mô tả/webhook Project (`PATCH`).
2. **Developer API Keys (`F-005`):** Tạo API Key mới $\rightarrow$ Nhận `rawKey` (`tvc_live_...`) hiển thị 1 lần $\rightarrow$ Kiểm tra `keyPrefix` trong danh sách $\rightarrow$ Thu hồi (`Revoke`) API Key.
3. **Upload kèm `title` & Multi-Rendition Playback (`F-006`, `F-007`, `F-008`):** Upload video với tiêu đề tùy chỉnh $\rightarrow$ Worker transcode `COMPLETED` $\rightarrow$ Kiểm tra 4 đường dẫn `assets` (`Master`, `720p`, `360p`, `Thumbnail`) $\rightarrow$ Chuyển đổi chất lượng phát giữa `Auto`, `720p`, `360p` trên `HlsPlayer`.
4. **Xóa Video & Xóa Project (`F-008`, `F-004`):** Xóa video (`DELETE /api/videos/{videoId}`) $\rightarrow$ Xóa mềm Project (`DELETE /api/projects/{projectId}`).
