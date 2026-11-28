Component({
  properties: { url: { type: String, value: '/modules/search-comment/pages/search/index' } },
  methods: {
    open() {
      wx.navigateTo({ url: this.data.url, fail: () => wx.showToast({ title: '搜索页面尚未注册', icon: 'none' }) });
    }
  }
});
