package com.ecsite.module.reporting.application;

import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.reporting.domain.DeliveryStatus;
import com.ecsite.module.reporting.domain.ReportCancellationKind;
import com.ecsite.module.reporting.domain.ReportOrderStatus;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * イベントの列挙を、レポートの列挙に写す変換の単体テスト
 */
class ReportEventHandlerTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(OrderStatusChanged.Status.class)
    @DisplayName("AC-277 注文の状態の全ての値を、レポートの状態の同じ名前の値に写す")
    void mapsEveryOrderStatus(OrderStatusChanged.Status status) {
        assertThat(ReportEventHandler.toOrderStatus(status)).isEqualTo(ReportOrderStatus.valueOf(status.name()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(OrderStatusChanged.CancellationKind.class)
    @DisplayName("AC-277 キャンセルの種類の全ての値を、レポートのキャンセルの種類の同じ名前の値に写す")
    void mapsEveryCancellationKind(OrderStatusChanged.CancellationKind kind) {
        assertThat(ReportEventHandler.toCancellationKind(kind)).isEqualTo(ReportCancellationKind.valueOf(kind.name()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DeliveryStatusChanged.Status.class)
    @DisplayName("AC-277 配送状態の全ての値を、レポートの配送状態の同じ名前の値に写す")
    void mapsEveryDeliveryStatus(DeliveryStatusChanged.Status status) {
        assertThat(ReportEventHandler.toDeliveryStatus(status)).isEqualTo(DeliveryStatus.valueOf(status.name()));
    }
}
