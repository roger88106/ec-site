package com.ecsite.module.shipping.application;

import com.ecsite.module.shipping.application.port.CarrierTracking;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/**
 * 追跡の開始の依頼のリスナー（docs/03-機能仕様/shipping.md「物流サービス」）。<br />
 * コミットの後に非同期で、物流サービスに追跡を始めさせる。DB を使わないので、トランザクションを始めない（物流サービスを待つ間に DB の接続を持たない）。
 * 物流サービスが失敗したら例外で終わり、イベントは未完了で残って、再発行のジョブがやり直す。
 */
@Component
@RequiredArgsConstructor
class TrackingRequestedHandler {

    /**
     * 物流サービスの追跡
     */
    private final CarrierTracking tracking;

    /**
     * 追跡の開始の依頼：物流サービスに追跡を始めさせる。
     *
     * @param event 追跡の開始の依頼
     */
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(TrackingRequested event) {
        tracking.startTracking(event.carrier(), event.trackingNumber());
    }
}
