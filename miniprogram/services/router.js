// 统一路由表与导航器。自动区分 TabBar 页面与普通页面栈，处理登录后原路径回跳。
const ROUTES = Object.freeze({
  HOME: '/modules/post/pages/home/index',
  PUBLISH: '/modules/post/pages/publish/index',
  DETAIL: '/modules/post/pages/detail/index',
  SEARCH: '/modules/search-comment/pages/search/index',
  LOGIN: '/pages/login/index',
  PROFILE: '/pages/profile/index'
});

const TAB_PATHS = new Set([ROUTES.HOME, ROUTES.PROFILE]);

function stripQuery(url) {
  return String(url || '').split('?')[0];
}

function isTabPath(url) {
  return TAB_PATHS.has(stripQuery(url));
}

function isValidPostId(id) {
  const text = String(id ?? '').trim();
  return /^[1-9]\d*$/.test(text) ? text : '';
}

function serializeQuery(options = {}) {
  const pairs = [];
  for (const [key, rawValue] of Object.entries(options || {})) {
    if (rawValue === undefined || rawValue === null || rawValue === '') continue;
    pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(rawValue))}`);
  }
  return pairs.length ? `?${pairs.join('&')}` : '';
}

function createRouter(deps = {}) {
  const getWx = () => deps.wx || (typeof wx !== 'undefined' ? wx : null);
  const getPages = () => {
    if (typeof deps.getCurrentPages === 'function') return deps.getCurrentPages();
    if (typeof getCurrentPages === 'function') return getCurrentPages();
    return [];
  };

  function currentPageUrl() {
    const pages = getPages();
    const top = Array.isArray(pages) && pages.length ? pages[pages.length - 1] : null;
    if (!top || !top.route) return '';
    const route = top.route.startsWith('/') ? top.route : `/${top.route}`;
    return `${route}${serializeQuery(top.options)}`;
  }

  function callWxNav(method, url) {
    const api = getWx();
    if (!api || typeof api[method] !== 'function') {
      return Promise.resolve(false);
    }
    return new Promise((resolve) => {
      api[method]({
        url,
        success: () => resolve(true),
        fail: () => resolve(false)
      });
    });
  }

  function openHome(options = {}) {
    if (options.relaunch) {
      return callWxNav('reLaunch', ROUTES.HOME);
    }
    return callWxNav('switchTab', ROUTES.HOME).then((ok) => (
      ok ? true : callWxNav('reLaunch', ROUTES.HOME)
    ));
  }

  function openProfile() {
    return callWxNav('switchTab', ROUTES.PROFILE).then((ok) => (
      ok ? true : callWxNav('navigateTo', ROUTES.PROFILE)
    ));
  }

  function openPublish() {
    return callWxNav('navigateTo', ROUTES.PUBLISH);
  }

  function openSearch() {
    return callWxNav('navigateTo', ROUTES.SEARCH);
  }

  function openDetail(id) {
    const postId = isValidPostId(id);
    if (!postId) {
      const error = new Error('印记 ID 无效');
      error.code = 'INVALID_ID';
      return Promise.reject(error);
    }
    return callWxNav('navigateTo', `${ROUTES.DETAIL}?id=${encodeURIComponent(postId)}`);
  }

  function openLogin() {
    if (stripQuery(currentPageUrl()) === ROUTES.LOGIN) {
      return Promise.resolve(true);
    }
    return callWxNav('navigateTo', ROUTES.LOGIN);
  }

  function redirectAfterLogin(targetUrl) {
    const target = typeof targetUrl === 'string' && targetUrl.startsWith('/')
      ? targetUrl
      : ROUTES.HOME;
    if (stripQuery(target) === ROUTES.LOGIN) {
      return openHome({ relaunch: true });
    }
    if (isTabPath(target)) {
      // Tab 页使用 reLaunch 触发页面重新加载，确保拿到新登录态刷新首屏数据。
      return callWxNav('reLaunch', stripQuery(target));
    }
    return callWxNav('redirectTo', target).then((ok) => (
      ok ? true : openHome({ relaunch: true })
    ));
  }

  return {
    ROUTES,
    isTabPath,
    currentPageUrl,
    openHome,
    openProfile,
    openPublish,
    openSearch,
    openDetail,
    openLogin,
    redirectAfterLogin
  };
}

const defaultRouter = createRouter();

module.exports = Object.assign({ createRouter, ROUTES, isTabPath, serializeQuery }, defaultRouter);
