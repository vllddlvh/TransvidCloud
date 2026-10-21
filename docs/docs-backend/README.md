# Backend Documentation

Tài liệu thiết kế và phát triển **Spring Boot Backend** của TransVidCloud. Đây là tài liệu Backend, không phải hướng dẫn harness/coding agent.

## Đọc theo thứ tự

1. [Introduction](01-introduction.md) — phạm vi và trách nhiệm Backend.
2. [Business Domain](02-business-domain.md) — các khái niệm và quy tắc nghiệp vụ.
3. [Requirements & Scope](03-requirements-and-scope.md) — chức năng, ưu tiên và tiêu chí chấp nhận.
4. [Design & Architecture](04-design-and-architecture.md) — module và dependency.
5. [API & Contracts](05-api-and-contracts.md), [Data Model](06-data-model.md), [Security](07-security-and-authorization.md).
6. [Upload & Processing Workflow](08-upload-and-processing-workflow.md) — luồng cốt lõi.
7. [Development](09-development-guide.md), [Testing](10-testing-and-quality.md), [Plan](11-plan-and-timeline.md).

## Nguồn sự thật

- **Giao diện giữa các thành phần:** [`../contracts/`](../contracts/). Đặc biệt: `openapi.yaml`, `video-state-machine.md`, `dynamodb-tables.md`, `sqs-job-message.schema.json`, `s3-key-convention.md`, `upload-limits.md`.
- **Tổng quan và lệnh chạy repo:** [`../README.md`](../README.md) cùng tài liệu dùng chung trong `../docs/`.
- **Thiết kế nội bộ Backend:** các file trong thư mục này.

Các contract hiện được đánh dấu **DRAFT**. Khi chưa có định nghĩa trong contract, tài liệu này sẽ đánh dấu `TBD` hoặc `Proposed`, không tự tạo một hợp đồng khác. Nếu code, docs và contract mâu thuẫn, cần xác minh với nhóm và cập nhật nguồn có thẩm quyền trong cùng thay đổi.

## Trạng thái quyết định

- **Confirmed**: đã được ghi trong repo/thiết kế nền tảng.
- **Proposed**: phương án thiết kế gợi ý, cần xác nhận nếu ảnh hưởng đến các module khác.
- **TBD**: chưa đủ thông tin hoặc đang chờ nhóm chốt.
