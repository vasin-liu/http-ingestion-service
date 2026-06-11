# Dev-mode wrapper: run from project root against http-ingestion-boot/target/*.jar

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CommandArgs
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$env:HI_PACKAGE_ROOT = $ProjectRoot
$env:HI_BIN_DIR = Join-Path $ProjectRoot 'http-ingestion-boot\target'

& (Join-Path $ProjectRoot 'http-ingestion-boot\script\run.ps1') @CommandArgs
