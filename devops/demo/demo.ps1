param(
    [ValidateSet('Start', 'Stop', 'Status', 'Pull', 'Bootstrap')]
    [string]$Action = 'Status'
)
$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '.env'
if (!(Test-Path -LiteralPath $envFile)) { throw 'Create devops/demo/.env using .env.example first.' }
$settings = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { $settings[$matches[1]] = $matches[2].Trim() }
}
if ($settings.IMAGE_TAG -notmatch '^\d+$') { throw 'IMAGE_TAG must be a specific numeric Jenkins build, not latest.' }
foreach ($key in 'DB_PASSWORD','DB_ROOT_PASSWORD','JWT_SECRET','AI_SERVICE_TOKEN','GRAFANA_PASSWORD') {
    if (!$settings[$key] -or $settings[$key].StartsWith('REPLACE_') -or $settings[$key].Length -lt 32) {
        throw "Set $key to a random value of at least 32 characters."
    }
}
# Fixed project/file paths: never act on the existing DevOps or HA lab stack.
$composeArgs = @('compose', '--project-name', 'e-biessa-ghcr-demo', '--env-file', $envFile, '-f', (Join-Path $PSScriptRoot 'compose.yml'))
function Invoke-DemoCompose {
    param([string[]]$Arguments)
    & docker @composeArgs @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Demo Compose operation failed: $($Arguments -join ' ')" }
}
function Assert-DemoFrontend {
    $image = "ghcr.io/jassernoomani/e-biessa-web:$($settings.IMAGE_TAG)"
    & docker run --rm --network none --entrypoint sh $image -c 'grep -q runtime-config.js /usr/share/nginx/html/index.html'
    if ($LASTEXITCODE -ne 0) {
        throw 'This frontend image lacks runtime API configuration. Publish the correction in Jenkins and select that build before starting the demo.'
    }
}
switch ($Action) {
    'Pull' { Invoke-DemoCompose -Arguments @('pull', 'api-1', 'api-2', 'ai', 'web', 'mysql', 'redis', 'gateway'); Assert-DemoFrontend }
    'Bootstrap' {
        Assert-DemoFrontend
        $running = & docker @composeArgs ps --status running --services
        if ($LASTEXITCODE -ne 0) { throw 'Could not inspect demo services.' }
        if ($running -contains 'api-1' -or $running -contains 'api-2') { throw 'Stop the demo before schema initialization.' }
        try { Invoke-DemoCompose -Arguments @('up', '-d', '--wait', '--wait-timeout', '240', 'bootstrap') }
        finally { Invoke-DemoCompose -Arguments @('stop', 'bootstrap') }
    }
    'Start' {
        Assert-DemoFrontend
        $running = & docker @composeArgs ps --status running --services
        if ($LASTEXITCODE -ne 0) { throw 'Could not inspect demo services.' }
        if ($running -contains 'bootstrap') { throw 'Stop bootstrap before starting replicas.' }
        Invoke-DemoCompose -Arguments @('--profile', 'monitoring', 'up', '-d', '--wait', '--wait-timeout', '240')
    }
    'Stop' { Invoke-DemoCompose -Arguments @('--profile', 'monitoring', '--profile', 'bootstrap', 'stop') }
    'Status' { Invoke-DemoCompose -Arguments @('--profile', 'monitoring', '--profile', 'bootstrap', 'ps', '-a') }
}
