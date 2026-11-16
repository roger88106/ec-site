package com.ecsite.module.order.presentation;

import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.module.order.application.payment.OrderPayments;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.OrderTestData;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ecsite.module.order.presentation.CheckoutStartFixture.expectConfirmationRequired;
import static com.ecsite.module.order.presentation.CheckoutStartFixture.expectLimitExceeded;
import static com.ecsite.support.data.OrderTestData.startCheckoutRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 支払待ちの注文の上限による、注文手続きの開始の拒否の受け入れテスト
 *
 * <p>上限の既定は 3 件。上限を超えた状態を作るテストだけ、注文の設定の上限を一時的に差し替える。
 * 支払待ちの注文の確認は {@link CheckoutPendingOrderConfirmationApiTest} が確認する。
 * 注文の時点の上限は {@code PlaceOrderPendingLimitApiTest} が確認する。
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class CheckoutPendingOrderLimitApiTest {

    // ============================== 定数 ==============================

    /**
     * 上限を一時的に上げる値
     *
     * <p>上限を超えた状態を作るために、既定の上限より十分大きくする。
     */
    private static final int RAISED_LIMIT = 10;

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationContext applicationContext;

    /**
     * 注文の設定
     *
     * <p>上限の既定は 3 件。上限を超えた状態を作るテストだけ、上限を差し替える。
     */
    @MockitoSpyBean
    OrderProperties properties;

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
    @DisplayName("上限に達しているときの開始")
    class AtLimit {

        @Test
        @DisplayName("AC-411-3 支払待ちの注文が上限（3 件）に達していると、全て確認していても開始は 409 で拒否し、注文手続きも引当も変えない")
        void rejectsStartAtLimit() throws Exception {
            // 準備：2 件の支払待ちの注文の後、在庫 5 の品目をカートに残したまま 3 件目を作る（注文で 1 個減る）
            var variant = fixture.stockedVariant(5);
            var pendingIds = placePendingOrdersOfNewVariants(2);
            orders.add(cartId, variant, 1);
            pendingIds.add(orders.placeFromCheckout(orders.startCheckout(cartId, pendingIds)));
            assertThat(fixture.sellable(variant)).isEqualTo(4);
            var checkouts = fixture.checkoutCount();

            // 操作と確認：3 件を確認して始めても、上限で拒否される
            expectLimitExceeded(mockMvc.perform(startCheckoutRequest(cartId, pendingIds)));

            // 確認：カートに残った品目を新たに引き当てず、注文手続きも増えていない
            assertThat(fixture.sellable(variant)).isEqualTo(4);
            assertThat(fixture.checkoutCount()).isEqualTo(checkouts);
        }

        @Test
        @DisplayName("AC-411-3 上限に達して拒否するときは、前の注文手続きの引当も解かない")
        void keepsPreviousReservationWhenRejected() throws Exception {
            // 準備：3 件の支払待ちの注文と、前の注文手続きの引当（在庫 5 のうち 1 個）
            var pendingIds = placePendingOrdersOfNewVariants(3);
            var variant = fixture.stockedVariant(5);
            orders.add(cartId, variant, 1);
            // 前の注文手続きの引当を残すため、その開始の間だけ上限を上げる
            raiseLimit();
            orders.startCheckout(cartId, pendingIds);
            restoreLimit();
            assertThat(fixture.sellable(variant)).isEqualTo(4);
            var checkouts = fixture.checkoutCount();

            // 操作と確認：上限で拒否される
            expectLimitExceeded(mockMvc.perform(startCheckoutRequest(cartId, pendingIds)));

            // 確認：前の引当が残り、注文手続きも増えていない
            assertThat(fixture.sellable(variant)).isEqualTo(4);
            assertThat(fixture.checkoutCount()).isEqualTo(checkouts);
        }

        @Test
        @DisplayName("BR-411-1 支払の期限を過ぎても、期限切れのキャンセルが済むまでは、上限の件数に数えて開始を拒否する")
        void countsOverdueOrderUntilCancelled() throws Exception {
            // 準備：3 件の支払待ちの注文のうち、最後の注文の支払の期限の 1 秒後にする（キャンセルはしない）
            var pendingIds = placePendingOrdersOfNewVariants(3);
            orders.add(cartId, fixture.stockedVariant(3), 1);
            var order = mockMvc.perform(get("/api/store/orders/" + pendingIds.getLast())).andReturn().getResponse().getContentAsString();
            clock.setInstant(Instant.parse(JsonPath.read(order, "$.paymentDeadline")).plusSeconds(1));

            // 操作と確認：上限で拒否される
            expectLimitExceeded(mockMvc.perform(startCheckoutRequest(cartId, pendingIds)));
        }

        @Test
        @DisplayName("AC-411-8 上限を超える件数（4 件）の支払待ちの注文を持つ会員の開始も、409 で拒否する")
        void rejectsStartAboveLimit() throws Exception {
            // 準備：上限を下げる前の状態を作るため、4 件を作る間だけ上限を上げる（上限が 3 件のままでは 4 件目を注文できない）
            raiseLimit();
            var pendingIds = placePendingOrdersOfNewVariants(4);
            restoreLimit();
            orders.add(cartId, fixture.stockedVariant(3), 1);

            // 操作と確認：上限で拒否される
            expectLimitExceeded(mockMvc.perform(startCheckoutRequest(cartId, pendingIds)));
        }
    }

    @Nested
    @DisplayName("確認の判定との順序")
    class ConfirmationFirst {

        @Test
        @DisplayName("AC-411-4 上限に達していても、一覧を送らなければ、上限でなく確認を求める 409 を返す")
        void requiresConfirmationWithoutList() throws Exception {
            // 準備：上限の 3 件の支払待ちの注文
            placePendingOrdersOfNewVariants(3);
            orders.add(cartId, fixture.stockedVariant(3), 1);

            // 操作と確認：確認を求められる
            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));
        }

        @Test
        @DisplayName("AC-411-4 上限に達していても、確認していない支払待ちの注文があれば、上限でなく確認を求める 409 を返す")
        void requiresConfirmationWithPartialList() throws Exception {
            // 準備：上限の 3 件の支払待ちの注文
            var pendingIds = placePendingOrdersOfNewVariants(3);
            orders.add(cartId, fixture.stockedVariant(3), 1);

            // 操作と確認：2 件だけを確認して送ると、確認を求められる
            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId, pendingIds.subList(0, 2))));
        }
    }

    @Nested
    @DisplayName("上限より少ないときの開始")
    class BelowLimit {

        @Test
        @DisplayName("AC-411-3 支払待ちの注文が上限より 1 件少ない（2 件）ときは、確認すれば開始できる")
        void startsBelowLimit() throws Exception {
            // 準備：2 件の支払待ちの注文
            var pendingIds = placePendingOrdersOfNewVariants(2);
            orders.add(cartId, fixture.stockedVariant(3), 1);

            // 操作と確認：確認して始められ、2 件を確認したと保存する
            assertThat(fixture.acknowledgedOrderIds(orders.startCheckout(cartId, pendingIds)))
                    .containsExactlyInAnyOrderElementsOf(pendingIds);
        }

        @Test
        @DisplayName("AC-411-6 支払の期限切れでキャンセルされて上限より少なくなると、再び注文手続きを開始できる")
        void startsAfterExpiry() throws Exception {
            // 準備：上限で拒否された後に、1 件を期限切れでキャンセルする
            var pendingIds = placePendingOrdersOfNewVariants(3);
            orders.add(cartId, fixture.stockedVariant(3), 1);
            expectLimitExceeded(mockMvc.perform(startCheckoutRequest(cartId, pendingIds)));
            OrderPayments.expire(applicationContext, pendingIds.removeFirst());

            // 操作と確認：残りの 2 件を確認して始められる
            assertThat(fixture.acknowledgedOrderIds(orders.startCheckout(cartId, pendingIds)))
                    .containsExactlyInAnyOrderElementsOf(pendingIds);
        }

        @Test
        @DisplayName("AC-411-6 与信の成功で受付になって上限より少なくなると、再び注文手続きを開始できる")
        void startsAfterAcceptance() throws Exception {
            // 準備：上限で拒否された後に、1 件を受付にする
            var pendingIds = placePendingOrdersOfNewVariants(3);
            orders.add(cartId, fixture.stockedVariant(3), 1);
            expectLimitExceeded(mockMvc.perform(startCheckoutRequest(cartId, pendingIds)));
            OrderPayments.accept(applicationContext, pendingIds.removeFirst());

            // 操作と確認：残りの 2 件を確認して始められる
            assertThat(fixture.acknowledgedOrderIds(orders.startCheckout(cartId, pendingIds)))
                    .containsExactlyInAnyOrderElementsOf(pendingIds);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 上限を一時的に上げる（上限を超えた状態を作るため）。
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
     * 支払待ちの注文を、指定した件数まで続けて作る（注文ごとに別の商品）。
     *
     * @param count 作る件数
     * @return 作った注文の ID（作った順。呼ぶ側で変えられる一覧）
     */
    private List<UUID> placePendingOrdersOfNewVariants(int count) throws Exception {
        var variants = new ArrayList<UUID>();
        for (int i = 0; i < count; i++) {
            variants.add(fixture.stockedVariant(3));
        }
        return orders.placePendingOrders(cartId, variants);
    }
}
