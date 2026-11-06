variable "environment" {
  type        = string
  description = "Tên môi trường (dev hoặc demo)"
}

variable "account_id" {
  type        = string
  description = "AWS Account ID để đặt tên bucket duy nhất toàn cầu"
}

variable "allowed_origins" {
  type        = list(string)
  default     = ["http://localhost:8000", "https://vbeeaistt.com", "https://*.cloudfront.net"]
  description = "Origin trình duyệt được phép upload trực tiếp lên S3"
}
