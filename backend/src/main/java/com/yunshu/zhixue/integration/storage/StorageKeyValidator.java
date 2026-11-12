package com.yunshu.zhixue.integration.storage;

import java.util.regex.Pattern;

/**
 * 对象 key 防御性校验：key 由服务端生成，客户端不得传入完整路径（06 文档 7.4）。
 */
final class StorageKeyValidator {

    private static final Pattern SAFE_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]{0,511}$");

    private StorageKeyValidator() {
    }

    static void validate(String key) {
        if (key == null || key.isBlank()
                || key.contains("..")
                || key.startsWith("/")
                || key.contains("\\")
                || !SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("非法的对象 key");
        }
    }
}
