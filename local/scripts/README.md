# local/scripts/

Script hỗ trợ kiểm thử local. Gọi qua `make`, ít khi chạy tay.

| Script | Mục đích |
|---|---|
| `smoke-test.sh` | Kiểm tra LocalStack đã có đủ bucket, queue, bảng |
| `seed.sh` | Tạo dữ liệu mẫu (user, project) cho demo/dev |
| `chaos-kill-worker.sh` | Gửi job, kill worker giữa chừng, kiểm tra retry và DLQ |

Lưu ý Git Bash: lệnh `docker exec` có đường dẫn kiểu `/abc` cần tiền tố `MSYS_NO_PATHCONV=1`.
