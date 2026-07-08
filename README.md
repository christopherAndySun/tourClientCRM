# 旅游客户 CRM 系统

面向旅游客资录入、分配、销售跟进、成交记录、三方下载池、数据统计和钉钉播报的 CRM 系统。

当前推荐部署方式是：前端先构建成静态资源，再打进 Spring Boot 后端 jar，服务器只启动一个后端服务。生产环境不要使用 `npm run dev` 或 `mvn spring-boot:run`。

## 目录结构

```text
tourClientCRM/
├── crm-server/              # Spring Boot 后端
├── crm-web/                 # Vue 3 + Vite 前端
├── docs/                    # 部署、备份、实现说明
├── scripts/                 # 本地启动、生产打包、备份恢复脚本
├── tools/                   # 本地便携 JDK/Maven 等工具
└── README.md
```

## 技术栈

- 后端：Java 17、Spring Boot、Spring Security、MySQL 8
- 前端：Vue 3、Vite、Element Plus
- 存储：MySQL 存业务数据，上传图片落磁盘目录，数据库只存图片路径
- 认证：后端会话 token，支持 24 小时过期和可选单点登录

## 本地开发

本地建议版本：

- JDK 17
- Maven 3.9+
- Node.js 20+
- MySQL 8+

启动 MySQL：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-mysql.ps1
```

启动后端：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start_backend.ps1
```

启动前端：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start_frontend.ps1
```

本地访问：

```text
http://localhost:5173/login
```

首次空库登录：

```text
员工编号：ADMIN
初始密码：admin123
```

空数据库下，系统会在首次 `ADMIN/admin123` 登录时自动创建管理员账号。首次登录后会要求修改密码；正式上线前不要继续使用默认密码。

## 生产打包

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-production.ps1
```

脚本会执行：

- `crm-web` 下 `npm ci`
- `crm-web` 下 `npm run build`
- `crm-server` 下 `mvn clean package`
- 将 `crm-web/dist` 写入后端 jar 的 `BOOT-INF/classes/static`

最终 jar 默认生成在：

```text
crm-server\target\crm-server-0.0.1-SNAPSHOT.jar
```

## 服务器目录建议

Linux 服务器建议：

```text
/opt/web/
├── crm-server.jar
├── app.log
├── logs/
├── start.sh
└── stop.sh

/data/tour-crm/
└── uploads/
```

说明：

- `crm-server.jar` 是生产运行文件。
- `logs/` 放应用日志。
- `uploads/` 必须放在固定磁盘目录，不能依赖项目相对目录，后续要纳入备份。
- 当前 jar 已内置前端静态资源，一般不需要再单独维护 `/opt/web/static/`。

## 生产环境变量

生产环境必须使用真实环境变量，不要依赖 `application.yml` 里的开发默认值。

```bash
export CRM_DB_URL='jdbc:mysql://127.0.0.1:3306/tour_client_crm?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
export CRM_DB_USERNAME='正式数据库账号'
export CRM_DB_PASSWORD='正式数据库密码'
export CRM_UPLOAD_DIR='/data/tour-crm/uploads'
export CRM_JWT_SECRET='至少32位随机字符串'
export CRM_SETTINGS_CRYPTO_KEY='至少32位随机字符串'
export CRM_SINGLE_LOGIN='false'
export CRM_JWT_EXPIRATION_MINUTES='1440'
```

Windows PowerShell 示例：

```powershell
$env:CRM_DB_URL="jdbc:mysql://127.0.0.1:3306/tour_client_crm?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
$env:CRM_DB_USERNAME="正式数据库账号"
$env:CRM_DB_PASSWORD="正式数据库密码"
$env:CRM_UPLOAD_DIR="D:\tour-crm\uploads"
$env:CRM_JWT_SECRET="至少32位随机字符串"
$env:CRM_SETTINGS_CRYPTO_KEY="至少32位随机字符串"
$env:CRM_SINGLE_LOGIN="false"
$env:CRM_JWT_EXPIRATION_MINUTES="1440"
```

## 生产启动

Windows 可使用项目脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-production.ps1 -JarPath "crm-server\target\crm-server-0.0.1-SNAPSHOT.jar"
```

Linux 可使用：

```bash
java -jar /opt/web/crm-server.jar --server.port=8080 > /opt/web/app.log 2>&1 &
```

启动后访问：

```text
http://服务器IP:8080/login
```

如果只是本机临时验证生产 jar，可以使用：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-production.ps1 -AllowDevDefaults
```

正式上线不要加 `-AllowDevDefaults`。

## 上线前检查

必须完成：

- 设置真实数据库账号和密码。
- 设置固定上传目录 `CRM_UPLOAD_DIR`，并确认目录有读写权限。
- 设置真实 `CRM_JWT_SECRET` 和 `CRM_SETTINGS_CRYPTO_KEY`。
- 首次登录 `ADMIN/admin123` 后立即修改管理员密码。
- 确认钉钉机器人 webhook 已配置到正式群，不要混用测试群。
- 确认本部和分公司机器人分开配置。
- 跑一遍后端测试、前端构建和核心业务手工回归。

推荐命令：

```powershell
cd crm-server
..\tools\apache-maven-3.9.10\bin\mvn.cmd test

cd ..\crm-web
npm run build
npm run test:e2e
```

## 清理测试数据

如果要在上线前清空测试业务数据，只保留初始化账号和菜单等基础数据，使用：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\reset-production-data.ps1
```

注意：

- 清理前先备份数据库和上传目录。
- 正式生产库不要随意执行清理脚本。
- 开发环境可以继续保留测试数据，不影响生产数据库。

## 备份与恢复

备份内容至少包括：

- MySQL 数据库
- 上传图片目录
- 系统设置中的 OCR、钉钉机器人等配置

备份脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\backup-production.ps1
```

恢复脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\restore-production.ps1
```

更多说明见：

```text
docs/backup-restore.md
docs/production-deploy.md
```

## 后续发布流程

后续功能修改后，建议流程：

1. 本地开发并自测。
2. 提交 Git 并推送 GitHub。
3. 重新执行 `scripts\build-production.ps1`。
4. 上传新的 `crm-server.jar` 到服务器。
5. 先备份，再停止旧服务，替换 jar，启动新服务。
6. 验证登录、新增客户、分配、成交、下载、钉钉播报等核心流程。

