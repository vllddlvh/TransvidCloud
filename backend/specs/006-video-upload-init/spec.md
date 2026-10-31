# Feature Specification: Khởi tạo Upload Video & Cấp S3 Presigned PUT URL

**Feature Branch**: `phuong-backend`

**Created**: 2026-10-31

**Status**: Draft

**Input**: User description: "F-006: Khởi tạo Upload Video & Cấp S3 Presigned PUT URL (Epic A3)"

## Clarifications

### Session 2026-10-31

- Q: Hệ thống nên xử lý như thế nào khi người dùng khởi tạo video mới có tiêu đề (`title`) trùng lặp với video đã có trong cùng Project? → A: **Cho phép tiêu đề trùng lặp trong cùng Project**. Mỗi video được định danh và phân biệt bằng `videoId` duy nhất, không áp đặt ràng buộc duy nhất trên trường `title` để thuận tiện cho việc tải nhiều phiên bản hoặc video cùng chủ đề.
- Q: Phần mở rộng tệp tin (`ext`) từ `filename` nên được chuẩn hóa như thế nào khi cấu thành đường dẫn lưu trữ S3 `inputKey` (`{projectId}/{videoId}/source.{ext}`)? → A: **Trích xuất phần mở rộng sau dấu chấm cuối cùng và chuẩn hóa về chữ thường (lowercase)** (ví dụ: `.MP4` -> `source.mp4`, `.MOV` -> `source.mov`, `my.video.clip.mkv` -> `source.mkv`).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Khởi tạo Video và nhận S3 Presigned PUT URL từ SaaS Web qua JWT (Priority: P1)

Người dùng quản trị dự án đã đăng nhập vào hệ thống SaaS Web (sử dụng Access Token dạng Bearer JWT) muốn tải lên một video mới vào dự án của mình. Người dùng cung cấp thông tin tiêu đề video (`title`), tên tệp tin gốc (`filename`), dung lượng dự kiến (`sizeBytes`) và loại nội dung (`contentType`). Hệ thống kiểm tra tính hợp lệ của dữ liệu đầu vào, tạo một bản ghi video mới trong cơ sở dữ liệu ở trạng thái `UPLOADING` với định danh quy ước (`videoId` dạng `vid_...`), sinh đường dẫn S3 Presigned PUT URL có thời hạn 15 phút trỏ tới bucket tiếp nhận (`inputKey = {projectId}/{videoId}/source.{ext}`), kèm theo các tiêu đề HTTP bắt buộc (`Content-Type` và `Content-Length`). Người dùng nhận được phản hồi chứa thông tin video vừa tạo cùng URL ký sẵn để trình duyệt có thể trực tiếp đẩy luồng tệp lên Amazon S3 mà không cần trung chuyển qua Backend.

**Why this priority**: Đây là luồng nghiệp vụ cốt lõi đầu tiên của Epic A3 (Upload & Storage). Không có chức năng này, người dùng trên SaaS Web hoàn toàn không thể đưa video vào hệ thống để tiếp tục quy trình chuyển mã.

**Independent Test**: Có thể kiểm thử độc lập hoàn toàn bằng cách gửi yêu cầu `POST /api/projects/{projectId}/videos` với Bearer JWT hợp lệ và dữ liệu video hợp lệ (ví dụ: `title`: "Bài giảng 1", `filename`: "demo.mp4", `sizeBytes`: 52428800, `contentType`: "video/mp4"). Xác minh hệ thống phản hồi HTTP 201 Created chứa `videoId`, `status = UPLOADING`, `inputKey`, và đối tượng `upload` chứa `presignedUrl` (phương thức PUT, hết hạn sau 900 giây, kèm tiêu đề `Content-Type` và `Content-Length`), đồng thời kiểm tra bản ghi tương ứng đã được lưu trong bảng DynamoDB `videos`.

**Acceptance Scenarios**:

1. **Given** Người dùng đã đăng nhập với Bearer JWT hợp lệ và là chủ sở hữu (`ownerId`) của một Project đang hoạt động (`ACTIVE`), **When** Gửi yêu cầu `POST /api/projects/{projectId}/videos` với thông tin video hợp lệ (`title` từ 1-200 ký tự, `filename` có đuôi hợp lệ, `sizeBytes <= 209715200`, `contentType` thuộc danh mục cho phép), **Then** Hệ thống tạo bản ghi mới trong bảng `videos` với `status = UPLOADING`, `attemptCount = 0`, sinh S3 Presigned PUT URL có hiệu lực 900 giây và phản hồi HTTP 201 Created kèm dữ liệu video và thông tin upload.
2. **Given** Người dùng gửi yêu cầu khởi tạo video thành công, **When** Kiểm tra URL `presignedUrl` được cấp trong phản hồi, **Then** URL phải có cấu trúc trỏ tới S3 bucket tiếp nhận (`tvc-{env}-input`), đường dẫn đối tượng tuân thủ đúng quy ước `{projectId}/{videoId}/source.{ext}` và chứa chữ ký AWS SigV4 hợp lệ.
3. **Given** Người dùng gửi yêu cầu với các trường bắt buộc bị thiếu hoặc không đúng định dạng (ví dụ `title` để trống hoặc vượt quá 200 ký tự, `filename` để trống, `sizeBytes <= 0`), **When** Hệ thống xử lý yêu cầu, **Then** Hệ thống từ chối và phản hồi mã lỗi HTTP 400 Bad Request (`INVALID_KEY` hoặc lỗi validation).

---

### User Story 2 - Khởi tạo Video tự động từ Developer Application qua API Key (Priority: P1)

Ứng dụng của nhà phát triển bên thứ ba (Developer Client) muốn tự động hóa quy trình tải video lên TransvidCloud theo chương trình. Ứng dụng gửi yêu cầu khởi tạo video kèm theo tiêu đề xác thực `X-API-Key: tvc_live_...` của dự án tương ứng. Bộ lọc bảo mật kép (Dual Security Filter) tự động xác thực khóa API, kiểm tra sự tương khớp với `projectId` trên URL và cho phép tạo bản ghi video cũng như cấp S3 Presigned PUT URL tương tự như người dùng SaaS Web.

**Why this priority**: Hỗ trợ các kịch bản tích hợp tự động qua API (Developer Platform / PaaS), đảm bảo tính nhất quán giữa SaaS Web và Developer API.

**Independent Test**: Gửi yêu cầu `POST /api/projects/{projectId}/videos` với tiêu đề `X-API-Key` hợp lệ của `projectId` đó; xác minh hệ thống xác thực thành công, trả về HTTP 201 Created cùng bản ghi video và Presigned PUT URL.

**Acceptance Scenarios**:

1. **Given** Client gửi yêu cầu kèm tiêu đề `X-API-Key` hợp lệ gắn liền với `projectId` đích, **When** Gửi thông tin video hợp lệ, **Then** Hệ thống xác thực danh tính thành công và phản hồi HTTP 201 Created kèm thông tin video ở trạng thái `UPLOADING` và Presigned URL 15 phút.
2. **Given** Client gửi yêu cầu kèm `X-API-Key` hợp lệ của Project A nhưng lại gọi vào URL của Project B (`/api/projects/prj_B/videos`), **When** Hệ thống kiểm tra ranh giới ủy quyền, **Then** Hệ thống từ chối với HTTP 403 Forbidden (`ACCESS_DENIED`).
3. **Given** Client gửi yêu cầu với `X-API-Key` không tồn tại, sai định dạng hoặc đã bị thu hồi, **When** Hệ thống kiểm tra xác thực, **Then** Hệ thống từ chối với HTTP 401 Unauthorized (`UNAUTHENTICATED`).

---

### User Story 3 - Kiểm soát giới hạn dung lượng tối đa 200MB (Priority: P1)

Hệ thống cần ngăn chặn sớm việc người dùng cố gắng tải lên các tệp video có kích thước vượt quá giới hạn tối đa của gói MVP là 200 MB (209,715,200 bytes) theo thỏa thuận `contracts/upload-limits.md`.

**Why this priority**: Bảo vệ hạ tầng khỏi việc bị quá tải dung lượng lưu trữ và lãng phí băng thông mạng, đồng thời đưa ra phản hồi rõ ràng ngay lập tức cho client.

**Independent Test**: Gửi yêu cầu khởi tạo video với `sizeBytes = 209715201` (200MB + 1 byte); xác minh hệ thống lập tức từ chối và phản hồi HTTP 413 Payload Too Large mà không tạo bất kỳ bản ghi nào trong cơ sở dữ liệu hay sinh URL S3.

**Acceptance Scenarios**:

1. **Given** Client gửi yêu cầu khởi tạo video với `sizeBytes` vượt quá `209715200` bytes (200 MB), **When** Hệ thống thẩm định dung lượng, **Then** Hệ thống từ chối và phản hồi HTTP 413 Payload Too Large (`PAYLOAD_TOO_LARGE` / "Video file size exceeds maximum limit of 200MB").
2. **Given** Client gửi yêu cầu với `sizeBytes` đúng bằng `209715200` bytes (ngưỡng biên trên chính xác), **When** Hệ thống thẩm định dung lượng, **Then** Hệ thống chấp nhận yêu cầu và phản hồi HTTP 201 Created.

---

### User Story 4 - Kiểm soát định dạng video và phần mở rộng tệp tin hợp lệ (Priority: P1)

Hệ thống chỉ hỗ trợ 4 định dạng container video tiêu chuẩn phục vụ quá trình chuyển mã HLS: MP4, MOV, MKV và WebM (tương ứng với MIME types: `video/mp4`, `video/quicktime`, `video/x-matroska`, `video/webm`). Tên tệp tin gốc `filename` cũng phải có đuôi mở rộng tương ứng (`.mp4`, `.mov`, `.mkv`, `.webm`). Nếu người dùng gửi định dạng tệp không được hỗ trợ (ví dụ AVI, WMV, FLV, hoặc tệp âm thanh/hình ảnh), hệ thống phải từ chối.

**Why this priority**: Tránh lãng phí tài nguyên lưu trữ và lỗi chuyển mã downstream tại Worker khi client tải lên các định dạng không được hỗ trợ.

**Independent Test**: Gửi yêu cầu khởi tạo video với `contentType = "video/avi"` hoặc `filename = "test.avi"`; xác minh hệ thống từ chối với HTTP 422 Unprocessable Entity (`UNPROCESSABLE_ENTITY` / "Unsupported video content type").

**Acceptance Scenarios**:

1. **Given** Client gửi yêu cầu với `contentType` không thuộc danh sách cho phép (`video/mp4`, `video/quicktime`, `video/x-matroska`, `video/webm`), **When** Hệ thống thẩm định định dạng, **Then** Hệ thống từ chối và phản hồi HTTP 422 Unprocessable Entity (`UNPROCESSABLE_ENTITY`).
2. **Given** Client gửi yêu cầu với `filename` không có phần mở rộng hoặc phần mở rộng không khớp với danh sách đuôi video hợp lệ (`.mp4`, `.mov`, `.mkv`, `.webm`), **When** Hệ thống thẩm định phần mở rộng, **Then** Hệ thống từ chối và phản hồi HTTP 422 Unprocessable Entity (`UNPROCESSABLE_ENTITY`).
3. **Given** Client gửi yêu cầu với `contentType = "video/mp4"` và `filename = "video.mp4"` (hợp lệ), **When** Hệ thống xử lý, **Then** Hệ thống trích xuất phần mở rộng `mp4` để cấu thành `inputKey = {projectId}/{videoId}/source.mp4` và tạo video thành công.

---

### User Story 5 - Phân quyền và cô lập tài nguyên Project (Priority: P1)

Hệ thống bảo đảm rằng chỉ người dùng sở hữu Project (khi dùng Bearer JWT) hoặc ứng dụng sở hữu API Key hợp lệ của Project đó mới có quyền khởi tạo video. Nếu truy cập trái phép hoặc thao tác trên Project không tồn tại, hệ thống phải từ chối.

**Why this priority**: Bảo đảm tính bảo mật đa người dùng (Multi-tenancy isolation), ngăn chặn việc tải trộm video vào dự án của người khác.

**Independent Test**: Dùng tài khoản Người dùng B gọi API tạo video vào Project thuộc quyền sở hữu của Người dùng A -> xác minh bị từ chối HTTP 403 Forbidden (`ACCESS_DENIED`). Gọi tới một `projectId` ngẫu nhiên không tồn tại -> xác minh bị từ chối HTTP 404 Not Found (`RESOURCE_NOT_FOUND`).

**Acceptance Scenarios**:

1. **Given** Người dùng đăng nhập bằng JWT nhưng không phải là chủ sở hữu (`ownerId`) của Project, **When** Gửi yêu cầu khởi tạo video vào Project đó, **Then** Hệ thống từ chối với HTTP 403 Forbidden (`ACCESS_DENIED`).
2. **Given** Yêu cầu được gửi tới một `projectId` không tồn tại trong hệ thống hoặc đã bị đánh dấu xóa mềm (`DELETED`), **When** Hệ thống kiểm tra sự tồn tại của Project, **Then** Hệ thống từ chối với HTTP 404 Not Found (`RESOURCE_NOT_FOUND`).
3. **Given** Yêu cầu không có thông tin xác thực (thiếu cả Bearer JWT và `X-API-Key`), **When** Gửi yêu cầu tới endpoint, **Then** Hệ thống từ chối với HTTP 401 Unauthorized (`UNAUTHENTICATED`).

---

### Edge Cases

- Tên file `filename` chứa ký tự đường dẫn nguy hiểm (`../`, ký tự điều khiển, ký tự Unicode đặc biệt) -> Hệ thống làm sạch (sanitize) hoặc chỉ trích xuất phần mở rộng an toàn chữ thường để tạo `inputKey`, bảo đảm không gây rủi ro Path Traversal.
- Tên file có nhiều dấu chấm (ví dụ: `my.lecture.v1.0.mp4`) hoặc đuôi tệp viết hoa (`.MP4`, `.MOV`) -> Hệ thống trích xuất chuỗi sau dấu chấm cuối cùng và chuyển thành chữ thường để sinh `inputKey = {projectId}/{videoId}/source.mp4`.
- Người dùng gửi yêu cầu khởi tạo video có `title` trùng lặp với video đã tồn tại trong Project -> Hệ thống vẫn chấp nhận và xử lý bình thường, cấp `videoId` mới độc lập.
- Client gửi đồng thời cả `Authorization: Bearer <JWT>` và `X-API-Key: ...` -> Hệ thống tuân thủ quy tắc ưu tiên xác thực theo Bearer JWT của người dùng SaaS Web; chỉ khi không có JWT mới xác thực qua `X-API-Key`.
- Tên tiêu đề video `title` chứa khoảng trắng ở đầu/cuối -> Hệ thống tự động cắt tỉa (trim); nếu sau khi cắt tỉa chuỗi rỗng thì từ chối HTTP 400 Bad Request.
- Đồng hồ hệ thống hoặc thời gian hết hạn S3 Presigned URL: Presigned URL được cấu hình hết hạn chính xác sau 900 giây (15 phút) tính từ thời điểm ký.
- Trường hợp `sizeBytes` khai báo là 0 byte hoặc số âm -> Từ chối ngay bằng HTTP 400 Bad Request (`Validation failed: sizeBytes must be greater than 0`).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống BẮT BUỘC cung cấp endpoint `POST /api/projects/{projectId}/videos` cho phép khởi tạo một bản ghi video mới và cấp S3 Presigned PUT URL.
- **FR-002**: Hệ thống BẮT BUỘC hỗ trợ cơ chế xác thực kép (Dual Security): chấp nhận `Authorization: Bearer <JWT>` từ SaaS Web User và `X-API-Key: <key>` từ Developer Client.
- **FR-003**: Khi xác thực bằng Bearer JWT, hệ thống BẮT BUỘC kiểm tra quyền sở hữu (`ownerId == currentUser.userId`) và trạng thái của Project (`status == ACTIVE`). Nếu không khớp quyền, từ chối với HTTP 403 Forbidden; nếu Project không tồn tại hoặc đã bị xóa mềm (`DELETED`), từ chối với HTTP 404 Not Found.
- **FR-004**: Khi xác thực bằng `X-API-Key`, hệ thống BẮT BUỘC thẩm định khóa trong bảng `apikeys`, kiểm tra `key.projectId == request.projectId` và kiểm tra Project cha đang `ACTIVE`. Nếu sai khóa, từ chối với HTTP 401 Unauthorized; nếu không khớp Project, từ chối với HTTP 403 Forbidden.
- **FR-005**: Hệ thống BẮT BUỘC thẩm định dữ liệu đầu vào `InitVideoRequest`:
  - `title`: Chuỗi không rỗng, từ 1 đến 200 ký tự (cho phép trùng lặp tiêu đề với các video khác trong cùng Project).
  - `filename`: Chuỗi không rỗng, từ 1 đến 255 ký tự, có phần mở rộng thuộc danh sách: `mp4`, `mov`, `mkv`, `webm` (không phân biệt hoa thường).
  - `sizeBytes`: Số nguyên dương (`1 <= sizeBytes <= 209715200`).
  - `contentType`: Chuỗi thuộc danh mục: `video/mp4`, `video/quicktime`, `video/x-matroska`, `video/webm`.
- **FR-006**: Nếu `sizeBytes` vượt quá 200 MB (`> 209715200` bytes), hệ thống BẮT BUỘC từ chối và phản hồi HTTP 413 Payload Too Large với mã lỗi chuẩn `ErrorCode.PAYLOAD_TOO_LARGE` ("Video file size exceeds maximum limit of 200MB").
- **FR-007**: Nếu `contentType` hoặc phần mở rộng tệp tin không hợp lệ, hệ thống BẮT BUỘC từ chối và phản hồi HTTP 422 Unprocessable Entity với mã lỗi chuẩn `ErrorCode.UNPROCESSABLE_ENTITY` ("Unsupported video content type").
- **FR-008**: Hệ thống BẮT BUỘC sinh định danh duy nhất cho video theo quy ước tiền tố `vid_` kết hợp chuỗi ngẫu nhiên 12 ký tự hex (ví dụ: `vid_4a77e1c8d102`) sử dụng bộ sinh ngẫu nhiên an toàn (CSPRNG/UUID).
- **FR-009**: Hệ thống BẮT BUỘC cấu thành đường dẫn lưu trữ S3 input (`inputKey`) theo đúng quy ước của `contracts/s3-key-convention.md`: `{projectId}/{videoId}/source.{ext}`, trong đó `{ext}` là phần mở rộng được trích xuất từ phần chuỗi sau dấu chấm cuối cùng của `filename` và chuẩn hóa về chữ thường (lowercase).
- **FR-010**: Hệ thống BẮT BUỘC lưu trữ bản ghi video mới vào bảng DynamoDB `videos` (`tvc-{env}-videos`) với các trường:
  - `videoId` (PK, String).
  - `projectId` (GSI PK `project-index`, String).
  - `title` (String).
  - `sizeBytes` (Long).
  - `contentType` (String).
  - `status` = `UPLOADING` (theo `contracts/video-state-machine.md`).
  - `attemptCount` = 0.
  - `inputKey` (String).
  - `assets` = null.
  - `errorCode` = null.
  - `createdAt` và `updatedAt` = Thời điểm hiện tại UTC (ISO-8601).
- **FR-011**: Hệ thống BẮT BUỘC sử dụng `S3Presigner` để ký S3 Presigned PUT URL:
  - Bucket: `tvc-{env}-input` (lấy từ cấu hình `tvcProperties.getS3().getBucketInput()`).
  - Key: `inputKey`.
  - HTTP Method: `PUT`.
  - Thời gian hết hạn: 900 giây (15 phút).
  - Ký kèm tiêu đề `Content-Type` và `Content-Length`.
- **FR-012**: Hệ thống BẮT BUỘC phản hồi HTTP 201 Created bọc trong `ApiResponse<InitVideoResponse>` chứa:
  - Đối tượng `video` (khớp schema `VideoResponse`).
  - Đối tượng `upload` (khớp schema `PresignedUploadInfo`: `presignedUrl`, `method: "PUT"`, `expiresInSeconds: 900`, `requiredHeaders: {"Content-Type": ..., "Content-Length": ...}`).
- **FR-013**: Tuyệt đối KHÔNG nhận, đọc hoặc trung chuyển luồng byte của tệp video qua Backend trong yêu cầu này.
- **FR-014**: Mọi lỗi phát sinh BẮT BUỘC được chuẩn hóa qua `GlobalExceptionHandler` theo định dạng `ApiResponse` thống nhất.

### Key Entities *(include if feature involves data)*

- **VideoItem** (Bảng DynamoDB `videos`):
  - `videoId` (String, Partition Key): Mã định danh duy nhất của video (`vid_...`).
  - `projectId` (String, GSI Partition Key `project-index`): Mã định danh dự án chứa video.
  - `title` (String): Tiêu đề hiển thị của video.
  - `sizeBytes` (Long): Kích thước tệp video tính bằng byte.
  - `contentType` (String): MIME type của video (`video/mp4`, v.v.).
  - `status` (String): Trạng thái vòng đời (`UPLOADING`, `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED_*`).
  - `attemptCount` (Integer): Số lần đã giao/xử lý job (khởi tạo bằng 0).
  - `inputKey` (String): Khóa lưu trữ S3 của video gốc (`{projectId}/{videoId}/source.{ext}`).
  - `assets` (Map / Object): Đường dẫn các tài nguyên đầu ra (HLS master, 360p, 720p, thumbnail) - khởi tạo rỗng/null.
  - `errorCode` (String): Mã lỗi xử lý nếu thất bại - khởi tạo null.
  - `createdAt` (String / Instant): Thời điểm tạo bản ghi (SK của GSI `project-index`).
  - `updatedAt` (String / Instant): Thời điểm cập nhật trạng thái gần nhất.
  - `startedAt` (String / Instant): Thời điểm bắt đầu xử lý chuyển mã (null khi mới tạo).
  - `completedAt` (String / Instant): Thời điểm hoàn tất xử lý (null khi mới tạo).
- **InitVideoRequest**:
  - `title` (String, required): Tiêu đề video (1-200 ký tự).
  - `filename` (String, required): Tên tệp tin gốc kèm phần mở rộng hợp lệ.
  - `sizeBytes` (Long, required): Dung lượng tệp tính bằng byte (tối đa 209715200).
  - `contentType` (String, required): Loại nội dung MIME type cho phép.
- **InitVideoResponse**:
  - `video` (`VideoResponse`): Thông tin metadata của video vừa được tạo ở trạng thái `UPLOADING`.
  - `upload` (`PresignedUploadInfo`): `presignedUrl` (String), `method` ("PUT"), `expiresInSeconds` (900), `requiredHeaders` (Map chứa `Content-Type` và `Content-Length`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Client nhận được phản hồi khởi tạo video và S3 Presigned PUT URL hợp lệ trong thời gian dưới 500ms đối với các yêu cầu hợp lệ.
- **SC-002**: 100% các yêu cầu có dung lượng `sizeBytes` vượt quá 200MB (209,715,200 bytes) bị chặn ngay lập tức tại tầng API với mã lỗi HTTP 413 Payload Too Large.
- **SC-003**: 100% các tệp có định dạng MIME type hoặc đuôi tệp không thuộc danh mục hợp lệ (`mp4`, `mov`, `mkv`, `webm`) bị từ chối với mã lỗi HTTP 422 Unprocessable Entity.
- **SC-004**: URL S3 Presigned PUT sinh ra có thể được sử dụng trực tiếp bởi client để đẩy file mẫu lên bucket S3 (`tvc-{env}-input`) thành công thông qua phương thức HTTP PUT.
- **SC-005**: 100% các kiểm thử hồi quy (Zero Regression) cho toàn bộ 89 test hiện tại tiếp tục đạt PASS, cùng với bộ kiểm thử mới (Unit Tests và Integration Test `VideoUploadInitIT` trên LocalStack S3 + DynamoDB) đạt PASS 100%.

## Assumptions

- Môi trường S3 input bucket (`tvc-{env}-input`) đã được khởi tạo sẵn qua script cấu hình hạ tầng LocalStack hoặc Terraform.
- Client trực tiếp chịu trách nhiệm thực hiện lệnh HTTP PUT lên `presignedUrl` với các header tương ứng; Backend không can thiệp vào quá trình truyền tải byte.
- Cấu hình `TVC_S3_PRESIGN_ENDPOINT` hoặc endpoint mặc định của S3 client cho phép sinh presigned URL tương thích với môi trường chạy (cả LocalStack lẫn AWS thực tế).
- Việc xác thực `HeadObject` và đẩy job sang SQS sẽ được thực hiện ở Feature tiếp theo (**F-007: Confirm Video Upload**) khi client tải xong file.
