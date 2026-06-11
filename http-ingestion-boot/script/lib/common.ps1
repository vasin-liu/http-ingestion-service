# Shared runtime helpers for HTTP Ingestion Service packaged deployments (Windows PowerShell).
# Dot-sourced by run.ps1, health-check.ps1.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Hi-Now { Get-Date -Format 'yyyy-MM-dd HH:mm:ss' }

function Hi-Log {
    param(
        [Parameter(Mandatory)][string]$Level,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$Message
    )
    $text = ($Message -join ' ')
    Write-Host "[$(Hi-Now)] [$Level] $text"
}

function Hi-LogInfo([string]$Message) { Hi-Log -Level 'INFO' $Message }
function Hi-LogWarn([string]$Message) { Hi-Log -Level 'WARN' $Message }
function Hi-LogError([string]$Message) { Hi-Log -Level 'ERROR' $Message }

function Hi-EnsureDir([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Hi-ResolvePathFromRoot([string]$Path) {
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return (Join-Path $script:RootDir ($Path -replace '^\.[\\/]', ''))
}

function Hi-InitPaths([string]$BinDir) {
    if ($env:HI_BIN_DIR) {
        $script:BinDir = (Resolve-Path -LiteralPath $env:HI_BIN_DIR).Path
    } else {
        $script:BinDir = (Resolve-Path -LiteralPath $BinDir).Path
    }
    if ($env:HI_PACKAGE_ROOT) {
        $script:RootDir = (Resolve-Path -LiteralPath $env:HI_PACKAGE_ROOT).Path
    } else {
        $script:RootDir = (Resolve-Path -LiteralPath (Join-Path $script:BinDir '..')).Path
    }
    if (-not $env:HI_LOG_DIR) { $script:LogDir = Join-Path $script:RootDir 'logs' } else { $script:LogDir = Hi-ResolvePathFromRoot $env:HI_LOG_DIR }
    $script:ConfDir = Join-Path $script:RootDir 'conf'
    if (-not $env:HI_DATA_DIR) { $script:DataDir = Join-Path $script:RootDir 'data' } else { $script:DataDir = Hi-ResolvePathFromRoot $env:HI_DATA_DIR }
    if (-not $env:HI_JVMDUMP_DIR) { $script:JvmDumpDir = Join-Path $script:RootDir 'jvmdump' } else { $script:JvmDumpDir = Hi-ResolvePathFromRoot $env:HI_JVMDUMP_DIR }
    if (-not $env:HI_SERVICE_NAME) { $script:ServiceName = 'http-ingestion-service' } else { $script:ServiceName = $env:HI_SERVICE_NAME }
    if (-not $env:HI_LOG_FILE) { $script:LogFile = Join-Path $script:LogDir "$($script:ServiceName).log" } else { $script:LogFile = Hi-ResolvePathFromRoot $env:HI_LOG_FILE }
    if (-not $env:HI_PID_FILE) { $script:PidFile = Join-Path $script:RootDir "$($script:ServiceName).pid" } else { $script:PidFile = Hi-ResolvePathFromRoot $env:HI_PID_FILE }
    if (-not $env:HI_SERVER_PORT) { $script:ServerPort = '8080' } else { $script:ServerPort = $env:HI_SERVER_PORT }
    if (-not $env:HI_HEALTH_URL) { $script:HealthUrl = "http://127.0.0.1:$($script:ServerPort)/actuator/health" } else { $script:HealthUrl = $env:HI_HEALTH_URL }
    if (-not $env:HI_START_WAIT_SEC) { $script:StartWaitSec = 30 } else { $script:StartWaitSec = [int]$env:HI_START_WAIT_SEC }
    if (-not $env:HI_STOP_TIMEOUT_SEC) { $script:StopTimeoutSec = 60 } else { $script:StopTimeoutSec = [int]$env:HI_STOP_TIMEOUT_SEC }
    if (-not $env:HI_DAEMON) { $script:Daemon = '1' } else { $script:Daemon = $env:HI_DAEMON }
    if (-not $env:HI_JAVA_MIN_VERSION) { $script:JavaMinVersion = 21 } else { $script:JavaMinVersion = [int]$env:HI_JAVA_MIN_VERSION }
    if (-not $env:HI_HEAP_MIN) { $script:HeapMin = '512m' } else { $script:HeapMin = $env:HI_HEAP_MIN }
    if (-not $env:HI_HEAP_MAX) { $script:HeapMax = '1g' } else { $script:HeapMax = $env:HI_HEAP_MAX }
    if (-not $env:HI_META_DB_PATH) { $script:MetaDbPath = $script:DataDir } else { $script:MetaDbPath = Hi-ResolvePathFromRoot $env:HI_META_DB_PATH }
}

function Hi-ApplyRuntimeEnv {
    [System.Environment]::SetEnvironmentVariable('SERVER_PORT', $script:ServerPort, 'Process')
    [System.Environment]::SetEnvironmentVariable('META_DB_PATH', $script:MetaDbPath, 'Process')
    if ($env:EXTERNAL_PG_URL) { [System.Environment]::SetEnvironmentVariable('EXTERNAL_PG_URL', $env:EXTERNAL_PG_URL, 'Process') }
    if ($env:EXTERNAL_PG_USER) { [System.Environment]::SetEnvironmentVariable('EXTERNAL_PG_USER', $env:EXTERNAL_PG_USER, 'Process') }
    if ($env:EXTERNAL_PG_PASSWORD) { [System.Environment]::SetEnvironmentVariable('EXTERNAL_PG_PASSWORD', $env:EXTERNAL_PG_PASSWORD, 'Process') }
}

function Hi-LoadConfig {
    $envFile = Join-Path $script:RootDir 'conf\service.env'
    if (Test-Path -LiteralPath $envFile) {
        Hi-LoadEnvFile $envFile
    } elseif (Test-Path -LiteralPath (Join-Path $script:RootDir 'scripts\env')) {
        Hi-LoadEnvFile (Join-Path $script:RootDir 'scripts\env')
    }
    Hi-InitPaths $script:BinDir
}

function Hi-LoadEnvFile([string]$EnvFile) {
    Hi-LogInfo "Loading configuration: $EnvFile"
    Get-Content -LiteralPath $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq '' -or $line.StartsWith('#')) { return }
        $idx = $line.IndexOf('=')
        if ($idx -le 0) { return }
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        if ($value.StartsWith('"') -and $value.EndsWith('"')) { $value = $value.Substring(1, $value.Length - 2) }
        switch ($key) {
            'JAVA_HOME' { if (-not $env:HI_JAVA_HOME) { $env:HI_JAVA_HOME = $value } }
            'SERVER_PORT' { if (-not $env:HI_SERVER_PORT) { $env:HI_SERVER_PORT = $value } }
            'META_DB_PATH' { if (-not $env:HI_META_DB_PATH) { $env:HI_META_DB_PATH = $value } }
            'JAVA_OPTS' { if (-not $env:HI_JVM_OPTS) { $env:HI_JVM_OPTS = $value } }
            'START_TIMEOUT' { if (-not $env:HI_START_WAIT_SEC) { $env:HI_START_WAIT_SEC = $value } }
            'STOP_TIMEOUT' { if (-not $env:HI_STOP_TIMEOUT_SEC) { $env:HI_STOP_TIMEOUT_SEC = $value } }
            'HEALTH_PATH' {
                if (-not $env:HI_HEALTH_URL) {
                    $serverHost = if ($env:SERVER_HOST) { $env:SERVER_HOST } else { '127.0.0.1' }
                    $port = if ($env:HI_SERVER_PORT) { $env:HI_SERVER_PORT } elseif ($env:SERVER_PORT) { $env:SERVER_PORT } else { '8080' }
                    $env:HI_HEALTH_URL = "http://${serverHost}:$port$value"
                }
            }
            default {
                [System.Environment]::SetEnvironmentVariable($key, $value, 'Process')
            }
        }
    }
}

function Hi-JavaMajorVersion([string]$JavaExe) {
    $output = & $JavaExe -version 2>&1 | Out-String
    if ($output -match 'version "([^"]+)"') {
        $ver = $Matches[1]
        $parts = $ver.Split('.')
        if ($parts[0] -eq '1') { return [int]$parts[1] }
        return [int]$parts[0]
    }
    return 0
}

function Hi-ResolveJava {
    $candidates = @()
    if ($env:HI_JAVA_HOME) { $candidates += (Join-Path $env:HI_JAVA_HOME 'bin\java.exe') }
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $pathJava = $null
    try { $pathJava = (Get-Command java -ErrorAction Stop).Source } catch { }
    if ($pathJava) { $candidates += $pathJava }

    foreach ($java in $candidates | Select-Object -Unique) {
        if (-not (Test-Path -LiteralPath $java)) { continue }
        $major = Hi-JavaMajorVersion $java
        if ($major -ge $script:JavaMinVersion) {
            $script:JavaCmd = $java
            $verLine = (& $java -version 2>&1 | Select-Object -First 1)
            Hi-LogInfo "Java: $verLine"
            return
        }
        Hi-LogWarn "Skipping Java $java (version $major < $($script:JavaMinVersion))"
    }
    throw "JDK $($script:JavaMinVersion)+ required. Set HI_JAVA_HOME in conf\service.env (see service.env.example)."
}

function Hi-FindAppJar {
    $exact = Join-Path $script:BinDir "$($script:ServiceName).jar"
    if (Test-Path -LiteralPath $exact) {
        $script:AppJar = $exact
        Hi-LogInfo "Application JAR: $($script:AppJar)"
        return
    }
    $pattern = Join-Path $script:BinDir "$($script:ServiceName)-*.jar"
    $jar = Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
    if (-not $jar) { throw "Application JAR not found in $($script:BinDir)" }
    $script:AppJar = $jar.FullName
    Hi-LogInfo "Application JAR: $($script:AppJar)"
}

function Hi-BuildSpringArgs {
    $args = @()
    if (Test-Path -LiteralPath $script:ConfDir) {
        $args += "--spring.config.additional-location=$($script:ConfDir)/"
    }
    if ($env:HI_SPRING_PROFILES_ACTIVE) { $args += "--spring.profiles.active=$($env:HI_SPRING_PROFILES_ACTIVE)" }
    $logback = Join-Path $script:ConfDir 'logback-spring.xml'
    if (Test-Path -LiteralPath $logback) { $args += "--logging.config=$logback" }
    if ($script:ServerPort) { $args += "--server.port=$($script:ServerPort)" }
    if ($env:HI_SPRING_ARGS) { $args += ($env:HI_SPRING_ARGS -split '\s+') }
    return ,$args
}

function Hi-BuildJvmArgs {
    $args = @()
    if ($script:Daemon -eq '0') {
        $args += '-XX:MaxRAMPercentage=95.0'
    } else {
        $args += @("-Xms$($script:HeapMin)", "-Xmx$($script:HeapMax)", '-XX:MetaspaceSize=256m', '-XX:MaxMetaspaceSize=256m')
    }
    $args += @('-XX:+UseG1GC', '-XX:+HeapDumpOnOutOfMemoryError', "-XX:HeapDumpPath=$(Join-Path $script:JvmDumpDir "$($script:ServiceName).hprof")")
    if ($env:HI_JVM_OPTS) { $args += ($env:HI_JVM_OPTS -split '\s+') }
    return ,$args
}

function Hi-ReadPid {
    if (-not (Test-Path -LiteralPath $script:PidFile)) { return $null }
    $text = (Get-Content -LiteralPath $script:PidFile -Raw).Trim()
    if ($text -match '^\d+$') { return [int]$text }
    return $null
}

function Hi-IsRunning([Nullable[int]]$Pid) {
    if (-not $Pid) { return $false }
    return $null -ne (Get-Process -Id $Pid -ErrorAction SilentlyContinue)
}

function Hi-ServiceStatus {
    $pidVal = Hi-ReadPid
    if (Hi-IsRunning $pidVal) {
        Hi-LogInfo "$($script:ServiceName) is running (pid=$pidVal)"
        Hi-LogInfo "Log file: $($script:LogFile)"
        Hi-LogInfo "Health URL: $($script:HealthUrl)"
        Hi-LogInfo "Meta DB path: $($script:MetaDbPath)"
        return
    }
    if ($pidVal) {
        Hi-LogWarn "Removing stale PID file: $($script:PidFile) (pid=$pidVal)"
        Remove-Item -LiteralPath $script:PidFile -Force -ErrorAction SilentlyContinue
    }
    Hi-LogInfo "$($script:ServiceName) is not running"
    exit 1
}

function Hi-HealthCheckQuiet {
    try {
        $resp = Invoke-WebRequest -Uri $script:HealthUrl -UseBasicParsing -TimeoutSec 8
        if ($resp.StatusCode -ne 200) { return $false }
        return ($resp.Content -match '"status"\s*:\s*"UP"')
    } catch {
        return $false
    }
}

function Hi-HealthCheck {
    Hi-LogInfo "Checking $($script:HealthUrl) ..."
    try {
        $resp = Invoke-WebRequest -Uri $script:HealthUrl -UseBasicParsing -TimeoutSec 10
    } catch {
        Hi-LogError "Health check failed: $($_.Exception.Message)"
        exit 1
    }
    if ($resp.StatusCode -ne 200) {
        Hi-LogError "Health check failed: HTTP $($resp.StatusCode)"
        exit 1
    }
    if ($resp.Content -notmatch '"status"\s*:\s*"UP"') {
        Hi-LogError "Health check failed: response status is not UP"
        Hi-LogError "Body: $($resp.Content)"
        exit 1
    }
    Hi-LogInfo "Health check OK: $($resp.Content)"
}

function Hi-ServiceStart {
    $pidVal = Hi-ReadPid
    if (Hi-IsRunning $pidVal) {
        Hi-LogInfo "$($script:ServiceName) already running (pid=$pidVal)"
        return
    }
    if ($pidVal) { Remove-Item -LiteralPath $script:PidFile -Force -ErrorAction SilentlyContinue }

    Hi-EnsureDir $script:LogDir
    Hi-EnsureDir $script:DataDir
    Hi-EnsureDir $script:MetaDbPath
    Hi-EnsureDir $script:JvmDumpDir
    Hi-ResolveJava
    Hi-FindAppJar
    Hi-ApplyRuntimeEnv
    $jvm = Hi-BuildJvmArgs
    $spring = Hi-BuildSpringArgs
    $cmdArgs = @($jvm) + @('-jar', $script:AppJar) + $spring

    if ($script:Daemon -eq '0') {
        Hi-LogInfo "Starting $($script:ServiceName) in foreground ..."
        Hi-LogInfo 'Press Ctrl+C to stop.'
        & $script:JavaCmd @cmdArgs
        return
    }

    Hi-LogInfo "Starting $($script:ServiceName) in background ..."
    Hi-LogInfo "Stdout/stderr -> $($script:LogFile)"
    $proc = Start-Process -FilePath $script:JavaCmd -ArgumentList $cmdArgs -RedirectStandardOutput $script:LogFile -RedirectStandardError $script:LogFile -PassThru -WindowStyle Hidden
    Set-Content -LiteralPath $script:PidFile -Value $proc.Id -NoNewline
    Hi-LogInfo "Started with pid=$($proc.Id)"

    for ($i = 0; $i -lt $script:StartWaitSec; $i++) {
        Start-Sleep -Seconds 1
        if (Hi-HealthCheckQuiet) {
            Hi-LogInfo "Health check passed: $($script:HealthUrl)"
            return
        }
    }
    Hi-LogWarn "Process started but health check not yet OK ($($script:HealthUrl))"
    Hi-LogWarn "Tail logs: Get-Content -Wait $($script:LogFile)"
}

function Hi-ServiceStop {
    $pidVal = Hi-ReadPid
    if (-not (Hi-IsRunning $pidVal)) {
        Hi-LogInfo "$($script:ServiceName) is not running"
        Remove-Item -LiteralPath $script:PidFile -Force -ErrorAction SilentlyContinue
        return
    }
    Hi-LogInfo "Stopping $($script:ServiceName) (pid=$pidVal) ..."
    Stop-Process -Id $pidVal -ErrorAction SilentlyContinue
    $waited = 0
    while ((Hi-IsRunning $pidVal) -and ($waited -lt $script:StopTimeoutSec)) {
        Start-Sleep -Seconds 1
        $waited++
    }
    if (Hi-IsRunning $pidVal) {
        Hi-LogWarn "Graceful stop timed out; forcing stop"
        Stop-Process -Id $pidVal -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $script:PidFile -Force -ErrorAction SilentlyContinue
    Hi-LogInfo "$($script:ServiceName) stopped"
}

function Hi-ServiceRestart {
    Hi-ServiceStop
    Hi-ServiceStart
}

function Hi-PrintUsage {
    @"

HTTP Ingestion Service runtime script (Windows)
  Package root : $($script:RootDir)
  Service      : $($script:ServiceName)
  Config file  : $(Join-Path $script:RootDir 'conf\service.env')

Commands:
  start [0|1]   Start service (1=background default, 0=foreground)
  stop          Stop service
  restart       Restart service
  status        Show running state
  health        Run HTTP health check
  help          Show this help

"@
}

function Hi-Main {
    param([string[]]$CommandArgs)
    $action = if ($CommandArgs.Count -gt 0) { $CommandArgs[0].ToLowerInvariant() } else { 'help' }
    if ($CommandArgs.Count -gt 1 -and ($CommandArgs[1] -eq '0' -or $CommandArgs[1] -eq '1')) {
        $env:HI_DAEMON = $CommandArgs[1]
    }
    if ($env:HI_FOREGROUND -eq '1') { $env:HI_DAEMON = '0' }

    switch ($action) {
        'start' { Hi-ServiceStart }
        'stop' { Hi-ServiceStop }
        'restart' { Hi-ServiceRestart }
        'status' { Hi-ServiceStatus }
        'health' { Hi-HealthCheck }
        'help' { Hi-PrintUsage }
        default {
            Hi-LogError "Unknown command: $action"
            Hi-PrintUsage
            exit 1
        }
    }
}
