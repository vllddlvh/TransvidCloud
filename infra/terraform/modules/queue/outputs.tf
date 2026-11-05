output "jobs_queue_url" {
  value       = aws_sqs_queue.jobs.url
  description = "URL của SQS Jobs Queue"
}

output "jobs_queue_arn" {
  value       = aws_sqs_queue.jobs.arn
  description = "ARN của SQS Jobs Queue"
}

output "jobs_dlq_url" {
  value       = aws_sqs_queue.jobs_dlq.url
  description = "URL của SQS DLQ"
}

output "jobs_dlq_arn" {
  value       = aws_sqs_queue.jobs_dlq.arn
  description = "ARN của SQS DLQ"
}
