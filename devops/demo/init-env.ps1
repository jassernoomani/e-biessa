param([Parameter(Mandatory=$true)][ValidatePattern('^\d+$')][string]$ImageTag)
$ErrorActionPreference = 'Stop'
$destination = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $destination) { throw 'Demo .env already exists; refusing to overwrite its secrets.' }
function New-DemoSecret {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
$lines = @("IMAGE_TAG=$ImageTag")
foreach ($key in 'DB_PASSWORD','DB_ROOT_PASSWORD','JWT_SECRET','AI_SERVICE_TOKEN','GRAFANA_PASSWORD') {
    $lines += "$key=$(New-DemoSecret)"
}
[System.IO.File]::WriteAllLines($destination, $lines)
Write-Output 'Created ignored devops/demo/.env with unique random secrets. No secret values printed.'
