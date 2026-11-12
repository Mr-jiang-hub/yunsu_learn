package com.yunshu.zhixue.common.upload.dto;

/** 上传结果（04 §6.9）：数据库只存 `key`，`url` 为短时签名地址，前端不得缓存。 */
public record UploadResponse(String key, String url, String expireAt) {
}
