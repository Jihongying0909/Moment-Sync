var api = require('../../services/client');
var model = require('../../services/post-model');

Page({
  data: {
    post: null,
    loading: true,
    error: '',
    commentCount: null
  },

  onLoad: function (options) {
    this._generation = 0;
    try {
      this.postId = model.parseId(options && options.id);
    } catch (error) {
      this.setData({ loading: false, error: error.message });
      return;
    }
    this.load();
  },

  onUnload: function () {
    this._generation += 1;
  },

  load: function () {
    var self = this;
    var generation = this._generation;
    this.setData({ loading: true, error: '' });
    return api.getPost(this.postId).then(function (post) {
      if (generation !== self._generation) return;
      self.setData({ post: post, commentCount: post.commentCount, loading: false });
    }).catch(function (error) {
      if (generation === self._generation) self.setData({ error: error.message, loading: false });
    });
  },

  onRetry: function () {
    if (this.data.loading || !this.postId) return;
    return this.load();
  },

  onPullDownRefresh: function () {
    var comments = typeof this.selectComponent === 'function' ? this.selectComponent('#comments') : null;
    var refreshComments = comments && typeof comments.refresh === 'function' ? comments.refresh() : Promise.resolve();
    return Promise.all([this.load(), refreshComments]).then(function () { wx.stopPullDownRefresh(); });
  },

  // 搜索评论模块合并后，comment-panel 的 countchange 事件会调用这里，以真实数量覆盖详情值。
  onCommentCountChange: function (event) {
    var detail = event.detail || {};
    if (detail.loaded) this.setData({ commentCount: detail.total });
  },

  onShareAppMessage: function () {
    var post = this.data.post || {};
    return {
      title: post.title || '拾光同频',
      path: '/modules/post/pages/detail/index?id=' + this.postId
    };
  }
});
