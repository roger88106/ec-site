package com.ecsite.module.order.presentation;

import com.ecsite.TestTokens;
import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.event.OrderPlaced;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文の成立と注文の表示の受け入れテスト（T2313、AC-86〜94、107）。<br />
 * 同時の注文と二重送信は、コミットが必要なので {@code PlaceOrderConcurrencyTest} で確かめる。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@RecordApplicationEvents
@Transactional
class PlaceOrderApiTest {

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

    @Autowired
    StockReservationApi stockReservationApi;

    @Autowired
    ApplicationEvents events;

    @Autowired
    DSLContext dsl;

    private CatalogTestData data;

    private UUID standard;

    private UUID cartId;

    private CatalogTestData.TestProduct shirt;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        data = new CatalogTestData(mockMvc, clock);
        standard = data.taxCategory("標準", "10");
        shirt = data.product(standard, "T シャツ", 2700, "色", "サイズ").variant("白", "M").publish();
        stock(shirt.variantIds.getFirst(), 3);
        cartId = createCart();
        add(shirt.variantIds.getFirst(), 2);
    }

    @Nested
    @DisplayName("AC-87、88、89、90、91、92 注文の成立")
    class Place {

        @Test
        @DisplayName("AC-87、90、107 成立すると在庫が減り、引当が消え、カートが空になり、注文番号が振られる")
        void placesOrder() throws Exception {
            var checkoutId = startCheckout();

            var orderId = place(checkoutId, 6940)
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn().getResponse().getContentAsString();

            var variant = shirt.variantIds.getFirst();
            assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, 1);
            mockMvc.perform(get("/api/store/carts/" + cartId)).andExpect(jsonPath("$.items").isEmpty());
            var id = JsonPath.<String>read(orderId, "$.id");
            mockMvc.perform(get("/api/store/orders/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderNumber", matchesPattern("20261008-\\d{6}")))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
            // 調整履歴に、理由「注文」と注文番号が残る（AC-107）
            var orderNumber = JsonPath.<String>read(mockMvc.perform(get("/api/store/orders/" + id))
                    .andReturn().getResponse().getContentAsString(), "$.orderNumber");
            mockMvc.perform(get("/api/admin/inventory/variants/" + variant + "/adjustments"))
                    .andExpect(jsonPath("$.items[0].reason").value("ORDER"))
                    .andExpect(jsonPath("$.items[0].delta").value(-2))
                    .andExpect(jsonPath("$.items[0].orderNumber").value(orderNumber))
                    .andExpect(jsonPath("$.items[0].orderId").value(id));
            assertThat(events.stream(OrderPlaced.class)).extracting(OrderPlaced::orderId).containsExactly(UUID.fromString(id));
        }

        @Test
        @DisplayName("AC-87 引当の期限が切れていても、足りれば成立する")
        void placesAfterExpiryWhenSufficient() throws Exception {
            var checkoutId = startCheckout();
            clock.setInstant(clock.instant().plus(Duration.ofMinutes(20)));

            place(checkoutId, 6940).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-87 期限切れの間にほかの消費者に引き当てられて足りなければ 409 で、在庫も注文もカートも変わらない")
        void rejectsWhenOthersReservedAfterExpiry() throws Exception {
            var checkoutId = startCheckout();
            clock.setInstant(clock.instant().plus(Duration.ofMinutes(20)));
            var variant = shirt.variantIds.getFirst();
            stockReservationApi.reserve(UUID.randomUUID(), Map.of(variant, 2), clock.instant().plus(Duration.ofMinutes(15)));

            place(checkoutId, 6940)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"));

            mockMvc.perform(get("/api/admin/inventory/variants/" + variant)).andExpect(jsonPath("$.quantity").value(3));
            mockMvc.perform(get("/api/store/carts/" + cartId)).andExpect(jsonPath("$.items[0].quantity").value(2));
            mockMvc.perform(get("/api/store/checkouts/" + checkoutId)).andExpect(jsonPath("$.orderId").isEmpty());
        }

        @Test
        @DisplayName("AC-89 確認した総額と、注文時点の総額が違えば 409")
        void rejectsChangedTotal() throws Exception {
            var checkoutId = startCheckout();
            shirt.changePrice(standard, 3000);

            place(checkoutId, 6940)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("TOTAL_AMOUNT_CHANGED"));
            place(checkoutId, 7600).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("注文手続きの後にカートが変われば 409")
        void rejectsChangedCart() throws Exception {
            var checkoutId = startCheckout();
            // 減らす変更は販売可能数を確かめないので、注文手続き中でもできる
            mockMvc.perform(put("/api/store/carts/" + cartId + "/items/" + shirt.variantIds.getFirst())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantity": 1}"""))
                    .andExpect(status().isNoContent());

            place(checkoutId, 6940)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CART_CHANGED"));
        }

        @Test
        @DisplayName("AC-86 入力の形が違えば 400")
        void rejectsInvalidInput() throws Exception {
            var checkoutId = startCheckout();

            placeWith(checkoutId, """
                    {"expectedTotal": 6940, "shippingAddress": {"name": "山田 太郎",
                     "postalCode": "100-0001", "prefecture": "東京", "address": "千代田区", "phone": "03-1234-5678"}}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PREFECTURE"));
            placeWith(checkoutId, """
                    {"expectedTotal": 6940, "shippingAddress": {"name": "山田 太郎",
                     "postalCode": "1000", "prefecture": "東京都", "address": "千代田区", "phone": "03-1234-5678"}}""")
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("都道府県が契約の長さ（4 文字）を超えれば、入力の検証で 400")
        void rejectsTooLongPrefecture() throws Exception {
            var checkoutId = startCheckout();

            placeWith(checkoutId, """
                    {"expectedTotal": 6940, "shippingAddress": {"name": "山田 太郎",
                     "postalCode": "100-0001", "prefecture": "東京都千代田", "address": "千代田区", "phone": "03-1234-5678"}}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("shippingAddress.prefecture"));
        }

        @Test
        @DisplayName("AC-91 同じ注文手続きでの再送には、作成済みの注文 ID を 200 で返し、注文は 1 件だけ")
        void returnsExistingOrderOnResend() throws Exception {
            var checkoutId = startCheckout();
            var first = JsonPath.<String>read(place(checkoutId, 6940).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString(), "$.id");

            place(checkoutId, 6940)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(first));

            var variant = shirt.variantIds.getFirst();
            assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, 1);
        }

        @Test
        @DisplayName("存在しない注文手続きは 404")
        void rejectsUnknownCheckout() throws Exception {
            place(UUID.randomUUID(), 6940).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("AC-92、93、94 注文の表示")
    class Show {

        @Test
        @DisplayName("AC-92、93 明細は注文時点の写しで、その後に商品の価格を変えても注文の内容と金額は変わらない")
        void showsOrderAsPlaced() throws Exception {
            var orderId = placeOrder();
            shirt.changePrice(standard, 9999);

            mockMvc.perform(get("/api/store/orders/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(orderId))
                    .andExpect(jsonPath("$.orderedAt").exists())
                    .andExpect(jsonPath("$.lines[0].productCode").value(shirt.productCode))
                    .andExpect(jsonPath("$.lines[0].variantCode").value(shirt.variantCodes.getFirst()))
                    .andExpect(jsonPath("$.lines[0].productName").value("T シャツ"))
                    .andExpect(jsonPath("$.lines[0].axisValues[*].value", contains("白", "M")))
                    .andExpect(jsonPath("$.lines[0].unitPrice.exclTax").value(2700))
                    .andExpect(jsonPath("$.lines[0].unitPrice.inclTax").value(2970))
                    .andExpect(jsonPath("$.lines[0].ratePercent").value(10))
                    .andExpect(jsonPath("$.lines[0].quantity").value(2))
                    .andExpect(jsonPath("$.lines[0].subtotal").value(5940))
                    .andExpect(jsonPath("$.amounts.itemsTotal").value(5940))
                    .andExpect(jsonPath("$.amounts.shippingFee").value(1000))
                    .andExpect(jsonPath("$.amounts.total").value(6940))
                    .andExpect(jsonPath("$.amounts.taxes[0].taxAmount").value(630))
                    .andExpect(jsonPath("$.email").value("member@example.com"))
                    .andExpect(jsonPath("$.shippingAddress.postalCode").value("1000001"))
                    .andExpect(jsonPath("$.shippingAddress.phone").value("0312345678"))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("AC-94 存在しない注文は 404")
        void returnsNotFound() throws Exception {
            mockMvc.perform(get("/api/store/orders/" + UUID.randomUUID())).andExpect(status().isNotFound());
        }
    }

    // ============================== 補助 ==============================

    /**
     * T シャツ 2 枚を注文し、注文 ID を返す
     */
    private String placeOrder() throws Exception {
        var body = place(startCheckout(), 6940).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /**
     * 確認した総額を指定して、決まったお届け先で注文する
     */
    private ResultActions place(UUID checkoutId, long expectedTotal) throws Exception {
        return placeWith(checkoutId, """
                {"expectedTotal": %d, "shippingAddress": {"name": "山田 太郎",
                 "postalCode": "100-0001", "prefecture": "東京都", "address": "千代田区千代田 1-1", "phone": "03-1234-5678"}}"""
                .formatted(expectedTotal));
    }

    /**
     * 本文を指定して注文する
     */
    private ResultActions placeWith(UUID checkoutId, String body) throws Exception {
        return mockMvc.perform(post("/api/store/checkouts/" + checkoutId + "/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /**
     * 注文手続きに進み、注文手続き ID を返す
     */
    private UUID startCheckout() throws Exception {
        var body = mockMvc.perform(post("/api/store/carts/" + cartId + "/checkouts"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    /**
     * 既定の会員（TestSecurityConfig）の会員のカートの ID を返す（注文手続きは会員のカートからだけ始められる。AC-125）
     */
    private UUID createCart() throws Exception {
        var body = mockMvc.perform(get("/api/store/me/cart")).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.cartId"));
    }

    /**
     * カートに入れ、204 を確かめる
     */
    private void add(UUID variantId, int quantity) throws Exception {
        mockMvc.perform(post("/api/store/carts/" + cartId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantId": "%s", "quantity": %d}""".formatted(variantId, quantity)))
                .andExpect(status().isNoContent());
    }

    /**
     * バリアントの在庫を作り、在庫数を設定する（入荷として調整する）
     */
    private void stock(UUID variantId, int quantity) {
        stockItemRepository.createIfAbsent(variantId, shirt.id);
        stockItemRepository.adjust(variantId,
                StockAdjustment.create(quantity, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, clock.instant()));
    }
}
