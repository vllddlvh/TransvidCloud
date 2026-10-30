# Week 2 — Trạng thái thật (polling) + trình phát HLS (F4–F5, chế độ độc lập)

Tuần làm việc thứ hai của frontend, tương ứng **Sprint 3 (phần đầu)** trong [2. Timelines.md](../2.%20Timelines.md).

> **Vẫn làm trên mock.** Tuần này hoàn thiện hai task cuối của luồng chính — **F4 polling trạng thái** và **F5 trình phát HLS + link streaming + mã nhúng** — trên nền mock layer đã dựng ở [work/Week1.md](work/Week1.md). Không chờ worker (A4) hay CloudFront (C4): mock tự mô phỏng vòng đời `PENDING → PROCESSING → COMPLETED/FAILED_*` và phục vụ playlist/segment giả theo đúng key convention. Hợp nhất với thật vẫn vào **tuần 9**.

Phạm vi tuần này: **F4. Danh sách video + polling (0,25 pd) · F5. Trình phát HLS + link streaming + mã nhúng (0,25 pd)**, cộng thêm phần mock nền cho cả hai (mở rộng mock state machine + media fixture, ~0,5 pd). Tổng ~1,0 pd — gấp đôi baseline A6 cho phần sprint này vì mock phải tự đóng vai worker và vai `/media/*`.

- **Chủ trì:** Vũ (lưu ý: Vũ đồng thời chạy [worker Week2](../../docs-worker/work/Week2.md) — A4/A5 vẫn ưu tiên tiên 1 vì gate M2; frontend tuần này cắt được, mock không cắt được).
- **Kế thừa từ Week1 (đã kiểm chứng):** khung app + token + router 5 route; mock layer MSW với interface `mock|real`; fixture 3 project / 7 video đủ 7 trạng thái; luồng upload presigned 3 bước + XHR progress + retry; e2e 10/10.
- **Nguyên tắc tiếp tục áp dụng:** đường dẫn tương đối `/api/...`, `/media/...`; màu/kích thước qua token `variables.css`; TypeScript kiểu chặt; **mock và real cùng một interface — UI không biết mình đang gọi ai**. Mọi giả định mới (nhánh media fixture, polling, embed URL) ghi tiếp vào [5. API Integration.md](../5.%20API%20Integration.md) với nhãn **"giả định — chờ Phương xác nhận"**.

## Mục tiêu cuối tuần

Người dùng upload video → xác nhận → thấy `PENDING` trong danh sách, trạng thái **tự chuyển** `PROCESSING` rồi `COMPLETED` sau vài giây mà không cần tải lại trang (polling trên mock sinh trạng thái theo state machine). Mở trang chi tiết video `COMPLETED`: **trình phát hls.js chạy được playlist do mock phục vụ** (debug mode trên fixture nhỏ), nút **copy link streaming** và **copy mã nhúng `<iframe>`** hoạt động; route `/embed?video=<id>` phát được không sidebar/header. Các nhánh `FAILED_*` hiện đúng `errorCode` + gợi ý. `npm test`, `npm run build`, `npm run test:e2e` pass. **Chưa cần backend, worker thật, LocalStack hay CloudFront.**

---

## Buổi 1 — Mock đóng vai worker + mock phục vụ media (0,5 pd, nền cho F4/F5)

F4 và F5 đều cần "phía thật" mà tuần này chưa có: worker ghi trạng thái và `/media/*` phát playlist. Buổi 1 dựng cả hai trong mock — làm trước, vì mọi task sau phụ thuộc.

- [ ] **W2.1 Mock state machine: tự sinh `PENDING → PROCESSING → COMPLETED`**
  - Đây là phần **mock** đóng vai worker, không phải frontend tự gán trạng thái trong luồng thật — ghi chú ngược lại với Week1 F3.2 (tuần 1 cố ý không làm vì chưa có polling; tuần 2 có polling nên dựng được)
  - Cơ chế: mock tick khi có request `GET` (list/detail) — video `PENDING` chuyển `PROCESSING` sau ~5–10 giây, `PROCESSING` chuyển `COMPLETED` (kèm `assets` đủ `hls`/`hls360`/`hls720`/`thumbnail` theo URL `/media/{projectId}/{videoId}/...`) sau ~10–20 giây; ghi `updatedAt` mỗi lần
  - Biến môi trường điều khiển: `VITE_MOCK_PROCESS=auto|freeze|fail` — `auto` (mặc định dev), `freeze` (trạng thái đứng yên, phục vụ test polling không phụ thuộc thời gian), `fail` (chuyển `PROCESSING → FAILED_TRANSCODE` kèm `errorCode` giả định `TRANSCODE_FAILED`)
  - Rule respect state machine [video-state-machine.md](../../contracts/video-state-machine.md): `COMPLETED` không bị ghi đè; `FAILED_*` chỉ đi từ `PROCESSING`; không suy giảm trạng thái
  - **Không làm thêm:** mô phỏng `attemptCount`, retry DLQ, queue wait — frontend chỉ thấy trạng thái cuối qua API
- [ ] **W2.2 Mock `/media/*`: fixture HLS tối thiểu để player chạy thật**
  - Thêm MSW handler cho `/media/**` (route mới, currently bypass theo Week1 mục 6.3) sinh: master playlist (adaptive, trỏ 2 rendition `360p`/`720p`), variant playlist (5 segment, `targetduration` ~2s), thumbnail `thumb/default.jpg` (JPEG đặc generated, không ảnh ngoài)
  - **Segment:** tạo fixture byte hợp lệ đủ để **hls.js debug mode** parse playlist + segment mà không lỗi fatal — ghi rõ đây là bằng chứng "pipeline player hoạt động" (mức A4 POC tuần 1 của worker từng dùng cách tương tự: byte giả + debug mode), **không phải** luồng video nhìn được; video nhìn được thuộc hợp nhất tuần 9 khi worker thật (A4) xuất output thật
  - Hai lựa chọn segment, cân nhắc khi làm: (a) tự sinh MPEG-TS null packet tối giản, hoặc (b) dùng file test nhỏ do `ffmpeg` sinh một lần và commit dưới `frontend/src/mocks/media/` (≤100 KB) rồi handler phát byte thật — ưu tiên (b) nếu tổng nhẹ, vì hls.js không debug mode vẫn ổn. **Quyết định + lý do ghi vào `5. API Integration.md`**
  - Content-Type đúng [s3-key-convention.md](../../contracts/s3-key-convention.md): `.m3u8` → `application/vnd.apple.mpegurl`, `.ts` → `video/mp2t`, `.jpg` → `image/jpeg`
  - Fixture `demo-completed` (Week1 §4) trở thành video play-first trên mock; mỗi video mock `COMPLETED` tự sinh cũng trỏ cùng bộ media này — chấp nhận đơn giản hoá (mọi video "đã xong" phát cùng nội dung), ghi rõ là giả định
  - **Không tự thêm vào OpenAPI SaaS inventory** — `/media/*` là CloudFront/nginx origin, không phải API endpoint (khác với `/__mock-upload/` tuần 1 vì tuần đó không có player). Ghi giả định "UI resolve `assets.hls` như URL tương đối, trình duyệt fetch qua `/media/*`" chờ Phương + Lưu/Nguyên xác nhận cách real serve

**Xong khi:** bật `VITE_API_MODE=mock` + `VITE_MOCK_PROCESS=auto`, một video confirm xong tự chạy tới `COMPLETED` với assets trong ≤30 giây; `curl` qua dev server (hoặc test node) với `/media/.../hls/master.m3u8` trả playlist `#EXTM3U` + `Content-Type` đúng.

## Buổi 2 — F4. Polling + hoàn thiện danh sách video (0,25 pd baseline + ~0,25 pd việc polling)

- [ ] **F4.1 Cơ chế polling (làm trước, dùng cho cả list lẫn detail)**
  - Hook `usePolling` (hoặc mở rộng `useResource`): khi kết quả hiện tại chứa video `PENDING`/`PROCESSING`, gọi lại `GET` sau mỗi **3 giây** (đề xuất 3–5 s trong [3. Pages & Features.md](../3.%20Pages%20%26%20Features.md) mục 3 → chọn 3 s, ghi nhãn giả định); dừng khi mọi video về trạng thái cuối (`COMPLETED`/`FAILED_*`), khi trang unmount, hoặc khi tab ẩn (`document.visibilityState`) — resume khi tab hiện lại
  - Không chồng request: tick tiếp theo chỉ chạy sau khi request trước kết thúc; giữ dữ liệu cũ trong lúc refresh (không flash loading)
  - **Polling list = đi lại toàn bộ `listVideos`** — không có endpoint trạng thái batch/tối giản trong contract; **đây là gap contract, mở issue/ghi vào `docs/contracts/` cho Phương, không tự bịa trong mock** (theo Week1 F3.3). Tạm chấp nhận cho quy mô demo (≤50 item/trang)
  - Xử lý lỗi theo bảng "Xử lý lỗi chung" trang 3: lỗi đơn lẻ → giữ trạng thái cũ + đếm liên tiếp; **≥3 lần lỗi liên tiếp** → hiển thị "trạng thái không xác định" + nút tải lại; 401 → theo luồng session-expired sẵn có, dừng polling
- [ ] **F4.2 Danh sách video `/projects/:id/videos` — thay placeholder "arrive in Week 2"**
  - Badge đủ 7 trạng thái (đã có sẵn `VideoStatus` từ Week1; thêm spinner `PROCESSING`/`UPLOADING` quay đúng token, không thêm màu mới)
  - Row cập nhật live nhờ F4.1; thêm cột thời lượng + thumbnail khi có assets (fixture đã đủ mọi nhánh — kiểm cả nhánh thiếu thumbnail không vỡ layout)
  - Sau confirm từ UploadDialog: video mới `PENDING` **tự xuất hiện và tự chạy** tới trạng thái cuối không cần reload (bỏ dòng footnote Week1 ở trang)
- [ ] **F4.3 Trang chi tiết `/videos/:id` — polling một video + nhánh lỗi**
  - Poll `GET /videos/{id}` khi `PENDING`/`PROCESSING`; khi `COMPLETED` → render player (buổi 3); khi `FAILED_*` → hiện `errorCode` + gợi ý theo trang 3 mục 5 (vd `DURATION_EXCEEDED` → "vượt 5 phút", resolution → "tối đa 1080p"; bảng message ánh xạ errorCode là **giả định — chờ Phương xác nhận** vì errorCode worker thật chưa chốt — Week1 §4 đã ghi `TRANSCODE_FAILED` v.v. chỉ là đề xuất)
  - Retry đề xuất từ worker: với `FAILED_TRANSCODE`/`FAILED_STORAGE`, UI chỉ ghi "hệ thống sẽ thử lại tự động" (retry thuộc SQS/worker, frontend **không** có nút retry job) — câu chữ chốt ở tuần 9

**Xong khi (F4):** upload + confirm một mp4 → không reload, danh sách tự chuyển `PENDING → PROCESSING → COMPLETED` trong ~30 s; tab ẩn thì ngừng gọi, hiện lại thì bắt tiếp; tắt mock giữa lúc poll (`VITE_API_MODE=real` không backend) → sau 3 lần lỗi hiện "không xác định" + nút tải lại; unit test polling dùng fake timer + `VITE_MOCK_PROCESS=freeze` (không test chờ thời gian thật).

## Buổi 3 — F5. HLS player + link streaming + embed + e2e (0,25 pd baseline + ~0,25 pd integration)

- [ ] **F5.1 Component `src/player/HlsPlayer.tsx`**
  - Cài `hls.js` (dependency mới — hiện chưa có trong `package.json`; version mới nhất tại thời điểm cài, ghi vào `5. API Integration.md`)
  - Logic: `Hls.isSupported()` → tạo instance, `loadSource(assets.hls)`, `attachMedia`, `MANIFEST_PARSED` rồi mới autoplay-play; ngược lại nếu `canPlayType('application/vnd.apple.mpegurl')` (Safari) → gán `video.src` trực tiếp (native HLS)
  - **Không hard-code rendition nào** — chỉ ăn master playlist từ `assets.hls`; switch 360p/720p để hls.js autoder tự quyết (fixture 2 rendition có sẵn để thấy manifest nhiều level)
  - Cleanup bắt buộc: `hls.destroy()` + clear `src` khi unmount/route đổi/video `COMPLETED → FAILED` (không thể xảy ra theo state machine, nhưng guard khi detail reload) — kiểm bằng test có timer, không để instance rò khi polling re-render
  - Trạng thái UI: poster = `assets.thumbnail` (nếu thiếu → nền token xám, không broken image); lỗi `FATAL` (network/m/media) → card lỗi + nút "Thử lại" gọi `hls.startLoad()`/re-init; điều khiển để native `controls` của `<video>` theo layout trang 3
  - Container responsive: 16/9, radius/border theo token, không shadow/gradient; mobile 390px không vỡ (đã có pattern từ Week1 e2e)
- [ ] **F5.2 Link streaming + mã nhúng trên trang chi tiết**
  - Nút "Copy link streaming": copy **URL tuyệt đối** `location.origin + assets.hls` (master playlist) — clipboard qua `navigator.clipboard.writeText` với fallback `document.execCommand` cho môi trường e2e/http; toast feedback dạng Card theo layout Week1
  - Nút "Copy mã nhúng": sinh `<iframe src="{origin}/embed?video={videoId}" width="640" height="360" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen frameborder="0"></iframe>` (chỉ khi `COMPLETED`; kích thước/attributes là **giả định** ghi vào `5. API Integration.md`)
  - **Thứ tự cắt giảm** ([2. Timelines.md](../2.%20Timelines.md) mục 4): nếu quá tải, bỏ mã nhúng trước, giữ player + link streaming — F5.2 cắt được riêng phần iframe, F5.1 không cắt
- [ ] **F5.3 Trang embed `/embed?video=<id>` — thay route placeholder**
  - Route hiện tại `/embed` không tham số → chuyển thành query `?video=<id>` (embed URL phải không phụ thuộc routing nội dung khác). Nếu contract chưa mô tả, ghi là giả định frontend
  - Chỉ player `<video>` + fullscreen: **không** sidebar/header, `margin:0`, nền `#0f0f0f` đã có trong token, không brand chrome
  - Hành vi: video không `COMPLETED` hoặc không tìm thấy → card thông báo gọn ("Video chưa sẵn sàng"), **không autoplay** (chờ `canplay`/manifest parsed; embed autoplay là giả định — chưa bật, tránh chính sách chặn autoplay của browser)
  - Trang nhúng chỉ dùng `GET /videos/{id}` (bearer?) — **embed không đăng nhập**: cần contract cho phép đọc video `COMPLETED` không bearer hoặc presigned embed URL. **Gap contract — mở issue cho Phương**, tuần này mock tạm cho embed đọc được `/media/*` nhưng API detail vẫn yêu cầu bearer, embed hiển thị demo-only; ghi rõ vào checklist tuần 9
- [ ] **F5.4 Test + e2e mở rộng**
  - Unit (vitest + jsdom): `HlsPlayer` — mock `hls.js` module (không chạy WebMedia thật), kiểm trình tự `isSupported`/`loadSource`/`attachMedia`/`destroy`, nhánh native fallback, nhánh `FATAL`; `usePolling` — fake timers, dừng ở trạng thái cuối, đếm lỗi liên tiếp, visibility change
  - Contract test: fixture media URL khớp pattern `/media/{projectId}/{videoId}/...` cho mọi video `COMPLETED` sinh bởi mock auto-process
  - e2e (Playwright, thêm vào bộ 10 kịch bản Week1): **upload → confirm → tự `COMPLETED` (mock auto, chờ có điều kiện chứ không sleep cố) → mở detail → player nhận manifest (chờ `MANIFEST_PARSED` qua data-attribute test, không chờ video frame)**; copy link/nút embed hoạt động ở `http://localhost:5180`; `/embed` không sidebar/header; FAILED branch: `VITE_MOCK_PROCESS=fail` → detail hiện errorCode + gợi ý

**Xong khi (F5):** mở video `COMPLETED` (fixture hoặc vừa upload) → player render, `readyState` tăng, seek/play không lỗi console trên Chromium (debug mode fixture); copy link → dán ra đúng URL master; `/embed?video=...` chạy không chrome; bộ test xanh toàn bộ.

---

## Kết quả — kiểm chứng ngày 2026-10-30

- [x] Unit + contract test: **127/127 pass** (11 file) — gồm mock transitions, media fixture 18 test, polling 7 test, `HlsPlayer` 7 test, clipboard 5 test, helper trạng thái/thời lượng 6 test, public detail 4 test và các regression Week 1. Test StrictMode bắt được lỗi poster bị cleanup xoá (đã sửa).
- [x] `npm run build` (tsc strict + Vite) + `npm run api:check`: **pass**. Vite cảnh báo chunk chính ~900 kB sau minify vì `hls.js`; chưa code-split trong phạm vi tuần này.
- [x] `npm run test:e2e`: **15/15 kịch bản Chromium pass** (29,7 giây), gồm 5 kịch bản Week 2: upload → tự `COMPLETED` + manifest parsed; copy link streaming tuyệt đối; copy đúng chuỗi iframe; embed công khai không chrome/không autoplay; embed thiếu id hoặc video chưa xong hiện "Video is not available yet."; `VITE_MOCK_PROCESS=fail` → `TRANSCODE_FAILED` + gợi ý tự retry.
- [x] Thao tác tay desktop 1440px + mobile 390px: upload → tự `COMPLETED` → phát + copy link/embed; console **0 lỗi**; không tràn ngang ở list/detail/embed; poster hiện đúng; player `readyState=4`, duration 10,4 giây. Tab ẩn 7 giây: **0 request poll**; hiện lại: poll ngay rồi cách nhau ~3.007–3.012 ms (lần tải đầu trong dev có thêm request trùng do StrictMode).
- [ ] **Chưa kiểm chứng — dời tuần 9 (bổ sung từ Week1):** hls.js với output FFmpeg thật (segment thật, duration/bitrate thật), `/media/*` qua nginx `localhost:8000` (stack `full`) rồi CloudFront thật (C4/Lưu), auth cho embed (đang gap contract — xem [`docs/contracts/frontend-week2-gaps.md`](../../contracts/frontend-week2-gaps.md)), errorCode thật từ worker (A4) cho branch `FAILED_*`, hành vi autoplay thật của browser với `/embed`, và clipboard trên LAN IP không HTTPS.

## Việc chuyển tiếp / cộng thêm nếu sớm (không mở P1)

- Thumbnail thật trong danh sách (hi fixture chưa có ảnh — chờ worker; mock có thể sinh JPEG per-video nếu rảnh)
- Màn hình rỗng/lỗi cho `/embed` video lạ/nhà (liên quan gap auth embed)
- Chuẩn bị scaffold trang dashboard/đếm video theo trạng thái — **chỉ khi F4/F5 xong và không lấn A4 worker**

## Rủi ro tuần 2 và cách khống chế

1. **Vũ gánh song song A4 worker (M2 gate) + F4/F5.** Polling + mock state machine là việc có thể làm độc lập và lập trình trước; player là việc tích hợp dễ ngốn giờ (hls.js với fixture byte giả có thể sa lầy debug). Khống chế: thứ tự Buổi 1 → 2 → 3; nếu giữa tuần phải dồn cho M2, **cắt F5.2 embed + mã nhúng trước** (theo thứ tự cắt giảm timeline), giữ nguyên F4 (không cắt được: luồng cốt lõi demo `upload → COMPLETED`).
2. **Mock tự sinh trạng thái có thể "xanh ảo":** UI test pass trên auto-process mock nhưng worker thật chậm/lỗi khác. Khống chế: `VITE_MOCK_PROCESS=freeze` cho test polling thuần; kịch bản "worker không bao giờ nhận job" (PENDING đứng yên) phải được demo được — chính là hành vi thật khi A4 chết.
3. **Fixture HLS không giống output FFmpeg thật** (playlist không có `EXT-X-PROGRAM-DATE-TIME`, không fMP4, segment nhỏ bất thường). Khống chế: ghi nợ kiểm chứng vào mục Kết quả; tuần 9 chạy `upload → COMPLETED → playback` thật (đã có trong kế hoạch hợp nhất Week1); nếu worker A4 xong sớm trong tuần 8, **thử player với output LocalStack thật sớm** thay vì chờ tuần 9.
4. **Gap contract lộ ra (batch status, embed auth):** không được sửa UI để che — mở issue/ghi `docs/contracts/` ngay, nhãn "giả định — chờ Phương xác nhận" cho tạm, chốt ở checklist tuần 9.
5. **Clipboard/autoplay khác nhau giữa browser & môi trường http:** e2e chạy Chromium headless trên `localhost` (secure context — clipboard OK); ghi rõ giới hạn khi demo trên LAN IP không HTTPS.

## Liên kết nhanh

- [work/Week1.md](work/Week1.md) — mock layer, fixture 7 trạng thái, kế hoạch hợp nhất tuần 9 (giữ nguyên, tuần này không đổi)
- [2. Timelines.md](../2.%20Timelines.md) — F4/F5 + thứ tự cắt giảm; [3. Pages & Features.md](../3.%20Pages%20%26%20Features.md) mục 3–6 — hành vi trang; [4. Design System.md](../4.%20Design%20System.md) — token player/embed
- [5. API Integration.md](../5.%20API%20Integration.md) — nơi ghi mọi giả định mới (media fixture, polling interval, embed URL, errorCode mapping)
- [../../contracts/video-state-machine.md](../../contracts/video-state-machine.md) · [../../contracts/s3-key-convention.md](../../contracts/s3-key-convention.md) — nguồn của mock auto-process và `/media/*`
- [../../docs-worker/work/Week2.md](../../docs-worker/work/Week2.md) — phần A4 của cùng Vũ: output HLS thật + Content-Type là thứ F5 sẽ ăn ngay khi hợp nhất
