<<<<<<< HEAD
variable "prefix" { type = string }
variable "region" { type = string }
variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

data "aws_availability_zones" "available" { state = "available" }

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = "${var.prefix}-vpc" }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${var.prefix}-igw" }
}

resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true
  tags                    = { Name = "${var.prefix}-public-${count.index + 1}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }
  tags = { Name = "${var.prefix}-public" }
}

resource "aws_route_table_association" "public" {
  count          = 2
=======
# 1. VPC chính
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "tvc-${var.environment}-vpc"
  }
}

# 2. Internet Gateway (kết nối ra ngoài cho EC2)
resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "tvc-${var.environment}-igw"
  }
}

# 3. 2 Public Subnet trên 2 AZs (Đạt chuẩn High Availability)
resource "aws_subnet" "public" {
  count                   = length(var.public_subnet_cidrs)
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "tvc-${var.environment}-public-subnet-${count.index + 1}"
  }
}

# 4. Route Table trỏ ra Internet Gateway
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }

  tags = {
    Name = "tvc-${var.environment}-public-rt"
  }
}

# 5. Gắn Route Table vào 2 Subnet
resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
>>>>>>> 6317d81836060cb47572c643c512292ce87cb101
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

<<<<<<< HEAD
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id]
  tags              = { Name = "${var.prefix}-s3" }
}

resource "aws_security_group" "runtime" {
  for_each    = toset(["api", "worker"])
  name        = "${var.prefix}-${each.key}"
  description = "No inbound; outbound for AWS APIs, image pulls and Tunnel"
  vpc_id      = aws_vpc.this.id
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Name = "${var.prefix}-${each.key}" }
}

output "vpc_id" { value = aws_vpc.this.id }
output "public_subnet_ids" { value = aws_subnet.public[*].id }
output "security_group_ids" {
  value = { for name, group in aws_security_group.runtime : name => group.id }
=======
# 6. S3 Gateway Endpoint (MIỄN PHÍ 100% - ĐIỂM SÁNG FINOPS)
# Giúp EC2 API và Worker đẩy/tải video S3 qua mạng nội bộ AWS, không tốn phí NAT
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.ap-southeast-1.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id]

  tags = {
    Name = "tvc-${var.environment}-s3-endpoint"
  }
>>>>>>> 6317d81836060cb47572c643c512292ce87cb101
}
