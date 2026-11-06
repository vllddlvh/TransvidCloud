variable "alert_emails" {
  type    = set(string)
  default = []
}
resource "aws_sns_topic_subscription" "email" {
  for_each  = var.alert_emails
  topic_arn = module.observability.alerts_arn
  protocol  = "email"
  endpoint  = each.value
}
