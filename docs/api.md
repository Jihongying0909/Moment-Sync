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
| `404` | `40401` | 印记不存在或当前用户不可见 |
| `415` | `41501` | 请求的 `Content-Type` 不受支持 |
| `422` | `42201` | 内容命中敏感词 |
| `500` | `50001` | 未预期的服务异常 |
| `503` | `50301` | 健康检查发现数据库不可用 |

新增接口复用 `ApiResponse` 和已定义的错误码，业务校验失败用 `ApiException` 表达。需要新增业务错误码时，在对应接口条目里记录 HTTP 状态、错误码和含义。未预期的异常保留服务端日志，响应只给通用提示。

当前 `ApiExceptionHandler` 覆盖 `auth`、`post`、`comment` 和 `admin` 包。鉴权过滤器单独处理 `401`、`403`，并使用同一响应结构。接入 `config`、`media` 的新业务接口时，统一将它们纳入公共异常处理，并验证各自的错误响应。

## 接口清单

| 功能 | 方法 | 路径 | 权限 | 状态 |
| --- | --- | --- | --- | --- |
| 健康检查 | GET | `/api/health` | 公开 | 可联调 |
| 普通用户登录 | POST | `/api/auth/login` | 公开 | 可联调 |
| 当前用户信息 | GET | `/api/users/me` | 需登录 | 可联调 |
| 管理员登录 | POST | `/api/admin/auth/login` | 公开，登录时校验管理员角色 | 可联调 |
| 标签配置 | GET | `/api/tags` | 需登录 | 待开发 |
| 心情配置 | GET | `/api/moods` | 需登录 | 待开发 |
| 地点配置 | GET | `/api/locations` | 需登录 | 待开发 |
| 单张图片上传 | POST | `/api/files/images` | 需登录 | 待开发 |
| 发布印记 | POST | `/api/posts` | 需登录 | 待开发 |
| 首页印记列表 | GET | `/api/posts` | 需登录 | 待开发 |
| 印记详情 | GET | `/api/posts/{id}` | 需登录 | 待开发 |
| 搜索印记 | GET | `/api/posts/search` | 需登录 | 可联调 |
| 发表评论 | POST | `/api/posts/{id}/comments` | 需登录 | 可联调 |
| 评论列表 | GET | `/api/posts/{id}/comments` | 需登录 | 可联调 |
| 后台用户查询 | GET | `/api/admin/users` | 管理员 | 可联调 |
| 后台内容列表 | GET | `/api/admin/posts` | 管理员 | 可联调 |
| 后台内容详情 | GET | `/api/admin/posts/{id}` | 管理员 | 待开发 |
| 内容下架 | PATCH | `/api/admin/posts/{id}/status` | 管理员 | 待开发 |
| 后台基础统计 | GET | `/api/admin/statistics` | 管理员 | 待开发 |

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

印记 ID、JSON 请求体或状态值无效时返回 HTTP `400` / `40001`。印记不存在或已删除时返回 HTTP `404` / `40401`。

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
