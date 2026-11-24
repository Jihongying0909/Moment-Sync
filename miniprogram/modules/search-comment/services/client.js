// 登录、配置与详情路由由各负责人注入；不重复实现公共能力。
let config = null;
function configure(options) {
  if (!options || !/^https?:\/\//.test(options.baseUrl || '') || typeof options.getToken !== 'function') {
    throw new Error('请配置 API 基础地址和登录令牌获取方法');
  }
  config = Object.assign({}, options, { baseUrl: options.baseUrl.replace(/\/$/, '') });
}
function failure(message, code) { return Object.assign(new Error(message), { code }); }
function settings() {
  if (!config) throw failure('搜索评论模块尚未配置，请联系开发者', 'NOT_CONFIGURED');
  return config;
}
function request(path, method, data) {
  return Promise.resolve().then(() => {
    const current = settings();
    const token = current.getToken();
    if (!token) {
      if (current.onUnauthorized) current.onUnauthorized();
      throw failure('请先登录后再操作', 40101);
    }
    return new Promise((resolve, reject) => {
      (current.request || wx.request)({
        url: current.baseUrl + path, method, data, timeout: 15000,
        header: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
        success(res) {
          const body = res.data;
          if (res.statusCode === 401) {
            if (current.onUnauthorized) current.onUnauthorized();
            reject(failure('登录已失效，请重新登录', 40101));
          } else if (res.statusCode >= 200 && res.statusCode < 300 && body && body.code === 0) {
            resolve(body.data);
          } else {
            reject(failure(body && body.message || '请求失败，请稍后重试', body && body.code));
          }
        },
        fail() { reject(failure('网络连接失败，请检查网络后重试', 'NETWORK')); }
      });
    });
  });
}
function idPath(id) {
  const value = String(id);
  if (!/^[1-9]\d*$/.test(value)) throw failure('印记 ID 无效', 'INVALID_ID');
  return '/posts/' + value + '/comments';
}
function search(query) { return request('/posts/search', 'GET', query); }
function comments(id, page = 1) { return request(idPath(id), 'GET', { page, pageSize: 20 }); }
function publish(id, content) { return request(idPath(id), 'POST', { content }); }
function loadTags() {
  return Promise.resolve().then(() => {
    const current = settings();
    if (typeof current.loadTags !== 'function') throw failure('标签选项暂不可用，请重试', 'TAGS_UNAVAILABLE');
    return current.loadTags();
  }).then(tags => {
    if (!Array.isArray(tags)) throw failure('标签接口返回格式不正确', 'INVALID_TAGS');
    return tags.map(tag => ({ id: String(tag.id), name: String(tag.name) }));
  });
}
function openPost(id) {
  const current = settings();
  if (typeof current.openPost !== 'function') throw failure('详情页尚未接入', 'DETAIL_UNAVAILABLE');
  return current.openPost(String(id));
}
module.exports = { configure, request, search, comments, publish, loadTags, openPost };
