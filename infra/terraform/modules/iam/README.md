# C2 — runtime IAM

Hai role chỉ trust EC2 và có instance profile riêng. Không tạo access key.

| Quyền | API | Worker |
|---|---|---|
| S3 | GetObject/PutObject input | GetObject input; PutObject/AbortMultipartUpload output |
| SQS jobs | SendMessage | ReceiveMessage/DeleteMessage/ChangeMessageVisibility/GetQueueAttributes |
| DynamoDB | Get/Put/Update/Delete/Query/BatchGet trên bốn bảng và index | GetItem/UpdateItem trên videos |
| ECR | Pull tvc-backend | Pull tvc-worker |
| Parameter Store | GetParameter(s) tại /tvc/dev/api/* và token Tunnel | GetParameter(s) tại /tvc/dev/worker/* |
| CloudWatch Logs | CreateLogStream/PutLogEvents tại /tvc/dev/api | Tương tự tại /tvc/dev/worker |
| ASG | Không | SetInstanceProtection trên tvc-dev-worker-asg |

`AmazonSSMManagedInstanceCore` hỗ trợ SSM Agent/Session Manager. Policy managed này có quyền đọc parameter rộng; inline policy bổ sung explicit Deny GetParameter/GetParameters ngoài path runtime riêng. Không cấp GetParametersByPath; bootstrap phải đọc theo tên parameter đã biết. Không gán thêm policy rộng vào hai role.

Tên repository, log group và ASG là tài nguyên dự kiến cho C3/C5; module IAM không tạo các tài nguyên đó. Cần tạo log group trước khi dùng awslogs vì role không được CreateLogGroup. App review lại quyền dựa trên lời gọi SDK thực tế; hiện chưa kiểm chứng trên EC2 hay IAM simulator. Worker biết ASG name qua cấu hình, không cần quyền Describe mọi ASG.

Đợt này chỉ tạo IAM (8 resources): hai role, hai profile, hai SSM policy attachment và hai inline policy. C2 còn cần secret/config SSM và kiểm thử quyền âm trên AWS; CI/deploy role thuộc C6 chưa tạo.

## Secret và cấu hình SSM

Chưa tạo secret hoặc giá trị giả trong Terraform: giá trị SecureString vẫn có thể được lưu trong state nếu quản lý bằng resource Terraform thông thường. Khi App/Tunnel sẵn sàng, người vận hành tạo SecureString qua Console:

- `/tvc/dev/api/jwt-secret`: thống nhất với cấu hình backend trước khi dùng.
- `/tvc/dev/cloudflared/token`: token Tunnel thật, theo Designs.
- `/tvc/dev/worker/image-tag`: String chứa SHA đã push lên ECR (tạo ở C3).

JWT/Tunnel không được ghi vào repo, tfvars, log hoặc chat. Nếu chọn customer-managed KMS key, phải bổ sung decrypt chỉ đúng key và encryption context; chưa có quyền CMK trong module này. Chưa dùng runtime config trong EC2 vì ứng dụng chưa có source.
