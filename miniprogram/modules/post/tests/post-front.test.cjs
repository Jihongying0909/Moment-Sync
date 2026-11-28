const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const model = require('../services/post-model');
const client = require('../services/client');

const tick = () => new Promise(resolve => setImmediate(resolve));
function deferred() {
  let resolve, reject;
  const promise = new Promise((a, b) => { resolve = a; reject = b; });
  return { promise, resolve, reject };
}

function mount(relative, stubs = {}) {
  let definition;
  const context = {
    require: id => (String(id).indexOf('client') !== -1 ? stubs.api : model),
    Page: value => { definition = value; },
    Component: value => { definition = value; },
    wx: stubs.wx || { showToast() {}, showLoading() {}, stopPullDownRefresh() {}, navigateTo() {}, reLaunch() {} },
    console,
    Promise
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '..', relative), 'utf8'), context);
  const instance = {
    data: JSON.parse(JSON.stringify(definition.data || {})),
    events: [],
    setData(value) { Object.assign(this.data, value); },
    triggerEvent(name, detail) { this.events.push({ name, detail }); }
  };
  Object.assign(instance, definition.methods || definition, stubs.extra || {});
  return { instance, definition };
}

test('normalizePost keeps large IDs as strings and reads the first ordered image', () => {
  const post = model.normalizePost({
    id: '9007199254740993',
    title: ' 晚霞 ',
    content: '运动场旁边很漂亮',
    author: { id: '3', nickname: '小频' },
    campus_location: '运动场',
    mood: '开心',
    tags: [{ id: '2', name: '摄影' }, '校园生活'],
    postMedia: [
      { id: '9', mediaUrl: 'https://img.test/late.jpg', sort_order: 5 },
      { id: '8', mediaUrl: 'https://img.test/first.jpg', sort_order: 1 }
    ],
    comment_count: 2,
    created_at: '2026-11-24 12:00:00'
  });
  assert.equal(post.id, '9007199254740993');
  assert.equal(post.title, '晚霞');
  assert.equal(post.author.nickname, '小频');
  assert.equal(post.imageUrl, 'https://img.test/first.jpg');
  assert.equal(post.campusLocation, '运动场');
  assert.equal(post.tags.length, 2);
  assert.equal(post.commentCount, 2);
  assert.equal(post.createdAt, '2026-11-24 12:00:00');
});

test('options accept enum strings and object lists, decoration marks selection', () => {
  const options = model.normalizeOptions(['开心', { id: '2', name: '摄影' }, { value: '图书馆', label: '图书馆' }]);
  assert.deepEqual(options, [
    { value: '开心', label: '开心' },
    { value: '2', label: '摄影' },
    { value: '图书馆', label: '图书馆' }
  ]);
  const decorated = model.decorateOptions(options, ['2']);
  assert.equal(decorated[0].selected, false);
  assert.equal(decorated[1].selected, true);
});

test('draft validation rejects empty and oversized fields and keeps valid data', () => {
  assert.throws(() => model.validateDraft({ title: '  ', content: '正文' }), /标题需为/);
  assert.throws(() => model.validateDraft({ title: '题', content: 'x'.repeat(2001) }), /正文需为/);
  assert.throws(() => model.validateDraft({ title: '题', content: '正文', tags: ['1', '2', '3', '4', '5', '6'] }), /最多选择 5 个/);
  const draft = model.validateDraft({
    title: ' 图书馆\u200B记录 ',
    content: '  今天整理需求 ',
    tags: [{ id: '1' }, '2', '1'],
    mood: '平静',
    campusLocation: '图书馆',
    imagePath: 'wxfile://tmp_photo.jpg'
  });
  assert.equal(draft.title, '图书馆记录');
  assert.equal(draft.content, '今天整理需求');
  assert.deepEqual(draft.tagIds, ['1', '2']);
  assert.equal(draft.imagePath, 'wxfile://tmp_photo.jpg');
  const payload = model.toCreatePayload(draft, 'https://img.test/a.jpg');
  assert.deepEqual(payload, {
    title: '图书馆记录', content: '今天整理需求', tagIds: ['1', '2'],
    mood: '平静', campusLocation: '图书馆', imageUrl: 'https://img.test/a.jpg'
  });
  assert.equal('imagePath' in model.toCreatePayload(draft, ''), false);
});

test('uploaded URL and pagination helpers tolerate backend envelope variants', () => {
  assert.equal(model.extractUploadedUrl('https://img.test/a.jpg'), 'https://img.test/a.jpg');
  assert.equal(model.extractUploadedUrl({ code: 0, data: { url: 'https://img.test/b.jpg' } }), 'https://img.test/b.jpg');
  assert.equal(model.extractUploadedUrl({ url: '' }), '');
  assert.deepEqual(model.buildPageQuery(-3, 500), { page: 1, pageSize: 50 });
  const page = model.normalizePage({ items: [{ id: '1' }], total: 3 }, 1, 2);
  assert.equal(page.items[0].id, '1');
  assert.equal(page.hasMore, true);
  assert.equal(model.normalizePage({ items: [{ id: '1' }], total: 1 }, 1, 20).hasMore, false);
});

test('client uses configured base URL, bearer token and unwrapped envelope', async () => {
  let received;
  client.configure({
    baseUrl: 'https://example.test/api/',
    getToken: () => 'signed-token',
    request(options) { received = options; options.success({ statusCode: 200, data: { code: 0, data: { items: [] } } }); }
  });
  const result = await client.listPosts({ page: 2, pageSize: 10 });
  assert.equal(received.url, 'https://example.test/api/posts');
  assert.equal(received.header.Authorization, 'Bearer signed-token');
  assert.deepEqual(received.data, { page: 2, pageSize: 10 });
  assert.equal(result.page, 2);
});

test('client surfaces auth failures, server messages and network errors', async () => {
  let logins = 0;
  client.configure({ baseUrl: 'https://example.test/api', getToken: () => '', onUnauthorized: () => { logins += 1; }, request() { assert.fail('must not request'); } });
  await assert.rejects(client.listPosts(), /请先登录/);
  assert.equal(logins, 1);
  client.configure({ baseUrl: 'https://example.test/api', getToken: () => 'expired', onUnauthorized: () => { logins += 1; }, request(options) { options.success({ statusCode: 401 }); } });
  await assert.rejects(client.getPost('1'), /登录已失效/);
  assert.equal(logins, 2);
  client.configure({ baseUrl: 'https://example.test/api', getToken: () => 'token', request(options) { options.success({ statusCode: 422, data: { code: 42201, message: '内容含有不适宜词语' } }); } });
  await assert.rejects(client.createPost({ title: 'x', content: 'y' }), /不适宜词语/);
  client.configure({ baseUrl: 'https://example.test/api', getToken: () => 'token', request(options) { options.fail(); } });
  await assert.rejects(client.listPosts(), /网络连接失败/);
  await assert.rejects(client.getPost('../admin'), /ID 无效/);
});

test('upload uses multipart file field and parses string JSON responses', async () => {
  let received;
  client.configure({
    baseUrl: 'https://example.test/api',
    getToken: () => 'token',
    upload(options) {
      received = options;
      options.success({ statusCode: 200, data: JSON.stringify({ code: 0, data: { url: 'https://img.test/a.jpg' } }) });
    }
  });
  assert.equal(await client.uploadImage('wxfile://photo.jpg'), 'https://img.test/a.jpg');
  assert.equal(received.url, 'https://example.test/api/files/images');
  assert.equal(received.name, 'file');
  assert.equal(received.filePath, 'wxfile://photo.jpg');
  assert.equal(received.header.Authorization, 'Bearer token');
  client.configure({ baseUrl: 'https://example.test/api', getToken: () => 'token', upload(options) { options.success({ statusCode: 200, data: { code: 0, data: {} } }); } });
  await assert.rejects(client.uploadImage('wxfile://photo.jpg'), /未取得图片地址/);
});

test('config adapters map tag/mood/location options without fake defaults', async () => {
  client.configure({
    baseUrl: 'https://example.test/api',
    getToken: () => 'token',
    loadTags: () => [{ id: '2', name: '摄影' }],
    loadMoods: () => ['开心', '平静'],
    loadLocations: () => Promise.resolve([{ id: '7', name: '图书馆' }])
  });
  const config = await client.loadConfig();
  assert.equal(config.tags[0].value, '2');
  assert.equal(config.moods.length, 2);
  assert.equal(config.locations[0].label, '图书馆');
});

test('publish page uploads once, creates post with remote URL and opens home', async () => {
  const uploads = [];
  let payload, opened = false;
  const api = {
    loadConfig: async () => ({ tags: [], moods: [], locations: [] }),
    uploadImage: async path => { uploads.push(path); return 'https://img.test/remote.jpg'; },
    createPost: async value => { payload = value; return { id: '8', title: value.title }; },
    openHome: () => { opened = true; }
  };
  const page = mount('pages/publish/index.js', { api }).instance;
  await page.loadConfig();
  page.onTitleInput({ detail: { value: '校园晚霞' } });
  page.onContentInput({ detail: { value: '操场边看到了晚霞' } });
  page.onImageChange({ detail: { path: 'wxfile://local.jpg', size: 100 } });
  page.onTagsChange({ detail: { values: ['2'] } });
  page.onMoodChange({ detail: { value: '开心' } });
  page.onLocationChange({ detail: { value: '运动场' } });
  const pending = page.onSubmit();
  assert.equal(page.data.status, 'uploading');
  assert.equal(page.data.uploading, true);
  await pending;
  assert.deepEqual(uploads, ['wxfile://local.jpg']);
  assert.equal(payload.imageUrl, 'https://img.test/remote.jpg');
  assert.equal(payload.mood, '开心');
  assert.equal(payload.campusLocation, '运动场');
  assert.deepEqual(payload.tagIds, ['2']);
  assert.equal(page.data.status, 'success');
  assert.equal(opened, true);
  await page.onSubmit();
  assert.equal(uploads.length, 1, 'successful page must not publish twice');
});

test('publish failure keeps draft and retries upload result without duplicating it', async () => {
  const uploads = [];
  let attempts = 0;
  const api = {
    loadConfig: async () => ({ tags: [], moods: [], locations: [] }),
    uploadImage: async () => { uploads.push(1); return 'https://img.test/remote.jpg'; },
    createPost: async () => { attempts += 1; if (attempts === 1) throw new Error('服务器繁忙'); return { id: '9' }; },
    openHome() {}
  };
  const page = mount('pages/publish/index.js', { api }).instance;
  page.data.title = '标题';
  page.data.content = '正文';
  page.data.imagePath = 'wxfile://local.jpg';
  await page.onSubmit();
  assert.equal(page.data.error, '服务器繁忙');
  assert.equal(page.data.title, '标题');
  assert.equal(page.data.imagePath, 'wxfile://local.jpg');
  await page.onSubmit();
  assert.equal(attempts, 2);
  assert.equal(uploads.length, 1);
  assert.equal(page.data.status, 'success');
});

test('publish blocks validation errors and concurrent submits', async () => {
  const sent = deferred();
  let calls = 0;
  const api = { uploadImage: async () => '', createPost: () => { calls += 1; return sent.promise; }, openHome() {}, loadConfig: async () => ({ tags: [], moods: [], locations: [] }) };
  const page = mount('pages/publish/index.js', { api }).instance;
  await page.onSubmit();
  assert.match(page.data.error, /标题需为/);
  assert.equal(calls, 0);
  page.data.title = '标题';
  page.data.content = '正文';
  const first = page.onSubmit();
  await tick();
  await tick();
  await page.onSubmit();
  assert.equal(calls, 1);
  sent.resolve({ id: '1' });
  await first;
});

test('home pagination keeps prior items and retries the failed page', async () => {
  let fail = false;
  const api = {
    listPosts: async query => {
      if (query.page === 2 && fail) throw new Error('网络断开');
      const items = [{ id: String(query.page), title: '第' + query.page + '页' }];
      return { items, total: 40, page: query.page, pageSize: 20, hasMore: query.page < 2 };
    },
    openDetail() {}, openPublish() {}, openSearch() {}
  };
  const page = mount('pages/home/index.js', { api }).instance;
  page._generation = 0;
  await page.refresh();
  assert.equal(page.data.items.length, 1);
  fail = true;
  await page.onMore();
  assert.equal(page.data.error, '网络断开');
  assert.equal(page.data.items.length, 1);
  fail = false;
  await page.onRetry();
  assert.equal(page.data.items.length, 2);
  assert.equal(page.data.page, 2);
});

test('home ignores stale responses after refresh', async () => {
  const old = deferred();
  let calls = 0;
  const api = {
    listPosts: () => { calls += 1; return calls === 1 ? old.promise : Promise.resolve({ items: [{ id: 'new' }], total: 1, page: 1, pageSize: 20, hasMore: false }); }
  };
  const page = mount('pages/home/index.js', { api }).instance;
  page._generation = 0;
  const first = page.refresh();
  const second = page.refresh();
  await second;
  old.resolve({ items: [{ id: 'old' }], total: 1, page: 1, pageSize: 20, hasMore: false });
  await first;
  await tick();
  assert.equal(page.data.items[0].id, 'new');
  assert.equal(page.data.loading, false);
});

test('detail loads post, rejects invalid ID and accepts only loaded comment counts', async () => {
  const api = { getPost: async () => ({ id: '5', title: '晚霞', commentCount: 3, tags: [], author: {} }) };
  const page = mount('pages/detail/index.js', { api }).instance;
  page.onLoad({ id: '5' });
  await tick();
  assert.equal(page.data.post.id, '5');
  assert.equal(page.data.commentCount, 3);
  page.onCommentCountChange({ detail: { total: 99, loaded: false } });
  assert.equal(page.data.commentCount, 3);
  page.onCommentCountChange({ detail: { total: 9, loaded: true } });
  assert.equal(page.data.commentCount, 9);
  const invalid = mount('pages/detail/index.js', { api }).instance;
  invalid.onLoad({ id: 'abc' });
  assert.match(invalid.data.error, /ID 无效/);
});

test('option picker toggles single and multiple selections', () => {
  const picker = mount('components/option-picker/index.js').instance;
  picker.data.options = [{ value: '1', label: '摄影', selected: true }, { value: '2', label: '校园生活', selected: false }];
  picker.onTap({ currentTarget: { dataset: { value: '1' } } });
  assert.deepEqual(JSON.parse(JSON.stringify(picker.events.pop().detail)), { value: '', values: [], multiple: false });
  picker.onTap({ currentTarget: { dataset: { value: '2' } } });
  assert.equal(picker.events.pop().detail.value, '2');
  // 页面会在 change 后重新 decorate 选项，这里同步模拟已选中状态。
  picker.data.options[1].selected = true;
  picker.data.multiple = true;
  picker.onTap({ currentTarget: { dataset: { value: '2' } } });
  assert.deepEqual(JSON.parse(JSON.stringify(picker.events.pop().detail.values)), ['1']);
});

test('media picker emits chosen image, rejects oversized files and ignores cancellation', () => {
  let cancelled = false;
  const wx = {
    chooseMedia(options) {
      if (cancelled) options.fail({ errMsg: 'chooseMedia:fail cancel' });
      else options.success({ tempFiles: [{ tempFilePath: 'wxfile://photo.jpg', size: 2 * 1024 * 1024 }] });
    }
  };
  const picker = mount('components/media-picker/index.js', { wx }).instance;
  picker.choose();
  assert.deepEqual(JSON.parse(JSON.stringify(picker.events.pop())), { name: 'change', detail: { path: 'wxfile://photo.jpg', size: 2 * 1024 * 1024 } });
  picker.data.maxSizeMb = 1;
  picker.choose();
  assert.match(picker.events.pop().detail.message, /不能超过 1MB/);
  picker.events.length = 0;
  cancelled = true;
  picker.choose();
  assert.equal(picker.events.length, 0);
});
