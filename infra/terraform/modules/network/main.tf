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
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

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
}
