package com.ecsite.module.reporting.domain.snapshot;

import com.ecsite.common.model.id.OrderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 状態の履歴の 1 行の単体テスト
 */
class StatusHistoryEntryTest {

    // ============================== テスト ==============================

    @Nested
    @DisplayName("出荷の判定")
    class MarksShipment {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(ReportOrderStatus.class)
        @DisplayName("AC-277、278 後の状態が注文の状態のときは、出荷済みだけを出荷とみなす")
        void byOrderStatus(ReportOrderStatus to) {
            // 準備：後の状態が注文の状態の行
            var entry = entry(HistoryKind.ORDER_STATUS, to);

            // 操作と確認：出荷済みのときだけ出荷とみなす
            assertThat(entry.marksShipment()).isEqualTo(to == ReportOrderStatus.SHIPPED);
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(DeliveryStatus.class)
        @DisplayName("AC-277、278 後の状態が配送状態のときは、出荷済みだけを出荷とみなす")
        void byDeliveryStatus(DeliveryStatus to) {
            // 準備：後の状態が配送状態の行
            var entry = entry(HistoryKind.DELIVERY_STATUS, to);

            // 操作と確認：出荷済みのときだけ出荷とみなす
            assertThat(entry.marksShipment()).isEqualTo(to == DeliveryStatus.SHIPPED);
        }
    }

    @Nested
    @DisplayName("配送状態")
    class DeliveryStatusOfEntry {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(DeliveryStatus.class)
        @DisplayName("AC-277、278 配送状態の変化は、後の状態を配送状態の列挙で返す")
        void returnsDeliveryStatus(DeliveryStatus to) {
            // 準備：後の状態が配送状態の行
            var entry = entry(HistoryKind.DELIVERY_STATUS, to);

            // 操作と確認：後の状態のまま
            assertThat(entry.deliveryStatus()).isEqualTo(to);
        }

        @Test
        @DisplayName("AC-277、278 後の状態が配送状態でなければ、配送状態を求めると失敗する")
        void failsForOrderStatus() {
            // 準備：後の状態が注文の状態の行
            var entry = entry(HistoryKind.ORDER_STATUS, ReportOrderStatus.SHIPPED);

            // 操作と確認：プログラムの誤りとして失敗する
            assertThatThrownBy(entry::deliveryStatus).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("作成")
    class Creation {

        @Test
        @DisplayName("AC-277、278 注文の状態の出来事は、前と後が注文の状態なら作れる")
        void acceptsOrderStatuses() {
            // 操作：前と後が注文の状態の行を作る
            var entry = new StatusHistoryEntry(UUID.randomUUID(), new OrderId(UUID.randomUUID()), HistoryKind.ORDER_STATUS,
                    ReportOrderStatus.PENDING_PAYMENT, ReportOrderStatus.ACCEPTED, Instant.EPOCH, null, null);

            // 確認：作れる
            assertThat(entry).isNotNull();
        }

        @Test
        @DisplayName("AC-277、278 配送状態の出来事は、前と後が配送状態なら作れる")
        void acceptsDeliveryStatuses() {
            // 操作：前と後が配送状態の行を作る
            var entry = new StatusHistoryEntry(UUID.randomUUID(), new OrderId(UUID.randomUUID()), HistoryKind.DELIVERY_STATUS,
                    DeliveryStatus.SHIPPED, DeliveryStatus.IN_TRANSIT, Instant.EPOCH, null, null);

            // 確認：作れる
            assertThat(entry).isNotNull();
        }

        @Test
        @DisplayName("AC-277、278 前の状態は null でもよい")
        void acceptsMissingFrom() {
            // 操作：前の状態のない行を作る
            var entry = entry(HistoryKind.DELIVERY_STATUS, DeliveryStatus.SHIPPED);

            // 確認：前の状態がないまま
            assertThat(entry.from()).isNull();
        }

        @Test
        @DisplayName("AC-277、278 注文の状態の出来事に配送状態を入れると作れない")
        void rejectsDeliveryStatusForOrderKind() {
            // 操作と確認：プログラムの誤りとして拒否される
            assertThatThrownBy(() -> entry(HistoryKind.ORDER_STATUS, DeliveryStatus.SHIPPED))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("AC-277、278 配送状態の出来事に注文の状態を入れると作れない")
        void rejectsOrderStatusForDeliveryKind() {
            // 操作と確認：プログラムの誤りとして拒否される
            assertThatThrownBy(() -> entry(HistoryKind.DELIVERY_STATUS, ReportOrderStatus.SHIPPED))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("AC-277、278 前の状態が出来事に合わない型だと作れない")
        void rejectsMismatchedFrom() {
            // 操作と確認：前だけが配送状態の、注文の状態の出来事は、プログラムの誤りとして拒否される
            assertThatThrownBy(() -> new StatusHistoryEntry(UUID.randomUUID(), new OrderId(UUID.randomUUID()), HistoryKind.ORDER_STATUS,
                    DeliveryStatus.SHIPPED, ReportOrderStatus.ACCEPTED, Instant.EPOCH, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 前の状態のない、状態の履歴の 1 行を作る。
     *
     * @param kind 出来事
     * @param to   後の状態
     * @return 履歴の 1 行
     */
    private static StatusHistoryEntry entry(HistoryKind kind, HistoryStatus to) {
        return new StatusHistoryEntry(UUID.randomUUID(), new OrderId(UUID.randomUUID()), kind, null, to, Instant.EPOCH, null, null);
    }
}
