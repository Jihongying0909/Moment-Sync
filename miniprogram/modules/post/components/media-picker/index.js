var LIMITS = require('../../services/post-model').LIMITS;

Component({
  properties: {
    path: { type: String, value: '' },
    disabled: { type: Boolean, value: false },
    maxSizeMb: { type: Number, value: LIMITS.imageMb }
  },
  methods: {
    choose: function () {
      if (this.data.disabled) return;
      var self = this;
      var choose = wx.chooseMedia || wx.chooseImage;
      if (!choose) {
        this.triggerEvent('error', { message: '当前微信版本不支持选择图片' });
        return;
      }
      choose({
        count: 1,
        mediaType: ['image'],
        sourceType: ['album', 'camera'],
        sizeType: ['compressed'],
        success: function (res) {
          var file = res.tempFiles && res.tempFiles[0] ? res.tempFiles[0] : null;
          var path = file ? (file.tempFilePath || file.path) : (res.tempFilePaths && res.tempFilePaths[0]);
          var size = file ? Number(file.size) : 0;
          if (!path) {
            self.triggerEvent('error', { message: '未获取到图片，请重试' });
            return;
          }
          if (size && size > self.data.maxSizeMb * 1024 * 1024) {
            self.triggerEvent('error', { message: '图片不能超过 ' + self.data.maxSizeMb + 'MB，请重新选择' });
            return;
          }
          self.triggerEvent('change', { path: path, size: size });
        },
        fail: function (error) {
          if (error && error.errMsg && error.errMsg.indexOf('cancel') !== -1) return;
          self.triggerEvent('error', { message: '选择图片失败，请重试' });
        }
      });
    },
    preview: function () {
      if (!this.data.path) return;
      wx.previewImage({ current: this.data.path, urls: [this.data.path] });
    },
    clear: function () {
      if (this.data.disabled) return;
      this.triggerEvent('change', { path: '', size: 0 });
    }
  }
});
