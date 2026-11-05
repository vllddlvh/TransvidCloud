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
