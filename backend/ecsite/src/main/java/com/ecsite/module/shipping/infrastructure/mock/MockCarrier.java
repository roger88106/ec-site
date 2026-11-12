package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.infrastructure.carrier.CarrierNotification;
import com.ecsite.module.shipping.infrastructure.carrier.CarrierSignature;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

/**
 * mock の物流サービス
 *
 * <p>署名付きの通知を作り、HTTP で通知のエンドポイントに送る。本物の物流サービスと同じ道（署名の検証を含む）を通す。
 * 通知の重複は同じ通知を重ねて送り、順序の入れ替わりと途中の欠けは送る順序と内容を選んで作る。
 * 合わない送り状番号の通知は、登録していない番号を渡して作る。署名の誤りは、テストで共有するソースの
 * {@code TestCarrierNotifications} で作る。
 */
@Component
@ConditionalOnBooleanProperty(name = "ecsite.shipping.mock.enabled")
@RequiredArgsConstructor
public class MockCarrier {

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
     * 通知のエンドポイントを呼ぶ HTTP クライアント
     */
    @Qualifier(MockCarrierConfig.REST_CLIENT)
    private final RestClient restClient;

    /**
     * 正しい署名の通知を作る。
     *
     * <p>発生日時は今。
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
     * 通知を、HTTP で通知のエンドポイントに送る。
     *
     * @param baseUrl      バックエンドの URL（エンドポイントを持つサーバー）
     * @param notification 署名付きの通知
     * @return エンドポイントの応答のステータス
     */
    public int send(String baseUrl, SignedNotification notification) {
        return restClient.post()
                .uri(baseUrl + CarrierNotification.WEBHOOK_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(CarrierSignature.HEADER, notification.signature())
                .body(notification.body())
                .exchange((request, response) -> response.getStatusCode().value());
    }

    /**
     * 配送状態に当たる、ヤマト運輸の表示の名前を返す。
     *
     * @param status 配送状態（配送中、配達完了、返送。出荷済みには当たる名前がない）
     * @return 状態の名前
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
    // 本番で動かない mock。body は HTTP の本文として送るだけで equals・hashCode・toString は使わず、直す手間に見合わないので、配列の成分を許す
    @SuppressWarnings("ArrayRecordComponent")
    public record SignedNotification(byte[] body, String signature) {
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
