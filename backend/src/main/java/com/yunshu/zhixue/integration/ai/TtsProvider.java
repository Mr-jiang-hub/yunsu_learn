package com.yunshu.zhixue.integration.ai;

/**
 * 语音合成端口（P1 可选）。P0 只返回文字，不调用 TTS；失败时前端回退纯文本。
 */
public interface TtsProvider {

    byte[] synthesize(String text);
}
