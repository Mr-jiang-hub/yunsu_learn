package com.yunshu.zhixue.integration.storage;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObjectSummary;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 生产环境阿里云 OSS 适配器（私有 Bucket）。
 * 与 S3 适配器是独立实现：签名算法、URL 结构与 SDK 行为均不同（06 文档 4.4 / 5）。
 */
public class OssStorageAdapter implements ObjectStorage, AutoCloseable {

    private final OSS client;
    private final OSS publicClient;
    private final String bucket;

    public OssStorageAdapter(StorageProperties.Oss properties) {
        this.bucket = properties.bucket();
        this.client = new OSSClientBuilder().build(
                properties.endpoint(), properties.accessKey(), properties.secretKey());
        // 签名 URL 必须由绑定公网 endpoint 的客户端生成，否则 host 浏览器不可达
        this.publicClient = new OSSClientBuilder().build(
                properties.publicEndpoint(), properties.accessKey(), properties.secretKey());
    }

    @Override
    public void put(String key, InputStream stream, String contentType, long size) {
        StorageKeyValidator.validate(key);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(size);
            client.putObject(bucket, key, new NonClosingInputStream(stream), metadata);
        } catch (OSSException | ClientException ex) {
            throw new StorageException("写入对象失败", ex);
        }
    }

    @Override
    public void delete(String key) {
        StorageKeyValidator.validate(key);
        try {
            client.deleteObject(bucket, key);
        } catch (OSSException ex) {
            if (ex.getErrorCode() != null && "NoSuchKey".equals(ex.getErrorCode())) {
                return;
            }
            throw new StorageException("删除对象失败", ex);
        } catch (ClientException ex) {
            throw new StorageException("删除对象失败", ex);
        }
    }

    @Override
    public boolean exists(String key) {
        StorageKeyValidator.validate(key);
        try {
            return client.doesObjectExist(bucket, key);
        } catch (OSSException | ClientException ex) {
            throw new StorageException("查询对象失败", ex);
        }
    }

    @Override
    public String presignGet(String key, Duration ttl) {
        StorageKeyValidator.validate(key);
        try {
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(bucket, key, HttpMethod.GET);
            request.setExpiration(expireAt(ttl));
            return publicClient.generatePresignedUrl(request).toString();
        } catch (OSSException | ClientException ex) {
            throw new StorageException("生成下载地址失败", ex);
        }
    }

    @Override
    public String presignPut(String key, Duration ttl) {
        StorageKeyValidator.validate(key);
        try {
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(bucket, key, HttpMethod.PUT);
            request.setExpiration(expireAt(ttl));
            return publicClient.generatePresignedUrl(request).toString();
        } catch (OSSException | ClientException ex) {
            throw new StorageException("生成上传地址失败", ex);
        }
    }

    @Override
    public List<StoredObject> list(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("非法的对象前缀");
        }
        List<StoredObject> objects = new ArrayList<>();
        try {
            String marker = null;
            while (true) {
                ListObjectsRequest request = new ListObjectsRequest(bucket)
                        .withPrefix(prefix.isEmpty() ? null : prefix)
                        .withMarker(marker)
                        .withMaxKeys(1000);
                ObjectListing listing = client.listObjects(request);
                for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                    objects.add(new StoredObject(
                            summary.getKey(),
                            summary.getSize(),
                            summary.getLastModified() == null ? null : summary.getLastModified().toInstant()));
                }
                if (!listing.isTruncated()) {
                    break;
                }
                marker = listing.getNextMarker();
            }
        } catch (OSSException | ClientException ex) {
            throw new StorageException("列举对象失败", ex);
        }
        return objects;
    }

    private static Date expireAt(Duration ttl) {
        return Date.from(Instant.now().plus(ttl));
    }

    @Override
    public void close() {
        publicClient.shutdown();
        client.shutdown();
    }
}
