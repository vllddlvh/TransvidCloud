# Hướng Dẫn Kiểm Thử Nhanh (Quickstart): F-008 — Video Query & Lifecycle

---

## 1. Chuẩn Bị Môi Trường
Đảm bảo LocalStack đang chạy và Backend Spring Boot đang chạy:
```bash
# Kiểm tra LocalStack
curl -s http://localhost:4566/_localstack/health

# Kiểm tra Backend
curl -s http://localhost:8080/api/actuator/health
```

---

## 2. Kịch Bản Kiểm Thử Bằng cURL

### Bước 1: Đăng ký & Đăng nhập lấy Token
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"dev.query.'$(date +%s)'@transvidcloud.io","password":"Password123@","fullName":"Tester F008"}' \
  | jq -r .accessToken)
echo "JWT Token: $TOKEN"
```

### Bước 2: Tạo Project
```bash
PROJECT_ID=$(curl -s -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Dự án Test Phân Trang F008","description":"Test F008"}' \
  | jq -r .projectId)
echo "Project ID: $PROJECT_ID"
```

### Bước 3: Tạo nhiều video để kiểm tra phân trang
```bash
for i in {1..5}; do
  curl -s -X POST "http://localhost:8080/api/projects/$PROJECT_ID/videos" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"Video $i\",\"filename\":\"video$i.mp4\",\"sizeBytes\":1024,\"contentType\":\"video/mp4\"}" > /dev/null
done
echo "Đã tạo 5 videos!"
```

### Bước 4: Lấy danh sách trang 1 (limit=2)
```bash
RESP_PAGE1=$(curl -s -X GET "http://localhost:8080/api/projects/$PROJECT_ID/videos?limit=2" \
  -H "Authorization: Bearer $TOKEN")
echo "$RESP_PAGE1" | jq .
CURSOR=$(echo "$RESP_PAGE1" | jq -r .nextCursor)
echo "Next Cursor: $CURSOR"
```

### Bước 5: Lấy danh sách trang 2 bằng cursor
```bash
curl -s -X GET "http://localhost:8080/api/projects/$PROJECT_ID/videos?limit=2&cursor=$CURSOR" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### Bước 6: Kiểm tra chi tiết video và Assets
```bash
FIRST_VIDEO_ID=$(echo "$RESP_PAGE1" | jq -r '.items[0].videoId')
curl -s -X GET "http://localhost:8080/api/videos/$FIRST_VIDEO_ID" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### Bước 7: Xóa video (204 No Content)
```bash
curl -i -X DELETE "http://localhost:8080/api/videos/$FIRST_VIDEO_ID" \
  -H "Authorization: Bearer $TOKEN"
```

### Bước 8: Kiểm tra video đã bị xóa (404 Not Found)
```bash
curl -i -X GET "http://localhost:8080/api/videos/$FIRST_VIDEO_ID" \
  -H "Authorization: Bearer $TOKEN"
```
