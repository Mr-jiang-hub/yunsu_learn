package com.yunshu.zhixue.common.crypto;

import com.yunshu.zhixue.common.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 手机号加密与盲索引（02-Q18）：AES-GCM 加密、HMAC-SHA256 盲索引。
 * 密钥来自环境变量（dev 默认值仅限本地开发）；注销时密文置 NULL 释放手机号。
 */
@Component
public class PhoneCrypto {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int GCM_IV_LENGTH = 12;

    private final SecretKeySpec aesKey;
    private final byte[] hmacKey;

    public PhoneCrypto(AppProperties properties) {
        byte[] aesBytes = Base64.getDecoder().decode(properties.security().phoneAesKey());
        if (aesBytes.length != 32) {
            throw new IllegalStateException("app.security.phone-aes-key 解码后必须为 32 字节");
        }
        this.aesKey = new SecretKeySpec(aesBytes, "AES");
        byte[] hmacBytes = Base64.getDecoder().decode(properties.security().phoneHmacKey());
        if (hmacBytes.length != 32) {
            throw new IllegalStateException("app.security.phone-hmac-key 解码后必须为 32 字节");
        }
        this.hmacKey = hmacBytes;
    }

    /** AES-GCM 加密，密文格式 = IV(12 字节) + 密文+Tag */
    public byte[] encrypt(String plainPhone) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainPhone.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return out;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("手机号加密失败", ex);
        }
    }

    /** 解密 phone_cipher 列的密文 */
    public byte[] decrypt(byte[] cipherWithIv) {
        try {
            byte[] iv = java.util.Arrays.copyOfRange(cipherWithIv, 0, GCM_IV_LENGTH);
            byte[] encrypted = java.util.Arrays.copyOfRange(cipherWithIv, GCM_IV_LENGTH, cipherWithIv.length);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
            return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("手机号解密失败", ex);
        }
    }

    /** HMAC-SHA256 盲索引，返回 64 位小写 hex（对应 phone_hash CHAR(64)） */
    public String hmacHex(String plainPhone) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(hmacKey, HMAC_SHA256));
            byte[] digest = mac.doFinal(plainPhone.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("手机号盲索引计算失败", ex);
        }
    }
}
