// 江可妮：印记前端模块接口客户端。
// 宿主在 App.onLaunch 调用 configure 注入 API 地址、登录令牌与路由适配器。
// 本模块不保存令牌，不内置演示账号或假数据。

var model = require('./post-model');

var config = null;

function configure(options) {
  if (!options || !/^https?:\/\//.test(options.baseUrl || '') || typeof options.getToken !== 'function') {
    throw model.failure('请配置 API 基础地址和登录令牌获取方法', 'NOT_CONFIGURED');
  }
  config = {};
  for (var key in options) {
    if (Object.prototype.hasOwnProperty.call(options, key)) config[key] = options[key];
  }
  config.baseUrl = options.baseUrl.replace(/\/$/, '');
  return config;
}

function settings() {
  if (!config) throw model.failure('印记模块尚未配置，请联系集成负责人', 'NOT_CONFIGURED');
  return config;
}

function requireToken(current, action) {
  var token = current.getToken();
  if (!token) {
    if (current.onUnauthorized) current.onUnauthorized();
    throw model.failure('请先登录后再' + action, 40101);
  }
  return token;
}

function request(path, method, data) {
  return Promise.resolve().then(function () {
    var current = settings();
    var token = requireToken(current, '操作');
    return new Promise(function (resolve, reject) {
      (current.request || wx.request)({
        url: current.baseUrl + path,
        method: method,
        data: data,
        timeout: 15000,
        header: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
        success: function (res) {
          var body = res.data;
          if (res.statusCode === 401) {
            if (current.onUnauthorized) current.onUnauthorized();
            reject(model.failure('登录已失效，请重新登录', 40101));
          } else if (res.statusCode >= 200 && res.statusCode < 300 && body && body.code === 0) {
            resolve(body.data);
          } else {
            reject(model.failure((body && body.message) || '请求失败，请稍后重试', body && body.code));
          }
        },
        fail: function () { reject(model.failure('网络连接失败，请检查网络后重试', 'NETWORK')); }
      });
    });
  });
}

function parseResponse(res) {
  var body = null;
  try {
    body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
  } catch (error) {
    body = null;
  }
  return body;
}

function uploadImage(filePath) {
  return Promise.resolve().then(function () {
    var current = settings();
    var token = requireToken(current, '上传图片');
    var path = model.text(filePath);
    if (!path) throw model.failure('请先选择一张图片', 40001);
    return new Promise(function (resolve, reject) {
      (current.upload || wx.uploadFile)({
        url: current.baseUrl + '/files/images',
        filePath: path,
        name: current.uploadField || 'file',
        timeout: 60000,
        header: { Authorization: 'Bearer ' + token },
        success: function (res) {
          var body = parseResponse(res);
          if (res.statusCode === 401) {
            if (current.onUnauthorized) current.onUnauthorized();
            reject(model.failure('登录已失效，请重新登录', 40101));
          } else if (res.statusCode >= 200 && res.statusCode < 300 && body && body.code === 0) {
            var url = model.extractUploadedUrl(body.data);
            if (!url) reject(model.failure('上传成功但未取得图片地址，请重试', 'UPLOAD_NO_URL'));
            else resolve(url);
          } else {
            reject(model.failure((body && body.message) || '图片上传失败，请稍后重试', body && body.code));
          }
        },
        fail: function () { reject(model.failure('图片上传失败，请检查网络后重试', 'NETWORK')); }
      });
    });
  });
}

function listPosts(query) {
  var source = query || {};
  var pageQuery = model.buildPageQuery(source.page, source.pageSize);
  return request('/posts', 'GET', pageQuery).then(function (result) {
    return model.normalizePage(result, pageQuery.page, pageQuery.pageSize);
  });
}

function getPost(id) {
  return Promise.resolve().then(function () {
    var postId = model.parseId(id);
    return request('/posts/' + postId, 'GET').then(function (result) {
      var post = model.normalizePost(result);
      if (!post || !post.id) throw model.failure('印记数据异常，请稍后重试', 'INVALID_POST');
      return post;
    });
  });
}

function createPost(payload) {
  return request('/posts', 'POST', payload).then(function (result) {
    var post = model.normalizePost(result);
    if (!post || !post.id) throw model.failure('发布成功但返回数据异常，请刷新列表确认', 'INVALID_POST');
    return post;
  });
}

function loadOptions(adapterName) {
  return Promise.resolve().then(function () {
    var current = settings();
    var adapter = current[adapterName];
    if (typeof adapter !== 'function') return [];
    return Promise.resolve().then(adapter).then(function (value) {
      return model.normalizeOptions(value);
    });
  });
}

function loadConfig() {
  return Promise.all([
    loadOptions('loadTags'),
    loadOptions('loadMoods'),
    loadOptions('loadLocations')
  ]).then(function (all) {
    return { tags: all[0], moods: all[1], locations: all[2] };
  });
}

function navigate(url, mode) {
  var method = mode === 'reLaunch' ? 'reLaunch' : 'navigateTo';
  return new Promise(function (resolve) {
    wx[method]({
      url: url,
      fail: function () {
        wx.showToast({ title: '目标页面尚未注册，请联系集成负责人', icon: 'none' });
        resolve(false);
      },
      success: function () { resolve(true); }
    });
  });
}

function openDetail(id) {
  var postId = model.parseId(id);
  var current = settings();
  if (typeof current.openPost === 'function') {
    return Promise.resolve().then(function () { return current.openPost(postId); });
  }
  return navigate('/modules/post/pages/detail/index?id=' + postId);
}

function openHome() {
  var current = settings();
  if (typeof current.openHome === 'function') {
    return Promise.resolve().then(function () { return current.openHome(); });
  }
  return navigate('/modules/post/pages/home/index', 'reLaunch');
}

function openPublish() {
  var current = settings();
  if (typeof current.openPublish === 'function') {
    return Promise.resolve().then(function () { return current.openPublish(); });
  }
  return navigate('/modules/post/pages/publish/index');
}

function openSearch() {
  var current = settings();
  if (typeof current.openSearch === 'function') {
    return Promise.resolve().then(function () { return current.openSearch(); });
  }
  return navigate('/modules/search-comment/pages/search/index');
}

module.exports = {
  configure: configure,
  request: request,
  uploadImage: uploadImage,
  listPosts: listPosts,
  getPost: getPost,
  createPost: createPost,
  loadConfig: loadConfig,
  openDetail: openDetail,
  openHome: openHome,
  openPublish: openPublish,
  openSearch: openSearch
};
