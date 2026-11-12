package com.yunshu.zhixue.integration.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 开发环境 S3 适配器（MinIO）。实现要点见 06 文档第 4 章：
 * forcePathStyle、内部地址与对外签名地址分离、region 固定 us-east-1。
 */
public class S3StorageAdapter implements ObjectStorage, AutoCloseable {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3StorageAdapter(StorageProperties.S3 properties) {
        this.bucket = properties.bucket();
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        Region region = Region.of(properties.region());
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyle())
                .build();

        // 内部读写走 endpoint；签名 URL 必须走浏览器可达的 public-endpoint，二者不能复用
        this.client = S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(region)
                .credentialsProvider(credentials)
                .forcePathStyle(properties.pathStyle())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(properties.publicEndpoint()))
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    @Override
    public void put(String key, InputStream stream, String contentType, long size) {
        StorageKeyValidator.validate(key);
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(size)
                            .build(),
                    RequestBody.fromInputStream(new NonClosingInputStream(stream), size));
        } catch (SdkException ex) {
            throw new StorageException("写入对象失败", ex);
        }
    }

    @Override
    public void delete(String key) {
        StorageKeyValidator.validate(key);
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return;
            }
            throw new StorageException("删除对象失败", ex);
        } catch (SdkException ex) {
            throw new StorageException("删除对象失败", ex);
        }
    }

    @Override
    public boolean exists(String key) {
        StorageKeyValidator.validate(key);
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw new StorageException("查询对象失败", ex);
        } catch (SdkException ex) {
            throw new StorageException("查询对象失败", ex);
        }
    }

    @Override
    public String presignGet(String key, Duration ttl) {
        StorageKeyValidator.validate(key);
        try {
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build();
            return presigner.presignGetObject(request).url().toString();
        } catch (SdkException ex) {
            throw new StorageException("生成下载地址失败", ex);
        }
    }

    @Override
    public String presignPut(String key, Duration ttl) {
        StorageKeyValidator.validate(key);
        try {
            PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .putObjectRequest(PutObjectRequest.builder().bucket(bucket).key(key).build())
                    .build();
            return presigner.presignPutObject(request).url().toString();
        } catch (SdkException ex) {
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
            ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder().bucket(bucket);
            if (!prefix.isEmpty()) {
                builder.prefix(prefix);
            }
            ListObjectsV2Request request = builder.build();
            while (true) {
                ListObjectsV2Response response = client.listObjectsV2(request);
                response.contents().forEach(item -> objects.add(new StoredObject(
                        item.key(),
                        item.size() == null ? 0L : item.size(),
                        item.lastModified())));
                if (Boolean.FALSE.equals(response.isTruncated())) {
                    break;
                }
                request = builder.continuationToken(response.nextContinuationToken()).build();
            }
        } catch (SdkException ex) {
            throw new StorageException("列举对象失败", ex);
        }
        return objects;
    }

    @Override
    public void close() {
        presigner.close();
        client.close();
    }
}
