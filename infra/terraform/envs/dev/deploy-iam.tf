resource "aws_iam_role_policy" "github_deploy" {
  name       = "tvc-dev-deploy"
  role       = "tvc-dev-github-deploy-role"
  depends_on = [module.iam]
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
      { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart", "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"], Resource = [for repo in ["backend", "worker"] : "arn:aws:ecr:${var.aws_region}:${var.account_id}:repository/tvc-${repo}"] },
      { Effect = "Allow", Action = ["ssm:GetParameter", "ssm:PutParameter"], Resource = [for service in ["api", "worker"] : "arn:aws:ssm:${var.aws_region}:${var.account_id}:parameter/tvc/dev/${service}/image-tag"] },
      { Effect = "Allow", Action = ["ssm:SendCommand"], Resource = "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript" },
      { Effect = "Allow", Action = ["ssm:SendCommand"], Resource = "arn:aws:ec2:${var.aws_region}:${var.account_id}:instance/*", Condition = { StringEquals = { "ssm:resourceTag/Name" = "tvc-dev-api" } } },
      { Effect = "Allow", Action = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations", "ec2:DescribeInstances"], Resource = "*" },
      { Effect = "Allow", Action = ["autoscaling:StartInstanceRefresh", "autoscaling:CancelInstanceRefresh"], Resource = "arn:aws:autoscaling:${var.aws_region}:${var.account_id}:autoScalingGroup:*:autoScalingGroupName/tvc-dev-worker-asg" },
      { Effect = "Allow", Action = ["autoscaling:DescribeInstanceRefreshes", "autoscaling:DescribeAutoScalingGroups"], Resource = "*" },
      { Effect = "Allow", Action = ["s3:ListBucket", "s3:GetBucketLocation"], Resource = module.storage.frontend_bucket_arn },
      { Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"], Resource = "${module.storage.frontend_bucket_arn}/*" },
      { Effect = "Allow", Action = ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"], Resource = module.edge.arn }
    ]
  })
}
