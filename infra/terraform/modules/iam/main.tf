variable "environment" { type = string }
variable "account_id" { type = string }
variable "region" { type = string }
variable "input_arn" { type = string }
variable "output_arn" { type = string }
variable "queue_arn" { type = string }
locals {
  prefix        = "tvc-${var.environment}"
  assume_ec2    = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }] })
  table_arn     = "arn:aws:dynamodb:${var.region}:${var.account_id}:table/${local.prefix}"
  parameter_arn = "arn:aws:ssm:${var.region}:${var.account_id}:parameter/tvc/${var.environment}"
  log_arn       = "arn:aws:logs:${var.region}:${var.account_id}:log-group:/tvc/${var.environment}/*"
}
resource "aws_iam_role" "instance" {
  for_each           = toset(["api", "worker"])
  name               = "${local.prefix}-${each.key}-role"
  assume_role_policy = local.assume_ec2
}
resource "aws_iam_instance_profile" "instance" {
  for_each = aws_iam_role.instance
  name     = each.value.name
  role     = each.value.name
}
resource "aws_iam_role_policy_attachment" "ssm" {
  for_each   = aws_iam_role.instance
  role       = each.value.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
resource "aws_iam_role_policy" "runtime" {
  for_each = aws_iam_role.instance
  name     = "${local.prefix}-runtime"
  role     = each.value.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat([
      { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
      { Effect = "Allow", Action = ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"], Resource = "arn:aws:ecr:${var.region}:${var.account_id}:repository/tvc-${each.key == "api" ? "backend" : "worker"}" },
      { Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "${local.log_arn}:*" },
      { Effect = "Allow", Action = ["ssm:GetParameter"], Resource = each.key == "api" ? ["${local.parameter_arn}/api/*", "${local.parameter_arn}/cloudflared/token"] : ["${local.parameter_arn}/worker/*"] },
      { Effect = "Deny", Action = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"], NotResource = each.key == "api" ? ["${local.parameter_arn}/api/*", "${local.parameter_arn}/cloudflared/token"] : ["${local.parameter_arn}/worker/*"] }
      ], each.key == "api" ? [
      { Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject"], Resource = ["${var.input_arn}/*"] },
      { Effect = "Allow", Action = ["sqs:SendMessage", "sqs:GetQueueUrl"], Resource = [var.queue_arn] },
      { Effect = "Allow", Action = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:DeleteItem", "dynamodb:Query", "dynamodb:BatchGetItem", "dynamodb:TransactWriteItems"], Resource = flatten([for table in ["users", "projects", "apikeys", "videos"] : ["${local.table_arn}-${table}", "${local.table_arn}-${table}/index/*"]]) }
      ] : [
      { Effect = "Allow", Action = ["s3:GetObject"], Resource = ["${var.input_arn}/*"] },
      { Effect = "Allow", Action = ["s3:PutObject"], Resource = ["${var.output_arn}/*"] },
      { Effect = "Allow", Action = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueAttributes"], Resource = [var.queue_arn] },
      { Effect = "Allow", Action = ["dynamodb:GetItem", "dynamodb:UpdateItem"], Resource = ["${local.table_arn}-videos"] },
      { Effect = "Allow", Action = ["autoscaling:SetInstanceProtection"], Resource = ["arn:aws:autoscaling:${var.region}:${var.account_id}:autoScalingGroup:*:autoScalingGroupName/${local.prefix}-worker-asg"] }
    ])
  })
}
resource "aws_iam_role" "github" {
  name = "${local.prefix}-github-deploy-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = "arn:aws:iam::${var.account_id}:oidc-provider/token.actions.githubusercontent.com" }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          "token.actions.githubusercontent.com:sub" = "repo:vllddlvh/TransvidCloud:ref:refs/heads/nguyen-1"
        }
      }
    }]
  })
}
output "api_profile" { value = aws_iam_instance_profile.instance["api"].name }
output "worker_profile" { value = aws_iam_instance_profile.instance["worker"].name }
output "github_role_arn" { value = aws_iam_role.github.arn }
