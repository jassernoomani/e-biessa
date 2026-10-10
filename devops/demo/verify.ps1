# Read-only deployment checks; never sends requests to the existing port 8080 API.
$ErrorActionPreference = 'Stop'
$origin = 'http://localhost:4280'
$config = Invoke-WebRequest "$origin/runtime-config.js" -UseBasicParsing
if ($config.Content -notmatch 'apiBaseUrl: "/api"') { throw 'Wrong runtime API configuration' }
$html = Invoke-WebRequest $origin -UseBasicParsing
if ($html.Content -notmatch 'runtime-config.js') { throw 'Frontend image is too old' }
$upstreams = @{}
1..12 | ForEach-Object {
    $response = Invoke-WebRequest "$origin/api/categories" -UseBasicParsing
    $null = $response.Content | ConvertFrom-Json
    $upstreams[[string]$response.Headers['X-Demo-Upstream']] = $true
}
if ($upstreams.Count -lt 2) { throw 'Did not observe both API replicas' }
$targets = Invoke-RestMethod 'http://localhost:19090/api/v1/targets'
$expected = @('api-1:8080', 'api-2:8080', 'ai:8001')
foreach ($instance in $expected) {
    $target = $targets.data.activeTargets | Where-Object { $_.labels.instance -eq $instance }
    if (!$target -or $target.health -ne 'up') { throw "Monitoring target not healthy: $instance" }
}
$grafana = Invoke-RestMethod 'http://localhost:13000/api/health'
if ($grafana.database -ne 'ok') { throw 'Grafana is not healthy' }
Write-Output 'PASS: runtime isolation, two responding replicas, Prometheus targets, Grafana health.'
Write-Output 'Login, messaging, AI inference and outage recovery still require the documented functional checks.'
