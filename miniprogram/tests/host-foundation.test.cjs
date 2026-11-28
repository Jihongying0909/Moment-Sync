const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const { resolveEnv, normalizeBaseUrl } = require('../config/env');
const { createRouter, ROUTES } = require('../services/router');
const { createAuthService } = require('../services/auth');
const { createHttpClient } = require('../services/request');
const { createConfigService } = require('../services/config');
const { bootstrapHost } = require('../app');
const postClient = require('../modules/post/services/client');
const interactionClient = require('../modules/search-comment/services/client');

function createStorageMock(initial = {}) {
  const store = new Map(Object.entries(initial));
  return {
    getStorageSync: (k) => (store.has(k) ? store.get(k) : ''),
    setStorageSync: (k, v) => store.set(k, v),
    removeStorageSync: (k) => store.delete(k)
  };
}

function loadComponentDefinition(relPath) {
  let captured = null;
  const prevComponent = global.Component;
  global.Component = (def) => { captured = def; };
  try {
    const resolved = require.resolve(relPath);
    delete require.cache[resolved];
    require(resolved);
  } finally {
    global.Component = prevComponent;
  }
  return captured;
}

function mountComponent(def, props = {}) {
  const events = [];
  const data = {};
  for (const [key, spec] of Object.entries(def.properties || {})) {
    data[key] = Object.prototype.hasOwnProperty.call(props, key) ? props[key] : spec.value;
  }
  Object.assign(data, JSON.parse(JSON.stringify(def.data || {})));

  const instance = {
    data,
    events,
    setData(patch) {
      Object.assign(this.data, patch);
    },
    triggerEvent(name, detail) {
      this.events.push({ name, detail });
    }
  };
  for (const [name, fn] of Object.entries(def.methods || {})) {
    instance[name] = fn.bind(instance);
  }
  if (def.lifetimes && typeof def.lifetimes.attached === 'function') {
    def.lifetimes.attached.call(instance);
  }
  return instance;
}

test('env normalizes valid URLs and falls back safely on invalid overrides', () => {
  assert.equal(normalizeBaseUrl(' http://192.168.1.8:8080/api/// '), 'http://192.168.1.8:8080/api');
  assert.equal(normalizeBaseUrl('ftp://bad-host/api'), '');
  const env = resolveEnv({ storage: createStorageMock({ moment_sync_api_base_url: 'not-a-url' }) });
  assert.equal(env.apiBaseUrl, 'http://localhost:8080/api');
});

test('auth deduplicates concurrent 401 redirects and preserves exact return route with BIGINT query', async () => {
  const navCalls = [];
  let resolveLoginNav;
  const wxMock = {
    navigateTo({ url, success }) {
      navCalls.push({ method: 'navigateTo', url });
      resolveLoginNav = () => success && success();
    },
    redirectTo({ url, success }) {
      navCalls.push({ method: 'redirectTo', url });
      if (success) success();
    },
    reLaunch({ url, success }) {
      navCalls.push({ method: 'reLaunch', url });
      if (success) success();
    }
  };
  const router = createRouter({
    wx: wxMock,
    getCurrentPages: () => [{
      route: 'modules/post/pages/detail/index',
      options: { id: '9007199254740993' }
    }]
  });
  let cacheInvalidated = 0;
  const auth = createAuthService({
    storage: createStorageMock(),
    router,
    onSessionClear: () => { cacheInvalidated += 1; }
  });
  auth.setSession({
    token: 'Bearer expired.jwt.token',
    user: { id: '9007199254740995', nickname: '小光\u200B', username: 'student_a' }
  });
  assert.equal(auth.getToken(), 'expired.jwt.token');
  assert.equal(auth.getUser().id, '9007199254740995');
  assert.equal(auth.getUser().nickname, '小光');

  // 模拟详情接口与评论接口同时触发 401
  const p1 = auth.requireLogin();
  const p2 = auth.requireLogin();
  const p3 = auth.requireLogin();
  assert.equal(navCalls.length, 1);
  assert.equal(navCalls[0].url, ROUTES.LOGIN);
  resolveLoginNav();
  await Promise.all([p1, p2, p3]);

  assert.equal(auth.isAuthenticated(), false);
  assert.ok(cacheInvalidated >= 1);

  const redirectTarget = auth.consumePostLoginRedirect();
  assert.equal(redirectTarget, '/modules/post/pages/detail/index?id=9007199254740993');
  await router.redirectAfterLogin(redirectTarget);
  assert.deepEqual(navCalls[1], {
    method: 'redirectTo',
    url: '/modules/post/pages/detail/index?id=9007199254740993'
  });

  // 再次消费应回退至首页 Tab 并通过 reLaunch 刷新
  const fallbackTarget = auth.consumePostLoginRedirect();
  assert.equal(fallbackTarget, ROUTES.HOME);
  await router.redirectAfterLogin(fallbackTarget);
  assert.deepEqual(navCalls[2], { method: 'reLaunch', url: ROUTES.HOME });
});

test('http client unwraps envelope, injects bearer header, parses upload JSON and handles 401/422', async () => {
  let unauthorizedCount = 0;
  const auth = {
    getToken: () => 'jwt-123',
    requireLogin: () => { unauthorizedCount += 1; }
  };
  const http = createHttpClient({
    baseUrl: 'http://localhost:8080/api/',
    auth,
    wx: {
      request(opts) {
        if (opts.url.endsWith('/users/me')) {
          assert.equal(opts.header.Authorization, 'Bearer jwt-123');
          opts.success({
            statusCode: 200,
            data: { code: 0, message: 'success', data: { id: '2', nickname: '小光' } }
          });
          return;
        }
        if (opts.url.endsWith('/posts')) {
          opts.success({
            statusCode: 422,
            data: { code: 42201, message: '内容含有不适宜词语', data: null }
          });
          return;
        }
        opts.success({
          statusCode: 401,
          data: { code: 40101, message: '请先登录', data: null }
        });
      },
      uploadFile(opts) {
        assert.equal(opts.header.Authorization, 'Bearer jwt-123');
        assert.equal(opts.name, 'file');
        opts.success({
          statusCode: 200,
          data: JSON.stringify({ code: 0, message: 'success', data: { imageUrl: 'http://localhost:8080/uploads/1.jpg' } })
        });
      }
    }
  });

  const me = await http.request({ path: 'users/me' });
  assert.deepEqual(me, { id: '2', nickname: '小光' });

  const uploaded = await http.uploadImage('/tmp/campus.jpg');
  assert.equal(uploaded, 'http://localhost:8080/uploads/1.jpg');

  await assert.rejects(
    () => http.request({ path: '/posts', method: 'POST', data: { title: '测试' } }),
    (err) => err.code === 42201 && err.statusCode === 422
  );
  assert.equal(unauthorizedCount, 0);

  await assert.rejects(
    () => http.request({ path: '/expired' }),
    (err) => err.code === 40101
  );
  assert.equal(unauthorizedCount, 1);
});

test('config service coalesces concurrent requests, caches within TTL and keeps BIGINT tag IDs as strings', async () => {
  let tagFetches = 0;
  let currentNow = 1000;
  const config = createConfigService({
    ttlMs: 5000,
    now: () => currentNow,
    http: {
      request({ path }) {
        if (path === '/tags') {
          tagFetches += 1;
          return new Promise((resolve) => {
            setTimeout(() => resolve([
              { id: '9007199254740993', name: '摄影' },
              { id: '9007199254740993', name: '重复项' },
              { id: 'invalid', name: '脏数据' },
              { id: 5, name: '校园生活' }
            ]), 5);
          });
        }
        if (path === '/moods') {
          return Promise.resolve({ items: [{ name: '开心' }, '平静', '开心', ''] });
        }
        return Promise.resolve(['图书馆', { label: '运动场' }]);
      }
    }
  });

  const [r1, r2, r3] = await Promise.all([config.listTags(), config.listTags(), config.listTags()]);
  assert.equal(tagFetches, 1);
  assert.deepEqual(r1, [
    { id: '9007199254740993', name: '摄影' },
    { id: '5', name: '校园生活' }
  ]);
  assert.deepEqual(r1, r2);
  assert.deepEqual(r2, r3);

  // TTL 内命中缓存，不重复请求
  currentNow = 4000;
  await config.listTags();
  assert.equal(tagFetches, 1);

  // 超过 TTL 后重新拉取
  currentNow = 7000;
  await config.listTags();
  assert.equal(tagFetches, 2);

  assert.deepEqual(await config.listMoods(), ['开心', '平静']);
  assert.deepEqual(await config.listLocations(), ['图书馆', '运动场']);
});

test('tag-picker enforces max limit, supports clear, and mood/location pickers toggle cleanly', () => {
  const tagDef = loadComponentDefinition('../components/tag-picker/index.js');
  const moodDef = loadComponentDefinition('../components/mood-picker/index.js');
  const locDef = loadComponentDefinition('../components/location-picker/index.js');

  const tagPicker = mountComponent(tagDef, {
    max: 2,
    options: [
      { value: '1', label: '学习', selected: true },
      { value: '2', label: '摄影', selected: true },
      { value: '3', label: '运动', selected: false }
    ]
  });
  assert.equal(tagPicker.data.selectedCount, 2);

  // 超出上限应触发 limit 事件且不改变选择
  tagPicker.onToggle({ currentTarget: { dataset: { value: '3' } } });
  assert.equal(tagPicker.events.length, 1);
  assert.equal(tagPicker.events[0].name, 'limit');
  assert.match(tagPicker.data.limitHint, /最多选择 2 个标签/);

  // 取消已选项应正常触发 change
  tagPicker.onToggle({ currentTarget: { dataset: { value: '2' } } });
  assert.equal(tagPicker.events[1].name, 'change');
  assert.deepEqual(tagPicker.events[1].detail, { value: ['1'], values: ['1'], multiple: true });

  // 一键清空
  tagPicker.onClear();
  assert.deepEqual(tagPicker.events[2].detail, { value: [], values: [], multiple: true });

  // 心情与地点单选及反选
  const moodPicker = mountComponent(moodDef, {
    options: [
      { value: '开心', label: '开心', selected: true },
      { value: '平静', label: '平静', selected: false }
    ]
  });
  assert.equal(moodPicker.data.current, '开心');
  moodPicker.onSelect({ currentTarget: { dataset: { value: '开心' } } });
  assert.deepEqual(moodPicker.events[0].detail, { value: '', values: [], multiple: false });

  const locPicker = mountComponent(locDef, {
    options: [{ value: '图书馆', label: '图书馆', selected: false }]
  });
  locPicker.onSelect({ currentTarget: { dataset: { value: '图书馆' } } });
  assert.deepEqual(locPicker.events[0].detail, { value: '图书馆', values: ['图书馆'], multiple: false });
});

test('bootstrapHost wires post and search-comment modules onto shared auth, config cache and router', async () => {
  const navigated = [];
  let tagNetworkHits = 0;
  const storage = createStorageMock();
  const wxMock = {
    navigateTo({ url, success }) {
      navigated.push(url);
      if (success) success();
    },
    request(opts) {
      if (opts.url === 'http://localhost:8080/api/tags') {
        tagNetworkHits += 1;
        opts.success({
          statusCode: 200,
          data: { code: 0, message: 'success', data: [{ id: '9007199254740999', name: '深大晚霞' }] }
        });
        return;
      }
      if (opts.url === 'http://localhost:8080/api/moods') {
        opts.success({ statusCode: 200, data: { code: 0, message: 'success', data: ['开心', '平静'] } });
        return;
      }
      if (opts.url === 'http://localhost:8080/api/locations') {
        opts.success({ statusCode: 200, data: { code: 0, message: 'success', data: ['图书馆', '运动场'] } });
        return;
      }
      opts.fail(new Error('unexpected url: ' + opts.url));
    }
  };

  const services = bootstrapHost({
    baseUrl: 'http://localhost:8080/api',
    storage,
    wx: wxMock,
    getCurrentPages: () => [{ route: 'modules/post/pages/home/index', options: {} }]
  });
  services.auth.setSession({ token: 'shared-jwt', user: { id: '2', nickname: '小光' } });

  // 印记模块与搜索模块同时读取标签，共享同一份缓存，仅发起 1 次网络请求
  const [postConfig, searchTags] = await Promise.all([
    postClient.loadConfig(),
    interactionClient.loadTags()
  ]);
  assert.equal(tagNetworkHits, 1);
  assert.deepEqual(postConfig.tags, [{ value: '9007199254740999', label: '深大晚霞' }]);
  assert.deepEqual(searchTags, [{ id: '9007199254740999', name: '深大晚霞' }]);

  // 搜索模块点击结果打开详情页，应精确路由到印记详情页并保留大整数字符串
  await interactionClient.openPost('9007199254740999');
  assert.deepEqual(navigated, ['/modules/post/pages/detail/index?id=9007199254740999']);
});

test('miniprogram manifest integrity: all app.json pages and component references exist on disk', () => {
  const root = path.resolve(__dirname, '..');
  const appJson = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'));
  assert.ok(Array.isArray(appJson.pages) && appJson.pages.length >= 6);

  for (const pageRoute of appJson.pages) {
    for (const ext of ['.js', '.json', '.wxml']) {
      const fullPath = path.join(root, pageRoute + ext);
      assert.ok(fs.existsSync(fullPath), `Missing page file: ${pageRoute}${ext}`);
    }
    const pageJson = JSON.parse(fs.readFileSync(path.join(root, pageRoute + '.json'), 'utf8'));
    const usingComponents = pageJson.usingComponents || {};
    for (const [tagName, compPath] of Object.entries(usingComponents)) {
      const resolvedBase = compPath.startsWith('/')
        ? path.join(root, compPath.slice(1))
        : path.resolve(path.dirname(path.join(root, pageRoute)), compPath);
      for (const ext of ['.js', '.json', '.wxml']) {
        assert.ok(
          fs.existsSync(resolvedBase + ext),
          `Page ${pageRoute} component <${tagName}> missing ${resolvedBase}${ext}`
        );
      }
    }
  }
});
