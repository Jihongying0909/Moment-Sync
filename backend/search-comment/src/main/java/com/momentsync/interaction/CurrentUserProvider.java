package com.momentsync.interaction;

/** 由公共鉴权模块实现：仅从已验证的 JWT/security context 返回 ACTIVE 用户 ID。
 * 禁止直接信任请求体、查询参数或未经验证的 header；未登录/过期须抛出 401。 */
@FunctionalInterface
public interface CurrentUserProvider {
    long requireUserId();
}
