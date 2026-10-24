package com.ecsite.module.media.infrastructure.converter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * 画像の変換（imgproxy）の設定。環境変数で上書きする（例：ECSITE_MEDIA_CONVERTER_BASE_URL）。
 *
 * @param baseUrl imgproxy の URL（内部のネットワークだけで公開する）
 * @param key     URL の署名の鍵（16 進。imgproxy の IMGPROXY_KEY と同じ値）
 * @param salt    URL の署名のソルト（16 進。imgproxy の IMGPROXY_SALT と同じ値）
 * @param quality WebP の品質（1〜100）
 * @param timeout 変換を待つ時間の上限
 */
@Validated
@ConfigurationProperties("ecsite.media.converter")
public record ImgproxyProperties(
        @NotNull URI baseUrl,
        @NotBlank String key,
        @NotBlank String salt,
        @DefaultValue("82") @Min(1) @Max(100) int quality,
        @DefaultValue("30s") Duration timeout) {

    /**
     * 文字列表現を返す。署名の鍵とソルトは、ログに出さないため {@code ***} にする。
     *
     * @return 文字列表現
     */
    @Override
    public String toString() {
        return "ImgproxyProperties[baseUrl=" + baseUrl + ", key=***, salt=***, quality=" + quality
                + ", timeout=" + timeout + "]";
    }
}
