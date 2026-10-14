package com.ecsite.module.shipping.application.port;

import com.ecsite.module.shipping.domain.Carrier;

/**
 * 物流サービスの追跡（docs/backend/modules/shipping.md「物流サービス」）。<br />
 * 物流サービスの型とエラーは、この口の実装（infrastructure）の外に出さない。
 */
public interface CarrierTracking {

    /**
     * 追跡を始める。物流サービスに送り状番号を登録し、配送状態の通知を受け取れるようにする。
     * 出荷を作ったトランザクションのコミットの後に呼ぶ。失敗したら例外で終わり、再発行でやり直すので、重ねて呼んでも結果は同じにする。
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（ハイフンを除いた数字）
     */
    void startTracking(Carrier carrier, String trackingNumber);
}
