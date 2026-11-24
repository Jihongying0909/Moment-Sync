const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const client = require('../services/client');
const tick = () => new Promise(resolve => setImmediate(resolve));
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; }
function mount(relative, api, extra = {}) {
  let definition;
  const context = { require: () => api, Page: value => { definition = value; }, Component: value => { definition = value; },
    wx: { showToast() {}, stopPullDownRefresh() {} }, console, Promise };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '..', relative), 'utf8'), context);
  const instance = { data: JSON.parse(JSON.stringify(definition.data)), events: [],
    setData(value) { Object.assign(this.data, value); }, triggerEvent(name, detail) { this.events.push({ name, detail }); } };
  Object.assign(instance, definition.methods || definition, extra);
  return { instance, definition };
}
const page = api => mount('pages/search/index.js', api).instance;
const panel = (api, id = '1') => {
  const result = mount('components/comment-panel/index.js', api);
  result.instance.data.postId = id;
  result.instance._ready = true;
  result.instance._generation = 0;
  return result.instance;
};
const list = (id, total = 1) => ({ items: [{ id }], total, page: 1, pageSize: 20 });
test('request uses host base URL, bearer token and unwraps response', async () => {
  let received;
  client.configure({ baseUrl: 'https://example.test/api/', getToken: () => 'signed-token', request(options) {
    received = options; options.success({ statusCode: 200, data: { code: 0, data: list('1') } });
  } });
  assert.equal((await client.search({ keyword: '晚霞' })).total, 1);
  assert.equal(received.url, 'https://example.test/api/posts/search');
  assert.equal(received.header.Authorization, 'Bearer signed-token');
  assert.equal(received.data.keyword, '晚霞');
});
test('missing token stops request and delegates login handling', async () => {
  let logins = 0;
  client.configure({ baseUrl: 'https://example.test/api', getToken: () => '', onUnauthorized: () => logins++, request() { assert.fail('must not request'); } });
  await assert.rejects(client.search({}), /请先登录/);
  assert.equal(logins, 1);
});
test('expired token and server business errors remain actionable', async () => {
  let logins = 0;
  client.configure({ baseUrl: 'https://example.test/api', getToken: () => 'expired', onUnauthorized: () => logins++, request(o) { o.success({ statusCode: 401 }); } });
  await assert.rejects(client.comments('1'), /登录已失效/);
  assert.equal(logins, 1);
  client.configure({ baseUrl: 'https://example.test/api', getToken: () => 'token', request(o) { o.success({ statusCode: 422, data: { code: 42201, message: '请修改敏感词' } }); } });
  await assert.rejects(client.publish('1', '词'), /请修改敏感词/);
});
test('network failure is surfaced and unsafe IDs rejected', async () => {
  client.configure({ baseUrl: 'https://example.test/api', getToken: () => 'token', request(o) { o.fail(); } });
  await assert.rejects(client.search({}), /网络连接失败/);
  assert.throws(() => client.comments('../admin'), /ID 无效/);
});
test('host tag and detail adapters retain large IDs', async () => {
  let opened;
  client.configure({ baseUrl: 'https://example.test/api', getToken: () => 'token', loadTags: () => [{ id: '9007199254740993', name: '学习' }], openPost: id => { opened = id; } });
  assert.equal((await client.loadTags())[0].id, '9007199254740993');
  client.openPost('9007199254740993');
  assert.equal(opened, '9007199254740993');
});
test('latest search wins when old response arrives late', async () => {
  const old = deferred(), recent = deferred(); let calls = 0;
  const p = page({ search: () => (++calls === 1 ? old.promise : recent.promise) });
  p._generation = 0; p.data.keyword = '旧'; const first = p.runSearch();
  p.data.keyword = '新'; const second = p.runSearch();
  recent.resolve(list('new')); await second;
  old.resolve(list('old')); await first;
  assert.equal(p.data.items[0].id, 'new'); assert.equal(p.data.loading, false);
});
test('unsubmitted input does not change pagination query', async () => {
  const queries = [];
  const p = page({ search: async query => { queries.push(query); return list(String(query.page), 40); } });
  p._generation = 0; p.data.keyword = '晚霞'; p.data.tagId = '2';
  await p.runSearch(); p.data.keyword = '图书馆'; await p.onMore();
  assert.equal(queries[1].keyword, '晚霞'); assert.equal(queries[1].tagId, '2'); assert.equal(queries[1].page, 2);
});
test('failed pagination retries the failed page without dropping prior results', async () => {
  let fail = true;
  const p = page({ search: async query => {
    if (query.page === 2 && fail) throw new Error('网络断开');
    return list(String(query.page), 40);
  } });
  p._generation = 0; await p.runSearch(); await p.onMore();
  assert.equal(p.data.error, '网络断开'); assert.equal(p.data.items.length, 1);
  fail = false; await p.onRetry(); assert.equal(p.data.page, 2); assert.equal(p.data.items.length, 2);
});
test('switching posts ignores comments loaded for previous post', async () => {
  const old = deferred();
  const c = panel({ comments: id => id === '1' ? old.promise : Promise.resolve(list('second')) });
  const first = c.refresh(); c.data.postId = '2'; await c.onPostChange();
  old.resolve(list('first')); await first;
  assert.equal(c.data.items[0].id, 'second');
  assert.equal(c.events.at(-1).detail.total, 1);
});
test('submit prevents double clicks and refreshes actual total', async () => {
  const sent = deferred(); let calls = 0;
  const c = panel({ publish: () => { calls++; return sent.promise; }, comments: async () => list('new', 7) });
  c.data.content = '加油'; const pending = c.onSubmit(); await c.onSubmit();
  assert.equal(calls, 1); assert.equal(c.data.sending, true);
  sent.resolve({}); await pending;
  assert.equal(c.data.content, ''); assert.equal(c.data.total, 7); assert.equal(c.data.sending, false);
});
test('failed comment preserves draft for correction', async () => {
  const c = panel({ publish: async () => { throw new Error('含敏感词'); } }); c.data.content = '原始草稿';
  await c.onSubmit(); assert.equal(c.data.content, '原始草稿'); assert.equal(c.data.submitError, '含敏感词');
});
test('empty or oversized comments do not make a request', async () => {
  const c = panel({ publish() { assert.fail('must not publish'); } });
  c.data.content = '  '; await c.onSubmit(); assert.match(c.data.submitError, /1–500/);
  c.data.content = 'x'.repeat(501); await c.onSubmit(); assert.match(c.data.submitError, /1–500/);
});
test('new post draft survives previous post submit completion', async () => {
  const sent = deferred(); const c = panel({ publish: () => sent.promise, comments: async () => list('2') });
  c.data.content = '旧草稿'; const pending = c.onSubmit();
  c.data.postId = '2'; await c.onPostChange(); c.data.content = '新草稿'; sent.resolve({}); await pending;
  assert.equal(c.data.content, '新草稿'); assert.equal(c.events.filter(e => e.name === 'submitted').length, 0);
});
test('comment pagination deduplicates overlapping rows', async () => {
  const c = panel({ comments: async (id, page) => ({ items: page === 1 ? [{ id: '1' }] : [{ id: '1' }, { id: '2' }], total: 21, page, pageSize: 20 }) });
  await c.refresh(); await c.onMore(); assert.equal(c.data.items.length, 2); assert.equal(c.data.hasMore, false);
});
test('returning to the original post cannot unlock a newer submission', async () => {
  const old = deferred(), recent = deferred(); let calls = 0;
  const c = panel({ publish: () => ++calls === 1 ? old.promise : recent.promise, comments: async () => list('1') });
  c.data.content = '第一份'; const first = c.onSubmit();
  c.data.postId = '2'; await c.onPostChange(); c.data.postId = '1'; await c.onPostChange();
  c.data.content = '第二份'; const second = c.onSubmit();
  old.resolve({}); await first; assert.equal(c.data.sending, true);
  recent.resolve({}); await second; assert.equal(c.data.sending, false);
});
test('successful submit with failed refresh keeps a retryable list error', async () => {
  const c = panel({ publish: async () => ({}), comments: async () => { throw new Error('刷新失败'); } });
  c.data.content = '已送出'; await c.onSubmit();
  assert.equal(c.data.content, ''); assert.equal(c.data.sending, false); assert.equal(c.data.error, '刷新失败');
});
