package com.ecsite.module.media.infrastructure.converter;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.media.application.port.ImageConverter;
import com.ecsite.module.media.domain.ImageFormat;
import com.ecsite.module.media.domain.MediaErrorCode;
import com.ecsite.module.media.infrastructure.storage.MediaStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * imgproxy の画像の変換
 *
 * <p>imgproxy はアップロードのバケットを S3 の連携で直接読み、変換した WebP を返す。向きの反映とメタデータの除去は、
 * imgproxy の設定（IMGPROXY_AUTO_ROTATE、IMGPROXY_STRIP_METADATA）で行う。URL には署名を付ける。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ImgproxyImageConverter implements ImageConverter {

    /**
     * imgproxy に届いていないことを示す HTTP の状態（imgproxy の手前の代理が返す）
     */
    private static final Set<Integer> UNREACHABLE_STATUSES = Set.of(502, 503, 504);

    /**
     * URL に入れる base64（URL セーフ、パディングなし。imgproxy の決まり）
     */
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    /**
     * imgproxy を呼ぶ HTTP クライアント
     */
    @Qualifier(ImgproxyConfig.REST_CLIENT)
    private final RestClient restClient;

    /**
     * 画像の変換の設定
     */
    private final ImgproxyProperties properties;

    /**
     * ファイルの置き場所の設定（変換元のアップロードのバケット）
     */
    private final MediaStorageProperties storageProperties;

    /**
     * imgproxy に WebP へ変換させる。
     *
     * @param uploadKey アップロードのバケットのキー
     * @return 変換した WebP
     * @throws BusinessException imgproxy が変換できなかった、応答が WebP でない
     * @throws SystemException   imgproxy に接続できない、届かない、時間の上限を超えた
     */
    @Override
    public byte[] convertToWebp(String uploadKey) {
        var path = processingPath(uploadKey);
        try {
            var body = restClient.get().uri(signature(path) + path).retrieve().body(byte[].class);
            // 配信する画像は全て WebP にするので、本文の先頭のバイトで確認する（Content-Type には頼らない）
            if (body == null || ImageFormat.detect(body).filter(ImageFormat.WEBP::equals).isEmpty()) {
                log.warn("imgproxy の応答が WebP でない：uploadKey={}, bytes={}", uploadKey, body == null ? 0 : body.length);
                throw BusinessException.of(MediaErrorCode.IMAGE_CONVERSION_FAILED);
            }
            return body;
        } catch (RestClientResponseException e) {
            var status = e.getStatusCode().value();
            // 手前の代理の 502・503・504 は、imgproxy に届いていない
            if (UNREACHABLE_STATUSES.contains(status)) {
                throw SystemException.of("imgproxy に届かない：uploadKey=" + uploadKey + ", status=" + status, e,
                        MediaErrorCode.IMAGE_SERVICE_UNAVAILABLE);
            }
            // imgproxy が応答したエラー（4xx と、壊れたファイルに返す 500）は変換の失敗にする。
            // imgproxy の中の故障（アップロードのバケットを読めないなど）も 500 で区別できないので、応答をログに残す
            log.warn("imgproxy が画像を変換できなかった：uploadKey={}, status={}, body={}",
                    uploadKey, status, e.getResponseBodyAsString());
            throw BusinessException.of(MediaErrorCode.IMAGE_CONVERSION_FAILED);
        } catch (ResourceAccessException e) {
            // 接続できない・時間の上限を超えた（imgproxy が応えない）
            var reason = e.getCause() instanceof HttpTimeoutException
                    ? "imgproxy の変換が時間の上限（" + properties.timeout() + "）を超えた"
                    : "imgproxy に接続できない";
            throw SystemException.of(reason + "：uploadKey=" + uploadKey, e, MediaErrorCode.IMAGE_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * アップロードのバケットのファイルの、S3 の連携での場所（imgproxy が変換元として読む）を返す。
     *
     * @param uploadKey アップロードのキー
     * @return {@code s3://<アップロードのバケット>/<キー>}
     */
    String uploadSourceUrl(String uploadKey) {
        return "s3://" + storageProperties.incomingBucket() + "/" + uploadKey;
    }

// ============================== プライベートメソッド ==============================

    /**
     * 処理の指定と元のファイルの URL（base64url）。
     *
     * <p>/q:{品質}/f:webp/{元の URL}
     */
    private String processingPath(String uploadKey) {
        var source = uploadSourceUrl(uploadKey);
        return "/q:" + properties.quality() + "/f:webp/" + BASE64_URL.encodeToString(source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * URL の署名。
     *
     * <p>base64url(HMAC-SHA256(鍵, ソルト + パス))
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
