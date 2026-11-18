package com.ecsite.module.shipping.application.port;

import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 配送状態の通知の受け取りの port
 *
 * <p>同じ通知を何度受けても、順序が入れ替わって届いても、結果は変わらない。
 */
public interface ShipmentNotificationReceiver {

    /**
     * 配送状態の通知を受ける。
     *
     * <p>今より高い順位の状態だけを反映し、反映したときだけ配送状態の変化のイベントを出す。
     * 通知の発生日時が出荷日時より前の出荷には反映しない（出荷ごとに判定する）。
     * 出すイベントの発生日時は、通知の発生日時にする。
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（ハイフンがあってもよい）。null のときは NOT_FOUND を返す
     * @param status         通知の配送状態
     * @param occurredAt     通知の発生日時（物流サービスで状態が変わった日時）
     * @return 受けた結果
     */
    Result receive(Carrier carrier, @Nullable String trackingNumber, DeliveryStatus status, Instant occurredAt);

    /**
     * 通知を受けた結果
     */
    enum Result {

        /**
         * 配送状態を進めた
         */
        ADVANCED,

        /**
         * 出荷はあったが、どの出荷も進めなかった（出荷ごとに、順位が上がらないか、通知の発生日時が出荷日時より前）
         */
        UNCHANGED,

        /**
         * 登録済みのどの出荷とも合わなかった
         */
        NOT_FOUND,
    }
}
