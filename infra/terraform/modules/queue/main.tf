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
