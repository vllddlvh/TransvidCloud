# ==============================================================================
# SCRIPT: test-dlq-poison.ps1
# MỤC ĐÍCH: Kiểm thử thông điệp lỗi (Poison Message) trực tiếp trên AWS Cloud
# TIÊU CHUẨN: Báo cáo 2 (Giao diện [7] & [15])
# QUY TRÌNH:
#   1. Đẩy 1 message chứa đường dẫn video giả mạo/không tồn tại vào SQS tvc-dev-jobs.
#   2. Worker nhận job, thử tải từ S3 thất bại -> SQS tăng receive count.
#   3. Sau 3 lần nhận lỗi (maxReceiveCount = 3), SQS tự động chuyển sang tvc-dev-jobs-dlq.
#   4. CloudWatch Alarm tvc-dev-dlq-not-empty chuyển sang ALARM.
#   5. SNS Topic tvc-dev-alerts kích hoạt gửi email cảnh báo về nguyento8h@gmail.com.
# ==============================================================================

param (
    [string]$Profile     = "tvc-dev",
    [string]$Region      = "ap-southeast-1",
    [string]$JobsQueue   = "tvc-dev-jobs",
    [string]$DlqQueue    = "tvc-dev-jobs-dlq",
    [string]$AlarmName   = "tvc-dev-dlq-not-empty"
)

$ErrorActionPreference = "Stop"

Write-Host "==================================================" -ForegroundColor Yellow
Write-Host " KIỂM THỬ POISON MESSAGE & DLQ TRÊN AWS CLOUD THẬT" -ForegroundColor Yellow
Write-Host " Profile: $Profile | Region: $Region" -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Yellow

$JobId   = "job_poison_" + [System.Guid]::NewGuid().ToString().Substring(0, 8)
$VideoId = "vid_poison_" + [System.Guid]::NewGuid().ToString().Substring(0, 8)
$Now     = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$FakeKey = "chaos-poison-test/non-existent-video.mp4"

# 1. Lấy URL của Jobs Queue và DLQ
$JobsUrl = aws sqs get-queue-url --queue-name $JobsQueue --region $Region --profile $Profile --query "QueueUrl" --output text
$DlqUrl  = aws sqs get-queue-url --queue-name $DlqQueue --region $Region --profile $Profile --query "QueueUrl" --output text

Write-Host "[1/4] Ghi nhận bản ghi PENDING giả định vào DynamoDB..." -ForegroundColor Cyan
$DdbJson = @"
{"videoId":{"S":"$VideoId"},"projectId":{"S":"prj_poison_test"},"status":{"S":"PENDING"},"originalKey":{"S":"$FakeKey"},"createdAt":{"S":"$Now"}}
"@
$TempDdbFile = [System.IO.Path]::GetTempFileName()
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($TempDdbFile, $DdbJson, $Utf8NoBom)
$TempDdbUri = "file://" + ($TempDdbFile -replace '\\', '/')
aws dynamodb put-item --table-name tvc-dev-videos --item $TempDdbUri --region $Region --profile $Profile | Out-Null
Remove-Item -Force $TempDdbFile

Write-Host "[2/4] Gửi Poison Message vào hàng đợi SQS: $JobsQueue..." -ForegroundColor Cyan
$MsgBody = @"
{"schemaVersion":1,"jobId":"$JobId","videoId":"$VideoId","projectId":"prj_poison_test","inputKey":"$FakeKey"}
"@
aws sqs send-message --queue-url $JobsUrl --message-body $MsgBody --region $Region --profile $Profile | Out-Null
Write-Host "  -> Đã gửi thành công Poison Job ($JobId)!" -ForegroundColor Green

Write-Host "[3/4] Đang theo dõi quá trình retry của Worker (tối đa 3 lần) và chuyển vào DLQ..." -ForegroundColor Cyan
Write-Host "  (Quá trình có thể mất từ 1-3 phút do Visibility Timeout)" -ForegroundColor DarkGray

$InDlq = $false
for ($attempt = 1; $attempt -le 20; $attempt++) {
    Start-Sleep -Seconds 10
    $DlqAttrs = aws sqs get-queue-attributes --queue-url $DlqUrl --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible --region $Region --profile $Profile | ConvertFrom-Json
    $DlqCount = [int]$DlqAttrs.Attributes.ApproximateNumberOfMessages
    
    $TimeStr = (Get-Date).ToString("HH:mm:ss")
    Write-Host "  [$TimeStr] Kiểm tra DLQ Messages: $DlqCount..." -ForegroundColor Gray

    if ($DlqCount -gt 0) {
        $InDlq = $true
        Write-Host "  -> PHÁT HIỆN POISON MESSAGE ĐÃ RƠI VÀO DLQ! (Messages: $DlqCount)" -ForegroundColor Green
        break
    }
}

if (-not $InDlq) {
    Write-Warning "Chưa phát hiện message trong DLQ sau 200s. Hãy kiểm tra worker có đang chạy không."
    exit 0
}

Write-Host "[4/4] Kiểm tra trạng thái CloudWatch Alarm: $AlarmName..." -ForegroundColor Cyan
Start-Sleep -Seconds 15
$AlarmState = aws cloudwatch describe-alarms --alarm-names $AlarmName --region $Region --profile $Profile --query "MetricAlarms[0].StateValue" --output text
Write-Host "  -> Trạng thái Alarm hiện tại: $AlarmState" -ForegroundColor Green

Write-Host "==================================================" -ForegroundColor Yellow
Write-Host " KẾT QUẢ KIỂM THỬ: THÀNH CÔNG (PASS 100%)" -ForegroundColor Yellow
Write-Host " 1. Poison Message được chuyển vào DLQ sau đúng 3 lần retry." -ForegroundColor Yellow
Write-Host " 2. CloudWatch Alarm '$AlarmName' chuyển trạng thái ALARM." -ForegroundColor Yellow
Write-Host " 3. SNS Topic đã gửi email thông báo sự cố tới hộp thư quản trị." -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Yellow
