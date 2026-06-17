param(
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$SkipJiaduPush,
    [switch]$Force
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

function Import-PilotConnector(
    [string]$ConnectorId,
    [string]$Name,
    [string]$TemplateId,
    [string]$MockUrl,
    [string]$Cron,
    [bool]$UseDataEnvelope = $false,
    [string]$Mode = "pull"
) {
    $template = Invoke-RestMethod -Uri "$BaseUrl/api/templates/$TemplateId" -Method Get
    $config = $template.config | ConvertTo-Json -Depth 30 | ConvertFrom-Json
    $config.http.url = $MockUrl
    $config.schedule.enabled = $true
    $config.schedule.type = "cron"
    $config.schedule.expression = $Cron
    if ($UseDataEnvelope) {
        $config.transform.input_root = "$.data"
    }
    $body = @{
        id                 = $ConnectorId
        name               = $Name
        mode               = $Mode
        config             = $config
        overwrite          = $true
        publishAfterImport = $true
    } | ConvertTo-Json -Depth 30
    Invoke-RestMethod -Uri "$BaseUrl/api/connectors/import" -Method Post -Body $body -ContentType "application/json; charset=utf-8" | Out-Null
    Write-Host "[pilot] published $ConnectorId ($TemplateId)"
}

Wait-Healthy "$BaseUrl/actuator/health"

Write-Host "[pilot] Resetting mock integration data..."
Invoke-RestMethod -Uri "$BaseUrl/mock/_test/reset" -Method Post | Out-Null

$pullConnectors = @(
    @{
        Id         = "pilot-mock-pagination"
        Name       = "Pilot Mock - page/page_size"
        TemplateId = "rest-pagination"
        Cron       = "0 0/15 * * * ?"
        MockPath   = "/mock/e2e/pagination-items"
    },
    @{
        Id         = "pilot-mock-offset-limit"
        Name       = "Pilot Mock - offset/limit"
        TemplateId = "rest-offset-limit"
        Cron       = "0 5/15 * * * ?"
        MockPath   = "/mock/e2e/pagination-items"
    },
    @{
        Id         = "pilot-mock-cursor"
        Name       = "Pilot Mock - cursor"
        TemplateId = "rest-cursor"
        Cron       = "0 10/15 * * * ?"
        MockPath   = "/mock/e2e/cursor-items"
    },
    @{
        Id         = "pilot-mock-kafka"
        Name       = "Pilot Mock - Kafka"
        TemplateId = "rest-kafka"
        Cron       = "0 15/15 * * * ?"
        MockPath   = "/mock/e2e/kafka-users"
    },
    @{
        Id         = "pilot-mock-monotonic-id"
        Name       = "Pilot Mock - monotonic_id"
        TemplateId = "rest-monotonic-id"
        Cron       = "0 20/15 * * * ?"
        MockPath   = "/mock/e2e/monotonic-items"
    },
    @{
        Id         = "pilot-mock-rolling-window"
        Name       = "Pilot Mock - rolling_window"
        TemplateId = "rest-rolling-window"
        Cron       = "0 25/15 * * * ?"
        MockPath   = "/mock/e2e/window-items"
    }
)

foreach ($row in $pullConnectors) {
    $useDataEnvelope = ($row.TemplateId -eq "rest-offset-limit" -and $row.MockPath -eq "/mock/e2e/pagination-items")
    Import-PilotConnector `
        -ConnectorId $row.Id `
        -Name $row.Name `
        -TemplateId $row.TemplateId `
        -MockUrl "$BaseUrl$($row.MockPath)" `
        -Cron $row.Cron `
        -UseDataEnvelope $useDataEnvelope
}

if (-not $SkipJiaduPush) {
    $jiaduId = "pilot-mock-jiadu"
    $jiaduBody = @{
        id                 = $jiaduId
        name               = "Pilot Mock - Jiadu Push"
        mode               = "push"
        config             = @{
            webhook   = @{ enabled = $true; path_suffix = ""; verify_sign = $false; plat_flag = "ivsp" }
            transform = @{
                input_root = "$"
                steps      = @(
                    @{
                        type     = "map_fields"
                        mappings = @(
                            @{ target = "event_id"; source = "$.EventID"; type = "string" },
                            @{ target = "event_type"; source = "$.EventType"; type = "long" },
                            @{ target = "event_name"; source = "$.EventName"; type = "string" }
                        )
                    }
                )
            }
            sink      = @{
                type       = "postgresql"
                target     = @{ schema = "public"; table = "jiadu_event_info" }
                keys       = @("event_id")
                write_mode = "upsert"
                batch_size = 500
            }
            schedule  = @{ enabled = $false; type = "cron"; expression = "0 0/5 * * * ?" }
        }
        overwrite          = $true
        publishAfterImport = $true
    } | ConvertTo-Json -Depth 20
    Invoke-RestMethod -Uri "$BaseUrl/api/connectors/import" -Method Post -Body $jiaduBody -ContentType "application/json; charset=utf-8" | Out-Null
    Write-Host "[pilot] published $jiaduId (jiadu push)"
    $sim = Invoke-RestMethod -Uri "$BaseUrl/mock/jiadu/push/$jiaduId`?rounds=3" -Method Post
    Write-Host "[pilot] jiadu simulator sent=$($sim.sent) success=$($sim.success)"
}

# Trigger one manual full sync per Pull connector so Day 1 has baseline job runs before Cron.
foreach ($row in $pullConnectors) {
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/connectors/$($row.Id)/sync?type=full" -Method Post | Out-Null
        Write-Host "[pilot] triggered full sync: $($row.Id)"
    } catch {
        Write-Warning "[pilot] full sync failed for $($row.Id): $_"
    }
}

Write-Host ""
Write-Host "Mock pilot connectors are ready."
Write-Host "  UI     : $BaseUrl"
Write-Host "  Report : docs/ops/pilot-report-2026-06.md"
Write-Host "  Daily  : .\scripts\pilot\collect-daily-metrics.ps1"
Write-Host ""
