package com.ecsite.module.media.infrastructure;

import com.ecsite.module.media.service.ObjectStorage;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * ファイルの置き場所の S3 の実装（plan 001 第 7.3 節）。AWS の S3 と、S3 互換のストレージ（SeaweedFS）で動く。
 */
@Component
class S3ObjectStorage implements ObjectStorage {

    /**
     * 配信のファイルのキャッシュの指定。キーを上書きしないので、長くキャッシュさせる
     */
    static final String PUBLIC_CACHE_CONTROL = "public, max-age=31536000, immutable";

    /**
     * ファイルがないときの HTTP ステータス
     */
    private static final int NOT_FOUND = 404;

    /**
     * S3 のクライアント
     */
    private final S3Client s3;

    /**
     * 署名付き URL の発行に使うクライアント
     */
    private final S3Presigner presigner;

    /**
     * ファイルの置き場所の設定
     */
    private final MediaStorageProperties properties;

    /**
     * コンストラクタ
     *
     * @param s3         S3 のクライアント
     * @param presigner  署名付き URL の発行に使うクライアント
     * @param properties ファイルの置き場所の設定
     */
    S3ObjectStorage(S3Client s3, S3Presigner presigner, MediaStorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
    }

    /**
     * 受付のバケットへの署名付き URL を発行する。形式と大きさを署名に含める（AWS は、違う形式・大きさの PUT を拒否する）。
     */
    @Override
    public PresignedUpload presignUpload(String uploadKey, String contentType, long contentLength) {
        var presigned = presigner.presignPutObject(request -> request
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(put -> put
                        .bucket(properties.incomingBucket())
                        .key(uploadKey)
                        .contentType(contentType)
                        .contentLength(contentLength)));
        return new PresignedUpload(URI.create(presigned.url().toString()), Map.of("Content-Type", contentType),
                presigned.expiration());
    }

    @Override
    public Optional<Long> findUploadSize(String uploadKey) {
        try {
            return Optional.of(s3.headObject(request -> request.bucket(properties.incomingBucket()).key(uploadKey))
                    .contentLength());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            // HEAD の応答には本文がないので、ファイルがなくても NoSuchKey にならないことがある
            if (e.statusCode() == NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public byte[] readUploadHead(String uploadKey, int length) {
        return s3.getObjectAsBytes(request -> request
                .bucket(properties.incomingBucket())
                .key(uploadKey)
                .range("bytes=0-" + (length - 1))).asByteArray();
    }

    @Override
    public void putPublished(String publicKey, byte[] content, String contentType) {
        s3.putObject(request -> request
                        .bucket(properties.publicBucket())
                        .key(publicKey)
                        .contentType(contentType)
                        .cacheControl(PUBLIC_CACHE_CONTROL),
                RequestBody.fromBytes(content));
    }

    @Override
    public void deletePublished(String publicKey) {
        // S3 の削除は、ファイルがなくても成功する
        s3.deleteObject(request -> request.bucket(properties.publicBucket()).key(publicKey));
    }

    @Override
    public URI publicUrl(String publicKey) {
        return URI.create(properties.publicBaseUrl() + "/" + publicKey);
    }
}
