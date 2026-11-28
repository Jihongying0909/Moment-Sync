// 标签、心情与校园地点配置服务。
// 提供 BIGINT 字符串安全归一化、并发单飞去重（single-flight）与短期缓存，不内置任何虚假选项。

const DEFAULT_CACHE_TTL_MS = 5 * 60 * 1000;

function cleanText(value) {
  if (value === null || value === undefined) return '';
  return String(value).replace(/[\u200B-\u200D\uFEFF]/g, '').trim();
}

function unwrapItems(payload) {
  if (Array.isArray(payload)) return payload;
  if (payload && Array.isArray(payload.items)) return payload.items;
  return [];
}

function normalizeTagList(payload) {
  const seen = new Set();
  const list = [];
  for (const raw of unwrapItems(payload)) {
    if (!raw || typeof raw !== 'object') continue;
    const id = cleanText(raw.id ?? raw.tagId);
    const name = cleanText(raw.name ?? raw.label);
    if (!/^[1-9]\d*$/.test(id) || !name || seen.has(id)) continue;
    seen.add(id);
    list.push({ id, name });
  }
  return list;
}

function normalizeNameList(payload) {
  const seen = new Set();
  const list = [];
  for (const raw of unwrapItems(payload)) {
    const name = typeof raw === 'string' || typeof raw === 'number'
      ? cleanText(raw)
      : cleanText(raw && (raw.name ?? raw.label ?? raw.value));
    if (!name || seen.has(name)) continue;
    seen.add(name);
    list.push(name);
  }
  return list;
}

function createConfigService(deps = {}) {
  let http = deps.http || null;
  const ttlMs = typeof deps.ttlMs === 'number' ? deps.ttlMs : DEFAULT_CACHE_TTL_MS;
  const now = typeof deps.now === 'function' ? deps.now : () => Date.now();
  const cache = new Map();
  const inflight = new Map();

  function bind({ http: nextHttp } = {}) {
    if (nextHttp) http = nextHttp;
  }

  function invalidate(key) {
    if (key) {
      cache.delete(key);
      inflight.delete(key);
      return;
    }
    cache.clear();
    inflight.clear();
  }

  function fetchCached(key, path, normalizer, options = {}) {
    const force = Boolean(options.force);
    if (!force && cache.has(key)) {
      const entry = cache.get(key);
      if (now() - entry.timestamp < ttlMs) {
        return Promise.resolve(entry.value.slice());
      }
      cache.delete(key);
    }

    if (!force && inflight.has(key)) {
      return inflight.get(key).then((list) => list.slice());
    }

    if (!http || typeof http.request !== 'function') {
      return Promise.reject(new Error('配置服务尚未绑定网络客户端'));
    }

    const task = http.request({ path, method: 'GET', authRequired: true })
      .then((payload) => {
        const normalized = normalizer(payload);
        cache.set(key, { value: normalized, timestamp: now() });
        return normalized;
      })
      .finally(() => {
        inflight.delete(key);
      });

    inflight.set(key, task);
    return task.then((list) => list.slice());
  }

  function listTags(options) {
    return fetchCached('tags', '/tags', normalizeTagList, options);
  }

  function listMoods(options) {
    return fetchCached('moods', '/moods', normalizeNameList, options);
  }

  function listLocations(options) {
    return fetchCached('locations', '/locations', normalizeNameList, options);
  }

  return {
    bind,
    invalidate,
    listTags,
    listMoods,
    listLocations
  };
}

const defaultConfigService = createConfigService();

module.exports = Object.assign(
  { createConfigService, normalizeTagList, normalizeNameList },
  defaultConfigService
);
