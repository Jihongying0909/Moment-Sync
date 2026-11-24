// 拾光同频微信小程序宿主入口。
// 统一初始化环境、鉴权、网络网关、配置缓存与页面路由，并向印记模块与搜索评论模块注入同一套公共适配器。

const { resolveEnv } = require('./config/env');
const { createRouter } = require('./services/router');
const { createAuthService } = require('./services/auth');
const { createHttpClient } = require('./services/request');
const { createConfigService } = require('./services/config');
const postClient = require('./modules/post/services/client');
const interactionClient = require('./modules/search-comment/services/client');

function bootstrapHost(overrides = {}) {
  const env = resolveEnv(overrides);
  const router = overrides.router || createRouter({
    wx: overrides.wx,
    getCurrentPages: overrides.getCurrentPages
  });
  const auth = overrides.auth || createAuthService({
    storage: overrides.storage,
    router
  });
  const http = overrides.http || createHttpClient({
    baseUrl: env.apiBaseUrl,
    auth,
    wx: overrides.wx
  });
  const config = overrides.config || createConfigService({
    http,
    ttlMs: overrides.configTtlMs,
    now: overrides.now
  });

  auth.bind({
    router,
    onSessionClear: () => config.invalidate()
  });
  http.configure({ baseUrl: env.apiBaseUrl, auth });
  config.bind({ http });

  const sharedAdapters = {
    baseUrl: env.apiBaseUrl,
    getToken: () => auth.getToken(),
    onUnauthorized: () => auth.requireLogin(),
    request: (opts) => http.rawRequest(opts),
    loadTags: () => config.listTags(),
    openPost: (id) => router.openDetail(id)
  };

  postClient.configure(Object.assign({}, sharedAdapters, {
    upload: (opts) => http.rawUpload(opts),
    loadMoods: () => config.listMoods(),
    loadLocations: () => config.listLocations(),
    openHome: () => router.openHome({ relaunch: true }),
    openPublish: () => router.openPublish(),
    openSearch: () => router.openSearch()
  }));

  interactionClient.configure(sharedAdapters);

  return { env, router, auth, http, config };
}

let activeServices = null;

function getServices() {
  if (typeof getApp === 'function') {
    const app = getApp({ allowDefault: true });
    if (app && app.globalData && app.globalData.services) {
      return app.globalData.services;
    }
  }
  if (!activeServices) {
    activeServices = bootstrapHost();
  }
  return activeServices;
}

if (typeof App === 'function') {
  App({
    globalData: {
      services: null
    },
    onLaunch() {
      activeServices = bootstrapHost();
      this.globalData.services = activeServices;
    }
  });
}

module.exports = {
  bootstrapHost,
  getServices
};
