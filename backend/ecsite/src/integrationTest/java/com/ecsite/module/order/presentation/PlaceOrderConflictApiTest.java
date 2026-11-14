package com.ecsite.module.order.presentation;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.ecsite.module.order.presentation.PlaceOrderFixture.TOTAL;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きの後の変化で注文を拒否する受け入れテスト
 *
 * <p>確認した総額と注文時点の総額の違い、注文手続きの後のカートの変更を確認する。
 * 支払待ちの注文の確認がカートの変更の判定より先であることは {@code PlaceOrderPendingConfirmationApiTest} が確認する。
 *
 * <p>準備は {@link PlaceOrderFixture}（T シャツ 2 枚のカート）。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class PlaceOrderConflictApiTest {

    // ============================== 定数 ==============================

    /**
     * 注文手続きの後に変える、T シャツの税抜単価
     */
    private static final long CHANGED_PRICE_EXCL_TAX = 3000;

    /**
     * 価格を変えた後の、T シャツ 2 枚の注文の総額
     *
     * <p>税込単価 3,300 円 × 2 枚と、送料 1,000 円。
     */
    private static final long CHANGED_TOTAL = 7600;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationContext applicationContext;

    /**
     * 共有の準備
     */
    private PlaceOrderFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(PlaceOrderFixture.NOW);
        fixture = PlaceOrderFixture.prepare(applicationContext);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("総額の変化")
    class TotalChanged {

        @Test
        @DisplayName("AC-89 確認した総額と、注文時点の総額が違えば、総額の変化を示す 409")
        void rejectsChangedTotal() throws Exception {
            // 準備：注文手続きの後に T シャツの価格を変える
            var checkoutId = checkoutWithChangedPrice();

            // 操作と確認：変える前の総額で注文すると 409 で、エラーコードが TOTAL_AMOUNT_CHANGED
            fixture.place(checkoutId, TOTAL)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("TOTAL_AMOUNT_CHANGED"));
        }

        @Test
        @DisplayName("AC-89 総額が違って拒否したときは、注文を作らない")
        void createsNoOrderWhenRejected() throws Exception {
            // 準備：注文手続きの後に T シャツの価格を変える
            var checkoutId = checkoutWithChangedPrice();

            // 操作：変える前の総額で注文して拒否される
            fixture.place(checkoutId, TOTAL);

            // 確認：注文手続きから作った注文がない
            fixture.expectNotPlaced(checkoutId);
        }

        @Test
        @DisplayName("AC-89 総額の変化で拒否された後、注文時点の総額を送り直せば成立する")
        void placesWithCurrentTotal() throws Exception {
            // 準備：価格を変え、変える前の総額で一度拒否される
            var checkoutId = checkoutWithChangedPrice();

            // 確認（前提）：変える前の総額は 409
            fixture.place(checkoutId, TOTAL).andExpect(status().isConflict());

            // 操作と確認：変えた後の総額で注文すると 201
            fixture.place(checkoutId, CHANGED_TOTAL).andExpect(status().isCreated());
        }

        /**
         * 注文手続きを始めた後に、T シャツの税抜単価を変える。
         *
         * @return 注文手続き ID
         * @throws Exception API の呼び出しに失敗したとき
         */
        private UUID checkoutWithChangedPrice() throws Exception {
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            fixture.shirt.changePrice(fixture.standard, CHANGED_PRICE_EXCL_TAX);
            return checkoutId;
        }
    }

    @Nested
    @DisplayName("カートの変化")
    class CartChanged {

        @Test
        @DisplayName("注文手続きの後にカートが変われば、カートの変化を示す 409")
        void rejectsChangedCart() throws Exception {
            // 準備：注文手続きの後に、カートの T シャツを 1 枚に減らす
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            fixture.changeShirtQuantity(1);

            // 操作と確認：409 で、エラーコードが CART_CHANGED
            fixture.place(checkoutId, TOTAL)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CART_CHANGED"));
        }
    }
}
