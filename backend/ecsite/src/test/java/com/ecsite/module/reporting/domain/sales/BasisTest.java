package com.ecsite.module.reporting.domain.sales;

import com.ecsite.module.reporting.domain.snapshot.ReportOrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 売上の数え方の単体テスト
 */
class BasisTest {

    @ParameterizedTest(name = "{0} の {1} は数える：{2}")
    @CsvSource({
            "ORDER, PENDING_PAYMENT, false",
            "ORDER, ACCEPTED, true",
            "ORDER, CANCELLATION_REQUESTED, true",
            "ORDER, SHIPPED, true",
            "ORDER, CANCELLED, false",
            "SHIPMENT, PENDING_PAYMENT, false",
            "SHIPMENT, ACCEPTED, false",
            "SHIPMENT, CANCELLATION_REQUESTED, false",
            "SHIPMENT, SHIPPED, true",
            "SHIPMENT, CANCELLED, false",
    })
    @DisplayName("AC-278 数える注文の状態を、注文の状態の列挙で決める")
    void countsByStatus(Basis basis, ReportOrderStatus status, boolean counted) {
        assertThat(basis.countedStatuses().contains(status)).isEqualTo(counted);
    }
}
