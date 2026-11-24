var api = require('../../services/client');

var PAGE_SIZE = 20;

Page({
  data: {
    items: [],
    total: 0,
    page: 0,
    hasMore: false,
    loading: false,
    error: '',
    loaded: false
  },

  onLoad: function () {
    this._generation = 0;
    this.refresh();
  },

  onUnload: function () {
    this._generation += 1;
  },

  refresh: function () {
    this._generation += 1;
    this._retryPage = 1;
    this.setData({ items: [], total: 0, page: 0, hasMore: false, error: '', loaded: false });
    return this.fetchPage(1, this._generation);
  },

  fetchPage: function (page, generation) {
    var self = this;
    this._retryPage = page;
    this.setData({ loading: true, error: '' });
    return api.listPosts({ page: page, pageSize: PAGE_SIZE }).then(function (result) {
      if (generation !== self._generation) return;
      var items = page === 1 ? result.items : self.data.items.concat(result.items);
      self.setData({
        items: items,
        total: result.total,
        page: result.page,
        hasMore: result.hasMore,
        loaded: true
      });
    }).catch(function (error) {
      if (generation === self._generation) self.setData({ error: error.message });
    }).then(function () {
      if (generation === self._generation) self.setData({ loading: false });
    });
  },

  onMore: function () {
    if (this.data.loading || !this.data.hasMore) return;
    return this.fetchPage(this.data.page + 1, this._generation);
  },

  onRetry: function () {
    if (this.data.loading) return;
    return this.fetchPage(this._retryPage || 1, this._generation);
  },

  onPullDownRefresh: function () {
    var self = this;
    return this.refresh().then(function () { wx.stopPullDownRefresh(); });
  },

  onReachBottom: function () {
    this.onMore();
  },

  onOpenPost: function (event) {
    var source = event.detail || {};
    var id = source.id || (event.currentTarget && event.currentTarget.dataset.id);
    Promise.resolve().then(function () { return api.openDetail(id); })
      .catch(function (error) { wx.showToast({ title: error.message, icon: 'none' }); });
  },

  onOpenPublish: function () {
    Promise.resolve().then(function () { return api.openPublish(); })
      .catch(function (error) { wx.showToast({ title: error.message, icon: 'none' }); });
  },

  onOpenSearch: function () {
    Promise.resolve().then(function () { return api.openSearch(); })
      .catch(function (error) { wx.showToast({ title: error.message, icon: 'none' }); });
  }
});
