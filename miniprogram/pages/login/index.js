const { getServices } = require('../../app');

Page({
  data: {
    username: '',
    password: '',
    submitting: false,
    error: ''
  },

  onUsernameInput(event) {
    this.setData({
      username: (event.detail && event.detail.value) || '',
      error: ''
    });
  },

  onPasswordInput(event) {
    this.setData({
      password: (event.detail && event.detail.value) || '',
      error: ''
    });
  },

  onSubmit() {
    if (this.data.submitting) return Promise.resolve();
    const { auth, http, router } = getServices();
    const username = String(this.data.username || '').trim();
    const password = String(this.data.password || '');

    if (!username || !password.trim()) {
      this.setData({ error: '请输入账号和密码' });
      return Promise.resolve();
    }

    this.setData({ submitting: true, error: '' });
    return auth.loginWithPassword(http, { username, password })
      .then(() => {
        this.setData({ submitting: false, password: '' });
        if (typeof wx !== 'undefined' && wx.showToast) {
          wx.showToast({ title: '登录成功', icon: 'success' });
        }
        const target = auth.consumePostLoginRedirect(router.ROUTES.HOME);
        return router.redirectAfterLogin(target);
      })
      .catch((err) => {
        this.setData({
          submitting: false,
          error: (err && err.message) || '登录失败，请检查账号或密码'
        });
      });
  }
});
