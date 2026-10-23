package com.ecsite.module.reporting.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 状態の履歴の 1 行の単体テスト（T016-09-02、AC-278）
 */
class StatusHistoryEntryTest {

    @ParameterizedTest(name = "{0} の {1} は出荷：{2}")
    @CsvSource({
            "ORDER_STATUS, ACCEPTED, false",
            "ORDER_STATUS, SHIPPED, true",
            "ORDER_STATUS, CANCELLED, false",
            "DELIVERY_STATUS, SHIPPED, true",
            "DELIVERY_STATUS, IN_TRANSIT, false",
            "DELIVERY_STATUS, DELIVERED, false",
    })
    @DisplayName("AC-277、AC-278 出荷の判定は、出来事ごとに、注文の状態か配送状態の列挙で比べる")
    void marksShipment(HistoryKind kind, String to, boolean shipped) {
        var entry = new StatusHistoryEntry(UUID.randomUUID(), UUID.randomUUID(), kind, null, to, Instant.EPOCH, null, null);

        assertThat(entry.marksShipment()).isEqualTo(shipped);
    }

    @ParameterizedTest(name = "後の状態 {0} は配送状態 {0}")
    @EnumSource(DeliveryStatus.class)
    @DisplayName("AC-277、AC-278 配送状態の変化は、後の状態を配送状態の列挙で返す")
    void deliveryStatus(DeliveryStatus to) {
        var entry = new StatusHistoryEntry(UUID.randomUUID(), UUID.randomUUID(), HistoryKind.DELIVERY_STATUS, null,
                to.name(), Instant.EPOCH, null, null);

        assertThat(entry.deliveryStatus()).isEqualTo(to);
    }
}
