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
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

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
}
