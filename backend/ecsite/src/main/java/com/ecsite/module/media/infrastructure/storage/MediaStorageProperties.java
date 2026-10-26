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
 * @param incomingBucket  アップロードのバケット（非公開）
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
     * 資格情報を設定で与えているかを返す。
     *
     * @return 資格情報を設定で与えているか（ローカル）
     */
    boolean hasStaticCredentials() {
        return accessKeyId != null && !accessKeyId.isBlank();
    }

    /**
     * 文字列表現を返す。アクセスキー ID と秘密の鍵は、ログに出さないため {@code ***} にする。<br />
     * 本番は資格情報を設定しない（null）ので、設定していないことが分かるように null はそのまま出す。
     *
     * @return 文字列表現
     */
    @Override
    public String toString() {
        return "MediaStorageProperties[endpoint=" + endpoint + ", region=" + region
                + ", pathStyleAccess=" + pathStyleAccess
                + ", accessKeyId=" + (accessKeyId == null ? null : "***")
                + ", secretAccessKey=" + (secretAccessKey == null ? null : "***")
                + ", incomingBucket=" + incomingBucket + ", publicBucket=" + publicBucket
                + ", publicBaseUrl=" + publicBaseUrl + ", uploadUrlTtl=" + uploadUrlTtl
                + ", attemptTimeout=" + attemptTimeout + ", callTimeout=" + callTimeout + "]";
    }
}
