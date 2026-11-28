// 配置选择组件纯函数核心。零 wx 依赖，同时兼容预装饰选项 {value,label,selected} 与原始配置项 {id,name} + values。

function cleanText(value) {
  if (value === null || value === undefined) return '';
  return String(value).replace(/[\u200B-\u200D\uFEFF]/g, '').trim();
}

function normalizePickerItems(options, externalSelected) {
  if (!Array.isArray(options)) return [];
  const hasExternal = Array.isArray(externalSelected)
    ? externalSelected.length > 0
    : Boolean(cleanText(externalSelected));
  const selectedSet = new Set(
    Array.isArray(externalSelected)
      ? externalSelected.map(cleanText).filter(Boolean)
      : (cleanText(externalSelected) ? [cleanText(externalSelected)] : [])
  );

  const items = [];
  const seen = new Set();
  for (const raw of options) {
    if (raw === null || raw === undefined) continue;
    let value = '';
    let label = '';
    let itemSelected = false;

    if (typeof raw === 'string' || typeof raw === 'number') {
      value = cleanText(raw);
      label = value;
    } else if (typeof raw === 'object') {
      value = cleanText(raw.value ?? raw.id ?? raw.name);
      label = cleanText(raw.label ?? raw.name ?? raw.value);
      itemSelected = Boolean(raw.selected);
    }

    if (!value || !label || seen.has(value)) continue;
    seen.add(value);
    items.push({
      value,
      label,
      selected: hasExternal ? selectedSet.has(value) : itemSelected
    });
  }
  return items;
}

function currentSelectedValues(items) {
  const result = [];
  for (const item of items) {
    if (item.selected) result.push(item.value);
  }
  return result;
}

function toggleMultiple(items, targetValue, maxCount = 5) {
  const target = cleanText(targetValue);
  if (!target) {
    const unchanged = currentSelectedValues(items);
    return { values: unchanged, limited: false };
  }
  const current = currentSelectedValues(items);
  const exists = current.includes(target);
  if (exists) {
    const next = current.filter((item) => item !== target);
    return { values: next, limited: false };
  }
  const limit = Number.isInteger(maxCount) && maxCount > 0 ? maxCount : 5;
  if (current.length >= limit) {
    return { values: current, limited: true, max: limit };
  }
  return { values: current.concat(target), limited: false };
}

function toggleSingle(items, targetValue) {
  const target = cleanText(targetValue);
  if (!target) return '';
  const current = currentSelectedValues(items);
  return current.length === 1 && current[0] === target ? '' : target;
}

module.exports = {
  cleanText,
  normalizePickerItems,
  currentSelectedValues,
  toggleMultiple,
  toggleSingle
};
