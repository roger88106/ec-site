package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.infrastructure.carrier.CarrierNotification;
import com.ecsite.module.shipping.infrastructure.carrier.CarrierSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * mock の物流サービス（docs/backend/modules/shipping.md「mock」）。<br />
 * 署名付きの通知を作り、HTTP で通知の受け口に送る。本物の物流サービスと同じ道（署名の検証を含む）を通す。
 * mock の画面のボタンと、テストのコードが使う。通知の重複は同じ通知を重ねて送り、順序の入れ替わりと途中の欠けは
 * 送る順序と内容を選び、署名の誤りと合わない送り状番号は、通知を作る口で選べる。
 */
@Component
@ConditionalOnProperty(name = "ecsite.shipping.carrier", havingValue = "mock", matchIfMissing = true)
public class MockCarrier {

    /**
     * 通知の受け口のパス
     */
    private static final String WEBHOOK_PATH = "/api/webhooks/carrier";

    /**
     * 受け口の応答を待つ時間の上限
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /**
     * 署名
     */
    private final CarrierSignature signature;

    /**
     * JSON の書き出し
     */
    private final JsonMapper jsonMapper;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * HTTP の呼び出し
     */
    private final RestClient restClient;

    /**
     * @param signature  署名
     * @param jsonMapper JSON の書き出し
     * @param clock      時計
     * @param builder    HTTP の呼び出しの土台
     */
    MockCarrier(CarrierSignature signature, JsonMapper jsonMapper, Clock clock, RestClient.Builder builder) {
        this.signature = signature;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        var requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = builder.requestFactory(requestFactory).build();
    }

    /**
     * 正しい署名の通知を作る。発生日時は今。
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @param statusName     物流サービスの状態の名前
     * @return 署名付きの通知
     */
    public SignedNotification notification(Carrier carrier, String trackingNumber, String statusName) {
        return notification(carrier, trackingNumber, statusName, clock.instant());
    }

    /**
     * 署名が誤っている通知を作る（本文は正しいが、署名は別の鍵で作ったもの）。
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @param statusName     物流サービスの状態の名前
     * @return 署名を確かめられない通知
     */
    public SignedNotification notificationWithInvalidSignature(Carrier carrier, String trackingNumber, String statusName) {
        var valid = notification(carrier, trackingNumber, statusName);
        return new SignedNotification(valid.body(), new CarrierSignature("mock-carrier-wrong-secret").sign(valid.body()));
    }

    /**
     * 通知を、HTTP で通知の受け口に送る。
     *
     * @param baseUrl      バックエンドの URL（受け口を持つサーバー）
     * @param notification 署名付きの通知
     * @return 受け口の応答のステータス
     */
    public int send(String baseUrl, SignedNotification notification) {
        return restClient.post()
                .uri(baseUrl + WEBHOOK_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(CarrierSignature.HEADER, notification.signature())
                .body(notification.body())
                .exchange((request, response) -> response.getStatusCode().value());
    }

    /**
     * 配送状態に当たる、ヤマト運輸の表示の名前を返す。mock の画面のボタンは、この名前で通知する。
     *
     * @param status 配送状態（配送中、配達完了、返送）
     * @return 状態の名前。出荷済みには当たる名前がない
     */
    public static String yamatoNameOf(DeliveryStatus status) {
        return switch (status) {
            case IN_TRANSIT -> "配達中";
            case DELIVERED -> "配達完了";
            case RETURNED -> "返品";
            case SHIPPED -> throw new IllegalArgumentException("出荷済みに当たる通知はない");
        };
    }

    /**
     * 署名付きの通知
     *
     * @param body      本文（署名の対象のバイト列）
     * @param signature ヘッダー {@code X-Carrier-Signature} の値
     */
    public record SignedNotification(byte[] body, String signature) {

        /**
         * @return 本文の文字列
         */
        public String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /**
     * 発生日時を指定して、正しい署名の通知を作る。
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @param statusName     物流サービスの状態の名前
     * @param occurredAt     状態が変わった日時
     * @return 署名付きの通知
     */
    public SignedNotification notification(Carrier carrier, String trackingNumber, String statusName, Instant occurredAt) {
        var body = jsonMapper.writeValueAsString(new CarrierNotification(carrier, trackingNumber, statusName, occurredAt))
                .getBytes(StandardCharsets.UTF_8);
        return new SignedNotification(body, signature.sign(body));
    }
}
