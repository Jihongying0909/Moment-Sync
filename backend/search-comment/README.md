# 搜索、评论与基础内容安全（陈昂雄）

独立 Spring Boot library，不包含公共应用入口或 JWT 实现。

在仓库根目录运行：

```bash
mvn -f backend/search-comment/pom.xml clean install
```

默认使用 H2 执行 14 项集成测试。宿主通过 Maven 依赖与 `@Import(InteractionConfiguration.class)` 接入，并提供 `CurrentUserProvider`、数据源及事务管理器。

完整接口、鉴权契约、MySQL 测试及协作边界见 [模块接入说明](../../docs/modules/search-comment-cax.md)。
