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
import com.ecsite.module.order.event.OrderStatusChanged;
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

import static com.ecsite.OrderTestData.placeOrderRequest;
import static com.ecsite.OrderTestData.startCheckoutRequest;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
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
    @DisplayName("注文の成立")
    class Place {

        @Test
        @DisplayName("AC-87、90、107、144 成立すると在庫が減り、引当が消え、注文番号が振られ、支払待ちになる。カートは変わらない")
        void placesOrder() throws Exception {
            var checkoutId = startCheckout();

            var orderId = place(checkoutId, 6940)
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn().getResponse().getContentAsString();

            var variant = shirt.variantIds.getFirst();
            assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, 1);
            // 注文した品目は、与信の成功でカートから除く（spec 007 で AC-90 を変更。AC-147）
            mockMvc.perform(get("/api/store/carts/" + cartId)).andExpect(jsonPath("$.items").isNotEmpty());
            var id = JsonPath.<String>read(orderId, "$.id");
            mockMvc.perform(get("/api/store/orders/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderNumber", matchesPattern("20261008-\\d{6}")))
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
            // 調整履歴に、理由「注文」と注文番号が残る（AC-107）
            var orderNumber = JsonPath.<String>read(mockMvc.perform(get("/api/store/orders/" + id))
                    .andReturn().getResponse().getContentAsString(), "$.orderNumber");
            mockMvc.perform(get("/api/admin/inventory/variants/" + variant + "/adjustments"))
                    .andExpect(jsonPath("$.items[0].reason").value("ORDER"))
                    .andExpect(jsonPath("$.items[0].delta").value(-2))
                    .andExpect(jsonPath("$.items[0].orderNumber").value(orderNumber))
                    .andExpect(jsonPath("$.items[0].orderId").value(id));
            assertThat(events.stream(OrderStatusChanged.class)).extracting(OrderStatusChanged::orderId).containsExactly(UUID.fromString(id));
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

            placeWith(checkoutId, body("1000", "13", "13101"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AC-251 マスターデータにない都道府県は 400（INVALID_PREFECTURE）")
        void rejectsUnknownPrefecture() throws Exception {
            var checkoutId = startCheckout();

            placeWith(checkoutId, body("100-0001", "48", "13101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PREFECTURE"));
        }

        @Test
        @DisplayName("AC-251 マスターデータにない市区町村と、選んだ都道府県に属さない市区町村は 400（INVALID_MUNICIPALITY）")
        void rejectsUnknownOrMismatchedMunicipality() throws Exception {
            var checkoutId = startCheckout();

            placeWith(checkoutId, body("100-0001", "13", "13999"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_MUNICIPALITY"));
            placeWith(checkoutId, body("100-0001", "13", "01101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_MUNICIPALITY"));
        }

        @Test
        @DisplayName("都道府県と市区町村のコードの形が違えば、入力の検証で 400")
        void rejectsMalformedCodes() throws Exception {
            var checkoutId = startCheckout();

            placeWith(checkoutId, body("100-0001", "東京都", "13101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("shippingAddress.prefectureCode"));
            placeWith(checkoutId, body("100-0001", "13", "131"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("shippingAddress.municipalityCode"));
        }

        /**
         * 郵便番号と都道府県・市区町村のコードを指定した、注文の要求の本文
         */
        private static String body(String postalCode, String prefectureCode, String municipalityCode) {
            return """
                    {"expectedTotal": 6940, "shippingAddress": {"name": "山田 太郎", "postalCode": "%s",
                     "prefectureCode": "%s", "municipalityCode": "%s", "address": "千代田 1-1", "phone": "03-1234-5678"}}"""
                    .formatted(postalCode, prefectureCode, municipalityCode);
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
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        }

        @Test
        @DisplayName("AC-258 注文は、お届け先の都道府県と市区町村のコードと、地域のマスターデータの名前を持つ")
        void showsCodesAndNames() throws Exception {
            var orderId = placeOrder();

            mockMvc.perform(get("/api/store/orders/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shippingAddress.prefectureCode").value("13"))
                    .andExpect(jsonPath("$.shippingAddress.prefectureName").value("東京都"))
                    .andExpect(jsonPath("$.shippingAddress.municipalityCode").value("13101"))
                    .andExpect(jsonPath("$.shippingAddress.municipalityName").value("千代田区"))
                    .andExpect(jsonPath("$.shippingAddress.address").value("千代田 1-1"));
        }

        @Test
        @DisplayName("AC-94 存在しない注文は 404")
        void returnsNotFound() throws Exception {
            mockMvc.perform(get("/api/store/orders/" + UUID.randomUUID())).andExpect(status().isNotFound());
        }
    }

    // ============================== 補助 ==============================

    @Nested
    @DisplayName("AC-212、222 注文時の年齢と性別")
    class BuyerProfile {

        @Test
        @DisplayName("AC-212、222 成立のときの token の生年月日と性別から、注文日（日本時間）の年齢と性別を記録する。後で token の値が変わっても、成立済みの注文は変わらない")
        void recordsAgeAndGenderAtPlacement() throws Exception {
            // NOW は日本時間の 2026-10-08 00:00。1990-10-08 生まれは、この日に 36 歳になる
            var first = placeAs(TestTokens.member().birthdate("1990-10-08").gender("female"));
            stock(shirt.variantIds.getFirst(), 2);

            var changed = TestTokens.member().birthdate("2000-01-01").gender("male");
            mockMvc.perform(get("/api/store/orders/" + first).with(changed.bearer())).andExpect(status().isOk());
            var second = placeAs(changed);

            assertThat(buyer(first)).containsExactly((short) 36, "FEMALE");
            assertThat(buyer(second)).containsExactly((short) 26, "MALE");
        }

        @Test
        @DisplayName("AC-208、222 token に生年月日と性別がなければ、年齢と性別は不明（空）")
        void recordsUnknownWithoutClaims() throws Exception {
            var orderId = placeAs(TestTokens.member());

            assertThat(buyer(orderId)).containsExactly(null, null);
        }

        @Test
        @DisplayName("AC-213 未来の生年月日と、一覧にない性別は、不明として記録する")
        void recordsUnknownForInvalidClaims() throws Exception {
            var orderId = placeAs(TestTokens.member().birthdate("2030-01-01").gender("unknown"));

            assertThat(buyer(orderId)).containsExactly(null, null);
        }

        /**
         * token を指定して、T シャツ 2 枚を注文し、注文 ID を返す
         */
        private UUID placeAs(TestTokens.Builder token) throws Exception {
            var checkoutId = UUID.fromString(JsonPath.read(mockMvc.perform(startCheckoutRequest(cartId).with(token.bearer()))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));
            var body = mockMvc.perform(placeOrderRequest(checkoutId, 6940).with(token.bearer()))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            return UUID.fromString(JsonPath.read(body, "$.id"));
        }

        /**
         * 注文に記録した年齢と性別
         */
        private List<Object> buyer(UUID orderId) {
            var record = dsl.select(SALES_ORDER.BUYER_AGE, SALES_ORDER.BUYER_GENDER).from(SALES_ORDER)
                    .where(SALES_ORDER.ID.eq(orderId)).fetchSingle();
            return java.util.Arrays.asList(record.value1(), record.value2());
        }
    }

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
        return mockMvc.perform(placeOrderRequest(checkoutId, expectedTotal));
    }

    /**
     * 本文を指定して注文する
     */
    private ResultActions placeWith(UUID checkoutId, String body) throws Exception {
        return mockMvc.perform(placeOrderRequest(checkoutId, body));
    }

    /**
     * 注文手続きに進み、注文手続き ID を返す
     */
    private UUID startCheckout() throws Exception {
        var body = mockMvc.perform(startCheckoutRequest(cartId))
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
