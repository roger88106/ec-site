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
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.payment.OrderPayments;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.ecsite.OrderTestData.orderBody;
import static com.ecsite.OrderTestData.startCheckoutRequest;
import static com.ecsite.jooq.member.Tables.MEMBER_;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会員の注文の受け入れテスト（docs/03-機能仕様/order.md）。<br />
 * 会員の token はテストの鍵で作る（TestTokens）。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class MemberOrderApiTest {

    private static final String STORE = "/api/store";

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    ApplicationContext applicationContext;

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
    DSLContext dsl;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    private UUID variantId;

    /**
     * 会員（持ち主）
     */
    private final UUID ownerSubject = UUID.randomUUID();

    private final TestTokens.Builder owner = TestTokens.member().subject(ownerSubject).email("owner@example.com");

    /**
     * ほかの会員
     */
    private final TestTokens.Builder other = TestTokens.member().subject(UUID.randomUUID()).email("other@example.com");

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
        ShippingTaxCategory.ensure(dsl);
        var data = new CatalogTestData(mockMvc, clock);
        orders = new OrderTestData(mockMvc);
        var standard = data.taxCategory("標準", "10");
        variantId = data.product(standard, "T シャツ", 2700).publish().variantIds.getFirst();
        inventory.stock(variantId, UUID.randomUUID(), 50);
    }

    @Nested
    @DisplayName("AC-125、126 注文手続き")
    class Checkout {

        @Test
        @DisplayName("AC-125 会員は、自分の会員のカートから注文手続きを始められる")
        void startsFromOwnMemberCart() throws Exception {
            var cartId = memberCartWithItem(owner);

            startCheckout(cartId, owner.bearer()).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-125 token がなければ 401")
        void requiresToken() throws Exception {
            var cartId = memberCartWithItem(owner);

            startCheckout(cartId, TestTokens.none())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        }

        @Test
        @DisplayName("AC-125 ログインせずに作ったカートからは始められない（404）")
        void rejectsGuestCart() throws Exception {
            var cartId = TestJson.createdId(mockMvc.perform(post(STORE + "/carts").with(TestTokens.none()))
                    .andReturn().getResponse().getContentAsString());
            orders.add(cartId, variantId, 1, TestTokens.none());

            startCheckout(cartId, owner.bearer()).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-126 ほかの会員のカートからは始められない（404）")
        void rejectsOthersCart() throws Exception {
            var cartId = memberCartWithItem(owner);

            startCheckout(cartId, other.bearer()).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-126 注文手続きは、始めた会員だけが参照できる。ほかの会員には 404、token がなければ 401")
        void onlyOwnerCanViewCheckout() throws Exception {
            var checkoutId = checkout(owner);

            mockMvc.perform(get(STORE + "/checkouts/" + checkoutId).with(owner.bearer()))
                    .andExpect(status().isOk());
            mockMvc.perform(get(STORE + "/checkouts/" + checkoutId).with(other.bearer()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(STORE + "/checkouts/" + checkoutId).with(TestTokens.none()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("AC-127、128 注文")
    class PlaceOrder {

        @Test
        @DisplayName("AC-127、128 メールアドレスは token から取って注文の写しに残し、注文は会員に結び付く")
        void copiesEmailFromToken() throws Exception {
            var orderId = placeOrder(owner);

            mockMvc.perform(get(STORE + "/orders/" + orderId).with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("owner@example.com"));
            var ownerMemberId = dsl.select(MEMBER_.ID).from(MEMBER_)
                    .where(MEMBER_.IDP_SUBJECT.eq(ownerSubject.toString())).fetchOne(MEMBER_.ID);
            assertThat(ownerMemberId).isNotNull();
            assertThat(dsl.fetchValue(SALES_ORDER.MEMBER_ID, SALES_ORDER.ID.eq(orderId))).isEqualTo(ownerMemberId);
        }

        @Test
        @DisplayName("AC-126 ほかの会員は、注文手続きで注文できない（404）")
        void othersCannotPlaceOrder() throws Exception {
            var checkoutId = checkout(owner);

            order(checkoutId, owner, other.bearer()).andExpect(status().isNotFound());
            assertThat(dsl.fetchCount(SALES_ORDER, SALES_ORDER.CHECKOUT_ID.eq(checkoutId))).isZero();
        }

        @Test
        @DisplayName("AC-127 token のメールアドレスが確認済みでなければ、注文しない")
        void rejectsUnverifiedEmail() throws Exception {
            var unverified = TestTokens.member().subject(UUID.randomUUID()).emailVerified(false);
            var checkoutId = checkout(unverified);

            order(checkoutId, unverified, unverified.bearer())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));
            assertThat(dsl.fetchCount(SALES_ORDER, SALES_ORDER.CHECKOUT_ID.eq(checkoutId))).isZero();
        }
    }

    @Nested
    @DisplayName("AC-130、131 注文の参照とキャンセルの申請")
    class OrderAccess {

        @Test
        @DisplayName("AC-131 注文は、注文した会員だけが参照できる。ほかの会員には 404、token がなければ 401")
        void onlyOwnerCanViewOrder() throws Exception {
            var orderId = placeOrder(owner);

            mockMvc.perform(get(STORE + "/orders/" + orderId).with(other.bearer()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(STORE + "/orders/" + orderId).with(TestTokens.none()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-130、131 キャンセルの申請は、注文した会員だけができる。ほかの会員には 404、token がなければ 401")
        void onlyOwnerCanRequestCancellation() throws Exception {
            var orderId = placeOrder(owner);
            // 申請できるのは受付の注文（与信の成功の後。ロールバックするテストなので、リスナーと同じ処理を呼ぶ）
            OrderPayments.accept(applicationContext, orderId);

            requestCancellation(orderId, other.bearer()).andExpect(status().isNotFound());
            requestCancellation(orderId, TestTokens.none()).andExpect(status().isUnauthorized());
            requestCancellation(orderId, owner.bearer()).andExpect(status().isNoContent());

            mockMvc.perform(get(STORE + "/orders/" + orderId).with(owner.bearer()))
                    .andExpect(jsonPath("$.status").value("CANCELLATION_REQUESTED"));
        }
    }

    @Nested
    @DisplayName("AC-129 自分の注文の一覧")
    class MyOrders {

        @Test
        @DisplayName("AC-129 自分の注文だけを、注文日時の新しい順に、ページ単位で返す")
        void listsOwnOrdersNewestFirst() throws Exception {
            var first = placeOrder(owner);
            clock.setInstant(NOW.plus(Duration.ofHours(1)));
            var second = placeOrder(owner);
            clock.setInstant(NOW.plus(Duration.ofHours(2)));
            var third = placeOrder(owner);
            placeOrder(other);

            mockMvc.perform(get(STORE + "/me/orders").param("size", "2").with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id").value(contains(third.toString(), second.toString())))
                    .andExpect(jsonPath("$.items[0].orderNumber").isNotEmpty())
                    .andExpect(jsonPath("$.items[0].orderedAt").isNotEmpty())
                    .andExpect(jsonPath("$.items[0].totalAmount").isNumber())
                    .andExpect(jsonPath("$.items[0].status").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalItems").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2));
            mockMvc.perform(get(STORE + "/me/orders").param("size", "2").param("page", "2").with(owner.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id").value(contains(first.toString())));
        }

        @Test
        @DisplayName("AC-129 注文のない会員は空の一覧")
        void emptyForMemberWithoutOrders() throws Exception {
            placeOrder(owner);

            mockMvc.perform(get(STORE + "/me/orders").with(other.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)))
                    .andExpect(jsonPath("$.totalItems").value(0));
        }

        @Test
        @DisplayName("AC-137 token がなければ 401")
        void requiresToken() throws Exception {
            mockMvc.perform(get(STORE + "/me/orders").with(TestTokens.none()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ============================== 補助 ==============================

    /**
     * 会員のカートに品目を入れ、カート ID を返す
     */
    private UUID memberCartWithItem(TestTokens.Builder member) throws Exception {
        var cartId = orders.cart(member.bearer());
        orders.add(cartId, variantId, 1, member.bearer());
        return cartId;
    }

    private ResultActions startCheckout(UUID cartId, RequestPostProcessor requester) throws Exception {
        return mockMvc.perform(startCheckoutRequest(cartId).with(requester));
    }

    /**
     * 会員のカートに入れて、注文手続きを始める
     */
    private UUID checkout(TestTokens.Builder member) throws Exception {
        return orders.startCheckout(memberCartWithItem(member), member.bearer());
    }

    /**
     * 注文の要求を送る。総額は、注文手続きの持ち主として読んだものを送る（ほかの会員の要求が、総額の違いで拒まれないように）
     */
    private ResultActions order(UUID checkoutId, TestTokens.Builder checkoutOwner, RequestPostProcessor requester) throws Exception {
        var checkout = mockMvc.perform(get(STORE + "/checkouts/" + checkoutId).with(checkoutOwner.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long total = JsonPath.<Number>read(checkout, "$.amounts.total").longValue();
        return mockMvc.perform(post(STORE + "/checkouts/" + checkoutId + "/order").with(requester)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(total)));
    }

    /**
     * 会員として注文し、注文 ID を返す
     */
    private UUID placeOrder(TestTokens.Builder member) throws Exception {
        var checkoutId = checkout(member);
        var checkout = mockMvc.perform(get(STORE + "/checkouts/" + checkoutId).with(member.bearer()))
                .andReturn().getResponse().getContentAsString();
        long total = JsonPath.<Number>read(checkout, "$.amounts.total").longValue();
        var body = mockMvc.perform(post(STORE + "/checkouts/" + checkoutId + "/order").with(member.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(total)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return TestJson.createdId(body);
    }

    private ResultActions requestCancellation(UUID orderId, RequestPostProcessor requester) throws Exception {
        return mockMvc.perform(put(STORE + "/orders/" + orderId + "/cancellation-request").with(requester)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"間違えた\"}"));
    }
}
