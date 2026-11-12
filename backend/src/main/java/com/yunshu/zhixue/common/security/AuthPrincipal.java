package com.yunshu.zhixue.common.security;

/**
 * 登录主体：id 与 audience 均来自服务端签发的 token，不信任前端传入。
 */
public record AuthPrincipal(long id, Audience audience) {
}
