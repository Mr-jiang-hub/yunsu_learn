package com.yunshu.zhixue.student.auth;

import com.yunshu.zhixue.integration.sms.SmsScene;

/**
 * 验证码用途，对应 sms_code.purpose（V1 注释：1注册 2验证码登录 3重置密码 4换绑手机号 5注销确认；
 * 6 为评审缺口 G-02 新增的"已登录改密"）。CHANGE_PHONE_OLD/NEW 共用 4。
 *
 * <p>改密单独占 6，不与 RESET_PASSWORD(3) 共用：找回密码是未登录场景、
 * 改密是已登录场景，共用一个 purpose 会让两种验证码互相可复用。</p>
 */
public enum SmsPurpose {

    REGISTER(1, SmsScene.REGISTER),
    LOGIN(2, SmsScene.LOGIN),
    RESET_PASSWORD(3, SmsScene.RESET_PASSWORD),
    CHANGE_PHONE_OLD(4, SmsScene.CHANGE_PHONE),
    CHANGE_PHONE_NEW(4, SmsScene.CHANGE_PHONE),
    CANCEL(5, SmsScene.CANCEL_ACCOUNT),
    CHANGE_PASSWORD(6, SmsScene.RESET_PASSWORD);

    private final int dbValue;
    private final SmsScene scene;

    SmsPurpose(int dbValue, SmsScene scene) {
        this.dbValue = dbValue;
        this.scene = scene;
    }

    public int dbValue() {
        return dbValue;
    }

    public SmsScene scene() {
        return scene;
    }
}
