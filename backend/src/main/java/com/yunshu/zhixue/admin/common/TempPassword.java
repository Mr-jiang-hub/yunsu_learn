package com.yunshu.zhixue.admin.common;

import java.security.SecureRandom;

/**
 * 后台重置密码用的一次性临时密码生成器。
 *
 * <p>12 位，去掉易混淆字符（0/O/1/l/I），仅用于响应体返回一次，
 * 库中只存 bcrypt 哈希并置 password_reset_required=1。</p>
 */
public final class TempPassword {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TempPassword() {
    }

    public static String generate() {
        StringBuilder builder = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
