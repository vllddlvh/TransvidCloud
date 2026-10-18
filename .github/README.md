# .github/

- `workflows/ci.yml`: chạy khi mở PR và khi push `main`; kiểm tra build/test từng module **nếu module đã có code**.
- `workflows/deploy.yml`: triển khai lên AWS (chạy tay `workflow_dispatch`, chọn môi trường). Cần hoàn tất epic C2/C6 trước.
- `CODEOWNERS`: yêu cầu người phụ trách duyệt PR theo thư mục.

Thay `@github-user-*` trong `CODEOWNERS` bằng tên GitHub thật của từng người.
