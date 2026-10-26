package com.ecsite.module.media.infrastructure.storage;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.media.application.port.ObjectStorage;
import com.ecsite.module.media.domain.MediaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * ファイルの置き場所の S3 の実装。AWS の S3 と、S3 互換のストレージ（SeaweedFS）で動く。
 */
@Component
@RequiredArgsConstructor
public class S3ObjectStorage implements ObjectStorage {

    /**
     * 配信のファイルのキャッシュの指定。キーを上書きしないので、長くキャッシュさせる
     */
    static final String PUBLIC_CACHE_CONTROL = "public, max-age=31536000, immutable";

    /**
     * ファイルがないときの HTTP ステータス
     */
    private static final int NOT_FOUND = 404;

    /**
     * S3 の側の故障の HTTP ステータスの下限
     */
    private static final int SERVER_ERROR = 500;

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
     * アップロードのバケットへの署名付き URL を発行する。形式と大きさを署名に含める（AWS は、違う形式・大きさの PUT を拒否する）。
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

    /**
     * アップロードのバケットのファイルの、S3 の連携での場所（imgproxy が変換元として読む）を返す。
     *
     * @param uploadKey アップロードのキー
     * @return {@code s3://<アップロードのバケット>/<キー>}
     */
    public String uploadSourceUrl(String uploadKey) {
        return "s3://" + properties.incomingBucket() + "/" + uploadKey;
    }

    /**
     * アップロードのバケットのファイルの大きさを返す。
     *
     * @param uploadKey アップロードのキー
     * @return 大きさ（バイト）。ファイルがなければ空
     */
    @Override
    public Optional<Long> findUploadSize(String uploadKey) {
        try {
            return Optional.of(call("受付の大きさを読めない：" + uploadKey,
                    () -> s3.headObject(request -> request.bucket(properties.incomingBucket()).key(uploadKey)))
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

    /**
     * アップロードのバケットのファイルの先頭を読む（形式の判定に使う）。
     *
     * @param uploadKey アップロードのキー
     * @param length    読む長さ（バイト）
     * @return 先頭のバイト。ファイルが length より短ければ、ファイルの全体
     */
    @Override
    public byte[] readUploadHead(String uploadKey, int length) {
        return call("受付の先頭を読めない：" + uploadKey, () -> s3.getObjectAsBytes(request -> request
                .bucket(properties.incomingBucket())
                .key(uploadKey)
                .range("bytes=0-" + (length - 1)))).asByteArray();
    }

    /**
     * 配信のバケットへファイルを書き込む。長くキャッシュさせる（キーを上書きしないため）。
     *
     * @param publicKey   配信のキー
     * @param content     中身
     * @param contentType 形式
     */
    @Override
    public void putPublished(String publicKey, byte[] content, String contentType) {
        call("配信のバケットに書けない：" + publicKey, () -> s3.putObject(request -> request
                        .bucket(properties.publicBucket())
                        .key(publicKey)
                        .contentType(contentType)
                        .cacheControl(PUBLIC_CACHE_CONTROL),
                RequestBody.fromBytes(content)));
    }

    /**
     * 配信のバケットのファイルを消す。ファイルがなくても成功とする（何度行っても同じ結果）。
     *
     * @param publicKey 配信のキー
     */
    @Override
    public void deletePublished(String publicKey) {
        // S3 の削除は、ファイルがなくても成功する
        call("配信のファイルを消せない：" + publicKey,
                () -> s3.deleteObject(request -> request.bucket(properties.publicBucket()).key(publicKey)));
    }

    /**
     * 配信のバケットのファイルの URL を返す。
     *
     * @param publicKey 配信のキー
     * @return 配信の URL
     */
    @Override
    public URI publicUrl(String publicKey) {
        return URI.create(properties.publicBaseUrl() + "/" + publicKey);
    }

    /**
     * S3 を呼ぶ。接続できない・時間内に応答がない（SDK の例外）と、S3 の側の故障（5xx）は、外部サービスのエラーにする。
     * 5xx でない S3 の失敗（権限がない、ファイルがないなど）は、そのまま投げる（呼ぶ側が扱うか、予期しないエラー）。
     */
    private <T> T call(String failure, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (S3Exception e) {
            if (e.statusCode() >= SERVER_ERROR) {
                throw SystemException.of("S3：" + failure + ", status=" + e.statusCode(), e,
                        MediaErrorCode.IMAGE_SERVICE_UNAVAILABLE);
            }
            throw e;
        } catch (SdkClientException e) {
            throw SystemException.of("S3：" + failure, e, MediaErrorCode.IMAGE_SERVICE_UNAVAILABLE);
        }
    }
}
