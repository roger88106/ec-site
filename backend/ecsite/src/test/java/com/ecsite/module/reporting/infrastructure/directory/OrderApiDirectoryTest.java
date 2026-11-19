package com.ecsite.module.reporting.infrastructure.directory;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.api.model.AxisValue;
import com.ecsite.module.order.api.model.OrderSnapshot;
import com.ecsite.module.order.api.model.TaxAmount;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.reporting.domain.snapshot.ReportBuyerGender;
import com.ecsite.module.reporting.domain.snapshot.ReportCancellationKind;
import com.ecsite.module.reporting.domain.snapshot.ReportOrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
        @EnumSource(OrderStatusChanged.Status.class)
        @DisplayName("AC-277 注文の API の注文の状態の全ての値を、レポートの状態の同じ名前の値に写す")
        void mapsEveryStatus(OrderStatusChanged.Status status) {
            // 操作と確認：同じ名前の値になる
            assertThat(OrderApiDirectory.toStatus(status)).isEqualTo(ReportOrderStatus.valueOf(status.name()));
        }
    }

    @Nested
    @DisplayName("キャンセルの種類")
    class CancellationKind {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(OrderStatusChanged.CancellationKind.class)
        @DisplayName("AC-277 注文の API のキャンセルの種類の全ての値を、レポートのキャンセルの種類の同じ名前の値に写す")
        void mapsEveryCancellationKind(OrderStatusChanged.CancellationKind kind) {
            // 操作と確認：同じ名前の値になる
            assertThat(OrderApiDirectory.toCancellationKind(kind)).isEqualTo(ReportCancellationKind.valueOf(kind.name()));
        }
    }

    @Nested
    @DisplayName("消費税の合計")
    class TaxTotal {

        @Test
        @DisplayName("AC-277 税率ごとの消費税額を合計する")
        void sumsTaxAmounts() {
            // 準備：10% の 290 円と 8% の 80 円
            var taxes = List.of(
                    new TaxAmount(BigDecimal.TEN, Money.of(3200), Money.of(290)),
                    new TaxAmount(BigDecimal.valueOf(8), Money.of(1080), Money.of(80)));

            // 操作と確認：370 円
            assertThat(OrderApiDirectory.taxTotal(taxes)).isEqualTo(Money.of(370));
        }

        @Test
        @DisplayName("AC-277 消費税額がなければ 0 円")
        void returnsZeroWithoutTaxes() {
            // 操作と確認：0 円
            assertThat(OrderApiDirectory.taxTotal(List.of())).isEqualTo(Money.ZERO);
        }
    }

    @Nested
    @DisplayName("明細の税抜小計")
    class SubtotalExclTax {

        @Test
        @DisplayName("AC-277 税抜単価 × 数量を求める")
        void multipliesUnitPriceByQuantity() {
            // 準備：税抜単価 1,000 円、数量 2 の明細
            var line = new OrderSnapshot.Line(1, UUID.randomUUID(), "P-1", "V-1", "T シャツ",
                    List.of(new AxisValue("色", "白")), 2, BigDecimal.TEN, Money.of(1000), Money.of(2200));

            // 操作と確認：2,000 円
            assertThat(OrderApiDirectory.subtotalExclTax(line)).isEqualTo(Money.of(2000));
        }
    }

    @Nested
    @DisplayName("性別")
    class Gender {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(OrderSnapshot.Gender.class)
        @DisplayName("AC-277 注文の API の性別の全ての値を、レポートの注文時の性別の同じ名前の値に写す")
        void mapsEveryGender(OrderSnapshot.Gender gender) {
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
