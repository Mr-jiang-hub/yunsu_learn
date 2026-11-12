package com.yunshu.zhixue.common.upload;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.upload.dto.UploadResponse;
import com.yunshu.zhixue.common.util.TimeFormats;
import com.yunshu.zhixue.integration.storage.ObjectStorage;
import com.yunshu.zhixue.integration.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 图片上传（04 §6.9 / rules §12）：只收 jpeg/png/webp，≤2MB、≤4096×4096。
 *
 * <p>校验顺序固定为 空文件 → 大小 → 后缀/MIME → 魔数 → 尺寸，
 * 任何一步失败都在写对象存储之前返回，不产生孤儿文件。
 * 数据库只存服务端生成的 key，返回的 url 是短时签名地址。</p>
 */
@Service
public class ImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final Duration TTL = Duration.ofMinutes(30);

    private final ObjectStorage objectStorage;

    public ImageUploadService(ObjectStorage objectStorage) {
        this.objectStorage = objectStorage;
    }

    /** `purpose=AVATAR` 的头像上传 */
    public UploadResponse uploadAvatar(MultipartFile file) {
        return upload(file, "avatar");
    }

    /**
     * 通用图片上传：`prefix` 由服务端按业务固定给出（avatar / subject / course），
     * 不接受客户端传入，避免越权写到别人的前缀下。
     * 前缀范围与库里能承接 key 的列一一对应（04-Q47：无列可绑定即孤儿文件）。
     */
    public UploadResponse upload(MultipartFile file, String prefix) {
        byte[] bytes = readBytes(file);
        Format format = detect(bytes);
        int[] size = dimensions(bytes, format);
        if (size != null && (size[0] > MAX_DIMENSION || size[1] > MAX_DIMENSION)) {
            throw new BizException(ErrorCode.IMAGE_DIMENSION_EXCEEDED);
        }

        String key = prefix + "/" + LocalDate.now(ZoneOffset.UTC) + "/" + UUID.randomUUID() + "."
                + format.extension();
        try {
            objectStorage.put(key, new ByteArrayInputStream(bytes), format.contentType(), bytes.length);
            String url = objectStorage.presignGet(key, TTL);
            log.info("图片上传成功: key={}, bytes={}, size={}x{}", key, bytes.length,
                    size == null ? -1 : size[0], size == null ? -1 : size[1]);
            return new UploadResponse(key, url, TimeFormats.rfc3339(Instant.now().plus(TTL)));
        } catch (StorageException ex) {
            log.warn("图片写存储失败: key={}", key, ex);
            throw new BizException(ErrorCode.STORAGE_UNAVAILABLE);
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.UPLOAD_EMPTY);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException(ErrorCode.IMAGE_TOO_LARGE);
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw new BizException(ErrorCode.UPLOAD_EMPTY);
            }
            return bytes;
        } catch (IOException ex) {
            throw new BizException(ErrorCode.UPLOAD_EMPTY);
        }
    }

    /** 以魔数为准判断真实格式，不信客户端上报的 MIME（rules §12.1） */
    private static Format detect(byte[] bytes) {
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return Format.JPEG;
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Format.PNG;
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return Format.WEBP;
        }
        throw new BizException(ErrorCode.IMAGE_FORMAT_UNSUPPORTED);
    }

    /** 解析宽高；无法解析时返回 null（不因解析失败拒绝合法图片） */
    private static int[] dimensions(byte[] bytes, Format format) {
        return switch (format) {
            case PNG -> pngSize(bytes);
            case JPEG -> jpegSize(bytes);
            case WEBP -> webpSize(bytes);
        };
    }

    private static int[] pngSize(byte[] bytes) {
        if (bytes.length < 24) {
            return null;
        }
        int width = readInt(bytes, 16);
        int height = readInt(bytes, 20);
        return width > 0 && height > 0 ? new int[]{width, height} : null;
    }

    private static int[] jpegSize(byte[] bytes) {
        int index = 2;
        while (index + 9 < bytes.length) {
            if ((bytes[index] & 0xFF) != 0xFF) {
                index++;
                continue;
            }
            int marker = bytes[index + 1] & 0xFF;
            // SOF0-SOF15，排除 DHT(C4)/JPG(C8)/DAC(CC)
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                int height = ((bytes[index + 5] & 0xFF) << 8) | (bytes[index + 6] & 0xFF);
                int width = ((bytes[index + 7] & 0xFF) << 8) | (bytes[index + 8] & 0xFF);
                return width > 0 && height > 0 ? new int[]{width, height} : null;
            }
            int length = ((bytes[index + 2] & 0xFF) << 8) | (bytes[index + 3] & 0xFF);
            if (length <= 0) {
                return null;
            }
            index += 2 + length;
        }
        return null;
    }

    private static int[] webpSize(byte[] bytes) {
        if (bytes.length < 30) {
            return null;
        }
        String chunk = new String(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]},
                java.nio.charset.StandardCharsets.US_ASCII);
        if ("VP8X".equals(chunk)) {
            // 24 位小端 +1
            int width = ((bytes[24] & 0xFF) | ((bytes[25] & 0xFF) << 8) | ((bytes[26] & 0xFF) << 16)) + 1;
            int height = ((bytes[27] & 0xFF) | ((bytes[28] & 0xFF) << 8) | ((bytes[29] & 0xFF) << 16)) + 1;
            return new int[]{width, height};
        }
        if ("VP8 ".equals(chunk) && bytes.length >= 30) {
            int width = (((bytes[27] & 0xFF) << 8) | (bytes[26] & 0xFF)) & 0x3FFF;
            int height = (((bytes[29] & 0xFF) << 8) | (bytes[28] & 0xFF)) & 0x3FFF;
            return width > 0 && height > 0 ? new int[]{width, height} : null;
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    /** 允许的图片格式；扩展名与服务端 MIME 都由魔数决定，不取客户端值 */
    private enum Format {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp");

        private final String extension;
        private final String contentType;

        Format(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        String extension() {
            return extension;
        }

        String contentType() {
            return contentType;
        }
    }
}
