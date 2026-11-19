package com.ecsite.module.cart.api;

import com.ecsite.module.cart.api.model.CartContents;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.security.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * カートの内容の API の統合テスト
 *
 * <p>注文の確定でカートから品目を除く操作の、トランザクションの前提を確認する。
 * トランザクションの外から呼ぶので、データはコミットする。テストごとの会員のカートを使う。
 */
@IntegrationTest
class CartContentsApiTest {

    // ============================== Bean ==============================

    @Autowired
    CartContentsApi api;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("トランザクションの前提")
    class TransactionPrecondition {

        @Test
        @DisplayName("品目の除去は、トランザクションの外から呼ぶと失敗し、品目は残る")
        void removeItemsFailsOutsideTransaction() throws Exception {
            // 準備：テストごとの会員のカートに、在庫のあるバリアントを 1 個入れる
            var catalog = new CatalogTestData(mockMvc, clock);
            var variantId = new InventoryTestData(stockItemRepository, clock)
                    .stockedVariant(catalog, catalog.taxCategory("標準 " + UUID.randomUUID(), "10"), "商品", 5);
            var member = TestTokens.member().subject(UUID.randomUUID()).bearer();
            var orders = new OrderTestData(mockMvc);
            var cartId = orders.cart(member);
            orders.add(cartId, variantId, 1, member);

            // 操作と確認：トランザクションの外で除くと失敗する
            assertThatThrownBy(() -> api.removeItems(cartId, List.of(variantId)))
                    .isInstanceOf(IllegalTransactionStateException.class);

            // 確認：品目は除かれずに残る
            assertThat(api.find(cartId).orElseThrow().items())
                    .extracting(CartContents.Item::variantId)
                    .containsExactly(variantId);
        }
    }
}
