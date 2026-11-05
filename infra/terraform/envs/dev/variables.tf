variable "aws_region" {
  type        = string
  default     = "ap-southeast-1"
  description = "AWS Region triển khai"
}

variable "environment" {
  type        = string
  default     = "dev"
  description = "Tên môi trường triển khai"
}

variable "account_id" {
  type        = string
  default     = "060245183757"
  description = "AWS Account ID triển khai"
}
