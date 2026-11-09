package com.ecsite.module.order.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.TestJson;
import com.ecsite.TestTokens;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.module.order.application.payment.OrderPayments;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.ecsite.OrderTestData.startCheckoutRequest;
import static com.ecsite.jooq.ordering.Tables.CHECKOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文手続きの受け入れテスト。<br />
 * テストごとにロールバックする。カートの変更のイベントによる引当の解除は、コミットが必要なので
 * {@link CheckoutCartChangeApiTest} で確認する。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class CheckoutApiTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    @Autowired
    StockReservationApi stockReservationApi;

    @Autowired
    DSLContext dsl;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    CheckoutRepository checkoutRepository;

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    private CatalogTestData data;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    private UUID standard;

    private UUID cartId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, clock);
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        data = new CatalogTestData(mockMvc, clock);
        orders = new OrderTestData(mockMvc);
        standard = data.taxCategory("標準", "10");
        cartId = orders.cart();
    }

    @Test
    @DisplayName("AC-82 注文手続きに進むと、全品目をカートの数量だけ 15 分間引き当てる")
    void reservesAllItems() throws Exception {
        var shirt = stockedVariant(3);
        var cap = stockedVariant(5);
        orders.add(cartId, shirt, 2);
        orders.add(cartId, cap, 1);

        var checkoutId = startCheckout(cartId);

        assertThat(stockReservationApi.sellableQuantities(List.of(shirt, cap), null))
                .containsEntry(shirt, 1).containsEntry(cap, 4);
        mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(checkoutId.toString()))
                .andExpect(jsonPath("$.expiresAt").value(clock.instant().plus(Duration.ofMinutes(15)).toString()))
                .andExpect(jsonPath("$.orderId").value(nullValue()));
    }

    @Test
    @DisplayName("AC-82 1 品目でも引き当てられなければ 409 で、どれも引き当てない")
    void reservesNothingWhenShort() throws Exception {
        var plenty = stockedVariant(5);
        var scarce = stockedVariant(2);
        orders.add(cartId, plenty, 1);
        orders.add(cartId, scarce, 2);
        // ほかの消費者が 1 個引き当てる
        stockReservationApi.reserve(UUID.randomUUID(), Map.of(scarce, 1), clock.instant().plus(Duration.ofMinutes(15)));

        mockMvc.perform(post("/api/store/carts/" + cartId + "/checkouts"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"));

        assertThat(stockReservationApi.sellableQuantities(List.of(plenty), null)).containsEntry(plenty, 5);
        // どの品目が足りないかは、カートの表示で示す
        mockMvc.perform(get("/api/store/carts/" + cartId))
                .andExpect(jsonPath("$.items[1].unpurchasableReason").value("INSUFFICIENT_STOCK"));
    }

    @Test
    @DisplayName("AC-88 空のカート、購入できない品目を含むカートは 400 で注文手続きに進めない")
    void rejectsUnorderableCart() throws Exception {
        mockMvc.perform(post("/api/store/carts/" + cartId + "/checkouts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CART_NOT_ORDERABLE"));

        var product = data.product(standard, "販売停止にする", 1000, "色").variant("白").publish();
        inventory.stock(product.variantIds.getFirst(), product.id, 3);
        orders.add(cartId, product.variantIds.getFirst(), 1);
        product.suspend(0);

        mockMvc.perform(post("/api/store/carts/" + cartId + "/checkouts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CART_NOT_ORDERABLE"));
    }

    @Test
    @DisplayName("AC-84 進み直すと、前の注文手続きの引当を解いて、その時点のカートで引き当て直す")
    void replacesReservationOnRestart() throws Exception {
        var variant = stockedVariant(3);
        orders.add(cartId, variant, 2);
        startCheckout(cartId);

        clock.setInstant(clock.instant().plus(Duration.ofMinutes(10)));
        var second = startCheckout(cartId);

        assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, 1);
        // 期限も 15 分に戻る
        mockMvc.perform(get("/api/store/checkouts/" + second))
                .andExpect(jsonPath("$.expiresAt").value(clock.instant().plus(Duration.ofMinutes(15)).toString()));
    }

    @Test
    @DisplayName("AC-85 注文手続きの表示：内容と金額を返す。自分の引当は販売可能数から除くので、購入できる")
    void showsCheckoutExcludingOwnReservation() throws Exception {
        var variant = stockedVariant(1);
        orders.add(cartId, variant, 1);

        var checkoutId = startCheckout(cartId);

        mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                .andExpect(jsonPath("$.items[0].variantId").value(variant.toString()))
                .andExpect(jsonPath("$.items[0].purchasable").value(true))
                .andExpect(jsonPath("$.amounts.itemsTotal").value(1100))
                .andExpect(jsonPath("$.amounts.shippingFee").value(1000))
                .andExpect(jsonPath("$.amounts.total").value(2100));
        // カートの表示でも、注文手続き中の自分の引当は除いて判定する
        mockMvc.perform(get("/api/store/carts/" + cartId))
                .andExpect(jsonPath("$.items[0].purchasable").value(true))
                .andExpect(jsonPath("$.orderable").value(true));
    }

    @Test
    @DisplayName("AC-85 注文手続きの表示：税率ごとの消費税額を返す（税率の高い順。送料は 10% の対象）")
    void showsTaxAmountPerRate() throws Exception {
        var reduced = data.taxCategory("軽減", "8");
        var standardVariant = stockedVariant(1);
        var reducedProduct = data.product(reduced, "軽減の商品", 1000).publish();
        var reducedVariant = reducedProduct.variantIds.getFirst();
        inventory.stock(reducedVariant, reducedProduct.id, 1);
        orders.add(cartId, standardVariant, 1);
        orders.add(cartId, reducedVariant, 1);

        var checkoutId = startCheckout(cartId);

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

    @Test
    @DisplayName("存在しないカート・注文手続きは 404")
    void returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/store/carts/" + UUID.randomUUID() + "/checkouts")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/store/checkouts/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Nested
    @DisplayName("支払待ちの注文の確認")
    class PendingOrderConfirmation {

        @Test
        @DisplayName("AC-406-1、AC-406-18 支払待ちの注文があれば、開始は確認を求める 409 を返し、注文手続きを作らない")
        void requiresConfirmation() throws Exception {
            orders.placePendingOrder(stockedVariant(3), 1);
            var checkouts = checkoutCount();

            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));

            assertThat(checkoutCount()).isEqualTo(checkouts);
        }

        @Test
        @DisplayName("AC-406-1 古い一覧（今の支払待ちの注文を含まない）を送ると、確認を求める")
        void rejectsStaleList() throws Exception {
            var first = orders.placePendingOrder(stockedVariant(5), 1);
            var second = orders.placeFromCheckout(orders.startCheckout(cartId, List.of(first)));
            var checkouts = checkoutCount();

            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId, List.of(first))));

            assertThat(checkoutCount()).isEqualTo(checkouts);
            assertThat(acknowledgedOrderIds(orders.startCheckout(cartId, List.of(first, second)))).containsExactlyInAnyOrder(first, second);
        }

        @Test
        @DisplayName("AC-406-1 本文の一覧が空、null、または null の要素を含むなら、確認していないものとして確認を求める")
        void treatsEmptyListAsNotAcknowledged() throws Exception {
            orders.placePendingOrder(stockedVariant(3), 1);

            for (var body : List.of("{}", "{\"acknowledgedOrderIds\": []}", "{\"acknowledgedOrderIds\": null}",
                    "{\"acknowledgedOrderIds\": [null]}")) {
                expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)
                        .contentType(MediaType.APPLICATION_JSON).content(body)));
            }
        }

        @Test
        @DisplayName("AC-406-2 確認を求めるときは、前の注文手続きの引当を解かず、注文手続きも増やさない")
        void keepsPreviousReservationWhenRejected() throws Exception {
            var variant = stockedVariant(5);
            var pending = orders.placePendingOrder(variant, 1);
            orders.startCheckout(cartId, List.of(pending));
            assertThat(sellable(variant)).isEqualTo(3);
            var checkouts = checkoutCount();

            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));

            assertThat(sellable(variant)).isEqualTo(3);
            assertThat(checkoutCount()).isEqualTo(checkouts);
        }

        @Test
        @DisplayName("AC-406-3 カートを変えた後も、品目を比べずに確認を求める")
        void requiresConfirmationAfterCartChange() throws Exception {
            orders.placePendingOrder(stockedVariant(3), 1);
            orders.add(cartId, stockedVariant(3), 1);

            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));
        }

        @Test
        @DisplayName("AC-406-3 購入できない品目（販売停止）があっても、購入の可否の判定より先に確認を求める")
        void requiresConfirmationBeforeOrderableCheck() throws Exception {
            orderThenSuspend();

            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));
        }

        @Test
        @DisplayName("AC-406-3 在庫が足りない品目があっても、購入の可否の判定より先に確認を求める")
        void requiresConfirmationBeforeStockCheck() throws Exception {
            // 在庫の全てを注文したので、カートに残った品目は在庫が足りない
            orders.placePendingOrder(stockedVariant(1), 1);

            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));
        }

        @Test
        @DisplayName("AC-406-3 カートが空でも、購入の可否の判定より先に確認を求める")
        void requiresConfirmationForEmptyCart() throws Exception {
            var variant = stockedVariant(3);
            orders.placePendingOrder(variant, 1);
            mockMvc.perform(delete("/api/store/carts/" + cartId + "/items/" + variant)).andExpect(status().isNoContent());

            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));
        }

        @Test
        @DisplayName("AC-406-7 確認した一覧を送ると、通常と同じく引き当てて注文手続きを始める")
        void startsWhenAcknowledged() throws Exception {
            var variant = stockedVariant(5);
            var pending = orders.placePendingOrder(variant, 1);

            var checkoutId = orders.startCheckout(cartId, List.of(pending));

            assertThat(sellable(variant)).isEqualTo(3);
            mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.expiresAt").value(clock.instant().plus(Duration.ofMinutes(15)).toString()));
        }

        @Test
        @DisplayName("AC-406-7 保存するのは受けた一覧でなく、その時点で支払待ちの注文の ID である")
        void savesCurrentPendingOrderIds() throws Exception {
            var pending = orders.placePendingOrder(stockedVariant(3), 1);

            var checkoutId = orders.startCheckout(cartId, List.of(pending, UUID.randomUUID()));

            assertThat(acknowledgedOrderIds(checkoutId)).containsExactly(pending);
        }

        @Test
        @DisplayName("AC-406-7 確認した後も、在庫が足りなければ 409 で始められない")
        void checksStockAfterAcknowledged() throws Exception {
            var pending = orders.placePendingOrder(stockedVariant(1), 1);

            mockMvc.perform(startCheckoutRequest(cartId, List.of(pending)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"));
        }

        @Test
        @DisplayName("AC-406-7 確認した後も、購入できない品目があれば 400 で始められない")
        void checksOrderableAfterAcknowledged() throws Exception {
            var pending = orderThenSuspend();

            mockMvc.perform(startCheckoutRequest(cartId, List.of(pending)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CART_NOT_ORDERABLE"));
        }

        @Test
        @DisplayName("AC-406-10 確認はその注文手続きにだけ有効で、次に始めるときは再び確認を求める")
        void requiresConfirmationAgain() throws Exception {
            var pending = orders.placePendingOrder(stockedVariant(5), 1);
            orders.startCheckout(cartId, List.of(pending));

            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));

            assertThat(acknowledgedOrderIds(orders.startCheckout(cartId, List.of(pending)))).containsExactly(pending);
        }

        @Test
        @DisplayName("AC-406-16 支払の期限切れでキャンセルされた後は、確認なしで始められ、確認した注文は空になる")
        void startsAfterExpiry() throws Exception {
            var pending = orders.placePendingOrder(stockedVariant(3), 1);
            OrderPayments.expire(applicationContext, pending);

            assertThat(acknowledgedOrderIds(startCheckout(cartId))).isEmpty();
        }

        @Test
        @DisplayName("AC-406-16 与信の成功で受付になった後は、確認なしで始められ、確認した注文は空になる")
        void startsAfterAcceptance() throws Exception {
            var variant = stockedVariant(3);
            var pending = orders.placePendingOrder(variant, 1);
            OrderPayments.accept(applicationContext, pending);
            // 受付になると、注文した品目はカートから除かれる
            orders.add(cartId, variant, 1);

            assertThat(acknowledgedOrderIds(startCheckout(cartId))).isEmpty();
        }

        @Test
        @DisplayName("AC-406-16 支払待ちの注文がなければ、送った一覧は保存しない")
        void ignoresListWithoutPendingOrders() throws Exception {
            orders.add(cartId, stockedVariant(3), 1);

            assertThat(acknowledgedOrderIds(orders.startCheckout(cartId, List.of(UUID.randomUUID())))).isEmpty();
        }

        @Test
        @DisplayName("AC-406-17 他の会員のカートは、持ち主や自分に支払待ちの注文があっても 404 で、注文を示さない")
        void hidesOrdersFromOtherMembers() throws Exception {
            var pending = orders.placePendingOrder(stockedVariant(5), 1);
            var other = TestTokens.member().subject(UUID.randomUUID()).email("other@example.com");
            new OrderTestData(mockMvc, null, null, other.bearer()).placePendingOrder(stockedVariant(3), 1);
            var checkouts = checkoutCount();

            mockMvc.perform(startCheckoutRequest(cartId).with(other.bearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
            mockMvc.perform(startCheckoutRequest(cartId, List.of(pending)).with(other.bearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));

            assertThat(checkoutCount()).isEqualTo(checkouts);
        }

        @Test
        @DisplayName("BR-406-1 支払の期限を過ぎ、まだキャンセルされていない注文も、支払待ちとして確認を求める")
        void treatsOverdueOrderAsPending() throws Exception {
            var pending = orders.placePendingOrder(stockedVariant(3), 1);
            var order = mockMvc.perform(get("/api/store/orders/" + pending)).andReturn().getResponse().getContentAsString();
            clock.setInstant(Instant.parse(JsonPath.read(order, "$.paymentDeadline")).plusSeconds(1));

            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));
        }

        @Test
        @DisplayName("BR-406-2 他の会員の支払待ちの注文は、判定の対象にしない")
        void ignoresPendingOrdersOfOtherMembers() throws Exception {
            var other = TestTokens.member().subject(UUID.randomUUID()).email("other@example.com");
            new OrderTestData(mockMvc, null, null, other.bearer()).placePendingOrder(stockedVariant(3), 1);
            orders.add(cartId, stockedVariant(3), 1);

            assertThat(acknowledgedOrderIds(startCheckout(cartId))).isEmpty();
        }
    }

    @Nested
    @DisplayName("支払待ちの注文の上限")
    class PendingOrderLimit {

        /**
         * 注文の設定（上限の既定は 3 件。上限を超えた状態を作るテストだけ、上限を差し替える）
         */
        @MockitoSpyBean
        OrderProperties properties;

        @Test
        @DisplayName("AC-411-3 支払待ちの注文が上限（3 件）に達していると、全て確認していても開始は 409 で拒否し、注文手続きも引当も変えない")
        void rejectsStartAtLimit() throws Exception {
            var variant = stockedVariant(5);
            var pendingIds = placePendingOrders(2);
            // 3 件目の支払待ちの注文は、このバリアントがカートに残ったまま作る
            orders.add(cartId, variant, 1);
            pendingIds.add(orders.placeFromCheckout(orders.startCheckout(cartId, pendingIds)));
            assertThat(sellable(variant)).isEqualTo(4);
            var checkouts = checkoutCount();

            expectLimitExceeded(mockMvc.perform(startCheckoutRequest(cartId, pendingIds)));

            // カートに残った品目を、新たに引き当てない
            assertThat(sellable(variant)).isEqualTo(4);
            assertThat(checkoutCount()).isEqualTo(checkouts);
        }

        @Test
        @DisplayName("AC-411-4 上限に達していても、確認していない支払待ちの注文があれば、上限でなく確認を求める 409 を返す")
        void requiresConfirmationBeforeLimit() throws Exception {
            var pendingIds = placePendingOrders(3);
            orders.add(cartId, stockedVariant(3), 1);

            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId)));
            expectConfirmationRequired(mockMvc.perform(startCheckoutRequest(cartId, pendingIds.subList(0, 2))));
        }

        @Test
        @DisplayName("AC-411-6 支払の期限切れでキャンセルされて上限より少なくなると、再び注文手続きを開始できる")
        void startsAfterExpiry() throws Exception {
            var pendingIds = placePendingOrders(3);
            orders.add(cartId, stockedVariant(3), 1);
            expectLimitExceeded(mockMvc.perform(startCheckoutRequest(cartId, pendingIds)));

            OrderPayments.expire(applicationContext, pendingIds.removeFirst());

            assertThat(acknowledgedOrderIds(orders.startCheckout(cartId, pendingIds)))
                    .containsExactlyInAnyOrderElementsOf(pendingIds);
        }

        @Test
        @DisplayName("AC-411-6 与信の成功で受付になって上限より少なくなると、再び注文手続きを開始できる")
        void startsAfterAcceptance() throws Exception {
            var pendingIds = placePendingOrders(3);
            orders.add(cartId, stockedVariant(3), 1);
            expectLimitExceeded(mockMvc.perform(startCheckoutRequest(cartId, pendingIds)));

            OrderPayments.accept(applicationContext, pendingIds.removeFirst());

            assertThat(acknowledgedOrderIds(orders.startCheckout(cartId, pendingIds)))
                    .containsExactlyInAnyOrderElementsOf(pendingIds);
        }

        @Test
        @DisplayName("AC-411-8 上限を超える件数（4 件）の支払待ちの注文を持つ会員の開始も、409 で拒否する")
        void rejectsStartAboveLimit() throws Exception {
            // 変更の適用前の状態を作るため、4 件を作る間だけ上限を上げる（上限が 3 件のままでは 4 件目を注文できない）
            doReturn(10).when(properties).pendingOrderLimit();
            var pendingIds = placePendingOrders(4);
            doReturn(3).when(properties).pendingOrderLimit();
            orders.add(cartId, stockedVariant(3), 1);

            expectLimitExceeded(mockMvc.perform(startCheckoutRequest(cartId, pendingIds)));
        }

        @Test
        @DisplayName("AC-411-3 支払待ちの注文が上限より 1 件少ない（2 件）ときは、確認すれば開始できる")
        void startsBelowLimit() throws Exception {
            var pendingIds = placePendingOrders(2);
            orders.add(cartId, stockedVariant(3), 1);

            assertThat(acknowledgedOrderIds(orders.startCheckout(cartId, pendingIds)))
                    .containsExactlyInAnyOrderElementsOf(pendingIds);
        }

        /**
         * 支払待ちの注文を、指定した件数まで続けて作る（注文ごとに別の商品）
         */
        private List<UUID> placePendingOrders(int count) throws Exception {
            var variants = new ArrayList<UUID>();
            for (int i = 0; i < count; i++) {
                variants.add(stockedVariant(3));
            }
            return orders.placePendingOrders(cartId, variants);
        }
    }

    // ============================== 補助 ==============================

    /**
     * 支払待ちの注文が上限に達していることを示す 409 であることを確認する
     */
    private static void expectLimitExceeded(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PENDING_ORDER_LIMIT_EXCEEDED"));
    }

    /**
     * 注文手続きに進み、201 と Location を確認して、注文手続き ID を返す
     */
    private UUID startCheckout(UUID cart) throws Exception {
        var body = mockMvc.perform(startCheckoutRequest(cart))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return TestJson.createdId(body);
    }

    /**
     * 商品を注文した後に販売停止にして、注文 ID を返す（カートには購入できない品目が残る）
     */
    private UUID orderThenSuspend() throws Exception {
        var product = data.product(standard, "販売停止にする", 1000, "色").variant("白").publish();
        var variant = product.variantIds.getFirst();
        inventory.stock(variant, product.id, 3);
        var pending = orders.placePendingOrder(variant, 1);
        product.suspend(0);
        return pending;
    }

    /**
     * 確認を求める 409 であることを確認する
     */
    private static void expectConfirmationRequired(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PENDING_ORDER_CONFIRMATION_REQUIRED"));
    }

    /**
     * 注文手続きに保存した、確認した注文の ID を返す
     */
    private Set<UUID> acknowledgedOrderIds(UUID checkoutId) {
        return checkoutRepository.findById(checkoutId).orElseThrow().acknowledgedOrderIds();
    }

    /**
     * テストのカートの注文手続きの数を返す
     */
    private int checkoutCount() {
        return dsl.fetchCount(CHECKOUT, CHECKOUT.CART_ID.eq(cartId));
    }

    /**
     * バリアントの販売可能数を返す
     */
    private int sellable(UUID variant) {
        return stockReservationApi.sellableQuantities(List.of(variant), null).get(variant);
    }

    /**
     * 公開した商品（1,000 円・10%）のバリアントを作り、在庫数を設定して、バリアント ID を返す
     */
    private UUID stockedVariant(int quantity) throws Exception {
        var product = data.product(standard, "商品", 1000).publish();
        var variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, quantity);
        return variantId;
    }
}
