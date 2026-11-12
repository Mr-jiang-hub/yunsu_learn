package com.yunshu.zhixue.common.storage;

import com.yunshu.zhixue.integration.storage.ObjectStorage;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 封面地址：数据库存 key，接口返回短时签名 URL（api-rules §12.2）。
 * 已是完整 URL 的历史数据原样返回；签名失败降级为空，不影响列表可用。
 *
 * <p>C 端与 B 端共用，故放在 common 下。</p>
 */
@Component
public class CoverUrlResolver {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final ObjectStorage objectStorage;

    public CoverUrlResolver(ObjectStorage objectStorage) {
        this.objectStorage = objectStorage;
    }

    public String resolve(String coverKey) {
        if (coverKey == null || coverKey.isBlank()) {
            return "";
        }
        if (coverKey.startsWith("http://") || coverKey.startsWith("https://")) {
            return coverKey;
        }
        try {
            return objectStorage.presignGet(coverKey, TTL);
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
