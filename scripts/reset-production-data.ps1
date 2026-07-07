param(
    [string]$MysqlPath = "",
    [string]$Database = "tour_client_crm",
    [string]$Username = "",
    [string]$Password = "",
    [string]$UploadDir = "",
    [switch]$ClearSystemSettings,
    [switch]$DeleteUploads,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

if (-not $MysqlPath) {
    $localMysql = Join-Path $root "tools\mysql-8.4.9\PFiles64\MySQL\MySQL Server 8.4\bin\mysql.exe"
    if (Test-Path $localMysql) {
        $MysqlPath = $localMysql
    } elseif (Test-Path "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe") {
        $MysqlPath = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
    } else {
        $MysqlPath = "mysql"
    }
}

if (-not $Username) {
    $Username = if ($env:CRM_DB_USERNAME) { $env:CRM_DB_USERNAME } else { "root" }
}

if (-not $Password) {
    $Password = if ($env:CRM_DB_PASSWORD) { $env:CRM_DB_PASSWORD } else { "root" }
}

if (-not $UploadDir -and $env:CRM_UPLOAD_DIR) {
    $UploadDir = $env:CRM_UPLOAD_DIR
}

if (-not $Force) {
    Write-Host "This will delete business test data from database '$Database'."
    Write-Host "It keeps ADMIN, menus, ADMIN menu permissions, and optionally system settings."
    $answer = Read-Host "Type RESET to continue"
    if ($answer -ne "RESET") {
        Write-Host "Cancelled."
        exit 0
    }
}

$settingsSql = ""
if ($ClearSystemSettings) {
    $settingsSql = @"
UPDATE crm_system_settings
SET ocr_app_code = NULL,
    ocr_app_secret = NULL,
    dingtalk_hq_clue_webhook = NULL,
    dingtalk_hq_clue_enabled = 0,
    dingtalk_branch_clue_webhook = NULL,
    dingtalk_branch_clue_enabled = 0,
    remark = NULL,
    updated_at_text = NULL
WHERE id = 1;
"@
}

$sql = @"
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE crm_login_sessions;
TRUNCATE TABLE crm_system_audit_logs;
TRUNCATE TABLE crm_ocr_call_logs;
TRUNCATE TABLE crm_third_party_download_logs;
TRUNCATE TABLE crm_third_party_downloads;
TRUNCATE TABLE crm_deals;
TRUNCATE TABLE crm_clue_operation_logs;
TRUNCATE TABLE crm_clue_assign_logs;
TRUNCATE TABLE crm_clue_follow_records;
TRUNCATE TABLE crm_clue_status_history;
TRUNCATE TABLE crm_clue_images;
TRUNCATE TABLE crm_customer_contacts;
TRUNCATE TABLE crm_customer_profiles;
TRUNCATE TABLE crm_contact_locks;
TRUNCATE TABLE crm_deal_daily_sequences;
TRUNCATE TABLE crm_clue_daily_sequences;
TRUNCATE TABLE crm_clues;

DELETE FROM crm_user_menu_permissions WHERE employee_code <> 'ADMIN';
DELETE FROM crm_users WHERE employee_code <> 'ADMIN';

$settingsSql

SET FOREIGN_KEY_CHECKS = 1;
"@

$args = @(
    "-u$Username",
    "-p$Password",
    "--default-character-set=utf8mb4",
    $Database,
    "--batch",
    "--raw"
)

$sql | & $MysqlPath @args

if ($DeleteUploads) {
    if (-not $UploadDir) {
        throw "UploadDir is required when using -DeleteUploads."
    }
    $resolvedUploadDir = Resolve-Path -LiteralPath $UploadDir -ErrorAction SilentlyContinue
    if ($resolvedUploadDir) {
        Get-ChildItem -LiteralPath $resolvedUploadDir.Path -Force | Remove-Item -Recurse -Force
        Write-Host "Upload directory cleared: $($resolvedUploadDir.Path)"
    } else {
        Write-Host "Upload directory does not exist, skipped: $UploadDir"
    }
}

Write-Host "Production data reset completed."
