# local/nginx/

Giả lập CloudFront khi chạy local: một địa chỉ `http://localhost:8000` với 3 đường dẫn giống production.

| Path | Đích | Tương ứng cloud |
|---|---|---|
| `/` | Vite dev server trên máy (`localhost:5173`) hoặc frontend build tĩnh | CloudFront -> S3 frontend |
| `/api/*` | container `backend:8080` | CloudFront -> EC2 API |
| `/media/*` | bucket `tvc-local-output` trên LocalStack | CloudFront -> S3 output (OAC) |

Frontend luôn gọi đường dẫn tương đối (`/api/...`, `/media/...`) để code giống hệt local và cloud.

## Chạy frontend qua :8000

**Cách 1 — script trên máy (không cần Docker cho nginx), dùng khi Docker không mount được repo:**

```bash
cd frontend
bash scripts/serve-nginx.sh dev      # proxy Vite 5173 (chạy npm run dev trước)
bash scripts/serve-nginx.sh static   # phục vụ dist-mock (chạy npm run build:mock trước)
bash scripts/serve-nginx.sh prod     # phục vụ dist (chạy npm run build trước)
```

Script sinh `frontend/.nginx/nginx.conf` + `mime.types`, listen `8000`, proxy `/api/*` → `127.0.0.1:8080` và `/media/*` → LocalStack `127.0.0.1:4566` (rewrite `/tvc-local-output/`), và chạy nginx foreground. Log nằm trong `frontend/.nginx/logs/`.

**Cách 2 — Compose profile `static`:**

```bash
cd frontend && npm run build:mock
cd .. && docker compose --profile static up edge-static
```

`edge-static` phục vụ `frontend/dist-mock` qua `local/nginx/static.conf`, proxy `/api/` → `backend:8080` và `/media/` → `localstack:4566`. Profile `static` chỉ dùng cho demo mock tĩnh; cloud deploy thật do Lưu thực hiện.

> Trên máy phát triển hiện tại, Docker Desktop không mount được đường dẫn repo nên `edge-static` không khởi động đúng; dùng script host ở cách 1 làm đường kiểm chứng local.
