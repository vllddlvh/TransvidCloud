variable "environment" { type = string }
resource "aws_cloudwatch_log_group" "runtime" {
  for_each          = toset(["api", "worker"])
  name              = "/tvc/${var.environment}/${each.key}"
  retention_in_days = 7
}
resource "aws_sns_topic" "alerts" { name = "tvc-${var.environment}-alerts" }
resource "aws_cloudwatch_metric_alarm" "dlq" {
  alarm_name          = "tvc-${var.environment}-dlq-not-empty"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = "tvc-${var.environment}-jobs-dlq" }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}
output "alerts_arn" { value = aws_sns_topic.alerts.arn }
