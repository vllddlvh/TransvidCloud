# TransVidCloud

Nền tảng xử lý và phân phối video trên AWS (môn Điện toán đám mây, UET-VNU). Một backend, hai cách dùng:
**SaaS Web** cho người dùng cuối và **Developer API** cho hệ thống bên ngoài; cùng dùng chung pipeline xử lý video.

```
Client -> CloudFront -> Cloudflare Tunnel -> EC2 API (Spring Boot) -> SQS -> Worker ASG (Go + FFmpeg) -> S3 output -> CloudFront -> người xem
                              |                                  |
                              +---------- DynamoDB <-------------+
```

## Bắt đầu nhanh
```bash
cp .env.example .env     # rồi điền LOCALSTACK_AUTH_TOKEN (xem docs/LOCAL_AND_CICD.md, mục 1.1)
make infra      # LocalStack: S3, SQS+DLQ, DynamoDB
make smoke      # kiểm tra tài nguyên đã tạo
```
Chi tiết (chạy full stack, kiểm thử, CI/CD lên AWS): [`docs/LOCAL_AND_CICD.md`](2.%20LOCAL_AND_CICD.md)
Việc cần làm của từng nhóm: [`docs/EPICS.md`](1.%20Timelines.md)

## Cấu trúc repo
```
transvidcloud/
├── README.md              (file này)
├── Makefile               lệnh dùng hằng ngày: make help
├── docker-compose.yml     local stack (LocalStack, backend, worker, nginx)
├── .env.example           biến môi trường mẫu
├── contracts/             HỢP ĐỒNG tích hợp: API, message SQS, state, bảng, S3 key
├── backend/               Spring Boot API
├── worker/                Go worker + FFmpeg (+ metricpub)
├── frontend/              React SaaS Web
├── infra/terraform/       hạ tầng AWS (modules + envs dev/demo)
├── local/                 chỉ phục vụ chạy local: init LocalStack, nginx, script test
├── scripts/               script deploy dùng chung (SSM, Instance Refresh)
├── tests/e2e/             test đầu-cuối chạy được cả local lẫn cloud
├── docs/                  tài liệu, ADR, báo cáo
└── .github/               CI/CD, CODEOWNERS
```

| Thư mục | Làm gì | Chủ |
|---|---|---|
| `contracts/` | Nguồn sự thật cho mọi giao diện giữa các thành phần | Phương (cả nhóm review) |
| `backend/` | API, xác thực, project/video, presigned URL, đẩy job vào SQS | Phương |
| `worker/` | Chuyển mã HLS 360p/720p, thumbnail, cập nhật trạng thái | Vũ |
| `frontend/` | Giao diện SaaS tối thiểu, trình phát HLS | Vũ |
| `infra/` | Terraform tạo S3, SQS, DynamoDB, VPC, IAM, EC2/ASG, CloudFront, CloudWatch | Lưu + Nguyên |
| `local/` + Docker | Giả lập AWS trên máy, Dockerfile, compose; đường đi local -> cloud | Lưu |
| `scripts/` | Deploy API (SSM) và Worker (Instance Refresh) | Nguyên |
| `tests/e2e/` | Kịch bản upload -> COMPLETED, chạy cả local và cloud | Lưu (App viết kịch bản) |
| `docs/` | Hướng dẫn, ADR, báo cáo LaTeX | Cả nhóm |
| `.github/` | Pipeline CI/CD, quy định duyệt PR | Nguyên |

Mỗi thư mục có `README.md` riêng mô tả trách nhiệm và quy tắc.

## Nguyên tắc làm việc
1. **Contract-first:** muốn đổi API, message, bảng hay S3 key thì sửa `contracts/` trước, cần cả bên App và Cloud duyệt.
2. **Local trước, cloud sau:** chỉ đẩy lên AWS khi luồng chính đã chạy được trên local.
3. **Cùng code, khác cấu hình:** local và cloud chỉ khác biến môi trường (`AWS_ENDPOINT_URL` có giá trị = LocalStack, để trống = AWS thật).
4. **Hạ tầng là mã:** mọi thay đổi AWS qua Terraform, không sửa tay trên console.
5. **Không commit bí mật:** `.env`, `*.tfvars`, access key. Secret trên cloud nằm ở SSM Parameter Store.

## Quy ước Git
- Nhánh: `main` (ổn định), nhánh làm việc `feature/<module>-<mô-tả>` hoặc theo tên cá nhân.
- Mọi thay đổi vào `main` qua Pull Request; CODEOWNERS yêu cầu người phụ trách thư mục duyệt.
- Commit ngắn gọn theo dạng `module: nội dung` (vd: `worker: thêm transcode 720p`).
