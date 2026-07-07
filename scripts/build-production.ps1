param(
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$webDir = Join-Path $root "crm-web"
$serverDir = Join-Path $root "crm-server"
$toolsDir = Join-Path $root "tools"
$mavenHome = Join-Path $toolsDir "apache-maven-3.9.10"

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

Use-LocalJavaIfAvailable

$mvn = if (Test-Path (Join-Path $mavenHome "bin\mvn.cmd")) {
    Join-Path $mavenHome "bin\mvn.cmd"
} else {
    "mvn.cmd"
}

$jarTool = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\jar.exe"))) {
    Join-Path $env:JAVA_HOME "bin\jar.exe"
} else {
    "jar.exe"
}

Write-Host "Installing frontend dependencies..."
Set-Location $webDir
npm ci

Write-Host "Building frontend static files..."
npm run build

Write-Host "Packaging backend jar..."
Set-Location $serverDir
$mavenArgs = @("clean", "package")
if ($SkipTests) {
    $mavenArgs += "-DskipTests"
}
& $mvn @mavenArgs

$jar = Get-ChildItem -Path (Join-Path $serverDir "target") -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*.original" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jar) {
    throw "Backend jar was not generated."
}

$frontendStatic = Join-Path $serverDir "target\frontend-static"
if (Test-Path $frontendStatic) {
    Remove-Item -LiteralPath $frontendStatic -Recurse -Force
}
$staticDir = Join-Path $frontendStatic "BOOT-INF\classes\static"
New-Item -ItemType Directory -Path $staticDir | Out-Null
Copy-Item -Path (Join-Path $webDir "dist\*") -Destination $staticDir -Recurse -Force

Write-Host "Embedding frontend dist into jar..."
& $jarTool "uf" $jar.FullName "-C" $frontendStatic "BOOT-INF/classes/static"

Write-Host ""
Write-Host "Production jar ready:"
Write-Host $jar.FullName
Write-Host ""
Write-Host "Start it with:"
Write-Host "powershell -ExecutionPolicy Bypass -File scripts\start-production.ps1 -JarPath `"$($jar.FullName)`""
