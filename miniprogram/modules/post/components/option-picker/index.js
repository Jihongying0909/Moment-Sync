Component({
  properties: {
    label: { type: String, value: '' },
    options: { type: Array, value: [] },
    multiple: { type: Boolean, value: false },
    disabled: { type: Boolean, value: false },
    emptyText: { type: String, value: '配置项暂未接入' }
  },
  methods: {
    onTap: function (event) {
      if (this.data.disabled) return;
      var value = String(event.currentTarget.dataset.value || '');
      if (!value) return;
      var options = this.data.options || [];
      var current = [];
      var isSelected = false;
      for (var i = 0; i < options.length; i += 1) {
        if (options[i].selected) current.push(String(options[i].value));
        if (String(options[i].value) === value && options[i].selected) isSelected = true;
      }
      if (this.data.multiple) {
        var next = [];
        for (var j = 0; j < current.length; j += 1) {
          if (current[j] !== value) next.push(current[j]);
        }
        if (!isSelected) next.push(value);
        this.triggerEvent('change', { value: next, values: next, multiple: true });
        return;
      }
      var single = isSelected ? '' : value;
      this.triggerEvent('change', { value: single, values: single ? [single] : [], multiple: false });
    }
  }
});
