param(
    [Parameter(Mandatory = $true)]
    [string]$BackupPath,
    [string]$DbUrl = $env:CRM_DB_URL,
    [string]$DbUsername = $env:CRM_DB_USERNAME,
    [string]$DbPassword = $env:CRM_DB_PASSWORD,
    [string]$UploadDir = $env:CRM_UPLOAD_DIR,
    [switch]$AllowDevDefaults,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

function Require-Value {
    param([string]$Name, [string]$Value)
    if (-not $Value) {
        throw "Missing required value: $Name"
    }
}

function Resolve-MySqlTool {
    param([string]$ToolName)
    $localTool = Join-Path $root "tools\mysql-8.4.9\PFiles64\MySQL\MySQL Server 8.4\bin\$ToolName"
    if (Test-Path $localTool) {
        return $localTool
    }
    $command = Get-Command $ToolName -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    throw "$ToolName was not found. Install MySQL client tools or keep project tools/mysql-8.4.9."
}

function Parse-JdbcMysqlUrl {
    param([string]$Url)
    if ($Url -notmatch '^jdbc:mysql://([^/:?]+)(?::([0-9]+))?/([^?]+)') {
        throw "Unsupported CRM_DB_URL. Expected jdbc:mysql://host:port/database?params"
    }
    return @{
        Host = $Matches[1]
        Port = if ($Matches[2]) { $Matches[2] } else { "3306" }
        Database = $Matches[3]
    }
}

if ($AllowDevDefaults) {
    if (-not $DbUrl) {
        $DbUrl = "jdbc:mysql://127.0.0.1:3306/tour_client_crm?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
    }
    if (-not $DbUsername) {
        $DbUsername = "root"
    }
    if (-not $DbPassword) {
        $DbPassword = "root"
    }
    if (-not $UploadDir) {
        $UploadDir = Join-Path $root "crm-server\uploads"
    }
}

Require-Value "CRM_DB_URL" $DbUrl
Require-Value "CRM_DB_USERNAME" $DbUsername
Require-Value "CRM_DB_PASSWORD" $DbPassword
Require-Value "CRM_UPLOAD_DIR" $UploadDir

if (-not (Test-Path $BackupPath)) {
    throw "Backup path not found: $BackupPath"
}

$extractDir = $BackupPath
$tempDir = $null
if ((Get-Item $BackupPath).PSIsContainer -eq $false) {
    $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("tour-crm-restore-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $tempDir | Out-Null
    Expand-Archive -Path $BackupPath -DestinationPath $tempDir -Force
    $extractDir = $tempDir
}

$fullDump = Join-Path $extractDir "db-full.sql"
$uploadsArchive = Join-Path $extractDir "uploads.zip"
if (-not (Test-Path $fullDump)) {
    throw "db-full.sql not found in backup."
}
if (-not (Test-Path $uploadsArchive)) {
    throw "uploads.zip not found in backup."
}

$db = Parse-JdbcMysqlUrl $DbUrl
$mysql = Resolve-MySqlTool "mysql.exe"

if (-not $Force) {
    Write-Host "Restore will overwrite database and upload files."
    Write-Host "Database: $($db.Database) on $($db.Host):$($db.Port)"
    Write-Host "UploadDir: $UploadDir"
    $confirm = Read-Host "Type RESTORE to continue"
    if ($confirm -ne "RESTORE") {
        throw "Restore cancelled."
    }
}

$oldMysqlPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = $DbPassword
try {
    $sourcePath = $fullDump.Replace("\", "/")
    Write-Host "Restoring MySQL database..."
    & $mysql `
        "--host=$($db.Host)" `
        "--port=$($db.Port)" `
        "--user=$DbUsername" `
        "--default-character-set=utf8mb4" `
        "--binary-mode=1" `
        "--execute=source $sourcePath"
} finally {
    $env:MYSQL_PWD = $oldMysqlPwd
}

Write-Host "Restoring uploads..."
if (Test-Path $UploadDir) {
    $backupOldUploads = "$UploadDir.before-restore-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Move-Item -LiteralPath $UploadDir -Destination $backupOldUploads
    Write-Host "Existing uploads moved to $backupOldUploads"
}
New-Item -ItemType Directory -Path $UploadDir -Force | Out-Null
Expand-Archive -Path $uploadsArchive -DestinationPath $UploadDir -Force
$emptyUploads = Join-Path $UploadDir "empty-uploads"
if (Test-Path $emptyUploads) {
    Remove-Item -LiteralPath $emptyUploads -Recurse -Force
}

if ($tempDir -and (Test-Path $tempDir)) {
    Remove-Item -LiteralPath $tempDir -Recurse -Force
}

Write-Host ""
Write-Host "Restore completed. Restart the CRM service before use."
