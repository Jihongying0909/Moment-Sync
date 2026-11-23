# 数据库设计说明

第一周核心表：

- `user`：普通用户和管理员账号。
- `post`：时光印记主体、标签以外的展示字段和内容状态。
- `post_media`：印记图片。
- `tag`：预设标签。
- `post_tag`：印记与标签的多对多关系。
- `comment`：评论。

第一周将心情和校园地点直接保存在 `post` 表中，减少联调复杂度。所有字段修改必须同步更新 `database/schema.sql` 和 `docs/api.md`。

## 状态约定

- 用户状态：`ACTIVE`、`DISABLED`
- 内容状态：`PUBLISHED`、`OFFLINE`、`DELETED`
- 用户角色：`USER`、`ADMIN`

