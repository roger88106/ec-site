package com.ecsite.module.shipping.application.port;

import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;

/**
 * 物流サービスからの通知の受け口（docs/backend/modules/shipping.md「通知のエンドポイント」）。<br />
 * 通知の受け口（HTTP）が、署名を確かめ、物流サービスの状態の名前を配送状態に直してから渡す。
 * 通知は重ねて届き、順序が入れ替わるので、何度受けても結果が変わらないように処理する。
 */
public interface ShipmentNotificationReceiver {

    /**
     * 配送状態の通知を受ける。今より高い順位の状態だけを反映する。
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（ハイフンがあってもよい）
     * @param status         通知の配送状態
     * @return 受けた結果
     */
    Result receive(Carrier carrier, String trackingNumber, DeliveryStatus status);

    /**
     * 通知を受けた結果
     */
    enum Result {

        /**
         * 配送状態を進めた
         */
        ADVANCED,

        /**
         * 出荷はあったが、順位が上がらないので何も変えなかった（重ねて届いた、順序が入れ替わった）
         */
        UNCHANGED,

        /**
         * 登録済みのどの出荷とも合わなかった
         */
        NOT_FOUND,
    }
}
