param(
  [ValidateSet('status', 'start', 'stop')]
  [string]$Action = 'status',
  [string]$Profile = 'tvc-dev',
  [string]$CredentialCsv = ''
)
$ErrorActionPreference = 'Stop'
$env:AWS_DEFAULT_REGION = 'ap-southeast-1'
$env:AWS_PAGER = ''
if (-not $CredentialCsv -and -not $env:AWS_ACCESS_KEY_ID -and
    -not $PSBoundParameters.ContainsKey('Profile')) {
  $localCredentialCsv = Join-Path (Split-Path -Parent $PSScriptRoot) 'tvc-dev_accessKeys.csv'
  if (Test-Path -LiteralPath $localCredentialCsv -PathType Leaf) {
    $CredentialCsv = $localCredentialCsv
  }
}
if ($CredentialCsv) {
  $credentialRow = @(Import-Csv -LiteralPath $CredentialCsv)[0]
  if ([string]::IsNullOrWhiteSpace($credentialRow.'Access key ID') -or
      [string]::IsNullOrWhiteSpace($credentialRow.'Secret access key')) {
    throw 'CSV phải có Access key ID và Secret access key không rỗng.'
  }
  $env:AWS_ACCESS_KEY_ID = $credentialRow.'Access key ID'
  $env:AWS_SECRET_ACCESS_KEY = $credentialRow.'Secret access key'
  Remove-Item Env:AWS_SESSION_TOKEN -ErrorAction SilentlyContinue
}
function Invoke-TvcAws {
  param([string[]]$Arguments)
  $profileArguments = @()
  if (-not $env:AWS_ACCESS_KEY_ID) { $profileArguments = @('--profile', $Profile) }
  $result = & aws @Arguments @profileArguments --region ap-southeast-1 --output json
  if ($LASTEXITCODE -ne 0) { throw "AWS CLI thất bại: $($Arguments[0]) $($Arguments[1])" }
  if ($result) { return ($result | ConvertFrom-Json) }
}
$api = Invoke-TvcAws -Arguments @('ec2','describe-instances','--filters','Name=tag:Name,Values=tvc-dev-api','Name=instance-state-name,Values=pending,running,stopping,stopped')
$apiInstances = @($api.Reservations | ForEach-Object { $_.Instances })
$groupResult = Invoke-TvcAws -Arguments @('autoscaling','describe-auto-scaling-groups','--auto-scaling-group-names','tvc-dev-worker-asg')
$group = @($groupResult.AutoScalingGroups)[0]
if ($Action -eq 'status') {
  $apiInstances | Select-Object InstanceId,@{Name='State';Expression={$_.State.Name}} |
    Format-Table -AutoSize | Out-String
  $group | Select-Object AutoScalingGroupName,MinSize,DesiredCapacity,MaxSize |
    Format-Table -AutoSize | Out-String
  return
}
if ($Action -eq 'start') {
  foreach ($instance in $apiInstances) {
    if ($instance.State.Name -eq 'stopped') {
      Invoke-TvcAws -Arguments @('ec2','start-instances','--instance-ids',$instance.InstanceId) | Out-Null
    }
  }
  Invoke-TvcAws -Arguments @('autoscaling','update-auto-scaling-group','--auto-scaling-group-name','tvc-dev-worker-asg','--min-size','0','--desired-capacity','1') | Out-Null
  Write-Output 'Đã yêu cầu bật API và 1 worker; cần chờ health/tunnel trước khi test.'
  return
}
$queue = Invoke-TvcAws -Arguments @('sqs','get-queue-attributes','--queue-url','https://sqs.ap-southeast-1.amazonaws.com/060245183757/tvc-dev-jobs','--attribute-names','ApproximateNumberOfMessages','ApproximateNumberOfMessagesNotVisible')
if ([int]$queue.Attributes.ApproximateNumberOfMessages -gt 0 -or [int]$queue.Attributes.ApproximateNumberOfMessagesNotVisible -gt 0) {
  throw 'Queue còn job chờ/đang xử lý. Đợi hoàn tất rồi chạy stop.'
}
foreach ($instance in @($group.Instances)) {
  if ($instance.LifecycleState -ne 'InService') { throw 'Worker đang chuyển trạng thái; đợi ổn định rồi chạy stop.' }
  $parameters = '{"commands":["if docker inspect tvc-worker >/dev/null 2>&1; then docker stop -t 60 tvc-worker; fi"],"executionTimeout":["120"]}'
  $parameterFile = [System.IO.Path]::GetTempFileName()
  try {
    [System.IO.File]::WriteAllText($parameterFile, $parameters)
    $command = Invoke-TvcAws -Arguments @('ssm','send-command','--instance-ids',$instance.InstanceId,'--document-name','AWS-RunShellScript','--parameters',("file://" + $parameterFile))
  } finally { Remove-Item -LiteralPath $parameterFile }
  $success = $false
  for ($attempt = 0; $attempt -lt 90; $attempt++) {
    Start-Sleep -Seconds 2
    $invocations = Invoke-TvcAws -Arguments @('ssm','list-command-invocations','--command-id',$command.Command.CommandId)
    $invocation = @($invocations.CommandInvocations)[0]
    if ($invocation.Status -eq 'Success') { $success = $true; break }
    if ($invocation.Status -in @('Failed','Cancelled','TimedOut')) { throw 'Không dừng được worker qua SSM.' }
  }
  if (-not $success) { throw 'Hết thời gian chờ dừng worker; chưa gỡ scale-in protection.' }
  Invoke-TvcAws -Arguments @('autoscaling','set-instance-protection','--auto-scaling-group-name','tvc-dev-worker-asg','--instance-ids',$instance.InstanceId,'--no-protected-from-scale-in') | Out-Null
}
Invoke-TvcAws -Arguments @('autoscaling','update-auto-scaling-group','--auto-scaling-group-name','tvc-dev-worker-asg','--min-size','0','--desired-capacity','0') | Out-Null
foreach ($instance in $apiInstances) {
  if ($instance.State.Name -eq 'running') {
    Invoke-TvcAws -Arguments @('ec2','stop-instances','--instance-ids',$instance.InstanceId) | Out-Null
  }
}
Write-Output 'Đã yêu cầu Worker về 0 và dừng API; S3/CloudFront/ECR/EBS vẫn được giữ.'
