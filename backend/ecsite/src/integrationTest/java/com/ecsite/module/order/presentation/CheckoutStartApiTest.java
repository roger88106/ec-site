package com.ecsite.module.order.presentation;

import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.OrderTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.module.order.presentation.CheckoutStartFixture.RESERVATION_TTL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きの開始と表示の受け入れテスト
 *
 * <p>支払待ちの注文がない会員の、引当、進み直し、注文できないカート、表示を確認する。
 * 支払待ちの注文があるときの確認は {@link CheckoutPendingOrderConfirmationApiTest} が確認する。
 * 支払待ちの注文の上限は {@link CheckoutPendingOrderLimitApiTest} が確認する。
 * カートの変更のイベントによる引当の解除は、コミットが必要なので {@link CheckoutCartChangeApiTest} が確認する。
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class CheckoutStartApiTest {

    // ============================== 定数 ==============================

    /**
     * 進み直すまでの時間（引当の期限の前）
     */
    private static final Duration RESTART_AFTER = Duration.ofMinutes(10);

    /**
     * 今から、将来の税区分の税率が始まるまでの時間
     */
    private static final Duration RATE_START_AFTER = Duration.ofHours(1);

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    StockReservationApi stockReservationApi;

    @Autowired
    ApplicationContext applicationContext;

    /**
     * 注文手続きの開始の準備と確認の補助
     */
    private CheckoutStartFixture fixture;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    /**
     * テストの会員のカート ID
     */
    private UUID cartId;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new CheckoutStartFixture(applicationContext);
        orders = fixture.orders();
        cartId = fixture.cartId();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("引当")
    class Reservation {

        @Test
        @DisplayName("AC-82 注文手続きに進むと、全品目をカートの数量だけ引き当てる")
        void reservesAllItems() throws Exception {
            // 準備：在庫 3 の品目を 2 個、在庫 5 の品目を 1 個カートに入れる
            var shirt = fixture.stockedVariant(3);
            var cap = fixture.stockedVariant(5);
            orders.add(cartId, shirt, 2);
            orders.add(cartId, cap, 1);

            // 操作：注文手続きに進む
            fixture.startCheckout(cartId);

            // 確認：販売可能数が、カートの数量だけ減っている
            assertThat(stockReservationApi.sellableQuantities(List.of(shirt, cap), null))
                    .containsEntry(shirt, 1).containsEntry(cap, 4);
        }

        @Test
        @DisplayName("AC-82 注文手続きの期限は、進んだ時点から 15 分後になる")
        void expiresInFifteenMinutes() throws Exception {
            // 準備：在庫のある品目をカートに入れる
            orders.add(cartId, fixture.stockedVariant(3), 2);
            // 商品の公開が時計を進めるので、進む時点の時刻を読む
            var startedAt = clock.instant();

            // 操作：注文手続きに進む
            var checkoutId = fixture.startCheckout(cartId);

            // 確認：期限が進んだ時点から 15 分後で、まだ注文になっていない
            assertCheckoutExpiresAt(checkoutId, startedAt.plus(RESERVATION_TTL));
        }

        @Test
        @DisplayName("AC-82 1 品目でも引き当てられなければ 409 で、どれも引き当てない")
        void reservesNothingWhenShort() throws Exception {
            // 準備：足りる品目と、ほかの消費者の引当で足りなくなる品目をカートに入れる
            var plenty = fixture.stockedVariant(5);
            reserveShortCart(plenty);

            // 操作と確認：在庫の不足で拒否される
            mockMvc.perform(post("/api/store/carts/" + cartId + "/checkouts"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"));

            // 確認：足りる品目も引き当てていない
            assertThat(fixture.sellable(plenty)).isEqualTo(5);
        }

        @Test
        @DisplayName("AC-82 引き当てられなかった品目は、カートの表示で示す")
        void showsShortItemInCart() throws Exception {
            // 準備：2 番目の品目が足りないカートで、注文手続きに進んで拒否される
            reserveShortCart(fixture.stockedVariant(5));
            mockMvc.perform(post("/api/store/carts/" + cartId + "/checkouts")).andExpect(status().isConflict());

            // 操作と確認：カートの表示で、2 番目の品目が在庫不足と示される
            mockMvc.perform(get("/api/store/carts/" + cartId))
                    .andExpect(jsonPath("$.items[1].unpurchasableReason").value("INSUFFICIENT_STOCK"));
        }
    }

    @Nested
    @DisplayName("進み直し")
    class Restart {

        @Test
        @DisplayName("AC-84 進み直すと、前の注文手続きの引当を解いて、その時点のカートで引き当て直す")
        void replacesReservationOnRestart() throws Exception {
            // 準備：在庫 3 の品目を 2 個入れて注文手続きに進み、10 分後にする
            var variant = fixture.stockedVariant(3);
            orders.add(cartId, variant, 2);
            fixture.startCheckout(cartId);
            clock.setInstant(clock.instant().plus(RESTART_AFTER));

            // 操作：進み直す
            fixture.startCheckout(cartId);

            // 確認：引当は、今の注文手続きの 2 個だけ
            assertThat(fixture.sellable(variant)).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-84 進み直すと、期限は進み直した時点から 15 分後になる")
        void resetsExpiryOnRestart() throws Exception {
            // 準備：注文手続きに進み、10 分後にする
            orders.add(cartId, fixture.stockedVariant(3), 2);
            fixture.startCheckout(cartId);
            var restartedAt = clock.instant().plus(RESTART_AFTER);
            clock.setInstant(restartedAt);

            // 操作：進み直す
            var second = fixture.startCheckout(cartId);

            // 確認：期限が進み直した時点から 15 分後
            mockMvc.perform(get("/api/store/checkouts/" + second))
                    .andExpect(jsonPath("$.expiresAt").value(restartedAt.plus(RESERVATION_TTL).toString()));
        }
    }

    @Nested
    @DisplayName("期限の経過")
    class Expiry {

        @Test
        @DisplayName("AC-84 進んでから 15 分が経過すると、注文手続きの引当は解ける")
        void releasesReservationAfterFifteenMinutes() throws Exception {
            // 準備：在庫 3 の品目を 2 個入れて注文手続きに進む
            var variant = fixture.stockedVariant(3);
            orders.add(cartId, variant, 2);
            // 商品の公開が時計を進めるので、進む時点の時刻を読む
            var startedAt = clock.instant();
            fixture.startCheckout(cartId);

            // 確認（前提）：期限の 1 秒前は、2 個を引き当てている
            clock.setInstant(startedAt.plus(RESERVATION_TTL).minusSeconds(1));
            assertThat(fixture.sellable(variant)).isEqualTo(1);

            // 操作：期限の時刻にする
            clock.setInstant(startedAt.plus(RESERVATION_TTL));

            // 確認：引当は数えず、販売可能数が在庫数に戻る
            assertThat(fixture.sellable(variant)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("注文できないカート")
    class UnorderableCart {

        @Test
        @DisplayName("AC-88 空のカートは 400 で注文手続きに進めない")
        void rejectsEmptyCart() throws Exception {
            // 操作と確認：品目のないカートは拒否される
            expectCartNotOrderable();
        }

        @Test
        @DisplayName("AC-88 購入できない品目（販売停止）を含むカートは 400 で注文手続きに進めない")
        void rejectsCartWithUnpurchasableItem() throws Exception {
            // 準備：カートに入れた後に販売停止にした品目
            var product = fixture.catalog().product(fixture.standard(), "販売停止にする", 1000, "色").variant("白").publish();
            fixture.inventory().stock(product.variantIds.getFirst(), product.id, 3);
            orders.add(cartId, product.variantIds.getFirst(), 1);
            product.suspend(0);

            // 操作と確認：購入できない品目を含むカートは拒否される
            expectCartNotOrderable();
        }

        @Test
        @DisplayName("AC-88 購入できない品目（非公開）を含むカートは 400 で注文手続きに進めない")
        void rejectsCartWithUnpublishedItem() throws Exception {
            // 準備：カートに入れた後に非公開にした商品
            var product = fixture.catalog().product(fixture.standard(), "非公開にする", 1000).publish();
            fixture.inventory().stock(product.variantIds.getFirst(), product.id, 3);
            orders.add(cartId, product.variantIds.getFirst(), 1);
            product.unpublish();

            // 操作と確認：購入できない品目を含むカートは拒否される
            expectCartNotOrderable();
        }

        @Test
        @DisplayName("AC-88 税区分に有効な税率のない品目を含むカートは 400 で注文手続きに進めない")
        void rejectsCartWithItemWithoutRate() throws Exception {
            // 準備：税率の開始の時刻に商品を登録してカートに入れた後、時計を税率の開始の前に戻す
            var rateStart = fixture.catalog().taxCategoryStartingAfter("10", RATE_START_AFTER);
            var product = fixture.catalog().product(rateStart.taxCategoryId(), "税率の前の商品", 1000).publish();
            fixture.inventory().stock(product.variantIds.getFirst(), product.id, 3);
            orders.add(cartId, product.variantIds.getFirst(), 1);
            fixture.catalog().rewindBeforeRateStart(rateStart.startAt(), applicationContext);

            // 操作と確認：購入できない品目を含むカートは拒否される
            expectCartNotOrderable();
        }

        @Test
        @DisplayName("AC-88 購入できない品目があって拒否したときは、注文手続きを作らず、どの品目も引き当てない")
        void reservesNothingWhenRejected() throws Exception {
            // 準備：足りる品目と、カートに入れた後に販売停止にした品目
            var available = fixture.stockedVariant(3);
            orders.add(cartId, available, 1);
            var product = fixture.catalog().product(fixture.standard(), "販売停止にする", 1000, "色").variant("白").publish();
            fixture.inventory().stock(product.variantIds.getFirst(), product.id, 3);
            orders.add(cartId, product.variantIds.getFirst(), 1);
            product.suspend(0);

            // 操作：注文手続きに進んで拒否される
            expectCartNotOrderable();

            // 確認：注文手続きがなく、足りる品目の販売可能数が在庫数のまま
            assertThat(fixture.checkoutCount()).isZero();
            assertThat(fixture.sellable(available)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("注文手続き中の購入の可否の変化")
    class PurchasabilityChanged {

        @Test
        @DisplayName("AC-80、85 注文手続きの後に商品が非公開になると、表示の品目は「表示されない」を理由に購入できない")
        void showsNotShown() throws Exception {
            // 準備：品目で注文手続きに進み、その後に商品を非公開にする
            var product = fixture.catalog().product(fixture.standard(), "非公開にする", 1000).publish();
            fixture.inventory().stock(product.variantIds.getFirst(), product.id, 3);
            orders.add(cartId, product.variantIds.getFirst(), 1);
            var checkoutId = fixture.startCheckout(cartId);
            product.unpublish();

            // 操作と確認：品目は購入できず、理由が NOT_SHOWN
            expectUnpurchasable(checkoutId, "NOT_SHOWN");
        }

        @Test
        @DisplayName("AC-80、85 注文手続きの後にバリアントが販売停止になると、表示の品目は「販売停止」を理由に購入できない")
        void showsSalesSuspended() throws Exception {
            // 準備：品目で注文手続きに進み、その後にバリアントを販売停止にする
            var product = fixture.catalog().product(fixture.standard(), "販売停止にする", 1000, "色").variant("白").publish();
            fixture.inventory().stock(product.variantIds.getFirst(), product.id, 3);
            orders.add(cartId, product.variantIds.getFirst(), 1);
            var checkoutId = fixture.startCheckout(cartId);
            product.suspend(0);

            // 操作と確認：品目は購入できず、理由が SALES_SUSPENDED
            expectUnpurchasable(checkoutId, "SALES_SUSPENDED");
        }

        @Test
        @DisplayName("AC-80、85 注文手続きの後に在庫数が 0 になると、表示の品目は「在庫切れ」を理由に購入できない")
        void showsOutOfStock() throws Exception {
            // 準備：在庫 1 の品目を 1 個入れて注文手続きに進み、その後に在庫数を 0 にする
            var variant = fixture.stockedVariant(1);
            orders.add(cartId, variant, 1);
            var checkoutId = fixture.startCheckout(cartId);
            fixture.inventory().adjust(variant, -1);

            // 操作と確認：品目は購入できず、理由が OUT_OF_STOCK
            expectUnpurchasable(checkoutId, "OUT_OF_STOCK");
        }

        @Test
        @DisplayName("AC-80、85 注文手続きの後に購入できなくなった品目は、表示の合計と総額に含めない")
        void excludesUnpurchasableFromAmounts() throws Exception {
            // 準備：1,100 円（税込）の品目 2 つで注文手続きに進み、その後に 2 つ目を販売停止にする
            var available = fixture.stockedVariant(3);
            orders.add(cartId, available, 1);
            var product = fixture.catalog().product(fixture.standard(), "販売停止にする", 1000, "色").variant("白").publish();
            fixture.inventory().stock(product.variantIds.getFirst(), product.id, 3);
            orders.add(cartId, product.variantIds.getFirst(), 1);
            var checkoutId = fixture.startCheckout(cartId);
            product.suspend(0);

            // 操作と確認：合計は購入できる 1 つ目の 1,100 円だけで、総額はそれに送料を足した額
            mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.amounts.itemsTotal").value(1100))
                    .andExpect(jsonPath("$.amounts.total").value(1100 + OrderTestData.SHIPPING_FEE));
        }

        /**
         * 注文手続きの表示で、1 つだけの品目が、指定の理由で購入できないことを確認する。
         *
         * <p>確認すること：
         * <ul>
         *   <li>品目が 1 つ</li>
         *   <li>品目が購入できない</li>
         *   <li>購入できない理由</li>
         *   <li>商品の合計が 0</li>
         * </ul>
         *
         * @param checkoutId 注文手続き ID
         * @param reason     購入できない理由
         * @throws Exception API の呼び出しに失敗したとき、または確認が通らないとき
         */
        private void expectUnpurchasable(UUID checkoutId, String reason) throws Exception {
            mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].purchasable").value(false))
                    .andExpect(jsonPath("$.items[0].unpurchasableReason").value(reason))
                    .andExpect(jsonPath("$.amounts.itemsTotal").value(0));
        }
    }

    @Nested
    @DisplayName("注文手続きの表示")
    class View {

        @Test
        @DisplayName("AC-85 注文手続きの表示は、カートの品目と、品目の合計・送料・総額を返す")
        void showsItemsAndAmounts() throws Exception {
            // 準備：1,100 円（税込）の品目 1 個で注文手続きに進む
            var variant = fixture.stockedVariant(1);
            orders.add(cartId, variant, 1);
            var checkoutId = fixture.startCheckout(cartId);

            // 操作と確認：品目と、送料 1,000 円を足した総額を返す
            mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                    .andExpect(jsonPath("$.items[0].variantId").value(variant.toString()))
                    .andExpect(jsonPath("$.amounts.itemsTotal").value(1100))
                    .andExpect(jsonPath("$.amounts.shippingFee").value(OrderTestData.SHIPPING_FEE))
                    .andExpect(jsonPath("$.amounts.total").value(1100 + OrderTestData.SHIPPING_FEE));
        }

        @Test
        @DisplayName("AC-85 注文手続きの表示は、自分の引当を販売可能数から除くので、在庫の全てを引き当てた品目も購入できる")
        void excludesOwnReservationInCheckout() throws Exception {
            // 準備：在庫 1 の品目を 1 個入れて注文手続きに進む（在庫の全てを自分が引き当てる）
            orders.add(cartId, fixture.stockedVariant(1), 1);
            var checkoutId = fixture.startCheckout(cartId);

            // 操作と確認：品目は購入できる
            mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                    .andExpect(jsonPath("$.items[0].purchasable").value(true));
        }

        @Test
        @DisplayName("AC-85 カートの表示でも、注文手続き中の自分の引当を除いて判定する")
        void excludesOwnReservationInCart() throws Exception {
            // 準備：在庫 1 の品目を 1 個入れて注文手続きに進む（在庫の全てを自分が引き当てる）
            orders.add(cartId, fixture.stockedVariant(1), 1);
            fixture.startCheckout(cartId);

            // 操作と確認：カートの品目は購入でき、カートは注文できる
            mockMvc.perform(get("/api/store/carts/" + cartId))
                    .andExpect(jsonPath("$.items[0].purchasable").value(true))
                    .andExpect(jsonPath("$.orderable").value(true));
        }

        @Test
        @DisplayName("AC-85 注文手続きの表示は、税率ごとの消費税額を、税率の高い順に返す（送料は 10% の対象）")
        void showsTaxAmountPerRate() throws Exception {
            // 準備：10% の品目（1,100 円）と 8% の品目（1,080 円）を 1 個ずつ入れて注文手続きに進む
            var reduced = fixture.catalog().taxCategory("軽減", "8");
            var standardVariant = fixture.stockedVariant(1);
            var reducedProduct = fixture.catalog().product(reduced, "軽減の商品", 1000).publish();
            var reducedVariant = reducedProduct.variantIds.getFirst();
            fixture.inventory().stock(reducedVariant, reducedProduct.id, 1);
            orders.add(cartId, standardVariant, 1);
            orders.add(cartId, reducedVariant, 1);
            var checkoutId = fixture.startCheckout(cartId);

            // 操作と確認：税率ごとの対象額と消費税額
            // 10%：1,100 円 + 送料 1,000 円 = 2,100 円 → 2,100 × 10 ÷ 110 = 190.9 → 190 円
            // 8%：1,080 円 → 1,080 × 8 ÷ 108 = 80 円
            mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                    .andExpect(jsonPath("$.amounts.itemsTotal").value(2180))
                    .andExpect(jsonPath("$.amounts.total").value(3180))
                    .andExpect(jsonPath("$.amounts.taxes.length()").value(2))
                    .andExpect(jsonPath("$.amounts.taxes[0].ratePercent").value(10))
                    .andExpect(jsonPath("$.amounts.taxes[0].taxableAmount").value(2100))
                    .andExpect(jsonPath("$.amounts.taxes[0].taxAmount").value(190))
                    .andExpect(jsonPath("$.amounts.taxes[1].ratePercent").value(8))
                    .andExpect(jsonPath("$.amounts.taxes[1].taxableAmount").value(1080))
                    .andExpect(jsonPath("$.amounts.taxes[1].taxAmount").value(80));
        }
    }

    @Nested
    @DisplayName("存在しないもの")
    class NotFound {

        @Test
        @DisplayName("存在しないカートからは、注文手続きに進めず 404")
        void rejectsUnknownCart() throws Exception {
            // 操作と確認：ない ID のカートは見つからない
            mockMvc.perform(post("/api/store/carts/" + UUID.randomUUID() + "/checkouts")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("存在しない注文手続きの表示は 404")
        void rejectsUnknownCheckout() throws Exception {
            // 操作と確認：ない ID の注文手続きは見つからない
            mockMvc.perform(get("/api/store/checkouts/" + UUID.randomUUID())).andExpect(status().isNotFound());
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 始めた注文手続きの表示で、期限を確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 200</li>
     *   <li>ID がこの注文手続き</li>
     *   <li>期限が、指定した時刻</li>
     *   <li>注文 ID がない（まだ注文になっていない）</li>
     * </ul>
     *
     * @param checkoutId 注文手続き ID（実行ごとに作るので、定数にできない）
     * @param expiresAt  期待する期限（商品の公開が時計を進めるので、呼ぶ側が進んだ時点の時刻から求める）
     */
    private void assertCheckoutExpiresAt(UUID checkoutId, Instant expiresAt) throws Exception {
        mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(checkoutId.toString()))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
                .andExpect(jsonPath("$.orderId").value(nullValue()));
    }

    /**
     * 注文手続きに進むと、注文できないカートとして 400 になることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 400</li>
     *   <li>エラーコードが {@code CART_NOT_ORDERABLE}</li>
     * </ul>
     */
    private void expectCartNotOrderable() throws Exception {
        mockMvc.perform(post("/api/store/carts/" + cartId + "/checkouts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CART_NOT_ORDERABLE"));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 足りる品目を 1 個と、在庫 2 のうち 1 個をほかの消費者が引き当てた品目を 2 個、この順にカートに入れる。
     *
     * <p>2 番目の品目は、販売可能数が 1 なので引き当てられない。
     *
     * @param plenty 足りる品目のバリアント ID（在庫は呼ぶ側で用意する）
     */
    private void reserveShortCart(UUID plenty) throws Exception {
        var scarce = fixture.stockedVariant(2);
        orders.add(cartId, plenty, 1);
        orders.add(cartId, scarce, 2);
        stockReservationApi.reserve(UUID.randomUUID(), Map.of(scarce, 1), clock.instant().plus(RESERVATION_TTL));
    }
}
