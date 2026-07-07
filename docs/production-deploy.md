# 生产部署说明

生产环境不要使用 `npm run dev` 或 `mvn spring-boot:run`。这两个命令只适合本地开发。

生产推荐方式是：前端先构建为静态文件，再打进后端 Spring Boot jar，服务器只启动一个后端 jar。

## 1. 打包生产 jar

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-production.ps1
```

脚本会自动执行：

- `crm-web` 下 `npm ci`
- `crm-web` 下 `npm run build`
- `crm-server` 下 `mvn clean package`
- 把 `crm-web/dist` 写入后端 jar 的 `BOOT-INF/classes/static`

最终 jar 在：

```text
crm-server\target\crm-server-0.0.1-SNAPSHOT.jar
```

## 2. 设置生产环境变量

上线前必须设置真实环境变量，不要使用代码里的默认值：

```powershell
$env:CRM_DB_URL="jdbc:mysql://127.0.0.1:3306/tour_client_crm?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
$env:CRM_DB_USERNAME="正式数据库账号"
$env:CRM_DB_PASSWORD="正式数据库密码"
$env:CRM_UPLOAD_DIR="D:\crm\uploads"
$env:CRM_JWT_SECRET="至少32位的随机字符串"
$env:CRM_SETTINGS_CRYPTO_KEY="至少32位的随机字符串"
$env:CRM_SINGLE_LOGIN="false"
$env:CRM_JWT_EXPIRATION_MINUTES="1440"
```

`CRM_UPLOAD_DIR` 后续换服务器时改成服务器上的固定磁盘目录，例如 `/data/tour-crm/uploads`。

## 3. 启动生产服务

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-production.ps1
```

启动后访问：

```text
http://服务器IP:8080/login
```

如果只是本机临时验证生产 jar，可以加 `-AllowDevDefaults`：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-production.ps1 -AllowDevDefaults
```

正式上线不要加 `-AllowDevDefaults`。
