# SaaS Auth & JWT Security Checklist: Xác thực người dùng SaaS (Feature F-003)

**Purpose**: Thẩm định chất lượng, tính đầy đủ, tính rõ ràng và khả năng kiểm chứng của các yêu cầu nghiệp vụ và kỹ thuật cho tính năng Xác thực người dùng SaaS (Register, Login & Bearer JWT) trước khi lập trình và mở PR review.  
**Created**: 2026-10-31  
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [data-model.md](../data-model.md)  

**Review Ownership**: Checklist này là tài liệu thẩm định chất lượng yêu cầu thuộc quyền sở hữu của Reviewer (PR Reviewer & Release Gate). Chỉ đánh dấu `[x]` khi Reviewer xác nhận tiêu chí chất lượng yêu cầu tương ứng đã được thỏa mãn trong tài liệu đặc tả/thiết kế.  
**Marker Semantics**: `[x]` mang ý nghĩa tiêu chí yêu cầu đã được thẩm định đạt chuẩn chất lượng (Unit Test for Requirements PASS). `[x]` KHÔNG mang ý nghĩa công việc lập trình code đã hoàn tất.  

---

## 1. Requirement Completeness (Tính đầy đủ của yêu cầu)

- [ ] CHK001 - Yêu cầu về các trường dữ liệu bắt buộc và tùy chọn trong luồng đăng ký tài khoản có được định nghĩa đầy đủ và rõ ràng không? [Completeness, Spec §FR-001, Spec §FR-004]
- [ ] CHK002 - Quy định về hành vi tự động cấp phát Access Token ngay sau khi đăng ký thành công có được quy định tường minh để người dùng không phải đăng nhập lại không? [Completeness, Spec §FR-006]
- [ ] CHK003 - Cấu trúc dữ liệu phản hồi phiên xác thực (`AuthResponse`) có được mô tả đầy đủ các trường `accessToken`, `tokenType`, `expiresInSeconds` và đối tượng `user` không? [Completeness, Spec §FR-009, Contracts §auth-api.yaml]
- [ ] CHK004 - Yêu cầu về thuật toán và phương thức băm mật khẩu có được chỉ định rõ ràng là thuật toán một chiều kết hợp muối (salted one-way hash) không? [Completeness, Spec §FR-005, Plan §Summary]
- [ ] CHK005 - Các trường thông tin nhận diện người dùng (claims) trong payload của JWT token có được liệt kê đầy đủ (`sub`, `email`, `iat`, `exp`) không? [Completeness, Plan §Research §Nghiên cứu 2]

---

## 2. Requirement Clarity & Precision (Tính rõ ràng và chính xác)

- [ ] CHK006 - Giới hạn độ dài mật khẩu có được lượng hóa chính xác với ngưỡng tối thiểu (8 ký tự) và tối đa (100 ký tự) không? [Clarity, Spec §FR-004]
- [ ] CHK007 - Quy tắc chuẩn hóa địa chỉ email trước khi kiểm tra trùng lặp có được định nghĩa chính xác với các thao tác chuyển chữ thường (`lowercase`) và cắt khoảng trắng (`trim`) không? [Clarity, Spec §FR-003, Plan §Research §Nghiên cứu 3]
- [ ] CHK008 - Tiêu chuẩn về cấu trúc định danh người dùng (`userId`) có được quy định rõ ràng về tiền tố `usr_` và định dạng chuỗi ngẫu nhiên không? [Clarity, Spec §Key Entities, DataModel §1.1]
- [ ] CHK009 - Thời hạn hiệu lực mặc định của Access Token có được lượng hóa bằng số giây cụ thể (86.400 giây / 24 giờ) thay vì dùng từ ngữ mơ hồ ("thời gian hợp lý") không? [Clarity, Spec §Assumptions, Plan §Technical Context]
- [ ] CHK010 - Độ dài tối thiểu của khóa bí mật ký token JWT (`tvc.security.jwt.secret`) có được quy định rõ ràng ít nhất 32 bytes cho thuật toán HMAC-SHA256 không? [Clarity, Plan §Technical Context, Config §TvcProperties]

---

## 3. Requirement Consistency & Alignment (Tính nhất quán và đồng bộ)

- [ ] CHK011 - Các mã trạng thái HTTP trong đặc tả (`201` cho đăng ký mới, `200` cho đăng nhập, `400` cho validation, `401` cho sai thông tin, `409` cho trùng email) có hoàn toàn đồng nhất với master contract `contracts/openapi.yaml` không? [Consistency, Spec §User Scenarios, Contracts §auth-api.yaml]
- [ ] CHK012 - Định dạng thông báo lỗi trong các kịch bản thất bại có tuân thủ cấu trúc chuẩn hóa `ErrorResponse` (`code`, `message`, `timestamp`) mà không để lộ chi tiết nội bộ không? [Consistency, Spec §FR-012, Common §ErrorResponse]
- [ ] CHK013 - Tên bảng DynamoDB (`tvc-{env}-users`) và tên chỉ mục phụ (`email-index`) trong thiết kế có khớp hoàn toàn với quy định trong `contracts/dynamodb-tables.md` không? [Consistency, DataModel §1, Contracts §dynamodb-tables.md]
- [ ] CHK014 - Ranh giới bảo mật stateless (phi trạng thái) có được áp dụng đồng bộ giữa cấu hình Spring Security (`SessionCreationPolicy.STATELESS`) và tài liệu kiến trúc không? [Consistency, Plan §Constitution Check §Gate 2]

---

## 4. Security & Threat Mitigation (An toàn thông tin & Phòng thủ tấn công)

- [ ] CHK015 - Thông điệp từ chối đăng nhập khi sai email hoặc sai mật khẩu có được quy định thống nhất để ngăn chặn kỹ thuật dò quét tài khoản (User Enumeration Attack) không? [Security, Spec §Edge Cases, Spec §FR-008]
- [ ] CHK016 - Yêu cầu về việc tuyệt đối không ghi log bản rõ mật khẩu, khóa bí mật JWT hoặc token trong luồng xử lý có được ghi nhận rõ ràng không? [Security, Spec §FR-005, Plan §Technical Context]
- [ ] CHK017 - Có yêu cầu bảo vệ rõ ràng rằng bản ghi `passwordHash` tuyệt đối không bao giờ được tuần tự hóa (serialize) hoặc phản hồi ra ngoài qua API DTO không? [Security, DataModel §5]
- [ ] CHK018 - Cơ chế từ chối các yêu cầu truy cập tài nguyên bảo vệ khi token bị sửa đổi, sai chữ ký số hoặc hết hạn có được đặc tả chính xác không? [Security, Spec §FR-011, Spec §User Story 3]

---

## 5. Data Integrity & Concurrency (Toàn vẹn dữ liệu & Xử lý đồng thời)

- [ ] CHK019 - Cơ chế bảo đảm tính duy nhất của email trong môi trường phân tán có được xác định rõ ràng thông qua giao dịch nguyên tử `TransactWriteItems` với bản ghi khóa `EMAIL#<normalized-email>` không? [Concurrency, Spec §Edge Cases, DataModel §2]
- [ ] CHK020 - Điều kiện kiểm tra `attribute_not_exists(userId)` trên bản ghi khóa email có được quy định rõ ràng trong thao tác ghi giao dịch không? [Data Integrity, DataModel §2, Plan §Research §Nghiên cứu 1]
- [ ] CHK021 - Kịch bản lỗi khi giao dịch `TransactWriteItems` bị hủy (`TransactionCanceledException`) do trùng email có được ánh xạ cụ thể thành mã lỗi `EMAIL_ALREADY_EXISTS` và HTTP `409 Conflict` không? [Error Handling, DataModel §2, Quickstart §3.2]

---

## 6. Scenario & Edge Case Coverage (Độ bao phủ kịch bản & Trường hợp biên)

- [ ] CHK022 - Có yêu cầu quy định rõ ràng về hành vi khi người dùng nhập email có chứa ký tự viết hoa hoặc khoảng trắng đầu cuối (` User@Example.Com `) không? [Edge Case, Spec §Edge Cases, Spec §FR-003]
- [ ] CHK023 - Yêu cầu xử lý đối với mật khẩu chứa ký tự đặc biệt, dấu cách hoặc đạt độ dài biên (8 ký tự và 100 ký tự) có được nêu rõ không? [Edge Case, Spec §Edge Cases, Spec §FR-004]
- [ ] CHK024 - Kịch bản gửi thiếu trường dữ liệu bắt buộc (thiếu email hoặc thiếu password) có được mô tả kết quả trả về `400 Bad Request` kèm thông tin lỗi xác thực không? [Coverage, Spec §User Story 1, Spec §User Story 2]
- [ ] CHK025 - Yêu cầu về phạm vi tính năng (Out-of-scope) đối với Refresh Token, Quên mật khẩu qua email và Token Blacklist có được ghi nhận rõ ràng để tránh scope creep không? [Scope Boundary, Spec §Assumptions]

---

## 7. Acceptance Criteria & Measurability (Tiêu chuẩn nghiệm thu & Khả năng đo lường)

- [ ] CHK026 - Thời gian phản hồi của yêu cầu đăng ký tài khoản có được định lượng cụ thể bằng ngưỡng đo lường khách quan (< 2 giây) không? [Measurability, Spec §SC-001]
- [ ] CHK027 - Thời gian phản hồi của yêu cầu đăng nhập có được định lượng cụ thể bằng ngưỡng đo lường khách quan (< 1 giây) không? [Measurability, Spec §SC-002]
- [ ] CHK028 - Tiêu chí kiểm chứng 100% các request đăng ký trùng email đều bị chặn an toàn và không để lại dữ liệu rác có thể kiểm thử tự động được không? [Measurability, Spec §SC-003, Quickstart §2.2]
- [ ] CHK029 - Kịch bản kiểm chứng tích hợp thực tế với DynamoDB (Real Verification với Testcontainers LocalStack `UserAuthIT`) có được chỉ định bắt buộc thay vì chỉ kiểm thử Mock cục bộ không? [Verification Standard, Plan §Constitution Check §Gate 5, Quickstart §2.2]
- [ ] CHK030 - Kịch bản kiểm thử bảo toàn không suy giảm chất lượng quá khứ (Zero Regression qua `./scripts/verify.sh`) có được định nghĩa như một điều kiện bắt buộc trước khi nghiệm thu không? [Regression Guard, Plan §Summary, Quickstart §2.3]

---

## Notes

* Đánh dấu `[x]` chỉ sau khi Reviewer đã đối chiếu và xác nhận tiêu chí chất lượng yêu cầu đạt chuẩn trong các tài liệu đặc tả/thiết kế liên quan.
* Để trống `[ ]` đối với các mục cần bổ sung, chỉnh sửa hoặc đang chờ thảo luận thêm.
* Lệnh `/speckit-implement` đọc trạng thái checklist này như một quality gate và tuyệt đối không tự ý chỉnh sửa đánh dấu của Reviewer.
* Toàn bộ 30 mục kiểm tra được đánh số tuần tự từ `CHK001` đến `CHK030` với ít nhất một mã tham chiếu truy vết (`[Traceability Reference]`).
