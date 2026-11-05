variable "environment" {
  type        = string
  description = "Tên môi trường (dev hoặc demo)"
}

variable "vpc_cidr" {
  type        = string
  default     = "10.0.0.0/16"
  description = "Dải IP của VPC"
}

variable "public_subnet_cidrs" {
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
  description = "Dải IP cho 2 Public Subnet"
}

variable "availability_zones" {
  type        = list(string)
  default     = ["ap-southeast-1a", "ap-southeast-1b"]
  description = "2 Availability Zones để đạt chuẩn High Availability"
}
