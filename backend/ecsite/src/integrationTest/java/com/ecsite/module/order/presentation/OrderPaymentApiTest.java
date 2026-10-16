package com.ecsite.module.order.presentation;

import com.ecsite.AsyncEvents;
import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.TestTokens;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.payment.OrderPaymentService;
import com.ecsite.module.payment.application.PaymentExpiryJob;
import com.ecsite.module.payment.domain.payment.VoidRequested;
import com.ecsite.module.payment.infrastructure.mock.MockPaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static com.ecsite.jooq.cart.Tables.CART_;
import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.inventory.Tables.STOCK_ITEM;
import static com.ecsite.jooq.ordering.Tables.CHECKOUT;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.jooq.payment.Tables.PAYMENT_;
import static com.ecsite.jooq.tax.Tables.TAX_CATEGORY;
import static com.ecsite.jooq.tax.Tables.TAX_RATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文と支払の受け入れテスト（T7305、AC-143〜166）。<br />
 * 決済のイベントはコミットの後に非同期で届くので、コミットして処理を待ち、作ったデータを後で消す。
 * 決済サービスは mock。支払の期限は、時計を進めて期限切れのジョブを呼んで確かめる。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class OrderPaymentApiTest {

    /**
     * テストの現在時刻（商品を公開するたびに 1 分進むので、注文日時はこれより後になる）
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MockPaymentGateway gateway;

    @Autowired
    OrderPaymentService orderPaymentService;

    @Autowired
    PaymentExpiryJob expiryJob;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    DSLContext dsl;

    @Autowired
    AsyncEvents asyncEvents;

    private OrderTestData orders;

    private UUID taxCategoryId;

    /**
     * このテストで作った商品
     */
    private final List<CatalogTestData.TestProduct> products = new ArrayList<>();

    /**
     * このテストで作った注文
     */
    private final List<UUID> orderIds = new ArrayList<>();

    private UUID variantId;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        gateway.reset();
        ShippingTaxCategory.ensure(dsl);
        taxCategoryId = new CatalogTestData(mockMvc, clock).taxCategory("支払-" + UUID.randomUUID().toString().substring(0, 8), "10");
        variantId = stockedVariant("支払の商品");
        orders = new OrderTestData(mockMvc, gateway, orderPaymentService);
    }

    @AfterEach
    void cleanUp() {
        var checkoutIds = dsl.select(SALES_ORDER.CHECKOUT_ID).from(SALES_ORDER).where(SALES_ORDER.ID.in(orderIds))
                .fetch(SALES_ORDER.CHECKOUT_ID);
        var cartIds = dsl.select(CHECKOUT.CART_ID).from(CHECKOUT).where(CHECKOUT.ID.in(checkoutIds)).fetch(CHECKOUT.CART_ID);
        dsl.deleteFrom(PAYMENT_).where(PAYMENT_.ORDER_ID.in(orderIds)).execute();
        dsl.deleteFrom(SALES_ORDER).where(SALES_ORDER.ID.in(orderIds)).execute();
        dsl.deleteFrom(CHECKOUT).where(CHECKOUT.ID.in(checkoutIds)).execute();
        // 会員のカートは、支払待ちの注文の品目が残るので消す（次に使うときに作り直される）
        dsl.deleteFrom(CART_).where(CART_.ID.in(cartIds)).execute();
        products.forEach(product -> {
            dsl.deleteFrom(STOCK_ITEM).where(STOCK_ITEM.PRODUCT_ID.eq(product.id)).execute();
            dsl.deleteFrom(PRODUCT).where(PRODUCT.ID.eq(product.id)).execute();
        });
        dsl.deleteFrom(TAX_RATE).where(TAX_RATE.TAX_CATEGORY_ID.eq(taxCategoryId)).execute();
        dsl.deleteFrom(TAX_CATEGORY).where(TAX_CATEGORY.ID.eq(taxCategoryId)).execute();
    }

    @Nested
    @DisplayName("US-31 注文の代金を支払う")
    class Pay {

        @Test
        @DisplayName("AC-143、144 注文すると支払待ちで作られ、在庫数が減り、支払（未払い）が作られ、カートは変わらない")
        void placesPendingOrder() throws Exception {
            var orderId = pending(2);

            store(orderId)
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.paymentDeadline").value(deadlineOf(orderId).toString()))
                    .andExpect(jsonPath("$.actions.pay").value(true))
                    .andExpect(jsonPath("$.actions.requestCancellation").value(false));
            expectQuantity(variantId, 3);
            admin(orderId).andExpect(jsonPath("$.payment.status").value("UNPAID"));
            assertThat(cartVariantIds()).contains(variantId.toString());
        }

        @Test
        @DisplayName("AC-146、147 与信が成功すると受付になり、注文した品目だけがカートから除かれる")
        void acceptsOnAuthorization() throws Exception {
            var orderId = pending(2);
            var addedLater = stockedVariant("後から足した商品");
            addToCart(addedLater);

            gateway.pay(orders.openPaymentPage(orderId));

            orders.awaitStatus(orderId, "ACCEPTED");
            store(orderId)
                    .andExpect(jsonPath("$.paymentDeadline").value(nullValue()))
                    .andExpect(jsonPath("$.actions.pay").value(false))
                    .andExpect(jsonPath("$.actions.requestCancellation").value(true));
            admin(orderId).andExpect(jsonPath("$.payment.status").value("AUTHORIZED"));
            assertThat(cartVariantIds()).containsExactly(addedLater.toString());
        }

        @Test
        @DisplayName("AC-147 品目が複数の注文でも、与信が成功すると受付になり、注文した品目がすべてカートから除かれる")
        void removesAllOrderedItems() throws Exception {
            var other = stockedVariant("一緒に注文する商品");
            addToCart(other);
            var orderId = pending(1);

            gateway.pay(orders.openPaymentPage(orderId));

            orders.awaitStatus(orderId, "ACCEPTED");
            assertThat(cartVariantIds()).isEmpty();
        }

        @Test
        @DisplayName("AC-151 支払の画面は、持ち主だけが開ける")
        void onlyOwnerOpensPage() throws Exception {
            var orderId = pending(1);
            var other = TestTokens.member().subject(UUID.randomUUID()).email("other@example.com");

            mockMvc.perform(post("/api/store/orders/" + orderId + "/payment-page").with(other.bearer()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/store/orders/" + orderId + "/payment-page").with(TestTokens.none()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-151 受付の注文と、支払の期限の後の注文は、支払の画面を開けない")
        void notPayable() throws Exception {
            var accepted = pending(1);
            orders.pay(accepted);
            var expired = pending(1);
            clock.setInstant(deadlineOf(expired));

            openPage(accepted).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ORDER_NOT_PAYABLE"));
            openPage(expired).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ORDER_NOT_PAYABLE"));
            store(expired).andExpect(jsonPath("$.actions.pay").value(false));
        }

        @Test
        @DisplayName("AC-152 支払の画面を何度開いても、同じ画面になる")
        void reusesPage() throws Exception {
            var orderId = pending(1);

            var first = orders.openPaymentPage(orderId);
            var second = orders.openPaymentPage(orderId);

            assertThat(second).isEqualTo(first);
            assertThat(gateway.openedPages()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("US-32 支払の期限が切れた注文を取り消す")
    class Expire {

        @Test
        @DisplayName("AC-153、154、155 期限を過ぎるとキャンセル（支払期限切れ）になり、在庫が戻り、調整履歴の理由は「注文のキャンセル」")
        void cancelsExpiredOrder() throws Exception {
            var orderId = pending(2);
            var deadline = deadlineOf(orderId);

            clock.setInstant(deadline.minusSeconds(1));
            expiryJob.expire();
            store(orderId).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

            clock.setInstant(deadline);
            expiryJob.expire();

            orders.awaitStatus(orderId, "CANCELLED");
            store(orderId).andExpect(jsonPath("$.cancellation.kind").value("PAYMENT_EXPIRED"));
            admin(orderId)
                    .andExpect(jsonPath("$.cancellation.kind").value("PAYMENT_EXPIRED"))
                    .andExpect(jsonPath("$.cancellation.reason").value(nullValue()))
                    .andExpect(jsonPath("$.cancellation.cancelledBy").value(nullValue()))
                    .andExpect(jsonPath("$.payment.status").value("NOT_PAID"));
            expectQuantity(variantId, 5);
            mockMvc.perform(get("/api/admin/inventory/variants/" + variantId + "/adjustments"))
                    .andExpect(jsonPath("$.items[0].reason").value("ORDER_CANCELLATION"))
                    .andExpect(jsonPath("$.items[0].delta").value(2));
        }

        @Test
        @DisplayName("AC-157 期限切れでキャンセルした後に与信の成功が届いたら、その与信を取り消し、注文はキャンセルのまま")
        void voidsLateAuthorization() throws Exception {
            var orderId = pending(1);
            var pageId = orders.openPaymentPage(orderId);
            clock.setInstant(deadlineOf(orderId));
            expiryJob.expire();
            orders.awaitStatus(orderId, "CANCELLED");

            gateway.notifyAuthorized(pageId);

            awaitPaymentStatus(orderId, "VOIDED");
            store(orderId).andExpect(jsonPath("$.status").value("CANCELLED"));
            assertThat(gateway.voided()).containsExactly(gateway.authorizationRefOf(pageId));
        }

        @Test
        @DisplayName("AC-158 支払待ちの注文は、運営者もキャンセルできない")
        void operatorCannotCancelPending() throws Exception {
            var orderId = pending(1);

            admin(orderId).andExpect(jsonPath("$.actions.cancel").value(false));
            cancelByOperator(orderId)
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.errorCode").value("AWAITING_PAYMENT"));
            store(orderId).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        }
    }

    @Nested
    @DisplayName("US-33 支払の状態を確認する")
    class PaymentStatusView {

        @Test
        @DisplayName("AC-160、161 管理画面の一覧で「支払待ち」で絞り込め、各行に支払の状態がある")
        void filtersPendingPayment() throws Exception {
            var pendingId = pending(1);
            var acceptedId = pending(1);
            orders.pay(acceptedId);

            mockMvc.perform(get("/api/admin/orders").param("status", "PENDING_PAYMENT").param("size", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id").value(hasItem(pendingId.toString())))
                    .andExpect(jsonPath("$.items[*].id").value(not(hasItem(acceptedId.toString()))))
                    .andExpect(jsonPath("$.items[?(@.id == '" + pendingId + "')].paymentStatus").value(hasItem("UNPAID")));
            mockMvc.perform(get("/api/admin/orders").param("size", "100"))
                    .andExpect(jsonPath("$.items[?(@.id == '" + acceptedId + "')].paymentStatus").value(hasItem("AUTHORIZED")));
        }

        @Test
        @DisplayName("AC-160 管理画面の詳細に、支払の状態と支払の期限がある")
        void showsPaymentInDetail() throws Exception {
            var orderId = pending(1);

            admin(orderId)
                    .andExpect(jsonPath("$.payment.status").value("UNPAID"))
                    .andExpect(jsonPath("$.payment.deadline").value(deadlineOf(orderId).toString()));
        }
    }

    @Nested
    @DisplayName("US-34 キャンセルした注文の与信を取り消す")
    class VoidAuthorization {

        @Test
        @DisplayName("AC-162 運営者のキャンセルで、与信が取り消される")
        void voidsOnOperatorCancel() throws Exception {
            var orderId = accepted();

            cancelByOperator(orderId).andExpect(status().isNoContent());

            store(orderId).andExpect(jsonPath("$.status").value("CANCELLED"));
            awaitPaymentStatus(orderId, "VOIDED");
        }

        @Test
        @DisplayName("AC-162 申請の承認によるキャンセルでも、与信が取り消される")
        void voidsOnApproval() throws Exception {
            var orderId = accepted();
            mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNoContent());

            mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancellation-request/approve"))
                    .andExpect(status().isNoContent());

            awaitPaymentStatus(orderId, "VOIDED");
        }

        @Test
        @DisplayName("AC-163、165 決済サービスで取消に失敗しても、キャンセルは成立し、管理画面に「与信の取消待ち」と出る")
        void cancelsEvenIfVoidFails() throws Exception {
            var orderId = accepted();
            gateway.failNextVoids(1);

            cancelByOperator(orderId).andExpect(status().isNoContent());

            store(orderId).andExpect(jsonPath("$.status").value("CANCELLED"));
            var paymentId = dsl.select(PAYMENT_.ID).from(PAYMENT_).where(PAYMENT_.ORDER_ID.eq(orderId)).fetchOne(PAYMENT_.ID);
            asyncEvents.awaitFailed(event -> event instanceof VoidRequested requested && requested.paymentId().equals(paymentId));
            assertThat(paymentStatus(orderId)).isEqualTo("VOID_PENDING");
        }
    }

    // ============================== 補助 ==============================

    /**
     * 商品を作って公開し、在庫を 5 にする。
     *
     * @return バリアント ID
     */
    private UUID stockedVariant(String name) throws Exception {
        var data = new CatalogTestData(mockMvc, clock);
        var product = data.product(taxCategoryId, name, 1000).publish();
        products.add(product);
        var id = product.variantIds.getFirst();
        stockItemRepository.createIfAbsent(id, product.id);
        stockItemRepository.adjust(id, StockAdjustment.create(5, StockAdjustmentReason.ARRIVAL, null, TestTokens.OPERATOR, NOW));
        return id;
    }

    /**
     * 支払わずに注文する。
     */
    private UUID pending(int quantity) throws Exception {
        var orderId = orders.placePendingOrder(variantId, quantity);
        orderIds.add(orderId);
        return orderId;
    }

    /**
     * 注文して支払い、受付にする（与信の成功のイベントの処理を待つ）。
     */
    private UUID accepted() throws Exception {
        var orderId = pending(1);
        gateway.pay(orders.openPaymentPage(orderId));
        orders.awaitStatus(orderId, "ACCEPTED");
        return orderId;
    }

    private void addToCart(UUID variant) throws Exception {
        mockMvc.perform(post("/api/store/carts/" + cartId() + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantId": "%s", "quantity": 1}""".formatted(variant)))
                .andExpect(status().isNoContent());
    }

    private String cartId() throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/store/me/cart")).andReturn().getResponse().getContentAsString(), "$.cartId");
    }

    private List<String> cartVariantIds() throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/store/carts/" + cartId())).andReturn().getResponse().getContentAsString(),
                "$.items[*].variantId");
    }

    private ResultActions store(UUID orderId) throws Exception {
        return mockMvc.perform(get("/api/store/orders/" + orderId)).andExpect(status().isOk());
    }

    private ResultActions admin(UUID orderId) throws Exception {
        return mockMvc.perform(get("/api/admin/orders/" + orderId)).andExpect(status().isOk());
    }

    private ResultActions openPage(UUID orderId) throws Exception {
        return mockMvc.perform(post("/api/store/orders/" + orderId + "/payment-page"));
    }

    private ResultActions cancelByOperator(UUID orderId) throws Exception {
        return mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "在庫の不備"}"""));
    }

    /**
     * 注文の支払の期限（注文日時 ＋ 30 分。BR-16）
     */
    private Instant deadlineOf(UUID orderId) throws Exception {
        String orderedAt = JsonPath.read(mockMvc.perform(get("/api/store/orders/" + orderId)).andReturn().getResponse()
                .getContentAsString(), "$.orderedAt");
        return Instant.parse(orderedAt).plus(Duration.ofMinutes(30));
    }

    private String paymentStatus(UUID orderId) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/admin/orders/" + orderId)).andReturn().getResponse().getContentAsString(),
                "$.payment.status");
    }

    private void awaitPaymentStatus(UUID orderId, String expected) {
        await().atMost(TIMEOUT).until(() -> paymentStatus(orderId).equals(expected));
    }

    private void expectQuantity(UUID variant, int quantity) throws Exception {
        mockMvc.perform(get("/api/admin/inventory/variants/" + variant)).andExpect(jsonPath("$.quantity").value(quantity));
    }
}
