package com.ecsite.module.order.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestJson;
import com.ecsite.TestTokens;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.module.order.application.payment.OrderPayments;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.OrderTestData.placeOrderRequest;
import static com.ecsite.OrderTestData.startCheckoutRequest;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文の成立と注文の表示の受け入れテスト
 *
 * <p>同時の注文と二重送信は、コミットが必要なので {@code PlaceOrderConcurrencyTest} で確認する。テストごとにロールバックする。
 */
@IntegrationTest
@RecordApplicationEvents
@Transactional
class PlaceOrderApiTest {

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
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
    ApplicationEvents events;

    @Autowired
    DSLContext dsl;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    CheckoutRepository checkoutRepository;

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    private CatalogTestData catalog;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    private UUID standard;

    private UUID cartId;

    private CatalogTestData.TestProduct shirt;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, clock);
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        catalog = new CatalogTestData(mockMvc, clock);
        orders = new OrderTestData(mockMvc);
        standard = catalog.taxCategory("標準", "10");
        shirt = catalog.product(standard, "T シャツ", 2700, "色", "サイズ").variant("白", "M").publish();
        inventory.stock(shirt.variantIds.getFirst(), shirt.id, 3);
        cartId = orders.cart();
        orders.add(cartId, shirt.variantIds.getFirst(), 2);
    }

    @Nested
    @DisplayName("注文の成立")
    class Place {

        @Test
        @DisplayName("AC-87、107、144 成立すると在庫が減り、引当が消え、支払待ちになり、調整履歴に注文が残る。カートは変わらない")
        void placesOrder() throws Exception {
            var checkoutId = orders.startCheckout(cartId);

            var body = place(checkoutId, 6940)
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn().getResponse().getContentAsString();

            var variant = shirt.variantIds.getFirst();
            assertThat(stockReservationApi.sellableQuantities(List.of(variant), null)).containsEntry(variant, 1);
            // 注文した品目は、与信の成功でカートから除く
            mockMvc.perform(get("/api/store/carts/" + cartId))
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].variantId").value(variant.toString()))
                    .andExpect(jsonPath("$.items[0].quantity").value(2));
            var orderId = TestJson.createdId(body);
            mockMvc.perform(get("/api/store/orders/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
            // 調整履歴に、理由「注文」と注文番号が残る
            var orderNumber = JsonPath.<String>read(mockMvc.perform(get("/api/store/orders/" + orderId))
                    .andReturn().getResponse().getContentAsString(), "$.orderNumber");
            mockMvc.perform(get("/api/admin/inventory/variants/" + variant + "/adjustments"))
                    .andExpect(jsonPath("$.items[0].reason").value("ORDER"))
                    .andExpect(jsonPath("$.items[0].delta").value(-2))
                    .andExpect(jsonPath("$.items[0].orderNumber").value(orderNumber))
                    .andExpect(jsonPath("$.items[0].orderId").value(orderId.toString()));
            assertThat(events.stream(OrderStatusChanged.class)).extracting(OrderStatusChanged::orderId).containsExactly(orderId);
        }

        @Test
        @DisplayName("AC-90 成立すると、注文日（日本時間）と 6 桁の数字の注文番号を採番する")
        void numbersOrder() throws Exception {
            var checkoutId = orders.startCheckout(cartId);

            var body = place(checkoutId, 6940).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

            mockMvc.perform(get("/api/store/orders/" + TestJson.createdId(body)))
                    .andExpect(jsonPath("$.orderNumber", matchesPattern("20261008-\\d{6}")));
        }

        @Test
        @DisplayName("AC-87 引当の期限が切れていても、足りれば成立する")
        void placesAfterExpiryWhenSufficient() throws Exception {
            var checkoutId = orders.startCheckout(cartId);
            clock.setInstant(clock.instant().plus(Duration.ofMinutes(20)));

            place(checkoutId, 6940).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-87 期限切れの間にほかの消費者に引き当てられて足りなければ 409 で、在庫も注文もカートも変わらない")
        void rejectsWhenOthersReservedAfterExpiry() throws Exception {
            var checkoutId = orders.startCheckout(cartId);
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
            var checkoutId = orders.startCheckout(cartId);
            shirt.changePrice(standard, 3000);

            place(checkoutId, 6940)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("TOTAL_AMOUNT_CHANGED"));
            place(checkoutId, 7600).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("注文手続きの後にカートが変われば 409")
        void rejectsChangedCart() throws Exception {
            var checkoutId = orders.startCheckout(cartId);
            // 減らす変更は販売可能数を確認しないので、注文手続き中でもできる
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
            var checkoutId = orders.startCheckout(cartId);

            placeWith(checkoutId, body("1000", "13", "13101"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AC-251 マスターデータにない都道府県は 400（INVALID_PREFECTURE）")
        void rejectsUnknownPrefecture() throws Exception {
            var checkoutId = orders.startCheckout(cartId);

            placeWith(checkoutId, body("100-0001", "48", "13101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PREFECTURE"));
        }

        @Test
        @DisplayName("AC-251 マスターデータにない市区町村と、選んだ都道府県に属さない市区町村は 400（INVALID_MUNICIPALITY）")
        void rejectsUnknownOrMismatchedMunicipality() throws Exception {
            var checkoutId = orders.startCheckout(cartId);

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
            var checkoutId = orders.startCheckout(cartId);

            placeWith(checkoutId, body("100-0001", "東京都", "13101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("shippingAddress.prefectureCode"));
            placeWith(checkoutId, body("100-0001", "13", "131"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("shippingAddress.municipalityCode"));
        }

        @Test
        @DisplayName("AC-265 郵便番号と電話番号の形の誤りは、入力の検証で見つかっても業務のエラーと同じ文を欄のエラーに返す")
        void returnsBusinessMessagesForMalformedPostalCodeAndPhone() throws Exception {
            var checkoutId = orders.startCheckout(cartId);

            placeWith(checkoutId, body("1000", "13", "13101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("shippingAddress.postalCode"))
                    .andExpect(jsonPath("$.errors[0].message").value("郵便番号は数字 7 桁で入力してください。"));
            placeWith(checkoutId, body("100-0001", "13", "13101", "03-12"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("shippingAddress.phone"))
                    .andExpect(jsonPath("$.errors[0].message").value("電話番号は数字 10〜11 桁で入力してください。"));
        }

        /**
         * 郵便番号と都道府県・市区町村のコードを指定した、注文のリクエストの本文
         */
        private static String body(String postalCode, String prefectureCode, String municipalityCode) {
            return body(postalCode, prefectureCode, municipalityCode, "03-1234-5678");
        }

        /**
         * 郵便番号と都道府県・市区町村のコードと電話番号を指定した、注文のリクエストの本文
         */
        private static String body(String postalCode, String prefectureCode, String municipalityCode, String phone) {
            return """
                    {"expectedTotal": 6940, "shippingAddress": {"name": "山田 太郎", "postalCode": "%s",
                     "prefectureCode": "%s", "municipalityCode": "%s", "address": "千代田 1-1", "phone": "%s"}}"""
                    .formatted(postalCode, prefectureCode, municipalityCode, phone);
        }

        @Test
        @DisplayName("AC-91 同じ注文手続きでの再送には、作成済みの注文 ID を 200 で返し、注文は 1 件だけ")
        void returnsExistingOrderOnResend() throws Exception {
            var checkoutId = orders.startCheckout(cartId);
            var first = TestJson.createdId(place(checkoutId, 6940).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString()).toString();

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

        @Test
        @DisplayName("AC-75 カートの 1 品目を上限の数量にして注文を確定でき、注文の明細の数量はその値になる（カートの上限と注文の明細の数量の制約は同じ値）")
        void placesOrderWithMaxQuantity() throws Exception {
            var variant = shirt.variantIds.getFirst();
            var max = maxCartQuantity();
            inventory.stock(variant, shirt.id, max + 5);
            mockMvc.perform(put("/api/store/carts/" + cartId + "/items/" + variant)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantity": %d}""".formatted(max)))
                    .andExpect(status().isNoContent());
            var checkoutId = orders.startCheckout(cartId);
            var shown = mockMvc.perform(get("/api/store/checkouts/" + checkoutId))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            long total = ((Number) JsonPath.read(shown, "$.amounts.total")).longValue();

            var body = place(checkoutId, total).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            mockMvc.perform(get("/api/store/orders/" + TestJson.createdId(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lines[0].quantity").value(max));
        }

        /**
         * カートの 1 品目の数量の上限（{@code Cart.MAX_QUANTITY}）。
         *
         * <p>値を直書きせず、カートの定数から取る。
         * 定数はカートのモジュールの中だけに公開しているので、リフレクションで読む（FieldConstraintContractTest と同じ）
         */
        private static int maxCartQuantity() throws Exception {
            var field = Class.forName("com.ecsite.module.cart.domain.Cart").getDeclaredField("MAX_QUANTITY");
            field.setAccessible(true);
            return field.getInt(null);
        }
    }

    @Nested
    @DisplayName("支払待ちの注文の確認")
    class PendingOrderConfirmation {

        /**
         * 複数の注文ができるように、7 を足して在庫数を 10 にする
         */
        @BeforeEach
        void stockEnough() {
            inventory.stock(shirt.variantIds.getFirst(), shirt.id, 7);
        }

        @Test
        @DisplayName("AC-406-9 確認して始めた注文手続きからは、支払待ちの注文があっても 2 件目が成立し、在庫も減る")
        void placesSecondOrderWhenAcknowledged() throws Exception {
            var first = placed(orders.startCheckout(cartId));

            var second = placed(startAcknowledging(first));

            assertThat(second).isNotEqualTo(first);
            mockMvc.perform(get("/api/store/orders/" + second)).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
            mockMvc.perform(get("/api/store/orders/" + first)).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
            expectQuantity(6);
        }

        @Test
        @DisplayName("AC-406-9 支払待ちの注文が複数あっても、全てを確認して始めた注文手続きからは注文できる")
        void placesWhenAllPendingOrdersAcknowledged() throws Exception {
            var first = placed(orders.startCheckout(cartId));
            var second = placed(startAcknowledging(first));

            placed(startAcknowledging(first, second));

            expectQuantity(4);
        }

        @Test
        @DisplayName("AC-406-11 2 つのタブで始めて順に注文すると、確認していない 2 つ目は確認を求める 409 で、注文を作らず、在庫を減らさない")
        void rejectsUnacknowledgedSecondTab() throws Exception {
            var firstTab = orders.startCheckout(cartId);
            var secondTab = orders.startCheckout(cartId);
            placed(firstTab);

            expectConfirmationRequired(place(secondTab, 6940));

            expectNotPlaced(secondTab);
            expectQuantity(8);
        }

        @Test
        @DisplayName("AC-406-11 カートが変わった注文手続きでも、カートの変更の判定より先に確認を求める")
        void requiresConfirmationBeforeCartChangeCheck() throws Exception {
            var firstTab = orders.startCheckout(cartId);
            var secondTab = orders.startCheckout(cartId);
            placed(firstTab);
            // 減らす変更は販売可能数を確認しないので、注文手続き中でもできる
            mockMvc.perform(put("/api/store/carts/" + cartId + "/items/" + shirt.variantIds.getFirst())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantity": 1}"""))
                    .andExpect(status().isNoContent());

            expectConfirmationRequired(place(secondTab, 6940));
        }

        @Test
        @DisplayName("AC-406-12 確認した後に別の注文が支払待ちになると、確認した注文手続きからも注文できず、注文を作らず、在庫を減らさない")
        void rejectsWhenPendingOrderAddedAfterAcknowledgement() throws Exception {
            var first = placed(orders.startCheckout(cartId));
            var earlier = startAcknowledging(first);
            var later = startAcknowledging(first);
            placed(later);

            expectConfirmationRequired(place(earlier, 6940));

            expectNotPlaced(earlier);
            expectQuantity(6);
        }

        @Test
        @DisplayName("AC-406-15 確認していない注文手続きの再送は、その注文が支払待ちでも、作成済みの注文を 200 で返す")
        void returnsExistingPendingOrderOnResend() throws Exception {
            var checkoutId = orders.startCheckout(cartId);
            var first = placed(checkoutId);

            expectExisting(checkoutId, first);

            expectQuantity(8);
        }

        @Test
        @DisplayName("AC-406-15 注文が受付になった後の再送も、作成済みの注文を 200 で返す")
        void returnsExistingAcceptedOrderOnResend() throws Exception {
            var checkoutId = orders.startCheckout(cartId);
            var first = placed(checkoutId);
            OrderPayments.accept(applicationContext, first);

            expectExisting(checkoutId, first);

            expectQuantity(8);
        }

        @Test
        @DisplayName("AC-406-15 確認していない別の支払待ちの注文が増えた後の再送も、作成済みの注文を 200 で返す")
        void returnsExistingOrderAfterAnotherPendingOrder() throws Exception {
            var firstCheckout = orders.startCheckout(cartId);
            var first = placed(firstCheckout);
            var secondCheckout = startAcknowledging(first);
            var second = placed(secondCheckout);
            placed(startAcknowledging(first, second));

            expectExisting(firstCheckout, first);
            expectExisting(secondCheckout, second);

            expectQuantity(4);
        }

        @Test
        @DisplayName("AC-406-16 支払の期限切れでキャンセルされた後は、確認なしで始めた注文手続きから注文できる")
        void placesAfterExpiry() throws Exception {
            OrderPayments.expire(applicationContext, placed(orders.startCheckout(cartId)));

            placed(orders.startCheckout(cartId));
        }

        @Test
        @DisplayName("AC-406-16 支払の期限切れでキャンセルされた後は、先に始めていた確認していない注文手続きからも注文できる")
        void placesFromEarlierCheckoutAfterExpiry() throws Exception {
            var firstTab = orders.startCheckout(cartId);
            var secondTab = orders.startCheckout(cartId);
            OrderPayments.expire(applicationContext, placed(firstTab));

            placed(secondTab);
        }

        @Test
        @DisplayName("AC-406-16 与信の成功で受付になった後は、確認なしで始めた注文手続きから注文できる")
        void placesAfterAcceptance() throws Exception {
            OrderPayments.accept(applicationContext, placed(orders.startCheckout(cartId)));
            // 受付になると、注文した品目はカートから除かれる
            orders.add(cartId, shirt.variantIds.getFirst(), 2);

            placed(orders.startCheckout(cartId));
        }

        @Test
        @DisplayName("AC-406-17 他の会員の注文手続きからの注文は、持ち主にも送った会員にも支払待ちの注文があっても 404 で、注文を作らない")
        void hidesCheckoutFromOtherMembers() throws Exception {
            var firstTab = orders.startCheckout(cartId);
            var secondTab = orders.startCheckout(cartId);
            placed(firstTab);
            var other = otherMember();
            new OrderTestData(mockMvc, null, null, other.bearer()).placePendingOrder(shirt.variantIds.getFirst(), 1);

            mockMvc.perform(placeOrderRequest(secondTab, 6940).with(other.bearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));

            expectNotPlaced(secondTab);
        }

        @Test
        @DisplayName("AC-406-17 注文の済んだ注文手続きに対して、他の会員が注文を送っても 404 で、注文を示さない")
        void hidesPlacedOrderFromOtherMembers() throws Exception {
            var checkoutId = orders.startCheckout(cartId);
            placed(checkoutId);

            mockMvc.perform(placeOrderRequest(checkoutId, 6940).with(otherMember().bearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.id").doesNotExist());
        }

        @Test
        @DisplayName("BR-406-1 支払の期限を過ぎ、まだキャンセルされていない注文も、支払待ちとして確認を求める")
        void treatsOverdueOrderAsPending() throws Exception {
            var firstTab = orders.startCheckout(cartId);
            var secondTab = orders.startCheckout(cartId);
            var first = placed(firstTab);
            var order = mockMvc.perform(get("/api/store/orders/" + first)).andReturn().getResponse().getContentAsString();
            clock.setInstant(Instant.parse(JsonPath.read(order, "$.paymentDeadline")).plusSeconds(1));

            expectConfirmationRequired(place(secondTab, 6940));
        }

        /**
         * 確認した注文の一覧を送って注文手続きに進み、201 を確認して、注文手続き ID を返す
         */
        private UUID startAcknowledging(UUID... acknowledged) throws Exception {
            return orders.startCheckout(cartId, List.of(acknowledged));
        }

        /**
         * 既定の会員と別の会員の token を返す
         */
        private TestTokens.Builder otherMember() {
            return TestTokens.member().subject(UUID.randomUUID()).email("other@example.com");
        }
    }

    @Nested
    @DisplayName("支払待ちの注文の上限")
    class PendingOrderLimit {

        /**
         * 上限を一時的に上げる値
         *
         * <p>上限を超えた状態を作るために、既定の上限より十分大きくする。
         */
        private static final int RAISED_LIMIT = 10;

        /**
         * 注文の設定
         *
         * <p>上限の既定は 3 件。注文の時点で上限に達した状態を作るテストだけ、上限を差し替える。
         */
        @MockitoSpyBean
        OrderProperties properties;

        /**
         * 支払待ちの注文は、注文ごとに別の商品で作るので、カートを空にしておく（T シャツは、確認した注文を送って注文手続きを始める前に、もう一度入れる）
         */
        @BeforeEach
        void emptyCart() throws Exception {
            mockMvc.perform(delete("/api/store/carts/" + cartId + "/items/" + shirt.variantIds.getFirst()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("AC-411-1、BR-411-2 支払待ちの注文が上限（3 件）に達していると、全て確認した注文手続きからの注文も 409 で拒否し、注文を作らず、在庫を減らさない")
        void rejectsAtLimit() throws Exception {
            var checkoutId = startAcknowledgingAtLimit();

            expectLimitExceeded(place(checkoutId, 6940));

            expectNotPlaced(checkoutId);
            expectQuantity(3);
            assertThat(pendingOrderCount(checkoutId)).isEqualTo(3);
        }

        @Test
        @DisplayName("AC-411-8 上限を超える件数（4 件）の支払待ちの注文を持つ会員の注文も、全て確認した注文手続きからでも 409 で拒否し、注文を作らず、在庫を減らさない")
        void rejectsAboveLimit() throws Exception {
            // 上限を下げる前の状態を作るため、4 件を作る間だけ上限を上げる（上限が 3 件のままでは 4 件目を注文できない）
            raiseLimit();
            var checkoutId = startWithShirts(orders.placePendingOrders(cartId, stockedVariants(4)));
            restoreLimit();

            expectLimitExceeded(place(checkoutId, 6940));

            expectNotPlaced(checkoutId);
            expectQuantity(3);
            assertThat(pendingOrderCount(checkoutId)).isEqualTo(4);
        }

        @Test
        @DisplayName("BR-411-1 支払の期限を過ぎても、期限切れのキャンセルが済むまでは、上限の件数に数えて注文を 409 で拒否する")
        void countsOverdueOrderUntilCancelled() throws Exception {
            var pendingIds = orders.placePendingOrders(cartId, stockedVariants(3));
            raiseLimit();
            var checkoutId = startWithShirts(pendingIds);
            restoreLimit();
            var order = mockMvc.perform(get("/api/store/orders/" + pendingIds.getLast())).andReturn().getResponse().getContentAsString();
            clock.setInstant(Instant.parse(JsonPath.read(order, "$.paymentDeadline")).plusSeconds(1));

            expectLimitExceeded(place(checkoutId, 6940));

            expectNotPlaced(checkoutId);
        }

        @Test
        @DisplayName("AC-411-2 支払待ちの注文が上限より 1 件少ない（2 件）ときは、全て確認した注文手続きから注文でき、支払待ちが 3 件になる")
        void placesBelowLimit() throws Exception {
            var checkoutId = startWithShirts(orders.placePendingOrders(cartId, stockedVariants(2)));

            var orderId = placed(checkoutId);

            mockMvc.perform(get("/api/store/orders/" + orderId)).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
            expectQuantity(1);
            assertThat(pendingOrderCount(checkoutId)).isEqualTo(3);
        }

        @Test
        @DisplayName("AC-411-4 上限に達していても、確認していない支払待ちの注文があれば、注文の時点でも上限でなく確認を求める 409 を返し、注文を作らない")
        void requiresConfirmationBeforeLimit() throws Exception {
            var pendingIds = orders.placePendingOrders(cartId, stockedVariants(2));
            var earlier = startWithShirts(pendingIds);
            var later = orders.startCheckout(cartId, pendingIds);
            // 後に始めた注文手続きから 3 件目が成立し、先に始めた注文手続きはその注文を確認していない
            placed(later);

            expectConfirmationRequired(place(earlier, 6940));

            expectNotPlaced(earlier);
            expectQuantity(1);
        }

        @Test
        @DisplayName("AC-411-6 開始の後に上限が下がった注文手続きでも、支払の期限切れでキャンセルされて上限より少なくなると、その注文手続きから再び注文できる")
        void placesAfterExpiry() throws Exception {
            var checkoutId = startAcknowledgingAtLimit();
            expectLimitExceeded(place(checkoutId, 6940));

            OrderPayments.expire(applicationContext, anyAcknowledged(checkoutId));

            placed(checkoutId);
            expectQuantity(1);
        }

        @Test
        @DisplayName("AC-411-6 開始の後に上限が下がった注文手続きでも、与信の成功で受付になって上限より少なくなると、その注文手続きから再び注文できる")
        void placesAfterAcceptance() throws Exception {
            var checkoutId = startAcknowledgingAtLimit();
            expectLimitExceeded(place(checkoutId, 6940));

            // 受付にした注文の品目は、もうカートにないので、注文手続きのカートは変わらない
            OrderPayments.accept(applicationContext, anyAcknowledged(checkoutId));

            placed(checkoutId);
            expectQuantity(1);
        }

        @Test
        @DisplayName("AC-411-6 上限で開始を拒否された後、支払の期限切れでキャンセルされて上限より少なくなると、残りの支払待ちを確認して再び開始した注文手続きから 201 で注文でき、在庫が減る")
        void startsAgainAndPlacesAfterExpiry() throws Exception {
            var pendingIds = orders.placePendingOrders(cartId, stockedVariants(3));
            orders.add(cartId, shirt.variantIds.getFirst(), 2);
            expectLimitExceeded(mockMvc.perform(startCheckoutRequest(cartId, pendingIds)));

            OrderPayments.expire(applicationContext, pendingIds.getFirst());

            var checkoutId = orders.startCheckout(cartId, pendingIds.subList(1, 3));
            placed(checkoutId);
            expectQuantity(1);
        }

        @Test
        @DisplayName("AC-411-6 上限で開始を拒否された後、与信の成功で受付になって上限より少なくなると、残りの支払待ちを確認して再び開始した注文手続きから 201 で注文でき、在庫が減る")
        void startsAgainAndPlacesAfterAcceptance() throws Exception {
            var pendingIds = orders.placePendingOrders(cartId, stockedVariants(3));
            orders.add(cartId, shirt.variantIds.getFirst(), 2);
            expectLimitExceeded(mockMvc.perform(startCheckoutRequest(cartId, pendingIds)));

            OrderPayments.accept(applicationContext, pendingIds.getFirst());

            var checkoutId = orders.startCheckout(cartId, pendingIds.subList(1, 3));
            placed(checkoutId);
            expectQuantity(1);
        }

        @Test
        @DisplayName("AC-411-7 上限に達した後の同じ注文手続きの再送は、作成済みの注文 ID を 200 で返し、注文は 1 件だけになる")
        void returnsExistingOrderOnResendAtLimit() throws Exception {
            var checkoutId = startWithShirts(orders.placePendingOrders(cartId, stockedVariants(2)));
            var orderId = placed(checkoutId);
            assertThat(pendingOrderCount(checkoutId)).isEqualTo(3);

            expectExisting(checkoutId, orderId);

            assertThat(dsl.fetchCount(SALES_ORDER, SALES_ORDER.CHECKOUT_ID.eq(checkoutId))).isEqualTo(1);
            expectQuantity(1);
        }

        /**
         * 支払待ちの注文を上限（3 件）まで作り、それらを全て確認して T シャツ 2 枚の注文手続きを始め、注文手続き ID を返す。
         *
         * <p>開始の時点も上限で拒否するので、開始の間だけ上限を上げる。設定の上限が、開始と注文の間に下がった場合を想定する（最終的な判定は注文の時点）
         */
        private UUID startAcknowledgingAtLimit() throws Exception {
            var pendingIds = orders.placePendingOrders(cartId, stockedVariants(3));
            raiseLimit();
            var checkoutId = startWithShirts(pendingIds);
            restoreLimit();
            return checkoutId;
        }

        /**
         * 上限を一時的に上げる（開始の時点の判定を通すため）
         */
        private void raiseLimit() {
            doReturn(RAISED_LIMIT).when(properties).pendingOrderLimit();
        }

        /**
         * 上げた上限を、差し替える前の値に戻す
         */
        private void restoreLimit() {
            reset(properties);
        }

        /**
         * カートに T シャツ 2 枚を入れ、確認した注文の一覧を送って注文手続きを始め、注文手続き ID を返す
         */
        private UUID startWithShirts(List<UUID> acknowledged) throws Exception {
            orders.add(cartId, shirt.variantIds.getFirst(), 2);
            return orders.startCheckout(cartId, acknowledged);
        }

        /**
         * 注文手続きに保存した、確認した注文の ID の 1 つを返す
         */
        private UUID anyAcknowledged(UUID checkoutId) {
            return checkoutRepository.findById(checkoutId).orElseThrow().acknowledgedOrderIds().iterator().next();
        }

        /**
         * 注文手続きの会員が持つ、支払待ちの注文の件数
         */
        private int pendingOrderCount(UUID checkoutId) {
            return OrderTestData.pendingOrderCount(dsl, checkoutRepository.findById(checkoutId).orElseThrow().memberId());
        }

        /**
         * 公開した商品（1,000 円・10%）のバリアントを、指定した数だけ作る（在庫数は各 3）。
         *
         * <p>バリアント ID を返す
         */
        private List<UUID> stockedVariants(int count) throws Exception {
            var variants = new ArrayList<UUID>();
            for (int i = 0; i < count; i++) {
                var product = catalog.product(standard, "商品", 1000).publish();
                var variantId = product.variantIds.getFirst();
                inventory.stock(variantId, product.id, 3);
                variants.add(variantId);
            }
            return variants;
        }
    }

    @Nested
    @DisplayName("注文の表示")
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

    @Nested
    @DisplayName("注文時の年齢と性別")
    class BuyerProfile {

        @Test
        @DisplayName("AC-212、222 成立のときの token の生年月日と性別から、注文日（日本時間）の年齢と性別を記録する。後で token の値が変わっても、成立済みの注文は変わらない")
        void recordsAgeAndGenderAtPlacement() throws Exception {
            // NOW は日本時間の 2026-10-08 00:00。1990-10-08 生まれは、この日に 36 歳になる
            var first = placeAs(TestTokens.member().birthdate("1990-10-08").gender("female"));
            inventory.stock(shirt.variantIds.getFirst(), shirt.id, 2);

            var changed = TestTokens.member().birthdate("2000-01-01").gender("male");
            mockMvc.perform(get("/api/store/orders/" + first).with(changed.bearer())).andExpect(status().isOk());
            var second = placeAs(changed, first);

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
        @DisplayName("AC-213 未来の生年月日は、不明として記録する")
        void recordsUnknownForFutureBirthdate() throws Exception {
            var orderId = placeAs(TestTokens.member().birthdate("2030-01-01").gender("female"));

            assertThat(buyer(orderId)).containsExactly(null, "FEMALE");
        }

        @Test
        @DisplayName("BR-29 一覧にない性別は、不明として記録する")
        void recordsUnknownForUnlistedGender() throws Exception {
            var orderId = placeAs(TestTokens.member().birthdate("1990-10-08").gender("unknown"));

            assertThat(buyer(orderId)).containsExactly((short) 36, null);
        }

        /**
         * token を指定して、確認した注文の一覧を送り、T シャツ 2 枚を注文し、注文 ID を返す
         */
        private UUID placeAs(TestTokens.Builder token, UUID... acknowledged) throws Exception {
            var checkoutId = orders.startCheckout(cartId, token.bearer(), List.of(acknowledged));
            var body = mockMvc.perform(placeOrderRequest(checkoutId, 6940).with(token.bearer()))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            return TestJson.createdId(body);
        }

        /**
         * 注文に記録した年齢と性別
         */
        private List<Object> buyer(UUID orderId) {
            var record = dsl.select(SALES_ORDER.BUYER_AGE, SALES_ORDER.BUYER_GENDER).from(SALES_ORDER)
                    .where(SALES_ORDER.ID.eq(orderId)).fetchSingle();
            return Arrays.asList(record.get(SALES_ORDER.BUYER_AGE), record.get(SALES_ORDER.BUYER_GENDER));
        }
    }

    // ============================== 補助 ==============================

    /**
     * T シャツ 2 枚を注文し、注文 ID を返す
     */
    private String placeOrder() throws Exception {
        var body = place(orders.startCheckout(cartId), 6940).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return TestJson.createdId(body).toString();
    }

    /**
     * T シャツ 2 枚の注文手続きから注文し、201 を確認して、注文 ID を返す
     */
    private UUID placed(UUID checkoutId) throws Exception {
        return TestJson.createdId(place(checkoutId, 6940).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    /**
     * 再送が、作成済みの注文 ID を 200 で返すことを確認する
     */
    private void expectExisting(UUID checkoutId, UUID orderId) throws Exception {
        place(checkoutId, 6940)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }

    /**
     * 確認を求める 409 であることを確認する
     */
    private static void expectConfirmationRequired(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PENDING_ORDER_CONFIRMATION_REQUIRED"));
    }

    /**
     * 支払待ちの注文が上限に達していることを示す 409 であることを確認する
     */
    private static void expectLimitExceeded(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PENDING_ORDER_LIMIT_EXCEEDED"));
    }

    /**
     * 注文手続きに注文がないことを確認する
     */
    private void expectNotPlaced(UUID checkoutId) {
        assertThat(dsl.fetchCount(SALES_ORDER, SALES_ORDER.CHECKOUT_ID.eq(checkoutId))).isZero();
    }

    /**
     * T シャツの在庫数を確認する
     */
    private void expectQuantity(int quantity) throws Exception {
        mockMvc.perform(get("/api/admin/inventory/variants/" + shirt.variantIds.getFirst()))
                .andExpect(jsonPath("$.quantity").value(quantity));
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
}
