variable "prefix" { type = string }
variable "parameter_prefix" { type = string }
variable "region" { type = string }
variable "account_id" { type = string }
variable "input_bucket_arn" { type = string }
variable "output_bucket_arn" { type = string }
variable "jobs_queue_arn" { type = string }
variable "table_arns" { type = map(string) }
