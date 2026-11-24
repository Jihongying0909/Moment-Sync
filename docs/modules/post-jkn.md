# 江可妮：印记发布、图片上传与内容展示（前端）

交付分支：`JKN-1124`。基于 `develop`（`8b8a390`）。本分支只包含小程序前端模块与自测，**不创建 `app.js` / `app.json` 宿主，不实现登录、配置接口、后端、搜索评论或管理后台**。团队其他成员完成后按本文接入即可。

## 1. 交付范围

| 页面 | 路径 | 已完成 |
| --- | --- | --- |
| 首页列表 | `miniprogram/modules/post/pages/home/index` | 已发布印记分页、下拉刷新、空态、错误重试、发布/搜索入口 |
| 印记发布 | `miniprogram/modules/post/pages/publish/index` | 标题、正文、单图选择预览、标签/心情/地点选择、上传与发布状态、失败保留草稿 |
| 印记详情 | `miniprogram/modules/post/pages/detail/index` | 图片、正文、标签、心情、地点、作者、时间、评论数量、刷新与错误态 |

组件：`components/post-card`（列表卡片）、`components/media-picker`（单图选择）、`components/option-picker`（发布页内部使用的通用 chip 选择控件）。`option-picker` 是发布页的临时选择 UI；段晨鸣的三个独立配置组件合并后，可在发布页 JSON/WXML 中按相同事件契约替换，本模块不声称实现他的组件。

服务：`services/client.js` 负责统一 envelope 解包、Bearer 头、401 处理、图片上传、列表/详情/发布请求与路由适配；`services/post-model.js` 是纯函数层，负责 BIGINT 字符串保持、字段归一化、草稿校验和分页计算。

## 2. 接口契约（前端按此实现，待后端负责人最终确认）

统一响应：`{ "code": 0, "message": "success", "data": ... }`；鉴权头 `Authorization: Bearer <token>`；`baseUrl` 需包含 `/api`。所有 ID 作为字符串处理，不先转成 Number。

| 方法 | 路径 | 前端用法 |
| --- | --- | --- |
| GET | `/api/posts?page&pageSize` | 首页列表。`data` 为 `{ items, total, page, pageSize }` |
| GET | `/api/posts/{id}` | 详情页。字段与列表项一致 |
| POST | `/api/files/images` | `multipart/form-data`，字段名默认 `file`；`data` 为 URL 字符串或 `{url}` / `{imageUrl}` |
| POST | `/api/posts` | 创建印记；请求体见下文 |

列表项字段与陈昂雄搜索模块保持一致：

```json
{
  "id": "2",
  "title": "校园晚霞",
  "content": "运动场旁边的晚霞很漂亮。",
  "author": { "id": "3", "nickname": "小频", "avatarUrl": null },
  "imageUrl": "https://example.test/notice.jpg",
  "mood": "开心",
  "campusLocation": "运动场",
  "tags": [{ "id": "2", "name": "摄影" }],
  "commentCount": 1,
  "createdAt": "2026-11-24 12:00:00"
}
```

发布请求体（本模块选定并在此备案）：

```json
{
  "title": "校园晚霞",
  "content": "运动场旁边的晚霞很漂亮。",
  "imageUrl": "https://example.test/notice.jpg",
  "mood": "开心",
  "campusLocation": "运动场",
  "tagIds": ["2", "5"]
}
```

前端校验：标题 1–100 字、正文 1–2000 字、标签最多 5 个；图片先经 `/files/images` 得到远端 URL，再随印记提交，不把本地临时路径发给后端。校验失败、未登录、401、网络失败、业务错误都会在页面显示可操作提示；创建失败时保留草稿和图片，重试不会重复上传同一张图片。

## 3. 宿主接入

### 3.1 注册页面

由宿主在 `app.json.pages` **追加**：

```json
"modules/post/pages/home/index",
"modules/post/pages/publish/index",
"modules/post/pages/detail/index"
```

### 3.2 初始化客户端

在 `App.onLaunch` 中调用，必须在打开本模块页面之前完成：

```js
const post = require('./modules/post/services/client');

post.configure({
  baseUrl: 'http://localhost:8080/api', // 真机需改为可访问地址并配置 request/uploadFile 合法域名
  getToken: () => auth.getToken(),
  onUnauthorized: () => auth.requireLogin(),
  loadTags: async () => (await configApi.listTags()).map(tag => ({ id: String(tag.id), name: tag.name })),
  loadMoods: async () => (await configApi.listMoods()).map(item => typeof item === 'string' ? item : item.name),
  loadLocations: async () => (await configApi.listLocations()).map(item => typeof item === 'string' ? item : item.name),
  openSearch: () => wx.navigateTo({ url: '/modules/search-comment/pages/search/index' }),
  openPost: id => wx.navigateTo({ url: '/modules/post/pages/detail/index?id=' + id })
});
```

`auth` 与 `configApi` 是登录负责人、配置负责人提供的适配位置，本模块不重复实现。`loadTags` / `loadMoods` / `loadLocations` 可缺省；缺省时发布页显示“配置未接入”，不填假选项。`baseUrl` 以 `/api` 结尾；模块内部只追加 `/posts`、`/files/images`。`request`、`upload` 可注入兼容 `wx.request` / `wx.uploadFile` 的适配器。

### 3.3 与陈昂雄搜索评论模块联动

本分支没有硬编码另一分支的组件路径，避免独立分支编译失败。集成时：

1. 首页 `index.json` 的 `usingComponents` 追加 `"search-entry": "/modules/search-comment/components/search-entry/index"`，并将首页 WXML 的“搜一搜”按钮替换为 `<search-entry />`（或保留按钮并通过 `openSearch` 适配）。
2. 详情页 `index.json` 追加 `"comment-panel": "/modules/search-comment/components/comment-panel/index"`，在“同频留言”标题下放入：
   ```xml
   <comment-panel id="comments" post-id="{{post.id}}" bind:countchange="onCommentCountChange" />
   ```
   页面已提供 `onCommentCountChange`，只接受 `loaded: true` 的真实数量。
3. 两个模块共用同一个 `baseUrl`、`getToken` 与 `openPost` 适配；BIGINT 一律保持字符串。

## 4. 状态与异常设计

- 首页：请求带 generation 序号，刷新后旧响应不会覆盖新数据；失败保留已加载项并允许重试失败页。
- 发布页：`validating → uploading → submitting → success / error`；`uploading` 与 `submitting` 期间锁定提交按钮；失败保留标题、正文、图片和选项。
- 详情页：无效 ID 直接提示；接口 404 展示后端消息（印记不存在或已下架）；下拉刷新重载并停止刷新动画。
- 图片：`wx.chooseMedia` 不可用时回退 `wx.chooseImage`；单张、默认上限 10MB；取消选择不报错。

## 5. 自测与待联调

```bash
node --test miniprogram/modules/post/tests/*.test.cjs
```

| 检查 | 结果 |
| --- | --- |
| 字段归一化、字符串 BIGINT、首张图片排序 | 通过 |
| 草稿校验、标签去重与上限、分页参数 | 通过 |
| 请求头、envelope 解包、401/网络/业务错误 | 通过 |
| 图片上传、字符串 JSON 响应、空 URL 拦截 | 通过 |
| 发布状态流转、失败保留草稿、重复提交拦截、上传结果复用 | 通过 |
| 首页分页与旧响应竞态、详情页评论数量事件 | 通过 |
| 组件选择/取消、超尺寸图片、取消选择 | 通过 |
| 合计 | 16/16 通过（Node 逻辑测试） |

待联调（当前环境无宿主 app 与微信开发者工具，不能宣称完成）：

1. 微信开发者工具中检查三个页面的真实渲染、窄屏与长中文文本。
2. 登录负责人注入 token、401 跳登录后返回原页面。
3. 段晨鸣的配置接口联调标签、心情、地点；陈昂雄模块合并后验证搜索入口和评论组件。
4. 吴家裔确认 `/api/posts`、`/api/files/images` 的实际字段与上传字段名，如有差异只改 `services/client.js` 适配层并同步本文。
5. 发布带图印记后刷新首页与详情，确认数据库读取一致；后台下架后详情应显示 404。

实际自测日期：2026-11-24。
