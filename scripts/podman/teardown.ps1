$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location (Join-Path $Root "deploy")
podman compose -f podman-compose.yml down -v
Write-Host "Podman stack stopped."
