# Run from any directory. Requires Docker Compose; no host AWS CLI needed.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '../..')

function Invoke-LocalAws {
    param([string[]]$AwsArguments)
    $result = & docker compose exec -T localstack awslocal @AwsArguments --output json
    if ($LASTEXITCODE -ne 0) { throw "LocalStack command failed: $($AwsArguments -join ' ')" }
    return (($result -join "`n") | ConvertFrom-Json)
}

function Assert-Cloud {
    param([bool]$Condition, [string]$Description)
    if (-not $Condition) { throw "FAIL: $Description" }
    Write-Host "OK: $Description"
}

foreach ($bucket in 'frontend', 'input', 'output') {
    $null = Invoke-LocalAws @('s3api', 'head-bucket', '--bucket', "tvc-local-$bucket")
    Write-Host "OK: bucket tvc-local-$bucket"
}
$cors = Invoke-LocalAws @('s3api', 'get-bucket-cors', '--bucket', 'tvc-local-input')
Assert-Cloud (@($cors.CORSRules | Where-Object { $_.AllowedMethods -contains 'PUT' -and $_.AllowedOrigins -contains '*' }).Count -gt 0) 'input CORS allows local upload'
$lifecycle = Invoke-LocalAws @('s3api', 'get-bucket-lifecycle-configuration', '--bucket', 'tvc-local-input')
Assert-Cloud (@($lifecycle.Rules | Where-Object { $_.Status -eq 'Enabled' -and $_.Expiration.Days -eq 7 -and $_.Filter.Prefix -eq '' }).Count -gt 0) 'input expires after 7 days'

$dlq = Invoke-LocalAws @('sqs', 'get-queue-url', '--queue-name', 'tvc-local-jobs-dlq')
$dlqAttributes = Invoke-LocalAws @('sqs', 'get-queue-attributes', '--queue-url', $dlq.QueueUrl, '--attribute-names', 'QueueArn')
$queue = Invoke-LocalAws @('sqs', 'get-queue-url', '--queue-name', 'tvc-local-jobs')
$attributes = (Invoke-LocalAws @('sqs', 'get-queue-attributes', '--queue-url', $queue.QueueUrl, '--attribute-names', 'All')).Attributes
Assert-Cloud ($attributes.VisibilityTimeout -eq '300') 'queue visibility 300 seconds'
Assert-Cloud ($attributes.ReceiveMessageWaitTimeSeconds -eq '20') 'queue long polling 20 seconds'
$redrive = $attributes.RedrivePolicy | ConvertFrom-Json
Assert-Cloud ($redrive.maxReceiveCount -eq '3' -and $redrive.deadLetterTargetArn -eq $dlqAttributes.Attributes.QueueArn) 'queue redrive to expected DLQ after 3 receives'

$schemas = @(
    @{ Name = 'users'; Key = 'userId'; Index = 'email-index'; IndexKey = 'email'; Sort = $null },
    @{ Name = 'projects'; Key = 'projectId'; Index = 'owner-index'; IndexKey = 'ownerId'; Sort = 'createdAt' },
    @{ Name = 'apikeys'; Key = 'keyHash'; Index = 'project-index'; IndexKey = 'projectId'; Sort = $null },
    @{ Name = 'videos'; Key = 'videoId'; Index = 'project-index'; IndexKey = 'projectId'; Sort = 'createdAt' }
)
foreach ($schema in $schemas) {
    $table = (Invoke-LocalAws @('dynamodb', 'describe-table', '--table-name', "tvc-local-$($schema.Name)")).Table
    Assert-Cloud ($table.TableStatus -eq 'ACTIVE' -and $table.BillingModeSummary.BillingMode -eq 'PAY_PER_REQUEST') "$($schema.Name) active and on-demand"
    Assert-Cloud (@($table.KeySchema).Count -eq 1 -and $table.KeySchema[0].AttributeName -eq $schema.Key -and $table.KeySchema[0].KeyType -eq 'HASH') "$($schema.Name) partition key"
    $index = @($table.GlobalSecondaryIndexes | Where-Object { $_.IndexName -eq $schema.Index })
    Assert-Cloud ($index.Count -eq 1) "$($schema.Name) GSI exists"
    $hash = @($index[0].KeySchema | Where-Object { $_.KeyType -eq 'HASH' })
    $sort = @($index[0].KeySchema | Where-Object { $_.KeyType -eq 'RANGE' })
    Assert-Cloud ($hash.Count -eq 1 -and $hash[0].AttributeName -eq $schema.IndexKey -and $index[0].Projection.ProjectionType -eq 'ALL') "$($schema.Name) GSI partition key and projection"
    if ($schema.Sort) {
        Assert-Cloud ($sort.Count -eq 1 -and $sort[0].AttributeName -eq $schema.Sort) "$($schema.Name) GSI sort key"
    } else {
        Assert-Cloud ($sort.Count -eq 0) "$($schema.Name) GSI has no sort key"
    }
}
Write-Host 'Infrastructure smoke passed. App connectivity and E2E still required for M0.'
