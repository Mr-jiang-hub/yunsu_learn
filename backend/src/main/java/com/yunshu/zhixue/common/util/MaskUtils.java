package com.yunshu.zhixue.common.util;

/**
 * 日志与异常信息脱敏工具：手机号不得以明文进入日志（database-rules 第 11 章）。
 */
public final class MaskUtils {

    private MaskUtils() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
