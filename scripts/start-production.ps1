param(
    [string]$JarPath = "",
    [int]$Port = 8080,
    [switch]$AllowDevDefaults
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$serverDir = Join-Path $root "crm-server"
$toolsDir = Join-Path $root "tools"

function Use-LocalJavaIfAvailable {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        return
    }
    $jdkHome = Get-ChildItem -LiteralPath $toolsDir -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
        Select-Object -First 1 -ExpandProperty FullName
    if ($jdkHome) {
        $env:JAVA_HOME = $jdkHome
        $env:Path = (Join-Path $jdkHome "bin") + ";" + $env:Path
    }
}

function Require-Env {
    param([string]$Name)
    if (-not [Environment]::GetEnvironmentVariable($Name, "Process")) {
        throw "Missing required environment variable: $Name"
    }
}

Use-LocalJavaIfAvailable

if (-not $JarPath) {
    $jar = Get-ChildItem -Path (Join-Path $serverDir "target") -Filter "*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*.original" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($jar) {
        $JarPath = $jar.FullName
    }
}

if (-not $JarPath -or -not (Test-Path $JarPath)) {
    throw "Production jar not found. Run scripts\build-production.ps1 first."
}

if (-not $AllowDevDefaults) {
    Require-Env "CRM_DB_URL"
    Require-Env "CRM_DB_USERNAME"
    Require-Env "CRM_DB_PASSWORD"
    Require-Env "CRM_UPLOAD_DIR"
    Require-Env "CRM_JWT_SECRET"
    Require-Env "CRM_SETTINGS_CRYPTO_KEY"
    if ($env:CRM_JWT_SECRET -eq "change-this-secret-to-a-long-random-value" -or $env:CRM_JWT_SECRET.Length -lt 32) {
        throw "CRM_JWT_SECRET must be a real random secret with at least 32 characters."
    }
}

$java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    "java.exe"
}

Write-Host "Starting production service from jar:"
Write-Host $JarPath
Write-Host "Port: $Port"

& $java "-jar" $JarPath "--server.port=$Port"
