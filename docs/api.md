# API 文档

## 统一约定

- 所有接口位于 `/api` 下，响应格式为 `application/json`。请求体格式以各接口的约定为准。
- 需要登录的接口使用 `Authorization: Bearer <token>`。
- 时间格式为 `yyyy-MM-dd HH:mm:ss`。

### 响应结构

业务接口统一使用 `ApiResponse<T>`。成功时 `code` 为 `0`，`message` 为 `success`，`data` 放接口数据。失败时返回对应的 HTTP 状态和业务错误码，`data` 为 `null`。前端按状态码和业务错误码判断处理方式，`message` 用于提示。

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

响应示例：

```json
{
  "code": 40101,
  "message": "请先登录",
  "data": null
}
```

### 错误码与后端处理

| HTTP 状态 | 业务错误码 | 含义 |
| --- | --- | --- |
| `400` | `40001` | 请求参数不合法或请求体格式错误 |
| `401` | `40101` | 缺少有效的登录身份 |
| `403` | `40301` | 已登录，角色权限不足 |
| `403` | `40302` | 当前账号已被禁用 |
| `403` | `40303` | 时光胶囊尚未到达开启时间 |
| `404` | `40401` | 目标资源不存在或当前用户不可见 |
| `409` | `40901` | 状态冲突或重复提交（如重复申请好友、重复报名） |
| `413` | `41301` | 上传图片或请求超过大小限制 |
| `415` | `41501` | 请求的 `Content-Type` 不受支持 |
| `422` | `42201` | 内容命中敏感词 |
| `500` | `50001` | 未预期的服务异常 |
| `503` | `50301` | 健康检查发现数据库不可用 |

新增接口复用 `ApiResponse` 和已定义的错误码，业务校验失败用 `ApiException` 表达。需要新增业务错误码时，在对应接口条目里记录 HTTP 状态、错误码和含义。未预期的异常保留服务端日志，响应只给通用提示。

`ApiExceptionHandler` 统一处理业务接口和上传异常；鉴权过滤器单独处理 `401`、`403`。

## 接口清单

| 功能 | 方法 | 路径 | 权限 | 状态 |
| --- | --- | --- | --- | --- |
| 健康检查 | GET | `/api/health` | 公开 | 可联调 |
| 普通用户登录 | POST | `/api/auth/login` | 公开 | 可联调 |
| 当前用户信息 | GET | `/api/users/me` | 需登录 | 可联调 |
| 管理员登录 | POST | `/api/admin/auth/login` | 公开，登录时校验管理员角色 | 可联调 |
| 标签配置 | GET | `/api/tags` | 需登录 | 可联调 |
| 心情配置 | GET | `/api/moods` | 需登录 | 可联调 |
| 地点配置 | GET | `/api/locations` | 需登录 | 可联调 |
| 单张图片上传 | POST | `/api/files/images` | 需登录 | 可联调 |
| 发布印记 | POST | `/api/posts` | 需登录 | 可联调（团队环境待验收） |
| 首页印记列表 | GET | `/api/posts` | 需登录 | 可联调（团队环境待验收） |
| 印记详情 | GET | `/api/posts/{id}` | 需登录 | 可联调（团队环境待验收） |
| 搜索印记 | GET | `/api/posts/search` | 需登录 | 可联调 |
| 发表评论 | POST | `/api/posts/{id}/comments` | 需登录 | 可联调 |
| 评论列表 | GET | `/api/posts/{id}/comments` | 需登录 | 可联调 |
| 后台用户查询 | GET | `/api/admin/users` | 管理员 | 可联调 |
| 后台内容列表 | GET | `/api/admin/posts` | 管理员 | 可联调 |
| 后台内容详情 | GET | `/api/admin/posts/{id}` | 管理员 | 可联调 |
| 内容下架 | PATCH | `/api/admin/posts/{id}/status` | 管理员 | 可联调 |
| 后台基础统计 | GET | `/api/admin/statistics` | 管理员 | 可联调 |

## 账号与登录接口

### 普通用户登录与管理员登录

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| POST | `/api/auth/login` | 无需登录 |
| POST | `/api/admin/auth/login` | 无需登录，账号需具备管理员角色 |

请求使用 `Content-Type: application/json`。`username` 和 `password` 都是必填的 JSON 字符串。`username` 去除首尾空白后最多 50 个字符。`password` 按原样校验，UTF-8 编码最长 72 字节。

```json
{"username":"student_a","password":"<本地演示密码>"}
```

两个入口共用 BCrypt 密码校验和 JWT 签发，成功响应结构相同。登录按账号密码校验，请求携带的旧令牌不参与登录。管理员登录时使用管理员账号，响应中的 `role` 为 `ADMIN`。

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "<JWT>",
    "user": {
      "id": "2",
      "username": "student_a",
      "nickname": "小光",
      "avatarUrl": null,
      "role": "USER"
    }
  }
}
```

`token` 有效期为 2 小时，前端保存原始令牌，在后续请求中使用 `Authorization: Bearer <token>`。`user.id` 为字符串，`avatarUrl` 未配置时为 `null`，`role` 为 `USER` 或 `ADMIN`。

### 当前用户信息

`GET /api/users/me` 需要 Bearer JWT，无请求体和查询参数。`data` 直接返回用户信息，不包装在 `user` 字段下。

```http
GET /api/users/me
Authorization: Bearer <JWT>
```

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "2",
    "username": "student_a",
    "nickname": "小光",
    "avatarUrl": null,
    "role": "USER"
  }
}
```

### 账号接口常见错误

| 接口 | HTTP 状态 / 业务码 | 场景 |
| --- | --- | --- |
| 两个登录接口 | `400 / 40001` | JSON 格式错误、账号或密码缺失、字段类型错误、长度超限 |
| 两个登录接口 | `401 / 40101` | 账号或密码不正确，或账号不可使用 |
| 管理员登录 | `403 / 40301` | 账号密码正确，但账号没有管理员权限 |
| 两个登录接口 | `415 / 41501` | 请求使用不支持的 `Content-Type`，例如 `text/plain` |
| 当前用户信息 | `401 / 40101` | 令牌缺失、无效、过期，或账号已禁用 |
| 所有账号接口 | `500 / 50001` | 服务暂时不可用 |

## 已实现接口

- 健康检查连库成功时返回 HTTP `200` 和 `data.database: UP`，连库失败时返回 HTTP `503` 和错误码 `50301`。
- 搜索、评论列表和发表评论已接入后端，集成测试覆盖真实 JWT，待团队环境和小程序联调。请求参数、返回字段和错误码见 [搜索评论接口契约](modules/search-comment-cax.md#api-契约)。

## 待开发接口的联调约定

- 标签、心情和地点的取值、图片上传的返回字段及发布请求的图片引用方式待联调确认。发布时检查标题和正文的敏感词。
- 普通用户在首页、详情和搜索只能看到公开且已发布的印记。下架后，这些入口不再展示该印记，普通用户也无法读取或发布其评论。

## 配置接口

`GET /api/tags`、`GET /api/moods`、`GET /api/locations` 均要求真实 JWT 和 ACTIVE 账号。

- 标签：`data` 为 `[{"id":"2","name":"摄影"}]`；读取数据库 `tag` 中 ACTIVE 记录，按 ID 升序，ID 保持字符串；没有标签时返回 `[]`。
- 心情：`data` 为名称数组，默认 `["开心","平静","期待","充实","疲惫","难过"]`。
- 地点：`data` 为名称数组，默认 `["图书馆","运动场","活动中心","教学楼","食堂","宿舍"]`。
- 心情和地点分别通过 `CAMPUS_MOODS`、`CAMPUS_LOCATIONS` 逗号分隔配置；启动时去首尾空白、去重，拒绝空配置和超过数据库长度的名称。变更需重启。
- 发布请求提交 `tagIds` 字符串数组、`mood` 名称和 `campusLocation` 名称，发布负责人需在服务端验证它们。

## 图片上传

2026-11-25 记录本次确认的联调约定，字段与江可妮的印记前端、现有搜索响应对齐。当前已实现图片上传、发布、首页与详情。上传已落实文件字段、返回地址和大小限制，印记后端需同步字段校验、敏感词校验及可见性规则。

四个接口都需要 `Authorization: Bearer <token>`。成功统一返回 HTTP `200` 和 `{code: 0, message: "success", data: ...}`。印记、作者、标签的 ID 在响应中均为字符串，客户端不能先转换成 JavaScript Number。

### 单张图片上传

`POST /api/files/images`

- 请求为 `multipart/form-data`，文件字段名固定为 `file`，每次上传一张图片；缺少、重复或额外文件字段返回 `40001`。
- 仅支持实际可解码的 JPEG、PNG，最多 2000 万像素。不信任扩展名或客户端 MIME；重新编码移除原始元数据和尾随内容，以 UUID 文件名保存。GIF、WebP、HEIC、SVG 请先转换格式。
- 文件保存到 `UPLOAD_DIR`（默认进程工作目录下 `./uploads`），URL 前缀使用 `UPLOAD_PUBLIC_BASE_URL`（默认 `http://localhost:8080`）。该配置是后端公共地址，不包含末尾 `/api`；真机必须使用可访问的服务地址。
- 图片通过 `GET /api/files/images/{生成的文件名}` 匿名访问，返回图片二进制及对应 MIME；微信 image 组件无需附加 Token。未找到图片返回 `40401`。上传接口仍要求登录。
- 第一周图片链接为公开、不可猜测的 UUID 地址；下架印记不会删除原图或撤销已知图片链接，后续私密媒体需单独设计访问控制。尚未关联印记的上传文件不会自动清理。
- 单张图片最大 `10MB`（`10 × 1024 × 1024` 字节），前后端均校验。后端须配置 multipart 文件上限，并为总请求大小预留表单开销。
- 标准成功响应固定使用 `data.imageUrl`。现有前端可以继续兼容 `data.url`，但它不是另一套标准响应。
- `imageUrl` 是小程序可直接访问的远端图片地址，不返回服务器文件系统路径或小程序本地临时路径。该地址随后作为发布请求的 `imageUrl`。

```json
{
  "code": 0,
  "message": "success",
  "data": {"imageUrl": "https://example.test/uploads/campus.jpg"}
}
```

上传客户端使用 `wx.uploadFile` 的 `name: "file"`；由上传 API 生成 multipart boundary，不手动设置 `Content-Type: application/json`。上传 Controller 已纳入公共异常处理，失败响应中的 `message` 可直接用于页面提示。


## 印记发布、首页与详情

负责人为廖子豪，对接江可妮的印记页面，和陈昂雄核对配置取值及上传图片引用。以下字段按当前 `miniprogram/modules/post/` 调用约定实现，团队环境联调后确认验收。

三个接口均需有效的 Bearer JWT。成功返回 HTTP `200`，响应使用统一的 `ApiResponse<T>`。

### 发布印记

`POST /api/posts` 使用 `Content-Type: application/json`。

| 字段 | JSON 类型 | 必填 | 约定 |
| --- | --- | --- | --- |
| `title` | 字符串 | 是 | 去除首尾空白后 1–100 个字符，全空白内容无效 |
| `content` | 字符串 | 是 | 去除首尾空白后 1–2000 个字符，全空白内容无效 |
| `imageUrl` | 字符串 | 否 | 上传结果中的图片引用，最多 500 个字符。支持 HTTP(S) URL 或以单个 `/` 开头的站内路径。省略、`null` 或空字符串表示无图 |
| `mood` | 字符串 | 否 | 配置选项的展示值，最多 30 个字符。省略、`null` 或空字符串表示未选择 |
| `campusLocation` | 字符串 | 否 | 配置选项的展示值，最多 100 个字符。省略、`null` 或空字符串表示未选择 |
| `tagIds` | 字符串数组 | 否 | 最多 5 项，每项为正整数 ID 字符串，范围不超过有符号 BIGINT。标签必须存在且为 `ACTIVE`，重复 ID 去重。省略、`null` 或 `[]` 表示无标签 |

文字长度按 Java / JavaScript 的 UTF-16 长度计算，与现有页面一致。心情和地点当前按字符串及长度校验，配置接口提供的候选取值需与陈昂雄联调确认。

```json
{
  "title": "校园晚霞",
  "content": "运动场旁边的晚霞很漂亮。",
  "imageUrl": "https://example.test/uploads/sunset.jpg",
  "mood": "开心",
  "campusLocation": "运动场",
  "tagIds": ["2", "5"]
}
```

作者由后端当前登录身份确定，发布状态为 `PUBLISHED`，可见性为 `PUBLIC`。标题和正文分别通过公共 `ContentPolicy` 检查后，印记、单张图片关联和标签关联在同一事务内保存，任一步失败均回滚。

成功时 `data` 返回完整印记对象，结构见下方示例，必须包含字符串 `id`。新印记的 `commentCount` 为 `0`。前端上传使用 `multipart/form-data` 的 `file` 字段，读取上传返回的 `url` 或 `imageUrl` 后，将引用以 `imageUrl` 字段提交到本接口。上传接口由陈昂雄维护。

### 首页列表

`GET /api/posts?page=1&pageSize=20` 无请求体。`page` 默认 `1`，范围 `1–100000`，`pageSize` 默认 `20`，范围 `1–50`。按 `created_at DESC, id DESC` 排序。

只返回 `PUBLIC + PUBLISHED` 的印记。`data` 为 `{ items, total, page, pageSize }`，其中 `items` 的每项使用下方印记结构，`total` 为公开且已发布的总条数。没有结果或页码超过末页时，`items` 为 `[]`，保留真实 `total`。

```json
{
  "code": 0,
  "message": "success",
  "data": { "items": [], "total": 0, "page": 1, "pageSize": 20 }
}
```

### 详情与公共印记结构

`GET /api/posts/{id}` 无请求体，`id` 为有符号 BIGINT 范围内的正整数。首页、详情和搜索共用可见性规则。找不到印记、已下架、已删除或非公开时，均返回 HTTP `404`、业务码 `40401`。

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "4",
    "title": "校园晚霞",
    "content": "运动场旁边的晚霞很漂亮。",
    "author": { "id": "2", "nickname": "小光", "avatarUrl": null },
    "imageUrl": "https://example.test/uploads/sunset.jpg",
    "mood": "开心",
    "campusLocation": "运动场",
    "tags": [{ "id": "2", "name": "摄影" }, { "id": "5", "name": "校园生活" }],
    "commentCount": 0,
    "createdAt": "2026-11-25 15:00:00"
  }
}
```

印记、作者、标签 ID 一律为字符串。`imageUrl`、`mood`、`campusLocation` 未设置时为 `null`，`tags` 没有有效标签时为 `[]`。标签只展示 `ACTIVE` 项并按 ID 升序排列。`commentCount` 只统计 `PUBLISHED` 评论。`createdAt` 使用数据库时间，格式为 `yyyy-MM-dd HH:mm:ss`。

### 印记接口常见错误

| HTTP 状态 | 业务码 | 场景 |
| --- | --- | --- |
| `400` | `40001` | JSON 格式或字段类型错误，标题/正文缺失或超限，可选字段超限，图片引用无效，标签无效或已停用，分页或路径 ID 无效 |
| `401` | `40101` | 缺少有效登录身份、Token 失效或账号被禁用 |
| `404` | `40401` | 详情对应的印记不存在或不可见 |
| `415` | `41501` | 发布请求使用不支持的 Content-Type |
| `422` | `42201` | 标题或正文命中敏感词，页面展示响应 `message` |
| `500` | `50001` | 服务异常或数据库写入失败，发布事务回滚 |

页面自动重试或再次提交属于新的发布请求。网络中断后应先刷新首页确认是否已经发布。

## 管理后台接口

五个接口都需要 `Authorization: Bearer <token>`，且调用者当前必须是启用的管理员（`ACTIVE`、`ADMIN`）。

成功统一返回 HTTP `200` 和 `ApiResponse<T>`。ID 在响应中使用字符串，路径中的 `id` 为正整数。所有接口共用下列错误。

| HTTP 状态 | 业务错误码 | 场景 |
| --- | --- | --- |
| `401` | `40101` | 缺少有效 JWT、JWT 已过期或账号已禁用 |
| `403` | `40301` | 当前账号为启用的普通用户 |
| `500` | `50001` | 服务端异常 |

### 分页

用户列表和印记列表共用以下查询参数。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | 整数 | 否 | `1` | 页码，范围 `1` 至 `100000` |
| `pageSize` | 整数 | 否 | `20` | 每页条数，范围 `1` 至 `50` |

响应的 `data.items` 为当前页数组，`total` 为筛选后、分页前的总条数，`page` 和 `pageSize` 为本次使用的分页值。空页返回 `items: []`。列表按各自的时间字段倒序，同一时间按 ID 倒序。

### 后台用户查询

`GET /api/admin/users`

只查询角色为 `USER` 的账号。默认包含启用和禁用账号，按账号创建时间倒序。查询参数可以和分页参数组合使用。

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | 字符串 | 否 | 去除首尾空白后，按用户名或昵称做包含匹配。空字符串等同于省略，最多 `100` 个字符。 |
| `status` | 字符串 | 否 | `ACTIVE` 或 `DISABLED`。省略时查询两种状态。 |

```http
GET /api/admin/users?keyword=%E5%B0%8F%E6%9E%97&status=ACTIVE&page=1&pageSize=20
```

`items` 中每项包含字符串字段 `id`（用户 ID）、`username`、`nickname`、`status` 和 `createdAt`（账号创建时间）。

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "12",
        "username": "lin",
        "nickname": "小林",
        "status": "ACTIVE",
        "createdAt": "2026-11-24 09:30:00"
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20
  }
}
```

分页参数无效、关键词超长或账号状态不在允许值内时，返回 HTTP `400` / `40001`。

### 后台印记列表

`GET /api/admin/posts`

默认返回公开和私密印记中的已发布、已下架内容，按发布时间倒序。两个筛选条件可同时使用，已删除印记始终排除。

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `visibility` | 字符串 | 否 | `PUBLIC` 或 `PRIVATE`。省略时查询两种可见性。 |
| `status` | 字符串 | 否 | `PUBLISHED` 或 `OFFLINE`。省略时查询两种状态。 |

```http
GET /api/admin/posts?visibility=PRIVATE&status=PUBLISHED&page=1&pageSize=20
```

| 列表项字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | 字符串 | 印记 ID |
| `title` | 字符串 | 标题 |
| `author` | 对象 | `id` 和 `nickname` 为字符串，`avatarUrl` 为字符串或 `null` |
| `visibility` | 字符串 | `PUBLIC` 或 `PRIVATE` |
| `status` | 字符串 | `PUBLISHED` 或 `OFFLINE` |
| `createdAt` | 字符串 | 发布时间 |

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "42",
        "title": "图书馆自习",
        "author": {"id": "12", "nickname": "小林", "avatarUrl": null},
        "visibility": "PRIVATE",
        "status": "PUBLISHED",
        "createdAt": "2026-11-24 14:20:00"
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20
  }
}
```

分页或筛选参数无效时，返回 HTTP `400` / `40001`。

### 后台印记详情

`GET /api/admin/posts/{id}`

路径参数 `id` 为必填的印记 ID。详情和列表的可见范围相同，管理员可以打开私密或已下架印记。

```http
GET /api/admin/posts/42
```

`data` 包含印记列表项的所有字段，另有以下字段。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | 字符串 | 正文 |
| `imageUrl` | 字符串或 `null` | 图片地址。无图时为 `null`。 |
| `tags` | 对象数组 | 每项包含字符串 `id` 和 `name`。无标签时为 `[]`。 |
| `mood` | 字符串或 `null` | 心情 |
| `campusLocation` | 字符串或 `null` | 校园地点 |

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "42",
    "title": "图书馆自习",
    "content": "今天在图书馆完成软件工程项目。",
    "author": {"id": "12", "nickname": "小林", "avatarUrl": null},
    "imageUrl": "https://example.test/uploads/library.jpg",
    "tags": [{"id": "1", "name": "学习"}],
    "mood": "平静",
    "campusLocation": "图书馆",
    "visibility": "PRIVATE",
    "status": "PUBLISHED",
    "createdAt": "2026-11-24 14:20:00"
  }
}
```

印记 ID 格式无效时返回 HTTP `400` / `40001`。印记不存在或已删除时返回 HTTP `404` / `40401`。

### 下架印记

`PATCH /api/admin/posts/{id}/status`

路径参数 `id` 为必填的印记 ID。请求使用 `Content-Type: application/json`，请求体中的 `status` 为必填字符串，取值为 `OFFLINE`。

```http
PATCH /api/admin/posts/42/status
Content-Type: application/json

{"status": "OFFLINE"}
```

公开和私密的已发布印记都可以下架。重复下架仍返回成功。成功响应的 `data.id` 是字符串形式的印记 ID，`data.status` 为 `OFFLINE`。

```json
{
  "code": 0,
  "message": "success",
  "data": {"id": "42", "status": "OFFLINE"}
}
```

印记 ID、JSON 请求体或状态值无效时返回 HTTP `400` / `40001`。印记不存在或已删除时返回 HTTP `404` / `40401`。请求内容类型不受支持时返回 HTTP `415` / `41501`。

```json
{
  "code": 40401,
  "message": "印记不存在或已不可见",
  "data": null
}
```

下架后的印记仍可在后台列表和详情中查看。普通用户刷新首页或搜索时看不到该印记，直接请求详情、评论列表或发表评论也会被拒绝。

### 后台基础统计

`GET /api/admin/statistics`

无需请求参数。返回三个非负整数，按全量数据统计，与列表当前的筛选和分页无关。

| 字段 | 统计口径 |
| --- | --- |
| `userCount` | 启用和禁用的普通用户总数，排除管理员。 |
| `postCount` | 公开和私密印记中已发布、已下架的总数，排除已删除印记。 |
| `commentCount` | 上述印记下未删除的评论总数，包含已下架印记的未删除评论。 |

```http
GET /api/admin/statistics
```

```json
{
  "code": 0,
  "message": "success",
  "data": {"userCount": 12, "postCount": 18, "commentCount": 27}
}
```

下架操作不影响这三个统计值。鉴权和服务端错误见本节共用错误码。

## 第二周新增与扩展接口契约大纲

> 各后端模块负责人在开发对应模块时，直接在本节对应子表下方按末尾《单个接口记录模板》补充请求/响应示例，保持路径前缀与枚举统一。所有 `BIGINT` ID 一律使用字符串传输。

### 1. 账号扩展、关注与好友关系

| 功能 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 普通用户注册 | POST | `/api/auth/register` | 公开 |
| 修改登录密码 | PUT | `/api/users/me/password` | 需登录 |
| 修改个人资料与兴趣标签 | PUT | `/api/users/me/profile` | 需登录 |
| 查看他人公开主页（含同频度） | GET | `/api/users/{id}/profile` | 需登录 |
| 关注 / 取消关注用户 | POST / DELETE | `/api/users/{id}/follow` | 需登录 |
| 关注列表 / 粉丝列表 | GET | `/api/users/{id}/following`、`/followers` | 需登录 |
| 发起好友申请 / 申请列表 | POST / GET | `/api/friends/requests` | 需登录 |
| 审批好友申请（通过/拒绝） | PATCH | `/api/friends/requests/{id}` | 需登录 |
| 好友列表 / 解除好友 | GET / DELETE | `/api/friends`、`/api/friends/{friendId}` | 需登录 |

### 2. 多媒体印记扩展、评分、回响与二级评论

| 功能 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 短视频上传（MP4）与读取 | POST / GET | `/api/files/videos`、`/api/files/videos/{name}` | 上传需登录 / 读取公开 |
| 编辑 / 删除本人印记 | PUT / DELETE | `/api/posts/{id}` | 需登录（仅作者本人） |
| 首页分栏流（`tab=latest/following/similar/hot/circle`） | GET | `/api/posts` | 需登录 |
| 多条件组合搜索（扩展日期/心情/地点/媒体/圈子/排序） | GET | `/api/posts/search` | 需登录 |
| 印记评分（1～5 星） | PUT | `/api/posts/{id}/rating` | 需登录 |
| 时光回响与收藏切换（`RESONATE/WARM/FUNNY/CHEER/FAVORITE`） | POST / DELETE | `/api/posts/{id}/reactions` | 需登录 |
| 发表二级回复 / 删除本人评论 | POST / DELETE | `/api/posts/{id}/comments`、`/api/comments/{commentId}` | 需登录 |
| 提交内容或评论举报 | POST | `/api/reports` | 需登录 |

### 3. 个人记忆四件套（时光轴 / 故事集 / 胶囊 / 年报）

| 功能 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 个人四维时光轴（时间归档/去年今日/心情统计/校园足迹） | GET | `/api/memories/timeline` | 需登录 |
| 我的收藏与回响列表 | GET | `/api/memories/favorites` | 需登录 |
| 时光胶囊列表（未到期隐藏正文与媒体） | GET | `/api/memories/capsules` | 需登录 |
| 年度拾光报告统计数据 | GET | `/api/memories/annual-report` | 需登录 |
| 故事集列表 / 创建故事集 | GET / POST | `/api/stories` | 需登录 |
| 故事集详情 / 编辑 / 删除 | GET / PUT / DELETE | `/api/stories/{id}` | 需登录（作者或好友协作者可添加印记） |
| 管理故事集好友协作者与印记编排 | POST / DELETE | `/api/stories/{id}/collaborators`、`/posts` | 需登录 |

### 4. 同频圈子、话题、共振、双人时光线与校园搭子

| 功能 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 同频话题详情与聚合印记 | GET | `/api/topics/{tagId}` | 需登录 |
| 同频圈子列表 / 创建圈子 / 圈子详情 | GET / POST | `/api/circles`、`/api/circles/{id}` | 需登录 |
| 加入 / 退出圈子 | POST / DELETE | `/api/circles/{id}/members` | 需登录 |
| 圈主管理圈资料 / 移除圈内印记 / 处理圈内举报 | PUT / DELETE / PATCH | `/api/circles/{id}/manage/*` | 圈主或圈管理员 |
| 同频用户推荐列表（含加权同频度与共同点） | GET | `/api/social/similar-users` | 需登录 |
| 时光共振聚合列表 | GET | `/api/social/resonances` | 需登录 |
| 好友/互关用户双人时光线 | GET | `/api/social/dual-timeline/{otherUserId}` | 需登录 |
| 校园搭子招募列表 / 发布 / 报名参与 | GET / POST | `/api/buddies`、`/api/buddies/{id}/signup` | 需登录 |

### 5. 即时通讯（好友单聊 + 拉群聊）与站内通知

| 功能 | 方法 / 协议 | 路径 | 权限 |
| --- | --- | --- | --- |
| 实时聊天连接（握手参数或首帧携带 JWT） | WebSocket | `/ws/chat` | 需登录 |
| 会话列表 / 发起单聊或勾选好友拉群聊 | GET / POST | `/api/chats/conversations` | 需登录 |
| 群聊改名 / 邀请好友入群 / 移出成员 / 退群 | PATCH / POST / DELETE | `/api/chats/conversations/{id}/members` | 群成员 / 群主 |
| 历史消息分页 / HTTP 发送消息兜底 / 标记已读 | GET / POST / PUT | `/api/chats/conversations/{id}/messages` | 会话成员 |
| 站内通知列表 / 未读总数 / 一键已读 | GET / PUT | `/api/notifications`、`/api/notifications/read-all` | 需登录 |

### 6. 「拾光助手」智能 Agent 与 Web 后台扩展

| 功能 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 发布文案润色与标签/心情/圈子推荐 | POST | `/api/agent/polish` | 需登录 |
| 自然语言检索、故事集草稿生成与同频推荐 | POST | `/api/agent/chat` | 需登录 |
| 后台 ECharts 多维统计大屏数据 | GET | `/api/admin/dashboard` | 管理员 |
| 后台禁用 / 恢复启用用户 | PATCH | `/api/admin/users/{id}/status` | 管理员 |
| 后台恢复上架或删除印记 / 查询与删除评论 | PATCH / DELETE / GET | `/api/admin/posts/{id}/status`、`/api/admin/comments` | 管理员 |
| 后台话题与圈子管理 | GET / PATCH | `/api/admin/circles`、`/api/admin/tags` | 管理员 |
| 后台举报列表与审核结案 | GET / PATCH | `/api/admin/reports`、`/api/admin/reports/{id}` | 管理员 |
| 后台敏感词库增删查 / 操作审计日志查询 | GET / POST / DELETE | `/api/admin/sensitive-words`、`/api/admin/audit-logs` | 管理员 |

## 单个接口记录模板

### 接口名称

- 状态：待设计 / 待开发 / 开发中 / 可联调 / 已验收
- 方法与路径：
- 权限：
- 请求参数：
- 请求示例：
- 成功响应：
- 错误码：
- 备注：
