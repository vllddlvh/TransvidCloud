# Read-only IAM simulation. Does not modify roles or S3 objects.
param([string]$Profile = 'tvc-dev')
$ErrorActionPreference = 'Stop'

$policyJson = & aws iam get-role-policy --role-name tvc-dev-worker --policy-name tvc-dev-worker-runtime --query PolicyDocument --profile $Profile --output json --no-cli-pager
if ($LASTEXITCODE -ne 0) { throw 'Cannot read worker runtime policy.' }
$policy = ($policyJson -join "`n") | ConvertFrom-Json
$statements = @($policy.Statement)
if ($statements.Count -eq 0 -or -not ($statements | Where-Object { $_.Sid -eq 'ReadInput' })) {
    throw 'Worker policy is empty or missing ReadInput; simulation stopped.'
}

# Each entry is actual policy JSON, not a file:// reference or an annotated
# Get-Content object. Bound JSON depth to avoid PS 5.1 metadata expansion.
$documents = [string[]]@(
    foreach ($statement in $statements) {
        $part = ConvertTo-Json -InputObject @{
            Version = '2012-10-17'
            Statement = @($statement)
        } -Depth 10 -Compress
        if ($part.Length -gt 2000) { throw 'Policy fragment exceeds observed API limit.' }
        [string]$part
    }
)
if ($documents.Count -ne $statements.Count) { throw 'Incomplete simulation request.' }
$request = @{
    PolicyInputList = $documents
    ActionNames = @('s3:GetObject', 's3:PutObject')
    ResourceArns = @('arn:aws:s3:::tvc-dev-input-975473296238-ap-southeast-2/check.txt')
}
$requestFile = Join-Path ([System.IO.Path]::GetTempPath()) ('tvc-iam-' + [guid]::NewGuid().ToString() + '.json')
try {
    [System.IO.File]::WriteAllText($requestFile, (ConvertTo-Json -InputObject $request -Depth 6 -Compress))
    Write-Host "Simulating $($documents.Count) policy fragments (inline policy only)."
    $responseJson = & aws iam simulate-custom-policy --cli-input-json "file://$requestFile" --profile $Profile --output json --no-cli-pager
    if ($LASTEXITCODE -ne 0) { throw 'IAM simulation failed.' }
    $response = ($responseJson -join "`n") | ConvertFrom-Json
    $response.EvaluationResults | Select-Object EvalActionName, EvalDecision | Format-Table -AutoSize
    $read = @($response.EvaluationResults | Where-Object { $_.EvalActionName -eq 's3:GetObject' })
    $write = @($response.EvaluationResults | Where-Object { $_.EvalActionName -eq 's3:PutObject' })
    if ($read.Count -ne 1 -or $write.Count -ne 1 -or $read[0].EvalDecision -ne 'allowed' -or $write[0].EvalDecision -ne 'implicitDeny') {
        throw 'Unexpected inline-policy simulation result. Organizations and runtime checks remain separate.'
    }
    Write-Host 'PASS: inline policy permits input read and denies input write. Organizations/runtime access remains unverified.'
} finally {
    if (Test-Path -LiteralPath $requestFile) { Remove-Item -LiteralPath $requestFile }
}
