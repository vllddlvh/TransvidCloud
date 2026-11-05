<<<<<<< HEAD
variable "prefix" { type = string }

resource "aws_sqs_queue" "dlq" {
  name                      = "${var.prefix}-jobs-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "jobs" {
  name                       = "${var.prefix}-jobs"
  visibility_timeout_seconds = 300
  receive_wait_time_seconds  = 20
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "dlq" {
  queue_url = aws_sqs_queue.dlq.id
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.jobs.arn]
  })
}

output "jobs" {
  value = { url = aws_sqs_queue.jobs.id, arn = aws_sqs_queue.jobs.arn, name = aws_sqs_queue.jobs.name }
}
output "dlq" {
  value = { url = aws_sqs_queue.dlq.id, arn = aws_sqs_queue.dlq.arn, name = aws_sqs_queue.dlq.name }
}
=======
# 1. Dead-Letter Queue (tạo trước để lấy ARN gán vào queue chính)
resource "aws_sqs_queue" "jobs_dlq" {
  name                      = "tvc-${var.environment}-jobs-dlq"
  message_retention_seconds = 1209600 # Lưu trữ 14 ngày để debug
}

# 2. Hàng đợi Jobs chính
resource "aws_sqs_queue" "jobs" {
  name                       = "tvc-${var.environment}-jobs"
  visibility_timeout_seconds = 300 # 5 phút để worker transcode
  receive_wait_time_seconds  = 20  # Long Polling: Giảm chi phí request rỗng về 0$

  # Redrive Policy: Thử lỗi quá 3 lần thì chuyển vào DLQ cách ly
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.jobs_dlq.arn
    maxReceiveCount     = 3
  })
}
>>>>>>> 6317d81836060cb47572c643c512292ce87cb101
