locals {
  arn_prefix = "arn:aws"
  parameters = {
    api = [
      "${local.arn_prefix}:ssm:${var.region}:${var.account_id}:parameter${var.parameter_prefix}/api/*",
      "${local.arn_prefix}:ssm:${var.region}:${var.account_id}:parameter${var.parameter_prefix}/cloudflared/token"
    ]
    worker = ["${local.arn_prefix}:ssm:${var.region}:${var.account_id}:parameter${var.parameter_prefix}/worker/*"]
  }
  repositories = { api = "tvc-backend", worker = "tvc-worker" }
  common_statements = {
    for role in ["api", "worker"] : role => [
      {
        Sid      = "ReadOwnRuntimeParameters"
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParameters"]
        Resource = local.parameters[role]
      },
      # AmazonSSMManagedInstanceCore also allows parameter reads on '*'.
      # Keep Session Manager while explicitly excluding other runtime secrets.
      {
        Sid         = "DenyOtherRuntimeParameters"
        Effect      = "Deny"
        Action      = ["ssm:GetParameter", "ssm:GetParameters"]
        NotResource = local.parameters[role]
      },
      {
        Sid      = "EcrLogin"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Sid      = "PullOwnImage"
        Effect   = "Allow"
        Action   = ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"]
        Resource = "${local.arn_prefix}:ecr:${var.region}:${var.account_id}:repository/${local.repositories[role]}"
      },
      {
        Sid      = "WriteRuntimeLogs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "${local.arn_prefix}:logs:${var.region}:${var.account_id}:log-group:${var.parameter_prefix}/${role}:log-stream:*"
      }
    ]
  }
  app_statements = {
    api = [
      {
        Sid      = "InputUploadAndHead"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject"]
        Resource = "${var.input_bucket_arn}/*"
      },
      {
        Sid      = "SubmitJobs"
        Effect   = "Allow"
        Action   = ["sqs:SendMessage"]
        Resource = var.jobs_queue_arn
      },
      {
        Sid      = "AppTables"
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:DeleteItem", "dynamodb:Query", "dynamodb:BatchGetItem"]
        Resource = concat(values(var.table_arns), [for arn in values(var.table_arns) : "${arn}/index/*"])
      }
    ]
    worker = [
      {
        Sid      = "ReadInput"
        Effect   = "Allow"
        Action   = ["s3:GetObject"]
        Resource = "${var.input_bucket_arn}/*"
      },
      {
        Sid      = "WriteOutput"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:AbortMultipartUpload"]
        Resource = "${var.output_bucket_arn}/*"
      },
      {
        Sid      = "ConsumeJobs"
        Effect   = "Allow"
        Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueAttributes"]
        Resource = var.jobs_queue_arn
      },
      {
        Sid      = "UpdateVideoState"
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem", "dynamodb:UpdateItem"]
        Resource = var.table_arns["videos"]
      },
      {
        Sid      = "ProtectBusyWorker"
        Effect   = "Allow"
        Action   = ["autoscaling:SetInstanceProtection"]
        Resource = "${local.arn_prefix}:autoscaling:${var.region}:${var.account_id}:autoScalingGroup:*:autoScalingGroupName/${var.prefix}-worker-asg"
      }
    ]
  }
}

resource "aws_iam_role" "runtime" {
  for_each = toset(["api", "worker"])
  name     = "${var.prefix}-${each.key}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_instance_profile" "runtime" {
  for_each = aws_iam_role.runtime
  name     = each.value.name
  role     = each.value.name
}

resource "aws_iam_role_policy_attachment" "ssm" {
  for_each   = aws_iam_role.runtime
  role       = each.value.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "runtime" {
  for_each = aws_iam_role.runtime
  name     = "${var.prefix}-${each.key}-runtime"
  role     = each.value.id
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = concat(local.common_statements[each.key], local.app_statements[each.key])
  })
}
