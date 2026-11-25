# 后端开发与启动

`pom.xml` 是 Maven 构建入口，`MomentSyncApplication.java` 是 Spring Boot 启动入口。

```text
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/momentsync/
    │   │   ├── MomentSyncApplication.java
    │   │   ├── auth/
    │   │   ├── post/
    │   │   ├── comment/
    │   │   ├── moderation/
    │   │   └── web/
    │   └── resources/             # 应用配置和敏感词表
    └── test/java/com/momentsync/  # 集成测试
```

新的 Java 业务包统一建在 `backend/src/main/java/com/momentsync/` 下。

| Java 包 | 代码归属 |
| --- | --- |
| `auth` | 登录和 JWT、当前用户身份、活跃账号检查 |
| `post` | 印记发布、列表、详情和搜索，统一维护公开印记的可见性规则 |
| `comment` | 评论列表、发布及写入事务 |
| `config` | 标签数据库查询、心情和地点预设配置 |
| `media` | 图片校验、存储、公开图片读取 |
| `moderation` | 内容检查接口和敏感词实现 |
| `web` | 统一响应、通用异常处理和健康检查 |
| `admin` | 后续管理端接口 |

例如，新增印记详情在 `post/` 开发。开始写管理接口时，在这里新建 `admin/`，Java 文件声明 `package com.momentsync.admin;`。

包按业务划分。同一业务里，`Controller` 接收 HTTP 请求，`Service` 执行业务规则、管理事务，`Repository` 读写数据库。

## 本地启动

准备 JDK 17 或 21、Maven 3.9+ 和 MySQL 8。在仓库根目录执行 `database/schema.sql`、`database/seed.sql` 后，参照 `.env.example` 导出本机环境变量。后端需要 `DB_PASSWORD` 和至少 32 字节的随机 `JWT_SECRET`，数据库主机、端口、名称和用户名可用 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME` 设置。终端可用 `openssl rand -hex 32` 生成开发用密钥。

```bash
mvn -f backend/pom.xml clean package
java -jar backend/target/moment-sync-backend-0.1.0-SNAPSHOT.jar
```

另开一个终端验证：

```bash
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/api/posts/search
```

第一条在连库成功时应返回 `200` 及 `data.database: UP`。第二条未带登录凭证，应返回 `401` 及业务错误码 `40101`。管理后台的本地开发端口默认为 `5173`，其他来源可通过逗号分隔的 `CORS_ALLOWED_ORIGINS` 配置。

## 初始化本地演示账号

导入 `schema.sql` 和 `seed.sql` 后，首次演示时用 `demo-accounts` profile 启动。按提示给 `student_a`、`student_b` 和 `admin_demo` 分别输入本地密码。每个密码至少 8、最多 72 个 UTF-8 字节。

```bash
printf 'student_a 密码: '; IFS= read -r -s DEMO_STUDENT_A_PASSWORD; printf '\n'
printf 'student_b 密码: '; IFS= read -r -s DEMO_STUDENT_B_PASSWORD; printf '\n'
printf 'admin_demo 密码: '; IFS= read -r -s DEMO_ADMIN_PASSWORD; printf '\n'
export DEMO_STUDENT_A_PASSWORD DEMO_STUDENT_B_PASSWORD DEMO_ADMIN_PASSWORD
java -jar backend/target/moment-sync-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo-accounts
unset DEMO_STUDENT_A_PASSWORD DEMO_STUDENT_B_PASSWORD DEMO_ADMIN_PASSWORD
```

初始化只为 `seed.sql` 中仍保留占位密码的三个账号写入 BCrypt 哈希，重复启动保留现有密码。初始化后，后续运行使用普通启动命令。密码通过本地环境传入，请勿提交。

## 登录接口

小程序调用 `POST /api/auth/login`，管理后台调用 `POST /api/admin/auth/login`。两者收到 `data.token` 和 `data.user`。前端在后续请求中携带 `Authorization: Bearer <token>`，可通过 `GET /api/users/me` 获取当前账号，并使用同一个令牌调用已实现的搜索、评论接口。请求格式和错误码见 [账号接口契约](../docs/api.md#账号与登录接口)。

## 测试

从仓库根目录运行 `mvn -f backend/pom.xml clean verify`，统一运行鉴权和搜索评论集成测试，并构建可执行 JAR。测试默认使用 H2。鉴权集成测试也支持真实 MySQL，使用 `APP_TEST_DB_URL`、`APP_TEST_DB_USER`、`APP_TEST_DB_PASSWORD` 指向单独的 `moment_auth_test_*` 测试库。搜索评论的 MySQL 测试用单独的 `interaction_test_*` 测试库，配置见[搜索评论说明](../docs/modules/search-comment-cax.md#验证与复现)。

## 配置和图片上传（陈昂雄）

上传采用本地磁盘存储，部署时将 `UPLOAD_DIR` 指向持久目录，并配置进程的读写权限。`UPLOAD_PUBLIC_BASE_URL` 是小程序能访问的后端公共地址，默认 `http://localhost:8080`，不要附加 `/api`。反向代理需转发 `/api/files/images/**`，并允许至少 11MB 请求体。真机配置 HTTPS 的 request/uploadFile 合法域名及图片访问域名。

支持 JPEG/PNG、10MB、2000 万像素；不支持的格式在上传时返回明确错误。服务重启后目录内图片仍可读取。图片 URL 公开读取，上传需要 JWT；本周不包含私密媒体和孤立上传清理。

标签来自数据库 ACTIVE 记录；心情、地点分别使用 `CAMPUS_MOODS`、`CAMPUS_LOCATIONS` 逗号分隔的部署配置。配置返回值与发布请求字段见 [API 文档](../docs/api.md)。

新增配置/上传测试为 `ConfigMediaIntegrationTest`，包含真实 HTTP multipart 解析、JWT、图片下载和错误返回。默认使用 H2 和临时图片目录；设置 `CONFIG_MEDIA_TEST_DB_URL`、`CONFIG_MEDIA_TEST_DB_USER`、`CONFIG_MEDIA_TEST_DB_PASSWORD` 可在独立 `moment_media_test_*` MySQL 数据库复测。测试会清空该专用库业务表，禁止指定团队业务库。
