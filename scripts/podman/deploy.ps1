param(
    [switch]$SkipBuild,
    [switch]$SkipDrvfsCheck
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $Root

if (-not (Get-Command podman -ErrorAction SilentlyContinue)) {
    throw "podman not found on PATH. Install Podman Desktop or Podman Machine."
}

function Get-WslProjectPath([string]$WindowsPath) {
    $resolved = (Resolve-Path $WindowsPath).Path
    if ($resolved -match '^([A-Za-z]):\\(.*)$') {
        $drive = $Matches[1].ToLower()
        $rest = $Matches[2] -replace '\\', '/'
        return "/mnt/$drive/$rest"
    }
    throw "Unsupported path for Podman Machine drvfs check: $WindowsPath"
}

function Test-DrvfsReadable([string]$WslPath) {
    wsl -d podman-machine-default -- test -r $WslPath 2>$null | Out-Null
    return $LASTEXITCODE -eq 0
}

function Ensure-DrvfsAccess() {
    $wslProject = Get-WslProjectPath $Root
    $paths = @(
        "$wslProject/deploy/init-pg.sql",
        "$wslProject/http-ingestion-boot/target/http-ingestion-service.jar"
    )
    foreach ($path in $paths) {
        if (Test-DrvfsReadable $path) {
            continue
        }
        Write-Host "[deploy] Podman Machine cannot read Windows mount: $path"
        Write-Host "[deploy] Restarting podman-machine-default to repair drvfs..."
        podman machine stop | Out-Null
        podman machine start | Out-Null
        Start-Sleep -Seconds 3
        foreach ($retryPath in $paths) {
            if (-not (Test-DrvfsReadable $retryPath)) {
                throw @"
Podman Machine still cannot read project files via /mnt (drvfs I/O error).
Run: .\scripts\podman\doctor.ps1 -TryRepair
Or: podman machine stop; podman machine start
If the issue persists, move the repo to C:\ or a WSL path (~/code/...).
"@
            }
        }
        return
    }
}

if (-not $SkipDrvfsCheck) {
    Ensure-DrvfsAccess
}

$Jar = Join-Path $Root "http-ingestion-boot\target\http-ingestion-service.jar"
if (-not $SkipBuild) {
    & (Join-Path $Root "mvnw-jdk21.ps1") "-pl" "http-ingestion-boot" "-am" "package" "-DskipTests"
}
if (-not (Test-Path $Jar)) {
    throw "Missing jar: $Jar. Run without -SkipBuild or build manually."
}

$dataDir = Join-Path $Root "data"
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null

function Wait-PostgresHealthy([string]$ComposeDir) {
    $deadline = (Get-Date).AddMinutes(2)
    while ((Get-Date) -lt $deadline) {
        $result = podman compose -f podman-compose.yml exec -T postgres pg_isready -U postgres 2>$null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "PostgreSQL did not become ready in time"
}

function Apply-PgSchema([string]$ComposeDir) {
    $initSql = Join-Path $Root "deploy\init-pg.sql"
    Write-Host "[deploy] Applying PostgreSQL schema from deploy/init-pg.sql ..."
    Push-Location $ComposeDir
    try {
        Get-Content $initSql -Raw | podman compose -f podman-compose.yml exec -T postgres psql -U postgres -d postgres
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to apply deploy/init-pg.sql"
        }
    } finally {
        Pop-Location
    }
}

Set-Location (Join-Path $Root "deploy")
podman compose -f podman-compose.yml up -d
Wait-PostgresHealthy (Get-Location)
Apply-PgSchema (Get-Location)

$healthUrl = "http://localhost:8080/actuator/health"
$deadline = (Get-Date).AddMinutes(3)
$healthy = $false
while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            $healthy = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $healthy) {
    throw "Service did not become healthy at $healthUrl within 3 minutes"
}

Write-Host ""
Write-Host "HTTP Ingestion is ready."
Write-Host "  UI/API : http://localhost:8080"
Write-Host "  Health : $healthUrl"
Write-Host "  Mock   : http://localhost:8080/mock/..."
Write-Host "  PG     : localhost:5432 (postgres/postgres)"
Write-Host "  Kafka  : localhost:9092 (in-cluster kafka:9092)"
Write-Host ""
Write-Host "Teardown: .\scripts\podman\teardown.ps1"
