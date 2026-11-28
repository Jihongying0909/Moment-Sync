// 环境与接口基础地址解析。不硬编码任何密钥、密码或真实生产凭据。
const DEFAULT_API_BASE_URL = 'http://localhost:8080/api';
const BASE_URL_STORAGE_KEY = 'moment_sync_api_base_url';

function normalizeBaseUrl(raw) {
  const value = typeof raw === 'string' ? raw.trim().replace(/\/+$/, '') : '';
  if (!/^https?:\/\/[^\s/$.?#].[^\s]*$/i.test(value)) {
    return '';
  }
  return value;
}

function resolveEnv(options = {}) {
  const storage = options.storage || (typeof wx !== 'undefined' ? wx : null);
  let override = '';
  if (options.baseUrl) {
    override = normalizeBaseUrl(options.baseUrl);
  } else if (storage && typeof storage.getStorageSync === 'function') {
    try {
      override = normalizeBaseUrl(storage.getStorageSync(BASE_URL_STORAGE_KEY));
    } catch (_) {
      override = '';
    }
  }
  return Object.freeze({
    apiBaseUrl: override || DEFAULT_API_BASE_URL,
    baseUrlStorageKey: BASE_URL_STORAGE_KEY
  });
}

module.exports = {
  DEFAULT_API_BASE_URL,
  BASE_URL_STORAGE_KEY,
  normalizeBaseUrl,
  resolveEnv
};
