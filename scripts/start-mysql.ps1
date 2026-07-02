$ErrorActionPreference = 'Stop'

$baseDir = 'C:\Program Files\MySQL\MySQL Server 8.4'
$dataDir = 'D:\Tool\aotuTool\tourClientCRM\tools\mysql-data'
$mysqld = Join-Path $baseDir 'bin\mysqld.exe'

if (-not (Test-Path $mysqld)) {
  throw "MySQL mysqld.exe not found: $mysqld"
}

if (Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue) {
  Write-Host 'MySQL is already running on port 3306'
  exit 0
}

if (-not (Test-Path $dataDir)) {
  New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
  & $mysqld --initialize-insecure --basedir="$baseDir" --datadir="$dataDir" --console
}

Start-Process -FilePath $mysqld -ArgumentList "--basedir=`"$baseDir`" --datadir=`"$dataDir`" --port=3306 --console" -WindowStyle Hidden
Start-Sleep -Seconds 6

if (-not (Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue)) {
  throw 'MySQL failed to start on port 3306'
}

Write-Host 'MySQL started: localhost:3306'
