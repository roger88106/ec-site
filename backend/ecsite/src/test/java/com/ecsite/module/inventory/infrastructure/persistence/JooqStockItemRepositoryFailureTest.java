package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.inventory.domain.InventoryErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;

/**
 * 在庫の調整が更新されなかったときの、エラーの選び方の単体テスト。<br />
 * 更新の後に在庫が作られる並びは統合テストで再現しにくいので、選び方だけを確認する。
 */
class JooqStockItemRepositoryFailureTest {

    @Test
    @DisplayName("注文のキャンセルで増やす調整が更新されなければ、更新の後に在庫が作られていても見つからない（負になるエラーにしない）")
    void positiveDeltaIsNotFoundEvenIfStockAppearsLater() {
        assertBusinessError(() -> {
            throw JooqStockItemRepository.failureOf(1, false, () -> Optional.of(0));
        }, CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("AC-42 運営者の増やす調整が更新されず、今の在庫数に加えると上限を超えるなら、上限を超えるエラー")
    void positiveManualDeltaOverLimitIsExceeded() {
        assertBusinessError(() -> {
            throw JooqStockItemRepository.failureOf(1, true, () -> Optional.of(1_000_000));
        }, InventoryErrorCode.STOCK_QUANTITY_EXCEEDED);
    }

    @Test
    @DisplayName("運営者の増やす調整が更新されず、更新の後に作られた在庫（在庫数 0）なら見つからない（上限を超えるエラーにしない）")
    void positiveManualDeltaIsNotFoundIfStockAppearsLater() {
        assertBusinessError(() -> {
            throw JooqStockItemRepository.failureOf(1_000_000, true, () -> Optional.of(0));
        }, CommonErrorCode.NOT_FOUND);
        assertBusinessError(() -> {
            throw JooqStockItemRepository.failureOf(1, true, Optional::empty);
        }, CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("AC-43 減らす調整が更新されず、在庫があれば負になるエラー")
    void negativeDeltaWithStockIsInsufficient() {
        assertBusinessError(() -> {
            throw JooqStockItemRepository.failureOf(-1, true, () -> Optional.of(0));
        }, InventoryErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    @DisplayName("減らす調整が更新されず、在庫がなければ見つからない")
    void negativeDeltaWithoutStockIsNotFound() {
        assertBusinessError(() -> {
            throw JooqStockItemRepository.failureOf(-1, true, Optional::empty);
        }, CommonErrorCode.NOT_FOUND);
    }
}
