# infra/terraform/envs/

- `dev/`: môi trường phát triển, dùng hằng ngày.
- `demo/`: môi trường trình diễn; dựng trước buổi demo, **hủy bằng `terraform destroy` sau khi dùng** để tiết kiệm chi phí.

Hai môi trường dùng chung mã trong `../modules`, chỉ khác file biến (`terraform.tfvars`, không commit).
