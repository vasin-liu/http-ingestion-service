param(
    [switch]$TryRepair
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")

function Write-Status([string]$Label, [bool]$Ok, [string]$Detail = "") {
    $mark = if ($Ok) { "[OK]" } else { "[FAIL]" }
    $line = "$mark $Label"
    if ($Detail) {
        $line += " — $Detail"
    }
    Write-Host $line
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
    $output = wsl -d podman-machine-default -- test -r $WslPath 2>&1
    return $LASTEXITCODE -eq 0
}

function Restart-PodmanMachine {
    Write-Host "Restarting podman-machine-default..."
    podman machine stop | Out-Null
    podman machine start | Out-Null
    Start-Sleep -Seconds 3
}

Write-Host "Podman deploy doctor"
Write-Host "Project: $Root"
Write-Host ""

$hasPodman = [bool](Get-Command podman -ErrorAction SilentlyContinue)
Write-Status "podman CLI" $hasPodman
if (-not $hasPodman) {
    exit 1
}

$machineList = podman machine list --format json | ConvertFrom-Json
$defaultMachine = $machineList | Where-Object { $_.Name -eq "podman-machine-default" } | Select-Object -First 1
$machineRunning = $defaultMachine -and $defaultMachine.Running
Write-Status "podman-machine-default running" $machineRunning $(if ($defaultMachine) { "LastUp: $($defaultMachine.LastUp)" } else { "machine not found" })

$jarPath = Join-Path $Root "http-ingestion-boot\target\http-ingestion-service.jar"
Write-Status "application jar" (Test-Path $jarPath) $jarPath

$initSqlPath = Join-Path $Root "deploy\init-pg.sql"
Write-Status "deploy/init-pg.sql" (Test-Path $initSqlPath) $initSqlPath

$wslProject = Get-WslProjectPath $Root
$wslInitSql = "$wslProject/deploy/init-pg.sql"
$wslJar = "$wslProject/http-ingestion-boot/target/http-ingestion-service.jar"

$drvfsOk = $false
if ($machineRunning) {
    $drvfsOk = (Test-DrvfsReadable $wslInitSql) -and (Test-DrvfsReadable $wslJar)
    Write-Status "WSL drvfs can read deploy mounts" $drvfsOk "paths: $wslInitSql"
}

if (-not $drvfsOk -and $TryRepair -and $machineRunning) {
    Restart-PodmanMachine
    $drvfsOk = (Test-DrvfsReadable $wslInitSql) -and (Test-DrvfsReadable $wslJar)
    Write-Status "WSL drvfs after machine restart" $drvfsOk
}

if (-not $drvfsOk) {
    Write-Host ""
    Write-Host "Drvfs I/O errors usually mean Podman Machine's Windows mount is stale."
    Write-Host "Fix:"
    Write-Host "  podman machine stop"
    Write-Host "  podman machine start"
    Write-Host "Or rerun:"
    Write-Host "  .\scripts\podman\doctor.ps1 -TryRepair"
    Write-Host ""
    Write-Host "If it keeps failing, clone the repo under C:\ or inside WSL (~/code/...)."
    exit 1
}

try {
    $health = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 3
    Write-Status "service health @ :8080" ($health.StatusCode -eq 200)
} catch {
    Write-Status "service health @ :8080" $false "not running (expected before first deploy)"
}

Write-Host ""
Write-Host "Doctor checks passed. Deploy with:"
Write-Host "  .\scripts\podman\deploy.ps1 -SkipBuild"
