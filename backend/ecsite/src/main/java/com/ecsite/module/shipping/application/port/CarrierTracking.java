package com.ecsite.module.shipping.application.port;

import com.ecsite.module.shipping.domain.Carrier;

/**
 * 物流サービスの追跡の port
 *
 * <p>物流サービスの型とエラーは、この port の実装（infrastructure）の外に出さない。
 */
public interface CarrierTracking {

    /**
     * 追跡を始める。
     *
     * <p>物流サービスに送り状番号を登録し、配送状態の通知を受け取れるようにする。重ねて呼んでも結果は同じ。
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（ハイフンを除いた数字）
     */
    void startTracking(Carrier carrier, String trackingNumber);
}
