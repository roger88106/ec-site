package com.ecsite.module.order.presentation;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.payment.application.PaymentProperties;
import com.ecsite.module.payment.application.PaymentRequests;
import com.ecsite.module.payment.application.job.PaymentJobs;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.data.InventoryTestData;
import com.ecsite.support.data.OrderTestData;
import com.ecsite.support.data.ShippingTaxCategory;
import com.ecsite.support.data.ShippingTestData;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.support.async.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 売上確定の後にキャンセルされた注文の、通しの流れのテスト
 *
 * <p>出荷の登録で売上を確定している最中に、消費者がキャンセルを申請する。出荷の登録は 409 で失敗し、
 * 運営者が申請を承認すると、確定した売上を返金する。流れの観点（出荷の失敗、キャンセル、返金、在庫）ごとにテストを分け、
 * 承認までの流れは準備の補助（{@link #approvedAfterCapture()}）で作る。
 * 返金はコミットの後に非同期で行うので、このテストはコミットし、リスナーの処理を待つ。
 * 作ったデータは消さないので、税区分の名前、会員、送り状番号はテストごとに変え、確認は自分の注文だけにする。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る。
 */
@IntegrationTest
class CapturedOrderCancellationRefundTest {

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
     * 入荷した在庫数
     */
    private static final int STOCKED = 5;

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

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    /**
     * テスト用の決済サービス（売上確定の最中の処理と、返金・与信の取消の記録）
     */
    @Autowired
    TestPaymentGateway gateway;

    /**
     * Spring のコンテキスト（注文の補助が支払の結果を反映するのに使う）
     */
    @Autowired
    ApplicationContext applicationContext;

    /**
     * テストの時計
     */
    @Autowired
    MutableClock clock;

    /**
     * 決済の設定（照合を始めるまでの時間）
     */
    @Autowired
    PaymentProperties properties;

    /**
     * 在庫のリポジトリ（入荷に使う）
     */
    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * DB（送料の税区分の用意と、支払への依頼のイベントの選択に使う）
     */
    @Autowired
    DSLContext dsl;

    /**
     * 非同期のリスナーを待つ補助
     */
    @Autowired
    AsyncEvents asyncEvents;

    /**
     * 注文した会員
     */
    private RequestPostProcessor owner;

    /**
     * 注文した商品のバリアント ID
     */
    private UUID variantId;

    /**
     * 受付の注文の ID
     */
    private UUID orderId;

    /**
     * 注文の補助（申請の準備に使う）
     */
    private OrderTestData orders;

    /**
     * 注文の与信の参照
     */
    private String authorizationRef;

    /**
     * 入荷した商品を、テストごとの会員が注文して支払い、受付にする。
     */
    @BeforeEach
    void setUp() throws Exception {
        var inventory = new InventoryTestData(stockItemRepository, NOW);
        ShippingTaxCategory.ensure(dsl);
        var catalog = new CatalogTestData(mockMvc, clock);
        var taxCategoryId = catalog.taxCategory("確定後の取消-" + UUID.randomUUID().toString().substring(0, 8), "10");
        var product = catalog.product(taxCategoryId, "確定後の取消の商品", PRICE_EXCL_TAX).publish();
        variantId = product.variantIds.getFirst();
        inventory.stock(variantId, product.id, STOCKED);
        // コミットするので、他のテストとカートを共有しないよう、テストごとの会員で注文する
        owner = TestTokens.member().subject(UUID.randomUUID()).email("captured-" + UUID.randomUUID() + "@example.com").bearer();
        orders = new OrderTestData(mockMvc, gateway, applicationContext, owner);
        orderId = orders.acceptedOrder(variantId, ORDERED);
        authorizationRef = gateway.authorizationRefOf(orders.paidPageId(orderId));
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-170 売上確定の最中に消費者がキャンセルを申請すると、出荷の登録は 409 で、出荷は作られない")
    void rejectsShipmentWhenRequestedDuringCapture() throws Exception {
        // 準備：出荷の登録の売上確定の最中に、消費者がキャンセルを申請する
        gateway.duringNextCapture(this::requestCancellationQuietly);

        // 操作と確認：注文が受付でなくなったので 409
        ship().andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ORDER_NOT_SHIPPABLE"));

        // 確認：出荷はない
        mockMvc.perform(get("/api/admin/orders/" + orderId)).andExpect(jsonPath("$.shipment").doesNotExist());
    }

    @Test
    @DisplayName("AC-101 出荷の登録が 409 になった後に申請を承認すると、注文はキャンセルになる")
    void cancelsOrderOnApproval() throws Exception {
        // 準備と操作：売上確定の最中に申請され、運営者が承認する
        approvedAfterCapture();

        // 確認：キャンセル
        mockMvc.perform(get("/api/admin/orders/" + orderId)).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("AC-407-1 売上を確定した注文のキャンセルを承認すると、総額を 1 回だけ返金し、確定した与信は取り消さない")
    void refundsTotalOnceWithoutVoid() throws Exception {
        // 準備と操作：売上確定の最中に申請され、運営者が承認する
        approvedAfterCapture();

        // 確認：総額の返金が 1 回だけで、与信の取消はない
        assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId)).containsExactly(ORDER_TOTAL);
        assertThat(gateway.voided()).doesNotContain(authorizationRef);
    }

    @Test
    @DisplayName("AC-101 出荷の登録が 409 になった後に申請を承認すると、在庫が注文の前の数に戻る")
    void restoresStockOnApproval() throws Exception {
        // 準備と操作：売上確定の最中に申請され、運営者が承認する
        approvedAfterCapture();

        // 確認：入荷した数に戻る
        InventoryTestData.expectQuantity(mockMvc, variantId, STOCKED);
    }

    @Test
    @DisplayName("AC-423-10 照合で売上確定済みにした注文をキャンセルすると、総額を 1 回だけ返金し、与信は取り消さない")
    void refundsTotalOnceAfterReconciliation() throws Exception {
        // 準備：売上確定の応答が届かず、照合で売上確定済みにした注文
        gateway.loseNextCaptureResponses(1);
        ship().andExpect(status().isBadGateway());
        clock.setInstant(clock.instant().plus(properties.captureReconcileDelay()));
        PaymentJobs.expireUntil(applicationContext, () -> "CAPTURED".equals(adminOrderUnchecked("$.payment.status")));
        assertThat(orders.paymentStatus(orderId)).isEqualTo("CAPTURED");

        // 操作：運営者がキャンセルする
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"reason": "在庫の不備"}"""))
                .andExpect(status().isNoContent());
        awaitRefunded();

        // 確認：総額の返金が 1 回だけで、与信の取消はない
        assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId)).containsExactly(ORDER_TOTAL);
        assertThat(gateway.voided()).doesNotContain(authorizationRef);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 売上確定の最中に消費者がキャンセルを申請して出荷の登録が 409 になった注文を、運営者が承認する。
     *
     * <p>返金済みになり、キャンセルのイベントと、支払への依頼（返金・与信の取消）のイベントのリスナーが全て終わるまで待つ
     * （返金と与信の取消が起きないことを、リスナーが終わってから数えるため）。
     */
    private void approvedAfterCapture() throws Exception {
        gateway.duringNextCapture(this::requestCancellationQuietly);
        ship().andExpect(status().isConflict());
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancellation-request/approve")).andExpect(status().isNoContent());
        awaitRefunded();
    }

    /**
     * 支払が返金済みになり、キャンセルのイベントと、支払への依頼のイベントのリスナーが全て終わるまで待つ。
     */
    private void awaitRefunded() throws Exception {
        await().atMost(TIMEOUT).until(() -> "REFUNDED".equals(orders.paymentStatus(orderId)));
        asyncEvents.awaitPublishedThenHandled(event -> event instanceof OrderStatusChanged changed
                && changed.orderId().value().equals(orderId) && changed.to() == OrderStatusChanged.Status.CANCELLED);
        asyncEvents.awaitHandled(PaymentRequests.of(dsl, orderId));
    }

    /**
     * 出荷を登録する。
     *
     * @return 応答
     */
    private ResultActions ship() throws Exception {
        return mockMvc.perform(post("/api/admin/orders/" + orderId + "/shipment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"carrier": "YAMATO", "trackingNumber": "%s"}""".formatted(ShippingTestData.trackingNumber())));
    }

    /**
     * 消費者がキャンセルを申請する（売上確定の最中の処理なので、検査例外を包む）。
     */
    private void requestCancellationQuietly() {
        try {
            orders.requestCancellation(orderId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 管理の API で、注文の項目を読む（検査例外を包む）。
     *
     * @param path JSON のパス
     * @return 項目の値
     */
    private String adminOrderUnchecked(String path) {
        try {
            return adminOrder(path);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 管理の API で、注文の項目を読む。
     *
     * @param path JSON のパス
     * @return 項目の値
     */
    private String adminOrder(String path) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/admin/orders/" + orderId)).andReturn().getResponse().getContentAsString(), path);
    }
}
