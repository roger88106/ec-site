package com.ecsite.module.reporting.application;

import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.reporting.domain.DeliveryStatus;
import com.ecsite.module.reporting.domain.HistoryKind;
import com.ecsite.module.reporting.domain.ReportCancellationKind;
import com.ecsite.module.reporting.domain.ReportOrderStatus;
import com.ecsite.module.reporting.domain.StatusHistoryEntry;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 注文と出荷のイベントのリスナー（docs/design/modules/reporting.md「写しの作り方」）。<br />
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
     * 注文の状態の変化：履歴に追加し、注文の写しを再作成する。履歴の状態は、レポートの列挙の名前で持つ。
     *
     * @param event 注文の状態の変化
     */
    @ApplicationModuleListener
    void on(OrderStatusChanged event) {
        reportCollectionService.record(new StatusHistoryEntry(event.eventId(), event.orderId(), HistoryKind.ORDER_STATUS,
                event.from() == null ? null : toOrderStatus(event.from()).name(), toOrderStatus(event.to()).name(),
                event.occurredAt(),
                event.cancellationKind() == null ? null : toCancellationKind(event.cancellationKind()),
                event.restocked()));
    }

    /**
     * 配送状態の変化：履歴に追加し、注文の写しを再作成する。履歴の状態は、レポートの列挙の名前で持つ。
     *
     * @param event 配送状態の変化
     */
    @ApplicationModuleListener
    void on(DeliveryStatusChanged event) {
        reportCollectionService.record(new StatusHistoryEntry(event.eventId(), event.orderId(), HistoryKind.DELIVERY_STATUS,
                event.from() == null ? null : toDeliveryStatus(event.from()).name(), toDeliveryStatus(event.to()).name(),
                event.occurredAt(), null, null));
    }

    /**
     * 注文のイベントの状態を、レポートの型にする。
     */
    static ReportOrderStatus toOrderStatus(OrderStatusChanged.Status status) {
        return switch (status) {
            case PENDING_PAYMENT -> ReportOrderStatus.PENDING_PAYMENT;
            case ACCEPTED -> ReportOrderStatus.ACCEPTED;
            case CANCELLATION_REQUESTED -> ReportOrderStatus.CANCELLATION_REQUESTED;
            case CANCELLED -> ReportOrderStatus.CANCELLED;
            case SHIPPED -> ReportOrderStatus.SHIPPED;
        };
    }

    /**
     * 注文のイベントのキャンセルの種類を、レポートの型にする。
     */
    static ReportCancellationKind toCancellationKind(OrderStatusChanged.CancellationKind kind) {
        return switch (kind) {
            case REQUEST_APPROVED -> ReportCancellationKind.REQUEST_APPROVED;
            case OPERATOR -> ReportCancellationKind.OPERATOR;
            case PAYMENT_EXPIRED -> ReportCancellationKind.PAYMENT_EXPIRED;
        };
    }

    /**
     * 出荷のイベントの配送状態を、レポートの型にする。
     */
    static DeliveryStatus toDeliveryStatus(DeliveryStatusChanged.Status status) {
        return switch (status) {
            case SHIPPED -> DeliveryStatus.SHIPPED;
            case IN_TRANSIT -> DeliveryStatus.IN_TRANSIT;
            case DELIVERED -> DeliveryStatus.DELIVERED;
            case RETURNED -> DeliveryStatus.RETURNED;
        };
    }
}
