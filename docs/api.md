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
| `422` | `42201` | 内容命中敏感词 |
| `500` | `50001` | 未预期的服务异常 |
| `503` | `50301` | 健康检查发现数据库不可用 |

新增接口复用 `ApiResponse` 和已定义的错误码，业务校验失败用 `ApiException` 表达。需要新增业务错误码时，在对应接口条目里记录 HTTP 状态、错误码和含义。未预期的异常保留服务端日志，响应只给通用提示。

当前 `ApiExceptionHandler` 覆盖 `post` 和 `comment` 包。鉴权过滤器单独处理 `401`、`403`，并使用同一响应结构。接入 `auth`、`config`、`media`、`admin` 的新业务接口时，统一将它们纳入公共异常处理，并验证各自的错误响应。

## 接口清单

| 功能 | 方法 | 路径 | 权限 | 状态 |
| --- | --- | --- | --- | --- |
| 健康检查 | GET | `/api/health` | 公开 | 可联调 |
| 普通用户登录 | POST | `/api/auth/login` | 公开 | 待开发 |
| 当前用户信息 | GET | `/api/users/me` | 需登录 | 待开发 |
| 管理员登录 | POST | `/api/admin/auth/login` | 公开，登录时校验管理员角色 | 待开发 |
| 标签配置 | GET | `/api/tags` | 需登录 | 待开发 |
| 心情配置 | GET | `/api/moods` | 需登录 | 待开发 |
| 地点配置 | GET | `/api/locations` | 需登录 | 待开发 |
| 单张图片上传 | POST | `/api/files/images` | 需登录 | 联调契约已记录，后端待开发 |
| 发布印记 | POST | `/api/posts` | 需登录 | 联调契约已记录，后端待开发 |
| 首页印记列表 | GET | `/api/posts` | 需登录 | 联调契约已记录，后端待开发 |
| 印记详情 | GET | `/api/posts/{id}` | 需登录 | 联调契约已记录，后端待开发 |
| 搜索印记 | GET | `/api/posts/search` | 需登录 | 可联调 |
| 发表评论 | POST | `/api/posts/{id}/comments` | 需登录 | 可联调 |
| 评论列表 | GET | `/api/posts/{id}/comments` | 需登录 | 可联调 |
| 后台用户查询 | GET | `/api/admin/users` | 管理员 | 待开发 |
| 后台内容列表 | GET | `/api/admin/posts` | 管理员 | 待开发 |
| 后台内容详情 | GET | `/api/admin/posts/{id}` | 管理员 | 待开发 |
| 内容下架 | PATCH | `/api/admin/posts/{id}/status` | 管理员 | 待开发 |
| 后台基础统计 | GET | `/api/admin/statistics` | 管理员 | 待开发 |

## 已实现接口

- 健康检查连库成功时返回 HTTP `200` 和 `data.database: UP`，连库失败时返回 HTTP `503` 和错误码 `50301`。
- 搜索、评论列表和发表评论已接入后端，集成测试覆盖真实 JWT，待团队环境和小程序联调。请求参数、返回字段和错误码见 [搜索评论接口契约](modules/search-comment-cax.md#api-契约)。

## 待开发接口的联调约定

- 普通用户登录和管理员登录共用 BCrypt 密码校验及 JWT 签发逻辑。管理员登录还需检查账号角色。
- 印记前端与搜索评论模块按下文“印记与图片上传接口”对齐。标签、心情和地点的具体选项由配置接口提供；标签提交字符串 ID，心情和地点提交名称字符串。发布时检查标题和正文的敏感词。
- 普通用户在首页、详情和搜索只能看到公开且已发布的印记。下架后，这些入口不再展示该印记，普通用户也无法读取或发布其评论。

## 印记与图片上传接口

2026-11-25 记录本次确认的联调约定，字段与江可妮的印记前端、现有搜索响应对齐。以下四个后端接口在当前 `develop` 中仍待开发；记录契约不代表接口已实现或已通过真实联调。上传负责人需按本节确认并落实文件字段、返回地址和大小配置，后端实现者需同步字段校验、敏感词校验及可见性规则。

四个接口都需要 `Authorization: Bearer <token>`。成功统一返回 HTTP `200` 和 `{code: 0, message: "success", data: ...}`。印记、作者、标签的 ID 在响应中均为字符串，客户端不能先转换成 JavaScript Number。

### 单张图片上传

`POST /api/files/images`

- 请求为 `multipart/form-data`，文件字段名固定为 `file`，每次上传一张图片。
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

上传客户端使用 `wx.uploadFile` 的 `name: "file"`；由上传 API 生成 multipart boundary，不手动设置 `Content-Type: application/json`。接入上传 Controller 时需纳入公共异常处理，使失败响应中的 `message` 可以直接用于页面提示。

### 发布印记

`POST /api/posts`

请求为 `application/json`。采用现有前端的必填项与数量限制，后端也必须校验。文本长度与前端 JavaScript `String.length` 保持一致，按 UTF-16 码元计数；标题和正文去除首尾空白后校验，不能只有空白。

| 字段 | 类型 | 必填 | 约定 |
| --- | --- | --- | --- |
| `title` | 字符串 | 是 | 长度 `1–100` |
| `content` | 字符串 | 是 | 长度 `1–2000` |
| `imageUrl` | 字符串 | 否 | 最多一张，使用上传接口返回的远端地址；无图时省略 |
| `tagIds` | 字符串数组 | 否 | 最多 `5` 个标签 ID，例如 `["2", "5"]`；不选时传 `[]` 或省略 |
| `mood` | 字符串 | 否 | 配置接口提供的心情名称，例如 `开心`；不选时省略 |
| `campusLocation` | 字符串 | 否 | 配置接口提供的校园地点名称，例如 `运动场`；不选时省略 |

```json
{
  "title": "校园晚霞",
  "content": "运动场旁边的晚霞很漂亮。",
  "imageUrl": "https://example.test/uploads/campus.jpg",
  "mood": "开心",
  "campusLocation": "运动场",
  "tagIds": ["2", "5"]
}
```

无图、无标签、无心情或地点的文字印记允许发布。作者身份从已验证的登录上下文获得，不接受客户端指定作者。后端在写入前分别调用 `com.momentsync.moderation.ContentPolicy.check(title)` 与 `check(content)`，命中敏感词返回 HTTP `422` / `42201`，不得创建印记。

**成功响应的 `data` 返回完整印记对象，结构见下文。** 必须包含新建印记的字符串 `id`；不能只返回成功消息、布尔值或裸 ID，否则现有前端会判断为发布响应异常。前端拿到成功响应后刷新首页。

### 印记对象

首页列表的 `data.items` 中每项、详情的 `data`、发布成功的 `data` 均采用此结构，与搜索结果的单项字段一致。

| 字段 | 类型 | 约定 |
| --- | --- | --- |
| `id` | 字符串 | 印记 ID |
| `title`、`content` | 字符串 | 标题和正文 |
| `author` | 对象 | `{id, nickname, avatarUrl}`；`id` 为字符串，`avatarUrl` 可为 `null` |
| `imageUrl` | 字符串或 `null` | 单张图片的可访问地址，无图为 `null` |
| `mood`、`campusLocation` | 字符串或 `null` | 心情和校园地点名称，未选为 `null` |
| `tags` | 对象数组 | `[{id, name}]`，`id` 为字符串，无标签为 `[]` |
| `commentCount` | 非负整数 | 当前印记下状态为 `PUBLISHED` 的评论数量 |
| `createdAt` | 字符串 | `yyyy-MM-dd HH:mm:ss` |

发布或详情成功响应示例（ID、时间以数据库实际值为准）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "2",
    "title": "校园晚霞",
    "content": "运动场旁边的晚霞很漂亮。",
    "author": {"id": "3", "nickname": "小频", "avatarUrl": null},
    "imageUrl": "https://example.test/uploads/campus.jpg",
    "mood": "开心",
    "campusLocation": "运动场",
    "tags": [{"id": "2", "name": "摄影"}, {"id": "5", "name": "校园生活"}],
    "commentCount": 0,
    "createdAt": "2026-11-25 12:00:00"
  }
}
```

### 首页印记列表

`GET /api/posts?page=1&pageSize=20`

`page` 默认 `1`，范围 `1–100000`；`pageSize` 默认 `20`，范围 `1–50`。只返回 `status=PUBLISHED` 且 `visibility=PUBLIC` 的印记，按 `created_at DESC, id DESC` 排序。

`data` 必须是分页对象 `{items, total, page, pageSize}`，不能直接返回单个印记或裸数组。`items` 中每项是上文的印记对象；`total` 是筛选后、分页前的总条数。空结果或超出末页时，`items` 为 `[]`，仍返回 HTTP `200`。示例为没有可展示印记的首页：

```json
{
  "code": 0,
  "message": "success",
  "data": {"items": [], "total": 0, "page": 1, "pageSize": 20}
}
```

### 印记详情

`GET /api/posts/{id}`

`id` 为正整数形式的印记 ID。成功时 `data` 直接为上文的印记对象，不包裹 `items`。只允许读取公开且已发布的印记；不存在、下架、删除或非公开内容返回 HTTP `404` / `40401`。

详情页已挂载评论组件，通过 `countchange` 事件中 `loaded: true` 的 `total` 更新页面评论数量。评论列表与发表评论继续使用 [搜索评论接口契约](modules/search-comment-cax.md#api-契约)。

### 印记校验与可见性错误

| HTTP 状态 | 业务错误码 | 场景 |
| --- | --- | --- |
| `400` | `40001` | 印记请求字段、长度、标签数量、路径 ID 或分页参数不合法 |
| `401` | `40101` | 缺少有效 JWT、JWT 已过期或账号已禁用 |
| `404` | `40401` | 普通用户请求不可见印记的详情、评论列表或发表评论 |
| `422` | `42201` | 标题、正文或评论命中敏感词 |
| `500` | `50001` | 服务异常 |

失败均返回 `{code, message, data: null}`，前端展示 `message`。下架后的内容从首页和搜索列表中排除，**列表请求仍正常返回 HTTP `200`**；详情和评论读写返回 `404` / `40401`。管理员查看已下架内容遵循下文后台接口的独立权限规则。

## 管理后台接口

接口契约待确认。五个接口都需要 `Authorization: Bearer <token>`，且调用者当前必须是启用的管理员（`ACTIVE`、`ADMIN`）。

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
