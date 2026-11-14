package com.ecsite.module.order.presentation;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.inventory.Tables.STOCK_RESERVATION;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.SHIRT_QUANTITY;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.SHIRT_STOCK;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.TOTAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文の成立での在庫の引き当ての受け入れテスト
 *
 * <p>注文の成立で在庫数が減り、引当が解かれ、調整履歴が残ること、引当の期限が切れたときの扱いを確認する。
 * 在庫のモジュールの減算そのもの（一部の品目が足りないときなど）は {@code StockOrderApiTest} が確認する。
 * 在庫の行を待つ間に在庫がなくなる場合は {@code PlaceOrderConcurrencyTest} が確認する。
 *
 * <p>準備は {@link PlaceOrderFixture}（在庫数 3 の T シャツ 2 枚のカート）。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class PlaceOrderStockApiTest {

    // ============================== 定数 ==============================

    /**
     * 引当の期限が切れるまで進める時間
     *
     * <p>注文手続きの引当の期限（既定の 15 分）より長くする。
     */
    private static final Duration PAST_EXPIRY = Duration.ofMinutes(20);

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    StockReservationApi stockReservationApi;

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
    @DisplayName("成立のときの在庫")
    class Commit {

        @Test
        @DisplayName("AC-87、144 成立すると、在庫数が注文の数量だけ減る")
        void decreasesQuantity() throws Exception {
            // 操作：在庫数 3 の T シャツを 2 枚注文する
            fixture.placeOrder();

            // 確認：在庫数が 1
            fixture.expectQuantity(SHIRT_STOCK - SHIRT_QUANTITY);
        }

        @Test
        @DisplayName("AC-87 成立すると、注文手続きの引当が解かれる")
        void releasesReservation() throws Exception {
            // 準備：注文手続きを始めて引き当てる
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);

            // 確認（前提）：注文手続きの引当がある
            assertThat(reservationCount(checkoutId)).isEqualTo(1);

            // 操作：注文する
            fixture.placed(checkoutId);

            // 確認：注文手続きの引当がない
            assertThat(reservationCount(checkoutId)).isZero();
        }

        @Test
        @DisplayName("AC-87 成立すると、販売可能数は減った在庫数と同じになる")
        void sellableEqualsRemainingQuantity() throws Exception {
            // 操作：在庫数 3 の T シャツを 2 枚注文する
            fixture.placeOrder();

            // 確認：販売可能数が 1（在庫数が減り、引当が残っていない）
            var variant = fixture.shirtVariant();
            assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, SHIRT_STOCK - SHIRT_QUANTITY);
        }

        @Test
        @DisplayName("AC-107 成立すると、在庫の調整履歴に、理由「注文」と注文番号が残る")
        void recordsOrderAdjustment() throws Exception {
            // 操作：T シャツ 2 枚を注文する
            var orderId = fixture.placeOrder();

            // 確認：最新の調整履歴が、この注文による減算
            assertLatestAdjustmentIsOrder(orderId, orderNumberOf(orderId));
        }
    }

    @Nested
    @DisplayName("引当の期限切れ")
    class ExpiredReservation {

        @Test
        @DisplayName("AC-87 引当の期限が切れていても、足りれば成立する")
        void placesAfterExpiryWhenSufficient() throws Exception {
            // 準備：注文手続きを始め、引当の期限を過ぎる
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            clock.setInstant(clock.instant().plus(PAST_EXPIRY));

            // 操作と確認：201 で成立する
            fixture.place(checkoutId, TOTAL).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-87 期限切れの間にほかの消費者に引き当てられて足りなければ、在庫の不足を示す 409")
        void rejectsWhenOthersReservedAfterExpiry() throws Exception {
            // 準備：期限切れの間に、ほかの消費者が残りの在庫を引き当てる
            var checkoutId = checkoutReservedByOthersAfterExpiry();

            // 操作と確認：409 で、エラーコードが OUT_OF_STOCK
            fixture.place(checkoutId, TOTAL)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"));
        }

        @Test
        @DisplayName("AC-87 期限切れの間にほかの消費者に引き当てられて拒否したときは、在庫も注文もカートも変わらない")
        void keepsEverythingWhenRejected() throws Exception {
            // 準備：期限切れの間に、ほかの消費者が残りの在庫を引き当てる
            var checkoutId = checkoutReservedByOthersAfterExpiry();

            // 操作：注文して拒否される
            fixture.place(checkoutId, TOTAL);

            // 確認：在庫・注文・カートが注文の前のまま
            assertNothingChanged(checkoutId);
        }

        /**
         * 注文手続きを始め、引当の期限を過ぎた後に、ほかの消費者が T シャツ 2 枚を引き当てた状態を作る。
         *
         * <p>在庫数 3 のうち 2 枚がほかの消費者の引当になり、注文の数量（2 枚）に足りない。
         *
         * @return 注文手続き ID
         * @throws Exception API の呼び出しに失敗したとき
         */
        private UUID checkoutReservedByOthersAfterExpiry() throws Exception {
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            clock.setInstant(clock.instant().plus(PAST_EXPIRY));
            stockReservationApi.reserve(UUID.randomUUID(), Map.of(fixture.shirtVariant(), 2), clock.instant().plus(Duration.ofMinutes(15)));
            return checkoutId;
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * T シャツの最新の調整履歴が、注文による減算であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>理由が「注文」</li>
     *   <li>増減が注文の数量のマイナス</li>
     *   <li>注文番号</li>
     *   <li>注文 ID</li>
     * </ul>
     *
     * @param orderId     注文 ID（実行ごとに作るので、定数にできない）
     * @param orderNumber 注文番号（乱数で採番するので、定数にできない）
     * @throws Exception API の呼び出しに失敗したとき、または確認が通らないとき
     */
    private void assertLatestAdjustmentIsOrder(UUID orderId, String orderNumber) throws Exception {
        fixture.mockMvc.perform(get("/api/admin/inventory/variants/" + fixture.shirtVariant() + "/adjustments"))
                .andExpect(jsonPath("$.items[0].reason").value("ORDER"))
                .andExpect(jsonPath("$.items[0].delta").value(-SHIRT_QUANTITY))
                .andExpect(jsonPath("$.items[0].orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.items[0].orderId").value(orderId.toString()));
    }

    /**
     * 拒否された注文の前後で、在庫・注文・カートが変わっていないことを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>T シャツの在庫数が準備の時点のまま</li>
     *   <li>注文手続きから作った注文がない</li>
     *   <li>カートの T シャツの数量が準備の時点のまま</li>
     *   <li>注文手続きに注文 ID がない</li>
     * </ul>
     *
     * @param checkoutId 注文手続き ID（実行ごとに作るので、定数にできない）
     * @throws Exception API の呼び出しに失敗したとき、または確認が通らないとき
     */
    private void assertNothingChanged(UUID checkoutId) throws Exception {
        fixture.expectQuantity(SHIRT_STOCK);
        fixture.expectNotPlaced(checkoutId);
        fixture.mockMvc.perform(get("/api/store/carts/" + fixture.cartId)).andExpect(jsonPath("$.items[0].quantity").value(SHIRT_QUANTITY));
        fixture.mockMvc.perform(get("/api/store/checkouts/" + checkoutId)).andExpect(jsonPath("$.orderId").isEmpty());
    }

    // ============================== 準備の補助 ==============================

    /**
     * 注文の注文番号を、注文の API から読む。
     *
     * @param orderId 注文 ID
     * @return 注文番号
     * @throws Exception API の呼び出しに失敗したとき
     */
    private String orderNumberOf(UUID orderId) throws Exception {
        return JsonPath.read(fixture.mockMvc.perform(get("/api/store/orders/" + orderId))
                .andReturn().getResponse().getContentAsString(), "$.orderNumber");
    }

    /**
     * 注文手続きが持つ引当の件数を返す。
     *
     * @param checkoutId 注文手続き ID（引当の持ち主）
     * @return 引当の件数
     */
    private int reservationCount(UUID checkoutId) {
        return fixture.dsl.fetchCount(STOCK_RESERVATION, STOCK_RESERVATION.HOLDER_ID.eq(checkoutId));
    }
}
