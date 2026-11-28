// 登录状态持久化、用户信息归一化与 401 防重入登录跳转协议。
const STORAGE_KEYS = Object.freeze({
  TOKEN: 'moment_sync_token',
  USER: 'moment_sync_user',
  REDIRECT: 'moment_sync_post_login_redirect'
});

function cleanText(value) {
  if (value === null || value === undefined) return '';
  return String(value).replace(/[\u200B-\u200D\uFEFF]/g, '').trim();
}

function normalizeUser(raw) {
  if (!raw || typeof raw !== 'object') return null;
  const rawId = cleanText(raw.id ?? raw.userId);
  const id = /^[1-9]\d*$/.test(rawId) ? rawId : '';
  const username = cleanText(raw.username);
  const nickname = cleanText(raw.nickname || username) || '校园同学';
  const avatarUrl = cleanText(raw.avatarUrl ?? raw.avatar_url);
  const role = cleanText(raw.role) || 'USER';
  if (!id && !username && !raw.nickname) return null;
  return { id, username, nickname, avatarUrl, role };
}

function sanitizeRedirectUrl(url) {
  const value = cleanText(url);
  if (!value.startsWith('/') || value.startsWith('//')) return '';
  if (value.split('?')[0] === '/pages/login/index') return '';
  return value;
}

function createMemoryStorage() {
  const map = new Map();
  return {
    getStorageSync: (key) => (map.has(key) ? map.get(key) : ''),
    setStorageSync: (key, value) => map.set(key, value),
    removeStorageSync: (key) => map.delete(key)
  };
}

function createAuthService(deps = {}) {
  const storage = deps.storage || (typeof wx !== 'undefined' && wx.getStorageSync ? wx : createMemoryStorage());
  const listeners = new Set();
  let redirecting = false;
  let onSessionClear = typeof deps.onSessionClear === 'function' ? deps.onSessionClear : null;
  let router = deps.router || null;

  function bind({ router: nextRouter, onSessionClear: nextClear } = {}) {
    if (nextRouter) router = nextRouter;
    if (typeof nextClear === 'function') onSessionClear = nextClear;
  }

  function readStorage(key) {
    try {
      return storage.getStorageSync(key);
    } catch (_) {
      return '';
    }
  }

  function writeStorage(key, value) {
    try {
      if (value === null || value === undefined || value === '') {
        storage.removeStorageSync(key);
      } else {
        storage.setStorageSync(key, value);
      }
    } catch (_) {
      // 忽略受限环境下的存储异常
    }
  }

  function getToken() {
    const raw = cleanText(readStorage(STORAGE_KEYS.TOKEN));
    return raw.replace(/^Bearer\s+/i, '');
  }

  function getUser() {
    return normalizeUser(readStorage(STORAGE_KEYS.USER));
  }

  function isAuthenticated() {
    return Boolean(getToken());
  }

  function notify() {
    const snapshot = { token: getToken(), user: getUser(), authenticated: isAuthenticated() };
    for (const listener of listeners) {
      try {
        listener(snapshot);
      } catch (_) {
        // 单个订阅者异常不影响主流程
      }
    }
  }

  function subscribe(listener) {
    if (typeof listener !== 'function') return () => {};
    listeners.add(listener);
    return () => listeners.delete(listener);
  }

  function setSession({ token, user } = {}) {
    const cleanToken = cleanText(token).replace(/^Bearer\s+/i, '');
    if (!cleanToken) {
      const error = new Error('登录响应缺少有效令牌');
      error.code = 'INVALID_TOKEN';
      throw error;
    }
    writeStorage(STORAGE_KEYS.TOKEN, cleanToken);
    const normalized = normalizeUser(user);
    if (normalized) {
      writeStorage(STORAGE_KEYS.USER, normalized);
    }
    notify();
    return { token: cleanToken, user: normalized };
  }

  function setUser(user) {
    const normalized = normalizeUser(user);
    writeStorage(STORAGE_KEYS.USER, normalized);
    notify();
    return normalized;
  }

  function clearSession() {
    writeStorage(STORAGE_KEYS.TOKEN, '');
    writeStorage(STORAGE_KEYS.USER, '');
    if (onSessionClear) onSessionClear();
    notify();
  }

  function saveRedirect(url) {
    const valid = sanitizeRedirectUrl(url);
    if (valid) {
      writeStorage(STORAGE_KEYS.REDIRECT, valid);
    }
  }

  function consumePostLoginRedirect(fallback = '/modules/post/pages/home/index') {
    const saved = sanitizeRedirectUrl(readStorage(STORAGE_KEYS.REDIRECT));
    writeStorage(STORAGE_KEYS.REDIRECT, '');
    return saved || fallback;
  }

  // 并发请求同时触发 401 时只执行一次清理与跳转，避免页面栈压入多个登录页。
  function requireLogin(explicitRedirectUrl) {
    const currentUrl = explicitRedirectUrl || (router && router.currentPageUrl ? router.currentPageUrl() : '');
    saveRedirect(currentUrl);
    clearSession();

    if (redirecting || !router || typeof router.openLogin !== 'function') {
      return Promise.resolve(false);
    }
    redirecting = true;
    let navTask;
    try {
      navTask = Promise.resolve(router.openLogin());
    } catch (err) {
      redirecting = false;
      return Promise.reject(err);
    }
    return navTask.finally(() => {
      redirecting = false;
    });
  }

  async function loginWithPassword(http, credentials = {}) {
    const username = cleanText(credentials.username);
    const password = typeof credentials.password === 'string' ? credentials.password : '';
    if (!username || !password.trim()) {
      const error = new Error('请输入账号和密码');
      error.code = 40001;
      throw error;
    }
    const payload = await http.request({
      path: '/auth/login',
      method: 'POST',
      data: { username, password },
      authRequired: false
    });
    const token = typeof payload === 'string'
      ? payload
      : cleanText(payload && (payload.token || payload.accessToken));
    const inlineUser = payload && typeof payload === 'object' ? (payload.user || payload.account || payload) : null;
    const session = setSession({ token, user: inlineUser && inlineUser.token ? null : inlineUser });

    if (!session.user) {
      try {
        await fetchCurrentUser(http);
      } catch (_) {
        // 登录已成功签发令牌时，个人信息补充失败不阻断主登录流程
      }
    }
    return { token: getToken(), user: getUser() };
  }

  async function fetchCurrentUser(http) {
    const data = await http.request({ path: '/users/me', method: 'GET', authRequired: true });
    return setUser(data);
  }

  function logout() {
    writeStorage(STORAGE_KEYS.REDIRECT, '');
    clearSession();
  }

  return {
    STORAGE_KEYS,
    bind,
    getToken,
    getUser,
    isAuthenticated,
    subscribe,
    setSession,
    setUser,
    clearSession,
    saveRedirect,
    consumePostLoginRedirect,
    requireLogin,
    loginWithPassword,
    fetchCurrentUser,
    logout
  };
}

const defaultAuth = createAuthService();

module.exports = Object.assign({ createAuthService, normalizeUser, sanitizeRedirectUrl, STORAGE_KEYS }, defaultAuth);
