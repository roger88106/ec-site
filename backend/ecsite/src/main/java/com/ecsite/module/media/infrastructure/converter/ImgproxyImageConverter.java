package com.ecsite.module.media.infrastructure.converter;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.media.application.port.ImageConverter;
import com.ecsite.module.media.domain.ImageFormat;
import com.ecsite.module.media.domain.MediaErrorCode;
import com.ecsite.module.media.infrastructure.storage.MediaStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 画像の変換の imgproxy の実装。<br />
 * imgproxy は受付のバケットを S3 の連携で直接読み、変換した WebP を返す。向きの反映とメタデータの除去は、
 * imgproxy の設定（IMGPROXY_AUTO_ROTATE、IMGPROXY_STRIP_METADATA）で行う。URL には署名を付ける。
 */
@Slf4j
@Component
class ImgproxyImageConverter implements ImageConverter {

    /**
     * imgproxy への接続を待つ時間の上限（変換を待つ時間とは別）
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * imgproxy に届いていないことを示す HTTP の状態（imgproxy の手前の代理が返す）
     */
    private static final Set<Integer> UNREACHABLE_STATUSES = Set.of(502, 503, 504);

    /**
     * URL に入れる base64（URL セーフ、パディングなし。imgproxy の決まり）
     */
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    /**
     * imgproxy を呼ぶ HTTP クライアント（基準 URL と時間の上限を設定済み）
     */
    private final RestClient restClient;

    /**
     * 画像の変換の設定
     */
    private final ImgproxyProperties properties;

    /**
     * ファイルの置き場所の設定（受付のバケットの名前）
     */
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
     * imgproxy に変換させる。imgproxy が応答したエラー（4xx と、壊れたファイルに返す 500）は、変換の失敗にする。
     * imgproxy の中の故障（受付のバケットを読めないなど）も 500 なので区別できないため、応答をログに残す。
     * imgproxy に届かない・応えない（接続できない、時間切れ、手前の代理の 502・503・504）は、外部サービスのエラーにする。<br />
     * 配信する画像はすべて WebP にするので、応答の本文が WebP でなければ（先頭のバイトで判定し、Content-Type には
     * 頼らない）、変換の失敗にする。
     */
    @Override
    public byte[] convertToWebp(String uploadKey) {
        var path = processingPath(uploadKey);
        try {
            byte[] body = restClient.get().uri(signature(path) + path).retrieve().body(byte[].class);
            if (body == null || ImageFormat.detect(body).filter(ImageFormat.WEBP::equals).isEmpty()) {
                log.warn("imgproxy の応答が WebP でない：uploadKey={}, bytes={}", uploadKey, body == null ? 0 : body.length);
                throw BusinessException.of(MediaErrorCode.IMAGE_CONVERSION_FAILED);
            }
            return body;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (UNREACHABLE_STATUSES.contains(status)) {
                throw SystemException.of("imgproxy に届かない：uploadKey=" + uploadKey + ", status=" + status, e,
                        MediaErrorCode.IMAGE_SERVICE_UNAVAILABLE);
            }
            log.warn("imgproxy が画像を変換できなかった：uploadKey={}, status={}, body={}",
                    uploadKey, status, e.getResponseBodyAsString());
            throw BusinessException.of(MediaErrorCode.IMAGE_CONVERSION_FAILED);
        } catch (ResourceAccessException e) {
            var reason = e.getCause() instanceof HttpTimeoutException
                    ? "imgproxy の変換が時間の上限（" + properties.timeout() + "）を超えた"
                    : "imgproxy に接続できない";
            throw SystemException.of(reason + "：uploadKey=" + uploadKey, e, MediaErrorCode.IMAGE_SERVICE_UNAVAILABLE);
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
