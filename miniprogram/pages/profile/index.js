const { getServices } = require('../../app');

Page({
  data: {
    authenticated: false,
    user: null,
    avatarInitial: '光',
    loading: false,
    error: ''
  },

  onShow() {
    this.syncSession(true);
  },

  onPullDownRefresh() {
    Promise.resolve(this.syncSession(true)).finally(() => {
      if (typeof wx !== 'undefined' && wx.stopPullDownRefresh) {
        wx.stopPullDownRefresh();
      }
    });
  },

  syncSession(fetchRemote) {
    const { auth, http } = getServices();
    const authenticated = auth.isAuthenticated();
    const cachedUser = auth.getUser();
    this.applyUser(authenticated, cachedUser);

    if (!authenticated || !fetchRemote) {
      return Promise.resolve();
    }

    this.setData({ loading: true, error: '' });
    return auth.fetchCurrentUser(http)
      .then((freshUser) => {
        this.applyUser(auth.isAuthenticated(), freshUser);
      })
      .catch((err) => {
        this.setData({
          authenticated: auth.isAuthenticated(),
          error: (err && err.message) || '刷新个人信息失败'
        });
      })
      .finally(() => {
        this.setData({ loading: false });
      });
  },

  applyUser(authenticated, user) {
    const name = (user && user.nickname) || '校园同学';
    this.setData({
      authenticated,
      user: authenticated ? user : null,
      avatarInitial: name.slice(0, 1)
    });
  },

  onGoLogin() {
    const { auth, router } = getServices();
    auth.saveRedirect(router.ROUTES.PROFILE);
    return router.openLogin();
  },

  onOpenPublish() {
    const { router } = getServices();
    return router.openPublish();
  },

  onLogout() {
    const { auth } = getServices();
    auth.logout();
    this.applyUser(false, null);
    this.setData({ error: '' });
    if (typeof wx !== 'undefined' && wx.showToast) {
      wx.showToast({ title: '已退出登录', icon: 'none' });
    }
  }
});
