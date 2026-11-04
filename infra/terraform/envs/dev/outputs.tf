output "vpc_id" {
  value       = module.network.vpc_id
  description = "VPC ID"
}

output "public_subnet_ids" {
  value       = module.network.public_subnet_ids
  description = "Danh sách Public Subnet IDs"
}

output "input_bucket_name" {
  value       = module.storage.input_bucket_name
  description = "Tên S3 Input Bucket"
}

output "output_bucket_name" {
  value       = module.storage.output_bucket_name
  description = "Tên S3 Output Bucket"
}

output "frontend_bucket_name" {
  value       = module.storage.frontend_bucket_name
  description = "Tên S3 Frontend Bucket"
}

output "jobs_queue_url" {
  value       = module.queue.jobs_queue_url
  description = "SQS Jobs Queue URL"
}

output "jobs_dlq_url" {
  value       = module.queue.jobs_dlq_url
  description = "SQS Jobs DLQ URL"
}

output "videos_table_name" {
  value       = module.database.table_videos_name
  description = "Tên bảng DynamoDB Videos"
}
