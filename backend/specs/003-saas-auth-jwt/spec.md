# Feature Specification: Xác thực người dùng SaaS — Register, Login & JWT Security

**Feature Branch**: `phuong-backend`

**Created**: 2026-10-31

**Status**: Draft

**Input**: User description: "feature F-003: Xác thực người dùng SaaS — Register, Login & JWT Security (Epic A2)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Đăng ký tài khoản người dùng SaaS mới (Priority: P1)

Người dùng mới truy cập nền tảng và muốn tạo một tài khoản SaaS để bắt đầu quản lý các dự án video của mình. Người dùng cung cấp địa chỉ email, mật khẩu bảo mật và họ tên. Hệ thống kiểm tra dữ liệu, bảo đảm email chưa từng được sử dụng trước đó, tạo tài khoản và tự động cấp phiên đăng nhập hợp lệ để người dùng có thể sử dụng ngay lập tức mà không phải thực hiện đăng nhập lại.

**Why this priority**: Đây là bước tiếp nhận người dùng đầu tiên (onboarding) của nền tảng SaaS. Không có tài khoản người dùng, toàn bộ các quy trình tạo dự án, tải video và phân quyền phía sau đều không thể hoạt động.

**Independent Test**: Có thể kiểm thử độc lập hoàn toàn bằng cách gửi thông tin đăng ký mới hợp lệ, xác minh hệ thống tạo tài khoản mới thành công, trả về phiên đăng nhập hợp lệ và không thể đăng ký lại cùng email đó lần thứ hai.

**Acceptance Scenarios**:

1. **Given** Người dùng cung cấp email hợp lệ chưa tồn tại trong hệ thống, mật khẩu đủ điều kiện bảo mật (ít nhất 8 ký tự) và họ tên, **When** Người dùng gửi yêu cầu đăng ký, **Then** Hệ thống tạo tài khoản người dùng mới thành công, phản hồi mã thành công và cấp phiên xác thực (Access Token) kèm thông tin cơ bản của người dùng.
2. **Given** Người dùng cung cấp địa chỉ email đã tồn tại trong hệ thống (kể cả khác biệt về chữ hoa/chữ thường hoặc khoảng trắng đầu cuối), **When** Người dùng gửi yêu cầu đăng ký, **Then** Hệ thống từ chối đăng ký, báo lỗi trùng lặp tài khoản và không tạo thêm bất kỳ dữ liệu nào.
3. **Given** Người dùng cung cấp mật khẩu không đủ độ dài tối thiểu (dưới 8 ký tự) hoặc định dạng email không đúng chuẩn, **When** Người dùng gửi yêu cầu đăng ký, **Then** Hệ thống từ chối với thông báo lỗi xác thực dữ liệu đầu vào không hợp lệ.

---

### User Story 2 - Đăng nhập tài khoản SaaS và nhận Access Token (Priority: P1)

Người dùng đã đăng ký quay lại nền tảng và muốn đăng nhập vào tài khoản của mình bằng email và mật khẩu đã tạo. Khi thông tin đăng nhập chính xác, hệ thống phản hồi phiên làm việc hợp lệ (Access Token) cùng thông tin tóm tắt của người dùng để ứng dụng Web có thể hiển thị danh tính và gửi kèm phiên làm việc trong các yêu cầu kế tiếp.

**Why this priority**: Cùng với việc đăng ký, đây là luồng nghiệp vụ thiết yếu cho phép người dùng hiện tại tiếp tục truy cập và sử dụng dịch vụ trong các phiên làm việc sau.

**Independent Test**: Có thể kiểm thử độc lập bằng cách sử dụng tài khoản đã đăng ký, gửi thông tin đăng nhập đúng và xác minh nhận được Access Token hợp lệ; gửi thông tin sai và xác minh bị từ chối truy cập.

**Acceptance Scenarios**:

1. **Given** Tài khoản người dùng đã tồn tại trong hệ thống, **When** Người dùng cung cấp đúng email và mật khẩu, **Then** Hệ thống xác thực thành công, trả về Access Token hợp lệ, loại token, thời gian hiệu lực và thông tin người dùng.
2. **Given** Email không tồn tại trong hệ thống hoặc mật khẩu cung cấp không khớp với mật khẩu đã lưu, **When** Người dùng gửi yêu cầu đăng nhập, **Then** Hệ thống từ chối xác thực với thông báo không hợp lệ và không cấp Access Token.
3. **Given** Người dùng gửi thiếu email hoặc mật khẩu, **When** Người dùng gửi yêu cầu đăng nhập, **Then** Hệ thống từ chối với thông báo lỗi dữ liệu đầu vào thiếu thông tin bắt buộc.

---

### User Story 3 - Truy cập tài nguyên bảo vệ bằng Bearer Token và từ chối truy cập trái phép (Priority: P2)

Người dùng đã đăng nhập sử dụng Access Token (dưới định dạng Bearer) để truy cập các tính năng hoặc tài nguyên được bảo vệ trong hệ thống. Hệ thống kiểm tra tính hợp lệ và thời hạn của token, trích xuất danh tính người dùng để cho phép thực hiện thao tác nghiệp vụ, đồng thời chặn đứng các yêu cầu không mang token hoặc mang token không hợp lệ.

**Why this priority**: Đảm bảo an ninh và cô lập tài nguyên cho toàn bộ các chức năng nghiệp vụ tiếp theo của hệ thống SaaS (như quản lý Project, Video).

**Independent Test**: Có thể kiểm thử độc lập bằng cách thiết lập một điểm cuối kiểm tra yêu cầu xác thực, gửi request kèm token hợp lệ để xác nhận được phép truy cập, và gửi request không có token hoặc token giả mạo để xác nhận bị từ chối.

**Acceptance Scenarios**:

1. **Given** Một Access Token hợp lệ còn thời hạn được đính kèm trong tiêu đề yêu cầu, **When** Người dùng truy cập điểm cuối được bảo vệ, **Then** Hệ thống chấp nhận yêu cầu, xác định đúng định danh người dùng và cho phép xử lý nghiệp vụ tiếp theo.
2. **Given** Một yêu cầu truy cập không đính kèm token hoặc đính kèm token bị sửa đổi, chữ ký sai, **When** Yêu cầu được gửi đến điểm cuối được bảo vệ, **Then** Hệ thống từ chối truy cập ngay tại ranh giới bảo mật và thông báo lỗi chưa được xác thực.
3. **Given** Một Access Token đã hết thời hạn hiệu lực, **When** Yêu cầu được gửi đến điểm cuối được bảo vệ, **Then** Hệ thống từ chối truy cập và thông báo token đã hết hạn.

---

### Edge Cases

- **Xử lý đăng ký đồng thời cùng một email (Concurrent Registration)**: Khi có hai hoặc nhiều yêu cầu đăng ký cùng một địa chỉ email diễn ra đồng thời, hệ thống phải đảm bảo tính nguyên tử: chỉ duy nhất một yêu cầu thành công, các yêu cầu còn lại bị từ chối do xung đột dữ liệu.
- **Chuẩn hóa định dạng email**: Email người dùng nhập có thể chứa ký tự viết hoa hoặc khoảng trắng đầu/cuối (ví dụ: ` User@Example.COM `). Hệ thống phải tự động chuẩn hóa về dạng chữ thường và cắt khoảng trắng trước khi kiểm tra trùng lặp và lưu trữ.
- **Mật khẩu chứa ký tự đặc biệt hoặc độ dài lớn**: Hệ thống phải xử lý an toàn các mật khẩu chứa ký tự đặc biệt, dấu câu, khoảng trắng hoặc độ dài tối đa lên đến 100 ký tự mà không gây lỗi hoặc treo hệ thống.
- **Bảo mật phản hồi đăng nhập sai**: Để phòng chống tấn công dò quét tài khoản (User Enumeration Attack), phản hồi từ chối đăng nhập khi sai mật khẩu hoặc khi email không tồn tại phải đưa ra thông báo chung thống nhất, không chỉ rõ email có tồn tại hay không.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống PHẢI cung cấp khả năng đăng ký tài khoản người dùng mới với các thông tin bắt buộc gồm địa chỉ email hợp lệ và mật khẩu bảo mật, cùng thông tin họ tên tùy chọn.
- **FR-002**: Hệ thống PHẢI kiểm tra và bảo đảm tính duy nhất tuyệt đối của địa chỉ email trên toàn bộ nền tảng, không cho phép hai tài khoản có cùng email.
- **FR-003**: Hệ thống PHẢI tự động chuẩn hóa địa chỉ email (chuyển sang chữ thường và loại bỏ khoảng trắng thừa đầu cuối) trước khi kiểm tra tính duy nhất hoặc lưu trữ.
- **FR-004**: Hệ thống PHẢI xác thực độ phức tạp của dữ liệu đầu vào: địa chỉ email phải đúng định dạng email tiêu chuẩn; mật khẩu phải có độ dài từ 8 đến 100 ký tự; họ tên không vượt quá 100 ký tự.
- **FR-005**: Hệ thống PHẢI lưu trữ mật khẩu người dùng dưới dạng băm một chiều an toàn kết hợp với muối ngẫu nhiên (salted hash); tuyệt đối KHÔNG lưu trữ hoặc làm lộ mật khẩu ở dạng văn bản gốc trong kho dữ liệu, nhật ký hoạt động hay thông điệp phản hồi.
- **FR-006**: Khi đăng ký thành công, hệ thống PHẢI tự động tạo định danh người dùng duy nhất, lưu trữ bản ghi người dùng, và trả về phiên xác thực (Access Token) kèm thông tin cơ bản của người dùng để người dùng có thể sử dụng hệ thống ngay lập tức.
- **FR-007**: Hệ thống PHẢI cung cấp khả năng đăng nhập cho người dùng đã đăng ký thông qua địa chỉ email và mật khẩu.
- **FR-008**: Hệ thống PHẢI từ chối yêu cầu đăng nhập nếu email không tồn tại hoặc mật khẩu không khớp; thông báo từ chối phải thống nhất để bảo vệ quyền riêng tư và ngăn ngừa dò quét tài khoản.
- **FR-009**: Khi đăng nhập thành công, hệ thống PHẢI phát hành Access Token có chữ ký số bảo mật, định dạng Bearer, có thời hạn hiệu lực rõ ràng, cùng thông tin hồ sơ tóm tắt của người dùng.
- **FR-010**: Hệ thống PHẢI cung cấp cơ chế chặn và xác thực Access Token trên các yêu cầu truy cập tài nguyên bảo vệ; cho phép các yêu cầu có token hợp lệ đi qua và trích xuất danh tính người dùng cho tầng nghiệp vụ.
- **FR-011**: Hệ thống PHẢI từ chối truy cập đối với các yêu cầu thiếu token xác thực, token không đúng định dạng Bearer, token có chữ ký không hợp lệ hoặc token đã hết thời hạn sử dụng.
- **FR-012**: Toàn bộ phản hồi thành công và thông báo lỗi PHẢI tuân thủ định dạng dữ liệu chuẩn hóa của nền tảng, không để lộ dấu vết ngăn xếp (stack trace) hoặc chi tiết hạ tầng nội bộ ra bên ngoài.

### Key Entities *(include if feature involves data)*

- **User Account (Tài khoản người dùng)**: Đại diện cho danh tính người dùng sở hữu tài nguyên trên nền tảng SaaS. Bao gồm định danh duy nhất (`userId`), địa chỉ email đã chuẩn hóa (`email`), mã băm mật khẩu bảo mật (`passwordHash`), họ tên hiển thị (`fullName`), và thời điểm khởi tạo tài khoản (`createdAt`).
- **Unique Email Constraint Record (Bản ghi kiểm soát tính duy nhất của email)**: Thực thể kỹ thuật phục vụ việc khóa và bảo đảm tính duy nhất của email trong kho dữ liệu phân tán khi xử lý các thao tác ghi đồng thời.
- **Authentication Token (Mã phiên xác thực)**: Đại diện cho phiên làm việc có chữ ký số bảo mật của người dùng, mang các thông tin định danh (`userId`, `email`), thời điểm phát hành (`issuedAt`) và thời điểm hết hạn (`expiresAt`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Người dùng hoàn thành quá trình đăng ký tài khoản mới và nhận token trong thời gian phản hồi dưới 2 giây.
- **SC-002**: Người dùng hoàn thành quá trình đăng nhập và nhận token trong thời gian phản hồi dưới 1 giây.
- **SC-003**: 100% các yêu cầu đăng ký sử dụng địa chỉ email đã tồn tại đều bị từ chối chính xác mà không gây ra dữ liệu rác hay xung đột hệ thống.
- **SC-004**: 100% các yêu cầu đăng ký đồng thời cùng một email trong môi trường phân tán chỉ có duy nhất 1 yêu cầu thành công, các yêu cầu còn lại bị từ chối an toàn.
- **SC-005**: 100% mật khẩu được lưu trữ an toàn dưới dạng băm một chiều, không có bất kỳ mật khẩu dạng văn bản gốc nào xuất hiện trong kho lưu trữ hoặc nhật ký hệ thống.
- **SC-006**: 100% các yêu cầu truy cập điểm cuối bảo vệ thiếu token hoặc mang token không hợp lệ (sai chữ ký, hết hạn) bị từ chối ngay tại tầng bảo mật.

## Assumptions

- Phiên bản hiện tại (v1) áp dụng mô hình xác thực phi trạng thái (Stateless Bearer Token) với thời hạn hiệu lực mặc định là 24 giờ (86.400 giây); cơ chế Refresh Token và danh sách đen thu hồi token (Token Revocation/Blacklist) nằm ngoài phạm vi của v1 và có thể được xem xét bổ sung ở các giai đoạn sau nếu có nhu cầu.
- Tính năng tự phục hồi mật khẩu qua email (Password Reset) và gửi email kích hoạt tài khoản nằm ngoài phạm vi của Feature F-003.
- Mọi địa chỉ email được tự động chuẩn hóa về chữ thường (lowercase) và loại bỏ khoảng trắng hai đầu (trim) trước khi xử lý nghiệp vụ.
- Độ dài tối thiểu của mật khẩu là 8 ký tự, tối đa 100 ký tự.
