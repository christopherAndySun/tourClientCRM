$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$jdkHome = Get-ChildItem -LiteralPath (Join-Path $root "tools") -Directory -Filter "zulu*" | Select-Object -First 1 -ExpandProperty FullName
$mavenHome = Join-Path $root "tools\apache-maven-3.9.10"

$env:JAVA_HOME = $jdkHome
$env:Path = (Join-Path $jdkHome "bin") + ";" + (Join-Path $mavenHome "bin") + ";" + $env:Path

Set-Location (Join-Path $root "crm-server")
& (Join-Path $mavenHome "bin\mvn.cmd") spring-boot:run

