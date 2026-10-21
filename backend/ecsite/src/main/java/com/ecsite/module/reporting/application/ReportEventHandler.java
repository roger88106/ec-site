package com.ecsite.module.reporting.application;

import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.reporting.domain.HistoryKind;
import com.ecsite.module.reporting.domain.StatusHistoryEntry;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 注文と出荷のイベントのリスナー（docs/backend/modules/reporting.md「写しの作り方」）。<br />
 * コミットの後に非同期で、別のトランザクションで動く。何度・どの順で届いても結果は同じ。
 * 処理に失敗したら、未完了のイベントの再発行でやり直す。
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
     * @param event 注文の状態の変化
     */
    @ApplicationModuleListener
    void on(OrderStatusChanged event) {
        reportCollectionService.record(new StatusHistoryEntry(event.eventId(), event.orderId(), HistoryKind.ORDER_STATUS,
                event.from() == null ? null : event.from().name(), event.to().name(), event.occurredAt(),
                event.cancellationKind() == null ? null : event.cancellationKind().name(), event.restocked()));
    }

    /**
     * 配送状態の変化：履歴に追加し、注文の写しを再作成する。
     *
     * @param event 配送状態の変化
     */
    @ApplicationModuleListener
    void on(DeliveryStatusChanged event) {
        reportCollectionService.record(new StatusHistoryEntry(event.eventId(), event.orderId(), HistoryKind.DELIVERY_STATUS,
                event.from() == null ? null : event.from().name(), event.to().name(), event.occurredAt(), null, null));
    }
}
