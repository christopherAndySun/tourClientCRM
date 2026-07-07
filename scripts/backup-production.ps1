param(
    [string]$DbUrl = $env:CRM_DB_URL,
    [string]$DbUsername = $env:CRM_DB_USERNAME,
    [string]$DbPassword = $env:CRM_DB_PASSWORD,
    [string]$UploadDir = $env:CRM_UPLOAD_DIR,
    [string]$BackupDir = "",
    [int]$RetentionDays = 30,
    [switch]$AllowDevDefaults
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
if (-not $BackupDir) {
    $BackupDir = Join-Path $root "backups"
}

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

$db = Parse-JdbcMysqlUrl $DbUrl
$mysqldump = Resolve-MySqlTool "mysqldump.exe"

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$workDir = Join-Path $BackupDir "tour-crm-$timestamp"
$archivePath = Join-Path $BackupDir "tour-crm-$timestamp.zip"
New-Item -ItemType Directory -Path $workDir -Force | Out-Null

$fullDump = Join-Path $workDir "db-full.sql"
$settingsDump = Join-Path $workDir "system-settings.sql"
$uploadsArchive = Join-Path $workDir "uploads.zip"
$manifestPath = Join-Path $workDir "manifest.json"

$oldMysqlPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = $DbPassword
try {
    Write-Host "Backing up MySQL database $($db.Database) from $($db.Host):$($db.Port)..."
    & $mysqldump `
        "--host=$($db.Host)" `
        "--port=$($db.Port)" `
        "--user=$DbUsername" `
        "--default-character-set=utf8mb4" `
        "--single-transaction" `
        "--routines" `
        "--triggers" `
        "--events" `
        "--databases" $db.Database `
        "--result-file=$fullDump"

    Write-Host "Backing up system settings table..."
    & $mysqldump `
        "--host=$($db.Host)" `
        "--port=$($db.Port)" `
        "--user=$DbUsername" `
        "--default-character-set=utf8mb4" `
        "--single-transaction" `
        $db.Database "crm_system_settings" `
        "--result-file=$settingsDump"
} finally {
    $env:MYSQL_PWD = $oldMysqlPwd
}

Write-Host "Backing up upload directory..."
if (Test-Path $UploadDir) {
    Compress-Archive -Path (Join-Path $UploadDir "*") -DestinationPath $uploadsArchive -Force
} else {
    New-Item -ItemType Directory -Path (Join-Path $workDir "empty-uploads") | Out-Null
    Compress-Archive -Path (Join-Path $workDir "empty-uploads") -DestinationPath $uploadsArchive -Force
}

$manifest = [ordered]@{
    app = "tour-client-crm"
    createdAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    database = $db.Database
    dbHost = $db.Host
    dbPort = $db.Port
    uploadDir = (Resolve-Path -LiteralPath $UploadDir -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Path)
    files = @("db-full.sql", "system-settings.sql", "uploads.zip")
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -Path $manifestPath -Encoding UTF8

Write-Host "Creating backup archive..."
Compress-Archive -Path (Join-Path $workDir "*") -DestinationPath $archivePath -Force

if ($RetentionDays -gt 0) {
    Get-ChildItem -Path $BackupDir -Filter "tour-crm-*.zip" -File |
        Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) } |
        Remove-Item -Force
}

Write-Host ""
Write-Host "Backup created:"
Write-Host $archivePath
