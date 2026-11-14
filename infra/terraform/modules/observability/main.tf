variable "environment" { type = string }

resource "aws_cloudwatch_log_group" "runtime" {
  for_each          = toset(["api", "worker"])
  name              = "/tvc/${var.environment}/${each.key}"
  retention_in_days = 7
}

resource "aws_sns_topic" "alerts" { name = "tvc-${var.environment}-alerts" }

# 1. Cảnh báo khi có thông điệp trong Dead-Letter Queue (Giao diện [15] & [7])
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
  alarm_description   = "Cảnh báo khi có video thất bại chuyển mã bị đẩy vào DLQ"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

# 2. Cảnh báo CPU cao trên cụm Worker ASG (Giao diện [15])
resource "aws_cloudwatch_metric_alarm" "worker_high_cpu" {
  alarm_name          = "tvc-${var.environment}-worker-high-cpu"
  namespace           = "AWS/EC2"
  metric_name         = "CPUUtilization"
  dimensions          = { AutoScalingGroupName = "tvc-${var.environment}-worker-asg" }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 85
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_description   = "Cảnh báo khi CPU trung bình cụm Worker vượt 85% trong 10 phút"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

# 3. Cảnh báo thông điệp chờ quá lâu trong hàng đợi chính SQS (Giao diện [15])
resource "aws_cloudwatch_metric_alarm" "queue_job_age_high" {
  alarm_name          = "tvc-${var.environment}-queue-job-age-high"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateAgeOfOldestMessage"
  dimensions          = { QueueName = "tvc-${var.environment}-jobs" }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 300 # 5 phút
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_description   = "Cảnh báo khi có video chờ xử lý quá 5 phút trong hàng đợi chính SQS"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

output "alerts_arn" { value = aws_sns_topic.alerts.arn }
