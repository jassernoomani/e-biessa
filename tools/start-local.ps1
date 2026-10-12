param(
    [ValidateSet('App','DevOps','All')][string]$Mode = 'App',
    [ValidateSet('','Api','Web','Ai','Ollama','Mysql')][string]$Service = '',
    [switch]$Check,
    [switch]$NoBrowser
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$apiDir = Join-Path $projectRoot 'marketplace-api'
$aiDir = Join-Path $projectRoot 'ai-service'
$webDir = Join-Path $projectRoot 'marketplace-frontend'
$python = Join-Path $aiDir '.venv\Scripts\python.exe'
$ollama = Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe'
$mysql = 'C:\xampp\mysql\bin\mysqld.exe'
$mysqlConfig = 'C:\xampp\mysql\bin\my.ini'

function Read-Setting($Directory, $Name, $Default) {
    $inherited = [Environment]::GetEnvironmentVariable($Name)
    if ($null -ne $inherited) { return $inherited }
    $file = Join-Path $Directory '.env'
    if (Test-Path -LiteralPath $file) {
        foreach ($line in Get-Content -LiteralPath $file) {
            if ($line -match ('^\s*' + [regex]::Escape($Name) + '\s*=(.*)$')) {
                return $matches[1].Trim().Trim('"').Trim("'")
            }
        }
    }
    return $Default
}
function Assert-File($Path) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Missing prerequisite: $Path" }
}
function Set-Java {
    $candidate = Join-Path $env:USERPROFILE '.jdks\ms-21.0.12'
    if (Test-Path -LiteralPath "$candidate\bin\java.exe") { $env:JAVA_HOME = $candidate }
    if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
        throw 'Install JDK 21 and set JAVA_HOME before starting Spring Boot.'
    }
}
function Port-Open([string]$HostName, [int]$Port) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($HostName, $Port)
        return ($task.Wait(700) -and $client.Connected)
    } catch { return $false } finally { $client.Dispose() }
}
function Ready($Url, $Kind) {
    try {
        if ($Kind -eq 'Web') {
            return (Invoke-WebRequest $Url -UseBasicParsing -TimeoutSec 3).Content.Contains('<app-root')
        }
        $response = Invoke-RestMethod $Url -TimeoutSec 3
        switch ($Kind) {
            'Api' { return $response.status -eq 'UP' }
            'Ai' { return $response.service -eq 'e-biessa-ai' -and $response.status -eq 'ok' }
            'Ollama' { return $null -ne $response.models }
        }
    } catch { return $false }
    return $false
}
function Docker-Ready {
    try {
        & $docker info --format '{{.ServerVersion}}' 2>$null | Out-Null
        return $LASTEXITCODE -eq 0
    } catch { return $false }
}
function Start-Worker($Name) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $out = Join-Path $logDir "$Name-$stamp.log"
    $err = Join-Path $logDir "$Name-$stamp-error.log"
    $args = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Service $Name"
    $process = Start-Process powershell.exe -ArgumentList $args -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    Write-Host "Starting $Name (launcher PID $($process.Id)). Logs: $out"
    return $process
}
function Ensure-Service($Name, $Url, [int]$Port) {
    if (Ready $Url $Name) { Write-Host "$Name is already ready."; return }
    if (Port-Open '127.0.0.1' $Port) { throw "Port $Port is occupied but $Name is not healthy. Check the existing service; nothing was stopped." }
    $process = Start-Worker $Name
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline) {
        if (Ready $Url $Name) { Write-Host "$Name is ready."; return }
        if ($process.HasExited) { throw "$Name exited. Read the latest $Name logs in $logDir" }
        Start-Sleep -Seconds 2
    }
    throw "$Name is still starting or unhealthy after five minutes. Check $logDir before retrying; the process was left running."
}

# Worker processes inherit existing configuration; no secrets are printed or rewritten.
if ($Service) {
    switch ($Service) {
        'Api' { Set-Java; Set-Location $apiDir; & .\mvnw.cmd spring-boot:run }
        'Web' { Set-Location $webDir; & node .\node_modules\@angular\cli\bin\ng.js serve --host localhost --port 4200 }
        'Ai' { Set-Location $aiDir; & $python -m uvicorn app.main:app --host 127.0.0.1 --port $env:EBIESSA_LAUNCH_AI_PORT }
        'Ollama' { & $ollama serve }
        'Mysql' { & $mysql "--defaults-file=$mysqlConfig" --console }
    }
    exit $LASTEXITCODE
}

$mutex = New-Object Threading.Mutex($false, 'Local\EbiessaLocalStartup')
$locked = $false
try {
    $locked = $mutex.WaitOne(0)
    if (!$locked) { throw 'Another E-biessa startup is already in progress.' }
    $logDir = Join-Path $projectRoot 'tmp\launcher-logs'
    $docker = (Get-Command docker -ErrorAction Stop).Source
    if ($Mode -ne 'DevOps') {
        Assert-File "$apiDir\.env"; Assert-File "$aiDir\.env"; Assert-File $python
        Assert-File "$webDir\node_modules\@angular\cli\bin\ng.js"
        Set-Java
        $null = Get-Command node -ErrorAction Stop
        $profiles = Read-Setting $apiDir 'SPRING_PROFILES_ACTIVE' 'local'
        if ($profiles -match '(^|,)\s*ha\s*(,|$)') { throw 'This launcher is for local development, not the HA profile. Use the HA/demo Compose workflow.' }
        $dbUrl = Read-Setting $apiDir 'DB_URL' 'jdbc:mysql://localhost:3306/marketplace'
        if ($dbUrl -notmatch '^jdbc:mysql://(localhost|127\.0\.0\.1):3306/') { throw 'This launcher expects local MySQL on port 3306. Start your custom database separately and adapt the launcher first.' }
        $aiUrl = [uri](Read-Setting $apiDir 'AI_SERVICE_URL' 'http://127.0.0.1:8001')
        if ($aiUrl.Host -notin @('localhost','127.0.0.1')) { throw 'AI_SERVICE_URL must point to the local machine for this launcher.' }
        $env:EBIESSA_LAUNCH_AI_PORT = [string]$aiUrl.Port
        $useOllama = (Read-Setting $aiDir 'OLLAMA_ENABLED' 'false') -eq 'true'
        if ($useOllama) {
            Assert-File $ollama
            if ((Read-Setting $aiDir 'OLLAMA_URL' 'http://127.0.0.1:11434').TrimEnd('/') -notin @('http://localhost:11434','http://127.0.0.1:11434')) { throw 'Custom OLLAMA_URL: start that service separately and adapt this launcher.' }
        }
        if (!(Port-Open '127.0.0.1' 3306)) { Assert-File $mysql; Assert-File $mysqlConfig }
    }
    if ($Mode -ne 'App') { Assert-File "$projectRoot\devops\.env" }
    if ($Check) { Write-Host 'Prerequisite checks passed. Nothing was started.'; exit 0 }
    $null = New-Item -ItemType Directory -Force -Path $logDir
    if (!(Docker-Ready)) {
        $desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
        Assert-File $desktop
        Start-Process $desktop -WindowStyle Hidden
        Write-Host 'Waiting for Docker Desktop...'
        $deadline = (Get-Date).AddMinutes(3)
        do {
            Start-Sleep -Seconds 3
            if (Docker-Ready) { break }
        } while ((Get-Date) -lt $deadline)
        if (!(Docker-Ready)) { throw 'Docker did not become ready. Open Docker Desktop and check WSL.' }
    }
    if ($Mode -ne 'DevOps') {
        if (!(Port-Open '127.0.0.1' 3306)) {
            $dbProcess = Start-Worker Mysql
            $deadline = (Get-Date).AddSeconds(60)
            while (!(Port-Open '127.0.0.1' 3306)) {
                if ($dbProcess.HasExited -or (Get-Date) -gt $deadline) { throw "MySQL did not start. Check XAMPP and $logDir" }
                Start-Sleep -Seconds 2
            }
        }
        Write-Host 'MySQL port is available; Spring Boot will verify the configured database connection.'
        if ($useOllama) { Ensure-Service Ollama 'http://127.0.0.1:11434/api/tags' 11434 }
        Ensure-Service Ai "$($aiUrl.AbsoluteUri.TrimEnd('/'))/health" $aiUrl.Port
        Ensure-Service Api 'http://127.0.0.1:8080/actuator/health' 8080
        Ensure-Service Web 'http://localhost:4200/' 4200
    }
    if ($Mode -ne 'App') {
        Write-Host 'Starting existing DevOps images (no rebuild, no image downloads)...'
        & $docker compose --env-file "$projectRoot\devops\.env" -f "$projectRoot\devops\docker-compose.devops.yml" up -d --no-build --pull never --wait --wait-timeout 300
        if ($LASTEXITCODE -ne 0) { throw 'DevOps startup failed. Check Docker container logs; existing data was retained.' }
        Write-Host 'Jenkins: http://localhost:8085 | SonarQube: http://localhost:9000'
        Write-Host 'Prometheus: http://localhost:9090 | Grafana: http://localhost:3000'
    }
    Write-Host 'Startup completed.' -ForegroundColor Green
    if (!$NoBrowser) {
        $url = if ($Mode -eq 'DevOps') { 'http://localhost:3000' } else { 'http://localhost:4200' }
        Start-Process $url
    }
} catch {
    Write-Host "Startup stopped: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'No running services were killed and no database was reset.'
    exit 1
} finally {
    if ($locked) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
