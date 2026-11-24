const shared = require('../picker-shared');

Component({
  properties: {
    label: { type: String, value: '心情' },
    options: { type: Array, value: [] },
    value: { type: String, value: '' },
    disabled: { type: Boolean, value: false },
    emptyText: { type: String, value: '心情配置暂未接入' }
  },

  data: {
    items: [],
    current: ''
  },

  observers: {
    'options, value'(options, value) {
      this.syncItems(options, value);
    }
  },

  lifetimes: {
    attached() {
      this.syncItems(this.data.options, this.data.value);
    }
  },

  methods: {
    syncItems(options, value) {
      const items = shared.normalizePickerItems(options, value);
      const selected = shared.currentSelectedValues(items);
      this.setData({ items, current: selected[0] || '' });
    },

    onSelect(event) {
      if (this.data.disabled) return;
      const dataset = (event && event.currentTarget && event.currentTarget.dataset) || {};
      const items = shared.normalizePickerItems(this.data.options, this.data.value);
      const next = shared.toggleSingle(items, dataset.value);
      this.triggerEvent('change', {
        value: next,
        values: next ? [next] : [],
        multiple: false
      });
    }
  }
});
