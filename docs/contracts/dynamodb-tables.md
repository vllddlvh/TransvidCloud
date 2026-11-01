# DynamoDB tables (DRAFT)

Tên bảng: `tvc-{env}-{table}` (local: `tvc-local-*`). Billing: on-demand.

| Bảng | Partition key | GSI | Dùng cho |
|---|---|---|---|
| `users` | `userId` | `email-index` (PK `email`) | Đăng nhập theo email |
| `projects` | `projectId` | `owner-index` (PK `ownerId`, SK `createdAt`) | Liệt kê project của user; chứa `webhookUrl`, `webhookSecret` |
| `apikeys` | `keyHash` | `project-index` (PK `projectId`) | Xác thực API key (chỉ lưu hash) |
| `videos` | `videoId` | `project-index` (PK `projectId`, SK `createdAt`) | Liệt kê video theo project; nguồn sự thật về trạng thái |

## Trường chính của `videos`
`videoId`, `projectId`, `title`, `sizeBytes`, `contentType`, `status`, `attemptCount`, `inputKey`, `assets` (map S3 keys: `hls`, `hls360`, `hls720`, `thumbnail`), `errorCode`, `createdAt`, `updatedAt`, `startedAt`, `completedAt`.

## Lưu ý
- Email duy nhất: dùng `TransactWriteItems` với item khóa `EMAIL#<email>` + `attribute_not_exists`.
- Phân trang bằng `LastEvaluatedKey` (cursor), không dùng offset.
- Thay đổi key schema phải sửa đồng thời: file này, `local/localstack/init/01-resources.sh`, Terraform module `database`.
