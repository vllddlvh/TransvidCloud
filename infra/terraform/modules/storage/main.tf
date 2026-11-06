locals {
  input_bucket_name    = "tvc-${var.environment}-input-${var.account_id}"
  output_bucket_name   = "tvc-${var.environment}-output-${var.account_id}"
  frontend_bucket_name = "tvc-${var.environment}-frontend-${var.account_id}"
}

# ----------------- 1. INPUT BUCKET -----------------
resource "aws_s3_bucket" "input" {
  bucket = local.input_bucket_name
}

# Chặn 100% Public Access
resource "aws_s3_bucket_public_access_block" "input" {
  bucket                  = aws_s3_bucket.input.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# CORS: Cho phép trình duyệt PUT video trực tiếp qua Presigned URL
resource "aws_s3_bucket_cors_configuration" "input" {
  bucket = aws_s3_bucket.input.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "PUT", "POST", "HEAD"]
    allowed_origins = var.allowed_origins
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

# Lifecycle: TỰ ĐỘNG XÓA video gốc sau 7 ngày (FinOps bảo vệ dung lượng)
resource "aws_s3_bucket_lifecycle_configuration" "input" {
  bucket = aws_s3_bucket.input.id

  rule {
    id     = "delete-raw-video-after-7-days"
    status = "Enabled"

    filter {}

    expiration {
      days = 7
    }
  }
}

# ----------------- 2. OUTPUT BUCKET (Private) -----------------
resource "aws_s3_bucket" "output" {
  bucket = local.output_bucket_name
}

resource "aws_s3_bucket_public_access_block" "output" {
  bucket                  = aws_s3_bucket.output.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ----------------- 3. FRONTEND BUCKET (Private) -----------------
resource "aws_s3_bucket" "frontend" {
  bucket = local.frontend_bucket_name
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
