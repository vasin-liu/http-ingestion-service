# Backward-compatible wrapper — use scripts/run.ps1 instead.

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CommandArgs
)

& "$PSScriptRoot\run.ps1" start @CommandArgs
