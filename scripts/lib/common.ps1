# Shared helpers for start/stop/status scripts (Windows PowerShell)

function Get-ProjectRoot {
    $scriptDir = Split-Path -Parent $PSScriptRoot
    return (Resolve-Path (Join-Path $scriptDir "..")).Path
}

function Load-Env {
    param(
        [string]$ProjectRoot
    )

    $envFile = Join-Path $ProjectRoot "scripts\env"
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            $line = $_.Trim()
            if ($line -eq "" -or $line.StartsWith("#")) { return }
            if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
                $name = $Matches[1]
                $value = $Matches[2].Trim().Trim('"').Trim("'")
                Set-Item -Path "env:$name" -Value $value
            }
        }
    }

    if (-not $env:SERVER_PORT) { $env:SERVER_PORT = "8080" }
    if (-not $env:SERVER_HOST) { $env:SERVER_HOST = "127.0.0.1" }
    if (-not $env:JAVA_OPTS) { $env:JAVA_OPTS = "-Xms512m -Xmx1024m" }
    if (-not $env:META_DB_PATH) { $env:META_DB_PATH = ".\data" }
    if (-not $env:JAR_PATH) { $env:JAR_PATH = ".\http-ingestion-boot\target\http-ingestion-service.jar" }
    if (-not $env:START_TIMEOUT) { $env:START_TIMEOUT = "120" }
    if (-not $env:STOP_TIMEOUT) { $env:STOP_TIMEOUT = "60" }
    if (-not $env:HEALTH_PATH) { $env:HEALTH_PATH = "/actuator/health" }

    if ($env:JAVA_HOME) {
        $javaBin = Join-Path $env:JAVA_HOME "bin"
        if (Test-Path $javaBin) {
            $env:PATH = "$javaBin;$env:PATH"
        }
    }

    $meta = $env:META_DB_PATH
    if (-not [System.IO.Path]::IsPathRooted($meta)) {
        $meta = Join-Path $ProjectRoot ($meta -replace '^\./', '')
    }

    $jar = $env:JAR_PATH
    if (-not [System.IO.Path]::IsPathRooted($jar)) {
        $jar = Join-Path $ProjectRoot ($jar -replace '^\./', '')
    }

    return [PSCustomObject]@{
        ServerPort   = [int]$env:SERVER_PORT
        ServerHost   = $env:SERVER_HOST
        JavaOpts     = $env:JAVA_OPTS
        MetaDbPath   = $meta
        JarPath      = $jar
        StartTimeout = [int]$env:START_TIMEOUT
        StopTimeout  = [int]$env:STOP_TIMEOUT
        HealthPath   = $env:HEALTH_PATH
        PidFile      = Join-Path $meta "http-ingestion.pid"
        LogFile      = Join-Path $meta "http-ingestion.log"
    }
}

function Test-ServiceRunning {
    param($Config)

    if (-not (Test-Path $Config.PidFile)) { return $false }
    $pidText = Get-Content $Config.PidFile -ErrorAction SilentlyContinue
    if (-not $pidText) { return $false }
    $proc = Get-Process -Id ([int]$pidText) -ErrorAction SilentlyContinue
    return ($null -ne $proc)
}

function Get-HealthUrl {
    param($Config)
    return "http://$($Config.ServerHost):$($Config.ServerPort)$($Config.HealthPath)"
}

function Wait-ForHealth {
    param(
        $Config,
        [int]$TimeoutSeconds
    )

    $url = Get-HealthUrl $Config
    $elapsed = 0
    while ($elapsed -lt $TimeoutSeconds) {
        try {
            $null = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
            return $true
        } catch {
            Start-Sleep -Seconds 2
            $elapsed += 2
        }
    }
    return $false
}
