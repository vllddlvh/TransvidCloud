variable "environment" { type = string }
variable "frontend_bucket" { type = string }
variable "output_bucket" { type = string }
variable "api_hostname" { type = string }
variable "certificate_arn" { type = string }
locals { prefix = "tvc-${var.environment}" }
data "aws_cloudfront_cache_policy" "optimized" { name = "Managed-CachingOptimized" }
data "aws_cloudfront_cache_policy" "disabled" { name = "Managed-CachingDisabled" }
data "aws_cloudfront_origin_request_policy" "api" { name = "Managed-AllViewerExceptHostHeader" }
resource "aws_cloudfront_origin_access_control" "s3" {
  name                              = "${local.prefix}-s3"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}
resource "aws_cloudfront_function" "media" {
  name    = "${local.prefix}-media-rewrite"
  runtime = "cloudfront-js-2.0"
  publish = true
  code    = "function handler(event) { var r = event.request; r.uri = r.uri.replace(/^\\/media\\//, '/'); return r; }"
}
resource "aws_cloudfront_function" "spa" {
  name    = "${local.prefix}-spa-rewrite"
  runtime = "cloudfront-js-2.0"
  publish = true
  code    = "function handler(event) { var r = event.request; if (r.uri.indexOf('.') === -1 || r.uri.endsWith('/')) r.uri = '/index.html'; return r; }"
}
resource "aws_cloudfront_distribution" "app" {
  enabled             = true
  is_ipv6_enabled     = true
  comment             = "${local.prefix} frontend, API and media"
  default_root_object = "index.html"
  price_class         = "PriceClass_200"
  aliases             = var.certificate_arn == "" ? [] : ["vbeeaistt.com"]
  origin {
    origin_id                = "frontend"
    domain_name              = "${var.frontend_bucket}.s3.ap-southeast-1.amazonaws.com"
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }
  origin {
    origin_id                = "media"
    domain_name              = "${var.output_bucket}.s3.ap-southeast-1.amazonaws.com"
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }
  origin {
    origin_id   = "api"
    domain_name = var.api_hostname
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }
  default_cache_behavior {
    target_origin_id       = "frontend"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.optimized.id
    compress               = true
    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa.arn
    }
  }
  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = "api"
    viewer_protocol_policy   = "https-only"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "PATCH", "POST", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.api.id
    compress                 = true
  }
  ordered_cache_behavior {
    path_pattern           = "/media/*"
    target_origin_id       = "media"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.optimized.id
    compress               = true
    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.media.arn
    }
  }
  restrictions {
    geo_restriction { restriction_type = "none" }
  }
  viewer_certificate {
    cloudfront_default_certificate = var.certificate_arn == ""
    acm_certificate_arn            = var.certificate_arn == "" ? null : var.certificate_arn
    ssl_support_method             = var.certificate_arn == "" ? null : "sni-only"
    minimum_protocol_version       = var.certificate_arn == "" ? "TLSv1" : "TLSv1.2_2021"
  }
}
resource "aws_s3_bucket_policy" "cloudfront" {
  for_each = toset([var.frontend_bucket, var.output_bucket])
  bucket   = each.value
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "arn:aws:s3:::${each.value}/*"
      Condition = { StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.app.arn } }
    }]
  })
}
output "distribution_id" { value = aws_cloudfront_distribution.app.id }
output "domain" { value = aws_cloudfront_distribution.app.domain_name }
output "arn" { value = aws_cloudfront_distribution.app.arn }
