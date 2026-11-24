# API 文档

## 统一约定

- 基础路径：`/api`
- 数据格式：`application/json`
- 鉴权头：`Authorization: Bearer <token>`
- 时间格式：`yyyy-MM-dd HH:mm:ss`

### 统一响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

## 接口清单

| 模块 | 方法 | 路径 | 权限 | 负责人 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 用户 | POST | `/api/auth/login` | 公开 | 廖子豪 | 待设计 |
| 用户 | GET | `/api/users/me` | 用户 | 廖子豪 | 待设计 |
| 配置 | GET | `/api/tags` | 用户 | 段晨鸣 | 待设计 |
| 配置 | GET | `/api/moods` | 用户 | 段晨鸣 | 待设计 |
| 配置 | GET | `/api/locations` | 用户 | 段晨鸣 | 待设计 |
| 印记 | POST | `/api/posts` | 用户 | 江可妮 | 待设计 |
| 印记 | GET | `/api/posts` | 用户 | 江可妮 | 待设计 |
| 印记 | GET | `/api/posts/{id}` | 用户 | 江可妮 | 待设计 |
| 媒体 | POST | `/api/files/images` | 用户 | 江可妮 | 待设计 |
| 搜索 | GET | `/api/posts/search` | 用户 | 陈昂雄 | 可联调（独立模块） |
| 评论 | POST | `/api/posts/{id}/comments` | 用户 | 陈昂雄 | 可联调（独立模块） |
| 评论 | GET | `/api/posts/{id}/comments` | 用户 | 陈昂雄 | 可联调（独立模块） |
| 后台 | POST | `/api/admin/auth/login` | 公开 | 周黎 | 待设计 |
| 后台 | GET | `/api/admin/users` | 管理员 | 周黎 | 待设计 |
| 后台 | GET | `/api/admin/posts` | 管理员 | 周黎 | 待设计 |
| 后台 | PATCH | `/api/admin/posts/{id}/status` | 管理员 | 周黎 | 待设计 |
| 后台 | GET | `/api/admin/statistics` | 管理员 | 周黎 | 待设计 |

## 单个接口记录模板

### 接口名称

- 负责人：
- 状态：待设计 / 开发中 / 可联调 / 已验收
- 方法与路径：
- 权限：
- 请求参数：
- 请求示例：
- 成功响应：
- 错误码：
- 备注：


## 陈昂雄：搜索、评论与基础内容安全

上述三个接口的请求参数、分页结构、成功响应、错误码、鉴权和集成要求见 [模块接口与接入说明](modules/search-comment-cax.md#api-契约)。实现遵循现有表结构和统一响应，模块测试已通过，真实 JWT 与小程序宿主待联调。
