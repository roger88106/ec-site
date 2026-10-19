package com.ecsite.module.reporting.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    @DisplayName("AC-278 出荷の判定は、出来事ごとに、注文の状態か配送状態の列挙で比べる")
    void marksShipment(HistoryKind kind, String to, boolean shipped) {
        var entry = new StatusHistoryEntry(UUID.randomUUID(), UUID.randomUUID(), kind, null, to, Instant.EPOCH, null, null);

        assertThat(entry.marksShipment()).isEqualTo(shipped);
    }
}
