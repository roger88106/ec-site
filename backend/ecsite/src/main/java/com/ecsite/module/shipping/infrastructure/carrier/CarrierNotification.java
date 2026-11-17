package com.ecsite.module.shipping.infrastructure.carrier;

import com.ecsite.module.shipping.domain.Carrier;

import java.time.Instant;

/**
 * 物流サービスの通知の本文
 *
 * <p>JSON で受け取る。
 *
 * @param carrier        配送業者
 * @param trackingNumber 送り状番号
 * @param status         物流サービスの状態の名前（ヤマト運輸なら「配達中」など）
 * @param occurredAt     状態が変わった日時（ISO 8601）
 */
public record CarrierNotification(Carrier carrier, String trackingNumber, String status, Instant occurredAt) {

    /**
     * 通知のエンドポイントのパス
     */
    public static final String WEBHOOK_PATH = "/api/webhooks/carrier";

    /**
     * 項目が全てそろっているかを返す。
     *
     * @return そろっていれば true
     */
    public boolean isComplete() {
        return carrier != null && trackingNumber != null && !trackingNumber.isBlank()
                && status != null && !status.isBlank() && occurredAt != null;
    }
}
