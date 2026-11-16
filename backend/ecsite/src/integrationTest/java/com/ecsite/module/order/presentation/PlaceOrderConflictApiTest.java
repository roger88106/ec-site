package com.ecsite.module.order.presentation;

import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.ecsite.module.order.presentation.PlaceOrderFixture.SHIRT_QUANTITY;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.SHIRT_STOCK;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.TOTAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きの後の変化で注文を拒否する受け入れテスト
 *
 * <p>確認した総額と注文時点の総額の違い、注文手続きの後のカートの変更、注文手続きの後に品目が購入できなくなること
 * （販売停止・非公開・税率なし）を確認する。在庫だけが足りないときの 409 は {@code PlaceOrderStockApiTest} が確認する。
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

    /**
     * T シャツ 2 枚と帽子 1 個の注文の総額
     *
     * <p>T シャツ 2 枚の総額 6,940 円に、帽子の税込単価 1,100 円を加えた額。
     */
    private static final long TOTAL_WITH_CAP = 8040;

    /**
     * 今から、将来の税区分の税率が始まるまでの時間
     */
    private static final Duration RATE_START_AFTER = Duration.ofHours(1);

    // ============================== Bean ==============================

    @Autowired
    ApplicationContext applicationContext;

    /**
     * 在庫の引当の API（拒否の後に、注文手続きの引当が残ることを販売可能数で確認する）
     */
    @Autowired
    StockReservationApi stockReservationApi;

    /**
     * 共有の準備
     */
    private PlaceOrderFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
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

        @Test
        @DisplayName("AC-89 下限の 0 円の確認した総額は、入力として受け付け、総額の変化を示す 409")
        void acceptsZeroTotalAsInput() throws Exception {
            // 準備：注文手続きを始める
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);

            // 操作と確認：0 円は入力の誤りでなく、注文時点の総額と違うので 409
            fixture.place(checkoutId, 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("TOTAL_AMOUNT_CHANGED"));
        }

        @Test
        @DisplayName("AC-89 下限を 1 円下回る -1 円の確認した総額は、入力の誤りの 400 で総額の欄を示し、注文を作らない")
        void rejectsNegativeTotal() throws Exception {
            // 準備：注文手続きを始める
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);

            // 操作と確認：-1 円は 400 で、確認した総額の欄を示す
            fixture.place(checkoutId, -1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("expectedTotal"));

            // 確認：注文がない
            fixture.expectNotPlaced(checkoutId);
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

        @Test
        @DisplayName("注文手続きの後にカートが変わって拒否したときは、注文を作らず、在庫数が変わらない")
        void keepsEverythingWhenCartChanged() throws Exception {
            // 準備：注文手続きの後に、カートの T シャツを 1 枚に減らす
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            fixture.changeShirtQuantity(1);

            // 操作：注文して拒否される
            fixture.place(checkoutId, TOTAL).andExpect(status().isConflict());

            // 確認：注文がなく、在庫数が注文の前のまま（引当は、カートの変更で解ける）
            assertNotWritten(checkoutId);
        }
    }

    @Nested
    @DisplayName("購入の可否の変化")
    class PurchasabilityChanged {

        @Test
        @DisplayName("AC-88 注文手続きの後にバリアントが販売停止になると、注文は購入できない品目を示す 400 で、注文を作らず、在庫とカートが変わらない")
        void rejectsSuspendedAfterCheckout() throws Exception {
            // 準備：T シャツ 2 枚で注文手続きに進み、その後に運営者が T シャツを販売停止にする
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            fixture.shirt.suspend(0);

            // 操作と確認：確認した総額で注文すると 400 で、エラーコードが CART_NOT_ORDERABLE
            fixture.place(checkoutId, TOTAL)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CART_NOT_ORDERABLE"));

            // 確認：注文がなく、在庫数が注文の前のまま
            assertNotWritten(checkoutId);

            // 確認：注文手続きの引当は残る（販売可能数は、在庫数からカートの数量を除いた数）
            assertThat(stockReservationApi.sellableQuantities(List.of(fixture.shirtVariant()), null))
                    .containsEntry(fixture.shirtVariant(), SHIRT_STOCK - SHIRT_QUANTITY);

            // 確認：カートの T シャツは残り、販売停止を理由に購入できない
            fixture.mockMvc.perform(get("/api/store/carts/" + fixture.cartId))
                    .andExpect(jsonPath("$.items[0].quantity").value(SHIRT_QUANTITY))
                    .andExpect(jsonPath("$.items[0].unpurchasableReason").value("SALES_SUSPENDED"))
                    .andExpect(jsonPath("$.orderable").value(false));
        }

        @Test
        @DisplayName("AC-88 注文手続きの後に商品が非公開になると、注文は購入できない品目を示す 400")
        void rejectsUnpublishedAfterCheckout() throws Exception {
            // 準備：注文手続きの後に T シャツを非公開にする
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            fixture.shirt.unpublish();

            // 操作と確認：400 で、エラーコードが CART_NOT_ORDERABLE
            fixture.place(checkoutId, TOTAL)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CART_NOT_ORDERABLE"));
        }

        @Test
        @DisplayName("AC-88 在庫切れの品目と販売停止の品目が混ざれば、在庫の不足の 409 でなく、購入できない品目を示す 400")
        void rejectsMixedReasonsAsNotOrderable() throws Exception {
            // 準備：T シャツと帽子で注文手続きに進み、その後に T シャツの在庫を 0 にし、帽子を販売停止にする
            var cap = fixture.catalog.product(fixture.standard, "帽子", 1000, "色").variant("黒").publish();
            fixture.inventory.stock(cap.variantIds.getFirst(), cap.id, 1);
            fixture.orders.add(fixture.cartId, cap.variantIds.getFirst(), 1);
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            fixture.inventory.adjust(fixture.shirtVariant(), -SHIRT_STOCK);
            cap.suspend(0);

            // 操作と確認：400 で、エラーコードが CART_NOT_ORDERABLE
            fixture.place(checkoutId, TOTAL_WITH_CAP)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CART_NOT_ORDERABLE"));
        }

        @Test
        @DisplayName("AC-88 注文の時点で商品の税区分に有効な税率がなければ、注文は購入できない品目を示す 400")
        void rejectsWithoutRateAtOrder() throws Exception {
            // 準備：税率の開始の時刻に帽子を登録してカートに入れ、注文手続きに進んだ後、時計を税率の開始の前に戻す
            var rateStart = fixture.catalog.taxCategoryStartingAfter("10", RATE_START_AFTER);
            var cap = fixture.catalog.product(rateStart.taxCategoryId(), "帽子", 1000, "色").variant("黒").publish();
            fixture.inventory.stock(cap.variantIds.getFirst(), cap.id, 1);
            fixture.orders.add(fixture.cartId, cap.variantIds.getFirst(), 1);
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            fixture.catalog.rewindBeforeRateStart(rateStart.startAt(), applicationContext);

            // 操作と確認：400 で、エラーコードが CART_NOT_ORDERABLE
            fixture.place(checkoutId, TOTAL_WITH_CAP)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CART_NOT_ORDERABLE"));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 拒否された注文の前後で、注文がなく、在庫数が変わっていないことを確認する。
     *
     * <p>支払は注文に付くので、注文がなければ支払もない。確認すること：
     * <ul>
     *   <li>注文手続きから作った注文がない</li>
     *   <li>注文手続きに注文 ID がない</li>
     *   <li>T シャツの在庫数が準備の時点のまま</li>
     * </ul>
     *
     * @param checkoutId 注文手続き ID（実行ごとに作るので、定数にできない）
     * @throws Exception API の呼び出しに失敗したとき、または確認が通らないとき
     */
    private void assertNotWritten(UUID checkoutId) throws Exception {
        fixture.expectNotPlaced(checkoutId);
        fixture.mockMvc.perform(get("/api/store/checkouts/" + checkoutId)).andExpect(jsonPath("$.orderId").isEmpty());
        fixture.expectQuantity(SHIRT_STOCK);
    }
}
