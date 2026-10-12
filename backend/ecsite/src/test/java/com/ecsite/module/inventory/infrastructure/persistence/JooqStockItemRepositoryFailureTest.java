package com.ecsite.module.inventory.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.inventory.domain.stock.InventoryErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;

/**
 * 在庫の調整が更新されなかったときの、エラーの選び方の単体テスト。<br />
 * 更新の後に在庫が作られる並びは統合テストで再現しにくいので、選び方だけを確かめる。
 */
class JooqStockItemRepositoryFailureTest {

    @Test
    @DisplayName("増やす調整が更新されなければ、更新の後に在庫が作られていても見つからない（負になるエラーにしない）")
    void positiveDeltaIsNotFoundEvenIfStockAppearsLater() {
        assertBusinessError(() -> {
            throw JooqStockItemRepository.failureOf(1, () -> true);
        }, CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("AC-43 減らす調整が更新されず、在庫があれば負になるエラー")
    void negativeDeltaWithStockIsInsufficient() {
        assertBusinessError(() -> {
            throw JooqStockItemRepository.failureOf(-1, () -> true);
        }, InventoryErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    @DisplayName("減らす調整が更新されず、在庫がなければ見つからない")
    void negativeDeltaWithoutStockIsNotFound() {
        assertBusinessError(() -> {
            throw JooqStockItemRepository.failureOf(-1, () -> false);
        }, CommonErrorCode.NOT_FOUND);
    }
}
