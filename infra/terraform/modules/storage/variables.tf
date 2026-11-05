variable "environment" {
  type        = string
  description = "Tên môi trường (dev hoặc demo)"
}

variable "account_id" {
  type        = string
  description = "AWS Account ID để đặt tên bucket duy nhất toàn cầu"
}
