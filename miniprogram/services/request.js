// 统一网络请求与文件上传网关。
// 同时提供底层 wx.request / wx.uploadFile 适配函数（供子模块注入）与 Promise 业务请求封装。

function createApiError(message, code, statusCode) {
  const error = new Error(message || '请求失败，请稍后重试');
  error.code = code ?? 'UNKNOWN';
  if (statusCode !== undefined) error.statusCode = statusCode;
  return error;
}

function parseBody(raw) {
  if (raw && typeof raw === 'object') return raw;
  if (typeof raw !== 'string') return null;
  try {
    return JSON.parse(raw);
  } catch (_) {
    return null;
  }
}

function extractUploadUrl(data) {
  if (typeof data === 'string') return data.trim();
  if (!data || typeof data !== 'object') return '';
  return String(data.url || data.imageUrl || data.mediaUrl || '').trim();
}

function createHttpClient(deps = {}) {
  let baseUrl = String(deps.baseUrl || 'http://localhost:8080/api').replace(/\/+$/, '');
  let auth = deps.auth || null;
  const getTransport = () => deps.wx || (typeof wx !== 'undefined' ? wx : null);

  function configure(options = {}) {
    if (options.baseUrl) {
      baseUrl = String(options.baseUrl).trim().replace(/\/+$/, '');
    }
    if (options.auth) {
      auth = options.auth;
    }
  }

  function getBaseUrl() {
    return baseUrl;
  }

  function rawRequest(options) {
    const api = getTransport();
    if (!api || typeof api.request !== 'function') {
      if (options && typeof options.fail === 'function') {
        options.fail(new Error('当前环境不支持 wx.request'));
      }
      return null;
    }
    return api.request(options);
  }

  function rawUpload(options) {
    const api = getTransport();
    if (!api || typeof api.uploadFile !== 'function') {
      if (options && typeof options.fail === 'function') {
        options.fail(new Error('当前环境不支持 wx.uploadFile'));
      }
      return null;
    }
    return api.uploadFile(options);
  }

  function notifyUnauthorized() {
    if (auth && typeof auth.requireLogin === 'function') {
      auth.requireLogin();
    }
  }

  function request({ path, method = 'GET', data, header = {}, authRequired = true, timeout = 15000 } = {}) {
    return Promise.resolve().then(() => {
      const normalizedPath = String(path || '').startsWith('/') ? String(path) : `/${path || ''}`;
      const token = auth && typeof auth.getToken === 'function' ? auth.getToken() : '';

      if (authRequired && !token) {
        notifyUnauthorized();
        throw createApiError('请先登录后再操作', 40101, 401);
      }

      const headers = Object.assign({ 'Content-Type': 'application/json' }, header);
      if (token && !headers.Authorization) {
        headers.Authorization = `Bearer ${token}`;
      }

      return new Promise((resolve, reject) => {
        rawRequest({
          url: `${baseUrl}${normalizedPath}`,
          method,
          data,
          timeout,
          header: headers,
          success(res) {
            const body = parseBody(res.data);
            const code = body && body.code !== undefined ? body.code : undefined;

            if (res.statusCode === 401 || code === 40101) {
              if (authRequired || token) notifyUnauthorized();
              reject(createApiError((body && body.message) || '登录已失效，请重新登录', 40101, 401));
              return;
            }

            if (res.statusCode >= 200 && res.statusCode < 300 && body && body.code === 0) {
              resolve(body.data);
              return;
            }

            reject(createApiError(
              (body && body.message) || '请求失败，请稍后重试',
              code ?? res.statusCode,
              res.statusCode
            ));
          },
          fail() {
            reject(createApiError('网络连接失败，请检查后端服务与网络设置', 'NETWORK', 0));
          }
        });
      });
    });
  }

  function uploadImage(filePath, fieldName = 'file') {
    return Promise.resolve().then(() => {
      const cleanPath = String(filePath || '').trim();
      if (!cleanPath) {
        throw createApiError('请先选择一张图片', 40001, 400);
      }
      const token = auth && typeof auth.getToken === 'function' ? auth.getToken() : '';
      if (!token) {
        notifyUnauthorized();
        throw createApiError('请先登录后再上传图片', 40101, 401);
      }

      return new Promise((resolve, reject) => {
        rawUpload({
          url: `${baseUrl}/files/images`,
          filePath: cleanPath,
          name: fieldName,
          timeout: 60000,
          header: { Authorization: `Bearer ${token}` },
          success(res) {
            const body = parseBody(res.data);
            const code = body && body.code !== undefined ? body.code : undefined;
            if (res.statusCode === 401 || code === 40101) {
              notifyUnauthorized();
              reject(createApiError((body && body.message) || '登录已失效，请重新登录', 40101, 401));
              return;
            }
            if (res.statusCode >= 200 && res.statusCode < 300 && body && body.code === 0) {
              const url = extractUploadUrl(body.data);
              if (!url) {
                reject(createApiError('上传成功但未返回图片地址', 'UPLOAD_NO_URL', res.statusCode));
                return;
              }
              resolve(url);
              return;
            }
            reject(createApiError(
              (body && body.message) || '图片上传失败，请稍后重试',
              code ?? res.statusCode,
              res.statusCode
            ));
          },
          fail() {
            reject(createApiError('图片上传失败，请检查网络连接', 'NETWORK', 0));
          }
        });
      });
    });
  }

  return {
    configure,
    getBaseUrl,
    rawRequest,
    rawUpload,
    request,
    uploadImage
  };
}

const defaultHttp = createHttpClient();

module.exports = Object.assign({ createHttpClient, createApiError, extractUploadUrl }, defaultHttp);
