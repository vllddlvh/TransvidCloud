# Week 3 — Hợp nhất backend/worker thật + phát hành qua CloudFront + đóng băng (kết thúc chế độ độc lập)

Tuần làm việc thứ ba của frontend, tương ứng **Sprint 3 (phần còn lại) + Sprint 4** trong [2. Timelines.md](../2.%20Timelines.md) — tức **tuần 8–9 học kỳ**, đè lên mốc **M3** (đầu tuần 9) và **M4** (cuối tuần 9).

> **Chế độ độc lập kết thúc.** F1–F5 đã hoàn thành trên mock ([work/Week1.md](work/Week1.md), [work/Week2.md](work/Week2.md) kiểm chứng 28–30/10: 127 unit + 15 e2e pass). Tuần này thực hiện **kế hoạch hợp nhất 5 bước** đã ghi cuối Week1: đổi `VITE_API_MODE=real` mà không sửa UI, đối chiếu từng "giả định" trong [5. API Integration.md](../5.%20API%20Integration.md), chạy stack thật, e2e thật, đóng băng. Nếu backend (Phương, A2/A3) có endpoint thật sớm hơn trong tuần 8 thì **hợp nhất sớm từng phần** (auth trước, upload sau) — theo rủi ro 2 của Week1; nếu chưa có, làm Buổi 1 (gap contract + nền) và giữ mock cho demo, không chặn.

Phạm vi tuần này: **hợp nhất luồng chính thật · phát hành build cho hosting (C4) · hỗ trợ A8 e2e + R1 diễn tập · đóng băng M4**. Không có task F mới — A6 đã dùng hết baseline 2,0 pd ở Week1–2; phần hợp nhất tính trong tổng tải của Vũ (A6 + A4/A5 + A8/R1 hỗ trợ), không có quỹ pd riêng.

- **Chủ trì:** Vũ. **Lưu ý cân đối:** worker A4/A5 vẫn là **ưu tiên tiên 1** cho gate M2 (cuối tuần 8) và M3 — frontend tuần này cắt được gần hết nếu M2/M3 cần; việc duy nhất không cắt là Buổi 1 (mở issue gap contract, vì nó chặn cả Phương).
- **Kế thừa (đã kiểm chứng):** khung app + mock layer MSW `mock|real` cùng interface; fixture 7 trạng thái + media HLS; upload presigned 3 bước + polling 3 s + `HlsPlayer` + embed + clipboard; e2e 15/15 Chromium.
- **Nguyên tắc giữ nguyên:** đường tương đối `/api/...`, `/media/...`; token design system; TypeScript chặt; **mock và real cùng interface — mọi lệch khi đổi `VITE_API_MODE=real` là mock drift, sửa ở contract/types/transport, không sửa logic UI để che**.

## Hiện trạng đầu tuần (lập kế hoạch 31/10/2026)

| Hạng mục | Trạng thái lúc lập kế hoạch | Ảnh hưởng tuần này |
|---|---|---|
| `backend/` | Chỉ có README, **chưa có code** (A2/A3 chưa triển khai) | Hợp nhất sớm chưa khả thi ngay; Buổi 1 không phụ thuộc backend, Buổi 2 mở điều kiện "khi Phương có endpoint" |
| `worker/` | Week2 đang chạy cho M2 — pipeline local thật (`make up` → `COMPLETED`) là mục tiêu cuối tuần 8 | `/media/*` output thật + errorCode thật có thể có sớm cho buổi 2; thử player với output LocalStack thật **không đợi tuần 9** (rủi ro 3 của Week2) |
| `frontend/` | F1–F5 xong trên mock; nợ kiểm chứng: nginx `localhost:8000`, presigned/CORS thật, HLS output thật, embed auth, errorCode thật, autoplay/clipboard ngoài localhost | Toàn bộ nợ này là chính phạm vi tuần này |
| Contract | `openapi.yaml` + 4 file vẫn **DRAFT**; [`frontend-week2-gaps.md`](../../contracts/frontend-week2-gaps.md) có 4 gap chưa kết luận | Chốt với Phương ngay đầu tuần — đầu vào cho mọi việc sau |

## Mục tiêu cuối tuần

Người dùng mở **stack thật** (`make up`): đăng nhập bằng backend thật (JWT thật), tạo project, upload mp4 qua presigned URL thật lên S3/LocalStack (progress thật, không mang bearer), `confirm` → worker thật xử lý → danh sách tự chuyển `PENDING → PROCESSING → COMPLETED` → **`HlsPlayer` phát master playlist do worker sinh ra** qua `http://localhost:8000/media/...`. Nhánh `FAILED_*` hiện `errorCode` thật của worker. Mọi dòng "giả định — chờ Phương xác nhận" trong [5. API Integration.md](../5.%20API%20Integration.md) và mọi mục "Chưa kiểm chứng — dời tuần 9" của Week1/Week2 **có kết luận hoặc có lý do rõ ràng còn mở**; build production bàn giao Lưu deploy CloudFront (C4); cuối tuần **đóng băng** — tuần 10 chỉ sửa lỗi chặn nộp.

---

## Buổi 1 — Chốt gap contract + nền cho hợp nhất (0,5 pd, không phụ thuộc backend)

- [ ] **W3.1 Chốt 4 gap của [`frontend-week2-gaps.md`](../../contracts/frontend-week2-gaps.md) với Phương** — việc đầu tiên của tuần, chặn cả hai bên: **Trạng thái 31/10: chưa có phản hồi; bằng chứng đã ghi trong gap file và mục 9 của `5. API Integration.md`, không tick.**
  - **Embed công khai** (gap chặn demo thật): chốt một trong hai hướng — endpoint/presigned embed URL công khai chỉ cho `COMPLETED`, hoặc embed dùng token riêng; ghi quyền, thời hạn, thu hồi vào `openapi.yaml`/contract mới; cập nhật mock theo kết luận
  - **Polling cả danh sách**: chốt có làm batch-status không (nếu không, ghi rõ "chấp nhận re-list cho quy mô demo" vào contract; nếu có, sinh types lại `npm run api:generate`)
  - **errorCode worker thật**: đối chiếu bảng map UI (`DURATION_EXCEEDED`, `HEIGHT_EXCEEDED`, `CODEC_UNSUPPORTED`, `NOT_A_VIDEO`, `TRANSCODE_FAILED`, `STORAGE_WRITE_FAILED`) với tập mã A4 chốt — sai ở đâu sửa mapping, không sửa logic
  - **Serve `/media/*`**: xác nhận với Lưu/Nguyên cách nginx local + CloudFront thật phục vụ `/media/**` (cache m3u8 vs segment, Content-Type) khớp giả định "UI resolve `assets.hls` là URL tương đối"
  - Kết quả mỗi gap ghi kết luận vào chính file gap + checklist mục 7 của `5. API Integration.md`; hết tuần mọi nhãn DRAFT liên quan frontend phải được Phương/App duyệt hoặc có lý do còn mở
- [x] **W3.2 Trả nợ kiểm chứng nginx `localhost:8000` (nợ Week1)**: bật stack `full`, chạy frontend mock qua nginx — `npm run dev` (5173 proxy) và **thêm profile chạy build tĩnh** (dùng cho prod-style); kiểm service worker MSW đăng ký qua đường proxy, media fixture `/media/*` phát qua 8000, e2e Playwright trỏ `BASE_URL=http://localhost:8000`. Nếu sai path service worker/asset base → sửa cấu hình Vite/nginx, ghi lại
- [x] **W3.3 Real-mode readiness**: xác nhận đổi `VITE_API_MODE=real` tắt sạch mock — unregister service worker cũ (nợ từ mục 6 `5. API Integration.md`), không fixture leak; e2e có profile "real, không backend → báo lỗi mạng đúng nghĩa" (đã có manual, đưa vào e2e nếu rẻ); tách `VITE_MOCK_*` chỉ còn dùng ở dev/demo
- [ ] **W3.4 Hợp nhất sớm từng phần nếu backend có endpoint thật** (hiện chưa có — làm ngay khi Phương bật được `/auth/login`): đổi mode real chỉ cho nhóm auth, chạy login/projects thật với stack còn mock phần upload (nếu mode chọn được theo nhóm API thì tách; nếu không, theo cặp UI-real/mock khi test tay), mọi lệch ghi mock drift theo quy trình Week1 bước 2. **Trạng thái 31/10: chưa làm — `backend/` chưa có code.**

**Xong khi:** 4 gap có kết luận ghi trong repo; frontend chạy qua `localhost:8000` cả dev lẫn build tĩnh, e2e pass trên URL nginx; `VITE_API_MODE=real` sạch mock; nếu có backend thật thì login/projects thật hoạt động không sửa UI.

## Buổi 2 — Hợp nhất luồng chính end-to-end thật (0,5 pd, điều kiện: backend + worker chạy được)

Làm theo **đúng thứ tự bảng 5 bước** cuối Week1. Mỗi bước chỉ sang bước sau khi bước trước xanh; mọi lệch xử lý ở contract/types/transport.

- [ ] **W3.5 Upload thật (bước 1–3 của bảng Week1)**: đổi `VITE_API_MODE=real` giữ nguyên code UI → `POST videos` thật trả presigned URL thật → **PUT lên S3/LocalStack thật**: rà CORS presigned PUT (`S3_PRESIGN_ENDPOINT`, bucket input — bẫy đã ghi trong [LOCAL_AND_CICD Phần 3](../../introduction/2.%20LOCAL_AND_CICD.md)); lỗi S3 thật là **XML/empty, không phải JSON envelope như mock** → mở rộng `ApiError`/transport xử lý dạng này mà không in raw HTML/XML ra UI; PUT không mang bearer (kiểm bằng network tab/e2e assert); progress XHR thật với file 10 MB thật; file 201 MB bị API từ chối 413 thật + client chặn; `confirm` qua HeadObject thật (409 đúng khi PUT chưa xong). **Trạng thái 31/10: chưa có backend/presigned API để PUT thật; xử lý S3 XML/empty đã xong ở transport và có unit test.**
- [x] **W3.6 Trạng thái + player thật (bước 4)**: worker thật xử lý (output A4 của chính Vũ — không chờ ai ngoài stack): polling 3 s với `updatedAt` thật, `PROCESSING` kéo dài thật (không còn auto 5–10 s của mock); `HlsPlayer` phát master playlist **do FFmpeg sinh**: kiểm bitrate/keyframe/Content-Type thật, hai rendition 360p/720p, thumbnail thật trong danh sách; nhánh chết worker giữa chừng (chaos script Lưu) → `PROCESSING`/`PENDING` đứng yên đúng hành vi "trạng thái không xác định" sau 3 lỗi; nhánh fail validation thật (video 301 s / 1440p) → `FAILED_VALIDATION` + `errorCode` thật khớp mapping đã chốt ở W3.1. **Trạng thái 31/10: phần local worker output đã kiểm (real-media e2e, Content-Type, 360p/720p); polling API thật, nhánh fail thật và chaos còn chờ backend/worker stack.**
- [ ] **W3.7 e2e thật + embed theo contract mới**: bộ e2e Week2 chạy lại trên stack thật (`upload → COMPLETED → phát`, có điều kiện chờ theo trạng thái, không sleep cố định); kịch bản quá giới hạn; nếu W3.1 chốt embed công khai → `/embed?video=` thật (không bearer theo contract mới), không thì ghi rõ demo-only. **Trạng thái 31/10: chưa làm — backend chưa có code và embed chưa được chốt; bộ e2e mock vẫn giữ.**
- [ ] **Ghi kết quả đối chiếu**: cập nhật checklist mục 7 `5. API Integration.md` từng dòng một (duyệt/đã kiểm chứng/lý do còn mở); cập nhật trạng thái các mục "Chưa kiểm chứng" cuối Week1/Week2

**Xong khi:** e2e thật pass trên stack local đầy đủ; không còn sửa UI nào để chạy được với thật (mọi sửa là contract/types/transport); console sạch khi phát output thật; các mục dời tuần 9 của Week1/Week2 trả hết.

## Buổi 3 — Phát hành qua CloudFront + hỗ trợ A8/R1 + đóng băng (0,5 pd)

- [x] **W3.8 Build production cho hosting (C4 — Lưu deploy lên bucket frontend + CloudFront)**: build tĩnh hoàn chỉnh — `base` đúng cho CloudFront root, không MSW/mock trong prod bundle, `VITE_API_MODE` cứng `real`; **code-split `hls.js`** (chunk chính ~900 kB sau minify — cảnh báo Week2): lazy route chứa `HlsPlayer` bằng `React.lazy`/dynamic import, kiểm bundle sau khi tách; xác minh `vite preview` + nginx tĩnh chạy được login→upload→phát (trên stack thật); bàn giao cho Lưu kèm ghi chú env/cấu hình cần (`C4 hosting React 0,5 pd` của Lưu ăn được ngay). **Trạng thái 31/10: build real-only + bundle checker + lazy split + nginx static đã kiểm trên mock/worker output; login→upload qua API thật và deploy CloudFront vẫn chờ Lưu/backend.**
- [x] **W3.9 Hỗ trợ A8 (Lưu chủ trì)**: đảm bảo e2e frontend chạy được với `BASE_URL` **local và cloud** (CloudFront domain `*.cloudfront.net`) — không hard-code origin (đã là nguyên tắc, kiểm lại trên cloud thật); ca upload quá giới hạn đã có; phối hợp đo luồng `upload → COMPLETED → phát` trên cloud khi Lưu cần kịch bản người dùng cuối. **Trạng thái 31/10: `E2E_BASE_URL` + auxiliary-server gating đã kiểm local; chưa có CloudFront để chạy cloud.**
- [x] **W3.10 R1 diễn tập + đóng băng M4**: chạy demo kịch bản đầy đủ **ít nhất 1 lần trong tuần** (2 lần bắt buộc trước nộp — lần còn lại đầu tuần 10): cả stack thật và **phương án dự phòng mock** (mock vẫn giữ đúng giá trị "backend không trực tuyến"); đóng băng phiên bản: tag/ghi commit, chỉ nhận sửa lỗi chặn nộp/demo sau điểm này; dọn fixture không dùng nhưng **giữ mock cho demo** (bước 5 bảng Week1). **Trạng thái 31/10: đã diễn tập mock qua nginx `:8000` + các nhánh lỗi/fallback; full-stack thật chưa chạy do backend chưa có code; freeze candidate ghi ở mục Kết quả, không tag/commit.**
- [ ] *(Cộng thêm nếu sớm — không mở P1)*: scaffold trang dashboard/đếm video theo trạng thái (đã đề xuất Week2, chỉ khi W3.1–W3.8 xong và không lấn A4); empty-state `/embed` video lạ; xoá nốt cảnh báo bundle nếu còn

**Xong khi:** Lưu nhận được bản build + hướng dẫn chạy CloudFront và deploy được (M3: một địa chỉ HTTPS có web + API + HLS); e2e chạy được theo `BASE_URL`; diễn tập demo xong ≥1 lần kèm ghi chú sự cố; điểm đóng băng được ghi nhận trong repo (commit/tag + ngày).

---

## Kết quả — kiểm chứng (2026-10-31)

HEAD ghi nhận: `b5cf8eaa705847f73f11becda0d787f7e26412e6` (nhánh `dev`). Không commit/tag trong tuần này theo yêu cầu; working tree chứa thay đổi frontend/docs/script.

### Kiểm thử tự động

- `npm test`: **136/136 pass** (13 file), gồm runtime mode 3, service worker 3, transport S3 XML 3 mới và toàn bộ regression Week 1–2.
- `npm run api:check`: **pass**, không drift generated types.
- `npm run build`: **pass**; `check-production-bundle.mjs` xác nhận `dist/` không có `mockServiceWorker.js`, entry `mocks/`, `setupWorker` hay fixture; main **301.963 B**, `HlsPlayer` lazy **597.001 B**.
- `npm run build:mock`: **pass**, `dist-mock/` giữ `mockServiceWorker.js` và mock chunk cho demo.
- `docker compose --profile static config`: **pass** (dịch vụ `edge-static`); chưa `up` được trên máy này do Docker Desktop không mount repo — dùng script host thay thế.
- Guard: build production với `VITE_API_MODE=mock` **fail có chủ đích** (production bắt buộc real).
- Playwright managed (4 server 5181–5184): **15 pass / 2 skip** (hosting và real-media cần gate riêng).
- Playwright static nginx `:8000` (`dist-mock`, `E2E_HOSTED=1`): **12 pass / 5 skip**.
- Playwright dev-proxy nginx `:8000`: **12 pass / 5 skip** với `E2E_HOSTED=1` (11 pass / 6 skip nếu không bật hosting gate).
- Playwright real-media: **1 pass (563 ms) trên nginx static `dist-mock`** và **1 pass (337 ms) trên nginx prod `dist/`** với output worker thật.

### Diễn tập demo mock qua nginx (R1)

- 2026-10-31 21:12 +07: `npm run build:mock` → `bash scripts/serve-nginx.sh static` → `E2E_BASE_URL=http://localhost:8000 E2E_HOSTED=1 npx playwright test`: **12 pass / 5 skip** (25,8 s) tại `http://localhost:8000`.
- Kiểm tra thêm player trên `dist-mock`: đăng nhập demo, mở `/videos/demo-completed`, manifest parsed; **0 console error / 0 page error**.
- Fallback story: `npx playwright test` (managed, 21:12 +07): **15 pass / 2 skip** (33,0 s), gồm real mode không backend báo lỗi đúng, PUT lỗi retry, confirm lỗi retry, transcode fail + guidance và embed công khai. Đây là phương án demo dự phòng khi backend không trực tuyến.
- Chưa diễn tập được kịch bản full stack thật (`upload → COMPLETED → phát` qua API) vì `backend/` chưa có code; phần worker output được diễn tập riêng bằng real-media e2e.

### Bằng chứng worker HLS

`bash worker/scripts/local-media.sh`: worker `COMPLETED` trong **2.142 ms**, `bytesIn=1520913`, `bytesOut=7106269`, 8 object. Qua nginx `:8000`, hls.js 1.7.4 parse master và nhận level 360/720. `Content-Type` kiểm bằng curl/e2e:

| Object | Content-Type |
|---|---|
| `hls/master.m3u8` | `application/vnd.apple.mpegurl` |
| `hls/360p/index.m3u8` và `hls/720p/index.m3u8` | `application/vnd.apple.mpegurl` |
| segment `.ts` | `video/mp2t` |
| `thumb/default.jpg` | `image/jpeg` |

### Mục còn mở / chặn

- **W3.4, W3.5, W3.7 — chưa thể hợp nhất thật:** `backend/` chỉ có README, chưa có code; không có `/auth/login`, presigned API hay `confirm` để chạy login/upload/status/e2e thật. Xử lý S3 XML đã xong ở transport nhưng chưa có backend để PUT thật; e2e thật và embed thật vẫn chờ.
- **W3.1 — chưa có phê duyệt:** 4 gap đã có bằng chứng (embed mở, batch polling mở, media verified local, errorCode worker-side confirmed) nhưng Phương/Lưu/Nguyên chưa chốt; checklist mục 7 của [`5. API Integration.md`](../5.%20API%20Integration.md) giữ nguyên chưa tick.
- **CloudFront (C4/Lưu):** chưa có domain/HTTPS/OAC/cache TTL; chưa kiểm Safari native HLS, clipboard ngoài localhost.
- **Backend + worker thật:** chưa chạy polling `PENDING → PROCESSING → COMPLETED` qua API thật, chưa có envelope `errorCode`, chưa kiểm CORS presigned thật.

### Đóng băng

- Ứng viên đóng băng: `b5cf8eaa705847f73f11becda0d787f7e26412e6` (2026-10-31). **Không tạo tag/commit** theo yêu cầu người dùng; working tree là bản bàn giao hiện tại.
- Mock + fixture được giữ nguyên cho demo dự phòng; sau điểm này chỉ sửa lỗi chặn nộp/demo tới M4.
- Việc chuyển tuần 10: chờ backend thật để trả W3.4/W3.5/W3.7, chờ Phương chốt W3.1, chờ Lưu/Nguyên deploy CloudFront và kiểm cache/Safari. Không mở task F mới.

## Rủi ro tuần 3 và cách khống chế

1. **Backend chưa có code (A2/A3) — hợp nhất tuột lịch tuần 9.** Khống chế: Buổi 1 không phụ thuộc backend (gap contract + nginx + real-mode readiness vẫn làm được); hợp nhất sớm từng phần ngay khi Phương bật endpoint; nếu đến giữa tuần 9 backend vẫn thiếu, e2e/M4 chạy bằng worker + API tối thiểu Phương cam kết, phần thiếu dùng mock và **ghi rõ trong báo cáo** — không sửa UI để che.
2. **Vũ gánh song song A4/A5 (M2, ưu tiên tiên 1).** Thứ tự cắt tuần này (cắt từ trên xuống): W3.4/W3.9 cộng thêm → code-split W3.8 (dời sau đóng băng nếu không chặn nộp) → W3.10 diễn tập mock → **không cắt**: W3.1 (chặn Phương), W3.5–W3.6 (luồng cốt lõi demo `upload → COMPLETED → phát`), W3.8 phần build tĩnh tối thiểu cho C4.
3. **S3/CORS/XML khác mock** (SignatureDoesNotMatch, presigned endpoint, lỗi XML không JSON) — phần không mock được, đã là nợ kỹ thuật dự kiến: xử lý trong transport/`ApiError`, đối chiếu bảng sự cố [LOCAL_AND_CICD Phần 3](../../introduction/2.%20LOCAL_AND_CICD.md); không đổi giả định contract vì lỗi cấu hình hạ tầng.
4. **CloudFront phục vụ `/media/*` khác local nginx** (cache m3u8 gây player stale, Content-Type hành vi OAC): chốt với Nguyên/Lưu ở W3.1; nếu có vấn đề cache thì yêu cầu TTL ngắn cho `.m3u8` — quyết định thuộc Cloud (C4), frontend không tự workaround bằng URL ngẫu nhiên.
5. **Clipboard/autoplay thật trên domain cloud**: clipboard cần secure context (HTTPS CloudFront ổn, LAN IP không); autoplay embed vẫn tắt (giả định Week2 giữ) — chỉ bật nếu diễn tập yêu cầu và browser cho phép; kiểm Safari native HLS lần đầu trên cloud (không có máy Mac trong e2e Chromium).
6. **Đóng băng trùng benchmark/báo cáo (M4).** Đóng băng nửa sau tuần 9 như kế hoạch; sau điểm đó chỉ sửa lỗi chặn — mọi ý tưởng mới ghi lại cho báo cáo "hướng phát triển", không implement.

## Liên kết nhanh

- [work/Week1.md](work/Week1.md) — **bảng kế hoạch hợp nhất 5 bước** (tham chiếu chính của tuần này) + rủi ro mock drift
- [work/Week2.md](work/Week2.md) — danh mục "Chưa kiểm chứng — dời tuần 9" cần trả; mock state machine/media fixture giữ cho demo
- [5. API Integration.md](../5.%20API%20Integration.md) mục 7 — checklist đối chiếu từng giả định; mục 8 — giả định Week2
- [`../../contracts/frontend-week2-gaps.md`](../../contracts/frontend-week2-gaps.md) — 4 gap chốt ở W3.1
- [2. Timelines.md](../2.%20Timelines.md) mục 2 — yêu cầu M3/M4/M5 với frontend; mục 4 — thứ tự cắt giảm chung
- [../../introduction/2.%20LOCAL_AND_CICD.md](../../introduction/2.%20LOCAL_AND_CICD.md) Phần 3 — bẫy CORS presigned; [../../docs-worker/work/Week2.md](../../docs-worker/work/Week2.md) — output HLS thật + errorCode là đầu vào W3.6
