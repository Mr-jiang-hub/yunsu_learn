package com.yunshu.zhixue.common.security;

/**
 * 每次鉴权校验账号状态（api-rules 8.3）：不能只信任 token 内嵌信息。
 */
public interface AccountStatusVerifier {

    void verify(AuthPrincipal principal);
}
