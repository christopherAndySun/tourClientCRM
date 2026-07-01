# 旅游客户 CRM 系统

本项目采用前后端分离：

- 后端：Java + Spring Boot + MyBatis-Plus + MySQL
- 前端：Vue 3 + Vite + Element Plus
- 第一阶段：运营侧线索录入、图片上传、权限查看、成单补录、Excel 导出、Word 模板生成
- 第二阶段：销售真实分配、钉钉日报/月报

## 目录

- `crm-server/`：Java 后端服务
- `crm-web/`：Vue3 前端页面，支持移动端展示
- `database/schema.sql`：MySQL 初始化表结构
- `docs/implementation-plan.md`：阶段实现计划

## 本地开发准备

当前机器需要安装：

- JDK 17+
- Maven 3.9+
- Node.js 20+
- MySQL 8+

前端启动：

```bash
cd crm-web
npm install
npm run dev
```

也可以使用项目脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start_frontend.ps1
```

后端启动：

```bash
cd crm-server
mvn spring-boot:run
```

本项目已提供本地便携版 JDK/Maven 启动脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start_backend.ps1
```
