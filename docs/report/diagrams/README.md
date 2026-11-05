# diagrams/ — Hình kiến trúc cho báo cáo

Nguồn sự thật của các hình là file `.dot` (Graphviz). File `.png` là bản render —
sửa `.dot` rồi render lại, không chỉnh PNG trực tiếp.

```bash
brew install graphviz   # lần đầu
dot -Tpng -Gdpi=160 <tên-file>.dot -o <tên-file>.png
```

| Hình | File nguồn | Nội dung | Vai trò trong Progress 2 |
|---|---|---|---|
| 1.1 | `saas-paas-architecture.dot` | Hai lớp tiếp cận (SaaS Web / Developer API) trên một backend + một lõi xử lý cloud dùng chung | Mục 1.1 |
| 1.2 | `saas-upload-flow.dot` | Luồng upload presigned: video PUT thẳng S3, không qua API; confirm → SQS → worker → HLS | Mục 1.2.2 |
| 1.3 | `developer-integration-flow.dot` | Vòng đời tích hợp Developer API của ứng dụng ngoài (E-learning) | Mục 1.3.3 |
| 1.4 | `functional-architecture.dot` | Kiến trúc chức năng: module A–H xếp theo 5 tầng (trình bày, ứng dụng, xử lý, dữ liệu, ngang hệ thống) | Mục 1.4 |

Căn cứ nội dung: `docs/contracts/openapi.yaml`, `upload-limits.md`, `video-state-machine.md`,
`docs/docs-common/1. Overall system.md`, `docs/docs-frontend/3. Pages & Features.md`,
phân rã module A–H ở `docs/introduction/3. SRS highlevel.md` mục 26 (render ngày 27/10/2026).
Khi contract đổi, sửa `.dot` cho khớp rồi render lại.
