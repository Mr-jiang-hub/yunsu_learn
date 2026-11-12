package com.yunshu.zhixue.integration.ai;

import java.time.Duration;

/**
 * 语音识别端口。音频只在内存/流中经过，不落盘、不写 OSS（02 §5.1）。
 */
public interface AsrProvider {

    AsrResponse transcribe(byte[] audio, String contentType, Duration audioDuration);
}
