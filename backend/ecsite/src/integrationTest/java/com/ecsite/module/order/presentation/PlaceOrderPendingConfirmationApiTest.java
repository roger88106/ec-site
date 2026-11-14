package com.ecsite.module.order.presentation;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.TestTokens;
import com.ecsite.module.order.application.payment.OrderPayments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.ecsite.OrderTestData.placeOrderRequest;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.SHIRT_QUANTITY;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.SHIRT_STOCK;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.TOTAL;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.expectConfirmationRequired;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支払待ちの注文の確認の受け入れテスト（注文の時点）
 *
 * <p>支払待ちの注文を確認していない注文手続きからの注文の拒否、確認した注文手続きからの注文、再送、
 * 支払待ちがなくなった後の注文、他の会員の注文手続きを確認する。
 * 開始の時点の確認は {@code CheckoutPendingOrderConfirmationApiTest} が確認する。
 * 上限は {@code PlaceOrderPendingLimitApiTest}、同時の注文は {@code PlaceOrderConcurrencyTest} が確認する。
 *
 * <p>準備は {@link PlaceOrderFixture}（T シャツ 2 枚のカート）。複数の注文ができるように、T シャツの在庫数を 10 にする。
 * テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class PlaceOrderPendingConfirmationApiTest {

    // ============================== 定数 ==============================

    /**
     * 準備の在庫数に加える数
     *
     * <p>在庫数を 10 にして、T シャツ 2 枚の注文を複数できるようにする。
     */
    private static final int ADDED_STOCK = 7;

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
        fixture.inventory.stock(fixture.shirtVariant(), fixture.shirt.id, ADDED_STOCK);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("確認した注文手続きからの注文")
    class Acknowledged {

        @Test
        @DisplayName("AC-406-9 確認して始めた注文手続きからは、支払待ちの注文があっても 2 件目が成立し、在庫も減る")
        void placesSecondOrderWhenAcknowledged() throws Exception {
            // 準備：1 件目を支払待ちにする
            var first = fixture.placeOrder();

            // 操作：1 件目を確認して始めた注文手続きから注文する
            var second = fixture.placed(fixture.startAcknowledging(first));

            // 確認：別の注文が成立して両方とも支払待ちで、在庫は 2 件分減る
            assertThat(second).isNotEqualTo(first);
            expectStatus(second, "PENDING_PAYMENT");
            expectStatus(first, "PENDING_PAYMENT");
            fixture.expectQuantity(stockAfter(2));
        }

        @Test
        @DisplayName("AC-406-9 支払待ちの注文が複数あっても、全てを確認して始めた注文手続きからは注文できる")
        void placesWhenAllPendingOrdersAcknowledged() throws Exception {
            // 準備：支払待ちの注文を 2 件にする
            var first = fixture.placeOrder();
            var second = fixture.placed(fixture.startAcknowledging(first));

            // 操作：2 件とも確認して始めた注文手続きから注文する（201 を確認する）
            fixture.placed(fixture.startAcknowledging(first, second));

            // 確認：在庫は 3 件分減る
            fixture.expectQuantity(stockAfter(3));
        }
    }

    @Nested
    @DisplayName("確認していない注文手続きからの注文")
    class Unacknowledged {

        @Test
        @DisplayName("AC-406-11 2 つのタブで始めて順に注文すると、確認していない 2 つ目は確認を求める 409 で、注文を作らず、在庫を減らさない")
        void rejectsUnacknowledgedSecondTab() throws Exception {
            // 準備：2 つの注文手続きを始め、1 つ目から注文する
            var firstTab = fixture.orders.startCheckout(fixture.cartId);
            var secondTab = fixture.orders.startCheckout(fixture.cartId);
            fixture.placed(firstTab);

            // 操作と確認：2 つ目からの注文は、確認を求める 409
            expectConfirmationRequired(fixture.place(secondTab, TOTAL));

            // 確認：2 つ目の注文はなく、在庫は 1 件分だけ減っている
            fixture.expectNotPlaced(secondTab);
            fixture.expectQuantity(stockAfter(1));
        }

        @Test
        @DisplayName("AC-406-11 カートが変わった注文手続きでも、カートの変更の判定より先に確認を求める")
        void requiresConfirmationBeforeCartChangeCheck() throws Exception {
            // 準備：2 つの注文手続きを始めて 1 つ目から注文し、その後にカートを変える
            var firstTab = fixture.orders.startCheckout(fixture.cartId);
            var secondTab = fixture.orders.startCheckout(fixture.cartId);
            fixture.placed(firstTab);
            fixture.changeShirtQuantity(1);

            // 操作と確認：2 つ目からの注文は、カートの変化でなく確認を求める 409
            expectConfirmationRequired(fixture.place(secondTab, TOTAL));
        }

        @Test
        @DisplayName("AC-406-12 確認した後に別の注文が支払待ちになると、確認した注文手続きからも注文できず、注文を作らず、在庫を減らさない")
        void rejectsWhenPendingOrderAddedAfterAcknowledgement() throws Exception {
            // 準備：1 件目を確認した注文手続きを 2 つ始め、後に始めた方から注文する
            var first = fixture.placeOrder();
            var earlier = fixture.startAcknowledging(first);
            var later = fixture.startAcknowledging(first);
            fixture.placed(later);

            // 操作と確認：先に始めた方からの注文は、確認を求める 409
            expectConfirmationRequired(fixture.place(earlier, TOTAL));

            // 確認：先に始めた方の注文はなく、在庫は 2 件分だけ減っている
            fixture.expectNotPlaced(earlier);
            fixture.expectQuantity(stockAfter(2));
        }

        @Test
        @DisplayName("BR-406-1 支払の期限を過ぎ、まだキャンセルされていない注文も、支払待ちとして確認を求める")
        void treatsOverdueOrderAsPending() throws Exception {
            // 準備：2 つの注文手続きを始めて 1 つ目から注文し、その注文の支払の期限を過ぎる
            var firstTab = fixture.orders.startCheckout(fixture.cartId);
            var secondTab = fixture.orders.startCheckout(fixture.cartId);
            var first = fixture.placed(firstTab);
            clock.setInstant(fixture.paymentDeadlineOf(first).plusSeconds(1));

            // 操作と確認：2 つ目からの注文は、確認を求める 409
            expectConfirmationRequired(fixture.place(secondTab, TOTAL));
        }
    }

    @Nested
    @DisplayName("同じ注文手続きの再送")
    class Resend {

        @Test
        @DisplayName("AC-406-15 確認していない注文手続きの再送は、その注文が支払待ちでも、作成済みの注文を 200 で返す")
        void returnsExistingPendingOrder() throws Exception {
            // 準備：注文手続きから注文し、支払待ちにする
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            var first = fixture.placed(checkoutId);

            // 操作と確認：再送は 200 で、作成済みの注文 ID を返す
            fixture.expectExisting(checkoutId, first);

            // 確認：在庫は 1 件分だけ減っている
            fixture.expectQuantity(stockAfter(1));
        }

        @Test
        @DisplayName("AC-406-15 注文が受付になった後の再送も、作成済みの注文を 200 で返す")
        void returnsExistingAcceptedOrder() throws Exception {
            // 準備：注文手続きから注文し、受付にする
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            var first = fixture.placed(checkoutId);
            OrderPayments.accept(applicationContext, first);

            // 操作と確認：再送は 200 で、作成済みの注文 ID を返す
            fixture.expectExisting(checkoutId, first);

            // 確認：在庫は 1 件分だけ減っている
            fixture.expectQuantity(stockAfter(1));
        }

        @Test
        @DisplayName("AC-406-15 確認していない別の支払待ちの注文が増えた後の再送も、作成済みの注文を 200 で返す")
        void returnsExistingOrderAfterAnotherPendingOrder() throws Exception {
            // 準備：注文を 3 件作る。1 件目と 2 件目の注文手続きは、後の注文を確認していない
            var firstCheckout = fixture.orders.startCheckout(fixture.cartId);
            var first = fixture.placed(firstCheckout);
            var secondCheckout = fixture.startAcknowledging(first);
            var second = fixture.placed(secondCheckout);
            fixture.placed(fixture.startAcknowledging(first, second));

            // 操作と確認：1 件目と 2 件目の再送は、どちらも 200 で、作成済みの注文 ID を返す
            fixture.expectExisting(firstCheckout, first);
            fixture.expectExisting(secondCheckout, second);

            // 確認：在庫は 3 件分だけ減っている
            fixture.expectQuantity(stockAfter(3));
        }
    }

    @Nested
    @DisplayName("支払待ちの注文がなくなった後")
    class NoPendingOrder {

        @Test
        @DisplayName("AC-406-16 支払の期限切れでキャンセルされた後は、確認なしで始めた注文手続きから注文できる")
        void placesAfterExpiry() throws Exception {
            // 準備：注文を支払の期限切れでキャンセルする
            OrderPayments.expire(applicationContext, fixture.placeOrder());

            // 操作と確認：確認なしで始めた注文手続きから 201 で注文できる
            fixture.placed(fixture.orders.startCheckout(fixture.cartId));
        }

        @Test
        @DisplayName("AC-406-16 支払の期限切れでキャンセルされた後は、先に始めていた確認していない注文手続きからも注文できる")
        void placesFromEarlierCheckoutAfterExpiry() throws Exception {
            // 準備：2 つの注文手続きを始め、1 つ目の注文を支払の期限切れでキャンセルする
            var firstTab = fixture.orders.startCheckout(fixture.cartId);
            var secondTab = fixture.orders.startCheckout(fixture.cartId);
            OrderPayments.expire(applicationContext, fixture.placed(firstTab));

            // 操作と確認：2 つ目から 201 で注文できる
            fixture.placed(secondTab);
        }

        @Test
        @DisplayName("AC-406-16 与信の成功で受付になった後は、確認なしで始めた注文手続きから注文できる")
        void placesAfterAcceptance() throws Exception {
            // 準備：注文を受付にし、カートに T シャツを入れ直す（受付になると、注文した品目はカートから除かれる）
            OrderPayments.accept(applicationContext, fixture.placeOrder());
            fixture.orders.add(fixture.cartId, fixture.shirtVariant(), SHIRT_QUANTITY);

            // 操作と確認：確認なしで始めた注文手続きから 201 で注文できる
            fixture.placed(fixture.orders.startCheckout(fixture.cartId));
        }
    }

    @Nested
    @DisplayName("他の会員の注文手続き")
    class OtherMember {

        @Test
        @DisplayName("AC-406-17 他の会員の注文手続きからの注文は、持ち主にも送った会員にも支払待ちの注文があっても 404 で、注文を作らない")
        void hidesCheckoutFromOtherMembers() throws Exception {
            // 準備：持ち主に支払待ちの注文と、確認していない注文手続きを作り、他の会員にも支払待ちの注文を作る
            var firstTab = fixture.orders.startCheckout(fixture.cartId);
            var secondTab = fixture.orders.startCheckout(fixture.cartId);
            fixture.placed(firstTab);
            var other = otherMember();
            new OrderTestData(fixture.mockMvc, null, null, other.bearer()).placePendingOrder(fixture.shirtVariant(), 1);

            // 操作と確認：他の会員が持ち主の注文手続きから注文すると 404
            fixture.mockMvc.perform(placeOrderRequest(secondTab, TOTAL).with(other.bearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));

            // 確認：注文手続きの注文がない
            fixture.expectNotPlaced(secondTab);
        }

        @Test
        @DisplayName("AC-406-17 注文の済んだ注文手続きに対して、他の会員が注文を送っても 404 で、注文を示さない")
        void hidesPlacedOrderFromOtherMembers() throws Exception {
            // 準備：持ち主が注文手続きから注文する
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            fixture.placed(checkoutId);

            // 操作と確認：他の会員が同じ注文手続きで送ると 404 で、注文 ID を返さない
            fixture.mockMvc.perform(placeOrderRequest(checkoutId, TOTAL).with(otherMember().bearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.id").doesNotExist());
        }

        /**
         * 既定の会員と別の会員の token を作る。
         *
         * @return token の組み立て
         */
        private TestTokens.Builder otherMember() {
            return TestTokens.member().subject(UUID.randomUUID()).email("other@example.com");
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文の状態を確認する。
     *
     * @param orderId 注文 ID（実行ごとに作るので、定数にできない）
     * @param status  期待する状態
     * @throws Exception API の呼び出しに失敗したとき、または状態が違うとき
     */
    private void expectStatus(UUID orderId, String status) throws Exception {
        fixture.mockMvc.perform(get("/api/store/orders/" + orderId)).andExpect(jsonPath("$.status").value(status));
    }

    // ============================== 準備の補助 ==============================

    /**
     * T シャツ 2 枚の注文を指定の件数だけした後の、T シャツの在庫数を返す。
     *
     * @param placedOrders 成立した注文の件数
     * @return 在庫数
     */
    private static int stockAfter(int placedOrders) {
        return SHIRT_STOCK + ADDED_STOCK - placedOrders * SHIRT_QUANTITY;
    }
}
