# Bàn giao hạ tầng dev

## Làm rõ mô hình tài khoản — 25/10/2026

Ảnh AWS Settings của người vận hành cho thấy giao diện Projects của new AWS experience, project Idea Launchpad, Free plan và mục Activate advanced features. Theo tài liệu AWS, AWS quản lý SCP/RCP trong trải nghiệm này; không có cơ sở quy việc hạn chế Organizations cho quản trị trường. Người dùng sở hữu project qua Builder ID nhưng chưa có toàn quyền quản trị Organizations. Xem https://docs.aws.amazon.com/accounts/latest/reference/scps-and-rcps-for-projects.html.

Chưa chứng minh cần activate advanced features để chạy MVP hoặc để xử lý kết quả simulator. AWS yêu cầu nâng cấp gói trước khi activation; activation không đảo ngược và bỏ spend limits. Không hướng dẫn kích hoạt như một bước sửa lỗi IAM mặc định. Xem https://docs.aws.amazon.com/accounts/latest/reference/activate-advanced-features.html.

## Bằng chứng triển khai

Nguồn: kết quả `terraform output` do người vận hành gửi ngày 24/10/2026.

Bằng chứng sau triển khai do người vận hành cung cấp:

- `terraform plan` refresh tài nguyên AWS và trả `No changes. Your infrastructure matches the configuration.`
- S3 input: lifecycle Enabled, toàn bộ prefix, expiration 7 ngày.
- SQS jobs: visibility 300 giây, long polling 20 giây, redrive tới đúng DLQ với maxReceiveCount 3; managed SSE bật.
- Cả ba bucket: bốn cờ Block Public Access đều true. CORS input cho phép origin `http://localhost:8000`, GET/HEAD/POST/PUT, expose ETag.
- Bốn bảng và GSI đều ACTIVE, billing PAY_PER_REQUEST, partition key/index key/sort key khớp contract; projection ALL.
- Hai SG API/worker: inbound rỗng, outbound IPv4 toàn bộ theo thiết kế MVP.
- Route table `rtb-0029589f612a0c08e`: route `0.0.0.0/0` qua `igw-0c6ce227eb275713e` active, liên kết đúng hai subnet đã bàn giao; route S3 endpoint active.
- Endpoint `vpce-0999f249e84dc93be`: S3 Sydney, Gateway, available, gắn đúng route table public. Main route table còn lại chỉ có route local là bình thường; hai subnet dùng association riêng.
- Hai subnet: available, cùng VPC, AZ ap-southeast-2a/2b, CIDR 10.20.0.0/24 và 10.20.1.0/24, MapPublicIpOnLaunch true. Các kiểm tra kỹ thuật C1 đã đạt; chưa thay thế nghiệm thu M1 về contract/image ứng dụng.
- Cảnh báo deprecated hash_key/range_key chưa chặn plan; giữ nguyên schema contract khi cập nhật cú pháp provider.

- Account: `975473296238`; profile local: `tvc-dev`.
- Region: `ap-southeast-2` (Sydney), theo lựa chọn người vận hành.
- State bucket: `tvc-tfstate-975473296238-ap-southeast-2`; key: `dev/terraform.tfstate`.

| Hạng mục | Giá trị |
|---|---|
| Frontend bucket | `tvc-dev-frontend-975473296238-ap-southeast-2` |
| Input bucket | `tvc-dev-input-975473296238-ap-southeast-2` |
| Output bucket | `tvc-dev-output-975473296238-ap-southeast-2` |
| Jobs URL | `https://sqs.ap-southeast-2.amazonaws.com/975473296238/tvc-dev-jobs` |
| DLQ URL | `https://sqs.ap-southeast-2.amazonaws.com/975473296238/tvc-dev-jobs-dlq` |
| Users table | `tvc-dev-users` |
| Projects table | `tvc-dev-projects` |
| API keys table | `tvc-dev-apikeys` |
| Videos table | `tvc-dev-videos` |
| VPC | `vpc-01dcb29ed9f60d313` |
| Public subnet 1 | `subnet-057633aba0eb02fb2` |
| Public subnet 2 | `subnet-081d7944c61b5ee1e` |
| API security group | `sg-0e6e833d97d572300` |
| Worker security group | `sg-0173719b6d4afaafc` |

Ứng dụng AWS dùng instance profile, không dùng credential test hoặc endpoint LocalStack. Chưa có media URL CloudFront, ASG, image hay SSM runtime; bổ sung khi C2–C5 triển khai. Không thêm secret vào tệp bàn giao.

C2: người vận hành cung cấp log apply thành công 8 tạo/0 đổi/0 xóa và outputs:

| Danh tính | Role ARN | Instance profile ARN |
|---|---|---|
| API | `arn:aws:iam::975473296238:role/tvc-dev-api` | `arn:aws:iam::975473296238:instance-profile/tvc-dev-api` |
| Worker | `arn:aws:iam::975473296238:role/tvc-dev-worker` | `arn:aws:iam::975473296238:instance-profile/tvc-dev-worker` |

Hai role có inline runtime policy và attachment AmazonSSMManagedInstanceCore theo log apply. Kết quả simulator do người vận hành gửi: worker GetObject/PutObject input đều explicitDeny; API PutObject output explicitDeny; worker GetParameter secret API explicitDeny. GetObject input của worker phải allowed nên C2 chưa đạt kiểm tra quyền; cần kết quả mô phỏng đầy đủ để xác định nguồn deny, chưa thay đổi policy theo suy đoán. Chưa kiểm chứng trên EC2, cấu hình SSM runtime hoặc gắn profile vào EC2. Xem [phạm vi và quyền IAM](../../infra/terraform/modules/iam/README.md).

Chi tiết bổ sung: GetObject input có MatchedStatements rỗng, MissingContextValues rỗng, OrganizationsDecisionDetail.AllowedByOrganizations=false. Policy thực tế từ get-role-policy có Allow GetObject đúng input ARN. Kết quả simulator cho thấy từ chối ở đánh giá Organizations; chưa xác định SCP cụ thể hay hành vi thực tế trên EC2. Không nới quyền role để xử lý giới hạn tổ chức. Tiếp theo mô phỏng riêng inline policy bằng SimulateCustomPolicy; kết quả đó không thay thế đánh giá SCP/boundary/managed policy hoặc kiểm tra runtime.

Kiểm tra custom-policy tiếp theo ban đầu trả implicitDeny cả Get/Put do file request thực tế có PolicyInputList rỗng (đã đọc file tạm để xác nhận). Đã bổ sung `scripts/test-worker-iam.ps1`, đọc policy mới trực tiếp từ AWS, kiểm tra ReadInput và số lượng fragment trước khi mô phỏng. Chạy bằng profile tvc-dev thành công: 10 fragment, GetObject input allowed, PutObject input implicitDeny, exit code 0. Đây chỉ là bằng chứng inline policy; vấn đề Organizations ở principal simulation chưa được giải quyết.

Điều tra Organizations bằng profile tvc-dev: DescribeOrganization thành công, org `o-dhf0qi4dqu`, management account `880287984954`, FeatureSet ALL và SERVICE_CONTROL_POLICY ENABLED. ListPoliciesForTarget cho account `975473296238` trả AccessDeniedException. Chưa đọc được SCP trực tiếp hoặc kế thừa; cần quản trị Organizations xác minh policy hiệu lực cho role EC2 ở Sydney. Không khẳng định lỗi runtime từ kết quả mô phỏng và không sửa/nới SCP khi chưa có bằng chứng cụ thể.
