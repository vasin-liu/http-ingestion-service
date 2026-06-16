param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OutFile = "",
    [string[]]$ConnectorPrefix = @("pilot-mock-")
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($OutFile)) {
    $month = Get-Date -Format "yyyy-MM"
    $OutFile = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..\..\data")).Path "pilot-metrics-$month.csv"
}

$raw = Invoke-WebRequest -Uri "$BaseUrl/actuator/prometheus" -UseBasicParsing
$lines = $raw.Content -split "`n"
$today = Get-Date -Format "yyyy-MM-dd"

function Parse-Labels([string]$labelPart) {
    $map = @{}
    foreach ($m in [regex]::Matches($labelPart, '(\w+)="([^"]*)"')) {
        $map[$m.Groups[1].Value] = $m.Groups[2].Value
    }
    return $map
}

$jobs = @{}
$lags = @{}

foreach ($line in $lines) {
    if ($line -match '^ingestion_job_total\{([^}]*)\}\s+([0-9.eE+-]+)$') {
        $labels = Parse-Labels $Matches[1]
        $connectorId = $labels["connector_id"]
        $status = $labels["status"]
        if (-not $connectorId) { continue }
        if ($ConnectorPrefix | Where-Object { $connectorId -like "$_*" }) {
            if (-not $jobs.ContainsKey($connectorId)) { $jobs[$connectorId] = @{ success = 0; failed = 0 } }
            $jobs[$connectorId][$status] = [double]$Matches[2]
        }
    }
    if ($line -match '^ingestion_watermark_lag_seconds\{([^}]*)\}\s+([0-9.eE+-]+)$') {
        $labels = Parse-Labels $Matches[1]
        $connectorId = $labels["connector_id"]
        if (-not $connectorId) { continue }
        if ($ConnectorPrefix | Where-Object { $connectorId -like "$_*" }) {
            $lags[$connectorId] = [double]$Matches[2]
        }
    }
}

$rows = @()
foreach ($id in ($jobs.Keys | Sort-Object)) {
    $success = [int]($jobs[$id]["success"])
    $failed = [int]($jobs[$id]["failed"])
    $total = $success + $failed
    $rate = if ($total -gt 0) { [math]::Round(100.0 * $success / $total, 1) } else { 0 }
    $lag = if ($lags.ContainsKey($id)) { [math]::Round($lags[$id], 1) } else { "" }
    $rows += [pscustomobject]@{
        date        = $today
        connector   = $id
        success_pct = $rate
        jobs_ok     = $success
        jobs_failed = $failed
        max_lag_s   = $lag
    }
}

if ($rows.Count -eq 0) {
    Write-Warning "No pilot connector metrics found (prefix: $($ConnectorPrefix -join ', '))"
    exit 0
}

$rows | Format-Table -AutoSize

$dir = Split-Path $OutFile -Parent
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
$header = "date,connector,success_pct,jobs_ok,jobs_failed,max_lag_s"
$exists = Test-Path $OutFile
foreach ($row in $rows) {
    $line = "{0},{1},{2},{3},{4},{5}" -f $row.date, $row.connector, $row.success_pct, $row.jobs_ok, $row.jobs_failed, $row.max_lag_s
    if (-not $exists) {
        Set-Content -Path $OutFile -Value $header -Encoding utf8
        $exists = $true
    }
    Add-Content -Path $OutFile -Value $line -Encoding utf8
}
Write-Host "[pilot] appended $($rows.Count) row(s) to $OutFile"
