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

    if (!username) {
      this.setData({ error: '账号不能为空' });
      return Promise.resolve();
    }
    if (!password) {
      this.setData({ error: '密码不能为空' });
      return Promise.resolve();
    }

    this.setData({ submitting: true, error: '' });
    
      // // --- 临时测试后门开始 ---
      // if (username === 'admin' && password === '123456') {
      //   // 假装后端返回了 token 和用户信息
      //   const mockUser = { id: '1', nickname: 'Cheang', avatarUrl: '' };
      //   wx.setStorageSync('moment_sync_token', 'fake_token_123'); // 存个假token
      //   wx.setStorageSync('moment_sync_user', mockUser); // 存个假用户
      //   this.setData({ submitting: false, password: '' });
      //   if (wx.showToast) wx.showToast({ title: '登录成功(测试)', icon: 'success' });
      //   const target = auth.consumePostLoginRedirect(router.ROUTES.HOME);
      //   return router.redirectAfterLogin(target);
      // }
      // // --- 临时测试后门结束 ---

      // 下面这行是原来的真实请求，先留着
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
        this.setData({ submitting: false });
        
        let errorMessage = '登录失败，请稍后重试';
        if (err && err.code) {
           if (err.code === 40101 || err.code === 401) {
               errorMessage = '账号或密码不正确';
           } else if (err.code === 40001 || err.code === 400) {
               errorMessage = '请求参数格式错误';
           } else {
               errorMessage = err.message || errorMessage;
           }
        } else if (err && err.message && err.message.includes('network')) {
           errorMessage = '网络连接失败，请检查后端是否启动';
        }
        
        this.setData({ error: errorMessage });
      });
  }
});