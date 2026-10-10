package com.ecsite.module.media.infrastructure;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.media.service.ImageConverter;
import com.ecsite.module.media.service.MediaErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 画像の変換の imgproxy の実装（plan 001 第 7.5 節）。<br />
 * imgproxy は受付のバケットを S3 の連携で直接読み、変換した WebP を返す。向きの反映とメタデータの除去は、
 * imgproxy の設定（IMGPROXY_AUTO_ROTATE、IMGPROXY_STRIP_METADATA）で行う。URL には署名を付ける。
 */
@Slf4j
@Component
@EnableConfigurationProperties(ImgproxyProperties.class)
class ImgproxyImageConverter implements ImageConverter {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final RestClient restClient;

    private final ImgproxyProperties properties;

    private final MediaStorageProperties storageProperties;

    /**
     * コンストラクタ
     *
     * @param restClientBuilder HTTP クライアントのビルダー（トレースとメトリクスが付く）
     * @param properties        画像の変換の設定
     * @param storageProperties ファイルの置き場所の設定（受付のバケット）
     */
    ImgproxyImageConverter(RestClient.Builder restClientBuilder, ImgproxyProperties properties,
                           MediaStorageProperties storageProperties) {
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl().toString()).requestFactory(requestFactory).build();
        this.properties = properties;
        this.storageProperties = storageProperties;
    }

    /**
     * imgproxy に変換させる。imgproxy がエラーを返したとき（壊れたファイルには 500 を返すこともある）と、時間切れは、
     * 変換の失敗にする。応答だけではストレージの不具合と区別できないため、応答をログに残す。
     */
    @Override
    public byte[] convertToWebp(String uploadKey) {
        var path = processingPath(uploadKey);
        try {
            return restClient.get().uri(signature(path) + path).retrieve().body(byte[].class);
        } catch (RestClientResponseException e) {
            log.warn("imgproxy が画像を変換できなかった：uploadKey={}, status={}, body={}",
                    uploadKey, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw BusinessException.of(MediaErrorCode.IMAGE_CONVERSION_FAILED);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof HttpTimeoutException) {
                log.warn("imgproxy の変換が時間の上限を超えた：uploadKey={}, timeout={}", uploadKey, properties.timeout());
                throw BusinessException.of(MediaErrorCode.IMAGE_CONVERSION_FAILED);
            }
            throw SystemException.of("imgproxy に接続できない", e, CommonErrorCode.SYSTEM_ERROR);
        }
    }

// ============================== プライベートメソッド ==============================

    /**
     * 処理の指定と元のファイルの URL（base64url）。/q:{品質}/f:webp/{元の URL}
     */
    private String processingPath(String uploadKey) {
        var source = "s3://" + storageProperties.incomingBucket() + "/" + uploadKey;
        return "/q:" + properties.quality() + "/f:webp/" + BASE64_URL.encodeToString(source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * URL の署名。base64url(HMAC-SHA256(鍵, ソルト + パス))
     */
    private String signature(String path) {
        try {
            var hex = HexFormat.of();
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hex.parseHex(properties.key()), "HmacSHA256"));
            mac.update(hex.parseHex(properties.salt()));
            mac.update(path.getBytes(StandardCharsets.UTF_8));
            return "/" + BASE64_URL.encodeToString(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw SystemException.of("imgproxy の URL に署名できない", e, CommonErrorCode.SYSTEM_ERROR);
        }
    }
}
