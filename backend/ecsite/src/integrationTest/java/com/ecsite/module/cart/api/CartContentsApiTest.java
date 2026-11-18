package com.ecsite.module.cart.api;

import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * カートの内容の API の統合テスト
 *
 * <p>注文の確定でカートから品目を除く操作の、トランザクションの前提を確認する。
 */
@IntegrationTest
class CartContentsApiTest {

    // ============================== Bean ==============================

    @Autowired
    CartContentsApi api;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("トランザクションの前提")
    class TransactionPrecondition {

        @Test
        @DisplayName("品目の除去は、トランザクションの外から呼ぶと失敗する")
        void removeItemsFailsOutsideTransaction() {
            // 操作と確認：トランザクションの外で除くと失敗する
            assertThatThrownBy(() -> api.removeItems(UUID.randomUUID(), List.of(UUID.randomUUID())))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }
    }
}
