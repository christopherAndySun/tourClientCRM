$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location (Join-Path $root "crm-web")
npm run dev -- --host 0.0.0.0

