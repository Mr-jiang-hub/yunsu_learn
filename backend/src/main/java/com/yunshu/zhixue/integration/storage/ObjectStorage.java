package com.yunshu.zhixue.integration.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

/**
 * 协议无关的对象存储端口。业务层只依赖本接口，
 * 方法签名不得出现 OSS / S3 SDK 类型（06 文档 2.2）。
 */
public interface ObjectStorage {

    /** 写入对象；调用方负责生成 key、校验 MIME/魔数并传入准确大小。不关闭传入流。 */
    void put(String key, InputStream stream, String contentType, long size);

    /** 删除对象；对象不存在视为成功（幂等）。 */
    void delete(String key);

    /** 判断对象是否存在。 */
    boolean exists(String key);

    /** 生成短时下载地址；host 必须是浏览器可达的对外地址。 */
    String presignGet(String key, Duration ttl);

    /** 生成短时上传地址；仅用于开发期 Q34 验证或后续直传方案。 */
    String presignPut(String key, Duration ttl);

    /**
     * 列出指定前缀下的全部对象（只读巡检用，内部按存储分页拉取）。
     *
     * <p>调用方负责把前缀收窄到业务前缀；本方法不做截断，桶内对象极多时应由调用方限流。
     * 前缀为空串表示列全桶。</p>
     */
    List<StoredObject> list(String prefix);
}
