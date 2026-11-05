# Feature Specification: Nền tảng Common, AWS Clients, CORS & Xử lý lỗi chuẩn (F-002)

**Feature Branch**: `002-common-aws-foundation`

**Created**: 2026-10-31

**Status**: Draft

**Input**: User description: "Feature F-002: Nền tảng Common, AWS Clients, CORS & Xử lý lỗi chuẩn (Epic A1/A2)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Phản hồi lỗi chuẩn hóa và thân thiện cho Client (Priority: P1)

Khi ứng dụng web của người dùng hoặc ứng dụng tích hợp của lập trình viên gửi một yêu cầu không hợp lệ (thiếu tham số, định dạng sai, không có quyền truy cập, hoặc hệ thống gặp sự cố), hệ thống luôn trả về phản hồi lỗi thống nhất theo một định dạng duy nhất đã được chuẩn hóa trong hợp đồng API. Người dùng và lập trình viên nhận được mã lỗi rõ ràng, mô tả lỗi dễ hiểu và chi tiết trường dữ liệu vi phạm mà không làm rò rỉ bất kỳ thông tin nhạy cảm hay lỗi nội bộ nào của hệ thống.

**Why this priority**: Đây là nền tảng cốt lõi của giao diện người dùng và API. Nếu không có định dạng lỗi chuẩn hóa, Frontend và các ứng dụng tích hợp bên ngoài sẽ không thể hiển thị thông báo lỗi chính xác, gây khó khăn cho việc xử lý sự cố và có nguy cơ để lộ thông tin bảo mật qua stack trace.

**Independent Test**: Có thể kiểm thử độc lập bằng cách gửi các request cố tình vi phạm validation (ví dụ body JSON rỗng, sai kiểu dữ liệu), truy cập endpoint không tồn tại, hoặc kích hoạt lỗi hệ thống, sau đó kiểm tra định dạng HTTP response trả về xem có đúng cấu trúc `{ "code", "message", "timestamp", "details" }` hay không.

**Acceptance Scenarios**:

1. **Given** Client gửi request với body JSON không đúng định dạng hoặc thiếu các trường bắt buộc, **When** hệ thống tiếp nhận request, **Then** hệ thống trả về mã trạng thái HTTP `400 Bad Request` kèm mã lỗi máy đọc được `VALIDATION_ERROR`, thông điệp hướng dẫn rõ ràng, và danh sách các trường vi phạm trong trường `details`.
2. **Given** Client truy cập tài nguyên hoặc thực hiện hành vi vi phạm điều kiện nghiệp vụ hoặc không có quyền hợp lệ, **When** request được xử lý, **Then** hệ thống trả về mã trạng thái tương ứng (`401`, `403`, `404`, `409`, `413`, `422`) cùng mã lỗi định danh (`UNAUTHENTICATED`, `UNAUTHORIZED`, `NOT_FOUND`, v.v.) và thông báo phù hợp.
3. **Given** Hệ thống gặp sự cố nội bộ không mong muốn (ví dụ mất kết nối ngoại vi chưa phân loại), **When** sự cố xảy ra, **Then** hệ thống trả về HTTP `500 Internal Server Error` với mã `INTERNAL_SERVER_ERROR` và thông báo lỗi chung an toàn, tuyệt đối không trả về stack trace Java, tên class hay thông tin nhạy cảm.

---

### User Story 2 - Khởi tạo và kết nối hạ tầng dịch vụ AWS cho các module nghiệp vụ (Priority: P1)

Nhà phát triển và các module nghiệp vụ tiếp theo (xác thực, quản lý dự án, upload video, gửi hàng đợi) cần các kết nối sẵn sàng tới các dịch vụ lưu trữ dữ liệu (DynamoDB), kho lưu trữ tập tin (S3) và hàng đợi thông điệp (SQS). Hệ thống tự động thiết lập và cung cấp các bộ điều hợp (clients) sẵn sàng hoạt động ổn định ở cả môi trường phát triển cục bộ (LocalStack) và môi trường điện toán đám mây thực tế (AWS Cloud) dựa trên cấu hình môi trường mà không cần can thiệp mã nguồn.

**Why this priority**: Toàn bộ hệ thống TransvidCloud phụ thuộc vào DynamoDB cho cơ sở dữ liệu, S3 cho lưu trữ video, và SQS cho xử lý bất đồng bộ. Đây là điều kiện tiên quyết bắt buộc để triển khai bất kỳ tính năng nghiệp vụ nào sau đó.

**Independent Test**: Kiểm thử độc lập bằng cách chạy kiểm tra tích hợp hoặc unit test khởi tạo context, xác nhận các đối tượng client AWS (DynamoDbEnhancedClient, S3Client, S3Presigner, SqsAsyncClient) được tạo thành công, kết nối đúng endpoint được cấu hình, và sẵn sàng thực thi các lệnh đọc/ghi.

**Acceptance Scenarios**:

1. **Given** Môi trường chạy cục bộ với biến cấu hình trỏ tới LocalStack (`http://localhost:4566`), **When** ứng dụng khởi động, **Then** tất cả các client DynamoDB, S3, S3Presigner và SQS kết nối thành công tới LocalStack với chế độ path-style access cho S3.
2. **Given** Môi trường chạy trên đám mây thực tế không cấu hình endpoint tùy biến, **When** ứng dụng khởi động, **Then** các client tự động sử dụng endpoint mặc định của AWS theo vùng (region) đã chỉ định.
3. **Given** Cần tạo đường dẫn tải lên tạm thời (Presigned URL) cho client, **When** bộ sinh đường dẫn được gọi, **Then** URL được tạo ra sử dụng đúng endpoint công khai được chỉ định và tuân thủ thời gian sống cấu hình (15 phút).

---

### User Story 3 - Truy cập API an toàn xuyên miền từ ứng dụng Web Frontend (Priority: P2)

Người dùng truy cập ứng dụng TransvidCloud thông qua trình duyệt web trên một tên miền khác (ví dụ `localhost:5173` hoặc domain production) có thể gọi các API của Backend một cách liền mạch và an toàn. Trình duyệt có thể thực hiện các yêu cầu tiền kiểm tra (preflight OPTIONS request) và nhận được các chỉ dẫn CORS chính xác, cho phép gửi các header xác thực và dữ liệu cần thiết.

**Why this priority**: Frontend là kênh tương tác chính của người dùng SaaS. Nếu không cấu hình CORS chính xác, trình duyệt sẽ chặn mọi yêu cầu API từ giao diện người dùng, làm tê liệt toàn bộ trải nghiệm người dùng.

**Independent Test**: Gửi các HTTP request mẫu kèm header `Origin` hợp lệ và không hợp lệ, cùng các request tiền kiểm tra `OPTIONS`, kiểm tra các header phản hồi `Access-Control-Allow-Origin`, `Access-Control-Allow-Methods`, `Access-Control-Allow-Headers`.

**Acceptance Scenarios**:

1. **Given** Trình duyệt gửi preflight request (`OPTIONS`) từ một nguồn hợp lệ (thuộc danh sách được cấp phép), **When** request đến hệ thống, **Then** hệ thống trả về HTTP `200 OK` với đầy đủ các header CORS cho phép các phương thức `GET`, `POST`, `PATCH`, `DELETE` và các header `Authorization`, `Content-Type`, `X-API-Key`.
2. **Given** Trình duyệt gửi request từ một nguồn không nằm trong danh sách cho phép, **When** request đến hệ thống, **Then** hệ thống không cấp phát header `Access-Control-Allow-Origin` cho nguồn đó, ngăn chặn hành vi truy cập trái phép từ trang web lạ.

---

### User Story 4 - Quản lý cấu hình tập trung và thẩm định an toàn khi khởi động (Priority: P2)

Quản trị viên hệ thống có thể quản lý tập trung toàn bộ cấu hình hoạt động của ứng dụng (giới hạn dung lượng tải lên, định dạng video cho phép, thời hạn chữ ký số, tên hàng đợi, tên bucket, cấu hình bảo mật) qua các biến môi trường và tệp cấu hình YAML. Ứng dụng tự động kiểm tra tính hợp lệ của các tham số khi khởi động và dừng lại ngay lập tức nếu phát hiện cấu hình quan trọng bị thiếu hoặc sai lệch (Fail-fast).

**Why this priority**: Đảm bảo hệ thống không chạy ở trạng thái thiếu cấu hình hoặc cấu hình sai, tránh các lỗi tiềm ẩn khi đang phục vụ người dùng thực tế và đơn giản hóa quá trình triển khai đa môi trường.

**Independent Test**: Chạy ứng dụng với các bộ cấu hình hợp lệ và bộ cấu hình cố tình thiếu/sai tham số (ví dụ thời gian sống URL âm, dung lượng tối đa không hợp lệ), xác nhận ứng dụng phát hiện lỗi và báo cáo nguyên nhân rõ ràng.

**Acceptance Scenarios**:

1. **Given** Môi trường cung cấp đầy đủ các biến cấu hình hợp lệ, **When** ứng dụng khởi động, **Then** tất cả các tham số được nạp vào cấu hình tập trung một cách chính xác và sẵn sàng cho các dịch vụ sử dụng.
2. **Given** Một tham số bắt buộc bị cấu hình sai (ví dụ dung lượng tải lên là số âm hoặc secret key quá ngắn), **When** ứng dụng khởi động, **Then** quá trình khởi động bị hủy với thông báo lỗi cụ thể chỉ rõ tham số vi phạm.

---

### Edge Cases

- Khi client gửi một payload JSON có cú pháp không hợp lệ (malformed JSON syntax như thiếu dấu ngoặc nhọn hoặc sai định dạng chuỗi): Hệ thống phải bắt ngoại lệ phân tích cú pháp và trả về `400 Bad Request` với mã `MALFORMED_JSON` thay vì để lộ lỗi parser của thư viện.
- Khi một tham số trên đường dẫn (path variable) hoặc truy vấn (query param) sai kiểu dữ liệu (ví dụ truyền chuỗi vào trường số nguyên): Hệ thống phải chuyển đổi thành lỗi `400 Bad Request` kèm mô tả rõ ràng về kiểu dữ liệu yêu cầu.
- Khi client gọi một phương thức HTTP không được hỗ trợ trên endpoint (ví dụ `PUT` vào endpoint chỉ hỗ trợ `POST` và `GET`): Hệ thống phải trả về `405 Method Not Allowed` với định dạng lỗi chuẩn hóa.
- Khi dịch vụ hạ tầng AWS cục bộ (LocalStack) tạm thời không phản hồi: Các thao tác gọi client phải ném ra ngoại lệ có kiểm soát và phản hồi lỗi `500` hoặc mã lỗi kết nối phù hợp, có ghi log giám sát.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống PHẢI đóng gói và quản lý tập trung toàn bộ cấu hình đặc thù của TransvidCloud thông qua một đối tượng cấu hình có kiểm tra tính hợp lệ (`TvcProperties`), bao gồm:
  - Cấu hình AWS: endpoint tùy biến, tên bucket lưu trữ đầu vào/đầu ra, endpoint ký presigned URL, thời hạn ký presigned URL, tên hàng đợi SQS, tiền tố tên bảng DynamoDB.
  - Cấu hình giới hạn tải lên: dung lượng tối đa cho phép (mặc định 200 MB), danh sách định dạng video hợp lệ (`video/mp4`, `video/quicktime`, `video/x-matroska`, `video/webm`).
  - Cấu hình bảo mật: khóa bí mật JWT, thời hạn sống của JWT, danh sách các nguồn gốc được phép truy cập CORS.
  - Cấu hình truyền thông: đường dẫn cơ sở phát media (`base-url`).
- **FR-002**: Hệ thống PHẢI tự động thẩm định các giá trị cấu hình khi khởi động và dừng ứng dụng (Fail-fast) nếu phát hiện giá trị bất hợp lệ (ví dụ: thời hạn ký URL nhỏ hơn hoặc bằng 0, khóa bí mật JWT không đủ độ dài an toàn tối thiểu 32 bytes).
- **FR-003**: Hệ thống PHẢI khởi tạo và đăng ký các bean client AWS SDK v2 dùng chung:
  - `DynamoDbClient` và `DynamoDbEnhancedClient` để tương tác với cơ sở dữ liệu NoSQL.
  - `S3Client` để kiểm tra metadata và quản lý tập tin trên kho lưu trữ S3.
  - `S3Presigner` để ký phát hành URL tải lên tạm thời.
  - `SqsAsyncClient` để gửi thông điệp điều phối công việc vào hàng đợi SQS.
- **FR-004**: Các AWS client PHẢI hỗ trợ chuyển đổi linh hoạt giữa chế độ phát triển cục bộ (LocalStack endpoint với S3 path-style access được kích hoạt) và chế độ đám mây thực tế (sử dụng AWS SDK default endpoint và Region) mà không cần thay đổi mã nguồn.
- **FR-005**: Hệ thống PHẢI cấu hình chính sách CORS toàn cục cho phép các ứng dụng Web Frontend được cấu hình truy cập các tài nguyên API, chấp nhận các phương thức HTTP `GET`, `POST`, `PATCH`, `DELETE`, `OPTIONS` và các header `Authorization`, `Content-Type`, `X-API-Key`.
- **FR-006**: Hệ thống PHẢI triển khai bộ xử lý ngoại lệ tập trung toàn cục (`GlobalExceptionHandler`), bắt toàn bộ các ngoại lệ phát sinh trong quá trình tiếp nhận và xử lý HTTP request.
- **FR-007**: MỌI phản hồi lỗi của hệ thống (mã trạng thái HTTP `4xx` và `5xx`) PHẢI tuân thủ tuyệt đối cấu trúc hợp đồng `ErrorResponse` đã quy định trong `contracts/openapi.yaml`, gồm 4 trường dữ liệu:
  - `code` (string): Mã định danh lỗi máy đọc được.
  - `message` (string): Thông điệp mô tả lỗi.
  - `timestamp` (string): Thời điểm phát sinh lỗi theo chuẩn ISO-8601 UTC.
  - `details` (object/map, tùy chọn): Thông tin chi tiết bổ sung (ví dụ lỗi xác thực dữ liệu từng trường).
- **FR-008**: Hệ thống PHẢI định nghĩa bảng mã lỗi (`ErrorCode`) làm nguồn sự thật nội bộ, quy định mã chuỗi định danh, thông điệp mặc định và mã trạng thái HTTP tương ứng, bao gồm các mã lỗi chuẩn:
  - `VALIDATION_ERROR` (HTTP 400)
  - `MALFORMED_JSON` (HTTP 400)
  - `TYPE_MISMATCH` (HTTP 400)
  - `UNAUTHENTICATED` (HTTP 401)
  - `UNAUTHORIZED` (HTTP 403)
  - `NOT_FOUND` (HTTP 404)
  - `METHOD_NOT_ALLOWED` (HTTP 405)
  - `CONFLICT` (HTTP 409)
  - `UPLOAD_LIMIT_EXCEEDED` (HTTP 413)
  - `UNSUPPORTED_MEDIA_TYPE` (HTTP 422)
  - `INTERNAL_SERVER_ERROR` (HTTP 500)
- **FR-009**: Khi xảy ra lỗi xác thực dữ liệu đầu vào (`MethodArgumentNotValidException` hoặc vi phạm ràng buộc dữ liệu), hệ thống PHẢI thu thập tất cả các trường vi phạm và đưa vào thuộc tính `details` dưới dạng danh sách hoặc cặp `{ tên_trường: thông_báo_lỗi }`.
- **FR-010**: Hệ thống PHẢI có ngoại lệ nghiệp vụ riêng (`AppException`) nhận tham số `ErrorCode` hoặc mã lỗi tùy chỉnh để các dịch vụ nghiệp vụ ném ra khi vi phạm quy tắc mà không cần tự xử lý HTTP status.
- **FR-011**: Hệ thống TUYỆT ĐỐI KHÔNG được xuất stack trace, thông tin cơ sở hạ tầng nội bộ hoặc các chuỗi định danh nhạy cảm ra ngoài body của phản hồi lỗi cho client. Mọi chi tiết kỹ thuật chỉ được phép ghi vào log máy chủ.
- **FR-012**: Hệ thống PHẢI cung cấp bộ kiểm thử đơn vị và kiểm thử lát cắt (Web Slice Test) xác minh toàn diện hành vi của bộ xử lý lỗi, bộ thẩm định cấu hình và các bean AWS client.

---

### Key Entities *(include if feature involves data)*

- **ErrorResponse**: Đối tượng đại diện cho dữ liệu phản hồi lỗi trả về cho client theo hợp đồng `openapi.yaml`. Bao gồm các thuộc tính:
  - `code`: Chuỗi ký tự định danh mã lỗi máy đọc được (ví dụ `VALIDATION_ERROR`).
  - `message`: Chuỗi văn bản mô tả lỗi cho người dùng.
  - `timestamp`: Thời điểm xảy ra lỗi (Instant / ISO-8601 date-time).
  - `details`: Đối tượng chứa thông tin bổ sung về lỗi (danh sách lỗi từng trường, chi tiết vi phạm).
- **ErrorCode**: Bảng liệt kê các mã lỗi chuẩn hóa của hệ thống, mỗi mã gắn liền với một mã định danh máy đọc được (`code`), thông điệp mô tả mặc định (`message`), và mã trạng thái HTTP tương ứng (`HttpStatus`).
- **TvcProperties**: Đối tượng chứa toàn bộ các thuộc tính cấu hình hệ thống của TransvidCloud, được ánh xạ từ tiền tố cấu hình `tvc` trong tệp cấu hình ứng dụng, bao gồm các nhóm cấu hình con: `aws`, `upload`, `security`, `media`.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% các phản hồi lỗi từ hệ thống (mọi mã trạng thái HTTP 4xx và 5xx) được trả về dưới đúng cấu trúc JSON `{ code, message, timestamp, details }` theo đúng hợp đồng OpenAPI.
- **SC-002**: 100% các trường hợp dữ liệu đầu vào không hợp lệ được phát hiện và chỉ rõ tên trường cùng lý do vi phạm trong trường `details` của phản hồi lỗi.
- **SC-003**: Thời gian xử lý và trả về phản hồi lỗi của hệ thống diễn ra tức thì, không làm tăng độ trễ của yêu cầu so với xử lý mặc định.
- **SC-004**: Toàn bộ các kiểm thử đơn vị và kiểm thử cấu hình nền tảng (validation, exception handling, client initialization, CORS) đạt kết quả kiểm tra thành công 100% (`PASS`).

---

## Assumptions

- Môi trường thực thi sử dụng Java 21 LTS và Spring Boot 3.4.x.
- Hạ tầng lưu trữ và hàng đợi cục bộ được cung cấp bởi LocalStack thông qua Docker Compose ở cổng `4566`.
- Cấu trúc phản hồi lỗi tuân thủ nghiêm ngặt theo hợp đồng `contracts/openapi.yaml` (SSOT), trong đó `code` là chuỗi định danh máy đọc được (string code), ưu tiên tuyệt đối so với các mã số nguyên cũ.
- Các yêu cầu CORS được áp dụng cho toàn bộ các endpoint bắt đầu bằng prefix `/api/**`.
- Việc quản lý xác thực chi tiết (JWT Filter, API Key Filter) sẽ được hoàn thiện trong các tính năng chuyên biệt `F-003` và `F-005` trên nền tảng xử lý lỗi và cấu hình của tính năng này.
