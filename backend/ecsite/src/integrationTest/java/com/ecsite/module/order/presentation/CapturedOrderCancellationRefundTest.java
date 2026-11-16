package com.ecsite.module.order.presentation;

import com.ecsite.AsyncEvents;
import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.OrderTestData;
import com.ecsite.ShippingTaxCategory;
import com.ecsite.ShippingTestData;
import com.ecsite.TestTokens;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
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

import static com.ecsite.AsyncEvents.TIMEOUT;
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
 * 運営者が申請を承認すると、確定した売上を返金する。
 * 返金はコミットの後に非同期で行うので、このテストはコミットし、結果を待つ。
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

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestPaymentGateway gateway;

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
     * 注文の与信の参照
     */
    private String authorizationRef;

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
        var orders = new OrderTestData(mockMvc, gateway, applicationContext, owner);
        orderId = orders.placeOrder(variantId, ORDERED);
        authorizationRef = gateway.authorizationRefOf(orders.paidPageId(orderId));
        // 支払うとリスナーが非同期で注文を受け付け直す。終わる前に出荷すると、楽観ロックで衝突することがある
        asyncEvents.awaitHandled(OrderTestData.paymentAuthorized(orderId));
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-172、192 売上確定の最中に申請されて出荷が 409 になった注文は、承認するとキャンセルになり、総額を 1 回だけ返金し、在庫が戻る")
    void refundsCapturedOrderAfterApproval() throws Exception {
        // 準備：出荷の登録の売上確定の最中に、消費者がキャンセルを申請する
        gateway.duringNextCapture(this::requestCancellationQuietly);

        // 操作と確認：出荷の登録は、注文が受付でなくなったので 409
        ship().andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ORDER_NOT_SHIPPABLE"));

        // 操作：運営者が申請を承認し、返金済みになるのを待つ
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancellation-request/approve")).andExpect(status().isNoContent());
        await().atMost(TIMEOUT).until(() -> "REFUNDED".equals(adminOrder("$.payment.status")));

        // 確認：キャンセルで、出荷はない
        mockMvc.perform(get("/api/admin/orders/" + orderId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.shipment").doesNotExist());

        // 確認：総額を 1 回だけ返金し、確定した与信は取り消さない
        assertThat(gateway.amountsOf(TestPaymentGateway.Operation.REFUND, orderId)).containsExactly(ORDER_TOTAL);
        assertThat(gateway.voided()).doesNotContain(authorizationRef);

        // 確認：在庫が注文の前の数に戻る
        InventoryTestData.expectQuantity(mockMvc, variantId, STOCKED);
    }

    // ============================== 準備の補助 ==============================

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
            mockMvc.perform(put("/api/store/orders/" + orderId + "/cancellation-request").with(owner)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNoContent());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ============================== 確認の補助 ==============================

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
