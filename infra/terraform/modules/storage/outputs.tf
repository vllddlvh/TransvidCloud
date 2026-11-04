output "input_bucket_name" {
  value       = aws_s3_bucket.input.id
  description = "Tên S3 Input Bucket"
}

output "input_bucket_arn" {
  value       = aws_s3_bucket.input.arn
  description = "ARN của S3 Input Bucket"
}

output "output_bucket_name" {
  value       = aws_s3_bucket.output.id
  description = "Tên S3 Output Bucket"
}

output "output_bucket_arn" {
  value       = aws_s3_bucket.output.arn
  description = "ARN của S3 Output Bucket"
}

output "frontend_bucket_name" {
  value       = aws_s3_bucket.frontend.id
  description = "Tên S3 Frontend Bucket"
}

output "frontend_bucket_arn" {
  value       = aws_s3_bucket.frontend.arn
  description = "ARN của S3 Frontend Bucket"
}
