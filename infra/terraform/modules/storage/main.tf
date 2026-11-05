<<<<<<< HEAD
variable "prefix" { type = string }
variable "bucket_suffix" { type = string }
variable "allowed_origins" {
  type = list(string)
  validation {
    condition     = length(var.allowed_origins) > 0 && !contains(var.allowed_origins, "*")
    error_message = "Provide explicit browser origins; wildcard is not allowed on AWS."
  }
}

resource "aws_s3_bucket" "this" {
  for_each      = toset(["frontend", "input", "output"])
  bucket        = "${var.prefix}-${each.key}-${var.bucket_suffix}"
  force_destroy = false
}

resource "aws_s3_bucket_public_access_block" "this" {
  for_each                = aws_s3_bucket.this
  bucket                  = each.value.id
=======
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
>>>>>>> 6317d81836060cb47572c643c512292ce87cb101
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

<<<<<<< HEAD
resource "aws_s3_bucket_ownership_controls" "this" {
  for_each = aws_s3_bucket.this
  bucket   = each.value.id
  rule { object_ownership = "BucketOwnerEnforced" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  for_each = aws_s3_bucket.this
  bucket   = each.value.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}

resource "aws_s3_bucket_cors_configuration" "input" {
  bucket = aws_s3_bucket.this["input"].id
  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "PUT", "POST", "HEAD"]
    allowed_origins = var.allowed_origins
    expose_headers  = ["ETag"]
    max_age_seconds = 3600
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "input" {
  bucket = aws_s3_bucket.this["input"].id
  rule {
    id     = "expire-input-after-7-days"
    status = "Enabled"
    filter { prefix = "" }
    expiration { days = 7 }
  }
}

output "buckets" {
  value = { for name, bucket in aws_s3_bucket.this : name => { name = bucket.id, arn = bucket.arn } }
=======
# CORS: Cho phép trình duyệt PUT video trực tiếp qua Presigned URL
resource "aws_s3_bucket_cors_configuration" "input" {
  bucket = aws_s3_bucket.input.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "PUT", "POST", "HEAD"]
    allowed_origins = ["*"]
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
>>>>>>> 6317d81836060cb47572c643c512292ce87cb101
}
