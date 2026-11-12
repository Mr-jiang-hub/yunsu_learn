package com.yunshu.zhixue.common.security;

/**
 * C/B 双端 audience：token 不可跨端使用（api-rules 第 8 章）。
 */
public enum Audience {
    C("c", "ROLE_C"),
    ADMIN("admin", "ROLE_ADMIN");

    private final String claimValue;
    private final String authority;

    Audience(String claimValue, String authority) {
        this.claimValue = claimValue;
        this.authority = authority;
    }

    public String claimValue() {
        return claimValue;
    }

    public String authority() {
        return authority;
    }
}
