const api = require('../../services/client');
Component({
  properties: { postId: { type: String, value: '', observer: 'onPostChange' } },
  data: { items: [], total: 0, page: 0, content: '', loading: false, sending: false,
    error: '', submitError: '', hasMore: false, loaded: false },
  lifetimes: {
    attached() { this._ready = true; this.onPostChange(); },
    detached() {
      this._ready = false;
      this._generation = (this._generation || 0) + 1;
      this._submitToken = (this._submitToken || 0) + 1;
    }
  },
  methods: {
    onPostChange() {
      if (!this._ready) return;
      this._submitToken = (this._submitToken || 0) + 1;
      this.setData({ content: '', sending: false, submitError: '' });
      return this.refresh();
    },
    refresh() {
      this._generation = (this._generation || 0) + 1;
      this.setData({ items: [], total: 0, page: 0, hasMore: false, error: '', loading: false, loaded: false });
      this.triggerEvent('countchange', { total: 0, loaded: false });
      if (this.data.postId) return this.fetchPage(1, this._generation);
    },
    async fetchPage(page, generation) {
      this._retryPage = page;
      this.setData({ loading: true, error: '' });
      try {
        const result = await api.comments(this.data.postId, page);
        if (generation !== this._generation) return;
        // 分页期间有新评论时，offset 可能重叠；按 ID 去重。
        const merged = page === 1 ? result.items : this.data.items.concat(result.items);
        const items = merged.filter((item, index) => merged.findIndex(other => other.id === item.id) === index);
        this.setData({ items, total: result.total, page, loaded: true, hasMore: page * result.pageSize < result.total });
        this.triggerEvent('countchange', { total: result.total, loaded: true });
      } catch (error) {
        if (generation === this._generation) this.setData({ error: error.message });
      } finally {
        if (generation === this._generation) this.setData({ loading: false });
      }
    },
    onInput(event) { this.setData({ content: event.detail.value, submitError: '' }); },
    onMore() {
      if (!this.data.loading && this.data.hasMore) return this.fetchPage(this.data.page + 1, this._generation);
    },
    onRetry() { if (!this.data.loading) return this.fetchPage(this._retryPage || 1, this._generation); },
    async onSubmit() {
      if (this.data.sending || !this.data.postId) return;
      const content = this.data.content.trim();
      if (!content || content.length > 500) { this.setData({ submitError: '请输入 1–500 个字符的评论' }); return; }
      const postId = this.data.postId;
      const generation = this._generation;
      const submitToken = this._submitToken = (this._submitToken || 0) + 1;
      this.setData({ sending: true, submitError: '' });
      try {
        await api.publish(postId, content);
        if (postId !== this.data.postId || generation !== this._generation) return;
        this.setData({ content: '' });
        this.triggerEvent('submitted', { postId });
        wx.showToast({ title: '评论已送达', icon: 'success' });
        await this.refresh();
      } catch (error) {
        if (postId === this.data.postId && generation === this._generation) this.setData({ submitError: error.message });
      } finally {
        if (submitToken === this._submitToken) this.setData({ sending: false });
      }
    }
  }
});
