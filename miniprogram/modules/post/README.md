# 印记发布与内容展示小程序模块（江可妮）

原生微信小程序模块，交付分支 `JKN-1124`。只实现江可妮负责的前端部分，不创建第二套应用入口，也不内置演示令牌、假数据或真实密钥。

## 范围

- `pages/home`：首页印记列表、分页、下拉刷新、错误重试、发布/搜索入口。
- `pages/publish`：标题与正文输入、单张图片选择预览、标签/心情/地点选择、上传与发布状态、失败保留草稿。
- `pages/detail`：印记详情、标签/心情/地点与评论数量展示、下拉刷新、无效 ID 与下架内容提示。
- `components/post-card`、`media-picker`、`option-picker`：本模块复用的卡片、单图选择与通用选项控件。
- `services/client.js`、`post-model.js`：接口封装、字段归一化、草稿校验与分页处理。
- `tests/post-front.test.cjs`：Node 逻辑自测。

登录、标签/心情/地点配置组件、搜索评论组件、后端接口、后台与 `app.json` 宿主由对应负责人提供。接入方法见 [模块接入说明](../../../docs/modules/post-jkn.md)。

## 自测

```bash
node --test miniprogram/modules/post/tests/*.test.cjs
```

当前结果：16/16 通过（Node 环境逻辑测试，不等同于微信开发者工具或真机验收）。

视觉沿用项目纸张底色、深墨绿色边框与柔和标记的原创 WXSS，未引入第三方 UI 库、外部字体或图片资源。
