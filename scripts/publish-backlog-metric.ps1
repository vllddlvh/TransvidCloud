# ==============================================================================
# SCRIPT: publish-backlog-metric.ps1
# MỤC ĐÍCH: Hiện thực hóa tiện ích "metricpub" theo cam kết Báo cáo 2 (Giao diện [13])
# ĐO ĐẠC: BacklogPerInstance = Số message đang chờ / max(1, Số Worker đang chạy)
# ĐẨY CHỈ SỐ: CloudWatch Custom Metric "TransVidCloud/dev" -> "BacklogPerInstance"
# ĐIỀU PHỐI: Kích hoạt Target Tracking Scaling Policy của Worker ASG (Giao diện [14])
# ==============================================================================

param (
    [string]$Profile     = "tvc-dev",
    [string]$Region      = "ap-southeast-1",
    [string]$QueueName   = "tvc-dev-jobs",
    [string]$AsgName     = "tvc-dev-worker-asg",
    [string]$Namespace   = "TransVidCloud/dev",
    [int]$IntervalSec    = 20,
    [switch]$Once        = $false
)

$ErrorActionPreference = "Stop"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " TIỆN ÍCH METRICPUB: ĐO & CÔNG BỐ BACKLOGPERINSTANCE" -ForegroundColor Cyan
Write-Host " Profile: $Profile | Region: $Region" -ForegroundColor Cyan
Write-Host " ASG: $AsgName | Queue: $QueueName" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# Lấy URL của queue
$QueueUrl = aws sqs get-queue-url --queue-name $QueueName --region $Region --profile $Profile --query "QueueUrl" --output text
if (-not $QueueUrl) {
    Write-Error "Không tìm thấy SQS Queue: $QueueName"
    exit 1
}

do {
    $Now = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")

    # 1. Đọc số message đang chờ trong SQS
    $QueueAttrs = aws sqs get-queue-attributes --queue-url $QueueUrl --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible --region $Region --profile $Profile | ConvertFrom-Json
    $Visible = [int]$QueueAttrs.Attributes.ApproximateNumberOfMessages
    $NotVisible = [int]$QueueAttrs.Attributes.ApproximateNumberOfMessagesNotVisible
    $TotalBacklog = $Visible

    # 2. Đọc số lượng worker instance đang chạy trong ASG
    $AsgInfo = aws autoscaling describe-auto-scaling-groups --auto-scaling-group-name $AsgName --region $Region --profile $Profile | ConvertFrom-Json
    $InServiceWorkers = 0
    if ($AsgInfo.AutoScalingGroups.Count -gt 0) {
        $Instances = $AsgInfo.AutoScalingGroups[0].Instances | Where-Object { $_.LifecycleState -eq "InService" }
        $InServiceWorkers = $Instances.Count
    }

    # 3. Tính toán BacklogPerInstance theo công thức Báo cáo 2:
    # BacklogPerInstance = Số message đang chờ / max(1, Số Worker đang chạy)
    $EffectiveWorkers = [Math]::Max(1, $InServiceWorkers)
    $BacklogPerInstance = [Math]::Round(($TotalBacklog / $EffectiveWorkers), 2)

    # 4. Công bố chỉ số lên CloudWatch Metrics
    aws cloudwatch put-metric-data `
        --namespace $Namespace `
        --metric-name "BacklogPerInstance" `
        --value $BacklogPerInstance `
        --unit "Count" `
        --dimensions "AutoScalingGroupName=$AsgName" `
        --region $Region `
        --profile $Profile

    Write-Host "[$Now] Backlog: $TotalBacklog | Workers: $InServiceWorkers | BacklogPerInstance: $BacklogPerInstance -> Đã đẩy lên CloudWatch" -ForegroundColor Green

    if ($Once) {
        break
    }

    Start-Sleep -Seconds $IntervalSec
} while ($true)
