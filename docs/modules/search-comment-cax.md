# 陈昂雄：搜索、评论与基础内容安全

搜索和评论已接入统一的 Spring Boot 后端及 JWT 鉴权，小程序页面仍需按下文接入。

## 范围与文件

- `backend/src/main/java/com/momentsync/` 的 `post`、`comment` 和 `moderation` 包分别包含搜索、评论和内容检查实现。
- `miniprogram/modules/search-comment`：原生微信小程序搜索入口、搜索及结果页、评论组件和接口客户端。
- [接口文档](../api.md)和[任务看板](../tasks.md)记录接口及联调进度。

直接使用已有 `post`、`post_tag`、`tag`、`post_media`、`comment`、`user` 表，没有修改数据库字段，没有创建登录、发布、首页、详情页、后台或全局应用骨架。评论删除是本周可选项，本模块不提供删除 API。

前端没有内置假数据、演示令牌或自行约定的登录存储键。后端从注入的鉴权接口获得用户 ID，评论作者不接受客户端指定。

## 后端接入（吴家裔）

按[后端启动说明](../../backend/README.md)构建并启动统一应用。搜索和评论通过 `auth.CurrentUserProvider` 读取已验证的用户身份，三个接口均需要登录。评论作者取自登录身份。

`post` 统一维护 `PUBLISHED + PUBLIC` 可见性规则。搜索使用绑定参数、关键词文字匹配和批量标签查询。评论写入在同一事务中复查账号、锁定并检查目标印记，再向 `comment` 表插入，使下架与评论校验串行。

HTTP 路由为 `/api/posts/search` 和 `/api/posts/{id}/comments`，统一使用 `{code,message,data}` 响应格式。错误码见下文。

## 敏感词接入（江可妮、吴家裔）

评论已自动调用 `ContentPolicy.check(content)`。发布印记时可注入同一个接口，分别对标题、正文调用 `check`。

默认词表在 `backend/src/main/resources/interaction-sensitive-words.txt`，UTF-8，一行一个词，支持空行和 `#` 注释。当前演示词为“敏感词测试”“代开发票”“办假证”。可在后端配置：

```properties
moment.interaction.sensitive-words=file:/absolute/path/sensitive-words.txt
```

缺失或空词表会阻止启动，更新词表后需重启。校验做 NFKC、英文大小写和空白/零宽字符归一化；这是本地子串匹配，不包含完整审核、同音识别或模型能力。命中返回 HTTP 422，不返回具体命中的词。

## 小程序接入（登录、配置、首页与详情负责人）

保留整个 `miniprogram/modules/search-comment` 目录。以下示例中的 `auth`、`configApi`、`postRouter` 是宿主已有功能的适配位置，不是本模块提供的包；按团队实际实现注入。

```js
const interaction = require('./modules/search-comment/services/client');

// App.onLaunch 中调用；必须在打开搜索页或挂载评论组件之前完成。
interaction.configure({
  baseUrl: 'http://localhost:8080/api', // 真机需填可访问的服务地址，并配置 request 合法域名
  getToken: () => auth.getToken(),
  onUnauthorized: () => auth.requireLogin(), // 登录负责人保留当前页面及返回路径
  loadTags: async () => {
    const tags = await configApi.listTags();
    // 必须返回数组；将配置接口的具体 envelope/字段在这里适配。
    return tags.map(tag => ({ id: String(tag.id), name: tag.name }));
  },
  openPost: id => postRouter.openDetail(id)
});
```

`baseUrl` 包含 `/api`，客户端只追加 `/posts/...`。`getToken()` 返回未经 `Bearer ` 前缀包装的 token。可选 `request` 参数可注入兼容 `wx.request(options)` 的公共请求适配器；未传时直接使用 `wx.request`。后端 BIGINT 均使用字符串输出，宿主不得先转成 Number。

由应用入口负责人向 `app.json.pages` **追加**：

```json
"modules/search-comment/pages/search/index"
```

首页 JSON 的 `usingComponents` 追加：

```json
"search-entry": "/modules/search-comment/components/search-entry/index"
```

首页 WXML 在合适位置放 `<search-entry />`。组件的可选 `url` 属性可适配分包路径。

详情页 JSON 的 `usingComponents` 追加：

```json
"comment-panel": "/modules/search-comment/components/comment-panel/index"
```

详情页 WXML 放入以下片段。组件内部已展示真实评论总数；如果详情顶部还需要数量，可监听事件：

```xml
<comment-panel id="comments" post-id="{{post.id}}" bind:countchange="onCommentCountChange" />
```

```js
onCommentCountChange(event) {
  // loaded=false 是切换印记/刷新中，不能把尚未读取的数量显示为已确认值。
  this.setData({ commentCount: event.detail.loaded ? event.detail.total : null });
}
// 在现有下拉刷新逻辑中调用：
// await this.selectComponent('#comments').refresh();
```

组件暴露 `refresh()` 方法、`countchange({total,loaded})` 与 `submitted({postId})` 事件。评论按最新优先分页；发送失败保留草稿；发送成功重新向服务器读取数量。搜索页支持键盘确认、单标签与关键词交集、清空筛选、下拉刷新和分页重试；同一搜索的翻页使用已提交的筛选条件。

搜索结果标签、心情和地点来自后端真实字段。所有内容通过 WXML 文本绑定显示，不使用 `rich-text` 或原始 HTML。

## API 契约

共同规则：需要 `Authorization: Bearer <token>`，JSON 请求/响应；ID 是正整数，响应 ID 为字符串；时间为数据库时间 `yyyy-MM-dd HH:mm:ss`，宿主应与团队统一时区。`page` 默认 1，范围 1–100000；`pageSize` 默认 20，范围 1–50。

### GET /api/posts/search

参数：`keyword` 可空，去首尾空格后最长 100 个 UTF-16 码元；`tagId` 可省略，仅单个正整数。标题或正文包含关键词，标签条件与关键词取交集。空条件返回公开印记，未知/停用标签返回空结果。`%`、`_`、`!` 当作普通文字。排序 `created_at DESC, id DESC`。

```http
GET /api/posts/search?keyword=晚霞&tagId=2&page=1&pageSize=20
```

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [{
      "id": "2",
      "title": "校园晚霞",
      "content": "运动场旁边的晚霞很漂亮。",
      "author": {"id": "3", "nickname": "小频", "avatarUrl": null},
      "imageUrl": null,
      "mood": "开心",
      "campusLocation": "运动场",
      "tags": [{"id": "2", "name": "摄影"}, {"id": "5", "name": "校园生活"}],
      "commentCount": 1,
      "createdAt": "2026-11-24 12:00:00"
    }],
    "total": 1,
    "page": 1,
    "pageSize": 20
  }
}
```

此为字段示例，时间以数据库实际值为准。图片取 `IMAGE` 类型中 `sort_order,id` 排序的第一张。没有图片返回 null，不使用虚构图片。

### GET /api/posts/{id}/comments

参数：`page`、`pageSize`。只返回 `PUBLISHED` 评论，按 `created_at DESC,id DESC`。`total` 为有效评论总数，也用于详情页评论数量。

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [{
      "id": "1", "postId": "1",
      "author": {"id": "3", "nickname": "小频", "avatarUrl": null},
      "content": "一起加油，期待看到最终成果！",
      "createdAt": "2026-11-24 12:00:00"
    }],
    "total": 1, "page": 1, "pageSize": 20
  }
}
```

### POST /api/posts/{id}/comments

```json
{"content": "一起加油！"}
```

去除首尾空白后须为 1–500 个 UTF-16 码元，不能只含空白或零宽字符。不接受客户端作者身份，未知 `userId` 字段不会用于写库。返回 HTTP 200，`data` 为上面的单个 Comment 对象。重复点击由前端提交锁拦截；没有跨网络重试的幂等键，超时后应先刷新确认结果，再决定是否重发。

### 错误

```json
{"code": 42201, "message": "内容含有不适宜词语，请修改后重试", "data": null}
```

| HTTP | code | 含义 |
| --- | --- | --- |
| 400 | 40001 | 参数类型、长度、ID、分页值错误，或评论为空 |
| 401 | 40101 | 未登录、过期或用户不可用（鉴权适配器按此契约返回） |
| 404 | 40401 | 印记不存在、下架、删除或非公开 |
| 422 | 42201 | 敏感词命中 |
| 500 | 50001 | 服务异常，客户端不暴露 SQL 或内部异常 |

## 验证与复现

```bash
# 在仓库根目录
mvn -f backend/pom.xml -Dtest=InteractionIntegrationTest test
node --test miniprogram/modules/search-comment/tests/*.test.cjs
```

后端测试从仓库 `database/schema.sql` 和 `seed.sql` 读取真实表结构和演示数据，仅移除建库/USE 语句；默认用 H2 MySQL 模式。测试鉴权替身仅存在于 `src/test`，不会进入生产 jar。

要复测真实 MySQL，先新建**专用空测试数据库**，名称必须为 `interaction_test_*`。测试每条用例会清理该库中的业务表，因此禁止指向开发/生产数据库；测试程序也校验库名。通过本机环境变量提供 URL、用户名和密码后执行同一 Maven 命令：

```bash
export INTERACTION_TEST_DB_URL='jdbc:mysql://127.0.0.1:13316/interaction_test_cax?serverTimezone=Asia/Shanghai'
export INTERACTION_TEST_DB_USER='your_test_user'
# INTERACTION_TEST_DB_PASSWORD 由本机环境提供，不写入仓库
mvn -f backend/pom.xml -Dtest=InteractionIntegrationTest test
```

下表记录原交付于 2026-11-24 的验证结果。

| 检查 | 结果 |
| --- | --- |
| 原交付的 Java 17 编译及 Maven 打包 | 通过 |
| 后端集成测试：H2 | 14/14 通过 |
| 后端集成测试：隔离 MySQL 26.7.0，读取已有 schema/seed | 14/14 通过 |
| 小程序客户端、分页、异步竞态及提交状态逻辑 | 16/16 通过（Node 测试，非微信渲染器） |
| 关键词/标签交集、特殊字符、防 SQL 注入、分页、字符串 BIGINT | 通过 |
| 数据库评论写入后重新读取、真实数量、作者不可伪造、下架/私有内容不可访问 | 通过 |
| 敏感词、空白/超长内容、无登录、禁用用户等异常 | 通过 |
| 微信开发者工具/真机视觉、交互、路由跳转 | 待验证：当前环境未安装微信开发者工具，宿主 app 尚未提交 |
| 真实 JWT 登录、配置接口、首页/详情嵌入与发布安全校验 | 待对应负责人接入后联调 |

视觉采用附带指南中的纸张底色、细边框、柔和绿/黄标记和完整中文无衬线字体，使用原创 WXML/WXSS。没有复制参考网站品牌、图片、字体或组件源码，没有引入 React、Looseleaf 或其他前端框架，因此没有新增第三方美术资源许可证。未生成浏览器截图，也未将浏览器仿真页面当作微信原生验收。

## 联调验收顺序

1. 启动统一后端并连接数据库，验证无令牌请求返回 401。
2. 配置负责人提供 `{id,name}[]` 标签适配；搜索“晚霞”和标签“摄影”得到对应印记；不同标签返回空结果。
3. 首页负责人注册搜索页并放置入口；点击搜索结果打开已有详情页。
4. 详情负责人挂载评论组件；发布普通评论，刷新页面后内容仍在，数量同步增加。
5. 提交“敏感词测试”，提示修改且数据库无新增；发布负责人单独接入 `ContentPolicy` 后再验证标题和正文。
6. 后台负责人下架目标印记，确认搜索不显示且评论读写返回 404。
7. 在微信开发者工具与真机检查中文长文、图片比例、窄屏布局、网络失败、登录过期和重复点击，然后由测试负责人标记完整验收。
