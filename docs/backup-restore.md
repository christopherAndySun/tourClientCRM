# 备份与恢复方案

这个系统必须备份三类数据：

- MySQL 数据库：客户线索、账号、权限、成交记录、分配日志、系统设置等都在这里。
- 上传图片目录：客户抖音截图、微信截图等文件在 `CRM_UPLOAD_DIR`。
- 系统设置：OCR、钉钉机器人配置在 `crm_system_settings` 表里，已包含在全库备份里，同时脚本会额外导出一份 `system-settings.sql` 方便单独核对。

## 服务器要求

备份不会明显提高服务器要求。当前规模建议：

- 每天凌晨做一次全量备份。
- 备份保留 30 天。
- 备份文件不要只放在同一台服务器，至少复制到另一块盘、NAS、网盘或对象存储。
- 图片越来越多后，再考虑增量同步或对象存储。

## 备份

生产环境先设置好这些环境变量：

```powershell
$env:CRM_DB_URL="jdbc:mysql://127.0.0.1:3306/tour_client_crm?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
$env:CRM_DB_USERNAME="正式数据库账号"
$env:CRM_DB_PASSWORD="正式数据库密码"
$env:CRM_UPLOAD_DIR="D:\crm\uploads"
```

执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\backup-production.ps1
```

备份结果在 `backups` 目录，格式类似：

```text
backups\tour-crm-20260707-213000.zip
```

备份包里包含：

- `db-full.sql`
- `system-settings.sql`
- `uploads.zip`
- `manifest.json`

本机临时测试可以使用开发默认值：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\backup-production.ps1 -AllowDevDefaults
```

## 恢复

恢复会覆盖数据库和上传图片目录，必须先停止 CRM 服务。

```powershell
powershell -ExecutionPolicy Bypass -File scripts\restore-production.ps1 -BackupPath "backups\tour-crm-20260707-213000.zip"
```

脚本会要求手动输入：

```text
RESTORE
```

确认后才会恢复。

本机临时测试可以使用：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\restore-production.ps1 -BackupPath "backups\tour-crm-20260707-213000.zip" -AllowDevDefaults
```

恢复完成后，重新启动 CRM 服务。

## 建议的上线策略

- 上线当天，先手动执行一次备份并确认有备份包。
- 每天凌晨定时执行 `backup-production.ps1`。
- 每周至少抽查一次备份包是否能解压，至少每月做一次恢复演练。
- 备份包包含客户数据和敏感配置，不要发到聊天软件里长期保存。
