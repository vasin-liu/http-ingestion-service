# Start/stop the HTTP Ingestion Service process.

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CommandArgs
)

$BinDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $BinDir 'lib\common.ps1')
Hi-InitPaths $BinDir
Hi-LoadConfig
Hi-Main -CommandArgs $CommandArgs
