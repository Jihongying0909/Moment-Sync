var api = require('../../services/client');
var model = require('../../services/post-model');

Page({
  data: {
    title: '',
    content: '',
    titleLength: 0,
    contentLength: 0,
    imagePath: '',
    imageSize: 0,
    tags: [],
    moods: [],
    locations: [],
    tagOptions: [],
    moodOptions: [],
    locationOptions: [],
    tagIds: [],
    mood: '',
    campusLocation: '',
    configError: '',
    error: '',
    status: 'idle',
    uploading: false,
    submitting: false
  },

  onLoad: function () {
    this._generation = 0;
    this.loadConfig();
  },

  onUnload: function () {
    this._generation += 1;
  },

  loadConfig: function () {
    var self = this;
    var generation = this._generation;
    return api.loadConfig().then(function (config) {
      if (generation !== self._generation) return;
      self.setData({
        tags: config.tags,
        moods: config.moods,
        locations: config.locations,
        tagOptions: model.decorateOptions(config.tags, self.data.tagIds),
        moodOptions: model.decorateOptions(config.moods, self.data.mood),
        locationOptions: model.decorateOptions(config.locations, self.data.campusLocation),
        configError: config.tags.length || config.moods.length || config.locations.length
          ? ''
          : '标签、心情和地点配置尚未接入；可先发布不带选项的印记。'
      });
    }).catch(function (error) {
      if (generation === self._generation) self.setData({ configError: error.message });
    });
  },

  onTitleInput: function (event) {
    var value = event.detail.value || '';
    this.setData({ title: value, titleLength: value.length });
  },

  onContentInput: function (event) {
    var value = event.detail.value || '';
    this.setData({ content: value, contentLength: value.length });
  },

  onImageChange: function (event) {
    this.setData({
      imagePath: event.detail.path || '',
      imageSize: event.detail.size || 0,
      error: ''
    });
  },

  onImageError: function (event) {
    this.setData({ error: event.detail.message || '选择图片失败' });
  },

  onTagsChange: function (event) {
    var values = event.detail.values || [];
    this.setData({ tagIds: values, tagOptions: model.decorateOptions(this.data.tags, values) });
  },

  onMoodChange: function (event) {
    var value = event.detail.value || '';
    this.setData({ mood: value, moodOptions: model.decorateOptions(this.data.moods, value) });
  },

  onLocationChange: function (event) {
    var value = event.detail.value || '';
    this.setData({ campusLocation: value, locationOptions: model.decorateOptions(this.data.locations, value) });
  },

  onSubmit: function () {
    var self = this;
    if (this.data.submitting || this.data.uploading) return;
    this.setData({ error: '', status: 'validating' });
    var draft;
    try {
      draft = model.validateDraft(this.data);
    } catch (error) {
      this.setData({ error: error.message, status: 'error' });
      return;
    }
    this.setData({
      submitting: true,
      uploading: !!draft.imagePath,
      status: draft.imagePath ? 'uploading' : 'submitting'
    });
    return Promise.resolve()
      .then(function () {
        if (!draft.imagePath) return '';
        // 创建失败后重试时复用本次会话已上传的地址，避免重复产生媒体文件。
        if (self._uploadedPath === draft.imagePath) return self._uploadedUrl;
        return api.uploadImage(draft.imagePath).then(function (url) {
          self._uploadedPath = draft.imagePath;
          self._uploadedUrl = url;
          return url;
        });
      })
      .then(function (imageUrl) {
        self.setData({ uploading: false, status: 'submitting' });
        return api.createPost(model.toCreatePayload(draft, imageUrl));
      })
      .then(function (post) {
        self.setData({ submitting: false, status: 'success' });
        wx.showToast({ title: '发布成功', icon: 'success' });
        Promise.resolve().then(function () { return api.openHome(); }).catch(function () {});
        return post;
      })
      .catch(function (error) {
        // 失败时保留草稿与已选图片，用户可直接重试，不必重新输入。
        self.setData({
          submitting: false,
          uploading: false,
          status: 'error',
          error: error.message
        });
      });
  }
});
