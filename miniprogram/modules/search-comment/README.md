# 搜索与评论小程序模块（陈昂雄）

原生微信小程序组件，无第三方前端运行时依赖。

- `pages/search`：关键词、单标签组合搜索与结果分页。
- `components/search-entry`：首页可嵌入搜索入口。
- `components/comment-panel`：评论列表、数量、发表评论与状态反馈。
- `services/client.js`：统一注入 API 地址、token、标签和详情路由适配器。

运行逻辑测试：

```bash
node --test miniprogram/modules/search-comment/tests/*.test.cjs
```

当前宿主 app、登录和详情页尚未提交，本模块没有替其他成员创建这些页面。注册方法与事件契约见 [模块接入说明](../../../docs/modules/search-comment-cax.md)。
