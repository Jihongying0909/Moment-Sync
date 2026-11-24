// 江可妮：印记前端模块的数据归一化与草稿校验。
// 纯函数，不依赖 wx，便于在 Node 中做逻辑自测。

var LIMITS = {
  title: 100,
  content: 2000,
  tagCount: 5,
  mood: 30,
  location: 100,
  imageMb: 10
};

function failure(message, code) {
  var error = new Error(message);
  error.code = code || 40001;
  return error;
}

function text(value) {
  if (value === null || value === undefined) return '';
  return String(value).replace(/[\u200B-\u200D\uFEFF]/g, '').trim();
}

function firstValue() {
  for (var i = 0; i < arguments.length; i += 1) {
    var value = arguments[i];
    if (value !== null && value !== undefined && value !== '') return value;
  }
  return '';
}

function idString(value) {
  var valueText = text(value);
  return /^[1-9]\d*$/.test(valueText) ? valueText : '';
}

function parseId(value) {
  var id = idString(value);
  if (!id) throw failure('印记 ID 无效', 40001);
  return id;
}

function normalizeAuthor(raw) {
  if (!raw || typeof raw !== 'object') return { id: '', nickname: '校园同学', avatarUrl: '' };
  return {
    id: idString(raw.id || raw.userId),
    nickname: text(raw.nickname || raw.name) || '校园同学',
    avatarUrl: text(raw.avatarUrl || raw.avatar_url)
  };
}

function normalizeTags(raw) {
  if (!Array.isArray(raw)) return [];
  var result = [];
  for (var i = 0; i < raw.length; i += 1) {
    var item = raw[i];
    if (typeof item === 'string' || typeof item === 'number') {
      var nameOnly = text(item);
      if (nameOnly) result.push({ id: '', name: nameOnly });
      continue;
    }
    if (!item || typeof item !== 'object') continue;
    var name = text(firstValue(item.name, item.label, item.title));
    var id = idString(firstValue(item.id, item.tagId));
    if (name || id) result.push({ id: id, name: name || id });
  }
  return result;
}

function mediaUrl(item) {
  if (!item) return '';
  if (typeof item === 'string') return text(item);
  if (typeof item !== 'object') return '';
  return text(firstValue(item.mediaUrl, item.imageUrl, item.url, item.fileUrl, item.path));
}

function imageFrom(raw) {
  var direct = mediaUrl({ mediaUrl: firstValue(raw.imageUrl, raw.coverUrl, raw.cover, raw.mediaUrl) });
  if (direct) return direct;
  var media = firstValue(raw.postMedia, raw.medias, raw.media, raw.images);
  if (!Array.isArray(media)) return '';
  var sorted = media.slice().sort(function (a, b) {
    var left = a && typeof a === 'object' ? Number(firstValue(a.sortOrder, a.sort_order, 0)) || 0 : 0;
    var right = b && typeof b === 'object' ? Number(firstValue(b.sortOrder, b.sort_order, 0)) || 0 : 0;
    return left - right;
  });
  for (var i = 0; i < sorted.length; i += 1) {
    var url = mediaUrl(sorted[i]);
    if (url) return url;
  }
  return '';
}

function count(value) {
  var parsed = Number(value);
  return isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : 0;
}

function normalizePost(raw) {
  if (!raw || typeof raw !== 'object') return null;
  return {
    id: idString(raw.id),
    title: text(raw.title),
    content: text(raw.content),
    author: normalizeAuthor(raw.author || raw.user),
    imageUrl: imageFrom(raw),
    mood: text(raw.mood),
    campusLocation: text(firstValue(raw.campusLocation, raw.campus_location)),
    tags: normalizeTags(raw.tags),
    commentCount: count(firstValue(raw.commentCount, raw.comment_count)),
    createdAt: text(firstValue(raw.createdAt, raw.created_at)),
    status: text(raw.status) || 'PUBLISHED'
  };
}

// 配置项既可能是 {id,name}，也可能是 {"开心"} 这样的纯文本枚举。
function normalizeOptions(input) {
  if (!Array.isArray(input)) return [];
  var result = [];
  for (var i = 0; i < input.length; i += 1) {
    var item = input[i];
    if (typeof item === 'string' || typeof item === 'number') {
      var label = text(item);
      if (label) result.push({ value: label, label: label });
      continue;
    }
    if (!item || typeof item !== 'object') continue;
    var name = text(firstValue(item.name, item.label, item.title, item.value));
    var value = firstValue(item.id, item.value, item.code, name);
    var valueText = text(value);
    if (valueText) result.push({ value: valueText, label: name || valueText });
  }
  return result;
}

function decorateOptions(options, selected) {
  var values = [];
  if (Array.isArray(selected)) {
    for (var i = 0; i < selected.length; i += 1) values.push(String(selected[i]));
  } else if (selected !== '' && selected !== null && selected !== undefined) {
    values.push(String(selected));
  }
  return normalizeOptions(options).map(function (option) {
    return {
      value: option.value,
      label: option.label,
      selected: values.indexOf(option.value) !== -1
    };
  });
}

function normalizeTagIds(input) {
  var source = Array.isArray(input) ? input : [];
  var ids = [];
  for (var i = 0; i < source.length; i += 1) {
    var item = source[i];
    var raw = item && typeof item === 'object' ? firstValue(item.id, item.tagId, item.value) : item;
    var id = idString(raw);
    if (id && ids.indexOf(id) === -1) ids.push(id);
  }
  return ids;
}

function validImagePath(value) {
  if (!value) return true;
  return /^(https?:\/\/|wxfile:\/\/|file:\/\/|http:\/\/tmp\/|\/)/i.test(value);
}

function validateDraft(draft) {
  var source = draft || {};
  var title = text(firstValue(source.title, source.titleText));
  var content = text(source.content);
  if (!title || title.length > LIMITS.title) {
    throw failure('标题需为 1–' + LIMITS.title + ' 个字符', 40001);
  }
  if (!content || content.length > LIMITS.content) {
    throw failure('正文需为 1–' + LIMITS.content + ' 个字符', 40001);
  }
  var tagIds = normalizeTagIds(firstValue(source.tagIds, source.tags));
  if (tagIds.length > LIMITS.tagCount) {
    throw failure('最多选择 ' + LIMITS.tagCount + ' 个标签', 40001);
  }
  var mood = text(source.mood);
  if (mood.length > LIMITS.mood) throw failure('心情选项无效', 40001);
  var campusLocation = text(firstValue(source.campusLocation, source.location));
  if (campusLocation.length > LIMITS.location) throw failure('地点选项无效', 40001);
  var imagePath = text(firstValue(source.imagePath, source.imageUrl));
  if (imagePath && (!validImagePath(imagePath) || imagePath.length > 1000)) {
    throw failure('图片路径无效，请重新选择', 40001);
  }
  return {
    title: title,
    content: content,
    tagIds: tagIds,
    mood: mood,
    campusLocation: campusLocation,
    imagePath: imagePath
  };
}

function toCreatePayload(draft, uploadedUrl) {
  var payload = {
    title: draft.title,
    content: draft.content,
    tagIds: draft.tagIds || []
  };
  if (draft.mood) payload.mood = draft.mood;
  if (draft.campusLocation) payload.campusLocation = draft.campusLocation;
  var url = text(uploadedUrl);
  if (url) payload.imageUrl = url;
  return payload;
}

function extractUploadedUrl(data) {
  if (typeof data === 'string') return text(data);
  if (!data || typeof data !== 'object') return '';
  var url = text(firstValue(data.url, data.imageUrl, data.mediaUrl, data.fileUrl, data.path));
  if (url) return url;
  if (data.data && typeof data.data === 'object' && data.data !== data) {
    var nested = extractUploadedUrl(data.data);
    if (nested) return nested;
  }
  if (Array.isArray(data.files)) return extractUploadedUrl(data.files[0]);
  if (data.file) return extractUploadedUrl(data.file);
  return '';
}

function buildPageQuery(page, pageSize) {
  var parsedPage = Math.floor(Number(page));
  var parsedSize = Math.floor(Number(pageSize));
  return {
    page: isFinite(parsedPage) && parsedPage >= 1 ? Math.min(parsedPage, 100000) : 1,
    pageSize: isFinite(parsedSize) && parsedSize >= 1 ? Math.min(parsedSize, 50) : 20
  };
}

function normalizePage(result, page, pageSize) {
  var source = result && typeof result === 'object' ? result : {};
  var query = buildPageQuery(page, pageSize);
  var items = Array.isArray(source.items) ? source.items.map(normalizePost).filter(Boolean) : [];
  var total = count(source.total);
  return {
    items: items,
    total: total,
    page: query.page,
    pageSize: query.pageSize,
    hasMore: items.length + (query.page - 1) * query.pageSize < total
  };
}

module.exports = {
  LIMITS: LIMITS,
  failure: failure,
  text: text,
  parseId: parseId,
  normalizeAuthor: normalizeAuthor,
  normalizeTags: normalizeTags,
  normalizePost: normalizePost,
  normalizeOptions: normalizeOptions,
  decorateOptions: decorateOptions,
  normalizeTagIds: normalizeTagIds,
  validateDraft: validateDraft,
  toCreatePayload: toCreatePayload,
  extractUploadedUrl: extractUploadedUrl,
  buildPageQuery: buildPageQuery,
  normalizePage: normalizePage
};
