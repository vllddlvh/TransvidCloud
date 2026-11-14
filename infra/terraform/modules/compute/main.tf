variable "environment" { type = string }
variable "region" { type = string }
variable "account_id" { type = string }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "api_profile" { type = string }
variable "worker_profile" { type = string }
variable "input_bucket" { type = string }
variable "output_bucket" { type = string }
variable "queue_url" { type = string }
variable "api_enabled" { type = bool }
variable "allowed_origins" { type = list(string) }
variable "worker_min_size" {
  type        = number
  default     = 1
  description = "Số lượng worker tối thiểu (Báo cáo 2: min=1 cho demo và benchmark; min=0 khi hạ tải nghỉ)"
}
variable "worker_max_size" {
  type        = number
  default     = 4
  description = "Số lượng worker tối đa (Báo cáo 2: max=4; có thể hạ về 2 nếu AWS Budgets cảnh báo)"
}
data "aws_ssm_parameter" "ami" { name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64" }
locals {
  prefix   = "tvc-${var.environment}"
  registry = "${var.account_id}.dkr.ecr.${var.region}.amazonaws.com"
  bootstrap = {
    region               = var.region
    environment          = var.environment
    registry             = local.registry
    input_bucket         = var.input_bucket
    output_bucket        = var.output_bucket
    queue_url            = var.queue_url
    cors_allowed_origins = join(",", var.allowed_origins)
  }
}
resource "aws_ecr_repository" "images" {
  for_each             = toset(["backend", "worker"])
  name                 = "tvc-${each.key}"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
}
resource "aws_ecr_lifecycle_policy" "images" {
  for_each   = aws_ecr_repository.images
  repository = each.value.name
  policy     = jsonencode({ rules = [{ rulePriority = 1, description = "Keep latest five images", selection = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 5 }, action = { type = "expire" } }] })
}
resource "aws_security_group" "runtime" {
  name        = "${local.prefix}-runtime"
  description = "No inbound; outbound for AWS and Cloudflare Tunnel"
  vpc_id      = var.vpc_id
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
resource "aws_instance" "api" {
  count                       = var.api_enabled ? 1 : 0
  ami                         = data.aws_ssm_parameter.ami.value
  instance_type               = "t3.small"
  subnet_id                   = var.subnet_ids[0]
  associate_public_ip_address = true
  vpc_security_group_ids      = [aws_security_group.runtime.id]
  iam_instance_profile        = var.api_profile
  user_data                   = templatefile("${path.module}/bootstrap.sh.tftpl", merge(local.bootstrap, { component = "api" }))
  user_data_replace_on_change = false
  # Khi Stop, EC2 nhả public IPv4 và provider đọc thuộc tính này là false.
  # Giữ cấu hình public IP lúc tạo, tránh thay máy sau quy trình hạ tải hàng ngày.
  lifecycle { ignore_changes = [associate_public_ip_address] }
  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }
  root_block_device {
    volume_size = 20
    volume_type = "gp3"
    encrypted   = true
  }
  tags = { Name = "${local.prefix}-api" }
}
resource "aws_launch_template" "worker" {
  name_prefix            = "${local.prefix}-worker-"
  image_id               = data.aws_ssm_parameter.ami.value
  instance_type          = "c6i.large"
  update_default_version = true
  iam_instance_profile { name = var.worker_profile }
  network_interfaces {
    associate_public_ip_address = true
    security_groups             = [aws_security_group.runtime.id]
  }
  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }
  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_size           = 30
      volume_type           = "gp3"
      encrypted             = true
      delete_on_termination = true
    }
  }
  user_data = base64encode(templatefile("${path.module}/bootstrap.sh.tftpl", merge(local.bootstrap, { component = "worker" })))
}
resource "aws_autoscaling_group" "worker" {
  name                  = "${local.prefix}-worker-asg"
  min_size              = var.worker_min_size
  desired_capacity      = var.worker_min_size
  max_size              = var.worker_max_size
  vpc_zone_identifier   = var.subnet_ids
  protect_from_scale_in = true
  mixed_instances_policy {
    instances_distribution {
      on_demand_base_capacity                  = 0
      on_demand_percentage_above_base_capacity = 0
      spot_allocation_strategy                 = "price-capacity-optimized"
    }
    launch_template {
      launch_template_specification {
        launch_template_id = aws_launch_template.worker.id
        version            = "$Latest"
      }
      dynamic "override" {
        for_each = ["c6i.large", "c5.large", "t3.large"]
        content { instance_type = override.value }
      }
    }
  }
  tag {
    key                 = "Name"
    value               = "${local.prefix}-worker"
    propagate_at_launch = true
  }
  tag {
    key                 = "Project"
    value               = "TransVidCloud"
    propagate_at_launch = true
  }
  lifecycle { ignore_changes = [desired_capacity] }
}

# Giao diện [14]: Target Tracking Scaling Policy theo BacklogPerInstance (1-4 máy)
resource "aws_autoscaling_policy" "worker_backlog" {
  name                   = "${local.prefix}-worker-backlog-tracking"
  autoscaling_group_name = aws_autoscaling_group.worker.name
  policy_type            = "TargetTrackingScaling"

  target_tracking_configuration {
    target_value     = 2.0 # Mỗi worker gánh trung bình 2 message đang chờ
    disable_scale_in = false

    customized_metric_specification {
      metric_name = "BacklogPerInstance"
      namespace   = "TransVidCloud/${var.environment}"
      statistic   = "Average"
    }
  }
}

output "api_id" { value = try(aws_instance.api[0].id, null) }
output "worker_asg" { value = aws_autoscaling_group.worker.name }
output "registry" { value = local.registry }
