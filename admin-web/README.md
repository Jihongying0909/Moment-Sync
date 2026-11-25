# 拾光同频管理后台

Vue 3 + Vite 管理后台，包含管理员登录、基础统计、用户列表、印记列表、详情查看与内容下架。

## 环境要求

- Node.js 20+
- npm 10+
- Moment-Sync 后端运行在 `http://localhost:8080`

## 启动

```bash
cd admin-web
npm install
npm run dev
```

访问 `http://localhost:5173`。开发服务器会将 `/api` 代理到后端 `http://localhost:8080`。

如需连接其他后端，复制 `.env.example` 为 `.env.local`，修改 `VITE_API_BASE_URL`。

## 后端接口

| 功能 | 接口 |
| --- | --- |
| 管理员登录 | `POST /api/admin/auth/login` |
| 基础统计 | `GET /api/admin/statistics` |
| 用户列表 | `GET /api/admin/users` |
| 内容列表 | `GET /api/admin/posts` |
| 内容详情 | `GET /api/admin/posts/{id}` |
| 内容下架 | `PATCH /api/admin/posts/{id}/status` |

后台详情接口当前由前端按 `GET /api/admin/posts/{id}` 接入；后端最终路径若有调整，只需修改 `src/api/admin.js`。

所有 BIGINT ID 都按字符串保存和传递。接口使用统一 `{ code, message, data }` 响应，`40101` 会清除管理员会话并跳转登录页，`40301` 会显示权限不足。

## 验证

```bash
npm run test
npm run build
```
