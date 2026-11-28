const shared = require('../picker-shared');

Component({
  properties: {
    label: { type: String, value: '标签' },
    options: { type: Array, value: [] },
    values: { type: Array, value: [] },
    max: { type: Number, value: 5 },
    disabled: { type: Boolean, value: false },
    emptyText: { type: String, value: '标签配置暂未接入' }
  },

  data: {
    items: [],
    selectedCount: 0,
    limitHint: ''
  },

  observers: {
    'options, values'(options, values) {
      this.syncItems(options, values);
    }
  },

  lifetimes: {
    attached() {
      this.syncItems(this.data.options, this.data.values);
    }
  },

  methods: {
    syncItems(options, values) {
      const items = shared.normalizePickerItems(options, values);
      const selectedCount = shared.currentSelectedValues(items).length;
      this.setData({ items, selectedCount, limitHint: '' });
    },

    onToggle(event) {
      if (this.data.disabled) return;
      const dataset = (event && event.currentTarget && event.currentTarget.dataset) || {};
      const items = shared.normalizePickerItems(this.data.options, this.data.values);
      const outcome = shared.toggleMultiple(items, dataset.value, this.data.max);
      if (outcome.limited) {
        const message = `最多选择 ${outcome.max} 个标签`;
        this.setData({ limitHint: message });
        this.triggerEvent('limit', { max: outcome.max, message });
        return;
      }
      if (this.data.limitHint) {
        this.setData({ limitHint: '' });
      }
      this.triggerEvent('change', {
        value: outcome.values,
        values: outcome.values,
        multiple: true
      });
    },

    onClear() {
      if (this.data.disabled) return;
      this.setData({ limitHint: '' });
      this.triggerEvent('change', { value: [], values: [], multiple: true });
    }
  }
});
