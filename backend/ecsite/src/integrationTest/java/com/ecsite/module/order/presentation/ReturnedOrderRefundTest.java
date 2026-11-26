package com.ecsite.module.order.presentation;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.payment.application.PaymentRequests;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.data.ShippingTaxCategory;
import com.ecsite.support.data.ShippingTestData;
import com.ecsite.support.http.Mailpit;
import com.ecsite.support.security.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.support.async.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 返送された注文のキャンセルの、返金とメールの受け入れテスト
 *
 * <p>返金とメールの送信はコミットの後に非同期で行うので、このテストはコミットし、リスナーを待つ。
 * 作ったデータは消さないので、税区分の名前、会員とそのメールアドレス、送り状番号はテストごとに変え、確認は自分の注文だけにする。
 * キャンセルの成立と在庫の戻しは、ロールバックする {@code ReturnedOrderCancellationApiTest} が確認する。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る。
 */
@IntegrationTest
class ReturnedOrderRefundTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 商品の税抜価格（円）
     */
    private static final long PRICE_EXCL_TAX = 1000;

    /**
     * 注文の数量
     */
    private static final int ORDERED = 2;

    /**
     * 注文の総額（送料を含む）
     *
     * <p>商品 1,000 円（税抜。税率 10% で税込 1,100 円）× 2 個の 2,200 円に、送料の既定の 1,000 円（税込）を加えた額。
     */
    private static final Money ORDER_TOTAL = Money.of(3200);

    /**
     * キャンセルの本文
     */
    private static final String CANCEL_BODY = """
            {"reason": "返送された", "restock": true}""";

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestPaymentGateway gateway;

    @Autowired
    MockCarrier mockCarrier;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    MutableClock clock;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    DSLContext dsl;

    @Autowired
    AsyncEvents asyncEvents;

    @Autowired
    Mailpit mailpit;

    /**
     * 注文した会員のメールアドレス（注文の連絡先。他のテストのメールと混ぜないよう、テストごとに変える）
     */
    private String email;

    /**
     * 返送された注文の ID
     */
    private UUID orderId;

    @BeforeEach
    void setUp() throws Exception {
        var inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        var taxCategoryId = catalog.taxCategory("返金-" + UUID.randomUUID().toString().substring(0, 8), "10");
        var product = catalog.product(taxCategoryId, "返金の商品", PRICE_EXCL_TAX).publish();
        var variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, 5);
        // コミットするので、他のテストとカートを共有しないよう、テストごとの会員で注文する
        email = "returned-" + UUID.randomUUID() + "@example.com";
        var owner = TestTokens.member().subject(UUID.randomUUID()).email(email);
        orderId = new OrderTestData(mockMvc, gateway, applicationContext, owner.bearer()).acceptedOrder(variantId, ORDERED);
        var trackingNumber = ShippingTestData.trackingNumber();
        var shipping = new ShippingTestData(mockMvc, mockCarrier, applicationContext);
        shipping.ship(orderId, Carrier.YAMATO, trackingNumber);
        shipping.notifyStatus(Carrier.YAMATO, trackingNumber, "返品");
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-193 決済サービスが返金に失敗しても、キャンセルは成立し、支払は返金待ちのまま残る")
    void cancelsEvenIfRefundFails() throws Exception {
        // 準備：決済サービスが次の返金に失敗する
        gateway.failNextRefunds(1);

        // 操作：キャンセルし、この注文の返金の依頼のリスナーが失敗するのを待つ
        cancel();
        asyncEvents.awaitFailed(PaymentRequests.refund(dsl, orderId));

        // 確認（前提）：返金されていない
        assertThat(gateway.refunded()).doesNotContain(orderId);

        // 確認：キャンセルのままで、支払は返金待ち
        mockMvc.perform(get("/api/admin/orders/" + orderId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payment.status").value("REFUND_PENDING"));
    }

    @Test
    @DisplayName("AC-192 返送の注文をキャンセルすると、総額（送料を含む）を返金する")
    void refundsOrderTotal() throws Exception {
        // 操作：キャンセルし、返金済みになるのを待つ
        cancel();
        await().atMost(TIMEOUT).until(() -> paymentStatus().equals("REFUNDED"));

        // 確認：決済サービスに渡した返金の額が、送料を含む総額
        assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId)).containsExactly(ORDER_TOTAL);
    }

    @Test
    @DisplayName("AC-196 返送の注文をキャンセルしても、消費者にメールを送らない")
    void sendsNoMailOnCancellation() throws Exception {
        // 準備：出荷通知メールが 1 通届くのを待つ
        await().atMost(TIMEOUT).until(() -> mailpit.messagesTo(email).size() == 1);

        // 操作：キャンセルし、キャンセルのイベントのリスナーと返金が終わるのを待つ
        cancel();
        asyncEvents.awaitPublishedThenHandled(event -> event instanceof OrderStatusChanged changed
                && changed.orderId().value().equals(orderId) && changed.to() == OrderStatusChanged.Status.CANCELLED);
        await().atMost(TIMEOUT).until(() -> paymentStatus().equals("REFUNDED"));

        // 確認：キャンセルのリスナーが終わった後も、この注文の連絡先へのメールは出荷通知の 1 通のまま
        assertThat(mailpit.messagesTo(email)).hasSize(1);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 在庫に戻して、運営者のキャンセルを送り、204 を確認する。
     */
    private void cancel() throws Exception {
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CANCEL_BODY))
                .andExpect(status().isNoContent());
    }

    // ============================== 確認の補助 ==============================

    /**
     * 管理の API で、注文の支払の状態を読む。
     *
     * @return 支払の状態
     */
    private String paymentStatus() throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/admin/orders/" + orderId)).andReturn().getResponse().getContentAsString(),
                "$.payment.status");
    }
}
