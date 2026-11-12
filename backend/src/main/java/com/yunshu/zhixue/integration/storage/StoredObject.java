package com.yunshu.zhixue.integration.storage;

import java.time.Instant;

/**
 * 对象存储里的一个对象（06 文档 2.2：端口不泄漏 OSS / S3 SDK 类型）。
 *
 * <p>只用于只读巡检，不作为业务读写契约。</p>
 *
 * @param key          对象 key
 * @param size         字节数
 * @param lastModified 最后修改时间（UTC）；存储未返回时为 null
 */
public record StoredObject(String key, long size, Instant lastModified) {
}
