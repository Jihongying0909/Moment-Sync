# Demo 前端分工

小程序宿主工程骨架（`miniprogram/`）、公共请求与登录态服务层、标签/心情/地点三个配置选择组件已完成搭建，江可妮的印记模块（首页、发布、详情）与陈昂雄的搜索评论模块已完成合入与宿主挂载。各成员从 `develop` 拉取最新代码后开个人 `feature/` 分支开发，并与对应后端同学按 [API 文档](../api.md) 同步确认字段和联调。

## 段晨鸣 · 小程序宿主底座、配置组件与跨模块集成

开发位置为 `miniprogram/app.*`、`miniprogram/config/`、`miniprogram/services/` 及 `miniprogram/components/`。

- 维护小程序全局路由、底部导航、公共手账视觉规范（`app.wxss`）及统一服务层（`request`、`auth`、`config`、`router`）。
- 维护 `tag-picker`、`mood-picker`、`location-picker` 三个配置选择组件，与陈昂雄联调 `GET /api/tags`、`GET /api/moods`、`GET /api/locations` 真实配置返回。
- 负责前端代码审核、跨模块样式统一与合并冲突处理。

## 江可妮 · 印记首页、发布、图片上传与详情联调

开发位置为 `miniprogram/modules/post/`。首页、发布页、详情页及单图选择组件已完成编码并挂载至宿主。

- 与陈昂雄确认并联调单张图片上传 `POST /api/files/images`，验证选图、上传进度提示及失败重试不重复上传。
- 与廖子豪确认并联调 `POST /api/posts`、`GET /api/posts`、`GET /api/posts/{id}`，确认图片引用字段、`tagIds`、心情与地点的提交和回显一致，验证标题/正文命中敏感词（`42201`）时的页面提示。
- 在微信开发者工具中联合验证首页 `<search-entry />` 跳转、详情页 `<comment-panel />` 评论数同步与下拉刷新表现。

## 郑凯柔 · 小程序登录、个人中心与用户端主链路验收

开发位置为 `miniprogram/pages/login/` 与 `miniprogram/pages/profile/`。宿主已接入 `services/auth.js` 基础登录与会话出口，可直接在现有页面上迭代完善。

- 与吴家裔确认并联调普通用户登录 `POST /api/auth/login` 和当前用户信息 `GET /api/users/me`，完善账号密码校验、错误提示、头像昵称及个人中心信息展示。
- 验证未登录拦截、Token 持久化、退出清理，以及从受保护页面触发 `401` 登录跳转后自动返回原页面的完整链路。
- 牵头在微信开发者工具中执行小程序端「登录 → 选图发布 → 首页查看 → 关键词/标签搜索 → 详情发表评论」主链路自测与缺陷记录。

## 吉虹颖 · Vue 3 管理后台与全链路集成协调

开发位置为 `admin-web/`，同时负责团队演示账号、数据库种子脚本与全链路集成协调（可与郑凯柔根据排期互助分担页面与验收工作）。

- 初始化 `admin-web/` Vue 3 工程，实现管理员登录页、基础后台布局与路由守卫，对接吴家裔的 `POST /api/admin/auth/login`（校验普通用户账号无法进入后台）。
- 对接周黎的 `GET /api/admin/statistics`、`GET /api/admin/users`、`GET /api/admin/posts`、内容详情及下架接口 `PATCH /api/admin/posts/{id}/status`，完成统计卡片（用户数、印记数、评论数）、用户列表、内容列表与下架二次确认交互。
- 组织前后端全链路联调，确认后台执行下架后，小程序首页、搜索结果、印记详情与评论读写均立即按预期拦截或隐藏，并更新根目录启动说明与演示脚本。

## 开发与联调约定

1. 小程序页面统一通过 `app.js` 暴露的 `getServices()` 调用 `http`、`auth`、`config` 和 `router`，不单独硬编码后端域名、存储键名或模拟数据。
2. 所有后端 `BIGINT` 主键在前端一律按字符串保存和传参，禁止转换为 `Number`。
3. 提交 PR 到 `develop` 前先在本地运行 `node --test miniprogram/tests/*.test.cjs miniprogram/modules/post/tests/*.test.cjs miniprogram/modules/search-comment/tests/*.test.cjs` 确保逻辑测试全部通过，并在微信开发者工具完成页面自测。
