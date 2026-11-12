package com.yunshu.zhixue.integration.storage;

/**
 * 对象存储异常。适配器把厂商异常转换为本异常，业务层统一按 50004 处理；
 * 不向接口调用方泄露厂商错误码与堆栈。
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
