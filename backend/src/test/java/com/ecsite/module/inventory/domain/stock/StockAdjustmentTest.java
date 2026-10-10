package com.ecsite.module.inventory.domain.stock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 在庫調整の単体テスト（T402）
 */
class StockAdjustmentTest {

    /**
     * 現在：2026-10-08 00:00（日本時間）
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Test
    @DisplayName("AC-42 増減量と理由で作れる")
    void creates() {
        var adjustment = StockAdjustment.create(10, StockAdjustmentReason.ARRIVAL, "入荷分", NOW);

        assertThat(adjustment.id()).isNotNull();
        assertThat(adjustment.delta()).isEqualTo(10);
        assertThat(adjustment.reason()).isEqualTo(StockAdjustmentReason.ARRIVAL);
        assertThat(adjustment.memo()).isEqualTo("入荷分");
        assertThat(adjustment.adjustedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("AC-42 減らす調整も作れる")
    void createsNegativeDelta() {
        assertThat(StockAdjustment.create(-3, StockAdjustmentReason.DAMAGE_OR_LOSS, null, NOW).delta()).isEqualTo(-3);
    }

    @Test
    @DisplayName("増減量が 0 なら拒否される")
    void rejectsZeroDelta() {
        assertBusinessError(() -> StockAdjustment.create(0, StockAdjustmentReason.ARRIVAL, null, NOW),
                InventoryErrorCode.INVALID_STOCK_DELTA);
    }

    @ParameterizedTest
    @EnumSource(value = StockAdjustmentReason.class, names = {"OTHER", "ORDER", "ORDER_CANCELLATION"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("AC-46 その他以外の理由では、メモは省略できる")
    void acceptsMissingMemoForReasonsOtherThanOther(StockAdjustmentReason reason) {
        assertThatCode(() -> StockAdjustment.create(1, reason, null, NOW)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("AC-46 理由がその他なら、メモは必須")
    void requiresMemoForOther(String memo) {
        assertBusinessError(() -> StockAdjustment.create(1, StockAdjustmentReason.OTHER, memo, NOW),
                InventoryErrorCode.STOCK_MEMO_REQUIRED);
    }

    @Test
    @DisplayName("AC-46 理由がその他で、メモがあれば作れる")
    void acceptsOtherWithMemo() {
        assertThat(StockAdjustment.create(1, StockAdjustmentReason.OTHER, "サンプル出荷", NOW).memo()).isEqualTo("サンプル出荷");
    }

    @Test
    @DisplayName("メモは 500 文字まで")
    void acceptsMemoUpTo500Characters() {
        assertThatCode(() -> StockAdjustment.create(1, StockAdjustmentReason.OTHER, "あ".repeat(500), NOW))
                .doesNotThrowAnyException();
        assertBusinessError(() -> StockAdjustment.create(1, StockAdjustmentReason.OTHER, "あ".repeat(501), NOW),
                InventoryErrorCode.INVALID_STOCK_MEMO);
    }

    @Test
    @DisplayName("空白だけのメモは、メモなしとして扱う")
    void treatsBlankMemoAsNull() {
        assertThat(StockAdjustment.create(1, StockAdjustmentReason.ARRIVAL, " ", NOW).memo()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = StockAdjustmentReason.class, names = {"ORDER", "ORDER_CANCELLATION"})
    @DisplayName("AC-107 運営者の調整では、注文に由来する理由を選べない")
    void rejectsOrderReasonsForManualAdjustment(StockAdjustmentReason reason) {
        assertBusinessError(() -> StockAdjustment.create(1, reason, null, NOW),
                InventoryErrorCode.ORDER_REASON_NOT_SELECTABLE);
    }

    @Test
    @DisplayName("AC-107 注文による調整は、注文 ID と注文番号を持ち、メモを持たない")
    void createsOrderAdjustment() {
        var orderId = UUID.randomUUID();

        var adjustment = StockAdjustment.forOrder(-2, StockAdjustmentReason.ORDER, orderId, "20261010-482915", NOW);

        assertThat(adjustment.delta()).isEqualTo(-2);
        assertThat(adjustment.reason()).isEqualTo(StockAdjustmentReason.ORDER);
        assertThat(adjustment.memo()).isNull();
        assertThat(adjustment.orderId()).isEqualTo(orderId);
        assertThat(adjustment.orderNumber()).isEqualTo("20261010-482915");
    }
}
