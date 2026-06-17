param(
    [string]$ConfigPath = (Join-Path $PSScriptRoot "pilot-openapi.config.json"),
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Wait-Healthy([string]$Url, [int]$TimeoutSec = 180) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw "Service not healthy at $Url within ${TimeoutSec}s"
}

function Resolve-RepoPath([string]$RelativePath) {
    $root = Resolve-Path (Join-Path $PSScriptRoot "../..")
    return Join-Path $root $RelativePath
}

function Build-ConnectorImportBody(
    $Operation,
    $Row,
    [string]$ServerUrlOverride
) {
    $config = @{
        http         = $Operation.httpConfig
        transform    = @{
            input_root = $(if ($Operation.suggestedInputRoot) { $Operation.suggestedInputRoot } else { "$" })
            steps      = @(
                @{
                    type     = "map_fields"
                    mappings = @()
                }
            )
        }
        openapi_meta = @{
            request_schema  = $Operation.requestSchema
            response_schema = $Operation.responseSchema
            operation_id    = $Operation.operationId
            path            = $Operation.path
            method          = $Operation.method
        }
        sink         = @{
            type       = "postgresql"
            target     = @{ schema = "public"; table = $Row.sinkTable }
            keys       = @($Row.sinkKeys)
            write_mode = "upsert"
            batch_size = 500
        }
        pagination   = @{}
        incremental  = @{ enabled = $false }
        schedule     = @{
            enabled    = $true
            type       = "cron"
            expression = $Row.cron
        }
    }

    if ($Operation.suggestedPagination) {
        $config.pagination = $Operation.suggestedPagination
    } else {
        $config.pagination = @{
            strategy        = "page_page_size"
            location        = "query"
            page_param      = "page"
            page_size_param = "page_size"
            page_start      = 1
            page_size       = 100
            max_pages       = 1000
            total_count     = @{ source = "none"; json_path = "" }
        }
    }

    if ($Row.incremental) {
        $config.incremental = $Row.incremental
    }

    if ($ServerUrlOverride) {
        $uri = [Uri]$Operation.httpConfig.url
        $path = $uri.AbsolutePath
        if (-not $path -or $path -eq "/") { $path = $Operation.path }
        $config.http.url = ($ServerUrlOverride.TrimEnd("/") + $path)
    }

    $name = if ($Operation.summary) { $Operation.summary } else { "$($Operation.method) $($Operation.path)" }

    return @{
        id                 = $Row.connectorId
        name               = $name
        mode               = "pull"
        config             = $config
        overwrite          = $true
        publishAfterImport = $true
    }
}

if (-not (Test-Path $ConfigPath)) {
    throw "Config not found: $ConfigPath. Copy pilot-openapi.config.example.json to pilot-openapi.config.json and edit."
}

$config = Get-Content $ConfigPath -Raw | ConvertFrom-Json
if ($config.serviceBaseUrl) { $BaseUrl = $config.serviceBaseUrl }

Wait-Healthy "$BaseUrl/actuator/health"

$parseBody = @{}
if ($config.specUrl) {
    $parseBody.specUrl = $config.specUrl
} elseif ($config.specPath) {
    $specFile = Resolve-RepoPath $config.specPath
    if (-not (Test-Path $specFile)) { throw "OpenAPI spec not found: $specFile" }
    $parseBody.spec = Get-Content $specFile -Raw
} else {
    throw "Config must set specPath or specUrl"
}

Write-Host "[pilot-openapi] Parsing OpenAPI..."
$parsed = Invoke-RestMethod -Uri "$BaseUrl/api/openapi/parse" -Method Post `
    -Body ($parseBody | ConvertTo-Json -Depth 5) -ContentType "application/json; charset=utf-8"

$imported = @()
foreach ($row in $config.operations) {
    $op = $parsed.operations | Where-Object { $_.operationId -eq $row.operationId } | Select-Object -First 1
    if (-not $op) {
        throw "Operation not found in spec: $($row.operationId)"
    }

    $importBody = Build-ConnectorImportBody -Operation $op -Row $row -ServerUrlOverride $config.serverUrlOverride
    Invoke-RestMethod -Uri "$BaseUrl/api/connectors/import" -Method Post `
        -Body ($importBody | ConvertTo-Json -Depth 40) -ContentType "application/json; charset=utf-8" | Out-Null
    Write-Host "[pilot-openapi] published $($row.connectorId) ($($row.operationId))"

    if ($row.triggerFullSync -ne $false) {
        try {
            Invoke-RestMethod -Uri "$BaseUrl/api/connectors/$($row.connectorId)/sync?type=full" -Method Post | Out-Null
            Write-Host "[pilot-openapi] triggered full sync: $($row.connectorId)"
        } catch {
            Write-Warning "[pilot-openapi] full sync failed for $($row.connectorId): $_"
        }
    }

    $imported += $row.connectorId
}

Write-Host ""
Write-Host "OpenAPI pilot connectors are ready."
Write-Host "  UI      : $BaseUrl"
Write-Host "  Report  : docs/ops/pilot-report-openapi-2026-06.md"
Write-Host "  Daily   : .\scripts\pilot\collect-daily-metrics.ps1"
Write-Host "  Imported: $($imported -join ', ')"
Write-Host ""
