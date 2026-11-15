package com.ecsite.module.reporting.infrastructure.directory;

import com.ecsite.module.order.api.model.OrderReportSnapshot;
import com.ecsite.module.reporting.domain.snapshot.ReportBuyerGender;
import com.ecsite.module.reporting.domain.snapshot.ReportCancellationKind;
import com.ecsite.module.reporting.domain.snapshot.ReportOrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文の API の列挙を、レポートの列挙に写す変換の単体テスト
 *
 * <p>注文の API の列挙の全ての値を回すので、値を追加して対応を書き忘れると失敗する。
 */
class OrderApiDirectoryTest {

    @Nested
    @DisplayName("注文の状態")
    class Status {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(OrderReportSnapshot.Status.class)
        @DisplayName("AC-277 注文の API の注文の状態の全ての値を、レポートの状態の同じ名前の値に写す")
        void mapsEveryStatus(OrderReportSnapshot.Status status) {
            // 操作と確認：同じ名前の値になる
            assertThat(OrderApiDirectory.toStatus(status)).isEqualTo(ReportOrderStatus.valueOf(status.name()));
        }
    }

    @Nested
    @DisplayName("キャンセルの種類")
    class CancellationKind {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(OrderReportSnapshot.CancellationKind.class)
        @DisplayName("AC-277 注文の API のキャンセルの種類の全ての値を、レポートのキャンセルの種類の同じ名前の値に写す")
        void mapsEveryCancellationKind(OrderReportSnapshot.CancellationKind kind) {
            // 操作と確認：同じ名前の値になる
            assertThat(OrderApiDirectory.toCancellationKind(kind)).isEqualTo(ReportCancellationKind.valueOf(kind.name()));
        }

        @Test
        @DisplayName("AC-277 キャンセルの種類がなければ、写しにも持たない")
        void keepsMissingCancellationKind() {
            // 操作と確認：ないまま
            assertThat(OrderApiDirectory.toCancellationKind(null)).isNull();
        }
    }

    @Nested
    @DisplayName("性別")
    class Gender {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(OrderReportSnapshot.Gender.class)
        @DisplayName("AC-277 注文の API の性別の全ての値を、レポートの注文時の性別の同じ名前の値に写す")
        void mapsEveryGender(OrderReportSnapshot.Gender gender) {
            // 操作と確認：同じ名前の値になる
            assertThat(OrderApiDirectory.toBuyerGender(gender)).isEqualTo(ReportBuyerGender.valueOf(gender.name()));
        }

        @Test
        @DisplayName("AC-277 性別の記録がなければ、写しにも持たない")
        void keepsMissingGender() {
            // 操作と確認：ないまま
            assertThat(OrderApiDirectory.toBuyerGender(null)).isNull();
        }
    }
}
