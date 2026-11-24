# Moment Sync 拾光同频

Agent 驱动的校园多媒体记录与兴趣社交平台。

## 第一周 Demo

第一周目标是打通以下真实业务链路：

1. 普通用户登录小程序。
2. 发布包含文字、单张图片、标签、心情和校园地点的时光印记。
3. 在首页和详情页查看印记。
4. 按关键词或标签搜索内容。
5. 对印记发表评论。
6. 管理员登录 Web 后台，查看用户和内容，并下架内容。

## 技术栈

- 用户端：微信小程序
- 管理后台：Vue 3、ECharts
- 后端：Spring Boot
- 数据库：MySQL
- 鉴权：JWT

## 目录结构

```text
Moment-Sync/
├─ miniprogram/     微信小程序
├─ admin-web/       Vue 3 管理后台
├─ backend/         Spring Boot 后端
├─ database/        建表与演示数据脚本
└─ docs/            需求、接口、任务、测试与演示文档
```

## 开始开发

1. 复制 `.env.example` 为本地配置文件，填写本机配置，禁止提交真实密码或密钥。
2. 执行 `database/schema.sql` 创建表。
3. 执行 `database/seed.sql` 导入演示数据。
4. 按[后端启动说明](backend/README.md)运行 Spring Boot，按[小程序启动与协作说明](miniprogram/README.md)在微信开发者工具导入 `miniprogram/`。管理后台的启动命令由负责人在 `admin-web/` 补充。

## 分支约定

- `main`：可稳定演示的版本。
- `develop`：本周集成分支。
- `feature/模块名-姓名缩写`：个人功能分支。
- `fix/问题名-姓名缩写`：缺陷修复分支。

功能完成后先合并到 `develop`，通过联调和测试后再合并到 `main`。禁止直接向 `main` 提交日常开发代码。

## 文档入口

- [第一周需求范围](docs/requirements.md)
- [第一周前后端分组方案](docs/第一周Demo前后端分组方案.md)
- [Demo 前端分工](docs/tasks/Demo前端分工.md)
- [Demo 后端分工](docs/tasks/Demo后端分工.md)
- [接口文档](docs/api.md)
- [任务看板](docs/tasks.md)
- [数据库说明](docs/database.md)
- [缺陷记录](docs/bugs.md)
- [演示脚本](docs/demo-script.md)

