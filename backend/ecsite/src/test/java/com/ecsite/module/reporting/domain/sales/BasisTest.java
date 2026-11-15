package com.ecsite.module.reporting.domain.sales;

import com.ecsite.module.reporting.domain.snapshot.ReportOrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 売上の数え方の単体テスト
 *
 * <p>数える注文の状態は、注文の状態の列挙で決める（文字列で比べない）。
 */
class BasisTest {

    @ParameterizedTest(name = "「{0}」を数える：{1}")
    @CsvSource({
            "PENDING_PAYMENT, false",
            "ACCEPTED, true",
            "CANCELLATION_REQUESTED, true",
            "SHIPPED, true",
            "CANCELLED, false",
    })
    @DisplayName("AC-278、BR-25 注文基準は、受付・キャンセル申請中・出荷済みの注文を数える")
    void orderBasisCountsAcceptedToShipped(ReportOrderStatus status, boolean counted) {
        // 操作と確認：数える状態に入るかが、期待のとおり
        assertThat(Basis.ORDER.countedStatuses().contains(status)).isEqualTo(counted);
    }

    @ParameterizedTest(name = "「{0}」を数える：{1}")
    @CsvSource({
            "PENDING_PAYMENT, false",
            "ACCEPTED, false",
            "CANCELLATION_REQUESTED, false",
            "SHIPPED, true",
            "CANCELLED, false",
    })
    @DisplayName("AC-278、BR-25 出荷基準は、出荷済みの注文だけを数える")
    void shipmentBasisCountsShippedOnly(ReportOrderStatus status, boolean counted) {
        // 操作と確認：数える状態に入るかが、期待のとおり
        assertThat(Basis.SHIPMENT.countedStatuses().contains(status)).isEqualTo(counted);
    }
}
