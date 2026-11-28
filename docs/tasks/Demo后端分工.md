# Demo 后端分工

吴家裔已完成 JWT 鉴权和角色权限，陈昂雄已完成搜索、评论和敏感词检查。这些工作计入 Demo 的总工作量。两人继续补齐各自功能的接口与联调，廖子豪负责印记业务，周黎负责管理后台。

## 吴家裔 · 账号与登录

开发位置为 `backend/src/main/java/com/momentsync/auth/`。

- 实现普通用户登录 `POST /api/auth/login` 和当前用户信息 `GET /api/users/me`。
- 实现管理员登录 `POST /api/admin/auth/login`。两个登录入口共用 BCrypt 密码校验和现有 JWT 签发逻辑，管理员入口校验管理员角色。
- 准备普通用户和管理员演示账号，分别和小程序、管理后台同学验证登录。

## 陈昂雄 · 配置、上传与搜索评论联调

配置接口放在 `backend/src/main/java/com/momentsync/config/`，图片上传放在 `backend/src/main/java/com/momentsync/media/`。现有搜索和评论代码分别在同一目录下的 `post/` 和 `comment/`。

- 实现 `GET /api/tags`、`GET /api/moods`、`GET /api/locations`，和廖子豪一起确认发布时提交的值。
- 实现单张图片上传 `POST /api/files/images`，和廖子豪约定上传返回的图片引用及发布时的提交方式。
- 搜索和评论已接入后端。本轮用团队环境和真实小程序验证关键词与标签搜索、发表评论、刷新后查看评论，以及登录失效时的提示。

本轮交付（2026-11-25）：三个配置接口与图片上传/读取已实现，详见 [陈昂雄模块说明](../modules/search-comment-cax.md)。已提供接口契约、公共错误处理和自动化测试。真实账号登录、印记发布与团队微信真机联合验收仍需对应负责人协作。

## 廖子豪 · 印记发布、首页和详情

开发位置为 `backend/src/main/java/com/momentsync/post/`。

- 实现发布印记 `POST /api/posts`，在同一事务内保存印记、图片关联和标签关联。发布前用 `moderation.ContentPolicy` 检查标题和正文。
- 实现首页列表 `GET /api/posts` 和详情 `GET /api/posts/{id}`。首页和普通用户详情沿用搜索的公开规则，只展示公开且已发布的印记。
- 和陈昂雄联调配置、上传和发布，与小程序同学验证发布后能从首页进入详情。

## 周黎 · 管理后台

开发位置为 `backend/src/main/java/com/momentsync/admin/`。管理员登录由吴家裔在 `auth/` 实现。

- 实现后台用户查询 `GET /api/admin/users`、内容列表 `GET /api/admin/posts` 和内容详情。开工时和管理后台同学确定详情路径及字段。
- 实现下架 `PATCH /api/admin/posts/{id}/status` 和基础统计 `GET /api/admin/statistics`，统计用户数、印记数和评论数。联调前确定统计是否包含禁用账号、已下架印记和已删除评论。
- 组织后端接口确认和代码审核，和廖子豪、陈昂雄一起验收下架后的首页、详情、搜索和评论行为。

## 开发与验收

1. 廖子豪先用演示标签和图片引用开发发布，周黎用已有账号和印记开发后台。需要鉴权的接口可以用集成测试签发 JWT，等登录接口可用后再用演示账号联调。
2. 普通用户链路按登录、上传、发布、首页与详情、搜索与评论的顺序验收。管理员下架后，印记应从首页和搜索结果消失，普通用户无法查看详情、读取评论或发表评论。
3. 每人负责为自己新增的接口补充单元测试和集成测试，测试代码放在 `backend/src/test/java/com/momentsync/` 下。
