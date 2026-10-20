package com.ecsite.module.media.infrastructure.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * ファイルの置き場所の設定。環境変数で上書きする（例：ECSITE_MEDIA_STORAGE_PUBLIC_BUCKET）。
 *
 * @param endpoint        S3 のエンドポイント。エミュレーター（SeaweedFS）のときだけ指定し、AWS では空にする
 * @param region          リージョン
 * @param pathStyleAccess パス形式のアドレス（エミュレーターのときに true）
 * @param accessKeyId     アクセスキー。ローカル専用で、本番は空にして既定の資格情報の連鎖（IAM ロール）を使う
 * @param secretAccessKey シークレットキー。accessKeyId と一緒に指定する
 * @param incomingBucket  受付のバケット（非公開）
 * @param publicBucket    配信のバケット（公開）
 * @param publicBaseUrl   配信の基準 URL（本番は CDN）。ファイルの URL は「基準 URL/キー」
 * @param uploadUrlTtl    署名付き URL の有効期間
 * @param attemptTimeout  S3 の 1 回の試行を待つ時間の上限
 * @param callTimeout     S3 の呼び出しの全体（やり直しを含む）を待つ時間の上限
 */
@Validated
@ConfigurationProperties("ecsite.media.storage")
public record MediaStorageProperties(
        URI endpoint,
        @NotBlank String region,
        @DefaultValue("false") boolean pathStyleAccess,
        String accessKeyId,
        String secretAccessKey,
        @NotBlank String incomingBucket,
        @NotBlank String publicBucket,
        @NotNull URI publicBaseUrl,
        @DefaultValue("5m") Duration uploadUrlTtl,
        @DefaultValue("10s") Duration attemptTimeout,
        @DefaultValue("30s") Duration callTimeout) {

    /**
     * 署名付き URL の有効期間を変えた設定を返す（テストで期限切れを確かめるため）。
     *
     * @param uploadUrlTtl 署名付き URL の有効期間
     * @return 設定
     */
    public MediaStorageProperties withUploadUrlTtl(Duration uploadUrlTtl) {
        return new MediaStorageProperties(endpoint, region, pathStyleAccess, accessKeyId, secretAccessKey,
                incomingBucket, publicBucket, publicBaseUrl, uploadUrlTtl, attemptTimeout, callTimeout);
    }

    /**
     * S3 の宛先と待つ時間の上限を変えた設定を返す（テストで、応答しない S3 を確かめるため）。
     *
     * @param endpoint       S3 のエンドポイント
     * @param attemptTimeout 1 回の試行を待つ時間の上限
     * @param callTimeout    呼び出しの全体を待つ時間の上限
     * @return 設定
     */
    public MediaStorageProperties withEndpoint(URI endpoint, Duration attemptTimeout, Duration callTimeout) {
        return new MediaStorageProperties(endpoint, region, pathStyleAccess, accessKeyId, secretAccessKey,
                incomingBucket, publicBucket, publicBaseUrl, uploadUrlTtl, attemptTimeout, callTimeout);
    }

    /**
     * @return 資格情報を設定で与えているか（ローカル）
     */
    boolean hasStaticCredentials() {
        return accessKeyId != null && !accessKeyId.isBlank();
    }
}
