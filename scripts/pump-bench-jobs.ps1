param (
    [int]$Count = 20,
    [string]$Profile = "tvc-dev",
    [string]$Region = "ap-southeast-1"
)

$QueueUrl  = "https://sqs.ap-southeast-1.amazonaws.com/060245183757/tvc-dev-jobs"
$InputKey  = "benchmark/sample-10s-720p.mp4"
$ProjectId = "prj_scale_demo"
$Table     = "tvc-dev-videos"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " ĐANG BƠM $Count JOBS VÀO SQS & DYNAMODB (AWS CLOUD)" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Kiểm tra video mẫu trên S3
Write-Host "[1/2] Kiểm tra video mẫu trên S3 Input..." -ForegroundColor Yellow
$s3Check = aws s3 ls "s3://tvc-dev-input-060245183757/$InputKey" --profile $Profile 2>$null
if (-not $s3Check) {
    Write-Host "Chưa có video trên S3. Đang tải worker/testdata/sample-10s-720p.mp4 lên..." -ForegroundColor Yellow
    aws s3 cp "worker/testdata/sample-10s-720p.mp4" "s3://tvc-dev-input-060245183757/$InputKey" --profile $Profile
} else {
    Write-Host "Video mẫu đã tồn tại trên S3: $InputKey" -ForegroundColor Green
}

# 2. Bơm N jobs vào SQS và DynamoDB
Write-Host "[2/2] Bơm $Count jobs vào hệ thống..." -ForegroundColor Yellow
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

for ($i = 1; $i -le $Count; $i++) {
    $JobId   = "job_scale_$i"
    $VideoId = "vid_scale_$i"
    $Now     = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    
    # Tạo JSON cho DynamoDB Item
    $itemJson = @{
        videoId     = @{ S = $VideoId }
        projectId   = @{ S = $ProjectId }
        status      = @{ S = "PENDING" }
        originalKey = @{ S = $InputKey }
        createdAt   = @{ S = $Now }
    } | ConvertTo-Json -Compress
    
    # Tạo JSON cho SQS Message Body
    $bodyJson = @{
        schemaVersion = 1
        jobId         = $JobId
        videoId       = $VideoId
        projectId     = $ProjectId
        inputKey      = $InputKey
    } | ConvertTo-Json -Compress
    
    # Ghi DynamoDB qua file tạm UTF-8 không có BOM (tránh lỗi ký tự lạ ï»¿)
    $tempItemFile = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tempItemFile, $itemJson, $utf8NoBom)
    $fileUriItem = "file://" + $tempItemFile.Replace('\', '/')
    aws dynamodb put-item --table-name $Table --item $fileUriItem --profile $Profile > $null
    Remove-Item $tempItemFile -Force -ErrorAction SilentlyContinue
    
    # Gửi SQS Message qua file tạm UTF-8 không có BOM
    $tempBodyFile = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tempBodyFile, $bodyJson, $utf8NoBom)
    $fileUriBody = "file://" + $tempBodyFile.Replace('\', '/')
    aws sqs send-message --queue-url $QueueUrl --message-body $fileUriBody --profile $Profile > $null
    Remove-Item $tempBodyFile -Force -ErrorAction SilentlyContinue
    
    Write-Host "  -> [$i/$Count] Đã đẩy $JobId ($VideoId) thành công." -ForegroundColor Green
}

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " HOÀN TẤT! Đã đẩy đủ $Count jobs vào queue." -ForegroundColor Cyan
Write-Host " Hãy mở AWS Console để xem Auto-scaling co giãn:" -ForegroundColor Yellow
Write-Host "  1. SQS: https://console.aws.amazon.com/sqs"
Write-Host "  2. EC2 ASG: https://console.aws.amazon.com/ec2autoscaling"
Write-Host "==================================================" -ForegroundColor Cyan
