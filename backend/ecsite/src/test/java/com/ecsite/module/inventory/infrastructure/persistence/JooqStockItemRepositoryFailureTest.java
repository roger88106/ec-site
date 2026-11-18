package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.inventory.domain.InventoryErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;

/**
 * 在庫の調整が更新されなかったときの、エラーの選び方の単体テスト
 *
 * <p>更新の後に在庫が作られる並びは統合テストで再現しにくいので、選び方だけを確認する。
 */
class JooqStockItemRepositoryFailureTest {

    // ============================== 定数 ==============================

    /**
     * 運営者の調整の後の在庫数の上限
     */
    private static final int MAX_MANUAL_QUANTITY = 1_000_000;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("増やす調整")
    class PositiveDelta {

        @Test
        @DisplayName("注文のキャンセルで増やす調整が更新されなければ、更新の後に在庫が作られていても見つからない（負になるエラーにしない）")
        void positiveDeltaIsNotFoundEvenIfStockAppearsLater() {
            // 操作と確認：在庫数 0 の在庫が見えても、NOT_FOUND を選ぶ
            assertBusinessError(() -> {
                throw JooqStockItemRepository.failureOf(1, false, () -> Optional.of(0));
            }, CommonErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("AC-42 運営者の増やす調整が更新されず、今の在庫数に加えると上限を超えるなら、上限を超えるエラー")
        void positiveManualDeltaOverLimitIsExceeded() {
            // 操作と確認：上限ちょうどの在庫に 1 を加える調整は、STOCK_QUANTITY_EXCEEDED を選ぶ
            assertBusinessError(() -> {
                throw JooqStockItemRepository.failureOf(1, true, () -> Optional.of(MAX_MANUAL_QUANTITY));
            }, InventoryErrorCode.STOCK_QUANTITY_EXCEEDED);
        }

        @Test
        @DisplayName("運営者の増やす調整が更新されず、更新の後に作られた在庫（在庫数 0）なら見つからない（上限を超えるエラーにしない）")
        void positiveManualDeltaIsNotFoundIfStockAppearsLater() {
            // 操作と確認：上限ちょうどの増減量でも、在庫数 0 の在庫なら NOT_FOUND を選ぶ
            assertBusinessError(() -> {
                throw JooqStockItemRepository.failureOf(MAX_MANUAL_QUANTITY, true, () -> Optional.of(0));
            }, CommonErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("運営者の増やす調整が更新されず、在庫がなければ見つからない")
        void positiveManualDeltaWithoutStockIsNotFound() {
            // 操作と確認：NOT_FOUND を選ぶ
            assertBusinessError(() -> {
                throw JooqStockItemRepository.failureOf(1, true, Optional::empty);
            }, CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("減らす調整")
    class NegativeDelta {

        @Test
        @DisplayName("AC-43 減らす調整が更新されず、在庫があれば負になるエラー")
        void negativeDeltaWithStockIsInsufficient() {
            // 操作と確認：INSUFFICIENT_STOCK を選ぶ
            assertBusinessError(() -> {
                throw JooqStockItemRepository.failureOf(-1, true, () -> Optional.of(0));
            }, InventoryErrorCode.INSUFFICIENT_STOCK);
        }

        @Test
        @DisplayName("運営者の減らす調整が更新されず、今の在庫数に加えると上限を超えるなら、上限を超えるエラー")
        void negativeManualDeltaOverLimitIsExceeded() {
            // 操作と確認：在庫数 1,000,010 に -5 を加えても上限を超えるので、STOCK_QUANTITY_EXCEEDED を選ぶ
            assertBusinessError(() -> {
                throw JooqStockItemRepository.failureOf(-5, true, () -> Optional.of(MAX_MANUAL_QUANTITY + 10));
            }, InventoryErrorCode.STOCK_QUANTITY_EXCEEDED);
        }

        @Test
        @DisplayName("運営者の減らす調整が更新されず、今の在庫数に加えても上限を超えないなら、負になるエラー")
        void negativeManualDeltaWithinLimitIsInsufficient() {
            // 操作と確認：在庫数 3 に -5 を加えると負になるので、INSUFFICIENT_STOCK を選ぶ
            assertBusinessError(() -> {
                throw JooqStockItemRepository.failureOf(-5, true, () -> Optional.of(3));
            }, InventoryErrorCode.INSUFFICIENT_STOCK);
        }

        @Test
        @DisplayName("運営者でない減らす調整は、在庫数が上限を超えていても上限を当てず、在庫があれば負になるエラー")
        void negativeNonManualDeltaIgnoresLimit() {
            // 操作と確認：上限を当てない調整は、在庫があれば INSUFFICIENT_STOCK を選ぶ
            assertBusinessError(() -> {
                throw JooqStockItemRepository.failureOf(-5, false, () -> Optional.of(MAX_MANUAL_QUANTITY + 10));
            }, InventoryErrorCode.INSUFFICIENT_STOCK);
        }

        @Test
        @DisplayName("減らす調整が更新されず、在庫がなければ見つからない")
        void negativeDeltaWithoutStockIsNotFound() {
            // 操作と確認：NOT_FOUND を選ぶ
            assertBusinessError(() -> {
                throw JooqStockItemRepository.failureOf(-1, true, Optional::empty);
            }, CommonErrorCode.NOT_FOUND);
        }
    }
}
