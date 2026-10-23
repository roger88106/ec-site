package com.ecsite.module.reporting.domain.snapshot;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * 状態の履歴の 1 行（docs/design/modules/reporting.md「集めるデータ」）。受けたイベントごとに 1 行。<br />
 * 状態は、出来事に応じて注文の状態か配送状態の列挙で持つ（後から出来事を追加しても表を変えずに済むため）。
 *
 * @param eventId          元のイベント ID
 * @param orderId          注文 ID
 * @param kind             出来事
 * @param from             前の状態。作成では null
 * @param to               後の状態
 * @param occurredAt       発生日時
 * @param cancellationKind キャンセルの種類。キャンセルでなければ null
 * @param restocked        キャンセルで在庫に戻したか。キャンセルでなければ null
 */
public record StatusHistoryEntry(UUID eventId, UUID orderId, HistoryKind kind, @Nullable HistoryStatus from, HistoryStatus to,
                                 Instant occurredAt, @Nullable ReportCancellationKind cancellationKind, @Nullable Boolean restocked) {

    /**
     * 後の状態を配送状態の列挙にする。
     *
     * @return 後の状態を配送状態の列挙にしたもの（出来事が配送状態の変化のときだけ使う）
     */
    public DeliveryStatus deliveryStatus() {
        if (to instanceof DeliveryStatus delivery) {
            return delivery;
        }
        throw new IllegalStateException("後の状態が配送状態ではない：" + to);
    }

    /**
     * 出荷を表す変化かを返す。
     *
     * @return 出荷を表す変化か（注文の状態か配送状態が出荷済みになった）
     */
    public boolean marksShipment() {
        return switch (to) {
            case ReportOrderStatus status -> status == ReportOrderStatus.SHIPPED;
            case DeliveryStatus status -> status == DeliveryStatus.SHIPPED;
        };
    }
}
