package com.ecsite.module.reporting.infrastructure.directory;

import com.ecsite.module.order.api.OrderReportSnapshot;
import com.ecsite.module.reporting.domain.ReportCancellationKind;
import com.ecsite.module.reporting.domain.ReportOrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文の API の列挙を、レポートの列挙に写す変換の単体テスト
 */
class OrderApiDirectoryTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(OrderReportSnapshot.Status.class)
    @DisplayName("AC-277 注文の API の注文の状態の全ての値を、レポートの状態の同じ名前の値に写す")
    void mapsEveryStatus(OrderReportSnapshot.Status status) {
        assertThat(OrderApiDirectory.toStatus(status)).isEqualTo(ReportOrderStatus.valueOf(status.name()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(OrderReportSnapshot.CancellationKind.class)
    @DisplayName("AC-277 注文の API のキャンセルの種類の全ての値を、レポートのキャンセルの種類の同じ名前の値に写す")
    void mapsEveryCancellationKind(OrderReportSnapshot.CancellationKind kind) {
        assertThat(OrderApiDirectory.toCancellationKind(kind)).isEqualTo(ReportCancellationKind.valueOf(kind.name()).name());
    }

    @Test
    @DisplayName("AC-277 キャンセルの種類がなければ、写しにも持たない")
    void keepsMissingCancellationKind() {
        assertThat(OrderApiDirectory.toCancellationKind(null)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(OrderReportSnapshot.Gender.class)
    @DisplayName("AC-277 注文の API の性別の全ての値を、写しに持つ同じ名前の値に写す")
    void mapsEveryGender(OrderReportSnapshot.Gender gender) {
        assertThat(OrderApiDirectory.toBuyerGender(gender)).isEqualTo(gender.name());
    }

    @Test
    @DisplayName("AC-277 性別の記録がなければ、写しにも持たない")
    void keepsMissingGender() {
        assertThat(OrderApiDirectory.toBuyerGender(null)).isNull();
    }
}
