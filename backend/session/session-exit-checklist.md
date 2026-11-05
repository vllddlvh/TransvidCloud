# Session Exit / Clean Handoff Checklist

- [ ] Cập nhật `tasks.md` theo công việc thực tế; không đánh dấu test chưa chạy là completed.
- [ ] Cập nhật `backlog/features.md` nếu feature state thay đổi.
- [ ] Cập nhật/thay thế `PROGRESS.md` bằng snapshot mới nhất.
- [ ] Ghi blocker, quyết định cần con người và next action.
- [ ] Ghi command verification và kết quả thực tế (`PASS`, `FAIL`, `NOT RUN`, `BLOCKED`).
- [ ] Kiểm tra `git diff` và `git status`; xác định file ngoài scope.
- [ ] Xóa file tạm/debug do session hiện tại tạo ra nếu an toàn; không xóa thay đổi của người dùng.
- [ ] Đảm bảo startup path còn được tài liệu hóa; nếu không kiểm tra được, ghi rõ.
- [ ] Không tuyên bố clean handoff nếu build/test bắt buộc vẫn fail hoặc chưa chạy.
