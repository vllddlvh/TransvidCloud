# infra/terraform/modules/

| Module | Tài nguyên | Chủ |
|---|---|---|
| `storage` | 3 bucket S3 (frontend/input/output), CORS, chặn public | Lưu |
| `queue` | SQS + DLQ (visibility 300s, maxReceiveCount 3), alarm DLQ | Lưu |
| `database` | 4 bảng DynamoDB + GSI | Lưu |
| `network` | VPC, public subnet, S3 Gateway Endpoint | Lưu |
| `iam` | Role riêng cho API, Worker, CI (least privilege) | Nguyên |
| `compute` | EC2 API, Launch Template + ASG Worker (1-4) | Nguyên |
| `edge` | CloudFront (3 behavior), OAC, hosting frontend | Lưu |
| `observability` | CloudWatch log/alarm, SNS, AWS Budgets, scaling policy | Nguyên |

Mỗi module có `main.tf`, `variables.tf`, `outputs.tf`. Module chỉ nhận đầu vào qua biến, không đọc tên môi trường cứng.
