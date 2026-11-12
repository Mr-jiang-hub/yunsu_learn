package com.yunshu.zhixue.integration.sms;

/**
 * 短信发送端口。频控与每日上限由数据库原子计数在业务层完成，
 * 适配器只负责调用厂商网关。
 */
public interface SmsSender {

    void sendVerificationCode(String phone, String code, SmsScene scene);
}
