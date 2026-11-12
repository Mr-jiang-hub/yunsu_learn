package com.yunshu.zhixue.integration.sms;

/**
 * 验证码使用场景，对应 PRD 5.1 / S-05 的统一短信能力。
 */
public enum SmsScene {
    REGISTER,
    LOGIN,
    RESET_PASSWORD,
    CHANGE_PHONE,
    CANCEL_ACCOUNT
}
