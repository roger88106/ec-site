package com.ecsite.module.order.presentation;

import com.ecsite.module.order.application.payment.OrderPayments;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.security.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.module.order.presentation.CheckoutStartFixture.RESERVATION_TTL;
import static com.ecsite.module.order.presentation.CheckoutStartFixture.expectConfirmationRequired;
import static com.ecsite.support.data.OrderTestData.startCheckoutRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支払待ちの注文があるときの、注文手続きの開始の確認の受け入れテスト
 *
 * <p>会員に支払待ちの注文があるとき、開始が確認を求めること、確認した一覧を送れば始められること、
 * 支払待ちの注文がなくなれば確認が必要なくなることを確認する。支払待ちの注文の上限は
 * {@link CheckoutPendingOrderLimitApiTest}、注文の時点の確認は {@code PlaceOrderPendingConfirmationApiTest} が確認する。
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class CheckoutPendingOrderConfirmationApiTest {

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

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
    @DisplayName("確認を求める応答")
    class ConfirmationRequired {

        @Test
        @DisplayName("AC-406-1、AC-406-18 支払待ちの注文があれば、開始は確認を求める 409 を返し、注文手続きを作らない")
        void requiresConfirmation() throws Exception {
            // 準備：支払待ちの注文を 1 件作る
            orders.placePendingOrder(fixture.stockedVariant(3), 1);
            var checkouts = fixture.checkoutCount();

            // 操作と確認：一覧を送らずに始めると、確認を求められる
            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));

            // 確認：注文手続きは増えていない
            assertThat(fixture.checkoutCount()).isEqualTo(checkouts);
        }

        @Test
        @DisplayName("AC-406-1 古い一覧（今の支払待ちの注文を含まない）を送ると、確認を求め、注文手続きを作らない")
        void rejectsStaleList() throws Exception {
            // 準備：1 件目を確認して 2 件目の支払待ちの注文を作る
            var first = orders.placePendingOrder(fixture.stockedVariant(5), 1);
            var second = orders.placeFromCheckout(orders.startCheckout(cartId, List.of(first)));
            var checkouts = fixture.checkoutCount();

            // 操作と確認：1 件目だけの一覧を送ると、確認を求められる
            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId, List.of(first))));

            // 確認：注文手続きは増えていない
            assertThat(fixture.checkoutCount()).isEqualTo(checkouts);

            // 確認（対照）：今の 2 件を送れば始められ、2 件を確認したと保存する
            assertThat(fixture.acknowledgedOrderIds(orders.startCheckout(cartId, List.of(first, second))))
                    .containsExactlyInAnyOrder(first, second);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"{}", "{\"acknowledgedOrderIds\": []}", "{\"acknowledgedOrderIds\": null}",
                "{\"acknowledgedOrderIds\": [null]}"})
        @DisplayName("AC-406-1 本文の一覧が空、null、または null の要素を含むなら、確認していないものとして確認を求める")
        void treatsEmptyListAsNotAcknowledged(String body) throws Exception {
            // 準備：支払待ちの注文を 1 件作る
            orders.placePendingOrder(fixture.stockedVariant(3), 1);

            // 操作と確認：確認を求められる
            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)
                    .contentType(MediaType.APPLICATION_JSON).content(body)));
        }

        @Test
        @DisplayName("AC-406-2 確認を求めるときは、前の注文手続きの引当を解かず、新たに引き当てず、注文手続きも増やさない")
        void keepsPreviousReservationWhenRejected() throws Exception {
            // 準備：在庫 5 の品目を 1 個注文し、確認して注文手続きを始める（注文で 1 個、引当で 1 個）
            var variant = fixture.stockedVariant(5);
            var pending = orders.placePendingOrder(variant, 1);
            orders.startCheckout(cartId, List.of(pending));
            assertThat(fixture.sellable(variant)).isEqualTo(3);
            var checkouts = fixture.checkoutCount();

            // 操作と確認：一覧を送らずに始めると、確認を求められる
            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));

            // 確認：販売可能数も注文手続きの数も変わらない
            assertThat(fixture.sellable(variant)).isEqualTo(3);
            assertThat(fixture.checkoutCount()).isEqualTo(checkouts);
        }
    }

    @Nested
    @DisplayName("判定の順序")
    class JudgementOrder {

        @Test
        @DisplayName("AC-406-3 カートを変えた後も、品目を比べずに確認を求める")
        void requiresConfirmationAfterCartChange() throws Exception {
            // 準備：支払待ちの注文を作った後に、別の品目をカートに入れる
            orders.placePendingOrder(fixture.stockedVariant(3), 1);
            orders.add(cartId, fixture.stockedVariant(3), 1);

            // 操作と確認：確認を求められる
            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));
        }

        @Test
        @DisplayName("AC-406-3 購入できない品目（販売停止）があっても、購入の可否の判定より先に確認を求める")
        void requiresConfirmationBeforeOrderableCheck() throws Exception {
            // 準備：注文した品目を販売停止にする（カートに残る）
            fixture.orderThenSuspend();

            // 操作と確認：400 でなく、確認を求められる
            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));
        }

        @Test
        @DisplayName("AC-406-3 在庫が足りない品目があっても、購入の可否の判定より先に確認を求める")
        void requiresConfirmationBeforeStockCheck() throws Exception {
            // 準備：在庫の全てを注文したので、カートに残った品目は在庫が足りない
            orders.placePendingOrder(fixture.stockedVariant(1), 1);

            // 操作と確認：在庫の不足でなく、確認を求められる
            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));
        }

        @Test
        @DisplayName("AC-406-3 カートが空でも、購入の可否の判定より先に確認を求める")
        void requiresConfirmationForEmptyCart() throws Exception {
            // 準備：注文した品目をカートから除き、カートを空にする
            var variant = fixture.stockedVariant(3);
            orders.placePendingOrder(variant, 1);
            mockMvc.perform(delete("/api/store/carts/" + cartId + "/items/" + variant)).andExpect(status().isNoContent());

            // 操作と確認：空のカートの 400 でなく、確認を求められる
            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));
        }
    }

    @Nested
    @DisplayName("確認した後の開始")
    class Acknowledged {

        @Test
        @DisplayName("AC-406-7 確認した一覧を送ると、通常と同じく引き当てて注文手続きを始める")
        void startsWhenAcknowledged() throws Exception {
            // 準備：在庫 5 の品目を 1 個注文する（カートに残る）
            var variant = fixture.stockedVariant(5);
            var pending = orders.placePendingOrder(variant, 1);
            // 商品の公開が時計を進めるので、始める時点の時刻を読む
            var startedAt = clock.instant();

            // 操作：確認した一覧を送って始める
            var checkoutId = orders.startCheckout(cartId, List.of(pending));

            // 確認：カートの品目を引き当て、期限は 15 分後
            assertThat(fixture.sellable(variant)).isEqualTo(3);
            mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.expiresAt").value(startedAt.plus(RESERVATION_TTL).toString()));
        }

        @Test
        @DisplayName("AC-406-7 保存するのは受けた一覧でなく、その時点で支払待ちの注文の ID である")
        void savesCurrentPendingOrderIds() throws Exception {
            // 準備：支払待ちの注文を 1 件作る
            var pending = orders.placePendingOrder(fixture.stockedVariant(3), 1);

            // 操作：支払待ちの注文と、ない ID を送って始める
            var checkoutId = orders.startCheckout(cartId, List.of(pending, UUID.randomUUID()));

            // 確認：保存したのは支払待ちの注文だけ
            assertThat(fixture.acknowledgedOrderIds(checkoutId)).containsExactly(pending);
        }

        @Test
        @DisplayName("AC-406-7 確認した後も、在庫が足りなければ 409 で始められない")
        void checksStockAfterAcknowledged() throws Exception {
            // 準備：在庫の全てを注文したので、カートに残った品目は在庫が足りない
            var pending = orders.placePendingOrder(fixture.stockedVariant(1), 1);

            // 操作と確認：確認していても、在庫の不足で拒否される
            mockMvc.perform(startCheckoutRequest(cartId, List.of(pending)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"));
        }

        @Test
        @DisplayName("AC-406-7 確認した後も、購入できない品目があれば 400 で始められない")
        void checksOrderableAfterAcknowledged() throws Exception {
            // 準備：注文した品目を販売停止にする（カートに残る）
            var pending = fixture.orderThenSuspend();

            // 操作と確認：確認していても、注文できないカートとして拒否される
            mockMvc.perform(startCheckoutRequest(cartId, List.of(pending)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CART_NOT_ORDERABLE"));
        }

        @Test
        @DisplayName("AC-406-10 確認はその注文手続きにだけ有効で、次に始めるときは再び確認を求める")
        void requiresConfirmationAgain() throws Exception {
            // 準備：確認して注文手続きを始める
            var pending = orders.placePendingOrder(fixture.stockedVariant(5), 1);
            orders.startCheckout(cartId, List.of(pending));

            // 操作と確認：次に一覧を送らずに始めると、再び確認を求められる
            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));

            // 確認（対照）：もう一度確認すれば始められる
            assertThat(fixture.acknowledgedOrderIds(orders.startCheckout(cartId, List.of(pending)))).containsExactly(pending);
        }
    }

    @Nested
    @DisplayName("支払待ちの注文がなくなった後")
    class AfterPendingOrdersGone {

        @Test
        @DisplayName("AC-406-16 支払の期限切れでキャンセルされた後は、確認なしで始められ、確認した注文は空になる")
        void startsAfterExpiry() throws Exception {
            // 準備：支払待ちの注文を、期限切れでキャンセルする
            var pending = orders.placePendingOrder(fixture.stockedVariant(3), 1);
            OrderPayments.expire(applicationContext, pending);

            // 操作と確認：一覧を送らずに始められ、確認した注文は空
            assertThat(fixture.acknowledgedOrderIds(fixture.startCheckout(cartId))).isEmpty();
        }

        @Test
        @DisplayName("AC-406-16 与信の成功で受付になった後は、確認なしで始められ、確認した注文は空になる")
        void startsAfterAcceptance() throws Exception {
            // 準備：支払待ちの注文を受付にする。受付になると注文した品目はカートから除かれるので、入れ直す
            var variant = fixture.stockedVariant(3);
            var pending = orders.placePendingOrder(variant, 1);
            OrderPayments.accept(applicationContext, pending);
            orders.add(cartId, variant, 1);

            // 操作と確認：一覧を送らずに始められ、確認した注文は空
            assertThat(fixture.acknowledgedOrderIds(fixture.startCheckout(cartId))).isEmpty();
        }

        @Test
        @DisplayName("AC-406-16 支払待ちの注文がなければ、送った一覧は保存しない")
        void ignoresListWithoutPendingOrders() throws Exception {
            // 準備：支払待ちの注文のない会員のカートに品目を入れる
            orders.add(cartId, fixture.stockedVariant(3), 1);

            // 操作と確認：ない ID の一覧を送って始めると、確認した注文は空
            assertThat(fixture.acknowledgedOrderIds(orders.startCheckout(cartId, List.of(UUID.randomUUID())))).isEmpty();
        }
    }

    @Nested
    @DisplayName("他の会員")
    class OtherMembers {

        @Test
        @DisplayName("AC-406-17 他の会員のカートの開始は、自分に支払待ちの注文があっても 404 で、注文を示さず、注文手続きを作らない")
        void hidesOrdersFromOtherMembersWithoutList() throws Exception {
            // 準備：持ち主と他の会員の両方に、支払待ちの注文を作る
            var pending = orders.placePendingOrder(fixture.stockedVariant(5), 1);
            var other = otherMemberWithPendingOrder();
            var checkouts = fixture.checkoutCount();

            // 操作：他の会員が、一覧を送らずに持ち主のカートで始める
            var result = mockMvc.perform(startCheckoutRequest(cartId).with(other.bearer()));

            // 確認：見つからない応答で、持ち主の注文を示さず、注文手続きも増えていない
            expectHidden(result, pending);
            assertThat(fixture.checkoutCount()).isEqualTo(checkouts);
        }

        @Test
        @DisplayName("AC-406-17 他の会員のカートの開始は、持ち主の支払待ちの注文を一覧で送っても 404 で、注文を示さず、注文手続きを作らない")
        void hidesOrdersFromOtherMembersWithList() throws Exception {
            // 準備：持ち主と他の会員の両方に、支払待ちの注文を作る
            var pending = orders.placePendingOrder(fixture.stockedVariant(5), 1);
            var other = otherMemberWithPendingOrder();
            var checkouts = fixture.checkoutCount();

            // 操作：他の会員が、持ち主の注文の一覧を送って持ち主のカートで始める
            var result = mockMvc.perform(startCheckoutRequest(cartId, List.of(pending)).with(other.bearer()));

            // 確認：見つからない応答で、持ち主の注文を示さず、注文手続きも増えていない
            expectHidden(result, pending);
            assertThat(fixture.checkoutCount()).isEqualTo(checkouts);
        }

        @Test
        @DisplayName("BR-406-2 他の会員の支払待ちの注文は、判定の対象にしない")
        void ignoresPendingOrdersOfOtherMembers() throws Exception {
            // 準備：他の会員にだけ支払待ちの注文を作り、自分のカートに品目を入れる
            otherMemberWithPendingOrder();
            orders.add(cartId, fixture.stockedVariant(3), 1);

            // 操作と確認：一覧を送らずに始められ、確認した注文は空
            assertThat(fixture.acknowledgedOrderIds(fixture.startCheckout(cartId))).isEmpty();
        }
    }

    @Nested
    @DisplayName("支払の期限を過ぎた注文")
    class OverdueOrder {

        @Test
        @DisplayName("BR-406-1 支払の期限を過ぎ、まだキャンセルされていない注文も、支払待ちとして確認を求める")
        void treatsOverdueOrderAsPending() throws Exception {
            // 準備：支払待ちの注文の支払の期限の 1 秒後にする（期限切れのキャンセルはしない）
            var pending = orders.placePendingOrder(fixture.stockedVariant(3), 1);
            var order = mockMvc.perform(get("/api/store/orders/" + pending)).andReturn().getResponse().getContentAsString();
            clock.setInstant(Instant.parse(JsonPath.read(order, "$.paymentDeadline")).plusSeconds(1));

            // 操作と確認：確認を求められる
            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 他の会員の開始が、持ち主の注文を示さない 404 であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 404</li>
     *   <li>エラーコードが {@code NOT_FOUND}</li>
     *   <li>本文に、持ち主の支払待ちの注文の ID がない</li>
     * </ul>
     *
     * @param result       他の会員の開始の応答
     * @param ownerOrderId 持ち主の支払待ちの注文 ID（実行ごとに作るので、定数にできない）
     */
    private static void expectHidden(ResultActions result, UUID ownerOrderId) throws Exception {
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString(ownerOrderId.toString()))));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 他の会員を作り、その会員の支払待ちの注文を 1 件作る。
     *
     * @return 他の会員の token
     */
    private TestTokens.Builder otherMemberWithPendingOrder() throws Exception {
        var other = TestTokens.member().subject(UUID.randomUUID()).email("other@example.com");
        new OrderTestData(mockMvc, null, null, other.bearer()).placePendingOrder(fixture.stockedVariant(3), 1);
        return other;
    }
}
