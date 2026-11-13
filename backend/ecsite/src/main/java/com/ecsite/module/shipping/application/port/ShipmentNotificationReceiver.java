package com.ecsite.module.shipping.application.port;

import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import org.jspecify.annotations.Nullable;

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
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（ハイフンがあってもよい）。null のときは NOT_FOUND を返す
     * @param status         通知の配送状態
     * @return 受けた結果
     */
    Result receive(Carrier carrier, @Nullable String trackingNumber, DeliveryStatus status);

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
