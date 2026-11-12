package com.yunshu.zhixue.student.profile.dto;

/** 换绑结果（04 §6.9）；成功后必须重新登录。 */
public record PhoneChangeResponse(String phoneMasked, String changedAt) {
}
