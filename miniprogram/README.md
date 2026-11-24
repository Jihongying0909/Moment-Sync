# Moment Sync 微信小程序端（`miniprogram/`）

原生微信小程序工程，零第三方 UI 框架或运行时依赖，统一采用原创纸张手账视觉规范。

## 1. 目录结构

```text
miniprogram/
├─ app.js                       # 宿主启动入口：装配公共服务并向业务子模块注入适配器
├─ app.json                     # 页面路由注册、窗口配置与底部 TabBar（时光 / 我的）
├─ app.wxss                     # 全局设计变量与公共纸张手账样式
├─ project.config.json          # 微信开发者工具导入配置
├─ config/
│  └─ env.js                    # API 基础地址解析（默认 http://localhost:8080/api）
├─ services/
│  ├─ request.js                # 统一 HTTP 请求与单图上传网关（Bearer 注入、ApiResponse 解包、401 联动）
│  ├─ auth.js                   # 登录态存储、当前用户归一化、401 防抖重入锁与原路径回跳
│  ├─ config.js                 # 标签 / 心情 / 地点配置拉取（BIGINT 字符串安全、并发单飞去重与 TTL 缓存）
│  └─ router.js                 # 统一路由表与导航器（自动区分 Tab 页与普通页栈）
├─ components/
│  ├─ picker-shared.js          # 配置选择器纯函数逻辑
│  ├─ tag-picker/               # 多选标签组件（支持数量上限拦截与一键清空）
│  ├─ mood-picker/              # 单选心情组件（支持再次点击取消）
│  └─ location-picker/          # 单选校园地点组件（支持再次点击取消）
├─ pages/
│  ├─ login/                    # 普通用户账号密码登录页
│  └─ profile/                  # 个人中心页（底部 Tab「我的」）
├─ modules/
│  ├─ post/                     # 印记模块：首页列表、发布页、详情页、卡片与媒体选择组件
│  └─ search-comment/           # 搜索与评论模块：搜索页、首页搜索入口组件、详情评论面板组件
└─ tests/
   └─ host-foundation.test.cjs  # 宿主公共层、选择器组件与跨模块装配自动化测试
```

## 2. 本地运行与调试

1. 启动本地 MySQL 与 `backend/` Spring Boot 服务（默认监听 `http://localhost:8080`）。
2. 打开**微信开发者工具**，选择「导入项目」，目录指向本仓库下的 `miniprogram/` 目录（工程已内置 `touristappid` 测试配置并关闭开发期域名强校验）。
3. 如需在真机或局域网联调自定义后端地址，可在开发者工具 Console 执行：
   ```js
   wx.setStorageSync('moment_sync_api_base_url', 'http://<你的局域网IP>:8080/api')
   ```
   重新编译即可生效，切勿将个人局域网 IP 或任何真实密钥提交入库。

## 3. 前端编码与协作规范

1. **统一服务调用**：新增页面通过 `const { getServices } = require('../../app')` 获取 `{ auth, http, config, router }`，禁止在业务页面私自拼接 `wx.request` Header、硬编码存储键名或内置假数据。
2. **BIGINT 字符串安全**：后端所有 64 位主键均以字符串返回，前端全链路保持字符串传递与比对，严禁调用 `Number(id)` 或 `parseInt(id, 10)`。
3. **401 与登录回跳**：未登录或令牌失效（HTTP `401` / 业务码 `40101`）统一由 `auth.requireLogin()` 拦截，自动记录当前路由及查询参数；登录成功后调用 `auth.consumePostLoginRedirect()` + `router.redirectAfterLogin()` 返回原页面。
4. **视觉一致性**：页面根节点统一使用 `.notebook`，卡片容器使用 `.paper`，主按钮使用 `.button.primary`，错误态使用 `.error`，不引入外部字体包或重型组件库。

## 4. 运行自动化逻辑测试

无需启动微信开发者工具即可在仓库根目录执行全量前端单测与集成测试：

```bash
node --test miniprogram/tests/*.test.cjs miniprogram/modules/post/tests/*.test.cjs miniprogram/modules/search-comment/tests/*.test.cjs
```
