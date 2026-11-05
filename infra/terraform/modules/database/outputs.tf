output "table_users_name" {
  value       = aws_dynamodb_table.users.name
  description = "Tên bảng DynamoDB Users"
}

output "table_projects_name" {
  value       = aws_dynamodb_table.projects.name
  description = "Tên bảng DynamoDB Projects"
}

output "table_apikeys_name" {
  value       = aws_dynamodb_table.apikeys.name
  description = "Tên bảng DynamoDB ApiKeys"
}

output "table_videos_name" {
  value       = aws_dynamodb_table.videos.name
  description = "Tên bảng DynamoDB Videos"
}

output "table_videos_arn" {
  value       = aws_dynamodb_table.videos.arn
  description = "ARN bảng DynamoDB Videos"
}
