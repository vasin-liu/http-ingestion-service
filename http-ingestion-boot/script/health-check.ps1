# HTTP health check for Windows Task Scheduler and manual checks.

$BinDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $BinDir 'lib\common.ps1')
Hi-InitPaths $BinDir
Hi-LoadConfig
Hi-HealthCheck
