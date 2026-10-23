package com.ecsite.module.reporting.domain.snapshot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 状態の履歴の 1 行の単体テスト（T016-09-02、AC-277、AC-278）
 */
class StatusHistoryEntryTest {

    @ParameterizedTest(name = "注文の状態 {0} は出荷を示す：{0} == SHIPPED")
    @EnumSource(ReportOrderStatus.class)
    @DisplayName("AC-277、AC-278 出荷の判定は、後の状態が注文の状態のときは、出荷済みだけを出荷とみなす")
    void marksShipmentByOrderStatus(ReportOrderStatus to) {
        var entry = entry(HistoryKind.ORDER_STATUS, to);

        assertThat(entry.marksShipment()).isEqualTo(to == ReportOrderStatus.SHIPPED);
    }

    @ParameterizedTest(name = "配送状態 {0} は出荷を示す：{0} == SHIPPED")
    @EnumSource(DeliveryStatus.class)
    @DisplayName("AC-277、AC-278 出荷の判定は、後の状態が配送状態のときは、出荷済みだけを出荷とみなす")
    void marksShipmentByDeliveryStatus(DeliveryStatus to) {
        var entry = entry(HistoryKind.DELIVERY_STATUS, to);

        assertThat(entry.marksShipment()).isEqualTo(to == DeliveryStatus.SHIPPED);
    }

    @ParameterizedTest(name = "後の状態 {0} は配送状態 {0}")
    @EnumSource(DeliveryStatus.class)
    @DisplayName("AC-277、AC-278 配送状態の変化は、後の状態を配送状態の列挙で返す")
    void deliveryStatus(DeliveryStatus to) {
        var entry = entry(HistoryKind.DELIVERY_STATUS, to);

        assertThat(entry.deliveryStatus()).isEqualTo(to);
    }

    @Test
    @DisplayName("AC-277、AC-278 後の状態が配送状態でなければ、配送状態を求めると失敗する")
    void deliveryStatusFailsForOrderStatus() {
        var entry = entry(HistoryKind.ORDER_STATUS, ReportOrderStatus.SHIPPED);

        assertThatThrownBy(entry::deliveryStatus).isInstanceOf(IllegalStateException.class);
    }

    private static StatusHistoryEntry entry(HistoryKind kind, HistoryStatus to) {
        return new StatusHistoryEntry(UUID.randomUUID(), UUID.randomUUID(), kind, null, to, Instant.EPOCH, null, null);
    }
}
