output "vpc_id" {
  value       = aws_vpc.main.id
  description = "ID của VPC"
}

output "public_subnet_ids" {
  value       = aws_subnet.public[*].id
  description = "Danh sách IDs của các Public Subnet"
}
