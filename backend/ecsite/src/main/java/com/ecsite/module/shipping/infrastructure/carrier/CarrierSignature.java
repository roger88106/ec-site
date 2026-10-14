package com.ecsite.module.shipping.infrastructure.carrier;

import com.ecsite.common.error.exception.SystemException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 物流サービスの通知の署名（docs/backend/modules/shipping.md「通知の受け口」）。<br />
 * ヘッダーの値は {@code sha256=} と、本文のバイト列の HMAC-SHA256（16 進、小文字）。
 */
public final class CarrierSignature {

    /**
     * 署名を載せるヘッダーの名前
     */
    public static final String HEADER = "X-Carrier-Signature";

    /**
     * ヘッダーの値の接頭辞
     */
    private static final String PREFIX = "sha256=";

    /**
     * HMAC の方式
     */
    private static final String ALGORITHM = "HmacSHA256";

    /**
     * 共有の鍵
     */
    private final SecretKeySpec key;

    /**
     * @param secret 共有の鍵
     */
    public CarrierSignature(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /**
     * 本文に署名して、ヘッダーの値を作る。
     *
     * @param body 本文
     * @return ヘッダーの値
     */
    public String sign(byte[] body) {
        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return PREFIX + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw SystemException.of("通知の署名を作れない", e);
        }
    }

    /**
     * ヘッダーの値が、本文の署名と合うかを確かめる。比べる時間が値で変わらないようにする。
     *
     * @param body   本文
     * @param header ヘッダーの値。なければ null
     * @return 合えば true
     */
    public boolean verify(byte[] body, String header) {
        if (header == null) {
            return false;
        }
        var expected = sign(body).getBytes(StandardCharsets.UTF_8);
        var actual = header.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
