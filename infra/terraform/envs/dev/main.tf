terraform {
  required_version = ">= 1.10, < 2.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
  backend "s3" {}
}

variable "aws_region" {
  type    = string
  default = "ap-southeast-2"
}
variable "allowed_account_id" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.allowed_account_id))
    error_message = "Enter the intended 12-digit AWS account ID."
  }
}
variable "allowed_origins" {
  type    = list(string)
  default = ["http://localhost:8000"]
}

provider "aws" {
  region              = var.aws_region
  allowed_account_ids = [var.allowed_account_id]
  default_tags {
    tags = { Project = "TransVidCloud", Env = "dev", ManagedBy = "Terraform" }
  }
}

module "storage" {
  source          = "../../modules/storage"
  prefix          = "tvc-dev"
  bucket_suffix   = "${var.allowed_account_id}-${var.aws_region}"
  allowed_origins = var.allowed_origins
}
module "queue" {
  source = "../../modules/queue"
  prefix = "tvc-dev"
}
module "database" {
  source = "../../modules/database"
  prefix = "tvc-dev"
}
module "network" {
  source = "../../modules/network"
  prefix = "tvc-dev"
  region = var.aws_region
}

module "iam" {
  source            = "../../modules/iam"
  prefix            = "tvc-dev"
  parameter_prefix  = "/tvc/dev"
  region            = var.aws_region
  account_id        = var.allowed_account_id
  input_bucket_arn  = module.storage.buckets["input"].arn
  output_bucket_arn = module.storage.buckets["output"].arn
  jobs_queue_arn    = module.queue.jobs.arn
  table_arns        = { for name, table in module.database.tables : name => table.arn }
}

output "region" { value = var.aws_region }
output "buckets" { value = module.storage.buckets }
output "jobs_queue" { value = module.queue.jobs }
output "dlq" { value = module.queue.dlq }
output "tables" { value = module.database.tables }
output "vpc_id" { value = module.network.vpc_id }
output "public_subnet_ids" { value = module.network.public_subnet_ids }
output "security_group_ids" { value = module.network.security_group_ids }
output "runtime_roles" { value = module.iam.roles }
output "instance_profiles" { value = module.iam.instance_profiles }
