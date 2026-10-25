package com.ecsite.module.order.presentation;

import com.ecsite.AsyncEvents;
import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.TestClockConfig;
import com.ecsite.TestTokens;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.application.payment.OrderPaymentService;
import com.ecsite.module.payment.application.PaymentExpiryJob;
import com.ecsite.module.payment.domain.payment.VoidRequested;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.jooq.payment.Tables.PAYMENT_;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
 * 注文と支払の受け入れテスト。<br />
 * 決済のイベントはコミットの後に非同期で届くので、コミットして処理を待つ。作ったデータは消さない。
 * 決済サービスは mock。支払の期限は、時計を進めて期限切れのジョブを呼んで確認する。
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
    TestPaymentGateway gateway;

    @Autowired
    OrderPaymentService orderPaymentService;

    @Autowired
    PaymentExpiryJob expiryJob;

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

    @Autowired
    AsyncEvents asyncEvents;

    /**
     * 注文を作る補助
     */
    private OrderTestData orders;

    /**
     * このテストの会員（巻き戻さないので、他のテストとカートを共有しないよう、テストごとに変える）
     */
    private TestTokens.Builder owner;

    /**
     * テストで使う税区分の ID
     */
    private UUID taxCategoryId;

    private UUID variantId;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
        gateway.reset();
        ShippingTaxCategory.ensure(dsl);
        taxCategoryId = new CatalogTestData(mockMvc, clock).taxCategory("支払-" + UUID.randomUUID().toString().substring(0, 8), "10");
        variantId = stockedVariant("支払の商品");
        owner = TestTokens.member().subject(UUID.randomUUID());
        orders = new OrderTestData(mockMvc, gateway, orderPaymentService, owner.bearer());
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
            var addedLater = stockedVariant("後から追加した商品");
            orders.add(orders.cart(), addedLater, 1);

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
            orders.add(orders.cart(), other, 1);
            var orderId = pending(1);

            gateway.pay(orders.openPaymentPage(orderId));

            orders.awaitStatus(orderId, "ACCEPTED");
            assertThat(cartVariantIds()).isEmpty();
        }

        @Test
        @DisplayName("AC-151 支払画面は、持ち主だけが開ける")
        void onlyOwnerOpensPage() throws Exception {
            var orderId = pending(1);
            var other = TestTokens.member().subject(UUID.randomUUID()).email("other@example.com");

            mockMvc.perform(post("/api/store/orders/" + orderId + "/payment-page").with(other.bearer()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/store/orders/" + orderId + "/payment-page").with(TestTokens.none()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-151 受付の注文と、支払の期限の後の注文は、支払画面を開けない")
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
        @DisplayName("AC-295 決済サービスに接続できなければ、502 と、やり直せることを示すメッセージを返し、やり直せば開ける")
        void gatewayUnavailable() throws Exception {
            var orderId = pending(1);
            gateway.failNextOpens(1);

            openPage(orderId)
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.errorCode").value("PAYMENT_GATEWAY_UNAVAILABLE"))
                    .andExpect(jsonPath("$.message").value(containsString("やり直して")));
            openPage(orderId).andExpect(status().isOk());
        }

        @Test
        @DisplayName("AC-143 支払画面を開くと、決済サービスに、注文の総額（送料を含む）を与信の額として渡す")
        void opensPageWithOrderTotal() throws Exception {
            var orderId = pending(2);

            orders.openPaymentPage(orderId);

            var total = dsl.select(SALES_ORDER.TOTAL_AMOUNT).from(SALES_ORDER).where(SALES_ORDER.ID.eq(orderId))
                    .fetchOne(SALES_ORDER.TOTAL_AMOUNT);
            // 商品 1,000 円（税抜）× 2 個の税込 2,200 円に、送料が加わる
            assertThat(total).isGreaterThan(2200L);
            assertThat(gateway.amountsOf(TestPaymentGateway.Operation.OPEN_PAGE, orderId))
                    .containsExactly(Money.of(total));
        }

        @Test
        @DisplayName("AC-152 支払画面を何度開いても、同じ画面になる")
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
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("AWAITING_PAYMENT"));
            store(orderId).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        }

        @Test
        @DisplayName("AC-158 支払待ちの注文は、消費者もキャンセルを申請できない")
        void consumerCannotRequestCancellationOfPending() throws Exception {
            var orderId = pending(1);

            mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request").with(owner.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CANCELLATION_NOT_REQUESTABLE"));

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

            // 他のテストの注文が混ざらないよう、自分の注文の注文番号でも絞る
            var pendingNumber = orderNumber(pendingId);
            var acceptedNumber = orderNumber(acceptedId);
            mockMvc.perform(get("/api/admin/orders").param("status", "PENDING_PAYMENT").param("orderNumber", pendingNumber))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id").value(hasItem(pendingId.toString())))
                    .andExpect(jsonPath("$.items[?(@.id == '" + pendingId + "')].paymentStatus").value(hasItem("UNPAID")));
            mockMvc.perform(get("/api/admin/orders").param("status", "PENDING_PAYMENT").param("orderNumber", acceptedNumber))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id").value(not(hasItem(acceptedId.toString()))));
            mockMvc.perform(get("/api/admin/orders").param("orderNumber", acceptedNumber))
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
            mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request").with(owner.bearer())
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
        var id = product.variantIds.getFirst();
        inventory.stock(id, product.id, 5);
        return id;
    }

    /**
     * 支払わずに注文する。
     */
    private UUID pending(int quantity) throws Exception {
        return orders.placePendingOrder(variantId, quantity);
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

    private List<String> cartVariantIds() throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/store/carts/" + orders.cart()).with(owner.bearer())).andReturn().getResponse().getContentAsString(),
                "$.items[*].variantId");
    }

    private ResultActions store(UUID orderId) throws Exception {
        return mockMvc.perform(get("/api/store/orders/" + orderId).with(owner.bearer())).andExpect(status().isOk());
    }

    /**
     * 注文の注文番号を、購入者として取得する。
     */
    private String orderNumber(UUID orderId) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/store/orders/" + orderId).with(owner.bearer())).andReturn().getResponse()
                .getContentAsString(), "$.orderNumber");
    }

    private ResultActions admin(UUID orderId) throws Exception {
        return mockMvc.perform(get("/api/admin/orders/" + orderId)).andExpect(status().isOk());
    }

    private ResultActions openPage(UUID orderId) throws Exception {
        return mockMvc.perform(post("/api/store/orders/" + orderId + "/payment-page").with(owner.bearer()));
    }

    private ResultActions cancelByOperator(UUID orderId) throws Exception {
        return mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "在庫の不備"}"""));
    }

    /**
     * 注文の支払の期限（注文日時 ＋ 30 分）
     */
    private Instant deadlineOf(UUID orderId) throws Exception {
        String orderedAt = JsonPath.read(mockMvc.perform(get("/api/store/orders/" + orderId).with(owner.bearer())).andReturn().getResponse()
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
