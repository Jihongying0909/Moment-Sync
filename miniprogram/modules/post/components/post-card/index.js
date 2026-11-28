Component({
  properties: {
    post: { type: Object, value: {} },
    showExcerpt: { type: Boolean, value: true }
  },
  methods: {
    open: function () {
      var post = this.data.post || {};
      if (!post.id) return;
      this.triggerEvent('open', { id: String(post.id) });
    }
  }
});
