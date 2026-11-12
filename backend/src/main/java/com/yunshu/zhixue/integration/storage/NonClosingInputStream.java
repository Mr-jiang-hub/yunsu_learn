package com.yunshu.zhixue.integration.storage;

import java.io.FilterInputStream;
import java.io.InputStream;

/**
 * 阻止 SDK 关闭调用方传入的流，满足 ObjectStorage#put 的资源约束。
 */
final class NonClosingInputStream extends FilterInputStream {

    NonClosingInputStream(InputStream in) {
        super(in);
    }

    @Override
    public void close() {
        // 由调用方负责关闭
    }
}
