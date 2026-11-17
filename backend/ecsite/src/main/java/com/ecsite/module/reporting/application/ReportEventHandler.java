package com.ecsite.module.reporting.application;

import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 注文と出荷のイベントのハンドラー
 */
@Component
@RequiredArgsConstructor
class ReportEventHandler {

    /**
     * 写しと状態の履歴を集めるサービス
     */
    private final ReportCollectionService reportCollectionService;

    /**
     * 注文の状態の変化：履歴に追加し、注文の写しを再作成する。
     *
     * <p>コミットの後に非同期で、別のトランザクションで受ける。処理に失敗したら、未完了のイベントの再発行でやり直す。
     *
     * @param event 注文の状態の変化
     */
    @ApplicationModuleListener
    void on(OrderStatusChanged event) {
        reportCollectionService.record(event);
    }

    /**
     * 配送状態の変化：履歴に追加し、注文の写しを再作成する。
     *
     * <p>コミットの後に非同期で、別のトランザクションで受ける。処理に失敗したら、未完了のイベントの再発行でやり直す。
     *
     * @param event 配送状態の変化
     */
    @ApplicationModuleListener
    void on(DeliveryStatusChanged event) {
        reportCollectionService.record(event);
    }
}
