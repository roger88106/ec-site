package com.ecsite.module.shipping.application;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/**
 * 追跡の開始の依頼のハンドラー
 */
@Component
@RequiredArgsConstructor
class TrackingRequestedHandler {

    /**
     * 出荷のサービス
     */
    private final ShipmentService shipmentService;

    /**
     * 追跡の開始の依頼：物流サービスに追跡を始めさせる。
     *
     * <p>コミットの後に受ける。DB を使わないので、トランザクションを始めない（物流サービスを待つ間に DB の接続を持たない）。
     * 物流サービスが失敗したら例外で終わり、イベントは未完了で残って、再発行でやり直す。
     *
     * @param event 追跡の開始の依頼
     */
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(TrackingRequested event) {
        shipmentService.startTracking(event);
    }
}
