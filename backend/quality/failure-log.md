# Harness / Engineering Failure Log

Chỉ ghi failure có giá trị học tập hoặc cần ngăn tái diễn; không ghi mọi lỗi nhỏ đã được sửa ngay.

| Date | Task / Feature | Symptom | Root-cause layer | Root cause | Fix / prevention | Automated check / Evidence |
|---|---|---|---|---|---|---|
| `TODO(SETUP)` | `TODO(SETUP)` | `TODO(SETUP)` | `Instruction / Tool / Environment / State / Feedback / Spec` | `TODO(SETUP)` | `TODO(SETUP)` | `TODO(SETUP)` |

## Diagnostic Loop

1. Quan sát lỗi và giữ lại command/output an toàn.
2. Xác định lớp có khả năng gây lỗi: specification, context/instructions, tools, environment, state hoặc feedback/verification.
3. Sửa nguyên nhân gốc, không chỉ lặp lại prompt.
4. Chạy lại kiểm tra liên quan.
5. Nếu có nguy cơ tái diễn, promote bài học thành rule, test, lint hoặc script.
