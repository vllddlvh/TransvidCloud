# Data Model & Frontend Type Contracts: F-011 — Đồng bộ Đầy đủ Tính năng Frontend theo Đặc tả Backend

**Feature**: `011-frontend-feature-parity`  
**Date**: 2026-11-01T21:50:30+07:00

---

## 1. Thực thể & Giao diện TypeScript (`frontend/src/api/types.ts`)

### 1.1. `UserSummary` & `Session` (`F-003`)
```ts
export interface UserSummary {
  userId: string
  email: string
  name: string
  fullName?: string
  createdAt?: string
}

export interface Session {
  accessToken: string
  expiresAt: string
  user: UserSummary
}
```
- **Validation / Normalization**:
  - `normalizeUserSummary(value)`: Chấp nhận `fullName` hoặc `name` (fallback về phần trước `@` của `email`), kiểm tra `userId` không rỗng và `email` đúng định dạng.

### 1.2. `Project` & `UpdateProjectRequest` (`F-004`)
```ts
export interface Project {
  projectId: string
  ownerId?: string
  name: string
  description: string
  webhookUrl?: string
  status?: 'ACTIVE' | 'DELETED'
  videoCount: number
  createdAt: string
  updatedAt?: string
}

export interface UpdateProjectRequest {
  name?: string
  description?: string
  webhookUrl?: string | null
}
```
- **Validation Rules (`F-004`)**:
  - `name`: Chuỗi không rỗng, độ dài 1–120 ký tự.
  - `description`: Tối đa 500 ký tự (tùy chọn).
  - `webhookUrl`: Định dạng URL hợp lệ (`http://` hoặc `https://`) hoặc `null`/`""` để gỡ bỏ.

### 1.3. `ApiKey` & `CreatedApiKey` (`F-005`)
```ts
export interface ApiKey {
  keyHash: string
  projectId: string
  name: string
  keyPrefix: string
  createdAt: string
}

export interface CreatedApiKey extends ApiKey {
  rawKey: string
}

export interface CreateApiKeyRequest {
  name: string
}
```
- **Validation Rules (`F-005`)**:
  - `name`: Chuỗi không rỗng, độ dài 1–100 ký tự.
  - `keyHash`: Chuỗi SHA-256 hex không rỗng.
  - `keyPrefix`: Tiền tố nhận diện (`tvc_live_xxxxxxxx...`).
  - `rawKey`: Chuỗi khóa bí mật đầy đủ (`tvc_live_<32_hex_chars>`), chỉ tồn tại trong phản hồi `201 Created` của `POST /api/projects/{projectId}/api-keys`.
  - Giới hạn tối đa: **5 API Keys** đang hoạt động cho mỗi Project.

### 1.4. `Video`, `VideoAssets` & `VideoPage` (`F-006`, `F-007`, `F-008`)
```ts
export interface Video {
  videoId: string
  projectId: string
  filename: string
  title?: string
  inputKey?: string
  sizeBytes: number
  contentType: string
  status: VideoStatus
  attemptCount?: number
  createdAt: string
  updatedAt: string
  startedAt?: string
  completedAt?: string
  errorCode?: string
  durationSeconds?: number
  assets?: {
    hls: string
    hls360?: string
    hls720?: string
    thumbnail?: string
  }
}

export interface VideoPage {
  items: Video[]
  nextCursor?: string
  hasMore: boolean
}
```
- **Validation & State Transitions (`F-006` $\rightarrow$ `F-008`)**:
  - `title`: Chuỗi 1–200 ký tự.
  - `status`: `UPLOADING` $\rightarrow$ `PENDING` $\rightarrow$ `PROCESSING` $\rightarrow$ `COMPLETED` | `FAILED_VALIDATION` | `FAILED_TRANSCODE` | `FAILED_STORAGE`.
  - Quy tắc xóa video (`DELETE /api/videos/{videoId}`): Chỉ cho phép khi `status !== 'PROCESSING'`.
  - Quy tắc xóa project (`DELETE /api/projects/{projectId}`): Chỉ cho phép khi không có video nào thuộc `{UPLOADING, PENDING, PROCESSING}`.

---

## 2. Hợp đồng Phương thức trong `frontend/src/api/client.ts`

| Phương thức Client | HTTP Endpoint | Request Body / Query | Phản hồi |
|---|---|---|---|
| `api.login(input)` | `POST /api/auth/login` | `LoginRequest` | `Session` |
| `api.register(input)` | `POST /api/auth/register` | `RegisterRequest` | `Session` |
| `api.getMe()` | `GET /api/auth/me` | — | `UserSummary` |
| `api.listProjects()` | `GET /api/projects` | — | `Project[]` |
| `api.getProject(projectId)` | `GET /api/projects/{projectId}` | — | `Project` |
| `api.createProject(input)` | `POST /api/projects` | `CreateProjectRequest` | `Project` |
| `api.updateProject(projectId, input)` | `PATCH /api/projects/{projectId}` | `UpdateProjectRequest` | `Project` |
| `api.deleteProject(projectId)` | `DELETE /api/projects/{projectId}` | — | `void` (`204 No Content`) |
| `api.listApiKeys(projectId)` | `GET /api/projects/{projectId}/api-keys` | — | `ApiKey[]` |
| `api.createApiKey(projectId, input)` | `POST /api/projects/{projectId}/api-keys` | `CreateApiKeyRequest` | `CreatedApiKey` (`201 Created`) |
| `api.revokeApiKey(projectId, keyHash)` | `DELETE /api/projects/{projectId}/api-keys/{keyHash}` | — | `void` (`204 No Content`) |
| `api.listVideos(projectId)` | `GET /api/projects/{projectId}/videos` | — | `Video[]` |
| `api.listVideosPage(projectId, opts)` | `GET /api/projects/{projectId}/videos?limit=&cursor=` | `{ limit?, cursor? }` | `VideoPage` |
| `api.createVideo(projectId, input)` | `POST /api/projects/{projectId}/videos` | `CreateVideoRequest` | `UploadTicket` (`201 Created`) |
| `api.confirmVideo(videoId)` | `POST /api/videos/{videoId}/confirm` | — | `Video` (`202` / `200`) |
| `api.getVideo(videoId)` | `GET /api/videos/{videoId}` | — | `Video` |
| `api.deleteVideo(videoId)` | `DELETE /api/videos/{videoId}` | — | `void` (`204 No Content`) |
