package com.ecsite.module.order.presentation;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.module.order.application.payment.OrderPayments;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ecsite.OrderTestData.startCheckoutRequest;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.SHIRT_QUANTITY;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.SHIRT_STOCK;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.TOTAL;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.expectConfirmationRequired;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.expectLimitExceeded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支払待ちの注文の上限の受け入れテスト（注文の時点）
 *
 * <p>上限（既定の 3 件）に達した会員の注文の拒否、上限より少ないときの注文、上限が下がった後の扱い、
 * 支払待ちが減った後の再開、再送を確認する。
 * 開始の時点の上限は {@code CheckoutPendingOrderLimitApiTest} が確認する。
 * 同時の注文で上限を超えないことは {@code PlaceOrderConcurrencyTest} が確認する。
 *
 * <p>準備は {@link PlaceOrderFixture}。支払待ちの注文は、注文ごとに別の商品で作るので、カートを空にしておく
 * （T シャツは、確認した注文を送って注文手続きを始める前に、もう一度入れる）。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class PlaceOrderPendingLimitApiTest {

    // ============================== 定数 ==============================

    /**
     * 上限を一時的に上げる値
     *
     * <p>上限を超えた状態を作るために、既定の上限より十分大きくする。
     */
    private static final int RAISED_LIMIT = 10;

    /**
     * 支払待ちの注文の上限の既定の件数
     */
    private static final int LIMIT = 3;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    CheckoutRepository checkoutRepository;

    /**
     * 注文の設定
     *
     * <p>注文の時点で上限に達した状態を作るテストだけ、上限を差し替える。
     */
    @MockitoSpyBean
    OrderProperties properties;

    /**
     * 共有の準備
     */
    private PlaceOrderFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(PlaceOrderFixture.NOW);
        fixture = PlaceOrderFixture.prepare(applicationContext);
        fixture.mockMvc.perform(delete("/api/store/carts/" + fixture.cartId + "/items/" + fixture.shirtVariant()))
                .andExpect(status().isNoContent());
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("上限に達した会員の注文")
    class AtLimit {

        @Test
        @DisplayName("AC-411-1、BR-411-2 支払待ちの注文が上限（3 件）に達していると、全て確認した注文手続きからの注文も 409 で拒否し、注文を作らず、在庫を減らさない")
        void rejectsAtLimit() throws Exception {
            // 準備：支払待ちを上限まで作り、全て確認した注文手続きを始める
            var checkoutId = startAcknowledgingAtLimit();

            // 操作と確認：上限を示す 409
            expectLimitExceeded(fixture.place(checkoutId, TOTAL));

            // 確認：注文はなく、T シャツの在庫も支払待ちの件数も変わらない
            fixture.expectNotPlaced(checkoutId);
            fixture.expectQuantity(SHIRT_STOCK);
            assertThat(pendingOrderCount(checkoutId)).isEqualTo(LIMIT);
        }

        @Test
        @DisplayName("AC-411-8 上限を超える件数（4 件）の支払待ちの注文を持つ会員の注文も、全て確認した注文手続きからでも 409 で拒否し、注文を作らず、在庫を減らさない")
        void rejectsAboveLimit() throws Exception {
            // 準備：上限を下げる前の状態を作るため、4 件を作る間だけ上限を上げる（上限が 3 件のままでは 4 件目を注文できない）
            raiseLimit();
            var checkoutId = startWithShirts(fixture.orders.placePendingOrders(fixture.cartId, stockedVariants(LIMIT + 1)));
            restoreLimit();

            // 操作と確認：上限を示す 409
            expectLimitExceeded(fixture.place(checkoutId, TOTAL));

            // 確認：注文はなく、T シャツの在庫も支払待ちの件数も変わらない
            fixture.expectNotPlaced(checkoutId);
            fixture.expectQuantity(SHIRT_STOCK);
            assertThat(pendingOrderCount(checkoutId)).isEqualTo(LIMIT + 1);
        }

        @Test
        @DisplayName("BR-411-1 支払の期限を過ぎても、期限切れのキャンセルが済むまでは、上限の件数に数えて注文を 409 で拒否する")
        void countsOverdueOrderUntilCancelled() throws Exception {
            // 準備：支払待ちを上限まで作って全て確認した注文手続きを始め、最後の注文の支払の期限を過ぎる
            var pendingIds = fixture.orders.placePendingOrders(fixture.cartId, stockedVariants(LIMIT));
            raiseLimit();
            var checkoutId = startWithShirts(pendingIds);
            restoreLimit();
            clock.setInstant(fixture.paymentDeadlineOf(pendingIds.getLast()).plusSeconds(1));

            // 操作と確認：上限を示す 409
            expectLimitExceeded(fixture.place(checkoutId, TOTAL));

            // 確認：注文がない
            fixture.expectNotPlaced(checkoutId);
        }

        @Test
        @DisplayName("AC-411-4 上限に達していても、確認していない支払待ちの注文があれば、注文の時点でも上限でなく確認を求める 409 を返し、注文を作らない")
        void requiresConfirmationBeforeLimit() throws Exception {
            // 準備：支払待ちを 2 件作り、それらを確認した注文手続きを 2 つ始める。
            // 後に始めた注文手続きから 3 件目が成立し、先に始めた注文手続きはその注文を確認していない
            var pendingIds = fixture.orders.placePendingOrders(fixture.cartId, stockedVariants(LIMIT - 1));
            var earlier = startWithShirts(pendingIds);
            var later = fixture.orders.startCheckout(fixture.cartId, pendingIds);
            fixture.placed(later);

            // 操作と確認：先に始めた方からの注文は、確認を求める 409
            expectConfirmationRequired(fixture.place(earlier, TOTAL));

            // 確認：先に始めた方の注文はなく、T シャツの在庫は 1 件分だけ減っている
            fixture.expectNotPlaced(earlier);
            fixture.expectQuantity(SHIRT_STOCK - SHIRT_QUANTITY);
        }
    }

    @Nested
    @DisplayName("上限より少ない会員の注文")
    class BelowLimit {

        @Test
        @DisplayName("AC-411-2 支払待ちの注文が上限より 1 件少ない（2 件）ときは、全て確認した注文手続きから注文でき、支払待ちが 3 件になる")
        void placesBelowLimit() throws Exception {
            // 準備：支払待ちを 2 件作り、全て確認した注文手続きを始める
            var checkoutId = startWithShirts(fixture.orders.placePendingOrders(fixture.cartId, stockedVariants(LIMIT - 1)));

            // 操作：注文する（201 を確認する）
            var orderId = fixture.placed(checkoutId);

            // 確認：注文が支払待ちで、在庫が減り、支払待ちが上限の件数になる
            fixture.mockMvc.perform(get("/api/store/orders/" + orderId)).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
            fixture.expectQuantity(SHIRT_STOCK - SHIRT_QUANTITY);
            assertThat(pendingOrderCount(checkoutId)).isEqualTo(LIMIT);
        }

        @Test
        @DisplayName("AC-411-7 上限に達した後の同じ注文手続きの再送は、作成済みの注文 ID を 200 で返し、注文は 1 件だけになる")
        void returnsExistingOrderOnResendAtLimit() throws Exception {
            // 準備：支払待ちを 2 件作り、全て確認した注文手続きから 3 件目を注文する
            var checkoutId = startWithShirts(fixture.orders.placePendingOrders(fixture.cartId, stockedVariants(LIMIT - 1)));
            var orderId = fixture.placed(checkoutId);

            // 確認（前提）：支払待ちが上限に達している
            assertThat(pendingOrderCount(checkoutId)).isEqualTo(LIMIT);

            // 操作と確認：再送は 200 で、作成済みの注文 ID を返す
            fixture.expectExisting(checkoutId, orderId);

            // 確認：注文手続きの注文は 1 件で、在庫は 1 件分だけ減っている
            assertThat(fixture.orderCount(checkoutId)).isEqualTo(1);
            fixture.expectQuantity(SHIRT_STOCK - SHIRT_QUANTITY);
        }
    }

    @Nested
    @DisplayName("支払待ちが上限より少なくなった後")
    class AfterDecrease {

        @Test
        @DisplayName("AC-411-6 開始の後に上限が下がった注文手続きでも、支払の期限切れでキャンセルされて上限より少なくなると、その注文手続きから再び注文できる")
        void placesAfterExpiry() throws Exception {
            // 準備：上限に達して拒否された後、確認した注文の 1 件を支払の期限切れでキャンセルする
            var checkoutId = startAcknowledgingAtLimit();
            expectLimitExceeded(fixture.place(checkoutId, TOTAL));
            OrderPayments.expire(applicationContext, anyAcknowledged(checkoutId));

            // 操作：同じ注文手続きから注文する（201 を確認する）
            fixture.placed(checkoutId);

            // 確認：T シャツの在庫が減っている
            fixture.expectQuantity(SHIRT_STOCK - SHIRT_QUANTITY);
        }

        @Test
        @DisplayName("AC-411-6 開始の後に上限が下がった注文手続きでも、与信の成功で受付になって上限より少なくなると、その注文手続きから再び注文できる")
        void placesAfterAcceptance() throws Exception {
            // 準備：上限に達して拒否された後、確認した注文の 1 件を受付にする
            // （受付にした注文の品目は、もうカートにないので、注文手続きのカートは変わらない）
            var checkoutId = startAcknowledgingAtLimit();
            expectLimitExceeded(fixture.place(checkoutId, TOTAL));
            OrderPayments.accept(applicationContext, anyAcknowledged(checkoutId));

            // 操作：同じ注文手続きから注文する（201 を確認する）
            fixture.placed(checkoutId);

            // 確認：T シャツの在庫が減っている
            fixture.expectQuantity(SHIRT_STOCK - SHIRT_QUANTITY);
        }

        @Test
        @DisplayName("AC-411-6 上限で開始を拒否された後、支払の期限切れでキャンセルされて上限より少なくなると、残りの支払待ちを確認して再び開始した注文手続きから 201 で注文でき、在庫が減る")
        void startsAgainAndPlacesAfterExpiry() throws Exception {
            // 準備：支払待ちを上限まで作って開始を拒否された後、1 件を支払の期限切れでキャンセルする
            var pendingIds = startRejectedAtLimit();
            OrderPayments.expire(applicationContext, pendingIds.getFirst());

            // 操作：残りの 2 件を確認して開始し、注文する（201 を確認する）
            fixture.placed(fixture.orders.startCheckout(fixture.cartId, pendingIds.subList(1, LIMIT)));

            // 確認：T シャツの在庫が減っている
            fixture.expectQuantity(SHIRT_STOCK - SHIRT_QUANTITY);
        }

        @Test
        @DisplayName("AC-411-6 上限で開始を拒否された後、与信の成功で受付になって上限より少なくなると、残りの支払待ちを確認して再び開始した注文手続きから 201 で注文でき、在庫が減る")
        void startsAgainAndPlacesAfterAcceptance() throws Exception {
            // 準備：支払待ちを上限まで作って開始を拒否された後、1 件を受付にする
            var pendingIds = startRejectedAtLimit();
            OrderPayments.accept(applicationContext, pendingIds.getFirst());

            // 操作：残りの 2 件を確認して開始し、注文する（201 を確認する）
            fixture.placed(fixture.orders.startCheckout(fixture.cartId, pendingIds.subList(1, LIMIT)));

            // 確認：T シャツの在庫が減っている
            fixture.expectQuantity(SHIRT_STOCK - SHIRT_QUANTITY);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 支払待ちの注文を上限（3 件）まで作り、それらを全て確認して T シャツ 2 枚の注文手続きを始め、注文手続き ID を返す。
     *
     * <p>開始の時点も上限で拒否するので、開始の間だけ上限を上げる。設定の上限が、開始と注文の間に下がった場合を想定する（最終的な判定は注文の時点）。
     *
     * @return 注文手続き ID
     * @throws Exception API の呼び出しに失敗したとき
     */
    private UUID startAcknowledgingAtLimit() throws Exception {
        var pendingIds = fixture.orders.placePendingOrders(fixture.cartId, stockedVariants(LIMIT));
        raiseLimit();
        var checkoutId = startWithShirts(pendingIds);
        restoreLimit();
        return checkoutId;
    }

    /**
     * 支払待ちの注文を上限（3 件）まで作り、カートに T シャツ 2 枚を入れ、全て確認した開始が上限で拒否されることを確認する。
     *
     * @return 支払待ちの注文の ID
     * @throws Exception API の呼び出しに失敗したとき、または上限を示す 409 でないとき
     */
    private List<UUID> startRejectedAtLimit() throws Exception {
        var pendingIds = fixture.orders.placePendingOrders(fixture.cartId, stockedVariants(LIMIT));
        fixture.orders.add(fixture.cartId, fixture.shirtVariant(), SHIRT_QUANTITY);
        expectLimitExceeded(fixture.mockMvc.perform(startCheckoutRequest(fixture.cartId, pendingIds)));
        return pendingIds;
    }

    /**
     * 上限を一時的に上げる（開始の時点の判定を通すため）。
     */
    private void raiseLimit() {
        doReturn(RAISED_LIMIT).when(properties).pendingOrderLimit();
    }

    /**
     * 上げた上限を、差し替える前の値に戻す。
     */
    private void restoreLimit() {
        reset(properties);
    }

    /**
     * カートに T シャツ 2 枚を入れ、確認した注文の一覧を送って注文手続きを始め、注文手続き ID を返す。
     *
     * @param acknowledged 確認した支払待ちの注文の ID
     * @return 注文手続き ID
     * @throws Exception API の呼び出しに失敗したとき
     */
    private UUID startWithShirts(List<UUID> acknowledged) throws Exception {
        fixture.orders.add(fixture.cartId, fixture.shirtVariant(), SHIRT_QUANTITY);
        return fixture.orders.startCheckout(fixture.cartId, acknowledged);
    }

    /**
     * 注文手続きに保存した、確認した注文の ID の 1 つを返す。
     *
     * @param checkoutId 注文手続き ID
     * @return 確認した注文の ID
     */
    private UUID anyAcknowledged(UUID checkoutId) {
        return checkoutRepository.findById(checkoutId).orElseThrow().acknowledgedOrderIds().iterator().next();
    }

    /**
     * 注文手続きの会員が持つ、支払待ちの注文の件数を返す。
     *
     * @param checkoutId 注文手続き ID
     * @return 支払待ちの注文の件数
     */
    private int pendingOrderCount(UUID checkoutId) {
        return OrderTestData.pendingOrderCount(fixture.dsl, checkoutRepository.findById(checkoutId).orElseThrow().memberId());
    }

    /**
     * 公開した商品（1,000 円・10%）のバリアントを、指定した数だけ作る（在庫数は各 3）。
     *
     * @param count 作る数
     * @return バリアント ID
     * @throws Exception API の呼び出しに失敗したとき
     */
    private List<UUID> stockedVariants(int count) throws Exception {
        var variants = new ArrayList<UUID>();
        for (int i = 0; i < count; i++) {
            var product = fixture.catalog.product(fixture.standard, "商品", 1000).publish();
            var variantId = product.variantIds.getFirst();
            fixture.inventory.stock(variantId, product.id, 3);
            variants.add(variantId);
        }
        return variants;
    }
}
