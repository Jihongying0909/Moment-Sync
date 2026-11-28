# 数据库设计说明

所有表主键与外键均采用 `BIGINT`，API 层序列化为**字符串**以防前端精度丢失。所有结构变更必须同步更新 `database/schema.sql`、`database/migrate_v1_to_v2.sql` 和 `docs/api.md`。

## 1. 初始化与本地升级方式

- **全新环境初始化**：依次执行 `database/schema.sql`、`database/seed.sql`。
- **本地已有第一周数据库的增量升级（保留旧数据）**：依次执行 `database/migrate_v1_to_v2.sql` → `database/schema.sql` → `database/seed.sql`。

## 2. 数据表全景（共 26 张表）

### 2.1 账号、标签与同频圈子
- `user`：普通用户与系统管理员账号（第二周新增 `bio` 个人简介）。
- `tag`：预设标签与公开同频话题字典（第二周新增 `description` 话题说明）。
- `user_interest_tag`：用户主动公开的个人兴趣标签（用于计算印记同频度）。
- `circle`：同频兴趣圈子（摄影圈、编程圈等，`owner_id` 为圈主）。
- `circle_member`：圈子成员及圈内角色（`OWNER` / `MANAGER` / `MEMBER`）。

### 2.2 时光印记、多媒体与深度互动
- `post`：时光印记主体（第二周兼容新增 `event_date` 事件日期、`circle_id` 所属圈子、`unlock_at` 时光胶囊开启时间、`rating_avg` 平均星级、`rating_count` 评分人数）。
- `post_media`：印记多媒体资源，按 `sort_order` 排序（支持多图 `IMAGE` 与短视频 `VIDEO`）。
- `post_tag`：印记与标签多对多关联。
- `comment`：印记评论（第二周兼容新增 `parent_id` 支持二级回复）。
- `post_rating`：1～5 星内容评分（`(post_id, user_id)` 唯一约束）。
- `post_reaction`：时光回响与收藏（`RESONATE` 共鸣 / `WARM` 温暖 / `FUNNY` 有趣 / `CHEER` 加油 / `FAVORITE` 收藏）。

### 2.3 关注、好友与记忆故事集
- `user_follow`：单向关注关系。
- `friend_request`：好友申请记录（`PENDING` / `ACCEPTED` / `REJECTED`）。
- `friendship`：双向好友关系（成对写入 `(user_id, friend_id)` 与 `(friend_id, user_id)` 以便高效查询）。
- `story_collection`：记忆故事集主体。
- `story_collaborator`：好友共建故事集协作者。
- `story_post`：故事集所含印记及排序（记录 `added_by` 添加人）。

### 2.4 即时通讯（单聊 + 群聊）、校园搭子与站内通知
- `chat_conversation`：统一会话表（`DIRECT` 好友单聊通过 `direct_key` 如 `'2:3'` 唯一去重；`GROUP` 群聊记录群名与群主 `owner_id`）。
- `chat_member`：会话成员与未读游标（`last_read_message_id`）。
- `chat_message`：聊天消息表（`TEXT` 文字 / `IMAGE` 图片 / `POST_SHARE` 印记分享卡 / `POSTCARD` 预留第三周风物明信片）。
- `buddy_post` / `buddy_signup`：校园搭子招募卡片与报名记录（可通过 `conversation_id` 关联群聊）。
- `notification`：站内消息通知（关注、好友申请、评论回复、回响、举报结果、胶囊解锁）。

### 2.5 社区治理、敏感词与审计
- `content_report`：用户对印记或评论的违规举报及审核处理记录（支持全站与圈内举报）。
- `sensitive_word`：在线维护的动态敏感词表。
- `admin_audit_log`：管理员关键写操作审计日志。

## 3. 核心状态与枚举约定

- **用户角色 / 状态**：`USER`、`ADMIN` ｜ `ACTIVE`、`DISABLED`
- **印记可见范围**：`PUBLIC`（所有人可见）、`CONNECTIONS`（仅关注者或好友可见）、`PRIVATE`（仅自己可见）
- **印记状态**：`PUBLISHED`、`PENDING_REVIEW`、`OFFLINE`、`DELETED`
- **媒体类型**：`IMAGE`、`VIDEO`
- **圈内角色**：`OWNER`、`MANAGER`、`MEMBER`
- **举报状态**：`PENDING`、`RESOLVED`、`REJECTED`
