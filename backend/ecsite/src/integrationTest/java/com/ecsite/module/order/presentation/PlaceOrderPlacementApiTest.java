package com.ecsite.module.order.presentation;

import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.http.TestJson;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.ecsite.module.order.presentation.PlaceOrderFixture.SHIRT_QUANTITY;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.SHIRT_STOCK;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.TOTAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文の成立の受け入れテスト
 *
 * <p>成立の応答と注文の状態、カートとイベント、注文番号、数量の上限、再送を確認する。ほかの観点は次のテストが確認する。
 * <ul>
 *   <li>在庫の引き当て：{@code PlaceOrderStockApiTest}</li>
 *   <li>総額とカートの変化：{@code PlaceOrderConflictApiTest}</li>
 *   <li>お届け先の検証：{@code PlaceOrderAddressApiTest}</li>
 *   <li>支払待ちの注文の確認：{@code PlaceOrderPendingConfirmationApiTest}</li>
 *   <li>支払待ちの注文の上限：{@code PlaceOrderPendingLimitApiTest}</li>
 *   <li>注文の表示：{@code PlaceOrderViewApiTest}</li>
 *   <li>年齢と性別：{@code PlaceOrderBuyerProfileApiTest}</li>
 *   <li>同時の注文と二重送信（コミットが必要）：{@code PlaceOrderConcurrencyTest}</li>
 * </ul>
 *
 * <p>準備は {@link PlaceOrderFixture}（T シャツ 2 枚のカート）。テストごとにロールバックする。
 */
@IntegrationTest
@RecordApplicationEvents
@Transactional
class PlaceOrderPlacementApiTest {

    // ============================== Bean ==============================

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    StockReservationApi stockReservationApi;

    @Autowired
    ApplicationEvents events;

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
    @DisplayName("注文の成立")
    class Placement {

        @Test
        @DisplayName("成立すると 201 で、注文の場所を返す")
        void returnsCreatedWithLocation() throws Exception {
            // 準備：T シャツ 2 枚の注文手続き
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);

            // 操作と確認：201 で、Location のヘッダーがある
            fixture.place(checkoutId, TOTAL)
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"));
        }

        @Test
        @DisplayName("AC-143、144 成立した注文は「支払待ち」になる")
        void isPendingPayment() throws Exception {
            // 準備：T シャツ 2 枚を注文する
            var orderId = fixture.placeOrder();

            // 操作と確認：注文の状態が「支払待ち」
            fixture.mockMvc.perform(get("/api/store/orders/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        }

        @Test
        @DisplayName("AC-90、144 成立しても、カートは変わらない")
        void keepsCart() throws Exception {
            // 準備：T シャツ 2 枚を注文する（注文した品目は、与信の成功でカートから除く）
            fixture.placeOrder();

            // 操作と確認：カートに T シャツ 2 枚が残っている
            fixture.mockMvc.perform(get("/api/store/carts/" + fixture.cartId))
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].variantId").value(fixture.shirtVariant().toString()))
                    .andExpect(jsonPath("$.items[0].quantity").value(SHIRT_QUANTITY));
        }

        @Test
        @DisplayName("成立すると、この注文の状態の変更のイベントだけが発行される")
        void publishesStatusChangedEvent() throws Exception {
            // 操作：T シャツ 2 枚を注文する
            var orderId = fixture.placeOrder();

            // 確認：状態の変更のイベントは、この注文の 1 件だけ
            assertThat(events.stream(OrderStatusChanged.class)).extracting(OrderStatusChanged::orderId).containsExactly(orderId);
        }

        @Test
        @DisplayName("AC-90 成立すると、注文日（日本時間）と 6 桁の数字の注文番号を採番する")
        void numbersOrder() throws Exception {
            // 準備：日本時間の 2026-10-08 に T シャツ 2 枚を注文する
            var orderId = fixture.placeOrder();

            // 操作と確認：注文番号が、注文日と 6 桁の数字
            fixture.mockMvc.perform(get("/api/store/orders/" + orderId))
                    .andExpect(jsonPath("$.orderNumber", matchesPattern("20261008-\\d{6}")));
        }

        @Test
        @DisplayName("AC-75 カートの 1 品目を上限の数量にして注文でき、注文の明細の数量はその値になる")
        void placesOrderWithMaxQuantity() throws Exception {
            // 準備：カートの上限と注文の明細の数量の制約は同じ値。上限まで買える在庫を追加し、カートの T シャツを上限の数量にする
            var max = maxCartQuantity();
            fixture.inventory.stock(fixture.shirtVariant(), fixture.shirt.id, max + 5);
            fixture.changeShirtQuantity(max);
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            var shown = fixture.mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            long total = ((Number) JsonPath.read(shown, "$.amounts.total")).longValue();

            // 操作：表示された総額で注文する
            var body = fixture.place(checkoutId, total).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            // 確認：明細の数量が上限の値
            fixture.mockMvc.perform(get("/api/store/orders/" + TestJson.createdId(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lines[0].quantity").value(max));
        }
    }

    @Nested
    @DisplayName("同じ注文手続きの再送")
    class Resend {

        @Test
        @DisplayName("AC-91 同じ注文手続きでの再送には、作成済みの注文 ID を 200 で返す")
        void returnsExistingOrder() throws Exception {
            // 準備：注文手続きから一度注文する
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            var first = fixture.placed(checkoutId);

            // 操作と確認：再送は 200 で、最初の注文 ID を返す
            fixture.expectExisting(checkoutId, first);
        }

        @Test
        @DisplayName("AC-91 同じ注文手続きで再送しても、注文は 1 件だけで、在庫は 1 回分だけ減る")
        void placesOnlyOnce() throws Exception {
            // 準備：注文手続きから一度注文する
            var checkoutId = fixture.orders.startCheckout(fixture.cartId);
            fixture.placed(checkoutId);

            // 操作：同じ注文手続きで再送する
            fixture.place(checkoutId, TOTAL);

            // 確認：注文は 1 件で、販売可能数は 1 回分（2 枚）だけ減っている
            var variant = fixture.shirtVariant();
            assertThat(fixture.orderCount(checkoutId)).isEqualTo(1);
            assertThat(stockReservationApi.sellableQuantities(List.of(new VariantId(variant)), null)).containsEntry(new VariantId(variant), SHIRT_STOCK - SHIRT_QUANTITY);
        }
    }

    @Nested
    @DisplayName("注文手続きがない")
    class UnknownCheckout {

        @Test
        @DisplayName("存在しない注文手続きは 404")
        void rejectsUnknownCheckout() throws Exception {
            // 操作と確認：ない注文手続きの ID で注文すると 404
            fixture.place(UUID.randomUUID(), TOTAL).andExpect(status().isNotFound());
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * カートの 1 品目の数量の上限（{@code Cart.MAX_QUANTITY}）を返す。
     *
     * <p>値を直書きせず、カートの定数から取る。
     * 定数はカートのモジュールの中だけに公開しているので、リフレクションで読む（FieldConstraintContractTest と同じ）。
     *
     * @return 数量の上限
     * @throws Exception 定数を読めないとき
     */
    private static int maxCartQuantity() throws Exception {
        var field = Class.forName("com.ecsite.module.cart.domain.Cart").getDeclaredField("MAX_QUANTITY");
        field.setAccessible(true);
        return field.getInt(null);
    }
}
