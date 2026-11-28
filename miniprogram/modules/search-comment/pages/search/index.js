const api = require('../../services/client');
Page({
  data: {
    keyword: '', tagId: '', tags: [], tagsError: '', tagsLoading: false,
    items: [], total: 0, page: 0, loading: false, error: '', hasMore: false, searched: false
  },
  onLoad() { this._generation = 0; this.loadTags(); this.runSearch(); },
  onUnload() { this._generation += 1; },
  onInput(event) { this.setData({ keyword: event.detail.value }); },
  onConfirm() { this.runSearch(); },
  onClear() { this.setData({ keyword: '', tagId: '' }); this.runSearch(); },
  onTag(event) { this.setData({ tagId: String(event.currentTarget.dataset.id || '') }); this.runSearch(); },
  async loadTags() {
    if (this.data.tagsLoading) return;
    this.setData({ tagsLoading: true, tagsError: '' });
    try { this.setData({ tags: await api.loadTags() }); }
    catch (error) { this.setData({ tagsError: error.message }); }
    finally { this.setData({ tagsLoading: false }); }
  },
  runSearch() {
    this._generation += 1;
    // 分页固定在已提交的条件上，输入框中未提交的文字不会污染下一页。
    this._query = { keyword: this.data.keyword.trim(), tagId: this.data.tagId };
    this.setData({ items: [], total: 0, page: 0, error: '', hasMore: false, searched: false });
    return this.fetchPage(1, this._generation);
  },
  async fetchPage(page, generation) {
    this._retryPage = page;
    this.setData({ loading: true, error: '' });
    const query = { keyword: this._query.keyword, page, pageSize: 20 };
    if (this._query.tagId) query.tagId = this._query.tagId;
    try {
      const result = await api.search(query);
      if (generation !== this._generation) return;
      const items = page === 1 ? result.items : this.data.items.concat(result.items);
      this.setData({ items, total: result.total, page, hasMore: items.length < result.total, searched: true });
    } catch (error) {
      if (generation === this._generation) this.setData({ error: error.message });
    } finally {
      if (generation === this._generation) this.setData({ loading: false });
    }
  },
  onMore() {
    if (!this.data.loading && this.data.hasMore) return this.fetchPage(this.data.page + 1, this._generation);
  },
  onRetry() { if (!this.data.loading) return this.fetchPage(this._retryPage || 1, this._generation); },
  onReachBottom() { this.onMore(); },
  async onPullDownRefresh() { try { await this.runSearch(); } finally { wx.stopPullDownRefresh(); } },
  onOpen(event) {
    Promise.resolve().then(() => api.openPost(event.currentTarget.dataset.id))
      .catch(error => wx.showToast({ title: error.message, icon: 'none' }));
  }
});
