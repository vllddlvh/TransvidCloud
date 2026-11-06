provider "aws" {
  alias  = "virginia"
  region = "us-east-1"
  default_tags {
    tags = { Project = "TransVidCloud", Environment = "dev" }
  }
}
variable "web_certificate_arn" {
  type    = string
  default = ""
}
resource "aws_acm_certificate" "web" {
  provider          = aws.virginia
  domain_name       = "vbeeaistt.com"
  validation_method = "DNS"
  lifecycle { create_before_destroy = true }
}
module "edge" {
  source          = "../../modules/edge"
  environment     = var.environment
  frontend_bucket = module.storage.frontend_bucket_name
  output_bucket   = module.storage.output_bucket_name
  api_hostname    = "api.vbeeaistt.com"
  certificate_arn = var.web_certificate_arn
}
output "cloudfront_distribution_id" { value = module.edge.distribution_id }
output "cloudfront_domain" { value = module.edge.domain }
output "web_certificate_arn" { value = aws_acm_certificate.web.arn }
output "web_certificate_dns" { value = aws_acm_certificate.web.domain_validation_options }
