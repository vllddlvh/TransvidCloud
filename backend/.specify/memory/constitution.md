<!--
Sync Impact Report:
- Version change: Template Placeholder → 1.0.0
- Rationale: Khởi tạo và phê chuẩn bản Hiến pháp dự án (Project Constitution) v1.0.0 chính thức cho TransVidCloud, thiết lập 9 nhóm nguyên tắc cốt lõi, tiêu chuẩn kỹ thuật, quy trình phát triển và cơ chế quản trị.
- Modified principles: Không có (khởi tạo từ template mẫu ban đầu).
- Added sections:
  - Core Principles:
    - I. Code Quality & Maintainability
    - II. Architecture & Modularity
    - III. Security & Privacy
    - IV. Correctness & Data Integrity
    - V. Testing & Verification
    - VI. Error Handling & Observability
    - VII. Documentation & Technical Decisions
    - VIII. Dependency & Engineering Discipline
    - IX. Human Oversight & Governance
  - Tiêu chuẩn Kỹ thuật & Ràng buộc Hệ thống
  - Quy trình Phát triển & Quality Gates
  - Governance
- Removed sections: Không có (thay thế toàn bộ các placeholder mẫu).
- Follow-up TODOs: Cần con người (Project Owner / Technical Lead) xem xét và phê chuẩn chính thức trước khi áp dụng như chính sách bắt buộc đối với toàn bộ thành viên và AI Agent.
-->

# TransVidCloud Constitution

## Core Principles

### I. Code Quality & Maintainability
- Code MUST rõ ràng, dễ đọc, dễ hiểu và dễ bảo trì; ưu tiên tính tự tài liệu hóa thông qua cấu trúc mạch lạc và đặt tên tường minh theo chuẩn ngôn ngữ.
- Giải pháp thiết kế MUST ưu tiên tính đơn giản, tuân thủ nguyên lý YAGNI (You Aren't Gonna Need It) và KISS (Keep It Simple, Stupid); MUST NOT áp dụng over-engineering, trừu tượng hóa quá mức hoặc bổ sung các tầng abstraction không cần thiết khi chưa có yêu cầu nghiệp vụ thực tế.
- Mã nguồn MUST tuân thủ nghiêm ngặt coding conventions và engineering standards hiện hành của dự án (quy định tại `docs/engineering-standards.md`).
- Thiết kế và mã nguồn MUST hạn chế tối đa việc trùng lặp code (DRY), liên kết chặt chẽ (tight coupling) và độ phức tạp không cần thiết (unnecessary complexity).
- Quá trình refactoring mã nguồn MUST bảo toàn tuyệt đối behavior hiện tại của hệ thống, trừ khi có yêu cầu thay đổi nghiệp vụ rõ ràng được con người phê duyệt.
- Rationale: Giữ cho codebase luôn trong sạch, giảm tải nhận thức (cognitive load) cho kỹ sư và AI Agent, ngăn ngừa tích lũy nợ kỹ thuật và bảo đảm hệ thống có thể mở rộng lâu dài.

### II. Architecture & Modularity
- Mọi thay đổi mã nguồn MUST tôn trọng kiến trúc tổng thể, ranh giới module (module boundaries) và phân định trách nhiệm rõ ràng của từng thành phần trong hệ thống.
- Thiết kế MUST duy trì nguyên lý phân tách các mối quan tâm (Separation of Concerns - SoC) và chiều phụ thuộc (dependency direction) hợp lý giữa các tầng kiến trúc.
- Tuyệt đối MUST NOT tạo ra phụ thuộc vòng (circular dependencies) hoặc ghép nối chặt không cần thiết giữa các module, package hoặc domain service.
- Kỹ sư và AI Agent MUST ưu tiên tái sử dụng các abstraction, tiện ích dùng chung và hạ tầng hiện có trước khi đề xuất tạo mới.
- Mọi thay đổi kiến trúc quan trọng, sửa đổi ranh giới module hoặc phá vỡ cấu trúc hiện tại MUST có căn cứ kỹ thuật rõ ràng và được review, phê duyệt trước khi thực hiện.
- Rationale: Bảo đảm tính module hóa cao, ngăn chặn nguy cơ suy thoái kiến trúc ("spaghetti architecture"), cho phép các module phát triển và kiểm thử độc lập mà không gây rủi ro dây chuyền.

### III. Security & Privacy
- Bảo mật (Security) MUST được xem xét và tích hợp xuyên suốt vòng đời phát triển phần mềm (Secure by Design), không được coi là một bước phụ xử lý sau cùng.
- Hệ thống MUST áp dụng nguyên tắc đặc quyền tối thiểu (Least Privilege) và phòng thủ theo chiều sâu (Defense in Depth) trên mọi thành phần và tầng xử lý.
- Mọi thao tác truy cập dữ liệu, API hoặc tài nguyên hệ thống MUST được xác thực (Authentication) và phân quyền (Authorization) chặt chẽ, bảo đảm cô lập quyền sở hữu tài nguyên (Resource Ownership Isolation).
- Toàn bộ bí mật hệ thống (credentials, access keys, tokens, secrets, private keys), dữ liệu nhạy cảm và thông tin định danh cá nhân (PII) MUST được bảo vệ an toàn; MUST NOT commit lên kho mã nguồn, MUST NOT lưu trữ dạng bản rõ (plaintext) và MUST NOT ghi vào log streams.
- Toàn bộ dữ liệu đầu vào (Input) từ bên ngoài boundary MUST được validate nghiêm ngặt và xử lý các rủi ro bảo mật phù hợp với đặc thù của hệ thống.
- Tuyệt đối MUST NOT vô hiệu hóa, bypass hoặc nới lỏng các cơ chế bảo mật (như tắt bộ lọc xác thực, tắt kiểm tra quyền sở hữu, hoặc nới lỏng CORS sai quy định) nhằm mục đích làm cho việc kiểm thử hoặc triển khai dễ dàng hơn.
- Rationale: Bảo vệ dữ liệu người dùng, bảo vệ uy tín dịch vụ SaaS và tính toàn vẹn của nền tảng, loại trừ triệt để nguy cơ rò rỉ dữ liệu hoặc xâm nhập trái phép.

### IV. Correctness & Data Integrity
- Hệ thống MUST bảo đảm tính chính xác, tính nhất quán và toàn vẹn của dữ liệu trong mọi điều kiện vận hành.
- Các thao tác nghiệp vụ có rủi ro tranh chấp MUST xử lý thấu đáo transaction, kiểm soát đồng thời (concurrency control), tính bất biến/lặp lại (idempotency) và các kịch bản thất bại (failure scenarios).
- Hợp đồng giao tiếp (API contracts) và hợp đồng dữ liệu (data contracts, event schemas) MUST được xem là Nguồn Sự Thật Duy Nhất (Single Source of Truth) và được quản lý tập trung, minh bạch.
- Mọi thay đổi đối với public API, schema lưu trữ dữ liệu hoặc message schema MUST xem xét kỹ lưỡng tính tương thích ngược (backward compatibility) và tác động di chuyển dữ liệu (migration impact).
- Tuyệt đối MUST NOT tự ý thay đổi business behavior hoặc hợp đồng giao tiếp ngoài phạm vi đã được phê duyệt.
- Rationale: Ngăn chặn lỗi sai lệch dữ liệu, xung đột trạng thái giữa các service và bảo đảm các client cũng như các thành phần tích hợp hoạt động liền mạch, tin cậy.

### V. Testing & Verification
- Mọi thay đổi có chứa logic xử lý hoặc ảnh hưởng đến behavior của hệ thống MUST có kiểm chứng (verification) bằng kiểm thử phù hợp.
- Chiến lược kiểm thử (Testing Strategy) MUST dựa trên mức độ rủi ro, độ phức tạp và phạm vi ảnh hưởng của thay đổi.
- Sử dụng phối hợp Unit Test, Component/Web Slice Test, Integration Test, Contract Test và End-to-End Test khi phù hợp với ma trận kiểm thử được phê chuẩn.
- Các thành phần tích hợp như database, message broker và external services MUST được kiểm chứng tại integration boundaries (thông qua môi trường giả lập cục bộ hoặc test harness) khi có tương tác hoặc thay đổi.
- MUST chạy các verification commands thực tế và sử dụng kết quả quan sát được làm bằng chứng khách quan.
- Tuyệt đối MUST NOT tuyên bố hoàn thành task hoặc xác nhận verification PASS khi chưa thực sự chạy lệnh và thu được kết quả thành công.
- Test coverage là một chỉ số hỗ trợ đánh giá phạm vi kiểm thử, MUST NOT được coi là bằng chứng duy nhất hoặc thước đo tuyệt đối về chất lượng phần mềm.
- Rationale: Tạo lập mạng lưới an toàn (safety net) đáng tin cậy ngăn ngừa regression, đảm bảo mọi tính năng đưa vào vận hành đều đạt đúng tiêu chí nghiệm thu (Acceptance Criteria) và hoạt động chuẩn xác trong thực tế.

### VI. Error Handling & Observability
- Hệ thống MUST xử lý lỗi một cách nhất quán, có cấu trúc và có thể dự đoán trước trên toàn bộ các tầng xử lý; phản hồi lỗi ra bên ngoài boundary MUST tuân thủ schema chuẩn hóa.
- Tuyệt đối MUST NOT nuốt lỗi (swallow exceptions), che giấu nguyên nhân thất bại (root cause) hoặc để lộ stack trace/chi tiết hạ tầng nội bộ ra người dùng cuối.
- Cơ chế Logging và Monitoring SHOULD cung cấp đầy đủ thông tin ngữ cảnh (correlation IDs, timestamps, domain identifiers) để hỗ trợ chẩn đoán và khắc phục sự cố nhanh chóng.
- Tuyệt đối MUST NOT ghi credentials, secrets, tokens hoặc dữ liệu nhạy cảm vào logs.
- Các luồng nghiệp vụ quan trọng (critical business paths) SHOULD có khả năng quan sát (observability), truy vết (tracing) và giám sát trạng thái sức khỏe vận hành phù hợp.
- Rationale: Rút ngắn thời gian phát hiện và khắc phục sự cố (MTTR), duy trì tính minh bạch trong vận hành và bảo vệ an toàn thông tin khi xảy ra lỗi ngoại lệ.

### VII. Documentation & Technical Decisions
- Tài liệu kỹ thuật MUST phản ánh trung thực, chính xác hiện trạng mã nguồn và các quyết định kỹ thuật đã được thống nhất.
- Mọi thay đổi quan trọng về kiến trúc, API contracts, data model hoặc quy tắc nghiệp vụ MUST được cập nhật đồng bộ vào tài liệu liên quan trước hoặc cùng thời điểm phát hành mã nguồn.
- Các quyết định kỹ thuật quan trọng, có tầm ảnh hưởng lớn hoặc làm thay đổi hướng tiếp cận kiến trúc SHOULD được ghi nhận chính thức dưới dạng Architecture Decision Records (ADR).
- Tài liệu kỹ thuật MUST tránh sự trùng lặp (duplication) và bảo đảm mỗi nội dung thông tin đều có một nguồn chính thức duy nhất (Single Source of Truth).
- Rationale: Bảo tồn tri thức dự án (project memory), hỗ trợ đào tạo và bàn giao thuận tiện, đồng thời tạo cơ sở vững chắc cho việc bảo trì và tiến hóa hệ thống về sau.

### VIII. Dependency & Engineering Discipline
- Kỹ sư và AI Agent MUST ưu tiên tối đa việc sử dụng các thư viện, dependencies và năng lực hạ tầng hiện có nếu đáp ứng được yêu cầu kỹ thuật.
- Việc bổ sung một dependency hoặc công cụ mới MUST có lý do kỹ thuật chính đáng và MUST được đánh giá toàn diện về tính tương thích, rủi ro bảo mật, giấy phép (license) và chi phí bảo trì lâu dài.
- Phạm vi thay đổi (Change Scope) của mỗi task hoặc Pull Request MUST được giữ ở mức nhỏ, tập trung và có thể review; MUST tránh các thay đổi lan man hoặc sửa đổi mã nguồn không liên quan đến task hiện tại.
- Các khoản nợ kỹ thuật (Technical Debt) phát sinh hoặc phát hiện trong quá trình làm việc SHOULD được ghi nhận rõ ràng vào backlog để xử lý có kế hoạch, thay vì âm thầm tích lũy.
- Rationale: Giảm thiểu rủi ro chuỗi cung ứng phần mềm (supply chain risks), kiểm soát kích thước ứng dụng và duy trì kỷ luật kỹ thuật cao trong suốt vòng đời dự án.

### IX. Human Oversight & Governance
- Con người giữ quyền quyết định tối cao đối với mọi thay đổi quan trọng về nghiệp vụ, kiến trúc, bảo mật, mô hình dữ liệu, hợp đồng giao tiếp và quy trình phát hành.
- AI Agent MUST phân biệt rành mạch giữa sự thật đã được kiểm chứng (verified facts), giả định (assumptions) và đề xuất kỹ thuật (proposals).
- Khi đối mặt với sự thiếu hụt thông tin hoặc các yêu cầu có điểm mơ hồ có khả năng ảnh hưởng đáng kể đến thiết kế hoặc hành vi hệ thống, AI Agent MUST chủ động dừng lại và yêu cầu con người làm rõ (Clarification).
- Hiến pháp (Constitution) chỉ được thay đổi thông qua quy trình review và phê duyệt chính thức từ người có thẩm quyền.
- Khi phát sinh mâu thuẫn giữa yêu cầu mới và các nguyên tắc hiện hành trong Constitution, kỹ sư hoặc AI Agent MUST chỉ rõ mâu thuẫn và xin quyết định từ con người, tuyệt đối MUST NOT tự ý bỏ qua hoặc vi phạm nguyên tắc.
- Rationale: Bảo đảm AI Agent hoạt động như một cộng sự đắc lực dưới sự kiểm soát an toàn và định hướng chuẩn xác của con người, ngăn ngừa sai lệch định hướng kiến trúc và nghiệp vụ.

## Tiêu chuẩn Kỹ thuật & Ràng buộc Hệ thống

- Tích hợp theo Hợp đồng (Contract-First Integration): Mọi giao diện tương tác giữa các service, giữa client và backend, hoặc giữa các thành phần xử lý không đồng bộ MUST tuân thủ hợp đồng định nghĩa tại `contracts/` làm nguồn sự thật duy nhất. Bất kỳ sự thay đổi giao diện nào MUST được cập nhật tại hợp đồng trước khi triển khai mã nguồn.
- Tính Nhất quán Môi trường (Environment Parity): Kiến trúc hệ thống MUST hỗ trợ tính nhất quán môi trường ("cùng code, khác cấu hình"), cho phép toàn bộ luồng xử lý chính được kiểm thử và xác minh trên môi trường giả lập cục bộ trước khi triển khai lên môi trường đám mây thực tế.
- Phân định Ranh giới Lưu trữ và Xử lý (Storage & Compute Boundary Separation): Các tác vụ xử lý tính toán nặng hoặc truyền tải dữ liệu kích thước lớn MUST tuân theo cơ chế chuyển giao bất đồng bộ và tải lên trực tiếp (direct upload) thông qua cơ chế cấp quyền giới hạn thời gian; backend đóng vai trò điều phối luồng và quản lý trạng thái, MUST NOT trung chuyển trực tiếp luồng dữ liệu nặng qua tầng API.
- Quản lý Cấu hình và Bí mật (Configuration & Secrets Management): Cấu hình ứng dụng MUST được tách biệt khỏi mã nguồn và quản lý qua biến môi trường hoặc các dịch vụ quản lý tham số chuyên dụng; tuyệt đối không đưa cấu hình nhạy cảm vào kho lưu trữ mã nguồn.

## Quy trình Phát triển & Quality Gates

- Phát triển Dựa trên Đặc tả (Spec-Driven Development): Đối với các tính năng hoặc thay đổi có độ phức tạp cao, quá trình triển khai SHOULD tuân thủ nghiêm ngặt quy trình SDD (đặc tả yêu cầu, làm rõ, lập kế hoạch, phân rã công việc thành task nhỏ và kiểm chứng).
- Kiểm soát Phạm vi Công việc (WIP Control): Mặc định duy trì giới hạn một task duy nhất đang thực hiện tại một thời điểm (WIP=1) cho mỗi agent để đảm bảo chất lượng, tính tập trung và bằng chứng kiểm chứng độc lập.
- Cổng Kiểm chứng Bắt buộc (Verification Gates): Một task hoặc tính năng chỉ được đánh dấu trạng thái `verified` khi các lệnh kiểm thử và kiểm tra kỹ thuật bắt buộc đã thực thi thành công và có bằng chứng cụ thể.
- Kiểm duyệt và Phê duyệt (Review & Approval Gate): Mọi thay đổi mã nguồn trước khi tích hợp vào nhánh chính MUST thông qua Pull Request, vượt qua các bài kiểm tra tự động và nhận được sự phê duyệt của người phụ trách module (CODEOWNERS).

## Governance

- Hiến pháp (Constitution) là văn bản pháp quy kỹ thuật cao nhất của dự án TransVidCloud, có hiệu lực ưu tiên cao hơn mọi tài liệu hướng dẫn cục bộ khác khi có xung đột.
- Quy trình Sửa đổi (Amendment Procedure):
  1. Mọi đề xuất sửa đổi Constitution MUST nêu rõ lý do kỹ thuật, đánh giá phạm vi ảnh hưởng và kế hoạch tương thích hoặc chuyển đổi liên quan.
  2. Đề xuất sửa đổi MUST được xem xét, thảo luận và phê duyệt chính thức bởi người có thẩm quyền (Project Owner / Technical Lead).
  3. Mọi sửa đổi được phê duyệt MUST được cập nhật vào văn bản này kèm theo việc tăng phiên bản (version bump), cập nhật ngày sửa đổi và ghi nhận báo cáo tác động (Sync Impact Report).
- Chính sách Quản lý Phiên bản (Versioning Policy):
  Tuân thủ chuẩn Semantic Versioning (SemVer - `MAJOR.MINOR.PATCH`):
  - **MAJOR (X.0.0)**: Thay đổi không tương thích ngược về nguyên tắc quản trị, loại bỏ hoặc tái định nghĩa cơ bản một nguyên tắc cốt lõi.
  - **MINOR (x.Y.0)**: Bổ sung nguyên tắc mới, phần mới hoặc mở rộng đáng kể nội dung hướng dẫn mà không phá vỡ các quy tắc hiện có.
  - **PATCH (x.y.Z)**: Làm rõ câu chữ, giải thích bổ sung, sửa lỗi chính tả hoặc cải thiện cấu trúc trình bày không làm thay đổi bản chất của các nguyên tắc.
- Kỳ vọng Kiểm tra Tuân thủ (Compliance Review Expectations):
  - Mọi Pull Request, thiết kế kỹ thuật (Spec/Plan) và phiên làm việc của AI Agent MUST đối chiếu với các nguyên tắc trong Constitution.
  - Bất kỳ sự phức tạp hoặc ngoại lệ nào so với nguyên tắc đều phải được giải trình thuyết phục và được phê duyệt trước khi chấp nhận.
  - Sử dụng `AGENTS.md` và `docs/engineering-standards.md` làm tài liệu hướng dẫn thực thi chi tiết trong quá trình phát triển thường nhật.

**Version**: 1.0.0 | **Ratified**: 2026-10-31 | **Last Amended**: 2026-10-31
