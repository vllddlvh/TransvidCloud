variable "api_enabled" {
  type    = bool
  default = true
}
module "iam" {
  source      = "../../modules/iam"
  environment = var.environment
  account_id  = var.account_id
  region      = var.aws_region
  input_arn   = module.storage.input_bucket_arn
  output_arn  = module.storage.output_bucket_arn
  queue_arn   = module.queue.jobs_queue_arn
}
module "observability" {
  source      = "../../modules/observability"
  environment = var.environment
}
module "compute" {
  source          = "../../modules/compute"
  environment     = var.environment
  account_id      = var.account_id
  region          = var.aws_region
  vpc_id          = module.network.vpc_id
  subnet_ids      = module.network.public_subnet_ids
  api_profile     = module.iam.api_profile
  worker_profile  = module.iam.worker_profile
  input_bucket    = module.storage.input_bucket_name
  output_bucket   = module.storage.output_bucket_name
  queue_url       = module.queue.jobs_queue_url
  api_enabled     = var.api_enabled
  allowed_origins = ["https://vbeeaistt.com", "https://${module.edge.domain}"]
}
output "api_instance_id" { value = module.compute.api_id }
output "worker_asg_name" { value = module.compute.worker_asg }
output "ecr_registry" { value = module.compute.registry }
output "github_deploy_role_arn" { value = module.iam.github_role_arn }
output "alerts_topic_arn" { value = module.observability.alerts_arn }
