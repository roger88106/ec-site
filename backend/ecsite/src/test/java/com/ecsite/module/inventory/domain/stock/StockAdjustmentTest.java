package com.ecsite.module.inventory.domain.stock;

import com.ecsite.common.model.value.Operator;
import com.ecsite.module.inventory.domain.InventoryErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 在庫調整の単体テスト
 *
 * <p>運営者の調整（{@link StockAdjustment#create}）と注文による調整（{@link StockAdjustment#forOrder}）の作り方だけを確認する。
 * 在庫数への反映と、負になる・上限を超える調整の拒否は、Repository と API の統合テストが確認する。
 */
class StockAdjustmentTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 調整した運営者
     */
    private static final Operator OPERATOR = new Operator(UUID.randomUUID(), "管理 花子");

    /**
     * 入荷の増減量
     */
    private static final int ARRIVAL_DELTA = 10;

    /**
     * 入荷のメモ
     */
    private static final String ARRIVAL_MEMO = "入荷分";

    /**
     * 理由「その他」のメモ
     */
    private static final String OTHER_MEMO = "サンプル出荷";

    /**
     * メモの最大の文字数
     */
    private static final int MEMO_MAX_LENGTH = 500;

    /**
     * 注文による減算の増減量
     */
    private static final int ORDER_DELTA = -2;

    /**
     * 注文番号
     */
    private static final String ORDER_NUMBER = "20261010-482915";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("増減量")
    class Delta {

        @Test
        @DisplayName("AC-42 増減量と理由で、運営者の調整を作れる")
        void creates() {
            // 操作：入荷の調整を作る
            var adjustment = StockAdjustment.create(ARRIVAL_DELTA, StockAdjustmentReason.ARRIVAL, ARRIVAL_MEMO, OPERATOR, NOW);

            // 確認：渡した値を持つ
            assertArrivalAdjustment(adjustment);
        }

        @Test
        @DisplayName("AC-42 減らす調整も作れる")
        void createsNegativeDelta() {
            // 操作：破損・紛失で 3 減らす調整を作る
            var adjustment = StockAdjustment.create(-3, StockAdjustmentReason.DAMAGE_OR_LOSS, null, OPERATOR, NOW);

            // 確認：増減量が負のまま
            assertThat(adjustment.delta()).isEqualTo(-3);
        }

        @Test
        @DisplayName("増減量が 0 なら拒否される")
        void rejectsZeroDelta() {
            // 操作と確認：業務のエラー INVALID_STOCK_DELTA で拒否される
            assertBusinessError(() -> StockAdjustment.create(0, StockAdjustmentReason.ARRIVAL, null, OPERATOR, NOW),
                    InventoryErrorCode.INVALID_STOCK_DELTA);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1_000_000, -1_000_000})
        @DisplayName("AC-42 運営者の 1 回の増減量は、上限の ±1,000,000 ちょうどまで作れる")
        void acceptsDeltaUpToOneMillion(int delta) {
            // 操作：上限ちょうどの調整を作る
            var adjustment = StockAdjustment.create(delta, StockAdjustmentReason.ARRIVAL, null, OPERATOR, NOW);

            // 確認：増減量がそのまま
            assertThat(adjustment.delta()).isEqualTo(delta);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1_000_001, -1_000_001, Integer.MAX_VALUE, Integer.MIN_VALUE})
        @DisplayName("AC-42 運営者の 1 回の増減量が ±1,000,000 を超えれば拒否される")
        void rejectsDeltaOverOneMillion(int delta) {
            // 操作と確認：業務のエラー INVALID_STOCK_DELTA で拒否される
            assertBusinessError(() -> StockAdjustment.create(delta, StockAdjustmentReason.ARRIVAL, null, OPERATOR, NOW),
                    InventoryErrorCode.INVALID_STOCK_DELTA);
        }
    }

    @Nested
    @DisplayName("理由とメモ")
    class ReasonAndMemo {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(value = StockAdjustmentReason.class, names = {"OTHER", "ORDER", "ORDER_CANCELLATION"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("AC-46 その他以外の理由では、メモは省略できる")
        void acceptsMissingMemoForReasonsOtherThanOther(StockAdjustmentReason reason) {
            // 操作と確認：メモなしで作れる
            assertThatCode(() -> StockAdjustment.create(1, reason, null, OPERATOR, NOW)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "「{0}」")
        @NullAndEmptySource
        @ValueSource(strings = " ")
        @DisplayName("AC-46 理由がその他なら、メモは必須")
        void requiresMemoForOther(String memo) {
            // 操作と確認：業務のエラー STOCK_MEMO_REQUIRED で拒否される
            assertBusinessError(() -> StockAdjustment.create(1, StockAdjustmentReason.OTHER, memo, OPERATOR, NOW),
                    InventoryErrorCode.STOCK_MEMO_REQUIRED);
        }

        @Test
        @DisplayName("AC-46 理由がその他で、メモがあれば作れる")
        void acceptsOtherWithMemo() {
            // 操作：メモ付きで、理由「その他」の調整を作る
            var adjustment = StockAdjustment.create(1, StockAdjustmentReason.OTHER, OTHER_MEMO, OPERATOR, NOW);

            // 確認：メモを持つ
            assertThat(adjustment.memo()).isEqualTo(OTHER_MEMO);
        }

        @Test
        @DisplayName("メモは 500 文字ちょうどまで作れる")
        void acceptsMemoUpTo500Characters() {
            // 操作と確認：上限ちょうどの長さのメモで作れる
            assertThatCode(() -> StockAdjustment.create(1, StockAdjustmentReason.OTHER, "あ".repeat(MEMO_MAX_LENGTH), OPERATOR, NOW))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("メモが 500 文字を超えれば拒否される")
        void rejectsMemoOver500Characters() {
            // 操作と確認：上限を 1 文字超えるメモは、業務のエラー INVALID_STOCK_MEMO で拒否される
            assertBusinessError(() -> StockAdjustment.create(1, StockAdjustmentReason.OTHER, "あ".repeat(MEMO_MAX_LENGTH + 1), OPERATOR, NOW),
                    InventoryErrorCode.INVALID_STOCK_MEMO);
        }

        @Test
        @DisplayName("空白だけのメモは、メモなしとして扱う")
        void treatsBlankMemoAsNull() {
            // 操作：空白だけのメモで調整を作る
            var adjustment = StockAdjustment.create(1, StockAdjustmentReason.ARRIVAL, " ", OPERATOR, NOW);

            // 確認：メモがない
            assertThat(adjustment.memo()).isNull();
        }
    }

    @Nested
    @DisplayName("注文に由来する調整")
    class FromOrder {

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(value = StockAdjustmentReason.class, names = {"ORDER", "ORDER_CANCELLATION"})
        @DisplayName("AC-107 運営者の調整では、注文に由来する理由を選べない")
        void rejectsOrderReasonsForManualAdjustment(StockAdjustmentReason reason) {
            // 操作と確認：業務のエラー ORDER_REASON_NOT_SELECTABLE で拒否される
            assertBusinessError(() -> StockAdjustment.create(1, reason, null, OPERATOR, NOW),
                    InventoryErrorCode.ORDER_REASON_NOT_SELECTABLE);
        }

        @Test
        @DisplayName("AC-107 注文による調整は、注文 ID と注文番号を持ち、メモを持たない")
        void createsOrderAdjustment() {
            // 準備：注文
            var orderId = UUID.randomUUID();

            // 操作：注文による減算の調整を作る
            var adjustment = StockAdjustment.forOrder(ORDER_DELTA, StockAdjustmentReason.ORDER, orderId, ORDER_NUMBER, NOW);

            // 確認：注文の写しを持ち、メモを持たない
            assertOrderAdjustment(adjustment, orderId);
        }

        @Test
        @DisplayName("AC-107 注文のキャンセルによる戻しの調整を、理由「注文のキャンセル」で作れる")
        void createsCancellationAdjustment() {
            // 操作：注文のキャンセルで 2 個戻す調整を作る
            var adjustment = StockAdjustment.forOrder(-ORDER_DELTA, StockAdjustmentReason.ORDER_CANCELLATION, UUID.randomUUID(),
                    ORDER_NUMBER, NOW);

            // 確認：増える調整で、理由が注文のキャンセル
            assertThat(adjustment.delta()).isEqualTo(-ORDER_DELTA);
            assertThat(adjustment.reason()).isEqualTo(StockAdjustmentReason.ORDER_CANCELLATION);
        }

        @Test
        @DisplayName("注文による調整でも、増減量が 0 なら拒否される")
        void rejectsZeroDeltaForOrder() {
            // 操作と確認：業務のエラー INVALID_STOCK_DELTA で拒否される
            assertBusinessError(() -> StockAdjustment.forOrder(0, StockAdjustmentReason.ORDER, UUID.randomUUID(), ORDER_NUMBER, NOW),
                    InventoryErrorCode.INVALID_STOCK_DELTA);
        }

        @ParameterizedTest(name = "「{0}」")
        @EnumSource(value = StockAdjustmentReason.class, names = {"ORDER", "ORDER_CANCELLATION"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("注文による調整は、運営者の理由では作れない（プログラムの誤り）")
        void rejectsOperatorReasonsForOrder(StockAdjustmentReason reason) {
            // 操作と確認：プログラムの誤りとして拒否される
            assertThatThrownBy(() -> StockAdjustment.forOrder(ORDER_DELTA, reason, UUID.randomUUID(), ORDER_NUMBER, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 入荷の調整が、渡した値を持つことを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>ID がある</li>
     *   <li>増減量</li>
     *   <li>理由が「入荷」</li>
     *   <li>運営者</li>
     *   <li>メモ</li>
     *   <li>調整日時が現在時刻</li>
     * </ul>
     *
     * @param adjustment 確認する調整
     */
    private static void assertArrivalAdjustment(StockAdjustment adjustment) {
        assertThat(adjustment.id()).isNotNull();
        assertThat(adjustment.delta()).isEqualTo(ARRIVAL_DELTA);
        assertThat(adjustment.reason()).isEqualTo(StockAdjustmentReason.ARRIVAL);
        assertThat(adjustment.operator()).isEqualTo(OPERATOR);
        assertThat(adjustment.memo()).isEqualTo(ARRIVAL_MEMO);
        assertThat(adjustment.adjustedAt()).isEqualTo(NOW);
    }

    /**
     * 注文による減算の調整が、注文の写しを持つことを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>増減量</li>
     *   <li>理由が「注文」</li>
     *   <li>メモがない</li>
     *   <li>注文 ID</li>
     *   <li>注文番号</li>
     * </ul>
     *
     * @param adjustment 確認する調整
     * @param orderId    注文 ID（実行ごとに作るので、定数にできない）
     */
    private static void assertOrderAdjustment(StockAdjustment adjustment, UUID orderId) {
        assertThat(adjustment.delta()).isEqualTo(ORDER_DELTA);
        assertThat(adjustment.reason()).isEqualTo(StockAdjustmentReason.ORDER);
        assertThat(adjustment.memo()).isNull();
        assertThat(adjustment.orderId()).isEqualTo(orderId);
        assertThat(adjustment.orderNumber()).isEqualTo(ORDER_NUMBER);
    }
}
